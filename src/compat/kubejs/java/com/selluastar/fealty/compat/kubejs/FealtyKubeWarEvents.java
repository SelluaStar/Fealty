package com.selluastar.fealty.compat.kubejs;

import java.util.List;
import java.util.Locale;

import org.jetbrains.annotations.Nullable;

import com.selluastar.fealty.api.Stronghold;
import com.selluastar.fealty.api.event.BanditRaidEvent;
import com.selluastar.fealty.api.event.CampaignEvent;
import com.selluastar.fealty.api.event.CaptiveEvent;
import com.selluastar.fealty.api.event.FineEvent;
import com.selluastar.fealty.api.event.GuardEvent;
import com.selluastar.fealty.api.event.LockpickEvent;
import com.selluastar.fealty.api.event.MailEvent;
import com.selluastar.fealty.api.event.MenaceRaidEvent;
import com.selluastar.fealty.api.event.StrongholdEvent;
import com.selluastar.fealty.api.event.VillageEvent;

import dev.latvian.mods.kubejs.event.KubeEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

/**
 * Script-facing wrappers for the war, guard, fine, lockpick, mail and village events. Each keeps the original event
 * as {@code event.event} for anything not wrapped here; {@code status} says which step fired.
 */
public final class FealtyKubeWarEvents {
    private FealtyKubeWarEvents() {
    }

    private static String lower(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT);
    }

    /** The original NeoForge event, for anything the wrapper does not cover. */
    public abstract static class Of<E> implements KubeEvent {
        protected final E event;

        protected Of(E event) {
            this.event = event;
        }

        public E getEvent() {
            return event;
        }
    }

    public static class Campaign extends Of<CampaignEvent> {
        public Campaign(CampaignEvent event) {
            super(event);
        }

        /** {@code declare}, {@code started}, {@code muster}, {@code battle}, {@code won} or {@code ended}. */
        public String getStatus() {
            return switch (event) {
                case CampaignEvent.Declare d -> "declare";
                case CampaignEvent.Started s -> "started";
                case CampaignEvent.Muster m -> "muster";
                case CampaignEvent.BattleStarted b -> "battle";
                case CampaignEvent.Won w -> "won";
                default -> "ended";
            };
        }

        @Nullable
        public ServerPlayer getLord() {
            return event.getLord().orElse(null);
        }

        public ResourceLocation getVillage() {
            return event.getVillage();
        }

        public Stronghold getStronghold() {
            return event.getStronghold();
        }

        /** Declare: emeralds the raid costs. */
        public int getCost() {
            return event instanceof CampaignEvent.Declare d ? d.getCost() : 0;
        }

        public void setCost(int cost) {
            if (event instanceof CampaignEvent.Declare d) {
                d.setCost(cost);
            }
        }

        /** Declare: forbid the raid, telling the lord why. */
        public void forbid(String reason) {
            if (event instanceof CampaignEvent.Declare d) {
                d.cancel(Component.literal(reason));
            }
        }

        /** Muster: lend the lord a soldier of your own. */
        public void addMember(Mob mob) {
            if (event instanceof CampaignEvent.Muster m) {
                m.addMember(mob);
            }
        }

        /** Battle: add a defender. */
        public void addDefender(LivingEntity entity) {
            if (event instanceof CampaignEvent.BattleStarted b) {
                b.addDefender(entity);
            }
        }

        /** Won: days of peace for the village. */
        public int getPeaceDays() {
            return event instanceof CampaignEvent.Won w ? w.getPeaceDays() : 0;
        }

        public void setPeaceDays(int days) {
            if (event instanceof CampaignEvent.Won w) {
                w.setPeaceDays(days);
            }
        }

        /** Won: players who fought. */
        public List<ServerPlayer> getFighters() {
            return event instanceof CampaignEvent.Won w ? w.getFighters() : List.of();
        }

        /** Ended: why ({@code lord_died}, {@code warband_fell}, {@code timed_out}, {@code called_off}, {@code lordship_lost}). */
        public String getReason() {
            return event instanceof CampaignEvent.Ended e ? lower(e.getReason()) : "";
        }
    }

    public static class StrongholdChange extends Of<StrongholdEvent> {
        public StrongholdChange(StrongholdEvent event) {
            super(event);
        }

        /** {@code discovered}, {@code razed} or {@code reoccupied}. */
        public String getStatus() {
            return event instanceof StrongholdEvent.Discovered ? "discovered" : event instanceof StrongholdEvent.Razed ? "razed" : "reoccupied";
        }

        public Stronghold getStronghold() {
            return event.getStronghold();
        }
    }

    public static class Captive extends Of<CaptiveEvent> {
        public Captive(CaptiveEvent event) {
            super(event);
        }

        /** {@code taken} or {@code freed}. */
        public String getStatus() {
            return event instanceof CaptiveEvent.Taken ? "taken" : "freed";
        }

        public LivingEntity getCaptive() {
            return event.getCaptive();
        }

        @Nullable
        public ResourceLocation getHome() {
            return event.getHome().orElse(null);
        }

        @Nullable
        public ServerPlayer getRescuer() {
            return event instanceof CaptiveEvent.Freed f ? f.getRescuer().orElse(null) : null;
        }
    }

    public static class Menace extends Of<MenaceRaidEvent> {
        public Menace(MenaceRaidEvent event) {
            super(event);
        }

        public ResourceLocation getVillage() {
            return event.getVillage();
        }

        public Stronghold getStronghold() {
            return event.getStronghold();
        }

        public ServerPlayer getPlayer() {
            return event.getPlayer();
        }

        public int getOmenLevel() {
            return event.getOmenLevel();
        }

        public void setOmenLevel(int level) {
            event.setOmenLevel(level);
        }
    }

    public static class BanditRaid extends Of<BanditRaidEvent> {
        public BanditRaid(BanditRaidEvent event) {
            super(event);
        }

        /** {@code start}, {@code won} or {@code withdrew}. */
        public String getStatus() {
            return event instanceof BanditRaidEvent.Start ? "start" : event instanceof BanditRaidEvent.Won ? "won" : "withdrew";
        }

        public ResourceLocation getVillage() {
            return event.getVillage();
        }

        public int getSize() {
            return event instanceof BanditRaidEvent.Start s ? s.getSize() : 0;
        }

        public void setSize(int size) {
            if (event instanceof BanditRaidEvent.Start s) {
                s.setSize(size);
            }
        }
    }

    public static class Guard extends Of<GuardEvent> {
        public Guard(GuardEvent event) {
            super(event);
        }

        /** {@code sworn}, {@code fell} or {@code ordered}. */
        public String getStatus() {
            return event instanceof GuardEvent.Sworn ? "sworn" : event instanceof GuardEvent.Fell ? "fell" : "ordered";
        }

        public ResourceLocation getVillage() {
            return event.getVillage();
        }

        @Nullable
        public Mob getGuard() {
            return event instanceof GuardEvent.Sworn s ? s.getGuard() : event instanceof GuardEvent.Fell f ? f.getGuard() : null;
        }

        /** Ordered: the horn order. */
        public String getOrder() {
            return event instanceof GuardEvent.Ordered o ? o.getOrder() : "";
        }
    }

    public static class Fine extends Of<FineEvent> {
        public Fine(FineEvent event) {
            super(event);
        }

        /** {@code issued}, {@code paid} or {@code refused}. */
        public String getStatus() {
            return event instanceof FineEvent.Issued ? "issued" : event instanceof FineEvent.Paid ? "paid" : "refused";
        }

        public ServerPlayer getPlayer() {
            return event.getPlayer();
        }

        public ResourceLocation getFaction() {
            return event.getFaction();
        }

        public int getCost() {
            return event instanceof FineEvent.Issued i ? i.getCost() : event instanceof FineEvent.Paid p ? p.getCost() : 0;
        }

        public void setCost(int cost) {
            if (event instanceof FineEvent.Issued i) {
                i.setCost(cost);
            }
        }
    }

    public static class Lockpick extends Of<LockpickEvent> {
        public Lockpick(LockpickEvent event) {
            super(event);
        }

        /** {@code attempt}, {@code opened} or {@code broke}. */
        public String getStatus() {
            return event instanceof LockpickEvent.Attempt ? "attempt" : event instanceof LockpickEvent.Opened ? "opened" : "broke";
        }

        public ServerPlayer getPlayer() {
            return event.getPlayer();
        }

        public BlockPos getPos() {
            return event.getPos();
        }
    }

    public static class Mail extends Of<MailEvent> {
        public Mail(MailEvent event) {
            super(event);
        }

        /** {@code sent} or {@code delivered}. */
        public String getStatus() {
            return event instanceof MailEvent.Sent ? "sent" : "delivered";
        }

        public Component getSubject() {
            return event.getSubject();
        }
    }

    public static class Village extends Of<VillageEvent> {
        public Village(VillageEvent event) {
            super(event);
        }

        public ResourceLocation getVillage() {
            return event.getVillage();
        }

        /** {@code discovered}, {@code elder_arrived}, {@code elder_fell}, {@code elder_restored}, {@code peace_started}, ... */
        public String getType() {
            return lower(event.getType());
        }
    }
}
