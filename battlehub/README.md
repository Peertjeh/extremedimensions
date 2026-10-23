# BattleHub

**A complete PvP and minigame hub for your Fabric server, behind one compass.**

BattleHub brings its **own world**: a grand hub with a marble plaza and four big buildings, built by the mod. Type
`/hub` and you're there with the **BattleHub Compass**; type `/survival` and you're back in the normal
world exactly as you left it. Everything else happens in chest menus: duels (best of 1 or best of 3),
ranked matches, party games such as **Bed Wars** and **Prop Hunt**, a tournament, a cosmetics shop,
spectating and stats. Nobody has to remember a
command, and **vanilla clients can join**: BattleHub runs on the server only.

---

## Features

### Its own world
BattleHub lives in its own dimension, `battlehub:hub`, in a lobby the mod generates itself: **the Citadel**, a
whole city on a floating island, designed by BattleHub's **procedural architecture engine** (see
[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)). At its heart lies the marble plaza, 75 blocks across (compass rose,
three-tier fountain under a beacon, the leaderboards on its balustrade, the lobby characters) with four gateways and
four avenues out to four districts, each anchored by a landmark that faces the plaza:
* a **gothic cathedral** to the north, framed by the north gate as you arrive: nave, aisles, transept, apse,
  flying buttresses, a west front with a portal, a rose window and towers, stained glass and pews inside;
* a **castle** on a hill to the east: curtain walls with wall walks, round, square or octagonal towers
  (crenellated or with cones), a gatehouse with a portcullis, a keep, a great hall and a courtyard with a well;
* a **temple ruin** to the south: a podium, a colonnade or a round tholos, a precinct with a stoa, obelisks,
  broken columns and moss everywhere;
* a **mage tower** to the west: tapering stages with balconies and glowing runes, turrets, a copper or crystal
  crown, floating crystals and a library annex.
Between them, town blocks with dozens of houses, shops, chapels and market halls along streets and avenues,
gardens inside the blocks, watchtowers on the cliffs, forests towards the edge and small islands floating
around. Every district has its own palette (slate roofs by the cathedral, copper by the mage tower...), and
houses between two districts take after the nearer one. Every seed gives a different city with the same plan
(`lobby.seed`), and the classic lobby of 1.16 is still there (`lobby.style: "classic"`). The normal world is
left alone: no compass, no protection, no changed damage rules there.

* `/hub` takes you to BattleHub. Your survival state (inventory, XP, health, hunger, effects, game mode
  and position) is saved to disk.
* `/survival`, the **door** in your last hotbar slot or the button in the menu take you back, with
  everything restored.
* In the hub your XP bar shows your BattleHub level.
* Players moved in or out some other way (an admin `/tp`) are checked in or out automatically.
* For a server that is only BattleHub, `hub.sendToHubOnJoin` puts everyone in the hub when they join.

### Bots: never wait alone
Not enough people online? Bots play along. They are real (simulated) players, so every game works with them,
and they come in six levels, from **beginner** (slow, misses a lot) to **master** (as quick as a fast human,
hardly any mistakes); `easy`, `normal` and `hard` still work and mean casual, intermediate and expert.

**How a bot fights (the AI framework).** In close combat a bot runs a small mind of its own:
* *It only knows what a player could know*: an enemy on screen (within its field of view) in sight, an enemy's name
  tag through a wall (also only on screen, and not while someone sneaks), a glowing enemy, footsteps of someone
  close who walks or runs, who just hit it, or where it last saw someone, with fading certainty. Turn behind it and
  it loses you until it turns round. Health is only known from sight. It never reads anyone's inputs and gets no
  extra health or damage.
* *It aims like a hand on a mouse, not a lock*: the crosshair follows you with a moment's delay, turns no faster
  than a hand, wobbles a little, overshoots a big turn and is thrown off by a hit. A swing only lands when the
  crosshair is really on your hitbox, within reach and with nothing in between; otherwise it whiffs (and loses its
  charge, as a player does). Beginners spam weak clicks and sometimes swing from too far, experts wait for full
  hits but still react a little late. Against someone sprint-strafing a beginner lands about one swing in five, a
  master about four in five; against someone standing still nearly all. It flies back from a hit like a player
  and everyone sees its arm swing, for every hit and every block it mines.
* *The weapon of the fight*: in a bow fight the bow is its weapon (it keeps its distance and only draws the
  sword when you are right on top of it), in a mace fight it fights with the mace, jumps for falling hits and now
  and then throws a wind charge at its feet to come down on you with the smash; in a sword or axe fight it holds
  that weapon. Mixed kits (Free for All, CTF, The Bridge) leave the choice to the situation.
* *It minds the edges*: it looks before it jumps (the jump is simulated with the player's own movement and skipped
  when it would end in the void), slows down when it slides towards an edge, and in the air after a knock it holds
  the keys back towards the last ground, as a player does. Lower levels do not always remember to. Half slabs are
  ground like any block (the paths of The Bridge in the End, Castle or Nether theme), and like a player it walks a
  narrow bridge at an angle as long as its feet are on it.
* *It decides between tactics* (approach, strike, hold distance and punish, back off, heal, dodge arrows, get away
  from an edge, search, the game's objective) by scoring each one with reasons, keeps a choice instead of
  dithering, and lets safety (an edge behind it) interrupt at once.
* *It has a personality*: rusher, duelist, counter, evasive, opportunist, balanced, improviser or archer, each a
  set of numbers (aggression, risk, patience, preferred distance, adaptability, variability, defence, objective)
  the decisions really use, with a small personal variation per bot. A rusher presses; a counter waits just
  outside reach and punishes you for stepping in, but loses its patience against someone who only stands there.
* *It learns during the fight*: it predicts where you move, notices how aggressive you are, how often you change
  strafe direction, how you click (spamming weak hits or timing full ones) and how often you land, and uses it:
  it out-trades a spammer with full hits, keeps its distance from someone who times their hits, presses someone who
  misses a lot, leads a steady strafer all the way and an erratic one less. It drops a tactic that keeps costing it
  more than it brings.
* *Its level* (six: beginner, casual, intermediate, advanced, expert, master; pick one in the practice menu or with
  `/practice <mode> <level>`, `bots.fillLevel` for bots that fill in) sets reaction time, how often it thinks,
  aim, prediction, techniques (crits, w-tap, shield) and how often it misjudges.
* *Bow and blade*: with a bow it keeps a distance the arrow likes, strafes while drawing, leads you by the arrow's
  real flight time (as far ahead as its level can judge) and backs away while drawing when you come close; with
  a sword too it shoots from afar and fights up close. It moves until it can see you instead of shooting cover.
* *Choosing whom to fight*: it fights back against whoever just hit it, finishes weak targets (aggressive and
  opportunist styles more), prefers what it can see and does not flick between two targets.
* *Spleef*: a bot reads the floor round it, digs the last block under someone standing still or where someone is
  running to, keeps its own ground and never digs itself onto a small island, stays at digging distance but not
  next to someone who can dig under it, and throws snowballs at whoever stands next to a hole. It digs like a
  player: looking at the block, within reach, only what it can see.
* *Parkour*: for every jump it simulates a few ways with real player movement (walk or sprint, short or long
  run-up), picks the one that lands with the most room, walks to the start, runs and jumps on the right tick,
  steers in the air and brakes after landing (ice). Ladders it climbs by pushing into them and holding jump.
  It only presses keys like a player. A jump that failed is remembered and the next try uses another way.
* *It remembers you* (if the server allows it): how you move, click and land and which of its tactics worked
  against you, so the next bot starts from that; and how strong you are against bots. `/bots memory` shows what
  bots remember about you, `/bots forget` wipes it.
* *Adaptive level*: pick **Adaptive** in the practice menu (or `/practice <mode> adaptive`). It follows your
  strength against bots, a rating from **every** fight with them (practice, bots filling a queue, party games;
  a duel against one bot counts fully, a fight with more people half), with the damage share as extra signal next
  to winning, and picks the level where you should win a little more than half your fights. Someone new starts
  from their ranked rating when they have one. The chat says after each step why (your rating before and after,
  and your expected chance at the new level).
* *After the fight* it tells you what it noticed (how aggressive you were, the distance you like, how often you
  change strafe direction), how the damage went, what its main plan was, and one tip to work on.
* *Kind to the server*: all bots share a think budget. When ticks get slow (from `bots.slowAboveMspt`, 35 ms by
  default) they think less often, up to four times; dangers (an edge behind them, an arrow) still make them think
  at once, and a crowd of bots wanting to think in the same tick is spread over the next ticks.
  `/battlehub bots` shows how many bots there are, how often and how long they think and the tick time.
* Admins can look inside: `/battlehub bots inspect <bot>` shows its style, current tactic, the best-scored options
  with their reasons, what it learned about its opponent, its objective, its last switches and how long its thinking takes;
  `/battlehub bots show <bot>` draws what it does as particles only you see (its route, where it heads, what it
  acts on, its objective) with its tactic above the hotbar;
  `/battlehub bots profile <bot> <style>` gives it another style; `/battlehub bots debug <bot>` writes its
  decisions to the server log.

* **Filling in**: someone alone in a casual duel queue, a party lobby, 1v2 or 1v3 for 15 seconds gets bots
  (party lobbies up to 4 players). When a second person joins, the bots step out. Never in ranked.
* **No bots, thanks**: joining a queue bots may fill shows one line in chat with a **[No bots]** button, and
  while you wait the hotbar has a switch next to the leave item (a name tag: bots on, grey dye: bots off).
  `/bots off`, `/bots on` or just `/bots` (switch) do the same. The choice is remembered. With bots off you
  only wait for real players, bots already waiting with you step out, and a party lobby or 1v2/1v3 queue with
  someone who said no gets no bots at all.
* **How many bots**: in a party game's menu the **Bots** button (an armor stand) says how many bots you want in
  its lobby: the server's standard (up to 4 players, after the wait), a number of bots (left-click more,
  right-click fewer) or the lobby filled to the game's maximum. A number or a full lobby comes at once; the
  biggest wish of the people in the lobby counts. `/bots standard`, `/bots fill` and `/bots <number>` do the
  same. A private game's owner sets its bots and their level in the `/custom` menu; they join at the start.
* **Practice**: `/practice [mode] [level|adaptive]` or the **Practice** item in the menu (and the
  character on the plaza): a duel, 1v2 or 1v3 against bots, right away.
* They know every game: they fight in duels, Free for All, Sumo and LMS, shoot in Bow and Crossbow PvP, run in
  TNT Run, shoot the floor in Bow Spleef, dig in Spleef, jump the Parkour Race and hide or hunt in Prop Hunt. In
  the games with an objective the same mind weighs the objective against fighting:
  * **KOTH**: they go up the hill and hold it; on it they fight whoever comes up, they don't chase anyone off it.
  * **CTF**: a bot with the flag runs home and only hits who blocks the way; with a team of two or more one bot
    guards the own flag, the others go for the enemy's.
  * **Bed Wars**: they fetch iron from their generator, buy wool at their own shop (same price as a player), one
    of a team guards the bed while it stands, the others build a bridge to the nearest enemy bed and break it
    (mining takes as long as for a player; blocks put round a bed are mined first). A bot that gets within
    reach of an enemy bed breaks it before anything else, even with someone hitting it.
  * **The Bridge**: they head for the other goal, in 2v2 one keeps the own goal. They walk the path (or a bridge
    someone built) and only build their own where there is no way across: sneaking to the edge, looking at the side
    of the block and placing clay like a player.
* **Like a player at a chest** (SkyWars, Hunger Games): a bot opens the chest (the lid opens, you hear it), stands
  still looking at it and takes one stack at a time, closes it, and then puts on better armor one piece at a time;
  lower levels are slower. A hit or an enemy close by makes it close the chest and fight; it comes back later. Its
  arm swings for every hit, every miss and every chest it opens.
* *A learned fight in every melee mode*: a neural network trained outside Minecraft (`battlehub/ml`: a fast
  simulation of the game, PPO with self-play) does the fighting itself: walking, strafing, sprinting, jumping,
  turning and clicking, every two ticks, from what the bot can see. The model (spec v3) knows the ground round it
  (edges, the void, steps and walls, how much ground lies behind its target, to knock it off a bridge or an
  island), both kits (damage per hit against the armor, the weapons' cooldown and knockback, speed, jump, size and
  the like from The Bridge: Chaos' effects), what it carries, an arrow coming at it, a second and a third enemy,
  its nearest team mate (where it is, its health when seen, how far it is from the bot's target), what it remembers
  (since it last saw its target, since it was hurt, since it hurt its target) and its last decisions. It was
  trained in 1v1, against two, three or four at once and in teams of two and three with a shared reward. It fights
  in SkyWars, Bed Wars, the Hunger Games, The Bridge (also Chaos, duos and 1v1v1v1), FFA, 1v2 and 1v3, sumo, the
  axe and Build UHC duels, sword duels and boxing, KOTH, CTF, LMS and the tournaments; mace and bow fights stay
  scripted. The mind still
  decides when to fight, heal, shoot or go for the objective (near an objective the model takes over only for
  enemies within 8 blocks). The model chooses with action masks: never something the bot does not have in its
  hotbar, never food at full hunger. A model that learned its items says which (golden apples and food, pearls,
  wind charges; the bow so far stays the bot's own) and, with `bots.learnedItems`, uses those itself in the fight;
  everything else stays the bot's own rules, as does healing at a distance. The crosshair check of every click
  stays. Each difficulty level has its own model, picked by Elo rating, with a little delay, aim noise and a turn
  limit on the lower levels. `/battlehub bots inspect <bot>` shows what a model decision costs the tick (average,
  99th percentile, maximum) and how many decisions had a team mate and a history. `bots.learnedCombat` turns it off.
* *Fight logs*: every fight (melee and bow games, people and bots) is recorded for training in
  `config/battlehub/fights`, a gzip file per fight: ten times a second, for every fighter, what the model would see
  and what the fighter did (keys, jump, sprint, swings, turning, what they held, the right button), for bots also
  what their model chose, plus the hits and the result. Names are left out unless `fights.names` is on; the folder
  keeps at most `fights.maxMegabytes` (500). `/battlehub fights export` zips the new fights into one file to hand
  over; `battlehub/ml/train/imitate.py` teaches the model what the people in them did.
* *Model reports for operators*: after every match with a learned model, each bot tells the operators online how
  much of its fight went as its model chose, how much its own rules changed (it would walk off an edge or jump into
  the void, it was steered back in the air, it was stuck, it ate) and how much the scripted mind took over (away
  from an edge, dodging, healing, the bow, the objective), which of the model's inputs were new to it (outside
  what the simulation produced in training: creative reach, effects, more enemies, other terrain) and what it
  should practise in the simulation (for example fights at edges on bridges and islands, or effects from Chaos).
  The totals go to `config/battlehub/ml_feedback.json`; `battlehub/ml/train/selfplay2.py --focus` trains on them.
  `/battlehub bots report` shows the totals, `/battlehub bots report off` stops them in your chat,
  `bots.modelReport` turns them off for everyone.
* Their names start with `Bot_`. Nothing about them is saved (no player files, no profiles), they never show
  up on a leaderboard, and their join and leave messages stay out of chat.
* **One real player with bots earns nothing**: no coins, no XP, no quest, pass or achievement progress, and
  a tournament won against bots only pays no prize. With other people and bots in the game everyone gets half
  the coins and XP. Matches with bots never count for stats, streaks or ELO, so they cannot be farmed.

### Quests, season pass, achievements, prestige
* **Quests**: three new ones every day and three every week (`/quests`), different for every player: play,
  win, kills, damage, ranked wins, the mode of the day, being MVP, or a specific mode. They pay coins, XP and
  season pass XP.
* **Login streak**: the first visit to the hub each day pays coins, more for every day in a row (50 up to 400).
* **Season pass** (`/pass`), free for everyone: 50 tiers per ranked season, pass XP from every match and every
  quest; coins on every tier, more on every fifth, a cosmetic you don't own yet on every tenth. Rewards are given
  right away.
* **Achievements** (`/achievements`): twelve goals with tiers (Fighter, Winner, Duelist, Free Runner, Bed
  Breaker, Collector, ...); every tier pays coins once.
* **Prestige**: at the top level, start again at level 1 with a star before your name and 5% more coins per
  prestige (up to 50%).

### The show: intros, killfeed, victory shot
* **Match intro**: when a match starts, the camera flies over the arena while the mode and the players are shown
  (only your view moves; you stay at your spawn).
* **Killfeed** on the right of the screen in every game without its own scoreboard: the last kills with an
  icon for how they happened (sword, bow, a fall), plus a line in chat.
* **Victory shot**: at the end the camera circles the winner while the win effects go off.

### Lobby extras
* **Double jump** everywhere in the lobby (press jump twice), with a short cooldown.
* **Training dummy** on the plaza: each player sees their own numbers above it: what the hit would have done
  with a diamond sword (the attack cooldown and critical hits count), the combo, clicks per second, reach and
  the average. A number floats up from every hit; right-click to start over.
* **Wardrobe**: hats (glass, slime, TNT, a beacon, a dragon head, ...) and outfits in a colour, worn in the lobby
  only, in the cosmetics shop.

### Optional resource pack
Off by default. When switched on, players get it offered when they come into BattleHub and may say no:
everything works without it. With it they get pixel icons in the killfeed and on the dummy, BattleHub sounds
(intro, kill, hit, double jump) and lobby music. BattleHub builds the pack itself and can serve it on its own
port, or you host the zip from `config/battlehub/resourcepack/` yourself. Put your own `.ogg` sounds, textures or
a `pack.png` in `config/battlehub/resourcepack/overlay/` and they are added to the pack.

### Friends, parties, clans and private games
* **Friends** (`/friend add <name>`): a friends list that shows who is online and what they are doing (in the
  lobby, waiting, playing Bed Wars, ...). Your friends hear it when you come into BattleHub.
* **Parties** (`/party invite <name>`, up to 8): when the leader joins a party game, a 1v2 or a 1v3 on the team
  side, the rest of the party comes along, and in Capture the Flag and Bed Wars the party ends up on the same
  team. `/pc <message>` for party chat.
* **Clans** (`/clan create <name> <tag>`, costs 1000 coins, up to 20 members): the clan tag in front of your
  name, `/cc <message>` for clan chat, a point for every win of a member (three for a ranked win), a clan
  leaderboard, and a **weekly clan war**: the clan with the most points in a week gets coins for every member.
* **Private games** (`/custom`): open a lobby for any duel or party game and get a four-letter code; friends
  join with `/custom join <code>`, you start it. The owner can add bots (a number, or as many as fit) and pick
  their level. Private games never count for stats, rewards or ELO.

### Ranked seasons
Ranked runs in **seasons** of 8 weeks. Every rating has a **division** inside its tier (Gold III, Gold II,
Gold I). The first 5 ranked matches per mode in a season are **placement matches** that count double. When a
season ends, your peak rating decides your reward (coins per tier) and the season titles (Season Veteran, Gold,
Diamond and Netherite Season); then every rating moves halfway back to the start. The Ranked menu shows the
season, the days left and how your last season ended. A season that ends while the server is off is closed at
the next start.

### After every match: a recap
Damage dealt and taken (in hearts), hits and swings with your accuracy, your longest combo, arrows shot and hit,
and the **MVP** of the match.

### Kit editor
`/kits` or **Kit editor** in the menu: put the items of every kit where you want them (click an item, then its
new place). Used every time that kit is handed out.

### Fair play
* **Leaving early** (disconnect or `/hub` while a match with at least two people runs) blocks queueing for a
  while: 30 seconds, then 2, 5, 15 and 30 minutes within a day. Matches with bots don't count.
* **Idle players** are taken out of a match after 90 seconds and out of a queue after 5 minutes (props in Prop
  Hunt may stand still, of course).
* **No teaming** in Free for All and Last Man Standing: two players who stay together for a long time, fighting
  others but never each other, are warned and glow for a while.
* **Alerts for admins** (never automatic punishments): hits from too far away, clicking faster than a hand
  can, hovering in the air, moving faster than possible. Logged and shown to online admins.
* `/report <player> <reason>`: saved in `world/battlehub/reports.log` with the match the player was in and
  shown to online admins; `/battlehub reports` lists the latest.

### Lobby characters and the mode of the day
Eight characters stand in a ring around the fountain, between it and the planters (22 blocks out, so the plaza
stays easy to walk through): PvP, Free for All, Ranked, Practice, Bed Wars, Prop Hunt, Party Games and Parkour. Above each: its name, how many people play it right now and a click hint; their heads turn
towards you when you come close. Click one to go to that game.

Every day another mode is the **mode of the day** with **double coins**: announced when you enter the hub,
starred in the menus and on its character.

### Leaderboards in the lobby
Floating boards hang above the plaza wall: **total kills** and **total deaths** over every BattleHub game
on the north-west wall (between the ice and castle bridges), and the **best parkour runner** (most parkour
races won) in the middle of the south-west wall (between the nether and ice bridges). Top 10 each.

Each player sees the boards in their own language, with their own row highlighted and their own place
at the bottom. The boards (and the name tags of the lobby characters) are lit as at noon whatever the time of
day, on a dark backing, so they read as well at night as by day. The boards are sent to the players' clients
only: nothing is placed in the world, and nothing is left behind when the mod is removed. Other boards (K/D, coins earned, wins, ELO, per mode or
ranked, switching modes every few seconds) can be set in `config.json` (`leaderboards`).

### One compass, seven menus
| | |
|---|---|
| ⚔ **PvP** | Free for All, 1v1, 1v2 and 1v3, each with the sword, axe or mace kit |
| 🏹 **Ranged** | Bow PvP, Crossbow PvP |
| 🎮 **Party Games** | Bow Spleef, TNT Run, Sumo, King of the Hill, Capture The Flag, Parkour Race, Last Man Standing, **Tournament** |
| ✨ **Cosmetics** | Almost 250: trails, auras (wings, halos, clouds), kill and win effects with real fireworks and lightning, death cries, weapon skins, pets, hats, outfits and name colours, bought with coins |
| 🏆 **Ranked** | Duels that change your ELO, from Bronze to Netherite |
| 👁 **Spectate** | Every running match, live, with a **Join Spectator** button |
| 📊 **Stats** | Wins, losses, kills, deaths, streaks, level, coins, title, ELO per mode, parkour records, leaderboards (also on the lobby wall) |

The compass always sits in the same hotbar slot and cannot be dropped or moved. Every submenu has a back
button. In a mode menu you choose **Queue** (random opponent) or **Challenge** (pick a player).

### PvP: Free for All, 1v1, 1v2, 1v3
The **PvP** button opens four choices, and after that the kit (sword, axe or mace):
* **Free for All**: picking a kit joins the Free for All lobby with a vote for that kit; the most votes
  decide.
* **1v1**: the duels below.
* **1v2** and **1v3**: one player against a team of two or three. Every kit has its own queue, where you
  choose to play **alone**, **in the team**, or **either** (whatever starts a match sooner). The team has no
  friendly fire and loses when everyone in it is down; the arena grows with the number of players.

### Duels: best of 1 or best of 3
1v1 with fixed kits (editable): sword with golden apples, axe and shield, mace with wind charges
(on extra large floating islands), bow with a back-up sword, and crossbow. Five-second countdown, then
fight. A totem of undying in a kit works as in vanilla.
Dying doesn't show a death screen: you're out, and afterwards everyone gets their own inventory,
health, hunger, game mode and position back.

Pick **best of 1** or **best of 3** with the button in the duel, ranked and challenge menus (it is
remembered). In a best of 3 the loser of a round gives the opponent a point; both go back to their
spawn with a fresh kit and a short countdown. When a round's time runs out, more health wins it. The
series counts as one match for stats, coins and ELO. Queues are separate per format.

### Duel games: The Bridge, Boxing, Build UHC
In their own menu (Duel Games), with queues, challenges, ranked, practice against bots and their own ELO:
* **The Bridge**: red against blue, each on an island with a goal. Build across with clay and jump into the
  other goal to score; dying only sends you home. First to five goals wins.
* **The Bridge: Chaos** *(★ fan favourite, never ranked)*: the Bridge with a glowing power-up falling onto the
  middle every few seconds (wind charges, an ender pearl, a golden apple, snowballs, cobwebs, arrows, extra clay
  or a knockback stick) and **an event every ten seconds**, from a pool of **172**. Every match plans 25
  of them: twenty drawn at random (rare ones less often), plus five that are in every match, all within the
  first fifteen: **a pearl for everyone, low gravity, the bridge crumbling, cobwebs and double points**.
  After those 25 **the same events come again, twice as bad**: they last longer, effects are stronger, you get
  twice as many items, body changes are twice as big (within what a body can take), there are twice as many
  bombs, arrows and chickens, pushes are harder. After 50 events four times as bad, after 75 eight times, and so on.
  The events are of six kinds, and two of the same kind never follow each other when it can be avoided:
  * *items*: wind charges, snowballs and eggs (they hurt), golden and Notch apples, a fishing rod, a knockback
    stick, a Punch bow, a mace, a loyal trident, a multishot crossbow, chorus fruit, slime, honey, ice, glass
    and scaffolding to build with, a totem, potions to throw or drink, a lucky draw, wings with rockets;
  * *effects*: speed rush, slow motion, haste, strength, weakness, regeneration, super jump, blindness,
    darkness, ghosts (invisible), spotlight (glowing), lift off, golden hearts, berserk, tanks and more;
  * *body*: tiny, giants, moon walk, heavy, long arms, long reach, knockback frenzy, anchored, springs, glass
    cannon, juggernaut, spam click, mice, slow giants, trampoline, freeze and more;
  * *the world*: launch, swap places, magnet, shockwave, a glass bridge that shatters, an ice bridge, a
    snowstorm, arrow rain, erosion, bombs, a thunderstorm;
  * *rules*: double and triple points, hands off the clay (no building), truce (no hitting), full heal, back
    home, restock, danger zone (three hearts);
  * *just for fun*: fireworks, chicken rain, butterfingers (hotbar shuffled), hot feet.
  Each event shows as a title and a line in chat ("event 7 of 25"; later "event 31, 2x as bad", with "x2" in
  the title). A goal ends every running event; a player
  who dies gets the running effects back when they return. In the Duel Games menu Chaos has a glint and a
  *Fan favourite* badge, and it is in the mode-of-the-day rotation. The normal Bridge stays as it is.
* **Boxing**: no damage, only knockback; the first to land 100 hits wins the round. Combos are shown.
* **Build UHC**: blocks, lava and water buckets, a rod, a bow and golden apples. No natural healing: only
  golden apples heal. Only blocks players placed can be broken. You build inside the walls, up to the build
  height (`buildHeight`); an invisible barrier above the wall keeps players and water in.

### Play again
After every match a **[Play again]** button appears in chat and a lime dye in your first hotbar slot,
for 30 seconds. It queues you for the same mode, format and ranked setting, asks the same opponent for a
**rematch** after a challenge, or puts you back in the same party game lobby.

### Party games (2–16 players)
* **Bow Spleef**: arrows burst the floor; fall and you're out.
* **TNT Run**: blocks vanish behind you, across three floors.
* **Sumo**: knockback only, no damage; stay on the platform.
* **King of the Hill**: hold the top of the hill alone to score.
* **Capture The Flag**: red against blue, three captures to win, respawns after a short delay.
* **Parkour Race**: a new course every race, with checkpoints and a timer; ranked by finish time.
* **Last Man Standing**: free-for-all inside a shrinking border.
* **Spleef**: dig the snow away under the others with a fast shovel; every block gives a snowball to push with.
* **SkyWars** (2–12): a big map, every match a different size: 1.5 to 2.5 times as wide as in 1.29, the
  islands, the middle and the bridges alike. Everyone starts on their own island with two chests (always enough
  blocks to bridge to the next island). A ring of smaller middle islands with better loot lies beside the ways
  in (never on the straight way from a spawn island to the middle), and in the middle a super big island rises
  in two terraces to a pavilion: eight chests with the best loot, twelve when the middle is very big (diamond
  gear, pearls, golden apples, a totem now and then). Each tier has its own list in `skywars.json`. Build,
  break, fight; whoever dies drops everything. You can bridge between the islands, but not far out into the
  void or high into the sky. Bots loot their island, put on the armor they find and bridge to the next chests;
  they do not walk off the edge.
* **Hunger Games** (2–16): the lobby city is the map. Every week (Monday to Sunday) a new city is generated from
  the lobby's plan: the same plaza, avenues, landmarks and town, but other houses and streets; every match dresses
  it in another theme that suits a city (as built, a desert city, winter, cherry blossom, nether, badlands,
  jungle, deep dark, End). The theme only changes the materials: every block stays where it is, so you learn
  the city during the week. Everyone starts empty-handed on the dark ring of the plaza round the cornucopia (a
  ring of twelve chests with the best loot). Every house has its chest and barrel, the castle, the cathedral, the
  mage tower, the temple ruin, the chapels and the watchtowers have better chests inside. The doors are taken out,
  so you (and the bots) walk in and out. The first thirty seconds nobody can hurt anyone; after ten minutes all
  chests are filled again; after five minutes a border closes in on the plaza. The city cannot be broken (only
  what you put down, leaves, cobwebs and plants); whoever dies drops everything and a cannon sounds. The last one
  alive wins, when time is up the most kills. Bots loot the cornucopia and the houses near them, put on armor,
  eat when hungry and head for the plaza. Loot lists in `hunger_games.json`.
* **The Bridge 2v2**: red against blue with two on each island; score together, no friendly fire.
* **The Bridge 1v1v1v1** (2–4): everyone on their own island (red, blue, green, yellow) round a middle platform;
  jump into anyone else's goal to score. First to four goals wins.
* **The Bridge 1v1v1v1: Chaos** (2–4, never ranked): the 1v1v1v1 with everything of The Bridge: Chaos: a
  power-up on the middle platform every few seconds and an event every ten seconds, the same 172 events, which
  come back worse every round.
* **Murder Mystery** (4–16): one murderer (a sword after ten seconds, one hit kills), one detective (a bow
  with an arrow back after every shot), the rest innocent. Gold lies around: ten gold buys a bow. Shoot an
  innocent and you die too. The innocents win when the murderer is shot or time runs out.
* **Bed Wars** and **Prop Hunt**: see below.
* **Free for All** (in the PvP menu): everyone against everyone with respawns and spawn protection; the
  first to the kill target (15) wins, or the most kills when time runs out.

Each game has a lobby with a minimum and maximum player count. A countdown starts once the minimum
is reached, and gets shorter when the lobby is full.

**Map vote**: in every party game lobby you can vote for the map: any, one of the themes, or a hand-built map.
The most votes win.

### Hand-built maps
Build a map, save it with a structure block and put the `.nbt` file in `config/battlehub/maps/<mode id>/`
(or `maps/all/` for every game that takes hand-built maps), then `/battlehub reload`. Mark the spots with
structure blocks in **Data** mode: `spawn` (or `spawn:90` to face west; `0` faces south), `spectator`, and the
game's own spots. The markers are not built. Only blocks are taken over: what is inside chests or written
on signs is not (SkyWars fills its chests itself).
* Duels, Boxing, Build UHC, Free for All, Last Man Standing, Sumo, Spleef, SkyWars, Murder Mystery: spawns
  (duels need two). SkyWars fills chests marked `chest`, `mid_chest` and `center_chest`; Murder Mystery drops gold on
  `gold` spots (anywhere on the floor without them); Build UHC builds inside `build_min`/`build_max` (the whole
  map without them).
* The Bridge: two spawns (red first) plus `goal_red`, `goal_blue`, `build_min` and `build_max`.
* King of the Hill: spawns plus `zone`.

Without a vote, a hand-built map is used half of the time when there is one (`arenas.customMapChance`).

### Bed Wars (2–16 players)
In the lobby you vote for **solo** (everyone for themselves) or **duos** (teams of two). Every player or
team starts on their own floating island (up to eight) with a **bed**, a **generator** and a
**shopkeeper**. The island generator drops iron
and gold; diamond islands between the teams and emerald generators in the middle get faster on a
schedule (diamond II at 6:00, emerald II at 12:00, and so on). Spend it in the **shop**: wool, glass,
end stone and more to build bridges, swords and bows, armour that stays on, pickaxes and axes in tiers,
golden apples, TNT, ender pearls, plus **team upgrades** (sharpness, protection, a faster forge).
While your bed stands you respawn a few seconds after dying; once an enemy breaks it, your next death is
a **final kill**. Whoever kills you takes your resources. Only blocks that players placed can be broken
(TNT too); you cannot build right next to spawns, shops and generators. At 25:00 **sudden death**
destroys every bed, at 27:00 everyone glows. The last team standing wins and stands on a golden podium.
A sidebar shows the teams and beds, a boss bar the next event.

The maps are built by the architecture engine, dressed in the arena's theme, and every map is new:
* **Your island**: the spawn on a square in your team colour, the generator under a **forge** canopy (an anvil, a
  blast furnace, lamps; it covers you from arrows while you collect, but is open on every side), the bed on a
  wool plinth at the back with a **gate** behind it (an arch with team banners; nothing stands within three
  blocks of the bed, so you can build your defence all round it), the shopkeeper under a **market stall** with a
  striped awning in your colour, and a **lookout**: a platform four blocks up with a parapet and a stair, high
  ground over the bridges coming in. Trees, lamps and plants fill the rest, but never the lanes: every island
  keeps a free stretch of coast towards the middle, its neighbours and its two nearest diamond islands.
* **Diamond islands**: a ruined pavilion, the same on all four sides: pillars on the corners, broken lintels,
  knee-high cover beside each pillar and gaps three wide to run through.
* **The middle**: a colonnade of eight columns under a round entablature with a banner for every team, a
  compass in the floor, pedestals with lamps as cover between the emerald generators, and a crystal floating
  high above.
* **Around it**: landmarks of the theme on big rocks in the distance (a castle, a mage tower, a temple ruin, a
  chapel...) and small floating islands with trees, outside the area you can build in.
* **Fair**: every team gets exactly the same island, turned or mirrored (eight teams sit on an octagon so every
  island faces the middle alike). The map is checked before it is placed: the spawn, bed, generator and shop
  can be walked to, the free zones are free, every lane has a coast to bridge from, and nothing hangs in the air.

The plain islands of earlier versions are still there: `"mapStyle": "classic"` in `bedwars.json`.

### Prop Hunt (3–16 players)
Each round some players are **hunters** and the rest are **props**. Props get 30 seconds to hide while
the hunters wait blind in a glass cage. Props are a quarter slower and have half the health of a hunter.
Right-click any everyday block in the market square (or use the
disguise list) to *become* it. Stand still for a moment and the prop turns **solid**: a real-looking
block on the grid. Props can **lock their rotation**, **taunt** (a sound, for points) and drop a
**decoy** copy of their block, each on a cooldown. Then the hunt is on: hunters hit props with their
sword or shoot them, but **hitting the wrong object costs health and points, and swinging at thin air costs points**;
finding a prop heals them and earns points and arrows. Found props join the hunters. The round ends when
time is up or every prop is found; after all rounds (2 by default, everyone hunts in turn) the most
points win.

### Tournament (up to 16 players)
In the tournament lobby you vote for the **team size (1, 2, 4 or 8)** and the **kit** (one of the duel
kits, or random). When it starts, the players are split into random teams (smaller ones when there are
fewer players) and play a **knock-out bracket**: semi-finals, final, and so on, with a bye in the first
round when the number of teams is not a power of two. **Every match is best of 3 rounds** in the kit's
duel arena, made bigger for bigger teams; a round is won when nobody of the other team is standing. No
friendly fire. Matches of a round are played side by side; while you wait you can watch the others.
`/hub` or the leave item leaves the tournament; a team with nobody left loses by walkover. The
champions get 500 coins each (runners-up 150) and a tournament win in their stats.

### Weekly tournament
Every week at a set time (Saturday 20:00 by default) BattleHub runs a tournament on its own. Sign-ups open
15 minutes before with an announcement and a **[Sign up]** button (or `/battlehub weekly join`), with
reminders at 5 and 1 minute. Everyone who signed up and is free in BattleHub plays; with too few players it is
called off. On top of the normal prizes the champions get 2000 coins (runners-up 500), a weekly win in their
stats and the **Weekly Champion** title (five wins: **Weekly Legend**). It runs once a week, also across
restarts, and the results are kept for the website export. `/battlehub weekly` shows when the next one is.

### Stats for a website
Switch on `export` in `config.json` and BattleHub writes JSON files every few minutes (to
`config/battlehub/export/` or a folder you choose, for example your web server's): `players.json` (every
player's level, coins, stats per mode, ELO, clan, records), `leaderboards.json` (kills, wins, K/D, level,
prestige, coins earned, tournaments, weekly wins, ELO per duel mode, wins per mode, parkour times),
`clans.json`, `season.json`, `matches.json` (the last 100 matches), `tournaments.json` (the weekly
tournaments) and `server.json` (who is online, matches per mode, the mode of the day). An `index.html` that
shows them is written once; change it as you like. With `serveHttp` on, BattleHub serves the folder itself
on its own port, readable by a website on another address (CORS). `/battlehub export` writes them right away.

### Parkour: always unique
Every race is a newly generated course: **30, 50, 70 or 90 jumps** for **easy, medium, hard or
extreme**. Courses mix ladders, half-block slab steps, fence posts, slippery ice, 1×1 landings,
diagonal jumps and turns. When you join you vote for a difficulty (or random); the majority wins.
Every jump is checked while generating against what a player can reach at that difficulty, and parts
of the course are kept out of each other's reach, so there are no shortcuts. Gold platforms are
checkpoints. You can also run a course **alone**: finishing counts as a win, and your best time is
kept per difficulty. Easy and medium courses give XP but no coins.

### A new arena for every match
Arenas are generated per match, in **13 themes**: Forest, Castle, Nether, Ice, Sky, Desert, Jungle,
Cherry Grove, End, Mushroom, Badlands, Deep Dark and Ocean. The shape (circle, square, octagon,
hexagon, ellipse, cross), size, hills, obstacles, cover and scenery are random. Duel arenas are
**point-symmetric**, so both players get exactly the same ground and cover. Sky and End are floating
islands. Party games get random shapes, hills and cover too.

Every arena also gets its own **look**: one of the theme's colour gradients (stone to polished andesite, the
badlands terracotta bands, waxed copper from fresh to green, ...) laid out in a random pattern (clouds, bands,
rings, patches or marble veins) with frayed borders, walls that fade from foot to top, and a random middle (a
hill, pillars, a fountain, a monument, a crater, a plateau, ruins or a floor mosaic). How big the arena is, how
far apart the spawns are, how high the walls are and how hilly the ground is all change per match, as do the
number, distance and height of the floating islands.

**Immersion**: walled arenas (duels, team fights, Boxing, King of the Hill, Free for All, Last Man Standing)
stand in the middle of **stands**, as in a real arena: rows of seats on a podium wall, a promenade with lamps
and braziers, an outer wall with windows, battlements and banners in red and blue, a gatehouse with two towers
behind each spawn side, and landmarks of the theme on rocks beyond, where the spectators look. Arenas over the
void and the open themes (Sky, End) get small floating islands with trees and lamps, and landmarks further out.
The cover on the field includes pieces from the architecture engine (archways to fight through, broken
colonnades, monuments on stepped plinths, roofed wells, stacked crates and barrels, market stalls), always the
same on both halves. A few particles of the theme drift round the players: fireflies in the forest, spores in
the nether and the jungle, snow on ice, petals under the cherry trees, souls in the deep dark.
(`arenas.architecture` and `arenas.ambience` switch these off.)

You don't have to build anything. Matches run in their own empty dimension (`battlehub:arenas`), each
in its own slot, so many matches can run at the same time. Every arena is cleaned up afterwards.

### Cosmetics
Spend your coins in the **Cosmetics** shop (compass menu or `/cosmetics`):
* **Trails**: particles behind you while you walk: flames, hearts, cherry blossom, coloured dust, fading
  colours (Inferno, Frostbite, Void), light streaks, a rainbow, Midas Touch, diamond rain, …
* **Auras** in the lobby: angel, demon, phoenix, frost, soul and rainbow **wings**, halos, crowns, orbits, a DNA
  helix, rain, thunder and snow **clouds** above your head, a galaxy at your feet, a tornado, a royal cape, …
* **Arrow trails**: particles behind your arrows (rainbow and laser arrows too)
* **Kill effects**: lightning, a real **firework burst**, a **lightning strike**, shatter, sculk shriek, gust,
  confetti, sonic boom, jackpot, … where you defeat someone
* **Win effects**: a **grand finale** of fireworks, a **thunderstorm**, rainbow rings, dragon's breath, an aurora,
  confetti, sakura, gold rain, … around you while the match ends
* **Death cries**: a sound where you fall (a screaming goat, a dragon roar, a war horn, …)
* **Weapon skins**: your sword takes the look of another item in matches: a blaze rod, a trident, a spear, a
  lightsaber, a war hammer, a fish… It is still the same sword, so it changes nothing in the fight.
* **Pets**: a baby dragon, a copper golem, a little ghast, a beacon, a nether star, … floating by your shoulder
  in the lobby
* **Hats** and **outfits** (the wardrobe), outfits also fading from top to bottom (Sunset, Ocean, Fire, …)
* **Name colours**: on your nametag, in chat and in the tab list while you're in BattleHub

One cosmetic per category is active; click to buy, use or turn it off. Everything is vanilla particles,
models and sounds, purely visual, and only in BattleHub's worlds; fireworks and lightning are only drawn on the
players' screens, so they never hurt anyone. Prices and the list are in `cosmetics.json`.

### Progression
* **Coins** for winning, playing and kills.
* **XP and levels**, with a configurable curve and a coin bonus every few levels.
* **Titles** unlocked through levels, wins, win streaks, ELO or bought with coins. The selected title
  shows in chat, above your head and in the tab list while you're in BattleHub.
* **Cosmetics** bought with coins (see above).
* **Win streaks**, overall and per mode. Milestones (5, 10, 25, …) are announced to everyone.
* **ELO** per ranked mode (start 1000, K-factor 32), with tiers and a top 10 per mode.

### Spectating
Watch any running match in spectator mode, kept inside the arena. Spectators (also players who are out or
waiting to respawn) cannot open chests or take anything out of them. Leave with `/hub` or the clickable
**[Leave]** in chat. When the match ends, spectators go back to the lobby automatically.

### Solid by design
* Your survival inventory never mixes with BattleHub: it is saved to disk when you enter and given
  back when you leave, also after a crash.
* A disconnect counts as giving up; the opponent wins. The player gets their items back on the spot,
  and their position the next time they join.
* Inventory snapshots are also saved to disk. If the server crashes in the middle of a match, the
  player gets everything back on the next join.
* When the server stops, every match ends cleanly and every player is restored.
* Nothing can be taken out of a BattleHub menu: not by clicking, not with inventory-sorting mods, not
  in creative mode.
* No item drops from kits, no block breaking in matches or the hub, no damage in the hub.
* A player is never in two matches or queues at once.

---

## Installation

1. Install **Fabric Loader 0.19.5+** for **Minecraft 26.3** on your server (Java 25).
2. Put **Fabric API** and **BattleHub** in the `mods` folder.
3. Start the server. The hub world is built on the first start; players get there with `/hub`.
   Optionally stand somewhere in the hub and run `/battlehub setlobby` to move the spawn point.

Players don't need any mods. BattleHub also works in singleplayer and on LAN.

---

## Commands

| Command | Who | What |
|---|---|---|
| `/hub` (or `/lobby`) | everyone | Go to BattleHub; inside it: back to the hub spawn (leaves a queue, stops spectating, or gives up the current match) |
| `/survival` | everyone | Leave BattleHub: back to the normal world with your own inventory and position |
| `/duel <player> [mode] [bo1\|bo3]` | everyone | Challenge a player (opens a mode menu without a mode) |
| `/duel accept <player>` / `/duel deny <player>` | everyone | Answer a challenge (or click [Accept] / [Deny] in chat) |
| `/stats [player]` | everyone | Stats menu for yourself or another player |
| `/cosmetics` | everyone | The cosmetics shop |
| `/spectate [player]` | everyone | Spectate menu, or watch the match that player is in |
| `/spectate leave` | everyone | Stop spectating |
| `/quests` / `/pass` / `/achievements` | everyone | Quests, the season pass, achievements and prestige |
| `/kits` | everyone | The kit editor |
| `/report <player> <reason>` | everyone | Report a player to the admins (once a minute) |
| `/friend` / `/friend add\|accept\|deny\|remove <player>` | everyone | Friends and their status |
| `/party invite\|accept\|kick <player>` / `/party leave\|disband` / `/pc <message>` | everyone | A party that plays together, and party chat |
| `/clan` / `/clan create <name> <tag>` / `/clan invite\|kick <player>` / `/clan join <clan>` / `/clan leave\|disband` / `/cc <message>` | everyone | Clans and clan chat |
| `/custom` / `/custom create <mode>` / `/custom join <code>` / `/custom start\|leave` | everyone | Private games with a code |
| `/practice [mode] [level\|adaptive] [kit]` | everyone | Practice against bots (sword, axe, mace, bow, crossbow, 1v2, 1v3) at a level (beginner .. master) or adaptive; 1v2 and 1v3 with a kit (sword, axe or mace; without one the kit choice opens); without a mode: the practice menu |
| `/bots` / `/bots on\|off` | everyone | May bots fill in the queues you wait in? Without on/off: switch |
| `/bots standard\|fill\|<number>` | everyone | How many bots you want in party lobbies: the server's standard, a full lobby, or that many (at once) |
| `/bots memory` / `/bots forget` | everyone | What bots remember about how you fight and your results against them; wipe it |
| `/battlehub` | everyone | Open the main menu |
| `/battlehub join <mode>` | everyone | Join a party lobby or duel queue (used by invites) |
| `/battlehub again` | everyone | Play again (same as the chat button or the lime dye) |
| `/battlehub exit` | everyone | Same as `/survival` |
| `/battlehub setlobby` | admin (level 2) | Set the hub spawn to your position (in the hub world) |
| `/battlehub lobby rebuild` | admin | Build the hub again (after it was damaged or changed) |
| `/battlehub world reset [new]` | admin | Reset BattleHub's world: everyone in the lobby goes back to the normal world, the whole lobby area is emptied and the lobby built again from scratch; `new` also gives it a new seed (another city on the same plan). Matches go on; everybody hears when it is ready |
| `/battlehub reload` | admin | Reload the config, kits, titles and language files |
| `/battlehub reports` | admin | The latest reports since the server started |
| `/battlehub bots` / `/battlehub bots clear` | admin | How many bots are on and what they cost the server (thinks, think time, tick time, slow-down); take them all off |
| `/battlehub bots show <bot>` | admin | See what a bot does as particles only you see (again: off) |
| `/battlehub bots inspect\|debug <bot>` / `/battlehub bots profile <bot> <style>` | admin | What a bot thinks and why; log its decisions; give it another fighting style |
| `/battlehub bots forget <player\|all>` | admin | Wipe what bots remember about a player, or about everyone |
| `/battlehub fights [export\|clear]` | admin | The fight logs for training: how many wait; zip the new ones into one file (`config/battlehub/fights/export-<time>.zip`); delete them all |
| `/battlehub bots report [on\|off\|reset]` | admin | How the learned bots kept to their models since the totals began, per spec and mode, and what to practise; your chat reports after matches on or off; start the totals again |
| `/battlehub matches` | admin | List running matches |
| `/battlehub endmatch <id>` | admin | Stop a match without results and restore its players |
| `/battlehub arena list` | admin | Arena themes, which modes use them, slots in use |
| `/battlehub coins <player>` | admin | Show a player's coins |
| `/battlehub coins give\|take\|set <player> <amount>` | admin | Change a player's coins (also offline; never below zero) |
| `/battlehub weekly` / `/battlehub weekly join\|leave` | everyone | When the weekly tournament is; sign up or out |
| `/battlehub weekly open <seconds>` | admin | Open sign-ups now, starting in that many seconds |
| `/battlehub export` | admin | Write the stats files for the website now |

Mode ids: `sword`, `axe`, `mace`, `bow`, `crossbow`, `bridge`, `boxing`, `build_uhc`, `bow_spleef`, `tnt_run`,
`sumo`, `koth`, `ctf`, `parkour`, `lms`, `ffa`, `tournament`, `bed_wars`, `prop_hunt`, `spleef`, `skywars`,
`murder_mystery`, `1v2`, `1v3`.

---

## Configuration

Everything lives in `config/battlehub/`. Run `/battlehub reload` after editing.

* **`config.json`**
  * `hub`: send players to BattleHub when they join (for BattleHub-only servers), join hint, game mode in the
    hub (adventure or survival), level on the XP bar, exit door item
  * `lobby`: spawn position in the hub (set with `/battlehub setlobby`), teleport on join, keep fed, void height,
    and the lobby itself: `style` (`citadel` or `classic`), `seed` (another seed, another city), `detail` (0
    structure only, 1 normal, 2 every ornament and piece of furniture), `islandRadius` (100-200), `townDensity`
    (0-1) and `discoverModBlocks` (let palettes use block families from other mods). Changing them rebuilds the
    lobby at the next start (or `/battlehub lobby rebuild`).
  * `compass`: hotbar slot, item, glint
  * `protection`: no damage or death outside matches, lobby block protection, no item drops. Beds and respawn
    anchors in BattleHub's worlds do nothing when used (they would explode there).
  * `combat`: `snowballDamage`, the damage of a snowball or egg hitting another player in a match (2 = one
    heart; vanilla only pushes). Spleef keeps the push only.
  * `timings`: countdowns (also between best-of-3 rounds), end delay, play-again time, challenge expiry,
    duel time limit, respawn delay
  * `rewards`: coins and XP for duels, ranked and party games (win, participation, per kill)
  * `elo`: start rating, K-factor, floor, tier thresholds, ranked matchmaking window
  * `levels`: XP curve (`baseXp`, `increment`, `growth`, `maxLevel`) and level bonus coins
  * `streaks.milestones`: which win streaks are announced
  * `arenas`: number of slots, slot spacing, floor height, blocks built per tick, **themes per mode**, how
    often a hand-built map is used without a vote (`customMapChance`), stands, islands, landmarks and engine
    cover round and in generated arenas (`architecture`), and the theme particles (`ambience`)
  * `seasons`: on/off, the start date of season 1, the length in days, the number of placement matches, how
    much of a rating stays at a new season, the coins per tier, the size of the Netherite divisions
  * `fairness`: the leave penalty and its waiting times, the idle limits in matches and queues, the teaming
    check and its time, the admin alerts and their limits (reach, clicks per second, hovering, speed), the
    time between reports
  * `bots`: on/off, filling queues on/off, after how many seconds, party lobbies up to how many players, the
    level of bots that fill in, the most bots at once, the share of rewards in matches with bots and other
    people (alone with bots always pays nothing), the fighting styles handed out (`archetypes`), how much each
    bot differs from its style (`personalityVariation`, 0 .. 0.2), whether bots remember players between fights
    (`learning`) and across restarts (`learningPersistent`, in `battlehub/ai/`), whether the learned model fights
    (`learnedCombat`), in which modes (`learnedModes`, default every melee mode) and whether a model that learned items uses
    them itself (`learnedItems`, off; the master model knows food, golden apples and wind charges), whether operators get the model reports after matches (`modelReport`, on), from what
    tick time bots think
    less often (`slowAboveMspt`), and how soon
    a bot leaves after its match
  * `fights`: recording fights for training on/off (`record`), names in the logs (`names`, off), the most the folder
    holds (`maxMegabytes`)
  * `effects`: the match intro on/off and its seconds, the victory shot, the killfeed and its number of lines
  * `lobbyExtras`: double jump on/off, its push forward and up and its cooldown; the training dummy on/off and
    where it stands (angle around the fountain and distance)
  * `resourcePack`: on/off, your own `url` for the zip or the `host` (the address players use) and `port` for the
    built-in server, whether players must accept it, lobby music and how often
  * `weekly`: the weekly tournament on/off, day, time, time zone, sign-up minutes, minimum players, kit, team
    size and the extra coins for champions and runners-up
  * `export`: the stats files on/off, every how many minutes, the folder, the built-in web server and its port,
    players per leaderboard and how many recent matches
  * `clans`: the price of a clan, the most members, and the coins per member for winning the weekly clan war
  * `featured`: the mode of the day on/off, the coin multiplier and the modes it picks from
  * `npcs`: the lobby characters on/off, their distance from the fountain, and per character the id, what a
    click does (`menu:pvp`, `menu:ranked`, `menu:party`, `menu:practice` or `game:<mode>`), the item it holds
    and where it stands (an angle around the fountain)
  * `leaderboards`: on/off, seconds per mode, rows per board, the kills needed for the K/D board, text
    size, and per wall: the side (`northwest`, `northeast`, `southwest`, `southeast`), ranked or not, the
    boards from left to right (`kills`, `deaths`, `kd`, `coins`, `wins`, `elo`) and the modes shown in
    turn (`all`, `pvp` or a mode id such as `sword`, `ffa`, `1v2` or `bed_wars`)
  * `games`: per mode: on/off, min/max players, time limit, kit, and game settings
    (duel `arenaScale` (mace 1.8), TNT Run decay and floors, KOTH target, CTF captures, parkour jumps and coins
    per difficulty and countdown, LMS border, FFA `killsToWin` and spawn protection, tournament
    `winnerCoins`, `finalistCoins` and `breakSeconds`; the tournament time limit is per round; 1v2 and 1v3
    `roundsToWin`)
* **`bedwars.json`**: the maps (`mapStyle`: `arch`, built by the architecture engine, or `classic`), the
  format when nobody votes (`solo` or `duo`), the duo team size, the most islands
  (2–8), respawn time, podium time, build
  height and depth, the protected radius around spawns, shops and generators, dropping, the killer taking
  resources, TNT fuse and power; per generator kind the ticks between drops per tier, items per drop and
  the most lying around; the event schedule (`diamond`, `emerald`, `sudden_death`, `glow` at a second); the
  shop offers (category, item with `{team}` for the team colour, count, currency, price, upgrade group and
  tier, enchantments); team upgrades and their prices. The start kit is `bedwars` in `kits.json`
* **`prophunt.json`**: rounds, hide, hunt and round-end seconds, the share of hunters, whether found props
  join the hunters, hunter respawn time, prop health and walking speed, the penalties for missing (air, wrong object) and the
  heal for a hit, how long a prop must stand still to turn solid, taunt and decoy cooldowns and decoy time,
  arrows at the start and per find, the points for everything, and the blocks props can become. Kits:
  `prophunt_hunter` and `prophunt_prop`
* **`progress.json`**: the number of daily and weekly quests, the quests (period, goal, mode, target, coins,
  XP, pass XP), the login streak coins, the season pass (tiers, XP per tier, per match and per win, coins per
  tier and every fifth tier, coins when the cosmetic is already owned), achievement coins and prestige on/off
* **`hunger_games.json`**: the loot of the house chests and barrels (`houseLoot`), the chests in the landmarks
  (`landmarkLoot`) and the cornucopia (`cornucopiaLoot`), as "item min-max xWeight" lines. How many items a chest
  gets, the peace, the border and the refill are settings of the game `hunger_games` in `config.json`
  (`houseChestRolls`, `landmarkChestRolls`, `cornucopiaChestRolls`, `graceSeconds`, `borderDelaySeconds`,
  `borderShrinkSeconds`, `borderEnd`, `borderDamagePerSecond`, `refillSeconds`). The city comes from the lobby's
  `seed`, `islandRadius`, `detail` and `townDensity` and the week; its themes are in `arenas.themes.hunger_games`.
* **`skywars.json`**: the loot of the spawn island chests (`islandLoot`), the middle islands (`midLoot`) and
  the big middle (`centerLoot`), one line per item: `"minecraft:iron_sword 1 x3"` (item, count or `min-max`,
  `x` weight). How many items a chest gets: `islandChestRolls`, `midChestRolls`, `centerChestRolls` in
  config.json (skywars)
* **`kits.json`**: items per kit with slot (`0`–`35`, `head`, `chest`, `legs`, `feet`, `offhand`),
  count, enchantments, unbreakable, leather colour, plus potion effects
* **`titles.json`**: titles with display text (`&` colour codes) and requirement:
  `default`, `level`, `wins`, `duel_wins`, `mode_wins`, `streak`, `mode_streak`, `kills`, `elo` or `coins` (price)
* **`cosmetics.json`**: the shop: per cosmetic an id, category (`trail`, `aura`, `arrow`, `kill`, `win`, `cry`,
  `skin`, `pet`, `hat`, `outfit`, `color`), display text, icon item, price and effect:
  * trails and arrows: a particle: an id (`flame`), `dust:#RRGGBB` (optionally `:size`), `dust:#RRGGBB>#RRGGBB`
    (fading), `rainbow`, `trail:#RRGGBB` (a streak of light), `block:<block>`, `item:<item>` or
    `tint:entity_effect:#RRGGBB`; join several with `+`
  * auras: `shape:particle`, with shape `halo`, `crown`, `wings`, `orbit`, `helix`, `cloud`, `spiral`, `galaxy`,
    `heart`, `ring`, `tornado` or `cape` (for example `wings:dust:#FFFFFF`)
  * kills: `lightning`, `explosion`, `hearts`, `flames`, `souls`, `blood`, `firework`, `totem`, `smite`, `rocket`,
    `frost`, `sculk`, `gust`, `confetti`, `ender`, `cherry`, `sonic`, `squid`, `bubbles`, `skull`, `gold`, `notes`
  * wins: `fireworks`, `totem`, `hearts`, `notes`, `flames`, `sparks`, `rockets`, `storm`, `rainbow`, `dragon`,
    `galaxy`, `snow`, `confetti`, `aurora`, `sakura`, `souls`, `gold_rain`
  * death cries: a sound id, optionally `;pitch` (`entity.goat.screaming.death;1.2`)
  * weapon skins: the item whose look swords take, optionally `;glint` (`minecraft:trident`)
  * pets and hats: an item id; outfits: `#RRGGBB`, `#RRGGBB>#RRGGBB` (fading) or three colours `#..,#..,#..`
  * name colours: a chat colour

  plus `trailsInMatches`. New cosmetics are added to an existing file when BattleHub updates.
* **`lang/`**: every text players see. **English and Dutch are built in**: players see BattleHub in
  their own client language when there is a file for it, otherwise in the server default (`language` in
  `config.json`). `en_us.json` is written here as a template. Copy it to e.g. `de_de.json` to add a
  language, or put changed lines in `en_us.json` / `nl_nl.json` to reword them.

Player data is stored per world in `world/battlehub/players/<uuid>.json`; saved survival states in
`world/battlehub/survival/`; clans in `world/battlehub/clans.json`; the weekly tournament history in
`world/battlehub/weekly.json`.

---

## FAQ

**Do players need the mod?** No. All menus are vanilla chest menus, and all text is translated on the
server, in each player's own language (English and Dutch built in).

**Does BattleHub change my survival world?** No. BattleHub has its own hub dimension
(`battlehub:hub`) and arena dimension (`battlehub:arenas`). The overworld, nether and end keep their
normal rules, and your survival inventory is kept separately while you play BattleHub.

**Where are the arenas?** In the `battlehub:arenas` dimension, which the mod adds itself. Arenas are
generated per match and removed afterwards.

**Can I change the kits?** Yes, in `kits.json`; then run `/battlehub reload`.

**Does it work together with Extreme Dimensions?** Yes. Extreme Dimensions (in the same repository) stands
still while you are in BattleHub's worlds: no charge drain, no penalties, no HUD. Everything carries on
when you leave.

**Does it work with other plugins that use scoreboard teams?** Titles use one scoreboard team per
player (also for name colours). If a player is already in a team from somewhere else, BattleHub leaves
it alone. You can turn titles-through-teams off with `titles.useScoreboardTeams`.

---

*License: CC0-1.0. Source and issues: <https://github.com/Peertjeh/extremedimensions> (folder `battlehub/`).*
