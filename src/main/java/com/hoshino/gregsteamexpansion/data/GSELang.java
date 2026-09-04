package com.hoshino.gregsteamexpansion.data;

import com.hoshino.gregsteamexpansion.registry.GSERegistration;

public final class GSELang {
    private GSELang() {}

    public static void init() {
        add("block.gregsteamexpansion.crafting_station", "Crafting Station");
        add("block.gregsteamexpansion.crafting_station_slab", "Crafting Station Slab");
        add("gregsteamexpansion.crafting_station.in_use", "Crafting Station is in use");
        add("gregsteamexpansion.crafting_station.slab.tooltip", "Can only be placed on the side of a container.");

        // items-and-blocks.md: names and one-line gray tooltips for the plain
        // items and blocks; no shift-expansion details.
        add("block.gregsteamexpansion.steam_grinding_block", "Steam Grinding Block");
        add("gregsteamexpansion.steam_grinding_block.tooltip",
                "A heavy steam grinding structure component fitted with a diamond grinding head.");
        add("block.gregsteamexpansion.steam_assembly_block", "Steam Assembly Block");
        add("gregsteamexpansion.steam_assembly_block.tooltip",
                "A standardized assembly component for large steam machinery structures.");
        add("block.gregsteamexpansion.steam_circuit_assembly_block", "Steam Circuit Assembly Block");
        add("gregsteamexpansion.steam_circuit_assembly_block.tooltip",
                "A precision circuit assembly component for large steam machinery structures.");
        add("block.gregsteamexpansion.steam_mixing_block", "Steam Mixing Block");
        add("gregsteamexpansion.steam_mixing_block.tooltip",
                "A steam mixing structure component fitted with twin bronze rotors.");
        add("item.gregsteamexpansion.bronze_component", "Bronze Component");
        add("gregsteamexpansion.bronze_component.tooltip",
                "A standardized load-bearing component for assembling large steam machinery.");

        // EMI power display (miscellaneous.md EMI 功耗显示): combined body line
        // with the actual EU/t plus the amperage and tier the upstream view
        // already formatted. zh_cn.json carries the matching Chinese template.
        add("gregsteamexpansion.emi.recipe.eu", "Usage: %s EU/t (%s A @ %s)");
        add("gregsteamexpansion.emi.recipe.eu_inverted", "Generation: %s EU/t (%s A @ %s)");

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

        add("config.gregsteamexpansion.difficulty.easy", "Easy (MoYu)");
        add("config.gregsteamexpansion.difficulty.normal", "Normal (ShuShi)");
        add("config.gregsteamexpansion.difficulty.expert", "Expert (YaZha)");
        add("config.gregsteamexpansion.difficulty.invalid", "Invalid");
        add("config.gregsteamexpansion.difficulty.mismatch",
                "Work intensity mismatch: your client difficulty is %s, but the save requires %s. " +
                        "Set difficulty in config/gregsteamexpansion-common.toml to the save tier, " +
                        "then restart your client before reconnecting.");

        add("config.gregsteamexpansion.difficulty.select.title", "Select Work Intensity");
        add("config.gregsteamexpansion.difficulty.select.hint",
                "This save has no work intensity yet. Your choice is written to the save, " +
                        "applies to everyone, and can only be lowered later.");
        add("config.gregsteamexpansion.difficulty.select.cancelled",
                "You left without choosing a work intensity; the save stays untouched.");

        add("config.gregsteamexpansion.screen.title", "Greg Steam Expansion Config");
        add("config.gregsteamexpansion.screen.difficulty", "Work intensity (difficulty)");
        add("config.gregsteamexpansion.screen.restart",
                "Saved to the config file; a full restart is required before it takes effect.");
        add("config.gregsteamexpansion.screen.reset", "Reset to Default");
        add("config.gregsteamexpansion.request.ask", "Ask at first entry (ASK, default)");
    }

    private static void add(String key, String value) {
        GSERegistration.REGISTRATE.addRawLang(key, value);
    }
}
