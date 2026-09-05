package com.hoshino.gregsteamexpansion.registry;

import com.hoshino.gregsteamexpansion.GregSteamExpansion;
import com.hoshino.gregsteamexpansion.migration.recipe.ExactDirectionShapedRecipe;

import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Mod-owned vanilla recipe serializers. The large steam crusher's acquisition
 * recipe must not accept horizontal mirroring (steam-crushers.md 大型蒸汽粉碎机
 * 控制器配方), which vanilla shaped matching always tries — the exact-direction
 * serializer keeps the brass rotor on the left and the saw blade on the right.
 */
public final class GSERecipeSerializers {
    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
            DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, GregSteamExpansion.MOD_ID);

    public static final RegistryObject<RecipeSerializer<?>> EXACT_DIRECTION_SHAPED =
            SERIALIZERS.register("exact_direction_shaped", ExactDirectionShapedRecipe.Serializer::new);

    private GSERecipeSerializers() {}

    public static void register(IEventBus modEventBus) {
        SERIALIZERS.register(modEventBus);
    }
}
