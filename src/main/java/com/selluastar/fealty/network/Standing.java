package com.selluastar.fealty.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

/**
 * A player's standing with one faction, as the client sees it.
 *
 * @param lord whether this player is the faction's sworn lord
 */
public record Standing(ResourceLocation faction, Component name, int rep, boolean village, boolean lord) {
    public static final StreamCodec<RegistryFriendlyByteBuf, Standing> STREAM_CODEC = StreamCodec.composite(
            ResourceLocation.STREAM_CODEC, Standing::faction,
            ComponentSerialization.STREAM_CODEC, Standing::name,
            ByteBufCodecs.VAR_INT, Standing::rep,
            ByteBufCodecs.BOOL, Standing::village,
            ByteBufCodecs.BOOL, Standing::lord,
            Standing::new);
}
