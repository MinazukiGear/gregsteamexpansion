package com.hoshino.gregsteamexpansion.client;

import com.hoshino.gregsteamexpansion.difficulty.GSEDifficultyConfig;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import org.jetbrains.annotations.Nullable;

/**
 * In-game editor for the single {@code difficulty} entry of
 * gregsteamexpansion-common.toml, opened from the Mods screen config button.
 * Standard left-right layout: setting label on the left, value control on
 * the right, footer buttons across the bottom. Writing follows the restart
 * rule: the value lands in the TOML, while the running session keeps its
 * captured value until the next full restart.
 */
public final class GSEConfigScreen extends Screen {
    private static final int PANEL_HALF_WIDTH = 155;
    private static final int ROW_Y = 76;
    private static final int FOOTER_Y = 52;
    private static final int FOOTER_BUTTON_WIDTH = 100;

    private static final Component DIFFICULTY_LABEL =
            Component.translatable("config.gregsteamexpansion.screen.difficulty");
    private static final Component RESET_LABEL =
            Component.translatable("config.gregsteamexpansion.screen.reset");
    private static final Component RESTART_HINT =
            Component.translatable("config.gregsteamexpansion.screen.restart");

    @Nullable
    private final Screen parent;
    private GSEDifficultyConfig.Request value;

    @Nullable
    private Button valueButton;

    public GSEConfigScreen(@Nullable Screen parent) {
        super(Component.translatable("config.gregsteamexpansion.screen.title"));
        this.parent = parent;
        this.value = GSEDifficultyConfig.capturedRequest();
    }

    @Override
    protected void init() {
        int left = this.width / 2 - PANEL_HALF_WIDTH;
        // Right-aligned value control; the label is drawn beside it in render.
        this.valueButton = this.addRenderableWidget(
                Button.builder(valueLabel(), button -> cycleValue())
                        .bounds(this.width / 2 + 5, ROW_Y, 150, 20).build());

        int footerY = this.height - FOOTER_Y;
        this.addRenderableWidget(Button.builder(RESET_LABEL, button -> {
                    value = GSEDifficultyConfig.Request.ASK;
                    if (this.valueButton != null) {
                        this.valueButton.setMessage(valueLabel());
                    }
                }).bounds(left, footerY, FOOTER_BUTTON_WIDTH, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), button -> this.onClose())
                .bounds(this.width / 2 - 51, footerY, FOOTER_BUTTON_WIDTH, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> {
                    GSEDifficultyConfig.setRequest(value);
                    this.onClose();
                }).bounds(this.width / 2 + 53, footerY, FOOTER_BUTTON_WIDTH, 20).build());
    }

    private void cycleValue() {
        GSEDifficultyConfig.Request[] requests = GSEDifficultyConfig.Request.values();
        value = requests[(value.ordinal() + 1) % requests.length];
        if (this.valueButton != null) {
            this.valueButton.setMessage(valueLabel());
        }
    }

    private Component valueLabel() {
        return value.difficulty() != null
                ? Component.translatable(value.difficulty().getDisplayNameKey())
                : Component.translatable("config.gregsteamexpansion.request.ask");
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 30, 0xFFFFFF);
        graphics.drawString(this.font, DIFFICULTY_LABEL,
                this.width / 2 - PANEL_HALF_WIDTH, ROW_Y + 6, 0xFFFFFF);
        graphics.drawCenteredString(this.font, RESTART_HINT, this.width / 2, this.height - 28, 0x808080);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }
}
