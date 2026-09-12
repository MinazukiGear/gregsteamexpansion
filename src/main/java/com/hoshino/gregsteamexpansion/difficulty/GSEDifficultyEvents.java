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
    public static void onDeclared(ServerPlayer player, @Nullable Difficulty declared) {
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
}
