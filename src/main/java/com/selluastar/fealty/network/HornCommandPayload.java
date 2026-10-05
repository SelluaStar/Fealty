package com.selluastar.fealty.network;

import com.selluastar.fealty.Fealty;
import com.selluastar.fealty.guard.GarrisonManager;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client to server: the lord picked a village and an order on the horn screen, and whether to sound it now. */
public record HornCommandPayload(String village, String order, boolean blow) implements CustomPacketPayload {
    public static final Type<HornCommandPayload> TYPE = new Type<>(Fealty.id("horn_command"));
    public static final StreamCodec<RegistryFriendlyByteBuf, HornCommandPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(256), HornCommandPayload::village,
            ByteBufCodecs.stringUtf8(16), HornCommandPayload::order,
            ByteBufCodecs.BOOL, HornCommandPayload::blow,
            HornCommandPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(HornCommandPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            GarrisonManager.hornCommand(player, payload.village(), payload.order(), payload.blow());
        }
    }
}
