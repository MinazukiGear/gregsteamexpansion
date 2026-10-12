package com.hoshino.gregsteamexpansion.terminal;

/** Opt-in benchmark timings; no player, preview, inventory or world data is retained. */
public final class TerminalPreviewMetrics {
    public enum Stage { REFRESH, PREVIEW, PLAN, REQUIREMENTS, MATERIALS, SNAPSHOT, DIFF, ENCODE, CANDIDATES }

    private static final boolean ENABLED = Boolean.getBoolean("gse.refreshBenchmark");
    private static final ThreadLocal<Sample> ACTIVE = new ThreadLocal<>();

    private TerminalPreviewMetrics() {}

    public record Sample(long[] nanos, long[] calls) {}

    public static void begin() {
        if (ENABLED) ACTIVE.set(new Sample(new long[Stage.values().length], new long[Stage.values().length]));
    }

    public static Sample end() {
        Sample sample = ACTIVE.get();
        ACTIVE.remove();
        return sample;
    }

    public static long start() {
        return ENABLED && ACTIVE.get() != null ? System.nanoTime() : 0;
    }

    public static void finish(Stage stage, long start) {
        if (start == 0) return;
        Sample sample = ACTIVE.get();
        if (sample != null) {
            sample.nanos()[stage.ordinal()] += System.nanoTime() - start;
            sample.calls()[stage.ordinal()]++;
        }
    }
}
