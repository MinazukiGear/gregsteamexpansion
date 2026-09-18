package com.hoshino.gregsteamexpansion.client;

import com.hoshino.gregsteamexpansion.registry.GSEBlocks;
import com.hoshino.gregsteamexpansion.terminal.TerminalCompatibility;
import com.hoshino.gregsteamexpansion.terminal.UltimateTerminalMessages;
import com.hoshino.gregsteamexpansion.terminal.UltimateTerminalSnapshot;

import net.minecraft.client.Minecraft;
import net.minecraftforge.event.TickEvent;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;

/** Client-only preview cache and view filters. */
public final class UltimateTerminalClientState {
    public enum DisplayMode {
        DIFFERENCES,
        FULL,
        LAYER;

        public DisplayMode next() { return values()[(ordinal() + 1) % values().length]; }
    }

    private static @Nullable UltimateTerminalSnapshot snapshot;
    private static DisplayMode displayMode = DisplayMode.DIFFERENCES;
    private static boolean batchMaterials;
    private static int layer;
    private static int requestCooldown;

    private UltimateTerminalClientState() {}

    public static void accept(UltimateTerminalSnapshot value) {
        snapshot = value;
        if (!value.cells().isEmpty()) {
            int minimum = value.cells().stream().mapToInt(cell -> cell.pos().getY()).min().orElse(0);
            int maximum = value.cells().stream().mapToInt(cell -> cell.pos().getY()).max().orElse(minimum);
            layer = Math.max(minimum, Math.min(maximum, layer == 0 ? minimum : layer));
        }
    }

    public static void apply(UltimateTerminalMessages.DeltaPacket packet) {
        UltimateTerminalSnapshot current = snapshot;
        if (current == null || current.revision() != packet.revision()) return;
        var cells = new ArrayList<>(current.cells());
        for (var change : packet.changes()) {
            if (change.index() < 0 || change.index() >= cells.size()) continue;
            var old = cells.get(change.index());
            cells.set(change.index(), new UltimateTerminalSnapshot.CellInfo(
                    old.pos(), old.expected(), change.status()));
        }
        snapshot = new UltimateTerminalSnapshot(current.revision(), current.targets(), current.selectedTarget(),
                current.dimension(), current.controller(), java.util.List.copyOf(cells),
                packet.selectedMaterials(), packet.batchMaterials(), packet.candidates(),
                packet.targetOverride(), packet.error());
    }

    public static @Nullable UltimateTerminalSnapshot snapshot() { return snapshot; }
    public static DisplayMode displayMode() { return displayMode; }
    public static void cycleDisplayMode() { displayMode = displayMode.next(); }
    public static boolean batchMaterials() { return batchMaterials; }
    public static void toggleMaterialScope() { batchMaterials = !batchMaterials; }
    public static int layer() { return layer; }

    public static void changeLayer(int delta) {
        UltimateTerminalSnapshot current = snapshot;
        if (current == null || current.cells().isEmpty()) return;
        int minimum = current.cells().stream().mapToInt(cell -> cell.pos().getY()).min().orElse(layer);
        int maximum = current.cells().stream().mapToInt(cell -> cell.pos().getY()).max().orElse(layer);
        layer = Math.max(minimum, Math.min(maximum, layer + delta));
    }

    public static boolean isHoldingTerminal() {
        var player = Minecraft.getInstance().player;
        return player != null && (player.getMainHandItem().is(GSEBlocks.ULTIMATE_TERMINAL.get())
                || player.getOffhandItem().is(GSEBlocks.ULTIMATE_TERMINAL.get()));
    }

    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !TerminalCompatibility.isAvailable()) return;
        if (!isHoldingTerminal()) {
            requestCooldown = 0;
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) return;
        if (requestCooldown > 0) requestCooldown--;
        boolean missing = snapshot == null || !snapshot.dimension().equals(minecraft.level.dimension().location());
        if (missing && requestCooldown == 0) {
            UltimateTerminalMessages.requestFromClient(-1);
            requestCooldown = 40;
        }
    }

    public static void clear() {
        snapshot = null;
        displayMode = DisplayMode.DIFFERENCES;
        batchMaterials = false;
        layer = 0;
        requestCooldown = 0;
    }
}
