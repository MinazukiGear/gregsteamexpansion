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
 * 大型蒸汽洗矿厂 / Large Steam Ore Washer controller (large-steam-ore-washer.md):
 * fixed 11×11×6 three-region shell with the 17-block mixing cross on layer 2,
 * running the full {@code gtceu:ore_washer} recipe type at up to 64 parallel.
 *
 * <p>Family rules via {@link AbstractSteamProcessorMachine}: ×1.5 duration,
 * 2 mB steam per EU with per-tick atomic withdrawal (1,200 mB/t per supply
 * hatch), LV voltage gate, last-successful-recipe preference, worst-case
 * output precheck, atomic inputs, persisted pending outputs, steam-shortage
 * rollback to 1 tick. Machine-specific: exactly one Steam Exhaust Hatch whose
 * obstruction freezes progress (no rollback) and whose hazard cycles run on
 * actually-consuming ticks; water enters ONLY through GTCEu standard fluid
 * input hatches — the mod's steam fluid hatches are rejected at formation
 * (议题 3).</p>
 */
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class LargeSteamOreWasherMachine extends AbstractSteamProcessorMachine {

    public LargeSteamOreWasherMachine(IMachineBlockEntity holder) {
        super(holder);
    }

    @Override
    public GTRecipeType recipeType() {
        return GTRecipeTypes.ORE_WASHER_RECIPES;
    }

    @Override
    public int maximumParallel() {
        return 64;
    }

    @Override
    protected boolean requiresFluidInput() {
        // 议题 3: 水仅经 GTCEu 标准流体输入仓进入.
        return true;
    }

    @Override
    protected boolean allowsSteamFluidHatches() {
        // 议题 3: 蒸汽流体输入/输出仓一律禁止.
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
        // 议题 9: GTCEu 药浴 (BATH) 声效, 与上游洗矿机/药浴机同源.
        return GTSoundEntries.BATH;
    }

    @Override
    public BlockPattern getPattern() {
        return GSEProcessorPatterns.createOreWasher(getDefinition());
    }
}
