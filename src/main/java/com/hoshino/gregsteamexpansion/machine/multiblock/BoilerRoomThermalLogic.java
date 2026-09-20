package com.hoshino.gregsteamexpansion.machine.multiblock;

import com.hoshino.gregsteamexpansion.difficulty.Difficulty;
import com.hoshino.gregsteamexpansion.difficulty.GSEDifficultyProfile;

/** Pure temperature, descaling and water-scale transitions for boiler rooms. */
public final class BoilerRoomThermalLogic {
    private static final double TICKS_PER_HOUR = 72_000.0;

    private BoilerRoomThermalLogic() {}

    public static long steamOutputPerTick(int temperature, int throttle, Difficulty difficulty) {
        return steamOutputPerTick(temperature, throttle,
                (float) GSEDifficultyProfile.defaults(difficulty).boilerRoomSteamOutputMultiplier());
    }

    public static long steamOutputPerTick(int temperature, int throttle, float difficultyMultiplier) {
        return Math.round(temperature * (double) throttle / 20.0
                * BoilerRoomMachine.CO_FIRING_MULTIPLIER * difficultyMultiplier);
    }

    public static long applyScaleLoss(long cleanOutput, int lossPercent) {
        return Math.round(cleanOutput * Math.max(0, 100 - lossPercent) / 100.0);
    }

    public static double scaleIncrement(long cleanOutput, long maximumCleanOutput,
                                        int cycleTicks, double failureHours) {
        if (cleanOutput <= 0 || maximumCleanOutput <= 0 || cycleTicks <= 0 || failureHours <= 0.0) {
            return 0.0;
        }
        double load = Math.min(1.0, cleanOutput / (double) maximumCleanOutput);
        return cycleTicks * load / (failureHours * TICKS_PER_HOUR);
    }

    public static TickResult advance(ThermalState state, TickInput input) {
        int temperature = state.temperature();
        int heatCounter = state.heatCounter();
        int coolCounter = state.coolCounter();
        int descalingRemaining = state.descalingTicksRemaining();
        int descalingTotal = state.descalingTicksTotal();
        double scaleProgress = state.scaleProgress();
        boolean markDirty = false;

        if (descalingRemaining > 0) {
            if (temperature > 0 && ++coolCounter >= input.cooldownIntervalTicks()) {
                coolCounter = 0;
                temperature--;
            }
            descalingRemaining--;
            if (descalingRemaining <= 0) {
                descalingRemaining = 0;
                descalingTotal = 0;
                scaleProgress = Math.max(0.0, scaleProgress - BoilerRoomMachine.SCALE_STAGE_SIZE);
                markDirty = true;
            } else if (descalingRemaining % 20 == 0) {
                markDirty = true;
            }
            return new TickResult(new ThermalState(temperature, heatCounter, coolCounter,
                    descalingRemaining, descalingTotal, scaleProgress), true, markDirty);
        }

        if (input.scrapped()) {
            if (temperature > 0 && ++coolCounter >= input.cooldownIntervalTicks()) {
                coolCounter = 0;
                temperature--;
            }
            return new TickResult(new ThermalState(temperature, heatCounter, coolCounter,
                    descalingRemaining, descalingTotal, scaleProgress), true, false);
        }

        if (input.working()) {
            if (input.airAvailable() && ++heatCounter >= input.heatIntervalTicks()) {
                heatCounter = 0;
                if (temperature < input.maximumTemperature()) {
                    temperature++;
                }
            }
        } else {
            int interval = input.missingPowder()
                    ? input.noPowderCooldownIntervalTicks()
                    : input.cooldownIntervalTicks();
            if (++coolCounter >= interval) {
                coolCounter = 0;
                if (temperature > 0) temperature--;
            }
        }
        return new TickResult(new ThermalState(temperature, heatCounter, coolCounter,
                descalingRemaining, descalingTotal, scaleProgress), false, false);
    }

    public static ScaleUpdate addScale(double currentProgress, double increment,
                                       double warningThreshold, double scrapThreshold) {
        if (increment <= 0.0) {
            return new ScaleUpdate(currentProgress, false, false);
        }
        double next = Math.min(scrapThreshold, currentProgress + increment);
        return new ScaleUpdate(next,
                currentProgress < warningThreshold && next >= warningThreshold,
                next >= scrapThreshold);
    }

    public record ThermalState(int temperature, int heatCounter, int coolCounter,
                               int descalingTicksRemaining, int descalingTicksTotal,
                               double scaleProgress) {}

    public record TickInput(boolean working, boolean airAvailable, boolean missingPowder,
                            boolean scrapped, int maximumTemperature, int heatIntervalTicks,
                            int cooldownIntervalTicks, int noPowderCooldownIntervalTicks) {}

    public record TickResult(ThermalState state, boolean suppressSteamGeneration, boolean markDirty) {}

    public record ScaleUpdate(double progress, boolean warningCrossed, boolean scrapped) {}
}
