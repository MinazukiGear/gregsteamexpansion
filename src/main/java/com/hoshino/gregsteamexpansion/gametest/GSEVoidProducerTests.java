package com.hoshino.gregsteamexpansion.gametest;

import com.hoshino.gregsteamexpansion.GregSteamExpansion;
import com.hoshino.gregsteamexpansion.difficulty.GSEDifficultyConfig;
import com.hoshino.gregsteamexpansion.machine.multiblock.voidproducer.AbstractSteamVoidMachine;
import com.hoshino.gregsteamexpansion.registry.GSEMachines;

import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.common.data.GTMaterials;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;

import static com.hoshino.gregsteamexpansion.gametest.GSESteamEngineTestSupport.*;

/** Runtime contracts for steam-powered void producers. */
@GameTestHolder(GregSteamExpansion.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GSEVoidProducerTests {
    private GSEVoidProducerTests() {}

    @GameTest(template = "empty_32x32x32", timeoutTicks = 300)
    public static void voidProducerStateBoundaries(GameTestHelper h) {
        formed(h, GSEMachines.LARGE_STEAM_ORE_PLANT, m -> {
            if (assertDisabledOrePlantStaysIdle(h, m)) return;
            stateBoundaries(h, m);
        });
    }

    @GameTest(template = "empty_32x32x32", timeoutTicks = 300)
    public static void voidProducerPersistedStateRoundTrip(GameTestHelper h) {
        formed(h, GSEMachines.LARGE_STEAM_ORE_PLANT, m -> {
            set(m, "workingEnabled", false);
            set(m, "largeSteamOverclockEnabled", true);
            set(m, "cycleProgress", 137);
            set(m, "cycleLargeSteamOverclock", true);
            pending(m).add(new ItemStack(Items.RAW_GOLD, 11));
            List<FluidStack> fluids = list(m, "pendingFluids");
            fluids.add(GTMaterials.Water.getFluid(333));

            CompoundTag saved = saveState(h, m);
            set(m, "workingEnabled", true);
            set(m, "largeSteamOverclockEnabled", false);
            set(m, "cycleProgress", 0);
            set(m, "cycleLargeSteamOverclock", false);
            pending(m).clear();
            fluids.clear();
            loadState(h, m, saved);

            h.assertTrue(!(boolean) call(m, "isWorkingEnabled"),
                    "Void producer work-enabled state was not restored");
            h.assertTrue((boolean) call(m, "isLargeSteamOverclockEnabled"),
                    "Void producer overclock preference was not restored");
            eq(h, number(m, "cycleProgress"), 137, "Void producer progress was not restored");
            h.assertTrue((boolean) call(m, "isCurrentCycleLargeSteamOverclocked"),
                    "Void producer locked cycle overclock was not restored");
            eq(h, count(pending(m), Items.RAW_GOLD), 11, "Void producer pending item was not restored");
            eq(h, fluidAmount(fluids, GTMaterials.Water.getFluid(1)), 333,
                    "Void producer pending fluid was not restored");
            h.assertTrue((boolean) call(get(m, "pendingBuffer"), "hasAny"),
                    "Void producer pending buffer detached from restored lists");
        });
    }

    @GameTest(template = "empty_32x32x32", timeoutTicks = 300)
    public static void voidPendingRecovery(GameTestHelper h) {
        formed(h, GSEMachines.LARGE_STEAM_ORE_PLANT, m -> {
            if (assertDisabledOrePlantStaysIdle(h, m)) return;
            fillOutputs(m, true);
            set(m, "cycleProgress", 199);
            fillSteam(m, 32_000);
            tick(m);
            eq(h, progress(m), 0, "Completed production cycle did not reset");
            int produced = pending(m).stream().mapToInt(ItemStack::getCount).sum();
            var producer = (AbstractSteamVoidMachine) m;
            eq(h, produced, 4L * 8 * producer.outputMultiplier(), "Cycle did not produce one draw per station");
            long before = steam(m);
            tick(m);
            eq(h, steam(m), before, "Pending output blockage consumed steam");
            eq(h, pending(m).stream().mapToInt(ItemStack::getCount).sum(), produced, "Blocked cycle generated again");
            fillOutputs(m, false);
            call(m, "setWorkingEnabled", false);
            tick(m);
            h.assertTrue(pending(m).isEmpty(), "Paused void producer did not deliver retained output");
            eq(h, outputTotal(m), produced, "Void output recovery lost or duplicated products");
        });
    }

    @GameTest(template = "empty_32x32x32", timeoutTicks = 300)
    public static void largeSteamOverclockLocksOnlyAtVoidCycleStart(GameTestHelper h) {
        formed(h, GSEMachines.LARGE_STEAM_ORE_PLANT, m -> {
            if (assertDisabledOrePlantStaysIdle(h, m)) return;
            AbstractSteamVoidMachine producer = (AbstractSteamVoidMachine) m;
            eq(h, replaceSteamSupplyHatches(h, m, Integer.MAX_VALUE), 10,
                    "Ore plant fixture did not replace all ten supply hatches");
            h.assertTrue(producer.hasLargeSteamSupplyHatch(),
                    "Reformed ore plant did not collect its Large Steam Supply Hatches");

            int normalDuration = producer.cycleTicks();
            long normalDemand = producer.steamPerStationTick() * producer.stationCount();
            producer.setLargeSteamOverclockEnabled(true);
            fillSteam(m, 256_000);
            long steamBeforeTick = steam(m);
            tick(m);
            h.assertTrue(producer.isCurrentCycleLargeSteamOverclocked(),
                    "Void producer did not lock overclock at cycle start");
            eq(h, producer.getCycleTicks(), (normalDuration + 1L) / 2,
                    "Void producer did not halve its cycle duration");
            eq(h, producer.getSteamPerTickDemand(), normalDemand * 3,
                    "Void producer did not triple its cycle demand");
            eq(h, steamBeforeTick - steam(m), normalDemand * 3,
                    "Overclocked void cycle did not draw its locked demand");

            producer.setLargeSteamOverclockEnabled(false);
            steamBeforeTick = steam(m);
            tick(m);
            h.assertTrue(producer.isCurrentCycleLargeSteamOverclocked(),
                    "Disabling the toggle rewrote the running void cycle");
            eq(h, steamBeforeTick - steam(m), normalDemand * 3,
                    "Running void cycle did not retain its overclocked demand");

            set(m, "cycleProgress", 0);
            steamBeforeTick = steam(m);
            tick(m);
            h.assertTrue(!producer.isCurrentCycleLargeSteamOverclocked(),
                    "Disabled overclock remained active for the next void cycle");
            eq(h, producer.getCycleTicks(), normalDuration,
                    "Normal void cycle kept the overclocked duration");
            eq(h, producer.getSteamPerTickDemand(), normalDemand,
                    "Normal void cycle kept the overclocked demand");
            eq(h, steamBeforeTick - steam(m), normalDemand,
                    "Normal void cycle did not draw its locked demand");
        });
    }

    private static boolean assertDisabledOrePlantStaysIdle(GameTestHelper h,
                                                           MultiblockControllerMachine m) {
        if (GSEDifficultyConfig.orePlantEnabled()) return false;
        set(m, "cycleProgress", 7);
        fillSteam(m, 32_000);
        long before = steam(m);
        tick(m);
        eq(h, progress(m), 7, "Config-disabled ore plant advanced its production cycle");
        eq(h, steam(m), before, "Config-disabled ore plant consumed steam");
        eq(h, demand(m), 0, "Config-disabled ore plant displayed a steam demand");
        h.assertTrue(!(boolean) call(m, "isConsumingSteam"),
                "Config-disabled ore plant reported active consumption");
        return true;
    }

    private static int outputTotal(Object m) {
        int total = 0;
        for (var bus : outputs(m)) for (int slot = 0; slot < bus.getInventory().getSlots(); slot++) {
            total += bus.getInventory().getStackInSlot(slot).getCount();
        }
        return total;
    }
}
