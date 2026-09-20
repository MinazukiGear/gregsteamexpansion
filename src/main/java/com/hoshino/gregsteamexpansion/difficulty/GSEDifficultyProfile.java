package com.hoshino.gregsteamexpansion.difficulty;

import com.gregtechceu.gtceu.config.ConfigHolder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Immutable, startup-captured values for one difficulty tier. Defaults preserve
 * the original built-in balance, while every field can be overridden by a
 * modpack through {@code gregsteamexpansion-common.toml}.
 */
public record GSEDifficultyProfile(
                                   int gtceuCasingsPerCraft,
                                   double steamOutputMultiplier,
                                   int singleblockSteamCacheMultiplier,
                                   int preheatCostPercent,
                                   int preheatIntervalTicks,
                                   int processingSteamPercent,
                                   double oreCrushingMultiplier,
                                   double boilerRoomSteamOutputMultiplier,
                                   double assemblerOutputMultiplier,
                                   int voidProducerOutputMultiplier,
                                   int circuitAssemblerBonusChancePercent,
                                   int circuitAssemblerBonusMultiplier,
                                   boolean hardBronzeComponentRecipes,
                                   boolean harderSteamGrindingBlockRecipes,
                                   boolean hardSteamAssemblyBlockRecipes,
                                   boolean hardSteamCircuitAssemblyBlockRecipes,
                                   boolean hardSteamMixingBlockRecipes,
                                   boolean disableManualCompression,
                                   boolean harderRods,
                                   boolean harderBrickRecipes,
                                   boolean nerfWoodCrafting,
                                   boolean hardWoodRecipes,
                                   boolean hardIronRecipes,
                                   boolean hardRedstoneRecipes,
                                   boolean hardToolArmorRecipes,
                                   boolean hardMiscRecipes,
                                   boolean hardGlassRecipes,
                                   boolean nerfPaperCrafting,
                                   boolean hardAdvancedIronRecipes,
                                   boolean hardDyeRecipes,
                                   boolean harderCharcoalRecipe,
                                   boolean flintAndSteelRequireSteel,
                                   boolean removeVanillaBlockRecipes,
                                   boolean removeVanillaTNTRecipe,
                                   boolean harderCircuitRecipes,
                                   boolean hardMultiRecipes) {

    public static GSEDifficultyProfile defaults(Difficulty difficulty) {
        return switch (difficulty) {
            case EASY -> new GSEDifficultyProfile(
                    2, 5.0, 2, 40, 2, 50, 2.0, 3.0, 2.0, 4,
                    100, 7,
                    false, false, false, false, false,
                    false, false, false, false, false, false, false, false, false,
                    false, false, false, false, false, false, false, false, false, false);
            case NORMAL -> new GSEDifficultyProfile(
                    1, 5.0, 1, 100, 5, 100, 1.5, 2.0, 1.0, 2,
                    50, 3,
                    false, false, false, false, false,
                    true, true, false, false, false, true, false, false, false,
                    true, true, true, false, true, true, false, true, false, false);
            case EXPERT -> new GSEDifficultyProfile(
                    1, 2.0, 1, 220, 10, 100, 1.0, 1.5, 1.0, 1,
                    25, 1,
                    true, true, true, true, true,
                    true, true, true, true, true, true, true, true, true,
                    true, true, true, true, true, true, true, true, true, true);
        };
    }

    /** Applies the complete configurable GTCEu recipe profile at startup. */
    public void applyTo(ConfigHolder.RecipeConfigs recipes) {
        recipes.disableManualCompression = disableManualCompression;
        recipes.harderRods = harderRods;
        recipes.harderBrickRecipes = harderBrickRecipes;
        recipes.nerfWoodCrafting = nerfWoodCrafting;
        recipes.hardWoodRecipes = hardWoodRecipes;
        recipes.hardIronRecipes = hardIronRecipes;
        recipes.hardRedstoneRecipes = hardRedstoneRecipes;
        recipes.hardToolArmorRecipes = hardToolArmorRecipes;
        recipes.hardMiscRecipes = hardMiscRecipes;
        recipes.hardGlassRecipes = hardGlassRecipes;
        recipes.nerfPaperCrafting = nerfPaperCrafting;
        recipes.hardAdvancedIronRecipes = hardAdvancedIronRecipes;
        recipes.hardDyeRecipes = hardDyeRecipes;
        recipes.harderCharcoalRecipe = harderCharcoalRecipe;
        recipes.flintAndSteelRequireSteel = flintAndSteelRequireSteel;
        recipes.removeVanillaBlockRecipes = removeVanillaBlockRecipes;
        recipes.removeVanillaTNTRecipe = removeVanillaTNTRecipe;
        recipes.harderCircuitRecipes = harderCircuitRecipes;
        recipes.hardMultiRecipes = hardMultiRecipes;
        recipes.casingsPerCraft = gtceuCasingsPerCraft;
    }

    /** Stable multiplayer identity for every effective value in this profile. */
    public String fingerprint() {
        String canonical = gtceuCasingsPerCraft + "|" + Double.toHexString(steamOutputMultiplier) + "|"
                + singleblockSteamCacheMultiplier + "|" + preheatCostPercent + "|" + preheatIntervalTicks + "|"
                + processingSteamPercent + "|" + Double.toHexString(oreCrushingMultiplier) + "|"
                + Double.toHexString(boilerRoomSteamOutputMultiplier) + "|"
                + Double.toHexString(assemblerOutputMultiplier) + "|" + voidProducerOutputMultiplier + "|"
                + circuitAssemblerBonusChancePercent + "|" + circuitAssemblerBonusMultiplier + "|"
                + hardBronzeComponentRecipes + "|" + harderSteamGrindingBlockRecipes + "|"
                + hardSteamAssemblyBlockRecipes + "|" + hardSteamCircuitAssemblyBlockRecipes + "|"
                + hardSteamMixingBlockRecipes + "|"
                + disableManualCompression + "|" + harderRods + "|" + harderBrickRecipes + "|"
                + nerfWoodCrafting + "|" + hardWoodRecipes + "|" + hardIronRecipes + "|"
                + hardRedstoneRecipes + "|" + hardToolArmorRecipes + "|" + hardMiscRecipes + "|"
                + hardGlassRecipes + "|" + nerfPaperCrafting + "|" + hardAdvancedIronRecipes + "|"
                + hardDyeRecipes + "|" + harderCharcoalRecipe + "|" + flintAndSteelRequireSteel + "|"
                + removeVanillaBlockRecipes + "|" + removeVanillaTNTRecipe + "|"
                + harderCircuitRecipes + "|" + hardMultiRecipes;
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by the Java runtime", exception);
        }
    }
}
