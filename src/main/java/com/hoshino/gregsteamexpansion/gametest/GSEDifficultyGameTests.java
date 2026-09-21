package com.hoshino.gregsteamexpansion.gametest;

import com.gregtechceu.gtceu.config.ConfigHolder;
import com.hoshino.gregsteamexpansion.GregSteamExpansion;
import com.hoshino.gregsteamexpansion.difficulty.Difficulty;
import com.hoshino.gregsteamexpansion.difficulty.GSEDifficultyAuthority;
import com.hoshino.gregsteamexpansion.difficulty.GSEDifficultyConfig;
import com.hoshino.gregsteamexpansion.difficulty.GSEDifficultyProfile;
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
        boolean enabled = GSEDifficultyState.isEnabled();
        ConfigHolder.RecipeConfigs recipes = ConfigHolder.INSTANCE.recipes;

        helper.assertTrue(GSEDifficultyState.isResolved(), "Startup difficulty was not initialized");
        if (!GSEDifficultyAuthority.isExternallyManaged()) {
            helper.assertTrue(enabled == GSEDifficultyConfig.capturedDifficultyEnabled(),
                    "Captured difficulty switch and process state differ");
        }
        GSEDifficultyProfile easyDefaults = GSEDifficultyProfile.defaults(Difficulty.EASY);
        GSEDifficultyProfile normalDefaults = GSEDifficultyProfile.defaults(Difficulty.NORMAL);
        GSEDifficultyProfile expertDefaults = GSEDifficultyProfile.defaults(Difficulty.EXPERT);
        helper.assertTrue(easyDefaults.circuitAssemblerBonusChancePercent() == 100
                        && easyDefaults.circuitAssemblerBonusMultiplier() == 7
                        && normalDefaults.circuitAssemblerBonusChancePercent() == 50
                        && normalDefaults.circuitAssemblerBonusMultiplier() == 3
                        && expertDefaults.circuitAssemblerBonusChancePercent() == 25
                        && expertDefaults.circuitAssemblerBonusMultiplier() == 1,
                "Circuit-specialization tier defaults are not 100%/+7x, 50%/+3x, 25%/+1x");
        helper.assertTrue(easyDefaults.boilerRoomScaleFailureHours() == 720.0
                        && easyDefaults.boilerRoomScaleLossStage1Percent() == 5
                        && easyDefaults.boilerRoomScaleLossStage2Percent() == 10
                        && easyDefaults.boilerRoomScaleLossStage3Percent() == 20
                        && normalDefaults.boilerRoomScaleFailureHours() == 24.0
                        && expertDefaults.boilerRoomScaleFailureHours() == 8.0
                        && normalDefaults.boilerRoomScaleLossStage1Percent() == 10
                        && normalDefaults.boilerRoomScaleLossStage2Percent() == 25
                        && normalDefaults.boilerRoomScaleLossStage3Percent() == 50
                        && expertDefaults.boilerRoomScaleLossStage1Percent() == 15
                        && expertDefaults.boilerRoomScaleLossStage2Percent() == 40
                        && expertDefaults.boilerRoomScaleLossStage3Percent() == 75,
                "Boiler-room water-scale defaults do not match the approved profile");
        helper.assertTrue(easyDefaults.blastFurnaceNoviceDurationPercent() == 75
                        && easyDefaults.blastFurnaceFamiliarDurationPercent() == 65
                        && easyDefaults.blastFurnaceSkilledDurationPercent() == 55
                        && easyDefaults.blastFurnaceMasteredDurationPercent() == 45
                        && easyDefaults.blastFurnaceFamiliarOperations() == 144
                        && easyDefaults.blastFurnaceSkilledOperations() == 576
                        && easyDefaults.blastFurnaceMasteredOperations() == 1440
                        && normalDefaults.blastFurnaceNoviceDurationPercent() == 80
                        && normalDefaults.blastFurnaceFamiliarDurationPercent() == 70
                        && normalDefaults.blastFurnaceSkilledDurationPercent() == 60
                        && normalDefaults.blastFurnaceMasteredDurationPercent() == 50
                        && normalDefaults.blastFurnaceFamiliarOperations() == 192
                        && normalDefaults.blastFurnaceSkilledOperations() == 768
                        && normalDefaults.blastFurnaceMasteredOperations() == 1920
                        && expertDefaults.blastFurnaceNoviceDurationPercent() == 90
                        && expertDefaults.blastFurnaceFamiliarDurationPercent() == 80
                        && expertDefaults.blastFurnaceSkilledDurationPercent() == 70
                        && expertDefaults.blastFurnaceMasteredDurationPercent() == 60
                        && expertDefaults.blastFurnaceFamiliarOperations() == 288
                        && expertDefaults.blastFurnaceSkilledOperations() == 1152
                        && expertDefaults.blastFurnaceMasteredOperations() == 2880,
                "Blast-furnace proficiency defaults do not match the approved profiles");
        if (!enabled) {
            helper.assertTrue(difficulty == Difficulty.NORMAL,
                    "Disabled difficulty must resolve to the recipe baseline Normal tier");
            helper.assertTrue(GSEDifficultyState.steamOutputMultiplier(false) == 1.0F,
                    "Disabled difficulty applied a steam-output multiplier");
            helper.assertTrue(GSEDifficultyState.singleblockSteamCacheMultiplier(false) == 1,
                    "Disabled difficulty applied a steam-cache multiplier");
            helper.assertTrue(GSEDifficultyState.boilerRoomSteamOutputMultiplier(false) == 1.0F,
                    "Disabled difficulty applied a boiler-room multiplier");
            helper.assertTrue(GSEDifficultyState.oreCrushingMultiplier(false) == 1.0F,
                    "Disabled difficulty applied an ore-crushing multiplier");
            helper.assertTrue(GSEDifficultyState.assemblerOutputMultiplier(false) == 1.0F,
                    "Disabled difficulty applied an assembler-output multiplier");
            helper.assertTrue(GSEDifficultyState.voidProducerOutputMultiplier(false) == 1,
                    "Disabled difficulty applied a void-producer multiplier");
            helper.assertTrue(GSEDifficultyState.circuitAssemblerBonusChancePercent(false) == 50
                            && GSEDifficultyState.circuitAssemblerBonusMultiplier(false) == 3,
                    "Disabled difficulty did not use the Normal circuit-specialization baseline");
            helper.assertTrue(GSEDifficultyState.blastFurnaceDurationPercent(false, 0) == 80
                            && GSEDifficultyState.blastFurnaceDurationPercent(false, 3) == 50
                            && GSEDifficultyState.blastFurnaceRequiredOperations(false, 3) == 1920,
                    "Disabled difficulty did not use the Normal blast-furnace proficiency baseline");
            helper.assertTrue(GSEDifficultyState.preheatCostPercent(false) == 100
                            && GSEDifficultyState.processingSteamPercent(false) == 100,
                    "Disabled difficulty applied a furnace consumption multiplier");
            helper.succeed();
            return;
        }
        if (!GSEDifficultyAuthority.isExternallyManaged()) {
            helper.assertTrue(difficulty == GSEDifficultyConfig.capturedDifficulty(),
                    "Captured config and enabled process difficulty differ");
        }
        GSEDifficultyProfile profile = GSEDifficultyState.resolvedProfile();
        assertValue(helper, "disableManualCompression", recipes.disableManualCompression, profile.disableManualCompression());
        assertValue(helper, "harderRods", recipes.harderRods, profile.harderRods());
        assertValue(helper, "harderBrickRecipes", recipes.harderBrickRecipes, profile.harderBrickRecipes());
        assertValue(helper, "nerfWoodCrafting", recipes.nerfWoodCrafting, profile.nerfWoodCrafting());
        assertValue(helper, "hardWoodRecipes", recipes.hardWoodRecipes, profile.hardWoodRecipes());
        assertValue(helper, "hardIronRecipes", recipes.hardIronRecipes, profile.hardIronRecipes());
        assertValue(helper, "hardRedstoneRecipes", recipes.hardRedstoneRecipes, profile.hardRedstoneRecipes());
        assertValue(helper, "hardToolArmorRecipes", recipes.hardToolArmorRecipes, profile.hardToolArmorRecipes());
        assertValue(helper, "hardMiscRecipes", recipes.hardMiscRecipes, profile.hardMiscRecipes());
        assertValue(helper, "hardGlassRecipes", recipes.hardGlassRecipes, profile.hardGlassRecipes());
        assertValue(helper, "nerfPaperCrafting", recipes.nerfPaperCrafting, profile.nerfPaperCrafting());
        assertValue(helper, "hardAdvancedIronRecipes", recipes.hardAdvancedIronRecipes, profile.hardAdvancedIronRecipes());
        assertValue(helper, "hardDyeRecipes", recipes.hardDyeRecipes, profile.hardDyeRecipes());
        assertValue(helper, "harderCharcoalRecipe", recipes.harderCharcoalRecipe, profile.harderCharcoalRecipe());
        assertValue(helper, "flintAndSteelRequireSteel", recipes.flintAndSteelRequireSteel, profile.flintAndSteelRequireSteel());
        assertValue(helper, "removeVanillaBlockRecipes", recipes.removeVanillaBlockRecipes, profile.removeVanillaBlockRecipes());
        assertValue(helper, "removeVanillaTNTRecipe", recipes.removeVanillaTNTRecipe, profile.removeVanillaTNTRecipe());
        assertValue(helper, "harderCircuitRecipes", recipes.harderCircuitRecipes, profile.harderCircuitRecipes());
        assertValue(helper, "hardMultiRecipes", recipes.hardMultiRecipes, profile.hardMultiRecipes());
        helper.assertTrue(recipes.casingsPerCraft == profile.gtceuCasingsPerCraft(),
                "casingsPerCraft does not match the configured startup profile");
        helper.assertTrue(GSEDifficultyState.recipeCasingsPerCraft() == profile.gtceuCasingsPerCraft(),
                "GSE block recipe output does not follow gtceuCasingsPerCraft");
        helper.assertTrue(GSEDifficultyState.profileFingerprint().equals(
                        GSEDifficultyConfig.configurationFingerprint(profile)),
                "Runtime profile fingerprint does not match the effective startup profile");
        helper.assertTrue(GSEDifficultyState.steamOutputMultiplier(false) == (float) profile.steamOutputMultiplier(),
                "Steam-output multiplier does not match the configured profile");
        helper.assertTrue(GSEDifficultyState.singleblockSteamCacheMultiplier(false)
                        == profile.singleblockSteamCacheMultiplier(),
                "Steam-cache multiplier does not match the configured profile");
        helper.assertTrue(GSEDifficultyState.preheatCostPercent(false) == profile.preheatCostPercent()
                        && GSEDifficultyState.preheatIntervalTicks(false) == profile.preheatIntervalTicks()
                        && GSEDifficultyState.processingSteamPercent(false) == profile.processingSteamPercent(),
                "Furnace settings do not match the configured profile");
        helper.assertTrue(GSEDifficultyState.oreCrushingMultiplier(false) == (float) profile.oreCrushingMultiplier()
                        && GSEDifficultyState.boilerRoomSteamOutputMultiplier(false)
                        == (float) profile.boilerRoomSteamOutputMultiplier(),
                "Recipe or boiler-room multiplier does not match the configured profile");
        helper.assertTrue(GSEDifficultyState.assemblerOutputMultiplier(false)
                        == (float) profile.assemblerOutputMultiplier()
                        && GSEDifficultyState.voidProducerOutputMultiplier(false)
                        == profile.voidProducerOutputMultiplier(),
                "Machine output multiplier does not match the configured profile");
        helper.assertTrue(GSEDifficultyState.circuitAssemblerBonusChancePercent(false)
                        == profile.circuitAssemblerBonusChancePercent()
                        && GSEDifficultyState.circuitAssemblerBonusMultiplier(false)
                        == profile.circuitAssemblerBonusMultiplier(),
                "Circuit-specialization settings do not match the configured profile");
        helper.assertTrue(GSEDifficultyState.blastFurnaceDurationPercent(false, 0)
                        == profile.blastFurnaceNoviceDurationPercent()
                        && GSEDifficultyState.blastFurnaceDurationPercent(false, 1)
                        == profile.blastFurnaceFamiliarDurationPercent()
                        && GSEDifficultyState.blastFurnaceDurationPercent(false, 2)
                        == profile.blastFurnaceSkilledDurationPercent()
                        && GSEDifficultyState.blastFurnaceDurationPercent(false, 3)
                        == profile.blastFurnaceMasteredDurationPercent()
                        && GSEDifficultyState.blastFurnaceRequiredOperations(false, 1)
                        == profile.blastFurnaceFamiliarOperations()
                        && GSEDifficultyState.blastFurnaceRequiredOperations(false, 2)
                        == profile.blastFurnaceSkilledOperations()
                        && GSEDifficultyState.blastFurnaceRequiredOperations(false, 3)
                        == profile.blastFurnaceMasteredOperations(),
                "Blast-furnace proficiency settings do not match the configured profile");
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
        GSEDifficultyProfile profile = GSEDifficultyConfig.capturedProfile(difficulty);
        assertExpectedInt(helper, "profile casingsPerCraft", profile.gtceuCasingsPerCraft(),
                System.getenv("GSE_EXPECTED_PROFILE_CASINGS"));
        assertExpectedDouble(helper, "profile steam output", profile.steamOutputMultiplier(),
                System.getenv("GSE_EXPECTED_PROFILE_STEAM_OUTPUT"));
        assertExpectedDouble(helper, "profile boiler scale failure hours", profile.boilerRoomScaleFailureHours(),
                System.getenv("GSE_EXPECTED_PROFILE_SCALE_HOURS"));
        assertExpectedInt(helper, "profile void output", profile.voidProducerOutputMultiplier(),
                System.getenv("GSE_EXPECTED_PROFILE_VOID_OUTPUT"));
        assertExpectedInt(helper, "profile circuit bonus chance", profile.circuitAssemblerBonusChancePercent(),
                System.getenv("GSE_EXPECTED_PROFILE_CIRCUIT_BONUS_CHANCE"));
        assertExpectedInt(helper, "profile circuit bonus multiplier", profile.circuitAssemblerBonusMultiplier(),
                System.getenv("GSE_EXPECTED_PROFILE_CIRCUIT_BONUS_MULTIPLIER"));
        assertExpectedInt(helper, "profile blast novice duration", profile.blastFurnaceNoviceDurationPercent(),
                System.getenv("GSE_EXPECTED_PROFILE_BLASTFURNACENOVICEDURATIONPERCENT"));
        assertExpectedInt(helper, "profile blast familiar duration", profile.blastFurnaceFamiliarDurationPercent(),
                System.getenv("GSE_EXPECTED_PROFILE_BLASTFURNACEFAMILIARDURATIONPERCENT"));
        assertExpectedInt(helper, "profile blast skilled duration", profile.blastFurnaceSkilledDurationPercent(),
                System.getenv("GSE_EXPECTED_PROFILE_BLASTFURNACESKILLEDDURATIONPERCENT"));
        assertExpectedInt(helper, "profile blast mastered duration", profile.blastFurnaceMasteredDurationPercent(),
                System.getenv("GSE_EXPECTED_PROFILE_BLASTFURNACEMASTEREDDURATIONPERCENT"));
        assertExpectedInt(helper, "profile blast familiar operations", profile.blastFurnaceFamiliarOperations(),
                System.getenv("GSE_EXPECTED_PROFILE_BLASTFURNACEFAMILIAROPERATIONS"));
        assertExpectedInt(helper, "profile blast skilled operations", profile.blastFurnaceSkilledOperations(),
                System.getenv("GSE_EXPECTED_PROFILE_BLASTFURNACESKILLEDOPERATIONS"));
        assertExpectedInt(helper, "profile blast mastered operations", profile.blastFurnaceMasteredOperations(),
                System.getenv("GSE_EXPECTED_PROFILE_BLASTFURNACEMASTEREDOPERATIONS"));
        assertExpectedBoolean(helper, "profile harderRods", profile.harderRods(),
                System.getenv("GSE_EXPECTED_PROFILE_HARDER_RODS"));
        assertExpectedBoolean(helper, "profile hardBronzeComponentRecipes",
                profile.hardBronzeComponentRecipes(),
                System.getenv("GSE_EXPECTED_PROFILE_HARD_BRONZE_COMPONENT"));
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

    private static void assertExpectedInt(GameTestHelper helper, String name, int actual, String configured) {
        helper.assertTrue(configured != null, "Missing restart expectation for " + name);
        int expected = Integer.parseInt(configured);
        helper.assertTrue(actual == expected, name + " was " + actual + ", expected " + expected);
    }

    private static void assertExpectedDouble(GameTestHelper helper, String name, double actual, String configured) {
        helper.assertTrue(configured != null, "Missing restart expectation for " + name);
        double expected = Double.parseDouble(configured);
        helper.assertTrue(Double.compare(actual, expected) == 0,
                name + " was " + actual + ", expected " + expected);
    }

    private static void assertValue(GameTestHelper helper, String field,
                                    boolean actual, boolean expected) {
        helper.assertTrue(actual == expected,
                field + " was " + actual + " but startup difficulty requires " + expected);
    }
}
