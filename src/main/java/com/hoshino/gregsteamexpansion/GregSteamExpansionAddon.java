package com.hoshino.gregsteamexpansion;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.addon.GTAddon;
import com.gregtechceu.gtceu.api.addon.IGTAddon;
import com.gregtechceu.gtceu.api.registry.registrate.GTRegistrate;
import com.hoshino.gregsteamexpansion.data.GSELang;
import com.hoshino.gregsteamexpansion.registry.GSERegistration;

import java.util.function.Consumer;

@GTAddon
public final class GregSteamExpansionAddon implements IGTAddon {
    @Override
    public GTRegistrate getRegistrate() {
        return GSERegistration.REGISTRATE;
    }

    @Override
    public void initializeAddon() {
        GSELang.init();
    }

    @Override
    public void removeRecipes(Consumer<net.minecraft.resources.ResourceLocation> consumer) {
        // 禁用上游蒸汽输入仓配方 (machines-and-hatches.md 禁用范围第 1 条):
        // GTCEu turns every id reported here into a resource-pack filter, so
        // gtceu:steam_hatch can never load under any work intensity or
        // machines.steelSteamMultiblocks setting.
        consumer.accept(GTCEu.id("steam_hatch"));
    }

    @Override
    public String addonModId() {
        return GregSteamExpansion.MOD_ID;
    }
}
