package com.hoshino.gregsteamexpansion.integration.jade;

import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.common.data.GTMaterials;
import com.gregtechceu.gtceu.utils.FormattingUtil;
import com.hoshino.gregsteamexpansion.GregSteamExpansion;
import com.hoshino.gregsteamexpansion.client.StructureErrorHighlight;
import com.hoshino.gregsteamexpansion.machine.multiblock.BoilerRoomMachine;
import com.hoshino.gregsteamexpansion.machine.multiblock.LargeSteamTankMachine;
import com.hoshino.gregsteamexpansion.machine.multiblock.part.SteamTankValvePartMachine;
import com.hoshino.gregsteamexpansion.machine.steam.MixedFuelBoilerMachine;
import com.hoshino.gregsteamexpansion.structure.StructureDiagnostics;
import com.hoshino.gregsteamexpansion.structure.StructureProblem;
import com.hoshino.gregsteamexpansion.structure.StructureText;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import snownee.jade.api.Accessor;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.fluid.JadeFluidObject;
import snownee.jade.api.view.ClientViewGroup;
import snownee.jade.api.view.FluidView;
import snownee.jade.api.view.IClientExtensionProvider;
import snownee.jade.api.view.IServerExtensionProvider;
import snownee.jade.api.view.ViewGroup;

import java.util.List;

/** Boiler, tank and generic structure-diagnostic providers. */
final class UtilityJadeProviders {
    private UtilityJadeProviders() {}

    enum BoilerRoomScaleProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
        INSTANCE;

        private static final ResourceLocation UID = GregSteamExpansion.id("boiler_room_water_scale");
        private static final String DATA_KEY = "GregSteamExpansionBoilerRoomScale";

        @Override
        public void appendServerData(CompoundTag serverData, BlockAccessor accessor) {
            if (!(accessor.getBlockEntity() instanceof MetaMachineBlockEntity blockEntity) ||
                    !(blockEntity.getMetaMachine() instanceof BoilerRoomMachine boiler)) return;
            CompoundTag data = new CompoundTag();
            data.putInt("Scale", boiler.getWaterScalePercent());
            data.putInt("Loss", boiler.getWaterScaleLossPercent());
            data.putBoolean("Scrapped", boiler.isScrappedByScale());
            data.putBoolean("Descaling", boiler.isDescaling());
            data.putInt("DescaleProgress", (int) Math.round(boiler.getDescalingProgress() * 100.0));
            serverData.put(DATA_KEY, data);
        }

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            if (!accessor.getServerData().contains(DATA_KEY, Tag.TAG_COMPOUND)) return;
            CompoundTag data = accessor.getServerData().getCompound(DATA_KEY);
            if (data.getBoolean("Scrapped")) {
                tooltip.add(Component.translatable("gregsteamexpansion.jade.boiler_room.scrapped")
                        .withStyle(ChatFormatting.DARK_RED));
            } else if (data.getBoolean("Descaling")) {
                tooltip.add(Component.translatable("gregsteamexpansion.jade.boiler_room.descaling",
                        data.getInt("DescaleProgress")).withStyle(ChatFormatting.AQUA));
            } else {
                tooltip.add(Component.translatable("gregsteamexpansion.jade.boiler_room.water_scale",
                        data.getInt("Scale"), data.getInt("Loss")).withStyle(ChatFormatting.GRAY));
            }
        }

        @Override
        public ResourceLocation getUid() {
            return UID;
        }
    }

    enum MixedFuelBoilerProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
        INSTANCE;

        private static final ResourceLocation UID = GregSteamExpansion.id("mixed_fuel_boiler_info");
        private static final String DATA_KEY = "GregSteamExpansionMixedFuelBoiler";

        @Override
        public void appendServerData(CompoundTag serverData, BlockAccessor accessor) {
            if (!(accessor.getBlockEntity() instanceof MetaMachineBlockEntity blockEntity) ||
                    !(blockEntity.getMetaMachine() instanceof MixedFuelBoilerMachine boiler)) return;
            CompoundTag data = new CompoundTag();
            data.putBoolean("CoFiring", boiler.isCoFiring());
            data.putInt("Temperature", boiler.getCurrentTemperature());
            data.putInt("MaxTemperature", boiler.getMaxTemperature());
            data.putDouble("SteamOutput", boiler.getCurrentSteamOutputPerTick());
            data.putInt("PowderTicks", boiler.getPowderBurnRemainingTicks());
            data.putString("Status", boiler.getStatusTranslationKey());
            serverData.put(DATA_KEY, data);
        }

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            CompoundTag serverData = accessor.getServerData();
            if (!serverData.contains(DATA_KEY, Tag.TAG_COMPOUND)) return;
            CompoundTag data = serverData.getCompound(DATA_KEY);
            Component mode = Component.translatable(data.getBoolean("CoFiring")
                    ? "gregsteamexpansion.machine.mixed_fuel_boiler.mode.co_firing"
                    : "gregsteamexpansion.machine.mixed_fuel_boiler.mode.liquid");
            tooltip.add(line("mode", mode));
            tooltip.add(line("status", Component.translatable(data.getString("Status"))));
            tooltip.add(line("temperature",
                    FormattingUtil.formatNumbers(data.getInt("Temperature") + 274),
                    FormattingUtil.formatNumbers(data.getInt("MaxTemperature") + 274)));
            tooltip.add(line("steam_output", FormattingUtil.formatNumbers(data.getDouble("SteamOutput"))));
            if (data.getBoolean("CoFiring")) {
                tooltip.add(line("powder_time", FormattingUtil.formatNumbers(data.getInt("PowderTicks") / 20.0)));
            }
        }

        private static Component line(String name, Object... arguments) {
            return Component.translatable("gregsteamexpansion.jade.mixed_fuel_boiler." + name, arguments)
                    .withStyle(ChatFormatting.GRAY);
        }

        @Override
        public ResourceLocation getUid() {
            return UID;
        }
    }

    enum SteamTankFluidProvider implements
            IServerExtensionProvider<MetaMachineBlockEntity, CompoundTag>,
            IClientExtensionProvider<CompoundTag, FluidView> {
        INSTANCE;

        private static final ResourceLocation UID = GregSteamExpansion.id("large_steam_tank_storage");

        @Override
        public List<ClientViewGroup<FluidView>> getClientGroups(
                Accessor<?> accessor, List<ViewGroup<CompoundTag>> groups) {
            return ClientViewGroup.map(groups, FluidView::readDefault, null);
        }

        @Override
        public List<ViewGroup<CompoundTag>> getGroups(ServerPlayer player, ServerLevel level,
                                                       MetaMachineBlockEntity blockEntity,
                                                       boolean showDetails) {
            if (!(blockEntity.getMetaMachine() instanceof LargeSteamTankMachine tank)
                    || !tank.isFormed() || tank.getFormedCapacity() <= 0) return List.of();
            CompoundTag fluid = FluidView.writeDefault(
                    JadeFluidObject.of(GTMaterials.Steam.getFluid(), tank.getStoredAmount()),
                    tank.getFormedCapacity());
            return List.of(new ViewGroup<>(List.of(fluid)));
        }

        @Override
        public ResourceLocation getUid() {
            return UID;
        }
    }

    enum SteamTankProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
        INSTANCE;

        private static final ResourceLocation UID = GregSteamExpansion.id("large_steam_tank_info");
        private static final String DATA_KEY = "GregSteamExpansionLargeSteamTank";

        @Override
        public void appendServerData(CompoundTag serverData, BlockAccessor accessor) {
            if (!(accessor.getBlockEntity() instanceof MetaMachineBlockEntity blockEntity)) return;
            Object machine = blockEntity.getMetaMachine();
            LargeSteamTankMachine tank;
            boolean valve;
            boolean outputMode = false;
            if (machine instanceof LargeSteamTankMachine controller) {
                tank = controller;
                valve = false;
            } else if (machine instanceof SteamTankValvePartMachine part) {
                tank = part.getLinkedTank();
                valve = true;
                outputMode = part.isOutputMode();
            } else return;

            CompoundTag data = new CompoundTag();
            data.putBoolean("valve", valve);
            data.putBoolean("outputMode", outputMode);
            data.putBoolean("formed", tank != null && tank.isFormed());
            if (tank != null) {
                data.putInt("width", tank.getFormedWidth());
                data.putInt("height", tank.getFormedHeight());
                data.putInt("valves", tank.getValveCount());
                data.putBoolean("overCapacity", tank.isOverCapacity());
            }
            serverData.put(DATA_KEY, data);
        }

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            if (!accessor.getServerData().contains(DATA_KEY, Tag.TAG_COMPOUND)) return;
            CompoundTag data = accessor.getServerData().getCompound(DATA_KEY);
            if (data.getBoolean("valve")) {
                tooltip.add(Component.translatable("gregsteamexpansion.jade.large_steam_tank.valve_mode",
                        Component.translatable(data.getBoolean("outputMode")
                                ? "gregsteamexpansion.jade.large_steam_tank.output"
                                : "gregsteamexpansion.jade.large_steam_tank.input")));
            }
            if (!data.getBoolean("formed")) {
                tooltip.add(Component.translatable("gregsteamexpansion.jade.large_steam_tank.unformed"));
                return;
            }
            tooltip.add(Component.translatable("gregsteamexpansion.jade.large_steam_tank.structure",
                    data.getInt("width"), data.getInt("width"), data.getInt("height"), data.getInt("valves")));
            if (data.getBoolean("overCapacity")) {
                tooltip.add(Component.translatable("gregsteamexpansion.jade.large_steam_tank.over_capacity")
                        .withStyle(ChatFormatting.YELLOW));
            }
        }

        @Override
        public ResourceLocation getUid() {
            return UID;
        }
    }

    enum StructureDiagnosticsProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
        INSTANCE;

        private static final ResourceLocation UID = GregSteamExpansion.id("structure_diagnostics");
        private static final String DATA_KEY = "GregSteamExpansionStructureDiagnostics";
        private static final String FORMED_KEY = "GregSteamExpansionStructureFormed";

        @Override
        public void appendServerData(CompoundTag serverData, BlockAccessor accessor) {
            if (!(accessor.getBlockEntity() instanceof MetaMachineBlockEntity blockEntity) ||
                    !(blockEntity.getMetaMachine() instanceof IMultiController controller)) return;
            serverData.putBoolean(FORMED_KEY, controller.isFormed());
            if (!controller.isFormed()) {
                StructureDiagnostics.describe(controller)
                        .ifPresent(problem -> serverData.put(DATA_KEY, problem.toTag()));
            }
        }

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            CompoundTag serverData = accessor.getServerData();
            if (serverData.getBoolean(FORMED_KEY)) {
                StructureErrorHighlight.clearForController(accessor.getPosition());
                return;
            }
            if (!serverData.contains(DATA_KEY, Tag.TAG_COMPOUND)) return;
            StructureProblem problem = StructureProblem.fromTag(serverData.getCompound(DATA_KEY));
            if (problem == null) return;
            tooltip.add(line("title", StructureText.reason(problem)));
            if (problem.hasPosition()) {
                BlockPos pos = problem.pos();
                StructureErrorHighlight.show(accessor.getPosition(), pos);
                tooltip.add(Component.translatable("gregsteamexpansion.jade.structure.pos",
                        pos.getX(), pos.getY(), pos.getZ()).withStyle(ChatFormatting.RED));
            }
            if (!problem.expected().isEmpty()) {
                tooltip.add(line("expected", StructureText.expectedNames(problem)));
            }
            tooltip.add(line("maybe_more"));
        }

        private static Component line(String name, Object... arguments) {
            return Component.translatable("gregsteamexpansion.jade.structure." + name, arguments)
                    .withStyle(ChatFormatting.GRAY);
        }

        @Override
        public ResourceLocation getUid() {
            return UID;
        }
    }
}
