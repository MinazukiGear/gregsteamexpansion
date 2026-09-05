package com.hoshino.gregsteamexpansion.mixins;

import com.gregtechceu.gtceu.integration.xei.widgets.GTOreByProduct;
import com.hoshino.gregsteamexpansion.registry.GSEMachines;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;

import com.google.common.collect.ImmutableList;

import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 矿石处理图第一步机器 (steam-crushers.md / ore-crushing.md 迁移后的表现):
 * the ore → crushed step now runs in the steam crusher, so the diagram's
 * first machine badge shows the steam crusher instead of the LV macerator.
 * The retained upstream routes (crushed → impure dust, centrifuged → dust,
 * purified → dust) keep their macerator badges untouched.
 */
@Mixin(value = GTOreByProduct.class, remap = false)
public class GTOreByProductMixin {

    @ModifyExpressionValue(method = "<init>", at = @At(value = "INVOKE", target =
            "Lcom/google/common/collect/ImmutableList;of(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Lcom/google/common/collect/ImmutableList;"))
    private static ImmutableList<ItemStack> gse$firstStepIsSteamCrusher(ImmutableList original) {
        var copy = new java.util.ArrayList<ItemStack>(original);
        if (copy.isEmpty()) {
            return original;
        }
        copy.set(0, GSEMachines.STEAM_CRUSHER.asStack());
        return ImmutableList.copyOf(copy);
    }
}
