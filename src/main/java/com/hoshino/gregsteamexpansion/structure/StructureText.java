package com.hoshino.gregsteamexpansion.structure;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * 结构诊断的文案装配 (structure-diagnostics.md)。
 *
 * <p>Jade 原因行与 {@code /gse structure} 指令共用这里的映射, 避免两处各自
 * 维护一份 kind → 文案的 switch 而产生漂移。
 *
 * <p>只有一律在**客户端**渲染才安全: {@link Component#translatable} 是惰性的,
 * 名字在玩家自己的语言下解析。反之, 已经 {@code getString()} 过的字符串一旦
 * 跨网络就会固化成服务端语言 (设计文档 R6)。
 */
public final class StructureText {

    private StructureText() {}

    /** L1 类别 → 可读原因。不一致类直接透传引擎给的键 (本来就是完整句子)。 */
    public static Component reason(StructureProblem problem) {
        return switch (problem.kind()) {
            case MISSING_OR_WRONG ->
                    Component.translatable("gregsteamexpansion.structure.problem.missing");
            case COUNT_LIMIT -> limitText(problem);
            case CHUNK_UNLOADED ->
                    Component.translatable("gregsteamexpansion.structure.problem.chunk_unloaded");
            case UNINITIALIZED ->
                    Component.translatable("gregsteamexpansion.structure.problem.uninitialized");
            case INCONSISTENT, UNKNOWN -> problem.rawKey() != null
                    ? Component.translatable(problem.rawKey())
                    : Component.translatable("gregsteamexpansion.structure.problem.unknown");
        };
    }

    /**
     * L4 阈值 → 数量类文案。{@code type} 口径与上游
     * {@code SinglePredicateError} 一致: 0=max, 1=min, 2=maxLayer, 3=minLayer。
     */
    private static Component limitText(StructureProblem problem) {
        String kind = switch (problem.limitType()) {
            case 0 -> "max";
            case 1 -> "min";
            case 2 -> "max_layer";
            case 3 -> "min_layer";
            default -> "min";
        };
        return Component.translatable("gregsteamexpansion.structure.problem.count." + kind,
                problem.limitNumber());
    }

    /** L3: 候选名按客户端语言拼接, 被截断时补"等 N 种" (P2)。返回不含前缀的片段。 */
    public static Component expectedNames(StructureProblem problem) {
        String separator = Component.translatable("gregsteamexpansion.structure.list_separator").getString();
        StringBuilder names = new StringBuilder();
        for (ItemStack stack : problem.expected()) {
            if (names.length() > 0) {
                names.append(separator);
            }
            names.append(stack.getHoverName().getString());
        }
        if (problem.hasMoreExpected()) {
            return Component.translatable("gregsteamexpansion.structure.expected.more", names.toString(),
                    problem.expectedTotal() - problem.expected().size());
        }
        return Component.literal(names.toString());
    }
}
