package com.hoshino.gregsteamexpansion.machine.multiblock.furnace;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fluids.FluidStack;

import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Immutable declaration supplied by a future dedicated furnace steam hatch.
 *
 * <p>The heat value is an exact ratio relative to one millibucket of standard
 * GTCEu steam. Capped sources declare a positive machine-side input limit;
 * unlimited sources use the explicit flag and a zero limit. The accepting
 * predicate is the sole authority for the source fluid, so consumers must not
 * infer support from names, tags, temperature or stored quantity.</p>
 */
public record FurnaceSteamSourceSpec(
        ResourceLocation steamTypeId,
        Predicate<FluidStack> acceptedFluid,
        long heatValueNumerator,
        long heatValueDenominator,
        boolean unlimitedInput,
        long inputLimitMbPerTick,
        int priority,
        Set<FurnaceSteamCapability> capabilities) {

    public FurnaceSteamSourceSpec {
        Objects.requireNonNull(steamTypeId, "steamTypeId");
        Objects.requireNonNull(acceptedFluid, "acceptedFluid");
        Objects.requireNonNull(capabilities, "capabilities");
        if (heatValueNumerator <= 0 || heatValueDenominator <= 0) {
            throw new IllegalArgumentException("Furnace steam heat value must be a positive ratio");
        }
        if (unlimitedInput ? inputLimitMbPerTick != 0 : inputLimitMbPerTick <= 0) {
            throw new IllegalArgumentException(
                    "Unlimited furnace steam sources use a zero limit; capped sources need a positive limit");
        }
        capabilities = Set.copyOf(capabilities);
    }

    public static FurnaceSteamSourceSpec capped(ResourceLocation steamTypeId,
                                                 Predicate<FluidStack> acceptedFluid,
                                                 long heatValueNumerator,
                                                 long heatValueDenominator,
                                                 long inputLimitMbPerTick,
                                                 int priority,
                                                 Set<FurnaceSteamCapability> capabilities) {
        return new FurnaceSteamSourceSpec(steamTypeId, acceptedFluid,
                heatValueNumerator, heatValueDenominator, false, inputLimitMbPerTick,
                priority, capabilities);
    }

    public static FurnaceSteamSourceSpec unlimited(ResourceLocation steamTypeId,
                                                    Predicate<FluidStack> acceptedFluid,
                                                    long heatValueNumerator,
                                                    long heatValueDenominator,
                                                    int priority,
                                                    Set<FurnaceSteamCapability> capabilities) {
        return new FurnaceSteamSourceSpec(steamTypeId, acceptedFluid,
                heatValueNumerator, heatValueDenominator, true, 0, priority, capabilities);
    }

    /** Rejects empty stacks before consulting the provider's strict predicate. */
    public boolean accepts(FluidStack stack) {
        return stack != null && !stack.isEmpty() && acceptedFluid.test(stack);
    }

    public boolean unlocks(FurnaceSteamCapability capability) {
        return capabilities.contains(capability);
    }
}
