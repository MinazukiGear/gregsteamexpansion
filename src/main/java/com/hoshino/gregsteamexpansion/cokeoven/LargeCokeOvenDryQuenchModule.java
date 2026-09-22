package com.hoshino.gregsteamexpansion.cokeoven;

import com.gregtechceu.gtceu.common.data.GTBlocks;
import com.hoshino.gregsteamexpansion.terminal.UltimateTerminalModuleProvider;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.List;

/** Fixed ordinary-block geometry of the large coke oven's right-side dry-quenching tower. */
public final class LargeCokeOvenDryQuenchModule {

    public static final String ID = "COKE-R-05";

    public enum Result {
        VALID,
        MISSING,
        INVALID,
        UNLOADED
    }

    private LargeCokeOvenDryQuenchModule() {}

    public static Result validate(ServerLevel level, BlockPos controller, Direction front) {
        List<Requirement> requirements = requirements(controller, front);
        for (Requirement requirement : requirements) {
            if (!level.hasChunkAt(requirement.pos())) return Result.UNLOADED;
        }
        boolean anyBlockPresent = false;
        boolean valid = true;
        for (Requirement requirement : requirements) {
            Block block = level.getBlockState(requirement.pos()).getBlock();
            anyBlockPresent |= !level.getBlockState(requirement.pos()).isAir();
            valid &= requirement.air() ? level.getBlockState(requirement.pos()).isAir()
                    : block == requirement.block();
        }
        if (valid) return Result.VALID;
        return anyBlockPresent ? Result.INVALID : Result.MISSING;
    }

    public static BlockPos[] bounds(BlockPos controller, Direction front) {
        BlockPos a = local(controller, front, 4, 0, 0);
        BlockPos b = local(controller, front, 8, 4, 6);
        return orderedBounds(a, b);
    }

    public static BlockPos anchor(BlockPos controller, Direction front) {
        return local(controller, front, 4, 2, 0);
    }

    public static void place(ServerLevel level, BlockPos controller, Direction front) {
        for (Requirement requirement : requirements(controller, front)) {
            level.setBlockAndUpdate(requirement.pos(), requirement.air()
                    ? Blocks.AIR.defaultBlockState() : requirement.block().defaultBlockState());
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
        List<Requirement> result = new ArrayList<>(175);
        Block brick = GTBlocks.CASING_COKE_BRICKS.get();
        for (int side = 4; side <= 8; side++) {
            for (int back = 0; back <= 4; back++) {
                for (int up = 0; up <= 6; up++) {
                    boolean connector = side <= 5 && back == 2 && up == 0;
                    boolean tower = side >= 6;
                    boolean towerBrick = tower && (up == 0 || up == 6
                            || side == 6 || side == 8 || back == 0 || back == 4);
                    result.add(new Requirement(local(controller, front, side, back, up),
                            towerBrick || connector ? brick : Blocks.AIR,
                            !(towerBrick || connector)));
                }
            }
        }
        return result;
    }

    private static BlockPos[] orderedBounds(BlockPos a, BlockPos b) {
        return new BlockPos[] {
                new BlockPos(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ())),
                new BlockPos(Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ()))
        };
    }

    static BlockPos local(BlockPos controller, Direction front, int side, int back, int up) {
        return controller.relative(front.getClockWise(), side)
                .relative(front.getOpposite(), back).above(up);
    }

    private record Requirement(BlockPos pos, Block block, boolean air) {}
}
