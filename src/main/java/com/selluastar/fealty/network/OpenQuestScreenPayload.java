package com.selluastar.fealty.network;

import java.util.List;

import com.selluastar.fealty.Fealty;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Server to client: open (or refresh) a quest giver's screen. */
public record OpenQuestScreenPayload(int entityId, Component title, Component subtitle, Component greeting, int rep, boolean showRep,
                                     List<QuestEntry> quests, List<ActionEntry> actions) implements CustomPacketPayload {
    public static final Type<OpenQuestScreenPayload> TYPE = new Type<>(Fealty.id("open_quest_screen"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenQuestScreenPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.entityId());
                ComponentSerialization.STREAM_CODEC.encode(buf, p.title());
                ComponentSerialization.STREAM_CODEC.encode(buf, p.subtitle());
                ComponentSerialization.STREAM_CODEC.encode(buf, p.greeting());
                buf.writeVarInt(p.rep());
                buf.writeBoolean(p.showRep());
                QuestEntry.STREAM_CODEC.apply(ByteBufCodecs.list()).encode(buf, p.quests());
                ActionEntry.STREAM_CODEC.apply(ByteBufCodecs.list()).encode(buf, p.actions());
            },
            buf -> new OpenQuestScreenPayload(buf.readVarInt(), ComponentSerialization.STREAM_CODEC.decode(buf),
                    ComponentSerialization.STREAM_CODEC.decode(buf), ComponentSerialization.STREAM_CODEC.decode(buf),
                    buf.readVarInt(), buf.readBoolean(),
                    QuestEntry.STREAM_CODEC.apply(ByteBufCodecs.list()).decode(buf),
                    ActionEntry.STREAM_CODEC.apply(ByteBufCodecs.list()).decode(buf)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public enum Status {
        OFFER,
        ACTIVE,
        READY
    }

    /** A quest shown on the screen: an offer to accept, or the accepted quest with its progress. */
    public record QuestEntry(ResourceLocation id, Component title, Component description, List<Component> lines, int repReward,
                             int difficulty, Status status, Rewards rewards) {
        public static final StreamCodec<RegistryFriendlyByteBuf, QuestEntry> STREAM_CODEC = StreamCodec.of(
                (buf, e) -> {
                    ResourceLocation.STREAM_CODEC.encode(buf, e.id());
                    ComponentSerialization.STREAM_CODEC.encode(buf, e.title());
                    ComponentSerialization.STREAM_CODEC.encode(buf, e.description());
                    ComponentSerialization.STREAM_CODEC.apply(ByteBufCodecs.list()).encode(buf, e.lines());
                    buf.writeVarInt(e.repReward());
                    buf.writeVarInt(e.difficulty());
                    buf.writeEnum(e.status());
                    Rewards.STREAM_CODEC.encode(buf, e.rewards());
                },
                buf -> new QuestEntry(ResourceLocation.STREAM_CODEC.decode(buf), ComponentSerialization.STREAM_CODEC.decode(buf),
                        ComponentSerialization.STREAM_CODEC.decode(buf), ComponentSerialization.STREAM_CODEC.apply(ByteBufCodecs.list()).decode(buf),
                        buf.readVarInt(), buf.readVarInt(), buf.readEnum(Status.class), Rewards.STREAM_CODEC.decode(buf)));

        public QuestEntry withDescription(Component newDescription) {
            return new QuestEntry(id, title, newDescription, lines, repReward, difficulty, status, rewards);
        }
    }

    /**
     * What a quest pays besides reputation, for the reward preview.
     *
     * @param loot      whether a loot table adds more
     * @param timeLimit ticks to finish the quest, or 0
     */
    public record Rewards(List<ItemStack> items, int renown, int experience, boolean loot, int timeLimit) {
        public static final Rewards NONE = new Rewards(List.of(), 0, 0, false, 0);
        public static final StreamCodec<RegistryFriendlyByteBuf, Rewards> STREAM_CODEC = StreamCodec.composite(
                ItemStack.OPTIONAL_STREAM_CODEC.apply(ByteBufCodecs.list()), Rewards::items,
                ByteBufCodecs.VAR_INT, Rewards::renown,
                ByteBufCodecs.VAR_INT, Rewards::experience,
                ByteBufCodecs.BOOL, Rewards::loot,
                ByteBufCodecs.VAR_INT, Rewards::timeLimit,
                Rewards::new);
    }

    /** A button for something other than a quest (rumours, presenting a writ, taxes...). */
    public record ActionEntry(String id, Component label, boolean enabled, Component hint) {
        public static final StreamCodec<RegistryFriendlyByteBuf, ActionEntry> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, ActionEntry::id,
                ComponentSerialization.STREAM_CODEC, ActionEntry::label,
                ByteBufCodecs.BOOL, ActionEntry::enabled,
                ComponentSerialization.STREAM_CODEC, ActionEntry::hint,
                ActionEntry::new);

        public static ActionEntry of(String id, Component label) {
            return new ActionEntry(id, label, true, Component.empty());
        }

        public static ActionEntry disabled(String id, Component label, Component hint) {
            return new ActionEntry(id, label, false, hint);
        }
    }
}
