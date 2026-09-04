package com.hoshino.gregsteamexpansion.data;

import com.hoshino.gregsteamexpansion.GregSteamExpansion;
import com.hoshino.gregsteamexpansion.registry.GSEBlocks;
import com.tterrag.registrate.providers.loot.RegistrateLootTableProvider;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.predicates.ExplosionCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraftforge.registries.RegistryObject;

public final class GSELoot {
    private GSELoot() {}

    public static void init(RegistrateLootTableProvider provider) {
        provider.addLootAction(LootContextParamSets.BLOCK, tables -> {
            // Both forms drop the full crafting station item; block entity
            // contents are dropped separately in code (crafting-station.md 2.3 / 5.4).
            tables.accept(modLoc("blocks/crafting_station"), selfDrop(GSEBlocks.CRAFTING_STATION_ITEM));
            tables.accept(modLoc("blocks/crafting_station_slab"), selfDrop(GSEBlocks.CRAFTING_STATION_ITEM));

            // Plain steam structure blocks drop themselves; embedded parts
            // (grinding head, rotors) are never returned (items-and-blocks.md).
            tables.accept(modLoc("blocks/steam_grinding_block"), selfDrop(GSEBlocks.STEAM_GRINDING_BLOCK_ITEM));
            tables.accept(modLoc("blocks/steam_assembly_block"), selfDrop(GSEBlocks.STEAM_ASSEMBLY_BLOCK_ITEM));
            tables.accept(modLoc("blocks/steam_circuit_assembly_block"), selfDrop(GSEBlocks.STEAM_CIRCUIT_ASSEMBLY_BLOCK_ITEM));
            tables.accept(modLoc("blocks/steam_mixing_block"), selfDrop(GSEBlocks.STEAM_MIXING_BLOCK_ITEM));
        });
    }

    private static LootTable.Builder selfDrop(RegistryObject<? extends net.minecraft.world.item.Item> item) {
        return LootTable.lootTable()
                .withPool(LootPool.lootPool()
                        .setRolls(ConstantValue.exactly(1.0F))
                        .add(LootItem.lootTableItem(item.get()))
                        .when(ExplosionCondition.survivesExplosion()));
    }

    private static ResourceLocation modLoc(String path) {
        return GregSteamExpansion.id(path);
    }
}
