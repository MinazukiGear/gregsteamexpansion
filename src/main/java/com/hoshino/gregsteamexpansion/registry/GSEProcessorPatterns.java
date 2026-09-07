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
     * 蒸汽提取机: fixed 3×3×3 (steam-extractor.md 议题 4 逐层图) — bottom layer
     * carries the front-centre controller, the structure's single inner cell
     * (centre of the middle layer) is one bronze pipe casing, no air gap.
     */
    public static BlockPattern createExtractor(MultiblockMachineDefinition definition) {
        return FactoryBlockPattern.start(RelativeDirection.LEFT, RelativeDirection.FRONT, RelativeDirection.UP)
                .aisle("BBB", "BBB", "BCB")
                .aisle("BBB", "BPB", "BBB")
                .aisle("BBB", "BBB", "BBB")
                .where('B', shellCandidates())
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
