package com.hoshino.gregsteamexpansion.registry;

import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;
import com.gregtechceu.gtceu.api.pattern.BlockPattern;
import com.gregtechceu.gtceu.api.pattern.FactoryBlockPattern;
import com.gregtechceu.gtceu.api.pattern.MultiblockShapeInfo;
import com.gregtechceu.gtceu.api.pattern.Predicates;
import com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate;
import com.gregtechceu.gtceu.api.data.chemical.ChemicalHelper;
import com.gregtechceu.gtceu.api.data.tag.TagPrefix;
import com.gregtechceu.gtceu.common.data.GTBlocks;
import com.gregtechceu.gtceu.common.data.GTMaterials;
import com.gregtechceu.gtceu.common.data.GTMachines;
import com.hoshino.gregsteamexpansion.registry.GSEBlocks;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;

/**
 * Fixed patterns for the two steam crushers (steam-crushers.md 结构):
 * the small crusher is a fixed 3×3×3 with a bronze-frame core and four steam
 * grinding blocks, the large crusher a fixed 7×7×9 cylinder-and-drill.
 *
 * <p>Aisle order follows {@link com.gregtechceu.gtceu.api.pattern.util.RelativeDirection}
 * like {@link GSEFurnacePatterns}: aisles stack bottom-up, each aisle string is
 * one row from the machine's back (index 0) to its front (last index), chars run
 * left (west when the controller faces south) to right.</p>
 *
 * <p>Cross-type interface counts (exactly one input bus across steam/electric/ME
 * candidates, output-bus + supply-hatch combined limit) cannot be expressed as
 * per-predicate pattern limits, so {@code AbstractSteamCrusherMachine} re-checks
 * them against the collected parts after formation.</p>
 */
public final class GSECrusherPatterns {

    private GSECrusherPatterns() {}

    /** gtceu:steam_machine_casing — the bronze steam machine casing. */
    public static Block bronzeSteamCasing() {
        return GTBlocks.CASING_BRONZE_BRICKS.get();
    }

    public static Block bronzeFrame() {
        return ChemicalHelper.getBlock(TagPrefix.frameGt, GTMaterials.Bronze);
    }

    public static Block bronzePipeCasing() {
        return GTBlocks.CASING_BRONZE_PIPE.get();
    }

    /** The small crusher's unified candidate rule for the 21 X positions. */
    private static TraceabilityPredicate smallCandidates() {
        return Predicates.blocks(bronzeSteamCasing())
                .or(Predicates.abilities(PartAbility.STEAM_IMPORT_ITEMS).setExactLimit(1))
                .or(Predicates.abilities(PartAbility.STEAM_EXPORT_ITEMS).setExactLimit(1))
                .or(Predicates.abilities(PartAbility.STEAM).setExactLimit(1));
    }

    /**
     * The large crusher's unified candidate rule for the 127 W positions: item
     * input buses accept steam, electric-era and (when the AE2 integration
     * registers them under the standard abilities) ME buses; output buses the
     * same. Cross-type counts are re-checked post-formation.
     */
    private static TraceabilityPredicate largeCandidates() {
        return Predicates.blocks(bronzeSteamCasing()).setMinGlobalLimited(110)
                .or(Predicates.abilities(PartAbility.STEAM_IMPORT_ITEMS))
                .or(Predicates.abilities(PartAbility.IMPORT_ITEMS))
                .or(Predicates.abilities(PartAbility.STEAM_EXPORT_ITEMS))
                .or(Predicates.abilities(PartAbility.EXPORT_ITEMS))
                .or(Predicates.abilities(PartAbility.STEAM))
                .or(Predicates.blocks(GSEMachines.STEAM_EXHAUST_HATCH.getBlock()).setExactLimit(1));
    }

    /** 蒸汽粉碎机: fixed 3×3×3 (steam-crushers.md 分层结构图). */
    public static BlockPattern createSmall(MultiblockMachineDefinition definition) {
        return FactoryBlockPattern.start(com.gregtechceu.gtceu.api.pattern.util.RelativeDirection.LEFT,
                com.gregtechceu.gtceu.api.pattern.util.RelativeDirection.FRONT,
                com.gregtechceu.gtceu.api.pattern.util.RelativeDirection.UP)
                .aisle("XXX", "XGX", "XXX")
                .aisle("XXX", "GFG", "XKX")
                .aisle("XXX", "XGX", "XXX")
                .where('X', smallCandidates())
                .where('G', Predicates.blocks(GSEBlocks.STEAM_GRINDING_BLOCK.get()))
                .where('F', Predicates.blocks(bronzeFrame()))
                .where('K', Predicates.controller(Predicates.blocks(definition.getBlock())))
                .build();
    }

    /**
     * 大型蒸汽粉碎机: fixed 7×7×9 (steam-crushers.md 圆筒分层截面 / 钻头分层截面),
     * aisles bottom-up: layer 1 base, layers 2-6 rings, layers 7-8 the 3×3 drill
     * sections, layer 9 the 5×5 drill base.
     */
    public static BlockPattern createLarge(MultiblockMachineDefinition definition) {
        return FactoryBlockPattern.start(com.gregtechceu.gtceu.api.pattern.util.RelativeDirection.LEFT,
                com.gregtechceu.gtceu.api.pattern.util.RelativeDirection.FRONT,
                com.gregtechceu.gtceu.api.pattern.util.RelativeDirection.UP)
                .aisle(
                        "WWWWWWW",
                        "WWWWWWW",
                        "WWWWWWW",
                        "WWWPWWW",
                        "WWWWWWW",
                        "WWWWWWW",
                        "WWWWWWW")
                .aisle(
                        "  WWW  ",
                        " W   W ",
                        "W     W",
                        "W  P  W",
                        "W     W",
                        " W   W ",
                        "  WWW  ")
                .aisle(
                        "  WWW  ",
                        " W   W ",
                        "W     W",
                        "W  P  W",
                        "W     W",
                        " W   W ",
                        "  WKW  ")
                .aisle(
                        "  WWW  ",
                        " W   W ",
                        "W     W",
                        "W  P  W",
                        "W     W",
                        " W   W ",
                        "  WWW  ")
                .aisle(
                        "  WWW  ",
                        " W   W ",
                        "W     W",
                        "W  G  W",
                        "W     W",
                        " W   W ",
                        "  WWW  ")
                .aisle(
                        "  WWW  ",
                        " W   W ",
                        "W     W",
                        "W  G  W",
                        "W     W",
                        " W   W ",
                        "  WWW  ")
                .aisle(
                        "       ",
                        "       ",
                        "  CCC  ",
                        "  CGC  ",
                        "  CCC  ",
                        "       ",
                        "       ")
                .aisle(
                        "       ",
                        "       ",
                        "  CCC  ",
                        "  CGC  ",
                        "  CCC  ",
                        "       ",
                        "       ")
                .aisle(
                        "       ",
                        " CCCCC ",
                        " CCCCC ",
                        " CCGCC ",
                        " CCCCC ",
                        " CCCCC ",
                        "       ")
                .where('W', largeCandidates())
                .where('C', Predicates.blocks(bronzeSteamCasing()))
                .where('P', Predicates.blocks(bronzePipeCasing()))
                .where('G', Predicates.blocks(GSEBlocks.STEAM_GRINDING_BLOCK.get()))
                .where('K', Predicates.controller(Predicates.blocks(definition.getBlock())))
                .build();
    }

/** Small crusher representative layout (steam-crushers.md 蒸汽粉碎机代表布局).
 * <p>ShapeInfo axis convention, derived from
 * {@code BlockPattern#setActualRelativeOffset} for the
 * {@code start(LEFT, FRONT, UP)} pattern this mod uses (facing NORTH):
 * pattern chars run west, rows run north and aisles run up.
 * {@code PatternPreviewWidget} places shape arrays directly at positive
 * {@code [x][y][z]} coordinates, so both horizontal pattern axes must be
 * reversed. LDLib's builder bakes {@code [char][row][aisle]}, so shape aisles
 * run north to south, rows bottom to top, and chars west to east.
 * All machines face NORTH out of the first-aisle wall.</p> */
public static MultiblockShapeInfo smallShapeInfo(MultiblockMachineDefinition definition) {
    // layers bottom -> top, each 3 rows south -> north
    String[][] layers = {
            {"XXX", "XGX", "ISO"},
            {"XXX", "GFG", "XKX"},
            {"XXX", "XGX", "XXX"},
    };
    return buildShapeInfo(layers)
            .where('X', bronzeSteamCasing())
            .where('G', GSEBlocks.STEAM_GRINDING_BLOCK.get())
            .where('F', bronzeFrame())
            .where('I', GTMachines.STEAM_IMPORT_BUS, Direction.NORTH)
            .where('S', GSEMachines.STEAM_SUPPLY_HATCH, Direction.NORTH)
            .where('O', GTMachines.STEAM_EXPORT_BUS, Direction.NORTH)
            .where('K', definition, Direction.NORTH)
            .build();
}

/**
 * Large crusher representative layout (steam-crushers.md 大型蒸汽粉碎机代表布局):
 * minimum-interface set with the supply hatch on layer 2, input/output buses
 * beside the layer-3 controller and the exhaust hatch on layer 4. Axis
 * convention as in {@link #smallShapeInfo(MultiblockMachineDefinition)}.
 * Dots are air (ring interior, open cylinder top and the area around the
 * drill). */
public static MultiblockShapeInfo largeShapeInfo(MultiblockMachineDefinition definition) {
    String[][] layers = {
            {"WWWWWWW", "WWWWWWW", "WWWWWWW", "WWWPWWW", "WWWWWWW", "WWWWWWW", "WWWWWWW"},
            {"..WWW..", ".W...W.", "W.....W", "W..P..W", "W.....W", ".W...W.", "..WSW.."},
            {"..WWW..", ".W...W.", "W.....W", "W..P..W", "W.....W", ".W...W.", "..IKO.."},
            {"..WWW..", ".W...W.", "W.....W", "W..P..W", "W.....W", ".W...W.", "..WEW.."},
            {"..WWW..", ".W...W.", "W.....W", "W..G..W", "W.....W", ".W...W.", "..WWW.."},
            {"..WWW..", ".W...W.", "W.....W", "W..G..W", "W.....W", ".W...W.", "..WWW.."},
            {".......", ".......", "..CCC..", "..CGC..", "..CCC..", ".......", "......."},
            {".......", ".......", "..CCC..", "..CGC..", "..CCC..", ".......", "......."},
            {".......", ".CCCCC.", ".CCCCC.", ".CCGCC.", ".CCCCC.", ".CCCCC.", "......."},
    };
    return buildShapeInfo(layers)
            .where('W', bronzeSteamCasing())
            .where('C', bronzeSteamCasing())
            .where('P', bronzePipeCasing())
            .where('G', GSEBlocks.STEAM_GRINDING_BLOCK.get())
            .where('I', GTMachines.STEAM_IMPORT_BUS, Direction.NORTH)
            .where('S', GSEMachines.STEAM_SUPPLY_HATCH, Direction.NORTH)
            .where('O', GTMachines.STEAM_EXPORT_BUS, Direction.NORTH)
            .where('E', GSEMachines.STEAM_EXHAUST_HATCH, Direction.NORTH)
            .where('K', definition, Direction.NORTH)
            .where('.', net.minecraft.world.level.block.Blocks.AIR.defaultBlockState())
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
