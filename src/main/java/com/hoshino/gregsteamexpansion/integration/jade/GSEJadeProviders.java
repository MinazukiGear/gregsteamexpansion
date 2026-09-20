package com.hoshino.gregsteamexpansion.integration.jade;

import com.gregtechceu.gtceu.api.block.MetaMachineBlock;
import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.hoshino.gregsteamexpansion.machine.multiblock.LargeHeatStorageSteamFurnaceMachine;
import com.hoshino.gregsteamexpansion.machine.multiblock.cokeoven.GSECokeOvenMachine;
import com.hoshino.gregsteamexpansion.machine.multiblock.crusher.AbstractSteamCrusherMachine;
import com.hoshino.gregsteamexpansion.machine.multiblock.processor.AbstractSteamProcessorMachine;
import com.hoshino.gregsteamexpansion.machine.multiblock.voidproducer.AbstractSteamVoidMachine;
import com.hoshino.gregsteamexpansion.machine.multiblock.largecokeoven.LargeCokeOvenMachine;
import com.hoshino.gregsteamexpansion.machine.multiblock.part.GSECokeOvenHatch;
import com.hoshino.gregsteamexpansion.machine.multiblock.part.LargeCokeOvenHatchPartMachine;
import com.hoshino.gregsteamexpansion.machine.multiblock.part.SteamAirIntakeHatchPartMachine;

import net.minecraft.resources.ResourceLocation;

import snownee.jade.api.Accessor;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.Identifiers;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;

import static com.hoshino.gregsteamexpansion.integration.jade.CokeOvenJadeProviders.*;
import static com.hoshino.gregsteamexpansion.integration.jade.SteamMachineJadeProviders.*;

final class GSEJadeProviders {
    private static final ResourceLocation GT_CONTROLLABLE_PROVIDER =
            ResourceLocation.fromNamespaceAndPath("gtceu", "controllable_provider");
    private static final ResourceLocation GT_WORKABLE_PROVIDER =
            ResourceLocation.fromNamespaceAndPath("gtceu", "workable_provider");
    private static final ResourceLocation GT_RECIPE_LOGIC_PROVIDER =
            ResourceLocation.fromNamespaceAndPath("gtceu", "recipe_logic_provider");
    private static final ResourceLocation GT_PARALLEL_PROVIDER =
            ResourceLocation.fromNamespaceAndPath("gtceu", "parallel_info");
    private static final ResourceLocation GT_MULTIBLOCK_STRUCTURE_PROVIDER =
            ResourceLocation.fromNamespaceAndPath("gtceu", "multiblock_structure");

    static void registerCommon(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(UtilityJadeProviders.MixedFuelBoilerProvider.INSTANCE, MetaMachineBlockEntity.class);
        registration.registerBlockDataProvider(UtilityJadeProviders.BoilerRoomScaleProvider.INSTANCE, MetaMachineBlockEntity.class);
        registration.registerBlockDataProvider(FurnaceProvider.INSTANCE, MetaMachineBlockEntity.class);
        registration.registerBlockDataProvider(AirIntakeProvider.INSTANCE, MetaMachineBlockEntity.class);
        registration.registerBlockDataProvider(CrusherProvider.INSTANCE, MetaMachineBlockEntity.class);
        registration.registerBlockDataProvider(ProcessorProvider.INSTANCE, MetaMachineBlockEntity.class);
        registration.registerBlockDataProvider(VoidProducerProvider.INSTANCE, MetaMachineBlockEntity.class);
        registration.registerBlockDataProvider(CokeOvenProvider.INSTANCE, MetaMachineBlockEntity.class);
        registration.registerBlockDataProvider(CokeOvenHatchProvider.INSTANCE, MetaMachineBlockEntity.class);
        registration.registerBlockDataProvider(LargeCokeOvenHatchProvider.INSTANCE, MetaMachineBlockEntity.class);
        registration.registerBlockDataProvider(UtilityJadeProviders.StructureDiagnosticsProvider.INSTANCE, MetaMachineBlockEntity.class);
        registration.registerBlockDataProvider(UtilityJadeProviders.SteamTankProvider.INSTANCE, MetaMachineBlockEntity.class);
        registration.registerFluidStorage(UtilityJadeProviders.SteamTankFluidProvider.INSTANCE, MetaMachineBlockEntity.class);
    }

    static void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(UtilityJadeProviders.MixedFuelBoilerProvider.INSTANCE, MetaMachineBlock.class);
        registration.registerBlockComponent(UtilityJadeProviders.BoilerRoomScaleProvider.INSTANCE, MetaMachineBlock.class);
        registration.registerBlockComponent(FurnaceProvider.INSTANCE, MetaMachineBlock.class);
        registration.registerBlockComponent(AirIntakeProvider.INSTANCE, MetaMachineBlock.class);
        registration.registerBlockComponent(CrusherProvider.INSTANCE, MetaMachineBlock.class);
        registration.registerBlockComponent(ProcessorProvider.INSTANCE, MetaMachineBlock.class);
        registration.registerBlockComponent(VoidProducerProvider.INSTANCE, MetaMachineBlock.class);
        registration.registerBlockComponent(CokeOvenProvider.INSTANCE, MetaMachineBlock.class);
        registration.registerBlockComponent(CokeOvenHatchProvider.INSTANCE, MetaMachineBlock.class);
        registration.registerBlockComponent(LargeCokeOvenProvider.INSTANCE, MetaMachineBlock.class);
        registration.registerBlockComponent(LargeCokeOvenHatchProvider.INSTANCE, MetaMachineBlock.class);
        registration.registerBlockComponent(UtilityJadeProviders.StructureDiagnosticsProvider.INSTANCE, MetaMachineBlock.class);
        registration.registerBlockComponent(UtilityJadeProviders.SteamTankProvider.INSTANCE, MetaMachineBlock.class);
        registration.registerFluidStorageClient(UtilityJadeProviders.SteamTankFluidProvider.INSTANCE);
        registration.registerBlockComponent(OwnedBrickProvider.INSTANCE,
                com.gregtechceu.gtceu.common.data.GTBlocks.CASING_COKE_BRICKS.get().getClass());
        registration.addTooltipCollectedCallback(TooltipDeduplication.INSTANCE);
    }

    /**
     * Removes upstream rows only when the corresponding GSE provider actually
     * contributed its replacement. Jade tags every element with its provider
     * UID, so this remains independent of plugin/provider registration order.
     */
    private enum TooltipDeduplication implements snownee.jade.api.callback.JadeTooltipCollectedCallback {
        INSTANCE;

        @Override
        public void onTooltipCollected(ITooltip tooltip, Accessor<?> genericAccessor) {
            if (!(genericAccessor instanceof BlockAccessor accessor) ||
                    !(accessor.getBlockEntity() instanceof MetaMachineBlockEntity blockEntity)) {
                return;
            }
            Object machine = blockEntity.getMetaMachine();

            boolean manualSteamController =
                    (machine instanceof AbstractSteamCrusherMachine && contributed(tooltip, CrusherProvider.UID)) ||
                    (machine instanceof AbstractSteamProcessorMachine && contributed(tooltip, ProcessorProvider.UID)) ||
                    (machine instanceof AbstractSteamVoidMachine && contributed(tooltip, VoidProducerProvider.UID)) ||
                    (machine instanceof LargeHeatStorageSteamFurnaceMachine &&
                            contributed(tooltip, FurnaceProvider.UID));
            if (manualSteamController) {
                // These controllers provide their own authoritative status,
                // including structure-invalid and working-disabled states.
                tooltip.remove(GT_MULTIBLOCK_STRUCTURE_PROVIDER);
                tooltip.remove(GT_CONTROLLABLE_PROVIDER);
                return;
            }

            if (machine instanceof GSECokeOvenMachine && contributed(tooltip, CokeOvenProvider.UID)) {
                tooltip.remove(GT_MULTIBLOCK_STRUCTURE_PROVIDER);
                tooltip.remove(GT_WORKABLE_PROVIDER);
                tooltip.remove(GT_RECIPE_LOGIC_PROVIDER);
            }

            if (machine instanceof LargeCokeOvenMachine && contributed(tooltip, LargeCokeOvenProvider.UID)) {
                tooltip.remove(GT_MULTIBLOCK_STRUCTURE_PROVIDER);
                tooltip.remove(GT_WORKABLE_PROVIDER);
                tooltip.remove(GT_RECIPE_LOGIC_PROVIDER);
                tooltip.remove(GT_PARALLEL_PROVIDER);
                return;
            }

            // Prefer Jade's native inventory/tank views when they are enabled;
            // keep the compact GSE summary as a fallback when they are absent.
            if (machine instanceof SteamAirIntakeHatchPartMachine &&
                    contributed(tooltip, AIR_INTAKE_FLUID_SUMMARY) &&
                    contributed(tooltip, Identifiers.UNIVERSAL_FLUID_STORAGE)) {
                // Keep Jade/GTCEu's native fluid view when available. The GSE
                // capacity bar is only a fallback for configurations where
                // the native view is disabled or unavailable.
                tooltip.remove(AIR_INTAKE_FLUID_SUMMARY);
            } else if (machine instanceof GSECokeOvenMachine &&
                    contributed(tooltip, Identifiers.UNIVERSAL_FLUID_STORAGE)) {
                tooltip.remove(COKE_OVEN_FLUID_SUMMARY);
            } else if (machine instanceof GSECokeOvenHatch) {
                removeNativeStorageDuplicates(tooltip, COKE_OVEN_HATCH_ITEM_SUMMARY,
                        COKE_OVEN_HATCH_FLUID_SUMMARY);
            } else if (machine instanceof LargeCokeOvenHatchPartMachine) {
                removeNativeStorageDuplicates(tooltip, LARGE_COKE_OVEN_HATCH_ITEM_SUMMARY,
                        LARGE_COKE_OVEN_HATCH_FLUID_SUMMARY);
            }
        }

        private static void removeNativeStorageDuplicates(ITooltip tooltip, ResourceLocation itemSummary,
                                                           ResourceLocation fluidSummary) {
            if (contributed(tooltip, Identifiers.UNIVERSAL_ITEM_STORAGE)) {
                tooltip.remove(itemSummary);
            }
            if (contributed(tooltip, Identifiers.UNIVERSAL_FLUID_STORAGE)) {
                tooltip.remove(fluidSummary);
            }
        }

        private static boolean contributed(ITooltip tooltip, ResourceLocation uid) {
            return !tooltip.get(uid).isEmpty();
        }
    }

}
