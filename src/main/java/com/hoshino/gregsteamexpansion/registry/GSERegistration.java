package com.hoshino.gregsteamexpansion.registry;

import com.gregtechceu.gtceu.api.registry.registrate.GTRegistrate;
import com.hoshino.gregsteamexpansion.GregSteamExpansion;

public final class GSERegistration {
    public static final GTRegistrate REGISTRATE = GTRegistrate.create(GregSteamExpansion.MOD_ID);

    private GSERegistration() {}
}
