package com.hoshino.gregsteamexpansion.machine.multiblock.processor;

import com.gregtechceu.gtceu.common.data.GTBlocks;
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

/** Fixed ordinary-block geometry of the large steam blast furnace's twin hot-blast stoves. */
public final class BlastFurnaceHotBlastModule {

    public static final String ID = "BF-B-01";
    public static final int WIDTH = 7;
    public static final int DEPTH = 5;
    public static final int HEIGHT = 9;
    public static final int MAIN_REAR_OFFSET = 12;
    public static final int MODULE_FRONT_OFFSET = 13;
    public static final int MODULE_REAR_OFFSET = 17;

    private BlastFurnaceHotBlastModule() {}

    public static Result validate(ServerLevel level, BlockPos controller, Direction front) {
        BlockPos connector = local(controller, front, 0, MAIN_REAR_OFFSET, 1);
        return ModuleStructure.validate(level, requirements(controller, front),
                (requirement, state) -> !requirement.pos().equals(connector) && !state.is(Blocks.AIR));
    }

    /** Full 7×5×9 module box; claims include the deliberate air between the towers. */
    public static BlockPos[] bounds(BlockPos controller, Direction front) {
        return ModuleStructure.orderedBounds(
                local(controller, front, -3, MODULE_FRONT_OFFSET, 0),
                local(controller, front, 3, MODULE_REAR_OFFSET, HEIGHT - 1));
    }

    /** Ordinary industrial-casing junction used only as the claim's stable geometric key. */
    public static BlockPos anchor(BlockPos controller, Direction front) {
        return local(controller, front, 0, 14, 1);
    }

    /** Places the exact structure for GameTest fixtures and future blueprint helpers. */
    public static void place(ServerLevel level, BlockPos controller, Direction front) {
        ModuleStructure.place(level, requirements(controller, front));
    }

    /** Exact terminal blueprint, including the two strict-air combustion cores. */
    public static List<UltimateTerminalModuleProvider.Requirement> terminalRequirements(
            BlockPos controller, Direction front) {
        return UltimateTerminalModuleProvider.fromStructure(requirements(controller, front));
    }

    private static List<Requirement> requirements(BlockPos controller, Direction front) {
        List<Requirement> result = new ArrayList<>(170);
        Block industrial = GSEProcessorPatterns.industrialSteamCasing();
        Block firebrick = GSEProcessorPatterns.blastBricks();
        Block cokeBrick = GSEProcessorPatterns.cokeBricksCasing();
        Block firebox = GTBlocks.FIREBOX_BRONZE.get();

        // The optional module may only connect through an ordinary rear-center blast brick.
        result.add(solid(controller, front, 0, MAIN_REAR_OFFSET, 1, firebrick));

        // Floating T-shaped hot-air main: one stem and a five-wide branch in front of the towers.
        result.add(solid(controller, front, 0, 13, 1, industrial));
        for (int x = -2; x <= 2; x++) {
            result.add(solid(controller, front, x, 14, 1, industrial));
        }

        for (int centerX : new int[] { -2, 2 }) {
            // 3×3 bronze firebox foundation.
            for (int x = centerX - 1; x <= centerX + 1; x++) {
                for (int z = 15; z <= 17; z++) {
                    result.add(solid(controller, front, x, z, 0, firebox));
                }
            }

            // Six checkerwork floors: blast-brick shell and coke-brick storage core.
            for (int y = 1; y <= 6; y++) {
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        int x = centerX + dx;
                        int z = 16 + dz;
                        if (dx == 0 && dz == 0) {
                            result.add(solid(controller, front, x, z, y, cokeBrick));
                        } else if (y == 1 && dx == 0 && dz == -1) {
                            result.add(solid(controller, front, x, z, y, industrial));
                        } else {
                            result.add(solid(controller, front, x, z, y, firebrick));
                        }
                    }
                }
            }

            // Combustion dome with a strict air core, then a solid industrial cap.
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    int x = centerX + dx;
                    int z = 16 + dz;
                    result.add(dx == 0 && dz == 0
                            ? air(controller, front, x, z, 7)
                            : solid(controller, front, x, z, 7, firebrick));
                    result.add(solid(controller, front, x, z, 8, industrial));
                }
            }
        }
        return result;
    }

    private static Requirement solid(BlockPos controller, Direction front,
                                     int side, int back, int up, Block block) {
        return new Requirement(local(controller, front, side, back, up), block, false);
    }

    private static Requirement air(BlockPos controller, Direction front, int side, int back, int up) {
        return new Requirement(local(controller, front, side, back, up), net.minecraft.world.level.block.Blocks.AIR, true);
    }
}
