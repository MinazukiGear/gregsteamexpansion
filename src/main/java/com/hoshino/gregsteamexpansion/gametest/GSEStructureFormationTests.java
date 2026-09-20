package com.hoshino.gregsteamexpansion.gametest;

import com.hoshino.gregsteamexpansion.GregSteamExpansion;
import com.hoshino.gregsteamexpansion.machine.multiblock.processor.BlastFurnaceHotBlastModule;
import com.hoshino.gregsteamexpansion.registry.GSEBlocks;
import com.hoshino.gregsteamexpansion.registry.GSEMachines;
import com.hoshino.gregsteamexpansion.registry.GSEProcessorPatterns;
import com.hoshino.gregsteamexpansion.registry.GSECrusherPatterns;

import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.api.pattern.MultiblockShapeInfo;
import com.gregtechceu.gtceu.common.data.GTBlocks;
import com.gregtechceu.gtceu.common.data.GTMachines;
import com.lowdragmc.lowdraglib.utils.BlockInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@GameTestHolder(GregSteamExpansion.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GSEStructureFormationTests {
    private GSEStructureFormationTests() {}

    //////////////////////////////////////
    // *** 结构成型（照 shapeInfo 铺，验证图案本身）***//
    //////////////////////////////////////

    @GameTest(template = "empty_32x32x32", timeoutTicks = 200)
    public static void steamChemicalBathFormsFromShape(GameTestHelper helper) {
        GSEStructureTestUtils.assertFirstShapeForms(helper, GSEMachines.STEAM_CHEMICAL_BATH);
    }

    @GameTest(template = "empty_32x32x32", timeoutTicks = 200)
    public static void steamCrusherFormsFromShape(GameTestHelper helper) {
        GSEStructureTestUtils.assertFirstShapeReplacementMatches(
                helper,
                GSEMachines.STEAM_CRUSHER,
                GSEMachines.STEAM_SUPPLY_HATCH.getBlock(),
                GSEMachines.LARGE_STEAM_SUPPLY_HATCH,
                false);
    }

    @GameTest(template = "empty_32x32x32", timeoutTicks = 200)
    public static void largeSteamCrusherFormsFromShape(GameTestHelper helper) {
        GSEStructureTestUtils.assertFirstShapeReplacementMatches(
                helper,
                GSEMachines.LARGE_STEAM_CRUSHER,
                GSEMachines.STEAM_SUPPLY_HATCH.getBlock(),
                GSEMachines.LARGE_STEAM_SUPPLY_HATCH,
                true);
    }

    @GameTest(template = "empty_32x32x32", timeoutTicks = 200)
    public static void largeSteamCrusherAcceptsAdvancedExhaustHatch(GameTestHelper helper) {
        GSEStructureTestUtils.assertFirstShapeReplacementMatches(
                helper,
                GSEMachines.LARGE_STEAM_CRUSHER,
                GSEMachines.STEAM_EXHAUST_HATCH.getBlock(),
                GSEMachines.ADVANCED_STEAM_EXHAUST_HATCH,
                true);
    }

    @GameTest(template = "empty_32x32x32", timeoutTicks = 200)
    public static void smallSteamCrusherRejectsAdvancedExhaustHatch(GameTestHelper helper) {
        GSEStructureTestUtils.assertFirstShapeReplacementMatches(
                helper,
                GSEMachines.STEAM_CRUSHER,
                GSECrusherPatterns.bronzeSteamCasing(),
                GSEMachines.ADVANCED_STEAM_EXHAUST_HATCH,
                false);
    }

    @GameTest(template = "empty_32x32x32", timeoutTicks = 200)
    public static void steamCompressorFormsFromShape(GameTestHelper helper) {
        GSEStructureTestUtils.assertFirstShapeForms(helper, GSEMachines.STEAM_COMPRESSOR);
    }

    @GameTest(template = "empty_32x32x32", timeoutTicks = 200)
    public static void steamExtractorFormsFromShape(GameTestHelper helper) {
        GSEStructureTestUtils.assertFirstShapeForms(helper, GSEMachines.STEAM_EXTRACTOR);
    }

    @GameTest(template = "empty_32x32x32", timeoutTicks = 200)
    public static void steamForgeFormsFromShape(GameTestHelper helper) {
        GSEStructureTestUtils.assertFirstShapeForms(helper, GSEMachines.STEAM_FORGE);
    }

    @GameTest(template = "empty_32x32x32", timeoutTicks = 200)
    public static void largeSteamOreWasherFormsFromShape(GameTestHelper helper) {
        GSEStructureTestUtils.assertFirstShapeForms(helper, GSEMachines.LARGE_STEAM_ORE_WASHER);
    }

    @GameTest(template = "empty_32x32x32", timeoutTicks = 200)
    public static void largeSteamThermalCentrifugeFormsFromShape(GameTestHelper helper) {
        GSEStructureTestUtils.assertFirstShapeForms(helper, GSEMachines.LARGE_STEAM_THERMAL_CENTRIFUGE);
    }

    @GameTest(template = "empty_32x32x32", timeoutTicks = 200)
    public static void largeSteamMaceratorFormsFromShape(GameTestHelper helper) {
        GSEStructureTestUtils.assertFirstShapeForms(helper, GSEMachines.LARGE_STEAM_MACERATOR);
    }

    @GameTest(template = "empty_32x32x32", timeoutTicks = 200)
    public static void largeSteamMixerFormsFromShape(GameTestHelper helper) {
        GSEStructureTestUtils.assertFirstShapeForms(helper, GSEMachines.LARGE_STEAM_MIXER);
    }

    @GameTest(template = "empty_32x32x32", timeoutTicks = 200)
    public static void steamCentrifugeFormsFromShape(GameTestHelper helper) {
        GSEStructureTestUtils.assertFirstShapeForms(helper, GSEMachines.STEAM_CENTRIFUGE);
    }

    @GameTest(template = "empty_32x32x32", timeoutTicks = 200)
    public static void largeSteamCentrifugeFormsFromShape(GameTestHelper helper) {
        GSEStructureTestUtils.assertFirstShapeForms(helper, GSEMachines.LARGE_STEAM_CENTRIFUGE);
    }

    @GameTest(template = "empty_32x32x32", timeoutTicks = 200)
    public static void largeSteamBlastFurnaceFormsFromShape(GameTestHelper helper) {
        GSEStructureTestUtils.assertFirstShapeForms(helper, GSEMachines.LARGE_STEAM_BLAST_FURNACE);
    }

    @GameTest(template = "empty_32x32x32", timeoutTicks = 300)
    public static void largeSteamBlastFurnaceHotBlastPreviewMatchesModule(GameTestHelper helper) {
        var definition = GSEMachines.LARGE_STEAM_BLAST_FURNACE;
        helper.assertTrue(definition.getMatchingShapes().size() == 2,
                "Blast furnace must expose base and hot-blast previews");
        MultiblockControllerMachine machine = GSEStructureTestUtils.placeShape(
                helper, definition, definition.getMatchingShapes().get(1),
                new BlockPos(16, 16, 8), Direction.NORTH);
        helper.assertTrue(machine != null, "Missing hot-blast preview controller");
        if (machine == null) {
            return;
        }
        helper.assertTrue(BlastFurnaceHotBlastModule.validate(
                        helper.getLevel(), machine.getPos(), machine.getFrontFacing())
                        == BlastFurnaceHotBlastModule.Result.VALID,
                "Combined preview does not match the runtime module validator");
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(machine.isFormed(),
                        "Combined hot-blast preview did not preserve base formation"))
                .thenSucceed();
    }

    @GameTest(template = "empty_32x32x32", timeoutTicks = 300)
    public static void largeSteamBlastFurnaceRejectsInvalidInterfaces(GameTestHelper helper) {
        var definition = GSEMachines.LARGE_STEAM_BLAST_FURNACE;
        MultiblockControllerMachine machine = GSEStructureTestUtils.placeShape(
                helper, definition, definition.getMatchingShapes().get(0));
        helper.assertTrue(machine != null, "Missing blast-furnace fixture controller");

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(machine.isFormed(),
                        "Baseline blast-furnace fixture did not form"))
                .thenExecute(() -> {
                    List<BlockPos> intakes = new java.util.ArrayList<>();
                    List<BlockPos> exhausts = new java.util.ArrayList<>();
                    List<BlockPos> bricks = new java.util.ArrayList<>();
                    for (BlockPos pos : BlockPos.betweenClosed(
                            machine.getPos().offset(-15, -15, -15),
                            machine.getPos().offset(15, 15, 15))) {
                        BlockState state = helper.getLevel().getBlockState(pos);
                        if (state.is(GSEMachines.STEAM_AIR_INTAKE_HATCH.getBlock())) {
                            intakes.add(pos.immutable());
                        } else if (state.is(GSEMachines.STEAM_EXHAUST_HATCH.getBlock())) {
                            exhausts.add(pos.immutable());
                        } else if (state.is(GSEProcessorPatterns.blastBricks())) {
                            bricks.add(pos.immutable());
                        }
                    }
                    helper.assertTrue(intakes.size() == 3,
                            "Blast-furnace preview intake count changed: " + intakes.size());
                    helper.assertTrue(exhausts.size() == 1,
                            "Blast-furnace preview exhaust count changed: " + exhausts.size());
                    helper.assertTrue(bricks.size() >= 8,
                            "Blast-furnace preview lacks mutation candidate bricks");

                    machine.onStructureInvalid();
                    Map<BlockPos, BlockState> intakeStates = new HashMap<>();
                    for (BlockPos pos : intakes) {
                        intakeStates.put(pos, helper.getLevel().getBlockState(pos));
                        helper.getLevel().setBlockAndUpdate(
                                pos, GSEProcessorPatterns.blastBricks().defaultBlockState());
                    }
                    helper.assertTrue(!machine.checkPattern(),
                            "Blast furnace formed without an air intake hatch");
                    intakeStates.forEach(helper.getLevel()::setBlockAndUpdate);
                    helper.assertTrue(machine.checkPattern(),
                            "Blast furnace did not recover after restoring its air intakes");

                    BlockState intakeState = intakeStates.get(intakes.get(0));
                    for (int i = 0; i < 5; i++) {
                        helper.getLevel().setBlockAndUpdate(bricks.get(i), intakeState);
                    }
                    helper.assertTrue(machine.checkPattern(),
                            "Blast furnace rejected the legal eight-intake maximum");
                    helper.getLevel().setBlockAndUpdate(bricks.get(5), intakeState);
                    helper.assertTrue(!machine.checkPattern(),
                            "Blast furnace formed with a ninth air intake hatch");
                    for (int i = 0; i < 6; i++) {
                        helper.getLevel().setBlockAndUpdate(
                                bricks.get(i), GSEProcessorPatterns.blastBricks().defaultBlockState());
                    }
                    helper.assertTrue(machine.checkPattern(),
                            "Blast furnace did not recover after removing excess air intakes");

                    BlockPos fluidProbe = bricks.get(6);
                    helper.getLevel().setBlockAndUpdate(
                            fluidProbe, GTMachines.FLUID_IMPORT_HATCH[1].getBlock().defaultBlockState());
                    helper.assertTrue(!machine.checkPattern(),
                            "Blast furnace accepted a standard fluid input hatch");
                    helper.getLevel().setBlockAndUpdate(fluidProbe,
                            GSEMachines.STEAM_FLUID_IMPORT_HATCH.getBlock().defaultBlockState());
                    helper.assertTrue(!machine.checkPattern(),
                            "Blast furnace accepted a steam fluid input hatch");
                    helper.getLevel().setBlockAndUpdate(
                            fluidProbe, GSEProcessorPatterns.blastBricks().defaultBlockState());
                    helper.assertTrue(machine.checkPattern(),
                            "Blast furnace did not recover after removing the fluid hatch");

                    BlockPos exhaust = exhausts.get(0);
                    BlockState exhaustState = helper.getLevel().getBlockState(exhaust);
                    helper.getLevel().setBlockAndUpdate(
                            exhaust, GSEProcessorPatterns.blastBricks().defaultBlockState());
                    helper.assertTrue(!machine.checkPattern(),
                            "Blast furnace formed without its exhaust hatch");
                    helper.getLevel().setBlockAndUpdate(exhaust, exhaustState);
                    helper.assertTrue(machine.checkPattern(),
                            "Blast furnace did not recover after restoring its exhaust hatch");

                    BlockPos duplicateExhaust = bricks.get(7);
                    helper.getLevel().setBlockAndUpdate(duplicateExhaust, exhaustState);
                    helper.assertTrue(!machine.checkPattern(),
                            "Blast furnace formed with two exhaust hatches");
                    helper.getLevel().setBlockAndUpdate(
                            duplicateExhaust, GSEProcessorPatterns.blastBricks().defaultBlockState());
                    helper.assertTrue(machine.checkPattern(),
                            "Blast furnace did not recover after removing its duplicate exhaust");
                    machine.onStructureFormed();
                    helper.assertTrue(machine.isFormed(),
                            "Blast furnace remained invalid after restoring its legal interfaces");
                })
                .thenSucceed();
    }

    @GameTest(template = "empty_32x32x32", timeoutTicks = 300)
    public static void largeSteamBlastFurnaceHasNoCombinedInterfaceCap(GameTestHelper helper) {
        var definition = GSEMachines.LARGE_STEAM_BLAST_FURNACE;
        MultiblockControllerMachine machine = GSEStructureTestUtils.placeShape(
                helper, definition, definition.getMatchingShapes().get(0));
        helper.assertTrue(machine != null, "Missing blast-furnace fixture controller");
        if (machine == null) {
            return;
        }

        List<BlockPos> bricks = new java.util.ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(
                machine.getPos().offset(-15, -15, -15),
                machine.getPos().offset(15, 15, 15))) {
            if (helper.getLevel().getBlockState(pos).is(GSEProcessorPatterns.blastBricks())) {
                bricks.add(pos.immutable());
            }
        }
        // The preview contains 14 interfaces. Fifteen additional supply
        // hatches produce 29 total, deliberately crossing the obsolete tooltip
        // limit while preserving all per-interface minimum and exact limits.
        helper.assertTrue(bricks.size() >= 15,
                "Blast-furnace preview lacks interface expansion positions");
        machine.onStructureInvalid();
        for (int i = 0; i < 15; i++) {
            helper.getLevel().setBlockAndUpdate(bricks.get(i),
                    GSEMachines.STEAM_SUPPLY_HATCH.getBlock().defaultBlockState());
        }
        helper.assertTrue(machine.checkPattern(),
                "Blast furnace rejected 29 interfaces despite having no combined cap");
        machine.onStructureFormed();
        helper.assertTrue(machine.isFormed(),
                "Blast furnace did not form with 29 interfaces");
        helper.assertTrue(machine.getParts().size() == 29,
                "Blast furnace collected " + machine.getParts().size() + " interfaces instead of 29");
        helper.succeed();
    }

    @GameTest(template = "empty_32x32x32", timeoutTicks = 300)
    public static void largeSteamBlastFurnaceFormsInAllHorizontalDirections(GameTestHelper helper) {
        var definition = GSEMachines.LARGE_STEAM_BLAST_FURNACE;
        var shape = definition.getMatchingShapes().get(0);
        Direction[] facings = { Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST };
        BlockPos[] anchors = {
                new BlockPos(7, 16, 1),
                new BlockPos(30, 16, 7),
                new BlockPos(24, 16, 30),
                new BlockPos(1, 16, 24)
        };
        List<MultiblockControllerMachine> machines = new java.util.ArrayList<>();

        for (int i = 0; i < facings.length; i++) {
            Direction facing = facings[i];
            MultiblockControllerMachine machine = GSEStructureTestUtils.placeShape(
                    helper, definition, shape, anchors[i], facing);
            helper.assertTrue(machine != null,
                    "Missing " + facing + " blast-furnace fixture controller");
            if (machine == null) {
                return;
            }
            helper.assertTrue(machine.getFrontFacing() == facing,
                    "Blast-furnace controller did not retain " + facing + " facing");
            helper.assertTrue(machine.checkPattern(),
                    facing + " blast-furnace structure did not match its pattern");
            machines.add(machine);
        }

        helper.startSequence()
                .thenWaitUntil(() -> {
                    for (int i = 0; i < machines.size(); i++) {
                        helper.assertTrue(machines.get(i).isFormed(),
                                facings[i] + " blast furnace did not form");
                    }
                })
                .thenSucceed();
    }

    @GameTest(template = "empty_32x32x32", timeoutTicks = 300)
    public static void adjacentLargeSteamBlastFurnacesShareSideWall(GameTestHelper helper) {
        var definition = GSEMachines.LARGE_STEAM_BLAST_FURNACE;
        var shape = definition.getMatchingShapes().get(0);
        MultiblockControllerMachine left = GSEStructureTestUtils.placeShape(
                helper, definition, shape, new BlockPos(7, 16, 1), Direction.NORTH);
        MultiblockControllerMachine right = GSEStructureTestUtils.placeShape(
                helper, definition, shape, new BlockPos(19, 16, 1), Direction.NORTH);
        helper.assertTrue(left != null && right != null,
                "Missing adjacent blast-furnace fixture controller");
        if (left == null || right == null) {
            return;
        }

        // The 13-wide hearth and tuyere deck overlap at x=13. The west preview
        // carries two intake hatches on this plane, so normalize the eleven
        // candidate cells to blast bricks: both structures then share only
        // ordinary structure blocks, while the industrial corner cells remain.
        for (int z = 2; z <= 12; z++) {
            helper.setBlock(new BlockPos(13, 17, z),
                    GSEProcessorPatterns.blastBricks().defaultBlockState());
        }
        for (int z = 1; z <= 13; z++) {
            helper.assertTrue(!helper.getBlockState(new BlockPos(13, 16, z)).isAir(),
                    "Shared hearth wall contains air at z=" + z);
            helper.assertTrue(!helper.getBlockState(new BlockPos(13, 17, z)).isAir(),
                    "Shared tuyere wall contains air at z=" + z);
        }

        helper.assertTrue(left.checkPattern(),
                "Left blast furnace rejected the shared side wall");
        helper.assertTrue(right.checkPattern(),
                "Right blast furnace rejected the shared side wall");
        helper.startSequence()
                .thenWaitUntil(() -> {
                    helper.assertTrue(left.isFormed(),
                            "Left blast furnace did not remain formed on the shared wall");
                    helper.assertTrue(right.isFormed(),
                            "Right blast furnace did not remain formed on the shared wall");
                })
                .thenSucceed();
    }

    @GameTest(template = "empty_32x32x32", timeoutTicks = 200)
    public static void largeSteamAssemblerFormsFromShape(GameTestHelper helper) {
        var definition = GSEMachines.LARGE_STEAM_ASSEMBLER;
        List<MultiblockShapeInfo> shapes = definition.getMatchingShapes();
        helper.assertTrue(!shapes.isEmpty(), "Large steam assembler has no preview shape");
        if (shapes.isEmpty()) {
            return;
        }

        BlockInfo[][][] blocks = shapes.get(0).getBlocks();
        helper.assertTrue(blocks.length == 9, "Large steam assembler width is not 9");
        int industrial = 0;
        int wallCasings = 0;
        int workstations = 0;
        int air = 0;
        int interfaces = 0;
        int controllers = 0;
        for (int column = 0; column < blocks.length; column++) {
            helper.assertTrue(blocks[column].length == 9,
                    "Large steam assembler height is not 9 at column " + column);
            for (int layer = 0; layer < blocks[column].length; layer++) {
                helper.assertTrue(blocks[column][layer].length == 9,
                        "Large steam assembler depth is not 9 at column/layer " + column + "/" + layer);
                for (int depth = 0; depth < blocks[column][layer].length; depth++) {
                    BlockState state = blocks[column][layer][depth].getBlockState();
                    Block expected = expectedLargeSteamAssemblerBlock(definition, depth, layer, column);
                    helper.assertTrue(expected == Blocks.AIR ? state.isAir() : state.is(expected),
                            "Large steam assembler shape differs at depth/layer/column "
                                    + depth + "/" + layer + "/" + column
                                    + ": expected=" + expected + ", actual=" + state.getBlock());

                    if (state.is(GSEProcessorPatterns.industrialSteamCasing())) industrial++;
                    else if (state.is(GSEProcessorPatterns.bronzeSteamCasing())) wallCasings++;
                    else if (state.is(GSEBlocks.STEAM_ASSEMBLY_BLOCK.get())) workstations++;
                    else if (state.isAir()) air++;
                    else if (state.is(definition.getBlock())) controllers++;
                    else interfaces++;
                }
            }
        }
        helper.assertTrue(industrial == 189, "Expected 189 industrial casings, found " + industrial);
        helper.assertTrue(wallCasings == 191,
                "Expected 191 wall casings after five representative interfaces, found " + wallCasings);
        helper.assertTrue(interfaces == 5, "Expected five representative interfaces, found " + interfaces);
        helper.assertTrue(workstations == 18, "Expected 18 assembly workstations, found " + workstations);
        helper.assertTrue(air == 325, "Expected 325 strict-air cells, found " + air);
        helper.assertTrue(controllers == 1, "Expected one assembler controller, found " + controllers);

        MultiblockControllerMachine machine = GSEStructureTestUtils.placeShape(
                helper, definition, shapes.get(0));
        helper.assertTrue(machine != null, "Large steam assembler fixture has no controller");
        if (machine == null) {
            return;
        }
        helper.assertTrue(machine.checkPattern(), "Exact large steam assembler shape did not match its pattern");
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(machine.isFormed(),
                        "Exact large steam assembler did not become formed"))
                .thenSucceed();
    }

    @GameTest(template = "empty_32x32x32", timeoutTicks = 300)
    public static void largeSteamAssemblerRestrictsHatchesToWallCasings(GameTestHelper helper) {
        var definition = GSEMachines.LARGE_STEAM_ASSEMBLER;
        MultiblockControllerMachine machine = GSEStructureTestUtils.placeShape(
                helper, definition, definition.getMatchingShapes().get(0));
        helper.assertTrue(machine != null, "Missing large steam assembler fixture controller");
        if (machine == null) {
            return;
        }

        BlockPos controller = machine.getPos();
        // North-facing preview coordinates relative to the bottom-front controller:
        // one replaceable front wall cell and one fixed cell from each industrial region.
        BlockPos wall = controller.offset(-3, 2, 0);
        BlockPos bottom = controller.offset(-3, 0, 4);
        BlockPos top = controller.offset(0, 8, 4);
        BlockPos verticalEdge = controller.offset(-4, 2, 0);
        BlockState wallState = helper.getLevel().getBlockState(wall);
        BlockState bottomState = helper.getLevel().getBlockState(bottom);
        BlockState topState = helper.getLevel().getBlockState(top);
        BlockState edgeState = helper.getLevel().getBlockState(verticalEdge);
        BlockState hatchState = GSEMachines.STEAM_SUPPLY_HATCH.getBlock().defaultBlockState();

        helper.assertTrue(wallState.is(GSEProcessorPatterns.bronzeSteamCasing()),
                "Assembler wall probe is not a bronze steam casing");
        helper.assertTrue(bottomState.is(GSEProcessorPatterns.industrialSteamCasing()),
                "Assembler bottom probe is not an industrial steam casing");
        helper.assertTrue(topState.is(GSEProcessorPatterns.industrialSteamCasing()),
                "Assembler top probe is not an industrial steam casing");
        helper.assertTrue(edgeState.is(GSEProcessorPatterns.industrialSteamCasing()),
                "Assembler edge probe is not an industrial steam casing");

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(machine.isFormed(),
                        "Baseline large steam assembler fixture did not form"))
                .thenExecute(() -> {
                    machine.onStructureInvalid();

                    helper.getLevel().setBlockAndUpdate(wall, hatchState);
                    helper.assertTrue(machine.checkPattern(),
                            "Large steam assembler rejected a supply hatch on its wall");
                    helper.getLevel().setBlockAndUpdate(wall, wallState);
                    helper.assertTrue(machine.checkPattern(),
                            "Large steam assembler did not recover its wall casing");

                    helper.getLevel().setBlockAndUpdate(bottom, hatchState);
                    helper.assertTrue(!machine.checkPattern(),
                            "Large steam assembler accepted a hatch on its bottom face");
                    helper.getLevel().setBlockAndUpdate(bottom, bottomState);
                    helper.assertTrue(machine.checkPattern(),
                            "Large steam assembler did not recover its bottom casing");

                    helper.getLevel().setBlockAndUpdate(top, hatchState);
                    helper.assertTrue(!machine.checkPattern(),
                            "Large steam assembler accepted a hatch on its top face");
                    helper.getLevel().setBlockAndUpdate(top, topState);
                    helper.assertTrue(machine.checkPattern(),
                            "Large steam assembler did not recover its top casing");

                    helper.getLevel().setBlockAndUpdate(verticalEdge, hatchState);
                    helper.assertTrue(!machine.checkPattern(),
                            "Large steam assembler accepted a hatch on its vertical edge");
                    helper.getLevel().setBlockAndUpdate(verticalEdge, edgeState);
                    helper.assertTrue(machine.checkPattern(),
                            "Large steam assembler did not recover its edge casing");

                    machine.onStructureFormed();
                    helper.assertTrue(machine.isFormed(),
                            "Large steam assembler remained invalid after restoring its shell");
                })
                .thenSucceed();
    }

    @GameTest(template = "empty_32x32x32", timeoutTicks = 300)
    public static void largeSteamAssemblerRequiresExactlyOneExhaust(GameTestHelper helper) {
        var definition = GSEMachines.LARGE_STEAM_ASSEMBLER;
        MultiblockControllerMachine machine = GSEStructureTestUtils.placeShape(
                helper, definition, definition.getMatchingShapes().get(0));
        helper.assertTrue(machine != null, "Missing large steam assembler fixture controller");
        if (machine == null) {
            return;
        }

        List<BlockPos> exhausts = new java.util.ArrayList<>();
        List<BlockPos> wallCasings = new java.util.ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(
                machine.getPos().offset(-4, 0, 0), machine.getPos().offset(4, 8, 8))) {
            BlockState state = helper.getLevel().getBlockState(pos);
            if (state.is(GSEMachines.STEAM_EXHAUST_HATCH.getBlock())) {
                exhausts.add(pos.immutable());
            } else if (state.is(GSEProcessorPatterns.bronzeSteamCasing())) {
                wallCasings.add(pos.immutable());
            }
        }
        helper.assertTrue(exhausts.size() == 1,
                "Assembler preview exhaust count changed: " + exhausts.size());
        helper.assertTrue(!wallCasings.isEmpty(), "Assembler preview has no wall casing mutation position");

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(machine.isFormed(),
                        "Baseline large steam assembler fixture did not form"))
                .thenExecute(() -> {
                    machine.onStructureInvalid();
                    BlockPos exhaust = exhausts.get(0);
                    BlockState exhaustState = helper.getLevel().getBlockState(exhaust);
                    BlockState wallState = GSEProcessorPatterns.bronzeSteamCasing().defaultBlockState();

                    helper.getLevel().setBlockAndUpdate(exhaust, wallState);
                    helper.assertTrue(!machine.checkPattern(),
                            "Large steam assembler formed without an exhaust hatch");
                    helper.getLevel().setBlockAndUpdate(exhaust, exhaustState);
                    helper.assertTrue(machine.checkPattern(),
                            "Large steam assembler did not recover after restoring its exhaust hatch");

                    BlockPos duplicate = wallCasings.get(0);
                    BlockState duplicateOriginal = helper.getLevel().getBlockState(duplicate);
                    helper.getLevel().setBlockAndUpdate(duplicate, exhaustState);
                    helper.assertTrue(!machine.checkPattern(),
                            "Large steam assembler formed with two exhaust hatches");
                    helper.getLevel().setBlockAndUpdate(duplicate, duplicateOriginal);
                    helper.assertTrue(machine.checkPattern(),
                            "Large steam assembler did not recover after removing its duplicate exhaust");

                    machine.onStructureFormed();
                    helper.assertTrue(machine.isFormed(),
                            "Large steam assembler remained invalid with exactly one exhaust hatch");
                })
                .thenSucceed();
    }

    @GameTest(template = "empty_32x32x32", timeoutTicks = 300)
    public static void adjacentLargeSteamAssemblersShareSideWall(GameTestHelper helper) {
        var definition = GSEMachines.LARGE_STEAM_ASSEMBLER;
        var shape = definition.getMatchingShapes().get(0);
        MultiblockControllerMachine left = GSEStructureTestUtils.placeShape(
                helper, definition, shape, new BlockPos(5, 16, 4), Direction.NORTH);
        MultiblockControllerMachine right = GSEStructureTestUtils.placeShape(
                helper, definition, shape, new BlockPos(13, 16, 4), Direction.NORTH);
        helper.assertTrue(left != null && right != null,
                "Missing adjacent large steam assembler fixture controller");
        if (left == null || right == null) {
            return;
        }

        // The controllers are eight blocks apart, so their nine-wide shells
        // share the complete x=9 side wall without sharing any interface part.
        for (int y = 16; y <= 24; y++) {
            for (int z = 4; z <= 12; z++) {
                BlockState state = helper.getBlockState(new BlockPos(9, y, z));
                boolean industrial = y == 16 || y == 24 || z == 4 || z == 12;
                helper.assertTrue(industrial
                                ? state.is(GSEProcessorPatterns.industrialSteamCasing())
                                : state.is(GSEProcessorPatterns.bronzeSteamCasing()),
                        "Shared assembler wall differs at y/z=" + y + "/" + z);
            }
        }

        helper.assertTrue(left.checkPattern(), "Left assembler rejected the shared side wall");
        helper.assertTrue(right.checkPattern(), "Right assembler rejected the shared side wall");
        helper.startSequence()
                .thenWaitUntil(() -> {
                    helper.assertTrue(left.isFormed(),
                            "Left assembler did not remain formed on the shared side wall");
                    helper.assertTrue(right.isFormed(),
                            "Right assembler did not remain formed on the shared side wall");
                })
                .thenSucceed();
    }

    private static Block expectedLargeSteamAssemblerBlock(MultiblockMachineDefinition definition,
                                                           int depth, int layer, int column) {
        if (layer == 0) {
            return depth == 0 && column == 4
                    ? definition.getBlock()
                    : GSEProcessorPatterns.industrialSteamCasing();
        }
        if (layer == 8) {
            return GSEProcessorPatterns.industrialSteamCasing();
        }
        boolean boundary = depth == 0 || depth == 8 || column == 0 || column == 8;
        if (boundary) {
            if (layer == 1 && depth == 0) {
                return switch (column) {
                    case 2 -> GTMachines.STEAM_IMPORT_BUS.getBlock();
                    case 3 -> GTMachines.STEAM_EXPORT_BUS.getBlock();
                    case 4 -> GSEMachines.STEAM_SUPPLY_HATCH.getBlock();
                    case 5 -> GTMachines.FLUID_IMPORT_HATCH[1].getBlock();
                    case 6 -> GSEMachines.STEAM_EXHAUST_HATCH.getBlock();
                    default -> expectedLargeSteamAssemblerShell(depth, column);
                };
            }
            return expectedLargeSteamAssemblerShell(depth, column);
        }
        boolean workstationLayer = layer == 1 || layer == 7;
        boolean workstationColumn = column == 2 || column == 4 || column == 6;
        boolean workstationDepth = depth == 2 || depth == 4 || depth == 6;
        return workstationLayer && workstationColumn && workstationDepth
                ? GSEBlocks.STEAM_ASSEMBLY_BLOCK.get()
                : Blocks.AIR;
    }

    private static Block expectedLargeSteamAssemblerShell(int depth, int column) {
        boolean edge = (depth == 0 || depth == 8) && (column == 0 || column == 8);
        return edge ? GSEProcessorPatterns.industrialSteamCasing()
                : GSEProcessorPatterns.bronzeSteamCasing();
    }

    @GameTest(template = "empty_32x32x32", timeoutTicks = 200)
    public static void largeSteamCircuitAssemblerFormsFromShape(GameTestHelper helper) {
        var definition = GSEMachines.LARGE_STEAM_CIRCUIT_ASSEMBLER;
        List<MultiblockShapeInfo> shapes = definition.getMatchingShapes();
        helper.assertTrue(!shapes.isEmpty(), "Large steam circuit assembler has no preview shape");
        if (shapes.isEmpty()) {
            return;
        }

        BlockInfo[][][] blocks = shapes.get(0).getBlocks();
        helper.assertTrue(blocks.length == 5, "Large steam circuit assembler width is not 5");
        int bronzeCasings = 0;
        int industrial = 0;
        int circuitBlocks = 0;
        int gearboxes = 0;
        int assemblyBlocks = 0;
        int pipeCasings = 0;
        int air = 0;
        int interfaces = 0;
        int controllers = 0;
        for (int column = 0; column < blocks.length; column++) {
            helper.assertTrue(blocks[column].length == 6,
                    "Large steam circuit assembler height is not 6 at column " + column);
            for (int layer = 0; layer < blocks[column].length; layer++) {
                helper.assertTrue(blocks[column][layer].length == 11,
                        "Large steam circuit assembler depth is not 11 at column/layer "
                                + column + "/" + layer);
                for (int depth = 0; depth < blocks[column][layer].length; depth++) {
                    BlockState state = blocks[column][layer][depth].getBlockState();
                    Block expected = expectedLargeSteamCircuitAssemblerBlock(
                            definition, depth, layer, column);
                    helper.assertTrue(expected == Blocks.AIR ? state.isAir() : state.is(expected),
                            "Large steam circuit assembler shape differs at depth/layer/column "
                                    + depth + "/" + layer + "/" + column
                                    + ": expected=" + expected + ", actual=" + state.getBlock());

                    if (state.is(GSEProcessorPatterns.bronzeSteamCasing())) bronzeCasings++;
                    else if (state.is(GSEProcessorPatterns.industrialSteamCasing())) industrial++;
                    else if (state.is(GSEBlocks.STEAM_CIRCUIT_ASSEMBLY_BLOCK.get())) circuitBlocks++;
                    else if (state.is(GTBlocks.CASING_BRONZE_GEARBOX.get())) gearboxes++;
                    else if (state.is(GSEBlocks.STEAM_ASSEMBLY_BLOCK.get())) assemblyBlocks++;
                    else if (state.is(GSEProcessorPatterns.bronzePipeCasing())) pipeCasings++;
                    else if (state.isAir()) air++;
                    else if (state.is(definition.getBlock())) controllers++;
                    else interfaces++;
                }
            }
        }
        helper.assertTrue(bronzeCasings == 161,
                "Expected 161 bronze steam casings after five representative interfaces, found "
                        + bronzeCasings);
        helper.assertTrue(industrial == 11, "Expected 11 industrial ridge casings, found " + industrial);
        helper.assertTrue(circuitBlocks == 9, "Expected 9 circuit assembly blocks, found " + circuitBlocks);
        helper.assertTrue(gearboxes == 9, "Expected 9 bronze gearboxes, found " + gearboxes);
        helper.assertTrue(assemblyBlocks == 9, "Expected 9 assembly blocks, found " + assemblyBlocks);
        helper.assertTrue(pipeCasings == 9, "Expected 9 bronze pipe casings, found " + pipeCasings);
        helper.assertTrue(air == 116, "Expected 116 strict-air cells, found " + air);
        helper.assertTrue(interfaces == 5, "Expected five representative interfaces, found " + interfaces);
        helper.assertTrue(controllers == 1, "Expected one circuit assembler controller, found " + controllers);

        MultiblockControllerMachine machine = GSEStructureTestUtils.placeShape(
                helper, definition, shapes.get(0));
        helper.assertTrue(machine != null, "Large steam circuit assembler fixture has no controller");
        if (machine == null) {
            return;
        }
        helper.assertTrue(machine.checkPattern(),
                "Exact large steam circuit assembler shape did not match its pattern");
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(machine.isFormed(),
                        "Exact large steam circuit assembler did not become formed"))
                .thenSucceed();
    }

    @GameTest(template = "empty_32x32x32", timeoutTicks = 300)
    public static void largeSteamCircuitAssemblerRestrictsHatchesToShell(GameTestHelper helper) {
        var definition = GSEMachines.LARGE_STEAM_CIRCUIT_ASSEMBLER;
        MultiblockControllerMachine machine = GSEStructureTestUtils.placeShape(
                helper, definition, definition.getMatchingShapes().get(0));
        helper.assertTrue(machine != null, "Missing large steam circuit assembler fixture controller");
        if (machine == null) {
            return;
        }

        BlockPos controller = machine.getPos();
        BlockPos wall = controller.offset(-1, 2, 0);
        BlockPos bottom = controller.offset(-1, 0, 4);
        BlockPos ridge = controller.offset(0, 5, 4);
        BlockPos processTower = controller.offset(0, 1, 4);
        BlockState wallState = helper.getLevel().getBlockState(wall);
        BlockState bottomState = helper.getLevel().getBlockState(bottom);
        BlockState ridgeState = helper.getLevel().getBlockState(ridge);
        BlockState processTowerState = helper.getLevel().getBlockState(processTower);
        BlockState hatchState = GSEMachines.STEAM_SUPPLY_HATCH.getBlock().defaultBlockState();

        helper.assertTrue(wallState.is(GSEProcessorPatterns.bronzeSteamCasing()),
                "Circuit assembler wall probe is not a bronze steam casing");
        helper.assertTrue(bottomState.is(GSEProcessorPatterns.bronzeSteamCasing()),
                "Circuit assembler bottom probe is not a bronze steam casing");
        helper.assertTrue(ridgeState.is(GSEProcessorPatterns.industrialSteamCasing()),
                "Circuit assembler ridge probe is not an industrial steam casing");
        helper.assertTrue(processTowerState.is(GSEBlocks.STEAM_CIRCUIT_ASSEMBLY_BLOCK.get()),
                "Circuit assembler process-tower probe is not a circuit assembly block");

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(machine.isFormed(),
                        "Baseline large steam circuit assembler fixture did not form"))
                .thenExecute(() -> {
                    machine.onStructureInvalid();

                    helper.getLevel().setBlockAndUpdate(wall, hatchState);
                    helper.assertTrue(machine.checkPattern(),
                            "Large steam circuit assembler rejected a supply hatch on its wall");
                    helper.getLevel().setBlockAndUpdate(wall, wallState);
                    helper.assertTrue(machine.checkPattern(),
                            "Large steam circuit assembler did not recover its wall casing");

                    helper.getLevel().setBlockAndUpdate(bottom, hatchState);
                    helper.assertTrue(machine.checkPattern(),
                            "Large steam circuit assembler rejected a supply hatch on its bottom face");
                    helper.getLevel().setBlockAndUpdate(bottom, bottomState);
                    helper.assertTrue(machine.checkPattern(),
                            "Large steam circuit assembler did not recover its bottom casing");

                    helper.getLevel().setBlockAndUpdate(ridge, hatchState);
                    helper.assertTrue(!machine.checkPattern(),
                            "Large steam circuit assembler accepted a hatch on its industrial ridge");
                    helper.getLevel().setBlockAndUpdate(ridge, ridgeState);
                    helper.assertTrue(machine.checkPattern(),
                            "Large steam circuit assembler did not recover its ridge casing");

                    helper.getLevel().setBlockAndUpdate(processTower, hatchState);
                    helper.assertTrue(!machine.checkPattern(),
                            "Large steam circuit assembler accepted a hatch in its process tower");
                    helper.getLevel().setBlockAndUpdate(processTower, processTowerState);
                    helper.assertTrue(machine.checkPattern(),
                            "Large steam circuit assembler did not recover its process tower");

                    machine.onStructureFormed();
                    helper.assertTrue(machine.isFormed(),
                            "Large steam circuit assembler remained invalid after restoring its structure");
                })
                .thenSucceed();
    }

    @GameTest(template = "empty_32x32x32", timeoutTicks = 300)
    public static void largeSteamCircuitAssemblerRequiresExactlyOneExhaust(GameTestHelper helper) {
        var definition = GSEMachines.LARGE_STEAM_CIRCUIT_ASSEMBLER;
        MultiblockControllerMachine machine = GSEStructureTestUtils.placeShape(
                helper, definition, definition.getMatchingShapes().get(0));
        helper.assertTrue(machine != null, "Missing large steam circuit assembler fixture controller");
        if (machine == null) {
            return;
        }

        List<BlockPos> exhausts = new java.util.ArrayList<>();
        List<BlockPos> shellCasings = new java.util.ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(
                machine.getPos().offset(-2, 0, 0), machine.getPos().offset(2, 5, 10))) {
            BlockState state = helper.getLevel().getBlockState(pos);
            if (state.is(GSEMachines.STEAM_EXHAUST_HATCH.getBlock())) {
                exhausts.add(pos.immutable());
            } else if (state.is(GSEProcessorPatterns.bronzeSteamCasing())) {
                shellCasings.add(pos.immutable());
            }
        }
        helper.assertTrue(exhausts.size() == 1,
                "Circuit assembler preview exhaust count changed: " + exhausts.size());
        helper.assertTrue(!shellCasings.isEmpty(),
                "Circuit assembler preview has no shell casing mutation position");

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(machine.isFormed(),
                        "Baseline large steam circuit assembler fixture did not form"))
                .thenExecute(() -> {
                    machine.onStructureInvalid();
                    BlockPos exhaust = exhausts.get(0);
                    BlockState exhaustState = helper.getLevel().getBlockState(exhaust);
                    BlockState casingState = GSEProcessorPatterns.bronzeSteamCasing().defaultBlockState();

                    helper.getLevel().setBlockAndUpdate(exhaust, casingState);
                    helper.assertTrue(!machine.checkPattern(),
                            "Large steam circuit assembler formed without an exhaust hatch");
                    helper.getLevel().setBlockAndUpdate(exhaust, exhaustState);
                    helper.assertTrue(machine.checkPattern(),
                            "Large steam circuit assembler did not recover its exhaust hatch");

                    BlockPos duplicate = shellCasings.get(0);
                    BlockState duplicateOriginal = helper.getLevel().getBlockState(duplicate);
                    helper.getLevel().setBlockAndUpdate(duplicate, exhaustState);
                    helper.assertTrue(!machine.checkPattern(),
                            "Large steam circuit assembler formed with two exhaust hatches");
                    helper.getLevel().setBlockAndUpdate(duplicate, duplicateOriginal);
                    helper.assertTrue(machine.checkPattern(),
                            "Large steam circuit assembler did not recover after removing its duplicate exhaust");

                    machine.onStructureFormed();
                    helper.assertTrue(machine.isFormed(),
                            "Large steam circuit assembler remained invalid with exactly one exhaust hatch");
                })
                .thenSucceed();
    }

    @GameTest(template = "empty_32x32x32", timeoutTicks = 300)
    public static void backToBackLargeSteamCircuitAssemblersShareRearWall(GameTestHelper helper) {
        var definition = GSEMachines.LARGE_STEAM_CIRCUIT_ASSEMBLER;
        var shape = definition.getMatchingShapes().get(0);
        MultiblockControllerMachine north = GSEStructureTestUtils.placeShape(
                helper, definition, shape, new BlockPos(8, 16, 4), Direction.NORTH);
        MultiblockControllerMachine south = GSEStructureTestUtils.placeShape(
                helper, definition, shape, new BlockPos(8, 16, 24), Direction.SOUTH);
        helper.assertTrue(north != null && south != null,
                "Missing back-to-back large steam circuit assembler fixture controller");
        if (north == null || south == null) {
            return;
        }

        // Opposite-facing structures meet at their plain rear faces (z=14),
        // keeping both five-interface front rows independent.
        for (int y = 16; y <= 20; y++) {
            for (int x = 6; x <= 10; x++) {
                helper.assertTrue(helper.getBlockState(new BlockPos(x, y, 14))
                                .is(GSEProcessorPatterns.bronzeSteamCasing()),
                        "Shared circuit assembler rear wall differs at x/y=" + x + "/" + y);
            }
        }
        for (int x = 6; x <= 10; x++) {
            BlockState state = helper.getBlockState(new BlockPos(x, 21, 14));
            helper.assertTrue(x == 8
                            ? state.is(GSEProcessorPatterns.industrialSteamCasing())
                            : state.isAir(),
                    "Shared circuit assembler roof edge differs at x=" + x);
        }

        helper.assertTrue(north.checkPattern(),
                "North-facing circuit assembler rejected the shared rear wall");
        helper.assertTrue(south.checkPattern(),
                "South-facing circuit assembler rejected the shared rear wall");
        helper.startSequence()
                .thenWaitUntil(() -> {
                    helper.assertTrue(north.isFormed(),
                            "North-facing circuit assembler did not remain formed");
                    helper.assertTrue(south.isFormed(),
                            "South-facing circuit assembler did not remain formed");
                })
                .thenSucceed();
    }

    private static Block expectedLargeSteamCircuitAssemblerBlock(
            MultiblockMachineDefinition definition, int depth, int layer, int column) {
        if (layer == 0) {
            return depth == 0 && column == 2
                    ? definition.getBlock()
                    : GSEProcessorPatterns.bronzeSteamCasing();
        }
        if (layer == 5) {
            return column == 2 ? GSEProcessorPatterns.industrialSteamCasing() : Blocks.AIR;
        }
        boolean boundary = depth == 0 || depth == 10 || column == 0 || column == 4;
        if (boundary) {
            if (layer == 1 && depth == 0) {
                return switch (column) {
                    case 0 -> GTMachines.STEAM_IMPORT_BUS.getBlock();
                    case 1 -> GTMachines.STEAM_EXPORT_BUS.getBlock();
                    case 2 -> GSEMachines.STEAM_SUPPLY_HATCH.getBlock();
                    case 3 -> GTMachines.FLUID_IMPORT_HATCH[1].getBlock();
                    case 4 -> GSEMachines.STEAM_EXHAUST_HATCH.getBlock();
                    default -> throw new IllegalStateException("Unexpected circuit assembler column " + column);
                };
            }
            return GSEProcessorPatterns.bronzeSteamCasing();
        }
        if (column != 2) {
            return Blocks.AIR;
        }
        return switch (layer) {
            case 1 -> GSEBlocks.STEAM_CIRCUIT_ASSEMBLY_BLOCK.get();
            case 2 -> GTBlocks.CASING_BRONZE_GEARBOX.get();
            case 3 -> GSEBlocks.STEAM_ASSEMBLY_BLOCK.get();
            case 4 -> GSEProcessorPatterns.bronzePipeCasing();
            default -> throw new IllegalStateException("Unexpected circuit assembler layer " + layer);
        };
    }

    @GameTest(template = "empty_32x32x32", timeoutTicks = 300)
    public static void largeSteamOrePlantFormsFromShape(GameTestHelper helper) {
        GSEStructureTestUtils.assertFirstShapeForms(helper, GSEMachines.LARGE_STEAM_ORE_PLANT);
    }

    @GameTest(template = "empty_32x32x32", timeoutTicks = 300)
    public static void largeSteamFluidDrillFormsFromShape(GameTestHelper helper) {
        GSEStructureTestUtils.assertFirstShapeForms(helper, GSEMachines.LARGE_STEAM_FLUID_DRILL);
    }

    @GameTest(template = "empty_32x32x32", timeoutTicks = 300)
    public static void largeHeatStorageSteamFurnaceFormsFromShape(GameTestHelper helper) {
        GSEStructureTestUtils.assertFirstShapeForms(helper, GSEMachines.LARGE_HEAT_STORAGE_STEAM_FURNACE);
    }

    @GameTest(template = "empty_32x32x32", timeoutTicks = 300)
    public static void largeCokeOvenFormsFromShape(GameTestHelper helper) {
        GSEStructureTestUtils.assertFirstShapeForms(helper, GSEMachines.LARGE_COKE_OVEN);
    }

    @GameTest(template = "empty_32x32x32", timeoutTicks = 300)
    public static void boilerRoomBronzeFormsFromShape(GameTestHelper helper) {
        GSEStructureTestUtils.assertFirstShapeForms(helper, GSEMachines.BOILER_ROOM_BRONZE);
    }
}
