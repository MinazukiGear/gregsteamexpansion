package com.hoshino.gregsteamexpansion.registry;

import com.gregtechceu.gtceu.api.data.chemical.ChemicalHelper;
import com.gregtechceu.gtceu.api.data.tag.TagPrefix;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;
import com.gregtechceu.gtceu.api.pattern.BlockPattern;
import com.gregtechceu.gtceu.api.pattern.FactoryBlockPattern;
import com.gregtechceu.gtceu.api.pattern.MultiblockShapeInfo;
import com.gregtechceu.gtceu.api.pattern.Predicates;
import com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate;
import com.gregtechceu.gtceu.api.pattern.util.RelativeDirection;
import com.gregtechceu.gtceu.common.data.GTBlocks;
import com.gregtechceu.gtceu.common.data.GCYMBlocks;
import com.gregtechceu.gtceu.common.data.GTMaterials;
import com.gregtechceu.gtceu.common.data.GTMachines;
import com.hoshino.gregsteamexpansion.GregSteamExpansion;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.BlockState;

import com.lowdragmc.lowdraglib.utils.BlockInfo;

import java.util.List;

/**
 * Fixed patterns for the light steam processor family (steam-compressor.md /
 * steam-extractor.md / steam-forge.md 议题 4): 3×3×3 small machines with a
 * bronze-frame core, structured like the small crusher (GSECrusherPatterns).
 *
 * <p>Aisle order follows {@link RelativeDirection} like the crusher patterns:
 * aisles stack bottom-up, each aisle string is one row from the machine's back
 * (index 0) to its front (last index), chars run left to right.</p>
 *
 * <p>Interface counts (buses/supply hatch each ≥1, combined ≤8) cannot be
 * expressed as per-predicate pattern limits, so
 * {@code AbstractSteamProcessorMachine} re-checks them against the collected
 * parts after formation.</p>
 */
public final class GSEProcessorPatterns {

    private GSEProcessorPatterns() {}

    /** gtceu:steam_machine_casing — the bronze steam machine casing. */
    public static Block bronzeSteamCasing() {
        return GTBlocks.CASING_BRONZE_BRICKS.get();
    }

    public static Block bronzeFrame() {
        return ChemicalHelper.getBlock(TagPrefix.frameGt, GTMaterials.Bronze);
    }

    /** The family's unified candidate rule for the shell positions. */
    private static TraceabilityPredicate shellCandidates() {
        return Predicates.blocks(bronzeSteamCasing())
                .or(Predicates.abilities(PartAbility.STEAM_IMPORT_ITEMS))
                .or(Predicates.abilities(PartAbility.STEAM_EXPORT_ITEMS))
                .or(Predicates.abilities(PartAbility.STEAM));
    }

    /**
     * 背面中心活塞 (steam-compressor.md 议题 4 建议): a vanilla piston that is
     * NOT extended and whose pushing facing points at the controller column
     * (i.e. towards the structure front and the central bronze frame). The
     * design doc allows relaxing to any facing if the strict check proves too
     * awkward in practice; strict is implemented first per the documented
     * suggestion.
     */
    private static TraceabilityPredicate rearPiston() {
        return Predicates.custom(GSEProcessorPatterns::testPiston,
                () -> new BlockInfo[]{new BlockInfo(Blocks.PISTON.defaultBlockState())});
    }

    private static boolean testPiston(com.gregtechceu.gtceu.api.pattern.MultiblockState state) {
        BlockState piston = state.getBlockState();
        if (!piston.is(Blocks.PISTON) || !piston.hasProperty(DirectionalBlock.FACING)
                || !piston.hasProperty(PistonBaseBlock.EXTENDED)) {
            return false;
        }
        // 未伸出: an extended base block plus its head would read EXTENDED=true.
        if (piston.getValue(PistonBaseBlock.EXTENDED)) {
            return false;
        }
        Direction facing = piston.getValue(DirectionalBlock.FACING);
        // 推压方向指向内部青铜框架: from the back-centre piston towards the
        // front-bottom-centre controller column = the structure's front facing.
        var delta = state.controllerPos.subtract(state.getPos());
        Direction toward = Direction.fromDelta(delta.getX(), 0, delta.getZ());
        return toward != null && facing == toward;
    }

    /**
     * 蒸汽压缩机: fixed 3×3×3 (steam-compressor.md 议题 4 逐层图) — bottom layer
     * carries the front-centre controller, middle layer the bronze frame core
     * and the back-centre piston, top layer plain shell.
     */
    public static BlockPattern createCompressor(MultiblockMachineDefinition definition) {
        return FactoryBlockPattern.start(RelativeDirection.LEFT, RelativeDirection.FRONT, RelativeDirection.UP)
                .aisle("BBB", "BBB", "BCB")
                .aisle("BPB", "BFB", "BBB")
                .aisle("BBB", "BBB", "BBB")
                .where('B', shellCandidates())
                .where('F', Predicates.blocks(bronzeFrame()))
                .where('P', rearPiston())
                .where('C', Predicates.controller(Predicates.blocks(definition.getBlock())))
                .build();
    }

    /**
     * Compressor representative layout (steam-compressor.md 结构): controller
     * front-bottom-centre, input/output buses beside it, supply hatch on the
     * top-front-centre, piston on the back-centre of the middle layer facing
     * the frame. Axis convention as in {@code GSECrusherPatterns#smallShapeInfo}
     * (layers bottom -> top, each 3 rows south -> north, chars west -> east).
     */
    public static MultiblockShapeInfo compressorShapeInfo(MultiblockMachineDefinition definition) {
        String[][] layers = {
                {"BBB", "BBB", "IKO"},
                {"BPB", "BFB", "BBB"},
                {"BBB", "BBB", "BSB"},
        };
        return buildShapeInfo(layers)
                .where('B', bronzeSteamCasing())
                .where('F', bronzeFrame())
                .where('P', Blocks.PISTON.defaultBlockState()
                        .setValue(DirectionalBlock.FACING, Direction.NORTH))
                .where('I', GTMachines.STEAM_IMPORT_BUS, Direction.NORTH)
                .where('S', GSEMachines.STEAM_SUPPLY_HATCH, Direction.NORTH)
                .where('O', GTMachines.STEAM_EXPORT_BUS, Direction.NORTH)
                .where('K', definition, Direction.NORTH)
                .build();
    }

    /** gtceu:bronze_pipe_casing — the bronze pipe casing (extraction core). */
    public static Block bronzePipeCasing() {
        return GTBlocks.CASING_BRONZE_PIPE.get();
    }

    /**
     * The extractor's candidate rule for the 24 shell positions: the family
     * shell rule plus BOTH fluid output hatch families — the GTCEu standard
     * output hatch ({@code EXPORT_FLUIDS}) and the mod's steam fluid output
     * hatch ({@code GSEPartAbilities.STEAM_EXPORT_FLUIDS}) — per
     * steam-extractor.md 议题 4 仓室表 (GTCEu 标准输出仓或本模组蒸汽流体输出仓,
     * 可选或混用). Without these abilities the required fluid output hatch
     * could never be part of the structure at all.
     */
    private static TraceabilityPredicate extractorCandidates() {
        return shellCandidates()
                .or(Predicates.abilities(PartAbility.EXPORT_FLUIDS))
                .or(Predicates.abilities(GSEPartAbilities.STEAM_EXPORT_FLUIDS));
    }

    /**
     * 蒸汽提取机: fixed 3×3×3 (steam-extractor.md 议题 4 逐层图) — bottom layer
     * carries the front-centre controller, the structure's single inner cell
     * (centre of the middle layer) is one bronze pipe casing, no air gap.
     */
    public static BlockPattern createExtractor(MultiblockMachineDefinition definition) {
        return FactoryBlockPattern.start(RelativeDirection.LEFT, RelativeDirection.FRONT, RelativeDirection.UP)
                .aisle("BBB", "BBB", "BCB")
                .aisle("BBB", "BPB", "BBB")
                .aisle("BBB", "BBB", "BBB")
                .where('B', extractorCandidates())
                .where('P', Predicates.blocks(bronzePipeCasing()))
                .where('C', Predicates.controller(Predicates.blocks(definition.getBlock())))
                .build();
    }

    /**
     * Extractor representative layout (steam-extractor.md 结构): controller
     * front-bottom-centre, input/output buses beside it, steam supply hatch and
     * fluid output hatch on the top-front wall. Axis convention as in
     * {@code GSECrusherPatterns#smallShapeInfo} (layers bottom -> top, each 3
     * rows south -> north, chars west -> east).
     */
    public static MultiblockShapeInfo extractorShapeInfo(MultiblockMachineDefinition definition) {
        String[][] layers = {
                {"BBB", "BBB", "IKO"},
                {"BBB", "BPB", "BBB"},
                {"BBB", "BBB", "FSB"},
        };
        return buildShapeInfo(layers)
                .where('B', bronzeSteamCasing())
                .where('P', bronzePipeCasing())
                .where('I', GTMachines.STEAM_IMPORT_BUS, Direction.NORTH)
                .where('S', GSEMachines.STEAM_SUPPLY_HATCH, Direction.NORTH)
                .where('O', GTMachines.STEAM_EXPORT_BUS, Direction.NORTH)
                .where('F', GSEMachines.STEAM_FLUID_EXPORT_HATCH, Direction.NORTH)
                .where('K', definition, Direction.NORTH)
                .build();
    }

    /**
     * 蒸汽锻压机: 3×3×5 tower (steam-forge.md 议题 4 逐层图) — bottom two
     * layers are full 3×3 (layer 2 centre holds the Steam Assembly Block as
     * the forge-anvil core), the top three layers carry only the depth-centre
     * hammer row (spaces are don't-care). No air gap inside the forge layers.
     */
    public static BlockPattern createForge(MultiblockMachineDefinition definition) {
        return FactoryBlockPattern.start(RelativeDirection.LEFT, RelativeDirection.FRONT, RelativeDirection.UP)
                .aisle("BBB", "BBB", "BCB")
                .aisle("BBB", "BMB", "BBB")
                .aisle("   ", "BBB", "   ")
                .aisle("   ", "BBB", "   ")
                .aisle("   ", "BBB", "   ")
                .where('B', shellCandidates())
                .where('M', Predicates.blocks(GSEBlocks.STEAM_ASSEMBLY_BLOCK.get()))
                .where('C', Predicates.controller(Predicates.blocks(definition.getBlock())))
                .build();
    }

    /**
     * Forge representative layout (steam-forge.md 结构): controller
     * front-bottom-centre, input/output buses beside it, steam supply hatch on
     * the top hammer row centre. Spaces bake as air in the preview. Axis
     * convention as in {@code GSECrusherPatterns#smallShapeInfo} (layers
     * bottom -> top, each 3 rows south -> north, chars west -> east).
     */
    public static MultiblockShapeInfo forgeShapeInfo(MultiblockMachineDefinition definition) {
        String[][] layers = {
                {"BBB", "BBB", "IKO"},
                {"BBB", "BMB", "BBB"},
                {"   ", "BBB", "   "},
                {"   ", "BBB", "   "},
                {"   ", "BSB", "   "},
        };
        return buildShapeInfo(layers)
                .where('B', bronzeSteamCasing())
                .where('M', GSEBlocks.STEAM_ASSEMBLY_BLOCK.get())
                .where('I', GTMachines.STEAM_IMPORT_BUS, Direction.NORTH)
                .where('S', GSEMachines.STEAM_SUPPLY_HATCH, Direction.NORTH)
                .where('O', GTMachines.STEAM_EXPORT_BUS, Direction.NORTH)
                .where('K', definition, Direction.NORTH)
                .build();
    }

    /** gtceu:industrial_steam_casing — the industrial steam machine casing (棱部). */
    public static Block industrialSteamCasing() {
        return GCYMBlocks.CASING_INDUSTRIAL_STEAM.get();
    }

    /**
     * 任意种类的玻璃 (large-steam-ore-washer.md 议题 4): every vanilla and
     * GTCEu glass (plain/tempered/clean GlassBlock, stained, tinted) extends
     * AbstractGlassBlock; add-on glasses qualify through the common
     * {@code c:glass} item tag on the block's item.
     */
    private static TraceabilityPredicate anyGlass() {
        return Predicates.custom(GSEProcessorPatterns::testGlass,
                () -> new BlockInfo[]{new BlockInfo(Blocks.GLASS.defaultBlockState())});
    }

    private static boolean testGlass(com.gregtechceu.gtceu.api.pattern.MultiblockState state) {
        Block block = state.getBlockState().getBlock();
        if (block instanceof net.minecraft.world.level.block.AbstractGlassBlock) {
            return true;
        }
        return block.asItem() != net.minecraft.world.item.Items.AIR
                && block.asItem().builtInRegistryHolder().is(net.minecraft.tags.TagKey.create(
                        net.minecraft.core.registries.Registries.ITEM,
                        new net.minecraft.resources.ResourceLocation("c", "glass")));
    }

    /**
     * The ore washer's candidate rule for the 225 wall/floor positions: steam
     * machine casing with hatches as replacements. 蒸汽流体输入/输出仓 are NOT
     * admissible (议题 3): the mod's steam fluid hatches register under
     * GSEPartAbilities.STEAM_*_FLUIDS, never under the GTCEu IMPORT_FLUIDS
     * ability admitted here. The 205-casing minimum bounds the hatch total at
     * 20 (225 − 20, 议题 4 仓室上限); the exhaust hatch takes one candidate
     * slot and is post-checked to exactly one by the controller.
     */
    private static TraceabilityPredicate washerCandidates() {
        return Predicates.blocks(bronzeSteamCasing()).setMinGlobalLimited(205)
                .or(Predicates.abilities(PartAbility.STEAM_IMPORT_ITEMS))
                .or(Predicates.abilities(PartAbility.IMPORT_ITEMS))
                .or(Predicates.abilities(PartAbility.STEAM_EXPORT_ITEMS))
                .or(Predicates.abilities(PartAbility.EXPORT_ITEMS))
                .or(Predicates.abilities(PartAbility.STEAM))
                .or(Predicates.abilities(PartAbility.IMPORT_FLUIDS))
                .or(Predicates.blocks(GSEMachines.STEAM_EXHAUST_HATCH.getBlock()).setExactLimit(1));
    }

    /**
     * 大型蒸汽洗矿厂: fixed 11×11×6 (large-steam-ore-washer.md 议题 4 逐层图).
     * Aisle rows run back -> front, so the design's front row (row 0) is the
     * LAST string of each aisle. Bottom layer: full 11×11 floor, edges are
     * industrial casings and the controller sits front-edge-centre (one edge
     * position). Layer 2 interior carries the mixing-block cross (central row
     * + central column, 17 blocks). Layers 3-5 are hollow. The top face is
     * glass (any glass). Interior air is STRICT air (Predicates.air).
     */
    public static BlockPattern createOreWasher(MultiblockMachineDefinition definition) {
        return FactoryBlockPattern.start(RelativeDirection.LEFT, RelativeDirection.FRONT, RelativeDirection.UP)
                .aisle(
                        "IIIIIIIIIII",
                        "IBBBBBBBBBI",
                        "IBBBBBBBBBI",
                        "IBBBBBBBBBI",
                        "IBBBBBBBBBI",
                        "IBBBBBBBBBI",
                        "IBBBBBBBBBI",
                        "IBBBBBBBBBI",
                        "IBBBBBBBBBI",
                        "IBBBBBBBBBI",
                        "IIIIICIIIII")
                .aisle(
                        "IBBBBBBBBBI",
                        "BAAAAMAAAAB",
                        "BAAAAMAAAAB",
                        "BAAAAMAAAAB",
                        "BAAAAMAAAAB",
                        "BMMMMMMMMMB",
                        "BAAAAMAAAAB",
                        "BAAAAMAAAAB",
                        "BAAAAMAAAAB",
                        "BAAAAMAAAAB",
                        "IBBBBBBBBBI")
                .aisle(
                        "IBBBBBBBBBI",
                        "BAAAAAAAAAB",
                        "BAAAAAAAAAB",
                        "BAAAAAAAAAB",
                        "BAAAAAAAAAB",
                        "BAAAAAAAAAB",
                        "BAAAAAAAAAB",
                        "BAAAAAAAAAB",
                        "BAAAAAAAAAB",
                        "BAAAAAAAAAB",
                        "IBBBBBBBBBI")
                .aisle(
                        "IBBBBBBBBBI",
                        "BAAAAAAAAAB",
                        "BAAAAAAAAAB",
                        "BAAAAAAAAAB",
                        "BAAAAAAAAAB",
                        "BAAAAAAAAAB",
                        "BAAAAAAAAAB",
                        "BAAAAAAAAAB",
                        "BAAAAAAAAAB",
                        "BAAAAAAAAAB",
                        "IBBBBBBBBBI")
                .aisle(
                        "IBBBBBBBBBI",
                        "BAAAAAAAAAB",
                        "BAAAAAAAAAB",
                        "BAAAAAAAAAB",
                        "BAAAAAAAAAB",
                        "BAAAAAAAAAB",
                        "BAAAAAAAAAB",
                        "BAAAAAAAAAB",
                        "BAAAAAAAAAB",
                        "BAAAAAAAAAB",
                        "IBBBBBBBBBI")
                .aisle(
                        "IIIIIIIIIII",
                        "IGGGGGGGGGI",
                        "IGGGGGGGGGI",
                        "IGGGGGGGGGI",
                        "IGGGGGGGGGI",
                        "IGGGGGGGGGI",
                        "IGGGGGGGGGI",
                        "IGGGGGGGGGI",
                        "IGGGGGGGGGI",
                        "IGGGGGGGGGI",
                        "IIIIIIIIIII")
                .where('I', Predicates.blocks(industrialSteamCasing()))
                .where('B', washerCandidates())
                .where('G', anyGlass())
                .where('M', Predicates.blocks(GSEBlocks.STEAM_MIXING_BLOCK.get()))
                .where('A', Predicates.air())
                .where('C', Predicates.controller(Predicates.blocks(definition.getBlock())))
                .build();
    }

    /**
     * Ore washer representative layout (large-steam-ore-washer.md 结构):
     * minimum 5-hatch set on the layer-2 front wall (import bus, export bus,
     * supply hatch, standard fluid input hatch, exhaust hatch left of the
     * mixing cross's centre column). Axis convention as in
     * {@code GSECrusherPatterns#smallShapeInfo} (layers bottom -> top, each
     * layer's rows south -> north, chars west -> east).
     */
    public static MultiblockShapeInfo oreWasherShapeInfo(MultiblockMachineDefinition definition) {
        String[][] layers = {
                {"WWWWWWWWWWW", "WBBBBBBBBBW", "WBBBBBBBBBW", "WBBBBBBBBBW", "WBBBBBBBBBW",
                        "WBBBBBBBBBW", "WBBBBBBBBBW", "WBBBBBBBBBW", "WBBBBBBBBBW",
                        "WBBBBBBBBBW", "WWWWWKWWWWW"},
                {"WBBBBBBBBBW", "BAAAAMAAAAB", "BAAAAMAAAAB", "BAAAAMAAAAB", "BAAAAMAAAAB",
                        "BMMMMMMMMMB", "BAAAAMAAAAB", "BAAAAMAAAAB", "BAAAAMAAAAB",
                        "BAAAAMAAAAB", "WBIOSFEBBBW"},
                {"WBBBBBBBBBW", "BAAAAAAAAAB", "BAAAAAAAAAB", "BAAAAAAAAAB", "BAAAAAAAAAB",
                        "BAAAAAAAAAB", "BAAAAAAAAAB", "BAAAAAAAAAB", "BAAAAAAAAAB",
                        "BAAAAAAAAAB", "WBBBBBBBBBW"},
                {"WBBBBBBBBBW", "BAAAAAAAAAB", "BAAAAAAAAAB", "BAAAAAAAAAB", "BAAAAAAAAAB",
                        "BAAAAAAAAAB", "BAAAAAAAAAB", "BAAAAAAAAAB", "BAAAAAAAAAB",
                        "BAAAAAAAAAB", "WBBBBBBBBBW"},
                {"WBBBBBBBBBW", "BAAAAAAAAAB", "BAAAAAAAAAB", "BAAAAAAAAAB", "BAAAAAAAAAB",
                        "BAAAAAAAAAB", "BAAAAAAAAAB", "BAAAAAAAAAB", "BAAAAAAAAAB",
                        "BAAAAAAAAAB", "WBBBBBBBBBW"},
                {"WWWWWWWWWWW", "WGGGGGGGGGW", "WGGGGGGGGGW", "WGGGGGGGGGW", "WGGGGGGGGGW",
                        "WGGGGGGGGGW", "WGGGGGGGGGW", "WGGGGGGGGGW", "WGGGGGGGGGW",
                        "WGGGGGGGGGW", "WWWWWWWWWWW"},
        };
        return buildShapeInfo(layers)
                .where('W', industrialSteamCasing())
                .where('B', bronzeSteamCasing())
                .where('G', Blocks.GLASS)
                .where('M', GSEBlocks.STEAM_MIXING_BLOCK.get())
                .where('A', Blocks.AIR)
                .where('K', definition, Direction.NORTH)
                .where('I', GTMachines.STEAM_IMPORT_BUS, Direction.NORTH)
                .where('O', GTMachines.STEAM_EXPORT_BUS, Direction.NORTH)
                .where('S', GSEMachines.STEAM_SUPPLY_HATCH, Direction.NORTH)
                .where('F', GTMachines.FLUID_IMPORT_HATCH[1], Direction.NORTH)
                .where('E', GSEMachines.STEAM_EXHAUST_HATCH, Direction.NORTH)
                .build();
    }

    /**
     * The thermal centrifuge's candidate rule for the 84 wall positions
     * (layers 2-4, 28 per layer): steam machine casing with hatches as
     * replacements. Pure-dry recipe type — NO fluid hatch ability of any kind
     * is admitted (large-steam-thermal-centrifuge.md 议题 4 仓室表 流体仓 0),
     * so the mod's steam fluid hatches cannot appear either. The 68-casing
     * minimum bounds the hatch total (incl. the exhaust hatch) at 16
     * (84 − 68, 议题 4 仓室合计上限); the exhaust hatch takes one candidate
     * slot and is post-checked to exactly one by the controller.
     */
    private static TraceabilityPredicate thermalCentrifugeCandidates() {
        return Predicates.blocks(bronzeSteamCasing()).setMinGlobalLimited(68)
                .or(Predicates.abilities(PartAbility.STEAM_IMPORT_ITEMS))
                .or(Predicates.abilities(PartAbility.IMPORT_ITEMS))
                .or(Predicates.abilities(PartAbility.STEAM_EXPORT_ITEMS))
                .or(Predicates.abilities(PartAbility.EXPORT_ITEMS))
                .or(Predicates.abilities(PartAbility.STEAM))
                .or(Predicates.blocks(GSEMachines.STEAM_EXHAUST_HATCH.getBlock()).setExactLimit(1));
    }

    /** gtceu:bronze_firebox_casing — the bronze boiler firebox casing (heat source hearth). */
    public static Block bronzeFireboxCasing() {
        return GTBlocks.FIREBOX_BRONZE.get();
    }

    /**
     * 大型蒸汽热力离心机: fixed 9×9×7 stepped tower
     * (large-steam-thermal-centrifuge.md 议题 4 逐层图). Aisle rows run
     * back -> front, so the design's front row is the LAST string of each
     * aisle. Bottom layer: industrial ring (front-centre controller) around a
     * 7×7 bronze firebox hearth. Layers 2-4: industrial corners, steam-casing
     * walls, hollow interior with the mixing blocks (axis column layers 2-4
     * centres + impeller crosses on layers 2 and 4). Layer 5: full industrial
     * top face. Layers 6-7: solid 5×5 industrial crown inset 2 per side.
     * Interior air is STRICT air (Predicates.air).
     */
    public static BlockPattern createThermalCentrifuge(MultiblockMachineDefinition definition) {
        return FactoryBlockPattern.start(RelativeDirection.LEFT, RelativeDirection.FRONT, RelativeDirection.UP)
                .aisle(
                        "IIIIIIIII",
                        "IFFFFFFFI",
                        "IFFFFFFFI",
                        "IFFFFFFFI",
                        "IFFFFFFFI",
                        "IFFFFFFFI",
                        "IFFFFFFFI",
                        "IFFFFFFFI",
                        "IIIIICIII")
                .aisle(
                        "IBBBBBBBI",
                        "BAAAAAAAB",
                        "BAAAMAAAB",
                        "BAAAAAAAB",
                        "BAMAMAMAB",
                        "BAAAAAAAB",
                        "BAAAMAAAB",
                        "BAAAAAAAB",
                        "IBBBBBBBI")
                .aisle(
                        "IBBBBBBBI",
                        "BAAAAAAAB",
                        "BAAAAAAAB",
                        "BAAAAAAAB",
                        "BAAAMAAAB",
                        "BAAAAAAAB",
                        "BAAAAAAAB",
                        "BAAAAAAAB",
                        "IBBBBBBBI")
                .aisle(
                        "IBBBBBBBI",
                        "BAAAAAAAB",
                        "BAAAMAAAB",
                        "BAAAAAAAB",
                        "BAMAMAMAB",
                        "BAAAAAAAB",
                        "BAAAMAAAB",
                        "BAAAAAAAB",
                        "IBBBBBBBI")
                .aisle(
                        "IIIIIIIII",
                        "IIIIIIIII",
                        "IIIIIIIII",
                        "IIIIIIIII",
                        "IIIIIIIII",
                        "IIIIIIIII",
                        "IIIIIIIII",
                        "IIIIIIIII",
                        "IIIIIIIII")
                .aisle(
                        "         ",
                        "         ",
                        "  IIIII  ",
                        "  IIIII  ",
                        "  IIIII  ",
                        "  IIIII  ",
                        "  IIIII  ",
                        "         ",
                        "         ")
                .aisle(
                        "         ",
                        "         ",
                        "  IIIII  ",
                        "  IIIII  ",
                        "  IIIII  ",
                        "  IIIII  ",
                        "  IIIII  ",
                        "         ",
                        "         ")
                .where('I', Predicates.blocks(industrialSteamCasing()))
                .where('B', thermalCentrifugeCandidates())
                .where('F', Predicates.blocks(bronzeFireboxCasing()))
                .where('M', Predicates.blocks(GSEBlocks.STEAM_MIXING_BLOCK.get()))
                .where('A', Predicates.air())
                .where('C', Predicates.controller(Predicates.blocks(definition.getBlock())))
                .build();
    }

    /**
     * Thermal centrifuge representative layout
     * (large-steam-thermal-centrifuge.md 结构): minimum 4-hatch set on the
     * layer-2 front wall (import bus, export bus, supply hatch, exhaust
     * hatch). Axis convention as in {@code GSECrusherPatterns#smallShapeInfo}
     * (layers bottom -> top, each layer's rows south -> north, chars west ->
     * east); the crown layers are 5×5 centred in the 9×9 footprint with
     * spaces baking as air.
     */
    public static MultiblockShapeInfo thermalCentrifugeShapeInfo(MultiblockMachineDefinition definition) {
        String[][] layers = {
                {"WWWWWWWWW", "WFFFFFFFW", "WFFFFFFFW", "WFFFFFFFW", "WFFFFFFFW",
                        "WFFFFFFFW", "WFFFFFFFW", "WFFFFFFFW", "WWWWWKWWW"},
                {"WBBBBBBBW", "BAAAAAAAB", "BAAAMAAAB", "BAAAAAAAB", "BAMAMAMAB",
                        "BAAAAAAAB", "BAAAMAAAB", "BAAAAAAAB", "WBIOSEBBW"},
                {"WBBBBBBBW", "BAAAAAAAB", "BAAAAAAAB", "BAAAAAAAB", "BAAAMAAAB",
                        "BAAAAAAAB", "BAAAAAAAB", "BAAAAAAAB", "WBBBBBBBW"},
                {"WBBBBBBBW", "BAAAAAAAB", "BAAAMAAAB", "BAAAAAAAB", "BAMAMAMAB",
                        "BAAAAAAAB", "BAAAMAAAB", "BAAAAAAAB", "WBBBBBBBW"},
                {"WWWWWWWWW", "WWWWWWWWW", "WWWWWWWWW", "WWWWWWWWW", "WWWWWWWWW",
                        "WWWWWWWWW", "WWWWWWWWW", "WWWWWWWWW", "WWWWWWWWW"},
                {"         ", "         ", "  WWWWW  ", "  WWWWW  ", "  WWWWW  ",
                        "  WWWWW  ", "  WWWWW  ", "         ", "         "},
                {"         ", "         ", "  WWWWW  ", "  WWWWW  ", "  WWWWW  ",
                        "  WWWWW  ", "  WWWWW  ", "         ", "         "},
        };
        return buildShapeInfo(layers)
                .where('W', industrialSteamCasing())
                .where('B', bronzeSteamCasing())
                .where('F', bronzeFireboxCasing())
                .where('M', GSEBlocks.STEAM_MIXING_BLOCK.get())
                .where('A', Blocks.AIR)
                .where('K', definition, Direction.NORTH)
                .where('I', GTMachines.STEAM_IMPORT_BUS, Direction.NORTH)
                .where('O', GTMachines.STEAM_EXPORT_BUS, Direction.NORTH)
                .where('S', GSEMachines.STEAM_SUPPLY_HATCH, Direction.NORTH)
                .where('E', GSEMachines.STEAM_EXHAUST_HATCH, Direction.NORTH)
                .build();
    }

    /**
     * The macerator's candidate rule for the 97 shell steam-machine-casing
     * positions (large-steam-macerator.md 议题 4): the spherical shell with
     * hatches as replacements. Pure-dry recipe type — NO fluid hatch ability
     * of any kind is admitted (议题 4 仓室表 流体仓 0), so the mod's steam
     * fluid hatches cannot appear either. The 85-casing minimum bounds the
     * hatch total (incl. the exhaust hatch) at 12 (97 − 85, 议题 4 仓室合计
     * 上限); the exhaust hatch takes one candidate slot and is post-checked
     * to exactly one by the controller.
     */
    private static TraceabilityPredicate maceratorCandidates() {
        return Predicates.blocks(bronzeSteamCasing()).setMinGlobalLimited(85)
                .or(Predicates.abilities(PartAbility.STEAM_IMPORT_ITEMS))
                .or(Predicates.abilities(PartAbility.IMPORT_ITEMS))
                .or(Predicates.abilities(PartAbility.STEAM_EXPORT_ITEMS))
                .or(Predicates.abilities(PartAbility.EXPORT_ITEMS))
                .or(Predicates.abilities(PartAbility.STEAM))
                .or(Predicates.blocks(GSEMachines.STEAM_EXHAUST_HATCH.getBlock()).setExactLimit(1));
    }

    /**
     * 大型蒸汽研磨厂: fixed spherical structure in a 7×7×7 bounding box
     * (large-steam-macerator.md 议题 4 逐层图). Aisle rows run back ->
     * front, so the design's front row is the LAST string of each aisle.
     * Aisles stack bottom-up over the design's layers 7..1 (design numbers
     * top-down). Shell: 98 positions = 97 bronze steam machine casings + the
     * front-equator-centre controller. Interior: 13 Steam Grinding Blocks
     * (centre + both blocks along ±x/±y/±z, arm tips touching the inner
     * shell) and 68 blocks of STRICT air (Predicates.air). Don't-care
     * positions outside the sphere are spaces (Predicates.any()).
     */
    public static BlockPattern createMacerator(MultiblockMachineDefinition definition) {
        return FactoryBlockPattern.start(RelativeDirection.LEFT, RelativeDirection.FRONT, RelativeDirection.UP)
                .aisle( // layer 7 (bottom crown, dy = -3)
                        "       ",
                        "       ",
                        "  BBB  ",
                        "  BBB  ",
                        "  BBB  ",
                        "       ",
                        "       ")
                .aisle( // layer 6 (dy = -2)
                        "       ",
                        " BBBBB ",
                        " BAAAB ",
                        " BAMAB ",
                        " BAAAB ",
                        " BBBBB ",
                        "       ")
                .aisle( // layer 5 (dy = -1)
                        "  BBB  ",
                        " BAAAB ",
                        "BAAAAAB",
                        "BAAMAAB",
                        "BAAAAAB",
                        " BAAAB ",
                        "  BBB  ")
                .aisle( // layer 4 (equator, dy = 0; controller front-centre)
                        "  BBB  ",
                        " BAMAB ",
                        "BAAMAAB",
                        "BMMMMMB",
                        "BAAMAAB",
                        " BAMAB ",
                        "  BCB  ")
                .aisle( // layer 3 (dy = +1)
                        "  BBB  ",
                        " BAAAB ",
                        "BAAAAAB",
                        "BAAMAAB",
                        "BAAAAAB",
                        " BAAAB ",
                        "  BBB  ")
                .aisle( // layer 2 (dy = +2)
                        "       ",
                        " BBBBB ",
                        " BAAAB ",
                        " BAMAB ",
                        " BAAAB ",
                        " BBBBB ",
                        "       ")
                .aisle( // layer 1 (top crown, dy = +3)
                        "       ",
                        "       ",
                        "  BBB  ",
                        "  BBB  ",
                        "  BBB  ",
                        "       ",
                        "       ")
                .where('B', maceratorCandidates())
                .where('M', Predicates.blocks(GSEBlocks.STEAM_GRINDING_BLOCK.get()))
                .where('A', Predicates.air())
                .where('C', Predicates.controller(Predicates.blocks(definition.getBlock())))
                .build();
    }

    /**
     * Macerator representative layout (large-steam-macerator.md 结构):
     * minimum 4-hatch set (import bus and export bus flanking the equator
     * controller, supply and exhaust hatch on the layer-6 front wall). Axis
     * convention as in {@code GSECrusherPatterns#smallShapeInfo} (layers
     * bottom -> top, each layer's rows back -> front, chars west -> east);
     * spaces outside the sphere bake as air.
     */
    public static MultiblockShapeInfo maceratorShapeInfo(MultiblockMachineDefinition definition) {
        String[][] layers = {
                {"       ", "       ", "  BBB  ", "  BBB  ", "  BBB  ", "       ", "       "},
                {"       ", " BBBBB ", " BAAAB ", " BAMAB ", " BAAAB ", " BESBB ", "       "},
                {"  BBB  ", " BAAAB ", "BAAAAAB", "BAAMAAB", "BAAAAAB", " BAAAB ", "  BBB  "},
                {"  BBB  ", " BAMAB ", "BAAMAAB", "BMMMMMB", "BAAMAAB", " BAMAB ", "  ICO  "},
                {"  BBB  ", " BAAAB ", "BAAAAAB", "BAAMAAB", "BAAAAAB", " BAAAB ", "  BBB  "},
                {"       ", " BBBBB ", " BAAAB ", " BAMAB ", " BAAAB ", " BBBBB ", "       "},
                {"       ", "       ", "  BBB  ", "  BBB  ", "  BBB  ", "       ", "       "},
        };
        return buildShapeInfo(layers)
                .where('B', bronzeSteamCasing())
                .where('M', GSEBlocks.STEAM_GRINDING_BLOCK.get())
                .where('A', Blocks.AIR)
                .where('K', definition, Direction.NORTH)
                .where('I', GTMachines.STEAM_IMPORT_BUS, Direction.NORTH)
                .where('O', GTMachines.STEAM_EXPORT_BUS, Direction.NORTH)
                .where('S', GSEMachines.STEAM_SUPPLY_HATCH, Direction.NORTH)
                .where('E', GSEMachines.STEAM_EXHAUST_HATCH, Direction.NORTH)
                .build();
    }

    /**
     * The mixer's candidate rule for the 48 wall steam-machine-casing
     * positions (large-steam-mixer.md 议题 4): the widest fluid interface of
     * the family — BOTH families of fluid input AND output hatches are
     * admissible (议题 4 仓室表: GTCEu 标准仓或本模组蒸汽流体仓, 可选或混用,
     * B4/C0 口径). The 32-casing minimum bounds the hatch total (incl. the
     * exhaust hatch) at 16 (48 − 32, 议题 4 仓室合计上限); the exhaust hatch
     * takes one candidate slot and is post-checked to exactly one by the
     * controller.
     */
    private static TraceabilityPredicate mixerCandidates() {
        return Predicates.blocks(bronzeSteamCasing()).setMinGlobalLimited(32)
                .or(Predicates.abilities(PartAbility.STEAM_IMPORT_ITEMS))
                .or(Predicates.abilities(PartAbility.IMPORT_ITEMS))
                .or(Predicates.abilities(PartAbility.STEAM_EXPORT_ITEMS))
                .or(Predicates.abilities(PartAbility.EXPORT_ITEMS))
                .or(Predicates.abilities(PartAbility.STEAM))
                .or(Predicates.abilities(PartAbility.IMPORT_FLUIDS))
                .or(Predicates.abilities(PartAbility.EXPORT_FLUIDS))
                .or(Predicates.abilities(GSEPartAbilities.STEAM_IMPORT_FLUIDS))
                .or(Predicates.abilities(GSEPartAbilities.STEAM_EXPORT_FLUIDS))
                .or(Predicates.blocks(GSEMachines.STEAM_EXHAUST_HATCH.getBlock()).setExactLimit(1));
    }

    /**
     * 大型蒸汽搅拌机: fixed 7×5×5 (large-steam-mixer.md 议题 4 逐层图). Aisle
     * rows run back -> front, so the design's front row is the LAST string of
     * each aisle. Bottom and top layers are full industrial casings (the
     * controller sits front-bottom-centre inside the bottom industrial band);
     * the three middle layers carry industrial vertical-edge columns and
     * steam-machine-casing walls (16 per layer, hatch-replaceable) around the
     * 5×3×3 interior with the mixing cross: axis column layers 2-4 centres +
     * the equidistant impeller cross on layer 3 (arms at x=3/5 and z=2/4, all
     * four adjacent to the hub). Interior air is STRICT air (Predicates.air).
     */
    public static BlockPattern createMixer(MultiblockMachineDefinition definition) {
        return FactoryBlockPattern.start(RelativeDirection.LEFT, RelativeDirection.FRONT, RelativeDirection.UP)
                .aisle( // layer 1 (bottom band, controller front-centre)
                        "IIIIIII",
                        "IIIIIII",
                        "IIIIIII",
                        "IIIIIII",
                        "IIICIII")
                .aisle( // layer 2 (axis root)
                        "IBBBBBI",
                        "BAAAAB",
                        "BAAMAAB",
                        "BAAAAB",
                        "IBBBBBI")
                .aisle( // layer 3 (impeller cross)
                        "IBBBBBI",
                        "BAAMAAB",
                        "BAMMMAB",
                        "BAAMAAB",
                        "IBBBBBI")
                .aisle( // layer 4 (axis top)
                        "IBBBBBI",
                        "BAAAAB",
                        "BAAMAAB",
                        "BAAAAB",
                        "IBBBBBI")
                .aisle( // layer 5 (top band)
                        "IIIIIII",
                        "IIIIIII",
                        "IIIIIII",
                        "IIIIIII",
                        "IIIIIII")
                .where('I', Predicates.blocks(industrialSteamCasing()))
                .where('B', mixerCandidates())
                .where('M', Predicates.blocks(GSEBlocks.STEAM_MIXING_BLOCK.get()))
                .where('A', Predicates.air())
                .where('C', Predicates.controller(Predicates.blocks(definition.getBlock())))
                .build();
    }

    /**
     * Mixer representative layout (large-steam-mixer.md 结构): minimum
     * 6-hatch set on the wall layers (import bus, export bus, steam supply
     * hatch, exhaust hatch and a standard fluid import hatch on the layer-2
     * front wall; a standard fluid export hatch on the layer-4 front wall —
     * both fluid hatch families are admissible per 议题 4). Axis convention
     * as in {@code GSECrusherPatterns#smallShapeInfo} (layers bottom -> top,
     * each layer's rows back -> front, chars west -> east).
     */
    public static MultiblockShapeInfo mixerShapeInfo(MultiblockMachineDefinition definition) {
        String[][] layers = {
                {"IIIIIII", "IIIIIII", "IIIIIII", "IIIIIII", "IIICIII"},
                {"IBBBBBI", "BAAAAB", "BAAMAAB", "BAAAAB", "IJOSEFI"},
                {"IBBBBBI", "BAAMAAB", "BAMMMAB", "BAAMAAB", "IBBBBBI"},
                {"IBBBBBI", "BAAAAB", "BAAMAAB", "BAAAAB", "IGBBBBI"},
                {"IIIIIII", "IIIIIII", "IIIIIII", "IIIIIII", "IIIIIII"},
        };
        return buildShapeInfo(layers)
                .where('I', industrialSteamCasing())
                .where('B', bronzeSteamCasing())
                .where('M', GSEBlocks.STEAM_MIXING_BLOCK.get())
                .where('A', Blocks.AIR)
                .where('K', definition, Direction.NORTH)
                .where('J', GTMachines.STEAM_IMPORT_BUS, Direction.NORTH)
                .where('O', GTMachines.STEAM_EXPORT_BUS, Direction.NORTH)
                .where('S', GSEMachines.STEAM_SUPPLY_HATCH, Direction.NORTH)
                .where('F', GTMachines.FLUID_IMPORT_HATCH[1], Direction.NORTH)
                .where('G', GTMachines.FLUID_EXPORT_HATCH[1], Direction.NORTH)
                .where('E', GSEMachines.STEAM_EXHAUST_HATCH, Direction.NORTH)
                .build();
    }

    /**
     * The chemical bath's candidate rule for the 33 shell industrial-casing
     * positions (large-steam-chemical-bath.md 议题 4): industrial steam
     * machine casing with hatches as replacements. Fluid interface per
     * 仓室表: the fluid input hatch is REQUIRED and BOTH families are
     * admissible (GTCEu standard {@code IMPORT_FLUIDS} or the mod's
     * {@code STEAM_IMPORT_FLUIDS}, 可选或混用); NO fluid output hatch of
     * either family and NO steam exhaust hatch is admissible at all (议题
     * 3 连带后果: 本机不安装流体输出仓; 2026-09-07 全模组裁定: 非大型蒸汽
     * 多方块不使用排气仓). The 25-casing minimum bounds the hatch total at
     * 8 (33 − 25, 议题 4 仓室合计上限).
     */
    private static TraceabilityPredicate chemicalBathCandidates() {
        return Predicates.blocks(industrialSteamCasing()).setMinGlobalLimited(25)
                .or(Predicates.abilities(PartAbility.STEAM_IMPORT_ITEMS))
                .or(Predicates.abilities(PartAbility.STEAM_EXPORT_ITEMS))
                .or(Predicates.abilities(PartAbility.STEAM))
                .or(Predicates.abilities(PartAbility.IMPORT_FLUIDS))
                .or(Predicates.abilities(GSEPartAbilities.STEAM_IMPORT_FLUIDS));
    }

    /**
     * 蒸汽化学浸洗厂: fixed 3×4×3 (large-steam-chemical-bath.md 议题 4 逐层
     * 图). Aisle rows run back -> front, so the design's front row is the
     * LAST string of each aisle. All three layers are full industrial
     * casings except the two strict-air cells (middle column × middle two
     * rows of the middle layer — the immersion chamber); the controller
     * sits front-bottom-centre. Aisles here are 4 rows deep × 3 chars wide,
     * 3 aisles tall.
     */
    public static BlockPattern createChemicalBath(MultiblockMachineDefinition definition) {
        return FactoryBlockPattern.start(RelativeDirection.LEFT, RelativeDirection.FRONT, RelativeDirection.UP)
                .aisle("III", "III", "III", "ICI")
                .aisle("III", "IAI", "IAI", "III")
                .aisle("III", "III", "III", "III")
                .where('I', chemicalBathCandidates())
                .where('A', Predicates.air())
                .where('C', Predicates.controller(Predicates.blocks(definition.getBlock())))
                .build();
    }

    /**
     * Chemical bath representative layout
     * (large-steam-chemical-bath.md 结构): minimum 4-hatch set — import and
     * export buses flanking the bottom-front-centre controller, steam supply
     * hatch and a standard fluid input hatch on the middle-layer front wall.
     * Axis convention as in {@code GSECrusherPatterns#smallShapeInfo}
     * (layers bottom -> top, each layer's 4 rows back -> front, chars west
     * -> east).
     */
    public static MultiblockShapeInfo chemicalBathShapeInfo(MultiblockMachineDefinition definition) {
        String[][] layers = {
                {"WWW", "WWW", "WWW", "IKO"},
                {"WWW", "WAW", "WAW", "WSF"},
                {"WWW", "WWW", "WWW", "WWW"},
        };
        return buildShapeInfo(layers)
                .where('W', industrialSteamCasing())
                .where('A', Blocks.AIR)
                .where('K', definition, Direction.NORTH)
                .where('I', GTMachines.STEAM_IMPORT_BUS, Direction.NORTH)
                .where('O', GTMachines.STEAM_EXPORT_BUS, Direction.NORTH)
                .where('S', GSEMachines.STEAM_SUPPLY_HATCH, Direction.NORTH)
                .where('F', GTMachines.FLUID_IMPORT_HATCH[1], Direction.NORTH)
                .build();
    }

    /**
     * The small centrifuge's candidate rule for the 33 shell bronze-casing
     * positions (steam-centrifuges.md 议题 4): steam machine casing with
     * hatches as replacements. Fluid interface per 议题 3: fluid input AND
     * output hatches are required and BOTH families are admissible (GTCEu
     * standard hatches or the mod's steam fluid hatches, 可选或混用 — the
     * steam fluid output hatch gains its first legal consumer here). The
     * small machine uses NO exhaust hatch (2026-09-07 全模组裁定), so no
     * exhaust ability is admitted. The 25-casing minimum bounds the hatch
     * total at 8 (33 − 25, 议题 4 仓室合计上限).
     */
    private static TraceabilityPredicate centrifugeCandidates() {
        return Predicates.blocks(bronzeSteamCasing()).setMinGlobalLimited(25)
                .or(Predicates.abilities(PartAbility.STEAM_IMPORT_ITEMS))
                .or(Predicates.abilities(PartAbility.STEAM_EXPORT_ITEMS))
                .or(Predicates.abilities(PartAbility.STEAM))
                .or(Predicates.abilities(PartAbility.IMPORT_FLUIDS))
                .or(Predicates.abilities(PartAbility.EXPORT_FLUIDS))
                .or(Predicates.abilities(GSEPartAbilities.STEAM_IMPORT_FLUIDS))
                .or(Predicates.abilities(GSEPartAbilities.STEAM_EXPORT_FLUIDS));
    }

    /**
     * 蒸汽离心机: fixed 3×4×3 (steam-centrifuges.md 议题 4 逐层图). Aisle
     * rows run back -> front, so the design's front row is the LAST string
     * of each aisle. The two interior middle-layer cells (centre column ×
     * middle two rows) are Steam Mixing Blocks — the rotor pair fills the
     * interior completely (用户 2026-09-07 补充: 加至 2 个, 填满内部, 无
     * 空气腔); the controller sits front-bottom-centre.
     */
    public static BlockPattern createCentrifuge(MultiblockMachineDefinition definition) {
        return FactoryBlockPattern.start(RelativeDirection.LEFT, RelativeDirection.FRONT, RelativeDirection.UP)
                .aisle("BBB", "BBB", "BBB", "BCB")
                .aisle("BBB", "BMB", "BMB", "BBB")
                .aisle("BBB", "BBB", "BBB", "BBB")
                .where('B', centrifugeCandidates())
                .where('M', Predicates.blocks(GSEBlocks.STEAM_MIXING_BLOCK.get()))
                .where('C', Predicates.controller(Predicates.blocks(definition.getBlock())))
                .build();
    }

    /**
     * Small centrifuge representative layout (steam-centrifuges.md 结构):
     * minimum 5-hatch set — import and export buses flanking the
     * bottom-front-centre controller, steam supply and a standard fluid
     * import hatch on the middle-layer front wall, a standard fluid export
     * hatch on the top-layer front wall. Axis convention as in
     * {@code GSECrusherPatterns#smallShapeInfo} (layers bottom -> top, each
     * layer's 4 rows back -> front, chars west -> east).
     */
    public static MultiblockShapeInfo centrifugeShapeInfo(MultiblockMachineDefinition definition) {
        String[][] layers = {
                {"BBB", "BBB", "BBB", "JKO"},
                {"BBB", "BMB", "BMB", "BSF"},
                {"BBB", "BBB", "BBB", "BGB"},
        };
        return buildShapeInfo(layers)
                .where('B', bronzeSteamCasing())
                .where('M', GSEBlocks.STEAM_MIXING_BLOCK.get())
                .where('K', definition, Direction.NORTH)
                .where('J', GTMachines.STEAM_IMPORT_BUS, Direction.NORTH)
                .where('O', GTMachines.STEAM_EXPORT_BUS, Direction.NORTH)
                .where('S', GSEMachines.STEAM_SUPPLY_HATCH, Direction.NORTH)
                .where('F', GTMachines.FLUID_IMPORT_HATCH[1], Direction.NORTH)
                .where('G', GTMachines.FLUID_EXPORT_HATCH[1], Direction.NORTH)
                .build();
    }

    /**
     * The large centrifuge's candidate rule for the 112 side-ring positions
     * (steam-centrifuges.md 议题 4): steam machine casing with hatches as
     * replacements, the same fluid interface as the small machine (both
     * families of fluid input AND output hatches admissible, 可选或混用).
     * The exhaust hatch takes one candidate slot (大型机必须且只能 1 个,
     * post-checked by the controller) and the 100-casing minimum bounds the
     * hatch total at 12 (112 − 100, 议题 4 仓室合计上限).
     */
    private static TraceabilityPredicate largeCentrifugeCandidates() {
        return Predicates.blocks(bronzeSteamCasing()).setMinGlobalLimited(100)
                .or(Predicates.abilities(PartAbility.STEAM_IMPORT_ITEMS))
                .or(Predicates.abilities(PartAbility.IMPORT_ITEMS))
                .or(Predicates.abilities(PartAbility.STEAM_EXPORT_ITEMS))
                .or(Predicates.abilities(PartAbility.EXPORT_ITEMS))
                .or(Predicates.abilities(PartAbility.STEAM))
                .or(Predicates.abilities(PartAbility.IMPORT_FLUIDS))
                .or(Predicates.abilities(PartAbility.EXPORT_FLUIDS))
                .or(Predicates.abilities(GSEPartAbilities.STEAM_IMPORT_FLUIDS))
                .or(Predicates.abilities(GSEPartAbilities.STEAM_EXPORT_FLUIDS))
                .or(Predicates.blocks(GSEMachines.STEAM_EXHAUST_HATCH.getBlock()).setExactLimit(1));
    }

    /**
     * 大型蒸汽离心机: fixed 7×7×9 vertical separation tower
     * (steam-centrifuges.md 议题 4 逐层图). Aisle rows run back -> front,
     * so the design's front row is the LAST string of each aisle. Layers 1
     * and 9 are full 37-block disc crowns (d² ≤ 12.25 from the centre, A4
     * disc convention); layer 1 carries the front-centre controller. Layers
     * 2-8 are 16-cell side rings around a 21-cell interior carrying the
     * Steam Mixing Block axis (centre) and two bronze pipe casing columns
     * (x=3/5, z=4); interior air is STRICT air (Predicates.air).
     */
    public static BlockPattern createLargeCentrifuge(MultiblockMachineDefinition definition) {
        return FactoryBlockPattern.start(RelativeDirection.LEFT, RelativeDirection.FRONT, RelativeDirection.UP)
                .aisle( // layer 1 (bottom crown, controller front-centre)
                        "..BBB..",
                        ".BBBBB.",
                        "BBBBBBB",
                        "BBBBBBB",
                        "BBBBBBB",
                        ".BBBBB.",
                        "..BCB..")
                .aisle( // layers 2-8 (ring + axis + pipe columns), 7 identical aisles
                        "..BBB..",
                        ".BAAAB.",
                        "BAAAAAB",
                        "BAPMPAB",
                        "BAAAAAB",
                        ".BAAAB.",
                        "..BBB..")
                .aisle(
                        "..BBB..",
                        ".BAAAB.",
                        "BAAAAAB",
                        "BAPMPAB",
                        "BAAAAAB",
                        ".BAAAB.",
                        "..BBB..")
                .aisle(
                        "..BBB..",
                        ".BAAAB.",
                        "BAAAAAB",
                        "BAPMPAB",
                        "BAAAAAB",
                        ".BAAAB.",
                        "..BBB..")
                .aisle(
                        "..BBB..",
                        ".BAAAB.",
                        "BAAAAAB",
                        "BAPMPAB",
                        "BAAAAAB",
                        ".BAAAB.",
                        "..BBB..")
                .aisle(
                        "..BBB..",
                        ".BAAAB.",
                        "BAAAAAB",
                        "BAPMPAB",
                        "BAAAAAB",
                        ".BAAAB.",
                        "..BBB..")
                .aisle(
                        "..BBB..",
                        ".BAAAB.",
                        "BAAAAAB",
                        "BAPMPAB",
                        "BAAAAAB",
                        ".BAAAB.",
                        "..BBB..")
                .aisle(
                        "..BBB..",
                        ".BAAAB.",
                        "BAAAAAB",
                        "BAPMPAB",
                        "BAAAAAB",
                        ".BAAAB.",
                        "..BBB..")
                .aisle( // layer 9 (top crown)
                        "..BBB..",
                        ".BBBBB.",
                        "BBBBBBB",
                        "BBBBBBB",
                        "BBBBBBB",
                        ".BBBBB.",
                        "..BBB..")
                .where('B', largeCentrifugeCandidates())
                .where('M', Predicates.blocks(GSEBlocks.STEAM_MIXING_BLOCK.get()))
                .where('P', Predicates.blocks(bronzePipeCasing()))
                .where('A', Predicates.air())
                .where('C', Predicates.controller(Predicates.blocks(definition.getBlock())))
                .build();
    }

    /**
     * Large centrifuge representative layout (steam-centrifuges.md 结构):
     * minimum 6-hatch set on the front arc — import and export buses with
     * the exhaust hatch between them on the layer-2 front row, steam supply
     * and both standard fluid hatches on the layer-3 front row. Axis
     * convention as in {@code GSECrusherPatterns#smallShapeInfo} (layers
     * bottom -> top, each layer's 7 rows back -> front, chars west -> east).
     */
    public static MultiblockShapeInfo largeCentrifugeShapeInfo(MultiblockMachineDefinition definition) {
        String[] middle = {
                "..BBB..",
                ".BAAAB.",
                "BAAAAAB",
                "BAPMPAB",
                "BAAAAAB",
                ".BAAAB.",
                "..BBB..",
        };
        String[] crown = {
                "..BBB..",
                ".BBBBB.",
                "BBBBBBB",
                "BBBBBBB",
                "BBBBBBB",
                ".BBBBB.",
                "..BBB..",
        };
        String[] crownWithController = {
                "..BBB..",
                ".BBBBB.",
                "BBBBBBB",
                "BBBBBBB",
                "BBBBBBB",
                ".BBBBB.",
                "..BKB..",
        };
        String[] ringWithBuses = {
                "..BBB..",
                ".BAAAB.",
                "BAAAAAB",
                "BAPMPAB",
                "BAAAAAB",
                ".BAAAB.",
                "..JOE..",
        };
        String[] ringWithFluid = {
                "..BBB..",
                ".BAAAB.",
                "BAAAAAB",
                "BAPMPAB",
                "BAAAAAB",
                ".BAAAB.",
                "..SFG..",
        };
        String[][] layers = {
                crownWithController,
                ringWithBuses,
                ringWithFluid,
                middle, middle, middle, middle, middle,
                crown,
        };
        return buildShapeInfo(layers)
                .where('B', bronzeSteamCasing())
                .where('M', GSEBlocks.STEAM_MIXING_BLOCK.get())
                .where('P', bronzePipeCasing())
                .where('A', Blocks.AIR)
                .where('K', definition, Direction.NORTH)
                .where('J', GTMachines.STEAM_IMPORT_BUS, Direction.NORTH)
                .where('O', GTMachines.STEAM_EXPORT_BUS, Direction.NORTH)
                .where('S', GSEMachines.STEAM_SUPPLY_HATCH, Direction.NORTH)
                .where('F', GTMachines.FLUID_IMPORT_HATCH[1], Direction.NORTH)
                .where('G', GTMachines.FLUID_EXPORT_HATCH[1], Direction.NORTH)
                .where('E', GSEMachines.STEAM_EXHAUST_HATCH, Direction.NORTH)
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
