package com.hoshino.gregsteamexpansion.machine.multiblock.part;

import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.machine.multiblock.part.MultiblockPartMachine;
import com.gregtechceu.gtceu.api.machine.trait.FluidTankProxyTrait;
import com.gregtechceu.gtceu.api.machine.trait.ItemHandlerProxyTrait;
import com.gregtechceu.gtceu.api.transfer.fluid.IFluidHandlerModifiable;
import com.gregtechceu.gtceu.api.misc.IOFluidHandlerList;
import com.hoshino.gregsteamexpansion.GregSteamExpansion;
import com.hoshino.gregsteamexpansion.cokeoven.CokeOvenMode;
import com.hoshino.gregsteamexpansion.cokeoven.CokeOvenWorldData;
import com.hoshino.gregsteamexpansion.machine.multiblock.largecokeoven.LargeCokeOvenMachine;
import com.lowdragmc.lowdraglib.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;
import net.minecraft.ChatFormatting;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

import javax.annotation.ParametersAreNonnullByDefault;

/**
 * 大型焦炉仓 (coke-ovens.md 已确认大型焦炉仓身份与接口体系)。独立注册为
 * `gregsteamexpansion:large_coke_oven_hatch`, 是大型焦炉唯一合法的自动化接口;
 * 自身不持有独立库存, 只按当前模式代理控制器的对应共享库存。
 *
 * <ul>
 * <li>物品输入 / 固体输出 / 流体输出三种互斥模式; 新放置默认物品输入并持久化;
 *     潜行螺丝刀按固定顺序循环, 成型未运行时切换必须保持三模式各至少一个,
 *     运行/待输出时锁定;</li>
 * <li>只在面向机器外部的正面暴露当前模式能力; 背面 3 仓位必须朝机器后方,
 *     左/右侧仓位必须朝机器左/右侧, 成型后扳手无法转向非法方向 (不消耗耐久);</li>
 * <li>不自行推送: 固体与流体由控制器每 5 tick 按固定顺序轮询;</li>
 * <li>流体输出模式只出不进; 正面覆板按模式取交集。</li>
 * </ul>
 */
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class LargeCokeOvenHatchPartMachine extends MultiblockPartMachine {

    protected static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(
            LargeCokeOvenHatchPartMachine.class, MultiblockPartMachine.MANAGED_FIELD_HOLDER);

    public static final int DATA_VERSION = 1;

    @Persisted
    @DescSynced
    private CokeOvenMode mode = CokeOvenMode.DEFAULT;
    /** 本模组逻辑的数据版本; 0 表示旧版数据 (缺少模式)。 */
    @Persisted
    private int dataVersion;

    public final ItemHandlerProxyTrait inputInventory, outputInventory;
    public final FluidTankProxyTrait tank;

    public LargeCokeOvenHatchPartMachine(IMachineBlockEntity holder, Object... args) {
        super(holder);
        this.inputInventory = new ItemHandlerProxyTrait(this, IO.IN);
        this.outputInventory = new ItemHandlerProxyTrait(this, IO.OUT);
        this.tank = new FluidTankProxyTrait(this, IO.BOTH);
        // 模式标志与能力只作用于面向机器外部的当前正面 (动态谓词)。
        this.inputInventory.setCapabilityValidator(
                side -> mode == CokeOvenMode.ITEM_INPUT && side == getFrontFacing());
        this.outputInventory.setCapabilityValidator(
                side -> mode == CokeOvenMode.ITEM_OUTPUT && side == getFrontFacing());
        this.tank.setCapabilityValidator(
                side -> mode == CokeOvenMode.FLUID_OUTPUT && side == getFrontFacing());
    }

    @Override
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }

    public CokeOvenMode getMode() {
        return mode;
    }

    //////////////////////////////////////
    // ****** 控制器连接与代理 ******//
    //////////////////////////////////////

    @Override
    public void addedToController(IMultiController controller) {
        super.addedToController(controller);
        if (controller instanceof LargeCokeOvenMachine oven) {
            inputInventory.setProxy(oven.importItems);
            outputInventory.setProxy(oven.exportItems);
            tank.setProxy(oven.exportFluids);
        }
    }

    @Override
    public void removedFromController(IMultiController controller) {
        super.removedFromController(controller);
        inputInventory.setProxy(null);
        outputInventory.setProxy(null);
        tank.setProxy(null);
    }

    @Override
    public void onUnload() {
        super.onUnload();
        inputInventory.setProxy(null);
        outputInventory.setProxy(null);
        tank.setProxy(null);
    }

    /** 同一时刻只能连接并代理一台大型焦炉。 */
    @Override
    public boolean canShared() {
        return false;
    }

    /** 成型后保持仓自身外观 (模式标志所在正面持续可见)。 */
    @Override
    public boolean replacePartModelWhenFormed() {
        return false;
    }

    //////////////////////////////////////
    // ******* 朝向与旋转锁 *******//
    //////////////////////////////////////

    /** 所在候选位唯一合法的外向工作面; 未连接任何大型焦炉时返回 null (自由旋转)。 */
    @Nullable
    public Direction getLegalFacing() {
        for (IMultiController controller : getControllers()) {
            if (!(controller instanceof LargeCokeOvenMachine oven)) continue;
            Direction back = oven.getFrontFacing().getOpposite();
            Direction side = oven.getFrontFacing().getClockWise();
            var rel = self().getPos().subtract(oven.self().getPos());
            int dBack = rel.getX() * back.getStepX() + rel.getZ() * back.getStepZ();
            int dSide = rel.getX() * side.getStepX() + rel.getZ() * side.getStepZ();
            if (dBack == 4) return back;             // 背面候选位: 朝机器后方
            if (dSide >= 3) return side;             // 右侧候选位: 朝机器右侧
            if (dSide <= -3) return side.getOpposite(); // 左侧候选位: 朝机器左侧
        }
        return null;
    }

    @Override
    public boolean isFacingValid(Direction facing) {
        Direction legal = getLegalFacing();
        if (legal != null && facing != legal) return false;
        return super.isFacingValid(facing);
    }

    /**
     * 已成型仓尝试旋转到非法方向: 直接拒绝、不改变朝向、不消耗扳手耐久
     * (coke-ovens.md 已确认大型焦炉仓主动传输、方向与覆板)。
     */
    @Override
    protected InteractionResult onWrenchClick(Player playerIn, InteractionHand hand, Direction gridSide,
                                              BlockHitResult hitResult) {
        Direction legal = getLegalFacing();
        if (legal != null && playerIn.isShiftKeyDown()) {
            if (gridSide == getFrontFacing() || !super.isFacingValid(gridSide)) {
                return InteractionResult.FAIL;
            }
            if (gridSide != legal) {
                if (!isRemote()) {
                    playerIn.displayClientMessage(Component.translatable(
                            "gregsteamexpansion.large_coke_oven_hatch.facing.locked",
                            legal.getName()).withStyle(ChatFormatting.RED), true);
                }
                return InteractionResult.FAIL;
            }
        }
        return super.onWrenchClick(playerIn, hand, gridSide, hitResult);
    }

    //////////////////////////////////////
    // ******* 螺丝刀模式循环 *******//
    //////////////////////////////////////

    @Override
    protected InteractionResult onScrewdriverClick(Player playerIn, InteractionHand hand, Direction gridSide,
                                                   BlockHitResult hitResult) {
        if (!playerIn.isShiftKeyDown()) {
            return InteractionResult.PASS; // 非潜行螺丝刀优先操作覆板或上游部件
        }
        if (isRemote()) {
            return InteractionResult.SUCCESS;
        }
        // 标准行为 (用户 2026-09-06 变更): 模式循环始终成功, 不做配额/运行
        // 拒绝; 切换后立即重新验证结构, 破坏三种模式配额时结构进入无效状态
        // 并提示, 切回后自动恢复成型。
        mode = mode.next();
        onModeChanged();
        playerIn.displayClientMessage(Component.translatable("gregsteamexpansion.coke_oven_hatch.mode.changed",
                Component.translatable(mode.getTranslationKey())).withStyle(ChatFormatting.GREEN), true);
        return InteractionResult.sidedSuccess(isRemote());
    }

    /**
     * 模式切换后立即刷新能力并触发已连接控制器的结构重检: 破坏三种模式各
     * 至少一个的配额时结构即时失效 (GUI/Jade 显示 missing_mode 原因), 切回
     * 合法分配后自动恢复成型; 这与 GTCEu "部件变化在结构检查时生效" 的标准
     * 行为一致。
     */
    private void onModeChanged() {
        markDirty();
        notifyBlockUpdate();
        scheduleRenderUpdate();
        if (!isRemote() && isFormed()) {
            for (IMultiController controller : getControllers()) {
                if (controller.checkPatternWithLock()) {
                    controller.onStructureFormed();
                } else {
                    controller.onStructureInvalid();
                    if (controller.self().getLevel() instanceof ServerLevel serverLevel) {
                        com.gregtechceu.gtceu.api.pattern.MultiblockWorldSavedData.getOrCreate(serverLevel)
                                .addAsyncLogic(controller);
                    }
                }
            }
        }
    }
    //////////////////////////////////////
    // ******* 旧存档模式迁移 *******//
    //////////////////////////////////////

    @Override
    public void onLoad() {
        super.onLoad();
        if (!isRemote() && dataVersion < DATA_VERSION) {
            mode = CokeOvenMode.DEFAULT;
            dataVersion = DATA_VERSION;
            markDirty();
            scheduleRenderUpdate();
            notifyBlockUpdate();
            if (getLevel() instanceof ServerLevel serverLevel) {
                var data = CokeOvenWorldData.getOrCreate(serverLevel);
                if (data.isLegacySave()) {
                    GregSteamExpansion.LOGGER.info(
                            "[Large Coke Oven] hatch at {} initialized to item input mode.",
                            getPos().toShortString());
                }
            }
        }
    }

    //////////////////////////////////////
    // ******* 能力暴露语义 *******//
    //////////////////////////////////////

    /** 流体输出模式只允许从正面抽取, 拒绝任何灌入; 覆板按 OUT 侧照常生效。 */
    @Override
    @Nullable
    public IFluidHandlerModifiable getFluidHandlerCap(@Nullable Direction side, boolean useCoverCapability) {
        if (mode == CokeOvenMode.FLUID_OUTPUT && side == getFrontFacing()) {
            var handlerList = new IOFluidHandlerList(java.util.List.of(tank),
                    IO.OUT, getFluidCapFilter(side, IO.IN), getFluidCapFilter(side, IO.OUT));
            if (!useCoverCapability || side == null) return handlerList;
            var cover = getCoverContainer().getCoverAtSide(side);
            return cover != null ? cover.getFluidHandlerCap(handlerList) : handlerList;
        }
        return super.getFluidHandlerCap(side, useCoverCapability);
    }

    //////////////////////////////////////
    // ******* 连接状态与摘要 (Jade) *******//
    //////////////////////////////////////

    /** "formed" / "invalid" / "none" (Jade 连接状态)。 */
    public String getConnectionState() {
        for (IMultiController controller : getControllers()) {
            if (controller instanceof LargeCokeOvenMachine oven) {
                return oven.isFormed() ? "formed" : "invalid";
            }
        }
        return "none";
    }

    /** Jade: 当前模式对应共享库存的已用槽位/总槽位 (物品类模式)。 */
    @Nullable
    public String getSlotSummary() {
        var oven = getConnectedOven();
        if (oven == null) return null;
        return switch (mode) {
            case ITEM_INPUT -> usedSlots(oven.importItems) + "/" + LargeCokeOvenMachine.INPUT_SLOTS;
            case ITEM_OUTPUT -> usedSlots(oven.exportItems) + "/" + LargeCokeOvenMachine.OUTPUT_SLOTS;
            default -> null;
        };
    }

    private static int usedSlots(com.gregtechceu.gtceu.api.machine.trait.NotifiableItemStackHandler handler) {
        int used = 0;
        for (int i = 0; i < handler.getSlots(); i++) {
            if (!handler.storage.getStackInSlot(i).isEmpty()) used++;
        }
        return used;
    }

    @Nullable
    private LargeCokeOvenMachine getConnectedOven() {
        for (IMultiController controller : getControllers()) {
            if (controller instanceof LargeCokeOvenMachine oven && oven.isFormed()) {
                return oven;
            }
        }
        return null;
    }

    /** Jade: 流体输出模式的流体摘要; 其他模式返回 null。 */
    @Nullable
    public String getFluidSummary() {
        if (mode != CokeOvenMode.FLUID_OUTPUT) return null;
        var oven = getConnectedOven();
        if (oven == null) return null;
        var stack = oven.exportFluids.getStorages()[0].getFluid();
        return stack.isEmpty()
                ? Component.translatable("gregsteamexpansion.jade.coke_oven_hatch.empty").getString()
                : stack.getDisplayName().getString() + " " + stack.getAmount() + " mB";
    }

    //////////////////////////////////////
    // ******* 客户端渲染数据 *******//
    //////////////////////////////////////

    @Override
    public void updateModelData(net.minecraftforge.client.model.data.ModelData.Builder builder) {
        super.updateModelData(builder);
        builder.with(com.hoshino.gregsteamexpansion.client.cokeoven.CokeOvenHatchModeModel.MODE_PROPERTY, mode);
    }
}
