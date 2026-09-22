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

/** Two-level asymmetric coke-brick foundation that raises the large coke oven's parallel cap. */
public final class LargeCokeOvenFurnaceBaseModule {

    public static final String ID = "COKE-D-13";

    public enum Result {
        VALID,
        MISSING,
        INVALID,
        UNLOADED
    }

    private LargeCokeOvenFurnaceBaseModule() {}

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
        BlockPos a = LargeCokeOvenDryQuenchModule.local(controller, front, -3, -3, -2);
        BlockPos b = LargeCokeOvenDryQuenchModule.local(controller, front, 9, 7, -1);
        return new BlockPos[] {
                new BlockPos(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ())),
                new BlockPos(Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ()))
        };
    }

    public static BlockPos anchor(BlockPos controller, Direction front) {
        return LargeCokeOvenDryQuenchModule.local(controller, front, -3, -2, -1);
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
        List<Requirement> result = new ArrayList<>(286);
        Block brick = GTBlocks.CASING_COKE_BRICKS.get();
        for (int side = -3; side <= 9; side++) {
            for (int back = -3; back <= 7; back++) {
                result.add(new Requirement(
                        LargeCokeOvenDryQuenchModule.local(controller, front, side, back, -2),
                        brick, false));
                boolean upperBrick = side <= 7 && back >= -2 && back <= 6;
                result.add(new Requirement(
                        LargeCokeOvenDryQuenchModule.local(controller, front, side, back, -1),
                        upperBrick ? brick : Blocks.AIR, !upperBrick));
            }
        }
        return result;
    }

    private record Requirement(BlockPos pos, Block block, boolean air) {}
}
