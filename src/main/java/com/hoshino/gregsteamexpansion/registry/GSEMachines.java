package com.hoshino.gregsteamexpansion.registry;

import com.gregtechceu.gtceu.api.data.RotationState;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.property.GTMachineModelProperties;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;
import com.gregtechceu.gtceu.api.registry.registrate.MachineBuilder;
import com.gregtechceu.gtceu.api.registry.registrate.provider.GTBlockstateProvider;
import com.gregtechceu.gtceu.client.model.machine.overlays.WorkableOverlays;
import com.gregtechceu.gtceu.common.data.GTBlocks;
import com.gregtechceu.gtceu.common.data.GCYMBlocks;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.gregtechceu.gtceu.common.data.machines.GTMachineUtils;
import com.gregtechceu.gtceu.common.data.models.GTMachineModels;
import com.gregtechceu.gtceu.data.model.builder.MachineModelBuilder;
import com.hoshino.gregsteamexpansion.GregSteamExpansion;
import com.hoshino.gregsteamexpansion.cokeoven.LargeCokeOvenStructures;
import com.hoshino.gregsteamexpansion.migration.OreCrushingMigration;
import com.hoshino.gregsteamexpansion.registry.GSERecipeTypes;
import com.hoshino.gregsteamexpansion.machine.multiblock.BoilerRoomMachine;
import com.hoshino.gregsteamexpansion.machine.multiblock.LargeHeatStorageSteamFurnaceMachine;
import com.hoshino.gregsteamexpansion.machine.multiblock.voidproducer.LargeSteamFluidDrillMachine;
import com.hoshino.gregsteamexpansion.machine.multiblock.voidproducer.LargeSteamOrePlantMachine;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.machine.SimpleTieredMachine;
import com.gregtechceu.gtceu.common.data.GTRecipeModifiers;
import com.gregtechceu.gtceu.common.data.machines.GTMachineUtils;
import com.hoshino.gregsteamexpansion.machine.multiblock.largecokeoven.LargeCokeOvenMachine;
import com.hoshino.gregsteamexpansion.machine.multiblock.crusher.LargeSteamCrusherMachine;
import com.hoshino.gregsteamexpansion.machine.multiblock.crusher.SteamCrusherMachine;
import com.hoshino.gregsteamexpansion.machine.multiblock.processor.LargeSteamAssemblerMachine;
import com.hoshino.gregsteamexpansion.machine.multiblock.processor.LargeSteamBlastFurnaceMachine;
import com.hoshino.gregsteamexpansion.machine.multiblock.processor.LargeSteamCentrifugeMachine;
import com.hoshino.gregsteamexpansion.machine.multiblock.processor.LargeSteamCircuitAssemblerMachine;
import com.hoshino.gregsteamexpansion.machine.multiblock.processor.SteamCentrifugeMachine;
import com.hoshino.gregsteamexpansion.machine.multiblock.processor.SteamChemicalBathMachine;
import com.hoshino.gregsteamexpansion.machine.multiblock.processor.LargeSteamMaceratorMachine;
import com.hoshino.gregsteamexpansion.machine.multiblock.processor.LargeSteamMixerMachine;
import com.hoshino.gregsteamexpansion.machine.multiblock.processor.LargeSteamOreWasherMachine;
import com.hoshino.gregsteamexpansion.machine.multiblock.processor.LargeSteamThermalCentrifugeMachine;
import com.hoshino.gregsteamexpansion.machine.multiblock.processor.SteamCompressorMachine;
import com.hoshino.gregsteamexpansion.machine.multiblock.processor.SteamExtractorMachine;
import com.hoshino.gregsteamexpansion.machine.multiblock.processor.SteamForgeMachine;
import com.hoshino.gregsteamexpansion.machine.multiblock.part.SteamAirIntakeHatchPartMachine;
import com.hoshino.gregsteamexpansion.machine.multiblock.part.LargeCokeOvenHatchPartMachine;
import com.hoshino.gregsteamexpansion.machine.multiblock.part.SteamExhaustHatchMachine;
import com.hoshino.gregsteamexpansion.machine.multiblock.part.SteamFluidHatchPartMachine;
import com.hoshino.gregsteamexpansion.machine.multiblock.part.SteamSupplyHatchPartMachine;
import com.hoshino.gregsteamexpansion.machine.steam.MixedFuelBoilerMachine;

import com.gregtechceu.gtceu.api.capability.recipe.IO;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;

import com.gregtechceu.gtceu.api.pattern.MultiblockShapeInfo;
import net.minecraftforge.client.model.generators.BlockModelBuilder;

import com.tterrag.registrate.providers.DataGenContext;

import it.unimi.dsi.fastutil.Pair;

import java.util.ArrayList;

import java.util.List;

public final class GSEMachines {
    public static final Pair<MachineDefinition, MachineDefinition> MIXED_FUEL_BOILER =
            GTMachineUtils.registerSteamMachines(
                    GSERegistration.REGISTRATE,
                    "steam_mixed_fuel_boiler",
                    MixedFuelBoilerMachine::new,
                    (highPressure, builder) -> builder
                            .langValue(highPressure ? "High Pressure Mixed-Fuel Steam Boiler" :
                                    "Low Pressure Mixed-Fuel Steam Boiler")
                            .rotationState(RotationState.ALL)
                            .recipeType(GTRecipeTypes.STEAM_BOILER_RECIPES)
                            .recipeModifier(MixedFuelBoilerMachine::recipeModifier)
                            .regressWhenWaiting(false)
                            .modelProperty(GTMachineModelProperties.RECIPE_LOGIC_STATUS, RecipeLogic.Status.IDLE)
                            .model(mixedFuelBoilerModel(highPressure,
                                    GregSteamExpansion.gtceuId("block/generators/boiler/coal")))
                            .tooltips(
                                    Component.translatable("gtceu.universal.tooltip.produces_fluid",
                                            highPressure ? 40 : 16),
                                    Component.translatable(
                                            "gregsteamexpansion.machine.mixed_fuel_boiler.tooltip.co_firing"))
                            .register());

    /**
     * 蒸汽排气仓 / Steam Exhaust Hatch (large-heat-storage-steam-furnace.md):
     * generic exhaust interface for compatible multiblock steam machines,
     * registered without furnace-specific naming on purpose.
     */
    public static final MachineDefinition STEAM_EXHAUST_HATCH = GSERegistration.REGISTRATE
            .machine("steam_exhaust_hatch", SteamExhaustHatchMachine::new)
            .rotationState(RotationState.ALL)
            .model(GSEMachines::steamExhaustHatchModel)
            .langValue("Steam Exhaust Hatch")
            .tooltipBuilder(GSEMachineTooltips.STEAM_EXHAUST_HATCH)
            .register();

    private static void steamExhaustHatchModel(DataGenContext<Block, ? extends Block> context,
                                               GTBlockstateProvider provider,
                                               MachineModelBuilder<BlockModelBuilder> builder) {
        // Bronze steam-machine hull with a single static front grille; all other
        // overlay faces and emissive layers keep the template's void defaults
        // (large-heat-storage-steam-furnace.md 美术方向: one static texture, no
        // idle/active/blocked variants, no emissive mask).
        BlockModelBuilder model = provider.models().nested()
                .parent(provider.models().getExistingFile(GTMachineModels.SIDED_SIDED_OVERLAY_MODEL));
        GTMachineModels.steamCasingTextures(model, false);
        model.texture("overlay_front",
                GregSteamExpansion.gtceuId("block/overlay/machine/overlay_steam_vent"));
        builder.forAllStatesModels(state -> model);
        builder.addReplaceableTextures("bottom", "top", "side");
    }

    // ------------------------------------------------------------------
    // 蒸汽供给仓 / Steam Supply Hatch (machines-and-hatches.md 已定案)
    // ------------------------------------------------------------------

    public static final MachineDefinition STEAM_SUPPLY_HATCH = GSERegistration.REGISTRATE
            .machine("steam_supply_hatch", SteamSupplyHatchPartMachine::new)
            .rotationState(RotationState.ALL)
            .abilities(PartAbility.STEAM)
            .modelProperty(GTMachineModelProperties.IS_STEEL_MACHINE,
                    com.gregtechceu.gtceu.config.ConfigHolder.INSTANCE.machines.steelSteamMultiblocks)
            .model(steamHatchModel(
                    GregSteamExpansion.gtceuId("block/overlay/machine/overlay_steam_miner")))
            .langValue("Steam Supply Hatch")
            .tooltipBuilder(GSEMachineTooltips.STEAM_SUPPLY_HATCH)
            .allowCoverOnFront(true)
            .register();

    // ------------------------------------------------------------------
    // 蒸汽流体输入/输出仓 / Steam Fluid Input & Output Hatches
    // (machines-and-hatches.md 已定案)
    // ------------------------------------------------------------------

    public static final MachineDefinition STEAM_FLUID_IMPORT_HATCH = GSERegistration.REGISTRATE
            .machine("steam_fluid_input_hatch", holder -> new SteamFluidHatchPartMachine(holder, IO.IN))
            .rotationState(RotationState.ALL)
            .abilities(GSEPartAbilities.STEAM_IMPORT_FLUIDS)
            .modelProperty(GTMachineModelProperties.IS_STEEL_MACHINE,
                    com.gregtechceu.gtceu.config.ConfigHolder.INSTANCE.machines.steelSteamMultiblocks)
            .model(steamHatchModel(
                    GregSteamExpansion.gtceuId("block/overlay/machine/overlay_fluid_hatch_input")))
            .langValue("Steam Fluid Input Hatch")
            .tooltipBuilder(GSEMachineTooltips.STEAM_FLUID_IMPORT_HATCH)
            .allowCoverOnFront(true)
            .register();

    public static final MachineDefinition STEAM_FLUID_EXPORT_HATCH = GSERegistration.REGISTRATE
            .machine("steam_fluid_output_hatch", holder -> new SteamFluidHatchPartMachine(holder, IO.OUT))
            .rotationState(RotationState.ALL)
            .abilities(GSEPartAbilities.STEAM_EXPORT_FLUIDS)
            .modelProperty(GTMachineModelProperties.IS_STEEL_MACHINE,
                    com.gregtechceu.gtceu.config.ConfigHolder.INSTANCE.machines.steelSteamMultiblocks)
            .model(steamHatchModel(
                    GregSteamExpansion.gtceuId("block/overlay/machine/overlay_fluid_hatch_output")))
            .langValue("Steam Fluid Output Hatch")
            .tooltipBuilder(GSEMachineTooltips.STEAM_FLUID_EXPORT_HATCH)
            .allowCoverOnFront(true)
            .register();

    // ------------------------------------------------------------------
    // 蒸汽进气室 / Steam Air Intake Hatch (machines-and-hatches.md 已定案)
    // ------------------------------------------------------------------
    public static final MachineDefinition STEAM_AIR_INTAKE_HATCH = GSERegistration.REGISTRATE
            .machine("steam_air_intake_hatch", SteamAirIntakeHatchPartMachine::new)
            .rotationState(RotationState.ALL)
            .abilities(GSEPartAbilities.STEAM_AIR_INTAKE)
            .modelProperty(GTMachineModelProperties.IS_STEEL_MACHINE,
                    com.gregtechceu.gtceu.config.ConfigHolder.INSTANCE.machines.steelSteamMultiblocks)
            .model(steamHatchModel(
                    GregSteamExpansion.gtceuId("block/overlay/machine/overlay_air_vent")))
            .langValue("Steam Air Intake Hatch")
            .tooltipBuilder(GSEMachineTooltips.STEAM_AIR_INTAKE_HATCH)
            // 进气正面拒绝封面: with allowCoverOnFront(false) and a six-way
            // front facing, CoverBehavior#canAttach already rejects every front
            // cover, so the louver grille can never be visually sealed while
            // the air check keeps looking straight through it.
            .allowCoverOnFront(false)
            .register();

    /**
     * Shared steam-hatch model: GTCEu bronze/steel steam hull chosen by the
     * {@code IS_STEEL_MACHINE} state property (kept in step with the
     * {@code machines.steelSteamMultiblocks} config) plus a mod-provided static
     * front overlay. Covers stay allowed where registration permits them.
     */
    private static MachineBuilder.ModelInitializer steamHatchModel(ResourceLocation overlayFront) {
        return (context, provider, builder) -> {
            builder.forAllStatesModels(state -> {
                boolean steel = state.getOptionalValue(GTMachineModelProperties.IS_STEEL_MACHINE).orElse(false);
                BlockModelBuilder model = provider.models().nested()
                        .parent(provider.models().getExistingFile(GTMachineModels.SIDED_SIDED_OVERLAY_MODEL));
                GTMachineModels.steamCasingTextures(model, steel);
                model.texture("overlay_front", overlayFront);
                return model;
            });
            // Match the standard steam-hatch hull: inside a formed multiblock
            // the non-front faces render as the structure casing, keeping only
            // the front overlay visible.
            builder.addReplaceableTextures("bottom", "top", "side");
        };
    }

    // ------------------------------------------------------------------
    // 蒸汽粉碎机 / Large Steam Crusher controllers (steam-crushers.md)
    // Fixed bronze steam hull (never the steelSteamMultiblocks steel look),
    // hardness 5.0 / blast 6.0 / metal sound, four horizontal facings only,
    // no covers on the reserved front face.
    // ------------------------------------------------------------------

    private static MachineBuilder.ModelInitializer steamMultiblockModel(ResourceLocation overlayDir) {
        // appearanceBlock supplies the CTM connection identity; formed parts
        // also need an explicit texture override to replace their tier hull.
        // "all" maps to the buses'/hatches' bottom, top and side textures,
        // preserving their front overlays and the controller's steam model.
        return GTMachineModels.createWorkableSteamHullMachineModel(false, overlayDir)
                .andThen(builder -> builder.addTextureOverride("all",
                        GregSteamExpansion.gtceuId("block/casings/solid/machine_casing_bronze_plated_bricks")));
    }

    public static final MultiblockMachineDefinition STEAM_CRUSHER = GSERegistration.REGISTRATE
            .multiblock("steam_crusher", SteamCrusherMachine::new)
            .rotationState(RotationState.NON_Y_AXIS)
            .recipeType(GSERecipeTypes.ORE_CRUSHING_RECIPES)
            .appearanceBlock(GTBlocks.CASING_BRONZE_BRICKS)
            .blockProp(properties -> properties.strength(5.0F, 6.0F).sound(SoundType.METAL))
            .modelProperty(GTMachineModelProperties.RECIPE_LOGIC_STATUS, RecipeLogic.Status.IDLE)
            .model(steamMultiblockModel(
                    GregSteamExpansion.gtceuId("block/machines/rock_crusher")))
            .pattern(GSECrusherPatterns::createSmall)
            .shapeInfos(definition -> List.of(GSECrusherPatterns.smallShapeInfo(definition)))
            .langValue("Steam Crusher")
            .tooltipBuilder(GSEMachineTooltips.STEAM_CRUSHER)
            .allowCoverOnFront(false)
            .register();

    public static final MultiblockMachineDefinition LARGE_STEAM_CRUSHER = GSERegistration.REGISTRATE
            .multiblock("large_steam_crusher", LargeSteamCrusherMachine::new)
            .rotationState(RotationState.NON_Y_AXIS)
            .recipeType(GSERecipeTypes.ORE_CRUSHING_RECIPES)
            .appearanceBlock(GTBlocks.CASING_BRONZE_BRICKS)
            .blockProp(properties -> properties.strength(5.0F, 6.0F).sound(SoundType.METAL))
            .modelProperty(GTMachineModelProperties.RECIPE_LOGIC_STATUS, RecipeLogic.Status.IDLE)
            .model(steamMultiblockModel(
                    GregSteamExpansion.gtceuId("block/machines/rock_crusher")))
            .pattern(GSECrusherPatterns::createLarge)
            .shapeInfos(definition -> List.of(GSECrusherPatterns.largeShapeInfo(definition)))
            .langValue("Large Steam Crusher")
            .tooltipBuilder(GSEMachineTooltips.LARGE_STEAM_CRUSHER)
            .allowCoverOnFront(false)
            .register();

    // ------------------------------------------------------------------
    // 电力粉碎机 / Electric Ore Crushers (ore-crushing.md 电力消费机器):
    // ore_crushing 类型对应的电力分级单方块机器 (MV–UV, 标准不完全超频),
    // 与蒸汽粉碎机共享同一配方池 (4× ×难度产出已烘焙在迁移配方中);
    // 叠加层纹理在模组自身命名空间, 故不经 registerSimpleMachines 注册
    // (其模型工厂硬编码 gtceu 命名空间), 而用同形态的 registerTieredMachines。
    // ------------------------------------------------------------------

    public static final MachineDefinition[] ELECTRIC_ORE_CRUSHERS = GTMachineUtils.registerTieredMachines(
            GSERegistration.REGISTRATE,
            "electric_ore_crusher",
            (holder, tier) -> new SimpleTieredMachine(holder, tier, GTMachineUtils.defaultTankSizeFunction),
            (tier, builder) -> builder
                    .langValue("%s Electric Ore Crusher".formatted(GTValues.VN[tier]))
                    .recipeModifier(GTRecipeModifiers.OC_NON_PERFECT)
                    .editableUI(SimpleTieredMachine.EDITABLE_UI_CREATOR.apply(
                            GregSteamExpansion.id("electric_ore_crusher"), GSERecipeTypes.ORE_CRUSHING_RECIPES))
                    .rotationState(RotationState.NON_Y_AXIS)
                    .recipeType(GSERecipeTypes.ORE_CRUSHING_RECIPES)
                    .workableTieredHullModel(
                            GregSteamExpansion.gtceuId("block/machines/rock_crusher"))
                    .tooltips(Component.translatable(
                            "gregsteamexpansion.machine.electric_ore_crusher.tooltip"))
                    .register(),
            // 档位固定 MV–UV: 配方基准功率 100 EU/t 已超出 LV (32 EU/t)
            // 上限, 电力粉碎机自 MV 起可用 (高配档后续按需扩展).
            GTValues.tiersBetween(GTValues.MV, GTValues.UV));

    // ------------------------------------------------------------------
    // 轻量蒸汽多方块家族 / Light Steam Processor family
    // (steam-compressor.md / steam-extractor.md / steam-forge.md): 3×3×3-ish
    // fixed structures, parallel 8, LV voltage gate, no exhaust hatch. The
    // controller hull and CTM identity follow the crusher conventions
    // (appearanceBlock + addTextureOverride keep the formed controller in step
    // with the surrounding bronze steam machine casings for connected textures).
    // ------------------------------------------------------------------

    /** 蒸汽压缩机 / Steam Compressor (steam-compressor.md 议题 1). */
    public static final MultiblockMachineDefinition STEAM_COMPRESSOR = GSERegistration.REGISTRATE
            .multiblock("steam_compressor", SteamCompressorMachine::new)
            .rotationState(RotationState.NON_Y_AXIS)
            .recipeType(GTRecipeTypes.COMPRESSOR_RECIPES)
            .appearanceBlock(GTBlocks.CASING_BRONZE_BRICKS)
            .blockProp(properties -> properties.strength(5.0F, 6.0F).sound(SoundType.METAL))
            .modelProperty(GTMachineModelProperties.RECIPE_LOGIC_STATUS, RecipeLogic.Status.IDLE)
            .model(steamMultiblockModel(
                    GregSteamExpansion.gtceuId("block/machines/compressor")))
            .pattern(GSEProcessorPatterns::createCompressor)
            .shapeInfos(definition -> List.of(GSEProcessorPatterns.compressorShapeInfo(definition)))
            .langValue("Steam Compressor")
            .tooltipBuilder(GSEMachineTooltips.STEAM_COMPRESSOR)
            .allowCoverOnFront(false)
            .register();

    /** 蒸汽提取机 / Steam Extractor (steam-extractor.md 议题 1). */
    public static final MultiblockMachineDefinition STEAM_EXTRACTOR = GSERegistration.REGISTRATE
            .multiblock("steam_extractor", SteamExtractorMachine::new)
            .rotationState(RotationState.NON_Y_AXIS)
            .recipeType(GTRecipeTypes.EXTRACTOR_RECIPES)
            .appearanceBlock(GTBlocks.CASING_BRONZE_BRICKS)
            .blockProp(properties -> properties.strength(5.0F, 6.0F).sound(SoundType.METAL))
            .modelProperty(GTMachineModelProperties.RECIPE_LOGIC_STATUS, RecipeLogic.Status.IDLE)
            .model(steamMultiblockModel(
                    GregSteamExpansion.gtceuId("block/machines/extractor")))
            .pattern(GSEProcessorPatterns::createExtractor)
            .shapeInfos(definition -> List.of(GSEProcessorPatterns.extractorShapeInfo(definition)))
            .langValue("Steam Extractor")
            .tooltipBuilder(GSEMachineTooltips.STEAM_EXTRACTOR)
            .allowCoverOnFront(false)
            .register();

    /** 蒸汽锻压机 / Steam Forge (steam-forge.md 议题 1). */
    public static final MultiblockMachineDefinition STEAM_FORGE = GSERegistration.REGISTRATE
            .multiblock("steam_forge", SteamForgeMachine::new)
            .rotationState(RotationState.NON_Y_AXIS)
            .recipeType(GTRecipeTypes.FORGE_HAMMER_RECIPES)
            .appearanceBlock(GTBlocks.CASING_BRONZE_BRICKS)
            .blockProp(properties -> properties.strength(5.0F, 6.0F).sound(SoundType.METAL))
            .modelProperty(GTMachineModelProperties.RECIPE_LOGIC_STATUS, RecipeLogic.Status.IDLE)
            .model(steamMultiblockModel(
                    GregSteamExpansion.gtceuId("block/machines/forge_hammer")))
            .pattern(GSEProcessorPatterns::createForge)
            .shapeInfos(definition -> List.of(GSEProcessorPatterns.forgeShapeInfo(definition)))
            .langValue("Steam Forge")
            .tooltipBuilder(GSEMachineTooltips.STEAM_FORGE)
            .allowCoverOnFront(false)
            .register();

    /** 大型蒸汽洗矿厂 / Large Steam Ore Washer (large-steam-ore-washer.md 议题 1). */
    public static final MultiblockMachineDefinition LARGE_STEAM_ORE_WASHER = GSERegistration.REGISTRATE
            .multiblock("large_steam_ore_washer", LargeSteamOreWasherMachine::new)
            .rotationState(RotationState.NON_Y_AXIS)
            .recipeType(GTRecipeTypes.ORE_WASHER_RECIPES)
            .appearanceBlock(GCYMBlocks.CASING_INDUSTRIAL_STEAM)
            .blockProp(properties -> properties.strength(5.0F, 6.0F).sound(SoundType.METAL))
            .modelProperty(GTMachineModelProperties.RECIPE_LOGIC_STATUS, RecipeLogic.Status.IDLE)
            .model(steamMultiblockModel(
                    GregSteamExpansion.gtceuId("block/machines/ore_washer")))
            .pattern(GSEProcessorPatterns::createOreWasher)
            .shapeInfos(definition -> List.of(GSEProcessorPatterns.oreWasherShapeInfo(definition)))
            .langValue("Large Steam Ore Washer")
            .tooltipBuilder(GSEMachineTooltips.LARGE_STEAM_ORE_WASHER)
            .allowCoverOnFront(false)
            .register();

    /** 大型蒸汽热力离心机 / Large Steam Thermal Centrifuge (C0b 议题 1). */
    public static final MultiblockMachineDefinition LARGE_STEAM_THERMAL_CENTRIFUGE = GSERegistration.REGISTRATE
            .multiblock("large_steam_thermal_centrifuge", LargeSteamThermalCentrifugeMachine::new)
            .rotationState(RotationState.NON_Y_AXIS)
            .recipeType(GTRecipeTypes.THERMAL_CENTRIFUGE_RECIPES)
            .appearanceBlock(GCYMBlocks.CASING_INDUSTRIAL_STEAM)
            .blockProp(properties -> properties.strength(5.0F, 6.0F).sound(SoundType.METAL))
            .modelProperty(GTMachineModelProperties.RECIPE_LOGIC_STATUS, RecipeLogic.Status.IDLE)
            .model(steamMultiblockModel(
                    GregSteamExpansion.gtceuId("block/machines/thermal_centrifuge")))
            .pattern(GSEProcessorPatterns::createThermalCentrifuge)
            .shapeInfos(definition -> List.of(GSEProcessorPatterns.thermalCentrifugeShapeInfo(definition)))
            .langValue("Large Steam Thermal Centrifuge")
            .tooltipBuilder(GSEMachineTooltips.LARGE_STEAM_THERMAL_CENTRIFUGE)
            .allowCoverOnFront(false)
            .register();

    /** 大型蒸汽研磨厂 / Large Steam Macerator (A4 议题 1). */
    public static final MultiblockMachineDefinition LARGE_STEAM_MACERATOR = GSERegistration.REGISTRATE
            .multiblock("large_steam_macerator", LargeSteamMaceratorMachine::new)
            .rotationState(RotationState.NON_Y_AXIS)
            .recipeType(GTRecipeTypes.MACERATOR_RECIPES)
            .appearanceBlock(GTBlocks.CASING_BRONZE_BRICKS)
            .blockProp(properties -> properties.strength(5.0F, 6.0F).sound(SoundType.METAL))
            .modelProperty(GTMachineModelProperties.RECIPE_LOGIC_STATUS, RecipeLogic.Status.IDLE)
            .model(steamMultiblockModel(
                    GregSteamExpansion.gtceuId("block/machines/macerator")))
            .pattern(GSEProcessorPatterns::createMacerator)
            .shapeInfos(definition -> List.of(GSEProcessorPatterns.maceratorShapeInfo(definition)))
            .langValue("Large Steam Macerator")
            .tooltipBuilder(GSEMachineTooltips.LARGE_STEAM_MACERATOR)
            .allowCoverOnFront(false)
            .register();

    /** 大型蒸汽搅拌机 / Large Steam Mixer (B3 议题 1). */
    public static final MultiblockMachineDefinition LARGE_STEAM_MIXER = GSERegistration.REGISTRATE
            .multiblock("large_steam_mixer", LargeSteamMixerMachine::new)
            .rotationState(RotationState.NON_Y_AXIS)
            .recipeType(GTRecipeTypes.MIXER_RECIPES)
            .appearanceBlock(GCYMBlocks.CASING_INDUSTRIAL_STEAM)
            .blockProp(properties -> properties.strength(5.0F, 6.0F).sound(SoundType.METAL))
            .modelProperty(GTMachineModelProperties.RECIPE_LOGIC_STATUS, RecipeLogic.Status.IDLE)
            .model(steamMultiblockModel(
                    GregSteamExpansion.gtceuId("block/machines/mixer")))
            .pattern(GSEProcessorPatterns::createMixer)
            .shapeInfos(definition -> List.of(GSEProcessorPatterns.mixerShapeInfo(definition)))
            .langValue("Large Steam Mixer")
            .tooltipBuilder(GSEMachineTooltips.LARGE_STEAM_MIXER)
            .allowCoverOnFront(false)
            .register();

    /** 大型蒸汽高炉 / Large Steam Blast Furnace (large-steam-blast-furnace.md 议题 1). */
    public static final MultiblockMachineDefinition LARGE_STEAM_BLAST_FURNACE = GSERegistration.REGISTRATE
            .multiblock("large_steam_blast_furnace", LargeSteamBlastFurnaceMachine::new)
            .rotationState(RotationState.NON_Y_AXIS)
            .recipeType(GTRecipeTypes.PRIMITIVE_BLAST_FURNACE_RECIPES)
            .appearanceBlock(GCYMBlocks.CASING_INDUSTRIAL_STEAM)
            .blockProp(properties -> properties.strength(5.0F, 6.0F).sound(SoundType.METAL))
            .modelProperty(GTMachineModelProperties.RECIPE_LOGIC_STATUS, RecipeLogic.Status.IDLE)
            .model(steamMultiblockModel(
                    GregSteamExpansion.gtceuId("block/multiblock/primitive_blast_furnace")))
            .pattern(GSEProcessorPatterns::createBlastFurnace)
            .shapeInfos(definition -> List.of(GSEProcessorPatterns.blastFurnaceShapeInfo(definition)))
            .langValue("Large Steam Blast Furnace")
            .tooltipBuilder(GSEMachineTooltips.LARGE_STEAM_BLAST_FURNACE)
            .allowCoverOnFront(false)
            .register();

    public static final MultiblockMachineDefinition STEAM_CHEMICAL_BATH = GSERegistration.REGISTRATE
            .multiblock("steam_chemical_bath", SteamChemicalBathMachine::new)
            .rotationState(RotationState.NON_Y_AXIS)
            .recipeType(GTRecipeTypes.CHEMICAL_BATH_RECIPES)
            .appearanceBlock(GCYMBlocks.CASING_INDUSTRIAL_STEAM)
            .blockProp(properties -> properties.strength(5.0F, 6.0F).sound(SoundType.METAL))
            .modelProperty(GTMachineModelProperties.RECIPE_LOGIC_STATUS, RecipeLogic.Status.IDLE)
            .model(steamMultiblockModel(
                    GregSteamExpansion.gtceuId("block/machines/chemical_bath")))
            .pattern(GSEProcessorPatterns::createChemicalBath)
            .shapeInfos(definition -> List.of(GSEProcessorPatterns.chemicalBathShapeInfo(definition)))
            .langValue("Steam Chemical Bath")
            .tooltipBuilder(GSEMachineTooltips.STEAM_CHEMICAL_BATH)
            .allowCoverOnFront(false)
            .register();

    public static final MultiblockMachineDefinition STEAM_CENTRIFUGE = GSERegistration.REGISTRATE
            .multiblock("steam_centrifuge", SteamCentrifugeMachine::new)
            .rotationState(RotationState.NON_Y_AXIS)
            .recipeType(GTRecipeTypes.CENTRIFUGE_RECIPES)
            .appearanceBlock(GTBlocks.CASING_BRONZE_BRICKS)
            .blockProp(properties -> properties.strength(5.0F, 6.0F).sound(SoundType.METAL))
            .modelProperty(GTMachineModelProperties.RECIPE_LOGIC_STATUS, RecipeLogic.Status.IDLE)
            .model(steamMultiblockModel(
                    GregSteamExpansion.gtceuId("block/machines/centrifuge")))
            .pattern(GSEProcessorPatterns::createCentrifuge)
            .shapeInfos(definition -> List.of(GSEProcessorPatterns.centrifugeShapeInfo(definition)))
            .langValue("Steam Centrifuge")
            .tooltipBuilder(GSEMachineTooltips.STEAM_CENTRIFUGE)
            .allowCoverOnFront(false)
            .register();

    public static final MultiblockMachineDefinition LARGE_STEAM_CENTRIFUGE = GSERegistration.REGISTRATE
            .multiblock("large_steam_centrifuge", LargeSteamCentrifugeMachine::new)
            .rotationState(RotationState.NON_Y_AXIS)
            .recipeType(GTRecipeTypes.CENTRIFUGE_RECIPES)
            .appearanceBlock(GTBlocks.CASING_BRONZE_BRICKS)
            .blockProp(properties -> properties.strength(5.0F, 6.0F).sound(SoundType.METAL))
            .modelProperty(GTMachineModelProperties.RECIPE_LOGIC_STATUS, RecipeLogic.Status.IDLE)
            .model(steamMultiblockModel(
                    GregSteamExpansion.gtceuId("block/machines/centrifuge")))
            .pattern(GSEProcessorPatterns::createLargeCentrifuge)
            .shapeInfos(definition -> List.of(GSEProcessorPatterns.largeCentrifugeShapeInfo(definition)))
            .langValue("Large Steam Centrifuge")
            .tooltipBuilder(GSEMachineTooltips.LARGE_STEAM_CENTRIFUGE)
            .allowCoverOnFront(false)
            .register();

    /** 大型蒸汽组装机 / Large Steam Assembler (large-steam-assembler.md 议题 1). */
    public static final MultiblockMachineDefinition LARGE_STEAM_ASSEMBLER = GSERegistration.REGISTRATE
            .multiblock("large_steam_assembler", LargeSteamAssemblerMachine::new)
            .rotationState(RotationState.NON_Y_AXIS)
            .recipeType(GTRecipeTypes.ASSEMBLER_RECIPES)
            .appearanceBlock(GCYMBlocks.CASING_INDUSTRIAL_STEAM)
            .blockProp(properties -> properties.strength(5.0F, 6.0F).sound(SoundType.METAL))
            .modelProperty(GTMachineModelProperties.RECIPE_LOGIC_STATUS, RecipeLogic.Status.IDLE)
            .model(steamMultiblockModel(
                    GregSteamExpansion.gtceuId("block/machines/assembler")))
            .pattern(GSEProcessorPatterns::createLargeSteamAssembler)
            .shapeInfos(definition -> List.of(GSEProcessorPatterns.assemblerShapeInfo(definition)))
            .langValue("Large Steam Assembler")
            .tooltipBuilder(GSEMachineTooltips.LARGE_STEAM_ASSEMBLER)
            .allowCoverOnFront(false)
            .register();

    /** 大型蒸汽电路组装机 / Large Steam Circuit Assembler (large-steam-circuit-assembler.md 议题 1). */
    public static final MultiblockMachineDefinition LARGE_STEAM_CIRCUIT_ASSEMBLER = GSERegistration.REGISTRATE
            .multiblock("large_steam_circuit_assembler", LargeSteamCircuitAssemblerMachine::new)
            .rotationState(RotationState.NON_Y_AXIS)
            .recipeType(GTRecipeTypes.CIRCUIT_ASSEMBLER_RECIPES)
            .appearanceBlock(GTBlocks.CASING_BRONZE_BRICKS)
            .blockProp(properties -> properties.strength(5.0F, 6.0F).sound(SoundType.METAL))
            .modelProperty(GTMachineModelProperties.RECIPE_LOGIC_STATUS, RecipeLogic.Status.IDLE)
            .model(steamMultiblockModel(
                    GregSteamExpansion.gtceuId("block/machines/circuit_assembler")))
            .pattern(GSEProcessorPatterns::createLargeSteamCircuitAssembler)
            .shapeInfos(definition -> List.of(GSEProcessorPatterns.circuitAssemblerShapeInfo(definition)))
            .langValue("Large Steam Circuit Assembler")
            .tooltipBuilder(GSEMachineTooltips.LARGE_STEAM_CIRCUIT_ASSEMBLER)
            .allowCoverOnFront(false)
            .register();

    /** 大型蒸汽采矿厂 / Large Steam Ore Plant (large-steam-ore-plant.md 议题 1). */
    public static final MultiblockMachineDefinition LARGE_STEAM_ORE_PLANT = GSERegistration.REGISTRATE
            .multiblock("large_steam_ore_plant", LargeSteamOrePlantMachine::new)
            .rotationState(RotationState.NON_Y_AXIS)
            // 无配方类型: 纯虚空生成机器 (议题 1).
            .appearanceBlock(GCYMBlocks.CASING_INDUSTRIAL_STEAM)
            .blockProp(properties -> properties.strength(5.0F, 6.0F).sound(SoundType.METAL))
            .modelProperty(GTMachineModelProperties.RECIPE_LOGIC_STATUS, RecipeLogic.Status.IDLE)
            .model(steamMultiblockModel(
                    GregSteamExpansion.gtceuId("block/machines/miner")))
            .pattern(GSEVoidPatterns::createOrePlant)
            .shapeInfos(definition -> List.of(GSEVoidPatterns.orePlantShapeInfo(definition)))
            .langValue("Large Steam Ore Plant")
            .tooltipBuilder(GSEMachineTooltips.LARGE_STEAM_ORE_PLANT)
            .allowCoverOnFront(false)
            .register();

    /** 大型蒸汽流体钻井 / Large Steam Fluid Drill (large-steam-fluid-drill.md 议题 1). */
    public static final MultiblockMachineDefinition LARGE_STEAM_FLUID_DRILL = GSERegistration.REGISTRATE
            .multiblock("large_steam_fluid_drill", LargeSteamFluidDrillMachine::new)
            .rotationState(RotationState.NON_Y_AXIS)
            // 无配方类型: 纯虚空生成机器 (议题 1).
            .appearanceBlock(GTBlocks.CASING_BRONZE_BRICKS)
            .blockProp(properties -> properties.strength(5.0F, 6.0F).sound(SoundType.METAL))
            .modelProperty(GTMachineModelProperties.RECIPE_LOGIC_STATUS, RecipeLogic.Status.IDLE)
            .model(steamMultiblockModel(
                    GregSteamExpansion.gtceuId("block/multiblock/fluid_drilling_rig")))
            .pattern(GSEVoidPatterns::createFluidDrill)
            .shapeInfos(definition -> List.of(GSEVoidPatterns.fluidDrillShapeInfo(definition)))
            .langValue("Large Steam Fluid Drill")
            .tooltipBuilder(GSEMachineTooltips.LARGE_STEAM_FLUID_DRILL)
            .allowCoverOnFront(false)
            .register();

    // ------------------------------------------------------------------
    // 锅炉房 / Boiler Room (boiler-room.md): 四档终端蒸汽锅炉 (青铜/钢/钛/
    // 钨钢), 共用 BoilerRoomMachine 与 7×11×7 去角长方体图案, 产能严格上位于
    // 同档 GTCEu 大型锅炉 (协同 +50%; Easy ×2)。部件位置 (P2#9): 控制器正面
    // 中心、消音器背面中心、蒸汽进气室顶面中心、火室两片 3×9、管道中轴 9 格。
    // ------------------------------------------------------------------

    /** 锅炉房（青铜）/ Boiler Room (Bronze) — 800 K 级. */
    public static final MultiblockMachineDefinition BOILER_ROOM_BRONZE = registerBoilerRoom("bronze",
            "Boiler Room (Bronze)", BoilerRoomMachine.BRONZE_TIER,
            GTBlocks.CASING_BRONZE_BRICKS, GTBlocks.CASING_BRONZE_PIPE, GTBlocks.FIREBOX_BRONZE,
            com.gregtechceu.gtceu.GTCEu.id("block/casings/solid/machine_casing_bronze_plated_bricks"),
            com.gregtechceu.gtceu.common.block.BoilerFireboxType.BRONZE_FIREBOX);

    /** 锅炉房（钢）/ Boiler Room (Steel) — 1800 K 级. */
    public static final MultiblockMachineDefinition BOILER_ROOM_STEEL = registerBoilerRoom("steel",
            "Boiler Room (Steel)", BoilerRoomMachine.STEEL_TIER,
            GTBlocks.CASING_STEEL_SOLID, GTBlocks.CASING_STEEL_PIPE, GTBlocks.FIREBOX_STEEL,
            com.gregtechceu.gtceu.GTCEu.id("block/casings/solid/machine_casing_solid_steel"),
            com.gregtechceu.gtceu.common.block.BoilerFireboxType.STEEL_FIREBOX);

    /** 锅炉房（钛）/ Boiler Room (Titanium) — 3200 K 级. */
    public static final MultiblockMachineDefinition BOILER_ROOM_TITANIUM = registerBoilerRoom("titanium",
            "Boiler Room (Titanium)", BoilerRoomMachine.TITANIUM_TIER,
            GTBlocks.CASING_TITANIUM_STABLE, GTBlocks.CASING_TITANIUM_PIPE, GTBlocks.FIREBOX_TITANIUM,
            com.gregtechceu.gtceu.GTCEu.id("block/casings/solid/machine_casing_stable_titanium"),
            com.gregtechceu.gtceu.common.block.BoilerFireboxType.TITANIUM_FIREBOX);

    /** 锅炉房（钨钢）/ Boiler Room (Tungstensteel) — 6400 K 级. */
    public static final MultiblockMachineDefinition BOILER_ROOM_TUNGSTENSTEEL = registerBoilerRoom(
            "tungstensteel",
            "Boiler Room (Tungstensteel)", BoilerRoomMachine.TUNGSTENSTEEL_TIER,
            GTBlocks.CASING_TUNGSTENSTEEL_ROBUST, GTBlocks.CASING_TUNGSTENSTEEL_PIPE,
            GTBlocks.FIREBOX_TUNGSTENSTEEL,
            com.gregtechceu.gtceu.GTCEu.id("block/casings/solid/machine_casing_robust_tungstensteel"),
            com.gregtechceu.gtceu.common.block.BoilerFireboxType.TUNGSTENSTEEL_FIREBOX);

    private static MultiblockMachineDefinition registerBoilerRoom(String name, String englishName, int tierIndex,
                                                                  java.util.function.Supplier<? extends Block> casing,
                                                                  java.util.function.Supplier<? extends Block> pipe,
                                                                  java.util.function.Supplier<? extends Block> firebox,
                                                                  ResourceLocation hullTexture,
                                                                  com.gregtechceu.gtceu.common.block.BoilerFireboxType fireboxType) {
        var tierBlocks = new GSEBoilerPatterns.TierBlocks(casing, pipe, firebox);
        return GSERegistration.REGISTRATE
                .multiblock("boiler_room_" + name, holder -> new BoilerRoomMachine(holder, tierIndex))
                .langValue(englishName)
                .allowExtendedFacing(false)
                .rotationState(RotationState.NON_Y_AXIS)
                .recipeType(GSERecipeTypes.BOILER_ROOM_RECIPES)
                .recipeModifier(com.gregtechceu.gtceu.common.machine.multiblock.steam.LargeBoilerMachine::recipeModifier,
                        true)
                // 缺粉/缺空气暂停 = 冻结燃料批次进度, 不回退 (P1#8, 混合燃料
                // 锅炉口径).
                .regressWhenWaiting(false)
                .appearanceBlock(casing)
                .partAppearance((controller, part, side) ->
                        part.self().getPos().getY() == controller.self().getPos().getY() - 3
                                ? firebox.get().defaultBlockState()
                                : casing.get().defaultBlockState())
                .pattern(definition -> GSEBoilerPatterns.createPattern(definition, tierBlocks))
                .shapeInfos(definition -> List.of(GSEBoilerPatterns.shapeInfo(definition, tierBlocks)))
                .modelProperty(GTMachineModelProperties.RECIPE_LOGIC_STATUS, RecipeLogic.Status.IDLE)
                .model(GTMachineModels.createWorkableCasingMachineModel(hullTexture,
                        com.gregtechceu.gtceu.GTCEu.id("block/multiblock/generator/large_" + name + "_boiler"))
                        .andThen(b -> b.addDynamicRenderer(
                                () -> com.gregtechceu.gtceu.client.renderer.machine.DynamicRenderHelper
                                        .makeBoilerPartRender(fireboxType, casing))))
                .tooltips(
                        Component.translatable("gtceu.multiblock.large_boiler.max_temperature",
                                BoilerRoomMachine.MAX_TEMPERATURES[tierIndex] + 274,
                                BoilerRoomMachine.MAX_TEMPERATURES[tierIndex]),
                        Component.translatable("gregsteamexpansion.machine.boiler_room.tooltip.co_firing",
                                String.format("%,d", BoilerRoomMachine.AIR_PER_TICK[tierIndex])),
                        Component.translatable("gregsteamexpansion.machine.boiler_room.tooltip.air_intake"),
                        Component.translatable("gtceu.multiblock.large_boiler.explosion_tooltip")
                                .withStyle(ChatFormatting.DARK_RED))
                .register();
    }

    static {
        // 配方迁移启用保护 (steam-crushers.md): the small crusher registers as
        // the explicit ore-crushing consumer; the large crusher alone never
        // satisfies the migration check.
        OreCrushingMigration.registerConsumer(STEAM_CRUSHER, GregSteamExpansion.id("shaped/steam_crusher"));
    }

    /**
     * 大型蓄热蒸汽熔炉 / Large Heat-Storage Steam Furnace controller
     * (large-heat-storage-steam-furnace.md 注册与命名): pure-steam variable-size
     * smelting furnace for the low-voltage era. The pattern supplier returns
     * the canonical 15×15 pattern for terminal auto-build and preview pages;
     * the controller itself checks all three widths.
     */
    public static final MultiblockMachineDefinition LARGE_HEAT_STORAGE_STEAM_FURNACE = GSERegistration.REGISTRATE
            .multiblock("large_heat_storage_steam_furnace", LargeHeatStorageSteamFurnaceMachine::new)
            .rotationState(RotationState.ALL)
            .recipeType(GTRecipeTypes.FURNACE_RECIPES)
            .appearanceBlock(GTBlocks.CASING_BRONZE_BRICKS)
            .pattern(definition -> GSEFurnacePatterns.create(definition, 15))
            // XEI validates every ShapeInfo against this definition's single
            // canonical pattern. Smaller runtime widths are checked by the
            // controller itself and cannot be represented as alternate pages
            // here without being rejected as malformed 15x15 previews.
            .shapeInfos(definition -> List.of(GSEFurnacePatterns.createShapeInfo(definition, 15)))
            .model(steamMultiblockModel(
                    GregSteamExpansion.gtceuId("block/machines/electric_furnace")))
            .langValue("Large Heat-Storage Steam Furnace")
            .tooltipBuilder(GSEMachineTooltips.LARGE_HEAT_STORAGE_STEAM_FURNACE)
            .register();

    private GSEMachines() {}

    public static void init() {}

    // ------------------------------------------------------------------
    // 大型焦炉 (coke-ovens.md 已确认注册身份): 独立注册, 不替换 gtceu:coke_oven。
    // 物理属性对齐上游焦炉系: GTCEu 机器方块默认继承发射器 (Blocks.DISPENSER)
    // 属性, 上游 coke_oven/coke_oven_hatch 均未调用 blockProp, 故此处同样不调用
    // (硬度/抗爆性/挖掘工具/方块声音/正常掉落/最大堆叠 64 完全一致)。
    // ------------------------------------------------------------------

    /** 大型焦炉控制器: 最大并行 6、固定 0.5× 耗时、7×7×5 包围范围。 */
    public static final MultiblockMachineDefinition LARGE_COKE_OVEN = GSERegistration.REGISTRATE
            .multiblock("large_coke_oven", LargeCokeOvenMachine::new)
            .rotationState(RotationState.NON_Y_AXIS)
            .recipeType(GTRecipeTypes.COKE_OVEN_RECIPES)
            .appearanceBlock(GTBlocks.CASING_COKE_BRICKS)
            .modelProperty(GTMachineModelProperties.RECIPE_LOGIC_STATUS, RecipeLogic.Status.IDLE)
            // 工作外观: 上游焦炉砖 + 可工作叠加层, 外加三炉门同步渲染与状态符号
            // (coke-ovens.md 已确认表现方案; DynamicRender 由控制器统一驱动,
            // 不给焦炉砖增加方块实体或状态)。
            .hasBER(true)
            .model(largeCokeOvenModel())
            .pattern(LargeCokeOvenStructures::createPattern)
            .shapeInfos(LargeCokeOvenStructures::shapeInfos)
            .langValue("Large Coke Oven")
            .tooltipBuilder((stack, lines) -> appendClientTooltip(
                    "gregsteamexpansion.machine.large_coke_oven.tooltip", lines))
            .allowCoverOnFront(false)
            .register();

    /**
     * 大型焦炉控制器模型: 复刻上游 workableCasingModel 组合 (焦炉砖 + coke_oven
     * 可工作叠加层), 并挂载三炉门同步渲染器。
     */
    private static MachineBuilder.ModelInitializer largeCokeOvenModel() {
        return (ctx, prov, builder) -> {
            var overlays = WorkableOverlays.get(
                    com.gregtechceu.gtceu.GTCEu.id("block/multiblock/coke_oven"),
                    prov.getExistingFileHelper());
            builder.forAllStates(state -> {
                RecipeLogic.Status status = state.getValue(GTMachineModelProperties.RECIPE_LOGIC_STATUS);
                var model = prov.models().nested()
                        .parent(prov.models().getExistingFile(
                                com.gregtechceu.gtceu.common.data.models.GTMachineModels.CUBE_ALL_SIDED_OVERLAY_MODEL))
                        .texture("all", com.gregtechceu.gtceu.GTCEu.id("block/casings/solid/machine_coke_bricks"));
                return com.gregtechceu.gtceu.common.data.models.GTMachineModels.addWorkableOverlays(
                        overlays, status, model);
            });
            builder.addTextureOverride("all",
                    com.gregtechceu.gtceu.GTCEu.id("block/casings/solid/machine_coke_bricks"));
            builder.addDynamicRenderer(
                    com.hoshino.gregsteamexpansion.cokeoven.LargeCokeOvenRenderer::new);
        };
    }

    /** 大型焦炉仓: 大型焦炉唯一合法的自动化接口, 3–5 个且三种模式各至少一个。 */
    public static final MachineDefinition LARGE_COKE_OVEN_HATCH = GSERegistration.REGISTRATE
            .machine("large_coke_oven_hatch", LargeCokeOvenHatchPartMachine::new)
            .rotationState(RotationState.ALL)
            .modelProperty(GTMachineModelProperties.IS_FORMED, false)
            // 沿用上游普通焦炉仓底模，客户端模型包装器叠加当前模式标志。
            .simpleModel(com.gregtechceu.gtceu.GTCEu.id("block/machine/part/coke_oven_hatch"))
            .langValue("Large Coke Oven Hatch")
            .tooltipBuilder((stack, lines) -> appendClientTooltip(
                    "gregsteamexpansion.machine.large_coke_oven_hatch.tooltip", lines))
            // 正面允许 GTCEu 合法覆板 (实际能力 = 当前模式 ∩ 覆板过滤)。
            .allowCoverOnFront(true)
            .register();

    /**
     * 两级物品提示的客户端委托: tooltipBuilder 由物品 hover 在客户端调用;
     * dist 守卫防止专用服务器解析 client 类 (方法体惰性解析, 仅客户端调用)。
     */
    private static void appendClientTooltip(String prefix, java.util.List<Component> lines) {
        if (net.minecraftforge.fml.loading.FMLEnvironment.dist.isClient()) {
            com.hoshino.gregsteamexpansion.client.cokeoven.CokeOvenTooltipBuilder.append(prefix, lines);
        }
    }

    private static MachineBuilder.ModelInitializer mixedFuelBoilerModel(boolean highPressure,
                                                                         ResourceLocation overlayDirectory) {
        return (context, provider, builder) -> {
            WorkableOverlays overlays = WorkableOverlays.get(overlayDirectory, provider.getExistingFileHelper());
            var parent = GTMachineModels.steamHullModel(provider.models(), highPressure);

            for (RecipeLogic.Status status : RecipeLogic.Status.values()) {
                // A waiting mixed-fuel boiler is not burning: missing powder, water,
                // fuel, or output room must therefore use the idle overlay.
                RecipeLogic.Status textureStatus = status == RecipeLogic.Status.WAITING ?
                        RecipeLogic.Status.IDLE : status;
                var model = provider.models().nested().parent(parent);
                GTMachineModels.addWorkableOverlays(overlays, textureStatus, model);
                builder.part(model).condition(GTMachineModelProperties.RECIPE_LOGIC_STATUS, status);
            }
        };
    }
}
