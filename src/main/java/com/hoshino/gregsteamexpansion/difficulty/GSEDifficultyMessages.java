package com.hoshino.gregsteamexpansion.difficulty;

import com.hoshino.gregsteamexpansion.GregSteamExpansion;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Difficulty network traffic (difficulty.md 客户端进入校验). The client
 * declares its config tier as the first play-phase packet right after
 * joining; the server gates the connection the moment the declaration
 * arrives and, on a pass, syncs the save's authoritative tier back for
 * client-side machine construction. (Login-phase payloads were tried here
 * first, but they require the indexed wrapper-reply protocol — a handler
 * that never replies stalls the entire login handshake.)
 */
public final class GSEDifficultyMessages {
    private static final String PROTOCOL_VERSION = "1";
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
        CHANNEL.registerMessage(id++, SyncDifficultyPacket.class,
                SyncDifficultyPacket::encode, SyncDifficultyPacket::decode,
                SyncDifficultyPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, OpenChoicePacket.class,
                OpenChoicePacket::encode, OpenChoicePacket::decode,
                OpenChoicePacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, ChooseDifficultyPacket.class,
                ChooseDifficultyPacket::encode, ChooseDifficultyPacket::decode,
                ChooseDifficultyPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
    }

    public static void sendDifficultySync(ServerPlayer player, Difficulty difficulty) {
        CHANNEL.sendTo(new SyncDifficultyPacket(difficulty), player.connection.connection,
                NetworkDirection.PLAY_TO_CLIENT);
    }

    public static void sendOpenChoice(ServerPlayer player) {
        CHANNEL.sendTo(new OpenChoicePacket(), player.connection.connection,
                NetworkDirection.PLAY_TO_CLIENT);
    }

    /** Client side: submits the tier picked on the first-entry choice screen. */
    public static void sendChooseDifficulty(Difficulty difficulty) {
        CHANNEL.sendToServer(new ChooseDifficultyPacket(difficulty));
    }

    /**
     * Client side: declares the config tier captured at startup; null when
     * the config says ASK. Always sent, so an ASK client on an initialized
     * save is refused instead of silently playing unsynced.
     */
    public static void sendDeclaration() {
        CHANNEL.sendToServer(new DeclareDifficultyPacket(GSEDifficultyConfig.clientDeclarationTier()));
    }

    /**
     * Client to server right after joining (first play-phase packet). An
     * invalid or mismatching tier is refused with the mismatch message.
     */
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

    /** Server to client after a passed check: the save's authoritative tier. */
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

    /**
     * Server to client when the save carries no difficulty yet: the client
     * shows the first-entry choice screen (difficulty.md 服务端与存档权威性).
     */
    public record OpenChoicePacket() {
        public static void encode(OpenChoicePacket packet, FriendlyByteBuf buf) {}

        public static OpenChoicePacket decode(FriendlyByteBuf buf) {
            return new OpenChoicePacket();
        }

        public static void handle(OpenChoicePacket packet,
                                  Supplier<NetworkEvent.Context> context) {
            NetworkEvent.Context ctx = context.get();
            ctx.enqueueWork(GSEDifficultyState::requestClientChoiceScreen);
            ctx.setPacketHandled(true);
        }
    }

    /**
     * Client to server in the play phase: the tier picked on the first-entry
     * choice screen. An invalid tier is treated as a mismatch and refused.
     */
    public record ChooseDifficultyPacket(@Nullable Difficulty difficulty) {
        public static void encode(ChooseDifficultyPacket packet, FriendlyByteBuf buf) {
            buf.writeUtf(packet.difficulty != null ? packet.difficulty.getSerializedName() : "", 16);
        }

        public static ChooseDifficultyPacket decode(FriendlyByteBuf buf) {
            return new ChooseDifficultyPacket(Difficulty.byName(buf.readUtf(16)));
        }

        public static void handle(ChooseDifficultyPacket packet,
                                  Supplier<NetworkEvent.Context> context) {
            NetworkEvent.Context ctx = context.get();
            ctx.enqueueWork(() -> {
                ServerPlayer sender = ctx.getSender();
                if (sender == null) {
                    return;
                }
                if (packet.difficulty == null) {
                    sender.connection.disconnect(Component.translatable(
                            "config.gregsteamexpansion.difficulty.mismatch",
                            Component.translatable("config.gregsteamexpansion.difficulty.invalid"),
                            Component.translatable(GSEDifficultyState.resolved().getDisplayNameKey())));
                    return;
                }
                GSEDifficultyEvents.onDifficultyChosen(sender, packet.difficulty);
            });
            ctx.setPacketHandled(true);
        }
    }
}
