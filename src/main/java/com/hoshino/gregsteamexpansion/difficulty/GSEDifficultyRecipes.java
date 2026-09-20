package com.hoshino.gregsteamexpansion.difficulty;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import net.minecraft.data.recipes.FinishedRecipe;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeSerializer;

import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;

/**
 * Datagen helpers for startup-configured recipes. Generated variants carry
 * Forge conditions because conditions select complete JSON recipes rather
 * than rewriting their ingredients or result stacks after loading.
 */
public final class GSEDifficultyRecipes {
    private GSEDifficultyRecipes() {}

    public static Consumer<FinishedRecipe> atDifficulty(Consumer<FinishedRecipe> provider,
                                                        Difficulty difficulty) {
        return recipe -> provider.accept(new ConditionalRecipe(recipe, difficulty));
    }

    /** Adds one or more startup-profile predicates to a generated variant. */
    public static Consumer<FinishedRecipe> atRecipeConfig(
            Consumer<FinishedRecipe> provider,
            GSERecipeConfigCondition... conditions) {
        List<GSERecipeConfigCondition> copied = List.of(conditions);
        return recipe -> provider.accept(new ProfileConditionalRecipe(recipe, copied));
    }

    private record ConditionalRecipe(FinishedRecipe inner, Difficulty difficulty)
            implements FinishedRecipe {

        @Override
        public void serializeRecipeData(JsonObject json) {
            inner.serializeRecipeData(json);
            JsonObject condition = new JsonObject();
            condition.addProperty("type", GSEDifficultyCondition.ID.toString());
            condition.addProperty("difficulty", difficulty().getSerializedName());
            JsonArray conditions = new JsonArray();
            conditions.add(condition);
            json.add("conditions", conditions);
        }

        @Override
        public ResourceLocation getId() {
            return inner.getId();
        }

        @Override
        public RecipeSerializer<?> getType() {
            return inner.getType();
        }

        @Override
        public JsonObject serializeAdvancement() {
            return inner.serializeAdvancement();
        }

        @Override
        @Nullable
        public ResourceLocation getAdvancementId() {
            return inner.getAdvancementId();
        }
    }

    private record ProfileConditionalRecipe(FinishedRecipe inner,
                                            List<GSERecipeConfigCondition> conditions)
            implements FinishedRecipe {

        @Override
        public void serializeRecipeData(JsonObject json) {
            inner.serializeRecipeData(json);
            JsonArray serializedConditions = new JsonArray();
            for (GSERecipeConfigCondition condition : conditions) {
                JsonObject serialized = new JsonObject();
                serialized.addProperty("type", GSERecipeConfigCondition.ID.toString());
                GSERecipeConfigCondition.Serializer.INSTANCE.write(serialized, condition);
                serializedConditions.add(serialized);
            }
            json.add("conditions", serializedConditions);
        }

        @Override
        public ResourceLocation getId() {
            return inner.getId();
        }

        @Override
        public RecipeSerializer<?> getType() {
            return inner.getType();
        }

        @Override
        public JsonObject serializeAdvancement() {
            return inner.serializeAdvancement();
        }

        @Override
        @Nullable
        public ResourceLocation getAdvancementId() {
            return inner.getAdvancementId();
        }
    }
}
