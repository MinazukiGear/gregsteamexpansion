package com.hoshino.gregsteamexpansion.machine.multiblock.largecokeoven;

import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.machine.multiblock.part.MultiblockPartMachine;
import com.gregtechceu.gtceu.api.machine.trait.FluidTankProxyTrait;
import com.gregtechceu.gtceu.api.machine.trait.ItemHandlerProxyTrait;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

import javax.annotation.ParametersAreNonnullByDefault;

/**
 * 大型焦炉仓 (coke-ovens.md 已确认大型焦炉仓身份与接口体系)。独立注册为
 * `gregsteamexpansion:large_coke_oven_hatch`, 是大型焦炉唯一合法的自动化接口;
 * 自身不持有独立物品或流体库存, 只按当前职责代理控制器的对应共享库存。
 *
 * <p>第 1 步 (当前): 与上游普通焦炉仓相同的三路代理布线 (被动行为, 无自动
 * 推送——大型焦炉由控制器每 5 tick 统一轮询)。后续步骤: 三互斥模式与方框
 * 图标、成型后唯一合法外向朝向、覆板交集、配额与运行锁定的螺丝刀循环、
 * 每类模式的正面能力收窄。</p>
 */
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class LargeCokeOvenHatchPartMachine extends MultiblockPartMachine {

    protected static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(
            LargeCokeOvenHatchPartMachine.class, MultiblockPartMachine.MANAGED_FIELD_HOLDER);

    public final ItemHandlerProxyTrait inputInventory, outputInventory;
    public final FluidTankProxyTrait tank;

    public LargeCokeOvenHatchPartMachine(IMachineBlockEntity holder, Object... args) {
        super(holder);
        this.inputInventory = new ItemHandlerProxyTrait(this, IO.IN);
        this.outputInventory = new ItemHandlerProxyTrait(this, IO.OUT);
        this.tank = new FluidTankProxyTrait(this, IO.BOTH);
    }

    @Override
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
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

    /** 同一时刻只能连接并代理一台大型焦炉 (coke-ovens.md 结构独占)。 */
    @Override
    public boolean canShared() {
        return false;
    }

    /** 成型后保持仓自身外观 (模式标志所在正面必须持续可见)。 */
    @Override
    public boolean replacePartModelWhenFormed() {
        return false;
    }

    //////////////////////////////////////
    // ********* GUI *********//
    //////////////////////////////////////

    @Override
    public boolean shouldOpenUI(Player player, InteractionHand hand, BlockHitResult hit) {
        return false;
    }

    //////////////////////////////////////
    // ******** 后续接入点 ********//
    //////////////////////////////////////

    /** 第 4 步: 当前模式 (物品输入/固体输出/流体输出), 新放置默认物品输入。 */
    @Nullable
    public String placeholderModeName() {
        return null;
    }
}
