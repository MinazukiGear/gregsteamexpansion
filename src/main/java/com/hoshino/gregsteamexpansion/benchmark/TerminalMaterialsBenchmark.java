package com.hoshino.gregsteamexpansion.benchmark;

import com.hoshino.gregsteamexpansion.gametest.GSEStructureTestUtils;
import com.hoshino.gregsteamexpansion.gametest.GSETerminalRefreshTestSupport;
import com.hoshino.gregsteamexpansion.registry.GSEMachines;
import com.hoshino.gregsteamexpansion.terminal.TerminalPreviewMetrics;
import com.hoshino.gregsteamexpansion.terminal.UltimateTerminalMessages;

import appeng.api.config.Actionable;
import appeng.api.implementations.blockentities.IWirelessAccessPoint;
import appeng.api.networking.IGrid;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.IStorageProvider;
import appeng.api.storage.MEStorage;
import appeng.api.storage.StorageCells;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.items.tools.powered.WirelessTerminalItem;
import appeng.me.helpers.PlayerSource;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/** Loaded only by the explicit materials benchmark, never reflected in core-only runtimes. */
public final class TerminalMaterialsBenchmark {
    private static final int WARMUP = 20;
    private static final int SAMPLES = 100;

    private TerminalMaterialsBenchmark() {}

    public static void measure(GameTestHelper helper) { new Run(helper).start(); }

    private static final class Run {
        private final GameTestHelper helper;
        private final List<BlockPos> controllers = new ArrayList<>();
        private final JsonObject report = new JsonObject();
        private final JsonArray cases = new JsonArray();
        private GSETerminalRefreshTestSupport fixture;
        private GSETerminalRefreshTestSupport.Owner owner;
        private BlockPos access;
        private IGrid grid;
        private MEStorage cell;
        private IStorageProvider provider;
        private ItemStack wireless;
        private List<ItemStack> materials = List.of();
        private boolean ae;
        private boolean linked;
        private int index;
        private int elapsed;
        private TerminalRefreshBenchmark.Stats quiet;
        private TerminalRefreshBenchmark.Stats changing;
        private long tickStart;
        private long endStart;
        private long packetsAtStart;
        private Consumer<TickEvent.ServerTickEvent> before;
        private Consumer<TickEvent.ServerTickEvent> after;

        Run(GameTestHelper helper) { this.helper = helper; }

        void start() {
            for (int z = 0; z < 4; z++) for (int x = 0; x < 4; x++) {
                place(new BlockPos(16 + x * 32, 280, 16 + z * 32), false);
            }
            place(new BlockPos(160, 280, 16), true);
            access = helper.absolutePos(new BlockPos(4, 4, 4));
            helper.getLevel().setBlockAndUpdate(access, AEBlocks.WIRELESS_ACCESS_POINT.block().defaultBlockState());
            cell = StorageCells.getCellInventory(AEItems.ITEM_CELL_64K.stack(), null);
            helper.assertTrue(cell != null, "Materials benchmark needs a real AE item cell");
            provider = mounts -> mounts.mount(cell);
            report.addProperty("java_version", System.getProperty("java.version"));
            report.addProperty("os_arch", System.getProperty("os.arch"));
            report.addProperty("warmup_ticks", WARMUP);
            report.addProperty("sample_ticks_per_phase", SAMPLES);
            report.addProperty("scope", "One survival owner per case; backpack vs backpack plus actual wireless AE grid, one global provider and one 64K item cell. Real END refresh/diff/encoding; socket-free recipients, no compression/client FPS. Event intervals are not full server MSPT.");
            report.add("cases", cases);
            helper.startSequence().thenWaitUntil(() -> helper.assertTrue(
                    helper.getLevel().getBlockEntity(access) instanceof IWirelessAccessPoint point
                            && point.getGrid() != null, "AE grid not ready"))
                    .thenExecute(() -> {
                        grid = ((IWirelessAccessPoint) helper.getLevel().getBlockEntity(access)).getGrid();
                        grid.getStorageService().addGlobalStorageProvider(provider);
                        listen();
                        nextCase();
                    });
        }

        private void place(BlockPos relative, boolean large) {
            BlockPos absolute = helper.absolutePos(relative);
            for (int dz = -1; dz <= 1; dz++) for (int dx = -1; dx <= 1; dx++) {
                helper.getLevel().getChunk((absolute.getX() >> 4) + dx, (absolute.getZ() >> 4) + dz);
            }
            GSEStructureTestUtils.placeMachine(helper,
                    large ? GSEMachines.LARGE_HEAT_STORAGE_STEAM_FURNACE : GSEMachines.STEAM_CRUSHER, relative);
            controllers.add(absolute);
        }

        private void listen() {
            before = event -> {
                if (event.getServer() != helper.getLevel().getServer() || fixture == null) return;
                if (event.phase == TickEvent.Phase.START) {
                    TerminalPreviewMetrics.begin();
                    tickStart = System.nanoTime();
                    packetsAtStart = owner.connection.packets;
                } else endStart = System.nanoTime();
            };
            after = event -> {
                if (event.getServer() != helper.getLevel().getServer() || fixture == null
                        || event.phase != TickEvent.Phase.END) return;
                long now = System.nanoTime();
                var sample = TerminalPreviewMetrics.end();
                if (elapsed < WARMUP) return;
                var stats = elapsed < WARMUP + SAMPLES ? quiet : changing;
                stats.profile.add(sample);
                stats.endMs.add((now - endStart) / 1_000_000.0);
                stats.tickMs.add((now - tickStart) / 1_000_000.0);
                stats.peakPackets = Math.max(stats.peakPackets, owner.connection.packets - packetsAtStart);
            };
            MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGH, false, TickEvent.ServerTickEvent.class, before);
            MinecraftForge.EVENT_BUS.addListener(EventPriority.LOW, false, TickEvent.ServerTickEvent.class, after);
        }

        private void nextCase() {
            try {
                if (index == 12) {
                    finish();
                    Files.writeString(Path.of(System.getProperty("gse.previewBenchmarkOutput")),
                            new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n", StandardCharsets.UTF_8);
                    helper.succeed();
                    return;
                }
                int combination = (index % 6 + (index / 6) * 3) % 6;
                ae = combination % 2 == 1;
                boolean large = combination / 2 == 2;
                int targets = combination / 2 == 1 ? 16 : 1;
                fixture = new GSETerminalRefreshTestSupport(helper);
                UUID id = UUID.nameUUIDFromBytes(("gse-materials-" + index).getBytes(StandardCharsets.UTF_8));
                owner = fixture.add(id, large ? List.of(controllers.get(16)) : controllers.subList(0, targets), 0, 5);
                var preview = fixture.data().preview(owner.player);
                materials = preview.batchMaterials().stream().map(value -> value.stack().copyWithCount(1)).toList();
                int slot = 2;
                for (ItemStack material : materials) {
                    if (!ItemStack.isSameItemSameTags(material, owner.probe)) {
                        owner.player.getInventory().setItem(slot++, material.copyWithCount(9));
                    }
                    setStored(material, 37);
                }
                wireless = AEItems.WIRELESS_TERMINAL.stack();
                setLinked(true);
                if (ae) {
                    owner.player.getInventory().setItem(35, wireless);
                    fixture.data().toggleAE(id);
                    owner.expectedNetwork = () -> linked ? cell.getAvailableStacks().get(AEItemKey.of(owner.probe)) : 0;
                }
                UltimateTerminalMessages.forceFull(owner.player);
                owner.flush();
                quiet = new TerminalRefreshBenchmark.Stats();
                changing = new TerminalRefreshBenchmark.Stats();
                quiet.record = record(large, targets, "quiet", preview.plan().cells().size());
                changing.record = record(large, targets, "changing", preview.plan().cells().size());
                elapsed = -1;
                helper.runAfterDelay(1, this::advance);
            } catch (Exception exception) { fail(exception); }
        }

        private JsonObject record(boolean large, int targets, String phase, int cells) {
            JsonObject value = new JsonObject();
            value.addProperty("round", index / 6 + 1);
            value.addProperty("fixture", large ? "heat_storage_furnace_15x15" : "steam_crusher_3x3");
            value.addProperty("owners", 1);
            value.addProperty("targets", targets);
            value.addProperty("phase", phase);
            value.addProperty("material_source", ae ? "backpack_ae" : "backpack");
            value.addProperty("batch_material_types", materials.size());
            value.addProperty("selected_cells", cells);
            return value;
        }

        private void advance() {
            try {
                owner.flush();
                elapsed++;
                if (elapsed == WARMUP) {
                    quiet.packetHead = owner.connection.packets;
                    quiet.byteHead = owner.connection.payloadBytes;
                }
                if (elapsed == WARMUP + SAMPLES) {
                    quiet.finish(owner.connection.packets, owner.connection.payloadBytes, cases);
                    changing.packetHead = owner.connection.packets;
                    changing.byteHead = owner.connection.payloadBytes;
                }
                if (elapsed == WARMUP + 2 * SAMPLES) {
                    changing.finish(owner.connection.packets, owner.connection.payloadBytes, cases);
                    cleanupCase();
                    index++;
                    helper.runAfterDelay(1, this::nextCase);
                    return;
                }
                if (elapsed >= WARMUP + SAMPLES) {
                    int sample = elapsed - WARMUP - SAMPLES;
                    owner.setInventory(5 + sample % 23);
                    setStored(owner.probe, 37 + sample % 31);
                    if (sample == 30) setLinked(false);
                    if (sample == 40) setLinked(true);
                    if (sample == 31 || sample == 41) {
                        UltimateTerminalMessages.forceFull(owner.player);
                    }
                    if (sample == 50 && owner.targets.size() > 1) {
                        fixture.select(owner, owner.targets.size() - 1);
                        UltimateTerminalMessages.forceFull(owner.player);
                    }
                }
                helper.runAfterDelay(1, this::advance);
            } catch (Exception exception) { fail(exception); }
        }

        private void setLinked(boolean value) {
            linked = value;
            if (value) WirelessTerminalItem.LINKABLE_HANDLER.link(wireless,
                    GlobalPos.of(helper.getLevel().dimension(), access));
            else WirelessTerminalItem.LINKABLE_HANDLER.unlink(wireless);
        }

        private void setStored(ItemStack stack, long amount) {
            var key = AEItemKey.of(stack);
            long old = cell.getAvailableStacks().get(key);
            var source = new PlayerSource(owner.player);
            long changed = old < amount ? cell.insert(key, amount - old, Actionable.MODULATE, source)
                    : cell.extract(key, old - amount, Actionable.MODULATE, source);
            helper.assertTrue(changed == Math.abs(amount - old), "AE cell mutation did not apply exact amount");
        }

        private void cleanupCase() {
            TerminalPreviewMetrics.end();
            if (owner != null) for (ItemStack material : materials) setStored(material, 0);
            if (fixture != null) fixture.close();
            fixture = null;
            owner = null;
        }

        private void finish() {
            cleanupCase();
            if (before != null) MinecraftForge.EVENT_BUS.unregister(before);
            if (after != null) MinecraftForge.EVENT_BUS.unregister(after);
            if (grid != null) grid.getStorageService().removeGlobalStorageProvider(provider);
        }

        private void fail(Exception exception) {
            finish();
            helper.fail("Materials benchmark: " + exception);
        }
    }
}
