package com.hoshino.gregsteamexpansion.client.cokeoven;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;

import java.util.List;

/**
 * 焦炉家族两级物品提示的统一构建器: 由 GTCEu 的
 * {@code MachineDefinition#setTooltipBuilder} 在物品 hover 时直接调用
 * (MetaMachineItem#appendHoverText), 不依赖事件转发, 保证显示。默认摘要只保留
 * 定位与最重要操作入口, 按住 Shift 展开完整限制与危险说明。
 */
public final class CokeOvenTooltipBuilder {

    private CokeOvenTooltipBuilder() {}

    /**
     * 按 {@code <prefix>.summary.N} / {@code <prefix>.details.N} 追加两级提示。
     *
     * @param prefix 例如 {@code gregsteamexpansion.machine.large_coke_oven.tooltip}
     */
    public static void append(String prefix, List<Component> lines) {
        if (Screen.hasShiftDown()) {
            addNumbered(lines, prefix + ".details");
        } else {
            addNumbered(lines, prefix + ".summary");
            lines.add(Component.translatable("gregsteamexpansion.tooltip.shift_hint")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    private static void addNumbered(List<Component> lines, String baseKey) {
        for (int index = 0; index <= 32; index++) {
            String key = baseKey + "." + index;
            if (!I18n.exists(key)) break;
            lines.add(Component.translatable(key).withStyle(ChatFormatting.GRAY));
        }
    }
}
