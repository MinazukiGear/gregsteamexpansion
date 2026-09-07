package com.hoshino.gregsteamexpansion.difficulty;

import com.hoshino.gregsteamexpansion.GregSteamExpansion;

import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.common.ForgeConfigSpec;

import org.jetbrains.annotations.Nullable;

/**
 * The {@code difficulty} entry in gregsteamexpansion-common.toml. A concrete
 * tier requests the difficulty for saves: it initializes a save without a
 * stored tier and may permanently downgrade an initialized one; {@code ASK}
 * defers an uninitialized save to the in-game first-entry choice. The
 * save-file tier is authoritative (difficulty.md 服务端与存档权威性), and per the
 * client restart rule the request is captured exactly once when the config
 * file is first loaded: later file changes are logged but ignored until the
 * next full restart.
 */
public final class GSEDifficultyConfig {

    /** Config-facing request value; ASK defers to the in-game choice. */
    public enum Request {
        ASK(null),
        EASY(Difficulty.EASY),
        NORMAL(Difficulty.NORMAL),
        EXPERT(Difficulty.EXPERT);

        @Nullable
        private final Difficulty difficulty;

        Request(@Nullable Difficulty difficulty) {
            this.difficulty = difficulty;
        }

        /** The tier this request names, or null for ASK. */
        @Nullable
        public Difficulty difficulty() {
            return difficulty;
        }
    }

    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    private static final ForgeConfigSpec.EnumValue<Request> REQUESTED = BUILDER
            .comment(
                    "Work intensity (difficulty) request; the save-file tier is authoritative.",
                    "  ASK    = ask the first entering player in game (default)",
                    "  EASY   = 摸鱼, NORMAL = 舒适, EXPERT = 压榨",
                    "  A concrete value initializes a save without a stored tier and may",
                    "  permanently downgrade an initialized save; upgrades are refused with",
                    "  a warning. A client must declare exactly the save tier to join, so",
                    "  changing this value requires a full server (or single-player) restart.",
                    "  工作强度请求档位；存档内实际档位是唯一权威来源。ASK（默认）表示未",
                    "  初始化存档在第一次进入时由进入者在游戏内选择；填写具体档位则会以",
                    "  该档位初始化未初始化存档，或将已初始化存档永久降档（升档会被拒绝",
                    "  并记录警告）。客户端必须声明与存档完全一致的档位才能进入世界，修改",
                    "  后必须完整重启才会生效。")
            .defineEnum("difficulty", Request.ASK);

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

    private static volatile Request capturedRequest;
    private static volatile boolean suppressNextReloadWarning;
    // 旗舰机器捕获值: 仅在 Loading 时应用 (重启生效口径, 同 capturedRequest)。
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

    /** The request captured at config load; the only one the server honors. */
    public static Request capturedRequest() {
        Request captured = capturedRequest;
        return captured != null ? captured : Request.ASK;
    }

    /**
     * The tier this installation declares during the login handshake; null
     * when the config says ASK (an uninitialized save asks in game instead).
     */
    @Nullable
    public static Difficulty clientDeclarationTier() {
        return capturedRequest().difficulty();
    }

    public static void onConfigLoading(ModConfigEvent.Loading event) {
        if (event.getConfig().getSpec() == SPEC) {
            capturedRequest = REQUESTED.get();
            capturedOrePlantEnabled = ORE_PLANT_ENABLED.get();
            capturedFluidDrillEnabled = FLUID_DRILL_ENABLED.get();
            GregSteamExpansion.LOGGER.info(
                    "[Difficulty] Config requests difficulty {}; flagship machines: ore plant {}, fluid drill {}.",
                    capturedRequest, capturedOrePlantEnabled, capturedFluidDrillEnabled);
        }
    }

    public static void onConfigReloading(ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() == SPEC) {
            if (suppressNextReloadWarning) {
                // The file change comes from the client's own first-entry choice.
                suppressNextReloadWarning = false;
                return;
            }
            GregSteamExpansion.LOGGER.warn(
                    "[Difficulty] config changed while running (difficulty {}, ore plant {}, fluid drill {}); it is ignored until the next full restart.",
                    REQUESTED.get(), ORE_PLANT_ENABLED.get(), FLUID_DRILL_ENABLED.get());
        }
    }

    /**
     * Client-side only: applies the tier the player picked at first entry to
     * the local config, so this and future sessions declare the same tier.
     */
    public static void applyChosenRequest(Difficulty difficulty) {
        suppressNextReloadWarning = true;
        REQUESTED.set(Request.valueOf(difficulty.name()));
        capturedRequest = Request.valueOf(difficulty.name());
    }

    /**
     * Writes the request to the config file (config screen save path) without
     * touching the captured value: the running session keeps following the
     * restart rule, and the file-watcher reload logs the usual reminder.
     */
    public static void setRequest(Request request) {
        REQUESTED.set(request);
    }
}
