package com.hoshino.gregsteamexpansion.machine.multiblock.part;

import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.UITemplate;
import com.gregtechceu.gtceu.api.gui.widget.PhantomFluidWidget;
import com.gregtechceu.gtceu.api.gui.widget.TankWidget;
import com.gregtechceu.gtceu.api.gui.widget.ToggleButtonWidget;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.common.machine.multiblock.part.FluidHatchPartMachine;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableItemStackHandler;
import com.hoshino.gregsteamexpansion.registry.GSEMachines;
import com.gregtechceu.gtceu.config.ConfigHolder;

import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import com.lowdragmc.lowdraglib.gui.widget.ImageWidget;
import com.lowdragmc.lowdraglib.gui.widget.LabelWidget;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidType;

import javax.annotation.ParametersAreNonnullByDefault;

import java.util.List;

/**
 * 蒸汽流体输入/输出仓 / Steam Fluid Input/Output Hatch
 * (machines-and-hatches.md 已定案：蒸汽流体输入/输出仓).
 *
 * <p>One definition per direction ({@code IO.IN} / {@code IO.OUT}), both built
 * on this class with a single fixed {@code 16,000 mB} tank — the same scale as
 * an LV single-fluid hatch. The hatches only carry recipe fluids for steam-era
 * multiblocks: they register no {@code PartAbility.STEAM} membership and can
 * never feed steam as energy, and they expose no programming-circuit slot.</p>
 *
 * <p>World screwdriver swap ({@code swapIO()}) converts between the two
 * definitions while keeping fluid content, fluid lock, facings, paint color,
 * working state and every compatible cover; incompatible covers drop as items
 * instead of silently disappearing.</p>
 */
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class SteamFluidHatchPartMachine extends FluidHatchPartMachine {

    public static final int INITIAL_TANK_CAPACITY = 16 * FluidType.BUCKET_VOLUME;

    public SteamFluidHatchPartMachine(IMachineBlockEntity holder, IO io, Object... args) {
        super(holder, 0, io, INITIAL_TANK_CAPACITY, 1, args);
    }

    @Override
    protected NotifiableItemStackHandler createCircuitItemHandler(Object... args) {
        // Steam-era fluid hatches carry no programming-circuit slot; circuits
        // stay on the steam item buses (machines-and-hatches.md 明确不包含).
        return new NotifiableItemStackHandler(this, 0, IO.NONE);
    }

    //////////////////////////////////////
    // ********** GUI ***********//
    //////////////////////////////////////

    @Override
    public ModularUI createUI(Player entityPlayer) {
        boolean steel = ConfigHolder.INSTANCE.machines.steelSteamMultiblocks;
        var ui = new ModularUI(176, 166, this, entityPlayer)
                .background(GuiTextures.BACKGROUND_STEAM.get(steel))
                .widget(new LabelWidget(6, 6, getBlockState().getBlock().getDescriptionId()))
                .widget(new ImageWidget(7, 16, 81, 55, GuiTextures.DISPLAY_STEAM.get(steel)))
                .widget(new LabelWidget(11, 20, "gtceu.gui.fluid_amount"))
                .widget(new LabelWidget(11, 30, () -> String.format("%,d / %,d mB",
                        tank.getFluidInTank(0).getAmount(), INITIAL_TANK_CAPACITY)).setTextColor(-1)
                        .setDropShadow(true))
                .widget(new LabelWidget(11, 40, () -> tank.getFluidInTank(0).isEmpty() ? "" :
                        tank.getFluidInTank(0).getDisplayName().getString()));

        // Fluid slot + automation toggle on the right; the output hatch adds
        // its lock fluid phantom and lock button like the standard fluid hatch.
        ui.widget(new TankWidget(tank.getStorages()[0], 92, 25, true, io.support(IO.IN))
                .setBackground(GuiTextures.FLUID_SLOT));
        ui.widget(new ToggleButtonWidget(92, 47, 18, 18,
                io == IO.OUT ? GuiTextures.BUTTON_FLUID_OUTPUT : GuiTextures.BUTTON_ALLOW_IMPORT_EXPORT,
                this::isWorkingEnabled, this::setWorkingEnabled)
                .setShouldUseBaseBackground()
                .setTooltipText(io == IO.OUT ? "gtceu.gui.fluid_auto_output.tooltip" :
                        "gtceu.gui.fluid_auto_input.tooltip"));
        if (io == IO.OUT) {
            ui.widget(new PhantomFluidWidget(tank.getLockedFluid(), 0, 114, 25, 18, 18,
                    () -> tank.getLockedFluid().getFluid(), fluid -> {
                        if (!tank.getFluidInTank(0).isEmpty()) {
                            return;
                        }
                        if (fluid == null || fluid.isEmpty()) {
                            tank.setLocked(false);
                        } else {
                            FluidStack newFluid = fluid.copy();
                            newFluid.setAmount(1);
                            tank.setLocked(true, newFluid);
                        }
                    }).setShowAmount(false).setDrawHoverTips(true).setBackground(GuiTextures.FLUID_SLOT));
            ui.widget(new ToggleButtonWidget(114, 47, 18, 18, GuiTextures.BUTTON_LOCK,
                    tank::isLocked, tank::setLocked)
                    .setTooltipText("gtceu.gui.fluid_lock.tooltip")
                    .setShouldUseBaseBackground());
        } else {
            ui.widget(new ToggleButtonWidget(114, 47, 18, 18, GuiTextures.BUTTON_LOCK,
                    tank::isLocked, locked -> tank.setLocked(locked, tank.getFluidInTank(0)))
                    .setTooltipText("gtceu.gui.fluid_lock.tooltip")
                    .setShouldUseBaseBackground());
        }

        ui.widget(UITemplate.bindPlayerInventory(entityPlayer.getInventory(),
                GuiTextures.SLOT_STEAM.get(steel), 7, 84, true));
        return ui;
    }

    //////////////////////////////////////
    // ********** IO swap ***********//
    //////////////////////////////////////

    @Override
    public boolean swapIO() {
        Level level = getLevel();
        if (level == null || level.isClientSide) {
            return false;
        }
        MachineDefinition newDefinition = io == IO.IN ? GSEMachines.STEAM_FLUID_EXPORT_HATCH :
                GSEMachines.STEAM_FLUID_IMPORT_HATCH;
        if (newDefinition == null) {
            return false;
        }
        BlockPos pos = getHolder().pos();

        // Snapshot everything the swapped hatch must keep, then detach covers
        // without dropping them: MetaMachineBlock#onRemove would otherwise pop
        // every cover into the world during the block swap.
        Direction frontFacing = getFrontFacing();
        Direction upwardsFacing = getUpwardsFacing();
        int paintingColor = getPaintingColor();
        boolean workingEnabled = isWorkingEnabled();
        FluidStack fluidContent = tank.getFluidInTank(0).copy();
        boolean locked = tank.isLocked();
        FluidStack lockedFluid = tank.getLockedFluid().getFluid().copy();
        List<SteamHatchIOTransfer.CoverData> covers = SteamHatchIOTransfer.detachCoversSilently(this);

        level.setBlockAndUpdate(pos, newDefinition.getBlock().defaultBlockState());

        if (level.getBlockEntity(pos) instanceof IMachineBlockEntity newHolder &&
                newHolder.getMetaMachine() instanceof SteamFluidHatchPartMachine newMachine) {
            newMachine.setFrontFacing(frontFacing);
            newMachine.setUpwardsFacing(upwardsFacing);
            newMachine.setPaintingColor(paintingColor);
            newMachine.setWorkingEnabled(workingEnabled);
            newMachine.tank.setFluidInTank(0, fluidContent);
            if (locked) {
                newMachine.tank.setLocked(true, lockedFluid);
            }
            SteamHatchIOTransfer.restoreCovers(newMachine, covers, pos);
            newMachine.markDirty();
            return true;
        }
        // The swapped block was not created as expected; covers were already
        // detached from the removed machine, so drop them beside it.
        SteamHatchIOTransfer.dropCapturedCovers(level, pos, covers);
        return false;
    }
}
