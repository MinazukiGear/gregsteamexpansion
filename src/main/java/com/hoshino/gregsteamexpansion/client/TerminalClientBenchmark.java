package com.hoshino.gregsteamexpansion.client;

import com.hoshino.gregsteamexpansion.registry.GSEBlocks;
import com.hoshino.gregsteamexpansion.registry.GSEMachines;
import com.hoshino.gregsteamexpansion.terminal.UltimateStructurePlanner;
import com.hoshino.gregsteamexpansion.terminal.UltimateTerminalMessages;
import com.hoshino.gregsteamexpansion.terminal.UltimateTerminalSnapshot;
import com.hoshino.gregsteamexpansion.terminal.UltimateTerminalWorldData;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import org.lwjgl.opengl.GL11;

import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Real OpenGL client, replaying server-authored private selected snapshots in a scratch flat world.
 * Packet transport, server planning and GPU execution time are deliberately outside this benchmark. */
public final class TerminalClientBenchmark {
    private static final int WARMUP = 240, SAMPLES = 600;
    private static final List<Workload> WORKLOADS = workloads();
    private static final JsonObject REPORT = new JsonObject();
    private static final JsonArray CASES = new JsonArray();
    private static final long[][] VALUES = new long[5][SAMPLES];
    private static CompletableFuture<List<UltimateTerminalSnapshot>> fixtures;
    private static List<UltimateTerminalSnapshot> snapshots;
    private static boolean started, preparing, finished;
    private static int caseIndex, frame, clientTicks, lastChange, mutations;
    private static long previousFrame, interval, gcStart;
    private static int ghostMin, ghostMax, outlineMax;

    private TerminalClientBenchmark() {}

    static void register(IEventBus bus) {
        bus.addListener(TerminalClientBenchmark::tick);
        bus.addListener(TerminalClientBenchmark::render);
    }

    private static List<Workload> workloads() {
        List<Workload> result = new ArrayList<>();
        for (int round = 1; round <= 2; round++) {
            List<Workload> cases = new ArrayList<>();
            cases.add(new Workload(round, "none", "stationary", 0));
            for (int fixture = 1; fixture <= 3; fixture++) {
                String name = fixture == 1 ? "crusher_1" : fixture == 2 ? "crusher_16" : "furnace_15x15";
                for (String action : List.of("stationary", "camera", "switch", "status_delta")) {
                    cases.add(new Workload(round, name, action, fixture));
                }
            }
            cases.add(new Workload(round, "furnace_15x15", "full", 3));
            cases.add(new Workload(round, "furnace_15x15", "layer", 3));
            java.util.Collections.rotate(cases, (round - 1) * 7);
            result.addAll(cases);
        }
        return List.copyOf(result);
    }

    private static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || finished) return;
        Minecraft mc = Minecraft.getInstance();
        try {
            if (!started && mc.screen instanceof TitleScreen) {
                Path directory = mc.gameDirectory.toPath().toAbsolutePath().normalize();
                Path output = Path.of(System.getProperty("gse.clientBenchmarkOutput")).toAbsolutePath().normalize();
                if (!directory.endsWith(Path.of("build", "verification", "terminal-client"))
                        || !output.startsWith(directory.resolve("reports"))) {
                    throw new IllegalStateException("Client benchmark requires its isolated directory and report path");
                }
                started = true;
                mc.options.pauseOnLostFocus = false;
                mc.options.enableVsync().set(false);
                mc.options.framerateLimit().set(120);
                mc.options.renderDistance().set(4);
                mc.options.simulationDistance().set(4);
                mc.options.hideGui = true;
                mc.options.fov().set(70);
                mc.getWindow().updateVsync(false);
                mc.createWorldOpenFlows().createFreshLevel("benchmark-" + System.currentTimeMillis(),
                        new LevelSettings("Terminal client benchmark", GameType.CREATIVE, false, Difficulty.PEACEFUL,
                                true, new GameRules(), WorldDataConfiguration.DEFAULT),
                        new WorldOptions(15102026L, false, false),
                        access -> access.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT)
                                .value().createWorldDimensions());
            }
            if (mc.player == null || mc.level == null || mc.getSingleplayerServer() == null) return;
            if (fixtures == null) {
                fixtures = CompletableFuture.supplyAsync(() -> makeFixtures(mc), mc.getSingleplayerServer());
            }
            if (snapshots == null && fixtures.isDone()) {
                snapshots = fixtures.join();
                initializeReport(mc);
                if (Boolean.getBoolean("gse.clientBenchmarkChecksOnly")) {
                    finish();
                    return;
                }
                prepare();
            }
            if (snapshots == null || preparing) return;
            clientTicks++;
            Workload work = WORKLOADS.get(caseIndex);
            if (work.action.equals("camera")) {
                mc.player.setYRot(180 + (float) (12 * Math.sin(clientTicks * 0.08)));
                mc.player.yRotO = mc.player.getYRot();
            }
            if (work.action.equals("switch") && clientTicks - lastChange >= 10) {
                lastChange = clientTicks;
                mutations++;
                // Two real target geometries for the sixteen-target case; full snapshot replacement otherwise.
                UltimateTerminalClientState.accept(snapshots.get(work.fixture == 2 && mutations % 2 == 1 ? 4 : work.fixture));
                positionCamera();
            }
            if (work.action.equals("status_delta") && clientTicks - lastChange >= 10) {
                lastChange = clientTicks;
                mutations++;
                var current = UltimateTerminalClientState.snapshot();
                var status = mutations % 2 == 1 ? UltimateStructurePlanner.CellStatus.SATISFIED
                        : snapshots.get(work.fixture).cells().get(0).status();
                UltimateTerminalClientState.apply(new UltimateTerminalMessages.DeltaPacket(current.revision(),
                        List.of(new UltimateTerminalMessages.CellDelta(0, status)), current.selectedMaterials(),
                        current.batchMaterials(), current.candidates(), current.channels(), current.structure(),
                        current.module(), current.targetOverride(), current.unlimitedMaterials(), current.error()));
            }
        } catch (Exception exception) { fail(exception); }
    }

    private static List<UltimateTerminalSnapshot> makeFixtures(Minecraft mc) {
        var server = mc.getSingleplayerServer();
        var level = server.overworld();
        var player = server.getPlayerList().getPlayer(mc.player.getUUID());
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
        level.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false, server);
        level.setDayTime(6000);
        List<BlockPos> controllers = new ArrayList<>();
        for (int i = 0; i < 16; i++) {
            BlockPos pos = new BlockPos((i % 4) * 20, 80, (i / 4) * 20);
            level.setBlockAndUpdate(pos, GSEMachines.STEAM_CRUSHER.defaultBlockState());
            controllers.add(pos);
        }
        BlockPos large = new BlockPos(0, 80, -40);
        for (int x = -2; x <= 5; x++) for (int z = -4; z <= 5; z++) level.getChunk(x, z);
        level.setBlockAndUpdate(large, GSEMachines.LARGE_HEAT_STORAGE_STEAM_FURNACE.defaultBlockState());
        var data = UltimateTerminalWorldData.load(new CompoundTag());
        List<UltimateTerminalSnapshot> result = new ArrayList<>();
        result.add(UltimateTerminalSnapshot.from(data.preview(player), 1));
        check(data.addTarget(player, level.dimension(), controllers.get(0)), "Small target rejected");
        result.add(UltimateTerminalSnapshot.from(data.preview(player), 2));
        for (int i = 1; i < 16; i++) check(data.addTarget(player, level.dimension(), controllers.get(i)), "Target rejected");
        data.selectTarget(player, 0);
        result.add(UltimateTerminalSnapshot.from(data.preview(player), 3));
        var largeData = UltimateTerminalWorldData.load(new CompoundTag());
        check(largeData.addTarget(player, level.dimension(), large), "Large target rejected");
        result.add(UltimateTerminalSnapshot.from(largeData.preview(player), 4));
        data.selectTarget(player, 1);
        result.add(UltimateTerminalSnapshot.from(data.preview(player), 5));
        check(result.get(2).targets().size() == 16 && result.get(2).cells().size() == result.get(1).cells().size(),
                "Sixteen targets must contain only one selected geometry");
        for (int i = 1; i < result.size(); i++) check(!result.get(i).cells().isEmpty(), "Empty fixture " + i);
        player.teleportTo(level, 0, 85, 20, 180, 18);
        player.getAbilities().flying = true;
        player.onUpdateAbilities();
        return List.copyOf(result);
    }

    private static void initializeReport(Minecraft mc) {
        check(mc.getWindow().getWidth() == 1280 && mc.getWindow().getHeight() == 720, "Window must be 1280x720");
        REPORT.addProperty("schema", 1);
        REPORT.addProperty("checks_only", Boolean.getBoolean("gse.clientBenchmarkChecksOnly"));
        REPORT.addProperty("java", System.getProperty("java.version"));
        REPORT.addProperty("os", System.getProperty("os.name"));
        REPORT.addProperty("architecture", System.getProperty("os.arch"));
        REPORT.addProperty("gpu", GL11.glGetString(GL11.GL_RENDERER));
        REPORT.addProperty("opengl", GL11.glGetString(GL11.GL_VERSION));
        REPORT.addProperty("width", mc.getWindow().getWidth());
        REPORT.addProperty("height", mc.getWindow().getHeight());
        REPORT.addProperty("fps_limit", 120);
        REPORT.addProperty("render_distance", 4);
        REPORT.addProperty("warmup_frames", WARMUP);
        REPORT.addProperty("samples", SAMPLES);
        REPORT.addProperty("allocation_supported", TerminalClientMetrics.allocationSupported());
        REPORT.addProperty("scope", "Actual OpenGL frame intervals and render-thread CPU submission/allocation; server-authored snapshot replay. No GPU query, transport or live server refresh timing.");
        REPORT.add("cases", CASES);
    }

    private static void prepare() {
        preparing = true;
        frame = -WARMUP;
        ghostMin = Integer.MAX_VALUE;
        ghostMax = outlineMax = mutations = clientTicks = lastChange = 0;
        UltimateTerminalClientState.clear();
        var work = WORKLOADS.get(caseIndex);
        UltimateTerminalClientState.accept(snapshots.get(work.fixture));
        if (work.action.equals("full")) UltimateTerminalClientState.cycleDisplayMode();
        if (work.action.equals("layer")) {
            UltimateTerminalClientState.cycleDisplayMode();
            UltimateTerminalClientState.cycleDisplayMode();
        }
        Minecraft mc = Minecraft.getInstance();
        mc.player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(GSEBlocks.ULTIMATE_TERMINAL.get()));
        mc.player.getAbilities().flying = true;
        mc.player.setNoGravity(true);
        mc.setScreen(null);
        positionCamera();
        gcStart = gcCount();
        preparing = false;
    }

    private static void positionCamera() {
        Minecraft mc = Minecraft.getInstance();
        var current = UltimateTerminalClientState.snapshot();
        BlockPos pos = current.controller();
        double y = current.cells().stream().mapToInt(cell -> cell.pos().getY()).average().orElse(80);
        mc.player.setPos(pos.getX() + 0.5, y + 5, pos.getZ() + 23.5);
        mc.player.xo = mc.player.getX(); mc.player.yo = mc.player.getY(); mc.player.zo = mc.player.getZ();
        mc.player.setYRot(180); mc.player.yRotO = 180;
        mc.player.setXRot(18); mc.player.xRotO = 18;
    }

    private static void render(TickEvent.RenderTickEvent event) {
        if (snapshots == null || preparing || finished) return;
        try {
            if (event.phase == TickEvent.Phase.START) {
                long now = System.nanoTime();
                interval = previousFrame == 0 ? 0 : now - previousFrame;
                previousFrame = now;
                TerminalClientMetrics.reset();
                return;
            }
            if (frame >= 0) {
                VALUES[0][frame] = interval;
                VALUES[1][frame] = TerminalClientMetrics.drawNanos;
                VALUES[2][frame] = TerminalClientMetrics.ghostNanos;
                VALUES[3][frame] = TerminalClientMetrics.outlineNanos;
                VALUES[4][frame] = TerminalClientMetrics.allocatedBytes;
                ghostMin = Math.min(ghostMin, TerminalClientMetrics.ghosts);
                ghostMax = Math.max(ghostMax, TerminalClientMetrics.ghosts);
                outlineMax = Math.max(outlineMax, TerminalClientMetrics.outlines);
            }
            if (++frame != SAMPLES) return;
            record();
            if (++caseIndex == WORKLOADS.size()) { finish(); return; }
            prepare();
        } catch (Exception exception) { fail(exception); }
    }

    private static void record() throws Exception {
        var work = WORKLOADS.get(caseIndex);
        JsonObject result = new JsonObject();
        result.addProperty("round", work.round);
        result.addProperty("fixture", work.name);
        result.addProperty("action", work.action);
        result.addProperty("selected_cells", snapshots.get(work.fixture).cells().size());
        result.addProperty("targets", snapshots.get(work.fixture).targets().size());
        result.addProperty("ghost_min", ghostMin);
        result.addProperty("ghost_max", ghostMax);
        result.addProperty("outline_max", outlineMax);
        result.addProperty("mutations", mutations);
        result.addProperty("gc_collections", gcCount() - gcStart);
        String[] names = {"frame", "draw", "ghost", "outline", "allocated_bytes"};
        for (int i = 0; i < names.length; i++) {
            JsonArray raw = new JsonArray();
            for (long value : VALUES[i]) raw.add(value);
            result.add(names[i] + "_raw", raw);
            long[] sorted = VALUES[i].clone();
            Arrays.sort(sorted);
            double scale = i == 4 ? 1 : 1_000_000.0;
            result.addProperty(names[i] + "_p50", sorted[SAMPLES / 2] / scale);
            result.addProperty(names[i] + "_p95", sorted[(int) Math.ceil(SAMPLES * .95) - 1] / scale);
            result.addProperty(names[i] + "_p99", sorted[(int) Math.ceil(SAMPLES * .99) - 1] / scale);
            result.addProperty(names[i] + "_mean", Arrays.stream(VALUES[i]).average().orElse(0) / scale);
        }
        check(work.fixture == 0 ? ghostMax == 0 && outlineMax == 0 : ghostMax > 0, "Preview did not render");
        if (work.action.equals("switch") || work.action.equals("status_delta")) check(mutations > 1, "No mutations sampled");
        CASES.add(result);
        writeReport();
        System.out.println("GSE client benchmark: round=" + work.round + " fixture=" + work.name
                + " action=" + work.action + " draw-p95=" + result.get("draw_p95") + "ms");
        if (work.round == 1 && work.action.equals("stationary") && work.fixture != 0) {
            Path directory = Path.of(System.getProperty("gse.clientBenchmarkOutput")).getParent();
            Screenshot.grab(directory.toFile(), work.name + ".png", Minecraft.getInstance().getMainRenderTarget(), ignored -> {});
        }
    }

    private static void finish() throws Exception {
        // clearLevel renders a saving screen and can re-enter RenderTick while unloading.
        finished = true;
        REPORT.addProperty("vertex_parity_quads", TerminalHologramVertexChecks.verify(snapshots));
        UltimateTerminalClientState.clear();
        check(UltimateTerminalClientState.snapshot() == null
                && UltimateTerminalClientState.displayMode() == UltimateTerminalClientState.DisplayMode.DIFFERENCES,
                "Preview state not cleared");
        REPORT.addProperty("clear_verified", true);
        UltimateTerminalClientState.accept(snapshots.get(3));
        // Match the vanilla disconnect-button order; clearLevel waits for the
        // integrated server to stop and does not itself disconnect its socket.
        Minecraft.getInstance().level.disconnect();
        Minecraft.getInstance().clearLevel();
        check(UltimateTerminalClientState.snapshot() == null, "Logout did not clear preview state");
        REPORT.addProperty("logout_verified", true);
        REPORT.addProperty("complete", true);
        writeReport();
        finished = true;
        Minecraft.getInstance().stop();
    }

    private static void writeReport() throws Exception {
        Files.writeString(Path.of(System.getProperty("gse.clientBenchmarkOutput")),
                new GsonBuilder().setPrettyPrinting().create().toJson(REPORT) + "\n");
    }

    private static long gcCount() {
        return ManagementFactory.getGarbageCollectorMXBeans().stream().mapToLong(bean -> Math.max(0, bean.getCollectionCount())).sum();
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private static void fail(Exception exception) {
        finished = true;
        exception.printStackTrace();
        if (started) {
            REPORT.addProperty("complete", false);
            REPORT.addProperty("failure", exception.toString());
            try { writeReport(); } catch (Exception writeFailure) { writeFailure.printStackTrace(); }
        }
        Minecraft.getInstance().stop();
    }

    private record Workload(int round, String name, String action, int fixture) {}
}
