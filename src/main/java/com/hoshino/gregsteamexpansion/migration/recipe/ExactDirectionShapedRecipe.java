package com.hoshino.gregsteamexpansion.migration.recipe;

import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.core.registries.BuiltInRegistries;

import com.google.gson.JsonObject;

/**
 * Vanilla shaped recipe without the horizontal mirror attempt
 * (steam-crushers.md 大型蒸汽粉碎机控制器配方): the brass rotor must sit on the
 * middle row's left slot and the steel buzz saw blade on its right; swapping
 * the two must not craft. Everything else (JSON format, network format, book
 * category, remainder items) behaves exactly like the vanilla serializer.
 */
public class ExactDirectionShapedRecipe extends ShapedRecipe {

    public ExactDirectionShapedRecipe(ShapedRecipe original) {
        super(original.getId(), original.getGroup(), original.category(), original.getRecipeWidth(),
                original.getRecipeHeight(), original.getIngredients(), original.getResultItem(
                        RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY)),
                true);
    }

    /** Only the as-written placement; the vanilla mirror attempt is removed. */
    @Override
    public boolean matches(CraftingContainer inv, net.minecraft.world.level.Level level) {
        for (int i = 0; i <= inv.getWidth() - this.getRecipeWidth(); i++) {
            for (int j = 0; j <= inv.getHeight() - this.getRecipeHeight(); j++) {
                if (matchesAt(inv, i, j)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean matchesAt(CraftingContainer inv, int offsetX, int offsetY) {
        for (int i = 0; i < inv.getWidth(); i++) {
            for (int j = 0; j < inv.getHeight(); j++) {
                int x = i - offsetX;
                int y = j - offsetY;
                Ingredient ingredient = Ingredient.EMPTY;
                if (x >= 0 && y >= 0 && x < this.getRecipeWidth() && y < this.getRecipeHeight()) {
                    ingredient = this.getIngredients().get(y * this.getRecipeWidth() + x);
                }
                if (!ingredient.test(inv.getItem(i + j * inv.getWidth()))) {
                    return false;
                }
            }
        }
        return true;
    }

    public static class Serializer extends ShapedRecipe.Serializer {

        @Override
        public ShapedRecipe fromJson(ResourceLocation id, JsonObject json) {
            return new ExactDirectionShapedRecipe(super.fromJson(id, json));
        }

        @Override
        public ShapedRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buffer) {
            return new ExactDirectionShapedRecipe(super.fromNetwork(id, buffer));
        }
    }
}
