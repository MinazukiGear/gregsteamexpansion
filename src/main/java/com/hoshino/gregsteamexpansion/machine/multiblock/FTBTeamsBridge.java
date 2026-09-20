package com.hoshino.gregsteamexpansion.machine.multiblock;

import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;

import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** Isolated optional-linkage bridge; callers load it only when FTB Teams is present. */
final class FTBTeamsBridge {
    private FTBTeamsBridge() {}

    static Collection<ServerPlayer> onlineMembers(UUID owner) {
        var api = FTBTeamsAPI.api();
        if (!api.isManagerLoaded()) return List.of();
        return api.getManager().getTeamForPlayerID(owner)
                .map(team -> (Collection<ServerPlayer>) team.getOnlineMembers())
                .orElseGet(List::of);
    }
}
