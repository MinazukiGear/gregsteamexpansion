package com.hoshino.gregsteamexpansion.integration.jade;

import com.gregtechceu.gtceu.api.block.MetaMachineBlock;
import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.utils.FormattingUtil;
import com.hoshino.gregsteamexpansion.GregSteamExpansion;
import com.hoshino.gregsteamexpansion.client.cokeoven.OwnedBrickClient;
import com.hoshino.gregsteamexpansion.machine.multiblock.LargeHeatStorageSteamFurnaceMachine;
import com.hoshino.gregsteamexpansion.machine.multiblock.cokeoven.GSECokeOvenMachine;
import com.hoshino.gregsteamexpansion.machine.multiblock.crusher.AbstractSteamCrusherMachine;
import com.hoshino.gregsteamexpansion.machine.multiblock.largecokeoven.LargeCokeOvenMachine;
import com.hoshino.gregsteamexpansion.machine.multiblock.largecokeoven.LargeCokeOvenRecipeLogic;
import com.hoshino.gregsteamexpansion.machine.multiblock.part.GSECokeOvenHatch;
import com.hoshino.gregsteamexpansion.machine.multiblock.part.LargeCokeOvenHatchPartMachine;
import com.hoshino.gregsteamexpansion.machine.multiblock.part.SteamAirIntakeHatchPartMachine;
import com.hoshino.gregsteamexpansion.machine.steam.MixedFuelBoilerMachine;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
        registration.registerBlockDataProvider(AirIntakeProvider.INSTANCE, MetaMachineBlockEntity.class);
        registration.registerBlockDataProvider(CrusherProvider.INSTANCE, MetaMachineBlockEntity.class);
        registration.registerBlockDataProvider(CokeOvenProvider.INSTANCE, MetaMachineBlockEntity.class);
        registration.registerBlockDataProvider(CokeOvenHatchProvider.INSTANCE, MetaMachineBlockEntity.class);
        registration.registerBlockDataProvider(LargeCokeOvenHatchProvider.INSTANCE, MetaMachineBlockEntity.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(MixedFuelBoilerProvider.INSTANCE, MetaMachineBlock.class);
        registration.registerBlockComponent(FurnaceProvider.INSTANCE, MetaMachineBlock.class);
        registration.registerBlockComponent(AirIntakeProvider.INSTANCE, MetaMachineBlock.class);
        registration.registerBlockComponent(CrusherProvider.INSTANCE, MetaMachineBlock.class);
        registration.registerBlockComponent(CokeOvenProvider.INSTANCE, MetaMachineBlock.class);
        registration.registerBlockComponent(CokeOvenHatchProvider.INSTANCE, MetaMachineBlock.class);
        registration.registerBlockComponent(LargeCokeOvenProvider.INSTANCE, MetaMachineBlock.class);
        registration.registerBlockComponent(LargeCokeOvenHatchProvider.INSTANCE, MetaMachineBlock.class);
        registration.registerBlockComponent(OwnedBrickProvider.INSTANCE,
                com.gregtechceu.gtceu.common.data.GTBlocks.CASING_COKE_BRICKS.get().getClass());
    }

    /**
     * 蒸汽进气室 Jade 数据协议 (machines-and-hatches.md GUI/Jade 一致性): the
     * same server-side status source as the hatch GUI — stable status id,
     * synced remaining ticks and the raw tank amount, with capacity reported
     * identically to tooltips and the fluid capability.
     */

    /**
     * 粉碎机 Jade 数据协议 (steam-crushers.md Jade 信息与同步): the same
     * server-authoritative snapshot as the controller GUI — status priority,
     * locked recipe/progress/parallel, steam totals and the pending-output
     * summary; the full pending list stays GUI-only.
     */
    private enum CrusherProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
        INSTANCE;

        private static final ResourceLocation UID = GregSteamExpansion.id("steam_crusher_info");
        private static final String DATA_KEY = "GregSteamExpansionCrusher";

        @Override
        public void appendServerData(CompoundTag serverData, BlockAccessor accessor) {
            if (!(accessor.getBlockEntity() instanceof MetaMachineBlockEntity blockEntity) ||
                    !(blockEntity.getMetaMachine() instanceof AbstractSteamCrusherMachine crusher)) {
                return;
            }
            CompoundTag data = new CompoundTag();
            data.putString("statusId", crusher.getStatusId());
            data.putString("recipeId", crusher.getBatchRecipeId());
            data.putString("inputItem", crusher.getBatchInputDisplay().isEmpty() ? ""
                    : net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(
                            crusher.getBatchInputDisplay().getItem()).toString());
            data.putInt("progress", crusher.getBatchProgress());
            data.putInt("duration", crusher.getBatchDuration());
            data.putInt("parallel", crusher.getBatchParallel());
            data.putInt("parallelCap", crusher.maximumParallel());
            data.putLong("steamTotal", crusher.getSteamTotalStored());
            data.putLong("steamCap", crusher.getSteamTotalCapacity());
            data.putLong("steamPerTick", crusher.getBatchSteamPerTick());
            data.putBoolean("consuming", crusher.isConsumingSteam());
            data.putLong("pendingTotal", crusher.getPendingTotalCount());
            data.putInt("pendingKinds", crusher.getPendingKinds());
            serverData.put(DATA_KEY, data);
        }

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            CompoundTag serverData = accessor.getServerData();
            if (!serverData.contains(DATA_KEY, Tag.TAG_COMPOUND)) return;
            CompoundTag data = serverData.getCompound(DATA_KEY);

            tooltip.add(line("status", Component.translatable(statusKey(data.getString("statusId")))));
            if (!data.getString("recipeId").isEmpty()) {
                tooltip.add(line("recipe", data.getString("recipeId")));
                tooltip.add(line("progress", FormattingUtil.formatNumbers(data.getInt("progress")),
                        FormattingUtil.formatNumbers(data.getInt("duration"))));
            }
            tooltip.add(line("parallel", data.contains("parallel") && data.getInt("parallel") > 0
                    ? FormattingUtil.formatNumbers(data.getInt("parallel"))
                    : "—",
                    FormattingUtil.formatNumbers(data.getInt("parallelCap"))));
            tooltip.add(line("steam", FormattingUtil.formatNumbers(data.getLong("steamTotal")),
                    FormattingUtil.formatNumbers(data.getLong("steamCap"))));
            tooltip.add(line("demand",
                    data.getLong("steamPerTick") > 0
                            ? FormattingUtil.formatNumbers(data.getLong("steamPerTick"))
                            : "0"));
            if (data.getLong("pendingTotal") > 0) {
                tooltip.add(line("pending", FormattingUtil.formatNumbers(data.getLong("pendingTotal")),
                        String.valueOf(data.getInt("pendingKinds"))));
            }
        }

        private static String statusKey(String statusId) {
            return switch (statusId) {
                case "invalid_structure" -> "gtceu.multiblock.invalid_structure";
                case "exhaust_obstructed" -> "gregsteamexpansion.multiblock.steam_exhaust_hatch_obstructed";
                case "insufficient_outputs" -> "gtceu.recipe_logic.insufficient_out";
                case "working_disabled" -> "gtceu.top.working_disabled";
                case "low_steam" -> "gtceu.multiblock.steam.low_steam";
                case "working" -> "gtceu.multiblock.large_miner.working";
                default -> "gtceu.multiblock.idling";
            };
        }

        private static Component line(String name, Object... arguments) {
            return Component.translatable("gregsteamexpansion.jade.steam_crusher." + name, arguments)
                    .withStyle(ChatFormatting.GRAY);
        }

        @Override
        public ResourceLocation getUid() {
            return UID;
        }
    }

    private enum AirIntakeProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
        INSTANCE;

        private static final ResourceLocation UID = GregSteamExpansion.id("steam_air_intake_hatch_info");
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
            tooltip.add(line("air",
                    FormattingUtil.formatNumbers(data.getInt("storedAmount")),
                    FormattingUtil.formatNumbers(data.getInt("capacity"))));
            int ticks = data.getInt("ticksUntilCollection");
            if (ticks > 0) {
                tooltip.add(line("next_collect", FormattingUtil.formatNumbers(ticks)));
            }
        }

        private static Component line(String name, Object... arguments) {
            return Component.translatable("gregsteamexpansion.jade.steam_air_intake_hatch." + name, arguments)
                    .withStyle(ChatFormatting.GRAY);
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

    /**
     * 普通焦炉控制器 Jade 数据协议 (coke-ovens.md 普通焦炉与焦炉仓 Jade 信息):
     * 结构状态 + 按优先级选出的主状态 + 进度百分比与预计剩余时间 + 流体输出罐
     * 实际内容与固定容量 + 全部阻塞原因; 不显示能源、蒸汽、燃料、温度信息。
     * 状态/进度/详情为 @DescSynced 字段由客户端直读; 流体罐内容不同步, 由服务端
     * 数据补充。
     */
    private enum CokeOvenProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
        INSTANCE;

        private static final ResourceLocation UID = GregSteamExpansion.id("coke_oven_info");
        private static final String DATA_KEY = "GregSteamExpansionCokeOven";

        @Override
        public void appendServerData(CompoundTag serverData, BlockAccessor accessor) {
            if (!(accessor.getBlockEntity() instanceof MetaMachineBlockEntity blockEntity) ||
                    !(blockEntity.getMetaMachine() instanceof GSECokeOvenMachine oven)) {
                return;
            }
            CompoundTag data = new CompoundTag();
            var fluid = oven.exportFluids.getStorages()[0].getFluid();
            data.putInt("fluidAmount", fluid.getAmount());
            data.putString("fluidId", fluid.isEmpty() ? ""
                    : net.minecraftforge.registries.ForgeRegistries.FLUIDS.getKey(fluid.getFluid()).toString());
            data.putInt("fluidCapacity", oven.exportFluids.getStorages()[0].getCapacity());
            serverData.put(DATA_KEY, data);
        }

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            if (!(accessor.getBlockEntity() instanceof MetaMachineBlockEntity blockEntity) ||
                    !(blockEntity.getMetaMachine() instanceof GSECokeOvenMachine oven)) {
                return;
            }

            tooltip.add(line("status", Component.translatable(statusKey(oven.getStatusId()))));
            for (Component detail : oven.getStatusDetails()) {
                tooltip.add(line("detail", detail.getString()));
            }
            if (oven.isFormed() && oven.getRecipeLogic().getMaxProgress() > 0) {
                int progress = oven.getRecipeLogic().getProgress();
                int duration = oven.getRecipeLogic().getMaxProgress();
                double percent = Math.round(progress * 1000.0 / duration) / 10.0;
                int remaining = oven.getRemainingTicks();
                if (remaining >= 0) {
                    tooltip.add(line("progress", percent + "%",
                            FormattingUtil.formatNumbers(remaining / 20.0) + "s"));
                }
            }
            String fluidId = "";
            int amount = 0;
            int capacity = 32000;
            CompoundTag serverData = accessor.getServerData();
            if (serverData.contains(DATA_KEY, Tag.TAG_COMPOUND)) {
                CompoundTag data = serverData.getCompound(DATA_KEY);
                fluidId = data.getString("fluidId");
                amount = data.getInt("fluidAmount");
                capacity = data.getInt("fluidCapacity");
            }
            String fluidName;
            if (!fluidId.isEmpty()) {
                var fluid = net.minecraftforge.registries.ForgeRegistries.FLUIDS.getValue(
                        new ResourceLocation(fluidId));
                fluidName = fluid == null ? fluidId
                        : fluid.getFluidType().getDescription().getString();
            } else {
                fluidName = Component.translatable("gregsteamexpansion.jade.coke_oven.empty").getString();
            }
            tooltip.add(line("fluid", fluidName,
                    FormattingUtil.formatNumbers(amount), FormattingUtil.formatNumbers(capacity)));
        }

        private static String statusKey(String statusId) {
            return switch (statusId) {
                case "invalid_structure" -> "gtceu.multiblock.invalid_structure";
                case "pending_output" -> "gregsteamexpansion.coke_oven.status.pending_output";
                case "working" -> "gtceu.multiblock.running";
                case "awaiting_reinput" -> "gregsteamexpansion.coke_oven.status.awaiting_reinput";
                case "input_invalid" -> "gregsteamexpansion.coke_oven.status.input_invalid";
                case "item_blocked" -> "gregsteamexpansion.coke_oven.status.item_output_blocked";
                case "fluid_blocked" -> "gregsteamexpansion.coke_oven.status.fluid_output_blocked";
                case "both_blocked" -> "gregsteamexpansion.coke_oven.status.both_output_blocked";
                case "ready" -> "gregsteamexpansion.coke_oven.status.ready";
                default -> "gtceu.multiblock.idling";
            };
        }

        private static Component line(String name, Object... arguments) {
            return Component.translatable("gregsteamexpansion.jade.coke_oven." + name, arguments)
                    .withStyle(ChatFormatting.GRAY);
        }

        @Override
        public ResourceLocation getUid() {
            return UID;
        }
    }

    /**
     * 可配置焦炉仓 Jade 数据协议 (coke-ovens.md): 始终显示当前模式; 连接状态区分
     * 已连接且结构有效 / 已归属但结构无效 / 未连接; 每种模式只显示有权访问的
     * 对应库存内容。
     */
    private enum CokeOvenHatchProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
        INSTANCE;

        private static final ResourceLocation UID = GregSteamExpansion.id("coke_oven_hatch_info");
        private static final String DATA_KEY = "GregSteamExpansionCokeOvenHatch";

        @Override
        public void appendServerData(CompoundTag serverData, BlockAccessor accessor) {
            if (!(accessor.getBlockEntity() instanceof MetaMachineBlockEntity blockEntity) ||
                    !(blockEntity.getMetaMachine() instanceof GSECokeOvenHatch hatch)) {
                return;
            }
            CompoundTag data = new CompoundTag();
            data.putString("itemSummary", hatch.getItemSummary());
            String fluidSummary = hatch.getFluidSummary();
            data.putString("fluidSummary", fluidSummary == null ? "" : fluidSummary);
            serverData.put(DATA_KEY, data);
        }

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            if (!(accessor.getBlockEntity() instanceof MetaMachineBlockEntity blockEntity) ||
                    !(blockEntity.getMetaMachine() instanceof GSECokeOvenHatch hatch)) {
                return;
            }
            // 模式与连接状态为 @DescSynced 字段, 客户端直读; 库存摘要走服务端数据。
            tooltip.add(line("mode", Component.translatable(
                    "gregsteamexpansion.coke_oven_hatch.mode." + hatch.getMode().getSerializedName())));
            String connection = "none";
            for (var controller : hatch.getControllers()) {
                if (controller instanceof GSECokeOvenMachine oven) {
                    connection = oven.isFormed() ? "formed" : "invalid";
                    break;
                }
            }
            tooltip.add(line("connection", Component.translatable(
                    "gregsteamexpansion.jade.coke_oven_hatch.connection." + connection)));
            if ("formed".equals(connection)) {
                CompoundTag serverData = accessor.getServerData();
                if (serverData.contains(DATA_KEY, Tag.TAG_COMPOUND)) {
                    CompoundTag data = serverData.getCompound(DATA_KEY);
                    tooltip.add(line("items", data.getString("itemSummary")));
                    String fluid = data.getString("fluidSummary");
                    if (!fluid.isEmpty()) {
                        tooltip.add(line("fluid", fluid));
                    }
                }
            }
        }

        private static Component line(String name, Object... arguments) {
            return Component.translatable("gregsteamexpansion.jade.coke_oven_hatch." + name, arguments)
                    .withStyle(ChatFormatting.GRAY);
        }

        @Override
        public ResourceLocation getUid() {
            return UID;
        }
    }

    /**
     * 大型焦炉控制器 Jade 数据协议: 全部状态字段 (主状态/诊断/批次) 均为
     * {@code @DescSynced}, 客户端直接读取机器实例显示, 不依赖服务端数据同步;
     * 不逐槽展开库存 (需要 GUI)。
     */
    private enum LargeCokeOvenProvider implements IBlockComponentProvider {
        INSTANCE;

        private static final ResourceLocation UID = GregSteamExpansion.id("large_coke_oven_info");

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            if (!(accessor.getBlockEntity() instanceof MetaMachineBlockEntity blockEntity) ||
                    !(blockEntity.getMetaMachine() instanceof LargeCokeOvenMachine oven)) {
                return;
            }
            String statusId = oven.getStatusId();
            tooltip.add(line("status", Component.translatable(statusKey(statusId))));
            var details = oven.getStatusDetails();
            if (!details.isEmpty()) {
                tooltip.add(line("detail", details.get(0).getString()));
            }
            var recipeId = oven.getOvenLogic().getBatchRecipeId();
            if (recipeId != null) {
                tooltip.add(line("recipe", recipeId.toString(),
                        oven.getOvenLogic().getBatchParallel() + "/" + LargeCokeOvenRecipeLogic.MAX_PARALLEL));
            }
            if ("working".equals(statusId)) {
                int progress = oven.getOvenLogic().getBatchProgress();
                int duration = oven.getOvenLogic().getBatchTotalDuration();
                double percent = duration == 0 ? 0 : Math.round(progress * 1000.0 / duration) / 10.0;
                int remaining = Math.max(0, duration - progress);
                tooltip.add(line("progress", percent + "%",
                        FormattingUtil.formatNumbers(remaining / 20.0) + "s"));
            } else if ("waiting_output".equals(statusId)) {
                tooltip.add(line("waiting", "100%"));
            }
        }

        private static String statusKey(String statusId) {
            return switch (statusId) {
                case "invalid_structure" -> "gtceu.multiblock.invalid_structure";
                case "working" -> "gtceu.multiblock.running";
                default -> "gregsteamexpansion.large_coke_oven.status." + statusId;
            };
        }

        private static Component line(String name, Object... arguments) {
            return Component.translatable("gregsteamexpansion.jade.large_coke_oven." + name, arguments)
                    .withStyle(ChatFormatting.GRAY);
        }

        @Override
        public ResourceLocation getUid() {
            return UID;
        }
    }

    /**
     * 大型焦炉仓 Jade 数据协议: 模式/朝向/连接状态由客户端直读 (@DescSynced
     * 字段); 共享库存摘要 (已用/总槽位数、流体名称与存量) 库存不同步到客户端,
     * 由服务端数据补充。
     */
    private enum LargeCokeOvenHatchProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
        INSTANCE;

        private static final ResourceLocation UID = GregSteamExpansion.id("large_coke_oven_hatch_info");
        private static final String DATA_KEY = "GregSteamExpansionLargeCokeOvenHatch";

        @Override
        public void appendServerData(CompoundTag serverData, BlockAccessor accessor) {
            if (!(accessor.getBlockEntity() instanceof MetaMachineBlockEntity blockEntity) ||
                    !(blockEntity.getMetaMachine() instanceof LargeCokeOvenHatchPartMachine hatch)) {
                return;
            }
            CompoundTag data = new CompoundTag();
            String slots = hatch.getSlotSummary();
            data.putString("slotSummary", slots == null ? "" : slots);
            String fluid = hatch.getFluidSummary();
            data.putString("fluidSummary", fluid == null ? "" : fluid);
            serverData.put(DATA_KEY, data);
        }

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            if (!(accessor.getBlockEntity() instanceof MetaMachineBlockEntity blockEntity) ||
                    !(blockEntity.getMetaMachine() instanceof LargeCokeOvenHatchPartMachine hatch)) {
                return;
            }
            tooltip.add(line("mode", Component.translatable("gregsteamexpansion.coke_oven_hatch.mode."
                    + hatch.getMode().getSerializedName())));
            Direction legal = hatch.getLegalFacing();
            tooltip.add(line("facing", Component.translatable(
                    "gregsteamexpansion.jade.large_coke_oven_hatch.direction."
                            + (legal == null ? hatch.getFrontFacing() : legal).getName())));
            if (hatch.getCoverContainer().hasCover(hatch.getFrontFacing())) {
                tooltip.add(line("covered"));
            }
            String connection = hatch.getConnectionState();
            tooltip.add(line("connection", Component.translatable(
                    "gregsteamexpansion.jade.coke_oven_hatch.connection." + connection)));
            if ("formed".equals(connection)) {
                CompoundTag serverData = accessor.getServerData();
                if (serverData.contains(DATA_KEY, Tag.TAG_COMPOUND)) {
                    CompoundTag data = serverData.getCompound(DATA_KEY);
                    String slots = data.getString("slotSummary");
                    if (!slots.isEmpty()) {
                        tooltip.add(line("slots", slots));
                    }
                    String fluid = data.getString("fluidSummary");
                    if (!fluid.isEmpty()) {
                        tooltip.add(line("fluid", fluid));
                    }
                }
            }
        }

        private static Component line(String name, Object... arguments) {
            return Component.translatable("gregsteamexpansion.jade.large_coke_oven_hatch." + name, arguments)
                    .withStyle(ChatFormatting.GRAY);
        }

        @Override
        public ResourceLocation getUid() {
            return UID;
        }
    }

    /**
     * 已归属焦炉砖探针 (coke-ovens.md): 只显示"属于大型焦炉/普通焦炉"及控制器
     * 方向; 所有权记录仍在但结构无效时额外显示"已归属, 结构无效"。焦炉砖无
     * 方块实体, 数据来自控制器 @DescSynced 占用盒的客户端缓存扫描。
     */
    private enum OwnedBrickProvider implements IBlockComponentProvider {
        INSTANCE;

        private static final ResourceLocation UID = GregSteamExpansion.id("coke_oven_brick_ownership");

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            var ownership = OwnedBrickClient.query(accessor.getLevel(), accessor.getPosition());
            if (ownership == null) return; // 未归属: 保持原有方块信息, 不新增字段
            tooltip.add(line("owned", Component.translatable(
                    "gregsteamexpansion.jade.coke_oven_brick.kind." + ownership.kind())));
            tooltip.add(line("controller", directionName(accessor.getPosition(), ownership.controller())));
            if (!ownership.structureValid()) {
                tooltip.add(line("invalid"));
            }
        }

        private static Component directionName(BlockPos from, BlockPos to) {
            var rel = to.subtract(from);
            Direction best = Direction.NORTH;
            long bestDist = Long.MIN_VALUE;
            for (Direction dir : Direction.values()) {
                long dot = (long) rel.getX() * dir.getStepX() + (long) rel.getY() * dir.getStepY() +
                        (long) rel.getZ() * dir.getStepZ();
                if (dot > bestDist) {
                    bestDist = dot;
                    best = dir;
                }
            }
            return Component.translatable("gregsteamexpansion.jade.coke_oven_brick.direction." + best.getName());
        }

        private static Component line(String name, Object... arguments) {
            return Component.translatable("gregsteamexpansion.jade.coke_oven_brick." + name, arguments)
                    .withStyle(ChatFormatting.GRAY);
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
