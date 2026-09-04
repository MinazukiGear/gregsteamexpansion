package com.hoshino.gregsteamexpansion.difficulty;

import com.hoshino.gregsteamexpansion.GregSteamExpansion;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;

import org.jetbrains.annotations.Nullable;

/**
 * Server lifecycle for the global difficulty (difficulty.md 服务端与存档权威性 and
 * 上游覆盖生命周期): resolve the save tier at server start, refuse upgrades with
 * a warning, accept downgrades permanently, apply the session-scoped upstream
 * overrides, queue one datapack reload when the initial recipe load cannot
 * match the resolved tier, and gate client logins on an exact tier match.
 */
public final class GSEDifficultyEvents {
    private GSEDifficultyEvents() {}

    /**
     * Resolves the save tier once the server is up. Runs at
     * {@link ServerStartedEvent} on purpose: the levels (and with them the
     * SavedData storage) only exist after loadWorld, which fires between
     * AboutToStart and Started, and no player can join before Started.
     */
    public static void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        DifficultySavedData data = DifficultySavedData.getOrCreate(server);
        GSEDifficultyConfig.Request request = GSEDifficultyConfig.capturedRequest();
        Difficulty requestedTier = request.difficulty();
        if (!data.hasStoredDifficulty()) {
            if (requestedTier == null) {
                // Uninitialized save with an ASK request (default): run on the
                // Normal baseline without writing a tier; the first entering
                // player picks one in game.
                GSEDifficultyState.applyUpstreamOverrides(Difficulty.NORMAL);
                GSEDifficultyState.setServerDifficulty(Difficulty.NORMAL);
                GSEDifficultyState.setRecipeReloadPending(false);
                GSEDifficultyState.setAwaitingChoice(true);
                GregSteamExpansion.LOGGER.info(
                        "[Difficulty] Save has no difficulty field yet; the first entering player will choose one.");
                return;
            }
            // Uninitialized save with a concrete config request: the config
            // file initializes the save without the in-game choice.
            Difficulty resolved = requestedTier;
            data.setDifficulty(resolved);
            GSEDifficultyState.applyUpstreamOverrides(resolved);
            GSEDifficultyState.setServerDifficulty(resolved);
            GSEDifficultyState.setRecipeReloadPending(GSEDifficultyState.needsRecipeReloadFor(resolved));
            GregSteamExpansion.LOGGER.info(
                    "[Difficulty] Save had no difficulty field; initialized to {} from the config request.",
                    resolved);
            GregSteamExpansion.LOGGER.info(
                    "[Difficulty] Save difficulty is {} (casingsPerCraft {}, datapack reload {}).",
                    resolved, resolved.getCasingsPerCraft(),
                    GSEDifficultyState.isRecipeReloadPending() ? "queued" : "not needed");
        } else {
            Difficulty stored = data.getDifficulty();
            Difficulty resolved;
            if (requestedTier != null && requestedTier.isLowerThan(stored)) {
                resolved = requestedTier;
                data.setDifficulty(resolved);
                GregSteamExpansion.LOGGER.info("[Difficulty] Accepted permanent difficulty downgrade {} -> {}.",
                        stored, resolved);
            } else if (requestedTier != null && stored.isLowerThan(requestedTier)) {
                resolved = stored;
                GregSteamExpansion.LOGGER.warn(
                        "[Difficulty] Refused difficulty upgrade {} -> {}; the save stays at {}.",
                        stored, requestedTier, stored);
            } else {
                resolved = stored;
            }

            // The process's first datapack load ran before any world existed,
            // so it used the Normal recipe baseline and the original upstream
            // config values. Reload whenever that cannot match the tier.
            GSEDifficultyState.applyUpstreamOverrides(resolved);
            GSEDifficultyState.setServerDifficulty(resolved);
            GSEDifficultyState.setRecipeReloadPending(GSEDifficultyState.needsRecipeReloadFor(resolved));
            GregSteamExpansion.LOGGER.info(
                    "[Difficulty] Save difficulty is {} (casingsPerCraft {}, datapack reload {}).",
                    resolved, resolved.getCasingsPerCraft(),
                    GSEDifficultyState.isRecipeReloadPending() ? "queued" : "not needed");
        }

        if (GSEDifficultyState.isRecipeReloadPending()) {
            GSEDifficultyState.setRecipeReloadPending(false);
            GregSteamExpansion.LOGGER.info(
                    "[Difficulty] Reloading datapacks so recipes match difficulty {}.",
                    GSEDifficultyState.resolved());
            reloadResources(server);
        }
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        GSEDifficultyState.revertUpstreamOverrides();
        GSEDifficultyState.clearServerDifficulty();
    }

    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && GSEDifficultyState.isAwaitingChoice()) {
            // Uninitialized save: the entering client picks the save tier; the
            // declaration gate below is skipped for the choice flow.
            GSEDifficultyMessages.sendOpenChoice(player);
        }
    }

    /**
     * Gate on the client's play-phase declaration, which arrives right after
     * the join completes: a mismatch (or an invalid declaration, including an
     * ASK config on an initialized save) is refused immediately, and a pass
     * triggers the tier sync for client-side machine construction.
     */
    public static void onDeclared(ServerPlayer player, @Nullable Difficulty declared) {
        if (GSEDifficultyState.isAwaitingChoice()) {
            return;
        }
        Difficulty required = GSEDifficultyState.resolved();
        if (declared != required) {
            Component declaredName = Component.translatable(declared != null
                    ? declared.getDisplayNameKey()
                    : "config.gregsteamexpansion.difficulty.invalid");
            player.connection.disconnect(Component.translatable(
                    "config.gregsteamexpansion.difficulty.mismatch",
                    declaredName,
                    Component.translatable(required.getDisplayNameKey())));
            return;
        }
        GSEDifficultyMessages.sendDifficultySync(player, required);
    }

    /**
     * Server side of the first-entry choice: the submitting client initializes
     * the save tier (first submission wins); a client whose choice arrives
     * after someone else initialized falls back to the exact-match rules.
     */
    public static void onDifficultyChosen(ServerPlayer player, Difficulty chosen) {
        if (!GSEDifficultyState.isAwaitingChoice()) {
            Difficulty save = GSEDifficultyState.resolved();
            if (chosen == save) {
                GSEDifficultyMessages.sendDifficultySync(player, save);
            } else {
                player.connection.disconnect(Component.translatable(
                        "config.gregsteamexpansion.difficulty.mismatch",
                        Component.translatable(chosen.getDisplayNameKey()),
                        Component.translatable(save.getDisplayNameKey())));
            }
            return;
        }
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        DifficultySavedData.getOrCreate(server).setDifficulty(chosen);
        GSEDifficultyState.setAwaitingChoice(false);
        GSEDifficultyState.setServerDifficulty(chosen);
        GSEDifficultyState.applyUpstreamOverrides(chosen);
        GregSteamExpansion.LOGGER.info(
                "[Difficulty] Save difficulty initialized to {} by the first entering player.", chosen);
        if (GSEDifficultyState.needsRecipeReloadFor(chosen)) {
            GregSteamExpansion.LOGGER.info(
                    "[Difficulty] Reloading datapacks so recipes match difficulty {}.", chosen);
            reloadResources(server);
        }
        GSEDifficultyMessages.sendDifficultySync(player, chosen);
    }

    private static void reloadResources(MinecraftServer server) {
        server.reloadResources(server.getPackRepository().getSelectedIds())
                .whenComplete((unused, error) -> {
                    if (error != null) {
                        GregSteamExpansion.LOGGER.error(
                                "[Difficulty] Difficulty datapack reload failed.", error);
                    } else {
                        GregSteamExpansion.LOGGER.info("[Difficulty] Difficulty datapack reload finished.");
                    }
                });
    }
}
