# Example: a stranger's tale

A data pack (and, through `assets/`, a resource pack for its text) that uses Fealty's story extension points with
nothing but data: a rumour, a branching conversation and a single-giver quest chain. Everything is in the neutral
`example` namespace. Copy the folder into a world's `datapacks/` (and `resourcepacks/` for the text), or use it as a
starting point for your own mod's `data/` and `assets/`.

| File | What it does |
|---|---|
| `rumours/stranger_tale.json` | Villagers may tell it (rolled once a day per villager) to a Neutral or better player in the overworld. Hearing it starts the chain (stage 1) and gives a letter (`minecraft:paper` here; a mod would use its own item). |
| `quest_chains/stranger_tale.json` | `"kind": "story"`, `"giver": "single"`: one villager of the village becomes the Stranger and gives all three steps in order. The first waits for the flag `example:met_stranger`, the last for nightfall. The final reward is a compass. |
| `conversations/stranger/first_meeting.json` | Takes over the Stranger's dialogue until the player has shown the letter. Showing it (only possible with the letter in hand; otherwise the reply is greyed out with a hint) takes the letter, sets `example:met_stranger` and adds a little standing with the village. |
| `rep_quests/stranger/*.json` | The three steps: plain `fealty:fetch` quests in pool `fealty:none` (only chains use them). |
| `assets/example/lang/en_us.json` | The text. A translation key gets the player's name as `%1$s` and the speaker's as `%2$s`. |

How it plays:

1. Talk to villagers in a village where you are at least Neutral. Now and then one passes on the rumour (raise
   `rumour_chance` in `serverconfig/fealty-server.toml`, or use the pity: after `guarantee_after` conversations that could have
   turned it up, it comes for sure). You get the letter, and a villager is named "<name> the Stranger".
2. Talk to the Stranger: the first meeting plays. Show the letter.
3. Ask the Stranger for work: wood, then lanterns, then (after dark) the night watch. Each step is handed in to them.
4. The last step pays its emeralds and the chain's final reward.

Useful while building a story: `/fealty flag <player> list`, `/fealty flag <player> clear example:met_stranger`.
