package com.hoshino.gregsteamexpansion.recipe;

import com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;

import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.material.Fluid;

import java.util.HashMap;
import java.util.Map;

/** Instance-owned server cache: no client predictions or previous worlds can leak into it. */
public final class BoilerFuelCache {
    private final Map<Fluid, Boolean> fuels = new HashMap<>();
    private RecipeManager manager;
    private GTRecipeType type;
    private long revision = -1;

    public boolean accepts(RecipeManager manager, GTRecipeType type, Fluid fluid) {
        long currentRevision = RecipeCacheLifecycle.revision();
        if (this.manager != manager || this.type != type || revision != currentRevision) {
            fuels.clear();
            this.manager = manager;
            this.type = type;
            revision = currentRevision;
        }
        return fuels.computeIfAbsent(fluid, candidate -> {
            for (var recipe : manager.getAllRecipesFor(type)) {
                var inputs = recipe.inputs.get(FluidRecipeCapability.CAP);
                if (inputs == null || inputs.isEmpty()) continue;
                for (var stack : FluidRecipeCapability.CAP.of(inputs.get(0).content).getStacks()) {
                    if (stack.getFluid() == candidate) return true;
                }
            }
            return false;
        });
    }
}
