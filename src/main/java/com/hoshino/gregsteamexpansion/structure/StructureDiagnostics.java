package com.hoshino.gregsteamexpansion.structure;

import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.pattern.MultiblockState;
import com.gregtechceu.gtceu.api.pattern.error.PatternError;
import com.gregtechceu.gtceu.api.pattern.error.PatternStringError;
import com.gregtechceu.gtceu.api.pattern.error.SinglePredicateError;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;

/**
 * 结构诊断抽取器 (structure-diagnostics.md 五、接线方式 W1)。
 *
 * <p>从 GTCEu 已经算好的 {@code MultiblockState.error} 读出"为什么没成型"。
 * 全部依赖的方法 ({@code IMultiController#getMultiblockState()},
 * {@code PatternError#getPos()} / {@code getCandidates()}) 都是 public, 因此
 * 不需要 mixin 也不需要改动任何一台控制器 —— 一处实现覆盖全部多方块机器。
 *
 * <p>🚫 三条硬性约束 (设计文档 1.1 / R3):
 * <ul>
 *   <li>{@code UNINIT_ERROR} 是 {@code MultiblockState} 构造时的直接赋值,
 *       没有经过 {@code setError()} ⇒ 它的 {@code worldState} 为 null,
 *       调 {@code getPos()} / {@code getCandidates()} 会 NPE。必须先按类型分派。</li>
 *   <li>诊断只在**服务端**有意义: 结构检查跑在异步线程, 客户端从不执行。</li>
 *   <li>{@code error} 只记录**首个**失败点, 且按朝向循环后会保留最后一次尝试的
 *       失败位置。调用方必须按"首个问题"措辞, 不能暗示这是全部问题。</li>
 * </ul>
 */
public final class StructureDiagnostics {

    private StructureDiagnostics() {}

    /** 与 {@code gtceu.multiblock.pattern.error.coils/filters/batteries} 对应的不一致类错误。 */
    private static final List<String> INCONSISTENT_KEYS = List.of(
            "gtceu.multiblock.pattern.error.coils",
            "gtceu.multiblock.pattern.error.filters",
            "gtceu.multiblock.pattern.error.batteries");

    /** 默认截断阈值 (P2) 的描述, 见 {@link StructureProblem#MAX_EXPECTED}。 */
    public static Optional<StructureProblem> describe(IMultiController controller) {
        return describe(controller, StructureProblem.MAX_EXPECTED);
    }

    /**
     * @param maxExpected 候选方块上限; 传 {@link Integer#MAX_VALUE} 取全量 (调试指令用)。
     */
    public static Optional<StructureProblem> describe(IMultiController controller, int maxExpected) {
        if (controller.isFormed()) {
            // 成型后引擎已 setError(null); 这里再挡一次, 含义是"结构有效就不报原因"。
            return Optional.empty();
        }
        MultiblockState state = controller.getMultiblockState();
        PatternError error = state.error;
        if (error == null) {
            return Optional.empty();
        }
        // 🚫 必须先判 UNINIT: 它的 worldState 为 null, 任何 getPos()/getCandidates() 都会 NPE。
        if (error == MultiblockState.UNINIT_ERROR) {
            return Optional.of(new StructureProblem(StructureProblem.Kind.UNINITIALIZED, null,
                    List.of(), 0, StructureProblem.NO_LIMIT, 0, null));
        }
        if (error == MultiblockState.UNLOAD_ERROR) {
            return Optional.of(new StructureProblem(StructureProblem.Kind.CHUNK_UNLOADED, safePos(error),
                    List.of(), 0, StructureProblem.NO_LIMIT, 0, null));
        }
        if (error instanceof SinglePredicateError single) {
            // 位置都匹配, 但某类方块数量超过 / 不足上下限。type: 0=max, 1=min,
            // 2=maxLayer, 3=minLayer (与上游 SinglePredicateError#getErrorInfo 同口径)。
            List<ItemStack> candidates = flatten(single.getCandidates());
            return Optional.of(new StructureProblem(StructureProblem.Kind.COUNT_LIMIT, safePos(error),
                    truncate(candidates, maxExpected), candidates.size(),
                    single.type, limitNumber(single), null));
        }
        if (error instanceof PatternStringError stringError) {
            StructureProblem.Kind kind = INCONSISTENT_KEYS.contains(stringError.translateKey)
                    ? StructureProblem.Kind.INCONSISTENT
                    : StructureProblem.Kind.UNKNOWN;
            return Optional.of(new StructureProblem(kind, safePos(error),
                    List.of(), 0, StructureProblem.NO_LIMIT, 0, stringError.translateKey));
        }
        // 默认: 某个位置不是期望的方块 (缺失或放错)。
        List<ItemStack> candidates = flatten(error.getCandidates());
        return Optional.of(new StructureProblem(StructureProblem.Kind.MISSING_OR_WRONG, safePos(error),
                truncate(candidates, maxExpected), candidates.size(),
                StructureProblem.NO_LIMIT, 0, null));
    }

    /**
     * 数量类错误的阈值, 与上游 {@code SinglePredicateError#getErrorInfo()} 同口径
     * (上游只把该值用于文案, 没有暴露成字段, 这里按同样的 switch 复算一次)。
     */
    private static int limitNumber(SinglePredicateError error) {
        return switch (error.type) {
            case 0 -> error.predicate.maxCount;
            case 1 -> error.predicate.minCount;
            case 2 -> error.predicate.maxLayerCount;
            case 3 -> error.predicate.minLayerCount;
            default -> StructureProblem.NO_LIMIT;
        };
    }

    /**
     * 抽取器对外的统一位置读取点。只有 {@code UNINIT_ERROR} 的 {@code worldState}
     * 为 null, 调用方已在前面把它分流走, 所以到这里位置一定可读。
     */
    @Nullable
    private static BlockPos safePos(PatternError error) {
        try {
            return error.getPos();
        } catch (NullPointerException e) {
            // 防御: 若未来上游引入新的"未绑定 worldState"的错误类型, 宁可少给一个
            // 坐标也不要让 Jade / 指令崩在渲染路径上。
            return null;
        }
    }

    private static List<ItemStack> truncate(List<ItemStack> candidates, int maxExpected) {
        if (maxExpected < 0 || candidates.size() <= maxExpected) {
            return candidates;
        }
        return List.copyOf(candidates.subList(0, maxExpected));
    }

    /**
     * 展平 {@code List<List<ItemStack>>} (每个谓词一组候选) 并按物品+组件去重。
     * {@code copyWithCount(1)} 让 {@link ItemStack#equals} 退化成语义等价判断
     * (不把堆叠数量算作差异)。
     */
    private static List<ItemStack> flatten(List<List<ItemStack>> groups) {
        LinkedHashMap<ItemStack, ItemStack> unique = new LinkedHashMap<>();
        for (List<ItemStack> group : groups) {
            for (ItemStack stack : group) {
                if (stack.isEmpty()) {
                    continue;
                }
                ItemStack key = stack.copyWithCount(1);
                unique.putIfAbsent(key, key);
            }
        }
        return new ArrayList<>(unique.values());
    }
}
