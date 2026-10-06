package com.selluastar.fealty.compat.kubejs;

import com.selluastar.fealty.api.FealtyApi;

import dev.latvian.mods.kubejs.event.EventGroup;
import dev.latvian.mods.kubejs.event.EventGroupRegistry;
import dev.latvian.mods.kubejs.event.EventHandler;
import dev.latvian.mods.kubejs.plugin.KubeJSPlugin;
import dev.latvian.mods.kubejs.script.BindingRegistry;

/**
 * KubeJS bindings. Scripts get a {@code Fealty} binding (the {@code RepApi}) and {@code FealtyEvents}:
 *
 * <pre>{@code
 * FealtyEvents.repChange(event => { if (event.reason == 'fealty:trade') event.amount *= 2 })
 * FealtyEvents.tierChanged(event => event.player.tell(`Now ${event.newTier.id()}`))
 * FealtyEvents.crimeWitnessed(event => { if (event.crime == 'fealty:steal') event.cancel() })
 * FealtyEvents.campaign(event => { if (event.status == 'declare' && truce) event.forbid('The truce holds') })
 * FealtyEvents.captive(event => { if (event.status == 'freed') event.rescuer?.tell('They are safe') })
 * }</pre>
 */
public class FealtyKubePlugin implements KubeJSPlugin {
    public static final EventGroup GROUP = EventGroup.of("FealtyEvents");
    public static final EventHandler REP_CHANGE = GROUP.server("repChange", () -> FealtyKubeEvents.RepChange.class).hasResult();
    public static final EventHandler REP_CHANGED = GROUP.server("repChanged", () -> FealtyKubeEvents.RepChanged.class);
    public static final EventHandler TIER_CHANGED = GROUP.server("tierChanged", () -> FealtyKubeEvents.TierChanged.class);
    public static final EventHandler CRIME_WITNESSED = GROUP.server("crimeWitnessed", () -> FealtyKubeEvents.CrimeWitnessed.class).hasResult();
    public static final EventHandler THREAT = GROUP.server("threat", () -> FealtyKubeEvents.Threat.class).hasResult();
    public static final EventHandler PRICE = GROUP.server("price", () -> FealtyKubeEvents.Price.class);
    public static final EventHandler QUEST = GROUP.server("quest", () -> FealtyKubeEvents.Quest.class);
    public static final EventHandler LORDSHIP = GROUP.server("lordship", () -> FealtyKubeEvents.Lordship.class);
    public static final EventHandler WANTED = GROUP.server("wanted", () -> FealtyKubeEvents.Wanted.class);
    public static final EventHandler CAMPAIGN = GROUP.server("campaign", () -> FealtyKubeWarEvents.Campaign.class).hasResult();
    public static final EventHandler STRONGHOLD = GROUP.server("stronghold", () -> FealtyKubeWarEvents.StrongholdChange.class);
    public static final EventHandler CAPTIVE = GROUP.server("captive", () -> FealtyKubeWarEvents.Captive.class).hasResult();
    public static final EventHandler MENACE_RAID = GROUP.server("menaceRaid", () -> FealtyKubeWarEvents.Menace.class).hasResult();
    public static final EventHandler BANDIT_RAID = GROUP.server("banditRaid", () -> FealtyKubeWarEvents.BanditRaid.class).hasResult();
    public static final EventHandler GUARD = GROUP.server("guard", () -> FealtyKubeWarEvents.Guard.class).hasResult();
    public static final EventHandler FINE = GROUP.server("fine", () -> FealtyKubeWarEvents.Fine.class).hasResult();
    public static final EventHandler LOCKPICK = GROUP.server("lockpick", () -> FealtyKubeWarEvents.Lockpick.class).hasResult();
    public static final EventHandler MAIL = GROUP.server("mail", () -> FealtyKubeWarEvents.Mail.class).hasResult();
    public static final EventHandler VILLAGE = GROUP.server("village", () -> FealtyKubeWarEvents.Village.class);

    @Override
    public void registerEvents(EventGroupRegistry registry) {
        registry.register(GROUP);
    }

    @Override
    public void registerBindings(BindingRegistry bindings) {
        if (FealtyApi.isAvailable()) {
            bindings.add("Fealty", FealtyApi.get());
        }
    }
}
