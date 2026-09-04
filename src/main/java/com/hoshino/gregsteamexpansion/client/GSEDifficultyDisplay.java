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

    public static String worldListSuffix(Difficulty difficulty) {
        return " " + difficulty.getDisplayColor() + "[" + I18n.get(difficulty.getDisplayNameKey()) + "]";
    }

    public static String titleSuffix(Difficulty difficulty) {
        return " [" + I18n.get(difficulty.getDisplayNameKey()) + "]";
    }
}
