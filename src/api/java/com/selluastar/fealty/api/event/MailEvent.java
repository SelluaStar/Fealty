package com.selluastar.fealty.api.event;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

/** Fealty's mail. Fired on {@code NeoForge.EVENT_BUS}. */
public abstract class MailEvent extends Event {
    private final MinecraftServer server;
    private final Component subject;

    protected MailEvent(MinecraftServer server, Component subject) {
        this.server = server;
        this.subject = subject;
    }

    public MinecraftServer getServer() {
        return server;
    }

    public Component getSubject() {
        return subject;
    }

    /**
     * A player posts a letter, to another player or to a village (or someone in it). Cancel to refuse it; nothing
     * is taken from the sender.
     */
    public static class Sent extends MailEvent implements ICancellableEvent {
        private final ServerPlayer sender;
        @Nullable
        private final UUID toPlayer;
        @Nullable
        private final ResourceLocation toVillage;
        private final List<ItemStack> parcels;

        public Sent(MinecraftServer server, Component subject, ServerPlayer sender, @Nullable UUID toPlayer,
                    @Nullable ResourceLocation toVillage, List<ItemStack> parcels) {
            super(server, subject);
            this.sender = sender;
            this.toPlayer = toPlayer;
            this.toVillage = toVillage;
            this.parcels = List.copyOf(parcels);
        }

        public ServerPlayer getSender() {
            return sender;
        }

        public Optional<UUID> getToPlayer() {
            return Optional.ofNullable(toPlayer);
        }

        public Optional<ResourceLocation> getToVillage() {
            return Optional.ofNullable(toVillage);
        }

        public List<ItemStack> getParcels() {
            return parcels;
        }
    }

    /** A letter arrived in a player's inbox (from a player or a village). */
    public static class Delivered extends MailEvent {
        private final UUID recipient;
        @Nullable
        private final ResourceLocation fromVillage;

        public Delivered(MinecraftServer server, Component subject, UUID recipient, @Nullable ResourceLocation fromVillage) {
            super(server, subject);
            this.recipient = recipient;
            this.fromVillage = fromVillage;
        }

        public UUID getRecipient() {
            return recipient;
        }

        /** The village that wrote it, for village letters. */
        public Optional<ResourceLocation> getFromVillage() {
            return Optional.ofNullable(fromVillage);
        }
    }
}
