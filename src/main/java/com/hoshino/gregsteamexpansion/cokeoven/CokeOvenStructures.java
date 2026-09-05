package com.hoshino.gregsteamexpansion.cokeoven;

import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.pattern.BlockPattern;
import com.gregtechceu.gtceu.api.pattern.FactoryBlockPattern;
import com.gregtechceu.gtceu.api.pattern.MultiblockShapeInfo;
import com.gregtechceu.gtceu.api.pattern.Predicates;
import com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate;
import com.gregtechceu.gtceu.api.pattern.util.RelativeDirection;
import com.gregtechceu.gtceu.common.data.GTBlocks;
import com.gregtechceu.gtceu.common.data.GTMachines;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;

import java.util.List;

/**
 * 普通焦炉结构 (coke-ovens.md 普通焦炉结构 / 结构预览与终端自动搭建):
 * 保持 GTCEu 7.5.3 原有固定 3×3×3 与 `0–5` 个焦炉仓规则, 唯一变化是内部中心
 * 空气格正下方的底层几何中心 (`B`) 固定使用 `gtceu:coke_oven_bricks`,
 * 不可用焦炉仓替换。
 *
 * <p>图案坐标映射 (默认 start() = LEFT/UP/FRONT, 控制器朝北时): 第 0 行字符串
 * 对应世界下层, 通道索引沿控制器背面增长; 控制器 `Y` 位于正面中央高度,
 * 空气格 `#` 位于结构几何中心。</p>
 */
public final class CokeOvenStructures {

    private CokeOvenStructures() {}

    public static BlockPattern createPattern(MultiblockMachineDefinition definition) {
        return FactoryBlockPattern.start(RelativeDirection.LEFT, RelativeDirection.UP, RelativeDirection.FRONT)
                .aisle("XXX", "XXX", "XXX")
                .aisle("XBX", "X#X", "XXX")
                .aisle("XXX", "XYX", "XXX")
                .where('X', shellCandidates())
                .where('#', Predicates.air())
                .where('B', Predicates.blocks(GTBlocks.CASING_COKE_BRICKS.get()))
                .where('Y', Predicates.controller(Predicates.blocks(definition.getBlock())))
                .build();
    }

    /** 24 个可替换外壳位置: 焦炉砖, 或总计最多 5 个可配置焦炉仓。 */
    private static TraceabilityPredicate shellCandidates() {
        return Predicates.blocks(GTBlocks.CASING_COKE_BRICKS.get())
                .or(Predicates.blocks(GTMachines.COKE_OVEN_HATCH.getBlock()).setMaxGlobalLimited(5));
    }

    /**
     * 结构预览 (coke-ovens.md 结构预览): 第一项为最小合法结构 (全焦炉砖,
     * 不默认放置焦炉仓), 第二项为分别展示三种模式焦炉仓的三仓完整自动化代表
     * 布局 (仅示例, 不强制位置或模式)。
     *
     * <p>LDLib builder 按 `blocks[字符][行][aisle]` 生成数组, 预览再按
     * `blocks[x][y][z]` 放置: aisle 索引 → z (自北向南), 行索引 → y
     * (自下而上), 字符索引 → x (自西向东)。
     * 全部机器朝向正面朝外。</p>
     */
    public static List<MultiblockShapeInfo> shapeInfos(MultiblockMachineDefinition definition) {
        String[] frontWall = {"BBB", "BKB", "BBB"};
        // z1 中间切片: 几何中心留空, 底层中心为焦炉砖。
        String[] middleSliceMinimal = {"BBB", "BAB", "BBB"};
        String[] backWall = {"BBB", "BBB", "BBB"};

        MultiblockShapeInfo minimal = MultiblockShapeInfo.builder()
                .aisle(frontWall)
                .aisle(middleSliceMinimal)
                .aisle(backWall)
                .where('B', GTBlocks.CASING_COKE_BRICKS.get())
                .where('A', Blocks.AIR.defaultBlockState())
                .where('K', definition, Direction.NORTH)
                .build();

        // 三仓代表布局: 左侧物品输入 (朝西)、右侧物品输出 (朝东)、背面流体输出 (朝南)。
        MultiblockShapeInfo automated = MultiblockShapeInfo.builder()
                .aisle(frontWall)
                .aisle("BBB", "IAO", "BBB")
                .aisle("BBB", "BFB", "BBB")
                .where('B', GTBlocks.CASING_COKE_BRICKS.get())
                .where('A', Blocks.AIR.defaultBlockState())
                .where('I', GTMachines.COKE_OVEN_HATCH, Direction.WEST)
                .where('O', GTMachines.COKE_OVEN_HATCH, Direction.EAST)
                .where('F', GTMachines.COKE_OVEN_HATCH, Direction.SOUTH)
                .where('K', definition, Direction.NORTH)
                .build();

        return List.of(minimal, automated);
    }
}
