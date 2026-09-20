package com.hoshino.gregsteamexpansion.integration.jade;

import com.gregtechceu.gtceu.utils.FormattingUtil;
import com.hoshino.gregsteamexpansion.machine.multiblock.crusher.AbstractSteamCrusherMachine;
import com.hoshino.gregsteamexpansion.machine.multiblock.processor.AbstractSteamProcessorMachine;

import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import snownee.jade.api.ITooltip;
import snownee.jade.api.ui.BoxStyle;

/** Shared wire snapshot and GTCEu-style rendering used by steam-machine providers. */
final class SteamMachineJadeSupport {
    static final String CRUSHER_TOOLTIP_PREFIX = "gregsteamexpansion.jade.steam_crusher.";
    static final String PROCESSOR_TOOLTIP_PREFIX = "gregsteamexpansion.jade.steam_processor.";
    static final String VOID_PRODUCER_TOOLTIP_PREFIX = "gregsteamexpansion.jade.void_producer.";

    private static final int GT_PROGRESS_COLOR = 0xFF4CBB17;
    private static final int GT_DISABLED_COLOR = 0xFFBB1C28;
    private static final int GT_STORAGE_COLOR = 0xFFEEE600;
    private static final int GT_BORDER_COLOR = 0xFF555555;

    private SteamMachineJadeSupport() {}

    record SteamMachineSnapshot(
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

        static SteamMachineSnapshot from(AbstractSteamCrusherMachine machine) {
            return new SteamMachineSnapshot(
                    machine.getStatusId(), !machine.getBatchRecipeId().isEmpty(), machine.getBatchInputDisplay(),
                    machine.getBatchProgress(), machine.getBatchDuration(), machine.getBatchParallel(),
                    machine.maximumParallel(), machine.getSteamTotalStored(), machine.getSteamTotalCapacity(),
                    machine.getBatchSteamPerTick(), machine.getSteamInputLimitPerTick(),
                    machine.isConsumingSteam(), machine.getPendingTotalCount(), machine.getPendingKinds());
        }

        static SteamMachineSnapshot from(AbstractSteamProcessorMachine machine) {
            return new SteamMachineSnapshot(
                    machine.getStatusId(), !machine.getBatchRecipeId().isEmpty(), machine.getBatchInputDisplay(),
                    machine.getBatchProgress(), machine.getBatchDuration(), machine.getBatchParallel(),
                    machine.maximumParallel(), machine.getSteamTotalStored(), machine.getSteamTotalCapacity(),
                    machine.getBatchSteamPerTick(), machine.getSteamInputLimitPerTick(),
                    machine.isConsumingSteam(), machine.getPendingTotalCount(), machine.getPendingKinds());
        }

        CompoundTag toTag() {
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

    static void appendSteamMachineTooltip(ITooltip tooltip, CompoundTag data,
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

    static String steamMachineStatusKey(String statusId) {
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

    static Component steamMachineLine(String translationPrefix, String name, Object... arguments) {
        return steamMachineText(translationPrefix, name, arguments).withStyle(ChatFormatting.GRAY);
    }

    private static MutableComponent steamMachineText(String translationPrefix, String name, Object... arguments) {
        return Component.translatable(translationPrefix + name, arguments);
    }

    static void addWorkProgressBar(ITooltip tooltip, long value, long maximum, boolean workingEnabled) {
        Component text = maximum < 20
                ? Component.translatable("gtceu.jade.progress_tick", value, maximum)
                : Component.translatable("gtceu.jade.progress_sec",
                        Math.round(value / 20.0F), Math.round(maximum / 20.0F));
        addBar(tooltip, value, maximum, text, workingEnabled ? GT_PROGRESS_COLOR : GT_DISABLED_COLOR);
    }

    static void addStorageBar(ITooltip tooltip, long value, long maximum) {
        addBar(tooltip, value, maximum, fluidStoredText(value, maximum), GT_STORAGE_COLOR);
    }

    static void addStorageBar(ITooltip tooltip, long value, long maximum, ResourceLocation uid) {
        addBar(tooltip, value, maximum, fluidStoredText(value, maximum), GT_STORAGE_COLOR, uid);
    }

    static void addRatioBar(ITooltip tooltip, long value, long maximum, Component text) {
        addBar(tooltip, value, maximum, text, GT_PROGRESS_COLOR);
    }

    static Component fluidStoredText(long value, long maximum) {
        return Component.translatable("gregsteamexpansion.jade.bar.fluid_stored",
                FormattingUtil.formatNumbers(value), FormattingUtil.formatNumbers(maximum));
    }

    static Component parallelText(long value, long maximum) {
        return Component.translatable("gregsteamexpansion.jade.bar.parallel",
                FormattingUtil.formatNumbers(value), FormattingUtil.formatNumbers(maximum));
    }

    private static void addBar(ITooltip tooltip, long value, long maximum, Component text, int color) {
        addBar(tooltip, value, maximum, text, color, null);
    }

    private static void addBar(ITooltip tooltip, long value, long maximum, Component text, int color,
                               ResourceLocation uid) {
        if (maximum <= 0) return;
        float ratio = (float) Math.max(0.0D, Math.min(1.0D, (double) value / maximum));
        var elements = tooltip.getElementHelper();
        if (elements == null) {
            if (uid == null) tooltip.add(text);
            else tooltip.add(text, uid);
            return;
        }
        var bar = elements.progress(ratio, text,
                elements.progressStyle().color(color, color).textColor(-1),
                Util.make(BoxStyle.DEFAULT, style -> style.borderColor = GT_BORDER_COLOR), true);
        if (uid != null) bar.tag(uid);
        tooltip.add(bar);
    }
}
