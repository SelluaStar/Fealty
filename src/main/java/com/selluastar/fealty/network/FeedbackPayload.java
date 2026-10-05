package com.selluastar.fealty.network;

import com.selluastar.fealty.Fealty;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;

/**
 * Server to client: something worth showing the player. Reputation changes go to the rep feed, big moments to a
 * banner across the screen, and smaller news to a toast.
 *
 * @param icon a GUI icon name ({@code fealty:icon/<name>}), used when {@code item} is empty
 */
public record FeedbackPayload(Kind kind, Component title, Component detail, int amount, int color, String icon, ItemStack item)
        implements CustomPacketPayload {
    public static final Type<FeedbackPayload> TYPE = new Type<>(Fealty.id("feedback"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FeedbackPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeEnum(p.kind());
                ComponentSerialization.STREAM_CODEC.encode(buf, p.title());
                ComponentSerialization.STREAM_CODEC.encode(buf, p.detail());
                buf.writeVarInt(p.amount());
                buf.writeInt(p.color());
                ByteBufCodecs.STRING_UTF8.encode(buf, p.icon());
                ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, p.item());
            },
            buf -> new FeedbackPayload(buf.readEnum(Kind.class), ComponentSerialization.STREAM_CODEC.decode(buf),
                    ComponentSerialization.STREAM_CODEC.decode(buf), buf.readVarInt(), buf.readInt(),
                    ByteBufCodecs.STRING_UTF8.decode(buf), ItemStack.OPTIONAL_STREAM_CODEC.decode(buf)));

    public enum Kind {
        /** A line in the reputation feed: {@code title} is the faction, {@code amount} the change. */
        REP,
        /** A banner across the screen: {@code title} with {@code detail} beneath. */
        BANNER,
        /** A toast in the corner. */
        TOAST
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
