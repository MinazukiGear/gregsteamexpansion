package com.hoshino.gregsteamexpansion.registry;

import com.gregtechceu.gtceu.utils.GTUtil;
import com.hoshino.gregsteamexpansion.machine.multiblock.part.SteamAirIntakeHatchPartMachine;
import com.hoshino.gregsteamexpansion.machine.multiblock.part.SteamFluidHatchPartMachine;
import com.hoshino.gregsteamexpansion.machine.multiblock.part.SteamSupplyHatchPartMachine;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidType;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/** Declarative tooltip profiles shared by machine registrations. */
final class GSEMachineTooltips {

    static final BiConsumer<ItemStack, List<Component>> STEAM_CENTRIFUGE =
            tooltip(centrifuge("steam_centrifuge"));
    static final BiConsumer<ItemStack, List<Component>> LARGE_STEAM_CENTRIFUGE =
            tooltip(centrifuge("large_steam_centrifuge"));
    static final BiConsumer<ItemStack, List<Component>> LARGE_STEAM_ASSEMBLER =
            tooltip(standard("large_steam_assembler", true));
    static final BiConsumer<ItemStack, List<Component>> LARGE_STEAM_CIRCUIT_ASSEMBLER =
            tooltip(standard("large_steam_circuit_assembler", true));
    static final BiConsumer<ItemStack, List<Component>> LARGE_STEAM_ORE_PLANT =
            tooltip(standard("large_steam_ore_plant", true));
    static final BiConsumer<ItemStack, List<Component>> LARGE_STEAM_FLUID_DRILL =
            tooltip(standard("large_steam_fluid_drill", true));
    static final BiConsumer<ItemStack, List<Component>> STEAM_CHEMICAL_BATH =
            tooltip(standard("steam_chemical_bath", false));
    static final BiConsumer<ItemStack, List<Component>> LARGE_STEAM_ORE_WASHER =
            tooltip(standard("large_steam_ore_washer", true));
    static final BiConsumer<ItemStack, List<Component>> LARGE_STEAM_MIXER =
            tooltip(standard("large_steam_mixer", true));
    static final BiConsumer<ItemStack, List<Component>> LARGE_STEAM_BLAST_FURNACE =
            tooltip(standard("large_steam_blast_furnace", true));
    static final BiConsumer<ItemStack, List<Component>> LARGE_STEAM_MACERATOR =
            tooltip(standard("large_steam_macerator", true));
    static final BiConsumer<ItemStack, List<Component>> LARGE_STEAM_THERMAL_CENTRIFUGE =
            tooltip(standard("large_steam_thermal_centrifuge", true));

    static final BiConsumer<ItemStack, List<Component>> STEAM_CRUSHER =
            tooltip(threeSection("steam_crusher", ChatFormatting.GRAY, 2, 5, 9, 8));
    static final BiConsumer<ItemStack, List<Component>> LARGE_STEAM_CRUSHER =
            tooltip(threeSection("large_steam_crusher", ChatFormatting.RED, 2, 5, 11, 10));
    static final BiConsumer<ItemStack, List<Component>> STEAM_FORGE =
            tooltip(threeSection("steam_forge", ChatFormatting.GRAY, 1, 4, 8, 7));
    static final BiConsumer<ItemStack, List<Component>> STEAM_EXTRACTOR =
            tooltip(threeSection("steam_extractor", ChatFormatting.GRAY, 1, 4, 8, 7));
    static final BiConsumer<ItemStack, List<Component>> STEAM_COMPRESSOR =
            tooltip(threeSection("steam_compressor", ChatFormatting.GRAY, 2, 5, 9, 8));

    static final BiConsumer<ItemStack, List<Component>> STEAM_SUPPLY_HATCH = tooltip(profile(
            row("steam_supply_hatch", "capacity", ChatFormatting.AQUA,
                    String.format("%,d", SteamSupplyHatchPartMachine.INITIAL_TANK_CAPACITY)),
            row("steam_supply_hatch", "accepted", ChatFormatting.GRAY),
            row("steam_supply_hatch", "summary", ChatFormatting.GRAY),
            detail("steam_supply_hatch", "details.subtitle", ChatFormatting.DARK_AQUA),
            detail("steam_supply_hatch", "details.0", ChatFormatting.GRAY),
            detail("steam_supply_hatch", "details.1", ChatFormatting.GRAY),
            detail("steam_supply_hatch", "details.2", ChatFormatting.GRAY),
            detail("steam_supply_hatch", "details.3", ChatFormatting.YELLOW)));

    static final BiConsumer<ItemStack, List<Component>> STEAM_FLUID_IMPORT_HATCH =
            tooltip(fluidHatch("import.summary"));
    static final BiConsumer<ItemStack, List<Component>> STEAM_FLUID_EXPORT_HATCH =
            tooltip(fluidHatch("export.summary"));

    static final BiConsumer<ItemStack, List<Component>> STEAM_AIR_INTAKE_HATCH = tooltip(profile(
            row("steam_air_intake_hatch", "capacity", ChatFormatting.AQUA,
                    String.valueOf(SteamAirIntakeHatchPartMachine.INITIAL_TANK_CAPACITY /
                            FluidType.BUCKET_VOLUME),
                    String.format("%,d", SteamAirIntakeHatchPartMachine.INITIAL_TANK_CAPACITY)),
            row("steam_air_intake_hatch", "rate", ChatFormatting.GRAY,
                    String.valueOf(SteamAirIntakeHatchPartMachine.COLLECT_CYCLE_TICKS),
                    String.format("%,d", SteamAirIntakeHatchPartMachine.COLLECT_AMOUNT)),
            row("steam_air_intake_hatch", "summary", ChatFormatting.GRAY),
            row("steam_air_intake_hatch", "no_output", ChatFormatting.AQUA),
            detail("steam_air_intake_hatch", "details.subtitle", ChatFormatting.DARK_AQUA),
            detail("steam_air_intake_hatch", "details.0", ChatFormatting.GRAY,
                    String.format("%,d", SteamAirIntakeHatchPartMachine.COLLECT_AMOUNT)),
            detail("steam_air_intake_hatch", "details.1", ChatFormatting.GRAY),
            detail("steam_air_intake_hatch", "details.2", ChatFormatting.GRAY),
            detail("steam_air_intake_hatch", "details.3", ChatFormatting.GRAY),
            detail("steam_air_intake_hatch", "details.4", ChatFormatting.YELLOW)));

    static final BiConsumer<ItemStack, List<Component>> STEAM_EXHAUST_HATCH = tooltip(profile(
            row("steam_exhaust_hatch", "summary.0", ChatFormatting.GRAY),
            row("steam_exhaust_hatch", "summary.1", ChatFormatting.YELLOW),
            row("steam_exhaust_hatch", "summary.2", ChatFormatting.RED),
            detail("steam_exhaust_hatch", "details.subtitle", ChatFormatting.GRAY),
            detail("steam_exhaust_hatch", "details.0", ChatFormatting.GRAY),
            detail("steam_exhaust_hatch", "details.1", ChatFormatting.YELLOW),
            detail("steam_exhaust_hatch", "details.2", ChatFormatting.GRAY, aquaText("20")),
            detail("steam_exhaust_hatch", "details.3", ChatFormatting.RED,
                    aquaText("200"), aquaText("12"), aquaText("20")),
            detail("steam_exhaust_hatch", "details.4", ChatFormatting.GRAY),
            detail("steam_exhaust_hatch", "details.5", ChatFormatting.GRAY),
            detail("steam_exhaust_hatch", "details.6", ChatFormatting.YELLOW)));

    static final BiConsumer<ItemStack, List<Component>> LARGE_HEAT_STORAGE_STEAM_FURNACE = tooltip(profile(
            row("large_heat_storage_steam_furnace", "summary.0", ChatFormatting.GRAY),
            row("large_heat_storage_steam_furnace", "summary.1", ChatFormatting.GRAY),
            row("large_heat_storage_steam_furnace", "summary.2", ChatFormatting.YELLOW),
            detail("large_heat_storage_steam_furnace", "subtitle.0", ChatFormatting.DARK_AQUA),
            detail("large_heat_storage_steam_furnace", "details.0", ChatFormatting.GRAY,
                    aquaText("7×7"), aquaText("11×11"), aquaText("15×15"), aquaText("6"), aquaText("18")),
            detail("large_heat_storage_steam_furnace", "details.1", ChatFormatting.GRAY),
            detail("large_heat_storage_steam_furnace", "details.2", ChatFormatting.GRAY),
            detail("large_heat_storage_steam_furnace", "subtitle.1", ChatFormatting.DARK_AQUA),
            detail("large_heat_storage_steam_furnace", "details.3", ChatFormatting.YELLOW),
            detail("large_heat_storage_steam_furnace", "details.4", ChatFormatting.GRAY, aquaText("1200")),
            detail("large_heat_storage_steam_furnace", "details.5", ChatFormatting.GRAY,
                    aquaText("600"), aquaText("900"), aquaText("1200"),
                    aquaText("1000"), aquaText("1500"), aquaText("2000")),
            detail("large_heat_storage_steam_furnace", "details.6", ChatFormatting.GRAY),
            detail("large_heat_storage_steam_furnace", "subtitle.2", ChatFormatting.DARK_AQUA),
            detail("large_heat_storage_steam_furnace", "details.7", ChatFormatting.GRAY, aquaText("32")),
            detail("large_heat_storage_steam_furnace", "details.8", ChatFormatting.GRAY),
            detail("large_heat_storage_steam_furnace", "details.9", ChatFormatting.GRAY),
            detail("large_heat_storage_steam_furnace", "details.10", ChatFormatting.YELLOW),
            detail("large_heat_storage_steam_furnace", "subtitle.3", ChatFormatting.DARK_AQUA),
            detail("large_heat_storage_steam_furnace", "details.11", ChatFormatting.YELLOW),
            detail("large_heat_storage_steam_furnace", "details.12", ChatFormatting.YELLOW, aquaText("3")),
            detail("large_heat_storage_steam_furnace", "details.13", ChatFormatting.RED, aquaText("12")),
            detail("large_heat_storage_steam_furnace", "subtitle.4", ChatFormatting.DARK_AQUA),
            detail("large_heat_storage_steam_furnace", "details.14", ChatFormatting.GRAY),
            detail("large_heat_storage_steam_furnace", "details.15", ChatFormatting.GRAY),
            detail("large_heat_storage_steam_furnace", "details.16", ChatFormatting.GRAY)));

    private GSEMachineTooltips() {}

    private static BiConsumer<ItemStack, List<Component>> tooltip(TooltipProfile profile) {
        return (stack, tooltip) -> append(profile, GTUtil.isShiftDown(), tooltip);
    }

    private static void append(TooltipProfile profile, boolean expanded, List<Component> tooltip) {
        for (TooltipRow row : profile.rows()) {
            if (!row.expandedOnly() || expanded) {
                tooltip.add(row.component());
            }
        }
    }

    private static TooltipProfile standard(String machine, boolean warningAtSix) {
        List<TooltipRow> rows = summaries(machine, ChatFormatting.GRAY);
        addSection(rows, machine, "details.subtitle", 0, 2, Integer.MAX_VALUE);
        addSection(rows, machine, "details.subtitle2", 3, 6, warningAtSix ? 6 : Integer.MAX_VALUE);
        addSection(rows, machine, "details.subtitle3", 7, 9, Integer.MAX_VALUE);
        return new TooltipProfile(List.copyOf(rows));
    }

    private static TooltipProfile centrifuge(String machine) {
        List<TooltipRow> rows = summaries(machine, ChatFormatting.GRAY);
        addSection(rows, machine, "details.subtitle", 0, 2, Integer.MAX_VALUE);
        addSection(rows, machine, "details.subtitle2", 3, 6, Integer.MAX_VALUE);
        rows.add(detail(machine, "details.10", ChatFormatting.GRAY));
        addSection(rows, machine, "details.subtitle3", 7, 9, Integer.MAX_VALUE);
        return new TooltipProfile(List.copyOf(rows));
    }

    private static TooltipProfile threeSection(String machine, ChatFormatting lastSummaryStyle,
                                               int firstEnd, int secondEnd, int lastEnd,
                                               int warningsFrom) {
        List<TooltipRow> rows = summaries(machine, lastSummaryStyle);
        addSection(rows, machine, "details.subtitle", 0, firstEnd, Integer.MAX_VALUE);
        addSection(rows, machine, "details.subtitle2", firstEnd + 1, secondEnd, Integer.MAX_VALUE);
        addSection(rows, machine, "details.subtitle3", secondEnd + 1, lastEnd, warningsFrom);
        return new TooltipProfile(List.copyOf(rows));
    }

    private static TooltipProfile fluidHatch(String summarySuffix) {
        return profile(
                row("steam_fluid_hatch", "capacity", ChatFormatting.AQUA,
                        String.format("%,d", SteamFluidHatchPartMachine.INITIAL_TANK_CAPACITY)),
                row("steam_fluid_hatch", summarySuffix, ChatFormatting.GRAY),
                row("steam_fluid_hatch", "not_steam_energy", ChatFormatting.AQUA),
                detail("steam_fluid_hatch", "details.subtitle", ChatFormatting.DARK_AQUA),
                detail("steam_fluid_hatch", "details.0", ChatFormatting.GRAY),
                detail("steam_fluid_hatch", "details.1", ChatFormatting.GRAY),
                detail("steam_fluid_hatch", "details.2", ChatFormatting.GRAY),
                detail("steam_fluid_hatch", "details.3", ChatFormatting.GRAY),
                detail("steam_fluid_hatch", "details.4", ChatFormatting.YELLOW));
    }

    private static List<TooltipRow> summaries(String machine, ChatFormatting lastStyle) {
        List<TooltipRow> rows = new ArrayList<>();
        rows.add(row(machine, "summary.0", ChatFormatting.GRAY));
        rows.add(row(machine, "summary.1", ChatFormatting.GRAY));
        rows.add(row(machine, "summary.2", lastStyle));
        return rows;
    }

    private static void addSection(List<TooltipRow> rows, String machine, String subtitle,
                                   int first, int last, int warningsFrom) {
        rows.add(detail(machine, subtitle, ChatFormatting.DARK_AQUA));
        for (int index = first; index <= last; index++) {
            rows.add(detail(machine, "details." + index,
                    index >= warningsFrom ? ChatFormatting.YELLOW : ChatFormatting.GRAY));
        }
    }

    private static TooltipProfile profile(TooltipRow... rows) {
        return new TooltipProfile(List.of(rows));
    }

    private static TooltipRow row(String machine, String suffix, ChatFormatting style, Object... arguments) {
        return new TooltipRow(key(machine, suffix), style, false, arguments);
    }

    private static TooltipRow detail(String machine, String suffix, ChatFormatting style, Object... arguments) {
        return new TooltipRow(key(machine, suffix), style, true, arguments);
    }

    private static String key(String machine, String suffix) {
        return "gregsteamexpansion.machine." + machine + ".tooltip." + suffix;
    }

    private static Component aquaText(String value) {
        return Component.literal(value).withStyle(ChatFormatting.AQUA);
    }

    private record TooltipProfile(List<TooltipRow> rows) {}

    private record TooltipRow(String key, ChatFormatting style, boolean expandedOnly, Object[] arguments) {
        private Component component() {
            return Component.translatable(key, arguments.clone()).withStyle(style);
        }
    }
}
