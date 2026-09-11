package com.hoshino.gregsteamexpansion.machine.multiblock.processor;

import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.pattern.BlockPattern;
import com.hoshino.gregsteamexpansion.registry.GSEProcessorPatterns;

import net.minecraft.MethodsReturnNonnullByDefault;

import javax.annotation.ParametersAreNonnullByDefault;

/**
 * 大型蒸汽离心机 / Large Steam Centrifuge controller (steam-centrifuges.md,
 * C0 large machine): fixed 7×7×9 vertical separation tower — 37-block full
 * disc crowns top and bottom (73 bronze steam machine casings + the
 * front-bottom-centre controller), seven ring layers (16 ring cells each)
 * around a 7-block Steam Mixing Block central axis flanked by two bronze
 * pipe casing columns (x=3/5, z=4) — running {@code gtceu:centrifuge}
 * recipes at up to 64 parallel.
 *
 * <p>Machine-specific: exactly one Steam Exhaust Hatch whose obstruction
     * freezes progress and whose hazard cycles run on actually-consuming ticks.
     * Ring candidates have no casing minimum or total-interface cap.</p>
 */
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class LargeSteamCentrifugeMachine extends AbstractSteamCentrifugeMachine {

    public LargeSteamCentrifugeMachine(IMachineBlockEntity holder) {
        super(holder);
    }

    @Override
    public int maximumParallel() {
        // 议题 6: 固定 64, 与大型蒸汽粉碎机/洗矿厂/研磨厂同级.
        return 64;
    }

    @Override
    protected boolean requiresExhaustHatch() {
        // 议题 4: 排气仓仅大型机安装且必须且只能 1 个.
        return true;
    }

    @Override
    protected boolean hasExhaustHazard() {
        // 议题 4/8: 排气反馈 + 热伤害, 通用排气仓规则.
        return true;
    }

    @Override
    public BlockPattern getPattern() {
        return GSEProcessorPatterns.createLargeCentrifuge(getDefinition());
    }
}
