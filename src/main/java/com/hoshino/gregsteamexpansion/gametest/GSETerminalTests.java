package com.hoshino.gregsteamexpansion.gametest;

import com.hoshino.gregsteamexpansion.GregSteamExpansion;
import com.hoshino.gregsteamexpansion.registry.GSEBlocks;
import com.hoshino.gregsteamexpansion.registry.GSEMachines;
import com.hoshino.gregsteamexpansion.terminal.TerminalBuildProfile;
import com.hoshino.gregsteamexpansion.terminal.UltimateStructurePlanner;
import com.hoshino.gregsteamexpansion.terminal.UltimateTerminalWorldData;

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
        profile.toggleHatches();
        profile.changePart(casing, 25, 20);

        TerminalBuildProfile restored = TerminalBuildProfile.load(profile.save());
        helper.assertTrue(restored.repeatCount() == 64, "Repeat count was not clamped to 64");
        helper.assertTrue(restored.coilTier() == 16, "Coil tier was not clamped to 16");
        helper.assertTrue(!restored.buildHatches(), "Build-parts choice did not survive NBT");
        helper.assertTrue(restored.partTargets().getOrDefault(casing, 0) == 20,
                "Candidate target did not survive NBT");

        CompoundTag legacy = new CompoundTag();
        TerminalBuildProfile migrated = TerminalBuildProfile.load(legacy);
        helper.assertTrue(migrated.buildHatches(), "A legacy profile must default build-parts to enabled");
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
}
