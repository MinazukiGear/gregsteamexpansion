package com.hoshino.gregsteamexpansion.client;

import com.hoshino.gregsteamexpansion.difficulty.Difficulty;

import net.minecraft.client.resources.language.I18n;

/**
 * Client-side difficulty display helpers: a colored "[name]" tag for world
 * list entries (green Easy, yellow Normal, red Expert) and a plain-text tag
 * for the window title.
 */
public final class GSEDifficultyDisplay {
    private GSEDifficultyDisplay() {}

    public static String worldListSuffix(boolean enabled, Difficulty difficulty) {
        String key = enabled ? difficulty.getDisplayNameKey()
                : "config.gregsteamexpansion.difficulty.disabled";
        return " " + (enabled ? difficulty.getDisplayColor() : net.minecraft.ChatFormatting.GRAY)
                + "[" + I18n.get(key) + "]";
    }

    public static String titleSuffix(boolean enabled, Difficulty difficulty) {
        String key = enabled ? difficulty.getDisplayNameKey()
                : "config.gregsteamexpansion.difficulty.disabled";
        return " [" + I18n.get(key) + "]";
    }
}
