package com.hoshino.gregsteamexpansion.machine.multiblock;

import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.widget.ToggleButtonWidget;
import com.gregtechceu.gtceu.utils.FormattingUtil;
import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import com.lowdragmc.lowdraglib.gui.widget.ComponentPanelWidget;
import com.lowdragmc.lowdraglib.gui.widget.DraggableScrollableWidgetGroup;
import com.lowdragmc.lowdraglib.gui.widget.LabelWidget;

import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.items.ItemHandlerHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/** Common widgets and live text formatting for steam controller information pages. */
public final class SteamProcessorUI {

    public static final int WIDTH = 260;
    public static final int DEFAULT_HEIGHT = 170;
    private static final int FOOTER_HEIGHT = 32;
    private static final int LABEL_X = 2;
    private static final int VALUE_X = 104;
    private static final int ROW_HEIGHT = 10;

    private SteamProcessorUI() {}

    public static DraggableScrollableWidgetGroup scrollArea(int height) {
        return new DraggableScrollableWidgetGroup(5, 5, WIDTH - 10, height - FOOTER_HEIGHT);
    }

    public static int infoRow(DraggableScrollableWidgetGroup group, int y, String labelKey,
                              Supplier<String> value, ChatFormatting valueColor) {
        group.addWidget(new LabelWidget(LABEL_X, y, () -> Component.translatable(labelKey).getString())
                .setTextColor(-1).setDropShadow(true));
        group.addWidget(label(VALUE_X, y, () -> escape(value.get()), valueColor));
        return y + ROW_HEIGHT;
    }

    public static int tooltipRow(DraggableScrollableWidgetGroup group, int y, String labelKey,
                                 Supplier<String> value, Supplier<List<Component>> tooltips) {
        group.addWidget(new LabelWidget(LABEL_X, y, () -> Component.translatable(labelKey).getString())
                .setTextColor(-1).setDropShadow(true));
        LabelWidget valueLabel = new LabelWidget(VALUE_X, y, () -> escape(value.get())) {
            @Override
            public List<Component> getTooltipTexts() {
                return tooltips.get();
            }
        };
        Integer rgb = ChatFormatting.WHITE.getColor();
        valueLabel.setTextColor(rgb == null ? -1 : (rgb & 0xFFFFFF)).setDropShadow(true);
        group.addWidget(valueLabel);
        return y + ROW_HEIGHT;
    }

    public static void addPowerButton(ModularUI ui, int height, BooleanSupplier enabled,
                                      Consumer<Boolean> setEnabled) {
        ui.widget(new ToggleButtonWidget(6, height - 24, 18, 18, GuiTextures.BUTTON_POWER,
                enabled, value -> setEnabled.accept(value)));
    }

    /** Adds the upstream large-boiler-style throttle readout and [-]/[+] controls. */
    public static int throttleRows(DraggableScrollableWidgetGroup group, int y, boolean remote,
                                   IntSupplier throttlePercent, IntConsumer setThrottlePercent) {
        Consumer<List<Component>> text = lines -> {
            Component throttle = Component.translatable("gtceu.multiblock.large_boiler.throttle",
                    ChatFormatting.AQUA.toString() + throttlePercent.getAsInt() + "%")
                    .withStyle(Style.EMPTY.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                            Component.translatable("gregsteamexpansion.machine.steam_throttle.tooltip"))));
            lines.add(throttle);
            var controls = Component.translatable("gtceu.multiblock.large_boiler.throttle_modify");
            controls.append(" ");
            controls.append(ComponentPanelWidget.withButton(Component.literal("[-]"), "sub"));
            controls.append(" ");
            controls.append(ComponentPanelWidget.withButton(Component.literal("[+]"), "add"));
            lines.add(controls);
        };
        group.addWidget(new ComponentPanelWidget(LABEL_X, y, text)
                .textSupplier(remote ? null : text)
                .setMaxWidthLimit(WIDTH - 14)
                .clickHandler((componentData, clickData) -> {
                    if (!clickData.isRemote) {
                        setThrottlePercent.accept(SteamThrottle.step(
                                throttlePercent.getAsInt(), componentData.equals("add")));
                    }
                }));
        return y + ROW_HEIGHT * 2;
    }

    /** Adds the Large Steam Supply Hatch overclock toggle at the right edge. */
    public static void addLargeSteamOverclockButton(ModularUI ui, int height, BooleanSupplier available,
                                                    BooleanSupplier enabled,
                                                    Consumer<Boolean> setEnabled) {
        ui.widget(new AvailableToggleButtonWidget(WIDTH - 24, height - 24, available,
                enabled, setEnabled).setHoverTooltips(
                Component.translatable("gregsteamexpansion.machine.large_steam_overclock")
                        .withStyle(ChatFormatting.YELLOW),
                Component.translatable("gregsteamexpansion.machine.large_steam_overclock.info")
                        .withStyle(ChatFormatting.GRAY),
                Component.translatable("gregsteamexpansion.machine.large_steam_overclock.next_recipe")
                        .withStyle(ChatFormatting.GRAY)));
    }

    /**
     * Keeps the controller UI widget tree identical on both logical sides.
     * The part collector is server-only runtime state, so deciding whether to
     * construct the widget in {@code createUI} can omit it from the client
     * tree. Only its visibility is derived from that state and synchronized.
     */
    private static final class AvailableToggleButtonWidget extends ToggleButtonWidget {

        private static final int AVAILABILITY_UPDATE_ID = 3;
        private final BooleanSupplier available;

        private AvailableToggleButtonWidget(int x, int y, BooleanSupplier available,
                                            BooleanSupplier enabled, Consumer<Boolean> setEnabled) {
            super(x, y, 18, 18, GuiTextures.BUTTON_BATCH, enabled,
                    value -> setEnabled.accept(value));
            this.available = available;
            setVisible(false);
            setActive(false);
        }

        @Override
        public void writeInitialData(FriendlyByteBuf buffer) {
            super.writeInitialData(buffer);
            boolean value = available.getAsBoolean();
            setAvailable(value);
            buffer.writeBoolean(value);
        }

        @Override
        public void readInitialData(FriendlyByteBuf buffer) {
            super.readInitialData(buffer);
            setAvailable(buffer.readBoolean());
        }

        @Override
        public void detectAndSendChanges() {
            super.detectAndSendChanges();
            boolean value = available.getAsBoolean();
            if (isVisible() != value || isActive() != value) {
                setAvailable(value);
                writeUpdateInfo(AVAILABILITY_UPDATE_ID, data -> data.writeBoolean(value));
            }
        }

        @Override
        @OnlyIn(Dist.CLIENT)
        public void readUpdateInfo(int id, FriendlyByteBuf buffer) {
            if (id == AVAILABILITY_UPDATE_ID) {
                setAvailable(buffer.readBoolean());
            } else {
                super.readUpdateInfo(id, buffer);
            }
        }

        private void setAvailable(boolean value) {
            setVisible(value);
            setActive(value);
        }
    }

    public static String progress(boolean visible, int progress, int duration) {
        if (!visible) {
            return "—";
        }
        int boundedDuration = Math.max(1, duration);
        int boundedProgress = Math.min(progress, duration);
        double percent = Math.round(boundedProgress * 1000.0 / boundedDuration) / 10.0;
        return String.format(Locale.ROOT, "%.1f%%", percent) + " ("
                + FormattingUtil.formatNumbers(boundedProgress) + " / "
                + FormattingUtil.formatNumbers(duration) + "t)";
    }

    public static String duration(int ticks) {
        if (ticks < 20) {
            return String.valueOf(ticks);
        }
        long seconds = ticks / 20L;
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;
        return hours > 0 ? String.format("%d:%02d:%02d", hours, minutes, secs)
                : String.format("%d:%02d", minutes, secs);
    }

    public static String demand(long currentDemand, long lockedDemand, boolean consuming, String notConsumingKey) {
        if (currentDemand == 0) {
            return "0 mB/t";
        }
        String demand = FormattingUtil.formatNumbers(lockedDemand) + " mB/t";
        return consuming ? demand
                : demand + " (" + Component.translatable(notConsumingKey).getString() + ")";
    }

    public static String steamStorage(boolean formed, long stored, long capacity) {
        return formed ? FormattingUtil.formatNumbers(stored) + " / "
                + FormattingUtil.formatNumbers(capacity) + " mB" : "—";
    }

    public static String pendingSummary(List<ItemStack> items, List<FluidStack> fluids, String summaryKey) {
        long total = itemTotal(items) + fluidTotal(fluids);
        if (total == 0) {
            return "—";
        }
        return Component.translatable(summaryKey, FormattingUtil.formatNumbers(total),
                itemKinds(items) + fluidKinds(fluids)).getString();
    }

    public static List<Component> pendingTooltips(List<ItemStack> items, List<FluidStack> fluids,
                                                   String emptyKey, String detailKey) {
        if (items.isEmpty() && fluids.isEmpty()) {
            return List.of(Component.translatable(emptyKey).withStyle(ChatFormatting.GRAY));
        }
        List<Component> tooltips = new ArrayList<>();
        tooltips.add(Component.translatable(detailKey).withStyle(ChatFormatting.GRAY));
        for (ItemStack stack : items) {
            tooltips.add(Component.literal("- " + stack.getHoverName().getString() + " × "
                    + FormattingUtil.formatNumbers(stack.getCount())).withStyle(ChatFormatting.WHITE));
        }
        for (FluidStack stack : fluids) {
            tooltips.add(Component.literal("- " + stack.getDisplayName().getString() + " × "
                    + FormattingUtil.formatNumbers(stack.getAmount()) + " mB").withStyle(ChatFormatting.WHITE));
        }
        return tooltips;
    }

    public static long itemTotal(List<ItemStack> items) {
        long total = 0;
        for (ItemStack stack : items) {
            total += stack.getCount();
        }
        return total;
    }

    public static long fluidTotal(List<FluidStack> fluids) {
        long total = 0;
        for (FluidStack stack : fluids) {
            total += stack.getAmount();
        }
        return total;
    }

    public static int itemKinds(List<ItemStack> items) {
        List<ItemStack> kinds = new ArrayList<>();
        for (ItemStack stack : items) {
            boolean merged = false;
            for (ItemStack kind : kinds) {
                if (ItemHandlerHelper.canItemStacksStack(kind, stack)) {
                    kind.grow(stack.getCount());
                    merged = true;
                    break;
                }
            }
            if (!merged) {
                kinds.add(stack.copy());
            }
        }
        return kinds.size();
    }

    public static int fluidKinds(List<FluidStack> fluids) {
        List<FluidStack> kinds = new ArrayList<>();
        for (FluidStack stack : fluids) {
            boolean merged = false;
            for (FluidStack kind : kinds) {
                if (kind.isFluidEqual(stack)) {
                    kind.grow(stack.getAmount());
                    merged = true;
                    break;
                }
            }
            if (!merged) {
                kinds.add(stack.copy());
            }
        }
        return kinds.size();
    }

    private static LabelWidget label(int x, int y, Supplier<String> text, ChatFormatting color) {
        Integer rgb = color.getColor();
        return new LabelWidget(x, y, text)
                .setTextColor(rgb == null ? -1 : (rgb & 0xFFFFFF)).setDropShadow(true);
    }

    private static String escape(String value) {
        return value.replace("%", "%%");
    }
}
