package com.selluastar.fealty.network;

import java.util.List;

import com.selluastar.fealty.Fealty;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server to client: the guards following the player (or on their way), for the retinue bar. Empty clears it. */
public record RetinuePayload(List<Member> members) implements CustomPacketPayload {
    public static final Type<RetinuePayload> TYPE = new Type<>(Fealty.id("retinue"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RetinuePayload> STREAM_CODEC = StreamCodec.composite(
            Member.STREAM_CODEC.apply(ByteBufCodecs.list(16)), RetinuePayload::members,
            RetinuePayload::new);

    /**
     * One guard: their name, health, current order ({@code follow}, {@code hold}, {@code guard}, {@code escort},
     * {@code return} or {@code arriving}), ticks until they arrive, rank ({@code swordsman}, {@code archer},
     * {@code sergeant}, or {@code other} for golems and guards from other mods) and colours.
     */
    public record Member(Component name, float health, float maxHealth, String order, int eta, String kind, int color) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Member> STREAM_CODEC = StreamCodec.of(
                (buf, m) -> {
                    ComponentSerialization.STREAM_CODEC.encode(buf, m.name());
                    buf.writeFloat(m.health());
                    buf.writeFloat(m.maxHealth());
                    ByteBufCodecs.stringUtf8(16).encode(buf, m.order());
                    buf.writeVarInt(m.eta());
                    ByteBufCodecs.stringUtf8(16).encode(buf, m.kind());
                    buf.writeInt(m.color());
                },
                buf -> new Member(ComponentSerialization.STREAM_CODEC.decode(buf), buf.readFloat(), buf.readFloat(),
                        ByteBufCodecs.stringUtf8(16).decode(buf), buf.readVarInt(), ByteBufCodecs.stringUtf8(16).decode(buf), buf.readInt()));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
