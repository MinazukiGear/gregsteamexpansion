package com.hoshino.gregsteamexpansion.machine.multiblock.processor;

import com.gregtechceu.gtceu.api.capability.IControllable;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.capability.recipe.IRecipeCapabilityHolder;
import com.gregtechceu.gtceu.api.capability.recipe.IRecipeHandler;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.api.machine.feature.IUIMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.api.machine.property.GTMachineModelProperties;
import com.gregtechceu.gtceu.api.machine.trait.IRecipeHandlerTrait;
import com.gregtechceu.gtceu.api.machine.trait.RecipeHandlerList;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.pattern.BlockPattern;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.recipe.chance.logic.ChanceLogic;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.api.recipe.content.ContentModifier;
import com.gregtechceu.gtceu.api.recipe.modifier.ParallelLogic;
import com.gregtechceu.gtceu.api.recipe.RecipeHelper;
import com.gregtechceu.gtceu.common.data.GTMaterials;
import com.gregtechceu.gtceu.common.data.GTSoundEntries;
import com.gregtechceu.gtceu.common.machine.multiblock.part.ItemBusPartMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.part.FluidHatchPartMachine;
import com.gregtechceu.gtceu.utils.FormattingUtil;
import com.gregtechceu.gtceu.utils.GTUtil;
import com.hoshino.gregsteamexpansion.GregSteamExpansion;
import com.hoshino.gregsteamexpansion.recipe.RecipeCacheLifecycle;
import com.hoshino.gregsteamexpansion.recipe.SteamRecipeCache;
import com.hoshino.gregsteamexpansion.registry.GSEPatternBufferCompat;
import com.hoshino.gregsteamexpansion.machine.multiblock.part.SteamAirIntakeHatchPartMachine;
import com.hoshino.gregsteamexpansion.machine.multiblock.part.SteamExhaustHatchMachine;
import com.hoshino.gregsteamexpansion.machine.multiblock.part.SteamFluidHatchPartMachine;
import com.hoshino.gregsteamexpansion.machine.multiblock.part.SteamSupplyHatchPartMachine;
import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import com.lowdragmc.lowdraglib.gui.widget.DraggableScrollableWidgetGroup;
import com.lowdragmc.lowdraglib.gui.widget.LabelWidget;
import com.gregtechceu.gtceu.api.gui.widget.ToggleButtonWidget;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.syncdata.ISubscription;
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
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.ParametersAreNonnullByDefault;

/**
 * 轻量蒸汽多方块家族共用基类 (steam-compressor.md / steam-extractor.md /
 * steam-forge.md 议题 5–9 同口径): recipe-type processors running the full
 * GTCEu recipe type with a LV voltage gate, recipe-scaled duration (×1.5) and
 * linear steam cost (2 mB per EU), fixed parallel cap, worst-case output
 * precheck, atomic input consumption, per-tick atomic steam withdrawal across
 * all supply hatches (position order, 1,200 mB/t per hatch machine-side cap),
 * exactly-once chance roll with a persisted pending-output list, last
 * successful recipe preference, working control and the steam-shortage /
 * structure-loss rollback to 1 tick.
 *
 * <p>Family-wide rules baked in: no Steam Exhaust Hatch (2026-09-07 全模组
 * 裁定: 非大型蒸汽多方块不使用排气仓), standard-steam-only energy, three
 * difficulty tiers identical.</p>
 */
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public abstract class AbstractSteamProcessorMachine extends MultiblockControllerMachine
        implements IControllable, IRecipeCapabilityHolder, IUIMachine, com.gregtechceu.gtceu.api.machine.feature.IMachineLife {

    protected static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(
            AbstractSteamProcessorMachine.class, MultiblockControllerMachine.MANAGED_FIELD_HOLDER);

    /** Fixed duration multiplier for steam consumers (家族口径 1.5×). */
    public static final double DURATION_MULTIPLIER = 1.5;
    /** 2 mB of standard steam per EU (家族换算率). */
    public static final long STEAM_PER_EU_MB = 2;
    /** Machine-side per-hatch withdrawal cap (家族口径 1,200 mB/t). */
    public static final long PER_HATCH_STEAM_CAP_MB = 1200;

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
    /** Locked batch duration: max(1, ceil(recipe.duration × 1.5)). */
    @Persisted
    private int batchDurationTicks = 0;
    /** Locked per-tick demand: recipe EU/t × 2 × P mB/t. */
    @Persisted
    private long batchSteamPerTickMb = 0;
    /** Locked batch total: recipe EU/t × 2 × duration × P mB. */
    @Persisted
    private long batchTotalSteamMb = 0;
    /** Output multiplier locked at batch start (B1/B2 Easy 2×; family default 1). */
    @Persisted
    private float batchOutputMultiplier = 1.0f;
    /** One copy of the locked input item, for the GUI recipe display. */
    @Persisted
    private ItemStack batchInputDisplay = ItemStack.EMPTY;
    /** Finished products waiting for output space (chances rolled exactly once). */
    @Persisted
    private final List<ItemStack> pendingOutputs = new ArrayList<>();
    /** Finished fluids waiting for output space (chances rolled exactly once). */
    @Persisted
    private final List<FluidStack> pendingFluids = new ArrayList<>();
    /** 最近成功配方优先 (议题 6): survives batch completion and reloads. */
    @Persisted
    private String preferredRecipeId = "";

    //////////////////////////////////////
    // ***** Runtime state ******//
    //////////////////////////////////////

    @Nullable
    private TickableSubscription tickSubscription;
    /** 供汽仓 stable position order (议题 5). */
    private final List<SteamSupplyHatchPartMachine> supplyHatches = new ArrayList<>();
    private final List<IMultiPart> inputBuses = new ArrayList<>();
    private final List<ItemBusPartMachine> outputBuses = new ArrayList<>();
    /** Fluid output hatches (GTCEu standard or the mod's steam fluid hatch). */
    private final List<FluidHatchPartMachine> fluidOutputHatches = new ArrayList<>();
    /** GTCEu standard fluid input hatches (water inlet for the ore washer). */
    private final List<FluidHatchPartMachine> fluidInputHatches = new ArrayList<>();
    /** The mod's steam fluid hatches wherever they appear (tracked for the forbid check). */
    private final List<FluidHatchPartMachine> steamFluidHatches = new ArrayList<>();
    /** Steam exhaust hatch (large machines; at most one per structure). */
    private final List<SteamExhaustHatchMachine> exhaustHatches = new ArrayList<>();
    /** Steam air intake hatches (议题 12: only the centrifuge pair, `0` or `1`). */
    private final List<SteamAirIntakeHatchPartMachine> airIntakeHatches = new ArrayList<>();
    /** Whether the exhaust channel is obstructed this tick (freeze, no rollback). */
    private boolean exhaustBlocked = false;
    private int exhaustFeedbackTimer = 0;
    private long exhaustDamageTimer = 0;
    /** False when the post-formation interface count rules failed. */
    private boolean interfaceCountsValid = true;
    private boolean waitingForSteam = false;
    /** True when this tick froze on the auxiliary input (blast air) shortfall. */
    private boolean waitingForAuxiliary = false;
    private boolean waitingForOutputs = false;
    /** Whether this tick actually consumed the full steam demand. */
    private boolean lastTickConsumedSteam = false;
    /** Live recipe instance re-resolved from {@link #batchRecipeId} after reloads. */
    @Nullable
    private GTRecipe batchRecipe;
    /** Working sound handle (client only). */
    @Nullable
    @OnlyIn(Dist.CLIENT)
    private Object workingSound;
    /** Part recipe handlers aggregated on formation. */
    private final Map<IO, List<RecipeHandlerList>> capabilitiesProxy = new EnumMap<>(IO.class);
    private final Map<IO, Map<RecipeCapability<?>, List<IRecipeHandler<?>>>> capabilitiesFlat = new EnumMap<>(IO.class);

    private static final int IDLE_RECIPE_RETRY_TICKS = 20;
    /**
     * Upper bound on the distinct input kinds probed from a pattern buffer, so a
     * deep ME network view cannot make candidate collection unbounded. Reaching
     * the bound is not a correctness problem: exceeding it only widens the
     * candidate set, which {@code tryStartRecipe} still filters.
     */
    private static final int MAX_INDEXED_INPUT_KINDS = 64;
    private final List<ISubscription> searchSubscriptions = new ArrayList<>();
    private boolean recipeSearchDirty = true;
    private long nextRecipeSearchTick;
    /**
     * Shared, revision-keyed view of the recipe type (see {@link SteamRecipeCache}):
     * the list and indexes are identical across every machine of a type, so they
     * live in a process-wide cache rather than being copied per instance.
     */
    private SteamRecipeCache.Entry recipeCache = SteamRecipeCache.get(null);

    protected final void requestRecipeSearch() {
        recipeSearchDirty = true;
    }

    private void clearSearchSubscriptions() {
        searchSubscriptions.forEach(ISubscription::unsubscribe);
        searchSubscriptions.clear();
        requestRecipeSearch();
    }

    @Override
    public void onUnload() {
        clearSearchSubscriptions();
        super.onUnload();
    }

    private void refreshRecipeCache() {
        recipeCache = SteamRecipeCache.get(recipeType());
        requestRecipeSearch();
    }

    protected AbstractSteamProcessorMachine(IMachineBlockEntity holder) {
        super(holder);
    }

    @Override
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }

    //////////////////////////////////////
    // ***** Machine-specific hooks ******//
    //////////////////////////////////////

    /** The single GTCEu recipe type this processor runs (议题 2/3). */
    public abstract GTRecipeType recipeType();

    /** Fixed parallel cap: 8 across the light family (议题 6). */
    public abstract int maximumParallel();

    /** Working loop sound (议题 9 声效沿用类型自带样式). */
    protected com.gregtechceu.gtceu.api.sound.SoundEntry workingSoundEntry() {
        return GTSoundEntries.COMPRESSOR;
    }

    /**
     * Whether the recipe type carries a fluid output slot, making at least one
     * fluid output hatch a formation requirement (议题 4 仓室口径: extractor
     * requires ≥1, pure-dry types require none).
     */
    protected boolean requiresFluidOutput() {
        return false;
    }

    /**
     * Whether the recipe type needs a fluid input slot, making at least one
     * GTCEu STANDARD fluid input hatch a formation requirement (ore washer
     * 议题 3: water enters only through standard hatches).
     */
    protected boolean requiresFluidInput() {
        return false;
    }

    /**
     * Whether the mod's steam fluid input/output hatches are admissible in the
     * structure at all. Default true — the light-family patterns simply have
     * no fluid hatch slots; the ore washer returns false (议题 3 约束禁止).
     */
    protected boolean allowsSteamFluidHatches() {
        return true;
    }

    /**
     * Whether the structure requires exactly one Steam Exhaust Hatch (large
     * machines). Default false — the light family bans it outright
     * (2026-09-07 全模组裁定).
     */
    protected boolean requiresExhaustHatch() {
        return false;
    }

    /**
     * True when exhaust feedback pulses and the heat-damage cycle apply while
     * steam is actually consumed (large-crusher / ore-washer precedent).
     */
    protected boolean hasExhaustHazard() {
        return false;
    }

    /**
     * Whether the structure admits the Steam Air Intake Hatch at all. Default
     * false — only controllers whose pattern declares
     * {@code GSEPartAbilities.STEAM_AIR_INTAKE} opt in (steam-centrifuges.md
     * 议题 12: 离心双机 true, 热力离心机 false).
     */
    protected boolean allowsAirIntake() {
        return false;
    }

    /**
     * Maximum number of air intake hatches in one structure
     * (steam-centrifuges.md 议题 12: 离心双机 `1`, 可选 `0` 或 `1`).
     * Ignored while {@link #allowsAirIntake()} is false.
     */
    protected int maximumAirIntakes() {
        return 0;
    }

    /**
     * Whether the structure requires at least one Steam Air Intake Hatch to
     * form (大型蒸汽高炉鼓风口口径: 进气室必需化). Only meaningful while
     * {@link #allowsAirIntake()} is true — the centrifuge family keeps it
     * optional (`false`), the blast furnace opts in.
     */
    protected boolean requiresAirIntake() {
        return false;
    }

    //////////////////////////////////////
    // ***** Pattern ******//
    //////////////////////////////////////

    @Override
    public abstract BlockPattern getPattern();

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
            tickSubscription = subscribeServerTick(this::processorServerTick);
        }
        updateWorkingAppearance();
    }

    @Override
    public void onStructureInvalid() {
        clearSearchSubscriptions();
        super.onStructureInvalid();
        // 结构失效: keep the batch and locked parameters, roll progress back to
        // 1 tick (议题 8).
        if (hasBatch) {
            batchProgress = Math.min(batchProgress, 1);
        }
        lastTickConsumedSteam = false;
        supplyHatches.clear();
        inputBuses.clear();
        outputBuses.clear();
        fluidOutputHatches.clear();
        fluidInputHatches.clear();
        steamFluidHatches.clear();
        exhaustHatches.clear();
        airIntakeHatches.clear();
        exhaustBlocked = false;
        capabilitiesProxy.clear();
        capabilitiesFlat.clear();
        updateWorkingAppearance();
    }

    /**
     * Post-formation check of the interface count rules (议题 4 仓室): at least
     * one item input bus, one item output bus and one steam supply hatch.
     * Ordinary casing positions have no retained minimum and therefore do not
     * impose a derived total-interface cap.
     */
    private boolean validateInterfaceCounts() {
        if (inputBuses.size() < 1 || outputBuses.size() < 1 || supplyHatches.size() < 1) {
            return false;
        }
        if (requiresFluidOutput() && fluidOutputHatches.size() < 1) {
            return false;
        }
        if (requiresFluidInput() && fluidInputHatches.size() < 1) {
            return false;
        }
        if (!allowsSteamFluidHatches() && !steamFluidHatches.isEmpty()) {
            // 议题 3 约束: steam fluid hatches must never appear in the structure.
            return false;
        }
        if (requiresExhaustHatch() && exhaustHatches.size() != 1) {
            // 大型机排气仓规则: 必须且只能 1 个 (成型后复核口径).
            return false;
        }
        if (!allowsAirIntake() && !airIntakeHatches.isEmpty()) {
            // 议题 12: 未声明进气室能力的机器装了就不成型 (与图案谓词双保险).
            return false;
        }
        if (airIntakeHatches.size() > maximumAirIntakes()) {
            // 议题 12: 每台最多 1 个 (图案 setMaxGlobalLimited 之外的成型后复核).
            return false;
        }
        if (requiresAirIntake() && airIntakeHatches.isEmpty()) {
            // 大型蒸汽高炉鼓风口口径: 进气室必需 (成型后复核, 与图案双保险).
            return false;
        }
        return true;
    }

    private void collectParts() {
        clearSearchSubscriptions();
        supplyHatches.clear();
        inputBuses.clear();
        outputBuses.clear();
        airIntakeHatches.clear();
        capabilitiesProxy.clear();
        capabilitiesFlat.clear();
        it.unimi.dsi.fastutil.longs.Long2ObjectMap<IO> ioMap = getMultiblockState().getMatchContext()
                .getOrCreate("ioMap", it.unimi.dsi.fastutil.longs.Long2ObjectMaps::emptyMap);
        for (IMultiPart part : getParts()) {
            IO io = ioMap.getOrDefault(part.self().getPos().asLong(), IO.BOTH);
            if (io == IO.NONE) continue;
            if (com.hoshino.gregsteamexpansion.registry.GSEPatternBufferCompat.isPatternBuffer(part)) {
                io = IO.IN;
            }
            for (RecipeHandlerList handlerList : part.getRecipeHandlers()) {
                if (!handlerList.isValid(io)) continue;
                addHandlerList(handlerList);
                if (!(part instanceof SteamSupplyHatchPartMachine)) {
                    searchSubscriptions.add(handlerList.subscribe(this::requestRecipeSearch, ItemRecipeCapability.CAP));
                    searchSubscriptions.add(handlerList.subscribe(this::requestRecipeSearch, FluidRecipeCapability.CAP));
                }
            }
            if (part instanceof SteamSupplyHatchPartMachine supplyHatch) {
                supplyHatches.add(supplyHatch);
            } else if (com.hoshino.gregsteamexpansion.registry.GSEPatternBufferCompat.isPatternBuffer(part)) {
                inputBuses.add(part);
            } else if (part instanceof ItemBusPartMachine bus) {
                if (bus.getInventory().getHandlerIO() == IO.OUT) {
                    outputBuses.add(bus);
                } else {
                    inputBuses.add(bus);
                }
            } else if (part instanceof SteamFluidHatchPartMachine steamFluidHatch) {
                // The mod's steam fluid hatch — tracked separately so machines
                // that forbid it (ore washer 议题 3) can reject the structure.
                steamFluidHatches.add(steamFluidHatch);
                if (allowsSteamFluidHatches()) {
                    // B4/C0 口径 (可选或混用): steam fluid hatches count as
                    // regular fluid interfaces on the side their tank faces,
                    // so the requiresFluidInput/Output re-check and the
                    // interface total below treat both families equally.
                    if (steamFluidHatch.tank.handlerIO == IO.OUT) {
                        fluidOutputHatches.add(steamFluidHatch);
                    } else {
                        fluidInputHatches.add(steamFluidHatch);
                    }
                }
            } else if (part instanceof FluidHatchPartMachine fluidHatch) {
                if (fluidHatch.tank.handlerIO == IO.OUT) {
                    // Covers both the GTCEu standard fluid output hatch and the
                    // mod's steam fluid output hatch (steam-extractor.md 议题 4:
                    // 二者可选或混用); the steam supply hatch is IO.IN and cannot
                    // land here.
                    fluidOutputHatches.add(fluidHatch);
                } else {
                    // GTCEu standard fluid input hatch (water inlet).
                    fluidInputHatches.add(fluidHatch);
                }
            } else if (part instanceof SteamExhaustHatchMachine exhaustHatch) {
                exhaustHatches.add(exhaustHatch);
            } else if (part instanceof SteamAirIntakeHatchPartMachine airIntake) {
                // 议题 12: 进气室走独立能力, 不是流体输入仓 — 不进 fluidInputHatches,
                // 因此 requiresFluidInput() 仍然强制另配 1 个真流体输入仓.
                airIntakeHatches.add(airIntake);
            }
        }
        // Stable orders (粉碎机家族口径): supply hatches and buses by block
        // position; ME output buses first within the output group.
        supplyHatches.sort(Comparator.comparing(hatch -> hatch.self().getPos()));
        outputBuses.sort(Comparator
                .comparing((ItemBusPartMachine bus) -> !isMeBus(bus))
                .thenComparing(bus -> bus.self().getPos()));
        inputBuses.sort(Comparator.comparing(bus -> bus.self().getPos()));
        fluidOutputHatches.sort(Comparator.comparing(hatch -> hatch.self().getPos()));
        fluidInputHatches.sort(Comparator.comparing(hatch -> hatch.self().getPos()));
        steamFluidHatches.sort(Comparator.comparing(hatch -> hatch.self().getPos()));
        exhaustHatches.sort(Comparator.comparing(hatch -> hatch.self().getPos()));
        airIntakeHatches.sort(Comparator.comparing(hatch -> hatch.self().getPos()));
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

    private void processorServerTick() {
        Level level = getLevel();
        if (level == null || level.isClientSide) {
            return;
        }
        refreshRecipeCache();
        waitingForSteam = false;
        waitingForAuxiliary = false;
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

        // 排气受阻 (large machines): freeze progress WITHOUT the steam-shortage
        // 1-tick rollback, no steam withdrawn (议题 5/8 排气受阻与缺汽明确区分).
        exhaustBlocked = requiresExhaustHatch() && !exhaustHatches.isEmpty()
                && exhaustHatches.get(0).isExhaustBlocked();
        if (exhaustBlocked) {
            lastTickConsumedSteam = false;
            updateWorkingAppearance();
            return;
        }

        if (hasBatch) {
            runBatchTick();
        } else if (recipeSearchDirty || level.getGameTime() >= nextRecipeSearchTick) {
            recipeSearchDirty = false;
            // Fallback for external handlers or recipe conditions that do not emit notifications.
            nextRecipeSearchTick = level.getGameTime() + IDLE_RECIPE_RETRY_TICKS;
            tryStartBatch();
        }
        updateWorkingAppearance();
    }

    /** 逐 tick 原子取汽: full demand or nothing; shortfall rolls back to 1 tick. */
    private void runBatchTick() {
        if (batchRecipe == null) {
            // 区块/世界重载后按 id 重新解析; 配方消失时批次冻结等待.
            batchRecipe = findRecipeById(batchRecipeId);
            if (batchRecipe == null) {
                lastTickConsumedSteam = false;
                return;
            }
        }
        if (!drawAuxiliaryInputs(batchParallel, true)) {
            // 鼓风等辅助输入短缺: 与缺汽同口径 — no consumption, rollback to 1 tick.
            lastTickConsumedSteam = false;
            waitingForAuxiliary = true;
            batchProgress = Math.min(batchProgress, 1);
            updateWorkingAppearance();
            return;
        }
        if (!drawSteam(batchSteamPerTickMb)) {
            lastTickConsumedSteam = false;
            waitingForSteam = true;
            // 缺汽回退: keep the batch and locked parameters, progress → 1 tick.
            batchProgress = Math.min(batchProgress, 1);
            updateWorkingAppearance();
            return;
        }
        if (!drawAuxiliaryInputs(batchParallel, false)) {
            GregSteamExpansion.LOGGER.warn(
                    "Steam processor at {} auxiliary draw fell short after the steam draw succeeded",
                    getPos());
        }
        lastTickConsumedSteam = true;
        batchProgress++;
        if (hasExhaustHazard() && !exhaustHatches.isEmpty()) {
            runExhaustCycles(exhaustHatches.get(0));
        }
        if (batchProgress >= batchDurationTicks) {
            completeBatch();
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

    //////////////////////////////////////
    // ***** Recipe search ******//
    //////////////////////////////////////

    /**
     * LV 电压门 (议题 3): only recipes at ULV/LV or below, input-powered only.
     * B1/B2 override the tier ceiling dynamically from the assembler slot.
     */
    protected boolean passesVoltageGate(GTRecipe recipe) {
        var eut = recipe.getInputEUt();
        if (eut.isEmpty()) {
            // No input EU: nothing to convert to steam — reject (家族只收输入功耗配方).
            return false;
        }
        long voltage = eut.voltage();
        if (voltage <= 0) {
            return true;
        }
        return GTUtil.getTierByVoltage(voltage)
                <= com.gregtechceu.gtceu.api.GTValues.LV;
    }

    /**
     * 配方白名单 hook: 默认收下配方类型内的全部配方; 控制器可覆写为只接受特定
     * 命名空间/前缀, 把共用配方类型在机器侧切开, 而不必修改或删除任何配方数据
     * (当前无覆写者——大型蒸汽高炉仍执行 primitive_blast_furnace 全类型, 切开只
     * 做在上游原始高炉那一侧, 见 mixins/RecipeLogicMixin)。
     */
    protected boolean acceptsRecipe(GTRecipe recipe) {
        return true;
    }

    /**
     * Locked batch economics for an accepted recipe (议题 5): ×1.5 duration,
     * 2 mB/EU linear in the parallel. B1/B2 override both with the sub-linear
     * ladder (large-steam-assembler.md 议题 5).
     */
    protected long batchDurationTicks(GTRecipe recipe, int parallel) {
        return Math.max(1, (long) Math.ceil(recipe.duration * DURATION_MULTIPLIER));
    }

    /** Locked per-tick steam demand; family default is linear in the parallel. */
    protected long batchSteamPerTickMb(GTRecipe recipe, long eu, int parallel) {
        return eu * STEAM_PER_EU_MB * parallel;
    }

    /**
     * The recipe's input EU/t for economics purposes. Family default reads the
     * EU content; the primitive blast furnace type carries NO EU at all, so
     * EU-less recipes resolve to `0` instead of throwing (大型蒸汽高炉 议题 3).
     */
    protected long batchEu(GTRecipe recipe) {
        var eut = recipe.getInputEUt();
        return eut.isEmpty() ? 0 : eut.voltage();
    }

    /**
     * Per-tick auxiliary input drawn alongside steam, e.g. the blast furnace's
     * tuyere air (大型蒸汽高炉 议题 5). Called once with `simulate=true` before
     * the steam draw — return `false` to freeze this tick with the
     * steam-shortage rollback semantics — and once with `simulate=false` AFTER
     * the steam draw succeeded, so an auxiliary shortfall never wastes steam.
     * Family default consumes nothing.
     */
    protected boolean drawAuxiliaryInputs(int parallel, boolean simulate) {
        return true;
    }

    /**
     * Output multiplier for the NEXT batch, locked at batch start (议题 11:
     * 档位切换后已启动批次保持原锁定产出). Family default is exactly 1.
     */
    protected float batchOutputMultiplier() {
        return 1.0f;
    }

    /**
     * 议题 12 空气配方判定: whether the recipe consumes GTCEu standard air and
     * can therefore only be fed by the Steam Air Intake Hatch (no standard
     * fluid hatch holds a full `10,000 mB` dose, so these recipes are the
     * intake's exclusive customers). Only meaningful for controllers that
     * actually admit an intake — every other machine sees `false`.
     */
    private boolean isAirIntakeRecipe(GTRecipe recipe) {
        return isAirIntakeRecipe(recipe, allowsAirIntake() && !airIntakeHatches.isEmpty());
    }

    /**
     * {@link #isAirIntakeRecipe(GTRecipe)} with the machine-level gate already
     * resolved, so hot loops can hoist it and pay for the probe stack once per
     * candidate instead of twice (the caller iterates each recipe exactly once).
     */
    private boolean isAirIntakeRecipe(GTRecipe recipe, boolean airIntakeUsable) {
        if (!airIntakeUsable) {
            return false;
        }
        var fluids = recipe.inputs.get(FluidRecipeCapability.CAP);
        if (fluids == null || fluids.isEmpty()) {
            return false;
        }
        FluidStack probe = new FluidStack(GTMaterials.Air.getFluid(), 1000);
        for (Content content : fluids) {
            var ingredient = FluidRecipeCapability.CAP.of(content.content);
            if (ingredient != null && ingredient.test(probe)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 配方选择 (议题 6): 最近成功配方优先, then the recipe type's registration
     * order. A candidate must pass the voltage gate, match the aggregated bus
     * contents, admit at least one parallel and pass the worst-case output
     * precheck before its input is consumed atomically.
     *
     * <p>议题 12: air-fed recipes are sorted to the very end so the intake's
     * permanent air cache never starves the item recipes.</p>
     *
     * <p>候选集来自总线实际内容 (而不是全表): an idle machine on the 20-tick
     * retry used to walk every recipe of its type, allocating an air probe per
     * candidate. Now the candidate list is the union of the content buckets for
     * what the input buses and fluid hatches actually hold, so an empty machine
     * short-circuits and a loaded one only walks plausible recipes. Bucket
     * membership is a filter, not a verdict — {@code tryStartRecipe} still runs
     * the full match.</p>
     */
    private void tryStartBatch() {
        GTRecipeType type = recipeType();
        if (type == null || !hasCapabilityProxies()) {
            return;
        }
        // Air is not counted as an input here on purpose: the intake's air cache
        // is effectively infinite, so an air-only recipe must stay a last resort
        // rather than waking the machine ahead of real item/fluid work.
        List<GTRecipe> candidates = candidateRecipes();
        if (candidates.isEmpty()) {
            return;
        }
        boolean airIntakeUsable = allowsAirIntake() && !airIntakeHatches.isEmpty();
        GTRecipe preferred = findRecipeById(preferredRecipeId);
        boolean preferredIsAir = preferred != null && isAirIntakeRecipe(preferred, airIntakeUsable);

        // 双优先级组: prefer the last successful recipe, else walk the candidates
        // in registration order; both skip air recipes, which are deferred to a
        // second pass. One evaluation per candidate per pass — the air predicate
        // allocates a probe stack, so it is computed once and reused.
        List<GTRecipe> airCandidates = null;
        for (GTRecipe recipe : candidates) {
            if (recipe == preferred) continue;
            if (isAirIntakeRecipe(recipe, airIntakeUsable)) {
                if (airCandidates == null) airCandidates = new ArrayList<>();
                airCandidates.add(recipe);
                continue;
            }
            if (tryStartRecipe(recipe)) return;
        }
        if (preferred != null && !preferredIsAir && tryStartRecipe(preferred)) return;
        if (airCandidates != null) {
            for (GTRecipe recipe : airCandidates) {
                if (tryStartRecipe(recipe)) return;
            }
        }
        if (preferredIsAir) {
            tryStartRecipe(preferred);
        }
    }

    /**
     * Recipes that could plausibly run with the current bus and hatch contents:
     * the union of the item/fluid index buckets for everything present, falling
     * back to the full list when no input content is available at all (a machine
     * fed by something this index does not see must not lose its recipes).
     */
    private List<GTRecipe> candidateRecipes() {
        Set<Object> itemKeys = inputBusContents();
        Set<Object> fluidKeys = inputFluidContents();
        if (!allowsAirIntake() && itemKeys.isEmpty() && fluidKeys.isEmpty()) {
            // Dry-only machine with nothing in the input buses: no candidate can
            // ever match, so skip the walk entirely (the 20-tick idle retry).
            return List.of();
        }
        List<GTRecipe> candidates = new ArrayList<>();
        for (Object key : itemKeys) {
            addBucket(candidates, ItemRecipeCapability.CAP, key);
        }
        for (Object key : fluidKeys) {
            addBucket(candidates, FluidRecipeCapability.CAP, key);
        }
        if (candidates.isEmpty()) {
            // No indexable input: keep the previous full-list behaviour rather
            // than silently disabling the machine.
            return recipeCache.recipes();
        }
        return candidates;
    }

    /** Item kinds currently held by the input buses (deduplicated, in slot order). */
    private Set<Object> inputBusContents() {
        Set<Object> keys = new LinkedHashSet<>();
        for (IMultiPart part : inputBuses) {
            if (part instanceof ItemBusPartMachine bus) {
                collectContainerItems(bus.getInventory().storage, keys);
            } else if (GSEPatternBufferCompat.isPatternBuffer(part)) {
                // ME pattern buffers are item-input interfaces but not
                // ItemBusPartMachine; probe their capability view instead.
                collectCapabilityItems(part, keys);
            }
        }
        return keys;
    }

    /**
     * Fluid kinds currently held by the fluid input hatches (incl. the steam ones).
     *
     * <p>Air intake hatches are read here too, even though they are not
     * {@link FluidHatchPartMachine}s and do not appear in
     * {@link #fluidInputHatches}: the recipes the intake feeds — {@code air_separation}
     * and friends — declare their air as an ordinary fluid input, so the machine
     * must see that fluid when building its candidate set. Omitting it would make
     * the index blind to exactly the recipes the intake exists to run, and the
     * machine would sit idle with a full intake tank.</p>
     */
    private Set<Object> inputFluidContents() {
        Set<Object> keys = new LinkedHashSet<>();
        for (FluidHatchPartMachine hatch : fluidInputHatches) {
            FluidStack stack = hatch.tank.getFluidInTank(0);
            if (!stack.isEmpty() && stack.getFluid() != null) {
                keys.add(stack.getFluid());
            }
        }
        for (SteamAirIntakeHatchPartMachine intake : airIntakeHatches) {
            FluidStack stack = intake.tank.getFluidInTank(0);
            if (!stack.isEmpty() && stack.getFluid() != null) {
                keys.add(stack.getFluid());
            }
        }
        return keys;
    }

    private static void collectContainerItems(IItemHandler handler, Set<Object> keys) {
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (!stack.isEmpty()) {
                keys.add(stack.getItem());
            }
        }
    }

    /**
     * Item kinds reachable through a part's recipe handlers, used for pattern
     * buffers. Bounded so a huge ME network view cannot materialise unboundedly:
     * a handful of keys is already enough to reach every plausible bucket, and
     * the fallback below covers the keys this truncates away.
     */
    private static void collectCapabilityItems(IMultiPart part, Set<Object> keys) {
        for (RecipeHandlerList handlerList : part.getRecipeHandlers()) {
            for (IRecipeHandler<?> handler : handlerList.getHandlersFlat()) {
                if (handler instanceof IRecipeHandlerTrait<?> trait && !trait.getHandlerIO().support(IO.IN)) {
                    continue;
                }
                for (Object content : handler.getContents()) {
                    if (content instanceof ItemStack stack && !stack.isEmpty()) {
                        keys.add(stack.getItem());
                        if (keys.size() >= MAX_INDEXED_INPUT_KINDS) {
                            return;
                        }
                    }
                }
            }
        }
    }

    /** Appends one content bucket, skipping recipes already collected. */
    private void addBucket(List<GTRecipe> candidates, RecipeCapability<?> capability, Object key) {
        Map<Object, List<GTRecipe>> buckets = recipeCache.byContent().get(capability);
        if (buckets == null) {
            return;
        }
        List<GTRecipe> bucket = buckets.get(key);
        if (bucket == null) {
            return;
        }
        for (GTRecipe recipe : bucket) {
            // Buckets are small and can share recipes across capacities, so a
            // linear contains() beats the allocation cost of a seen-set here.
            if (!candidates.contains(recipe)) {
                candidates.add(recipe);
            }
        }
    }

    private boolean tryStartRecipe(GTRecipe recipe) {
        if (!acceptsRecipe(recipe)) return false;
        if (!passesVoltageGate(recipe)) {
            return false;
        }
        if (!RecipeHelper.matchRecipe(this, recipe).isSuccess()) {
            return false;
        }
        int byInputs = ParallelLogic.getMaxByInput(this, recipe, maximumParallel(), List.of());
        if (byInputs <= 0) {
            return false;
        }
        // 按可输出槽位决定并行: worst-case output (every chanced output
        // assumed successful, per-parallel) simulated through the EXACT
        // insertion path deliverPendingOutputs uses, so a batch that
        // passes this check can never end in output blocking later.
        int parallel = largestParallelThatFits(recipe, Math.min(maximumParallel(), byInputs));
        if (parallel <= 0) {
            return false;
        }

        GTRecipe multiplied = recipe.copy(ContentModifier.multiplier(parallel));
        multiplied.parallels = parallel;
        // 原子扣取: extract the full parallel input in one operation.
        var result = RecipeHelper.handleRecipe(this, multiplied, IO.IN,
                multiplied.inputs, new HashMap<>(), false, false);
        if (!result.isSuccess()) {
            return false;
        }

        long eu = batchEu(recipe);
        hasBatch = true;
        batchRecipe = recipe;
        batchRecipeId = recipe.getId().toString();
        // 议题 12: 空气配方不写偏好, 避免跑过一次就锁死、永久挤占后续物品配方.
        if (!isAirIntakeRecipe(recipe)) {
            preferredRecipeId = recipe.getId().toString();
        }
        batchParallel = parallel;
        batchProgress = 0;
        batchDurationTicks = (int) batchDurationTicks(recipe, parallel);
        batchSteamPerTickMb = batchSteamPerTickMb(recipe, eu, parallel);
        batchTotalSteamMb = batchSteamPerTickMb * batchDurationTicks;
        batchOutputMultiplier = batchOutputMultiplier();
        batchInputDisplay = firstInputDisplay(recipe);
        GregSteamExpansion.LOGGER.debug(
                "Steam processor at {} started batch {} with parallel {} ({} ticks, {} mB total, {} mB/t)",
                getPos(), batchRecipeId, parallel, batchDurationTicks, batchTotalSteamMb, batchSteamPerTickMb);
        return true;
    }

    /** First sized item input of the recipe, for the GUI display. */
    private static ItemStack firstInputDisplay(GTRecipe recipe) {
        List<Content> inputs = recipe.inputs.get(ItemRecipeCapability.CAP);
        if (inputs != null) {
            for (Content content : inputs) {
                ItemStack[] items = ItemRecipeCapability.CAP.of(content.getContent()).getItems();
                if (items.length > 0 && !items[0].isEmpty()) {
                    ItemStack stack = items[0].copy();
                    if (content.getContent() instanceof com.gregtechceu.gtceu.api.recipe.ingredient.SizedIngredient sized) {
                        stack.setCount(Math.max(1, sized.getAmount()));
                    }
                    return stack;
                }
            }
        }
        return ItemStack.EMPTY;
    }

    @Nullable
    private GTRecipe findRecipeById(String recipeId) {
        if (recipeId == null || recipeId.isEmpty()) {
            return null;
        }
        GTRecipeType type = recipeType();
        if (type == null) {
            return null;
        }
        ResourceLocation id = ResourceLocation.tryParse(recipeId);
        if (id == null) {
            return null;
        }
        refreshRecipeCache();
        return recipeCache.byId(id);
    }

    //////////////////////////////////////
    // ***** Output fit + commit ******//
    //////////////////////////////////////

    /**
     * 按可输出槽位决定并行: from the candidate cap downward, find the largest
     * parallel whose WORST-CASE output list fits the outputs right now — item
     * outputs simulated on the output buses, fluid outputs on the fluid output
     * hatches (家族口径 输出最坏情况预检; steam-extractor.md 议题 6 输出预检含
     * 1 物品位 + 1 流体位).
     */
    private int largestParallelThatFits(GTRecipe recipe, int candidate) {
        List<Content> itemOutputs = recipe.outputs.get(ItemRecipeCapability.CAP);
        List<Content> fluidOutputs = recipe.outputs.get(FluidRecipeCapability.CAP);
        List<ItemStack> perOperationItems = new ArrayList<>();
        if (itemOutputs != null) {
            for (Content content : itemOutputs) {
                ItemStack stack = representativeStackOf(content);
                if (stack == null || stack.isEmpty()) {
                    continue;
                }
                if (content.chance >= content.maxChance) {
                    perOperationItems.add(stack);
                } else {
                    perOperationItems.add(stack.copyWithCount(1));
                }
            }
        }
        List<FluidStack> perOperationFluids = new ArrayList<>();
        if (fluidOutputs != null) {
            for (Content content : fluidOutputs) {
                FluidStack stack = representativeFluidOf(content);
                if (stack == null || stack.isEmpty()) {
                    continue;
                }
                if (content.chance >= content.maxChance) {
                    perOperationFluids.add(stack);
                } else {
                    FluidStack conservative = stack.copy();
                    conservative.setAmount(1);
                    perOperationFluids.add(conservative);
                }
            }
        }
        if (perOperationItems.isEmpty() && perOperationFluids.isEmpty()) {
            return candidate;
        }
        for (int parallel = candidate; parallel >= 1; parallel--) {
            boolean itemsFit = perOperationItems.isEmpty()
                    || worstCaseFits(perOperationItems, parallel);
            boolean fluidsFit = perOperationFluids.isEmpty()
                    || worstCaseFluidsFit(perOperationFluids, parallel);
            if (itemsFit && fluidsFit) {
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
        mergeStacks(simulation);
        for (ItemBusPartMachine bus : outputBuses) {
            for (int i = 0; i < simulation.size(); i++) {
                simulation.set(i, insertIntoBus(bus, simulation.get(i), true));
            }
        }
        return simulation.stream().allMatch(ItemStack::isEmpty);
    }

    /** Representative stack of an output Content (sized amount preserved). */
    @Nullable
    private static ItemStack representativeStackOf(Content content) {
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
     * Representative fluid of an output Content — FluidIngredient#getStacks
     * already bakes the sized amount into the returned stacks.
     */
    @Nullable
    private static FluidStack representativeFluidOf(Content content) {
        var ingredient = FluidRecipeCapability.CAP.of(content.content);
        if (ingredient == null) {
            return null;
        }
        FluidStack[] stacks = ingredient.getStacks();
        if (stacks.length == 0 || stacks[0].isEmpty()) {
            return null;
        }
        return stacks[0].copy();
    }

    /**
     * Worst-case fluid output for `parallel` operations, simulated with fill on
     * the fluid output hatches in stable position order. Merges equal fluids
     * first so a single hatch can take the whole amount when space allows.
     *
     * <p>{@code fillInternal} is used rather than the capability-facing
     * {@code fill}: an output hatch's {@code capabilityIO} deliberately excludes
     * {@code IO.IN}, so {@code fill} short-circuits to {@code 0} and this
     * precheck would reject every recipe that produces fluid — including
     * {@code air_separation}. The mod's own output path
     * ({@code LargeCokeOvenRecipeLogic}) writes through the same internal hook,
     * so the simulation stays faithful to what delivery actually does.</p>
     */
    private boolean worstCaseFluidsFit(List<FluidStack> perOperation, int parallel) {
        List<FluidStack> simulation = new ArrayList<>();
        for (FluidStack stack : perOperation) {
            FluidStack scaled = stack.copy();
            scaled.setAmount((int) Math.min(Integer.MAX_VALUE, (long) scaled.getAmount() * parallel));
            simulation.add(scaled);
        }
        mergeFluids(simulation);
        if (fluidOutputHatches.isEmpty()) {
            return false;
        }
        for (FluidHatchPartMachine hatch : fluidOutputHatches) {
            for (int i = 0; i < simulation.size(); i++) {
                FluidStack remaining = simulation.get(i);
                if (!remaining.isEmpty()) {
                    int accepted = hatch.tank.fillInternal(remaining, IFluidHandler.FluidAction.SIMULATE);
                    remaining.shrink(accepted);
                }
            }
        }
        return simulation.stream().allMatch(FluidStack::isEmpty);
    }

    private static void mergeFluids(List<FluidStack> stacks) {
        for (int i = 0; i < stacks.size(); i++) {
            FluidStack keep = stacks.get(i);
            for (int j = stacks.size() - 1; j > i; j--) {
                FluidStack other = stacks.get(j);
                if (!keep.isEmpty() && keep.isFluidEqual(other)) {
                    keep.grow(other.getAmount());
                    stacks.remove(j);
                }
            }
        }
        stacks.removeIf(FluidStack::isEmpty);
    }

    /**
     * 配方完成: one chance roll, products persisted to the pending list first,
     * then delivered atomically (议题 7).
     */
    private void completeBatch() {
        requestRecipeSearch();
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
            // multiply the guaranteed part AGAIN.
            produced.addAll(materializeItemContents(rolled));
        });
        List<ItemStack> scaledProduced = scaleByMultiplier(produced, batchOutputMultiplier);
        mergeStacks(scaledProduced);
        pendingOutputs.addAll(scaledProduced);

        hasBatch = false;
        batchRecipe = null;
        batchProgress = 0;
        batchRecipeId = "";
        batchInputDisplay = ItemStack.EMPTY;
        batchOutputMultiplier = 1.0f;
        deliverPendingOutputs();
    }

    /**
     * 议题 11 (B1/B2 Easy 档): multiply the item output COUNT by the locked
     * batch multiplier, splitting stacks that exceed their max stack size —
     * the multiplier never touches chance rolls or input consumption.
     */
    private static List<ItemStack> scaleByMultiplier(List<ItemStack> produced, float multiplier) {
        if (multiplier <= 1.0f || produced.isEmpty()) {
            return produced;
        }
        List<ItemStack> scaled = new ArrayList<>();
        for (ItemStack stack : produced) {
            long count = Math.round(stack.getCount() * (double) multiplier);
            while (count > 0) {
                int chunk = (int) Math.min(stack.getMaxStackSize(), count);
                scaled.add(stack.copyWithCount(chunk));
                count -= chunk;
            }
        }
        return scaled;
    }

    /**
     * Official fluid content materialization: fluid contents hold
     * FluidIngredients whose getStacks already carry the sized amount.
     */
    public static List<FluidStack> materializeFluidContents(List<Content> rolled) {
        List<FluidStack> stacks = new ArrayList<>();
        for (Content content : rolled) {
            FluidStack stack = representativeFluidOf(content);
            if (stack != null && !stack.isEmpty()) {
                stacks.add(stack);
            }
        }
        return stacks;
    }

    /**
     * 待输出整批原子输出 (议题 7): items and fluids are committed SEPARATELY —
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
        // fillInternal, not the capability-facing fill: an output hatch's
        // capabilityIO excludes IO.IN by design, so fill() returns 0 for every
        // call and pending fluid outputs could never be delivered. The coke
        // oven's output path (LargeCokeOvenRecipeLogic) uses the same hook.
        List<FluidStack> simulation = new ArrayList<>();
        for (FluidStack stack : pendingFluids) {
            simulation.add(stack.copy());
        }
        for (FluidHatchPartMachine hatch : fluidOutputHatches) {
            for (int i = 0; i < simulation.size(); i++) {
                FluidStack remaining = simulation.get(i);
                if (!remaining.isEmpty()) {
                    int accepted = hatch.tank.fillInternal(remaining, IFluidHandler.FluidAction.SIMULATE);
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
                    int accepted = hatch.tank.fillInternal(remaining, IFluidHandler.FluidAction.EXECUTE);
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
     * 原子取汽 (议题 5): simulate the full per-tick demand across all supply
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
                    "Steam processor at {} draw execution fell short of the simulated plan by {} mB",
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
        requestRecipeSearch();
        updateWorkingAppearance();
    }

    /**
     * 工作视觉状态: only ticks that actually withdrew the full steam demand and
     * advanced the recipe count (家族口径 共用表现规则).
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

    private void updateWorkingSound(boolean active) {
        if (isRemote()) {
            updateWorkingSoundClient(active);
        }
    }

    @OnlyIn(Dist.CLIENT)
    private void updateWorkingSoundClient(boolean active) {
        com.gregtechceu.gtceu.api.sound.SoundEntry entry = workingSoundEntry();
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
                            && !exhaustBlocked && !waitingForOutputs && lastTickConsumedSteam
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
     * 服务端权威状态 (家族口径 优先级表):
     * 结构未成型 > 输出堵塞 > 主动暂停 > 蒸汽不足 > 运行中 > 待机.
     */
    public String getStatusId() {
        if (!isFormed() || !interfaceCountsValid) {
            return "invalid_structure";
        }
        if (requiresExhaustHatch() && exhaustBlocked) {
            return "exhaust_obstructed";
        }
        if (hasPendingOutputs()) {
            return "insufficient_outputs";
        }
        if (!isWorkingEnabled()) {
            return "working_disabled";
        }
        if (waitingForAuxiliary) {
            return "auxiliary_shortfall";
        }
        if (waitingForSteam) {
            return "low_steam";
        }
        if (hasBatch) {
            return "working";
        }
        return "idle";
    }

    /**
     * Display text for the "auxiliary_shortfall" status (blast air 鼓风不足 for
     * the blast furnace). Only reachable on controllers that consume auxiliary
     * inputs; family default never triggers it.
     */
    protected Component auxiliaryShortfallText() {
        return Component.translatable("gtceu.multiblock.steam.low_steam");
    }

    public Component getStatusText() {
        return switch (getStatusId()) {
            case "invalid_structure" -> Component.translatable("gtceu.multiblock.invalid_structure");
            case "insufficient_outputs" -> Component.translatable("gtceu.recipe_logic.insufficient_out");
            case "working_disabled" -> Component.translatable("gtceu.top.working_disabled");
            case "auxiliary_shortfall" -> auxiliaryShortfallText();
            case "low_steam" -> Component.translatable("gtceu.multiblock.steam.low_steam");
            case "working" -> Component.translatable("gtceu.multiblock.large_miner.working");
            default -> Component.translatable("gtceu.multiblock.idling");
        };
    }

    public ChatFormatting getStatusColor() {
        return switch (getStatusId()) {
            case "invalid_structure", "exhaust_obstructed", "insufficient_outputs" -> ChatFormatting.RED;
            case "working_disabled", "low_steam", "auxiliary_shortfall" -> ChatFormatting.YELLOW;
            case "working" -> ChatFormatting.GREEN;
            default -> ChatFormatting.GRAY;
        };
    }

    //////////////////////////////////////
    // ***** Controller UI ******//
    //////////////////////////////////////

    private static final String UI_PREFIX = "gregsteamexpansion.machine.steam_processor.ui.";

    /**
     * 单页可滚动运行信息页 (议题 9 沿用粉碎机骨架), fixed row order, power
     * button outside the scroll area. Shared layout across the light family.
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
        if (allowsAirIntake()) {
            // 议题 12: 进气室状态与缓存存量 (未安装显示 —), 仅接受进气室的机型显示.
            y = infoRow(scroll, y, UI_PREFIX + "intake", this::intakeText, ChatFormatting.WHITE);
        }
        scroll.addWidget(new LabelWidget(2, y, () -> Component.translatable(UI_PREFIX + "pending").getString())
                .setTextColor(-1).setDropShadow(true));
        Integer pendingRgb = ChatFormatting.WHITE.getColor();
        // Hover lists the persisted pending items in their stable order, live.
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

    private int infoRow(DraggableScrollableWidgetGroup group, int y, String labelKey,
                        java.util.function.Supplier<String> value, ChatFormatting valueColor) {
        group.addWidget(new LabelWidget(2, y, () -> Component.translatable(labelKey).getString())
                .setTextColor(-1).setDropShadow(true));
        Integer rgb = valueColor.getColor();
        // LabelWidget runs LocalizationUtils.format even on already formatted
        // values. Escape literal percent signs at this UI boundary so progress
        // and item names survive that final formatting pass.
        group.addWidget(new LabelWidget(104, y, () -> value.get().replace("%", "%%"))
                .setTextColor(rgb == null ? -1 : (rgb.intValue() & 0xFFFFFF)).setDropShadow(true));
        return y + 10;
    }

    /** `45.0%（135 / 300 tick）`; completed-but-undelivered stays at 100%. */
    private String progressText() {
        if (!hasBatch) {
            return "—";
        }
        double percent = Math.round(Math.min(batchProgress, batchDurationTicks) * 1000.0
                / Math.max(1, batchDurationTicks)) / 10.0;
        String percentText = String.format(java.util.Locale.ROOT, "%.1f%%", percent);
        return percentText + " (" + FormattingUtil.formatNumbers(Math.min(batchProgress, batchDurationTicks))
                + " / " + FormattingUtil.formatNumbers(batchDurationTicks) + "t)";
    }

    /** 当前每刻需求为 EU/t × 2 × P；仅运行中且成功扣取蒸汽时才算实际消耗。 */
    private String demandText() {
        if (currentSteamDemandPerTick() == 0) {
            return "0 mB/t";
        }
        String demand = FormattingUtil.formatNumbers(batchSteamPerTickMb) + " mB/t";
        if (!lastTickConsumedSteam) {
            return demand + " (" + Component.translatable(UI_PREFIX + "not_consuming").getString() + ")";
        }
        return demand;
    }

    /**
     * 议题 12 进气室行: `采集中（12,000 / 64,000 mB）`, 未安装时 `—`. Status
     * text comes straight from the hatch's own synced state, so the wrong
     * dimension / blocked / cache-full cases are visible from the controller.
     */
    private String intakeText() {
        if (airIntakeHatches.isEmpty()) {
            return "—";
        }
        SteamAirIntakeHatchPartMachine intake = airIntakeHatches.get(0);
        return intake.getIntakeStatus().getDisplayName().getString() + " ("
                + FormattingUtil.formatNumbers(intake.tank.getFluidInTank(0).getAmount()) + " / "
                + FormattingUtil.formatNumbers(SteamAirIntakeHatchPartMachine.INITIAL_TANK_CAPACITY) + " mB)";
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
        int kinds = countPendingKinds() + countPendingFluidKinds();
        return Component.translatable(UI_PREFIX + "pending_summary",
                FormattingUtil.formatNumbers(itemTotal + fluidTotal), kinds).getString();
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
        return batchDurationTicks;
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
        return status.equals("working") || status.equals("low_steam") || status.equals("auxiliary_shortfall")
                ? batchSteamPerTickMb : 0;
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

    public boolean isOutputBlocked() {
        return hasPendingOutputs();
    }

    //////////////////////////////////////
    // ***** Air intake snapshot (议题 12) ******//
    //////////////////////////////////////

    /** Whether a Steam Air Intake Hatch is part of the formed structure. */
    public boolean hasAirIntake() {
        return !airIntakeHatches.isEmpty();
    }

    /** The structure's first air intake in stable position order, or null. */
    @Nullable
    protected SteamAirIntakeHatchPartMachine primaryAirIntake() {
        return airIntakeHatches.isEmpty() ? null : airIntakeHatches.get(0);
    }

    /** All air intake hatches in stable position order (大型蒸汽高炉多鼓风口聚合抽取用). */
    protected List<SteamAirIntakeHatchPartMachine> airIntakes() {
        return airIntakeHatches;
    }

    /** The intake's own stable status id (structure/dimension/blocked/full/collecting). */
    public String getAirIntakeStatusId() {
        return airIntakeHatches.isEmpty() ? "" : airIntakeHatches.get(0).getIntakeStatus().getId();
    }

    /** Cached air in mB; 0 when no intake is installed. */
    public long getAirIntakeStored() {
        return airIntakeHatches.isEmpty() ? 0 : airIntakeHatches.get(0).tank.getFluidInTank(0).getAmount();
    }

    /** Fixed intake cache capacity in mB (`64,000`); 0 when no intake is installed. */
    public long getAirIntakeCapacity() {
        return airIntakeHatches.isEmpty() ? 0 : SteamAirIntakeHatchPartMachine.INITIAL_TANK_CAPACITY;
    }

    /** 拆除清理: batch, pending outputs and preference never survive. */
    @Override
    public void onMachineRemoved() {
        hasBatch = false;
        batchRecipe = null;
        batchRecipeId = "";
        preferredRecipeId = "";
        batchProgress = 0;
        batchParallel = 0;
        batchDurationTicks = 0;
        batchTotalSteamMb = 0;
        batchSteamPerTickMb = 0;
        batchOutputMultiplier = 1.0f;
        batchInputDisplay = ItemStack.EMPTY;
        pendingOutputs.clear();
        pendingFluids.clear();
        exhaustFeedbackTimer = 0;
        exhaustDamageTimer = 0;
        exhaustBlocked = false;
    }
}
