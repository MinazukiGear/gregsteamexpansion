package com.hoshino.gregsteamexpansion.registry;

import com.gregtechceu.gtceu.api.GTCEuAPI;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.recipe.GTRecipeSerializer;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.gregtechceu.gtceu.common.data.GTSoundEntries;
import com.hoshino.gregsteamexpansion.GregSteamExpansion;

import com.lowdragmc.lowdraglib.gui.texture.ProgressTexture.FillDirection;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

import static com.lowdragmc.lowdraglib.gui.texture.ProgressTexture.FillDirection.LEFT_TO_RIGHT;

/**
 * 矿石粉碎 / Ore Crushing recipe type (ore-crushing.md 基本定义): the first-stage
 * "ore or raw ore → crushed ore" processing step for the upcoming steam
 * crusher multiblocks.
 *
 * <p>Recipes are recorded against the 2 EU/t / 400 tick baseline so vanilla GT
 * recipe logic and viewers understand them; steam consumers run them at
 * 600 ticks by their own machine spec, not by this type.</p>
 *
 * <p>Per ore-crushing.md 配方查看器与表现 the viewer category stays hidden until
 * the first obtainable consumer machine registers — the same gate that enables
 * the macerator migration (ore-crushing.md 从研磨机迁移). Flip
 * {@link #CONSUMER_EXISTS} once that machine lands.</p>
 */
public final class GSERecipeTypes {
    /**
     * Whether an obtainable machine consuming {@link #ORE_CRUSHING_RECIPES} is
     * registered. The steam crushers from steam-crushers.md are the intended
     * first consumer; until then the macerator keeps its ore recipes and the
     * viewer category is hidden.
     */
    public static final boolean CONSUMER_EXISTS = false;

    public static GTRecipeType ORE_CRUSHING_RECIPES;

    private GSERecipeTypes() {}

    /**
     * Mirrors {@code GTRecipeTypes.register} with this mod's namespace: the
     * vanilla recipe type and serializer registries plus GTCEu's own recipe
     * type registry, which unfreezes for exactly this addon event.
     */
    public static void init(GTCEuAPI.RegisterEvent<ResourceLocation, GTRecipeType> event) {
        ORE_CRUSHING_RECIPES = registerOreCrushing(event);
    }

    private static GTRecipeType registerOreCrushing(GTCEuAPI.RegisterEvent<ResourceLocation, GTRecipeType> event) {
        ResourceLocation id = GregSteamExpansion.id("ore_crushing");
        GTRecipeType recipeType = new GTRecipeType(id, GTRecipeTypes.MULTIBLOCK)
                .setMaxIOSize(1, 4, 0, 0)
                .setEUIO(IO.IN)
                // ore-crushing.md 基本定义: 2 EU/t and 400 ticks are the recorded
                // baseline; steam machines apply the fixed 1.5x / 600-tick rule
                // in their own logic, never through this type.
                .prepareBuilder(builder -> builder.duration(400).EUt(2))
                // ore-crushing.md 配方查看器与表现: the macerator's crushed-ore
                // input overlay, dust output overlay, grinding progress bars
                // and sounds are reused, but the category identity is new.
                .setSlotOverlay(false, false, GuiTextures.CRUSHED_ORE_OVERLAY)
                .setSlotOverlay(true, false, GuiTextures.DUST_OVERLAY)
                .setProgressBar(GuiTextures.PROGRESS_BAR_MACERATE, FillDirection.LEFT_TO_RIGHT)
                .setSteamProgressBar(GuiTextures.PROGRESS_BAR_MACERATE_STEAM, FillDirection.LEFT_TO_RIGHT)
                .setSound(GTSoundEntries.MACERATOR)
                // Hidden while no consumer machine exists; an empty
                // viewer-only category must not register (ore-crushing.md:
                // 消费者尚未完成时不注册一个仅供查看、无法执行的空类别).
                .setXEIVisible(CONSUMER_EXISTS);

        GTRegistries.register(BuiltInRegistries.RECIPE_TYPE, recipeType.registryName, recipeType);
        GTRegistries.register(BuiltInRegistries.RECIPE_SERIALIZER, recipeType.registryName,
                new GTRecipeSerializer());
        event.register(id, recipeType);
        return recipeType;
    }
}
