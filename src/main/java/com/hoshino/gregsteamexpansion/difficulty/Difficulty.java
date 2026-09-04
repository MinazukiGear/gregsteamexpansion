package com.hoshino.gregsteamexpansion.difficulty;

import net.minecraft.ChatFormatting;

import org.jetbrains.annotations.Nullable;

/**
 * Global work-intensity tiers ("difficulty"). The order EASY &lt; NORMAL &lt; EXPERT
 * and the NORMAL default are save-compatible data (difficulty.md 档位定义) and must
 * never be reshuffled or renamed.
 */
public enum Difficulty {
    EASY("easy", 2, 5.0F, 2),
    NORMAL("normal", 1, 5.0F, 1),
    EXPERT("expert", 1, 2.0F, 1);

    private final String serialName;
    private final int casingsPerCraft;
    private final float steamOutputMultiplier;
    private final int singleblockSteamCacheMultiplier;

    Difficulty(String serialName, int casingsPerCraft, float steamOutputMultiplier,
               int singleblockSteamCacheMultiplier) {
        this.serialName = serialName;
        this.casingsPerCraft = casingsPerCraft;
        this.steamOutputMultiplier = steamOutputMultiplier;
        this.singleblockSteamCacheMultiplier = singleblockSteamCacheMultiplier;
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

    /** Forced value for the GTCEu {@code recipes.casingsPerCraft} whitelist entry. */
    public int getCasingsPerCraft() {
        return casingsPerCraft;
    }

    /** Multiplier applied to every whitelisted dedicated steam boiler's output. */
    public float getSteamOutputMultiplier() {
        return steamOutputMultiplier;
    }

    /** Cache multiplier for single-block boiler steam tanks only. */
    public int getSingleblockSteamCacheMultiplier() {
        return singleblockSteamCacheMultiplier;
    }

    public boolean isLowerThan(Difficulty other) {
        return ordinal() < other.ordinal();
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
