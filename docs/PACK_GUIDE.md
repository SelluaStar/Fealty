# Fealty pack-maker guide

## Villages from other mods, and elders

Every structure the `fealty:village` template matches becomes a village faction. By default that is
`#minecraft:village`, ChoiceTheorem's Overhauled Village (`ctov:*`), and any structure whose id mentions village,
town, castle, city, settlement, burg, hamlet or citadel, except ruins, outposts and the like. Stand in a town and
run `/rep village scan` to see which structures are there and whether they count. To add or drop one without a data
pack, use the server config lists `extra_village_structures` and `excluded_village_structures` (ids, `#tags` or
patterns such as `mymod:*keep*`). Settlements with three or more homes and no structure are found too, and
`/rep village create <radius> <name>` makes a village by hand where nothing is detected.

Only villages whose structure is also in `#fealty:elder_villages` get a trusting elder and a coffer. By default
that tag holds `#minecraft:village`, so every village gets an elder. To limit elders to your castle villages,
override the tag in a data pack:

```json
// data/fealty/tags/worldgen/structure/elder_villages.json
{ "replace": true, "values": ["yourcastlemod:castle_village", "yourcastlemod:keep_town"] }
```

Villages nothing matches are still found by their bell, and their villagers belong to that village. Villagers with
no village nearby belong to `fealty:wanderers`.

The elder spawns indoors the first time a player visits, in the village's main building (see
`village_layouts/` in [DATAPACKS.md](DATAPACKS.md)), with the village coffer beside them, a mailbox outside and a
guard post. The village centre is the bell nearest the structure's town-centre piece, so big towns with a bell in
every square still centre on the main one. Config: `spawn_elders`, `place_coffers`, `village_mailboxes`,
`bell_villages_have_elders`, `village_margin`.

## Guards

Each village keeps a watch of Fealty guards: one per `villagers_per_guard` (4) villagers, between `min_guards` (2)
and `max_guards` (6), plus a sergeant at `sergeant_population` (12) villagers, and `empty_village_guards` (3) for a
castle with no villagers. A fallen guard is replaced after `guard_respawn_days` (1). Set `fealty_guards = false`
to rely on golems and guard mods alone.

Anything in `#fealty:guards` also acts as a guard, and is part of its village's watch: it shows on the Village
Hall's roster (with you, on duty, away or fallen), answers the Lord's Horn (follow, hold, guard, and walk home on
Return), and can demand a fine (golems excepted; pay by using emeralds on any of the village's guards). The default tag holds Fealty's guard, iron golems and, if
installed, Guard Villagers' `guardvillagers:guard`. Add your guard mod's entities to the tag. Fealty gives them its
targeting goals when they spawn, so the guard mod needs no compile dependency. Guards attack Hated and wanted
players, watch Distrusted ones, help Trusted ones, and escort Honored ones. A witnessed crime alerts guards within
32 blocks. A player the village honoured before the crime (`guards_forgive_at_rep`, 61) is only scolded: the crime
costs rep and nothing more. Below that, small crimes get a warning first (Fealty guards demand a fine: `fines`,
`guard_fine_seconds`), and guards attack for severe crimes, a repeat offence, a refused fine, or any crime by a
Distrusted player. Golems and other mods' guards follow the same rules instead of their own anger, though any guard
hits back when struck by someone the village does not honour. Guards of other villages only answer if the
criminal's Renown is low.

## FTB Quests

Two ways to use it:

1. **Advancements** (no extra setup). Every story beat is an advancement or a `fealty:event` criterion, and FTB
   Quests' built-in Advancement task can track them: `fealty:trusted`, `fealty:honored`, `fealty:royal_writ`,
   `fealty:sworn_lord`, `fealty:regicide`, and so on. Use Command rewards with `/rep add @p here 10` to grant rep.
2. **Native task and reward** (needs the `compat_ftbquests` build). Task `fealty:reputation`: faction (`here`,
   `any_village`, `fealty:renown` or an id), minimum tier, minimum rep. Reward `fealty:reputation`: faction and
   amount.

## KubeJS

Needs the `compat_kubejs` build:

```js
// server_scripts/fealty.js
FealtyEvents.repChange(event => {
  if (event.reason == 'fealty:trade') event.amount = event.amount * 2
})
FealtyEvents.crimeWitnessed(event => {
  if (event.player.isCrouching()) event.severity = 'minor'
})
FealtyEvents.quest(event => {
  if (event.status == 'completed') event.player.tell('The village thanks you.')
})
// Fealty is the RepApi binding:
// Fealty.addRep(player, faction, 5, 'kubejs:bonus')
```

## Balancing knobs

- Tier ranges, prices and behaviour: `data/fealty/fealty/tiers/`.
- Every rep amount: `rep_actions/` and `crimes/`.
- Server config (`serverconfig/fealty-server.toml`):
  - rep range; witness radius (16) and guard alert radius (32)
  - Renown share (10%) and start factor (¼, capped at ±25)
  - threat cooldown (one day); negative rep decay (off)
  - staying away: `neglect` (on), `neglect_grace_days` (2), `neglect_first` (10), `neglect_daily` (5),
    `neglect_floor` (0): standing with a village fades once you have been away two days, never below Neutral
  - consequences: `fines`, `word_travels_share` (25%) and `word_travels_radius` (1000), `hated_arrival`,
    `locked_village_chests`
  - quests: `max_active_quests`, `elder_quests_at_once`, `favors` and `favor_chance`
  - mail: `village_mailboxes`, delivery delays
  - heat thresholds and bounty interval
  - tribute and tax table (`tribute_by_tax_level`, `tax_daily_rep`, `gift_chance_by_tax_level`); max lordships per
    player; horn summons
  - bandits: `camp_exclusion_radius` (768), `bandit_raids`, `raid_range` (640), `raid_min_days` and
    `raid_max_days` (3 to 5)
  - outlaw Renown gates
- Client config (`config/fealty-client.toml`): quest tracker mode, position and length, speech bubbles, the rep
  feed, banners, quest markers, the retinue bar, and the dialogue box's typing speed and `text_scale`.

## Lordship

The Royal Writ comes from the rare villager chain. Its holder can swear a village where they are Honored.
Lords run the village from the Village Hall (ask the elder, or open it from the journal):

- tribute gathered every day into the treasury, whether anyone is there or not (more villagers and higher taxes
  mean more), collected in person; a report by letter every `tribute_interval_days`
- a tax setting: None and Light raise the lord's standing a little each day, Heavy and Crushing lower it. Tribute
  per day scales with it (`tribute_by_tax_level`, default 0.1×, 0.4×, 1×, 2×, 3.5×)
- gifts of love: a village that loves its lord may gather a big gift (one good thing in quantity, from
  `fealty:gameplay/gift_of_love`) on any day, likelier the lighter the taxes (`gift_chance_by_tax_level`, default 15%,
  10%, 5%, 1%, 0%), halved for a lord who is only Trusted and half again likelier for one the village adores
- decrees: a weekly feast, and paying to replace a fallen guard at once
- the Lord's Horn, usable anywhere: call guards (they arrive from out of sight), hold, guard an area, or send them
  home

A lord who falls below Honored gets a letter and has `lord_grace_days` (2) to win the village back; after that it
renounces them. In multiplayer, a rival with a Writ who is Honored and better loved
than the current lord can usurp the village.

## Outlaw path

Outlaw gates use Renown (a player's name across villages):

- Black market and thieves guild: Renown at or below −21 (Distrusted).
- Bandit followers: at or below −61 (Hated).

Being Hated by a village builds heat. Enough heat sends bounty hunters; more summons the Tyrant Lord to a castle
village. Killing him drops the Tyrant's Crown, clears heat and frees the village.

Bandit camps are generated far apart, and only one within `camp_exclusion_radius` is manned. Only generated
standards man a camp. A manned camp may raid villages within `raid_range` every few days while a player is there;
`/rep village raid <village>` starts one for testing.

## War: pillager camps and raids

Pillager strongholds come in sizes, and each shows its danger as one to five skulls (its **threat**):

| Kind | Where | Holds | Threat |
|---|---|---|---|
| Scout camp | Grassland, forest, taiga, snowy plains; the commonest | 2-3 pillagers and a captain, one cage | 1 |
| Camp | Plains, savanna, taiga, forest, meadow, snowy | Pillagers, vindicators and a captain behind a palisade, two cages | 2 |
| Outpost | Vanilla's and modded (`#fealty:pillager_outposts`) | Whoever is there | 2 |
| Fort | Plains, meadow, forest, taiga; uncommon | A walled yard with towers, barracks, three cages, an evoker | 3 |
| Castle | Dark forest, taiga, snowy, meadow; rare | Curtain walls, four towers, a keep with dungeon cells, evokers, a witch and a ravager | 4 |

Each stronghold also has a **trait**, the same every time for the same place, that adds a skull: **Veterans**
(iron armour and enchanted arms), **An evoker** or **War beasts** (a ravager). So a scout camp can be small but
nasty, and a castle with a trait is the only five-skull stronghold. Camps keep 6 chunks from villages, and the
scout camps, forts and castles 7 (one structure set, `fealty:pillager_strongholds`, so they never overlap). The
War Banner in each keeps its garrison and fills the cages with captives from the nearest village.

A lord raids them from the **War** tab of the Village Hall: send scouts (they find strongholds within
`war_range`), pick one and raise the warband. The tab shows each stronghold's skulls, trait, rough number of
defenders, cost and rewards. The lord gets a War Map and the raid as a tracked quest; the guards nearby fall in,
and near the stronghold the rest of the watch and a levy of militia (one per `levy_per_villagers` villagers, up to
`max_levy`) catch up. A bar counts the defenders. Victory frees the captives (they turn up at home), razes the
stronghold (no garrison, no illager spawns), adds tribute to the treasury and gives the village days without
bandit raids or pillager patrols. Strongholds left standing raid the villages in reach every `menace_min_days` to
`menace_max_days` days (`pillager_menace`). Elders also offer "Eyes on the Pillagers" (find a stronghold) and "The
Cages" (free captives).

The threat scales the raid:

| | Scales with threat |
|---|---|
| Cost | `raid_cost_emeralds` for 2 skulls, a quarter less or more per skull (9, 12, 15, 18, 21 by default) |
| Warband | `warband_size`, +2 guards at 3 skulls, +4 at 5; the levy cap is half again at 4+ |
| Rewards | The kind's spoils, peace and raze days (scout camp 1/4/6, camp the config's, fort 4/10/14, castle 7/14/20); +1 spoils for a trait |
| The lord | One more roll of `quest_rewards/war_spoils` per skull past the first |
| Fighters | Rep for `raid_stronghold` times threat/2 (at least 1), and Renown equal to the threat |
| Menace | Reach and Raid Omen from the kind (castles 1000 blocks, omen 3); a trait adds an omen level |

Kinds are data: `data/<ns>/fealty/stronghold_kinds/` (see DATAPACKS.md), so a pack can retune them or give a
modded fort or castle a kind of its own.

Vanilla and modded pillager outposts are targets too (add yours to `#fealty:pillager_outposts`); their defenders
are the mobs in `#fealty:stronghold_defenders` on the outpost's ground when the battle starts, so illager mods whose
mobs are in `#minecraft:raiders` work as they are. Commands: `/rep war list|scout <village>|declare <village>|win|cancel|menace <village>`.

## Compatibility notes

- **Oh The Biomes We've Gone:** its six village kinds (Skyris, Salem, Red Rock, Pumpkin Patch, Forgotten, Swamp)
  are in `#minecraft:village`, so they are villages with elders. The `biomeswevegone` layout puts the elder in the
  temple or largest house and never in streets, pens, farms or markets. Bandit camps and the hidden hamlet use the
  `#minecraft:is_forest`, `#minecraft:is_taiga`, `#minecraft:is_savanna` and `#c:is_plains` tags, which BWG's
  biomes are in.
- **Epic Structures: Villages:** it rebuilds the five vanilla villages under vanilla's piece names, so the names no
  longer say what a building is (a "small house" can be a three-storey hall). With the mod loaded, the
  `epic_villages` layout takes over: the elder lives in the town-centre complex by the main bell, and Fealty
  judges other buildings by their beds rather than their names. If you use the **data pack** version instead of
  the mod, copy `data/fealty/fealty/village_layouts/epic_villages.json` into your pack without the `mods` line.
- Villages visited before a structure mod or layout was added keep their elder where they are;
  `/rep village rehome <village>` moves the elder to the best building now.
- **Tectonic and Lithosphere:** both replace the overworld terrain, so use one of them, not both. Fealty does not
  depend on terrain height: elder spots, bandit spawns, quest sites and guard arrivals are all found column by
  column on the real ground, and bandit camps and the hidden hamlet only generate on dry, fairly flat land, so they
  are rarer in very mountainous worlds. Tectonic's taller worlds work as they are.

- MineColonies overlaps heavily with a village reputation system and is best left out of the pack.
- Mods that rewrite villager AI or professions can clash with the price and threat logic. Test them before
  release.
- Boss mods can link to rep through the API (spawn on a Hated event, gate drops on a tier).
