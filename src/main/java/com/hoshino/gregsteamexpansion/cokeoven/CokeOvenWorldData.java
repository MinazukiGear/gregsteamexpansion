package com.hoshino.gregsteamexpansion.cokeoven;

import com.hoshino.gregsteamexpansion.GregSteamExpansion;
import com.hoshino.gregsteamexpansion.machine.multiblock.cokeoven.GSECokeOvenMachine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 普通焦炉的世界级结构独占与间距注册表 (coke-ovens.md 普通焦炉结构独占):
 * 每座成型焦炉独占其 3×3×3 占用坐标, 并把占用包围盒向六向各扩展一格作为间距
 * 排斥范围; 另一座普通焦炉的占用包围盒不得与该范围相交。
 *
 * <p>记录持久化保存, 区块卸载与服务器重启期间不释放; 只有在相关区块加载并确认
 * 结构真正失效, 或控制器被拆除后才释放 ({@link #release})。陈旧记录 (控制器
 * 方块已消失) 由区块加载事件通过 {@link #pruneStaleClaims} 确认后清理。</p>
 *
 * <p>本类同时保存每个维度的一次性迁移标记: 旧焦炉仓模式初始化提醒与旧版进行中
 * 配方取消提醒在每个存档只发送一次。</p>
 */
public final class CokeOvenWorldData extends SavedData {

    private static final String DATA_NAME = "gregsteamexpansion_coke_oven";

    /** 一条结构占用记录: 占用包围盒 (min/max) + 是否为旧存档迁移自动登记。 */
    public static final class Claim {
        public final BlockPos min;
        public final BlockPos max;
        public boolean migrated;

        Claim(BlockPos min, BlockPos max, boolean migrated) {
            this.min = min;
            this.max = max;
            this.migrated = migrated;
        }

        boolean intersects(BlockPos otherMin, BlockPos otherMax) {
            return boxIntersects(min, max, otherMin, otherMax);
        }

        CompoundTag save(long controller) {
            CompoundTag tag = new CompoundTag();
            tag.putLong("controller", controller);
            tag.putLong("min", min.asLong());
            tag.putLong("max", max.asLong());
            tag.putBoolean("migrated", migrated);
            return tag;
        }

        static Claim load(CompoundTag tag) {
            return new Claim(BlockPos.of(tag.getLong("min")), BlockPos.of(tag.getLong("max")),
                    tag.getBoolean("migrated"));
        }
    }

    public enum ConflictType {
        /** 与另一多方块结构重叠 (占用坐标相交)。 */
        OVERLAP("gregsteamexpansion.coke_oven.status.overlap"),
        /** 与另一焦炉间距不足 (占用包围盒进入既有排斥范围)。 */
        TOO_CLOSE("gregsteamexpansion.coke_oven.status.too_close");

        public final String langKey;

        ConflictType(String langKey) {
            this.langKey = langKey;
        }
    }

    public record ConflictResult(ConflictType type, BlockPos otherController) {}

    /** 结构独占登记结果。 */
    public sealed interface ClaimResult {
        record Success(Claim claim) implements ClaimResult {}

        record Failed(ConflictResult conflict) implements ClaimResult {}
    }

    private final Long2ObjectMap<Claim> claims = new Long2ObjectOpenHashMap<>();
    private boolean hatchModeNoticeSent;
    private boolean recipeMigrationNoticeSent;
    /** 存档数据版本: 0 表示本模组焦炉数据建立之前的旧存档; 新建的为 CURRENT。 */
    public static final int CURRENT_VERSION = 1;
    private int dataVersion = CURRENT_VERSION;

    private CokeOvenWorldData() {}

    public static CokeOvenWorldData getOrCreate(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(CokeOvenWorldData::load, CokeOvenWorldData::new, DATA_NAME);
    }

    public static CokeOvenWorldData load(CompoundTag tag) {
        CokeOvenWorldData data = new CokeOvenWorldData();
        ListTag list = tag.getList("claims", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            data.claims.put(entry.getLong("controller"), Claim.load(entry));
        }
        data.hatchModeNoticeSent = tag.getBoolean("hatchModeNoticeSent");
        data.recipeMigrationNoticeSent = tag.getBoolean("recipeMigrationNoticeSent");
        data.dataVersion = tag.getInt("dataVersion"); // 缺失 → 0 = 旧存档
        return data;
    }

    @Override
    public @NotNull CompoundTag save(@NotNull CompoundTag tag) {
        ListTag list = new ListTag();
        claims.long2ObjectEntrySet().forEach(entry -> list.add(entry.getValue().save(entry.getLongKey())));
        tag.put("claims", list);
        tag.putBoolean("hatchModeNoticeSent", hatchModeNoticeSent);
        tag.putBoolean("recipeMigrationNoticeSent", recipeMigrationNoticeSent);
        tag.putInt("dataVersion", dataVersion);
        return tag;
    }

    /** 是否为在本模组焦炉数据建立之前创建的旧存档 (决定是否发送一次性迁移提醒)。 */
    public boolean isLegacySave() {
        return dataVersion < CURRENT_VERSION;
    }

    public boolean isHatchModeNoticeSent() {
        return hatchModeNoticeSent;
    }

    public void markHatchModeNoticeSent() {
        if (!hatchModeNoticeSent) {
            hatchModeNoticeSent = true;
            setDirty();
        }
    }

    public boolean isRecipeMigrationNoticeSent() {
        return recipeMigrationNoticeSent;
    }

    public void markRecipeMigrationNoticeSent() {
        if (!recipeMigrationNoticeSent) {
            recipeMigrationNoticeSent = true;
            setDirty();
        }
    }

    /** 由控制器朝向计算 3×3×3 占用包围盒: 控制器位于正面中央高度, 结构向其背面延伸两格。 */
    public static BlockPos[] occupiedBounds(BlockPos controller, Direction front) {
        var back = front.getOpposite();
        var far = controller.relative(back, 2);
        int minX = Math.min(controller.getX(), far.getX()) - 1;
        int maxX = Math.max(controller.getX(), far.getX()) + 1;
        int minY = controller.getY() - 1;
        int maxY = controller.getY() + 1;
        int minZ = Math.min(controller.getZ(), far.getZ()) - 1;
        int maxZ = Math.max(controller.getZ(), far.getZ()) + 1;
        return new BlockPos[] { new BlockPos(minX, minY, minZ), new BlockPos(maxX, maxY, maxZ) };
    }

    private static boolean boxIntersects(BlockPos aMin, BlockPos aMax, BlockPos bMin, BlockPos bMax) {
        return aMin.getX() <= bMax.getX() && aMax.getX() >= bMin.getX() &&
                aMin.getY() <= bMax.getY() && aMax.getY() >= bMin.getY() &&
                aMin.getZ() <= bMax.getZ() && aMax.getZ() >= bMin.getZ();
    }

    /**
     * 检查给定控制器位置与朝向是否与既有记录冲突: 对方占用与我的占用相交 →
     * 重叠; 对方占用进入我的排斥范围, 或我的占用进入对方排斥范围 → 间距不足。
     *
     * @param self 已成型但尚未登记的竞争控制器 (迁移期按 (X,Y,Z) 排序比较), 可为 null。
     */
    @Nullable
    public ConflictResult findConflict(ServerLevel level, BlockPos controller, Direction front,
                                       @Nullable GSECokeOvenMachine self) {
        BlockPos[] bounds = occupiedBounds(controller, front);
        BlockPos min = bounds[0];
        BlockPos max = bounds[1];
        // 排斥范围: 占用包围盒向六向各扩展一格。
        BlockPos exMin = min.offset(-1, -1, -1);
        BlockPos exMax = max.offset(1, 1, 1);

        for (Long2ObjectMap.Entry<Claim> entry : claims.long2ObjectEntrySet()) {
            long otherPos = entry.getLongKey();
            if (otherPos == controller.asLong()) continue;
            Claim claim = entry.getValue();
            ConflictType conflict = null;
            if (claim.intersects(min, max)) {
                conflict = ConflictType.OVERLAP;
            } else if (claim.intersects(exMin, exMax)) {
                // 对方占用进入我的排斥范围。
                conflict = ConflictType.TOO_CLOSE;
            } else if (boxIntersects(min, max, claim.min.offset(-1, -1, -1), claim.max.offset(1, 1, 1))) {
                // 我的占用进入对方的排斥范围。
                conflict = ConflictType.TOO_CLOSE;
            }
            if (conflict != null) {
                if (canStealMigrationClaim(level, BlockPos.of(otherPos), controller, self)) {
                    continue;
                }
                return new ConflictResult(conflict, BlockPos.of(otherPos));
            }
        }
        return null;
    }

    /**
     * 旧世界迁移排序规则: 多个原本成型且尚未登记的焦炉发生冲突时, 控制器坐标
     * (X,Y,Z) 从小到大排序, 坐标最小者优先取得占用权。仅当既有记录是迁移自动
     * 登记 (migrated) 且对方控制器坐标更小时才允许抢占。
     */
    private boolean canStealMigrationClaim(ServerLevel level, BlockPos other, BlockPos self,
                                           @Nullable GSECokeOvenMachine selfMachine) {
        Claim claim = claims.get(other.asLong());
        if (claim == null || !claim.migrated) return false;
        if (selfMachine == null) return false;
        if (compareCoords(self, other) >= 0) return false;
        // 被抢占方进入结构无效状态并释放记录。
        if (GSECokeOvenMachine.getMachine(level, other) instanceof GSECokeOvenMachine oven) {
            oven.invalidateByOwnershipConflict();
        } else {
            release(other);
        }
        GregSteamExpansion.LOGGER.info(
                "[Coke Oven] Migration ownership resolved: {} wins over {} (coords ascending)",
                self.toShortString(), other.toShortString());
        return true;
    }

    private static int compareCoords(BlockPos a, BlockPos b) {
        int c = Integer.compare(a.getX(), b.getX());
        if (c != 0) return c;
        c = Integer.compare(a.getY(), b.getY());
        if (c != 0) return c;
        return Integer.compare(a.getZ(), b.getZ());
    }

    /**
     * 结构成型时登记占用; 与既有记录冲突时返回失败, 不修改任何记录。
     * 旧存档迁移登记 (migrated=true) 可传入 selfMachine, 允许坐标更小的
     * 已成型旧控制器按 (X,Y,Z) 升序抢占同为难民的迁移记录。
     */
    public ClaimResult claim(ServerLevel level, BlockPos controller, Direction front, boolean migrated,
                             @Nullable GSECokeOvenMachine selfMachine) {
        var conflict = findConflict(level, controller, front, selfMachine);
        if (conflict != null) {
            return new ClaimResult.Failed(conflict);
        }
        BlockPos[] bounds = occupiedBounds(controller, front);
        Claim claim = new Claim(bounds[0], bounds[1], migrated);
        claims.put(controller.asLong(), claim);
        setDirty();
        return new ClaimResult.Success(claim);
    }

    /** 结构真正失效或控制器被拆除时释放占用。 */
    public void release(BlockPos controller) {
        if (claims.remove(controller.asLong()) != null) {
            setDirty();
        }
    }

    public boolean hasClaim(BlockPos controller) {
        return claims.containsKey(controller.asLong());
    }

    /**
     * 相关区块加载后确认控制器方块已消失的陈旧记录必须释放, 不能仅因区块暂时
     * 不可用而删除 (coke-ovens.md 普通焦炉结构独占 保存数据条目)。
     */
    public void pruneStaleClaims(ServerLevel level, ChunkPos chunkPos) {
        long[] stale = null;
        for (Long2ObjectMap.Entry<Claim> entry : claims.long2ObjectEntrySet()) {
            BlockPos controller = BlockPos.of(entry.getLongKey());
            if (!chunkPos.equals(new ChunkPos(controller))) continue;
            if (level.isLoaded(controller) &&
                    !(GSECokeOvenMachine.getMachine(level, controller) instanceof GSECokeOvenMachine)) {
                if (stale == null) stale = new long[] { entry.getLongKey() };
                else {
                    long[] grown = new long[stale.length + 1];
                    System.arraycopy(stale, 0, grown, 0, stale.length);
                    grown[stale.length] = entry.getLongKey();
                    stale = grown;
                }
            }
        }
        if (stale == null) return;
        for (long pos : stale) {
            claims.remove(pos);
            GregSteamExpansion.LOGGER.info("[Coke Oven] Released stale structure claim at {}",
                    BlockPos.of(pos).toShortString());
        }
        setDirty();
    }
}
