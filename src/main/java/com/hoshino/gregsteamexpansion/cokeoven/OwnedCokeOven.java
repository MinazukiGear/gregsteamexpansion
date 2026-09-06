package com.hoshino.gregsteamexpansion.cokeoven;

/**
 * 焦炉家族控制器的所有权回调 (普通焦炉与大型焦炉共用): 在旧存档迁移所有权
 * 仲裁中被坐标更小的竞争者抢占时, 由 {@code CokeOvenWorldData} 回调进入
 * 结构无效状态并释放占用。
 */
public interface OwnedCokeOven {

    /** 结构独占竞争失败: 强制失效并给出原因, 不触发任何清空。 */
    void invalidateByOwnershipConflict();
}
