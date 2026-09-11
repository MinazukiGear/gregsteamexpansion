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
 * 蒸汽化学浸洗厂 / Steam Chemical Bath controller
 * (large-steam-chemical-bath.md, B4): fixed 3×4×3 structure — a 34-block
 * industrial steam machine casing shell (incl. the front-bottom-centre
 * controller) around 2 blocks of strict air forming the immersion chamber in
 * the middle layer's centre column — running the full
 * {@code gtceu:chemical_bath} recipe type at up to 8 parallel.
 *
 * <p>Family rules via {@link AbstractSteamProcessorMachine}: ×1.5 duration,
 * 2 mB steam per EU with per-tick atomic withdrawal (1,200 mB/t per supply
 * hatch), LV voltage gate, last-successful-recipe preference, worst-case
 * output precheck, atomic inputs, persisted pending outputs, steam-shortage
 * rollback to 1 tick. Machine-specific (议题 3/4): NO steam exhaust hatch —
 * 非大型蒸汽多方块不使用排气仓, so there is no exhaust obstruction or
 * heat-hazard cycle at all; NO fluid output hatch of either family — recipes
 * that produce fluids have nowhere to deliver them and fail the startup
 * precheck (议题 3 连带后果); the required fluid input hatch may be a GTCEu
 * standard hatch or the mod's steam fluid input hatch, freely mixed
 * (steam fluid hatches admissible via the base default
 * {@code allowsSteamFluidHatches()}).</p>
 */
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class SteamChemicalBathMachine extends AbstractSteamProcessorMachine {

    public SteamChemicalBathMachine(IMachineBlockEntity holder) {
        super(holder);
    }

    @Override
    public GTRecipeType recipeType() {
        return GTRecipeTypes.CHEMICAL_BATH_RECIPES;
    }

    @Override
    public int maximumParallel() {
        // 议题 6: 固定 8, 与蒸汽粉碎机同级; 不由接口数量或环境动态计算.
        return 8;
    }

    @Override
    protected boolean requiresFluidInput() {
        // 议题 4 仓室表: 流体输入仓 ≥1 (浸洗液入口, 流体按配方声明原样兼容).
        return true;
    }

    @Override
    protected SoundEntry workingSoundEntry() {
        // 议题 9: 类型自带 BATH 声, 与 S1 洗矿厂/上游药浴机同源.
        return GTSoundEntries.BATH;
    }

    @Override
    public BlockPattern getPattern() {
        return GSEProcessorPatterns.createChemicalBath(getDefinition());
    }
}
