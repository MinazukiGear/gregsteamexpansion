package com.hoshino.gregsteamexpansion.gametest;

import com.hoshino.gregsteamexpansion.GregSteamExpansion;
import com.hoshino.gregsteamexpansion.difficulty.Difficulty;
import com.hoshino.gregsteamexpansion.difficulty.GSEDifficultyConfig;
import com.hoshino.gregsteamexpansion.machine.multiblock.processor.AbstractSteamAssemblerMachine;
import com.hoshino.gregsteamexpansion.registry.GSEMachines;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.common.data.GTMaterials;
import com.gregtechceu.gtceu.common.data.GTMachines;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.gregtechceu.gtceu.common.machine.multiblock.part.FluidHatchPartMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.part.ItemBusPartMachine;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;

import static com.hoshino.gregsteamexpansion.gametest.GSESteamEngineTestSupport.*;

/** Runtime contracts for the large steam assembler and circuit assembler. */
@GameTestHolder(GregSteamExpansion.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GSEAssemblerTests {
    private GSEAssemblerTests() {}

    @GameTest(template = "empty_32x32x32", timeoutTicks = 300)
    public static void assemblerStateBoundaries(GameTestHelper h) {
        formed(h, GSEMachines.LARGE_STEAM_ASSEMBLER, m -> stateBoundaries(h, m));
    }

    @GameTest(template = "empty_32x32x32", timeoutTicks = 300)
    public static void circuitAssemblerStateBoundaries(GameTestHelper h) {
        formed(h, GSEMachines.LARGE_STEAM_CIRCUIT_ASSEMBLER, m -> stateBoundaries(h, m));
    }

    @GameTest(template = "empty_32x32x32", timeoutTicks = 300)
    public static void emptyAssemblerSlotRunsOnlyUlvAtSingleParallel(GameTestHelper h) {
        emptyAssemblerSlotRunsOnlyUlvAtSingleParallel(
                h, GSEMachines.LARGE_STEAM_ASSEMBLER, GTRecipeTypes.ASSEMBLER_RECIPES,
                "assembler");
    }

    @GameTest(template = "empty_32x32x32", timeoutTicks = 300)
    public static void emptyCircuitAssemblerSlotRunsOnlyUlvAtSingleParallel(GameTestHelper h) {
        emptyAssemblerSlotRunsOnlyUlvAtSingleParallel(
                h, GSEMachines.LARGE_STEAM_CIRCUIT_ASSEMBLER,
                GTRecipeTypes.CIRCUIT_ASSEMBLER_RECIPES, "circuit assembler");
    }

    @GameTest(template = "empty_32x32x32", timeoutTicks = 300)
    public static void assemblerSlotStackLocksParallelAndEconomicsLadder(GameTestHelper h) {
        assemblerSlotStackLocksParallelAndEconomicsLadder(
                h, GSEMachines.LARGE_STEAM_ASSEMBLER, GTRecipeTypes.ASSEMBLER_RECIPES,
                GTMachines.ASSEMBLER[GTValues.LV].asStack(), "assembler");
    }

    @GameTest(template = "empty_32x32x32", timeoutTicks = 300)
    public static void circuitAssemblerSlotStackLocksParallelAndEconomicsLadder(GameTestHelper h) {
        assemblerSlotStackLocksParallelAndEconomicsLadder(
                h, GSEMachines.LARGE_STEAM_CIRCUIT_ASSEMBLER,
                GTRecipeTypes.CIRCUIT_ASSEMBLER_RECIPES,
                GTMachines.CIRCUIT_ASSEMBLER[GTValues.LV].asStack(), "circuit assembler");
    }

    private static void assemblerSlotStackLocksParallelAndEconomicsLadder(
            GameTestHelper h, MultiblockMachineDefinition definition,
            GTRecipeType recipeType, ItemStack installedMachine, String machineName) {
        formed(h, definition, controller -> {
            AbstractSteamAssemblerMachine machine = (AbstractSteamAssemblerMachine) controller;
            ItemBusPartMachine input = inputBus(machine);
            fillOutputs(machine, false);

            GTRecipe lvRecipe = recipeType
                    .recipeBuilder(GregSteamExpansion.id(machineName.replace(' ', '_') + "_slot_ladder_lv"))
                    .inputItems(new ItemStack(Items.COBBLESTONE))
                    .outputItems(new ItemStack(Items.STONE))
                    .duration(20).EUt(GTValues.VA[GTValues.LV]).buildRawRecipe();
            GTRecipe mvRecipe = recipeType
                    .recipeBuilder(GregSteamExpansion.id(machineName.replace(' ', '_') + "_slot_ladder_mv"))
                    .inputItems(new ItemStack(Items.COBBLESTONE))
                    .outputItems(new ItemStack(Items.STONE))
                    .duration(20).EUt(GTValues.VA[GTValues.MV]).buildRawRecipe();
            int[] expectedParallel = { 2, 4, 8, 16 };
            int[] expectedDuration = { 45, 60, 75, 90 };
            long[] expectedSteamPerTick = { 75, 90, 105, 120 };

            for (int count = 1; count <= AbstractSteamAssemblerMachine.MAX_SLOT_STACK; count++) {
                machine.getAssemblerSlotHandler().setStackInSlot(0, installedMachine.copyWithCount(count));
                clearInventory(input);
                input.getInventory().setStackInSlot(0, new ItemStack(Items.COBBLESTONE, 32));

                h.assertTrue(!(boolean) call(machine, "tryStartRecipe", mvRecipe),
                        "LV " + machineName + " stack accepted an MV recipe at count " + count);
                eq(h, inputItemCount(input, Items.COBBLESTONE), 32,
                        "Rejected MV " + machineName + " recipe consumed input at count " + count);
                h.assertTrue(machine.getBatchRecipeId().isEmpty(),
                        "Rejected MV " + machineName + " recipe left a locked batch at count " + count);

                h.assertTrue((boolean) call(machine, "tryStartRecipe", lvRecipe),
                        "LV " + machineName + " stack rejected an LV recipe at count " + count);
                int parallel = expectedParallel[count - 1];
                eq(h, machine.getAssemblerTier(), GTValues.LV,
                        "LV " + machineName + " stack exposed the wrong tier at count " + count);
                eq(h, machine.getAssemblerCount(), count,
                        machineName + " slot reported the wrong installed count");
                eq(h, machine.maximumParallel(), parallel,
                        machineName + " slot exposed the wrong parallel cap at count " + count);
                eq(h, machine.getBatchParallel(), parallel,
                        machineName + " batch locked the wrong parallel at count " + count);
                eq(h, inputItemCount(input, Items.COBBLESTONE), 32 - parallel,
                        machineName + " batch consumed the wrong item count at count " + count);
                eq(h, machine.getBatchDuration(), expectedDuration[count - 1],
                        machineName + " batch locked the wrong duration at count " + count);
                eq(h, machine.getBatchSteamPerTick(), expectedSteamPerTick[count - 1],
                        machineName + " batch locked the wrong steam rate at count " + count);
                eq(h, number(machine, "batchTotalSteamMb"),
                        expectedDuration[count - 1] * expectedSteamPerTick[count - 1],
                        machineName + " batch locked the wrong steam total at count " + count);
                clearActiveProcessorBatch(machine);
            }
        });
    }

    @GameTest(template = "empty_32x32x32", timeoutTicks = 300)
    public static void circuitAssemblerConsumesSolderAndItemsAtomically(GameTestHelper h) {
        formed(h, GSEMachines.LARGE_STEAM_CIRCUIT_ASSEMBLER, controller -> {
            AbstractSteamAssemblerMachine machine = (AbstractSteamAssemblerMachine) controller;
            machine.getAssemblerSlotHandler().setStackInSlot(
                    0, GTMachines.CIRCUIT_ASSEMBLER[GTValues.LV].asStack());
            ItemBusPartMachine itemInput = inputBus(machine);
            List<FluidHatchPartMachine> fluidInputs = list(machine, "fluidInputHatches");
            h.assertTrue(!fluidInputs.isEmpty(), "Circuit assembler fixture lacks a fluid input hatch");
            var fluidInput = fluidInputs.get(0).tank.getStorages()[0];
            fillOutputs(machine, false);

            GTRecipe recipe = GTRecipeTypes.CIRCUIT_ASSEMBLER_RECIPES
                    .recipeBuilder(GregSteamExpansion.id("circuit_assembler_atomic_solder"))
                    .inputItems(new ItemStack(Items.COBBLESTONE))
                    .inputFluids(GTMaterials.SolderingAlloy.getFluid(144))
                    .outputItems(new ItemStack(Items.STONE))
                    .duration(20).EUt(GTValues.VA[GTValues.LV]).buildRawRecipe();

            itemInput.getInventory().setStackInSlot(0, new ItemStack(Items.COBBLESTONE, 2));
            fluidInput.setFluid(GTMaterials.SolderingAlloy.getFluid(143));
            h.assertTrue(!(boolean) call(machine, "tryStartRecipe", recipe),
                    "Circuit assembler started with less than one operation of solder");
            eq(h, inputItemCount(itemInput, Items.COBBLESTONE), 2,
                    "Rejected solder-short recipe partially consumed its item input");
            eq(h, fluidInput.getFluidAmount(), 143,
                    "Rejected solder-short recipe partially consumed its fluid input");
            h.assertTrue(machine.getBatchRecipeId().isEmpty(),
                    "Rejected solder-short recipe left a locked batch");

            fluidInput.setFluid(GTMaterials.SolderingAlloy.getFluid(144));
            h.assertTrue((boolean) call(machine, "tryStartRecipe", recipe),
                    "Circuit assembler rejected an exactly supplied solder recipe");
            eq(h, machine.getBatchParallel(), 1,
                    "One operation of solder did not limit the circuit assembler to one parallel");
            eq(h, inputItemCount(itemInput, Items.COBBLESTONE), 1,
                    "Accepted circuit recipe consumed the wrong item count");
            eq(h, fluidInput.getFluidAmount(), 0,
                    "Accepted circuit recipe did not consume exactly one operation of solder");

            float expectedMultiplier = GSEDifficultyConfig.capturedDifficulty() == Difficulty.EASY
                    ? 2.0F : 1.0F;
            h.assertTrue(((Number) get(machine, "batchOutputMultiplier")).floatValue() == expectedMultiplier,
                    "Circuit assembler locked the wrong output multiplier for the startup difficulty");
            call(machine, "completeBatch");
            eq(h, outputCount(machine, Items.STONE), Math.round(expectedMultiplier),
                    "Circuit assembler produced the wrong item count for the startup difficulty");
        });
    }

    private static void emptyAssemblerSlotRunsOnlyUlvAtSingleParallel(
            GameTestHelper h, MultiblockMachineDefinition definition,
            GTRecipeType recipeType, String machineName) {
        formed(h, definition, controller -> {
            AbstractSteamAssemblerMachine machine = (AbstractSteamAssemblerMachine) controller;
            ItemBusPartMachine input = inputBus(machine);
            fillOutputs(machine, false);

            h.assertTrue(machine.getAssemblerStack().isEmpty(),
                    machineName + " fixture controller slot was not empty");
            eq(h, machine.getAssemblerTier(), GTValues.ULV,
                    "Empty " + machineName + " slot did not expose the ULV tier ceiling");
            eq(h, machine.getAssemblerCount(), 0,
                    "Empty " + machineName + " slot reported installed machines");
            eq(h, machine.maximumParallel(), 1,
                    "Empty " + machineName + " slot did not enforce single parallel");

            GTRecipe lvRecipe = recipeType
                    .recipeBuilder(GregSteamExpansion.id("empty_" + machineName.replace(' ', '_')
                            + "_slot_lv_rejected"))
                    .inputItems(new ItemStack(Items.COBBLESTONE))
                    .outputItems(new ItemStack(Items.STONE))
                    .duration(20).EUt(GTValues.VA[GTValues.LV]).buildRawRecipe();
            GTRecipe ulvRecipe = recipeType
                    .recipeBuilder(GregSteamExpansion.id("empty_" + machineName.replace(' ', '_')
                            + "_slot_ulv_accepted"))
                    .inputItems(new ItemStack(Items.COBBLESTONE))
                    .outputItems(new ItemStack(Items.STONE))
                    .duration(20).EUt(GTValues.VA[GTValues.ULV]).buildRawRecipe();
            input.getInventory().setStackInSlot(0, new ItemStack(Items.COBBLESTONE, 2));

            h.assertTrue(!(boolean) call(machine, "tryStartRecipe", lvRecipe),
                    "Empty " + machineName + " slot accepted an LV recipe");
            eq(h, input.getInventory().getStackInSlot(0).getCount(), 2,
                    "Rejected LV recipe consumed an input");
            h.assertTrue(machine.getBatchRecipeId().isEmpty(),
                    "Rejected LV recipe left a locked batch");

            h.assertTrue((boolean) call(machine, "tryStartRecipe", ulvRecipe),
                    "Empty " + machineName + " slot rejected an ULV recipe");
            eq(h, machine.getBatchParallel(), 1,
                    "Empty " + machineName + " slot started more than one parallel");
            eq(h, input.getInventory().getStackInSlot(0).getCount(), 1,
                    "Single-parallel ULV batch consumed the wrong input count");
            eq(h, machine.getBatchDuration(), 30,
                    "Single-parallel ULV batch locked the wrong adjusted duration");
            eq(h, machine.getBatchSteamPerTick(), GTValues.VA[GTValues.ULV] * 2L,
                    "Single-parallel ULV batch locked the wrong steam demand");
        });
    }
}
