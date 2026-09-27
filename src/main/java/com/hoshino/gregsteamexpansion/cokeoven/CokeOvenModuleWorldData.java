package com.hoshino.gregsteamexpansion.cokeoven;

import com.hoshino.gregsteamexpansion.GregSteamExpansion;
import com.hoshino.gregsteamexpansion.machine.module.ExternalModuleWorldData;
import com.hoshino.gregsteamexpansion.machine.multiblock.largecokeoven.LargeCokeOvenMachine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import org.jetbrains.annotations.Nullable;

public final class CokeOvenModuleWorldData extends ExternalModuleWorldData {

    private static final String DATA_NAME = "gregsteamexpansion_coke_oven_modules";

    private CokeOvenModuleWorldData() {
        super(LargeCokeOvenMachine.class);
    }

    public static CokeOvenModuleWorldData getOrCreate(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                CokeOvenModuleWorldData::load, CokeOvenModuleWorldData::new, DATA_NAME);
    }

    public static CokeOvenModuleWorldData load(CompoundTag tag) {
        CokeOvenModuleWorldData data = new CokeOvenModuleWorldData();
        data.loadClaims(tag);
        return data;
    }

    public static Claim dryQuenchClaim(BlockPos controller, Direction front) {
        BlockPos[] bounds = LargeCokeOvenDryQuenchModule.bounds(controller, front);
        return new Claim(controller, LargeCokeOvenDryQuenchModule.ID, "right",
                LargeCokeOvenDryQuenchModule.anchor(controller, front), bounds[0], bounds[1]);
    }

    public static Claim furnaceBaseClaim(BlockPos controller, Direction front) {
        BlockPos[] bounds = LargeCokeOvenFurnaceBaseModule.bounds(controller, front);
        return new Claim(controller, LargeCokeOvenFurnaceBaseModule.ID, "down",
                LargeCokeOvenFurnaceBaseModule.anchor(controller, front), bounds[0], bounds[1]);
    }

    @Override
    protected boolean conflicts(Claim mine, Claim other) {
        if (mine.controller().equals(other.controller())) {
            return mine.faceId().equals(other.faceId());
        }
        return intersects(mine.min().offset(-1, -1, -1), mine.max().offset(1, 1, 1),
                other.min(), other.max());
    }

    @Nullable
    public BlockPos findConflict(BlockPos controller, BlockPos min, BlockPos max) {
        return findForeignConflict(controller, min, max, 1);
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
