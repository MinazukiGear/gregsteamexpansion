package com.hoshino.gregsteamexpansion.difficulty;

import net.minecraft.ChatFormatting;

import org.jetbrains.annotations.Nullable;

/**
 * Global work-intensity tiers ("difficulty"). The order EASY &lt; NORMAL &lt; EXPERT
 * and the NORMAL default are config-compatible data (difficulty.md 档位定义) and must
 * never be reshuffled or renamed.
 */
public enum Difficulty {
    EASY("easy"),
    NORMAL("normal"),
    EXPERT("expert");

    private final String serialName;

    Difficulty(String serialName) {
        this.serialName = serialName;
    }

    public String getSerializedName() {
        return serialName;
    }

    public String getDisplayNameKey() {
        return "config.gregsteamexpansion.difficulty." + serialName;
    }

    /** Green / yellow / red display color for the difficulty tag. */
    public ChatFormatting getDisplayColor() {
        return switch (this) {
            case EASY -> ChatFormatting.GREEN;
            case NORMAL -> ChatFormatting.YELLOW;
            case EXPERT -> ChatFormatting.RED;
        };
    }

    @Nullable
    public static Difficulty byName(@Nullable String name) {
        if (name != null && !name.isEmpty()) {
            for (Difficulty difficulty : values()) {
                if (difficulty.serialName.equalsIgnoreCase(name.trim())) {
                    return difficulty;
                }
            }
        }
        return null;
    }
}
