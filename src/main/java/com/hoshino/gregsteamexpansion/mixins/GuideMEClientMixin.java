package com.hoshino.gregsteamexpansion.mixins;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.config.ConfigTracker;
import net.minecraftforge.fml.loading.FMLEnvironment;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * GuideME 20.1.7 prepares guide resources concurrently with Forge's client
 * config loading. Its translation flag can therefore be read before its spec
 * is loaded. Match Forge's production default for this one early development
 * read without disabling config validation or adding a GuideME dependency.
 */
@Pseudo
@Mixin(targets = "guideme.internal.GuideMEClient", remap = false)
public abstract class GuideMEClientMixin {

    // An optional mod's internal API may change; never make its renamed or
    // removed flag a new production startup requirement for GSE.
    @Redirect(method = "isIgnoreTranslatedGuides", remap = false, require = 0,
            at = @At(value = "INVOKE",
                     target = "Lnet/minecraftforge/common/ForgeConfigSpec$BooleanValue;get()Ljava/lang/Object;"))
    private Object gse$readTranslationFlag(ForgeConfigSpec.BooleanValue value) {
        if (!FMLEnvironment.production) {
            var config = ConfigTracker.INSTANCE.fileMap().get("guideme.toml");
            if (config != null && config.getModId().equals("guideme")
                    && config.getSpec() instanceof ForgeConfigSpec spec && !spec.isLoaded()) {
                return value.getDefault();
            }
        }
        return value.get();
    }
}
