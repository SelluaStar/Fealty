package com.selluastar.fealty.network;

import com.selluastar.fealty.Fealty;
import com.selluastar.fealty.dialogue.DialogueNode;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server to client: open (or update) the dialogue box with an NPC. */
public record OpenDialoguePayload(int entityId, DialogueNode node) implements CustomPacketPayload {
    public static final Type<OpenDialoguePayload> TYPE = new Type<>(Fealty.id("open_dialogue"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenDialoguePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, OpenDialoguePayload::entityId,
            DialogueNode.STREAM_CODEC, OpenDialoguePayload::node,
            OpenDialoguePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
