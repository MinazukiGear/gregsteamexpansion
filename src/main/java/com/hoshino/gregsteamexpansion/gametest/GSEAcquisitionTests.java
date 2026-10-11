package com.hoshino.gregsteamexpansion.gametest;

import com.hoshino.gregsteamexpansion.GregSteamExpansion;
import com.hoshino.gregsteamexpansion.difficulty.GSEDifficultyState;
import com.hoshino.gregsteamexpansion.registry.GSEBlocks;
import com.hoshino.gregsteamexpansion.registry.GSEMachines;

import com.gregtechceu.gtceu.api.data.chemical.ChemicalHelper;
import com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.data.tag.TagPrefix;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.common.data.GTBlocks;
import com.gregtechceu.gtceu.common.data.GTMaterials;
import com.gregtechceu.gtceu.common.data.GTMachines;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.gregtechceu.gtceu.common.data.machines.GTMultiMachines;
import com.gregtechceu.gtceu.common.machine.multiblock.primitive.PrimitiveBlastFurnaceMachine;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@GameTestHolder(GregSteamExpansion.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GSEAcquisitionTests {
    private GSEAcquisitionTests() {}

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void implementedContentHasLoadedAcquisitionRecipes(GameTestHelper helper) {
        // Alpha survival-loop guard: every implemented controller, hatch and
        // supporting structure item must have a loaded crafting route. Legacy
        // migration-only IDs are deliberately absent from this inventory.
        String[][] fixedRoutes = {
                { "shaped/lp_steam_mixed_fuel_boiler", "lp_steam_mixed_fuel_boiler" },
                { "shaped/hp_steam_mixed_fuel_boiler", "hp_steam_mixed_fuel_boiler" },
                { "shaped/steam_exhaust_hatch", "steam_exhaust_hatch" },
                { "shaped/advanced_steam_exhaust_hatch", "advanced_steam_exhaust_hatch" },
                { "shaped/steam_supply_hatch", "steam_supply_hatch" },
                { "shaped/large_steam_supply_hatch", "large_steam_supply_hatch" },
                { "shaped/steam_fluid_input_hatch", "steam_fluid_input_hatch" },
                { "shaped/steam_fluid_output_hatch", "steam_fluid_output_hatch" },
                { "shaped/steam_air_intake_hatch", "steam_air_intake_hatch" },
                { "shaped/steam_crusher", "steam_crusher" },
                { "shaped/large_steam_crusher", "large_steam_crusher" },
                { "shaped/steam_compressor", "steam_compressor" },
                { "shaped/steam_extractor", "steam_extractor" },
                { "shaped/steam_forge", "steam_forge" },
                { "shaped/large_steam_ore_washer", "large_steam_ore_washer" },
                { "shaped/steam_chemical_bath", "steam_chemical_bath" },
                { "shaped/large_steam_macerator", "large_steam_macerator" },
                { "shaped/large_steam_mixer", "large_steam_mixer" },
                { "shaped/steam_centrifuge", "steam_centrifuge" },
                { "shaped/large_steam_centrifuge", "large_steam_centrifuge" },
                { "shaped/large_steam_thermal_centrifuge", "large_steam_thermal_centrifuge" },
                { "shaped/large_steam_assembler", "large_steam_assembler" },
                { "shaped/large_steam_circuit_assembler", "large_steam_circuit_assembler" },
                { "shaped/large_steam_blast_furnace", "large_steam_blast_furnace" },
                { "shaped/large_steam_ore_plant", "large_steam_ore_plant" },
                { "shaped/large_steam_fluid_drill", "large_steam_fluid_drill" },
                { "shaped/large_heat_storage_steam_furnace", "large_heat_storage_steam_furnace" },
                { "shaped/large_steam_tank", "large_steam_tank" },
                { "shaped/steam_tank_valve", "steam_tank_valve" },
                { "large_coke_oven", "large_coke_oven" },
                { "large_coke_oven_hatch", "large_coke_oven_hatch" },
                { "shaped/boiler_room_bronze", "boiler_room_bronze" },
                { "shaped/boiler_room_steel", "boiler_room_steel" },
                { "shaped/boiler_room_titanium", "boiler_room_titanium" },
                { "shaped/boiler_room_tungstensteel", "boiler_room_tungstensteel" },
                { "shaped/electric_ore_crusher_mv", "mv_electric_ore_crusher" },
                { "shaped/electric_ore_crusher_hv", "hv_electric_ore_crusher" },
                { "shaped/electric_ore_crusher_ev", "ev_electric_ore_crusher" },
                { "shaped/electric_ore_crusher_iv", "iv_electric_ore_crusher" },
                { "shaped/electric_ore_crusher_luv", "luv_electric_ore_crusher" },
                { "shaped/electric_ore_crusher_zpm", "zpm_electric_ore_crusher" },
                { "shaped/electric_ore_crusher_uv", "uv_electric_ore_crusher" },
                { "shaped/crafting_station", "crafting_station" },
                { "shaped/crafting_station_slab", "crafting_station_slab" },
                { "shaped/ultimate_terminal", "ultimate_terminal" },
        };
        for (String[] route : fixedRoutes) {
            assertLoadedRecipeOutput(helper, route[0], GregSteamExpansion.id(route[1]));
        }
        assertUltimateTerminalRecipe(helper);

        assertOneConfiguredRecipeOutput(helper, "shaped/bronze_component",
                GregSteamExpansion.id("bronze_component"), 1);
        int blockOutputCount = GSEDifficultyState.recipeCasingsPerCraft();
        assertOneConfiguredRecipeOutput(helper, "shaped/industrial_steam_casing",
                GregSteamExpansion.gtceuId("industrial_steam_casing"), blockOutputCount);
        assertOneConfiguredRecipeOutput(helper, "shaped/steam_grinding_block",
                GregSteamExpansion.id("steam_grinding_block"), blockOutputCount);
        assertOneConfiguredRecipeOutput(helper, "shaped/steam_assembly_block",
                GregSteamExpansion.id("steam_assembly_block"), blockOutputCount);
        assertOneConfiguredRecipeOutput(helper, "shaped/steam_circuit_assembly_block",
                GregSteamExpansion.id("steam_circuit_assembly_block"), blockOutputCount);
        assertOneConfiguredRecipeOutput(helper, "shaped/steam_mixing_block",
                GregSteamExpansion.id("steam_mixing_block"), blockOutputCount);
        helper.succeed();
    }

    @GameTest(template = "empty_32x32x32", batch = "first_steel_progression", timeoutTicks = 2_100)
    public static void acquisitionIngredientsResolveWithoutInternalDeadlocks(GameTestHelper helper) {
        var recipeManager = helper.getLevel().getRecipeManager();
        var registryAccess = helper.getLevel().registryAccess();
        Map<Item, CraftingRecipe> acquisitionRoutes = new HashMap<>();

        for (var recipe : recipeManager.getRecipes()) {
            if (!(recipe instanceof CraftingRecipe craftingRecipe)
                    || !GregSteamExpansion.MOD_ID.equals(recipe.getId().getNamespace())) {
                continue;
            }
            ItemStack result = craftingRecipe.getResultItem(registryAccess);
            ResourceLocation resultId = ForgeRegistries.ITEMS.getKey(result.getItem());
            boolean isModItem = resultId != null && GregSteamExpansion.MOD_ID.equals(resultId.getNamespace());
            boolean isIndustrialSteamCasing = GregSteamExpansion.gtceuId("industrial_steam_casing").equals(resultId);
            if (!isModItem && !isIndustrialSteamCasing) {
                continue;
            }

            CraftingRecipe previous = acquisitionRoutes.put(result.getItem(), craftingRecipe);
            helper.assertTrue(previous == null,
                    "Multiple loaded crafting routes prevent deterministic dependency validation for " + resultId);
            for (var ingredient : craftingRecipe.getIngredients()) {
                if (!ingredient.isEmpty()) {
                    helper.assertTrue(ingredient.getItems().length > 0,
                            "Acquisition recipe has an empty item or tag ingredient: " + recipe.getId());
                }
            }
        }

        helper.assertTrue(!acquisitionRoutes.isEmpty(), "No acquisition recipes were available for dependency validation");
        Set<Item> resolved = new HashSet<>();
        for (Item output : acquisitionRoutes.keySet()) {
            ResourceLocation outputId = ForgeRegistries.ITEMS.getKey(output);
            helper.assertTrue(isAcquisitionDependencyResolvable(output, acquisitionRoutes, resolved, new HashSet<>()),
                    "Acquisition dependency is cyclic or has no crafting route: " + outputId);
        }
        assertEarlyProgressionRoutes(helper);

        // Execute the upstream bootstrap route, not a synthetic short recipe or
        // a GSE blast furnace which already requires steel to craft. Fixtures
        // supply iron/charcoal and the brick structure; this is not a timed
        // survival playthrough or a measurement of mining/crafting effort.
        var definition = GTMultiMachines.PRIMITIVE_BLAST_FURNACE;
        var furnace = (PrimitiveBlastFurnaceMachine) GSEStructureTestUtils.placeShape(
                helper, definition, definition.getMatchingShapes().get(0));
        helper.assertTrue(furnace != null, "Primitive first-steel furnace was not placed");
        ItemStack steel = ChemicalHelper.get(TagPrefix.ingot, GTMaterials.Steel);
        // This real 1,800-tick recipe must not share its run with fixtures that
        // unload/reload chunks or replace machine blocks. Keep the original
        // bounded timeout and report enough state to distinguish a stalled
        // recipe from a stale runtime instance if it still fails.
        for (int tick : new int[] { 300, 1_000, 1_800, 2_100 }) {
            helper.runAtTickTime(tick, () -> GregSteamExpansion.LOGGER.info(
                    "[Progression] First steel checkpoint: {}", firstSteelState(helper, furnace)));
        }
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(furnace.isFormed(), "Primitive first-steel furnace did not form"))
                .thenExecute(() -> {
                    furnace.importItems.setStackInSlot(0, new ItemStack(Items.IRON_INGOT));
                    furnace.importItems.setStackInSlot(1, new ItemStack(Items.CHARCOAL, 2));
                })
                .thenWaitUntil(() -> helper.assertTrue(furnace.getRecipeLogic().isWorking(),
                        "Primitive first-steel recipe did not start"))
                .thenExecute(() -> {
                    GTRecipe expected = loadedGTRecipe(helper, "primitive_blast_furnace/steel_from_charcoal_gem");
                    GTRecipe running = furnace.getRecipeLogic().getLastRecipe();
                    helper.assertTrue(running != null && running.getId().equals(expected.getId())
                                    && running.duration == expected.duration,
                            "First-steel fixture substituted or shortened the upstream recipe");
                    GregSteamExpansion.LOGGER.info("[Progression] First steel running recipe={}, durationTicks={}.",
                            running.getId(), running.duration);
                    GregSteamExpansion.LOGGER.info("[Progression] First steel started: {}",
                            firstSteelState(helper, furnace));
                })
                .thenWaitUntil(() -> helper.assertTrue(furnace.exportItems.getStackInSlot(0).is(steel.getItem()),
                        "Iron and charcoal did not produce the first steel through the upstream furnace: "
                                + firstSteelState(helper, furnace)))
                .thenExecute(() -> {
                    helper.assertTrue(furnace.exportItems.getStackInSlot(0).getCount() == 1,
                            "Bootstrap steel output was not exactly one ingot");
                    helper.assertTrue(furnace.importItems.getStackInSlot(0).isEmpty()
                                    && furnace.importItems.getStackInSlot(1).isEmpty(),
                            "Bootstrap steel did not consume the supplied iron and two charcoal");
                    GregSteamExpansion.LOGGER.info("[Progression] First steel completed using upstream iron + two charcoal, without steam or electricity.");
                })
                .thenSucceed();
    }

    private static String firstSteelState(GameTestHelper helper, PrimitiveBlastFurnaceMachine furnace) {
        var logic = furnace.getRecipeLogic();
        return "tick=" + helper.getTick() + ", pos=" + furnace.getPos()
                + ", sameInstance=" + (MetaMachine.getMachine(helper.getLevel(), furnace.getPos()) == furnace)
                + ", invalid=" + furnace.isInValid() + ", formed=" + furnace.isFormed()
                + ", status=" + logic.getStatus() + ", progress=" + logic.getProgress()
                + "/" + logic.getDuration() + ", output=" + furnace.exportItems.getStackInSlot(0);
    }

    private static void assertEarlyProgressionRoutes(GameTestHelper helper) {
        CraftingRecipe primitive = craftingRoute(helper, GTMultiMachines.PRIMITIVE_BLAST_FURNACE.asStack());
        for (Ingredient ingredient : primitive.getIngredients()) {
            if (ingredient.isEmpty()) continue;
            helper.assertTrue(java.util.Arrays.stream(ingredient.getItems()).anyMatch(candidate ->
                            !GregSteamExpansion.MOD_ID.equals(ForgeRegistries.ITEMS.getKey(candidate.getItem()).getNamespace())
                                    && ChemicalHelper.getMaterialStack(candidate).material() != GTMaterials.Steel),
                    "Primitive bootstrap controller has a steel/GSE-only ingredient: " + primitive.getId());
        }

        CraftingRecipe crusher = craftingRoute(helper, GSEMachines.STEAM_CRUSHER.asStack());
        ItemStack hpMacerator = GTMachines.STEAM_MACERATOR.right().asStack();
        ItemStack grindingHead = new ItemStack(ForgeRegistries.ITEMS.getValue(
                GregSteamExpansion.gtceuId("diamond_grinding_head")));
        helper.assertTrue(hasIngredient(crusher, hpMacerator) && hasIngredient(crusher, grindingHead),
                "Crusher no longer follows the audited HP macerator/grinding-head upgrade route");
        helper.assertTrue(hasIngredient(craftingRoute(helper, hpMacerator),
                        ChemicalHelper.get(TagPrefix.plate, GTMaterials.Steel)),
                "HP macerator no longer requires the audited first-steel plate");
        helper.assertTrue(hasIngredient(craftingRoute(helper, grindingHead),
                        ChemicalHelper.get(TagPrefix.plateDouble, GTMaterials.Steel)),
                "Diamond grinding head no longer requires the audited double steel plate");

        GTRecipe coke = loadedGTRecipe(helper, "coke_oven/coal_to_coke");
        GTRecipe fuel = loadedGTRecipe(helper, "steam_boiler/gtceu_creosote");
        var cokeFluids = coke.outputs.getOrDefault(FluidRecipeCapability.CAP, List.of());
        var fuelFluids = fuel.inputs.getOrDefault(FluidRecipeCapability.CAP, List.of());
        helper.assertTrue(coke.duration > 0 && fuel.duration > 1
                        && cokeFluids.size() == 1 && fuelFluids.size() == 1,
                "Coke/creosote bootstrap recipes changed shape");
        var creosoteOutput = FluidRecipeCapability.CAP.of(cokeFluids.get(0).content);
        var creosoteInput = FluidRecipeCapability.CAP.of(fuelFluids.get(0).content);
        helper.assertTrue(creosoteOutput.getAmount() > 0 && creosoteInput.getAmount() > 0
                        && !cokeFluids.get(0).isChanced()
                        && creosoteOutput.test(GTMaterials.Creosote.getFluid(1))
                        && creosoteInput.test(GTMaterials.Creosote.getFluid(1)),
                "Coke oven and mixed boiler no longer share the audited creosote fuel");
        helper.assertTrue(fuel.inputs.getOrDefault(ItemRecipeCapability.CAP, List.of()).isEmpty(),
                "Creosote boiler route unexpectedly also requires a solid fuel input");
        // HP mixed boilers halve the loaded fuel duration. Keep the model's
        // ideal supply comparison tied to real loaded amounts and durations.
        int hpFuelTicks = fuel.duration / 2;
        long fourHpOvens = ((long) 4 * creosoteInput.getAmount() * coke.duration
                + (long) hpFuelTicks * creosoteOutput.getAmount() - 1)
                / ((long) hpFuelTicks * creosoteOutput.getAmount());
        GregSteamExpansion.LOGGER.info(
                "[Progression] difficultyEnabled={}, primitiveController={}, crusher={}, coalCokeTicks={}, creosoteOutputMb={}, boilerFuelMb={}, lpFuelTicks={}, hpFuelTicks={}, idealCokeOvensForFourHp={}.",
                GSEDifficultyState.isEnabled(), primitive.getId(), crusher.getId(), coke.duration,
                creosoteOutput.getAmount(), creosoteInput.getAmount(), fuel.duration, hpFuelTicks, fourHpOvens);
        helper.assertTrue(GTRecipeTypes.STEAM_BOILER_RECIPES == fuel.getType(),
                "Audited creosote fuel no longer belongs to steam boilers");
    }

    private static CraftingRecipe craftingRoute(GameTestHelper helper, ItemStack result) {
        return helper.getLevel().getRecipeManager().getRecipes().stream()
                .filter(CraftingRecipe.class::isInstance).map(CraftingRecipe.class::cast)
                .filter(recipe -> recipe.getResultItem(helper.getLevel().registryAccess()).is(result.getItem()))
                .filter(recipe -> recipe.getId().getNamespace().equals("gtceu")
                        || recipe.getId().getNamespace().equals(GregSteamExpansion.MOD_ID))
                .sorted(java.util.Comparator.comparing(recipe -> recipe.getId().toString()))
                .findFirst().orElseThrow(() -> new AssertionError("No crafting route for "
                        + ForgeRegistries.ITEMS.getKey(result.getItem())));
    }

    private static boolean hasIngredient(CraftingRecipe recipe, ItemStack item) {
        return recipe.getIngredients().stream().anyMatch(ingredient -> ingredient.test(item));
    }

    private static GTRecipe loadedGTRecipe(GameTestHelper helper, String path) {
        Recipe<?> recipe = helper.getLevel().getRecipeManager().byKey(GregSteamExpansion.gtceuId(path)).orElse(null);
        helper.assertTrue(recipe instanceof GTRecipe, "Missing loaded upstream progression recipe: " + path);
        return (GTRecipe) recipe;
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void largeSteamBlastFurnaceUsesExactUpgradeRecipe(GameTestHelper helper) {
        var loaded = helper.getLevel().getRecipeManager()
                .byKey(GregSteamExpansion.id("shaped/large_steam_blast_furnace"))
                .orElse(null);
        helper.assertTrue(loaded instanceof ShapedRecipe,
                "Large steam blast furnace recipe is missing or is not shaped");
        ShapedRecipe recipe = (ShapedRecipe) loaded;
        helper.assertTrue(recipe.getWidth() == 3 && recipe.getHeight() == 3,
                "Large steam blast furnace recipe is not an exact 3x3 pattern");

        ItemStack hull = GTBlocks.STEEL_BRICKS_HULL.asStack();
        ItemStack steelPlate = ChemicalHelper.get(TagPrefix.plateDouble, GTMaterials.Steel);
        ItemStack primitiveBlastFurnace = GTMultiMachines.PRIMITIVE_BLAST_FURNACE.asStack();
        List<Ingredient> ingredients = recipe.getIngredients();
        helper.assertTrue(ingredients.size() == 9,
                "Large steam blast furnace recipe does not occupy all nine slots");
        for (int slot = 0; slot < ingredients.size(); slot++) {
            ItemStack expected = switch (slot) {
                case 0, 2, 6, 8 -> hull;
                case 1, 3, 5, 7 -> steelPlate;
                case 4 -> primitiveBlastFurnace;
                default -> throw new AssertionError("Unexpected crafting slot " + slot);
            };
            assertExactIngredient(helper, ingredients.get(slot), expected, slot);
        }

        for (ItemStack hatch : List.of(
                GSEMachines.STEAM_SUPPLY_HATCH.asStack(),
                GSEMachines.LARGE_STEAM_SUPPLY_HATCH.asStack(),
                GSEMachines.STEAM_EXHAUST_HATCH.asStack(),
                GSEMachines.ADVANCED_STEAM_EXHAUST_HATCH.asStack(),
                GSEMachines.STEAM_AIR_INTAKE_HATCH.asStack(),
                GSEMachines.STEAM_FLUID_IMPORT_HATCH.asStack(),
                GSEMachines.STEAM_FLUID_EXPORT_HATCH.asStack())) {
            helper.assertTrue(ingredients.stream().noneMatch(ingredient -> ingredient.test(hatch)),
                    "Large steam blast furnace recipe unexpectedly accepts hatch "
                            + ForgeRegistries.ITEMS.getKey(hatch.getItem()));
        }

        AbstractContainerMenu menu = new AbstractContainerMenu(null, -1) {
            @Override
            public ItemStack quickMoveStack(Player player, int index) {
                return ItemStack.EMPTY;
            }

            @Override
            public boolean stillValid(Player player) {
                return true;
            }
        };
        TransientCraftingContainer grid = new TransientCraftingContainer(menu, 3, 3);
        for (int slot = 0; slot < ingredients.size(); slot++) {
            grid.setItem(slot, switch (slot) {
                case 0, 2, 6, 8 -> hull.copy();
                case 1, 3, 5, 7 -> steelPlate.copy();
                case 4 -> primitiveBlastFurnace.copy();
                default -> ItemStack.EMPTY;
            });
        }
        helper.assertTrue(recipe.matches(grid, helper.getLevel()),
                "Exact HSH/SPS/HSH upgrade grid did not match the loaded recipe");
        ItemStack result = recipe.assemble(grid, helper.getLevel().registryAccess());
        helper.assertTrue(result.is(GSEMachines.LARGE_STEAM_BLAST_FURNACE.asStack().getItem())
                        && result.getCount() == 1,
                "Exact upgrade grid did not assemble one large steam blast furnace controller");

        grid.setItem(4, GSEMachines.STEAM_SUPPLY_HATCH.asStack());
        helper.assertTrue(!recipe.matches(grid, helper.getLevel()),
                "A steam supply hatch replaced the required primitive blast furnace core");
        grid.setItem(4, primitiveBlastFurnace.copy());
        grid.setItem(1, GSEMachines.STEAM_EXHAUST_HATCH.asStack());
        helper.assertTrue(!recipe.matches(grid, helper.getLevel()),
                "A steam exhaust hatch replaced a required double steel plate");

        ItemStack controller = GSEMachines.LARGE_STEAM_BLAST_FURNACE.asStack();
        helper.assertTrue(controller.getHoverName().getContents() instanceof TranslatableContents text
                        && text.getKey().equals("block.gregsteamexpansion.large_steam_blast_furnace"),
                "Large steam blast furnace controller does not use its bilingual display key");
        helper.succeed();
    }

    private static void assertLoadedRecipeOutput(GameTestHelper helper, String recipePath,
                                                  ResourceLocation expectedItemId) {
        ResourceLocation recipeId = GregSteamExpansion.id(recipePath);
        var recipe = helper.getLevel().getRecipeManager().byKey(recipeId).orElse(null);
        helper.assertTrue(recipe != null, "Acquisition recipe was not loaded: " + recipeId);
        Item expectedItem = ForgeRegistries.ITEMS.getValue(expectedItemId);
        helper.assertTrue(expectedItem != null && expectedItem != Items.AIR,
                "Acquisition target item is not registered: " + expectedItemId);
        if (recipe != null && expectedItem != null) {
            ItemStack result = recipe.getResultItem(helper.getLevel().registryAccess());
            helper.assertTrue(result.is(expectedItem),
                    "Acquisition recipe " + recipeId + " produces " + result.getItem()
                            + " instead of " + expectedItemId);
        }
    }

    private static void assertUltimateTerminalRecipe(GameTestHelper helper) {
        ResourceLocation recipeId = GregSteamExpansion.id("shaped/ultimate_terminal");
        var loaded = helper.getLevel().getRecipeManager().byKey(recipeId).orElse(null);
        helper.assertTrue(loaded instanceof ShapedRecipe,
                "Ultimate terminal recipe is missing or is not shaped");
        ShapedRecipe recipe = (ShapedRecipe) loaded;
        helper.assertTrue(recipe.getWidth() == 3 && recipe.getHeight() == 3,
                "Ultimate terminal recipe is not an exact 3x3 pattern");

        ItemStack screw = ChemicalHelper.get(TagPrefix.screw, GTMaterials.Electrum);
        ItemStack plate = ChemicalHelper.get(TagPrefix.plate, GTMaterials.Electrum);
        ItemStack wire = ChemicalHelper.get(TagPrefix.wireGtSingle, GTMaterials.Tin);
        Item advancedItem = ForgeRegistries.ITEMS.getValue(
                ResourceLocation.fromNamespaceAndPath("gtmthings", "advanced_terminal"));
        helper.assertTrue(advancedItem != null && advancedItem != Items.AIR,
                "GTM Things advanced terminal is unavailable in the compatibility test runtime");
        ItemStack advanced = new ItemStack(advancedItem);

        List<Ingredient> ingredients = recipe.getIngredients();
        helper.assertTrue(ingredients.size() == 9,
                "Ultimate terminal recipe does not occupy all nine slots");
        assertExactTerminalIngredient(helper, ingredients.get(0), screw, 0);
        helper.assertTrue(ingredients.get(1).test(new ItemStack(Items.GLASS_PANE)),
                "Ultimate terminal slot 1 does not accept the glass pane tag");
        assertExactTerminalIngredient(helper, ingredients.get(2), screw, 2);
        assertExactTerminalIngredient(helper, ingredients.get(3), plate, 3);
        assertExactTerminalIngredient(helper, ingredients.get(4), advanced, 4);
        assertExactTerminalIngredient(helper, ingredients.get(5), plate, 5);
        assertExactTerminalIngredient(helper, ingredients.get(6), plate, 6);
        assertExactTerminalIngredient(helper, ingredients.get(7), wire, 7);
        assertExactTerminalIngredient(helper, ingredients.get(8), plate, 8);

        ItemStack result = recipe.getResultItem(helper.getLevel().registryAccess());
        helper.assertTrue(result.is(GSEBlocks.ULTIMATE_TERMINAL.get()) && result.getCount() == 1,
                "Ultimate terminal recipe does not produce exactly one ultimate terminal");
    }

    private static void assertExactTerminalIngredient(GameTestHelper helper, Ingredient ingredient,
                                                       ItemStack expected, int slot) {
        ItemStack[] candidates = ingredient.getItems();
        helper.assertTrue(candidates.length == 1 && candidates[0].is(expected.getItem())
                        && ingredient.test(expected),
                "Ultimate terminal slot " + slot + " does not require exactly "
                        + ForgeRegistries.ITEMS.getKey(expected.getItem()));
    }

    private static void assertExactIngredient(GameTestHelper helper, Ingredient ingredient,
                                              ItemStack expected, int slot) {
        ItemStack[] candidates = ingredient.getItems();
        helper.assertTrue(candidates.length == 1 && candidates[0].is(expected.getItem())
                        && ingredient.test(expected),
                "Large steam blast furnace slot " + slot + " does not require exactly "
                        + ForgeRegistries.ITEMS.getKey(expected.getItem()));
    }

    private static void assertOneConfiguredRecipeOutput(GameTestHelper helper, String recipeBasePath,
                                                        ResourceLocation expectedItemId, int expectedCount) {
        List<Recipe<?>> loaded = helper.getLevel().getRecipeManager().getRecipes().stream()
                .filter(recipe -> GregSteamExpansion.MOD_ID.equals(recipe.getId().getNamespace()))
                .filter(recipe -> recipe.getId().getPath().startsWith(recipeBasePath + "_"))
                .toList();
        helper.assertTrue(loaded.size() == 1,
                "Expected exactly one loaded configured recipe for " + recipeBasePath
                        + ", found " + loaded.size());
        Recipe<?> recipe = loaded.get(0);
        ItemStack result = recipe.getResultItem(helper.getLevel().registryAccess());
        ResourceLocation actualItemId = ForgeRegistries.ITEMS.getKey(result.getItem());
        helper.assertTrue(expectedItemId.equals(actualItemId),
                recipe.getId() + " outputs " + actualItemId + " instead of " + expectedItemId);
        helper.assertTrue(result.getCount() == expectedCount,
                recipe.getId() + " outputs " + result.getCount() + " instead of " + expectedCount);
    }

    private static boolean isAcquisitionDependencyResolvable(Item item,
                                                              Map<Item, CraftingRecipe> acquisitionRoutes,
                                                              Set<Item> resolved,
                                                              Set<Item> visiting) {
        if (resolved.contains(item)) {
            return true;
        }
        CraftingRecipe recipe = acquisitionRoutes.get(item);
        if (recipe == null) {
            ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(item);
            return itemId != null && !GregSteamExpansion.MOD_ID.equals(itemId.getNamespace());
        }
        if (!visiting.add(item)) {
            return false;
        }

        for (var ingredient : recipe.getIngredients()) {
            if (ingredient.isEmpty()) {
                continue;
            }
            boolean candidateResolved = false;
            for (ItemStack candidate : ingredient.getItems()) {
                if (isAcquisitionDependencyResolvable(candidate.getItem(), acquisitionRoutes, resolved, visiting)) {
                    candidateResolved = true;
                    break;
                }
            }
            if (!candidateResolved) {
                visiting.remove(item);
                return false;
            }
        }

        visiting.remove(item);
        resolved.add(item);
        return true;
    }

}
