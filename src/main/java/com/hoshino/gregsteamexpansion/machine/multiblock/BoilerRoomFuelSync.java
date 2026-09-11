package com.hoshino.gregsteamexpansion.machine.multiblock;

import com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.api.recipe.content.ContentModifier;
import com.gregtechceu.gtceu.api.recipe.lookup.RecipeManagerHandler;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.hoshino.gregsteamexpansion.GregSteamExpansion;
import com.hoshino.gregsteamexpansion.registry.GSERecipeTypes;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 锅炉房燃料同步 (boiler-room.md P0#3-A): fills the empty
 * {@code gregsteamexpansion:boiler_room} type at every datapack load with
 * the LIQUID-fuel recipes of the upstream steam-boiler type, burned at the
 * upstream large-boiler rate (duration ÷ 4, the same convention
 * {@code GTRecipeTypes.STEAM_BOILER_RECIPES.onRecipeBuild} applies when
 * feeding {@code LARGE_BOILER_RECIPES}). Solid-fuel (item-input) recipes
 * never enter the type — P1#5 keeps the boiler room co-firing only, with the
 * powder entering through the runtime item-bus scan instead of recipes.
 *
 * <p>Mechanics mirror {@code OreCrushingMigration}: server-side at the end
 * of the datapack reload (after the RecipeManager applied, before sync /
 * viewer indexing) and client-side on the synced recipes. The type's recipe
 * set is rebuilt from scratch every load, so addon recipes added to the
 * upstream steam-boiler type are picked up automatically and removed ones
 * disappear — the whitelist stays data-pack driven.</p>
 */
@Mod.EventBusSubscriber(modid = GregSteamExpansion.MOD_ID)
public final class BoilerRoomFuelSync {

    private BoilerRoomFuelSync() {}

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        RecipeManager manager = event.getServerResources().getRecipeManager();
        event.addListener((PreparableReloadListener) (barrier, resourceManager, preparations, executions,
                backgroundExecutor, gameExecutor) -> CompletableFuture
                .completedFuture(null)
                .thenCompose(barrier::wait)
                .thenAcceptAsync(ignored -> sync(manager), gameExecutor));
    }

    /** Client: run after the synced recipes were applied into the client manager. */
    @SubscribeEvent
    public static void onRecipesUpdated(net.minecraftforge.client.event.RecipesUpdatedEvent event) {
        sync(event.getRecipeManager());
    }

    private static void sync(RecipeManager manager) {
        GTRecipeType target = GSERecipeTypes.BOILER_ROOM_RECIPES;
        if (target == null) {
            throw new IllegalStateException("[Boiler Room] recipe type missing at sync time");
        }
        // Reader only: the boiler-room table is rebuilt directly from the
        // public per-type view, so no RecipeManager internals are exposed.
        List<GTRecipe> candidates = new ArrayList<>();
        for (GTRecipe recipe : manager.getAllRecipesFor(GTRecipeTypes.STEAM_BOILER_RECIPES)) {
            if (isLiquidFuel(recipe)) {
                candidates.add(recipe);
            }
        }
        Map<ResourceLocation, Recipe<?>> targetMap = new HashMap<>();
        List<ResourceLocation> copiedIds = new ArrayList<>();
        for (GTRecipe candidate : candidates) {
            GTRecipe copy = copyFuelRecipe(candidate, target);
            if (copy != null) {
                targetMap.put(copy.getId(), copy);
                copiedIds.add(copy.getId());
            }
        }
        restage(target, targetMap);
        GregSteamExpansion.LOGGER.info("[Boiler Room] Synced {} liquid fuel recipes into the boiler_room type.",
                copiedIds.size());
    }

    /** Liquid fuel: at least one fluid input, NO item input (P1#5: no solid path). */
    private static boolean isLiquidFuel(GTRecipe recipe) {
        List<Content> fluidInputs = recipe.inputs.get(FluidRecipeCapability.CAP);
        if (fluidInputs == null || fluidInputs.isEmpty()) {
            return false;
        }
        List<Content> itemInputs = recipe.inputs.get(ItemRecipeCapability.CAP);
        return itemInputs == null || itemInputs.isEmpty();
    }

    /**
     * Same-structure copy with the upstream large-boiler burn divisor
     * (duration ÷ 4, skipped when it rounds to zero, exactly like
     * {@code STEAM_BOILER_RECIPES.onRecipeBuild}); inputs/outputs (including
     * the viewer-facing steam output) are carried over untouched.
     */
    @Nullable
    private static GTRecipe copyFuelRecipe(GTRecipe original, GTRecipeType targetType) {
        int duration = original.duration / 4;
        if (duration <= 0) {
            return null;
        }
        ResourceLocation newId = GregSteamExpansion.id(
                "boiler_room/" + original.getId().getNamespace() + "/" + original.getId().getPath());
        GTRecipe detached = original.copy(ContentModifier.IDENTITY, false);
        GTRecipe copy = new GTRecipe(targetType, newId,
                detached.inputs, detached.outputs,
                detached.tickInputs, detached.tickOutputs,
                detached.inputChanceLogics, detached.outputChanceLogics,
                detached.tickInputChanceLogics, detached.tickOutputChanceLogics,
                detached.conditions, detached.ingredientActions, detached.data,
                duration, targetType.getCategory(), detached.groupColor);
        copy.ocLevel = detached.ocLevel;
        copy.parallels = detached.parallels;
        return copy;
    }

    private static void restage(GTRecipeType type, Map<ResourceLocation, Recipe<?>> postSyncMap) {
        type.beginStagingRecipes();
        RecipeManagerHandler.addRecipesToLookup(postSyncMap, type);
        type.getAdditionHandler().completeStaging();
    }
}
