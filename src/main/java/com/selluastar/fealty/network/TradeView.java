package com.selluastar.fealty.network;

import java.util.List;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** A village-exclusive trade as recipe viewers show it. */
public record TradeView(String key, List<ResourceLocation> professions, Component tier, int tierColor, int minLevel,
                        ItemStack costA, ItemStack costB, ItemStack result) {
    public static final StreamCodec<RegistryFriendlyByteBuf, TradeView> STREAM_CODEC = StreamCodec.of(
            (buf, v) -> {
                buf.writeUtf(v.key());
                ResourceLocation.STREAM_CODEC.apply(ByteBufCodecs.list()).encode(buf, v.professions());
                ComponentSerialization.STREAM_CODEC.encode(buf, v.tier());
                buf.writeInt(v.tierColor());
                buf.writeVarInt(v.minLevel());
                ItemStack.STREAM_CODEC.encode(buf, v.costA());
                ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, v.costB());
                ItemStack.STREAM_CODEC.encode(buf, v.result());
            },
            buf -> new TradeView(buf.readUtf(), ResourceLocation.STREAM_CODEC.apply(ByteBufCodecs.list()).decode(buf),
                    ComponentSerialization.STREAM_CODEC.decode(buf), buf.readInt(), buf.readVarInt(),
                    ItemStack.STREAM_CODEC.decode(buf), ItemStack.OPTIONAL_STREAM_CODEC.decode(buf), ItemStack.STREAM_CODEC.decode(buf)));
}
