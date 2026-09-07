package com.hoshino.gregsteamexpansion.machine.multiblock.voidproducer;

import com.gregtechceu.gtceu.api.capability.IControllable;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.widget.ToggleButtonWidget;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.api.machine.feature.IMachineLife;
import com.gregtechceu.gtceu.api.machine.feature.IUIMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.api.machine.property.GTMachineModelProperties;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.pattern.BlockPattern;
import com.gregtechceu.gtceu.api.sound.SoundEntry;
import com.gregtechceu.gtceu.common.data.GTMaterials;
import com.gregtechceu.gtceu.common.machine.multiblock.part.FluidHatchPartMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.part.ItemBusPartMachine;
import com.gregtechceu.gtceu.utils.FormattingUtil;
import com.hoshino.gregsteamexpansion.GregSteamExpansion;
import com.hoshino.gregsteamexpansion.machine.multiblock.part.SteamExhaustHatchMachine;
import com.hoshino.gregsteamexpansion.machine.multiblock.part.SteamSupplyHatchPartMachine;

import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import com.lowdragmc.lowdraglib.gui.widget.DraggableScrollableWidgetGroup;
import com.lowdragmc.lowdraglib.gui.widget.LabelWidget;
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
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.ItemHandlerHelper;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import javax.annotation.ParametersAreNonnullByDefault;

/**
 * 旗舰虚空生产机器共用基类 (large-steam-ore-plant.md / large-steam-fluid-drill.md,
 * F1/F2 议题 5–9 同口径): 无配方类型的纯生成机器 — 固定数量的生产工位共享一个
 * 200 tick 节拍, 期间逐刻跨全部蒸汽供给仓原子取汽 (单仓 1,200 mB/t 上限, 断汽回退
 * 1 tick 续跑); 节拍完成时每个工位独立按配置权重表抽取一份产出, 产物进入待输出
 * 缓存并原子提交 (阻塞则暂停且不耗汽)。大型机规则: 蒸汽排气仓必须且只能 1 个
 * (受阻冻结进度、反馈脉冲与热伤害), 仓室合计上限由子类声明。
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
    public static final long PER_HATCH_STEAM_CAP_MB = 1200;

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

    //////////////////////////////////////
    // ***** Runtime state ******//
    //////////////////////////////////////

    @Nullable
    private TickableSubscription tickSubscription;
    /** 供汽仓 stable position order (家族口径). */
    private final List<SteamSupplyHatchPartMachine> supplyHatches = new ArrayList<>();
    private final List<ItemBusPartMachine> outputBuses = new ArrayList<>();
    private final List<FluidHatchPartMachine> fluidOutputHatches = new ArrayList<>();
    /** 蒸汽排气仓: 必须且只能 1 个 (大型机规则). */
    private final List<SteamExhaustHatchMachine> exhaustHatches = new ArrayList<>();
    private boolean exhaustBlocked = false;
    private int exhaustFeedbackTimer = 0;
    private long exhaustDamageTimer = 0;
    /** False when the post-formation interface count rules failed. */
    private boolean interfaceCountsValid = true;
    private boolean waitingForSteam = false;
    private boolean waitingForOutputs = false;
    /** Whether this tick actually consumed the full steam demand. */
    private boolean lastTickConsumedSteam = false;
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

    /** Maximum total interface count incl. buses, supply and exhaust hatches. */
    protected abstract int maximumInterfaces();

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
        cycleProgress = Math.min(cycleProgress, 1);
        lastTickConsumedSteam = false;
        supplyHatches.clear();
        outputBuses.clear();
        fluidOutputHatches.clear();
        exhaustHatches.clear();
        exhaustBlocked = false;
        updateWorkingAppearance();
    }

    /**
     * Post-formation check of the interface count rules: supply hatch ≥1, exhaust
     * hatch 恰好 1, required outputs present, total at {@link #maximumInterfaces()}.
     */
    private boolean validateInterfaceCounts() {
        if (supplyHatches.size() < 1 || exhaustHatches.size() != 1) {
            return false;
        }
        if (requiresItemOutput() && outputBuses.size() < 1) {
            return false;
        }
        if (requiresFluidOutput() && fluidOutputHatches.size() < 1) {
            return false;
        }
        int interfaces = outputBuses.size() + fluidOutputHatches.size()
                + supplyHatches.size() + exhaustHatches.size();
        return interfaces <= maximumInterfaces();
    }

    private void collectParts() {
        supplyHatches.clear();
        outputBuses.clear();
        fluidOutputHatches.clear();
        exhaustHatches.clear();
        it.unimi.dsi.fastutil.longs.Long2ObjectMap<IO> ioMap = getMultiblockState().getMatchContext()
                .getOrCreate("ioMap", it.unimi.dsi.fastutil.longs.Long2ObjectMaps::emptyMap);
        for (IMultiPart part : getParts()) {
            IO io = ioMap.getOrDefault(part.self().getPos().asLong(), IO.BOTH);
            if (io == IO.NONE) continue;
            if (part instanceof SteamSupplyHatchPartMachine supplyHatch) {
                supplyHatches.add(supplyHatch);
            } else if (part instanceof ItemBusPartMachine bus) {
                if (bus.getInventory().getHandlerIO() == IO.OUT) {
                    outputBuses.add(bus);
                }
            } else if (part instanceof FluidHatchPartMachine fluidHatch) {
                if (fluidHatch.tank.handlerIO == IO.OUT) {
                    // Covers the GTCEu standard fluid output hatch and the mod's
                    // steam fluid output hatch (F2 议题 7: 可选或混用).
                    fluidOutputHatches.add(fluidHatch);
                }
            } else if (part instanceof SteamExhaustHatchMachine exhaustHatch) {
                exhaustHatches.add(exhaustHatch);
            }
        }
        supplyHatches.sort(Comparator.comparing(hatch -> hatch.self().getPos()));
        outputBuses.sort(Comparator
                .comparing((ItemBusPartMachine bus) -> !isMeBus(bus))
                .thenComparing(bus -> bus.self().getPos()));
        fluidOutputHatches.sort(Comparator.comparing(hatch -> hatch.self().getPos()));
        exhaustHatches.sort(Comparator.comparing(hatch -> hatch.self().getPos()));
    }

    /** ME parts are detected by definition id; AE2 classes are never loaded. */
    private static boolean isMeBus(IMultiPart part) {
        String path = part.self().getDefinition().getId().getPath();
        return path.startsWith("me_");
    }

    //////////////////////////////////////
    // ***** Ticking ******//
    //////////////////////////////////////

    private void voidServerTick() {
        Level level = getLevel();
        if (level == null || level.isClientSide) {
            return;
        }
        waitingForSteam = false;
        waitingForOutputs = false;

        // 待输出优先送出 (also while paused: delivering is not production work).
        if (isFormed() && (!pendingOutputs.isEmpty() || !pendingFluids.isEmpty())
                && !deliverPendingOutputs()) {
            waitingForOutputs = true;
        }
        if (waitingForOutputs) {
            lastTickConsumedSteam = false;
            updateWorkingAppearance();
            return;
        }

        // 主动暂停: freeze progress, no rollback.
        if (!isWorkingEnabled()) {
            lastTickConsumedSteam = false;
            updateWorkingAppearance();
            return;
        }
        if (!isFormed() || !interfaceCountsValid) {
            lastTickConsumedSteam = false;
            updateWorkingAppearance();
            return;
        }

        // 配置门禁 (议题 2/7): 最高优先级, 不运行不取汽.
        if (!configEnabled()) {
            lastTickConsumedSteam = false;
            updateWorkingAppearance();
            return;
        }

        // 排气受阻: freeze progress WITHOUT the steam-shortage 1-tick rollback,
        // no steam withdrawn (家族口径 与缺汽明确区分).
        exhaustBlocked = !exhaustHatches.isEmpty() && exhaustHatches.get(0).isExhaustBlocked();
        if (exhaustBlocked) {
            lastTickConsumedSteam = false;
            updateWorkingAppearance();
            return;
        }

        runCycleTick();
        updateWorkingAppearance();
    }

    /** 逐 tick 原子取汽 + 节拍推进; 断汽回退至 1 tick. */
    private void runCycleTick() {
        long demand = steamPerStationTick() * stationCount();
        if (!drawSteam(demand)) {
            lastTickConsumedSteam = false;
            waitingForSteam = true;
            cycleProgress = Math.min(cycleProgress, 1);
            updateWorkingAppearance();
            return;
        }
        lastTickConsumedSteam = true;
        cycleProgress++;
        if (!exhaustHatches.isEmpty()) {
            runExhaustCycles(exhaustHatches.get(0));
        }
        if (cycleProgress >= cycleTicks()) {
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
        int before = pendingOutputs.size() + pendingFluids.size();
        produceOutputs();
        mergePendingItems();
        if (pendingOutputs.size() + pendingFluids.size() > before) {
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
        if (!stack.isEmpty()) {
            pendingOutputs.add(stack);
        }
    }

    protected void addPendingFluid(FluidStack stack) {
        if (!stack.isEmpty()) {
            pendingFluids.add(stack);
        }
    }

    /**
     * 待输出整批原子输出 (家族口径): items and fluids are committed SEPARATELY —
     * the complete item list must fit the output buses, the complete fluid list
     * must fit the fluid output hatches, and each side only executes its plan
     * when fully simulable.
     */
    private boolean deliverPendingOutputs() {
        boolean itemsOk = deliverPendingItems();
        boolean fluidsOk = deliverPendingFluids();
        return itemsOk && fluidsOk;
    }

    private boolean deliverPendingItems() {
        if (pendingOutputs.isEmpty()) {
            return true;
        }
        if (outputBuses.isEmpty()) {
            return false;
        }
        List<ItemStack> simulation = new ArrayList<>();
        for (ItemStack stack : pendingOutputs) {
            simulation.add(stack.copy());
        }
        for (ItemBusPartMachine bus : outputBuses) {
            for (int i = 0; i < simulation.size(); i++) {
                simulation.set(i, insertIntoBus(bus, simulation.get(i), true));
            }
        }
        if (simulation.stream().anyMatch(stack -> !stack.isEmpty())) {
            return false;
        }
        for (ItemBusPartMachine bus : outputBuses) {
            for (int i = 0; i < pendingOutputs.size(); i++) {
                pendingOutputs.set(i, insertIntoBus(bus, pendingOutputs.get(i), false));
            }
        }
        pendingOutputs.removeIf(ItemStack::isEmpty);
        return pendingOutputs.isEmpty();
    }

    private boolean deliverPendingFluids() {
        if (pendingFluids.isEmpty()) {
            return true;
        }
        if (fluidOutputHatches.isEmpty()) {
            return false;
        }
        List<FluidStack> simulation = new ArrayList<>();
        for (FluidStack stack : pendingFluids) {
            simulation.add(stack.copy());
        }
        for (FluidHatchPartMachine hatch : fluidOutputHatches) {
            for (int i = 0; i < simulation.size(); i++) {
                FluidStack remaining = simulation.get(i);
                if (!remaining.isEmpty()) {
                    int accepted = hatch.tank.fill(remaining, IFluidHandler.FluidAction.SIMULATE);
                    remaining.shrink(accepted);
                }
            }
        }
        if (simulation.stream().anyMatch(stack -> !stack.isEmpty())) {
            return false;
        }
        for (FluidHatchPartMachine hatch : fluidOutputHatches) {
            for (int i = 0; i < pendingFluids.size(); i++) {
                FluidStack remaining = pendingFluids.get(i);
                if (!remaining.isEmpty()) {
                    int accepted = hatch.tank.fill(remaining, IFluidHandler.FluidAction.EXECUTE);
                    remaining.shrink(accepted);
                }
            }
        }
        pendingFluids.removeIf(FluidStack::isEmpty);
        return pendingFluids.isEmpty();
    }

    /** True while any finished output (item or fluid) still awaits delivery. */
    public boolean hasPendingOutputs() {
        return !pendingOutputs.isEmpty() || !pendingFluids.isEmpty();
    }

    private ItemStack insertIntoBus(ItemBusPartMachine bus, ItemStack stack, boolean simulate) {
        if (stack.isEmpty()) {
            return stack;
        }
        // insertItemInternal, not insertItemStacked: the output bus inventory's
        // capability face is IO.OUT (extract only), so the capability-level
        // insert is gated off for outside callers. Same stacking semantics as
        // ItemHandlerHelper.insertItemStacked, on the internal path.
        var inventory = bus.getInventory();
        ItemStack remaining = stack;
        for (int slot = 0; slot < inventory.getSlots() && !remaining.isEmpty(); slot++) {
            ItemStack current = inventory.getStackInSlot(slot);
            if (!current.isEmpty() && ItemHandlerHelper.canItemStacksStack(current, remaining)) {
                remaining = inventory.insertItemInternal(slot, remaining, simulate);
            }
        }
        for (int slot = 0; slot < inventory.getSlots() && !remaining.isEmpty(); slot++) {
            if (inventory.getStackInSlot(slot).isEmpty()) {
                remaining = inventory.insertItemInternal(slot, remaining, simulate);
            }
        }
        return remaining;
    }

    private void mergePendingItems() {
        for (int i = 0; i < pendingOutputs.size(); i++) {
            ItemStack keep = pendingOutputs.get(i);
            for (int j = pendingOutputs.size() - 1; j > i; j--) {
                ItemStack other = pendingOutputs.get(j);
                if (!keep.isEmpty() && ItemHandlerHelper.canItemStacksStack(keep, other)
                        && keep.getCount() < keep.getMaxStackSize()) {
                    int moved = Math.min(other.getCount(), keep.getMaxStackSize() - keep.getCount());
                    keep.grow(moved);
                    other.shrink(moved);
                    if (other.isEmpty()) {
                        pendingOutputs.remove(j);
                    }
                }
            }
        }
        pendingOutputs.removeIf(ItemStack::isEmpty);
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
        if (amountMb <= 0 || supplyHatches.isEmpty()) {
            return false;
        }
        // drainInternal, not drain(): the supply hatch's capability face is
        // IO.IN (input only), so the capability-level drain is gated off for
        // outside callers; machine-internal withdrawal uses the internal path.
        long remaining = amountMb;
        for (SteamSupplyHatchPartMachine hatch : supplyHatches) {
            long share = Math.min(remaining, PER_HATCH_STEAM_CAP_MB);
            FluidStack simulated = hatch.tank.drainInternal(steamFluid(share), IFluidHandler.FluidAction.SIMULATE);
            remaining -= simulated.getAmount();
            if (remaining <= 0) {
                break;
            }
        }
        if (remaining > 0) {
            return false;
        }
        remaining = amountMb;
        for (SteamSupplyHatchPartMachine hatch : supplyHatches) {
            long share = Math.min(remaining, PER_HATCH_STEAM_CAP_MB);
            FluidStack drained = hatch.tank.drainInternal(steamFluid(share), IFluidHandler.FluidAction.EXECUTE);
            remaining -= drained.getAmount();
            if (remaining <= 0) {
                break;
            }
        }
        if (remaining > 0) {
            GregSteamExpansion.LOGGER.warn(
                    "Void producer at {} draw execution fell short of the simulated plan by {} mB",
                    getPos(), remaining);
            return false;
        }
        return true;
    }

    private static FluidStack steamFluid(long amountMb) {
        return GTMaterials.Steam.getFluid((int) Math.min(amountMb, Integer.MAX_VALUE));
    }

    /** 供给仓合计存量 (mB). */
    public long getSteamTotalStored() {
        long total = 0;
        for (SteamSupplyHatchPartMachine hatch : supplyHatches) {
            total += hatch.tank.getFluidInTank(0).getAmount();
        }
        return total;
    }

    /** 供给仓合计容量 (mB); 0 when the structure is not formed. */
    public long getSteamTotalCapacity() {
        long total = 0;
        for (SteamSupplyHatchPartMachine hatch : supplyHatches) {
            total += SteamSupplyHatchPartMachine.INITIAL_TANK_CAPACITY;
        }
        return total;
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
                && configEnabled() && !exhaustBlocked && !waitingForOutputs && lastTickConsumedSteam;
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
                            && configEnabled() && !exhaustBlocked && !waitingForOutputs && lastTickConsumedSteam
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
        if (waitingForSteam) {
            return "low_steam";
        }
        if (cycleProgress > 0 || lastTickConsumedSteam) {
            return "working";
        }
        return "idle";
    }

    public Component getStatusText() {
        return switch (getStatusId()) {
            case "invalid_structure" -> Component.translatable("gtceu.multiblock.invalid_structure");
            case "disabled_by_config" -> Component.translatable(
                    "gregsteamexpansion.machine.void_producer.ui.disabled_by_config");
            case "exhaust_obstructed" -> Component.translatable(
                    "gregsteamexpansion.machine.void_producer.ui.exhaust_obstructed");
            case "insufficient_outputs" -> Component.translatable("gtceu.recipe_logic.insufficient_out");
            case "working_disabled" -> Component.translatable("gtceu.top.working_disabled");
            case "low_steam" -> Component.translatable("gtceu.multiblock.steam.low_steam");
            case "working" -> Component.translatable("gtceu.multiblock.large_miner.working");
            default -> Component.translatable("gtceu.multiblock.idling");
        };
    }

    public ChatFormatting getStatusColor() {
        return switch (getStatusId()) {
            case "invalid_structure", "exhaust_obstructed", "insufficient_outputs" -> ChatFormatting.RED;
            case "disabled_by_config", "working_disabled", "low_steam" -> ChatFormatting.YELLOW;
            case "working" -> ChatFormatting.GREEN;
            default -> ChatFormatting.GRAY;
        };
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
        int uiWidth = 260;
        int uiHeight = 170;
        var ui = new ModularUI(uiWidth, uiHeight, this, entityPlayer)
                .background(GuiTextures.BACKGROUND);
        var scroll = new DraggableScrollableWidgetGroup(5, 5, uiWidth - 10, uiHeight - 32);
        int y = 2;
        y = infoRow(scroll, y, UI_PREFIX + "status", () -> getStatusText().getString(), getStatusColor());
        y = infoRow(scroll, y, UI_PREFIX + "progress", this::progressText, ChatFormatting.WHITE);
        y = addMachineInfoRows(scroll, y);
        y = infoRow(scroll, y, UI_PREFIX + "steam",
                () -> (isFormed()
                        ? FormattingUtil.formatNumbers(getSteamTotalStored()) + " / "
                                + FormattingUtil.formatNumbers(getSteamTotalCapacity()) + " mB"
                        : "—"),
                ChatFormatting.WHITE);
        y = infoRow(scroll, y, UI_PREFIX + "demand", this::demandText, ChatFormatting.WHITE);
        scroll.addWidget(new LabelWidget(2, y, () -> Component.translatable(UI_PREFIX + "pending").getString())
                .setTextColor(-1).setDropShadow(true));
        Integer pendingRgb = ChatFormatting.WHITE.getColor();
        LabelWidget pendingValue = new LabelWidget(104, y, () -> pendingSummaryText().replace("%", "%%")) {
            @Override
            public java.util.List<Component> getTooltipTexts() {
                return pendingDetailTooltips();
            }
        };
        pendingValue.setTextColor(pendingRgb == null ? -1 : (pendingRgb.intValue() & 0xFFFFFF)).setDropShadow(true);
        scroll.addWidget(pendingValue);
        y += 10;
        ui.widget(scroll);
        // GTCEu standard power button fixed outside the scroll area.
        ui.widget(new ToggleButtonWidget(6, uiHeight - 24, 18, 18, GuiTextures.BUTTON_POWER,
                this::isWorkingEnabled, this::setWorkingEnabled));
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
        scroll.addWidget(new LabelWidget(2, y, () -> Component.translatable(UI_PREFIX + "pool").getString())
                .setTextColor(-1).setDropShadow(true));
        Integer rgb = ChatFormatting.WHITE.getColor();
        LabelWidget value = new LabelWidget(104, y, () -> summary.get().replace("%", "%%")) {
            @Override
            public java.util.List<Component> getTooltipTexts() {
                return tooltips.get();
            }
        };
        value.setTextColor(rgb == null ? -1 : (rgb.intValue() & 0xFFFFFF)).setDropShadow(true);
        scroll.addWidget(value);
        return y + 10;
    }

    protected int infoRow(DraggableScrollableWidgetGroup group, int y, String labelKey,
                        java.util.function.Supplier<String> value, ChatFormatting valueColor) {
        group.addWidget(new LabelWidget(2, y, () -> Component.translatable(labelKey).getString())
                .setTextColor(-1).setDropShadow(true));
        Integer rgb = valueColor.getColor();
        group.addWidget(new LabelWidget(104, y, () -> value.get().replace("%", "%%"))
                .setTextColor(rgb == null ? -1 : (rgb.intValue() & 0xFFFFFF)).setDropShadow(true));
        return y + 10;
    }

    /** `45.0%（135 / 200 tick）`. */
    private String progressText() {
        int ticks = cycleTicks();
        double percent = Math.round(Math.min(cycleProgress, ticks) * 1000.0 / Math.max(1, ticks)) / 10.0;
        String percentText = String.format(java.util.Locale.ROOT, "%.1f%%", percent);
        return percentText + " (" + FormattingUtil.formatNumbers(Math.min(cycleProgress, ticks))
                + " / " + FormattingUtil.formatNumbers(ticks) + "t)";
    }

    /** 总需求 = 工位 × 单工位; only "运行中" with a successful draw consumes. */
    private String demandText() {
        long demand = steamPerStationTick() * stationCount();
        String demandText = FormattingUtil.formatNumbers(demand) + " mB/t";
        if (getStatusId().equals("working") && !lastTickConsumedSteam) {
            return demandText + " (" + Component.translatable(UI_PREFIX + "not_consuming").getString() + ")";
        }
        return demandText;
    }

    /** `128（3 种）` style pending summary; `—` when nothing is pending. */
    private String pendingSummaryText() {
        long itemTotal = 0;
        for (ItemStack stack : pendingOutputs) {
            itemTotal += stack.getCount();
        }
        long fluidTotal = 0;
        for (FluidStack stack : pendingFluids) {
            fluidTotal += stack.getAmount();
        }
        if (itemTotal == 0 && fluidTotal == 0) {
            return "—";
        }
        int kinds = countPendingItemKinds() + countPendingFluidKinds();
        return Component.translatable(UI_PREFIX + "pending_summary",
                FormattingUtil.formatNumbers(itemTotal + fluidTotal), kinds).getString();
    }

    private int countPendingItemKinds() {
        List<ItemStack> kinds = new ArrayList<>();
        for (ItemStack stack : pendingOutputs) {
            boolean merged = false;
            for (ItemStack kind : kinds) {
                if (ItemHandlerHelper.canItemStacksStack(kind, stack)) {
                    kind.grow(stack.getCount());
                    merged = true;
                    break;
                }
            }
            if (!merged) {
                kinds.add(stack.copy());
            }
        }
        return kinds.size();
    }

    private int countPendingFluidKinds() {
        List<FluidStack> kinds = new ArrayList<>();
        for (FluidStack stack : pendingFluids) {
            boolean merged = false;
            for (FluidStack kind : kinds) {
                if (kind.isFluidEqual(stack)) {
                    kind.grow(stack.getAmount());
                    merged = true;
                    break;
                }
            }
            if (!merged) {
                kinds.add(stack.copy());
            }
        }
        return kinds.size();
    }

    /** Hover list of every pending item/fluid in the persisted stable order. */
    private List<Component> pendingDetailTooltips() {
        if (!hasPendingOutputs()) {
            return List.of(Component.translatable(UI_PREFIX + "pending_empty").withStyle(ChatFormatting.GRAY));
        }
        List<Component> tooltips = new ArrayList<>();
        tooltips.add(Component.translatable(UI_PREFIX + "pending_detail").withStyle(ChatFormatting.GRAY));
        for (ItemStack stack : pendingOutputs) {
            tooltips.add(Component.literal("- " + stack.getHoverName().getString() + " × "
                    + FormattingUtil.formatNumbers(stack.getCount())).withStyle(ChatFormatting.WHITE));
        }
        for (FluidStack stack : pendingFluids) {
            tooltips.add(Component.literal("- " + stack.getDisplayName().getString() + " × "
                    + FormattingUtil.formatNumbers(stack.getAmount()) + " mB")
                    .withStyle(ChatFormatting.WHITE));
        }
        return tooltips;
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
        return lastTickConsumedSteam;
    }

    public long getPendingTotalCount() {
        long total = 0;
        for (ItemStack stack : pendingOutputs) {
            total += stack.getCount();
        }
        return total;
    }

    public int getPendingKinds() {
        return countPendingItemKinds();
    }

    /** Total pending fluid amount in mB (Jade snapshot). */
    public long getPendingFluidTotal() {
        long total = 0;
        for (FluidStack stack : pendingFluids) {
            total += stack.getAmount();
        }
        return total;
    }

    public int getPendingFluidKinds() {
        return countPendingFluidKinds();
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
        pendingOutputs.clear();
        pendingFluids.clear();
        exhaustFeedbackTimer = 0;
        exhaustDamageTimer = 0;
        exhaustBlocked = false;
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
