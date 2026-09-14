package com.hoshino.gregsteamexpansion.machine.multiblock.part;

import com.gregtechceu.gtceu.api.cover.CoverBehavior;
import com.gregtechceu.gtceu.api.cover.CoverDefinition;
import com.gregtechceu.gtceu.api.machine.MetaMachine;

import com.lowdragmc.lowdraglib.gui.editor.runtime.PersistedParser;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.HashMap;
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

    /** One detached cover: side, definition, attached item and persisted settings. */
    public record CoverData(Direction side, CoverDefinition definition, ItemStack item, CompoundTag persistedState) {}

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
                CompoundTag persistedState = new CompoundTag();
                PersistedParser.serializeNBT(persistedState, cover.getClass(), cover);
                covers.add(new CoverData(side, cover.coverDefinition, cover.getPickItem().copy(), persistedState));
                machine.getCoverContainer().setCoverAtSide(null, side);
            }
        }
        return covers;
    }

    /** Re-attaches covers on the new machine; rejects drop as items at pos. */
    public static void restoreCovers(MetaMachine machine, List<CoverData> covers, BlockPos pos) {
        for (CoverData cover : covers) {
            var container = machine.getCoverContainer();
            CoverBehavior restored = cover.definition().createCoverBehavior(container, cover.side());
            boolean attached = container.getCoverAtSide(cover.side()) == null &&
                    container.canPlaceCoverOnSide(cover.definition(), cover.side()) && restored.canAttach();
            if (attached) {
                restored.onAttached(cover.item(), null);
                PersistedParser.deserializeNBT(cover.persistedState(), new HashMap<>(), restored.getClass(), restored);
                restored.onLoad();
                container.setCoverAtSide(restored, cover.side());
                container.notifyBlockUpdate();
                container.markDirty();
                container.scheduleNeighborShapeUpdate();
            }
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
