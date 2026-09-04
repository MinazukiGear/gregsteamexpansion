package com.hoshino.gregsteamexpansion.difficulty;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import org.jetbrains.annotations.Nullable;

/**
 * Save-file authority for the global difficulty, persisted in the overworld
 * data storage (difficulty.md 服务端与存档权威性). A missing or unparsable field
 * counts as "no stored tier" so the save is initialized from the config
 * request exactly once.
 */
public final class DifficultySavedData extends SavedData {
    private static final String DATA_NAME = "gregsteamexpansion_difficulty";
    private static final String TAG_DIFFICULTY = "difficulty";

    @Nullable
    private Difficulty difficulty;

    public DifficultySavedData() {}

    public static DifficultySavedData getOrCreate(MinecraftServer server) {
        return server.overworld().getDataStorage()
                .computeIfAbsent(DifficultySavedData::load, DifficultySavedData::new, DATA_NAME);
    }

    public static DifficultySavedData load(CompoundTag tag) {
        DifficultySavedData data = new DifficultySavedData();
        data.difficulty = Difficulty.byName(tag.getString(TAG_DIFFICULTY));
        return data;
    }

    /** True when the save already carries a valid persisted tier. */
    public boolean hasStoredDifficulty() {
        return difficulty != null;
    }

    public Difficulty getDifficulty() {
        return difficulty != null ? difficulty : Difficulty.NORMAL;
    }

    public void setDifficulty(Difficulty difficulty) {
        this.difficulty = difficulty;
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putString(TAG_DIFFICULTY, getDifficulty().getSerializedName());
        return tag;
    }
}
