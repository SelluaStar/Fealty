# Fealty data packs

Everything that balances Fealty lives in data packs, so packs can retune it without rebuilding the mod. Files go
under `data/<namespace>/fealty/<folder>/`. Fealty's own defaults are in `data/fealty/fealty/...`; override one by
shipping a file with the same id, or add new ones under your own namespace. All folders reload with `/reload`.

| Folder | Defines |
|---|---|
| `tiers/` | Tier names, rep ranges, price multipliers, guard and trade behaviour |
| `factions/` | Village templates and static factions (bandits, the thieves guild, ...) |
| `rep_actions/` | Amounts for ways to gain rep (gifts, trades, raids, ...) |
| `crimes/` | Offences: amount, severity, heat |
| `rep_quests/` | Trusting villager quest pools, by tier |
| `quest_chains/` | The rare villager chain and the thieves guild |
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
  "structures": "#minecraft:village",
  "elder": true,
  "renown_share": 0.1,
  "renown_start_factor": 0.25,
  "priority": 0,
  "members": "#fealty:village_members"
}
```

- `kind: village` is a template. Every structure start in `structures` becomes its own faction, with id
  `village:<dimension namespace>/<dimension path>/<chunkX>_<chunkZ>`. Villages found only by their bell use
  `.../bell_<x>_<y>_<z>`. When several templates match a structure, the highest `priority` wins.
- `elder`: whether villages of this template get a trusting elder. The structure must also be in
  `#fealty:elder_villages`.
- `renown_share`: share of each rep change that also moves Renown. `renown_start_factor`: a new faction starts at
  this fraction of the player's Renown.
- `kind: static` is a single faction under the file's id (e.g. `fealty:bandits`).

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

Defaults (from the design): quest +5..+15, defend raid +20, gift +2, trade +1 per 5 (max +3/day), break village
blocks −5, steal −15, threaten −3, hit −10, kill guard −50, kill villager −40.

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
  - `fealty:restore`: offered when a Broken village is near.
  - `fealty:none`: used only by chains.
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
| `fealty:courier` | | Carry a sealed letter to another elder; completes on delivery |
| `fealty:hunt` | `targets: [{entity, name?, health_multiplier, damage_bonus, armor_bonus, equipment}]`, `min_distance`, `max_distance` | A named elite appears when you near its lair |
| `fealty:restore_elder` | `items` | Two stages: hand in items for an Elder's Mantle, then use it on a villager of the Broken village |
| `fealty:steal` | `items`, `unseen` | Thieves guild |
| `fealty:pickpocket` | `count` | Thieves guild |
| `fealty:vault_raid` | `count` | Thieves guild: empty coffers unseen |

## quest_chains/

```json
{
  "kind": "rare_villager",
  "start_tier": "fealty:trusted",
  "steps": [
    {"role": "smith", "professions": ["minecraft:armorer", "minecraft:weaponsmith", "minecraft:toolsmith"], "quest": "fealty:chain/smith_ore"}
  ],
  "steps_reward": {"id": "fealty:signet_of_the_old_crown"},
  "map_structure": "#fealty:hidden_hamlets",
  "trial_quest": "fealty:chain/keeper_trial",
  "trial_reward": {"id": "fealty:keepers_charter"},
  "final_reward": {"id": "fealty:royal_writ"},
  "repeatable": true
}
```

The `fealty:rare_villager` chain:

1. A Trusted player asks the elder about rumours.
2. Each step's role goes to a villager of that village (matching profession preferred), who becomes a named
   villager. Sneak-talk to them for their quest.
3. When every step is done, the player gets `steps_reward` and a map to `map_structure`.
4. The Keeper there checks that the player is still at `start_tier` with the origin village.
5. The Keeper gives `trial_quest`; completing it rewards `trial_reward`.
6. The Keeper combines `steps_reward` and `trial_reward` into `final_reward`.

The `fealty:thieves_guild` chain (`kind: guild`) is handed out one step at a time by the guild fence. Each step
can carry its own `rewards`.

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
| `fealty:chests/hidden_hamlet` | The Keeper's lodge |
| `fealty:chests/hamlet_cottage` | Hamlet cottages |
| `fealty:gameplay/tribute` | Rolled per tribute measure |
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
| `#fealty:village_containers` (block) | Taking from these in a village is theft |
| `#fealty:workstations` (block) | Counted by restock quests |
| `#fealty:rebuildable` (block) | May crumble for rebuild quests |
| `#fealty:threat_weapons` (item) | Counts as a drawn weapon |
| `#fealty:villager_gifts` (item) | Welcome as gifts |
| `#fealty:elder_villages` (structure) | Villages that get an elder |
| `#fealty:hidden_hamlets` (structure) | Where the chain's map leads |
| `#fealty:bandit_camps` (structure) | Bandit camps |

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
  - Outlaw: `black_market_trade`, `guild_joined`, `follower_hired`, `wanted`, `tyrant_slain`.
