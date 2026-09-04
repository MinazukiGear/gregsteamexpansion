package com.hoshino.gregsteamexpansion.mixins;

import com.gregtechceu.gtceu.common.machine.multiblock.steam.LargeBoilerMachine;
import com.hoshino.gregsteamexpansion.difficulty.GSEDifficultyState;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Applies the confirmed steam-output coverage to GTCEu large (multiblock)
 * boilers (difficulty.md 上游模组设置覆盖). Both steamPerWater reads inside
 * updateCurrentTemperature are linear factors — the first sizes the requested
 * water import, the second converts the consumed water into steamGenerated —
 * so wrapping the field reads multiplies the requested water and the produced
 * steam together while keeping the upstream water-to-steam ratio, the fuel
 * burn and the temperature logic untouched, and the GUI's steam_output line
 * shows the already-scaled steamGenerated. The multiblock boiler's internal
 * tanks deliberately take no cache multiplier; only single-block boiler
 * caches do.
 */
@Mixin(LargeBoilerMachine.class)
public abstract class LargeBoilerMachineMixin {

    // GTCEu targets are never SRG-renamed at runtime; remap = false keeps the
    // annotation processor from looking for obfuscation mappings that cannot
    // exist for mod methods.
    @ModifyExpressionValue(method = "updateCurrentTemperature", remap = false,
            at = @At(value = "FIELD",
                     target = "Lcom/gregtechceu/gtceu/config/ConfigHolder$MachineConfigs$LargeBoilers;steamPerWater:I"))
    private int gse$multiplySteamGeneration(int original) {
        LargeBoilerMachine self = (LargeBoilerMachine) (Object) this;
        return Math.round(original * GSEDifficultyState.current(self.isRemote()).getSteamOutputMultiplier());
    }
}
