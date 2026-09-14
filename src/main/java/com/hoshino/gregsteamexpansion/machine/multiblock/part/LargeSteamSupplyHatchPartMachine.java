package com.hoshino.gregsteamexpansion.machine.multiblock.part;

import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;

import net.minecraftforge.fluids.FluidType;

import javax.annotation.ParametersAreNonnullByDefault;

/**
 * 大型蒸汽供给仓 / Large Steam Supply Hatch: the high-capacity variant of
 * {@link SteamSupplyHatchPartMachine}. Filtering, input-only behavior, covers,
 * UI and multiblock ability stay identical; only the single tank grows to
 * {@code 256,000 mB}.
 */
@ParametersAreNonnullByDefault
public final class LargeSteamSupplyHatchPartMachine extends SteamSupplyHatchPartMachine {

    public static final int TANK_CAPACITY = 256 * FluidType.BUCKET_VOLUME;

    public LargeSteamSupplyHatchPartMachine(IMachineBlockEntity holder, Object... args) {
        super(holder, TANK_CAPACITY, args);
    }
}
