package com.hoshino.gregsteamexpansion.machine.multiblock.crusher;

import com.gregtechceu.gtceu.api.capability.IControllable;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.capability.recipe.IRecipeCapabilityHolder;
import com.gregtechceu.gtceu.api.capability.recipe.IRecipeHandler;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.api.machine.feature.IUIMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.api.machine.property.GTMachineModelProperties;
import com.gregtechceu.gtceu.api.machine.trait.RecipeHandlerList;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.pattern.BlockPattern;
import com.gregtechceu.gtceu.api.pattern.MultiblockState;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.recipe.chance.logic.ChanceLogic;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.api.recipe.content.ContentModifier;
import com.gregtechceu.gtceu.api.recipe.modifier.ParallelLogic;
import com.gregtechceu.gtceu.api.recipe.RecipeHelper;
import com.gregtechceu.gtceu.common.machine.multiblock.part.ItemBusPartMachine;
import com.gregtechceu.gtceu.common.data.GTSoundEntries;
import com.hoshino.gregsteamexpansion.GregSteamExpansion;
import com.hoshino.gregsteamexpansion.machine.multiblock.BatchStateMachine;
import com.hoshino.gregsteamexpansion.machine.multiblock.PendingOutputBuffer;
import com.hoshino.gregsteamexpansion.machine.multiblock.SteamBudget;
import com.hoshino.gregsteamexpansion.machine.multiblock.SteamPartCollector;
import com.hoshino.gregsteamexpansion.machine.multiblock.SteamProcessorUI;
import com.hoshino.gregsteamexpansion.machine.multiblock.SteamStatusText;
import com.hoshino.gregsteamexpansion.machine.multiblock.part.SteamExhaustHatchMachine;
import com.hoshino.gregsteamexpansion.machine.multiblock.part.SteamSupplyHatchPartMachine;
import com.hoshino.gregsteamexpansion.registry.GSERecipeTypes;

import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;

import net.minecraft.ChatFormatting;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fluids.FluidStack;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.ParametersAreNonnullByDefault;

/**
 * 蒸汽粉碎机 / 大型蒸汽粉碎机 shared base (steam-crushers.md 共用机器基类).
 *
 * <p>Owns the ore-crushing batch engine: slot-ordered recipe selection on the
 * single input bus, single-recipe parallel batches, atomic input consumption,
 * per-tick atomic steam withdrawal across all supply hatches (stable position
 * order, simulate then execute), worst-case output precheck, exactly-once
 * chance roll with a persisted pending-output list, working control, the
 * steam-shortage / structure-loss progress rollback to 1 tick, and full state
 * clearing when the controller itself is removed.</p>
 *
 * <p>The concrete classes only supply the structure pattern, the fixed
 * parallel cap (8 / 64), the legal interface set with its count rules and
 * whether a Steam Exhaust Hatch is required. The base never defaults to
 * requiring an exhaust hatch or several buses, so the large crusher's rules
 * cannot leak into the small one (steam-crushers.md 共用机器基类).</p>
 */
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public abstract class AbstractSteamCrusherMachine extends MultiblockControllerMachine
        implements IControllable, IRecipeCapabilityHolder, IUIMachine, com.gregtechceu.gtceu.api.machine.feature.IMachineLife {

    protected static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(
            AbstractSteamCrusherMachine.class, MultiblockControllerMachine.MANAGED_FIELD_HOLDER);

    /** Fixed ore-crushing duration for steam consumers (ore-crushing.md 1.5×). */
    public static final int DURATION_TICKS = 600;
    /**
     * Fixed per-operation steam cost (20260908 大幅上调 ×50, 用户定案): 每并行
     * 200 mB/t × 600 tick = 120,000 mB/份——以重蒸汽溢价承载矿石 4× 主产物
     * 的高价值产出, 与锅炉房(6,000–48,000 mB/t)的产能档位对齐。
     */
    public static final int STEAM_PER_OPERATION_MB = 120_000;
    /** Per-tick demand of a full-parallel batch: 200 × P mB/t. */
    public static final int STEAM_PER_TICK_PER_PARALLEL = 200;
    /** Each physical Steam Supply Hatch can provide at most this much per tick. */
    public static final long PER_HATCH_STEAM_CAP_MB = SteamBudget.PHYSICAL_HATCH_LIMIT_MB;
    /** Heat damage of one large-crusher exhaust damage cycle. */
    public static final float EXHAUST_DAMAGE = 12.0F;

    //////////////////////////////////////
    // ***** Persisted state ******//
    //////////////////////////////////////

    /** Work-enabled flag shared by the power button, soft hammer and covers. */
    @Persisted
    private boolean workingEnabled = true;
    /** Whether the locked-batch fields below describe a live batch. */
    @Persisted
    private boolean hasBatch = false;
    @Persisted
    private String batchRecipeId = "";
    @Persisted
    private int batchParallel = 0;
    @Persisted
    private int batchProgress = 0;
    /** Locked per-tick demand: 4 × P mB/t. */
    @Persisted
    private long batchSteamPerTickMb = 0;
    /** Locked batch total: 120,000 × P mB. */
    @Persisted
    private long batchTotalSteamMb = 0;
    /** One copy of the locked input item, for the GUI recipe display. */
    @Persisted
    private ItemStack batchInputDisplay = ItemStack.EMPTY;
    /** Finished products waiting for output space (chances rolled exactly once). */
    @Persisted
    private final List<ItemStack> pendingOutputs = new ArrayList<>();
    private final PendingOutputBuffer pendingBuffer = new PendingOutputBuffer(pendingOutputs);
    /** Pending-output persistence format version (safe default on mismatch). */
    @Persisted
    private byte pendingDataVersion = 1;
    /** Large crusher only: accumulated actually-running ticks towards the next exhaust strike. */
    @Persisted
    private long exhaustDamageTimer = 0;

    //////////////////////////////////////
    // ***** Runtime state ******//
    //////////////////////////////////////

    @Nullable
    private TickableSubscription tickSubscription;
    private final SteamPartCollector partCollector = new SteamPartCollector();
    /** 供汽仓 stable position order (steam-crushers.md 蒸汽消耗). */
    private final List<SteamSupplyHatchPartMachine> supplyHatches = partCollector.supplyHatches();
    private final SteamBudget steamBudget = new SteamBudget(supplyHatches);
    private final BatchStateMachine batchState = new BatchStateMachine();
    private final List<IMultiPart> inputBuses = partCollector.inputParts();
    /** 输出总线 stable order: ME first, then block position (steam-crushers.md). */
    private final List<ItemBusPartMachine> outputBuses = partCollector.outputBuses();
    private final List<SteamExhaustHatchMachine> exhaustHatches = partCollector.exhaustHatches();
    /** False when the post-formation interface count rules failed. */
    private boolean interfaceCountsValid = true;
    private boolean exhaustBlocked = false;
    private int exhaustFeedbackTimer = 0;
    /** Live recipe instance re-resolved from {@link #batchRecipeId} after reloads. */
    @Nullable
    private GTRecipe batchRecipe;
    /** Working sound handle (client only). */
    @Nullable
    @OnlyIn(Dist.CLIENT)
    private Object workingSound;
    /** Part recipe handlers aggregated on formation (WorkableMultiblockMachine wiring). */
    private final Map<IO, List<RecipeHandlerList>> capabilitiesProxy = new EnumMap<>(IO.class);
    private final Map<IO, Map<RecipeCapability<?>, List<IRecipeHandler<?>>>> capabilitiesFlat = new EnumMap<>(IO.class);

    protected AbstractSteamCrusherMachine(IMachineBlockEntity holder) {
        super(holder);
    }

    @Override
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }

    //////////////////////////////////////
    // ***** Machine-specific hooks ******//
    //////////////////////////////////////

    /** Fixed parallel cap: 8 (small) / 64 (large). */
    public abstract int maximumParallel();

    /** Whether the structure requires exactly one Steam Exhaust Hatch. */
    protected abstract boolean requiresExhaustHatch();

    /** True for the large crusher (exhaust feedback + damage cycles). */
    protected boolean hasExhaustHazard() {
        return false;
    }

    //////////////////////////////////////
    // ***** Pattern ******//
    //////////////////////////////////////

    @Override
    public abstract BlockPattern getPattern();

    @Override
    public boolean checkPattern() {
        MultiblockState state = getMultiblockState();
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
            // Interface count rules failed: treat as 结构未成型 (post-check of
            // the cross-type counts the per-predicate pattern limits cannot express).
            onStructureInvalid();
            return;
        }
        if (tickSubscription == null) {
            tickSubscription = subscribeServerTick(this::crusherServerTick);
        }
        updateWorkingAppearance();
    }

    @Override
    public void onStructureInvalid() {
        super.onStructureInvalid();
        // 结构失效: keep the batch and locked parameters, roll progress back to
        // 1 tick (steam-crushers.md 结构失效). Exhaust timers freeze, not clear.
        batchProgress = batchState.invalidate(batchProgress, hasBatch);
        exhaustBlocked = false;
        partCollector.clear();
        capabilitiesProxy.clear();
        capabilitiesFlat.clear();
        updateWorkingAppearance();
    }

    /**
     * Post-formation check of the interface count rules the pattern cannot
     * express across types (steam-crushers.md 圆筒接口): exactly one input bus,
     * exactly the required exhaust hatches, at least one output bus and at
     * least one supply hatch. Ordinary casing positions
     * impose no total-interface cap.
     */
    private boolean validateInterfaceCounts() {
        int expectedExhaust = requiresExhaustHatch() ? 1 : 0;
        return partCollector.validate(new SteamPartCollector.InterfaceRules(
                1, 1,
                1, 1,
                expectedExhaust,
                0, 0,
                true,
                0, 0));
    }

    private void collectParts() {
        capabilitiesProxy.clear();
        capabilitiesFlat.clear();
        partCollector.collect(this);
        for (SteamPartCollector.HandlerBinding binding : partCollector.recipeHandlers()) {
            addHandlerList(binding.handlers());
        }
    }

    @NotNull
    @Override
    public Map<IO, List<RecipeHandlerList>> getCapabilitiesProxy() {
        return capabilitiesProxy;
    }

    @NotNull
    @Override
    public Map<IO, Map<RecipeCapability<?>, List<IRecipeHandler<?>>>> getCapabilitiesFlat() {
        return capabilitiesFlat;
    }

    //////////////////////////////////////
    // ***** Ticking ******//
    //////////////////////////////////////

    private void crusherServerTick() {
        Level level = getLevel();
        if (level == null || level.isClientSide) {
            return;
        }
        batchState.beginTick();

        // 待输出优先送出 (also while paused: delivering is not recipe work).
        if (isFormed() && !pendingOutputs.isEmpty() && !deliverPendingOutputs()) {
            batchState.freeze(BatchStateMachine.HoldReason.OUTPUTS);
        }
        if (!pendingOutputs.isEmpty() && batchState.isWaitingFor(BatchStateMachine.HoldReason.OUTPUTS)) {
            updateWorkingAppearance();
            return;
        }

        // 主动暂停: freeze progress and locked parameters, no rollback.
        if (!isWorkingEnabled()) {
            batchState.freeze(BatchStateMachine.HoldReason.PAUSED);
            updateWorkingAppearance();
            return;
        }
        if (!isFormed() || !interfaceCountsValid) {
            // 结构失效 rollback already applied in onStructureInvalid.
            batchState.freeze(BatchStateMachine.HoldReason.INVALID_STRUCTURE);
            updateWorkingAppearance();
            return;
        }
        exhaustBlocked = requiresExhaustHatch() && !exhaustHatches.isEmpty()
                && exhaustHatches.get(0).isExhaustBlocked();
        if (exhaustBlocked) {
            // 排气受阻: stop drawing steam and freeze the original progress
            // without the 1-tick rollback (steam-crushers.md 控制与状态).
            batchState.freeze(BatchStateMachine.HoldReason.EXHAUST_BLOCKED);
            updateWorkingAppearance();
            return;
        }

        if (hasBatch) {
            runBatchTick();
        } else {
            tryStartBatch();
        }
        updateWorkingAppearance();
    }

    /** 逐 tick 原子取汽: full demand or nothing; shortfall rolls back to 1 tick. */
    private void runBatchTick() {
        if (batchRecipe == null) {
            // 区块/世界重载后按 id 重新解析; 配方消失时批次冻结等待.
            batchRecipe = findRecipeById();
            if (batchRecipe == null) {
                batchState.freeze(BatchStateMachine.HoldReason.RECIPE_MISSING);
                return;
            }
        }
        BatchStateMachine.TickResult tick = batchState.runTick(
                batchProgress, DURATION_TICKS, () -> drawSteam(batchSteamPerTickMb));
        batchProgress = tick.progress();
        if (!tick.consumed()) {
            updateWorkingAppearance();
            return;
        }
        if (hasExhaustHazard() && !exhaustHatches.isEmpty()) {
            runExhaustCycles(exhaustHatches.get(0));
        }
        if (tick.completed()) {
            completeBatch();
        }
    }

    /** Only ticks with a successful full steam withdrawal advance these cycles. */
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

    //////////////////////////////////////
    // ***** Batch start ******//
    //////////////////////////////////////

    /**
     * 配方选择: scan the single input bus by slot index from 0 upward; the first
     * slot whose recipe, input count and worst-case output space allow at least
     * 1 parallel starts the batch (steam-crushers.md 并行与处理时间). No
     * cross-batch cursor is kept.
     */
    private void tryStartBatch() {
        if (inputBuses.size() != 1) {
            return;
        }
        List<ItemStack> inputs = new ArrayList<>();
        for (var handlers : inputBuses.get(0).getRecipeHandlers()) {
            if (!handlers.isValid(IO.IN)) continue;
            for (var handler : handlers.getHandlerMap().getOrDefault(ItemRecipeCapability.CAP, List.of())) {
                if (!handler.shouldSearchContent()) continue;
                for (Object content : handler.getContents()) {
                    if (content instanceof ItemStack stack) inputs.add(stack);
                }
            }
        }
        for (ItemStack stack : inputs) {
            if (stack.isEmpty()) {
                continue;
            }
            GTRecipe recipe = findRecipeForStack(stack);
            if (recipe == null) {
                continue;
            }
            int byInputs = ParallelLogic.getMaxByInput(this, recipe, maximumParallel(), List.of());
            if (byInputs <= 0) {
                continue;
            }
            // 按可输出槽位决定并行: worst-case output (every chanced output
            // assumed successful, per-parallel) simulated through the EXACT
            // insertion path deliverPendingOutputs uses, so a batch that
            // passes this check can never end in output blocking later.
            int parallel = largestParallelThatFits(recipe, Math.min(maximumParallel(), byInputs));
            if (parallel <= 0) {
                continue;
            }

            GTRecipe multiplied = recipe.copy(ContentModifier.multiplier(parallel));
            multiplied.parallels = parallel;
            // 原子扣取: extract the full parallel input in one operation.
            var result = RecipeHelper.handleRecipe(this, multiplied, IO.IN,
                    multiplied.inputs, new HashMap<>(), false, false);
            if (!result.isSuccess()) {
                continue;
            }

            hasBatch = true;
            batchRecipe = recipe;
            batchRecipeId = recipe.getId().toString();
            batchParallel = parallel;
            batchProgress = 0;
            batchTotalSteamMb = (long) STEAM_PER_OPERATION_MB * parallel;
            batchSteamPerTickMb = (long) STEAM_PER_TICK_PER_PARALLEL * parallel;
            batchInputDisplay = stack.copyWithCount(1);
            GregSteamExpansion.LOGGER.debug("Steam crusher at {} started batch {} with parallel {} ({} mB total, {} mB/t)",
                    getPos(), batchRecipeId, parallel, batchTotalSteamMb, batchSteamPerTickMb);
            return;
        }
    }

    /**
     * First ore-crushing recipe whose single item input accepts the stack.
     * 功率门 (全局工作强度原则): 只接受基础输入功率不超过 MV (128 EU/t) 的配方——
     * 迁移配方的记录功率已 ×50 至 100 EU/t (MV 档), 固定 120,000 mB/份的蒸汽
     * 经济以 MV 上限为前提; 电力粉碎机按配方基准功率付费.
     */
    @Nullable
    private GTRecipe findRecipeForStack(ItemStack stack) {
        GTRecipeType type = GSERecipeTypes.ORE_CRUSHING_RECIPES;
        if (type == null || !hasCapabilityProxies()) {
            return null;
        }
        for (GTRecipe recipe : type.getRecipesInCategory(type.getCategory())) {
            List<Content> inputs = recipe.inputs.get(ItemRecipeCapability.CAP);
            if (inputs == null || inputs.size() != 1) {
                continue;
            }
            if (inputs.get(0).content instanceof net.minecraft.world.item.crafting.Ingredient ingredient
                    && ingredient.test(stack)) {
                if (RecipeHelper.getRecipeEUtTier(recipe) > 2) {
                    continue;
                }
                return recipe;
            }
        }
        return null;
    }

    @Nullable
    private GTRecipe findRecipeById() {
        if (batchRecipeId.isEmpty()) {
            return null;
        }
        ResourceLocation id = ResourceLocation.tryParse(batchRecipeId);
        if (id == null) {
            return null;
        }
        GTRecipeType type = GSERecipeTypes.ORE_CRUSHING_RECIPES;
        if (type == null) {
            return null;
        }
        for (GTRecipe recipe : type.getRecipesInCategory(type.getCategory())) {
            if (recipe.getId().equals(id)) {
                return recipe;
            }
        }
        return null;
    }

    /**
     * 按可输出槽位决定并行: from the candidate cap downward, find the largest
     * parallel whose WORST-CASE output list fits the output buses right now.
     * The worst case is the guaranteed main product plus every chanced output
     * at one full stack per parallel (steam-crushers.md 启动前按全部概率产物
     * 成功的最坏情况检查). The simulation reuses the exact insertion helper
     * deliverPendingOutputs commits with, so a batch admitted here always
     * completes into the buses instead of output blocking.
     */
    private int largestParallelThatFits(GTRecipe recipe, int candidate) {
        List<Content> itemOutputs = recipe.outputs.get(ItemRecipeCapability.CAP);
        if (itemOutputs == null || itemOutputs.isEmpty()) {
            return candidate;
        }
        // worst-case single-operation output: guaranteed products at their
        // sized amount + one of every chanced product
        List<ItemStack> perOperation = new ArrayList<>();
        for (Content content : itemOutputs) {
            ItemStack stack = representativeStackOf(content);
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            if (content.chance >= content.maxChance) {
                perOperation.add(stack);
            } else {
                perOperation.add(stack.copyWithCount(1));
            }
        }
        if (perOperation.isEmpty()) {
            return candidate;
        }
        for (int parallel = candidate; parallel >= 1; parallel--) {
            if (worstCaseFits(perOperation, parallel)) {
                return parallel;
            }
        }
        return 0;
    }

    /** Worst-case output for `parallel` operations, simulated on the buses. */
    private boolean worstCaseFits(List<ItemStack> perOperation, int parallel) {
        List<ItemStack> simulation = new ArrayList<>();
        for (ItemStack stack : perOperation) {
            ItemStack scaled = stack.copy();
            scaled.setCount(Math.min(scaled.getMaxStackSize(),
                    (int) Math.min(Integer.MAX_VALUE, (long) scaled.getCount() * parallel)));
            simulation.add(scaled);
        }
        // merge equal stacks first so the simulation respects stacking capacity
        PendingOutputBuffer.mergeItems(simulation);
        return PendingOutputBuffer.itemsFit(simulation, outputBuses);
    }

    /** Representative stack of an output Content (sized amount preserved). */
    @Nullable
    private ItemStack representativeStackOf(Content content) {
        var ingredient = ItemRecipeCapability.CAP.of(content.content);
        if (ingredient == null) {
            return null;
        }
        ItemStack[] items = ingredient.getItems();
        if (items.length == 0 || items[0].isEmpty()) {
            return null;
        }
        ItemStack stack = items[0].copy();
        if (content.content instanceof com.gregtechceu.gtceu.api.recipe.ingredient.SizedIngredient sized) {
            stack.setCount(Math.max(1, sized.getAmount()));
        }
        return stack;
    }

    /**
     * 配方完成: one chance roll, products persisted to the pending list first,
     * then delivered atomically (steam-crushers.md 并行与处理时间).
     */
    private void completeBatch() {
        if (batchRecipe == null) {
            hasBatch = false;
            return;
        }
        GTRecipe multiplied = batchRecipe.copy(ContentModifier.multiplier(batchParallel));
        multiplied.parallels = batchParallel;
        int recipeTier = RecipeHelper.getPreOCRecipeEuTier(multiplied);
        int chanceTier = recipeTier + multiplied.ocLevel;
        var chanceFunction = multiplied.getType().getChanceFunction();
        List<ItemStack> produced = new ArrayList<>();
        multiplied.outputs.forEach((capability, contents) -> {
            if (capability != ItemRecipeCapability.CAP) {
                return;
            }
            ChanceLogic logic = multiplied.getChanceLogicForCapability(capability, IO.OUT, false);
            List<Content> rolled = logic.roll(capability, new ArrayList<>(contents), chanceFunction,
                    recipeTier, chanceTier, null, 1);
            // times=1: the parallel quantity is ALREADY in the copied outputs
            // (SizedIngredient amount × P). ChanceLogic.OR's `times` would
            // multiply the guaranteed part AGAIN (output = 3 × P²).
            produced.addAll(materializeItemContents(rolled));
        });
        pendingBuffer.addMergedItems(produced);

        hasBatch = false;
        batchRecipe = null;
        batchProgress = 0;
        batchRecipeId = "";
        batchInputDisplay = ItemStack.EMPTY;
        deliverPendingOutputs();
    }

    /**
     * 待输出整批原子输出: simulate the complete list over the stable bus order
     * first, and only commit the same plan when every item can be received
     * (steam-crushers.md 输出模拟必须生成完整分配计划).
     */
    private boolean deliverPendingOutputs() {
        return pendingBuffer.deliverItems(outputBuses);
    }

    /**
     * Official content materialization (mirrors NotifiableItemStackHandler):
     * item contents hold Ingredients (usually SizedIngredient) — take the
     * representative stack and re-apply the sized amount.
     */
    public static List<ItemStack> materializeItemContents(List<Content> rolled) {
        List<ItemStack> stacks = new ArrayList<>();
        for (Content content : rolled) {
            var ingredient = ItemRecipeCapability.CAP.of(content.content);
            if (ingredient == null) {
                continue;
            }
            ItemStack[] items = ingredient.getItems();
            if (items.length == 0 || items[0].isEmpty()) {
                continue;
            }
            ItemStack stack = items[0].copy();
            int amount = content.content instanceof com.gregtechceu.gtceu.api.recipe.ingredient.SizedIngredient sized
                    ? sized.getAmount()
                    : stack.getCount();
            stack.setCount(Math.max(1, amount));
            stacks.add(stack);
        }
        return stacks;
    }

    //////////////////////////////////////
    // ***** Steam supply ******//
    //////////////////////////////////////

    /**
     * 原子取汽: simulate the full per-tick demand across all supply hatches in
     * stable position order and only execute the same plan when every hatch can
     * deliver its share. Each physical supply hatch is capped at 1,200 mB/t.
     */
    private boolean drawSteam(long amountMb) {
        return steamBudget.drawSteam(amountMb, remaining -> GregSteamExpansion.LOGGER.warn(
                "Steam crusher at {} draw execution fell short of the simulated plan by {} mB",
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
     * advanced the recipe count (steam-crushers.md 共用表现规则).
     */
    private void updateWorkingAppearance() {
        boolean active = isFormed() && interfaceCountsValid && isWorkingEnabled()
                && !exhaustBlocked && batchState.consumedThisTick();
        var status = active ? RecipeLogic.Status.WORKING : RecipeLogic.Status.IDLE;
        var renderState = getRenderState();
        if (renderState.hasProperty(GTMachineModelProperties.RECIPE_LOGIC_STATUS)
                && renderState.getValue(GTMachineModelProperties.RECIPE_LOGIC_STATUS) != status) {
            setRenderState(renderState.setValue(GTMachineModelProperties.RECIPE_LOGIC_STATUS, status));
        }
        updateWorkingSound(active);
    }

    /**
     * GTSoundEntries.MACERATOR loop while working, honouring the global machine
     * sound toggle; silence on every non-working state (steam-crushers.md 声音).
     */
    private void updateWorkingSound(boolean active) {
        if (isRemote()) {
            updateWorkingSoundClient(active);
        }
    }

    @OnlyIn(Dist.CLIENT)
    private void updateWorkingSoundClient(boolean active) {
        boolean shouldPlay = active && com.gregtechceu.gtceu.config.ConfigHolder.INSTANCE.machines.machineSounds;
        if (shouldPlay) {
            if (workingSound instanceof com.gregtechceu.gtceu.api.sound.AutoReleasedSound soundEntry) {
                if (soundEntry.soundEntry == GTSoundEntries.MACERATOR && !soundEntry.isStopped()) {
                    return;
                }
                soundEntry.release();
                workingSound = null;
            }
            workingSound = GTSoundEntries.MACERATOR.playAutoReleasedSound(
                () -> isFormed() && interfaceCountsValid && isWorkingEnabled() && !exhaustBlocked
                        && batchState.consumedThisTick()
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
     * 服务端权威状态 (steam-crushers.md 控制器界面优先级表):
     * 结构未成型 > 蒸汽排气受阻 > 输出堵塞 > 主动暂停 > 蒸汽不足 > 运行中 > 待机.
     */
    public String getStatusId() {
        if (!isFormed() || !interfaceCountsValid) {
            return "invalid_structure";
        }
        if (requiresExhaustHatch() && exhaustBlocked) {
            return "exhaust_obstructed";
        }
        if (!pendingOutputs.isEmpty()) {
            return "insufficient_outputs";
        }
        if (!isWorkingEnabled()) {
            return "working_disabled";
        }
        if (batchState.isWaitingFor(BatchStateMachine.HoldReason.STEAM)) {
            return "low_steam";
        }
        if (hasBatch) {
            return "working";
        }
        return "idle";
    }

    public Component getStatusText() {
        return SteamStatusText.text(getStatusId());
    }

    public ChatFormatting getStatusColor() {
        return SteamStatusText.color(getStatusId());
    }

    //////////////////////////////////////
    // ***** Controller UI ******//
    //////////////////////////////////////

    private static final String UI_PREFIX = "gregsteamexpansion.machine.steam_crusher.ui.";

    /**
     * 单页可滚动运行信息页, fixed row order, power button outside the scroll
     * area (steam-crushers.md 运行信息页布局与格式). Shared layout for both
     * crushers; the parallel cap differs by machine.
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
        y = SteamProcessorUI.infoRow(scroll, y, UI_PREFIX + "recipe",
                () -> hasBatch ? batchInputDisplay.getHoverName().getString() : "—", ChatFormatting.WHITE);
        y = SteamProcessorUI.infoRow(scroll, y, UI_PREFIX + "progress", this::progressText, ChatFormatting.WHITE);
        y = SteamProcessorUI.infoRow(scroll, y, UI_PREFIX + "parallel",
                () -> (hasBatch ? batchParallel + " / " : "— / ") + maximumParallel(), ChatFormatting.WHITE);
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

    /** `45.0%（270 / 600 tick）`; completed-but-undelivered stays at 100%. */
    private String progressText() {
        return SteamProcessorUI.progress(hasBatch, batchProgress, DURATION_TICKS);
    }

    /** 当前每刻需求为 4×P；仅运行中且成功扣取蒸汽时才算实际消耗。 */
    private String demandText() {
        return SteamProcessorUI.demand(currentSteamDemandPerTick(), batchSteamPerTickMb,
                batchState.consumedThisTick(), UI_PREFIX + "not_consuming");
    }

    /** `128（3 种）` style pending summary; `—` when nothing is pending. */
    private String pendingSummaryText() {
        return SteamProcessorUI.pendingSummary(pendingOutputs, List.of(), UI_PREFIX + "pending_summary");
    }

    /** Hover list of every pending item in the persisted stable order. */
    private List<Component> pendingDetailTooltips() {
        return SteamProcessorUI.pendingTooltips(pendingOutputs, List.of(),
                UI_PREFIX + "pending_empty", UI_PREFIX + "pending_detail");
    }

    //////////////////////////////////////
    // ***** Jade snapshot ******//
    //////////////////////////////////////

    public String getBatchRecipeId() {
        return batchRecipeId;
    }

    public ItemStack getBatchInputDisplay() {
        return batchInputDisplay;
    }

    public int getBatchProgress() {
        return batchProgress;
    }

    public int getBatchDuration() {
        return DURATION_TICKS;
    }

    public int getBatchParallel() {
        return batchParallel;
    }

    public long getBatchSteamPerTick() {
        return currentSteamDemandPerTick();
    }

    private long currentSteamDemandPerTick() {
        if (!hasBatch) return 0;
        String status = getStatusId();
        return status.equals("working") || status.equals("low_steam") ? batchSteamPerTickMb : 0;
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

    public boolean isOutputBlocked() {
        return !pendingOutputs.isEmpty();
    }

    /** 拆除清理: batch, pending outputs and exhaust timer never survive. */
    @Override
    public void onMachineRemoved() {
        hasBatch = false;
        batchRecipe = null;
        batchRecipeId = "";
        batchProgress = 0;
        batchParallel = 0;
        batchTotalSteamMb = 0;
        batchSteamPerTickMb = 0;
        batchInputDisplay = ItemStack.EMPTY;
        pendingBuffer.clear();
        exhaustDamageTimer = 0;
        batchState.reset();
    }
}
