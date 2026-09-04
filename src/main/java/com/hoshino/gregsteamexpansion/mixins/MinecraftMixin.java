package com.hoshino.gregsteamexpansion.mixins;

import com.hoshino.gregsteamexpansion.client.GSEDifficultyDisplay;
import com.hoshino.gregsteamexpansion.difficulty.GSEDifficultyState;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Appends the save's difficulty to the window title while a world is open,
 * right after the vanilla-computed title ("Minecraft* x.y.z — level name");
 * the tag shows once the server has synced the authoritative tier.
 */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {

    @Redirect(method = "updateTitle",
            at = @At(value = "INVOKE",
                     target = "Lcom/mojang/blaze3d/platform/Window;setTitle(Ljava/lang/String;)V"))
    private void gse$appendDifficultyToTitle(Window window, String title) {
        Minecraft minecraft = (Minecraft) (Object) this;
        if (minecraft.level != null && GSEDifficultyState.isClientTierSynced()) {
            window.setTitle(title + GSEDifficultyDisplay.titleSuffix(GSEDifficultyState.getClientDifficulty()));
        } else {
            window.setTitle(title);
        }
    }
}
