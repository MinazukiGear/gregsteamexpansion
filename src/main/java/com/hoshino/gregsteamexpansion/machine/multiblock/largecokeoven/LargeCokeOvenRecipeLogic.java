package com.hoshino.gregsteamexpansion.machine.multiblock.largecokeoven;

import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.RecipeHelper;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.hoshino.gregsteamexpansion.GregSteamExpansion;
import com.hoshino.gregsteamexpansion.cokeoven.CokeOvenRecipeIndex;
import com.lowdragmc.lowdraglib.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 大型焦炉批次引擎 (coke-ovens.md 已确认开工预检、批次快照与原子扣取 /
 * 已确认完成提交与待输出保险 / 已确认配方选择与输入竞争)。
 *
 * <p>关键语义:</p>
 * <ul>
 * <li>最近成功配方优先; 偏好无完整输入时回退槽序 + 资源 ID 字典序; 偏好有完整
 *     输入但连一份产物都放不下时等待而不改选;</li>
 * <li>从最大并行 6 向下尝试: 每个候选并行先把配方乘以 p, 为合并产物预抽一次
 *     概率并写入精确快照, 模拟全部产物同时放入共享输出; 成功才原子扣取全部
 *     输入并创建批次;</li>
 * <li>锁定耗时 = max(1, ceil(原始耗时 ÷ 2)), 与并行数无关; 批次唯一序号 +
     提交标志持久化;</li>
 * <li>完成时按快照全有或全无提交; 失败进入"等待输出"(进度固定 100%), 库存
 *     变化后下一 tick 重试并每 20 tick 保底; 提交成功后下一 tick 才能开新批;</li>
 * <li>一氧化碳 0.1 × p 在进度首次到达完成点时结算一次, 等待输出/重载不重复;</li>
 * <li>数据包重载不取消进行中/待输出批次; 偏好被移除时视为无完整输入并回退。</li>
 * </ul>
 *
 * <p>标准 GTCEu 启停 (用户 2026-09-06 变更): 软锤/GUI 电源按钮经
 * isWorkingEnabled/setWorkingEnabled 控制 — 空闲时暂停立即挂起, 批次进行中
 * 暂停 = 本批完成后挂起; 恢复后从原进度继续。结构失效/未成型时进度回退至
 * 1 tick。</p>
 *
 * <p>全部批次状态保存在自有 {@link Persisted} 字段 (不占用上游 progress/
 * duration), 上游 {@link RecipeLogic#resetRecipeLogic()} (结构失效路径) 只
 * 清除其自有字段, 批次回退由 {@link #rewindBatchForStructureInvalid()} 显式
 * 执行。</p>
 */
public class LargeCokeOvenRecipeLogic extends RecipeLogic {

    protected static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(
            LargeCokeOvenRecipeLogic.class, RecipeLogic.MANAGED_FIELD_HOLDER);

    public static final int DATA_VERSION = 1;
    public static final int MAX_PARALLEL = 6;

    /** 最近成功配方 (持久化; 空闲重载后仍先尝试)。 */
    @Persisted
    @DescSynced
    private ResourceLocation preferredRecipeId;
    /** 当前批次: 乘以实际并行后的锁定配方。 */
    @Persisted
    @DescSynced
    @Nullable
    private GTRecipe batchRecipe;
    /** 批次创建时的原始配方资源 ID (显示与偏好用)。 */
    @Persisted
    @DescSynced
    @Nullable
    private ResourceLocation batchRecipeId;
    @Persisted
    @DescSynced
    private int batchParallel;
    /** 锁定总耗时 = max(1, ceil(原始耗时 ÷ 2))。 */
    @Persisted
    @DescSynced
    private int batchTotalDuration;
    /** 批次进度 (失效回退至 1 tick; 不用上游 progress 字段)。 */
    @Persisted
    @DescSynced
    private int batchProgress;
    /** 精确输出快照 (开工时按合并批次预抽一次概率)。 */
    @Persisted
    private List<ItemStack> snapshotItems = new ArrayList<>();
    @Persisted
    private List<FluidStack> snapshotFluids = new ArrayList<>();
    /** 批次唯一序号 (一次性事件防重)。 */
    @Persisted
    private long batchSeq;
    /** 进度已到达完成点且快照尚未提交。 */
    @Persisted
    @DescSynced
    private boolean batchCompleted;
    /** 一氧化碳危害已随本批次结算 (0.1 × p, 一次性)。 */
    @Persisted
    private boolean hazardSettled;
    /** 完成反馈已随本批次触发 (一次性)。 */
    @Persisted
    private boolean feedbackDone;
    /** 本批次实际扣取的输入摘要 (控制器拆除结算掉落用)。 */
    @Persisted
    private List<ItemStack> consumedInputs = new ArrayList<>();
    /** 旧批次迁移锁 (未来存档迁移安全路径; 首版数据一律视为首版语义)。 */
    @Persisted
    private boolean awaitingReInput;
    @Persisted
    private int dataVersion;
    /** 提交成功后本 tick 不再开新批。 */
    private boolean justCommitted;
    /** 等待输出期间共享输出库存是否发生变化 (变化后下一 tick 重试)。 */
    private boolean pendingDirty;

    public LargeCokeOvenRecipeLogic(LargeCokeOvenMachine machine) {
        super(machine);
    }

    @Override
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }

    @Override
    public LargeCokeOvenMachine getMachine() {
        return (LargeCokeOvenMachine) machine;
    }

    //////////////////////////////////////
    // ******* 状态查询 (GUI/Jade) *******//
    //////////////////////////////////////

    public boolean hasActiveBatch() {
        return batchRecipe != null;
    }

    public boolean isWaitingOutput() {
        return batchCompleted;
    }

    public int getBatchParallel() {
        return batchParallel;
    }

    public int getBatchProgress() {
        return batchProgress;
    }

    public int getBatchTotalDuration() {
        return batchTotalDuration;
    }

    @Nullable
    public ResourceLocation getBatchRecipeId() {
        return batchRecipeId;
    }

    @Nullable
    public ResourceLocation getPreferredRecipeId() {
        return preferredRecipeId;
    }

    public List<ItemStack> getPendingItems() {
        return batchCompleted ? snapshotItems : List.of();
    }

    public List<FluidStack> getPendingFluids() {
        return batchCompleted ? snapshotFluids : List.of();
    }

    public boolean isAwaitingReInput() {
        return awaitingReInput;
    }

    //////////////////////////////////////
    // ******* 存档版本迁移 *******//
    //////////////////////////////////////

    /**
     * 首版即把存档版本字段写入持久化数据; 读取时缺失版本字段 (dataVersion==0)
     * 的数据一律视为首版, 并按首版语义原样初始化, 不触发任何迁移。
     */
    @Override
    public void onMachineLoad() {
        super.onMachineLoad();
        if (dataVersion < DATA_VERSION) {
            dataVersion = DATA_VERSION;
            getMachine().markDirty();
        }
    }

    /** 供状态判定: 是否存在"识别得到原料但一份都开不了"的输入不足情形。 */
    public enum SelectionOutcome {
        NONE, NO_CANDIDATE, INSUFFICIENT, STARTUP_OUTPUT_BLOCKED
    }

    @Nullable
    private SelectionOutcome lastSelectionOutcome;

    public SelectionOutcome getLastSelectionOutcome() {
        return lastSelectionOutcome == null ? SelectionOutcome.NONE : lastSelectionOutcome;
    }

    //////////////////////////////////////
    // ********* 主循环 *********//
    //////////////////////////////////////

    @Override
    public void serverTick() {
        if (awaitingReInput || isSuspend()) return;
        var oven = getMachine();
        if (!oven.isFormed() || !(machine.self().getLevel() instanceof ServerLevel)) return;

        if (batchRecipe != null) {
            if (!batchCompleted) {
                tickWorking();
            } else {
                tickPendingCommit();
            }
        } else if (!justCommitted) {
            // 标准 GTCEu 启停: 已请求"本批完成后暂停"且批次已了结时挂起,
            // 不再开始新批次 (setStatus(SUSPEND) 会消费 suspendAfterFinish)。
            if (isSuspendAfterFinish()) {
                setStatus(Status.SUSPEND);
                return;
            }
            tryStartBatch();
        } else {
            // 成功提交后的下一服务端 tick 才能开始新批次。
            justCommitted = false;
        }
    }

    /** 推进加工进度; 到达锁定总耗时即结算一次性事件并进入待提交状态。 */
    private void tickWorking() {
        if (batchProgress < batchTotalDuration) {
            batchProgress++;
            setStatus(Status.WORKING);
            isActive = true;
        }
        if (batchProgress >= batchTotalDuration) {
            // 完成点一次性事件: 危害按完成份数 0.1 × p; 完成反馈单次。
            if (!hazardSettled) {
                hazardSettled = true;
                getMachine().emitCarbonMonoxideHazard(0.1f * batchParallel);
            }
            if (!feedbackDone) {
                feedbackDone = true;
                getMachine().playBatchCompletionFeedback();
            }
            batchCompleted = true;
            // 等待输出不再是加工: 上游状态切 WAITING 让炉火循环声与工作表现
            // 立即淡出; 自有批次字段保持精确状态。
            setStatus(Status.WAITING);
            pendingDirty = true;
            GregSteamExpansion.LOGGER.debug("[Large Coke Oven] batch #{} completed at {}, awaiting commit",
                    batchSeq, machine.self().getPos());
        }
    }

    /** 等待输出: 变化后下一 tick 重试 + 每 20 tick 保底; 全有或全无提交。 */
    private void tickPendingCommit() {
        boolean shouldTry = pendingDirty || getMachine().getOffsetTimer() % 20 == 0;
        pendingDirty = false;
        if (!shouldTry) return;
        if (!fitsOutputs(snapshotItems, snapshotFluids)) return;
        if (!commitOutputs()) return;
        // 成功提交: 清除批次; 下一 tick 才能开始新批次。已请求"本批完成后暂停"
        // 时直接挂起 (标准 GTCEu 启停语义), 等待输出的提交不受暂停影响。
        clearBatch();
        justCommitted = true;
        setStatus(isSuspendAfterFinish() ? Status.SUSPEND : Status.IDLE);
    }

    private void clearBatch() {
        batchRecipe = null;
        batchRecipeId = null;
        batchParallel = 0;
        batchTotalDuration = 0;
        batchProgress = 0;
        snapshotItems = new ArrayList<>();
        snapshotFluids = new ArrayList<>();
        batchCompleted = false;
        hazardSettled = false;
        feedbackDone = false;
        consumedInputs = new ArrayList<>();
    }

    //////////////////////////////////////
    // ****** 选配方与开工 ******//
    //////////////////////////////////////

    private void tryStartBatch() {
        lastSelectionOutcome = SelectionOutcome.NONE;
        var oven = getMachine();
        if (!(machine.self().getLevel() instanceof ServerLevel serverLevel)) return;
        var manager = serverLevel.getServer().getRecipeManager();

        GTRecipe chosen = selectRecipe(manager);
        if (chosen == null) return; // 状态由 selectRecipe 记录 (空闲/输入无效/输入不足/堵塞)

        // 从最大并行向下尝试: 每个候选并行先乘配方、预抽概率快照、无副作用模拟。
        int inputPortions = countInputPortions(chosen);
        int upper = Math.min(MAX_PARALLEL, inputPortions);
        for (int p = Math.max(upper, 0); p >= 1; p--) {
            GTRecipe multiplied = chosen.copy(com.gregtechceu.gtceu.api.recipe.content.ContentModifier.multiplier(p));
            // 概率输出在开工时预抽一次并写入精确快照 (p 份合并判定)。
            rollOutputSnapshot(multiplied);
            if (!fitsOutputs(snapshotItems, snapshotFluids)) continue;
            // 模拟通过 → 同一服务端操作内原子扣取全部输入并创建批次。
            var drawn = RecipeHelper.handleRecipe(machine, multiplied, IO.IN, multiplied.inputs,
                    chanceCaches, false, false);
            if (!drawn.isSuccess()) continue; // 与模拟不一致: 整次失败, 不保留部分扣取
            createBatch(chosen, multiplied, p);
            return;
        }
        // 所有并行均失败: 不创建批次、不扣取输入、清除试探快照、不更新偏好。
        snapshotItems = new ArrayList<>();
        snapshotFluids = new ArrayList<>();
        if (inputPortions >= 1) {
            lastSelectionOutcome = SelectionOutcome.STARTUP_OUTPUT_BLOCKED;
        }
        // 并行不足 1 份: 不开工、不扣取、不更新偏好。
    }

    /**
     * 最近成功配方优先: 偏好存在且有完整输入时坚持使用 (产物连一份都放不下时
     * 等待而不改选); 否则按槽序 (最小编号槽位) + 资源 ID 字典序选择。
     */
    @Nullable
    private GTRecipe selectRecipe(net.minecraft.world.item.crafting.RecipeManager manager) {
        if (preferredRecipeId != null) {
            GTRecipe preferred = findRecipeById(manager, preferredRecipeId);
            if (preferred != null && countInputPortions(preferred) >= 1) {
                lastSelectionOutcome = SelectionOutcome.NONE;
                return preferred;
            }
            // 偏好有完整输入但输出连一份都放不下: 仍返回偏好, 由开工预检失败
            // 标记启动前堵塞, 不改选其他配方。
            if (preferred != null) {
                lastSelectionOutcome = SelectionOutcome.STARTUP_OUTPUT_BLOCKED;
                return preferred;
            }
            // 偏好被数据包移除: 视为无法组成完整输入, 回退固定槽序。
        }

        var inputStacks = getMachine().getImportStacks();
        int firstNonEmpty = -1;
        for (int i = 0; i < inputStacks.length; i++) {
            if (!inputStacks[i].isEmpty()) {
                firstNonEmpty = i;
                break;
            }
        }
        if (firstNonEmpty < 0) {
            lastSelectionOutcome = SelectionOutcome.NONE; // 空闲
            return null;
        }
        // 由编号最小且能参与一条完整合法配方的输入槽决定候选; 同槽多配方按
        // 资源 ID 字典序取最前。
        for (int i = firstNonEmpty; i < inputStacks.length; i++) {
            ItemStack stack = inputStacks[i];
            if (stack.isEmpty()) continue;
            GTRecipe match = null;
            boolean anyIngredientMatch = false;
            for (GTRecipe recipe : CokeOvenRecipeIndex.recipesSortedById(manager)) {
                var contents = recipe.getInputContents(ItemRecipeCapability.CAP);
                if (contents.isEmpty()) continue;
                var ingredient = ItemRecipeCapability.CAP.of(contents.get(0).getContent());
                if (!ingredient.test(stack)) continue;
                anyIngredientMatch = true;
                if (RecipeHelper.checkConditions(recipe, this).isSuccess()) {
                    match = recipe;
                    break;
                }
            }
            if (match != null) {
                if (countInputPortions(match) < 1) {
                    lastSelectionOutcome = SelectionOutcome.INSUFFICIENT;
                    return match; // 交给开工路径记录"输入不足"
                }
                lastSelectionOutcome = SelectionOutcome.NONE;
                return match;
            }
            if (anyIngredientMatch) continue; // 条件不满足的槽位继续向后扫描
        }
        lastSelectionOutcome = SelectionOutcome.NO_CANDIDATE; // 输入无效
        return null;
    }

    @Nullable
    private GTRecipe findRecipeById(net.minecraft.world.item.crafting.RecipeManager manager,
                                    ResourceLocation id) {
        var holder = manager.byKey(id);
        return holder.orElse(null) instanceof GTRecipe gtRecipe ? gtRecipe : null;
    }

    //////////////////////////////////////
    // ****** 并行与快照 ******//
    //////////////////////////////////////

    /** 输入份数: 唯一物品输入项的完整数量, 跨 6 槽按匹配物品合计。 */
    private int countInputPortions(GTRecipe recipe) {
        var contents = recipe.getInputContents(ItemRecipeCapability.CAP);
        if (contents.isEmpty()) return 0;
        var ingredient = ItemRecipeCapability.CAP.of(contents.get(0).getContent());
        long available = 0;
        for (ItemStack stack : getMachine().getImportStacks()) {
            if (!stack.isEmpty() && ingredient.test(stack)) {
                available += stack.getCount();
            }
        }
        long perPortion = 0;
        for (ItemStack representative : ingredient.getItems()) {
            perPortion = Math.max(perPortion, representative.getCount());
        }
        if (perPortion <= 0) return 0;
        return (int) Math.min(Integer.MAX_VALUE, available / perPortion);
    }

    /**
     * 为合并批次 (配方 × p) 预抽一次概率并物化精确输出快照; 后续完成、等待、
     * 重载均不得重抽 (快照持久化, chanceCaches 持久化保证重载后同一判定)。
     */
    private void rollOutputSnapshot(GTRecipe multiplied) {
        rollChancedContents(multiplied);
    }

    /**
     * 按执行路径语义滚动概率产物: 必定产出 (chance >= maxChance) 直接物化,
     * 概率产物经 ChanceLogic + 持久化 chanceCaches 判定。
     */
    private void rollChancedContents(GTRecipe multiplied) {
        // snapshotItems / snapshotFluids 在此填充。
        List<ItemStack> items = new ArrayList<>();
        var itemContents = multiplied.getOutputContents(ItemRecipeCapability.CAP);
        var itemLogic = multiplied.getChanceLogicForCapability(ItemRecipeCapability.CAP, IO.OUT, false);
        int recipeTier = RecipeHelper.getPreOCRecipeEuTier(multiplied);
        int chanceTier = recipeTier + multiplied.ocLevel;
        var chanceFunction = multiplied.getType().getChanceFunction();

        List<Content> guaranteed = new ArrayList<>();
        List<Content> chanced = new ArrayList<>();
        for (Content content : itemContents) {
            if (content.chance >= content.maxChance) guaranteed.add(content);
            else if (content.chance > 0 || content.tierChanceBoost > 0) chanced.add(content);
        }
        for (Content content : guaranteed) {
            for (ItemStack stack : ItemRecipeCapability.CAP.of(content.getContent()).getItems()) {
                items.add(stack.copy());
            }
        }
        if (!chanced.isEmpty()) {
            var rolled = itemLogic.roll(ItemRecipeCapability.CAP, chanced, chanceFunction,
                    recipeTier, chanceTier, chanceCaches.get(ItemRecipeCapability.CAP), 1);
            for (Content content : rolled) {
                for (ItemStack stack : ItemRecipeCapability.CAP.of(content.getContent()).getItems()) {
                    items.add(stack.copy());
                }
            }
        }
        this.snapshotItems = items;

        List<FluidStack> fluids = new ArrayList<>();
        var fluidCap = com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability.CAP;
        var fluidContents = multiplied.getOutputContents(fluidCap);
        var fluidLogic = multiplied.getChanceLogicForCapability(fluidCap, IO.OUT, false);
        List<Content> fluidChanced = new ArrayList<>();
        for (Content content : fluidContents) {
            if (content.chance >= content.maxChance) {
                fluids.add(fluidCap.of(content.getContent()).getStacks()[0].copy());
            } else if (content.chance > 0 || content.tierChanceBoost > 0) {
                fluidChanced.add(content);
            }
        }
        if (!fluidChanced.isEmpty()) {
            var rolled = fluidLogic.roll(fluidCap, fluidChanced, chanceFunction,
                    recipeTier, chanceTier, chanceCaches.get(fluidCap), 1);
            for (Content content : rolled) {
                fluids.add(fluidCap.of(content.getContent()).getStacks()[0].copy());
            }
        }
        this.snapshotFluids = fluids;
    }

    private void createBatch(GTRecipe original, GTRecipe multiplied, int parallel) {
        batchRecipe = multiplied;
        batchRecipeId = original.getId();
        batchParallel = parallel;
        batchTotalDuration = Math.max(1, (original.duration + 1) / 2); // ceil(÷2)
        batchProgress = 0;
        batchCompleted = false;
        hazardSettled = false;
        feedbackDone = false;
        batchSeq++;
        preferredRecipeId = original.getId(); // 最近成功配方
        consumedInputs = recordConsumedInputs(original);
        GregSteamExpansion.LOGGER.debug("[Large Coke Oven] batch #{} started: {} x{} ({} ticks)",
                batchSeq, original.getId(), parallel, batchTotalDuration);
    }

    /** 记录本批次实际扣取的原物品 (拆除结算时按原始身份掉落)。 */
    private List<ItemStack> recordConsumedInputs(GTRecipe original) {
        // 扣取刚在本 tick 完成: 对比扣取前后差值不可行 (已扣), 直接按槽位与
        // 配方输入推算: 逐槽消耗与 handleRecipe 的槽序一致。
        List<ItemStack> consumed = new ArrayList<>();
        var contents = original.getInputContents(ItemRecipeCapability.CAP);
        if (contents.isEmpty()) return consumed;
        var ingredient = ItemRecipeCapability.CAP.of(contents.get(0).getContent());
        int remaining = 0;
        for (ItemStack rep : ingredient.getItems()) remaining = Math.max(remaining, rep.getCount());
        int parallel = batchParallel;
        remaining *= Math.max(1, parallel);
        for (ItemStack stack : getMachine().getImportStacks()) {
            if (remaining <= 0) break;
            if (stack.isEmpty() || !ingredient.test(stack)) continue;
            int take = Math.min(remaining, stack.getCount());
            ItemStack part = stack.copy();
            part.setCount(take);
            consumed.add(part);
            remaining -= take;
        }
        return consumed;
    }

    //////////////////////////////////////
    // ****** 完成提交与待输出 ******//
    //////////////////////////////////////

    private boolean fitsOutputs(List<ItemStack> items, List<FluidStack> fluids) {
        var oven = getMachine();
        for (ItemStack stack : items) {
            if (stack.isEmpty()) continue;
            if (!fitsItem(oven, stack)) return false;
        }
        for (FluidStack stack : fluids) {
            if (stack.isEmpty()) continue;
            if (oven.exportFluids.fillInternal(stack,
                    net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.SIMULATE)
                    < stack.getAmount()) return false;
        }
        return true;
    }

    /** 单个物品堆能否放入输出槽 (允许与现有堆叠合并; 区分总量与堆叠上限)。 */
    private boolean fitsItem(LargeCokeOvenMachine oven, ItemStack stack) {
        int remaining = stack.getCount();
        for (int slot = 0; slot < oven.exportItems.getSlots(); slot++) {
            ItemStack current = oven.exportItems.storage.getStackInSlot(slot);
            if (current.isEmpty()) {
                remaining -= Math.min(remaining, stack.getMaxStackSize());
            } else if (ItemStack.isSameItemSameTags(current, stack)) {
                int space = Math.min(current.getMaxStackSize(), oven.exportItems.storage.getSlotLimit(slot))
                        - current.getCount();
                if (space > 0) remaining -= Math.min(remaining, space);
            }
            if (remaining <= 0) return true;
        }
        return remaining <= 0;
    }

    /** 原子提交: 先整体模拟再整体执行, 任一类失败则两类都不写入。 */
    private boolean commitOutputs() {
        var oven = getMachine();
        if (!fitsOutputs(snapshotItems, snapshotFluids)) return false;
        for (ItemStack stack : snapshotItems) {
            if (stack.isEmpty()) continue;
            var rest = insertItemStacked(oven, stack);
            if (!rest.isEmpty()) {
                GregSteamExpansion.LOGGER.error(
                        "[Large Coke Oven] Output commit overflow despite simulation at {}",
                        machine.self().getPos());
            }
        }
        for (FluidStack stack : snapshotFluids) {
            if (stack.isEmpty()) continue;
            oven.exportFluids.fillInternal(stack,
                    net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
        }
        return true;
    }

    private ItemStack insertItemStacked(LargeCokeOvenMachine oven, ItemStack stack) {
        ItemStack rest = stack.copy();
        for (int slot = 0; slot < oven.exportItems.getSlots() && !rest.isEmpty(); slot++) {
            rest = oven.exportItems.insertItemInternal(slot, rest, false);
        }
        return rest;
    }

    //////////////////////////////////////
    // ****** 库存变化与重载 ******//
    //////////////////////////////////////

    /** 输出库存变化后下一 tick 重试待提交快照 (控制器接线)。 */
    public void markPendingDirty() {
        this.pendingDirty = true;
    }

    //////////////////////////////////////
    // ******* 控制器拆除结算 *******//
    //////////////////////////////////////

    /** 批次尚未完成时: 掉落实际扣取的原始输入 (不生成产物); 已完成: 掉落快照固体。 */
    public List<ItemStack> takeDropStacksOnRemoval() {
        List<ItemStack> drops = new ArrayList<>(consumedInputs);
        if (batchCompleted) {
            for (ItemStack stack : snapshotItems) {
                if (!stack.isEmpty()) drops.add(stack.copy());
            }
        }
        consumedInputs = new ArrayList<>();
        return drops;
    }

    /** 控制器拆除: 直接作废批次记录 (流体由机器统一丢失)。 */
    public void discardBatchOnRemoval() {
        clearBatch();
    }

    //////////////////////////////////////
    // ****** 结构失效进度回退 ******//
    //////////////////////////////////////

    /**
     * 结构失效/未成型时进行中批次停止并把进度回退至 1 tick (不足 1 tick 保持
     * 原值); 修复结构重新成型后从回退处继续同一批次, 不重新扣取输入、不重新
     * 选择配方、不重摇概率。等待输出状态不受影响 (进度固定 100%)。
     */
    public void rewindBatchForStructureInvalid() {
        if (batchRecipe == null || batchCompleted) return;
        if (batchProgress > 1) {
            batchProgress = 1;
        }
    }

    //////////////////////////////////////
    // ******* 待输出配额锁辅助 *******//
    //////////////////////////////////////

    /** 正在推进或持有待输出批次 (仓模式/朝向锁判定)。 */
    public boolean isBatchActiveOrPending() {
        return hasActiveBatch();
    }
}
