package com.hoshino.gregsteamexpansion.mixins;

import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.ForgeRegistries;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 禁用上游蒸汽输入仓 (machines-and-hatches.md GTCEu 蒸汽输入仓的禁用范围):
 * exactly one block — {@code gtceu:steam_input_hatch} — is skipped when GTCEu
 * registers its machine blocks into part abilities, so it can never make any
 * {@code PartAbility.STEAM} structure form again. The replacement
 * {@code gregsteamexpansion:steam_supply_hatch} registers normally afterwards.
 *
 * <p>The skip matches the precise resource ID, never the steam hatch's class,
 * the ability itself or a name substring, and it runs during registration so
 * the legacy block never enters the ability map in the first place.</p>
 */
@Mixin(value = PartAbility.class, remap = false)
public abstract class PartAbilityMixin {

    @Inject(method = "register", at = @At("HEAD"), cancellable = true)
    private void gse$skipLegacySteamInputHatch(int tier, Block block, CallbackInfo ci) {
        ResourceLocation id = ForgeRegistries.BLOCKS.getKey(block);
        if (id != null && "gtceu".equals(id.getNamespace()) && "steam_input_hatch".equals(id.getPath())) {
            ci.cancel();
        }
    }
}
