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
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.gregtechceu.gtceu.common.data.machines.GTMachineUtils;
import com.gregtechceu.gtceu.common.data.models.GTMachineModels;
import com.gregtechceu.gtceu.data.model.builder.MachineModelBuilder;
import com.gregtechceu.gtceu.utils.GTUtil;
import com.hoshino.gregsteamexpansion.GregSteamExpansion;
import com.hoshino.gregsteamexpansion.machine.multiblock.LargeHeatStorageSteamFurnaceMachine;
import com.hoshino.gregsteamexpansion.machine.multiblock.part.SteamAirIntakeHatchPartMachine;
import com.hoshino.gregsteamexpansion.machine.multiblock.part.SteamExhaustHatchMachine;
import com.hoshino.gregsteamexpansion.machine.multiblock.part.SteamFluidHatchPartMachine;
import com.hoshino.gregsteamexpansion.machine.multiblock.part.SteamSupplyHatchPartMachine;
import com.hoshino.gregsteamexpansion.machine.steam.MixedFuelBoilerMachine;

import com.gregtechceu.gtceu.api.capability.recipe.IO;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

import com.gregtechceu.gtceu.api.pattern.MultiblockShapeInfo;
import net.minecraftforge.client.model.generators.BlockModelBuilder;
import net.minecraftforge.fluids.FluidType;

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
                                    GregSteamExpansion.id("block/generators/boiler/mixed_fuel")))
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
            .tooltipBuilder(GSEMachines::steamExhaustHatchTooltips)
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
        model.texture("overlay_front", GregSteamExpansion.id("block/machine/part/steam_exhaust_hatch"));
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
            .model(steamHatchModel(GregSteamExpansion.id("block/machine/part/steam_supply_hatch")))
            .langValue("Steam Supply Hatch")
            .tooltipBuilder(GSEMachines::steamSupplyHatchTooltips)
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
            .model(steamHatchModel(GregSteamExpansion.id("block/machine/part/steam_fluid_input_hatch")))
            .langValue("Steam Fluid Input Hatch")
            .tooltipBuilder(GSEMachines::steamFluidImportHatchTooltips)
            .allowCoverOnFront(true)
            .register();

    public static final MachineDefinition STEAM_FLUID_EXPORT_HATCH = GSERegistration.REGISTRATE
            .machine("steam_fluid_output_hatch", holder -> new SteamFluidHatchPartMachine(holder, IO.OUT))
            .rotationState(RotationState.ALL)
            .abilities(GSEPartAbilities.STEAM_EXPORT_FLUIDS)
            .modelProperty(GTMachineModelProperties.IS_STEEL_MACHINE,
                    com.gregtechceu.gtceu.config.ConfigHolder.INSTANCE.machines.steelSteamMultiblocks)
            .model(steamHatchModel(GregSteamExpansion.id("block/machine/part/steam_fluid_output_hatch")))
            .langValue("Steam Fluid Output Hatch")
            .tooltipBuilder(GSEMachines::steamFluidExportHatchTooltips)
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
            .model(steamHatchModel(GregSteamExpansion.id("block/machine/part/steam_air_intake_hatch")))
            .langValue("Steam Air Intake Hatch")
            .tooltipBuilder(GSEMachines::steamAirIntakeHatchTooltips)
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


    private static void steamSupplyHatchTooltips(ItemStack stack, List<Component> tooltip) {
        tooltip.add(Component.translatable(
                "gregsteamexpansion.machine.steam_supply_hatch.tooltip.capacity",
                String.format("%,d", SteamSupplyHatchPartMachine.INITIAL_TANK_CAPACITY))
                .withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable(
                "gregsteamexpansion.machine.steam_supply_hatch.tooltip.accepted").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(
                "gregsteamexpansion.machine.steam_supply_hatch.tooltip.summary").withStyle(ChatFormatting.GRAY));
        if (!GTUtil.isShiftDown()) {
            return;
        }
        tooltip.add(Component.translatable(
                "gregsteamexpansion.machine.steam_supply_hatch.tooltip.details.subtitle")
                .withStyle(ChatFormatting.DARK_AQUA));
        tooltip.add(Component.translatable(
                "gregsteamexpansion.machine.steam_supply_hatch.tooltip.details.0").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(
                "gregsteamexpansion.machine.steam_supply_hatch.tooltip.details.1").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(
                "gregsteamexpansion.machine.steam_supply_hatch.tooltip.details.2").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(
                "gregsteamexpansion.machine.steam_supply_hatch.tooltip.details.3").withStyle(ChatFormatting.YELLOW));
    }

    private static void steamFluidImportHatchTooltips(ItemStack stack, List<Component> tooltip) {
        tooltip.add(Component.translatable(
                "gregsteamexpansion.machine.steam_fluid_hatch.tooltip.capacity",
                String.format("%,d", SteamFluidHatchPartMachine.INITIAL_TANK_CAPACITY))
                .withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable(
                "gregsteamexpansion.machine.steam_fluid_hatch.tooltip.import.summary")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(
                "gregsteamexpansion.machine.steam_fluid_hatch.tooltip.not_steam_energy")
                .withStyle(ChatFormatting.AQUA));
        if (!GTUtil.isShiftDown()) {
            return;
        }
        tooltip.add(Component.translatable(
                "gregsteamexpansion.machine.steam_fluid_hatch.tooltip.details.subtitle")
                .withStyle(ChatFormatting.DARK_AQUA));
        tooltip.add(Component.translatable(
                "gregsteamexpansion.machine.steam_fluid_hatch.tooltip.details.0").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(
                "gregsteamexpansion.machine.steam_fluid_hatch.tooltip.details.1").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(
                "gregsteamexpansion.machine.steam_fluid_hatch.tooltip.details.2").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(
                "gregsteamexpansion.machine.steam_fluid_hatch.tooltip.details.3").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(
                "gregsteamexpansion.machine.steam_fluid_hatch.tooltip.details.4").withStyle(ChatFormatting.YELLOW));
    }

    private static void steamFluidExportHatchTooltips(ItemStack stack, List<Component> tooltip) {
        tooltip.add(Component.translatable(
                "gregsteamexpansion.machine.steam_fluid_hatch.tooltip.capacity",
                String.format("%,d", SteamFluidHatchPartMachine.INITIAL_TANK_CAPACITY))
                .withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable(
                "gregsteamexpansion.machine.steam_fluid_hatch.tooltip.export.summary")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(
                "gregsteamexpansion.machine.steam_fluid_hatch.tooltip.not_steam_energy")
                .withStyle(ChatFormatting.AQUA));
        if (!GTUtil.isShiftDown()) {
            return;
        }
        tooltip.add(Component.translatable(
                "gregsteamexpansion.machine.steam_fluid_hatch.tooltip.details.subtitle")
                .withStyle(ChatFormatting.DARK_AQUA));
        tooltip.add(Component.translatable(
                "gregsteamexpansion.machine.steam_fluid_hatch.tooltip.details.0").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(
                "gregsteamexpansion.machine.steam_fluid_hatch.tooltip.details.1").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(
                "gregsteamexpansion.machine.steam_fluid_hatch.tooltip.details.2").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(
                "gregsteamexpansion.machine.steam_fluid_hatch.tooltip.details.3").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(
                "gregsteamexpansion.machine.steam_fluid_hatch.tooltip.details.4").withStyle(ChatFormatting.YELLOW));
    }

    private static void steamAirIntakeHatchTooltips(ItemStack stack, List<Component> tooltip) {
        tooltip.add(Component.translatable(
                "gregsteamexpansion.machine.steam_air_intake_hatch.tooltip.capacity",
                String.valueOf(SteamAirIntakeHatchPartMachine.INITIAL_TANK_CAPACITY / FluidType.BUCKET_VOLUME),
                String.format("%,d", SteamAirIntakeHatchPartMachine.INITIAL_TANK_CAPACITY))
                .withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable(
                "gregsteamexpansion.machine.steam_air_intake_hatch.tooltip.rate",
                String.valueOf(SteamAirIntakeHatchPartMachine.COLLECT_CYCLE_TICKS),
                String.format("%,d", SteamAirIntakeHatchPartMachine.COLLECT_AMOUNT))
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(
                "gregsteamexpansion.machine.steam_air_intake_hatch.tooltip.summary").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(
                "gregsteamexpansion.machine.steam_air_intake_hatch.tooltip.no_output")
                .withStyle(ChatFormatting.AQUA));
        if (!GTUtil.isShiftDown()) {
            return;
        }
        tooltip.add(Component.translatable(
                "gregsteamexpansion.machine.steam_air_intake_hatch.tooltip.details.subtitle")
                .withStyle(ChatFormatting.DARK_AQUA));
        tooltip.add(Component.translatable(
                "gregsteamexpansion.machine.steam_air_intake_hatch.tooltip.details.0",
                String.format("%,d", SteamAirIntakeHatchPartMachine.COLLECT_AMOUNT))
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(
                "gregsteamexpansion.machine.steam_air_intake_hatch.tooltip.details.1").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(
                "gregsteamexpansion.machine.steam_air_intake_hatch.tooltip.details.2").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(
                "gregsteamexpansion.machine.steam_air_intake_hatch.tooltip.details.3").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(
                "gregsteamexpansion.machine.steam_air_intake_hatch.tooltip.details.4").withStyle(ChatFormatting.YELLOW));
    }

    private static void steamExhaustHatchTooltips(ItemStack stack, List<Component> tooltip) {
        // Two-tier item tooltip (large-heat-storage-steam-furnace.md 物品提示):
        // gray = normal, aqua = key values, yellow/red = restrictions and danger.
        tooltip.add(Component.translatable(
                "gregsteamexpansion.machine.steam_exhaust_hatch.tooltip.summary.0").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(
                "gregsteamexpansion.machine.steam_exhaust_hatch.tooltip.summary.1").withStyle(ChatFormatting.YELLOW));
        tooltip.add(Component.translatable(
                "gregsteamexpansion.machine.steam_exhaust_hatch.tooltip.summary.2").withStyle(ChatFormatting.RED));
        if (!GTUtil.isShiftDown()) {
            return;
        }
        tooltip.add(Component.translatable(
                "gregsteamexpansion.machine.steam_exhaust_hatch.tooltip.details.subtitle")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(
                "gregsteamexpansion.machine.steam_exhaust_hatch.tooltip.details.0").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(
                "gregsteamexpansion.machine.steam_exhaust_hatch.tooltip.details.1").withStyle(ChatFormatting.YELLOW));
        tooltip.add(Component.translatable(
                "gregsteamexpansion.machine.steam_exhaust_hatch.tooltip.details.2",
                aquaText("20")).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(
                "gregsteamexpansion.machine.steam_exhaust_hatch.tooltip.details.3",
                aquaText("200"), aquaText("12"), aquaText("20")).withStyle(ChatFormatting.RED));
        tooltip.add(Component.translatable(
                "gregsteamexpansion.machine.steam_exhaust_hatch.tooltip.details.4").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(
                "gregsteamexpansion.machine.steam_exhaust_hatch.tooltip.details.5").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(
                "gregsteamexpansion.machine.steam_exhaust_hatch.tooltip.details.6").withStyle(ChatFormatting.YELLOW));
    }

    private static Component aquaText(String value) {
        return Component.literal(value).withStyle(ChatFormatting.AQUA);
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
            .shapeInfos(definition -> {
                List<MultiblockShapeInfo> infos = new ArrayList<>();
                for (int width : GSEFurnacePatterns.WIDTHS) {
                    infos.add(GSEFurnacePatterns.createShapeInfo(definition, width));
                }
                return infos;
            })
            .model(GTMachineModels.createWorkableSteamHullMachineModel(false,
                    GregSteamExpansion.id("block/machine/large_heat_storage_steam_furnace")))
            .langValue("Large Heat-Storage Steam Furnace")
            .tooltipBuilder(GSEMachines::furnaceTooltips)
            .register();

    private static void furnaceTooltips(ItemStack stack, List<Component> tooltip) {
        var p = "gregsteamexpansion.machine.large_heat_storage_steam_furnace.tooltip.";
        tooltip.add(Component.translatable(p + "summary.0").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(p + "summary.1").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(p + "summary.2").withStyle(ChatFormatting.YELLOW));
        if (!GTUtil.isShiftDown()) {
            return;
        }
        subtitle(tooltip, p + "subtitle.0");
        tooltip.add(Component.translatable(p + "details.0", aquaText("7×7"), aquaText("11×11"),
                aquaText("15×15"), aquaText("6"), aquaText("18")).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(p + "details.1").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(p + "details.2").withStyle(ChatFormatting.GRAY));
        subtitle(tooltip, p + "subtitle.1");
        tooltip.add(Component.translatable(p + "details.3").withStyle(ChatFormatting.YELLOW));
        tooltip.add(Component.translatable(p + "details.4", aquaText("1200")).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(p + "details.5", aquaText("600"), aquaText("900"), aquaText("1200"),
                aquaText("1000"), aquaText("1500"), aquaText("2000")).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(p + "details.6").withStyle(ChatFormatting.GRAY));
        subtitle(tooltip, p + "subtitle.2");
        tooltip.add(Component.translatable(p + "details.7", aquaText("32")).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(p + "details.8").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(p + "details.9").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(p + "details.10").withStyle(ChatFormatting.YELLOW));
        subtitle(tooltip, p + "subtitle.3");
        tooltip.add(Component.translatable(p + "details.11").withStyle(ChatFormatting.YELLOW));
        tooltip.add(Component.translatable(p + "details.12", aquaText("3")).withStyle(ChatFormatting.YELLOW));
        tooltip.add(Component.translatable(p + "details.13", aquaText("12")).withStyle(ChatFormatting.RED));
        subtitle(tooltip, p + "subtitle.4");
        tooltip.add(Component.translatable(p + "details.14").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(p + "details.15").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(p + "details.16").withStyle(ChatFormatting.GRAY));
    }

    private static void subtitle(List<Component> tooltip, String key) {
        tooltip.add(Component.translatable(key).withStyle(ChatFormatting.DARK_AQUA));
    }

    private GSEMachines() {}

    public static void init() {}

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
