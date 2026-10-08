package com.selluastar.fealty.network;

import java.util.Optional;
import java.util.UUID;

import com.selluastar.fealty.Fealty;
import com.selluastar.fealty.mail.MailService;

import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client to server: read, empty or throw away a letter, start writing one, or post a courier letter. */
public record MailActionPayload(BlockPos pos, String action, Optional<UUID> letter) implements CustomPacketPayload {
    public static final Type<MailActionPayload> TYPE = new Type<>(Fealty.id("mail_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MailActionPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, MailActionPayload::pos,
            ByteBufCodecs.stringUtf8(16), MailActionPayload::action,
            ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC), MailActionPayload::letter,
            MailActionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(MailActionPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            MailService.action(player, payload.pos(), payload.action(), payload.letter().orElse(null));
        }
    }
}
