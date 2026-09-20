package com.hoshino.gregsteamexpansion.client;

import com.hoshino.gregsteamexpansion.menu.UltimateTerminalMenu;
import com.hoshino.gregsteamexpansion.terminal.UltimateStructurePlanner;
import com.hoshino.gregsteamexpansion.terminal.UltimateTerminalConfig;
import com.hoshino.gregsteamexpansion.terminal.UltimateTerminalMessages;
import com.hoshino.gregsteamexpansion.terminal.UltimateTerminalMode;
import com.hoshino.gregsteamexpansion.terminal.UltimateTerminalSnapshot;
import com.hoshino.gregsteamexpansion.terminal.UltimateTerminalStructureVariants;
import com.hoshino.gregsteamexpansion.terminal.UltimateTerminalWorldData;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Engineering console for targets, hologram controls, materials, channels and block quotas. */
public final class UltimateTerminalScreen extends AbstractContainerScreen<UltimateTerminalMenu> {
    private enum Page { TARGETS, PREVIEW, MATERIALS, CHANNELS, QUOTAS }

    private Page page = Page.TARGETS;
    private int targetPage;
    private int quotaPage;
    private int channelPage;
    private int materialPage;
    private @Nullable String openDropdown;
    private int dropdownPage;
    private @Nullable UltimateTerminalSnapshot lastSnapshot;
    private Button modeButton;
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
        aeButton = null;
        overrideButton = null;
        displayButton = null;
        materialScopeButton = null;
        int x = leftPos + 10;
        int y = topPos + 25;
        for (Page candidate : Page.values()) {
            Page selectedPage = candidate;
            addRenderableWidget(localButton(x + candidate.ordinal() * 64, y, 60,
                    Component.translatable("gregsteamexpansion.ultimate_terminal.tab."
                            + candidate.name().toLowerCase()), () -> {
                        page = selectedPage;
                        openDropdown = null;
                        rebuildPage();
                    }));
        }
        switch (page) {
            case TARGETS -> buildTargets();
            case PREVIEW -> buildPreview();
            case MATERIALS -> buildMaterials();
            case CHANNELS -> buildChannels();
            case QUOTAS -> buildQuotas();
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
        aeButton = addRenderableWidget(menuButton(x + 158, y + 28, 150, UltimateTerminalMenu.BUTTON_AE, Component.empty()));
        overrideButton = addRenderableWidget(menuButton(x, y + 56, 150, UltimateTerminalMenu.BUTTON_OVERRIDE, Component.empty()));
        displayButton = addRenderableWidget(localButton(x + 158, y + 56, 150, Component.empty(), () -> {
            UltimateTerminalClientState.cycleDisplayMode(); refreshLabels();
        }));
        addRenderableWidget(localButton(x, y + 84, 28, Component.literal("-"), () -> {
            UltimateTerminalClientState.changeLayer(-1); refreshLabels();
        }));
        addRenderableWidget(localButton(x + 122, y + 84, 28, Component.literal("+"), () -> {
            UltimateTerminalClientState.changeLayer(1); refreshLabels();
        }));
        addRenderableWidget(localButton(x + 158, y + 84, 150,
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

    private void buildQuotas() {
        UltimateTerminalSnapshot snapshot = UltimateTerminalClientState.snapshot();
        int x = leftPos + 14;
        int y = topPos + 55;
        overrideButton = addRenderableWidget(menuButton(x, y, 190, UltimateTerminalMenu.BUTTON_OVERRIDE, Component.empty()));
        addRenderableWidget(localButton(x + 210, y, 46, Component.literal("<"), () -> {
            quotaPage = Math.max(0, quotaPage - 1); rebuildPage();
        }));
        addRenderableWidget(localButton(x + 262, y, 46, Component.literal(">"), () -> {
            quotaPage++; rebuildPage();
        }));
        if (snapshot == null) return;
        List<UltimateTerminalSnapshot.CandidateInfo> configurable = snapshot.candidates().stream()
                .filter(UltimateTerminalSnapshot.CandidateInfo::configurable).toList();
        int totalRows = configurable.size();
        quotaPage = Math.min(quotaPage, Math.max(0, (totalRows - 1) / 6));
        int start = quotaPage * 6;
        for (int row = 0; row < 6 && start + row < totalRows; row++) {
            int index = start + row;
            int rowY = y + 30 + row * 23;
            var candidate = configurable.get(index);
            ResourceLocation id = blockId(candidate);
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

    private void buildChannels() {
        UltimateTerminalSnapshot snapshot = UltimateTerminalClientState.snapshot();
        if (snapshot == null) return;
        int x = leftPos + 14;
        int y = topPos + 55;
        int structureRows = snapshot.structure().options().isEmpty() ? 0 : 1;
        int totalRows = structureRows + snapshot.channels().size();
        channelPage = Math.min(channelPage, Math.max(0, (totalRows - 1) / 6));
        int start = channelPage * 6;
        for (int row = 0; row < 6 && start + row < totalRows; row++) {
            int index = start + row;
            if (index < structureRows) {
                var structure = snapshot.structure();
                Component selected = structure.selected() >= 0 && structure.selected() < structure.options().size()
                        ? Component.literal(structure.options().get(structure.selected())) : Component.empty();
                addRenderableWidget(dropdownButton(x + 132, y + row * 23, 176,
                        UltimateTerminalStructureVariants.CHANNEL_ID, selected));
            } else {
                var channel = snapshot.channels().get(index - structureRows);
                ItemStack selected = channel.selected() >= 0 && channel.selected() < channel.options().size()
                        ? channel.options().get(channel.selected()) : ItemStack.EMPTY;
                addRenderableWidget(dropdownButton(x + 132, y + row * 23, 176, channel.id(), selected.isEmpty()
                        ? Component.translatable("gregsteamexpansion.ultimate_terminal.channel.auto")
                        : selected.getHoverName()));
            }
        }
        addRenderableWidget(localButton(x + 230, topPos + 202, 34, Component.literal("<"), () -> {
            channelPage = Math.max(0, channelPage - 1); rebuildPage();
        }));
        addRenderableWidget(localButton(x + 274, topPos + 202, 34, Component.literal(">"), () -> {
            channelPage++; rebuildPage();
        }));
        buildOpenDropdown(snapshot, x);
    }

    private Button dropdownButton(int x, int y, int width, String id, Component value) {
        return localButton(x, y, width, Component.literal("▼ ").append(value), () -> {
            openDropdown = id.equals(openDropdown) ? null : id;
            dropdownPage = 0;
            rebuildPage();
        });
    }

    private void buildOpenDropdown(UltimateTerminalSnapshot snapshot, int x) {
        if (openDropdown == null) return;
        List<Component> options;
        int firstSelection;
        if (openDropdown.equals(UltimateTerminalStructureVariants.CHANNEL_ID)) {
            options = snapshot.structure().options().stream()
                    .map(value -> (Component) Component.literal(value)).toList();
            firstSelection = 0;
        } else {
            var channel = snapshot.channels().stream().filter(value -> value.id().equals(openDropdown))
                    .findFirst().orElse(null);
            if (channel == null) {
                openDropdown = null;
                return;
            }
            java.util.ArrayList<Component> values = new java.util.ArrayList<>();
            values.add(Component.translatable("gregsteamexpansion.ultimate_terminal.channel.auto"));
            channel.options().forEach(stack -> values.add(stack.getHoverName()));
            options = List.copyOf(values);
            firstSelection = UltimateTerminalConfig.AUTO_CHANNEL_SELECTION;
        }
        int pageSize = 5;
        int pages = Math.max(1, (options.size() - 1) / pageSize + 1);
        dropdownPage = Math.min(dropdownPage, pages - 1);
        int start = dropdownPage * pageSize;
        int popupX = x + 96;
        int popupY = topPos + 55;
        for (int row = 0; row < pageSize && start + row < options.size(); row++) {
            int optionIndex = start + row;
            int selection = firstSelection + optionIndex;
            addRenderableWidget(localButton(popupX, popupY + row * 22, 212, options.get(optionIndex), () -> {
                UltimateTerminalMessages.selectChannelFromClient(menu.containerId, openDropdown, selection);
                openDropdown = null;
                rebuildPage();
            }));
        }
        if (pages > 1) {
            addRenderableWidget(localButton(popupX, popupY + 112, 103, Component.literal("<"), () -> {
                dropdownPage = Math.max(0, dropdownPage - 1); rebuildPage();
            }));
            addRenderableWidget(localButton(popupX + 109, popupY + 112, 103, Component.literal(">"), () -> {
                dropdownPage = Math.min(pages - 1, dropdownPage + 1); rebuildPage();
            }));
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
            case CHANNELS -> renderChannels(graphics, snapshot);
            case QUOTAS -> renderQuotas(graphics, snapshot);
        }
        int state = Math.max(0, Math.min(UltimateTerminalWorldData.JobState.values().length - 1, menu.state()));
        Component status = Component.translatable("gregsteamexpansion.ultimate_terminal.status",
                Component.translatable("gregsteamexpansion.ultimate_terminal.state."
                        + UltimateTerminalWorldData.JobState.values()[state].name().toLowerCase()));
        graphics.drawString(font, status, imageWidth - 12 - font.width(status), 10, 0xFFAAAAAA, false);
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
        graphics.drawString(font, Component.translatable("gregsteamexpansion.ultimate_terminal.layer",
                UltimateTerminalClientState.layer()), 48, 145, 0xFFD6D6D6, false);
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
            Component availability = snapshot.unlimitedMaterials()
                    ? Component.translatable("gregsteamexpansion.ultimate_terminal.materials.unlimited",
                            material.required())
                    : Component.literal(material.required() + " / " + material.inventory()
                            + " / " + material.network() + " / " + material.missing());
            graphics.drawString(font, availability, 208, y,
                    snapshot.unlimitedMaterials() || material.missing() == 0 ? 0xFF55FF55 : 0xFFFF5555, false);
        }
        String legend = snapshot.unlimitedMaterials()
                ? "gregsteamexpansion.ultimate_terminal.materials.creative_legend"
                : "gregsteamexpansion.ultimate_terminal.materials.legend";
        graphics.drawString(font, Component.translatable(legend),
                14, 202, 0xFF888888, false);
    }

    private void renderQuotas(GuiGraphics graphics, @Nullable UltimateTerminalSnapshot snapshot) {
        if (snapshot == null) return;
        var parts = snapshot.candidates().stream().filter(UltimateTerminalSnapshot.CandidateInfo::configurable).toList();
        int start = quotaPage * 6;
        int totalRows = parts.size();
        if (totalRows == 0) graphics.drawString(font,
                Component.translatable("gregsteamexpansion.ultimate_terminal.quotas.empty"),
                14, 88, 0xFFAAAAAA, false);
        for (int row = 0; row < 6 && start + row < totalRows; row++) {
            int index = start + row;
            int y = 88 + row * 23;
            var part = parts.get(index);
            graphics.renderItem(part.stack(), 14, y - 5);
            graphics.drawString(font, part.stack().getHoverName(), 35, y, 0xFFD6D6D6, false);
            graphics.drawString(font, Component.literal(part.requested() + " / " + part.present()
                    + " / " + part.maximum()), 180, y, 0xFFAAAAAA, false);
        }
        graphics.drawString(font, Component.translatable("gregsteamexpansion.ultimate_terminal.quotas.legend"),
                14, 216, 0xFF888888, false);
    }

    private void renderChannels(GuiGraphics graphics, @Nullable UltimateTerminalSnapshot snapshot) {
        if (snapshot == null) return;
        int structureRows = snapshot.structure().options().isEmpty() ? 0 : 1;
        int totalRows = structureRows + snapshot.channels().size();
        int start = channelPage * 6;
        for (int row = 0; row < 6 && start + row < totalRows; row++) {
            int index = start + row;
            if (index < structureRows) {
                graphics.drawString(font,
                        Component.translatable("gregsteamexpansion.ultimate_terminal.channel.structure_size"),
                        14, 60 + row * 23, 0xFF55FFFF, false);
            } else {
                var channel = snapshot.channels().get(index - structureRows);
                ItemStack selected = channel.selected() >= 0 && channel.selected() < channel.options().size()
                        ? channel.options().get(channel.selected()) : ItemStack.EMPTY;
                if (!selected.isEmpty()) graphics.renderItem(selected, 106, 55 + row * 23);
                Component name = channel.id().equals("coil")
                        ? Component.translatable("gregsteamexpansion.ultimate_terminal.channel.coil")
                        : Component.literal(channel.id());
                graphics.drawString(font, name, 14, 60 + row * 23, 0xFF55FFFF, false);
            }
        }
        if (totalRows == 0) graphics.drawString(font,
                Component.translatable("gregsteamexpansion.ultimate_terminal.channels.empty"),
                14, 60, 0xFFAAAAAA, false);
        graphics.drawString(font, Component.translatable("gregsteamexpansion.ultimate_terminal.channels.legend"),
                14, 216, 0xFF888888, false);
    }
}
