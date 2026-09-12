package com.hoshino.gregsteamexpansion.mixins;

import com.hoshino.gregsteamexpansion.client.GSEDifficultyDisplay;
import com.hoshino.gregsteamexpansion.difficulty.GSEDifficultyState;
import net.minecraft.client.gui.screens.worldselection.WorldSelectionList;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;

/**
 * Tags every local world with this client's process-wide startup difficulty.
 */
@Mixin(WorldSelectionList.WorldListEntry.class)
public abstract class WorldListEntryMixin {

    @ModifyExpressionValue(method = "render",
            at = @At(value = "INVOKE",
                     target = "Lnet/minecraft/world/level/storage/LevelSummary;getLevelName()Ljava/lang/String;"))
    private String gse$appendDifficultyTag(String name) {
        return name + GSEDifficultyDisplay.worldListSuffix(GSEDifficultyState.resolved());
    }
}
