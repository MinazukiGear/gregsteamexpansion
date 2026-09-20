package com.hoshino.gregsteamexpansion.difficulty;

import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.hoshino.gregsteamexpansion.GregSteamExpansion;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;

import net.minecraftforge.common.crafting.conditions.ICondition;
import net.minecraftforge.common.crafting.conditions.IConditionSerializer;

import org.jetbrains.annotations.Nullable;

/** Selects a generated recipe variant from the startup-captured profile. */
public record GSERecipeConfigCondition(Key key, int expectedValue) implements ICondition {
    public static final ResourceLocation ID = GregSteamExpansion.id("difficulty_recipe_config");

    public static GSERecipeConfigCondition casingsPerCraft(int count) {
        return new GSERecipeConfigCondition(Key.GTCEU_CASINGS_PER_CRAFT, count);
    }

    public static GSERecipeConfigCondition recipeOption(Key key, boolean enabled) {
        if (!key.booleanOption) {
            throw new IllegalArgumentException(key.serialName + " is not a boolean recipe option");
        }
        return new GSERecipeConfigCondition(key, enabled ? 1 : 0);
    }

    @Override
    public ResourceLocation getID() {
        return ID;
    }

    @Override
    public boolean test(IContext context) {
        GSEDifficultyProfile profile = GSEDifficultyState.currentProfile(false);
        int actual = switch (key) {
            case GTCEU_CASINGS_PER_CRAFT -> GSEDifficultyState.recipeCasingsPerCraft();
            case HARD_BRONZE_COMPONENT_RECIPES -> profile.hardBronzeComponentRecipes() ? 1 : 0;
            case HARDER_STEAM_GRINDING_BLOCK_RECIPES -> profile.harderSteamGrindingBlockRecipes() ? 1 : 0;
            case HARD_STEAM_ASSEMBLY_BLOCK_RECIPES -> profile.hardSteamAssemblyBlockRecipes() ? 1 : 0;
            case HARD_STEAM_CIRCUIT_ASSEMBLY_BLOCK_RECIPES ->
                    profile.hardSteamCircuitAssemblyBlockRecipes() ? 1 : 0;
            case HARD_STEAM_MIXING_BLOCK_RECIPES -> profile.hardSteamMixingBlockRecipes() ? 1 : 0;
        };
        return actual == expectedValue;
    }

    public enum Key {
        GTCEU_CASINGS_PER_CRAFT("gtceuCasingsPerCraft", false),
        HARD_BRONZE_COMPONENT_RECIPES("hardBronzeComponentRecipes", true),
        HARDER_STEAM_GRINDING_BLOCK_RECIPES("harderSteamGrindingBlockRecipes", true),
        HARD_STEAM_ASSEMBLY_BLOCK_RECIPES("hardSteamAssemblyBlockRecipes", true),
        HARD_STEAM_CIRCUIT_ASSEMBLY_BLOCK_RECIPES("hardSteamCircuitAssemblyBlockRecipes", true),
        HARD_STEAM_MIXING_BLOCK_RECIPES("hardSteamMixingBlockRecipes", true);

        private final String serialName;
        private final boolean booleanOption;

        Key(String serialName, boolean booleanOption) {
            this.serialName = serialName;
            this.booleanOption = booleanOption;
        }

        public String serialName() {
            return serialName;
        }

        @Nullable
        public static Key byName(String name) {
            for (Key key : values()) {
                if (key.serialName.equals(name)) {
                    return key;
                }
            }
            return null;
        }
    }

    public static final class Serializer implements IConditionSerializer<GSERecipeConfigCondition> {
        public static final Serializer INSTANCE = new Serializer();

        private Serializer() {}

        @Override
        public ResourceLocation getID() {
            return ID;
        }

        @Override
        public void write(JsonObject json, GSERecipeConfigCondition condition) {
            json.addProperty("key", condition.key.serialName);
            if (condition.key.booleanOption) {
                json.addProperty("value", condition.expectedValue != 0);
            } else {
                json.addProperty("value", condition.expectedValue);
            }
        }

        @Override
        public GSERecipeConfigCondition read(JsonObject json) {
            String name = GsonHelper.getAsString(json, "key");
            Key key = Key.byName(name);
            if (key == null) {
                throw new JsonSyntaxException("Unknown GSE recipe config key '" + name + "'");
            }
            int expected = key.booleanOption
                    ? (GsonHelper.getAsBoolean(json, "value") ? 1 : 0)
                    : GsonHelper.getAsInt(json, "value");
            if (key == Key.GTCEU_CASINGS_PER_CRAFT && (expected < 1 || expected > 3)) {
                throw new JsonSyntaxException("gtceuCasingsPerCraft recipe condition must be in 1..3");
            }
            return new GSERecipeConfigCondition(key, expected);
        }
    }
}
