package com.hoshino.gregsteamexpansion.integration.jade;

import com.gregtechceu.gtceu.api.block.MetaMachineBlock;
import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.utils.FormattingUtil;
import com.hoshino.gregsteamexpansion.GregSteamExpansion;
import com.hoshino.gregsteamexpansion.client.StructureErrorHighlight;
import com.hoshino.gregsteamexpansion.client.cokeoven.OwnedBrickClient;
import com.hoshino.gregsteamexpansion.machine.multiblock.LargeHeatStorageSteamFurnaceMachine;
import com.hoshino.gregsteamexpansion.machine.multiblock.LargeSteamTankMachine;
import com.hoshino.gregsteamexpansion.machine.multiblock.cokeoven.GSECokeOvenMachine;
import com.hoshino.gregsteamexpansion.machine.multiblock.crusher.AbstractSteamCrusherMachine;
import com.hoshino.gregsteamexpansion.machine.multiblock.processor.AbstractSteamProcessorMachine;
import com.hoshino.gregsteamexpansion.machine.multiblock.voidproducer.AbstractSteamVoidMachine;
import com.hoshino.gregsteamexpansion.machine.multiblock.largecokeoven.LargeCokeOvenMachine;
import com.hoshino.gregsteamexpansion.machine.multiblock.largecokeoven.LargeCokeOvenRecipeLogic;
import com.hoshino.gregsteamexpansion.machine.multiblock.part.GSECokeOvenHatch;
import com.hoshino.gregsteamexpansion.machine.multiblock.part.LargeCokeOvenHatchPartMachine;
import com.hoshino.gregsteamexpansion.machine.multiblock.part.SteamAirIntakeHatchPartMachine;
import com.hoshino.gregsteamexpansion.machine.multiblock.part.SteamTankValvePartMachine;
import com.hoshino.gregsteamexpansion.machine.steam.MixedFuelBoilerMachine;
import com.hoshino.gregsteamexpansion.structure.StructureDiagnostics;
import com.hoshino.gregsteamexpansion.structure.StructureProblem;
import com.hoshino.gregsteamexpansion.structure.StructureText;

import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import net.minecraftforge.registries.ForgeRegistries;

import snownee.jade.api.Accessor;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.Identifiers;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.ui.BoxStyle;

import java.util.List;

@WailaPlugin
public final class GSEJadePlugin implements IWailaPlugin {
    private static final String CRUSHER_TOOLTIP_PREFIX = "gregsteamexpansion.jade.steam_crusher.";
    private static final String PROCESSOR_TOOLTIP_PREFIX = "gregsteamexpansion.jade.steam_processor.";
    private static final String VOID_PRODUCER_TOOLTIP_PREFIX = "gregsteamexpansion.jade.void_producer.";
    private static final int GT_PROGRESS_COLOR = 0xFF4CBB17;
    private static final int GT_DISABLED_COLOR = 0xFFBB1C28;
    private static final int GT_STORAGE_COLOR = 0xFFEEE600;
    private static final int GT_BORDER_COLOR = 0xFF555555;
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
    private static final ResourceLocation AIR_INTAKE_FLUID_SUMMARY =
            GregSteamExpansion.id("steam_air_intake_fluid_summary");
    private static final ResourceLocation COKE_OVEN_FLUID_SUMMARY =
            GregSteamExpansion.id("coke_oven_fluid_summary");
    private static final ResourceLocation COKE_OVEN_HATCH_ITEM_SUMMARY =
            GregSteamExpansion.id("coke_oven_hatch_item_summary");
    private static final ResourceLocation COKE_OVEN_HATCH_FLUID_SUMMARY =
            GregSteamExpansion.id("coke_oven_hatch_fluid_summary");
    private static final ResourceLocation LARGE_COKE_OVEN_HATCH_ITEM_SUMMARY =
            GregSteamExpansion.id("large_coke_oven_hatch_item_summary");
    private static final ResourceLocation LARGE_COKE_OVEN_HATCH_FLUID_SUMMARY =
            GregSteamExpansion.id("large_coke_oven_hatch_fluid_summary");

    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(MixedFuelBoilerProvider.INSTANCE, MetaMachineBlockEntity.class);
        registration.registerBlockDataProvider(FurnaceProvider.INSTANCE, MetaMachineBlockEntity.class);
        registration.registerBlockDataProvider(AirIntakeProvider.INSTANCE, MetaMachineBlockEntity.class);
        registration.registerBlockDataProvider(CrusherProvider.INSTANCE, MetaMachineBlockEntity.class);
        registration.registerBlockDataProvider(ProcessorProvider.INSTANCE, MetaMachineBlockEntity.class);
        registration.registerBlockDataProvider(VoidProducerProvider.INSTANCE, MetaMachineBlockEntity.class);
        registration.registerBlockDataProvider(CokeOvenProvider.INSTANCE, MetaMachineBlockEntity.class);
        registration.registerBlockDataProvider(CokeOvenHatchProvider.INSTANCE, MetaMachineBlockEntity.class);
        registration.registerBlockDataProvider(LargeCokeOvenHatchProvider.INSTANCE, MetaMachineBlockEntity.class);
        registration.registerBlockDataProvider(StructureDiagnosticsProvider.INSTANCE, MetaMachineBlockEntity.class);
        registration.registerBlockDataProvider(SteamTankProvider.INSTANCE, MetaMachineBlockEntity.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(MixedFuelBoilerProvider.INSTANCE, MetaMachineBlock.class);
        registration.registerBlockComponent(FurnaceProvider.INSTANCE, MetaMachineBlock.class);
        registration.registerBlockComponent(AirIntakeProvider.INSTANCE, MetaMachineBlock.class);
        registration.registerBlockComponent(CrusherProvider.INSTANCE, MetaMachineBlock.class);
        registration.registerBlockComponent(ProcessorProvider.INSTANCE, MetaMachineBlock.class);
        registration.registerBlockComponent(VoidProducerProvider.INSTANCE, MetaMachineBlock.class);
        registration.registerBlockComponent(CokeOvenProvider.INSTANCE, MetaMachineBlock.class);
        registration.registerBlockComponent(CokeOvenHatchProvider.INSTANCE, MetaMachineBlock.class);
        registration.registerBlockComponent(LargeCokeOvenProvider.INSTANCE, MetaMachineBlock.class);
        registration.registerBlockComponent(LargeCokeOvenHatchProvider.INSTANCE, MetaMachineBlock.class);
        registration.registerBlockComponent(StructureDiagnosticsProvider.INSTANCE, MetaMachineBlock.class);
        registration.registerBlockComponent(SteamTankProvider.INSTANCE, MetaMachineBlock.class);
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
            } else if (machine instanceof SteamTankValvePartMachine &&
                    contributed(tooltip, SteamTankProvider.UID) &&
                    contributed(tooltip, Identifiers.UNIVERSAL_FLUID_STORAGE)) {
                tooltip.remove(Identifiers.UNIVERSAL_FLUID_STORAGE);
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
    private enum ProcessorProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
        INSTANCE;

        private static final ResourceLocation UID = GregSteamExpansion.id("steam_compressor_info");
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

    private record SteamMachineSnapshot(
            String statusId,
            boolean hasBatch,
            ItemStack inputItem,
            int progress,
            int duration,
            int parallel,
            int parallelCap,
            long steamTotal,
            long steamCapacity,
            long steamPerTick,
            long steamInputLimit,
            boolean consuming,
            long pendingTotal,
            int pendingKinds) {

        private static SteamMachineSnapshot from(AbstractSteamCrusherMachine machine) {
            return new SteamMachineSnapshot(
                    machine.getStatusId(), !machine.getBatchRecipeId().isEmpty(), machine.getBatchInputDisplay(),
                    machine.getBatchProgress(), machine.getBatchDuration(), machine.getBatchParallel(),
                    machine.maximumParallel(), machine.getSteamTotalStored(), machine.getSteamTotalCapacity(),
                    machine.getBatchSteamPerTick(), machine.getSteamInputLimitPerTick(),
                    machine.isConsumingSteam(), machine.getPendingTotalCount(),
                    machine.getPendingKinds());
        }

        private static SteamMachineSnapshot from(AbstractSteamProcessorMachine machine) {
            return new SteamMachineSnapshot(
                    machine.getStatusId(), !machine.getBatchRecipeId().isEmpty(), machine.getBatchInputDisplay(),
                    machine.getBatchProgress(), machine.getBatchDuration(), machine.getBatchParallel(),
                    machine.maximumParallel(), machine.getSteamTotalStored(), machine.getSteamTotalCapacity(),
                    machine.getBatchSteamPerTick(), machine.getSteamInputLimitPerTick(),
                    machine.isConsumingSteam(), machine.getPendingTotalCount(),
                    machine.getPendingKinds());
        }

        private CompoundTag toTag() {
            CompoundTag data = new CompoundTag();
            data.putString("statusId", statusId);
            data.putBoolean("hasBatch", hasBatch);
            data.put("inputItem", inputItem.save(new CompoundTag()));
            data.putInt("progress", progress);
            data.putInt("duration", duration);
            data.putInt("parallel", parallel);
            data.putInt("parallelCap", parallelCap);
            data.putLong("steamTotal", steamTotal);
            data.putLong("steamCap", steamCapacity);
            data.putLong("steamPerTick", steamPerTick);
            data.putLong("steamInputLimit", steamInputLimit);
            data.putBoolean("consuming", consuming);
            data.putLong("pendingTotal", pendingTotal);
            data.putInt("pendingKinds", pendingKinds);
            return data;
        }
    }

    private static void appendSteamMachineTooltip(ITooltip tooltip, CompoundTag data,
                                                   String translationPrefix, String statusKey) {
        tooltip.add(steamMachineLine(translationPrefix, "status", Component.translatable(statusKey)));
        if (data.getBoolean("hasBatch")) {
            ItemStack inputItem = ItemStack.of(data.getCompound("inputItem"));
            if (!inputItem.isEmpty()) {
                tooltip.add(steamMachineLine(translationPrefix, "recipe", inputItem.getHoverName()));
            }
            addWorkProgressBar(tooltip, data.getInt("progress"), data.getInt("duration"),
                    !"working_disabled".equals(data.getString("statusId")));
        }
        addRatioBar(tooltip, data.getInt("parallel"), data.getInt("parallelCap"),
                parallelText(data.getInt("parallel"), data.getInt("parallelCap")));
        addStorageBar(tooltip, data.getLong("steamTotal"), data.getLong("steamCap"));
        addRatioBar(tooltip, data.getLong("steamPerTick"), data.getLong("steamInputLimit"),
                Component.translatable("gtceu.jade.fluid_use",
                        FormattingUtil.formatNumbers(data.getLong("steamPerTick"))));
        if (data.getLong("pendingTotal") > 0) {
            tooltip.add(steamMachineLine(translationPrefix, "pending",
                    FormattingUtil.formatNumbers(data.getLong("pendingTotal")),
                    String.valueOf(data.getInt("pendingKinds"))));
        }
        if (data.contains("pendingFluidTotal") && data.getLong("pendingFluidTotal") > 0) {
            tooltip.add(steamMachineLine(translationPrefix, "pending_fluid",
                    FormattingUtil.formatNumbers(data.getLong("pendingFluidTotal")),
                    String.valueOf(data.getInt("pendingFluidKinds"))));
        }
    }

    private static String steamMachineStatusKey(String statusId) {
        return switch (statusId) {
            case "invalid_structure" -> "gtceu.multiblock.invalid_structure";
            case "exhaust_obstructed" -> "gregsteamexpansion.multiblock.steam_exhaust_hatch_obstructed";
            case "insufficient_outputs" -> "gtceu.recipe_logic.insufficient_out";
            case "working_disabled" -> "gtceu.top.working_disabled";
            case "auxiliary_shortfall" -> "gregsteamexpansion.multiblock.auxiliary_shortfall";
            case "disabled_by_config" -> "gregsteamexpansion.machine.void_producer.ui.disabled_by_config";
            case "low_steam" -> "gtceu.multiblock.steam.low_steam";
            case "working" -> "gtceu.multiblock.large_miner.working";
            default -> "gtceu.multiblock.idling";
        };
    }

    private static Component steamMachineLine(String translationPrefix, String name, Object... arguments) {
        return steamMachineText(translationPrefix, name, arguments).withStyle(ChatFormatting.GRAY);
    }

    private static MutableComponent steamMachineText(String translationPrefix, String name, Object... arguments) {
        return Component.translatable(translationPrefix + name, arguments);
    }

    /**
     * Mirrors GTCEu's WorkableBlockProvider: recipe/cycle progress uses the
     * upstream tick/second text and switches to the upstream red fill while
     * working is disabled.
     */
    private static void addWorkProgressBar(ITooltip tooltip, long value, long maximum,
                                           boolean workingEnabled) {
        Component text = maximum < 20
                ? Component.translatable("gtceu.jade.progress_tick", value, maximum)
                : Component.translatable("gtceu.jade.progress_sec",
                        Math.round(value / 20.0F), Math.round(maximum / 20.0F));
        addBar(tooltip, value, maximum, text, workingEnabled ? GT_PROGRESS_COLOR : GT_DISABLED_COLOR);
    }

    /** Mirrors GTCEu's yellow ElectricContainerBlockProvider capacity bar. */
    private static void addStorageBar(ITooltip tooltip, long value, long maximum) {
        addBar(tooltip, value, maximum, fluidStoredText(value, maximum), GT_STORAGE_COLOR);
    }

    private static void addStorageBar(ITooltip tooltip, long value, long maximum,
                                      ResourceLocation uid) {
        addBar(tooltip, value, maximum, fluidStoredText(value, maximum), GT_STORAGE_COLOR, uid);
    }

    private static void addRatioBar(ITooltip tooltip, long value, long maximum, Component text) {
        addBar(tooltip, value, maximum, text, GT_PROGRESS_COLOR);
    }

    private static Component fluidStoredText(long value, long maximum) {
        return Component.translatable("gregsteamexpansion.jade.bar.fluid_stored",
                FormattingUtil.formatNumbers(value), FormattingUtil.formatNumbers(maximum));
    }

    private static Component parallelText(long value, long maximum) {
        return Component.translatable("gregsteamexpansion.jade.bar.parallel",
                FormattingUtil.formatNumbers(value), FormattingUtil.formatNumbers(maximum));
    }

    private static void addBar(ITooltip tooltip, long value, long maximum, Component text, int color) {
        addBar(tooltip, value, maximum, text, color, null);
    }

    private static void addBar(ITooltip tooltip, long value, long maximum, Component text, int color,
                               ResourceLocation uid) {
        // Match the upstream providers: a ratio without a positive maximum is
        // not a meaningful bar and is omitted instead of drawing an empty row.
        if (maximum <= 0) return;
        float ratio = (float) Math.max(0.0D, Math.min(1.0D, (double) value / maximum));
        var elements = tooltip.getElementHelper();
        // Dedicated-server GameTests use a minimal tooltip proxy and cannot
        // load Jade's client rendering types. Real Jade tooltips always expose
        // an element helper; retaining the text is the safest fallback.
        if (elements == null) {
            if (uid == null) {
                tooltip.add(text);
            } else {
                tooltip.add(text, uid);
            }
            return;
        }
        var bar = elements.progress(ratio, text,
                elements.progressStyle().color(color, color).textColor(-1),
                Util.make(BoxStyle.DEFAULT, style -> style.borderColor = GT_BORDER_COLOR), true);
        if (uid != null) {
            bar.tag(uid);
        }
        tooltip.add(bar);
    }

    /**
     * 虚空产物机 Jade 数据协议: controller UI keeps compact text rows while
     * values with a meaningful ratio are rendered as native GTCEu-style bars
     * in the hover tooltip.
     */
    private enum VoidProducerProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
        INSTANCE;

        private static final ResourceLocation UID = GregSteamExpansion.id("void_producer_info");
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

    /**
     * 结构诊断 Jade 数据协议 (structure-diagnostics.md 通道 T1 / 决策 P5)。
     *
     * <p>一处实现覆盖全部多方块机器: Jade 按方块类型注册, 不像既有 8 个 provider
     * 那样逐台机器写。未成型时显示**首个问题**的类别 / 坐标 / 期望方块候选。
     *
     * <p>分工约定 (P5): 既有 provider 保留它们的状态行 ("结构未成型"), 本 provider
     * 只补原因, 不重复状态。
     *
     * <p>为什么必须走服务端数据通道: GTCEu 的结构检查只在服务端异步线程执行
     * ({@code MultiblockControllerMachine#asyncCheckPattern}), 客户端的
     * {@code MultiblockState} 永远是初始态, 直接读拿不到任何原因。诊断以
     * ItemStack/坐标的形式序列化, 方块名由客户端按本地语言渲染 (设计文档 R6)。
     */
    private enum SteamTankProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
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
            } else {
                return;
            }

            CompoundTag data = new CompoundTag();
            data.putBoolean("valve", valve);
            data.putBoolean("outputMode", outputMode);
            data.putBoolean("formed", tank != null && tank.isFormed());
            if (tank != null) {
                data.putInt("amount", tank.getStoredAmount());
                data.putInt("capacity", tank.getFormedCapacity());
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
                        Component.translatable(data.getBoolean("outputMode") ?
                                "gregsteamexpansion.jade.large_steam_tank.output" :
                                "gregsteamexpansion.jade.large_steam_tank.input")));
            }
            if (!data.getBoolean("formed")) {
                tooltip.add(Component.translatable("gregsteamexpansion.jade.large_steam_tank.unformed"));
                return;
            }
            tooltip.add(Component.translatable("gregsteamexpansion.jade.large_steam_tank.storage",
                    FormattingUtil.formatNumbers(data.getInt("amount")),
                    FormattingUtil.formatNumbers(data.getInt("capacity"))));
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

    private enum StructureDiagnosticsProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
        INSTANCE;

        private static final ResourceLocation UID = GregSteamExpansion.id("structure_diagnostics");
        private static final String DATA_KEY = "GregSteamExpansionStructureDiagnostics";
        private static final String FORMED_KEY = "GregSteamExpansionStructureFormed";

        @Override
        public void appendServerData(CompoundTag serverData, BlockAccessor accessor) {
            if (!(accessor.getBlockEntity() instanceof MetaMachineBlockEntity blockEntity) ||
                    !(blockEntity.getMetaMachine() instanceof IMultiController controller)) {
                return;
            }
            serverData.putBoolean(FORMED_KEY, controller.isFormed());
            if (controller.isFormed()) {
                return; // 成型后引擎已清空 error, 不再发数据
            }
            StructureDiagnostics.describe(controller)
                    .ifPresent(problem -> serverData.put(DATA_KEY, problem.toTag()));
        }

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            CompoundTag serverData = accessor.getServerData();
            if (serverData.getBoolean(FORMED_KEY)) {
                StructureErrorHighlight.clearForController(accessor.getPosition());
                return;
            }
            if (!serverData.contains(DATA_KEY, Tag.TAG_COMPOUND)) {
                return; // 未成型但没有已知原因 (尚未跑过校验, 或结构其实有效)
            }
            StructureProblem problem = StructureProblem.fromTag(serverData.getCompound(DATA_KEY));
            if (problem == null) {
                return;
            }
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
            // P6: 引擎只记录首个失败点, 明写以免玩家误以为修好这一处就完事。
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
