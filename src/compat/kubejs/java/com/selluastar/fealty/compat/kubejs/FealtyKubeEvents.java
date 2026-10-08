package com.selluastar.fealty.compat.kubejs;

import com.selluastar.fealty.api.RepTier;
import com.selluastar.fealty.api.Severity;
import com.selluastar.fealty.api.event.CrimeWitnessedEvent;
import com.selluastar.fealty.api.event.LordshipEvent;
import com.selluastar.fealty.api.event.PriceEvent;
import com.selluastar.fealty.api.event.RepChangeEvent;
import com.selluastar.fealty.api.event.RepQuestEvent;
import com.selluastar.fealty.api.event.ThreatEvent;
import com.selluastar.fealty.api.event.TierChangedEvent;
import com.selluastar.fealty.api.event.WantedLevelEvent;

import dev.latvian.mods.kubejs.event.KubeEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;

/** Script-facing wrappers around Fealty's API events. */
public final class FealtyKubeEvents {
    private FealtyKubeEvents() {
    }

    public static class RepChange implements KubeEvent {
        private final RepChangeEvent.Pre event;

        public RepChange(RepChangeEvent.Pre event) {
            this.event = event;
        }

        public ServerPlayer getPlayer() {
            return event.getPlayer();
        }

        public ResourceLocation getFaction() {
            return event.getFaction();
        }

        public ResourceLocation getReason() {
            return event.getReason();
        }

        public int getOldRep() {
            return event.getOldRep();
        }

        public int getAmount() {
            return event.getAmount();
        }

        public void setAmount(int amount) {
            event.setAmount(amount);
        }
    }

    public record RepChanged(ServerPlayer player, ResourceLocation faction, ResourceLocation reason, int oldRep, int newRep)
            implements KubeEvent {
        public RepChanged(RepChangeEvent.Post event) {
            this(event.getPlayer(), event.getFaction(), event.getReason(), event.getOldRep(), event.getNewRep());
        }
    }

    public record TierChanged(ServerPlayer player, ResourceLocation faction, RepTier oldTier, RepTier newTier) implements KubeEvent {
        public TierChanged(TierChangedEvent event) {
            this(event.getPlayer(), event.getFaction(), event.getOldTier(), event.getNewTier());
        }
    }

    public static class CrimeWitnessed implements KubeEvent {
        private final CrimeWitnessedEvent event;

        public CrimeWitnessed(CrimeWitnessedEvent event) {
            this.event = event;
        }

        public ServerPlayer getPlayer() {
            return event.getPlayer();
        }

        public ResourceLocation getFaction() {
            return event.getFaction();
        }

        public ResourceLocation getCrime() {
            return event.getCrime();
        }

        public int getWitnessCount() {
            return event.getWitnesses().size();
        }

        public String getSeverity() {
            return event.getSeverity().getSerializedName();
        }

        public void setSeverity(String severity) {
            for (Severity value : Severity.values()) {
                if (value.getSerializedName().equals(severity)) {
                    event.setSeverity(value);
                }
            }
        }
    }

    public record Threat(ServerPlayer player, Villager villager, ResourceLocation faction) implements KubeEvent {
        public Threat(ThreatEvent event) {
            this(event.getPlayer(), event.getVillager(), event.getFaction());
        }
    }

    public static class Price implements KubeEvent {
        private final PriceEvent event;

        public Price(PriceEvent event) {
            this.event = event;
        }

        public ServerPlayer getPlayer() {
            return event.getPlayer();
        }

        public Villager getVillager() {
            return event.getVillager();
        }

        public RepTier getTier() {
            return event.getTier();
        }

        public float getMultiplier() {
            return event.getMultiplier();
        }

        public void setMultiplier(float multiplier) {
            event.setMultiplier(multiplier);
        }
    }

    public record Quest(ServerPlayer player, ResourceLocation faction, ResourceLocation quest, ResourceLocation type, String status)
            implements KubeEvent {
        public static Quest of(RepQuestEvent event) {
            String status = event instanceof RepQuestEvent.Start ? "started" : event instanceof RepQuestEvent.Complete ? "completed" : "failed";
            return new Quest(event.getPlayer(), event.getFaction(), event.getQuest(), event.getQuestType(), status);
        }
    }

    public record Lordship(ResourceLocation village, String type) implements KubeEvent {
        public Lordship(LordshipEvent event) {
            this(event.getVillage(), event.getType().name().toLowerCase(java.util.Locale.ROOT));
        }
    }

    public record Wanted(ServerPlayer player, ResourceLocation faction, int oldLevel, int newLevel) implements KubeEvent {
        public Wanted(WantedLevelEvent event) {
            this(event.getPlayer(), event.getFaction(), event.getOldLevel(), event.getNewLevel());
        }
    }
}
