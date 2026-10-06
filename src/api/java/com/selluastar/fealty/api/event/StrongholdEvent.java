package com.selluastar.fealty.api.event;

import com.selluastar.fealty.api.Stronghold;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.Event;

/** Something happened to a pillager stronghold. Fired on {@code NeoForge.EVENT_BUS}. */
public abstract class StrongholdEvent extends Event {
    private final MinecraftServer server;
    private final Stronghold stronghold;

    protected StrongholdEvent(MinecraftServer server, Stronghold stronghold) {
        this.server = server;
        this.stronghold = stronghold;
    }

    public MinecraftServer getServer() {
        return server;
    }

    public Stronghold getStronghold() {
        return stronghold;
    }

    /** A stronghold became known to Fealty. */
    public static class Discovered extends StrongholdEvent {
        private final How how;

        public Discovered(MinecraftServer server, Stronghold stronghold, How how) {
            super(server, stronghold);
            this.how = how;
        }

        public How getHow() {
            return how;
        }

        public enum How {
            /** A pillager camp was generated and loaded for the first time. */
            GENERATED,
            /** A player came near it. */
            APPROACHED,
            /** A lord's scouts found it. */
            SCOUTED
        }
    }

    /** A lord's warband razed the stronghold. */
    public static class Razed extends StrongholdEvent {
        private final ResourceLocation village;
        private final int days;

        public Razed(MinecraftServer server, Stronghold stronghold, ResourceLocation village, int days) {
            super(server, stronghold);
            this.village = village;
            this.days = days;
        }

        /** The village whose warband razed it. */
        public ResourceLocation getVillage() {
            return village;
        }

        /** How many Fealty days it stays razed. */
        public int getDays() {
            return days;
        }
    }

    /** The raze is over: pillagers move back in. */
    public static class Reoccupied extends StrongholdEvent {
        public Reoccupied(MinecraftServer server, Stronghold stronghold) {
            super(server, stronghold);
        }
    }
}
