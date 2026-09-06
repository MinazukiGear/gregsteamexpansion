package com.hoshino.gregsteamexpansion.machine.multiblock.largecokeoven;

import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.api.machine.feature.IEnvironmentalHazardEmitter;
import com.gregtechceu.gtceu.api.machine.feature.IMachineLife;
import com.gregtechceu.gtceu.api.machine.feature.IUIMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableFluidTank;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableItemStackHandler;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.pattern.MultiblockState;
import com.gregtechceu.gtceu.api.pattern.error.PatternStringError;
import com.gregtechceu.gtceu.api.transfer.fluid.IFluidHandlerModifiable;
import com.gregtechceu.gtceu.common.data.GTBlocks;
import com.gregtechceu.gtceu.utils.FormattingUtil;
import com.hoshino.gregsteamexpansion.GregSteamExpansion;
import com.hoshino.gregsteamexpansion.cokeoven.CokeOvenMode;
import com.hoshino.gregsteamexpansion.cokeoven.CokeOvenWorldData;
import com.hoshino.gregsteamexpansion.cokeoven.LargeCokeOvenStructures;
import com.hoshino.gregsteamexpansion.cokeoven.OwnedCokeOven;
import com.hoshino.gregsteamexpansion.machine.multiblock.part.LargeCokeOvenHatchPartMachine;
import com.gregtechceu.gtceu.utils.GTTransferUtils;
import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import com.lowdragmc.lowdraglib.gui.texture.GuiTextureGroup;
import com.lowdragmc.lowdraglib.gui.texture.ProgressTexture;
import com.lowdragmc.lowdraglib.gui.widget.LabelWidget;
import com.lowdragmc.lowdraglib.gui.widget.ProgressWidget;
import com.gregtechceu.gtceu.api.gui.widget.TankWidget;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.UITemplate;
import com.gregtechceu.gtceu.api.gui.widget.SlotWidget;
import com.lowdragmc.lowdraglib.syncdata.ISubscription;
import com.lowdragmc.lowdraglib.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.fluids.FluidActionResult;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.items.IItemHandlerModifiable;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 大型焦炉控制器 (coke-ovens.md 大型焦炉已确认设计)。独立注册为
 * `gregsteamexpansion:large_coke_oven`, 不替换 GTCEu 普通焦炉。
 *
 * <ul>
 * <li>结构: 7×7×5 包围范围逐层图案 + 成型后校验 (仓 3–5 个、三种模式各至少
 *     一个、仓朝向为所在候选位唯一合法外向); 任一相关区块不可用时暂停推进
 *     (状态"结构范围未完全加载"), 不判为结构失效;</li>
 * <li>所有权: 独占主体 175 坐标 + 料斗 10 坐标, 间距排斥 = 包围盒扩展一格,
 *     与普通焦炉互相判定;</li>
 * <li>批次: 结构失效进度回退至 1 tick (见 {@link LargeCokeOvenRecipeLogic});
 *     标准软锤/GUI 启停; 控制器拆除是唯一结算事件;</li>
 * <li>表现/接口: 一氧化碳 0.1 × p、每 5 tick 输出轮询 (背面左/中/右 → 左 → 右)、
 *     212×208 正式 GUI、状态优先级模型。</li>
 * </ul>
 */
public class LargeCokeOvenMachine extends WorkableMultiblockMachine
        implements IUIMachine, IMachineLife, IEnvironmentalHazardEmitter, OwnedCokeOven {

    protected static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(
            LargeCokeOvenMachine.class, WorkableMultiblockMachine.MANAGED_FIELD_HOLDER);

    /** 固定共享库存规格。 */
    public static final int INPUT_SLOTS = 6;
    public static final int OUTPUT_SLOTS = 6;
    public static final int FLUID_TANK_CAPACITY_MB = 64_000;

    /** 状态优先级 (coke-ovens.md 已确认运行状态集合), 枚举顺序即优先级。 */
    public enum OvenStatus {
        RANGE_NOT_LOADED("gregsteamexpansion.large_coke_oven.status.range_not_loaded"),
        STRUCTURE_INVALID("gtceu.multiblock.invalid_structure"),
        AWAITING_REINPUT("gregsteamexpansion.large_coke_oven.status.awaiting_reinput"),
        WAITING_OUTPUT("gregsteamexpansion.large_coke_oven.status.waiting_output"),
        WORKING("gtceu.multiblock.running"),
        STARTUP_OUTPUT_BLOCKED("gregsteamexpansion.large_coke_oven.status.startup_output_blocked"),
        INPUT_INVALID("gregsteamexpansion.large_coke_oven.status.input_invalid"),
        INPUT_INSUFFICIENT("gregsteamexpansion.large_coke_oven.status.input_insufficient"),
        READY("gregsteamexpansion.large_coke_oven.status.ready"),
        IDLE("gregsteamexpansion.large_coke_oven.status.idle");

        public final String langKey;

        OvenStatus(String langKey) {
            this.langKey = langKey;
        }
    }

    @Persisted
    public final NotifiableItemStackHandler importItems;
    @Persisted
    public final NotifiableItemStackHandler exportItems;
    @Persisted
    public final NotifiableFluidTank exportFluids;

    /** 服务端权威判定并同步的唯一主状态。 */
    @Persisted
    @DescSynced
    private OvenStatus syncedStatus = OvenStatus.RANGE_NOT_LOADED;
    /** 结构诊断: "invalid" 时的首个错误坐标文本 / "missing_hatch" / "facing"。 */
    @Persisted
    @DescSynced
    private String syncedStructureDetail = "";
    @Persisted
    @DescSynced
    private String syncedStructureDetailPos = "";
    /** 所有权冲突: "overlap" / "too_close" / ""。 */
    @Persisted
    @DescSynced
    private String syncedConflictType = "";
    @Persisted
    @DescSynced
    private String syncedConflictPos = "";
    /** 等待输出详情: 待提交产物摘要 (服务端在批次完成时构建)。 */
    @Persisted
    @DescSynced
    private String syncedPendingSummary = "";
    /** 已归属砖探针数据源: "large|occMin|occMax|extra1,extra2,..." (占用盒 + 料斗附加坐标)。 */
    @Persisted
    @DescSynced
    private String syncedClaimBox = "";
    /** 一次性危害强度 (spreadEnvironmentalHazard 读取)。 */
    private float pendingHazardStrength = 0.1f;

    /** 批次引擎 (构造于 createRecipeLogic, 时机在超类构造期间)。 */
    private LargeCokeOvenRecipeLogic ovenLogic;
    @Nullable
    private ISubscription importListenerSubs;
    @Nullable
    private TickableSubscription controllerTickSubs;
    private ItemStack[] previousImportStacks = new ItemStack[INPUT_SLOTS];
    @Nullable
    private CokeOvenWorldData.ConflictType currentConflictType;
    @Nullable
    private BlockPos currentConflictPos;
    @Nullable
    private String currentStructureDetail;
    @Nullable
    private String currentStructureDetailPos;
    /** 失效反馈资格: 上次"有效→无效"边沿触发后置 false, 重新成型才重置。 */
    private boolean invalidFeedbackArmed;
    /** 控制器拆除结算进行中 (避免拆除路径重复播放失效反馈)。 */
    private boolean removalSettled;

    public LargeCokeOvenMachine(IMachineBlockEntity holder, Object... args) {
        super(holder, args);
        this.importItems = new NotifiableItemStackHandler(this, INPUT_SLOTS, IO.IN);
        this.exportItems = new NotifiableItemStackHandler(this, OUTPUT_SLOTS, IO.OUT);
        this.exportFluids = new NotifiableFluidTank(this, 1, FLUID_TANK_CAPACITY_MB, IO.OUT);
    }

    @Override
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }

    @Override
    protected RecipeLogic createRecipeLogic(Object... args) {
        this.ovenLogic = new LargeCokeOvenRecipeLogic(this);
        return ovenLogic;
    }

    //////////////////////////////////////
    // ****** 生命周期 ******//
    //////////////////////////////////////

    @Override
    public void onLoad() {
        super.onLoad();
        if (!isRemote()) {
            importListenerSubs = importItems.addChangedListener(this::onSharedInventoriesChanged);
            exportItems.addChangedListener(this::onSharedInventoriesChanged);
            exportFluids.addChangedListener(this::onSharedInventoriesChanged);
            for (int i = 0; i < previousImportStacks.length; i++) {
                previousImportStacks[i] = getImportStack(i).copy();
            }
            controllerTickSubs = subscribeServerTick(controllerTickSubs, this::controllerTick);
            // 旧存档迁移: 已成型的大型焦炉在区块加载时补登记占用。
            if (isFormed() && getLevel() instanceof ServerLevel serverLevel) {
                var data = CokeOvenWorldData.getOrCreate(serverLevel);
                if (!data.hasClaim(getPos())) {
                    var claim = CokeOvenWorldData.largeClaim(getPos(), getFrontFacing(), true);
                    var result = data.claim(serverLevel, claim, this);
                    if (result instanceof CokeOvenWorldData.ClaimResult.Failed failed) {
                        setConflict(failed.conflict().type(), failed.conflict().otherController());
                        invalidateByOwnershipConflict();
                    } else {
                        syncClaimBox(claim);
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
        if (controllerTickSubs != null) {
            controllerTickSubs.unsubscribe();
            controllerTickSubs = null;
        }
    }

    /** 控制器级常量开销 tick: 状态同步 (10t) + 输出轮询 (5t)。 */
    private void controllerTick() {
        long timer = getOffsetTimer();
        if (timer % 5 == 0) {
            pollOutputs();
        }
        if (timer % 10 == 0) {
            tickStatusSync();
        }
    }

    //////////////////////////////////////
    // ****** 能力封锁 ******//
    //////////////////////////////////////

    @Override
    @Nullable
    public IItemHandlerModifiable getItemHandlerCap(@Nullable Direction side, boolean useCoverCapability) {
        return null; // 自动化只能通过大型焦炉仓
    }

    @Override
    @Nullable
    public IFluidHandlerModifiable getFluidHandlerCap(@Nullable Direction side, boolean useCoverCapability) {
        return null;
    }

    //////////////////////////////////////
    // ****** 结构校验与所有权 ******//
    //////////////////////////////////////

    /** 完整 7×7×5 包围范围涉及的区块是否全部可用。 */
    public boolean isStructureRangeLoaded() {
        if (getLevel() == null) return false;
        var back = getFrontFacing().getOpposite();
        BlockPos backCenterBottom = getPos().relative(back, 4);
        int minX = Math.min(getPos().getX(), backCenterBottom.getX()) - 1;
        int maxX = Math.max(getPos().getX(), backCenterBottom.getX()) + 1;
        int minZ = Math.min(getPos().getZ(), backCenterBottom.getZ()) - 1;
        int maxZ = Math.max(getPos().getZ(), backCenterBottom.getZ()) + 1;
        if (getFrontFacing().getAxis() == Direction.Axis.X) {
            minZ -= 4;
            maxZ += 4;
        } else {
            minX -= 4;
            maxX += 4;
        }
        for (int x = minX; x <= maxX; x += 15) {
            for (int z = minZ; z <= maxZ; z += 15) {
                if (!getLevel().isLoaded(new BlockPos(x, getPos().getY(), z))) return false;
            }
        }
        return getLevel().isLoaded(new BlockPos(maxX, getPos().getY(), maxZ));
    }

    @Override
    public boolean checkPattern() {
        // 1. 完整包围范围区块可用性: 不可用 = 无法完成权威结构验证 (暂停, 非失效)。
        if (!isStructureRangeLoaded()) {
            currentStructureDetail = "range_not_loaded";
            currentStructureDetailPos = null;
            getMultiblockState().setError(new PatternStringError("range_not_loaded"));
            return false;
        }
        // 2. 纯图案检查; 失败时给出首个错误坐标诊断。
        var pattern = getPattern();
        if (pattern == null || !pattern.checkPatternAt(getMultiblockState(), false)) {
            diagnoseFirstError();
            getMultiblockState().setError(new PatternStringError(
                    currentStructureDetail == null ? "invalid" : currentStructureDetail));
            return false;
        }
        // 3. 成型后校验: 大型焦炉仓数量、三种模式配额、仓朝向。
        String hatchError = validateHatches();
        if (hatchError != null) {
            currentStructureDetail = hatchError;
            currentStructureDetailPos = null;
            getMultiblockState().setError(new PatternStringError(hatchError));
            return false;
        }
        // 4. 结构独占与一格间距 (与普通焦炉互相判定) — 仅主线程: 异步探测线程
        // 禁止访问世界存档数据 (并发 computeIfAbsent 死循环风险); 最终裁决在
        // 成型回调的 claim 中完成。
        if (com.gregtechceu.gtceu.api.pattern.MultiblockWorldSavedData.isThreadService()) {
            return true;
        }
        if (getLevel() instanceof ServerLevel serverLevel) {
            var claim = CokeOvenWorldData.largeClaim(getPos(), getFrontFacing(), false);
            var conflict = CokeOvenWorldData.getOrCreate(serverLevel).findConflict(serverLevel, claim, this);
            if (conflict != null) {
                setConflict(conflict.type(), conflict.otherController());
                getMultiblockState().setError(new PatternStringError(conflict.type().langKey));
                return false;
            }
        }
        setConflict(null, null);
        currentStructureDetail = null;
        currentStructureDetailPos = null;
        return true;
    }

    /** 逐格诊断图案, 记录首个错误坐标与期望内容 (设计: 首个错误坐标)。 */
    private void diagnoseFirstError() {
        Direction front = getFrontFacing();
        Direction back = front.getOpposite();
        // 遍历顺序: 层 (下→上) → 深度 (正面→背面) → 宽度。控制器位于
        // 第 1 层正面中央; 世界原点 = 控制器。
        BlockPos frontCenter = getPos();
        for (int layer = 0; layer < LargeCokeOvenStructures.layerCount(); layer++) {
            for (int d = 0; d < LargeCokeOvenStructures.depth(); d++) {
                for (int w = 0; w < LargeCokeOvenStructures.width(); w++) {
                    char symbol = LargeCokeOvenStructures.symbolAt(layer, d, w);
                    if (symbol == '.') continue;
                    // 世界坐标: 宽度沿 front.getClockWise() (左→右 = +side),
                    // 深度沿 back, 高度沿 up。
                    BlockPos pos = frontCenter
                            .relative(back, d)
                            .relative(front.getClockWise(), w - (LargeCokeOvenStructures.width() - 1) / 2)
                            .above(layer);
                    BlockState state = getLevel().getBlockState(pos);
                    if (matchesSymbol(symbol, state)) continue;
                    currentStructureDetail = symbol == 'A' ? "air_blocked" : "invalid";
                    currentStructureDetailPos = pos.toShortString();
                    return;
                }
            }
        }
        currentStructureDetail = "invalid";
        currentStructureDetailPos = null;
    }

    private boolean matchesSymbol(char symbol, BlockState state) {
        var hatchBlock = com.hoshino.gregsteamexpansion.registry.GSEMachines.LARGE_COKE_OVEN_HATCH.getBlock();
        return switch (symbol) {
            case 'B', 'W' -> state.is(GTBlocks.CASING_COKE_BRICKS.get());
            case 'I' -> state.is(GTBlocks.CASING_COKE_BRICKS.get()) || state.is(hatchBlock);
            case 'A' -> state.isAir();
            case 'C' -> state.is(getBlockState().getBlock());
            default -> true; // '.' / ' '
        };
    }

    /** 大型焦炉仓数量 (3–5) 与三种模式配额 (各至少 1) 校验。 */
    @Nullable
    private String validateHatches() {
        int total = 0;
        int inputs = 0;
        int outputs = 0;
        int fluids = 0;
        for (var part : getMultiblockState().getMatchContext().getOrCreate("parts", java.util.Collections::emptySet)) {
            if (!(part instanceof LargeCokeOvenHatchPartMachine hatch)) continue;
            total++;
            switch (hatch.getMode()) {
                case ITEM_INPUT -> inputs++;
                case ITEM_OUTPUT -> outputs++;
                case FLUID_OUTPUT -> fluids++;
            }
        }
        if (total < 3) return "missing_hatch";
        if (inputs == 0 || outputs == 0 || fluids == 0) return "missing_mode";
        return null;
    }

    @Override
    public void onStructureFormed() {
        super.onStructureFormed();
        if (isRemote()) return;
        invalidFeedbackArmed = true; // 失效反馈资格在成功恢复成型后重置
        setConflict(null, null);
        currentStructureDetail = null;
        currentStructureDetailPos = null;
        if (getLevel() instanceof ServerLevel serverLevel) {
            var claim = CokeOvenWorldData.largeClaim(getPos(), getFrontFacing(), false);
            var result = CokeOvenWorldData.getOrCreate(serverLevel).claim(serverLevel, claim, null);
            if (result instanceof CokeOvenWorldData.ClaimResult.Failed failed) {
                setConflict(failed.conflict().type(), failed.conflict().otherController());
                onStructureInvalid();
            } else {
                syncClaimBox(claim);
            }
        }
    }

    /** 同步占用盒信息给客户端 (已归属砖探针使用)。 */
    private void syncClaimBox(CokeOvenWorldData.Claim claim) {
        StringBuilder sb = new StringBuilder("large|").append(claim.occupiedMin.asLong())
                .append('|').append(claim.occupiedMax.asLong());
        for (long extra : claim.extraCoords) {
            sb.append('|').append(extra);
        }
        syncedClaimBox = sb.toString();
        markDirty();
    }

    /**
     * 结构失效 (coke-ovens.md, 用户 2026-09-06 变更为回退语义): 批次进度、配方、
     * 并行、快照全部保存在 {@link LargeCokeOvenRecipeLogic} 自有持久化字段,
     * 上游 resetRecipeLogic 只清除其自有字段, 本方法无需额外保留逻辑。
     * 修复杂结构 + 释放占用竞争后自动从原精确进度继续。
     */
    /**
     * 结构失效 (用户 2026-09-06 变更): 进行中批次停止并把进度回退至 1 tick,
     * 修复结构后从回退处继续同一批次; 等待输出状态保留并继续提交重试。
     * 批次状态保存在 {@link LargeCokeOvenRecipeLogic} 自有持久化字段。
     */
    @Override
    public void onStructureInvalid() {
        boolean wasFormed = isFormed();
        super.onStructureInvalid();
        if (isRemote()) return;
        ovenLogic.rewindBatchForStructureInvalid();
        releaseClaim();
        if (!syncedClaimBox.isEmpty()) {
            syncedClaimBox = "";
            markDirty();
        }
        // 首次失效反馈: 一次"有效 → 无效"状态边沿触发; 持续无效期间的相邻更新、
        // 重复结构检查、区块重载、世界重载和服务器重启均不能重复播放; 结构范围
        // 未完全加载本身不播放; 控制器拆除使用自己的更明显反馈。
        if (wasFormed && invalidFeedbackArmed && isStructureRangeLoaded() && !removalSettled) {
            invalidFeedbackArmed = false;
            playStructureInvalidFeedback();
        }
        GregSteamExpansion.LOGGER.debug("[Large Coke Oven] structure invalid at {} (progress rewound to 1 tick)",
                getPos().toShortString());
    }

    /** 单次短促熄火声 + 首错坐标附近黑烟 (只作表现, 不附带库存损失或伤害)。 */
    private void playStructureInvalidFeedback() {
        if (!(getLevel() instanceof ServerLevel serverLevel)) return;
        serverLevel.playSound(null, getPos(), net.minecraft.sounds.SoundEvents.FIRE_EXTINGUISH,
                net.minecraft.sounds.SoundSource.BLOCKS, 0.8F, 0.7F);
        serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.LARGE_SMOKE,
                getPos().getX() + 0.5, getPos().getY() + 0.5, getPos().getZ() + 0.5,
                6, 0.4, 0.4, 0.4, 0.01);
        if (currentStructureDetailPos != null && !currentStructureDetailPos.isEmpty()) {
            String[] xyz = currentStructureDetailPos.replace("(", "").replace(")", "").split(", ");
            if (xyz.length == 3) {
                try {
                    int ex = Integer.parseInt(xyz[0].trim());
                    int ey = Integer.parseInt(xyz[1].trim());
                    int ez = Integer.parseInt(xyz[2].trim());
                    serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.LARGE_SMOKE,
                            ex + 0.5, ey + 0.5, ez + 0.5, 6, 0.3, 0.3, 0.3, 0.01);
                } catch (NumberFormatException ignored) {
                    // 坐标文本损坏: 跳过首错坐标粒子
                }
            }
        }
    }

    /** 结构独占竞争失败: 强制失效并给出原因, 不触发任何清空。 */
    @Override
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

    //////////////////////////////////////
    // ****** 控制器拆除结算 ******//
    //////////////////////////////////////

    /**
     * 控制器拆除是唯一执行批次取消与库存清理结算的事件 (一次性):
     * 六输入/六输出现有物品全部掉落; 批次未完成时额外掉落实际扣取的原始输入,
     * 已完成 (等待输出) 时额外掉落待输出固体; 全部现存与待输出流体直接丢失。
     */
    @Override
    public void onMachineRemoved() {
        if (!isRemote()) {
            removalSettled = true;
            boolean hadContent = !importItems.isEmpty() || !exportItems.isEmpty() ||
                    !exportFluids.isEmpty() || ovenLogic.hasActiveBatch();
            for (ItemStack stack : ovenLogic.takeDropStacksOnRemoval()) {
                Block.popResource(getLevel(), getPos(), stack);
                hadContent = true;
            }
            clearInventory(importItems.storage);
            clearInventory(exportItems.storage);
            exportFluids.getStorages()[0].setFluid(FluidStack.EMPTY);
            ovenLogic.discardBatchOnRemoval();
            if (hadContent) {
                playSpecialClearFeedback();
            }
            releaseClaim();
        }
    }

    /** 单次熄火声 + 黑烟 (炉体泄漏与熄灭的表现, 不生成真实流体或额外破坏)。 */
    private void playSpecialClearFeedback() {
        if (!(getLevel() instanceof ServerLevel serverLevel)) return;
        serverLevel.playSound(null, getPos(), net.minecraft.sounds.SoundEvents.FIRE_EXTINGUISH,
                net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 0.6F);
        serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.LARGE_SMOKE,
                getPos().getX() + 0.5, getPos().getY() + 0.5, getPos().getZ() + 0.5,
                12, 0.6, 0.6, 0.6, 0.01);
    }

    /**
     * 批次完成反馈 (一次性): 短促完成声 + 轻微烟气脉冲; 由批次唯一序号与
     * 已处理标志保证最多一次, 待输出重试/重载不重复。
     */
    public void playBatchCompletionFeedback() {
        if (!(getLevel() instanceof ServerLevel serverLevel)) return;
        serverLevel.playSound(null, getPos(), net.minecraft.sounds.SoundEvents.FURNACE_FIRE_CRACKLE,
                net.minecraft.sounds.SoundSource.BLOCKS, 0.8F, 0.7F);
        serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.LARGE_SMOKE,
                getPos().getX() + 0.5, getPos().getY() + 1.2, getPos().getZ() + 0.5,
                6, 0.3, 0.2, 0.3, 0.01);
    }

    //////////////////////////////////////
    // ****** 一氧化碳危害 ******//
    //////////////////////////////////////

    @Override
    public float getHazardStrengthPerOperation() {
        return pendingHazardStrength;
    }

    /** 进度首次到达完成点时按完成份数结算一次 (0.1 × p); 配置关闭时不积累。 */
    public void emitCarbonMonoxideHazard(float strength) {
        this.pendingHazardStrength = strength;
        spreadEnvironmentalHazard();
    }

    //////////////////////////////////////
    // ****** 每 5 tick 输出轮询 ******//
    //////////////////////////////////////

    /**
     * 控制器级主动输出轮询 (coke-ovens.md 已确认大型焦炉仓主动传输): 固定相对
     * 顺序"背面左、背面中、背面右、左侧、右侧"; 固体与流体各执行一次; 遇到
     * 完全不能接收的目标继续检查下一个, 找到第一个可接收目标完成一次传输后
     * 本类型立即结束。覆板过滤经仓特质照常生效。
     */
    private void pollOutputs() {
        if (!isFormed() || isRemote()) return;
        List<LargeCokeOvenHatchPartMachine> itemOutputs = new ArrayList<>();
        List<LargeCokeOvenHatchPartMachine> fluidOutputs = new ArrayList<>();
        collectOutputHatches(itemOutputs, fluidOutputs);

        if (!exportItems.isEmpty()) {
            for (var hatch : itemOutputs) {
                if (!GTTransferUtils.hasAdjacentItemHandler(getLevel(), hatch.self().getPos(),
                        hatch.getFrontFacing())) continue;
                hatch.outputInventory.exportToNearby(hatch.getFrontFacing());
                break; // 本轮固体最多一次传输
            }
        }
        if (!exportFluids.isEmpty()) {
            for (var hatch : fluidOutputs) {
                if (!GTTransferUtils.hasAdjacentFluidHandler(getLevel(), hatch.self().getPos(),
                        hatch.getFrontFacing())) continue;
                hatch.tank.exportToNearby(hatch.getFrontFacing());
                break; // 本轮流体最多一次传输
            }
        }
    }

    /** 按"背面左、背面中、背面右、左侧、右侧"排序收集两种输出模式仓。 */
    private void collectOutputHatches(List<LargeCokeOvenHatchPartMachine> itemOutputs,
                                      List<LargeCokeOvenHatchPartMachine> fluidOutputs) {
        Direction sideAxis = getFrontFacing().getClockWise();
        List<LargeCokeOvenHatchPartMachine> back = new ArrayList<>();
        LargeCokeOvenHatchPartMachine left = null;
        LargeCokeOvenHatchPartMachine right = null;
        for (var part : getParts()) {
            if (!(part instanceof LargeCokeOvenHatchPartMachine hatch)) continue;
            if (hatch.getMode() != CokeOvenMode.ITEM_OUTPUT && hatch.getMode() != CokeOvenMode.FLUID_OUTPUT) {
                continue;
            }
            int dSide = sideOffset(hatch.self().getPos());
            if (backOffset(hatch.self().getPos()) == 4) {
                back.add(hatch);
            } else if (dSide >= 3) {
                left = hatch; // 正面视角左侧 = +side
            } else if (dSide <= -3) {
                right = hatch;
            }
        }
        // 背面左→中→右: 正面视角左 = +side, 降序。
        back.sort((a, b) -> Integer.compare(sideOffset(b.self().getPos()), sideOffset(a.self().getPos())));
        for (var hatch : back) {
            if (hatch.getMode() == CokeOvenMode.ITEM_OUTPUT) itemOutputs.add(hatch);
            else fluidOutputs.add(hatch);
        }
        for (var edge : new LargeCokeOvenHatchPartMachine[] {left, right}) {
            if (edge == null) continue;
            if (edge.getMode() == CokeOvenMode.ITEM_OUTPUT) itemOutputs.add(edge);
            else fluidOutputs.add(edge);
        }
    }

    private int backOffset(BlockPos pos) {
        Direction back = getFrontFacing().getOpposite();
        var rel = pos.subtract(getPos());
        return rel.getX() * back.getStepX() + rel.getZ() * back.getStepZ();
    }

    private int sideOffset(BlockPos pos) {
        Direction side = getFrontFacing().getClockWise();
        var rel = pos.subtract(getPos());
        return rel.getX() * side.getStepX() + rel.getZ() * side.getStepZ();
    }

    //////////////////////////////////////
    // ****** 状态模型与同步 ******//
    //////////////////////////////////////

    private void onSharedInventoriesChanged() {
        ovenLogic.markPendingDirty();
    }

    private void tickStatusSync() {
        OvenStatus computed = computeOvenStatus();
        String detail = currentStructureDetail == null ? "" : currentStructureDetail;
        String detailPos = currentStructureDetailPos == null ? "" : currentStructureDetailPos;
        String conflictType = currentConflictType == null ? "" : currentConflictType.name();
        String conflictPos = currentConflictPos == null ? "" : currentConflictPos.toShortString();
        String pending = buildPendingSummary();
        if (computed != syncedStatus || !detail.equals(syncedStructureDetail) ||
                !detailPos.equals(syncedStructureDetailPos) || !conflictType.equals(syncedConflictType) ||
                !conflictPos.equals(syncedConflictPos) || !pending.equals(syncedPendingSummary)) {
            syncedStatus = computed;
            syncedStructureDetail = detail;
            syncedStructureDetailPos = detailPos;
            syncedConflictType = conflictType;
            syncedConflictPos = conflictPos;
            syncedPendingSummary = pending;
            markDirty();
        }
    }

    private OvenStatus computeOvenStatus() {
        if (!isStructureRangeLoaded()) return OvenStatus.RANGE_NOT_LOADED;
        if (!isFormed()) return OvenStatus.STRUCTURE_INVALID;
        if (ovenLogic.isAwaitingReInput()) return OvenStatus.AWAITING_REINPUT;
        if (ovenLogic.isWaitingOutput()) return OvenStatus.WAITING_OUTPUT;
        if (ovenLogic.hasActiveBatch()) return OvenStatus.WORKING;
        return computeIdleStatus();
    }

    private OvenStatus computeIdleStatus() {
        return switch (ovenLogic.getLastSelectionOutcome()) {
            case STARTUP_OUTPUT_BLOCKED -> OvenStatus.STARTUP_OUTPUT_BLOCKED;
            case NO_CANDIDATE -> OvenStatus.INPUT_INVALID;
            case INSUFFICIENT -> OvenStatus.INPUT_INSUFFICIENT;
            case NONE -> hasAnyInput() ? OvenStatus.READY : OvenStatus.IDLE;
        };
    }

    private boolean hasAnyInput() {
        for (ItemStack stack : getImportStacks()) {
            if (!stack.isEmpty()) return true;
        }
        return false;
    }

    private String buildPendingSummary() {
        if (!ovenLogic.isWaitingOutput()) return "";
        StringBuilder sb = new StringBuilder();
        for (ItemStack stack : ovenLogic.getPendingItems()) {
            if (stack.isEmpty()) continue;
            if (sb.length() > 0) sb.append(", ");
            sb.append(stack.getHoverName().getString()).append(" x").append(stack.getCount());
        }
        for (FluidStack stack : ovenLogic.getPendingFluids()) {
            if (stack.isEmpty()) continue;
            if (sb.length() > 0) sb.append(", ");
            sb.append(stack.getDisplayName().getString()).append(" ").append(stack.getAmount()).append(" mB");
        }
        return sb.toString();
    }

    public OvenStatus getOvenStatus() {
        return syncedStatus;
    }

    /** 已归属砖探针数据源 (客户端读取)。 */
    public String getSyncedClaimBox() {
        return syncedClaimBox;
    }

    /** Jade / GUI 读取批次引擎 (构造于 createRecipeLogic)。 */
    public LargeCokeOvenRecipeLogic getOvenLogic() {
        return ovenLogic;
    }

    public Component getStatusText() {
        return Component.translatable(getOvenStatus().langKey);
    }

    /** GUI/Jade 详细原因: 同时列出全部实际阻塞情况 (悬浮提示)。 */
    public List<Component> getStatusDetails() {
        List<Component> details = new ArrayList<>();
        if (!syncedConflictType.isEmpty()) {
            var type = "OVERLAP".equals(syncedConflictType)
                    ? CokeOvenWorldData.ConflictType.OVERLAP : CokeOvenWorldData.ConflictType.TOO_CLOSE;
            details.add(Component.translatable(type.langKey, syncedConflictPos));
        }
        switch (syncedStructureDetail.isEmpty() ? "" : syncedStructureDetail) {
            case "range_not_loaded" -> details.add(Component.translatable(
                    "gregsteamexpansion.large_coke_oven.detail.range_not_loaded"));
            case "air_blocked" -> details.add(Component.translatable(
                    "gregsteamexpansion.large_coke_oven.detail.air_blocked", syncedStructureDetailPos));
            case "invalid" -> details.add(Component.translatable(
                    "gregsteamexpansion.large_coke_oven.detail.first_error", syncedStructureDetailPos));
            case "missing_hatch" -> details.add(Component.translatable(
                    "gregsteamexpansion.large_coke_oven.detail.missing_hatch"));
            case "missing_mode" -> details.add(Component.translatable(
                    "gregsteamexpansion.large_coke_oven.detail.missing_mode"));
            default -> {}
        }
        switch (getOvenStatus()) {
            case WAITING_OUTPUT -> {
                details.add(Component.translatable(
                        "gregsteamexpansion.large_coke_oven.detail.waiting_output"));
                if (!syncedPendingSummary.isEmpty()) {
                    details.add(Component.translatable(
                            "gregsteamexpansion.large_coke_oven.detail.pending", syncedPendingSummary));
                }
            }
            case STARTUP_OUTPUT_BLOCKED -> details.add(Component.translatable(
                    "gregsteamexpansion.large_coke_oven.detail.startup_blocked"));
            case AWAITING_REINPUT -> details.add(Component.translatable(
                    "gregsteamexpansion.large_coke_oven.detail.awaiting_reinput"));
            default -> {}
        }
        // 优先配方仅在附加提示标注, 不覆盖当前批次显示。
        if (ovenLogic.getPreferredRecipeId() != null &&
                !ovenLogic.getPreferredRecipeId().equals(ovenLogic.getBatchRecipeId())) {
            details.add(Component.translatable("gregsteamexpansion.large_coke_oven.detail.preferred",
                    ovenLogic.getPreferredRecipeId().toString()));
        }
        return details;
    }

    public String getStatusId() {
        return switch (getOvenStatus()) {
            case RANGE_NOT_LOADED -> "range_not_loaded";
            case STRUCTURE_INVALID -> "invalid_structure";
            case AWAITING_REINPUT -> "awaiting_reinput";
            case WAITING_OUTPUT -> "waiting_output";
            case WORKING -> "working";
            case STARTUP_OUTPUT_BLOCKED -> "startup_output_blocked";
            case INPUT_INVALID -> "input_invalid";
            case INPUT_INSUFFICIENT -> "input_insufficient";
            case READY -> "ready";
            case IDLE -> "idle";
        };
    }

    //////////////////////////////////////
    // ****** 模式/朝向锁 (供仓查询) ******//
    //////////////////////////////////////

    /** 正在推进批次或持有待输出快照时禁止切换仓模式/旋转仓。 */
    public boolean isModeSwitchLocked() {
        return isFormed() && ovenLogic.isBatchActiveOrPending();
    }

    /** 结构中各模式仓数量 (配额校验: 切换后三种模式各至少一个)。 */
    public int[] countHatchModes() {
        int[] counts = new int[CokeOvenMode.values().length];
        for (var part : getParts()) {
            if (part instanceof LargeCokeOvenHatchPartMachine hatch) {
                counts[hatch.getMode().ordinal()]++;
            }
        }
        return counts;
    }

    //////////////////////////////////////
    // ****** 库存访问 (GUI/仓代理) ******//
    //////////////////////////////////////

    public ItemStack getImportStack(int slot) {
        return importItems.storage.getStackInSlot(slot);
    }

    public ItemStack[] getImportStacks() {
        ItemStack[] stacks = new ItemStack[importItems.getSlots()];
        for (int i = 0; i < stacks.length; i++) {
            stacks[i] = importItems.storage.getStackInSlot(i);
        }
        return stacks;
    }

    public FluidStack getExportFluid() {
        return exportFluids.getStorages()[0].getFluid();
    }

    //////////////////////////////////////
    // ****** 正式 GUI (212×208) ******//
    //////////////////////////////////////

    /**
     * GUI: 顶部只读状态栏 (悬浮列出全部详细原因); 3×2 编号输入槽 →
     * 配方进度 → 3×2 输出槽 → 64,000 mB 流体罐; 配方信息行 (配方 / 并行 p/6 /
     * 进度 / 百分比 / 预计剩余); 待提交产物仅在"等待输出"详情中显示。
     * 标准 GTCEu 启停: 左下角固定电源按钮 (用户 2026-09-06 变更)。
     */
    @Override
    public ModularUI createUI(Player entityPlayer) {
        var ui = new ModularUI(212, 208, this, entityPlayer)
                .background(GuiTextures.BACKGROUND);
        ui.widget(new LabelWidget(5, 5, getBlockState().getBlock().getDescriptionId()));
        // 状态栏: 动态悬浮 (每帧刷新详细原因)。
        var statusBar = new LabelWidget(5, 17, this::getStatusLine) {
            @Override
            public void drawInForeground(net.minecraft.client.gui.GuiGraphics graphics, int mouseX,
                                         int mouseY, float partialTicks) {
                setHoverTooltips(getStatusDetails());
                super.drawInForeground(graphics, mouseX, mouseY, partialTicks);
            }
        };
        ui.widget(statusBar);

        for (int slot = 0; slot < INPUT_SLOTS; slot++) {
            int row = slot / 3;
            int col = slot % 3;
            ui.widget(new SlotWidget(importItems.storage, slot, 7 + col * 18, 36 + row * 18, true, true)
                    .setHoverTooltips(Component.translatable(
                            "gregsteamexpansion.large_coke_oven.gui.slot_number", slot + 1)));
        }
        ui.widget(new ProgressWidget(
                () -> ovenLogic.getBatchTotalDuration() == 0 ? 0.0
                        : ovenLogic.getBatchProgress() / (double) ovenLogic.getBatchTotalDuration(),
                78, 45, 24, 17, GuiTextures.PROGRESS_BAR_ARROW)
                .setDynamicHoverTips(p -> getProgressHoverText(p)));
        for (int slot = 0; slot < OUTPUT_SLOTS; slot++) {
            int row = slot / 3;
            int col = slot % 3;
            ui.widget(new SlotWidget(exportItems.storage, slot, 121 + col * 18, 36 + row * 18, true, false)
                    .setHoverTooltips(Component.translatable(
                            "gregsteamexpansion.large_coke_oven.gui.slot_number", INPUT_SLOTS + slot + 1)));
        }
        ui.widget(new TankWidget(exportFluids.getStorages()[0], 186, 22, 20, 58, true, false)
                .setBackground(GuiTextures.FLUID_TANK_BACKGROUND)
                .setFillDirection(ProgressTexture.FillDirection.DOWN_TO_UP)
                .setShowAmountOverlay(false));
        // 配方信息行: 配方 / 并行 p/6 / 进度 / 百分比 / 预计剩余时间。
        var recipeLine = new LabelWidget(5, 84, this::getRecipeInfoLine) {
            @Override
            public void drawInForeground(net.minecraft.client.gui.GuiGraphics graphics, int mouseX,
                                         int mouseY, float partialTicks) {
                setHoverTooltips(getStatusDetails());
                super.drawInForeground(graphics, mouseX, mouseY, partialTicks);
            }
        };
        ui.widget(recipeLine);
        ui.widget(UITemplate.bindPlayerInventory(entityPlayer.getInventory(), GuiTextures.SLOT, 25, 126, true));
        // 标准 GTCEu 电源按钮: 固定在玩家背包区之外, 委托 RecipeLogic 启停。
        ui.widget(new com.gregtechceu.gtceu.api.gui.widget.ToggleButtonWidget(6, 186, 18, 18,
                GuiTextures.BUTTON_POWER, this::isWorkingEnabled, this::setWorkingEnabled));
        return ui;
    }

    private String getStatusLine() {
        String status = getStatusText().getString();
        int details = getStatusDetails().size();
        return details > 0 ? status + " (" + details + ")" : status;
    }

    private String getRecipeInfoLine() {
        var recipeId = ovenLogic.getBatchRecipeId();
        if (recipeId == null) {
            return Component.translatable("gregsteamexpansion.large_coke_oven.gui.no_batch").getString();
        }
        double percent = ovenLogic.getBatchTotalDuration() == 0 ? 0 :
                Math.round(ovenLogic.getBatchProgress() * 1000.0 / ovenLogic.getBatchTotalDuration()) / 10.0;
        String eta;
        if (ovenLogic.isWaitingOutput()) {
            eta = Component.translatable("gregsteamexpansion.large_coke_oven.gui.waiting_output").getString();
        } else {
            int remaining = Math.max(0, ovenLogic.getBatchTotalDuration() - ovenLogic.getBatchProgress());
            eta = FormattingUtil.formatNumbers(remaining / 20.0) + "s";
        }
        return Component.translatable("gregsteamexpansion.large_coke_oven.gui.recipe_line",
                recipeId.getPath(),
                ovenLogic.getBatchParallel() + "/" + LargeCokeOvenRecipeLogic.MAX_PARALLEL,
                ovenLogic.getBatchProgress() + "/" + ovenLogic.getBatchTotalDuration(),
                percent + "%", eta).getString();
    }

    private String getProgressHoverText(double percent) {
        if (ovenLogic.getBatchTotalDuration() == 0) {
            return Component.translatable("gregsteamexpansion.large_coke_oven.gui.progress.idle",
                    String.format("%.0f%%", percent * 100)).getString();
        }
        int remaining = Math.max(0, ovenLogic.getBatchTotalDuration() - ovenLogic.getBatchProgress());
        return Component.translatable("gregsteamexpansion.large_coke_oven.gui.progress",
                String.format("%.0f%%", percent * 100),
                FormattingUtil.formatNumbers(remaining / 20.0) + "s").getString();
    }

    //////////////////////////////////////
    // ****** 粒子表现 (整台预算) ******//
    //////////////////////////////////////

    /**
     * 只有实际推进进度时从三个炉门生成火焰与烟雾; 粒子数量按整台机器设置总
     * 预算 (每 tick 最多 1 个, 在三个炉门之间随机分配), 不让每个炉门各自复制
     * 一台普通焦炉的完整粒子密度。空闲、堵塞、等待输出与失效状态不生成。
     */
    @Override
    public void animateTick(net.minecraft.util.RandomSource random) {
        if (getOvenStatus() != OvenStatus.WORKING) return;
        if (!(getLevel() instanceof net.minecraft.client.multiplayer.ClientLevel level)) return;
        Direction front = getFrontFacing();
        Direction side = front.getClockWise();
        // 从三个炉室随机选一个, 炉室 3 行中随机选一格。
        int chamber = CHAMBER_OFFSETS[random.nextInt(CHAMBER_OFFSETS.length)];
        int row = 1 + random.nextInt(3);
        var pos = getPos()
                .relative(side, chamber)
                .above(row)
                .relative(front, 0);
        float x = pos.getX() + 0.5f + (random.nextFloat() - 0.5f) * 0.4f;
        float y = pos.getY() + 0.3f + random.nextFloat() * 0.3f;
        float z = pos.getZ() + 0.5f + (random.nextFloat() - 0.5f) * 0.4f;
        // 火焰出烟口沿正面墙面向外偏移半格。
        x += front.getStepX() * 0.52f;
        z += front.getStepZ() * 0.52f;
        if (com.gregtechceu.gtceu.config.ConfigHolder.INSTANCE.machines.machineSounds &&
                random.nextDouble() < 0.08) {
            level.playLocalSound(x, y, z, net.minecraft.sounds.SoundEvents.FURNACE_FIRE_CRACKLE,
                    net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 1.0F, false);
        }
        level.addParticle(net.minecraft.core.particles.ParticleTypes.LARGE_SMOKE, x, y, z, 0, 0, 0);
        if (random.nextDouble() < 0.4) {
            level.addParticle(net.minecraft.core.particles.ParticleTypes.FLAME, x, y, z, 0, 0, 0);
        }
    }

    /** 三炉室宽度偏移 (渲染与粒子共用)。 */
    private static final int[] CHAMBER_OFFSETS = {-2, 0, 2};

    //////////////////////////////////////
    // ****** 手动流体抽取 (只出不进) ******//
    //////////////////////////////////////

    @Override
    public InteractionResult onUse(BlockState state, net.minecraft.world.level.Level world, BlockPos pos,
                                   Player player, InteractionHand hand, BlockHitResult hit) {
        if (!isRemote()) {
            if (super.onUse(state, world, pos, player, hand, hit) == InteractionResult.SUCCESS) {
                return InteractionResult.SUCCESS;
            }
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

    //////////////////////////////////////
    // ****** 标准 GTCEu 启停控制 ******//
    //////////////////////////////////////

    // 软锤与 GUI 电源按钮使用标准 GTCEu 控制语义 (用户 2026-09-06 变更):
    // isWorkingEnabled/setWorkingEnabled 由 IRecipeLogicMachine 默认实现委托给
    // RecipeLogic, 软锤走上游 onSoftMalletClick, 无需额外覆写。
    // 批次进行中暂停 = 本批完成后挂起; 空闲时暂停 = 立即挂起。

    //////////////////////////////////////
    // ****** 冲突状态记录 ******//
    //////////////////////////////////////

    private void setConflict(@Nullable CokeOvenWorldData.ConflictType type, @Nullable BlockPos other) {
        currentConflictType = type;
        currentConflictPos = other;
        syncedConflictType = type == null ? "" : type.name();
        syncedConflictPos = other == null ? "" : other.toShortString();
    }

    /** Jade 备用。 */
    public int getRemainingTicks() {
        if (!ovenLogic.hasActiveBatch() || ovenLogic.isWaitingOutput()) return -1;
        return Math.max(0, ovenLogic.getBatchTotalDuration() - ovenLogic.getBatchProgress());
    }
}
