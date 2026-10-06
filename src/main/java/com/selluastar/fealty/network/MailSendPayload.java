package com.selluastar.fealty.network;

import com.selluastar.fealty.Fealty;
import com.selluastar.fealty.mail.MailWriteMenu;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client to server: send the letter being written ({@code kind} of recipient is {@code village} or {@code player}). */
public record MailSendPayload(String kind, String recipient, String subject, String body) implements CustomPacketPayload {
    public static final Type<MailSendPayload> TYPE = new Type<>(Fealty.id("mail_send"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MailSendPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(16), MailSendPayload::kind,
            ByteBufCodecs.stringUtf8(256), MailSendPayload::recipient,
            ByteBufCodecs.stringUtf8(64), MailSendPayload::subject,
            ByteBufCodecs.stringUtf8(1024), MailSendPayload::body,
            MailSendPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(MailSendPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player && player.containerMenu instanceof MailWriteMenu menu) {
            menu.send(player, payload.kind(), payload.recipient(), payload.subject(), payload.body());
        }
    }
}
