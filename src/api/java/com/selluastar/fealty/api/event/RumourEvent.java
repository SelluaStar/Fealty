package com.selluastar.fealty.api.event;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.neoforged.bus.api.Event;

/** What a villager tells a player who asks "What's up?". Fired on {@code NeoForge.EVENT_BUS}; since API 1.3.0. */
public abstract class RumourEvent extends Event {
    private final ServerPlayer player;
    private final Villager villager;

    protected RumourEvent(ServerPlayer player, Villager villager) {
        this.player = player;
        this.villager = villager;
    }

    public ServerPlayer getPlayer() {
        return player;
    }

    /** The villager being asked. */
    public Villager getVillager() {
        return villager;
    }

    /**
     * A villager is about to tell the player something they know. Add rumours of your own: they join the pool Fealty
     * draws from (strongholds, bandit camps, who needs help, what a villager sells, how the village fares).
     * Only fired when the villager has real news to give (a neutral or better player, once a day per villager).
     */
    public static class Gather extends RumourEvent {
        private final List<Rumour> rumours = new ArrayList<>();

        public Gather(ServerPlayer player, Villager villager) {
            super(player, villager);
        }

        /**
         * @param id     an id for the rumour, for {@link Told}
         * @param tell   what the villager says
         * @param weight how likely it is against the others (Fealty's are 2 to 5)
         */
        public void add(ResourceLocation id, Component tell, int weight) {
            rumours.add(new Rumour(id, tell, Math.max(1, weight)));
        }

        public List<Rumour> getRumours() {
            return List.copyOf(rumours);
        }

        public record Rumour(ResourceLocation id, Component tell, int weight) {
        }
    }

    /** The villager told the player something. */
    public static class Told extends RumourEvent {
        private final ResourceLocation topic;
        private final Component text;
        private final boolean useful;
        private final boolean fromChat;

        public Told(ServerPlayer player, Villager villager, ResourceLocation topic, Component text, boolean useful, boolean fromChat) {
            super(player, villager);
            this.topic = topic;
            this.text = text;
            this.useful = useful;
            this.fromChat = fromChat;
        }

        /** The topic's id (a {@code chatter/} file, a live rumour such as {@code fealty:rumour/stronghold}, or a trivia/refusal). */
        public ResourceLocation getTopic() {
            return topic;
        }

        public Component getText() {
            return text;
        }

        /** Whether it was real news, rather than small talk or a refusal. */
        public boolean isUseful() {
            return useful;
        }

        /** Whether it was what two villagers had been chatting about. */
        public boolean isFromChat() {
            return fromChat;
        }
    }
}
