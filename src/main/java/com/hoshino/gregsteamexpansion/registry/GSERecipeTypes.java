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
     * registered. The steam crushers from steam-crushers.md are the first
     * consumer and ship in the same version as the macerator migration, so the
     * viewer category ships visible; the runtime consumer verification in
     * {@code OreCrushingMigration} still gates the actual migration on every
     * datapack load.
     */
    public static final boolean CONSUMER_EXISTS = true;

    public static GTRecipeType ORE_CRUSHING_RECIPES;

    /**
     * 锅炉房 / Boiler Room fuel type (boiler-room.md P0#3-A): liquid fuel +
     * water -> steam, the multiblock's ONLY energy source. The type starts
     * EMPTY — {@code BoilerRoomFuelSync} copies every liquid-fuel recipe of
     * the upstream steam-boiler type into it at each datapack load with the
     * upstream large-boiler burn-time divisor, so the whitelist stays
     * data-pack driven and pure-solid fuels never enter the type.
     */
    public static GTRecipeType BOILER_ROOM_RECIPES;

    private GSERecipeTypes() {}

    /**
     * Mirrors {@code GTRecipeTypes.register} with this mod's namespace: the
     * vanilla recipe type and serializer registries plus GTCEu's own recipe
     * type registry, which unfreezes for exactly this addon event.
     */
    public static void init(GTCEuAPI.RegisterEvent<ResourceLocation, GTRecipeType> event) {
        ORE_CRUSHING_RECIPES = registerOreCrushing(event);
        BOILER_ROOM_RECIPES = registerBoilerRoom(event);
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

    private static GTRecipeType registerBoilerRoom(GTCEuAPI.RegisterEvent<ResourceLocation, GTRecipeType> event) {
        ResourceLocation id = GregSteamExpansion.id("boiler_room");
        GTRecipeType recipeType = new GTRecipeType(id, GTRecipeTypes.MULTIBLOCK)
                // boiler-room.md P0#3: 1 物品入(占位, 实际不使用)/1 流体入/1 流体出,
                // 与上游大型锅炉燃料配方同构; 配方内容由 BoilerRoomFuelSync 注入。
                .setMaxIOSize(1, 0, 1, 1)
                .setProgressBar(GuiTextures.PROGRESS_BAR_BOILER_FUEL.get(true), FillDirection.DOWN_TO_UP)
                .setMaxTooltips(1)
                .setSound(GTSoundEntries.FURNACE)
                .setXEIVisible(true);

        GTRegistries.register(BuiltInRegistries.RECIPE_TYPE, recipeType.registryName, recipeType);
        GTRegistries.register(BuiltInRegistries.RECIPE_SERIALIZER, recipeType.registryName,
                new GTRecipeSerializer());
        event.register(id, recipeType);
        return recipeType;
    }
}
