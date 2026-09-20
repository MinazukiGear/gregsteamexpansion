package com.hoshino.gregsteamexpansion.machine.multiblock;

import com.gregtechceu.gtceu.common.data.GTBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.List;

/** Fixed ordinary-block geometry of the boiler room's left-side water softener. */
public final class BoilerRoomWaterSoftenerModule {

    public static final String MODULE_ID = "water_softener";
    public static final String FACE_ID = "left";
    public static final int OUTWARD_WIDTH = 3;
    public static final int DEPTH = 7;
    public static final int HEIGHT = 3;
    public static final int FRONT_DEPTH_OFFSET = 2;
    public static final int REAR_DEPTH_OFFSET = 8;
    public static final int NEAR_SIDE_OFFSET = -4;
    public static final int FAR_SIDE_OFFSET = -6;
    public static final int BOTTOM_OFFSET = -3;
    public static final int TOP_OFFSET = -1;

    public enum Result {
        VALID,
        MISSING,
        INVALID,
        UNLOADED
    }

    private BoilerRoomWaterSoftenerModule() {}

    public static Result validate(ServerLevel level, BlockPos controller, Direction front, int tierIndex) {
        List<Requirement> requirements = requirements(controller, front, tierIndex);
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
            valid &= block == requirement.block();
        }
        if (valid) {
            return Result.VALID;
        }
        return present == 0 ? Result.MISSING : Result.INVALID;
    }

    /** The full 3x7x3 box is exclusive, including every occupied glass/casing position. */
    public static BlockPos[] bounds(BlockPos controller, Direction front) {
        BlockPos a = local(controller, front, FAR_SIDE_OFFSET, FRONT_DEPTH_OFFSET, BOTTOM_OFFSET);
        BlockPos b = local(controller, front, NEAR_SIDE_OFFSET, REAR_DEPTH_OFFSET, TOP_OFFSET);
        return new BlockPos[] {
                new BlockPos(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()),
                        Math.min(a.getZ(), b.getZ())),
                new BlockPos(Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()),
                        Math.max(a.getZ(), b.getZ()))
        };
    }

    /** The pipe touching the middle of the boiler's left wall is the stable ownership anchor. */
    public static BlockPos anchor(BlockPos controller, Direction front) {
        return local(controller, front, NEAR_SIDE_OFFSET, 5, -2);
    }

    /** Test/blueprint helper which places exactly the 63 required ordinary blocks. */
    public static void place(ServerLevel level, BlockPos controller, Direction front, int tierIndex) {
        for (Requirement requirement : requirements(controller, front, tierIndex)) {
            level.setBlockAndUpdate(requirement.pos(), requirement.block().defaultBlockState());
        }
    }

    private static List<Requirement> requirements(BlockPos controller, Direction front, int tierIndex) {
        List<Requirement> result = new ArrayList<>(63);
        Block casing = casing(tierIndex);
        Block pipe = pipe(tierIndex);

        for (int side = FAR_SIDE_OFFSET; side <= NEAR_SIDE_OFFSET; side++) {
            for (int back = FRONT_DEPTH_OFFSET; back <= REAR_DEPTH_OFFSET; back++) {
                result.add(solid(controller, front, side, back, BOTTOM_OFFSET, casing));
                result.add(solid(controller, front, side, back, TOP_OFFSET, casing));
            }
        }

        for (int back = FRONT_DEPTH_OFFSET; back <= REAR_DEPTH_OFFSET; back++) {
            int depthIndex = back - FRONT_DEPTH_OFFSET;
            result.add(solid(controller, front, FAR_SIDE_OFFSET, back, -2,
                    depthIndex == 0 || depthIndex == DEPTH - 1 ? casing : Blocks.GLASS));
            result.add(solid(controller, front, -5, back, -2,
                    depthIndex == 0 || depthIndex == DEPTH - 1 ? casing : pipe));
            result.add(solid(controller, front, NEAR_SIDE_OFFSET, back, -2,
                    depthIndex == 3 ? pipe : casing));
        }
        return result;
    }

    private static Block casing(int tierIndex) {
        return switch (tierIndex) {
            case BoilerRoomMachine.BRONZE_TIER -> GTBlocks.CASING_BRONZE_BRICKS.get();
            case BoilerRoomMachine.STEEL_TIER -> GTBlocks.CASING_STEEL_SOLID.get();
            case BoilerRoomMachine.TITANIUM_TIER -> GTBlocks.CASING_TITANIUM_STABLE.get();
            case BoilerRoomMachine.TUNGSTENSTEEL_TIER -> GTBlocks.CASING_TUNGSTENSTEEL_ROBUST.get();
            default -> throw new IllegalArgumentException("Unknown boiler-room tier " + tierIndex);
        };
    }

    private static Block pipe(int tierIndex) {
        return switch (tierIndex) {
            case BoilerRoomMachine.BRONZE_TIER -> GTBlocks.CASING_BRONZE_PIPE.get();
            case BoilerRoomMachine.STEEL_TIER -> GTBlocks.CASING_STEEL_PIPE.get();
            case BoilerRoomMachine.TITANIUM_TIER -> GTBlocks.CASING_TITANIUM_PIPE.get();
            case BoilerRoomMachine.TUNGSTENSTEEL_TIER -> GTBlocks.CASING_TUNGSTENSTEEL_PIPE.get();
            default -> throw new IllegalArgumentException("Unknown boiler-room tier " + tierIndex);
        };
    }

    private static Requirement solid(BlockPos controller, Direction front,
                                     int side, int back, int up, Block block) {
        return new Requirement(local(controller, front, side, back, up), block);
    }

    private static BlockPos local(BlockPos controller, Direction front, int side, int back, int up) {
        Direction rear = front.getOpposite();
        Direction right = front.getClockWise();
        return controller.relative(right, side).relative(rear, back).above(up);
    }

    private record Requirement(BlockPos pos, Block block) {}
}
