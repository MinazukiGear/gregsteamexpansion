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
import com.gregtechceu.gtceu.common.machine.multiblock.steam.LargeBoilerMachine;
import com.gregtechceu.gtceu.config.ConfigHolder;
import com.hoshino.gregsteamexpansion.difficulty.Difficulty;
import com.hoshino.gregsteamexpansion.difficulty.GSEDifficultyProfile;
import com.hoshino.gregsteamexpansion.difficulty.GSEDifficultyConfig;
import com.hoshino.gregsteamexpansion.difficulty.GSEDifficultyState;
import com.hoshino.gregsteamexpansion.machine.CoFiringPowderFuel;
import com.hoshino.gregsteamexpansion.machine.multiblock.part.SteamAirIntakeHatchPartMachine;

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
import java.util.List;

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
public class BoilerRoomMachine extends LargeBoilerMachine implements IMachineLife, IDropSaveMachine, IMachineModifyDrops {

    protected static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(
            BoilerRoomMachine.class, LargeBoilerMachine.MANAGED_FIELD_HOLDER);

    /** 协同燃烧 +50% 产出乘子 (P1#4). */
    public static final double CO_FIRING_MULTIPLIER = 1.5;
    /** 粉料消耗速率: 十分之一 tick / tick (沿用单方块高压口径, P1#8). */
    private static final int TENTHS_PER_POWDER_BURN_TICK = 6;
    public static final double SCALE_STAGE_SIZE = 0.25;
    private static final double SCALE_WARNING_THRESHOLD = 0.75;
    private static final double SCALE_SCRAP_THRESHOLD = 1.0;
    private static final double TICKS_PER_HOUR = 72_000.0;
    private static final String ITEM_SCALE_KEY = "GSEBoilerWaterScale";

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

    //////////////////////////////////////
    // ***** Tier configuration ******//
    //////////////////////////////////////

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

    //////////////////////////////////////
    // ***** Runtime state ******//
    //////////////////////////////////////

    @Nullable
    private TickableSubscription roomTemperatureSubs;
    private int heatCounter;
    private int coolCounter;
    /** Collected on formation: the roof-strip air intakes. */
    private final List<SteamAirIntakeHatchPartMachine> airIntakes = new ArrayList<>();

    /**
     * @param tierIndex 0..3 = bronze / steel / titanium / tungstensteel
     *                  (P1#6 numbers; maxTemperature via the parent ctor).
     */
    public BoilerRoomMachine(IMachineBlockEntity holder, int tierIndex) {
        super(holder, MAX_TEMPERATURES[tierIndex], 1);
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

    /** Exact per-tick output before the five-tick water conversion batch. */
    public static long calculateSteamOutputPerTick(int temperature, int throttle, Difficulty difficulty) {
        return calculateSteamOutputPerTick(temperature, throttle,
                (float) GSEDifficultyProfile.defaults(difficulty).boilerRoomSteamOutputMultiplier());
    }

    /** Same formula with an explicit multiplier, allowing disabled difficulty to use neutral x1. */
    public static long calculateSteamOutputPerTick(int temperature, int throttle, float difficultyMultiplier) {
        return Math.round(temperature * (double) throttle / 20.0
                * CO_FIRING_MULTIPLIER * difficultyMultiplier);
    }

    public static long applyWaterScaleLoss(long cleanOutput, int lossPercent) {
        return Math.round(cleanOutput * Math.max(0, 100 - lossPercent) / 100.0);
    }

    /** Converts a production cycle into equivalent-full-load lifetime progress. */
    public static double calculateWaterScaleIncrement(long cleanOutput, long maximumCleanOutput,
                                                       int cycleTicks, double failureHours) {
        if (cleanOutput <= 0 || maximumCleanOutput <= 0 || cycleTicks <= 0 || failureHours <= 0.0) {
            return 0.0;
        }
        double load = Math.min(1.0, cleanOutput / (double) maximumCleanOutput);
        return cycleTicks * load / (failureHours * TICKS_PER_HOUR);
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
        int remaining = airConsumptionPerTick;
        for (var intake : airIntakes) {
            remaining -= intake.tank.drainInternal(GTMaterials.Air.getFluid(remaining), FluidAction.SIMULATE)
                    .getAmount();
            if (remaining == 0) return false;
        }
        return true;
    }

    /** Simulate the whole demand first so shortage never partially drains the intakes. */
    private boolean consumeAir() {
        if (isAirStarved()) return false;
        int remaining = airConsumptionPerTick;
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
        return scrappedByScale || isDescaling() || isMissingPowder() || isAirStarved();
    }

    /** Starts one fixed-cost cycle which removes at most one 25-point scale band. */
    public boolean startDescaling() {
        if (isRemote() || !GSEDifficultyConfig.boilerRoomWaterScaleEnabled() || !isFormed()
                || scrappedByScale || isDescaling() || waterScaleProgress <= 0.0
                || roomTemperature >= 100 || getRecipeLogic().isWorking()
                || isStructureTemporarilyUnavailable()) {
            return false;
        }
        int acid = GSEDifficultyConfig.boilerRoomDescalingAcidMb();
        if (!drainDescalingAcid(acid, true) || !drainDescalingAcid(acid, false)) return false;
        descalingTicksTotal = GSEDifficultyConfig.boilerRoomDescalingDurationTicks();
        descalingTicksRemaining = descalingTicksTotal;
        cycleSteamGenerated = 0;
        updateRoomTemperatureSubscription();
        markDirty();
        return true;
    }

    @SuppressWarnings("unchecked")
    private boolean drainDescalingAcid(int amount, boolean simulate) {
        var remaining = List.of(FluidIngredient.of(GTMaterials.DilutedHydrochloricAcid.getFluid(amount)));
        List<IRecipeHandler<?>> inputTanks = new ArrayList<>();
        inputTanks.addAll(getCapabilitiesFlat(IO.IN, FluidRecipeCapability.CAP));
        inputTanks.addAll(getCapabilitiesFlat(IO.BOTH, FluidRecipeCapability.CAP));
        for (IRecipeHandler<?> tank : inputTanks) {
            remaining = (List<FluidIngredient>) tank.handleRecipe(IO.IN, null, remaining, simulate);
            if (remaining == null || remaining.isEmpty()) return true;
        }
        return false;
    }

    @Override
    public boolean beforeWorking(@Nullable GTRecipe recipe) {
        if (scrappedByScale || isDescaling()) return false;
        // 开工预检 (P1#8/P2#10): powder buffer refill + intake presence, else waiting.
        return preparePowder(recipe) && hasAirIntake() && super.beforeWorking(recipe);
    }

    @Override
    public boolean onWorking() {
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
    public void onUnload() {
        if (roomTemperatureSubs != null) {
            roomTemperatureSubs.unsubscribe();
            roomTemperatureSubs = null;
        }
        super.onUnload();
    }

    @Override
    public void onLoad() {
        super.onLoad();
        // 区块重载后按持久化温度恢复产汽订阅 (parent keys on its own field).
        if (getLevel() instanceof ServerLevel serverLevel) {
            serverLevel.getServer().tell(new TickTask(0, this::updateRoomTemperatureSubscription));
        }
    }

    protected void updateRoomTemperatureSubscription() {
        if (roomTemperature > 0 || isDescaling()) {
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

        if (isDescaling()) {
            cycleSteamGenerated = 0;
            if (roomTemperature > 0 && ++coolCounter >= cooldownIntervalTicks) {
                coolCounter = 0;
                roomTemperature--;
            }
            descalingTicksRemaining--;
            if (descalingTicksRemaining <= 0) {
                descalingTicksRemaining = 0;
                descalingTicksTotal = 0;
                waterScaleProgress = Math.max(0.0, waterScaleProgress - SCALE_STAGE_SIZE);
                markDirty();
            } else if (descalingTicksRemaining % 20 == 0) {
                markDirty();
            }
            updateRoomTemperatureSubscription();
            return;
        }

        if (scrappedByScale) {
            cycleSteamGenerated = 0;
            if (roomTemperature > 0 && ++coolCounter >= cooldownIntervalTicks) {
                coolCounter = 0;
                roomTemperature--;
            }
            updateRoomTemperatureSubscription();
            return;
        }

        if (recipeLogic.isWorking()) {
            if (consumeAir() && ++heatCounter >= heatIntervalTicks) {
                heatCounter = 0;
                if (roomTemperature < getMaxTemperature()) {
                    roomTemperature++;
                }
            }
        } else {
            int interval = isMissingPowder() ? noPowderCooldownIntervalTicks : cooldownIntervalTicks;
            if (++coolCounter >= interval) {
                coolCounter = 0;
                if (roomTemperature > 0) {
                    roomTemperature--;
                }
            }
        }

        if (isFormed() && getOffsetTimer() % TICKS_PER_STEAM_GENERATION == 0) {
            generateSteamCycle();
        }
        updateRoomTemperatureSubscription();
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
        if (roomTemperature < 100) {
            cycleSteamGenerated = 0;
            return;
        }
        long cleanSteamPerTick = calculateSteamOutputPerTick(
                roomTemperature, getThrottle(), GSEDifficultyState.boilerRoomSteamOutputMultiplier(isRemote()));
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
        if (steamProduced > 0) {
            var fillSteam = List.of(FluidIngredient.of(
                    GTMaterials.Steam.getFluid((int) Math.min(steamProduced, Integer.MAX_VALUE))));
            List<IRecipeHandler<?>> outputTanks = new ArrayList<>();
            outputTanks.addAll(getCapabilitiesFlat(IO.OUT, FluidRecipeCapability.CAP));
            outputTanks.addAll(getCapabilitiesFlat(IO.BOTH, FluidRecipeCapability.CAP));
            for (IRecipeHandler<?> tank : outputTanks) {
                fillSteam = (List<FluidIngredient>) tank.handleRecipe(IO.OUT, null, fillSteam, false);
                if (fillSteam == null) break;
            }
        }

        // 干烧爆炸 (P1#7): inherited strength-2 explosion, no double-blast rule.
        if (drained < waterNeeded) {
            doExplosion(2f);
        } else if (steamProduced > 0) {
            accumulateWaterScale(cleanSteamPerTick);
        }
    }

    private void accumulateWaterScale(long cleanSteamPerTick) {
        if (!isWaterScaleEffective() || scrappedByScale || isDescaling() || cleanSteamPerTick <= 0) return;
        double failureHours = GSEDifficultyState.boilerRoomScaleFailureHours(isRemote());
        long maximumCleanOutput = calculateSteamOutputPerTick(getMaxTemperature(), 100,
                GSEDifficultyState.boilerRoomSteamOutputMultiplier(isRemote()));
        double increment = calculateWaterScaleIncrement(cleanSteamPerTick, maximumCleanOutput,
                TICKS_PER_STEAM_GENERATION, failureHours);
        if (increment <= 0.0) return;

        double previous = waterScaleProgress;
        waterScaleProgress = Math.min(SCALE_SCRAP_THRESHOLD,
                waterScaleProgress + increment);
        if (previous < SCALE_WARNING_THRESHOLD && waterScaleProgress >= SCALE_WARNING_THRESHOLD) {
            sendScaleWarning();
        }
        if (waterScaleProgress >= SCALE_SCRAP_THRESHOLD) {
            scrapByWaterScale();
        }
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
        if ("descale".equals(componentData)) {
            startDescaling();
        } else {
            super.handleDisplayClick(componentData, clickData);
        }
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
            if (room.isDescaling()) {
                setWaiting(Component.translatable("gregsteamexpansion.machine.boiler_room.water_scale.descaling",
                        (int) Math.round(room.getDescalingProgress() * 100.0)));
                return;
            }
            if (room.isCoFiringPaused()) {
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
    }

    @Override
    public void onDrops(List<ItemStack> drops) {
        if (scrappedByScale) drops.clear();
    }

    @Override
    public void onMachineRemoved() {
        burningPowder = ItemStack.EMPTY;
        powderBurnRemaining = 0;
        powderBurnTotal = 0;
        powderConsumptionTenths = 0;
        cycleSteamGenerated = 0;
        descalingTicksRemaining = 0;
        descalingTicksTotal = 0;
        airIntakes.clear();
    }
}
