package com.hoshino.gregsteamexpansion.machine.multiblock.furnace;

import com.hoshino.gregsteamexpansion.difficulty.Difficulty;

/** Pure temperature formulas and state transitions for the heat-storage furnace. */
public final class FurnaceThermalLogic {

    public static final int COLD_TEMPERATURE = 20;
    public static final int MIN_WORKING_TEMPERATURE = 400;

    private FurnaceThermalLogic() {}

    public static State coldState() {
        return new State(COLD_TEMPERATURE, 0, 0, 0);
    }

    public static PreheatPlan planPreheat(int width, int height, State state,
                                         long steamLimitUnits, Difficulty difficulty) {
        if (width == 0 || state.temperature() >= maxTemperature(width)) {
            return PreheatPlan.BLOCKED;
        }
        long missingUnits = preheatCostPerDegreeUnits(width, height, difficulty)
                - state.preheatProgressUnits();
        if (missingUnits <= 0) {
            return PreheatPlan.FUNDED;
        }
        long plannedUnits = Math.min(missingUnits, steamLimitUnits);
        return new PreheatPlan(true, true, (plannedUnits + 99) / 100);
    }

    public static State advanceHeating(State state, int maximumTemperature,
                                       int intervalTicks, long addedSteamUnits) {
        int nextTimer = state.heatTimer() + 1;
        long nextProgress = state.preheatProgressUnits() + addedSteamUnits;
        int nextTemperature = state.temperature();
        if (nextTimer >= intervalTicks) {
            nextTimer = 0;
            nextProgress = 0;
            if (nextTemperature < maximumTemperature) {
                nextTemperature++;
            }
        }
        return new State(nextTemperature, nextProgress, nextTimer, state.coolTimer());
    }

    public static State cool(int width, int height, State state, boolean processing) {
        if (state.temperature() <= COLD_TEMPERATURE) {
            return state;
        }
        int nextTimer = state.coolTimer() + 1;
        int nextTemperature = state.temperature();
        if (nextTimer >= coolingIntervalTicks(width, height, state.temperature(), processing)) {
            nextTimer = 0;
            nextTemperature--;
        }
        return new State(nextTemperature, state.preheatProgressUnits(), state.heatTimer(), nextTimer);
    }

    public static int maxTemperature(int width) {
        return switch (width) {
            case 11 -> 1500;
            case 15 -> 2000;
            default -> 1000;
        };
    }

    public static int startupTemperature(int width) {
        return maxTemperature(width) * 3 / 5;
    }

    public static long preheatCostPerDegreeUnits(int width, int height, Difficulty difficulty) {
        return formedVolume(width, height) * 2 * difficulty.getPreheatCostPercent();
    }

    public static int coolingIntervalTicks(int width, int height, int temperature, boolean processing) {
        if (width == 0 || temperature <= COLD_TEMPERATURE) {
            return Integer.MAX_VALUE;
        }
        double innerArea = (double) width * width - 4;
        double volume = innerArea * height;
        double area = 2 * innerArea + 4.0 * width * height;
        double temperatureFactor = (temperature - COLD_TEMPERATURE) / 980.0;
        if (temperatureFactor <= 0) {
            return Integer.MAX_VALUE;
        }
        return (int) Math.ceil((processing ? 40 : 5) * volume / area / temperatureFactor);
    }

    public static double speedMultiplier(int width, int height, int temperature) {
        double volume = volumeProgress(width, height);
        double heat = temperatureProgress(width, temperature);
        return 1 + (2 + 22 * volume * volume * volume) * heat * heat;
    }

    public static double steamDiscount(int width, int height) {
        double volume = volumeProgress(width, height);
        return 1 / (1 + 3 * volume * volume);
    }

    public static long formedVolume(int width, int height) {
        return width == 0 ? 0 : ((long) width * width - 4) * height;
    }

    private static double volumeProgress(int width, int height) {
        if (width == 0) {
            return 0;
        }
        return Math.min(1, Math.max(0, (formedVolume(width, height) - 270) / (3978.0 - 270)));
    }

    private static double temperatureProgress(int width, int temperature) {
        int maximum = maxTemperature(width);
        if (maximum <= MIN_WORKING_TEMPERATURE) {
            return 0;
        }
        return Math.min(1, Math.max(0,
                (temperature - MIN_WORKING_TEMPERATURE) / (double) (maximum - MIN_WORKING_TEMPERATURE)));
    }

    public record State(int temperature, long preheatProgressUnits, int heatTimer, int coolTimer) {

        public State clearPreheat() {
            return new State(temperature, 0, 0, coolTimer);
        }
    }

    public record PreheatPlan(boolean shouldAdvance, boolean drawsSteam, long steamMb) {

        private static final PreheatPlan BLOCKED = new PreheatPlan(false, false, 0);
        private static final PreheatPlan FUNDED = new PreheatPlan(true, false, 0);
    }
}
