package com.hoshino.gregsteamexpansion.machine.multiblock;

import com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.capability.recipe.IRecipeHandler;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.UITemplate;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.api.machine.feature.IMachineLife;
import com.gregtechceu.gtceu.api.machine.feature.IDropSaveMachine;
import com.gregtechceu.gtceu.api.machine.feature.IMachineModifyDrops;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.ingredient.FluidIngredient;
import com.gregtechceu.gtceu.common.data.GTMaterials;
import com.gregtechceu.gtceu.common.data.GTItems;
import com.gregtechceu.gtceu.common.machine.multiblock.steam.LargeBoilerMachine;
import com.gregtechceu.gtceu.config.ConfigHolder;
import com.hoshino.gregsteamexpansion.GregSteamExpansion;
import com.hoshino.gregsteamexpansion.difficulty.Difficulty;
import com.hoshino.gregsteamexpansion.difficulty.GSEDifficultyConfig;
import com.hoshino.gregsteamexpansion.difficulty.GSEDifficultyState;
import com.hoshino.gregsteamexpansion.machine.CoFiringPowderFuel;
import com.hoshino.gregsteamexpansion.machine.multiblock.part.SteamAirIntakeHatchPartMachine;
import com.hoshino.gregsteamexpansion.registry.GSERecipeTypes;
import com.hoshino.gregsteamexpansion.terminal.UltimateTerminalModuleProvider;

import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.texture.ProgressTexture;
import com.lowdragmc.lowdraglib.gui.widget.ComponentPanelWidget;
import com.lowdragmc.lowdraglib.gui.widget.DraggableScrollableWidgetGroup;
import com.lowdragmc.lowdraglib.gui.widget.LabelWidget;
import com.lowdragmc.lowdraglib.gui.widget.ProgressWidget;
import com.lowdragmc.lowdraglib.gui.util.ClickData;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;

import net.minecraft.ChatFormatting;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.annotation.ParametersAreNonnullByDefault;

/**
 * 锅炉房 / Boiler Room (boiler-room.md): the four-tier terminal steam boiler
 * extending GTCEu's {@link LargeBoilerMachine} skeleton (throttle ±, dry-burn
 * explosion, muffler venting, water-to-steam conversion, throttle-scaled
 * burn time via the inherited logic) with this mod's mixed-fuel semantics —
 * co-firing ONLY (P1#5): a liquid-fuel recipe burns while a
 * {@code co_firing_dust_fuels} powder feeds the +50% output multiplier
 * (P1#4/P1#8). Steam generation follows the explicit rule
 * {@code temperature × throttle / 20 × 1.5 × difficulty} per tick (P1#4,
 * P2#12: Easy ×3, Normal ×2, Expert ×1.5); the tiers differ only in
 * {@code maxTemperature} (800/1800/3200/6400) and the heating/cooling cadence
 * (P1#6), where missing-powder cooling must always beat both shutdown cooling
 * and heating (hard constraint).
 *
 * <p>The parent's steam-generation subscription is disabled: the per-tier
 * cadence (integer tick intervals) cannot be expressed through the parent's
 * integer {@code heatSpeed}, so this class keeps its own persisted
 * {@link #roomTemperature} and is exclusively responsible for steam output.
 * The steam air intake hatch is the hard co-firing
 * prerequisite (P2#10): without it the room never runs, and each tier
 * continuously draws its own air amount (50/100/200/400 mB/t). No steam
 * input/output hatches of any kind and no steam item bus are admissible
 * (P2#9 hard constraint) — fluids move through GTCEu standard hatches,
 * venting through the muffler.</p>
 */
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class BoilerRoomMachine extends LargeBoilerMachine implements IMachineLife, IDropSaveMachine,
        IMachineModifyDrops, UltimateTerminalModuleProvider {

    protected static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(
            BoilerRoomMachine.class, LargeBoilerMachine.MANAGED_FIELD_HOLDER);

    /** 协同燃烧 +50% 产出乘子 (P1#4). */
    public static final double CO_FIRING_MULTIPLIER = 1.5;
    /** 粉料消耗速率: 十分之一 tick / tick (沿用单方块高压口径, P1#8). */
    private static final int TENTHS_PER_POWDER_BURN_TICK = 6;
    public static final double SCALE_STAGE_SIZE = 0.25;
    private static final double SCALE_WARNING_THRESHOLD = 0.75;
    private static final double SCALE_SCRAP_THRESHOLD = 1.0;
    private static final String ITEM_SCALE_KEY = "GSEBoilerWaterScale";
    private static final long MODULE_VALIDATION_INTERVAL_TICKS = 20L;
    private static final int SAFE_INTERNAL_TEMPERATURE = 26; // 300 K on GTCEu's +274 display convention.
    private static final int HOT_WASH_MAX_INTERNAL_TEMPERATURE = 199; // 473 K.
    private static final int FORCE_COOLING_DURATION = 1_200;

    /** Tier constants (P1#6/P2#10): max temperature / heat / cooldown / no-powder cooldown / air, by tier index 0-3. */
    public static final int BRONZE_TIER = 0;
    public static final int STEEL_TIER = 1;
    public static final int TITANIUM_TIER = 2;
    public static final int TUNGSTENSTEEL_TIER = 3;
    public static final int[] MAX_TEMPERATURES = {800, 1800, 3200, 6400};
    public static final int[] HEAT_INTERVALS = {20, 12, 8, 5};
    public static final int[] COOL_INTERVALS = {30, 20, 15, 10};
    public static final int[] NO_POWDER_COOL_INTERVALS = {15, 10, 6, 4};
    public static final int[] AIR_PER_TICK = {50, 100, 200, 400};
    public static final int[] WATER_SOFTENER_REDUCTION_PERCENT = {40, 55, 70, 85};
    public static final int[] WATER_SOFTENER_RESIN_TICKS = {3_600, 2_400, 1_800, 1_200};

    private static final int DESCALING_COLD = 0;
    private static final int DESCALING_AUTO = 1;
    private static final int DESCALING_HOT = 2;
    private static final int AUTO_IDLE = 0;
    private static final int AUTO_COOLING = 1;
    private static final int AUTO_WASHING = 2;

    //////////////////////////////////////
    // ***** Tier configuration ******//
    //////////////////////////////////////

    private final int tierIndex;
    private final int heatIntervalTicks;
    private final int cooldownIntervalTicks;
    private final int noPowderCooldownIntervalTicks;
    private final int airConsumptionPerTick;

    //////////////////////////////////////
    // ***** Persisted state ******//
    //////////////////////////////////////

    /** 房内温度: the parent's field stays unused; this copy drives everything. */
    @Persisted
    private int roomTemperature;
    /** 5-tick cycle steam amount, for the display panel (per-tick = /5). */
    @Persisted
    private int cycleSteamGenerated;
    @Persisted
    private ItemStack burningPowder = ItemStack.EMPTY;
    @Persisted
    private int powderBurnRemaining;
    @Persisted
    private int powderBurnTotal;
    @Persisted
    private int powderConsumptionTenths;
    /** Normalized 0..1 lifetime scale progress; values below 1 remain acid-cleanable. */
    @Persisted
    private double waterScaleProgress;
    @Persisted
    private boolean scrappedByScale;
    @Persisted
    private int descalingTicksRemaining;
    @Persisted
    private int descalingTicksTotal;
    /** Exact throttle-percent ticks already dissolved into the connected softener. */
    @Persisted
    private long waterSoftenerDoseUnits;
    @Persisted
    private boolean forcedDraftEnabled;
    @Persisted
    private boolean atomizerEnabled;
    @Persisted
    private boolean atomizerBatchLocked;
    @Persisted
    private boolean condenserEnabled;
    @Persisted
    private boolean automaticWashEnabled;
    @Persisted
    private int automaticWashThreshold = 75;
    @Persisted
    private int automaticWashPhase;
    @Persisted
    private boolean automaticWashResumeEnabled;
    @Persisted
    private int forceCoolingTicksRemaining;
    @Persisted
    private int forceCoolingStartTemperature;
    @Persisted
    private long steamBufferAmount;
    @Persisted
    private int descalingMode;
    @Persisted
    private int descalingAcidConsumed;
    @Persisted
    private boolean acidRecoveryEligible;

    //////////////////////////////////////
    // ***** Runtime state ******//
    //////////////////////////////////////

    @Nullable
    private TickableSubscription roomTemperatureSubs;
    @Nullable
    private TickableSubscription moduleSubs;
    private int heatCounter;
    private int coolCounter;
    private long lastModuleValidationTick = Long.MIN_VALUE;
    private boolean waterSoftenerAppliedLastCycle;
    private boolean forcedDraftHalfTick;
    private final Map<String, ModuleRuntime> modules = new HashMap<>();
    @Nullable
    private GTRecipe waterSoftenerTransactionRecipe;
    /** Collected on formation: the roof-strip air intakes. */
    private final List<SteamAirIntakeHatchPartMachine> airIntakes = new ArrayList<>();

    private record ModuleRuntime(BoilerRoomModules.Status status, @Nullable IMultiPart port) {}

    /**
     * @param tierIndex 0..3 = bronze / steel / titanium / tungstensteel
     *                  (P1#6 numbers; maxTemperature via the parent ctor).
     */
    public BoilerRoomMachine(IMachineBlockEntity holder, int tierIndex) {
        super(holder, MAX_TEMPERATURES[tierIndex], 1);
        this.tierIndex = tierIndex;
        this.heatIntervalTicks = HEAT_INTERVALS[tierIndex];
        this.cooldownIntervalTicks = COOL_INTERVALS[tierIndex];
        this.noPowderCooldownIntervalTicks = NO_POWDER_COOL_INTERVALS[tierIndex];
        this.airConsumptionPerTick = AIR_PER_TICK[tierIndex];
    }

    public BoilerRoomMachine(IMachineBlockEntity holder, int tierIndex, Object... args) {
        this(holder, tierIndex);
    }

    @Override
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }

    @Override
    protected RecipeLogic createRecipeLogic(Object... args) {
        return new BoilerRoomLogic(this);
    }

    @Override
    public BoilerRoomLogic getRecipeLogic() {
        return (BoilerRoomLogic) super.getRecipeLogic();
    }


    public int getHeatIntervalTicks() {
        return heatIntervalTicks;
    }

    public int getCooldownIntervalTicks() {
        return cooldownIntervalTicks;
    }

    public int getNoPowderCooldownIntervalTicks() {
        return noPowderCooldownIntervalTicks;
    }

    public int getAirConsumptionPerTick() {
        return airConsumptionPerTick;
    }

    public int getTierIndex() {
        return tierIndex;
    }

    @Override
    public List<UltimateTerminalModuleProvider.Module> terminalModules() {
        if (!GSEDifficultyConfig.externalModulesEnabled()) return List.of();
        if (!(getLevel() instanceof ServerLevel level)) return List.of();
        return BoilerRoomModules.ALL.stream()
                .filter(module -> BoilerRoomModules.countPresent(
                        level, getPos(), getFrontFacing(), tierIndex, module) > 0)
                .map(module -> new UltimateTerminalModuleProvider.Module(
                        module.id(), module.translationKey(), BoilerRoomModules.terminalRequirements(
                                getPos(), getFrontFacing(), tierIndex, module)))
                .toList();
    }

    public int getRoomTemperature() {
        return roomTemperature;
    }

    public int getCycleSteamGenerated() {
        return cycleSteamGenerated;
    }

    public ItemStack getBurningPowder() {
        return burningPowder;
    }

    public int getPowderBurnRemaining() {
        return powderBurnRemaining;
    }

    public double getWaterScaleProgress() {
        return Math.max(0.0, Math.min(1.0, waterScaleProgress));
    }

    public int getWaterScalePercent() {
        return (int) Math.floor(getWaterScaleProgress() * 100.0 + 1.0e-9);
    }

    public BoilerScaleStage getWaterScaleStage() {
        return BoilerScaleStage.fromProgress(getWaterScaleProgress(), scrappedByScale);
    }

    public int getWaterScaleLossPercent() {
        if (!isWaterScaleEffective() || scrappedByScale) return 0;
        return GSEDifficultyState.boilerRoomScaleLossPercent(isRemote(), getWaterScaleStage().index());
    }

    public boolean isScrappedByScale() {
        return scrappedByScale;
    }

    public boolean isDescaling() {
        return descalingTicksRemaining > 0;
    }

    public int getDescalingTicksRemaining() {
        return descalingTicksRemaining;
    }

    public int getDescalingTicksTotal() {
        return descalingTicksTotal;
    }

    public double getDescalingProgress() {
        return descalingTicksTotal <= 0 ? 0.0
                : 1.0 - descalingTicksRemaining / (double) descalingTicksTotal;
    }

    private boolean isWaterScaleEffective() {
        return GSEDifficultyConfig.boilerRoomWaterScaleEnabled()
                && GSEDifficultyState.boilerRoomScaleFailureHours(isRemote()) > 0.0;
    }

    public int getWaterSoftenerReductionPercent() {
        return WATER_SOFTENER_REDUCTION_PERCENT[tierIndex];
    }

    public long getWaterSoftenerDoseUnits() {
        return Math.max(0, waterSoftenerDoseUnits);
    }

    public long getWaterSoftenerRemainingTicksAtCurrentThrottle() {
        return getWaterSoftenerDoseUnits() / Math.max(1, getThrottle());
    }

    public String getWaterSoftenerStatusId() {
        return moduleStatus(BoilerRoomModules.WATER_SOFTENER).key();
    }

    public boolean isForcedDraftEnabled() { return forcedDraftEnabled; }

    public boolean isAtomizerEnabled() { return atomizerEnabled; }

    public boolean isCondenserEnabled() { return condenserEnabled; }

    public boolean isAutomaticWashEnabled() { return automaticWashEnabled; }

    public int getAutomaticWashThreshold() { return automaticWashThreshold; }

    public boolean isForceCooling() { return forceCoolingTicksRemaining > 0; }

    private boolean hasFrozenModuleTransaction() {
        return !GSEDifficultyConfig.externalModulesEnabled()
                && (atomizerBatchLocked || isForceCooling() || automaticWashPhase != AUTO_IDLE
                || isDescaling() && (descalingMode != DESCALING_COLD || acidRecoveryEligible));
    }

    public long getSteamBufferAmount() { return Math.max(0, steamBufferAmount); }

    public boolean isWaterSoftenerAppliedLastCycle() {
        return waterSoftenerAppliedLastCycle;
    }

    public static long waterSoftenerDoseUnitsPerResin(int tierIndex) {
        return (long) WATER_SOFTENER_RESIN_TICKS[tierIndex] * 100L;
    }

    public static long waterSoftenerCycleCost(int throttlePercent, int cycleTicks) {
        return (long) Math.max(0, throttlePercent) * Math.max(0, cycleTicks);
    }

    public static double applyWaterSoftenerReduction(double scaleIncrement, int reductionPercent) {
        if (scaleIncrement <= 0.0) return 0.0;
        int boundedReduction = Math.max(0, Math.min(100, reductionPercent));
        return scaleIncrement * (100 - boundedReduction) / 100.0;
    }

    /** Exact per-tick output before the five-tick water conversion batch. */
    public static long calculateSteamOutputPerTick(int temperature, int throttle, Difficulty difficulty) {
        return BoilerRoomThermalLogic.steamOutputPerTick(temperature, throttle, difficulty);
    }

    /** Same formula with an explicit multiplier, allowing disabled difficulty to use neutral x1. */
    public static long calculateSteamOutputPerTick(int temperature, int throttle, float difficultyMultiplier) {
        return BoilerRoomThermalLogic.steamOutputPerTick(temperature, throttle, difficultyMultiplier);
    }

    public static long applyWaterScaleLoss(long cleanOutput, int lossPercent) {
        return BoilerRoomThermalLogic.applyScaleLoss(cleanOutput, lossPercent);
    }

    /** Converts a production cycle into equivalent-full-load lifetime progress. */
    public static double calculateWaterScaleIncrement(long cleanOutput, long maximumCleanOutput,
                                                       int cycleTicks, double failureHours) {
        return BoilerRoomThermalLogic.scaleIncrement(cleanOutput, maximumCleanOutput, cycleTicks, failureHours);
    }

    public static int getStoredWaterScalePercent(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(ITEM_SCALE_KEY, Tag.TAG_DOUBLE)) return 0;
        return (int) Math.floor(Math.max(0.0, Math.min(1.0, tag.getDouble(ITEM_SCALE_KEY))) * 100.0);
    }

    //////////////////////////////////////
    // ***** Structure parts ******//
    //////////////////////////////////////

    @Override
    public void onStructureFormed() {
        super.onStructureFormed();
        collectBoilerParts();
        if (GSEDifficultyConfig.externalModulesEnabled() && getLevel() instanceof ServerLevel level) {
            BoilerRoomModuleWorldData.getOrCreate(level).claimBody(
                    BoilerRoomModules.bodyClaim(getPos(), getFrontFacing()));
        }
        refreshModules(true);
    }

    @Override
    public void onStructureInvalid() {
        super.onStructureInvalid();
        airIntakes.clear();
    }

    private void collectBoilerParts() {
        airIntakes.clear();
        for (IMultiPart part : getParts()) {
            if (part instanceof SteamAirIntakeHatchPartMachine intake) {
                airIntakes.add(intake);
            }
        }
    }

    //////////////////////////////////////
    // ***** External modules ******//
    //////////////////////////////////////

    private void updateModuleSubscription() {
        moduleSubs = subscribeServerTick(moduleSubs, () -> refreshModules(false));
    }

    private void refreshModules(boolean force) {
        if (!GSEDifficultyConfig.externalModulesEnabled()) return;
        if (!(getLevel() instanceof ServerLevel level)) return;
        long now = level.getGameTime();
        if (!force && lastModuleValidationTick != Long.MIN_VALUE
                && now - lastModuleValidationTick < MODULE_VALIDATION_INTERVAL_TICKS) return;
        lastModuleValidationTick = now;
        var data = BoilerRoomModuleWorldData.getOrCreate(level);
        data.claimBody(BoilerRoomModules.bodyClaim(getPos(), getFrontFacing()));
        for (BoilerRoomModules.Descriptor descriptor : BoilerRoomModules.ALL) {
            var validation = BoilerRoomModules.validate(level, getPos(), getFrontFacing(), tierIndex, descriptor);
            BoilerRoomModules.Status next = validation.status();
            IMultiPart port = validation.portPart();
            if (next == BoilerRoomModules.Status.VALID) {
                var result = data.claim(BoilerRoomModules.claim(getPos(), getFrontFacing(), descriptor));
                if (!result.success()) {
                    data.release(getPos(), descriptor.id());
                    next = BoilerRoomModules.Status.CONFLICT;
                    port = null;
                }
            } else if (next != BoilerRoomModules.Status.UNLOADED) {
                data.release(getPos(), descriptor.id());
            }
            ModuleRuntime old = modules.put(descriptor.id(), new ModuleRuntime(next, port));
            boolean confirmedInvalid = next != BoilerRoomModules.Status.VALID
                    && next != BoilerRoomModules.Status.UNLOADED;
            boolean lostRuntimeModule = old != null && old.status() == BoilerRoomModules.Status.VALID;
            if (confirmedInvalid && (lostRuntimeModule || old == null && hasPersistedModuleState(descriptor))) {
                onModuleInvalidated(descriptor);
            }
        }
    }

    private boolean hasPersistedModuleState(BoilerRoomModules.Descriptor descriptor) {
        if (descriptor == BoilerRoomModules.WATER_SOFTENER) return waterSoftenerDoseUnits > 0;
        if (descriptor == BoilerRoomModules.COOLING_TANK) return isForceCooling();
        if (descriptor == BoilerRoomModules.AUTO_WASH_STATION) return automaticWashPhase == AUTO_COOLING;
        if (descriptor == BoilerRoomModules.HOT_ACID_FACILITY) {
            return descalingMode == DESCALING_HOT && isDescaling();
        }
        if (descriptor == BoilerRoomModules.BUFFER_TANK) return steamBufferAmount > 0;
        return descriptor == BoilerRoomModules.ATOMIZATION_ROOM && atomizerBatchLocked;
    }

    private void onModuleInvalidated(BoilerRoomModules.Descriptor descriptor) {
        if (descriptor == BoilerRoomModules.WATER_SOFTENER) {
            waterSoftenerDoseUnits = 0;
            waterSoftenerAppliedLastCycle = false;
        } else if (descriptor == BoilerRoomModules.COOLING_TANK && isForceCooling()) {
            forceCoolingTicksRemaining = 0;
        } else if (descriptor == BoilerRoomModules.AUTO_WASH_STATION
                && automaticWashPhase == AUTO_COOLING) {
            restoreAutomaticWashState();
        } else if (descriptor == BoilerRoomModules.HOT_ACID_FACILITY
                && descalingMode == DESCALING_HOT && isDescaling()) {
            cancelDescaling();
        } else if (descriptor == BoilerRoomModules.BUFFER_TANK) {
            steamBufferAmount = 0;
        } else if (descriptor == BoilerRoomModules.ATOMIZATION_ROOM && atomizerBatchLocked) {
            getRecipeLogic().interruptRecipe();
            atomizerBatchLocked = false;
        }
        markDirty();
    }

    private BoilerRoomModules.Status moduleStatus(BoilerRoomModules.Descriptor descriptor) {
        if (!GSEDifficultyConfig.externalModulesEnabled()) return BoilerRoomModules.Status.MISSING;
        ModuleRuntime runtime = modules.get(descriptor.id());
        return runtime == null ? BoilerRoomModules.Status.MISSING : runtime.status();
    }

    private boolean moduleValid(BoilerRoomModules.Descriptor descriptor) {
        refreshModules(false);
        return moduleStatus(descriptor) == BoilerRoomModules.Status.VALID;
    }

    private List<IRecipeHandler<?>> moduleHandlers(BoilerRoomModules.Descriptor descriptor,
                                                   IO io, boolean item) {
        if (!GSEDifficultyConfig.externalModulesEnabled()) return List.of();
        ModuleRuntime runtime = modules.get(descriptor.id());
        if (runtime == null || runtime.status() != BoilerRoomModules.Status.VALID || runtime.port() == null) {
            return List.of();
        }
        List<IRecipeHandler<?>> result = new ArrayList<>();
        for (var list : runtime.port().getRecipeHandlers()) {
            if (!list.isValid(io)) continue;
            result.addAll(list.getHandlerMap().getOrDefault(
                    item ? ItemRecipeCapability.CAP : FluidRecipeCapability.CAP, List.of()));
        }
        return result;
    }

    private static boolean isStickyResin(ItemStack stack) {
        return !stack.isEmpty() && stack.is(GTItems.STICKY_RESIN.asStack().getItem());
    }

    private boolean hasStickyResin() {
        for (IRecipeHandler<?> handler : moduleHandlers(BoilerRoomModules.WATER_SOFTENER, IO.IN, true)) {
            for (Object content : handler.getContents()) {
                if (content instanceof ItemStack stack && isStickyResin(stack)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Draw one resin only after a complete steam cycle proves that scale would be added. */
    @SuppressWarnings("unchecked")
    private boolean extractOneStickyResin() {
        Ingredient one = Ingredient.of(GTItems.STICKY_RESIN.asStack());
        for (IRecipeHandler<?> handler : moduleHandlers(BoilerRoomModules.WATER_SOFTENER, IO.IN, true)) {
            boolean present = false;
            for (Object content : handler.getContents()) {
                if (content instanceof ItemStack stack && isStickyResin(stack)) {
                    present = true;
                    break;
                }
            }
            if (!present) continue;
            List<Ingredient> left = (List<Ingredient>) handler.handleRecipe(
                    IO.IN, waterSoftenerTransactionRecipe(), List.of(one), false);
            if (left == null || left.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private GTRecipe waterSoftenerTransactionRecipe() {
        if (waterSoftenerTransactionRecipe == null) {
            waterSoftenerTransactionRecipe = GSERecipeTypes.BOILER_ROOM_RECIPES
                    .recipeBuilder(GregSteamExpansion.id("water_softener_transaction"))
                    .duration(1)
                    .buildRawRecipe();
        }
        return waterSoftenerTransactionRecipe;
    }

    private boolean consumeWaterSoftenerDoseForCycle() {
        waterSoftenerAppliedLastCycle = false;
        refreshModules(true);
        if (!isWaterScaleEffective() || !moduleValid(BoilerRoomModules.WATER_SOFTENER)) {
            return false;
        }
        long cost = waterSoftenerCycleCost(getThrottle(), TICKS_PER_STEAM_GENERATION);
        if (cost <= 0) {
            return false;
        }
        if (waterSoftenerDoseUnits < cost && extractOneStickyResin()) {
            waterSoftenerDoseUnits += waterSoftenerDoseUnitsPerResin(tierIndex);
        }
        if (waterSoftenerDoseUnits < cost) {
            return false;
        }
        waterSoftenerDoseUnits -= cost;
        waterSoftenerAppliedLastCycle = true;
        markDirty();
        return true;
    }

    //////////////////////////////////////
    // ***** Co-firing powder ******//
    //////////////////////////////////////

    /**
     * All item-input recipe handlers attached to the formed structure. Reading
     * the controller capability map also supports addon buses which advertise
     * {@code IMPORT_ITEMS} without extending GTCEu's concrete
     * {@code ItemBusPartMachine} (for example GTM Things' creative input bus).
     */
    private List<IRecipeHandler<?>> getPowderInputs() {
        List<IRecipeHandler<?>> inputs = new ArrayList<>();
        inputs.addAll(getCapabilitiesFlat(IO.IN, ItemRecipeCapability.CAP));
        inputs.addAll(getCapabilitiesFlat(IO.BOTH, ItemRecipeCapability.CAP));
        return inputs;
    }

    private static ItemStack firstValidPowder(IRecipeHandler<?> handler) {
        for (Object content : handler.getContents()) {
            if (content instanceof ItemStack stack && CoFiringPowderFuel.isValid(stack)) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    /** Extracts one powder item across every compatible item-input handler. */
    private ItemStack extractOnePowder(@Nullable GTRecipe recipe) {
        if (recipe == null) return ItemStack.EMPTY;
        for (IRecipeHandler<?> handler : getPowderInputs()) {
            // Consume through GTCEu's recipe handler. Input buses intentionally
            // reject extraction through their external item capability, while
            // this path performs the controller-authorized internal draw for
            // native, addon, creative and network-backed inputs alike.
            ItemStack candidate = firstValidPowder(handler);
            if (!candidate.isEmpty()) {
                Ingredient one = Ingredient.of(candidate.copyWithCount(1));
                List<?> left = handler.handleRecipe(IO.IN, recipe, List.of(one), false);
                if (left == null || left.isEmpty()) {
                    return candidate.copyWithCount(1);
                }
            }
        }
        return ItemStack.EMPTY;
    }

    /** Refills the powder heat buffer from the buses; false when nothing to burn. */
    private boolean preparePowder(@Nullable GTRecipe recipe) {
        if (powderBurnRemaining > 0) return true;
        ItemStack powder = extractOnePowder(recipe);
        if (powder.isEmpty()) return false;
        int burnTime = CoFiringPowderFuel.burnTime(powder);
        if (burnTime <= 0) return false;
        burningPowder = powder.copyWithCount(1);
        powderBurnRemaining = burnTime;
        powderBurnTotal = burnTime;
        markDirty();
        return true;
    }

    private void consumePowderTick() {
        powderConsumptionTenths += TENTHS_PER_POWDER_BURN_TICK;
        while (powderConsumptionTenths >= 10 && powderBurnRemaining > 0) {
            powderConsumptionTenths -= 10;
            powderBurnRemaining--;
        }
        if (powderBurnRemaining == 0) {
            powderBurnTotal = 0;
            burningPowder = ItemStack.EMPTY;
        }
    }

    private boolean hasUsablePowder() {
        if (powderBurnRemaining > 0) return true;
        for (IRecipeHandler<?> handler : getPowderInputs()) {
            if (!firstValidPowder(handler).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /** 缺粉 (P1#8): no buffer and no powder in any bus — pause + accelerated cooling. */
    public boolean isMissingPowder() {
        return !hasUsablePowder();
    }

    /** 协同粉料燃烧进度 (0–1), for the display panel. */
    public double getPowderProgress() {
        return powderBurnTotal == 0 ? 0 : powderBurnRemaining / (double) powderBurnTotal;
    }

    //////////////////////////////////////
    // ***** Air intake ******//
    //////////////////////////////////////

    /** 助燃空气前置 (P2#10): the intake hatch is mandatory for co-firing. */
    public boolean hasAirIntake() {
        return !airIntakes.isEmpty();
    }

    /** True when the combined intakes cannot supply this tier's per-tick air amount. */
    public boolean isAirStarved() {
        return !canConsumeAir(requiredAirForLockedBatch());
    }

    private int requiredAirForLockedBatch() {
        return atomizerBatchLocked ? atomizerAirDemand(airConsumptionPerTick) : airConsumptionPerTick;
    }

    public static int atomizerAirDemand(int baseAir) {
        return Math.max(0, (baseAir * 5 + 3) / 4);
    }

    public static int forceCoolingWaterRequired(int internalTemperature) {
        return Math.max(0, internalTemperature - SAFE_INTERNAL_TEMPERATURE) * 25;
    }

    public static SteamSplit condenserSplit(long actualSteam, int steamPerWater) {
        if (actualSteam <= 0 || steamPerWater <= 0) return new SteamSplit(Math.max(0, actualSteam), 0, 0);
        long distilledWater = (actualSteam / 10) / steamPerWater;
        long condensedSteam = distilledWater * steamPerWater;
        return new SteamSplit(actualSteam - condensedSteam, distilledWater, condensedSteam);
    }

    public record SteamSplit(long standardSteam, long distilledWater, long condensedSteam) {}

    private boolean canConsumeAir(int amount) {
        int remaining = Math.max(0, amount);
        for (var intake : airIntakes) {
            remaining -= intake.tank.drainInternal(GTMaterials.Air.getFluid(remaining), FluidAction.SIMULATE)
                    .getAmount();
            if (remaining == 0) return true;
        }
        return remaining == 0;
    }

    /** Simulate the whole demand first so shortage never partially drains the intakes. */
    private boolean consumeAir(int amount) {
        if (!canConsumeAir(amount)) return false;
        int remaining = Math.max(0, amount);
        for (var intake : airIntakes) {
            remaining -= intake.tank.drainInternal(GTMaterials.Air.getFluid(remaining), FluidAction.EXECUTE)
                    .getAmount();
            if (remaining == 0) return true;
        }
        return false;
    }

    //////////////////////////////////////
    // ***** Temperature & steam ******//
    //////////////////////////////////////

    /** True while the fuel recipe is paused for missing powder or air. */
    public boolean isCoFiringPaused() {
        return scrappedByScale || isDescaling() || isForceCooling()
                || isMissingPowder() || isAirStarved()
                || atomizerBatchLocked
                && moduleStatus(BoilerRoomModules.ATOMIZATION_ROOM) == BoilerRoomModules.Status.UNLOADED;
    }

    /** Starts one fixed-cost cycle which removes at most one 25-point scale band. */
    public boolean startDescaling() {
        if (isRemote() || !GSEDifficultyConfig.boilerRoomWaterScaleEnabled() || !isFormed()
                || scrappedByScale || isDescaling() || waterScaleProgress <= 0.0
                || roomTemperature > HOT_WASH_MAX_INTERNAL_TEMPERATURE || getRecipeLogic().isWorking()
                || isStructureTemporarilyUnavailable()) {
            return false;
        }
        int baseAcid = GSEDifficultyConfig.boilerRoomDescalingAcidMb();
        int mode = DESCALING_COLD;
        int acid = baseAcid;
        boolean moduleAcid = false;
        if (roomTemperature > SAFE_INTERNAL_TEMPERATURE) {
            if (!moduleValid(BoilerRoomModules.HOT_ACID_FACILITY)) return false;
            mode = DESCALING_HOT;
            moduleAcid = true;
            acid = hotWashAcidAmount(baseAcid, roomTemperature);
        }
        if (!drainDescalingAcid(acid, true, moduleAcid)
                || !drainDescalingAcid(acid, false, moduleAcid)) return false;
        beginDescaling(mode, acid);
        return true;
    }

    public static int hotWashAcidAmount(int baseAmount, int internalTemperature) {
        if (internalTemperature <= SAFE_INTERNAL_TEMPERATURE) return Math.max(0, baseAmount);
        if (internalTemperature <= 99) return (Math.max(0, baseAmount) * 3 + 1) / 2;
        return Math.multiplyExact(Math.max(0, baseAmount), 3);
    }

    private void beginDescaling(int mode, int acidConsumed) {
        descalingMode = mode;
        descalingAcidConsumed = acidConsumed;
        acidRecoveryEligible = moduleValid(BoilerRoomModules.RECOVERY_POOL);
        descalingTicksTotal = GSEDifficultyConfig.boilerRoomDescalingDurationTicks();
        descalingTicksRemaining = descalingTicksTotal;
        cycleSteamGenerated = 0;
        updateRoomTemperatureSubscription();
        markDirty();
    }

    @SuppressWarnings("unchecked")
    private boolean drainDescalingAcid(int amount, boolean simulate, boolean modulePort) {
        var remaining = List.of(FluidIngredient.of(GTMaterials.DilutedHydrochloricAcid.getFluid(amount)));
        List<IRecipeHandler<?>> inputTanks = modulePort
                ? moduleHandlers(descalingPort(), IO.IN, false) : bodyFluidHandlers(IO.IN);
        for (IRecipeHandler<?> tank : inputTanks) {
            remaining = (List<FluidIngredient>) tank.handleRecipe(IO.IN, null, remaining, simulate);
            if (remaining == null || remaining.isEmpty()) return true;
        }
        return false;
    }

    /** Kept as the baseline-air entry point used by existing diagnostics and compatibility tests. */
    @SuppressWarnings("unused")
    private boolean consumeAir() {
        return consumeAir(airConsumptionPerTick);
    }

    private BoilerRoomModules.Descriptor descalingPort() {
        return automaticWashPhase != AUTO_IDLE
                ? BoilerRoomModules.AUTO_WASH_STATION : BoilerRoomModules.HOT_ACID_FACILITY;
    }

    private List<IRecipeHandler<?>> bodyFluidHandlers(IO direction) {
        List<IRecipeHandler<?>> tanks = new ArrayList<>();
        tanks.addAll(getCapabilitiesFlat(direction, FluidRecipeCapability.CAP));
        tanks.addAll(getCapabilitiesFlat(IO.BOTH, FluidRecipeCapability.CAP));
        return tanks;
    }

    public boolean startForceCooling() {
        if (isRemote() || !isFormed() || roomTemperature <= SAFE_INTERNAL_TEMPERATURE
                || isForceCooling() || !moduleValid(BoilerRoomModules.COOLING_TANK)) return false;
        int water = forceCoolingWaterRequired(roomTemperature);
        if (!drainModuleFluid(BoilerRoomModules.COOLING_TANK, Fluids.WATER, water, true)
                || !drainModuleFluid(BoilerRoomModules.COOLING_TANK, Fluids.WATER, water, false)) return false;
        getRecipeLogic().interruptRecipe();
        getRecipeLogic().setWorkingEnabled(false);
        forceCoolingStartTemperature = roomTemperature;
        forceCoolingTicksRemaining = FORCE_COOLING_DURATION;
        cycleSteamGenerated = 0;
        updateRoomTemperatureSubscription();
        markDirty();
        return true;
    }

    @SuppressWarnings("unchecked")
    private boolean drainModuleFluid(BoilerRoomModules.Descriptor descriptor,
                                     net.minecraft.world.level.material.Fluid fluid,
                                     int amount, boolean simulate) {
        List<FluidIngredient> remaining = List.of(FluidIngredient.of(fluid, amount));
        for (IRecipeHandler<?> handler : moduleHandlers(descriptor, IO.IN, false)) {
            remaining = (List<FluidIngredient>) handler.handleRecipe(IO.IN, null, remaining, simulate);
            if (remaining == null || remaining.isEmpty()) return true;
        }
        return false;
    }

    @Override
    public boolean beforeWorking(@Nullable GTRecipe recipe) {
        if (scrappedByScale || isDescaling() || isForceCooling() || automaticWashPhase != AUTO_IDLE) return false;
        refreshModules(false);
        atomizerBatchLocked = atomizerEnabled && moduleValid(BoilerRoomModules.ATOMIZATION_ROOM);
        if (atomizerBatchLocked && !canConsumeAir(atomizerAirDemand(airConsumptionPerTick))) {
            atomizerBatchLocked = false;
            return false;
        }
        // 开工预检 (P1#8/P2#10): powder buffer refill + intake presence, else waiting.
        boolean accepted = preparePowder(recipe) && hasAirIntake() && super.beforeWorking(recipe);
        if (!accepted) atomizerBatchLocked = false;
        return accepted;
    }

    @Override
    public boolean onWorking() {
        if (!GSEDifficultyConfig.externalModulesEnabled() && atomizerBatchLocked) {
            return false;
        }
        boolean working = super.onWorking();
        if (working) {
            if (roomTemperature < getMaxTemperature()) {
                roomTemperature = Math.max(1, roomTemperature);
                updateRoomTemperatureSubscription();
            }
            consumePowderTick();
        }
        return working;
    }

    @Override
    public void afterWorking() {
        atomizerBatchLocked = false;
        super.afterWorking();
    }

    @Override
    public void onUnload() {
        if (roomTemperatureSubs != null) {
            roomTemperatureSubs.unsubscribe();
            roomTemperatureSubs = null;
        }
        if (moduleSubs != null) {
            moduleSubs.unsubscribe();
            moduleSubs = null;
        }
        super.onUnload();
    }

    @Override
    public void onLoad() {
        super.onLoad();
        // 区块重载后按持久化温度恢复产汽订阅 (parent keys on its own field).
        if (getLevel() instanceof ServerLevel serverLevel) {
            serverLevel.getServer().tell(new TickTask(0, () -> {
                updateRoomTemperatureSubscription();
                updateModuleSubscription();
                refreshModules(true);
            }));
        }
    }

    protected void updateRoomTemperatureSubscription() {
        if (roomTemperature > 0 || isDescaling() || isForceCooling()
                || automaticWashPhase != AUTO_IDLE || steamBufferAmount > 0) {
            roomTemperatureSubs = subscribeServerTick(roomTemperatureSubs, this::updateRoomTemperature);
        } else if (roomTemperatureSubs != null) {
            roomTemperatureSubs.unsubscribe();
            roomTemperatureSubs = null;
        }
    }

    /**
     * The inherited large-boiler loop must never run for a boiler room. Its
     * own temperature and steam output would otherwise operate in parallel
     * with {@link #updateRoomTemperature()}, adding a second difficulty-scaled
     * steam stream to every output hatch.
     */
    @Override
    protected void updateSteamSubscription() {
        if (temperatureSubs != null) {
            temperatureSubs.unsubscribe();
            temperatureSubs = null;
        }
    }

    /**
     * P1#6 cadence with the hard constraint: no-powder cooling beats both
     * shutdown cooling and heating on every tier. Steam generation runs on
     * the parent's 5-tick cycle with the P1#4 co-firing formula.
     */
    protected void updateRoomTemperature() {
        // GTCEu persists isFormed, but recreates MultiblockState as UNINIT_ERROR
        // after a world load. It can also keep isFormed true with UNLOAD_ERROR
        // while part chunks reconnect. In both cases the capability map is not
        // safe to use yet, even when a chunk loader keeps the whole range loaded.
        if (isStructureTemporarilyUnavailable()) {
            cycleSteamGenerated = 0;
            return;
        }

        if (hasFrozenModuleTransaction()) {
            cycleSteamGenerated = 0;
            return;
        }

        refreshModules(false);
        updateAutomaticWash();
        drainSteamBuffer();
        if (automaticWashPhase == AUTO_COOLING && roomTemperature <= SAFE_INTERNAL_TEMPERATURE) {
            cycleSteamGenerated = 0;
            return;
        }
        if (isForceCooling()) {
            BoilerRoomModules.Status status = moduleStatus(BoilerRoomModules.COOLING_TANK);
            if (status == BoilerRoomModules.Status.UNLOADED) {
                cycleSteamGenerated = 0;
                return;
            }
            if (status != BoilerRoomModules.Status.VALID) {
                forceCoolingTicksRemaining = 0;
                markDirty();
            } else {
                int elapsed = FORCE_COOLING_DURATION - forceCoolingTicksRemaining + 1;
                roomTemperature = Math.max(SAFE_INTERNAL_TEMPERATURE,
                        forceCoolingStartTemperature - (int) Math.ceil(
                                (forceCoolingStartTemperature - SAFE_INTERNAL_TEMPERATURE)
                                        * elapsed / (double) FORCE_COOLING_DURATION));
                forceCoolingTicksRemaining--;
                cycleSteamGenerated = 0;
                if (forceCoolingTicksRemaining % 20 == 0) markDirty();
                return;
            }
        }

        if (descalingMode == DESCALING_HOT && isDescaling()) {
            BoilerRoomModules.Status hotStatus = moduleStatus(BoilerRoomModules.HOT_ACID_FACILITY);
            if (hotStatus == BoilerRoomModules.Status.UNLOADED) {
                cycleSteamGenerated = 0;
                return;
            }
            if (hotStatus != BoilerRoomModules.Status.VALID) cancelDescaling();
        }

        boolean working = recipeLogic.isWorking();
        boolean thermalHold = isDescaling() || scrappedByScale;
        boolean airAvailable = !working || thermalHold;
        if (working && !thermalHold) {
            int required = requiredAirForLockedBatch();
            boolean boosted = roomTemperature < getMaxTemperature() && forcedDraftEnabled
                    && moduleValid(BoilerRoomModules.DRAFT_ROOM)
                    && canConsumeAir(Math.max(required, airConsumptionPerTick * 2));
            int demand = boosted ? Math.max(required, airConsumptionPerTick * 2) : required;
            airAvailable = consumeAir(demand);
            if (atomizerBatchLocked && !airAvailable) {
                getRecipeLogic().interruptRecipe();
                atomizerBatchLocked = false;
            }
            if (boosted && airAvailable) {
                forcedDraftHalfTick = !forcedDraftHalfTick;
                if (forcedDraftHalfTick) heatCounter++;
            }
        }
        boolean descalingBefore = isDescaling();
        var result = BoilerRoomThermalLogic.advance(
                new BoilerRoomThermalLogic.ThermalState(roomTemperature, heatCounter, coolCounter,
                        descalingTicksRemaining, descalingTicksTotal, waterScaleProgress),
                new BoilerRoomThermalLogic.TickInput(
                        working, airAvailable,
                        !working && !thermalHold && isMissingPowder(), scrappedByScale,
                        getMaxTemperature(), heatIntervalTicks, cooldownIntervalTicks,
                        noPowderCooldownIntervalTicks));
        applyThermalState(result.state());
        if (descalingBefore && !isDescaling()) finishDescaling();
        if (result.markDirty()) markDirty();
        if (result.suppressSteamGeneration()) {
            cycleSteamGenerated = 0;
            updateRoomTemperatureSubscription();
            return;
        }

        if (isFormed() && getOffsetTimer() % TICKS_PER_STEAM_GENERATION == 0) {
            generateSteamCycle();
        }
        updateRoomTemperatureSubscription();
    }

    private void updateAutomaticWash() {
        if (!automaticWashEnabled || scrappedByScale || isForceCooling()) return;
        if (automaticWashPhase == AUTO_IDLE && !isDescaling()
                && getWaterScalePercent() >= automaticWashThreshold
                && moduleValid(BoilerRoomModules.AUTO_WASH_STATION)) {
            automaticWashResumeEnabled = getRecipeLogic().isWorkingEnabled();
            getRecipeLogic().interruptRecipe();
            getRecipeLogic().setWorkingEnabled(false);
            automaticWashPhase = AUTO_COOLING;
            cycleSteamGenerated = 0;
            markDirty();
        }
        if (automaticWashPhase != AUTO_COOLING) return;
        BoilerRoomModules.Status status = moduleStatus(BoilerRoomModules.AUTO_WASH_STATION);
        if (status != BoilerRoomModules.Status.VALID) {
            if (status != BoilerRoomModules.Status.UNLOADED) restoreAutomaticWashState();
            return;
        }
        if (roomTemperature > SAFE_INTERNAL_TEMPERATURE || waterScaleProgress <= 0.0) return;
        int acid = GSEDifficultyConfig.boilerRoomDescalingAcidMb();
        if (!drainDescalingAcid(acid, true, true) || !drainDescalingAcid(acid, false, true)) return;
        automaticWashPhase = AUTO_WASHING;
        beginDescaling(DESCALING_AUTO, acid);
    }

    private void restoreAutomaticWashState() {
        boolean resume = automaticWashResumeEnabled;
        automaticWashPhase = AUTO_IDLE;
        automaticWashResumeEnabled = false;
        if (resume) getRecipeLogic().setWorkingEnabled(true);
        markDirty();
    }

    private void cancelDescaling() {
        descalingTicksRemaining = 0;
        descalingTicksTotal = 0;
        descalingAcidConsumed = 0;
        acidRecoveryEligible = false;
        descalingMode = DESCALING_COLD;
        cycleSteamGenerated = 0;
        markDirty();
    }

    private void finishDescaling() {
        if (acidRecoveryEligible && moduleValid(BoilerRoomModules.RECOVERY_POOL)) {
            outputModuleFluid(BoilerRoomModules.RECOVERY_POOL,
                    GTMaterials.DilutedHydrochloricAcid.getFluid(), descalingAcidConsumed / 10);
        }
        int completedMode = descalingMode;
        descalingMode = DESCALING_COLD;
        descalingAcidConsumed = 0;
        acidRecoveryEligible = false;
        if (completedMode == DESCALING_AUTO) restoreAutomaticWashState();
        markDirty();
    }

    @SuppressWarnings("unchecked")
    private int outputModuleFluid(BoilerRoomModules.Descriptor descriptor,
                                  net.minecraft.world.level.material.Fluid fluid, int amount) {
        if (amount <= 0) return 0;
        List<FluidIngredient> remaining = List.of(FluidIngredient.of(fluid, amount));
        for (IRecipeHandler<?> handler : moduleHandlers(descriptor, IO.OUT, false)) {
            remaining = (List<FluidIngredient>) handler.handleRecipe(IO.OUT, null, remaining, false);
            if (remaining == null || remaining.isEmpty()) return amount;
        }
        return amount - (remaining == null || remaining.isEmpty() ? 0 : remaining.get(0).getAmount());
    }

    private void applyThermalState(BoilerRoomThermalLogic.ThermalState state) {
        roomTemperature = state.temperature();
        heatCounter = state.heatCounter();
        coolCounter = state.coolCounter();
        descalingTicksRemaining = state.descalingTicksRemaining();
        descalingTicksTotal = state.descalingTicksTotal();
        waterScaleProgress = state.scaleProgress();
    }

    private boolean isStructureTemporarilyUnavailable() {
        return getMultiblockState().hasError() || !isStructureRangeLoaded();
    }

    /** Whether every chunk intersecting the 7-wide, 11-deep structure is loaded. */
    private boolean isStructureRangeLoaded() {
        if (getLevel() == null) return false;

        var back = getFrontFacing().getOpposite();
        var side = getFrontFacing().getClockWise();
        BlockPos backCenter = getPos().relative(back, 10);
        BlockPos[] corners = {
                getPos().relative(side, 3),
                getPos().relative(side, -3),
                backCenter.relative(side, 3),
                backCenter.relative(side, -3),
        };
        int minX = corners[0].getX();
        int maxX = minX;
        int minZ = corners[0].getZ();
        int maxZ = minZ;
        for (int i = 1; i < corners.length; i++) {
            minX = Math.min(minX, corners[i].getX());
            maxX = Math.max(maxX, corners[i].getX());
            minZ = Math.min(minZ, corners[i].getZ());
            maxZ = Math.max(maxZ, corners[i].getZ());
        }

        int minChunkX = Math.floorDiv(minX, 16);
        int maxChunkX = Math.floorDiv(maxX, 16);
        int minChunkZ = Math.floorDiv(minZ, 16);
        int maxChunkZ = Math.floorDiv(maxZ, 16);
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                if (!getLevel().isLoaded(new BlockPos(chunkX * 16, getPos().getY(), chunkZ * 16))) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * P1#4: {@code temperature × throttle / 20 × 1.5 × difficulty} mB/tick,
     * generated (and water drained) on the inherited 5-tick cycle with the
     * parent's water-to-steam ratio; dry burn explodes at strength 2 (P1#7).
     */
    private void generateSteamCycle() {
        waterSoftenerAppliedLastCycle = false;
        if (roomTemperature < 100) {
            cycleSteamGenerated = 0;
            return;
        }
        long baseCleanSteamPerTick = calculateSteamOutputPerTick(
                roomTemperature, getThrottle(), GSEDifficultyState.boilerRoomSteamOutputMultiplier(isRemote()));
        long cleanSteamPerTick = atomizerBatchLocked
                ? Math.round(baseCleanSteamPerTick * 1.10) : baseCleanSteamPerTick;
        long steamPerTick = applyWaterScaleLoss(cleanSteamPerTick, getWaterScaleLossPercent());
        if (steamPerTick <= 0) {
            cycleSteamGenerated = 0;
            return;
        }
        long steamTarget = steamPerTick * TICKS_PER_STEAM_GENERATION;
        int steamPerWater = ConfigHolder.INSTANCE.machines.largeBoilers.steamPerWater;
        long waterNeeded = Math.max(1, (steamTarget + steamPerWater - 1) / steamPerWater);

        var drainWater = List.of(FluidIngredient.of(Fluids.WATER,
                (int) Math.min(waterNeeded, Integer.MAX_VALUE)));
        List<IRecipeHandler<?>> inputTanks = new ArrayList<>();
        inputTanks.addAll(getCapabilitiesFlat(IO.IN, FluidRecipeCapability.CAP));
        inputTanks.addAll(getCapabilitiesFlat(IO.BOTH, FluidRecipeCapability.CAP));
        for (IRecipeHandler<?> tank : inputTanks) {
            drainWater = (List<FluidIngredient>) tank.handleRecipe(IO.IN, null, drainWater, false);
            if (drainWater == null || drainWater.isEmpty()) {
                break;
            }
        }
        long drained = (drainWater == null || drainWater.isEmpty()) ? waterNeeded
                : waterNeeded - drainWater.get(0).getAmount();

        long steamProduced = Math.min(steamTarget, drained * steamPerWater);
        cycleSteamGenerated = (int) Math.min(Integer.MAX_VALUE, steamProduced);
        long standardSteam = steamProduced;
        if (steamProduced > 0 && condenserEnabled && moduleValid(BoilerRoomModules.CONDENSER_TOWER)) {
            SteamSplit split = condenserSplit(steamProduced, steamPerWater);
            standardSteam = split.standardSteam();
            outputModuleFluid(BoilerRoomModules.CONDENSER_TOWER,
                    GTMaterials.DistilledWater.getFluid(),
                    (int) Math.min(Integer.MAX_VALUE, split.distilledWater()));
        }
        long accepted = outputBodySteam(standardSteam);
        long overflow = standardSteam - accepted;
        if (overflow > 0 && moduleValid(BoilerRoomModules.BUFFER_TANK)) {
            long room = Math.max(0, steamBufferCapacity() - steamBufferAmount);
            long buffered = Math.min(room, overflow);
            steamBufferAmount += buffered;
            if (buffered > 0) markDirty();
        }

        // 干烧爆炸 (P1#7): inherited strength-2 explosion, no double-blast rule.
        if (drained < waterNeeded) {
            doExplosion(2f);
        } else if (steamProduced > 0) {
            accumulateWaterScale(cleanSteamPerTick, atomizerBatchLocked);
        }
    }

    @SuppressWarnings("unchecked")
    private long outputBodySteam(long amount) {
        long remainingAmount = Math.max(0, amount);
        for (IRecipeHandler<?> tank : bodyFluidHandlers(IO.OUT)) {
            while (remainingAmount > 0) {
                int chunk = (int) Math.min(Integer.MAX_VALUE, remainingAmount);
                List<FluidIngredient> remaining = (List<FluidIngredient>) tank.handleRecipe(IO.OUT, null,
                        List.of(FluidIngredient.of(GTMaterials.Steam.getFluid(chunk))), false);
                int left = remaining == null || remaining.isEmpty() ? 0 : remaining.get(0).getAmount();
                remainingAmount -= chunk - left;
                if (left > 0) break;
            }
            if (remainingAmount == 0) break;
        }
        return amount - remainingAmount;
    }

    private long baseMaximumSteamPerTick() {
        return calculateSteamOutputPerTick(getMaxTemperature(), 100,
                GSEDifficultyState.boilerRoomSteamOutputMultiplier(isRemote()));
    }

    public long steamBufferCapacity() {
        return Math.multiplyExact(baseMaximumSteamPerTick(), 600L);
    }

    private void drainSteamBuffer() {
        if (steamBufferAmount <= 0 || !isFormed()
                || moduleStatus(BoilerRoomModules.BUFFER_TANK) != BoilerRoomModules.Status.VALID) return;
        long capacity = steamBufferCapacity();
        if (steamBufferAmount > capacity) steamBufferAmount = capacity;
        long attempted = Math.min(steamBufferAmount, baseMaximumSteamPerTick());
        long accepted = outputBodySteam(attempted);
        if (accepted > 0) {
            steamBufferAmount -= accepted;
            markDirty();
        }
    }

    private void accumulateWaterScale(long cleanSteamPerTick, boolean atomized) {
        waterSoftenerAppliedLastCycle = false;
        if (!isWaterScaleEffective() || scrappedByScale || isDescaling() || cleanSteamPerTick <= 0) return;
        double failureHours = GSEDifficultyState.boilerRoomScaleFailureHours(isRemote());
        long maximumCleanOutput = calculateSteamOutputPerTick(getMaxTemperature(), 100,
                GSEDifficultyState.boilerRoomSteamOutputMultiplier(isRemote()));
        double increment = calculateWaterScaleIncrement(cleanSteamPerTick, maximumCleanOutput,
                TICKS_PER_STEAM_GENERATION, failureHours);
        if (atomized) increment *= 2.0;
        if (increment <= 0.0) return;
        if (consumeWaterSoftenerDoseForCycle()) {
            increment = applyWaterSoftenerReduction(increment, getWaterSoftenerReductionPercent());
        }

        var update = BoilerRoomThermalLogic.addScale(
                waterScaleProgress, increment, SCALE_WARNING_THRESHOLD, SCALE_SCRAP_THRESHOLD);
        waterScaleProgress = update.progress();
        if (update.warningCrossed()) sendScaleWarning();
        if (update.scrapped()) scrapByWaterScale();
        markDirty();
    }

    private void sendScaleWarning() {
        if (!(getLevel() instanceof ServerLevel level)) return;
        Component message = Component.translatable(
                "gregsteamexpansion.machine.boiler_room.water_scale.warning",
                getBlockState().getBlock().getName(),
                level.dimension().location().toString(),
                getPos().getX(), getPos().getY(), getPos().getZ(),
                GSEDifficultyState.boilerRoomScaleLossPercent(false, 3)).withStyle(ChatFormatting.RED);
        BoilerScaleWarning.send(level, getOwnerUUID(), message);
    }

    private void scrapByWaterScale() {
        scrappedByScale = true;
        waterScaleProgress = SCALE_SCRAP_THRESHOLD;
        descalingTicksRemaining = 0;
        descalingTicksTotal = 0;
        cycleSteamGenerated = 0;
        getRecipeLogic().interruptRecipe();
        markDirty();
    }

    //////////////////////////////////////
    // ***** Display panel ******//
    //////////////////////////////////////

    @Override
    public ModularUI createUI(Player player) {
        // Keep the upstream scrolling display and throttle click handler.
        // The fixed side gauges use ProgressWidget's server-to-client updates;
        // client machines do not need the server-only powder buses/part list.
        var screen = new DraggableScrollableWidgetGroup(7, 4, 162, 121)
                .setBackground(getScreenTexture());
        screen.addWidget(new LabelWidget(4, 5, getBlockState().getBlock().getDescriptionId()));
        screen.addWidget(new ComponentPanelWidget(4, 17, this::addDisplayText)
                .textSupplier(isRemote() ? null : this::addDisplayText)
                .setMaxWidthLimit(150)
                .clickHandler(this::handleDisplayClick));
        boolean steel = getMaxTemperature() > MAX_TEMPERATURES[BRONZE_TIER];
        return new ModularUI(196, 216, this, player)
                .background(GuiTextures.BACKGROUND)
                .widget(screen)
                .widget(new ProgressWidget(() -> roomTemperature / (double) getMaxTemperature(), 176, 26, 10, 54)
                        .setProgressTexture(GuiTextures.PROGRESS_BAR_BOILER_EMPTY.get(steel),
                                GuiTextures.PROGRESS_BAR_BOILER_HEAT)
                        .setFillDirection(ProgressTexture.FillDirection.DOWN_TO_UP)
                        .setDynamicHoverTips(percent -> Component.translatable(
                                "gtceu.multiblock.large_boiler.temperature",
                                (int) Math.round(percent * getMaxTemperature()) + 274,
                                getMaxTemperature() + 274).getString()))
                .widget(new ProgressWidget(this::getPowderProgress, 172, 94, 18, 18)
                        .setProgressTexture(
                                GuiTextures.PROGRESS_BAR_BOILER_FUEL.get(steel).getSubTexture(0, 0, 1, 0.5),
                                GuiTextures.PROGRESS_BAR_BOILER_FUEL.get(steel).getSubTexture(0, 0.5, 1, 0.5))
                        .setFillDirection(ProgressTexture.FillDirection.DOWN_TO_UP)
                        .setDynamicHoverTips(percent -> Component.translatable(
                                "gregsteamexpansion.machine.boiler_room.powder_remaining",
                                (int) Math.round(percent * 100)).getString()))
                .widget(UITemplate.bindPlayerInventory(player.getInventory(), GuiTextures.SLOT, 17, 134, true));
    }

    @Override
    public void addDisplayText(@NotNull List<Component> textList) {
        if (!isFormed()) {
            // 复刻 IDisplayUIMachine 默认首行 (基类实现被父类覆盖为带 0 产出的
            // 温度面板, 这里改为仅提示结构未成型).
            textList.add(Component.translatable("gtceu.multiblock.invalid_structure"));
            // A missing mandatory intake prevents formation, so the placement
            // requirement must also be visible on the unformed screen.
            textList.add(Component.translatable("gregsteamexpansion.machine.boiler_room.status.no_air_intake")
                    .withStyle(ChatFormatting.GRAY));
            addWaterSoftenerDisplayText(textList);
            addModuleDisplayText(textList);
            return;
        }
        {
            textList.add(Component.translatable("gtceu.multiblock.large_boiler.temperature",
                    roomTemperature + 274, getMaxTemperature() + 274));
            textList.add(Component.translatable("gtceu.multiblock.large_boiler.steam_output",
                    cycleSteamGenerated / TICKS_PER_STEAM_GENERATION));

            if (scrappedByScale) {
                textList.add(Component.translatable(
                        "gregsteamexpansion.machine.boiler_room.water_scale.scrapped")
                        .withStyle(ChatFormatting.DARK_RED));
            } else if (isDescaling()) {
                textList.add(Component.translatable(
                        "gregsteamexpansion.machine.boiler_room.water_scale.descaling",
                        (int) Math.round(getDescalingProgress() * 100.0))
                        .withStyle(ChatFormatting.AQUA));
            } else if (GSEDifficultyConfig.boilerRoomWaterScaleEnabled()) {
                textList.add(Component.translatable(
                        "gregsteamexpansion.machine.boiler_room.water_scale.level",
                        getWaterScalePercent(), getWaterScaleLossPercent())
                        .withStyle(getWaterScaleStage() == BoilerScaleStage.STAGE_3
                                ? ChatFormatting.RED : ChatFormatting.GRAY));
                if (waterScaleProgress > 0.0) {
                    var descale = Component.translatable(
                            "gregsteamexpansion.machine.boiler_room.water_scale.start",
                            GSEDifficultyConfig.boilerRoomDescalingAcidMb());
                    textList.add(ComponentPanelWidget.withButton(descale, "descale"));
                }
            }

            addWaterSoftenerDisplayText(textList);

            var throttleText = Component.translatable("gtceu.multiblock.large_boiler.throttle",
                    ChatFormatting.AQUA.toString() + getThrottle() + "%")
                    .withStyle(Style.EMPTY.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                            Component.translatable("gtceu.multiblock.large_boiler.throttle.tooltip"))));
            textList.add(throttleText);

            var buttonText = Component.translatable("gtceu.multiblock.large_boiler.throttle_modify");
            buttonText.append(" ");
            buttonText.append(ComponentPanelWidget.withButton(Component.literal("[-]"), "sub"));
            buttonText.append(" ");
            buttonText.append(ComponentPanelWidget.withButton(Component.literal("[+]"), "add"));
            textList.add(buttonText);

            // 协同燃烧状态 (P2#10/P3#13): powder progress + air intake hint.
            if (scrappedByScale || isDescaling()) {
                // The dedicated water-scale line above is the authoritative state.
            } else if (!hasAirIntake()) {
                textList.add(Component.translatable(
                        "gregsteamexpansion.machine.boiler_room.status.no_air_intake")
                        .withStyle(ChatFormatting.RED));
            } else if (isAirStarved()) {
                textList.add(Component.translatable(
                        "gregsteamexpansion.machine.boiler_room.status.air_starved")
                        .withStyle(ChatFormatting.YELLOW));
            } else if (isMissingPowder()) {
                textList.add(Component.translatable(
                        "gregsteamexpansion.machine.boiler_room.status.missing_powder")
                        .withStyle(ChatFormatting.YELLOW));
            } else {
                textList.add(Component.translatable(
                        "gregsteamexpansion.machine.boiler_room.status.co_firing",
                        (int) Math.round(getPowderProgress() * 100))
                        .withStyle(ChatFormatting.GRAY));
            }
        }
    }

    // Parent handleDisplayClick keeps servicing the [±] throttle buttons;
    // no mode button exists (P1#5: co-firing only).

    @Override
    public void handleDisplayClick(String componentData, ClickData clickData) {
        switch (componentData) {
            case "descale" -> startDescaling();
            case "force_cool" -> startForceCooling();
            case "forced_draft" -> { forcedDraftEnabled = !forcedDraftEnabled; markDirty(); }
            case "atomizer" -> { atomizerEnabled = !atomizerEnabled; markDirty(); }
            case "condenser" -> { condenserEnabled = !condenserEnabled; markDirty(); }
            case "auto_wash" -> { automaticWashEnabled = !automaticWashEnabled; markDirty(); }
            case "auto_threshold" -> {
                automaticWashThreshold = automaticWashThreshold == 25 ? 50
                        : automaticWashThreshold == 50 ? 75 : 25;
                markDirty();
            }
            default -> super.handleDisplayClick(componentData, clickData);
        }
    }

    private void addWaterSoftenerDisplayText(List<Component> textList) {
        if (!isWaterScaleEffective()) {
            textList.add(Component.translatable(
                    "gregsteamexpansion.machine.boiler_room.water_softener.not_needed")
                    .withStyle(ChatFormatting.GRAY));
            return;
        }

        BoilerRoomModules.Status status = moduleStatus(BoilerRoomModules.WATER_SOFTENER);
        if (status != BoilerRoomModules.Status.VALID) {
            ChatFormatting color = switch (status) {
                case INVALID, CONFLICT -> ChatFormatting.RED;
                case UNLOADED -> ChatFormatting.YELLOW;
                default -> ChatFormatting.GRAY;
            };
            textList.add(Component.translatable(
                    "gregsteamexpansion.machine.boiler_room.water_softener." + status.key())
                    .withStyle(color));
            return;
        }

        long cycleCost = waterSoftenerCycleCost(getThrottle(), TICKS_PER_STEAM_GENERATION);
        if (waterSoftenerDoseUnits < cycleCost && isFormed() && !hasStickyResin()) {
            textList.add(Component.translatable(
                    "gregsteamexpansion.machine.boiler_room.water_softener.bypass")
                    .withStyle(ChatFormatting.YELLOW));
            return;
        }
        if (waterSoftenerDoseUnits < cycleCost) {
            textList.add(Component.translatable(
                    "gregsteamexpansion.machine.boiler_room.water_softener.ready_empty",
                    getWaterSoftenerReductionPercent()).withStyle(ChatFormatting.GRAY));
            return;
        }

        String key = waterSoftenerAppliedLastCycle
                ? "gregsteamexpansion.machine.boiler_room.water_softener.softening"
                : "gregsteamexpansion.machine.boiler_room.water_softener.ready";
        textList.add(Component.translatable(key, getWaterSoftenerReductionPercent(),
                formatWaterSoftenerTime(getWaterSoftenerRemainingTicksAtCurrentThrottle()))
                .withStyle(waterSoftenerAppliedLastCycle ? ChatFormatting.AQUA : ChatFormatting.GRAY));
    }

    private void addModuleDisplayText(List<Component> textList) {
        if (!GSEDifficultyConfig.externalModulesEnabled()) {
            textList.add(Component.translatable(
                    "gregsteamexpansion.machine.external_modules.disabled_by_config")
                    .withStyle(ChatFormatting.YELLOW));
            return;
        }
        for (BoilerRoomModules.Descriptor module : BoilerRoomModules.ALL) {
            if (module == BoilerRoomModules.WATER_SOFTENER) continue;
            BoilerRoomModules.Status status = moduleStatus(module);
            if (status == BoilerRoomModules.Status.MISSING) continue;
            ChatFormatting color = status == BoilerRoomModules.Status.VALID ? ChatFormatting.GRAY
                    : status == BoilerRoomModules.Status.UNLOADED ? ChatFormatting.YELLOW : ChatFormatting.RED;
            textList.add(Component.translatable("gregsteamexpansion.machine.boiler_room.module.status",
                    Component.translatable(module.translationKey()),
                    Component.translatable("gregsteamexpansion.machine.boiler_room.module.status." + status.key()))
                    .withStyle(color));
        }
        if (moduleValid(BoilerRoomModules.COOLING_TANK) && roomTemperature > SAFE_INTERNAL_TEMPERATURE
                && !isForceCooling()) {
            textList.add(ComponentPanelWidget.withButton(Component.translatable(
                    "gregsteamexpansion.machine.boiler_room.module.force_cooling.start",
                    (roomTemperature - SAFE_INTERNAL_TEMPERATURE) * 25), "force_cool"));
        }
        addToggle(textList, BoilerRoomModules.DRAFT_ROOM, forcedDraftEnabled, "forced_draft");
        addToggle(textList, BoilerRoomModules.ATOMIZATION_ROOM, atomizerEnabled, "atomizer");
        addToggle(textList, BoilerRoomModules.CONDENSER_TOWER, condenserEnabled, "condenser");
        if (moduleValid(BoilerRoomModules.AUTO_WASH_STATION)) {
            addToggle(textList, BoilerRoomModules.AUTO_WASH_STATION, automaticWashEnabled, "auto_wash");
            textList.add(ComponentPanelWidget.withButton(Component.translatable(
                    "gregsteamexpansion.machine.boiler_room.module.auto_acid.threshold",
                    automaticWashThreshold), "auto_threshold"));
        }
        if (moduleValid(BoilerRoomModules.BUFFER_TANK)) {
            textList.add(Component.translatable("gregsteamexpansion.machine.boiler_room.module.steam_buffer.amount",
                    steamBufferAmount, steamBufferCapacity()).withStyle(ChatFormatting.GRAY));
        }
    }

    private void addToggle(List<Component> textList, BoilerRoomModules.Descriptor module,
                           boolean enabled, String action) {
        if (!moduleValid(module)) return;
        textList.add(ComponentPanelWidget.withButton(Component.translatable(
                "gregsteamexpansion.machine.boiler_room.module.toggle",
                Component.translatable(module.translationKey()),
                Component.translatable(enabled
                        ? "gregsteamexpansion.machine.boiler_room.module.enabled"
                        : "gregsteamexpansion.machine.boiler_room.module.disabled")), action));
    }

    private static String formatWaterSoftenerTime(long ticks) {
        long seconds = Math.max(0, ticks) / 20;
        long hours = seconds / 3_600;
        long minutes = seconds % 3_600 / 60;
        long remainingSeconds = seconds % 60;
        return hours > 0
                ? String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, remainingSeconds)
                : String.format(Locale.ROOT, "%d:%02d", minutes, remainingSeconds);
    }

    @Override
    public IGuiTexture getScreenTexture() {
        return GuiTextures.DISPLAY_STEAM.get(getMaxTemperature() > 800);
    }

    //////////////////////////////////////
    // ***** Recipe logic ******//
    //////////////////////////////////////

    public static class BoilerRoomLogic extends LargeBoilerRecipeLogic {

        private final BoilerRoomMachine room;

        public BoilerRoomLogic(com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine machine) {
            super(machine);
            this.room = (BoilerRoomMachine) machine;
        }

        @Override
        public void handleRecipeWorking() {
            if (room.scrappedByScale) {
                setWaiting(Component.translatable("gregsteamexpansion.machine.boiler_room.water_scale.scrapped"));
                return;
            }
            if (room.hasFrozenModuleTransaction()) {
                setWaiting(Component.translatable(
                        "gregsteamexpansion.machine.external_modules.disabled_by_config"));
                return;
            }
            if (room.isDescaling()) {
                setWaiting(Component.translatable("gregsteamexpansion.machine.boiler_room.water_scale.descaling",
                        (int) Math.round(room.getDescalingProgress() * 100.0)));
                return;
            }
            if (room.isForceCooling()) {
                setWaiting(Component.translatable(
                        "gregsteamexpansion.machine.boiler_room.module.force_cooling.running"));
                return;
            }
            if (room.automaticWashPhase == AUTO_COOLING) {
                setWaiting(Component.translatable(
                        "gregsteamexpansion.machine.boiler_room.module.auto_acid.waiting"));
                return;
            }
            if (room.isCoFiringPaused()) {
                if (room.atomizerBatchLocked && room.isAirStarved()) {
                    interruptRecipe();
                    return;
                }
                setWaiting(Component.translatable(room.isMissingPowder()
                        ? "gregsteamexpansion.machine.boiler_room.status.missing_powder"
                        : "gregsteamexpansion.machine.boiler_room.status.air_starved"));
                return;
            }
            super.handleRecipeWorking();
        }
    }

    @Override
    public boolean saveBreak() {
        return !scrappedByScale;
    }

    @Override
    public void saveToItem(CompoundTag tag) {
        IDropSaveMachine.super.saveToItem(tag);
        if (waterScaleProgress > 0.0) {
            tag.putDouble(ITEM_SCALE_KEY, getWaterScaleProgress());
        }
    }

    @Override
    public void loadFromItem(CompoundTag tag) {
        IDropSaveMachine.super.loadFromItem(tag);
        if (tag.contains(ITEM_SCALE_KEY, Tag.TAG_DOUBLE)) {
            waterScaleProgress = Math.max(0.0, Math.min(0.999_999, tag.getDouble(ITEM_SCALE_KEY)));
        }
        scrappedByScale = false;
        descalingTicksRemaining = 0;
        descalingTicksTotal = 0;
        waterSoftenerDoseUnits = 0;
        waterSoftenerAppliedLastCycle = false;
        forcedDraftEnabled = false;
        atomizerEnabled = false;
        atomizerBatchLocked = false;
        condenserEnabled = false;
        automaticWashEnabled = false;
        automaticWashThreshold = 75;
        automaticWashPhase = AUTO_IDLE;
        automaticWashResumeEnabled = false;
        forceCoolingTicksRemaining = 0;
        forceCoolingStartTemperature = 0;
        steamBufferAmount = 0;
        descalingMode = DESCALING_COLD;
        descalingAcidConsumed = 0;
        acidRecoveryEligible = false;
    }

    @Override
    public void onDrops(List<ItemStack> drops) {
        if (scrappedByScale) drops.clear();
    }

    @Override
    public void onMachineRemoved() {
        if (getLevel() instanceof ServerLevel level) {
            BoilerRoomModuleWorldData.getOrCreate(level).releaseAll(getPos());
        }
        burningPowder = ItemStack.EMPTY;
        powderBurnRemaining = 0;
        powderBurnTotal = 0;
        powderConsumptionTenths = 0;
        cycleSteamGenerated = 0;
        descalingTicksRemaining = 0;
        descalingTicksTotal = 0;
        waterSoftenerDoseUnits = 0;
        waterSoftenerAppliedLastCycle = false;
        atomizerBatchLocked = false;
        automaticWashPhase = AUTO_IDLE;
        automaticWashResumeEnabled = false;
        forceCoolingTicksRemaining = 0;
        forceCoolingStartTemperature = 0;
        steamBufferAmount = 0;
        descalingMode = DESCALING_COLD;
        descalingAcidConsumed = 0;
        acidRecoveryEligible = false;
        modules.clear();
        airIntakes.clear();
    }
}
