package com.selluastar.fealty.compat.kubejs;

import java.util.function.Function;

import com.selluastar.fealty.api.event.BanditRaidEvent;
import com.selluastar.fealty.api.event.CampaignEvent;
import com.selluastar.fealty.api.event.CaptiveEvent;
import com.selluastar.fealty.api.event.CrimeWitnessedEvent;
import com.selluastar.fealty.api.event.FineEvent;
import com.selluastar.fealty.api.event.GuardEvent;
import com.selluastar.fealty.api.event.LockpickEvent;
import com.selluastar.fealty.api.event.MailEvent;
import com.selluastar.fealty.api.event.MenaceRaidEvent;
import com.selluastar.fealty.api.event.StrongholdEvent;
import com.selluastar.fealty.api.event.VillageEvent;
import com.selluastar.fealty.api.event.LordshipEvent;
import com.selluastar.fealty.api.event.PriceEvent;
import com.selluastar.fealty.api.event.RepChangeEvent;
import com.selluastar.fealty.api.event.RepQuestEvent;
import com.selluastar.fealty.api.event.ThreatEvent;
import com.selluastar.fealty.api.event.TierChangedEvent;
import com.selluastar.fealty.api.event.WantedLevelEvent;

import dev.latvian.mods.kubejs.event.EventHandler;
import dev.latvian.mods.kubejs.event.KubeEvent;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;

/** Forwards Fealty's API events to KubeJS scripts. */
public final class KubeJsCompat {
    private KubeJsCompat() {
    }

    private static void quest(RepQuestEvent e) {
        if (FealtyKubePlugin.QUEST.hasListeners()) {
            FealtyKubePlugin.QUEST.post(FealtyKubeEvents.Quest.of(e));
        }
    }

    /**
     * Pass an event of one type on to a script handler. A script's {@code event.cancel()} cancels events that can
     * be cancelled.
     */
    private static <E extends Event> void forward(Class<E> type, EventHandler handler, Function<E, KubeEvent> wrap) {
        NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, type, event -> {
            if (handler.hasListeners() && handler.post(wrap.apply(event)).interruptFalse() && event instanceof ICancellableEvent cancellable) {
                cancellable.setCanceled(true);
            }
        });
    }

    public static void init(IEventBus modBus) {
        NeoForge.EVENT_BUS.addListener((RepChangeEvent.Pre e) -> {
            if (FealtyKubePlugin.REP_CHANGE.hasListeners() && FealtyKubePlugin.REP_CHANGE.post(new FealtyKubeEvents.RepChange(e)).interruptFalse()) {
                e.setCanceled(true);
            }
        });
        NeoForge.EVENT_BUS.addListener((RepChangeEvent.Post e) -> {
            if (FealtyKubePlugin.REP_CHANGED.hasListeners()) {
                FealtyKubePlugin.REP_CHANGED.post(new FealtyKubeEvents.RepChanged(e));
            }
        });
        NeoForge.EVENT_BUS.addListener((TierChangedEvent e) -> {
            if (FealtyKubePlugin.TIER_CHANGED.hasListeners()) {
                FealtyKubePlugin.TIER_CHANGED.post(new FealtyKubeEvents.TierChanged(e));
            }
        });
        NeoForge.EVENT_BUS.addListener((CrimeWitnessedEvent e) -> {
            if (FealtyKubePlugin.CRIME_WITNESSED.hasListeners()
                    && FealtyKubePlugin.CRIME_WITNESSED.post(new FealtyKubeEvents.CrimeWitnessed(e)).interruptFalse()) {
                e.setCanceled(true);
            }
        });
        NeoForge.EVENT_BUS.addListener((ThreatEvent e) -> {
            if (FealtyKubePlugin.THREAT.hasListeners() && FealtyKubePlugin.THREAT.post(new FealtyKubeEvents.Threat(e)).interruptFalse()) {
                e.setCanceled(true);
            }
        });
        NeoForge.EVENT_BUS.addListener((PriceEvent e) -> {
            if (FealtyKubePlugin.PRICE.hasListeners()) {
                FealtyKubePlugin.PRICE.post(new FealtyKubeEvents.Price(e));
            }
        });
        // RepQuestEvent is abstract, and the event bus only accepts concrete types.
        NeoForge.EVENT_BUS.addListener((RepQuestEvent.Start e) -> quest(e));
        NeoForge.EVENT_BUS.addListener((RepQuestEvent.Complete e) -> quest(e));
        NeoForge.EVENT_BUS.addListener((RepQuestEvent.Fail e) -> quest(e));
        NeoForge.EVENT_BUS.addListener((LordshipEvent e) -> {
            if (FealtyKubePlugin.LORDSHIP.hasListeners()) {
                FealtyKubePlugin.LORDSHIP.post(new FealtyKubeEvents.Lordship(e));
            }
        });
        NeoForge.EVENT_BUS.addListener((WantedLevelEvent e) -> {
            if (FealtyKubePlugin.WANTED.hasListeners()) {
                FealtyKubePlugin.WANTED.post(new FealtyKubeEvents.Wanted(e));
            }
        });
        // The newer events have abstract bases; the bus needs each concrete type.
        forward(CampaignEvent.Declare.class, FealtyKubePlugin.CAMPAIGN, FealtyKubeWarEvents.Campaign::new);
        forward(CampaignEvent.Started.class, FealtyKubePlugin.CAMPAIGN, FealtyKubeWarEvents.Campaign::new);
        forward(CampaignEvent.Muster.class, FealtyKubePlugin.CAMPAIGN, FealtyKubeWarEvents.Campaign::new);
        forward(CampaignEvent.BattleStarted.class, FealtyKubePlugin.CAMPAIGN, FealtyKubeWarEvents.Campaign::new);
        forward(CampaignEvent.Won.class, FealtyKubePlugin.CAMPAIGN, FealtyKubeWarEvents.Campaign::new);
        forward(CampaignEvent.Ended.class, FealtyKubePlugin.CAMPAIGN, FealtyKubeWarEvents.Campaign::new);
        forward(StrongholdEvent.Discovered.class, FealtyKubePlugin.STRONGHOLD, FealtyKubeWarEvents.StrongholdChange::new);
        forward(StrongholdEvent.Razed.class, FealtyKubePlugin.STRONGHOLD, FealtyKubeWarEvents.StrongholdChange::new);
        forward(StrongholdEvent.Reoccupied.class, FealtyKubePlugin.STRONGHOLD, FealtyKubeWarEvents.StrongholdChange::new);
        forward(CaptiveEvent.Taken.class, FealtyKubePlugin.CAPTIVE, FealtyKubeWarEvents.Captive::new);
        forward(CaptiveEvent.Freed.class, FealtyKubePlugin.CAPTIVE, FealtyKubeWarEvents.Captive::new);
        forward(MenaceRaidEvent.class, FealtyKubePlugin.MENACE_RAID, FealtyKubeWarEvents.Menace::new);
        forward(BanditRaidEvent.Start.class, FealtyKubePlugin.BANDIT_RAID, FealtyKubeWarEvents.BanditRaid::new);
        forward(BanditRaidEvent.Won.class, FealtyKubePlugin.BANDIT_RAID, FealtyKubeWarEvents.BanditRaid::new);
        forward(BanditRaidEvent.Withdrew.class, FealtyKubePlugin.BANDIT_RAID, FealtyKubeWarEvents.BanditRaid::new);
        forward(GuardEvent.Sworn.class, FealtyKubePlugin.GUARD, FealtyKubeWarEvents.Guard::new);
        forward(GuardEvent.Fell.class, FealtyKubePlugin.GUARD, FealtyKubeWarEvents.Guard::new);
        forward(GuardEvent.Ordered.class, FealtyKubePlugin.GUARD, FealtyKubeWarEvents.Guard::new);
        forward(FineEvent.Issued.class, FealtyKubePlugin.FINE, FealtyKubeWarEvents.Fine::new);
        forward(FineEvent.Paid.class, FealtyKubePlugin.FINE, FealtyKubeWarEvents.Fine::new);
        forward(FineEvent.Refused.class, FealtyKubePlugin.FINE, FealtyKubeWarEvents.Fine::new);
        forward(LockpickEvent.Attempt.class, FealtyKubePlugin.LOCKPICK, FealtyKubeWarEvents.Lockpick::new);
        forward(LockpickEvent.Opened.class, FealtyKubePlugin.LOCKPICK, FealtyKubeWarEvents.Lockpick::new);
        forward(LockpickEvent.Broke.class, FealtyKubePlugin.LOCKPICK, FealtyKubeWarEvents.Lockpick::new);
        forward(MailEvent.Sent.class, FealtyKubePlugin.MAIL, FealtyKubeWarEvents.Mail::new);
        forward(MailEvent.Delivered.class, FealtyKubePlugin.MAIL, FealtyKubeWarEvents.Mail::new);
        forward(VillageEvent.class, FealtyKubePlugin.VILLAGE, FealtyKubeWarEvents.Village::new);
    }
}
