package com.hoshino.gregsteamexpansion.machine.multiblock.processor;

import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.pattern.BlockPattern;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.common.data.GTMachines;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.hoshino.gregsteamexpansion.registry.GSEProcessorPatterns;

import net.minecraft.MethodsReturnNonnullByDefault;

import javax.annotation.ParametersAreNonnullByDefault;

/**
 * 大型蒸汽电路组装机 / Large Steam Circuit Assembler controller
 * (large-steam-circuit-assembler.md, B2): fixed 5×11×6 + centre ridge
 * structure — steam-machine-casing shell (hatch-replaceable) around the
 * vertical process tower (circuit assembly blocks / bronze gearbox /
 * assembly blocks / bronze pipe casings, 9 each), industrial ridge on top —
 * running the full {@code gtceu:circuit_assembler} recipe type through the
 * same assembler-slot mechanism as B1. Every circuit assembler recipe
 * carries a mandatory fluid input (solder), so a fluid input hatch is a
 * first-class formation requirement.
 */
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class LargeSteamCircuitAssemblerMachine extends AbstractSteamAssemblerMachine {

    public LargeSteamCircuitAssemblerMachine(IMachineBlockEntity holder) {
        super(holder);
    }

    @Override
    public GTRecipeType recipeType() {
        // 议题 2: 仅 gtceu:circuit_assembler, 不越界组装机与装配线.
        return GTRecipeTypes.CIRCUIT_ASSEMBLER_RECIPES;
    }

    @Override
    protected MachineDefinition[] assemblerTiers() {
        // 议题 3: 槽位只收电力电路组装机 LV–EV 四级 (镜像 B1).
        return GTMachines.CIRCUIT_ASSEMBLER;
    }

    @Override
    public BlockPattern getPattern() {
        return GSEProcessorPatterns.createLargeSteamCircuitAssembler(getDefinition());
    }
}
