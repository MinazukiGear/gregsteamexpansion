package com.hoshino.gregsteamexpansion.registry;

import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;
import com.gregtechceu.gtceu.api.pattern.BlockPattern;
import com.gregtechceu.gtceu.api.pattern.FactoryBlockPattern;
import com.gregtechceu.gtceu.api.pattern.MultiblockShapeInfo;
import com.gregtechceu.gtceu.api.pattern.Predicates;
import com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate;
import com.gregtechceu.gtceu.api.pattern.util.RelativeDirection;
import com.gregtechceu.gtceu.common.data.GTBlocks;
import com.gregtechceu.gtceu.common.data.GTMachines;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import static com.hoshino.gregsteamexpansion.registry.GSEMachines.STEAM_EXHAUST_HATCH;
import static com.hoshino.gregsteamexpansion.registry.GSEMachines.STEAM_FLUID_EXPORT_HATCH;
import static com.hoshino.gregsteamexpansion.registry.GSEMachines.STEAM_SUPPLY_HATCH;

/**
 * Fixed patterns for the two flagship void producers (large-steam-ore-plant.md
 * 议题 4 / large-steam-fluid-drill.md 议题 4). Aisle order follows
 * {@link RelativeDirection} like the processor patterns: aisles stack
 * bottom-up, each aisle string is one row from the machine's back (index 0)
 * to its front (last index), chars run left to right.
 *
 * <p>Interface counts (supply hatch ≥1, exhaust hatch 恰好 1, required output
 * present, combined ≤16) cannot be expressed as per-predicate pattern limits,
 * so {@code AbstractSteamVoidMachine} re-checks them against the collected
 * parts after formation.</p>
 */
public final class GSEVoidPatterns {

    private GSEVoidPatterns() {}

    /** gtceu:industrial_steam_casing — the industrial steam machine casing. */
    public static Block industrialSteamCasing() {
        return com.gregtechceu.gtceu.common.data.GCYMBlocks.CASING_INDUSTRIAL_STEAM.get();
    }

    /** gtceu:steam_machine_casing — the bronze steam machine casing. */
    public static Block bronzeSteamCasing() {
        return GTBlocks.CASING_BRONZE_BRICKS.get();
    }

    // =====================================================================
    // F1 大型蒸汽采矿厂: 9×9×7 选矿大厅
    // =====================================================================

    /**
     * The ore plant's candidate rule for the 140 wall steam-machine-casing
     * positions (large-steam-ore-plant.md 议题 4): the ONLY hatch-replaceable
     * zone (top/bottom faces and the 12 edge columns are fixed industrial
     * casings). 产出机器无输入面 — no item input bus, no fluid hatch of any
     * family is admitted; outputs are item output buses only. The exhaust
     * hatch takes one candidate slot (必须且只能 1 个, post-checked by the
     * controller) and the 124-casing minimum bounds the hatch total at 16
     * (140 − 124, 议题 4 仓室合计上限).
     */
    private static TraceabilityPredicate orePlantCandidates() {
        return Predicates.blocks(bronzeSteamCasing()).setMinGlobalLimited(124)
                .or(Predicates.abilities(PartAbility.STEAM))
                .or(Predicates.abilities(PartAbility.STEAM_EXPORT_ITEMS))
                .or(Predicates.abilities(PartAbility.EXPORT_ITEMS))
                .or(Predicates.blocks(STEAM_EXHAUST_HATCH.getBlock()).setExactLimit(1));
    }

    /** Row set of an ore-plant workstation layer: 3×3 grinding grid (z/x ∈ {3,5,7}). */
    private static final String[] ORE_PLANT_WORK_LAYER = {
            "IBBBBBBBI",
            "BAAAAAAAB",
            "BAMAMAMAB",
            "BAAAAAAAB",
            "BAMAMAMAB",
            "BAAAAAAAB",
            "BAMAMAMAB",
            "BAAAAAAAB",
            "IBBBBBBBI",
    };

    /** Row set of an ore-plant hollow layer (3–5). */
    private static final String[] ORE_PLANT_HOLLOW_LAYER = {
            "IBBBBBBBI",
            "BAAAAAAAB",
            "BAAAAAAAB",
            "BAAAAAAAB",
            "BAAAAAAAB",
            "BAAAAAAAB",
            "BAAAAAAAB",
            "BAAAAAAAB",
            "IBBBBBBBI",
    };

    /**
     * 大型蒸汽采矿厂: fixed 9×9×7 (large-steam-ore-plant.md 议题 4 逐层图).
     * Bottom and top faces plus the 12 edge columns are industrial casings
     * (front-bottom-centre controller); layers 2 and 6 carry the 9-block
     * Steam Grinding Block arrays; layers 3-5 are hollow. Interior air is
     * STRICT air (Predicates.air).
     */
    public static BlockPattern createOrePlant(MultiblockMachineDefinition definition) {
        return FactoryBlockPattern.start(RelativeDirection.LEFT, RelativeDirection.FRONT, RelativeDirection.UP)
                .aisle( // layer 1 (bottom industrial face, controller front-centre)
                        "IIIIIIIII", "IIIIIIIII", "IIIIIIIII", "IIIIIIIII",
                        "IIIIIIIII", "IIIIIIIII", "IIIIIIIII", "IIIIIIIII",
                        "IIIIICIII")
                .aisle(ORE_PLANT_WORK_LAYER)     // layer 2 (workstation array)
                .aisle(ORE_PLANT_HOLLOW_LAYER)   // layer 3
                .aisle(ORE_PLANT_HOLLOW_LAYER)   // layer 4
                .aisle(ORE_PLANT_HOLLOW_LAYER)   // layer 5
                .aisle(ORE_PLANT_WORK_LAYER)     // layer 6 (workstation array)
                .aisle( // layer 7 (top industrial face)
                        "IIIIIIIII", "IIIIIIIII", "IIIIIIIII", "IIIIIIIII",
                        "IIIIIIIII", "IIIIIIIII", "IIIIIIIII", "IIIIIIIII",
                        "IIIIIIIII")
                .where('I', Predicates.blocks(industrialSteamCasing()))
                .where('B', orePlantCandidates())
                .where('M', Predicates.blocks(GSEBlocks.STEAM_GRINDING_BLOCK.get()))
                .where('A', Predicates.air())
                .where('C', Predicates.controller(Predicates.blocks(definition.getBlock())))
                .build();
    }

    /**
     * Ore plant representative layout (large-steam-ore-plant.md 结构): minimum
     * 3-hatch set on the layer-2 front wall (item output bus, steam supply
     * hatch, exhaust hatch). Axis convention as in
     * {@code GSEProcessorPatterns} (layers bottom -> top, rows back -> front,
     * chars west -> east).
     */
    public static MultiblockShapeInfo orePlantShapeInfo(MultiblockMachineDefinition definition) {
        String[] bottom = {
                "WWWWWWWWW", "WWWWWWWWW", "WWWWWWWWW", "WWWWWWWWW",
                "WWWWWWWWW", "WWWWWWWWW", "WWWWWWWWW", "WWWWWWWWW",
                "WWWWKWWWW",
        };
        String[] workWithHatches = {
                "WBBBBBBBW",
                "BAAAAAAAB",
                "BAMAMAMAB",
                "BAAAAAAAB",
                "BAMAMAMAB",
                "BAAAAAAAB",
                "BAMAMAMAB",
                "BAAAAAAAB",
                "WBOSEBBBW",
        };
        String[] hollow = ORE_PLANT_HOLLOW_LAYER.clone();
        String[] workPlain = ORE_PLANT_WORK_LAYER.clone();
        String[] top = {
                "WWWWWWWWW", "WWWWWWWWW", "WWWWWWWWW", "WWWWWWWWW",
                "WWWWWWWWW", "WWWWWWWWW", "WWWWWWWWW", "WWWWWWWWW",
                "WWWWWWWWW",
        };
        String[][] layers = {
                bottom,
                workWithHatches,
                hollow, hollow, hollow,
                workPlain,
                top,
        };
        return buildShapeInfo(layers)
                .where('W', industrialSteamCasing())
                .where('B', bronzeSteamCasing())
                .where('M', GSEBlocks.STEAM_GRINDING_BLOCK.get())
                .where('A', Blocks.AIR)
                .where('K', definition, Direction.NORTH)
                .where('O', GTMachines.STEAM_EXPORT_BUS, Direction.NORTH)
                .where('S', STEAM_SUPPLY_HATCH, Direction.NORTH)
                .where('E', STEAM_EXHAUST_HATCH, Direction.NORTH)
                .build();
    }

    // =====================================================================
    // F2 大型蒸汽流体钻井: 7×7×11 井架塔
    // =====================================================================

    /**
     * The fluid drill's candidate rule for the 172 replaceable
     * steam-machine-casing positions (large-steam-fluid-drill.md 议题 4):
     * ring walls (layers 2-8) plus the stepped 5×5 crown rings (layers 9-10).
     * 产出机器无输入面 — no item bus, no fluid INPUT hatch of any family;
     * outputs are fluid output hatches of BOTH families (GTCEu standard or
     * the mod's steam fluid output hatch, 可选或混用). The exhaust hatch
     * takes one candidate slot (必须且只能 1 个) and the 156-casing minimum
     * bounds the hatch total at 16 (172 − 156).
     */
    private static TraceabilityPredicate fluidDrillCandidates() {
        return Predicates.blocks(bronzeSteamCasing()).setMinGlobalLimited(156)
                .or(Predicates.abilities(PartAbility.STEAM))
                .or(Predicates.abilities(PartAbility.EXPORT_FLUIDS))
                .or(Predicates.abilities(GSEPartAbilities.STEAM_EXPORT_FLUIDS))
                .or(Predicates.blocks(STEAM_EXHAUST_HATCH.getBlock()).setExactLimit(1));
    }

    /** Row set of a drill ring layer (h2-h7: pipe column; h8: mixing separator). */
    private static String[] fluidDrillRingLayer(char centreChar) {
        String centreRow = "CAA" + centreChar + "AC";
        String plainRow = "CAAAAAC";
        return new String[]{
                "ICCCCCI",
                plainRow, plainRow, plainRow, plainRow,
                centreRow,
                plainRow, plainRow, plainRow, plainRow,
                "ICCCCCI",
        };
    }

    /** Row set of a drill crown layer (h9-h10, 5×5 ring inset). */
    private static final String[] FLUID_DRILL_CROWN_LAYER = {
            "       ",
            "       ",
            "       ",
            " CCCCC ",
            " CAAAC ",
            " CAAAC ",
            " CAAAC ",
            " CCCCC ",
            "       ",
            "       ",
            "       ",
    };

    /** Row set of the drill top cap (h11, 3×3 industrial, centred). */
    private static final String[] FLUID_DRILL_CAP_LAYER = {
            "       ",
            "       ",
            "       ",
            "       ",
            "  III  ",
            "  III  ",
            "  III  ",
            "       ",
            "       ",
            "       ",
            "       ",
    };

    /**
     * 大型蒸汽流体钻井: fixed 7×7×11 derrick tower
     * (large-steam-fluid-drill.md 议题 4 逐层图). Bottom face full industrial
     * (front-centre controller); layers 2-8 are 7×7 rings (industrial corner
     * columns, steam-casing walls) around the 5×5 interior with the centre
     * bronze pipe column (h2-h7) and the h8 Steam Mixing Block separator;
     * layers 9-10 are stepped 5×5 steam rings; layer 11 is the 3×3
     * industrial cap. Interior air is STRICT air.
     */
    public static BlockPattern createFluidDrill(MultiblockMachineDefinition definition) {
        return FactoryBlockPattern.start(RelativeDirection.LEFT, RelativeDirection.FRONT, RelativeDirection.UP)
                .aisle( // h1 (bottom industrial face, controller front-centre)
                        "IIIIIII", "IIIIIII", "IIIIIII", "IIIIIII", "IIIIIII",
                        "IIIIIII", "IIIIIII", "IIIIIII", "IIIIIII", "IIIIIII",
                        "IIIIKII")
                .aisle(fluidDrillRingLayer('P')) // h2 (pipe column root)
                .aisle(fluidDrillRingLayer('P')) // h3
                .aisle(fluidDrillRingLayer('P')) // h4
                .aisle(fluidDrillRingLayer('P')) // h5
                .aisle(fluidDrillRingLayer('P')) // h6
                .aisle(fluidDrillRingLayer('P')) // h7 (pipe column top)
                .aisle(fluidDrillRingLayer('M')) // h8 (mixing separator)
                .aisle(FLUID_DRILL_CROWN_LAYER)  // h9
                .aisle(FLUID_DRILL_CROWN_LAYER)  // h10
                .aisle(FLUID_DRILL_CAP_LAYER)    // h11 (industrial cap)
                .where('I', Predicates.blocks(industrialSteamCasing()))
                .where('C', fluidDrillCandidates())
                .where('P', Predicates.blocks(GTBlocks.CASING_BRONZE_PIPE.get()))
                .where('M', Predicates.blocks(GSEBlocks.STEAM_MIXING_BLOCK.get()))
                .where('A', Predicates.air())
                .where('K', Predicates.controller(Predicates.blocks(definition.getBlock())))
                .build();
    }

    /**
     * Fluid drill representative layout (large-steam-fluid-drill.md 结构):
     * minimum 4-hatch set on the layer-2 front wall (steam supply hatch, two
     * fluid output hatches of both families, exhaust hatch). Axis convention
     * as in {@code GSEProcessorPatterns} (layers bottom -> top, rows back ->
     * front, chars west -> east).
     */
    public static MultiblockShapeInfo fluidDrillShapeInfo(MultiblockMachineDefinition definition) {
        String[] bottom = new String[11];
        for (int i = 0; i < 10; i++) {
            bottom[i] = "IIIIIII";
        }
        bottom[10] = "IIIKIII";
        String[] ringWithHatches = fluidDrillRingLayer('P').clone();
        ringWithHatches[10] = "ISFEGCI";
        String[] ringPlain = fluidDrillRingLayer('P');
        String[] ringSeparator = fluidDrillRingLayer('M');
        String[] crown = FLUID_DRILL_CROWN_LAYER.clone();
        String[] cap = FLUID_DRILL_CAP_LAYER.clone();
        String[][] layers = {
                bottom,
                ringWithHatches,
                ringPlain, ringPlain, ringPlain, ringPlain, ringPlain,
                ringSeparator,
                crown,
                crown.clone(),
                cap,
        };
        return buildShapeInfo(layers)
                .where('I', industrialSteamCasing())
                .where('C', bronzeSteamCasing())
                .where('P', GTBlocks.CASING_BRONZE_PIPE.get())
                .where('M', GSEBlocks.STEAM_MIXING_BLOCK.get())
                .where('A', Blocks.AIR)
                .where('K', definition, Direction.NORTH)
                .where('S', STEAM_SUPPLY_HATCH, Direction.NORTH)
                .where('F', GTMachines.FLUID_EXPORT_HATCH[1], Direction.NORTH)
                .where('G', STEAM_FLUID_EXPORT_HATCH, Direction.NORTH)
                .where('E', STEAM_EXHAUST_HATCH, Direction.NORTH)
                .build();
    }

    /**
     * Converts LEFT/FRONT/UP pattern layers into the preview's positive X/Y/Z
     * coordinates, keeping the controller on the north (z = 0) wall.
     */
    private static MultiblockShapeInfo.ShapeInfoBuilder buildShapeInfo(String[][] layers) {
        int height = layers.length;
        int width = layers[0][0].length();
        int depth = layers[0].length;
        var builder = MultiblockShapeInfo.builder();
        for (int r = depth - 1; r >= 0; r--) {
            String[] rows = new String[height];
            for (int l = 0; l < height; l++) {
                StringBuilder sb = new StringBuilder();
                for (int a = width - 1; a >= 0; a--) {
                    sb.append(layers[l][r].charAt(a));
                }
                rows[l] = sb.toString();
            }
            builder.aisle(rows);
        }
        return builder;
    }
}
