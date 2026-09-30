package com.hoshino.gregsteamexpansion.terminal;

import com.hoshino.gregsteamexpansion.machine.module.ModuleStructure;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;

import java.util.List;

/**
 * Exposes controller-owned external structures to the Ultimate Terminal.
 *
 * <p>Module IDs and installation slots are persistent identities. One module
 * can be selected per slot. The old ordered list is used only to migrate saved
 * single-module indices.</p>
 */
public interface UltimateTerminalModuleProvider {
    String CHANNEL_ID = "module";
    String CHANNEL_PREFIX = "module.";

    List<Module> terminalModules();

    default List<Module> legacyTerminalModules() {
        return terminalModules();
    }

    static boolean isModuleChannel(String id) {
        return id.startsWith(CHANNEL_PREFIX);
    }

    static List<Requirement> fromStructure(List<ModuleStructure.Requirement> requirements) {
        return requirements.stream()
                .map(requirement -> requirement.air()
                        ? Requirement.air(requirement.pos())
                        : Requirement.solid(requirement.pos(), requirement.block()))
                .toList();
    }

    record Module(String id, String translationKey, String slot, List<Requirement> requirements) {
        /** Third-party providers using the old constructor retain a single exclusive slot. */
        public Module(String id, String translationKey, List<Requirement> requirements) {
            this(id, translationKey, "legacy", requirements);
        }

        public Module {
            if (!id.matches("[A-Za-z0-9_.:-]{1,128}") || !slot.matches("[a-z0-9_-]{1,25}")) {
                throw new IllegalArgumentException("Invalid terminal module identity or slot");
            }
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
