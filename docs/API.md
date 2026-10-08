# Fealty API

The API lives in `com.selluastar.fealty.api` and ships as a separate jar
(`fealty-<version>+api.<api_version>-api.jar`). Compile against that jar only, and declare Fealty as an
optional dependency. The API follows semantic versioning (`FealtyApi.API_VERSION`).

```groovy
dependencies {
    compileOnly files("libs/fealty-0.1.0+api.1.0.0-api.jar")
}
```

## Reading and changing reputation

```java
if (FealtyApi.isAvailable()) {
    RepApi api = FealtyApi.get();
    ResourceLocation village = api.getFactionAt(level, pos).orElse(null);
    if (village != null) {
        int rep = api.getRep(player, village);
        RepTier tier = api.getTier(player, village);
        if (tier.isAtLeast(api.getTier(RepTiers.TRUSTED).orElseThrow())) {
            // ...
        }
        api.addRep(player, village, 5, RepSources.QUEST);
    }
}
```

| Method | Purpose |
|---|---|
| `getRep(player, faction)`, `getTier(player, faction)` | Standing with a faction |
| `addRep(player, faction, amount, reason)` | Change rep. Fires `RepChangeEvent.Pre/Post` and follows the "negative rep only heals through quests" rule for unregistered or non-redemption reasons |
| `setRep(...)` | Set rep directly |
| `applySource(player, faction, source)` | Apply a registered source with its data pack amount and caps |
| `priceMultiplier(player, villager)` | What that villager charges the player |
| `isWanted(player, faction)`, `getHeat(...)` | Wanted escalation |
| `getRenown`, `getRenownTier` | Global Renown |
| `getFactionOf(entity)`, `getFactionAt(level, pos)` | Which faction an entity or place belongs to |
| `getLord(server, village)` | A village's sworn lord |
| `getTiers()`, `getTier(id)` | The data-driven tier table |
| `getStrongholds(server)`, `getNearestStronghold(level, pos, radius)` | Known pillager camps and outposts |
| `getCampaign(server, lord)` | The raid a lord is leading (village, stronghold, phase) |
| `declareRaid(lord, village, stronghold)` | Start a raid as if from the Village Hall; returns the problem if it cannot |
| `isAtPeace(server, village)` | Whether a victory's peace protects the village |
| `isCaptive`, `isWarbandMember`, `isStrongholdDefender` | What part an entity plays in the war |

Faction ids: villages are `village:<dim namespace>/<dim path>/<x>_<z>`. Static factions are `fealty:wanderers`,
`fealty:bandits` and `fealty:thieves_guild`, plus any a data pack adds.

## Events (on `NeoForge.EVENT_BUS`)

| Event | Fires when | You can |
|---|---|---|
| `RepChangeEvent.Pre` | A change is about to apply | Cancel it, or `setAmount` |
| `RepChangeEvent.Post` | A change has applied | React |
| `TierChangedEvent` | A player crosses a tier boundary (faction `fealty:renown` for Renown) | React |
| `CrimeWitnessedEvent` | A villager or guard sees a crime | Cancel it, or `setSeverity` |
| `ThreatEvent` | A player threatens a villager | Cancel it |
| `PriceEvent` | A villager's prices are calculated | `setMultiplier` |
| `RepQuestEvent.Start/Complete/Fail` | A trusting villager quest starts, completes or fails | React |
| `LordshipEvent` | A village is sworn, lost or usurped | React |
| `WantedLevelEvent` | Wanted level changes (1 = bounty hunters, 2 = Tyrant Lord) | React |
| `LordshipEvent` (UNREST, CONTENT) | A lord falls below Honored (the grace starts) / is Honored again | React |

### War: pillager strongholds and the lord's raids (API 1.1.0)

A `Stronghold` record describes a target: `id`, `dimension`, `pos`, `kind` (`CAMP` for Fealty's pillager camps,
forts and castles, `OUTPOST` for pillager outposts), `structure`, `name`, `razed`, `captives`, and (API 1.2.0)
`tier` (its stronghold kind's id, such as `fealty:scout_camp`, `fealty:camp`, `fealty:outpost`, `fealty:fort` or
`fealty:castle`), `threat` (1 to 5 skulls, trait included) and `trait` (`none`, `veterans`, `evoker` or `beasts`).
Every event below carries it, so a listener can tell a scout camp from a castle; `CampaignEvent.Declare.getCost()`
and `CampaignEvent.Won`'s spoils, peace and raze days already include the threat's scaling.

| Event | Fires when | You can |
|---|---|---|
| `StrongholdEvent.Discovered` | A camp or outpost becomes known (`getHow()`: GENERATED, APPROACHED, SCOUTED) | React |
| `CampaignEvent.Declare` | A lord is about to raise the warband, before paying | `cancel(reason)`, `setCost` |
| `CampaignEvent.Started` | A raid on a stronghold has begun | React |
| `CampaignEvent.Muster` | The warband forms (stage VILLAGE, then FIELD near the stronghold) | `addMember(mob)` (it follows the lord and counts), `setLevySize` |
| `CampaignEvent.BattleStarted` | The fight at the stronghold begins | `addDefender(entity)` |
| `CampaignEvent.Won` | The last defender falls | `setPeaceDays`, `setRazeDays`, `setSpoils` |
| `CampaignEvent.Ended` | A raid ends without victory (`Reason`: LORD_DIED, WARBAND_FELL, TIMED_OUT, CALLED_OFF, LORDSHIP_LOST) | React |
| `StrongholdEvent.Razed` / `.Reoccupied` | A stronghold is razed / the pillagers return | React |
| `CaptiveEvent.Taken` / `.Freed` | A captive is put in a camp's cage / freed (`getRescuer()` empty when a raid did it) | Cancel `Taken` |
| `MenaceRaidEvent` | A stronghold is about to send a (vanilla) raid against a village | Cancel, `setOmenLevel` |

### Villager gossip (API 1.3.0)

| Event | Fires when | You can |
|---|---|---|
| `VillagerChatEvent.Started` | Two villagers are about to chat (`getFirst()`, `getSecond()`, `getTopic()`) | Cancel it |
| `RumourEvent.Gather` | A villager is about to tell a trusted-enough player real news | `add(id, tell, weight)`: your own rumours join the pool |
| `RumourEvent.Told` | A villager told a player something (`getTopic()`, `getText()`, `isUseful()`, `isFromChat()`) | React |

`StrongholdEvent.Discovered.How` also has `RUMOURED` now: a villager's tip put a stronghold on a Trusted player's War tab.

### Other systems

| Event | Fires when | You can |
|---|---|---|
| `BanditRaidEvent.Start` / `.Won` / `.Withdrew` | A bandit raid sets out / is beaten / makes off with plunder | Cancel or `setSize` the start |
| `GuardEvent.Sworn` / `.Fell` / `.Ordered` | A Fealty guard takes a place in the watch / a village guard dies / the lord blows the horn | Cancel an order |
| `FineEvent.Issued` / `.Paid` / `.Refused` | A guard demands a fine / a fine is paid (to a guard or the elder) / refused | Cancel or `setCost` when issued |
| `LockpickEvent.Attempt` / `.Opened` / `.Broke` | A try at a lock / it opens / the pick snaps | Cancel an attempt |
| `MailEvent.Sent` / `.Delivered` | A player posts a letter / a letter arrives | Cancel sending |
| `VillageEvent` | A village is discovered; its elder arrives, falls or is restored; peace starts or ends; freed villagers return | React |

A mod that lends its own soldiers to a lord's raid, and one that forbids raids during a truce:

```java
NeoForge.EVENT_BUS.addListener((CampaignEvent.Muster e) -> {
    if (e.getStage() == CampaignEvent.Muster.Stage.FIELD) {
        e.getLord().ifPresent(lord -> myRecruits.near(lord).forEach(e::addMember));
    }
});
NeoForge.EVENT_BUS.addListener((CampaignEvent.Declare e) -> {
    if (Truces.active(e.getVillage())) {
        e.cancel(Component.literal("The truce with the illagers holds until the full moon."));
    }
});
```

Example for a guards mod with no compile dependency on Fealty internals:

```java
NeoForge.EVENT_BUS.addListener((CrimeWitnessedEvent e) -> {
    if (e.getSeverity() == Severity.SEVERE) {
        myGuards.alert(e.getPlayer(), e.getPos());
    }
});
```

## New ways to gain or lose rep

Register a `RepSource`, then apply it. Pack makers can retune its amount with a data pack file of the same id in
`fealty/rep_actions/` or `fealty/crimes/`.

```java
public static final DeferredRegister<RepSource> SOURCES = DeferredRegister.create(RepSource.REGISTRY_KEY, MODID);
public static final DeferredHolder<RepSource, RepSource> RESCUED_CAT =
        SOURCES.register("rescued_cat", () -> RepSource.action(4));

FealtyApi.get().applySource(player, village, ResourceLocation.fromNamespaceAndPath(MODID, "rescued_cat"));
```

## New quest types

Register a `QuestType` in the `fealty:quest_type` registry (`FealtyRegistries.QUEST_TYPE_KEY`) with a `MapCodec`
of your `QuestObjective`. Data packs can then use `"objective": {"type": "yourmod:your_type", ...}`. This lives
in the main jar (`com.selluastar.fealty.quest`), not the stable API.

## Tags

`FealtyTags` lists every tag Fealty reads (guards, village members, bandits, property blocks, threat weapons,
elder villages, ...).
