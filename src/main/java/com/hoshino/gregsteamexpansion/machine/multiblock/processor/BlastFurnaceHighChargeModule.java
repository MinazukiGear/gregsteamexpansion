package com.hoshino.gregsteamexpansion.machine.multiblock.processor;

import com.hoshino.gregsteamexpansion.machine.module.ModuleStructure;
import com.hoshino.gregsteamexpansion.machine.module.ModuleStructure.Requirement;
import com.hoshino.gregsteamexpansion.machine.module.ModuleStructure.Result;
import com.hoshino.gregsteamexpansion.registry.GSEProcessorPatterns;
import com.hoshino.gregsteamexpansion.terminal.UltimateTerminalModuleProvider;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.List;

import static com.hoshino.gregsteamexpansion.machine.module.ModuleStructure.local;

/** Fixed ordinary-block geometry of BF-T-10, the blast furnace high-charge tower. */
public final class BlastFurnaceHighChargeModule {

    public static final String ID = "BF-T-10";
    public static final int WIDTH = 5;
    public static final int DEPTH = 5;
    public static final int HEIGHT = 11;
    public static final int CENTER_BACK = 6;
    public static final int BASE_UP = 15;
    public static final int TOP_UP = 25;

    private BlastFurnaceHighChargeModule() {}

    public static Result validate(ServerLevel level, BlockPos controller, Direction front) {
        return ModuleStructure.validate(level, requirements(controller, front),
                (requirement, state) -> !state.is(Blocks.AIR));
    }

    /** Claims the complete 5x5x11 box, including the strict-air skirt around the upper 3x3 stage. */
    public static BlockPos[] bounds(BlockPos controller, Direction front) {
        return ModuleStructure.orderedBounds(
                local(controller, front, -2, CENTER_BACK - 2, BASE_UP),
                local(controller, front, 2, CENTER_BACK + 2, TOP_UP));
    }

    public static BlockPos anchor(BlockPos controller, Direction front) {
        return local(controller, front, 0, CENTER_BACK, BASE_UP);
    }

    public static void place(ServerLevel level, BlockPos controller, Direction front) {
        ModuleStructure.place(level, requirements(controller, front));
    }

    public static List<UltimateTerminalModuleProvider.Requirement> terminalRequirements(
            BlockPos controller, Direction front) {
        return UltimateTerminalModuleProvider.fromStructure(requirements(controller, front));
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
}
