package com.selluastar.fealty.network;

import com.selluastar.fealty.Fealty;
import com.selluastar.fealty.quest.QuestGivers;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client to server: a button on a quest giver's screen was pressed. */
public record QuestActionPayload(int entityId, String action, String argument) implements CustomPacketPayload {
    public static final Type<QuestActionPayload> TYPE = new Type<>(Fealty.id("quest_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, QuestActionPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, QuestActionPayload::entityId,
            ByteBufCodecs.stringUtf8(64), QuestActionPayload::action,
            ByteBufCodecs.stringUtf8(256), QuestActionPayload::argument,
            QuestActionPayload::new);

    public static final String ACCEPT = "accept";
    public static final String TURN_IN = "turn_in";
    public static final String ABANDON = "abandon";

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(QuestActionPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        Entity entity = player.level().getEntity(payload.entityId());
        if (entity == null || !entity.isAlive() || entity.distanceToSqr(player) > 8 * 8) {
            return;
        }
        QuestGivers.forEntity(entity).ifPresent(giver -> {
            giver.handleAction(player, entity, payload.action(), payload.argument());
            if (entity.isAlive()) {
                QuestGivers.open(player, entity);
            }
        });
    }
}
