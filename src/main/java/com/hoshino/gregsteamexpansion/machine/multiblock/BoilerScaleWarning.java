package com.hoshino.gregsteamexpansion.machine.multiblock;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.Team;

import net.minecraftforge.fml.ModList;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/** Resolves the controller owner's online team and sends one stage-three warning. */
final class BoilerScaleWarning {
    private BoilerScaleWarning() {}

    static void send(ServerLevel level, UUID owner, Component message) {
        if (owner == null) return;
        Set<ServerPlayer> recipients = new LinkedHashSet<>();
        if (ModList.get().isLoaded("ftbteams")) {
            recipients.addAll(FTBTeamsBridge.onlineMembers(owner));
        }
        if (recipients.isEmpty()) {
            addVanillaTeam(level, owner, recipients);
        }
        if (recipients.isEmpty()) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(owner);
            if (player != null) recipients.add(player);
        }
        recipients.forEach(player -> player.sendSystemMessage(message));
    }

    private static void addVanillaTeam(ServerLevel level, UUID owner, Set<ServerPlayer> recipients) {
        ServerPlayer onlineOwner = level.getServer().getPlayerList().getPlayer(owner);
        Team team = onlineOwner == null ? null : onlineOwner.getTeam();
        if (team == null) {
            var profile = level.getServer().getProfileCache().get(owner);
            if (profile.isPresent()) {
                team = level.getServer().getScoreboard().getPlayersTeam(profile.get().getName());
            }
        }
        if (team == null) return;
        Team ownerTeam = team;
        level.getServer().getPlayerList().getPlayers().stream()
                .filter(player -> player.getTeam() == ownerTeam)
                .forEach(recipients::add);
    }
}
