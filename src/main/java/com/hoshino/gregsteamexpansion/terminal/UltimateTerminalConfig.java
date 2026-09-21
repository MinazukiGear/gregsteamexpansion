package com.hoshino.gregsteamexpansion.terminal;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.ForgeConfigSpec;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Live server-side throughput limit for persistent construction jobs. */
public final class UltimateTerminalConfig {
    public static final int DEFAULT_BLOCKS_PER_TICK = 32;
    public static final int MAX_SELECTION_CHANNELS = 32;
    public static final int MAX_CHANNEL_OPTIONS = 64;
    public static final int MAX_CHANNEL_INDEX = MAX_CHANNEL_OPTIONS - 1;
    public static final int AUTO_CHANNEL_SELECTION = -1;
    public static final List<String> DEFAULT_SELECTION_CHANNELS = List.of(
            "glass=minecraft:glass,gtceu:tempered_glass,gtceu:laminated_glass,gtceu:cleanroom_glass,gtceu:fusion_glass");

    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    private static final ForgeConfigSpec.IntValue BLOCKS_PER_TICK = BUILDER
            .comment(
                    "Maximum Ultimate Terminal block operations per server tick.",
                    "终极终端每个服务端 tick 最多执行的方块操作数。")
            .defineInRange("ultimateTerminalBlocksPerTick", DEFAULT_BLOCKS_PER_TICK, 1, 256);
    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> SELECTION_CHANNELS = BUILDER
            .comment(
                    "Ordered structure-selection channels used by the Ultimate Terminal.",
                    "Syntax: channel_id=namespace:block,namespace:block,...",
                    "A channel is shown only when at least two of its blocks are legal candidates for the selected structure.",
                    "Selection -1 means automatic; configured blocks use zero-based values 0-63 in the written order.",
                    "Up to 32 channels and 64 blocks per channel are accepted. The ids 'coil', 'module' and 'structure_size' are reserved by built-in controls.",
                    "终极终端的有序结构选材信道。格式：信道ID=命名空间:方块,命名空间:方块,...",
                    "只有当前结构至少允许其中两个方块时才显示该信道；-1 表示自动，配置条目按顺序对应 0–63。",
                    "最多接受 32 个信道、每个信道 64 个方块；coil、module 与 structure_size ID 由内置信道占用。")
            .defineListAllowEmpty("ultimateTerminalSelectionChannels", DEFAULT_SELECTION_CHANNELS,
                    value -> value instanceof String);

    public static final ForgeConfigSpec SPEC = BUILDER.build();

    private UltimateTerminalConfig() {}

    public static int blocksPerTick() {
        return BLOCKS_PER_TICK.get();
    }

    /** Raw entries shown by the in-game config editor, including entries that fail semantic parsing. */
    public static List<String> configuredSelectionChannelEntries() {
        return SELECTION_CHANNELS.get().stream().map(String::valueOf).toList();
    }

    public static boolean areSelectionChannelEntriesValid(List<String> entries) {
        if (entries.size() > MAX_SELECTION_CHANNELS) return false;
        Set<String> ids = new HashSet<>();
        for (String entry : entries) {
            SelectionChannel channel = parseChannel(entry);
            if (channel == null || !ids.add(channel.id())) return false;
        }
        return true;
    }

    /** Save path used by the Mods-screen editor. Runtime readers observe these live common-config values. */
    public static void setConfig(int blocksPerTick, List<String> selectionChannelEntries) {
        if (blocksPerTick < 1 || blocksPerTick > 256) {
            throw new IllegalArgumentException("Ultimate Terminal blocks per tick must be in [1, 256]");
        }
        if (!areSelectionChannelEntriesValid(selectionChannelEntries)) {
            throw new IllegalArgumentException("Invalid Ultimate Terminal selection channel list");
        }
        BLOCKS_PER_TICK.set(blocksPerTick);
        SELECTION_CHANNELS.set(List.copyOf(selectionChannelEntries));
    }

    public static void setBlocksPerTick(int blocksPerTick) {
        if (blocksPerTick < 1 || blocksPerTick > 256) {
            throw new IllegalArgumentException("Ultimate Terminal blocks per tick must be in [1, 256]");
        }
        BLOCKS_PER_TICK.set(blocksPerTick);
    }

    /** Save only the channel list from its dedicated editor without overwriting other terminal settings. */
    public static void setSelectionChannelEntries(List<String> selectionChannelEntries) {
        if (!areSelectionChannelEntriesValid(selectionChannelEntries)) {
            throw new IllegalArgumentException("Invalid Ultimate Terminal selection channel list");
        }
        SELECTION_CHANNELS.set(List.copyOf(selectionChannelEntries));
    }

    public static List<SelectionChannel> selectionChannels() {
        Map<String, SelectionChannel> channels = new LinkedHashMap<>();
        for (String configured : SELECTION_CHANNELS.get()) {
            SelectionChannel channel = parseChannel(configured);
            if (channel != null && channels.size() < MAX_SELECTION_CHANNELS) {
                channels.putIfAbsent(channel.id(), channel);
            }
        }
        return List.copyOf(channels.values());
    }

    public static SelectionChannel channel(String id) {
        if (id == null) return null;
        for (SelectionChannel channel : selectionChannels()) {
            if (channel.id().equals(id)) return channel;
        }
        return null;
    }

    public static SelectionChannel parseChannel(String configured) {
        if (configured == null) return null;
        int separator = configured.indexOf('=');
        if (separator <= 0 || separator == configured.length() - 1) return null;
        String id = configured.substring(0, separator).trim().toLowerCase(Locale.ROOT);
        if (!id.matches("[a-z0-9_.-]{1,32}") || id.equals("coil")
                || id.equals(UltimateTerminalModuleProvider.CHANNEL_ID)
                || id.equals(UltimateTerminalStructureVariants.CHANNEL_ID)) return null;
        List<ResourceLocation> blocks = new ArrayList<>();
        for (String token : configured.substring(separator + 1).split(",")) {
            ResourceLocation block = ResourceLocation.tryParse(token.trim());
            if (block != null && !blocks.contains(block)) {
                if (blocks.size() >= MAX_CHANNEL_OPTIONS) return null;
                blocks.add(block);
            }
        }
        return blocks.size() < 2 ? null : new SelectionChannel(id, List.copyOf(blocks));
    }

    public record SelectionChannel(String id, List<ResourceLocation> blocks) {}
}
