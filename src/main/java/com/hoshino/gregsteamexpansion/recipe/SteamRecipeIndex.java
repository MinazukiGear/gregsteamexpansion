package com.hoshino.gregsteamexpansion.recipe;

import com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.recipe.content.Content;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Builds the immutable {@link SteamRecipeCache.Entry} for one recipe type. */
final class SteamRecipeIndex {

    private SteamRecipeIndex() {}

    static SteamRecipeCache.Entry build(GTRecipeType type) {
        // getRecipesInCategory returns an unmodifiable view of the type's
        // insertion-ordered set, so this list keeps registration order — the
        // order tryStartBatch relies on for deterministic recipe selection.
        List<GTRecipe> recipes = List.copyOf(type.getRecipesInCategory(type.getCategory()));

        Map<ResourceLocation, GTRecipe> byId = new HashMap<>(recipes.size());
        Map<Object, List<GTRecipe>> byItem = new LinkedHashMap<>();
        Map<Object, List<GTRecipe>> byFluid = new LinkedHashMap<>();

        for (GTRecipe recipe : recipes) {
            byId.put(recipe.getId(), recipe);
            indexContents(recipe, ItemRecipeCapability.CAP, byItem);
            indexContents(recipe, FluidRecipeCapability.CAP, byFluid);
        }

        Map<RecipeCapability<?>, Map<Object, List<GTRecipe>>> byContent = new LinkedHashMap<>();
        byContent.put(ItemRecipeCapability.CAP, freeze(byItem));
        byContent.put(FluidRecipeCapability.CAP, freeze(byFluid));
        return new SteamRecipeCache.Entry(recipes, Map.copyOf(byId), Map.copyOf(byContent));
    }

    /**
     * Buckets a recipe under every item/fluid its inputs accept.
     *
     * <p>Bucketing by <em>every</em> accepted value, rather than by one
     * representative, is deliberate: an ore-dictionary ingredient accepts a dozen
     * items, and a bus holding any one of them must find the recipe. It also
     * means lookup is a union over the bus contents, which may therefore return
     * recipes that do not actually match — the existing
     * {@code RecipeHelper.matchRecipe} gate in {@code tryStartRecipe} is what
     * decides, exactly as it did for the full-table scan. The index is a
     * candidate filter, never a decision.</p>
     */
    private static void indexContents(GTRecipe recipe, RecipeCapability<?> capability,
                                      Map<Object, List<GTRecipe>> index) {
        List<Content> contents = recipe.inputs.get(capability);
        if (contents == null || contents.isEmpty()) {
            return;
        }
        // A recipe can accept several alternatives of the same key; dedupe so it
        // never appears twice in one bucket.
        java.util.Set<Object> keys = new java.util.LinkedHashSet<>();
        for (Content content : contents) {
            if (capability == ItemRecipeCapability.CAP) {
                var ingredient = ItemRecipeCapability.CAP.of(content.content);
                if (ingredient == null) {
                    continue;
                }
                for (ItemStack stack : ingredient.getItems()) {
                    if (!stack.isEmpty()) {
                        keys.add(stack.getItem());
                    }
                }
            } else if (capability == FluidRecipeCapability.CAP) {
                var ingredient = FluidRecipeCapability.CAP.of(content.content);
                if (ingredient == null) {
                    continue;
                }
                for (var stack : ingredient.getStacks()) {
                    if (!stack.isEmpty()) {
                        Fluid fluid = stack.getFluid();
                        if (fluid != null) {
                            keys.add(fluid);
                        }
                    }
                }
            }
        }
        for (Object key : keys) {
            index.computeIfAbsent(key, ignored -> new ArrayList<>()).add(recipe);
        }
    }

    /**
     * Flips a value → list map into a fully immutable map. The bucket lists stay
     * mutable internally and are wrapped in {@code List.copyOf} — they are
     * append-only during the build and handed out read-only afterwards.
     */
    private static <K> Map<Object, List<GTRecipe>> freeze(Map<K, List<GTRecipe>> source) {
        Map<Object, List<GTRecipe>> frozen = new HashMap<>(source.size());
        source.forEach((key, value) -> frozen.put(key, List.copyOf(value)));
        return Map.copyOf(frozen);
    }
}
