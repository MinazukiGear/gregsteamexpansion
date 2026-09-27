package com.hoshino.gregsteamexpansion.cokeoven;

import com.gregtechceu.gtceu.common.data.GTBlocks;
import com.hoshino.gregsteamexpansion.machine.module.ModuleStructure;
import com.hoshino.gregsteamexpansion.machine.module.ModuleStructure.Requirement;
import com.hoshino.gregsteamexpansion.machine.module.ModuleStructure.Result;
import com.hoshino.gregsteamexpansion.terminal.UltimateTerminalModuleProvider;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.List;

import static com.hoshino.gregsteamexpansion.machine.module.ModuleStructure.local;

/** Two-level asymmetric coke-brick foundation that raises the large coke oven's parallel cap. */
public final class LargeCokeOvenFurnaceBaseModule {

    public static final String ID = "COKE-D-13";

    private LargeCokeOvenFurnaceBaseModule() {}

    public static Result validate(ServerLevel level, BlockPos controller, Direction front) {
        return ModuleStructure.validate(level, requirements(controller, front));
    }

    public static BlockPos[] bounds(BlockPos controller, Direction front) {
        return ModuleStructure.orderedBounds(
                local(controller, front, -3, -3, -2),
                local(controller, front, 9, 7, -1));
    }

    public static BlockPos anchor(BlockPos controller, Direction front) {
        return local(controller, front, -3, -2, -1);
    }

    public static void place(ServerLevel level, BlockPos controller, Direction front) {
        ModuleStructure.place(level, requirements(controller, front));
    }

    public static List<UltimateTerminalModuleProvider.Requirement> terminalRequirements(
            BlockPos controller, Direction front) {
        return UltimateTerminalModuleProvider.fromStructure(requirements(controller, front));
    }

    private static List<Requirement> requirements(BlockPos controller, Direction front) {
        List<Requirement> result = new ArrayList<>(286);
        Block brick = GTBlocks.CASING_COKE_BRICKS.get();
        for (int side = -3; side <= 9; side++) {
            for (int back = -3; back <= 7; back++) {
                result.add(new Requirement(
                        local(controller, front, side, back, -2),
                        brick, false));
                boolean upperBrick = side <= 7 && back >= -2 && back <= 6;
                result.add(new Requirement(
                        local(controller, front, side, back, -1),
                        upperBrick ? brick : Blocks.AIR, !upperBrick));
            }
        }
        return result;
    }
}
