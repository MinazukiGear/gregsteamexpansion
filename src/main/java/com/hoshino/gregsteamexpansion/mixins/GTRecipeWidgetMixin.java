package com.hoshino.gregsteamexpansion.mixins;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.RecipeHelper;
import com.gregtechceu.gtceu.api.recipe.ingredient.EnergyStack;
import com.gregtechceu.gtceu.integration.xei.widgets.GTRecipeWidget;
import com.gregtechceu.gtceu.utils.FormattingUtil;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * EMI power display (miscellaneous.md EMI 功耗显示): GTCEu's generic recipe view
 * puts only "{amperage} A @ {tier}" in the body and hides the EU/t value in a
 * hover tooltip. Both power-line sites (initial build and the overclock-tier
 * refresh) are rewritten into the combined
 * "{consume|generate}：{EU/t} EU/t（{amperage} A @ {tier}）" format.
 *
 * <p>The EU/t value is taken from the same EnergyStack the upstream code
 * formatted, and the amperage / tier strings are reused from the upstream
 * arguments, so an overclock-tier switch refreshes all three from one OC
 * result and no stale base value can remain. The wrapped call only executes
 * when upstream drew an EU line, so pure steam / fuel recipes are untouched.
 * Everything is gated on EMI being loaded: without it the original text
 * renders unchanged and EMI stays an optional client tool.</p>
 *
 * <p>The {@code @Local} sugar parameters sit AFTER the {@code Operation}
 * parameter: MixinExtras requires sugars to be trailing at the very end of
 * the handler signature, and a mid-list sugar aborts the whole mixin at
 * class-load time (which took down GTCEu's whole EMI plugin registration).</p>
 *
 * <p>Unlike the other GTCEu mixins this one must NOT set {@code remap = false}:
 * the wrapped {@code Component.translatable} call is a vanilla method that only
 * resolves through the refmap in production. The GTCEu target class and method
 * names are absent from the obfuscation mappings, so remapping them is a
 * no-op.</p>
 */
@Mixin(GTRecipeWidget.class)
public abstract class GTRecipeWidgetMixin {
    @Shadow(remap = false)
    @Final
    private GTRecipe recipe;

    @WrapOperation(method = "initializeRecipeTextWidget", at = @At(value = "INVOKE", ordinal = 0,
            target = "Lnet/minecraft/network/chat/Component;translatable(Ljava/lang/String;[Ljava/lang/Object;)Lnet/minecraft/network/chat/MutableComponent;"))
    private MutableComponent gse$initialPowerLineWithEUt(String key, Object[] args,
            Operation<MutableComponent> original,
            @Local(type = EnergyStack.WithIO.class) EnergyStack.WithIO eut) {
        if (!GTCEu.Mods.isEMILoaded()) {
            return original.call(key, args);
        }
        return gse$combinedPowerLine(eut.isInput(), eut.getTotalEU(), args);
    }

    @WrapOperation(method = "setRecipeOverclockWidget", at = @At(value = "INVOKE", ordinal = 0,
            target = "Lnet/minecraft/network/chat/Component;translatable(Ljava/lang/String;[Ljava/lang/Object;)Lnet/minecraft/network/chat/MutableComponent;"))
    private MutableComponent gse$overclockedPowerLineWithEUt(String key, Object[] args,
            Operation<MutableComponent> original,
            @Local(type = EnergyStack.class) EnergyStack overclockedEut) {
        if (!GTCEu.Mods.isEMILoaded()) {
            return original.call(key, args);
        }
        return gse$combinedPowerLine(RecipeHelper.getRealEUtWithIO(recipe).isInput(),
                overclockedEut.getTotalEU(), args);
    }

    @Unique
    private static MutableComponent gse$combinedPowerLine(boolean consumer, long eutPerTick, Object[] upstreamArgs) {
        // Amperage and tier are upstream's own formatted values; only the EU/t
        // figure is added, formatted like the upstream hover tooltip.
        return Component.translatable(consumer
                ? "gregsteamexpansion.emi.recipe.eu"
                : "gregsteamexpansion.emi.recipe.eu_inverted",
                FormattingUtil.formatNumbers(eutPerTick), upstreamArgs[0], upstreamArgs[1]);
    }
}
