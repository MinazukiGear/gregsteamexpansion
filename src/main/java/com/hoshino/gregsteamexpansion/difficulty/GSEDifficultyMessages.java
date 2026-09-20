package com.hoshino.gregsteamexpansion.difficulty;

import com.hoshino.gregsteamexpansion.GregSteamExpansion;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.function.Supplier;

/** Network validation and display sync for the startup difficulty switch and tier. */
public final class GSEDifficultyMessages {
    private static final String PROTOCOL_VERSION = "4";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            GregSteamExpansion.id("difficulty"),
            () -> PROTOCOL_VERSION, PROTOCOL_VERSION::equals, PROTOCOL_VERSION::equals);

    private GSEDifficultyMessages() {}

    public static void register() {
        int id = 0;
        CHANNEL.registerMessage(id++, DeclareDifficultyPacket.class,
                DeclareDifficultyPacket::encode, DeclareDifficultyPacket::decode,
                DeclareDifficultyPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id, SyncDifficultyPacket.class,
                SyncDifficultyPacket::encode, SyncDifficultyPacket::decode,
                SyncDifficultyPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    public static void sendDifficultySync(ServerPlayer player, boolean enabled, Difficulty difficulty) {
        CHANNEL.sendTo(new SyncDifficultyPacket(enabled, difficulty), player.connection.connection,
                NetworkDirection.PLAY_TO_CLIENT);
    }

    /** Client side: declares the tier captured when this process started. */
    public static void sendDeclaration() {
        CHANNEL.sendToServer(new DeclareDifficultyPacket(
                GSEDifficultyConfig.capturedDifficultyEnabled(), GSEDifficultyConfig.capturedDifficulty(),
                GSEDifficultyConfig.capturedProfile(GSEDifficultyConfig.capturedDifficulty()).fingerprint()));
    }

    public record DeclareDifficultyPacket(boolean enabled, @Nullable Difficulty difficulty, String profileFingerprint) {
        public static void encode(DeclareDifficultyPacket packet, FriendlyByteBuf buf) {
            buf.writeBoolean(packet.enabled);
            buf.writeUtf(packet.difficulty != null ? packet.difficulty.getSerializedName() : "", 16);
            buf.writeUtf(packet.profileFingerprint, 64);
        }

        public static DeclareDifficultyPacket decode(FriendlyByteBuf buf) {
            return new DeclareDifficultyPacket(
                    buf.readBoolean(), Difficulty.byName(buf.readUtf(16)), buf.readUtf(64));
        }

        public static void handle(DeclareDifficultyPacket packet,
                                  Supplier<NetworkEvent.Context> context) {
            NetworkEvent.Context ctx = context.get();
            ServerPlayer sender = ctx.getSender();
            if (sender != null) {
                ctx.enqueueWork(() -> GSEDifficultyEvents.onDeclared(
                        sender, packet.enabled, packet.difficulty, packet.profileFingerprint));
            }
            ctx.setPacketHandled(true);
        }
    }

    public record SyncDifficultyPacket(boolean enabled, Difficulty difficulty) {
        public static void encode(SyncDifficultyPacket packet, FriendlyByteBuf buf) {
            buf.writeBoolean(packet.enabled);
            buf.writeUtf(packet.difficulty.getSerializedName(), 16);
        }

        public static SyncDifficultyPacket decode(FriendlyByteBuf buf) {
            boolean enabled = buf.readBoolean();
            Difficulty difficulty = Difficulty.byName(buf.readUtf(16));
            return new SyncDifficultyPacket(enabled,
                    difficulty != null ? difficulty : Difficulty.NORMAL);
        }

        public static void handle(SyncDifficultyPacket packet,
                                  Supplier<NetworkEvent.Context> context) {
            NetworkEvent.Context ctx = context.get();
            ctx.enqueueWork(() -> GSEDifficultyState.setClientDifficulty(
                    packet.enabled, packet.difficulty));
            ctx.setPacketHandled(true);
        }
    }
}
