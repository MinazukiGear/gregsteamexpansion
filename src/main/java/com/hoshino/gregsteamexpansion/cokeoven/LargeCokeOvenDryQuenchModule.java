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

/** Fixed ordinary-block geometry of the large coke oven's right-side dry-quenching tower. */
public final class LargeCokeOvenDryQuenchModule {

    public static final String ID = "COKE-R-05";

    private LargeCokeOvenDryQuenchModule() {}

    public static Result validate(ServerLevel level, BlockPos controller, Direction front) {
        return ModuleStructure.validate(level, requirements(controller, front));
    }

    public static BlockPos[] bounds(BlockPos controller, Direction front) {
        return ModuleStructure.orderedBounds(
                local(controller, front, 4, 0, 0),
                local(controller, front, 8, 4, 6));
    }

    public static BlockPos anchor(BlockPos controller, Direction front) {
        return local(controller, front, 4, 2, 0);
    }

    public static void place(ServerLevel level, BlockPos controller, Direction front) {
        ModuleStructure.place(level, requirements(controller, front));
    }

    public static List<UltimateTerminalModuleProvider.Requirement> terminalRequirements(
            BlockPos controller, Direction front) {
        return UltimateTerminalModuleProvider.fromStructure(requirements(controller, front));
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
}
