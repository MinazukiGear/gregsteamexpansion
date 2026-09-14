package com.hoshino.gregsteamexpansion.machine.multiblock.part;

import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.UITemplate;
import com.gregtechceu.gtceu.api.gui.widget.TankWidget;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.common.machine.multiblock.part.FluidHatchPartMachine;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableFluidTank;
import com.gregtechceu.gtceu.api.transfer.fluid.CustomFluidTank;
import com.gregtechceu.gtceu.common.data.GTMaterials;
import com.gregtechceu.gtceu.config.ConfigHolder;

import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import com.lowdragmc.lowdraglib.gui.widget.ImageWidget;
import com.lowdragmc.lowdraglib.gui.widget.LabelWidget;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;

import org.jetbrains.annotations.NotNull;

import java.util.List;

import javax.annotation.ParametersAreNonnullByDefault;

/**
 * 蒸汽供给仓 / Steam Supply Hatch (machines-and-hatches.md 已定案：蒸汽供给仓):
 * the steam-age steam energy interface registered by this mod, replacing the
 * legacy upstream steam input hatch as the standard supply part accepted by
 * every steam multiblock. Large machines may additionally accept its upgraded
 * high-throughput variant.
 *
 * <p>The hatch stores exactly one input slot of {@code 32,000 mB} — half of the
 * upstream cache — and only accepts GTCEu standard steam (the fluid's own steam
 * tag, so same-named third-party steams and superheated steam stay out). It
 * never produces or consumes steam itself and its {@code swapIO()} is fixed to
 * disallowed: it can never be turned into an output hatch.</p>
 *
 * <p>Steam energy controllers discover this part through
 * {@code PartAbility.STEAM} plus its steam-filtered {@link NotifiableFluidTank},
 * mirroring how {@code SteamParallelMultiblockMachine} locates steam hatches.
 * Structure-side per-hatch flow limits (e.g. the furnace's {@code 1,200 mB/t})
 * remain the consuming machine's responsibility.</p>
 */
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class SteamSupplyHatchPartMachine extends FluidHatchPartMachine {

    public static final int INITIAL_TANK_CAPACITY = 32 * FluidType.BUCKET_VOLUME;
    public static final int MACHINE_INPUT_RATE_MULTIPLIER = 1;

    public SteamSupplyHatchPartMachine(IMachineBlockEntity holder, Object... args) {
        this(holder, INITIAL_TANK_CAPACITY, args);
    }

    protected SteamSupplyHatchPartMachine(IMachineBlockEntity holder, int tankCapacity, Object... args) {
        super(holder, 0, IO.IN, tankCapacity, 1, args);
    }

    @Override
    protected NotifiableFluidTank createTank(int initialCapacity, int slots, Object... args) {
        CustomFluidTank storage = new OverflowSafeFluidTank(getTankCapacity(initialCapacity, getTier()));
        return new NotifiableFluidTank(this, List.of(storage), IO.IN)
                .setFilter(fluidStack -> fluidStack.getFluid().is(GTMaterials.Steam.getFluidTag()));
    }

    /**
     * Forge's base tank assumes stored content never exceeds its capacity. A
     * migrated legacy hatch may legitimately contain more, so reject fills
     * before the base implementation can calculate a negative free space.
     */
    private static final class OverflowSafeFluidTank extends CustomFluidTank {

        private OverflowSafeFluidTank(int capacity) {
            super(capacity);
        }

        @Override
        public int fill(@NotNull FluidStack resource, FluidAction action) {
            if (getFluidAmount() >= getCapacity()) {
                return 0;
            }
            return super.fill(resource, action);
        }
    }

    @Override
    public ModularUI createUI(Player entityPlayer) {
        boolean steel = ConfigHolder.INSTANCE.machines.steelSteamMultiblocks;
        return new ModularUI(176, 166, this, entityPlayer)
                .background(GuiTextures.BACKGROUND_STEAM.get(steel))
                .widget(new LabelWidget(6, 6, getBlockState().getBlock().getDescriptionId()))
                .widget(new ImageWidget(7, 16, 81, 55, GuiTextures.DISPLAY_STEAM.get(steel)))
                .widget(new LabelWidget(11, 20, "gtceu.gui.fluid_amount"))
                .widget(new LabelWidget(11, 30, () -> formatTankAmount()).setTextColor(-1)
                        .setDropShadow(true))
                .widget(new TankWidget(tank.getStorages()[0], 90, 35, true, true)
                        .setBackground(GuiTextures.FLUID_SLOT))
                .widget(UITemplate.bindPlayerInventory(entityPlayer.getInventory(),
                        GuiTextures.SLOT_STEAM.get(steel), 7, 84, true));
    }

    private String formatTankAmount() {
        // Tooltip, GUI, Jade and the fluid capability must all report the same
        // real capacity. Migrated over-limit legacy content may display above
        // it, which is exactly what the tank holds.
        return String.format("%,d / %,d mB", tank.getFluidInTank(0).getAmount(), tank.getTankCapacity(0));
    }

    /** Multiplier applied to the consuming machine's ordinary per-hatch limit. */
    public int machineInputRateMultiplier() {
        return MACHINE_INPUT_RATE_MULTIPLIER;
    }

    // A steam supply hatch only feeds steam in; a screwdriver must never turn
    // it into a steam output hatch (machines-and-hatches.md swapIO 固定不允许).
    @Override
    public boolean swapIO() {
        return false;
    }
}
