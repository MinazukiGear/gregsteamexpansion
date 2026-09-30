package com.hoshino.gregsteamexpansion.gametest;

import com.hoshino.gregsteamexpansion.GregSteamExpansion;
import com.hoshino.gregsteamexpansion.difficulty.GSEDifficultyConfig;
import com.hoshino.gregsteamexpansion.registry.GSEMachines;
import com.hoshino.gregsteamexpansion.terminal.*;
import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.Map;
import java.util.UUID;

import static com.hoshino.gregsteamexpansion.gametest.GSETerminalTransactionTests.*;

/** Uses real SavedData files and isolated End chunks, outside cleared GameTest templates. */
@GameTestHolder(GregSteamExpansion.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GSETerminalRestartTests {
    private static final UUID OWNER = UUID.fromString("9956f388-550a-42f5-858f-5c4313154181");
    private static final UUID AIR_OWNER = UUID.fromString("9956f388-550a-42f5-858f-5c4313154182");
    private static final BlockPos POS = new BlockPos(270 * 16 + 8, 80, 270 * 16 + 8);
    private static final BlockPos AIR_POS = new BlockPos(274 * 16 + 8, 80, 274 * 16 + 8);

    private GSETerminalRestartTests() {}

    @GameTest(template = "empty", timeoutTicks = 1200)
    public static void terminalQueueSurvivesChunkUnloadAndServerRestart(GameTestHelper h) throws Exception {
        var level = h.getLevel().getServer().getLevel(Level.END);
        h.assertTrue(level != null, "End is unavailable for terminal persistence fixture");
        var data = UltimateTerminalWorldData.get(level.getServer());
        String phase = System.getenv("GSE_RESTART_TEST_PHASE");
        if ("verify".equals(phase)) {
            h.assertTrue(GSEDifficultyConfig.externalModulesEnabled(), "Restart gate must cover enabled combined modules");
            try {
                verify(h, level, data, OWNER, POS, false);
                verify(h, level, data, AIR_OWNER, AIR_POS, true);
            } finally {
                cleanup(level, data, OWNER, POS);
                cleanup(level, data, AIR_OWNER, AIR_POS);
            }
            h.succeed();
            return;
        }
        h.assertTrue(phase == null || phase.isBlank() || phase.equals("seed"), "Unknown restart phase: " + phase);
        seed(h, level, data, OWNER, POS);
        if ("seed".equals(phase)) {
            h.assertTrue(GSEDifficultyConfig.externalModulesEnabled(), "Restart gate must cover enabled combined modules");
            seed(h, level, data, AIR_OWNER, AIR_POS);
            level.getChunkSource().save(true);
            level.getServer().overworld().getDataStorage().save();
            force(level, POS, false);
            force(level, AIR_POS, false);
            h.succeed();
            return;
        }
        var controller = level.getBlockEntity(POS);
        h.assertTrue(controller != null, "Terminal reload fixture has no controller");
        level.getChunkSource().save(true);
        force(level, POS, false);
        h.startSequence().thenWaitUntil(() -> h.assertTrue(
                level.getChunkSource().getChunkNow(POS.getX() >> 4, POS.getZ() >> 4) == null && controller.isRemoved(),
                "Terminal fixture chunk has not unloaded"))
                .thenExecute(() -> {
                    var player = fake(level, OWNER);
                    h.assertTrue(data.tickPlayer(player, 32) == 0 && !level.hasChunkAt(POS),
                            "Paused terminal loaded a missing chunk");
                    try { verify(h, level, data, OWNER, POS, false); }
                    finally { cleanup(level, data, OWNER, POS); }
                }).thenSucceed();
    }

    private static void seed(GameTestHelper h, ServerLevel level, UltimateTerminalWorldData global,
                             UUID owner, BlockPos pos) throws Exception {
        force(level, pos, true);
        clearBlocks(level, pos);
        level.setBlockAndUpdate(pos, GSEMachines.LARGE_STEAM_BLAST_FURNACE.defaultBlockState());
        var player = fake(level, owner);
        player.getInventory().clearContent();
        var network = new StockNetwork();
        var local = UltimateTerminalWorldData.load(new CompoundTag(), network);
        h.assertTrue(local.addTarget(player, level.dimension(), pos), "Persistent terminal target rejected");
        if (GSEDifficultyConfig.externalModulesEnabled()) {
            local.setChannel(player, "module.back", 0);
            local.setChannel(player, "module.top", 0);
        }
        var preview = local.preview(player);
        h.assertTrue(preview.plan().valid() && preview.plan().placements().size() > 7,
                "Persistent combined plan invalid: " + preview.plan().error());
        fund(h, player, local, network);
        h.assertTrue(local.start(player) && local.tickPlayer(player, 7) == 7,
                "Survival restart fixture did not escrow materials and build its prefix");
        // Put the real transaction into the world's ledger, including an existing unclaimed refund.
        var field = UltimateTerminalWorldData.class.getDeclaredField("projects");
        field.setAccessible(true);
        @SuppressWarnings("unchecked") var localProjects = (Map<UUID, Object>) field.get(local);
        @SuppressWarnings("unchecked") var globalProjects = (Map<UUID, Object>) field.get(global);
        Object savedProject = localProjects.get(owner);
        var pending = savedProject.getClass().getDeclaredField("pending");
        pending.setAccessible(true);
        @SuppressWarnings("unchecked") var items = (java.util.List<ItemStack>) pending.get(savedProject);
        items.add(new ItemStack(Items.DIAMOND, 7));
        globalProjects.put(owner, savedProject);
        global.setDirty();
        global.tick(level.getServer(), 1); // Fake player is offline to the real server player list.
        h.assertTrue(global.state(owner) == UltimateTerminalWorldData.JobState.PAUSED
                        && global.progress(owner) == 7 && global.pendingCount(owner) == 7,
                "Offline seed changed its cursor or refund");
        player.getInventory().clearContent();
    }

    private static void verify(GameTestHelper h, ServerLevel level, UltimateTerminalWorldData data,
                               UUID owner, BlockPos pos, boolean obstructAir) {
        CompoundTag saved = project(data.save(new CompoundTag()), owner);
        var job = saved.getCompound("job");
        var work = job.getList("targets", Tag.TAG_COMPOUND).getCompound(0);
        var operations = work.getList("operations", Tag.TAG_COMPOUND);
        var air = work.getLongArray("requiredAir");
        h.assertTrue(work.getInt("cursor") == 7 && job.getInt("completedOperations") == 7
                        && !job.getBoolean("unlimitedMaterials") && data.pendingCount(owner) == 7
                        && stackCount(job.getList("escrow", Tag.TAG_COMPOUND)) == operations.size() - 7
                        && work.getList("journal", Tag.TAG_COMPOUND).size() == 7
                        && work.contains("controller", Tag.TAG_COMPOUND) && !work.getBoolean("controllerInvalidated"),
                "Disk/reload changed cursor, survival escrow, journal, or pending refund");
        force(level, pos, true);
        h.assertTrue(level.getBlockEntity(pos) != null, "Controller was not restored from chunk storage");
        var player = fake(level, owner);
        player.getInventory().clearContent();
        var profile = data.preview(player).profile();
        if (GSEDifficultyConfig.externalModulesEnabled()) {
            h.assertTrue(profile.moduleSelections().equals(Map.of("back", "hot_blast_stoves", "top", "high_charge_tower"))
                            && air.length > 0, "Restart discarded combined module identities or strict air");
        }
        for (int i = 0; i < 7; i++) {
            var operation = operations.getCompound(i);
            h.assertTrue(level.getBlockState(BlockPos.of(operation.getLong("pos"))).getBlock().asItem()
                            == ItemStack.of(operation.getCompound("stack")).getItem(),
                    "Saved construction prefix was absent from world storage");
        }
        if (obstructAir) {
            for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
                player.getInventory().setItem(slot, new ItemStack(Items.DIRT, 64));
            }
            BlockPos obstruction = BlockPos.of(air[0]);
            level.setBlockAndUpdate(obstruction, Blocks.STONE.defaultBlockState());
            data.tickPlayer(player, 256);
            h.assertTrue(data.state(owner) == UltimateTerminalWorldData.JobState.FAILED
                            && data.pendingCount(owner) == operations.size() + 7
                            && level.getBlockState(obstruction).is(Blocks.STONE),
                    "Persisted air guard failed to preserve the obstruction and refund every reserved material");
            for (int i = 0; i < 7; i++) h.assertTrue(level.isEmptyBlock(BlockPos.of(operations.getCompound(i).getLong("pos"))),
                    "Persisted rollback journal did not clear its own prefix");
            player.getInventory().clearContent();
            // A large refund may require several backpack collections; the pending ledger retains the remainder.
            int collected = 0;
            while (data.pendingCount(owner) > 0) {
                data.collectPending(player);
                int delivered = inventoryCount(player);
                h.assertTrue(delivered > 0, "Persisted pending refund could not make progress");
                collected += delivered;
                player.getInventory().clearContent();
            }
            data.collectPending(player);
            h.assertTrue(collected == operations.size() + 7 && inventoryCount(player) == 0,
                    "Restart rollback refund was lost or duplicated across collections");
        } else {
            h.assertTrue(data.tickPlayer(player, 4) == 4 && data.progress(owner) == 11,
                    "Restart did not continue at the saved cursor");
            for (int i = 0; i < 64 && data.state(owner) != UltimateTerminalWorldData.JobState.COMPLETE; i++) {
                data.tickPlayer(player, 256);
            }
            h.assertTrue(data.state(owner) == UltimateTerminalWorldData.JobState.COMPLETE
                            && data.progress(owner) == operations.size() && data.pendingCount(owner) == 7,
                    "Restored survival queue did not complete without touching the prior refund");
            for (int i = 0; i < operations.size(); i++) {
                var operation = operations.getCompound(i);
                h.assertTrue(level.getBlockState(BlockPos.of(operation.getLong("pos"))).getBlock().asItem()
                                == ItemStack.of(operation.getCompound("stack")).getItem(),
                        "Restored queue omitted or repeated an operation");
            }
            data.collectPending(player);
            data.collectPending(player);
            h.assertTrue(player.getInventory().countItem(Items.DIAMOND) == 7 && inventoryCount(player) == 7
                            && data.pendingCount(owner) == 0 && data.tickPlayer(player, 256) == 0,
                    "Completion consumed, lost, or duplicated the saved refund");
        }
    }

    private static ServerPlayer fake(ServerLevel level, UUID owner) {
        var player = FakePlayerFactory.get(level, new GameProfile(owner, "[GSE terminal restart]"));
        player.getAbilities().instabuild = false;
        return player;
    }

    private static void force(ServerLevel level, BlockPos pos, boolean forced) {
        for (int x = (pos.getX() - 24) >> 4; x <= (pos.getX() + 24) >> 4; x++) {
            for (int z = (pos.getZ() - 24) >> 4; z <= (pos.getZ() + 24) >> 4; z++) {
                level.setChunkForced(x, z, forced);
                if (forced) level.getChunk(x, z);
            }
        }
    }

    private static void clearBlocks(ServerLevel level, BlockPos pos) {
        for (BlockPos block : BlockPos.betweenClosed(pos.offset(-22, -3, -22), pos.offset(22, 30, 22))) {
            if (!level.isEmptyBlock(block)) level.setBlockAndUpdate(block, Blocks.AIR.defaultBlockState());
        }
    }

    private static void cleanup(ServerLevel level, UltimateTerminalWorldData data, UUID owner, BlockPos pos) {
        clearBlocks(level, pos);
        level.getChunkSource().save(true);
        force(level, pos, false);
        data.clear(owner);
    }
}
