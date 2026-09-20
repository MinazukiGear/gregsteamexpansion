package com.hoshino.gregsteamexpansion.integration.jade;

import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.utils.FormattingUtil;
import com.hoshino.gregsteamexpansion.GregSteamExpansion;
import com.hoshino.gregsteamexpansion.machine.multiblock.LargeHeatStorageSteamFurnaceMachine;
import com.hoshino.gregsteamexpansion.machine.multiblock.crusher.AbstractSteamCrusherMachine;
import com.hoshino.gregsteamexpansion.machine.multiblock.processor.AbstractSteamProcessorMachine;
import com.hoshino.gregsteamexpansion.machine.multiblock.voidproducer.AbstractSteamVoidMachine;
import com.hoshino.gregsteamexpansion.machine.multiblock.part.SteamAirIntakeHatchPartMachine;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;

import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

import static com.hoshino.gregsteamexpansion.integration.jade.SteamMachineJadeSupport.*;

/** Steam controller and air-intake providers. */
final class SteamMachineJadeProviders {
    static final ResourceLocation AIR_INTAKE_FLUID_SUMMARY = GregSteamExpansion.id("steam_air_intake_fluid_summary");

    private SteamMachineJadeProviders() {}

    enum CrusherProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
        INSTANCE;

        static final ResourceLocation UID = GregSteamExpansion.id("steam_crusher_info");
        private static final String DATA_KEY = "GregSteamExpansionCrusher";

        @Override
        public void appendServerData(CompoundTag serverData, BlockAccessor accessor) {
            if (!(accessor.getBlockEntity() instanceof MetaMachineBlockEntity blockEntity) ||
                    !(blockEntity.getMetaMachine() instanceof AbstractSteamCrusherMachine crusher)) {
                return;
            }
            serverData.put(DATA_KEY, SteamMachineSnapshot.from(crusher).toTag());
        }

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            CompoundTag serverData = accessor.getServerData();
            if (!serverData.contains(DATA_KEY, Tag.TAG_COMPOUND)) return;
            CompoundTag data = serverData.getCompound(DATA_KEY);
            appendSteamMachineTooltip(tooltip, data, CRUSHER_TOOLTIP_PREFIX,
                    steamMachineStatusKey(data.getString("statusId")));
        }

        @Override
        public ResourceLocation getUid() {
            return UID;
        }
    }

    /**
     * 轻量蒸汽多方块家族 Jade 数据协议 (steam-compressor.md 议题 9 GUI/Jade
     * 沿用粉碎机骨架): the same server-authoritative snapshot as the crusher
     * provider, minus the exhaust state — status priority, locked
     * recipe/progress/parallel, steam totals and the pending-output summary.
     */
    enum ProcessorProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
        INSTANCE;

        static final ResourceLocation UID = GregSteamExpansion.id("steam_compressor_info");
        private static final String DATA_KEY = "GregSteamExpansionProcessor";

        @Override
        public void appendServerData(CompoundTag serverData, BlockAccessor accessor) {
            if (!(accessor.getBlockEntity() instanceof MetaMachineBlockEntity blockEntity) ||
                    !(blockEntity.getMetaMachine() instanceof AbstractSteamProcessorMachine processor)) {
                return;
            }
            CompoundTag data = SteamMachineSnapshot.from(processor).toTag();
            // Preserve the controller's concrete status wording. Some processors
            // intentionally specialize a shared state id (the blast furnace uses
            // auxiliary_shortfall for its dedicated "blast air shortage" text).
            Component statusText = processor.getStatusText();
            if (statusText.getContents() instanceof TranslatableContents translatable) {
                data.putString("statusKey", translatable.getKey());
            }
            data.putLong("pendingFluidTotal", processor.getPendingFluidTotal());
            data.putInt("pendingFluidKinds", processor.getPendingFluidKinds());
            // 议题 12: 进气室状态与缓存 (仅接受进气室的机型会带出非空状态 id).
            data.putBoolean("hasIntake", processor.hasAirIntake());
            if (processor.hasAirIntake()) {
                data.putString("intakeStatusId", processor.getAirIntakeStatusId());
                data.putLong("intakeStored", processor.getAirIntakeStored());
                data.putLong("intakeCapacity", processor.getAirIntakeCapacity());
            }
            serverData.put(DATA_KEY, data);
        }

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            CompoundTag serverData = accessor.getServerData();
            if (!serverData.contains(DATA_KEY, Tag.TAG_COMPOUND)) return;
            CompoundTag data = serverData.getCompound(DATA_KEY);

            String statusKey = data.contains("statusKey", Tag.TAG_STRING)
                    ? data.getString("statusKey")
                    : steamMachineStatusKey(data.getString("statusId"));
            appendSteamMachineTooltip(tooltip, data, PROCESSOR_TOOLTIP_PREFIX, statusKey);
            if (data.getBoolean("hasIntake")) {
                // 议题 12: 与控制器 GUI 同源 — 状态 id 复用进气室自有文本.
                tooltip.add(steamMachineLine(PROCESSOR_TOOLTIP_PREFIX, "intake", Component.translatable(
                        "gregsteamexpansion.machine.steam_air_intake_hatch.status."
                                + data.getString("intakeStatusId"))));
                addStorageBar(tooltip, data.getLong("intakeStored"), data.getLong("intakeCapacity"));
            }
        }

        @Override
        public ResourceLocation getUid() {
            return UID;
        }
    }

    /**
     * 虚空产物机 Jade 数据协议: controller UI keeps compact text rows while
     * values with a meaningful ratio are rendered as native GTCEu-style bars
     * in the hover tooltip.
     */
    enum VoidProducerProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
        INSTANCE;

        static final ResourceLocation UID = GregSteamExpansion.id("void_producer_info");
        private static final String DATA_KEY = "GregSteamExpansionVoidProducer";

        @Override
        public void appendServerData(CompoundTag serverData, BlockAccessor accessor) {
            if (!(accessor.getBlockEntity() instanceof MetaMachineBlockEntity blockEntity) ||
                    !(blockEntity.getMetaMachine() instanceof AbstractSteamVoidMachine machine)) {
                return;
            }
            CompoundTag data = new CompoundTag();
            data.putString("statusId", machine.getStatusId());
            data.putInt("progress", machine.getCycleProgress());
            data.putInt("duration", machine.getCycleTicks());
            data.putLong("steamTotal", machine.getSteamTotalStored());
            data.putLong("steamCapacity", machine.getSteamTotalCapacity());
            data.putLong("steamDemand", machine.getCurrentSteamDemandPerTick());
            data.putLong("steamInputLimit", machine.getSteamInputLimitPerTick());
            data.putLong("pendingTotal", machine.getPendingTotalCount());
            data.putInt("pendingKinds", machine.getPendingKinds());
            data.putLong("pendingFluidTotal", machine.getPendingFluidTotal());
            data.putInt("pendingFluidKinds", machine.getPendingFluidKinds());
            serverData.put(DATA_KEY, data);
        }

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            CompoundTag serverData = accessor.getServerData();
            if (!serverData.contains(DATA_KEY, Tag.TAG_COMPOUND)) return;
            CompoundTag data = serverData.getCompound(DATA_KEY);

            tooltip.add(steamMachineLine(VOID_PRODUCER_TOOLTIP_PREFIX, "status",
                    Component.translatable(steamMachineStatusKey(data.getString("statusId")))));
            addWorkProgressBar(tooltip, data.getInt("progress"), data.getInt("duration"),
                    !"working_disabled".equals(data.getString("statusId")));
            addStorageBar(tooltip, data.getLong("steamTotal"), data.getLong("steamCapacity"));
            addRatioBar(tooltip, data.getLong("steamDemand"), data.getLong("steamInputLimit"),
                    Component.translatable("gtceu.jade.fluid_use",
                            FormattingUtil.formatNumbers(data.getLong("steamDemand"))));
            if (data.getLong("pendingTotal") > 0) {
                tooltip.add(steamMachineLine(VOID_PRODUCER_TOOLTIP_PREFIX, "pending",
                        FormattingUtil.formatNumbers(data.getLong("pendingTotal")),
                        data.getInt("pendingKinds")));
            }
            if (data.getLong("pendingFluidTotal") > 0) {
                tooltip.add(steamMachineLine(VOID_PRODUCER_TOOLTIP_PREFIX, "pending_fluid",
                        FormattingUtil.formatNumbers(data.getLong("pendingFluidTotal")),
                        data.getInt("pendingFluidKinds")));
            }
        }

        @Override
        public ResourceLocation getUid() {
            return UID;
        }
    }

    enum AirIntakeProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
        INSTANCE;
        static final ResourceLocation UID = GregSteamExpansion.id("steam_air_intake_hatch_info");
        private static final String DATA_KEY = "GregSteamExpansionAirIntake";

        @Override
        public void appendServerData(CompoundTag serverData, BlockAccessor accessor) {
            if (!(accessor.getBlockEntity() instanceof MetaMachineBlockEntity blockEntity) ||
                    !(blockEntity.getMetaMachine() instanceof SteamAirIntakeHatchPartMachine intake)) {
                return;
            }
            CompoundTag data = new CompoundTag();
            data.putString("statusId", intake.getIntakeStatus().getId());
            data.putInt("ticksUntilCollection", intake.getTicksUntilCollection());
            data.putInt("storedAmount", intake.tank.getFluidInTank(0).getAmount());
            data.putInt("capacity", SteamAirIntakeHatchPartMachine.INITIAL_TANK_CAPACITY);
            serverData.put(DATA_KEY, data);
        }

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            CompoundTag serverData = accessor.getServerData();
            if (!serverData.contains(DATA_KEY, Tag.TAG_COMPOUND)) return;
            CompoundTag data = serverData.getCompound(DATA_KEY);

            tooltip.add(line("status", Component.translatable(
                    "gregsteamexpansion.machine.steam_air_intake_hatch.status." + data.getString("statusId"))));
            addStorageBar(tooltip, data.getInt("storedAmount"), data.getInt("capacity"),
                    AIR_INTAKE_FLUID_SUMMARY);
            int ticks = data.getInt("ticksUntilCollection");
            if ("collecting".equals(data.getString("statusId"))) {
                addWorkProgressBar(tooltip, SteamAirIntakeHatchPartMachine.COLLECT_CYCLE_TICKS - ticks,
                        SteamAirIntakeHatchPartMachine.COLLECT_CYCLE_TICKS, true);
            }
        }

        private static Component line(String name, Object... arguments) {
            return text(name, arguments).withStyle(ChatFormatting.GRAY);
        }

        private static MutableComponent text(String name, Object... arguments) {
            return Component.translatable("gregsteamexpansion.jade.steam_air_intake_hatch." + name, arguments);
        }

        @Override
        public ResourceLocation getUid() {
            return UID;
        }
    }

    /**
     * 大型蓄热蒸汽熔炉 Jade 数据协议 (large-heat-storage-steam-furnace.md Jade
     * 数据协议与显示格式): versioned raw NBT, stable string status ids, lossless
     * numbers, five fixed display lines consistent with the controller UI.
     */
    enum FurnaceProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
        INSTANCE;

        static final ResourceLocation UID = GregSteamExpansion.id("large_heat_storage_steam_furnace_info");
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
            data.putLong("preheatProgressUnits", furnace.getPreheatProgressUnits());
            data.putLong("preheatTargetUnits", furnace.getPreheatTargetUnits());
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

            int temperature = structureValid ? data.getInt("currentTemperature") : 0;
            int maximumTemperature = structureValid ? data.getInt("maximumTemperature") : 0;
            addRatioBar(tooltip, temperature, maximumTemperature,
                    Component.translatable("gregsteamexpansion.jade.bar.temperature",
                            FormattingUtil.formatNumbers(temperature),
                            FormattingUtil.formatNumbers(maximumTemperature)));

            int parallel = structureValid && hasBatch ? data.getInt("currentParallel") : 0;
            int maximumParallel = structureValid ? data.getInt("maximumParallel") : 0;
            addRatioBar(tooltip, parallel, maximumParallel, parallelText(parallel, maximumParallel));

            long demand = structureValid && hasBatch ? data.getLong("steamDemandPerTick") : 0;
            long inputLimit = structureValid && !unlimited ? data.getLong("steamInputLimitPerTick")
                    : Math.max(1, demand);
            addRatioBar(tooltip, demand, inputLimit,
                    Component.translatable("gtceu.jade.fluid_use", FormattingUtil.formatNumbers(demand)));

            int progress = structureValid && hasBatch ? data.getInt("progressTicks") : 0;
            int duration = structureValid && hasBatch ? data.getInt("durationTicks") : 0;
            addWorkProgressBar(tooltip, progress, duration, !"working_disabled".equals(statusId));

            long preheat = structureValid ? data.getLong("preheatProgressUnits") : 0;
            long preheatTarget = structureValid ? data.getLong("preheatTargetUnits") : 0;
            addRatioBar(tooltip, preheat, preheatTarget,
                    fluidStoredText(preheat / 100, preheatTarget / 100));
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
            return text(name, arguments).withStyle(ChatFormatting.GRAY);
        }

        private static MutableComponent text(String name, Object... arguments) {
            return Component.translatable("gregsteamexpansion.jade.large_heat_storage_steam_furnace." + name,
                    arguments);
        }

        @Override
        public ResourceLocation getUid() {
            return UID;
        }
    }

    /**
     * 普通焦炉控制器 Jade 数据协议 (coke-ovens.md 普通焦炉与焦炉仓 Jade 信息):
     * 按优先级选出的唯一主状态 + GTCEu 样式进度条 + 流体输出罐实际内容与
     * 固定容量 + 全部阻塞原因; 不显示能源、蒸汽、燃料、温度信息。
     * 状态/进度/详情为 @DescSynced 字段由客户端直读; 流体罐内容不同步, 由服务端
     * 数据补充。
     */
}
