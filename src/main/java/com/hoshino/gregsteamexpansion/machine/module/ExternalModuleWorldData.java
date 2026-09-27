package com.hoshino.gregsteamexpansion.machine.module;

import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.hoshino.gregsteamexpansion.GregSteamExpansion;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.saveddata.SavedData;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public abstract class ExternalModuleWorldData extends SavedData {

    public static final String BODY_ID = "__body";

    public record Claim(BlockPos controller, String moduleId, String faceId,
                        BlockPos anchor, BlockPos min, BlockPos max) {

        public Claim {
            controller = controller.immutable();
            anchor = anchor.immutable();
            min = min.immutable();
            max = max.immutable();
        }

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

        private static Claim load(CompoundTag tag, String legacyModuleId, String legacyFaceId) {
            String moduleId = tag.contains("module", Tag.TAG_STRING) ? tag.getString("module") : legacyModuleId;
            String faceId = tag.contains("face", Tag.TAG_STRING) ? tag.getString("face") : legacyFaceId;
            return new Claim(BlockPos.of(tag.getLong("controller")), moduleId, faceId,
                    BlockPos.of(tag.getLong("anchor")),
                    BlockPos.of(tag.getLong("min")), BlockPos.of(tag.getLong("max")));
        }
    }

    public record ClaimResult(boolean success, @Nullable BlockPos conflictingController) {}

    private final Map<String, Claim> claims = new ConcurrentHashMap<>();
    private final Class<? extends MetaMachine> controllerType;

    protected ExternalModuleWorldData(Class<? extends MetaMachine> controllerType) {
        this.controllerType = controllerType;
    }

    protected final void loadClaims(CompoundTag tag) {
        loadClaims(tag, "", "");
    }

    protected final void loadClaims(CompoundTag tag, String legacyModuleId, String legacyFaceId) {
        ListTag list = tag.getList("claims", Tag.TAG_COMPOUND);
        for (int index = 0; index < list.size(); index++) {
            Claim claim = Claim.load(list.getCompound(index), legacyModuleId, legacyFaceId);
            claims.put(key(claim.controller(), claim.moduleId()), claim);
        }
    }

    @Override
    public final @NotNull CompoundTag save(@NotNull CompoundTag tag) {
        ListTag list = new ListTag();
        claims.values().forEach(claim -> list.add(claim.save()));
        tag.put("claims", list);
        return tag;
    }

    public final synchronized void claimBody(Claim body) {
        boolean changed = claims.entrySet().removeIf(entry -> {
            Claim other = entry.getValue();
            return !other.controller().equals(body.controller())
                    && !BODY_ID.equals(other.moduleId())
                    && intersects(body.min(), body.max(), other.min(), other.max());
        });
        Claim previous = claims.put(key(body.controller(), body.moduleId()), body);
        if (changed || !body.equals(previous)) setDirty();
    }

    public final synchronized ClaimResult claim(Claim mine) {
        String mineKey = key(mine.controller(), mine.moduleId());
        for (var entry : claims.entrySet()) {
            if (!entry.getKey().equals(mineKey) && conflicts(mine, entry.getValue())) {
                return new ClaimResult(false, entry.getValue().controller());
            }
        }
        Claim previous = claims.put(mineKey, mine);
        if (!mine.equals(previous)) setDirty();
        return new ClaimResult(true, null);
    }

    protected boolean conflicts(Claim mine, Claim other) {
        return mine.controller().equals(other.controller()) && mine.faceId().equals(other.faceId())
                || intersects(mine.min(), mine.max(), other.min(), other.max());
    }

    @Nullable
    protected final BlockPos findForeignConflict(BlockPos controller, BlockPos min, BlockPos max, int clearance) {
        BlockPos expandedMin = min.offset(-clearance, -clearance, -clearance);
        BlockPos expandedMax = max.offset(clearance, clearance, clearance);
        for (Claim other : claims.values()) {
            if (!other.controller().equals(controller)
                    && intersects(expandedMin, expandedMax, other.min(), other.max())) {
                return other.controller();
            }
        }
        return null;
    }

    public final synchronized void release(BlockPos controller, String moduleId) {
        if (claims.remove(key(controller, moduleId)) != null) setDirty();
    }

    public final synchronized void releaseAll(BlockPos controller) {
        if (claims.entrySet().removeIf(entry -> entry.getValue().controller().equals(controller))) {
            setDirty();
        }
    }

    public final boolean owns(BlockPos controller, BlockPos anchor) {
        return claims.values().stream().anyMatch(claim -> claim.controller().equals(controller)
                && claim.anchor().equals(anchor));
    }

    @Nullable
    public final Claim claimOf(BlockPos controller, String moduleId) {
        return claims.get(key(controller, moduleId));
    }

    private static String key(BlockPos controller, String moduleId) {
        return controller.asLong() + ":" + moduleId;
    }

    protected static boolean intersects(BlockPos firstMin, BlockPos firstMax, BlockPos secondMin, BlockPos secondMax) {
        return firstMin.getX() <= secondMax.getX() && firstMax.getX() >= secondMin.getX()
                && firstMin.getY() <= secondMax.getY() && firstMax.getY() >= secondMin.getY()
                && firstMin.getZ() <= secondMax.getZ() && firstMax.getZ() >= secondMin.getZ();
    }

    public final synchronized void pruneStaleClaims(LevelChunk chunk) {
        ChunkPos chunkPos = chunk.getPos();
        List<String> stale = new ArrayList<>();
        for (var entry : claims.entrySet()) {
            BlockPos controller = entry.getValue().controller();
            if (!chunkPos.equals(new ChunkPos(controller))) continue;
            var blockEntity = chunk.getBlockEntity(controller);
            if (!(blockEntity instanceof IMachineBlockEntity holder
                    && controllerType.isInstance(holder.getMetaMachine()))) {
                stale.add(entry.getKey());
            }
        }
        if (stale.isEmpty()) return;
        for (String key : stale) {
            Claim removed = claims.remove(key);
            if (removed != null) {
                GregSteamExpansion.LOGGER.info("Released stale {} module {} claim at {}",
                        controllerType.getSimpleName(), removed.moduleId(), removed.controller().toShortString());
            }
        }
        setDirty();
    }
}
