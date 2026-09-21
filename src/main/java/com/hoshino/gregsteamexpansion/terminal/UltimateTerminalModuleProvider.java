package com.hoshino.gregsteamexpansion.terminal;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;

import java.util.List;

/**
 * Exposes controller-owned external structures to the Ultimate Terminal.
 *
 * <p>The returned list is ordered: index {@code 0} is channel value {@code 0},
 * index {@code 1} is channel value {@code 1}, and so on. New modules must be
 * appended so persisted channel values keep their meaning.</p>
 */
public interface UltimateTerminalModuleProvider {
    String CHANNEL_ID = "module";

    List<Module> terminalModules();

    record Module(String id, String translationKey, List<Requirement> requirements) {
        public Module {
            requirements = List.copyOf(requirements);
        }
    }

    record Requirement(BlockPos pos, Block block, boolean air) {
        public Requirement {
            pos = pos.immutable();
        }

        public static Requirement solid(BlockPos pos, Block block) {
            return new Requirement(pos, block, false);
        }

        public static Requirement air(BlockPos pos) {
            return new Requirement(pos, net.minecraft.world.level.block.Blocks.AIR, true);
        }
    }
}
