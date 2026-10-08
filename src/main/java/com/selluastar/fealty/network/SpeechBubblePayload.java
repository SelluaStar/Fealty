package com.selluastar.fealty.network;

import com.selluastar.fealty.Fealty;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server to client: show a speech bubble above an entity for {@code ticks} ticks. */
public record SpeechBubblePayload(int entityId, Component text, int ticks) implements CustomPacketPayload {
    public static final Type<SpeechBubblePayload> TYPE = new Type<>(Fealty.id("speech_bubble"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SpeechBubblePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SpeechBubblePayload::entityId,
            ComponentSerialization.STREAM_CODEC, SpeechBubblePayload::text,
            ByteBufCodecs.VAR_INT, SpeechBubblePayload::ticks,
            SpeechBubblePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
