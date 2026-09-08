package com.hoshino.gregsteamexpansion.machine.multiblock.processor;

import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.sound.SoundEntry;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.gregtechceu.gtceu.common.data.GTSoundEntries;

import net.minecraft.MethodsReturnNonnullByDefault;

import javax.annotation.ParametersAreNonnullByDefault;

/**
 * Shared base of the centrifuge pair (steam-centrifuges.md 议题 1, crusher
 * family precedent): both machines run ONLY the {@code gtceu:centrifuge}
 * recipe type (议题 2 — the thermal centrifuge route stays upstream) behind
 * the LV voltage gate, and carry the family's widest fluid interface before
 * B1/B2 — fluid input AND output hatches are required, and BOTH families are
 * admissible (GTCEu standard hatches or the mod's steam fluid hatches,
 * 可选或混用, 议题 3; the steam fluid output hatch gains its first legal
 * consumer here, non-mandatory).
 *
 * <p>Family rules via {@link AbstractSteamProcessorMachine}: ×1.5 duration,
 * 2 mB steam per EU with per-tick atomic withdrawal (1,200 mB/t per supply
 * hatch), LV voltage gate, last-successful-recipe preference, worst-case
 * output precheck (up to 6 item + 6 fluid slots for this type), atomic
 * inputs, persisted pending outputs, steam-shortage rollback to 1 tick,
 * linear steam demand scaling with parallel (议题 5, 粉碎机家族固定线性).</p>
 */
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public abstract class AbstractSteamCentrifugeMachine extends AbstractSteamProcessorMachine {

    protected AbstractSteamCentrifugeMachine(IMachineBlockEntity holder) {
        super(holder);
    }

    @Override
    public GTRecipeType recipeType() {
        // 议题 2: 仅 gtceu:centrifuge (通用离心分离), 热离心类型保留上游.
        return GTRecipeTypes.CENTRIFUGE_RECIPES;
    }

    @Override
    protected boolean requiresFluidInput() {
        // 议题 4 共同口径: 流体输入仓 ≥1 (类型最多 1 个流体输入槽).
        return true;
    }

    @Override
    protected boolean requiresFluidOutput() {
        // 议题 3/4: 流体输出仓 ≥1 (类型最多 6 个流体输出槽), 双家族可选或混用.
        return true;
    }

    @Override
    protected boolean allowsAirIntake() {
        // 议题 12: 双机接受蒸汽进气室 (可选 0 或 1).
        return true;
    }

    @Override
    protected int maximumAirIntakes() {
        // 议题 12: 每台最多 1 个 (用户 2026-09-08 定).
        return 1;
    }

    @Override
    protected SoundEntry workingSoundEntry() {
        // 议题 9: 沿用类型自带离心声效.
        return GTSoundEntries.CENTRIFUGE;
    }
}
