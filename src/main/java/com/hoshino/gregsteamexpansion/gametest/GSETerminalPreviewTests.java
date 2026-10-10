package com.hoshino.gregsteamexpansion.gametest;

import com.hoshino.gregsteamexpansion.GregSteamExpansion;
import com.hoshino.gregsteamexpansion.registry.GSEMachines;
import com.hoshino.gregsteamexpansion.terminal.TerminalBuildProfile;
import com.hoshino.gregsteamexpansion.terminal.UltimateStructurePlanner;
import com.hoshino.gregsteamexpansion.terminal.UltimateTerminalMode;
import com.hoshino.gregsteamexpansion.terminal.UltimateTerminalWorldData;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Preview reuse must preserve per-target materials and refresh every request. */
@GameTestHolder(GregSteamExpansion.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GSETerminalPreviewTests {
    private GSETerminalPreviewTests() {}

    @GameTest(template = "empty_32x32x32", timeoutTicks = 200)
    public static void selectedPreviewKeepsBatchMaterialsAndWorldChangesFresh(GameTestHelper helper) {
        BlockPos first = new BlockPos(8, 16, 8);
        BlockPos second = new BlockPos(23, 16, 23);
        GSEStructureTestUtils.placeMachine(helper, GSEMachines.STEAM_CRUSHER, first);
        GSEStructureTestUtils.placeMachine(helper, GSEMachines.STEAM_CRUSHER, second);
        first = helper.absolutePos(first);
        second = helper.absolutePos(second);
        var player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(
                UUID.fromString("6dd77a42-816b-4d27-a9d2-b96200000001"), "[GSE preview regression]"));
        player.getInventory().clearContent();
        player.getAbilities().instabuild = false;
        var data = UltimateTerminalWorldData.load(new CompoundTag());
        data.toggleAE(player.getUUID());
        helper.assertTrue(data.preview(player).error().equals("no_target"), "Empty project preview changed");
        data.addTarget(player, helper.getLevel().dimension(), first);
        var single = data.preview(player);
        helper.assertTrue(materials(single.selectedMaterials()).equals(materials(single.batchMaterials())),
                "Single-target selected and batch materials differ");
        data.addTarget(player, helper.getLevel().dimension(), second);
        var initial = data.preview(player);
        helper.assertTrue(initial.selectedTarget() == 1 && initial.plan().valid(), "Second target was not selected");
        var placed = initial.plan().cells().stream()
                .filter(cell -> cell.status() == UltimateStructurePlanner.CellStatus.MISSING)
                .findFirst().orElseThrow();
        helper.getLevel().setBlockAndUpdate(placed.pos(), ((BlockItem) placed.expected().getItem())
                .getBlock().defaultBlockState());
        var changed = data.preview(player);
        helper.assertTrue(changed.plan().cells().stream().anyMatch(cell -> cell.pos().equals(placed.pos())
                        && cell.status() == UltimateStructurePlanner.CellStatus.SATISFIED),
                "A later preview reused stale world status");
        helper.assertTrue(total(changed.batchMaterials()) == total(initial.batchMaterials()) - 1,
                "Selected-target placement changed another target's batch requirements");

        data.selectTarget(player, 0);
        var switched = data.preview(player);
        helper.assertTrue(switched.selectedTarget() == 0
                        && materials(switched.batchMaterials()).equals(materials(changed.batchMaterials()))
                        && !materials(switched.selectedMaterials()).equals(materials(changed.selectedMaterials())),
                "Changing selection changed batch materials or retained the previous selected plan");
        var needed = switched.selectedMaterials().get(0);
        player.getInventory().add(needed.stack().copyWithCount(1));
        var stocked = data.preview(player);
        helper.assertTrue(stocked.selectedMaterials().stream().anyMatch(view ->
                        view.stack().getItem() == needed.stack().getItem() && view.inventory() == 1),
                "Preview did not refresh inventory availability");

        // Different world states on the two targets make accidental reuse for every target observable.
        for (UltimateTerminalMode mode : UltimateTerminalMode.values()) {
            var firstPlan = mode == UltimateTerminalMode.DISMANTLE
                    ? UltimateStructurePlanner.planDismantle(helper.getLevel(), first, new TerminalBuildProfile())
                    : UltimateStructurePlanner.plan(helper.getLevel(), first, new TerminalBuildProfile(),
                            mode == UltimateTerminalMode.UPGRADE);
            var secondPlan = mode == UltimateTerminalMode.DISMANTLE
                    ? UltimateStructurePlanner.planDismantle(helper.getLevel(), second, new TerminalBuildProfile())
                    : UltimateStructurePlanner.plan(helper.getLevel(), second, new TerminalBuildProfile(),
                            mode == UltimateTerminalMode.UPGRADE);
            var preview = data.preview(player);
            helper.assertTrue(firstPlan.valid() && secondPlan.valid() && preview.plan().valid()
                            && materials(preview.selectedMaterials()).equals(requirements(List.of(firstPlan)))
                            && materials(preview.batchMaterials()).equals(requirements(List.of(firstPlan, secondPlan))),
                    "Preview materials disagree with independent target plans in " + mode);
            data.cycleMode(player.getUUID());
        }

        data.selectTarget(player, 1);
        CompoundTag saved = data.save(new CompoundTag());
        var project = GSETerminalTransactionTests.project(saved, player.getUUID());
        BlockPos unloaded = new BlockPos(900_000, 80, 900_000);
        project.getList("targets", Tag.TAG_COMPOUND).getCompound(1).putLong("pos", unloaded.asLong());
        var restored = UltimateTerminalWorldData.load(saved);
        var absent = restored.preview(player);
        helper.assertTrue(absent.error().equals("controller_unloaded") && absent.selectedMaterials().isEmpty()
                        && materials(absent.batchMaterials()).equals(materials(stocked.selectedMaterials()))
                        && !helper.getLevel().hasChunkAt(unloaded),
                "Unloaded selected target erased another target's materials or forced a chunk load");
        helper.getLevel().setBlockAndUpdate(placed.pos(), Blocks.AIR.defaultBlockState());
        player.getInventory().clearContent();
        helper.succeed();
    }

    private static long total(List<UltimateTerminalWorldData.MaterialView> views) {
        return views.stream().mapToLong(UltimateTerminalWorldData.MaterialView::required).sum();
    }

    private static Map<Item, Long> materials(List<UltimateTerminalWorldData.MaterialView> views) {
        Map<Item, Long> result = new HashMap<>();
        for (var view : views) result.merge(view.stack().getItem(), view.required(), Long::sum);
        return result;
    }

    private static Map<Item, Long> requirements(List<UltimateStructurePlanner.Plan> plans) {
        Map<Item, Long> result = new HashMap<>();
        for (var plan : plans) {
            for (var placement : plan.placements()) result.merge(placement.stack().getItem(), 1L, Long::sum);
        }
        return result;
    }
}
