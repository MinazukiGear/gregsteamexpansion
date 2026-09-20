package com.hoshino.gregsteamexpansion.menu;

import com.gregtechceu.gtceu.api.item.IGTTool;
import com.gregtechceu.gtceu.api.item.tool.ToolHelper;
import com.hoshino.gregsteamexpansion.blockentity.CraftingStationBlockEntity;

import net.minecraft.core.NonNullList;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.StackedContents;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.items.IItemHandler;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;

/** Recipe matching and atomic item movement for one crafting-station menu. */
final class CraftingStationCrafting {
    private final CraftingStationBlockEntity station;
    private final Runnable changeListener;
    private final GridView gridView = new GridView();

    CraftingStationCrafting(CraftingStationBlockEntity station, Runnable changeListener) {
        this.station = station;
        this.changeListener = changeListener;
    }

    @Nullable
    Match findMatch(Level level) {
        var recipes = level.getRecipeManager();
        var matched = recipes.getRecipeFor(RecipeType.CRAFTING, gridView, level).orElse(null);
        if (matched != null) {
            return new Match(matched, -1, -1);
        }
        List<Integer> emptyCells = new ArrayList<>();
        for (int i = 0; i < CraftingStationMenu.GRID_SLOTS; i++) {
            if (station.getGrid().getStackInSlot(i).isEmpty()) {
                emptyCells.add(i);
            }
        }
        if (emptyCells.isEmpty()) {
            return null;
        }
        Set<Item> triedItems = new HashSet<>();
        for (int toolSlot = 0; toolSlot < CraftingStationMenu.TOOL_SLOTS; toolSlot++) {
            ItemStack tool = station.getTools().getStackInSlot(toolSlot);
            if (tool.isEmpty() || !tool.isDamageableItem() || !triedItems.add(tool.getItem())) {
                continue;
            }
            for (int cell : emptyCells) {
                var candidate = recipes.getRecipeFor(
                        RecipeType.CRAFTING, new AugmentedView(cell, tool), level).orElse(null);
                if (candidate != null) {
                    return new Match(candidate, cell, toolSlot);
                }
            }
        }
        return null;
    }

    boolean stillMatches(Match operation, Level level) {
        if (operation.recipe().matches(gridView, level)) {
            return true;
        }
        if (operation.virtualCell() >= 0 && operation.toolSlot() >= 0) {
            ItemStack tool = station.getTools().getStackInSlot(operation.toolSlot());
            if (!tool.isEmpty()) {
                return operation.recipe().matches(new AugmentedView(operation.virtualCell(), tool), level);
            }
        }
        return false;
    }

    ItemStack assemble(Match match, Level level) {
        return match.recipe().assemble(gridView, level.registryAccess());
    }

    @Nullable
    ItemStack commit(Level level, Player player, Match match, @Nullable IItemHandler source) {
        CraftingRecipe recipe = match.recipe();
        ItemStack result = assemble(match, level);
        if (result.isEmpty()) {
            return null;
        }

        result.onCraftedBy(level, player, result.getCount());
        ForgeEventFactory.firePlayerCraftingEvent(player, result, gridView);

        ForgeHooks.setCraftingPlayer(player);
        NonNullList<ItemStack> remainders;
        try {
            remainders = recipe.getRemainingItems(gridView);
        } finally {
            ForgeHooks.setCraftingPlayer(null);
        }

        ItemStack[] originals = new ItemStack[CraftingStationMenu.GRID_SLOTS];
        for (int i = 0; i < CraftingStationMenu.GRID_SLOTS; i++) {
            originals[i] = station.getGrid().getStackInSlot(i).copy();
        }

        for (int i = 0; i < CraftingStationMenu.GRID_SLOTS; i++) {
            if (!station.getGrid().getStackInSlot(i).isEmpty()) {
                station.getGrid().extractItem(i, 1, false);
            }
        }
        if (match.toolSlot() >= 0) {
            damageToolSlot(match.toolSlot(), player);
        }

        for (int i = 0; i < CraftingStationMenu.GRID_SLOTS; i++) {
            ItemStack remainder = remainders.get(i);
            if (remainder == null || remainder.isEmpty()) {
                continue;
            }
            ItemStack cell = station.getGrid().getStackInSlot(i);
            if (cell.isEmpty() && originals[i].getItem() == remainder.getItem()) {
                station.getGrid().setStackInSlot(i, remainder);
            } else if (!cell.isEmpty() && cell.getItem() == remainder.getItem()) {
                remainder.grow(cell.getCount());
                station.getGrid().setStackInSlot(i, remainder);
            } else {
                routeRemainder(remainder, source, player);
            }
        }

        if (source != null) {
            for (int i = 0; i < CraftingStationMenu.GRID_SLOTS; i++) {
                if (!originals[i].isEmpty() && station.getGrid().getStackInSlot(i).isEmpty()) {
                    refillFromSource(i, originals[i], source, player);
                }
            }
        }
        return result;
    }

    static boolean fitsIntoInventory(ItemStack result, Player player) {
        int remaining = result.getCount();
        var items = player.getInventory().items;
        for (int i = 0; i < items.size() && remaining > 0; i++) {
            ItemStack slotStack = items.get(i);
            if (slotStack.isEmpty()) {
                remaining -= Math.min(remaining, result.getMaxStackSize());
            } else if (ItemStack.isSameItemSameTags(slotStack, result) && slotStack.isStackable()) {
                remaining -= Math.min(remaining,
                        Math.min(result.getMaxStackSize(), slotStack.getMaxStackSize()) - slotStack.getCount());
            }
        }
        return remaining <= 0;
    }

    record Match(CraftingRecipe recipe, int virtualCell, int toolSlot) {}

    private void damageToolSlot(int slot, Player player) {
        ItemStack tool = station.getTools().getStackInSlot(slot);
        if (tool.isEmpty()) {
            return;
        }
        ToolHelper.damageItemWhenCrafting(tool, player);
        if (tool.isEmpty() && tool.getItem() instanceof IGTTool gtTool) {
            station.getTools().setStackInSlot(slot, gtTool.getToolStats().getBrokenStack());
        }
    }

    private void refillFromSource(int cell, ItemStack original, IItemHandler source, Player player) {
        for (int slot = 0; slot < source.getSlots(); slot++) {
            ItemStack candidate = source.getStackInSlot(slot);
            if (candidate.isEmpty() || !ItemStack.isSameItemSameTags(candidate, original)) {
                continue;
            }
            ItemStack took = source.extractItem(slot, 1, false);
            if (took.isEmpty()) {
                continue;
            }
            ItemStack leftover = station.getGrid().insertItem(cell, took, false);
            if (!leftover.isEmpty()) {
                routeRemainder(leftover, source, player);
            }
            return;
        }
    }

    private static void routeRemainder(ItemStack stack, @Nullable IItemHandler source, Player player) {
        if (source != null) {
            for (int slot = 0; slot < source.getSlots() && !stack.isEmpty(); slot++) {
                stack = source.insertItem(slot, stack, false);
            }
        }
        if (!stack.isEmpty() && !player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    private class GridView implements CraftingContainer {
        @Override
        public int getContainerSize() {
            return CraftingStationMenu.GRID_SLOTS;
        }

        @Override
        public boolean isEmpty() {
            for (int i = 0; i < CraftingStationMenu.GRID_SLOTS; i++) {
                if (!station.getGrid().getStackInSlot(i).isEmpty()) return false;
            }
            return true;
        }

        @Override
        public ItemStack getItem(int slot) {
            return station.getGrid().getStackInSlot(slot);
        }

        @Override
        public ItemStack removeItem(int slot, int count) {
            return station.getGrid().extractItem(slot, count, false);
        }

        @Override
        public ItemStack removeItemNoUpdate(int slot) {
            ItemStack stack = station.getGrid().getStackInSlot(slot);
            station.getGrid().setStackInSlot(slot, ItemStack.EMPTY);
            return stack;
        }

        @Override
        public void setItem(int slot, ItemStack stack) {
            station.getGrid().setStackInSlot(slot, stack);
        }

        @Override
        public void setChanged() {
            changeListener.run();
        }

        @Override
        public boolean stillValid(Player player) {
            return station.canPlayerUse(player);
        }

        @Override
        public void clearContent() {
            for (int i = 0; i < CraftingStationMenu.GRID_SLOTS; i++) {
                station.getGrid().setStackInSlot(i, ItemStack.EMPTY);
            }
        }

        @Override
        public int getWidth() {
            return 3;
        }

        @Override
        public int getHeight() {
            return 3;
        }

        @Override
        public List<ItemStack> getItems() {
            List<ItemStack> items = new ArrayList<>(CraftingStationMenu.GRID_SLOTS);
            for (int i = 0; i < CraftingStationMenu.GRID_SLOTS; i++) items.add(getItem(i));
            return items;
        }

        @Override
        public void fillStackedContents(StackedContents contents) {
            for (int i = 0; i < CraftingStationMenu.GRID_SLOTS; i++) contents.accountSimpleStack(getItem(i));
        }
    }

    private final class AugmentedView extends GridView {
        private final int substitutedCell;
        private final ItemStack tool;

        private AugmentedView(int substitutedCell, ItemStack tool) {
            this.substitutedCell = substitutedCell;
            this.tool = tool;
        }

        @Override
        public ItemStack getItem(int slot) {
            if (slot == substitutedCell && station.getGrid().getStackInSlot(slot).isEmpty()) {
                return tool;
            }
            return station.getGrid().getStackInSlot(slot);
        }
    }
}
