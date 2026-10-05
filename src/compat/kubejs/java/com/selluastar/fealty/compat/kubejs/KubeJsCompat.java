package com.selluastar.fealty.compat.kubejs;

import com.selluastar.fealty.api.event.CrimeWitnessedEvent;
import com.selluastar.fealty.api.event.LordshipEvent;
import com.selluastar.fealty.api.event.PriceEvent;
import com.selluastar.fealty.api.event.RepChangeEvent;
import com.selluastar.fealty.api.event.RepQuestEvent;
import com.selluastar.fealty.api.event.ThreatEvent;
import com.selluastar.fealty.api.event.TierChangedEvent;
import com.selluastar.fealty.api.event.WantedLevelEvent;

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
    }
}
