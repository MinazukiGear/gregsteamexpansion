package com.hoshino.gregsteamexpansion;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.addon.GTAddon;
import com.gregtechceu.gtceu.api.addon.IGTAddon;
import com.gregtechceu.gtceu.api.registry.registrate.GTRegistrate;
import com.gregtechceu.gtceu.data.pack.GTDynamicDataPack;
import com.hoshino.gregsteamexpansion.data.GSELang;
import com.hoshino.gregsteamexpansion.data.GSERecipes;
import com.hoshino.gregsteamexpansion.registry.GSERegistration;

import net.minecraft.data.recipes.FinishedRecipe;

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

        // 精确覆盖普通焦炉控制器与焦炉仓的上游合成配方 (coke-ovens.md 获取配方):
        // 此过滤器同时屏蔽低优先级数据包中的同 ID JSON。替换配方必须由
        // addRecipes 写入过滤器所属的动态包本身, 不能只依赖生成的静态 JSON。
        consumer.accept(GTCEu.id("shaped/coke_oven"));
        consumer.accept(GTCEu.id("shaped/coke_oven_hatch"));
    }

    @Override
    public void addRecipes(Consumer<FinishedRecipe> provider) {
        // GTRecipes passes a consumer that rejects every ID in RECIPE_FILTERS,
        // including these replacements. Write just these two recipes directly
        // into the dynamic pack: its resource filter affects lower packs, not
        // its own entries. This runs again on every data reload and preserves
        // the original IDs while keeping the upstream recipes filtered out.
        GSERecipes.addCokeOvenRecipes(GTDynamicDataPack::addRecipe);
    }

    @Override
    public String addonModId() {
        return GregSteamExpansion.MOD_ID;
    }
}
