package com.hoshino.gregsteamexpansion.machine.module;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.function.BiPredicate;

public final class ModuleStructure {

    public enum Result {
        VALID,
        MISSING,
        INVALID,
        UNLOADED
    }

    public record Requirement(BlockPos pos, Block block, boolean air) {

        public Requirement {
            pos = pos.immutable();
        }

        public static Requirement solid(BlockPos pos, Block block) {
            return new Requirement(pos, block, false);
        }

        public static Requirement air(BlockPos pos) {
            return new Requirement(pos, Blocks.AIR, true);
        }
    }

    private ModuleStructure() {}

    public static Result validate(ServerLevel level, List<Requirement> requirements) {
        return validate(level, requirements, (requirement, state) -> !state.isAir());
    }

    public static Result validate(ServerLevel level, List<Requirement> requirements,
                                  BiPredicate<Requirement, BlockState> countsAsPresent) {
        for (Requirement requirement : requirements) {
            if (!level.hasChunkAt(requirement.pos())) return Result.UNLOADED;
        }
        boolean present = false;
        boolean valid = true;
        for (Requirement requirement : requirements) {
            BlockState state = level.getBlockState(requirement.pos());
            present |= countsAsPresent.test(requirement, state);
            valid &= requirement.air() ? state.isAir() : state.is(requirement.block());
        }
        if (valid) return Result.VALID;
        return present ? Result.INVALID : Result.MISSING;
    }

    public static void place(ServerLevel level, List<Requirement> requirements) {
        for (Requirement requirement : requirements) {
            level.setBlockAndUpdate(requirement.pos(), requirement.air()
                    ? Blocks.AIR.defaultBlockState() : requirement.block().defaultBlockState());
        }
    }

    public static BlockPos local(BlockPos controller, Direction front, int side, int back, int up) {
        return controller.relative(front.getClockWise(), side).relative(front.getOpposite(), back).above(up);
    }

    public static BlockPos[] orderedBounds(BlockPos first, BlockPos second) {
        return new BlockPos[] {
                new BlockPos(Math.min(first.getX(), second.getX()), Math.min(first.getY(), second.getY()),
                        Math.min(first.getZ(), second.getZ())),
                new BlockPos(Math.max(first.getX(), second.getX()), Math.max(first.getY(), second.getY()),
                        Math.max(first.getZ(), second.getZ()))
        };
    }
}
