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
import com.gregtechceu.gtceu.common.data.GTMaterials;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.gregtechceu.gtceu.common.machine.multiblock.part.ItemBusPartMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.part.SteamHatchPartMachine;
import com.gregtechceu.gtceu.common.data.GTSoundEntries;
import com.hoshino.gregsteamexpansion.GregSteamExpansion;
import com.hoshino.gregsteamexpansion.machine.multiblock.part.SteamExhaustHatchMachine;
import com.hoshino.gregsteamexpansion.machine.multiblock.part.SteamSupplyHatchPartMachine;
import com.gregtechceu.gtceu.utils.FormattingUtil;
import com.hoshino.gregsteamexpansion.registry.GSERecipeTypes;

import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import com.lowdragmc.lowdraglib.gui.widget.DraggableScrollableWidgetGroup;
import com.lowdragmc.lowdraglib.gui.widget.LabelWidget;
import com.gregtechceu.gtceu.api.gui.widget.ToggleButtonWidget;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;

import net.minecraft.ChatFormatting;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
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
    /** 2 EU/t × 600 tick × 2 mB/EU, fixed per recipe operation. */
    public static final int STEAM_PER_OPERATION_MB = 2400;
    /** Per-tick demand of a full-parallel batch: 4 × P mB/t. */
    public static final int STEAM_PER_TICK_PER_PARALLEL = 4;
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
    /** Locked batch total: 2,400 × P mB. */
    @Persisted
    private long batchTotalSteamMb = 0;
    /** One copy of the locked input item, for the GUI recipe display. */
    @Persisted
    private ItemStack batchInputDisplay = ItemStack.EMPTY;
    /** Finished products waiting for output space (chances rolled exactly once). */
    @Persisted
    private final List<ItemStack> pendingOutputs = new ArrayList<>();
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
    /** 供汽仓 stable position order (steam-crushers.md 蒸汽消耗). */
    private final List<SteamSupplyHatchPartMachine> supplyHatches = new ArrayList<>();
    private final List<ItemBusPartMachine> inputBuses = new ArrayList<>();
    /** 输出总线 stable order: ME first, then block position (steam-crushers.md). */
    private final List<ItemBusPartMachine> outputBuses = new ArrayList<>();
    private final List<SteamExhaustHatchMachine> exhaustHatches = new ArrayList<>();
    /** False when the post-formation interface count rules failed. */
    private boolean interfaceCountsValid = true;
    private boolean exhaustBlocked = false;
    private int exhaustFeedbackTimer = 0;
    /** Whether this tick actually consumed the full steam demand. */
    private boolean lastTickConsumedSteam = false;
    private boolean waitingForSteam = false;
    private boolean waitingForOutputs = false;
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

    /** Minimum bronze steam machine casings among the candidate positions. */
    protected abstract int minimumCasings();

    /** Total candidate (replaceable) positions in the structure. */
    protected abstract int candidatePositions();

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
        if (hasBatch) {
            batchProgress = Math.min(batchProgress, 1);
        }
        exhaustBlocked = false;
        lastTickConsumedSteam = false;
        supplyHatches.clear();
        inputBuses.clear();
        outputBuses.clear();
        exhaustHatches.clear();
        capabilitiesProxy.clear();
        capabilitiesFlat.clear();
        updateWorkingAppearance();
    }

    /**
     * Post-formation check of the interface count rules the pattern cannot
     * express across types (steam-crushers.md 圆筒接口): exactly one input bus,
     * exactly the required exhaust hatches, at least one output bus and supply
     * hatch, and their combined limit so the candidate positions keep the
     * minimum bronze steam machine casings.
     */
    private boolean validateInterfaceCounts() {
        if (inputBuses.size() != 1) {
            return false;
        }
        int expectedExhaust = requiresExhaustHatch() ? 1 : 0;
        if (exhaustHatches.size() != expectedExhaust) {
            return false;
        }
        if (outputBuses.size() < 1 || supplyHatches.size() < 1) {
            return false;
        }
        int interfaces = inputBuses.size() + outputBuses.size() + supplyHatches.size() + exhaustHatches.size();
        if (outputBuses.size() + supplyHatches.size() > 15) {
            return false;
        }
        return candidatePositions() - interfaces >= minimumCasings();
    }

    private void collectParts() {
        supplyHatches.clear();
        inputBuses.clear();
        outputBuses.clear();
        exhaustHatches.clear();
        capabilitiesProxy.clear();
        capabilitiesFlat.clear();
        it.unimi.dsi.fastutil.longs.Long2ObjectMap<IO> ioMap = getMultiblockState().getMatchContext()
                .getOrCreate("ioMap", it.unimi.dsi.fastutil.longs.Long2ObjectMaps::emptyMap);
        for (IMultiPart part : getParts()) {
            IO io = ioMap.getOrDefault(part.self().getPos().asLong(), IO.BOTH);
            if (io == IO.NONE) continue;
            for (RecipeHandlerList handlerList : part.getRecipeHandlers()) {
                if (!handlerList.isValid(io)) continue;
                addHandlerList(handlerList);
            }
            if (part instanceof SteamSupplyHatchPartMachine supplyHatch) {
                supplyHatches.add(supplyHatch);
            } else if (part instanceof SteamExhaustHatchMachine exhaustHatch) {
                exhaustHatches.add(exhaustHatch);
            } else if (part instanceof ItemBusPartMachine bus) {
                if (bus.getInventory().getHandlerIO() == IO.OUT) {
                    outputBuses.add(bus);
                } else {
                    inputBuses.add(bus);
                }
            }
        }
        // Stable orders (steam-crushers.md): supply hatches and output buses by
        // block position; ME output buses first within the output group.
        supplyHatches.sort(Comparator.comparing(hatch -> hatch.self().getPos()));
        outputBuses.sort(Comparator
                .comparing((ItemBusPartMachine bus) -> !isMeBus(bus))
                .thenComparing(bus -> bus.self().getPos()));
        inputBuses.sort(Comparator.comparing(bus -> bus.self().getPos()));
    }

    /** ME parts are detected by definition id; AE2 classes are never loaded. */
    private static boolean isMeBus(IMultiPart part) {
        String path = part.self().getDefinition().getId().getPath();
        return path.startsWith("me_");
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
        waitingForSteam = false;
        waitingForOutputs = false;

        // 待输出优先送出 (also while paused: delivering is not recipe work).
        if (isFormed() && !pendingOutputs.isEmpty() && !deliverPendingOutputs()) {
            waitingForOutputs = true;
        }
        if (!pendingOutputs.isEmpty() && waitingForOutputs) {
            lastTickConsumedSteam = false;
            updateWorkingAppearance();
            return;
        }

        // 主动暂停: freeze progress and locked parameters, no rollback.
        if (!isWorkingEnabled()) {
            lastTickConsumedSteam = false;
            updateWorkingAppearance();
            return;
        }
        if (!isFormed() || !interfaceCountsValid) {
            // 结构失效 rollback already applied in onStructureInvalid.
            lastTickConsumedSteam = false;
            updateWorkingAppearance();
            return;
        }
        exhaustBlocked = requiresExhaustHatch() && !exhaustHatches.isEmpty()
                && exhaustHatches.get(0).isExhaustBlocked();
        if (exhaustBlocked) {
            // 排气受阻: stop drawing steam and freeze the original progress
            // without the 1-tick rollback (steam-crushers.md 控制与状态).
            lastTickConsumedSteam = false;
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
                lastTickConsumedSteam = false;
                return;
            }
        }
        if (!drawSteam(batchSteamPerTickMb)) {
            lastTickConsumedSteam = false;
            waitingForSteam = true;
            // 缺汽回退: keep the batch and locked parameters, progress → 1 tick.
            batchProgress = Math.min(batchProgress, 1);
            updateWorkingAppearance();
            return;
        }
        lastTickConsumedSteam = true;
        if (hasExhaustHazard() && !exhaustHatches.isEmpty()) {
            runExhaustCycles(exhaustHatches.get(0));
        }
        batchProgress++;
        if (batchProgress >= DURATION_TICKS) {
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
        var inventory = inputBuses.get(0).getInventory();
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
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
            // 最坏情况输出预检: every chanced output assumed successful.
            int parallel = ParallelLogic.limitByOutputMerging(this, recipe,
                    Math.min(maximumParallel(), byInputs), capability -> false, List.of());
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

    /** First ore-crushing recipe whose single item input accepts the stack. */
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
                    recipeTier, chanceTier, null, multiplied.getTotalRuns());
            for (Content content : rolled) {
                if (capability.of(content.content) instanceof ItemStack itemStack && !itemStack.isEmpty()) {
                    produced.add(itemStack.copy());
                }
            }
        });
        mergeStacks(produced);
        pendingOutputs.addAll(produced);

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

    private ItemStack insertIntoBus(ItemBusPartMachine bus, ItemStack stack, boolean simulate) {
        if (stack.isEmpty()) {
            return stack;
        }
        return ItemHandlerHelper.insertItemStacked(bus.getInventory(), stack, simulate);
    }

    private static void mergeStacks(List<ItemStack> stacks) {
        for (int i = 0; i < stacks.size(); i++) {
            ItemStack keep = stacks.get(i);
            for (int j = stacks.size() - 1; j > i; j--) {
                ItemStack other = stacks.get(j);
                if (!keep.isEmpty() && ItemHandlerHelper.canItemStacksStack(keep, other)) {
                    int moved = Math.min(other.getCount(), keep.getMaxStackSize() - keep.getCount());
                    keep.grow(moved);
                    other.shrink(moved);
                    if (other.isEmpty()) {
                        stacks.remove(j);
                    }
                }
            }
        }
        stacks.removeIf(ItemStack::isEmpty);
    }

    //////////////////////////////////////
    // ***** Steam supply ******//
    //////////////////////////////////////

    /**
     * 原子取汽: simulate the full per-tick demand across all supply hatches in
     * stable position order and only execute the same plan when every hatch can
     * deliver its share. No machine-side per-hatch cap exists for the crushers.
     */
    private boolean drawSteam(long amountMb) {
        if (amountMb <= 0 || supplyHatches.isEmpty()) {
            return false;
        }
        long remaining = amountMb;
        for (SteamSupplyHatchPartMachine hatch : supplyHatches) {
            FluidStack simulated = hatch.tank.drain(steamFluid(remaining), IFluidHandler.FluidAction.SIMULATE);
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
            FluidStack drained = hatch.tank.drain(steamFluid(remaining), IFluidHandler.FluidAction.EXECUTE);
            remaining -= drained.getAmount();
            if (remaining <= 0) {
                break;
            }
        }
        if (remaining > 0) {
            GregSteamExpansion.LOGGER.warn(
                    "Steam crusher at {} draw execution fell short of the simulated plan by {} mB",
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
     * advanced the recipe count (steam-crushers.md 共用表现规则).
     */
    private void updateWorkingAppearance() {
        boolean active = isFormed() && interfaceCountsValid && isWorkingEnabled()
                && !exhaustBlocked && !waitingForOutputs && lastTickConsumedSteam;
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
                            && !waitingForOutputs && lastTickConsumedSteam
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
        if (waitingForSteam) {
            return "low_steam";
        }
        if (hasBatch) {
            return "working";
        }
        return "idle";
    }

    public Component getStatusText() {
        return switch (getStatusId()) {
            case "invalid_structure" -> Component.translatable("gtceu.multiblock.invalid_structure");
            case "exhaust_obstructed" -> Component.translatable(
                    "gregsteamexpansion.multiblock.steam_exhaust_hatch_obstructed");
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
            case "working_disabled", "low_steam" -> ChatFormatting.YELLOW;
            case "working" -> ChatFormatting.GREEN;
            default -> ChatFormatting.GRAY;
        };
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
        int uiWidth = 260;
        int uiHeight = 170;
        var ui = new ModularUI(uiWidth, uiHeight, this, entityPlayer)
                .background(GuiTextures.BACKGROUND);
        var scroll = new DraggableScrollableWidgetGroup(5, 5, uiWidth - 10, uiHeight - 32);
        int y = 2;
        y = infoRow(scroll, y, UI_PREFIX + "status", () -> getStatusText().getString(), getStatusColor());
        y = infoRow(scroll, y, UI_PREFIX + "recipe",
                () -> hasBatch ? batchInputDisplay.getHoverName().getString() : "—", ChatFormatting.WHITE);
        y = infoRow(scroll, y, UI_PREFIX + "progress", this::progressText, ChatFormatting.WHITE);
        y = infoRow(scroll, y, UI_PREFIX + "parallel",
                () -> (hasBatch ? batchParallel + " / " : "— / ") + maximumParallel(), ChatFormatting.WHITE);
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
        // Hover lists the persisted pending items in their stable order, live.
        LabelWidget pendingValue = new LabelWidget(104, y, this::pendingSummaryText) {
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

    private int infoRow(DraggableScrollableWidgetGroup group, int y, String labelKey,
                        java.util.function.Supplier<String> value, ChatFormatting valueColor) {
        group.addWidget(new LabelWidget(2, y, () -> Component.translatable(labelKey).getString())
                .setTextColor(-1).setDropShadow(true));
        Integer rgb = valueColor.getColor();
        group.addWidget(new LabelWidget(104, y, value)
                .setTextColor(rgb == null ? -1 : (rgb.intValue() & 0xFFFFFF)).setDropShadow(true));
        return y + 10;
    }

    /** `45.0%（270 / 600 tick）`; completed-but-undelivered stays at 100%. */
    private String progressText() {
        if (!hasBatch) {
            return "—";
        }
        double percent = Math.round(Math.min(batchProgress, DURATION_TICKS) * 1000.0 / DURATION_TICKS) / 10.0;
        return Component.translatable(UI_PREFIX + "progress_format",
                String.format("%.1f", percent), FormattingUtil.formatNumbers(Math.min(batchProgress, DURATION_TICKS)),
                FormattingUtil.formatNumbers(DURATION_TICKS)).getString();
    }

    /** 锁定每刻需求 4×P; only "运行中" with a successful draw is actually consuming. */
    private String demandText() {
        if (!hasBatch) {
            return "0 mB/t";
        }
        String demand = FormattingUtil.formatNumbers(batchSteamPerTickMb) + " mB/t";
        if (!lastTickConsumedSteam) {
            return demand + " (" + Component.translatable(UI_PREFIX + "not_consuming").getString() + ")";
        }
        return demand;
    }

    /** `128（3 种）` style pending summary; `—` when nothing is pending. */
    private String pendingSummaryText() {
        if (pendingOutputs.isEmpty()) {
            return "—";
        }
        long total = 0;
        for (ItemStack stack : pendingOutputs) {
            total += stack.getCount();
        }
        int kinds = countPendingKinds();
        return Component.translatable(UI_PREFIX + "pending_summary",
                FormattingUtil.formatNumbers(total), kinds).getString();
    }

    private int countPendingKinds() {
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

    /** Hover list of every pending item in the persisted stable order. */
    private List<Component> pendingDetailTooltips() {
        if (pendingOutputs.isEmpty()) {
            return List.of(Component.translatable(UI_PREFIX + "pending_empty").withStyle(ChatFormatting.GRAY));
        }
        List<Component> tooltips = new ArrayList<>();
        tooltips.add(Component.translatable(UI_PREFIX + "pending_detail").withStyle(ChatFormatting.GRAY));
        for (ItemStack stack : pendingOutputs) {
            tooltips.add(Component.literal("- " + stack.getHoverName().getString() + " × "
                    + FormattingUtil.formatNumbers(stack.getCount())).withStyle(ChatFormatting.WHITE));
        }
        return tooltips;
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
        return hasBatch ? batchSteamPerTickMb : 0;
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
        return countPendingKinds();
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
        pendingOutputs.clear();
        exhaustDamageTimer = 0;
    }
}
