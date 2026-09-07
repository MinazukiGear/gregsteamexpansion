package com.hoshino.gregsteamexpansion.machine.multiblock.processor;

import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.pattern.BlockPattern;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.hoshino.gregsteamexpansion.registry.GSEProcessorPatterns;

import net.minecraft.MethodsReturnNonnullByDefault;

import javax.annotation.ParametersAreNonnullByDefault;

/**
 * 蒸汽压缩机 / Steam Compressor controller (steam-compressor.md):
 * fixed 3×3×3 with a bronze-frame compression core at the center and a vanilla
 * piston on the back wall centre pointing at the frame. Item-in/item-out only
 * (pure dry type), parallel cap 8, LV voltage gate, no exhaust hatch.
 */
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class SteamCompressorMachine extends AbstractSteamProcessorMachine {

    public SteamCompressorMachine(IMachineBlockEntity holder) {
        super(holder);
    }

    @Override
    public GTRecipeType recipeType() {
        return GTRecipeTypes.COMPRESSOR_RECIPES;
    }

    @Override
    public int maximumParallel() {
        return 8;
    }

    @Override
    protected int maximumInterfaces() {
        // 仓室合计 ≤ 8: 24 个可替换位中至少保留 16 个青铜蒸汽机械方块 (议题 4).
        return 8;
    }

    @Override
    public BlockPattern getPattern() {
        return GSEProcessorPatterns.createCompressor(getDefinition());
    }
}
