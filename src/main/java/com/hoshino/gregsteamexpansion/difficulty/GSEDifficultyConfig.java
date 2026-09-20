package com.hoshino.gregsteamexpansion.difficulty;

import com.hoshino.gregsteamexpansion.GregSteamExpansion;

import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.common.ForgeConfigSpec;

import java.util.EnumMap;
import java.util.Map;

/**
 * Startup-only settings from gregsteamexpansion-common.toml. Difficulty is
 * captured once while Forge loads the config and immediately applied to the
 * GTCEu recipe switches that must be fixed before datapacks are built. File
 * watcher reloads never mutate the running process.
 */
public final class GSEDifficultyConfig {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    private static final ForgeConfigSpec.BooleanValue DIFFICULTY_ENABLED = BUILDER
            .comment(
                    "Whether GSE's difficulty system takes control at process startup (default: false).",
                    "  false = GSE uses its Normal baseline internally and leaves GTCEu difficulty",
                    "          settings and boiler values untouched.",
                    "  true  = the selected difficulty applies to GSE and the documented GTCEu",
                    "          difficulty integration. A full restart is required after changing it.",
                    "  Ignored when an external pack authority such as GTSF Core is installed.",
                    "  是否在进程启动时启用 GSE 难度系统（默认关闭）。关闭时，本模组内部使用",
                    "  Normal 基线，且不改写 GTCEu 的难度配置与锅炉数值；修改后须完整重启。",
                    "  安装 GTSF Core 等整合包难度权威时，此项无效。")
            .define("difficultyEnabled", false);
    private static final ForgeConfigSpec.EnumValue<Difficulty> DIFFICULTY = BUILDER
            .comment(
                    "Work intensity selected when this process starts (default: NORMAL).",
                    "  EASY   = 摸鱼, NORMAL = 舒适, EXPERT = 压榨",
                    "  When difficultyEnabled is true, the selected tier controls GSE mechanics",
                    "  and GTCEu recipe difficulty switches before recipes load. In-game edits require a client restart;",
                    "  dedicated-server edits require a server restart. Multiplayer clients",
                    "  must start with the same tier and selected profile values as the server.",
                    "  Ignored when an external pack authority such as GTSF Core is installed.",
                    "  difficultyEnabled=true 时，工作强度在进程启动时确定，并在配方加载前",
                    "  同时控制本模组机制与 GTCEu 配方难度开关。游戏内修改后须重启客户端；专用服务端修改后须",
                    "  重启服务端。联机客户端的启动档位及该档参数必须与服务端一致。",
                    "  安装 GTSF Core 等整合包难度权威时，此项无效。")
            .defineEnum("difficulty", Difficulty.NORMAL);
    private static final ForgeConfigSpec.BooleanValue DIFFICULTY_SETUP_COMPLETED = BUILDER
            .comment(
                    "Internal first-run marker. The client sets this after the initial difficulty choice.",
                    "  Dedicated servers may set it to true after editing difficultyEnabled/difficulty.",
                    "  首次难度选择完成标记；客户端保存首次选择后自动写入。专用服务端可在完成",
                    "  difficultyEnabled / difficulty 配置后手动设为 true。")
            .define("difficultySetupCompleted", false);

    private static final Map<Difficulty, ProfileValues> DIFFICULTY_PROFILES = defineDifficultyProfiles();

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
    private static final ForgeConfigSpec.BooleanValue BOILER_ROOM_WATER_SCALE_ENABLED = BUILDER
            .comment("Enables boiler-room water scale. Captured at startup; scrapped controllers stay scrapped.",
                    "启用锅炉房水垢；启动时读取，已报废控制器不会因关闭配置而恢复。")
            .define("machines.boiler_room.waterScale.enabled", true);
    private static final ForgeConfigSpec.IntValue BOILER_ROOM_DESCALING_ACID_MB = BUILDER
            .comment("Diluted hydrochloric acid consumed by one 25-point descaling cycle (mB).")
            .defineInRange("machines.boiler_room.waterScale.descalingAcidMb", 8000, 1, 1_000_000);
    private static final ForgeConfigSpec.IntValue BOILER_ROOM_DESCALING_DURATION_TICKS = BUILDER
            .comment("Loaded server ticks required by one descaling cycle.")
            .defineInRange("machines.boiler_room.waterScale.descalingDurationTicks", 3200, 1, 1_440_000);

    public static final ForgeConfigSpec SPEC = BUILDER.build();

    private static volatile boolean capturedDifficultyEnabled;
    private static volatile Difficulty capturedDifficulty = Difficulty.NORMAL;
    private static volatile Map<Difficulty, GSEDifficultyProfile> capturedProfiles = defaultProfiles();
    private static volatile boolean capturedSetupCompleted;
    private static volatile ModConfig loadedConfig;
    // 旗舰机器捕获值: 仅在 Loading 时应用 (重启生效口径, 同 capturedDifficulty)。
    private static volatile boolean capturedOrePlantEnabled = true;
    private static volatile boolean capturedFluidDrillEnabled = true;
    private static volatile java.util.List<String> capturedOrePlantWeights = java.util.List.of();
    private static volatile java.util.List<String> capturedFluidDrillWeights = java.util.List.of();
    private static volatile boolean capturedBoilerRoomWaterScaleEnabled = true;
    private static volatile int capturedBoilerRoomDescalingAcidMb = 8000;
    private static volatile int capturedBoilerRoomDescalingDurationTicks = 3200;

    private GSEDifficultyConfig() {}

    /** The ore plant's captured enable toggle; only applied at config load. */
    public static boolean orePlantEnabled() {
        return capturedOrePlantEnabled;
    }

    /** The fluid drill's captured enable toggle; only applied at config load. */
    public static boolean fluidDrillEnabled() {
        return capturedFluidDrillEnabled;
    }

    /** Weight-table entries captured at process startup and parsed by the machine on first use. */
    public static java.util.List<? extends String> orePlantWeightEntries() {
        return capturedOrePlantWeights;
    }

    /** Weight-table entries captured at process startup and parsed by the machine on first use. */
    public static java.util.List<? extends String> fluidDrillWeightEntries() {
        return capturedFluidDrillWeights;
    }

    public static boolean boilerRoomWaterScaleEnabled() {
        return capturedBoilerRoomWaterScaleEnabled;
    }

    public static int boilerRoomDescalingAcidMb() {
        return capturedBoilerRoomDescalingAcidMb;
    }

    public static int boilerRoomDescalingDurationTicks() {
        return capturedBoilerRoomDescalingDurationTicks;
    }

    /** Includes global boiler-scale settings in the multiplayer startup identity. */
    public static String configurationFingerprint(Difficulty difficulty) {
        return configurationFingerprint(capturedProfile(difficulty));
    }

    /** Includes GSE-owned global settings in the effective profile's multiplayer identity. */
    public static String configurationFingerprint(GSEDifficultyProfile profile) {
        String value = profile.fingerprint() + "|" + capturedBoilerRoomWaterScaleEnabled + "|"
                + capturedBoilerRoomDescalingAcidMb + "|" + capturedBoilerRoomDescalingDurationTicks;
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by the Java runtime", exception);
        }
    }

    /** The process-wide tier captured during the initial config load. */
    public static Difficulty capturedDifficulty() {
        return capturedDifficulty;
    }

    /** Startup-captured profile for a tier; file-watcher reloads do not change it. */
    public static GSEDifficultyProfile capturedProfile(Difficulty difficulty) {
        return capturedProfiles.getOrDefault(difficulty, GSEDifficultyProfile.defaults(difficulty));
    }

    /** Whether the difficulty integration was enabled when this process started. */
    public static boolean capturedDifficultyEnabled() {
        return capturedDifficultyEnabled;
    }

    /** Whether the client has already saved the one-time initial choice. */
    public static boolean isInitialSetupCompleted() {
        return capturedSetupCompleted || DIFFICULTY_SETUP_COMPLETED.get();
    }

    public static void onConfigLoading(ModConfigEvent.Loading event) {
        if (event.getConfig().getSpec() == SPEC) {
            loadedConfig = event.getConfig();
            capturedDifficultyEnabled = DIFFICULTY_ENABLED.get();
            capturedDifficulty = DIFFICULTY.get();
            capturedProfiles = captureProfiles();
            capturedSetupCompleted = DIFFICULTY_SETUP_COMPLETED.get();
            capturedOrePlantEnabled = ORE_PLANT_ENABLED.get();
            capturedFluidDrillEnabled = FLUID_DRILL_ENABLED.get();
            capturedOrePlantWeights = java.util.List.copyOf(ORE_PLANT_WEIGHTS.get());
            capturedFluidDrillWeights = java.util.List.copyOf(FLUID_DRILL_WEIGHTS.get());
            capturedBoilerRoomWaterScaleEnabled = BOILER_ROOM_WATER_SCALE_ENABLED.get();
            capturedBoilerRoomDescalingAcidMb = BOILER_ROOM_DESCALING_ACID_MB.get();
            capturedBoilerRoomDescalingDurationTicks = BOILER_ROOM_DESCALING_DURATION_TICKS.get();
            GSEDifficultyAuthority.resolveStandalone(
                    capturedDifficultyEnabled, capturedDifficulty, capturedProfile(capturedDifficulty));
            GregSteamExpansion.LOGGER.info(
                    "[Difficulty] Standalone config captured as {} with tier {}; effective authority {}; flagship machines: ore plant {}, fluid drill {}; circuit assembler specialization: {}%, +{}x.",
                    capturedDifficultyEnabled ? "enabled" : "disabled", capturedDifficulty,
                    GSEDifficultyAuthority.isExternallyManaged()
                            ? GSEDifficultyAuthority.externalOwnerModId() : GregSteamExpansion.MOD_ID,
                    capturedOrePlantEnabled, capturedFluidDrillEnabled,
                    GSEDifficultyState.circuitAssemblerBonusChancePercent(false),
                    GSEDifficultyState.circuitAssemblerBonusMultiplier(false));
        }
    }

    public static void onConfigReloading(ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() == SPEC) {
            if (GSEDifficultyAuthority.isExternallyManaged()) {
                GregSteamExpansion.LOGGER.warn(
                        "[Difficulty] GSE difficulty settings changed while {} is authoritative; " +
                                "difficultyEnabled, difficulty and difficultyProfiles are ignored. " +
                                "Machine settings still require a full restart (ore plant {}, fluid drill {}).",
                        GSEDifficultyAuthority.externalOwnerModId(),
                        ORE_PLANT_ENABLED.get(), FLUID_DRILL_ENABLED.get());
            } else {
                GregSteamExpansion.LOGGER.warn(
                        "[Difficulty] config changed while running (enabled {}, difficulty {}, ore plant {}, fluid drill {}); it is ignored until the next full restart.",
                        DIFFICULTY_ENABLED.get(), DIFFICULTY.get(), ORE_PLANT_ENABLED.get(), FLUID_DRILL_ENABLED.get());
            }
        }
    }

    /**
     * Writes the tier to the config file (config screen save path) without
     * touching the captured value: the running session keeps following the
     * restart rule, and the file-watcher reload logs the usual reminder.
     */
    public static void setDifficultySettings(boolean enabled, Difficulty difficulty) {
        DIFFICULTY_ENABLED.set(enabled);
        DIFFICULTY.set(difficulty);
        save();
    }

    /** Saves the one-time client choice and its marker before the required restart. */
    public static void completeInitialSetup(boolean enabled, Difficulty difficulty) {
        DIFFICULTY_ENABLED.set(enabled);
        DIFFICULTY.set(difficulty);
        DIFFICULTY_SETUP_COMPLETED.set(true);
        capturedSetupCompleted = true;
        save();
    }

    private static void save() {
        ModConfig config = loadedConfig;
        if (config != null) {
            config.save();
        }
    }

    private static Map<Difficulty, ProfileValues> defineDifficultyProfiles() {
        EnumMap<Difficulty, ProfileValues> values = new EnumMap<>(Difficulty.class);
        BUILDER.comment(
                "Per-tier balance profiles. Every value is captured only at startup.",
                "Ignored when an external pack authority such as GTSF Core is installed.",
                "These settings are intended for modpack authors; clients and servers must use",
                "the same selected profile. Most multipliers are ignored while difficultyEnabled=false;",
                "the circuit-assembler specialization uses the Normal profile as its baseline.",
                "三档难度的独立平衡参数，仅在启动时读取，适合整合包作者覆盖。联机双方所选档位",
                "的全部参数必须一致；difficultyEnabled=false 时多数倍率不生效，电路组装机专精",
                "按 Normal 档基线运行。安装 GTSF Core 等整合包难度权威时，本节无效。")
                .push("difficultyProfiles");
        for (Difficulty difficulty : Difficulty.values()) {
            values.put(difficulty, defineProfile(difficulty, GSEDifficultyProfile.defaults(difficulty)));
        }
        BUILDER.pop();
        return Map.copyOf(values);
    }

    private static ProfileValues defineProfile(Difficulty difficulty, GSEDifficultyProfile defaults) {
        BUILDER.comment("Balance values for " + difficulty.name() + ". / " + difficulty.name() + " 档参数。")
                .push(difficulty.getSerializedName());
        ProfileValues values = new ProfileValues(
                BUILDER.comment("GTCEu recipes.casingsPerCraft (1-3).")
                        .defineInRange("gtceuCasingsPerCraft", defaults.gtceuCasingsPerCraft(), 1, 3),
                BUILDER.comment("Dedicated steam boiler output multiplier (0.01-1000).")
                        .defineInRange("steamOutputMultiplier", defaults.steamOutputMultiplier(), 0.01, 1000.0),
                BUILDER.comment("Single-block boiler steam tank capacity multiplier (1-1000).")
                        .defineInRange("singleblockSteamCacheMultiplier", defaults.singleblockSteamCacheMultiplier(), 1, 1000),
                BUILDER.comment("Heat-storage furnace preheat steam cost, percent of baseline (1-10000).")
                        .defineInRange("preheatCostPercent", defaults.preheatCostPercent(), 1, 10000),
                BUILDER.comment("Heat-storage furnace ticks per +1 C while preheating (1-1200).")
                        .defineInRange("preheatIntervalTicks", defaults.preheatIntervalTicks(), 1, 1200),
                BUILDER.comment("Heat-storage furnace processing steam cost, percent of baseline (1-10000).")
                        .defineInRange("processingSteamPercent", defaults.processingSteamPercent(), 1, 10000),
                BUILDER.comment("Migrated ore-crushing main-output multiplier (0.01-1000).")
                        .defineInRange("oreCrushingMultiplier", defaults.oreCrushingMultiplier(), 0.01, 1000.0),
                BUILDER.comment("Boiler-room co-firing steam output multiplier (0.01-1000).")
                        .defineInRange("boilerRoomSteamOutputMultiplier", defaults.boilerRoomSteamOutputMultiplier(), 0.01, 1000.0),
                BUILDER.comment("Equivalent full-load hours before the boiler-room controller is scrapped; 0 disables buildup.")
                        .defineInRange("boilerRoomScaleFailureHours", defaults.boilerRoomScaleFailureHours(), 0.0, 100_000.0),
                BUILDER.comment("Steam-output loss at 25% water scale (0-99 percent).")
                        .defineInRange("boilerRoomScaleLossStage1Percent", defaults.boilerRoomScaleLossStage1Percent(), 0, 99),
                BUILDER.comment("Steam-output loss at 50% water scale (0-99 percent).")
                        .defineInRange("boilerRoomScaleLossStage2Percent", defaults.boilerRoomScaleLossStage2Percent(), 0, 99),
                BUILDER.comment("Steam-output loss at 75% water scale (0-99 percent).")
                        .defineInRange("boilerRoomScaleLossStage3Percent", defaults.boilerRoomScaleLossStage3Percent(), 0, 99),
                BUILDER.comment("Steam assembler batch item-output multiplier (0.01-1000).")
                        .defineInRange("assemblerOutputMultiplier", defaults.assemblerOutputMultiplier(), 0.01, 1000.0),
                BUILDER.comment("Ore plant and fluid drill output multiplier (1-1000).")
                        .defineInRange("voidProducerOutputMultiplier", defaults.voidProducerOutputMultiplier(), 1, 1000),
                BUILDER.comment(
                                "Large Steam Circuit Assembler specialization chance (0-100 percent).",
                                "A matching batch takes 50% longer and then rolls this chance once.",
                                "大型蒸汽电路组装机专精增产概率（0-100，单位 %）；匹配批次耗时与总耗汽",
                                "增加 50%，完成时按此概率掷骰一次。")
                        .defineInRange("circuitAssemblerBonusChancePercent",
                                defaults.circuitAssemblerBonusChancePercent(), 0, 100),
                BUILDER.comment(
                                "Additional target-circuit multiplier on a successful roll (1-15, integer).",
                                "7 means an additional 7x the target circuit output.",
                                "专精成功时追加的目标电路倍率（1-15，仅整数）；7 表示额外追加 7 倍。")
                        .defineInRange("circuitAssemblerBonusMultiplier",
                                defaults.circuitAssemblerBonusMultiplier(), 1, 15),
                defineGseRecipeBoolean("hardBronzeComponentRecipes", defaults.hardBronzeComponentRecipes()),
                defineGseRecipeBoolean("harderSteamGrindingBlockRecipes", defaults.harderSteamGrindingBlockRecipes()),
                defineGseRecipeBoolean("hardSteamAssemblyBlockRecipes", defaults.hardSteamAssemblyBlockRecipes()),
                defineGseRecipeBoolean("hardSteamCircuitAssemblyBlockRecipes", defaults.hardSteamCircuitAssemblyBlockRecipes()),
                defineGseRecipeBoolean("hardSteamMixingBlockRecipes", defaults.hardSteamMixingBlockRecipes()),
                defineRecipeBoolean("disableManualCompression", defaults.disableManualCompression()),
                defineRecipeBoolean("harderRods", defaults.harderRods()),
                defineRecipeBoolean("harderBrickRecipes", defaults.harderBrickRecipes()),
                defineRecipeBoolean("nerfWoodCrafting", defaults.nerfWoodCrafting()),
                defineRecipeBoolean("hardWoodRecipes", defaults.hardWoodRecipes()),
                defineRecipeBoolean("hardIronRecipes", defaults.hardIronRecipes()),
                defineRecipeBoolean("hardRedstoneRecipes", defaults.hardRedstoneRecipes()),
                defineRecipeBoolean("hardToolArmorRecipes", defaults.hardToolArmorRecipes()),
                defineRecipeBoolean("hardMiscRecipes", defaults.hardMiscRecipes()),
                defineRecipeBoolean("hardGlassRecipes", defaults.hardGlassRecipes()),
                defineRecipeBoolean("nerfPaperCrafting", defaults.nerfPaperCrafting()),
                defineRecipeBoolean("hardAdvancedIronRecipes", defaults.hardAdvancedIronRecipes()),
                defineRecipeBoolean("hardDyeRecipes", defaults.hardDyeRecipes()),
                defineRecipeBoolean("harderCharcoalRecipe", defaults.harderCharcoalRecipe()),
                defineRecipeBoolean("flintAndSteelRequireSteel", defaults.flintAndSteelRequireSteel()),
                defineRecipeBoolean("removeVanillaBlockRecipes", defaults.removeVanillaBlockRecipes()),
                defineRecipeBoolean("removeVanillaTNTRecipe", defaults.removeVanillaTNTRecipe()),
                defineRecipeBoolean("harderCircuitRecipes", defaults.harderCircuitRecipes()),
                defineRecipeBoolean("hardMultiRecipes", defaults.hardMultiRecipes()));
        BUILDER.pop();
        return values;
    }

    private static ForgeConfigSpec.BooleanValue defineRecipeBoolean(String name, boolean defaultValue) {
        return BUILDER.comment("Value written to GTCEu recipes." + name + " at startup.")
                .define("gtceuRecipeOptions." + name, defaultValue);
    }

    private static ForgeConfigSpec.BooleanValue defineGseRecipeBoolean(String name, boolean defaultValue) {
        return BUILDER.comment("Selects the harder GSE recipe variant for " + name + ".")
                .define("gseRecipeOptions." + name, defaultValue);
    }

    private static Map<Difficulty, GSEDifficultyProfile> captureProfiles() {
        EnumMap<Difficulty, GSEDifficultyProfile> profiles = new EnumMap<>(Difficulty.class);
        DIFFICULTY_PROFILES.forEach((difficulty, values) -> profiles.put(difficulty, values.capture()));
        return Map.copyOf(profiles);
    }

    private static Map<Difficulty, GSEDifficultyProfile> defaultProfiles() {
        EnumMap<Difficulty, GSEDifficultyProfile> profiles = new EnumMap<>(Difficulty.class);
        for (Difficulty difficulty : Difficulty.values()) {
            profiles.put(difficulty, GSEDifficultyProfile.defaults(difficulty));
        }
        return Map.copyOf(profiles);
    }

    private record ProfileValues(
                                 ForgeConfigSpec.IntValue gtceuCasingsPerCraft,
                                 ForgeConfigSpec.DoubleValue steamOutputMultiplier,
                                 ForgeConfigSpec.IntValue singleblockSteamCacheMultiplier,
                                 ForgeConfigSpec.IntValue preheatCostPercent,
                                 ForgeConfigSpec.IntValue preheatIntervalTicks,
                                 ForgeConfigSpec.IntValue processingSteamPercent,
                                 ForgeConfigSpec.DoubleValue oreCrushingMultiplier,
                                 ForgeConfigSpec.DoubleValue boilerRoomSteamOutputMultiplier,
                                 ForgeConfigSpec.DoubleValue boilerRoomScaleFailureHours,
                                 ForgeConfigSpec.IntValue boilerRoomScaleLossStage1Percent,
                                 ForgeConfigSpec.IntValue boilerRoomScaleLossStage2Percent,
                                 ForgeConfigSpec.IntValue boilerRoomScaleLossStage3Percent,
                                 ForgeConfigSpec.DoubleValue assemblerOutputMultiplier,
                                 ForgeConfigSpec.IntValue voidProducerOutputMultiplier,
                                 ForgeConfigSpec.IntValue circuitAssemblerBonusChancePercent,
                                 ForgeConfigSpec.IntValue circuitAssemblerBonusMultiplier,
                                 ForgeConfigSpec.BooleanValue hardBronzeComponentRecipes,
                                 ForgeConfigSpec.BooleanValue harderSteamGrindingBlockRecipes,
                                 ForgeConfigSpec.BooleanValue hardSteamAssemblyBlockRecipes,
                                 ForgeConfigSpec.BooleanValue hardSteamCircuitAssemblyBlockRecipes,
                                 ForgeConfigSpec.BooleanValue hardSteamMixingBlockRecipes,
                                 ForgeConfigSpec.BooleanValue disableManualCompression,
                                 ForgeConfigSpec.BooleanValue harderRods,
                                 ForgeConfigSpec.BooleanValue harderBrickRecipes,
                                 ForgeConfigSpec.BooleanValue nerfWoodCrafting,
                                 ForgeConfigSpec.BooleanValue hardWoodRecipes,
                                 ForgeConfigSpec.BooleanValue hardIronRecipes,
                                 ForgeConfigSpec.BooleanValue hardRedstoneRecipes,
                                 ForgeConfigSpec.BooleanValue hardToolArmorRecipes,
                                 ForgeConfigSpec.BooleanValue hardMiscRecipes,
                                 ForgeConfigSpec.BooleanValue hardGlassRecipes,
                                 ForgeConfigSpec.BooleanValue nerfPaperCrafting,
                                 ForgeConfigSpec.BooleanValue hardAdvancedIronRecipes,
                                 ForgeConfigSpec.BooleanValue hardDyeRecipes,
                                 ForgeConfigSpec.BooleanValue harderCharcoalRecipe,
                                 ForgeConfigSpec.BooleanValue flintAndSteelRequireSteel,
                                 ForgeConfigSpec.BooleanValue removeVanillaBlockRecipes,
                                 ForgeConfigSpec.BooleanValue removeVanillaTNTRecipe,
                                 ForgeConfigSpec.BooleanValue harderCircuitRecipes,
                                 ForgeConfigSpec.BooleanValue hardMultiRecipes) {

        private GSEDifficultyProfile capture() {
            int stage1 = boilerRoomScaleLossStage1Percent.get();
            int stage2 = boilerRoomScaleLossStage2Percent.get();
            int stage3 = boilerRoomScaleLossStage3Percent.get();
            if (stage1 > stage2 || stage2 > stage3) {
                throw new IllegalStateException("Boiler-room water-scale losses must be nondecreasing for the selected profile");
            }
            return new GSEDifficultyProfile(
                    gtceuCasingsPerCraft.get(), steamOutputMultiplier.get(), singleblockSteamCacheMultiplier.get(),
                    preheatCostPercent.get(), preheatIntervalTicks.get(), processingSteamPercent.get(),
                    oreCrushingMultiplier.get(), boilerRoomSteamOutputMultiplier.get(),
                    boilerRoomScaleFailureHours.get(), stage1, stage2, stage3,
                    assemblerOutputMultiplier.get(), voidProducerOutputMultiplier.get(),
                    circuitAssemblerBonusChancePercent.get(), circuitAssemblerBonusMultiplier.get(),
                    hardBronzeComponentRecipes.get(), harderSteamGrindingBlockRecipes.get(),
                    hardSteamAssemblyBlockRecipes.get(), hardSteamCircuitAssemblyBlockRecipes.get(),
                    hardSteamMixingBlockRecipes.get(),
                    disableManualCompression.get(), harderRods.get(), harderBrickRecipes.get(),
                    nerfWoodCrafting.get(), hardWoodRecipes.get(), hardIronRecipes.get(),
                    hardRedstoneRecipes.get(), hardToolArmorRecipes.get(), hardMiscRecipes.get(),
                    hardGlassRecipes.get(), nerfPaperCrafting.get(), hardAdvancedIronRecipes.get(),
                    hardDyeRecipes.get(), harderCharcoalRecipe.get(), flintAndSteelRequireSteel.get(),
                    removeVanillaBlockRecipes.get(), removeVanillaTNTRecipe.get(),
                    harderCircuitRecipes.get(), hardMultiRecipes.get());
        }
    }
}
