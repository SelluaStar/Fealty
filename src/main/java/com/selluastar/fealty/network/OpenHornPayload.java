package com.selluastar.fealty.network;

import java.util.List;

import com.selluastar.fealty.Fealty;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to client: open the Lord's Horn screen with the villages the player rules, the horn's settings, and how many
 * guards a call brings.
 */
public record OpenHornPayload(List<Village> villages, String selected, int order, int summon) implements CustomPacketPayload {
    public static final Type<OpenHornPayload> TYPE = new Type<>(Fealty.id("open_horn"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenHornPayload> STREAM_CODEC = StreamCodec.composite(
            Village.STREAM_CODEC.apply(ByteBufCodecs.list(64)), OpenHornPayload::villages,
            ByteBufCodecs.STRING_UTF8, OpenHornPayload::selected,
            ByteBufCodecs.VAR_INT, OpenHornPayload::order,
            ByteBufCodecs.VAR_INT, OpenHornPayload::summon,
            OpenHornPayload::new);

    /** A village the player rules: its guards standing (of the full watch), those following the player, and the distance. */
    public record Village(String id, String name, int color, int alive, int total, int following, int distance) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Village> STREAM_CODEC = StreamCodec.of(
                (buf, v) -> {
                    ByteBufCodecs.STRING_UTF8.encode(buf, v.id());
                    ByteBufCodecs.STRING_UTF8.encode(buf, v.name());
                    buf.writeInt(v.color());
                    buf.writeVarInt(v.alive());
                    buf.writeVarInt(v.total());
                    buf.writeVarInt(v.following());
                    buf.writeVarInt(v.distance());
                },
                buf -> new Village(ByteBufCodecs.STRING_UTF8.decode(buf), ByteBufCodecs.STRING_UTF8.decode(buf), buf.readInt(),
                        buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
