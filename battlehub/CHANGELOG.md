# Changelog

## 1.28.0

### Fixed
* **1v2 and 1v3 practice always used the sword kit**, whatever was chosen. The practice menu now asks for the kit
  (sword, axe or mace, as in the PvP tab), and `/practice 1v2 <level> <kit>` (or `/practice 1v2 mace`) takes it.
* **The Bridge's clay ignored your kit layout**: it was put in slots 4 and 5 after the kit. It is now part of the
  kit (in the team colour), so the kit editor shows it and places it where you want. Layouts made before keep
  working; kits.json gets the clay added automatically.
* A kit layout no longer resets when a kit gets more items: the new items go to their own slot (or the first free
  one) and the rest stays where you put it.
* **Bots built their own path where there already was one** (the path in The Bridge, a bridge in Bed Wars). They
  now walk any way there is and only build where there is none. A failed route search is no longer repeated
  every tick (lighter on the server).

### Changed
* **Bots fight with the weapon of the fight**: the bow in a bow fight (keeping their distance, the sword only when
  you are right on top), the mace in a mace fight with more jump attacks and, now and then, a wind charge at their
  feet to smash down on you; the sword or axe in those fights. `/battlehub bots inspect` shows the weapon, what the
  bot holds, the arrows it shot and the wind charges it threw.

## 1.27.1

### Fixed
* Bots aimed worse than intended while moving themselves (strafing, w-tapping): their own movement was followed
  with the same delay as the target's. They now aim from where their eye is now and only follow the target's
  movement with a delay, as a player who knows how they move.
* "Holding distance" was sometimes chosen without a reason in `/battlehub bots inspect`; every option has one now.
* Inspect no longer shows guessed click habits for an opponent whose swings it has not seen yet.

## 1.27.0

### Changed
* **Bots aim like people, no more lock.** The crosshair follows you with a moment's delay, a limited turning
  speed, a little wobble and an overshoot on big turns, and a hit throws it off. A swing only lands when the
  crosshair is on your hitbox within reach; otherwise it whiffs and the charge is gone. Beginners spam and swing
  from too far, experts wait for full hits; nobody clicks on the exact tick. Against a sprint-strafing player a
  beginner lands about 20%, a master about 80%.
* **Bots see what is on their screen**: behind their back you are gone until they turn round; they hear you walk
  or run close by (not when you sneak) and know who just hit them; name tags through walls only count on screen.
* **Bots fall into the void far less often**: every jump is simulated first and skipped when it would end in the
  void, they slow down when sliding towards an edge, look further ahead at speed, and in the air after a knock
  they hold the keys back towards the ground. Lower levels still forget sometimes.
* **Learning that matters**: bots notice how you click (spam or timed) and how often you land, and fight
  accordingly; they aim better at you once they know how you strafe. All of it is remembered between fights.
* **The adaptive level is a rating from every fight against bots** (practice, filled queues, party games;
  weighted, with the damage share), starting from your ranked rating, aimed at fights you win a little more than
  half the time. In a simulation it finds the right level for far more players than the old "three of four"
  rule (after twenty fights 76 to 100% against 43 to 79%).
* The debrief says how many of its swings the bot landed; `/bots memory` shows your rating and how you click.

## 1.26.0

### Added
* **A think budget for all bots**: when server ticks get slow (config `bots.slowAboveMspt`, default 35 ms) bots
  think less often, up to four times, and step back once there is room again; dangers still make them think at
  once. A crowd of bots wanting to think in the same tick is spread over the next ticks.
* **Metrics** in `/battlehub bots`: bots online and in a match, thinks a second, average and longest think, thinks
  put off, tick time and the current slow-down.
* **`/battlehub bots show <bot>`**: particles only the admin sees for the bot's route, where it is heading, what it
  acts on (its target, the block it digs, mines or builds, a parkour take-off) and its objective, with its tactic
  and reason above the hotbar.

## 1.25.0

### Added
* **Bots play the objective.** KOTH: they hold the hill and fight on it instead of chasing people off. CTF: the
  flag carrier runs home and avoids fights, one bot guards the own flag. Bed Wars: they fetch iron, buy wool at
  their shop, one guards the bed, the others bridge to the nearest enemy bed and break it. The Bridge: one keeps
  the own goal in 2v2, the others go for a goal.
* **Bots build bridges** where there is no way across (Bed Wars islands, a crumbled Bridge path): sneaking to
  the edge, looking at the side of the block and placing it through the normal building rules, no faster than
  their level allows.
* **Bots break through to a bed**: mining takes as long as it would for a player with the same item, and blocks
  players put round the bed are mined first.
* `/battlehub bots inspect` shows the bot's objective (and how many blocks it placed).

## 1.24.0

### Added
* **Spleef bots dig.** They read the floor, dig the last block under someone standing still or where someone is
  running to (as far ahead as their level can judge), never dig their own ground or cut themselves off on a
  small island, keep digging distance and throw snowballs at players next to a hole. Digging goes through the
  same rule as a player's (looking at the block, within reach, in sight).
* **Parkour bots plan their jumps** with real player movement: five ways per jump (walk or sprint, short or long
  run-up) simulated tick by tick, the one with the most room on landing wins. They walk to the start, run, jump
  on the right tick, steer in the air and brake on ice, only pressing keys like a player. A failed way is
  remembered and the next try is different.
* **Bots remember how you fight** across fights and restarts (how you move, which of their tactics worked) and
  the next bot starts from that. `/bots memory` shows it, `/bots forget` wipes it, admins have
  `/battlehub bots forget <player|all>`. Config: `bots.learning`, `bots.learningPersistent`.
* **Adaptive level** in the practice menu and `/practice <mode> adaptive`: three wins out of four go up a level,
  three losses go down, always with a chat line saying why.

### Fixed
* The debrief after practice could be empty when an admin changed the bot's style during the fight; a new style
  now keeps what the bot saw and how the fight went. The killing blow now counts in the debrief too.
* Parkour bots could get stuck at the start.

## 1.23.0

### Added
* **Six bot levels to choose from** (beginner, casual, intermediate, advanced, expert, master) in the practice
  menu, with `/practice <mode> <level>` and in `bots.fillLevel` (easy/normal/hard still work).
* **Bows in the bot mind**: keeping a good distance, strafing while drawing, leading you by the arrow's real flight
  time, backing away while drawing when you close in; bow plus sword: shoot from afar, fight up close.
* **Smarter target choice**: bots fight back against whoever hit them, finish weak targets and stop flicking.
* **A debrief after practice**: the bot tells you what it noticed about your play, how the damage went, its main
  plan and one tip.

## 1.22.0

### Added
* **An AI framework for the bots, first milestone.** In close combat a bot now runs a mind of its own: it only
  knows what a player could (sight, name tags through walls, glowing, memory with fading certainty), scores its
  options (approach, strike, hold distance and punish, back off, heal, dodge arrows, get away from an edge, search,
  the objective) with reasons, keeps a choice instead of dithering, predicts your movement, notices how you
  fight and drops tactics that keep failing.
* **Fighting styles**: rusher, duelist, counter, evasive, opportunist, balanced, improviser and archer, each a set
  of personality numbers the decisions use, with a personal variation per bot (the same bot keeps its style).
* **Six levels behind the scenes** (easy, normal and hard map to casual, intermediate and expert): reaction,
  thinking rate, aim, prediction, techniques and mistakes; never more health or damage.
* Admin commands `/battlehub bots inspect|debug <bot>` and `/battlehub bots profile <bot> <style>`.
* Config: `bots.archetypes`, `bots.personalityVariation`.

### Changed
* Bots no longer know where everyone is: hidden, sneaking players are only found by sight or memory.

## 1.21.0

### Added
* **The Bridge: Chaos is a normal mode now, the fan favourite**: no longer marked as a test, right after The
  Bridge in Duel Games with a glint and a *Fan favourite* badge, and in the mode-of-the-day rotation. Still
  never ranked.
* **A hundred Chaos events**: one every ten seconds. Every match plans at least twenty random ones (rare ones
  less often) plus five that are always there in the first fifteen: a pearl for everyone, low gravity, the
  bridge crumbling, cobwebs and double points. Items, potions, effects, body changes (tiny, giants, moon walk,
  knockback frenzy, freeze, ...), the world (swap, magnet, shockwave, glass and ice bridges, snowstorm, arrow
  rain, bombs), rules (triple points, no building, truce, back home) and fun ones (chicken rain,
  butterfingers). Each event shows as a title and a chat line with its number.
* Config: `combat.snowballDamage`; Chaos settings `eventSeconds` (now 10), `firstEventSeconds`, `randomEvents`.

### Fixed
* Beds in the lobby exploded when you tried to sleep: beds and respawn anchors now do nothing in BattleHub's
  worlds.
* Snowballs (and eggs) did nothing to other players: vanilla throws them for 0 damage and a player ignores a
  0-damage hit entirely. In matches they now hurt (one heart by default) with knockback; in Spleef they push,
  as they were always meant to.

### Changed
* The lobby characters stand further from the fountain (16 blocks instead of 10.5), so the plaza is clearer.

## 1.20.0

### Changed
* **A game with one real player and bots pays nothing**: no coins, no XP, no quest, season pass or achievement
  progress. A tournament won against bots only pays no prize money and counts no (weekly) tournament win.
  `bots.rewardFactor` now only applies when other people play too.

### Added
* **Bots off, the easy way**: joining a queue bots may fill shows a chat line with a **[No bots]** button; while
  waiting the hotbar has a switch next to the leave item (name tag: on, grey dye: off); and `/bots`,
  `/bots on`, `/bots off`. The choice is remembered. With bots off nobody fills in for you, bots already
  waiting with you step out, and a party lobby or 1v2/1v3 queue with someone who said no gets no bots.

## 1.19.1

### Fixed
* The lobby leaderboards were hard to read at night: like every text display they took the light of the world,
  so after sunset they turned dark. They are now always fully lit, on a slightly darker backing for contrast
  against the night sky. The name tags of the lobby characters and the training dummy are fully lit too.
* The lobby characters can be clicked right after joining: missing click boxes are made again within half a
  second instead of at the next five-second sweep.

## 1.19.0

### Added
* **Bed Wars maps from the architecture engine**, dressed in all 13 themes, with decoration that is also
  tactics: the generator under a forge canopy (cover from arrows, open on every side), the bed at the back with
  an arched gate behind it and three free blocks all round to build a defence, the shopkeeper under a market
  stall in the team colour, a lookout platform with a parapet and a stair (high ground over the bridges), ruined
  pavilions with corner cover on the diamond islands, a colonnade with team banners round the emerald
  generators, and landmarks of the theme (castles, mage towers, temple ruins, chapels) on rocks in the distance.
  Every team gets exactly the same island, turned or mirrored; eight teams sit on an octagon. Every map is
  checked before it is placed (points, free zones, routes from the spawn, a free coast towards every target,
  fairness, nothing floating). `bedwars.json` `"mapStyle": "classic"` brings back the old islands.
* **Stands round walled arenas** (duels, team fights, Boxing, King of the Hill, Free for All, Last Man
  Standing): seats on a podium wall, a promenade with lamps and braziers, an outer wall with windows and red and
  blue banners, gatehouses behind the spawn sides, landmarks beyond. Arenas over the void and the Sky and End
  themes get floating islands with trees and lamps instead.
* **Cover pieces from the engine** on generated fields: archways, broken colonnades, monuments, wells, crates,
  market stalls, the same on both halves.
* **Theme particles** drifting round the players in a match.
* Config: `arenas.architecture`, `arenas.ambience`, `bedwars.json` `mapStyle`.

### Changed
* KOTH, Free for All and Last Man Standing arenas now have surroundings too.
* Only the chunks where play happens are kept loaded while an arena stands; landmarks in the distance load and
  unload like any other chunk.

## 1.18.0

### Added
* **A new lobby: the Citadel.** A city on a floating island round the plaza, generated by a new procedural
  architecture engine: a gothic cathedral (north, in view from the spawn), a castle on a hill (east), a temple
  ruin (south) and a mage tower (west), each at the end of an avenue, with town blocks of houses, shops, chapels
  and market halls along the streets, gardens, watchtowers on the cliffs, forests and floating islands. The plaza,
  the spawn, the lobby characters and the leaderboards are where they were. Existing servers get the new lobby
  automatically (the old one is removed first).
* **The architecture engine** (`net.battlehub.arch`): buildings are planned as a brief and volumes first, then
  assembled from a library of reusable modules (walls, eleven kinds of windows from arrow slits to rose windows,
  doors, gothic portals, castle gates, buttresses, roofs as one height field so crossing roofs join, towers with
  spiral stairs and crowns, ornaments, furnished interiors), in roles rather than block names; palettes with
  inheritance and gradients evaluated in building coordinates decide the blocks afterwards, so gradients run on
  across modules and roles never mix. Age and condition (weathering, ruin, plants) follow the architecture. Every
  building and the whole lobby are validated (structure, entrances, stairs, roofs, palettes, a walkable way from
  the spawn to every landmark) before they are placed. Eight archetypes: cathedral, castle, mage tower, temple
  ruin, town house, watchtower, chapel and market hall. See `docs/ARCHITECTURE.md`.
* Config (`lobby`): `style` (`citadel` or `classic`), `seed`, `detail`, `islandRadius`, `townDensity`,
  `discoverModBlocks`.

## 1.17.0

### Added
* **The Bridge 2v2**: a party game for four, red against blue with two on each island. Friends in a party play
  on the same team; bots fill empty places.
* **The Bridge 1v1v1v1**: two to four players, each on their own island round a middle platform with a path to
  every island; score in anyone else's goal. First to four goals wins (`goalsToWin`).
* **The Bridge: Chaos** (test mode, next to the normal Bridge, never ranked): power-ups fall onto the middle every
  12 seconds (`powerupSeconds`) and every 35 seconds (`eventSeconds`) something happens to everyone: low gravity,
  a speed rush, a double goal, the bridge crumbling over the middle, or two wind charges each. Everyone starts
  with a wind charge.

## 1.16.0

### Added
* **A new, much bigger hub.** A marble plaza with a compass rose fading from white to grey, a three-tier
  fountain with four waterfalls under a beacon beam, a balustrade with four gateways (two towers, an arch, a
  hanging lantern and a coloured beacon each) and bridges with lamps and arches to four grand buildings:
  * a **colosseum** (north, in view from the spawn): two rows of arches, seats rising round a sand arena and a
    golden trophy;
  * a **castle** (east): curtain walls, a gatehouse with a portcullis, four round towers with pointed roofs and a
    keep with a flag;
  * a **temple of champions** (south): steps, a ring of quartz columns, a gable roof with a golden emblem, fires
    at the front and a gold, iron and copper podium inside;
  * an **arcane tower** (west): 34 blocks high with spiral windows and amethyst bands, a balcony, a copper spire
    and crystals floating round it.
  Small floating islands with trees fill the gaps. Walls, floors, roofs and the undersides of the islands fade
  through colour gradients. Existing servers get the new hub automatically (the old one is removed first).

### Changed
* Block names in layouts can carry properties (`oak_stairs[facing=north,half=top]`).
* **Bots find their way**: they work out a route round walls, cover and hills (and up steps) instead of running
  straight at you, so they no longer get stuck behind obstacles in the new, more varied arenas.

## 1.15.0

### Added
* **Every generated arena looks different.** Floors follow one of the theme's colour gradients (several per
  theme) in a random pattern: soft clouds, bands, rings, patches or marble veins, with frayed borders like a
  real fade. Walls fade from their foot to their top. Bed Wars, SkyWars and the party games get it too.
* **More variety in shape**: arenas from small to big (radius 13 to 24), spawns closer or further apart, walls
  3 to 6 high, flat to hilly ground, and four new middles (a crater, a plateau, ruins and a floor mosaic). Island
  arenas vary in how many satellites there are, how far away and how high.

## 1.14.0

### Added
* **Almost 250 cosmetics** (from 66), in eleven categories:
  * **Auras** (new): wings (angel, demon, phoenix, frost, soul, rainbow), halos, crowns, orbits, a DNA helix,
    rain, thunder and snow clouds above your head, a galaxy at your feet, a tornado, a royal cape; drawn round
    you in the lobby.
  * **Death cries** (new): a sound where you fall.
  * **Weapon skins** (new): your sword takes the look of a blaze rod, trident, spear, lightsaber, war hammer,
    fish, … in matches (purely the look: it stays the same sword).
  * **Pets** (new): a baby dragon, copper golem, little ghast, beacon, nether star, … floating by your shoulder
    in the lobby.
  * Many more **trails** and **arrow trails**: coloured and fading dust, rainbows, light streaks, block and item
    crumbs, fireflies, sculk souls, copper fire, …
  * More **kill effects**, among them a real **firework burst** and a **lightning strike**, both drawn only on
    the players' screens so they never hurt anyone; more **win effects** (a grand finale of fireworks, a
    thunderstorm, rainbow rings, an aurora, dragon's breath, …).
  * More **hats** and **outfits**, outfits now also fading from top to bottom or with a colour per piece.
* Cosmetic particles can be written in many ways in `cosmetics.json`: `dust:#RRGGBB`, fades, `rainbow`,
  `trail:#RRGGBB`, `block:`, `item:`, `tint:` and combinations with `+`. Existing files get the new cosmetics.

## 1.13.1

### Fixed
* **Items no longer stay behind in the arenas.** Chests (SkyWars loot, anything players placed) are emptied
  before an arena is cleared instead of spilling their contents; items, arrows and XP are swept up after the
  blocks are gone too; and an arena's chunks stay loaded until it is cleared, so nothing is saved in a chunk that
  unloaded mid-game and turns up in the next arena on that spot. Anything left from an older game is removed
  before the next one starts.
* **No more building out of the map.** Build UHC is always a walled field (also in the open themes) with an
  invisible barrier above the wall up to the build height; blocks and buckets only go on the playing field.
  SkyWars has a limit on how far out and how high you can build.
* **Water and lava stay on the playing field**: they never flow over a wall, off an island into the void or out
  of an arena, and buckets aimed at the air are checked like blocks.
* **The item in your hand after a respawn is the one you see.** When the kit selects the first hotbar slot
  (after a death, a new round or the start), the client is told as well; before, your screen still showed the
  old slot (blocks, a bow) that you could not use, while others saw you holding the sword.

## 1.13.0

### Added
* **Weekly tournament**: every week at a set time, with sign-ups, reminders, bigger prizes, a weekly win in
  your stats and the Weekly Champion and Weekly Legend titles (`/battlehub weekly`).
* **Stats export** for a website: players, leaderboards, clans, the season, recent matches, weekly tournaments
  and the server as JSON, an example `index.html`, and an optional built-in web server with CORS
  (`/battlehub export`).

### Fixed
* Titles for seasons, the season pass and prestige now say what they need in the titles menu.
* Bots go round walls and cover instead of running into them.
* Bots no longer stop at the edge of a fountain: shallow water is not a drop, and a bot held back at an edge walks round it.
* Standing still during the countdown no longer counts as being idle.
* Hand-built maps are read from the structure file by BattleHub itself and built like any arena.

## 1.12.0

### Added
* **The Bridge**, **Boxing** and **Build UHC**: duel games with their own rules, in a new Duel Games menu,
  with queues, challenges, ranked, practice against bots and their own ELO.
* **Spleef**, **SkyWars** and **Murder Mystery**: three new party games. SkyWars loot is set in `skywars.json`.
* **Map vote** in every party game lobby: any map, a theme, or a hand-built map.
* **Hand-built maps**: structure files (.nbt) in `config/battlehub/maps/<mode>/` with structure-block
  markers for spawns and game spots.
* Stats per mode moved to their own page (there are 24 modes now).

## 1.11.0

### Added
* **Match intro**: the camera flies over the arena at the start while the mode and the players are shown.
* **Killfeed** on the right of the screen with an icon per kill (games without their own scoreboard).
* **Victory shot**: the camera circles the winner at the end.
* **Double jump** in the lobby.
* **Training dummy** on the plaza with damage (as with a diamond sword), crits, combo, CPS and reach.
* **Wardrobe**: hats and outfits for the lobby in the cosmetics shop (added to existing `cosmetics.json` files).
* **Optional resource pack** (off by default): pixel icons, BattleHub sounds and lobby music, built by BattleHub
  and served on its own port or from your own URL; your own files can be added on top.

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
