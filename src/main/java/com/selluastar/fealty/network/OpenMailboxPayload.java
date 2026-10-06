package com.selluastar.fealty.network;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.selluastar.fealty.Fealty;

import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;

/**
 * Server to client: open (or refresh) a mailbox: the player's letters, and the sender of a courier letter they could
 * post here (empty if none).
 */
public record OpenMailboxPayload(BlockPos pos, Component title, List<LetterView> letters, Component postable) implements CustomPacketPayload {
    public static final Type<OpenMailboxPayload> TYPE = new Type<>(Fealty.id("open_mailbox"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenMailboxPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, OpenMailboxPayload::pos,
            ComponentSerialization.STREAM_CODEC, OpenMailboxPayload::title,
            LetterView.STREAM_CODEC.apply(ByteBufCodecs.list(64)), OpenMailboxPayload::letters,
            ComponentSerialization.STREAM_CODEC, OpenMailboxPayload::postable,
            OpenMailboxPayload::new);

    /** A letter as the mailbox shows it. {@code kind} is the ordinal of {@code Letter.Kind}. */
    public record LetterView(UUID id, int kind, Component from, Component subject, Component body, List<ItemStack> items, boolean read,
                             int minutesAgo) {
        public static final StreamCodec<RegistryFriendlyByteBuf, LetterView> STREAM_CODEC = StreamCodec.of(
                (buf, v) -> {
                    UUIDUtil.STREAM_CODEC.encode(buf, v.id());
                    buf.writeVarInt(v.kind());
                    ComponentSerialization.STREAM_CODEC.encode(buf, v.from());
                    ComponentSerialization.STREAM_CODEC.encode(buf, v.subject());
                    ComponentSerialization.STREAM_CODEC.encode(buf, v.body());
                    buf.writeVarInt(v.items().size());
                    for (ItemStack stack : v.items()) {
                        ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, stack);
                    }
                    buf.writeBoolean(v.read());
                    buf.writeVarInt(v.minutesAgo());
                },
                buf -> {
                    UUID id = UUIDUtil.STREAM_CODEC.decode(buf);
                    int kind = buf.readVarInt();
                    Component from = ComponentSerialization.STREAM_CODEC.decode(buf);
                    Component subject = ComponentSerialization.STREAM_CODEC.decode(buf);
                    Component body = ComponentSerialization.STREAM_CODEC.decode(buf);
                    int count = Math.min(buf.readVarInt(), 9);
                    List<ItemStack> items = new ArrayList<>();
                    for (int i = 0; i < count; i++) {
                        items.add(ItemStack.OPTIONAL_STREAM_CODEC.decode(buf));
                    }
                    return new LetterView(id, kind, from, subject, body, items, buf.readBoolean(), buf.readVarInt());
                });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
