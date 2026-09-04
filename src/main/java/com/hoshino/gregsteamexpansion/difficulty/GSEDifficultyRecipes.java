package com.hoshino.gregsteamexpansion.difficulty;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import net.minecraft.data.recipes.FinishedRecipe;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeSerializer;

import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

/**
 * Datagen helpers for difficulty-tiered recipes (difficulty.md 配方与数据重载
 * 机制). Recipes are authored once against the Normal baseline; wrapping the
 * finished recipe emits it with a gregsteamexpansion:difficulty condition so
 * exactly one tier's variant loads per save. Each tier's variant must occupy
 * its own recipe resource ID (for example {@code ..._easy} / {@code ..._normal}
 * / {@code ..._expert}) because conditions select files, not rewrite them.
 */
public final class GSEDifficultyRecipes {
    private GSEDifficultyRecipes() {}

    public static Consumer<FinishedRecipe> atDifficulty(Consumer<FinishedRecipe> provider,
                                                        Difficulty difficulty) {
        return recipe -> provider.accept(new ConditionalRecipe(recipe, difficulty));
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
}
