package com.hoshino.gregsteamexpansion.terminal;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.TreeMap;

/** Persistent, deterministic structure choices shared by a machine template or one target override. */
public final class TerminalBuildProfile {
    private static final int ZERO_BASED_CHANNEL_VERSION = 2;
    private int repeatCount;
    private int coilTier;
    private final TreeMap<ResourceLocation, Integer> partTargets = new TreeMap<>();
    private final TreeMap<String, Integer> channelSelections = new TreeMap<>();

    public TerminalBuildProfile() {}

    public TerminalBuildProfile(int repeatCount, int coilTier, boolean buildHatches) {
        this.repeatCount = clamp(repeatCount, 0, 64);
        this.coilTier = clamp(coilTier, 0, 16);
    }

    public TerminalBuildProfile copy() {
        TerminalBuildProfile copy = new TerminalBuildProfile(repeatCount, coilTier, false);
        copy.partTargets.putAll(partTargets);
        copy.channelSelections.putAll(channelSelections);
        return copy;
    }

    public int repeatCount() { return repeatCount; }
    public int coilTier() { return coilTier; }
    public boolean buildHatches() { return false; }
    public Map<ResourceLocation, Integer> partTargets() { return Map.copyOf(partTargets); }
    public int channelSelection(String channelId) {
        return channelSelections.getOrDefault(channelId, UltimateTerminalConfig.AUTO_CHANNEL_SELECTION);
    }

    public void changeRepeats(int delta) { repeatCount = clamp(repeatCount + delta, 0, 64); }
    public void changeCoilTier(int delta) { coilTier = clamp(coilTier + delta, 0, 16); }
    public void changeChannel(String channelId, int delta, int maximumIndex) {
        if (channelId.equals("coil")) coilTier = 0;
        int next = clamp(channelSelection(channelId) + delta,
                UltimateTerminalConfig.AUTO_CHANNEL_SELECTION, Math.max(0, maximumIndex));
        if (next == UltimateTerminalConfig.AUTO_CHANNEL_SELECTION) channelSelections.remove(channelId);
        else channelSelections.put(channelId, next);
    }

    public void setChannel(String channelId, int selection, int maximumIndex) {
        if (channelId.equals("coil")) coilTier = 0;
        int next = clamp(selection, UltimateTerminalConfig.AUTO_CHANNEL_SELECTION, Math.max(0, maximumIndex));
        if (next == UltimateTerminalConfig.AUTO_CHANNEL_SELECTION) channelSelections.remove(channelId);
        else channelSelections.put(channelId, next);
    }

    public void changePart(ResourceLocation blockId, int delta, int maximum) {
        int next = clamp(partTargets.getOrDefault(blockId, 0) + delta, 0, Math.max(0, maximum));
        if (next == 0) partTargets.remove(blockId);
        else partTargets.put(blockId, next);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("repeatCount", repeatCount);
        tag.putInt("coilTier", coilTier);
        tag.putBoolean("buildHatches", false);
        CompoundTag parts = new CompoundTag();
        partTargets.forEach((id, count) -> parts.putInt(id.toString(), count));
        tag.put("parts", parts);
        CompoundTag channels = new CompoundTag();
        channelSelections.forEach(channels::putInt);
        tag.put("channels", channels);
        tag.putInt("channelIndexVersion", ZERO_BASED_CHANNEL_VERSION);
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
        CompoundTag channels = tag.getCompound("channels");
        boolean zeroBasedChannels = tag.getInt("channelIndexVersion") >= ZERO_BASED_CHANNEL_VERSION;
        for (String key : channels.getAllKeys()) {
            int stored = channels.getInt(key);
            int selected = zeroBasedChannels ? stored : stored - 1;
            if (key.matches("[a-z0-9_.-]{1,32}") && selected >= 0) {
                profile.channelSelections.put(key, Math.min(selected, UltimateTerminalConfig.MAX_CHANNEL_INDEX));
            }
        }
        return profile;
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
