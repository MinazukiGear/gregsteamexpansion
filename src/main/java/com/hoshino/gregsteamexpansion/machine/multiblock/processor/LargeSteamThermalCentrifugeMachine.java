package com.hoshino.gregsteamexpansion.machine.multiblock.processor;

import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.pattern.BlockPattern;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.sound.SoundEntry;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.gregtechceu.gtceu.common.data.GTSoundEntries;
import com.hoshino.gregsteamexpansion.registry.GSEProcessorPatterns;

import net.minecraft.MethodsReturnNonnullByDefault;

import javax.annotation.ParametersAreNonnullByDefault;

/**
 * 大型蒸汽热力离心机 / Large Steam Thermal Centrifuge controller
 * (large-steam-thermal-centrifuge.md): fixed 9×9×7 stepped tower — an
 * industrial-cased base whose bottom layer carries a 7×7 bronze firebox
 * hearth, a full-casing top face and a solid 5×5 industrial casing crown —
 * running the full {@code gtceu:thermal_centrifuge} recipe type at up to 64
 * parallel.
 *
 * <p>Family rules via {@link AbstractSteamProcessorMachine}: ×1.5 duration,
 * 2 mB steam per EU with per-tick atomic withdrawal (1,200 mB/t per supply
 * hatch), LV voltage gate, last-successful-recipe preference, worst-case
 * output precheck, atomic inputs, persisted pending outputs, steam-shortage
 * rollback to 1 tick. Machine-specific: exactly one Steam Exhaust Hatch whose
 * obstruction freezes progress (no rollback) and whose hazard cycles run on
 * actually-consuming ticks; the type is pure-dry (1 item in / 3 items out), so
 * no fluid hatches exist anywhere in the structure (议题 3/4 仓室 0).</p>
 */
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class LargeSteamThermalCentrifugeMachine extends AbstractSteamProcessorMachine {

    public LargeSteamThermalCentrifugeMachine(IMachineBlockEntity holder) {
        super(holder);
    }

    @Override
    public GTRecipeType recipeType() {
        return GTRecipeTypes.THERMAL_CENTRIFUGE_RECIPES;
    }

    @Override
    public int maximumParallel() {
        return 64;
    }

    @Override
    protected boolean allowsSteamFluidHatches() {
        // 议题 4 仓室表: 类型纯干式, 流体仓为 0 — 图案本就不放行任何流体仓,
        // 此处复核兜底, 蒸汽流体仓同样不可出现.
        return false;
    }

    @Override
    protected boolean requiresExhaustHatch() {
        return true;
    }

    @Override
    protected boolean hasExhaustHazard() {
        return true;
    }

    @Override
    protected SoundEntry workingSoundEntry() {
        // 议题 9: 上游 thermal_centrifuge 类型自带声效即离心声效
        // (GTRecipeTypes#THERMAL_CENTRIFUGE_RECIPES setSound CENTRIFUGE).
        return GTSoundEntries.CENTRIFUGE;
    }

    @Override
    public BlockPattern getPattern() {
        return GSEProcessorPatterns.createThermalCentrifuge(getDefinition());
    }
}
