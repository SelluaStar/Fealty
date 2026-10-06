package com.selluastar.fealty.api.event;

import com.selluastar.fealty.api.Stronghold;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

/**
 * A pillager stronghold is about to send a raid (a vanilla raid) against a village it menaces. Cancel to spare the
 * village, or change how strong the raid is. Fired on {@code NeoForge.EVENT_BUS}.
 */
public class MenaceRaidEvent extends Event implements ICancellableEvent {
    private final ServerLevel level;
    private final ResourceLocation village;
    private final Stronghold stronghold;
    private final ServerPlayer player;
    private int omenLevel;

    public MenaceRaidEvent(ServerLevel level, ResourceLocation village, Stronghold stronghold, ServerPlayer player, int omenLevel) {
        this.level = level;
        this.village = village;
        this.stronghold = stronghold;
        this.player = player;
        this.omenLevel = omenLevel;
    }

    public ServerLevel getLevel() {
        return level;
    }

    public ResourceLocation getVillage() {
        return village;
    }

    public Stronghold getStronghold() {
        return stronghold;
    }

    /** The player in the village the raid is started for. */
    public ServerPlayer getPlayer() {
        return player;
    }

    /** The raid's strength, as a Raid Omen level (1 to 5). */
    public int getOmenLevel() {
        return omenLevel;
    }

    public void setOmenLevel(int omenLevel) {
        this.omenLevel = Math.max(1, Math.min(5, omenLevel));
    }
}
