package com.hoshino.gregsteamexpansion.machine.multiblock;

import com.gregtechceu.gtceu.common.data.GTBlocks;
import com.hoshino.gregsteamexpansion.terminal.UltimateTerminalModuleProvider;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.List;

/** Fixed ordinary-block geometry of the boiler room's left-side water softener. */
public final class BoilerRoomWaterSoftenerModule {

    public static final String MODULE_ID = BoilerRoomModules.SOFTENER;
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
        return switch (BoilerRoomModules.validate(level, controller, front, tierIndex,
                BoilerRoomModules.WATER_SOFTENER).status()) {
            case VALID -> Result.VALID;
            case MISSING -> Result.MISSING;
            case UNLOADED -> Result.UNLOADED;
            default -> Result.INVALID;
        };
    }

    /** The full 3x7x3 box is exclusive, including every occupied glass/casing position. */
    public static BlockPos[] bounds(BlockPos controller, Direction front) {
        return BoilerRoomModules.WATER_SOFTENER.bounds(controller, front);
    }

    /** The pipe touching the middle of the boiler's left wall is the stable ownership anchor. */
    public static BlockPos anchor(BlockPos controller, Direction front) {
        return BoilerRoomModules.WATER_SOFTENER.anchor(controller, front);
    }

    /** Test/blueprint helper which places exactly the 63 required ordinary blocks. */
    public static void place(ServerLevel level, BlockPos controller, Direction front, int tierIndex) {
        BoilerRoomModules.place(level, controller, front, tierIndex, BoilerRoomModules.WATER_SOFTENER);
    }

    /** Exact tier-aware terminal blueprint for the controller-owned softener. */
    public static List<UltimateTerminalModuleProvider.Requirement> terminalRequirements(
            BlockPos controller, Direction front, int tierIndex) {
        return BoilerRoomModules.terminalRequirements(controller, front, tierIndex,
                BoilerRoomModules.WATER_SOFTENER);
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
