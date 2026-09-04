package com.hoshino.gregsteamexpansion.mixins;

import com.hoshino.gregsteamexpansion.client.GSEDifficultyDisplay;
import com.hoshino.gregsteamexpansion.client.GSEDifficultySaveReader;
import com.hoshino.gregsteamexpansion.difficulty.Difficulty;
import net.minecraft.client.gui.screens.worldselection.WorldSelectionList;
import net.minecraft.world.level.storage.LevelSummary;

import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;

/**
 * Tags world list entries with the save's difficulty right after the save
 * name, colored green (Easy) / yellow (Normal) / red (Expert). The tier is
 * read once per entry from the save folder's difficulty file; saves without
 * one are left untagged.
 */
@Mixin(WorldSelectionList.WorldListEntry.class)
public abstract class WorldListEntryMixin {

    @Unique
    @Nullable
    private Difficulty gse$difficulty;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void gse$readSaveDifficulty(WorldSelectionList outer, WorldSelectionList list,
                                        LevelSummary summary, CallbackInfo ci) {
        this.gse$difficulty = GSEDifficultySaveReader.read(summary.getLevelId());
    }

    @ModifyExpressionValue(method = "render",
            at = @At(value = "INVOKE",
                     target = "Lnet/minecraft/world/level/storage/LevelSummary;getLevelName()Ljava/lang/String;"))
    private String gse$appendDifficultyTag(String name) {
        return this.gse$difficulty == null
                ? name
                : name + GSEDifficultyDisplay.worldListSuffix(this.gse$difficulty);
    }
}
