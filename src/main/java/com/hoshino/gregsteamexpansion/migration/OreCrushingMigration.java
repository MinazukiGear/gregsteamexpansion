package com.hoshino.gregsteamexpansion.migration;

import com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.data.chemical.ChemicalHelper;
import com.gregtechceu.gtceu.api.data.chemical.material.Material;
import com.gregtechceu.gtceu.api.data.tag.TagPrefix;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.api.recipe.content.ContentModifier;
import com.gregtechceu.gtceu.api.recipe.lookup.RecipeManagerHandler;
import com.gregtechceu.gtceu.common.data.GTRecipeCategories;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.hoshino.gregsteamexpansion.GregSteamExpansion;
import com.hoshino.gregsteamexpansion.difficulty.Difficulty;
import com.hoshino.gregsteamexpansion.difficulty.GSEDifficultyState;
import com.hoshino.gregsteamexpansion.machine.multiblock.crusher.SteamCrusherMachine;
import com.hoshino.gregsteamexpansion.registry.GSERecipeTypes;
import com.hoshino.gregsteamexpansion.registry.GSEMachines;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
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
 * 矿石粉碎配方迁移及其启用保护 (steam-crushers.md 配方迁移启用保护 /
 * ore-crushing.md 从研磨机迁移).
 *
 * <p>The steam crushers are the first obtainable consumer of
 * {@code gregsteamexpansion:ore_crushing}; they register into an explicit
 * consumer table at registration time. On every datapack load the migration
 * verifies the five protection conditions, identifies macerator ore recipes by
 * full semantics (macerator type + ORE_CRUSHING category + exactly one
 * ore/raw-ore item input + same-material crushed main product + no stray
 * consumables), copies each into the new type with the ore-only 4× and the
 * current work-intensity main-product multipliers baked into the main product,
 * then removes the originals — one indivisible copy-then-remove transaction
 * per load, re-executed from the freshly loaded originals every time.</p>
 *
 * <p>The server hook runs at the end of the datapack reload, after the
 * RecipeManager applied and before recipes sync to clients or viewers index
 * them; the client hook runs after the synced recipes were applied. Both GT
 * recipe databases are re-staged from the post-migration sets so execution,
 * category pages and the viewer stay consistent.</p>
 */
@Mod.EventBusSubscriber(modid = GregSteamExpansion.MOD_ID)
public final class OreCrushingMigration {

    /**
     * 显式消费者登记表 (steam-crushers.md 配方迁移启用保护): machines opt in by
     * registering their definition and acquisition recipe; nothing is scanned
     * by display name, class-name strings or id substrings.
     */
    public record ConsumerEntry(MachineDefinition definition, ResourceLocation acquisitionRecipeId) {}

    private static final List<ConsumerEntry> CONSUMERS = new ArrayList<>();

    public static void registerConsumer(MachineDefinition definition, ResourceLocation acquisitionRecipeId) {
        CONSUMERS.add(new ConsumerEntry(definition, acquisitionRecipeId));
    }

    private OreCrushingMigration() {}

    //////////////////////////////////////
    // ***** Hooks ******//
    //////////////////////////////////////

    /** Server: wait for the RecipeManager stage, then migrate before the sync. */
    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        RecipeManager manager = event.getServerResources().getRecipeManager();
        event.addListener((PreparableReloadListener) (barrier, resourceManager, preparations, executions,
                backgroundExecutor, gameExecutor) -> CompletableFuture
                .completedFuture(null)
                .thenCompose(barrier::wait)
                .thenAcceptAsync(ignored -> migrate(manager, false), gameExecutor));
    }

    /** Client: run after the synced recipes were applied into the client manager. */
    @SubscribeEvent
    public static void onRecipesUpdated(net.minecraftforge.client.event.RecipesUpdatedEvent event) {
        migrate(event.getRecipeManager(), true);
    }

    //////////////////////////////////////
    // ***** Migration ******//
    //////////////////////////////////////

    private static void migrate(RecipeManager manager, boolean client) {
        GTRecipeType targetType = GSERecipeTypes.ORE_CRUSHING_RECIPES;
        if (targetType == null) {
            return;
        }
        Map<RecipeType<?>, Map<ResourceLocation, Recipe<?>>> recipes = recipes(manager);
        if (recipes == null) {
            throw migrationFailure("cannot access the loaded recipe map", client);
        }
        // The RecipeManager maps are Guava ImmutableMaps after apply(): copy
        // everything into mutable maps, run the transaction on the copies, then
        // write the whole set back through the accessor.
        Map<ResourceLocation, Recipe<?>> maceratorMap = recipes.get(GTRecipeTypes.MACERATOR_RECIPES);
        if (maceratorMap == null) {
            // no macerator recipes at all: nothing to migrate, consumers stay empty
            return;
        }

        // 配方迁移启用保护: all five conditions must hold before any removal.
        List<String> violations = verifyConsumers(manager);
        if (!violations.isEmpty()) {
            // 服务端只记录一次汇总错误并逐项列出缺失条件; no target is touched.
            GregSteamExpansion.LOGGER.error(
                    "[Ore Crushing] Recipe migration skipped; consumer verification failed: {}",
                    String.join("; ", violations));
            return;
        }

        List<GTRecipe> candidates = new ArrayList<>();
        for (Recipe<?> recipe : maceratorMap.values()) {
            if (recipe instanceof GTRecipe gtRecipe && isCandidate(gtRecipe) && hasOreInput(gtRecipe)) {
                candidates.add(gtRecipe);
            }
        }
        if (candidates.isEmpty()) {
            // 条件 5 失败: the load cannot produce a single validated ore-crushing
            // recipe, so the originals stay and the viewer category stays hidden.
            // On the integrated-server client this is the NORMAL post-sync state:
            // the server already migrated its set and synced it down, so the
            // synced macerator table legitimately holds no ore recipes.
            boolean maceratorHasOreCategory = maceratorMap.values().stream().anyMatch(
                    recipe -> recipe instanceof GTRecipe gtRecipe && isCandidate(gtRecipe));
            if (maceratorHasOreCategory) {
                GregSteamExpansion.LOGGER.error(
                        "[Ore Crushing] Recipe migration skipped; {} ORE_CRUSHING macerator recipes exist but none passed target identification",
                        maceratorMap.size());
            } else {
                GregSteamExpansion.LOGGER.debug(
                        "[Ore Crushing] No macerator ore recipes in the synced set; migration already applied server-side");
            }
            return;
        }

        // Full semantic validation + copy of every target BEFORE any mutation.
        // ORE_CRUSHING recipes whose input is NOT an ore/raw ore (crushed ore →
        // impure dust and the other retained upstream routes) never reach this
        // loop: they are confirmed non-targets and stay in the macerator
        // (ore-crushing.md 保留的上游路线). A recipe that passes hasOreInput but
        // fails the material relation is the "cannot confirm" error state the
        // spec says must block the load.
        Difficulty difficulty = currentDifficulty(client);
        List<GTRecipe> migrated = new ArrayList<>();
        for (GTRecipe candidate : candidates) {
            GTRecipe copy = copyRecipe(candidate, targetType, difficulty);
            if (copy == null) {
                throw migrationFailure(
                        "macerator ore recipe " + candidate.getId() + " failed semantic validation", client);
            }
            migrated.add(copy);
        }

        // 不可分割的"复制后移除": remove originals, insert copies, re-stage both
        // DBs, then write the mutable copies back into the RecipeManager.
        for (GTRecipe candidate : candidates) {
            Recipe<?> removed = maceratorMap.remove(candidate.getId());
            if (removed == null) {
                throw migrationFailure("target " + candidate.getId() + " vanished mid-migration", client);
            }
        }
        Map<ResourceLocation, Recipe<?>> targetMap = recipes.computeIfAbsent(targetType, type -> new HashMap<>());
        for (GTRecipe copy : migrated) {
            targetMap.put(copy.getId(), copy);
        }
        restage(GTRecipeTypes.MACERATOR_RECIPES, maceratorMap);
        restage(targetType, targetMap);
        if (manager instanceof com.hoshino.gregsteamexpansion.mixins.RecipeManagerAccessor accessor) {
            accessor.gse$setRecipes(recipes);
            accessor.gse$setByName(rebuildByName(recipes));
        }

        // 完成后校验"新类型新增数 = 研磨机移除数".
        if (migrated.size() != candidates.size()) {
            throw migrationFailure("migrated count mismatch", client);
        }
        GregSteamExpansion.LOGGER.info("[Ore Crushing] Migrated {} macerator ore recipes for the steam crushers.",
                migrated.size());
    }

    /**
     * 五项消费者条件 (steam-crushers.md 配方迁移启用保护): the small crusher must
     * be registered with its exact definition, obtainable block and item, must
     * declare the ore-crushing type itself, and its acquisition recipe must
     * exist and output exactly one controller. The large crusher alone never
     * satisfies the check.
     */
    private static List<String> verifyConsumers(RecipeManager manager) {
        List<String> violations = new ArrayList<>();
        boolean satisfied = false;
        for (ConsumerEntry entry : CONSUMERS) {
            MachineDefinition lookedUp = com.gregtechceu.gtceu.api.registry.GTRegistries.MACHINES
                    .get(entry.definition().getId());
            if (lookedUp != entry.definition()) {
                violations.add(entry.definition().getId() + " is not registered as the captured definition");
                continue;
            }
            if (lookedUp.getBlock() == null || lookedUp.getItem() == net.minecraft.world.item.Items.AIR) {
                violations.add(entry.definition().getId() + " has no obtainable controller block and item");
                continue;
            }
            // The captured definition was built from SteamCrusherMachine::new at
            // registration; identity equality above keeps the class guarantee.
            if (!java.util.List.of(entry.definition().getRecipeTypes()).contains(GSERecipeTypes.ORE_CRUSHING_RECIPES)) {
                violations.add(entry.definition().getId() + " does not declare the ore-crushing recipe type");
                continue;
            }
            Recipe<?> acquisition = manager.byKey(entry.acquisitionRecipeId()).orElse(null);
            ItemStack acquisitionOutput = acquisition == null ? ItemStack.EMPTY
                    : acquisition.getResultItem(net.minecraft.core.RegistryAccess.fromRegistryOfRegistries(
                            net.minecraft.core.registries.BuiltInRegistries.REGISTRY));
            if (acquisitionOutput.isEmpty()
                    || !acquisitionOutput.is(GSEMachines.STEAM_CRUSHER.getItem())
                    || acquisitionOutput.getCount() != 1) {
                violations.add("the acquisition recipe " + entry.acquisitionRecipeId()
                        + " does not output exactly one steam crusher");
                continue;
            }
            satisfied = true;
        }
        if (!satisfied) {
            violations.add("no consumer entry passed the verification");
        }
        return violations;
    }

    /**
     * 目标识别前两条 (ore-crushing.md 目标识别): type + category. The remaining
     * conditions — exactly one ore/raw-ore input, same-material crushed main
     * product, no stray consumables — are checked by {@link #hasOreInput(GTRecipe)}
     * and {@link #copyRecipe(GTRecipe, GTRecipeType, Difficulty)}; recipes the
     * category shares with the retained upstream routes (crushed ore → impure
     * dust and friends) are kept in the macerator.
     */
    private static boolean isCandidate(GTRecipe recipe) {
        return recipe.recipeType == GTRecipeTypes.MACERATOR_RECIPES
                && recipe.recipeCategory == GTRecipeCategories.ORE_CRUSHING;
    }

    /**
     * 条件 3: exactly one item input whose prefix is a registered ore or the
     * raw-ore prefix. Confirmed non-targets (crushed-ore processing and other
     * retained routes) return false and are left untouched.
     */
    private static boolean hasOreInput(GTRecipe recipe) {
        List<Content> itemInputs = recipe.inputs.get(ItemRecipeCapability.CAP);
        if (itemInputs == null || itemInputs.size() != 1) {
            return false;
        }
        for (Map.Entry<RecipeCapability<?>, List<Content>> entry : recipe.inputs.entrySet()) {
            if (entry.getKey() != ItemRecipeCapability.CAP && !entry.getValue().isEmpty()) {
                return false;
            }
        }
        ItemStack representative = representativeStack(itemInputs.get(0));
        if (representative == null || representative.isEmpty()) {
            return false;
        }
        TagPrefix prefix = ChemicalHelper.getPrefix(representative.getItem());
        return prefix != null && (TagPrefix.ORES.containsKey(prefix) || prefix == TagPrefix.rawOre);
    }

    /**
     * 复制与修改: one ore/raw-ore item input, same-material crushed main product,
     * no fluid or stray consumables. The main product is multiplied by the ore
     * baseline 4× (ores only) and the current work-intensity multiplier; chance
     * outputs are copied verbatim. Returns null when the material relation
     * cannot be confirmed.
     */
    @Nullable
    private static GTRecipe copyRecipe(GTRecipe original, GTRecipeType targetType, Difficulty difficulty) {
        List<Content> itemInputs = original.inputs.get(ItemRecipeCapability.CAP);
        if (itemInputs == null || itemInputs.size() != 1) {
            return null;
        }
        for (Map.Entry<RecipeCapability<?>, List<Content>> entry : original.inputs.entrySet()) {
            if (entry.getKey() != ItemRecipeCapability.CAP && !entry.getValue().isEmpty()) {
                return null;
            }
        }
        for (Map.Entry<RecipeCapability<?>, List<Content>> entry : original.outputs.entrySet()) {
            if (entry.getKey() != ItemRecipeCapability.CAP) {
                return null;
            }
        }
        List<Content> itemOutputs = original.outputs.get(ItemRecipeCapability.CAP);
        if (itemOutputs == null || itemOutputs.isEmpty()) {
            return null;
        }

        // input material from the ingredient's first representative stack.
        // Deserialized recipe contents hold Ingredients (often SizedIngredient),
        // never raw ItemStacks — always go through CAP.of(...).getItems().
        var inputIngredient = ItemRecipeCapability.CAP.of(itemInputs.get(0).content);
        if (inputIngredient == null) {
            return null;
        }
        ItemStack[] representative = inputIngredient.getItems();
        if (representative.length == 0 || representative[0].isEmpty()) {
            return null;
        }
        TagPrefix inputPrefix = ChemicalHelper.getPrefix(representative[0].getItem());
        Material inputMaterial = inputPrefix != null
                ? resolveMaterial(representative[0])
                : null;
        boolean isOre = inputPrefix != null && TagPrefix.ORES.containsKey(inputPrefix);
        boolean isRawOre = inputPrefix == TagPrefix.rawOre;
        if (inputMaterial == null || inputMaterial.isNull() || (!isOre && !isRawOre)) {
            return null;
        }

        // main product: the guaranteed crushed output of the same material.
        // A guaranteed output carries chance == maxChance (10000/10000 after the
        // Content codec defaults); anything below is a chanced byproduct and is
        // copied verbatim.
        Content mainOutput = null;
        int mainIndex = -1;
        for (int i = 0; i < itemOutputs.size(); i++) {
            Content content = itemOutputs.get(i);
            if (content.chance < content.maxChance) {
                continue;
            }
            ItemStack stack = representativeStack(content);
            if (stack == null) {
                continue;
            }
            TagPrefix prefix = ChemicalHelper.getPrefix(stack.getItem());
            Material material = resolveMaterial(stack);
            if (prefix == TagPrefix.crushed && material == inputMaterial) {
                if (mainOutput != null) {
                    return null; // two guaranteed crushed outputs: ambiguous
                }
                mainOutput = content;
                mainIndex = i;
            }
        }
        if (mainOutput == null) {
            return null;
        }

        // main product count: ores ×4 baseline, then the work-intensity multiplier
        ItemStack mainStack = representativeStack(mainOutput).copy();
        double multiplier = (isOre ? 4.0 : 1.0) * difficulty.getOreCrushingMultiplier();
        mainStack.setCount(Math.max(1, (int) Math.round(mainStack.getCount() * multiplier)));

        ResourceLocation newId = GregSteamExpansion.id(
                "ore_crushing/" + original.getId().getNamespace() + "/" + original.getId().getPath());
        List<Content> newOutputs = new ArrayList<>(itemOutputs);
        newOutputs.set(mainIndex, new Content(mainStack, mainOutput.chance, mainOutput.maxChance,
                mainOutput.tierChanceBoost));

        GTRecipe detached = original.copy(ContentModifier.IDENTITY, false);
        GTRecipe copy = new GTRecipe(targetType, newId,
                detached.inputs, Map.of(ItemRecipeCapability.CAP, newOutputs),
                detached.tickInputs, detached.tickOutputs,
                detached.inputChanceLogics, detached.outputChanceLogics,
                detached.tickInputChanceLogics, detached.tickOutputChanceLogics,
                detached.conditions, detached.ingredientActions, detached.data,
                detached.duration, targetType.getCategory(), detached.groupColor);
        copy.ocLevel = detached.ocLevel;
        copy.parallels = detached.parallels;
        return copy;
    }

    /** First representative stack of a Content's item ingredient, or null. */
    @Nullable
    private static ItemStack representativeStack(Content content) {
        var ingredient = ItemRecipeCapability.CAP.of(content.content);
        if (ingredient == null) {
            return null;
        }
        ItemStack[] items = ingredient.getItems();
        return items.length == 0 ? null : items[0];
    }

    /** Material of a stack via the unifier; null when unresolvable. */
    @Nullable
    private static Material resolveMaterial(ItemStack stack) {
        var materialStack = ChemicalHelper.getMaterialStack(stack.getItem());
        Material material = materialStack.material();
        return material == null || material.isNull() ? null : material;
    }

    private static void restage(GTRecipeType type, Map<ResourceLocation, Recipe<?>> postMigrationMap) {
        type.beginStagingRecipes();
        RecipeManagerHandler.addRecipesToLookup(postMigrationMap, type);
        type.getAdditionHandler().completeStaging();
    }

    @Nullable
    private static Map<RecipeType<?>, Map<ResourceLocation, Recipe<?>>> recipes(RecipeManager manager) {
        if (manager instanceof com.hoshino.gregsteamexpansion.mixins.RecipeManagerAccessor accessor) {
            Map<RecipeType<?>, Map<ResourceLocation, Recipe<?>>> immutable = accessor.gse$getRecipes();
            if (immutable == null) {
                return null;
            }
            Map<RecipeType<?>, Map<ResourceLocation, Recipe<?>>> mutable = new HashMap<>();
            immutable.forEach((type, map) -> mutable.put(type, new HashMap<>(map)));
            return mutable;
        }
        return null;
    }

    /** Rebuilds the by-name index over the post-migration recipe set. */
    private static Map<ResourceLocation, Recipe<?>> rebuildByName(
            Map<RecipeType<?>, Map<ResourceLocation, Recipe<?>>> recipes) {
        Map<ResourceLocation, Recipe<?>> byName = new HashMap<>();
        recipes.values().forEach(map -> map.forEach(byName::put));
        return byName;
    }

    private static Difficulty currentDifficulty(boolean client) {
        if (client) {
            return GSEDifficultyState.isClientTierSynced() ? GSEDifficultyState.getClientDifficulty()
                    : Difficulty.NORMAL;
        }
        return GSEDifficultyState.current(false);
    }

    private static IllegalStateException migrationFailure(String message, boolean client) {
        String side = client ? "client" : "server";
        // 阻止完成配方加载: a half-migrated world must never be accepted.
        return new IllegalStateException("[Ore Crushing] " + side + " recipe migration aborted: " + message);
    }
}
