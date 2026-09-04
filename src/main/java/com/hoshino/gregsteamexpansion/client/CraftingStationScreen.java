package com.hoshino.gregsteamexpansion.client;

import com.hoshino.gregsteamexpansion.GregSteamExpansion;
import com.hoshino.gregsteamexpansion.menu.CraftingStationMenu;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class CraftingStationScreen extends AbstractContainerScreen<CraftingStationMenu> {
    private static final ResourceLocation BACKGROUND =
            GregSteamExpansion.id("textures/gui/crafting_station.png");
    private static final ResourceLocation TERMINAL_BACKGROUND =
            GregSteamExpansion.id("textures/gui/crafting_station_terminal.png");
    private static final int IMAGE_WIDTH = 184;
    private static final int IMAGE_HEIGHT = 190;
    // The source terminal is an independent panel docked onto the left edge
    // of the main crafting-station UI (crafting-station.md 6.1).
    private static final int TERMINAL_WIDTH = 140;

    // Scrollbar groove, terminal-relative. Sits fully inside the panel with
    // a margin from the panel border.
    private static final int SLIDER_X = -20;
    private static final int SLIDER_Y = 12;
    private static final int SLIDER_W = 6;
    private static final int SLIDER_H = 160;

    private boolean draggingSlider;

    public CraftingStationScreen(CraftingStationMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = IMAGE_WIDTH;
        this.imageHeight = IMAGE_HEIGHT;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        boolean docked = this.menu.getSourceDirection() >= 0;
        if (docked) {
            graphics.blit(TERMINAL_BACKGROUND, this.leftPos - TERMINAL_WIDTH, this.topPos, 0, 0,
                    TERMINAL_WIDTH, IMAGE_HEIGHT, TERMINAL_WIDTH, IMAGE_HEIGHT);
        }
        graphics.blit(BACKGROUND, this.leftPos, this.topPos, 0, 0,
                IMAGE_WIDTH, IMAGE_HEIGHT, IMAGE_WIDTH, IMAGE_HEIGHT);
        if (docked && this.menu.getSourcePageCount() > 1) {
            drawSliderThumb(graphics);
        }

        // Nearly broken tools left in their slots get a red tint (crafting-station.md 4.2).
        for (int i = 0; i < CraftingStationMenu.TOOL_SLOTS; i++) {
            Slot slot = this.menu.slots.get(CraftingStationMenu.TOOL_START + i);
            ItemStack stack = slot.getItem();
            if (!stack.isEmpty() && stack.isDamageableItem()
                    && stack.getMaxDamage() - stack.getDamageValue() <= 2) {
                int x = this.leftPos + slot.x;
                int y = this.topPos + slot.y;
                graphics.fill(x, y, x + 16, y + 16, 0x50FF2020);
            }
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        // Vanilla crafting-table style: the interface carries no text at all.
    }

    private void drawSliderThumb(GuiGraphics graphics) {
        int thumbHeight = thumbHeight();
        int pages = this.menu.getSourcePageCount();
        int thumbY = SLIDER_Y + (SLIDER_H - thumbHeight) * this.menu.getSourcePage()
                / Math.max(1, pages - 1);
        // renderBg runs in absolute screen coordinates: add the panel origin.
        int x = this.leftPos + SLIDER_X;
        int y = this.topPos + thumbY;
        graphics.fill(x, y, x + SLIDER_W, y + thumbHeight, 0xFF8B8B8B);
        graphics.fill(x, y, x + SLIDER_W, y + 1, 0xFFFFFFFF);
        graphics.fill(x, y, x + 1, y + thumbHeight, 0xFFFFFFFF);
        graphics.fill(x, y + thumbHeight - 1, x + SLIDER_W, y + thumbHeight, 0xFF555555);
        graphics.fill(x + SLIDER_W - 1, y, x + SLIDER_W, y + thumbHeight, 0xFF555555);
    }

    private int thumbHeight() {
        return Math.max(12, SLIDER_H / Math.max(1, this.menu.getSourcePageCount()));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && this.menu.getSourceDirection() >= 0 && this.menu.getSourcePageCount() > 1
                && withinSlider(mouseX - this.leftPos, mouseY - this.topPos)) {
            this.draggingSlider = true;
            updateSliderPage(mouseY - this.topPos);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.draggingSlider) {
            updateSliderPage(mouseY - this.topPos);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        this.draggingSlider = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private boolean withinSlider(double x, double y) {
        return x >= SLIDER_X - 3 && x <= SLIDER_X + SLIDER_W + 3
                && y >= SLIDER_Y - 2 && y <= SLIDER_Y + SLIDER_H + 2;
    }

    private void updateSliderPage(double y) {
        int pages = this.menu.getSourcePageCount();
        if (pages <= 1) {
            return;
        }
        double relative = (y - SLIDER_Y - thumbHeight() / 2.0) / (SLIDER_H - thumbHeight());
        int page = (int) Math.round(Mth.clamp(relative, 0.0, 1.0) * (pages - 1));
        if (page != this.menu.getSourcePage()) {
            this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, page);
        }
    }
}
