package com.selluastar.fealty.network;

import java.util.UUID;

import com.selluastar.fealty.Fealty;
import com.selluastar.fealty.quest.QuestManager;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client to server: abandon an accepted quest from the journal. */
public record AbandonQuestPayload(UUID instance) implements CustomPacketPayload {
    public static final Type<AbandonQuestPayload> TYPE = new Type<>(Fealty.id("abandon_quest"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AbandonQuestPayload> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, AbandonQuestPayload::instance,
            AbandonQuestPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(AbandonQuestPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            QuestManager.abandonInstance(player, payload.instance());
        }
    }
}
