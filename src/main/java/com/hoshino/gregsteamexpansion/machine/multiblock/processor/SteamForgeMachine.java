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
 * 蒸汽锻压机 / Steam Forge controller (steam-forge.md): 3×3×5 tower — two
 * full 3×3 forge-anvil layers (layer 2 centre holds the Steam Assembly Block)
 * plus three hammer-row layers carrying only the depth-centre row. Pure dry
 * 1→1 type (gtceu:forge_hammer, ORE_FORGING included), parallel cap 8, LV
 * voltage gate, no exhaust hatch.
 */
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class SteamForgeMachine extends AbstractSteamProcessorMachine {

    public SteamForgeMachine(IMachineBlockEntity holder) {
        super(holder);
    }

    @Override
    public GTRecipeType recipeType() {
        return GTRecipeTypes.FORGE_HAMMER_RECIPES;
    }

    @Override
    public int maximumParallel() {
        return 8;
    }

    @Override
    protected com.gregtechceu.gtceu.api.sound.SoundEntry workingSoundEntry() {
        // 锻压声效沿用类型自带样式 (议题 9): FORGE_HAMMER strike sound.
        return GTSoundEntries.FORGE_HAMMER;
    }

    @Override
    public BlockPattern getPattern() {
        return GSEProcessorPatterns.createForge(getDefinition());
    }
}
