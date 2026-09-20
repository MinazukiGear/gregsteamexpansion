package com.hoshino.gregsteamexpansion.client;

import com.hoshino.gregsteamexpansion.difficulty.Difficulty;
import com.hoshino.gregsteamexpansion.difficulty.GSEDifficultyAuthority;
import com.hoshino.gregsteamexpansion.difficulty.GSEDifficultyConfig;
import com.hoshino.gregsteamexpansion.difficulty.GSEDifficultyState;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import org.jetbrains.annotations.Nullable;

/**
 * In-game editor for the standalone difficulty switch and tier in
 * gregsteamexpansion-common.toml. With an external pack authority it becomes
 * a read-only status view. The one-time variant is shown before the
 * first title screen; the regular variant opens from the Mods config button.
 * Standard left-right layout: setting label on the left, value control on
 * the right, footer buttons across the bottom. Writing follows the restart
 * rule: the value lands in the TOML, while the running session keeps its
 * captured value until the next full restart.
 */
public final class GSEConfigScreen extends Screen {
    private static final int PANEL_HALF_WIDTH = 155;
    private static final int ENABLED_ROW_Y = 96;
    private static final int DIFFICULTY_ROW_Y = 126;
    private static final int TERMINAL_ROW_Y = 156;
    private static final int FOOTER_Y = 52;
    private static final int FOOTER_BUTTON_WIDTH = 100;

    private static final Component ENABLED_LABEL =
            Component.translatable("config.gregsteamexpansion.screen.difficulty_enabled");
    private static final Component DIFFICULTY_LABEL =
            Component.translatable("config.gregsteamexpansion.screen.difficulty");
    private static final Component RESET_LABEL =
            Component.translatable("config.gregsteamexpansion.screen.reset");
    private static final Component TERMINAL_LABEL =
            Component.translatable("config.gregsteamexpansion.screen.ultimate_terminal");
    private static final Component CONFIGURE_LABEL =
            Component.translatable("config.gregsteamexpansion.screen.configure");
    private static final Component RESTART_HINT =
            Component.translatable("config.gregsteamexpansion.screen.restart");
    private static final Component EXTERNAL_LABEL =
            Component.translatable("config.gregsteamexpansion.screen.external_authority");

    @Nullable
    private final Screen parent;
    private final boolean initialSetup;
    private boolean enabledValue;
    private Difficulty value;

    @Nullable
    private Button valueButton;
    @Nullable
    private Button enabledButton;

    public GSEConfigScreen(@Nullable Screen parent) {
        this(parent, false);
    }

    private GSEConfigScreen(@Nullable Screen parent, boolean initialSetup) {
        super(Component.translatable(initialSetup
                ? "config.gregsteamexpansion.screen.first_setup.title"
                : "config.gregsteamexpansion.screen.title"));
        this.parent = parent;
        this.initialSetup = initialSetup;
        this.enabledValue = GSEDifficultyAuthority.isExternallyManaged()
                ? GSEDifficultyState.isEnabled() : GSEDifficultyConfig.capturedDifficultyEnabled();
        this.value = GSEDifficultyAuthority.isExternallyManaged()
                ? GSEDifficultyState.resolved() : GSEDifficultyConfig.capturedDifficulty();
    }

    public static GSEConfigScreen initialSetup(Screen parent) {
        return new GSEConfigScreen(parent, true);
    }

    @Override
    protected void init() {
        int left = this.width / 2 - PANEL_HALF_WIDTH;
        boolean externallyManaged = GSEDifficultyAuthority.isExternallyManaged();
        if (!externallyManaged) {
            this.enabledButton = this.addRenderableWidget(
                    Button.builder(enabledLabel(), button -> toggleEnabled())
                            .bounds(this.width / 2 + 5, ENABLED_ROW_Y, 150, 20).build());
            // Right-aligned value control; the label is drawn beside it in render.
            this.valueButton = this.addRenderableWidget(
                    Button.builder(valueLabel(), button -> cycleValue())
                            .bounds(this.width / 2 + 5, DIFFICULTY_ROW_Y, 150, 20).build());
            this.valueButton.active = enabledValue;
        }
        if (!initialSetup) {
            this.addRenderableWidget(Button.builder(CONFIGURE_LABEL,
                            button -> {
                                if (this.minecraft != null) {
                                    this.minecraft.setScreen(new UltimateTerminalConfigScreen(this));
                                }
                            })
                    .bounds(this.width / 2 + 5, TERMINAL_ROW_Y, 150, 20).build());
        }

        int footerY = this.height - FOOTER_Y;
        if (!externallyManaged) {
            this.addRenderableWidget(Button.builder(RESET_LABEL, button -> {
                        enabledValue = false;
                        value = Difficulty.NORMAL;
                        refreshButtons();
                    }).bounds(left, footerY, FOOTER_BUTTON_WIDTH, 20).build());
        }
        if (!initialSetup && !externallyManaged) {
            this.addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), button -> this.onClose())
                    .bounds(this.width / 2 - 51, footerY, FOOTER_BUTTON_WIDTH, 20).build());
        }
        Component doneLabel = Component.translatable(initialSetup
                ? "config.gregsteamexpansion.screen.first_setup.save"
                : "gui.done");
        this.addRenderableWidget(Button.builder(doneLabel, button -> {
                    if (externallyManaged) {
                        this.onClose();
                    } else if (initialSetup) {
                        GSEDifficultyConfig.completeInitialSetup(enabledValue, value);
                        if (this.minecraft != null) {
                            this.minecraft.stop();
                        }
                    } else {
                        GSEDifficultyConfig.setDifficultySettings(enabledValue, value);
                        this.onClose();
                    }
                }).bounds(initialSetup || externallyManaged
                                ? this.width / 2 - FOOTER_BUTTON_WIDTH / 2 : this.width / 2 + 53,
                        footerY, FOOTER_BUTTON_WIDTH, 20).build());
    }

    private void toggleEnabled() {
        enabledValue = !enabledValue;
        refreshButtons();
    }

    private void refreshButtons() {
        if (this.enabledButton != null) {
            this.enabledButton.setMessage(enabledLabel());
        }
        if (this.valueButton != null) {
            this.valueButton.setMessage(valueLabel());
            this.valueButton.active = enabledValue;
        }
    }

    private void cycleValue() {
        Difficulty[] difficulties = Difficulty.values();
        value = difficulties[(value.ordinal() + 1) % difficulties.length];
        if (this.valueButton != null) {
            this.valueButton.setMessage(valueLabel());
        }
    }

    private Component valueLabel() {
        return Component.translatable(value.getDisplayNameKey());
    }

    private Component enabledLabel() {
        return Component.translatable(enabledValue ? "options.on" : "options.off");
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 30, 0xFFFFFF);
        boolean externallyManaged = GSEDifficultyAuthority.isExternallyManaged();
        if (externallyManaged) {
            graphics.drawCenteredString(this.font, EXTERNAL_LABEL, this.width / 2, 72, 0xA0A0A0);
            graphics.drawCenteredString(this.font,
                    Component.translatable("config.gregsteamexpansion.screen.external_difficulty", valueLabel()),
                    this.width / 2, 92, 0xFFFFFF);
        }
        if (initialSetup) {
            int lineY = 47;
            int lineWidth = Math.min(360, this.width - 30);
            for (var line : this.font.split(
                    Component.translatable("config.gregsteamexpansion.screen.first_setup.description"), lineWidth)) {
                graphics.drawCenteredString(this.font, line, this.width / 2, lineY, 0xA0A0A0);
                lineY += 10;
            }
            for (var line : this.font.split(
                    Component.translatable("config.gregsteamexpansion.screen.first_setup.restart"), lineWidth)) {
                graphics.drawCenteredString(this.font, line, this.width / 2, lineY, 0xA0A0A0);
                lineY += 10;
            }
        }
        if (!externallyManaged) {
            graphics.drawString(this.font, ENABLED_LABEL,
                    this.width / 2 - PANEL_HALF_WIDTH, ENABLED_ROW_Y + 6, 0xFFFFFF);
            graphics.drawString(this.font, DIFFICULTY_LABEL,
                    this.width / 2 - PANEL_HALF_WIDTH, DIFFICULTY_ROW_Y + 6,
                    enabledValue ? 0xFFFFFF : 0x808080);
        }
        if (!initialSetup) {
            graphics.drawString(this.font, TERMINAL_LABEL,
                    this.width / 2 - PANEL_HALF_WIDTH, TERMINAL_ROW_Y + 6, 0xFFFFFF);
        }
        if (!externallyManaged) {
            graphics.drawCenteredString(this.font, RESTART_HINT, this.width / 2, this.height - 28, 0x808080);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        if (!initialSetup && this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }
}
