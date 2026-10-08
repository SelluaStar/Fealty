package com.selluastar.fealty.api.event;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

/** Rumours villagers and elders tell players. Fired on {@code NeoForge.EVENT_BUS}; since API 1.3.0. */
public abstract class RumourEvent extends Event {
    private final ServerPlayer player;
    private final Entity teller;

    protected RumourEvent(ServerPlayer player, Entity teller) {
        this.player = player;
        this.teller = teller;
    }

    public ServerPlayer getPlayer() {
        return player;
    }

    /** Who tells it: a villager, or a village elder. */
    public Entity getTeller() {
        return teller;
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

        /** The villager being asked. */
        public Villager getVillager() {
            return (Villager) getTeller();
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

    /** The villager told the player something when asked "What's up?". */
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

        /** The villager who was asked. */
        public Villager getVillager() {
            return (Villager) getTeller();
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

    /**
     * A rumour from {@code fealty/rumours/} is about to be heard: a villager's roll came up during a conversation, or
     * the player asked an elder about rumours. Cancel it and nothing is told, granted or recorded. Change
     * {@link #getLines()} to change what the teller says (an empty list says nothing; the rumour still counts).
     */
    public static class Heard extends RumourEvent implements ICancellableEvent {
        private final ResourceLocation rumour;
        private final Optional<ResourceLocation> chain;
        private final List<Component> lines;

        public Heard(ServerPlayer player, Entity teller, ResourceLocation rumour, Optional<ResourceLocation> chain, List<Component> lines) {
            super(player, teller);
            this.rumour = rumour;
            this.chain = chain;
            this.lines = new ArrayList<>(lines);
        }

        /** The rumour's id ({@code rumours/} file). */
        public ResourceLocation getRumour() {
            return rumour;
        }

        /** The chain the rumour tells of, if any. */
        public Optional<ResourceLocation> getChain() {
            return chain;
        }

        /** What the teller will say; edit freely. */
        public List<Component> getLines() {
            return lines;
        }

        public void setLines(List<Component> lines) {
            this.lines.clear();
            this.lines.addAll(lines);
        }
    }
}
