package com.hoshino.gregsteamexpansion.machine.multiblock;

/** Stable water-scale bands shared by machine logic, UI, Jade and tests. */
public enum BoilerScaleStage {
    CLEAN(0),
    STAGE_1(1),
    STAGE_2(2),
    STAGE_3(3),
    SCRAPPED(4);

    private final int index;

    BoilerScaleStage(int index) {
        this.index = index;
    }

    public int index() {
        return index;
    }

    public static BoilerScaleStage fromProgress(double progress, boolean scrapped) {
        if (scrapped || progress >= 1.0) return SCRAPPED;
        if (progress >= 0.75) return STAGE_3;
        if (progress >= 0.50) return STAGE_2;
        if (progress >= 0.25) return STAGE_1;
        return CLEAN;
    }
}
