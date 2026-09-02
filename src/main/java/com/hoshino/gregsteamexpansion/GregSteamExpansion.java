package com.hoshino.gregsteamexpansion;

import com.gregtechceu.gtceu.api.GTCEuAPI;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.common.data.GTCreativeModeTabs;
import com.hoshino.gregsteamexpansion.data.GSERecipes;
import com.hoshino.gregsteamexpansion.registry.GSEMachines;
import com.hoshino.gregsteamexpansion.registry.GSERegistration;
import com.tterrag.registrate.providers.ProviderType;
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
        GSERegistration.REGISTRATE.registerEventListeners(modEventBus);
        GSERegistration.REGISTRATE.creativeModeTab(GTCreativeModeTabs.MACHINE);
        GSERegistration.REGISTRATE.addDataGenerator(ProviderType.RECIPE, GSERecipes::init);
        modEventBus.addGenericListener(MachineDefinition.class, this::registerMachines);
        modEventBus.addListener(this::commonSetup);
    }

    public static net.minecraft.resources.ResourceLocation id(String path) {
        return net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    private void registerMachines(final GTCEuAPI.RegisterEvent<?, MachineDefinition> event) {
        GSEMachines.init();
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info(
                "{} initialized with GTCEu {}.",
                MOD_NAME,
                loadedVersion("gtceu")
        );
    }

    private static String loadedVersion(final String modId) {
        return ModList.get()
                .getModContainerById(modId)
                .map(container -> container.getModInfo().getVersion().toString())
                .orElse("not loaded on this side");
    }
}
