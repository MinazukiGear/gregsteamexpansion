package com.hoshino.gregsteamexpansion.machine.multiblock.processor;

import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.pattern.BlockPattern;
import com.hoshino.gregsteamexpansion.registry.GSEProcessorPatterns;

import net.minecraft.MethodsReturnNonnullByDefault;

import javax.annotation.ParametersAreNonnullByDefault;

/**
 * 蒸汽离心机 / Steam Centrifuge controller (steam-centrifuges.md, C0 small
 * machine): fixed 3×4×3 structure — a 33-block bronze steam machine casing
 * shell (incl. the front-bottom-centre controller) with the two interior
 * middle-layer cells filled by Steam Mixing Blocks (rotor pair, 填满内部无
 * 空气腔) — running {@code gtceu:centrifuge} recipes at up to 8 parallel.
 *
 * <p>小型机不使用排气仓 (2026-09-07 全模组裁定): no exhaust hatch is
 * admissible in the pattern and no obstruction/hazard logic applies.</p>
 */
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class SteamCentrifugeMachine extends AbstractSteamCentrifugeMachine {

    public SteamCentrifugeMachine(IMachineBlockEntity holder) {
        super(holder);
    }

    @Override
    public int maximumParallel() {
        // 议题 6: 固定 8, 与蒸汽粉碎机同级.
        return 8;
    }

    @Override
    protected int maximumInterfaces() {
        // 仓室合计最多 8 个 (议题 4, 小型机不使用排气仓).
        return 8;
    }

    @Override
    public BlockPattern getPattern() {
        return GSEProcessorPatterns.createCentrifuge(getDefinition());
    }
}
