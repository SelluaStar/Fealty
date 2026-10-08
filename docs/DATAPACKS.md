# Fealty data packs

Everything that balances Fealty lives in data packs, so packs can retune it without rebuilding the mod. Files go
under `data/<namespace>/fealty/<folder>/`. Fealty's own defaults are in `data/fealty/fealty/...`; override one by
shipping a file with the same id, or add new ones under your own namespace. All folders reload with `/reload`.

| Folder | Defines |
|---|---|
| `tiers/` | Tier names, rep ranges, price multipliers, guard and trade behaviour |
| `factions/` | Village templates and static factions (bandits, the thieves guild, ...) |
| `village_layouts/` | Which building in each kind of village the elder lives in |
| `rep_actions/` | Amounts for ways to gain rep (gifts, trades, raids, ...) |
| `crimes/` | Offences: amount, severity, heat |
| `rep_quests/` | Quests: elder pools by tier, and villagers' favors |
| `quest_chains/` | The rare villager chain, the thieves guild, and stories of your own |
| `rumours/` | Rumours villagers and elders tell, which can start or move on a chain |
| `conversations/` | Branching conversations in the dialogue box |
| `dialogue/` | What NPCs say, in the dialogue box and in speech bubbles |
| `village_trades/` | Trades villages keep for Trusted/Honored players |
| `black_market/` | What black marketeers may stock |

## tiers/

One file per tier. Ranges should cover the configured rep range without gaps. Tiers are ordered by `min`.

```json
{
  "name": {"translate": "fealty.tier.trusted"},
  "min": 21, "max": 60,
  "price_multiplier": 0.85,
  "color": "#4CAF50",
  "guard_stance": "assist",
  "trade_policy": "standard",
  "locked_trade_level": 0,
  "villagers_hide": false,
  "villager_gifts": false
}
```

- `guard_stance`:
  - `attack_on_sight`
  - `watch`: follow and warn; attack after any crime
  - `ignore`
  - `assist`: help in fights and greet
  - `escort`: assist, plus escort on request
- `trade_policy`: `standard` or `refuse_unless_threatened`.
- `locked_trade_level`: villager trades from this level up are shown out of stock (5 locks master trades).
- `villagers_hide`: villagers near the player run home as if a bell rang.
- `villager_gifts`: villagers give the player gifts, once a day each.

The ids `fealty:hated`, `distrusted`, `neutral`, `trusted` and `honored` always exist. Lordship, the Keeper and
guard logic refer to them.

## factions/

```json
{
  "kind": "village",
  "name": {"translate": "fealty.faction.village"},
  "structures": ["#minecraft:village", "ctov:*", "*:*castle*", "*:*town*"],
  "exclude": ["minecraft:ancient_city", "*:*ruin*", "*:*outpost*"],
  "elder": true,
  "renown_share": 0.1,
  "renown_start_factor": 0.25,
  "priority": 0,
  "members": "#fealty:village_members"
}
```

- `structures` is one entry or a list. Each entry is a structure tag (`#minecraft:village`), a structure id
  (`mymod:keep`), or a pattern where `*` matches anything (`ctov:*`, `*:*castle*`). `exclude` takes the same
  entries and wins over `structures`. The server config adds `extra_village_structures` and
  `excluded_village_structures` on top, so packs need no data pack for a quick fix.
- `kind: village` is a template. Every structure start in `structures` becomes its own faction, with id
  `village:<dimension namespace>/<dimension path>/<chunkX>_<chunkZ>`. Villages found only by their bell use
  `.../bell_<x>_<y>_<z>`, and settlements of three or more homes with no structure or bell are found too. When
  several templates match a structure, the highest `priority` wins.
- `elder`: whether villages of this template get a trusting elder. The structure must also be in
  `#fealty:elder_villages`.
- `renown_share`: share of each rep change that also moves Renown. `renown_start_factor`: a new faction starts at
  this fraction of the player's Renown.
- `kind: static` is a single faction under the file's id (e.g. `fealty:bandits`).

## village_layouts/

Which building the elder lives in. Buildings are matched by the template they were built from (the jigsaw piece
name, such as `minecraft:village/plains/houses/plains_big_house_1`), with `*` matching anything. For each village
the highest-`priority` layout whose `structures` match is used; layouts whose `mods` are not all loaded are skipped.

```json
{
  "structures": ["biomeswevegone:*"],
  "exclude": [],
  "mods": [],
  "priority": 10,
  "reach": 64,
  "elder": ["*temple*", "*large_house*", "*library*", "*house*"],
  "avoid": ["*/streets/*", "*animal_pen*", "*farm*"]
}
```

- `elder`: buildings for the elder, best first. Unlisted buildings can still be picked.
- `avoid`: buildings that are never the elder's home: roads, walls, farms, pens, plazas.
- `reach`: how far (blocks) from the village centre the building may be.
- Beds inside a building, its size and how close it is to the centre count as well, so a town whose building names
  mean nothing (or that has no layout at all) still gets an indoor home near the middle. The spot itself must be
  indoors, standable, off the paths and near beds where possible.

Fealty ships `vanilla`, `biomeswevegone` (Oh The Biomes We've Gone), `epic_villages` (Epic Structures: Villages,
only when its mod `mr_epic_structuresvillages` is loaded) and a catch-all `default` at priority -100 for every
other town and castle mod. Elders already placed keep their home; `/rep village rehome <village>` moves one to the
best building the village has now (stand in the village so it is loaded).

## stronghold_kinds/

`data/<ns>/fealty/stronghold_kinds/<name>.json` sorts pillager strongholds into kinds. The kind that matches a
stronghold's structure (highest `priority` first) decides how dangerous it is, who holds it and what razing it
wins. Fealty ships `scout_camp`, `camp`, `outpost`, `fort` and `castle`.

```json
{
  "structures": ["fealty:pillager_fort"],
  "priority": 0,
  "name": "fealty.stronghold.fort",
  "threat": 3,
  "garrison": [
    {"entity": "minecraft:pillager", "min": 5, "max": 7},
    {"entity": "minecraft:vindicator", "min": 3, "max": 4},
    {"entity": "minecraft:evoker", "min": 1, "max": 1}
  ],
  "captain": "minecraft:pillager",
  "captives_min": 4, "captives_max": 6,
  "spoils": 4, "peace_days": 10, "raze_days": 14,
  "menace_range": 800, "menace_omen": 2,
  "traits": {"none": 4, "veterans": 3, "evoker": 1, "beasts": 2},
  "radius": 24
}
```

| Field | Meaning |
|---|---|
| `structures` | Structure ids and `#tags` it covers |
| `priority` | Higher wins when two kinds match (default 0; `outpost` is -10 so a pack's kind for a modded outpost wins) |
| `name` | Translation key, given the stronghold's name, such as `"%s Fort"` |
| `threat` | Skulls before its trait, 1-5 (default 2) |
| `garrison`, `captain` | Who a War Banner keeps there (strongholds without a banner, like outposts, are held by whoever is there) |
| `captives_min`, `captives_max` | Captives in its cages |
| `spoils`, `peace_days`, `raze_days`, `menace_range` | Override the war config (-1 or left out: use the config) |
| `menace_omen` | Raid Omen level of the raids it sends (1-5, default 1) |
| `traits` | Weights for `none`, `veterans`, `evoker` and `beasts` (default only `none`) |
| `radius` | How far its ground reaches from its heart (default 20) |

To make a modded structure raidable, add it to `#fealty:pillager_outposts` (or `#fealty:pillager_camps` if it has a
War Banner) and give it a kind.

## rep_actions/ and crimes/

The file id must match a registered rep source (see `RepSources` in the API). Mods can register more.

```json
// rep_actions/trade.json: +1 for every 5 trades, at most +3 a day
{ "amount": 1, "every": 5, "daily_cap": 3 }

// crimes/steal.json
{ "amount": -15, "severity": "moderate", "heat": 2, "alerts_guards": true }
```

| Field | Meaning |
|---|---|
| `amount` | Rep change |
| `every` | Apply once per N occurrences |
| `daily_cap` | Most rep this source can move in a day |
| `severity` | `minor` (half penalty, guards warn first), `moderate` (guards warn first), or `severe` (guards attack at once) |
| `requires_witness` | Crimes default to true |
| `alerts_guards` | Default true |
| `can_raise_negative` | Whether this source may raise rep that is below zero. By design only quests can. |
| `heat` | Wanted-level heat the crime adds |

Good deeds (`rep_actions/`):

| Source | Default |
|---|---|
| `quest` | from the quest, +5 to +15 |
| `favor` | from the favor, at most +6 a day per village |
| `trade` | +1 per 5 trades, at most +3 a day |
| `gift` | +2 |
| `courier` | +3 with the village that receives the letter |
| `defend_raid` | +20 for winning a vanilla raid |
| `defend_bandit_raid` | +15 for standing with a village through a bandit raid |
| `cure_villager` | +8 |
| `kill_monster` | +1 for a monster killed in the village (at most +5 a day) |
| `defend_villager` | +3 for killing something attacking a villager or guard |
| `kill_bandit`, `kill_raider` | +2 and +1, near a village |
| `heal_villager` | +2 for healing a hurt villager with a potion |
| `ring_bell` | +2 for ringing the bell while monsters, bandits or raiders are near |
| `repair_golem` | +1 for mending a village golem with iron |
| `light_village` | +1 per 8 lights placed where there was no light |
| `place_bed` | +2 for a new bed |
| `tend_crops` | +1 per 8 crops planted or bonemealed |
| `donate` | +1 per 8 emeralds put in the coffer |
| `feast` | +3 for the lord's feast |
| `fine` | what a paid fine wins back (amount comes from the fine; `daily_cap` limits it) |
| `liberation` | +25 for freeing a village from the Tyrant Lord |
| `raid_stronghold` | +8 for fighting in a lord's raid that razes a pillager stronghold |
| `free_captive` | +4 with the captive's home village for freeing them |
| `failed_campaign` | −5 for the lord when a raid is lost |
| `neglect` | standing that fades while a player stays away (amounts are server config) |

Crimes (`crimes/`):

| Source | Default |
|---|---|
| `break_block` | −5 per village block, at most −40 a day |
| `break_fixture` | −15 for the bell, coffer or mailbox |
| `steal` | −15, judged when the first item leaves the container |
| `vault_raid` | −20 for taking from the coffer |
| `pickpocket` | −8 |
| `lockpicking` | −5 when someone hears a missed pick |
| `threaten` | −3 |
| `hit_villager`, `hit_guard` | −10 |
| `hit_child` | −20, severe |
| `kill_villager`, `kill_child` | −40 and −60 |
| `kill_guard`, `kill_elder` | −50 and −60 |
| `kill_member` | −20 for other village members |
| `kill_animal`, `kill_cat` | −5 and −8 for the village's own animals |
| `kill_trader` | −15 with the nearest village, and with `fealty:wanderers` |
| `trample_crops` | −1 |
| `harvest_crops` | −2 per 8 crops taken |
| `arson` | −15, severe |
| `explosion` | −25, severe, no witness needed |
| `trespass` | −3 a minute in villagers' homes at night, after a warning |
| `sleep_in_bed` | −2 for sleeping in a villager's bed below Trusted |

`word_travels` is the source used when part of a witnessed crime's cost reaches nearby villages the next day
(server config `word_travels_share` and `word_travels_radius`).

## rep_quests/

```json
{
  "title": {"translate": "quest.mypack.bread"},
  "description": {"translate": "quest.mypack.bread.desc"},
  "pool": "fealty:redemption",
  "tiers": { "fealty:distrusted": 10, "fealty:neutral": 10 },
  "difficulty": 1,
  "reward": { "rep": 6, "renown": 0, "loot_table": "fealty:quest_rewards/common", "items": [], "experience": 0 },
  "time_limit": 0,
  "fail_rep": 0,
  "objective": { "type": "fealty:fetch", "items": [ { "ingredient": {"item": "minecraft:bread"}, "count": 24 } ] }
}
```

- `pool`:
  - `fealty:redemption`: elder quests.
  - `fealty:favor`: small favors any villager may offer once a day. `professions` limits who offers it
    (`minecraft:nitwit` and `minecraft:none` work; empty means anyone). Favors pay `favor` rep, capped daily.
  - `fealty:restore`: offered when a Broken village is near.
  - `fealty:none`: never offered; used by chains and by the lord's raid (`fealty:war/raid_stronghold`).
- `tiers`: maps tiers to weights. Elders offer up to 3 quests a day, weighted by your tier, and don't repeat one
  until the pool cycles. Give Hated weights only to hard, costly quests.
- `time_limit`: in ticks. `fail_rep` is applied when the quest fails or is abandoned; otherwise abandoning costs
  the `abandon_quest` action.

Objective types:

| Type | Fields | Notes |
|---|---|---|
| `fealty:fetch` | `items: [{ingredient, count}]` | Bring items to the giver |
| `fealty:defend_raid` | `omen_level` | Starts a vanilla raid at the village; win it |
| `fealty:clear_camp` | `give_map`, `search_radius` | Kill the nearest bandit camp's captain |
| `fealty:restock` | `count` | Place workstations (`#fealty:workstations`) in the village |
| `fealty:rebuild` | `count`, `radius` | Part of a building turns to rubble; rebuild it |
| `fealty:courier` | `recipient` (`elder`, `person` or `mailbox`), `profession` | Carry a sealed letter to another village: to its elder, to a villager there (named when you arrive; they take it from your hand), or to its mailbox |
| `fealty:kill` | `targets` (id, `#tag` or list), `count`, `near_village`, `night_only`, `label` | Defeat creatures |
| `fealty:explore` | `structure` (tag), `radius`, `search`, `give_map`, `label` | Find a structure and come back |
| `fealty:talk_to` | `profession`, `message`, `reply` | Carry word to another villager of the village and report back |
| `fealty:retrieve` | `item_name`, `item`, `site` (`cache`, `cart` or `stash`), `min_distance`, `max_distance`, `give_map` | Something lost out in the wilds: a site appears when you near it; bring the item back |
| `fealty:hunt` | `targets: [{entity, name?, health_multiplier, damage_bonus, armor_bonus, equipment}]`, `min_distance`, `max_distance` | A named elite appears when you near its lair |
| `fealty:restore_elder` | `items` | Two stages: hand in items for an Elder's Mantle, then use it on a villager of the Broken village |
| `fealty:steal` | `items`, `unseen` | Thieves guild |
| `fealty:pickpocket` | `count` | Thieves guild |
| `fealty:vault_raid` | `count` | Thieves guild: empty coffers unseen |

Objective types for the war (see also `fealty:explore` with `"structure": "#fealty:pillager_strongholds"`):

- `fealty:raid_stronghold`: the lord's raid, started from the Village Hall. No fields; the quest
  `fealty:war/raid_stronghold` sets its reward (default 15 rep, 5 Renown, `quest_rewards/war_spoils`).
- `fealty:free_captives` `{ "count": 2, "search_radius": 50 }`: free captives held in the nearest pillager camp, fort or castle
  (comes with a map).

## quest_chains/

```json
{
  "kind": "rare_villager",
  "start_tier": "fealty:trusted",
  "steps_count": 3,
  "step_pool": [
    {
      "role": "smith",
      "professions": ["minecraft:armorer", "minecraft:weaponsmith", "minecraft:toolsmith"],
      "variants": ["fealty:chain/smith_ore", "fealty:chain/smith_forge", "fealty:chain/smith_die"]
    },
    {"role": "fool", "professions": ["minecraft:nitwit"], "variants": ["fealty:chain/fool_riddle", "fealty:chain/fool_shiny"]},
    {"role": "drifter", "professions": ["minecraft:none"], "variants": ["fealty:chain/drifter_boots"]}
  ],
  "steps_reward": {"id": "fealty:signet_of_the_old_crown"},
  "map_structure": "#fealty:hidden_hamlets",
  "trial_quests": ["fealty:chain/keeper_trial", "fealty:chain/keeper_trial_shard"],
  "trial_reward": {"id": "fealty:keepers_charter"},
  "final_reward": {"id": "fealty:royal_writ"},
  "repeatable": true
}
```

The `fealty:rare_villager` chain:

1. A Trusted player asks the elder about rumours.
2. The run picks `steps_count` steps from `step_pool`, preferring roles the village has people for (each role
   needs a different villager), and one of each step's `variants`. A fixed `steps` list (same format) can be used
   instead of a pool; a step may give a single `quest` instead of `variants`.
3. Each role goes to a villager of that village: one of the listed `professions` (`minecraft:nitwit` and
   `minecraft:none` work too), else a jobless villager who takes up the trade, else anyone. The villager gets a
   name and a title (`fealty.chain.role.<role>`), and their profession is locked so they keep it. Ask them for
   work in the dialogue box; their greeting is `fealty.chain.villager.greet.<role>`.
4. If a step's quest cannot start (no structure of its kind nearby, say), another of its variants is set.
5. When every step is done, the player gets `steps_reward` and a map to `map_structure`, and the villagers'
   titles go once no other player's run needs them.
6. The Keeper there checks that the player is still at `start_tier` with the origin village, and sets one of the
   `trial_quests` (`trial_quest` takes a single one). A failed trial is replaced by another. Completing it
   rewards `trial_reward`.
7. The Keeper combines `steps_reward` and `trial_reward` into `final_reward`.

Named villagers keep their role through being zombified and cured. If one dies, any villager of the village
can be asked to take their place.

The `fealty:thieves_guild` chain (`kind: guild`) is handed out one step at a time by the guild fence. Each step
can carry its own `rewards`.

### Stories, kinds and single givers

`kind` names a chain kind (`fealty:chain_kind`); a name without a namespace is Fealty's. Fealty has
`rare_villager`, `guild` and `story`; other mods can register more. A `story` chain starts from a rumour (see
`rumours/`), a conversation's `start_chain` effect, or another mod through the API, and is complete when its last
step is handed in: `steps_reward` (if any) and then `final_reward` are given, and its villagers lose their titles.

```json
{
  "kind": "story",
  "start_tier": "fealty:neutral",
  "repeatable": false,
  "giver": "single",
  "giver_role": "stranger",
  "giver_title": "example.role.stranger",
  "steps": [
    {"quest": "example:stranger/firewood", "unlock": {"flag": "example:met_stranger"}, "unlock_hint": "example.hint.show_letter",
     "text": "example.step.firewood"},
    {"quest": "example:stranger/lantern"},
    {"quest": "example:stranger/night_watch", "unlock": {"time_of_day": "night"}, "rewards": [{"id": "minecraft:emerald", "count": 4}]}
  ],
  "final_reward": {"id": "minecraft:compass"}
}
```

| Field | Default | Meaning |
|---|---|---|
| `giver` | `per_step` | `per_step`: a named villager for each step's role (as the rare villager chain). `single`: one villager gives every step, in order. |
| `giver_role` | `giver` | The single giver's role. A villager given this role by another mod (API `assignRole`) in the starting village is used; else the villager nearest the village centre takes it. |
| `giver_title` | `fealty.chain.role.<giver_role>` | The title shown with the giver's name ("Ann the Stranger"). A string is a translation key. |
| step `unlock` | none | A condition (see below) the step waits for; the giver says `unlock_hint` until it passes. |
| step `text` | the role's greeting | What the giver says when offering the step (a translation key or a text component). |

A `steps` list keeps its order; a `step_pool` with a single giver picks `steps_count` steps at random. Fixed steps of a
single-giver chain may share a role. `steps_reward` is given when the last step of a run is handed in, for every kind.

## rumours/

Rumours villagers and elders tell. `data/<ns>/fealty/rumours/<id>.json`:

```json
{
  "chain": "example:stranger_tale",
  "stage": 1,
  "min_tier": "fealty:neutral",
  "weight": 5,
  "told_by": "villagers",
  "professions": [],
  "lines": ["example.rumour.stranger.0", "example.rumour.stranger.1"],
  "requires": {"dimension": "minecraft:overworld"},
  "grants": [{"give_item": "minecraft:paper"}]
}
```

| Field | Default | Meaning |
|---|---|---|
| `chain`, `stage` | none, 1 | The chain stage the rumour tells of. Stage 1 is told while the chain can start for the player in the teller's village (its kind is registered, it is not running, it is repeatable if done, the player meets its `start_tier`), and hearing it starts the chain there. A later stage (2 to 4) is told while a running chain is one stage short of it, and moves it on, if its kind allows that (`story` does; the rare villager chain and the guild do not). |
| `min_tier` | `fealty:neutral` | The player's least standing with the teller's village (or faction). |
| `weight` | 1 | How likely against the other rumours the teller could tell. |
| `told_by` | `any` | `villagers`, `elders` or `any`. |
| `professions` | any | Villager professions that tell it (elders ignore this). |
| `lines` | none | What the teller says: one of them, picked at random. Translation keys (with the player's name as `%1$s`, the teller's as `%2$s`) or text components. |
| `requires` | none | A condition (see below). |
| `grants` | none | Effects (see below) applied when it is heard. |

A rumour with no `chain` is told to each player once.

**Villagers.** The first time each day a player talks to a villager, the villager rolls to tell a rumour it could
tell; a success adds the line to its greeting. The chance is `rumour_chance` (0.03), plus `pity_step` (0.01) for each
conversation in a row that had a rumour to tell but missed, up to `pity_cap` (0.12); after `guarantee_after` (30) such
misses the next one is certain. A conversation with nothing to tell does not count. All four are in the server
config's `rumours` section; `rumour_chance = 0` turns villagers' rumours off.

**Elders.** The elder's "Ask about rumours" draws from the same pool (`told_by` `elders` or `any`). Fealty's own
`fealty:rare_villager` rumour (elders only, no lines) is how the rare villager chain starts, exactly as before; with
nothing in the pool, the button shows the rare chain's progress or lock as it always did.

## conversations/

Branching conversations. `data/<ns>/fealty/conversations/<id>.json`:

```json
{
  "id": "example:stranger/first_meeting",
  "speaker": "example:stranger",
  "if": {"not": {"flag": "example:met_stranger"}},
  "start": "greet",
  "nodes": {
    "greet": {
      "text": "example.dialogue.greet",
      "replies": [
        {"label": "example.reply.show_letter", "if": {"has_item": "minecraft:paper"}, "hint": "example.hint.need_letter", "goto": "letter"},
        {"label": "example.reply.bye", "end": true}
      ]
    },
    "letter": {
      "text": "example.dialogue.letter",
      "effects": [{"take_item": "minecraft:paper"}, {"set_flag": "example:met_stranger"}],
      "replies": [{"label": "example.reply.ready", "goto": "greet"}]
    }
  }
}
```

- `speaker`: who has the conversation. A chain role (`<chain namespace>:<role>`, so a chain `example:stranger_tale`
  with `giver_role` `stranger` is `example:stranger`, or the role's own full name if it has one), an entity type
  (`fealty:village_elder`, another mod's NPC) or a villager profession (`minecraft:librarian`).
- Without a `label`, the conversation takes over when the player starts talking to the speaker and `if` passes (the
  one with the highest `priority`, then by id). With a `label`, it is offered as a reply among the speaker's usual
  ones while `if` passes. Use a flag in `if` to play something once.
- `nodes`: what the speaker says (`text`) and the player's `replies` (at most 6). A node's `effects` happen when it is
  reached; a reply can have its own `effects` too, applied when it is picked.
- A reply whose `if` fails is shown greyed out with its `hint`. `goto` moves to another node; `end` (or no `goto`)
  ends the conversation and returns to the speaker's usual dialogue. `icon` picks the reply's icon (`talk`).
- `id` is optional; the file's path is the conversation's id. Texts are translation keys (the player's name is
  `%1$s`, the speaker's `%2$s`) or text components.

## Conditions and effects

Rumours (`requires`, `grants`), conversations (`if`, `effects`) and chain steps (`unlock`) share these. Each is an
object whose one key is its type; types without a namespace are Fealty's. Other mods register more in
`fealty:dialogue_condition` and `fealty:dialogue_effect`.

| Condition | Value | Passes when |
|---|---|---|
| `has_item` | item id, or `{"item", "count"}` | the player carries that many |
| `tier_at_least` | tier id, or `{"tier", "faction"}` | the player's standing with the NPC's faction (or the village they stand in, or `faction`) is at least the tier |
| `flag` | flag id, or `{"id", "min", "max"}` | the player's flag is set (not 0), or within the range |
| `advancement` | advancement id | the player has it |
| `dimension` | dimension id | the player is there |
| `quest_stage` | `{"chain", "stage"}` or `"min"`/`"max"`, `"steps_done"` | the player's stage in the chain matches (0 before it starts, 5 once complete) |
| `time_of_day` | `"day"`, `"night"` or `{"min", "max"}` (ticks, may wrap) | the time of day is in range |
| `all`, `any` | list of conditions | every one / at least one passes |
| `not` | a condition | it fails |

| Effect | Value | Does |
|---|---|---|
| `take_item` | item id, or `{"item", "count"}` | takes up to that many |
| `give_item` | item id, or an item stack `{"id", "count", "components"}` | gives it (dropped if the inventory is full) |
| `set_flag` | flag id, or `{"id", "value"}` | sets the player's flag (to 1) |
| `clear_flag` | flag id | clears it |
| `add_rep` | amount, or `{"amount", "faction", "reason"}` | changes standing with the NPC's faction (`reason` defaults to `fealty:dialogue`, which cannot raise standing below zero) |
| `start_quest` | quest id, or `{"quest", "giver"}` | the player takes up a quest; a villager's is handed back to them as a favor, others to their faction |
| `grant_advancement` | advancement id | awards it |
| `run_function` | function id | runs it as the player (permission level 2) |
| `start_chain` | chain id | starts a chain in the NPC's village, as a stage 1 rumour would |

Player flags are kept on the player through death and relogging. `/fealty flag <player> get|set|clear <flag> [value]`
and `/fealty flag <player> list` read and change them (operators only).

## dialogue/

Lines NPCs say, by context. Every file adds lines to its `context`; several files may share one.

```json
{
  "context": "greet",
  "lines": [
    { "text": {"translate": "mypack.line.greet.friend"}, "tiers": ["fealty:trusted", "fealty:honored"], "weight": 2 },
    { "text": "Late to be out. Mind the dark.", "night": true },
    { "text": {"translate": "mypack.line.greet.smith"}, "professions": ["minecraft:armorer"] }
  ]
}
```

Conditions (all optional, and a line must meet every one it has): `tiers` (the listener's tier with the speaker's
village), `professions`, `entities` (the speaker's type), `lord` (the listener rules the village), `broken` (the
village has lost its elder), `night`. In a `translate` line, `%s` is the listener's name. One line is picked at random
by `weight`.

Contexts Fealty uses include `greet`, `greet_child`, `farewell`, `rumour_trivia`, `rumour_refuse`, `chatter_hush`, `work_none`, the `favor_*` set, the `gift_*`
set, `threat_cower`, `threat_refuse`, `refuse_hated`, `refuse_threats`, `crime`, `crime_theft`, `crime_violence`,
`crime_vandalism`, `guard_greet`, `guard_hostile`, `thanks_rescue`, `thanks_healed` and `hated_arrival`.

## chatter/

`data/<ns>/fealty/chatter/<name>.json` is one thing two villagers can chat about: a few lines they say in speech
bubbles, taking turns (the one who walks up speaks first), and what either tells a player who asks "What's up?".
Say too little in the lines and leave the news for the `tell`.

```json
{
  "weight": 2,
  "lines": [{"translate": "mypack.chatter.pie.0"}, "Who ate the pie on the windowsill?", "Not me."],
  "tell": "A pie vanished from a windowsill. Everybody denies it. I blame the cat.",
  "professions": ["minecraft:farmer"],
  "night": false
}
```

`lines` has two to six entries, each a text component or a plain string. `professions` (optional): at least one of
the two villagers has one of them. `night` (optional): only at night (true) or only by day (false). `tell` should
read as said by either of the two, so say "we". `weight` defaults to 1.

The small talk a villager answers with when not chatting, and how they refuse, are `dialogue/` contexts:
`rumour_trivia` (the usual conditions apply, including `tiers` of the player's standing), `rumour_refuse` and
`chatter_hush` (what the other villager says when you make one tell).

## village_trades/

```json
{
  "professions": ["minecraft:farmer"],
  "min_tier": "fealty:trusted",
  "min_level": 1,
  "offers": [
    { "buy": {"id": "minecraft:emerald", "count": 3}, "buy_b": {"id": "minecraft:wheat", "count": 1},
      "sell": {"id": "minecraft:golden_carrot", "count": 8}, "max_uses": 8, "xp": 4, "price_multiplier": 0.05 }
  ]
}
```

Added to matching villagers' trades while a qualifying player trades. Uses restock daily. Leave `professions`
empty to apply to every profession.

## black_market/

```json
{ "buy": {"id": "minecraft:emerald", "count": 6}, "sell": {"id": "fealty:smoke_bomb", "count": 3}, "max_uses": 4, "weight": 10 }
```

Each marketeer stocks five offers picked by weight, and restocks daily.

## Loot tables

| Table | Used for |
|---|---|
| `fealty:chests/village_coffer` | Village coffers |
| `fealty:chests/bandit_camp` | Bandit camp chests |
| `fealty:chests/pillager_camp` | Pillager camp barrels and chests |
| `fealty:chests/pillager_scout_camp`, `pillager_fort`, `pillager_castle` | Scout camp, fort and castle stores |
| `fealty:quest_rewards/war_spoils` | The lord's reward for a won raid |
| `fealty:chests/hidden_hamlet` | The Keeper's lodge |
| `fealty:chests/hamlet_cottage` | Hamlet cottages |
| `fealty:gameplay/tribute` | Rolled per tribute measure |
| `fealty:gameplay/gift_of_love` | Rolled per gift of love a village gathers for its lord |
| `fealty:gameplay/pickpocket` | Pickpocketing |
| `fealty:gameplay/honored_gift` | Gifts from villagers without a vanilla Hero of the Village table |
| `fealty:quest_rewards/common`, `rare` | Quest rewards |
| `fealty:entities/<entity>` | Mob drops |

## Tags

| Tag | Used for |
|---|---|
| `#fealty:guards` (entity type) | Mobs that act as guards (iron golems; Guard Villagers' guards if installed) |
| `#fealty:village_members` (entity type) | Extra entities that belong to the village they stand in |
| `#fealty:bandits` (entity type) | Members of the bandit faction |
| `#fealty:village_property` (block) | Breaking it in a village is a crime (POI blocks always count) |
| `#fealty:village_containers` (block) | Taking from these in a village is theft; locked to players below Trusted (config `locked_village_chests`) |
| `#fealty:workstations` (block) | Counted by restock quests |
| `#fealty:rebuildable` (block) | May crumble for rebuild quests |
| `#fealty:threat_weapons` (item) | Counts as a drawn weapon |
| `#fealty:villager_gifts` (item) | Welcome as gifts |
| `#fealty:elder_villages` (structure) | Villages that get an elder |
| `#fealty:hidden_hamlets` (structure) | Where the chain's map leads |
| `#fealty:bandit_camps` (structure) | Bandit camps |
| `#fealty:pillager_camps` (structure) | Fealty's pillager strongholds with a War Banner: scout camps, camps, forts, castles |
| `#fealty:pillager_outposts` (structure) | Pillager outposts (vanilla's and `#c:pillager_outposts`) |
| `#fealty:pillager_strongholds` (structure) | Everything a lord can raid (the two above) |
| `#fealty:stronghold_defenders` (entity type) | Mobs that defend a stronghold (default `#minecraft:raiders`) |

## Loot condition and advancement triggers

```json
{ "condition": "fealty:rep_tier", "entity": "this", "faction": "here", "min_tier": "fealty:trusted" }
```

`faction` is a faction id, `fealty:renown`, or `here` (the village at the loot origin). It works in loot tables,
predicates and advancement `player` conditions.

Advancement triggers:

- `fealty:rep_tier`: fields `faction`, `villages_only`, `min_tier`, `max_tier`, `rep`.
- `fealty:renown`: fields `renown`, `min_tier`, `max_tier`.
- `fealty:crime`: fields `crime`, `witnessed`.
- `fealty:rep_quest`: fields `quest`, `type`, `status` (`started`, `completed` or `failed`).
- `fealty:event`: field `event`, one of the ids in `FealtyEvents`. For example:
  - Village: `met_elder`, `courier_delivered`, `raid_defended`, `village_broken`, `elder_restored`.
  - Chain and lordship: `chain_started`, `hamlet_found`, `writ_forged`, `sworn_lord`, `tribute_collected`, `usurped`.
  - Outlaw: `black_market_trade`, `guild_joined`, `follower_hired`, `unseen_theft`, `lock_picked`, `wanted`,
    `tyrant_slain`.
