package com.hoshino.gregsteamexpansion.cokeoven;

import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.hoshino.gregsteamexpansion.GregSteamExpansion;
import com.hoshino.gregsteamexpansion.machine.multiblock.largecokeoven.LargeCokeOvenMachine;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
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

/** Persistent ownership of large-coke-oven external-module boxes. */
public final class CokeOvenModuleWorldData extends SavedData {

    private static final String DATA_NAME = "gregsteamexpansion_coke_oven_modules";

    public record Claim(BlockPos controller, String moduleId, String faceId,
                        BlockPos anchor, BlockPos min, BlockPos max) {
        private CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putLong("controller", controller.asLong());
            tag.putString("module", moduleId);
            tag.putString("face", faceId);
            tag.putLong("anchor", anchor.asLong());
            tag.putLong("min", min.asLong());
            tag.putLong("max", max.asLong());
            return tag;
        }

        private static Claim load(CompoundTag tag) {
            return new Claim(BlockPos.of(tag.getLong("controller")), tag.getString("module"),
                    tag.getString("face"), BlockPos.of(tag.getLong("anchor")),
                    BlockPos.of(tag.getLong("min")), BlockPos.of(tag.getLong("max")));
        }
    }

    public record ClaimResult(boolean success, @Nullable BlockPos conflictingController) {}

    private final Map<String, Claim> claims = new ConcurrentHashMap<>();

    private CokeOvenModuleWorldData() {}

    public static CokeOvenModuleWorldData getOrCreate(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                CokeOvenModuleWorldData::load, CokeOvenModuleWorldData::new, DATA_NAME);
    }

    public static CokeOvenModuleWorldData load(CompoundTag tag) {
        CokeOvenModuleWorldData data = new CokeOvenModuleWorldData();
        ListTag list = tag.getList("claims", CompoundTag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            Claim claim = Claim.load(list.getCompound(i));
            data.claims.put(key(claim.controller(), claim.moduleId()), claim);
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

    public static Claim dryQuenchClaim(BlockPos controller, net.minecraft.core.Direction front) {
        BlockPos[] bounds = LargeCokeOvenDryQuenchModule.bounds(controller, front);
        return new Claim(controller.immutable(), LargeCokeOvenDryQuenchModule.ID, "right",
                LargeCokeOvenDryQuenchModule.anchor(controller, front), bounds[0], bounds[1]);
    }

    public static Claim furnaceBaseClaim(BlockPos controller, net.minecraft.core.Direction front) {
        BlockPos[] bounds = LargeCokeOvenFurnaceBaseModule.bounds(controller, front);
        return new Claim(controller.immutable(), LargeCokeOvenFurnaceBaseModule.ID, "down",
                LargeCokeOvenFurnaceBaseModule.anchor(controller, front), bounds[0], bounds[1]);
    }

    public synchronized ClaimResult claim(Claim mine) {
        String mineKey = key(mine.controller(), mine.moduleId());
        for (var entry : claims.entrySet()) {
            Claim other = entry.getValue();
            if (entry.getKey().equals(mineKey)) continue;
            if (other.controller().equals(mine.controller())) {
                if (other.faceId().equals(mine.faceId())) {
                    return new ClaimResult(false, other.controller());
                }
                continue;
            }
            if (intersects(expand(mine.min(), -1), expand(mine.max(), 1),
                    other.min(), other.max())) {
                return new ClaimResult(false, other.controller());
            }
        }
        Claim previous = claims.put(mineKey, mine);
        if (!mine.equals(previous)) setDirty();
        return new ClaimResult(true, null);
    }

    @Nullable
    public BlockPos findConflict(BlockPos controller, BlockPos min, BlockPos max) {
        for (Claim other : claims.values()) {
            if (other.controller().equals(controller)) continue;
            if (intersects(expand(min, -1), expand(max, 1), other.min(), other.max())) {
                return other.controller();
            }
        }
        return null;
    }

    public synchronized void release(BlockPos controller, String moduleId) {
        if (claims.remove(key(controller, moduleId)) != null) setDirty();
    }

    public synchronized void releaseAll(BlockPos controller) {
        if (claims.entrySet().removeIf(entry -> entry.getValue().controller().equals(controller))) {
            setDirty();
        }
    }

    private static String key(BlockPos controller, String moduleId) {
        return controller.asLong() + ":" + moduleId;
    }

    private static BlockPos expand(BlockPos pos, int amount) {
        return pos.offset(amount, amount, amount);
    }

    private static boolean intersects(BlockPos aMin, BlockPos aMax, BlockPos bMin, BlockPos bMax) {
        return aMin.getX() <= bMax.getX() && aMax.getX() >= bMin.getX()
                && aMin.getY() <= bMax.getY() && aMax.getY() >= bMin.getY()
                && aMin.getZ() <= bMax.getZ() && aMax.getZ() >= bMin.getZ();
    }

    public synchronized void pruneStaleClaims(LevelChunk chunk) {
        ChunkPos chunkPos = chunk.getPos();
        List<String> stale = new ArrayList<>();
        for (var entry : claims.entrySet()) {
            BlockPos controller = entry.getValue().controller();
            if (!chunkPos.equals(new ChunkPos(controller))) continue;
            var blockEntity = chunk.getBlockEntity(controller);
            if (!(blockEntity instanceof IMachineBlockEntity holder
                    && holder.getMetaMachine() instanceof LargeCokeOvenMachine)) {
                stale.add(entry.getKey());
            }
        }
        if (stale.isEmpty()) return;
        for (String key : stale) {
            Claim removed = claims.remove(key);
            if (removed != null) {
                GregSteamExpansion.LOGGER.info("Released stale coke-oven module {} claim at {}",
                        removed.moduleId(), removed.controller().toShortString());
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
