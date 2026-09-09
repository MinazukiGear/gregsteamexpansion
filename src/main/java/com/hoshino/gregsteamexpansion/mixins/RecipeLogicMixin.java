package com.hoshino.gregsteamexpansion.mixins;

import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.common.machine.multiblock.primitive.PrimitiveBlastFurnaceMachine;
import com.hoshino.gregsteamexpansion.GregSteamExpansion;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Iterator;

/**
 * Keeps the mod's injected wrought-iron recipes out of GTCEu's own Primitive
 * Blast Furnace (large-steam-blast-furnace.md, 2026-09-09 裁定).
 *
 * <p>The three {@code wrought_iron_from_dust_*_dust} recipes are injected
 * add-only into the shared {@code gtceu:primitive_blast_furnace} type, so the
 * upstream multiblock would otherwise match and execute them — letting a
 * vanilla PBF produce wrought iron from dust in 800t, far cheaper than the
 * 96-parallel megastructure that is supposed to be the wrought-iron source.
 * Rather than touching any upstream recipe, the candidate iterator is filtered
 * on the machine side: when the searching machine is a
 * {@link PrimitiveBlastFurnaceMachine}, recipes owned by this mod are dropped.
 * The mod's own Large Steam Blast Furnace does not go through {@code RecipeLogic}
 * at all (it batches via {@code AbstractSteamProcessorMachine#tryStartBatch}), so
 * it keeps executing the full shared type — 3 wrought-iron recipes plus all 18
 * upstream steel recipes — exactly as its design intends (2026-09-09).</p>
 */
@Mixin(RecipeLogic.class)
public abstract class RecipeLogicMixin {

    @ModifyExpressionValue(method = "searchRecipe", remap = false,
            at = @At(value = "INVOKE",
                     target = "Lcom/gregtechceu/gtceu/api/recipe/GTRecipeType;"
                             + "searchRecipe(Lcom/gregtechceu/gtceu/api/capability/recipe/IRecipeCapabilityHolder;"
                             + "Ljava/util/function/Predicate;)Ljava/util/Iterator;"))
    private Iterator<GTRecipe> gse$hideModRecipesFromPrimitiveBlastFurnace(Iterator<GTRecipe> original) {
        RecipeLogic self = (RecipeLogic) (Object) this;
        if (!(self.machine instanceof PrimitiveBlastFurnaceMachine)) {
            return original;
        }
        return new Iterator<>() {

            private GTRecipe next = advance();

            private GTRecipe advance() {
                while (original.hasNext()) {
                    GTRecipe candidate = original.next();
                    if (!GregSteamExpansion.MOD_ID.equals(candidate.getId().getNamespace())) {
                        return candidate;
                    }
                }
                return null;
            }

            @Override
            public boolean hasNext() {
                return next != null;
            }

            @Override
            public GTRecipe next() {
                GTRecipe current = next;
                next = advance();
                return current;
            }
        };
    }
}
