package com.selluastar.fealty.network;

import java.util.List;

import com.selluastar.fealty.Fealty;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server to client: the player's accepted quests, replacing the client's copy. */
public record SyncQuestsPayload(List<QuestView> quests) implements CustomPacketPayload {
    public static final Type<SyncQuestsPayload> TYPE = new Type<>(Fealty.id("sync_quests"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SyncQuestsPayload> STREAM_CODEC = StreamCodec.composite(
            QuestView.STREAM_CODEC.apply(ByteBufCodecs.list()), SyncQuestsPayload::quests,
            SyncQuestsPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
