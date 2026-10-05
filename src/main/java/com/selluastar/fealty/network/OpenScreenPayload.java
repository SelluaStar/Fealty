package com.selluastar.fealty.network;

import com.selluastar.fealty.Fealty;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server to client: open one of Fealty's client-only screens that need no extra data. */
public record OpenScreenPayload(String screen, String argument) implements CustomPacketPayload {
    public static final String JOURNAL = "journal";
    /** Close the dialogue box (or any Fealty screen). */
    public static final String CLOSE = "close";

    public static final Type<OpenScreenPayload> TYPE = new Type<>(Fealty.id("open_screen"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenScreenPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, OpenScreenPayload::screen,
            ByteBufCodecs.STRING_UTF8, OpenScreenPayload::argument,
            OpenScreenPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
