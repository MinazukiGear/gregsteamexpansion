package com.hoshino.gregsteamexpansion.data;

import com.hoshino.gregsteamexpansion.GregSteamExpansion;
import com.tterrag.registrate.providers.RegistrateItemModelProvider;

import net.minecraft.resources.ResourceLocation;

public final class GSEItemModels {
    private GSEItemModels() {}

    public static void init(RegistrateItemModelProvider provider) {
        provider.withExistingParent("crafting_station", modLoc("block/crafting_station"));
        provider.withExistingParent("crafting_station_slab", modLoc("block/crafting_station_slab"));

        // Block items reuse their block model; the bronze component is a flat
        // two-dimensional item texture (items-and-blocks.md 物品表现与提示).
        provider.withExistingParent("steam_grinding_block", modLoc("block/steam_grinding_block"));
        provider.withExistingParent("steam_assembly_block", modLoc("block/steam_assembly_block"));
        provider.withExistingParent("steam_circuit_assembly_block", modLoc("block/steam_circuit_assembly_block"));
        provider.withExistingParent("steam_mixing_block", modLoc("block/steam_mixing_block"));

        provider.withExistingParent("bronze_component", "minecraft:item/generated")
                .texture("layer0", modLoc("item/bronze_component"));
    }

    private static ResourceLocation modLoc(String path) {
        return GregSteamExpansion.id(path);
    }
}
