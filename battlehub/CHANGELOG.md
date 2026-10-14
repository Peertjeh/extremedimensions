# Changelog

## 1.3.0

### Added
* **PvP tab** in the compass menu (instead of Duels): Free for All, 1v1, 1v2 and 1v3, then the kit
  (sword, axe or mace).
* **1v2 and 1v3**: one player against a team of two or three. A queue per kit where you choose to play
  alone, in the team, or either; no friendly fire in the team; play again re-queues with the same kit and
  side; stats per mode.
* **Free for All with a kit vote**: pick sword, axe or mace when you join; the most votes decide.
* **`/battlehub coins`** for admins: show, give, take and set coins, also for offline players.

### Changed
* Free for All moved from Party Games to the PvP tab.
* Extreme Dimensions (same repository) pauses inside BattleHub's worlds.

## 1.2.0

### Added
* **Tournament** (up to 16 players): vote for teams of 1, 2, 4 or 8 and a duel kit (or random); random
  teams play a knock-out bracket (byes when needed), every match best of 3 rounds, matches of a round side
  by side. No friendly fire, team names on the nametag, a walkover when a team has nobody left. Champions
  get 500 coins each, runners-up 150, and a tournament win in their stats.
* **Free for All**: everyone against everyone with respawns at the spawn farthest from the others and
  3 seconds of spawn protection; first to 15 kills, or the most kills when time runs out. Its own arena
  and kit.
* Duel arenas can be scaled per mode (`arenaScale`).

### Changed
* Mace arenas are 1.8 times bigger, with more and farther floating islands.
* Parkour on easy and medium gives XP but no coins (`coinsEasy`, `coinsMedium`, `coinsHard`,
  `coinsExtreme`: the share of the coin reward).

### Fixed
* A totem of undying in a kit did nothing in matches: BattleHub cancelled the death before vanilla
  checked for a totem. It now saves the player as in vanilla (not against the void).

## 1.1.0

### Added
* **BattleHub's own world.** A hub dimension (`battlehub:hub`) with a plaza and four themed islands,
  built by the mod. `/hub` takes you there and saves your survival state (inventory, XP, health, hunger,
  effects, game mode, position); `/survival`, the door item or the menu button bring you back with
  everything restored. The normal world keeps its normal rules: no compass, no protection, no changed
  damage. Players moved in or out some other way are checked in or out automatically. In the hub the
  XP bar shows your BattleHub level. `hub.sendToHubOnJoin` for BattleHub-only servers.
* **Best of 1 / best of 3** duels, chosen per player (remembered) in the duel, ranked and challenge menus
  or with `/duel <player> <mode> bo1|bo3`. Rounds in the same arena with a fresh kit and a short
  countdown; when a round's time runs out, more health wins it. A series counts as one match for stats,
  coins and ELO. Separate queues per format.
* **Play again** after every match: a chat button and an item in the first hotbar slot (30 seconds)
  queue the same mode, format and ranked setting, ask for a rematch after a challenge, or rejoin the
  same party game lobby. Also `/battlehub again`.
* **Cosmetics shop** (compass menu, `/cosmetics`): trails, kill effects, arrow trails, win effects and name
  colours, bought with coins. 44 cosmetics built in; the list and prices are in `cosmetics.json`.
* **Random arenas.** Every match gets a newly generated arena: random shape, size, hills, obstacles,
  cover and scenery. Duel arenas are point-symmetric so both players get the same ground. **Eight new
  themes**: Desert, Jungle, Cherry Grove, End, Mushroom, Badlands, Deep Dark, Ocean (13 in total). Party
  game arenas get random shapes, hills and cover too.
* **Parkour v2.** A new course every race, much longer: 30/50/70/90 jumps for easy/medium/hard/extreme,
  with ladders, slab steps, fence posts, ice, 1×1 landings, diagonal jumps and turns. Players vote for
  the difficulty (or random) when they join. Every jump is checked against what a player can reach, and
  there are no shortcuts. Parkour can be played alone; best times are kept per difficulty and shown in
  the stats menu.
* Commands: `/survival`, `/cosmetics`, `/battlehub exit`, `/battlehub again`, `/battlehub lobby rebuild`.

### Changed
* The lobby is now the hub world. Lobby rules (protection, keep fed, no damage) only apply there.
  Config version 2: old lobby settings are reset to the hub, unchanged theme lists are upgraded to all
  themes, and parkour gets its new defaults (min 1 player, longer time limit).
* Mace duels are played on floating islands (Sky or End).
* Titles show only while the player is in BattleHub, not in the normal world.

## 1.0.1

### Fixed
* Items could be taken out of BattleHub menus in creative mode, for example with the buttons of
  inventory-sorting mods. Creative clients set their own inventory slots directly, which skipped the click
  protection. Now:
  * the server refuses creative inventory changes while a BattleHub menu is open;
  * it refuses any BattleHub item, and anything that would overwrite or clear the compass;
  * every menu item is marked and removed from an inventory if it ever ends up there by any route;
  * after a refused click the client gets the real contents back at once, so no ghost items remain.

## 1.0.0 – first release

For Minecraft 26.3, Fabric Loader 0.19.5+, Fabric API. Server-side only; vanilla clients can join.

### Added
* **BattleHub Compass** in a fixed hotbar slot. It can't be dropped or moved, and opens the main menu
  with Duels, Ranged, Party Games, Ranked, Spectate and Stats.
* **Duels:** Sword, Axe, Mace (Sky Arena), Bow, Crossbow. Queue for a random opponent, or challenge a
  player from the menu or with `/duel`.
* **Ranked:** ELO per mode (start 1000, K = 32), tiers Bronze → Netherite, matchmaking whose rating
  window widens while you wait, and leaderboards.
* **Party games:** Bow Spleef, TNT Run, Sumo, King of the Hill, Capture The Flag, Parkour Race,
  Last Man Standing. Each has a lobby with min/max players and a start countdown.
* **Five arenas** generated in code (Forest, Castle, Nether, Ice, Sky) in their own void dimension,
  in slots, so matches run side by side. Each is built fresh and cleared afterwards.
* **Spectating:** a live menu of running matches, spectator mode kept inside the arena, leave with
  `/hub` or [Leave].
* **Progression:** coins, XP and levels with level-up bonuses, buyable and unlockable titles
  (chat, nametag, tab list), win streaks with milestone announcements, and a stats menu with `/stats`.
* **Safety:**
  * Death is intercepted, so no death screen and no drops.
  * Inventory, health, hunger, game mode and position are restored after every match, after a
    disconnect and after a crash.
  * A disconnect counts as a forfeit.
  * Every match ends cleanly when the server stops.
  * The lobby is protected from block breaking and placing.
* **Languages:** English and Dutch built in, chosen per player from their client language. Add more
  languages or reword lines in `config/battlehub/lang/`.
* **Configuration** in `config/battlehub/`: `config.json`, `kits.json`, `titles.json` and `lang/`
  for translations. `/battlehub reload` applies changes.
* **Admin commands:** `/battlehub setlobby | reload | matches | endmatch <id> | arena list`.
