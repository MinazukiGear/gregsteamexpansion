package com.hoshino.gregsteamexpansion.gametest;

import com.hoshino.gregsteamexpansion.GregSteamExpansion;
import com.hoshino.gregsteamexpansion.registry.GSEBlocks;
import com.hoshino.gregsteamexpansion.terminal.UltimateTerminalMessages;
import com.hoshino.gregsteamexpansion.terminal.UltimateTerminalSnapshot;
import com.hoshino.gregsteamexpansion.terminal.UltimateTerminalWorldData;

import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.FakePlayerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Socket-free recipients for the real refresh loop and encoded terminal packets. */
public final class GSETerminalRefreshTestSupport implements AutoCloseable {
    private final GameTestHelper helper;
    private final Map<UUID, ServerPlayer> online;
    private final Map<UUID, Object> sessions;
    private final Map<UUID, Object> projects;
    private final List<Owner> owners = new ArrayList<>();

    @SuppressWarnings("unchecked")
    public GSETerminalRefreshTestSupport(GameTestHelper helper) {
        this.helper = helper;
        try {
            online = (Map<UUID, ServerPlayer>) field(PlayerList.class, "playersByUUID")
                    .get(helper.getLevel().getServer().getPlayerList());
            sessions = (Map<UUID, Object>) field(UltimateTerminalMessages.class, "SESSIONS").get(null);
            projects = (Map<UUID, Object>) field(UltimateTerminalWorldData.class, "projects")
                    .get(data());
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Cannot expose isolated terminal recipients", exception);
        }
    }

    public UltimateTerminalWorldData data() {
        return UltimateTerminalWorldData.get(helper.getLevel().getServer());
    }

    public Owner add(UUID id, List<BlockPos> targets, int selected, int inventory) {
        var player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(id, "[GSE refresh]"));
        var owner = new Owner(player, targets, online.get(id), sessions.get(id), projects.get(id));
        owners.add(owner);
        sessions.remove(id);
        projects.remove(id);
        player.getInventory().clearContent();
        player.getAbilities().instabuild = false;
        player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(GSEBlocks.ULTIMATE_TERMINAL.get()));
        for (BlockPos target : targets) {
            helper.assertTrue(data().addTarget(player, helper.getLevel().dimension(), target),
                    "Refresh target rejected");
        }
        select(owner, selected);
        data().toggleAE(id);
        var preview = data().preview(player);
        helper.assertTrue(preview.plan().valid() && !preview.selectedMaterials().isEmpty(),
                "Refresh fixture needs a valid unbuilt blueprint");
        owner.probe = preview.selectedMaterials().get(0).stack().copyWithCount(1);
        owner.setInventory(inventory);
        owner.connection = new Recipient(owner);
        new ServerGamePacketListenerImpl(player.server, owner.connection, player);
        online.put(id, player);
        UltimateTerminalMessages.forceFull(player);
        owner.flush();
        return owner;
    }

    public void select(Owner owner, int target) {
        data().selectTarget(owner.player, target);
        owner.controller = owner.targets.get(target);
    }

    public void online(Owner owner, boolean value) {
        if (value) online.put(owner.player.getUUID(), owner.player);
        else online.remove(owner.player.getUUID());
    }

    public boolean hasSession(Owner owner) { return sessions.containsKey(owner.player.getUUID()); }

    @Override
    public void close() {
        for (Owner owner : owners) {
            UUID id = owner.player.getUUID();
            restore(online, id, owner.previousPlayer);
            restore(sessions, id, owner.previousSession);
            restore(projects, id, owner.previousProject);
            owner.player.connection = owner.previousConnection;
            owner.player.getInventory().clearContent();
            owner.player.getAbilities().instabuild = owner.previousCreative;
        }
        owners.clear();
    }

    private static <T> void restore(Map<UUID, T> values, UUID id, T previous) {
        if (previous == null) values.remove(id);
        else values.put(id, previous);
    }

    private static Field field(Class<?> type, String name) throws ReflectiveOperationException {
        Field result = type.getDeclaredField(name);
        result.setAccessible(true);
        return result;
    }

    public final class Owner {
        public final ServerPlayer player;
        public final List<BlockPos> targets;
        public BlockPos controller;
        public ItemStack probe;
        public Recipient connection;
        private final ServerPlayer previousPlayer;
        private final Object previousSession;
        private final Object previousProject;
        private final ServerGamePacketListenerImpl previousConnection;
        private final boolean previousCreative;
        public UltimateTerminalSnapshot lastFull;
        public List<UltimateTerminalSnapshot.MaterialInfo> selectedMaterials;
        public List<UltimateTerminalSnapshot.MaterialInfo> batchMaterials;

        private Owner(ServerPlayer player, List<BlockPos> targets, ServerPlayer previousPlayer,
                      Object previousSession, Object previousProject) {
            this.player = player;
            this.targets = List.copyOf(targets);
            this.previousPlayer = previousPlayer;
            this.previousSession = previousSession;
            this.previousProject = previousProject;
            previousConnection = player.connection;
            previousCreative = player.getAbilities().instabuild;
        }

        public void setInventory(int count) {
            player.getInventory().setItem(1, probe.copyWithCount(count));
        }

        public long available() {
            return player.getInventory().items.stream().filter(stack ->
                    ItemStack.isSameItemSameTags(stack, probe)).mapToLong(ItemStack::getCount).sum();
        }

        /** Decode outside measured END events, while the previous tick's inventory is still current. */
        public void flush() {
            for (byte[] bytes : connection.pending) {
                FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.wrappedBuffer(bytes));
                try {
                    int discriminator = buffer.readVarInt();
                    if (discriminator == 4) {
                        var packet = (UltimateTerminalMessages.FullPacket) decode(
                                UltimateTerminalMessages.FullPacket.class, buffer);
                        lastFull = packet.snapshot();
                        helper.assertTrue(lastFull.controller().equals(controller)
                                        && lastFull.targets().stream().map(UltimateTerminalSnapshot.TargetInfo::pos)
                                        .toList().equals(targets),
                                "Full preview crossed recipient/target boundaries");
                        selectedMaterials = lastFull.selectedMaterials();
                        batchMaterials = lastFull.batchMaterials();
                    } else if (discriminator == 5) {
                        var packet = (UltimateTerminalMessages.DeltaPacket) decode(
                                UltimateTerminalMessages.DeltaPacket.class, buffer);
                        helper.assertTrue(lastFull != null && packet.revision() == lastFull.revision(),
                                "Delta revision does not belong to this recipient");
                        selectedMaterials = packet.selectedMaterials();
                        batchMaterials = packet.batchMaterials();
                    } else {
                        throw new IllegalStateException("Unexpected terminal response " + discriminator);
                    }
                    for (var materials : List.of(selectedMaterials, batchMaterials)) {
                        var material = materials.stream().filter(value ->
                                ItemStack.isSameItemSameTags(value.stack(), probe)).findFirst().orElseThrow();
                        helper.assertTrue(material.inventory() == available() && material.network() == 0,
                                "Encoded material availability leaked another owner's inventory or AE state");
                    }
                    helper.assertTrue(buffer.readableBytes() == 0, "Terminal packet has trailing data");
                } finally {
                    buffer.release();
                }
            }
            connection.pending.clear();
        }
    }

    public static final class Recipient extends Connection {
        private final Owner owner;
        private final List<byte[]> pending = new ArrayList<>();
        public long packets;
        public long payloadBytes;
        public final List<Integer> deltaTicks = new ArrayList<>();
        private static final Field DATA;
        static {
            try { DATA = field(ClientboundCustomPayloadPacket.class, "data"); }
            catch (ReflectiveOperationException exception) { throw new ExceptionInInitializerError(exception); }
        }

        private Recipient(Owner owner) {
            super(PacketFlow.SERVERBOUND);
            this.owner = owner;
        }

        @Override
        public void send(Packet<?> packet, PacketSendListener listener) {
            if (!(packet instanceof ClientboundCustomPayloadPacket payload)
                    || !payload.getIdentifier().equals(GregSteamExpansion.id("ultimate_terminal"))) return;
            try {
                FriendlyByteBuf data = (FriendlyByteBuf) DATA.get(payload);
                try {
                    byte[] bytes = new byte[data.readableBytes()];
                    data.getBytes(data.readerIndex(), bytes);
                    pending.add(bytes);
                    packets++;
                    payloadBytes += bytes.length;
                    if (bytes.length > 0 && bytes[0] == 5) deltaTicks.add(owner.player.server.getTickCount());
                } finally {
                    // The fake recipient consumes the packet instead of a socket's packet encoder.
                    data.release();
                }
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Cannot capture terminal payload", exception);
            }
        }
    }

    private static Object decode(Class<?> type, FriendlyByteBuf buffer) {
        try {
            Method method = type.getDeclaredMethod("decode", FriendlyByteBuf.class);
            method.setAccessible(true);
            return method.invoke(null, buffer);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Cannot decode terminal response", exception);
        }
    }
}
