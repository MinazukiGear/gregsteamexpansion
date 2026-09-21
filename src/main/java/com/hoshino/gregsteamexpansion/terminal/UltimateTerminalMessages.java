package com.hoshino.gregsteamexpansion.terminal;

import com.hoshino.gregsteamexpansion.GregSteamExpansion;
import com.hoshino.gregsteamexpansion.client.UltimateTerminalClientState;
import com.hoshino.gregsteamexpansion.menu.UltimateTerminalMenu;
import com.hoshino.gregsteamexpansion.registry.GSEBlocks;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/** Authenticated terminal preview/configuration protocol with bounded snapshot payloads. */
public final class UltimateTerminalMessages {
    private static final String PROTOCOL = "5";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            GregSteamExpansion.id("ultimate_terminal"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    private UltimateTerminalMessages() {}

    public static void register() {
        int id = 0;
        CHANNEL.registerMessage(id++, RequestPacket.class, RequestPacket::encode, RequestPacket::decode,
                RequestPacket::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, SelectTargetPacket.class, SelectTargetPacket::encode, SelectTargetPacket::decode,
                SelectTargetPacket::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, PartPacket.class, PartPacket::encode, PartPacket::decode,
                PartPacket::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, ChannelPacket.class, ChannelPacket::encode, ChannelPacket::decode,
                ChannelPacket::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, FullPacket.class, FullPacket::encode, FullPacket::decode,
                FullPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id, DeltaPacket.class, DeltaPacket::encode, DeltaPacket::decode,
                DeltaPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    public static void requestFromClient(int containerId) {
        CHANNEL.sendToServer(new RequestPacket(containerId));
    }

    public static void selectFromClient(int containerId, int target) {
        CHANNEL.sendToServer(new SelectTargetPacket(containerId, target));
    }

    public static void changePartFromClient(int containerId, ResourceLocation blockId, int delta) {
        CHANNEL.sendToServer(new PartPacket(containerId, blockId, Integer.signum(delta)));
    }

    public static void selectChannelFromClient(int containerId, String channelId, int selection) {
        CHANNEL.sendToServer(new ChannelPacket(containerId, channelId, selection));
    }

    public static void forceFull(ServerPlayer player) {
        if (!TerminalCompatibility.isAvailable() || player.connection == null) return;
        Session previous = SESSIONS.get(player.getUUID());
        int revision = previous == null ? 1 : previous.snapshot.revision() + 1;
        UltimateTerminalSnapshot snapshot = UltimateTerminalSnapshot.from(
                UltimateTerminalWorldData.get(player.server).preview(player), revision);
        SESSIONS.put(player.getUUID(), new Session(snapshot));
        CHANNEL.sendTo(new FullPacket(snapshot), player.connection.connection, NetworkDirection.PLAY_TO_CLIENT);
    }

    public static void tick(MinecraftServer server) {
        if (server.getTickCount() % 10 != 0 || SESSIONS.isEmpty()) return;
        for (var entry : new ArrayList<>(SESSIONS.entrySet())) {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) {
                SESSIONS.remove(entry.getKey());
                continue;
            }
            if (!isHolding(player)) continue;
            Session session = entry.getValue();
            UltimateTerminalSnapshot next = UltimateTerminalSnapshot.from(
                    UltimateTerminalWorldData.get(server).preview(player), session.snapshot.revision());
            if (!sameGeometry(session.snapshot, next)) {
                next = new UltimateTerminalSnapshot(session.snapshot.revision() + 1, next.targets(),
                        next.selectedTarget(), next.dimension(), next.controller(), next.cells(),
                        next.selectedMaterials(), next.batchMaterials(), next.candidates(),
                        next.channels(), next.structure(), next.module(), next.targetOverride(),
                        next.unlimitedMaterials(), next.error());
                session.snapshot = next;
                CHANNEL.sendTo(new FullPacket(next), player.connection.connection, NetworkDirection.PLAY_TO_CLIENT);
                continue;
            }
            List<CellDelta> changes = new ArrayList<>();
            for (int i = 0; i < next.cells().size(); i++) {
                if (next.cells().get(i).status() != session.snapshot.cells().get(i).status()) {
                    changes.add(new CellDelta(i, next.cells().get(i).status()));
                }
            }
            boolean summaryChanged = !sameMaterials(next.selectedMaterials(), session.snapshot.selectedMaterials())
                    || !sameMaterials(next.batchMaterials(), session.snapshot.batchMaterials())
                    || !sameCandidates(next.candidates(), session.snapshot.candidates())
                    || !next.channels().equals(session.snapshot.channels())
                    || !next.structure().equals(session.snapshot.structure())
                    || !next.module().equals(session.snapshot.module())
                    || next.targetOverride() != session.snapshot.targetOverride()
                    || next.unlimitedMaterials() != session.snapshot.unlimitedMaterials()
                    || !next.error().equals(session.snapshot.error());
            if (!changes.isEmpty() || summaryChanged) {
                session.snapshot = next;
                CHANNEL.sendTo(new DeltaPacket(next.revision(), changes, next.selectedMaterials(),
                                next.batchMaterials(), next.candidates(), next.channels(), next.structure(),
                                next.module(), next.targetOverride(), next.unlimitedMaterials(), next.error()),
                        player.connection.connection, NetworkDirection.PLAY_TO_CLIENT);
            }
        }
    }

    private static boolean sameGeometry(UltimateTerminalSnapshot first, UltimateTerminalSnapshot second) {
        if (!first.targets().equals(second.targets())
                || first.selectedTarget() != second.selectedTarget()
                || !first.dimension().equals(second.dimension())
                || !first.controller().equals(second.controller())
                || first.cells().size() != second.cells().size()) return false;
        for (int i = 0; i < first.cells().size(); i++) {
            var a = first.cells().get(i);
            var b = second.cells().get(i);
            if (!a.pos().equals(b.pos()) || !ItemStack.isSameItemSameTags(a.expected(), b.expected())) return false;
        }
        return true;
    }

    private static boolean sameMaterials(List<UltimateTerminalSnapshot.MaterialInfo> first,
                                         List<UltimateTerminalSnapshot.MaterialInfo> second) {
        if (first.size() != second.size()) return false;
        for (int i = 0; i < first.size(); i++) {
            var a = first.get(i);
            var b = second.get(i);
            if (!ItemStack.isSameItemSameTags(a.stack(), b.stack())
                    || a.required() != b.required() || a.inventory() != b.inventory()
                    || a.network() != b.network()) return false;
        }
        return true;
    }

    private static boolean sameCandidates(List<UltimateTerminalSnapshot.CandidateInfo> first,
                                          List<UltimateTerminalSnapshot.CandidateInfo> second) {
        if (first.size() != second.size()) return false;
        for (int i = 0; i < first.size(); i++) {
            var a = first.get(i);
            var b = second.get(i);
            if (!ItemStack.isSameItemSameTags(a.stack(), b.stack())
                    || a.requested() != b.requested() || a.present() != b.present()
                    || a.maximum() != b.maximum() || a.configurable() != b.configurable()) return false;
        }
        return true;
    }

    private static boolean isHolding(ServerPlayer player) {
        return player.getMainHandItem().is(GSEBlocks.ULTIMATE_TERMINAL.get())
                || player.getOffhandItem().is(GSEBlocks.ULTIMATE_TERMINAL.get());
    }

    private static boolean validMenu(ServerPlayer player, int containerId) {
        return player.containerMenu instanceof UltimateTerminalMenu && player.containerMenu.containerId == containerId;
    }

    private static void writeSnapshot(FriendlyByteBuf buf, UltimateTerminalSnapshot value) {
        buf.writeVarInt(value.revision());
        buf.writeVarInt(value.targets().size());
        for (var target : value.targets()) {
            buf.writeResourceLocation(target.dimension());
            buf.writeBlockPos(target.pos());
            buf.writeUtf(target.name(), 128);
        }
        buf.writeVarInt(value.selectedTarget());
        buf.writeResourceLocation(value.dimension());
        buf.writeBlockPos(value.controller());
        buf.writeVarInt(value.cells().size());
        for (var cell : value.cells()) {
            buf.writeBlockPos(cell.pos());
            buf.writeItem(cell.expected());
            buf.writeByte(cell.status().ordinal());
        }
        writeMaterials(buf, value.selectedMaterials());
        writeMaterials(buf, value.batchMaterials());
        writeCandidates(buf, value.candidates());
        writeChannels(buf, value.channels());
        writeStructure(buf, value.structure());
        writeModule(buf, value.module());
        buf.writeBoolean(value.targetOverride());
        buf.writeBoolean(value.unlimitedMaterials());
        buf.writeUtf(value.error(), 256);
    }

    private static UltimateTerminalSnapshot readSnapshot(FriendlyByteBuf buf) {
        int revision = buf.readVarInt();
        int targetCount = bounded(buf.readVarInt(), UltimateTerminalWorldData.MAX_TARGETS);
        List<UltimateTerminalSnapshot.TargetInfo> targets = new ArrayList<>(targetCount);
        for (int i = 0; i < targetCount; i++) targets.add(new UltimateTerminalSnapshot.TargetInfo(
                buf.readResourceLocation(), buf.readBlockPos(), buf.readUtf(128)));
        int selected = buf.readVarInt();
        ResourceLocation dimension = buf.readResourceLocation();
        var controller = buf.readBlockPos();
        int cellCount = bounded(buf.readVarInt(), UltimateTerminalSnapshot.MAX_CELLS);
        List<UltimateTerminalSnapshot.CellInfo> cells = new ArrayList<>(cellCount);
        for (int i = 0; i < cellCount; i++) cells.add(new UltimateTerminalSnapshot.CellInfo(
                buf.readBlockPos(), buf.readItem(), status(buf.readUnsignedByte())));
        return new UltimateTerminalSnapshot(revision, List.copyOf(targets), selected, dimension, controller,
                List.copyOf(cells), readMaterials(buf), readMaterials(buf), readCandidates(buf),
                readChannels(buf), readStructure(buf), readModule(buf), buf.readBoolean(), buf.readBoolean(),
                buf.readUtf(256));
    }

    private static void writeMaterials(FriendlyByteBuf buf, List<UltimateTerminalSnapshot.MaterialInfo> values) {
        buf.writeVarInt(values.size());
        for (var value : values) {
            buf.writeItem(value.stack());
            buf.writeVarLong(value.required());
            buf.writeVarLong(value.inventory());
            buf.writeVarLong(value.network());
        }
    }

    private static List<UltimateTerminalSnapshot.MaterialInfo> readMaterials(FriendlyByteBuf buf) {
        int size = bounded(buf.readVarInt(), UltimateTerminalSnapshot.MAX_MATERIALS);
        List<UltimateTerminalSnapshot.MaterialInfo> values = new ArrayList<>(size);
        for (int i = 0; i < size; i++) values.add(new UltimateTerminalSnapshot.MaterialInfo(
                buf.readItem(), buf.readVarLong(), buf.readVarLong(), buf.readVarLong()));
        return List.copyOf(values);
    }

    private static void writeCandidates(FriendlyByteBuf buf, List<UltimateTerminalSnapshot.CandidateInfo> values) {
        buf.writeVarInt(values.size());
        for (var value : values) {
            buf.writeItem(value.stack());
            buf.writeVarInt(value.requested());
            buf.writeVarInt(value.present());
            buf.writeVarInt(value.maximum());
            buf.writeBoolean(value.configurable());
        }
    }

    private static List<UltimateTerminalSnapshot.CandidateInfo> readCandidates(FriendlyByteBuf buf) {
        int size = bounded(buf.readVarInt(), UltimateTerminalSnapshot.MAX_CANDIDATES);
        List<UltimateTerminalSnapshot.CandidateInfo> values = new ArrayList<>(size);
        for (int i = 0; i < size; i++) values.add(new UltimateTerminalSnapshot.CandidateInfo(
                buf.readItem(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean()));
        return List.copyOf(values);
    }

    private static void writeChannels(FriendlyByteBuf buf, List<UltimateTerminalSnapshot.ChannelInfo> values) {
        buf.writeVarInt(values.size());
        for (var value : values) {
            buf.writeUtf(value.id(), 32);
            buf.writeVarInt(value.selected());
            buf.writeVarInt(value.options().size());
            for (ItemStack option : value.options()) buf.writeItem(option);
        }
    }

    private static List<UltimateTerminalSnapshot.ChannelInfo> readChannels(FriendlyByteBuf buf) {
        int size = bounded(buf.readVarInt(), UltimateTerminalSnapshot.MAX_CHANNELS);
        List<UltimateTerminalSnapshot.ChannelInfo> values = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            String id = buf.readUtf(32);
            int selected = buf.readVarInt();
            int optionCount = bounded(buf.readVarInt(), UltimateTerminalConfig.MAX_CHANNEL_OPTIONS);
            List<ItemStack> options = new ArrayList<>(optionCount);
            for (int j = 0; j < optionCount; j++) options.add(buf.readItem());
            values.add(new UltimateTerminalSnapshot.ChannelInfo(id,
                    Math.max(UltimateTerminalConfig.AUTO_CHANNEL_SELECTION,
                            Math.min(selected, optionCount - 1)), List.copyOf(options)));
        }
        return List.copyOf(values);
    }

    private static void writeStructure(FriendlyByteBuf buf, UltimateTerminalSnapshot.StructureInfo value) {
        buf.writeVarInt(value.selected());
        buf.writeVarInt(value.options().size());
        for (String option : value.options()) buf.writeUtf(option, 32);
    }

    private static UltimateTerminalSnapshot.StructureInfo readStructure(FriendlyByteBuf buf) {
        int selected = buf.readVarInt();
        int size = bounded(buf.readVarInt(), 32);
        List<String> options = new ArrayList<>(size);
        for (int i = 0; i < size; i++) options.add(buf.readUtf(32));
        return new UltimateTerminalSnapshot.StructureInfo(Math.max(0, Math.min(selected, size)),
                List.copyOf(options));
    }

    private static void writeModule(FriendlyByteBuf buf, UltimateTerminalSnapshot.ModuleInfo value) {
        buf.writeVarInt(value.selected());
        buf.writeVarInt(value.options().size());
        for (String option : value.options()) buf.writeUtf(option, 128);
    }

    private static UltimateTerminalSnapshot.ModuleInfo readModule(FriendlyByteBuf buf) {
        int selected = buf.readVarInt();
        int size = bounded(buf.readVarInt(), UltimateTerminalConfig.MAX_CHANNEL_OPTIONS);
        List<String> options = new ArrayList<>(size);
        for (int i = 0; i < size; i++) options.add(buf.readUtf(128));
        return new UltimateTerminalSnapshot.ModuleInfo(
                Math.max(UltimateTerminalConfig.AUTO_CHANNEL_SELECTION,
                        Math.min(selected, size - 1)), List.copyOf(options));
    }

    private static int bounded(int value, int maximum) {
        if (value < 0 || value > maximum) throw new IllegalArgumentException("Terminal packet list exceeds " + maximum);
        return value;
    }

    private static UltimateStructurePlanner.CellStatus status(int ordinal) {
        var values = UltimateStructurePlanner.CellStatus.values();
        if (ordinal < 0 || ordinal >= values.length) throw new IllegalArgumentException("Unknown terminal cell status");
        return values[ordinal];
    }

    private static final class Session {
        UltimateTerminalSnapshot snapshot;
        Session(UltimateTerminalSnapshot snapshot) { this.snapshot = snapshot; }
    }

    public record RequestPacket(int containerId) {
        static void encode(RequestPacket packet, FriendlyByteBuf buf) { buf.writeVarInt(packet.containerId); }
        static RequestPacket decode(FriendlyByteBuf buf) { return new RequestPacket(buf.readVarInt()); }
        static void handle(RequestPacket packet, Supplier<NetworkEvent.Context> supplier) {
            NetworkEvent.Context context = supplier.get();
            ServerPlayer sender = context.getSender();
            if (sender != null) context.enqueueWork(() -> {
                if (TerminalCompatibility.isAvailable()
                        && (validMenu(sender, packet.containerId) || isHolding(sender))) forceFull(sender);
            });
            context.setPacketHandled(true);
        }
    }

    public record SelectTargetPacket(int containerId, int target) {
        static void encode(SelectTargetPacket packet, FriendlyByteBuf buf) {
            buf.writeVarInt(packet.containerId); buf.writeVarInt(packet.target);
        }
        static SelectTargetPacket decode(FriendlyByteBuf buf) {
            return new SelectTargetPacket(buf.readVarInt(), buf.readVarInt());
        }
        static void handle(SelectTargetPacket packet, Supplier<NetworkEvent.Context> supplier) {
            NetworkEvent.Context context = supplier.get();
            ServerPlayer sender = context.getSender();
            if (sender != null) context.enqueueWork(() -> {
                if (validMenu(sender, packet.containerId)) {
                    UltimateTerminalWorldData.get(sender.server).selectTarget(sender, packet.target);
                    forceFull(sender);
                }
            });
            context.setPacketHandled(true);
        }
    }

    public record PartPacket(int containerId, ResourceLocation blockId, int delta) {
        static void encode(PartPacket packet, FriendlyByteBuf buf) {
            buf.writeVarInt(packet.containerId); buf.writeResourceLocation(packet.blockId); buf.writeByte(packet.delta);
        }
        static PartPacket decode(FriendlyByteBuf buf) {
            return new PartPacket(buf.readVarInt(), buf.readResourceLocation(), Integer.signum(buf.readByte()));
        }
        static void handle(PartPacket packet, Supplier<NetworkEvent.Context> supplier) {
            NetworkEvent.Context context = supplier.get();
            ServerPlayer sender = context.getSender();
            if (sender != null) context.enqueueWork(() -> {
                if (validMenu(sender, packet.containerId) && packet.delta != 0) {
                    UltimateTerminalWorldData.get(sender.server).changePart(sender, packet.blockId, packet.delta);
                    forceFull(sender);
                }
            });
            context.setPacketHandled(true);
        }
    }

    public record ChannelPacket(int containerId, String channelId, int selection) {
        static void encode(ChannelPacket packet, FriendlyByteBuf buf) {
            buf.writeVarInt(packet.containerId); buf.writeUtf(packet.channelId, 32); buf.writeVarInt(packet.selection);
        }
        static ChannelPacket decode(FriendlyByteBuf buf) {
            return new ChannelPacket(buf.readVarInt(), buf.readUtf(32), buf.readVarInt());
        }
        static void handle(ChannelPacket packet, Supplier<NetworkEvent.Context> supplier) {
            NetworkEvent.Context context = supplier.get();
            ServerPlayer sender = context.getSender();
            if (sender != null) context.enqueueWork(() -> {
                if (validMenu(sender, packet.containerId)
                        && packet.selection >= UltimateTerminalConfig.AUTO_CHANNEL_SELECTION
                        && packet.selection <= UltimateTerminalConfig.MAX_CHANNEL_INDEX) {
                    UltimateTerminalWorldData.get(sender.server)
                            .setChannel(sender, packet.channelId, packet.selection);
                    forceFull(sender);
                }
            });
            context.setPacketHandled(true);
        }
    }

    public record FullPacket(UltimateTerminalSnapshot snapshot) {
        static void encode(FullPacket packet, FriendlyByteBuf buf) { writeSnapshot(buf, packet.snapshot); }
        static FullPacket decode(FriendlyByteBuf buf) { return new FullPacket(readSnapshot(buf)); }
        static void handle(FullPacket packet, Supplier<NetworkEvent.Context> supplier) {
            NetworkEvent.Context context = supplier.get();
            context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> UltimateTerminalClientState.accept(packet.snapshot)));
            context.setPacketHandled(true);
        }
    }

    public record CellDelta(int index, UltimateStructurePlanner.CellStatus status) {}

    public record DeltaPacket(int revision, List<CellDelta> changes,
                              List<UltimateTerminalSnapshot.MaterialInfo> selectedMaterials,
                              List<UltimateTerminalSnapshot.MaterialInfo> batchMaterials,
                              List<UltimateTerminalSnapshot.CandidateInfo> candidates,
                              List<UltimateTerminalSnapshot.ChannelInfo> channels,
                              UltimateTerminalSnapshot.StructureInfo structure,
                              UltimateTerminalSnapshot.ModuleInfo module,
                              boolean targetOverride, boolean unlimitedMaterials, String error) {
        static void encode(DeltaPacket packet, FriendlyByteBuf buf) {
            buf.writeVarInt(packet.revision);
            buf.writeVarInt(packet.changes.size());
            for (CellDelta change : packet.changes) {
                buf.writeVarInt(change.index); buf.writeByte(change.status.ordinal());
            }
            writeMaterials(buf, packet.selectedMaterials);
            writeMaterials(buf, packet.batchMaterials);
            writeCandidates(buf, packet.candidates);
            writeChannels(buf, packet.channels);
            writeStructure(buf, packet.structure);
            writeModule(buf, packet.module);
            buf.writeBoolean(packet.targetOverride);
            buf.writeBoolean(packet.unlimitedMaterials);
            buf.writeUtf(packet.error, 256);
        }
        static DeltaPacket decode(FriendlyByteBuf buf) {
            int revision = buf.readVarInt();
            int size = bounded(buf.readVarInt(), UltimateTerminalSnapshot.MAX_CELLS);
            List<CellDelta> changes = new ArrayList<>(size);
            for (int i = 0; i < size; i++) changes.add(new CellDelta(buf.readVarInt(), status(buf.readUnsignedByte())));
            return new DeltaPacket(revision, List.copyOf(changes), readMaterials(buf), readMaterials(buf),
                    readCandidates(buf), readChannels(buf), readStructure(buf),
                    readModule(buf), buf.readBoolean(), buf.readBoolean(), buf.readUtf(256));
        }
        static void handle(DeltaPacket packet, Supplier<NetworkEvent.Context> supplier) {
            NetworkEvent.Context context = supplier.get();
            context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> UltimateTerminalClientState.apply(packet)));
            context.setPacketHandled(true);
        }
    }
}
