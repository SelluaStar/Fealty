# Fealty

Every villager remembers how you treated their village. Fealty is a reputation mod for medieval modpacks on
**Minecraft 1.21.1 / NeoForge**. Your standing with each village changes prices, dialogue, guards and quests, and
opens two real paths: the honourable road to **village lordship**, and the outlaw road of black markets, the thieves
guild, bandit followers, bounty hunters and the **Tyrant Lord**.

## Features

- **Reputation per village, plus Renown.** Rep runs from -100 to 100 across five tiers: Hated, Distrusted,
  Neutral, Trusted and Honored. Renown carries 10% of every change between villages, and a new village starts
  at a quarter of it (capped at ±25).
- **Witnesses only.** A crime counts only if a villager or guard sees it, within 16 blocks and facing you. Smoke
  bombs and rogue gear help you avoid being seen.
- **Crimes:** breaking village property, theft (container contents are diffed), coffer raids, pickpocketing,
  threats, assault and murder.
- **Prices and trades.** Prices scale by tier (Hated 2.0x down to Honored 0.7x). Hated villagers refuse to trade
  unless threatened. Distrusted players lose master trades, and Trusted/Honored players get village-exclusive trades.
  Vanilla gossip is switched off so the two systems don't stack.
- **Threats:** sneak and use a drawn weapon on a villager. The next trade is at the Neutral price, it costs 3 rep,
  and the cooldown is one day. After three threats the villager refuses to trade for a day.
- **Guards** (iron golems, Guard Villagers' guards, or anything in `#fealty:guards`) attack, watch, ignore, assist
  or escort you depending on your tier. A witnessed crime alerts guards within 32 blocks.
- **The trusting elder.** Each castle village has one robed elder, the only reliable way to raise rep. They offer
  redemption quests: fetch, defend (a real raid, or a bandit camp), repair (restock or rebuild), courier, and
  hunt. Kill the elder and the village is **Broken** until a neighbour's elder helps restore it.
- **The rare villager chain.** Ask the elder about rumours to start it. Three named villagers each give a quest,
  rewarding the Signet and a map. The map leads to a hidden hamlet where the Keeper sets a trial. Signet plus
  Charter makes the **Royal Writ**.
- **Lordship.** Present the Writ to a village that honours you. You collect tribute in the village coffer,
  command its guards with the Lord's Horn, and set taxes, trading tribute for loyalty. A better-loved rival can
  usurp you.
- **The outlaw path.** Bandit camps hold a black marketeer and a guild fence. The thieves guild questline rewards
  rogue gear. You can hire bandit followers. Heat builds while you're Hated, bringing bounty hunters and then the
  Tyrant Lord boss.
- **Ledger** item showing every standing, plus an action-bar notice when you enter a village.
- **Data-driven:** tiers, factions, rep actions, crimes, quests, quest chains, village trades and black market
  stock are all data pack JSON. The loot condition `fealty:rep_tier` and advancement triggers let anything gate on
  reputation.
- **Integrations (all optional):**
  - Jade shows standing in tooltips.
  - FTB Quests gets reputation task and reward types, and every story beat is an advancement.
  - KubeJS gets events and bindings.
  - JEI gets a village trades category.
- **Public API** in a separate jar for other mods (guards, bosses, quests): see [docs/API.md](docs/API.md).

## Getting started

1. Drop `fealty-<version>.jar` into `mods/`. NeoForge 21.1 is the only hard requirement.
2. Visit a village. The elder lives indoors near the bell; use them to see quests.
3. Open the Fealty Ledger (given on first join) to see your standing.

Controls: sneak + weapon on a villager to threaten, sneak + a gift to give it, and sneak + empty hand to talk or to
pickpocket from behind. Sneak + empty hand on a guard asks for an escort (Honored only).

## For pack makers

- [docs/PACK_GUIDE.md](docs/PACK_GUIDE.md): castle villages, guards, FTB Quests, KubeJS and balancing.
- [docs/DATAPACKS.md](docs/DATAPACKS.md): every data pack folder, with examples.
- Commands: `/rep get|add|set|list|heat|village|quest` (operators only). `here` means the village you stand in.
- Server config: `serverconfig/fealty-server.toml`.

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
