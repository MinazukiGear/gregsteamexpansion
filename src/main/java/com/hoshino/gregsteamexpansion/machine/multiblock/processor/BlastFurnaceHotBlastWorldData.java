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

/** Persistent ownership of large-steam-blast-furnace bodies and external module boxes. */
public final class BlastFurnaceHotBlastWorldData extends SavedData {

    private static final String DATA_NAME = "gregsteamexpansion_hot_blast_modules";

    public static final String BODY_ID = "__body";

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
            // Legacy saves stored exactly one hot-blast claim per controller and therefore had
            // neither module nor face identifiers.
            String moduleId = tag.contains("module", Tag.TAG_STRING)
                    ? tag.getString("module") : BlastFurnaceHotBlastModule.ID;
            String faceId = tag.contains("face", Tag.TAG_STRING)
                    ? tag.getString("face") : "back";
            return new Claim(BlockPos.of(tag.getLong("controller")), moduleId, faceId,
                    BlockPos.of(tag.getLong("anchor")),
                    BlockPos.of(tag.getLong("min")), BlockPos.of(tag.getLong("max")));
        }
    }

    public record ClaimResult(boolean success, @Nullable BlockPos conflictingController) {}

    private final Map<String, Claim> claims = new ConcurrentHashMap<>();

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

    public static Claim claimFor(BlockPos controller, net.minecraft.core.Direction front) {
        BlockPos[] bounds = BlastFurnaceHotBlastModule.bounds(controller, front);
        return new Claim(controller.immutable(), BlastFurnaceHotBlastModule.ID, "back",
                BlastFurnaceHotBlastModule.anchor(controller, front), bounds[0], bounds[1]);
    }

    public static Claim highChargeClaimFor(BlockPos controller, net.minecraft.core.Direction front) {
        BlockPos[] bounds = BlastFurnaceHighChargeModule.bounds(controller, front);
        return new Claim(controller.immutable(), BlastFurnaceHighChargeModule.ID, "top",
                BlastFurnaceHighChargeModule.anchor(controller, front), bounds[0], bounds[1]);
    }

    public static Claim bodyClaimFor(BlockPos controller, net.minecraft.core.Direction front) {
        BlockPos a = local(controller, front, -6, 0, 0);
        BlockPos b = local(controller, front, 6, 12, 14);
        return new Claim(controller.immutable(), BODY_ID, "body", controller.immutable(),
                new BlockPos(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()),
                        Math.min(a.getZ(), b.getZ())),
                new BlockPos(Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()),
                        Math.max(a.getZ(), b.getZ())));
    }

    /** Body boxes may overlap each other, but evict any foreign module they newly cover. */
    public synchronized void claimBody(Claim body) {
        boolean changed = claims.entrySet().removeIf(entry -> {
            Claim other = entry.getValue();
            return !other.controller().equals(body.controller())
                    && !BODY_ID.equals(other.moduleId())
                    && intersects(body.min(), body.max(), other.min(), other.max());
        });
        Claim previous = claims.put(key(body.controller(), body.moduleId()), body);
        if (changed || !body.equals(previous)) {
            setDirty();
        }
    }

    /** Same controller/face is unique; every module box is exclusive, including deliberate air. */
    public synchronized ClaimResult claim(Claim mine) {
        String mineKey = key(mine.controller(), mine.moduleId());
        for (var entry : claims.entrySet()) {
            Claim other = entry.getValue();
            if (entry.getKey().equals(mineKey)) {
                continue;
            }
            if (BODY_ID.equals(other.moduleId()) && other.controller().equals(mine.controller())) {
                continue;
            }
            boolean faceConflict = other.controller().equals(mine.controller())
                    && other.faceId().equals(mine.faceId());
            if (faceConflict || intersects(mine.min(), mine.max(), other.min(), other.max())) {
                return new ClaimResult(false, other.controller());
            }
        }
        Claim previous = claims.put(mineKey, mine);
        if (!mine.equals(previous)) {
            setDirty();
        }
        return new ClaimResult(true, null);
    }

    public synchronized void release(BlockPos controller, String moduleId) {
        if (claims.remove(key(controller, moduleId)) != null) {
            setDirty();
        }
    }

    /** Legacy hot-blast-only release entry point retained for existing callers/tests. */
    public synchronized void release(BlockPos controller) {
        release(controller, BlastFurnaceHotBlastModule.ID);
    }

    public synchronized void releaseAll(BlockPos controller) {
        boolean removed = claims.entrySet().removeIf(entry -> entry.getValue().controller().equals(controller));
        if (removed) {
            setDirty();
        }
    }

    public boolean owns(BlockPos controller, BlockPos anchor) {
        return claims.values().stream().anyMatch(claim -> claim.controller().equals(controller)
                && claim.anchor().equals(anchor));
    }

    @Nullable
    public Claim claimOf(BlockPos controller) {
        return claims.get(key(controller, BlastFurnaceHotBlastModule.ID));
    }


    @Nullable
    public Claim claimOf(BlockPos controller, String moduleId) {
        return claims.get(key(controller, moduleId));
    }

    private static String key(BlockPos controller, String moduleId) {
        return controller.asLong() + ":" + moduleId;
    }

    private static boolean intersects(BlockPos aMin, BlockPos aMax, BlockPos bMin, BlockPos bMax) {
        return aMin.getX() <= bMax.getX() && aMax.getX() >= bMin.getX()
                && aMin.getY() <= bMax.getY() && aMax.getY() >= bMin.getY()
                && aMin.getZ() <= bMax.getZ() && aMax.getZ() >= bMin.getZ();
    }

    /** Removes a claim only after the owner's already-materialized chunk proves it is stale. */
    public synchronized void pruneStaleClaims(LevelChunk chunk) {
        ChunkPos chunkPos = chunk.getPos();
        List<String> stale = new ArrayList<>();
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
        for (String key : stale) {
            Claim removed = claims.remove(key);
            if (removed != null) {
                GregSteamExpansion.LOGGER.info("Released stale blast-furnace {} claim at {}",
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

    private static BlockPos local(BlockPos controller, net.minecraft.core.Direction front,
                                  int side, int back, int up) {
        net.minecraft.core.Direction rear = front.getOpposite();
        net.minecraft.core.Direction right = front.getClockWise();
        return controller.relative(right, side).relative(rear, back).above(up);
    }
}
