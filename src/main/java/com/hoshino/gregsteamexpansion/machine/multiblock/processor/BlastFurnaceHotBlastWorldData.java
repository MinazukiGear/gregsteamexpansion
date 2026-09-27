package com.hoshino.gregsteamexpansion.machine.multiblock.processor;

import com.hoshino.gregsteamexpansion.GregSteamExpansion;
import com.hoshino.gregsteamexpansion.machine.module.ExternalModuleWorldData;
import com.hoshino.gregsteamexpansion.machine.module.ModuleStructure;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import org.jetbrains.annotations.Nullable;

public final class BlastFurnaceHotBlastWorldData extends ExternalModuleWorldData {

    private static final String DATA_NAME = "gregsteamexpansion_hot_blast_modules";

    private BlastFurnaceHotBlastWorldData() {
        super(LargeSteamBlastFurnaceMachine.class);
    }

    public static BlastFurnaceHotBlastWorldData getOrCreate(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                BlastFurnaceHotBlastWorldData::load,
                BlastFurnaceHotBlastWorldData::new,
                DATA_NAME);
    }

    public static BlastFurnaceHotBlastWorldData load(CompoundTag tag) {
        BlastFurnaceHotBlastWorldData data = new BlastFurnaceHotBlastWorldData();
        data.loadClaims(tag, BlastFurnaceHotBlastModule.ID, "back");
        return data;
    }

    public static Claim claimFor(BlockPos controller, Direction front) {
        BlockPos[] bounds = BlastFurnaceHotBlastModule.bounds(controller, front);
        return new Claim(controller, BlastFurnaceHotBlastModule.ID, "back",
                BlastFurnaceHotBlastModule.anchor(controller, front), bounds[0], bounds[1]);
    }

    public static Claim highChargeClaimFor(BlockPos controller, Direction front) {
        BlockPos[] bounds = BlastFurnaceHighChargeModule.bounds(controller, front);
        return new Claim(controller, BlastFurnaceHighChargeModule.ID, "top",
                BlastFurnaceHighChargeModule.anchor(controller, front), bounds[0], bounds[1]);
    }

    public static Claim bodyClaimFor(BlockPos controller, Direction front) {
        BlockPos[] bounds = ModuleStructure.orderedBounds(
                ModuleStructure.local(controller, front, -6, 0, 0),
                ModuleStructure.local(controller, front, 6, 12, 14));
        return new Claim(controller, BODY_ID, "body", controller, bounds[0], bounds[1]);
    }

    @Override
    protected boolean conflicts(Claim mine, Claim other) {
        if (BODY_ID.equals(other.moduleId()) && other.controller().equals(mine.controller())) {
            return false;
        }
        return super.conflicts(mine, other);
    }

    public void release(BlockPos controller) {
        release(controller, BlastFurnaceHotBlastModule.ID);
    }

    @Nullable
    public Claim claimOf(BlockPos controller) {
        return claimOf(controller, BlastFurnaceHotBlastModule.ID);
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
