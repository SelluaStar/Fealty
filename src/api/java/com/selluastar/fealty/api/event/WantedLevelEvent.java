package com.selluastar.fealty.api.event;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;

/** A player's wanted level with a faction changed (0 = not wanted). Fired on {@code NeoForge.EVENT_BUS}. */
public class WantedLevelEvent extends Event {
    public static final int NOT_WANTED = 0;
    /** Bounty hunters are sent after the player. */
    public static final int BOUNTY = 1;
    /** The Tyrant Lord rides out. */
    public static final int TYRANT = 2;

    private final ServerPlayer player;
    private final ResourceLocation faction;
    private final int oldLevel;
    private final int newLevel;

    public WantedLevelEvent(ServerPlayer player, ResourceLocation faction, int oldLevel, int newLevel) {
        this.player = player;
        this.faction = faction;
        this.oldLevel = oldLevel;
        this.newLevel = newLevel;
    }

    public ServerPlayer getPlayer() {
        return player;
    }

    public ResourceLocation getFaction() {
        return faction;
    }

    public int getOldLevel() {
        return oldLevel;
    }

    public int getNewLevel() {
        return newLevel;
    }
}
