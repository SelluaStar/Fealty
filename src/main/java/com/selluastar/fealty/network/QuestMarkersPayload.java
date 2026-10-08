package com.selluastar.fealty.network;

import java.util.List;

import com.selluastar.fealty.Fealty;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server to client: which NPCs near the player have work for them (shown as ! and ? above their heads). */
public record QuestMarkersPayload(List<Marker> markers) implements CustomPacketPayload {
    public static final Type<QuestMarkersPayload> TYPE = new Type<>(Fealty.id("quest_markers"));
    public static final StreamCodec<RegistryFriendlyByteBuf, QuestMarkersPayload> STREAM_CODEC = StreamCodec.composite(
            Marker.STREAM_CODEC.apply(ByteBufCodecs.list()), QuestMarkersPayload::markers,
            QuestMarkersPayload::new);

    public enum Kind {
        /** New main work (gold !). */
        OFFER,
        /** A small favor (white !). */
        FAVOR,
        /** A quest is in progress with this giver (grey ?). */
        ACTIVE,
        /** A quest is ready to hand in here (gold ?). */
        READY,
        /** The player carries a letter for this person. */
        LETTER,
        /** Someone a quest asks the player to find or speak to. */
        TARGET
    }

    public record Marker(int entityId, Kind kind) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Marker> STREAM_CODEC = StreamCodec.of(
                (buf, m) -> {
                    buf.writeVarInt(m.entityId());
                    buf.writeEnum(m.kind());
                },
                buf -> new Marker(buf.readVarInt(), buf.readEnum(Kind.class)));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
