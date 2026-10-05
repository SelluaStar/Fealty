package com.selluastar.fealty.network;

import com.selluastar.fealty.Fealty;
import com.selluastar.fealty.dialogue.DialogueService;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client to server: the player picked a reply in the dialogue box. */
public record DialogueChoicePayload(int entityId, String option) implements CustomPacketPayload {
    public static final Type<DialogueChoicePayload> TYPE = new Type<>(Fealty.id("dialogue_choice"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DialogueChoicePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, DialogueChoicePayload::entityId,
            ByteBufCodecs.stringUtf8(64), DialogueChoicePayload::option,
            DialogueChoicePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(DialogueChoicePayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            DialogueService.choose(player, payload.entityId(), payload.option());
        }
    }
}
