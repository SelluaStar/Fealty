package com.selluastar.fealty.api.event;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.selluastar.fealty.api.Stronghold;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

/**
 * A lord's raid on a pillager stronghold. Fired on {@code NeoForge.EVENT_BUS} at each step: {@link Declare} (can be
 * vetoed), {@link Started}, {@link Muster} (twice: in the village, then near the stronghold), {@link BattleStarted},
 * and finally {@link Won} or {@link Ended}.
 */
public abstract class CampaignEvent extends Event {
    private final MinecraftServer server;
    private final UUID lord;
    private final ResourceLocation village;
    private final Stronghold stronghold;

    protected CampaignEvent(MinecraftServer server, UUID lord, ResourceLocation village, Stronghold stronghold) {
        this.server = server;
        this.lord = lord;
        this.village = village;
        this.stronghold = stronghold;
    }

    public MinecraftServer getServer() {
        return server;
    }

    public UUID getLordId() {
        return lord;
    }

    /** The lord, if they are online. */
    public Optional<ServerPlayer> getLord() {
        return Optional.ofNullable(server.getPlayerList().getPlayer(lord));
    }

    public ResourceLocation getVillage() {
        return village;
    }

    public Stronghold getStronghold() {
        return stronghold;
    }

    /**
     * A lord is about to declare a raid, before anything is paid. Cancel it to forbid the raid (a truce, a quest
     * not yet reached); the reason, if set, is shown to the lord.
     */
    public static class Declare extends CampaignEvent implements ICancellableEvent {
        private int cost;
        @Nullable
        private Component cancelReason;

        public Declare(MinecraftServer server, UUID lord, ResourceLocation village, Stronghold stronghold, int cost) {
            super(server, lord, village, stronghold);
            this.cost = cost;
        }

        /** Emeralds the lord pays to raise the warband. */
        public int getCost() {
            return cost;
        }

        public void setCost(int cost) {
            this.cost = Math.max(0, cost);
        }

        public Optional<Component> getCancelReason() {
            return Optional.ofNullable(cancelReason);
        }

        /** Cancel the raid and tell the lord why. */
        public void cancel(Component reason) {
            this.cancelReason = reason;
            setCanceled(true);
        }
    }

    /** The raid has begun: the lord has the war map and the quest, and the village's guards fall in. */
    public static class Started extends CampaignEvent {
        private final int warbandSize;

        public Started(MinecraftServer server, UUID lord, ResourceLocation village, Stronghold stronghold, int warbandSize) {
            super(server, lord, village, stronghold);
            this.warbandSize = warbandSize;
        }

        /** How many guards the village means to send (more may join through {@link Muster}). */
        public int getWarbandSize() {
            return warbandSize;
        }
    }

    /**
     * The warband forms around the lord. Add your own soldiers with {@link #addMember}: they are ordered to follow
     * the lord and count as the warband. Fired in the village when the raid is declared, and again when the rest
     * arrive near the stronghold.
     */
    public static class Muster extends CampaignEvent {
        private final Stage stage;
        private final List<Mob> members;
        private int levySize;

        public Muster(MinecraftServer server, UUID lord, ResourceLocation village, Stronghold stronghold, Stage stage, List<Mob> members,
                      int levySize) {
            super(server, lord, village, stronghold);
            this.stage = stage;
            this.members = new ArrayList<>(members);
            this.levySize = levySize;
        }

        public Stage getStage() {
            return stage;
        }

        /** The warband so far (a copy; use {@link #addMember} to add to it). */
        public List<Mob> getMembers() {
            return List.copyOf(members);
        }

        public void addMember(Mob mob) {
            if (!members.contains(mob)) {
                members.add(mob);
            }
        }

        /** Villagers called up as militia ({@link Stage#FIELD} only). */
        public int getLevySize() {
            return levySize;
        }

        public void setLevySize(int levySize) {
            this.levySize = Math.max(0, levySize);
        }

        /** Everyone in the warband after listeners had their say. */
        public List<Mob> members() {
            return members;
        }

        public enum Stage {
            /** In the village, as the raid is declared. */
            VILLAGE,
            /** Near the stronghold, as the rest of the warband catches up. */
            FIELD
        }
    }

    /** The fighting starts. Add defenders of your own (your mod's illagers) with {@link #addDefender}. */
    public static class BattleStarted extends CampaignEvent {
        private final List<LivingEntity> defenders;

        public BattleStarted(MinecraftServer server, UUID lord, ResourceLocation village, Stronghold stronghold, List<LivingEntity> defenders) {
            super(server, lord, village, stronghold);
            this.defenders = new ArrayList<>(defenders);
        }

        public List<LivingEntity> getDefenders() {
            return List.copyOf(defenders);
        }

        public void addDefender(LivingEntity entity) {
            if (!defenders.contains(entity)) {
                defenders.add(entity);
            }
        }

        /** Every defender after listeners had their say. */
        public List<LivingEntity> defenders() {
            return defenders;
        }
    }

    /** The last defender fell. Change what the victory brings before it is applied. */
    public static class Won extends CampaignEvent {
        private final List<ServerPlayer> fighters;
        private final int captivesFreed;
        private int peaceDays;
        private int razeDays;
        private int spoils;

        public Won(MinecraftServer server, UUID lord, ResourceLocation village, Stronghold stronghold, List<ServerPlayer> fighters,
                   int captivesFreed, int peaceDays, int razeDays, int spoils) {
            super(server, lord, village, stronghold);
            this.fighters = List.copyOf(fighters);
            this.captivesFreed = captivesFreed;
            this.peaceDays = peaceDays;
            this.razeDays = razeDays;
            this.spoils = spoils;
        }

        /** Players who fought (the lord among them). */
        public List<ServerPlayer> getFighters() {
            return fighters;
        }

        public int getCaptivesFreed() {
            return captivesFreed;
        }

        /** Fealty days the village is spared bandit raids and pillager patrols. */
        public int getPeaceDays() {
            return peaceDays;
        }

        public void setPeaceDays(int days) {
            this.peaceDays = Math.max(0, days);
        }

        /** Fealty days the stronghold stays empty. */
        public int getRazeDays() {
            return razeDays;
        }

        public void setRazeDays(int days) {
            this.razeDays = Math.max(0, days);
        }

        /** Rolls of the tribute table added to the village treasury. */
        public int getSpoils() {
            return spoils;
        }

        public void setSpoils(int rolls) {
            this.spoils = Math.max(0, rolls);
        }
    }

    /** The raid ended without a victory. */
    public static class Ended extends CampaignEvent {
        private final Reason reason;

        public Ended(MinecraftServer server, UUID lord, ResourceLocation village, Stronghold stronghold, Reason reason) {
            super(server, lord, village, stronghold);
            this.reason = reason;
        }

        public Reason getReason() {
            return reason;
        }

        public enum Reason {
            /** The lord fell in battle. */
            LORD_DIED,
            /** The whole warband fell and the lord left the field. */
            WARBAND_FELL,
            /** The days set for the raid ran out. */
            TIMED_OUT,
            /** The lord called it off. */
            CALLED_OFF,
            /** The lord is no longer lord of the village. */
            LORDSHIP_LOST
        }
    }
}
