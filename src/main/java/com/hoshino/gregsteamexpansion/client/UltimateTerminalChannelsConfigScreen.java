package com.hoshino.gregsteamexpansion.client;

import com.hoshino.gregsteamexpansion.terminal.UltimateTerminalConfig;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Structured editor mapping each channel value (0-63) to one block registry ID. */
public final class UltimateTerminalChannelsConfigScreen extends Screen {
    private static final int PANEL_HALF_WIDTH = 160;
    private static final int CONTENT_TOP = 56;
    private static final int ROW_HEIGHT = 24;
    private static final int FOOTER_BUTTON_WIDTH = 100;

    private static final Component GUIDE =
            Component.translatable("config.gregsteamexpansion.screen.ultimate_terminal.channels.guide");
    private static final Component INVALID_HINT =
            Component.translatable("config.gregsteamexpansion.screen.ultimate_terminal.channels.invalid");

    @Nullable
    private final Screen parent;
    private final List<ChannelDraft> channels = new ArrayList<>();
    private final List<EditBox> activeEditors = new ArrayList<>();
    private final List<VisibleRow> visibleRows = new ArrayList<>();
    private int expandedChannel = -1;
    private int page;
    private int pageCount = 1;
    @Nullable
    private Button doneButton;

    public UltimateTerminalChannelsConfigScreen(@Nullable Screen parent) {
        super(Component.translatable("config.gregsteamexpansion.screen.ultimate_terminal.channels.title"));
        this.parent = parent;
        load(UltimateTerminalConfig.configuredSelectionChannelEntries());
    }

    private void load(List<String> entries) {
        channels.clear();
        for (String entry : entries) {
            int separator = entry.indexOf('=');
            String id = separator < 0 ? entry.trim() : entry.substring(0, separator).trim();
            List<String> blocks = new ArrayList<>();
            if (separator >= 0) {
                for (String block : entry.substring(separator + 1).split(",", -1)) {
                    blocks.add(block.trim());
                }
            }
            while (blocks.size() < 2) blocks.add("");
            if (blocks.size() > UltimateTerminalConfig.MAX_CHANNEL_OPTIONS) {
                blocks = new ArrayList<>(blocks.subList(0, UltimateTerminalConfig.MAX_CHANNEL_OPTIONS));
            }
            channels.add(new ChannelDraft(id, blocks));
        }
        if (channels.size() > UltimateTerminalConfig.MAX_SELECTION_CHANNELS) {
            channels.subList(UltimateTerminalConfig.MAX_SELECTION_CHANNELS, channels.size()).clear();
        }
    }

    @Override
    protected void init() {
        activeEditors.clear();
        visibleRows.clear();
        int left = this.width / 2 - PANEL_HALF_WIDTH;

        Button addChannel = this.addRenderableWidget(Button.builder(
                        Component.translatable("config.gregsteamexpansion.screen.ultimate_terminal.channels.add_channel"),
                        button -> addChannel())
                .bounds(left, 31, 110, 20).build());
        addChannel.active = channels.size() < UltimateTerminalConfig.MAX_SELECTION_CHANNELS;

        List<RowRef> rows = flattenedRows();
        int rowsPerPage = rowsPerPage();
        pageCount = Math.max(1, (rows.size() + rowsPerPage - 1) / rowsPerPage);
        page = Math.max(0, Math.min(page, pageCount - 1));
        int start = page * rowsPerPage;
        for (int row = 0; row < rowsPerPage && start + row < rows.size(); row++) {
            addRowWidgets(rows.get(start + row), left, CONTENT_TOP + row * ROW_HEIGHT);
        }

        int pagerY = this.height - 68;
        Button previous = this.addRenderableWidget(Button.builder(Component.literal("<"), button -> {
            page--;
            rebuildWidgets();
        }).bounds(this.width / 2 - 70, pagerY, 28, 20).build());
        Button next = this.addRenderableWidget(Button.builder(Component.literal(">"), button -> {
            page++;
            rebuildWidgets();
        }).bounds(this.width / 2 + 42, pagerY, 28, 20).build());
        previous.active = page > 0;
        next.active = page + 1 < pageCount;

        int footerY = this.height - 42;
        this.addRenderableWidget(Button.builder(
                        Component.translatable("config.gregsteamexpansion.screen.reset"), button -> resetDefaults())
                .bounds(left, footerY, FOOTER_BUTTON_WIDTH, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), button -> onClose())
                .bounds(this.width / 2 - 51, footerY, FOOTER_BUTTON_WIDTH, 20).build());
        this.doneButton = this.addRenderableWidget(Button.builder(Component.translatable("gui.done"),
                        button -> saveAndClose())
                .bounds(this.width / 2 + 53, footerY, FOOTER_BUTTON_WIDTH, 20).build());
        updateValidity();
    }

    private void addRowWidgets(RowRef row, int left, int y) {
        visibleRows.add(new VisibleRow(row, y));
        ChannelDraft channel = channels.get(row.channelIndex());
        if (row.kind() == RowKind.CHANNEL) {
            boolean expanded = expandedChannel == row.channelIndex();
            this.addRenderableWidget(Button.builder(
                            Component.literal((expanded ? "▼ " : "▶ ") + channel.blocks.size() + "/64"),
                            button -> {
                                expandedChannel = expanded ? -1 : row.channelIndex();
                                page = row.channelIndex() / rowsPerPage();
                                rebuildWidgets();
                            })
                    .bounds(left, y, 60, 20).build());
            EditBox id = new EditBox(this.font, left + 64, y, 184, 20,
                    Component.translatable("config.gregsteamexpansion.screen.ultimate_terminal.channels.channel_id"));
            id.setMaxLength(32);
            id.setFilter(value -> value.matches("[a-z0-9_.-]*"));
            id.setValue(channel.id);
            id.setResponder(value -> {
                channel.id = value;
                updateValidity();
            });
            activeEditors.add(id);
            this.addRenderableWidget(id);
            this.addRenderableWidget(Button.builder(
                            Component.translatable("config.gregsteamexpansion.screen.ultimate_terminal.channels.delete"),
                            button -> deleteChannel(row.channelIndex()))
                    .bounds(left + 252, y, 68, 20).build());
            return;
        }
        if (row.kind() == RowKind.ENTRY) {
            int entryIndex = row.entryIndex();
            EditBox block = new EditBox(this.font, left + 48, y, 204, 20,
                    Component.translatable("config.gregsteamexpansion.screen.ultimate_terminal.channels.block_id"));
            block.setMaxLength(256);
            block.setFilter(value -> value.matches("[a-z0-9_.:/-]*"));
            block.setValue(channel.blocks.get(entryIndex));
            block.setResponder(value -> {
                channel.blocks.set(entryIndex, value);
                updateValidity();
            });
            activeEditors.add(block);
            this.addRenderableWidget(block);
            Button delete = this.addRenderableWidget(Button.builder(
                            Component.translatable("config.gregsteamexpansion.screen.ultimate_terminal.channels.delete"),
                            button -> deleteEntry(row.channelIndex(), entryIndex))
                    .bounds(left + 256, y, 64, 20).build());
            delete.active = channel.blocks.size() > 2;
            return;
        }
        Button addEntry = this.addRenderableWidget(Button.builder(
                        Component.translatable("config.gregsteamexpansion.screen.ultimate_terminal.channels.add_entry"),
                        button -> addEntry(row.channelIndex()))
                .bounds(left + 48, y, 204, 20).build());
        addEntry.active = channel.blocks.size() < UltimateTerminalConfig.MAX_CHANNEL_OPTIONS;
    }

    private List<RowRef> flattenedRows() {
        List<RowRef> rows = new ArrayList<>();
        for (int channel = 0; channel < channels.size(); channel++) {
            rows.add(new RowRef(RowKind.CHANNEL, channel, -1));
            if (channel == expandedChannel) {
                for (int entry = 0; entry < channels.get(channel).blocks.size(); entry++) {
                    rows.add(new RowRef(RowKind.ENTRY, channel, entry));
                }
                rows.add(new RowRef(RowKind.ADD_ENTRY, channel, -1));
            }
        }
        return rows;
    }

    private void addChannel() {
        if (channels.size() >= UltimateTerminalConfig.MAX_SELECTION_CHANNELS) return;
        int suffix = channels.size();
        Set<String> existing = new HashSet<>();
        channels.forEach(channel -> existing.add(channel.id));
        String id;
        do id = "channel_" + suffix++; while (existing.contains(id));
        channels.add(new ChannelDraft(id, new ArrayList<>(List.of("", ""))));
        expandedChannel = channels.size() - 1;
        page = Integer.MAX_VALUE;
        rebuildWidgets();
    }

    private void deleteChannel(int channelIndex) {
        channels.remove(channelIndex);
        if (expandedChannel == channelIndex) expandedChannel = -1;
        else if (expandedChannel > channelIndex) expandedChannel--;
        rebuildWidgets();
    }

    private void addEntry(int channelIndex) {
        ChannelDraft channel = channels.get(channelIndex);
        if (channel.blocks.size() >= UltimateTerminalConfig.MAX_CHANNEL_OPTIONS) return;
        channel.blocks.add("");
        page = (channelIndex + channel.blocks.size()) / rowsPerPage();
        rebuildWidgets();
    }

    private void deleteEntry(int channelIndex, int entryIndex) {
        ChannelDraft channel = channels.get(channelIndex);
        if (channel.blocks.size() <= 2) return;
        channel.blocks.remove(entryIndex);
        rebuildWidgets();
    }

    private void resetDefaults() {
        load(UltimateTerminalConfig.DEFAULT_SELECTION_CHANNELS);
        expandedChannel = channels.isEmpty() ? -1 : 0;
        page = 0;
        rebuildWidgets();
    }

    private List<String> serializedEntries() {
        return channels.stream().map(channel -> channel.id.trim() + "=" + channel.blocks.stream()
                        .map(String::trim).collect(java.util.stream.Collectors.joining(",")))
                .toList();
    }

    private int rowsPerPage() {
        return Math.max(2, (this.height - CONTENT_TOP - 78) / ROW_HEIGHT);
    }

    private boolean isValid() {
        List<String> serialized = serializedEntries();
        if (!UltimateTerminalConfig.areSelectionChannelEntriesValid(serialized)) return false;
        for (int i = 0; i < channels.size(); i++) {
            ChannelDraft draft = channels.get(i);
            UltimateTerminalConfig.SelectionChannel parsed = UltimateTerminalConfig.parseChannel(serialized.get(i));
            if (parsed == null || parsed.blocks().size() != draft.blocks.size()) return false;
            for (int option = 0; option < draft.blocks.size(); option++) {
                ResourceLocation id = ResourceLocation.tryParse(draft.blocks.get(option).trim());
                if (id == null || !id.equals(parsed.blocks().get(option))) return false;
            }
        }
        return true;
    }

    private void updateValidity() {
        if (doneButton != null) doneButton.active = isValid();
    }

    private void saveAndClose() {
        if (!isValid()) return;
        UltimateTerminalConfig.setSelectionChannelEntries(serializedEntries());
        onClose();
    }

    @Override
    public void tick() {
        activeEditors.forEach(EditBox::tick);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 12, 0xFFFFFF);
        graphics.drawWordWrap(this.font, GUIDE,
                this.width / 2 - PANEL_HALF_WIDTH + 116, 32, 204, 0x808080);
        for (VisibleRow visible : visibleRows) {
            if (visible.row.kind() == RowKind.ENTRY) {
                graphics.drawString(this.font, Component.literal(visible.row.entryIndex() + " →"),
                        this.width / 2 - PANEL_HALF_WIDTH + 10, visible.y + 6, 0xFF55FFFF);
            }
        }
        graphics.drawCenteredString(this.font, Component.literal((page + 1) + " / " + pageCount),
                this.width / 2, this.height - 62, 0x808080);
        if (!isValid()) {
            graphics.drawCenteredString(this.font, INVALID_HINT, this.width / 2, this.height - 16, 0xFF5555);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) this.minecraft.setScreen(this.parent);
    }

    private enum RowKind { CHANNEL, ENTRY, ADD_ENTRY }

    private record RowRef(RowKind kind, int channelIndex, int entryIndex) {}

    private record VisibleRow(RowRef row, int y) {}

    private static final class ChannelDraft {
        private String id;
        private final List<String> blocks;

        private ChannelDraft(String id, List<String> blocks) {
            this.id = id;
            this.blocks = blocks;
        }
    }
}
