package com.hoshino.gregsteamexpansion.machine.multiblock.part;

import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.common.machine.multiblock.part.CokeOvenHatch;
import com.gregtechceu.gtceu.utils.GTTransferUtils;
import com.hoshino.gregsteamexpansion.GregSteamExpansion;
import com.hoshino.gregsteamexpansion.cokeoven.CokeOvenMode;
import com.hoshino.gregsteamexpansion.cokeoven.CokeOvenWorldData;
import com.hoshino.gregsteamexpansion.machine.multiblock.cokeoven.GSECokeOvenMachine;
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
import net.minecraftforge.fluids.FluidStack;

import org.jetbrains.annotations.Nullable;

import javax.annotation.ParametersAreNonnullByDefault;

/**
 * 可配置焦炉仓 (coke-ovens.md 可配置焦炉仓)。继续使用 GTCEu 的
 * `gtceu:coke_oven_hatch` 注册身份, 保留上游的控制器库存代理与基础模型, 新增:
 *
 * <ul>
 * <li>物品输入 / 物品输出 / 流体输出三种互斥模式, 潜行螺丝刀固定循环切换,
 *     模式由仓自身持久化; 旧存档缺失模式数据时初始化为物品输入并提醒一次;</li>
 * <li>模式标志与能力只作用于当前正面: 输入模式被动接受合法原料, 两种输出模式
 *     可抽取并每 5 tick 向正面相邻目标主动推送对应产物;</li>
 * <li>流体输出模式只出不进 (fill 恒为 0);</li>
 * <li>焦炉实际推进配方或持有待输出结果时拒绝切换模式。</li>
 * </ul>
 */
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class GSECokeOvenHatch extends CokeOvenHatch {

    protected static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(
            GSECokeOvenHatch.class, CokeOvenHatch.MANAGED_FIELD_HOLDER);

    public static final int DATA_VERSION = 1;

    @Persisted
    private CokeOvenMode mode = CokeOvenMode.DEFAULT;
    /** 本模组逻辑的数据版本; 0 表示旧版上游逻辑保存的数据 (缺少模式)。 */
    @Persisted
    private int dataVersion;

    @Nullable
    protected TickableSubscription autoIOSubs;

    public GSECokeOvenHatch(IMachineBlockEntity holder, Object... args) {
        super(holder, args);
        // 模式标志、能力与覆板都只作用于当前正面 (validator 为动态谓词)。
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
                // 仅旧存档首次发现旧仓时发送一次概括性提醒; 新存档静默初始化。
                if (data.isLegacySave()) {
                    GregSteamExpansion.LOGGER.info(
                            "[Coke Oven] Legacy coke oven hatch at {} initialized to item input mode.",
                            getPos().toShortString());
                    if (!data.isHatchModeNoticeSent()) {
                        data.markHatchModeNoticeSent();
                        for (var player : serverLevel.players()) {
                            player.sendSystemMessage(
                                    Component.translatable("gregsteamexpansion.coke_oven_hatch.migration.notice")
                                            .withStyle(ChatFormatting.YELLOW));
                        }
                    }
                }
            }
        }
    }

    //////////////////////////////////////
    // ******* 能力暴露语义 *******//
    //////////////////////////////////////

    /**
     * 流体输出模式只允许从正面抽取, 拒绝任何灌入; 正面覆板过滤按 OUT 侧照常
     * 生效。其他模式由特质自身的 IO 语义与动态 validator 限制。
     */
    @Override
    @Nullable
    public com.gregtechceu.gtceu.api.transfer.fluid.IFluidHandlerModifiable getFluidHandlerCap(
                                                                                               @Nullable Direction side,
                                                                                               boolean useCoverCapability) {
        if (mode == CokeOvenMode.FLUID_OUTPUT && side == getFrontFacing()) {
            var handlerList = new com.gregtechceu.gtceu.api.misc.IOFluidHandlerList(java.util.List.of(tank),
                    IO.OUT, getFluidCapFilter(side, IO.IN), getFluidCapFilter(side, IO.OUT));
            if (!useCoverCapability || side == null) return handlerList;
            var cover = getCoverContainer().getCoverAtSide(side);
            return cover != null ? cover.getFluidHandlerCap(handlerList) : handlerList;
        }
        return super.getFluidHandlerCap(side, useCoverCapability);
    }

    //////////////////////////////////////
    // ********* 模式切换 *********//
    //////////////////////////////////////

    /**
     * 潜行 + 螺丝刀按固定顺序循环模式; 服务端对一次交互去重。切换成功才播放
     * 螺丝刀反馈并显示新模式; 拒绝时保持原模式、不消耗耐久, 播放轻微失败声并
     * 显示原因。
     */
    @Override
    protected InteractionResult onScrewdriverClick(Player playerIn, InteractionHand hand, Direction gridSide,
                                                   BlockHitResult hitResult) {
        if (!playerIn.isShiftKeyDown()) {
            // 非潜行螺丝刀优先操作覆板或上游部件, 不切换模式。
            return InteractionResult.PASS;
        }
        if (isRemote()) {
            return InteractionResult.SUCCESS;
        }
        if (isModeSwitchLocked()) {
            playFailSound();
            playerIn.displayClientMessage(
                    Component.translatable("gregsteamexpansion.coke_oven_hatch.mode.locked")
                            .withStyle(ChatFormatting.RED),
                    true);
            return InteractionResult.FAIL;
        }
        mode = mode.next();
        onModeChanged();
        playerIn.displayClientMessage(
                Component.translatable("gregsteamexpansion.coke_oven_hatch.mode.changed",
                        Component.translatable(mode.getTranslationKey())).withStyle(ChatFormatting.GREEN),
                true);
        return InteractionResult.sidedSuccess(isRemote());
    }

    /** 正在推进配方或持有待输出结果时, 所连接焦炉锁定全部仓模式。 */
    public boolean isModeSwitchLocked() {
        for (IMultiController controller : getControllers()) {
            if (controller instanceof GSECokeOvenMachine oven && oven.isModeSwitchLocked()) {
                return true;
            }
        }
        return false;
    }

    private void onModeChanged() {
        markDirty();
        updateAutoIOSubscription();
        // 旋转或模式改变后立即刷新能力, 覆板按新正面及新模式重新取交集。
        notifyBlockUpdate();
        scheduleRenderUpdate();
    }

    private void playFailSound() {
        if (getLevel() instanceof ServerLevel serverLevel) {
            serverLevel.playSound(null, getPos(), SoundEvents.DISPENSER_FAIL, SoundSource.BLOCKS, 0.4F, 1.2F);
        }
    }

    //////////////////////////////////////
    // ****** 控制器连接与代理 ******//
    //////////////////////////////////////

    @Override
    public void addedToController(IMultiController controller) {
        super.addedToController(controller);
        updateAutoIOSubscription();
    }

    @Override
    public void removedFromController(IMultiController controller) {
        super.removedFromController(controller);
        updateAutoIOSubscription();
    }

    /** 该仓当前模式所代理的控制器库存是否连接到了一座结构有效的普通焦炉。 */
    public boolean isConnectedToFormedOven() {
        for (IMultiController controller : getControllers()) {
            if (controller instanceof GSECokeOvenMachine oven && oven.isFormed()) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    public GSECokeOvenMachine getConnectedOven() {
        for (IMultiController controller : getControllers()) {
            if (controller instanceof GSECokeOvenMachine oven && oven.isFormed()) {
                return oven;
            }
        }
        return null;
    }

    //////////////////////////////////////
    // ********* 主动输出 *********//
    //////////////////////////////////////

    @Override
    public void onNeighborChanged(Block block, BlockPos fromPos, boolean isMoving) {
        super.onNeighborChanged(block, fromPos, isMoving);
        updateAutoIOSubscription();
    }

    @Override
    public void onRotated(Direction oldFacing, Direction newFacing) {
        super.onRotated(oldFacing, newFacing);
        updateAutoIOSubscription();
    }

    /** 按当前模式订阅 5 tick 主动输出: 只有对应输出模式且正面存在相邻目标时。 */
    protected void updateAutoIOSubscription() {
        boolean hasOutput = switch (mode) {
            case ITEM_INPUT -> false;
            case ITEM_OUTPUT -> !outputInventory.isEmpty() &&
                    GTTransferUtils.hasAdjacentItemHandler(getLevel(), getPos(), getFrontFacing());
            case FLUID_OUTPUT -> !tank.isEmpty() &&
                    GTTransferUtils.hasAdjacentFluidHandler(getLevel(), getPos(), getFrontFacing());
        };
        if (hasOutput) {
            autoIOSubs = subscribeServerTick(autoIOSubs, this::autoIO);
        } else if (autoIOSubs != null) {
            autoIOSubs.unsubscribe();
            autoIOSubs = null;
        }
    }

    /** 每 5 tick 向正面相邻目标主动推送当前模式的对应产物; 失败内容完整保留。 */
    protected void autoIO() {
        if (getOffsetTimer() % 5 == 0) {
            switch (mode) {
                case ITEM_OUTPUT -> outputInventory.exportToNearby(getFrontFacing());
                case FLUID_OUTPUT -> tank.exportToNearby(getFrontFacing());
                default -> {}
            }
            updateAutoIOSubscription();
        }
    }

    //////////////////////////////////////
    // ********** Jade 数据 **********//
    //////////////////////////////////////

    /** Jade: 当前模式对应库存的物品摘要 (无权访问的其他库存不暴露)。 */
    public String getItemSummary() {
        var stack = mode == CokeOvenMode.ITEM_OUTPUT ? outputInventory.getStackInSlot(0)
                : mode == CokeOvenMode.ITEM_INPUT ? inputInventory.getStackInSlot(0)
                : net.minecraft.world.item.ItemStack.EMPTY;
        return stack.isEmpty() ? Component.translatable("gregsteamexpansion.jade.coke_oven_hatch.empty").getString()
                : stack.getHoverName().getString() + " x" + stack.getCount();
    }

    /** Jade: 流体输出模式的流体摘要; 其他模式返回 null (不显示)。 */
    @Nullable
    public String getFluidSummary() {
        if (mode != CokeOvenMode.FLUID_OUTPUT) return null;
        FluidStack stack = tank.getFluidInTank(0);
        return stack.isEmpty() ? Component.translatable("gregsteamexpansion.jade.coke_oven_hatch.empty").getString()
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
