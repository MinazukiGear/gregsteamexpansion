package com.hoshino.gregsteamexpansion.machine.multiblock;

import java.util.function.BooleanSupplier;

/**
 * Shared transient state transitions for steam-powered batch and cycle engines.
 *
 * <p>Persisted progress and locked batch fields stay on each controller. This
 * component owns only the current tick's outcome, so reload formats remain
 * unchanged while pause/blocking freezes and resource-shortage rollback use one
 * implementation across every engine family.</p>
 */
public final class BatchStateMachine {

    public enum HoldReason {
        NONE,
        INPUTS,
        STEAM,
        AUXILIARY,
        OUTPUTS,
        PAUSED,
        INVALID_STRUCTURE,
        CONFIG_DISABLED,
        EXHAUST_BLOCKED,
        RECIPE_MISSING,
        ORIGINAL_SIZE_MISMATCH
    }

    public record TickResult(int progress, boolean consumed, boolean completed,
                             boolean auxiliaryExecutionShortfall) {}

    private static final BooleanSupplier AVAILABLE = () -> true;

    private HoldReason holdReason = HoldReason.NONE;
    private boolean consumedThisTick;

    /** Clears all transient results before the controller evaluates this tick. */
    public void beginTick() {
        holdReason = HoldReason.NONE;
        consumedThisTick = false;
    }

    /** Freezes progress without applying the resource-shortage rollback. */
    public void freeze(HoldReason reason) {
        holdReason = reason;
        consumedThisTick = false;
    }

    /** Structure loss keeps a live batch/cycle and rolls its progress back to one tick. */
    public int invalidate(int progress, boolean active) {
        freeze(HoldReason.INVALID_STRUCTURE);
        return active ? rollback(progress) : progress;
    }

    /** Runs a consuming tick for a machine without auxiliary input. */
    public TickResult runTick(int progress, int duration, BooleanSupplier steamDraw) {
        return runTick(progress, duration, AVAILABLE, steamDraw, AVAILABLE);
    }

    /**
     * Runs the shared auxiliary-simulate → steam-draw → auxiliary-execute plan.
     * A simulated resource shortfall consumes nothing and rolls progress back;
     * an execution-only auxiliary shortfall is reported after the steam draw,
     * matching the controllers' existing defensive warning path.
     */
    public TickResult runTick(int progress, int duration,
                              BooleanSupplier auxiliarySimulation,
                              BooleanSupplier steamDraw,
                              BooleanSupplier auxiliaryExecution) {
        if (!auxiliarySimulation.getAsBoolean()) {
            return shortage(progress, HoldReason.AUXILIARY);
        }
        if (!steamDraw.getAsBoolean()) {
            return shortage(progress, HoldReason.STEAM);
        }
        boolean auxiliaryShortfall = !auxiliaryExecution.getAsBoolean();
        holdReason = HoldReason.NONE;
        consumedThisTick = true;
        int nextProgress = progress + 1;
        return new TickResult(nextProgress, true, nextProgress >= duration, auxiliaryShortfall);
    }

    /** Records non-batch steam use, such as furnace preheating. */
    public void markConsumed() {
        holdReason = HoldReason.NONE;
        consumedThisTick = true;
    }

    public boolean consumedThisTick() {
        return consumedThisTick;
    }

    public HoldReason holdReason() {
        return holdReason;
    }

    public boolean isWaitingFor(HoldReason reason) {
        return holdReason == reason;
    }

    public void reset() {
        beginTick();
    }

    private TickResult shortage(int progress, HoldReason reason) {
        freeze(reason);
        return new TickResult(rollback(progress), false, false, false);
    }

    private static int rollback(int progress) {
        return Math.min(progress, 1);
    }
}
