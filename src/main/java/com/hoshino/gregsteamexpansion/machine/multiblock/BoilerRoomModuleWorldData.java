package com.hoshino.gregsteamexpansion.machine.multiblock;

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

/** Persistent ownership of boiler-room-specific external module boxes and face slots. */
public final class BoilerRoomModuleWorldData extends SavedData {

    private static final String DATA_NAME = "gregsteamexpansion_boiler_room_modules";

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

    private BoilerRoomModuleWorldData() {}

    public static BoilerRoomModuleWorldData getOrCreate(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                BoilerRoomModuleWorldData::load,
                BoilerRoomModuleWorldData::new,
                DATA_NAME);
    }

    public static BoilerRoomModuleWorldData load(CompoundTag tag) {
        BoilerRoomModuleWorldData data = new BoilerRoomModuleWorldData();
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

    public static Claim waterSoftenerClaim(BlockPos controller, net.minecraft.core.Direction front) {
        return BoilerRoomModules.claim(controller, front, BoilerRoomModules.WATER_SOFTENER);
    }

    /** Body claims have priority over optional modules and make nearby-controller overlap deterministic. */
    public synchronized void claimBody(Claim body) {
        boolean changed = claims.entrySet().removeIf(entry -> {
            Claim other = entry.getValue();
            return !other.controller().equals(body.controller())
                    && !"__body".equals(other.moduleId())
                    && intersects(body.min(), body.max(), other.min(), other.max());
        });
        Claim previous = claims.put(key(body.controller(), body.moduleId()), body);
        if (changed || !body.equals(previous)) setDirty();
    }

    /** Same controller/type and same controller/face are unique; every complete box is exclusive. */
    public synchronized ClaimResult claim(Claim mine) {
        String mineKey = key(mine.controller(), mine.moduleId());
        for (var entry : claims.entrySet()) {
            Claim other = entry.getValue();
            if (entry.getKey().equals(mineKey)) {
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

    public synchronized void releaseAll(BlockPos controller) {
        boolean removed = claims.entrySet().removeIf(entry -> entry.getValue().controller().equals(controller));
        if (removed) {
            setDirty();
        }
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

    /** Remove claims only after the owning controller's loaded chunk proves them stale. */
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
                    && holder.getMetaMachine() instanceof BoilerRoomMachine)) {
                stale.add(entry.getKey());
            }
        }
        if (stale.isEmpty()) {
            return;
        }
        for (String key : stale) {
            Claim removed = claims.remove(key);
            if (removed != null) {
                GregSteamExpansion.LOGGER.info("Released stale boiler-room {} module claim at {}",
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
