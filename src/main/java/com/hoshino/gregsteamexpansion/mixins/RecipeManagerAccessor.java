package com.hoshino.gregsteamexpansion.mixins;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

/**
 * Read-write access to the loaded recipe map for the ore-crushing migration
 * (steam-crushers.md 配方迁移启用保护): the migrated recipes must exist in the
 * RecipeManager itself so the vanilla client sync and viewer indexing see the
 * same post-migration set as the server execution path. Both fields are Guava
 * ImmutableMaps after {@code apply}, so the migration copies them into mutable
 * maps, mutates the copies, and writes the whole map back through the setters.
 */
@Mixin(RecipeManager.class)
public interface RecipeManagerAccessor {

    @Accessor("recipes")
    Map<RecipeType<?>, Map<ResourceLocation, Recipe<?>>> gse$getRecipes();

    @Accessor("recipes")
    void gse$setRecipes(Map<RecipeType<?>, Map<ResourceLocation, Recipe<?>>> recipes);

    @Accessor("byName")
    void gse$setByName(Map<ResourceLocation, Recipe<?>> byName);
}
