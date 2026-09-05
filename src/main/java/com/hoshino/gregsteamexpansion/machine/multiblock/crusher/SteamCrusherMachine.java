package com.hoshino.gregsteamexpansion.machine.multiblock.crusher;

import com.gregtechceu.gtceu.api.pattern.BlockPattern;
import com.hoshino.gregsteamexpansion.registry.GSECrusherPatterns;

import net.minecraft.MethodsReturnNonnullByDefault;

import javax.annotation.ParametersAreNonnullByDefault;

/**
 * 蒸汽粉碎机 / Steam Crusher controller (steam-crushers.md 蒸汽粉碎机结构):
 * fixed 3×3×3 with one bronze frame core, four steam grinding blocks on the
 * top/bottom/left/right face centres and 21 unified candidate positions. No
 * exhaust hatch is required or allowed; the parallel cap is 8.
 */
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class SteamCrusherMachine extends AbstractSteamCrusherMachine {

    public SteamCrusherMachine(com.gregtechceu.gtceu.api.machine.IMachineBlockEntity holder) {
        super(holder);
    }

    @Override
    public int maximumParallel() {
        return 8;
    }

    @Override
    protected boolean requiresExhaustHatch() {
        return false;
    }

    @Override
    protected int minimumCasings() {
        return 18;
    }

    @Override
    protected int candidatePositions() {
        return 21;
    }

    @Override
    public BlockPattern getPattern() {
        return GSECrusherPatterns.createSmall(getDefinition());
    }
}
