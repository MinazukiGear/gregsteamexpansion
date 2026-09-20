package com.hoshino.gregsteamexpansion.integration.jade;

import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

/** Jade entry point; provider implementations are kept out of registration wiring. */
@WailaPlugin
public final class GSEJadePlugin implements IWailaPlugin {
    @Override
    public void register(IWailaCommonRegistration registration) {
        GSEJadeProviders.registerCommon(registration);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        GSEJadeProviders.registerClient(registration);
    }
}
