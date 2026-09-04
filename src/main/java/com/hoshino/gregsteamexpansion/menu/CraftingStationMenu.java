package com.hoshino.gregsteamexpansion.menu;

import com.gregtechceu.gtceu.api.item.IGTTool;
import com.gregtechceu.gtceu.api.item.tool.ToolHelper;
import com.hoshino.gregsteamexpansion.blockentity.CraftingStationBlockEntity;
import com.hoshino.gregsteamexpansion.registry.GSEMenuTypes;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.StackedContents;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.SlotItemHandler;
import net.minecraftforge.items.wrapper.EmptyHandler;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;

/**
 * Crafting station interface (crafting-station.md).
 *
 * Slot layout: 0 result, 1-9 crafting grid, 10-18 tool slots, 19-45 external
 * source container (terminal), 46-72 player inventory, 73-81 hotbar.
 */
public class CraftingStationMenu extends AbstractContainerMenu {
    public static final int RESULT_SLOT = 0;
    public static final int GRID_START = 1;
    public static final int GRID_SLOTS = CraftingStationBlockEntity.GRID_SLOTS;
    public static final int TOOL_START = GRID_START + GRID_SLOTS;
    public static final int TOOL_SLOTS = CraftingStationBlockEntity.TOOL_SLOTS;
    public static final int SOURCE_START = TOOL_START + TOOL_SLOTS;
    public static final int SOURCE_SLOTS = 54; // 6 columns x 9 rows per terminal page
    public static final int INV_START = SOURCE_START + SOURCE_SLOTS;
    public static final int HOTBAR_START = INV_START + 27;

    private static final int MAX_SHIFT_CRAFTS = 4096;

    public final CraftingStationBlockEntity station;
    private final Inventory playerInventory;
    private final Container resultContainer = new net.minecraft.world.SimpleContainer(1);
    private final ItemStack[] snapshot = new ItemStack[GRID_SLOTS + TOOL_SLOTS];

    /**
     * Server: delegates into the currently resolved source container.
     * Client: a plain handler kept in sync through the menu slot system.
     */
    private final IItemHandlerModifiable sourceHandler;
    private int sourceDirection = -1;
    // The terminal pages through the full source container, 27 slots per page
    // (crafting-station.md 6.1); refill and remainder routing always use the
    // whole inventory regardless of the visible page.
    private int sourcePage = 0;
    private int sourcePageCount = 0;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> sourceDirection;
                case 1 -> sourcePage;
                default -> sourcePageCount;
            };
        }

        @Override
        public void set(int index, int value) {
            // Server-authoritative values synced to the client menu here.
            switch (index) {
                case 0 -> sourceDirection = value;
                case 1 -> sourcePage = value;
                default -> sourcePageCount = value;
            }
        }

        @Override
        public int getCount() {
            return 3;
        }
    };

    private final GridView gridView = new GridView();
    private boolean matchDirty = true;

    @Nullable
    private MatchResult currentMatch;

    public CraftingStationMenu(int windowId, Inventory playerInventory, CraftingStationBlockEntity station) {
        super(GSEMenuTypes.CRAFTING_STATION.get(), windowId);
        this.station = station;
        this.playerInventory = playerInventory;
        Level level = station.getLevel();
        boolean serverSide = level != null && !level.isClientSide;
        if (serverSide) {
            this.sourceHandler = new DelegatingSourceHandler();
        } else {
            this.sourceHandler = new ItemStackHandler(SOURCE_SLOTS);
        }
        addSlots();
        addDataSlots(data);
        for (int i = 0; i < snapshot.length; i++) {
            snapshot[i] = ItemStack.EMPTY;
        }
    }

    public static CraftingStationMenu fromNetwork(int windowId, Inventory playerInventory, FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        if (playerInventory.player.level().getBlockEntity(pos) instanceof CraftingStationBlockEntity station) {
            return new CraftingStationMenu(windowId, playerInventory, station);
        }
        return null;
    }

    private void addSlots() {
        addSlot(new ResultSlotStation(resultContainer, RESULT_SLOT, 114, 35));

        for (int i = 0; i < GRID_SLOTS; i++) {
            addSlot(new SlotItemHandler(station.getGrid(), i,
                    30 + (i % 3) * 18, 17 + (i / 3) * 18));
        }
        for (int i = 0; i < TOOL_SLOTS; i++) {
            addSlot(new SlotItemHandler(station.getTools(), i,
                    8 + i * 18, 80) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    // Only durables that can serve as recipe tools (crafting-station.md 4.2).
                    return stack.isDamageableItem();
                }
            });
        }
        for (int i = 0; i < SOURCE_SLOTS; i++) {
            addSlot(new SlotItemHandler(sourceHandler, i,
                    -132 + (i % 6) * 18, 12 + (i / 6) * 18) {
                @Override
                public boolean isActive() {
                    // The terminal panel only docks onto the main UI while a
                    // valid source container exists (crafting-station.md 6.1).
                    return getSourceDirection() >= 0;
                }
            });
        }

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, 9 + row * 9 + col, 8 + col * 18, 106 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, 8 + col * 18, 168));
        }
    }

    public int getSourceDirection() {
        return sourceDirection;
    }

    public int getSourcePage() {
        return sourcePage;
    }

    public int getSourcePageCount() {
        return sourcePageCount;
    }

    // ------------------------------------------------------------------
    // Server-side state maintenance
    // ------------------------------------------------------------------

    @Override
    public void slotsChanged(Container container) {
        matchDirty = true;
    }

    @Override
    public void broadcastChanges() {
        Level level = station.getLevel();
        if (level != null && !level.isClientSide) {
            revalidateSource();
            if (matchDirty || snapshotChanged()) {
                recomputePreview(level);
                matchDirty = false;
            }
        }
        super.broadcastChanges();
    }

    private void revalidateSource() {
        var source = station.findSourceContainer();
        int ordinal = source != null ? source.direction().get3DDataValue() : -1;
        IItemHandler newHandler = source != null ? source.handler() : null;
        DelegatingSourceHandler delegating = (DelegatingSourceHandler) sourceHandler;
        if (sourceDirection != ordinal || delegating.getDelegate() != newHandler) {
            delegating.setDelegate(newHandler);
            delegating.setPage(0);
            sourceDirection = ordinal;
            sourcePage = 0;
        }
        sourcePageCount = newHandler != null
                ? Math.max(1, (newHandler.getSlots() + SOURCE_SLOTS - 1) / SOURCE_SLOTS)
                : 0;
        if (sourcePage >= sourcePageCount) {
            sourcePage = Math.max(0, sourcePageCount - 1);
            delegating.setPage(sourcePage);
        }
    }

    /**
     * Page flip requested by the terminal's prev/next buttons. Only meaningful
     * on the server, where the delegating window handler lives.
     */
    @Override
    public boolean clickMenuButton(Player player, int id) {
        // The scrollbar sends the absolute target page index.
        if (sourceHandler instanceof DelegatingSourceHandler delegating
                && id >= 0 && id < Math.max(1, sourcePageCount) && id != sourcePage) {
            sourcePage = id;
            delegating.setPage(sourcePage);
        }
        return true;
    }

    private boolean snapshotChanged() {
        boolean changed = false;
        for (int i = 0; i < GRID_SLOTS; i++) {
            ItemStack now = station.getGrid().getStackInSlot(i);
            if (!ItemStack.matches(snapshot[i], now)) {
                snapshot[i] = now.copy();
                changed = true;
            }
        }
        for (int i = 0; i < TOOL_SLOTS; i++) {
            ItemStack now = station.getTools().getStackInSlot(i);
            if (!ItemStack.matches(snapshot[GRID_SLOTS + i], now)) {
                snapshot[GRID_SLOTS + i] = now.copy();
                changed = true;
            }
        }
        return changed;
    }

    private void recomputePreview(Level level) {
        currentMatch = findMatch(level);
        ItemStack preview = ItemStack.EMPTY;
        if (currentMatch != null) {
            preview = currentMatch.recipe().assemble(gridView, level.registryAccess());
        }
        resultContainer.setItem(0, preview);
    }

    /**
     * Matches the grid as-is first; when it fails, tries supplying a single
     * empty cell from the tool slots (one cell per craft, crafting-station.md
     * 4.2). The first tool slot holding a viable item wins.
     */
    @Nullable
    private MatchResult findMatch(Level level) {
        var recipes = level.getRecipeManager();
        var matched = recipes.getRecipeFor(RecipeType.CRAFTING, gridView, level).orElse(null);
        if (matched != null) {
            return new MatchResult(matched, -1, -1);
        }
        List<Integer> emptyCells = new ArrayList<>();
        for (int i = 0; i < GRID_SLOTS; i++) {
            if (station.getGrid().getStackInSlot(i).isEmpty()) {
                emptyCells.add(i);
            }
        }
        if (emptyCells.isEmpty()) {
            return null;
        }
        Set<Item> triedItems = new HashSet<>();
        for (int toolSlot = 0; toolSlot < TOOL_SLOTS; toolSlot++) {
            ItemStack tool = station.getTools().getStackInSlot(toolSlot);
            if (tool.isEmpty() || !tool.isDamageableItem() || !triedItems.add(tool.getItem())) {
                continue;
            }
            for (int cell : emptyCells) {
                var view = new AugmentedView(cell, tool);
                var candidate = recipes.getRecipeFor(RecipeType.CRAFTING, view, level).orElse(null);
                if (candidate != null) {
                    return new MatchResult(candidate, cell, toolSlot);
                }
            }
        }
        return null;
    }

    /**
     * Re-verifies that the locked shift-craft recipe still matches the grid
     * (with the same tool-slot supply). A shift operation must never switch
     * to a different recipe mid-way (crafting-station.md 4.4).
     */
    private boolean stillMatches(MatchResult operation, Level level) {
        if (operation.recipe().matches(gridView, level)) {
            return true;
        }
        if (operation.virtualCell() >= 0 && operation.toolSlot() >= 0) {
            ItemStack tool = station.getTools().getStackInSlot(operation.toolSlot());
            if (!tool.isEmpty()) {
                return operation.recipe()
                        .matches(new AugmentedView(operation.virtualCell(), tool), level);
            }
        }
        return false;
    }

    private record MatchResult(CraftingRecipe recipe, int virtualCell, int toolSlot) {}

    // ------------------------------------------------------------------
    // Crafting transaction (crafting-station.md 4.1 - 4.5)
    // ------------------------------------------------------------------

    private void craftOnce(Player player) {
        Level level = station.getLevel();
        if (level == null || level.isClientSide) {
            return;
        }
        if (matchDirty) {
            recomputePreview(level);
            matchDirty = false;
        }
        if (currentMatch == null) {
            return;
        }
        commitMatch(level, player, currentMatch);
        recomputePreview(level);
    }

    @Nullable
    private ItemStack commitMatch(Level level, Player player, MatchResult match) {
        CraftingRecipe recipe = match.recipe();
        ItemStack result = recipe.assemble(gridView, level.registryAccess());
        if (result.isEmpty()) {
            return null;
        }

        result.onCraftedBy(level, player, result.getCount());
        ForgeEventFactory.firePlayerCraftingEvent(player, result, gridView);

        IItemHandler source = activeSourceHandler();

        // Remaining (container) items per cell; GT tools yield a damaged copy here.
        ForgeHooks.setCraftingPlayer(player);
        NonNullList<ItemStack> remainders;
        try {
            remainders = recipe.getRemainingItems(gridView);
        } finally {
            ForgeHooks.setCraftingPlayer(null);
        }

        ItemStack[] originals = new ItemStack[GRID_SLOTS];
        for (int i = 0; i < GRID_SLOTS; i++) {
            originals[i] = station.getGrid().getStackInSlot(i).copy();
        }

        // Consume exactly one item per occupied pattern cell.
        for (int i = 0; i < GRID_SLOTS; i++) {
            if (!station.getGrid().getStackInSlot(i).isEmpty()) {
                station.getGrid().extractItem(i, 1, false);
            }
        }

        // Auto-supplied tool: damage the borrowed tool in its tool slot.
        if (match.toolSlot() >= 0) {
            damageToolSlot(match.toolSlot(), player);
        }

        // Route remainders: the pattern's own tool (a GT saw damaged by the
        // craft) keeps its cell — compare by item, since the damaged copy
        // carries extra NBT and would otherwise be classified as a leftover
        // and ejected from the grid. True leftovers (an empty bucket) go back
        // to the source container, then the player inventory, then drop
        // (crafting-station.md 4.3).
        for (int i = 0; i < GRID_SLOTS; i++) {
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

        // Pattern maintenance: top consumed-and-now-empty cells back up to one.
        if (source != null) {
            for (int i = 0; i < GRID_SLOTS; i++) {
                if (originals[i].isEmpty() || !station.getGrid().getStackInSlot(i).isEmpty()) {
                    continue;
                }
                refillFromSource(i, originals[i], source, player);
            }
        }
        return result;
    }

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

    private void routeRemainder(ItemStack stack, @Nullable IItemHandler source, Player player) {
        if (source != null) {
            for (int slot = 0; slot < source.getSlots() && !stack.isEmpty(); slot++) {
                stack = source.insertItem(slot, stack, false);
            }
        }
        if (!stack.isEmpty() && !player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    @Nullable
    private IItemHandler activeSourceHandler() {
        if (sourceHandler instanceof DelegatingSourceHandler delegating) {
            return delegating.getDelegate();
        }
        return null;
    }

    private static boolean fitsIntoInventory(ItemStack result, Player player) {
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

    // ------------------------------------------------------------------
    // Standard menu plumbing
    // ------------------------------------------------------------------

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }

        if (index == RESULT_SLOT) {
            Level level = station.getLevel();
            if (level == null || level.isClientSide) {
                return ItemStack.EMPTY;
            }
            if (matchDirty) {
                recomputePreview(level);
                matchDirty = false;
            }
            // Lock the shift operation onto the currently previewed recipe:
            // it is repeated while it keeps matching, and the loop never
            // re-matches a different recipe (crafting-station.md 4.4).
            MatchResult operation = currentMatch;
            if (operation == null) {
                return ItemStack.EMPTY;
            }
            int crafted = 0;
            while (crafted < MAX_SHIFT_CRAFTS) {
                if (!stillMatches(operation, level)) {
                    break;
                }
                ItemStack result = operation.recipe().assemble(gridView, level.registryAccess());
                if (result.isEmpty() || !fitsIntoInventory(result, player)) {
                    break;
                }
                ItemStack produced = commitMatch(level, player, operation);
                if (produced == null || produced.isEmpty()) {
                    break;
                }
                if (!player.getInventory().add(produced)) {
                    player.drop(produced, false);
                }
                crafted++;
            }
            matchDirty = true;
            return ItemStack.EMPTY;
        }

        ItemStack original = slot.getItem();
        ItemStack moved = original.copy();
        if (index < INV_START) {
            // Tools shifted out of the crafting grid return to the tool slots
            // first (crafting-station.md 4.2); overflow falls through to the
            // player inventory below.
            if (index >= GRID_START && index < GRID_START + GRID_SLOTS && original.isDamageableItem()) {
                moveItemStackTo(moved, TOOL_START, TOOL_START + TOOL_SLOTS, false);
            }
            if (!moved.isEmpty() && !moveItemStackTo(moved, INV_START, HOTBAR_START + 9, true)
                    && moved.getCount() == original.getCount()) {
                return ItemStack.EMPTY;
            }
        } else {
            boolean accepted = moveItemStackTo(moved, TOOL_START, TOOL_START + TOOL_SLOTS, false)
                    || moveItemStackTo(moved, GRID_START, GRID_START + GRID_SLOTS, false)
                    || moveItemStackTo(moved, SOURCE_START, SOURCE_START + SOURCE_SLOTS, false);
            if (!accepted) {
                return ItemStack.EMPTY;
            }
        }

        if (moved.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        if (moved.getCount() == original.getCount()) {
            return ItemStack.EMPTY;
        }
        slot.onTake(player, moved);
        return original;
    }

    @Override
    public boolean stillValid(Player player) {
        return station.canPlayerUse(player);
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (player.level() != null && !player.level().isClientSide) {
            station.stopViewing(player);
        }
    }

    // ------------------------------------------------------------------
    // Crafting container views
    // ------------------------------------------------------------------

    private class GridView implements CraftingContainer {
        @Override
        public int getContainerSize() {
            return GRID_SLOTS;
        }

        @Override
        public boolean isEmpty() {
            for (int i = 0; i < GRID_SLOTS; i++) {
                if (!station.getGrid().getStackInSlot(i).isEmpty()) {
                    return false;
                }
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
            matchDirty = true;
        }

        @Override
        public boolean stillValid(Player player) {
            return station.canPlayerUse(player);
        }

        @Override
        public void clearContent() {
            for (int i = 0; i < GRID_SLOTS; i++) {
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
            List<ItemStack> items = new ArrayList<>(GRID_SLOTS);
            for (int i = 0; i < GRID_SLOTS; i++) {
                items.add(station.getGrid().getStackInSlot(i));
            }
            return items;
        }

        @Override
        public void fillStackedContents(StackedContents contents) {
            for (int i = 0; i < GRID_SLOTS; i++) {
                contents.accountSimpleStack(station.getGrid().getStackInSlot(i));
            }
        }
    }

    /**
     * Read-only matching view with one empty grid cell virtually filled by a
     * tool from the tool slots.
     */
    private class AugmentedView implements CraftingContainer {
        private final int substitutedCell;
        private final ItemStack tool;

        private AugmentedView(int substitutedCell, ItemStack tool) {
            this.substitutedCell = substitutedCell;
            this.tool = tool;
        }

        @Override
        public int getContainerSize() {
            return GRID_SLOTS;
        }

        @Override
        public boolean isEmpty() {
            return gridView.isEmpty();
        }

        @Override
        public ItemStack getItem(int slot) {
            if (slot == substitutedCell && station.getGrid().getStackInSlot(slot).isEmpty()) {
                return tool;
            }
            return station.getGrid().getStackInSlot(slot);
        }

        @Override
        public ItemStack removeItem(int slot, int count) {
            return gridView.removeItem(slot, count);
        }

        @Override
        public ItemStack removeItemNoUpdate(int slot) {
            return gridView.removeItemNoUpdate(slot);
        }

        @Override
        public void setItem(int slot, ItemStack stack) {
            gridView.setItem(slot, stack);
        }

        @Override
        public void setChanged() {
            matchDirty = true;
        }

        @Override
        public boolean stillValid(Player player) {
            return station.canPlayerUse(player);
        }

        @Override
        public void clearContent() {
            gridView.clearContent();
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
            List<ItemStack> items = new ArrayList<>(GRID_SLOTS);
            for (int i = 0; i < GRID_SLOTS; i++) {
                items.add(getItem(i));
            }
            return items;
        }

        @Override
        public void fillStackedContents(StackedContents contents) {
            for (int i = 0; i < GRID_SLOTS; i++) {
                contents.accountSimpleStack(getItem(i));
            }
        }
    }

    private static class DelegatingSourceHandler implements IItemHandlerModifiable {
        private IItemHandler delegate = EmptyHandler.INSTANCE;
        // Base container slot of the currently displayed terminal page.
        private int offset = 0;

        public void setDelegate(@Nullable IItemHandler newDelegate) {
            this.delegate = newDelegate != null ? newDelegate : EmptyHandler.INSTANCE;
        }

        public IItemHandler getDelegate() {
            return delegate;
        }

        public void setPage(int page) {
            this.offset = page * SOURCE_SLOTS;
        }

        // The menu always exposes 27 source slots; containers smaller than the
        // current page must answer out-of-range indices safely instead of throwing.
        private boolean inRange(int slot) {
            return slot >= 0 && slot + offset < delegate.getSlots();
        }

        @Override
        public void setStackInSlot(int slot, ItemStack stack) {
            if (inRange(slot) && delegate instanceof IItemHandlerModifiable modifiable) {
                modifiable.setStackInSlot(slot + offset, stack);
            }
        }

        @Override
        public int getSlots() {
            return SOURCE_SLOTS;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return inRange(slot) ? delegate.getStackInSlot(slot + offset) : ItemStack.EMPTY;
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return inRange(slot) ? delegate.insertItem(slot + offset, stack, simulate) : stack;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return inRange(slot) ? delegate.extractItem(slot + offset, amount, simulate) : ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return inRange(slot) ? delegate.getSlotLimit(slot + offset) : 64;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return inRange(slot) && delegate.isItemValid(slot + offset, stack);
        }
    }

    private class ResultSlotStation extends Slot {
        public ResultSlotStation(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public boolean mayPickup(Player player) {
            return hasItem();
        }

        @Override
        public void onTake(Player player, ItemStack stack) {
            craftOnce(player);
        }
    }
}
