package com.hoshino.gregsteamexpansion.machine.multiblock;

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
import com.gregtechceu.gtceu.api.recipe.RecipeHelper;
import com.gregtechceu.gtceu.api.recipe.chance.logic.ChanceLogic;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.api.recipe.content.ContentModifier;
import com.gregtechceu.gtceu.api.recipe.modifier.ParallelLogic;
import com.gregtechceu.gtceu.common.data.GTMaterials;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.gregtechceu.gtceu.common.machine.multiblock.part.FluidHatchPartMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.part.ItemBusPartMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.part.SteamHatchPartMachine;
import com.gregtechceu.gtceu.utils.FormattingUtil;
import com.hoshino.gregsteamexpansion.GregSteamExpansion;
import com.hoshino.gregsteamexpansion.difficulty.Difficulty;
import com.hoshino.gregsteamexpansion.difficulty.GSEDifficultyState;
import com.hoshino.gregsteamexpansion.machine.multiblock.part.SteamExhaustHatchMachine;
import com.hoshino.gregsteamexpansion.registry.GSEFurnacePatterns;

import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import com.lowdragmc.lowdraglib.gui.widget.DraggableScrollableWidgetGroup;
import com.gregtechceu.gtceu.api.gui.widget.ToggleButtonWidget;
import com.lowdragmc.lowdraglib.gui.widget.LabelWidget;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;

import net.minecraft.ChatFormatting;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.ItemHandlerHelper;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.ParametersAreNonnullByDefault;

/**
 * 大型蓄热蒸汽熔炉 / Large Heat-Storage Steam Furnace controller
 * (large-heat-storage-steam-furnace.md).
 *
 * <p>Implemented: formation with three variable-size patterns (P0.7), the
 * temperature/preheating/cooling state machine with size-change reset (P0.2),
 * steam drawing from standard steam hatches with the per-hatch 1200 mB/t
 * machine-side cap and atomic simulated-then-executed withdrawal (P0.3), the
 * parallel batch recipe engine (single-recipe batches, LV cap, worst-case
 * output precheck, one-shot chance roll, atomic input/output, per-tick steam
 * demand with progress regression; P0.4/P0.5/P0.8 core), furnace/alloy recipe
 * modes with screwdriver switching, exhaust hatch wiring and the difficulty
 * downgrade migration (P0.6 machine side). Not wired yet: ME hatch scheduling
 * (P1.10), the controller GUI info page (P1.13) and kept-batch origin-size
 * tracking refinement.</p>
 */
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class LargeHeatStorageSteamFurnaceMachine extends MultiblockControllerMachine
        implements IControllable, IRecipeCapabilityHolder, IUIMachine {

    protected static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(
            LargeHeatStorageSteamFurnaceMachine.class, MultiblockControllerMachine.MANAGED_FIELD_HOLDER);

    public static final int COLD_TEMPERATURE = 20;
    public static final int MIN_WORKING_TEMPERATURE = 400;
    /** Steam per tick a single standard steam hatch may supply to this machine. */
    public static final int STEAM_PER_HATCH_LIMIT_MB = 1200;
    /** Heat damage of one exhaust damage cycle. */
    public static final float EXHAUST_DAMAGE = 12.0F;

    /** Nominal voltage cap in EU/t: recipes above LV are never searched. */
    public static final long MAX_RECIPE_EUT = 32;

    //////////////////////////////////////
    // ***** Persisted state ******//
    //////////////////////////////////////

    /** Outer width of the last successfully formed structure (0 = never formed). */
    @Persisted
    private int formedWidth = 0;
    /** Total height of the last successfully formed structure. */
    @Persisted
    private int formedHeight = 0;
    /** Current furnace temperature in °C. */
    @Persisted
    private int currentTemperature = COLD_TEMPERATURE;
    /** Steam accumulated towards the next +1°C, in 1/100 mB units for exactness. */
    @Persisted
    private long preheatProgressUnits = 0;
    /** Ticks accumulated towards the next +1°C. */
    @Persisted
    private int heatTimer = 0;
    /** Ticks accumulated towards the next −1°C. */
    @Persisted
    private int coolTimer = 0;
    /** Actual run ticks accumulated towards the next exhaust damage strike. */
    @Persisted
    private long exhaustDamageTimer = 0;
    /** Work-enabled flag shared by the power button, soft hammer and covers. */
    @Persisted
    private boolean workingEnabled = true;
    /** Last applied save difficulty ordinal, for one-shot downgrade migration. */
    @Persisted
    private int lastAppliedDifficulty = Difficulty.NORMAL.ordinal();

    /** 0 = furnace (default), 1 = alloy smelter (15×15 only). */
    @Persisted
    private int recipeMode = MODE_FURNACE;
    /** Whether the started batch fields below describe a live batch. */
    @Persisted
    private boolean hasBatch = false;
    @Persisted
    private long batchTotalSteamMb = 0;
    @Persisted
    private long batchSteamPerTickMb = 0;
    @Persisted
    private int batchDuration = 0;
    @Persisted
    private int batchProgress = 0;
    @Persisted
    private int batchParallel = 0;
    @Persisted
    private float batchSpeed = 1.0F;
    @Persisted
    private String batchRecipeId = "";
    @Persisted
    private int batchRecipeMode = MODE_FURNACE;
    /** Finished products waiting for output space (rolled exactly once). */
    @Persisted
    private final List<ItemStack> pendingOutputs = new ArrayList<>();
    /** Pending-output persistence format version (safe default on mismatch). */
    @Persisted
    private byte pendingDataVersion = 1;
    /** Outer size the current batch was started with (kept-batch rule). */
    @Persisted
    private int batchOriginWidth = 0;
    @Persisted
    private int batchOriginHeight = 0;
    /** 总线隔离 state (UI control arrives with the controller GUI). */
    @Persisted
    private boolean distinctBuses = false;

    public static final int MODE_FURNACE = 0;
    public static final int MODE_ALLOY = 1;
    /** Alloy smelter batches can only start at ≥ 1200°C. */
    public static final int ALLOY_START_TEMPERATURE = 1200;

    //////////////////////////////////////
    // ***** Runtime state ******//
    //////////////////////////////////////

    @Nullable
    private TickableSubscription tickSubscription;
    @Nullable
    private SteamExhaustHatchMachine exhaustHatch;
    private final List<SteamHatchPartMachine> steamHatches = new ArrayList<>();
    private final List<ItemBusPartMachine> outputBuses = new ArrayList<>();
    /** ME fluid input hatches: unlimited machine-side supply (AE2 present). */
    private final List<FluidHatchPartMachine> meSteamHatches = new ArrayList<>();
    /** True when any compatible ME fluid input hatch is in the structure. */
    private boolean steamUnlimited = false;
    /** Active bronze firebox positions of the formed structure ("vaBlocks"). */
    @Nullable
    private it.unimi.dsi.fastutil.longs.LongSet activeBlocks = null;
    private boolean fireboxActive = false;
    /** Width matched by the last successful pattern check. */
    private int matchedWidth = 0;
    private final BlockPattern[] cachedPatterns = new BlockPattern[GSEFurnacePatterns.WIDTHS.length];
    private boolean exhaustBlocked = false;
    private int exhaustFeedbackTimer = 0;
    /** Whether this tick actually consumed steam (drives status + exhaust). */
    private boolean lastTickConsumedSteam = false;
    /** Live recipe instance resolved from {@link #batchRecipeId}. */
    @Nullable
    private GTRecipe batchRecipe;
    private boolean waitingForOutputs = false;
    private boolean waitingForSteam = false;
    private boolean waitingForInputs = false;
    private final Map<RecipeCapability<?>, Object2IntOpenHashMap<?>> chanceCaches = new HashMap<>();
    /** Part recipe handlers aggregated on formation (WorkableMultiblockMachine wiring). */
    private final Map<IO, List<RecipeHandlerList>> capabilitiesProxy = new EnumMap<>(IO.class);
    private final Map<IO, Map<RecipeCapability<?>, List<IRecipeHandler<?>>>> capabilitiesFlat = new EnumMap<>(IO.class);

    public LargeHeatStorageSteamFurnaceMachine(IMachineBlockEntity holder) {
        super(holder);
    }

    @Override
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }

    //////////////////////////////////////
    // ***** Formation ******//
    //////////////////////////////////////

    @Override
    public boolean checkPattern() {
        MultiblockState state = getMultiblockState();
        for (int i = 0; i < GSEFurnacePatterns.WIDTHS.length; i++) {
            if (patternFor(GSEFurnacePatterns.WIDTHS[i]).checkPatternAt(state, false)) {
                matchedWidth = GSEFurnacePatterns.WIDTHS[i];
                return true;
            }
        }
        return false;
    }

    /**
     * The terminal auto-builds a fixed 15×15×6 structure and the tooltip shows
     * its dimensions, so the largest pattern is the canonical one
     * (large-heat-storage-steam-furnace.md 结构预览与终端自动搭建).
     */
    @Override
    public BlockPattern getPattern() {
        return patternFor(15);
    }

    private BlockPattern patternFor(int width) {
        for (int i = 0; i < GSEFurnacePatterns.WIDTHS.length; i++) {
            if (GSEFurnacePatterns.WIDTHS[i] != width) {
                continue;
            }
            if (cachedPatterns[i] == null) {
                cachedPatterns[i] = GSEFurnacePatterns.create(getDefinition(), width);
            }
            return cachedPatterns[i];
        }
        throw new IllegalArgumentException("Unsupported furnace width " + width);
    }

    @Override
    public void onStructureFormed() {
        super.onStructureFormed();
        collectParts();
        // Exhaust hatch sits H−2 layers above the bottom-layer controller.
        int newHeight = exhaustHatch != null
                ? exhaustHatch.self().getPos().getY() - getPos().getY() + 2
                : formedHeight;
        int newWidth = matchedWidth > 0 ? matchedWidth : formedWidth;
        if (formedWidth > 0 && (formedWidth != newWidth || formedHeight != newHeight)) {
            // 尺寸改变时的温度重置: batches survive, heat state resets to cold.
            resetTemperatureState();
        }
        formedWidth = newWidth;
        formedHeight = newHeight;
        migrateDifficulty();
        if (tickSubscription == null) {
            tickSubscription = subscribeServerTick(this::furnaceServerTick);
        }
        updateWorkingAppearance();
    }

    /** 尺寸改变时的温度重置: cold furnace, accumulation and timers cleared. */
    private void resetTemperatureState() {
        currentTemperature = COLD_TEMPERATURE;
        preheatProgressUnits = 0;
        heatTimer = 0;
        coolTimer = 0;
    }

    @Override
    public void onStructureInvalid() {
        // Keep the tick subscription: an invalid-but-loaded furnace still cools
        // at the idle rate (large-heat-storage-steam-furnace.md 结构失效).
        super.onStructureInvalid();
        exhaustBlocked = false;
        fireboxActive = false;
        updateFireboxBlocks(false);
        updateWorkingAppearance();
    }

    private void collectParts() {
        exhaustHatch = null;
        steamHatches.clear();
        outputBuses.clear();
        meSteamHatches.clear();
        steamUnlimited = false;
        // Aggregate part recipe handlers exactly like WorkableMultiblockMachine:
        // the ioMap recorded during the pattern check decides each part's IO.
        capabilitiesProxy.clear();
        capabilitiesFlat.clear();
        it.unimi.dsi.fastutil.longs.Long2ObjectMap<IO> ioMap = getMultiblockState().getMatchContext()
                .getOrCreate("ioMap", it.unimi.dsi.fastutil.longs.Long2ObjectMaps::emptyMap);
        activeBlocks = getMultiblockState().getMatchContext()
                .getOrDefault("vaBlocks", it.unimi.dsi.fastutil.longs.LongSets.emptySet());
        for (IMultiPart part : getParts()) {
            IO io = ioMap.getOrDefault(part.self().getPos().asLong(), IO.BOTH);
            if (io == IO.NONE) continue;
            for (RecipeHandlerList handlerList : part.getRecipeHandlers()) {
                if (!handlerList.isValid(io)) continue;
                addHandlerList(handlerList);
            }
            if (part instanceof SteamExhaustHatchMachine hatch) {
                exhaustHatch = hatch;
            } else if (part instanceof SteamHatchPartMachine steamHatch) {
                steamHatches.add(steamHatch);
            } else if (part instanceof FluidHatchPartMachine fluidHatch && isMeFluidInputHatch(part)) {
                // 兼容 ME 流体输入仓: unlimited machine-side steam supply.
                meSteamHatches.add(fluidHatch);
                steamUnlimited = true;
            } else if (part instanceof ItemBusPartMachine bus) {
                boolean meBus = part.self().getDefinition().getId().getPath().startsWith("me_");
                if (bus.getInventory().getHandlerIO() == IO.OUT) {
                    outputBuses.add(bus);
                    // ME 输出总线优先接收产物 (stable order preserved inside groups).
                    if (meBus && outputBuses.size() > 1) {
                        ItemBusPartMachine last = outputBuses.remove(outputBuses.size() - 1);
                        outputBuses.add(0, last);
                    }
                }
            }
        }
        GregSteamExpansion.LOGGER.debug("Furnace at {} formed {}x{}x{}: {} steam hatches, {} ME hatches, unlimited={}",
                getPos(), formedWidth, formedWidth, formedHeight, steamHatches.size(), meSteamHatches.size(),
                steamUnlimited);
    }

    /**
     * ME parts are detected by their definition id so AE2 classes are never
     * loaded from this mod (large-heat-storage-steam-furnace.md 首版范围与
     * 未来蒸汽仓扩展接口 / P1.10).
     */
    private static boolean isMeFluidInputHatch(IMultiPart part) {
        String path = part.self().getDefinition().getId().getPath();
        return path.equals("me_input_hatch") || path.equals("me_stocking_input_hatch");
    }

    private boolean isMeInputBus(IMultiPart part) {
        String path = part.self().getDefinition().getId().getPath();
        return path.equals("me_input_bus") || path.equals("me_stocking_input_bus");
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

    private void furnaceServerTick() {
        Level level = getLevel();
        if (level == null || level.isClientSide) {
            return;
        }
        Difficulty difficulty = currentDifficulty();
        migrateDifficulty();
        waitingForSteam = false;
        waitingForInputs = false;

        if (!isWorkingEnabled()) {
            lastTickConsumedSteam = false;
            coolDown(difficulty);
            updateWorkingAppearance();
            return;
        }
        if (!isFormed()) {
            // 结构失效: cool at the idle rate while loaded; kept batches freeze.
            lastTickConsumedSteam = false;
            coolDown(difficulty);
            return;
        }
        exhaustBlocked = exhaustHatch != null && exhaustHatch.isExhaustBlocked();
        if (exhaustBlocked) {
            // 排气受阻: stop drawing steam and working, cool at idle rate.
            lastTickConsumedSteam = false;
            coolDown(difficulty);
            updateWorkingAppearance();
            return;
        }

        // 待输出产物优先送出; 失败即输出堵塞 (停机冷却, 保留待输出列表).
        if (!pendingOutputs.isEmpty() && !deliverPendingOutputs()) {
            lastTickConsumedSteam = false;
            waitingForOutputs = true;
            coolDown(difficulty);
            updateWorkingAppearance();
            return;
        }

        if (hasBatch) {
            runBatchTick();
        } else if (currentTemperature >= startupTemperature()
                && (formedWidth == 15 || recipeMode == MODE_FURNACE)) {
            if (!tryStartBatch(difficulty) && currentTemperature < maxTemperature()) {
                // 没有可执行配方且仍有蒸汽输入 → 继续升温直至本档上限 (doc).
                tryPreheat(difficulty);
            }
        } else {
            tryPreheat(difficulty);
        }
        updateWorkingAppearance();
    }

    /** 运行一个已锁定批次: 每刻原子扣取蒸汽, 不足时进度回退至 1 tick. */
    private void runBatchTick() {
        // 保留批次只在其启动时的原尺寸结构下恢复运行 (等待恢复原尺寸).
        if (!matchesOriginalSize()) {
            lastTickConsumedSteam = false;
            return;
        }
        if (batchRecipe == null) {
            // 区块/世界重载后按 id 重新解析配方; 配方消失时批次冻结等待.
            batchRecipe = findRecipeById();
            if (batchRecipe == null) {
                lastTickConsumedSteam = false;
                return;
            }
        }
        waitingForSteam = !drawSteam(batchSteamPerTickMb);
        if (waitingForSteam) {
            lastTickConsumedSteam = false;
            // GTCEu 电力机器相同的进度回退行为
            batchProgress = Math.min(batchProgress, 1);
            coolDown(currentDifficulty());
            updateWorkingAppearance();
            return;
        }
        lastTickConsumedSteam = true;
        runExhaustCycles();
        batchProgress++;
        if (batchProgress >= batchDuration) {
            completeBatch();
        }
    }

    /**
     * 接取新批次: search the current mode's recipe table (LV cap 32 EU/t),
     * pick the largest parallel allowed by inputs, per-tick steam supply and
     * the worst-case output precheck, then consume the inputs exactly once and
     * lock every batch parameter.
     */
    private boolean tryStartBatch(Difficulty difficulty) {
        GTRecipe recipe = findRecipe();
        if (recipe == null) {
            waitingForInputs = true;
            return false;
        }
        int structureCap = maximumParallel();
        int byInputs = ParallelLogic.getMaxByInput(this, recipe, structureCap, List.of());
        if (byInputs <= 0) {
            waitingForInputs = true;
            return false;
        }
        int bySteam = steamLimitedParallel(recipe, difficulty);
        if (bySteam <= 0) {
            waitingForSteam = true;
            return false;
        }
        int candidate = Math.min(structureCap, Math.min(byInputs, bySteam));
        // 输出最坏情况预检: all chance products assumed successful.
        int parallel = ParallelLogic.limitByOutputMerging(this, recipe, candidate,
                capability -> false, List.of());
        if (parallel <= 0) {
            waitingForOutputs = true;
            return false;
        }

        // 锁定批次参数 (温度/速度/消耗按当前状态锁定, 运行中不再重算).
        long baseEu = RecipeHelper.getRealEUt(recipe).getTotalEU();
        long baseEnergy = baseEu * recipe.duration;
        double discount = steamDiscount();
        double processingMultiplier = difficulty.getProcessingSteamPercent() / 100.0;
        long totalSteam = (long) Math.ceil(baseEnergy * 2.0 * parallel * discount * processingMultiplier);
        double speed = speedMultiplier();
        int duration = Math.max(1, (int) Math.ceil(recipe.duration / speed));
        long perTick = (long) Math.ceil((double) totalSteam / duration);

        GTRecipe multiplied = recipe.copy(ContentModifier.multiplier(parallel));
        multiplied.parallels = parallel;
        // 原子吞取输入: 仅当全部输入可满足时执行提取
        var result = RecipeHelper.handleRecipe(this, multiplied, IO.IN,
                multiplied.inputs, new HashMap<>(), false, false);
        if (!result.isSuccess()) {
            waitingForInputs = true;
            return false;
        }

        hasBatch = true;
        batchRecipe = recipe;
        batchRecipeId = recipe.getId().toString();
        batchRecipeMode = recipeMode;
        batchParallel = parallel;
        batchTotalSteamMb = totalSteam;
        batchSteamPerTickMb = perTick;
        batchDuration = duration;
        batchProgress = 0;
        batchSpeed = (float) speed;
        batchOriginWidth = formedWidth;
        batchOriginHeight = formedHeight;
        GregSteamExpansion.LOGGER.debug("Furnace at {} started batch {} with parallel {} ({} mB total, {} mB/t over {} ticks)",
                getPos(), batchRecipeId, parallel, totalSteam, perTick, duration);
        return true;
    }

    /** 配方完成: roll chances exactly once, hold products, deliver atomically. */
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
            produced.addAll(com.hoshino.gregsteamexpansion.machine.multiblock.crusher.AbstractSteamCrusherMachine
                    .materializeItemContents(rolled));
        });
        mergeStacks(produced);
        pendingOutputs.addAll(produced);

        // 批次结束; 温度低于启动温度时自然由预热路径接管.
        hasBatch = false;
        batchRecipe = null;
        batchProgress = 0;
        deliverPendingOutputs();
    }

    /** 待输出整批原子输出: simulate the full list first, then commit. */
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

    /** 每刻蒸汽上限允许的最大并行: ceil(E·2·P·D·C / duration) ≤ N × 1200. */
    private int steamLimitedParallel(GTRecipe recipe, Difficulty difficulty) {
        long limit = (long) steamHatches.size() * STEAM_PER_HATCH_LIMIT_MB;
        if (limit <= 0) {
            return 0;
        }
        long baseEu = RecipeHelper.getRealEUt(recipe).getTotalEU();
        long baseEnergy = baseEu * recipe.duration;
        double discount = steamDiscount();
        double processingMultiplier = difficulty.getProcessingSteamPercent() / 100.0;
        double speed = speedMultiplier();
        int duration = Math.max(1, (int) Math.ceil(recipe.duration / speed));
        for (int parallel = maximumParallel(); parallel >= 1; parallel--) {
            long total = (long) Math.ceil(baseEnergy * 2.0 * parallel * discount * processingMultiplier);
            long perTick = (long) Math.ceil((double) total / duration);
            if (perTick <= limit) {
                return parallel;
            }
        }
        return 0;
    }

    private GTRecipe findRecipe() {
        GTRecipeType type = recipeMode == MODE_ALLOY ? GTRecipeTypes.ALLOY_SMELTER_RECIPES
                : GTRecipeTypes.FURNACE_RECIPES;
        if (!hasCapabilityProxies()) {
            return null;
        }
        var iterator = type.searchRecipe(this, recipe ->
                // 机器只接受基础输入功率不超过 32 EU/t (LV) 的配方
                RecipeHelper.getRecipeEUtTier(recipe) <= 1);
        return iterator.hasNext() ? iterator.next() : null;
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
        for (GTRecipeType type : List.of(GTRecipeTypes.FURNACE_RECIPES, GTRecipeTypes.ALLOY_SMELTER_RECIPES)) {
            for (GTRecipe recipe : type.getRecipesInCategory(type.getCategory())) {
                if (recipe.getId().equals(id)) {
                    return recipe;
                }
            }
        }
        return null;
    }

    /**
     * 预热: consume steam towards the next +1°C. The plan is the smaller of the
     * missing steam for the next degree and this tick's supply cap; only a
     * fully satisfiable plan is executed (atomic), otherwise nothing is drawn
     * and the already-invested accumulation is kept.
     *
     * @return true when steam was actually consumed this tick
     */
    private boolean tryPreheat(Difficulty difficulty) {
        int limit = maxTemperature();
        if (formedWidth == 0 || currentTemperature >= limit) {
            return false;
        }
        long costUnits = preheatCostPerDegreeUnits(difficulty);
        long missingUnits = costUnits - preheatProgressUnits;
        if (missingUnits <= 0) {
            // Degree already funded; still honour the heating-rate interval.
            advanceHeatTimer();
            return false;
        }
        long planUnits = Math.min(missingUnits, steamInputLimitPerTickUnits());
        long planMb = (planUnits + 99) / 100;
        if (!drawSteam(planMb)) {
            return false;
        }
        preheatProgressUnits += planMb * 100L;
        advanceHeatTimer();
        return true;
    }

    private void advanceHeatTimer() {
        Difficulty difficulty = currentDifficulty();
        heatTimer++;
        if (heatTimer >= difficulty.getPreheatIntervalTicks()) {
            heatTimer = 0;
            preheatProgressUnits = 0;
            if (currentTemperature < maxTemperature()) {
                currentTemperature++;
            }
        }
    }

    /** 停机或加工冷却公式; a missing structure cools with its last known size. */
    private void coolDown(Difficulty difficulty) {
        if (currentTemperature <= COLD_TEMPERATURE) {
            return;
        }
        coolTimer++;
        if (coolTimer >= coolingIntervalTicks(false)) {
            coolTimer = 0;
            currentTemperature--;
        }
    }

    //////////////////////////////////////
    // ***** Steam supply ******//
    //////////////////////////////////////

    /** 合计机器侧供汽上限 in 1/100 mB units (N × 1200 mB/t; ME hatch later). */
    private long steamInputLimitPerTickUnits() {
        if (steamUnlimited) {
            // ME 流体输入仓取消机器侧供汽上限
            return Long.MAX_VALUE;
        }
        return (long) steamHatches.size() * STEAM_PER_HATCH_LIMIT_MB * 100L;
    }

    /**
     * 原子扣取: simulate the full plan over the hatches in stable part order and
     * only execute the same plan when every hatch can deliver its share. Normal
     * steam hatches are drawn first (each capped at 1200 mB/t); ME fluid input
     * hatches top up the remainder without a machine-side cap. Steam is drawn
     * as exact GTCEu steam; other fluids never match.
     */
    private boolean drawSteam(long amountMb) {
        if (amountMb <= 0) {
            return false;
        }
        if (steamHatches.isEmpty() && meSteamHatches.isEmpty()) {
            return false;
        }
        long remaining = amountMb;
        for (SteamHatchPartMachine hatch : steamHatches) {
            long capped = Math.min(remaining, STEAM_PER_HATCH_LIMIT_MB);
            FluidStack simulated = hatch.tank.drain(steamFluid(capped), IFluidHandler.FluidAction.SIMULATE);
            remaining -= simulated.getAmount();
            if (remaining <= 0) {
                break;
            }
        }
        if (remaining > 0) {
            for (FluidHatchPartMachine hatch : meSteamHatches) {
                FluidStack simulated = hatch.tank.drain(steamFluid(remaining), IFluidHandler.FluidAction.SIMULATE);
                remaining -= simulated.getAmount();
                if (remaining <= 0) {
                    break;
                }
            }
        }
        if (remaining > 0) {
            return false;
        }
        remaining = amountMb;
        for (SteamHatchPartMachine hatch : steamHatches) {
            long capped = Math.min(remaining, STEAM_PER_HATCH_LIMIT_MB);
            FluidStack drained = hatch.tank.drain(steamFluid(capped), IFluidHandler.FluidAction.EXECUTE);
            remaining -= drained.getAmount();
            if (remaining <= 0) {
                break;
            }
        }
        if (remaining > 0) {
            for (FluidHatchPartMachine hatch : meSteamHatches) {
                FluidStack drained = hatch.tank.drain(steamFluid(remaining), IFluidHandler.FluidAction.EXECUTE);
                remaining -= drained.getAmount();
                if (remaining <= 0) {
                    break;
                }
            }
        }
        if (remaining > 0) {
            // 执行阶段未按模拟结果提供计划量: 安全失败, 不推进任何状态.
            GregSteamExpansion.LOGGER.warn(
                    "Furnace at {} draw execution fell short of the simulated plan by {} mB",
                    getPos(), remaining);
            return false;
        }
        return true;
    }

    private static FluidStack steamFluid(long amountMb) {
        return GTMaterials.Steam.getFluid((int) Math.min(amountMb, Integer.MAX_VALUE));
    }

    //////////////////////////////////////
    // ***** Exhaust ******//
    //////////////////////////////////////

    /** Only ticks where steam was actually consumed advance these cycles. */
    private void runExhaustCycles() {
        if (exhaustHatch == null) {
            return;
        }
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
    // ***** Formulas ******//
    //////////////////////////////////////

    private Difficulty currentDifficulty() {
        return GSEDifficultyState.isResolved() ? GSEDifficultyState.resolved() : Difficulty.NORMAL;
    }

    /** One-shot downgrade migration: clear per-degree accumulation and timers. */
    private void migrateDifficulty() {
        Difficulty difficulty = currentDifficulty();
        if (difficulty.ordinal() < lastAppliedDifficulty) {
            preheatProgressUnits = 0;
            heatTimer = 0;
        }
        if (difficulty.ordinal() != lastAppliedDifficulty) {
            lastAppliedDifficulty = difficulty.ordinal();
        }
    }

    public int maxTemperature() {
        return switch (formedWidth) {
            case 11 -> 1500;
            case 15 -> 2000;
            default -> 1000;
        };
    }

    /** 启动温度统一取温度上限的 60%. */
    public int startupTemperature() {
        return maxTemperature() * 3 / 5;
    }

    /** 最大并行数 = 64 + 16 × (高度 − 6), clamped to the 64–256 range. */
    public int maximumParallel() {
        if (formedHeight < 6) {
            return 64;
        }
        return Math.min(256, 64 + 16 * (formedHeight - 6));
    }

    /** 预热每 1°C 蒸汽成本 in 1/100 mB units: (宽²−4) × 高 × 2 × percent / 100. */
    public long preheatCostPerDegreeUnits(Difficulty difficulty) {
        long base = (long) (formedWidth * (long) formedWidth - 4) * formedHeight * 2;
        return base * difficulty.getPreheatCostPercent();
    }

    /**
     * 冷却间隔: V/A ratio and temperature difference decide how many ticks one
     * −1°C takes; processing cools ~8× slower than idle.
     */
    public int coolingIntervalTicks(boolean processing) {
        if (formedWidth == 0 || currentTemperature <= COLD_TEMPERATURE) {
            return Integer.MAX_VALUE;
        }
        double innerArea = (double) formedWidth * formedWidth - 4;
        double volume = innerArea * formedHeight;
        double area = 2 * innerArea + 4.0 * formedWidth * formedHeight;
        double ratio = volume / area;
        double temperatureFactor = (currentTemperature - COLD_TEMPERATURE) / 980.0;
        if (temperatureFactor <= 0) {
            return Integer.MAX_VALUE;
        }
        return (int) Math.ceil((processing ? 40 : 5) * ratio / temperatureFactor);
    }

    /** 速度倍率 M = 1 + (2 + 22 × v³) × t² (ready for the batch engine). */
    public double speedMultiplier() {
        double v = volumeProgress();
        double t = temperatureProgress();
        return 1 + (2 + 22 * v * v * v) * t * t;
    }

    /** 加工蒸汽体积减免 D = 1 / (1 + 3 × v²) (ready for the batch engine). */
    public double steamDiscount() {
        double v = volumeProgress();
        return 1 / (1 + 3 * v * v);
    }

    private double volumeProgress() {
        if (formedWidth == 0) {
            return 0;
        }
        double volume = (double) (formedWidth * (long) formedWidth - 4) * formedHeight;
        return Math.min(1, Math.max(0, (volume - 270) / (3978.0 - 270)));
    }

    private double temperatureProgress() {
        int max = maxTemperature();
        if (max <= MIN_WORKING_TEMPERATURE) {
            return 0;
        }
        return Math.min(1, Math.max(0, (currentTemperature - MIN_WORKING_TEMPERATURE) / (double) (max - MIN_WORKING_TEMPERATURE)));
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

    private void updateWorkingAppearance() {
        // 预热或加工中的实际取汽驱动点火表现; 暂停/受阻/失效回落到静止.
        boolean active = isFormed() && isWorkingEnabled() && !exhaustBlocked
                && lastTickConsumedSteam;
        var status = active ? RecipeLogic.Status.WORKING : RecipeLogic.Status.IDLE;
        var renderState = getRenderState();
        if (renderState.hasProperty(GTMachineModelProperties.RECIPE_LOGIC_STATUS)
                && renderState.getValue(GTMachineModelProperties.RECIPE_LOGIC_STATUS) != status) {
            setRenderState(renderState.setValue(GTMachineModelProperties.RECIPE_LOGIC_STATUS, status));
        }
        // 底部青铜燃烧室点火由控制器单一状态驱动, 仅在点火状态变化时同步一次.
        if (fireboxActive != active) {
            fireboxActive = active;
            updateFireboxBlocks(active);
        }
    }

    /** Flips the ACTIVE property of the structure's bronze fireboxes. */
    private void updateFireboxBlocks(boolean active) {
        Level level = getLevel();
        if (activeBlocks == null || level == null || level.isClientSide) {
            return;
        }
        for (long packed : activeBlocks) {
            BlockPos pos = BlockPos.of(packed);
            var state = level.getBlockState(pos);
            if (state.hasProperty(com.gregtechceu.gtceu.api.block.property.GTBlockStateProperties.ACTIVE)
                    && state.getValue(com.gregtechceu.gtceu.api.block.property.GTBlockStateProperties.ACTIVE) != active) {
                level.setBlock(pos, state.setValue(
                        com.gregtechceu.gtceu.api.block.property.GTBlockStateProperties.ACTIVE, active),
                        net.minecraft.world.level.block.Block.UPDATE_CLIENTS
                                | net.minecraft.world.level.block.Block.UPDATE_KNOWN_SHAPE);
            }
        }
    }

    //////////////////////////////////////
    // ***** Recipe mode ******//
    //////////////////////////////////////

    public int getRecipeMode() {
        return recipeMode;
    }

    /** 螺丝刀切换熔炉/合金炉模式; 仅 15×15 且无运行/保留批次时允许. */
    @Override
    protected InteractionResult onScrewdriverClick(Player playerIn, InteractionHand hand, Direction gridSide,
                                                   BlockHitResult hitResult) {
        if (isRemote()) {
            return InteractionResult.SUCCESS;
        }
        if (formedWidth != 15 || hasBatch || !pendingOutputs.isEmpty()) {
            if (!isRemote()) {
                playerIn.sendSystemMessage(Component.translatable(
                        "gregsteamexpansion.machine.large_heat_storage_steam_furnace.mode.switch_blocked"));
            }
            return InteractionResult.FAIL;
        }
        recipeMode = recipeMode == MODE_FURNACE ? MODE_ALLOY : MODE_FURNACE;
        playerIn.sendSystemMessage(Component.translatable(recipeMode == MODE_ALLOY
                ? "gregsteamexpansion.machine.large_heat_storage_steam_furnace.mode.alloy"
                : "gregsteamexpansion.machine.large_heat_storage_steam_furnace.mode.furnace"));
        return InteractionResult.sidedSuccess(getLevel() != null && getLevel().isClientSide);
    }

    //////////////////////////////////////
    // ***** Display helpers ******//
    //////////////////////////////////////

    /** Stable status id per the Jade protocol (large-heat-storage-steam-furnace.md). */
    public String getStatusId() {
        if (!isFormed()) {
            return "invalid_structure";
        }
        if (hasBatch && !matchesOriginalSize()) {
            return "awaiting_original_size";
        }
        if (!isWorkingEnabled()) {
            return "working_disabled";
        }
        if (exhaustBlocked) {
            return "exhaust_obstructed";
        }
        if (waitingForSteam) {
            return "low_steam";
        }
        if (waitingForOutputs) {
            return "insufficient_outputs";
        }
        if (hasBatch) {
            return "working";
        }
        if (currentTemperature >= maxTemperature()) {
            return "at_temperature_limit";
        }
        if (lastTickConsumedSteam) {
            return currentTemperature < startupTemperature() ? "preheating" : "working";
        }
        if (currentTemperature >= startupTemperature() && waitingForInputs) {
            return "insufficient_inputs";
        }
        return "cooling";
    }

    /**
     * 尺寸改变重置规则: 已保留批次仍须等待原尺寸恢复; 同尺寸重建不算尺寸改变.
     */
    private boolean matchesOriginalSize() {
        return !hasBatch || (batchOriginWidth == formedWidth && batchOriginHeight == formedHeight);
    }

    //////////////////////////////////////
    // ***** Controller UI ******//
    //////////////////////////////////////

    private static final String TOOLTIP_PREFIX =
            "gregsteamexpansion.machine.large_heat_storage_steam_furnace.tooltip.";

    /**
     * 单页可滚动运行信息页: 固定顺序的信息行; 电源按钮与总线隔离控件固定在
     * 滚动区之外 (large-heat-storage-steam-furnace.md 运行信息页布局与格式).
     */
    @Override
    public ModularUI createUI(Player entityPlayer) {
        // 260px wide so the longest value row (温度 1000°C / 600°C / 1000°C)
        // fits; GuiTextures.BACKGROUND is a 9-slice border texture and scales
        // cleanly, unlike the fixed-size steam background.
        int uiWidth = 260;
        int uiHeight = 216;
        var ui = new ModularUI(uiWidth, uiHeight, this, entityPlayer)
                .background(GuiTextures.BACKGROUND);
        var scroll = new DraggableScrollableWidgetGroup(5, 5, uiWidth - 10, uiHeight - 32);
        int y = 2;
        y = infoRow(scroll, y, uiKey("ui.status"), () -> getStatusText().getString(), getStatusColor());
        y = infoRow(scroll, y, uiKey("ui.temperature"),
                () -> currentTemperature + "°C / " + startupTemperature() + "°C / " + maxTemperature() + "°C",
                ChatFormatting.WHITE);
        y = infoRow(scroll, y, uiKey("ui.progress"),
                () -> hasBatch ? progressPercent() + "% (" + formatDuration(batchProgress) + " / "
                        + formatDuration(batchDuration) + ")" : "—",
                ChatFormatting.WHITE);
        y = infoRow(scroll, y, uiKey("ui.steam"),
                () -> (hasBatch ? currentDemandPerTick() + " mB/t / "
                        + (steamUnlimited ? unlimitedText() : FormattingUtil.formatNumbers(steamInputLimitPerTickUnits() / 100))
                        : "— / " + (steamUnlimited ? unlimitedText()
                                : FormattingUtil.formatNumbers((long) steamHatches.size() * STEAM_PER_HATCH_LIMIT_MB))),
                ChatFormatting.WHITE);
        y = infoRow(scroll, y, uiKey("ui.parallel"),
                () -> (hasBatch ? batchParallel + " / " + maximumParallel() : "— / " + maximumParallel()),
                ChatFormatting.WHITE);
        y = infoRow(scroll, y, uiKey("ui.size"),
                () -> formedWidth == 0 ? "—" : formedWidth + "×" + formedWidth + "×" + formedHeight
                        + " (" + FormattingUtil.formatNumbers(formedVolume()) + ")",
                ChatFormatting.WHITE);
        y = infoRow(scroll, y, uiKey("ui.preheat"),
                () -> formedWidth == 0 ? "— / —"
                        : FormattingUtil.formatNumbers(preheatProgressUnits / 100) + " / "
                        + FormattingUtil.formatNumbers(preheatCostPerDegreeUnits(currentDifficulty()) / 100) + " mB",
                ChatFormatting.WHITE);
        y = infoRow(scroll, y, uiKey("ui.speed"),
                () -> hasBatch ? "×" + FormattingUtil.formatNumber2Places((float) batchSpeed) : "—",
                ChatFormatting.WHITE);
        infoRow(scroll, y, uiKey("ui.duration"),
                () -> hasBatch ? formatDuration(batchDuration) : "—", ChatFormatting.WHITE);
        ui.widget(scroll);
        // 电源按钮与总线隔离固定在滚动区之外; 总线隔离仅有按钮, 说明放入悬浮提示.
        ui.widget(new ToggleButtonWidget(6, uiHeight - 24, 18, 18, GuiTextures.BUTTON_POWER,
                this::isWorkingEnabled, this::setWorkingEnabled));
        ui.widget(new ToggleButtonWidget(28, uiHeight - 24, 18, 18, GuiTextures.BUTTON_DISTINCT_BUSES,
                () -> distinctBuses, value -> {
                    if (canToggleDistinctBuses()) {
                        distinctBuses = value;
                    }
                }).setHoverTooltips(
                Component.translatable("gtceu.multiblock.universal.distinct")
                        .withStyle(ChatFormatting.YELLOW),
                Component.translatable("gtceu.multiblock.universal.distinct.info")
                        .withStyle(ChatFormatting.GRAY)));
        return ui;
    }

    private static String uiKey(String suffix) {
        return TOOLTIP_PREFIX + suffix;
    }

    private int infoRow(DraggableScrollableWidgetGroup group, int y, String labelKey,
                        java.util.function.Supplier<String> value, ChatFormatting valueColor) {
        group.addWidget(new LabelWidget(2, y, () -> Component.translatable(labelKey).getString())
                .setTextColor(-1).setDropShadow(true));
        Integer rgb = valueColor.getColor();
        group.addWidget(new LabelWidget(104, y, value).setTextColor(rgb == null ? -1 : (rgb.intValue() & 0xFFFFFF))
                .setDropShadow(true));
        return y + 10;
    }

    private double progressPercent() {
        return batchDuration == 0 ? 0 : Math.round(batchProgress * 1000.0 / batchDuration) / 10.0;
    }

    private long currentDemandPerTick() {
        return hasBatch ? batchSteamPerTickMb : 0;
    }

    private String unlimitedText() {
        return Component.translatable(TOOLTIP_PREFIX + "ui.unlimited").getString();
    }

    private long formedVolume() {
        return formedWidth == 0 ? 0 : (long) (formedWidth * (long) formedWidth - 4) * formedHeight;
    }

    /** 不足 1 秒用 tick, 1 秒至不足 1 小时用 分:秒, 达到 1 小时用 时:分:秒. */
    private static String formatDuration(int ticks) {
        if (ticks < 20) {
            return String.valueOf(ticks);
        }
        long seconds = ticks / 20L;
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;
        if (hours > 0) {
            return String.format("%d:%02d:%02d", hours, minutes, secs);
        }
        return String.format("%d:%02d", minutes, secs);
    }

    private boolean canToggleDistinctBuses() {
        // 只有未运行配方且没有保留批次时才能切换总线隔离.
        return !hasBatch && pendingOutputs.isEmpty();
    }

    //////////////////////////////////////
    // ***** Status display ******//
    //////////////////////////////////////

    private static final String STATUS_KEY =
            "gregsteamexpansion.machine.large_heat_storage_steam_furnace.status.";

    /** 状态文本: 优先复用 GTCEu 已有本地化键, 缺失语义才新增本模组键. */
    public Component getStatusText() {
        return switch (getStatusId()) {
            case "invalid_structure" -> Component.translatable("gtceu.multiblock.invalid_structure");
            case "awaiting_original_size" -> Component.translatable(STATUS_KEY + "awaiting_original_size");
            case "working_disabled" -> Component.translatable("gtceu.top.working_disabled");
            case "exhaust_obstructed" -> Component.translatable(
                    "gregsteamexpansion.multiblock.steam_exhaust_hatch_obstructed");
            case "low_steam" -> Component.translatable("gtceu.multiblock.steam.low_steam");
            case "insufficient_outputs" -> Component.translatable("gtceu.recipe_logic.insufficient_out");
            case "working" -> Component.translatable("gtceu.multiblock.large_miner.working");
            case "preheating" -> Component.translatable(STATUS_KEY + "preheating");
            case "at_temperature_limit" -> Component.translatable(STATUS_KEY + "at_temperature_limit");
            case "insufficient_inputs" -> Component.translatable("gtceu.recipe_logic.insufficient_in");
            default -> Component.translatable(STATUS_KEY + "cooling");
        };
    }

    /** 状态颜色: 红 = 故障, 黄 = 等待, 绿 = 加工, 浅蓝 = 温度上限. */
    public ChatFormatting getStatusColor() {
        return switch (getStatusId()) {
            case "invalid_structure", "exhaust_obstructed", "low_steam", "insufficient_outputs" -> ChatFormatting.RED;
            case "awaiting_original_size", "working_disabled", "preheating",
                    "insufficient_inputs", "cooling" -> ChatFormatting.YELLOW;
            case "working" -> ChatFormatting.GREEN;
            case "at_temperature_limit" -> ChatFormatting.AQUA;
            default -> ChatFormatting.GRAY;
        };
    }

    public int getCurrentTemperature() {
        return currentTemperature;
    }

    public boolean hasBatch() {
        return hasBatch;
    }

    public int getCurrentBatchParallel() {
        return batchParallel;
    }

    public long getCurrentBatchSteamPerTick() {
        return batchSteamPerTickMb;
    }

    /** 普通供汽接口合计机器侧上限 (mB/t); 不限流时调用方应显示"无限制". */
    public long getSteamInputLimitPerTick() {
        return (long) steamHatches.size() * STEAM_PER_HATCH_LIMIT_MB;
    }

    public boolean isSteamInputUnlimited() {
        return steamUnlimited;
    }

    public int getBatchProgress() {
        return batchProgress;
    }

    public int getBatchDuration() {
        return batchDuration;
    }

    public int getStartupTemperature() {
        return startupTemperature();
    }

    public int getFormedWidth() {
        return formedWidth;
    }

    public int getFormedHeight() {
        return formedHeight;
    }

    @Nullable
    public Direction getExhaustFacing() {
        return exhaustHatch != null ? exhaustHatch.getFrontFacing() : null;
    }
}
