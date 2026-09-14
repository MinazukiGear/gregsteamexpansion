package com.hoshino.gregsteamexpansion.gametest;

import com.hoshino.gregsteamexpansion.GregSteamExpansion;
import com.hoshino.gregsteamexpansion.difficulty.GSEDifficultyConfig;
import com.hoshino.gregsteamexpansion.machine.multiblock.LargeHeatStorageSteamFurnaceMachine;
import com.hoshino.gregsteamexpansion.machine.multiblock.part.SteamAirIntakeHatchPartMachine;
import com.hoshino.gregsteamexpansion.machine.multiblock.part.SteamExhaustHatchMachine;
import com.hoshino.gregsteamexpansion.machine.multiblock.processor.AbstractSteamProcessorMachine;
import com.hoshino.gregsteamexpansion.machine.multiblock.largecokeoven.LargeCokeOvenMachine;
import com.hoshino.gregsteamexpansion.machine.multiblock.cokeoven.GSECokeOvenMachine;
import com.hoshino.gregsteamexpansion.machine.multiblock.part.LargeCokeOvenHatchPartMachine;
import com.hoshino.gregsteamexpansion.cokeoven.CokeOvenMode;
import com.hoshino.gregsteamexpansion.machine.multiblock.processor.LargeSteamBlastFurnaceMachine;
import com.hoshino.gregsteamexpansion.machine.multiblock.voidproducer.AbstractSteamVoidMachine;
import com.hoshino.gregsteamexpansion.registry.GSEMachines;
import com.hoshino.gregsteamexpansion.registry.GSERecipeTypes;
import com.hoshino.gregsteamexpansion.registry.GSEVoidPatterns;
import com.hoshino.gregsteamexpansion.machine.multiblock.crusher.AbstractSteamCrusherMachine;

import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.capability.recipe.IRecipeCapabilityHolder;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.data.chemical.ChemicalHelper;
import com.gregtechceu.gtceu.api.data.tag.TagPrefix;
import com.gregtechceu.gtceu.api.recipe.modifier.ParallelLogic;
import com.gregtechceu.gtceu.common.data.GTBlocks;
import com.gregtechceu.gtceu.common.data.GTMaterials;
import com.gregtechceu.gtceu.common.data.GTMachines;
import com.gregtechceu.gtceu.common.data.machines.GTMultiMachines;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.gregtechceu.gtceu.common.machine.multiblock.part.FluidHatchPartMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.part.ItemBusPartMachine;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

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

    private static final ChunkPos PROCESSOR_RELOAD_CHUNK = new ChunkPos(256, 256);

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
        formed(h, GSEMachines.LARGE_STEAM_ORE_PLANT, m -> {
            if (assertDisabledOrePlantStaysIdle(h, m)) return;
            stateBoundaries(h, m);
        });
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

    @GameTest(template = "empty_32x32x32", timeoutTicks = 600)
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
    public static void crusherPersistedStateRoundTrip(GameTestHelper h) {
        formed(h, GSEMachines.LARGE_STEAM_CRUSHER, m -> {
            set(m, "workingEnabled", false);
            set(m, "largeSteamOverclockEnabled", true);
            set(m, "hasBatch", true);
            set(m, "batchRecipeId", "gregsteamexpansion:persisted_crusher");
            set(m, "batchParallel", 6);
            set(m, "batchProgress", 173);
            set(m, "batchDurationTicks", 300);
            set(m, "batchSteamPerTickMb", 1_200L);
            set(m, "batchLargeSteamOverclock", true);
            set(m, "batchTotalSteamMb", 720_000L);
            set(m, "batchInputDisplay", new ItemStack(Items.RAW_IRON, 1));
            set(m, "pendingDataVersion", (byte) 1);
            set(m, "exhaustDamageTimer", 197L);
            pending(m).add(new ItemStack(Items.IRON_INGOT, 9));

            CompoundTag saved = saveState(h, m);
            clearCrusherState(m);
            loadState(h, m, saved);

            h.assertTrue(!(boolean) call(m, "isWorkingEnabled"), "Crusher work-enabled state was not restored");
            h.assertTrue((boolean) call(m, "isLargeSteamOverclockEnabled"),
                    "Crusher overclock preference was not restored");
            h.assertTrue((boolean) get(m, "hasBatch"), "Crusher batch flag was not restored");
            h.assertTrue((boolean) call(m, "isCurrentBatchLargeSteamOverclocked"),
                    "Crusher locked batch overclock was not restored");
            h.assertTrue(get(m, "batchRecipeId").equals("gregsteamexpansion:persisted_crusher"),
                    "Crusher recipe id was not restored");
            eq(h, number(m, "batchParallel"), 6, "Crusher parallel was not restored");
            eq(h, number(m, "batchProgress"), 173, "Crusher progress was not restored");
            eq(h, number(m, "batchDurationTicks"), 300, "Crusher duration was not restored");
            eq(h, number(m, "batchSteamPerTickMb"), 1_200, "Crusher steam demand was not restored");
            eq(h, number(m, "batchTotalSteamMb"), 720_000, "Crusher steam total was not restored");
            eq(h, number(m, "exhaustDamageTimer"), 197, "Crusher exhaust timer was not restored");
            ItemStack display = (ItemStack) get(m, "batchInputDisplay");
            h.assertTrue(display.is(Items.RAW_IRON) && display.getCount() == 1,
                    "Crusher input display was not restored");
            eq(h, count(pending(m), Items.IRON_INGOT), 9, "Crusher pending output was not restored");
            h.assertTrue((boolean) call(get(m, "pendingBuffer"), "hasAny"),
                    "Crusher pending buffer detached from restored list");

            fillOutputs(m, false);
            h.assertTrue((boolean) call(m, "deliverPendingOutputs"),
                    "Crusher could not deliver restored pending output");
            eq(h, outputCount(m, Items.IRON_INGOT), 9, "Crusher duplicated or lost restored output");
        });
    }

    @GameTest(template = "empty_32x32x32", timeoutTicks = 300)
    public static void furnacePersistedStateRoundTrip(GameTestHelper h) {
        formed(h, GSEMachines.LARGE_HEAT_STORAGE_STEAM_FURNACE, m -> {
            int width = (int) number(m, "formedWidth");
            int height = (int) number(m, "formedHeight");
            set(m, "currentTemperature", 777);
            set(m, "preheatProgressUnits", 12_345L);
            set(m, "heatTimer", 2);
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
        h.assertTrue(!(boolean) call(m, "isConsumingSteam"), "Paused machine retained a stale consuming state");
        call(m, "setWorkingEnabled", true);
        h.getLevel().setBlockAndUpdate(front, Blocks.STONE.defaultBlockState());
        tick(m);
        eq(h, demand(m), 0, "Exhaust-blocked machine displayed a current steam demand");
        eq(h, progress(m), 7, "Blocked exhaust rolled back progress");
        eq(h, steam(m), before, "Blocked exhaust consumed steam");
        h.assertTrue(!(boolean) call(m, "isConsumingSteam"), "Blocked machine retained a stale consuming state");
        eq(h, number(m, "exhaustDamageTimer"), 198, "Inactive tick advanced exhaust damage");
        h.getLevel().setBlockAndUpdate(front, Blocks.AIR.defaultBlockState());

        // A failed consuming tick rolls back but keeps locked batch economics.
        fillSteam(m, 1);
        tick(m);
        eq(h, demand(m), expectedDemand, "Steam shortage hid the demand needed to resume");
        eq(h, progress(m), 1, "Steam shortage did not roll back to one tick");
        eq(h, steam(m), supplies.size(), "Shortage consumed a partial steam budget");
        h.assertTrue(!(boolean) call(m, "isConsumingSteam"), "Steam shortage reported active consumption");
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
        h.assertTrue((boolean) call(m, "isConsumingSteam"), "Recovered machine did not report active consumption");
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

    @GameTest(template = "empty_32x32x32", timeoutTicks = 300)
    public static void processorParallelCapacity(GameTestHelper h) {
        formed(h, GSEMachines.STEAM_COMPRESSOR, m -> parallelCapacity(h, m));
    }

    @GameTest(template = "empty_32x32x32", timeoutTicks = 300)
    public static void crusherParallelCapacity(GameTestHelper h) {
        formed(h, GSEMachines.STEAM_CRUSHER, m -> parallelCapacity(h, m));
    }

    @GameTest(template = "empty_32x32x32", timeoutTicks = 300)
    public static void processorMultiProductCapacityCompetition(GameTestHelper h) {
        formed(h, GSEMachines.STEAM_COMPRESSOR, m -> multiProductCapacityCompetition(h, m));
    }

    @GameTest(template = "empty_32x32x32", timeoutTicks = 300)
    public static void crusherMultiProductCapacityCompetition(GameTestHelper h) {
        formed(h, GSEMachines.STEAM_CRUSHER, m -> multiProductCapacityCompetition(h, m));
    }

    private static void multiProductCapacityCompetition(GameTestHelper h, MultiblockControllerMachine m) {
        fillOutputs(m, true);
        ItemBusPartMachine bus = outputs(m).get(0);
        h.assertTrue(bus.getInventory().getSlots() >= 2, "Fixture output bus has fewer than two slots");
        bus.getInventory().setStackInSlot(0, ItemStack.EMPTY);
        bus.getInventory().setStackInSlot(1, ItemStack.EMPTY);
        GTRecipe recipe = GTRecipeTypes.MACERATOR_RECIPES.recipeBuilder(
                        GregSteamExpansion.id("engine_multi_product_capacity"))
                .outputItems(new ItemStack(Items.IRON_INGOT, 40))
                .outputItems(new ItemStack(Items.GOLD_INGOT))
                .duration(20).EUt(8).buildRawRecipe();

        eq(h, (int) call(m, "largestParallelThatFits", recipe, 2), 1,
                "Oversized first product hid the slot required by the second product");
        h.assertTrue(bus.getInventory().getStackInSlot(0).isEmpty()
                        && bus.getInventory().getStackInSlot(1).isEmpty(),
                "Multi-product capacity simulation mutated output inventory");
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
    public static void largeSteamOverclockLocksOnlyAtCrusherBatchStart(GameTestHelper h) {
        formed(h, GSEMachines.LARGE_STEAM_CRUSHER, m -> {
            AbstractSteamCrusherMachine crusher = (AbstractSteamCrusherMachine) m;
            eq(h, replaceSteamSupplyHatches(h, m, 1), 1,
                    "Large crusher fixture did not replace exactly one supply hatch");
            h.assertTrue(crusher.hasLargeSteamSupplyHatch(),
                    "Reformed crusher did not collect the Large Steam Supply Hatch");

            ItemBusPartMachine input = m.getParts().stream()
                    .filter(ItemBusPartMachine.class::isInstance)
                    .map(ItemBusPartMachine.class::cast)
                    .filter(bus -> bus.getInventory().getHandlerIO() == IO.IN)
                    .findFirst().orElseThrow();
            ItemStack crusherInput = migratedCrusherInput();
            fillOutputs(m, false);
            crusher.setLargeSteamOverclockEnabled(true);
            input.getInventory().setStackInSlot(0, crusherInput.copy());
            call(m, "tryStartBatch");
            h.assertTrue((boolean) get(m, "hasBatch"),
                    "Overclock-enabled crusher did not start a migrated ore-crushing recipe");
            h.assertTrue(crusher.isCurrentBatchLargeSteamOverclocked(),
                    "Crusher did not lock overclock at batch start");
            eq(h, number(m, "batchDurationTicks"), 300,
                    "Crusher did not halve its fixed 600-tick duration");
            eq(h, number(m, "batchSteamPerTickMb"), 600,
                    "Crusher did not triple its 200 mB/t single-parallel demand");
            eq(h, number(m, "batchTotalSteamMb"), 180_000,
                    "Crusher locked the wrong overclocked steam total");

            crusher.setLargeSteamOverclockEnabled(false);
            h.assertTrue(crusher.isCurrentBatchLargeSteamOverclocked(),
                    "Disabling the toggle rewrote the running crusher batch");
            fillSteam(m, 32_000);
            long steamBeforeTick = steam(m);
            tick(m);
            eq(h, steamBeforeTick - steam(m), 600,
                    "Running overclocked crusher batch did not draw its locked demand");

            call(m, "completeBatch");
            input.getInventory().setStackInSlot(0, crusherInput.copy());
            call(m, "tryStartBatch");
            h.assertTrue((boolean) get(m, "hasBatch"),
                    "Crusher did not start the next batch after disabling overclock");
            h.assertTrue(!crusher.isCurrentBatchLargeSteamOverclocked(),
                    "Disabled overclock remained active for the next crusher batch");
            eq(h, number(m, "batchDurationTicks"), 600,
                    "Normal crusher batch kept the overclocked duration");
            eq(h, number(m, "batchSteamPerTickMb"), 200,
                    "Normal crusher batch kept the overclocked demand");
        });
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
            h.assertTrue(pending(m).isEmpty() && GSESteamEngineTests.<FluidStack>list(m, "pendingFluids").isEmpty(),
                    "Completed fluid recipe left deliverable output pending");
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
            List<IRecipeCapabilityHolder> scopes = list(m, "inputScopes");
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

    @GameTest(template = "empty_32x32x32", timeoutTicks = 300)
    public static void blastFurnaceClosesWroughtIronAndSteelChain(GameTestHelper h) {
        formed(h, GSEMachines.LARGE_STEAM_BLAST_FURNACE, controller -> {
            LargeSteamBlastFurnaceMachine machine = (LargeSteamBlastFurnaceMachine) controller;
            eq(h, replaceSteamSupplyHatches(h, machine, 1), 1,
                    "Blast-furnace fixture did not install exactly one large steam supply hatch");
            ItemBusPartMachine input = machine.getParts().stream()
                    .filter(ItemBusPartMachine.class::isInstance)
                    .map(ItemBusPartMachine.class::cast)
                    .filter(bus -> bus.getInventory().getHandlerIO() == IO.IN)
                    .findFirst().orElseThrow();
            List<SteamAirIntakeHatchPartMachine> intakes = list(machine, "airIntakeHatches");
            h.assertTrue(!intakes.isEmpty(), "Blast-furnace fixture lacks an air intake hatch");
            intakes.get(0).tank.getStorages()[0].setFluid(GTMaterials.Air.getFluid(64_000));
            fillOutputs(machine, false);

            List<GTRecipe> upstreamSteel = GTRecipeTypes.PRIMITIVE_BLAST_FURNACE_RECIPES
                    .getRecipesInCategory(GTRecipeTypes.PRIMITIVE_BLAST_FURNACE_RECIPES.getCategory()).stream()
                    .filter(recipe -> recipe.getId().getNamespace().equals("gtceu"))
                    .filter(recipe -> recipe.getId().getPath().contains("steel_from_"))
                    .toList();
            eq(h, upstreamSteel.size(), 18,
                    "Primitive blast furnace upstream steel recipe inventory changed");
            for (GTRecipe recipe : upstreamSteel) {
                h.assertTrue((boolean) call(machine, "acceptsRecipe", recipe),
                        "Large steam blast furnace rejected upstream recipe " + recipe.getId());
                h.assertTrue((boolean) call(machine, "passesVoltageGate", recipe),
                        "Large steam blast furnace voltage-gated EU-less recipe " + recipe.getId());
            }

            GTRecipe wroughtRecipe = recipeEndingWith(
                    GTRecipeTypes.PRIMITIVE_BLAST_FURNACE_RECIPES,
                    "wrought_iron_from_dust_coke_dust");
            input.getInventory().setStackInSlot(0,
                    ChemicalHelper.get(TagPrefix.dust, GTMaterials.Iron, 4));
            input.getInventory().setStackInSlot(1,
                    ChemicalHelper.get(TagPrefix.dust, GTMaterials.Coke, 4));
            runBlastRecipe(h, machine, wroughtRecipe, input,
                    ChemicalHelper.get(TagPrefix.ingot, GTMaterials.WroughtIron).getItem());

            fillOutputs(machine, false);
            GTRecipe steelRecipe = recipeEndingWith(
                    GTRecipeTypes.PRIMITIVE_BLAST_FURNACE_RECIPES,
                    "steel_from_coke_dust_wrought");
            input.getInventory().setStackInSlot(0,
                    ChemicalHelper.get(TagPrefix.ingot, GTMaterials.WroughtIron, 4));
            input.getInventory().setStackInSlot(1,
                    ChemicalHelper.get(TagPrefix.dust, GTMaterials.Coke, 4));
            runBlastRecipe(h, machine, steelRecipe,
                    input, ChemicalHelper.get(TagPrefix.ingot, GTMaterials.Steel).getItem());
        });
    }

    @GameTest(template = "empty_32x32x32", timeoutTicks = 300)
    public static void blastFurnaceFullLoadUsesFourLargeSuppliesAndEightIntakes(GameTestHelper h) {
        formed(h, GSEMachines.LARGE_STEAM_BLAST_FURNACE, controller -> {
            LargeSteamBlastFurnaceMachine machine = (LargeSteamBlastFurnaceMachine) controller;
            configureBlastFurnaceFullLoadHatches(h, machine);

            List<FluidHatchPartMachine> supplies = supplies(machine);
            long largeSupplies = supplies.stream()
                    .filter(hatch -> hatch.self().getDefinition() == GSEMachines.LARGE_STEAM_SUPPLY_HATCH)
                    .count();
            eq(h, largeSupplies, 4, "Full-load fixture did not collect four large steam supply hatches");
            List<SteamAirIntakeHatchPartMachine> intakes = list(machine, "airIntakeHatches");
            eq(h, intakes.size(), 8, "Full-load fixture did not collect eight air intake hatches");

            ItemBusPartMachine input = machine.getParts().stream()
                    .filter(ItemBusPartMachine.class::isInstance)
                    .map(ItemBusPartMachine.class::cast)
                    .filter(bus -> bus.getInventory().getHandlerIO() == IO.IN)
                    .findFirst().orElseThrow();
            h.assertTrue(input.getInventory().getSlots() >= 4,
                    "Full-load fixture input bus lacks four slots for split stacks");
            fillOutputs(machine, false);
            input.getInventory().setStackInSlot(0,
                    ChemicalHelper.get(TagPrefix.dust, GTMaterials.Iron, 64));
            input.getInventory().setStackInSlot(1,
                    ChemicalHelper.get(TagPrefix.dust, GTMaterials.Iron, 32));
            input.getInventory().setStackInSlot(2,
                    ChemicalHelper.get(TagPrefix.dust, GTMaterials.Coke, 64));
            input.getInventory().setStackInSlot(3,
                    ChemicalHelper.get(TagPrefix.dust, GTMaterials.Coke, 32));

            GTRecipe recipe = recipeEndingWith(
                    GTRecipeTypes.PRIMITIVE_BLAST_FURNACE_RECIPES,
                    "wrought_iron_from_dust_coke_dust");
            h.assertTrue((boolean) call(machine, "tryStartRecipe", recipe),
                    "Large steam blast furnace did not start its 96-parallel real recipe");
            eq(h, machine.getBatchParallel(), 96, "Blast furnace did not lock its maximum parallel");
            eq(h, machine.getBatchDuration(), 240, "Full-load recipe did not apply 0.4x duration");
            eq(h, machine.getBatchSteamPerTick(), 19_200,
                    "Full-load recipe locked the wrong steam demand");
            for (int slot = 0; slot < 4; slot++) {
                h.assertTrue(input.getInventory().getStackInSlot(slot).isEmpty(),
                        "Full-load recipe left input in split stack slot " + slot);
            }

            for (FluidHatchPartMachine hatch : supplies) {
                boolean large = hatch.self().getDefinition() == GSEMachines.LARGE_STEAM_SUPPLY_HATCH;
                hatch.tank.getStorages()[0].setFluid(
                        large ? GTMaterials.Steam.getFluid(4_800) : FluidStack.EMPTY);
            }
            for (SteamAirIntakeHatchPartMachine intake : intakes) {
                intake.tank.getStorages()[0].setFluid(GTMaterials.Air.getFluid(48));
            }
            eq(h, steam(machine), 19_200, "Full-load fixture did not stage exactly one steam tick");
            eq(h, air(machine), 384, "Full-load fixture did not stage exactly one blast-air tick");

            call(machine, "runBatchTick");
            eq(h, machine.getBatchProgress(), 1, "Full-load batch did not advance exactly one tick");
            eq(h, steam(machine), 0, "Four large supply hatches did not atomically provide 19,200 mB");
            eq(h, air(machine), 0, "Eight air intakes did not atomically provide 384 mB");
        });
    }

    @GameTest(template = "empty_32x32x32", timeoutTicks = 300)
    public static void blastFurnaceAcceptsCapabilityInputPart(GameTestHelper h) {
        var definition = GSEMachines.LARGE_STEAM_BLAST_FURNACE;
        var m = GSEStructureTestUtils.placeShape(h, definition, definition.getMatchingShapes().get(0));
        h.assertTrue(m != null, "Missing blast-furnace fixture controller");

        var creativeInput = ForgeRegistries.BLOCKS.getValue(
                new ResourceLocation("gtmthings", "creative_item_input_bus"));
        h.assertTrue(creativeInput != null && creativeInput != Blocks.AIR,
                "Development GTM Things creative item input bus is unavailable");

        int replaced = 0;
        for (BlockPos pos : BlockPos.betweenClosed(
                m.getPos().offset(-15, 0, -15), m.getPos().offset(15, 15, 15))) {
            if (h.getLevel().getBlockState(pos).is(GTMachines.STEAM_IMPORT_BUS.getBlock())) {
                h.getLevel().setBlockAndUpdate(pos, creativeInput.defaultBlockState());
                replaced++;
            }
        }
        eq(h, replaced, 1, "Expected exactly one representative steam input bus");

        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(m.isFormed(),
                        "Capability-compatible creative item input bus did not form the blast furnace"))
                .thenExecute(() -> h.assertTrue(m.getParts().stream()
                                .anyMatch(part -> part.self().getDefinition().getId().toString()
                                        .equals("gtmthings:creative_item_input_bus")),
                        "Formed structure did not retain the creative item input bus"))
                .thenSucceed();
    }

    @GameTest(template = "empty_32x32x32", timeoutTicks = 300)
    public static void smallCrusherAcceptsAutomationItemInterfaces(GameTestHelper h) {
        assertAutomationItemInterfaces(h, GSEMachines.STEAM_CRUSHER);
    }

    @GameTest(template = "empty_32x32x32", timeoutTicks = 300)
    public static void compressorAcceptsAutomationItemInterfaces(GameTestHelper h) {
        assertAutomationItemInterfaces(h, GSEMachines.STEAM_COMPRESSOR);
    }

    @GameTest(template = "empty_32x32x32", timeoutTicks = 300)
    public static void chemicalBathAcceptsAutomationItemInterfaces(GameTestHelper h) {
        assertAutomationItemInterfaces(h, GSEMachines.STEAM_CHEMICAL_BATH);
    }

    @GameTest(template = "empty_32x32x32", timeoutTicks = 300)
    public static void smallCentrifugeAcceptsAutomationItemInterfaces(GameTestHelper h) {
        assertAutomationItemInterfaces(h, GSEMachines.STEAM_CENTRIFUGE);
    }

    private static void assertAutomationItemInterfaces(GameTestHelper h,
                                                       MultiblockMachineDefinition definition) {
        var m = GSEStructureTestUtils.placeShape(h, definition, definition.getMatchingShapes().get(0));
        h.assertTrue(m != null, "Missing fixture controller for " + definition.getId());
        BlockPos inputPos = findBlock(h, m, GTMachines.STEAM_IMPORT_BUS.getBlock());
        BlockPos outputPos = findBlock(h, m, GTMachines.STEAM_EXPORT_BUS.getBlock());
        h.assertTrue(inputPos != null, "Fixture has no steam item input bus: " + definition.getId());
        h.assertTrue(outputPos != null, "Fixture has no steam item output bus: " + definition.getId());

        var inputs = List.of(
                requiredBlock(h, "gtmthings:creative_item_input_bus"),
                requiredBlock(h, "gtceu:me_input_bus"),
                requiredBlock(h, "gtceu:me_pattern_buffer"));
        var outputs = List.of(
                requiredBlock(h, "gtceu:me_output_bus"),
                requiredBlock(h, "gtmthings:me_export_buffer"));
        for (var output : outputs) {
            h.getLevel().setBlockAndUpdate(outputPos, output.defaultBlockState());
            for (var input : inputs) {
                if (m.isFormed()) m.onStructureInvalid();
                h.getLevel().setBlockAndUpdate(inputPos, input.defaultBlockState());
                h.assertTrue(m.checkPattern(), definition.getId() + " rejected input "
                        + ForgeRegistries.BLOCKS.getKey(input) + " with output "
                        + ForgeRegistries.BLOCKS.getKey(output));
                m.onStructureFormed();
                h.assertTrue(m.isFormed(), definition.getId() + " did not form after compatibility match");
            }
        }
        h.succeed();
    }

    @GameTest(template = "empty_32x32x32", timeoutTicks = 300)
    public static void largeCokeOvenAcceptsAutomationItemInterfaces(GameTestHelper h) {
        var definition = GSEMachines.LARGE_COKE_OVEN;
        var m = (LargeCokeOvenMachine) GSEStructureTestUtils.placeShape(
                h, definition, definition.getMatchingShapes().get(1));
        h.assertTrue(m != null, "Missing large coke-oven fixture controller");
        List<BlockPos> hatches = new java.util.ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(
                m.getPos().offset(-8, 0, -8), m.getPos().offset(8, 8, 8))) {
            if (h.getLevel().getBlockState(pos).is(GSEMachines.LARGE_COKE_OVEN_HATCH.getBlock())) {
                hatches.add(pos.immutable());
            }
        }
        h.assertTrue(hatches.size() == 3, "Automated coke-oven fixture must contain three bespoke hatches");
        if (m.isFormed()) m.onStructureInvalid();
        var fluidMachine = MetaMachine.getMachine(h.getLevel(), hatches.get(2));
        h.assertTrue(fluidMachine instanceof LargeCokeOvenHatchPartMachine,
                "Remaining coke-oven fluid hatch machine missing");
        set(fluidMachine, "mode", CokeOvenMode.FLUID_OUTPUT);
        h.getLevel().setBlockAndUpdate(hatches.get(0),
                requiredBlock(h, "gtceu:me_pattern_buffer").defaultBlockState());
        h.getLevel().setBlockAndUpdate(hatches.get(1),
                requiredBlock(h, "gtmthings:me_export_buffer").defaultBlockState());
        h.assertTrue(m.checkPattern(), "Large coke oven rejected ME pattern input and ME output");
        m.onStructureFormed();
        h.assertTrue(m.isFormed(), "Large coke oven did not form with automation item interfaces");
        h.assertTrue(m.getStandardOutputBuses().size() == 1,
                "Large coke oven did not collect its ME output bus");
        h.succeed();
    }

    @GameTest(template = "empty_32x32x32", timeoutTicks = 300)
    public static void regularCokeOvenAcceptsAutomationItemInterfaces(GameTestHelper h) {
        var definition = GTMultiMachines.COKE_OVEN;
        var m = (GSECokeOvenMachine) GSEStructureTestUtils.placeShape(
                h, definition, definition.getMatchingShapes().get(1));
        h.assertTrue(m != null, "Missing regular coke-oven fixture controller");
        List<BlockPos> hatches = new java.util.ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(
                m.getPos().offset(-4, -4, -4), m.getPos().offset(4, 4, 4))) {
            if (h.getLevel().getBlockState(pos).is(GTMachines.COKE_OVEN_HATCH.getBlock())) {
                hatches.add(pos.immutable());
            }
        }
        h.assertTrue(hatches.size() == 3, "Automated regular coke-oven fixture must contain three hatches");
        var inputs = List.of(
                requiredBlock(h, "gtmthings:creative_item_input_bus"),
                requiredBlock(h, "gtceu:me_input_bus"),
                requiredBlock(h, "gtceu:me_pattern_buffer"));
        var outputs = List.of(
                requiredBlock(h, "gtceu:me_output_bus"),
                requiredBlock(h, "gtmthings:me_export_buffer"));
        for (var output : outputs) {
            h.getLevel().setBlockAndUpdate(hatches.get(1), output.defaultBlockState());
            for (var input : inputs) {
                if (m.isFormed()) m.onStructureInvalid();
                h.getLevel().setBlockAndUpdate(hatches.get(0), input.defaultBlockState());
                h.assertTrue(m.checkPattern(), "Regular coke oven rejected input "
                        + ForgeRegistries.BLOCKS.getKey(input) + " with output "
                        + ForgeRegistries.BLOCKS.getKey(output));
                m.onStructureFormed();
                h.assertTrue(m.isFormed(), "Regular coke oven did not form after compatibility match");
                h.assertTrue(m.getStandardOutputBuses().size() == 1,
                        "Regular coke oven did not collect its automation output bus");
            }
        }
        h.succeed();
    }

    private static net.minecraft.world.level.block.Block requiredBlock(GameTestHelper h, String id) {
        var block = ForgeRegistries.BLOCKS.getValue(ResourceLocation.tryParse(id));
        h.assertTrue(block != null && block != Blocks.AIR, "Required compatibility block missing: " + id);
        return block;
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

    @Nullable
    private static BlockPos findBlock(GameTestHelper h, MultiblockControllerMachine m,
                                      net.minecraft.world.level.block.Block block) {
        for (BlockPos pos : BlockPos.betweenClosed(
                m.getPos().offset(-15, -15, -15), m.getPos().offset(15, 15, 15))) {
            if (h.getLevel().getBlockState(pos).is(block)) return pos.immutable();
        }
        return null;
    }

    private static int replaceSteamSupplyHatches(GameTestHelper h, MultiblockControllerMachine m,
                                                  int maximum) {
        if (m.isFormed()) m.onStructureInvalid();
        List<BlockPos> ordinaryHatches = new java.util.ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(
                m.getPos().offset(-15, -15, -15), m.getPos().offset(15, 15, 15))) {
            if (h.getLevel().getBlockState(pos).is(GSEMachines.STEAM_SUPPLY_HATCH.getBlock())) {
                ordinaryHatches.add(pos.immutable());
            }
        }
        int replaced = Math.min(maximum, ordinaryHatches.size());
        for (int i = 0; i < replaced; i++) {
            h.getLevel().setBlockAndUpdate(ordinaryHatches.get(i),
                    GSEMachines.LARGE_STEAM_SUPPLY_HATCH.getBlock().defaultBlockState());
        }
        h.assertTrue(replaced > 0, "Fixture has no ordinary Steam Supply Hatch to replace");
        h.assertTrue(m.checkPattern(),
                "Large Steam Supply Hatch replacement did not reform " + m.getDefinition().getId());
        m.onStructureFormed();
        h.assertTrue(m.isFormed(), "Controller remained invalid after Large Steam Supply Hatch replacement");
        return replaced;
    }

    private static void configureBlastFurnaceFullLoadHatches(GameTestHelper h,
                                                              LargeSteamBlastFurnaceMachine machine) {
        if (machine.isFormed()) machine.onStructureInvalid();
        List<BlockPos> ordinarySupplies = new java.util.ArrayList<>();
        int existingIntakes = 0;
        for (BlockPos pos : BlockPos.betweenClosed(
                machine.getPos().offset(-15, -15, -15), machine.getPos().offset(15, 15, 15))) {
            if (h.getLevel().getBlockState(pos).is(GSEMachines.STEAM_SUPPLY_HATCH.getBlock())) {
                ordinarySupplies.add(pos.immutable());
            } else if (h.getLevel().getBlockState(pos).is(GSEMachines.STEAM_AIR_INTAKE_HATCH.getBlock())) {
                existingIntakes++;
            }
        }
        h.assertTrue(ordinarySupplies.size() >= 4,
                "Blast-furnace preview lacks four supply hatches to upgrade");
        for (int i = 0; i < 4; i++) {
            h.getLevel().setBlockAndUpdate(ordinarySupplies.get(i),
                    GSEMachines.LARGE_STEAM_SUPPLY_HATCH.getBlock().defaultBlockState());
        }

        int requiredIntakes = 8 - existingIntakes;
        h.assertTrue(requiredIntakes >= 0, "Blast-furnace preview already exceeds eight air intakes");
        int installedIntakes = 0;
        for (BlockPos pos : BlockPos.betweenClosed(
                machine.getPos().offset(-15, -15, -15), machine.getPos().offset(15, 15, 15))) {
            if (h.getLevel().getBlockState(pos).is(GTBlocks.CASING_PRIMITIVE_BRICKS.get())) {
                h.getLevel().setBlockAndUpdate(pos,
                        GSEMachines.STEAM_AIR_INTAKE_HATCH.getBlock().defaultBlockState());
                if (++installedIntakes == requiredIntakes) break;
            }
        }
        eq(h, installedIntakes, requiredIntakes,
                "Could not install all full-load air intake hatches in legal wall positions");
        h.assertTrue(machine.checkPattern(),
                "Four large supplies and eight air intakes did not reform the blast furnace");
        machine.onStructureFormed();
        h.assertTrue(machine.isFormed(), "Full-load blast-furnace fixture remained invalid");
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

    private static GTRecipe recipeEndingWith(GTRecipeType type, String suffix) {
        List<GTRecipe> matches = type.getRecipesInCategory(type.getCategory()).stream()
                .filter(recipe -> recipe.getId().getPath().endsWith(suffix))
                .toList();
        if (matches.size() != 1) {
            throw new AssertionError("Expected one recipe ending with " + suffix + ", found " + matches.size());
        }
        return matches.get(0);
    }

    private static void runBlastRecipe(GameTestHelper h, LargeSteamBlastFurnaceMachine machine,
                                       GTRecipe recipe, ItemBusPartMachine input, Item expectedOutput) {
        fillSteam(machine, 256_000);
        long steamBefore = steam(machine);
        long airBefore = air(machine);
        h.assertTrue((boolean) call(machine, "tryStartRecipe", recipe),
                "Large steam blast furnace did not start " + recipe.getId());
        eq(h, machine.getBatchParallel(), 4, "Blast recipe did not lock four parallels");
        eq(h, machine.getBatchDuration(), 240, "Blast recipe did not apply its 0.4x duration");
        eq(h, machine.getBatchSteamPerTick(), 800, "Blast recipe locked the wrong steam demand");
        h.assertTrue(input.getInventory().getStackInSlot(0).isEmpty()
                        && input.getInventory().getStackInSlot(1).isEmpty(),
                "Blast recipe did not consume both inputs atomically at batch start");

        for (int tick = 1; tick <= 240; tick++) {
            call(machine, "runBatchTick");
            if (tick < 240) {
                eq(h, machine.getBatchProgress(), tick,
                        "Blast recipe did not advance exactly once on supplied tick " + tick);
                h.assertTrue(!machine.getBatchRecipeId().isEmpty(),
                        "Blast recipe completed before its locked duration at tick " + tick);
            }
        }
        h.assertTrue(machine.getBatchRecipeId().isEmpty(),
                "Blast recipe did not complete after its locked duration");
        eq(h, outputCount(machine, expectedOutput), 4,
                "Blast recipe did not deliver its guaranteed parallel output");
        eq(h, steamBefore - steam(machine), 192_000,
                "Blast recipe consumed the wrong total steam");
        eq(h, airBefore - air(machine), 3_840,
                "Blast recipe consumed the wrong total blast air");
    }

    private static ItemStack migratedCrusherInput() {
        for (GTRecipe recipe : GSERecipeTypes.ORE_CRUSHING_RECIPES.getRecipesInCategory(
                GSERecipeTypes.ORE_CRUSHING_RECIPES.getCategory())) {
            var inputs = recipe.inputs.get(ItemRecipeCapability.CAP);
            if (inputs == null || inputs.size() != 1 || !(inputs.get(0).content instanceof Ingredient ingredient)) {
                continue;
            }
            for (ItemStack stack : ingredient.getItems()) {
                if (!stack.isEmpty()) return stack.copyWithCount(1);
            }
        }
        throw new AssertionError("No concrete input found in the migrated ore-crushing recipe table");
    }

    private static CompoundTag saveState(GameTestHelper h, MultiblockControllerMachine m) {
        BlockEntity blockEntity = h.getLevel().getBlockEntity(m.getPos());
        h.assertTrue(blockEntity != null, "Missing controller block entity");
        CompoundTag tag = blockEntity.saveWithoutMetadata();
        h.assertTrue(!tag.isEmpty(), "Controller block entity persisted no state");
        return tag;
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

    private static void loadState(GameTestHelper h, MultiblockControllerMachine m, CompoundTag tag) {
        BlockEntity blockEntity = h.getLevel().getBlockEntity(m.getPos());
        h.assertTrue(blockEntity != null, "Missing controller block entity");
        blockEntity.load(tag);
    }

    private static void clearProcessorState(MultiblockControllerMachine m) {
        set(m, "workingEnabled", true);
        set(m, "largeSteamOverclockEnabled", false);
        set(m, "hasBatch", false);
        set(m, "batchRecipeId", "");
        set(m, "batchParallel", 0);
        set(m, "batchProgress", 0);
        set(m, "batchDurationTicks", 0);
        set(m, "batchSteamPerTickMb", 0L);
        set(m, "batchLargeSteamOverclock", false);
        set(m, "batchTotalSteamMb", 0L);
        set(m, "batchOutputMultiplier", 1.0F);
        set(m, "batchInputDisplay", ItemStack.EMPTY);
        set(m, "preferredRecipeId", "");
        pending(m).clear();
        list(m, "pendingFluids").clear();
    }

    private static void clearCrusherState(MultiblockControllerMachine m) {
        set(m, "workingEnabled", true);
        set(m, "largeSteamOverclockEnabled", false);
        set(m, "hasBatch", false);
        set(m, "batchRecipeId", "");
        set(m, "batchParallel", 0);
        set(m, "batchProgress", 0);
        set(m, "batchDurationTicks", 600);
        set(m, "batchSteamPerTickMb", 0L);
        set(m, "batchLargeSteamOverclock", false);
        set(m, "batchTotalSteamMb", 0L);
        set(m, "batchInputDisplay", ItemStack.EMPTY);
        set(m, "exhaustDamageTimer", 0L);
        pending(m).clear();
    }

    private static void clearFurnaceState(MultiblockControllerMachine m) {
        set(m, "formedWidth", 0);
        set(m, "formedHeight", 0);
        set(m, "currentTemperature", LargeHeatStorageSteamFurnaceMachine.COLD_TEMPERATURE);
        set(m, "preheatProgressUnits", 0L);
        set(m, "heatTimer", 0);
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
    private static long air(Object m) {
        return GSESteamEngineTests.<SteamAirIntakeHatchPartMachine>list(m, "airIntakeHatches").stream()
                .mapToLong(hatch -> hatch.tank.getFluidInTank(0).getAmount()).sum();
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

    private static long fluidAmount(List<FluidStack> stacks, FluidStack expected) {
        return stacks.stream().filter(stack -> stack.isFluidEqual(expected)).mapToLong(FluidStack::getAmount).sum();
    }

    private static long fluidOutputAmount(Object m, FluidStack expected) {
        long total = 0;
        for (FluidHatchPartMachine hatch : GSESteamEngineTests.<FluidHatchPartMachine>list(m, "fluidOutputHatches")) {
            for (int tank = 0; tank < hatch.tank.getTanks(); tank++) {
                FluidStack stack = hatch.tank.getFluidInTank(tank);
                if (stack.isFluidEqual(expected)) total += stack.getAmount();
            }
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
