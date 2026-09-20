package com.hoshino.gregsteamexpansion.machine.multiblock.processor;

import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.hoshino.gregsteamexpansion.GregSteamExpansion;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Persistent one-controller ownership for optional blast-furnace hot-blast modules. */
public final class BlastFurnaceHotBlastWorldData extends SavedData {

    private static final String DATA_NAME = "gregsteamexpansion_hot_blast_modules";

    public record Claim(BlockPos controller, BlockPos anchor, BlockPos min, BlockPos max) {

        private CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putLong("controller", controller.asLong());
            tag.putLong("anchor", anchor.asLong());
            tag.putLong("min", min.asLong());
            tag.putLong("max", max.asLong());
            return tag;
        }

        private static Claim load(CompoundTag tag) {
            return new Claim(BlockPos.of(tag.getLong("controller")), BlockPos.of(tag.getLong("anchor")),
                    BlockPos.of(tag.getLong("min")), BlockPos.of(tag.getLong("max")));
        }
    }

    public record ClaimResult(boolean success, @Nullable BlockPos conflictingController) {}

    private final Map<Long, Claim> claims = new ConcurrentHashMap<>();

    private BlastFurnaceHotBlastWorldData() {}

    public static BlastFurnaceHotBlastWorldData getOrCreate(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                BlastFurnaceHotBlastWorldData::load,
                BlastFurnaceHotBlastWorldData::new,
                DATA_NAME);
    }

    public static BlastFurnaceHotBlastWorldData load(CompoundTag tag) {
        BlastFurnaceHotBlastWorldData data = new BlastFurnaceHotBlastWorldData();
        ListTag list = tag.getList("claims", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            Claim claim = Claim.load(list.getCompound(i));
            data.claims.put(claim.controller().asLong(), claim);
        }
        return data;
    }

    @Override
    public @NotNull CompoundTag save(@NotNull CompoundTag tag) {
        ListTag list = new ListTag();
        claims.values().forEach(claim -> list.add(claim.save()));
        tag.put("claims", list);
        return tag;
    }

    public static Claim claimFor(BlockPos controller, net.minecraft.core.Direction front) {
        BlockPos[] bounds = BlastFurnaceHotBlastModule.bounds(controller, front);
        return new Claim(controller.immutable(),
                BlastFurnaceHotBlastModule.anchor(controller, front), bounds[0], bounds[1]);
    }

    /** Claims the complete 7×5×9 box; even deliberate air cannot be shared by another module. */
    public synchronized ClaimResult claim(Claim mine) {
        for (Claim other : claims.values()) {
            if (other.controller().equals(mine.controller())) {
                continue;
            }
            if (intersects(mine.min(), mine.max(), other.min(), other.max())) {
                return new ClaimResult(false, other.controller());
            }
        }
        Claim previous = claims.put(mine.controller().asLong(), mine);
        if (!mine.equals(previous)) {
            setDirty();
        }
        return new ClaimResult(true, null);
    }

    public synchronized void release(BlockPos controller) {
        if (claims.remove(controller.asLong()) != null) {
            setDirty();
        }
    }

    public boolean owns(BlockPos controller, BlockPos anchor) {
        Claim claim = claims.get(controller.asLong());
        return claim != null && claim.anchor().equals(anchor);
    }

    @Nullable
    public Claim claimOf(BlockPos controller) {
        return claims.get(controller.asLong());
    }

    private static boolean intersects(BlockPos aMin, BlockPos aMax, BlockPos bMin, BlockPos bMax) {
        return aMin.getX() <= bMax.getX() && aMax.getX() >= bMin.getX()
                && aMin.getY() <= bMax.getY() && aMax.getY() >= bMin.getY()
                && aMin.getZ() <= bMax.getZ() && aMax.getZ() >= bMin.getZ();
    }

    /** Removes a claim only after the owner's already-materialized chunk proves it is stale. */
    public synchronized void pruneStaleClaims(LevelChunk chunk) {
        ChunkPos chunkPos = chunk.getPos();
        List<Long> stale = new ArrayList<>();
        for (var entry : claims.entrySet()) {
            BlockPos controller = entry.getValue().controller();
            if (!chunkPos.equals(new ChunkPos(controller))) {
                continue;
            }
            var blockEntity = chunk.getBlockEntity(controller);
            if (!(blockEntity instanceof IMachineBlockEntity holder
                    && holder.getMetaMachine() instanceof LargeSteamBlastFurnaceMachine)) {
                stale.add(entry.getKey());
            }
        }
        if (stale.isEmpty()) {
            return;
        }
        for (long key : stale) {
            Claim removed = claims.remove(key);
            if (removed != null) {
                GregSteamExpansion.LOGGER.info("Released stale hot-blast module claim at {}",
                        removed.controller().toShortString());
            }
        }
        setDirty();
    }

    @Mod.EventBusSubscriber(modid = GregSteamExpansion.MOD_ID)
    public static final class ChunkListener {

        private ChunkListener() {}

        @SubscribeEvent
        public static void onChunkLoad(ChunkEvent.Load event) {
            if (event.getLevel() instanceof ServerLevel level && event.getChunk() instanceof LevelChunk chunk) {
                getOrCreate(level).pruneStaleClaims(chunk);
            }
        }
    }
}
