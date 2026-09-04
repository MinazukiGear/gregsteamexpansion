package com.hoshino.gregsteamexpansion.registry;

import com.hoshino.gregsteamexpansion.GregSteamExpansion;
import com.hoshino.gregsteamexpansion.block.CraftingStationBlock;
import com.hoshino.gregsteamexpansion.block.CraftingStationSlabBlock;
import com.hoshino.gregsteamexpansion.block.CraftingStationSlabItem;
import com.hoshino.gregsteamexpansion.item.GSETooltipBlockItem;
import com.hoshino.gregsteamexpansion.item.GSETooltipItem;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class GSEBlocks {
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, GregSteamExpansion.MOD_ID);
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, GregSteamExpansion.MOD_ID);

    public static final RegistryObject<Block> CRAFTING_STATION = BLOCKS.register("crafting_station",
            () -> new CraftingStationBlock(BlockBehaviour.Properties.of()
                    .strength(2.5F, 2.5F)
                    .sound(SoundType.WOOD)));

    public static final RegistryObject<Block> CRAFTING_STATION_SLAB = BLOCKS.register("crafting_station_slab",
            () -> new CraftingStationSlabBlock(BlockBehaviour.Properties.of()
                    .strength(2.5F, 2.5F)
                    .sound(SoundType.WOOD)
                    .noOcclusion()));

    public static final RegistryObject<Item> CRAFTING_STATION_ITEM = ITEMS.register("crafting_station",
            () -> new BlockItem(CRAFTING_STATION.get(), new Item.Properties()));

    public static final RegistryObject<Item> CRAFTING_STATION_SLAB_ITEM = ITEMS.register("crafting_station_slab",
            () -> new CraftingStationSlabItem(CRAFTING_STATION_SLAB.get(), new Item.Properties()));

    // Plain structural blocks for upcoming steam multiblocks (items-and-blocks.md):
    // no block entity, no GUI, no rotation; pickaxe or GTCEu wrench mining with
    // correct-tool drop requirement, 5.0 hardness / 6.0 blast resistance, metal sound.
    private static BlockBehaviour.Properties structuralBlockProperties() {
        return BlockBehaviour.Properties.of()
                .strength(5.0F, 6.0F)
                .sound(SoundType.METAL)
                .requiresCorrectToolForDrops();
    }

    public static final RegistryObject<Block> STEAM_GRINDING_BLOCK = BLOCKS.register("steam_grinding_block",
            () -> new Block(structuralBlockProperties()));

    public static final RegistryObject<Block> STEAM_ASSEMBLY_BLOCK = BLOCKS.register("steam_assembly_block",
            () -> new Block(structuralBlockProperties()));

    public static final RegistryObject<Block> STEAM_CIRCUIT_ASSEMBLY_BLOCK = BLOCKS.register("steam_circuit_assembly_block",
            () -> new Block(structuralBlockProperties()));

    public static final RegistryObject<Block> STEAM_MIXING_BLOCK = BLOCKS.register("steam_mixing_block",
            () -> new Block(structuralBlockProperties()));

    public static final RegistryObject<Item> STEAM_GRINDING_BLOCK_ITEM = ITEMS.register("steam_grinding_block",
            () -> new GSETooltipBlockItem(STEAM_GRINDING_BLOCK.get(), new Item.Properties(),
                    "gregsteamexpansion.steam_grinding_block.tooltip"));

    public static final RegistryObject<Item> STEAM_ASSEMBLY_BLOCK_ITEM = ITEMS.register("steam_assembly_block",
            () -> new GSETooltipBlockItem(STEAM_ASSEMBLY_BLOCK.get(), new Item.Properties(),
                    "gregsteamexpansion.steam_assembly_block.tooltip"));

    public static final RegistryObject<Item> STEAM_CIRCUIT_ASSEMBLY_BLOCK_ITEM = ITEMS.register("steam_circuit_assembly_block",
            () -> new GSETooltipBlockItem(STEAM_CIRCUIT_ASSEMBLY_BLOCK.get(), new Item.Properties(),
                    "gregsteamexpansion.steam_circuit_assembly_block.tooltip"));

    public static final RegistryObject<Item> STEAM_MIXING_BLOCK_ITEM = ITEMS.register("steam_mixing_block",
            () -> new GSETooltipBlockItem(STEAM_MIXING_BLOCK.get(), new Item.Properties(),
                    "gregsteamexpansion.steam_mixing_block.tooltip"));

    /** Standardized bronze load-bearing component shared by steam-era recipes. */
    public static final RegistryObject<Item> BRONZE_COMPONENT = ITEMS.register("bronze_component",
            () -> new GSETooltipItem(new Item.Properties(),
                    "gregsteamexpansion.bronze_component.tooltip"));

    private GSEBlocks() {}
}
