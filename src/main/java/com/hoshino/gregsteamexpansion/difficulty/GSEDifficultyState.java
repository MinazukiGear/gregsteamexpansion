package com.hoshino.gregsteamexpansion.difficulty;

import com.gregtechceu.gtceu.config.ConfigHolder;
import com.hoshino.gregsteamexpansion.GregSteamExpansion;

import org.jetbrains.annotations.Nullable;

/**
 * Per-process difficulty state: the save tier resolved at server start, the
 * tier synced to this client, and the session-scoped upstream overrides
 * (difficulty.md 上游覆盖生命周期). All fields are process-lifetime only; the
 * authoritative tier lives in {@link DifficultySavedData}.
 */
public final class GSEDifficultyState {
    @Nullable
    private static volatile Difficulty serverDifficulty;
    private static volatile Difficulty clientDifficulty = Difficulty.NORMAL;
    private static volatile boolean awaitingChoice;
    private static volatile boolean clientChoicePending;
    private static volatile boolean clientTierSynced;
    private static boolean recipeReloadPending;
    private static boolean upstreamOverridden;
    private static int originalCasingsPerCraft;

    private GSEDifficultyState() {}

    /**
     * The tier resolved from the authoritative save. Falls back to the Normal
     * baseline before a server has resolved it (used by recipe conditions
     * during the process's first datapack load and by client machines before
     * the login sync arrives).
     */
    public static Difficulty resolved() {
        Difficulty difficulty = serverDifficulty;
        return difficulty != null ? difficulty : Difficulty.NORMAL;
    }

    /** True once this process's server has resolved the save tier. */
    public static boolean isResolved() {
        return serverDifficulty != null;
    }

    static void setServerDifficulty(Difficulty difficulty) {
        serverDifficulty = difficulty;
    }

    static void clearServerDifficulty() {
        serverDifficulty = null;
        awaitingChoice = false;
    }

    /**
     * True while the save carries no difficulty field yet and the first
     * entering client must pick one (difficulty.md 服务端与存档权威性).
     */
    public static boolean isAwaitingChoice() {
        return awaitingChoice;
    }

    static void setAwaitingChoice(boolean awaiting) {
        awaitingChoice = awaiting;
    }

    /** Set by the client on the open-choice packet; consumed by the client tick hook. */
    public static void requestClientChoiceScreen() {
        clientChoicePending = true;
    }

    public static boolean isClientChoicePending() {
        return clientChoicePending;
    }

    public static void clearClientChoicePending() {
        clientChoicePending = false;
    }

    static void setRecipeReloadPending(boolean pending) {
        recipeReloadPending = pending;
    }

    static boolean isRecipeReloadPending() {
        return recipeReloadPending;
    }

    /**
     * Whether the recipe set currently in the manager (Normal baseline plus
     * pre-override upstream values, as loaded before any player could enter)
     * still needs a datapack reload to match the given tier.
     */
    static boolean needsRecipeReloadFor(Difficulty difficulty) {
        return difficulty != Difficulty.NORMAL
                || originalCasingsPerCraft != difficulty.getCasingsPerCraft();
    }

    /** Stores the tier pushed to this client after a passed login check. */
    public static void setClientDifficulty(Difficulty difficulty) {
        clientDifficulty = difficulty;
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

    public static void clearClientTierSynced() {
        clientTierSynced = false;
    }

    /** Tier for machine-side logic: client machines read the synced tier. */
    public static Difficulty current(boolean remote) {
        return remote ? clientDifficulty : resolved();
    }

    /**
     * Forces the whitelisted upstream settings (difficulty.md 已确认的上游覆盖
     * 白名单) to the values for the given tier. Idempotent within a session;
     * the pre-override GTCEu value is captured once for {@link #revertUpstreamOverrides()}.
     */
    static void applyUpstreamOverrides(Difficulty difficulty) {
        if (!upstreamOverridden) {
            originalCasingsPerCraft = ConfigHolder.INSTANCE.recipes.casingsPerCraft;
        }
        ConfigHolder.INSTANCE.recipes.casingsPerCraft = difficulty.getCasingsPerCraft();
        upstreamOverridden = true;
    }

    /**
     * Restores the upstream settings so a following save started in the same
     * process never inherits this save's tier.
     */
    static void revertUpstreamOverrides() {
        if (upstreamOverridden) {
            ConfigHolder.INSTANCE.recipes.casingsPerCraft = originalCasingsPerCraft;
            upstreamOverridden = false;
            GregSteamExpansion.LOGGER.info("[Difficulty] Restored GTCEu casingsPerCraft to {}.",
                    originalCasingsPerCraft);
        }
    }
}
