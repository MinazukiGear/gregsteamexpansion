package com.hoshino.gregsteamexpansion.machine.multiblock.processor;

import com.gregtechceu.gtceu.common.data.GTBlocks;
import com.hoshino.gregsteamexpansion.registry.GSEProcessorPatterns;
import com.hoshino.gregsteamexpansion.terminal.UltimateTerminalModuleProvider;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.List;

/** Fixed ordinary-block geometry of the large steam blast furnace's twin hot-blast stoves. */
public final class BlastFurnaceHotBlastModule {

    public static final int WIDTH = 7;
    public static final int DEPTH = 5;
    public static final int HEIGHT = 9;
    public static final int MAIN_REAR_OFFSET = 12;
    public static final int MODULE_FRONT_OFFSET = 13;
    public static final int MODULE_REAR_OFFSET = 17;

    public enum Result {
        VALID,
        MISSING,
        INVALID,
        UNLOADED
    }

    private BlastFurnaceHotBlastModule() {}

    public static Result validate(ServerLevel level, BlockPos controller, Direction front) {
        List<Requirement> requirements = requirements(controller, front);
        for (Requirement requirement : requirements) {
            if (!level.hasChunkAt(requirement.pos())) {
                return Result.UNLOADED;
            }
        }

        int present = 0;
        boolean valid = true;
        BlockPos connector = local(controller, front, 0, MAIN_REAR_OFFSET, 1);
        for (Requirement requirement : requirements) {
            Block block = level.getBlockState(requirement.pos()).getBlock();
            if (!requirement.pos().equals(connector) && block != net.minecraft.world.level.block.Blocks.AIR) {
                present++;
            }
            if (requirement.air()) {
                valid &= level.getBlockState(requirement.pos()).isAir();
            } else {
                valid &= block == requirement.block();
            }
        }
        if (valid) {
            return Result.VALID;
        }
        return present == 0 ? Result.MISSING : Result.INVALID;
    }

    /** Full 7×5×9 module box; claims include the deliberate air between the towers. */
    public static BlockPos[] bounds(BlockPos controller, Direction front) {
        BlockPos a = local(controller, front, -3, MODULE_FRONT_OFFSET, 0);
        BlockPos b = local(controller, front, 3, MODULE_REAR_OFFSET, HEIGHT - 1);
        return new BlockPos[] {
                new BlockPos(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ())),
                new BlockPos(Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ()))
        };
    }

    /** Ordinary industrial-casing junction used only as the claim's stable geometric key. */
    public static BlockPos anchor(BlockPos controller, Direction front) {
        return local(controller, front, 0, 14, 1);
    }

    /** Places the exact structure for GameTest fixtures and future blueprint helpers. */
    public static void place(ServerLevel level, BlockPos controller, Direction front) {
        for (Requirement requirement : requirements(controller, front)) {
            if (requirement.air()) {
                level.setBlockAndUpdate(requirement.pos(), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
            } else {
                level.setBlockAndUpdate(requirement.pos(), requirement.block().defaultBlockState());
            }
        }
    }

    /** Exact terminal blueprint, including the two strict-air combustion cores. */
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

    private static BlockPos local(BlockPos controller, Direction front, int side, int back, int up) {
        Direction rear = front.getOpposite();
        Direction right = front.getClockWise();
        return controller.relative(right, side).relative(rear, back).above(up);
    }

    private record Requirement(BlockPos pos, Block block, boolean air) {}
}
