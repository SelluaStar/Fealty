package com.selluastar.fealty.api.event;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

/** The dialogue box with an NPC. Fired on {@code NeoForge.EVENT_BUS}; since API 1.3.0. */
public abstract class DialogueEvent extends Event {
    private final ServerPlayer player;
    private final Entity npc;

    protected DialogueEvent(ServerPlayer player, Entity npc) {
        this.player = player;
        this.npc = npc;
    }

    public ServerPlayer getPlayer() {
        return player;
    }

    public Entity getNpc() {
        return npc;
    }

    /** A player starts talking to an NPC. Cancel to keep Fealty's dialogue box shut (the interaction goes on as usual). */
    public static class Open extends DialogueEvent implements ICancellableEvent {
        public Open(ServerPlayer player, Entity npc) {
            super(player, npc);
        }
    }

    /** The player picked a reply. Cancel to ignore the pick (the box is drawn again). */
    public static class Choose extends DialogueEvent implements ICancellableEvent {
        private final String option;

        public Choose(ServerPlayer player, Entity npc, String option) {
            super(player, npc);
            this.option = option;
        }

        /** The reply's id. */
        public String getOption() {
            return option;
        }
    }
}
