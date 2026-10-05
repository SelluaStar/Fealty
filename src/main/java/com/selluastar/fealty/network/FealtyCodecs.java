package com.selluastar.fealty.network;

import com.selluastar.fealty.api.GuardStance;
import com.selluastar.fealty.api.RepTier;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

/** Stream codecs shared by Fealty's payloads. */
public final class FealtyCodecs {
    public static final StreamCodec<RegistryFriendlyByteBuf, RepTier> TIER = StreamCodec.of(
            (buf, tier) -> {
                ResourceLocation.STREAM_CODEC.encode(buf, tier.id());
                ComponentSerialization.STREAM_CODEC.encode(buf, tier.displayName());
                buf.writeVarInt(tier.min());
                buf.writeVarInt(tier.max());
                buf.writeFloat(tier.priceMultiplier());
                buf.writeVarInt(tier.rank());
                buf.writeInt(tier.color());
                buf.writeEnum(tier.guardStance());
            },
            buf -> new RepTier(ResourceLocation.STREAM_CODEC.decode(buf), ComponentSerialization.STREAM_CODEC.decode(buf),
                    buf.readVarInt(), buf.readVarInt(), buf.readFloat(), buf.readVarInt(), buf.readInt(), buf.readEnum(GuardStance.class)));

    private FealtyCodecs() {
    }
}
