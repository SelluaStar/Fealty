package com.selluastar.fealty.network;

import com.selluastar.fealty.Fealty;
import com.selluastar.fealty.crime.Locks;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client to server: try to turn the lock with the pick at {@code angle} degrees (0 to 180). */
public record LockpickAttemptPayload(BlockPos pos, float angle) implements CustomPacketPayload {
    public static final Type<LockpickAttemptPayload> TYPE = new Type<>(Fealty.id("lockpick_attempt"));
    public static final StreamCodec<RegistryFriendlyByteBuf, LockpickAttemptPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, LockpickAttemptPayload::pos,
            ByteBufCodecs.FLOAT, LockpickAttemptPayload::angle,
            LockpickAttemptPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(LockpickAttemptPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            Locks.attempt(player, payload.pos(), payload.angle());
        }
    }
}
