package com.hoshino.gregsteamexpansion.gametest;

import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.api.pattern.MultiblockShapeInfo;
import com.gregtechceu.gtceu.common.data.GTBlocks;
import com.hoshino.gregsteamexpansion.GregSteamExpansion;
import com.hoshino.gregsteamexpansion.registry.GSEBlocks;
import com.hoshino.gregsteamexpansion.registry.GSEMachines;
import com.hoshino.gregsteamexpansion.structure.StructureDiagnostics;
import com.hoshino.gregsteamexpansion.structure.StructureProblem;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * 结构诊断测试 (structure-diagnostics.md 九、验收清单)。
 *
 * <p>两类必须能被区分开的失败:
 * <ul>
 *   <li>把结构方块换成空气 ⇒ 位置不匹配 ⇒ {@code MISSING_OR_WRONG};</li>
 *   <li>把蒸汽供给仓换成外壳 ⇒ 位置仍然匹配 (外壳也在候选里), 但
 *       {@code setExactLimit(1)} 的最少数量未满足 ⇒ {@code COUNT_LIMIT}。</li>
 * </ul>
 * 两类都通过 {@code GSEStructureTestUtils.placeShape} 铺出真实预览形状, 所以
 * 断言跑的是玩家搭错时的同一条引擎路径。
 */
@GameTestHolder(GregSteamExpansion.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GSEStructureDiagnosticsTests {

    /** 与 {@code GSEStructureTestUtils#placeShape} 相同的铺放锚点 (结构相对坐标)。 */
    private static final BlockPos ANCHOR = new BlockPos(16, 16, 16);
    /** 蒸汽粉碎机是 3×3×3, 半径 3 足够覆盖全部方块。 */
    private static final int SCAN_RADIUS = 3;

    private GSEStructureDiagnosticsTests() {}

    @GameTest(template = "empty_32x32x32", timeoutTicks = 200)
    public static void missingBlockIsDiagnosed(GameTestHelper helper) {
        MultiblockControllerMachine controller = placeCrusher(helper);
        if (controller == null) {
            return;
        }
        BlockPos broken = findRelative(helper,
                state -> state.getBlock() == GSEBlocks.STEAM_GRINDING_BLOCK.get());
        if (broken == null) {
            helper.fail("Preview shape placed no steam grinding block to break");
            return;
        }
        helper.setBlock(broken, Blocks.AIR);

        StructureProblem problem = recheckAndDiagnose(helper, controller);
        if (problem == null) {
            return;
        }
        helper.assertTrue(problem.kind() == StructureProblem.Kind.MISSING_OR_WRONG,
                "A removed structure block must be diagnosed as MISSING_OR_WRONG, got " + problem.kind());
        // 坐标来自引擎记录的首个失败点。不断言它精确等于被破坏的方块: 匹配器
        // 会按朝向重试, 保留的是最后一次尝试的失败位置 (设计文档 R4)。
        helper.assertTrue(problem.hasPosition(), "MISSING_OR_WRONG must carry a position");
        helper.assertTrue(!problem.expected().isEmpty(),
                "MISSING_OR_WRONG must carry the expected block candidates");
        helper.succeed();
    }

    @GameTest(template = "empty_32x32x32", timeoutTicks = 200)
    public static void missingHatchIsDiagnosedAsCountLimit(GameTestHelper helper) {
        MultiblockControllerMachine controller = placeCrusher(helper);
        if (controller == null) {
            return;
        }
        BlockPos supply = findRelative(helper,
                state -> state.getBlock() == GSEMachines.STEAM_SUPPLY_HATCH.getBlock());
        if (supply == null) {
            helper.fail("Preview shape placed no steam supply hatch to replace");
            return;
        }
        // 外壳本身就在该位置的候选列表里, 所以位置仍然匹配 —— 失败的是
        // "至少 1 个蒸汽供给仓" 这条数量限制, 而不是方块类型。
        helper.setBlock(supply, GTBlocks.CASING_BRONZE_BRICKS.get().defaultBlockState());

        StructureProblem problem = recheckAndDiagnose(helper, controller);
        if (problem == null) {
            return;
        }
        helper.assertTrue(problem.kind() == StructureProblem.Kind.COUNT_LIMIT,
                "A replaced hatch must be diagnosed as COUNT_LIMIT, got " + problem.kind());
        helper.assertTrue(problem.limitType() == 1,
                "The missing hatch is a minimum-count violation (type 1), got " + problem.limitType());
        helper.assertTrue(!problem.expected().isEmpty(),
                "COUNT_LIMIT must carry the candidates that would satisfy the limit");
        helper.succeed();
    }

    /** 铺出粉碎机的首个预览形状, 失败时返回 null 并已标记 fail。 */
    @Nullable
    private static MultiblockControllerMachine placeCrusher(GameTestHelper helper) {
        List<MultiblockShapeInfo> shapes = GSEMachines.STEAM_CRUSHER.getMatchingShapes();
        if (shapes.isEmpty()) {
            helper.fail("Steam crusher registered no preview shape");
            return null;
        }
        return GSEStructureTestUtils.placeShape(helper, GSEMachines.STEAM_CRUSHER, shapes.get(0));
    }

    /**
     * 手动重跑结构检查并抽取诊断。测试里显式重置成型状态并自行调用
     * {@code checkPatternAt}, 而不是等服务端每 4 tick 一次的异步检查, 这样断言
     * 的时序是确定的。
     */
    @Nullable
    private static StructureProblem recheckAndDiagnose(GameTestHelper helper,
                                                       MultiblockControllerMachine controller) {
        // 异步检查可能已经把完整结构判为成型; 诊断对成型机器一律返回空, 所以先复位。
        if (controller.isFormed()) {
            controller.onStructureInvalid();
        }
        helper.assertTrue(!controller.getPattern().checkPatternAt(controller.getMultiblockState(), true),
                "A deliberately damaged structure must not match its pattern");

        Optional<StructureProblem> problem = StructureDiagnostics.describe(controller);
        if (problem.isEmpty()) {
            helper.fail("No structure diagnosis was produced for an invalid structure");
            return null;
        }
        return problem.get();
    }

    @Nullable
    private static BlockPos findRelative(GameTestHelper helper, Predicate<BlockState> match) {
        for (int dx = -SCAN_RADIUS; dx <= SCAN_RADIUS; dx++) {
            for (int dy = -SCAN_RADIUS; dy <= SCAN_RADIUS; dy++) {
                for (int dz = -SCAN_RADIUS; dz <= SCAN_RADIUS; dz++) {
                    BlockPos relative = ANCHOR.offset(dx, dy, dz);
                    BlockState state = helper.getLevel().getBlockState(helper.absolutePos(relative));
                    if (match.test(state)) {
                        return relative;
                    }
                }
            }
        }
        return null;
    }
}
