package com.hoshino.gregsteamexpansion.client;

import com.hoshino.gregsteamexpansion.menu.UltimateTerminalMenu;
import com.hoshino.gregsteamexpansion.terminal.UltimateStructurePlanner;
import com.hoshino.gregsteamexpansion.terminal.UltimateTerminalMessages;
import com.hoshino.gregsteamexpansion.terminal.UltimateTerminalMode;
import com.hoshino.gregsteamexpansion.terminal.UltimateTerminalSnapshot;
import com.hoshino.gregsteamexpansion.terminal.UltimateTerminalWorldData;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.BlockItem;
import net.minecraftforge.registries.ForgeRegistries;

import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Four-page engineering console for targets, hologram controls, materials and legal part choices. */
public final class UltimateTerminalScreen extends AbstractContainerScreen<UltimateTerminalMenu> {
    private enum Page { TARGETS, PREVIEW, MATERIALS, PARTS }

    private Page page = Page.TARGETS;
    private int targetPage;
    private int partPage;
    private int materialPage;
    private @Nullable UltimateTerminalSnapshot lastSnapshot;
    private Button modeButton;
    private Button hatchButton;
    private Button aeButton;
    private Button overrideButton;
    private Button displayButton;
    private Button materialScopeButton;

    public UltimateTerminalScreen(UltimateTerminalMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 340;
        imageHeight = 238;
    }

    @Override
    protected void init() {
        super.init();
        rebuildPage();
        UltimateTerminalMessages.requestFromClient(menu.containerId);
    }

    private void rebuildPage() {
        clearWidgets();
        modeButton = null;
        hatchButton = null;
        aeButton = null;
        overrideButton = null;
        displayButton = null;
        materialScopeButton = null;
        int x = leftPos + 10;
        int y = topPos + 25;
        for (Page candidate : Page.values()) {
            Page selectedPage = candidate;
            addRenderableWidget(localButton(x + candidate.ordinal() * 80, y, 76,
                    Component.translatable("gregsteamexpansion.ultimate_terminal.tab."
                            + candidate.name().toLowerCase()), () -> {
                        page = selectedPage;
                        rebuildPage();
                    }));
        }
        switch (page) {
            case TARGETS -> buildTargets();
            case PREVIEW -> buildPreview();
            case MATERIALS -> buildMaterials();
            case PARTS -> buildParts();
        }
        lastSnapshot = UltimateTerminalClientState.snapshot();
        refreshLabels();
    }

    private void buildTargets() {
        UltimateTerminalSnapshot snapshot = UltimateTerminalClientState.snapshot();
        int x = leftPos + 14;
        int y = topPos + 55;
        if (snapshot != null) {
            targetPage = Math.min(targetPage, Math.max(0, (snapshot.targets().size() - 1) / 6));
            int start = targetPage * 6;
            for (int row = 0; row < 6 && start + row < snapshot.targets().size(); row++) {
                int index = start + row;
                var target = snapshot.targets().get(index);
                String marker = index == snapshot.selectedTarget() ? "▶ " : "";
                addRenderableWidget(localButton(x, y + row * 24, 250,
                        Component.literal(marker + target.name()),
                        () -> UltimateTerminalMessages.selectFromClient(menu.containerId, index)));
            }
        }
        addRenderableWidget(localButton(x + 258, y, 28, Component.literal("<"), () -> {
            targetPage = Math.max(0, targetPage - 1); rebuildPage();
        }));
        addRenderableWidget(localButton(x + 292, y, 28, Component.literal(">"), () -> {
            targetPage++; rebuildPage();
        }));
        addRenderableWidget(menuButton(x, topPos + 202, 100, UltimateTerminalMenu.BUTTON_CLEAR,
                Component.translatable("gregsteamexpansion.ultimate_terminal.clear")));
        addRenderableWidget(menuButton(x + 108, topPos + 202, 100, UltimateTerminalMenu.BUTTON_START,
                Component.translatable("gregsteamexpansion.ultimate_terminal.start")));
    }

    private void buildPreview() {
        int x = leftPos + 14;
        int y = topPos + 55;
        modeButton = addRenderableWidget(menuButton(x, y, 150, UltimateTerminalMenu.BUTTON_MODE, Component.empty()));
        addRenderableWidget(menuButton(x + 158, y, 150, UltimateTerminalMenu.BUTTON_START,
                Component.translatable("gregsteamexpansion.ultimate_terminal.start")));
        addRenderableWidget(menuButton(x, y + 28, 28, UltimateTerminalMenu.BUTTON_REPEAT_DOWN, Component.literal("-")));
        addRenderableWidget(menuButton(x + 122, y + 28, 28, UltimateTerminalMenu.BUTTON_REPEAT_UP, Component.literal("+")));
        addRenderableWidget(menuButton(x + 158, y + 28, 28, UltimateTerminalMenu.BUTTON_COIL_DOWN, Component.literal("-")));
        addRenderableWidget(menuButton(x + 280, y + 28, 28, UltimateTerminalMenu.BUTTON_COIL_UP, Component.literal("+")));
        hatchButton = addRenderableWidget(menuButton(x, y + 56, 150, UltimateTerminalMenu.BUTTON_HATCHES, Component.empty()));
        aeButton = addRenderableWidget(menuButton(x + 158, y + 56, 150, UltimateTerminalMenu.BUTTON_AE, Component.empty()));
        overrideButton = addRenderableWidget(menuButton(x, y + 84, 150, UltimateTerminalMenu.BUTTON_OVERRIDE, Component.empty()));
        displayButton = addRenderableWidget(localButton(x + 158, y + 84, 150, Component.empty(), () -> {
            UltimateTerminalClientState.cycleDisplayMode(); refreshLabels();
        }));
        addRenderableWidget(localButton(x, y + 112, 28, Component.literal("-"), () -> {
            UltimateTerminalClientState.changeLayer(-1); refreshLabels();
        }));
        addRenderableWidget(localButton(x + 122, y + 112, 28, Component.literal("+"), () -> {
            UltimateTerminalClientState.changeLayer(1); refreshLabels();
        }));
        addRenderableWidget(localButton(x + 158, y + 112, 150,
                Component.translatable("gregsteamexpansion.ultimate_terminal.refresh"),
                () -> UltimateTerminalMessages.requestFromClient(menu.containerId)));
        addRenderableWidget(menuButton(x, topPos + 202, 150, UltimateTerminalMenu.BUTTON_COLLECT,
                Component.translatable("gregsteamexpansion.ultimate_terminal.collect")));
    }

    private void buildMaterials() {
        int x = leftPos + 14;
        int y = topPos + 55;
        materialScopeButton = addRenderableWidget(localButton(x, y, 190, Component.empty(), () -> {
            UltimateTerminalClientState.toggleMaterialScope();
            materialPage = 0;
            refreshLabels();
        }));
        addRenderableWidget(localButton(x + 198, y, 110,
                Component.translatable("gregsteamexpansion.ultimate_terminal.refresh"),
                () -> UltimateTerminalMessages.requestFromClient(menu.containerId)));
        addRenderableWidget(localButton(x + 244, topPos + 202, 28, Component.literal("<"), () -> {
            materialPage = Math.max(0, materialPage - 1); rebuildPage();
        }));
        addRenderableWidget(localButton(x + 280, topPos + 202, 28, Component.literal(">"), () -> {
            materialPage++; rebuildPage();
        }));
    }

    private void buildParts() {
        UltimateTerminalSnapshot snapshot = UltimateTerminalClientState.snapshot();
        int x = leftPos + 14;
        int y = topPos + 55;
        overrideButton = addRenderableWidget(menuButton(x, y, 190, UltimateTerminalMenu.BUTTON_OVERRIDE, Component.empty()));
        addRenderableWidget(localButton(x + 210, y, 46, Component.literal("<"), () -> {
            partPage = Math.max(0, partPage - 1); rebuildPage();
        }));
        addRenderableWidget(localButton(x + 262, y, 46, Component.literal(">"), () -> {
            partPage++; rebuildPage();
        }));
        if (snapshot == null) return;
        List<UltimateTerminalSnapshot.CandidateInfo> configurable = snapshot.candidates().stream()
                .filter(UltimateTerminalSnapshot.CandidateInfo::configurable).toList();
        partPage = Math.min(partPage, Math.max(0, (configurable.size() - 1) / 6));
        int start = partPage * 6;
        for (int row = 0; row < 6 && start + row < configurable.size(); row++) {
            var candidate = configurable.get(start + row);
            ResourceLocation id = blockId(candidate);
            int rowY = y + 30 + row * 23;
            Button down = localButton(x + 242, rowY, 28, Component.literal("-"), () -> {
                if (id != null) UltimateTerminalMessages.changePartFromClient(menu.containerId, id, -1);
            });
            Button up = localButton(x + 280, rowY, 28, Component.literal("+"), () -> {
                if (id != null) UltimateTerminalMessages.changePartFromClient(menu.containerId, id, 1);
            });
            down.active = candidate.requested() > 0;
            up.active = candidate.requested() < candidate.maximum();
            addRenderableWidget(down);
            addRenderableWidget(up);
        }
    }

    private static @Nullable ResourceLocation blockId(UltimateTerminalSnapshot.CandidateInfo candidate) {
        if (!(candidate.stack().getItem() instanceof BlockItem blockItem)) return null;
        return ForgeRegistries.BLOCKS.getKey(blockItem.getBlock());
    }

    private Button menuButton(int x, int y, int width, int id, Component label) {
        return Button.builder(label, ignored -> minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id))
                .bounds(x, y, width, 20).build();
    }

    private Button localButton(int x, int y, int width, Component label, Runnable action) {
        return Button.builder(label, ignored -> action.run()).bounds(x, y, width, 20).build();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (lastSnapshot != UltimateTerminalClientState.snapshot()) rebuildPage();
        else refreshLabels();
    }

    private void refreshLabels() {
        if (modeButton != null) {
            int modeId = Math.max(0, Math.min(UltimateTerminalMode.values().length - 1, menu.mode()));
            modeButton.setMessage(Component.translatable("gregsteamexpansion.ultimate_terminal.mode."
                    + UltimateTerminalMode.values()[modeId].name().toLowerCase()));
        }
        if (hatchButton != null) hatchButton.setMessage(Component.translatable(
                "gregsteamexpansion.ultimate_terminal.hatches", onOff(menu.buildHatches())));
        if (aeButton != null) aeButton.setMessage(Component.translatable(
                "gregsteamexpansion.ultimate_terminal.ae", onOff(menu.useAE())));
        if (overrideButton != null) overrideButton.setMessage(Component.translatable(
                menu.targetOverride() ? "gregsteamexpansion.ultimate_terminal.profile.override"
                        : "gregsteamexpansion.ultimate_terminal.profile.template"));
        if (displayButton != null) displayButton.setMessage(Component.translatable(
                "gregsteamexpansion.ultimate_terminal.display."
                        + UltimateTerminalClientState.displayMode().name().toLowerCase()));
        if (materialScopeButton != null) materialScopeButton.setMessage(Component.translatable(
                UltimateTerminalClientState.batchMaterials()
                        ? "gregsteamexpansion.ultimate_terminal.materials.batch"
                        : "gregsteamexpansion.ultimate_terminal.materials.target"));
    }

    private Component onOff(boolean enabled) {
        return Component.translatable(enabled ? "options.on" : "options.off");
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xEE171B20);
        graphics.fill(leftPos + 4, topPos + 4, leftPos + imageWidth - 4, topPos + imageHeight - 4, 0xFF343B43);
        graphics.fill(leftPos + 7, topPos + 7, leftPos + imageWidth - 7, topPos + imageHeight - 7, 0xFF101419);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, 12, 10, 0xFFE7C95A, false);
        UltimateTerminalSnapshot snapshot = UltimateTerminalClientState.snapshot();
        switch (page) {
            case TARGETS -> renderTargetDetails(graphics, snapshot);
            case PREVIEW -> renderPreviewDetails(graphics, snapshot);
            case MATERIALS -> renderMaterials(graphics, snapshot);
            case PARTS -> renderParts(graphics, snapshot);
        }
        int state = Math.max(0, Math.min(UltimateTerminalWorldData.JobState.values().length - 1, menu.state()));
        graphics.drawString(font, Component.translatable("gregsteamexpansion.ultimate_terminal.status",
                Component.translatable("gregsteamexpansion.ultimate_terminal.state."
                        + UltimateTerminalWorldData.JobState.values()[state].name().toLowerCase())),
                220, 216, 0xFFAAAAAA, false);
    }

    private void renderTargetDetails(GuiGraphics graphics, @Nullable UltimateTerminalSnapshot snapshot) {
        if (snapshot == null || snapshot.targets().isEmpty()) return;
        var target = snapshot.targets().get(Math.max(0, Math.min(snapshot.selectedTarget(), snapshot.targets().size() - 1)));
        graphics.drawString(font, target.dimension() + "  " + target.pos().toShortString(), 14, 180, 0xFFAAAAAA, false);
        graphics.drawString(font, Component.translatable("gregsteamexpansion.ultimate_terminal.targets",
                snapshot.targets().size(), UltimateTerminalWorldData.MAX_TARGETS), 220, 180, 0xFFD6D6D6, false);
    }

    private void renderPreviewDetails(GuiGraphics graphics, @Nullable UltimateTerminalSnapshot snapshot) {
        graphics.drawString(font, Component.translatable("gregsteamexpansion.ultimate_terminal.repeat",
                menu.repeatCount()), 48, 89, 0xFFD6D6D6, false);
        graphics.drawString(font, Component.translatable("gregsteamexpansion.ultimate_terminal.coil",
                menu.coilTier()), 206, 89, 0xFFD6D6D6, false);
        graphics.drawString(font, Component.translatable("gregsteamexpansion.ultimate_terminal.layer",
                UltimateTerminalClientState.layer()), 48, 173, 0xFFD6D6D6, false);
        if (snapshot != null) {
            long missing = snapshot.cells().stream().filter(cell -> cell.status() == UltimateStructurePlanner.CellStatus.MISSING).count();
            long conflict = snapshot.cells().stream().filter(cell -> cell.status() == UltimateStructurePlanner.CellStatus.CONFLICT).count();
            graphics.drawString(font, Component.translatable("gregsteamexpansion.ultimate_terminal.summary",
                    snapshot.cells().size(), missing, conflict), 14, 190, conflict > 0 ? 0xFFFF5555 : 0xFFAAAAAA, false);
            if (!snapshot.error().isBlank()) graphics.drawString(font, snapshot.error(), 14, 216, 0xFFFF5555, false);
        }
    }

    private void renderMaterials(GuiGraphics graphics, @Nullable UltimateTerminalSnapshot snapshot) {
        if (snapshot == null) return;
        var materials = UltimateTerminalClientState.batchMaterials()
                ? snapshot.batchMaterials() : snapshot.selectedMaterials();
        materialPage = Math.min(materialPage, Math.max(0, (materials.size() - 1) / 7));
        int start = materialPage * 7;
        for (int row = 0; row < 7 && start + row < materials.size(); row++) {
            var material = materials.get(start + row);
            int y = 84 + row * 20;
            graphics.renderItem(material.stack(), 14, y - 5);
            graphics.drawString(font, material.stack().getHoverName(), 35, y, 0xFFD6D6D6, false);
            graphics.drawString(font, Component.literal(material.required() + " / " + material.inventory()
                    + " / " + material.network() + " / " + material.missing()), 208, y,
                    material.missing() > 0 ? 0xFFFF5555 : 0xFF55FF55, false);
        }
        graphics.drawString(font, Component.translatable("gregsteamexpansion.ultimate_terminal.materials.legend"),
                14, 202, 0xFF888888, false);
    }

    private void renderParts(GuiGraphics graphics, @Nullable UltimateTerminalSnapshot snapshot) {
        if (snapshot == null) return;
        var parts = snapshot.candidates().stream().filter(UltimateTerminalSnapshot.CandidateInfo::configurable).toList();
        int start = partPage * 6;
        for (int row = 0; row < 6 && start + row < parts.size(); row++) {
            var part = parts.get(start + row);
            int y = 88 + row * 23;
            graphics.renderItem(part.stack(), 14, y - 5);
            graphics.drawString(font, part.stack().getHoverName(), 35, y, 0xFFD6D6D6, false);
            graphics.drawString(font, Component.literal(part.requested() + " / " + part.present()
                    + " / " + part.maximum()), 180, y, 0xFFAAAAAA, false);
        }
        graphics.drawString(font, Component.translatable("gregsteamexpansion.ultimate_terminal.parts.legend"),
                14, 216, 0xFF888888, false);
    }
}
