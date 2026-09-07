package com.hoshino.gregsteamexpansion.machine.multiblock.processor;

import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.pattern.BlockPattern;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.gregtechceu.gtceu.common.data.GTSoundEntries;
import com.hoshino.gregsteamexpansion.registry.GSEProcessorPatterns;

import net.minecraft.MethodsReturnNonnullByDefault;

import javax.annotation.ParametersAreNonnullByDefault;

/**
 * 蒸汽提取机 / Steam Extractor controller (steam-extractor.md): fixed 3×3×3
 * with a single bronze pipe casing as the extraction core at the structure
 * centre. One item in, one item and/or fluid out (gtceu:extractor carries 1
 * fluid output slot), parallel cap 8, LV voltage gate, no exhaust hatch.
 */
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class SteamExtractorMachine extends AbstractSteamProcessorMachine {

    public SteamExtractorMachine(IMachineBlockEntity holder) {
        super(holder);
    }

    @Override
    public GTRecipeType recipeType() {
        return GTRecipeTypes.EXTRACTOR_RECIPES;
    }

    @Override
    public int maximumParallel() {
        return 8;
    }

    @Override
    protected int maximumInterfaces() {
        // 仓室合计 ≤ 8: 25 个可替换位中至少保留 17 个青铜蒸汽机械方块 (议题 4).
        return 8;
    }

    @Override
    protected boolean requiresFluidOutput() {
        // 类型自带 1 个流体输出槽 (议题 3/4): at least one fluid output hatch
        // (GTCEu standard or the mod's steam fluid output hatch) is required.
        return true;
    }

    @Override
    protected com.gregtechceu.gtceu.api.sound.SoundEntry workingSoundEntry() {
        // 提取声效沿用类型自带样式 (议题 9): the extractor recipe type defines
        // no sound of its own; the pump-like MOTOR entry is the closest match.
        return GTSoundEntries.MOTOR;
    }

    @Override
    public BlockPattern getPattern() {
        return GSEProcessorPatterns.createExtractor(getDefinition());
    }
}
