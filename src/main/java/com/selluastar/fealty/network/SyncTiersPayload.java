package com.selluastar.fealty.network;

import java.util.List;

import com.selluastar.fealty.Fealty;
import com.selluastar.fealty.api.RepTier;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server to client: the tier table from data packs. */
public record SyncTiersPayload(List<RepTier> tiers) implements CustomPacketPayload {
    public static final Type<SyncTiersPayload> TYPE = new Type<>(Fealty.id("sync_tiers"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SyncTiersPayload> STREAM_CODEC = StreamCodec.composite(
            FealtyCodecs.TIER.apply(ByteBufCodecs.list()), SyncTiersPayload::tiers,
            SyncTiersPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
