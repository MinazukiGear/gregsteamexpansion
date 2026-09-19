package com.hoshino.gregsteamexpansion.menu;

import com.hoshino.gregsteamexpansion.registry.GSEBlocks;
import com.hoshino.gregsteamexpansion.registry.GSEMenuTypes;
import com.hoshino.gregsteamexpansion.terminal.UltimateTerminalWorldData;
import com.hoshino.gregsteamexpansion.terminal.UltimateTerminalMessages;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.item.ItemStack;

/** Slotless control menu; vanilla menu buttons form the authenticated command channel. */
public final class UltimateTerminalMenu extends AbstractContainerMenu {
    public static final int BUTTON_START = 0;
    public static final int BUTTON_CLEAR = 1;
    public static final int BUTTON_MODE = 2;
    public static final int BUTTON_REPEAT_DOWN = 3;
    public static final int BUTTON_REPEAT_UP = 4;
    public static final int BUTTON_AE = 8;
    public static final int BUTTON_COLLECT = 9;
    public static final int BUTTON_OVERRIDE = 10;

    private final Player player;
    private int targetCount;
    private int mode;
    private int repeatCount;
    private int state;
    private int progress;
    private int total;
    private int useAE;
    private int pending;
    private int selectedTarget;
    private int targetOverride;

    public UltimateTerminalMenu(int id, Inventory inventory) {
        super(GSEMenuTypes.ULTIMATE_TERMINAL.get(), id);
        this.player = inventory.player;
        track(0);
        track(1);
        track(2);
        track(3);
        track(4);
        track(5);
        track(6);
        track(7);
        track(8);
        track(9);
    }

    private void track(int index) {
        addDataSlot(new DataSlot() {
            @Override
            public int get() {
                if (player instanceof ServerPlayer serverPlayer) {
                    UltimateTerminalWorldData data = UltimateTerminalWorldData.get(serverPlayer.server);
                    return switch (index) {
                        case 0 -> data.targetCount(player.getUUID());
                        case 1 -> data.mode(player.getUUID());
                        case 2 -> data.repeatCount(serverPlayer);
                        case 3 -> data.state(player.getUUID()).ordinal();
                        case 4 -> data.progress(player.getUUID());
                        case 5 -> data.total(player.getUUID());
                        case 6 -> data.useAE(player.getUUID()) ? 1 : 0;
                        case 7 -> data.pendingCount(player.getUUID());
                        case 8 -> data.selectedTarget(player.getUUID());
                        case 9 -> data.targetOverride(serverPlayer) ? 1 : 0;
                        default -> 0;
                    };
                }
                return local(index);
            }

            @Override
            public void set(int value) {
                setLocal(index, value);
            }
        });
    }

    private int local(int index) {
        return switch (index) {
            case 0 -> targetCount;
            case 1 -> mode;
            case 2 -> repeatCount;
            case 3 -> state;
            case 4 -> progress;
            case 5 -> total;
            case 6 -> useAE;
            case 7 -> pending;
            case 8 -> selectedTarget;
            case 9 -> targetOverride;
            default -> 0;
        };
    }

    private void setLocal(int index, int value) {
        switch (index) {
            case 0 -> targetCount = value;
            case 1 -> mode = value;
            case 2 -> repeatCount = value;
            case 3 -> state = value;
            case 4 -> progress = value;
            case 5 -> total = value;
            case 6 -> useAE = value;
            case 7 -> pending = value;
            case 8 -> selectedTarget = value;
            case 9 -> targetOverride = value;
            default -> { }
        }
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (!(player instanceof ServerPlayer serverPlayer)) return false;
        UltimateTerminalWorldData data = UltimateTerminalWorldData.get(serverPlayer.server);
        switch (id) {
            case BUTTON_START -> data.start(serverPlayer);
            case BUTTON_CLEAR -> data.clear(player.getUUID());
            case BUTTON_MODE -> data.cycleMode(player.getUUID());
            case BUTTON_REPEAT_DOWN -> data.changeRepeats(serverPlayer, -1);
            case BUTTON_REPEAT_UP -> data.changeRepeats(serverPlayer, 1);
            case BUTTON_AE -> data.toggleAE(player.getUUID());
            case BUTTON_COLLECT -> data.collectPending(serverPlayer);
            case BUTTON_OVERRIDE -> data.toggleTargetOverride(serverPlayer);
            default -> { return false; }
        }
        UltimateTerminalMessages.forceFull(serverPlayer);
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        if (player.isCreative()) return true;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(GSEBlocks.ULTIMATE_TERMINAL.get())) return true;
        }
        return player.getOffhandItem().is(GSEBlocks.ULTIMATE_TERMINAL.get());
    }

    public int targetCount() { return targetCount; }
    public int mode() { return mode; }
    public int repeatCount() { return repeatCount; }
    public int state() { return state; }
    public int progress() { return progress; }
    public int total() { return total; }
    public boolean useAE() { return useAE != 0; }
    public int pending() { return pending; }
    public int selectedTarget() { return selectedTarget; }
    public boolean targetOverride() { return targetOverride != 0; }
}
