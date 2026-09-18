package com.hoshino.gregsteamexpansion.terminal;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.TreeMap;

/** Persistent, deterministic structure choices shared by a machine template or one target override. */
public final class TerminalBuildProfile {
    private int repeatCount;
    private int coilTier;
    private boolean buildHatches = true;
    private final TreeMap<ResourceLocation, Integer> partTargets = new TreeMap<>();

    public TerminalBuildProfile() {}

    public TerminalBuildProfile(int repeatCount, int coilTier, boolean buildHatches) {
        this.repeatCount = clamp(repeatCount, 0, 64);
        this.coilTier = clamp(coilTier, 0, 16);
        this.buildHatches = buildHatches;
    }

    public TerminalBuildProfile copy() {
        TerminalBuildProfile copy = new TerminalBuildProfile(repeatCount, coilTier, buildHatches);
        copy.partTargets.putAll(partTargets);
        return copy;
    }

    public int repeatCount() { return repeatCount; }
    public int coilTier() { return coilTier; }
    public boolean buildHatches() { return buildHatches; }
    public Map<ResourceLocation, Integer> partTargets() { return Map.copyOf(partTargets); }

    public void changeRepeats(int delta) { repeatCount = clamp(repeatCount + delta, 0, 64); }
    public void changeCoilTier(int delta) { coilTier = clamp(coilTier + delta, 0, 16); }
    public void toggleHatches() { buildHatches = !buildHatches; }

    public void changePart(ResourceLocation blockId, int delta, int maximum) {
        int next = clamp(partTargets.getOrDefault(blockId, 0) + delta, 0, Math.max(0, maximum));
        if (next == 0) partTargets.remove(blockId);
        else partTargets.put(blockId, next);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("repeatCount", repeatCount);
        tag.putInt("coilTier", coilTier);
        tag.putBoolean("buildHatches", buildHatches);
        CompoundTag parts = new CompoundTag();
        partTargets.forEach((id, count) -> parts.putInt(id.toString(), count));
        tag.put("parts", parts);
        return tag;
    }

    public static TerminalBuildProfile load(CompoundTag tag) {
        TerminalBuildProfile profile = new TerminalBuildProfile(
                tag.getInt("repeatCount"), tag.getInt("coilTier"),
                !tag.contains("buildHatches") || tag.getBoolean("buildHatches"));
        CompoundTag parts = tag.getCompound("parts");
        for (String key : parts.getAllKeys()) {
            ResourceLocation id = ResourceLocation.tryParse(key);
            int count = parts.getInt(key);
            if (id != null && count > 0) profile.partTargets.put(id, Math.min(count, 8192));
        }
        return profile;
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
