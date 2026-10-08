package com.selluastar.fealty.network;

import java.util.UUID;

import com.selluastar.fealty.Fealty;
import com.selluastar.fealty.quest.QuestSync;
import com.selluastar.fealty.rep.RepManager;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client to server: show or hide an accepted quest in the HUD tracker. */
public record TrackQuestPayload(UUID instance, boolean tracked) implements CustomPacketPayload {
    public static final Type<TrackQuestPayload> TYPE = new Type<>(Fealty.id("track_quest"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TrackQuestPayload> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, TrackQuestPayload::instance,
            ByteBufCodecs.BOOL, TrackQuestPayload::tracked,
            TrackQuestPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(TrackQuestPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            RepManager.data(player).setTracked(payload.instance(), payload.tracked());
            QuestSync.sync(player);
        }
    }
}
