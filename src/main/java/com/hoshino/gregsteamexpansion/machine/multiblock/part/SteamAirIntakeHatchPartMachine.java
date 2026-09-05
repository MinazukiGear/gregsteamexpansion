package com.hoshino.gregsteamexpansion.machine.multiblock.part;

import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.UITemplate;
import com.gregtechceu.gtceu.api.gui.widget.TankWidget;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.api.machine.feature.IMachineLife;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.machine.multiblock.part.MultiblockPartMachine;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableFluidTank;
import com.gregtechceu.gtceu.common.data.GTMaterials;

import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import com.lowdragmc.lowdraglib.gui.widget.ImageWidget;
import com.lowdragmc.lowdraglib.gui.widget.LabelWidget;
import com.lowdragmc.lowdraglib.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidType;

import org.jetbrains.annotations.Nullable;

import javax.annotation.ParametersAreNonnullByDefault;

/**
 * 蒸汽进气室 / Steam Air Intake Hatch
 * (machines-and-hatches.md 已定案：蒸汽进气室): a dedicated air-collecting
 * interface for steam multiblocks that explicitly accept
 * {@code GSEPartAbilities.STEAM_AIR_INTAKE}.
 *
 * <p>Every {@code 80} server ticks the hatch adds up to {@code 4,000 mB} of
 * GTCEu standard air into its single {@code 64,000 mB} cache — the same average
 * rate as an LV gas collector ({@code 50 mB/t}) on a longer internal cycle.
 * Collection only advances while the hatch belongs to a formed multiblock,
 * stands in the overworld, has one strict-air block directly in front and has
 * cache room left; every other condition freezes and clears the unfinished
 * cycle, and cycle progress is never persisted.</p>
 *
 * <p>The internal tank is a recipe input handler for its controller
 * ({@code handlerIO = IO.IN}) but deliberately exposes no Forge fluid
 * capability ({@code capabilityIO = IO.NONE}): pipes, covers and containers can
 * neither fill nor drain it, and {@code swapIO()} is fixed to disallowed.</p>
 */
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class SteamAirIntakeHatchPartMachine extends MultiblockPartMachine implements IMachineLife {

    protected static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(
            SteamAirIntakeHatchPartMachine.class, MultiblockPartMachine.MANAGED_FIELD_HOLDER);

    public static final int INITIAL_TANK_CAPACITY = 64 * FluidType.BUCKET_VOLUME;
    public static final int COLLECT_CYCLE_TICKS = 80;
    public static final int COLLECT_AMOUNT = 4_000;

    /**
     * 服务端状态来源: shared verbatim by the GUI, Jade/探针 and nothing else.
     * Priority is fixed (machines-and-hatches.md 状态固定区分):
     * 结构未成型 > 维度不支持 > 进气口阻塞 > 缓存已满 > 采集中.
     */
    public enum IntakeStatus {
        NOT_FORMED("structure_not_formed"),
        WRONG_DIMENSION("wrong_dimension"),
        BLOCKED("intake_blocked"),
        FULL("cache_full"),
        COLLECTING("collecting");

        public static final IntakeStatus[] VALUES = values();

        private final String id;

        IntakeStatus(String id) {
            this.id = id;
        }

        /** Stable string id for Jade network data and lang lookups. */
        public String getId() {
            return id;
        }

        public Component getDisplayName() {
            return Component.translatable("gregsteamexpansion.machine.steam_air_intake_hatch.status." + id);
        }
    }

    public final NotifiableFluidTank tank;

    @Nullable
    private TickableSubscription intakeSubs;
    /** Unfinished cycle progress; deliberately not persisted (设计: 进度不持久化). */
    private int cycleTimer = 0;
    @DescSynced
    private int syncedCycleTimer = 0;
    @DescSynced
    private int syncedStatus = IntakeStatus.NOT_FORMED.ordinal();

    public SteamAirIntakeHatchPartMachine(IMachineBlockEntity holder) {
        super(holder);
        // handlerIO = IO.IN lets a formed controller read the cache as a plain
        // RecipeCapability.FLUID input; capabilityIO = IO.NONE keeps the Forge
        // fluid capability away from every external side.
        this.tank = new NotifiableFluidTank(this, 1, INITIAL_TANK_CAPACITY, IO.IN, IO.NONE)
                .setFilter(fluidStack -> fluidStack.getFluid().is(GTMaterials.Air.getFluidTag()));
    }

    @Override
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }

    //////////////////////////////////////
    // ****** Air collection ******//
    //////////////////////////////////////

    @Override
    public void onLoad() {
        super.onLoad();
        if (getLevel() instanceof net.minecraft.server.level.ServerLevel) {
            intakeSubs = subscribeServerTick(intakeSubs, this::updateIntake);
        }
    }

    @Override
    public void onUnload() {
        super.onUnload();
        unsubscribe(intakeSubs);
        intakeSubs = null;
    }

    @Override
    public void onMachineRemoved() {
        // 拆除时空气直接散失; the dropped item never carries fluid data because
        // the tank is not part of any item save (machines-and-hatches.md).
        tank.getStorages()[0].setFluid(FluidStack.EMPTY);
        cycleTimer = 0;
    }

    private void updateIntake() {
        IntakeStatus status = computeStatus();
        syncedStatus = status.ordinal();
        if (status != IntakeStatus.COLLECTING) {
            // Blocked, wrong dimension, cache full or structure lost: freeze and
            // clear the unfinished cycle; recovery restarts from a full 80 ticks.
            cycleTimer = 0;
            syncedCycleTimer = 0;
            return;
        }
        cycleTimer++;
        syncedCycleTimer = cycleTimer;
        if (cycleTimer >= COLLECT_CYCLE_TICKS) {
            cycleTimer = 0;
            syncedCycleTimer = 0;
            FluidStack stored = tank.getFluidInTank(0);
            int addAmount = Math.min(COLLECT_AMOUNT, INITIAL_TANK_CAPACITY - stored.getAmount());
            if (addAmount > 0) {
                // Direct write: the capability-facing fill() is disabled by
                // design (capabilityIO = IO.NONE) and the written stack is
                // always GTCEu standard air, matching the tank filter.
                FluidStack result = stored.isEmpty() ? new FluidStack(GTMaterials.Air.getFluid(), addAmount) :
                        new FluidStack(stored, addAmount);
                tank.setFluidInTank(0, result);
            }
        }
    }

    /**
     * Fixed display/logic priority: NOT_FORMED > WRONG_DIMENSION > BLOCKED >
     * FULL > COLLECTING (machines-and-hatches.md 状态固定区分).
     */
    public IntakeStatus getIntakeStatus() {
        return IntakeStatus.VALUES[Math.floorMod(syncedStatus, IntakeStatus.VALUES.length)];
    }

    /** Ticks remaining until the next collection; 0 means "no cycle running". */
    public int getTicksUntilCollection() {
        return Math.max(0, COLLECT_CYCLE_TICKS - Math.floorMod(syncedCycleTimer, COLLECT_CYCLE_TICKS + 1));
    }

    private IntakeStatus computeStatus() {
        if (!isFormedForIntake()) {
            return IntakeStatus.NOT_FORMED;
        }
        Level level = getLevel();
        if (level == null || level.dimension() != Level.OVERWORLD) {
            return IntakeStatus.WRONG_DIMENSION;
        }
        if (!isFrontClear(level)) {
            return IntakeStatus.BLOCKED;
        }
        if (tank.getFluidInTank(0).getAmount() >= INITIAL_TANK_CAPACITY) {
            return IntakeStatus.FULL;
        }
        return IntakeStatus.COLLECTING;
    }

    /**
     * 成型判定: the hatch must belong to at least one controller whose structure
     * is actually formed — {@link MultiblockPartMachine#isFormed()} alone would
     * only prove that a controller position was ever registered.
     */
    private boolean isFormedForIntake() {
        for (IMultiController controller : getControllers()) {
            if (controller.isFormed()) {
                return true;
            }
        }
        return false;
    }

    /**
     * 严格空气判定 for exactly the first block in front: plain, cave and void
     * air pass; snow layers, fire, plants, vines, webs, non-colliding blocks
     * and fluids all block the intake. The hatch never clears them.
     */
    private boolean isFrontClear(Level level) {
        Direction facing = getFrontFacing();
        if (facing == null) {
            return false;
        }
        BlockPos front = getPos().relative(facing, 1);
        return level.getBlockState(front).isAir();
    }

    // 进气室没有 I/O 对调能力可言：它不在 FluidHatchPartMachine 体系内，不存在
    // swapIO 路径，因此永远无法被改造成空气输出仓或普通流体输出仓。

    //////////////////////////////////////
    // ********** GUI ***********//
    //////////////////////////////////////

    @Override
    public ModularUI createUI(Player entityPlayer) {
        boolean steel = com.gregtechceu.gtceu.config.ConfigHolder.INSTANCE.machines.steelSteamMultiblocks;
        return new ModularUI(176, 166, this, entityPlayer)
                .background(GuiTextures.BACKGROUND_STEAM.get(steel))
                .widget(new LabelWidget(6, 6, getBlockState().getBlock().getDescriptionId()))
                .widget(new ImageWidget(7, 16, 81, 55, GuiTextures.DISPLAY_STEAM.get(steel)))
                .widget(new LabelWidget(11, 20, "gtceu.gui.fluid_amount"))
                .widget(new LabelWidget(11, 30, () -> String.format("%,d / %,d mB",
                        tank.getFluidInTank(0).getAmount(), INITIAL_TANK_CAPACITY)).setTextColor(-1)
                        .setDropShadow(true))
                .widget(new LabelWidget(11, 40, () -> getTicksUntilCollection() > 0
                        ? Component.translatable("gregsteamexpansion.machine.steam_air_intake_hatch.gui.next_collect",
                                getTicksUntilCollection()).getString()
                        : getIntakeStatus().getDisplayName().getString()))
                .widget(new LabelWidget(92, 16, () -> getIntakeStatus().getDisplayName().getString()))
                // 只读空气槽: no container interaction, no auto-IO switch, no
                // fluid lock and no circuit slot (machines-and-hatches.md GUI).
                .widget(new TankWidget(tank.getStorages()[0], 92, 25, false, false)
                        .setBackground(GuiTextures.FLUID_SLOT))
                .widget(UITemplate.bindPlayerInventory(entityPlayer.getInventory(),
                        GuiTextures.SLOT_STEAM.get(steel), 7, 84, true));
    }
}
