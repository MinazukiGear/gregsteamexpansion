package com.hoshino.gregsteamexpansion.client;

import com.hoshino.gregsteamexpansion.terminal.UltimateTerminalConfig;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import org.jetbrains.annotations.Nullable;

/** In-game editor for the server-side Ultimate Terminal common config. */
public final class UltimateTerminalConfigScreen extends Screen {
    private static final int PANEL_HALF_WIDTH = 155;
    private static final int FOOTER_BUTTON_WIDTH = 100;
    private static final int BLOCKS_ROW_Y = 48;
    private static final int CHANNEL_ROW_Y = 82;

    private static final Component BLOCKS_LABEL =
            Component.translatable("config.gregsteamexpansion.screen.ultimate_terminal.blocks_per_tick");
    private static final Component CHANNELS_LABEL =
            Component.translatable("config.gregsteamexpansion.screen.ultimate_terminal.channels");
    private static final Component CHANNELS_HINT =
            Component.translatable("config.gregsteamexpansion.screen.ultimate_terminal.channels_hint");
    private static final Component SERVER_HINT =
            Component.translatable("config.gregsteamexpansion.screen.ultimate_terminal.server_hint");
    private static final Component INVALID_HINT =
            Component.translatable("config.gregsteamexpansion.screen.ultimate_terminal.invalid");

    @Nullable
    private final Screen parent;
    private String blocksPerTickValue;
    @Nullable
    private EditBox blocksPerTick;
    @Nullable
    private Button doneButton;

    public UltimateTerminalConfigScreen(@Nullable Screen parent) {
        super(Component.translatable("config.gregsteamexpansion.screen.ultimate_terminal.title"));
        this.parent = parent;
        this.blocksPerTickValue = Integer.toString(UltimateTerminalConfig.blocksPerTick());
    }

    @Override
    protected void init() {
        int left = this.width / 2 - PANEL_HALF_WIDTH;

        this.blocksPerTick = new EditBox(this.font, this.width / 2 + 5, BLOCKS_ROW_Y, 150, 20,
                BLOCKS_LABEL);
        this.blocksPerTick.setMaxLength(3);
        this.blocksPerTick.setFilter(value -> value.matches("\\d{0,3}"));
        this.blocksPerTick.setValue(this.blocksPerTickValue);
        this.blocksPerTick.setResponder(value -> {
            this.blocksPerTickValue = value;
            updateValidity();
        });
        this.addRenderableWidget(this.blocksPerTick);
        this.addRenderableWidget(Button.builder(
                        Component.translatable("config.gregsteamexpansion.screen.ultimate_terminal.channels.open",
                                UltimateTerminalConfig.configuredSelectionChannelEntries().size()),
                        button -> {
                            if (this.minecraft != null) {
                                this.minecraft.setScreen(new UltimateTerminalChannelsConfigScreen(this));
                            }
                        })
                .bounds(this.width / 2 + 5, CHANNEL_ROW_Y, 150, 20).build());

        int footerY = this.height - 42;
        this.addRenderableWidget(Button.builder(
                        Component.translatable("config.gregsteamexpansion.screen.reset"), button -> resetDefaults())
                .bounds(left, footerY, FOOTER_BUTTON_WIDTH, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), button -> this.onClose())
                .bounds(this.width / 2 - 51, footerY, FOOTER_BUTTON_WIDTH, 20).build());
        this.doneButton = this.addRenderableWidget(Button.builder(Component.translatable("gui.done"),
                        button -> saveAndClose())
                .bounds(this.width / 2 + 53, footerY, FOOTER_BUTTON_WIDTH, 20).build());
        updateValidity();
    }

    private void resetDefaults() {
        if (this.blocksPerTick != null) {
            this.blocksPerTick.setValue(Integer.toString(UltimateTerminalConfig.DEFAULT_BLOCKS_PER_TICK));
        }
        updateValidity();
    }

    private void saveAndClose() {
        if (this.blocksPerTick == null || !isValid()) return;
        UltimateTerminalConfig.setBlocksPerTick(Integer.parseInt(this.blocksPerTick.getValue()));
        onClose();
    }

    private void updateValidity() {
        if (this.doneButton != null) {
            this.doneButton.active = isValid();
        }
    }

    private boolean isValid() {
        if (this.blocksPerTick == null) return false;
        try {
            int value = Integer.parseInt(this.blocksPerTick.getValue());
            return value >= 1 && value <= 256;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    @Override
    public void tick() {
        if (this.blocksPerTick != null) this.blocksPerTick.tick();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 20, 0xFFFFFF);
        graphics.drawString(this.font, BLOCKS_LABEL,
                this.width / 2 - PANEL_HALF_WIDTH, BLOCKS_ROW_Y + 6, 0xFFFFFF);
        graphics.drawString(this.font, CHANNELS_LABEL,
                this.width / 2 - PANEL_HALF_WIDTH, CHANNEL_ROW_Y + 6, 0xFFFFFF);
        graphics.drawWordWrap(this.font, CHANNELS_HINT,
                this.width / 2 - PANEL_HALF_WIDTH, CHANNEL_ROW_Y + 30, PANEL_HALF_WIDTH * 2, 0x808080);
        graphics.drawCenteredString(this.font, isValid() ? SERVER_HINT : INVALID_HINT,
                this.width / 2, this.height - 16, isValid() ? 0x808080 : 0xFF5555);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) this.minecraft.setScreen(this.parent);
    }
}
