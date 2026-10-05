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
