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

/** Network validation and display sync for startup difficulty. */
public final class GSEDifficultyMessages {
    private static final String PROTOCOL_VERSION = "2";
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

    public static void sendDifficultySync(ServerPlayer player, Difficulty difficulty) {
        CHANNEL.sendTo(new SyncDifficultyPacket(difficulty), player.connection.connection,
                NetworkDirection.PLAY_TO_CLIENT);
    }

    /** Client side: declares the tier captured when this process started. */
    public static void sendDeclaration() {
        CHANNEL.sendToServer(new DeclareDifficultyPacket(GSEDifficultyConfig.capturedDifficulty()));
    }

    public record DeclareDifficultyPacket(@Nullable Difficulty difficulty) {
        public static void encode(DeclareDifficultyPacket packet, FriendlyByteBuf buf) {
            buf.writeUtf(packet.difficulty != null ? packet.difficulty.getSerializedName() : "", 16);
        }

        public static DeclareDifficultyPacket decode(FriendlyByteBuf buf) {
            return new DeclareDifficultyPacket(Difficulty.byName(buf.readUtf(16)));
        }

        public static void handle(DeclareDifficultyPacket packet,
                                  Supplier<NetworkEvent.Context> context) {
            NetworkEvent.Context ctx = context.get();
            ServerPlayer sender = ctx.getSender();
            if (sender != null) {
                ctx.enqueueWork(() -> GSEDifficultyEvents.onDeclared(sender, packet.difficulty));
            }
            ctx.setPacketHandled(true);
        }
    }

    public record SyncDifficultyPacket(Difficulty difficulty) {
        public static void encode(SyncDifficultyPacket packet, FriendlyByteBuf buf) {
            buf.writeUtf(packet.difficulty.getSerializedName(), 16);
        }

        public static SyncDifficultyPacket decode(FriendlyByteBuf buf) {
            Difficulty difficulty = Difficulty.byName(buf.readUtf(16));
            return new SyncDifficultyPacket(difficulty != null ? difficulty : Difficulty.NORMAL);
        }

        public static void handle(SyncDifficultyPacket packet,
                                  Supplier<NetworkEvent.Context> context) {
            NetworkEvent.Context ctx = context.get();
            ctx.enqueueWork(() -> GSEDifficultyState.setClientDifficulty(packet.difficulty));
            ctx.setPacketHandled(true);
        }
    }
}
