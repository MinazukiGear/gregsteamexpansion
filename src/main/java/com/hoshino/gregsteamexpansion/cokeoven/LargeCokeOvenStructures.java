package com.hoshino.gregsteamexpansion.cokeoven;

import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.pattern.BlockPattern;
import com.gregtechceu.gtceu.api.pattern.FactoryBlockPattern;
import com.gregtechceu.gtceu.api.pattern.MultiblockShapeInfo;
import com.gregtechceu.gtceu.api.pattern.Predicates;
import com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate;
import com.gregtechceu.gtceu.api.pattern.util.RelativeDirection;
import com.gregtechceu.gtceu.common.data.GTBlocks;
import com.hoshino.gregsteamexpansion.registry.GSEMachines;
import com.lowdragmc.lowdraglib.utils.BlockInfo;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.List;

/**
 * 大型焦炉结构 (coke-ovens.md 已确认逐层结构与基础方块计数): 完整包围范围
 * `7×7×5` = 主体 `7×5×5` (宽×高×深) + 两层砖制进料斗轮廓。控制器固定在主体
 * 最底层正面水平中心; 三个 `1×3×3` 炉室 (共 27 格固定空气) 由两面完整纵向
 * 隔墙 (18 块焦炉砖) 分隔; 5 个候选接口位置 (`I`, 主体第 2 层) 可用大型焦炉仓
 * 替换焦炉砖, 共同允许 3–5 个仓; 料斗 = 第 6 层单格底颈 + 第 7 层八砖一空气
 * 环形斗口。默认布局 156 块焦炉砖, 使用 n 个仓时实际需要 156−n 块。
 *
 * <p>图案坐标约定: {@code start(BACK, UP, LEFT)}, 控制器朝北时 —— 字符索引沿
 * 深度增长 (0 = 正面墙, 4 = 背面墙, 世界 +Z), 行索引自下而上 (0 = 主体第 1 层),
 * 通道索引沿宽度增长。逐层图 (行 = 正面→背面, 字符 = 玩家正面视角左→右) 在
 * {@link #buildPatternGrid} 中转置为图案。</p>
 */
public final class LargeCokeOvenStructures {

    private LargeCokeOvenStructures() {}

    /**
     * 主体/料斗的完整 7 层逐层图 (coke-ovens.md 已确认逐层结构图), 层序自下而上。
     * 每个文本块为一层: 5 行 = 深度 (正面→背面), 每行 7 字符 = 宽度 (正面视角左→右)。
     */
    private static final String[] LAYER_BLOCKS = {
            // 主体第 1 层: 正面行中央为控制器
            """
                    BBBCBBB
                    BBBBBBB
                    BBBBBBB
                    BBBBBBB
                    BBBBBBB""",
            // 主体第 2 层: 5 个候选接口位置 (背面 3 + 左右侧各 1)
            """
                    BBBBBBB
                    BAWAWAB
                    IAWAWAI
                    BAWAWAB
                    BIBIBIB""",
            // 主体第 3 层
            """
                    BBBBBBB
                    BAWAWAB
                    BAWAWAB
                    BAWAWAB
                    BBBBBBB""",
            // 主体第 4 层
            """
                    BBBBBBB
                    BAWAWAB
                    BAWAWAB
                    BAWAWAB
                    BBBBBBB""",
            // 主体第 5 层: 顶面全封
            """
                    BBBBBBB
                    BBBBBBB
                    BBBBBBB
                    BBBBBBB
                    BBBBBBB""",
            // 第 6 层: 单格底颈 (宽度第 4 格、深度第 3 格)
            """
                    .......
                    .......
                    ...B...
                    .......
                    .......""",
            // 第 7 层: 八砖一空气环形斗口 (宽度 3–5、深度 2–4)
            """
                    .......
                    ..BBB..
                    ..BAB..
                    ..BBB..
                    ......."""
    };

    private static String layerRow(int layer, int depthRow) {
        return LAYER_BLOCKS[layer].strip().split("\n")[depthRow].strip();
    }

    private static final int LAYER_COUNT = LAYER_BLOCKS.length; // 7 (主体 5 + 料斗 2)
    private static final int DEPTH = 5;
    private static final int WIDTH = 7;

    /** 供控制器的逐格诊断: 结构总层数 (7)。 */
    public static int layerCount() {
        return LAYER_COUNT;
    }

    /** 供控制器的逐格诊断: 深度 (5)。 */
    public static int depth() {
        return DEPTH;
    }

    /** 供控制器的逐格诊断: 宽度 (7)。 */
    public static int width() {
        return WIDTH;
    }

    /** 供控制器的逐格诊断: 第 layer 层、深度 d、宽度 w 的图案符号 (含 '.')。 */
    public static char symbolAt(int layer, int depthRow, int widthCol) {
        return layerRow(layer, depthRow).charAt(widthCol);
    }

    /**
     * 把逐层图转置为图案通道: 通道 = 宽度列 (7), 通道内行 = 层 (7, 自下而上),
     * 行内字符 = 深度 (5, 正面→背面)。`.` (非结构坐标) 转为空格 = 任意方块且
     * 不属于结构。
     */
    private static String[][] buildPatternGrid() {
        String[][] aisles = new String[WIDTH][];
        for (int w = 0; w < WIDTH; w++) {
            String[] rows = new String[LAYER_COUNT];
            for (int layer = 0; layer < LAYER_COUNT; layer++) {
                StringBuilder sb = new StringBuilder();
                for (int d = 0; d < DEPTH; d++) {
                    char c = layerRow(layer, d).charAt(w);
                    sb.append(c == '.' ? ' ' : c);
                }
                rows[layer] = sb.toString();
            }
            aisles[w] = rows;
        }
        return aisles;
    }

    public static BlockPattern createPattern(MultiblockMachineDefinition definition) {
        FactoryBlockPattern factory = FactoryBlockPattern.start(
                RelativeDirection.BACK, RelativeDirection.UP, RelativeDirection.LEFT);
        for (String[] rows : buildPatternGrid()) {
            factory.aisle(rows);
        }
        // 候选接口位置: 焦炉砖, 或大型焦炉仓 (全局最多 5 个; 3–5 总数与三模式
        // 配额由成型后校验补齐)。
        TraceabilityPredicate candidates = Predicates.blocks(GTBlocks.CASING_COKE_BRICKS.get())
                .or(Predicates.blocks(GSEMachines.LARGE_COKE_OVEN_HATCH.getBlock()).setMaxGlobalLimited(5));
        return factory
                .where('B', Predicates.blocks(GTBlocks.CASING_COKE_BRICKS.get()))
                .where('W', Predicates.blocks(GTBlocks.CASING_COKE_BRICKS.get()))
                .where('I', candidates)
                .where('A', Predicates.air())
                .where('C', Predicates.controller(Predicates.blocks(definition.getBlock())))
                .where(' ', Predicates.any())
                .build();
    }

    //////////////////////////////////////
    // ******** 结构预览 ********//
    //////////////////////////////////////

    /**
     * 结构预览 (coke-ovens.md 已确认结构预览): 基础结构预览 = 无仓最小布局
     * (候选位以焦炉砖填充); 自动化代表预览 = 背面三个候选位分别展示物品输入 /
     * 固体输出 / 流体输出模式仓、两个侧面候选位保持焦炉砖 (仅示例, 不强制)。
     *
     * <p>ShapeInfo 约定: aisle 索引 → 深度 z (正面墙 z=0), 行 → 高度 y (自下
     * 而上), 字符 → 世界 X; 逐层图宽度字符 (正面视角左→右) 反序映射到 X
     * (朝北时正面视角左侧 = 世界 +X)。全部机器正面朝结构外部。</p>
     */
    public static List<MultiblockShapeInfo> shapeInfos(MultiblockMachineDefinition definition) {
        MultiblockShapeInfo basic = buildShapeInfo(definition, null);
        // 背面三个候选位 (宽度第 2/4/6 格) 分别放置物品输入 / 固体输出 / 流体输出。
        java.util.Map<Integer, Character> backHatches = new java.util.HashMap<>();
        backHatches.put(1, 'I');
        backHatches.put(3, 'O');
        backHatches.put(5, 'F');
        MultiblockShapeInfo automated = buildShapeInfo(definition, backHatches);
        return List.of(basic, automated);
    }

    /**
     * @param backHatches 背面墙 (最后一行深度) 上放置大型焦炉仓的宽度列 → 预览
     *                    符号; null 表示基础预览 (全部候选位以焦炉砖填充)。
     */
    private static MultiblockShapeInfo buildShapeInfo(MultiblockMachineDefinition definition,
                                                      java.util.Map<Integer, Character> backHatches) {
        int backDepth = DEPTH - 1;
        var builder = MultiblockShapeInfo.builder();
        for (int d = 0; d < DEPTH; d++) {
            String[] rows = new String[LAYER_COUNT];
            for (int layer = 0; layer < LAYER_COUNT; layer++) {
                StringBuilder sb = new StringBuilder();
                for (int x = 0; x < WIDTH; x++) {
                    // 世界 X 自西向东 = 逐层图宽度自右向左 (正面视角左→右反序)。
                    int w = WIDTH - 1 - x;
                    char c = layerRow(layer, d).charAt(w);
                    if (c == 'I') {
                        // 候选位: 代表布局的背面候选位放仓, 其余 (含侧面) 用焦炉砖。
                        Character hatch = backHatches != null && d == backDepth ? backHatches.get(w) : null;
                        c = hatch != null ? hatch : 'B';
                    }
                    sb.append(c);
                }
                rows[layer] = sb.toString();
            }
            builder.aisle(rows);
        }
        builder
                .where('B', GTBlocks.CASING_COKE_BRICKS.get())
                .where('W', GTBlocks.CASING_COKE_BRICKS.get())
                .where('I', GSEMachines.LARGE_COKE_OVEN_HATCH, Direction.SOUTH)
                .where('O', GSEMachines.LARGE_COKE_OVEN_HATCH, Direction.SOUTH)
                .where('F', GSEMachines.LARGE_COKE_OVEN_HATCH, Direction.SOUTH)
                .where('A', Blocks.AIR.defaultBlockState())
                .where('C', definition, Direction.NORTH)
                .where('.', BlockInfo.EMPTY);
        return builder.build();
    }

    /** 供后续步骤复用: 逐层图原始行 (调试与文档比对)。 */
    public static List<String> debugLayerRows() {
        List<String> rows = new ArrayList<>();
        for (int layer = 0; layer < LAYER_COUNT; layer++) {
            for (int d = 0; d < DEPTH; d++) {
                rows.add(layerRow(layer, d));
            }
        }
        return rows;
    }
}
