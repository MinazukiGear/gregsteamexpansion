package com.hoshino.gregsteamexpansion.terminal;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.ForgeConfigSpec;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Live server-side throughput limit for persistent construction jobs. */
public final class UltimateTerminalConfig {
    public static final int MAX_SELECTION_CHANNELS = 32;
    public static final int MAX_CHANNEL_OPTIONS = 64;

    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    private static final ForgeConfigSpec.IntValue BLOCKS_PER_TICK = BUILDER
            .comment(
                    "Maximum Ultimate Terminal block operations per server tick.",
                    "终极终端每个服务端 tick 最多执行的方块操作数。")
            .defineInRange("ultimateTerminalBlocksPerTick", 32, 1, 256);
    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> SELECTION_CHANNELS = BUILDER
            .comment(
                    "Ordered structure-selection channels used by the Ultimate Terminal.",
                    "Syntax: channel_id=namespace:block,namespace:block,...",
                    "A channel is shown only when at least two of its blocks are legal candidates for the selected structure.",
                    "Index 0 in the terminal means automatic; later values follow the order written here.",
                    "Up to 32 channels and 64 blocks per channel are accepted. The ids 'coil' and 'structure_size' are reserved by built-in controls.",
                    "终极终端的有序结构选材信道。格式：信道ID=命名空间:方块,命名空间:方块,...",
                    "只有当前结构至少允许其中两个方块时才显示该信道；终端中的 0 表示自动，其余档位按此处顺序排列。",
                    "最多接受 32 个信道、每个信道 64 个方块；coil 与 structure_size ID 由内置信道占用。")
            .defineListAllowEmpty("ultimateTerminalSelectionChannels", List.of(
                    "glass=minecraft:glass,gtceu:tempered_glass,gtceu:laminated_glass,gtceu:cleanroom_glass,gtceu:fusion_glass"
            ), value -> value instanceof String);

    public static final ForgeConfigSpec SPEC = BUILDER.build();

    private UltimateTerminalConfig() {}

    public static int blocksPerTick() {
        return BLOCKS_PER_TICK.get();
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
                || id.equals(UltimateTerminalStructureVariants.CHANNEL_ID)) return null;
        List<ResourceLocation> blocks = new ArrayList<>();
        for (String token : configured.substring(separator + 1).split(",")) {
            ResourceLocation block = ResourceLocation.tryParse(token.trim());
            if (block != null && !blocks.contains(block) && blocks.size() < MAX_CHANNEL_OPTIONS) {
                blocks.add(block);
            }
        }
        return blocks.size() < 2 ? null : new SelectionChannel(id, List.copyOf(blocks));
    }

    public record SelectionChannel(String id, List<ResourceLocation> blocks) {}
}
