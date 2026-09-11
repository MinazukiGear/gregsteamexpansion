package com.hoshino.gregsteamexpansion.gametest;

import com.hoshino.gregsteamexpansion.GregSteamExpansion;
import com.hoshino.gregsteamexpansion.machine.multiblock.LargeHeatStorageSteamFurnaceMachine;
import com.hoshino.gregsteamexpansion.machine.multiblock.part.SteamAirIntakeHatchPartMachine;
import com.hoshino.gregsteamexpansion.machine.multiblock.part.SteamExhaustHatchMachine;
import com.hoshino.gregsteamexpansion.machine.multiblock.processor.AbstractSteamProcessorMachine;
import com.hoshino.gregsteamexpansion.machine.multiblock.voidproducer.AbstractSteamVoidMachine;
import com.hoshino.gregsteamexpansion.registry.GSEMachines;
import com.hoshino.gregsteamexpansion.registry.GSEVoidPatterns;
import com.hoshino.gregsteamexpansion.machine.multiblock.crusher.AbstractSteamCrusherMachine;

import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
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
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.function.Consumer;

/**
 * P2-7 characterization tests against real formed controllers and hatch inventories.
 * Fixtures inject a locked batch, then execute production tick/commit methods on
 * the server thread so exact failure boundaries do not depend on tick scheduling.
 * Reflection is isolated below: P0-1 should change these adapters, not assertions.
 * No recipes are registered globally and no shared random seed/config is changed.
 */
@GameTestHolder(GregSteamExpansion.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GSESteamEngineTests {
    private GSESteamEngineTests() {}

    @GameTest(template = "empty_32x32x32", timeoutTicks = 300)
    public static void processorStateBoundaries(GameTestHelper h) {
        formed(h, GSEMachines.LARGE_STEAM_MACERATOR, m -> stateBoundaries(h, m));
    }

    @GameTest(template = "empty_32x32x32", timeoutTicks = 300)
    public static void crusherStateBoundaries(GameTestHelper h) {
        formed(h, GSEMachines.LARGE_STEAM_CRUSHER, m -> stateBoundaries(h, m));
    }

    @GameTest(template = "empty_32x32x32", timeoutTicks = 300)
    public static void furnaceStateBoundaries(GameTestHelper h) {
        formed(h, GSEMachines.LARGE_HEAT_STORAGE_STEAM_FURNACE, m -> stateBoundaries(h, m));
    }

    @GameTest(template = "empty_32x32x32", timeoutTicks = 300)
    public static void voidProducerStateBoundaries(GameTestHelper h) {
        formed(h, GSEMachines.LARGE_STEAM_ORE_PLANT, m -> stateBoundaries(h, m));
    }

    private static void stateBoundaries(GameTestHelper h, MultiblockControllerMachine m) {
        List<FluidHatchPartMachine> supplies = supplies(m);
        h.assertTrue(!supplies.isEmpty(), "Fixture has no steam supply");
        fillSteam(m, 32_000);
        long before = steam(m);
        h.assertTrue(!(boolean) call(m, "drawSteam", supplies.size() * 1200L + 1),
                "Per-hatch 1200 mB/t limit was exceeded");
        eq(h, steam(m), before, "Over-budget simulation consumed steam");
        fillSteam(m, 1);
        h.assertTrue(!(boolean) call(m, "drawSteam", supplies.size() + 1L), "Partial supply satisfied full demand");
        eq(h, steam(m), supplies.size(), "Failed draw partially drained hatches");
        fillSteam(m, 32_000);
        before = steam(m);
        h.assertTrue((boolean) call(m, "drawSteam", supplies.size() * 1200L), "Exact per-hatch budget failed");
        eq(h, before - steam(m), supplies.size() * 1200L, "Successful draw charged wrong amount");

        seedBatch(m, itemRecipe(), 2);
        set(m, progressField(m), 7);
        long expectedDemand = demand(m);
        SteamExhaustHatchMachine exhaust = m.getParts().stream()
                .filter(SteamExhaustHatchMachine.class::isInstance)
                .map(SteamExhaustHatchMachine.class::cast).findFirst().orElseThrow();
        BlockPos front = exhaust.getPos().relative(exhaust.getFrontFacing());
        h.assertTrue(!exhaust.isExhaustBlocked(), "Fixture exhaust channel is blocked");
        set(m, "exhaustFeedbackTimer", 19);
        set(m, "exhaustDamageTimer", 198);

        // Pause and obstruction freeze; neither is the shortage rollback.
        call(m, "setWorkingEnabled", false);
        eq(h, demand(m), 0, "Paused machine displayed a current steam demand");
        before = steam(m);
        tick(m);
        eq(h, progress(m), 7, "Pause rolled back progress");
        eq(h, steam(m), before, "Pause consumed steam");
        call(m, "setWorkingEnabled", true);
        h.getLevel().setBlockAndUpdate(front, Blocks.STONE.defaultBlockState());
        tick(m);
        eq(h, demand(m), 0, "Exhaust-blocked machine displayed a current steam demand");
        eq(h, progress(m), 7, "Blocked exhaust rolled back progress");
        eq(h, steam(m), before, "Blocked exhaust consumed steam");
        eq(h, number(m, "exhaustDamageTimer"), 198, "Inactive tick advanced exhaust damage");
        h.getLevel().setBlockAndUpdate(front, Blocks.AIR.defaultBlockState());

        // A failed consuming tick rolls back but keeps locked batch economics.
        fillSteam(m, 1);
        tick(m);
        eq(h, demand(m), expectedDemand, "Steam shortage hid the demand needed to resume");
        eq(h, progress(m), 1, "Steam shortage did not roll back to one tick");
        eq(h, steam(m), supplies.size(), "Shortage consumed a partial steam budget");
        eq(h, number(m, "exhaustDamageTimer"), 198, "Shortage advanced exhaust damage");
        if (!(m instanceof AbstractSteamVoidMachine)) {
            h.assertTrue((boolean) get(m, "hasBatch"), "Shortage discarded the locked batch");
            eq(h, number(m, "batchParallel"), 2, "Shortage changed locked parallel");
            eq(h, number(m, "batchSteamPerTickMb"), 200, "Shortage changed locked demand");
        }

        var target = h.spawn(EntityType.COW, new BlockPos(1, 1, 1));
        target.moveTo(front.getX() + 0.5, front.getY(), front.getZ() + 0.5);
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(20);
        target.setHealth(20);
        target.setNoAi(true);
        target.setNoGravity(true);
        float health = target.getHealth();
        fillSteam(m, 32_000);
        tick(m);
        eq(h, demand(m), expectedDemand, "Running machine displayed the wrong current demand");
        eq(h, progress(m), 2, "Steam recovery did not continue retained progress");
        eq(h, number(m, "exhaustFeedbackTimer"), 0, "20th active tick did not reset feedback cycle");
        eq(h, number(m, "exhaustDamageTimer"), 199, "Active tick did not advance damage cycle");
        h.assertTrue(target.getHealth() == health, "Exhaust damage occurred before tick 200");
        tick(m);
        eq(h, number(m, "exhaustDamageTimer"), 0, "200th active tick did not reset damage cycle");
        h.assertTrue(target.getHealth() == health - SteamExhaustHatchMachine.EXHAUST_DAMAGE,
                "200th active tick did not deal exactly 12 heat damage");
        target.discard();

        pending(m).add(new ItemStack(Items.DIAMOND, 3));
        set(m, progressField(m), 7);
        call(m, "setWorkingEnabled", false);
        m.onStructureInvalid();
        eq(h, demand(m), 0, "Invalid structure displayed a current steam demand");
        eq(h, progress(m), 1, "Structure invalidation did not roll progress back to one tick");
        eq(h, count(pending(m), Items.DIAMOND), 3, "Invalidation lost pending output");
        if (!(m instanceof AbstractSteamVoidMachine)) {
            h.assertTrue((boolean) get(m, "hasBatch"), "Invalidation discarded the locked batch");
            eq(h, number(m, "batchParallel"), 2, "Invalidation changed locked parallel");
        }
    }

    @GameTest(template = "empty_32x32x32", timeoutTicks = 300)
    public static void processorPendingAndSettlement(GameTestHelper h) {
        formed(h, GSEMachines.STEAM_COMPRESSOR, m -> settlement(h, m));
    }

    @GameTest(template = "empty_32x32x32", timeoutTicks = 300)
    public static void crusherPendingAndSettlement(GameTestHelper h) {
        formed(h, GSEMachines.STEAM_CRUSHER, m -> settlement(h, m));
    }

    @GameTest(template = "empty_32x32x32", timeoutTicks = 300)
    public static void furnacePendingAndSettlement(GameTestHelper h) {
        formed(h, GSEMachines.LARGE_HEAT_STORAGE_STEAM_FURNACE, m -> settlement(h, m));
    }

    private static void settlement(GameTestHelper h, MultiblockControllerMachine m) {
        fillOutputs(m, true);
        seedBatch(m, itemRecipe(), 4);
        call(m, "completeBatch");
        h.assertTrue(!(boolean) get(m, "hasBatch"), "Completion left the batch active");
        eq(h, count(pending(m), Items.IRON_INGOT), 12, "Guaranteed output was not 3 x 4 (parallel squared/lost)");
        int gold = count(pending(m), Items.GOLD_INGOT);
        h.assertTrue(gold >= 0 && gold <= 4, "Chance output exceeded parallel quantity");
        for (int i = 0; i < 3; i++) {
            h.assertTrue(!(boolean) call(m, "deliverPendingOutputs"), "Full outputs accepted a batch");
            eq(h, count(pending(m), Items.IRON_INGOT), 12, "Blocked retry changed guaranteed output");
            eq(h, count(pending(m), Items.GOLD_INGOT), gold, "Blocked retry rerolled chance output");
            eq(h, outputCount(m, Items.IRON_INGOT), 0, "Blocked delivery partially committed output");
        }
        fillOutputs(m, false);
        h.assertTrue((boolean) call(m, "deliverPendingOutputs"), "Unblocked outputs did not recover");
        h.assertTrue(pending(m).isEmpty(), "Delivered output remained pending");
        eq(h, outputCount(m, Items.IRON_INGOT), 12, "Recovery lost guaranteed output");
        eq(h, outputCount(m, Items.GOLD_INGOT), gold, "Recovery changed chance result");
        call(m, "deliverPendingOutputs");
        eq(h, outputCount(m, Items.IRON_INGOT), 12, "Repeated delivery duplicated output");
    }

    @GameTest(template = "empty_32x32x32", timeoutTicks = 300)
    public static void voidPendingRecovery(GameTestHelper h) {
        formed(h, GSEMachines.LARGE_STEAM_ORE_PLANT, m -> {
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
    public static void processorParallelCapacity(GameTestHelper h) {
        formed(h, GSEMachines.STEAM_COMPRESSOR, m -> parallelCapacity(h, m));
    }

    @GameTest(template = "empty_32x32x32", timeoutTicks = 300)
    public static void crusherParallelCapacity(GameTestHelper h) {
        formed(h, GSEMachines.STEAM_CRUSHER, m -> parallelCapacity(h, m));
    }

    private static void parallelCapacity(GameTestHelper h, MultiblockControllerMachine m) {
        fillOutputs(m, true);
        var bus = outputs(m).get(0);
        bus.getInventory().setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 58));
        GTRecipe recipe = GTRecipeTypes.COMPRESSOR_RECIPES.recipeBuilder(GregSteamExpansion.id("engine_capacity"))
                .outputItems(new ItemStack(Items.IRON_INGOT, 3)).duration(20).EUt(8).buildRawRecipe();
        eq(h, (int) call(m, "largestParallelThatFits", recipe, 4), 2, "Six free item spaces must allow two parallels");
        eq(h, bus.getInventory().getStackInSlot(0).getCount(), 58, "Capacity simulation mutated output inventory");
        bus.getInventory().setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 62));
        eq(h, (int) call(m, "largestParallelThatFits", recipe, 4), 0, "Insufficient output accepted a parallel");
        // Every chance output must fit even when no roll has happened yet.
        bus.getInventory().setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 58));
        eq(h, (int) call(m, "largestParallelThatFits", itemRecipe(), 4), 0, "Precheck ignored chance output space");
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
    public static void blastAirAndSteamAreAtomic(GameTestHelper h) {
        formed(h, GSEMachines.LARGE_STEAM_BLAST_FURNACE, m -> {
            List<SteamAirIntakeHatchPartMachine> intakes = list(m, "airIntakeHatches");
            h.assertTrue(!intakes.isEmpty(), "Fixture lacks blast air intake");
            for (var intake : intakes) intake.tank.getStorages()[0].setFluid(FluidStack.EMPTY);
            seedBatch(m, itemRecipe(), 2);
            set(m, "batchProgress", 7);
            fillSteam(m, 32_000);
            long before = steam(m);
            call(m, "runBatchTick");
            eq(h, progress(m), 1, "Air shortage did not roll back");
            eq(h, steam(m), before, "Air shortage wasted steam");
            intakes.get(0).tank.getStorages()[0].setFluid(GTMaterials.Air.getFluid(8));
            fillSteam(m, 0);
            call(m, "runBatchTick");
            eq(h, intakes.get(0).tank.getFluidInTank(0).getAmount(), 8, "Steam shortage wasted blast air");
            fillSteam(m, 32_000);
            before = steam(m);
            call(m, "runBatchTick");
            eq(h, progress(m), 2, "Recovered blast inputs did not resume batch");
            eq(h, before - steam(m), 200, "Blast tick ignored locked steam demand");
            eq(h, intakes.get(0).tank.getFluidInTank(0).getAmount(), 0, "Blast tick did not consume 4 mB per parallel");
        });
    }

    private static void formed(GameTestHelper h, MultiblockMachineDefinition definition,
                               Consumer<MultiblockControllerMachine> checks) {
        var m = GSEStructureTestUtils.placeShape(h, definition, definition.getMatchingShapes().get(0));
        h.assertTrue(m != null, "Missing fixture controller");
        if (definition == GSEMachines.LARGE_STEAM_ORE_PLANT) {
            // The preview is a minimum forming layout with one supply hatch.
            // A running four-station fixture needs ten (12000 / 1200 mB/t).
            int added = 0;
            for (BlockPos pos : BlockPos.betweenClosed(m.getPos().offset(-9, 0, -9), m.getPos().offset(9, 6, 9))) {
                if (h.getLevel().getBlockState(pos).is(GSEVoidPatterns.bronzeSteamCasing())) {
                    h.getLevel().setBlockAndUpdate(pos, GSEMachines.STEAM_SUPPLY_HATCH.getBlock().defaultBlockState());
                    if (++added == 9) break;
                }
            }
            eq(h, added, 9, "Could not build full-supply ore plant fixture");
        }
        h.startSequence().thenWaitUntil(() -> h.assertTrue(m.isFormed(), "Fixture did not form: " + definition.getId()))
                .thenExecute(() -> checks.accept(m)).thenSucceed();
    }

    private static GTRecipe itemRecipe() {
        return GTRecipeTypes.MACERATOR_RECIPES.recipeBuilder(GregSteamExpansion.id("engine_settlement"))
                .outputItems(new ItemStack(Items.IRON_INGOT, 3))
                .chancedOutput(new ItemStack(Items.GOLD_INGOT), 5000, 0).duration(200).EUt(8).buildRawRecipe();
    }

    private static void seedBatch(MultiblockControllerMachine m, GTRecipe recipe, int parallel) {
        if (m instanceof AbstractSteamVoidMachine) return;
        set(m, "hasBatch", true);
        set(m, "batchRecipe", recipe);
        set(m, "batchRecipeId", recipe.getId().toString());
        set(m, "batchParallel", parallel);
        set(m, "batchSteamPerTickMb", 200L);
        if (m instanceof AbstractSteamProcessorMachine) set(m, "batchDurationTicks", 200);
        if (m instanceof LargeHeatStorageSteamFurnaceMachine) {
            set(m, "batchDuration", 200);
            set(m, "batchOriginWidth", get(m, "formedWidth"));
            set(m, "batchOriginHeight", get(m, "formedHeight"));
        }
    }

    private static void tick(MultiblockControllerMachine m) {
        call(m, m instanceof AbstractSteamProcessorMachine ? "processorServerTick"
                : m instanceof AbstractSteamVoidMachine ? "voidServerTick"
                : m instanceof LargeHeatStorageSteamFurnaceMachine ? "furnaceServerTick" : "crusherServerTick");
    }

    private static long demand(MultiblockControllerMachine m) {
        if (m instanceof AbstractSteamProcessorMachine || m instanceof AbstractSteamCrusherMachine) {
            return ((Number) call(m, "getBatchSteamPerTick")).longValue();
        }
        if (m instanceof LargeHeatStorageSteamFurnaceMachine) {
            return ((Number) call(m, "getCurrentBatchSteamPerTick")).longValue();
        }
        return ((Number) call(m, "currentSteamDemandPerTick")).longValue();
    }

    private static String progressField(Object m) {
        return m instanceof AbstractSteamVoidMachine ? "cycleProgress" : "batchProgress";
    }

    private static long progress(Object m) { return number(m, progressField(m)); }
    private static List<FluidHatchPartMachine> supplies(Object m) {
        return list(m, m instanceof LargeHeatStorageSteamFurnaceMachine ? "steamHatches" : "supplyHatches");
    }
    private static List<ItemBusPartMachine> outputs(Object m) { return list(m, "outputBuses"); }
    private static List<ItemStack> pending(Object m) { return list(m, "pendingOutputs"); }
    private static long steam(Object m) {
        return supplies(m).stream().mapToLong(hatch -> hatch.tank.getFluidInTank(0).getAmount()).sum();
    }
    private static void fillSteam(Object m, int amount) {
        for (var hatch : supplies(m)) hatch.tank.getStorages()[0].setFluid(GTMaterials.Steam.getFluid(amount));
    }
    private static void fillOutputs(Object m, boolean full) {
        for (var bus : outputs(m)) {
            for (int slot = 0; slot < bus.getInventory().getSlots(); slot++) {
                bus.getInventory().setStackInSlot(slot, full ? new ItemStack(Items.COBBLESTONE, 64) : ItemStack.EMPTY);
            }
        }
    }
    private static int count(List<ItemStack> stacks, Item item) {
        return stacks.stream().filter(s -> s.is(item)).mapToInt(ItemStack::getCount).sum();
    }
    private static int outputCount(Object m, Item item) {
        int total = 0;
        for (var bus : outputs(m)) for (int slot = 0; slot < bus.getInventory().getSlots(); slot++) {
            var stack = bus.getInventory().getStackInSlot(slot);
            if (stack.is(item)) total += stack.getCount();
        }
        return total;
    }
    private static int outputTotal(Object m) {
        int total = 0;
        for (var bus : outputs(m)) for (int slot = 0; slot < bus.getInventory().getSlots(); slot++) {
            total += bus.getInventory().getStackInSlot(slot).getCount();
        }
        return total;
    }
    private static void eq(GameTestHelper h, long actual, long expected, String message) {
        h.assertTrue(actual == expected, message + ": expected=" + expected + ", actual=" + actual);
    }
    private static Field field(Object target, String name) {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try {
                var field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {}
        }
        throw new AssertionError("Missing fixture field: " + name);
    }
    private static Object get(Object target, String name) {
        try { return field(target, name).get(target); }
        catch (IllegalAccessException e) { throw new AssertionError(e); }
    }
    private static long number(Object target, String name) { return ((Number) get(target, name)).longValue(); }
    @SuppressWarnings("unchecked")
    private static <T> List<T> list(Object target, String name) { return (List<T>) get(target, name); }
    private static void set(Object target, String name, Object value) {
        try {
            var field = field(target, name);
            if (field.getType() == long.class && value instanceof Number n) field.setLong(target, n.longValue());
            else field.set(target, value);
        } catch (IllegalAccessException e) { throw new AssertionError(e); }
    }
    private static Object call(Object target, String name, Object... args) {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            for (var method : type.getDeclaredMethods()) {
                if (!method.getName().equals(name) || method.getParameterCount() != args.length) continue;
                try {
                    method.setAccessible(true);
                    return method.invoke(target, args);
                } catch (InvocationTargetException e) {
                    throw new AssertionError("Engine call failed: " + name, e.getCause());
                } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
            }
        }
        throw new AssertionError("Missing fixture method: " + name);
    }
}
