package com.hoshino.gregsteamexpansion.benchmark;

import com.hoshino.gregsteamexpansion.gametest.GSEStructureTestUtils;
import com.hoshino.gregsteamexpansion.gametest.GSETerminalRefreshTestSupport;
import com.hoshino.gregsteamexpansion.registry.GSEMachines;
import com.hoshino.gregsteamexpansion.terminal.UltimateStructurePlanner;
import com.hoshino.gregsteamexpansion.terminal.UltimateTerminalMessages;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/** Continuous real END-event refreshes, encoded packets and isolated simulated recipients. */
@GameTestHolder("gregsteamexpansion_refresh_benchmark")
@PrefixGameTestTemplate(false)
public final class TerminalRefreshBenchmark {
    private static final int WARMUP = 20;
    private static final int SAMPLES = 100;
    private static final int[][] CASES = {{1, 1}, {2, 1}, {8, 1}, {1, 16}, {2, 16}, {8, 16}, {1, 1}};

    private TerminalRefreshBenchmark() {}

    @GameTest(template = "empty_32x32x32", timeoutTicks = 6000)
    public static void measureContinuousRefresh(GameTestHelper helper) {
        helper.assertTrue(Boolean.getBoolean("gse.refreshBenchmark"), "Refresh benchmark must be enabled");
        new Run(helper).start();
    }

    private static final class Run {
        private final GameTestHelper helper;
        private final List<BlockPos> controllers = new ArrayList<>();
        private final JsonObject report = new JsonObject();
        private final JsonArray cases = new JsonArray();
        private final List<GSETerminalRefreshTestSupport.Owner> owners = new ArrayList<>();
        private GSETerminalRefreshTestSupport fixture;
        private int caseIndex;
        private int elapsed;
        private BlockPos changedPos;
        private net.minecraft.world.level.block.Block changedBlock;
        private Stats quiet;
        private Stats changing;
        private long tickStart;
        private long endStart;
        private long packetsAtStart;
        private Consumer<TickEvent.ServerTickEvent> before;
        private Consumer<TickEvent.ServerTickEvent> after;

        Run(GameTestHelper helper) { this.helper = helper; }

        void start() {
            for (int z = 0; z < 4; z++) {
                for (int x = 0; x < 4; x++) {
                    place(new BlockPos(16 + x * 32, 280, 16 + z * 32), false);
                }
            }
            place(new BlockPos(160, 280, 16), true);
            report.addProperty("java_version", System.getProperty("java.version"));
            report.addProperty("os_arch", System.getProperty("os.arch"));
            report.addProperty("warmup_ticks", WARMUP);
            report.addProperty("sample_ticks_per_phase", SAMPLES);
            report.addProperty("scope", "Real server tick END refresh/diff/encoding; simulated socket-free recipients, AE disabled. Event intervals include other listeners; no TCP/compression/client FPS.");
            report.add("cases", cases);
            before = event -> {
                if (event.getServer() != helper.getLevel().getServer() || fixture == null) return;
                if (event.phase == TickEvent.Phase.START) {
                    tickStart = System.nanoTime();
                    packetsAtStart = packets();
                } else endStart = System.nanoTime();
            };
            after = event -> {
                if (event.getServer() != helper.getLevel().getServer() || fixture == null
                        || event.phase != TickEvent.Phase.END || elapsed < WARMUP) return;
                long now = System.nanoTime();
                Stats stats = elapsed < WARMUP + SAMPLES ? quiet : changing;
                stats.endMs.add((now - endStart) / 1_000_000.0);
                stats.tickMs.add((now - tickStart) / 1_000_000.0);
                stats.peakPackets = Math.max(stats.peakPackets, packets() - packetsAtStart);
            };
            MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGH, false, TickEvent.ServerTickEvent.class, before);
            MinecraftForge.EVENT_BUS.addListener(EventPriority.LOW, false, TickEvent.ServerTickEvent.class, after);
            nextCase();
        }

        private void place(BlockPos relative, boolean large) {
            BlockPos absolute = helper.absolutePos(relative);
            for (int dz = -1; dz <= 1; dz++) {
                for (int dx = -1; dx <= 1; dx++) {
                    helper.getLevel().getChunk((absolute.getX() >> 4) + dx, (absolute.getZ() >> 4) + dz);
                }
            }
            GSEStructureTestUtils.placeMachine(helper,
                    large ? GSEMachines.LARGE_HEAT_STORAGE_STEAM_FURNACE : GSEMachines.STEAM_CRUSHER, relative);
            controllers.add(absolute);
        }

        private void nextCase() {
            try {
                if (caseIndex == CASES.length * 2) {
                    MinecraftForge.EVENT_BUS.unregister(before);
                    MinecraftForge.EVENT_BUS.unregister(after);
                    Files.writeString(Path.of(System.getProperty("gse.previewBenchmarkOutput")),
                            new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n", StandardCharsets.UTF_8);
                    helper.succeed();
                    return;
                }
                int combination = (caseIndex % CASES.length + (caseIndex / CASES.length) * 3) % CASES.length;
                boolean large = combination == CASES.length - 1;
                int count = CASES[combination][0];
                int targets = CASES[combination][1];
                fixture = new GSETerminalRefreshTestSupport(helper);
                for (int i = 0; i < count; i++) {
                    UUID id = UUID.nameUUIDFromBytes(("gse-refresh-" + caseIndex + "-" + i).getBytes(StandardCharsets.UTF_8));
                    owners.add(fixture.add(id, large ? List.of(controllers.get(16))
                            : controllers.subList(0, targets), i % targets, i + 1));
                }
                var first = fixture.data().preview(owners.get(0).player);
                var missing = first.plan().cells().stream().filter(cell ->
                        cell.status() == UltimateStructurePlanner.CellStatus.MISSING
                                && cell.expected().getItem() instanceof BlockItem).findFirst().orElseThrow();
                changedPos = missing.pos();
                changedBlock = ((BlockItem) missing.expected().getItem()).getBlock();
                quiet = new Stats();
                changing = new Stats();
                quiet.record = record(large, count, targets, "quiet", first.plan().cells().size());
                changing.record = record(large, count, targets, "changing", first.plan().cells().size());
                elapsed = -1;
                helper.runAfterDelay(1, this::advance);
            } catch (Exception exception) {
                fail(exception);
            }
        }

        private JsonObject record(boolean large, int count, int targets, String phase, int cells) {
            JsonObject value = new JsonObject();
            value.addProperty("round", caseIndex / CASES.length + 1);
            value.addProperty("fixture", large ? "heat_storage_furnace_15x15" : "steam_crusher_3x3");
            value.addProperty("owners", count);
            value.addProperty("targets", targets);
            value.addProperty("phase", phase);
            value.addProperty("selected_cells", cells);
            return value;
        }

        private void advance() {
            try {
                owners.forEach(GSETerminalRefreshTestSupport.Owner::flush);
                elapsed++;
                if (elapsed == WARMUP) {
                    quiet.packetHead = packets();
                    quiet.byteHead = bytes();
                }
                if (elapsed == WARMUP + SAMPLES) {
                    quiet.finish(packets(), bytes(), cases);
                    changing.packetHead = packets();
                    changing.byteHead = bytes();
                }
                if (elapsed == WARMUP + 2 * SAMPLES) {
                    changing.finish(packets(), bytes(), cases);
                    cleanup();
                    caseIndex++;
                    helper.runAfterDelay(1, this::nextCase);
                    return;
                }
                if (elapsed >= WARMUP + SAMPLES) {
                    int sample = elapsed - WARMUP - SAMPLES;
                    for (int i = 0; i < owners.size(); i++) owners.get(i).setInventory(i + 1 + sample % 40);
                    if (sample % 20 == 0) {
                        helper.getLevel().setBlockAndUpdate(changedPos, (sample / 20 % 2 == 0
                                ? changedBlock : Blocks.AIR).defaultBlockState());
                    }
                    if (sample == 50 && owners.get(0).targets.size() > 1) {
                        var owner = owners.get(0);
                        fixture.select(owner, owner.targets.size() - 1);
                        UltimateTerminalMessages.forceFull(owner.player);
                    }
                }
                helper.runAfterDelay(1, this::advance);
            } catch (Exception exception) {
                fail(exception);
            }
        }

        private long packets() { return owners.stream().mapToLong(owner -> owner.connection.packets).sum(); }
        private long bytes() { return owners.stream().mapToLong(owner -> owner.connection.payloadBytes).sum(); }

        private void cleanup() {
            if (changedPos != null) helper.getLevel().setBlockAndUpdate(changedPos, Blocks.AIR.defaultBlockState());
            if (fixture != null) fixture.close();
            fixture = null;
            owners.clear();
        }

        private void fail(Exception exception) {
            cleanup();
            MinecraftForge.EVENT_BUS.unregister(before);
            MinecraftForge.EVENT_BUS.unregister(after);
            helper.fail("Refresh benchmark: " + exception);
        }
    }

    private static final class Stats {
        final List<Double> endMs = new ArrayList<>();
        final List<Double> tickMs = new ArrayList<>();
        JsonObject record;
        long packetHead;
        long byteHead;
        long peakPackets;

        void finish(long packets, long bytes, JsonArray cases) {
            if (endMs.size() != SAMPLES || tickMs.size() != SAMPLES) {
                throw new IllegalStateException("Expected " + SAMPLES + " END samples, got " + endMs.size());
            }
            record.addProperty("sample_ticks", endMs.size());
            record.addProperty("end_p50_ms", percentile(endMs, 0.50));
            record.addProperty("end_p95_ms", percentile(endMs, 0.95));
            record.addProperty("end_max_ms", percentile(endMs, 1));
            record.addProperty("end_total_ms", endMs.stream().mapToDouble(Double::doubleValue).sum());
            record.addProperty("tick_event_p95_ms", percentile(tickMs, 0.95));
            record.addProperty("tick_event_max_ms", percentile(tickMs, 1));
            record.addProperty("packets", packets - packetHead);
            record.addProperty("payload_bytes", bytes - byteHead);
            record.addProperty("peak_packets_per_tick", peakPackets);
            cases.add(record);
            System.out.println("GSE continuous refresh: " + record);
        }

        private static double percentile(List<Double> values, double quantile) {
            var sorted = values.stream().sorted().toList();
            return sorted.get(Math.max(0, (int) Math.ceil(sorted.size() * quantile) - 1));
        }
    }
}
