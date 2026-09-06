package com.hoshino.gregsteamexpansion.client.cokeoven;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.hoshino.gregsteamexpansion.GregSteamExpansion;
import com.hoshino.gregsteamexpansion.machine.multiblock.cokeoven.GSECokeOvenMachine;
import com.hoshino.gregsteamexpansion.machine.multiblock.largecokeoven.LargeCokeOvenMachine;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 焦炉砖块已归属探针数据源 (coke-ovens.md 已确认大型焦炉 Jade 与探针信息):
 * 玩家查看已归属于某座焦炉的普通焦炉砖或顶部进料斗砖时显示归属与控制器方向;
 * 未归属的普通焦炉砖保持其原有方块信息, 不新增字段。
 *
 * <p>焦炉砖没有方块实体, 无 Jade 服务端数据通道: 所有权信息由控制器经
 * {@code @DescSynced} 占用盒字段同步到客户端, 玩家实际查看时以被查看方块为
 * 中心做一次有界扫描并缓存 20 tick (按需 + 节流, 不向周围持续广播)。</p>
 */
@Mod.EventBusSubscriber(modid = GregSteamExpansion.MOD_ID, value = Dist.CLIENT)
public final class OwnedBrickClient {

    /** 探针扫描半径: 覆盖大型焦炉控制器到最远进料斗砖的距离。 */
    private static final int SCAN_RADIUS = 8;
    private static final long CACHE_TICKS = 20;

    /** "large" / "regular"。 */
    public record Ownership(String kind, BlockPos controller, boolean structureValid) {}

    private static final Map<Long, Cached> CACHE = new ConcurrentHashMap<>();

    private record Cached(long expireTick, @Nullable Ownership ownership) {}

    private OwnedBrickClient() {}

    /** 查询 (结果缓存 20 tick); ownership 为 null 表示该砖不属于任何焦炉。 */
    public static @Nullable Ownership query(Level level, BlockPos pos) {
        long now = level.getGameTime();
        Cached cached = CACHE.get(pos.asLong());
        if (cached != null && cached.expireTick() > now) {
            return cached.ownership();
        }
        Ownership found = scan(level, pos);
        CACHE.put(pos.asLong(), new Cached(now + CACHE_TICKS, found));
        if (CACHE.size() > 4096) {
            CACHE.clear(); // 防御: 避免长期驻留
        }
        return found;
    }

    private static @Nullable Ownership scan(Level level, BlockPos pos) {
        for (BlockPos p : BlockPos.betweenClosed(pos.offset(-SCAN_RADIUS, -SCAN_RADIUS, -SCAN_RADIUS),
                pos.offset(SCAN_RADIUS, SCAN_RADIUS, SCAN_RADIUS))) {
            MetaMachine machine = MetaMachine.getMachine(level, p);
            if (machine instanceof LargeCokeOvenMachine oven && matchClaimBox(oven.getSyncedClaimBox(), pos)) {
                return new Ownership("large", p.immutable(), oven.isFormed());
            }
            if (machine instanceof GSECokeOvenMachine oven && matchClaimBox(oven.getSyncedClaimBox(), pos)) {
                return new Ownership("regular", p.immutable(), oven.isFormed());
            }
        }
        return null;
    }

    /** 解析控制器的占用盒字段, 判断目标坐标是否在占用盒或附加坐标内。 */
    private static boolean matchClaimBox(String claimBox, BlockPos target) {
        if (claimBox == null || claimBox.isEmpty()) return false;
        String[] parts = claimBox.split("\\|");
        if (parts.length < 3) return false;
        try {
            BlockPos min = BlockPos.of(Long.parseLong(parts[1]));
            BlockPos max = BlockPos.of(Long.parseLong(parts[2]));
            if (target.getX() >= min.getX() && target.getX() <= max.getX() &&
                    target.getY() >= min.getY() && target.getY() <= max.getY() &&
                    target.getZ() >= min.getZ() && target.getZ() <= max.getZ()) {
                return true;
            }
            for (int i = 3; i < parts.length; i++) {
                if (BlockPos.of(Long.parseLong(parts[i])).equals(target)) {
                    return true;
                }
            }
        } catch (NumberFormatException ignored) {
            // 字段损坏: 视为未归属
        }
        return false;
    }
}
