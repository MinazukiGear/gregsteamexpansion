package com.hoshino.gregsteamexpansion.machine.multiblock;

import java.util.function.IntToLongFunction;

/**
 * Four-position manual throttle for large steam consumers. A locked throttle
 * lowers per-tick demand and extends duration while preserving the unthrottled
 * batch's exact nominal steam total; the final tick consumes any remainder.
 */
public final class SteamThrottle {

    public static final int MIN_PERCENT = 25;
    public static final int MAX_PERCENT = 100;
    public static final int STEP_PERCENT = 25;

    private SteamThrottle() {}

    public static int normalize(int percent) {
        int bounded = Math.max(MIN_PERCENT, Math.min(MAX_PERCENT, percent));
        return Math.max(MIN_PERCENT,
                Math.min(MAX_PERCENT, ((bounded + STEP_PERCENT / 2) / STEP_PERCENT) * STEP_PERCENT));
    }

    public static int step(int currentPercent, boolean increase) {
        int current = normalize(currentPercent);
        return Math.max(MIN_PERCENT, Math.min(MAX_PERCENT,
                current + (increase ? STEP_PERCENT : -STEP_PERCENT)));
    }

    public static LockedEconomics lock(int durationTicks, long steamPerTickMb, int throttlePercent) {
        int baseDuration = Math.max(1, durationTicks);
        long baseDemand = Math.max(0, steamPerTickMb);
        int throttle = normalize(throttlePercent);
        long total = saturatedMultiply(baseDemand, baseDuration);
        if (baseDemand == 0) {
            return new LockedEconomics(throttle, baseDuration, 0, 0);
        }

        long throttledDemand = ceilPercent(baseDemand, throttle);
        long duration = ceilDiv(total, throttledDemand);
        if (duration > Integer.MAX_VALUE) {
            duration = Integer.MAX_VALUE;
            throttledDemand = ceilDiv(total, duration);
        }
        return new LockedEconomics(throttle, (int) duration, throttledDemand, total);
    }

    /** Redistributes an exact total across an already locked duration. */
    public static LockedEconomics spread(long totalAmount, int durationTicks, int throttlePercent) {
        long total = Math.max(0, totalAmount);
        int duration = Math.max(1, durationTicks);
        long perTick = total == 0 ? 0 : ceilDiv(total, duration);
        return new LockedEconomics(normalize(throttlePercent), duration, perTick, total);
    }

    /** Duration scaled inversely with throttle, with no loss from integer division. */
    public static int scaledDuration(int durationTicks, int throttlePercent) {
        long scaled = ceilDiv((long) Math.max(1, durationTicks) * MAX_PERCENT,
                normalize(throttlePercent));
        return (int) Math.min(Integer.MAX_VALUE, scaled);
    }

    /**
     * Finds the largest parallel/station count whose locked per-tick demand
     * fits the current input throughput. The caller supplies the complete
     * demand calculation so overclock, exhaust efficiency and throttle are
     * all accounted for before any recipe input is consumed.
     */
    public static int largestSupportedCount(int candidate, long inputLimitMb,
                                            IntToLongFunction lockedDemandMb) {
        if (candidate <= 0 || inputLimitMb <= 0) {
            return 0;
        }
        for (int count = candidate; count >= 1; count--) {
            if (lockedDemandMb.applyAsLong(count) <= inputLimitMb) {
                return count;
            }
        }
        return 0;
    }

    private static long ceilPercent(long value, int percent) {
        long hundreds = value / 100;
        long remainder = value % 100;
        return hundreds * percent + (remainder * percent + 99) / 100;
    }

    private static long ceilDiv(long value, long divisor) {
        return value / divisor + (value % divisor == 0 ? 0 : 1);
    }

    private static long saturatedMultiply(long value, int multiplier) {
        if (value == 0 || multiplier == 0) {
            return 0;
        }
        return value > Long.MAX_VALUE / multiplier ? Long.MAX_VALUE : value * multiplier;
    }

    public record LockedEconomics(int throttlePercent, int durationTicks,
                                  long steamPerTickMb, long totalSteamMb) {

        public long steamForProgress(int progress) {
            if (steamPerTickMb <= 0 || totalSteamMb <= 0) {
                return 0;
            }
            long completed = saturatedMultiply(steamPerTickMb, Math.max(0, progress));
            long remaining = Math.max(0, totalSteamMb - completed);
            return Math.min(steamPerTickMb, remaining);
        }
    }
}
