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
    public static final ForgeConfigSpec SPEC = BUILDER.build();

    private static volatile Request capturedRequest;
    private static volatile boolean suppressNextReloadWarning;

    private GSEDifficultyConfig() {}

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
            GregSteamExpansion.LOGGER.info("[Difficulty] Config requests difficulty {}.",
                    capturedRequest);
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
                    "[Difficulty] difficulty config changed to {} while running; it is ignored until the next full restart.",
                    REQUESTED.get());
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
