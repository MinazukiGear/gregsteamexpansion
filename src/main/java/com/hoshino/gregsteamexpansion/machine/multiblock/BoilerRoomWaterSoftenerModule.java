package com.hoshino.gregsteamexpansion.machine.multiblock;

import com.hoshino.gregsteamexpansion.terminal.UltimateTerminalModuleProvider;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;

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
}
