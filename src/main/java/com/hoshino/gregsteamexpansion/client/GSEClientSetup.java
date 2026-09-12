package com.hoshino.gregsteamexpansion.client;

import com.hoshino.gregsteamexpansion.cokeoven.LargeCokeOvenRenderer;
import com.hoshino.gregsteamexpansion.difficulty.GSEDifficultyMessages;
import com.hoshino.gregsteamexpansion.difficulty.GSEDifficultyState;
import com.hoshino.gregsteamexpansion.registry.GSEMenuTypes;

import net.minecraft.client.gui.screens.MenuScreens;

import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

public final class GSEClientSetup {
    private GSEClientSetup() {}

    /**
     * Registers client-only model codecs while mods are being constructed.
     * Machine models are decoded before {@link FMLClientSetupEvent}, so this
     * cannot be deferred to {@link #init(FMLClientSetupEvent)}.
     */
    public static void registerEarly() {
        LargeCokeOvenRenderer.bootstrap();
    }

    public static void init(FMLClientSetupEvent event) {
        event.enqueueWork(() ->
                MenuScreens.register(GSEMenuTypes.CRAFTING_STATION.get(), CraftingStationScreen::new));
        IEventBus forgeBus = net.minecraftforge.common.MinecraftForge.EVENT_BUS;
        forgeBus.addListener(GSEClientSetup::onClientLoggingIn);
        forgeBus.addListener(GSEClientSetup::onClientLoggingOut);
    }

    /**
     * Declares this process's startup tier as the first play-phase
     * packet after the join completes; the server gates the connection the
     * moment it arrives (difficulty.md 客户端进入校验).
     */
    private static void onClientLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        GSEDifficultyMessages.sendDeclaration();
    }

    private static void onClientLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        GSEDifficultyState.clearClientTierSynced();
    }
}
