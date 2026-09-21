package com.hoshino.gregsteamexpansion.gametest;

import com.mojang.authlib.GameProfile;
import com.hoshino.gregsteamexpansion.GregSteamExpansion;
import com.hoshino.gregsteamexpansion.machine.multiblock.BoilerRoomMachine;
import com.hoshino.gregsteamexpansion.machine.multiblock.BoilerRoomModules;
import com.hoshino.gregsteamexpansion.registry.GSEBlocks;
import com.hoshino.gregsteamexpansion.registry.GSEMachines;
import com.hoshino.gregsteamexpansion.terminal.TerminalBuildProfile;
import com.hoshino.gregsteamexpansion.terminal.UltimateStructurePlanner;
import com.hoshino.gregsteamexpansion.terminal.UltimateTerminalConfig;
import com.hoshino.gregsteamexpansion.terminal.UltimateTerminalModuleProvider;
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

import java.util.UUID;

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
        profile.changeChannel("glass", 100, UltimateTerminalConfig.MAX_CHANNEL_INDEX);

        TerminalBuildProfile restored = TerminalBuildProfile.load(profile.save());
        helper.assertTrue(restored.repeatCount() == 64, "Repeat count was not clamped to 64");
        helper.assertTrue(restored.coilTier() == 16, "Coil tier was not clamped to 16");
        helper.assertTrue(!restored.buildHatches(), "Ultimate Terminal unexpectedly enabled hatch placement");
        helper.assertTrue(restored.partTargets().getOrDefault(casing, 0) == 20,
                "Candidate target did not survive NBT");
        helper.assertTrue(restored.channelSelection("glass") == 63,
                "Configured selection channel did not survive NBT");
        helper.assertTrue(restored.channelSelection("unset") == -1,
                "Unconfigured channel did not retain the -1 automatic sentinel");
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
        helper.assertTrue(UltimateTerminalConfig.parseChannel(
                "module=minecraft:stone,minecraft:glass") == null,
                "Reserved built-in module channel ID was accepted");
        String sixtyFourOptions = "limit=" + java.util.stream.IntStream.range(0, 64)
                .mapToObj(index -> "gregsteamexpansion:test_" + index)
                .collect(java.util.stream.Collectors.joining(","));
        helper.assertTrue(UltimateTerminalConfig.parseChannel(sixtyFourOptions) != null,
                "A channel with exactly 64 zero-based options was rejected");
        helper.assertTrue(UltimateTerminalConfig.parseChannel(
                sixtyFourOptions + ",gregsteamexpansion:test_64") == null,
                "A channel with more than 64 options was not rejected");

        CompoundTag legacy = new CompoundTag();
        CompoundTag legacyChannels = new CompoundTag();
        legacyChannels.putInt("glass", 1);
        legacy.put("channels", legacyChannels);
        TerminalBuildProfile migrated = TerminalBuildProfile.load(legacy);
        helper.assertTrue(!migrated.buildHatches(), "A legacy profile re-enabled terminal hatch placement");
        helper.assertTrue(migrated.channelSelection("glass") == 0,
                "Legacy one-based channel selection was not migrated to zero-based index 0");
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

        BlockPos coilController = helper.absolutePos(new BlockPos(8, 16, 8));
        GSEStructureTestUtils.placeMachine(helper, GTMultiMachines.ELECTRIC_BLAST_FURNACE,
                new BlockPos(8, 16, 8));
        UltimateStructurePlanner.Plan coilPlan = UltimateStructurePlanner.plan(
                helper.getLevel(), coilController, new TerminalBuildProfile(), false);
        helper.assertTrue(coilPlan.valid(), "Native GTCEu coil structure blueprint failed: "
                + coilPlan.error());
        helper.assertTrue(coilPlan.channels().stream().anyMatch(channel -> channel.id().equals("coil")),
                "Native GTCEu coil structure did not expose the coil dropdown");
        helper.assertTrue(coilPlan.candidates().stream().noneMatch(candidate -> candidate.configurable()
                        && candidate.stack().getItem() instanceof net.minecraft.world.item.BlockItem item
                        && item.getBlock() instanceof com.gregtechceu.gtceu.common.block.CoilBlock),
                "Uniform heating coils leaked into the block-quota candidates");
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
                        && large.structure().selected() == 2,
                "Furnace structure-size channel did not expose the expected default variants");

        TerminalBuildProfile smallProfile = new TerminalBuildProfile();
        smallProfile.setChannel(UltimateTerminalStructureVariants.CHANNEL_ID, 0, 2);
        UltimateStructurePlanner.Plan small = UltimateStructurePlanner.plan(
                helper.getLevel(), controllerPos, smallProfile, false);
        helper.assertTrue(small.valid(), "Selected 7×7 furnace structure plan failed: " + small.error());
        helper.assertTrue(small.structure().selected() == 0 && small.cells().size() < large.cells().size(),
                "Selecting 7×7 did not reduce the planned furnace geometry");
        helper.assertTrue(small.placements().stream().noneMatch(value ->
                        value.stack().getItem() instanceof net.minecraft.world.item.BlockItem item
                                && item.getBlock() instanceof com.gregtechceu.gtceu.api.block.MetaMachineBlock),
                "No-hatch mode planned a machine part block");
        helper.succeed();
    }

    @GameTest(template = "empty_32x32x32", timeoutTicks = 200)
    public static void moduleChannelUsesImplementationOrderAndPlansModule(GameTestHelper helper) {
        BoilerRoomMachine machine = GSEStructureTestUtils.placeMachine(
                helper, GSEMachines.BOILER_ROOM_BRONZE, CONTROLLER);
        BlockPos controllerPos = helper.absolutePos(CONTROLLER);
        TerminalBuildProfile baseProfile = new TerminalBuildProfile();
        UltimateStructurePlanner.Plan base = UltimateStructurePlanner.plan(
                helper.getLevel(), controllerPos, baseProfile, false);
        helper.assertTrue(base.valid(), "Base boiler-room plan failed: " + base.error());
        helper.assertTrue(base.module().selected() == UltimateTerminalConfig.AUTO_CHANNEL_SELECTION
                        && base.module().options().isEmpty(),
                "An absent boiler-room module was exposed by the terminal");

        BlockPos partialSoftener = BoilerRoomModules.local(
                controllerPos, machine.getFrontFacing(), -6, 2, -3);
        helper.getLevel().setBlockAndUpdate(partialSoftener,
                BoilerRoomModules.casing(BoilerRoomMachine.BRONZE_TIER).defaultBlockState());
        UltimateStructurePlanner.Plan partial = UltimateStructurePlanner.plan(
                helper.getLevel(), controllerPos, baseProfile, false);
        helper.assertTrue(partial.valid(), "Partial-module boiler-room plan failed: " + partial.error());
        helper.assertTrue(partial.module().selected() == UltimateTerminalConfig.AUTO_CHANNEL_SELECTION
                        && partial.module().options().equals(java.util.List.of(
                                "gregsteamexpansion.machine.boiler_room.module.water_softener")),
                "The partially built water softener was not exposed at channel value 0");

        TerminalBuildProfile moduleProfile = new TerminalBuildProfile();
        moduleProfile.setChannel(UltimateTerminalModuleProvider.CHANNEL_ID, 0, 0);
        UltimateStructurePlanner.Plan withModule = UltimateStructurePlanner.plan(
                helper.getLevel(), controllerPos, moduleProfile, false);
        helper.assertTrue(withModule.valid(), "Selected boiler-room module plan failed: " + withModule.error());
        helper.assertTrue(withModule.module().selected() == 0,
                "The first implemented module did not occupy channel value 0");
        helper.assertTrue(withModule.placements().size() == partial.placements().size() + 62
                        && withModule.cells().size() == partial.cells().size() + 63,
                "Selecting the partial water softener did not append its exact 63-block blueprint");
        helper.succeed();
    }

    @GameTest(template = "empty_32x32x32", timeoutTicks = 200)
    public static void creativePlayerBuildsWithoutInventoryOrAeMaterials(GameTestHelper helper) {
        GSEStructureTestUtils.placeMachine(helper, GSEMachines.STEAM_CRUSHER, CONTROLLER);
        BlockPos controllerPos = helper.absolutePos(CONTROLLER);
        UltimateStructurePlanner.Plan plan = UltimateStructurePlanner.plan(
                helper.getLevel(), controllerPos, new TerminalBuildProfile(), false);
        helper.assertTrue(plan.valid() && !plan.placements().isEmpty(),
                "Creative-material fixture did not produce a buildable terminal plan: " + plan.error());

        var player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(
                UUID.fromString("4959907e-f7ba-4597-a069-0e11c21bcbd4"), "[GSE creative terminal]"));
        player.getAbilities().instabuild = true;
        player.getInventory().clearContent();
        UltimateTerminalWorldData data = UltimateTerminalWorldData.get(helper.getLevel().getServer());
        data.clear(player.getUUID());
        helper.assertTrue(data.addTarget(player, helper.getLevel().dimension(), controllerPos),
                "Creative player could not add the terminal target");
        var preview = data.preview(player);
        helper.assertTrue(preview.unlimitedMaterials()
                        && preview.selectedMaterials().stream().allMatch(material ->
                                material.inventory() == 0 && material.network() == 0),
                "Creative terminal preview still depended on inventory or AE material counts");
        helper.assertTrue(data.start(player),
                "Creative player with an empty inventory could not start the terminal build");

        for (int i = 0; i < 64 && data.state(player.getUUID()) !=
                UltimateTerminalWorldData.JobState.COMPLETE; i++) {
            data.tickPlayer(player, 256);
        }
        helper.assertTrue(data.state(player.getUUID()) == UltimateTerminalWorldData.JobState.COMPLETE,
                "Creative terminal build did not complete without escrowed materials");
        helper.assertTrue(plan.placements().stream().allMatch(placement ->
                        helper.getLevel().getBlockState(placement.pos()).getBlock().asItem()
                                == placement.stack().getItem()),
                "Creative terminal build did not place every planned block");
        helper.assertTrue(player.getInventory().isEmpty() && data.pendingCount(player.getUUID()) == 0,
                "Creative terminal build consumed or generated inventory materials");
        data.clear(player.getUUID());
        helper.succeed();
    }
}
