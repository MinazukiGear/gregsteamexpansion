package com.hoshino.gregsteamexpansion.gametest;

import com.hoshino.gregsteamexpansion.GregSteamExpansion;
import com.hoshino.gregsteamexpansion.machine.steam.MixedFuelBoilerMachine;
import com.hoshino.gregsteamexpansion.registry.GSEMachines;
import com.hoshino.gregsteamexpansion.registry.GSERecipeTypes;

import com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.data.chemical.ChemicalHelper;
import com.gregtechceu.gtceu.api.data.tag.TagPrefix;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gregtechceu.gtceu.common.data.GTMaterials;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.registries.ForgeRegistries;

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

}
