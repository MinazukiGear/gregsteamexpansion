package com.hoshino.gregsteamexpansion.machine.multiblock.processor;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.pattern.BlockPattern;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.transfer.item.CustomItemStackHandler;
import com.gregtechceu.gtceu.api.sound.SoundEntry;
import com.gregtechceu.gtceu.common.data.GTSoundEntries;
import com.gregtechceu.gtceu.utils.GTUtil;
import com.hoshino.gregsteamexpansion.difficulty.Difficulty;
import com.hoshino.gregsteamexpansion.difficulty.GSEDifficultyState;

import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.UITemplate;
import com.gregtechceu.gtceu.api.gui.widget.SlotWidget;
import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import com.lowdragmc.lowdraglib.gui.widget.LabelWidget;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;

import net.minecraft.ChatFormatting;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import javax.annotation.ParametersAreNonnullByDefault;

/**
 * 大型蒸汽组装机 / 大型蒸汽电路组装机 shared base (large-steam-assembler.md /
 * large-steam-circuit-assembler.md, B1/B2 议题 3–11): the S1-family processor
 * skeleton extended with the assembler-slot mechanism — a single controller
 * slot holding 1–4 stacked electric assemblers of ONE tier (LV–EV, never
 * mixed). The slot gates the runnable recipe tier (empty → ULV only) and sets
 * the parallel cap (1 / 2 / 4 / 8 / 16), while batch economics follow the
 * sub-linear ladder: per-tick steam ×1 / 1.25 / 1.5 / 1.75 / 2 and duration
 * ×1 / 1.5 / 2 / 2.5 / 3. Slot assemblers never provide EU — steam at 2 mB
 * per recipe EU remains the only energy. Easy difficulty doubles the item
 * output of new batches (议题 11); a running batch keeps its locked output.
 *
 * <p>Structure rules (议题 4): large machines carry exactly one Steam Exhaust
 * Hatch (freeze while obstructed, feedback + heat damage while running), the
 * recipe type's single fluid input slot requires a fluid input hatch (GTCEu
 * standard or the mod's steam fluid input hatch, freely mixed), and the
 * interface total is capped at 16.</p>
 */
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public abstract class AbstractSteamAssemblerMachine extends AbstractSteamProcessorMachine {

    protected static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(
            AbstractSteamAssemblerMachine.class, AbstractSteamProcessorMachine.MANAGED_FIELD_HOLDER);

    /** 单槽可堆叠上限 (议题 3): 1–4 assemblers of one tier. */
    public static final int MAX_SLOT_STACK = 4;

    private static final String SLOT_UI_PREFIX = "gregsteamexpansion.machine.steam_assembler.ui.";

    //////////////////////////////////////
    // ***** Persisted state ******//
    //////////////////////////////////////

    /**
     * 组装机槽位 (议题 3): single slot, same-tier stacks only, hard cap 4.
     * Persisted so the content survives chunk unload and world reload;
     * {@link #onMachineRemoved()} returns it as an item entity.
     */
    /**
     * 组装机槽位 (议题 3): single slot, same-tier stacks only, hard cap 4.
     * Persisted so the content survives chunk unload and world reload;
     * {@link #onMachineRemoved()} returns it as an item entity.
     *
     * <p>🚩 类型必须是 {@link CustomItemStackHandler}（它实现了 ldlib 的
     * {@code ITagSerializable}），不能直接用 Forge 的 {@code ItemStackHandler}：
     * ldlib 的同步字段注册表里没有 {@code ItemStackHandler} 的 payload，
     * 方块实体一被 {@code clearRemoved()}（放置 / 读档 / 邻块更新）就抛
     * {@code No payload found for class ItemStackHandler} 直接崩服。
     */
    @Persisted
    private final CustomItemStackHandler assemblerSlot = new AssemblerSlotHandler();

    protected AbstractSteamAssemblerMachine(IMachineBlockEntity holder) {
        super(holder);
        assemblerSlot.setFilter(stack -> assemblerTierOf(stack) > 0);
        assemblerSlot.setOnContentsChanged(() -> {
            markDirty();
            requestRecipeSearch();
        });
    }

    /** 单槽、硬上限 {@link #MAX_SLOT_STACK}。具名类而非匿名类，避免同步字段按匿名类型查 payload。 */
    private static final class AssemblerSlotHandler extends CustomItemStackHandler {

        private AssemblerSlotHandler() {
            super(1);
        }

        @Override
        public int getSlotLimit(int slot) {
            return MAX_SLOT_STACK;
        }
    }

    @Override
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }

    //////////////////////////////////////
    // ***** Slot mechanism ******//
    //////////////////////////////////////

    /** The tier-key parts this machine accepts: the electric assembler or circuit-assembler array. */
    protected abstract MachineDefinition[] assemblerTiers();

    /** Tier of a stack against {@link #assemblerTiers()}: LV–EV, or 0 when not an accepted kind. */
    private int assemblerTierOf(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0;
        }
        MachineDefinition[] tiers = assemblerTiers();
        for (int tier = GTValues.LV; tier <= GTValues.EV; tier++) {
            if (tier < tiers.length && tiers[tier] != null && stack.is(tiers[tier].getItem())) {
                return tier;
            }
        }
        return 0;
    }

    /** Current slot stack for UI display (may hold a corrupted non-assembler after upgrades). */
    public ItemStack getAssemblerStack() {
        return assemblerSlot.getStackInSlot(0);
    }

    /** Internal controller slot; exposed for UI wiring and behavior verification, never as a block capability. */
    public CustomItemStackHandler getAssemblerSlotHandler() {
        return assemblerSlot;
    }

    /** Slot tier: 0 (ULV-only) when empty; the stacked kind's tier otherwise. */
    public int getAssemblerTier() {
        return assemblerTierOf(assemblerSlot.getStackInSlot(0));
    }

    /** Stacked assembler count of an accepted kind; 0 when empty or invalid. */
    public int getAssemblerCount() {
        ItemStack stack = assemblerSlot.getStackInSlot(0);
        return assemblerTierOf(stack) > 0 ? Math.min(MAX_SLOT_STACK, stack.getCount()) : 0;
    }

    //////////////////////////////////////
    // ***** Family rules ******//
    //////////////////////////////////////

    @Override
    public int maximumParallel() {
        // 议题 3/6: empty slot 1; 1 / 2 / 3 / 4 stacked assemblers → 2 / 4 / 8 / 16.
        return switch (getAssemblerCount()) {
            case 1 -> 2;
            case 2 -> 4;
            case 3 -> 8;
            case 4 -> 16;
            default -> 1;
        };
    }

    @Override
    protected int maximumInterfaces() {
        // 议题 4: 仓室合计 ≤ 16 (EV 满并行 16 需 7 供给仓 + 排气 + 物品/流体仓).
        return 16;
    }

    @Override
    protected boolean requiresFluidInput() {
        // 议题 3/4: 类型有 1 个流体输入槽 (B2 强制焊液; B1 按配方声明), ≥1 仓.
        return true;
    }

    @Override
    protected boolean requiresExhaustHatch() {
        // 议题 4: 蒸汽排气仓必须且只能 1 个 (大型机规则).
        return true;
    }

    @Override
    protected boolean hasExhaustHazard() {
        // 排气反馈与热伤害沿用通用排气仓规则.
        return true;
    }

    @Override
    protected SoundEntry workingSoundEntry() {
        // 议题 9: 类型自带 ASSEMBLER 声.
        return GTSoundEntries.ASSEMBLER;
    }

    //////////////////////////////////////
    // ***** Batch economics ******//
    //////////////////////////////////////

    /**
     * 议题 3: 配方等级访问 — empty slot runs ULV only; a stacked kind admits
     * recipes up to its tier. The family gate's EU-less reject stays.
     */
    @Override
    protected boolean passesVoltageGate(GTRecipe recipe) {
        var eut = recipe.getInputEUt();
        if (eut.isEmpty()) {
            return false;
        }
        long voltage = eut.voltage();
        if (voltage <= 0) {
            return true;
        }
        return GTUtil.getTierByVoltage(voltage) <= getAssemblerTier();
    }

    /** 议题 5 阶梯: batch duration = ceil(recipe × 1.5 × timeScale). */
    @Override
    protected long batchDurationTicks(GTRecipe recipe, int parallel) {
        return Math.max(1, (long) Math.ceil(recipe.duration * DURATION_MULTIPLIER * timeScale(parallel)));
    }

    /** 议题 5 阶梯: per-tick steam = ceil(EU/t × 2 × steamScale) — NOT linear in the parallel. */
    @Override
    protected long batchSteamPerTickMb(GTRecipe recipe, long eu, int parallel) {
        return (long) Math.ceil(eu * (double) STEAM_PER_EU_MB * steamScale(parallel));
    }

    /** 议题 11: Easy 档产出 2× (locked per batch; Normal / Expert stay 1×). */
    @Override
    protected float batchOutputMultiplier() {
        return GSEDifficultyState.current(isRemote()) == Difficulty.EASY ? 2.0f : 1.0f;
    }

    /** 蒸汽每刻倍率: floor to the largest ladder step ≤ 实际并行. */
    private static double steamScale(int parallel) {
        if (parallel >= 16) return 2.0;
        if (parallel >= 8) return 1.75;
        if (parallel >= 4) return 1.5;
        if (parallel >= 2) return 1.25;
        return 1.0;
    }

    /** 耗时倍率: floor to the largest ladder step ≤ 实际并行. */
    private static double timeScale(int parallel) {
        if (parallel >= 16) return 3.0;
        if (parallel >= 8) return 2.5;
        if (parallel >= 4) return 2.0;
        if (parallel >= 2) return 1.5;
        return 1.0;
    }

    //////////////////////////////////////
    // ***** Lifecycle ******//
    //////////////////////////////////////

    @Override
    public void onMachineRemoved() {
        // 议题 8: 槽内组装机以物品实体掉落返还, 不消失; 其余状态按基类清理.
        ItemStack stack = assemblerSlot.getStackInSlot(0);
        if (!stack.isEmpty()) {
            assemblerSlot.setStackInSlot(0, ItemStack.EMPTY);
            Level level = getLevel();
            if (level != null && !isRemote()) {
                Block.popResource(level, getPos(), stack);
            }
        }
        super.onMachineRemoved();
    }

    //////////////////////////////////////
    // ***** Controller UI ******//
    //////////////////////////////////////

    @Override
    public ModularUI createUI(Player entityPlayer) {
        ModularUI ui = super.createUI(entityPlayer);
        // The shared processor UI has no player inventory because most family
        // members expose no controller slots. These two machines do: without
        // binding the inventory there is no in-GUI source from which a player
        // can move an assembler into the dedicated slot.
        ui.setSize(260, 256);
        // 组装机槽位 (议题 9): fixed beside the power button, below the scroll
        // area; the live tier/parallel summary sits to its right.
        ui.widget(new SlotWidget(assemblerSlot, 0, 30, 146)
                .setBackgroundTexture(GuiTextures.SLOT));
        ui.widget(new LabelWidget(52, 152, this::slotSummaryText)
                .setTextColor(-1).setDropShadow(true));
        ui.widget(UITemplate.bindPlayerInventory(entityPlayer.getInventory(), GuiTextures.SLOT, 49, 174, true));
        return ui;
    }

    /** `组装机槽位: LV ×3（并行 ≤ 8）` / `组装机槽位: 空（仅 ULV）`. */
    private String slotSummaryText() {
        ItemStack stack = getAssemblerStack();
        if (getAssemblerTier() <= 0) {
            return Component.translatable(SLOT_UI_PREFIX + "slot_empty").getString();
        }
        int tier = getAssemblerTier();
        String tierName = tier < GTValues.VN.length ? GTValues.VN[tier] : "?";
        return Component.translatable(SLOT_UI_PREFIX + "slot_summary",
                tierName, stack.getCount(), maximumParallel()).getString();
    }

    //////////////////////////////////////
    // ***** Jade snapshot ******//
    //////////////////////////////////////

    /** Current slot-derived parallel cap (Jade/GUI display). */
    public int getSlotParallelCap() {
        return maximumParallel();
    }

    /** AQUA when a tier key is installed, GRAY when the slot is empty. */
    public ChatFormatting getSlotTierColor() {
        return getAssemblerTier() > 0 ? ChatFormatting.AQUA : ChatFormatting.GRAY;
    }

    /** Pattern source kept abstract in the family base (B1/B2 structures differ). */
    @Override
    public abstract BlockPattern getPattern();
}
