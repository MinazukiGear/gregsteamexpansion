package com.hoshino.gregsteamexpansion;

import com.gregtechceu.gtceu.api.GTCEuAPI;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.common.data.GTCreativeModeTabs;
import com.hoshino.gregsteamexpansion.client.GSEClientSetup;
import com.hoshino.gregsteamexpansion.client.GSEConfigScreen;
import com.hoshino.gregsteamexpansion.data.GSEBlockStates;
import com.hoshino.gregsteamexpansion.data.GSEBlockTags;
import com.hoshino.gregsteamexpansion.data.GSEItemModels;
import com.hoshino.gregsteamexpansion.data.GSELoot;
import com.hoshino.gregsteamexpansion.data.GSERecipes;
import com.hoshino.gregsteamexpansion.difficulty.GSEDifficultyConfig;
import com.hoshino.gregsteamexpansion.difficulty.GSEDifficultyCondition;
import com.hoshino.gregsteamexpansion.difficulty.GSEDifficultyEvents;
import com.hoshino.gregsteamexpansion.difficulty.GSEDifficultyMessages;
import com.hoshino.gregsteamexpansion.registry.GSEBlockEntityTypes;
import com.hoshino.gregsteamexpansion.registry.GSEBlocks;
import com.hoshino.gregsteamexpansion.registry.GSEMachines;
import com.hoshino.gregsteamexpansion.registry.GSEMenuTypes;
import com.hoshino.gregsteamexpansion.registry.GSERegistration;
import com.tterrag.registrate.providers.ProviderType;
import com.mojang.logging.LogUtils;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.crafting.CraftingHelper;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
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
        GSERegistration.REGISTRATE.addDataGenerator(ProviderType.BLOCKSTATE, GSEBlockStates::init);
        GSERegistration.REGISTRATE.addDataGenerator(ProviderType.ITEM_MODEL, GSEItemModels::init);
        GSERegistration.REGISTRATE.addDataGenerator(ProviderType.LOOT, GSELoot::init);
        GSERegistration.REGISTRATE.addDataGenerator(ProviderType.BLOCK_TAGS, GSEBlockTags::init);
        GSEBlocks.BLOCKS.register(modEventBus);
        GSEBlocks.ITEMS.register(modEventBus);
        GSEBlockEntityTypes.BLOCK_ENTITY_TYPES.register(modEventBus);
        GSEMenuTypes.MENU_TYPES.register(modEventBus);
        modEventBus.addListener(this::addCreative);
        modEventBus.addGenericListener(MachineDefinition.class, this::registerMachines);
        modEventBus.addListener(this::commonSetup);

        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, GSEDifficultyConfig.SPEC);
        // The mod is required on both sides (mods.toml displayTest MATCH_VERSION),
        // so the common constructor must stay server-safe: the client setup
        // listener and config screen only wire up on the client dist because
        // referencing their classes on a dedicated server fails dist cleaning.
        if (FMLEnvironment.dist.isClient()) {
            modEventBus.addListener(GSEClientSetup::init);
            ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                    () -> new ConfigScreenHandler.ConfigScreenFactory(GSEConfigScreen::new));
        }
        modEventBus.addListener(GSEDifficultyConfig::onConfigLoading);
        modEventBus.addListener(GSEDifficultyConfig::onConfigReloading);
        GSEDifficultyMessages.register();
        CraftingHelper.register(GSEDifficultyCondition.Serializer.INSTANCE);

        MinecraftForge.EVENT_BUS.addListener(GSEDifficultyEvents::onServerStarted);
        MinecraftForge.EVENT_BUS.addListener(GSEDifficultyEvents::onServerStopped);
        MinecraftForge.EVENT_BUS.addListener(GSEDifficultyEvents::onPlayerLoggedIn);
    }

    public static net.minecraft.resources.ResourceLocation id(String path) {
        return net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    private void addCreative(final BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(GSEBlocks.CRAFTING_STATION_ITEM.get());
            event.accept(GSEBlocks.CRAFTING_STATION_SLAB_ITEM.get());
        }
        // Steam structure blocks sit next to GTCEu machine casings; the bronze
        // component is a crafting material and goes with GTCEu parts.
        if (event.getTabKey() == GTCreativeModeTabs.MACHINE.getKey()) {
            event.accept(GSEBlocks.STEAM_GRINDING_BLOCK_ITEM.get());
            event.accept(GSEBlocks.STEAM_ASSEMBLY_BLOCK_ITEM.get());
            event.accept(GSEBlocks.STEAM_CIRCUIT_ASSEMBLY_BLOCK_ITEM.get());
            event.accept(GSEBlocks.STEAM_MIXING_BLOCK_ITEM.get());
        }
        if (event.getTabKey() == GTCreativeModeTabs.ITEM.getKey()) {
            event.accept(GSEBlocks.BRONZE_COMPONENT.get());
        }
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
