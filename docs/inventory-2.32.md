# Source-of-truth inventory (2.32.1)

The audit behind `docs/roadmap-3.0.md`, taken at 2.32.1. Paths: **M** = `src/main/java/com/extremedimensions`,
**C** = `src/client/java/com/extremedimensions/client`, **G** = `src/gametest/java/com/extremedimensions/gametest`,
**D** = `src/main/resources/data`, **A** = `src/client/resources/assets/extremedimensions`.

## 1. Build

- Minecraft 26.3, Fabric Loader 0.19.5, Loom 1.18-SNAPSHOT, Fabric API 0.162.0+26.3, Java 25, Gradle 9.7.1.
- Dependencies: Minecraft, Fabric Loader and Fabric API only. Mod Menu's API is a compile-only stand-in
  (`src/client/java/com/terraformersmc/`), kept out of the jar.
- Entrypoints: `ExtremeDimensions` (main), `client.ExtremeDimensionsClient` (client), `client.compat.ModMenuEntry` (modmenu).
- Mixins: 17 server mixins (`extremedimensions.mixins.json`) and 4 client mixins (`extremedimensions.client.mixins.json`).

## 2. Registries

| Kind | Count | Where |
| --- | --- | --- |
| Items | 60 (23 items, 37 block items) | M/registry/ModItems.java |
| Relics (items) | 9: ember_heart, pale_shard, hungering_hand, featherbone, keepers_eye, tithe_bell, lurkers_tooth, seam_lantern, kindred_knot | M/relic/Relic.java |
| Blocks | 39: ember_lodestone and void_lodestone (no item), 37 hush blocks incl. seam_vein | M/registry/ModBlocks.java |
| Entity types | 10: lurker, hollowling, keeper, starved_one, veil_ray, thinned, matriarch, ferryman, weaver, rift_portal | M/registry/ModEntities.java |
| Mob effects | 3: matriarch_hex, styx_weight, bound_wings | M/registry/ModEffects.java |
| Sound events | 66 registered; 71 in `sounds.json` (5 play through `Holder.direct`) | M/registry/ModSounds.java |
| Particle types | 25 | M/registry/ModParticles.java |
| Creative tabs | 1 | M/registry/ModItemGroups.java |
| Damage types | 1: elder_pierce | D/extremedimensions/damage_type |
| Network payloads | 27 (14 to the client, 13 to the server, rate-limited) | M/network/ModPackets.java |
| Block entities, data components, game rules, enchantments, own tags | none | |

Every item is a plain `Item` or `BlockItem`; behaviour lives in event handlers (M/server/ServerEvents.java).

## 3. Data

- **Recipes: 121** (36 shaped, 9 shapeless, 75 stonecutting, 1 smelting). Six are granted on first join (lodestone,
  tether_journal, tether, ember_tether, void_tether, mending_cloth); no advancement unlocks the rest.
- **Loot tables: 39**, all block drops. Chest loot (Seam barrels, tangles, hoards, raids) and boss drops are in code.
  `lore/WorldLoot.java` adds Wanderer's Pages and Tether Fragments to six vanilla chest tables.
- **Advancements: 36**, one tree under `root`, every one `minecraft:impossible` and granted from code
  (`progression/Advancements.grant`).
- **Tags:** vanilla tags extended (mineable, slabs, stairs, walls, stone materials, bypasses_armor); none of the mod's own.
- **Worldgen:** the Seam dimension (flat, no layers), its dimension type and one biome without spawns.

## 4. Journal

- 38 pages in `JournalEntry`, in this order: tethered, anchor, cord, wound, ember_deep, pale_void, starved, ended, scars,
  fragment, mending, weavers, unanchored, habit, silence, tribe, soul_light, ruined_gate, first_wound, hollows,
  little_thieves, keeper, seam, loom, thinned, woven, seal, storms, heart, wanderer, ending, matriarch, ferryman,
  last_weaver, barter, raid, fray, frayed_ray.
- `Journal.unlock` stores the id in `PlayerData.journal`, toasts it, and grants `journal_complete` when all are known.
- `ruined_gate` and `first_wound` come only from Wanderer's Pages; a page opens the first locked page in order, or gives
  20 XP when all are known.
- Bestiary: 10 creatures (lurker, hollowling, keeper, starved_one, thinned, veil_ray, frayed_ray, matriarch, ferryman,
  weaver). Ledger: road, burden and others.

## 5. World state

`DataStore.WorldData`: `sealBroken`, `starvedSlain`, `heartBuilt`, `seamSealed`, `ending` (`""`, `mended`, `severed`).
Unlock switches (`0` always, `1` seal broken, `2` ending chosen): `pact_unlock` 2, `trial_unlock` 0,
`ferryman_unlock` 1, `weaver_unlock` 2.

## 6. Periodic events (driven from `ServerEvents.tick`)

| Event | Cadence | Config |
| --- | --- | --- |
| Lurker encounter | every 400 ticks per player in the Overworld or Nether | lurker_* |
| Lurker offering | when a Lurker vanishes after the seal | lurker_offering_chance |
| Wound Storm | a roll every 1200 ticks per Overworld player | storm_chance, storm_cooldown_minutes, storm_seconds |
| Ferryman | once per Nether expedition after `ferryman_after_seconds` | ferryman_* |
| Last Weaver | first visit to each Thinned village | weaver_* |
| Matriarch | when a hoard is woken after enough Keepers | matriarch_after_keepers |
| Wild hoards | every 600 ticks in dark forests, pale gardens, soul sand valleys | hoard_wild_chance |
| Companion gift | every `companion_gift_minutes` | companion_gift_minutes |
| Keeper hunts | while the Tribe hunts you | tribe_hunt_minutes |
| Fray gloom, strands, rays | `SeamDepth.tick`, `VeilRay` | seam_fray_* |

## 7. Persistence

No `SavedData`: everything is Gson JSON in `<world>/extremedimensions.json` (`DataStore`, atomic write, `.bak`,
`.unreadable-<ms>`). Seam cells are keyed by prefix (`c@`, `u@`, `r@`, `w@`, `wv@`, `k@`, `a@`, `f@`, `v@`, `g@`,
`d@`, `b@`). Migrations: `PlayerData.migrate` (pre-2.3), `Anchor.fixLegacy` (pre-2.17), the old `w@` Weaver roll.
Config: `config/extremedimensions.json` (`config_version` 3, migrations in `Config.MIGRATIONS`); client options in
`config/extremedimensions-client.json`.

## 8. Config, commands, lang

- **Config:** 326 keys in 17 sections, found by reflection; bounds in `Config.BOUNDS`; presets pilgrim, seeker,
  tethered, starved, severed. All 326 names and descriptions exist in EN and NL, but no test checks it.
- **Commands:** `/tether` with `where`, `burden`, `share` and `tribe` open to everyone; the rest need level 2;
  `seam_reset` needs level 3. Fallen players cannot run commands.
- **Lang:** en_us and nl_nl, 1758 keys each; the Gradle `checkTranslations` task fails the build on a missing or
  unknown key or a placeholder mismatch.

## 9. Tests and CI

- 119 server gametests in 21 classes, and one client gametest (`ClientShots`, about 84 screenshots).
- CI (`.github/workflows/build.yml`): `./gradlew build` (server gametests and translations), then
  `runClientGameTest` under xvfb. `[shots]` prints screenshots into the log; `[probe]` runs `javap` on
  `tools/probe.txt`.

## 10. Discrepancies found

| Walkthrough | Code |
| --- | --- |
| "Je krijgt alle recepten van de mod" | Six recipes are granted |
| Relics "(8)" in Appendices I and K | 9 |
| 34 journal pages (Appendix P) | 38 |
| 28 advancements (Appendix Q) | 36 |
| Pacts open "nadat de Starved One is verslagen" | `pact_unlock` 2: when the ending is chosen |
| The Last Weaver wakes "nadat de Starved One is verslagen" (and journal XXXIV: "with the hunger at the Heart ended") | `weaver_unlock` 2: when the ending is chosen |
| The ultimate's name is shown | Names were removed in 2.30 |
| Version 2.26.0 / jar 2.21.0 | 2.32.1 |
