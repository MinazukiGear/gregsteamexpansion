package com.hoshino.gregsteamexpansion.difficulty;

import com.hoshino.gregsteamexpansion.GregSteamExpansion;

import java.util.Objects;

/** Coordinates GSE's standalone config with an optional modpack authority. */
public final class GSEDifficultyAuthority {
    private static GSEDifficultyProvider externalProvider;

    private GSEDifficultyAuthority() {}

    /**
     * Registers the sole external authority before Forge config loading begins.
     * An external provider always enables difficulty integration.
     */
    public static synchronized void registerExternalProvider(GSEDifficultyProvider provider) {
        Objects.requireNonNull(provider, "provider");
        String owner = requireOwner(provider.ownerModId());
        if (GSEDifficultyState.isResolved()) {
            throw new IllegalStateException(
                    "Cannot register difficulty provider " + owner + " after GSE difficulty was resolved");
        }
        if (externalProvider != null) {
            throw new IllegalStateException(
                    "GSE difficulty provider already registered by " + externalProvider.ownerModId());
        }
        externalProvider = provider;
        GregSteamExpansion.LOGGER.info("[Difficulty] External authority registered by {}.", owner);
    }

    /** Called when GSE's own config is ready. It is used only without an external provider. */
    static synchronized void resolveStandalone(boolean enabled, Difficulty difficulty,
                                               GSEDifficultyProfile profile) {
        if (externalProvider != null) {
            resolveExternalProvider();
            if (!GSEDifficultyState.isResolved()) {
                GregSteamExpansion.LOGGER.info(
                        "[Difficulty] Waiting for external authority {} to finish loading its config.",
                        externalProvider.ownerModId());
            }
            return;
        }
        GSEDifficultyState.initializeAtStartup(enabled, difficulty, profile);
    }

    /**
     * Captures the registered provider after its config has loaded. Repeated calls are harmless.
     */
    public static synchronized void resolveExternalProvider() {
        if (GSEDifficultyState.isResolved()) {
            return;
        }
        GSEDifficultyProvider provider = externalProvider;
        if (provider == null) {
            throw new IllegalStateException("No external GSE difficulty provider is registered");
        }
        GSEDifficultySelection selection = provider.selection();
        if (selection == null) {
            return;
        }
        GSEDifficultyState.initializeAtStartup(true, selection.difficulty(), selection.profile());
        GregSteamExpansion.LOGGER.info(
                "[Difficulty] {} controls the effective startup tier and profile.",
                provider.ownerModId());
    }

    /** Fails before gameplay setup if a registered authority never supplied its config. */
    public static synchronized void requireResolved() {
        if (GSEDifficultyState.isResolved()) {
            return;
        }
        String owner = externalProvider != null ? externalProvider.ownerModId() : "standalone config";
        throw new IllegalStateException(
                "GSE difficulty was not resolved by " + owner + " before common setup");
    }

    public static synchronized boolean isExternallyManaged() {
        return externalProvider != null;
    }

    public static synchronized String externalOwnerModId() {
        return externalProvider != null ? externalProvider.ownerModId() : "";
    }

    private static String requireOwner(String owner) {
        if (owner == null || owner.isBlank()) {
            throw new IllegalArgumentException("Difficulty provider owner mod id must not be blank");
        }
        return owner;
    }
}
