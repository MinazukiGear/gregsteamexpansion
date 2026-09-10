package com.hoshino.gregsteamexpansion.structure;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 结构诊断结果 (structure-diagnostics.md 三、诊断信息分级 L1–L4)。
 *
 * <p>由 {@link StructureDiagnostics} 从 GTCEu 的 {@code MultiblockState.error}
 * 派生, 是"为什么没成型"的结构化答案: L1 = {@link Kind}, L2 = {@link #pos},
 * L3 = {@link #expected} (已截断) 与 {@link #expectedTotal}, L4 = {@link #limitType}
 * 与 {@link #limitNumber}。
 *
 * <p>实例可序列化为 NBT 以便 Jade 的服务端数据通道把诊断送到客户端 —— 这是
 * 必需的, 因为 GTCEu 的结构检查只在服务端异步线程执行, 客户端的
 * {@code MultiblockState} 永远是初始态 (见设计文档 1.1)。序列化只携带
 * {@link ItemStack}/坐标这类结构化数据, 绝不携带已解析的字符串: 方块名必须
 * 由客户端按自己的语言渲染 (设计文档 R6)。
 */
public record StructureProblem(
        Kind kind,
        @Nullable BlockPos pos,
        List<ItemStack> expected,
        int expectedTotal,
        int limitType,
        int limitNumber,
        @Nullable String rawKey) {

    /** 非数量类错误的 {@link #limitType}/{@link #limitNumber} 占位值。 */
    public static final int NO_LIMIT = -1;

    /** L3 候选默认截断阈值 (P2: 4 个 + "等 N 种")。 */
    public static final int MAX_EXPECTED = 4;

    private static final String TAG_POS = "pos";
    private static final String TAG_KIND = "kind";
    private static final String TAG_EXPECTED = "expected";
    private static final String TAG_EXPECTED_TOTAL = "expectedTotal";
    private static final String TAG_LIMIT_TYPE = "limitType";
    private static final String TAG_LIMIT_NUMBER = "limitNumber";
    private static final String TAG_RAW_KEY = "rawKey";

    public enum Kind {
        /** 某个位置不是期望的方块 (缺失 / 放错)。对应 {@code PatternError}。 */
        MISSING_OR_WRONG,
        /** 位置都对了, 但某类方块的数量不满足上下限。对应 {@code SinglePredicateError}。 */
        COUNT_LIMIT,
        /** 同类方块必须一致 (线圈 / 过滤器 / 电池)。对应带这些键的 {@code PatternStringError}。 */
        INCONSISTENT,
        /** 结构所在区块未加载。 */
        CHUNK_UNLOADED,
        /** 尚未跑过结构校验 (刚放置 / 刚被改动, 异步检查按 4 tick 周期)。 */
        UNINITIALIZED,
        /** 未识别的引擎错误, 只透传 {@link #rawKey}。 */
        UNKNOWN
    }

    public StructureProblem {
        expected = List.copyOf(expected);
    }

    /** 候选是否被截断 (需要补一句"等 N 种")。 */
    public boolean hasMoreExpected() {
        return expectedTotal > expected.size();
    }

    public boolean hasPosition() {
        return pos != null;
    }

    public boolean hasLimit() {
        return limitType != NO_LIMIT;
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString(TAG_KIND, kind.name());
        if (pos != null) {
            tag.putLong(TAG_POS, pos.asLong());
        }
        ListTag stacks = new ListTag();
        for (ItemStack stack : expected) {
            stacks.add(stack.save(new CompoundTag()));
        }
        tag.put(TAG_EXPECTED, stacks);
        tag.putInt(TAG_EXPECTED_TOTAL, expectedTotal);
        tag.putInt(TAG_LIMIT_TYPE, limitType);
        tag.putInt(TAG_LIMIT_NUMBER, limitNumber);
        if (rawKey != null) {
            tag.putString(TAG_RAW_KEY, rawKey);
        }
        return tag;
    }

    @Nullable
    public static StructureProblem fromTag(CompoundTag tag) {
        Kind kind;
        try {
            kind = Kind.valueOf(tag.getString(TAG_KIND));
        } catch (IllegalArgumentException e) {
            return null;
        }
        BlockPos pos = tag.contains(TAG_POS, Tag.TAG_LONG) ? BlockPos.of(tag.getLong(TAG_POS)) : null;
        ListTag stacks = tag.getList(TAG_EXPECTED, Tag.TAG_COMPOUND);
        List<ItemStack> expected = new ArrayList<>(stacks.size());
        for (int i = 0; i < stacks.size(); i++) {
            ItemStack stack = ItemStack.of(stacks.getCompound(i));
            if (!stack.isEmpty()) {
                expected.add(stack);
            }
        }
        String rawKey = tag.contains(TAG_RAW_KEY, Tag.TAG_STRING) ? tag.getString(TAG_RAW_KEY) : null;
        return new StructureProblem(kind, pos, expected,
                tag.getInt(TAG_EXPECTED_TOTAL), tag.getInt(TAG_LIMIT_TYPE), tag.getInt(TAG_LIMIT_NUMBER), rawKey);
    }
}
