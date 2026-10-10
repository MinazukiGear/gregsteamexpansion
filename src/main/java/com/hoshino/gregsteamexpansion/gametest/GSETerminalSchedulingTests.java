package com.hoshino.gregsteamexpansion.gametest;

import com.hoshino.gregsteamexpansion.GregSteamExpansion;
import com.hoshino.gregsteamexpansion.registry.GSEMachines;
import com.hoshino.gregsteamexpansion.terminal.UltimateStructurePlanner;
import com.hoshino.gregsteamexpansion.terminal.UltimateTerminalWorldData;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.hoshino.gregsteamexpansion.gametest.GSETerminalTransactionTests.*;

/** Exercise the real server-wide scheduler with survival queues and scoped online players. */
@GameTestHolder(GregSteamExpansion.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GSETerminalSchedulingTests {
    private static final BlockPos FIRST = new BlockPos(8, 16, 8);
    private static final BlockPos SECOND = new BlockPos(23, 16, 23);
    private static final BlockPos THIRD = new BlockPos(23, 16, 8);

    private GSETerminalSchedulingTests() {}

    @GameTest(template = "empty_32x32x32", timeoutTicks = 200)
    public static void sharedBudgetAdvancesBothSurvivalQueuesAndRotatesSmallBudgets(GameTestHelper h)
            throws ReflectiveOperationException {
        var first = player(h, "b68251c4-3e45-4bf8-95af-47e813250001");
        var second = player(h, "b68251c4-3e45-4bf8-95af-47e813250002");
        var data = queues(h, first, second);
        try (var online = new OnlinePlayers(h, first, second)) {
            h.assertTrue(data.tick(h.getLevel().getServer(), 8) == 8
                            && data.progress(first.getUUID()) == 4 && data.progress(second.getUUID()) == 4,
                    "An earlier project monopolized the shared budget");
            int before = data.progress(first.getUUID()) + data.progress(second.getUUID());
            h.assertTrue(data.tick(h.getLevel().getServer(), 0) == 0
                            && data.tick(h.getLevel().getServer(), -1) == 0,
                    "A non-positive budget advanced construction");
            h.assertTrue(data.tick(h.getLevel().getServer(), 1) == 1
                            && data.tick(h.getLevel().getServer(), 1) == 1
                            && data.progress(first.getUUID()) == 5 && data.progress(second.getUUID()) == 5,
                    "A one-block budget did not rotate between owners");
            h.assertTrue(data.tick(h.getLevel().getServer(), 3) == 3
                            && data.progress(first.getUUID()) + data.progress(second.getUUID()) == before + 5
                            && Math.abs(data.progress(first.getUUID()) - data.progress(second.getUUID())) <= 1,
                    "An odd budget exceeded its limit or left an online owner behind");
            for (int i = 0; i < 4; i++) {
                h.assertTrue(data.tick(h.getLevel().getServer(), 3) == 3
                                && data.progress(first.getUUID()) + data.progress(second.getUUID()) == before + 8 + i * 3
                                && Math.abs(data.progress(first.getUUID()) - data.progress(second.getUUID())) <= 1,
                        "Repeated odd budgets always favoured the same owner");
            }
            data = UltimateTerminalWorldData.load(data.save(new CompoundTag()));
            finishQueues(h, data, first, second);
            h.assertTrue(data.tick(h.getLevel().getServer(), 256) == 0,
                    "Completed multiplayer work ran twice");
        }
        h.succeed();
    }

    @GameTest(template = "empty_32x32x32", timeoutTicks = 200)
    public static void pausedOwnersYieldBudgetAndResumePersistedEscrow(GameTestHelper h)
            throws ReflectiveOperationException {
        var first = player(h, "b68251c4-3e45-4bf8-95af-47e813250003");
        var second = player(h, "b68251c4-3e45-4bf8-95af-47e813250004");
        var third = player(h, "b68251c4-3e45-4bf8-95af-47e813250008");
        var data = queues(h, first, second, third);
        int firstTotal = data.total(first.getUUID());
        try (var online = new OnlinePlayers(h, first, second, third)) {
            online.set(first.getUUID(), null);
            assertYield(h, data, first, second, third);
            var elsewhere = FakePlayerFactory.get(h.getLevel().getServer().getLevel(Level.END),
                    first.getGameProfile());
            online.set(first.getUUID(), elsewhere);
            assertYield(h, data, first, second, third);
            online.set(first.getUUID(), first);

            CompoundTag unloaded = data.save(new CompoundTag());
            CompoundTag target = project(unloaded, first.getUUID()).getCompound("job")
                    .getList("targets", Tag.TAG_COMPOUND).getCompound(0).getCompound("target");
            long originalPos = target.getLong("pos");
            BlockPos absent = new BlockPos(900_000, 80, 900_000);
            h.assertTrue(!h.getLevel().hasChunkAt(absent), "Unloaded scheduler fixture was already loaded");
            target.putLong("pos", absent.asLong());
            data = UltimateTerminalWorldData.load(unloaded);
            assertYield(h, data, first, second, third);
            h.assertTrue(!h.getLevel().hasChunkAt(absent), "Scheduling forced a target chunk to load");

            CompoundTag resumed = data.save(new CompoundTag());
            project(resumed, first.getUUID()).getCompound("job").getList("targets", Tag.TAG_COMPOUND)
                    .getCompound(0).getCompound("target").putLong("pos", originalPos);
            data = UltimateTerminalWorldData.load(resumed);
            h.assertTrue(data.tick(h.getLevel().getServer(), 6) == 6 && data.progress(first.getUUID()) == 2,
                    "Restored owner did not resume sharing the budget");
            int reserved = stackCount(project(data.save(new CompoundTag()), first.getUUID())
                    .getCompound("job").getList("escrow", Tag.TAG_COMPOUND));
            h.assertTrue(reserved == firstTotal - 2 && inventoryCount(first) == 0,
                    "Resume re-reserved, lost or duplicated survival materials");
            finishQueues(h, data, first, second, third);
        }
        h.succeed();
    }

    @GameTest(template = "empty_32x32x32", timeoutTicks = 200)
    public static void failedAndCompletedOwnersReleaseBudgetAndAllowRestart(GameTestHelper h)
            throws ReflectiveOperationException {
        var first = player(h, "b68251c4-3e45-4bf8-95af-47e813250005");
        var second = player(h, "b68251c4-3e45-4bf8-95af-47e813250006");
        var data = queues(h, first, second);
        int firstTotal = data.total(first.getUUID());
        var machine = com.gregtechceu.gtceu.api.machine.MetaMachine.getMachine(h.getLevel(), h.absolutePos(FIRST));
        Direction facing = machine.getFrontFacing();
        try (var online = new OnlinePlayers(h, first, second)) {
            h.assertTrue(data.tickPlayer(first, 2) == 2, "Failure fixture did not build its prefix");
            machine.setFrontFacing(facing.getOpposite());
            h.assertTrue(data.tick(h.getLevel().getServer(), 4) == 4
                            && data.state(first.getUUID()) == UltimateTerminalWorldData.JobState.FAILED
                            && data.progress(second.getUUID()) == 4,
                    "Failed owner kept the budget from another player");
            h.assertTrue(inventoryCount(first) == firstTotal && data.pendingCount(first.getUUID()) == 0,
                    "Failed multiplayer construction did not refund exactly its reserved materials");
            machine.setFrontFacing(facing);
            h.assertTrue(data.start(first) && inventoryCount(first) == 0,
                    "A refunded owner could not restart without additional materials");
            int remaining = data.total(first.getUUID()) + data.total(second.getUUID())
                    - data.progress(second.getUUID());
            h.assertTrue(data.tick(h.getLevel().getServer(), 256) == remaining,
                    "Completion stranded available budget or charged completed work twice");
            finishQueues(h, data, first, second);
            data.collectPending(first);
            h.assertTrue(data.tick(h.getLevel().getServer(), 256) == 0 && inventoryCount(first) == 0,
                    "Finished or failed work duplicated a refund");
        } finally {
            machine.setFrontFacing(facing);
        }
        h.succeed();
    }

    private static UltimateTerminalWorldData queues(GameTestHelper h, ServerPlayer... players) {
        var data = UltimateTerminalWorldData.load(new CompoundTag());
        var owners = List.of(players);
        var positions = List.of(FIRST, SECOND, THIRD);
        for (int i = 0; i < owners.size(); i++) {
            ServerPlayer owner = owners.get(i);
            GSEStructureTestUtils.placeMachine(h, GSEMachines.STEAM_CRUSHER, positions.get(i));
            h.assertTrue(data.addTarget(owner, h.getLevel().dimension(), h.absolutePos(positions.get(i))),
                    "Multiplayer target rejected");
            data.toggleAE(owner.getUUID());
            fund(h, owner, data, null);
            h.assertTrue(data.start(owner) && inventoryCount(owner) == 0 && data.total(owner.getUUID()) > 16,
                    "Multiplayer survival materials were not reserved");
        }
        return data;
    }

    @GameTest(template = "empty_32x32x32", timeoutTicks = 200)
    public static void cancellationPreservesCompletedTargetsAndRefundsRemainingMaterialsOnce(GameTestHelper h) {
        var owner = player(h, "b68251c4-3e45-4bf8-95af-47e813250007");
        var data = UltimateTerminalWorldData.load(new CompoundTag());
        for (BlockPos pos : List.of(FIRST, SECOND)) {
            GSEStructureTestUtils.placeMachine(h, GSEMachines.STEAM_CRUSHER, pos);
            h.assertTrue(data.addTarget(owner, h.getLevel().dimension(), h.absolutePos(pos)),
                    "Cancellation target rejected");
        }
        data.toggleAE(owner.getUUID());
        data.selectTarget(owner, 0);
        var completed = data.preview(owner).plan();
        fund(h, owner, data, null);
        h.assertTrue(data.start(owner), "Cancellation fixture did not reserve survival materials");
        int expectedRefund = data.total(owner.getUUID()) - completed.placements().size();
        h.assertTrue(data.tickPlayer(owner, 256) == completed.placements().size()
                        && data.tickPlayer(owner, 2) == 2,
                "Cancellation fixture did not complete one target and start the next");
        for (int slot = 0; slot < owner.getInventory().getContainerSize(); slot++) {
            owner.getInventory().setItem(slot, new net.minecraft.world.item.ItemStack(
                    net.minecraft.world.item.Items.DIRT, 64));
        }
        h.assertTrue(data.cancel(owner) && data.state(owner.getUUID()) == UltimateTerminalWorldData.JobState.CANCELLED
                        && data.pendingCount(owner.getUUID()) == expectedRefund && !data.cancel(owner),
                "Cancellation lost a full-inventory refund or refunded twice");
        h.assertTrue(completed.placements().stream().allMatch(op -> h.getLevel().getBlockState(op.pos())
                        .getBlock().asItem() == op.stack().getItem()),
                "Cancellation rolled back an already completed target");
        data = UltimateTerminalWorldData.load(data.save(new CompoundTag()));
        h.assertTrue(data.state(owner.getUUID()) == UltimateTerminalWorldData.JobState.CANCELLED
                        && data.tickPlayer(owner, 256) == 0,
                "A restored cancelled queue resumed construction");
        owner.getInventory().clearContent();
        data.collectPending(owner);
        data.collectPending(owner);
        h.assertTrue(inventoryCount(owner) == expectedRefund && data.pendingCount(owner.getUUID()) == 0
                        && data.start(owner) && inventoryCount(owner) == 0,
                "Cancellation duplicated returns or required new materials to restart");
        for (int i = 0; i < 64 && data.state(owner.getUUID()) != UltimateTerminalWorldData.JobState.COMPLETE; i++) {
            data.tickPlayer(owner, 5);
        }
        h.assertTrue(data.state(owner.getUUID()) == UltimateTerminalWorldData.JobState.COMPLETE
                        && data.tickPlayer(owner, 256) == 0 && inventoryCount(owner) == 0
                        && data.pendingCount(owner.getUUID()) == 0,
                "Restarted cancellation did not settle its remaining materials exactly once");
        h.succeed();
    }

    private static void assertYield(GameTestHelper h, UltimateTerminalWorldData data,
                                    ServerPlayer paused, ServerPlayer second, ServerPlayer third) {
        int secondBefore = data.progress(second.getUUID());
        int thirdBefore = data.progress(third.getUUID());
        h.assertTrue(data.tick(h.getLevel().getServer(), 4) == 4
                        && data.state(paused.getUUID()) == UltimateTerminalWorldData.JobState.PAUSED
                        && data.progress(paused.getUUID()) == 0
                        && data.progress(second.getUUID()) == secondBefore + 2
                        && data.progress(third.getUUID()) == thirdBefore + 2,
                "An offline, cross-dimension or unloaded owner stranded or biased shared budget");
    }

    private static void finishQueues(GameTestHelper h, UltimateTerminalWorldData data,
                                     ServerPlayer... owners) {
        for (int i = 0; i < 64 && java.util.Arrays.stream(owners).anyMatch(owner ->
                data.state(owner.getUUID()) != UltimateTerminalWorldData.JobState.COMPLETE); i++) {
            int used = data.tick(h.getLevel().getServer(), 5);
            h.assertTrue(used > 0 && used <= 5, "Available multiplayer work stalled or exceeded the budget");
        }
        var positions = List.of(FIRST, SECOND, THIRD);
        for (int i = 0; i < owners.length; i++) {
            ServerPlayer owner = owners[i];
            h.assertTrue(data.state(owner.getUUID()) == UltimateTerminalWorldData.JobState.COMPLETE
                            && data.progress(owner.getUUID()) == data.total(owner.getUUID())
                            && inventoryCount(owner) == 0 && data.pendingCount(owner.getUUID()) == 0,
                    "Multiplayer queue did not settle exactly once");
            var plan = UltimateStructurePlanner.plan(h.getLevel(),
                    h.absolutePos(positions.get(i)), data.preview(owner).profile(), false);
            h.assertTrue(plan.valid() && plan.placements().isEmpty(), "Completed structure still has missing blocks");
        }
    }

    /** Fake players have no socket; expose them only to the lookup used by the actual scheduler. */
    private static final class OnlinePlayers implements AutoCloseable {
        private final Map<UUID, ServerPlayer> lookup;
        private final Map<UUID, ServerPlayer> previous = new HashMap<>();

        @SuppressWarnings("unchecked")
        OnlinePlayers(GameTestHelper h, ServerPlayer... players) throws ReflectiveOperationException {
            var field = PlayerList.class.getDeclaredField("playersByUUID");
            field.setAccessible(true);
            lookup = (Map<UUID, ServerPlayer>) field.get(h.getLevel().getServer().getPlayerList());
            for (ServerPlayer player : players) {
                previous.put(player.getUUID(), lookup.get(player.getUUID()));
                set(player.getUUID(), player);
            }
        }

        void set(UUID owner, ServerPlayer player) {
            if (player == null) lookup.remove(owner);
            else lookup.put(owner, player);
        }

        @Override
        public void close() { previous.forEach(this::set); }
    }
}
