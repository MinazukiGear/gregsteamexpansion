package com.hoshino.gregsteamexpansion.terminal;

import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraft.server.level.ServerLevel;

public final class UltimateTerminalEvents {
    private UltimateTerminalEvents() {}

    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !TerminalCompatibility.isAvailable()) return;
        UltimateTerminalWorldData.get(event.getServer()).tick(
                event.getServer(), UltimateTerminalConfig.blocksPerTick());
        UltimateTerminalMessages.tick(event.getServer());
    }

    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!event.isCanceled() && event.getLevel() instanceof ServerLevel level) {
            UltimateTerminalWorldData.get(level.getServer()).invalidateOwnership(level.dimension(), event.getPos());
        }
    }

    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.isCanceled() || !(event.getLevel() instanceof ServerLevel level)) return;
        var data = UltimateTerminalWorldData.get(level.getServer());
        if (event instanceof BlockEvent.EntityMultiPlaceEvent multiple) {
            multiple.getReplacedBlockSnapshots().forEach(snapshot ->
                    data.invalidateOwnership(level.dimension(), snapshot.getPos()));
        } else {
            data.invalidateOwnership(level.dimension(), event.getPos());
        }
    }
}
