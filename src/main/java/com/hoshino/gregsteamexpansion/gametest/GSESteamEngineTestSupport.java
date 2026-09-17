package com.hoshino.gregsteamexpansion.gametest;

import com.hoshino.gregsteamexpansion.GregSteamExpansion;
import com.hoshino.gregsteamexpansion.integration.jade.GSEJadePlugin;
import com.hoshino.gregsteamexpansion.machine.multiblock.LargeHeatStorageSteamFurnaceMachine;
import com.hoshino.gregsteamexpansion.machine.multiblock.crusher.AbstractSteamCrusherMachine;
import com.hoshino.gregsteamexpansion.machine.multiblock.part.SteamExhaustHatchMachine;
import com.hoshino.gregsteamexpansion.machine.multiblock.processor.AbstractSteamProcessorMachine;
import com.hoshino.gregsteamexpansion.machine.multiblock.voidproducer.AbstractSteamVoidMachine;
import com.hoshino.gregsteamexpansion.registry.GSEMachines;
import com.hoshino.gregsteamexpansion.registry.GSEVoidPatterns;

import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.gui.widget.ToggleButtonWidget;
import com.lowdragmc.lowdraglib.gui.widget.LabelWidget;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.common.data.GTMaterials;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.gregtechceu.gtceu.common.machine.multiblock.part.FluidHatchPartMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.part.ItemBusPartMachine;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.fluids.FluidStack;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.jetbrains.annotations.Nullable;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.ITooltip;

final class GSESteamEngineTestSupport {
    private GSESteamEngineTestSupport() {}

    static void stateBoundaries(GameTestHelper h, MultiblockControllerMachine m) {
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
        for (int distance = 1; distance <= SteamExhaustHatchMachine.EXHAUST_CHANNEL_LENGTH; distance++) {
            BlockPos obstruction = exhaust.getPos().relative(exhaust.getFrontFacing(), distance);
            h.getLevel().setBlockAndUpdate(obstruction, Blocks.STONE.defaultBlockState());
            h.assertTrue(exhaust.isExhaustBlocked(),
                    "Exhaust channel ignored an obstruction at distance " + distance);
            tick(m);
            eq(h, demand(m), 0, "Exhaust-blocked machine displayed a current steam demand");
            eq(h, progress(m), 7, "Blocked exhaust rolled back progress at distance " + distance);
            eq(h, steam(m), before, "Blocked exhaust consumed steam at distance " + distance);
            h.assertTrue(!(boolean) call(m, "isConsumingSteam"),
                    "Blocked machine retained a stale consuming state at distance " + distance);
            h.assertTrue(call(m, "getStatusId").equals("exhaust_obstructed"),
                    "Blocked machine exposed the wrong status at distance " + distance);
            eq(h, number(m, "exhaustDamageTimer"), 198,
                    "Inactive tick advanced exhaust damage at distance " + distance);
            h.getLevel().setBlockAndUpdate(obstruction, Blocks.AIR.defaultBlockState());
            h.assertTrue(!exhaust.isExhaustBlocked(),
                    "Exhaust channel stayed blocked after clearing distance " + distance);
        }

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

        var target = Objects.requireNonNull(EntityType.COW.create(h.getLevel()));
        target.moveTo(front.getX() + 0.5, front.getY(), front.getZ() + 0.5);
        h.getLevel().addFreshEntity(target);
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(20);
        target.setHealth(20);
        target.setNoAi(true);
        target.setNoGravity(true);
        h.assertTrue(h.getLevel().getEntitiesOfClass(LivingEntity.class, new AABB(front)).contains(target),
                "Exhaust target was not registered in the vent block: front=" + front
                        + ", target=" + target.blockPosition() + ", box=" + target.getBoundingBox());
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
                "200th active tick did not deal exactly 12 heat damage: before=" + health
                        + ", after=" + target.getHealth() + ", front=" + front
                        + ", target=" + target.blockPosition());
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

    static void settlement(GameTestHelper h, MultiblockControllerMachine m) {
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

    static int replaceSteamSupplyHatches(GameTestHelper h, MultiblockControllerMachine m,
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

    static void formed(GameTestHelper h, MultiblockMachineDefinition definition,
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

    static GTRecipe itemRecipe() {
        return GTRecipeTypes.MACERATOR_RECIPES.recipeBuilder(GregSteamExpansion.id("engine_settlement"))
                .outputItems(new ItemStack(Items.IRON_INGOT, 3))
                .chancedOutput(new ItemStack(Items.GOLD_INGOT), 5000, 0).duration(200).EUt(8).buildRawRecipe();
    }

    static CompoundTag saveState(GameTestHelper h, MultiblockControllerMachine m) {
        BlockEntity blockEntity = h.getLevel().getBlockEntity(m.getPos());
        h.assertTrue(blockEntity != null, "Missing controller block entity");
        CompoundTag tag = blockEntity.saveWithoutMetadata();
        h.assertTrue(!tag.isEmpty(), "Controller block entity persisted no state");
        return tag;
    }

    static void loadState(GameTestHelper h, MultiblockControllerMachine m, CompoundTag tag) {
        BlockEntity blockEntity = h.getLevel().getBlockEntity(m.getPos());
        h.assertTrue(blockEntity != null, "Missing controller block entity");
        blockEntity.load(tag);
    }

    static void seedBatch(MultiblockControllerMachine m, GTRecipe recipe, int parallel) {
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

    static void tick(MultiblockControllerMachine m) {
        call(m, m instanceof AbstractSteamProcessorMachine ? "processorServerTick"
                : m instanceof AbstractSteamVoidMachine ? "voidServerTick"
                : m instanceof LargeHeatStorageSteamFurnaceMachine ? "furnaceServerTick" : "crusherServerTick");
    }

    static long demand(MultiblockControllerMachine m) {
        if (m instanceof AbstractSteamProcessorMachine || m instanceof AbstractSteamCrusherMachine) {
            return ((Number) call(m, "getBatchSteamPerTick")).longValue();
        }
        if (m instanceof LargeHeatStorageSteamFurnaceMachine) {
            return ((Number) call(m, "getCurrentBatchSteamPerTick")).longValue();
        }
        return ((Number) call(m, "currentSteamDemandPerTick")).longValue();
    }

    static String progressField(Object m) {
        return m instanceof AbstractSteamVoidMachine ? "cycleProgress" : "batchProgress";
    }

    static long progress(Object m) { return number(m, progressField(m)); }

    static List<FluidHatchPartMachine> supplies(Object m) {
        return list(m, m instanceof LargeHeatStorageSteamFurnaceMachine ? "steamHatches" : "supplyHatches");
    }

    static List<ItemBusPartMachine> outputs(Object m) { return list(m, "outputBuses"); }

    static List<ItemStack> pending(Object m) { return list(m, "pendingOutputs"); }

    static long steam(Object m) {
        return supplies(m).stream().mapToLong(hatch -> hatch.tank.getFluidInTank(0).getAmount()).sum();
    }

    static void fillSteam(Object m, int amount) {
        for (var hatch : supplies(m)) hatch.tank.getStorages()[0].setFluid(GTMaterials.Steam.getFluid(amount));
    }

    static void fillOutputs(Object m, boolean full) {
        for (var bus : outputs(m)) {
            for (int slot = 0; slot < bus.getInventory().getSlots(); slot++) {
                bus.getInventory().setStackInSlot(slot, full ? new ItemStack(Items.COBBLESTONE, 64) : ItemStack.EMPTY);
            }
        }
    }

    static int count(List<ItemStack> stacks, Item item) {
        return stacks.stream().filter(s -> s.is(item)).mapToInt(ItemStack::getCount).sum();
    }

    static long fluidAmount(List<FluidStack> stacks, FluidStack expected) {
        return stacks.stream()
                .filter(stack -> stack.isFluidEqual(expected))
                .mapToLong(FluidStack::getAmount)
                .sum();
    }

    static int outputCount(Object m, Item item) {
        int total = 0;
        for (var bus : outputs(m)) for (int slot = 0; slot < bus.getInventory().getSlots(); slot++) {
            var stack = bus.getInventory().getStackInSlot(slot);
            if (stack.is(item)) total += stack.getCount();
        }
        return total;
    }

    static void multiProductCapacityCompetition(GameTestHelper h, MultiblockControllerMachine m) {
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

    static void parallelCapacity(GameTestHelper h, MultiblockControllerMachine m) {
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

    static void eq(GameTestHelper h, long actual, long expected, String message) {
        h.assertTrue(actual == expected, message + ": expected=" + expected + ", actual=" + actual);
    }

    static Field field(Object target, String name) {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try {
                var field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {}
        }
        throw new AssertionError("Missing fixture field: " + name);
    }

    static Object get(Object target, String name) {
        try { return field(target, name).get(target); }
        catch (IllegalAccessException e) { throw new AssertionError(e); }
    }

    static long number(Object target, String name) { return ((Number) get(target, name)).longValue(); }

    @SuppressWarnings("unchecked")
    static <T> List<T> list(Object target, String name) { return (List<T>) get(target, name); }

    static void set(Object target, String name, Object value) {
        try {
            var field = field(target, name);
            if (field.getType() == long.class && value instanceof Number n) field.setLong(target, n.longValue());
            else field.set(target, value);
        } catch (IllegalAccessException e) { throw new AssertionError(e); }
    }

    static Object call(Object target, String name, Object... args) {
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

    static ItemBusPartMachine inputBus(MultiblockControllerMachine machine) {
        return machine.getParts().stream()
                .filter(ItemBusPartMachine.class::isInstance)
                .map(ItemBusPartMachine.class::cast)
                .filter(bus -> bus.getInventory().getHandlerIO() == IO.IN)
                .findFirst().orElseThrow();
    }

    static void clearProcessorState(MultiblockControllerMachine m) {
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

    static void clearActiveProcessorBatch(AbstractSteamProcessorMachine m) {
        set(m, "hasBatch", false);
        set(m, "batchRecipe", null);
        set(m, "batchRecipeId", "");
        set(m, "batchParallel", 0);
        set(m, "batchProgress", 0);
        set(m, "batchDurationTicks", 0);
        set(m, "batchSteamPerTickMb", 0L);
        set(m, "batchTotalSteamMb", 0L);
        set(m, "batchLargeSteamOverclock", false);
        set(m, "batchOutputMultiplier", 1.0F);
        set(m, "batchInputDisplay", ItemStack.EMPTY);
    }

    static void clearInventory(ItemBusPartMachine bus) {
        for (int slot = 0; slot < bus.getInventory().getSlots(); slot++) {
            bus.getInventory().setStackInSlot(slot, ItemStack.EMPTY);
        }
    }

    static int inputItemCount(ItemBusPartMachine bus, Item item) {
        int total = 0;
        for (int slot = 0; slot < bus.getInventory().getSlots(); slot++) {
            ItemStack stack = bus.getInventory().getStackInSlot(slot);
            if (stack.is(item)) total += stack.getCount();
        }
        return total;
    }

    @SuppressWarnings("unchecked")
    static String labelText(List<?> widgets, int x, int y) {
        LabelWidget label = widgets.stream()
                .filter(LabelWidget.class::isInstance)
                .map(LabelWidget.class::cast)
                .filter(widget -> widget.getSelfPositionX() == x && widget.getSelfPositionY() == y)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing GUI label at " + x + "," + y));
        return ((Supplier<String>) get(label, "textSupplier")).get();
    }

    static Object jadeProvider(String simpleName) {
        try {
            Class<?> providerType = Class.forName(GSEJadePlugin.class.getName() + "$" + simpleName);
            Object[] constants = providerType.getEnumConstants();
            if (constants == null || constants.length != 1) {
                throw new AssertionError("Jade provider enum is missing: " + simpleName);
            }
            return constants[0];
        } catch (ClassNotFoundException e) {
            throw new AssertionError("Jade provider class is missing: " + simpleName, e);
        }
    }

    static BlockAccessor jadeAccessor(MultiblockControllerMachine machine, CompoundTag serverData) {
        return (BlockAccessor) Proxy.newProxyInstance(
                GSESteamEngineTestSupport.class.getClassLoader(),
                new Class<?>[] { BlockAccessor.class },
                (proxy, method, args) -> switch (method.getName()) {
                    case "getBlockEntity" -> machine.getHolder();
                    case "getServerData" -> serverData;
                    case "getLevel" -> machine.getLevel();
                    case "getPosition" -> machine.getPos();
                    case "getAccessorType" -> BlockAccessor.class;
                    case "isServerConnected" -> true;
                    default -> primitiveDefault(method.getReturnType());
                });
    }

    static ITooltip jadeTooltip(List<Component> lines) {
        return (ITooltip) Proxy.newProxyInstance(
                GSESteamEngineTestSupport.class.getClassLoader(),
                new Class<?>[] { ITooltip.class },
                (proxy, method, args) -> {
                    if (method.getName().equals("add") && args != null) {
                        for (Object argument : args) {
                            if (argument instanceof Component component) lines.add(component);
                        }
                    }
                    if (method.getName().equals("size")) return lines.size();
                    if (method.getName().equals("clear")) lines.clear();
                    return primitiveDefault(method.getReturnType());
                });
    }

    static Object primitiveDefault(Class<?> type) {
        if (!type.isPrimitive() || type == void.class) return null;
        if (type == boolean.class) return false;
        if (type == char.class) return '\0';
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0.0F;
        return 0.0D;
    }

    static boolean tooltipContains(List<Component> lines, Component expected) {
        return lines.stream().anyMatch(line -> line.getString().equals(expected.getString()));
    }

    static List<String> tooltipTranslationKeys(List<Component> lines) {
        return lines.stream()
                .map(Component::getContents)
                .filter(TranslatableContents.class::isInstance)
                .map(TranslatableContents.class::cast)
                .map(TranslatableContents::getKey)
                .toList();
    }

    @SuppressWarnings("unchecked")
    static void guiToggle(ToggleButtonWidget widget, boolean enabled) {
        ((BiConsumer<Object, Boolean>) get(widget, "onPressCallback")).accept(null, enabled);
    }

    @Nullable
    static BlockPos findBlock(GameTestHelper h, MultiblockControllerMachine m,
                                      net.minecraft.world.level.block.Block block) {
        for (BlockPos pos : BlockPos.betweenClosed(
                m.getPos().offset(-15, -15, -15), m.getPos().offset(15, 15, 15))) {
            if (h.getLevel().getBlockState(pos).is(block)) return pos.immutable();
        }
        return null;
    }
}
