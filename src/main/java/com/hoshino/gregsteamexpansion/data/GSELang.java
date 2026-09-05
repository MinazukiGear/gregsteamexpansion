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

        // Ore Crushing recipe type / viewer category name (ore-crushing.md).
        add("gregsteamexpansion.ore_crushing", "Ore Crushing");

        // 蒸汽供给仓 / Steam Supply Hatch two-tier item tooltip
        // (machines-and-hatches.md 方块、模型与提示).
        add("gregsteamexpansion.machine.steam_supply_hatch.tooltip.capacity", "Fluid capacity: %s mB");
        add("gregsteamexpansion.machine.steam_supply_hatch.tooltip.accepted", "Accepted fluid: Steam");
        add("gregsteamexpansion.machine.steam_supply_hatch.tooltip.summary",
                "Supplies steam to steam multiblock machines.");
        add("gregsteamexpansion.machine.steam_supply_hatch.tooltip.details.subtitle", "Interface and Supply");
        add("gregsteamexpansion.machine.steam_supply_hatch.tooltip.details.0",
                "One steam input slot with a fixed 32,000 mB cache on every work intensity; half of the legacy upstream hatch.");
        add("gregsteamexpansion.machine.steam_supply_hatch.tooltip.details.1",
                "Only GTCEu standard steam (its own steam tag) is accepted; same-named third-party steams, superheated steam and other fluids are always rejected.");
        add("gregsteamexpansion.machine.steam_supply_hatch.tooltip.details.2",
                "Covers may be installed on the machine-facing front; a screwdriver can rotate the hatch but never swap it into an output hatch.");
        add("gregsteamexpansion.machine.steam_supply_hatch.tooltip.details.3",
                "Structure-side supply limits still apply: the cache size never means the hatch can dump all 32,000 mB in one tick.");

        // 蒸汽流体输入/输出仓 / Steam Fluid Input & Output Hatches two-tier item
        // tooltip (machines-and-hatches.md 模型、界面与提示). Both directions
        // share the details block; only the summary line differs.
        add("gregsteamexpansion.machine.steam_fluid_hatch.tooltip.capacity", "Fluid capacity: %s mB");
        add("gregsteamexpansion.machine.steam_fluid_hatch.tooltip.import.summary",
                "Provides recipe fluids to steam multiblock machines.");
        add("gregsteamexpansion.machine.steam_fluid_hatch.tooltip.export.summary",
                "Receives recipe fluids produced by steam multiblock machines.");
        add("gregsteamexpansion.machine.steam_fluid_hatch.tooltip.not_steam_energy",
                "Cannot supply energy steam to machines.");
        add("gregsteamexpansion.machine.steam_fluid_hatch.tooltip.details.subtitle", "Interface and Semantics");
        add("gregsteamexpansion.machine.steam_fluid_hatch.tooltip.details.0",
                "Exactly one 16,000 mB tank per hatch, identical on every work intensity; no machine-side mB/t flow cap.");
        add("gregsteamexpansion.machine.steam_fluid_hatch.tooltip.details.1",
                "Automation runs through the machine-facing front only: input hatches accept pipe filling, output hatches can actively export; the reverse direction is refused.");
        add("gregsteamexpansion.machine.steam_fluid_hatch.tooltip.details.2",
                "A screwdriver swap converts between input and output hatch and keeps fluid, facings, paint color, compatible covers and the fluid lock.");
        add("gregsteamexpansion.machine.steam_fluid_hatch.tooltip.details.3",
                "Crafted from bronze without power in the steam age, or with equal material cost in an assembler once LV power is available.");
        add("gregsteamexpansion.machine.steam_fluid_hatch.tooltip.details.4",
                "Only controllers whose structure specs explicitly accept the steam recipe-fluid abilities can use these hatches; filled with steam they still never count as energy.");

        // 蒸汽进气室 / Steam Air Intake Hatch two-tier item tooltip
        // (machines-and-hatches.md 方块、界面与提示).
        add("gregsteamexpansion.machine.steam_air_intake_hatch.tooltip.capacity", "Air capacity: %s B (%s mB)");
        add("gregsteamexpansion.machine.steam_air_intake_hatch.tooltip.rate",
                "Collection cycle: every %s ticks, %s mB");
        add("gregsteamexpansion.machine.steam_air_intake_hatch.tooltip.summary",
                "Collects air for formed steam multiblock machines.");
        add("gregsteamexpansion.machine.steam_air_intake_hatch.tooltip.no_output",
                "Cannot output air to pipes or containers.");
        add("gregsteamexpansion.machine.steam_air_intake_hatch.tooltip.details.subtitle", "Collection and Interface");
        add("gregsteamexpansion.machine.steam_air_intake_hatch.tooltip.details.0",
                "Collects while its multiblock is formed, the hatch stands in the Overworld, one strict-air block sits directly in front and the cache has room; each finished cycle adds up to %s mB.");
        add("gregsteamexpansion.machine.steam_air_intake_hatch.tooltip.details.1",
                "Strict air means plain, cave and void air only: snow layers, fire, plants, vines, webs, non-colliding blocks and fluids all block the intake without being cleared, and any interruption discards the unfinished cycle.");
        add("gregsteamexpansion.machine.steam_air_intake_hatch.tooltip.details.2",
                "Pipes, covers and containers can neither fill nor drain the cache; only its formed controller consumes the actual air, atomically with every other recipe condition.");
        add("gregsteamexpansion.machine.steam_air_intake_hatch.tooltip.details.3",
                "Breaking the hatch scatters its air, the dropped item never carries fluid, and unfinished cycle progress is never saved.");
        add("gregsteamexpansion.machine.steam_air_intake_hatch.tooltip.details.4",
                "The intake front rejects covers, so keep the louver grille facing open air.");
        add("gregsteamexpansion.machine.steam_air_intake_hatch.gui.next_collect", "Next collection: %s ticks");
        add("gregsteamexpansion.machine.steam_air_intake_hatch.status.collecting", "Collecting");
        add("gregsteamexpansion.machine.steam_air_intake_hatch.status.cache_full", "Cache Full");
        add("gregsteamexpansion.machine.steam_air_intake_hatch.status.intake_blocked", "Intake Blocked");
        add("gregsteamexpansion.machine.steam_air_intake_hatch.status.wrong_dimension", "Dimension Not Supported");
        add("gregsteamexpansion.machine.steam_air_intake_hatch.status.structure_not_formed", "Structure Not Formed");
        // 蒸汽粉碎机 / Steam Crusher two-tier item tooltip (steam-crushers.md
        // 物品提示与本地化). Group subtitles reuse one shared key.
        add("gregsteamexpansion.machine.steam_crusher.tooltip.summary.0",
                "Pure-steam ore-crushing multiblock with up to 8 parallel operations.");
        add("gregsteamexpansion.machine.steam_crusher.tooltip.summary.1",
                "Fixed 3\u00d73\u00d73 structure; each recipe operation takes 600 ticks and 2,400 mB of steam.");
        add("gregsteamexpansion.machine.steam_crusher.tooltip.summary.2",
                "Hold Shift for full structure and operating rules.");
        add("gregsteamexpansion.machine.steam_crusher.tooltip.details.subtitle", "Structure");
        add("gregsteamexpansion.machine.steam_crusher.tooltip.details.0",
                "Fixed at 3\u00d73\u00d73; the controller is centered on the front and one Bronze Frame occupies the structure center.");
        add("gregsteamexpansion.machine.steam_crusher.tooltip.details.1",
                "One Steam Grinding Block is required at the center of each of the top, bottom, left, and right faces.");
        add("gregsteamexpansion.machine.steam_crusher.tooltip.details.2",
                "Requires exactly one Steam Item Input Bus, one Steam Item Output Bus, and one Steam Supply Hatch; the other 18 candidate positions use Bronze Steam Machine Casings.");
        add("gregsteamexpansion.machine.steam_crusher.tooltip.details.subtitle2", "Processing and Steam");
        add("gregsteamexpansion.machine.steam_crusher.tooltip.details.3",
                "Runs ore-crushing recipes only; each batch uses one recipe at up to 8 parallel operations and always takes 600 ticks.");
        add("gregsteamexpansion.machine.steam_crusher.tooltip.details.4",
                "Each recipe operation consumes 2,400 mB of standard steam; a batch at parallel P requires 4 \u00d7 P mB/t.");
        add("gregsteamexpansion.machine.steam_crusher.tooltip.details.5",
                "Output is checked before startup as if every chanced output succeeds; parallel is reduced or the machine stays idle if everything cannot fit, and outputs are never voided.");
        add("gregsteamexpansion.machine.steam_crusher.tooltip.details.subtitle3", "Control and Status");
        add("gregsteamexpansion.machine.steam_crusher.tooltip.details.6",
                "Accepts GTCEu standard steam only, never EU; a Steam Exhaust Hatch is neither required nor allowed.");
        add("gregsteamexpansion.machine.steam_crusher.tooltip.details.7",
                "Active pause freezes progress; loss of steam or structure rolls an active batch back to 1 tick; output blocking preserves the complete pending-output list.");
        add("gregsteamexpansion.machine.steam_crusher.tooltip.details.8",
                "Removing or destroying the controller permanently clears its batch and pending outputs; the High Pressure Steam Macerator and Diamond Grinding Head installed during crafting are not returned.");
        add("gregsteamexpansion.machine.steam_crusher.tooltip.details.9",
                "Explosion destruction does not guarantee a controller drop; any surviving drop is an ordinary controller item with no machine state.");

        // 大型蒸汽粉碎机 / Large Steam Crusher two-tier item tooltip.
        add("gregsteamexpansion.machine.large_steam_crusher.tooltip.summary.0",
                "Large pure-steam ore-crushing multiblock with up to 64 parallel operations.");
        add("gregsteamexpansion.machine.large_steam_crusher.tooltip.summary.1",
                "Fixed 7\u00d77\u00d79 cylinder-and-drill structure; each recipe operation takes 600 ticks and 2,400 mB of steam.");
        add("gregsteamexpansion.machine.large_steam_crusher.tooltip.summary.2",
                "Warning: steam exhaust can severely burn entities in front of the vent; hold Shift for details.");
        add("gregsteamexpansion.machine.large_steam_crusher.tooltip.details.subtitle", "Structure");
        add("gregsteamexpansion.machine.large_steam_crusher.tooltip.details.0",
                "Fixed at 7\u00d77\u00d79, formed by a six-layer open cylinder and a five-layer top drill that overlap by two layers.");
        add("gregsteamexpansion.machine.large_steam_crusher.tooltip.details.1",
                "The center shaft requires four Bronze Pipe Casings and five Steam Grinding Blocks; the other 40 drill positions require Bronze Steam Machine Casings.");
        add("gregsteamexpansion.machine.large_steam_crusher.tooltip.details.2",
                "Requires exactly one item input bus and one Steam Exhaust Hatch; at least one item output bus and one Steam Supply Hatch are required, with at most 15 combined so the cylinder retains at least 110 Bronze Steam Machine Casings.");
        add("gregsteamexpansion.machine.large_steam_crusher.tooltip.details.subtitle2", "Processing and Steam");
        add("gregsteamexpansion.machine.large_steam_crusher.tooltip.details.3",
                "Runs ore-crushing recipes only; each batch uses one recipe at up to 64 parallel operations and always takes 600 ticks.");
        add("gregsteamexpansion.machine.large_steam_crusher.tooltip.details.4",
                "Each recipe operation consumes 2,400 mB of standard steam; a batch at parallel P requires 4 \u00d7 P mB/t, and extra supply hatches do not increase parallel or efficiency.");
        add("gregsteamexpansion.machine.large_steam_crusher.tooltip.details.5",
                "All output buses are checked before startup as if every chanced output succeeds; parallel is reduced or the machine stays idle if everything cannot fit, and outputs are never voided.");
        add("gregsteamexpansion.machine.large_steam_crusher.tooltip.details.subtitle3", "Control and Status");
        add("gregsteamexpansion.machine.large_steam_crusher.tooltip.details.6",
                "Accepts GTCEu standard steam only, never EU; compatible electric-tier or ME buses provide item logistics only.");
        add("gregsteamexpansion.machine.large_steam_crusher.tooltip.details.7",
                "The three blocks directly in front of the Steam Exhaust Hatch must be air; blockage stops steam consumption and freezes progress without the 1-tick steam-shortage rollback.");
        add("gregsteamexpansion.machine.large_steam_crusher.tooltip.details.8",
                "Every 200 accumulated running ticks, exhaust deals 12 heat damage to entities in the first block in front of the vent, except Creative and Spectator players.");
        add("gregsteamexpansion.machine.large_steam_crusher.tooltip.details.9",
                "Active pause freezes progress; loss of steam or structure rolls an active batch back to 1 tick; output blocking preserves the complete pending-output list.");
        add("gregsteamexpansion.machine.large_steam_crusher.tooltip.details.10",
                "Removing or destroying the controller permanently clears its batch, pending outputs, and exhaust timer; the Steam Crusher controller installed during crafting is not returned.");
        add("gregsteamexpansion.machine.large_steam_crusher.tooltip.details.11",
                "Explosion destruction does not guarantee a Large Steam Crusher controller drop; any surviving drop is an ordinary controller item with no machine state.");

        // Shared controller GUI info-page labels (steam-crushers.md 运行信息页).
        add("gregsteamexpansion.machine.steam_crusher.ui.status", "Status");
        add("gregsteamexpansion.machine.steam_crusher.ui.recipe", "Recipe");
        add("gregsteamexpansion.machine.steam_crusher.ui.progress", "Progress");
        add("gregsteamexpansion.machine.steam_crusher.ui.parallel", "Parallel (current / cap)");
        add("gregsteamexpansion.machine.steam_crusher.ui.steam", "Steam (total / capacity)");
        add("gregsteamexpansion.machine.steam_crusher.ui.demand", "Steam demand");
        add("gregsteamexpansion.machine.steam_crusher.ui.not_consuming", "not consuming now");
        add("gregsteamexpansion.machine.steam_crusher.ui.pending", "Pending outputs");
        add("gregsteamexpansion.machine.steam_crusher.ui.pending_summary", "%s (%s kinds)");
        add("gregsteamexpansion.machine.steam_crusher.ui.pending_detail", "Pending outputs (stable order):");
        add("gregsteamexpansion.machine.steam_crusher.ui.pending_empty", "No pending outputs.");



        // Jade lines for the air intake hatch share the GUI's server status
        // source (machines-and-hatches.md GUI/Jade 一致性).
        add("gregsteamexpansion.jade.steam_air_intake_hatch.status", "Status: %s");
        add("gregsteamexpansion.jade.steam_air_intake_hatch.air", "Air: %s / %s mB");
        add("gregsteamexpansion.jade.steam_air_intake_hatch.next_collect", "Next collection: %s ticks");
        add("config.jade.plugin_gregsteamexpansion.steam_air_intake_hatch_info", "Steam Air Intake Hatch Info");
        add("gregsteamexpansion.jade.steam_crusher.status", "Status: %s");
        add("gregsteamexpansion.jade.steam_crusher.recipe", "Recipe: %s");
        add("gregsteamexpansion.jade.steam_crusher.progress", "Progress: %s / %s tick");
        add("gregsteamexpansion.jade.steam_crusher.parallel", "Parallel: %s / %s");
        add("gregsteamexpansion.jade.steam_crusher.steam", "Steam: %s / %s mB");
        add("gregsteamexpansion.jade.steam_crusher.demand", "Steam demand: %s mB/t");
        add("gregsteamexpansion.jade.steam_crusher.pending", "Pending: %s (%s kinds)");
        add("config.jade.plugin_gregsteamexpansion.steam_crusher_info", "Steam Crusher Info");


        // Steam Exhaust Hatch two-tier item tooltip
        // (large-heat-storage-steam-furnace.md 物品提示与本地化范围).
        add("gregsteamexpansion.machine.steam_exhaust_hatch.tooltip.summary.0",
                "A dedicated exhaust interface for compatible multiblock steam machines.");        add("gregsteamexpansion.machine.steam_exhaust_hatch.tooltip.summary.1",
                "The 3 blocks straight ahead must stay air.");
        add("gregsteamexpansion.machine.steam_exhaust_hatch.tooltip.summary.2",
                "Hot steam severely burns creatures in front of the vent; hold Shift for details.");
        add("gregsteamexpansion.machine.steam_exhaust_hatch.tooltip.details.subtitle", "Interface and Exhaust");
        add("gregsteamexpansion.machine.steam_exhaust_hatch.tooltip.details.0",
                "The block front can face all six directions; exhaust and obstruction checks always run along the current front facing.");
        add("gregsteamexpansion.machine.steam_exhaust_hatch.tooltip.details.1",
                "The 3-block channel requires strict air: snow, fire, plants, non-colliding blocks and fluids all obstruct it, and the hatch never clears them automatically.");
        add("gregsteamexpansion.machine.steam_exhaust_hatch.tooltip.details.2",
                "While a compatible machine actually consumes steam, it plays an exhaust feedback every %s ticks.");
        add("gregsteamexpansion.machine.steam_exhaust_hatch.tooltip.details.3",
                "Exhaust damage is timed separately by the compatible machine's accumulated actual run ticks: the counter only increases when the machine consumes steam and advances preheating or a recipe this tick; every accumulated %s ticks it deals %s points of heat damage to creatures in the first block ahead and restarts. Creative and spectator players are unaffected; the damage cycle runs separately from the %s-tick particle and sound feedback.");
        add("gregsteamexpansion.machine.steam_exhaust_hatch.tooltip.details.4",
                "Turning off GTCEu machine sounds only mutes the exhaust sound; particles, obstruction checks and heat damage remain.");
        add("gregsteamexpansion.machine.steam_exhaust_hatch.tooltip.details.5",
                "This hatch has no GUI, internal slots or generic fluid output; it outputs no condensate, recipe fluid, pollutant or item byproducts.");
        add("gregsteamexpansion.machine.steam_exhaust_hatch.tooltip.details.6",
                "In the Large Heat-Storage Steam Furnace exactly one hatch must be installed, in the outer wall layer of the second layer from the top.");

        // Large Heat-Storage Steam Furnace two-tier item tooltip
        // (large-heat-storage-steam-furnace.md 物品提示与本地化范围).
        add("gregsteamexpansion.machine.large_heat_storage_steam_furnace.tooltip.summary.0",
                "An expensive, variable-size pure steam multiblock furnace.");
        add("gregsteamexpansion.machine.large_heat_storage_steam_furnace.tooltip.summary.1",
                "Large-scale smelting and alloy smelting for the low-voltage era.");
        add("gregsteamexpansion.machine.large_heat_storage_steam_furnace.tooltip.summary.2",
                "Hold Shift for the full structure, operation and safety rules.");
        add("gregsteamexpansion.machine.large_heat_storage_steam_furnace.tooltip.subtitle.0", "Structure");
        add("gregsteamexpansion.machine.large_heat_storage_steam_furnace.tooltip.subtitle.1", "Power and Temperature");
        add("gregsteamexpansion.machine.large_heat_storage_steam_furnace.tooltip.subtitle.2", "Recipes and Parallel");
        add("gregsteamexpansion.machine.large_heat_storage_steam_furnace.tooltip.subtitle.3", "Interfaces and Exhaust");
        add("gregsteamexpansion.machine.large_heat_storage_steam_furnace.tooltip.subtitle.4", "Terminal and Status");
        add("gregsteamexpansion.machine.large_heat_storage_steam_furnace.tooltip.details.0",
                "The outer width only allows %s, %s or %s; the total height allows any integer from %s to %s.");
        add("gregsteamexpansion.machine.large_heat_storage_steam_furnace.tooltip.details.1",
                "The four vertical corner columns must stay empty, side walls are fixed two blocks thick, top and bottom one block; the interior is free except the centre bronze pipe column.");
        add("gregsteamexpansion.machine.large_heat_storage_steam_furnace.tooltip.details.2",
                "The controller must sit at the horizontal centre of any side on the bottom layer; reforming with a changed width or height resets furnace temperature, preheat accumulation and heating/cooling timers to cold.");
        add("gregsteamexpansion.machine.large_heat_storage_steam_furnace.tooltip.details.3",
                "The machine only consumes GTCEu standard steam; EU, other same-named steams, superheated steam or universal steam tag substitutes are not accepted.");
        add("gregsteamexpansion.machine.large_heat_storage_steam_furnace.tooltip.details.4",
                "Normal steam hatches are limited to %s mB/t each in both preheating and processing; a compatible ME fluid input hatch removes the machine-side supply limit.");
        add("gregsteamexpansion.machine.large_heat_storage_steam_furnace.tooltip.details.5",
                "Every size must preheat; startup temperatures are %s, %s and %s °C, temperature limits %s, %s and %s °C.");
        add("gregsteamexpansion.machine.large_heat_storage_steam_furnace.tooltip.details.6",
                "The save's work intensity decides preheating steam cost, maximum heating rate and processing steam consumption; only Easy halves processing steam. Saves can only lower the intensity; changes need a full server restart.");
        add("gregsteamexpansion.machine.large_heat_storage_steam_furnace.tooltip.details.7",
                "All sizes support furnace recipes; only 15×15 supports manually selecting alloy smelter mode; recipes with a base input power above %s EU/t are rejected.");
        add("gregsteamexpansion.machine.large_heat_storage_steam_furnace.tooltip.details.8",
                "Maximum parallel is 64 + 16 × (height − 6), ranging 64–256; one batch processes a single recipe in the current standard steam hatch system.");
        add("gregsteamexpansion.machine.large_heat_storage_steam_furnace.tooltip.details.9",
                "Bigger furnaces and higher temperatures shorten processing time; bigger furnaces also lower total processing steam, but high-temperature speed-ups raise per-tick steam demand.");
        add("gregsteamexpansion.machine.large_heat_storage_steam_furnace.tooltip.details.10",
                "Before starting, output is pre-checked with every chance product succeeding (worst case) and parallel is lowered; the machine never voids outputs, and space changes can still block finished batches.");
        add("gregsteamexpansion.machine.large_heat_storage_steam_furnace.tooltip.details.11",
                "The structure needs at least 1 item input bus, 1 item output bus, 1 valid steam hatch and exactly 1 steam exhaust hatch; energy hatches, maintenance hatches and generic fluid output hatches are not accepted.");
        add("gregsteamexpansion.machine.large_heat_storage_steam_furnace.tooltip.details.12",
                "The exhaust hatch can only replace the outer wall body of the second layer from the top, with %s strictly air blocks in front; when obstructed the machine stops drawing steam and working, cooling at the idle rate.");
        add("gregsteamexpansion.machine.large_heat_storage_steam_furnace.tooltip.details.13",
                "While the machine actually consumes steam the exhaust blows hot steam; creatures in the first block ahead take %s points of heat damage, except creative and spectator players.");
        add("gregsteamexpansion.machine.large_heat_storage_steam_furnace.tooltip.details.14",
                "Steam consumed by preheating and processing never produces condensate, pollutants or other fluid/item byproducts.");
        add("gregsteamexpansion.machine.large_heat_storage_steam_furnace.tooltip.details.15",
                "The existing GTCEu terminal auto-builds a fixed 15×15×6 structure; a future advanced terminal would add size adjustment.");
        add("gregsteamexpansion.machine.large_heat_storage_steam_furnace.tooltip.details.16",
                "Pausing, steam shortage, exhaust obstruction, output blockage, temporary structure loss and chunk reloads all keep started batches; recovery never re-consumes inputs or re-rolls products.");
        add("gregsteamexpansion.machine.large_heat_storage_steam_furnace.mode.furnace", "Recipe mode: Furnace");
        add("gregsteamexpansion.machine.large_heat_storage_steam_furnace.mode.alloy", "Recipe mode: Alloy Smelter");
        add("gregsteamexpansion.machine.large_heat_storage_steam_furnace.mode.switch_blocked",
                "Recipe mode can only be switched on a 15×15 furnace without a running or kept batch.");
        add("gregsteamexpansion.machine.large_heat_storage_steam_furnace.status.awaiting_original_size",
                "Awaiting Original Structure Size");
        add("gregsteamexpansion.machine.large_heat_storage_steam_furnace.status.preheating", "Preheating");
        add("gregsteamexpansion.machine.large_heat_storage_steam_furnace.status.at_temperature_limit",
                "At Temperature Limit");
        add("gregsteamexpansion.machine.large_heat_storage_steam_furnace.status.cooling", "Cooling");
        add("gregsteamexpansion.multiblock.steam_exhaust_hatch_obstructed", "Steam Exhaust Hatch is Obstructed!");
        // UI info-row labels carry no placeholders: values render in a second
        // label so the raw keys must not contain %s (format-error guard).
        add("gregsteamexpansion.machine.large_heat_storage_steam_furnace.tooltip.ui.status", "Status");
        add("gregsteamexpansion.machine.large_heat_storage_steam_furnace.tooltip.ui.temperature",
                "Temperature (current/startup/max)");
        add("gregsteamexpansion.machine.large_heat_storage_steam_furnace.tooltip.ui.progress", "Progress");
        add("gregsteamexpansion.machine.large_heat_storage_steam_furnace.tooltip.ui.steam",
                "Steam demand/supply limit");
        add("gregsteamexpansion.machine.large_heat_storage_steam_furnace.tooltip.ui.parallel",
                "Parallel (current/max)");
        add("gregsteamexpansion.machine.large_heat_storage_steam_furnace.tooltip.ui.size",
                "Furnace size & volume");
        add("gregsteamexpansion.machine.large_heat_storage_steam_furnace.tooltip.ui.preheat",
                "Preheat to next °C");
        add("gregsteamexpansion.machine.large_heat_storage_steam_furnace.tooltip.ui.speed", "Batch speed");
        add("gregsteamexpansion.machine.large_heat_storage_steam_furnace.tooltip.ui.duration",
                "Batch duration");
        add("gregsteamexpansion.machine.large_heat_storage_steam_furnace.tooltip.ui.unlimited", "Unlimited");
        add("gregsteamexpansion.jade.large_heat_storage_steam_furnace.status", "Status: %s");
        add("gregsteamexpansion.jade.large_heat_storage_steam_furnace.temperature",
                "Temperature: %s°C / %s°C / %s°C");
        add("gregsteamexpansion.jade.large_heat_storage_steam_furnace.parallel", "Parallel: %s / %s");
        add("gregsteamexpansion.jade.large_heat_storage_steam_furnace.steam", "Steam: %s mB/t / %s");
        add("gregsteamexpansion.jade.large_heat_storage_steam_furnace.progress", "Progress: %s (%s)");
        add("config.jade.plugin_gregsteamexpansion.large_heat_storage_steam_furnace_info",
                "Large Heat-Storage Steam Furnace Info");

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
