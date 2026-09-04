package com.hoshino.gregsteamexpansion.data;

import com.gregtechceu.gtceu.data.recipe.CustomTags;
import com.hoshino.gregsteamexpansion.registry.GSEBlocks;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.tags.TagsProvider;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import com.tterrag.registrate.providers.RegistrateTagsProvider;

import net.minecraftforge.registries.RegistryObject;

import java.util.List;

/**
 * Mining tags for the plain structural blocks (items-and-blocks.md 物理属性与采集):
 * harvestable by pickaxe or a GTCEu wrench, matching GTCEu machine casings. The
 * correct-tool drop requirement itself lives on the block properties.
 */
public final class GSEBlockTags {
    private GSEBlockTags() {}

    public static void init(RegistrateTagsProvider<Block> provider) {
        addToTag(provider, BlockTags.MINEABLE_WITH_PICKAXE);
        addToTag(provider, CustomTags.MINEABLE_WITH_CONFIG_VALID_PICKAXE_WRENCH);
    }

    private static void addToTag(RegistrateTagsProvider<Block> provider, TagKey<Block> tag) {
        TagsProvider.TagAppender<Block> appender = provider.addTag(tag);
        for (RegistryObject<Block> block : structuralBlocks()) {
            appender.add(BuiltInRegistries.BLOCK.getResourceKey(block.get()).orElseThrow());
        }
    }

    private static List<RegistryObject<Block>> structuralBlocks() {
        return List.of(
                GSEBlocks.STEAM_GRINDING_BLOCK,
                GSEBlocks.STEAM_ASSEMBLY_BLOCK,
                GSEBlocks.STEAM_CIRCUIT_ASSEMBLY_BLOCK,
                GSEBlocks.STEAM_MIXING_BLOCK);
    }
}
