package com.selluastar.fealty.api.event;

import java.util.Optional;
import java.util.UUID;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.Event;

/** A village gained, lost or changed its sworn lord. Fired on {@code NeoForge.EVENT_BUS}. */
public class LordshipEvent extends Event {
    private final MinecraftServer server;
    private final ResourceLocation village;
    private final Type type;
    private final Optional<UUID> newLord;
    private final Optional<UUID> previousLord;

    public LordshipEvent(MinecraftServer server, ResourceLocation village, Type type, Optional<UUID> newLord, Optional<UUID> previousLord) {
        this.server = server;
        this.village = village;
        this.type = type;
        this.newLord = newLord;
        this.previousLord = previousLord;
    }

    public MinecraftServer getServer() {
        return server;
    }

    public ResourceLocation getVillage() {
        return village;
    }

    public Type getType() {
        return type;
    }

    public Optional<UUID> getNewLord() {
        return newLord;
    }

    public Optional<UUID> getPreviousLord() {
        return previousLord;
    }

    public enum Type {
        /** A player swore the village to them with a Royal Writ. */
        SWORN,
        /** The lord fell below the required tier and the village renounced them. */
        LOST,
        /** Another player took the village from its lord. */
        USURPED
    }
}
