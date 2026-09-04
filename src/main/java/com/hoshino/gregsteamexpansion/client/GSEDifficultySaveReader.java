package com.hoshino.gregsteamexpansion.client;

import com.hoshino.gregsteamexpansion.GregSteamExpansion;
import com.hoshino.gregsteamexpansion.difficulty.Difficulty;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;

import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Reads a save's persisted difficulty straight from
 * {@code <save>/data/gregsteamexpansion_difficulty.dat} without loading the
 * world, so the world list can tag save names with the tier.
 */
public final class GSEDifficultySaveReader {
    private GSEDifficultySaveReader() {}

    @Nullable
    public static Difficulty read(String levelId) {
        Path file = net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get()
                .resolve("saves").resolve(levelId)
                .resolve("data").resolve("gregsteamexpansion_difficulty.dat");
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try {
            CompoundTag root = NbtIo.readCompressed(file.toFile());
            return Difficulty.byName(root.getCompound("data").getString("difficulty"));
        } catch (IOException e) {
            GregSteamExpansion.LOGGER.warn("[Difficulty] Failed to read the difficulty of save {}.", levelId, e);
            return null;
        }
    }
}
