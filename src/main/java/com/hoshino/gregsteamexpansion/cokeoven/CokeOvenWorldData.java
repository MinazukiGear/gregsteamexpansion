package com.hoshino.gregsteamexpansion.cokeoven;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.hoshino.gregsteamexpansion.GregSteamExpansion;
import com.hoshino.gregsteamexpansion.machine.multiblock.largecokeoven.LargeCokeOvenMachine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 焦炉家族的世界级结构独占与间距注册表 (coke-ovens.md 普通焦炉结构独占 /
 * 已确认结构独占、相邻间距与成型所有权): 普通焦炉独占其 3×3×3 占用坐标;
 * 大型焦炉独占主体 7×5×5 全部 175 坐标 + 顶部进料斗 9 砖 1 空气共 185 坐标。
 * 间距排斥 = 各自完整包围盒 (普通 3×3×3 / 大型 7×7×5) 向六向各扩展一格;
 * 另一普通或大型焦炉的结构占用不得与该范围相交。
 *
 * <p>记录持久化保存, 区块卸载与服务器重启期间不释放; 只有在相关区块加载并确认
 * 结构真正失效, 或控制器被拆除后才释放。陈旧记录 (控制器方块已消失) 由区块
 * 加载事件确认后清理。旧世界迁移冲突按控制器坐标 (X,Y,Z) 升序由最小者胜出。</p>
 *
 * <p>本类同时保存每个维度的一次性迁移标记与存档数据版本。</p>
 */
public final class CokeOvenWorldData extends SavedData {

    private static final String DATA_NAME = "gregsteamexpansion_coke_oven";

    /** 一条结构占用记录。 */
    public static final class Claim {
        /** 结构占用包围盒 (普通: 3×3×3; 大型: 主体 7×5×5)。 */
        public final BlockPos occupiedMin;
        public final BlockPos occupiedMax;
        /** 包围盒之外的结构占用坐标 (大型: 顶部进料斗 9 砖 + 1 固定空气)。 */
        public final LongSet extraCoords;
        /** 间距排斥盒 (完整包围盒向六向扩展一格)。 */
        public final BlockPos spacingMin;
        public final BlockPos spacingMax;
        public final BlockPos controllerPos;
        /** 是否为旧存档迁移自动登记 (可被坐标更小的旧控制器按 (X,Y,Z) 抢占)。 */
        public boolean migrated;

        Claim(BlockPos occupiedMin, BlockPos occupiedMax, LongSet extraCoords,
              BlockPos spacingMin, BlockPos spacingMax, BlockPos controllerPos, boolean migrated) {
            this.occupiedMin = occupiedMin;
            this.occupiedMax = occupiedMax;
            this.extraCoords = extraCoords;
            this.spacingMin = spacingMin;
            this.spacingMax = spacingMax;
            this.controllerPos = controllerPos;
            this.migrated = migrated;
        }

        boolean boxIntersectsOccupied(BlockPos min, BlockPos max) {
            return boxIntersects(occupiedMin, occupiedMax, min, max);
        }

        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putLong("controller", controllerPos.asLong());
            tag.putLong("occMin", occupiedMin.asLong());
            tag.putLong("occMax", occupiedMax.asLong());
            tag.putLong("spaMin", spacingMin.asLong());
            tag.putLong("spaMax", spacingMax.asLong());
            tag.putBoolean("migrated", migrated);
            if (!extraCoords.isEmpty()) {
                ListTag list = new ListTag();
                for (long pos : extraCoords) {
                    list.add(LongTag.valueOf(pos));
                }
                tag.put("extra", list);
            }
            return tag;
        }

        static Claim load(CompoundTag tag) {
            LongSet extra = new LongOpenHashSet();
            ListTag list = tag.getList("extra", Tag.TAG_LONG);
            for (int i = 0; i < list.size(); i++) {
                extra.add(((LongTag) list.get(i)).getAsLong());
            }
            return new Claim(BlockPos.of(tag.getLong("occMin")), BlockPos.of(tag.getLong("occMax")), extra,
                    BlockPos.of(tag.getLong("spaMin")), BlockPos.of(tag.getLong("spaMax")),
                    BlockPos.of(tag.getLong("controller")), tag.getBoolean("migrated"));
        }
    }

    public enum ConflictType {
        /** 与另一多方块结构重叠 (结构占用坐标相交)。 */
        OVERLAP("gregsteamexpansion.coke_oven.status.overlap"),
        /** 与另一焦炉间距不足 (结构占用进入既有排斥范围)。 */
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

    /** 并发容器: 独占记录可能被结构探测线程在竞态窗口读取 (防御性设计)。 */
    private final Map<Long, Claim> claims = new ConcurrentHashMap<>();
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
            Claim claim = Claim.load(entry);
            data.claims.put(claim.controllerPos.asLong(), claim);
        }
        data.hatchModeNoticeSent = tag.getBoolean("hatchModeNoticeSent");
        data.recipeMigrationNoticeSent = tag.getBoolean("recipeMigrationNoticeSent");
        data.dataVersion = tag.getInt("dataVersion"); // 缺失 → 0 = 旧存档
        return data;
    }

    @Override
    public @NotNull CompoundTag save(@NotNull CompoundTag tag) {
        ListTag list = new ListTag();
        claims.values().forEach(claim -> list.add(claim.save()));
        tag.put("claims", list);
        tag.putBoolean("hatchModeNoticeSent", hatchModeNoticeSent);
        tag.putBoolean("recipeMigrationNoticeSent", recipeMigrationNoticeSent);
        tag.putInt("dataVersion", dataVersion);
        return tag;
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

    /** 是否为在本模组焦炉数据建立之前创建的旧存档 (决定是否发送一次性迁移提醒)。 */
    public boolean isLegacySave() {
        return dataVersion < CURRENT_VERSION;
    }

    //////////////////////////////////////
    // ****** 占用/间距几何 ******//
    //////////////////////////////////////

    private static boolean boxIntersects(BlockPos aMin, BlockPos aMax, BlockPos bMin, BlockPos bMax) {
        return aMin.getX() <= bMax.getX() && aMax.getX() >= bMin.getX() &&
                aMin.getY() <= bMax.getY() && aMax.getY() >= bMin.getY() &&
                aMin.getZ() <= bMax.getZ() && aMax.getZ() >= bMin.getZ();
    }

    private static BlockPos expand(BlockPos pos, int x, int y, int z) {
        return pos.offset(x, y, z);
    }

    /** 普通焦炉占用记录: 3×3×3 结构 (控制器位于正面中央高度, 结构向背面延伸两格)。 */
    public static Claim regularClaim(BlockPos controller, Direction front, boolean migrated) {
        BlockPos[] bounds = regularOccupiedBounds(controller, front);
        return new Claim(bounds[0], bounds[1], LongSet.of(),
                expand(bounds[0], -1, -1, -1), expand(bounds[1], 1, 1, 1), controller, migrated);
    }

    public static BlockPos[] regularOccupiedBounds(BlockPos controller, Direction front) {
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

    /**
     * 大型焦炉占用记录: 主体 7×5×5 全部 175 坐标 (控制器位于最底层正面中心,
     * 主体向背面延伸 4 格、向上 4 格、两侧各 3 格) + 顶部进料斗 9 砖 1 空气;
     * 间距排斥 = 完整 7×7×5 包围盒向六向扩展一格。
     */
    public static Claim largeClaim(BlockPos controller, Direction front, boolean migrated) {
        Direction back = front.getOpposite();
        // 主体包围盒: 控制器与背面墙中心连线, 再沿垂直水平轴各扩 3。
        BlockPos backCenterBottom = controller.relative(back, 4);
        int minX = Math.min(controller.getX(), backCenterBottom.getX());
        int maxX = Math.max(controller.getX(), backCenterBottom.getX());
        int minZ = Math.min(controller.getZ(), backCenterBottom.getZ());
        int maxZ = Math.max(controller.getZ(), backCenterBottom.getZ());
        if (front.getAxis() == Direction.Axis.X) {
            minZ -= 3;
            maxZ += 3;
        } else {
            minX -= 3;
            maxX += 3;
        }
        BlockPos occMin = new BlockPos(minX, controller.getY(), minZ);
        BlockPos occMax = new BlockPos(maxX, controller.getY() + 4, maxZ);
        // 顶部进料斗: 第 6 层单格底颈 (宽度第 4、深度第 3) + 第 7 层八砖一空气环口。
        LongSet extra = new LongOpenHashSet();
        BlockPos neck = controller.relative(back, 2).above(5);
        extra.add(neck.asLong());
        BlockPos ringCenter = neck.above();
        extra.add(ringCenter.asLong()); // 固定空气坐标同样由成型机器独占
        // 环形斗口 8 砖 = 3×3 环减中心 (环口在世界 X/Z 平面上与朝向无关)。
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                extra.add(ringCenter.offset(dx, 0, dz).asLong());
            }
        }
        // 间距排斥: 完整 7×7×5 包围盒 (主体 + 料斗两层) 向六向扩展一格。
        BlockPos spaMin = expand(occMin, -1, -1, -1);
        BlockPos spaMax = expand(new BlockPos(occMax.getX(), occMax.getY() + 2, occMax.getZ()), 1, 1, 1);
        return new Claim(occMin, occMax, extra, spaMin, spaMax, controller, migrated);
    }

    //////////////////////////////////////
    // ****** 冲突与登记 ******//
    //////////////////////////////////////

    /** 我的结构占用 (包围盒 + 附加坐标) 是否与对方间距盒相交。 */
    private static boolean occupiedTouchesSpacing(Claim occupied, Claim spacing) {
        if (boxIntersects(occupied.occupiedMin, occupied.occupiedMax, spacing.spacingMin, spacing.spacingMax)) {
            return true;
        }
        for (long pos : occupied.extraCoords) {
            BlockPos p = BlockPos.of(pos);
            if (!boxIntersects(p, p, spacing.spacingMin, spacing.spacingMax)) continue;
            return true;
        }
        return false;
    }

    /** 两方结构占用坐标是否相交 (包围盒相交, 或任一方附加坐标落入对方包围盒/附加集合)。 */
    private static boolean structuresOverlap(Claim a, Claim b) {
        if (boxIntersects(a.occupiedMin, a.occupiedMax, b.occupiedMin, b.occupiedMax)) return true;
        for (long pos : a.extraCoords) {
            BlockPos p = BlockPos.of(pos);
            if (boxIntersects(p, p, b.occupiedMin, b.occupiedMax)) return true;
            if (b.extraCoords.contains(pos)) return true;
        }
        for (long pos : b.extraCoords) {
            BlockPos p = BlockPos.of(pos);
            if (boxIntersects(p, p, a.occupiedMin, a.occupiedMax)) return true;
        }
        return false;
    }

    /**
     * 检查给定占用记录是否与既有记录冲突: 结构坐标相交 → 重叠; 对方结构占用
     * 进入我的间距盒, 或我的结构占用进入对方间距盒 → 间距不足。
     *
     * @param self 已成型但尚未登记的竞争控制器 (迁移期按 (X,Y,Z) 排序比较), 可为 null。
     */
    @Nullable
    public ConflictResult findConflict(ServerLevel level, Claim mine, @Nullable OwnedCokeOven self) {
        for (var entry : claims.entrySet()) {
            Claim other = entry.getValue();
            if (other.controllerPos.equals(mine.controllerPos)) continue;
            ConflictType conflict = null;
            if (structuresOverlap(mine, other)) {
                conflict = ConflictType.OVERLAP;
            } else if (occupiedTouchesSpacing(other, mine) || occupiedTouchesSpacing(mine, other)) {
                conflict = ConflictType.TOO_CLOSE;
            }
            if (conflict != null) {
                if (canStealMigrationClaim(level, other, mine.controllerPos, self)) {
                    continue;
                }
                return new ConflictResult(conflict, other.controllerPos);
            }
        }
        return null;
    }

    /**
     * 旧世界迁移排序规则: 多个原本成型且尚未登记的焦炉发生冲突时, 控制器坐标
     * (X,Y,Z) 从小到大排序, 坐标最小者优先取得占用权。仅当既有记录是迁移自动
     * 登记 (migrated) 且对方控制器坐标更小时才允许抢占。
     */
    private boolean canStealMigrationClaim(ServerLevel level, Claim other, BlockPos self,
                                           @Nullable OwnedCokeOven selfOwner) {
        if (!other.migrated || selfOwner == null) return false;
        if (compareCoords(self, other.controllerPos) >= 0) return false;
        var otherMachine = MetaMachine.getMachine(level, other.controllerPos);
        if (otherMachine instanceof OwnedCokeOven oven) {
            oven.invalidateByOwnershipConflict();
        } else {
            release(other.controllerPos);
        }
        GregSteamExpansion.LOGGER.info(
                "[Coke Oven] Migration ownership resolved: {} wins over {} (coords ascending)",
                self.toShortString(), other.controllerPos.toShortString());
        return true;
    }

    private static int compareCoords(BlockPos a, BlockPos b) {
        int c = Integer.compare(a.getX(), b.getX());
        if (c != 0) return c;
        c = Integer.compare(a.getY(), b.getY());
        if (c != 0) return c;
        return Integer.compare(a.getZ(), b.getZ());
    }

    /** 结构成型时登记占用; 与既有记录冲突时返回失败, 不修改任何记录。 */
    public ClaimResult claim(ServerLevel level, Claim claim, @Nullable OwnedCokeOven self) {
        var conflict = findConflict(level, claim, self);
        if (conflict != null) {
            return new ClaimResult.Failed(conflict);
        }
        claims.put(claim.controllerPos.asLong(), claim);
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

    /** 已登记的占用记录 (无则 null), 供控制器同步占用盒给客户端。 */
    @Nullable
    public Claim claimOf(BlockPos controller) {
        return claims.get(controller.asLong());
    }

    /**
     * 相关区块加载后确认控制器方块已消失的陈旧记录必须释放, 不能仅因区块暂时
     * 不可用而删除 (coke-ovens.md 结构独占保存数据条目)。
     */
    public void pruneStaleClaims(ServerLevel level, ChunkPos chunkPos) {
        LongList stale = null;
        for (var entry : claims.entrySet()) {
            BlockPos controller = entry.getValue().controllerPos;
            if (!chunkPos.equals(new ChunkPos(controller))) continue;
            if (level.isLoaded(controller) &&
                    !(MetaMachine.getMachine(level, controller) instanceof OwnedCokeOven)) {
                if (stale == null) stale = new LongArrayList();
                stale.add(entry.getKey());
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
