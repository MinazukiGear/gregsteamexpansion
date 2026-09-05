package com.hoshino.gregsteamexpansion.integration.emi;

import com.gregtechceu.gtceu.GTCEu;

import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * 从 EMI 的普通物品索引中隐藏遗留的 {@code gtceu:steam_input_hatch}
 * (machines-and-hatches.md 禁用范围第 3 条). EMI stays an optional client-side
 * recipe viewer: this class is only instantiated by EMI itself through its
 * {@code @EmiEntrypoint} scan, so the mod never requires EMI at runtime.
 */
@EmiEntrypoint
public class GSEEmiPlugin implements EmiPlugin {

    @Override
    public void register(EmiRegistry registry) {
        registry.removeEmiStacks(stack -> {
            Item item = stack.getKeyOfType(Item.class);
            if (item == null) {
                return false;
            }
            ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
            return GTCEu.id("steam_input_hatch").equals(id);
        });
    }
}
