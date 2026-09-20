package com.hoshino.gregsteamexpansion.difficulty;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import org.jetbrains.annotations.Nullable;

/** Multiplayer validation for the process-wide startup difficulty. */
public final class GSEDifficultyEvents {
    private GSEDifficultyEvents() {}

    /**
     * Requires the client to have started with the same tier as the server.
     * A successful declaration is synced back for client-side display and
     * machine construction.
     */
    public static void onDeclared(ServerPlayer player, boolean declaredEnabled,
                                  @Nullable Difficulty declared, String declaredProfileFingerprint) {
        boolean requiredEnabled = GSEDifficultyState.isEnabled();
        Difficulty required = GSEDifficultyState.resolved();
        if (declaredEnabled != requiredEnabled || (requiredEnabled && declared != required)) {
            Component declaredName = settingName(declaredEnabled, declared);
            player.connection.disconnect(Component.translatable(
                    "config.gregsteamexpansion.difficulty.mismatch",
                    declaredName,
                    settingName(requiredEnabled, required)));
            return;
        }
        if (!GSEDifficultyState.profileFingerprint().equals(declaredProfileFingerprint)) {
            player.connection.disconnect(Component.translatable(
                    "config.gregsteamexpansion.difficulty.profile_mismatch"));
            return;
        }
        GSEDifficultyMessages.sendDifficultySync(player, requiredEnabled, required);
    }

    private static Component settingName(boolean enabled, @Nullable Difficulty difficulty) {
        if (!enabled) {
            return Component.translatable("config.gregsteamexpansion.difficulty.disabled");
        }
        return Component.translatable(difficulty != null
                ? difficulty.getDisplayNameKey()
                : "config.gregsteamexpansion.difficulty.invalid");
    }
}
