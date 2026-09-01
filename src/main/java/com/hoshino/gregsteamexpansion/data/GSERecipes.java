package com.hoshino.gregsteamexpansion.data;

import com.gregtechceu.gtceu.api.data.chemical.ChemicalHelper;
import com.gregtechceu.gtceu.api.data.tag.TagPrefix;
import com.gregtechceu.gtceu.common.data.GTMachines;
import com.gregtechceu.gtceu.common.data.GTMaterials;
import com.gregtechceu.gtceu.data.recipe.VanillaRecipeHelper;
import com.hoshino.gregsteamexpansion.GregSteamExpansion;
import com.hoshino.gregsteamexpansion.registry.GSEMachines;

import net.minecraft.data.recipes.FinishedRecipe;

import java.util.function.Consumer;

public final class GSERecipes {
    private GSERecipes() {}

    public static void init(Consumer<FinishedRecipe> provider) {
        VanillaRecipeHelper.addShapedRecipe(
                provider,
                GregSteamExpansion.id("lp_steam_mixed_fuel_boiler"),
                GSEMachines.MIXED_FUEL_BOILER.left().asStack(),
                "DQD",
                "SwL",
                "DQD",
                'D', ChemicalHelper.get(TagPrefix.plateDouble, GTMaterials.Bronze),
                'Q', ChemicalHelper.get(TagPrefix.pipeQuadrupleFluid, GTMaterials.Bronze),
                'S', GTMachines.STEAM_SOLID_BOILER.left().asStack(),
                'L', GTMachines.STEAM_LIQUID_BOILER.left().asStack());

        VanillaRecipeHelper.addShapedRecipe(
                provider,
                GregSteamExpansion.id("hp_steam_mixed_fuel_boiler"),
                GSEMachines.MIXED_FUEL_BOILER.right().asStack(),
                "DQD",
                "SwL",
                "DQD",
                'D', ChemicalHelper.get(TagPrefix.plateDouble, GTMaterials.Steel),
                'Q', ChemicalHelper.get(TagPrefix.pipeQuadrupleFluid, GTMaterials.Steel),
                'S', GTMachines.STEAM_SOLID_BOILER.right().asStack(),
                'L', GTMachines.STEAM_LIQUID_BOILER.right().asStack());
    }
}
