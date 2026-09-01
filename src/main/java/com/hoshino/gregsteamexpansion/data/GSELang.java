package com.hoshino.gregsteamexpansion.data;

import com.hoshino.gregsteamexpansion.registry.GSERegistration;

public final class GSELang {
    private GSELang() {}

    public static void init() {
        add("gregsteamexpansion.machine.mixed_fuel_boiler.mode.liquid", "Liquid Fuel");
        add("gregsteamexpansion.machine.mixed_fuel_boiler.mode.co_firing", "Co-firing");
        add("gregsteamexpansion.machine.mixed_fuel_boiler.mode.liquid.short", "L");
        add("gregsteamexpansion.machine.mixed_fuel_boiler.mode.co_firing.short", "C");
        add("gregsteamexpansion.machine.mixed_fuel_boiler.mode.liquid.tooltip",
                "Liquid fuel only; rated output is 16 mB/t (LP) or 40 mB/t (HP)");
        add("gregsteamexpansion.machine.mixed_fuel_boiler.mode.co_firing.tooltip",
                "Liquid fuel plus co-firing dust; rated output is 24 mB/t (LP) or 60 mB/t (HP)");

        add("gregsteamexpansion.machine.mixed_fuel_boiler.status.dry_boiler", "Dry!");
        add("gregsteamexpansion.machine.mixed_fuel_boiler.status.missing_water", "No Water");
        add("gregsteamexpansion.machine.mixed_fuel_boiler.status.missing_co_firing_fuel", "No Dust");
        add("gregsteamexpansion.machine.mixed_fuel_boiler.status.missing_liquid_fuel", "No Fuel");
        add("gregsteamexpansion.machine.mixed_fuel_boiler.status.steam_output_blocked", "Venting");
        add("gregsteamexpansion.machine.mixed_fuel_boiler.status.heating", "Heating");
        add("gregsteamexpansion.machine.mixed_fuel_boiler.status.running", "Running");
        add("gregsteamexpansion.machine.mixed_fuel_boiler.status.idle", "Idle");

        add("gregsteamexpansion.machine.mixed_fuel_boiler.tooltip.co_firing",
                "Co-firing raises steam output by 50% and consumes dust at 30% equivalent heat input");

        add("gregsteamexpansion.jade.mixed_fuel_boiler.mode", "Mode: %s");
        add("gregsteamexpansion.jade.mixed_fuel_boiler.status", "Status: %s");
        add("gregsteamexpansion.jade.mixed_fuel_boiler.temperature", "Temperature: %s / %s K");
        add("gregsteamexpansion.jade.mixed_fuel_boiler.steam_output", "Steam Output: %s mB/t");
        add("gregsteamexpansion.jade.mixed_fuel_boiler.powder_time", "Co-firing Dust: %s s remaining");

        // Jade validates every plugin toggle against the active language during
        // client startup in development environments.
        add("config.jade.plugin_gregsteamexpansion.mixed_fuel_boiler_info", "Mixed-Fuel Boiler Info");
    }

    private static void add(String key, String value) {
        GSERegistration.REGISTRATE.addRawLang(key, value);
    }
}
