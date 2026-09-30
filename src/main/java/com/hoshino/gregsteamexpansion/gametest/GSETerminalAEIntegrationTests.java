package com.hoshino.gregsteamexpansion.gametest;

import com.hoshino.gregsteamexpansion.terminal.UltimateTerminalWorldData;
import com.hoshino.gregsteamexpansion.terminal.compat.UltimateTerminalAECompat;

import appeng.api.config.Actionable;
import appeng.api.implementations.blockentities.IWirelessAccessPoint;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.IStorageProvider;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import static com.hoshino.gregsteamexpansion.gametest.GSETerminalTransactionTests.*;

/** Manually registered so core-only runtimes never reflect AE2 API signatures. */
@PrefixGameTestTemplate(false)
public final class GSETerminalAEIntegrationTests {
    private GSETerminalAEIntegrationTests() {}

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
