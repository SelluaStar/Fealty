package com.selluastar.fealty.api.event;

import java.util.List;
import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

/** A village's guards. Fired on {@code NeoForge.EVENT_BUS}. */
public abstract class GuardEvent extends Event {
    private final ResourceLocation village;

    protected GuardEvent(ResourceLocation village) {
        this.village = village;
    }

    public ResourceLocation getVillage() {
        return village;
    }

    /** A Fealty guard took up a place in the village's watch (the first time, or after a guard fell). */
    public static class Sworn extends GuardEvent {
        private final Mob guard;
        private final int slot;
        private final boolean replacement;

        public Sworn(ResourceLocation village, Mob guard, int slot, boolean replacement) {
            super(village);
            this.guard = guard;
            this.slot = slot;
            this.replacement = replacement;
        }

        public Mob getGuard() {
            return guard;
        }

        /** The guard's place on the roster. */
        public int getSlot() {
            return slot;
        }

        /** Whether this guard replaces one who fell. */
        public boolean isReplacement() {
            return replacement;
        }
    }

    /** One of the village's guards died (a Fealty guard, an iron golem or another mod's guard). */
    public static class Fell extends GuardEvent {
        private final Mob guard;
        @Nullable
        private final Entity killer;

        public Fell(ResourceLocation village, Mob guard, @Nullable Entity killer) {
            super(village);
            this.guard = guard;
            this.killer = killer;
        }

        public Mob getGuard() {
            return guard;
        }

        public Optional<Entity> getKiller() {
            return Optional.ofNullable(killer);
        }
    }

    /**
     * The lord gives the village's guards an order with the Lord's Horn ({@code call}, {@code hold}, {@code guard}
     * or {@code return}). Cancel to ignore the order.
     */
    public static class Ordered extends GuardEvent implements ICancellableEvent {
        private final ServerPlayer lord;
        private final String order;
        private final List<Mob> guards;

        public Ordered(ResourceLocation village, ServerPlayer lord, String order, List<Mob> guards) {
            super(village);
            this.lord = lord;
            this.order = order;
            this.guards = List.copyOf(guards);
        }

        public ServerPlayer getLord() {
            return lord;
        }

        public String getOrder() {
            return order;
        }

        /** The guards with the lord when the horn sounded. */
        public List<Mob> getGuards() {
            return guards;
        }
    }
}
