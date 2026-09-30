package com.hoshino.gregsteamexpansion.gametest;

import com.hoshino.gregsteamexpansion.GregSteamExpansion;

import net.minecraftforge.event.RegisterGameTestsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Keeps Jade-linked regression classes out of optional-dependency smoke runtimes. */
@Mod.EventBusSubscriber(modid = GregSteamExpansion.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class GSEGameTestRegistration {
    private GSEGameTestRegistration() {}

    @SubscribeEvent
    public static void register(RegisterGameTestsEvent event) {
        // Forge reflects every @GameTestHolder before applying its namespace
        // filter. Merely loading these method signatures requires Jade's API.
        if (System.getProperty("gse.dependencySmokeProfile") == null) {
            event.register(GSEBlastFurnaceTests.class);
            event.register(GSECrusherTests.class);
            event.register(GSESteamHatchTests.class);
            event.register(GSEVoidProducerTests.class);
            event.register(GSETerminalAEIntegrationTests.class);
        }
    }
}
