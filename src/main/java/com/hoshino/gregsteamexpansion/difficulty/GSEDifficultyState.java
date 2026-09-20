package com.hoshino.gregsteamexpansion.difficulty;

import com.gregtechceu.gtceu.config.ConfigHolder;
import com.hoshino.gregsteamexpansion.GregSteamExpansion;

/**
 * Process-wide difficulty state. The startup config is the authority for the
 * whole process and is applied before recipe/datapack loading; the synced
 * client value exists only for multiplayer display and machine-side parity.
 */
public final class GSEDifficultyState {
    private static volatile boolean startupEnabled;
    private static volatile Difficulty startupDifficulty = Difficulty.NORMAL;
    private static volatile GSEDifficultyProfile startupProfile = GSEDifficultyProfile.defaults(Difficulty.NORMAL);
    private static volatile boolean initialized;
    private static volatile boolean clientEnabled;
    private static volatile Difficulty clientDifficulty = Difficulty.NORMAL;
    private static volatile GSEDifficultyProfile clientProfile = GSEDifficultyProfile.defaults(Difficulty.NORMAL);
    private static volatile boolean clientTierSynced;

    private GSEDifficultyState() {}

    /** The startup tier, with Normal as the safe pre-config fallback. */
    public static Difficulty resolved() {
        return startupDifficulty;
    }

    /** Whether the startup process enabled difficulty integration. */
    public static boolean isEnabled() {
        return startupEnabled;
    }

    /** True once Forge has loaded this mod's startup config. */
    public static boolean isResolved() {
        return initialized;
    }

    /** Stores the tier pushed to this client after a passed login check. */
    public static void setClientDifficulty(boolean enabled, Difficulty difficulty) {
        clientEnabled = enabled;
        clientDifficulty = difficulty;
        // Login validation already proved that both processes use the same
        // effective profile. Reuse the startup snapshot so an external pack
        // authority never falls back to GSE's standalone config here.
        clientProfile = startupProfile;
        clientTierSynced = true;
    }

    /** True once this session's server pushed a tier to this client. */
    public static boolean isClientTierSynced() {
        return clientTierSynced;
    }

    /** The tier this client was last synced with; only meaningful when synced. */
    public static Difficulty getClientDifficulty() {
        return clientDifficulty;
    }

    public static boolean isClientDifficultyEnabled() {
        return clientEnabled;
    }

    public static void clearClientTierSynced() {
        clientTierSynced = false;
    }

    /** Tier for machine-side logic: client machines read the synced tier. */
    public static Difficulty current(boolean remote) {
        return remote ? clientDifficulty : resolved();
    }

    /** Startup-captured values used by machine-side logic. */
    public static GSEDifficultyProfile currentProfile(boolean remote) {
        return remote ? clientProfile : startupProfile;
    }

    /** Integration state for side-aware hooks that touch upstream GTCEu behavior. */
    public static boolean isEnabled(boolean remote) {
        return remote ? clientEnabled : startupEnabled;
    }

    public static float steamOutputMultiplier(boolean remote) {
        return isEnabled(remote) ? (float) currentProfile(remote).steamOutputMultiplier() : 1.0F;
    }

    public static int singleblockSteamCacheMultiplier(boolean remote) {
        return isEnabled(remote) ? currentProfile(remote).singleblockSteamCacheMultiplier() : 1;
    }

    public static float boilerRoomSteamOutputMultiplier(boolean remote) {
        return isEnabled(remote) ? (float) currentProfile(remote).boilerRoomSteamOutputMultiplier() : 1.0F;
    }

    public static double boilerRoomScaleFailureHours(boolean remote) {
        return currentProfile(remote).boilerRoomScaleFailureHours();
    }

    public static int boilerRoomScaleLossPercent(boolean remote, int stage) {
        GSEDifficultyProfile profile = currentProfile(remote);
        return switch (stage) {
            case 1 -> profile.boilerRoomScaleLossStage1Percent();
            case 2 -> profile.boilerRoomScaleLossStage2Percent();
            case 3 -> profile.boilerRoomScaleLossStage3Percent();
            default -> 0;
        };
    }

    public static float oreCrushingMultiplier(boolean remote) {
        return isEnabled(remote) ? (float) currentProfile(remote).oreCrushingMultiplier() : 1.0F;
    }

    public static int preheatCostPercent(boolean remote) {
        return isEnabled(remote) ? currentProfile(remote).preheatCostPercent() : 100;
    }

    public static int preheatIntervalTicks(boolean remote) {
        return isEnabled(remote) ? currentProfile(remote).preheatIntervalTicks() : 5;
    }

    public static int processingSteamPercent(boolean remote) {
        return isEnabled(remote) ? currentProfile(remote).processingSteamPercent() : 100;
    }

    public static float assemblerOutputMultiplier(boolean remote) {
        return isEnabled(remote) ? (float) currentProfile(remote).assemblerOutputMultiplier() : 1.0F;
    }

    public static int voidProducerOutputMultiplier(boolean remote) {
        return isEnabled(remote) ? currentProfile(remote).voidProducerOutputMultiplier() : 1;
    }

    /** Uses the selected tier, or the Normal profile when difficulty integration is disabled. */
    public static int circuitAssemblerBonusChancePercent(boolean remote) {
        return currentProfile(remote).circuitAssemblerBonusChancePercent();
    }

    /** Uses the selected tier, or the Normal profile when difficulty integration is disabled. */
    public static int circuitAssemblerBonusMultiplier(boolean remote) {
        return currentProfile(remote).circuitAssemblerBonusMultiplier();
    }

    /**
     * Effective casing/block recipe output. When difficulty is disabled GSE
     * follows GTCEu's untouched setting instead of imposing a profile value.
     */
    public static int recipeCasingsPerCraft() {
        if (startupEnabled) {
            return startupProfile.gtceuCasingsPerCraft();
        }
        ConfigHolder.init();
        return ConfigHolder.INSTANCE.recipes.casingsPerCraft;
    }

    public static String profileFingerprint() {
        return GSEDifficultyConfig.configurationFingerprint(startupProfile);
    }

    /** The immutable effective startup profile, regardless of who supplied it. */
    public static GSEDifficultyProfile resolvedProfile() {
        return startupProfile;
    }

    /**
     * Captures the process tier and applies its startup-configured GTCEu 7.5.3
     * recipe profile before recipes are loaded.
     */
    static synchronized void initializeAtStartup(boolean enabled, Difficulty difficulty,
                                                 GSEDifficultyProfile profile) {
        if (initialized) {
            return;
        }
        Difficulty effectiveDifficulty = enabled ? difficulty : Difficulty.NORMAL;
        if (enabled) {
            ConfigHolder.init();
            ConfigHolder.RecipeConfigs recipes = ConfigHolder.INSTANCE.recipes;
            profile.applyTo(recipes);

            GregSteamExpansion.LOGGER.info(
                    "[Difficulty] Applied configurable GTCEu startup recipe profile {} (casingsPerCraft {}).",
                    difficulty,
                    recipes.casingsPerCraft);
        } else {
            GregSteamExpansion.LOGGER.info(
                    "[Difficulty] Integration disabled; keeping GTCEu configuration untouched and using GSE Normal baseline.");
        }

        startupEnabled = enabled;
        startupDifficulty = effectiveDifficulty;
        startupProfile = enabled ? profile : GSEDifficultyProfile.defaults(Difficulty.NORMAL);
        clientEnabled = enabled;
        clientDifficulty = effectiveDifficulty;
        clientProfile = startupProfile;
        initialized = true;
    }
}
