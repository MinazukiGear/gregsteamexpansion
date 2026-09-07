package com.hoshino.gregsteamexpansion.machine.multiblock;

import com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.capability.recipe.IRecipeHandler;
import com.gregtechceu.gtceu.api.data.chemical.ChemicalHelper;
import com.gregtechceu.gtceu.api.data.chemical.material.Material;
import com.gregtechceu.gtceu.api.data.tag.TagPrefix;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.api.machine.feature.IMachineLife;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IDisplayUIMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.ingredient.FluidIngredient;
import com.gregtechceu.gtceu.common.data.GTMaterials;
import com.gregtechceu.gtceu.common.machine.multiblock.part.ItemBusPartMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.steam.LargeBoilerMachine;
import com.gregtechceu.gtceu.config.ConfigHolder;
import com.gregtechceu.gtceu.utils.GTUtil;
import com.hoshino.gregsteamexpansion.difficulty.Difficulty;
import com.hoshino.gregsteamexpansion.difficulty.GSEDifficultyState;
import com.hoshino.gregsteamexpansion.machine.multiblock.part.SteamAirIntakeHatchPartMachine;
import com.hoshino.gregsteamexpansion.registry.GSETags;

import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.widget.ComponentPanelWidget;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;

import net.minecraft.ChatFormatting;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
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
 * P2#12: Easy ×2, Normal/Expert ×1); the tiers differ only in
 * {@code maxTemperature} (800/1800/3200/6400) and the heating/cooling cadence
 * (P1#6), where missing-powder cooling must always beat both shutdown cooling
 * and heating (hard constraint).
 *
 * <p>The parent's own temperature field is left at zero on purpose: the
 * per-tier cadence (integer tick intervals) cannot be expressed through the
 * parent's integer {@code heatSpeed}, so this class keeps its own persisted
 * {@link #roomTemperature} and overrides every parent entry point that reads
 * the parent's field. The steam air intake hatch is the hard co-firing
 * prerequisite (P2#10): without it the room never runs, and each tier
 * continuously draws its own air amount (50/100/200/400 mB/t). No steam
 * input/output hatches of any kind and no steam item bus are admissible
 * (P2#9 hard constraint) — fluids move through GTCEu standard hatches,
 * venting through the muffler.</p>
 */
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class BoilerRoomMachine extends LargeBoilerMachine implements IMachineLife {

    protected static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(
            BoilerRoomMachine.class, LargeBoilerMachine.MANAGED_FIELD_HOLDER);

    /** 协同燃烧 +50% 产出乘子 (P1#4). */
    public static final double CO_FIRING_MULTIPLIER = 1.5;
    /** 粉料消耗速率: 十分之一 tick / tick (沿用单方块高压口径, P1#8). */
    private static final int TENTHS_PER_POWDER_BURN_TICK = 6;

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

    //////////////////////////////////////
    // ***** Runtime state ******//
    //////////////////////////////////////

    @Nullable
    private TickableSubscription roomTemperatureSubs;
    private int heatCounter;
    private int coolCounter;
    /** Collected on formation: the single air intake hatch and the powder buses. */
    private final List<SteamAirIntakeHatchPartMachine> airIntakes = new ArrayList<>();
    private final List<ItemBusPartMachine> powderBuses = new ArrayList<>();

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
        powderBuses.clear();
    }

    private void collectBoilerParts() {
        airIntakes.clear();
        powderBuses.clear();
        for (IMultiPart part : getParts()) {
            if (part instanceof SteamAirIntakeHatchPartMachine intake) {
                airIntakes.add(intake);
            } else if (part instanceof ItemBusPartMachine bus
                    && bus.getInventory().getHandlerIO() != IO.OUT) {
                powderBuses.add(bus);
            }
        }
    }

    //////////////////////////////////////
    // ***** Co-firing powder ******//
    //////////////////////////////////////

    private static boolean isValidPowder(ItemStack stack) {
        return !stack.isEmpty() && stack.is(GSETags.CO_FIRING_DUST_FUELS);
    }

    /** Extracts one powder item across the input buses; empty when none available. */
    private ItemStack extractOnePowder() {
        for (ItemBusPartMachine bus : powderBuses) {
            var inventory = bus.getInventory();
            for (int slot = 0; slot < inventory.getSlots(); slot++) {
                ItemStack candidate = inventory.getStackInSlot(slot);
                if (isValidPowder(candidate)) {
                    ItemStack extracted = inventory.extractItemInternal(slot, 1, false);
                    if (!extracted.isEmpty()) {
                        return extracted;
                    }
                }
            }
        }
        return ItemStack.EMPTY;
    }

    private static int getPowderBurnTime(ItemStack stack) {
        Material material = ChemicalHelper.getMaterialStack(stack).material();
        int base = material == GTMaterials.Coal || material == GTMaterials.Charcoal ? 1600 :
                material == GTMaterials.Coke ? 3200 : material == GTMaterials.Wood ? 300 :
                        GTUtil.getItemBurnTime(stack.getItem());
        if (base <= 0) base = 1600;
        TagPrefix prefix = ChemicalHelper.getPrefix(stack.getItem());
        if (prefix == TagPrefix.dustSmall) return Math.max(1, base / 4);
        if (prefix == TagPrefix.dustTiny) return Math.max(1, base / 9);
        return base;
    }

    /** Refills the powder heat buffer from the buses; false when nothing to burn. */
    private boolean preparePowder() {
        if (powderBurnRemaining > 0) return true;
        ItemStack powder = extractOnePowder();
        if (powder.isEmpty()) return false;
        int burnTime = getPowderBurnTime(powder);
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
        for (ItemBusPartMachine bus : powderBuses) {
            var inventory = bus.getInventory();
            for (int slot = 0; slot < inventory.getSlots(); slot++) {
                if (isValidPowder(inventory.getStackInSlot(slot))) {
                    return true;
                }
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

    /** True when the intake cannot supply this tier's per-tick air amount. */
    public boolean isAirStarved() {
        if (!hasAirIntake()) return true;
        return airIntakes.get(0).tank.getFluidInTank(0).getAmount() < airConsumptionPerTick;
    }

    /** Drains this tick's air; false when the intake is missing or dry. */
    private boolean consumeAir() {
        if (airIntakes.isEmpty()) return false;
        return !airIntakes.get(0).tank.drainInternal(
                GTMaterials.Air.getFluid(airConsumptionPerTick), FluidAction.EXECUTE).isEmpty();
    }

    //////////////////////////////////////
    // ***** Temperature & steam ******//
    //////////////////////////////////////

    /** True while the fuel recipe is paused for missing powder or air. */
    public boolean isCoFiringPaused() {
        return isMissingPowder() || isAirStarved();
    }

    @Override
    public boolean beforeWorking(@Nullable GTRecipe recipe) {
        // 开工预检 (P1#8/P2#10): powder buffer refill + intake presence, else waiting.
        return preparePowder() && hasAirIntake() && super.beforeWorking(recipe);
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
        if (roomTemperature > 0) {
            roomTemperatureSubs = subscribeServerTick(roomTemperatureSubs, this::updateRoomTemperature);
        } else if (roomTemperatureSubs != null) {
            roomTemperatureSubs.unsubscribe();
            roomTemperatureSubs = null;
        }
    }

    /**
     * P1#6 cadence with the hard constraint: no-powder cooling beats both
     * shutdown cooling and heating on every tier. Steam generation runs on
     * the parent's 5-tick cycle with the P1#4 co-firing formula.
     */
    protected void updateRoomTemperature() {
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
        double difficultyMultiplier = GSEDifficultyState.current(isRemote()) == Difficulty.EASY ? 2.0 : 1.0;
        long steamPerTick = Math.round(roomTemperature * (double) getThrottle() / 20.0
                * CO_FIRING_MULTIPLIER * difficultyMultiplier);
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

        long steamProduced = drained * steamPerWater;
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
        }
    }

    //////////////////////////////////////
    // ***** Display panel ******//
    //////////////////////////////////////

    @Override
    public void addDisplayText(@NotNull List<Component> textList) {
        if (!isFormed()) {
            // 复刻 IDisplayUIMachine 默认首行 (基类实现被父类覆盖为带 0 产出的
            // 温度面板, 这里改为仅提示结构未成型).
            textList.add(Component.translatable("gtceu.multiblock.invalid_structure"));
            return;
        }
        {
            textList.add(Component.translatable("gtceu.multiblock.large_boiler.temperature",
                    roomTemperature + 274, getMaxTemperature() + 274));
            textList.add(Component.translatable("gtceu.multiblock.large_boiler.steam_output",
                    cycleSteamGenerated / TICKS_PER_STEAM_GENERATION));

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
            if (!hasAirIntake()) {
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
    public void onMachineRemoved() {
        burningPowder = ItemStack.EMPTY;
        powderBurnRemaining = 0;
        powderBurnTotal = 0;
        powderConsumptionTenths = 0;
        cycleSteamGenerated = 0;
        airIntakes.clear();
        powderBuses.clear();
    }
}
