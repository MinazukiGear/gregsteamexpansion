package com.hoshino.gregsteamexpansion.cokeoven;

import net.minecraft.util.StringRepresentable;

import org.jetbrains.annotations.NotNull;

/**
 * 可配置焦炉仓的三种互斥模式 (coke-ovens.md 可配置焦炉仓):
 * 物品输入 / 物品输出 / 流体输出。新放置与旧存档迁移的默认模式都是物品输入。
 */
public enum CokeOvenMode implements StringRepresentable {
    ITEM_INPUT("item_input"),
    ITEM_OUTPUT("item_output"),
    FLUID_OUTPUT("fluid_output");

    public static final CokeOvenMode DEFAULT = ITEM_INPUT;

    private final String name;

    CokeOvenMode(String name) {
        this.name = name;
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
        return "gregsteamexpansion.coke_oven_hatch.mode." + name;
    }
}
