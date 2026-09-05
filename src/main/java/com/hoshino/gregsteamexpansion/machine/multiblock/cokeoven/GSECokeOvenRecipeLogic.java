package com.hoshino.gregsteamexpansion.machine.multiblock.cokeoven;

import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.RecipeHelper;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;

import com.hoshino.gregsteamexpansion.GregSteamExpansion;

import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;

import net.minecraft.world.item.ItemStack;

/**
 * 普通焦炉配方逻辑 (coke-ovens.md 配方启动与输出原子性 / 结构失效 / 旧版迁移)。
 *
 * <p>相对上游 {@link RecipeLogic} 的关键差异:</p>
 * <ul>
 * <li>开工前按最坏情况 (概率输出全部产生) 模拟输出空间, 失败则不启动、不扣输入;</li>
 * <li>批次完成时整体提交物品与流体结果; 异常无法提交时进入持久化"待输出"状态,
 *     不再开始新配方, 空间恢复后重试, 一氧化碳危害只在首次到达完成点结算一次;</li>
 * <li>一般结构失效保留批次, 进度回退至 1 tick, 重成型后从原进度继续 (不重新扣取
 *     输入、不重搜配方、不重摇概率);</li>
 * <li>关键部位清空时取消批次并交还已扣取的原物品;</li>
 * <li>旧存档迁移: 首次加载发现旧逻辑进行中批次时取消并锁定"等待重新输入"。</li>
 * </ul>
 */
public class GSECokeOvenRecipeLogic extends RecipeLogic {

    protected static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(
            GSECokeOvenRecipeLogic.class, RecipeLogic.MANAGED_FIELD_HOLDER);

    public static final int DATA_VERSION = 1;

    /** 批次已完成但产物无法整体提交的"待输出"状态 (coke-ovens.md 待输出结果)。 */
    @Persisted
    private boolean awaitingOutputCommit;
    /** 本批次实际扣取的原物品, 关键部位清空时掉落 (不生成产物、不按标签替换)。 */
    @Persisted
    private ItemStack consumedInput = ItemStack.EMPTY;
    /** 旧版进行中批次被迁移取消后, 等待一次新的合法物品插入。 */
    @Persisted
    private boolean awaitingReInput;
    /** 本模组逻辑的数据版本; 0 表示旧版上游逻辑保存的数据。 */
    @Persisted
    private int dataVersion;

    public GSECokeOvenRecipeLogic(GSECokeOvenMachine machine) {
        super(machine);
    }

    @Override
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }

    public GSECokeOvenMachine getOven() {
        return (GSECokeOvenMachine) machine;
    }

    public boolean hasPendingOutput() {
        return awaitingOutputCommit;
    }

    public boolean isAwaitingReInput() {
        return awaitingReInput;
    }

    //////////////////////////////////////
    // ****** 旧存档迁移 *******//
    //////////////////////////////////////

    @Override
    public void onMachineLoad() {
        super.onMachineLoad();
        if (dataVersion < DATA_VERSION) {
            boolean hadLegacyBatch = lastRecipe != null || progress > 0 || duration > 0;
            if (hadLegacyBatch) {
                cancelBatch();
                awaitingReInput = true;
                getOven().onLegacyBatchMigrated();
            }
            dataVersion = DATA_VERSION;
            updateTickSubscription();
        }
    }

    /** 取消当前批次: 不返还输入、不生成产物、进度归零 (迁移与关键清空共用)。 */
    public void cancelBatch() {
        lastRecipe = null;
        lastOriginRecipe = null;
        progress = 0;
        duration = 0;
        consecutiveRecipes = 0;
        isActive = false;
        awaitingOutputCommit = false;
        consumedInput = ItemStack.EMPTY;
        if (!isSuspend()) {
            setStatus(Status.IDLE);
        }
    }

    /**
     * 解除"等待重新输入": 只有一次成功的合法原料插入事件才调用
     * (coke-ovens.md 旧版进行中配方迁移)。
     */
    public void clearAwaitingReInput() {
        if (awaitingReInput) {
            awaitingReInput = false;
            updateTickSubscription();
        }
    }

    //////////////////////////////////////
    // **** 结构失效与批次保留 ****//
    //////////////////////////////////////

    // 结构失效瞬间批次快照 (transient: 快照在同一次失效调用内立即恢复,
    // 恢复后的正式字段本身已持久化)。
    private GTRecipe savedRecipe;
    private GTRecipe savedOriginRecipe;
    private int savedProgress;
    private int savedDuration;
    private int savedConsecutiveRecipes;

    /**
     * 一般结构失效: 上游 {@link RecipeLogic#resetRecipeLogic()} 会清空批次,
     * 失效前先快照, 失效后由 {@link #restoreAfterStructureInvalid()} 恢复。
     */
    public void snapshotForStructureInvalid() {
        savedRecipe = lastRecipe;
        savedOriginRecipe = lastOriginRecipe;
        savedProgress = progress;
        savedDuration = duration;
        savedConsecutiveRecipes = consecutiveRecipes;
    }

    /**
     * 一般结构失效后恢复批次: 保留锁定配方与已扣输入, 进度回退至 1 tick; 待输出
     * 状态原样保留 (进度固定在完成值)。状态置回 WORKING 以便重成型后从原进度
     * 继续, 不重新搜索配方、不重新扣取输入、不重新计算概率。
     */
    public void restoreAfterStructureInvalid() {
        if (savedRecipe == null) return;
        lastRecipe = savedRecipe;
        lastOriginRecipe = savedOriginRecipe;
        progress = savedProgress;
        duration = savedDuration;
        consecutiveRecipes = savedConsecutiveRecipes;
        if (!awaitingOutputCommit && progress < 1) {
            progress = 1;
        }
        isActive = true;
        savedRecipe = null;
        savedOriginRecipe = null;
        setStatus(Status.WORKING);
    }

    //////////////////////////////////////
    // ***** 开工预检与原子扣取 *****//
    //////////////////////////////////////

    @Override
    public void setupRecipe(GTRecipe recipe) {
        if (awaitingReInput) return;
        // 最坏情况输出预检: 概率输出按能够全部产生预留空间; 失败则不启动、
        // 不扣取任何输入, 进度保持为 0。
        var outputPrecheck = RecipeHelper.handleRecipe(machine, recipe, IO.OUT, recipe.outputs,
                chanceCaches, false, true);
        if (!outputPrecheck.isSuccess()) {
            RecipeLogic.putFailureReason(this, recipe, outputPrecheck.reason());
            return;
        }
        ItemStack before = getOven().getImportStack().copy();
        super.setupRecipe(recipe);
        if (isWorking() && lastRecipe == recipe && getMaxProgress() == recipe.duration) {
            // 已原子扣取: 记录实际扣取的原物品, 供关键部位清空时掉落。
            ItemStack after = getOven().getImportStack();
            if (!before.isEmpty() && ItemStack.isSameItemSameTags(before, after)) {
                before.setCount(before.getCount() - after.getCount());
                consumedInput = before;
            } else {
                consumedInput = before;
            }
        }
    }

    //////////////////////////////////////
    // **** 完成提交与待输出保险 ****//
    //////////////////////////////////////

    @Override
    public void onRecipeFinish() {
        if (lastRecipe == null) return;
        if (awaitingOutputCommit) {
            // 待输出重试: 不重新加工、不重摇概率 (chanceCaches 持久化), 不重复
            // 触发完成反馈与环境危害。
            tryCommitPending();
            return;
        }
        // 危害在进度首次到达完成点时结算一次, 不等待产物提交。
        machine.afterWorking();
        var committed = handleRecipeIO(lastRecipe, IO.OUT);
        if (committed.isSuccess()) {
            finishBatch();
        } else {
            // 异常状态导致无法整体提交: 保存为待输出结果, 停止开始新配方。
            awaitingOutputCommit = true;
            GregSteamExpansion.LOGGER.debug(
                    "[Coke Oven] Batch at {} entered pending-output state: {}",
                    machine.self().getPos(), committed.reason().getString());
        }
    }

    private void tryCommitPending() {
        if (lastRecipe == null) return;
        var committed = handleRecipeIO(lastRecipe, IO.OUT);
        if (committed.isSuccess()) {
            awaitingOutputCommit = false;
            finishBatch();
        }
    }

    /** 批次结果已整体提交: 清除批次并按上游语义立即尝试连下一批。 */
    private void finishBatch() {
        runAttempt = 0;
        runDelay = 0;
        consecutiveRecipes++;
        consumedInput = ItemStack.EMPTY;
        var recipeCheck = checkRecipe(lastRecipe);
        if (!recipeDirty && recipeCheck.isSuccess()) {
            setupRecipe(lastRecipe);
        } else {
            setStatus(Status.IDLE);
            consecutiveRecipes = 0;
            progress = 0;
            duration = 0;
            isActive = false;
        }
    }

    //////////////////////////////////////
    // ******* 关键部位清空 *******//
    //////////////////////////////////////

    /** 关键部位清空时取走本批次已扣取的原物品 (供机器掉落), 并取消批次状态。 */
    public ItemStack takeConsumedInputForDrop() {
        ItemStack stack = consumedInput;
        consumedInput = ItemStack.EMPTY;
        return stack;
    }

    //////////////////////////////////////
    // ********* Tick 调度 *********//
    //////////////////////////////////////

    @Override
    public void serverTick() {
        if (awaitingReInput) return;
        super.serverTick();
    }

    @Override
    public void updateTickSubscription() {
        if (awaitingReInput) {
            if (subscription != null) {
                subscription.unsubscribe();
                subscription = null;
            }
            return;
        }
        super.updateTickSubscription();
    }
}
