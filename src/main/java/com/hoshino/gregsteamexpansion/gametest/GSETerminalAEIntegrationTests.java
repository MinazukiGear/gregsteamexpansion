package com.hoshino.gregsteamexpansion.gametest;

import com.hoshino.gregsteamexpansion.terminal.UltimateTerminalWorldData;
import com.hoshino.gregsteamexpansion.terminal.UltimateTerminalMessages;
import com.hoshino.gregsteamexpansion.registry.GSEMachines;
import com.hoshino.gregsteamexpansion.terminal.compat.UltimateTerminalAECompat;

import appeng.api.config.Actionable;
import appeng.api.implementations.blockentities.IWirelessAccessPoint;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.IStorageProvider;
import appeng.api.storage.MEStorage;
import appeng.api.networking.IGrid;
import appeng.api.storage.StorageCells;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.items.tools.powered.WirelessTerminalItem;
import appeng.me.helpers.PlayerSource;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.hoshino.gregsteamexpansion.gametest.GSETerminalTransactionTests.*;

/** Manually registered so core-only runtimes never reflect AE2 API signatures. */
@PrefixGameTestTemplate(false)
public final class GSETerminalAEIntegrationTests {
    private GSETerminalAEIntegrationTests() {}

    @GameTest(templateNamespace = "gregsteamexpansion", template = "empty_32x32x32", timeoutTicks = 250)
    public static void realAeRefreshesStayPrivateAndConstructionRechecksMaterials(GameTestHelper h) {
        var fixture = new GSETerminalRefreshTestSupport(h);
        var owners = new ArrayList<GSETerminalRefreshTestSupport.Owner>();
        var cells = new ArrayList<MEStorage>();
        var wireless = new ArrayList<ItemStack>();
        var accesses = new ArrayList<BlockPos>();
        var grids = new ArrayList<IGrid>();
        var providers = new ArrayList<IStorageProvider>();
        int[] linkedTo = {0, 1};
        class Driver {
            void cleanup() {
                for (int i = 0; i < grids.size(); i++) {
                    grids.get(i).getStorageService().removeGlobalStorageProvider(providers.get(i));
                }
                grids.clear();
                providers.clear();
                fixture.close();
            }

            void link(int owner, int network) {
                linkedTo[owner] = network;
                if (network < 0) WirelessTerminalItem.LINKABLE_HANDLER.unlink(wireless.get(owner));
                else WirelessTerminalItem.LINKABLE_HANDLER.link(wireless.get(owner),
                        GlobalPos.of(h.getLevel().dimension(), accesses.get(network)));
            }

            void immediate(int index) {
                var owner = owners.get(index);
                var other = owners.get(1 - index);
                long packets = owner.connection.packets;
                long otherPackets = other.connection.packets;
                UltimateTerminalMessages.forceFull(owner.player);
                h.assertTrue(owner.connection.packets == packets + 1
                                && other.connection.packets == otherPackets,
                        "Explicit AE refresh was delayed or sent to another player");
                owner.flush();
            }

            void tick(int tick) {
                try {
                    owners.forEach(GSETerminalRefreshTestSupport.Owner::flush);
                    if (tick == 20) link(0, -1);
                    if (tick == 31) {
                        h.assertTrue(owners.get(0).batchMaterials.stream().allMatch(value -> value.network() == 0),
                                "Periodic preview retained disconnected AE quantities");
                        immediate(0);
                    }
                    if (tick == 35) { link(0, 1); immediate(0); }
                    if (tick == 40) { link(0, 0); immediate(0); }
                    if (tick == 50) {
                        var owner = owners.get(0);
                        h.assertTrue(fixture.data().preview(owner.player).batchMaterials().stream()
                                        .allMatch(value -> value.missing() == 0),
                                "Revalidation fixture must have a fully funded preview");
                        setStored(h, cells.get(0), owner.player, owner.probe, 0);
                        owner.setInventory(0);
                        // No intervening preview: construction must ignore the last funded snapshot.
                        h.assertTrue(!fixture.data().start(owner.player)
                                        && fixture.data().state(owner.player.getUUID()) == UltimateTerminalWorldData.JobState.IDLE
                                        && fixture.data().pendingCount(owner.player.getUUID()) == 0,
                                "Construction trusted stale preview quantities after live AE withdrawal");
                        immediate(0);
                        var material = owners.get(0).batchMaterials.stream().filter(value ->
                                ItemStack.isSameItemSameTags(value.stack(), owner.probe)).findFirst().orElseThrow();
                        h.assertTrue(material.inventory() == 0 && material.network() == 0 && material.missing() > 0,
                                "Same-tick refresh did not observe live inventory/network withdrawal");
                    }
                    if (tick == 60) {
                        var owner = owners.get(0);
                        h.assertTrue(fixture.data().preview(owner.player).batchMaterials().stream()
                                        .allMatch(value -> value.missing() == 0), "Restored AE cell was not visible");
                        link(0, -1);
                        h.assertTrue(!fixture.data().start(owner.player),
                                "Construction retained a wireless reader across requests");
                        immediate(0);
                    }
                    if (tick == 65) { link(0, 0); immediate(0); }
                    if (tick == 100) {
                        for (var owner : owners) {
                            var ticks = owner.connection.deltaTicks;
                            h.assertTrue(ticks.size() >= 5, "AE preview did not refresh periodically");
                            int phase = Math.floorMod(ticks.get(0), 10);
                            h.assertTrue(ticks.stream().allMatch(value -> Math.floorMod(value, 10) == phase),
                                    "AE operations changed the player's refresh phase");
                            for (int i = 1; i < ticks.size(); i++) {
                                int gap = ticks.get(i) - ticks.get(i - 1);
                                h.assertTrue(gap >= 10 && gap % 10 == 0, "AE refresh interval changed");
                            }
                        }
                        cleanup();
                        h.succeed();
                        return;
                    }
                    for (int i = 0; i < owners.size(); i++) {
                        var owner = owners.get(i);
                        owner.setInventory(2 + i * 10 + tick % 7);
                        setStored(h, cells.get(i), owner.player, owner.probe, 100 + i * 100 + tick % 31);
                    }
                    h.runAfterDelay(1, () -> tick(tick + 1));
                } catch (RuntimeException exception) { cleanup(); throw exception; }
            }
        }
        var driver = new Driver();
        try {
            for (int i = 0; i < 2; i++) {
                BlockPos target = new BlockPos(8 + i * 15, 16, 16);
                GSEStructureTestUtils.placeMachine(h, GSEMachines.STEAM_CRUSHER, target);
                var owner = fixture.add(new UUID(0x6d6174657269616cL, i + 1),
                        List.of(h.absolutePos(target)), 0, i + 2);
                owners.add(owner);
                BlockPos access = h.absolutePos(new BlockPos(2 + i * 5, 2, 2));
                accesses.add(access);
                h.getLevel().setBlockAndUpdate(access, AEBlocks.WIRELESS_ACCESS_POINT.block().defaultBlockState());
                var cell = StorageCells.getCellInventory(AEItems.ITEM_CELL_4K.stack(), null);
                h.assertTrue(cell != null, "Private AE fixture needs a real cell");
                cells.add(cell);
                for (var material : fixture.data().preview(owner.player).batchMaterials()) {
                    setStored(h, cell, owner.player, material.stack(), material.required() + 200);
                }
                wireless.add(AEItems.WIRELESS_TERMINAL.stack());
                driver.link(i, i);
                owner.player.getInventory().setItem(35, wireless.get(i));
                final int recipient = i;
                owner.expectedNetwork = () -> linkedTo[recipient] < 0 ? 0
                        : cells.get(linkedTo[recipient]).getAvailableStacks().get(AEItemKey.of(owner.probe));
            }
            h.startSequence().thenWaitUntil(() -> {
                for (BlockPos access : accesses) h.assertTrue(
                        h.getLevel().getBlockEntity(access) instanceof IWirelessAccessPoint point && point.getGrid() != null,
                        "Private wireless grid not ready");
            }).thenExecute(() -> {
                try {
                    for (int i = 0; i < owners.size(); i++) {
                        var grid = ((IWirelessAccessPoint) h.getLevel().getBlockEntity(accesses.get(i))).getGrid();
                        var cell = cells.get(i);
                        IStorageProvider provider = mounts -> mounts.mount(cell);
                        grid.getStorageService().addGlobalStorageProvider(provider);
                        grids.add(grid);
                        providers.add(provider);
                        fixture.data().toggleAE(owners.get(i).player.getUUID());
                        driver.immediate(i);
                    }
                    h.assertTrue(grids.get(0) != grids.get(1), "Private AE grids unexpectedly joined");
                    driver.tick(0);
                } catch (RuntimeException exception) { driver.cleanup(); throw exception; }
            });
        } catch (RuntimeException exception) { driver.cleanup(); throw exception; }
    }

    private static void setStored(GameTestHelper h, MEStorage cell, ServerPlayer player, ItemStack stack, long amount) {
        var key = AEItemKey.of(stack);
        long old = cell.getAvailableStacks().get(key);
        var source = new PlayerSource(player);
        long changed = old < amount ? cell.insert(key, amount - old, Actionable.MODULATE, source)
                : cell.extract(key, old - amount, Actionable.MODULATE, source);
        h.assertTrue(changed == Math.abs(amount - old), "Private AE cell did not apply exact mutation");
    }

    @GameTest(templateNamespace = "gregsteamexpansion", template = "empty_32x32x32", timeoutTicks = 300)
    public static void survivalBackpackAndWirelessAeTransferAndRefund(GameTestHelper h) {
        var player = player(h, "7abb36f4-680a-4ef1-9e7f-0ef2e1ee7706");
        fixture(h, player, new StockNetwork()); // Only places the controller; use the real AE backend below.
        var data = UltimateTerminalWorldData.load(new CompoundTag());
        h.assertTrue(data.addTarget(player, h.getLevel().dimension(), h.absolutePos(new BlockPos(16, 16, 16))),
                "AE survival target rejected");
        var plan = data.preview(player).plan();
        var materials = data.preview(player).batchMaterials();
        BlockPos access = h.absolutePos(new BlockPos(2, 2, 2));
        h.getLevel().setBlockAndUpdate(access, AEBlocks.WIRELESS_ACCESS_POINT.block().defaultBlockState());
        var wireless = AEItems.WIRELESS_TERMINAL.stack();
        WirelessTerminalItem.LINKABLE_HANDLER.link(wireless, GlobalPos.of(h.getLevel().dimension(), access));
        player.getInventory().setItem(0, wireless);
        var cellStack = AEItems.ITEM_CELL_4K.stack();
        var cell = StorageCells.getCellInventory(cellStack, null);
        h.assertTrue(cell != null, "AE storage cell did not create its real inventory");
        var source = new PlayerSource(player);
        for (var material : materials) {
            int backpack = (int) material.required() / 2;
            var stack = material.stack().copyWithCount(backpack);
            player.getInventory().add(stack);
            h.assertTrue(stack.isEmpty(), "AE fixture backpack portion did not fit");
            long network = material.required() - backpack;
            h.assertTrue(cell.insert(AEItemKey.of(material.stack()), network, Actionable.MODULATE, source) == network,
                    "AE cell did not accept its exact material portion");
        }
        h.startSequence().thenWaitUntil(() -> {
            h.assertTrue(h.getLevel().getBlockEntity(access) instanceof IWirelessAccessPoint point && point.getGrid() != null,
                    "Real AE wireless access point has no grid yet");
        }).thenExecute(() -> {
            var grid = ((IWirelessAccessPoint) h.getLevel().getBlockEntity(access)).getGrid();
            IStorageProvider provider = mounts -> mounts.mount(cell);
            grid.getStorageService().addGlobalStorageProvider(provider);
            try {
                var preview = data.preview(player).batchMaterials();
                h.assertTrue(preview.stream().allMatch(material ->
                                material.inventory() == material.required() / 2
                                        && material.network() == material.required() - material.required() / 2
                                        && material.missing() == 0)
                                && preview.stream().mapToLong(UltimateTerminalWorldData.MaterialView::inventory).sum() > 0
                                && preview.stream().mapToLong(UltimateTerminalWorldData.MaterialView::network).sum() > 0,
                        "Mixed material discovery mismatch: " + preview);
                h.assertTrue(data.start(player) && cell.getAvailableStacks().isEmpty(),
                        "Real wireless network material extraction failed or left duplicate materials");
                h.assertTrue(data.tickPlayer(player, 3) == 3, "AE survival build did not place its prefix");
                for (int slot = 1; slot < player.getInventory().getContainerSize(); slot++) {
                    player.getInventory().setItem(slot, new ItemStack(Items.DIRT, 64));
                }
                h.getLevel().setBlockAndUpdate(plan.placements().get(3).pos(), Blocks.STONE.defaultBlockState());
                data.tickPlayer(player, 256);
                h.assertTrue(data.state(player.getUUID()) == UltimateTerminalWorldData.JobState.FAILED
                                && data.pendingCount(player.getUUID()) == 0,
                        "Full backpack failed to return the rollback to the actual AE network");
                for (var material : materials) h.assertTrue(
                        cell.getAvailableStacks().get(AEItemKey.of(material.stack())) == material.required(),
                        "Real AE refund lost or duplicated " + material.stack());
                h.getLevel().setBlockAndUpdate(plan.placements().get(3).pos(), Blocks.AIR.defaultBlockState());
                player.getInventory().clearContent();
                player.getInventory().setItem(0, wireless);
                h.assertTrue(data.start(player), "AE-only retry did not acquire returned materials");
                complete(h, data, player, plan);
                h.assertTrue(cell.getAvailableStacks().isEmpty() && inventoryCount(player) == 1,
                        "AE-only completion consumed the terminal or duplicated materials");
                var diamond = new ItemStack(Items.DIAMOND);
                h.assertTrue(UltimateTerminalAECompat.insert(player, diamond, 5, false) == 5
                                && UltimateTerminalAECompat.available(player, diamond) == 5,
                        "Actual linked AE insert/query did not agree");
                WirelessTerminalItem.LINKABLE_HANDLER.unlink(wireless);
                h.assertTrue(UltimateTerminalAECompat.available(player, diamond) == 0
                                && UltimateTerminalAECompat.extract(player, diamond, 5, false) == 0
                                && cell.getAvailableStacks().get(AEItemKey.of(diamond)) == 5,
                        "Disconnected wireless access changed stored materials");
            } finally {
                grid.getStorageService().removeGlobalStorageProvider(provider);
            }
        }).thenSucceed();
    }
}
