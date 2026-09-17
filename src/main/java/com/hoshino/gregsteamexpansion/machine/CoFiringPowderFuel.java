package com.hoshino.gregsteamexpansion.machine;

import com.gregtechceu.gtceu.api.data.chemical.ChemicalHelper;
import com.gregtechceu.gtceu.api.data.chemical.material.Material;
import com.gregtechceu.gtceu.api.data.tag.TagPrefix;
import com.gregtechceu.gtceu.common.data.GTMaterials;
import com.gregtechceu.gtceu.utils.GTUtil;
import com.hoshino.gregsteamexpansion.registry.GSETags;

import net.minecraft.world.item.ItemStack;

/** Shared acceptance and heat-value rules for co-firing powder fuels. */
public final class CoFiringPowderFuel {

    private CoFiringPowderFuel() {}

    public static boolean isValid(ItemStack stack) {
        return !stack.isEmpty() && stack.is(GSETags.CO_FIRING_DUST_FUELS);
    }

    public static int burnTime(ItemStack stack) {
        if (!isValid(stack)) return 0;

        Material material = ChemicalHelper.getMaterialStack(stack).material();
        int base = material == GTMaterials.Coal || material == GTMaterials.Charcoal ? 1600 :
                material == GTMaterials.Coke ? 3200 : material == GTMaterials.Wood ? 300 :
                        GTUtil.getItemBurnTime(stack.getItem());
        if (base <= 0) base = 1600;

        TagPrefix prefix = ChemicalHelper.getPrefix(stack.getItem());
        if (prefix == TagPrefix.dustSmall) return Math.max(1, base / 4);
        if (prefix == TagPrefix.dustTiny) return Math.max(1, base / 9);
        return base;
    }
}
