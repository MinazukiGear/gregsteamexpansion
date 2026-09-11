package com.hoshino.gregsteamexpansion.gametest;

import com.hoshino.gregsteamexpansion.GregSteamExpansion;
import com.hoshino.gregsteamexpansion.cokeoven.CokeOvenMode;
import com.hoshino.gregsteamexpansion.cokeoven.CokeOvenWorldData;
import com.hoshino.gregsteamexpansion.machine.multiblock.part.LargeCokeOvenHatchPartMachine;
import com.hoshino.gregsteamexpansion.machine.steam.MixedFuelBoilerMachine;
import com.hoshino.gregsteamexpansion.registry.GSEMachines;
import com.hoshino.gregsteamexpansion.registry.GSERecipeTypes;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.data.chemical.ChemicalHelper;
import com.gregtechceu.gtceu.api.data.tag.TagPrefix;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gregtechceu.gtceu.common.data.GTMaterials;
import com.gregtechceu.gtceu.common.data.GTMachines;
import com.gregtechceu.gtceu.common.data.GTRecipes;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;

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
        int filled = boiler.steamTank.fillInternal(GTMaterials.Steam.getFluid(16_000), FluidAction.EXECUTE);
        helper.assertTrue(filled == 16_000, "Could not fill the steam tank before the venting test");

        helper.succeedWhen(() -> {
            helper.assertTrue(boiler.getCurrentTemperature() >= 100,
                    "High-pressure boiler had not reached steam-production temperature");
            int storedSteam = boiler.steamTank.getFluidInTank(0).getAmount();
            helper.assertTrue(storedSteam < 16_000,
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

            helper.assertTrue(output.swapIO(), "Steam fluid output hatch refused the swap back");
            MetaMachine back = MetaMachine.getMachine(helper.getLevel(), helper.absolutePos(HATCH_POS));
            helper.assertTrue(back instanceof SteamFluidHatchPartMachine restored &&
                    restored.getDefinition() == GSEMachines.STEAM_FLUID_IMPORT_HATCH &&
                    restored.tank.getFluidInTank(0).getAmount() == 5_000,
                    "Swapping back did not restore the input hatch with its content");
        }
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
        GSEStructureTestUtils.assertFirstShapeForms(helper, GSEMachines.STEAM_CRUSHER);
    }

    @GameTest(template = "empty_32x32x32", timeoutTicks = 200)
    public static void largeSteamCrusherFormsFromShape(GameTestHelper helper) {
        GSEStructureTestUtils.assertFirstShapeForms(helper, GSEMachines.LARGE_STEAM_CRUSHER);
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

    @GameTest(template = "empty_32x32x32", timeoutTicks = 200)
    public static void largeSteamAssemblerFormsFromShape(GameTestHelper helper) {
        GSEStructureTestUtils.assertFirstShapeForms(helper, GSEMachines.LARGE_STEAM_ASSEMBLER);
    }

    @GameTest(template = "empty_32x32x32", timeoutTicks = 200)
    public static void largeSteamCircuitAssemblerFormsFromShape(GameTestHelper helper) {
        GSEStructureTestUtils.assertFirstShapeForms(helper, GSEMachines.LARGE_STEAM_CIRCUIT_ASSEMBLER);
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

}
