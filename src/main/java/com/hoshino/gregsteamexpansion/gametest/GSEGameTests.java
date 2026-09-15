package com.hoshino.gregsteamexpansion.gametest;

import com.hoshino.gregsteamexpansion.GregSteamExpansion;
import com.hoshino.gregsteamexpansion.cokeoven.CokeOvenMode;
import com.hoshino.gregsteamexpansion.cokeoven.CokeOvenWorldData;
import com.hoshino.gregsteamexpansion.machine.multiblock.LargeSteamOverclock;
import com.hoshino.gregsteamexpansion.machine.multiblock.SteamBudget;
import com.hoshino.gregsteamexpansion.machine.multiblock.furnace.FurnaceSteamCapability;
import com.hoshino.gregsteamexpansion.machine.multiblock.furnace.FurnaceSteamSourceSpec;
import com.hoshino.gregsteamexpansion.machine.multiblock.part.LargeCokeOvenHatchPartMachine;
import com.hoshino.gregsteamexpansion.machine.multiblock.part.LargeSteamSupplyHatchPartMachine;
import com.hoshino.gregsteamexpansion.machine.steam.MixedFuelBoilerMachine;
import com.hoshino.gregsteamexpansion.registry.GSEBlocks;
import com.hoshino.gregsteamexpansion.registry.GSEMachines;
import com.hoshino.gregsteamexpansion.registry.GSEProcessorPatterns;
import com.hoshino.gregsteamexpansion.registry.GSERecipeTypes;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.data.chemical.ChemicalHelper;
import com.gregtechceu.gtceu.api.data.tag.TagPrefix;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;
import com.gregtechceu.gtceu.api.pattern.MultiblockShapeInfo;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gregtechceu.gtceu.common.data.GTMaterials;
import com.gregtechceu.gtceu.common.data.GTMachines;
import com.gregtechceu.gtceu.common.data.GTBlocks;
import com.gregtechceu.gtceu.common.data.GTRecipes;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.gregtechceu.gtceu.common.data.GTCovers;
import com.gregtechceu.gtceu.common.data.GTItems;
import com.gregtechceu.gtceu.common.data.machines.GTMultiMachines;
import com.gregtechceu.gtceu.data.recipe.CustomTags;
import com.gregtechceu.gtceu.common.cover.ShutterCover;
import com.gregtechceu.gtceu.common.machine.multiblock.part.FluidHatchPartMachine;
import com.lowdragmc.lowdraglib.utils.BlockInfo;
import com.hoshino.gregsteamexpansion.machine.multiblock.part.SteamAirIntakeHatchPartMachine;
import com.hoshino.gregsteamexpansion.machine.multiblock.part.SteamFluidHatchPartMachine;
import com.hoshino.gregsteamexpansion.machine.multiblock.part.SteamSupplyHatchPartMachine;
import com.hoshino.gregsteamexpansion.machine.multiblock.processor.AbstractSteamAssemblerMachine;
import com.hoshino.gregsteamexpansion.registry.GSEPartAbilities;
import com.hoshino.gregsteamexpansion.steamcompat.LegacySteamHatchCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@GameTestHolder(GregSteamExpansion.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GSEGameTests {
    private static final BlockPos LP_POS = new BlockPos(0, 0, 0);
    private static final BlockPos HP_POS = new BlockPos(2, 0, 0);
    private static final int HOT_BOILER_DELAY = 1_240;

    private GSEGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void mixedFuelBoilersRegisterAndCreate(GameTestHelper helper) {
        var lowPressure = GSEMachines.MIXED_FUEL_BOILER.left();
        var highPressure = GSEMachines.MIXED_FUEL_BOILER.right();

        helper.assertTrue(lowPressure.getId().equals(GregSteamExpansion.id("lp_steam_mixed_fuel_boiler")),
                "Low-pressure machine definition has the wrong ID");
        helper.assertTrue(highPressure.getId().equals(GregSteamExpansion.id("hp_steam_mixed_fuel_boiler")),
                "High-pressure machine definition has the wrong ID");
        helper.assertTrue(ForgeRegistries.BLOCKS.getKey(lowPressure.getBlock()).equals(lowPressure.getId()),
                "Low-pressure boiler block is not registered under its machine ID");
        helper.assertTrue(ForgeRegistries.BLOCKS.getKey(highPressure.getBlock()).equals(highPressure.getId()),
                "High-pressure boiler block is not registered under its machine ID");

        helper.setBlock(LP_POS, lowPressure.defaultBlockState());
        helper.setBlock(HP_POS, highPressure.defaultBlockState());

        MetaMachine lowPressureMachine = MetaMachine.getMachine(helper.getLevel(), helper.absolutePos(LP_POS));
        MetaMachine highPressureMachine = MetaMachine.getMachine(helper.getLevel(), helper.absolutePos(HP_POS));
        helper.assertTrue(lowPressureMachine instanceof MixedFuelBoilerMachine boiler && !boiler.isHighPressure(),
                "Low-pressure block did not create a low-pressure mixed-fuel boiler");
        helper.assertTrue(highPressureMachine instanceof MixedFuelBoilerMachine boiler && boiler.isHighPressure(),
                "High-pressure block did not create a high-pressure mixed-fuel boiler");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void mixedFuelBoilerRecipesLoad(GameTestHelper helper) {
        var recipeManager = helper.getLevel().getRecipeManager();
        helper.assertTrue(recipeManager.byKey(GregSteamExpansion.id("shaped/lp_steam_mixed_fuel_boiler")).isPresent(),
                "Low-pressure mixed-fuel boiler recipe was not loaded");
        helper.assertTrue(recipeManager.byKey(GregSteamExpansion.id("shaped/hp_steam_mixed_fuel_boiler")).isPresent(),
                "High-pressure mixed-fuel boiler recipe was not loaded");
        helper.succeed();
    }

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

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void mixedFuelBoilerFiltersInputsAndSwitchesModes(GameTestHelper helper) {
        MixedFuelBoilerMachine boiler = placeLowPressureBoiler(helper);

        helper.assertTrue(!boiler.isCoFiring(), "Mixed-fuel boiler did not default to liquid-fuel mode");
        boiler.setCoFiring(true);
        helper.assertTrue(boiler.isCoFiring(), "Mixed-fuel boiler did not switch to co-firing mode");
        boiler.setCoFiring(false);
        helper.assertTrue(!boiler.isCoFiring(), "Mixed-fuel boiler did not switch back to liquid-fuel mode");

        int rejectedWaterTankFuel = boiler.waterTank.fillInternal(GTMaterials.Creosote.getFluid(250),
                FluidAction.EXECUTE);
        int acceptedWater = boiler.waterTank.fillInternal(GTMaterials.Water.getFluid(1_000), FluidAction.EXECUTE);
        helper.assertTrue(rejectedWaterTankFuel == 0, "Water tank accepted creosote");
        helper.assertTrue(acceptedWater == 1_000, "Water tank rejected water");

        int rejectedFuelTankWater = boiler.fuelTank.fillInternal(GTMaterials.Water.getFluid(1_000),
                FluidAction.EXECUTE);
        int acceptedCreosote = boiler.fuelTank.fillInternal(GTMaterials.Creosote.getFluid(250),
                FluidAction.EXECUTE);
        helper.assertTrue(rejectedFuelTankWater == 0, "Liquid-fuel tank accepted water");
        helper.assertTrue(acceptedCreosote == 250, "Liquid-fuel tank rejected a steam-boiler fuel recipe");

        ItemStack rejectedCoal = boiler.powderHandler.insertItemInternal(0, new ItemStack(Items.COAL), false);
        ItemStack acceptedCoalDust = boiler.powderHandler.insertItemInternal(0,
                ChemicalHelper.get(TagPrefix.dust, GTMaterials.Coal), false);
        helper.assertTrue(rejectedCoal.getCount() == 1, "Powder slot accepted a non-powder coal item");
        helper.assertTrue(acceptedCoalDust.isEmpty(), "Powder slot rejected coal dust");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void mixedFuelBoilerLiquidModeRunsWithoutPowder(GameTestHelper helper) {
        MixedFuelBoilerMachine boiler = placeLowPressureBoiler(helper);
        boiler.waterTank.fillInternal(GTMaterials.Water.getFluid(1_000), FluidAction.EXECUTE);
        boiler.fuelTank.fillInternal(GTMaterials.Creosote.getFluid(250), FluidAction.EXECUTE);

        helper.succeedWhen(() -> {
            helper.assertTrue(!boiler.isCoFiring(), "Boiler left liquid-fuel mode unexpectedly");
            helper.assertTrue(boiler.powderHandler.getStackInSlot(0).isEmpty(),
                    "Liquid-fuel mode unexpectedly acquired powder fuel");
            helper.assertTrue(boiler.getRecipeLogic().isWorking(),
                    "Liquid-fuel mode did not start without powder fuel");
            helper.assertTrue(boiler.getCurrentTemperature() > 0,
                    "Liquid-fuel mode started but did not begin heating");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 120)
    public static void mixedFuelBoilerWaitsForPowderAndRecovers(GameTestHelper helper) {
        MixedFuelBoilerMachine boiler = placeLowPressureBoiler(helper);
        boiler.waterTank.fillInternal(GTMaterials.Water.getFluid(1_000), FluidAction.EXECUTE);
        boiler.fuelTank.fillInternal(GTMaterials.Creosote.getFluid(250), FluidAction.EXECUTE);
        boiler.setCoFiring(true);

        helper.runAfterDelay(10, () -> {
            helper.assertTrue(!boiler.getRecipeLogic().isWorking(),
                    "Co-firing boiler started without powder fuel");
            helper.assertTrue(boiler.getCurrentTemperature() == 0,
                    "Co-firing boiler heated up while powder fuel was missing");

            ItemStack remainder = boiler.powderHandler.insertItemInternal(0,
                    ChemicalHelper.get(TagPrefix.dust, GTMaterials.Coal), false);
            helper.assertTrue(remainder.isEmpty(), "Could not add coal dust while the boiler was waiting");

            helper.succeedWhen(() -> {
                helper.assertTrue(boiler.getRecipeLogic().isWorking(),
                        "Co-firing boiler did not resume after coal dust was added");
                helper.assertTrue(boiler.getCurrentTemperature() > 0,
                        "Co-firing boiler resumed but did not begin heating");
            });
        });
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void mixedFuelBoilerExposesFilteredCapabilitiesOnEverySide(GameTestHelper helper) {
        MixedFuelBoilerMachine boiler = placeLowPressureBoiler(helper);
        helper.assertTrue(boiler.steamTank.fillInternal(GTMaterials.Steam.getFluid(1_000), FluidAction.EXECUTE) == 1_000,
                "Could not prepare steam output for sided capability test");

        BlockEntity blockEntity = helper.getLevel().getBlockEntity(helper.absolutePos(LP_POS));
        helper.assertTrue(blockEntity != null, "Mixed-fuel boiler block entity was missing");
        for (Direction side : Direction.values()) {
            IFluidHandler fluids = blockEntity.getCapability(ForgeCapabilities.FLUID_HANDLER, side).orElse(null);
            helper.assertTrue(fluids != null, "No fluid capability on " + side.getName() + " side");
            helper.assertTrue(fluids.fill(GTMaterials.Water.getFluid(1_000), FluidAction.SIMULATE) == 1_000,
                    "Water was rejected on " + side.getName() + " side");
            helper.assertTrue(fluids.fill(GTMaterials.Creosote.getFluid(250), FluidAction.SIMULATE) == 250,
                    "Creosote was rejected on " + side.getName() + " side");
            helper.assertTrue(fluids.fill(GTMaterials.Steam.getFluid(100), FluidAction.SIMULATE) == 0,
                    "Steam was accepted as an input on " + side.getName() + " side");
            helper.assertTrue(fluids.drain(GTMaterials.Steam.getFluid(100), FluidAction.SIMULATE).getAmount() == 100,
                    "Steam could not be extracted on " + side.getName() + " side");

            IItemHandler items = blockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER, side).orElse(null);
            helper.assertTrue(items != null, "No item capability on " + side.getName() + " side");
            helper.assertTrue(insertIntoAnySlot(items,
                    ChemicalHelper.get(TagPrefix.dust, GTMaterials.Coal)).isEmpty(),
                    "Coal dust was rejected on " + side.getName() + " side");
            helper.assertTrue(insertIntoAnySlot(items, new ItemStack(Items.COAL)).getCount() == 1,
                    "Non-powder coal was accepted on " + side.getName() + " side");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void adjacentMixedFuelBoilersRunWhileStacked(GameTestHelper helper) {
        MixedFuelBoilerMachine first = placeLowPressureBoiler(helper, LP_POS);
        MixedFuelBoilerMachine second = placeLowPressureBoiler(helper, LP_POS.east());
        prepareLiquidMode(first);
        prepareLiquidMode(second);

        BlockEntity firstBlockEntity = helper.getLevel().getBlockEntity(helper.absolutePos(LP_POS));
        BlockEntity secondBlockEntity = helper.getLevel().getBlockEntity(helper.absolutePos(LP_POS.east()));
        helper.assertTrue(firstBlockEntity != null && firstBlockEntity
                        .getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.EAST).isPresent(),
                "First stacked boiler lost its shared-face fluid capability");
        helper.assertTrue(secondBlockEntity != null && secondBlockEntity
                        .getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.WEST).isPresent(),
                "Second stacked boiler lost its shared-face fluid capability");

        helper.succeedWhen(() -> {
            helper.assertTrue(first.getRecipeLogic().isWorking() && first.getCurrentTemperature() > 0,
                    "First stacked boiler did not run");
            helper.assertTrue(second.getRecipeLogic().isWorking() && second.getCurrentTemperature() > 0,
                    "Second stacked boiler did not run");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 1_400)
    public static void mixedFuelBoilerVentsWhenSteamOutputIsFull(GameTestHelper helper) {
        MixedFuelBoilerMachine boiler = placeHighPressureBoiler(helper);
        prepareHotBoiler(boiler);
        int capacity = boiler.steamTank.getTankCapacity(0);
        int filled = boiler.steamTank.fillInternal(GTMaterials.Steam.getFluid(capacity), FluidAction.EXECUTE);
        helper.assertTrue(filled == capacity, "Could not fill the steam tank before the venting test");

        helper.succeedWhen(() -> {
            helper.assertTrue(boiler.getCurrentTemperature() >= 100,
                    "High-pressure boiler had not reached steam-production temperature");
            int storedSteam = boiler.steamTank.getFluidInTank(0).getAmount();
            helper.assertTrue(storedSteam < capacity,
                    "A full steam tank did not vent when the boiler tried to produce steam");
            helper.assertTrue(MetaMachine.getMachine(helper.getLevel(), helper.absolutePos(HP_POS)) == boiler,
                    "Steam venting unexpectedly removed the boiler");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 1_400)
    public static void mixedFuelBoilerExplodesWhenWaterReturnsAfterDryBoiling(GameTestHelper helper) {
        MixedFuelBoilerMachine boiler = placeHighPressureBoiler(helper);
        prepareHotBoiler(boiler);

        helper.runAfterDelay(HOT_BOILER_DELAY, () -> {
            helper.assertTrue(boiler.getCurrentTemperature() >= 100,
                    "High-pressure boiler did not reach dry-boiler test temperature");
            boiler.waterTank.drainInternal(Integer.MAX_VALUE, FluidAction.EXECUTE);
            helper.assertTrue(boiler.waterTank.isEmpty(), "Could not drain the boiler water tank");

            helper.runAfterDelay(20, () -> {
                helper.assertTrue(MetaMachine.getMachine(helper.getLevel(), helper.absolutePos(HP_POS)) == boiler,
                        "Dry boiler exploded before water was restored");
                int restored = boiler.waterTank.fillInternal(GTMaterials.Water.getFluid(1_000), FluidAction.EXECUTE);
                helper.assertTrue(restored == 1_000, "Could not restore water to the dry boiler");

                helper.succeedWhen(() -> helper.assertTrue(
                        MetaMachine.getMachine(helper.getLevel(), helper.absolutePos(HP_POS)) == null,
                        "Dry boiler did not explode after water was restored"));
            });
        });
    }

    private static MixedFuelBoilerMachine placeLowPressureBoiler(GameTestHelper helper) {
        return placeLowPressureBoiler(helper, LP_POS);
    }

    private static MixedFuelBoilerMachine placeLowPressureBoiler(GameTestHelper helper, BlockPos pos) {
        var definition = GSEMachines.MIXED_FUEL_BOILER.left();
        helper.setBlock(pos, definition.defaultBlockState());
        MetaMachine machine = MetaMachine.getMachine(helper.getLevel(), helper.absolutePos(pos));
        helper.assertTrue(machine instanceof MixedFuelBoilerMachine,
                "Low-pressure block did not create a mixed-fuel boiler");
        return (MixedFuelBoilerMachine) machine;
    }

    private static MixedFuelBoilerMachine placeHighPressureBoiler(GameTestHelper helper) {
        var definition = GSEMachines.MIXED_FUEL_BOILER.right();
        helper.setBlock(HP_POS, definition.defaultBlockState());
        MetaMachine machine = MetaMachine.getMachine(helper.getLevel(), helper.absolutePos(HP_POS));
        helper.assertTrue(machine instanceof MixedFuelBoilerMachine,
                "High-pressure block did not create a mixed-fuel boiler");
        return (MixedFuelBoilerMachine) machine;
    }

    private static void prepareLiquidMode(MixedFuelBoilerMachine boiler) {
        boiler.waterTank.fillInternal(GTMaterials.Water.getFluid(1_000), FluidAction.EXECUTE);
        boiler.fuelTank.fillInternal(GTMaterials.Creosote.getFluid(250), FluidAction.EXECUTE);
    }

    private static void prepareHotBoiler(MixedFuelBoilerMachine boiler) {
        boiler.waterTank.fillInternal(GTMaterials.Water.getFluid(16_000), FluidAction.EXECUTE);
        boiler.fuelTank.fillInternal(GTMaterials.Creosote.getFluid(2_000), FluidAction.EXECUTE);
    }

    private static ItemStack insertIntoAnySlot(IItemHandler handler, ItemStack stack) {
        ItemStack remainder = stack.copy();
        for (int slot = 0; slot < handler.getSlots() && !remainder.isEmpty(); slot++) {
            remainder = handler.insertItem(slot, remainder, true);
        }
        return remainder;
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void oreCrushingRecipeTypeRegistered(GameTestHelper helper) {
        GTRecipeType type = GTRegistries.RECIPE_TYPES.get(GregSteamExpansion.id("ore_crushing"));
        helper.assertTrue(type != null, "Ore crushing recipe type is not registered");
        if (type == null) {
            return;
        }
        // ore-crushing.md 实现验收 1: 1 item input, up to 4 item outputs, no
        // fluid slots, EU input only, recorded baseline 2 EU/t over 400 ticks.
        helper.assertTrue(type.getMaxInputs(ItemRecipeCapability.CAP) == 1,
                "Ore crushing recipe type must accept exactly one item input slot");
        helper.assertTrue(type.getMaxOutputs(ItemRecipeCapability.CAP) == 4,
                "Ore crushing recipe type must provide up to four item output slots");
        helper.assertTrue(type.getMaxInputs(FluidRecipeCapability.CAP) == 0,
                "Ore crushing recipe type must not take fluid inputs");
        helper.assertTrue(type.getMaxOutputs(FluidRecipeCapability.CAP) == 0,
                "Ore crushing recipe type must not produce fluid outputs");
        helper.assertTrue(ForgeRegistries.RECIPE_SERIALIZERS.getValue(GregSteamExpansion.id("ore_crushing")) != null,
                "Ore crushing recipe serializer is not registered");
        helper.assertTrue(GSERecipeTypes.ORE_CRUSHING_RECIPES == type,
                "GSERecipeTypes.ORE_CRUSHING_RECIPES points at a different instance");
        helper.succeed();
    }

    /**
     * P2-6 load guard for both runtime recipe rewrites. The ore migration must
     * publish the same recipes through RecipeManager's type and by-name public
     * indexes and through GTCEu staging, while the boiler-room reader must
     * stage every eligible upstream liquid fuel without private field access.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void recipeRewriteHooksPublishConsistentLoadedSets(GameTestHelper helper) {
        var manager = helper.getLevel().getRecipeManager();

        List<GTRecipe> migrated = manager.getAllRecipesFor(GSERecipeTypes.ORE_CRUSHING_RECIPES);
        helper.assertTrue(!migrated.isEmpty(), "Ore-crushing migration published no recipes");
        java.util.Set<ResourceLocation> managerIds = new java.util.HashSet<>();
        for (GTRecipe recipe : migrated) {
            ResourceLocation id = recipe.getId();
            helper.assertTrue(GregSteamExpansion.MOD_ID.equals(id.getNamespace())
                            && id.getPath().startsWith("ore_crushing/"),
                    "Migrated recipe has an unexpected ID: " + id);
            helper.assertTrue(recipe.recipeType == GSERecipeTypes.ORE_CRUSHING_RECIPES,
                    "Migrated recipe retained the wrong GTCEu type: " + id);
            helper.assertTrue(manager.byKey(id).orElse(null) == recipe,
                    "RecipeManager by-name index disagrees with its ore-crushing type index: " + id);
            managerIds.add(id);

            String encodedSource = id.getPath().substring("ore_crushing/".length());
            int separator = encodedSource.indexOf('/');
            helper.assertTrue(separator > 0, "Migrated recipe ID does not encode its source: " + id);
            ResourceLocation sourceId = ResourceLocation.tryBuild(
                    encodedSource.substring(0, separator), encodedSource.substring(separator + 1));
            helper.assertTrue(sourceId != null && manager.byKey(sourceId).isEmpty(),
                    "Original macerator ore recipe remains after migration: " + sourceId);
        }

        java.util.Set<ResourceLocation> stagedOreIds = new java.util.HashSet<>();
        for (GTRecipe recipe : GSERecipeTypes.ORE_CRUSHING_RECIPES.getRecipesInCategory(
                GSERecipeTypes.ORE_CRUSHING_RECIPES.getCategory())) {
            stagedOreIds.add(recipe.getId());
        }
        helper.assertTrue(stagedOreIds.equals(managerIds),
                "GTCEu ore-crushing staging disagrees with RecipeManager: manager="
                        + managerIds.size() + ", staged=" + stagedOreIds.size());

        long eligibleBoilerFuels = manager.getAllRecipesFor(GTRecipeTypes.STEAM_BOILER_RECIPES).stream()
                .filter(recipe -> recipe.inputs.getOrDefault(FluidRecipeCapability.CAP, List.of()).size() > 0)
                .filter(recipe -> recipe.inputs.getOrDefault(ItemRecipeCapability.CAP, List.of()).isEmpty())
                .filter(recipe -> recipe.duration / 4 > 0)
                .count();
        List<GTRecipe> boilerRoomFuels = List.copyOf(
                GSERecipeTypes.BOILER_ROOM_RECIPES.getRecipesInCategory(
                        GSERecipeTypes.BOILER_ROOM_RECIPES.getCategory()));
        helper.assertTrue(eligibleBoilerFuels > 0, "Upstream exposes no eligible liquid boiler fuel");
        helper.assertTrue(boilerRoomFuels.size() == eligibleBoilerFuels,
                "Boiler-room staging count disagrees with upstream liquid fuels: expected="
                        + eligibleBoilerFuels + ", actual=" + boilerRoomFuels.size());
        for (GTRecipe recipe : boilerRoomFuels) {
            helper.assertTrue(GregSteamExpansion.MOD_ID.equals(recipe.getId().getNamespace())
                            && recipe.getId().getPath().startsWith("boiler_room/"),
                    "Boiler-room sync produced an unexpected ID: " + recipe.getId());
        }
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // Steam-era hatches (machines-and-hatches.md 实现验收)
    // ------------------------------------------------------------------

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void steamHatchesRegisterWithCorrectAbilities(GameTestHelper helper) {
        // machines-and-hatches.md 实现验收 1/3: each hatch registers into its
        // own ability, and the legacy upstream steam input hatch can no longer
        // make any PartAbility.STEAM structure form while remaining resolvable
        // for old saves.
        helper.assertTrue(PartAbility.STEAM.isApplicable(GSEMachines.STEAM_SUPPLY_HATCH.getBlock()),
                "Steam supply hatch is not registered in PartAbility.STEAM");
        helper.assertTrue(!PartAbility.STEAM.isApplicable(GSEMachines.LARGE_STEAM_SUPPLY_HATCH.getBlock()),
                "Large steam supply hatch can still form ordinary steam structures");
        helper.assertTrue(GSEPartAbilities.LARGE_STEAM_SUPPLY.isApplicable(
                        GSEMachines.LARGE_STEAM_SUPPLY_HATCH.getBlock()),
                "Large steam supply hatch is not registered in LARGE_STEAM_SUPPLY");
        helper.assertTrue(GSEPartAbilities.STEAM_IMPORT_FLUIDS.isApplicable(
                        GSEMachines.STEAM_FLUID_IMPORT_HATCH.getBlock()),
                "Steam fluid input hatch is not registered in STEAM_IMPORT_FLUIDS");
        helper.assertTrue(GSEPartAbilities.STEAM_EXPORT_FLUIDS.isApplicable(
                        GSEMachines.STEAM_FLUID_EXPORT_HATCH.getBlock()),
                "Steam fluid output hatch is not registered in STEAM_EXPORT_FLUIDS");
        helper.assertTrue(GSEPartAbilities.STEAM_AIR_INTAKE.isApplicable(
                        GSEMachines.STEAM_AIR_INTAKE_HATCH.getBlock()),
                "Steam air intake hatch is not registered in STEAM_AIR_INTAKE");

        var legacy = GTMachines.STEAM_HATCH;
        helper.assertTrue(legacy != null, "Legacy gtceu:steam_input_hatch definition no longer resolves");
        if (legacy != null) {
            helper.assertTrue(legacy.getId().equals(GTCEu.id("steam_input_hatch")),
                    "Legacy steam hatch definition has an unexpected ID");
            helper.assertTrue(!PartAbility.STEAM.isApplicable(legacy.getBlock()),
                    "Legacy steam input hatch can still form PartAbility.STEAM structures");
        }
        helper.assertTrue(GTRecipes.RECIPE_FILTERS.contains(GTCEu.id("steam_hatch")),
                "The gtceu:steam_hatch recipe filter was never registered");
        helper.assertTrue(!helper.getLevel().getRecipeManager().byKey(GTCEu.id("steam_hatch")).isPresent(),
                "The legacy gtceu:steam_hatch recipe still loads");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void furnaceSteamSourceExtensionStaysExplicitAndInactive(GameTestHelper helper) {
        // large-heat-storage-steam-furnace.md future extension contract: the
        // declaration can express every reserved property, while no current
        // hatch silently opts into the dedicated-source ability.
        ResourceLocation testType = GregSteamExpansion.id("test_superheated_steam");
        FurnaceSteamSourceSpec spec = FurnaceSteamSourceSpec.capped(
                testType,
                stack -> stack.getFluid() == GTMaterials.Steam.getFluid(),
                3, 2, 2_400, 10,
                Set.of(FurnaceSteamCapability.ALLOW_MULTI_RECIPE_BATCH));

        helper.assertTrue(spec.steamTypeId().equals(testType), "Dedicated steam type ID was not retained");
        helper.assertTrue(spec.accepts(GTMaterials.Steam.getFluid(1)), "Declared steam predicate was ignored");
        helper.assertTrue(!spec.accepts(GTMaterials.Water.getFluid(1)), "Undeclared fluid passed the steam predicate");
        helper.assertTrue(spec.heatValueNumerator() == 3 && spec.heatValueDenominator() == 2,
                "Exact steam heat-value ratio was not retained");
        helper.assertTrue(!spec.unlimitedInput() && spec.inputLimitMbPerTick() == 2_400,
                "Capped steam-source flow declaration changed");
        helper.assertTrue(spec.priority() == 10, "Steam-source priority was not retained");
        helper.assertTrue(spec.unlocks(FurnaceSteamCapability.ALLOW_MULTI_RECIPE_BATCH),
                "Multi-recipe batch capability was not retained");
        helper.assertTrue(FurnaceSteamCapability.ALLOW_MULTI_RECIPE_BATCH.id().equals(
                        GregSteamExpansion.id("allow_multi_recipe_batch")),
                "Multi-recipe batch capability ID is unstable");

        FurnaceSteamSourceSpec unlimited = FurnaceSteamSourceSpec.unlimited(
                GregSteamExpansion.id("test_network_steam"), stack -> true,
                1, 1, 20, Set.of());
        helper.assertTrue(unlimited.unlimitedInput() && unlimited.inputLimitMbPerTick() == 0,
                "Unlimited steam-source declaration is ambiguous");

        helper.assertTrue(!GSEPartAbilities.FURNACE_STEAM_SOURCE.isApplicable(
                        GSEMachines.STEAM_SUPPLY_HATCH.getBlock()),
                "Standard steam supply hatch was incorrectly registered as a dedicated source");
        helper.assertTrue(!GSEPartAbilities.FURNACE_STEAM_SOURCE.isApplicable(
                        GSEMachines.LARGE_STEAM_SUPPLY_HATCH.getBlock()),
                "Large standard steam supply hatch was incorrectly registered as a dedicated source");
        helper.assertTrue(!GSEPartAbilities.FURNACE_STEAM_SOURCE.isApplicable(
                        GSEMachines.STEAM_FLUID_IMPORT_HATCH.getBlock()),
                "Recipe fluid input hatch was incorrectly registered as a dedicated source");
        helper.assertTrue(!GSEPartAbilities.FURNACE_STEAM_SOURCE.isApplicable(
                        GSEMachines.STEAM_FLUID_EXPORT_HATCH.getBlock()),
                "Recipe fluid output hatch was incorrectly registered as a dedicated source");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void steamSupplyHatchStoresOnlyStandardSteam(GameTestHelper helper) {
        // machines-and-hatches.md 实现验收 4: 32,000 mB steam in, the 32,001st
        // rejected, other fluids always rejected, and nothing drains back out.
        SteamSupplyHatchPartMachine hatch = placeHatch(helper, GSEMachines.STEAM_SUPPLY_HATCH, HATCH_POS);

        int filled = hatch.tank.fill(GTMaterials.Steam.getFluid(32_000), FluidAction.EXECUTE);
        helper.assertTrue(filled == 32_000, "Steam supply hatch refused 32,000 mB of standard steam");
        int overflow = hatch.tank.fill(GTMaterials.Steam.getFluid(1), FluidAction.EXECUTE);
        helper.assertTrue(overflow == 0, "Steam supply hatch accepted more than its 32,000 mB cache");
        int water = hatch.tank.fill(GTMaterials.Water.getFluid(1_000), FluidAction.EXECUTE);
        helper.assertTrue(water == 0, "Steam supply hatch accepted water");
        helper.assertTrue(hatch.tank.drain(1_000, FluidAction.SIMULATE).isEmpty(),
                "Steam supply hatch exposed its steam for external extraction");

        // The block capability reports the same real cache on every side.
        BlockEntity blockEntity = helper.getLevel().getBlockEntity(helper.absolutePos(HATCH_POS));
        helper.assertTrue(blockEntity != null, "Steam supply hatch block entity was missing");
        if (blockEntity != null) {
            for (Direction side : Direction.values()) {
                IFluidHandler fluids = blockEntity.getCapability(ForgeCapabilities.FLUID_HANDLER, side)
                        .orElse(null);
                helper.assertTrue(fluids != null, "No fluid capability on " + side.getName() + " side");
                if (fluids != null) {
                    helper.assertTrue(fluids.getTankCapacity(0) == 32_000,
                            "Fluid capability reported a wrong cache size on " + side.getName() + " side");
                    helper.assertTrue(fluids.fill(GTMaterials.Steam.getFluid(1), FluidAction.SIMULATE) == 0,
                            "Full steam supply hatch accepted steam on " + side.getName() + " side");
                }
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void largeSteamSupplyHatchAppliesLargeMachineRules(GameTestHelper helper) {
        LargeSteamSupplyHatchPartMachine hatch = placeHatch(
                helper, GSEMachines.LARGE_STEAM_SUPPLY_HATCH, HATCH_POS);

        int capacity = LargeSteamSupplyHatchPartMachine.TANK_CAPACITY;
        helper.assertTrue(hatch.tank.getTankCapacity(0) == capacity,
                "Large steam supply hatch does not expose its 256,000 mB capacity");
        helper.assertTrue(hatch.tank.fill(GTMaterials.Steam.getFluid(capacity), FluidAction.EXECUTE) == capacity,
                "Large steam supply hatch refused 256,000 mB of standard steam");
        helper.assertTrue(hatch.tank.fill(GTMaterials.Steam.getFluid(1), FluidAction.EXECUTE) == 0,
                "Large steam supply hatch accepted more than 256,000 mB");
        helper.assertTrue(hatch.tank.fill(GTMaterials.Water.getFluid(1_000), FluidAction.SIMULATE) == 0,
                "Large steam supply hatch accepted water");
        helper.assertTrue(hatch.tank.drain(1_000, FluidAction.SIMULATE).isEmpty(),
                "Large steam supply hatch exposed steam for external extraction");
        helper.assertTrue(!hatch.swapIO(), "Large steam supply hatch swapped into an output hatch");

        SteamBudget steamBudget = new SteamBudget(List.of(hatch));
        long expectedInputRate = SteamBudget.PHYSICAL_HATCH_LIMIT_MB
                * LargeSteamSupplyHatchPartMachine.MACHINE_INPUT_RATE_MULTIPLIER;
        helper.assertTrue(steamBudget.physicalInputLimitMb() == expectedInputRate,
                "Large steam supply hatch does not provide four times the ordinary input rate");
        int beforeDraw = hatch.tank.getFluidInTank(0).getAmount();
        helper.assertTrue(!steamBudget.drawSteam(expectedInputRate + 1, ignored -> {}),
                "Large steam supply hatch exceeded its 4,800 mB/t machine-side limit");
        helper.assertTrue(hatch.tank.getFluidInTank(0).getAmount() == beforeDraw,
                "Failed large-hatch draw was not atomic");
        helper.assertTrue(steamBudget.drawSteam(expectedInputRate, ignored -> {}),
                "Large steam supply hatch refused its exact 4,800 mB/t budget");
        helper.assertTrue(beforeDraw - hatch.tank.getFluidInTank(0).getAmount() == expectedInputRate,
                "Large steam supply hatch charged the wrong amount after a successful draw");

        BlockEntity blockEntity = helper.getLevel().getBlockEntity(helper.absolutePos(HATCH_POS));
        helper.assertTrue(blockEntity != null, "Large steam supply hatch block entity was missing");
        if (blockEntity != null) {
            for (Direction side : Direction.values()) {
                IFluidHandler fluids = blockEntity.getCapability(ForgeCapabilities.FLUID_HANDLER, side)
                        .orElse(null);
                helper.assertTrue(fluids != null,
                        "Large steam supply hatch has no fluid capability on " + side.getName());
                if (fluids != null) {
                    helper.assertTrue(fluids.getTankCapacity(0) == capacity,
                            "Large hatch capability reported a wrong capacity on " + side.getName());
                }
            }
        }

        var recipe = helper.getLevel().getRecipeManager()
                .byKey(GregSteamExpansion.id("shaped/large_steam_supply_hatch"))
                .orElse(null);
        helper.assertTrue(recipe != null, "Large steam supply hatch upgrade recipe is missing");
        if (recipe != null) {
            helper.assertTrue(recipe.getIngredients().stream()
                            .anyMatch(ingredient -> ingredient.test(GSEMachines.STEAM_SUPPLY_HATCH.asStack())),
                    "Large steam supply hatch recipe does not upgrade the ordinary supply hatch");
            var hvCircuitIterator = ForgeRegistries.ITEMS.tags().getTag(CustomTags.HV_CIRCUITS).iterator();
            helper.assertTrue(hvCircuitIterator.hasNext(), "HV circuit tag is empty");
            if (hvCircuitIterator.hasNext()) {
                Item hvCircuit = hvCircuitIterator.next();
                long circuitSlots = recipe.getIngredients().stream()
                        .filter(ingredient -> ingredient.test(new ItemStack(hvCircuit)))
                        .count();
                helper.assertTrue(circuitSlots == 4,
                        "Large steam supply hatch recipe does not require four arbitrary HV circuits");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void largeSteamOverclockLocksNextExecutionEconomics(GameTestHelper helper) {
        LargeSteamOverclock.LockedEconomics normal = LargeSteamOverclock.lock(601, 400, false, true);
        helper.assertTrue(!normal.active() && normal.durationTicks() == 601 && normal.steamPerTickMb() == 400,
                "Disabled large steam overclock changed execution economics");

        LargeSteamOverclock.LockedEconomics unavailable = LargeSteamOverclock.lock(601, 400, true, false);
        helper.assertTrue(!unavailable.active()
                        && unavailable.durationTicks() == 601
                        && unavailable.steamPerTickMb() == 400,
                "Large steam overclock activated without a Large Steam Supply Hatch");

        LargeSteamOverclock.LockedEconomics overclocked = LargeSteamOverclock.lock(601, 400, true, true);
        helper.assertTrue(overclocked.active(), "Available large steam overclock did not lock as active");
        helper.assertTrue(overclocked.durationTicks() == 301,
                "Large steam overclock did not round half duration up to a whole tick");
        helper.assertTrue(overclocked.steamPerTickMb() == 1_200,
                "Large steam overclock did not triple per-tick steam demand");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void steamFluidHatchesFollowDirectionSemantics(GameTestHelper helper) {
        // machines-and-hatches.md 实现验收 2/3: one fixed 16,000 mB tank per
        // hatch; the input hatch fills from outside and refuses extraction,
        // the output hatch drains outside and refuses external filling.
        SteamFluidHatchPartMachine input = placeHatch(helper, GSEMachines.STEAM_FLUID_IMPORT_HATCH, HATCH_POS);
        int inputFilled = input.tank.fill(GTMaterials.Water.getFluid(16_000), FluidAction.EXECUTE);
        helper.assertTrue(inputFilled == 16_000, "Steam fluid input hatch refused 16,000 mB");
        helper.assertTrue(input.tank.fill(GTMaterials.Water.getFluid(1), FluidAction.EXECUTE) == 0,
                "Steam fluid input hatch accepted more than 16,000 mB");
        helper.assertTrue(input.tank.drain(100, FluidAction.SIMULATE).isEmpty(),
                "Steam fluid input hatch exposed its content for external extraction");

        SteamFluidHatchPartMachine output = placeHatch(helper, GSEMachines.STEAM_FLUID_EXPORT_HATCH, HATCH_POS_EAST);
        helper.assertTrue(output.tank.fill(GTMaterials.Water.getFluid(1_000), FluidAction.EXECUTE) == 0,
                "Steam fluid output hatch accepted an external fill");
        output.tank.setFluidInTank(0, GTMaterials.Water.getFluid(1_000));
        helper.assertTrue(output.tank.drain(100, FluidAction.SIMULATE).getAmount() == 100,
                "Steam fluid output hatch refused an external drain");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void steamFluidHatchSwapIOPreservesContentAndFacing(GameTestHelper helper) {
        // machines-and-hatches.md 实现验收 6: world screwdriver swap converts
        // between the two hatches while keeping fluid and facings.
        SteamFluidHatchPartMachine input = placeHatch(helper, GSEMachines.STEAM_FLUID_IMPORT_HATCH, HATCH_POS);
        input.tank.fill(GTMaterials.Water.getFluid(5_000), FluidAction.EXECUTE);
        input.setFrontFacing(Direction.EAST);
        attachDisabledShutter(helper, input, Direction.WEST);

        helper.assertTrue(input.swapIO(), "Steam fluid input hatch refused the screwdriver swap");
        MetaMachine swapped = MetaMachine.getMachine(helper.getLevel(), helper.absolutePos(HATCH_POS));
        helper.assertTrue(swapped instanceof SteamFluidHatchPartMachine,
                "Swapped block did not create a steam fluid hatch");
        if (swapped instanceof SteamFluidHatchPartMachine output) {
            helper.assertTrue(swapped.getDefinition() == GSEMachines.STEAM_FLUID_EXPORT_HATCH,
                    "Swap did not convert the input hatch into the output hatch");
            helper.assertTrue(output.tank.getFluidInTank(0).getAmount() == 5_000,
                    "Swap lost the stored fluid");
            helper.assertTrue(output.getFrontFacing() == Direction.EAST, "Swap lost the front facing");
            assertDisabledShutter(helper, output, Direction.WEST, "Swap");

            helper.assertTrue(output.swapIO(), "Steam fluid output hatch refused the swap back");
            MetaMachine back = MetaMachine.getMachine(helper.getLevel(), helper.absolutePos(HATCH_POS));
            helper.assertTrue(back instanceof SteamFluidHatchPartMachine restored &&
                    restored.getDefinition() == GSEMachines.STEAM_FLUID_IMPORT_HATCH &&
                    restored.tank.getFluidInTank(0).getAmount() == 5_000,
                    "Swapping back did not restore the input hatch with its content");
            if (back instanceof SteamFluidHatchPartMachine restored) {
                assertDisabledShutter(helper, restored, Direction.WEST, "Swap back");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void steamFluidHatchSwapFailureRollsBackAtomically(GameTestHelper helper) {
        // machines-and-hatches.md 实现验收 6: if any replacement state cannot
        // be created, the original hatch and all of its state must survive.
        SteamFluidHatchPartMachine input = placeHatch(helper, GSEMachines.STEAM_FLUID_IMPORT_HATCH, HATCH_POS);
        input.tank.fill(GTMaterials.Water.getFluid(5_000), FluidAction.EXECUTE);
        input.tank.setLocked(true, GTMaterials.Water.getFluid(1));
        input.setFrontFacing(Direction.EAST);
        input.setPaintingColor(0x4C7899);
        input.setWorkingEnabled(false);
        attachDisabledShutter(helper, input, Direction.WEST);
        Direction expectedFront = input.getFrontFacing();
        Direction expectedUpwards = input.getUpwardsFacing();

        // A supply-hatch definition deliberately creates the wrong machine
        // class, exercising the same rollback used for a failed real swap.
        helper.assertTrue(!invokeSteamFluidHatchSwap(input, GSEMachines.STEAM_SUPPLY_HATCH),
                "Invalid steam fluid hatch replacement unexpectedly succeeded");

        MetaMachine machine = MetaMachine.getMachine(helper.getLevel(), helper.absolutePos(HATCH_POS));
        helper.assertTrue(machine instanceof SteamFluidHatchPartMachine,
                "Failed swap did not restore the original steam fluid hatch");
        if (machine instanceof SteamFluidHatchPartMachine restored) {
            helper.assertTrue(restored.getDefinition() == GSEMachines.STEAM_FLUID_IMPORT_HATCH,
                    "Failed swap restored the wrong hatch definition");
            helper.assertTrue(restored.tank.getFluidInTank(0).getAmount() == 5_000,
                    "Failed swap lost or duplicated stored fluid");
            helper.assertTrue(restored.tank.isLocked() &&
                            restored.tank.getLockedFluid().getFluid().getFluid() ==
                                    GTMaterials.Water.getFluid(1).getFluid(),
                    "Failed swap lost the fluid lock");
            helper.assertTrue(restored.getFrontFacing() == expectedFront,
                    "Failed swap lost the front facing");
            helper.assertTrue(restored.getUpwardsFacing() == expectedUpwards,
                    "Failed swap lost the upwards-facing state");
            helper.assertTrue(restored.getPaintingColor() == 0x4C7899,
                    "Failed swap lost hatch painting color");
            helper.assertTrue(!restored.isWorkingEnabled(),
                    "Failed swap reset the working-enabled state");
            assertDisabledShutter(helper, restored, Direction.WEST, "Failed swap rollback");
        }

        BlockPos absolutePos = helper.absolutePos(HATCH_POS);
        List<ItemEntity> shutterDrops = helper.getLevel().getEntitiesOfClass(ItemEntity.class,
                new AABB(absolutePos).inflate(1.0),
                entity -> entity.getItem().is(GTItems.COVER_SHUTTER.get()));
        helper.assertTrue(shutterDrops.isEmpty(), "Failed swap duplicated the restored shutter as an item drop");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 160)
    public static void steamAirIntakeDoesNotCollectWhileUnformed(GameTestHelper helper) {
        // machines-and-hatches.md 实现验收 1/4: the intake exposes no fluid
        // capability and collects nothing until its multiblock forms. The
        // full 80-tick collection cycle is exercised once the first
        // controller that accepts STEAM_AIR_INTAKE exists.
        SteamAirIntakeHatchPartMachine intake = placeHatch(helper, GSEMachines.STEAM_AIR_INTAKE_HATCH, HATCH_POS);
        BlockEntity blockEntity = helper.getLevel().getBlockEntity(helper.absolutePos(HATCH_POS));
        helper.assertTrue(blockEntity != null, "Steam air intake hatch block entity was missing");
        if (blockEntity != null) {
            IFluidHandler fluids = blockEntity.getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.NORTH)
                    .orElse(null);
            helper.assertTrue(fluids == null ||
                            (fluids.fill(GTMaterials.Air.getFluid(1_000), FluidAction.SIMULATE) == 0 &&
                                    fluids.drain(1_000, FluidAction.SIMULATE).isEmpty()),
                    "Steam air intake hatch exposed a usable fluid capability");
        }

        helper.runAfterDelay(100, () -> {
            helper.assertTrue(intake.tank.getFluidInTank(0).isEmpty(),
                    "Steam air intake hatch collected air without a formed multiblock");
            helper.assertTrue(intake.getIntakeStatus() == SteamAirIntakeHatchPartMachine.IntakeStatus.NOT_FORMED,
                    "Unformed steam air intake hatch reported the wrong status");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void legacySteamHatchPlacementConvertsInPlace(GameTestHelper helper) {
        // machines-and-hatches.md 禁用范围 4/旧存档迁移: placing a legacy item
        // must never produce a legacy block — the placement converts 1:1.
        var legacyBlock = GTMachines.STEAM_HATCH.getBlock();
        helper.assertTrue(legacyBlock != null, "Legacy steam hatch block missing");
        if (legacyBlock == null) {
            helper.succeed();
            return;
        }
        helper.setBlock(HATCH_POS, legacyBlock.defaultBlockState());
        BlockSnapshot snapshot = BlockSnapshot.create(helper.getLevel().dimension(), helper.getLevel(),
                helper.absolutePos(HATCH_POS));
        LegacySteamHatchCompat.onEntityPlace(new BlockEvent.EntityPlaceEvent(snapshot, null, null));

        BlockState converted = helper.getLevel().getBlockState(helper.absolutePos(HATCH_POS));
        helper.assertTrue(converted.is(GSEMachines.STEAM_SUPPLY_HATCH.getBlock()),
                "Legacy steam hatch placement was not converted to a steam supply hatch");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void legacySteamHatchWorldMigrationPreservesOverflow(GameTestHelper helper) {
        // machines-and-hatches.md 旧存档迁移: exercise the same block-entity
        // migration called after a legacy chunk loads, including over-cap steam.
        var legacyBlock = GTMachines.STEAM_HATCH.getBlock();
        helper.assertTrue(legacyBlock != null, "Legacy steam hatch block missing");
        if (legacyBlock == null) {
            helper.succeed();
            return;
        }
        helper.setBlock(HATCH_POS, legacyBlock.defaultBlockState());
        MetaMachine machine = MetaMachine.getMachine(helper.getLevel(), helper.absolutePos(HATCH_POS));
        helper.assertTrue(machine instanceof FluidHatchPartMachine,
                "Legacy steam hatch did not create its fluid-hatch block entity");
        if (!(machine instanceof FluidHatchPartMachine legacy)) {
            helper.succeed();
            return;
        }

        legacy.setFrontFacing(Direction.EAST);
        legacy.setUpwardsFacing(Direction.UP);
        legacy.setPaintingColor(0x5A7C91);
        legacy.setWorkingEnabled(false);
        legacy.tank.setFluidInTank(0, GTMaterials.Steam.getFluid(50_000));
        attachDisabledShutter(helper, legacy, Direction.WEST);
        Direction expectedFront = legacy.getFrontFacing();
        Direction expectedUpwards = legacy.getUpwardsFacing();

        invokeLegacySteamHatchMigration(helper, helper.absolutePos(HATCH_POS));
        MetaMachine migrated = MetaMachine.getMachine(helper.getLevel(), helper.absolutePos(HATCH_POS));
        helper.assertTrue(migrated instanceof SteamSupplyHatchPartMachine,
                "Legacy block entity was not replaced by a steam supply hatch");
        if (migrated instanceof SteamSupplyHatchPartMachine supply) {
            helper.assertTrue(supply.getFrontFacing() == expectedFront,
                    "Legacy migration lost the front facing");
            helper.assertTrue(supply.getUpwardsFacing() == expectedUpwards,
                    "Legacy migration lost the upwards facing");
            helper.assertTrue(supply.getPaintingColor() == 0x5A7C91,
                    "Legacy migration lost the painting color");
            helper.assertTrue(!supply.isWorkingEnabled(),
                    "Legacy migration lost the working-enabled state");
            assertDisabledShutter(helper, supply, Direction.WEST, "Legacy migration");
            helper.assertTrue(supply.tank.getFluidInTank(0).getAmount() == 50_000,
                    "Legacy migration truncated or duplicated over-cap steam");
            helper.assertTrue(supply.tank.fill(GTMaterials.Steam.getFluid(1), FluidAction.EXECUTE) == 0,
                    "Over-cap migrated hatch accepted more steam");

            int drained = supply.tank.getStorages()[0]
                    .drain(GTMaterials.Steam.getFluid(18_001), FluidAction.EXECUTE).getAmount();
            helper.assertTrue(drained == 18_001 && supply.tank.getFluidInTank(0).getAmount() == 31_999,
                    "Migrated over-cap steam could not be consumed normally");
            helper.assertTrue(supply.tank.fill(GTMaterials.Steam.getFluid(1), FluidAction.EXECUTE) == 1,
                    "Migrated hatch did not resume normal capacity behavior below the limit");

            invokeLegacySteamHatchMigration(helper, helper.absolutePos(HATCH_POS));
            MetaMachine repeated = MetaMachine.getMachine(helper.getLevel(), helper.absolutePos(HATCH_POS));
            helper.assertTrue(repeated == supply && supply.tank.getFluidInTank(0).getAmount() == 32_000,
                    "Repeated migration changed an already converted hatch");
        }
        helper.succeed();
    }

    private static boolean invokeSteamFluidHatchSwap(SteamFluidHatchPartMachine hatch,
                                                     MachineDefinition targetDefinition) {
        try {
            var method = SteamFluidHatchPartMachine.class.getDeclaredMethod(
                    "swapToDefinition", MachineDefinition.class);
            method.setAccessible(true);
            return (boolean) method.invoke(hatch, targetDefinition);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Could not invoke steam fluid hatch swap transaction", e);
        }
    }

    private static void attachDisabledShutter(GameTestHelper helper, MetaMachine machine, Direction side) {
        boolean attached = machine.getCoverContainer().placeCoverOnSide(
                side, GTItems.COVER_SHUTTER.asStack(), GTCovers.SHUTTER, null);
        helper.assertTrue(attached, "Could not attach the shutter cover used by the transfer test");
        var cover = machine.getCoverContainer().getCoverAtSide(side);
        helper.assertTrue(cover instanceof ShutterCover, "Attached test cover was not a shutter");
        if (cover instanceof ShutterCover shutter) {
            shutter.setWorkingEnabled(false);
        }
    }

    private static void assertDisabledShutter(GameTestHelper helper, MetaMachine machine, Direction side,
                                              String operation) {
        var cover = machine.getCoverContainer().getCoverAtSide(side);
        helper.assertTrue(cover instanceof ShutterCover, operation + " lost the shutter cover");
        if (cover instanceof ShutterCover shutter) {
            helper.assertTrue(shutter.coverDefinition == GTCovers.SHUTTER,
                    operation + " changed the shutter cover definition");
            helper.assertTrue(!shutter.isWorkingEnabled(), operation + " reset the shutter cover configuration");
            helper.assertTrue(shutter.getPickItem().is(GTItems.COVER_SHUTTER.get()),
                    operation + " changed the shutter cover item");
        }
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void legacySteamHatchItemsConvertOneToOne(GameTestHelper helper) {
        // machines-and-hatches.md 旧存档迁移: dropped entities and loaded
        // containers share the same exact-ID, count-preserving conversion.
        ItemEntity dropped = new ItemEntity(helper.getLevel(), 0.5, 1.0, 0.5,
                GTMachines.STEAM_HATCH.asStack(7));
        LegacySteamHatchCompat.onEntityJoinLevel(new EntityJoinLevelEvent(dropped, helper.getLevel()));
        helper.assertTrue(dropped.getItem().is(GSEMachines.STEAM_SUPPLY_HATCH.asStack().getItem()) &&
                        dropped.getItem().getCount() == 7,
                "Dropped legacy hatch stack did not convert 1:1");

        ItemEntity unrelated = new ItemEntity(helper.getLevel(), 1.5, 1.0, 0.5,
                new ItemStack(Items.IRON_INGOT, 3));
        LegacySteamHatchCompat.onEntityJoinLevel(new EntityJoinLevelEvent(unrelated, helper.getLevel()));
        helper.assertTrue(unrelated.getItem().is(Items.IRON_INGOT) && unrelated.getItem().getCount() == 3,
                "Legacy hatch conversion changed an unrelated dropped item");

        SimpleContainer container = new SimpleContainer(2);
        container.setItem(0, GTMachines.STEAM_HATCH.asStack(11));
        container.setItem(1, new ItemStack(Items.COBBLESTONE, 5));
        invokeLegacySteamHatchContainerMigration(container);
        helper.assertTrue(container.getItem(0).is(GSEMachines.STEAM_SUPPLY_HATCH.asStack().getItem()) &&
                        container.getItem(0).getCount() == 11,
                "Container legacy hatch stack did not convert 1:1");
        helper.assertTrue(container.getItem(1).is(Items.COBBLESTONE) && container.getItem(1).getCount() == 5,
                "Container migration changed an unrelated stack");
        helper.succeed();
    }

    private static void invokeLegacySteamHatchMigration(GameTestHelper helper, BlockPos pos) {
        try {
            var method = LegacySteamHatchCompat.class.getDeclaredMethod(
                    "migrateLegacyHatch", ServerLevel.class, BlockPos.class);
            method.setAccessible(true);
            method.invoke(null, helper.getLevel(), pos);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Could not invoke legacy steam hatch migration", e);
        }
    }

    private static void invokeLegacySteamHatchContainerMigration(Container container) {
        try {
            var method = LegacySteamHatchCompat.class.getDeclaredMethod("convertContainer", Container.class);
            method.setAccessible(true);
            method.invoke(null, container);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Could not invoke legacy steam hatch container migration", e);
        }
    }

    private static final BlockPos HATCH_POS = new BlockPos(0, 0, 0);
    private static final BlockPos HATCH_POS_EAST = new BlockPos(1, 0, 0);

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void largeCokeOvenHatchUsesModeAwareImplementation(GameTestHelper helper) {
        MetaMachine machine = placeHatch(helper, GSEMachines.LARGE_COKE_OVEN_HATCH, HATCH_POS);
        helper.assertTrue(machine instanceof LargeCokeOvenHatchPartMachine,
                "Large coke oven hatch registration created the placeholder implementation");
        if (machine instanceof LargeCokeOvenHatchPartMachine hatch) {
            helper.assertTrue(hatch.getMode() == CokeOvenMode.ITEM_INPUT,
                    "New large coke oven hatch did not default to item input mode");
            helper.assertTrue(hatch.getConnectionState().equals("none"),
                    "Unconnected large coke oven hatch reported an incorrect connection state");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void cokeOvenStaleClaimPruningUsesLoadedChunk(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos controllerPos = helper.absolutePos(HATCH_POS);
        CokeOvenWorldData data = CokeOvenWorldData.getOrCreate(level);
        var result = data.claim(level,
                CokeOvenWorldData.regularClaim(controllerPos, Direction.NORTH, false), null);
        helper.assertTrue(result instanceof CokeOvenWorldData.ClaimResult.Success,
                "Could not create the stale coke oven claim used by the pruning test");
        helper.assertTrue(data.hasClaim(controllerPos), "Test stale claim was not registered");

        data.pruneStaleClaims(level.getChunkAt(controllerPos));
        helper.assertTrue(!data.hasClaim(controllerPos),
                "Stale coke oven claim survived after its loaded chunk confirmed no controller");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void steamAssemblerControllerSlotsAcceptOnlyMatchingMachines(GameTestHelper helper) {
        AbstractSteamAssemblerMachine assembler = placeHatch(
                helper, GSEMachines.LARGE_STEAM_ASSEMBLER, HATCH_POS);
        AbstractSteamAssemblerMachine circuitAssembler = placeHatch(
                helper, GSEMachines.LARGE_STEAM_CIRCUIT_ASSEMBLER, HATCH_POS_EAST);

        ItemStack assemblerRemainder = assembler.getAssemblerSlotHandler().insertItem(
                0, GTMachines.ASSEMBLER[GTValues.LV].asStack(5), false);
        helper.assertTrue(assemblerRemainder.getCount() == 1 && assembler.getAssemblerCount() == 4,
                "Large steam assembler slot did not accept a four-machine LV stack");
        helper.assertTrue(assembler.getAssemblerTier() == GTValues.LV && assembler.maximumParallel() == 16,
                "Large steam assembler slot did not update its tier and parallel cap");
        assembler.getAssemblerSlotHandler().setStackInSlot(0, ItemStack.EMPTY);
        ItemStack wrongAssembler = assembler.getAssemblerSlotHandler().insertItem(
                0, GTMachines.CIRCUIT_ASSEMBLER[GTValues.LV].asStack(), false);
        helper.assertTrue(!wrongAssembler.isEmpty() && assembler.getAssemblerStack().isEmpty(),
                "Large steam assembler slot accepted a circuit assembler");

        ItemStack circuitRemainder = circuitAssembler.getAssemblerSlotHandler().insertItem(
                0, GTMachines.CIRCUIT_ASSEMBLER[GTValues.EV].asStack(4), false);
        helper.assertTrue(circuitRemainder.isEmpty() && circuitAssembler.getAssemblerCount() == 4,
                "Large steam circuit assembler slot refused matching EV circuit assemblers");
        helper.assertTrue(circuitAssembler.getAssemblerTier() == GTValues.EV &&
                        circuitAssembler.maximumParallel() == 16,
                "Large steam circuit assembler slot did not update its tier and parallel cap");
        circuitAssembler.getAssemblerSlotHandler().setStackInSlot(0, ItemStack.EMPTY);
        ItemStack wrongCircuitAssembler = circuitAssembler.getAssemblerSlotHandler().insertItem(
                0, GTMachines.ASSEMBLER[GTValues.LV].asStack(), false);
        helper.assertTrue(!wrongCircuitAssembler.isEmpty() && circuitAssembler.getAssemblerStack().isEmpty(),
                "Large steam circuit assembler slot accepted a normal assembler");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void largeSteamBlastFurnaceWiredToPrimitiveBlastFurnaceType(GameTestHelper helper) {
        // large-steam-blast-furnace.md 实现验收: 控制器注册并绑定上游
        // primitive_blast_furnace 配方类型。
        MachineDefinition definition = GTRegistries.MACHINES.get(GregSteamExpansion.id("large_steam_blast_furnace"));
        helper.assertTrue(definition != null, "Large steam blast furnace is not registered");
        if (definition == null) {
            return;
        }
        helper.assertTrue(
                java.util.List.of(definition.getRecipeTypes())
                        .contains(com.gregtechceu.gtceu.common.data.GTRecipeTypes.PRIMITIVE_BLAST_FURNACE_RECIPES),
                "Large steam blast furnace must run the primitive_blast_furnace recipe type");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void wroughtIronRecipesInjectedIntoPrimitiveBlastFurnace(GameTestHelper helper) {
        // large-steam-blast-furnace.md 锻铁配方注入验收: 3 条纯粉配方进入上游
        // primitive_blast_furnace 类型 (PBF 与本机共用); 蒸汽时代锻铁缺口修复。
        // 2026-09-09 裁定: 铁源与燃料都必须是 dust, gem 版已移除。
        GTRecipeType type = com.gregtechceu.gtceu.common.data.GTRecipeTypes.PRIMITIVE_BLAST_FURNACE_RECIPES;
        java.util.Set<ResourceLocation> ids = new java.util.HashSet<>();
        for (var recipe : type.getRecipesInCategory(type.getCategory())) {
            ids.add(recipe.getId());
        }
        // 配方 id 由 datagen 的文件路径决定: data/gregsteamexpansion/recipes/
        // <type path>/<name>.json → gregsteamexpansion:<type path>/<name>, 因此
        // 这里按 path 后缀匹配而不是整体相等 (避免类型前缀变化导致误判)。
        String[] expected = {
                "wrought_iron_from_dust_coal_dust",
                "wrought_iron_from_dust_charcoal_dust",
                "wrought_iron_from_dust_coke_dust",
        };
        for (String path : expected) {
            boolean found = ids.stream().anyMatch(id -> GregSteamExpansion.MOD_ID.equals(id.getNamespace())
                    && id.getPath().endsWith(path));
            helper.assertTrue(found,
                    "Wrought iron blast recipe missing from the primitive blast furnace type: " + path);
        }
        long modRecipes = ids.stream()
                .filter(id -> GregSteamExpansion.MOD_ID.equals(id.getNamespace()))
                .filter(id -> id.getPath().contains("wrought_iron_from_dust"))
                .count();
        helper.assertTrue(modRecipes == expected.length,
                "Expected exactly " + expected.length + " wrought iron recipes, found " + modRecipes);
        helper.succeed();
    }

    //////////////////////////////////////
    // *** 结构成型（照 shapeInfo 铺，验证图案本身）***//
    //////////////////////////////////////

    @GameTest(template = "empty_32x32x32", timeoutTicks = 200)
    public static void steamChemicalBathFormsFromShape(GameTestHelper helper) {
        GSEStructureTestUtils.assertFirstShapeForms(helper, GSEMachines.STEAM_CHEMICAL_BATH);
    }

    @GameTest(template = "empty_32x32x32", timeoutTicks = 200)
    public static void steamCrusherFormsFromShape(GameTestHelper helper) {
        GSEStructureTestUtils.assertFirstShapeReplacementMatches(
                helper,
                GSEMachines.STEAM_CRUSHER,
                GSEMachines.STEAM_SUPPLY_HATCH.getBlock(),
                GSEMachines.LARGE_STEAM_SUPPLY_HATCH,
                false);
    }

    @GameTest(template = "empty_32x32x32", timeoutTicks = 200)
    public static void largeSteamCrusherFormsFromShape(GameTestHelper helper) {
        GSEStructureTestUtils.assertFirstShapeReplacementMatches(
                helper,
                GSEMachines.LARGE_STEAM_CRUSHER,
                GSEMachines.STEAM_SUPPLY_HATCH.getBlock(),
                GSEMachines.LARGE_STEAM_SUPPLY_HATCH,
                true);
    }

    @GameTest(template = "empty_32x32x32", timeoutTicks = 200)
    public static void steamCompressorFormsFromShape(GameTestHelper helper) {
        GSEStructureTestUtils.assertFirstShapeForms(helper, GSEMachines.STEAM_COMPRESSOR);
    }

    @GameTest(template = "empty_32x32x32", timeoutTicks = 200)
    public static void steamExtractorFormsFromShape(GameTestHelper helper) {
        GSEStructureTestUtils.assertFirstShapeForms(helper, GSEMachines.STEAM_EXTRACTOR);
    }

    @GameTest(template = "empty_32x32x32", timeoutTicks = 200)
    public static void steamForgeFormsFromShape(GameTestHelper helper) {
        GSEStructureTestUtils.assertFirstShapeForms(helper, GSEMachines.STEAM_FORGE);
    }

    @GameTest(template = "empty_32x32x32", timeoutTicks = 200)
    public static void largeSteamOreWasherFormsFromShape(GameTestHelper helper) {
        GSEStructureTestUtils.assertFirstShapeForms(helper, GSEMachines.LARGE_STEAM_ORE_WASHER);
    }

    @GameTest(template = "empty_32x32x32", timeoutTicks = 200)
    public static void largeSteamThermalCentrifugeFormsFromShape(GameTestHelper helper) {
        GSEStructureTestUtils.assertFirstShapeForms(helper, GSEMachines.LARGE_STEAM_THERMAL_CENTRIFUGE);
    }

    @GameTest(template = "empty_32x32x32", timeoutTicks = 200)
    public static void largeSteamMaceratorFormsFromShape(GameTestHelper helper) {
        GSEStructureTestUtils.assertFirstShapeForms(helper, GSEMachines.LARGE_STEAM_MACERATOR);
    }

    @GameTest(template = "empty_32x32x32", timeoutTicks = 200)
    public static void largeSteamMixerFormsFromShape(GameTestHelper helper) {
        GSEStructureTestUtils.assertFirstShapeForms(helper, GSEMachines.LARGE_STEAM_MIXER);
    }

    @GameTest(template = "empty_32x32x32", timeoutTicks = 200)
    public static void steamCentrifugeFormsFromShape(GameTestHelper helper) {
        GSEStructureTestUtils.assertFirstShapeForms(helper, GSEMachines.STEAM_CENTRIFUGE);
    }

    @GameTest(template = "empty_32x32x32", timeoutTicks = 200)
    public static void largeSteamCentrifugeFormsFromShape(GameTestHelper helper) {
        GSEStructureTestUtils.assertFirstShapeForms(helper, GSEMachines.LARGE_STEAM_CENTRIFUGE);
    }

    @GameTest(template = "empty_32x32x32", timeoutTicks = 200)
    public static void largeSteamBlastFurnaceFormsFromShape(GameTestHelper helper) {
        GSEStructureTestUtils.assertFirstShapeForms(helper, GSEMachines.LARGE_STEAM_BLAST_FURNACE);
    }

    @GameTest(template = "empty_32x32x32", timeoutTicks = 300)
    public static void largeSteamBlastFurnaceRejectsInvalidInterfaces(GameTestHelper helper) {
        var definition = GSEMachines.LARGE_STEAM_BLAST_FURNACE;
        MultiblockControllerMachine machine = GSEStructureTestUtils.placeShape(
                helper, definition, definition.getMatchingShapes().get(0));
        helper.assertTrue(machine != null, "Missing blast-furnace fixture controller");

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(machine.isFormed(),
                        "Baseline blast-furnace fixture did not form"))
                .thenExecute(() -> {
                    List<BlockPos> intakes = new java.util.ArrayList<>();
                    List<BlockPos> exhausts = new java.util.ArrayList<>();
                    List<BlockPos> bricks = new java.util.ArrayList<>();
                    for (BlockPos pos : BlockPos.betweenClosed(
                            machine.getPos().offset(-15, -15, -15),
                            machine.getPos().offset(15, 15, 15))) {
                        BlockState state = helper.getLevel().getBlockState(pos);
                        if (state.is(GSEMachines.STEAM_AIR_INTAKE_HATCH.getBlock())) {
                            intakes.add(pos.immutable());
                        } else if (state.is(GSEMachines.STEAM_EXHAUST_HATCH.getBlock())) {
                            exhausts.add(pos.immutable());
                        } else if (state.is(GSEProcessorPatterns.blastBricks())) {
                            bricks.add(pos.immutable());
                        }
                    }
                    helper.assertTrue(intakes.size() == 3,
                            "Blast-furnace preview intake count changed: " + intakes.size());
                    helper.assertTrue(exhausts.size() == 1,
                            "Blast-furnace preview exhaust count changed: " + exhausts.size());
                    helper.assertTrue(bricks.size() >= 8,
                            "Blast-furnace preview lacks mutation candidate bricks");

                    machine.onStructureInvalid();
                    Map<BlockPos, BlockState> intakeStates = new HashMap<>();
                    for (BlockPos pos : intakes) {
                        intakeStates.put(pos, helper.getLevel().getBlockState(pos));
                        helper.getLevel().setBlockAndUpdate(
                                pos, GSEProcessorPatterns.blastBricks().defaultBlockState());
                    }
                    helper.assertTrue(!machine.checkPattern(),
                            "Blast furnace formed without an air intake hatch");
                    intakeStates.forEach(helper.getLevel()::setBlockAndUpdate);
                    helper.assertTrue(machine.checkPattern(),
                            "Blast furnace did not recover after restoring its air intakes");

                    BlockState intakeState = intakeStates.get(intakes.get(0));
                    for (int i = 0; i < 5; i++) {
                        helper.getLevel().setBlockAndUpdate(bricks.get(i), intakeState);
                    }
                    helper.assertTrue(machine.checkPattern(),
                            "Blast furnace rejected the legal eight-intake maximum");
                    helper.getLevel().setBlockAndUpdate(bricks.get(5), intakeState);
                    helper.assertTrue(!machine.checkPattern(),
                            "Blast furnace formed with a ninth air intake hatch");
                    for (int i = 0; i < 6; i++) {
                        helper.getLevel().setBlockAndUpdate(
                                bricks.get(i), GSEProcessorPatterns.blastBricks().defaultBlockState());
                    }
                    helper.assertTrue(machine.checkPattern(),
                            "Blast furnace did not recover after removing excess air intakes");

                    BlockPos fluidProbe = bricks.get(6);
                    helper.getLevel().setBlockAndUpdate(
                            fluidProbe, GTMachines.FLUID_IMPORT_HATCH[1].getBlock().defaultBlockState());
                    helper.assertTrue(!machine.checkPattern(),
                            "Blast furnace accepted a standard fluid input hatch");
                    helper.getLevel().setBlockAndUpdate(fluidProbe,
                            GSEMachines.STEAM_FLUID_IMPORT_HATCH.getBlock().defaultBlockState());
                    helper.assertTrue(!machine.checkPattern(),
                            "Blast furnace accepted a steam fluid input hatch");
                    helper.getLevel().setBlockAndUpdate(
                            fluidProbe, GSEProcessorPatterns.blastBricks().defaultBlockState());
                    helper.assertTrue(machine.checkPattern(),
                            "Blast furnace did not recover after removing the fluid hatch");

                    BlockPos exhaust = exhausts.get(0);
                    BlockState exhaustState = helper.getLevel().getBlockState(exhaust);
                    helper.getLevel().setBlockAndUpdate(
                            exhaust, GSEProcessorPatterns.blastBricks().defaultBlockState());
                    helper.assertTrue(!machine.checkPattern(),
                            "Blast furnace formed without its exhaust hatch");
                    helper.getLevel().setBlockAndUpdate(exhaust, exhaustState);
                    helper.assertTrue(machine.checkPattern(),
                            "Blast furnace did not recover after restoring its exhaust hatch");

                    BlockPos duplicateExhaust = bricks.get(7);
                    helper.getLevel().setBlockAndUpdate(duplicateExhaust, exhaustState);
                    helper.assertTrue(!machine.checkPattern(),
                            "Blast furnace formed with two exhaust hatches");
                    helper.getLevel().setBlockAndUpdate(
                            duplicateExhaust, GSEProcessorPatterns.blastBricks().defaultBlockState());
                    helper.assertTrue(machine.checkPattern(),
                            "Blast furnace did not recover after removing its duplicate exhaust");
                    machine.onStructureFormed();
                    helper.assertTrue(machine.isFormed(),
                            "Blast furnace remained invalid after restoring its legal interfaces");
                })
                .thenSucceed();
    }

    @GameTest(template = "empty_32x32x32", timeoutTicks = 300)
    public static void largeSteamBlastFurnaceHasNoCombinedInterfaceCap(GameTestHelper helper) {
        var definition = GSEMachines.LARGE_STEAM_BLAST_FURNACE;
        MultiblockControllerMachine machine = GSEStructureTestUtils.placeShape(
                helper, definition, definition.getMatchingShapes().get(0));
        helper.assertTrue(machine != null, "Missing blast-furnace fixture controller");
        if (machine == null) {
            return;
        }

        List<BlockPos> bricks = new java.util.ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(
                machine.getPos().offset(-15, -15, -15),
                machine.getPos().offset(15, 15, 15))) {
            if (helper.getLevel().getBlockState(pos).is(GSEProcessorPatterns.blastBricks())) {
                bricks.add(pos.immutable());
            }
        }
        // The preview contains 14 interfaces. Fifteen additional supply
        // hatches produce 29 total, deliberately crossing the obsolete tooltip
        // limit while preserving all per-interface minimum and exact limits.
        helper.assertTrue(bricks.size() >= 15,
                "Blast-furnace preview lacks interface expansion positions");
        machine.onStructureInvalid();
        for (int i = 0; i < 15; i++) {
            helper.getLevel().setBlockAndUpdate(bricks.get(i),
                    GSEMachines.STEAM_SUPPLY_HATCH.getBlock().defaultBlockState());
        }
        helper.assertTrue(machine.checkPattern(),
                "Blast furnace rejected 29 interfaces despite having no combined cap");
        machine.onStructureFormed();
        helper.assertTrue(machine.isFormed(),
                "Blast furnace did not form with 29 interfaces");
        helper.assertTrue(machine.getParts().size() == 29,
                "Blast furnace collected " + machine.getParts().size() + " interfaces instead of 29");
        helper.succeed();
    }

    @GameTest(template = "empty_32x32x32", timeoutTicks = 300)
    public static void largeSteamBlastFurnaceFormsInAllHorizontalDirections(GameTestHelper helper) {
        var definition = GSEMachines.LARGE_STEAM_BLAST_FURNACE;
        var shape = definition.getMatchingShapes().get(0);
        Direction[] facings = { Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST };
        BlockPos[] anchors = {
                new BlockPos(7, 16, 1),
                new BlockPos(30, 16, 7),
                new BlockPos(24, 16, 30),
                new BlockPos(1, 16, 24)
        };
        List<MultiblockControllerMachine> machines = new java.util.ArrayList<>();

        for (int i = 0; i < facings.length; i++) {
            Direction facing = facings[i];
            MultiblockControllerMachine machine = GSEStructureTestUtils.placeShape(
                    helper, definition, shape, anchors[i], facing);
            helper.assertTrue(machine != null,
                    "Missing " + facing + " blast-furnace fixture controller");
            if (machine == null) {
                return;
            }
            helper.assertTrue(machine.getFrontFacing() == facing,
                    "Blast-furnace controller did not retain " + facing + " facing");
            helper.assertTrue(machine.checkPattern(),
                    facing + " blast-furnace structure did not match its pattern");
            machines.add(machine);
        }

        helper.startSequence()
                .thenWaitUntil(() -> {
                    for (int i = 0; i < machines.size(); i++) {
                        helper.assertTrue(machines.get(i).isFormed(),
                                facings[i] + " blast furnace did not form");
                    }
                })
                .thenSucceed();
    }

    @GameTest(template = "empty_32x32x32", timeoutTicks = 300)
    public static void adjacentLargeSteamBlastFurnacesShareSideWall(GameTestHelper helper) {
        var definition = GSEMachines.LARGE_STEAM_BLAST_FURNACE;
        var shape = definition.getMatchingShapes().get(0);
        MultiblockControllerMachine left = GSEStructureTestUtils.placeShape(
                helper, definition, shape, new BlockPos(7, 16, 1), Direction.NORTH);
        MultiblockControllerMachine right = GSEStructureTestUtils.placeShape(
                helper, definition, shape, new BlockPos(19, 16, 1), Direction.NORTH);
        helper.assertTrue(left != null && right != null,
                "Missing adjacent blast-furnace fixture controller");
        if (left == null || right == null) {
            return;
        }

        // The 13-wide hearth and tuyere deck overlap at x=13. The west preview
        // carries two intake hatches on this plane, so normalize the eleven
        // candidate cells to blast bricks: both structures then share only
        // ordinary structure blocks, while the industrial corner cells remain.
        for (int z = 2; z <= 12; z++) {
            helper.setBlock(new BlockPos(13, 17, z),
                    GSEProcessorPatterns.blastBricks().defaultBlockState());
        }
        for (int z = 1; z <= 13; z++) {
            helper.assertTrue(!helper.getBlockState(new BlockPos(13, 16, z)).isAir(),
                    "Shared hearth wall contains air at z=" + z);
            helper.assertTrue(!helper.getBlockState(new BlockPos(13, 17, z)).isAir(),
                    "Shared tuyere wall contains air at z=" + z);
        }

        helper.assertTrue(left.checkPattern(),
                "Left blast furnace rejected the shared side wall");
        helper.assertTrue(right.checkPattern(),
                "Right blast furnace rejected the shared side wall");
        helper.startSequence()
                .thenWaitUntil(() -> {
                    helper.assertTrue(left.isFormed(),
                            "Left blast furnace did not remain formed on the shared wall");
                    helper.assertTrue(right.isFormed(),
                            "Right blast furnace did not remain formed on the shared wall");
                })
                .thenSucceed();
    }

    @GameTest(template = "empty_32x32x32", timeoutTicks = 200)
    public static void largeSteamAssemblerFormsFromShape(GameTestHelper helper) {
        var definition = GSEMachines.LARGE_STEAM_ASSEMBLER;
        List<MultiblockShapeInfo> shapes = definition.getMatchingShapes();
        helper.assertTrue(!shapes.isEmpty(), "Large steam assembler has no preview shape");
        if (shapes.isEmpty()) {
            return;
        }

        BlockInfo[][][] blocks = shapes.get(0).getBlocks();
        helper.assertTrue(blocks.length == 9, "Large steam assembler width is not 9");
        int industrial = 0;
        int wallCasings = 0;
        int workstations = 0;
        int air = 0;
        int interfaces = 0;
        int controllers = 0;
        for (int column = 0; column < blocks.length; column++) {
            helper.assertTrue(blocks[column].length == 9,
                    "Large steam assembler height is not 9 at column " + column);
            for (int layer = 0; layer < blocks[column].length; layer++) {
                helper.assertTrue(blocks[column][layer].length == 9,
                        "Large steam assembler depth is not 9 at column/layer " + column + "/" + layer);
                for (int depth = 0; depth < blocks[column][layer].length; depth++) {
                    BlockState state = blocks[column][layer][depth].getBlockState();
                    Block expected = expectedLargeSteamAssemblerBlock(definition, depth, layer, column);
                    helper.assertTrue(expected == Blocks.AIR ? state.isAir() : state.is(expected),
                            "Large steam assembler shape differs at depth/layer/column "
                                    + depth + "/" + layer + "/" + column
                                    + ": expected=" + expected + ", actual=" + state.getBlock());

                    if (state.is(GSEProcessorPatterns.industrialSteamCasing())) industrial++;
                    else if (state.is(GSEProcessorPatterns.bronzeSteamCasing())) wallCasings++;
                    else if (state.is(GSEBlocks.STEAM_ASSEMBLY_BLOCK.get())) workstations++;
                    else if (state.isAir()) air++;
                    else if (state.is(definition.getBlock())) controllers++;
                    else interfaces++;
                }
            }
        }
        helper.assertTrue(industrial == 189, "Expected 189 industrial casings, found " + industrial);
        helper.assertTrue(wallCasings == 191,
                "Expected 191 wall casings after five representative interfaces, found " + wallCasings);
        helper.assertTrue(interfaces == 5, "Expected five representative interfaces, found " + interfaces);
        helper.assertTrue(workstations == 18, "Expected 18 assembly workstations, found " + workstations);
        helper.assertTrue(air == 325, "Expected 325 strict-air cells, found " + air);
        helper.assertTrue(controllers == 1, "Expected one assembler controller, found " + controllers);

        MultiblockControllerMachine machine = GSEStructureTestUtils.placeShape(
                helper, definition, shapes.get(0));
        helper.assertTrue(machine != null, "Large steam assembler fixture has no controller");
        if (machine == null) {
            return;
        }
        helper.assertTrue(machine.checkPattern(), "Exact large steam assembler shape did not match its pattern");
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(machine.isFormed(),
                        "Exact large steam assembler did not become formed"))
                .thenSucceed();
    }

    @GameTest(template = "empty_32x32x32", timeoutTicks = 300)
    public static void largeSteamAssemblerRestrictsHatchesToWallCasings(GameTestHelper helper) {
        var definition = GSEMachines.LARGE_STEAM_ASSEMBLER;
        MultiblockControllerMachine machine = GSEStructureTestUtils.placeShape(
                helper, definition, definition.getMatchingShapes().get(0));
        helper.assertTrue(machine != null, "Missing large steam assembler fixture controller");
        if (machine == null) {
            return;
        }

        BlockPos controller = machine.getPos();
        // North-facing preview coordinates relative to the bottom-front controller:
        // one replaceable front wall cell and one fixed cell from each industrial region.
        BlockPos wall = controller.offset(-3, 2, 0);
        BlockPos bottom = controller.offset(-3, 0, 4);
        BlockPos top = controller.offset(0, 8, 4);
        BlockPos verticalEdge = controller.offset(-4, 2, 0);
        BlockState wallState = helper.getLevel().getBlockState(wall);
        BlockState bottomState = helper.getLevel().getBlockState(bottom);
        BlockState topState = helper.getLevel().getBlockState(top);
        BlockState edgeState = helper.getLevel().getBlockState(verticalEdge);
        BlockState hatchState = GSEMachines.STEAM_SUPPLY_HATCH.getBlock().defaultBlockState();

        helper.assertTrue(wallState.is(GSEProcessorPatterns.bronzeSteamCasing()),
                "Assembler wall probe is not a bronze steam casing");
        helper.assertTrue(bottomState.is(GSEProcessorPatterns.industrialSteamCasing()),
                "Assembler bottom probe is not an industrial steam casing");
        helper.assertTrue(topState.is(GSEProcessorPatterns.industrialSteamCasing()),
                "Assembler top probe is not an industrial steam casing");
        helper.assertTrue(edgeState.is(GSEProcessorPatterns.industrialSteamCasing()),
                "Assembler edge probe is not an industrial steam casing");

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(machine.isFormed(),
                        "Baseline large steam assembler fixture did not form"))
                .thenExecute(() -> {
                    machine.onStructureInvalid();

                    helper.getLevel().setBlockAndUpdate(wall, hatchState);
                    helper.assertTrue(machine.checkPattern(),
                            "Large steam assembler rejected a supply hatch on its wall");
                    helper.getLevel().setBlockAndUpdate(wall, wallState);
                    helper.assertTrue(machine.checkPattern(),
                            "Large steam assembler did not recover its wall casing");

                    helper.getLevel().setBlockAndUpdate(bottom, hatchState);
                    helper.assertTrue(!machine.checkPattern(),
                            "Large steam assembler accepted a hatch on its bottom face");
                    helper.getLevel().setBlockAndUpdate(bottom, bottomState);
                    helper.assertTrue(machine.checkPattern(),
                            "Large steam assembler did not recover its bottom casing");

                    helper.getLevel().setBlockAndUpdate(top, hatchState);
                    helper.assertTrue(!machine.checkPattern(),
                            "Large steam assembler accepted a hatch on its top face");
                    helper.getLevel().setBlockAndUpdate(top, topState);
                    helper.assertTrue(machine.checkPattern(),
                            "Large steam assembler did not recover its top casing");

                    helper.getLevel().setBlockAndUpdate(verticalEdge, hatchState);
                    helper.assertTrue(!machine.checkPattern(),
                            "Large steam assembler accepted a hatch on its vertical edge");
                    helper.getLevel().setBlockAndUpdate(verticalEdge, edgeState);
                    helper.assertTrue(machine.checkPattern(),
                            "Large steam assembler did not recover its edge casing");

                    machine.onStructureFormed();
                    helper.assertTrue(machine.isFormed(),
                            "Large steam assembler remained invalid after restoring its shell");
                })
                .thenSucceed();
    }

    @GameTest(template = "empty_32x32x32", timeoutTicks = 300)
    public static void largeSteamAssemblerRequiresExactlyOneExhaust(GameTestHelper helper) {
        var definition = GSEMachines.LARGE_STEAM_ASSEMBLER;
        MultiblockControllerMachine machine = GSEStructureTestUtils.placeShape(
                helper, definition, definition.getMatchingShapes().get(0));
        helper.assertTrue(machine != null, "Missing large steam assembler fixture controller");
        if (machine == null) {
            return;
        }

        List<BlockPos> exhausts = new java.util.ArrayList<>();
        List<BlockPos> wallCasings = new java.util.ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(
                machine.getPos().offset(-4, 0, 0), machine.getPos().offset(4, 8, 8))) {
            BlockState state = helper.getLevel().getBlockState(pos);
            if (state.is(GSEMachines.STEAM_EXHAUST_HATCH.getBlock())) {
                exhausts.add(pos.immutable());
            } else if (state.is(GSEProcessorPatterns.bronzeSteamCasing())) {
                wallCasings.add(pos.immutable());
            }
        }
        helper.assertTrue(exhausts.size() == 1,
                "Assembler preview exhaust count changed: " + exhausts.size());
        helper.assertTrue(!wallCasings.isEmpty(), "Assembler preview has no wall casing mutation position");

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(machine.isFormed(),
                        "Baseline large steam assembler fixture did not form"))
                .thenExecute(() -> {
                    machine.onStructureInvalid();
                    BlockPos exhaust = exhausts.get(0);
                    BlockState exhaustState = helper.getLevel().getBlockState(exhaust);
                    BlockState wallState = GSEProcessorPatterns.bronzeSteamCasing().defaultBlockState();

                    helper.getLevel().setBlockAndUpdate(exhaust, wallState);
                    helper.assertTrue(!machine.checkPattern(),
                            "Large steam assembler formed without an exhaust hatch");
                    helper.getLevel().setBlockAndUpdate(exhaust, exhaustState);
                    helper.assertTrue(machine.checkPattern(),
                            "Large steam assembler did not recover after restoring its exhaust hatch");

                    BlockPos duplicate = wallCasings.get(0);
                    BlockState duplicateOriginal = helper.getLevel().getBlockState(duplicate);
                    helper.getLevel().setBlockAndUpdate(duplicate, exhaustState);
                    helper.assertTrue(!machine.checkPattern(),
                            "Large steam assembler formed with two exhaust hatches");
                    helper.getLevel().setBlockAndUpdate(duplicate, duplicateOriginal);
                    helper.assertTrue(machine.checkPattern(),
                            "Large steam assembler did not recover after removing its duplicate exhaust");

                    machine.onStructureFormed();
                    helper.assertTrue(machine.isFormed(),
                            "Large steam assembler remained invalid with exactly one exhaust hatch");
                })
                .thenSucceed();
    }

    @GameTest(template = "empty_32x32x32", timeoutTicks = 300)
    public static void adjacentLargeSteamAssemblersShareSideWall(GameTestHelper helper) {
        var definition = GSEMachines.LARGE_STEAM_ASSEMBLER;
        var shape = definition.getMatchingShapes().get(0);
        MultiblockControllerMachine left = GSEStructureTestUtils.placeShape(
                helper, definition, shape, new BlockPos(5, 16, 4), Direction.NORTH);
        MultiblockControllerMachine right = GSEStructureTestUtils.placeShape(
                helper, definition, shape, new BlockPos(13, 16, 4), Direction.NORTH);
        helper.assertTrue(left != null && right != null,
                "Missing adjacent large steam assembler fixture controller");
        if (left == null || right == null) {
            return;
        }

        // The controllers are eight blocks apart, so their nine-wide shells
        // share the complete x=9 side wall without sharing any interface part.
        for (int y = 16; y <= 24; y++) {
            for (int z = 4; z <= 12; z++) {
                BlockState state = helper.getBlockState(new BlockPos(9, y, z));
                boolean industrial = y == 16 || y == 24 || z == 4 || z == 12;
                helper.assertTrue(industrial
                                ? state.is(GSEProcessorPatterns.industrialSteamCasing())
                                : state.is(GSEProcessorPatterns.bronzeSteamCasing()),
                        "Shared assembler wall differs at y/z=" + y + "/" + z);
            }
        }

        helper.assertTrue(left.checkPattern(), "Left assembler rejected the shared side wall");
        helper.assertTrue(right.checkPattern(), "Right assembler rejected the shared side wall");
        helper.startSequence()
                .thenWaitUntil(() -> {
                    helper.assertTrue(left.isFormed(),
                            "Left assembler did not remain formed on the shared side wall");
                    helper.assertTrue(right.isFormed(),
                            "Right assembler did not remain formed on the shared side wall");
                })
                .thenSucceed();
    }

    private static Block expectedLargeSteamAssemblerBlock(MultiblockMachineDefinition definition,
                                                           int depth, int layer, int column) {
        if (layer == 0) {
            return depth == 0 && column == 4
                    ? definition.getBlock()
                    : GSEProcessorPatterns.industrialSteamCasing();
        }
        if (layer == 8) {
            return GSEProcessorPatterns.industrialSteamCasing();
        }
        boolean boundary = depth == 0 || depth == 8 || column == 0 || column == 8;
        if (boundary) {
            if (layer == 1 && depth == 0) {
                return switch (column) {
                    case 2 -> GTMachines.STEAM_IMPORT_BUS.getBlock();
                    case 3 -> GTMachines.STEAM_EXPORT_BUS.getBlock();
                    case 4 -> GSEMachines.STEAM_SUPPLY_HATCH.getBlock();
                    case 5 -> GTMachines.FLUID_IMPORT_HATCH[1].getBlock();
                    case 6 -> GSEMachines.STEAM_EXHAUST_HATCH.getBlock();
                    default -> expectedLargeSteamAssemblerShell(depth, column);
                };
            }
            return expectedLargeSteamAssemblerShell(depth, column);
        }
        boolean workstationLayer = layer == 1 || layer == 7;
        boolean workstationColumn = column == 2 || column == 4 || column == 6;
        boolean workstationDepth = depth == 2 || depth == 4 || depth == 6;
        return workstationLayer && workstationColumn && workstationDepth
                ? GSEBlocks.STEAM_ASSEMBLY_BLOCK.get()
                : Blocks.AIR;
    }

    private static Block expectedLargeSteamAssemblerShell(int depth, int column) {
        boolean edge = (depth == 0 || depth == 8) && (column == 0 || column == 8);
        return edge ? GSEProcessorPatterns.industrialSteamCasing()
                : GSEProcessorPatterns.bronzeSteamCasing();
    }

    @GameTest(template = "empty_32x32x32", timeoutTicks = 200)
    public static void largeSteamCircuitAssemblerFormsFromShape(GameTestHelper helper) {
        var definition = GSEMachines.LARGE_STEAM_CIRCUIT_ASSEMBLER;
        List<MultiblockShapeInfo> shapes = definition.getMatchingShapes();
        helper.assertTrue(!shapes.isEmpty(), "Large steam circuit assembler has no preview shape");
        if (shapes.isEmpty()) {
            return;
        }

        BlockInfo[][][] blocks = shapes.get(0).getBlocks();
        helper.assertTrue(blocks.length == 5, "Large steam circuit assembler width is not 5");
        int bronzeCasings = 0;
        int industrial = 0;
        int circuitBlocks = 0;
        int gearboxes = 0;
        int assemblyBlocks = 0;
        int pipeCasings = 0;
        int air = 0;
        int interfaces = 0;
        int controllers = 0;
        for (int column = 0; column < blocks.length; column++) {
            helper.assertTrue(blocks[column].length == 6,
                    "Large steam circuit assembler height is not 6 at column " + column);
            for (int layer = 0; layer < blocks[column].length; layer++) {
                helper.assertTrue(blocks[column][layer].length == 11,
                        "Large steam circuit assembler depth is not 11 at column/layer "
                                + column + "/" + layer);
                for (int depth = 0; depth < blocks[column][layer].length; depth++) {
                    BlockState state = blocks[column][layer][depth].getBlockState();
                    Block expected = expectedLargeSteamCircuitAssemblerBlock(
                            definition, depth, layer, column);
                    helper.assertTrue(expected == Blocks.AIR ? state.isAir() : state.is(expected),
                            "Large steam circuit assembler shape differs at depth/layer/column "
                                    + depth + "/" + layer + "/" + column
                                    + ": expected=" + expected + ", actual=" + state.getBlock());

                    if (state.is(GSEProcessorPatterns.bronzeSteamCasing())) bronzeCasings++;
                    else if (state.is(GSEProcessorPatterns.industrialSteamCasing())) industrial++;
                    else if (state.is(GSEBlocks.STEAM_CIRCUIT_ASSEMBLY_BLOCK.get())) circuitBlocks++;
                    else if (state.is(GTBlocks.CASING_BRONZE_GEARBOX.get())) gearboxes++;
                    else if (state.is(GSEBlocks.STEAM_ASSEMBLY_BLOCK.get())) assemblyBlocks++;
                    else if (state.is(GSEProcessorPatterns.bronzePipeCasing())) pipeCasings++;
                    else if (state.isAir()) air++;
                    else if (state.is(definition.getBlock())) controllers++;
                    else interfaces++;
                }
            }
        }
        helper.assertTrue(bronzeCasings == 161,
                "Expected 161 bronze steam casings after five representative interfaces, found "
                        + bronzeCasings);
        helper.assertTrue(industrial == 11, "Expected 11 industrial ridge casings, found " + industrial);
        helper.assertTrue(circuitBlocks == 9, "Expected 9 circuit assembly blocks, found " + circuitBlocks);
        helper.assertTrue(gearboxes == 9, "Expected 9 bronze gearboxes, found " + gearboxes);
        helper.assertTrue(assemblyBlocks == 9, "Expected 9 assembly blocks, found " + assemblyBlocks);
        helper.assertTrue(pipeCasings == 9, "Expected 9 bronze pipe casings, found " + pipeCasings);
        helper.assertTrue(air == 116, "Expected 116 strict-air cells, found " + air);
        helper.assertTrue(interfaces == 5, "Expected five representative interfaces, found " + interfaces);
        helper.assertTrue(controllers == 1, "Expected one circuit assembler controller, found " + controllers);

        MultiblockControllerMachine machine = GSEStructureTestUtils.placeShape(
                helper, definition, shapes.get(0));
        helper.assertTrue(machine != null, "Large steam circuit assembler fixture has no controller");
        if (machine == null) {
            return;
        }
        helper.assertTrue(machine.checkPattern(),
                "Exact large steam circuit assembler shape did not match its pattern");
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(machine.isFormed(),
                        "Exact large steam circuit assembler did not become formed"))
                .thenSucceed();
    }

    @GameTest(template = "empty_32x32x32", timeoutTicks = 300)
    public static void largeSteamCircuitAssemblerRestrictsHatchesToShell(GameTestHelper helper) {
        var definition = GSEMachines.LARGE_STEAM_CIRCUIT_ASSEMBLER;
        MultiblockControllerMachine machine = GSEStructureTestUtils.placeShape(
                helper, definition, definition.getMatchingShapes().get(0));
        helper.assertTrue(machine != null, "Missing large steam circuit assembler fixture controller");
        if (machine == null) {
            return;
        }

        BlockPos controller = machine.getPos();
        BlockPos wall = controller.offset(-1, 2, 0);
        BlockPos bottom = controller.offset(-1, 0, 4);
        BlockPos ridge = controller.offset(0, 5, 4);
        BlockPos processTower = controller.offset(0, 1, 4);
        BlockState wallState = helper.getLevel().getBlockState(wall);
        BlockState bottomState = helper.getLevel().getBlockState(bottom);
        BlockState ridgeState = helper.getLevel().getBlockState(ridge);
        BlockState processTowerState = helper.getLevel().getBlockState(processTower);
        BlockState hatchState = GSEMachines.STEAM_SUPPLY_HATCH.getBlock().defaultBlockState();

        helper.assertTrue(wallState.is(GSEProcessorPatterns.bronzeSteamCasing()),
                "Circuit assembler wall probe is not a bronze steam casing");
        helper.assertTrue(bottomState.is(GSEProcessorPatterns.bronzeSteamCasing()),
                "Circuit assembler bottom probe is not a bronze steam casing");
        helper.assertTrue(ridgeState.is(GSEProcessorPatterns.industrialSteamCasing()),
                "Circuit assembler ridge probe is not an industrial steam casing");
        helper.assertTrue(processTowerState.is(GSEBlocks.STEAM_CIRCUIT_ASSEMBLY_BLOCK.get()),
                "Circuit assembler process-tower probe is not a circuit assembly block");

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(machine.isFormed(),
                        "Baseline large steam circuit assembler fixture did not form"))
                .thenExecute(() -> {
                    machine.onStructureInvalid();

                    helper.getLevel().setBlockAndUpdate(wall, hatchState);
                    helper.assertTrue(machine.checkPattern(),
                            "Large steam circuit assembler rejected a supply hatch on its wall");
                    helper.getLevel().setBlockAndUpdate(wall, wallState);
                    helper.assertTrue(machine.checkPattern(),
                            "Large steam circuit assembler did not recover its wall casing");

                    helper.getLevel().setBlockAndUpdate(bottom, hatchState);
                    helper.assertTrue(machine.checkPattern(),
                            "Large steam circuit assembler rejected a supply hatch on its bottom face");
                    helper.getLevel().setBlockAndUpdate(bottom, bottomState);
                    helper.assertTrue(machine.checkPattern(),
                            "Large steam circuit assembler did not recover its bottom casing");

                    helper.getLevel().setBlockAndUpdate(ridge, hatchState);
                    helper.assertTrue(!machine.checkPattern(),
                            "Large steam circuit assembler accepted a hatch on its industrial ridge");
                    helper.getLevel().setBlockAndUpdate(ridge, ridgeState);
                    helper.assertTrue(machine.checkPattern(),
                            "Large steam circuit assembler did not recover its ridge casing");

                    helper.getLevel().setBlockAndUpdate(processTower, hatchState);
                    helper.assertTrue(!machine.checkPattern(),
                            "Large steam circuit assembler accepted a hatch in its process tower");
                    helper.getLevel().setBlockAndUpdate(processTower, processTowerState);
                    helper.assertTrue(machine.checkPattern(),
                            "Large steam circuit assembler did not recover its process tower");

                    machine.onStructureFormed();
                    helper.assertTrue(machine.isFormed(),
                            "Large steam circuit assembler remained invalid after restoring its structure");
                })
                .thenSucceed();
    }

    @GameTest(template = "empty_32x32x32", timeoutTicks = 300)
    public static void largeSteamCircuitAssemblerRequiresExactlyOneExhaust(GameTestHelper helper) {
        var definition = GSEMachines.LARGE_STEAM_CIRCUIT_ASSEMBLER;
        MultiblockControllerMachine machine = GSEStructureTestUtils.placeShape(
                helper, definition, definition.getMatchingShapes().get(0));
        helper.assertTrue(machine != null, "Missing large steam circuit assembler fixture controller");
        if (machine == null) {
            return;
        }

        List<BlockPos> exhausts = new java.util.ArrayList<>();
        List<BlockPos> shellCasings = new java.util.ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(
                machine.getPos().offset(-2, 0, 0), machine.getPos().offset(2, 5, 10))) {
            BlockState state = helper.getLevel().getBlockState(pos);
            if (state.is(GSEMachines.STEAM_EXHAUST_HATCH.getBlock())) {
                exhausts.add(pos.immutable());
            } else if (state.is(GSEProcessorPatterns.bronzeSteamCasing())) {
                shellCasings.add(pos.immutable());
            }
        }
        helper.assertTrue(exhausts.size() == 1,
                "Circuit assembler preview exhaust count changed: " + exhausts.size());
        helper.assertTrue(!shellCasings.isEmpty(),
                "Circuit assembler preview has no shell casing mutation position");

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(machine.isFormed(),
                        "Baseline large steam circuit assembler fixture did not form"))
                .thenExecute(() -> {
                    machine.onStructureInvalid();
                    BlockPos exhaust = exhausts.get(0);
                    BlockState exhaustState = helper.getLevel().getBlockState(exhaust);
                    BlockState casingState = GSEProcessorPatterns.bronzeSteamCasing().defaultBlockState();

                    helper.getLevel().setBlockAndUpdate(exhaust, casingState);
                    helper.assertTrue(!machine.checkPattern(),
                            "Large steam circuit assembler formed without an exhaust hatch");
                    helper.getLevel().setBlockAndUpdate(exhaust, exhaustState);
                    helper.assertTrue(machine.checkPattern(),
                            "Large steam circuit assembler did not recover its exhaust hatch");

                    BlockPos duplicate = shellCasings.get(0);
                    BlockState duplicateOriginal = helper.getLevel().getBlockState(duplicate);
                    helper.getLevel().setBlockAndUpdate(duplicate, exhaustState);
                    helper.assertTrue(!machine.checkPattern(),
                            "Large steam circuit assembler formed with two exhaust hatches");
                    helper.getLevel().setBlockAndUpdate(duplicate, duplicateOriginal);
                    helper.assertTrue(machine.checkPattern(),
                            "Large steam circuit assembler did not recover after removing its duplicate exhaust");

                    machine.onStructureFormed();
                    helper.assertTrue(machine.isFormed(),
                            "Large steam circuit assembler remained invalid with exactly one exhaust hatch");
                })
                .thenSucceed();
    }

    @GameTest(template = "empty_32x32x32", timeoutTicks = 300)
    public static void backToBackLargeSteamCircuitAssemblersShareRearWall(GameTestHelper helper) {
        var definition = GSEMachines.LARGE_STEAM_CIRCUIT_ASSEMBLER;
        var shape = definition.getMatchingShapes().get(0);
        MultiblockControllerMachine north = GSEStructureTestUtils.placeShape(
                helper, definition, shape, new BlockPos(8, 16, 4), Direction.NORTH);
        MultiblockControllerMachine south = GSEStructureTestUtils.placeShape(
                helper, definition, shape, new BlockPos(8, 16, 24), Direction.SOUTH);
        helper.assertTrue(north != null && south != null,
                "Missing back-to-back large steam circuit assembler fixture controller");
        if (north == null || south == null) {
            return;
        }

        // Opposite-facing structures meet at their plain rear faces (z=14),
        // keeping both five-interface front rows independent.
        for (int y = 16; y <= 20; y++) {
            for (int x = 6; x <= 10; x++) {
                helper.assertTrue(helper.getBlockState(new BlockPos(x, y, 14))
                                .is(GSEProcessorPatterns.bronzeSteamCasing()),
                        "Shared circuit assembler rear wall differs at x/y=" + x + "/" + y);
            }
        }
        for (int x = 6; x <= 10; x++) {
            BlockState state = helper.getBlockState(new BlockPos(x, 21, 14));
            helper.assertTrue(x == 8
                            ? state.is(GSEProcessorPatterns.industrialSteamCasing())
                            : state.isAir(),
                    "Shared circuit assembler roof edge differs at x=" + x);
        }

        helper.assertTrue(north.checkPattern(),
                "North-facing circuit assembler rejected the shared rear wall");
        helper.assertTrue(south.checkPattern(),
                "South-facing circuit assembler rejected the shared rear wall");
        helper.startSequence()
                .thenWaitUntil(() -> {
                    helper.assertTrue(north.isFormed(),
                            "North-facing circuit assembler did not remain formed");
                    helper.assertTrue(south.isFormed(),
                            "South-facing circuit assembler did not remain formed");
                })
                .thenSucceed();
    }

    private static Block expectedLargeSteamCircuitAssemblerBlock(
            MultiblockMachineDefinition definition, int depth, int layer, int column) {
        if (layer == 0) {
            return depth == 0 && column == 2
                    ? definition.getBlock()
                    : GSEProcessorPatterns.bronzeSteamCasing();
        }
        if (layer == 5) {
            return column == 2 ? GSEProcessorPatterns.industrialSteamCasing() : Blocks.AIR;
        }
        boolean boundary = depth == 0 || depth == 10 || column == 0 || column == 4;
        if (boundary) {
            if (layer == 1 && depth == 0) {
                return switch (column) {
                    case 0 -> GTMachines.STEAM_IMPORT_BUS.getBlock();
                    case 1 -> GTMachines.STEAM_EXPORT_BUS.getBlock();
                    case 2 -> GSEMachines.STEAM_SUPPLY_HATCH.getBlock();
                    case 3 -> GTMachines.FLUID_IMPORT_HATCH[1].getBlock();
                    case 4 -> GSEMachines.STEAM_EXHAUST_HATCH.getBlock();
                    default -> throw new IllegalStateException("Unexpected circuit assembler column " + column);
                };
            }
            return GSEProcessorPatterns.bronzeSteamCasing();
        }
        if (column != 2) {
            return Blocks.AIR;
        }
        return switch (layer) {
            case 1 -> GSEBlocks.STEAM_CIRCUIT_ASSEMBLY_BLOCK.get();
            case 2 -> GTBlocks.CASING_BRONZE_GEARBOX.get();
            case 3 -> GSEBlocks.STEAM_ASSEMBLY_BLOCK.get();
            case 4 -> GSEProcessorPatterns.bronzePipeCasing();
            default -> throw new IllegalStateException("Unexpected circuit assembler layer " + layer);
        };
    }

    @GameTest(template = "empty_32x32x32", timeoutTicks = 300)
    public static void largeSteamOrePlantFormsFromShape(GameTestHelper helper) {
        GSEStructureTestUtils.assertFirstShapeForms(helper, GSEMachines.LARGE_STEAM_ORE_PLANT);
    }

    @GameTest(template = "empty_32x32x32", timeoutTicks = 300)
    public static void largeSteamFluidDrillFormsFromShape(GameTestHelper helper) {
        GSEStructureTestUtils.assertFirstShapeForms(helper, GSEMachines.LARGE_STEAM_FLUID_DRILL);
    }

    @GameTest(template = "empty_32x32x32", timeoutTicks = 300)
    public static void largeHeatStorageSteamFurnaceFormsFromShape(GameTestHelper helper) {
        GSEStructureTestUtils.assertFirstShapeForms(helper, GSEMachines.LARGE_HEAT_STORAGE_STEAM_FURNACE);
    }

    @GameTest(template = "empty_32x32x32", timeoutTicks = 300)
    public static void largeCokeOvenFormsFromShape(GameTestHelper helper) {
        GSEStructureTestUtils.assertFirstShapeForms(helper, GSEMachines.LARGE_COKE_OVEN);
    }

    @GameTest(template = "empty_32x32x32", timeoutTicks = 300)
    public static void boilerRoomBronzeFormsFromShape(GameTestHelper helper) {
        GSEStructureTestUtils.assertFirstShapeForms(helper, GSEMachines.BOILER_ROOM_BRONZE);
    }

    private static <T extends MetaMachine> T placeHatch(GameTestHelper helper, MachineDefinition definition,
                                                        BlockPos pos) {
        helper.setBlock(pos, definition.defaultBlockState());
        MetaMachine machine = MetaMachine.getMachine(helper.getLevel(), helper.absolutePos(pos));
        helper.assertTrue(machine != null && machine.getDefinition() == definition,
                "Placed block did not create the expected hatch machine");
        // The generic cast is safe: every caller passes the matching definition.
        @SuppressWarnings("unchecked")
        T typed = (T) machine;
        return typed;
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
