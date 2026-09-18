package com.hoshino.gregsteamexpansion.gametest;

import com.hoshino.gregsteamexpansion.GregSteamExpansion;
import com.hoshino.gregsteamexpansion.blockentity.CraftingStationBlockEntity;
import com.hoshino.gregsteamexpansion.cokeoven.CokeOvenMode;
import com.hoshino.gregsteamexpansion.cokeoven.CokeOvenWorldData;
import com.hoshino.gregsteamexpansion.machine.CoFiringPowderFuel;
import com.hoshino.gregsteamexpansion.machine.multiblock.part.LargeCokeOvenHatchPartMachine;
import com.hoshino.gregsteamexpansion.machine.steam.MixedFuelBoilerMachine;
import com.hoshino.gregsteamexpansion.registry.GSEBlocks;
import com.hoshino.gregsteamexpansion.registry.GSEMachines;
import com.hoshino.gregsteamexpansion.registry.GSERecipeTypes;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.data.chemical.ChemicalHelper;
import com.gregtechceu.gtceu.api.data.tag.TagPrefix;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gregtechceu.gtceu.common.data.GTMaterials;
import com.gregtechceu.gtceu.common.data.GTMachines;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.gregtechceu.gtceu.data.recipe.CustomTags;
import com.hoshino.gregsteamexpansion.machine.multiblock.processor.AbstractSteamAssemblerMachine;
import com.hoshino.gregsteamexpansion.menu.CraftingStationMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Item;
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
    public static void craftingStationMatchesGridAndVirtualToolViews(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, GSEBlocks.CRAFTING_STATION.get().defaultBlockState());
        BlockEntity blockEntity = helper.getLevel().getBlockEntity(helper.absolutePos(pos));
        helper.assertTrue(blockEntity instanceof CraftingStationBlockEntity,
                "Crafting station block did not create its block entity");
        CraftingStationBlockEntity station = (CraftingStationBlockEntity) blockEntity;

        Item saw = ForgeRegistries.ITEMS.tags().getTag(CustomTags.CRAFTING_SAWS).stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No registered crafting saw"));
        ItemStack sawStack = new ItemStack(saw);
        var player = helper.makeMockSurvivalPlayer();
        BlockPos absolutePos = helper.absolutePos(pos);
        player.setPos(absolutePos.getX() + 0.5, absolutePos.getY() + 0.5, absolutePos.getZ() + 0.5);
        station.tryStartViewing(player);
        CraftingStationMenu menu = new CraftingStationMenu(1, player.getInventory(), station);

        station.getGrid().setStackInSlot(3, new ItemStack(GSEBlocks.CRAFTING_STATION_ITEM.get()));
        station.getTools().setStackInSlot(0, sawStack.copy());
        menu.broadcastChanges();
        helper.assertTrue(menu.getSlot(CraftingStationMenu.RESULT_SLOT).getItem()
                .is(GSEBlocks.CRAFTING_STATION_SLAB_ITEM.get()),
                "Tool-slot substitution did not match the crafting-station slab recipe");
        helper.assertTrue(station.getGrid().getStackInSlot(0).isEmpty(),
                "Virtual tool matching mutated the real crafting grid");

        station.getTools().setStackInSlot(0, ItemStack.EMPTY);
        station.getGrid().setStackInSlot(0, sawStack.copy());
        menu.broadcastChanges();
        helper.assertTrue(menu.getSlot(CraftingStationMenu.RESULT_SLOT).getItem()
                .is(GSEBlocks.CRAFTING_STATION_SLAB_ITEM.get()),
                "Direct grid view did not match the crafting-station slab recipe");

        menu.removed(player);
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
        helper.assertTrue(CoFiringPowderFuel.burnTime(ChemicalHelper.get(TagPrefix.dust, GTMaterials.Coal)) == 1600,
                "Coal dust burn time changed");
        helper.assertTrue(CoFiringPowderFuel.burnTime(ChemicalHelper.get(TagPrefix.dust, GTMaterials.Charcoal)) == 1600,
                "Charcoal dust burn time changed");
        helper.assertTrue(CoFiringPowderFuel.burnTime(ChemicalHelper.get(TagPrefix.dust, GTMaterials.Coke)) == 3200,
                "Coke dust burn time changed");
        helper.assertTrue(CoFiringPowderFuel.burnTime(ChemicalHelper.get(TagPrefix.dust, GTMaterials.Wood)) == 300,
                "Wood dust burn time changed");
        helper.assertTrue(CoFiringPowderFuel.burnTime(ChemicalHelper.get(TagPrefix.dustSmall, GTMaterials.Coal)) == 400,
                "Small coal dust burn time changed");
        helper.assertTrue(CoFiringPowderFuel.burnTime(ChemicalHelper.get(TagPrefix.dustTiny, GTMaterials.Coal)) == 177,
                "Tiny coal dust burn time changed");
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

        ItemStack rawCopper = ChemicalHelper.get(TagPrefix.rawOre, GTMaterials.Copper);
        helper.assertTrue(!rawCopper.isEmpty(), "GTCEu exposes no representative raw copper item");
        helper.assertTrue(migrated.stream().anyMatch(recipe -> recipeAcceptsItem(recipe, rawCopper)),
                "Raw copper recipe was not migrated into ore crushing");
        helper.assertTrue(manager.getAllRecipesFor(GTRecipeTypes.MACERATOR_RECIPES).stream()
                        .noneMatch(recipe -> recipeAcceptsItem(recipe, rawCopper)),
                "Raw copper recipe incorrectly remains in the macerator");

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

    private static boolean recipeAcceptsItem(GTRecipe recipe, ItemStack stack) {
        for (var content : recipe.inputs.getOrDefault(ItemRecipeCapability.CAP, List.of())) {
            var ingredient = ItemRecipeCapability.CAP.of(content.content);
            if (ingredient == null) continue;
            for (ItemStack candidate : ingredient.getItems()) {
                if (ItemStack.isSameItemSameTags(candidate, stack)) return true;
            }
        }
        return false;
    }

    private static final BlockPos HATCH_POS = new BlockPos(0, 0, 0);
    private static final BlockPos HATCH_POS_EAST = new BlockPos(1, 0, 0);

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void largeCokeOvenHatchUsesModeAwareImplementation(GameTestHelper helper) {
        MetaMachine machine = GSEStructureTestUtils.placeMachine(helper, GSEMachines.LARGE_COKE_OVEN_HATCH, HATCH_POS);
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
        AbstractSteamAssemblerMachine assembler = GSEStructureTestUtils.placeMachine(
                helper, GSEMachines.LARGE_STEAM_ASSEMBLER, HATCH_POS);
        AbstractSteamAssemblerMachine circuitAssembler = GSEStructureTestUtils.placeMachine(
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
}
