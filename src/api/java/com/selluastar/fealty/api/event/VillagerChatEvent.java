package com.selluastar.fealty.api.event;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

/** Villagers chatting with each other in speech bubbles. Fired on {@code NeoForge.EVENT_BUS}; since API 1.3.0. */
public abstract class VillagerChatEvent extends Event {
    private final ServerLevel level;
    private final Villager first;
    private final Villager second;
    private final ResourceLocation topic;

    protected VillagerChatEvent(ServerLevel level, Villager first, Villager second, ResourceLocation topic) {
        this.level = level;
        this.first = first;
        this.second = second;
        this.topic = topic;
    }

    public ServerLevel getLevel() {
        return level;
    }

    /** The villager who walks up to the other and speaks first. */
    public Villager getFirst() {
        return first;
    }

    public Villager getSecond() {
        return second;
    }

    /**
     * What they will talk about: the id of a {@code chatter/} data file, or one of Fealty's live topics such as
     * {@code fealty:rumour/stronghold}.
     */
    public ResourceLocation getTopic() {
        return topic;
    }

    /** Two villagers are about to start a chat. Cancel to stop it. */
    public static class Started extends VillagerChatEvent implements ICancellableEvent {
        public Started(ServerLevel level, Villager first, Villager second, ResourceLocation topic) {
            super(level, first, second, topic);
        }
    }
}
