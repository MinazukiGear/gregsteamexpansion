package com.hoshino.gregsteamexpansion.gametest;

import com.hoshino.gregsteamexpansion.GregSteamExpansion;
import com.hoshino.gregsteamexpansion.registry.GSEBlocks;
import com.hoshino.gregsteamexpansion.registry.GSEMachines;
import com.hoshino.gregsteamexpansion.terminal.TerminalBuildProfile;
import com.hoshino.gregsteamexpansion.terminal.UltimateStructurePlanner;
import com.hoshino.gregsteamexpansion.terminal.UltimateTerminalConfig;
import com.hoshino.gregsteamexpansion.terminal.UltimateTerminalStructureVariants;
import com.hoshino.gregsteamexpansion.terminal.UltimateTerminalWorldData;

import com.gregtechceu.gtceu.common.data.machines.GTMultiMachines;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Regression coverage for the Ultimate Terminal profile and hologram blueprint model. */
@GameTestHolder(GregSteamExpansion.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GSETerminalTests {
    private static final BlockPos CONTROLLER = new BlockPos(16, 16, 16);

    private GSETerminalTests() {}

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void profileRoundTripClampsPersistentChoices(GameTestHelper helper) {
        ResourceLocation casing = ResourceLocation.fromNamespaceAndPath("minecraft", "stone");
        TerminalBuildProfile profile = new TerminalBuildProfile();
        profile.changeRepeats(100);
        profile.changeCoilTier(100);
        profile.changePart(casing, 25, 20);
        profile.changeChannel("glass", 25, 5);

        TerminalBuildProfile restored = TerminalBuildProfile.load(profile.save());
        helper.assertTrue(restored.repeatCount() == 64, "Repeat count was not clamped to 64");
        helper.assertTrue(restored.coilTier() == 16, "Coil tier was not clamped to 16");
        helper.assertTrue(!restored.buildHatches(), "Ultimate Terminal unexpectedly enabled hatch placement");
        helper.assertTrue(restored.partTargets().getOrDefault(casing, 0) == 20,
                "Candidate target did not survive NBT");
        helper.assertTrue(restored.channelSelection("glass") == 5,
                "Configured selection channel did not survive NBT");
        var channel = UltimateTerminalConfig.parseChannel(
                "glass=minecraft:glass,gtceu:tempered_glass,minecraft:glass");
        helper.assertTrue(channel != null && channel.blocks().size() == 2,
                "Configured selection channel was not parsed or deduplicated");
        helper.assertTrue(UltimateTerminalConfig.parseChannel(
                "coil=gtceu:cupronickel_coil_block,gtceu:kanthal_coil_block") == null,
                "Reserved built-in coil channel ID was accepted");
        helper.assertTrue(UltimateTerminalConfig.parseChannel(
                "structure_size=minecraft:stone,minecraft:glass") == null,
                "Reserved built-in structure-size channel ID was accepted");

        CompoundTag legacy = new CompoundTag();
        TerminalBuildProfile migrated = TerminalBuildProfile.load(legacy);
        helper.assertTrue(!migrated.buildHatches(), "A legacy profile re-enabled terminal hatch placement");
        helper.succeed();
    }

    @GameTest(template = "empty_32x32x32", timeoutTicks = 200)
    public static void blueprintClassifiesMissingSatisfiedAndConflictCells(GameTestHelper helper) {
        GSEStructureTestUtils.placeMachine(helper, GSEMachines.STEAM_CRUSHER, CONTROLLER);
        BlockPos controllerPos = helper.absolutePos(CONTROLLER);
        TerminalBuildProfile profile = new TerminalBuildProfile();

        var player = FakePlayerFactory.getMinecraft(helper.getLevel());
        ItemStack terminal = new ItemStack(GSEBlocks.ULTIMATE_TERMINAL.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, terminal);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(controllerPos), Direction.UP,
                controllerPos, false);
        UltimateTerminalWorldData data = UltimateTerminalWorldData.get(helper.getLevel().getServer());
        data.removeTarget(player, helper.getLevel().dimension(), controllerPos);
        player.setShiftKeyDown(false);
        player.gameMode.useItemOn(player, helper.getLevel(), terminal, InteractionHand.MAIN_HAND, hit);
        helper.assertTrue(data.targetCount(player.getUUID()) == 1,
                "Controller use consumed the interaction before the terminal added its target");
        player.setShiftKeyDown(true);
        player.gameMode.useItemOn(player, helper.getLevel(), terminal, InteractionHand.MAIN_HAND, hit);
        helper.assertTrue(data.targetCount(player.getUUID()) == 0,
                "Sneak-use did not remove the selected controller target");
        player.setShiftKeyDown(false);

        UltimateStructurePlanner.Plan missing = UltimateStructurePlanner.plan(
                helper.getLevel(), controllerPos, profile, false);
        helper.assertTrue(missing.valid(), "An empty structure volume must produce a buildable blueprint: "
                + missing.error());
        helper.assertTrue(!missing.cells().isEmpty(), "The blueprint contains no structure cells");
        helper.assertTrue(!missing.candidates().isEmpty(), "The blueprint contains no legal candidates");
        var cell = missing.cells().stream()
                .filter(value -> value.status() == UltimateStructurePlanner.CellStatus.MISSING)
                .findFirst().orElse(null);
        helper.assertTrue(cell != null, "The empty structure volume produced no missing cell");
        if (cell == null) return;

        var expectedBlock = ((net.minecraft.world.item.BlockItem) cell.expected().getItem()).getBlock();
        helper.getLevel().setBlockAndUpdate(cell.pos(), expectedBlock.defaultBlockState());
        UltimateStructurePlanner.Plan satisfied = UltimateStructurePlanner.plan(
                helper.getLevel(), controllerPos, profile, false);
        helper.assertTrue(satisfied.cells().stream().anyMatch(value -> value.pos().equals(cell.pos())
                        && value.status() == UltimateStructurePlanner.CellStatus.SATISFIED),
                "A matching placed block was not classified as satisfied");

        helper.getLevel().setBlockAndUpdate(cell.pos(), Blocks.STONE.defaultBlockState());
        UltimateStructurePlanner.Plan conflict = UltimateStructurePlanner.plan(
                helper.getLevel(), controllerPos, profile, false);
        helper.assertTrue(!conflict.valid(), "An occupied incompatible cell must invalidate the blueprint");
        helper.assertTrue(conflict.cells().stream().anyMatch(value -> value.pos().equals(cell.pos())
                        && value.status() == UltimateStructurePlanner.CellStatus.CONFLICT),
                "An occupied incompatible cell was not classified as a conflict");
        helper.succeed();
    }

    @GameTest(template = "empty_32x32x32", timeoutTicks = 200)
    public static void dismantlePlansManuallyBuiltMultiblock(GameTestHelper helper) {
        var shapes = GSEMachines.STEAM_CRUSHER.getMatchingShapes();
        helper.assertTrue(!shapes.isEmpty(), "Steam crusher has no preview shape");
        if (shapes.isEmpty()) return;
        var controller = GSEStructureTestUtils.placeShape(helper, GSEMachines.STEAM_CRUSHER, shapes.get(0));
        helper.assertTrue(controller != null, "Steam crusher controller was not placed");
        if (controller == null) return;
        helper.assertTrue(controller.getPattern().checkPatternAt(controller.getMultiblockState(), true),
                "Manually placed steam crusher did not form");

        BlockPos controllerPos = helper.absolutePos(CONTROLLER);
        UltimateStructurePlanner.Plan plan = UltimateStructurePlanner.planDismantle(
                helper.getLevel(), controllerPos, new TerminalBuildProfile());
        helper.assertTrue(plan.valid(), "Valid manually built multiblock could not be planned for dismantling: "
                + plan.error());
        helper.assertTrue(!plan.placements().isEmpty(),
                "Manually built multiblock produced an empty dismantle queue");
        helper.assertTrue(plan.placements().stream().noneMatch(value -> value.pos().equals(controllerPos)
                        || helper.getLevel().getBlockEntity(value.pos()) != null),
                "Dismantle queue included the controller or a block entity");

        helper.succeed();
    }

    @GameTest(template = "empty_32x32x32", timeoutTicks = 200)
    public static void dismantlePlansUnformedPartialMultiblock(GameTestHelper helper) {
        GSEStructureTestUtils.placeMachine(helper, GSEMachines.STEAM_CRUSHER, CONTROLLER);
        BlockPos controllerPos = helper.absolutePos(CONTROLLER);
        TerminalBuildProfile profile = new TerminalBuildProfile();
        UltimateStructurePlanner.Plan blueprint = UltimateStructurePlanner.plan(
                helper.getLevel(), controllerPos, profile, false);
        var missing = blueprint.cells().stream()
                .filter(value -> value.status() == UltimateStructurePlanner.CellStatus.MISSING)
                .limit(2).toList();
        helper.assertTrue(missing.size() == 2, "Steam crusher blueprint has fewer than two ordinary cells");
        if (missing.size() < 2) return;

        var removable = missing.get(0);
        var expectedBlock = ((net.minecraft.world.item.BlockItem) removable.expected().getItem()).getBlock();
        helper.getLevel().setBlockAndUpdate(removable.pos(), expectedBlock.defaultBlockState());
        BlockPos conflict = missing.get(1).pos();
        helper.getLevel().setBlockAndUpdate(conflict, Blocks.STONE.defaultBlockState());

        UltimateStructurePlanner.Plan dismantle = UltimateStructurePlanner.planDismantle(
                helper.getLevel(), controllerPos, profile);
        helper.assertTrue(dismantle.valid(), "Unformed partial multiblock could not be dismantled: "
                + dismantle.error());
        helper.assertTrue(dismantle.placements().stream().anyMatch(value -> value.pos().equals(removable.pos())),
                "Matching partial-structure block was not added to the dismantle queue");
        helper.assertTrue(dismantle.placements().stream().noneMatch(value -> value.pos().equals(conflict)
                        || value.pos().equals(controllerPos) || helper.getLevel().getBlockEntity(value.pos()) != null),
                "Dismantle queue included a conflict, controller, or block entity");
        helper.succeed();
    }

    @GameTest(template = "empty_32x32x32", timeoutTicks = 200)
    public static void acceptsNativeGtceuMultiblockWithoutGseRegistration(GameTestHelper helper) {
        var definition = GTMultiMachines.STEAM_GRINDER;
        helper.assertTrue("gtceu".equals(definition.getId().getNamespace()),
                "Compatibility fixture is not a native GTCEu multiblock");
        GSEStructureTestUtils.placeMachine(helper, definition, CONTROLLER);
        BlockPos controllerPos = helper.absolutePos(CONTROLLER);

        var player = FakePlayerFactory.getMinecraft(helper.getLevel());
        UltimateTerminalWorldData data = UltimateTerminalWorldData.get(helper.getLevel().getServer());
        data.clear(player.getUUID());
        ItemStack terminal = new ItemStack(GSEBlocks.ULTIMATE_TERMINAL.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, terminal);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(controllerPos), Direction.UP,
                controllerPos, false);
        player.gameMode.useItemOn(player, helper.getLevel(), terminal, InteractionHand.MAIN_HAND, hit);
        helper.assertTrue(data.targetCount(player.getUUID()) == 1,
                "Ultimate Terminal rejected a native GTCEu controller");

        UltimateStructurePlanner.Plan plan = UltimateStructurePlanner.plan(
                helper.getLevel(), controllerPos, new TerminalBuildProfile(), false);
        helper.assertTrue(plan.valid(), "Native GTCEu structure blueprint failed: " + plan.error());
        helper.assertTrue(!plan.cells().isEmpty() && !plan.placements().isEmpty(),
                "Native GTCEu structure produced no ordinary-block blueprint");
        data.clear(player.getUUID());
        helper.succeed();
    }

    @GameTest(template = "empty_32x32x32", timeoutTicks = 200)
    public static void structureSizeChannelSelectsPatternWithoutHatches(GameTestHelper helper) {
        GSEStructureTestUtils.placeMachine(helper, GSEMachines.LARGE_HEAT_STORAGE_STEAM_FURNACE, CONTROLLER);
        BlockPos controllerPos = helper.absolutePos(CONTROLLER);
        TerminalBuildProfile largeProfile = new TerminalBuildProfile();
        UltimateStructurePlanner.Plan large = UltimateStructurePlanner.plan(
                helper.getLevel(), controllerPos, largeProfile, false);
        helper.assertTrue(large.valid(), "Default furnace structure plan failed: " + large.error());
        helper.assertTrue(large.structure().options().equals(java.util.List.of("7×7", "11×11", "15×15"))
                        && large.structure().selected() == 3,
                "Furnace structure-size channel did not expose the expected default variants");

        TerminalBuildProfile smallProfile = new TerminalBuildProfile();
        smallProfile.setChannel(UltimateTerminalStructureVariants.CHANNEL_ID, 1, 3);
        UltimateStructurePlanner.Plan small = UltimateStructurePlanner.plan(
                helper.getLevel(), controllerPos, smallProfile, false);
        helper.assertTrue(small.valid(), "Selected 7×7 furnace structure plan failed: " + small.error());
        helper.assertTrue(small.structure().selected() == 1 && small.cells().size() < large.cells().size(),
                "Selecting 7×7 did not reduce the planned furnace geometry");
        helper.assertTrue(small.placements().stream().noneMatch(value ->
                        value.stack().getItem() instanceof net.minecraft.world.item.BlockItem item
                                && item.getBlock() instanceof com.gregtechceu.gtceu.api.block.MetaMachineBlock),
                "No-hatch mode planned a machine part block");
        helper.succeed();
    }
}
