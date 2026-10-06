package com.selluastar.fealty.api.event;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.Event;

/** Something happened to a village. Fired on {@code NeoForge.EVENT_BUS}. */
public class VillageEvent extends Event {
    private final MinecraftServer server;
    private final ResourceLocation village;
    private final Type type;

    public VillageEvent(MinecraftServer server, ResourceLocation village, Type type) {
        this.server = server;
        this.village = village;
        this.type = type;
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

    public enum Type {
        /** Fealty found a new village (from a structure, a bell, a settlement or a command). */
        DISCOVERED,
        /** The village's elder took up their home. */
        ELDER_ARRIVED,
        /** The elder was killed; the village is broken until restored. */
        ELDER_FELL,
        /** A new elder was installed in a broken village. */
        ELDER_RESTORED,
        /** Razing a pillager stronghold won the village a spell of peace. */
        PEACE_STARTED,
        /** The peace ran out. */
        PEACE_ENDED,
        /** Villagers freed from a pillager camp came home. */
        VILLAGERS_RETURNED
    }
}
