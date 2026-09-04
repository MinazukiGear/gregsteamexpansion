package com.hoshino.gregsteamexpansion.registry;

import com.hoshino.gregsteamexpansion.GregSteamExpansion;
import com.hoshino.gregsteamexpansion.menu.CraftingStationMenu;

import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class GSEMenuTypes {
    public static final DeferredRegister<MenuType<?>> MENU_TYPES =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, GregSteamExpansion.MOD_ID);

    public static final RegistryObject<MenuType<CraftingStationMenu>> CRAFTING_STATION =
            MENU_TYPES.register("crafting_station",
                    () -> IForgeMenuType.create(CraftingStationMenu::fromNetwork));

    private GSEMenuTypes() {}
}
