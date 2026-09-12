package com.hoshino.gregsteamexpansion.difficulty;

import com.hoshino.gregsteamexpansion.GregSteamExpansion;

import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Startup-only settings from gregsteamexpansion-common.toml. Difficulty is
 * captured once while Forge loads the config and immediately applied to the
 * GTCEu recipe switches that must be fixed before datapacks are built. File
 * watcher reloads never mutate the running process.
 */
public final class GSEDifficultyConfig {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    private static final ForgeConfigSpec.EnumValue<Difficulty> DIFFICULTY = BUILDER
            .comment(
                    "Work intensity selected when this process starts (default: NORMAL).",
                    "  EASY   = 摸鱼, NORMAL = 舒适, EXPERT = 压榨",
                    "  The selected tier controls GSE mechanics and GTCEu recipe difficulty",
                    "  switches before recipes load. In-game edits require a client restart;",
                    "  dedicated-server edits require a server restart. Multiplayer clients",
                    "  must start with the same tier as the server.",
                    "  工作强度在进程启动时确定，并在配方加载前同时控制本模组机制与",
                    "  GTCEu 配方难度开关。游戏内修改后须重启客户端；专用服务端修改后须",
                    "  重启服务端。联机客户端的启动档位必须与服务端一致。")
            .defineEnum("difficulty", Difficulty.NORMAL);

    // ------------------------------------------------------------------
    // 旗舰虚空机器开关与概率权重表 (large-steam-ore-plant.md 议题 2/3,
    // large-steam-fluid-drill.md 议题 2/3): 启用开关默认 true, 禁用时机器
    // 不可运行(注册与结构保留); 权重表条目为字符串, 空/非法条目回退内置
    // 默认表。两项均与 difficulty 同口径: 修改后必须完整重启才生效。
    // ------------------------------------------------------------------
    private static final ForgeConfigSpec.BooleanValue ORE_PLANT_ENABLED = BUILDER
            .comment(
                    "Large Steam Ore Plant (void ore producer) enable toggle.",
                    "  false = the machine registers and keeps its structure but never runs,",
                    "  showing a config-disabled status; requires a full restart to apply.",
                    "  大型蒸汽采矿厂启用开关；false 时机器保留注册与结构但不可运行，显示",
                    "  已在配置中禁用；修改后必须完整重启才会生效。")
            .define("machines.large_steam_ore_plant.enabled", true);
    private static final ForgeConfigSpec.ConfigValue<java.util.List<? extends String>> ORE_PLANT_WEIGHTS = BUILDER
            .comment(
                    "Ore plant draw pool, overrides the built-in table. Entry format:",
                    "  <item registry name>|<weight>|[count]   e.g. gtceu:raw_iron|15 or minecraft:redstone|6|4",
                    "  Empty or invalid entries fall back to the built-in default table;",
                    "  requires a full restart to apply.",
                    "  采矿厂抽取池，覆盖内置默认表；条目格式 <物品注册名>|<权重>|[数量]，",
                    "  留空或含非法条目时回退内置默认表；修改后必须完整重启才会生效。")
            .defineListAllowEmpty("machines.large_steam_ore_plant.weights",
                    java.util.List.of(), entry -> entry instanceof String);
    private static final ForgeConfigSpec.BooleanValue FLUID_DRILL_ENABLED = BUILDER
            .comment(
                    "Large Steam Fluid Drill (void fluid producer) enable toggle;",
                    "  same semantics as the ore plant toggle; requires a full restart.",
                    "  大型蒸汽流体钻井启用开关；语义同采矿厂开关；修改后必须完整重启才会生效。")
            .define("machines.large_steam_fluid_drill.enabled", true);
    private static final ForgeConfigSpec.ConfigValue<java.util.List<? extends String>> FLUID_DRILL_WEIGHTS = BUILDER
            .comment(
                    "Fluid drill draw pool, overrides the built-in table. Entry format:",
                    "  <fluid registry name>|<weight>   e.g. gtceu:oil|20",
                    "  Empty or invalid entries fall back to the built-in default table;",
                    "  requires a full restart to apply.",
                    "  流体钻井抽取池，覆盖内置默认表；条目格式 <流体注册名>|<权重>，",
                    "  留空或含非法条目时回退内置默认表；修改后必须完整重启才会生效。")
            .defineListAllowEmpty("machines.large_steam_fluid_drill.weights",
                    java.util.List.of(), entry -> entry instanceof String);

    public static final ForgeConfigSpec SPEC = BUILDER.build();

    private static volatile Difficulty capturedDifficulty = Difficulty.NORMAL;
    // 旗舰机器捕获值: 仅在 Loading 时应用 (重启生效口径, 同 capturedDifficulty)。
    private static volatile boolean capturedOrePlantEnabled = true;
    private static volatile boolean capturedFluidDrillEnabled = true;

    private GSEDifficultyConfig() {}

    /** The ore plant's captured enable toggle; only applied at config load. */
    public static boolean orePlantEnabled() {
        return capturedOrePlantEnabled;
    }

    /** The fluid drill's captured enable toggle; only applied at config load. */
    public static boolean fluidDrillEnabled() {
        return capturedFluidDrillEnabled;
    }

    /** Raw configured weight-table entries (parsed by the machines at use time). */
    public static java.util.List<? extends String> orePlantWeightEntries() {
        return ORE_PLANT_WEIGHTS.get();
    }

    /** Raw configured weight-table entries (parsed by the machines at use time). */
    public static java.util.List<? extends String> fluidDrillWeightEntries() {
        return FLUID_DRILL_WEIGHTS.get();
    }

    /** The process-wide tier captured during the initial config load. */
    public static Difficulty capturedDifficulty() {
        return capturedDifficulty;
    }

    public static void onConfigLoading(ModConfigEvent.Loading event) {
        if (event.getConfig().getSpec() == SPEC) {
            capturedDifficulty = DIFFICULTY.get();
            capturedOrePlantEnabled = ORE_PLANT_ENABLED.get();
            capturedFluidDrillEnabled = FLUID_DRILL_ENABLED.get();
            GSEDifficultyState.initializeAtStartup(capturedDifficulty);
            GregSteamExpansion.LOGGER.info(
                    "[Difficulty] Startup difficulty is {}; flagship machines: ore plant {}, fluid drill {}.",
                    capturedDifficulty, capturedOrePlantEnabled, capturedFluidDrillEnabled);
        }
    }

    public static void onConfigReloading(ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() == SPEC) {
            GregSteamExpansion.LOGGER.warn(
                    "[Difficulty] config changed while running (difficulty {}, ore plant {}, fluid drill {}); it is ignored until the next full restart.",
                    DIFFICULTY.get(), ORE_PLANT_ENABLED.get(), FLUID_DRILL_ENABLED.get());
        }
    }

    /**
     * Writes the tier to the config file (config screen save path) without
     * touching the captured value: the running session keeps following the
     * restart rule, and the file-watcher reload logs the usual reminder.
     */
    public static void setDifficulty(Difficulty difficulty) {
        DIFFICULTY.set(difficulty);
    }
}
