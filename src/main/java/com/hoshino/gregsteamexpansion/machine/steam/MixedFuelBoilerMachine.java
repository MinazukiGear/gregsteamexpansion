package com.hoshino.gregsteamexpansion.machine.steam;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.data.chemical.ChemicalHelper;
import com.gregtechceu.gtceu.api.data.chemical.material.Material;
import com.gregtechceu.gtceu.api.data.tag.TagPrefix;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.UITemplate;
import com.gregtechceu.gtceu.api.gui.widget.SlotWidget;
import com.gregtechceu.gtceu.api.gui.widget.TankWidget;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.api.machine.feature.IDataInfoProvider;
import com.gregtechceu.gtceu.api.machine.feature.IExplosionMachine;
import com.gregtechceu.gtceu.api.machine.feature.IInteractedMachine;
import com.gregtechceu.gtceu.api.machine.feature.IUIMachine;
import com.gregtechceu.gtceu.api.machine.steam.SteamWorkableMachine;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableFluidTank;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableItemStackHandler;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.modifier.ModifierFunction;
import com.gregtechceu.gtceu.api.recipe.modifier.RecipeModifier;
import com.gregtechceu.gtceu.common.data.GTMaterials;
import com.gregtechceu.gtceu.common.item.PortableScannerBehavior;
import com.gregtechceu.gtceu.config.ConfigHolder;
import com.gregtechceu.gtceu.utils.FormattingUtil;
import com.gregtechceu.gtceu.utils.GTTransferUtils;
import com.gregtechceu.gtceu.utils.GTUtil;
import com.hoshino.gregsteamexpansion.registry.GSETags;

import com.lowdragmc.lowdraglib.gui.editor.ColorPattern;
import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import com.lowdragmc.lowdraglib.gui.texture.GuiTextureGroup;
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.texture.ProgressTexture;
import com.lowdragmc.lowdraglib.gui.texture.ResourceBorderTexture;
import com.lowdragmc.lowdraglib.gui.texture.ResourceTexture;
import com.lowdragmc.lowdraglib.gui.widget.ButtonWidget;
import com.lowdragmc.lowdraglib.gui.widget.ImageWidget;
import com.lowdragmc.lowdraglib.gui.widget.LabelWidget;
import com.lowdragmc.lowdraglib.gui.widget.ProgressWidget;
import com.lowdragmc.lowdraglib.syncdata.ISubscription;
import com.lowdragmc.lowdraglib.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import it.unimi.dsi.fastutil.objects.Object2BooleanMap;
import it.unimi.dsi.fastutil.objects.Object2BooleanOpenHashMap;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public final class MixedFuelBoilerMachine extends SteamWorkableMachine
                                          implements IUIMachine, IExplosionMachine, IDataInfoProvider,
                                          IInteractedMachine {
    private static final int LIQUID_OUTPUT_LP = 320;
    private static final int LIQUID_OUTPUT_HP = 800;
    private static final int CO_FIRING_OUTPUT_LP = 480;
    private static final int CO_FIRING_OUTPUT_HP = 1200;
    private static final int TENTHS_PER_POWDER_BURN_TICK_LP = 3;
    private static final int TENTHS_PER_POWDER_BURN_TICK_HP = 6;
    private static final Object2BooleanMap<Fluid> FUEL_CACHE = new Object2BooleanOpenHashMap<>();
    private static final String ICON_ROOT =
            "gregsteamexpansion:textures/gui/icon/mixed_fuel_boiler/";
    private static final ResourceTexture MODE_LIQUID_ICON = icon("mode_liquid");
    private static final ResourceTexture MODE_CO_FIRING_ICON = icon("mode_co_firing");
    private static final ResourceTexture STATUS_DRY_BOILER_ICON = icon("status_dry_boiler");
    private static final ResourceTexture STATUS_MISSING_WATER_ICON = icon("status_missing_water");
    private static final ResourceTexture STATUS_MISSING_LIQUID_FUEL_ICON = icon("status_missing_liquid_fuel");
    private static final ResourceTexture STATUS_MISSING_CO_FIRING_FUEL_ICON =
            icon("status_missing_co_firing_fuel");
    private static final ResourceTexture STATUS_STEAM_OUTPUT_BLOCKED_ICON = icon("status_steam_output_blocked");

    protected static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(
            MixedFuelBoilerMachine.class, SteamWorkableMachine.MANAGED_FIELD_HOLDER);

    @Persisted
    public final NotifiableFluidTank waterTank;
    @Persisted
    public final NotifiableFluidTank fuelTank;
    @Persisted
    public final NotifiableItemStackHandler powderHandler;

    @Persisted
    @DescSynced
    private boolean coFiring;
    @Persisted
    @DescSynced
    private int currentTemperature;
    @Persisted
    private int timeBeforeCoolingDown;
    @Persisted
    @DescSynced
    private boolean hasNoWater;
    @Persisted
    @DescSynced
    private int powderBurnRemaining;
    @Persisted
    @DescSynced
    private int powderBurnTotal;
    @Persisted
    @DescSynced
    private ItemStack burningPowder = ItemStack.EMPTY;
    @Persisted
    private int powderConsumptionTenths;
    @Persisted
    private int ventFeedbackCooldown;
    @DescSynced
    private int ventingTicks;

    @Nullable
    private TickableSubscription temperatureSubs;
    @Nullable
    private TickableSubscription autoOutputSubs;
    @Nullable
    private ISubscription steamTankSubs;
    private long nextCrackleTick;

    public MixedFuelBoilerMachine(IMachineBlockEntity holder, boolean isHighPressure, Object... args) {
        super(holder, isHighPressure, args);
        waterTank = new NotifiableFluidTank(this, 1, 16 * FluidType.BUCKET_VOLUME, IO.IN)
                .setFilter(fluid -> fluid.getFluid().is(GTMaterials.Water.getFluidTag()));
        fuelTank = createFuelTank();
        // Powder is an externally accessible co-firing input, not a main boiler recipe input.
        // Keeping handlerIO at NONE prevents solid steam-boiler recipes from using dust by itself.
        powderHandler = new NotifiableItemStackHandler(this, 1, IO.NONE, IO.IN)
                .setFilter(this::isValidPowder);
    }

    @Override
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }

    @Override
    protected RecipeLogic createRecipeLogic(Object... args) {
        return new MixedFuelRecipeLogic(this);
    }

    @Override
    protected NotifiableFluidTank createSteamTank(Object... args) {
        return new NotifiableFluidTank(this, 1, 16 * FluidType.BUCKET_VOLUME, IO.OUT);
    }

    private NotifiableFluidTank createFuelTank() {
        return new NotifiableFluidTank(this, 1, 16 * FluidType.BUCKET_VOLUME, IO.IN)
                .setFilter(stack -> FUEL_CACHE.computeIfAbsent(stack.getFluid(), fluid -> {
                    if (isRemote()) return true;
                    return recipeLogic.getRecipeManager().getAllRecipesFor(getRecipeType()).stream().anyMatch(recipe -> {
                        var inputs = recipe.inputs.getOrDefault(FluidRecipeCapability.CAP, Collections.emptyList());
                        if (inputs.isEmpty()) return false;
                        return Arrays.stream(FluidRecipeCapability.CAP.of(inputs.get(0).content).getStacks())
                                .anyMatch(candidate -> candidate.getFluid() == fluid);
                    });
                }));
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (getLevel() instanceof ServerLevel serverLevel) {
            serverLevel.getServer().tell(new TickTask(0, this::updateAutoOutputSubscription));
        }
        updateTemperatureSubscription();
        steamTankSubs = steamTank.addChangedListener(this::updateAutoOutputSubscription);
    }

    @Override
    public void onUnload() {
        super.onUnload();
        if (steamTankSubs != null) {
            steamTankSubs.unsubscribe();
            steamTankSubs = null;
        }
    }

    @Override
    public boolean hasOutputFacing() {
        return false;
    }

    @Override
    public boolean keepSubscribing() {
        return false;
    }

    @Override
    public boolean regressWhenWaiting() {
        return false;
    }

    @Override
    public void onNeighborChanged(Block block, BlockPos fromPos, boolean isMoving) {
        super.onNeighborChanged(block, fromPos, isMoving);
        updateAutoOutputSubscription();
    }

    private void updateAutoOutputSubscription() {
        if (Direction.stream().filter(direction -> direction != getFrontFacing() && direction != Direction.DOWN)
                .anyMatch(direction -> GTTransferUtils.hasAdjacentFluidHandler(getLevel(), getPos(), direction))) {
            autoOutputSubs = subscribeServerTick(autoOutputSubs, this::autoOutput);
        } else if (autoOutputSubs != null) {
            autoOutputSubs.unsubscribe();
            autoOutputSubs = null;
        }
    }

    private void autoOutput() {
        if (getOffsetTimer() % 5 == 0) {
            steamTank.exportToNearby(Direction.stream()
                    .filter(direction -> direction != getFrontFacing() && direction != Direction.DOWN)
                    .filter(direction -> GTTransferUtils.hasAdjacentFluidHandler(getLevel(), getPos(), direction))
                    .toArray(Direction[]::new));
            updateAutoOutputSubscription();
        }
    }

    private void updateTemperatureSubscription() {
        if (currentTemperature > 0) {
            temperatureSubs = subscribeServerTick(temperatureSubs, this::updateCurrentTemperature);
        } else if (temperatureSubs != null) {
            temperatureSubs.unsubscribe();
            temperatureSubs = null;
        }
    }

    private void updateCurrentTemperature() {
        if (ventFeedbackCooldown > 0) ventFeedbackCooldown--;
        if (ventingTicks > 0) ventingTicks--;

        if (recipeLogic.isWorking()) {
            if (getOffsetTimer() % 12 == 0 && currentTemperature < getMaxTemperature()) {
                if (isHighPressure || getOffsetTimer() % 24 == 0) currentTemperature++;
            }
        } else if (timeBeforeCoolingDown == 0) {
            if (currentTemperature > 0) {
                currentTemperature--;
                timeBeforeCoolingDown = getCooldownInterval();
            }
        } else {
            timeBeforeCoolingDown--;
        }

        if (getOffsetTimer() % 10 == 0) {
            produceSteam();
        }
        updateTemperatureSubscription();
    }

    private void produceSteam() {
        if (currentTemperature < 100) {
            hasNoWater = false;
            return;
        }
        if (isCoFiringPaused()) return;

        int fillAmount = (int) getTotalSteamOutput();
        boolean drainedWater = !waterTank.drainInternal(1, FluidAction.EXECUTE).isEmpty();
        long filledSteam = 0;
        if (drainedWater) {
            filledSteam = steamTank.fillInternal(GTMaterials.Steam.getFluid(fillAmount), FluidAction.EXECUTE);
        }

        if (hasNoWater && drainedWater) {
            doExplosion(2.0F);
        } else {
            hasNoWater = !drainedWater;
        }

        if (filledSteam == 0 && drainedWater && getLevel() instanceof ServerLevel serverLevel) {
            ventingTicks = 10;
            if (ventFeedbackCooldown == 0) {
                playVentFeedback(serverLevel);
                ventFeedbackCooldown = 40;
            }
            steamTank.drainInternal(4 * FluidType.BUCKET_VOLUME, FluidAction.EXECUTE);
        }
    }

    private void playVentFeedback(ServerLevel serverLevel) {
        float x = getPos().getX() + 0.5F;
        float y = getPos().getY() + 0.5F;
        float z = getPos().getZ() + 0.5F;
        Direction front = getFrontFacing();
        serverLevel.sendParticles(ParticleTypes.CLOUD,
                x + front.getStepX() * 0.6,
                y + front.getStepY() * 0.6,
                z + front.getStepZ() * 0.6,
                7 + GTValues.RNG.nextInt(3),
                front.getStepX() / 2.0,
                front.getStepY() / 2.0,
                front.getStepZ() / 2.0,
                0.1);
        if (ConfigHolder.INSTANCE.machines.machineSounds) {
            getLevel().playSound(null, x, y, z, SoundEvents.LAVA_EXTINGUISH, SoundSource.BLOCKS, 1.0F, 1.0F);
        }
    }

    private int getCooldownInterval() {
        return isMissingPowder() ? 20 : isHighPressure ? 40 : 45;
    }

    public int getMaxTemperature() {
        return isHighPressure ? 1000 : 500;
    }

    public int getCurrentTemperature() {
        return currentTemperature;
    }

    private double getTemperaturePercent() {
        return currentTemperature / (double) getMaxTemperature();
    }

    private long getBaseSteamOutput() {
        boolean activelyCoFiring = coFiring && recipeLogic.isWorking() && powderBurnRemaining > 0;
        if (activelyCoFiring) return isHighPressure ? CO_FIRING_OUTPUT_HP : CO_FIRING_OUTPUT_LP;
        return isHighPressure ? LIQUID_OUTPUT_HP : LIQUID_OUTPUT_LP;
    }

    /** Returns the amount of steam produced by one 10-tick production cycle. */
    public long getTotalSteamOutput() {
        if (currentTemperature < 100) return 0;
        return (long) (getBaseSteamOutput() * ((float) currentTemperature / getMaxTemperature()) / 2);
    }

    /** Returns the current net production rate shown by information integrations. */
    public double getCurrentSteamOutputPerTick() {
        if (waterTank.isEmpty() || isCoFiringPaused()) return 0;
        return getTotalSteamOutput() / 10.0;
    }

    /** Returns the real tick duration represented by the remaining powder heat buffer. */
    public int getPowderBurnRemainingTicks() {
        if (powderBurnRemaining <= 0) return 0;
        int tenthsPerTick = isHighPressure ? TENTHS_PER_POWDER_BURN_TICK_HP :
                TENTHS_PER_POWDER_BURN_TICK_LP;
        int remainingTenths = powderBurnRemaining * 10 - powderConsumptionTenths;
        return Math.max(0, (remainingTenths + tenthsPerTick - 1) / tenthsPerTick);
    }

    public static ModifierFunction recipeModifier(@NotNull MetaMachine machine, @NotNull GTRecipe recipe) {
        if (!(machine instanceof MixedFuelBoilerMachine boiler)) {
            return RecipeModifier.nullWrongType(MixedFuelBoilerMachine.class, machine);
        }
        if (!boiler.isHighPressure) return ModifierFunction.IDENTITY;
        return ModifierFunction.builder().durationMultiplier(0.5).build();
    }

    @Override
    public boolean beforeWorking(@Nullable GTRecipe recipe) {
        return (!coFiring || preparePowder()) && super.beforeWorking(recipe);
    }

    @Override
    public boolean onWorking() {
        boolean working = super.onWorking();
        if (working) {
            if (currentTemperature < getMaxTemperature()) {
                currentTemperature = Math.max(1, currentTemperature);
                updateTemperatureSubscription();
            }
            if (coFiring) consumePowderTick();
        }
        return working;
    }

    @Override
    public void afterWorking() {
        super.afterWorking();
        timeBeforeCoolingDown = getCooldownInterval();
    }

    private boolean canAdvanceRecipe() {
        return !coFiring || preparePowder();
    }

    private boolean preparePowder() {
        if (powderBurnRemaining > 0) return true;
        ItemStack stack = powderHandler.getStackInSlot(0);
        if (!isValidPowder(stack)) return false;

        int burnTime = getPowderBurnTime(stack);
        if (burnTime <= 0) return false;

        ItemStack consumed = powderHandler.extractItemInternal(0, 1, false);
        if (consumed.isEmpty()) return false;

        burningPowder = consumed.copyWithCount(1);
        powderBurnRemaining = burnTime;
        powderBurnTotal = burnTime;
        markDirty();
        return true;
    }

    private void consumePowderTick() {
        powderConsumptionTenths += isHighPressure ? TENTHS_PER_POWDER_BURN_TICK_HP :
                TENTHS_PER_POWDER_BURN_TICK_LP;
        while (powderConsumptionTenths >= 10 && powderBurnRemaining > 0) {
            powderConsumptionTenths -= 10;
            powderBurnRemaining--;
        }
        if (powderBurnRemaining == 0) {
            powderBurnTotal = 0;
            burningPowder = ItemStack.EMPTY;
        }
    }

    private boolean isValidPowder(ItemStack stack) {
        return !stack.isEmpty() && stack.is(GSETags.CO_FIRING_DUST_FUELS);
    }

    private int getPowderBurnTime(ItemStack stack) {
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

    public boolean isCoFiring() {
        return coFiring;
    }

    public void setCoFiring(boolean coFiring) {
        if (this.coFiring == coFiring) return;
        this.coFiring = coFiring;
        markDirty();
        recipeLogic.updateTickSubscription();
    }

    private boolean isMissingPowder() {
        return coFiring && powderBurnRemaining <= 0 && !isValidPowder(powderHandler.getStackInSlot(0));
    }

    private boolean isCoFiringPaused() {
        return isMissingPowder();
    }

    private double getPowderProgress() {
        return powderBurnTotal == 0 ? 0 : powderBurnRemaining / (double) powderBurnTotal;
    }

    public String getStatusTranslationKey() {
        String prefix = "gregsteamexpansion.machine.mixed_fuel_boiler.status.";
        if (hasNoWater && currentTemperature >= 100) return prefix + "dry_boiler";
        if (waterTank.isEmpty()) return prefix + "missing_water";
        if (isMissingPowder()) return prefix + "missing_co_firing_fuel";
        if (fuelTank.isEmpty() && recipeLogic.getLastRecipe() == null) return prefix + "missing_liquid_fuel";
        if (ventingTicks > 0) return prefix + "steam_output_blocked";
        if (recipeLogic.isWorking() && currentTemperature < getMaxTemperature()) return prefix + "heating";
        if (recipeLogic.isWorking()) return prefix + "running";
        return prefix + "idle";
    }

    @Override
    protected InteractionResult onSoftMalletClick(Player player, InteractionHand hand, Direction side,
                                                   BlockHitResult hit) {
        return InteractionResult.PASS;
    }

    @Override
    public InteractionResult onUse(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
                                   BlockHitResult hit) {
        if (!isRemote()) {
            if (FluidUtil.interactWithFluidHandler(player, hand, waterTank) ||
                    FluidUtil.interactWithFluidHandler(player, hand, fuelTank)) {
                return InteractionResult.SUCCESS;
            }
        }
        return IInteractedMachine.super.onUse(state, level, pos, player, hand, hit);
    }

    @Override
    public ModularUI createUI(Player player) {
        ModularUI ui = new ModularUI(176, 166, this, player)
                .background(GuiTextures.BACKGROUND_STEAM.get(isHighPressure))
                .widget(new LabelWidget(6, 6, getBlockState().getBlock().getDescriptionId()))
                .widget(new ButtonWidget(7, 26, 25, 18, IGuiTexture.EMPTY, click -> setCoFiring(false))
                        .setHoverTooltips(Component.translatable(
                                "gregsteamexpansion.machine.mixed_fuel_boiler.mode.liquid.tooltip")))
                .widget(new ImageWidget(7, 26, 25, 18,
                        () -> modeButtonTexture(!coFiring)))
                .widget(new ImageWidget(11, 27, 16, 16, MODE_LIQUID_ICON))
                .widget(new ButtonWidget(34, 26, 25, 18, IGuiTexture.EMPTY, click -> setCoFiring(true))
                        .setHoverTooltips(Component.translatable(
                                "gregsteamexpansion.machine.mixed_fuel_boiler.mode.co_firing.tooltip")))
                .widget(new ImageWidget(34, 26, 25, 18,
                        () -> modeButtonTexture(coFiring)))
                .widget(new ImageWidget(38, 27, 16, 16, MODE_CO_FIRING_ICON))
                .widget(new ProgressWidget(() -> recipeLogic.isWorking() ? 1 : 0, 24, 47, 18, 18)
                        .setProgressTexture(
                                GuiTextures.PROGRESS_BAR_BOILER_FUEL.get(isHighPressure).getSubTexture(0, 0, 1, 0.5),
                                GuiTextures.PROGRESS_BAR_BOILER_FUEL.get(isHighPressure).getSubTexture(0, 0.5, 1, 0.5))
                        .setFillDirection(ProgressTexture.FillDirection.DOWN_TO_UP))
                .widget(new ImageWidget(7, 67, 13, 13, this::getStatusIconTexture))
                .widget(new LabelWidget(21, 68, this::getStatusTranslationKey))
                .widget(new TankWidget(steamTank.getStorages()[0], 70, 26, 10, 54, true, false)
                        .setShowAmount(false)
                        .setFillDirection(ProgressTexture.FillDirection.DOWN_TO_UP)
                        .setBackground(GuiTextures.PROGRESS_BAR_BOILER_EMPTY.get(isHighPressure)))
                .widget(new TankWidget(waterTank.getStorages()[0], 83, 26, 10, 54, false, true)
                        .setShowAmount(false)
                        .setFillDirection(ProgressTexture.FillDirection.DOWN_TO_UP)
                        .setBackground(GuiTextures.PROGRESS_BAR_BOILER_EMPTY.get(isHighPressure)))
                .widget(new ProgressWidget(this::getTemperaturePercent, 96, 26, 10, 54)
                        .setProgressTexture(GuiTextures.PROGRESS_BAR_BOILER_EMPTY.get(isHighPressure),
                                GuiTextures.PROGRESS_BAR_BOILER_HEAT)
                        .setFillDirection(ProgressTexture.FillDirection.DOWN_TO_UP)
                        .setDynamicHoverTips(percent -> I18n.get("gtceu.multiblock.large_boiler.temperature",
                                currentTemperature + 274, getMaxTemperature() + 274)))
                .widget(new TankWidget(fuelTank.getStorages()[0], 119, 26, 10, 54, true, true)
                        .setShowAmount(false)
                        .setFillDirection(ProgressTexture.FillDirection.DOWN_TO_UP)
                        .setBackground(GuiTextures.PROGRESS_BAR_BOILER_EMPTY.get(isHighPressure)))
                .widget(new SlotWidget(powderHandler.storage, 0, 142, 26)
                        .setBackgroundTexture(new GuiTextureGroup(GuiTextures.SLOT_STEAM.get(isHighPressure),
                                GuiTextures.DUST_OVERLAY_STEAM.get(isHighPressure))))
                .widget(new ProgressWidget(this::getPowderProgress, 142, 44, 18, 18)
                        .setProgressTexture(
                                GuiTextures.PROGRESS_BAR_BOILER_FUEL.get(isHighPressure).getSubTexture(0, 0, 1, 0.5),
                                GuiTextures.PROGRESS_BAR_BOILER_FUEL.get(isHighPressure).getSubTexture(0, 0.5, 1, 0.5))
                        .setFillDirection(ProgressTexture.FillDirection.DOWN_TO_UP))
                .widget(UITemplate.bindPlayerInventory(player.getInventory(),
                        GuiTextures.SLOT_STEAM.get(isHighPressure), 7, 84, true));
        return ui;
    }

    private IGuiTexture modeButtonTexture(boolean selected) {
        IGuiTexture button = ResourceBorderTexture.BUTTON_COMMON.copy()
                .setColor(selected ? ColorPattern.CYAN.color : -1);
        return selected ? new GuiTextureGroup(button, ResourceBorderTexture.SELECTED) : button;
    }

    private IGuiTexture getStatusIconTexture() {
        return switch (getStatusTranslationKey()) {
            case "gregsteamexpansion.machine.mixed_fuel_boiler.status.dry_boiler" -> STATUS_DRY_BOILER_ICON;
            case "gregsteamexpansion.machine.mixed_fuel_boiler.status.missing_water" -> STATUS_MISSING_WATER_ICON;
            case "gregsteamexpansion.machine.mixed_fuel_boiler.status.missing_liquid_fuel" ->
                    STATUS_MISSING_LIQUID_FUEL_ICON;
            case "gregsteamexpansion.machine.mixed_fuel_boiler.status.missing_co_firing_fuel" ->
                    STATUS_MISSING_CO_FIRING_FUEL_ICON;
            case "gregsteamexpansion.machine.mixed_fuel_boiler.status.steam_output_blocked" ->
                    STATUS_STEAM_OUTPUT_BLOCKED_ICON;
            default -> IGuiTexture.EMPTY;
        };
    }

    private static ResourceTexture icon(String name) {
        return new ResourceTexture(ICON_ROOT + name + ".png");
    }

    @Override
    public void animateTick(RandomSource random) {
        if (!recipeLogic.isWorking() || getOffsetTimer() % 20 != 0) return;
        BlockPos pos = getPos();
        Direction front = getFrontFacing();
        double x = pos.getX() + 0.5 + front.getStepX() * 0.52;
        double y = pos.getY() + 0.25 + random.nextDouble() * 0.25;
        double z = pos.getZ() + 0.5 + front.getStepZ() * 0.52;
        double sideOffset = random.nextDouble() * 0.4 - 0.2;
        if (front.getAxis() == Direction.Axis.X) z += sideOffset;
        if (front.getAxis() == Direction.Axis.Z) x += sideOffset;

        if (random.nextFloat() < 0.25F) {
            getLevel().addParticle(ParticleTypes.SMOKE, x, y, z,
                    front.getStepX() * 0.02, 0.01, front.getStepZ() * 0.02);
        }
        if (coFiring && random.nextFloat() < 0.25F) {
            getLevel().addParticle(ParticleTypes.FLAME, x, y, z,
                    front.getStepX() * 0.01, 0.01, front.getStepZ() * 0.01);
        }
        if (coFiring && ConfigHolder.INSTANCE.machines.machineSounds && getLevel().getGameTime() >= nextCrackleTick) {
            float pitch = isHighPressure ? 0.85F + random.nextFloat() * 0.10F :
                    1.00F + random.nextFloat() * 0.10F;
            getLevel().playLocalSound(x, y, z, SoundEvents.CAMPFIRE_CRACKLE, SoundSource.BLOCKS,
                    0.25F, pitch, false);
            nextCrackleTick = getLevel().getGameTime() + 60 + random.nextInt(41);
        }
    }

    @NotNull
    @Override
    public List<Component> getDataInfo(PortableScannerBehavior.DisplayMode mode) {
        if (mode == PortableScannerBehavior.DisplayMode.SHOW_ALL ||
                mode == PortableScannerBehavior.DisplayMode.SHOW_MACHINE_INFO) {
            List<Component> data = new ArrayList<>();
            data.add(Component.translatable("gtceu.machine.steam_boiler.heat_amount",
                    FormattingUtil.formatNumbers((int) (getTemperaturePercent() * 100))));
            data.add(Component.translatable(coFiring ?
                    "gregsteamexpansion.machine.mixed_fuel_boiler.mode.co_firing" :
                    "gregsteamexpansion.machine.mixed_fuel_boiler.mode.liquid"));
            return data;
        }
        return Collections.emptyList();
    }

    @Override
    public void onMachineRemoved() {
        clearInventory(powderHandler.storage);
    }

    private static final class MixedFuelRecipeLogic extends RecipeLogic {
        private final MixedFuelBoilerMachine boiler;

        private MixedFuelRecipeLogic(MixedFuelBoilerMachine boiler) {
            super(boiler);
            this.boiler = boiler;
        }

        @Override
        public void handleRecipeWorking() {
            if (!boiler.canAdvanceRecipe()) {
                setWaiting(Component.translatable(boiler.getStatusTranslationKey()));
                return;
            }
            super.handleRecipeWorking();
        }
    }
}
