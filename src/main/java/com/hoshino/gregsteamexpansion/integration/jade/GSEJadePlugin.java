package com.hoshino.gregsteamexpansion.integration.jade;

import com.gregtechceu.gtceu.api.block.MetaMachineBlock;
import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.utils.FormattingUtil;
import com.hoshino.gregsteamexpansion.GregSteamExpansion;
import com.hoshino.gregsteamexpansion.machine.multiblock.LargeHeatStorageSteamFurnaceMachine;
import com.hoshino.gregsteamexpansion.machine.steam.MixedFuelBoilerMachine;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;
import snownee.jade.api.config.IPluginConfig;

@WailaPlugin
public final class GSEJadePlugin implements IWailaPlugin {
    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(MixedFuelBoilerProvider.INSTANCE, MetaMachineBlockEntity.class);
        registration.registerBlockDataProvider(FurnaceProvider.INSTANCE, MetaMachineBlockEntity.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(MixedFuelBoilerProvider.INSTANCE, MetaMachineBlock.class);
        registration.registerBlockComponent(FurnaceProvider.INSTANCE, MetaMachineBlock.class);
    }

    /**
     * 大型蓄热蒸汽熔炉 Jade 数据协议 (large-heat-storage-steam-furnace.md Jade
     * 数据协议与显示格式): versioned raw NBT, stable string status ids, lossless
     * numbers, five fixed display lines consistent with the controller UI.
     */
    private enum FurnaceProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
        INSTANCE;

        private static final ResourceLocation UID = GregSteamExpansion.id("large_heat_storage_steam_furnace_info");
        private static final String DATA_KEY = "GregSteamExpansionFurnace";
        private static final int DATA_VERSION = 1;

        @Override
        public void appendServerData(CompoundTag serverData, BlockAccessor accessor) {
            if (!(accessor.getBlockEntity() instanceof MetaMachineBlockEntity blockEntity) ||
                    !(blockEntity.getMetaMachine() instanceof LargeHeatStorageSteamFurnaceMachine furnace)) {
                return;
            }
            CompoundTag data = new CompoundTag();
            data.putInt("dataVersion", DATA_VERSION);
            data.putBoolean("structureValid", furnace.isFormed());
            data.putString("statusId", furnace.getStatusId());
            data.putInt("currentTemperature", furnace.getCurrentTemperature());
            data.putInt("startupTemperature", furnace.getStartupTemperature());
            data.putInt("maximumTemperature", furnace.maxTemperature());
            data.putBoolean("hasBatch", furnace.hasBatch());
            data.putInt("currentParallel", furnace.getCurrentBatchParallel());
            data.putInt("maximumParallel", furnace.maximumParallel());
            data.putLong("steamDemandPerTick", furnace.getCurrentBatchSteamPerTick());
            data.putLong("steamInputLimitPerTick", furnace.getSteamInputLimitPerTick());
            data.putBoolean("unlimitedSteamInput", furnace.isSteamInputUnlimited());
            data.putInt("progressTicks", furnace.getBatchProgress());
            data.putInt("durationTicks", furnace.getBatchDuration());
            serverData.put(DATA_KEY, data);
        }

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            CompoundTag serverData = accessor.getServerData();
            if (!serverData.contains(DATA_KEY, Tag.TAG_COMPOUND)) return;
            CompoundTag data = serverData.getCompound(DATA_KEY);
            if (data.getInt("dataVersion") > DATA_VERSION) {
                // 高版本数据: 只读取已知字段 (本版字段全集已知, 无需跳过).
            }
            boolean structureValid = data.getBoolean("structureValid");
            boolean hasBatch = data.getBoolean("hasBatch");
            boolean unlimited = data.getBoolean("unlimitedSteamInput");

            String statusId = data.contains("statusId") ? data.getString("statusId") : "cooling";
            tooltip.add(line("status", statusText(statusId)));

            if (structureValid) {
                tooltip.add(line("temperature", String.valueOf(data.getInt("currentTemperature")),
                        String.valueOf(data.getInt("startupTemperature")),
                        String.valueOf(data.getInt("maximumTemperature"))));
                tooltip.add(line("parallel", hasBatch ? String.valueOf(data.getInt("currentParallel")) : "—",
                        String.valueOf(data.getInt("maximumParallel"))));
                tooltip.add(line("steam",
                        hasBatch ? FormattingUtil.formatNumbers(data.getLong("steamDemandPerTick")) : "—",
                        unlimited ? Component.translatable(
                                        "gregsteamexpansion.machine.large_heat_storage_steam_furnace.tooltip.ui.unlimited")
                                .getString()
                                : FormattingUtil.formatNumbers(data.getLong("steamInputLimitPerTick"))));
                if (hasBatch) {
                    int progress = data.getInt("progressTicks");
                    int duration = data.getInt("durationTicks");
                    double percent = duration == 0 ? 0 : Math.round(progress * 1000.0 / duration) / 10.0;
                    tooltip.add(line("progress", percent + "%", progress + "/" + duration + "t"));
                } else {
                    tooltip.add(line("progress", "—", "—"));
                }
            } else {
                tooltip.add(line("temperature", "—", "—", "—"));
                tooltip.add(line("parallel", "—", "—"));
                tooltip.add(line("steam", "—", "—"));
                tooltip.add(line("progress", "—"));
            }
        }

        private static Component statusText(String statusId) {
            var key = switch (statusId) {
                case "invalid_structure" -> "gtceu.multiblock.invalid_structure";
                case "awaiting_original_size" ->
                        "gregsteamexpansion.machine.large_heat_storage_steam_furnace.status.awaiting_original_size";
                case "working_disabled" -> "gtceu.top.working_disabled";
                case "exhaust_obstructed" -> "gregsteamexpansion.multiblock.steam_exhaust_hatch_obstructed";
                case "low_steam" -> "gtceu.multiblock.steam.low_steam";
                case "insufficient_outputs" -> "gtceu.recipe_logic.insufficient_out";
                case "working" -> "gtceu.multiblock.large_miner.working";
                case "preheating" -> "gregsteamexpansion.machine.large_heat_storage_steam_furnace.status.preheating";
                case "at_temperature_limit" ->
                        "gregsteamexpansion.machine.large_heat_storage_steam_furnace.status.at_temperature_limit";
                case "insufficient_inputs" -> "gtceu.recipe_logic.insufficient_in";
                default -> "gregsteamexpansion.machine.large_heat_storage_steam_furnace.status.cooling";
            };
            return Component.translatable(key);
        }

        private static Component line(String name, Object... arguments) {
            return Component.translatable("gregsteamexpansion.jade.large_heat_storage_steam_furnace." + name,
                    arguments).withStyle(ChatFormatting.GRAY);
        }

        @Override
        public ResourceLocation getUid() {
            return UID;
        }
    }

    private enum MixedFuelBoilerProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
        INSTANCE;

        private static final ResourceLocation UID = GregSteamExpansion.id("mixed_fuel_boiler_info");
        private static final String DATA_KEY = "GregSteamExpansionMixedFuelBoiler";

        @Override
        public void appendServerData(CompoundTag serverData, BlockAccessor accessor) {
            if (!(accessor.getBlockEntity() instanceof MetaMachineBlockEntity blockEntity) ||
                    !(blockEntity.getMetaMachine() instanceof MixedFuelBoilerMachine boiler)) {
                return;
            }

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

            Component mode = Component.translatable(data.getBoolean("CoFiring") ?
                    "gregsteamexpansion.machine.mixed_fuel_boiler.mode.co_firing" :
                    "gregsteamexpansion.machine.mixed_fuel_boiler.mode.liquid");
            tooltip.add(line("mode", mode));
            tooltip.add(line("status", Component.translatable(data.getString("Status"))));
            tooltip.add(line("temperature",
                    FormattingUtil.formatNumbers(data.getInt("Temperature") + 274),
                    FormattingUtil.formatNumbers(data.getInt("MaxTemperature") + 274)));
            tooltip.add(line("steam_output", FormattingUtil.formatNumbers(data.getDouble("SteamOutput"))));
            if (data.getBoolean("CoFiring")) {
                tooltip.add(line("powder_time",
                        FormattingUtil.formatNumbers(data.getInt("PowderTicks") / 20.0)));
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
}
