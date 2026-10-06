package com.selluastar.fealty.mail;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.ItemStack;

/**
 * One letter: who it is from, who it is for (a player, or a village), its text, up to three parcels, and when it
 * arrives. Letters to players wait in their inbox until {@code deliverAt}; letters to villages are read by the
 * village when they arrive, and usually answered.
 */
public final class Letter {
    public static final int MAX_PARCELS = 3;
    public static final Codec<Letter> CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.CODEC.fieldOf("id").forGetter(l -> l.id),
            Kind.CODEC.optionalFieldOf("kind", Kind.PLAYER).forGetter(l -> l.kind),
            ComponentSerialization.CODEC.fieldOf("from").forGetter(l -> l.from),
            UUIDUtil.CODEC.optionalFieldOf("from_player").forGetter(l -> Optional.ofNullable(l.fromPlayer)),
            ResourceLocation.CODEC.optionalFieldOf("from_village").forGetter(l -> Optional.ofNullable(l.fromVillage)),
            UUIDUtil.CODEC.optionalFieldOf("to_player").forGetter(l -> Optional.ofNullable(l.toPlayer)),
            ResourceLocation.CODEC.optionalFieldOf("to_village").forGetter(l -> Optional.ofNullable(l.toVillage)),
            ComponentSerialization.CODEC.optionalFieldOf("subject", Component.empty()).forGetter(l -> l.subject),
            ComponentSerialization.CODEC.optionalFieldOf("body", Component.empty()).forGetter(l -> l.body),
            ItemStack.OPTIONAL_CODEC.listOf().optionalFieldOf("items", List.of()).forGetter(l -> l.items),
            Codec.LONG.optionalFieldOf("sent", 0L).forGetter(l -> l.sentAt),
            Codec.LONG.optionalFieldOf("deliver_at", 0L).forGetter(l -> l.deliverAt),
            Codec.BOOL.optionalFieldOf("read", false).forGetter(l -> l.read),
            Codec.BOOL.optionalFieldOf("announced", false).forGetter(l -> l.announced)
    ).apply(i, (id, kind, from, fromPlayer, fromVillage, toPlayer, toVillage, subject, body, items, sent, deliverAt, read, announced) -> {
        Letter l = new Letter(id, kind, from, fromPlayer.orElse(null), fromVillage.orElse(null), toPlayer.orElse(null),
                toVillage.orElse(null), subject, body, items, sent, deliverAt);
        l.read = read;
        l.announced = announced;
        return l;
    }));

    private final UUID id;
    private final Kind kind;
    private final Component from;
    @Nullable
    private final UUID fromPlayer;
    @Nullable
    private final ResourceLocation fromVillage;
    @Nullable
    private final UUID toPlayer;
    @Nullable
    private final ResourceLocation toVillage;
    private final Component subject;
    private final Component body;
    private final List<ItemStack> items = new ArrayList<>();
    private final long sentAt;
    private final long deliverAt;
    private boolean read;
    private boolean announced;

    public Letter(UUID id, Kind kind, Component from, @Nullable UUID fromPlayer, @Nullable ResourceLocation fromVillage,
                  @Nullable UUID toPlayer, @Nullable ResourceLocation toVillage, Component subject, Component body,
                  List<ItemStack> items, long sentAt, long deliverAt) {
        this.id = id;
        this.kind = kind;
        this.from = from;
        this.fromPlayer = fromPlayer;
        this.fromVillage = fromVillage;
        this.toPlayer = toPlayer;
        this.toVillage = toVillage;
        this.subject = subject;
        this.body = body;
        for (ItemStack stack : items) {
            if (!stack.isEmpty() && this.items.size() < MAX_PARCELS) {
                this.items.add(stack.copy());
            }
        }
        this.sentAt = sentAt;
        this.deliverAt = deliverAt;
    }

    public UUID id() {
        return id;
    }

    public Kind kind() {
        return kind;
    }

    public Component from() {
        return from;
    }

    @Nullable
    public UUID fromPlayer() {
        return fromPlayer;
    }

    @Nullable
    public ResourceLocation fromVillage() {
        return fromVillage;
    }

    @Nullable
    public UUID toPlayer() {
        return toPlayer;
    }

    @Nullable
    public ResourceLocation toVillage() {
        return toVillage;
    }

    public Component subject() {
        return subject;
    }

    public Component body() {
        return body;
    }

    /** The parcels still in the letter (taking them empties the list). */
    public List<ItemStack> items() {
        return items;
    }

    public long sentAt() {
        return sentAt;
    }

    public long deliverAt() {
        return deliverAt;
    }

    public boolean arrived(long now) {
        return now >= deliverAt;
    }

    public boolean isRead() {
        return read;
    }

    public void setRead(boolean read) {
        this.read = read;
    }

    /** Whether the recipient has been told it arrived. */
    public boolean announced() {
        return announced;
    }

    public void setAnnounced(boolean announced) {
        this.announced = announced;
    }

    public enum Kind implements StringRepresentable {
        /** Written by a player. */
        PLAYER("player"),
        /** Written by a village: its elder, its people, or someone who lives there. */
        VILLAGE("village"),
        /** A notice: bounties, warnings. */
        NOTICE("notice");

        public static final Codec<Kind> CODEC = StringRepresentable.fromEnum(Kind::values);
        private final String name;

        Kind(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }
}
