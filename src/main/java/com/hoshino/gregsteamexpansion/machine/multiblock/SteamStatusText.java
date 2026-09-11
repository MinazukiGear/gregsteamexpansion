package com.hoshino.gregsteamexpansion.machine.multiblock;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.Map;

/** Shared translation and color lookup for steam multiblock controller states. */
public final class SteamStatusText {

    private static final String FURNACE_STATUS_PREFIX =
            "gregsteamexpansion.machine.large_heat_storage_steam_furnace.status.";

    private static final Map<String, String> COMMON_TEXT = Map.of(
            "invalid_structure", "gtceu.multiblock.invalid_structure",
            "exhaust_obstructed", "gregsteamexpansion.multiblock.steam_exhaust_hatch_obstructed",
            "insufficient_outputs", "gtceu.recipe_logic.insufficient_out",
            "working_disabled", "gtceu.top.working_disabled",
            "low_steam", "gtceu.multiblock.steam.low_steam",
            "auxiliary_shortfall", "gtceu.multiblock.steam.low_steam",
            "working", "gtceu.multiblock.large_miner.working",
            "idle", "gtceu.multiblock.idling",
            "insufficient_inputs", "gtceu.recipe_logic.insufficient_in");

    private static final Map<String, ChatFormatting> COMMON_COLORS = Map.of(
            "invalid_structure", ChatFormatting.RED,
            "exhaust_obstructed", ChatFormatting.RED,
            "insufficient_outputs", ChatFormatting.RED,
            "working_disabled", ChatFormatting.YELLOW,
            "low_steam", ChatFormatting.YELLOW,
            "auxiliary_shortfall", ChatFormatting.YELLOW,
            "working", ChatFormatting.GREEN,
            "idle", ChatFormatting.GRAY);

    private SteamStatusText() {}

    public enum Profile {

        STANDARD(Map.of(), Map.of(), "gtceu.multiblock.idling"),
        FURNACE(
                Map.of(
                        "awaiting_original_size", FURNACE_STATUS_PREFIX + "awaiting_original_size",
                        "preheating", FURNACE_STATUS_PREFIX + "preheating",
                        "at_temperature_limit", FURNACE_STATUS_PREFIX + "at_temperature_limit",
                        "cooling", FURNACE_STATUS_PREFIX + "cooling"),
                Map.of(
                        "low_steam", ChatFormatting.RED,
                        "awaiting_original_size", ChatFormatting.YELLOW,
                        "preheating", ChatFormatting.YELLOW,
                        "insufficient_inputs", ChatFormatting.YELLOW,
                        "cooling", ChatFormatting.YELLOW,
                        "at_temperature_limit", ChatFormatting.AQUA),
                FURNACE_STATUS_PREFIX + "cooling"),
        VOID_PRODUCER(
                Map.of(
                        "disabled_by_config", "gregsteamexpansion.machine.void_producer.ui.disabled_by_config",
                        "exhaust_obstructed", "gregsteamexpansion.machine.void_producer.ui.exhaust_obstructed"),
                Map.of("disabled_by_config", ChatFormatting.YELLOW),
                "gtceu.multiblock.idling");

        private final Map<String, String> textOverrides;
        private final Map<String, ChatFormatting> colorOverrides;
        private final String fallbackText;

        Profile(Map<String, String> textOverrides, Map<String, ChatFormatting> colorOverrides,
                String fallbackText) {
            this.textOverrides = textOverrides;
            this.colorOverrides = colorOverrides;
            this.fallbackText = fallbackText;
        }
    }

    public static Component text(String statusId) {
        return text(statusId, Profile.STANDARD);
    }

    public static Component text(String statusId, Profile profile) {
        String key = profile.textOverrides.get(statusId);
        if (key == null) {
            key = COMMON_TEXT.getOrDefault(statusId, profile.fallbackText);
        }
        return Component.translatable(key);
    }

    public static ChatFormatting color(String statusId) {
        return color(statusId, Profile.STANDARD);
    }

    public static ChatFormatting color(String statusId, Profile profile) {
        ChatFormatting color = profile.colorOverrides.get(statusId);
        return color == null ? COMMON_COLORS.getOrDefault(statusId, ChatFormatting.GRAY) : color;
    }
}
