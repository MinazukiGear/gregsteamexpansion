package com.hoshino.gregsteamexpansion.machine.multiblock;

import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.fluids.PropertyFluidFilter;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.widget.TankWidget;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.api.machine.feature.IDropSaveMachine;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableFluidTank;
import com.gregtechceu.gtceu.api.pattern.BlockPattern;
import com.gregtechceu.gtceu.api.pattern.MultiblockState;
import com.gregtechceu.gtceu.api.transfer.fluid.CustomFluidTank;
import com.gregtechceu.gtceu.api.transfer.fluid.IFluidHandlerModifiable;
import com.gregtechceu.gtceu.common.data.GTMaterials;
import com.gregtechceu.gtceu.common.machine.multiblock.electric.MultiblockTankMachine;
import com.gregtechceu.gtceu.utils.FormattingUtil;
import com.hoshino.gregsteamexpansion.machine.multiblock.part.SteamTankValvePartMachine;
import com.hoshino.gregsteamexpansion.registry.GSESteamTankPatterns;

import com.lowdragmc.lowdraglib.gui.widget.ImageWidget;
import com.lowdragmc.lowdraglib.gui.widget.LabelWidget;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.syncdata.annotation.RequireRerender;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Variable-size, steam-only multiblock storage controller. */
public class LargeSteamTankMachine extends MultiblockTankMachine implements IDropSaveMachine {

    public static final int CAPACITY_PER_OUTER_BLOCK_MB = 64_000;
    public static final int MAX_CAPACITY_MB = capacityFor(9, 8);
    private static final byte STORAGE_DATA_VERSION = 1;
    private static final String DROP_STEAM_KEY = "StoredSteam";
    private static final String DROP_CAPACITY_KEY = "SteamTankCapacity";
    private static final String DROP_VERSION_KEY = "SteamTankDataVersion";

    public static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(
            LargeSteamTankMachine.class, MultiblockTankMachine.MANAGED_FIELD_HOLDER);

    @Persisted
    @DescSynced
    @RequireRerender
    private int formedWidth;
    @Persisted
    @DescSynced
    @RequireRerender
    private int formedHeight;
    @Persisted
    @DescSynced
    private int formedCapacity;
    @Persisted
    @DescSynced
    private int valveCount;
    @Persisted
    private byte storageDataVersion = STORAGE_DATA_VERSION;

    /** Throttled client snapshot used only by the in-world renderer. */
    @DescSynced
    @RequireRerender
    private int renderedAmount;

    private int matchedWidth;
    private int matchedHeight;
    private final BlockPattern[] cachedPatterns = new BlockPattern[GSESteamTankPatterns.WIDTHS.length];
    @Nullable
    private TickableSubscription renderSyncSubscription;

    public LargeSteamTankMachine(IMachineBlockEntity holder) {
        super(holder, MAX_CAPACITY_MB, null);
    }

    @Override
    protected NotifiableFluidTank createTank(int capacity, @Nullable PropertyFluidFilter ignored, Object... args) {
        CustomFluidTank storage = new OverflowSafeSteamTank(capacity);
        return new NotifiableFluidTank(this, List.of(storage), IO.BOTH)
                .setFilter(stack -> stack.getFluid().is(GTMaterials.Steam.getFluidTag()));
    }

    @Override
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }

    @Override
    public boolean checkPattern() {
        MultiblockState state = getMultiblockState();
        for (int i = 0; i < GSESteamTankPatterns.WIDTHS.length; i++) {
            BlockPattern pattern = patternForIndex(i);
            if (pattern.checkPatternAt(state, false)) {
                matchedWidth = GSESteamTankPatterns.WIDTHS[i];
                matchedHeight = pattern.getFormedRepetitionCount()[1] + 2;
                return true;
            }
        }
        return false;
    }

    @Override
    public BlockPattern getPattern() {
        return patternForWidth(9);
    }

    private BlockPattern patternForWidth(int width) {
        for (int i = 0; i < GSESteamTankPatterns.WIDTHS.length; i++) {
            if (GSESteamTankPatterns.WIDTHS[i] == width) return patternForIndex(i);
        }
        throw new IllegalArgumentException("Unsupported steam tank width " + width);
    }

    private BlockPattern patternForIndex(int index) {
        if (cachedPatterns[index] == null) {
            cachedPatterns[index] = GSESteamTankPatterns.create(getDefinition(), GSESteamTankPatterns.WIDTHS[index]);
        }
        return cachedPatterns[index];
    }

    @Override
    public void onStructureFormed() {
        super.onStructureFormed();
        formedWidth = matchedWidth;
        formedHeight = matchedHeight;
        formedCapacity = capacityFor(formedWidth, formedHeight);
        getTank().getStorages()[0].setCapacity(formedCapacity);
        valveCount = (int) getParts().stream().filter(SteamTankValvePartMachine.class::isInstance).count();
        syncRenderedAmount();
        if (renderSyncSubscription == null) {
            renderSyncSubscription = subscribeServerTick(this::renderSyncTick);
        }
        markDirty();
    }

    @Override
    public void onStructureInvalid() {
        super.onStructureInvalid();
        if (renderSyncSubscription != null) {
            renderSyncSubscription.unsubscribe();
            renderSyncSubscription = null;
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (!isRemote() && formedCapacity > 0) {
            getTank().getStorages()[0].setCapacity(formedCapacity);
        }
    }

    private void renderSyncTick() {
        if (getOffsetTimer() % 5 == 0) syncRenderedAmount();
    }

    private void syncRenderedAmount() {
        int amount = getStoredAmount();
        if (renderedAmount != amount) renderedAmount = amount;
    }

    public static int capacityFor(int width, int height) {
        return Math.multiplyExact(Math.multiplyExact(width, width),
                Math.multiplyExact(height, CAPACITY_PER_OUTER_BLOCK_MB));
    }

    public int getStoredAmount() {
        return getTank().getFluidInTank(0).getAmount();
    }

    public boolean isOverCapacity() {
        return formedCapacity > 0 && getStoredAmount() > formedCapacity;
    }

    public int getFormedWidth() {
        return formedWidth;
    }

    public int getFormedHeight() {
        return formedHeight;
    }

    public int getFormedCapacity() {
        return formedCapacity;
    }

    public int getValveCount() {
        return valveCount;
    }

    public int getRenderedAmount() {
        return renderedAmount;
    }

    /** The controller itself is display-only; all automation must use a formed valve. */
    @Override
    @Nullable
    public IFluidHandlerModifiable getFluidHandlerCap(@Nullable Direction side, boolean useCoverCapability) {
        return null;
    }

    @Override
    public boolean saveBreak() {
        return !getTank().isEmpty();
    }

    @Override
    public boolean savePickClone() {
        return false;
    }

    @Override
    public void saveToItem(CompoundTag tag) {
        IDropSaveMachine.super.saveToItem(tag);
        FluidStack steam = getTank().getFluidInTank(0);
        if (!steam.isEmpty()) {
            tag.put(DROP_STEAM_KEY, steam.writeToNBT(new CompoundTag()));
            tag.putInt(DROP_CAPACITY_KEY, formedCapacity > 0 ? formedCapacity : MAX_CAPACITY_MB);
            tag.putByte(DROP_VERSION_KEY, STORAGE_DATA_VERSION);
        }
    }

    @Override
    public void loadFromItem(CompoundTag tag) {
        IDropSaveMachine.super.loadFromItem(tag);
        storageDataVersion = STORAGE_DATA_VERSION;
        int savedCapacity = tag.contains(DROP_CAPACITY_KEY, Tag.TAG_INT) ?
                tag.getInt(DROP_CAPACITY_KEY) : formedCapacity;
        int capacity = savedCapacity > 0 && savedCapacity <= MAX_CAPACITY_MB ? savedCapacity : MAX_CAPACITY_MB;
        formedCapacity = capacity;
        getTank().getStorages()[0].setCapacity(capacity);
        if (tag.contains(DROP_STEAM_KEY, Tag.TAG_COMPOUND)) {
            FluidStack steam = FluidStack.loadFluidStackFromNBT(tag.getCompound(DROP_STEAM_KEY));
            if (!steam.isEmpty() && steam.getFluid().is(GTMaterials.Steam.getFluidTag())) {
                getTank().getStorages()[0].setFluid(steam);
            }
        }
        renderedAmount = getStoredAmount();
    }

    @Override
    public Widget createUIWidget() {
        WidgetGroup group = new WidgetGroup(0, 0, 150, 82);
        group.setBackground(GuiTextures.BACKGROUND_INVERSE);
        group.addWidget(new ImageWidget(4, 4, 142, 74, GuiTextures.DISPLAY));
        group.addWidget(new LabelWidget(8, 8, "gregsteamexpansion.gui.large_steam_tank.title"));
        group.addWidget(new LabelWidget(8, 20, this::formatAmount).setTextColor(-1).setDropShadow(true));
        group.addWidget(new LabelWidget(8, 32, this::formatStructure).setTextColor(-1));
        group.addWidget(new LabelWidget(8, 44, this::formatValves).setTextColor(-1));
        group.addWidget(new LabelWidget(8, 56, this::formatStatus).setTextColor(-1));
        group.addWidget(new TankWidget(getTank().getStorages()[0], 126, 22, false, false)
                .setBackground(GuiTextures.FLUID_SLOT));
        return group;
    }

    private String formatAmount() {
        return FormattingUtil.formatNumbers(getStoredAmount()) + " / " +
                FormattingUtil.formatNumbers(Math.max(0, formedCapacity)) + " mB";
    }

    private String formatStructure() {
        return formedWidth <= 0 ? "—" : formedWidth + "×" + formedWidth + "×" + formedHeight +
                " (" + (formedWidth * formedWidth * formedHeight) + ")";
    }

    private String formatValves() {
        return net.minecraft.network.chat.Component.translatable(
                "gregsteamexpansion.gui.large_steam_tank.valves", valveCount).getString();
    }

    private String formatStatus() {
        return net.minecraft.network.chat.Component.translatable(isOverCapacity() ?
                "gregsteamexpansion.gui.large_steam_tank.over_capacity" :
                "gregsteamexpansion.gui.large_steam_tank.ready").getString();
    }

    /** Forge's base fill assumes stored amount never exceeds the current capacity. */
    private static final class OverflowSafeSteamTank extends CustomFluidTank {

        private OverflowSafeSteamTank(int capacity) {
            super(capacity);
        }

        @Override
        public int fill(@NotNull FluidStack resource, FluidAction action) {
            if (getFluidAmount() >= getCapacity()) return 0;
            return super.fill(resource, action);
        }
    }
}
