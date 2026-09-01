package com.hoshino.gregsteamexpansion;

import com.gregtechceu.gtceu.api.addon.GTAddon;
import com.gregtechceu.gtceu.api.addon.IGTAddon;
import com.gregtechceu.gtceu.api.registry.registrate.GTRegistrate;
import com.hoshino.gregsteamexpansion.data.GSELang;
import com.hoshino.gregsteamexpansion.registry.GSERegistration;

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
    public String addonModId() {
        return GregSteamExpansion.MOD_ID;
    }
}
