package com.hoshino.gregsteamexpansion.gametest;

import com.hoshino.gregsteamexpansion.GregSteamExpansion;
import com.hoshino.gregsteamexpansion.machine.multiblock.crusher.AbstractSteamCrusherMachine;
import com.hoshino.gregsteamexpansion.registry.GSEMachines;
import com.hoshino.gregsteamexpansion.registry.GSERecipeTypes;

import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.data.chemical.ChemicalHelper;
import com.gregtechceu.gtceu.api.data.tag.TagPrefix;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.common.data.GTMaterials;
import com.gregtechceu.gtceu.common.machine.multiblock.part.ItemBusPartMachine;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;

import static com.hoshino.gregsteamexpansion.gametest.GSESteamEngineTestSupport.*;

/** Runtime contracts for small and large steam crushers. */
@GameTestHolder(GregSteamExpansion.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GSECrusherTests {
    private GSECrusherTests() {}

    @GameTest(template = "empty_32x32x32", timeoutTicks = 300)
    public static void crusherStateBoundaries(GameTestHelper h) {
        formed(h, GSEMachines.LARGE_STEAM_CRUSHER, m -> stateBoundaries(h, m));
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
    public static void crusherPendingAndSettlement(GameTestHelper h) {
        formed(h, GSEMachines.STEAM_CRUSHER, m -> {
            assertCrusherSearchWakeup(h, m);
            assertCrusherJadeSnapshot(h, m);
            settlement(h, m);
        });
    }

    @GameTest(template = "empty_32x32x32", timeoutTicks = 300)
    public static void crusherParallelCapacity(GameTestHelper h) {
        formed(h, GSEMachines.STEAM_CRUSHER, m -> parallelCapacity(h, m));
    }

    @GameTest(template = "empty_32x32x32", timeoutTicks = 300)
    public static void crusherMultiProductCapacityCompetition(GameTestHelper h) {
        formed(h, GSEMachines.STEAM_CRUSHER, m -> multiProductCapacityCompetition(h, m));
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

    private static void assertCrusherSearchWakeup(GameTestHelper h, MultiblockControllerMachine m) {
        ItemBusPartMachine input = m.getParts().stream()
                .filter(ItemBusPartMachine.class::isInstance)
                .map(ItemBusPartMachine.class::cast)
                .filter(bus -> bus.getInventory().getHandlerIO() == IO.IN)
                .findFirst().orElseThrow();

        // With no notification and a distant fallback deadline, an idle tick
        // must not rescan the input handlers.
        set(m, "recipeSearchDirty", false);
        set(m, "nextRecipeSearchTick", Long.MAX_VALUE);
        tick(m);
        h.assertTrue(!(boolean) get(m, "recipeSearchDirty"),
                "Idle crusher dirtied its search without an input change");
        eq(h, number(m, "nextRecipeSearchTick"), Long.MAX_VALUE,
                "Idle crusher ignored the scheduled fallback deadline");

        input.getInventory().setStackInSlot(0, migratedCrusherInput());
        h.assertTrue((boolean) get(m, "recipeSearchDirty"),
                "Crusher input change did not wake recipe search");
        input.getInventory().setStackInSlot(0, ItemStack.EMPTY);
    }

    private static void assertCrusherJadeSnapshot(GameTestHelper h, MultiblockControllerMachine controller) {
        AbstractSteamCrusherMachine crusher = (AbstractSteamCrusherMachine) controller;
        seedBatch(controller, itemRecipe(), 2);
        set(controller, "batchInputDisplay", new ItemStack(Items.DIAMOND));
        set(controller, "batchProgress", 7);
        set(controller, "batchDurationTicks", 123);
        pending(controller).add(new ItemStack(Items.IRON_INGOT, 3));

        CompoundTag serverData = new CompoundTag();
        Object provider = jadeProvider("CrusherProvider");
        BlockAccessor accessor = jadeAccessor(controller, serverData);
        @SuppressWarnings("unchecked")
        IServerDataProvider<BlockAccessor> serverProvider = (IServerDataProvider<BlockAccessor>) provider;
        serverProvider.appendServerData(serverData, accessor);

        h.assertTrue(serverData.contains("GregSteamExpansionCrusher", Tag.TAG_COMPOUND),
                "Jade server provider omitted the crusher snapshot");
        CompoundTag data = serverData.getCompound("GregSteamExpansionCrusher");
        List<String> commonKeys = List.of(
                "statusId", "hasBatch", "inputItem", "progress", "duration", "parallel", "parallelCap",
                "steamTotal", "steamCap", "steamPerTick", "steamInputLimit", "consuming", "pendingTotal",
                "pendingKinds");
        h.assertTrue(data.size() == commonKeys.size() && data.getAllKeys().containsAll(commonKeys),
                "Crusher Jade snapshot changed its common field schema: " + data.getAllKeys());
        h.assertTrue(data.getString("statusId").equals(crusher.getStatusId()),
                "Crusher Jade snapshot changed the status id");
        h.assertTrue(data.getBoolean("hasBatch"), "Crusher Jade snapshot lost the active-batch marker");
        h.assertTrue(!data.contains("recipeId"), "Crusher Jade snapshot exposed the internal recipe id");
        h.assertTrue(ItemStack.isSameItemSameTags(ItemStack.of(data.getCompound("inputItem")),
                        new ItemStack(Items.DIAMOND)),
                "Crusher Jade snapshot changed the display input item");
        eq(h, data.getInt("progress"), 7, "Crusher Jade snapshot changed progress");
        eq(h, data.getInt("duration"), 123, "Crusher Jade snapshot changed duration");
        eq(h, data.getInt("parallel"), 2, "Crusher Jade snapshot changed parallel");
        eq(h, data.getInt("parallelCap"), crusher.maximumParallel(),
                "Crusher Jade snapshot changed the parallel cap");
        eq(h, data.getLong("pendingTotal"), 3, "Crusher Jade snapshot changed pending output count");
        eq(h, data.getInt("pendingKinds"), 1, "Crusher Jade snapshot changed pending output kinds");

        List<Component> tooltipLines = new ArrayList<>();
        ((IBlockComponentProvider) provider).appendTooltip(jadeTooltip(tooltipLines), accessor, null);
        h.assertTrue(tooltipContains(tooltipLines, Component.translatable(
                        "gregsteamexpansion.jade.steam_crusher.recipe",
                        new ItemStack(Items.DIAMOND).getHoverName())),
                "Crusher Jade tooltip did not use the localized input name");
        h.assertTrue(tooltipLines.stream().noneMatch(line -> line.getString().contains(crusher.getBatchRecipeId())),
                "Crusher Jade tooltip exposed the current recipe id");
        h.assertTrue(tooltipTranslationKeys(tooltipLines).equals(List.of(
                        "gregsteamexpansion.jade.steam_crusher.status",
                        "gregsteamexpansion.jade.steam_crusher.recipe",
                        "gtceu.jade.progress_sec",
                        "gregsteamexpansion.jade.bar.parallel",
                        "gregsteamexpansion.jade.bar.fluid_stored",
                        "gtceu.jade.fluid_use",
                        "gregsteamexpansion.jade.steam_crusher.pending")),
                "Crusher Jade tooltip stopped using GTCEu-style bar text or changed row order");
        clearCrusherState(controller);
    }

    private static ItemStack migratedCrusherInput() {
        ItemStack rawCopper = ChemicalHelper.get(TagPrefix.rawOre, GTMaterials.Copper);
        for (GTRecipe recipe : GSERecipeTypes.ORE_CRUSHING_RECIPES.getRecipesInCategory(
                GSERecipeTypes.ORE_CRUSHING_RECIPES.getCategory())) {
            var inputs = recipe.inputs.get(ItemRecipeCapability.CAP);
            if (inputs == null || inputs.size() != 1 || !(inputs.get(0).content instanceof Ingredient ingredient)) {
                continue;
            }
            if (ingredient.test(rawCopper)) return rawCopper.copyWithCount(1);
        }
        throw new AssertionError("Raw copper is absent from the migrated ore-crushing recipe table");
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
}
