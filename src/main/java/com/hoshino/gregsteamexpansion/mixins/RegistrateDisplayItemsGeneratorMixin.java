package com.hoshino.gregsteamexpansion.mixins;

import com.gregtechceu.gtceu.api.registry.registrate.GTRegistrate;
import com.gregtechceu.gtceu.common.data.GTCreativeModeTabs;

import com.tterrag.registrate.util.entry.RegistryEntry;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 隐藏旧仓的创造模式页入口 (machines-and-hatches.md 禁用范围第 3 条): while
 * GTCEu rebuilds its machine creative tab, the generator's membership check
 * returns false for {@code gtceu:steam_input_hatch} only, so the legacy hatch
 * disappears from normal browsing while administrators can still reach it via
 * its full resource ID.
 */
@Mixin(value = GTCreativeModeTabs.RegistrateDisplayItemsGenerator.class, remap = false)
public abstract class RegistrateDisplayItemsGeneratorMixin {

    @Redirect(method = "accept", at = @At(value = "INVOKE", target =
            "Lcom/gregtechceu/gtceu/api/registry/registrate/GTRegistrate;isInCreativeTab(Lcom/tterrag/registrate/util/entry/RegistryEntry;Lcom/tterrag/registrate/util/entry/RegistryEntry;)Z"))
    private boolean gse$hideLegacySteamHatch(GTRegistrate registrate, RegistryEntry<?> entry,
                                             RegistryEntry<CreativeModeTab> tab) {
        ResourceLocation id = entry.getId();
        if (id != null && "gtceu".equals(id.getNamespace()) && "steam_input_hatch".equals(id.getPath())) {
            return false;
        }
        return registrate.isInCreativeTab(entry, tab);
    }
}
