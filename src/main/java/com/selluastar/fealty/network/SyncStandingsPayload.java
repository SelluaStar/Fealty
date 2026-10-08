package com.selluastar.fealty.network;

import java.util.List;

import com.selluastar.fealty.Fealty;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to client: the player's standings. {@code replace} means this is the full list; otherwise the
 * entries update the client's copy. Renown is always included.
 */
public record SyncStandingsPayload(List<Standing> standings, int renown, boolean replace) implements CustomPacketPayload {
    public static final Type<SyncStandingsPayload> TYPE = new Type<>(Fealty.id("sync_standings"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SyncStandingsPayload> STREAM_CODEC = StreamCodec.composite(
            Standing.STREAM_CODEC.apply(ByteBufCodecs.list()), SyncStandingsPayload::standings,
            ByteBufCodecs.VAR_INT, SyncStandingsPayload::renown,
            ByteBufCodecs.BOOL, SyncStandingsPayload::replace,
            SyncStandingsPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
