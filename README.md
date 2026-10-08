# Fealty

Every villager remembers how you treated their village. Fealty is a reputation mod for medieval modpacks on
**Minecraft 1.21.1 / NeoForge**. Your standing with each village changes prices, dialogue, guards and quests, and
opens two real paths: the honourable road to **village lordship**, and the outlaw road of black markets, the thieves
guild, bandit followers, bounty hunters and the **Tyrant Lord**.

## Features

### Standing
- **Reputation per village, plus Renown.** Rep runs from -100 to 100 across five tiers: Hated, Distrusted,
  Neutral, Trusted and Honored. Renown carries 10% of every change between villages, and a new village starts at a
  quarter of it (capped at ±25). Every change shows as a line on screen; tier changes get a banner.
- **Villages from any mod.** Vanilla villages, ChoiceTheorem's Overhauled Village, Oh The Biomes We've Gone,
  Epic Structures: Villages, castle and town mods, and any settlement with three or more homes are all villages.
  The elder lives in each kind of village's main building (data-driven `village_layouts`). `/rep village scan`
  shows what is at your feet, and `/rep village create` makes one by hand.
- **Good deeds** villages notice: quests and favors, trade, gifts, defending them from raids and bandits, killing
  monsters in the village (more if they were attacking a villager), healing villagers with potions, ringing the
  bell when danger is near, mending golems, lighting dark corners, giving beds, tending crops, and putting
  emeralds in the coffer.
- **Crimes, seen by witnesses only.** A crime counts only if a villager or guard sees it (16 blocks, facing you):
  breaking property or the bell, coffer or mailbox; theft (judged the moment the first item leaves the chest);
  pickpocketing; threats; hitting villagers, children or guards; murder; trampling or harvesting crops; killing
  the village's animals and cats; arson and explosions; lurking in homes at night; sleeping in a villager's bed;
  killing a wandering trader.
- **Consequences.** Word of a crime reaches the villages nearby the next day. A village that honours you lets a
  crime go with a scowl (it still costs you). Otherwise guards demand a fine for small crimes (pay it, or they come
  for you) and attack for violent ones. Walk into a village that hates you and the bell rings and everyone runs.
  Elders let you pay to clear your name, a little each day. Stay Hated and heat builds: bounty hunters, then the
  Tyrant Lord.
- **Out of sight, out of mind.** Stay away from a village for two days and your standing there starts to fade
  (10, then 5 a day), down to Neutral at worst.

### Talking
- **Dialogue box and speech bubbles.** Right-click a villager to talk: trade, ask for work, hear rumours, give a
  gift, deliver a letter, or threaten. NPCs say their lines out loud in bubbles above their heads. Sneak to trade
  straight away.
- **Quest tracker** at the top left (K cycles full, compact and hidden; J opens the journal), with progress,
  directions and timers for several quests at once.
- **Quests:** elder quests by tier, small daily favors from any villager (nitwits too), and objective types from
  fetch, hunt and courier to retrieving lost things from the wilds, exploring, talking to people and defending
  raids.
- **The rare villager chain.** Three named villagers from a pool of roles (smith, fool, drifter, ...), each with
  several quest variants and a locked profession, lead to a hidden hamlet and the Keeper's trial.

### The village
- **Guards.** Each village keeps a watch of Fealty guards (swordsmen, archers and a sergeant in the village's
  colours), each walking their own beat across the village, that respawn a day after they fall. Iron golems and other mods' guards in `#fealty:guards` work too.
- **Mail.** Every village has a mailbox, and you can craft your own. Write to a village or another player, with up
  to three parcels. Villages write back, thank those they trust, and warn those they hate.
- **Locks.** Village chests are locked to anyone the village does not trust, and the coffer to everyone but the
  lord. A lockpick opens them, if your hand is steady.
- **Smoke bombs** hide you for 15 seconds: nothing can target you or see you in the smoke.
- **Bandits.** Camps are far apart, only one in a wide area is manned, and bandits walk in from the treeline.
  Every few days a camp may raid a nearby village while you are there.

### Lordship
Present the Royal Writ to a village that honours you. Keep their love: fall below Honored for two days and they
renounce you. Run it from the **Village Hall**: set taxes (Crushing fills the
treasury fast and costs you their love; light taxes bring less, but a village that adores you sometimes gathers a
gift of love), collect the treasury, hold feasts, recruit guards. Golems and other mods' guards are on the roster too. Blow the **Lord's Horn**
anywhere to call your guards to you; a bar at the top of the screen shows who follows you.

### Gossip
Villagers chat with each other in speech bubbles now and then: one walks up to another, they trade a few lines and
go back to their day. The bubbles only hint at it. Ask "What's up?" and they tell you what it was about, or, away
from a chat, something they know: a pillager camp or bandit camp nearby, who needs help, who sells an enchanted book,
how the lord's taxes are taken, how many guards have fallen. Or something silly. The more a village trusts you, the
more it tells you (and Trusted players get strongholds noted on the War tab). Data-driven small talk in `chatter/`.

### War
Pillager strongholds dot the land: scout camps, palisaded camps, walled forts and stone castles with dungeon cells,
each rated one to five skulls, and pillager outposts count too. From the Village Hall's **War** tab a lord sends scouts, picks a stronghold and raises the warband: a war map
and a tracked quest lead the way, the guards fall in behind, and the rest of the watch and a levy of militia catch
up near the camp. Raze it to free the captives, fill the treasury and win the village days of peace. Leave it
standing and its raiders come for the villages nearby.

### For other mods
- **Data-driven:** tiers, factions, rep actions, crimes, quests, quest chains, dialogue, village trades and black
  market stock are all data pack JSON. The loot condition `fealty:rep_tier` and advancement triggers let anything
  gate on reputation.
- **Integrations (all optional):** Jade tooltips, FTB Quests task and reward types, KubeJS events, JEI village
  trades.
- **Public API** in a separate jar for other mods (guards, bosses, quests), with events for raids, strongholds,
  captives, guards, fines, lockpicks, mail and villages (also forwarded to KubeJS): see [docs/API.md](docs/API.md).

## Getting started

1. Drop `fealty-<version>.jar` into `mods/`. NeoForge 21.1 is the only hard requirement.
2. Visit a village. Talk to anyone; the elder lives indoors near the bell and has the village's real work.
3. Press J for the journal (or use the Ledger given on first join) to see your quests and standing.

Controls (Options > Controls > Fealty): J journal, K tracker mode. Right-click a villager to talk. Sneak and
right-click: with a weapon, threaten; with a gift, give it; with an empty hand from behind, pickpocket; otherwise,
trade.

## For pack makers

- [docs/PACK_GUIDE.md](docs/PACK_GUIDE.md): villages from other mods, guards, FTB Quests, KubeJS and balancing.
- [docs/DATAPACKS.md](docs/DATAPACKS.md): every data pack folder, with examples.
- Commands: `/rep get|add|set|list|heat|village|quest` (operators only). `here` means the village you stand in.
- Server config: `serverconfig/fealty-server.toml`. Client config (tracker, bubbles): `config/fealty-client.toml`.

## Building

```sh
./gradlew build              # mod jar + API jar in build/libs
./gradlew runGameTestServer  # in-game tests
./gradlew runClient
```

Optional integrations compile only when enabled in `gradle.properties` (`compat_jade`, `compat_jei`,
`compat_ftbquests`, `compat_kubejs`).

Textures are generated placeholders: `python3 tools/art/generate_art.py`. Blockbench projects for the custom
block models are in `tools/art/bbmodel/`. Entity textures use the standard villager and player-skin layouts, so
they open directly in Blockbench.

## License

All rights reserved unless stated otherwise by the author.
