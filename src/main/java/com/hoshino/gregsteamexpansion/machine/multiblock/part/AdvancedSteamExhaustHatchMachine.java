package com.hoshino.gregsteamexpansion.machine.multiblock.part;

import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;

import net.minecraft.MethodsReturnNonnullByDefault;

import javax.annotation.ParametersAreNonnullByDefault;

/**
 * High-efficiency variant of the steam exhaust hatch for large steam
 * multiblocks. It retains the ordinary hatch's obstruction and hazard
 * behavior while reducing newly locked steam demand by 33%.
 */
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public final class AdvancedSteamExhaustHatchMachine extends SteamExhaustHatchMachine {

    public static final int STEAM_PERCENT = 67;

    public AdvancedSteamExhaustHatchMachine(IMachineBlockEntity holder) {
        super(holder);
    }

    @Override
    public long modifySteamConsumption(long steamMb) {
        return discountedSteam(steamMb);
    }

    public static long discountedSteam(long steamMb) {
        if (steamMb <= 0) {
            return 0;
        }
        long hundreds = steamMb / 100;
        long remainder = steamMb % 100;
        return hundreds * STEAM_PERCENT + (remainder * STEAM_PERCENT + 99) / 100;
    }
}
