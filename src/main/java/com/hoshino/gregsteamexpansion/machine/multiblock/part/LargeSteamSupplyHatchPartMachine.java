package com.hoshino.gregsteamexpansion.machine.multiblock.part;

import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;

import net.minecraftforge.fluids.FluidType;

import javax.annotation.ParametersAreNonnullByDefault;

/**
 * 大型蒸汽供给仓 / Large Steam Supply Hatch: the high-capacity variant of
 * {@link SteamSupplyHatchPartMachine}. Filtering, input-only behavior, covers
 * and UI stay identical. Its single tank grows to {@code 256,000 mB}, large
 * machines draw through it at four times the ordinary rate, and its dedicated
 * ability keeps it out of smaller structures.
 */
@ParametersAreNonnullByDefault
public final class LargeSteamSupplyHatchPartMachine extends SteamSupplyHatchPartMachine {

    public static final int TANK_CAPACITY = 256 * FluidType.BUCKET_VOLUME;
    public static final int MACHINE_INPUT_RATE_MULTIPLIER = 4;

    public LargeSteamSupplyHatchPartMachine(IMachineBlockEntity holder, Object... args) {
        super(holder, TANK_CAPACITY, args);
    }

    @Override
    public int machineInputRateMultiplier() {
        return MACHINE_INPUT_RATE_MULTIPLIER;
    }
}
