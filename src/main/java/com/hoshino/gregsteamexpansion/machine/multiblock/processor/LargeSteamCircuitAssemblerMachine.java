package com.hoshino.gregsteamexpansion.machine.multiblock.processor;

import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.pattern.BlockPattern;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.api.transfer.item.CustomItemStackHandler;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.widget.SlotWidget;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.common.data.GTMachines;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.gregtechceu.gtceu.data.recipe.CustomTags;
import com.hoshino.gregsteamexpansion.difficulty.GSEDifficultyState;
import com.hoshino.gregsteamexpansion.machine.multiblock.PendingOutputBuffer;
import com.hoshino.gregsteamexpansion.registry.GSEProcessorPatterns;
import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import javax.annotation.ParametersAreNonnullByDefault;

import java.util.List;

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

    protected static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(
            LargeSteamCircuitAssemblerMachine.class, AbstractSteamAssemblerMachine.MANAGED_FIELD_HOLDER);

    private static final double SPECIALIZATION_COST_MULTIPLIER = 1.5;
    private static final String SPECIALIZATION_UI_PREFIX =
            "gregsteamexpansion.machine.large_steam_circuit_assembler.specialization.";

    /** Physical, non-consumed reference circuit. */
    @Persisted
    private final CustomItemStackHandler specializationSlot = new SpecializationSlotHandler();
    /** Batch-locked target and settings prevent slot/config changes from rerolling active work. */
    @Persisted
    private ItemStack batchSpecializationCircuit = ItemStack.EMPTY;
    @Persisted
    private int batchSpecializationChancePercent;
    @Persisted
    private int batchSpecializationMultiplier;

    public LargeSteamCircuitAssemblerMachine(IMachineBlockEntity holder) {
        super(holder);
        specializationSlot.setFilter(stack -> stack.is(CustomTags.CIRCUITS));
        specializationSlot.setOnContentsChanged(() -> {
            markDirty();
            requestRecipeSearch();
        });
    }

    private static final class SpecializationSlotHandler extends CustomItemStackHandler {

        private SpecializationSlotHandler() {
            super(1);
        }

        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }
    }

    @Override
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
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

    /** Specialization increases elapsed time and therefore total steam, but not the per-tick rate. */
    @Override
    protected long batchDurationTicks(GTRecipe recipe, int parallel) {
        long duration = super.batchDurationTicks(recipe, parallel);
        return matchingTargetOutput(recipe).isEmpty()
                ? duration
                : Math.max(1, (long) Math.ceil(duration * SPECIALIZATION_COST_MULTIPLIER));
    }

    @Override
    protected List<ItemStack> additionalWorstCaseItemOutputs(GTRecipe recipe) {
        ItemStack target = matchingTargetOutput(recipe);
        int chance = GSEDifficultyState.circuitAssemblerBonusChancePercent(isRemote());
        if (target.isEmpty() || chance <= 0) {
            return List.of();
        }
        target.setCount(Math.multiplyExact(target.getCount(),
                GSEDifficultyState.circuitAssemblerBonusMultiplier(isRemote())));
        return List.of(target);
    }

    @Override
    protected void onBatchStarted(GTRecipe recipe, int parallel) {
        batchSpecializationCircuit = matchingTargetOutput(recipe);
        if (batchSpecializationCircuit.isEmpty()) {
            batchSpecializationChancePercent = 0;
            batchSpecializationMultiplier = 0;
            return;
        }
        batchSpecializationChancePercent = GSEDifficultyState.circuitAssemblerBonusChancePercent(isRemote());
        batchSpecializationMultiplier = GSEDifficultyState.circuitAssemblerBonusMultiplier(isRemote());
    }

    @Override
    protected void appendAdditionalBatchOutputs(GTRecipe recipe, int parallel, List<ItemStack> produced) {
        if (batchSpecializationCircuit.isEmpty() || batchSpecializationChancePercent <= 0
                || batchSpecializationMultiplier <= 0 || !specializationRollSucceeds()) {
            return;
        }
        long remaining = (long) batchSpecializationCircuit.getCount()
                * parallel * batchSpecializationMultiplier;
        while (remaining > 0) {
            int count = (int) Math.min(batchSpecializationCircuit.getMaxStackSize(), remaining);
            produced.add(batchSpecializationCircuit.copyWithCount(count));
            remaining -= count;
        }
    }

    private boolean specializationRollSucceeds() {
        if (batchSpecializationChancePercent >= 100) {
            return true;
        }
        Level level = getLevel();
        return level != null && level.random.nextInt(100) < batchSpecializationChancePercent;
    }

    @Override
    protected void onBatchCleared() {
        batchSpecializationCircuit = ItemStack.EMPTY;
        batchSpecializationChancePercent = 0;
        batchSpecializationMultiplier = 0;
    }

    /** First guaranteed item output that exactly matches the installed reference circuit. */
    private ItemStack matchingTargetOutput(GTRecipe recipe) {
        ItemStack selected = specializationSlot.getStackInSlot(0);
        if (selected.isEmpty()) {
            return ItemStack.EMPTY;
        }
        List<Content> outputs = recipe.outputs.get(ItemRecipeCapability.CAP);
        if (outputs == null) {
            return ItemStack.EMPTY;
        }
        for (Content content : outputs) {
            if (content.chance < content.maxChance) {
                continue;
            }
            ItemStack output = PendingOutputBuffer.materializeItem(content);
            if (!output.isEmpty() && ItemStack.isSameItemSameTags(selected, output)) {
                return output;
            }
        }
        return ItemStack.EMPTY;
    }

    public CustomItemStackHandler getSpecializationSlotHandler() {
        return specializationSlot;
    }

    public ItemStack getSpecializationCircuit() {
        return specializationSlot.getStackInSlot(0);
    }

    @Override
    public ModularUI createUI(Player entityPlayer) {
        ModularUI ui = super.createUI(entityPlayer);
        ui.widget(new SlotWidget(specializationSlot, 0, 224, 146)
                .setBackgroundTexture(GuiTextures.SLOT)
                .setHoverTooltips(
                        Component.translatable(SPECIALIZATION_UI_PREFIX + "slot"),
                        Component.translatable(SPECIALIZATION_UI_PREFIX + "settings",
                                GSEDifficultyState.circuitAssemblerBonusChancePercent(isRemote()),
                                GSEDifficultyState.circuitAssemblerBonusMultiplier(isRemote()))));
        return ui;
    }

    @Override
    public void onMachineRemoved() {
        ItemStack stack = specializationSlot.getStackInSlot(0);
        if (!stack.isEmpty()) {
            specializationSlot.setStackInSlot(0, ItemStack.EMPTY);
            Level level = getLevel();
            if (level != null && !isRemote()) {
                Block.popResource(level, getPos(), stack);
            }
        }
        super.onMachineRemoved();
    }

    @Override
    public BlockPattern getPattern() {
        return GSEProcessorPatterns.createLargeSteamCircuitAssembler(getDefinition());
    }
}
