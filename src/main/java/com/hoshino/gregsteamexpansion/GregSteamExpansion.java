package com.hoshino.gregsteamexpansion;

import com.mojang.logging.LogUtils;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(GregSteamExpansion.MOD_ID)
public final class GregSteamExpansion {
    public static final String MOD_ID = "gregsteamexpansion";
    public static final String MOD_NAME = "Greg Steam Expansion";
    public static final Logger LOGGER = LogUtils.getLogger();

    public GregSteamExpansion(final FMLJavaModLoadingContext context) {
        IEventBus modEventBus = context.getModEventBus();
        modEventBus.addListener(this::commonSetup);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info(
                "{} initialized with GTCEu {} and EMI {}.",
                MOD_NAME,
                loadedVersion("gtceu"),
                loadedVersion("emi")
        );
    }

    private static String loadedVersion(final String modId) {
        return ModList.get()
                .getModContainerById(modId)
                .map(container -> container.getModInfo().getVersion().toString())
                .orElse("not loaded on this side");
    }
}
