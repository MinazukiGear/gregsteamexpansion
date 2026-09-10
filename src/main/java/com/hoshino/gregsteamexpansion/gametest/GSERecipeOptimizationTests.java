package com.hoshino.gregsteamexpansion.gametest;

import com.hoshino.gregsteamexpansion.GregSteamExpansion;
import com.hoshino.gregsteamexpansion.recipe.BoilerFuelCache;
import com.hoshino.gregsteamexpansion.recipe.RecipeCacheLifecycle;
import com.hoshino.gregsteamexpansion.registry.GSEMachines;
import com.hoshino.gregsteamexpansion.machine.multiblock.processor.AbstractSteamProcessorMachine;

import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.common.data.GTMaterials;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.gregtechceu.gtceu.common.machine.multiblock.part.ItemBusPartMachine;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;

@GameTestHolder(GregSteamExpansion.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GSERecipeOptimizationTests {
    private GSERecipeOptimizationTests() {}

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void fuelCacheTracksManagerAndReload(GameTestHelper helper) {
        var cache = new BoilerFuelCache();
        var type = GTRecipeTypes.STEAM_BOILER_RECIPES;
        var fluid = GTMaterials.Creosote.getFluid();
        var recipe = type.recipeBuilder(GregSteamExpansion.id("cache_test"))
                .inputFluids(GTMaterials.Creosote.getFluid(1)).duration(20).buildRawRecipe();
        var emptyManager = new RecipeManager();
        var manager = new RecipeManager();
        manager.replaceRecipes(List.of(recipe));
        helper.assertTrue(!cache.accepts(emptyManager, type, fluid), "Empty world accepted fuel");
        helper.assertTrue(cache.accepts(manager, type, fluid), "Negative cache leaked across worlds");
        helper.assertTrue(!cache.accepts(emptyManager, type, fluid), "Positive cache leaked across worlds");
        emptyManager.replaceRecipes(List.of(recipe));
        RecipeCacheLifecycle.invalidate();
        helper.assertTrue(cache.accepts(emptyManager, type, fluid), "Reload did not invalidate negative cache");
        emptyManager.replaceRecipes(List.of());
        RecipeCacheLifecycle.invalidate();
        helper.assertTrue(!cache.accepts(emptyManager, type, fluid), "Reload did not invalidate positive cache");
        helper.succeed();
    }

    @GameTest(template = "empty_32x32x32", timeoutTicks = 300)
    public static void idleProcessorWakesOnInput(GameTestHelper helper) {
        var definition = GSEMachines.STEAM_COMPRESSOR;
        var machine = (AbstractSteamProcessorMachine) GSEStructureTestUtils.placeShape(
                helper, definition, definition.getMatchingShapes().get(0));
        helper.assertTrue(machine != null, "Missing compressor controller");
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(machine.isFormed(), "Compressor did not form"))
                .thenIdle(23)
                .thenExecute(() -> {
                    helper.assertTrue(machine.getBatchParallel() == 0, "Empty compressor started a batch");
                    var input = machine.getParts().stream()
                            .filter(part -> part instanceof ItemBusPartMachine bus
                                    && bus.getInventory().getHandlerIO() == IO.IN)
                            .map(part -> (ItemBusPartMachine) part).findFirst().orElseThrow();
                    var remainder = input.getInventory().insertItemInternal(0, new ItemStack(Items.IRON_INGOT, 9), false);
                    helper.assertTrue(remainder.isEmpty(), "Input bus rejected iron");
                })
                .thenIdle(2)
                .thenExecute(() -> helper.assertTrue(machine.getBatchParallel() > 0,
                        "Input change did not wake idle recipe search within two ticks"))
                .thenSucceed();
    }
}
