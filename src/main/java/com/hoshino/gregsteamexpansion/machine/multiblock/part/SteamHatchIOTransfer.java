package com.hoshino.gregsteamexpansion.machine.multiblock.part;

import com.gregtechceu.gtceu.api.cover.CoverBehavior;
import com.gregtechceu.gtceu.api.cover.CoverDefinition;
import com.gregtechceu.gtceu.api.machine.MetaMachine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared helpers for machine-to-machine hatch replacement (legacy steam input
 * hatch migration and steam fluid hatch {@code swapIO()}). Covers are detached
 * through the internal {@code setCoverAtSide(null, side)} BEFORE the block swap
 * so {@code MetaMachineBlock#onRemove} cannot drop them into the world, then
 * re-attached on the new hatch by definition; a cover the new hatch rejects
 * falls as an item instead of silently disappearing. Covers are never both
 * re-attached and dropped, so no duplication path exists.
 */
public final class SteamHatchIOTransfer {

    /** One detached cover: side, definition and the item it was attached from. */
    public record CoverData(Direction side, CoverDefinition definition, ItemStack item) {}

    private SteamHatchIOTransfer() {}

    /**
     * Silently removes every cover from the machine and returns their snapshot.
     * Must be called before any {@code setBlock} that replaces the machine.
     */
    public static List<CoverData> detachCoversSilently(MetaMachine machine) {
        List<CoverData> covers = new ArrayList<>();
        for (Direction side : Direction.values()) {
            CoverBehavior cover = machine.getCoverContainer().getCoverAtSide(side);
            if (cover != null) {
                covers.add(new CoverData(side, cover.coverDefinition, cover.getPickItem().copy()));
                machine.getCoverContainer().setCoverAtSide(null, side);
            }
        }
        return covers;
    }

    /** Re-attaches covers on the new machine; rejects drop as items at pos. */
    public static void restoreCovers(MetaMachine machine, List<CoverData> covers, BlockPos pos) {
        for (CoverData cover : covers) {
            boolean attached = machine.getCoverContainer().placeCoverOnSide(cover.side(), cover.item(),
                    cover.definition(), null);
            if (!attached) {
                Level level = machine.getLevel();
                if (level != null) {
                    Block.popResource(level, pos, cover.item());
                }
            }
        }
    }

    /** Last-resort drop when the replacement block never materialized. */
    public static void dropCapturedCovers(Level level, BlockPos pos, List<CoverData> covers) {
        for (CoverData cover : covers) {
            Block.popResource(level, pos, cover.item());
        }
    }
}
