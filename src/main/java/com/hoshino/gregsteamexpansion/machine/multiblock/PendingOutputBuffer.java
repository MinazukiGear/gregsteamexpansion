package com.hoshino.gregsteamexpansion.machine.multiblock;

import com.gregtechceu.gtceu.common.machine.multiblock.part.FluidHatchPartMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.part.ItemBusPartMachine;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableItemStackHandler;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.ItemHandlerHelper;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared operations over a controller's persisted pending-output lists.
 *
 * <p>The lists remain fields of each controller so their existing sync-data
 * names and save format do not change. This adapter centralizes the atomic
 * simulate-then-execute delivery plan and the internal output-bus insertion
 * path used by every steam engine family.</p>
 */
public final class PendingOutputBuffer {

    private final List<ItemStack> items;
    @Nullable
    private final List<FluidStack> fluids;

    public PendingOutputBuffer(List<ItemStack> items) {
        this(items, null);
    }

    public PendingOutputBuffer(List<ItemStack> items, @Nullable List<FluidStack> fluids) {
        this.items = items;
        this.fluids = fluids;
    }

    public boolean hasAny() {
        return !items.isEmpty() || fluids != null && !fluids.isEmpty();
    }

    public int entryCount() {
        return items.size() + (fluids == null ? 0 : fluids.size());
    }

    public void addItem(ItemStack stack) {
        if (!stack.isEmpty()) {
            items.add(stack);
        }
    }

    public void addFluid(FluidStack stack) {
        if (fluids == null) {
            throw new IllegalStateException("This pending-output buffer does not accept fluids");
        }
        if (!stack.isEmpty()) {
            fluids.add(stack);
        }
    }

    public void addMergedItems(List<ItemStack> produced) {
        mergeItems(produced);
        items.addAll(produced);
    }

    public void addMergedFluids(List<FluidStack> produced) {
        if (fluids == null) {
            throw new IllegalStateException("This pending-output buffer does not accept fluids");
        }
        mergeFluids(produced);
        fluids.addAll(produced);
    }

    public void mergePendingItems() {
        mergeItems(items);
    }

    public void clear() {
        items.clear();
        if (fluids != null) {
            fluids.clear();
        }
    }

    /** Items and fluids are atomic within their own channel, matching existing behavior. */
    public boolean deliverAll(List<ItemBusPartMachine> outputBuses,
                              List<FluidHatchPartMachine> fluidOutputHatches,
                              FluidInsertion fluidInsertion) {
        boolean itemsOk = deliverItems(outputBuses);
        boolean fluidsOk = deliverFluids(fluidOutputHatches, fluidInsertion);
        return itemsOk && fluidsOk;
    }

    public boolean deliverItems(List<ItemBusPartMachine> outputBuses) {
        return deliverItems(outputBuses, null);
    }

    /** Delivers to ordered buses first, then an optional controller-owned fallback inventory. */
    public boolean deliverItems(List<ItemBusPartMachine> outputBuses,
                                @Nullable NotifiableItemStackHandler fallback) {
        if (items.isEmpty()) {
            return true;
        }
        if (!itemsFit(items, outputBuses, fallback)) {
            return false;
        }
        insertItems(items, outputBuses, fallback, false);
        items.removeIf(ItemStack::isEmpty);
        return items.isEmpty();
    }

    public boolean deliverFluids(List<FluidHatchPartMachine> fluidOutputHatches,
                                 FluidInsertion fluidInsertion) {
        if (fluids == null || fluids.isEmpty()) {
            return true;
        }
        if (!fluidsFit(fluids, fluidOutputHatches, fluidInsertion)) {
            return false;
        }
        insertFluids(fluids, fluidOutputHatches, fluidInsertion, IFluidHandler.FluidAction.EXECUTE);
        fluids.removeIf(FluidStack::isEmpty);
        return fluids.isEmpty();
    }

    public static boolean itemsFit(List<ItemStack> outputs, List<ItemBusPartMachine> outputBuses) {
        return itemsFit(outputs, outputBuses, null);
    }

    public static boolean itemsFit(List<ItemStack> outputs,
                                   List<ItemBusPartMachine> outputBuses,
                                   @Nullable NotifiableItemStackHandler fallback) {
        if (outputs.isEmpty()) {
            return true;
        }
        if (outputBuses.isEmpty() && fallback == null) {
            return false;
        }
        List<SimulatedItemInventory> inventories = new ArrayList<>(outputBuses.size() + (fallback == null ? 0 : 1));
        for (ItemBusPartMachine bus : outputBuses) {
            inventories.add(new SimulatedItemInventory(bus.getInventory()));
        }
        if (fallback != null) {
            inventories.add(new SimulatedItemInventory(fallback));
        }
        List<ItemStack> simulation = new ArrayList<>(outputs.size());
        for (ItemStack output : outputs) {
            simulation.add(output.copy());
        }
        // Match insertItems exactly: every product visits one bus before the next bus is considered.
        for (SimulatedItemInventory inventory : inventories) {
            for (int i = 0; i < simulation.size(); i++) {
                simulation.set(i, inventory.insert(simulation.get(i)));
            }
        }
        return simulation.stream().allMatch(ItemStack::isEmpty);
    }

    public static boolean fluidsFit(List<FluidStack> outputs,
                                    List<FluidHatchPartMachine> fluidOutputHatches,
                                    FluidInsertion fluidInsertion) {
        if (outputs.isEmpty()) {
            return true;
        }
        if (fluidOutputHatches.isEmpty()) {
            return false;
        }
        List<FluidStack> simulation = copyFluids(outputs);
        insertFluids(simulation, fluidOutputHatches, fluidInsertion, IFluidHandler.FluidAction.SIMULATE);
        return simulation.stream().allMatch(FluidStack::isEmpty);
    }

    public static List<ItemStack> scaleItemsForParallel(List<ItemStack> perOperation, int parallel) {
        List<ItemStack> scaled = new ArrayList<>();
        for (ItemStack stack : perOperation) {
            long remaining = (long) stack.getCount() * parallel;
            while (remaining > 0) {
                int count = (int) Math.min(stack.getMaxStackSize(), remaining);
                scaled.add(stack.copyWithCount(count));
                remaining -= count;
            }
        }
        return scaled;
    }

    private static List<FluidStack> copyFluids(List<FluidStack> stacks) {
        List<FluidStack> copies = new ArrayList<>(stacks.size());
        for (FluidStack stack : stacks) {
            copies.add(stack.copy());
        }
        return copies;
    }

    private static void insertItems(List<ItemStack> stacks,
                                    List<ItemBusPartMachine> outputBuses,
                                    @Nullable NotifiableItemStackHandler fallback,
                                    boolean simulate) {
        for (ItemBusPartMachine bus : outputBuses) {
            for (int i = 0; i < stacks.size(); i++) {
                stacks.set(i, insertIntoBus(bus, stacks.get(i), simulate));
            }
        }
        if (fallback != null) {
            for (int i = 0; i < stacks.size(); i++) {
                stacks.set(i, insertIntoHandler(fallback, stacks.get(i), simulate));
            }
        }
    }

    /** Uses the bus's internal path because its exposed capability is output-only. */
    public static ItemStack insertIntoBus(ItemBusPartMachine bus, ItemStack stack, boolean simulate) {
        return insertIntoHandler(bus.getInventory(), stack, simulate);
    }

    /** Uses the internal path because output handlers reject external insertion. */
    public static ItemStack insertIntoHandler(NotifiableItemStackHandler inventory,
                                              ItemStack stack,
                                              boolean simulate) {
        if (stack.isEmpty()) {
            return stack;
        }
        ItemStack remaining = stack;
        for (int slot = 0; slot < inventory.getSlots() && !remaining.isEmpty(); slot++) {
            ItemStack current = inventory.getStackInSlot(slot);
            if (!current.isEmpty() && ItemHandlerHelper.canItemStacksStack(current, remaining)) {
                remaining = inventory.insertItemInternal(slot, remaining, simulate);
            }
        }
        for (int slot = 0; slot < inventory.getSlots() && !remaining.isEmpty(); slot++) {
            if (inventory.getStackInSlot(slot).isEmpty()) {
                remaining = inventory.insertItemInternal(slot, remaining, simulate);
            }
        }
        return remaining;
    }

    /** Retains earlier simulated inserts so distinct products compete for the same slots. */
    private static final class SimulatedItemInventory {

        private final NotifiableItemStackHandler handler;
        private final List<ItemStack> slots;

        private SimulatedItemInventory(NotifiableItemStackHandler handler) {
            this.handler = handler;
            this.slots = new ArrayList<>(handler.getSlots());
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                slots.add(handler.getStackInSlot(slot).copy());
            }
        }

        private ItemStack insert(ItemStack stack) {
            if (stack.isEmpty()) {
                return stack;
            }
            ItemStack remaining = stack.copy();
            for (int slot = 0; slot < slots.size() && !remaining.isEmpty(); slot++) {
                ItemStack current = slots.get(slot);
                if (!current.isEmpty() && ItemHandlerHelper.canItemStacksStack(current, remaining)) {
                    remaining = insertIntoSlot(slot, remaining);
                }
            }
            for (int slot = 0; slot < slots.size() && !remaining.isEmpty(); slot++) {
                if (slots.get(slot).isEmpty()) {
                    remaining = insertIntoSlot(slot, remaining);
                }
            }
            return remaining;
        }

        private ItemStack insertIntoSlot(int slot, ItemStack stack) {
            if (!handler.isItemValid(slot, stack)) {
                return stack;
            }
            ItemStack current = slots.get(slot);
            if (!current.isEmpty() && !ItemHandlerHelper.canItemStacksStack(current, stack)) {
                return stack;
            }
            int limit = Math.min(handler.getSlotLimit(slot), stack.getMaxStackSize());
            int accepted = Math.min(stack.getCount(), limit - current.getCount());
            if (accepted <= 0) {
                return stack;
            }
            if (current.isEmpty()) {
                slots.set(slot, stack.copyWithCount(accepted));
            } else {
                current.grow(accepted);
            }
            ItemStack remaining = stack.copy();
            remaining.shrink(accepted);
            return remaining;
        }
    }

    private static void insertFluids(List<FluidStack> stacks,
                                     List<FluidHatchPartMachine> outputHatches,
                                     FluidInsertion insertion,
                                     IFluidHandler.FluidAction action) {
        for (FluidHatchPartMachine hatch : outputHatches) {
            for (FluidStack remaining : stacks) {
                if (!remaining.isEmpty()) {
                    int accepted = insertion == FluidInsertion.INTERNAL
                            ? hatch.tank.fillInternal(remaining, action)
                            : hatch.tank.fill(remaining, action);
                    remaining.shrink(accepted);
                }
            }
        }
    }

    public static void mergeItems(List<ItemStack> stacks) {
        for (int i = 0; i < stacks.size(); i++) {
            ItemStack keep = stacks.get(i);
            for (int j = stacks.size() - 1; j > i; j--) {
                ItemStack other = stacks.get(j);
                if (!keep.isEmpty() && ItemHandlerHelper.canItemStacksStack(keep, other)
                        && keep.getCount() < keep.getMaxStackSize()) {
                    int moved = Math.min(other.getCount(), keep.getMaxStackSize() - keep.getCount());
                    keep.grow(moved);
                    other.shrink(moved);
                    if (other.isEmpty()) {
                        stacks.remove(j);
                    }
                }
            }
        }
        stacks.removeIf(ItemStack::isEmpty);
    }

    public static void mergeFluids(List<FluidStack> stacks) {
        for (int i = 0; i < stacks.size(); i++) {
            FluidStack keep = stacks.get(i);
            for (int j = stacks.size() - 1; j > i; j--) {
                FluidStack other = stacks.get(j);
                if (!keep.isEmpty() && keep.isFluidEqual(other)) {
                    keep.grow(other.getAmount());
                    stacks.remove(j);
                }
            }
        }
        stacks.removeIf(FluidStack::isEmpty);
    }

    public enum FluidInsertion {
        CAPABILITY,
        INTERNAL
    }
}
