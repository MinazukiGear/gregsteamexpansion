package com.hoshino.gregsteamexpansion.difficulty;

import net.minecraft.ChatFormatting;

import org.jetbrains.annotations.Nullable;

/**
 * Global work-intensity tiers ("difficulty"). The order EASY &lt; NORMAL &lt; EXPERT
 * and the NORMAL default are save-compatible data (difficulty.md 档位定义) and must
 * never be reshuffled or renamed.
 */
public enum Difficulty {
    EASY("easy", 2, 5.0F, 2, 40, 2, 50, 2.0F),
    NORMAL("normal", 1, 5.0F, 1, 100, 5, 100, 1.5F),
    EXPERT("expert", 1, 2.0F, 1, 220, 10, 100, 1.0F);

    private final String serialName;
    private final int casingsPerCraft;
    /** Ore-crushing main product multiplier (ore-crushing.md: 2× / 1.5× / 1×). */
    private final float oreCrushingMultiplier;
    private final float steamOutputMultiplier;
    private final int singleblockSteamCacheMultiplier;
    /** Large heat-storage steam furnace preheating steam cost in percent (Normal = 100). */
    private final int preheatCostPercent;
    /** Ticks required per +1°C while preheating. */
    private final int preheatIntervalTicks;
    /** Large heat-storage steam furnace processing steam consumption in percent. */
    private final int processingSteamPercent;

    Difficulty(String serialName, int casingsPerCraft, float steamOutputMultiplier,
               int singleblockSteamCacheMultiplier, int preheatCostPercent, int preheatIntervalTicks,
               int processingSteamPercent, float oreCrushingMultiplier) {
        this.serialName = serialName;
        this.casingsPerCraft = casingsPerCraft;
        this.steamOutputMultiplier = steamOutputMultiplier;
        this.singleblockSteamCacheMultiplier = singleblockSteamCacheMultiplier;
        this.preheatCostPercent = preheatCostPercent;
        this.preheatIntervalTicks = preheatIntervalTicks;
        this.processingSteamPercent = processingSteamPercent;
        this.oreCrushingMultiplier = oreCrushingMultiplier;
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

    /**
     * Furnace preheating steam cost percent (difficulty.md 大型蓄热蒸汽熔炉:
     * 预热总消耗倍率 0.4× / 1.0× / 2.2×). Integer percent keeps the per-degree
     * cost exact when scaled: cost = (宽²−4)×高×2 × percent / 100 mB.
     */
    public int getPreheatCostPercent() {
        return preheatCostPercent;
    }

    /** Ticks needed per +1°C while preheating (2 / 5 / 10). */
    public int getPreheatIntervalTicks() {
        return preheatIntervalTicks;
    }

    /** Processing steam consumption percent (Easy halves it; others unchanged). */
    public int getProcessingSteamPercent() {
        return processingSteamPercent;
    }

    /**
     * Ore-crushing main product multiplier (ore-crushing.md 难度倍率): baked
     * into migrated recipes on top of the ore-only 4× baseline; chance
     * outputs are never multiplied.
     */
    public float getOreCrushingMultiplier() {
        return oreCrushingMultiplier;
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
