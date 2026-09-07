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
 * 大型蒸汽组装机 / Large Steam Assembler controller (large-steam-assembler.md,
 * B1): fixed 9×9×9 structure — industrial-cased top/bottom faces plus the 12
 * edges, steam-machine-casing walls (the only hatch-replaceable zone) and an
 * 18-block Steam Assembly Block array on interior layers 2 and 8 — running
 * the full {@code gtceu:assembler} recipe type through the assembler-slot
 * mechanism (empty slot = ULV only; 1–4 stacked electric assemblers unlock
 * their tier and set the 2 / 4 / 8 / 16 parallel cap).
 */
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class LargeSteamAssemblerMachine extends AbstractSteamAssemblerMachine {

    public LargeSteamAssemblerMachine(IMachineBlockEntity holder) {
        super(holder);
    }

    @Override
    public GTRecipeType recipeType() {
        // 议题 2: 仅 gtceu:assembler, 不越界装配线与电路组装机.
        return GTRecipeTypes.ASSEMBLER_RECIPES;
    }

    @Override
    protected MachineDefinition[] assemblerTiers() {
        // 议题 3: 槽位只收电力组装机 LV–EV 四级.
        return GTMachines.ASSEMBLER;
    }

    @Override
    public BlockPattern getPattern() {
        return GSEProcessorPatterns.createLargeSteamAssembler(getDefinition());
    }
}
