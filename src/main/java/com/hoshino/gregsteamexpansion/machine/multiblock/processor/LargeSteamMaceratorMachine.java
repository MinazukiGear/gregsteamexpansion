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
 * 大型蒸汽研磨厂 / Large Steam Macerator controller
 * (large-steam-macerator.md): fixed spherical structure inside a 7×7×7
 * bounding box — a 98-block bronze steam machine casing shell (97 casings +
 * the front-equator-centre controller) around a six-armed cross of 13 Steam
 * Grinding Blocks and 68 blocks of interior air — running the full
 * {@code gtceu:macerator} recipe type (including {@code MaceratorLogic}
 * dynamic tool/turbine-rotor breakdowns) at up to 64 parallel.
 *
 * <p>Family rules via {@link AbstractSteamProcessorMachine}: ×1.5 duration,
 * 2 mB steam per EU with per-tick atomic withdrawal (1,200 mB/t per supply
 * hatch), LV voltage gate, last-successful-recipe preference, worst-case
 * output precheck (the type allows up to 4 item outputs incl. 14% chance
 * byproducts), atomic inputs, persisted pending outputs, steam-shortage
 * rollback to 1 tick. Machine-specific: exactly one Steam Exhaust Hatch whose
 * obstruction freezes progress (no rollback) and whose hazard cycles run on
 * actually-consuming ticks; the type is pure-dry (0 fluid slots), so no fluid
 * hatches exist anywhere in the structure (议题 3/4 仓室 0).</p>
 */
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class LargeSteamMaceratorMachine extends AbstractSteamProcessorMachine {

    public LargeSteamMaceratorMachine(IMachineBlockEntity holder) {
        super(holder);
    }

    @Override
    public GTRecipeType recipeType() {
        return GTRecipeTypes.MACERATOR_RECIPES;
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
        // 议题 9: 上游 macerator 类型自带声效即研磨声效
        // (GTRecipeTypes#MACERATOR_RECIPES setSound MACERATOR).
        return GTSoundEntries.MACERATOR;
    }

    @Override
    public BlockPattern getPattern() {
        return GSEProcessorPatterns.createMacerator(getDefinition());
    }
}
