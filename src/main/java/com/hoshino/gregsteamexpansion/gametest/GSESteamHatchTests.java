package com.hoshino.gregsteamexpansion.gametest;

import com.hoshino.gregsteamexpansion.GregSteamExpansion;
import com.hoshino.gregsteamexpansion.machine.multiblock.LargeSteamOverclock;
import com.hoshino.gregsteamexpansion.machine.multiblock.SteamBudget;
import com.hoshino.gregsteamexpansion.machine.multiblock.SteamThrottle;
import com.hoshino.gregsteamexpansion.machine.multiblock.furnace.FurnaceSteamCapability;
import com.hoshino.gregsteamexpansion.machine.multiblock.furnace.FurnaceSteamSourceSpec;
import com.hoshino.gregsteamexpansion.machine.multiblock.part.LargeSteamSupplyHatchPartMachine;
import com.hoshino.gregsteamexpansion.machine.multiblock.part.AdvancedSteamExhaustHatchMachine;
import com.hoshino.gregsteamexpansion.machine.multiblock.part.SteamAirIntakeHatchPartMachine;
import com.hoshino.gregsteamexpansion.machine.multiblock.part.SteamFluidHatchPartMachine;
import com.hoshino.gregsteamexpansion.machine.multiblock.part.SteamSupplyHatchPartMachine;
import com.hoshino.gregsteamexpansion.registry.GSEMachines;
import com.hoshino.gregsteamexpansion.registry.GSEPartAbilities;
import com.hoshino.gregsteamexpansion.steamcompat.LegacySteamHatchCompat;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;
import com.gregtechceu.gtceu.common.cover.ShutterCover;
import com.gregtechceu.gtceu.common.data.GTCovers;
import com.gregtechceu.gtceu.common.data.GTItems;
import com.gregtechceu.gtceu.common.data.GTMaterials;
import com.gregtechceu.gtceu.common.data.GTMachines;
import com.gregtechceu.gtceu.common.data.GTRecipes;
import com.gregtechceu.gtceu.common.machine.multiblock.part.FluidHatchPartMachine;
import com.gregtechceu.gtceu.data.recipe.CustomTags;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.Identifiers;
import snownee.jade.api.callback.JadeTooltipCollectedCallback;

@GameTestHolder(GregSteamExpansion.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GSESteamHatchTests {
    private static final BlockPos HATCH_POS = new BlockPos(0, 0, 0);
    private static final BlockPos HATCH_POS_EAST = new BlockPos(1, 0, 0);

    private GSESteamHatchTests() {}

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
        SteamSupplyHatchPartMachine hatch = GSEStructureTestUtils.placeMachine(helper, GSEMachines.STEAM_SUPPLY_HATCH, HATCH_POS);

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
        LargeSteamSupplyHatchPartMachine hatch = GSEStructureTestUtils.placeMachine(
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
    public static void advancedSteamExhaustHatchDiscountAndRecipe(GameTestHelper helper) {
        AdvancedSteamExhaustHatchMachine hatch = GSEStructureTestUtils.placeMachine(
                helper, GSEMachines.ADVANCED_STEAM_EXHAUST_HATCH, HATCH_POS);
        helper.assertTrue(hatch.modifySteamConsumption(100) == 67,
                "Advanced exhaust hatch did not reduce steam demand by 33%");
        helper.assertTrue(hatch.modifySteamConsumption(1) == 1,
                "Advanced exhaust hatch rounded a positive steam demand down to zero");
        helper.assertTrue(hatch.modifySteamConsumption(801) == 537,
                "Advanced exhaust hatch did not round fractional mB/t demand up");

        var recipe = helper.getLevel().getRecipeManager()
                .byKey(GregSteamExpansion.id("shaped/advanced_steam_exhaust_hatch"))
                .orElse(null);
        helper.assertTrue(recipe != null, "Advanced steam exhaust hatch recipe is missing");
        if (recipe != null) {
            helper.assertTrue(recipe.getIngredients().stream()
                            .anyMatch(ingredient -> ingredient.test(GSEMachines.STEAM_EXHAUST_HATCH.asStack())),
                    "Advanced exhaust recipe does not upgrade the ordinary exhaust hatch");
            var hvCircuitIterator = ForgeRegistries.ITEMS.tags().getTag(CustomTags.HV_CIRCUITS).iterator();
            helper.assertTrue(hvCircuitIterator.hasNext(), "HV circuit tag is empty");
            if (hvCircuitIterator.hasNext()) {
                Item hvCircuit = hvCircuitIterator.next();
                long circuitSlots = recipe.getIngredients().stream()
                        .filter(ingredient -> ingredient.test(new ItemStack(hvCircuit)))
                        .count();
                helper.assertTrue(circuitSlots == 4,
                        "Advanced exhaust recipe does not require four arbitrary HV circuits");
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

        SteamThrottle.LockedEconomics quarter = SteamThrottle.lock(240, 19_200, 25);
        helper.assertTrue(quarter.throttlePercent() == 25
                        && quarter.durationTicks() == 960
                        && quarter.steamPerTickMb() == 4_800,
                "25% steam throttle did not quarter demand and quadruple duration");
        helper.assertTrue(quarter.totalSteamMb() == 4_608_000,
                "Steam throttle changed the batch's nominal total consumption");
        SteamThrottle.LockedEconomics rounded = SteamThrottle.lock(3, 7, 50);
        helper.assertTrue(rounded.durationTicks() == 6
                        && rounded.steamPerTickMb() == 4
                        && rounded.steamForProgress(5) == 1,
                "Steam throttle did not preserve an indivisible total on its final tick");
        helper.assertTrue(SteamThrottle.step(100, false) == 75
                        && SteamThrottle.step(25, false) == 25
                        && SteamThrottle.step(75, true) == 100,
                "Steam throttle did not use the four 25% controller levels");
        helper.assertTrue(SteamThrottle.scaledDuration(5, 25) == 20,
                "25% throttle did not quadruple a preheat interval");
        helper.assertTrue(SteamThrottle.largestSupportedCount(
                        96, 9_600, parallel -> 200L * parallel) == 48,
                "Startup protection did not lower parallel to the largest sustainable value");
        helper.assertTrue(SteamThrottle.largestSupportedCount(
                        4, 1_200, stations -> 3_000L * stations) == 0,
                "Startup protection admitted a minimum workload above the supply limit");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void steamFluidHatchesFollowDirectionSemantics(GameTestHelper helper) {
        // machines-and-hatches.md 实现验收 2/3: one fixed 16,000 mB tank per
        // hatch; the input hatch fills from outside and refuses extraction,
        // the output hatch drains outside and refuses external filling.
        SteamFluidHatchPartMachine input = GSEStructureTestUtils.placeMachine(helper, GSEMachines.STEAM_FLUID_IMPORT_HATCH, HATCH_POS);
        int inputFilled = input.tank.fill(GTMaterials.Water.getFluid(16_000), FluidAction.EXECUTE);
        helper.assertTrue(inputFilled == 16_000, "Steam fluid input hatch refused 16,000 mB");
        helper.assertTrue(input.tank.fill(GTMaterials.Water.getFluid(1), FluidAction.EXECUTE) == 0,
                "Steam fluid input hatch accepted more than 16,000 mB");
        helper.assertTrue(input.tank.drain(100, FluidAction.SIMULATE).isEmpty(),
                "Steam fluid input hatch exposed its content for external extraction");

        SteamFluidHatchPartMachine output = GSEStructureTestUtils.placeMachine(helper, GSEMachines.STEAM_FLUID_EXPORT_HATCH, HATCH_POS_EAST);
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
        SteamFluidHatchPartMachine input = GSEStructureTestUtils.placeMachine(helper, GSEMachines.STEAM_FLUID_IMPORT_HATCH, HATCH_POS);
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
        SteamFluidHatchPartMachine input = GSEStructureTestUtils.placeMachine(helper, GSEMachines.STEAM_FLUID_IMPORT_HATCH, HATCH_POS);
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

    @GameTest(template = "empty", timeoutTicks = 100)
    @SuppressWarnings("unchecked")
    public static void steamAirIntakeCollectsWhileUnformed(GameTestHelper helper) {
        // The intake begins collecting as soon as the placed block is loaded;
        // multiblock formation only controls which controller may consume the
        // cached air. External fluid access remains disabled.
        SteamAirIntakeHatchPartMachine intake = GSEStructureTestUtils.placeMachine(helper, GSEMachines.STEAM_AIR_INTAKE_HATCH, HATCH_POS);
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

        helper.runAfterDelay(SteamAirIntakeHatchPartMachine.COLLECT_CYCLE_TICKS + 20, () -> {
            helper.assertTrue(intake.tank.getFluidInTank(0).getAmount() ==
                            SteamAirIntakeHatchPartMachine.COLLECT_AMOUNT,
                    "Standalone steam air intake hatch did not complete one collection cycle");
            helper.assertTrue(intake.getIntakeStatus() ==
                            SteamAirIntakeHatchPartMachine.IntakeStatus.COLLECTING,
                    "Standalone steam air intake hatch did not report collecting status");

            CompoundTag serverData = new CompoundTag();
            BlockAccessor accessor = GSESteamEngineTestSupport.jadeAccessor(intake, serverData);
            Object airProvider = GSESteamEngineTestSupport.jadeProvider("AirIntakeProvider");
            ((IServerDataProvider<BlockAccessor>) airProvider).appendServerData(serverData, accessor);
            List<Component> lines = new ArrayList<>();
            Set<ResourceLocation> tags = new HashSet<>();
            ITooltip tooltip = GSESteamEngineTestSupport.jadeTooltip(lines, tags);
            ((IBlockComponentProvider) airProvider).appendTooltip(tooltip, accessor, null);
            ResourceLocation fallbackStorage = GregSteamExpansion.id("steam_air_intake_fluid_summary");
            helper.assertTrue(tags.contains(fallbackStorage),
                    "Air intake tooltip omitted its fallback storage bar");
            tags.add(Identifiers.UNIVERSAL_FLUID_STORAGE);
            Object deduplication = GSESteamEngineTestSupport.jadeProvider("TooltipDeduplication");
            ((JadeTooltipCollectedCallback) deduplication).onTooltipCollected(tooltip, accessor);
            helper.assertTrue(tags.contains(Identifiers.UNIVERSAL_FLUID_STORAGE),
                    "Air intake tooltip removed the preferred upstream fluid view");
            helper.assertTrue(!tags.contains(fallbackStorage),
                    "Air intake tooltip retained its fallback bar beside the upstream fluid view");
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

}
