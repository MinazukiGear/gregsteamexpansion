package com.hoshino.gregsteamexpansion.machine.multiblock.furnace;

import com.hoshino.gregsteamexpansion.GregSteamExpansion;

import net.minecraft.resources.ResourceLocation;

/**
 * Stable opt-in capability identifiers exposed by future dedicated furnace
 * steam sources. Merely declaring a capability does not enable its behavior;
 * the furnace implementation must explicitly support it as part of the same
 * extension.
 */
public enum FurnaceSteamCapability {

    /** Allows one locked furnace batch to contain more than one recipe. */
    ALLOW_MULTI_RECIPE_BATCH(GregSteamExpansion.id("allow_multi_recipe_batch"));

    private final ResourceLocation id;

    FurnaceSteamCapability(ResourceLocation id) {
        this.id = id;
    }

    public ResourceLocation id() {
        return id;
    }
}
