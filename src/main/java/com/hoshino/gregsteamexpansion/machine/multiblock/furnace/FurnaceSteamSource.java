package com.hoshino.gregsteamexpansion.machine.multiblock.furnace;

import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;

import net.minecraftforge.fluids.capability.IFluidHandler;

/**
 * Explicit extension contract for future dedicated steam hatches used by the
 * large heat-storage steam furnace.
 *
 * <p>An implementing part must also register
 * {@code GSEPartAbilities.FURNACE_STEAM_SOURCE}. The first release deliberately
 * has no implementation and the furnace pattern does not accept that ability
 * yet: an extension must add its mixing, batch-locking and persistence policy
 * before its source becomes usable.</p>
 */
public interface FurnaceSteamSource extends IMultiPart {

    FurnaceSteamSourceSpec getFurnaceSteamSourceSpec();

    /** Fluid handler from which a future furnace integration simulates and drains. */
    IFluidHandler getFurnaceSteamHandler();
}
