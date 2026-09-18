package com.hoshino.gregsteamexpansion.terminal;

import net.minecraftforge.event.TickEvent;

public final class UltimateTerminalEvents {
    private UltimateTerminalEvents() {}

    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !TerminalCompatibility.isAvailable()) return;
        UltimateTerminalWorldData.get(event.getServer()).tick(
                event.getServer(), UltimateTerminalConfig.blocksPerTick());
        UltimateTerminalMessages.tick(event.getServer());
    }
}
