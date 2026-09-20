package com.hoshino.gregsteamexpansion.integration.jade;

import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.utils.FormattingUtil;
import com.hoshino.gregsteamexpansion.GregSteamExpansion;
import com.hoshino.gregsteamexpansion.client.cokeoven.OwnedBrickClient;
import com.hoshino.gregsteamexpansion.machine.multiblock.cokeoven.GSECokeOvenMachine;
import com.hoshino.gregsteamexpansion.machine.multiblock.largecokeoven.LargeCokeOvenMachine;
import com.hoshino.gregsteamexpansion.machine.multiblock.largecokeoven.LargeCokeOvenRecipeLogic;
import com.hoshino.gregsteamexpansion.machine.multiblock.part.GSECokeOvenHatch;
import com.hoshino.gregsteamexpansion.machine.multiblock.part.LargeCokeOvenHatchPartMachine;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import net.minecraftforge.registries.ForgeRegistries;

import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

import static com.hoshino.gregsteamexpansion.integration.jade.SteamMachineJadeSupport.*;

/** Coke-oven controllers, hatches and brick-ownership providers. */
final class CokeOvenJadeProviders {
    static final ResourceLocation COKE_OVEN_FLUID_SUMMARY = GregSteamExpansion.id("coke_oven_fluid_summary");
    static final ResourceLocation COKE_OVEN_HATCH_ITEM_SUMMARY = GregSteamExpansion.id("coke_oven_hatch_item_summary");
    static final ResourceLocation COKE_OVEN_HATCH_FLUID_SUMMARY = GregSteamExpansion.id("coke_oven_hatch_fluid_summary");
    static final ResourceLocation LARGE_COKE_OVEN_HATCH_ITEM_SUMMARY = GregSteamExpansion.id("large_coke_oven_hatch_item_summary");
    static final ResourceLocation LARGE_COKE_OVEN_HATCH_FLUID_SUMMARY = GregSteamExpansion.id("large_coke_oven_hatch_fluid_summary");

    private CokeOvenJadeProviders() {}

    enum CokeOvenProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
        INSTANCE;

        static final ResourceLocation UID = GregSteamExpansion.id("coke_oven_info");
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
                    : ForgeRegistries.FLUIDS.getKey(fluid.getFluid()).toString());
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
                addWorkProgressBar(tooltip, progress, duration, true);
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
                ResourceLocation fluidKey = ResourceLocation.tryParse(fluidId);
                var fluid = fluidKey == null ? null
                        : ForgeRegistries.FLUIDS.getValue(fluidKey);
                fluidName = fluid == null ? fluidId
                        : fluid.getFluidType().getDescription().getString();
            } else {
                fluidName = Component.translatable("gregsteamexpansion.jade.coke_oven.empty").getString();
            }
            tooltip.add(line("fluid", fluidName,
                    FormattingUtil.formatNumbers(amount), FormattingUtil.formatNumbers(capacity)),
                    COKE_OVEN_FLUID_SUMMARY);
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
    enum CokeOvenHatchProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
        INSTANCE;

        static final ResourceLocation UID = GregSteamExpansion.id("coke_oven_hatch_info");
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
                    tooltip.add(line("items", data.getString("itemSummary")), COKE_OVEN_HATCH_ITEM_SUMMARY);
                    String fluid = data.getString("fluidSummary");
                    if (!fluid.isEmpty()) {
                        tooltip.add(line("fluid", fluid), COKE_OVEN_HATCH_FLUID_SUMMARY);
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
    enum LargeCokeOvenProvider implements IBlockComponentProvider {
        INSTANCE;

        static final ResourceLocation UID = GregSteamExpansion.id("large_coke_oven_info");

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
            if (oven.getOvenLogic().hasActiveBatch()) {
                addRatioBar(tooltip, oven.getOvenLogic().getBatchParallel(),
                        LargeCokeOvenRecipeLogic.MAX_PARALLEL,
                        parallelText(oven.getOvenLogic().getBatchParallel(), LargeCokeOvenRecipeLogic.MAX_PARALLEL));
            }
            if ("working".equals(statusId)) {
                int progress = oven.getOvenLogic().getBatchProgress();
                int duration = oven.getOvenLogic().getBatchTotalDuration();
                addWorkProgressBar(tooltip, progress, duration, true);
            } else if ("waiting_output".equals(statusId)) {
                int duration = Math.max(1, oven.getOvenLogic().getBatchTotalDuration());
                addWorkProgressBar(tooltip, duration, duration, true);
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
            return text(name, arguments).withStyle(ChatFormatting.GRAY);
        }

        private static MutableComponent text(String name, Object... arguments) {
            return Component.translatable("gregsteamexpansion.jade.large_coke_oven." + name, arguments);
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
    enum LargeCokeOvenHatchProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
        INSTANCE;

        static final ResourceLocation UID = GregSteamExpansion.id("large_coke_oven_hatch_info");
        private static final String DATA_KEY = "GregSteamExpansionLargeCokeOvenHatch";

        @Override
        public void appendServerData(CompoundTag serverData, BlockAccessor accessor) {
            if (!(accessor.getBlockEntity() instanceof MetaMachineBlockEntity blockEntity) ||
                    !(blockEntity.getMetaMachine() instanceof LargeCokeOvenHatchPartMachine hatch)) {
                return;
            }
            CompoundTag data = new CompoundTag();
            data.putString("mode", hatch.getMode().getSerializedName());
            Direction legalFacing = hatch.getLegalFacing();
            data.putString("facing", (legalFacing == null ? hatch.getFrontFacing() : legalFacing).getName());
            data.putBoolean("covered", hatch.getCoverContainer().hasCover(hatch.getFrontFacing()));
            data.putString("connection", hatch.getConnectionState());
            String slots = hatch.getSlotSummary();
            data.putString("slotSummary", slots == null ? "" : slots);
            var fluid = hatch.getFluidForDisplay();
            if (fluid != null) {
                data.putString("fluidName", fluid.isEmpty() ? "" : Component.Serializer.toJson(fluid.getDisplayName()));
                data.putLong("fluidAmount", fluid.getAmount());
                data.putLong("fluidCapacity", LargeCokeOvenMachine.FLUID_TANK_CAPACITY_MB);
            }
            serverData.put(DATA_KEY, data);
        }

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            if (!(accessor.getBlockEntity() instanceof MetaMachineBlockEntity blockEntity) ||
                    !(blockEntity.getMetaMachine() instanceof LargeCokeOvenHatchPartMachine hatch)) {
                return;
            }
            CompoundTag serverData = accessor.getServerData();
            CompoundTag data = serverData.contains(DATA_KEY, Tag.TAG_COMPOUND)
                    ? serverData.getCompound(DATA_KEY)
                    : new CompoundTag();

            String mode = data.contains("mode", Tag.TAG_STRING)
                    ? data.getString("mode")
                    : hatch.getMode().getSerializedName();
            tooltip.add(line("mode", Component.translatable(
                    "gregsteamexpansion.large_coke_oven_hatch.mode." + mode)));

            String facing = data.contains("facing", Tag.TAG_STRING)
                    ? data.getString("facing")
                    : hatch.getFrontFacing().getName();
            tooltip.add(line("facing", Component.translatable(
                    "gregsteamexpansion.jade.large_coke_oven_hatch.direction." + facing)));
            if (data.getBoolean("covered")) {
                tooltip.add(line("covered"));
            }

            String connection = data.contains("connection", Tag.TAG_STRING)
                    ? data.getString("connection")
                    : hatch.getConnectionState();
            tooltip.add(line("connection", Component.translatable(
                    "gregsteamexpansion.jade.coke_oven_hatch.connection." + connection)));
            if ("formed".equals(connection)) {
                String slots = data.getString("slotSummary");
                if (!slots.isEmpty()) {
                    tooltip.add(line("slots", slots), LARGE_COKE_OVEN_HATCH_ITEM_SUMMARY);
                }
                if (data.contains("fluidCapacity", Tag.TAG_LONG)) {
                    Component fluidName = Component.translatable(
                            "gregsteamexpansion.jade.large_coke_oven_hatch.empty");
                    String fluidJson = data.getString("fluidName");
                    if (!fluidJson.isEmpty()) {
                        Component parsedName = Component.Serializer.fromJson(fluidJson);
                        if (parsedName != null) fluidName = parsedName;
                    }
                    tooltip.add(line("fluid", fluidName,
                            FormattingUtil.formatNumbers(data.getLong("fluidAmount")),
                            FormattingUtil.formatNumbers(data.getLong("fluidCapacity"))),
                            LARGE_COKE_OVEN_HATCH_FLUID_SUMMARY);
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
    enum OwnedBrickProvider implements IBlockComponentProvider {
        INSTANCE;

        static final ResourceLocation UID = GregSteamExpansion.id("coke_oven_brick_ownership");

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

}
