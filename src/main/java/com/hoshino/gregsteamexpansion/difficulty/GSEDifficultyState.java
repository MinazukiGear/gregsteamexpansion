package com.hoshino.gregsteamexpansion.difficulty;

import com.gregtechceu.gtceu.config.ConfigHolder;
import com.hoshino.gregsteamexpansion.GregSteamExpansion;

/**
 * Process-wide difficulty state. The startup config is the authority for the
 * whole process and is applied before recipe/datapack loading; the synced
 * client value exists only for multiplayer display and machine-side parity.
 */
public final class GSEDifficultyState {
    private static volatile Difficulty startupDifficulty = Difficulty.NORMAL;
    private static volatile boolean initialized;
    private static volatile Difficulty clientDifficulty = Difficulty.NORMAL;
    private static volatile boolean clientTierSynced;

    private GSEDifficultyState() {}

    /** The startup tier, with Normal as the safe pre-config fallback. */
    public static Difficulty resolved() {
        return startupDifficulty;
    }

    /** True once Forge has loaded this mod's startup config. */
    public static boolean isResolved() {
        return initialized;
    }

    /** Stores the tier pushed to this client after a passed login check. */
    public static void setClientDifficulty(Difficulty difficulty) {
        clientDifficulty = difficulty;
        clientTierSynced = true;
    }

    /** True once this session's server pushed a tier to this client. */
    public static boolean isClientTierSynced() {
        return clientTierSynced;
    }

    /** The tier this client was last synced with; only meaningful when synced. */
    public static Difficulty getClientDifficulty() {
        return clientDifficulty;
    }

    public static void clearClientTierSynced() {
        clientTierSynced = false;
    }

    /** Tier for machine-side logic: client machines read the synced tier. */
    public static Difficulty current(boolean remote) {
        return remote ? clientDifficulty : resolved();
    }

    /**
     * Captures the process tier and applies the GTCEu 7.5.3 recipe-difficulty
     * profile before recipes are loaded. Easy disables the profile, Normal
     * reproduces GTCEu defaults, and Expert enables every listed hard option.
     */
    static synchronized void initializeAtStartup(Difficulty difficulty) {
        if (initialized) {
            return;
        }
        ConfigHolder.init();
        ConfigHolder.RecipeConfigs recipes = ConfigHolder.INSTANCE.recipes;
        boolean normalOrExpert = difficulty != Difficulty.EASY;
        boolean expert = difficulty == Difficulty.EXPERT;

        recipes.disableManualCompression = normalOrExpert;
        recipes.harderRods = normalOrExpert;
        recipes.harderBrickRecipes = expert;
        recipes.nerfWoodCrafting = expert;
        recipes.hardWoodRecipes = expert;
        recipes.hardIronRecipes = normalOrExpert;
        recipes.hardRedstoneRecipes = expert;
        recipes.hardToolArmorRecipes = expert;
        recipes.hardMiscRecipes = expert;
        recipes.hardGlassRecipes = normalOrExpert;
        recipes.nerfPaperCrafting = normalOrExpert;
        recipes.hardAdvancedIronRecipes = normalOrExpert;
        recipes.hardDyeRecipes = expert;
        recipes.harderCharcoalRecipe = normalOrExpert;
        recipes.flintAndSteelRequireSteel = normalOrExpert;
        recipes.removeVanillaBlockRecipes = expert;
        recipes.removeVanillaTNTRecipe = normalOrExpert;
        recipes.harderCircuitRecipes = expert;
        recipes.hardMultiRecipes = expert;
        recipes.casingsPerCraft = difficulty.getCasingsPerCraft();

        startupDifficulty = difficulty;
        clientDifficulty = difficulty;
        initialized = true;
        GregSteamExpansion.LOGGER.info(
                "[Difficulty] Applied GTCEu startup recipe profile {} (hard options {}, casingsPerCraft {}).",
                difficulty, expert ? "all" : difficulty == Difficulty.EASY ? "off" : "GTCEu defaults",
                recipes.casingsPerCraft);
    }
}
