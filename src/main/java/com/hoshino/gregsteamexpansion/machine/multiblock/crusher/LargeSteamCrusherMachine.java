package com.hoshino.gregsteamexpansion.machine.multiblock.crusher;

import com.gregtechceu.gtceu.api.pattern.BlockPattern;
import com.hoshino.gregsteamexpansion.registry.GSECrusherPatterns;

import net.minecraft.MethodsReturnNonnullByDefault;

import javax.annotation.ParametersAreNonnullByDefault;

/**
 * 大型蒸汽粉碎机 / Large Steam Crusher controller (steam-crushers.md 大型蒸汽
 * 粉碎机结构): fixed 7×7×9 cylinder-and-drill. The cylinder keeps at least 110
 * bronze steam machine casings among its 127 candidate positions, the drill is
 * fully fixed, and exactly one Steam Exhaust Hatch is required — its blockage
 * freezes progress instead of the 1-tick steam rollback, and its damage cycle
 * runs on accumulated actually-running ticks.
 */
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class LargeSteamCrusherMachine extends AbstractSteamCrusherMachine {

    public LargeSteamCrusherMachine(com.gregtechceu.gtceu.api.machine.IMachineBlockEntity holder) {
        super(holder);
    }

    @Override
    public int maximumParallel() {
        return 64;
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
    protected int minimumCasings() {
        return 110;
    }

    @Override
    protected int candidatePositions() {
        return 127;
    }

    @Override
    public BlockPattern getPattern() {
        return GSECrusherPatterns.createLarge(getDefinition());
    }
}
