package com.hoshino.gregsteamexpansion.client.cokeoven;

import com.hoshino.gregsteamexpansion.GregSteamExpansion;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;

/**
 * 普通焦炉控制器与焦炉仓的两级物品提示 (coke-ovens.md 物品提示):
 * 默认摘要只保留定位与最重要操作入口, 按住 Shift 展开完整限制与危险说明。
 */
@Mod.EventBusSubscriber(modid = GregSteamExpansion.MOD_ID, value = Dist.CLIENT)
public final class CokeOvenTooltips {

    private static final String OVEN_ITEM = "gtceu:coke_oven";
    private static final String HATCH_ITEM = "gtceu:coke_oven_hatch";

    private CokeOvenTooltips() {}

    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(event.getItemStack().getItem());
        if (itemId == null) return;
        String prefix;
        if (OVEN_ITEM.equals(itemId.toString())) {
            prefix = "gregsteamexpansion.machine.coke_oven.tooltip";
        } else if (HATCH_ITEM.equals(itemId.toString())) {
            prefix = "gregsteamexpansion.machine.coke_oven_hatch.tooltip";
        } else {
            return;
        }
        List<Component> tooltip = event.getToolTip();
        // 保留物品名称行, 重建其余内容: 本设计指定了完整的两级提示文本。
        Component name = tooltip.get(0);
        tooltip.clear();
        tooltip.add(name);
        boolean shift = Screen.hasShiftDown();
        if (!shift) {
            addNumbered(tooltip, prefix + ".summary");
            tooltip.add(Component.translatable("gregsteamexpansion.tooltip.shift_hint")
                    .withStyle(ChatFormatting.DARK_GRAY));
        } else {
            addNumbered(tooltip, prefix + ".details");
        }
    }

    private static void addNumbered(List<Component> tooltip, String baseKey) {
        int index = 0;
        while (true) {
            String key = baseKey + "." + index;
            if (!net.minecraft.client.resources.language.I18n.exists(key)) break;
            tooltip.add(Component.translatable(key).withStyle(ChatFormatting.GRAY));
            index++;
            if (index > 32) break; // 防御: 本地化键缺失时避免死循环
        }
    }
}
