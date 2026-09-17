package com.hoshino.gregsteamexpansion.gametest;

import com.hoshino.gregsteamexpansion.GregSteamExpansion;
import com.hoshino.gregsteamexpansion.registry.GSEMachines;

import com.gregtechceu.gtceu.api.data.chemical.ChemicalHelper;
import com.gregtechceu.gtceu.api.data.tag.TagPrefix;
import com.gregtechceu.gtceu.common.data.GTBlocks;
import com.gregtechceu.gtceu.common.data.GTMaterials;
import com.gregtechceu.gtceu.common.data.machines.GTMultiMachines;

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
        };
        for (String[] route : fixedRoutes) {
            assertLoadedRecipeOutput(helper, route[0], GregSteamExpansion.id(route[1]));
        }

        assertOneDifficultyRecipeOutput(helper, "shaped/bronze_component", GregSteamExpansion.id("bronze_component"));
        assertOneDifficultyRecipeOutput(helper, "shaped/industrial_steam_casing",
                GregSteamExpansion.gtceuId("industrial_steam_casing"));
        assertOneDifficultyRecipeOutput(helper, "shaped/steam_grinding_block",
                GregSteamExpansion.id("steam_grinding_block"));
        assertOneDifficultyRecipeOutput(helper, "shaped/steam_assembly_block",
                GregSteamExpansion.id("steam_assembly_block"));
        assertOneDifficultyRecipeOutput(helper, "shaped/steam_circuit_assembly_block",
                GregSteamExpansion.id("steam_circuit_assembly_block"));
        assertOneDifficultyRecipeOutput(helper, "shaped/steam_mixing_block",
                GregSteamExpansion.id("steam_mixing_block"));
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
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
        helper.succeed();
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

    private static void assertExactIngredient(GameTestHelper helper, Ingredient ingredient,
                                              ItemStack expected, int slot) {
        ItemStack[] candidates = ingredient.getItems();
        helper.assertTrue(candidates.length == 1 && candidates[0].is(expected.getItem())
                        && ingredient.test(expected),
                "Large steam blast furnace slot " + slot + " does not require exactly "
                        + ForgeRegistries.ITEMS.getKey(expected.getItem()));
    }

    private static void assertOneDifficultyRecipeOutput(GameTestHelper helper, String recipeBasePath,
                                                        ResourceLocation expectedItemId) {
        int loaded = 0;
        for (String difficulty : List.of("easy", "normal", "expert")) {
            String recipePath = recipeBasePath + "_" + difficulty;
            if (helper.getLevel().getRecipeManager().byKey(GregSteamExpansion.id(recipePath)).isPresent()) {
                loaded++;
                assertLoadedRecipeOutput(helper, recipePath, expectedItemId);
            }
        }
        helper.assertTrue(loaded == 1,
                "Expected exactly one loaded difficulty recipe for " + recipeBasePath + ", found " + loaded);
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
