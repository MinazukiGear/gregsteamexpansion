package com.hoshino.gregsteamexpansion.machine.multiblock.voidproducer;

import com.gregtechceu.gtceu.api.capability.IControllable;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.api.machine.feature.IMachineLife;
import com.gregtechceu.gtceu.api.machine.feature.IUIMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.api.machine.property.GTMachineModelProperties;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.pattern.BlockPattern;
import com.gregtechceu.gtceu.api.sound.SoundEntry;
import com.gregtechceu.gtceu.common.machine.multiblock.part.FluidHatchPartMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.part.ItemBusPartMachine;
import com.hoshino.gregsteamexpansion.GregSteamExpansion;
import com.hoshino.gregsteamexpansion.machine.multiblock.BatchStateMachine;
import com.hoshino.gregsteamexpansion.machine.multiblock.PendingOutputBuffer;
import com.hoshino.gregsteamexpansion.machine.multiblock.SteamBudget;
import com.hoshino.gregsteamexpansion.machine.multiblock.SteamPartCollector;
import com.hoshino.gregsteamexpansion.machine.multiblock.SteamProcessorUI;
import com.hoshino.gregsteamexpansion.machine.multiblock.SteamStatusText;
import com.hoshino.gregsteamexpansion.machine.multiblock.part.SteamExhaustHatchMachine;
import com.hoshino.gregsteamexpansion.machine.multiblock.part.SteamSupplyHatchPartMachine;

import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import com.lowdragmc.lowdraglib.gui.widget.DraggableScrollableWidgetGroup;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;

import net.minecraft.ChatFormatting;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fluids.FluidStack;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.ParametersAreNonnullByDefault;

/**
 * 旗舰虚空生产机器共用基类 (large-steam-ore-plant.md / large-steam-fluid-drill.md,
 * F1/F2 议题 5–9 同口径): 无配方类型的纯生成机器 — 固定数量的生产工位共享一个
 * 200 tick 节拍, 期间逐刻跨全部蒸汽供给仓原子取汽 (单仓 1,200 mB/t 上限, 断汽回退
 * 1 tick 续跑); 节拍完成时每个工位独立按配置权重表抽取一份产出, 产物进入待输出
 * 缓存并原子提交 (阻塞则暂停且不耗汽)。大型机规则: 蒸汽排气仓必须且只能 1 个
 * (受阻冻结进度、反馈脉冲与热伤害)，普通外壳候选位不设接口合计上限。
 *
 * <p>配置门禁 (议题 2/7): {@link #configEnabled()} 为 false 时优先级高于一切运行
 * 条件 — 结构与注册保留, 机器不启动, 状态栏显示"已在配置中禁用"。三档难度倍率
 * ({@link #outputMultiplier()}, F1/F2 均为 Easy 4× / Normal 2× / Expert 1×) 只放大
 * 产出数量, 不影响耗汽与节拍。</p>
 */
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public abstract class AbstractSteamVoidMachine extends MultiblockControllerMachine
        implements IControllable, IUIMachine, IMachineLife {

    protected static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(
            AbstractSteamVoidMachine.class, MultiblockControllerMachine.MANAGED_FIELD_HOLDER);

    /** 机器侧单供给仓取汽上限 (家族口径). */
    public static final long PER_HATCH_STEAM_CAP_MB = SteamBudget.PHYSICAL_HATCH_LIMIT_MB;

    //////////////////////////////////////
    // ***** Persisted state ******//
    //////////////////////////////////////

    /** Work-enabled flag shared by the power button, soft hammer and covers. */
    @Persisted
    private boolean workingEnabled = true;
    /** Progress inside the current production cycle (0..cycleTicks). */
    @Persisted
    private int cycleProgress = 0;
    /** Finished items waiting for output space. */
    @Persisted
    private final List<ItemStack> pendingOutputs = new ArrayList<>();
    /** Finished fluids waiting for output space. */
    @Persisted
    private final List<FluidStack> pendingFluids = new ArrayList<>();
    private final PendingOutputBuffer pendingBuffer = new PendingOutputBuffer(pendingOutputs, pendingFluids);

    //////////////////////////////////////
    // ***** Runtime state ******//
    //////////////////////////////////////

    @Nullable
    private TickableSubscription tickSubscription;
    private final SteamPartCollector partCollector = new SteamPartCollector();
    /** 供汽仓 stable position order (家族口径). */
    private final List<SteamSupplyHatchPartMachine> supplyHatches = partCollector.supplyHatches();
    private final SteamBudget steamBudget = new SteamBudget(supplyHatches);
    private final BatchStateMachine batchState = new BatchStateMachine();
    private final List<ItemBusPartMachine> outputBuses = partCollector.outputBuses();
    private final List<FluidHatchPartMachine> fluidOutputHatches = partCollector.fluidOutputHatches();
    /** 蒸汽排气仓: 必须且只能 1 个 (大型机规则). */
    private final List<SteamExhaustHatchMachine> exhaustHatches = partCollector.exhaustHatches();
    private boolean exhaustBlocked = false;
    private int exhaustFeedbackTimer = 0;
    private long exhaustDamageTimer = 0;
    /** False when the post-formation interface count rules failed. */
    private boolean interfaceCountsValid = true;
    /** Working sound handle (client only). */
    @Nullable
    @OnlyIn(Dist.CLIENT)
    private Object workingSound;

    protected AbstractSteamVoidMachine(IMachineBlockEntity holder) {
        super(holder);
    }

    @Override
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }

    //////////////////////////////////////
    // ***** Machine-specific hooks ******//
    //////////////////////////////////////

    /** 生产工位数量 (F1 = 4, F2 = 2), 共享同一节拍相位. */
    public abstract int stationCount();

    /** 单次节拍时长 (定案 200 tick). */
    public abstract int cycleTicks();

    /** 单工位每刻蒸汽需求 (定案 3,000 mB/t); 总需求 = × stationCount. */
    public abstract long steamPerStationTick();

    /** 配置启用开关 (议题 2/7): false 时机器不可运行. */
    protected abstract boolean configEnabled();

    /** 三档产出倍率 (议题 6): Easy 4 / Normal 2 / Expert 1, 只放大产出数量. */
    public abstract int outputMultiplier();

    /** Whether at least one item output bus is required (F1). */
    protected boolean requiresItemOutput() {
        return false;
    }

    /** Whether at least one fluid output hatch is required (F2). */
    protected boolean requiresFluidOutput() {
        return false;
    }

    /** Working loop sound (议题 9). */
    protected abstract SoundEntry workingSoundEntry();

    /**
     * 节拍完成: 每工位独立抽取一次, 产物写入 pending 列表 (由基类原子提交).
     * 调用时保证 formed / interfaceCountsValid / 未被配置禁用.
     */
    protected abstract void produceOutputs();

    /** Pattern source (per-machine structures). */
    @Override
    public abstract BlockPattern getPattern();

    //////////////////////////////////////
    // ***** Pattern ******//
    //////////////////////////////////////

    @Override
    public boolean checkPattern() {
        var state = getMultiblockState();
        return getPattern().checkPatternAt(state, false);
    }

    //////////////////////////////////////
    // ***** Formation ******//
    //////////////////////////////////////

    @Override
    public void onStructureFormed() {
        super.onStructureFormed();
        collectParts();
        interfaceCountsValid = validateInterfaceCounts();
        if (!interfaceCountsValid) {
            // 接口数量规则失败: 视为结构未成型 (图案逐谓词上限表达不了的合计口径).
            onStructureInvalid();
            return;
        }
        if (tickSubscription == null) {
            tickSubscription = subscribeServerTick(this::voidServerTick);
        }
        updateWorkingAppearance();
    }

    @Override
    public void onStructureInvalid() {
        super.onStructureInvalid();
        // 结构失效: 进度回退至 1 tick (家族口径), 待输出保留.
        cycleProgress = batchState.invalidate(cycleProgress, true);
        partCollector.clear();
        exhaustBlocked = false;
        updateWorkingAppearance();
    }

    /**
     * Post-formation check of the interface count rules: supply hatch ≥1,
     * exhaust hatch 恰好 1 and required outputs present. Ordinary casing
     * positions impose no total-interface cap.
     */
    private boolean validateInterfaceCounts() {
        return partCollector.validate(new SteamPartCollector.InterfaceRules(
                0, -1,
                requiresItemOutput() ? 1 : 0,
                1,
                1,
                0,
                requiresFluidOutput() ? 1 : 0,
                true,
                0, 0));
    }

    private void collectParts() {
        partCollector.collect(this);
    }

    //////////////////////////////////////
    // ***** Ticking ******//
    //////////////////////////////////////

    private void voidServerTick() {
        Level level = getLevel();
        if (level == null || level.isClientSide) {
            return;
        }
        batchState.beginTick();

        // 待输出优先送出 (also while paused: delivering is not production work).
        if (isFormed() && (!pendingOutputs.isEmpty() || !pendingFluids.isEmpty())
                && !deliverPendingOutputs()) {
            batchState.freeze(BatchStateMachine.HoldReason.OUTPUTS);
        }
        if (batchState.isWaitingFor(BatchStateMachine.HoldReason.OUTPUTS)) {
            updateWorkingAppearance();
            return;
        }

        // 主动暂停: freeze progress, no rollback.
        if (!isWorkingEnabled()) {
            batchState.freeze(BatchStateMachine.HoldReason.PAUSED);
            updateWorkingAppearance();
            return;
        }
        if (!isFormed() || !interfaceCountsValid) {
            batchState.freeze(BatchStateMachine.HoldReason.INVALID_STRUCTURE);
            updateWorkingAppearance();
            return;
        }

        // 配置门禁 (议题 2/7): 最高优先级, 不运行不取汽.
        if (!configEnabled()) {
            batchState.freeze(BatchStateMachine.HoldReason.CONFIG_DISABLED);
            updateWorkingAppearance();
            return;
        }

        // 排气受阻: freeze progress WITHOUT the steam-shortage 1-tick rollback,
        // no steam withdrawn (家族口径 与缺汽明确区分).
        exhaustBlocked = !exhaustHatches.isEmpty() && exhaustHatches.get(0).isExhaustBlocked();
        if (exhaustBlocked) {
            batchState.freeze(BatchStateMachine.HoldReason.EXHAUST_BLOCKED);
            updateWorkingAppearance();
            return;
        }

        runCycleTick();
        updateWorkingAppearance();
    }

    /** 逐 tick 原子取汽 + 节拍推进; 断汽回退至 1 tick. */
    private void runCycleTick() {
        long demand = steamPerStationTick() * stationCount();
        BatchStateMachine.TickResult tick = batchState.runTick(
                cycleProgress, cycleTicks(), () -> drawSteam(demand));
        cycleProgress = tick.progress();
        if (!tick.consumed()) {
            updateWorkingAppearance();
            return;
        }
        if (!exhaustHatches.isEmpty()) {
            runExhaustCycles(exhaustHatches.get(0));
        }
        if (tick.completed()) {
            cycleProgress = 0;
            completeCycle();
        }
    }

    /** Exhaust feedback pulse every 20 running ticks + 200-tick damage cycle. */
    private void runExhaustCycles(SteamExhaustHatchMachine exhaustHatch) {
        exhaustFeedbackTimer++;
        if (exhaustFeedbackTimer >= SteamExhaustHatchMachine.FEEDBACK_INTERVAL_TICKS) {
            exhaustFeedbackTimer = 0;
            exhaustHatch.performExhaustFeedback();
        }
        exhaustDamageTimer++;
        if (exhaustDamageTimer >= SteamExhaustHatchMachine.DAMAGE_CYCLE_TICKS) {
            exhaustDamageTimer = 0;
            exhaustHatch.applyExhaustDamage();
        }
    }

    /** 节拍完成: 每工位独立抽取一次 (议题 3), 产物进入待输出缓存并立即尝试提交. */
    private void completeCycle() {
        int before = pendingBuffer.entryCount();
        produceOutputs();
        pendingBuffer.mergePendingItems();
        if (pendingBuffer.entryCount() > before) {
            GregSteamExpansion.LOGGER.debug(
                    "Void producer at {} completed a cycle: {} draws, {} pending items / {} pending fluids",
                    getPos(), stationCount(), pendingOutputs.size(), pendingFluids.size());
        }
        deliverPendingOutputs();
    }

    //////////////////////////////////////
    // ***** Pending outputs ******//
    //////////////////////////////////////

    protected void addPendingItem(ItemStack stack) {
        pendingBuffer.addItem(stack);
    }

    protected void addPendingFluid(FluidStack stack) {
        pendingBuffer.addFluid(stack);
    }

    /**
     * 待输出整批原子输出 (家族口径): items and fluids are committed SEPARATELY —
     * the complete item list must fit the output buses, the complete fluid list
     * must fit the fluid output hatches, and each side only executes its plan
     * when fully simulable.
     */
    private boolean deliverPendingOutputs() {
        return pendingBuffer.deliverAll(
                outputBuses, fluidOutputHatches, PendingOutputBuffer.FluidInsertion.CAPABILITY);
    }

    /** True while any finished output (item or fluid) still awaits delivery. */
    public boolean hasPendingOutputs() {
        return pendingBuffer.hasAny();
    }

    //////////////////////////////////////
    // ***** Steam supply ******//
    //////////////////////////////////////

    /**
     * 原子取汽 (家族口径): simulate the full per-tick demand across all supply
     * hatches in stable position order — each hatch capped at
     * {@link #PER_HATCH_STEAM_CAP_MB} mB/t machine-side — and only execute the
     * same plan when every hatch can deliver its share.
     */
    private boolean drawSteam(long amountMb) {
        return steamBudget.drawSteam(amountMb, remaining -> GregSteamExpansion.LOGGER.warn(
                "Void producer at {} draw execution fell short of the simulated plan by {} mB",
                getPos(), remaining));
    }

    /** 供给仓合计存量 (mB). */
    public long getSteamTotalStored() {
        return steamBudget.totalStoredMb();
    }

    /** 供给仓合计容量 (mB); 0 when the structure is not formed. */
    public long getSteamTotalCapacity() {
        return steamBudget.totalCapacityMb();
    }

    //////////////////////////////////////
    // ***** Working state ******//
    //////////////////////////////////////

    public boolean isWorkingEnabled() {
        return workingEnabled;
    }

    public void setWorkingEnabled(boolean workingEnabled) {
        this.workingEnabled = workingEnabled;
        updateWorkingAppearance();
    }

    /**
     * 工作视觉状态: only ticks that actually withdrew the full steam demand and
     * advanced the cycle count (家族口径 共用表现规则).
     */
    private void updateWorkingAppearance() {
        boolean active = isFormed() && interfaceCountsValid && isWorkingEnabled()
                && configEnabled() && !exhaustBlocked && batchState.consumedThisTick();
        var status = active ? RecipeLogic.Status.WORKING : RecipeLogic.Status.IDLE;
        var renderState = getRenderState();
        if (renderState.hasProperty(GTMachineModelProperties.RECIPE_LOGIC_STATUS)
                && renderState.getValue(GTMachineModelProperties.RECIPE_LOGIC_STATUS) != status) {
            setRenderState(renderState.setValue(GTMachineModelProperties.RECIPE_LOGIC_STATUS, status));
        }
        updateWorkingSound(active);
    }

    private void updateWorkingSound(boolean active) {
        if (isRemote()) {
            updateWorkingSoundClient(active);
        }
    }

    @OnlyIn(Dist.CLIENT)
    private void updateWorkingSoundClient(boolean active) {
        SoundEntry entry = workingSoundEntry();
        boolean shouldPlay = active && com.gregtechceu.gtceu.config.ConfigHolder.INSTANCE.machines.machineSounds;
        if (shouldPlay) {
            if (workingSound instanceof com.gregtechceu.gtceu.api.sound.AutoReleasedSound soundEntry) {
                if (soundEntry.soundEntry == entry && !soundEntry.isStopped()) {
                    return;
                }
                soundEntry.release();
                workingSound = null;
            }
            workingSound = entry.playAutoReleasedSound(
                () -> isFormed() && interfaceCountsValid && isWorkingEnabled()
                        && configEnabled() && !exhaustBlocked && batchState.consumedThisTick()
                            && com.gregtechceu.gtceu.config.ConfigHolder.INSTANCE.machines.machineSounds,
                    getPos(), true, 0, 1.0F, 1.0F);
        } else if (workingSound instanceof com.gregtechceu.gtceu.api.sound.AutoReleasedSound soundEntry) {
            soundEntry.release();
            workingSound = null;
        }
    }

    //////////////////////////////////////
    // ***** Status display ******//
    //////////////////////////////////////

    /**
     * 服务端权威状态 (家族口径 优先级表 + 议题 2/7 配置门禁):
     * 结构未成型 > 配置禁用 > 排气受阻 > 输出堵塞 > 主动暂停 > 蒸汽不足 > 运行中 > 待机.
     */
    public String getStatusId() {
        if (!isFormed() || !interfaceCountsValid) {
            return "invalid_structure";
        }
        if (!configEnabled()) {
            return "disabled_by_config";
        }
        if (exhaustBlocked) {
            return "exhaust_obstructed";
        }
        if (hasPendingOutputs()) {
            return "insufficient_outputs";
        }
        if (!isWorkingEnabled()) {
            return "working_disabled";
        }
        if (batchState.isWaitingFor(BatchStateMachine.HoldReason.STEAM)) {
            return "low_steam";
        }
        if (cycleProgress > 0 || batchState.consumedThisTick()) {
            return "working";
        }
        return "idle";
    }

    public Component getStatusText() {
        return SteamStatusText.text(getStatusId(), SteamStatusText.Profile.VOID_PRODUCER);
    }

    public ChatFormatting getStatusColor() {
        return SteamStatusText.color(getStatusId(), SteamStatusText.Profile.VOID_PRODUCER);
    }

    //////////////////////////////////////
    // ***** Controller UI ******//
    //////////////////////////////////////

    private static final String UI_PREFIX = "gregsteamexpansion.machine.void_producer.ui.";

    /**
     * 单页可滚动运行信息页 (家族骨架), fixed row order, power button outside the
     * scroll area. Subclasses add their station / pool rows via
     * {@link #addMachineInfoRows} right after the demand row.
     */
    @Override
    public ModularUI createUI(Player entityPlayer) {
        int uiHeight = SteamProcessorUI.DEFAULT_HEIGHT;
        var ui = new ModularUI(SteamProcessorUI.WIDTH, uiHeight, this, entityPlayer)
                .background(GuiTextures.BACKGROUND);
        var scroll = SteamProcessorUI.scrollArea(uiHeight);
        int y = 2;
        y = SteamProcessorUI.infoRow(scroll, y, UI_PREFIX + "status",
                () -> getStatusText().getString(), getStatusColor());
        y = SteamProcessorUI.infoRow(scroll, y, UI_PREFIX + "progress", this::progressText, ChatFormatting.WHITE);
        y = addMachineInfoRows(scroll, y);
        y = SteamProcessorUI.infoRow(scroll, y, UI_PREFIX + "steam",
                () -> SteamProcessorUI.steamStorage(isFormed(), getSteamTotalStored(), getSteamTotalCapacity()),
                ChatFormatting.WHITE);
        y = SteamProcessorUI.infoRow(scroll, y, UI_PREFIX + "demand", this::demandText, ChatFormatting.WHITE);
        SteamProcessorUI.tooltipRow(scroll, y, UI_PREFIX + "pending", this::pendingSummaryText,
                this::pendingDetailTooltips);
        ui.widget(scroll);
        // GTCEu standard power button fixed outside the scroll area.
        SteamProcessorUI.addPowerButton(ui, uiHeight, this::isWorkingEnabled, this::setWorkingEnabled);
        return ui;
    }

    /**
     * 子类在 demand 行之前插入机器专属信息行 (工位/概率表), 返回新的 y.
     */
    protected abstract int addMachineInfoRows(DraggableScrollableWidgetGroup scroll, int y);

    /** 概率表悬浮行 (议题 9): hover 列出当前生效权重表. */
    protected int probabilityRow(DraggableScrollableWidgetGroup scroll, int y,
                                 java.util.function.Supplier<String> summary,
                                 java.util.function.Supplier<java.util.List<Component>> tooltips) {
        return SteamProcessorUI.tooltipRow(scroll, y, UI_PREFIX + "pool", summary, tooltips);
    }

    protected int infoRow(DraggableScrollableWidgetGroup group, int y, String labelKey,
                        java.util.function.Supplier<String> value, ChatFormatting valueColor) {
        return SteamProcessorUI.infoRow(group, y, labelKey, value, valueColor);
    }

    /** `45.0%（135 / 200 tick）`. */
    private String progressText() {
        return SteamProcessorUI.progress(true, cycleProgress, cycleTicks());
    }

    /** 总需求 = 工位 × 单工位; only "运行中" with a successful draw consumes. */
    private String demandText() {
        long demand = currentSteamDemandPerTick();
        return SteamProcessorUI.demand(demand, demand,
                !getStatusId().equals("working") || batchState.consumedThisTick(),
                UI_PREFIX + "not_consuming");
    }

    private long currentSteamDemandPerTick() {
        String status = getStatusId();
        return status.equals("working") || status.equals("low_steam")
                ? steamPerStationTick() * stationCount() : 0;
    }

    /** `128（3 种）` style pending summary; `—` when nothing is pending. */
    private String pendingSummaryText() {
        return SteamProcessorUI.pendingSummary(pendingOutputs, pendingFluids, UI_PREFIX + "pending_summary");
    }

    /** Hover list of every pending item/fluid in the persisted stable order. */
    private List<Component> pendingDetailTooltips() {
        return SteamProcessorUI.pendingTooltips(pendingOutputs, pendingFluids,
                UI_PREFIX + "pending_empty", UI_PREFIX + "pending_detail");
    }

    //////////////////////////////////////
    // ***** Jade snapshot ******//
    //////////////////////////////////////

    public int getCycleProgress() {
        return cycleProgress;
    }

    public int getCycleTicks() {
        return cycleTicks();
    }

    public long getSteamPerTickDemand() {
        return steamPerStationTick() * stationCount();
    }

    public boolean isConsumingSteam() {
        return batchState.consumedThisTick();
    }

    public long getPendingTotalCount() {
        return SteamProcessorUI.itemTotal(pendingOutputs);
    }

    public int getPendingKinds() {
        return SteamProcessorUI.itemKinds(pendingOutputs);
    }

    /** Total pending fluid amount in mB (Jade snapshot). */
    public long getPendingFluidTotal() {
        return SteamProcessorUI.fluidTotal(pendingFluids);
    }

    public int getPendingFluidKinds() {
        return SteamProcessorUI.fluidKinds(pendingFluids);
    }

    public boolean isOutputBlocked() {
        return hasPendingOutputs();
    }

    //////////////////////////////////////
    // ***** Lifecycle ******//
    //////////////////////////////////////

    /** 拆除清理: cycle progress, pending outputs never survive (家族口径). */
    @Override
    public void onMachineRemoved() {
        cycleProgress = 0;
        pendingBuffer.clear();
        exhaustFeedbackTimer = 0;
        exhaustDamageTimer = 0;
        exhaustBlocked = false;
        batchState.reset();
    }

    /** Client particles hook (议题 9, 子类按需覆写). */
    public void spawnWorkingParticles() {
    }

    @Override
    public void animateTick(net.minecraft.util.RandomSource random) {
        if (isFormed()
                && getRenderState().getValue(GTMachineModelProperties.RECIPE_LOGIC_STATUS) == RecipeLogic.Status.WORKING) {
            spawnWorkingParticles();
        }
    }

    /** Shared weighted-draw helper over a resolved pool. */
    protected record WeightedEntry(ItemStack stack, FluidStack fluid, int weight) {
    }

    /** Weighted pick over non-empty entries; null when the pool is empty. */
    protected static WeightedEntry weightedPick(List<WeightedEntry> pool) {
        int total = 0;
        for (WeightedEntry entry : pool) {
            total += entry.weight();
        }
        if (total <= 0) {
            return null;
        }
        int roll = com.gregtechceu.gtceu.api.GTValues.RNG.nextInt(total);
        for (WeightedEntry entry : pool) {
            roll -= entry.weight();
            if (roll < 0) {
                return entry;
            }
        }
        return pool.get(pool.size() - 1);
    }

}
