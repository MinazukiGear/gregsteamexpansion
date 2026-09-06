package com.hoshino.gregsteamexpansion.machine.multiblock.cokeoven;

import com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.UITemplate;
import com.gregtechceu.gtceu.api.gui.widget.SlotWidget;
import com.gregtechceu.gtceu.api.gui.widget.TankWidget;import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.pattern.error.PatternStringError;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.RecipeHelper;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.common.data.GTBlocks;
import com.gregtechceu.gtceu.common.machine.multiblock.primitive.CokeOvenMachine;
import com.gregtechceu.gtceu.utils.FormattingUtil;
import com.hoshino.gregsteamexpansion.GregSteamExpansion;
import com.hoshino.gregsteamexpansion.cokeoven.CokeOvenRecipeIndex;
import com.hoshino.gregsteamexpansion.cokeoven.CokeOvenWorldData;
import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import com.lowdragmc.lowdraglib.gui.texture.GuiTextureGroup;
import com.lowdragmc.lowdraglib.gui.texture.ProgressTexture;
import com.lowdragmc.lowdraglib.gui.widget.LabelWidget;
import com.lowdragmc.lowdraglib.gui.widget.ProgressWidget;
import com.lowdragmc.lowdraglib.syncdata.ISubscription;
import com.lowdragmc.lowdraglib.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.util.RandomSource;
import net.minecraftforge.fluids.FluidActionResult;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
import com.gregtechceu.gtceu.api.transfer.fluid.IFluidHandlerModifiable;
import net.minecraftforge.items.IItemHandlerModifiable;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 普通焦炉控制器 (coke-ovens.md 普通焦炉已确认设计)。继续使用 GTCEu 的
 * `gtceu:coke_oven` 注册身份, 通过替换机器工厂接入本模组逻辑:
 *
 * <ul>
 * <li>结构: 保持上游 3×3×3, 但内部中心空气格正下方的底层几何中心必须是焦炉砖,
 *     不可用焦炉仓替换; 成型时在世界数据登记 27 格占用与一格间距排斥范围;</li>
 * <li>控制器任意面不向自动化暴露物品/流体能力, 不接受软锤暂停与红石控制;</li>
 * <li>配方逻辑见 {@link GSECokeOvenRecipeLogic};</li>
 * <li>一般结构失效回退进度至 1 tick; 底层中心焦炉砖或控制器被拆除触发彻底清空;</li>
 * <li>按已确认优先级向 GUI / Jade 提供唯一主状态。</li>
 * </ul>
 */
public class GSECokeOvenMachine extends CokeOvenMachine
        implements com.hoshino.gregsteamexpansion.cokeoven.OwnedCokeOven {

    protected static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(
            GSECokeOvenMachine.class, CokeOvenMachine.MANAGED_FIELD_HOLDER);

    /** 状态优先级 (coke-ovens.md 普通焦炉运行状态与显示优先级), 枚举顺序即优先级。 */
    public enum OvenStatus {
        STRUCTURE_INVALID("gtceu.multiblock.invalid_structure"),
        PENDING_OUTPUT("gregsteamexpansion.coke_oven.status.pending_output"),
        RUNNING("gtceu.multiblock.running"),
        AWAITING_REINPUT("gregsteamexpansion.coke_oven.status.awaiting_reinput"),
        NO_INPUT("gtceu.multiblock.idling"),
        INPUT_INVALID("gregsteamexpansion.coke_oven.status.input_invalid"),
        ITEM_OUTPUT_BLOCKED("gregsteamexpansion.coke_oven.status.item_output_blocked"),
        FLUID_OUTPUT_BLOCKED("gregsteamexpansion.coke_oven.status.fluid_output_blocked"),
        BOTH_OUTPUT_BLOCKED("gregsteamexpansion.coke_oven.status.both_output_blocked"),
        READY("gregsteamexpansion.coke_oven.status.ready");

        public final String langKey;

        OvenStatus(String langKey) {
            this.langKey = langKey;
        }
    }

    @Persisted
    private boolean specialClearExecuted;
    /** 服务端权威判定并同步的唯一主状态 (客户端不得自行推测)。 */
    @Persisted
    @DescSynced
    private OvenStatus syncedStatus = OvenStatus.STRUCTURE_INVALID;
    /** 结构独占竞争失败原因: "overlap" / "too_close" / "" (空 = 无)。 */
    @Persisted
    @DescSynced
    private String syncedConflictType = "";
    /** 冲突对方控制器的坐标文本。 */
    @Persisted
    @DescSynced
    private String syncedConflictPos = "";
    /** 已归属砖探针数据源: "regular|occMin|occMax|"。 */
    @Persisted
    @DescSynced
    private String syncedClaimBox = "";

    private GSECokeOvenRecipeLogic ovenLogic;
    @Nullable
    private ISubscription importListenerSubs;
    private ItemStack previousImportStack = ItemStack.EMPTY;
    @Nullable
    private TickableSubscription statusSyncSubs;
    /** 服务端工作值: 最近一次结构检查/成型竞争的冲突结果。 */
    @Nullable
    private CokeOvenWorldData.ConflictType currentConflictType;
    @Nullable
    private BlockPos currentConflictPos;

    public GSECokeOvenMachine(IMachineBlockEntity holder, Object... args) {
        super(holder, args);
    }

    @Override
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }

    @Override
    public GSECokeOvenRecipeLogic getRecipeLogic() {
        return ovenLogic;
    }

    //////////////////////////////////////
    // ***** 初始化与生命周期 *****//
    //////////////////////////////////////

    @Override
    protected RecipeLogic createRecipeLogic(Object... args) {
        this.ovenLogic = new GSECokeOvenRecipeLogic(this);
        return ovenLogic;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (!isRemote()) {
            // "等待重新输入"只被一次成功的合法插入解除 (旧版迁移)。
            importListenerSubs = importItems.addChangedListener(this::onImportItemsChanged);
            previousImportStack = getImportStack().copy();
            // 状态同步: 每 10 tick 由服务端权威刷新一次主状态 (常数开销)。
            statusSyncSubs = subscribeServerTick(statusSyncSubs, this::tickStatusSync);
            // 旧存档迁移: 已成型的焦炉在区块加载时补登记占用 (migrated=true);
            // 多个旧焦炉冲突时按控制器坐标 (X,Y,Z) 升序由最小者胜出。
            if (isFormed() && getLevel() instanceof ServerLevel serverLevel) {
                var data = CokeOvenWorldData.getOrCreate(serverLevel);
                if (!data.hasClaim(getPos())) {
                    var claim = CokeOvenWorldData.regularClaim(getPos(), getFrontFacing(), true);
                    var result = data.claim(serverLevel, claim, this);
                    if (result instanceof CokeOvenWorldData.ClaimResult.Failed failed) {
                        setConflict(failed.conflict().type(), failed.conflict().otherController());
                        invalidateByOwnershipConflict();
                    } else {
                        syncedClaimBox = "regular|" + claim.occupiedMin.asLong() + "|" + claim.occupiedMax.asLong();
                        markDirty();
                    }
                } else {
                    var existing = data.claimOf(getPos());
                    if (existing != null) {
                        syncedClaimBox = "regular|" + existing.occupiedMin.asLong() + "|"
                                + existing.occupiedMax.asLong();
                        markDirty();
                    }
                }
            }
        }
    }

    @Override
    public void onUnload() {
        super.onUnload();
        if (importListenerSubs != null) {
            importListenerSubs.unsubscribe();
            importListenerSubs = null;
        }
        if (statusSyncSubs != null) {
            statusSyncSubs.unsubscribe();
            statusSyncSubs = null;
        }
    }

    private void tickStatusSync() {
        if (getOffsetTimer() % 10 != 0) return;
        OvenStatus computed = computeOvenStatus();
        String type = currentConflictType == null ? "" : currentConflictType.name();
        String pos = currentConflictPos == null ? "" : currentConflictPos.toShortString();
        if (computed != syncedStatus || !type.equals(syncedConflictType) || !pos.equals(syncedConflictPos)) {
            syncedStatus = computed;
            syncedConflictType = type;
            syncedConflictPos = pos;
            markDirty();
        }
    }

    private void setConflict(@Nullable CokeOvenWorldData.ConflictType type, @Nullable BlockPos other) {
        currentConflictType = type;
        currentConflictPos = other;
        syncedConflictType = type == null ? "" : type.name();
        syncedConflictPos = other == null ? "" : other.toShortString();
    }

    @Override
    public void onMachineRemoved() {
        // 控制器拆除: 唯一清理入口, 一次性执行批次取消 + 掉落 + 流体丢失
        // (coke-ovens.md 普通焦炉控制器拆除)。不调用上游的双库存清空避免重复掉落。
        if (!isRemote() && !specialClearExecuted) {
            performSpecialClear();
        }
        releaseClaim();
    }

    /** 旧版进行中批次迁移: 每个存档只发送一次概括性系统提醒并记录日志。 */
    public void onLegacyBatchMigrated() {
        if (!(getLevel() instanceof ServerLevel serverLevel)) return;
        var data = CokeOvenWorldData.getOrCreate(serverLevel);
        GregSteamExpansion.LOGGER.info(
                "[Coke Oven] Legacy in-progress recipe cancelled at {}; controller locked until a valid ingredient is re-inserted.",
                getPos().toShortString());
        if (!data.isRecipeMigrationNoticeSent()) {
            data.markRecipeMigrationNoticeSent();
            for (var player : serverLevel.players()) {
                player.sendSystemMessage(Component.translatable("gregsteamexpansion.coke_oven.migration.notice")
                        .withStyle(ChatFormatting.YELLOW));
            }
        }
    }

    //////////////////////////////////////
    // ****** 结构独占与间距 ******//
    //////////////////////////////////////

    @Override
    public boolean checkPattern() {
        // 上游默认实现 (接口默认方法): 纯图案检查, 之后叠加结构独占与间距检查。
        var pattern = getPattern();
        if (pattern == null || !pattern.checkPatternAt(getMultiblockState(), false)) return false;
        // 独占/间距检查只在主线程执行: GTCEu 异步探测线程禁止访问世界存档数据
        // (DimensionDataStorage 非线程安全, 并发 computeIfAbsent 会死循环卡死
        // 世界加载); 最终裁决在成型回调 (主线程) 的 claim 中完成, 异步探测成功
        // 后若占用冲突, claim 失败会立即失效并继续重试。
        if (com.gregtechceu.gtceu.api.pattern.MultiblockWorldSavedData.isThreadService()) {
            return true;
        }
        if (getLevel() instanceof ServerLevel serverLevel) {
            // 已成型机器优先保留占用; 重叠或间距不足时给出具体原因。
            var claim = CokeOvenWorldData.regularClaim(getPos(), getFrontFacing(), false);
            var conflict = CokeOvenWorldData.getOrCreate(serverLevel).findConflict(serverLevel, claim, this);
            if (conflict != null) {
                setConflict(conflict.type(), conflict.otherController());
                getMultiblockState().setError(new PatternStringError(conflict.type().langKey));
                return false;
            }
        }
        setConflict(null, null);
        return true;
    }

    @Override
    public void onStructureFormed() {
        super.onStructureFormed();
        if (isRemote()) return;
        specialClearExecuted = false;
        setConflict(null, null);
        refreshInputFilter();
        if (getLevel() instanceof ServerLevel serverLevel) {
            var claim = CokeOvenWorldData.regularClaim(getPos(), getFrontFacing(), false);
            var result = CokeOvenWorldData.getOrCreate(serverLevel).claim(serverLevel, claim, null);
            if (result instanceof CokeOvenWorldData.ClaimResult.Failed failed) {
                setConflict(failed.conflict().type(), failed.conflict().otherController());
                onStructureInvalid();
            } else {
                syncedClaimBox = "regular|" + claim.occupiedMin.asLong() + "|" + claim.occupiedMax.asLong();
                markDirty();
            }
        }
    }

    @Override
    public void onStructureInvalid() {
        boolean specialClear = isBottomCenterBrickMissing();
        // 上游实现会 resetRecipeLogic (清空批次); 普通焦炉设计要求一般结构失效
        // 保留批次并回退进度至 1 tick, 因此先快照、失效后再恢复。
        boolean hadBatch = ovenLogic.getLastRecipe() != null;
        ovenLogic.snapshotForStructureInvalid();
        super.onStructureInvalid();
        if (!isRemote()) {
            releaseClaim();
            if (!syncedClaimBox.isEmpty()) {
                syncedClaimBox = "";
                markDirty();
            }
            if (specialClear) {
                performSpecialClear();
            } else if (hadBatch) {
                ovenLogic.restoreAfterStructureInvalid();
            }
        }
    }

    /** 结构独占竞争失败: 强制失效并给出原因, 不触发任何清空。 */
    public void invalidateByOwnershipConflict() {
        setConflict(CokeOvenWorldData.ConflictType.OVERLAP, getPos());
        releaseClaim();
        onStructureInvalid();
        // 注册异步结构检查: 优先占用者被拆除并释放记录后可自动重新竞争成型。
        if (getLevel() instanceof ServerLevel serverLevel) {
            com.gregtechceu.gtceu.api.pattern.MultiblockWorldSavedData.getOrCreate(serverLevel).addAsyncLogic(this);
        }
    }

    private void releaseClaim() {
        if (getLevel() instanceof ServerLevel serverLevel) {
            CokeOvenWorldData.getOrCreate(serverLevel).release(getPos());
        }
    }

    /** 已归属砖探针数据源 (客户端读取)。 */
    public String getSyncedClaimBox() {
        return syncedClaimBox;
    }

    /** 底层几何中心 (内部中心空气格正下方) 是否已不是焦炉砖; 相关区块未加载时不判定。 */
    public boolean isBottomCenterBrickMissing() {
        if (getLevel() == null) return false;
        BlockPos pos = getPos().relative(getFrontFacing().getOpposite()).below();
        // 区块未加载时按一般结构失效处理, 不触发彻底清空 (卸载不等于失效)。
        if (!getLevel().isLoaded(pos)) return false;
        return !getLevel().getBlockState(pos).is(GTBlocks.CASING_COKE_BRICKS.get());
    }

    //////////////////////////////////////
    // ******* 关键部位清空 *******//
    //////////////////////////////////////

    /**
     * 彻底清空 (coke-ovens.md 底层中心焦炉砖特殊清空规则 / 控制器拆除):
     * 取消批次与待输出, 进度归零, 全部物品掉落 (含已扣取的原物品), 全部流体直接
     * 丢失; 通过一次性标志保证爆炸、相邻更新或重复回调只执行一次。
     */
    private void performSpecialClear() {
        specialClearExecuted = true;
        boolean hadContent = false;

        // 先取走已扣取的原物品再取消批次 (cancelBatch 会清空快照)。
        ItemStack consumed = ovenLogic.takeConsumedInputForDrop();
        ovenLogic.cancelBatch();
        if (!consumed.isEmpty()) {
            Block.popResource(getLevel(), getPos(), consumed);
            hadContent = true;
        }
        if (!importItems.isEmpty()) {
            hadContent = true;
            clearInventory(importItems.storage);
        }
        if (!exportItems.isEmpty()) {
            hadContent = true;
            clearInventory(exportItems.storage);
        }
        if (!exportFluids.isEmpty()) {
            hadContent = true;
            exportFluids.getStorages()[0].setFluid(FluidStack.EMPTY);
        }

        if (hadContent) {
            playSpecialClearFeedback();
        }
        GregSteamExpansion.LOGGER.debug("[Coke Oven] Special clear executed at {}", getPos().toShortString());
    }

    /** 单次熄火声 + 黑烟反馈, 只表示炉体泄漏和熄灭, 不生成真实流体或额外破坏。 */
    private void playSpecialClearFeedback() {
        if (!(getLevel() instanceof ServerLevel serverLevel)) return;
        BlockPos pos = getPos();
        serverLevel.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 1.0F, 0.6F);
        serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE,
                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 12, 0.4, 0.4, 0.4, 0.01);
        BlockPos hearth = getPos().relative(getFrontFacing().getOpposite());
        serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE,
                hearth.getX() + 0.5, hearth.getY() + 0.5, hearth.getZ() + 0.5, 12, 0.4, 0.4, 0.4, 0.01);
    }

    //////////////////////////////////////
    // ******* 输入过滤与解锁 *******//
    //////////////////////////////////////

    public ItemStack getImportStack() {
        return importItems.storage.getStackInSlot(0);
    }

    /** 输入槽只接受能够匹配服务端当前已加载合法焦炉配方的物品 (外部配方兼容)。 */
    private void refreshInputFilter() {
        if (!(getLevel() instanceof ServerLevel serverLevel)) return;
        var manager = serverLevel.getServer().getRecipeManager();
        importItems.setFilter(stack -> CokeOvenRecipeIndex.isValidInput(manager, stack));
    }

    private void onImportItemsChanged() {
        ItemStack current = getImportStack();
        // 仅"成功插入合法原料"事件解除等待重新输入: 新出现或数量增长的有效物品。
        boolean insertEvent;
        if (current.isEmpty()) {
            insertEvent = false;
        } else if (previousImportStack.isEmpty()) {
            insertEvent = true;
        } else if (ItemStack.isSameItemSameTags(current, previousImportStack)) {
            insertEvent = current.getCount() > previousImportStack.getCount();
        } else {
            insertEvent = true;
        }
        if (insertEvent && getLevel() instanceof ServerLevel serverLevel) {
            var manager = serverLevel.getServer().getRecipeManager();
            if (CokeOvenRecipeIndex.isValidInput(manager, current)) {
                ovenLogic.clearAwaitingReInput();
            }
        }
        previousImportStack = current.copy();
    }

    //////////////////////////////////////
    // ****** 能力与工作控制 ******//
    //////////////////////////////////////

    @Override
    @Nullable
    public IItemHandlerModifiable getItemHandlerCap(@Nullable Direction side, boolean useCoverCapability) {
        // 控制器任意面不向管道、漏斗、机械臂、封面或其他自动化设备暴露物品能力;
        // 所有外部自动化必须经过处于对应模式的焦炉仓。
        return null;
    }

    @Override
    @Nullable
    public IFluidHandlerModifiable getFluidHandlerCap(@Nullable Direction side, boolean useCoverCapability) {
        return null;
    }

    /** 软锤不改变任何状态、不消耗耐久、不重置配方 (无主动暂停)。 */
    @Override
    protected InteractionResult onSoftMalletClick(Player playerIn, InteractionHand hand, Direction gridSide,
                                                  BlockHitResult hitResult) {
        return InteractionResult.PASS;
    }

    //////////////////////////////////////
    // ******** 模式切换锁 ********//
    //////////////////////////////////////

    /** 正在推进配方或持有待输出结果时锁定全部焦炉仓模式。 */
    public boolean isModeSwitchLocked() {
        return isFormed() && (ovenLogic.isWorking() || ovenLogic.hasPendingOutput());
    }

    //////////////////////////////////////
    // ********* 状态与显示 ********//
    //////////////////////////////////////

    /** 按已确认优先级选出的唯一主状态; 服务端每 10 tick 刷新并同步 (服务端权威)。 */
    public OvenStatus getOvenStatus() {
        return syncedStatus;
    }

    /** 服务端完整状态计算 (含空闲细分; 需要 RecipeManager, 仅服务端调用)。 */
    private OvenStatus computeOvenStatus() {
        if (!isFormed()) return OvenStatus.STRUCTURE_INVALID;
        if (ovenLogic.hasPendingOutput()) return OvenStatus.PENDING_OUTPUT;
        if (ovenLogic.isWorking()) return OvenStatus.RUNNING;
        if (ovenLogic.isAwaitingReInput()) return OvenStatus.AWAITING_REINPUT;
        return computeIdleStatus();
    }

    /**
     * 空闲细分: 等待输入 / 输入无效 / 输出堵塞 / 准备启动。输出堵塞只在存在合法
     * 候选配方时判断; 物品与流体输出分开模拟以识别"两者均堵塞"。
     */
    private OvenStatus computeIdleStatus() {
        ItemStack input = getImportStack();
        if (input.isEmpty()) return OvenStatus.NO_INPUT;
        if (!(getLevel() instanceof ServerLevel serverLevel)) return OvenStatus.NO_INPUT;
        var manager = serverLevel.getServer().getRecipeManager();
        if (!CokeOvenRecipeIndex.hasRecipeFor(manager, input)) return OvenStatus.INPUT_INVALID;

        boolean anyCandidate = false;
        boolean itemBlocked = false;
        boolean fluidBlocked = false;
        for (GTRecipe recipe : CokeOvenRecipeIndex.recipes(manager)) {
            if (!matchesInput(recipe, input)) continue;
            anyCandidate = true;
            boolean itemsFit = simulateOutputs(recipe, ItemRecipeCapability.CAP);
            boolean fluidsFit = simulateOutputs(recipe, FluidRecipeCapability.CAP);
            if (itemsFit && fluidsFit) return OvenStatus.READY;
            if (!itemsFit) itemBlocked = true;
            if (!fluidsFit) fluidBlocked = true;
        }
        if (!anyCandidate) return OvenStatus.INPUT_INVALID;
        if (itemBlocked && fluidBlocked) return OvenStatus.BOTH_OUTPUT_BLOCKED;
        if (itemBlocked) return OvenStatus.ITEM_OUTPUT_BLOCKED;
        if (fluidBlocked) return OvenStatus.FLUID_OUTPUT_BLOCKED;
        return OvenStatus.READY;
    }

    private static boolean matchesInput(GTRecipe recipe, ItemStack input) {
        var contents = recipe.getInputContents(ItemRecipeCapability.CAP);
        if (contents.isEmpty()) return false;
        var ingredient = ItemRecipeCapability.CAP.of(contents.get(0).getContent());
        return ingredient.test(input);
    }

    /** 单独模拟一类输出的最坏情况空间检查 (概率输出全部产生)。 */
    private boolean simulateOutputs(GTRecipe recipe, RecipeCapability<?> cap) {
        var filtered = new java.util.HashMap<RecipeCapability<?>, List<Content>>();
        filtered.put(cap, new ArrayList<>(recipe.outputs.getOrDefault(cap, List.of())));
        var result = RecipeHelper.handleRecipe(this, recipe, IO.OUT, filtered, null, false, true);
        return result.isSuccess();
    }

    public Component getStatusText() {
        return Component.translatable(getOvenStatus().langKey);
    }

    /** GUI/Jade 详细信息: 同时列出全部实际阻塞原因。 */
    public List<Component> getStatusDetails() {
        List<Component> details = new ArrayList<>();
        if (!syncedConflictType.isEmpty()) {
            var type = "OVERLAP".equals(syncedConflictType)
                    ? CokeOvenWorldData.ConflictType.OVERLAP : CokeOvenWorldData.ConflictType.TOO_CLOSE;
            details.add(Component.translatable(type.langKey, syncedConflictPos));
        }
        switch (getOvenStatus()) {
            case PENDING_OUTPUT -> details.add(
                    Component.translatable("gregsteamexpansion.coke_oven.status.pending_output.detail"));
            case AWAITING_REINPUT -> details.add(
                    Component.translatable("gregsteamexpansion.coke_oven.status.awaiting_reinput.detail"));
            case ITEM_OUTPUT_BLOCKED, FLUID_OUTPUT_BLOCKED, BOTH_OUTPUT_BLOCKED -> details.add(
                    Component.translatable("gregsteamexpansion.coke_oven.status.blocked.detail"));
            default -> {}
        }
        return details;
    }

    /** Jade 使用的稳定状态 id。 */
    public String getStatusId() {
        return switch (getOvenStatus()) {
            case STRUCTURE_INVALID -> "invalid_structure";
            case PENDING_OUTPUT -> "pending_output";
            case RUNNING -> "working";
            case AWAITING_REINPUT -> "awaiting_reinput";
            case NO_INPUT -> "no_input";
            case INPUT_INVALID -> "input_invalid";
            case ITEM_OUTPUT_BLOCKED -> "item_blocked";
            case FLUID_OUTPUT_BLOCKED -> "fluid_blocked";
            case BOTH_OUTPUT_BLOCKED -> "both_blocked";
            case READY -> "ready";
        };
    }

    /** Jade: 预计剩余时间 (tick), 没有进行中配方时返回 -1。 */
    public int getRemainingTicks() {
        if (ovenLogic == null || !ovenLogic.isWorking() || ovenLogic.getMaxProgress() == 0) return -1;
        return Math.max(0, ovenLogic.getMaxProgress() - ovenLogic.getProgress());
    }

    //////////////////////////////////////
    // ********** 表现与交互 **********//
    //////////////////////////////////////

    /**
     * 控制器 GUI (coke-ovens.md 普通焦炉控制器 GUI): 沿用上游 176×166 原始布局,
     * 增加一行只读主状态文本; 进度条悬浮显示完成百分比与按固定耗时计算的预计
     * 剩余时间 (无进行中配方时不显示虚假剩余时间)。不加暂停/启停/能源等元素。
     */
    @Override
    public ModularUI createUI(Player entityPlayer) {
        return new ModularUI(176, 166, this, entityPlayer)
                .background(GuiTextures.PRIMITIVE_BACKGROUND)
                .widget(new LabelWidget(5, 5, getBlockState().getBlock().getDescriptionId()))
                .widget(new SlotWidget(importItems.storage, 0, 52, 30, true, true)
                        .setBackgroundTexture(new GuiTextureGroup(GuiTextures.PRIMITIVE_SLOT,
                                GuiTextures.PRIMITIVE_FURNACE_OVERLAY)))
                .widget(new ProgressWidget(this::getProgressPercentForGui, 76, 32, 20, 15,
                        GuiTextures.PRIMITIVE_BLAST_FURNACE_PROGRESS_BAR)
                                .setDynamicHoverTips(this::getProgressHoverText))
                .widget(new SlotWidget(exportItems.storage, 0, 103, 30, true, false)
                        .setBackgroundTexture(new GuiTextureGroup(GuiTextures.PRIMITIVE_SLOT,
                                GuiTextures.PRIMITIVE_FURNACE_OVERLAY)))
                .widget(new TankWidget(exportFluids.getStorages()[0], 134, 13, 20, 58, true, false)
                        .setBackground(GuiTextures.PRIMITIVE_LARGE_FLUID_TANK)
                        .setFillDirection(ProgressTexture.FillDirection.DOWN_TO_UP)
                        .setShowAmountOverlay(false)
                        .setOverlay(GuiTextures.PRIMITIVE_LARGE_FLUID_TANK_OVERLAY))
                // 只读主状态行 (最高优先级状态); 详细阻塞原因在状态文本悬浮提示中列出。
                .widget(new LabelWidget(5, 72, this::getStatusLine)
                        .setHoverTooltips(getStatusDetails()))
                .widget(UITemplate.bindPlayerInventory(entityPlayer.getInventory(), GuiTextures.PRIMITIVE_SLOT, 7, 84,
                        true));
    }

    private double getProgressPercentForGui() {
        if (ovenLogic == null || ovenLogic.getMaxProgress() == 0) return 0.0;
        return ovenLogic.getProgressPercent();
    }

    private String getProgressHoverText(double percent) {
        if (ovenLogic == null || !ovenLogic.isWorking() || ovenLogic.getMaxProgress() == 0) {
            return Component.translatable("gregsteamexpansion.coke_oven.gui.progress.idle",
                    String.format("%.0f%%", percent * 100)).getString();
        }
        int remaining = Math.max(0, ovenLogic.getMaxProgress() - ovenLogic.getProgress());
        return Component.translatable("gregsteamexpansion.coke_oven.gui.progress",
                String.format("%.0f%%", percent * 100),
                FormattingUtil.formatNumbers(remaining / 20.0) + "s").getString();
    }

    private String getStatusLine() {
        String status = getStatusText().getString();
        var details = getStatusDetails();
        if (details.isEmpty()) return status;
        return status + " (" + details.size() + ")";
    }

    /**
     * 只有配方正在实际推进时才显示火焰/大烟粒子并按上游概率播放燃烧声;
     * 空闲、堵塞、待输出等熄火状态不再产生工作表现 (coke-ovens.md 运行表现)。
     */
    @Override
    public void animateTick(RandomSource random) {
        if (getRecipeLogic() == null || !getRecipeLogic().isWorking()) return;
        super.animateTick(random);
    }

    /** 手持兼容容器只允许从流体输出罐抽取; 禁止向焦炉输出罐灌入任何流体。 */
    @Override
    public InteractionResult onUse(BlockState state, Level world, BlockPos pos, Player player, InteractionHand hand,
                                   BlockHitResult hit) {
        if (!isRemote()) {
            if (super.onUse(state, world, pos, player, hand, hit) == InteractionResult.SUCCESS) {
                return InteractionResult.SUCCESS;
            }
            // 只排空: tryFillContainer 从输出罐向玩家容器灌注, 永不反向灌入。
            FluidActionResult result = FluidUtil.tryFillContainer(player.getItemInHand(hand), exportFluids,
                    Integer.MAX_VALUE, player, true);
            if (result.isSuccess()) {
                if (!player.getAbilities().instabuild) {
                    player.setItemInHand(hand, result.getResult());
                }
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.PASS;
        }
        return super.onUse(state, world, pos, player, hand, hit);
    }
}
