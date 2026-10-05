# Fealty pack-maker guide

## Castle villages and elders

Every village structure in the `fealty:village` template's `structures` tag (default `#minecraft:village`) becomes
a village faction. Only villages whose structure is also in `#fealty:elder_villages` get a trusting elder and a
coffer. By default that tag holds `#minecraft:village`, so every village gets an elder. To limit elders to your
castle villages, override the tag in a data pack:

```json
// data/fealty/tags/worldgen/structure/elder_villages.json
{ "replace": true, "values": ["yourcastlemod:castle_village", "yourcastlemod:keep_town"] }
```

Villages from mods that aren't in any template's tag are still found by their bell, and their villagers belong
to that village. Villagers with no village nearby belong to `fealty:wanderers`. To make another mod's town a full
village, add its structure to `#minecraft:village`, or add a template in `fealty/factions/`.

The elder spawns indoors near the bell the first time a player visits, with the village coffer beside them.
Config: `spawn_elders`, `place_coffers`, `bell_villages_have_elders`, `village_margin`.

## Guards

Anything in `#fealty:guards` acts as a guard. The default tag holds iron golems and, if installed, Guard
Villagers' `guardvillagers:guard`. Add your guard mod's entities to the tag. Fealty gives them its targeting goals
when they spawn, so the guard mod needs no compile dependency. Guards attack Hated and wanted players, watch
Distrusted ones, help Trusted ones, and escort Honored ones. A witnessed crime alerts guards within 32 blocks.
Guards of other villages only answer if the criminal's Renown is low.

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
  - heat thresholds and bounty interval
  - tribute interval and tax table; max lordships per player
  - outlaw Renown gates

## Lordship

The Royal Writ comes from the rare villager chain. Its holder can swear a village where they are Honored.
Lords get:

- tribute in the coffer every few days (more villagers and higher taxes mean more)
- the Lord's Horn: follow, hold or defend
- a tax setting: None and Light raise the lord's standing a little each day, Heavy and Crushing lower it

Below Trusted, the village renounces its lord. In multiplayer, a rival with a Writ who is Honored and better loved
than the current lord can usurp the village.

## Outlaw path

Outlaw gates use Renown (a player's name across villages):

- Black market and thieves guild: Renown at or below −21 (Distrusted).
- Bandit followers: at or below −61 (Hated).

Being Hated by a village builds heat. Enough heat sends bounty hunters; more summons the Tyrant Lord to a castle
village. Killing him drops the Tyrant's Crown, clears heat and frees the village.

## Compatibility notes

- MineColonies overlaps heavily with a village reputation system and is best left out of the pack.
- Mods that rewrite villager AI or professions can clash with the price and threat logic. Test them before
  release.
- Boss mods can link to rep through the API (spawn on a Hated event, gate drops on a tier).
