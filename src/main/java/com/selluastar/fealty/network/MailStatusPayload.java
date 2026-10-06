package com.selluastar.fealty.network;

import com.selluastar.fealty.Fealty;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server to client: how many letters the player has not read yet (for the envelope on the HUD and mailbox flags). */
public record MailStatusPayload(int unread) implements CustomPacketPayload {
    public static final Type<MailStatusPayload> TYPE = new Type<>(Fealty.id("mail_status"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MailStatusPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, MailStatusPayload::unread,
            MailStatusPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
