package com.hoshino.gregsteamexpansion.machine.multiblock;

/**
 * Batch-start snapshot for the Large Steam Supply Hatch overclock.
 *
 * <p>The controller stores the returned values for the life of one recipe or
 * production cycle. Changing the UI toggle therefore affects only the next
 * execution, while a running execution keeps its original duration and steam
 * demand across pauses, structure loss and world reloads.</p>
 */
public final class LargeSteamOverclock {

    public static final int STEAM_PER_TICK_MULTIPLIER = 3;
    public static final int DURATION_DIVISOR = 2;

    private LargeSteamOverclock() {}

    /** Locks normal or overclocked economics for the next execution. */
    public static LockedEconomics lock(int normalDurationTicks, long normalSteamPerTickMb,
                                       boolean requested, boolean largeHatchAvailable) {
        int duration = Math.max(1, normalDurationTicks);
        long steam = Math.max(0, normalSteamPerTickMb);
        boolean active = requested && largeHatchAvailable;
        if (!active) {
            return new LockedEconomics(false, duration, steam);
        }
        return new LockedEconomics(true, duration / DURATION_DIVISOR + duration % DURATION_DIVISOR,
                saturatingMultiply(steam, STEAM_PER_TICK_MULTIPLIER));
    }

    private static long saturatingMultiply(long value, int multiplier) {
        return value > Long.MAX_VALUE / multiplier ? Long.MAX_VALUE : value * multiplier;
    }

    public record LockedEconomics(boolean active, int durationTicks, long steamPerTickMb) {}
}
