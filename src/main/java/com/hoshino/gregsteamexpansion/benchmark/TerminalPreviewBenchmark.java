package com.hoshino.gregsteamexpansion.benchmark;

import com.hoshino.gregsteamexpansion.gametest.GSEStructureTestUtils;
import com.hoshino.gregsteamexpansion.registry.GSEMachines;
import com.hoshino.gregsteamexpansion.terminal.UltimateTerminalWorldData;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/** Opt-in timings, excluded from ordinary regression and dependency-smoke namespaces. */
@GameTestHolder("gregsteamexpansion_preview_benchmark")
@PrefixGameTestTemplate(false)
public final class TerminalPreviewBenchmark {
    private static final int WARMUPS = 1;
    private static final int SAMPLES = 5;
    private static final int ROUNDS = 2;
    private static final int[] OWNERS = {1, 2, 8};
    private static final int[] TARGETS = {1, 4, 16};

    private TerminalPreviewBenchmark() {}

    @GameTest(template = "empty_32x32x32", timeoutTicks = 1200)
    public static void measurePreviewBatches(GameTestHelper helper) {
        helper.assertTrue(Boolean.getBoolean("gse.previewBenchmark"), "Benchmark must be explicitly enabled");
        List<BlockPos> controllers = new ArrayList<>();
        // Only this namespace runs in the scratch world. Separate empty structure volumes
        // exercise real planners without touching ordinary regression fixtures or developer saves.
        for (int z = 0; z < 4; z++) {
            for (int x = 0; x < 4; x++) {
                BlockPos relative = new BlockPos(16 + x * 32, 280, 16 + z * 32);
                BlockPos absolute = helper.absolutePos(relative);
                for (int dz = -1; dz <= 1; dz++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        helper.getLevel().getChunk((absolute.getX() >> 4) + dx, (absolute.getZ() >> 4) + dz);
                    }
                }
                GSEStructureTestUtils.placeMachine(helper, GSEMachines.STEAM_CRUSHER, relative);
                controllers.add(absolute);
            }
        }
        BlockPos large = new BlockPos(160, 280, 16);
        BlockPos largeAbsolute = helper.absolutePos(large);
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                helper.getLevel().getChunk((largeAbsolute.getX() >> 4) + dx, (largeAbsolute.getZ() >> 4) + dz);
            }
        }
        GSEStructureTestUtils.placeMachine(helper, GSEMachines.LARGE_HEAT_STORAGE_STEAM_FURNACE, large);
        controllers.add(largeAbsolute);
        JsonObject report = new JsonObject();
        report.addProperty("java_version", System.getProperty("java.version"));
        report.addProperty("os_arch", System.getProperty("os.arch"));
        report.addProperty("warmups", WARMUPS);
        report.addProperty("fixture", "Unbuilt steam-crusher matrix and default 15x15 furnace single target; survival owners, empty inventories, AE off");
        JsonArray cases = new JsonArray();
        report.add("cases", cases);
        runCase(helper, controllers, report, cases, 0);
    }

    private static void runCase(GameTestHelper helper, List<BlockPos> controllers,
                                JsonObject report, JsonArray cases, int index) {
        int matrixCases = ROUNDS * OWNERS.length * TARGETS.length;
        if (index == matrixCases + ROUNDS) {
            try {
                Path output = Path.of(System.getProperty("gse.previewBenchmarkOutput"));
                Files.writeString(output, new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n",
                        StandardCharsets.UTF_8);
            } catch (IOException exception) {
                helper.fail("Could not write benchmark report: " + exception.getMessage());
                return;
            }
            helper.succeed();
            return;
        }
        boolean large = index >= matrixCases;
        int round = large ? index - matrixCases : index / 9;
        // Rotate case order between rounds instead of always warming the largest case last.
        int combination = (index % 9 + round * 4) % 9;
        int owners = large ? 1 : OWNERS[combination / TARGETS.length];
        int targets = large ? 1 : TARGETS[combination % TARGETS.length];
        var data = UltimateTerminalWorldData.load(new CompoundTag());
        List<ServerPlayer> players = new ArrayList<>();
        for (int owner = 0; owner < owners; owner++) {
            UUID id = UUID.nameUUIDFromBytes(("gse-preview-benchmark-" + owner).getBytes(StandardCharsets.UTF_8));
            var player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(id, "[GSE preview]"));
            player.getInventory().clearContent();
            player.getAbilities().instabuild = false;
            for (int target = 0; target < targets; target++) {
                helper.assertTrue(data.addTarget(player, helper.getLevel().dimension(),
                                controllers.get(large ? 16 : target)),
                        "Benchmark target rejected");
            }
            data.toggleAE(id);
            players.add(player);
        }
        var first = data.preview(players.get(0));
        helper.assertTrue(first.plan().valid() && !first.plan().placements().isEmpty(),
                "Benchmark blueprint must be valid and require construction: " + first.error());
        // A high-load batch can take seconds. Yield between batches rather than placing
        // an entire case in one server tick, and retain small samples without a speed gate.
        class Sampling {
            final long[] samples = new long[SAMPLES];

            void next(int sample) {
                long start = System.nanoTime();
                int cells = previewBatch(data, players);
                long elapsed = System.nanoTime() - start;
                helper.assertTrue(cells == owners * first.plan().cells().size(),
                        "Preview geometry drifted during timing");
                if (sample >= 0) samples[sample] = elapsed;
                if (sample + 1 < SAMPLES) {
                    helper.runAfterDelay(1, () -> next(sample + 1));
                    return;
                }
                Arrays.sort(samples);
                JsonObject result = new JsonObject();
                result.addProperty("round", round + 1);
                result.addProperty("fixture", large ? "heat_storage_furnace_15x15" : "steam_crusher_3x3");
                result.addProperty("owners", owners);
                result.addProperty("targets", targets);
                result.addProperty("samples", SAMPLES);
                result.addProperty("selected_cells", first.plan().cells().size());
                result.addProperty("selected_placements", first.plan().placements().size());
                result.addProperty("batch_required", first.batchMaterials().stream().mapToLong(
                        UltimateTerminalWorldData.MaterialView::required).sum());
                result.addProperty("p50_batch_ms", samples[SAMPLES / 2] / 1_000_000.0);
                result.addProperty("max_batch_ms", samples[SAMPLES - 1] / 1_000_000.0);
                cases.add(result);
                System.out.println("GSE preview benchmark: " + result);
                helper.runAfterDelay(1, () -> runCase(helper, controllers, report, cases, index + 1));
            }
        }
        new Sampling().next(-WARMUPS);
    }

    private static int previewBatch(UltimateTerminalWorldData data, List<ServerPlayer> players) {
        int cells = 0;
        for (ServerPlayer player : players) cells += data.preview(player).plan().cells().size();
        return cells;
    }
}
