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
 * 大型蒸汽搅拌机 / Large Steam Mixer controller (large-steam-mixer.md): fixed
 * 7×5×5 two-zone structure — full industrial-casing bottom/top bands (the
 * controller sits front-bottom-centre) with industrial-edge columns and
 * steam-machine-casing walls around a 5×3×3 interior carrying 7 Steam Mixing
 * Blocks (axis column layers 2-4 + the equidistant impeller cross on layer
 * 3) — running the full {@code gtceu:mixer} recipe type at up to 16
 * parallel.
 *
 * <p>Family rules via {@link AbstractSteamProcessorMachine}: ×1.5 duration,
 * 2 mB steam per EU with per-tick atomic withdrawal (1,200 mB/t per supply
 * hatch), LV voltage gate, last-successful-recipe preference, worst-case
 * output precheck (1 item + 1 fluid slot), atomic inputs (up to 6 item + 2
 * fluid inputs), persisted pending outputs (items to buses, fluids to fluid
 * output hatches), steam-shortage rollback to 1 tick. Machine-specific:
 * exactly one Steam Exhaust Hatch whose obstruction freezes progress and
 * whose hazard cycles run on actually-consuming ticks; the widest fluid
 * interface of the family — fluid input AND output hatches are required, and
 * BOTH families are admissible (GTCEu standard or the mod's steam fluid
 * hatches, 可选或混用, B4/C0 口径).</p>
 */
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class LargeSteamMixerMachine extends AbstractSteamProcessorMachine {

    public LargeSteamMixerMachine(IMachineBlockEntity holder) {
        super(holder);
    }

    @Override
    public GTRecipeType recipeType() {
        return GTRecipeTypes.MIXER_RECIPES;
    }

    @Override
    public int maximumParallel() {
        // 议题 6: 梯度 小型 8 → 本机 16 → 洗矿/研磨 64.
        return 16;
    }

    @Override
    protected int maximumInterfaces() {
        // 仓室合计 (含排气仓) 最多 16 个 (议题 4 仓室表).
        return 16;
    }

    @Override
    protected boolean allowsSteamFluidHatches() {
        // 议题 4 仓室表: 流体输入/输出仓均可为 GTCEu 标准仓或本模组蒸汽流体仓,
        // 可选或混用 (B4/C0 口径).
        return true;
    }

    @Override
    protected boolean requiresFluidInput() {
        // 议题 4 仓室表: 流体输入仓 ≥1 (最多承载配方 2 个流体输入槽).
        return true;
    }

    @Override
    protected boolean requiresFluidOutput() {
        // 议题 4 仓室表: 流体输出仓 ≥1 (1 流体输出槽).
        return true;
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
        // 议题 9: 沿用类型自带 MIXER 声.
        return GTSoundEntries.MIXER;
    }

    @Override
    public BlockPattern getPattern() {
        return GSEProcessorPatterns.createMixer(getDefinition());
    }
}
