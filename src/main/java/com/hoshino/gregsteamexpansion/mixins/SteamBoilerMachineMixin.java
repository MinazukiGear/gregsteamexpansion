package com.hoshino.gregsteamexpansion.mixins;

import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.steam.SteamBoilerMachine;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableFluidTank;
import com.gregtechceu.gtceu.api.transfer.fluid.CustomFluidTank;
import com.hoshino.gregsteamexpansion.difficulty.GSEDifficultyState;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;

import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Applies the confirmed upstream coverage entries to GTCEu single-block steam
 * boilers (difficulty.md 上游模组设置覆盖): steam output x5/5/2 — every GUI and
 * data-integration path reads it through getTotalSteamOutput, so displays show
 * the applied value — water consumption following the amplified output at the
 * upstream water-to-steam ratio, and the single-block steam tank capacity
 * x2/1/1. The solar boiler overrides updateCurrentTemperature but calls this
 * base implementation via super, so it is covered as well.
 */
@Mixin(SteamBoilerMachine.class)
public abstract class SteamBoilerMachineMixin {

    // All targets below are GTCEu classes: they are never SRG-renamed at
    // runtime, so remap = false keeps the annotation processor from looking
    // for obfuscation mappings that cannot exist for mod methods.
    @Inject(method = "<init>", at = @At("TAIL"), remap = false)
    private void gse$applySteamTankCapacityOnCreate(IMachineBlockEntity holder, boolean isHighPressure,
                                                    Object[] args, CallbackInfo ci) {
        gse$applySteamTankCapacity();
    }

    @Inject(method = "onLoad", at = @At("TAIL"), remap = false)
    private void gse$applySteamTankCapacityOnLoad(CallbackInfo ci) {
        // CustomFluidTank NBT round-trips Capacity, so a save load restores the
        // original value; re-apply the multiplier after deserialization. The
        // divide-then-multiply keeps the operation idempotent.
        gse$applySteamTankCapacity();
    }

    @Unique
    private void gse$applySteamTankCapacity() {
        SteamBoilerMachine self = (SteamBoilerMachine) (Object) this;
        int multiplier = GSEDifficultyState.current(self.isRemote()).getSingleblockSteamCacheMultiplier();
        for (CustomFluidTank storage : self.steamTank.getStorages()) {
            storage.setCapacity(storage.getCapacity() / multiplier * multiplier);
        }
    }

    @ModifyExpressionValue(method = "getTotalSteamOutput", remap = false,
            at = @At(value = "INVOKE",
                     target = "Lcom/gregtechceu/gtceu/api/machine/steam/SteamBoilerMachine;getBaseSteamOutput()J"))
    private long gse$multiplySteamOutput(long original) {
        SteamBoilerMachine self = (SteamBoilerMachine) (Object) this;
        return (long) (original * GSEDifficultyState.current(self.isRemote()).getSteamOutputMultiplier());
    }

    @Redirect(method = "updateCurrentTemperature", remap = false,
            at = @At(value = "INVOKE",
                     target = "Lcom/gregtechceu/gtceu/api/machine/trait/NotifiableFluidTank;drainInternal(ILnet/minecraftforge/fluids/capability/IFluidHandler$FluidAction;)Lnet/minecraftforge/fluids/FluidStack;"))
    private FluidStack gse$multiplyWaterDrain(NotifiableFluidTank tank, int amount,
                                              IFluidHandler.FluidAction action) {
        // Only the water drain scales; the same method also drains the steam
        // tank when venting a blocked output.
        SteamBoilerMachine self = (SteamBoilerMachine) (Object) this;
        if (tank == self.waterTank) {
            float multiplier = GSEDifficultyState.current(self.isRemote()).getSteamOutputMultiplier();
            amount = Math.max(amount, Math.round(amount * multiplier));
        }
        return tank.drainInternal(amount, action);
    }
}
