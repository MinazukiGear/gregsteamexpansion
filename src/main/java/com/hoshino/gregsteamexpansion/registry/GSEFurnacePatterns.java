package com.hoshino.gregsteamexpansion.registry;

import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;
import com.gregtechceu.gtceu.api.pattern.BlockPattern;
import com.gregtechceu.gtceu.api.pattern.FactoryBlockPattern;
import com.gregtechceu.gtceu.api.pattern.MultiblockShapeInfo;
import com.gregtechceu.gtceu.api.pattern.Predicates;
import com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate;
import com.gregtechceu.gtceu.api.pattern.util.RelativeDirection;
import com.gregtechceu.gtceu.common.data.GTBlocks;
import com.gregtechceu.gtceu.common.data.GTMachines;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;

/**
 * Variable-size patterns for the 大型蓄热蒸汽熔炉 (large-heat-storage-steam-furnace.md
 * 炉体体积与并行 / 四周侧壁): three outer widths (7/11/15) with a repeatable middle
 * section covering every height from 6 to 18.
 *
 * <p>Aisles are horizontal slices stacked bottom-up
 * ({@code start(LEFT, FRONT, UP)}), so the aisle repetitions express the height
 * range exactly like the structure-preview "repeatable middle layer ×0–12" rule:
 * bottom + 3..15 middles + exhaust layer + top = 6..18 blocks.</p>
 *
 * <p>Slice symbols: {@code #} removed corner (air), {@code S} solid machine
 * casing border, {@code C} controller, {@code F} bronze firebox, {@code M}
 * steam machine casing or replaceable interface, {@code X} like {@code M} plus
 * the steam exhaust hatch (exactly one globally), {@code K} bricked wrought
 * iron corner columns, {@code R} firebricks, {@code P} bronze pipe casing,
 * {@code space} any block (free interior).</p>
 */
public final class GSEFurnacePatterns {

    public static final int[] WIDTHS = {7, 11, 15};
    public static final int MIN_MIDDLE_REPEATS = 3;
    public static final int MAX_MIDDLE_REPEATS = 15;

    private GSEFurnacePatterns() {}

    /**
     * Builds the pattern for one outer width. The exhaust hatch slice sits
     * directly below the top slice (顶部倒数第 2 层) and is the only slice whose
     * predicate accepts it, with a global exactly-one limit.
     */
    public static BlockPattern create(MultiblockMachineDefinition definition, int width) {
        TraceabilityPredicate interfaces = interfaces();
        return FactoryBlockPattern
                .start(RelativeDirection.LEFT, RelativeDirection.FRONT, RelativeDirection.UP)
                .aisle(bottomSlice(width))
                .aisleRepeatable(MIN_MIDDLE_REPEATS, MAX_MIDDLE_REPEATS, middleSlice(width, false))
                .aisle(middleSlice(width, true))
                .aisle(topSlice(width))
                .where('S', Predicates.blocks(GTBlocks.CASING_STEEL_SOLID.get()))
                .where('F', Predicates.blocks(GTBlocks.FIREBOX_BRONZE.get()))
                .where('M', interfaces)
                .where('X', interfaces.or(exhaustHatch()))
                .where('K', Predicates.blocks(GTBlocks.STEEL_BRICKS_HULL.get()))
                .where('R', Predicates.blocks(GTBlocks.CASING_PRIMITIVE_BRICKS.get()))
                .where('P', Predicates.blocks(GTBlocks.CASING_BRONZE_PIPE.get()))
                .where('C', Predicates.controller(Predicates.blocks(definition.getBlock())))
                .where('#', Predicates.air())
                .build();
    }

    /** Steam machine casing or any replaceable interface (hatches and buses). */
    private static TraceabilityPredicate interfaces() {
        return Predicates.blocks(GTBlocks.CASING_BRONZE_BRICKS.get())
                .or(Predicates.abilities(PartAbility.STEAM))
                .or(Predicates.abilities(PartAbility.IMPORT_FLUIDS))
                .or(Predicates.abilities(PartAbility.IMPORT_ITEMS))
                .or(Predicates.abilities(PartAbility.EXPORT_ITEMS));
    }

    private static TraceabilityPredicate exhaustHatch() {
        return Predicates.blocks(GSEMachines.STEAM_EXHAUST_HATCH.getBlock())
                .setMinGlobalLimited(1)
                .setMaxGlobalLimited(1);
    }

    /** Bottom layer: steel border with the controller, bronze firebox floor. */
    private static String[] bottomSlice(int width) {
        String[] rows = new String[width];
        for (int b = 0; b < width; b++) {
            StringBuilder row = new StringBuilder();
            for (int a = 0; a < width; a++) {
                if (isCorner(a, b, width)) {
                    row.append('#');
                } else if (isBorder(a, b, width)) {
                    row.append(a == width / 2 && b == width - 1 ? 'C' : 'S');
                } else {
                    row.append('F');
                }
            }
            rows[b] = row.toString();
        }
        return rows;
    }

    /**
     * Interior layer: two-thick wall (firebrick inner layer, steam-casing outer
     * layer with wrought-iron columns beside the removed corners), free
     * interior with the mandatory bronze pipe centre column.
     */
    private static String[] middleSlice(int width, boolean exhaustLayer) {
        String[] rows = new String[width];
        for (int b = 0; b < width; b++) {
            StringBuilder row = new StringBuilder();
            for (int a = 0; a < width; a++) {
                if (isCorner(a, b, width)) {
                    row.append('#');
                } else if (isBorder(a, b, width)) {
                    boolean besideCorner = a == 1 || a == width - 2 || b == 1 || b == width - 2;
                    row.append(besideCorner ? 'K' : exhaustLayer ? 'X' : 'M');
                } else if (a == 1 || a == width - 2 || b == 1 || b == width - 2) {
                    row.append('R');
                } else if (a == width / 2 && b == width / 2) {
                    row.append('P');
                } else {
                    row.append(' ');
                }
            }
            rows[b] = row.toString();
        }
        return rows;
    }

    /** Top layer: steel border, steam machine casing ceiling. */
    private static String[] topSlice(int width) {
        String[] rows = new String[width];
        for (int b = 0; b < width; b++) {
            StringBuilder row = new StringBuilder();
            for (int a = 0; a < width; a++) {
                if (isCorner(a, b, width)) {
                    row.append('#');
                } else if (isBorder(a, b, width)) {
                    row.append('S');
                } else {
                    row.append('M');
                }
            }
            rows[b] = row.toString();
        }
        return rows;
    }

    /**
     * JEI/EMI 结构预览与终端自动搭建: 该横截面的 6 格高基础结构
     * (large-heat-storage-steam-furnace.md 结构预览与终端自动搭建), with one
     * representative input bus, output bus, steam hatch and the mandatory
     * exhaust hatch on the second layer from the top.
     *
     * <p>ShapeInfo axis convention (must match {@code PatternPreviewWidget},
     * which places aisles along world X, rows along world Y (vertical) and
     * chars along world Z): shape aisles = the pattern's front/back rows (back
     * first), shape rows = the vertical layers (bottom first), shape chars =
     * the pattern's left/right chars. The controller faces EAST out of the
     * front face (the last aisle). The steam hatch is this mod's supply hatch
     * — the legacy upstream hatch no longer satisfies {@code PartAbility.STEAM}
     * (steam-crushers.md / machines-and-hatches.md 禁用范围).</p>
     */
    public static MultiblockShapeInfo createShapeInfo(MultiblockMachineDefinition definition, int width) {
        int height = 6;
        char[][][] grid = new char[height][width][width];
        for (int y = 0; y < height; y++) {
            String[] slice;
            if (y == 0) slice = bottomSlice(width);
            else if (y == height - 1) slice = topSlice(width);
            else slice = middleSlice(width, y == height - 2);
            for (int b = 0; b < width; b++) {
                for (int a = 0; a < width; a++) {
                    grid[y][b][a] = slice[b].charAt(a);
                }
            }
        }
        // Representative interfaces on outer main-body cells of layers 1..4.
        grid[1][width / 2][0] = 'I';
        grid[1][width / 2][width - 1] = 'O';
        grid[2][width - 1][width / 2] = 'H';
        grid[height - 2][0][width / 2] = 'E';

        var builder = MultiblockShapeInfo.builder();
        // Transposed placement: one shape aisle per pattern front/back row, so
        // the preview world stands the furnace upright.
        for (int b = 0; b < width; b++) {
            String[] rows = new String[height];
            for (int y = 0; y < height; y++) {
                rows[y] = new String(grid[y][b]);
            }
            builder.aisle(rows);
        }
        return builder
                .where('S', GTBlocks.CASING_STEEL_SOLID.get())
                .where('F', GTBlocks.FIREBOX_BRONZE.get())
                .where('M', GTBlocks.CASING_BRONZE_BRICKS.get())
                .where('K', GTBlocks.STEEL_BRICKS_HULL.get())
                .where('R', GTBlocks.CASING_PRIMITIVE_BRICKS.get())
                .where('P', GTBlocks.CASING_BRONZE_PIPE.get())
                .where('C', definition, Direction.EAST)
                .where('I', GTMachines.STEAM_IMPORT_BUS, Direction.EAST)
                .where('O', GTMachines.STEAM_EXPORT_BUS, Direction.EAST)
                .where('H', GSEMachines.STEAM_SUPPLY_HATCH, Direction.EAST)
                .where('E', GSEMachines.STEAM_EXHAUST_HATCH, Direction.EAST)
                .where('#', net.minecraft.world.level.block.Blocks.AIR.defaultBlockState())
                .where(' ', net.minecraft.world.level.block.Blocks.AIR.defaultBlockState())
                .build();
    }

    private static boolean isCorner(int a, int b, int width) {
        return (a == 0 || a == width - 1) && (b == 0 || b == width - 1);
    }

    private static boolean isBorder(int a, int b, int width) {
        return a == 0 || a == width - 1 || b == 0 || b == width - 1;
    }
}
