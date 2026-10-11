package com.hoshino.gregsteamexpansion.gametest;

import com.hoshino.gregsteamexpansion.GregSteamExpansion;
import com.hoshino.gregsteamexpansion.registry.GSEBlocks;
import com.hoshino.gregsteamexpansion.registry.GSEMachines;
import com.hoshino.gregsteamexpansion.terminal.UltimateStructurePlanner;
import com.hoshino.gregsteamexpansion.terminal.UltimateTerminalMessages;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

/** Exercise the real refresh loop and distinct recipients, including lifecycle changes. */
@GameTestHolder(GregSteamExpansion.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GSETerminalRefreshTests {
    private GSETerminalRefreshTests() {}

    @GameTest(template = "empty_32x32x32", timeoutTicks = 300)
    public static void staggeredRefreshKeepsPrivatePacketsAndFreshMaterials(GameTestHelper helper) {
        var fixture = new GSETerminalRefreshTestSupport(helper);
        var owners = new ArrayList<GSETerminalRefreshTestSupport.Owner>();
        try {
            for (int i = 0; i < 8; i++) {
                BlockPos relative = new BlockPos(4 + (i % 4) * 7, 16, 8 + (i / 4) * 14);
                GSEStructureTestUtils.placeMachine(helper, GSEMachines.STEAM_CRUSHER, relative);
                UUID id = UUID.nameUUIDFromBytes(("gse-refresh-regression-" + i).getBytes(StandardCharsets.UTF_8));
                var owner = fixture.add(id, List.of(helper.absolutePos(relative)), 0, i + 1);
                helper.assertTrue(owner.lastFull != null, "Initial preview was not sent immediately");
                owners.add(owner);
            }
            var plan = fixture.data().preview(owners.get(0).player).plan();
            var missing = plan.cells().stream().filter(cell ->
                    cell.status() == UltimateStructurePlanner.CellStatus.MISSING
                            && cell.expected().getItem() instanceof BlockItem).findFirst().orElseThrow();
            long required = owners.get(0).selectedMaterials.stream().mapToLong(
                    value -> value.required()).sum();
            class Driver {
                long stoppedPackets;

                void tick(int tick) {
                    try {
                        owners.forEach(GSETerminalRefreshTestSupport.Owner::flush);
                        if (tick == 35) helper.getLevel().setBlockAndUpdate(missing.pos(),
                                ((BlockItem) missing.expected().getItem()).getBlock().defaultBlockState());
                        if (tick == 50) {
                            var phases = new HashSet<Integer>();
                            for (var owner : owners) {
                                helper.assertTrue(!owner.connection.deltaTicks.isEmpty(),
                                        "Held terminal did not refresh within ten ticks");
                                phases.add(Math.floorMod(owner.connection.deltaTicks.get(0), 10));
                            }
                            helper.assertTrue(phases.size() == 8, "Eight recipients still share refresh tick phases");
                            helper.assertTrue(owners.get(0).selectedMaterials.stream().mapToLong(value ->
                                            value.required()).sum() == required - 1
                                            && owners.get(1).selectedMaterials.stream().mapToLong(value ->
                                            value.required()).sum() == required,
                                    "World changes were stale or crossed project boundaries");
                        }
                        if (tick == 55) {
                            stoppedPackets = owners.get(0).connection.packets;
                            owners.get(0).player.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
                        }
                        if (tick == 70) {
                            helper.assertTrue(owners.get(0).connection.packets == stoppedPackets,
                                    "A player without a held terminal kept receiving previews");
                            owners.get(0).player.setItemSlot(EquipmentSlot.MAINHAND,
                                    new ItemStack(GSEBlocks.ULTIMATE_TERMINAL.get()));
                        }
                        if (tick == 82) helper.assertTrue(owners.get(0).connection.packets > stoppedPackets,
                                "Reholding the terminal failed to resume automatic refreshes");
                        if (tick == 88) fixture.online(owners.get(7), false);
                        if (tick == 99) {
                            helper.assertTrue(!fixture.hasSession(owners.get(7)), "Offline session was retained");
                            fixture.online(owners.get(7), true);
                            UltimateTerminalMessages.forceFull(owners.get(7).player);
                            owners.get(7).flush();
                        }
                        if (tick == 100) {
                            long otherPackets = owners.get(2).connection.packets;
                            UltimateTerminalMessages.forceFull(owners.get(3).player);
                            owners.get(3).flush();
                            helper.assertTrue(owners.get(2).connection.packets == otherPackets,
                                    "An explicit request sent a snapshot to another recipient");
                        }
                        if (tick == 120) {
                            for (int i = 0; i < 7; i++) {
                                var ticks = owners.get(i).connection.deltaTicks;
                                int phase = Math.floorMod(ticks.get(0), 10);
                                helper.assertTrue(ticks.stream().allMatch(value -> Math.floorMod(value, 10) == phase),
                                        "A request or reholding changed an existing session's cadence");
                                for (int index = 1; index < ticks.size(); index++) {
                                    int gap = ticks.get(index) - ticks.get(index - 1);
                                    helper.assertTrue(gap >= 10 && gap % 10 == 0,
                                            "Automatic refresh interval changed or duplicated a preview");
                                }
                            }
                            cleanup();
                            helper.succeed();
                            return;
                        }
                        for (int i = 0; i < owners.size(); i++) owners.get(i).setInventory(i + 1 + tick % 40);
                        helper.runAfterDelay(1, () -> tick(tick + 1));
                    } catch (RuntimeException exception) {
                        cleanup();
                        throw exception;
                    }
                }

                void cleanup() {
                    helper.getLevel().setBlockAndUpdate(missing.pos(), Blocks.AIR.defaultBlockState());
                    fixture.close();
                }
            }
            new Driver().tick(0);
        } catch (RuntimeException exception) {
            fixture.close();
            throw exception;
        }
    }
}
