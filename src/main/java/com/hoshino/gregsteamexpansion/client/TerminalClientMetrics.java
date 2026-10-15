package com.hoshino.gregsteamexpansion.client;

import java.lang.management.ManagementFactory;

/** Explicit client benchmark only; ordinary rendering never reads clocks or allocation counters. */
public final class TerminalClientMetrics {
    public static final boolean ENABLED = Boolean.getBoolean("gse.clientBenchmark");
    private static final com.sun.management.ThreadMXBean ALLOCATIONS = allocationBean();
    static long drawNanos, ghostNanos, outlineNanos, allocatedBytes;
    static int ghosts, outlines;
    private static long start, ghostEnd, allocationStart;

    private TerminalClientMetrics() {}

    private static com.sun.management.ThreadMXBean allocationBean() {
        if (!ENABLED || !(ManagementFactory.getThreadMXBean() instanceof com.sun.management.ThreadMXBean bean)
                || !bean.isThreadAllocatedMemorySupported()) return null;
        if (!bean.isThreadAllocatedMemoryEnabled()) bean.setThreadAllocatedMemoryEnabled(true);
        return bean;
    }

    static void reset() {
        drawNanos = ghostNanos = outlineNanos = allocatedBytes = 0;
        ghosts = outlines = 0;
    }

    static void begin() {
        allocationStart = allocations();
        start = System.nanoTime();
    }

    static void ghostEnd(int count) {
        ghostEnd = System.nanoTime();
        ghostNanos = ghostEnd - start;
        ghosts = count;
    }

    static void end(int count) {
        long end = System.nanoTime();
        drawNanos = end - start;
        outlineNanos = end - ghostEnd;
        allocatedBytes = ALLOCATIONS == null ? -1 : allocations() - allocationStart;
        outlines = count;
    }

    private static long allocations() {
        return ALLOCATIONS == null ? 0 : ALLOCATIONS.getThreadAllocatedBytes(Thread.currentThread().getId());
    }

    static boolean allocationSupported() { return ALLOCATIONS != null; }
}
