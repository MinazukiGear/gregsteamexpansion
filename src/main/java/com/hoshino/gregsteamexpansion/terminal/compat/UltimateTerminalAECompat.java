package com.hoshino.gregsteamexpansion.terminal.compat;

import com.hoshino.gregsteamexpansion.terminal.TerminalPreviewMetrics;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.MEStorage;
import appeng.items.tools.powered.WirelessTerminalItem;
import appeng.me.helpers.PlayerSource;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.function.ToLongFunction;

/**
 * The only class that links AE2. Callers must guard every invocation with the
 * optional-mod gate so a GSE-only installation never loads this class.
 */
public final class UltimateTerminalAECompat {
    private UltimateTerminalAECompat() {}

    public static long available(ServerPlayer player, ItemStack wanted) {
        long metricStart = TerminalPreviewMetrics.start();
        try {
            return query(findStorage(player), wanted);
        } finally {
            TerminalPreviewMetrics.finish(TerminalPreviewMetrics.Stage.AE_QUERY, metricStart);
        }
    }

    /** Resolve this player's wireless grid once, solely for the current synchronous preview. */
    public static ToLongFunction<ItemStack> previewAvailability(ServerPlayer player) {
        Storage storage = findStorage(player);
        return wanted -> {
            long metricStart = TerminalPreviewMetrics.start();
            try {
                return query(storage, wanted);
            } finally {
                TerminalPreviewMetrics.finish(TerminalPreviewMetrics.Stage.AE_QUERY, metricStart);
            }
        };
    }

    private static long query(Storage storage, ItemStack wanted) {
        if (storage == null) return 0;
        // Preserve per-item simulation and PlayerSource permissions; a global stack
        // listing cannot substitute for what this player may actually extract.
        return storage.inventory.extract(AEItemKey.of(wanted), Long.MAX_VALUE,
                Actionable.SIMULATE, storage.source);
    }

    public static long extract(ServerPlayer player, ItemStack wanted, long amount, boolean simulate) {
        Storage storage = findStorage(player);
        if (storage == null || amount <= 0) return 0;
        return storage.inventory.extract(AEItemKey.of(wanted), amount,
                simulate ? Actionable.SIMULATE : Actionable.MODULATE, storage.source);
    }

    public static long insert(ServerPlayer player, ItemStack stack, long amount, boolean simulate) {
        Storage storage = findStorage(player);
        if (storage == null || amount <= 0) return 0;
        return storage.inventory.insert(AEItemKey.of(stack), amount,
                simulate ? Actionable.SIMULATE : Actionable.MODULATE, storage.source);
    }

    private static Storage findStorage(ServerPlayer player) {
        long metricStart = TerminalPreviewMetrics.start();
        try {
            for (ItemStack stack : player.getInventory().items) {
                if (!(stack.getItem() instanceof WirelessTerminalItem terminal)) continue;
                IGrid grid = terminal.getLinkedGrid(stack, player.level(), player);
                if (grid != null) {
                    return new Storage(grid.getStorageService().getInventory(), new PlayerSource(player));
                }
            }
            return null;
        } finally {
            TerminalPreviewMetrics.finish(TerminalPreviewMetrics.Stage.AE_LOOKUP, metricStart);
        }
    }

    private record Storage(MEStorage inventory, PlayerSource source) {}
}
