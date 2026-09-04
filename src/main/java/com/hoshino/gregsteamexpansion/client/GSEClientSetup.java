package com.hoshino.gregsteamexpansion.client;

import com.hoshino.gregsteamexpansion.difficulty.GSEDifficultyMessages;
import com.hoshino.gregsteamexpansion.difficulty.GSEDifficultyState;
import com.hoshino.gregsteamexpansion.registry.GSEMenuTypes;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.GenericDirtMessageScreen;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;

import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

public final class GSEClientSetup {
    private GSEClientSetup() {}

    public static void init(FMLClientSetupEvent event) {
        event.enqueueWork(() ->
                MenuScreens.register(GSEMenuTypes.CRAFTING_STATION.get(), CraftingStationScreen::new));
        IEventBus forgeBus = net.minecraftforge.common.MinecraftForge.EVENT_BUS;
        forgeBus.addListener(GSEClientSetup::onClientLoggingIn);
        forgeBus.addListener(GSEClientSetup::onClientTick);
    }

    /**
     * Declares this installation's config tier as the first play-phase
     * packet after the join completes; the server gates the connection the
     * moment it arrives (difficulty.md 客户端进入校验).
     */
    private static void onClientLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        GSEDifficultyMessages.sendDeclaration();
    }

    /**
     * Opens the first-entry difficulty choice once the join sequence has
     * handed the screen back (the choice packet arrives while terrain or
     * level screens are still up, which would otherwise cover it).
     */
    private static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            GSEDifficultyState.clearClientChoicePending();
            GSEDifficultyState.clearClientTierSynced();
            return;
        }
        if (GSEDifficultyState.isClientChoicePending()
                && (minecraft.screen == null
                        || minecraft.screen instanceof ReceivingLevelScreen
                        || minecraft.screen instanceof GenericDirtMessageScreen
                        || minecraft.screen instanceof LevelLoadingScreen)) {
            GSEDifficultyState.clearClientChoicePending();
            minecraft.setScreen(new GSEDifficultySelectScreen());
        }
    }
}
