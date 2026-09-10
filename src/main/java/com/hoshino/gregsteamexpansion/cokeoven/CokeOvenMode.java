package com.hoshino.gregsteamexpansion.cokeoven;

import net.minecraft.util.StringRepresentable;

import org.jetbrains.annotations.NotNull;

/**
 * 可配置焦炉仓的三种互斥模式 (coke-ovens.md 可配置焦炉仓):
 * 物品输入 / 物品输出 / 流体输出。新放置与旧存档迁移的默认模式都是物品输入。
 *
 * <p>模式名有两个命名空间: 普通焦炉仓与大型焦炉仓各自持有独立文案。
 * 早期实现把 {@code gregsteamexpansion.coke_oven_hatch.mode.*} 写死在枚举里,
 * 大型焦炉仓复用同一枚举后螺丝刀切换提示会退化成未翻译的原始键。</p>
 */
public enum CokeOvenMode implements StringRepresentable {
    ITEM_INPUT("item_input", "coke_oven_hatch"),
    ITEM_OUTPUT("item_output", "coke_oven_hatch"),
    FLUID_OUTPUT("fluid_output", "coke_oven_hatch");

    public static final CokeOvenMode DEFAULT = ITEM_INPUT;

    /** 大型焦炉仓的模式文案命名空间 (与普通焦炉仓分开维护)。 */
    public static final String LARGE_KEY_PREFIX = "large_coke_oven_hatch";

    private final String name;
    private final String keyPrefix;

    CokeOvenMode(String name, String keyPrefix) {
        this.name = name;
        this.keyPrefix = keyPrefix;
    }

    @Override
    public @NotNull String getSerializedName() {
        return name;
    }

    /** 螺丝刀固定循环: 物品输入 → 物品输出 → 流体输出 → 物品输入。 */
    public CokeOvenMode next() {
        return switch (this) {
            case ITEM_INPUT -> ITEM_OUTPUT;
            case ITEM_OUTPUT -> FLUID_OUTPUT;
            case FLUID_OUTPUT -> ITEM_INPUT;
        };
    }

    public String getTranslationKey() {
        return translationKey(keyPrefix);
    }

    /**
     * 按仓体命名空间取模式名。普通仓传 {@link #getKeyPrefix()}, 大型仓传
     * {@link #LARGE_KEY_PREFIX}。
     */
    public String translationKey(String prefix) {
        return "gregsteamexpansion." + prefix + ".mode." + name;
    }

    public String getKeyPrefix() {
        return keyPrefix;
    }
}
