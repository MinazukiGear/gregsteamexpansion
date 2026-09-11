package com.hoshino.gregsteamexpansion.machine.multiblock;

import com.gregtechceu.gtceu.common.data.GTMaterials;
import com.gregtechceu.gtceu.common.machine.multiblock.part.FluidHatchPartMachine;
import com.hoshino.gregsteamexpansion.machine.multiblock.part.SteamAirIntakeHatchPartMachine;

import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;

import java.util.List;
import java.util.function.LongConsumer;

/**
 * Shared resource budget for steam-powered multiblock controllers.
 *
 * <p>Physical hatches are consumed in stable part order and have a per-tick
 * machine-side cap. Optional uncapped hatches, currently the heat-storage
 * furnace's ME fluid inputs, supply only the remainder. A full draw is always
 * simulated before any tank is changed.</p>
 */
public final class SteamBudget {

    public static final long PHYSICAL_HATCH_LIMIT_MB = 1_200;

    private final List<? extends FluidHatchPartMachine> physicalHatches;
    private final List<? extends FluidHatchPartMachine> uncappedHatches;
    private final long physicalHatchLimitMb;

    public SteamBudget(List<? extends FluidHatchPartMachine> physicalHatches) {
        this(physicalHatches, List.of(), PHYSICAL_HATCH_LIMIT_MB);
    }

    public SteamBudget(List<? extends FluidHatchPartMachine> physicalHatches,
                       List<? extends FluidHatchPartMachine> uncappedHatches,
                       long physicalHatchLimitMb) {
        if (physicalHatchLimitMb <= 0) {
            throw new IllegalArgumentException("Physical steam hatch limit must be positive");
        }
        this.physicalHatches = physicalHatches;
        this.uncappedHatches = uncappedHatches;
        this.physicalHatchLimitMb = physicalHatchLimitMb;
    }

    /**
     * Atomically consumes exact GTCEu steam. The callback receives an execution
     * shortfall that occurred after a successful simulation.
     */
    public boolean drawSteam(long amountMb, LongConsumer executionShortfall) {
        if (amountMb <= 0 || physicalHatches.isEmpty() && uncappedHatches.isEmpty()) {
            return false;
        }
        if (drainSteam(amountMb, IFluidHandler.FluidAction.SIMULATE) > 0) {
            return false;
        }
        long remaining = drainSteam(amountMb, IFluidHandler.FluidAction.EXECUTE);
        if (remaining > 0) {
            executionShortfall.accept(remaining);
            return false;
        }
        return true;
    }

    private long drainSteam(long amountMb, IFluidHandler.FluidAction action) {
        long remaining = amountMb;
        for (FluidHatchPartMachine hatch : physicalHatches) {
            long share = Math.min(remaining, physicalHatchLimitMb);
            remaining -= hatch.tank.drainInternal(steam(share), action).getAmount();
            if (remaining <= 0) {
                return 0;
            }
        }
        for (FluidHatchPartMachine hatch : uncappedHatches) {
            remaining -= hatch.tank.drainInternal(steam(remaining), action).getAmount();
            if (remaining <= 0) {
                return 0;
            }
        }
        return remaining;
    }

    /** Machine-side physical-hatch throughput; excludes uncapped sources. */
    public long physicalInputLimitMb() {
        return (long) physicalHatches.size() * physicalHatchLimitMb;
    }

    /** Effective machine-side throughput, or {@link Long#MAX_VALUE} when uncapped. */
    public long inputLimitMb() {
        return isUnlimited() ? Long.MAX_VALUE : physicalInputLimitMb();
    }

    public boolean isUnlimited() {
        return !uncappedHatches.isEmpty();
    }

    /** Stored fluid in the physical supply hatches. */
    public long totalStoredMb() {
        long total = 0;
        for (FluidHatchPartMachine hatch : physicalHatches) {
            total += hatch.tank.getFluidInTank(0).getAmount();
        }
        return total;
    }

    /** Capacity of the physical supply hatches. */
    public long totalCapacityMb() {
        long total = 0;
        for (FluidHatchPartMachine hatch : physicalHatches) {
            total += hatch.tank.getTankCapacity(0);
        }
        return total;
    }

    /**
     * Draws one phase of the blast furnace's auxiliary-air plan across all
     * intakes. Call with SIMULATE before steam, then EXECUTE after steam.
     */
    public static boolean drawBlastAir(List<SteamAirIntakeHatchPartMachine> intakes,
                                       long amountMb,
                                       IFluidHandler.FluidAction action) {
        if (amountMb <= 0) {
            return true;
        }
        if (intakes.isEmpty()) {
            return false;
        }
        long remaining = amountMb;
        for (SteamAirIntakeHatchPartMachine intake : intakes) {
            FluidStack requested = GTMaterials.Air.getFluid((int) Math.min(remaining, Integer.MAX_VALUE));
            remaining -= intake.tank.drainInternal(requested, action).getAmount();
            if (remaining <= 0) {
                return true;
            }
        }
        return false;
    }

    private static FluidStack steam(long amountMb) {
        return GTMaterials.Steam.getFluid((int) Math.min(amountMb, Integer.MAX_VALUE));
    }
}
