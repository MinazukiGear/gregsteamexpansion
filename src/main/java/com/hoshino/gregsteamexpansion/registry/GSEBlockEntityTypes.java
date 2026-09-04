package com.hoshino.gregsteamexpansion.registry;

import com.hoshino.gregsteamexpansion.GregSteamExpansion;
import com.hoshino.gregsteamexpansion.blockentity.CraftingStationBlockEntity;

import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class GSEBlockEntityTypes {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, GregSteamExpansion.MOD_ID);

    // The normal form and the attached vertical-slab form share one block entity type:
    // both blocks expose identical inventory and crafting semantics.
    public static final RegistryObject<BlockEntityType<CraftingStationBlockEntity>> CRAFTING_STATION =
            BLOCK_ENTITY_TYPES.register("crafting_station",
                    () -> BlockEntityType.Builder.of(CraftingStationBlockEntity::new,
                            GSEBlocks.CRAFTING_STATION.get(), GSEBlocks.CRAFTING_STATION_SLAB.get())
                            .build(null));

    private GSEBlockEntityTypes() {}
}
