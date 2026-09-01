package com.hoshino.gregsteamexpansion.registry;

import com.hoshino.gregsteamexpansion.GregSteamExpansion;

import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

public final class GSETags {
    public static final TagKey<Item> CO_FIRING_DUST_FUELS = TagKey.create(
            Registries.ITEM,
            GregSteamExpansion.id("co_firing_dust_fuels"));

    private GSETags() {}
}
