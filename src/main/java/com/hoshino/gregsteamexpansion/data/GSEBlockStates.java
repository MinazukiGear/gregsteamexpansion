package com.hoshino.gregsteamexpansion.data;

import com.hoshino.gregsteamexpansion.GregSteamExpansion;
import com.hoshino.gregsteamexpansion.registry.GSEBlocks;
import com.tterrag.registrate.providers.RegistrateBlockstateProvider;

import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraftforge.client.model.generators.ConfiguredModel;
import net.minecraftforge.client.model.generators.ModelFile;
import net.minecraftforge.registries.RegistryObject;

public final class GSEBlockStates {
    private GSEBlockStates() {}

    public static void init(RegistrateBlockstateProvider provider) {
        ModelFile stationModel = provider.models().cubeBottomTop("crafting_station",
                modLoc("block/crafting_station/side"),
                modLoc("block/crafting_station/bottom"),
                modLoc("block/crafting_station/top"));
        provider.getVariantBuilder(GSEBlocks.CRAFTING_STATION.get())
                .partialState()
                .setModels(new ConfiguredModel(stationModel));        // Thin panel flush against the attached container side; the base model
        // sits on the west edge and is rotated per FACING (crafting-station.md 5.2).
        ModelFile slabModel = provider.models().getBuilder("crafting_station_slab")
                .parent(new ModelFile.UncheckedModelFile("minecraft:block/block"))
                .texture("particle", modLoc("block/crafting_station/top"))
                .texture("top", modLoc("block/crafting_station/top"))
                .texture("side", modLoc("block/crafting_station/side"))
                .texture("bottom", modLoc("block/crafting_station/bottom"))
                .element()
                        .from(0F, 0F, 0F).to(4F, 16F, 16F)
                        .face(Direction.DOWN).texture("#bottom").cullface(Direction.DOWN).end()
                        .face(Direction.UP).texture("#top").cullface(Direction.UP).end()
                        .face(Direction.NORTH).texture("#side").cullface(Direction.NORTH).end()
                        .face(Direction.SOUTH).texture("#side").cullface(Direction.SOUTH).end()
                        .face(Direction.WEST).texture("#side").cullface(Direction.WEST).end()
                        .face(Direction.EAST).texture("#side").end()
                .end();
        provider.getVariantBuilder(GSEBlocks.CRAFTING_STATION_SLAB.get())
                .forAllStates(state -> ConfiguredModel.builder()
                        .modelFile(slabModel)
                        .rotationY(slabRotation(state.getValue(BlockStateProperties.HORIZONTAL_FACING)))
                        .build());

        steamStructureBlock(provider, GSEBlocks.STEAM_GRINDING_BLOCK, "steam_grinding_block");
        steamStructureBlock(provider, GSEBlocks.STEAM_ASSEMBLY_BLOCK, "steam_assembly_block");
        steamStructureBlock(provider, GSEBlocks.STEAM_CIRCUIT_ASSEMBLY_BLOCK, "steam_circuit_assembly_block");
        steamStructureBlock(provider, GSEBlocks.STEAM_MIXING_BLOCK, "steam_mixing_block");
    }

    /**
     * Static full cubes for the plain steam structure blocks
     * (items-and-blocks.md 模型与纹理方向): no rotation states, side/top/bottom
     * textures may differ but carry no facing semantics.
     */
    private static void steamStructureBlock(RegistrateBlockstateProvider provider,
                                            RegistryObject<Block> block,
                                            String name) {
        ModelFile model = provider.models().cubeBottomTop(name,
                modLoc("block/" + name + "/side"),
                modLoc("block/" + name + "/bottom"),
                modLoc("block/" + name + "/top"));
        provider.getVariantBuilder(block.get())
                .partialState()
                .setModels(new ConfiguredModel(model));
    }

    private static int slabRotation(Direction facing) {
        return switch (facing) {
            case WEST -> 180;
            case SOUTH -> 90;
            case NORTH -> 270;
            default -> 0;
        };
    }

    private static ResourceLocation modLoc(String path) {
        return GregSteamExpansion.id(path);
    }
}
