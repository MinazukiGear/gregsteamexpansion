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
 * same post-migration set as the server execution path.
 */
@Mixin(RecipeManager.class)
public interface RecipeManagerAccessor {

    @Accessor("recipes")
    Map<RecipeType<?>, Map<ResourceLocation, Recipe<?>>> gse$getRecipes();
}
