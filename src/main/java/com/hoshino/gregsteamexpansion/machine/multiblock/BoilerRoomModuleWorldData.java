package com.hoshino.gregsteamexpansion.machine.multiblock;

import com.hoshino.gregsteamexpansion.GregSteamExpansion;
import com.hoshino.gregsteamexpansion.machine.module.ExternalModuleWorldData;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

public final class BoilerRoomModuleWorldData extends ExternalModuleWorldData {

    private static final String DATA_NAME = "gregsteamexpansion_boiler_room_modules";

    private BoilerRoomModuleWorldData() {
        super(BoilerRoomMachine.class);
    }

    public static BoilerRoomModuleWorldData getOrCreate(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                BoilerRoomModuleWorldData::load,
                BoilerRoomModuleWorldData::new,
                DATA_NAME);
    }

    public static BoilerRoomModuleWorldData load(CompoundTag tag) {
        BoilerRoomModuleWorldData data = new BoilerRoomModuleWorldData();
        data.loadClaims(tag);
        return data;
    }

    public static Claim waterSoftenerClaim(BlockPos controller, Direction front) {
        return BoilerRoomModules.claim(controller, front, BoilerRoomModules.WATER_SOFTENER);
    }

    @Mod.EventBusSubscriber(modid = GregSteamExpansion.MOD_ID)
    public static final class ChunkListener {

        private ChunkListener() {}

        @SubscribeEvent
        public static void onChunkLoad(ChunkEvent.Load event) {
            if (event.getLevel() instanceof ServerLevel level && event.getChunk() instanceof LevelChunk chunk) {
                getOrCreate(level).pruneStaleClaims(chunk);
            }
        }
    }
}
