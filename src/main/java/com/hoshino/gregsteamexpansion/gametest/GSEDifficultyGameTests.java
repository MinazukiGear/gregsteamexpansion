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
        assertExpectedRestartConfig(helper, difficulty);
        helper.succeed();
    }

    private static void assertExpectedRestartConfig(GameTestHelper helper, Difficulty difficulty) {
        String expectedDifficulty = System.getenv("GSE_EXPECTED_DIFFICULTY");
        if (expectedDifficulty == null || expectedDifficulty.isBlank()) {
            return;
        }

        Difficulty expected = Difficulty.byName(expectedDifficulty);
        helper.assertTrue(expected != null, "Invalid GSE_EXPECTED_DIFFICULTY: " + expectedDifficulty);
        helper.assertTrue(difficulty == expected,
                "Restart loaded " + difficulty + " instead of expected difficulty " + expected);
        assertExpectedBoolean(helper, "ore plant", GSEDifficultyConfig.orePlantEnabled(),
                System.getenv("GSE_EXPECTED_ORE_PLANT_ENABLED"));
        assertExpectedBoolean(helper, "fluid drill", GSEDifficultyConfig.fluidDrillEnabled(),
                System.getenv("GSE_EXPECTED_FLUID_DRILL_ENABLED"));
        assertExpectedWeights(helper, "ore plant", GSEDifficultyConfig.orePlantWeightEntries(),
                System.getenv("GSE_EXPECTED_ORE_PLANT_WEIGHTS"));
        assertExpectedWeights(helper, "fluid drill", GSEDifficultyConfig.fluidDrillWeightEntries(),
                System.getenv("GSE_EXPECTED_FLUID_DRILL_WEIGHTS"));
    }

    private static void assertExpectedBoolean(GameTestHelper helper, String name,
                                              boolean actual, String configured) {
        helper.assertTrue(configured != null && (configured.equals("true") || configured.equals("false")),
                "Missing or invalid restart expectation for " + name + ": " + configured);
        boolean expected = Boolean.parseBoolean(configured);
        helper.assertTrue(actual == expected,
                name + " enabled state was " + actual + " after restart, expected " + expected);
    }

    private static void assertExpectedWeights(GameTestHelper helper, String name,
                                              java.util.List<? extends String> actual,
                                              String configured) {
        helper.assertTrue(configured != null, "Missing restart weight expectation for " + name);
        java.util.List<String> expected = configured.isEmpty()
                ? java.util.List.of()
                : java.util.List.of(configured.split(";", -1));
        helper.assertTrue(actual.equals(expected),
                name + " weights were " + actual + " after restart, expected " + expected);
    }

    private static void assertValue(GameTestHelper helper, String field,
                                    boolean actual, boolean expected) {
        helper.assertTrue(actual == expected,
                field + " was " + actual + " but startup difficulty requires " + expected);
    }
}
