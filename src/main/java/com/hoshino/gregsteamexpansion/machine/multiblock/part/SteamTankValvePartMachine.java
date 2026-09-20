package com.hoshino.gregsteamexpansion.machine.multiblock.part;

import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.cover.CoverBehavior;
import com.gregtechceu.gtceu.api.machine.ConditionalSubscriptionHandler;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.property.GTMachineModelProperties;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.machine.multiblock.part.MultiblockPartMachine;
import com.gregtechceu.gtceu.api.machine.trait.FluidTankProxyTrait;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.misc.IOFluidHandlerList;
import com.gregtechceu.gtceu.api.transfer.fluid.IFluidHandlerModifiable;
import com.gregtechceu.gtceu.common.data.GTMaterials;
import com.gregtechceu.gtceu.utils.GTTransferUtils;
import com.hoshino.gregsteamexpansion.machine.multiblock.LargeSteamTankMachine;

import com.lowdragmc.lowdraglib.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.syncdata.annotation.RequireRerender;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.fluids.FluidStack;

import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Configurable input/output proxy for a formed {@link LargeSteamTankMachine}. */
public class SteamTankValvePartMachine extends MultiblockPartMachine {

    public static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(
            SteamTankValvePartMachine.class, MultiblockPartMachine.MANAGED_FIELD_HOLDER);

    private final FluidTankProxyTrait tankProxy;
    private final ConditionalSubscriptionHandler autoIOSubscription;

    @Persisted
    @DescSynced
    @RequireRerender
    private boolean outputMode;

    public SteamTankValvePartMachine(IMachineBlockEntity holder) {
        super(holder);
        tankProxy = new FluidTankProxyTrait(this, IO.BOTH);
        autoIOSubscription = new ConditionalSubscriptionHandler(this, this::autoIO, this::shouldAutoIO);
    }

    @Override
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }

    @Override
    public boolean canShared() {
        return false;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        updateModeAppearance();
        autoIOSubscription.initialize(getLevel());
    }

    @Override
    public void onUnload() {
        autoIOSubscription.unsubscribe();
        super.onUnload();
    }

    @Override
    public void addedToController(IMultiController controller) {
        super.addedToController(controller);
        if (controller instanceof LargeSteamTankMachine tank) {
            tankProxy.setProxy(tank.getTank());
        }
        autoIOSubscription.updateSubscription();
    }

    @Override
    public void removedFromController(IMultiController controller) {
        super.removedFromController(controller);
        tankProxy.setProxy(null);
        autoIOSubscription.updateSubscription();
    }

    @Override
    public void onRotated(Direction oldFacing, Direction newFacing) {
        super.onRotated(oldFacing, newFacing);
        autoIOSubscription.updateSubscription();
    }

    @Override
    public void onNeighborChanged(Block block, BlockPos fromPos, boolean isMoving) {
        super.onNeighborChanged(block, fromPos, isMoving);
        autoIOSubscription.updateSubscription();
    }

    public boolean isOutputMode() {
        return outputMode;
    }

    public void setOutputMode(boolean outputMode) {
        if (this.outputMode == outputMode) return;
        this.outputMode = outputMode;
        updateModeAppearance();
        autoIOSubscription.updateSubscription();
        notifyBlockUpdate();
        markDirty();
    }

    @Nullable
    public LargeSteamTankMachine getLinkedTank() {
        return getControllers().stream()
                .filter(LargeSteamTankMachine.class::isInstance)
                .map(LargeSteamTankMachine.class::cast)
                .findFirst()
                .orElse(null);
    }

    @Override
    @Nullable
    public IFluidHandlerModifiable getFluidHandlerCap(@Nullable Direction side, boolean useCoverCapability) {
        if (!isFormed() || tankProxy.getProxy() == null) return null;
        if (side != null && side != getFrontFacing()) return null;

        IO io = outputMode ? IO.OUT : IO.IN;
        IFluidHandlerModifiable handler = new IOFluidHandlerList(
                List.of(tankProxy), io, SteamTankValvePartMachine::isSteam, SteamTankValvePartMachine::isSteam);
        if (!useCoverCapability || side == null) return handler;
        CoverBehavior cover = getCoverContainer().getCoverAtSide(side);
        return cover == null ? handler : cover.getFluidHandlerCap(handler);
    }

    private static boolean isSteam(FluidStack stack) {
        return !stack.isEmpty() && stack.getFluid().is(GTMaterials.Steam.getFluidTag());
    }

    private boolean shouldAutoIO() {
        return isFormed() && (!outputMode || !tankProxy.isEmpty()) &&
                GTTransferUtils.hasAdjacentFluidHandler(getLevel(), getPos(), getFrontFacing());
    }

    private void autoIO() {
        Direction facing = getFrontFacing();
        if (outputMode) {
            tankProxy.exportToNearby(facing);
        } else {
            IFluidHandlerModifiable input = getFluidHandlerCap(facing, true);
            if (input != null) {
                GTTransferUtils.getAdjacentFluidHandler(getLevel(), getPos(), facing)
                        .ifPresent(source -> GTTransferUtils.transferFluidsFiltered(
                                source, input, SteamTankValvePartMachine::isSteam));
            }
        }
        autoIOSubscription.updateSubscription();
    }

    /** Uses the upstream valve's lit overlay to make output mode visible. */
    private void updateModeAppearance() {
        var status = outputMode ? RecipeLogic.Status.WORKING : RecipeLogic.Status.IDLE;
        var renderState = getRenderState();
        if (renderState.hasProperty(GTMachineModelProperties.RECIPE_LOGIC_STATUS)
                && renderState.getValue(GTMachineModelProperties.RECIPE_LOGIC_STATUS) != status) {
            setRenderState(renderState.setValue(GTMachineModelProperties.RECIPE_LOGIC_STATUS, status));
        }
    }

    @Override
    protected InteractionResult onScrewdriverClick(Player playerIn, InteractionHand hand, Direction gridSide,
                                                   BlockHitResult hitResult) {
        if (!isRemote()) {
            setOutputMode(!outputMode);
            playerIn.displayClientMessage(Component.translatable(
                    outputMode ? "gregsteamexpansion.machine.steam_tank_valve.mode.output" :
                            "gregsteamexpansion.machine.steam_tank_valve.mode.input"), true);
        }
        return InteractionResult.sidedSuccess(playerIn.level().isClientSide);
    }
}
