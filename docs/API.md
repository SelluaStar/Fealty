# Fealty API

The API lives in `com.selluastar.fealty.api` and ships as a separate jar
(`fealty-<version>+api.<api_version>-api.jar`). Compile against that jar only, and declare Fealty as an
optional dependency. The API follows semantic versioning (`FealtyApi.API_VERSION`).

```groovy
dependencies {
    compileOnly files("libs/fealty-0.1.0+api.1.3.0-api.jar")
}
```

Fealty works on its own; nothing here needs another mod. A mod that uses these extension points should check
`FealtyApi.isAvailable()` (or `ModList.get().isLoaded("fealty")`) before calling into the API, and register its
`DeferredRegister`s on Fealty's registry keys only when Fealty is installed.

A worked example data pack (a rumour, a conversation and a single-giver chain) is in
[examples/single_giver_chain](examples/single_giver_chain/).

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
| `getFlag`, `hasFlag`, `setFlag`, `clearFlag` (1.3.0) | A player's story flags |
| `assignRole`, `clearRole`, `hasRole` (1.3.0) | Make a villager a chain's special villager, with a title in its name |
| `findVillager(server, village, predicate)` (1.3.0) | A loaded villager of a village, nearest the centre first |
| `getVillage`, `findVillage(server, origin, min, max, predicate)` (1.3.0) | Villages Fealty knows of (or finds) as `VillageInfo` |
| `startChain(player, chain, village)`, `getChainStage` (1.3.0) | Start a quest chain as a rumour would; read a player's stage |

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

### Stories (API 1.3.0)

| Event | Fires when | You can |
|---|---|---|
| `RumourEvent.Heard` | A rumour from `fealty/rumours/` is about to be told (`getTeller()`, `getRumour()`, `getChain()`) | Cancel it, or edit `getLines()` |
| `DialogueEvent.Open` | A player starts talking to an NPC | Cancel it to keep Fealty's dialogue box shut |
| `DialogueEvent.Choose` | The player picks a reply (`getOption()`) | Cancel it (the box is drawn again) |
| `DialogueBuildEvent` | Fealty draws a villager's, quest giver's or guard's usual dialogue | `addLine`, `addOption` (see below) |
| `ChainStageEvent.Start` | A quest chain is about to start for a player (`getOrigin()`) | Cancel it |
| `ChainStageEvent.Advance` | A step is handed in (`getStep()`, `TRIAL`) or the stage moves (`NO_STEP`) | React |
| `ChainStageEvent.Complete` | A player completes a chain (`getTimesCompleted()`) | React |

`RumourEvent`'s base now carries the teller as an `Entity` (`getTeller()`); `Gather` and `Told` keep `getVillager()`.

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

`com.selluastar.fealty.api.quest` (since 1.3.0; in the main jar before) has what a quest type needs:

- `QuestType<T extends QuestObjective>(MapCodec<T> codec)`, registered on `QuestType.REGISTRY_KEY` (`fealty:quest_type`).
- `QuestObjective`: write `type()`, `start(ctx)`, `describe(ctx)` and `preview()`; everything else (`canTurnIn`,
  `onTurnIn` with `TurnIn.COMPLETE/PROGRESS/MISSING`, `completesOnReady`, `cleanup`, `tick`, `onKill`,
  `onTargetLost`, `onBlockPlaced`, `onBlockBroken`, `markedEntities`, `trackedPosition`, `dialogueReplies` and
  `onDialogue`, `onUseItemOnVillager`) has a default.
- `QuestContext`: the accepted quest as the objective sees it (`player()`, `questId()`, `giver()`, `faction()`,
  `state()` with `dirty()`, `isReady()` and `setReady()`, `villageId()`, `anchor()`, `dimension()`, ...).

```java
public static final DeferredRegister<QuestType<?>> TYPES = DeferredRegister.create(QuestType.REGISTRY_KEY, MODID);
public static final DeferredHolder<QuestType<?>, QuestType<RingBell>> RING_BELL =
        TYPES.register("ring_bell", () -> new QuestType<>(RingBell.CODEC));

public record RingBell(int times) implements QuestObjective {
    public static final MapCodec<RingBell> CODEC = Codec.INT.fieldOf("times").xmap(RingBell::new, RingBell::times);
    public QuestType<?> type() { return RING_BELL.get(); }
    public boolean start(QuestContext ctx) { return true; }
    public List<Component> describe(QuestContext ctx) { return List.of(Component.literal(ctx.getInt("rung") + "/" + times)); }
    public List<Component> preview() { return List.of(Component.literal("Ring the bell " + times + " times")); }
}
```

Data packs then use `"objective": {"type": "yourmod:ring_bell", "times": 3}` in `fealty/rep_quests/`.

## Quest givers

Make your own NPCs quest givers with `QuestGiver` (`fealty:quest_giver`). Talking to an entity it `appliesTo` opens
Fealty's dialogue box with the way to its quest board; Fealty accepts, hands in and abandons the board's quests itself.

```java
public static final DeferredRegister<QuestGiver> GIVERS = DeferredRegister.create(QuestGiver.REGISTRY_KEY, MODID);
public static final DeferredHolder<QuestGiver, QuestGiver> HERMIT = GIVERS.register("hermit", () -> new QuestGiver() {
    public boolean appliesTo(Entity entity) { return entity.getType() == MyEntities.HERMIT.get(); }
    public Board board(ServerPlayer player, Entity npc) {
        ResourceLocation key = ResourceLocation.fromNamespaceAndPath(MODID, "hermit/" + npc.getUUID());
        return Board.of(Component.translatable("yourmod.hermit.greet"), key, FealtyApi.id("wanderers"),
                List.of(ResourceLocation.fromNamespaceAndPath(MODID, "hermit_herbs")));
    }
});
```

## Chain kinds

A `fealty/quest_chains/` file's `kind` names a `ChainHandler` in `fealty:chain_kind` (`ChainHandler.REGISTRY_KEY`).
Fealty registers `fealty:rare_villager`, `fealty:guild` and `fealty:story`. Fealty does what every kind shares: it
lays out the run when the chain starts, hands the steps to their named villagers or to the single giver, marks steps
done and pays their `rewards`, and gives `final_reward` when the chain completes. A handler adds the rest:

| Method | Called when | Default |
|---|---|---|
| `start(ctx)` | The chain starts (run laid out, stage 1); return false to refuse | true |
| `onStepComplete(ctx, step)` | A step (or, as -1, the trial) was handed in; return true if the chain is now complete | complete when every step is done |
| `onChainComplete(ctx)` | The chain completed (`final_reward` given, stage 5) | nothing |
| `canAdvance(ctx, stage)` | A rumour would move the running chain to a later stage | any stage between the current one and 5 |

`ChainContext` gives the player, chain, kind, origin village, stage (`setStage` fires `ChainStageEvent.Advance`), the
run's steps and which are done, and `complete()`.

```java
public static final DeferredRegister<ChainHandler> KINDS = DeferredRegister.create(ChainHandler.REGISTRY_KEY, MODID);
public static final DeferredHolder<ChainHandler, ChainHandler> HEIST = KINDS.register("heist", () -> new ChainHandler() {
    public void onChainComplete(ChainContext ctx) {
        ctx.player().sendSystemMessage(Component.translatable("yourmod.heist.done"));
    }
});
```

A chain naming a kind no installed mod registers simply never starts.

## Dialogue conditions and effects

Rumours, conversations and chain steps use `DialogueCondition`s and `DialogueEffect`s (`com.selluastar.fealty.api.dialogue`).
In JSON each is an object whose one key is its type: `{"has_item": "minecraft:paper"}`, `{"yourmod:moon_phase": 4}`.
Register types with a `Codec` for the value under the key:

```java
public static final DeferredRegister<DialogueConditionType<?>> CONDITIONS =
        DeferredRegister.create(DialogueConditionType.REGISTRY_KEY, MODID);
public static final DeferredHolder<DialogueConditionType<?>, DialogueConditionType<MoonPhase>> MOON_PHASE =
        CONDITIONS.register("moon_phase", () -> new DialogueConditionType<>(MoonPhase.CODEC));

public record MoonPhase(int phase) implements DialogueCondition {
    public static final Codec<MoonPhase> CODEC = Codec.INT.xmap(MoonPhase::new, MoonPhase::phase);
    public DialogueConditionType<?> type() { return MOON_PHASE.get(); }
    public boolean test(DialogueContext ctx) { return ctx.player().level().getMoonPhase() == phase; }
}
```

`DialogueContext` is the player and (if any) the NPC. Effects work the same way with `DialogueEffectType` and
`apply(ctx)`. `DialogueCondition.CODEC` and `DialogueEffect.CODEC` read and write any registered type, so your own
data can use them too. The built-ins are listed in [DATAPACKS.md](DATAPACKS.md#conditions-and-effects).

## Adding to the dialogue box

`DialogueBuildEvent` fires each time Fealty draws a villager's, quest giver's or guard's usual dialogue. Add lines to
what the NPC says, and replies of your own. A reply id must be `<modid>:<name>` (64 characters at most); when the
player picks it, Fealty calls the handler you gave with it and does what the returned `DialogueReply` says (`say` an
answer and keep the box open, `refresh`, `close`, or `end` when you open a screen of your own).

```java
NeoForge.EVENT_BUS.addListener((DialogueBuildEvent e) -> {
    if (e.getNpc() instanceof Villager villager && MyStory.knowsSecret(villager)) {
        e.addLine(Component.translatable("yourmod.secret.hint"));
        e.addOption("yourmod:ask_secret", Component.translatable("yourmod.secret.ask"),
                (player, npc, option) -> DialogueReply.say(Component.translatable("yourmod.secret.answer")));
    }
});
```

The server decides what is shown and checks every pick against what it showed; the client only draws the box.

## Special villagers and villages

```java
RepApi api = FealtyApi.get();
// The nearest village 300 to 1500 blocks away that has an elder, and one of its villagers.
api.findVillage(server, GlobalPos.of(level.dimension(), player.blockPosition()), 300, 1500, VillageInfo::hasElder)
        .flatMap(village -> api.findVillager(server, village.id(), v -> !v.isBaby()))
        .ifPresent(v -> api.assignRole(v, ResourceLocation.fromNamespaceAndPath(MODID, "tale"), "stranger",
                Component.translatable("yourmod.role.stranger")));
```

`assignRole` names the villager with its title ("Ann the Stranger") and keeps its trade; the role stays until
`clearRole`. If the chain is a single-giver chain with that `giver_role`, the villager is its giver in its village.
Flags (`getFlag`, `setFlag`, `clearFlag`) are kept on the player through death and relogging; data uses them through
the `flag`, `set_flag` and `clear_flag` conditions and effects.

## Tags

`FealtyTags` lists every tag Fealty reads (guards, village members, bandits, property blocks, threat weapons,
elder villages, ...).
