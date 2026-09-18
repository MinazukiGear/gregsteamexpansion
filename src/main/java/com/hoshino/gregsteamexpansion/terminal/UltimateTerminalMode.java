package com.hoshino.gregsteamexpansion.terminal;

public enum UltimateTerminalMode {
    BUILD,
    REPAIR,
    UPGRADE,
    DISMANTLE;

    public UltimateTerminalMode next() {
        return values()[(ordinal() + 1) % values().length];
    }
}
