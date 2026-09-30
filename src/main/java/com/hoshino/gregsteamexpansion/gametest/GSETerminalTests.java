package com.hoshino.gregsteamexpansion.gametest;

import com.mojang.authlib.GameProfile;
import com.hoshino.gregsteamexpansion.GregSteamExpansion;
import com.hoshino.gregsteamexpansion.difficulty.GSEDifficultyConfig;
import com.hoshino.gregsteamexpansion.machine.multiblock.BoilerRoomMachine;
import com.hoshino.gregsteamexpansion.machine.multiblock.BoilerRoomModules;
import com.hoshino.gregsteamexpansion.registry.GSEBlocks;
import com.hoshino.gregsteamexpansion.registry.GSEMachines;
import com.hoshino.gregsteamexpansion.terminal.TerminalBuildProfile;
import com.hoshino.gregsteamexpansion.terminal.UltimateStructurePlanner;
import com.hoshino.gregsteamexpansion.terminal.UltimateTerminalConfig;
import com.hoshino.gregsteamexpansion.terminal.UltimateTerminalModuleProvider;
import com.hoshino.gregsteamexpansion.terminal.UltimateTerminalSnapshot;
import com.hoshino.gregsteamexpansion.terminal.UltimateTerminalMessages;
import com.hoshino.gregsteamexpansion.terminal.UltimateTerminalStructureVariants;
import com.hoshino.gregsteamexpansion.terminal.UltimateTerminalWorldData;

import com.gregtechceu.gtceu.common.data.machines.GTMultiMachines;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
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
        helper.assertTrue(GSEStructureTestUtils.checkPattern(controller),
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
        helper.assertTrue(GSEDifficultyConfig.externalModulesEnabled()
                        ? base.module().slots().size() == 5 && base.module().slots().stream()
                                .mapToInt(slot -> slot.options().size()).sum() == 9
                        : base.module().slots().isEmpty(),
                "The terminal did not expose all implemented boiler modules by installation face");

        BlockPos partialSoftener = BoilerRoomModules.local(
                controllerPos, machine.getFrontFacing(), -6, 2, -3);
        helper.getLevel().setBlockAndUpdate(partialSoftener,
                BoilerRoomModules.casing(BoilerRoomMachine.BRONZE_TIER).defaultBlockState());
        UltimateStructurePlanner.Plan partial = UltimateStructurePlanner.plan(
                helper.getLevel(), controllerPos, baseProfile, false);
        helper.assertTrue(partial.valid(), "Partial-module boiler-room plan failed: " + partial.error());
        if (!GSEDifficultyConfig.externalModulesEnabled()) {
            helper.assertTrue(partial.module().slots().isEmpty(),
                    "Config-disabled terminal exposed a partial external module");
            helper.succeed();
            return;
        }
        var left = partial.module().slots().stream().filter(slot -> slot.slot().equals("left")).findFirst().orElseThrow();
        helper.assertTrue(left.selected() == -1 && left.options().get(0).equals(
                        "gregsteamexpansion.machine.boiler_room.module.water_softener"),
                "The left slot did not expose the water softener in deterministic order");

        TerminalBuildProfile moduleProfile = new TerminalBuildProfile();
        moduleProfile.setChannel(UltimateTerminalModuleProvider.CHANNEL_ID, 0, 0);
        UltimateStructurePlanner.Plan withModule = UltimateStructurePlanner.plan(
                helper.getLevel(), controllerPos, moduleProfile, false);
        helper.assertTrue(withModule.valid(), "Selected boiler-room module plan failed: " + withModule.error());
        helper.assertTrue(withModule.module().slots().stream().anyMatch(slot -> slot.slot().equals("left")
                        && slot.selected() == 0), "The old single-module selection did not resolve to the left slot");
        helper.assertTrue(moduleProfile.migrateModules(machine.legacyTerminalModules())
                        && moduleProfile.moduleSelections().get("left").equals(BoilerRoomModules.SOFTENER),
                "The old boiler module index did not migrate to its stable identity");
        helper.assertTrue(withModule.placements().size() == partial.placements().size() + 61
                        && withModule.cells().size() == partial.cells().size() + machine.terminalModules().stream()
                                .filter(module -> module.id().equals(BoilerRoomModules.SOFTENER))
                                .findFirst().orElseThrow().requirements().size() - 1,
                "Selecting the partial water softener did not reserve its manual port or count ordinary blocks exactly");
        helper.succeed();
    }

    @GameTest(template = "empty_32x32x32", timeoutTicks = 200)
    public static void moduleSelectionsPersistByIdAndRespectBoilerFaces(GameTestHelper helper) {
        BoilerRoomMachine machine = GSEStructureTestUtils.placeMachine(
                helper, GSEMachines.BOILER_ROOM_BRONZE, new BlockPos(16, 12, 8));
        if (!GSEDifficultyConfig.externalModulesEnabled()) {
            helper.assertTrue(machine.terminalModules().isEmpty(), "Disabled modules remained selectable");
            helper.succeed();
            return;
        }
        BlockPos pos = machine.getPos();
        // The old boiler catalogue contained only physically present modules.
        var oldRight = BoilerRoomModules.terminalRequirements(pos, machine.getFrontFacing(),
                BoilerRoomMachine.BRONZE_TIER, BoilerRoomModules.COOLING_TANK).stream()
                .filter(requirement -> !requirement.air()).findFirst().orElseThrow();
        helper.getLevel().setBlockAndUpdate(oldRight.pos(), oldRight.block().defaultBlockState());
        var legacyCatalogue = machine.legacyTerminalModules();
        helper.assertTrue(!legacyCatalogue.isEmpty() && legacyCatalogue.get(0).slot().equals("right"),
                "Legacy fixture did not reproduce a filtered right-only catalogue");
        TerminalBuildProfile profile = new TerminalBuildProfile();
        profile.setChannel(UltimateTerminalModuleProvider.CHANNEL_ID, 0, 8);
        helper.assertTrue(profile.migrateModules(legacyCatalogue)
                        && profile.moduleSelections().get("right").equals(legacyCatalogue.get(0).id()),
                "Legacy index zero incorrectly became the first module of the new full catalogue");
        var player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(
                UUID.fromString("13f14af6-bbb5-44cc-a890-7d7f247d7bb8"), "[GSE legacy modules]"));
        var data = UltimateTerminalWorldData.load(new CompoundTag());
        helper.assertTrue(data.addTarget(player, helper.getLevel().dimension(), pos), "Legacy target was rejected");
        CompoundTag saved = data.save(new CompoundTag());
        TerminalBuildProfile legacyDefault = new TerminalBuildProfile();
        legacyDefault.setChannel(UltimateTerminalModuleProvider.CHANNEL_ID, 0, 8);
        saved.getList("projects", net.minecraft.nbt.Tag.TAG_COMPOUND).getCompound(0).getCompound("data")
                .put("defaultProfile", legacyDefault.save());
        data = UltimateTerminalWorldData.load(saved);
        helper.assertTrue(data.preview(player).profile().moduleSelections().equals(
                        java.util.Map.of("right", legacyCatalogue.get(0).id())),
                "Legacy shared default did not migrate into the boiler template");
        helper.getLevel().setBlockAndUpdate(oldRight.pos(), Blocks.AIR.defaultBlockState());

        profile.setModule("left", BoilerRoomModules.SOFTENER);
        profile.setModule("right", BoilerRoomModules.FORCE_COOLING);
        profile.setModule("back", BoilerRoomModules.FORCED_DRAFT);
        profile.setModule("down", BoilerRoomModules.ACID_RECOVERY);
        profile.setModule("top", BoilerRoomModules.AUTO_ACID);
        TerminalBuildProfile restored = TerminalBuildProfile.load(profile.save());
        helper.assertTrue(restored.moduleSelections().equals(profile.moduleSelections())
                        && restored.copy().moduleSelections().equals(profile.moduleSelections()),
                "Five stable module identities did not survive save/load and copying");
        var plan = UltimateStructurePlanner.plan(helper.getLevel(), pos, restored, false);
        helper.assertTrue(plan.valid() && plan.module().slots().stream().allMatch(slot -> slot.selected() >= 0),
                "Five compatible boiler faces did not form one blueprint: " + plan.error());
        helper.assertTrue(plan.placements().stream().map(UltimateStructurePlanner.Placement::pos).distinct().count()
                        == plan.placements().size(), "Module composition duplicated materials or placements");
        helper.assertTrue(plan.placements().stream().noneMatch(placement ->
                        ((net.minecraft.world.item.BlockItem) placement.stack().getItem()).getBlock()
                                instanceof com.gregtechceu.gtceu.api.block.MetaMachineBlock),
                "A module functional port bypassed the terminal's no-hatch policy");
        restored.setModule("left", BoilerRoomModules.ATOMIZER);
        helper.assertTrue(restored.moduleSelections().size() == 5
                        && restored.moduleSelections().get("left").equals(BoilerRoomModules.ATOMIZER),
                "Selecting a same-face module did not replace the previous choice");
        restored.setModule("left", null);
        helper.assertTrue(restored.moduleSelections().size() == 4
                        && !restored.moduleSelections().containsKey("left"), "Clearing one face lost other module choices");
        helper.assertTrue(UltimateTerminalConfig.parseChannel("module.left=minecraft:stone,minecraft:glass") == null,
                "A user material channel shadowed an installation-face channel");
        GSEStructureTestUtils.placeMachine(helper, GSEMachines.LARGE_STEAM_BLAST_FURNACE, new BlockPos(16, 12, 8));
        helper.assertTrue(data.preview(player).profile().moduleSelections().equals(
                        java.util.Map.of("back", "hot_blast_stoves")),
                "Migrating the boiler template rewrote the legacy default for other machine types");
        CompoundTag defaultAfter = data.save(new CompoundTag()).getList("projects", net.minecraft.nbt.Tag.TAG_COMPOUND)
                .getCompound(0).getCompound("data").getCompound("defaultProfile");
        helper.assertTrue(TerminalBuildProfile.load(defaultAfter).channelSelection("module") == 0,
                "Per-machine migration consumed the shared legacy default index");
        helper.succeed();
    }

    @GameTest(template = "empty_32x32x32", timeoutTicks = 200)
    public static void combinedBlastFurnaceModulesBuildAndResumeWithAirGuards(GameTestHelper helper) {
        var machine = GSEStructureTestUtils.placeMachine(helper, GSEMachines.LARGE_STEAM_BLAST_FURNACE,
                new BlockPos(16, 3, 8));
        if (!GSEDifficultyConfig.externalModulesEnabled()) {
            helper.assertTrue(((UltimateTerminalModuleProvider) machine).terminalModules().isEmpty(),
                    "Disabled blast-furnace modules remained selectable");
            helper.succeed();
            return;
        }
        var player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(
                UUID.fromString("b73379aa-173b-4813-82fc-6f62b4135b8b"), "[GSE combined terminal]"));
        player.getAbilities().instabuild = true;
        player.getInventory().clearContent();
        UltimateTerminalWorldData original = UltimateTerminalWorldData.load(new CompoundTag());
        helper.assertTrue(original.addTarget(player, helper.getLevel().dimension(), machine.getPos()),
                "Combined-module target was rejected");
        original.setChannel(player, "module.back", 0);
        original.setChannel(player, "module.top", 0);
        var preview = original.preview(player);
        var plan = preview.plan();
        helper.assertTrue(plan.valid() && plan.module().slots().size() == 2
                        && plan.module().slots().stream().allMatch(slot -> slot.selected() == 0),
                "Rear hot-blast stoves and top charge tower did not compose: " + plan.error());
        helper.assertTrue(!plan.requiredAir().isEmpty(), "Combined blueprint discarded strict air requirements");
        helper.assertTrue(preview.profile().moduleSelections().equals(java.util.Map.of(
                        "back", "hot_blast_stoves", "top", "high_charge_tower")),
                "Server channel requests did not persist stable module IDs");
        helper.assertTrue(original.start(player), "Combined creative build failed to start");
        original.tickPlayer(player, 7);
        UltimateTerminalWorldData restored = UltimateTerminalWorldData.load(original.save(new CompoundTag()));
        for (int i = 0; i < 64 && restored.state(player.getUUID()) != UltimateTerminalWorldData.JobState.COMPLETE; i++) {
            restored.tickPlayer(player, 256);
        }
        helper.assertTrue(restored.state(player.getUUID()) == UltimateTerminalWorldData.JobState.COMPLETE,
                "Serialized combined build did not resume and complete");
        helper.assertTrue(plan.placements().stream().allMatch(placement ->
                        helper.getLevel().getBlockState(placement.pos()).getBlock().asItem() == placement.stack().getItem()),
                "Combined job omitted body or module blocks");
        helper.assertTrue(player.getInventory().isEmpty() && restored.pendingCount(player.getUUID()) == 0,
                "Combined creative job generated material refunds");

        var missing = plan.placements().stream().limit(2).toList();
        missing.forEach(placement -> helper.getLevel().setBlockAndUpdate(placement.pos(), Blocks.AIR.defaultBlockState()));
        helper.assertTrue(restored.start(player), "Combined repair fixture could not start");
        restored.tickPlayer(player, 1);
        UltimateTerminalWorldData resumed = UltimateTerminalWorldData.load(restored.save(new CompoundTag()));
        BlockPos air = BlockPos.of(plan.requiredAir().get(0));
        helper.getLevel().setBlockAndUpdate(air, Blocks.STONE.defaultBlockState());
        resumed.tickPlayer(player, 256);
        helper.assertTrue(resumed.state(player.getUUID()) == UltimateTerminalWorldData.JobState.FAILED,
                "An obstruction in persisted strict air did not fail resumed work");
        helper.assertTrue(missing.stream().allMatch(placement -> helper.getLevel().isEmptyBlock(placement.pos()))
                        && helper.getLevel().getBlockState(air).is(Blocks.STONE),
                "Air-guard failure did not roll back own work or removed the external obstruction");
        resumed.clear(player.getUUID());
        helper.succeed();
    }

    @GameTest(template = "empty_32x32x32", timeoutTicks = 200)
    public static void moduleLayoutsRejectOverlapsAndBoundNetworkChoices(GameTestHelper helper) throws Exception {
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        var first = new UltimateTerminalModuleProvider.Module("first", "test.first", "back",
                java.util.List.of(UltimateTerminalModuleProvider.Requirement.solid(pos, Blocks.STONE)));
        TerminalBuildProfile saturated = new TerminalBuildProfile();
        for (int i = 0; i < UltimateTerminalConfig.MAX_CHANNEL_OPTIONS; i++) saturated.setModule("slot_" + i, "old_" + i);
        saturated.setChannel("module", 0, 0);
        helper.assertTrue(!saturated.migrateModules(java.util.List.of(first))
                        && saturated.moduleSelections().size() == UltimateTerminalConfig.MAX_CHANNEL_OPTIONS,
                "Legacy migration overflowed the persistent module slot limit");
        var second = new UltimateTerminalModuleProvider.Module("second", "test.second", "top",
                java.util.List.of(UltimateTerminalModuleProvider.Requirement.air(pos)));
        var merge = UltimateStructurePlanner.class.getDeclaredMethod("appendModules", ServerLevel.class,
                java.util.List.class, java.util.Set.class, java.util.List.class, java.util.List.class);
        merge.setAccessible(true);
        String overlap = (String) merge.invoke(null, helper.getLevel(), java.util.List.of(first, second),
                java.util.Set.of(), new java.util.ArrayList<>(), new java.util.ArrayList<>());
        helper.assertTrue(overlap != null && overlap.startsWith("module_overlap"),
                "Conflicting solid/air module requirements were silently deduplicated");
        overlap = (String) merge.invoke(null, helper.getLevel(), java.util.List.of(first), java.util.Set.of(pos),
                new java.util.ArrayList<>(), new java.util.ArrayList<>());
        helper.assertTrue(overlap != null && overlap.startsWith("module_overlap"),
                "A module could overwrite a reserved body position");
        var body = new java.util.ArrayList<UltimateStructurePlanner.Cell>();
        body.add(new UltimateStructurePlanner.Cell(pos, Blocks.STONE.asItem().getDefaultInstance(),
                UltimateStructurePlanner.CellStatus.MISSING));
        var mergedPlacements = new java.util.ArrayList<UltimateStructurePlanner.Placement>();
        mergedPlacements.add(new UltimateStructurePlanner.Placement(pos, Blocks.STONE.asItem().getDefaultInstance()));
        helper.assertTrue(merge.invoke(null, helper.getLevel(), java.util.List.of(first), java.util.Set.of(pos),
                        mergedPlacements, body) == null && mergedPlacements.size() == 1 && body.size() == 1,
                "A compatible body junction was rejected or counted twice");
        var duplicate = new UltimateTerminalModuleProvider.Module("second", "test.second", "top",
                first.requirements());
        overlap = (String) merge.invoke(null, helper.getLevel(), java.util.List.of(first, duplicate), java.util.Set.of(),
                new java.util.ArrayList<>(), new java.util.ArrayList<>());
        helper.assertTrue(overlap != null && overlap.startsWith("module_overlap"),
                "Two exclusive modules could share a physical block");

        var write = UltimateTerminalMessages.class.getDeclaredMethod("writeModule",
                net.minecraft.network.FriendlyByteBuf.class, UltimateTerminalSnapshot.ModuleInfo.class);
        var read = UltimateTerminalMessages.class.getDeclaredMethod("readModule", net.minecraft.network.FriendlyByteBuf.class);
        write.setAccessible(true);
        read.setAccessible(true);
        var buffer = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        try {
            var choices = new UltimateTerminalSnapshot.ModuleInfo(java.util.List.of(
                    new UltimateTerminalSnapshot.ModuleSlotInfo("back", 0, java.util.List.of("test.first")),
                    new UltimateTerminalSnapshot.ModuleSlotInfo("top", -2, java.util.List.of())));
            write.invoke(null, buffer, choices);
            helper.assertTrue(choices.equals(read.invoke(null, buffer)), "Grouped module snapshot lost selections in transit");
            buffer.clear();
            buffer.writeVarInt(UltimateTerminalConfig.MAX_CHANNEL_OPTIONS * 2 + 1);
            try {
                read.invoke(null, buffer);
                helper.fail("An oversized module snapshot was accepted");
            } catch (java.lang.reflect.InvocationTargetException exception) {
                helper.assertTrue(exception.getCause() instanceof IllegalArgumentException,
                        "Oversized module snapshot failed for an unexpected reason");
            }
        } finally {
            buffer.release();
        }
        var machine = GSEStructureTestUtils.placeMachine(helper, GSEMachines.LARGE_STEAM_BLAST_FURNACE, CONTROLLER);
        TerminalBuildProfile unknown = new TerminalBuildProfile();
        unknown.setModule("back", "removed_module");
        var invalid = UltimateStructurePlanner.plan(helper.getLevel(), machine.getPos(), unknown, false);
        helper.assertTrue(!GSEDifficultyConfig.externalModulesEnabled() || !invalid.valid()
                        && invalid.error().startsWith("module_unavailable"), "Removed module ID silently selected a different module");
        helper.assertTrue(TerminalBuildProfile.load(unknown.save()).moduleSelections().equals(unknown.moduleSelections()),
                "Unknown or disabled module identity was discarded instead of retained");
        var cokeOven = GSEStructureTestUtils.placeMachine(helper, GSEMachines.LARGE_COKE_OVEN, CONTROLLER);
        TerminalBuildProfile cokeProfile = new TerminalBuildProfile();
        ((UltimateTerminalModuleProvider) cokeOven).terminalModules().forEach(module ->
                cokeProfile.setModule(module.slot(), module.id()));
        var cokePlan = UltimateStructurePlanner.plan(helper.getLevel(), cokeOven.getPos(), cokeProfile, false);
        helper.assertTrue(cokePlan.valid(), "Coke oven dry-quench and foundation modules did not compose: " + cokePlan.error());
        helper.assertTrue(!GSEDifficultyConfig.externalModulesEnabled() || cokePlan.module().slots().size() == 2
                        && cokePlan.module().slots().stream().allMatch(slot -> slot.selected() == 0),
                "Coke oven module choices lost one installation face");
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
