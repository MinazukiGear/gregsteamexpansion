package com.hoshino.gregsteamexpansion.machine.multiblock.processor;

import com.hoshino.gregsteamexpansion.registry.GSEProcessorPatterns;
import com.hoshino.gregsteamexpansion.terminal.UltimateTerminalModuleProvider;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.List;

/** Fixed ordinary-block geometry of BF-T-10, the blast furnace high-charge tower. */
public final class BlastFurnaceHighChargeModule {

    public static final String ID = "BF-T-10";
    public static final int WIDTH = 5;
    public static final int DEPTH = 5;
    public static final int HEIGHT = 11;
    public static final int CENTER_BACK = 6;
    public static final int BASE_UP = 15;
    public static final int TOP_UP = 25;

    public enum Result {
        VALID,
        MISSING,
        INVALID,
        UNLOADED
    }

    private BlastFurnaceHighChargeModule() {}

    public static Result validate(ServerLevel level, BlockPos controller, Direction front) {
        List<Requirement> requirements = requirements(controller, front);
        for (Requirement requirement : requirements) {
            if (!level.hasChunkAt(requirement.pos())) {
                return Result.UNLOADED;
            }
        }

        int present = 0;
        boolean valid = true;
        for (Requirement requirement : requirements) {
            Block block = level.getBlockState(requirement.pos()).getBlock();
            if (block != Blocks.AIR) {
                present++;
            }
            if (requirement.air()) {
                valid &= level.getBlockState(requirement.pos()).isAir();
            } else {
                valid &= block == requirement.block();
            }
        }
        if (valid) {
            return Result.VALID;
        }
        return present == 0 ? Result.MISSING : Result.INVALID;
    }

    /** Claims the complete 5x5x11 box, including the strict-air skirt around the upper 3x3 stage. */
    public static BlockPos[] bounds(BlockPos controller, Direction front) {
        BlockPos a = local(controller, front, -2, CENTER_BACK - 2, BASE_UP);
        BlockPos b = local(controller, front, 2, CENTER_BACK + 2, TOP_UP);
        return new BlockPos[] {
                new BlockPos(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()),
                        Math.min(a.getZ(), b.getZ())),
                new BlockPos(Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()),
                        Math.max(a.getZ(), b.getZ()))
        };
    }

    public static BlockPos anchor(BlockPos controller, Direction front) {
        return local(controller, front, 0, CENTER_BACK, BASE_UP);
    }

    public static void place(ServerLevel level, BlockPos controller, Direction front) {
        for (Requirement requirement : requirements(controller, front)) {
            level.setBlockAndUpdate(requirement.pos(), requirement.air()
                    ? Blocks.AIR.defaultBlockState()
                    : requirement.block().defaultBlockState());
        }
    }

    public static List<UltimateTerminalModuleProvider.Requirement> terminalRequirements(
            BlockPos controller, Direction front) {
        return requirements(controller, front).stream()
                .map(requirement -> requirement.air()
                        ? UltimateTerminalModuleProvider.Requirement.air(requirement.pos())
                        : UltimateTerminalModuleProvider.Requirement.solid(
                                requirement.pos(), requirement.block()))
                .toList();
    }

    private static List<Requirement> requirements(BlockPos controller, Direction front) {
        List<Requirement> result = new ArrayList<>(WIDTH * DEPTH * HEIGHT);
        Block industrial = GSEProcessorPatterns.industrialSteamCasing();
        Block firebrick = GSEProcessorPatterns.blastBricks();

        // Four solid 5x5 decks: industrial outer ring around a solid 3x3 firebrick core.
        for (int up = BASE_UP; up <= BASE_UP + 3; up++) {
            for (int side = -2; side <= 2; side++) {
                for (int back = CENTER_BACK - 2; back <= CENTER_BACK + 2; back++) {
                    boolean outer = Math.abs(side) == 2 || Math.abs(back - CENTER_BACK) == 2;
                    result.add(solid(controller, front, side, back, up,
                            outer ? industrial : firebrick));
                }
            }
        }

        // Seven centered solid 3x3 decks. The surrounding 5x5 skirt is strict air and belongs
        // to the module claim, preventing another structure from occupying its visual clearance.
        for (int up = BASE_UP + 4; up <= TOP_UP; up++) {
            for (int side = -2; side <= 2; side++) {
                for (int back = CENTER_BACK - 2; back <= CENTER_BACK + 2; back++) {
                    if (Math.abs(side) <= 1 && Math.abs(back - CENTER_BACK) <= 1) {
                        boolean outer = Math.abs(side) == 1 || Math.abs(back - CENTER_BACK) == 1;
                        result.add(solid(controller, front, side, back, up,
                                outer ? industrial : firebrick));
                    } else {
                        result.add(air(controller, front, side, back, up));
                    }
                }
            }
        }
        return result;
    }

    private static Requirement solid(BlockPos controller, Direction front,
                                     int side, int back, int up, Block block) {
        return new Requirement(local(controller, front, side, back, up), block, false);
    }

    private static Requirement air(BlockPos controller, Direction front,
                                   int side, int back, int up) {
        return new Requirement(local(controller, front, side, back, up), Blocks.AIR, true);
    }

    private static BlockPos local(BlockPos controller, Direction front,
                                  int side, int back, int up) {
        Direction rear = front.getOpposite();
        Direction right = front.getClockWise();
        return controller.relative(right, side).relative(rear, back).above(up);
    }

    private record Requirement(BlockPos pos, Block block, boolean air) {}
}
