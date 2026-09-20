package com.hoshino.gregsteamexpansion.gametest;

import com.hoshino.gregsteamexpansion.GregSteamExpansion;
import com.hoshino.gregsteamexpansion.difficulty.Difficulty;
import com.hoshino.gregsteamexpansion.difficulty.GSEDifficultyState;
import com.hoshino.gregsteamexpansion.machine.multiblock.LargeHeatStorageSteamFurnaceMachine;
import com.hoshino.gregsteamexpansion.machine.multiblock.SteamThrottle;
import com.hoshino.gregsteamexpansion.machine.multiblock.furnace.FurnaceThermalLogic;
import com.hoshino.gregsteamexpansion.registry.GSEMachines;

import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.capability.recipe.IRecipeCapabilityHolder;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.modifier.ParallelLogic;
import com.gregtechceu.gtceu.common.data.GTBlocks;
import com.gregtechceu.gtceu.common.data.GTMachines;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.gregtechceu.gtceu.common.machine.multiblock.part.ItemBusPartMachine;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;

import static com.hoshino.gregsteamexpansion.gametest.GSESteamEngineTestSupport.*;

@GameTestHolder(GregSteamExpansion.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GSEFurnaceTests {
    private GSEFurnaceTests() {}

    @GameTest(template = "empty_32x32x32", timeoutTicks = 300)
    public static void furnaceStateBoundaries(GameTestHelper h) {
        formed(h, GSEMachines.LARGE_HEAT_STORAGE_STEAM_FURNACE, m -> {
            assertFurnaceThermalLogic(h);
            assertThrottledPreheat(h, m);
            stateBoundaries(h, m);
        });
    }

    private static void assertThrottledPreheat(GameTestHelper h, MultiblockControllerMachine m) {
        LargeHeatStorageSteamFurnaceMachine furnace = (LargeHeatStorageSteamFurnaceMachine) m;
        Difficulty difficulty = (Difficulty) call(m, "currentDifficulty");
        set(m, "currentTemperature", LargeHeatStorageSteamFurnaceMachine.COLD_TEMPERATURE);
        set(m, "preheatProgressUnits", 0L);
        set(m, "heatTimer", 0);
        set(m, "preheatDurationTicks", 0);
        set(m, "preheatTotalSteamMb", 0L);
        furnace.setSteamThrottlePercent(25);
        fillSteam(m, 32_000);
        long steamBefore = steam(m);

        h.assertTrue((boolean) call(m, "tryPreheat", difficulty),
                "Throttled furnace preheat did not consume its first tick");
        int duration = (int) number(m, "preheatDurationTicks");
        eq(h, duration, SteamThrottle.scaledDuration(GSEDifficultyState.preheatIntervalTicks(false), 25),
                "25% furnace preheat did not lock a four-times interval");
        long lockedTotal = number(m, "preheatTotalSteamMb");
        for (int tick = 1; tick < duration; tick++) {
            call(m, "tryPreheat", difficulty);
        }
        eq(h, number(m, "currentTemperature"), LargeHeatStorageSteamFurnaceMachine.COLD_TEMPERATURE + 1,
                "Throttled preheat did not advance exactly one degree at its locked duration");
        eq(h, steamBefore - steam(m), lockedTotal,
                "Throttled preheat changed the exact per-degree steam total");
        furnace.setSteamThrottlePercent(100);
    }

    private static void assertFurnaceThermalLogic(GameTestHelper h) {
        eq(h, FurnaceThermalLogic.maxTemperature(7), 1_000, "7-wide furnace maximum temperature changed");
        eq(h, FurnaceThermalLogic.maxTemperature(11), 1_500, "11-wide furnace maximum temperature changed");
        eq(h, FurnaceThermalLogic.maxTemperature(15), 2_000, "15-wide furnace maximum temperature changed");
        eq(h, FurnaceThermalLogic.startupTemperature(15), 1_200,
                "15-wide furnace startup temperature changed");
        eq(h, FurnaceThermalLogic.preheatCostPerDegreeUnits(7, 6, Difficulty.NORMAL), 54_000,
                "Normal furnace preheat cost changed");
        eq(h, FurnaceThermalLogic.coolingIntervalTicks(7, 6, 1_000, false), 6,
                "Idle furnace cooling interval changed");
        eq(h, FurnaceThermalLogic.coolingIntervalTicks(7, 6, 1_000, true), 42,
                "Processing furnace cooling interval changed");

        FurnaceThermalLogic.PreheatPlan coldPlan = FurnaceThermalLogic.planPreheat(
                7, 6, FurnaceThermalLogic.coldState(), 120_000, Difficulty.NORMAL);
        h.assertTrue(coldPlan.shouldAdvance() && coldPlan.drawsSteam() && coldPlan.steamMb() == 540,
                "Cold furnace preheat plan changed its per-degree steam request");
        FurnaceThermalLogic.State funded = new FurnaceThermalLogic.State(20, 54_000, 4, 3);
        FurnaceThermalLogic.PreheatPlan plan = FurnaceThermalLogic.planPreheat(
                7, 6, funded, 120_000, Difficulty.NORMAL);
        h.assertTrue(plan.shouldAdvance() && !plan.drawsSteam() && plan.steamMb() == 0,
                "Funded furnace degree requested more steam");
        FurnaceThermalLogic.State heated = FurnaceThermalLogic.advanceHeating(funded, 1_000, 5, 0);
        eq(h, heated.temperature(), 21, "Funded furnace degree did not advance");
        eq(h, heated.preheatProgressUnits(), 0, "Heating did not clear per-degree steam progress");
        eq(h, heated.heatTimer(), 0, "Heating did not reset the interval timer");
        eq(h, heated.coolTimer(), 3, "Heating changed the independent cooling timer");

        FurnaceThermalLogic.State cooled = FurnaceThermalLogic.cool(
                7, 6, new FurnaceThermalLogic.State(1_000, 12_345, 2, 5), false);
        eq(h, cooled.temperature(), 999, "Cooling did not lower temperature at its boundary");
        eq(h, cooled.coolTimer(), 0, "Cooling did not reset its interval timer");
        eq(h, cooled.preheatProgressUnits(), 12_345, "Cooling discarded partial preheat steam");
        eq(h, cooled.heatTimer(), 2, "Cooling changed the independent heating timer");
    }

    @GameTest(template = "empty_32x32x32", timeoutTicks = 300)
    public static void furnacePersistedStateRoundTrip(GameTestHelper h) {
        formed(h, GSEMachines.LARGE_HEAT_STORAGE_STEAM_FURNACE, m -> {
            int width = (int) number(m, "formedWidth");
            int height = (int) number(m, "formedHeight");
            set(m, "currentTemperature", 777);
            set(m, "preheatProgressUnits", 12_345L);
            set(m, "heatTimer", 2);
            set(m, "preheatSteamThrottlePercent", 25);
            set(m, "preheatDurationTicks", 20);
            set(m, "preheatTotalSteamMb", 2_652L);
            set(m, "coolTimer", 3);
            set(m, "exhaustDamageTimer", 199L);
            set(m, "workingEnabled", false);
            set(m, "largeSteamOverclockEnabled", true);
            set(m, "lastAppliedDifficulty", 1);
            set(m, "recipeMode", LargeHeatStorageSteamFurnaceMachine.MODE_FURNACE);
            set(m, "hasBatch", true);
            set(m, "batchTotalSteamMb", 80_000L);
            set(m, "batchSteamPerTickMb", 400L);
            set(m, "batchLargeSteamOverclock", true);
            set(m, "batchDuration", 200);
            set(m, "batchProgress", 73);
            set(m, "batchParallel", 4);
            set(m, "batchSpeed", 1.25F);
            set(m, "batchRecipeId", "gregsteamexpansion:persisted_furnace");
            set(m, "batchRecipeMode", LargeHeatStorageSteamFurnaceMachine.MODE_FURNACE);
            set(m, "batchOriginWidth", width);
            set(m, "batchOriginHeight", height);
            set(m, "distinctBuses", true);
            set(m, "inputBusCursor", 3);
            long inputSource = m.getPos().offset(2, 1, 0).asLong();
            set(m, "batchInputSourcePos", inputSource);
            pending(m).add(new ItemStack(Items.GLASS, 7));

            CompoundTag saved = saveState(h, m);
            clearFurnaceState(m);
            loadState(h, m, saved);

            eq(h, number(m, "formedWidth"), width, "Furnace width was not restored");
            eq(h, number(m, "formedHeight"), height, "Furnace height was not restored");
            eq(h, number(m, "currentTemperature"), 777, "Furnace temperature was not restored");
            eq(h, number(m, "preheatProgressUnits"), 12_345, "Furnace preheat remainder was not restored");
            eq(h, number(m, "heatTimer"), 2, "Furnace heat timer was not restored");
            eq(h, number(m, "preheatSteamThrottlePercent"), 25,
                    "Furnace locked preheat throttle was not restored");
            eq(h, number(m, "preheatDurationTicks"), 20,
                    "Furnace locked preheat duration was not restored");
            eq(h, number(m, "preheatTotalSteamMb"), 2_652,
                    "Furnace locked preheat total was not restored");
            eq(h, number(m, "coolTimer"), 3, "Furnace cool timer was not restored");
            eq(h, number(m, "exhaustDamageTimer"), 199, "Furnace exhaust timer was not restored");
            h.assertTrue(!(boolean) call(m, "isWorkingEnabled"), "Furnace work-enabled state was not restored");
            h.assertTrue((boolean) call(m, "isLargeSteamOverclockEnabled"),
                    "Furnace overclock preference was not restored");
            h.assertTrue((boolean) get(m, "hasBatch"), "Furnace batch flag was not restored");
            h.assertTrue((boolean) call(m, "isCurrentBatchLargeSteamOverclocked"),
                    "Furnace locked batch overclock was not restored");
            eq(h, number(m, "batchProgress"), 73, "Furnace progress was not restored");
            eq(h, number(m, "batchDuration"), 200, "Furnace duration was not restored");
            eq(h, number(m, "batchParallel"), 4, "Furnace parallel was not restored");
            eq(h, number(m, "batchOriginWidth"), width, "Furnace batch width was not restored");
            eq(h, number(m, "batchOriginHeight"), height, "Furnace batch height was not restored");
            h.assertTrue((boolean) get(m, "distinctBuses"), "Furnace distinct-bus setting was not restored");
            eq(h, number(m, "inputBusCursor"), 3, "Furnace input-bus cursor was not restored");
            eq(h, number(m, "batchInputSourcePos"), inputSource,
                    "Furnace locked input source was not restored");
            eq(h, count(pending(m), Items.GLASS), 7, "Furnace pending output was not restored");
            h.assertTrue((boolean) call(get(m, "pendingBuffer"), "hasAny"),
                    "Furnace pending buffer detached from restored list");

            fillOutputs(m, false);
            h.assertTrue((boolean) call(m, "deliverPendingOutputs"),
                    "Furnace could not deliver restored pending output");
            eq(h, outputCount(m, Items.GLASS), 7, "Furnace duplicated or lost restored output");
        });
    }

    @GameTest(template = "empty_32x32x32", timeoutTicks = 300)
    public static void furnacePendingAndSettlement(GameTestHelper h) {
        formed(h, GSEMachines.LARGE_HEAT_STORAGE_STEAM_FURNACE, m -> settlement(h, m));
    }

    @GameTest(template = "empty_32x32x32", timeoutTicks = 300)
    public static void largeSteamOverclockLocksOnlyAtFurnaceBatchStart(GameTestHelper h) {
        formed(h, GSEMachines.LARGE_HEAT_STORAGE_STEAM_FURNACE, m -> {
            LargeHeatStorageSteamFurnaceMachine furnace = (LargeHeatStorageSteamFurnaceMachine) m;
            eq(h, replaceSteamSupplyHatches(h, m, 1), 1,
                    "Furnace fixture did not replace exactly one supply hatch");
            h.assertTrue(furnace.hasLargeSteamSupplyHatch(),
                    "Reformed furnace did not collect the Large Steam Supply Hatch");

            ItemBusPartMachine input = m.getParts().stream()
                    .filter(ItemBusPartMachine.class::isInstance)
                    .map(ItemBusPartMachine.class::cast)
                    .filter(bus -> bus.getInventory().getHandlerIO() == IO.IN)
                    .findFirst().orElseThrow();
            fillOutputs(m, false);
            fillSteam(m, 32_000);
            set(m, "currentTemperature", call(m, "startupTemperature"));
            Object difficulty = call(m, "currentDifficulty");

            input.getInventory().setStackInSlot(0, new ItemStack(Items.COBBLESTONE));
            h.assertTrue((boolean) call(m, "tryStartBatch", difficulty),
                    "Furnace did not start its normal cobblestone batch");
            h.assertTrue(!furnace.isCurrentBatchLargeSteamOverclocked(),
                    "Furnace overclocked while its toggle was disabled");
            long normalDuration = number(m, "batchDuration");
            long normalDemand = number(m, "batchSteamPerTickMb");
            furnace.setLargeSteamOverclockEnabled(true);
            h.assertTrue(!furnace.isCurrentBatchLargeSteamOverclocked(),
                    "Enabling the toggle rewrote the running normal furnace batch");
            eq(h, number(m, "batchDuration"), normalDuration,
                    "Enabling the toggle changed the running furnace duration");
            eq(h, number(m, "batchSteamPerTickMb"), normalDemand,
                    "Enabling the toggle changed the running furnace demand");

            clearActiveFurnaceBatch(m);
            input.getInventory().setStackInSlot(0, new ItemStack(Items.COBBLESTONE));
            h.assertTrue((boolean) call(m, "tryStartBatch", difficulty),
                    "Furnace did not start the next batch after enabling overclock");
            h.assertTrue(furnace.isCurrentBatchLargeSteamOverclocked(),
                    "Furnace did not lock overclock for the next batch");
            eq(h, number(m, "batchDuration"), (normalDuration + 1) / 2,
                    "Furnace did not round its halved duration upward");
            eq(h, number(m, "batchSteamPerTickMb"), normalDemand * 3,
                    "Furnace did not triple its per-tick demand");

            furnace.setLargeSteamOverclockEnabled(false);
            h.assertTrue(furnace.isCurrentBatchLargeSteamOverclocked(),
                    "Disabling the toggle rewrote the running overclocked furnace batch");
            long steamBeforeTick = steam(m);
            tick(m);
            eq(h, steamBeforeTick - steam(m), normalDemand * 3,
                    "Running overclocked furnace batch did not draw its locked demand");
        });
    }

    @GameTest(template = "empty_32x32x32", timeoutTicks = 300)
    public static void furnaceParallelCapacity(GameTestHelper h) {
        formed(h, GSEMachines.LARGE_HEAT_STORAGE_STEAM_FURNACE, m -> {
            var input = m.getParts().stream().filter(ItemBusPartMachine.class::isInstance)
                    .map(ItemBusPartMachine.class::cast)
                    .filter(bus -> bus.getInventory().getHandlerIO() == IO.IN).findFirst().orElseThrow();
            input.getInventory().setStackInSlot(0, new ItemStack(Items.COBBLESTONE, 9));
            fillOutputs(m, true);
            fillSteam(m, 32_000);
            set(m, "currentTemperature", call(m, "startupTemperature"));
            Object difficulty = call(m, "currentDifficulty");
            h.assertTrue(!(boolean) call(m, "tryStartBatch", difficulty), "Full furnace outputs accepted a batch");
            eq(h, input.getInventory().getStackInSlot(0).getCount(), 9, "Rejected furnace batch consumed input");
            outputs(m).get(0).getInventory().setStackInSlot(0, new ItemStack(Items.STONE, 58));
            h.assertTrue((boolean) call(m, "tryStartBatch", difficulty), "Furnace did not start cobblestone smelting");
            eq(h, number(m, "batchParallel"), 6, "Furnace output capacity did not limit parallel to six");
            eq(h, input.getInventory().getStackInSlot(0).getCount(), 3, "Furnace did not consume six inputs");
            eq(h, outputs(m).get(0).getInventory().getStackInSlot(0).getCount(), 58,
                    "Furnace precheck mutated outputs before completion");
        });
    }

    @GameTest(template = "empty_32x32x32", timeoutTicks = 300)
    public static void furnaceDistinctBusesAreIsolatedAndFair(GameTestHelper h) {
        formed(h, GSEMachines.LARGE_HEAT_STORAGE_STEAM_FURNACE, m -> {
            List<ItemBusPartMachine> inputs = installAdditionalFurnaceInputBus(h, m);
            h.assertTrue(inputs.size() == 2, "Distinct-bus fixture must contain exactly two input buses");
            for (ItemBusPartMachine input : inputs) {
                for (int slot = 0; slot < input.getInventory().getSlots(); slot++) {
                    input.getInventory().setStackInSlot(slot, ItemStack.EMPTY);
                }
            }

            // One two-item ingredient can be satisfied by the controller's merged view,
            // but neither independently built input scope may combine across the buses.
            inputs.get(0).getInventory().setStackInSlot(0, new ItemStack(Items.COBBLESTONE));
            inputs.get(1).getInventory().setStackInSlot(0, new ItemStack(Items.COBBLESTONE));
            GTRecipe splitRecipe = GTRecipeTypes.FURNACE_RECIPES
                    .recipeBuilder(GregSteamExpansion.id("distinct_bus_split_probe"))
                    .inputItems(new ItemStack(Items.COBBLESTONE, 2))
                    .outputItems(new ItemStack(Items.STONE))
                    .duration(20).EUt(8).buildRawRecipe();
            eq(h, ParallelLogic.getMaxByInput((IRecipeCapabilityHolder) m, splitRecipe, 1, List.of()), 1,
                    "Merged furnace inputs could not satisfy the split probe");
            @SuppressWarnings("unchecked")
            List<IRecipeCapabilityHolder> scopes =
                    (List<IRecipeCapabilityHolder>) call(get(m, "inputScheduler"), "scopes");
            h.assertTrue(scopes.size() == 2, "Furnace did not build one isolated scope per input bus");
            for (IRecipeCapabilityHolder scope : scopes) {
                eq(h, ParallelLogic.getMaxByInput(scope, splitRecipe, 1, List.of()), 0,
                        "An isolated input scope consumed ingredients from another bus");
            }

            // Both buses can run the same real recipe. The persisted cursor must select
            // the first one, advance, then select the second even after the first is restocked.
            inputs.get(0).getInventory().setStackInSlot(0, new ItemStack(Items.COBBLESTONE));
            inputs.get(1).getInventory().setStackInSlot(0, new ItemStack(Items.COBBLESTONE));
            fillOutputs(m, false);
            fillSteam(m, 32_000);
            set(m, "currentTemperature", call(m, "startupTemperature"));
            set(m, "distinctBuses", true);
            set(m, "inputBusCursor", 0);
            Object difficulty = call(m, "currentDifficulty");

            h.assertTrue((boolean) call(m, "tryStartBatch", difficulty),
                    "Distinct furnace did not start from its first input bus");
            eq(h, inputs.get(0).getInventory().getStackInSlot(0).getCount(), 0,
                    "First distinct batch did not consume the cursor-selected bus");
            eq(h, inputs.get(1).getInventory().getStackInSlot(0).getCount(), 1,
                    "First distinct batch consumed the second bus");
            eq(h, number(m, "inputBusCursor"), 1, "Distinct cursor did not advance after the first batch");
            eq(h, number(m, "batchInputSourcePos"), inputs.get(0).getPos().asLong(),
                    "First batch did not lock its input source");

            clearActiveFurnaceBatch(m);
            inputs.get(0).getInventory().setStackInSlot(0, new ItemStack(Items.COBBLESTONE));
            h.assertTrue((boolean) call(m, "tryStartBatch", difficulty),
                    "Distinct furnace did not start from its second input bus");
            eq(h, inputs.get(0).getInventory().getStackInSlot(0).getCount(), 1,
                    "Second distinct batch ignored the advanced cursor");
            eq(h, inputs.get(1).getInventory().getStackInSlot(0).getCount(), 0,
                    "Second distinct batch did not consume the cursor-selected bus");
            eq(h, number(m, "inputBusCursor"), 0, "Distinct cursor did not wrap after the second batch");
            eq(h, number(m, "batchInputSourcePos"), inputs.get(1).getPos().asLong(),
                    "Second batch did not lock its input source");
        });
    }

    private static List<ItemBusPartMachine> installAdditionalFurnaceInputBus(
            GameTestHelper h, MultiblockControllerMachine m) {
        if (m.isFormed()) {
            m.onStructureInvalid();
        }
        List<BlockPos> candidates = new java.util.ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(
                m.getPos().offset(-8, 0, -8), m.getPos().offset(8, 8, 8))) {
            if (h.getLevel().getBlockState(pos).is(GTBlocks.CASING_BRONZE_BRICKS.get())) {
                candidates.add(pos.immutable());
            }
        }
        for (BlockPos candidate : candidates) {
            var original = h.getLevel().getBlockState(candidate);
            h.getLevel().setBlockAndUpdate(candidate,
                    GTMachines.STEAM_IMPORT_BUS.getBlock().defaultBlockState());
            if (m.checkPattern()) {
                m.onStructureFormed();
                return m.getParts().stream()
                        .filter(ItemBusPartMachine.class::isInstance)
                        .map(ItemBusPartMachine.class::cast)
                        .filter(bus -> bus.getInventory().getHandlerIO() == IO.IN)
                        .sorted(java.util.Comparator.comparing(bus -> bus.self().getPos()))
                        .toList();
            }
            h.getLevel().setBlockAndUpdate(candidate, original);
        }
        h.fail("Could not install a second legal furnace input bus");
        return List.of();
    }

    private static void clearFurnaceState(MultiblockControllerMachine m) {
        set(m, "formedWidth", 0);
        set(m, "formedHeight", 0);
        set(m, "currentTemperature", LargeHeatStorageSteamFurnaceMachine.COLD_TEMPERATURE);
        set(m, "preheatProgressUnits", 0L);
        set(m, "heatTimer", 0);
        set(m, "preheatSteamThrottlePercent", 100);
        set(m, "preheatDurationTicks", 0);
        set(m, "preheatTotalSteamMb", 0L);
        set(m, "coolTimer", 0);
        set(m, "exhaustDamageTimer", 0L);
        set(m, "workingEnabled", true);
        set(m, "largeSteamOverclockEnabled", false);
        set(m, "lastAppliedDifficulty", 0);
        set(m, "hasBatch", false);
        set(m, "batchTotalSteamMb", 0L);
        set(m, "batchSteamPerTickMb", 0L);
        set(m, "batchLargeSteamOverclock", false);
        set(m, "batchDuration", 0);
        set(m, "batchProgress", 0);
        set(m, "batchParallel", 0);
        set(m, "batchSpeed", 1.0F);
        set(m, "batchRecipeId", "");
        set(m, "batchOriginWidth", 0);
        set(m, "batchOriginHeight", 0);
        set(m, "distinctBuses", false);
        set(m, "inputBusCursor", 0);
        set(m, "batchInputSourcePos", Long.MIN_VALUE);
        pending(m).clear();
    }

    private static void clearActiveFurnaceBatch(MultiblockControllerMachine m) {
        set(m, "hasBatch", false);
        set(m, "batchRecipe", null);
        set(m, "batchRecipeId", "");
        set(m, "batchParallel", 0);
        set(m, "batchProgress", 0);
        set(m, "batchDuration", 0);
        set(m, "batchTotalSteamMb", 0L);
        set(m, "batchSteamPerTickMb", 0L);
        set(m, "batchLargeSteamOverclock", false);
        set(m, "batchInputSourcePos", Long.MIN_VALUE);
    }
}
