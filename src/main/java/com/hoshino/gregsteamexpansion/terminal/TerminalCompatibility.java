package com.hoshino.gregsteamexpansion.terminal;

import net.minecraftforge.fml.ModList;

/** Optional dependency gate shared by registration, recipes and item behaviour. */
public final class TerminalCompatibility {
    private TerminalCompatibility() {}

    public static boolean isAvailable() {
        return ModList.get().isLoaded("ae2") && ModList.get().isLoaded("gtmthings");
    }
}
