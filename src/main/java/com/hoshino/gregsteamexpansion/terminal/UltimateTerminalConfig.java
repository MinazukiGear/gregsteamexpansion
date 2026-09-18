package com.hoshino.gregsteamexpansion.terminal;

import net.minecraftforge.common.ForgeConfigSpec;

/** Live server-side throughput limit for persistent construction jobs. */
public final class UltimateTerminalConfig {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    private static final ForgeConfigSpec.IntValue BLOCKS_PER_TICK = BUILDER
            .comment(
                    "Maximum Ultimate Terminal block operations per server tick.",
                    "终极终端每个服务端 tick 最多执行的方块操作数。")
            .defineInRange("ultimateTerminalBlocksPerTick", 32, 1, 256);

    public static final ForgeConfigSpec SPEC = BUILDER.build();

    private UltimateTerminalConfig() {}

    public static int blocksPerTick() {
        return BLOCKS_PER_TICK.get();
    }
}
