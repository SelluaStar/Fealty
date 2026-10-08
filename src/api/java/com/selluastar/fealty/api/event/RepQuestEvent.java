package com.selluastar.fealty.api.event;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;

/** A trusting villager quest started, completed or failed. Fired on {@code NeoForge.EVENT_BUS}. */
public abstract class RepQuestEvent extends Event {
    private final ServerPlayer player;
    private final ResourceLocation faction;
    private final ResourceLocation quest;
    private final ResourceLocation questType;

    protected RepQuestEvent(ServerPlayer player, ResourceLocation faction, ResourceLocation quest, ResourceLocation questType) {
        this.player = player;
        this.faction = faction;
        this.quest = quest;
        this.questType = questType;
    }

    public ServerPlayer getPlayer() {
        return player;
    }

    /** The faction (usually a village) that gave the quest. */
    public ResourceLocation getFaction() {
        return faction;
    }

    /** The quest's data pack id. */
    public ResourceLocation getQuest() {
        return quest;
    }

    /** The quest type, e.g. {@code fealty:fetch}. */
    public ResourceLocation getQuestType() {
        return questType;
    }

    public static class Start extends RepQuestEvent {
        public Start(ServerPlayer player, ResourceLocation faction, ResourceLocation quest, ResourceLocation questType) {
            super(player, faction, quest, questType);
        }
    }

    public static class Complete extends RepQuestEvent {
        private final int repReward;

        public Complete(ServerPlayer player, ResourceLocation faction, ResourceLocation quest, ResourceLocation questType, int repReward) {
            super(player, faction, quest, questType);
            this.repReward = repReward;
        }

        public int getRepReward() {
            return repReward;
        }
    }

    public static class Fail extends RepQuestEvent {
        private final Reason reason;

        public Fail(ServerPlayer player, ResourceLocation faction, ResourceLocation quest, ResourceLocation questType, Reason reason) {
            super(player, faction, quest, questType);
            this.reason = reason;
        }

        public Reason getFailReason() {
            return reason;
        }
    }

    public enum Reason {
        ABANDONED,
        EXPIRED,
        GIVER_LOST,
        TARGET_LOST
    }
}
