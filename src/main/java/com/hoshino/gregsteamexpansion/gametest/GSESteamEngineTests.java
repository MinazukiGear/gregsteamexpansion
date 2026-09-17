package com.hoshino.gregsteamexpansion.gametest;

import com.hoshino.gregsteamexpansion.GregSteamExpansion;
import com.hoshino.gregsteamexpansion.machine.multiblock.processor.AbstractSteamProcessorMachine;
import com.hoshino.gregsteamexpansion.registry.GSEMachines;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.common.data.GTMaterials;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.gregtechceu.gtceu.common.machine.multiblock.part.FluidHatchPartMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.part.ItemBusPartMachine;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;

import static com.hoshino.gregsteamexpansion.gametest.GSESteamEngineTestSupport.*;

/**
 * P2-7 characterization tests against real formed controllers and hatch inventories.
 * Fixtures inject a locked batch, then execute production tick/commit methods on
 * the server thread so exact failure boundaries do not depend on tick scheduling.
 * Reflection is isolated in {@link GSESteamEngineTestSupport}; P0-1 should change those adapters, not assertions.
 * No recipes are registered globally and no shared random seed/config is changed.
 */
@GameTestHolder(GregSteamExpansion.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GSESteamEngineTests {

    private static final ChunkPos PROCESSOR_RELOAD_CHUNK = new ChunkPos(256, 256);

    private GSESteamEngineTests() {}

    @GameTest(template = "empty_32x32x32", timeoutTicks = 300)
    public static void processorStateBoundaries(GameTestHelper h) {
        formed(h, GSEMachines.LARGE_STEAM_MACERATOR, m -> stateBoundaries(h, m));
    }

    @GameTest(template = "empty_32x32x32", timeoutTicks = 300)
    public static void processorPersistedStateRoundTrip(GameTestHelper h) {
        formed(h, GSEMachines.STEAM_CENTRIFUGE, m -> {
            set(m, "workingEnabled", false);
            set(m, "largeSteamOverclockEnabled", true);
            set(m, "hasBatch", true);
            set(m, "batchRecipeId", "gregsteamexpansion:persisted_processor");
            set(m, "batchParallel", 3);
            set(m, "batchProgress", 47);
            set(m, "batchDurationTicks", 211);
            set(m, "batchSteamPerTickMb", 600L);
            set(m, "batchLargeSteamOverclock", true);
            set(m, "batchTotalSteamMb", 126_600L);
            set(m, "batchOutputMultiplier", 2.0F);
            set(m, "batchInputDisplay", new ItemStack(Items.IRON_INGOT, 2));
            set(m, "preferredRecipeId", "gregsteamexpansion:preferred_processor");
            pending(m).add(new ItemStack(Items.DIAMOND, 5));
            List<FluidStack> fluids = list(m, "pendingFluids");
            fluids.add(GTMaterials.Water.getFluid(750));

            CompoundTag saved = saveState(h, m);
            clearProcessorState(m);
            loadState(h, m, saved);

            h.assertTrue(!(boolean) call(m, "isWorkingEnabled"), "Processor work-enabled state was not restored");
            h.assertTrue((boolean) call(m, "isLargeSteamOverclockEnabled"),
                    "Processor overclock preference was not restored");
            h.assertTrue((boolean) get(m, "hasBatch"), "Processor batch flag was not restored");
            h.assertTrue((boolean) call(m, "isCurrentBatchLargeSteamOverclocked"),
                    "Processor locked batch overclock was not restored");
            h.assertTrue(get(m, "batchRecipeId").equals("gregsteamexpansion:persisted_processor"),
                    "Processor recipe id was not restored");
            eq(h, number(m, "batchParallel"), 3, "Processor parallel was not restored");
            eq(h, number(m, "batchProgress"), 47, "Processor progress was not restored");
            eq(h, number(m, "batchDurationTicks"), 211, "Processor duration was not restored");
            eq(h, number(m, "batchSteamPerTickMb"), 600, "Processor steam demand was not restored");
            eq(h, number(m, "batchTotalSteamMb"), 126_600, "Processor steam total was not restored");
            h.assertTrue(((Number) get(m, "batchOutputMultiplier")).floatValue() == 2.0F,
                    "Processor output multiplier was not restored");
            ItemStack display = (ItemStack) get(m, "batchInputDisplay");
            h.assertTrue(display.is(Items.IRON_INGOT) && display.getCount() == 2,
                    "Processor input display was not restored");
            h.assertTrue(get(m, "preferredRecipeId").equals("gregsteamexpansion:preferred_processor"),
                    "Processor recipe preference was not restored");
            eq(h, count(pending(m), Items.DIAMOND), 5, "Processor pending item was not restored");
            eq(h, fluidAmount(fluids, GTMaterials.Water.getFluid(1)), 750,
                    "Processor pending fluid was not restored");
            h.assertTrue((boolean) call(get(m, "pendingBuffer"), "hasAny"),
                    "Processor pending buffer detached from restored lists");

            fillOutputs(m, false);
            h.assertTrue((boolean) call(m, "deliverPendingOutputs"),
                    "Processor could not deliver restored pending outputs");
            eq(h, outputCount(m, Items.DIAMOND), 5, "Processor duplicated or lost restored item output");
            eq(h, fluidOutputAmount(m, GTMaterials.Water.getFluid(1)), 750,
                    "Processor duplicated or lost restored fluid output");
        });
    }

    // Chunk unload is asynchronous and can exceed 600 server ticks while the
    // full GameTest suite is saturating CI; the condition remains exact.
    @GameTest(template = "empty_32x32x32", timeoutTicks = 1200)
    public static void processorStateSurvivesChunkUnloadReload(GameTestHelper h) {
        ServerLevel reloadLevel = h.getLevel().getServer().getLevel(Level.END);
        h.assertTrue(reloadLevel != null, "End level is unavailable for isolated chunk reload test");
        ChunkPos chunkPos = PROCESSOR_RELOAD_CHUNK;
        BlockPos controllerPos = new BlockPos(chunkPos.getMiddleBlockX(), 80, chunkPos.getMiddleBlockZ());

        String restartPhase = System.getenv("GSE_RESTART_TEST_PHASE");
        if (restartPhase != null && !restartPhase.isBlank()) {
            if (restartPhase.equals("seed")) {
                seedProcessorReloadFixture(h, reloadLevel, chunkPos, controllerPos);
                reloadLevel.getChunkSource().save(true);
                reloadLevel.setChunkForced(chunkPos.x, chunkPos.z, false);
                h.succeed();
                return;
            }
            if (restartPhase.equals("verify")) {
                reloadLevel.setChunkForced(chunkPos.x, chunkPos.z, true);
                try {
                    reloadLevel.getChunk(chunkPos.x, chunkPos.z);
                    MetaMachine loaded = MetaMachine.getMachine(reloadLevel, controllerPos);
                    h.assertTrue(loaded instanceof MultiblockControllerMachine,
                            "Restart fixture controller was not restored from disk");
                    assertProcessorReloadState(h, (MultiblockControllerMachine) loaded,
                            "Server restart");
                } finally {
                    cleanupProcessorReloadFixture(reloadLevel, chunkPos, controllerPos);
                }
                h.succeed();
                return;
            }
            h.assertTrue(false, "Unknown GSE_RESTART_TEST_PHASE: " + restartPhase);
            return;
        }

        // GameTest structure chunks stay forced. Use an isolated End chunk whose only persistent ticket is ours.
        MultiblockControllerMachine placed = seedProcessorReloadFixture(h, reloadLevel, chunkPos, controllerPos);
        BlockEntity blockEntity = reloadLevel.getBlockEntity(controllerPos);
        h.assertTrue(blockEntity != null, "Reload fixture has no controller block entity after seeding");
        reloadLevel.getChunkSource().save(true);
        reloadLevel.setChunkForced(chunkPos.x, chunkPos.z, false);

        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(
                        reloadLevel.getChunkSource().getChunkNow(chunkPos.x, chunkPos.z) == null
                                && blockEntity.isRemoved(),
                        "Isolated controller chunk has not completed its unload"))
                .thenExecute(() -> {
                    try {
                        reloadLevel.getChunk(chunkPos.x, chunkPos.z);
                        MetaMachine loaded = MetaMachine.getMachine(reloadLevel, controllerPos);
                        h.assertTrue(loaded instanceof MultiblockControllerMachine,
                                "Controller block entity was not recreated after chunk reload");
                        h.assertTrue(loaded != placed,
                                "Chunk reload reused the original controller instance");
                        assertProcessorReloadState(h, (MultiblockControllerMachine) loaded,
                                "Chunk reload");
                    } finally {
                        cleanupProcessorReloadFixture(reloadLevel, chunkPos, controllerPos);
                    }
                })
                .thenSucceed();
    }

    @GameTest(template = "empty_32x32x32", timeoutTicks = 300)
    public static void processorPendingAndSettlement(GameTestHelper h) {
        formed(h, GSEMachines.STEAM_COMPRESSOR, m -> settlement(h, m));
    }

    @GameTest(template = "empty_32x32x32", timeoutTicks = 300)
    public static void processorParallelCapacity(GameTestHelper h) {
        formed(h, GSEMachines.STEAM_COMPRESSOR, m -> parallelCapacity(h, m));
    }

    @GameTest(template = "empty_32x32x32", timeoutTicks = 300)
    public static void processorMultiProductCapacityCompetition(GameTestHelper h) {
        formed(h, GSEMachines.STEAM_COMPRESSOR, m -> multiProductCapacityCompetition(h, m));
    }

    @GameTest(template = "empty_32x32x32", timeoutTicks = 300)
    public static void processorConsumesInputsOnlyAtBatchStart(GameTestHelper h) {
        formed(h, GSEMachines.STEAM_COMPRESSOR, m -> {
            var input = m.getParts().stream().filter(ItemBusPartMachine.class::isInstance)
                    .map(ItemBusPartMachine.class::cast)
                    .filter(bus -> bus.getInventory().getHandlerIO() == IO.IN).findFirst().orElseThrow();
            input.getInventory().setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 9));
            var recipe = GTRecipeTypes.COMPRESSOR_RECIPES.recipeBuilder(GregSteamExpansion.id("engine_input"))
                    .inputItems(new ItemStack(Items.IRON_INGOT, 2)).outputItems(new ItemStack(Items.GOLD_INGOT))
                    .duration(200).EUt(8).buildRawRecipe();
            fillOutputs(m, true);
            h.assertTrue(!(boolean) call(m, "tryStartRecipe", recipe), "Output-blocked recipe started");
            eq(h, input.getInventory().getStackInSlot(0).getCount(), 9, "Rejected recipe consumed inputs");
            fillOutputs(m, false);
            h.assertTrue((boolean) call(m, "tryStartRecipe", recipe), "Valid input recipe did not start");
            eq(h, number(m, "batchParallel"), 4, "Input amount did not limit parallel to four");
            eq(h, input.getInventory().getStackInSlot(0).getCount(), 1, "Start did not consume exactly eight inputs");
            set(m, "batchProgress", 7);
            fillSteam(m, 0);
            tick(m);
            fillSteam(m, 32_000);
            tick(m);
            eq(h, progress(m), 2, "Retained batch did not resume after shortage");
            eq(h, input.getInventory().getStackInSlot(0).getCount(), 1, "Recovery consumed inputs a second time");
            eq(h, number(m, "batchParallel"), 4, "Recovery recalculated parallel from remaining input");
        });
    }

    @GameTest(template = "empty_32x32x32", timeoutTicks = 300)
    public static void largeSteamOverclockLocksOnlyAtProcessorBatchStart(GameTestHelper h) {
        formed(h, GSEMachines.LARGE_STEAM_MACERATOR, m -> {
            AbstractSteamProcessorMachine processor = (AbstractSteamProcessorMachine) m;
            h.assertTrue(!processor.hasLargeSteamSupplyHatch(),
                    "Ordinary preview unexpectedly contains a Large Steam Supply Hatch");
            processor.setLargeSteamOverclockEnabled(true);
            h.assertTrue(!processor.isLargeSteamOverclockEnabled(),
                    "Controller enabled large steam overclock without a Large Steam Supply Hatch");

            BlockPos supplyPos = findBlock(h, m, GSEMachines.STEAM_SUPPLY_HATCH.getBlock());
            h.assertTrue(supplyPos != null, "Large macerator fixture has no ordinary steam supply hatch");
            m.onStructureInvalid();
            h.getLevel().setBlockAndUpdate(supplyPos,
                    GSEMachines.LARGE_STEAM_SUPPLY_HATCH.getBlock().defaultBlockState());
            h.assertTrue(m.checkPattern(), "Large Steam Supply Hatch did not reform the large macerator");
            m.onStructureFormed();
            h.assertTrue(m.isFormed() && processor.hasLargeSteamSupplyHatch(),
                    "Reformed controller did not collect the Large Steam Supply Hatch");

            ItemBusPartMachine input = m.getParts().stream()
                    .filter(ItemBusPartMachine.class::isInstance)
                    .map(ItemBusPartMachine.class::cast)
                    .filter(bus -> bus.getInventory().getHandlerIO() == IO.IN)
                    .findFirst().orElseThrow();
            GTRecipe recipe = GTRecipeTypes.MACERATOR_RECIPES.recipeBuilder(
                            GregSteamExpansion.id("engine_large_steam_overclock"))
                    .inputItems(new ItemStack(Items.IRON_INGOT))
                    .outputItems(new ItemStack(Items.GOLD_INGOT))
                    .duration(20).EUt(8).buildRawRecipe();
            fillOutputs(m, false);

            processor.setLargeSteamOverclockEnabled(true);
            input.getInventory().setStackInSlot(0, new ItemStack(Items.IRON_INGOT));
            h.assertTrue((boolean) call(m, "tryStartRecipe", recipe),
                    "Overclock-enabled processor recipe did not start");
            h.assertTrue(processor.isCurrentBatchLargeSteamOverclocked(),
                    "Processor did not lock overclock at batch start");
            eq(h, number(m, "batchDurationTicks"), 15,
                    "Processor did not halve its 30-tick adjusted duration");
            eq(h, number(m, "batchSteamPerTickMb"), 48,
                    "Processor did not triple its 16 mB/t base steam demand");
            eq(h, number(m, "batchTotalSteamMb"), 720,
                    "Processor locked the wrong overclocked steam total");

            processor.setLargeSteamOverclockEnabled(false);
            h.assertTrue(processor.isCurrentBatchLargeSteamOverclocked(),
                    "Disabling the toggle rewrote the running overclocked batch");
            eq(h, number(m, "batchDurationTicks"), 15,
                    "Disabling the toggle changed the running batch duration");
            eq(h, number(m, "batchSteamPerTickMb"), 48,
                    "Disabling the toggle changed the running batch demand");
            fillSteam(m, 32_000);
            long steamBeforeTick = steam(m);
            tick(m);
            eq(h, steamBeforeTick - steam(m), 48,
                    "Running overclocked batch did not consume its locked steam demand");
            eq(h, progress(m), 1,
                    "Running overclocked batch did not advance after its locked steam draw");

            call(m, "completeBatch");
            input.getInventory().setStackInSlot(0, new ItemStack(Items.IRON_INGOT));
            h.assertTrue((boolean) call(m, "tryStartRecipe", recipe),
                    "Normal processor recipe did not start after disabling overclock");
            h.assertTrue(!processor.isCurrentBatchLargeSteamOverclocked(),
                    "Disabled overclock remained active for the next batch");
            eq(h, number(m, "batchDurationTicks"), 30,
                    "Normal processor batch kept the overclocked duration");
            eq(h, number(m, "batchSteamPerTickMb"), 16,
                    "Normal processor batch kept the overclocked steam demand");
            eq(h, number(m, "batchTotalSteamMb"), 480,
                    "Normal processor batch kept the overclocked steam total");

            processor.setLargeSteamOverclockEnabled(true);
            h.assertTrue(!processor.isCurrentBatchLargeSteamOverclocked(),
                    "Enabling the toggle rewrote a running normal batch");
            eq(h, number(m, "batchDurationTicks"), 30,
                    "Enabling the toggle changed the running normal duration");
            eq(h, number(m, "batchSteamPerTickMb"), 16,
                    "Enabling the toggle changed the running normal demand");
            fillSteam(m, 32_000);
            steamBeforeTick = steam(m);
            tick(m);
            eq(h, steamBeforeTick - steam(m), 16,
                    "Running normal batch did not retain its locked steam demand");
            eq(h, progress(m), 1,
                    "Running normal batch did not advance after its locked steam draw");
        });
    }

    @GameTest(template = "empty_32x32x32", timeoutTicks = 300)
    public static void processorFluidCapacityAndRecovery(GameTestHelper h) {
        formed(h, GSEMachines.STEAM_CENTRIFUGE, m -> {
            List<FluidHatchPartMachine> hatches = list(m, "fluidOutputHatches");
            h.assertTrue(!hatches.isEmpty(), "Fixture lacks fluid output");
            for (var hatch : hatches) {
                for (var tank : hatch.tank.getStorages()) tank.setFluid(GTMaterials.Water.getFluid(tank.getCapacity()));
            }
            var tank = hatches.get(0).tank.getStorages()[0];
            tank.setFluid(GTMaterials.Water.getFluid(tank.getCapacity() - 200));
            var recipe = GTRecipeTypes.CENTRIFUGE_RECIPES.recipeBuilder(GregSteamExpansion.id("engine_fluid"))
                    .outputFluids(GTMaterials.Water.getFluid(100)).duration(20).EUt(8).buildRawRecipe();
            eq(h, (int) call(m, "largestParallelThatFits", recipe, 3), 2, "Fluid capacity did not limit parallel");
            eq(h, tank.getFluidAmount(), tank.getCapacity() - 200, "Fluid precheck mutated storage");
            List<FluidStack> pending = list(m, "pendingFluids");
            pending.add(GTMaterials.Water.getFluid(300));
            h.assertTrue(!(boolean) call(m, "deliverPendingOutputs"), "Insufficient fluid space accepted output");
            eq(h, pending.get(0).getAmount(), 300, "Blocked delivery lost pending fluid");
            eq(h, tank.getFluidAmount(), tank.getCapacity() - 200, "Blocked fluid delivery partially committed");
            tank.setFluid(GTMaterials.Water.getFluid(tank.getCapacity() - 300));
            h.assertTrue((boolean) call(m, "deliverPendingOutputs"), "Fluid recovery failed");
            h.assertTrue(pending.isEmpty(), "Delivered fluid remained pending");
            eq(h, tank.getFluidAmount(), tank.getCapacity(), "Fluid recovery charged wrong amount");
        });
    }

    @GameTest(template = "empty_32x32x32", timeoutTicks = 300)
    public static void processorCompletesFluidRecipe(GameTestHelper h) {
        formed(h, GSEMachines.STEAM_CENTRIFUGE, m -> {
            ItemBusPartMachine itemInput = m.getParts().stream().filter(ItemBusPartMachine.class::isInstance)
                    .map(ItemBusPartMachine.class::cast)
                    .filter(bus -> bus.getInventory().getHandlerIO() == IO.IN).findFirst().orElseThrow();
            List<FluidHatchPartMachine> fluidInputs = list(m, "fluidInputHatches");
            h.assertTrue(!fluidInputs.isEmpty(), "Fixture lacks fluid input");
            var fluidInput = fluidInputs.get(0).tank.getStorages()[0];

            itemInput.getInventory().setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 2));
            fluidInput.setFluid(GTMaterials.Water.getFluid(500));
            fillOutputs(m, false);
            fillSteam(m, 32_000);
            long steamBefore = steam(m);
            GTRecipe recipe = GTRecipeTypes.CENTRIFUGE_RECIPES.recipeBuilder(
                            GregSteamExpansion.id("engine_fluid_completion"))
                    .inputItems(new ItemStack(Items.IRON_INGOT))
                    .inputFluids(GTMaterials.Water.getFluid(250))
                    .outputItems(new ItemStack(Items.GOLD_INGOT))
                    .outputFluids(GTMaterials.Oxygen.getFluid(100))
                    .duration(4).EUt(8).buildRawRecipe();

            h.assertTrue((boolean) call(m, "tryStartRecipe", recipe), "Fluid recipe did not start");
            eq(h, number(m, "batchParallel"), 2, "Fluid recipe chose the wrong parallel");
            eq(h, itemInput.getInventory().getStackInSlot(0).getCount(), 0,
                    "Fluid recipe did not consume its item input");
            eq(h, fluidInput.getFluidAmount(), 0, "Fluid recipe did not consume its fluid input");
            int duration = (int) number(m, "batchDurationTicks");
            long totalSteam = number(m, "batchTotalSteamMb");
            int expectedItems = Math.round(2 * ((Number) get(m, "batchOutputMultiplier")).floatValue());
            List<FluidHatchPartMachine> fluidOutputs = list(m, "fluidOutputHatches");
            for (FluidHatchPartMachine hatch : fluidOutputs) {
                for (var tank : hatch.tank.getStorages()) {
                    tank.setFluid(GTMaterials.Water.getFluid(tank.getCapacity()));
                }
            }
            for (int i = 0; i < duration; i++) tick(m);

            h.assertTrue(!(boolean) get(m, "hasBatch"), "Completed fluid recipe remained active");
            eq(h, steamBefore - steam(m), totalSteam, "Fluid recipe charged the wrong steam total");
            eq(h, outputCount(m, Items.GOLD_INGOT), expectedItems,
                    "Fluid recipe lost or duplicated its item output");
            List<FluidStack> pendingFluids = list(m, "pendingFluids");
            eq(h, fluidAmount(pendingFluids, GTMaterials.Oxygen.getFluid(1)), 200,
                    "Blocked fluid output was not retained exactly once");
            long steamBeforeRecovery = steam(m);
            for (FluidHatchPartMachine hatch : fluidOutputs) {
                for (var tank : hatch.tank.getStorages()) tank.setFluid(FluidStack.EMPTY);
            }
            tick(m);
            eq(h, steam(m), steamBeforeRecovery, "Pending fluid recovery consumed steam");
            eq(h, fluidOutputAmount(m, GTMaterials.Oxygen.getFluid(1)), 200,
                    "Fluid recipe did not recover its blocked fluid output");
            h.assertTrue(pending(m).isEmpty() && GSESteamEngineTestSupport.<FluidStack>list(m, "pendingFluids").isEmpty(),
                    "Completed fluid recipe left deliverable output pending");
        });
    }

    private static MultiblockControllerMachine seedProcessorReloadFixture(GameTestHelper h,
                                                                           ServerLevel level,
                                                                           ChunkPos chunkPos,
                                                                           BlockPos controllerPos) {
        level.setChunkForced(chunkPos.x, chunkPos.z, false);
        level.setChunkForced(chunkPos.x, chunkPos.z, true);
        level.getChunk(chunkPos.x, chunkPos.z);
        level.setBlockAndUpdate(controllerPos, Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(controllerPos, GSEMachines.STEAM_CENTRIFUGE.getBlock().defaultBlockState());
        MetaMachine placed = MetaMachine.getMachine(level, controllerPos);
        h.assertTrue(placed instanceof MultiblockControllerMachine,
                "Reload fixture controller did not instantiate");
        MultiblockControllerMachine controller = (MultiblockControllerMachine) placed;

        set(controller, "workingEnabled", false);
        set(controller, "largeSteamOverclockEnabled", true);
        set(controller, "hasBatch", true);
        set(controller, "batchRecipeId", "gregsteamexpansion:chunk_reload_processor");
        set(controller, "batchParallel", 3);
        set(controller, "batchProgress", 47);
        set(controller, "batchDurationTicks", 211);
        set(controller, "batchSteamPerTickMb", 600L);
        set(controller, "batchLargeSteamOverclock", true);
        set(controller, "batchTotalSteamMb", 126_600L);
        pending(controller).add(new ItemStack(Items.DIAMOND, 5));
        list(controller, "pendingFluids").add(GTMaterials.Water.getFluid(750));

        BlockEntity blockEntity = level.getBlockEntity(controllerPos);
        h.assertTrue(blockEntity != null, "Reload fixture has no controller block entity");
        blockEntity.setChanged();
        level.getChunkAt(controllerPos).setUnsaved(true);
        return controller;
    }

    private static void assertProcessorReloadState(GameTestHelper h,
                                                   MultiblockControllerMachine restored,
                                                   String context) {
        h.assertTrue(!(boolean) call(restored, "isWorkingEnabled"),
                context + " lost processor work-enabled state");
        h.assertTrue((boolean) call(restored, "isLargeSteamOverclockEnabled"),
                context + " lost processor overclock preference");
        h.assertTrue((boolean) get(restored, "hasBatch"),
                context + " lost processor batch flag");
        h.assertTrue((boolean) call(restored, "isCurrentBatchLargeSteamOverclocked"),
                context + " lost processor locked batch overclock");
        h.assertTrue(get(restored, "batchRecipeId").equals("gregsteamexpansion:chunk_reload_processor"),
                context + " lost processor recipe id");
        eq(h, number(restored, "batchParallel"), 3, context + " lost processor parallel");
        eq(h, number(restored, "batchProgress"), 47, context + " lost processor progress");
        eq(h, count(pending(restored), Items.DIAMOND), 5, context + " lost processor pending item");
        eq(h, fluidAmount(list(restored, "pendingFluids"), GTMaterials.Water.getFluid(1)), 750,
                context + " lost processor pending fluid");
        h.assertTrue((boolean) call(get(restored, "pendingBuffer"), "hasAny"),
                context + " detached processor pending buffer");
    }

    private static void cleanupProcessorReloadFixture(ServerLevel level,
                                                      ChunkPos chunkPos,
                                                      BlockPos controllerPos) {
        level.setBlockAndUpdate(controllerPos, Blocks.AIR.defaultBlockState());
        level.getChunkAt(controllerPos).setUnsaved(true);
        level.getChunkSource().save(true);
        level.setChunkForced(chunkPos.x, chunkPos.z, false);
    }

    private static long fluidOutputAmount(Object m, FluidStack expected) {
        long total = 0;
        for (FluidHatchPartMachine hatch : GSESteamEngineTestSupport.<FluidHatchPartMachine>list(m, "fluidOutputHatches")) {
            for (int tank = 0; tank < hatch.tank.getTanks(); tank++) {
                FluidStack stack = hatch.tank.getFluidInTank(tank);
                if (stack.isFluidEqual(expected)) total += stack.getAmount();
            }
        }
        return total;
    }

}
