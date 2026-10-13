# Changelog

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
* **Configuration** in `config/battlehub/`: `config.json`, `kits.json`, `titles.json` and `lang/`
  for translations. `/battlehub reload` applies changes.
* **Admin commands:** `/battlehub setlobby | reload | matches | endmatch <id> | arena list`.
