package com.hoshino.gregsteamexpansion.blockentity;

import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.common.machine.storage.CrateMachine;
import com.hoshino.gregsteamexpansion.block.CraftingStationSlabBlock;
import com.hoshino.gregsteamexpansion.menu.CraftingStationMenu;
import com.hoshino.gregsteamexpansion.registry.GSEBlockEntityTypes;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;

import java.util.Objects;
import java.util.UUID;

import javax.annotation.Nullable;

public class CraftingStationBlockEntity extends BlockEntity implements MenuProvider {
    public static final int GRID_SLOTS = 9;
    public static final int TOOL_SLOTS = 9;
    public static final Component IN_USE_MESSAGE = Component.translatable("gregsteamexpansion.crafting_station.in_use");

    // Fixed world-direction scan order for the single external source (see crafting-station.md 3.4).
    private static final Direction[] SCAN_ORDER = { Direction.EAST, Direction.WEST, Direction.NORTH, Direction.SOUTH };

    private final ItemStackHandler grid = new ItemStackHandler(GRID_SLOTS) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };
    private final ItemStackHandler tools = new ItemStackHandler(TOOL_SLOTS) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };

    @Nullable
    private UUID viewer;

    public CraftingStationBlockEntity(BlockPos pos, BlockState state) {
        super(GSEBlockEntityTypes.CRAFTING_STATION.get(), pos, state);
    }

    public ItemStackHandler getGrid() {
        return grid;
    }

    public ItemStackHandler getTools() {
        return tools;
    }

    // ------------------------------------------------------------------
    // Single-user occupancy (crafting-station.md 1.7)
    // ------------------------------------------------------------------

    public boolean tryStartViewing(Player player) {
        if (viewer != null && !viewer.equals(player.getUUID())) {
            return false;
        }
        boolean changed = !Objects.equals(viewer, player.getUUID());
        viewer = player.getUUID();
        if (changed) {
            setChanged();
        }
        return true;
    }

    public void stopViewing(@Nullable Player player) {
        if (player != null && Objects.equals(viewer, player.getUUID())) {
            viewer = null;
            setChanged();
        }
    }

    public boolean canPlayerUse(Player player) {
        return !isRemoved() && viewer != null && viewer.equals(player.getUUID())
                && player.distanceToSqr(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5,
                        worldPosition.getZ() + 0.5) <= 64.0;
    }

    // ------------------------------------------------------------------
    // External source container (crafting-station.md 3.1 - 3.4)
    // ------------------------------------------------------------------

    public record SourceContainer(IItemHandler handler, Direction direction) {}

    /**
     * Resolves the single readable container. The vertical-slab form only ever
     * reads its attached container; the normal form scans east, west, north,
     * south and takes the first valid one.
     */
    @Nullable
    public SourceContainer findSourceContainer() {
        Level level = getLevel();
        if (level == null) {
            return null;
        }
        Direction attached = getAttachedDirection();
        if (attached != null) {
            return resolveContainer(level, worldPosition.relative(attached), attached);
        }
        for (Direction direction : SCAN_ORDER) {
            SourceContainer source = resolveContainer(level, worldPosition.relative(direction), direction);
            if (source != null) {
                return source;
            }
        }
        return null;
    }

    @Nullable
    private static SourceContainer resolveContainer(Level level, BlockPos pos, Direction fromStation) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null || be.isRemoved() || isExcludedContainer(be)) {
            return null;
        }
        // The capability is queried on the face of the container that touches the station.
        IItemHandler handler = be.getCapability(ForgeCapabilities.ITEM_HANDLER, fromStation.getOpposite())
                .orElse(null);
        if (handler == null || handler.getSlots() == 0) {
            return null;
        }
        return new SourceContainer(handler, fromStation);
    }

    /**
     * Processing containers are not storage: their slots carry roles (input,
     * fuel, output, catalyst) and must never be read, drained, refilled or
     * attached to by the station (crafting-station.md 3.2). GTCEu machines are
     * excluded wholesale with one exception: the tiered storage crates
     * ({@link CrateMachine}) are genuine item storage and stay readable.
     * GT fluid drums/barrels hold no items and drop out at the slot check.
     */
    private static boolean isExcludedContainer(BlockEntity be) {
        if (be instanceof MetaMachineBlockEntity machineBE) {
            return !(machineBE.getMetaMachine() instanceof CrateMachine);
        }
        return be instanceof AbstractFurnaceBlockEntity        // furnace, blast furnace, smoker
                || be instanceof BrewingStandBlockEntity
                || be instanceof HopperBlockEntity
                || be instanceof DispenserBlockEntity;         // also covers droppers
    }

    /**
     * The horizontal direction pointing from this block towards its attached
     * container, or {@code null} for the normal (full-cube) form.
     */
    @Nullable
    public Direction getAttachedDirection() {
        BlockState state = getBlockState();
        if (state.getBlock() instanceof CraftingStationSlabBlock) {
            return state.getValue(BlockStateProperties.HORIZONTAL_FACING).getOpposite();
        }
        return null;
    }

    public static boolean isValidContainer(LevelReader level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        return be != null && !be.isRemoved() && !isExcludedContainer(be)
                && be.getCapability(ForgeCapabilities.ITEM_HANDLER).isPresent();
    }

    // ------------------------------------------------------------------
    // Persistence
    // ------------------------------------------------------------------

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("Grid", grid.serializeNBT());
        tag.put("Tools", tools.serializeNBT());
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        grid.deserializeNBT(tag.getCompound("Grid"));
        tools.deserializeNBT(tag.getCompound("Tools"));
    }

    public void dropContents(Level level, BlockPos pos) {
        NonNullList<ItemStack> all = NonNullList.create();
        for (int i = 0; i < grid.getSlots(); i++) {
            all.add(grid.getStackInSlot(i));
        }
        for (int i = 0; i < tools.getSlots(); i++) {
            all.add(tools.getStackInSlot(i));
        }
        for (ItemStack stack : all) {
            if (!stack.isEmpty()) {
                Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), stack);
            }
        }
    }

    // ------------------------------------------------------------------
    // Menu provider
    // ------------------------------------------------------------------

    @Override
    public Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    @Override
    public AbstractContainerMenu createMenu(int windowId, Inventory inventory, Player player) {
        return new CraftingStationMenu(windowId, inventory, this);
    }
}
