package com.hoshino.gregsteamexpansion.gametest;

import com.hoshino.gregsteamexpansion.GregSteamExpansion;
import com.hoshino.gregsteamexpansion.registry.GSEMachines;
import com.hoshino.gregsteamexpansion.terminal.*;
import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Survival transaction and ownership regressions; network faults are scoped to one ledger. */
@GameTestHolder(GregSteamExpansion.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GSETerminalTransactionTests {
    private GSETerminalTransactionTests() {}

    @GameTest(template = "empty_32x32x32", timeoutTicks = 200)
    public static void controllerChangesStopRestoredSurvivalConstruction(GameTestHelper h) {
        for (int mutation = 0; mutation < 4; mutation++) {
            var player = player(h, "7abb36f4-680a-4ef1-9e7f-0ef2e1ee771" + mutation);
            var original = fixture(h, player, new StockNetwork());
            original.toggleAE(player.getUUID());
            fund(h, player, original, null);
            var plan = original.preview(player).plan();
            h.assertTrue(original.start(player) && original.tickPlayer(player, 3) == 3,
                    "Controller-change fixture did not build its prefix");
            var data = UltimateTerminalWorldData.load(original.save(new CompoundTag()));
            BlockPos pos = h.absolutePos(new BlockPos(16, 16, 16));
            if (mutation == 0) h.getLevel().setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            else if (mutation == 1) GSEStructureTestUtils.placeMachine(h, GSEMachines.STEAM_COMPRESSOR,
                    new BlockPos(16, 16, 16));
            else if (mutation == 2) com.gregtechceu.gtceu.api.machine.MetaMachine.getMachine(h.getLevel(), pos).setFrontFacing(Direction.EAST);
            else {
                data.invalidateOwnership(h.getLevel().dimension(), pos);
                GSEStructureTestUtils.placeMachine(h, GSEMachines.STEAM_CRUSHER, new BlockPos(16, 16, 16));
                data = UltimateTerminalWorldData.load(data.save(new CompoundTag()));
            }
            var externalState = h.getLevel().getBlockState(pos);
            data.tickPlayer(player, 256);
            h.assertTrue(data.state(player.getUUID()) == UltimateTerminalWorldData.JobState.FAILED
                            && data.progress(player.getUUID()) == 3,
                    "Restored queue kept constructing after controller mutation " + mutation);
            h.assertTrue(inventoryCount(player) == plan.placements().size() && data.pendingCount(player.getUUID()) == 0
                            && h.getLevel().getBlockState(pos) == externalState
                            && plan.placements().subList(0, 3).stream().allMatch(op -> h.getLevel().isEmptyBlock(op.pos())),
                    "Controller-change rollback lost material or overwrote the replacement controller");
        }
        h.succeed();
    }

    @GameTest(template = "empty_32x32x32", timeoutTicks = 200)
    public static void failedReservationPersistsUndeliverableRefunds(GameTestHelper h) {
        var player = player(h, "7abb36f4-680a-4ef1-9e7f-0ef2e1ee7701");
        var network = new StockNetwork();
        var data = fixture(h, player, network);
        var materials = data.preview(player).batchMaterials();
        h.assertTrue(materials.size() > 1, "Fault fixture needs two different materials");
        var first = data.preview(player).plan().placements().get(0).stack();
        materials.forEach(material -> network.add(material.stack(), material.required()
                - (ItemStack.isSameItemSameTags(material.stack(), first) ? 1 : 0)));
        h.assertTrue(network.available(player, first) > 1, "Fault fixture needs a partial first extraction");
        player.getInventory().setItem(0, first.copyWithCount(1));
        network.extractLimit = 1;
        network.rejectInsert = true;
        // Simulate a concurrent inventory fill after preflight and the backpack extraction.
        network.onExtract = () -> {
            for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
                player.getInventory().setItem(slot, new ItemStack(Items.DIRT, 64));
            }
        };
        h.assertTrue(!data.start(player), "Partial material extraction unexpectedly started a job");
        h.assertTrue(data.state(player.getUUID()) == UltimateTerminalWorldData.JobState.IDLE,
                "Failed reservation left an active job");
        h.assertTrue(data.pendingCount(player.getUUID()) == 2 && data.isDirty(),
                "Backpack plus partial network refund was lost or not marked for saving");
        var restored = UltimateTerminalWorldData.load(data.save(new CompoundTag()), network);
        h.assertTrue(restored.pendingCount(player.getUUID()) == 2, "Pending refund did not survive NBT");
        player.getInventory().clearContent();
        restored.collectPending(player);
        h.assertTrue(restored.pendingCount(player.getUUID()) == 0 && inventoryCount(player) == 2,
                "Persisted refund was not delivered exactly once");
        restored.collectPending(player);
        h.assertTrue(inventoryCount(player) == 2, "Repeated refund collection duplicated material");
        h.succeed();
    }

    @GameTest(template = "empty_32x32x32", timeoutTicks = 200)
    public static void survivalMaterialsSurviveOfflineDimensionAndUnloadedPauses(GameTestHelper h) {
        var player = player(h, "7abb36f4-680a-4ef1-9e7f-0ef2e1ee7702");
        var network = new StockNetwork();
        var data = fixture(h, player, network);
        var plan = data.preview(player).plan();
        fund(h, player, data, network);
        h.assertTrue(data.start(player), "Mixed backpack/network survival build did not start");
        h.assertTrue(inventoryCount(player) == 0 && network.count() == 0,
                "Reservation did not escrow exactly the required mixed materials");
        data.setDirty(false);
        h.assertTrue(data.tick(h.getLevel().getServer(), 8) == 0
                        && data.state(player.getUUID()) == UltimateTerminalWorldData.JobState.PAUSED
                        && data.isDirty(), "Offline pause advanced work or was not persisted");
        data = UltimateTerminalWorldData.load(data.save(new CompoundTag()), network);
        var wrongDimension = FakePlayerFactory.get(h.getLevel().getServer().getLevel(Level.END), player.getGameProfile());
        h.assertTrue(data.tickPlayer(wrongDimension, 8) == 0 && data.progress(player.getUUID()) == 0,
                "A player in another dimension advanced the queue");
        CompoundTag unloaded = data.save(new CompoundTag());
        CompoundTag target = work(unloaded).getCompound("target");
        BlockPos absent = new BlockPos(900_000, 80, 900_000);
        target.putLong("pos", absent.asLong());
        h.assertTrue(!h.getLevel().hasChunkAt(absent), "Unloaded pause fixture was already loaded");
        var paused = UltimateTerminalWorldData.load(unloaded, network);
        h.assertTrue(paused.tickPlayer(player, 8) == 0 && !h.getLevel().hasChunkAt(absent),
                "Unloaded target was force-loaded or advanced");
        h.assertTrue(stackCount(job(unloaded).getList("escrow", Tag.TAG_COMPOUND)) == plan.placements().size(),
                "Pause changed escrow quantities");
        h.assertTrue(data.tickPlayer(player, 3) == 3 && data.progress(player.getUUID()) == 3,
                "Resumed queue ignored its per-tick budget");
        complete(h, data, player, plan);
        h.assertTrue(inventoryCount(player) == 0 && network.count() == 0
                        && data.pendingCount(player.getUUID()) == 0,
                "Completed survival build lost or duplicated reserved materials");
        h.assertTrue(data.tickPlayer(player, 256) == 0, "Completed job ran twice");
        // Simulate an earlier operation chunk unloading after the final placement and before validation.
        CompoundTag awaitingValidation = data.save(new CompoundTag());
        job(awaitingValidation).putInt("state", UltimateTerminalWorldData.JobState.RUNNING.ordinal());
        job(awaitingValidation).putInt("targetIndex", 0);
        work(awaitingValidation).getList("operations", Tag.TAG_COMPOUND).getCompound(0).putLong("pos", absent.asLong());
        var finalCheck = UltimateTerminalWorldData.load(awaitingValidation, network);
        h.assertTrue(finalCheck.tickPlayer(player, 256) == 0
                        && finalCheck.state(player.getUUID()) == UltimateTerminalWorldData.JobState.PAUSED
                        && !h.getLevel().hasChunkAt(absent),
                "Final validation forced an earlier unloaded chunk or settled the job prematurely");
        h.succeed();
    }

    @GameTest(template = "empty_32x32x32", timeoutTicks = 200)
    public static void rollbackPreservesForeignMinedAndEntityBlocks(GameTestHelper h) {
        var player = player(h, "7abb36f4-680a-4ef1-9e7f-0ef2e1ee7703");
        var data = UltimateTerminalWorldData.get(h.getLevel().getServer());
        data.clear(player.getUUID());
        GSEStructureTestUtils.placeMachine(h, GSEMachines.STEAM_CRUSHER, new BlockPos(16, 16, 16));
        h.assertTrue(data.addTarget(player, h.getLevel().dimension(), h.absolutePos(new BlockPos(16, 16, 16))),
                "Rollback target rejected");
        if (data.useAE(player.getUUID())) data.toggleAE(player.getUUID());
        fund(h, player, data, null);
        var plan = data.preview(player).plan();
        h.assertTrue(data.start(player) && data.tickPlayer(player, 5) == 5, "Rollback prefix did not place five blocks");
        var ops = plan.placements();
        h.getLevel().setBlockAndUpdate(ops.get(0).pos(), Blocks.DIAMOND_BLOCK.defaultBlockState());
        h.getLevel().setBlockAndUpdate(ops.get(1).pos(), Blocks.AIR.defaultBlockState());
        player.getInventory().add(ops.get(1).stack().copyWithCount(1)); // Mining already yielded this material.
        h.getLevel().setBlockAndUpdate(ops.get(2).pos(), Blocks.CHEST.defaultBlockState());
        // A same-state replacement still belongs to the player after a Forge break event.
        UltimateTerminalEvents.onBlockBreak(new BlockEvent.BreakEvent(h.getLevel(), ops.get(3).pos(),
                h.getLevel().getBlockState(ops.get(3).pos()), player));
        h.assertTrue(!project(data.save(new CompoundTag()), player.getUUID()).getCompound("job")
                .getList("targets", Tag.TAG_COMPOUND).getCompound(0).getList("journal", Tag.TAG_COMPOUND).isEmpty(),
                "Placed-state journal was not persisted");
        h.getLevel().setBlockAndUpdate(ops.get(5).pos(), Blocks.STONE.defaultBlockState());
        data.tickPlayer(player, 256);
        h.assertTrue(data.state(player.getUUID()) == UltimateTerminalWorldData.JobState.FAILED,
                "External obstruction did not fail work");
        h.assertTrue(h.getLevel().getBlockState(ops.get(0).pos()).is(Blocks.DIAMOND_BLOCK)
                        && h.getLevel().isEmptyBlock(ops.get(1).pos())
                        && h.getLevel().getBlockEntity(ops.get(2).pos()) != null
                        && h.getLevel().getBlockState(ops.get(3).pos()).getBlock().asItem() == ops.get(3).stack().getItem()
                        && h.getLevel().isEmptyBlock(ops.get(4).pos()),
                "Rollback removed foreign ownership, refunded mining, or failed to remove its own block");
        h.assertTrue(inventoryCount(player) == ops.size() - 3 && data.pendingCount(player.getUUID()) == 0,
                "Rollback did not refund exactly the unplaced plus successfully removed blocks");
        data.tickPlayer(player, 256);
        data.collectPending(player);
        h.assertTrue(inventoryCount(player) == ops.size() - 3, "Failed job refunded twice");
        data.clear(player.getUUID());
        h.succeed();
    }

    @GameTest(template = "empty_32x32x32", timeoutTicks = 200)
    public static void upgradeRollbackRestoresExactStatesAndRecoversDisplacedMaterials(GameTestHelper h) {
        var player = player(h, "7abb36f4-680a-4ef1-9e7f-0ef2e1ee7704");
        var data = fixture(h, player, new StockNetwork());
        data.toggleAE(player.getUUID());
        fund(h, player, data, null);
        var ops = data.preview(player).plan().placements();
        h.assertTrue(data.start(player), "Upgrade journal fixture reservation failed");
        // A persisted upgrade queue can contain ordinary property-bearing blocks.
        CompoundTag tag = data.save(new CompoundTag());
        job(tag).putInt("mode", UltimateTerminalMode.UPGRADE.ordinal());
        var savedOps = work(tag).getList("operations", Tag.TAG_COMPOUND);
        for (int i = 0; i < 2; i++) savedOps.getCompound(i).put("replaced", new ItemStack(Items.OAK_STAIRS).save(new CompoundTag()));
        var before = Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.WEST)
                .setValue(StairBlock.HALF, Half.TOP);
        h.getLevel().setBlockAndUpdate(ops.get(0).pos(), before);
        h.getLevel().setBlockAndUpdate(ops.get(1).pos(), before);
        data = UltimateTerminalWorldData.load(tag);
        h.assertTrue(data.tickPlayer(player, 2) == 2, "Upgrade queue did not replace both ordinary blocks");
        data = UltimateTerminalWorldData.load(data.save(new CompoundTag()));
        h.getLevel().setBlockAndUpdate(ops.get(1).pos(), Blocks.DIAMOND_BLOCK.defaultBlockState());
        h.getLevel().setBlockAndUpdate(ops.get(2).pos(), Blocks.STONE.defaultBlockState());
        data.tickPlayer(player, 256);
        h.assertTrue(data.state(player.getUUID()) == UltimateTerminalWorldData.JobState.FAILED
                        && h.getLevel().getBlockState(ops.get(0).pos()) == before
                        && h.getLevel().getBlockState(ops.get(1).pos()).is(Blocks.DIAMOND_BLOCK),
                "Upgrade rollback lost facing/half or overwrote a foreign replacement");
        h.assertTrue(player.getInventory().countItem(Items.OAK_STAIRS) == 1
                        && inventoryCount(player) == ops.size(),
                "Upgrade rollback duplicated the restored original or lost the unrestorable old material");
        h.succeed();
    }

    static ServerPlayer player(GameTestHelper h, String id) {
        var player = FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.fromString(id), "[GSE transaction]"));
        player.getAbilities().instabuild = false;
        player.getInventory().clearContent();
        return player;
    }

    static UltimateTerminalWorldData fixture(GameTestHelper h, ServerPlayer player,
                                              UltimateTerminalWorldData.MaterialNetwork network) {
        GSEStructureTestUtils.placeMachine(h, GSEMachines.STEAM_CRUSHER, new BlockPos(16, 16, 16));
        var data = UltimateTerminalWorldData.load(new CompoundTag(), network);
        h.assertTrue(data.addTarget(player, h.getLevel().dimension(), h.absolutePos(new BlockPos(16, 16, 16))),
                "Transaction target rejected");
        h.assertTrue(data.preview(player).plan().valid(), "Transaction plan invalid");
        return data;
    }

    static void fund(GameTestHelper h, ServerPlayer player, UltimateTerminalWorldData data, StockNetwork network) {
        for (var material : data.preview(player).batchMaterials()) {
            int count = (int) material.required();
            int backpack = network == null ? count : count / 2;
            while (backpack > 0) {
                int amount = Math.min(backpack, material.stack().getMaxStackSize());
                var stack = material.stack().copyWithCount(amount);
                player.getInventory().add(stack);
                h.assertTrue(stack.isEmpty(), "Fixture materials do not fit the backpack");
                backpack -= amount;
            }
            if (network != null) network.add(material.stack(), count - count / 2);
        }
    }

    static void complete(GameTestHelper h, UltimateTerminalWorldData data, ServerPlayer player,
                         UltimateStructurePlanner.Plan plan) {
        for (int i = 0; i < 64 && data.state(player.getUUID()) != UltimateTerminalWorldData.JobState.COMPLETE; i++) {
            data.tickPlayer(player, 256);
        }
        h.assertTrue(data.state(player.getUUID()) == UltimateTerminalWorldData.JobState.COMPLETE
                        && plan.placements().stream().allMatch(op -> h.getLevel().getBlockState(op.pos())
                                .getBlock().asItem() == op.stack().getItem()), "Survival queue did not complete every block");
    }

    static int inventoryCount(ServerPlayer player) {
        int total = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) total += player.getInventory().getItem(i).getCount();
        return total;
    }

    static CompoundTag project(CompoundTag tag, UUID owner) {
        var entries = tag.getList("projects", Tag.TAG_COMPOUND);
        for (int i = 0; i < entries.size(); i++) {
            var entry = entries.getCompound(i);
            if (owner.equals(entry.getUUID("owner"))) return entry.getCompound("data");
        }
        throw new IllegalStateException("Missing test owner " + owner);
    }

    static CompoundTag job(CompoundTag tag) {
        return tag.getList("projects", Tag.TAG_COMPOUND).getCompound(0).getCompound("data").getCompound("job");
    }

    static CompoundTag work(CompoundTag tag) { return job(tag).getList("targets", Tag.TAG_COMPOUND).getCompound(0); }

    static int stackCount(net.minecraft.nbt.ListTag list) {
        int total = 0;
        for (int i = 0; i < list.size(); i++) total += ItemStack.of(list.getCompound(i)).getCount();
        return total;
    }

    static final class StockNetwork implements UltimateTerminalWorldData.MaterialNetwork {
        final List<ItemStack> stock = new ArrayList<>();
        long extractLimit = Long.MAX_VALUE;
        boolean rejectInsert;
        Runnable onExtract = () -> {};
        void add(ItemStack stack, long amount) { if (amount > 0) stock.add(stack.copyWithCount((int) amount)); }
        int count() { return stock.stream().mapToInt(ItemStack::getCount).sum(); }
        public long available(ServerPlayer player, ItemStack stack) {
            return stock.stream().filter(value -> ItemStack.isSameItemSameTags(value, stack)).mapToLong(ItemStack::getCount).sum();
        }
        public long extract(ServerPlayer player, ItemStack stack, long amount) {
            onExtract.run();
            int taken = (int) Math.min(amount, Math.min(extractLimit, available(player, stack)));
            int remaining = taken;
            for (var value : stock) {
                if (!ItemStack.isSameItemSameTags(value, stack)) continue;
                int count = Math.min(remaining, value.getCount());
                value.shrink(count);
                remaining -= count;
            }
            return taken;
        }
        public long insert(ServerPlayer player, ItemStack stack, long amount) {
            if (rejectInsert) return 0;
            add(stack, amount);
            return amount;
        }
    }
}
