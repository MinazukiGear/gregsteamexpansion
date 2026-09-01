package com.hoshino.gregsteamexpansion.registry;

import com.gregtechceu.gtceu.api.data.RotationState;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.property.GTMachineModelProperties;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.registry.registrate.MachineBuilder;
import com.gregtechceu.gtceu.client.model.machine.overlays.WorkableOverlays;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.gregtechceu.gtceu.common.data.machines.GTMachineUtils;
import com.gregtechceu.gtceu.common.data.models.GTMachineModels;
import com.hoshino.gregsteamexpansion.GregSteamExpansion;
import com.hoshino.gregsteamexpansion.machine.steam.MixedFuelBoilerMachine;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import it.unimi.dsi.fastutil.Pair;

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
