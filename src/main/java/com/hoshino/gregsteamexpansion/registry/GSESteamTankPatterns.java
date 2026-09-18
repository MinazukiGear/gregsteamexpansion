package com.hoshino.gregsteamexpansion.registry;

import com.gregtechceu.gtceu.api.block.IMachineBlock;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.pattern.BlockPattern;
import com.gregtechceu.gtceu.api.pattern.FactoryBlockPattern;
import com.gregtechceu.gtceu.api.pattern.MultiblockShapeInfo;
import com.gregtechceu.gtceu.api.pattern.Predicates;
import com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate;
import com.gregtechceu.gtceu.api.pattern.util.RelativeDirection;
import com.gregtechceu.gtceu.common.data.GCYMBlocks;
import com.gregtechceu.gtceu.common.data.GTBlocks;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;

/** Variable-width, variable-height shell for the Large Steam Tank. */
public final class GSESteamTankPatterns {

    public static final int[] WIDTHS = {3, 5, 7, 9};
    public static final int MIN_HEIGHT = 4;
    public static final int MAX_HEIGHT = 8;
    public static final int MIN_MIDDLE_REPEATS = MIN_HEIGHT - 2;
    public static final int MAX_MIDDLE_REPEATS = MAX_HEIGHT - 2;

    private GSESteamTankPatterns() {}

    public static BlockPattern create(MultiblockMachineDefinition definition, int width) {
        TraceabilityPredicate glass = GSEProcessorPatterns.anyGlass().setMinGlobalLimited(1);
        TraceabilityPredicate valve = Predicates.abilities(GSEPartAbilities.STEAM_TANK_VALVE)
                .setMinGlobalLimited(1);
        return FactoryBlockPattern
                .start(RelativeDirection.LEFT, RelativeDirection.FRONT, RelativeDirection.UP)
                .aisle(bottomSlice(width))
                .aisleRepeatable(MIN_MIDDLE_REPEATS, MAX_MIDDLE_REPEATS, middleSlice(width))
                .aisle(topSlice(width))
                .where('I', Predicates.blocks(GCYMBlocks.CASING_INDUSTRIAL_STEAM.get()))
                .where('B', Predicates.blocks(GTBlocks.CASING_BRONZE_BRICKS.get()))
                .where('G', glass.or(valve))
                .where('C', Predicates.controller(Predicates.blocks(definition.getBlock())))
                .where(' ', Predicates.air())
                .build();
    }

    public static MultiblockShapeInfo createShapeInfo(MultiblockMachineDefinition definition, int width) {
        return createShapeInfo(definition, width, MIN_HEIGHT);
    }

    public static MultiblockShapeInfo createShapeInfo(MultiblockMachineDefinition definition, int width, int height) {
        if (height < MIN_HEIGHT || height > MAX_HEIGHT) {
            throw new IllegalArgumentException("Unsupported steam tank height " + height);
        }
        char[][][] grid = new char[height][width][width];
        for (int y = 0; y < height; y++) {
            String[] slice = y == 0 ? bottomSlice(width) :
                    y == height - 1 ? topSlice(width) : middleSlice(width);
            for (int z = 0; z < width; z++) {
                for (int x = 0; x < width; x++) {
                    grid[y][z][x] = slice[z].charAt(x);
                }
            }
        }
        // One representative valve on the left wall, one layer above the controller.
        grid[1][width / 2][0] = 'V';

        var builder = MultiblockShapeInfo.builder();
        for (int z = width - 1; z >= 0; z--) {
            String[] rows = new String[height];
            for (int y = 0; y < height; y++) {
                StringBuilder row = new StringBuilder(width);
                for (int x = 0; x < width; x++) row.append(grid[y][z][x]);
                rows[y] = row.toString();
            }
            builder.aisle(rows);
        }
        return builder
                .where('I', GCYMBlocks.CASING_INDUSTRIAL_STEAM.get())
                .where('B', GTBlocks.CASING_BRONZE_BRICKS.get())
                .where('G', Blocks.GLASS.defaultBlockState())
                .where('C', definition, Direction.NORTH)
                .where('V', (IMachineBlock) GSEMachines.STEAM_TANK_VALVE.getBlock(), Direction.WEST)
                .where(' ', Blocks.AIR.defaultBlockState())
                .build();
    }

    private static String[] bottomSlice(int width) {
        String[] rows = new String[width];
        for (int z = 0; z < width; z++) {
            StringBuilder row = new StringBuilder(width);
            for (int x = 0; x < width; x++) {
                if (isBorder(x, z, width)) {
                    row.append(z == width - 1 && x == width / 2 ? 'C' : 'I');
                } else {
                    row.append('B');
                }
            }
            rows[z] = row.toString();
        }
        return rows;
    }

    private static String[] middleSlice(int width) {
        String[] rows = new String[width];
        for (int z = 0; z < width; z++) {
            StringBuilder row = new StringBuilder(width);
            for (int x = 0; x < width; x++) {
                if (isCorner(x, z, width)) row.append('I');
                else if (isBorder(x, z, width)) row.append('G');
                else row.append(' ');
            }
            rows[z] = row.toString();
        }
        return rows;
    }

    private static String[] topSlice(int width) {
        String[] rows = new String[width];
        for (int z = 0; z < width; z++) {
            StringBuilder row = new StringBuilder(width);
            for (int x = 0; x < width; x++) {
                row.append(isBorder(x, z, width) ? 'I' : 'B');
            }
            rows[z] = row.toString();
        }
        return rows;
    }

    private static boolean isCorner(int x, int z, int width) {
        return (x == 0 || x == width - 1) && (z == 0 || z == width - 1);
    }

    private static boolean isBorder(int x, int z, int width) {
        return x == 0 || x == width - 1 || z == 0 || z == width - 1;
    }
}
