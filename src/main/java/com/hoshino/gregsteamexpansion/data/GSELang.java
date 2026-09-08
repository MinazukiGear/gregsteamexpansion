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

        // 蒸汽压缩机 / Steam Compressor two-tier item tooltip
        // (steam-compressor.md 议题 9). Shares the processor-family GUI label
        // prefix and Jade keys with the later extractor/forge hammer.
        add("gregsteamexpansion.machine.steam_compressor.tooltip.summary.0",
                "Pure-steam compressor multiblock running full compressor recipes at up to 8 parallel operations.");
        add("gregsteamexpansion.machine.steam_compressor.tooltip.summary.1",
                "Fixed 3\u00d73\u00d73 structure; each recipe takes 1.5\u00d7 its base duration and 2 mB of steam per EU.");
        add("gregsteamexpansion.machine.steam_compressor.tooltip.summary.2",
                "Hold Shift for full structure and operating rules.");
        add("gregsteamexpansion.machine.steam_compressor.tooltip.details.subtitle", "Structure");
        add("gregsteamexpansion.machine.steam_compressor.tooltip.details.0",
                "Fixed at 3\u00d73\u00d73; the controller sits front-bottom-centre and a Bronze Frame forms the compression core at the structure centre.");
        add("gregsteamexpansion.machine.steam_compressor.tooltip.details.1",
                "A vanilla piston sits at the back wall centre, not extended and pushing towards the frame; it is a fixed structure block.");
        add("gregsteamexpansion.machine.steam_compressor.tooltip.details.2",
                "Requires at least one steam item input bus, one steam item output bus and one Steam Supply Hatch, with at most 8 buses and hatches combined; the other candidate positions use Bronze Steam Machine Casings.");
        add("gregsteamexpansion.machine.steam_compressor.tooltip.details.subtitle2", "Processing and Steam");
        add("gregsteamexpansion.machine.steam_compressor.tooltip.details.3",
                "Runs gtceu:compressor recipes only; each batch uses one recipe at up to 8 parallel operations with duration ceil(base \u00d7 1.5).");
        add("gregsteamexpansion.machine.steam_compressor.tooltip.details.4",
                "Recipes above LV voltage are rejected. Each operation consumes recipe EU/t \u00d7 duration \u00d7 2 mB of standard steam, and parallel scales steam demand linearly.");
        add("gregsteamexpansion.machine.steam_compressor.tooltip.details.5",
                "Output is checked before startup as if every chanced output succeeds; parallel is reduced or the machine stays idle if everything cannot fit, and outputs are never voided.");
        add("gregsteamexpansion.machine.steam_compressor.tooltip.details.subtitle3", "Control and Status");
        add("gregsteamexpansion.machine.steam_compressor.tooltip.details.6",
                "Accepts GTCEu standard steam only, never EU; no fluid hatches and no Steam Exhaust Hatch.");
        add("gregsteamexpansion.machine.steam_compressor.tooltip.details.7",
                "The last successful recipe is preferred; active pause freezes progress; loss of steam or structure rolls an active batch back to 1 tick.");
        add("gregsteamexpansion.machine.steam_compressor.tooltip.details.8",
                "Removing or destroying the controller permanently clears its batch, pending outputs and recipe preference; the piston and gear installed during crafting are not returned.");
        add("gregsteamexpansion.machine.steam_compressor.tooltip.details.9",
                "All three difficulty tiers behave identically.");

        // 大型蒸汽洗矿厂 / Large Steam Ore Washer (large-steam-ore-washer.md).
        add("gregsteamexpansion.machine.large_steam_ore_washer.tooltip.summary.0",
                "Pure-steam ore washer multiblock running ALL gtceu:ore_washer recipes at up to 64 parallel operations.");
        add("gregsteamexpansion.machine.large_steam_ore_washer.tooltip.summary.1",
                "Fixed 11\u00d711\u00d76 structure; each recipe takes 1.5\u00d7 its base duration and 2 mB of steam per EU.");
        add("gregsteamexpansion.machine.large_steam_ore_washer.tooltip.summary.2",
                "Hold Shift for the full structure and operating rules.");
        add("gregsteamexpansion.machine.large_steam_ore_washer.tooltip.details.subtitle",
                "Structure");
        add("gregsteamexpansion.machine.large_steam_ore_washer.tooltip.details.0",
                "Fixed 11\u00d711\u00d76 hollow shell: edges are industrial steam machine casings (96 incl. the front-bottom-centre controller), the top face is any glass (81), walls and floor are steam machine casings (225).");
        add("gregsteamexpansion.machine.large_steam_ore_washer.tooltip.details.1",
                "Layer 2 interior carries a 17-block Steam Mixing Block cross (central row + central column); it is structure-only and grants no bonuses.");
        add("gregsteamexpansion.machine.large_steam_ore_washer.tooltip.details.2",
                "Hatches replace ONLY wall/floor steam machine casing positions: at least 1 item input bus, 1 item output bus, 1 steam supply hatch, 1 GTCEu standard fluid input hatch and exactly 1 steam exhaust hatch, at most 20 hatches in total.");
        add("gregsteamexpansion.machine.large_steam_ore_washer.tooltip.details.subtitle2",
                "Processing & Steam");
        add("gregsteamexpansion.machine.large_steam_ore_washer.tooltip.details.3",
                "Runs every gtceu:ore_washer recipe (standard, fast, distilled, dirty/pure dust washing); one recipe per batch, up to 64 parallel, duration = base \u00d7 1.5 (rounded up); circuits enter through the item input bus.");
        add("gregsteamexpansion.machine.large_steam_ore_washer.tooltip.details.4",
                "Recipes above LV voltage are rejected. Each batch consumes recipe EU/t \u00d7 duration \u00d7 2 mB of standard steam; demand scales linearly with parallel (full 64-parallel standard washing needs \u22652 supply hatches and \u22654 fluid input hatches).");
        add("gregsteamexpansion.machine.large_steam_ore_washer.tooltip.details.5",
                "Water enters ONLY through GTCEu standard fluid input hatches; the mod's steam fluid hatches are forbidden anywhere in the structure.");
        add("gregsteamexpansion.machine.large_steam_ore_washer.tooltip.details.6",
                "The steam exhaust hatch must face open air (3 blocks straight ahead); an obstructed exhaust freezes progress and vents nothing until cleared, and venting deals heat damage on its cycle.");
        add("gregsteamexpansion.machine.large_steam_ore_washer.tooltip.details.subtitle3",
                "Control & Status");
        add("gregsteamexpansion.machine.large_steam_ore_washer.tooltip.details.7",
                "The last successful recipe is preferred; active pause freezes progress; loss of steam or structure rolls an active batch back to 1 tick.");
        add("gregsteamexpansion.machine.large_steam_ore_washer.tooltip.details.8",
                "Removing the controller drops only pending outputs and loses the recipe preference; the controller holds no internal item or water storage.");
        add("gregsteamexpansion.machine.large_steam_ore_washer.tooltip.details.9",
                "All three difficulty tiers behave identically.");

        // 大型蒸汽热力离心机 / Large Steam Thermal Centrifuge (C0b).
        add("gregsteamexpansion.machine.large_steam_thermal_centrifuge.tooltip.summary.0",
                "Pure-steam thermal centrifuge multiblock running ALL gtceu:thermal_centrifuge recipes at up to 64 parallel operations.");
        add("gregsteamexpansion.machine.large_steam_thermal_centrifuge.tooltip.summary.1",
                "Fixed 9\u00d79\u00d77 stepped tower; each recipe takes 1.5\u00d7 its base duration and 2 mB of steam per EU.");
        add("gregsteamexpansion.machine.large_steam_thermal_centrifuge.tooltip.summary.2",
                "Hold Shift for the full structure and operating rules.");
        add("gregsteamexpansion.machine.large_steam_thermal_centrifuge.tooltip.details.subtitle",
                "Structure");
        add("gregsteamexpansion.machine.large_steam_thermal_centrifuge.tooltip.details.0",
                "Fixed 9\u00d79\u00d77 stepped tower: edges, top face and the two solid 5\u00d75 crown layers are industrial steam machine casings (174 incl. the front-bottom-centre controller), the bottom layer centre is a 7\u00d77 bronze firebox hearth (49), the three wall layers are steam machine casings (84).");
        add("gregsteamexpansion.machine.large_steam_thermal_centrifuge.tooltip.details.1",
                "The interior carries 11 Steam Mixing Blocks (central axis column on layers 2-4 plus impeller crosses on layers 2 and 4); they are structure-only and grant no bonuses.");
        add("gregsteamexpansion.machine.large_steam_thermal_centrifuge.tooltip.details.2",
                "Hatches replace ONLY wall steam machine casing positions: at least 1 item input bus, 1 item output bus and 1 steam supply hatch, exactly 1 steam exhaust hatch, at most 16 hatches in total; the type is pure-dry, so no fluid hatch of any kind can be placed.");
        add("gregsteamexpansion.machine.large_steam_thermal_centrifuge.tooltip.details.subtitle2",
                "Processing & Steam");
        add("gregsteamexpansion.machine.large_steam_thermal_centrifuge.tooltip.details.3",
                "Runs every gtceu:thermal_centrifuge recipe (crushed/purified ore \u2192 centrifuged ore and add-on routes); one recipe per batch, up to 64 parallel, duration = base \u00d7 1.5 (rounded up).");
        add("gregsteamexpansion.machine.large_steam_thermal_centrifuge.tooltip.details.4",
                "Recipes above LV voltage are rejected. Each operation consumes recipe EU/t \u00d7 duration \u00d7 2 mB of standard steam (base 30 EU/t \u00d7 600 ticks = 36,000 mB); demand scales linearly with parallel (full 64-parallel needs \u22654 supply hatches).");
        add("gregsteamexpansion.machine.large_steam_thermal_centrifuge.tooltip.details.5",
                "Outputs only leave through the item output buses; outputs are prechecked at worst case before startup and are never voided.");
        add("gregsteamexpansion.machine.large_steam_thermal_centrifuge.tooltip.details.6",
                "The steam exhaust hatch must face open air (3 blocks straight ahead); an obstructed exhaust freezes progress and vents nothing until cleared, and venting deals heat damage on its cycle.");
        add("gregsteamexpansion.machine.large_steam_thermal_centrifuge.tooltip.details.subtitle3",
                "Control & Status");
        add("gregsteamexpansion.machine.large_steam_thermal_centrifuge.tooltip.details.7",
                "The last successful recipe is preferred; active pause freezes progress; loss of steam or structure rolls an active batch back to 1 tick.");
        add("gregsteamexpansion.machine.large_steam_thermal_centrifuge.tooltip.details.8",
                "Removing the controller drops only pending outputs and loses the recipe preference; the controller holds no internal item storage.");
        add("gregsteamexpansion.machine.large_steam_thermal_centrifuge.tooltip.details.9",
                "All three difficulty tiers behave identically.");

        // 大型蒸汽研磨厂 / Large Steam Macerator (A4).
        add("gregsteamexpansion.machine.large_steam_macerator.tooltip.summary.0",
                "Pure-steam macerator multiblock running ALL gtceu:macerator recipes (incl. MaceratorLogic tool breakdowns) at up to 64 parallel operations.");
        add("gregsteamexpansion.machine.large_steam_macerator.tooltip.summary.1",
                "Fixed spherical 7\u00d77\u00d77 structure; each recipe takes 1.5\u00d7 its base duration and 2 mB of steam per EU.");
        add("gregsteamexpansion.machine.large_steam_macerator.tooltip.summary.2",
                "Hold Shift for the full structure and operating rules.");
        add("gregsteamexpansion.machine.large_steam_macerator.tooltip.details.subtitle",
                "Structure");
        add("gregsteamexpansion.machine.large_steam_macerator.tooltip.details.0",
                "Fixed spherical structure in a 7\u00d77\u00d77 bounding box (179 blocks): a 98-block bronze steam machine casing shell (97 casings + the front-equator-centre controller) around a six-armed cross of 13 Steam Grinding Blocks (centre + both blocks along \u00b1x/\u00b1y/\u00b1z) and 68 blocks of interior air.");
        add("gregsteamexpansion.machine.large_steam_macerator.tooltip.details.1",
                "The Steam Grinding Blocks are structure-only and grant no bonuses.");
        add("gregsteamexpansion.machine.large_steam_macerator.tooltip.details.2",
                "Hatches replace ONLY shell steam machine casing positions: at least 1 item input bus, 1 item output bus and 1 steam supply hatch, exactly 1 steam exhaust hatch, at most 12 hatches in total; the type is pure-dry, so no fluid hatch of any kind can be placed.");
        add("gregsteamexpansion.machine.large_steam_macerator.tooltip.details.subtitle2",
                "Processing & Steam");
        add("gregsteamexpansion.machine.large_steam_macerator.tooltip.details.3",
                "Runs every gtceu:macerator recipe (crushed/purified/refined ore \u2192 dust routes, material grinding, recycling and dynamic tool breakdowns; add-on recipes are picked up automatically); one recipe per batch, up to 64 parallel, duration = base \u00d7 1.5 (rounded up).");
        add("gregsteamexpansion.machine.large_steam_macerator.tooltip.details.4",
                "Recipes above LV voltage are rejected. Each operation consumes recipe EU/t \u00d7 duration \u00d7 2 mB of standard steam (ore maceration 2 EU/t \u00d7 600 ticks = 2,400 mB; default base 2 EU/t \u00d7 225 ticks = 900 mB); demand scales linearly with parallel.");
        add("gregsteamexpansion.machine.large_steam_macerator.tooltip.details.5",
                "Outputs (up to 4 slots incl. 14% chance byproducts) only leave through the item output buses; outputs are prechecked at worst case before startup and are never voided.");
        add("gregsteamexpansion.machine.large_steam_macerator.tooltip.details.6",
                "The steam exhaust hatch must face open air (3 blocks straight ahead); an obstructed exhaust freezes progress and vents nothing until cleared, and venting deals heat damage on its cycle.");
        add("gregsteamexpansion.machine.large_steam_macerator.tooltip.details.subtitle3",
                "Control & Status");
        add("gregsteamexpansion.machine.large_steam_macerator.tooltip.details.7",
                "The last successful recipe is preferred; active pause freezes progress; loss of steam or structure rolls an active batch back to 1 tick.");
        add("gregsteamexpansion.machine.large_steam_macerator.tooltip.details.8",
                "Removing the controller drops only pending outputs and loses the recipe preference; the controller holds no internal item storage.");
        add("gregsteamexpansion.machine.large_steam_macerator.tooltip.details.9",
                "All three difficulty tiers behave identically.");

        // 大型蒸汽搅拌机 / Large Steam Mixer (B3).
        add("gregsteamexpansion.machine.large_steam_mixer.tooltip.summary.0",
                "Pure-steam mixer multiblock running ALL gtceu:mixer recipes (mixed dusts, rubber compounding, growth medium and more) at up to 16 parallel operations.");
        add("gregsteamexpansion.machine.large_steam_mixer.tooltip.summary.1",
                "Fixed 7\u00d75\u00d75 structure; each recipe takes 1.5\u00d7 its base duration and 2 mB of steam per EU.");
        add("gregsteamexpansion.machine.large_steam_mixer.tooltip.summary.2",
                "Hold Shift for the full structure and operating rules.");
        add("gregsteamexpansion.machine.large_steam_mixer.tooltip.details.subtitle",
                "Structure");
        add("gregsteamexpansion.machine.large_steam_mixer.tooltip.details.0",
                "Fixed 7\u00d75\u00d75 two-zone structure (175 blocks): the bottom/top faces and all 12 edges are industrial steam machine casings (81 incl. the front-bottom-centre controller), the four walls are steam machine casings (48), and the 5\u00d73\u00d73 interior carries 7 Steam Mixing Blocks (axis column on layers 2-4 + the equidistant impeller cross on layer 3) and 38 blocks of air.");
        add("gregsteamexpansion.machine.large_steam_mixer.tooltip.details.1",
                "The Steam Mixing Blocks are structure-only and grant no bonuses.");
        add("gregsteamexpansion.machine.large_steam_mixer.tooltip.details.2",
                "Hatches replace ONLY wall steam machine casing positions: at least 1 item input bus, 1 item output bus, 1 steam supply hatch, 1 fluid input hatch (carries up to the type's 2 fluid input slots) and 1 fluid output hatch, exactly 1 steam exhaust hatch, at most 16 hatches in total. Both fluid hatch families are admissible - GTCEu standard hatches or the mod's steam fluid hatches, freely mixed.");
        add("gregsteamexpansion.machine.large_steam_mixer.tooltip.details.subtitle2",
                "Processing & Steam");
        add("gregsteamexpansion.machine.large_steam_mixer.tooltip.details.3",
                "Runs every gtceu:mixer recipe (mixed dusts, rubber compounding, growth medium, battery chemistry, platinum-group processing and more; add-on recipes are picked up automatically); one recipe per batch, up to 16 parallel, duration = base \u00d7 1.5 (rounded up).");
        add("gregsteamexpansion.machine.large_steam_mixer.tooltip.details.4",
                "Recipes above LV voltage are rejected. Each operation consumes recipe EU/t \u00d7 duration \u00d7 2 mB of standard steam; demand scales linearly with parallel.");
        add("gregsteamexpansion.machine.large_steam_mixer.tooltip.details.5",
                "Outputs (up to 1 item slot + 1 fluid slot) only leave through the item output buses and fluid output hatches; outputs are prechecked at worst case before startup and are never voided.");
        add("gregsteamexpansion.machine.large_steam_mixer.tooltip.details.6",
                "The steam exhaust hatch must face open air (3 blocks straight ahead); an obstructed exhaust freezes progress and vents nothing until cleared, and venting deals heat damage on its cycle.");
        add("gregsteamexpansion.machine.large_steam_mixer.tooltip.details.subtitle3",
                "Control & Status");
        add("gregsteamexpansion.machine.large_steam_mixer.tooltip.details.7",
                "The last successful recipe is preferred; active pause freezes progress; loss of steam or structure rolls an active batch back to 1 tick.");
        add("gregsteamexpansion.machine.large_steam_mixer.tooltip.details.8",
                "Removing the controller drops pending output items, loses their fluids and the recipe preference; the controller holds no internal storage.");
        add("gregsteamexpansion.machine.large_steam_mixer.tooltip.details.9",
                "All three difficulty tiers behave identically.");

        // 大型蒸汽高炉 / Large Steam Blast Furnace (large-steam-blast-furnace.md,
        // 2026-09-09 裁定: 极高造价与极大的结构换取极高效率).
        add("gregsteamexpansion.machine.large_steam_blast_furnace.tooltip.summary.0",
                "A pure-steam MEGASTRUCTURE blast furnace running ALL gtceu:primitive_blast_furnace recipes (iron dust + fuel \u2192 wrought iron, iron or wrought iron + fuel \u2192 steel) at up to 96 parallel operations \u2014 240\u00d7 the primitive blast furnace's throughput.");
        add("gregsteamexpansion.machine.large_steam_blast_furnace.tooltip.summary.1",
                "Colossal three-stage tapered tower, 13\u00d713 at the base and 15 tall, walled in ~370 blast bricks; each recipe takes 0.4\u00d7 its base duration with a flat 200 mB of steam per tick per parallel plus scaling tuyere air.");
        add("gregsteamexpansion.machine.large_steam_blast_furnace.tooltip.summary.2",
                "Hold Shift for the full structure and operating rules.");
        add("gregsteamexpansion.machine.large_steam_blast_furnace.tooltip.details.subtitle",
                "Structure");
        add("gregsteamexpansion.machine.large_steam_blast_furnace.tooltip.details.0",
                "Fixed three-stage tapered megastructure, 13\u00d713 footprint and 15 tall (733 solid blocks): the hearth is a 48-block industrial ring (incl. the front-centre controller) around a 121-block coke-brick bed; the 13\u00d713 tuyere deck and nine 11\u00d711 shaft layers are walled in blast bricks (368, gtceu:firebricks - the primitive blast furnace's own material); a solid 9\u00d79 throat cap and the 5\u00d75\u00d73 chimney crown complete the taper.");
        add("gregsteamexpansion.machine.large_steam_blast_furnace.tooltip.details.1",
                "The coke-brick hearth bed is structure-only and grants no bonuses. The walls alone take ~370 blast bricks (\u2248 1,500 fireclay bricks) \u2014 a true megaproject in bricks; wrought iron stays reserved for the controller and the steel machinery it unlocks.");
        add("gregsteamexpansion.machine.large_steam_blast_furnace.tooltip.details.2",
                "Hatches replace ONLY wall blast brick positions: at least 1 item input bus, 1 item output bus and 1 steam supply hatch, EXACTLY 1 steam exhaust hatch, at least 1 steam air intake hatch (tuyere, up to 8), at most 28 hatches in total. No fluid hatch of any family is admissible. Full-load reference: 16 supply hatches + 2 buses + 1 exhaust + 8 tuyeres = 27.");
        add("gregsteamexpansion.machine.large_steam_blast_furnace.tooltip.details.subtitle2",
                "Processing & Steam");
        add("gregsteamexpansion.machine.large_steam_blast_furnace.tooltip.details.3",
                "Runs every gtceu:primitive_blast_furnace recipe (steel from iron or wrought iron plus coal, charcoal or coke; wrought iron comes from iron dust + fuel here, on top of the upstream iron nugget smelting route); one recipe per batch, up to 96 parallel, duration = base \u00d7 0.4 (rounded up) \u2014 240\u00d7 the primitive blast furnace's throughput.");
        add("gregsteamexpansion.machine.large_steam_blast_furnace.tooltip.details.4",
                "The recipe type carries no EU/t: steam is a flat 200 mB/t per parallel \u2014 19,200 mB/t at full load, exactly 16 supply hatches at their 1,200 mB/t caps. Every consuming tick also draws blast air at 4 mB/t per parallel (384 mB/t full load) across the tuyeres; each intake collects 50 mB/t, so sustained full load needs ALL 8 tuyeres. An air or steam shortfall rolls the batch back to 1 tick and the status shows blast air shortage when air runs out.");
        add("gregsteamexpansion.machine.large_steam_blast_furnace.tooltip.details.5",
                "Outputs only leave through the item output buses; worst-case outputs are prechecked before startup and are never voided.");
        add("gregsteamexpansion.machine.large_steam_blast_furnace.tooltip.details.6",
                "The steam exhaust hatch must face open air (3 blocks straight ahead); an obstructed exhaust freezes progress and vents nothing until cleared, and venting deals heat damage on its cycle.");
        add("gregsteamexpansion.machine.large_steam_blast_furnace.tooltip.details.subtitle3",
                "Control & Status");
        add("gregsteamexpansion.machine.large_steam_blast_furnace.tooltip.details.7",
                "The last successful recipe is preferred; active pause freezes progress; loss of steam, blast air or structure rolls an active batch back to 1 tick.");
        add("gregsteamexpansion.machine.large_steam_blast_furnace.tooltip.details.8",
                "Removing the controller drops only pending outputs and loses the recipe preference; the controller holds no internal item storage.");
        add("gregsteamexpansion.machine.large_steam_blast_furnace.tooltip.details.9",
                "All three difficulty tiers behave identically.");
        add("gregsteamexpansion.machine.large_steam_blast_furnace.low_blast",
                "Blast Air Shortage");
        add("gregsteamexpansion.multiblock.auxiliary_shortfall",
                "Auxiliary supply shortage");

        // 蒸汽化学浸洗厂 / Steam Chemical Bath (B4).
        add("gregsteamexpansion.machine.steam_chemical_bath.tooltip.summary.0",
                "Pure-steam chemical bath multiblock running ALL gtceu:chemical_bath recipes (ore bathing, paper making, treated planks and more) at up to 8 parallel operations.");
        add("gregsteamexpansion.machine.steam_chemical_bath.tooltip.summary.1",
                "Fixed 3\u00d74\u00d73 structure; each recipe takes 1.5\u00d7 its base duration and 2 mB of steam per EU.");
        add("gregsteamexpansion.machine.steam_chemical_bath.tooltip.summary.2",
                "Hold Shift for the full structure and operating rules.");
        add("gregsteamexpansion.machine.steam_chemical_bath.tooltip.details.subtitle",
                "Structure");
        add("gregsteamexpansion.machine.steam_chemical_bath.tooltip.details.0",
                "Fixed 3\u00d74\u00d73 structure (36 blocks): a 34-block industrial steam machine casing shell (incl. the front-bottom-centre controller) around 2 blocks of air forming the immersion chamber in the middle layer's centre column.");
        add("gregsteamexpansion.machine.steam_chemical_bath.tooltip.details.1",
                "The structure has no dedicated internals; blocks may be shared with neighbouring structures without interference.");
        add("gregsteamexpansion.machine.steam_chemical_bath.tooltip.details.2",
                "Hatches replace ONLY shell casing positions: at least 1 item input bus, 1 item output bus, 1 steam supply hatch and 1 fluid input hatch (the bath liquid inlet), at most 8 hatches in total. NO fluid output hatch and NO steam exhaust hatch may be installed. The fluid input hatch may be a GTCEu standard hatch or the mod's steam fluid input hatch, freely mixed.");
        add("gregsteamexpansion.machine.steam_chemical_bath.tooltip.details.subtitle2",
                "Processing & Steam");
        add("gregsteamexpansion.machine.steam_chemical_bath.tooltip.details.3",
                "Runs every gtceu:chemical_bath recipe (crushed ore bathing to purified ore with 70%/40% byproduct chances, paper making, treated planks, decoration and miscellaneous recipes; add-on recipes are picked up automatically); one recipe per batch, up to 8 parallel, duration = base \u00d7 1.5 (rounded up).");
        add("gregsteamexpansion.machine.steam_chemical_bath.tooltip.details.4",
                "Recipes above LV voltage are rejected. Each operation consumes recipe EU/t \u00d7 duration \u00d7 2 mB of standard steam; demand scales linearly with parallel.");
        add("gregsteamexpansion.machine.steam_chemical_bath.tooltip.details.5",
                "The machine has NO fluid output hatch: recipes that produce fluids have nowhere to deliver them and fail the startup precheck. Item outputs leave through the item output buses, prechecked at worst case and never voided.");
        add("gregsteamexpansion.machine.steam_chemical_bath.tooltip.details.6",
                "No exhaust hatch: the machine has no exhaust obstruction check and no heat-hazard cycles at all.");
        add("gregsteamexpansion.machine.steam_chemical_bath.tooltip.details.subtitle3",
                "Control & Status");
        add("gregsteamexpansion.machine.steam_chemical_bath.tooltip.details.7",
                "The last successful recipe is preferred; active pause freezes progress; loss of steam or structure rolls an active batch back to 1 tick.");
        add("gregsteamexpansion.machine.steam_chemical_bath.tooltip.details.8",
                "Removing the controller drops pending output items and loses the recipe preference; the controller holds no internal storage.");
        add("gregsteamexpansion.machine.steam_chemical_bath.tooltip.details.9",
                "All three difficulty tiers behave identically.");

        // 蒸汽离心机 / Steam Centrifuge (C0 small).
        add("gregsteamexpansion.machine.steam_centrifuge.tooltip.summary.0",
                "Pure-steam centrifuge multiblock running ALL gtceu:centrifuge recipes (isotope separation, dust sorting, fluid processing and more) at up to 8 parallel operations.");
        add("gregsteamexpansion.machine.steam_centrifuge.tooltip.summary.1",
                "Fixed 3\u00d74\u00d73 structure; each recipe takes 1.5\u00d7 its base duration and 2 mB of steam per EU.");
        add("gregsteamexpansion.machine.steam_centrifuge.tooltip.summary.2",
                "Hold Shift for the full structure and operating rules.");
        add("gregsteamexpansion.machine.steam_centrifuge.tooltip.details.subtitle",
                "Structure");
        add("gregsteamexpansion.machine.steam_centrifuge.tooltip.details.0",
                "Fixed 3\u00d74\u00d73 structure (36 blocks): a 33-block bronze steam machine casing shell (incl. the front-bottom-centre controller) whose two interior middle-layer cells are filled by a pair of Steam Mixing Blocks - the rotor, with no air cavity.");
        add("gregsteamexpansion.machine.steam_centrifuge.tooltip.details.1",
                "The Steam Mixing Blocks are structure-only and grant no bonuses.");
        add("gregsteamexpansion.machine.steam_centrifuge.tooltip.details.2",
                "Hatches replace ONLY shell casing positions: at least 1 item input bus, 1 item output bus, 1 steam supply hatch, 1 fluid input hatch and 1 fluid output hatch, at most 8 hatches in total. NO exhaust hatch (small steam multiblocks use none). Both fluid hatch families are admissible - GTCEu standard hatches or the mod's steam fluid hatches, freely mixed.");
        add("gregsteamexpansion.machine.steam_centrifuge.tooltip.details.subtitle2",
                "Processing & Steam");
        add("gregsteamexpansion.machine.steam_centrifuge.tooltip.details.3",
                "Runs every gtceu:centrifuge recipe (add-on recipes are picked up automatically); the thermal centrifuge route stays upstream. One recipe per batch, up to 8 parallel, duration = base \u00d7 1.5 (rounded up).");
        add("gregsteamexpansion.machine.steam_centrifuge.tooltip.details.4",
                "Recipes above LV voltage are rejected. Each operation consumes recipe EU/t \u00d7 duration \u00d7 2 mB of standard steam; demand scales linearly with parallel.");
        add("gregsteamexpansion.machine.steam_centrifuge.tooltip.details.5",
                "Outputs (up to 6 item slots + 6 fluid slots) leave through the item output buses and fluid output hatches; outputs are prechecked at worst case before startup and are never voided.");
        add("gregsteamexpansion.machine.steam_centrifuge.tooltip.details.6",
                "No exhaust hatch: the machine has no exhaust obstruction check and no heat-hazard cycles at all.");
        add("gregsteamexpansion.machine.steam_centrifuge.tooltip.details.subtitle3",
                "Control & Status");
        add("gregsteamexpansion.machine.steam_centrifuge.tooltip.details.7",
                "The last successful recipe is preferred; active pause freezes progress; loss of steam or structure rolls an active batch back to 1 tick.");
        add("gregsteamexpansion.machine.steam_centrifuge.tooltip.details.8",
                "Removing the controller drops pending output items, loses their fluids and the recipe preference; the controller holds no internal storage.");
        add("gregsteamexpansion.machine.steam_centrifuge.tooltip.details.9",
                "All three difficulty tiers behave identically.");
        add("gregsteamexpansion.machine.steam_centrifuge.tooltip.details.10",
                "Steam Air Intake Hatch: optional, at most one, and it counts as one of the 8 hatches. Only its 64,000 mB cache can feed the 10,000 mB air dose of air separation; recipes that consume air are sorted last so they never starve the item recipes.");

        // 大型蒸汽离心机 / Large Steam Centrifuge (C0 large).
        add("gregsteamexpansion.machine.large_steam_centrifuge.tooltip.summary.0",
                "Large pure-steam centrifuge multiblock running ALL gtceu:centrifuge recipes at up to 64 parallel operations - the scaled-up upper tier of the Steam Centrifuge.");
        add("gregsteamexpansion.machine.large_steam_centrifuge.tooltip.summary.1",
                "Fixed 7\u00d77\u00d79 disc tower; each recipe takes 1.5\u00d7 its base duration and 2 mB of steam per EU.");
        add("gregsteamexpansion.machine.large_steam_centrifuge.tooltip.summary.2",
                "Hold Shift for the full structure and operating rules.");
        add("gregsteamexpansion.machine.large_steam_centrifuge.tooltip.details.subtitle",
                "Structure");
        add("gregsteamexpansion.machine.large_steam_centrifuge.tooltip.details.0",
                "Fixed 7\u00d77\u00d79 vertical separation tower (333 blocks): full 37-block disc crowns top and bottom (73 bronze steam machine casings + the front-bottom-centre controller), seven 16-cell side rings (112) around a 21-cell interior carrying the 7-block Steam Mixing Block axis and two bronze pipe casing columns (18 blocks of air per layer, 126 in total).");
        add("gregsteamexpansion.machine.large_steam_centrifuge.tooltip.details.1",
                "The Steam Mixing Blocks and bronze pipe casings are structure-only and grant no bonuses.");
        add("gregsteamexpansion.machine.large_steam_centrifuge.tooltip.details.2",
                "Hatches replace ONLY side-ring casing positions: at least 1 item input bus, 1 item output bus, 1 steam supply hatch, 1 fluid input hatch and 1 fluid output hatch, exactly 1 steam exhaust hatch, at most 12 hatches in total. Both fluid hatch families are admissible - GTCEu standard hatches or the mod's steam fluid hatches, freely mixed.");
        add("gregsteamexpansion.machine.large_steam_centrifuge.tooltip.details.subtitle2",
                "Processing & Steam");
        add("gregsteamexpansion.machine.large_steam_centrifuge.tooltip.details.3",
                "Runs every gtceu:centrifuge recipe (add-on recipes are picked up automatically); the thermal centrifuge route stays upstream. One recipe per batch, up to 64 parallel, duration = base \u00d7 1.5 (rounded up).");
        add("gregsteamexpansion.machine.large_steam_centrifuge.tooltip.details.4",
                "Recipes above LV voltage are rejected. Each operation consumes recipe EU/t \u00d7 duration \u00d7 2 mB of standard steam; demand scales linearly with parallel.");
        add("gregsteamexpansion.machine.large_steam_centrifuge.tooltip.details.5",
                "Outputs (up to 6 item slots + 6 fluid slots) leave through the item output buses and fluid output hatches; outputs are prechecked at worst case before startup and are never voided.");
        add("gregsteamexpansion.machine.large_steam_centrifuge.tooltip.details.6",
                "The steam exhaust hatch must face open air (3 blocks straight ahead); an obstructed exhaust freezes progress and vents nothing until cleared, and venting deals heat damage on its cycle.");
        add("gregsteamexpansion.machine.large_steam_centrifuge.tooltip.details.subtitle3",
                "Control & Status");
        add("gregsteamexpansion.machine.large_steam_centrifuge.tooltip.details.7",
                "The last successful recipe is preferred; active pause freezes progress; loss of steam or structure rolls an active batch back to 1 tick.");
        add("gregsteamexpansion.machine.large_steam_centrifuge.tooltip.details.8",
                "Removing the controller drops pending output items, loses their fluids and the recipe preference; the controller holds no internal storage.");
        add("gregsteamexpansion.machine.large_steam_centrifuge.tooltip.details.9",
                "All three difficulty tiers behave identically.");
        add("gregsteamexpansion.machine.large_steam_centrifuge.tooltip.details.10",
                "Steam Air Intake Hatch: optional, at most one, and it counts as one of the 12 hatches. Only its 64,000 mB cache can feed the 10,000 mB air dose of air separation; recipes that consume air are sorted last so they never starve the item recipes.");

        // 蒸汽提取机 / Steam Extractor (steam-extractor.md).
        add("gregsteamexpansion.machine.steam_extractor.tooltip.summary.0",
                "Pure-steam extractor multiblock running full extractor recipes at up to 8 parallel operations.");
        add("gregsteamexpansion.machine.steam_extractor.tooltip.summary.1",
                "Fixed 3\u00d73\u00d73 structure; each recipe takes 1.5\u00d7 its base duration and 2 mB of steam per EU.");
        add("gregsteamexpansion.machine.steam_extractor.tooltip.summary.2",
                "Hold Shift for full structure and operating rules.");
        add("gregsteamexpansion.machine.steam_extractor.tooltip.details.subtitle", "Structure");
        add("gregsteamexpansion.machine.steam_extractor.tooltip.details.0",
                "Fixed at 3\u00d73\u00d73; the controller sits front-bottom-centre and a Bronze Pipe Casing forms the extraction core at the structure centre.");
        add("gregsteamexpansion.machine.steam_extractor.tooltip.details.1",
                "Requires at least one steam item input bus, one steam item output bus, one Steam Supply Hatch and one fluid output hatch (a GTCEu standard output hatch or this mod's Steam Fluid Output Hatch, mixable), with at most 8 buses and hatches combined; the other candidate positions use Bronze Steam Machine Casings.");
        add("gregsteamexpansion.machine.steam_extractor.tooltip.details.subtitle2", "Processing and Steam");
        add("gregsteamexpansion.machine.steam_extractor.tooltip.details.2",
                "Runs gtceu:extractor recipes only; each batch uses one recipe at up to 8 parallel operations with duration ceil(base \u00d7 1.5).");
        add("gregsteamexpansion.machine.steam_extractor.tooltip.details.3",
                "Recipes above LV voltage are rejected. Each operation consumes recipe EU/t \u00d7 duration \u00d7 2 mB of standard steam, and parallel scales steam demand linearly.");
        add("gregsteamexpansion.machine.steam_extractor.tooltip.details.4",
                "Output is checked before startup as if every chanced output succeeds (1 item slot + 1 fluid slot); parallel is reduced or the machine stays idle if everything cannot fit, and outputs are never voided.");
        add("gregsteamexpansion.machine.steam_extractor.tooltip.details.subtitle3", "Control and Status");
        add("gregsteamexpansion.machine.steam_extractor.tooltip.details.5",
                "Items commit to the output buses and fluids commit to the fluid output hatches, separately and atomically; pending fluids persist with the controller and are re-delivered whole once space recovers.");
        add("gregsteamexpansion.machine.steam_extractor.tooltip.details.6",
                "Accepts GTCEu standard steam only, never EU; no fluid input hatches and no Steam Exhaust Hatch.");
        add("gregsteamexpansion.machine.steam_extractor.tooltip.details.7",
                "Removing or destroying the controller permanently clears its batch, pending outputs and recipe preference; item pending outputs drop, fluids are lost.");
        add("gregsteamexpansion.machine.steam_extractor.tooltip.details.8",
                "All three difficulty tiers behave identically.");

        // 蒸汽锻压机 / Steam Forge (steam-forge.md).
        add("gregsteamexpansion.machine.steam_forge.tooltip.summary.0",
                "Pure-steam forge multiblock running full forge hammer recipes (ORE_FORGING included) at up to 8 parallel operations.");
        add("gregsteamexpansion.machine.steam_forge.tooltip.summary.1",
                "Fixed 3\u00d73\u00d75 structure; each recipe takes 1.5\u00d7 its base duration and 2 mB of steam per EU.");
        add("gregsteamexpansion.machine.steam_forge.tooltip.summary.2",
                "Hold Shift for full structure and operating rules.");
        add("gregsteamexpansion.machine.steam_forge.tooltip.details.subtitle", "Structure");
        add("gregsteamexpansion.machine.steam_forge.tooltip.details.0",
                "3 wide \u00d7 3 deep \u00d7 5 tall; the bottom two layers are full 3\u00d73 floors with a Steam Assembly Block at the layer-2 centre (the forge-anvil core), and the top three layers carry only the depth-centre hammer row.");
        add("gregsteamexpansion.machine.steam_forge.tooltip.details.1",
                "Requires at least one steam item input bus, one steam item output bus and one Steam Supply Hatch, with at most 8 buses and hatches combined; the other candidate positions use Bronze Steam Machine Casings.");
        add("gregsteamexpansion.machine.steam_forge.tooltip.details.subtitle2", "Processing and Steam");
        add("gregsteamexpansion.machine.steam_forge.tooltip.details.2",
                "Runs gtceu:forge_hammer recipes only; each batch uses one recipe at up to 8 parallel operations with duration ceil(base \u00d7 1.5).");
        add("gregsteamexpansion.machine.steam_forge.tooltip.details.3",
                "Recipes above LV voltage are rejected. Each operation consumes recipe EU/t \u00d7 duration \u00d7 2 mB of standard steam, and parallel scales steam demand linearly.");
        add("gregsteamexpansion.machine.steam_forge.tooltip.details.4",
                "Output is checked before startup as if every chanced output succeeds; parallel is reduced or the machine stays idle if everything cannot fit, and outputs are never voided.");
        add("gregsteamexpansion.machine.steam_forge.tooltip.details.subtitle3", "Control and Status");
        add("gregsteamexpansion.machine.steam_forge.tooltip.details.5",
                "Accepts GTCEu standard steam only, never EU; pure dry type with no fluid hatches and no Steam Exhaust Hatch.");
        add("gregsteamexpansion.machine.steam_forge.tooltip.details.6",
                "The last successful recipe is preferred; active pause freezes progress; loss of steam or structure rolls an active batch back to 1 tick.");
        add("gregsteamexpansion.machine.steam_forge.tooltip.details.7",
                "Removing or destroying the controller permanently clears its batch, pending outputs and recipe preference; the piston and gear installed during crafting are not returned.");
        add("gregsteamexpansion.machine.steam_forge.tooltip.details.8",
                "All three difficulty tiers behave identically.");

        // Shared light-processor controller GUI info-page labels
        // (steam-compressor.md 议题 9 沿用粉碎机骨架).
        add("gregsteamexpansion.machine.steam_processor.ui.status", "Status");
        add("gregsteamexpansion.machine.steam_processor.ui.recipe", "Recipe");
        add("gregsteamexpansion.machine.steam_processor.ui.progress", "Progress");
        add("gregsteamexpansion.machine.steam_processor.ui.parallel", "Parallel (current / cap)");
        add("gregsteamexpansion.machine.steam_processor.ui.steam", "Steam (total / capacity)");
        add("gregsteamexpansion.machine.steam_processor.ui.demand", "Steam demand");
        // 议题 12: 进气室状态行 (仅接受进气室的机型显示).
        add("gregsteamexpansion.machine.steam_processor.ui.intake", "Air intake");
        add("gregsteamexpansion.machine.steam_processor.ui.not_consuming", "not consuming now");
        add("gregsteamexpansion.machine.steam_processor.ui.pending", "Pending outputs");
        add("gregsteamexpansion.machine.steam_processor.ui.pending_summary", "%s (%s kinds)");
        add("gregsteamexpansion.machine.steam_processor.ui.pending_detail", "Pending outputs (stable order):");
        add("gregsteamexpansion.machine.steam_processor.ui.pending_empty", "No pending outputs.");

        // Jade lines for the light processor family share the crusher shape.
        add("gregsteamexpansion.jade.steam_processor.status", "Status: %s");
        add("gregsteamexpansion.jade.steam_processor.recipe", "Recipe: %s");
        add("gregsteamexpansion.jade.steam_processor.progress", "Progress: %s / %s tick");
        add("gregsteamexpansion.jade.steam_processor.parallel", "Parallel: %s / %s");
        add("gregsteamexpansion.jade.steam_processor.steam", "Steam: %s / %s mB");
        add("gregsteamexpansion.jade.steam_processor.demand", "Steam demand: %s mB/t");
        add("gregsteamexpansion.jade.steam_processor.pending", "Pending: %s (%s kinds)");
        add("gregsteamexpansion.jade.steam_processor.pending_fluid", "Pending fluids: %s mB (%s kinds)");
        // 议题 12: 控制器侧的进气室汇总行 (状态 + 缓存存量 / 容量).
        add("gregsteamexpansion.jade.steam_processor.intake", "Air intake: %s (%s / %s mB)");
        add("config.jade.plugin_gregsteamexpansion.steam_compressor_info", "Steam Compressor Info");

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
                "The existing GTCEu terminal auto-builds a fixed 15×15×6 structure; the GTM Things advanced terminal can set the repeatable middle layers to 3–15 for a total height of 6–18, while the width stays fixed at 15.");
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

        addCokeOvenLang();
        addLargeCokeOvenLang();
        addAssemblerFamilyLang();
        addBoilerRoomLang();
        addVoidProducerLang();
    }

    // ------------------------------------------------------------------
    // 旗舰虚空机器 (large-steam-ore-plant.md / large-steam-fluid-drill.md
    // 两级物品提示与共用 UI 文本)。
    // ------------------------------------------------------------------
    private static void addVoidProducerLang() {
        // ---- F1 大型蒸汽采矿厂两级物品提示 ----
        add("gregsteamexpansion.machine.large_steam_ore_plant.tooltip.summary.0",
                "9\u00d79\u00d77 flagship void ore plant: 4 stations each draw 8 raw ores every 200 ticks (12,000 mB/t steam at full speed).");
        add("gregsteamexpansion.machine.large_steam_ore_plant.tooltip.summary.1",
                "Draws are weighted-random overworld materials yielded as raw ores \u2014 feeding the washer \u2192 macerator \u2192 centrifuge chain unchanged.");
        add("gregsteamexpansion.machine.large_steam_ore_plant.tooltip.summary.2",
                "Hold Shift for pool, config toggle and structure details.");
        add("gregsteamexpansion.machine.large_steam_ore_plant.tooltip.details.subtitle", "Production");
        add("gregsteamexpansion.machine.large_steam_ore_plant.tooltip.details.0",
                "4 stations \u00d7 200-tick cycle; per draw 8 raw ores of one weighted-random overworld material (13 entries by default).");
        add("gregsteamexpansion.machine.large_steam_ore_plant.tooltip.details.1",
                "Difficulty multiplies the output count only: Easy \u00d74 / Normal \u00d72 / Expert \u00d71; steam and cycle are identical across tiers.");
        add("gregsteamexpansion.machine.large_steam_ore_plant.tooltip.details.2",
                "The weight table is config-overridable (machines.large_steam_ore_plant.weights); empty/invalid entries fall back to the built-in table. Restart required.");
        add("gregsteamexpansion.machine.large_steam_ore_plant.tooltip.details.subtitle2", "Steam and Outputs");
        add("gregsteamexpansion.machine.large_steam_ore_plant.tooltip.details.3",
                "Steam only: 3,000 mB/t per station, 12,000 mB/t full speed \u2014 needs 10 Steam Supply Hatches (1,200 mB/t per-hatch cap).");
        add("gregsteamexpansion.machine.large_steam_ore_plant.tooltip.details.4",
                "A steam shortage rewinds the cycle to 1 tick and resumes; an obstructed exhaust hatch freezes it.");
        add("gregsteamexpansion.machine.large_steam_ore_plant.tooltip.details.5",
                "Outputs go to item output buses; blocked outputs pause the plant without consuming steam.");
        add("gregsteamexpansion.machine.large_steam_ore_plant.tooltip.details.6",
                "Easy difficulty quadruples draw counts \u2014 a dedicated Steel Boiler Room sustains one plant at full speed.");
        add("gregsteamexpansion.machine.large_steam_ore_plant.tooltip.details.subtitle3", "Structure and Config");
        add("gregsteamexpansion.machine.large_steam_ore_plant.tooltip.details.7",
                "Industrial top/bottom faces and 12 edge columns; steam machine casing walls are the only hatch zone, \u226416 hatches.");
        add("gregsteamexpansion.machine.large_steam_ore_plant.tooltip.details.8",
                "18 Steam Grinding Blocks on interior layers 2 and 6 (3\u00d73 grids); exactly one Steam Exhaust Hatch is required.");
        add("gregsteamexpansion.machine.large_steam_ore_plant.tooltip.details.9",
                "Config toggle machines.large_steam_ore_plant.enabled=false keeps the structure but stops it with a config-disabled status.");

        // ---- F2 大型蒸汽流体钻井两级物品提示 ----
        add("gregsteamexpansion.machine.large_steam_fluid_drill.tooltip.summary.0",
                "Flagship void fluid drill (7 wide \u00d7 11 deep \u00d7 11 tall): 2 pumps each draw 2,000 mB every 200 ticks (6,000 mB/t steam at full speed).");
        add("gregsteamexpansion.machine.large_steam_fluid_drill.tooltip.summary.1",
                "Default pool mirrors the upstream overworld deposits: light oil, oil, heavy oil and natural gas (natural gas kept by decision).");
        add("gregsteamexpansion.machine.large_steam_fluid_drill.tooltip.summary.2",
                "Hold Shift for pool, config toggle and structure details.");
        add("gregsteamexpansion.machine.large_steam_fluid_drill.tooltip.details.subtitle", "Production");
        add("gregsteamexpansion.machine.large_steam_fluid_drill.tooltip.details.0",
                "2 pumps \u00d7 200-tick cycle; per draw 2,000 mB of one weighted-random overworld fluid; draws never mix.");
        add("gregsteamexpansion.machine.large_steam_fluid_drill.tooltip.details.1",
                "Difficulty multiplies the amount only: Easy \u00d74 / Normal \u00d72 / Expert \u00d71 \u2014 Expert full speed is about 1,440 buckets per hour.");
        add("gregsteamexpansion.machine.large_steam_fluid_drill.tooltip.details.2",
                "The weight table is config-overridable (machines.large_steam_fluid_drill.weights); empty/invalid entries fall back to the built-in table. Restart required.");
        add("gregsteamexpansion.machine.large_steam_fluid_drill.tooltip.details.subtitle2", "Steam and Outputs");
        add("gregsteamexpansion.machine.large_steam_fluid_drill.tooltip.details.3",
                "Steam only: 6,000 mB/t full speed \u2014 needs 5 Steam Supply Hatches; a shortage rewinds the cycle to 1 tick.");
        add("gregsteamexpansion.machine.large_steam_fluid_drill.tooltip.details.4",
                "Outputs go to fluid output hatches (GTCEu standard or the mod's steam fluid output hatch, freely mixed); blocked outputs pause the drill without steam.");
        add("gregsteamexpansion.machine.large_steam_fluid_drill.tooltip.details.5",
                "Water and lava are excluded by decision; the void oil is an energy sink (300 steam per mB) \u2014 never a fuel loophole.");
        add("gregsteamexpansion.machine.large_steam_fluid_drill.tooltip.details.6",
                "Expert full speed stockpiles about 1,440 buckets per hour (Easy 5,760) \u2014 banked value for the future refinery era.");
        add("gregsteamexpansion.machine.large_steam_fluid_drill.tooltip.details.subtitle3", "Structure and Config");
        add("gregsteamexpansion.machine.large_steam_fluid_drill.tooltip.details.7",
                "Industrial bottom face, corner columns and 3\u00d73 cap; steam machine casing walls and crown rings are the only hatch zone, \u226416 hatches.");
        add("gregsteamexpansion.machine.large_steam_fluid_drill.tooltip.details.8",
                "Centre bronze pipe column (6) with a Steam Mixing Block separator on layer 8; exactly one Steam Exhaust Hatch is required.");
        add("gregsteamexpansion.machine.large_steam_fluid_drill.tooltip.details.9",
                "Config toggle machines.large_steam_fluid_drill.enabled=false keeps the structure but stops it with a config-disabled status.");

        // ---- 虚空机器共用 UI 文本 ----
        add("gregsteamexpansion.machine.void_producer.ui.status", "Status");
        add("gregsteamexpansion.machine.void_producer.ui.progress", "Cycle");
        add("gregsteamexpansion.machine.void_producer.ui.stations", "Stations");
        add("gregsteamexpansion.machine.void_producer.ui.pool", "Draw pool");
        add("gregsteamexpansion.machine.void_producer.ui.pool_summary", "%s entries (weight %s)");
        add("gregsteamexpansion.machine.void_producer.ui.pool_detail",
                "Current effective draw pool (config overrides the built-in table):");
        add("gregsteamexpansion.machine.void_producer.ui.steam", "Steam");
        add("gregsteamexpansion.machine.void_producer.ui.demand", "Steam demand");
        add("gregsteamexpansion.machine.void_producer.ui.pending", "Pending output");
        add("gregsteamexpansion.machine.void_producer.ui.pending_summary", "%s (%s kinds)");
        add("gregsteamexpansion.machine.void_producer.ui.pending_detail", "Pending outputs:");
        add("gregsteamexpansion.machine.void_producer.ui.pending_empty", "Nothing pending");
        add("gregsteamexpansion.machine.void_producer.ui.not_consuming", "not consuming");
        add("gregsteamexpansion.machine.void_producer.ui.disabled_by_config",
                "Disabled in config (machines.*.enabled=false) \u2014 restart to apply changes");
        add("gregsteamexpansion.machine.void_producer.ui.exhaust_obstructed",
                "Exhaust hatch obstructed \u2014 cycle frozen");

        // ---- 电力粉碎机 (ore-crushing.md 电力消费机器) ----
        add("gregsteamexpansion.machine.electric_ore_crusher.tooltip",
                "Runs ore-crushing recipes on electricity (the ore_crushing type): same yields as the steam crushers (8 raw ores per operation, difficulty included), overclocked by machine tier.");
    }

    // ------------------------------------------------------------------
    // 锅炉房 (boiler-room.md 两级物品提示与状态文本)。控制器条目沿用上游
    // gtceu.multiblock.large_boiler.* 的温度/节流/爆炸文案, 仅新增协同燃烧
    // 与助燃空气两行及四条运行状态。
    // ------------------------------------------------------------------
    private static void addBoilerRoomLang() {
        add("gregsteamexpansion.machine.boiler_room.tooltip.co_firing",
                "Co-firing only: liquid fuel + a co-firing dust powder; output \u00d71.5, drawing %s mB/t of combustion air.");
        add("gregsteamexpansion.machine.boiler_room.tooltip.air_intake",
                "Requires exactly one Steam Air Intake Hatch on the top face; no steam hatch of any kind is allowed.");
        add("gregsteamexpansion.machine.boiler_room.status.no_air_intake",
                "Needs combustion air: attach a Steam Air Intake Hatch on the top face.");
        add("gregsteamexpansion.machine.boiler_room.status.air_starved",
                "Air intake cannot keep up \u2014 combustion paused.");
        add("gregsteamexpansion.machine.boiler_room.status.missing_powder",
                "Missing co-firing powder \u2014 paused and cooling fast.");
        add("gregsteamexpansion.machine.boiler_room.status.co_firing",
                "Co-firing: powder burn buffer %s%%");
    }

    // ------------------------------------------------------------------
    // 大型蒸汽组装机 / 大型蒸汽电路组装机 (large-steam-assembler.md /
    // large-steam-circuit-assembler.md 两级物品提示与槽位 UI 文本)。
    // ------------------------------------------------------------------
    private static void addAssemblerFamilyLang() {
        // ---- B1 大型蒸汽组装机两级物品提示 ----
        add("gregsteamexpansion.machine.large_steam_assembler.tooltip.summary.0",
                "9\u00d79\u00d79 large steam assembler running the full gtceu:assembler recipe type with pure steam power.");
        add("gregsteamexpansion.machine.large_steam_assembler.tooltip.summary.1",
                "The controller's assembler slot gates recipe tiers and parallel with electric assemblers; it never provides EU (2 mB steam per EU).");
        add("gregsteamexpansion.machine.large_steam_assembler.tooltip.summary.2",
                "Hold Shift for slot, parallel ladder and structure details.");
        add("gregsteamexpansion.machine.large_steam_assembler.tooltip.details.subtitle", "Assembler Slot");
        add("gregsteamexpansion.machine.large_steam_assembler.tooltip.details.0",
                "One controller slot holds 1\u20134 stacked electric assemblers of a single tier (LV\u2013EV); kinds cannot be mixed, and the stack persists across reloads.");
        add("gregsteamexpansion.machine.large_steam_assembler.tooltip.details.1",
                "Empty slot: ULV recipes only at parallel 1; a stacked kind admits recipes up to its own tier.");
        add("gregsteamexpansion.machine.large_steam_assembler.tooltip.details.2",
                "Stacks of 1 / 2 / 3 / 4 raise the parallel cap to 2 / 4 / 8 / 16; a running batch keeps its locked tier and parallel.");
        add("gregsteamexpansion.machine.large_steam_assembler.tooltip.details.subtitle2", "Batch Economics");
        add("gregsteamexpansion.machine.large_steam_assembler.tooltip.details.3",
                "Batch duration = recipe duration \u00d7 1.5 \u00d7 time ladder; steam per tick = recipe EU/t \u00d7 2 \u00d7 steam ladder (integer math).");
        add("gregsteamexpansion.machine.large_steam_assembler.tooltip.details.4",
                "Ladder at 1 / 2 / 4 / 8 / 16 parallel: steam \u00d71 / 1.25 / 1.5 / 1.75 / 2, duration \u00d71 / 1.5 / 2 / 2.5 / 3 \u2014 higher parallel lowers steam cost per item.");
        add("gregsteamexpansion.machine.large_steam_assembler.tooltip.details.5",
                "Actual parallel = min(cap, input portions, worst-case output room); a steam shortage rewinds progress to 1 tick and the batch resumes.");
        add("gregsteamexpansion.machine.large_steam_assembler.tooltip.details.6",
                "Easy difficulty doubles item outputs of new batches; Normal and Expert stay 1\u00d7 \u2014 inputs are never discounted.");
        add("gregsteamexpansion.machine.large_steam_assembler.tooltip.details.subtitle3", "Structure and Automation");
        add("gregsteamexpansion.machine.large_steam_assembler.tooltip.details.7",
                "Industrial casings form the top/bottom faces and all 12 edges; the four walls (steam machine casings) are the only hatch zone with \u226416 hatches in total.");
        add("gregsteamexpansion.machine.large_steam_assembler.tooltip.details.8",
                "18 Steam Assembly Blocks sit on interior layers 2 and 8 (3\u00d73 spaced grids); exactly one Steam Exhaust Hatch is required.");
        add("gregsteamexpansion.machine.large_steam_assembler.tooltip.details.9",
                "Breaking the controller returns the slot's assemblers as item drops; the preference recipe and pending outputs are lost.");

        // ---- B2 大型蒸汽电路组装机两级物品提示 ----
        add("gregsteamexpansion.machine.large_steam_circuit_assembler.tooltip.summary.0",
                "5\u00d711\u00d76 large steam circuit assembler running the full gtceu:circuit_assembler recipe type with pure steam power.");
        add("gregsteamexpansion.machine.large_steam_circuit_assembler.tooltip.summary.1",
                "Same assembler-slot mechanism as the Large Steam Assembler; every recipe also needs its mandatory solder-type fluid input.");
        add("gregsteamexpansion.machine.large_steam_circuit_assembler.tooltip.summary.2",
                "Hold Shift for slot, parallel ladder and structure details.");
        add("gregsteamexpansion.machine.large_steam_circuit_assembler.tooltip.details.subtitle", "Assembler Slot");
        add("gregsteamexpansion.machine.large_steam_circuit_assembler.tooltip.details.0",
                "One controller slot holds 1\u20134 stacked electric circuit assemblers of a single tier (LV\u2013EV); kinds cannot be mixed, and the stack persists across reloads.");
        add("gregsteamexpansion.machine.large_steam_circuit_assembler.tooltip.details.1",
                "Empty slot: ULV recipes only at parallel 1; a stacked kind admits recipes up to its own tier.");
        add("gregsteamexpansion.machine.large_steam_circuit_assembler.tooltip.details.2",
                "Stacks of 1 / 2 / 3 / 4 raise the parallel cap to 2 / 4 / 8 / 16; a running batch keeps its locked tier and parallel.");
        add("gregsteamexpansion.machine.large_steam_circuit_assembler.tooltip.details.subtitle2", "Batch Economics");
        add("gregsteamexpansion.machine.large_steam_circuit_assembler.tooltip.details.3",
                "Batch duration = recipe duration \u00d7 1.5 \u00d7 time ladder; steam per tick = recipe EU/t \u00d7 2 \u00d7 steam ladder (integer math).");
        add("gregsteamexpansion.machine.large_steam_circuit_assembler.tooltip.details.4",
                "Ladder at 1 / 2 / 4 / 8 / 16 parallel: steam \u00d71 / 1.25 / 1.5 / 1.75 / 2, duration \u00d71 / 1.5 / 2 / 2.5 / 3 \u2014 higher parallel lowers steam cost per item.");
        add("gregsteamexpansion.machine.large_steam_circuit_assembler.tooltip.details.5",
                "Every circuit assembler recipe carries a mandatory fluid input (upstream auto-adds solder); the fluid input hatch is a first-class requirement.");
        add("gregsteamexpansion.machine.large_steam_circuit_assembler.tooltip.details.6",
                "Easy difficulty doubles item outputs of new batches; Normal and Expert stay 1\u00d7 \u2014 inputs are never discounted.");
        add("gregsteamexpansion.machine.large_steam_circuit_assembler.tooltip.details.subtitle3", "Structure and Automation");
        add("gregsteamexpansion.machine.large_steam_circuit_assembler.tooltip.details.7",
                "Steam machine casings form the bottom face, walls and edges (\u226416 hatches in total); only the 1\u00d711 top ridge is industrial casing.");
        add("gregsteamexpansion.machine.large_steam_circuit_assembler.tooltip.details.8",
                "The interior centre tower stacks 9 circuit assembly blocks, 9 bronze gearbox casings, 9 assembly blocks and 9 bronze pipe casings; exactly one Steam Exhaust Hatch is required.");
        add("gregsteamexpansion.machine.large_steam_circuit_assembler.tooltip.details.9",
                "Breaking the controller returns the slot's circuit assemblers as item drops; the preference recipe and pending outputs are lost.");

        // ---- 组装机槽位控制器 UI 文本 (B1/B2 共用) ----
        add("gregsteamexpansion.machine.steam_assembler.ui.slot_empty", "Assembler slot: empty (ULV only)");
        add("gregsteamexpansion.machine.steam_assembler.ui.slot_summary",
                "Assembler slot: %s \u00d7%s (parallel \u2264 %s)");
    }

    /** 大型焦炉本地化 (coke-ovens.md 大型焦炉已确认设计)。 */
    private static void addLargeCokeOvenLang() {
        // Jade requires a configuration label for every registered provider UID.
        add("config.jade.plugin_gregsteamexpansion.large_coke_oven_info", "Large Coke Oven Info");
        add("config.jade.plugin_gregsteamexpansion.large_coke_oven_hatch_info", "Large Coke Oven Hatch Info");
        add("config.jade.plugin_gregsteamexpansion.coke_oven_brick_ownership", "Coke Oven Brick Ownership");

        // ---- 主状态 (优先级从高到低) ----
        add("gregsteamexpansion.large_coke_oven.status.range_not_loaded",
                "Structure range not fully loaded");
        add("gregsteamexpansion.large_coke_oven.status.awaiting_reinput",
                "Waiting for re-input");
        add("gregsteamexpansion.large_coke_oven.status.waiting_output",
                "Waiting for output");
        add("gregsteamexpansion.large_coke_oven.status.startup_output_blocked",
                "Output blocked before startup");
        add("gregsteamexpansion.large_coke_oven.status.input_invalid",
                "Input is not a valid coke oven ingredient");
        add("gregsteamexpansion.large_coke_oven.status.input_insufficient",
                "Insufficient input");
        add("gregsteamexpansion.large_coke_oven.status.ready",
                "Ready to start");
        add("gregsteamexpansion.large_coke_oven.status.idle",
                "Idle");

        // ---- 详细原因 ----
        add("gregsteamexpansion.large_coke_oven.detail.range_not_loaded",
                "Chunks of the full 7×7×5 structure range are not loaded; processing is paused.");
        add("gregsteamexpansion.large_coke_oven.detail.air_blocked",
                "Furnace chamber or funnel opening blocked at %s (must stay air).");
        add("gregsteamexpansion.large_coke_oven.detail.first_error",
                "First structure error at %s.");
        add("gregsteamexpansion.large_coke_oven.detail.missing_hatch",
                "Missing required large coke oven hatches: 3–5 hatches with all three modes are needed.");
        add("gregsteamexpansion.large_coke_oven.detail.missing_mode",
                "Missing required hatch mode: item input, item output and fluid output are each required at least once.");
        add("gregsteamexpansion.large_coke_oven.detail.waiting_output",
                "The completed batch snapshot cannot be committed yet; it retries automatically.");
        add("gregsteamexpansion.large_coke_oven.detail.pending",
                "Pending products: %s");
        add("gregsteamexpansion.large_coke_oven.detail.startup_blocked",
                "A valid recipe exists but even one portion of its output does not fit; remove products to proceed.");
        add("gregsteamexpansion.large_coke_oven.detail.awaiting_reinput",
                "A legacy batch was cancelled. Insert a valid coke oven ingredient once to resume.");
        add("gregsteamexpansion.large_coke_oven.detail.preferred",
                "Preferred recipe: %s");

        // ---- GUI ----
        add("gregsteamexpansion.large_coke_oven.gui.slot_number", "Slot %s");
        add("gregsteamexpansion.large_coke_oven.gui.no_batch", "No active batch");
        add("gregsteamexpansion.large_coke_oven.gui.recipe_line", "%s · parallel %s · %s ticks · %s · %s left");
        add("gregsteamexpansion.large_coke_oven.gui.progress", "%s · about %s remaining");
        add("gregsteamexpansion.large_coke_oven.gui.progress.idle", "%s · idle");
        add("gregsteamexpansion.large_coke_oven.gui.waiting_output", "awaiting output");

        // ---- 大型焦炉仓 ----
        add("gregsteamexpansion.large_coke_oven_hatch.facing.locked",
                "This hatch must face %s at its candidate position.");
        add("gregsteamexpansion.large_coke_oven_hatch.mode.locked",
                "The large coke oven is running or holding a pending batch; hatch modes are locked.");
        add("gregsteamexpansion.large_coke_oven_hatch.mode.last_of_mode",
                "Cannot remove the last hatch of a required mode.");

        addLargeCokeOvenTooltips();
    }

    /** 大型焦炉两级物品提示 (coke-ovens.md 已确认物品提示与结构说明)。 */
    private static void addLargeCokeOvenTooltips() {
        // ---- 控制器 ----
        add("gregsteamexpansion.machine.large_coke_oven.tooltip.summary.0",
                "A large no-energy coking machine: parallel 6 at fixed 0.5× recipe time, equal to 12 coke ovens at full load.");
        add("gregsteamexpansion.machine.large_coke_oven.tooltip.summary.1",
                "Hold Shift for structure, interface and hazard notes.");
        add("gregsteamexpansion.machine.large_coke_oven.tooltip.details.0",
                "The full structure footprint is fixed at 7×7×5 with three synced furnace chambers and a brick charging funnel on top.");
        add("gregsteamexpansion.machine.large_coke_oven.tooltip.details.1",
                "A valid structure uses 151–153 Coke Oven Bricks plus 3–5 Large Coke Oven Hatches, with all three hatch modes present at least once.");
        add("gregsteamexpansion.machine.large_coke_oven.tooltip.details.2",
                "The top funnel is structure and appearance only; all automation must go through Large Coke Oven Hatches.");
        add("gregsteamexpansion.machine.large_coke_oven.tooltip.details.3",
                "Consumes no energy and has no fuel, temperature, warm-up, maintenance or pause.");
        add("gregsteamexpansion.machine.large_coke_oven.tooltip.details.4",
                "Parallel 6 and halved duration never increase yield per input: single-portion inputs and products follow the original coke oven recipes exactly.");
        add("gregsteamexpansion.machine.large_coke_oven.tooltip.details.5",
                "With GTCEu environmental hazards enabled, each completed parallel portion emits 0.1 carbon monoxide.");
        add("gregsteamexpansion.machine.large_coke_oven.tooltip.details.6",
                "Breaking a normal shell halts the in-progress batch and rewinds its progress to 1 tick; the same batch continues after repair. Breaking the controller cancels it, drops items by the settled rules and voids all fluids.");
        add("gregsteamexpansion.machine.large_coke_oven.tooltip.details.7",
                "Use the structure preview to inspect layers, candidate hatch positions and error diagnostics.");

        // ---- 大型焦炉仓 ----
        add("gregsteamexpansion.machine.large_coke_oven_hatch.tooltip.summary.0",
                "The only legal automation interface of the Large Coke Oven; new hatches default to item input, sneak + screwdriver cycles modes.");
        add("gregsteamexpansion.machine.large_coke_oven_hatch.tooltip.summary.1",
                "Hold Shift for connection, facing and mode limits.");
        add("gregsteamexpansion.machine.large_coke_oven_hatch.tooltip.details.0",
                "Green inward arrow: item input; orange outward box arrow: item output; blue outward droplet: fluid output.");
        add("gregsteamexpansion.machine.large_coke_oven_hatch.tooltip.details.1",
                "Every Large Coke Oven needs 3–5 hatches with all three modes present at least once.");
        add("gregsteamexpansion.machine.large_coke_oven_hatch.tooltip.details.2",
                "A hatch only exposes its current mode on the outward-facing front; the other five faces are inert.");
        add("gregsteamexpansion.machine.large_coke_oven_hatch.tooltip.details.3",
                "All hatches proxy the controller's shared inventories; adding hatches never adds slots, capacity or parallel.");
        add("gregsteamexpansion.machine.large_coke_oven_hatch.tooltip.details.4",
                "Item input only accepts insertion; the controller pushes item and fluid outputs every 5 ticks in a fixed order.");
        add("gregsteamexpansion.machine.large_coke_oven_hatch.tooltip.details.5",
                "Modes cannot be switched while the oven is running or holding a pending batch; other states must keep one hatch of each mode.");
        add("gregsteamexpansion.machine.large_coke_oven_hatch.tooltip.details.6",
                "A front cover can only further restrict logistics the current mode already allows; it never changes the mode or controls crafting.");

        // ---- Jade: 大型焦炉与已归属砖 ----
        add("gregsteamexpansion.jade.large_coke_oven.status", "Status: %s");
        add("gregsteamexpansion.jade.large_coke_oven.detail", "· %s");
        add("gregsteamexpansion.jade.large_coke_oven.recipe", "Recipe: %s (parallel %s)");
        add("gregsteamexpansion.jade.large_coke_oven.progress", "Progress: %s (%s left)");
        add("gregsteamexpansion.jade.large_coke_oven.waiting", "Waiting for output: %s, products pending commit");
        add("gregsteamexpansion.jade.large_coke_oven_hatch.facing", "Working face: %s");
        add("gregsteamexpansion.jade.large_coke_oven_hatch.covered", "Cover installed on the front");
        add("gregsteamexpansion.jade.large_coke_oven_hatch.slots", "Inventory: %s slots used");
        add("gregsteamexpansion.jade.large_coke_oven_hatch.fluid", "Fluid: %s");
        add("gregsteamexpansion.jade.large_coke_oven_hatch.direction.north", "north");
        add("gregsteamexpansion.jade.large_coke_oven_hatch.direction.south", "south");
        add("gregsteamexpansion.jade.large_coke_oven_hatch.direction.east", "east");
        add("gregsteamexpansion.jade.large_coke_oven_hatch.direction.west", "west");
        add("gregsteamexpansion.jade.large_coke_oven_hatch.direction.up", "up");
        add("gregsteamexpansion.jade.large_coke_oven_hatch.direction.down", "down");
        add("gregsteamexpansion.jade.coke_oven_brick.owned", "%s");
        add("gregsteamexpansion.jade.coke_oven_brick.kind.large", "Belongs to a Large Coke Oven");
        add("gregsteamexpansion.jade.coke_oven_brick.kind.regular", "Belongs to a Coke Oven");
        add("gregsteamexpansion.jade.coke_oven_brick.controller", "Controller: %s");
        add("gregsteamexpansion.jade.coke_oven_brick.invalid", "Owned, structure invalid");
        add("gregsteamexpansion.jade.coke_oven_brick.direction.north", "north");
        add("gregsteamexpansion.jade.coke_oven_brick.direction.south", "south");
        add("gregsteamexpansion.jade.coke_oven_brick.direction.east", "east");
        add("gregsteamexpansion.jade.coke_oven_brick.direction.west", "west");
        add("gregsteamexpansion.jade.coke_oven_brick.direction.up", "up");
        add("gregsteamexpansion.jade.coke_oven_brick.direction.down", "down");
    }

    /**
     * 普通焦炉与焦炉仓本地化 (coke-ovens.md): 状态文本优先复用 GTCEu 语义一致
     * 的键 (invalid_structure / running / idling), 其余语义新增本模组键。
     */
    private static void addCokeOvenLang() {
        // ---- 通用 ----
        add("gregsteamexpansion.tooltip.shift_hint", "Hold Shift for details");
        // ---- 控制器状态 (优先级从高到低) ----
        add("gregsteamexpansion.coke_oven.status.pending_output", "Pending output result");
        add("gregsteamexpansion.coke_oven.status.pending_output.detail",
                "A completed batch result is waiting to be committed; it will retry automatically once output space frees up.");
        add("gregsteamexpansion.coke_oven.status.awaiting_reinput",
                "Waiting for re-input");
        add("gregsteamexpansion.coke_oven.status.awaiting_reinput.detail",
                "A legacy in-progress batch was cancelled by the update. Insert a valid coke oven ingredient once to resume.");
        add("gregsteamexpansion.coke_oven.status.input_invalid",
                "Input is not a valid coke oven ingredient");
        add("gregsteamexpansion.coke_oven.status.item_output_blocked",
                "Item output blocked");
        add("gregsteamexpansion.coke_oven.status.fluid_output_blocked",
                "Fluid output blocked");
        add("gregsteamexpansion.coke_oven.status.both_output_blocked",
                "Item and fluid output blocked");
        add("gregsteamexpansion.coke_oven.status.blocked.detail",
                "The candidate recipe's output cannot fit; remove products to resume.");
        add("gregsteamexpansion.coke_oven.status.ready",
                "Ready to start");
        add("gregsteamexpansion.coke_oven.status.overlap",
                "Overlaps another multiblock structure near %s");
        add("gregsteamexpansion.coke_oven.status.too_close",
                "Too close to another coke oven near %s (one block gap required)");

        // ---- 旧存档迁移提醒 (每存档一次) ----
        add("gregsteamexpansion.coke_oven.migration.notice",
                "Greg Steam Expansion: a coke oven had an old in-progress batch that cannot be carried over. " +
                        "Its input must be re-inserted once before it can work again.");
        add("gregsteamexpansion.coke_oven_hatch.migration.notice",
                "Greg Steam Expansion: existing coke oven hatches now default to item input mode. " +
                        "Use a sneak + screwdriver click to configure each hatch.");

        // ---- 控制器 GUI ----
        add("gregsteamexpansion.coke_oven.gui.progress", "%s · about %s remaining");
        add("gregsteamexpansion.coke_oven.gui.progress.idle", "%s · idle");

        // ---- Jade: 控制器 ----
        add("gregsteamexpansion.jade.coke_oven.status", "Status: %s");
        add("gregsteamexpansion.jade.coke_oven.detail", "· %s");
        add("gregsteamexpansion.jade.coke_oven.progress", "Progress: %s (%s left)");
        add("gregsteamexpansion.jade.coke_oven.fluid", "Output tank: %s %s / %s mB");
        add("gregsteamexpansion.jade.coke_oven.empty", "Empty");
        add("config.jade.plugin_gregsteamexpansion.coke_oven_info", "Coke Oven Info");

        // ---- Jade: 焦炉仓 ----
        add("gregsteamexpansion.jade.coke_oven_hatch.mode", "Mode: %s");
        add("gregsteamexpansion.jade.coke_oven_hatch.connection", "Connection: %s");
        add("gregsteamexpansion.jade.coke_oven_hatch.connection.formed", "Connected (structure valid)");
        add("gregsteamexpansion.jade.coke_oven_hatch.connection.invalid",
                "Owned, structure invalid");
        add("gregsteamexpansion.jade.coke_oven_hatch.connection.none", "Not connected");
        add("gregsteamexpansion.jade.coke_oven_hatch.items", "Items: %s");
        add("gregsteamexpansion.jade.coke_oven_hatch.fluid", "Fluid: %s");
        add("gregsteamexpansion.jade.coke_oven_hatch.empty", "Empty");
        add("config.jade.plugin_gregsteamexpansion.coke_oven_hatch_info", "Coke Oven Hatch Info");

        // ---- 焦炉仓模式与螺丝刀交互 ----
        add("gregsteamexpansion.coke_oven_hatch.mode.item_input", "Item Input");
        add("gregsteamexpansion.coke_oven_hatch.mode.item_output", "Item Output");
        add("gregsteamexpansion.coke_oven_hatch.mode.fluid_output", "Fluid Output");
        add("gregsteamexpansion.coke_oven_hatch.mode.changed", "Coke oven hatch mode: %s");
        add("gregsteamexpansion.coke_oven_hatch.mode.locked",
                "The coke oven is running or holding a pending result; hatch modes are locked.");

        // ---- 控制器两级物品提示 ----
        add("gregsteamexpansion.machine.coke_oven.tooltip.summary.0",
                "An early coke oven that needs no energy and runs a single recipe at a time.");
        add("gregsteamexpansion.machine.coke_oven.tooltip.summary.1",
                "Hold Shift for structure, automation and demolition warnings.");
        add("gregsteamexpansion.machine.coke_oven.tooltip.details.0",
                "Fixed 3×3×3 structure; the inner center block must stay air.");
        add("gregsteamexpansion.machine.coke_oven.tooltip.details.1",
                "The bottom geometric center right below the hearth must be Coke Oven Bricks and can never be a hatch.");
        add("gregsteamexpansion.machine.coke_oven.tooltip.details.2",
                "The other 24 shell slots together allow 0–5 configurable coke oven hatches; with zero hatches the oven is manual-only.");
        add("gregsteamexpansion.machine.coke_oven.tooltip.details.3",
                "Consumes no energy or fuel, and has no temperature, warm-up, cooldown or pause.");
        add("gregsteamexpansion.machine.coke_oven.tooltip.details.4",
                "Fixed single parallel and reads the full gtceu:coke_oven recipe type.");
        add("gregsteamexpansion.machine.coke_oven.tooltip.details.5",
                "Breaking a normal shell halts the current recipe and rewinds progress to 1 tick; the same batch continues after repair.");
        add("gregsteamexpansion.machine.coke_oven.tooltip.details.6",
                "Breaking the controller or the bottom center brick resets crafting, voids all stored and pending fluids, and drops every stored and consumed item to the world.");

        // ---- 焦炉仓两级物品提示 ----
        add("gregsteamexpansion.machine.coke_oven_hatch.tooltip.summary.0",
                "Use a sneak + screwdriver click to switch between item input, item output and fluid output modes.");
        add("gregsteamexpansion.machine.coke_oven_hatch.tooltip.summary.1",
                "New and legacy hatches default to item input mode.");
        add("gregsteamexpansion.machine.coke_oven_hatch.tooltip.details.0",
                "Green box: item input; orange box: item output; blue box: fluid output. Color is the only difference.");
        add("gregsteamexpansion.machine.coke_oven_hatch.tooltip.details.1",
                "The screwdriver always cycles item input → item output → fluid output → item input.");
        add("gregsteamexpansion.machine.coke_oven_hatch.tooltip.details.2",
                "Modes cannot be switched while the coke oven is running or holding a pending result.");
        add("gregsteamexpansion.machine.coke_oven_hatch.tooltip.details.3",
                "Item input only accepts insertion; both output modes push their products toward the front every 5 ticks.");
        add("gregsteamexpansion.machine.coke_oven_hatch.tooltip.details.4",
                "All hatches proxy the controller's shared inventories; adding hatches never adds slots, tanks, capacity or parallelism.");
        add("gregsteamexpansion.machine.coke_oven_hatch.tooltip.details.5",
                "An unconnected hatch keeps its mode and can still be reconfigured with a screwdriver.");
    }

    private static void add(String key, String value) {
        GSERegistration.REGISTRATE.addRawLang(key, value);
    }
}
