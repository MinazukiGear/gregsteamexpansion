package com.hoshino.gregsteamexpansion.integration.jade;

import com.gregtechceu.gtceu.api.block.MetaMachineBlock;
import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.utils.FormattingUtil;
import com.hoshino.gregsteamexpansion.GregSteamExpansion;
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
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(MixedFuelBoilerProvider.INSTANCE, MetaMachineBlock.class);
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
