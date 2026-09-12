package com.hoshino.gregsteamexpansion.gametest;

import com.gregtechceu.gtceu.config.ConfigHolder;
import com.hoshino.gregsteamexpansion.GregSteamExpansion;
import com.hoshino.gregsteamexpansion.difficulty.Difficulty;
import com.hoshino.gregsteamexpansion.difficulty.GSEDifficultyConfig;
import com.hoshino.gregsteamexpansion.difficulty.GSEDifficultyState;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Locks the startup difficulty mapping into GTCEu's runtime recipe config. */
@GameTestHolder(GregSteamExpansion.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GSEDifficultyGameTests {
    private GSEDifficultyGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void startupDifficultyControlsGtceuRecipeProfile(GameTestHelper helper) {
        Difficulty difficulty = GSEDifficultyState.resolved();
        ConfigHolder.RecipeConfigs recipes = ConfigHolder.INSTANCE.recipes;
        boolean normalOrExpert = difficulty != Difficulty.EASY;
        boolean expert = difficulty == Difficulty.EXPERT;

        helper.assertTrue(GSEDifficultyState.isResolved(), "Startup difficulty was not initialized");
        helper.assertTrue(difficulty == GSEDifficultyConfig.capturedDifficulty(),
                "Captured config and process difficulty differ");
        assertValue(helper, "disableManualCompression", recipes.disableManualCompression, normalOrExpert);
        assertValue(helper, "harderRods", recipes.harderRods, normalOrExpert);
        assertValue(helper, "harderBrickRecipes", recipes.harderBrickRecipes, expert);
        assertValue(helper, "nerfWoodCrafting", recipes.nerfWoodCrafting, expert);
        assertValue(helper, "hardWoodRecipes", recipes.hardWoodRecipes, expert);
        assertValue(helper, "hardIronRecipes", recipes.hardIronRecipes, normalOrExpert);
        assertValue(helper, "hardRedstoneRecipes", recipes.hardRedstoneRecipes, expert);
        assertValue(helper, "hardToolArmorRecipes", recipes.hardToolArmorRecipes, expert);
        assertValue(helper, "hardMiscRecipes", recipes.hardMiscRecipes, expert);
        assertValue(helper, "hardGlassRecipes", recipes.hardGlassRecipes, normalOrExpert);
        assertValue(helper, "nerfPaperCrafting", recipes.nerfPaperCrafting, normalOrExpert);
        assertValue(helper, "hardAdvancedIronRecipes", recipes.hardAdvancedIronRecipes, normalOrExpert);
        assertValue(helper, "hardDyeRecipes", recipes.hardDyeRecipes, expert);
        assertValue(helper, "harderCharcoalRecipe", recipes.harderCharcoalRecipe, normalOrExpert);
        assertValue(helper, "flintAndSteelRequireSteel", recipes.flintAndSteelRequireSteel, normalOrExpert);
        assertValue(helper, "removeVanillaBlockRecipes", recipes.removeVanillaBlockRecipes, expert);
        assertValue(helper, "removeVanillaTNTRecipe", recipes.removeVanillaTNTRecipe, normalOrExpert);
        assertValue(helper, "harderCircuitRecipes", recipes.harderCircuitRecipes, expert);
        assertValue(helper, "hardMultiRecipes", recipes.hardMultiRecipes, expert);
        helper.assertTrue(recipes.casingsPerCraft == difficulty.getCasingsPerCraft(),
                "casingsPerCraft does not match startup difficulty");
        helper.succeed();
    }

    private static void assertValue(GameTestHelper helper, String field,
                                    boolean actual, boolean expected) {
        helper.assertTrue(actual == expected,
                field + " was " + actual + " but startup difficulty requires " + expected);
    }
}
