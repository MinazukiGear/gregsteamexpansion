package com.hoshino.gregsteamexpansion.client;

import com.hoshino.gregsteamexpansion.difficulty.Difficulty;
import com.hoshino.gregsteamexpansion.difficulty.GSEDifficultyConfig;
import com.hoshino.gregsteamexpansion.difficulty.GSEDifficultyMessages;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * First-entry difficulty choice for a save without a stored tier
 * (difficulty.md 服务端与存档权威性). The first submission initializes the save
 * tier; leaving without choosing keeps the save uninitialized.
 */
public final class GSEDifficultySelectScreen extends Screen {
    private static final int BUTTON_WIDTH = 200;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_SPACING = 4;
    private static final int HINT_WIDTH = 300;

    private static final Component TITLE =
            Component.translatable("config.gregsteamexpansion.difficulty.select.title");
    private static final Component HINT =
            Component.translatable("config.gregsteamexpansion.difficulty.select.hint");

    public GSEDifficultySelectScreen() {
        super(TITLE);
    }

    @Override
    protected void init() {
        int left = this.width / 2 - BUTTON_WIDTH / 2;
        int top = Math.max(this.height / 2 - 50, 70);
        Difficulty[] difficulties = Difficulty.values();
        for (int i = 0; i < difficulties.length; i++) {
            Difficulty difficulty = difficulties[i];
            this.addRenderableWidget(Button.builder(
                            Component.translatable(difficulty.getDisplayNameKey()),
                            button -> choose(difficulty))
                    .bounds(left, top + i * (BUTTON_HEIGHT + BUTTON_SPACING),
                            BUTTON_WIDTH, BUTTON_HEIGHT)
                    .build());
        }
    }

    private void choose(Difficulty difficulty) {
        GSEDifficultyConfig.applyChosenRequest(difficulty);
        GSEDifficultyMessages.sendChooseDifficulty(difficulty);
        Minecraft minecraft = this.minecraft;
        if (minecraft != null) {
            minecraft.setScreen(null);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 40, 0xFFFFFF);
        graphics.drawWordWrap(this.font, HINT, this.width / 2 - HINT_WIDTH / 2, 58, HINT_WIDTH, 0xA0A0A0);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        // Leaving without choosing keeps the save uninitialized; the next
        // entry shows this screen again.
        Minecraft minecraft = this.minecraft;
        if (minecraft == null) {
            return;
        }
        if (minecraft.level != null) {
            minecraft.level.disconnect();
            minecraft.clearLevel(new DisconnectedScreen(null, this.title,
                    Component.translatable("config.gregsteamexpansion.difficulty.select.cancelled")));
        } else {
            minecraft.setScreen(null);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
