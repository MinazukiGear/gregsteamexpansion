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
                        "..WWW..",
                        ".W...W.",
                        "W.....W",
                        "W..P..W",
                        "W.....W",
                        ".W...W.",
                        "..WWW..")
                .aisle(
                        "..WWW..",
                        ".W...W.",
                        "W.....W",
                        "W..P..W",
                        "W.....W",
                        ".W...W.",
                        "..WKW..")
                .aisle(
                        "..WWW..",
                        ".W...W.",
                        "W.....W",
                        "W..P..W",
                        "W.....W",
                        ".W...W.",
                        "..WWW..")
                .aisle(
                        "..WWW..",
                        ".W...W.",
                        "W.....W",
                        "W..G..W",
                        "W.....W",
                        ".W...W.",
                        "..WWW..")
                .aisle(
                        "..WWW..",
                        ".W...W.",
                        "W.....W",
                        "W..G..W",
                        "W.....W",
                        ".W...W.",
                        "..WWW..")
                .aisle(
                        ".......",
                        ".......",
                        "..CCC..",
                        "..CGC..",
                        "..CCC..",
                        ".......",
                        ".......")
                .aisle(
                        ".......",
                        ".......",
                        "..CCC..",
                        "..CGC..",
                        "..CCC..",
                        ".......",
                        ".......")
                .aisle(
                        ".......",
                        ".CCCCC.",
                        ".CCCCC.",
                        ".CCGCC.",
                        ".CCCCC.",
                        ".CCCCC.",
                        ".......")
                .where('W', largeCandidates())
                .where('C', Predicates.blocks(bronzeSteamCasing()))
                .where('P', Predicates.blocks(bronzePipeCasing()))
                .where('G', Predicates.blocks(GSEBlocks.STEAM_GRINDING_BLOCK.get()))
                .where('K', Predicates.controller(Predicates.blocks(definition.getBlock())))
                .build();
    }

    /** Small crusher representative layout (steam-crushers.md 蒸汽粉碎机代表布局). */
    public static MultiblockShapeInfo smallShapeInfo(MultiblockMachineDefinition definition) {
        return MultiblockShapeInfo.builder()
                .aisle("CCC", "CGC", "CCC")
                .aisle("CCC", "GFG", "XKX")
                .aisle("CCC", "CGC", "ISO")
                .where('C', bronzeSteamCasing())
                .where('G', GSEBlocks.STEAM_GRINDING_BLOCK.get())
                .where('F', bronzeFrame())
                .where('X', bronzeSteamCasing())
                .where('I', GTMachines.STEAM_IMPORT_BUS, Direction.WEST)
                .where('S', GSEMachines.STEAM_SUPPLY_HATCH, Direction.SOUTH)
                .where('O', GTMachines.STEAM_EXPORT_BUS, Direction.EAST)
                .where('K', definition, Direction.SOUTH)
                .build();
    }

    /**
     * Large crusher representative layout (steam-crushers.md 大型蒸汽粉碎机代表布局):
     * minimum-interface set with the supply hatch on layer 2, input/output buses
     * beside the layer-3 controller and the exhaust hatch on layer 4.
     */
    public static MultiblockShapeInfo largeShapeInfo(MultiblockMachineDefinition definition) {
        String[] layer1 = {
                "CCCCCCC",
                "CCCCCCC",
                "CCCCCCC",
                "CCCPCCC",
                "CCCCCCC",
                "CCCCCCC",
                "CCCCCCC"};
        String[] layer2 = {
                "..CCC..",
                ".C...C.",
                "C.....C",
                "C..P..C",
                "C.....C",
                ".C...C.",
                "..CSC.."};
        String[] layer3 = {
                "..CCC..",
                ".C...C.",
                "C.....C",
                "C..P..C",
                "C.....C",
                ".C...C.",
                "..IKO.."};
        String[] layer4 = {
                "..CCC..",
                ".C...C.",
                "C.....C",
                "C..P..C",
                "C.....C",
                ".C...C.",
                "..CEC.."};
        String[] layer5 = {
                "..CCC..",
                ".C...C.",
                "C.....C",
                "C..G..C",
                "C.....C",
                ".C...C.",
                "..CCC.."};
        String[] layer6 = {
                "..CCC..",
                ".C...C.",
                "C.....C",
                "C..G..C",
                "C.....C",
                ".C...C.",
                "..CCC.."};
        String[] layer7 = {
                ".......",
                ".......",
                "..CCC..",
                "..CGC..",
                "..CCC..",
                ".......",
                "......."};
        String[] layer8 = {
                ".......",
                ".......",
                "..CCC..",
                "..CGC..",
                "..CCC..",
                ".......",
                "......."};
        String[] layer9 = {
                ".......",
                ".CCCCC.",
                ".CCCCC.",
                ".CCGCC.",
                ".CCCCC.",
                ".CCCCC.",
                "......."};

        var builder = MultiblockShapeInfo.builder();
        for (String[] layer : new String[][]{layer1, layer2, layer3, layer4, layer5, layer6, layer7, layer8, layer9}) {
            builder.aisle(layer);
        }
        // W positions must be matched by the pattern's candidate rule; the
        // ring interior stays air, so use plain casings for the two filler
        // ring slots of layers 5/6 (they sit where the pattern has W).
        return builder
                .where('C', bronzeSteamCasing())
                .where('W', bronzeSteamCasing())
                .where('P', bronzePipeCasing())
                .where('G', GSEBlocks.STEAM_GRINDING_BLOCK.get())
                .where('I', GTMachines.STEAM_IMPORT_BUS, Direction.WEST)
                .where('S', GSEMachines.STEAM_SUPPLY_HATCH, Direction.SOUTH)
                .where('O', GTMachines.STEAM_EXPORT_BUS, Direction.EAST)
                .where('E', GSEMachines.STEAM_EXHAUST_HATCH, Direction.SOUTH)
                .where('K', definition, Direction.SOUTH)
                .build();
    }
}
