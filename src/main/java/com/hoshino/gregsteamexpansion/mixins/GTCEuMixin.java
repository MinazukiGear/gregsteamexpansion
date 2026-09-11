package com.hoshino.gregsteamexpansion.mixins;

import com.hoshino.gregsteamexpansion.compat.GTCEuClientInstanceLookup;

import com.gregtechceu.gtceu.GTCEu;

import net.minecraftforge.fml.loading.FMLEnvironment;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Data-generation runs launch with the client dist but never create the
 * Minecraft instance, while GTCEu 7.5.3 dereferences it unconditionally in
 * {@code GTCEu.isClientThread} — and any {@code GTRecipeBuilder} JSON
 * serialization goes through {@code GTRegistries.builtinRegistry}. Treating a
 * missing instance as "not the client thread" routes datagen onto the same
 * frozen-registry path a dedicated server already exercises.
 *
 * <p>The Minecraft lookup is reflective and only attempted under the dist
 * check: a mixin handler that names {@code net.minecraft.client.Minecraft}
 * directly fails to apply on dedicated servers because Mixin resolves
 * referenced classes while patching {@code GTCEu}.</p>
 */
@Mixin(value = GTCEu.class, remap = false)
public abstract class GTCEuMixin {
    @Inject(method = "isClientThread", at = @At("HEAD"), cancellable = true)
    private static void gse$treatMissingClientAsServerThread(CallbackInfoReturnable<Boolean> cir) {
        if (FMLEnvironment.dist.isClient() && GTCEuClientInstanceLookup.isMissing()) {
            cir.setReturnValue(false);
        }
    }
}
