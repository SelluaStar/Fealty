package com.selluastar.fealty.network;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/**
 * An accepted quest as the client sees it: the journal and the tracker draw from these.
 *
 * @param expiresAt game time the quest runs out, or -1
 */
public record QuestView(UUID instance, ResourceLocation questId, Component title, Component description, Component giver,
                        Component place, List<Line> lines, OpenQuestScreenPayload.Status status, Optional<Waypoint> waypoint,
                        long expiresAt, boolean tracked, int repReward, int difficulty) {
    public static final StreamCodec<RegistryFriendlyByteBuf, QuestView> STREAM_CODEC = StreamCodec.of(
            (buf, q) -> {
                UUIDUtil.STREAM_CODEC.encode(buf, q.instance());
                ResourceLocation.STREAM_CODEC.encode(buf, q.questId());
                ComponentSerialization.STREAM_CODEC.encode(buf, q.title());
                ComponentSerialization.STREAM_CODEC.encode(buf, q.description());
                ComponentSerialization.STREAM_CODEC.encode(buf, q.giver());
                ComponentSerialization.STREAM_CODEC.encode(buf, q.place());
                Line.STREAM_CODEC.apply(ByteBufCodecs.list()).encode(buf, q.lines());
                buf.writeEnum(q.status());
                ByteBufCodecs.optional(Waypoint.STREAM_CODEC).encode(buf, q.waypoint());
                buf.writeLong(q.expiresAt());
                buf.writeBoolean(q.tracked());
                buf.writeVarInt(q.repReward());
                buf.writeVarInt(q.difficulty());
            },
            buf -> new QuestView(UUIDUtil.STREAM_CODEC.decode(buf), ResourceLocation.STREAM_CODEC.decode(buf),
                    ComponentSerialization.STREAM_CODEC.decode(buf), ComponentSerialization.STREAM_CODEC.decode(buf),
                    ComponentSerialization.STREAM_CODEC.decode(buf), ComponentSerialization.STREAM_CODEC.decode(buf),
                    Line.STREAM_CODEC.apply(ByteBufCodecs.list()).decode(buf), buf.readEnum(OpenQuestScreenPayload.Status.class),
                    ByteBufCodecs.optional(Waypoint.STREAM_CODEC).decode(buf), buf.readLong(), buf.readBoolean(),
                    buf.readVarInt(), buf.readVarInt()));

    public boolean ready() {
        return status == OpenQuestScreenPayload.Status.READY;
    }

    /**
     * One objective line. {@code need} is 0 for lines without a count.
     */
    public record Line(Component text, int have, int need, boolean done) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Line> STREAM_CODEC = StreamCodec.composite(
                ComponentSerialization.STREAM_CODEC, Line::text,
                ByteBufCodecs.VAR_INT, Line::have,
                ByteBufCodecs.VAR_INT, Line::need,
                ByteBufCodecs.BOOL, Line::done,
                Line::new);

        public static Line text(Component text) {
            return new Line(text, 0, 0, false);
        }

        public static Line count(Component text, int have, int need) {
            return new Line(text, Math.min(have, need), need, have >= need);
        }

        public static Line check(Component text, boolean done) {
            return new Line(text, 0, 0, done);
        }
    }

    /** Where the quest wants the player to go next. */
    public record Waypoint(ResourceKey<Level> dimension, BlockPos pos, Component label) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Waypoint> STREAM_CODEC = StreamCodec.composite(
                ResourceKey.streamCodec(Registries.DIMENSION), Waypoint::dimension,
                BlockPos.STREAM_CODEC, Waypoint::pos,
                ComponentSerialization.STREAM_CODEC, Waypoint::label,
                Waypoint::new);
    }
}
