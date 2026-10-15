# Changelog

## 1.10.0

### Added
* **Friends** (`/friend`): requests, a list with what each friend is doing, a note when friends come online.
* **Parties** (`/party`, up to 8): the party follows its leader into party games and team fights, and stays
  on one team in Capture the Flag and Bed Wars. Party chat with `/pc`.
* **Clans** (`/clan`): a tag before your name, clan chat (`/cc`), points for wins, a clan leaderboard and a
  weekly clan war with coins for the winning clan.
* **Private games** (`/custom`): your own lobby for a duel or party game with a code; never counts for stats,
  rewards or ELO.
* The main menu has buttons for friends, clans and private games.

## 1.9.0

### Added
* **Quests**: three daily and three weekly quests per player, with coins, XP and season pass XP (`/quests`).
* **Login streak**: coins for the first visit each day, more for every day in a row.
* **Season pass**: 50 free tiers per season with coins and cosmetics, paid out right away (`/pass`).
* **Achievements** with tiers and coin rewards (`/achievements`).
* **Prestige** at the top level: back to level 1 with a star before your name and more coins.
* New titles for the season pass and prestige (added to existing `titles.json` files).
* The main menu has a second row: practice, kit editor, quests, season pass and achievements.
* Settings in the new `progress.json`.

## 1.8.0

### Added
* **Ranked seasons** of 8 weeks: divisions in every tier (Gold III to Gold I), 5 placement matches per mode
  that count double, a reward in coins for the tier of your peak at the end, season titles, and a soft reset
  (halfway back to the start). The Ranked menu shows the season and how your last one ended.
* **Match recap** after every match: damage dealt and taken, hits and accuracy, longest combo, arrows, and the
  MVP.
* **Kit editor** (`/kits` and in the menu): your own layout for every kit.
* **Fair play**: a growing wait after leaving matches early, idle players taken out of matches and queues,
  a teaming warning in Free for All and Last Man Standing, alerts for admins (reach, click speed, hovering,
  speed) and `/report` with `/battlehub reports`.

## 1.7.0

### Added
* **Bots** in three levels (easy, normal, hard). They fill casual duel queues, party lobbies, 1v2 and 1v3 when
  someone waits alone for 15 seconds (party lobbies up to 4), and step out when a second person joins; never
  ranked. Each game has its own bot behaviour: fighting with strafes, crits, w-taps, shields and golden apples;
  bows and crossbows with leading and arcs; TNT Run running; Bow Spleef floor shots; parkour jumps; Prop Hunt
  hiding and hunting; KOTH, CTF and Bed Wars objectives.
* **Practice**: `/practice [mode] [level]` and a Practice menu: a duel, 1v2 or 1v3 against bots right away.
* Matches with bots pay half the rewards and never count for stats, streaks or ELO. Nothing is saved for bots.
* **Lobby characters** around the fountain for PvP, Free for All, Ranked, Practice, Bed Wars, Prop Hunt, Party
  Games and Parkour, with live player counts, in each player's language. Click to play.
* **Mode of the day** with double coins, announced in the hub and starred in the menus and on the characters.
* Admin: `/battlehub bots` and `/battlehub bots clear`.

## 1.6.1

### Changed
* **Three lobby leaderboards instead of nine**: total kills and total deaths over every BattleHub game on
  the north-west wall, and the best parkour runner (most parkour races won) on the south-west wall. The
  text is a little bigger. A `config.json` with the 1.6.0 boards is updated; changed boards are kept.

## 1.6.0

### Added
* **Leaderboards in the lobby**: floating boards above the plaza wall. PvP on the north-west wall (kills,
  deaths, K/D, coins earned) and Ranked on the south-west wall (ELO, kills, deaths, K/D, coins earned).
  The boards go through the modes in turn, show your own place at the bottom and are in each player's own
  language. They exist only on the players' clients. Walls, stats and modes are set in `config.json`
  (`leaderboards`).
* **Coins earned** are counted: per mode, for ranked, and in total (level bonuses and tournament prizes
  included). For profiles from before 1.6 an estimate is made once from their match counts and the
  reward settings, and the total is at least the coins they have plus what they spent in the shops.

## 1.5.0

### Added
* **Bed Wars solo and duos**: vote in the lobby for everyone for themselves or teams of two. Up to eight
  islands, with four new team colours (aqua, white, pink, gray).

### Changed
* **Prop Hunt**: props walk a quarter slower and have 10 health instead of 20 (two sword hits), so they are
  found and taken out faster (`propSpeed`, `propHealth`).
* **Prop Hunt**: swinging at thin air no longer costs hunters health, only points (`airMissDamage` 0);
  hitting the wrong object still costs both. Existing `prophunt.json` files are updated.
* The Bed Wars sidebar shows kills and final kills on one line, so eight teams fit.

## 1.4.0

### Added
* **Bed Wars** (party game, 2–16 players): two to four teams on floating islands with beds as respawn
  anchors, island generators (iron, gold) and diamond and emerald generators that speed up on a schedule,
  a shopkeeper with blocks, weapons, armour that stays on, tool tiers, utility and team upgrades
  (sharpness, protection, forge). Final kills once the bed is gone; the killer takes the victim's
  resources; only player-placed blocks break (TNT included); sudden death destroys every bed, then
  everyone glows; the winners stand on a podium. Sidebar with teams and beds, boss bar with the next
  event. Everything is configurable in `bedwars.json`.
* **Prop Hunt** (party game, 3–16 players): hunters against props disguised as blocks. A hide phase with
  the hunters blinded in a cage, then the hunt. Props become any block from the list, turn solid when
  standing still, lock their rotation, taunt and place decoys on cooldowns. Hunters lose health and
  points for swinging at air or hitting the wrong object, and heal and earn points and arrows for every
  find. Found props join the hunters; rounds rotate who hunts; the most points win. Configurable in
  `prophunt.json`.
* A sidebar and boss bar per match, sent only to its players.

### Changed
* Matches can decide per item what may be dropped.
* The stats menu shows the new modes.

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
