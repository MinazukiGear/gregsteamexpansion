package com.hoshino.gregsteamexpansion.machine.multiblock.furnace;

import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.capability.recipe.IRecipeCapabilityHolder;
import com.gregtechceu.gtceu.api.capability.recipe.IRecipeHandler;
import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.machine.trait.RecipeHandlerList;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.hoshino.gregsteamexpansion.machine.multiblock.SteamPartCollector;

import net.minecraft.core.BlockPos;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/** Runtime-only isolated input views and round-robin scheduling for distinct buses. */
public final class FurnaceInputScheduler {

    private final List<InputScope> scopes = new ArrayList<>();

    public void rebuild(SteamPartCollector collector) {
        scopes.clear();
        for (IMultiPart inputPart : collector.inputParts()) {
            InputScope scope = new InputScope(inputPart.self().getPos());
            for (SteamPartCollector.HandlerBinding binding : collector.recipeHandlers()) {
                if (binding.part() == inputPart && binding.handlers().isValid(IO.IN)) {
                    scope.addHandlerList(binding.handlers());
                }
            }
            if (scope.hasCapabilityProxies()) {
                scopes.add(scope);
            }
        }
    }

    public void clear() {
        scopes.clear();
    }

    public SearchResult tryStart(int cursor, RecipeFinder recipeFinder, RecipeStarter starter) {
        if (scopes.isEmpty()) {
            return new SearchResult(false, false, cursor);
        }
        int scopeCount = scopes.size();
        int start = Math.floorMod(cursor, scopeCount);
        boolean foundCandidate = false;
        for (int offset = 0; offset < scopeCount; offset++) {
            int index = (start + offset) % scopeCount;
            InputScope scope = scopes.get(index);
            Iterator<GTRecipe> recipes = recipeFinder.find(scope);
            while (recipes.hasNext()) {
                foundCandidate = true;
                if (starter.tryStart(scope, recipes.next(), scope.position.asLong())) {
                    return new SearchResult(true, true, (index + 1) % scopeCount);
                }
            }
        }
        return new SearchResult(false, foundCandidate, cursor);
    }

    /** Read-only snapshot used by characterization tests and diagnostics. */
    public List<IRecipeCapabilityHolder> scopes() {
        return List.copyOf(scopes);
    }

    @FunctionalInterface
    public interface RecipeFinder {

        Iterator<GTRecipe> find(IRecipeCapabilityHolder holder);
    }

    @FunctionalInterface
    public interface RecipeStarter {

        boolean tryStart(IRecipeCapabilityHolder holder, GTRecipe recipe, long sourcePosition);
    }

    public record SearchResult(boolean started, boolean foundCandidate, int nextCursor) {}

    private static final class InputScope implements IRecipeCapabilityHolder {

        private final BlockPos position;
        private final Map<IO, List<RecipeHandlerList>> capabilitiesProxy = new EnumMap<>(IO.class);
        private final Map<IO, Map<RecipeCapability<?>, List<IRecipeHandler<?>>>> capabilitiesFlat =
                new EnumMap<>(IO.class);

        private InputScope(BlockPos position) {
            this.position = position.immutable();
        }

        @NotNull
        @Override
        public Map<IO, List<RecipeHandlerList>> getCapabilitiesProxy() {
            return capabilitiesProxy;
        }

        @NotNull
        @Override
        public Map<IO, Map<RecipeCapability<?>, List<IRecipeHandler<?>>>> getCapabilitiesFlat() {
            return capabilitiesFlat;
        }
    }
}
