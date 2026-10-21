# Extreme Dimensions: the road to 3.0

*Every portal is a wound. Every wound remembers.*

This is the design package for the releases between 2.32.1 and 3.0. It joins two plans. The first is the road to 3.0
already in the roadmap document: integration into vanilla, refinement, the player's journey, finishing the world, and an
ending. The second is the master expansion brief: lore-driven content, enchantments, items, crafting, world events and
long-term progression. Section 3 sets out where the brief's proposed releases (2.27 to 2.30) land now that those numbers
are taken.

Status words used throughout:

| Word | Meaning |
| --- | --- |
| **Existing** | Already in the mod; improved, not replaced |
| **New** | A proposed feature |
| **Dependency** | Needed by a later feature |
| **Deferred** | Planned, but for a later release than the one it is listed under |

The source code is the authority on behaviour. The Dutch walkthrough (*Extreme Dimensions: de complete walkthrough*,
revision 209) is the authority on narrative and on what players are told. Where they disagree, the disagreement is
listed in section 1.4 rather than reconciled silently.

---

## 1. Current architecture and feature inventory

### 1.1 Platform

| Item | Value |
| --- | --- |
| Mod version | 2.32.1 (`gradle.properties`) |
| Minecraft | 26.3, Mojang mappings |
| Java | 25 |
| Fabric Loader / API / Loom | 0.19.5 / 0.162.0+26.3 / 1.18-SNAPSHOT |
| Source sets | `main` (common and server), `client`, `gametest` |
| Build and test | CI only (`.github/workflows/build.yml`): build, dedicated-server gametests, client gametest with screenshots (`[shots]`) |

### 1.2 Systems and where they live

All paths are under `src/main/java/com/extremedimensions/` unless they say `client/`.

| System | Main classes | Notes |
| --- | --- | --- |
| Anchors | `anchor/`, `data/Anchor.java` | Lodestone + fuel within 5 blocks of a portal; per-player charge, up to 30 min; soul wards; sharing |
| Tether and expeditions | `tether/`, `expedition/` | Drain with distance, boss-bar timer, Leeching, Collapse, Seam-Woven escape |
| Curses | `penalty/` | 68 curses in four sets (Beyond 26, Overworld 17, Everywhere 16, Extreme 9); offers, multipliers, spill; atonement, fragments, Mending Cloth, scars |
| Pacts | `pact/` | Heat 1 to 30; reward on a safe return |
| Relics | `relic/Relic.java`, `relic/RelicService.java` | 9 relics, one bound to the Tether |
| Rift Trials | `trial/` | Weekly task and twist; medals; scoreboard |
| The Tribe | `lurker/`, `hollow/`, `tribe/` | Lurker (watch, stalk, take, flee, drink anchors), Hollowlings, Keeper, hoards, raids, standing -100 to 100, barter |
| The Seam | `seam/` | Islands on a 100-block grid, hush masonry, looms, Thinned villages, colossi, the Return Gate, the Fray below |
| The Elders | `elder/` | Matriarch, Ferryman, Last Weaver: arenas, domains, cores, falling islands, ultimates |
| Seal and Heart | `mending/`, `world/WorldService.java` | Dragon = seal; Wound Storms; the Heart and the Starved One; Mending Thread and Severing Shears |
| Journal | `journal/` | 38 pages (`JournalEntry`), bestiary, ledger; Wanderer's Pages (`lore/WorldLoot.java` adds them to six vanilla chest tables) |
| Advancements | `progression/Advancements.java`, `data/extremedimensions/advancement/` | 36 advancements in one tree under `root`, all granted from code (`minecraft:impossible`) |
| Co-op | `coop/` | Fallen and lifted, carrying a burden, shared anchors |
| Guide | `guide/` | First-expedition card |
| Config | `config/Config.java` | One JSON file; every key has `config.extremedimensions.<key>` and `.desc` in EN and NL; presets |
| Commands | `command/` | `/tether ...`, mostly operator-only |
| Persistence | `data/DataStore.java`, `data/PlayerData.java` | One Gson JSON file per world (atomic write, backup); no `SavedData` |
| Tests | `src/gametest/` | 119 server gametests in 21 classes and one client gametest with about 84 screenshots |

The full inventory produced by the audit is appended as section 13.

### 1.3 Extension points the new content uses

| Need | Existing mechanism |
| --- | --- |
| Place something in the world near a player, once | Old hoards (`hollow/HollowService.wild`): checked as the player moves, built out of sight, recorded in `DataStore` |
| Add items to vanilla chests | `lore/WorldLoot.java` (`LootTableEvents.MODIFY_DROPS`) |
| Unlock a journal page | `Journal` + `JournalEntry` (the page text is in the lang files) |
| Grant an advancement | `progression/Advancements.grant` |
| Server-side visual effects | `server/WorldFx.java`, `elder/AttackFx`, `server/BlockFlight.java` |
| A periodic world event | Wound Storms (`world/WorldService.java`, driven from `ServerEvents.tick`) |
| Per-player state | `data/PlayerData.java` |
| Expedition hooks (start, return, Collapse) | `expedition/` |

### 1.4 Discrepancies between the walkthrough and the code

| Walkthrough (rev 209) | Code (2.32.1) | Action |
| --- | --- | --- |
| Header: "Extreme Dimensions 2.26.0"; install step names `extremedimensions-2.21.0.jar` | 2.32.1 | Fix in 2.33 |
| Chapter 2: "je krijgt alle recepten van de mod" | Only 6 recipes are granted at first join (`ServerEvents.RECIPES`); the other ~114 never appear in the recipe book | Fixed by 2.33 (recipe book through advancements) |
| Chapter 5 says nine relics; Appendices I and K say "(8)" | 9 relics (`Relic.java`) | Fix the appendix titles in 2.33 |
| Appendix P: 34 pages | 38 pages (`barter`, `raid`, `fray`, `frayed_ray` missing) | Add the four in 2.33 |
| Appendix Q: 28 advancements | 36 (`burden_carried`, `first_barter`, `fray_entered`, `fray_risen`, `hunted`, `lifted`, `raider`, `tribe_kin`, `tribe_trusted` and others missing) | Rewrite the appendix with the 2.33 tree |
| Chapter 10, the ultimate: "de naam van de aanval wordt eroverheen geslagen" | Since 2.30 blows and ultimates are no longer named on screen | Fix in 2.33 |
| Roadmap document: "Advancements bestaan los van elkaar, nog niet als één boom" | They already form one tree under `root`. What is missing is that they unlock recipes | Correct the roadmap |
| Chapters 5, 10 and 12, journal XXXIV and the settings' own descriptions: pacts open and the Last Weaver wakes once the Starved One falls | The unlock switches (`pact_unlock`, `weaver_unlock`, and the same switch for trials and the Ferryman) treated 2 as "the ending is chosen" | Fixed in 2.33: 2 means the Starved One has fallen, as the settings always said |

---

## 2. Canon: narrative and progression

### 2.1 What is established

These facts come from the journal (lang files) and the walkthrough. New content has to fit them.

- **The Tether** is "a cord older than the mountains, woven through every living thing" (I). It does not speak; it only
  moves things: a mark closes on one soul and opens on another (XI).
- **Portals are torn, not built** (IV). The ruined gates in the wilds are graves of wounds opened without an anchor.
  The Wanderer found a chain in the obsidian of one, *still warm*, and did not follow it (XVIII). **This is an open thread.**
- **The Tribe** made the first wound as a door, to walk where nothing could follow. Something hungry came through, and
  they hid in the seams. They did not die; they thinned (XVI, XIX). The Thinned's villages in the Seam were their
  home (XXV). Hollowlings "want to remember what having things was like" (XX).
- **The Weavers** built the cities of black stone under everything. At their centre is "a great frame, and nothing in
  it". They tried to close a portal "with the only thread strong enough. Themselves" (XII). They wove the Tether in
  the Seam "before anyone needed one" (XXIV). Not all of them finished weaving themselves into the seal: a Last Weaver
  waits in each village of the Thinned (XXXIV).
- **The Ender Dragon** was the lock on the first wound (XXVII). Killing it cracks the world: the Tribe turn to
  offerings, Wound Storms begin, and rifts open at the Heart.
- **The Starved** are souls that stayed too long unanchored (VII). The Starved One, white as bone and thin as hunger,
  waits at the Heart.
- **The Ferryman** is who the piglins pay (V, XXXIII). **Endermen** may be trying to build an anchor, "a way home"
  (XIII). **This is an open thread.**
- **The Wanderer** says they were the first to bind a tether, the first to walk the seams, and "the last of the tribe
  who did not thin" (XXX). The journal also says the Weavers wove the Tether before anyone needed one (XXIV): the
  account already contradicts itself, and that is deliberate.
- **The ending** is world-wide and permanent. Mend: "the world forgets the wound". Sever: "the world forgets the
  Tether". The last page: "the journal ends here. *The story does not*." (XXXI). **This is where 3.0 starts.**

### 2.2 Progression map (as built)

```
first join ─ journal ─ anchor ─ tether ─ Nether ─ End ─────────────── Dragon (seal) ─ Wound Storms
                  │                │      │                                 │
                  │                │      └─ Tether Fragments ─ curses ─ atonement, scars
                  │                └─ Lurker ─ hoards ─ Hollowlings ─ Keeper ×3 ─ Matriarch
                  └─ Wanderer's Pages ─ Echo Shard ─ Seam Needle ─ Seam ─ silk ─ woven Tethers
                                                                     ├─ Fray (wells)
                                                                     └─ Rift Trials
Dragon ─ Ferryman (Nether) ─ Heart ─ Starved One ─ Mending Thread / Severing Shears ─ ending
                                          └─ pacts open, Last Weaver can wake
```

### 2.3 Open threads the expansion takes up

| Thread | Where it is left | Picked up in |
| --- | --- | --- |
| The warm chain in a ruined gate | XVIII | 2.34 The Warm Chain |
| The empty frame in the Weavers' cities | XII | 2.39 The First Weaver |
| The Endermen building an anchor | XIII | 2.36 The Wounds Remember |
| Who wove the Tether: the Weavers or the Wanderer | XXIV against XXX | 2.34 to 2.39 (the second hand) |
| "The story does not [end]" | XXXI | 3.0 The Unfinished World |

The expansion takes up existing hooks. It does not invent a new villain, and it does not give final answers. Each
discovery adds a witness, and the witnesses disagree.

---

## 3. The road: 2.33 to 3.0

The brief's releases were numbered 2.27 to 2.30. Those numbers have been used (2.27 Seam purpose, 2.28 vanilla look,
2.29 vanilla pixels, 2.30 to 2.32 the boss update). Its themes are placed on the road as follows. Integration comes
first because new recipes need the recipe book and new loot needs loot tables. Refinement comes after the content, so
the bug sweep and the balance pass cover the new content too.

| Version | Theme | Pillar | Brief's theme |
| --- | --- | --- | --- |
| 2.33 | Rooted in Vanilla | Integration | Foundation for all of them |
| 2.34 | The Warm Chain | World and danger | The Forgotten Relics (and the vertical slice) |
| 2.35 | The Art of Binding | World and danger | The Art of Binding |
| 2.36 | The Wounds Remember | World and danger | The Wounds Remember |
| 2.37 | Refinement | Refinement | Balance, exploits, multiplayer |
| 2.38 | The Path | Player journey | Progression and guidance |
| 2.39 | The First Weaver | World and danger | The First Weaver |
| 3.0-beta | Frozen | All | Integration pass |
| 3.0 | The Unfinished World | Conclusion | The Unfinished World |

What does not change on the road: the vanilla norm in the roadmap document still applies to everything new. Every
release still needs green CI, screenshots that have been looked at, a changelog entry, both walkthroughs updated and a
download link.

### 3.1 Per release

Scores run from 1 (low) to 5 (high). **N**arrative, **G**ameplay, **C**omplexity, compatibility **R**isk, **B**alance
risk, **U**se of existing systems, **T**est cost, importance to **3.0**.

#### 2.33 Rooted in Vanilla

*Fantasy: the mod is part of Minecraft's own progression. Entry: a fresh world. No new systems.*

| Feature | Status | N | G | C | R | B | U | T | 3.0 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Recipe book through advancements: every recipe unlocked by holding its key ingredient | Existing, improved | 1 | 4 | 2 | 2 | 1 | 5 | 2 | 5 |
| The advancement tree completed for systems without one (trials, relics, pacts, falling islands) | Existing, improved | 2 | 3 | 1 | 1 | 1 | 5 | 1 | 4 |
| Vanilla rules tested: shield, milk, peaceful, mobGriefing | Existing, improved | 1 | 3 | 2 | 1 | 1 | 5 | 3 | 4 |
| Loot in loot tables: Seam barrels, tangles, old hoards, Keeper, Elders, offerings, gifts, Wanderer's Pages | Existing, improved (**Dependency**) | 1 | 2 | 3 | 2 | 1 | 4 | 3 | 5 |
| Walkthrough discrepancies (1.4) fixed; pacts and the Last Weaver open when the Starved One falls, as the journal says | Existing | 2 | 1 | 1 | 1 | 1 | 5 | 1 | 3 |

*Done when:* every recipe has an unlocking advancement (gametest); one gametest per vanilla mechanic; a test datapack
changes a Seam table and the barrel follows; the client test photographs the recipe book and the advancement tab.

*Status:* released as 2.33.0. All five criteria met; it also fixed the 26.3 loot table format for the mod's own block
drops (the Seam Vein's silk count), found while moving the loot into tables.

#### 2.34 The Warm Chain (the vertical slice)

*Fantasy: follow what the Wanderer would not. Entry: first Nether expedition (a ruined portal you can light). Narrative:
the Wanderer saw a chain and walked away; on the other side, someone was still holding it.*

| Feature | Status | N | G | C | R | B | U | T | 3.0 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Warm ruined portals: a chain in the obsidian, leading into the gate | New | 5 | 3 | 3 | 2 | 1 | 4 | 3 | 4 |
| The Held: at the chain's other end in the Nether, a sealed chamber, a letter and a relic | New | 5 | 4 | 3 | 2 | 2 | 4 | 3 | 4 |
| The second hand: notes in another hand in the margins of the journal | New (**Dependency** of 2.39 and 3.0) | 5 | 2 | 2 | 1 | 1 | 4 | 2 | 5 |
| Held's Chain, the tenth relic | New | 3 | 4 | 2 | 1 | 3 | 5 | 2 | 3 |
| Relic review: every relic worth binding; clear prices | Existing, improved (**Deferred** to 2.37 for the numbers) | 1 | 3 | 1 | 1 | 3 | 5 | 2 | 3 |

*Done when:* a gametest builds a warm portal, crosses it with a player and finds the chamber placed once; the relic's
grace is tested on both sides of the portal; the margin notes survive a relog; screenshots of the chain, the chamber
and the journal margin.

*Status:* built in 2.34.0. One change from the plan: each warm gate has its own chamber, and only the first chamber a
player raises holds the Held's letter and chain (so the Held stays one person, and every player can find them). Its
gametests were not registered with the runner in 2.34.0; they run from 2.35.0.

#### 2.35 The Art of Binding

*Fantasy: prepare differently, not just harder. Entry: enchanting table (Woundbane, Thrift, Silkbound) and the Seam
(Veilsight, Homebound).*

| Feature | Status | N | G | C | R | B | U | T | 3.0 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Five enchantments (section 5) | New | 3 | 5 | 3 | 2 | 4 | 4 | 3 | 4 |
| Tethers become enchantable (the table and anvils) | New (**Dependency**) | 2 | 3 | 2 | 2 | 2 | 5 | 2 | 3 |
| Wound Lens: reads a wound without removing its danger | New | 4 | 4 | 3 | 1 | 1 | 4 | 3 | 3 |
| New uses for Veil Wings and Echo Shards (section 6) | Existing, improved | 2 | 3 | 1 | 1 | 2 | 5 | 1 | 2 |

*Done when:* gametests per enchantment effect, table eligibility and exclusivity; the Lens's readings tested server-side;
screenshots of the enchanting table, a tooltip and the Lens view.

*Status:* built in 2.35.0. Homebound's kept gear waits in the player's data rather than in memory, so a restart between
death and respawn keeps it. The client test checks the tooltip line and photographs the bag and the Lens view instead
of the enchanting table (whose offers are random). The Veil Lantern moves to 2.36 with the storms it wards against; the
Echoing Journal is dropped as purely cosmetic.

#### 2.36 The Wounds Remember

*Fantasy: the world reacts to what you have done in it. Entry: varies per event (section 7). Narrative: Collapses leave
echoes; anchors feel storms; the Endermen build.*

| Feature | Status | N | G | C | R | B | U | T | 3.0 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Event framework: triggers, warnings, cooldowns, config, test hooks; Wound Storms moved onto it | Existing, improved (**Dependency**) | 1 | 2 | 3 | 3 | 1 | 4 | 3 | 5 |
| Echoes of Collapse | New | 5 | 4 | 4 | 2 | 2 | 4 | 3 | 4 |
| Unstable Anchors | New | 3 | 4 | 2 | 2 | 2 | 5 | 2 | 3 |
| The Unanchored Build (Endermen) | New | 5 | 3 | 3 | 2 | 1 | 3 | 3 | 3 |
| Frayed Seam | New | 3 | 4 | 3 | 2 | 2 | 5 | 2 | 3 |
| Wound Convergence | **Deferred** to 3.0 (the Weaving) | | | | | | | | |

*Done when:* every event can be started deterministically from a gametest and cleans up after itself; warnings, effects
and rewards are tested; each event is photographed.

*Status:* built in 2.36.0, with the Veil Lantern moved here from 2.35. Choices made on the way: an echo of a Collapse is
a place rather than a happening, so it does not count against the spacing between events; the Unanchored Build lays
end stone bricks (plain end stone would vanish into the island it stands on).

#### 2.37 Refinement

*No new systems.* Five independent reviews, as in 2.22. A balance pass of curses, atonement, the Fray and everything
added since 2.33 (section 9). Game feel: animation, sound and feedback on every hit. A per-tick performance budget
(PerfTests). Sodium, Iris and Mod Menu. Every system tested with two players.

*Done when:* the reviews find nothing new; PerfTests stay within budget; the balance targets in 9.2 hold.

*Status:* built in 2.37.0. The five reviews (correctness, persistence and multiplayer, exploits and balance, per-tick
cost, text) found 40-odd things; every one that held up is fixed, with a gametest where a test can hold it. Of the
balance targets, the drain floor now holds with every easing (tested), Woundbane V kills an Elder about 7% faster than
Sharpness V (Matriarch: 68 hits against 73), and an echo pays a listener at most once an hour. Two targets need a
played session and move to 3.0-beta: the length of a first Nether expedition and the relic pick rate (with the relic
review). Sodium, Iris and Mod Menu cannot be run in CI; they move to 3.0-beta's compatibility pass as well.

#### 2.38 The Path

*No new systems.* The Wanderer's path as vanilla advancements, with the current goal in the journal. A short
explanation in vanilla style the first time a player meets each system.

*Done when:* the client test walks the whole path from a fresh world to the Seam and back.

*Status:* built in 2.38.0. The journal has a fourth tab, Path: the next of seven steps (bind and return, into the Seam,
home from the Seam, break the seal, the Heart, the Starved One, mend or sever) with a line on how to take it, the steps
done, and beside the path what has been found of the Hollowling companion, the woven Tether, the warm chain and the
three Elders. A step counts however it was done, also before 2.38; the world's steps count for everyone. The
advancement tree runs the same way (a new advancement, The Heart of the Seam, sits between the seal and the Starved
One). Eleven first-meeting hints come as toasts (a Collapse, a curse, a Tether Fragment, the Lurker, a hoard, a relic,
the Seam Needle, Seam Silk, an Elder forming, a Wound Storm, falling beside an ally), once per player, off for the
world with `hints` and for a player under Display; a player from before 2.38 is not told what they already know. The
new client test `ClientPath` walks a fresh world: a portal, an anchor fed and a Tether bound, through to the Nether and
home, a needle on crying obsidian, into the Seam and home through the rift, reading the Path tab at every step.

#### 2.39 The First Weaver

*Fantasy: an investigation across every world. Entry: after the Seam (the frame needs Seam Silk); the end needs the
Fray. Narrative: if there was a last Weaver, there was a first.*

| Feature | Status | N | G | C | R | B | U | T | 3.0 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| The unstrung frame: string an Ancient City frame with silk; it shows what it held | New | 5 | 3 | 3 | 2 | 1 | 3 | 3 | 5 |
| Four threads in four worlds (Ancient City, a bastion, an End city, a loom) | New | 5 | 4 | 4 | 2 | 2 | 4 | 4 | 5 |
| The First Knot, deep in the Fray, and the Knot Mother (the Fray's boss, an optional encounter) | New (was in the old 2.36) | 4 | 5 | 5 | 2 | 3 | 4 | 4 | 4 |
| Finishing the world: damage by role, rock arches, terraces, villages that step with the slope | Existing, improved (was in the old 2.36) | 1 | 2 | 3 | 2 | 1 | 5 | 3 | 3 |

*Done when:* the investigation can be completed from start to end in a gametest chain; the Knot Mother has BossTests;
no point in the norm goes to "afwijking".

#### 3.0-beta

Frozen: no new features. Polish, bugs and text. A migration test opens a world saved by 2.32 and plays it on.

#### 3.0 The Unfinished World

*Fantasy: the story goes on in your hand. Entry: the ending (and, for the loom, the first Elder). Narrative: "Now it
is yours to finish" (XXX).*

| Feature | Status | N | G | C | R | B | U | T | 3.0 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Pages in your own hand after the ending, different in a Mended and a Severed world | New | 5 | 2 | 2 | 1 | 1 | 4 | 2 | 5 |
| The Loom and the Tapestry: threads earned by great deeds woven into panels, each a lasting boon | New | 4 | 5 | 4 | 2 | 4 | 4 | 4 | 5 |
| The Weaving: when the last panel is set, the wounds converge on your loom (a defensive event) | New (uses 2.36) | 5 | 5 | 4 | 2 | 3 | 4 | 4 | 5 |
| New Thread+: a harder second round with endless trials | New | 2 | 4 | 3 | 2 | 3 | 4 | 3 | 4 |
| README rewritten; 2.x worlds play on; EN and NL complete; the norm met everywhere | Existing | 1 | 1 | 2 | 3 | 1 | 5 | 4 | 5 |

The old 3.0 plan said: "the finale at the Heart: weave the wound shut". That cannot stand. After either ending, rifts
no longer open at the Heart, and the ending is irreversible. The finale moves to the player's own loom, and it works
the same way in both worlds.

### 3.2 Dependencies

```
2.33 recipe book ──► every new recipe (2.34, 2.35, 2.39, 3.0)
2.33 loot tables ──► every new find (2.34 chamber, 2.35 Veilsight/Homebound books, 2.36 rewards, 2.39 threads)
2.34 second hand ──► 2.39 clues ──► 3.0 pages in your own hand
2.35 enchantable Tethers ──► Thrift, Silkbound
2.36 event framework ──► 3.0 the Weaving (Wound Convergence)
2.39 threads and the First Knot ──► 3.0 the Tapestry's panels
```

---

## 4. Item catalogue

Every item is drawn at 16×16 in the existing palette. Every name has a lang entry in EN and NL. Mod items cannot be
stolen by Hollowlings (existing rule); a Lurker can take one like any item.

### 4.1 Held's Chain (relic) · `extremedimensions:helds_chain` · 2.34 · New

| Field | Value |
| --- | --- |
| Narrative | The chain the Held kept hold of for longer than anyone should. "It will hold you as it held me: a little longer than it should." |
| Acquisition | The barrel in the first Held's chamber a player raises (later chambers hold another's remains: fragments, a page). Not craftable, never a random relic |
| Stage | First Nether expedition onwards |
| Primary | **Gives:** when your charge runs out beyond the wound, the chain holds you for 15 s before the Collapse begins. Reach your arrival portal in that time and you come home without a curse |
| Secondary | Reads in the Curses screen as a relic. Order with a Seam-Woven Tether: the chain's grace comes first; the tether's escape only if you do not make it |
| Price | **Takes:** you can store at most 20 minutes per anchor instead of 30 |
| Limits | Not in the Seam (the Seam has no Collapse). A death beyond the wound is still a death |
| Multiplayer | Per player, like every relic |
| Balance risk | Makes short expeditions near the portal risk-free. Contained by the drain doubling with distance and by the price on long trips. Config: `relic_helds_chain_grace` (15), `relic_helds_chain_cap` (1200) |
| Assets | Item texture (a looped length of soot-dark chain with one warm link); reuses chain sounds |
| Tests | Grace starts at zero charge; portal reached → no curse; grace runs out → Collapse; cap enforced on charging |

### 4.2 Loose Leaf · `extremedimensions:loose_leaf` · 2.34 · New

| Field | Value |
| --- | --- |
| Narrative | A page in a hand that is not the Wanderer's, signed with a knot drawn in soot |
| Acquisition | The barrel in the first Held's chamber a player raises (a lectern only holds books; the barrel keeps it with the Held's Chain) |
| Primary | Use: opens journal page XXXIX *The Held* and the margin notes that belong to it, **for the reader**. Not consumed, so friends can read the same leaf |
| Limits | One per chamber. A second read does nothing |
| Multiplayer | World-level item, per-player journal state |
| Assets | Item texture (a torn leaf, soot-black knot) |
| Tests | Reading unlocks the page and notes once; survives a relog; no duplicate toast |

### 4.3 Wound Lens · `extremedimensions:wound_lens` · 2.35 · New

| Field | Value |
| --- | --- |
| Narrative | An echo shard set in a spyglass and bound with silk: it listens to wounds |
| Recipe | Shapeless: Spyglass + Echo Shard + 2 Seam Silk. Unlocked by holding Seam Silk and a Spyglass |
| Stage | After the first Seam visit |
| Primary | Scope like a spyglass. A charged **anchor** shows every player's stored charge in it and whether it is shared. A **Lurker** shows what it is doing (watching, stalking, drinking, fleeing) |
| Secondary | In a **Wound Storm**, the next breach point glows 3 s before something comes through. In the **Seam**, the rift glints gold when you look toward it |
| Limits | Information only: it never stops a blow, a theft or a breach. It does not name the Elders' blows (their tells stay colour, movement and sound) |
| Multiplayer | Readings are sent only to the player looking |
| Balance risk | Low; the information is already partly visible |
| Assets | Item texture; a scope overlay tint |
| Tests | Server answers per target type; nothing is revealed without the Lens |

### 4.4 Echo Thread, Ember Thread, Pale Thread, Silk Thread · 2.39 · New

The four clues of the investigation, one per world (section 8.4). Not craftable; used once in the Plumb Line recipe.

### 4.5 Plumb Line · `extremedimensions:plumb_line` · 2.39 · New

The four threads twisted together. Held over a Fray well, it points down to the First Knot. Recipe: the four threads +
Iron Chain (shapeless), unlocked by holding any thread.

### 4.6 Weaver's Loom (block) · `extremedimensions:weavers_loom` · 3.0 · New

| Field | Value |
| --- | --- |
| Narrative | Built after the looms in the Seam. "Now it is yours to finish" |
| Recipe | Shaped: 4 Hush Pillars, 2 Seam Silk, 1 Loom, 2 Tether Fragments. Unlocked by slaying an Elder |
| Primary | Holds the Tapestry: six panels, each woven from a Thread earned by a great deed (section 8.6) |
| Limits | One loom per world counts (the first placed); others are decorative |
| Multiplayer | World-level: every player's threads go into the same tapestry |

### 4.7 Items deliberately not added

| Idea | Why not |
| --- | --- |
| A portable charge ("spool") | Undercuts anchors and the preparation loop; the Seam-Woven Tether already insures |
| A glider from Veil Wings | The Elders ground elytra on purpose; a second glider reopens that problem |
| A Keeper's mask as a helmet | Turns the Tribe's faces into loot; the Tribe is not a trading post |

---

## 5. Enchantment catalogue (2.35)

Enchantments in 26.3 are data-driven (`data/extremedimensions/enchantment/*.json`) with effect components. Eligibility
uses vanilla tags (`#minecraft:in_enchanting_table`, `#minecraft:treasure`, `#minecraft:on_random_loot`,
`#minecraft:tradeable`, `#minecraft:exclusive_set/*`). Effects that vanilla components cannot express read the level in
code with `EnchantmentHelper.getItemEnchantmentLevel`. Every enchantment has a lang name in EN and NL, and a
description line in its tooltip (the existing tooltip style).

| Enchantment | Applies to | Max | Effect per level | Acquisition | Exclusive with | Decision it creates |
| --- | --- | --- | --- | --- | --- | --- |
| **Woundbane** `woundbane` | Swords, axes, maces | V | +2.5 damage against creatures of the wound (`#extremedimensions:wound_born`: Lurker, Hollowling, Keeper, Thinned, Frayed Ray); against the Elders and the Starved One only +1 at I and +0.65 per level after (+3.6 at V, against Sharpness V's +3) | Table, books, loot | Sharpness, Smite, Bane of Arthropods (vanilla damage set) | A blade for the Tribe and the Seam, or one for everything |
| **Thrift** `thrift` | Tethers | III | Drain −5% (−15% at III) | Table, books | Silkbound | Efficiency against safety: a Collapse with a Thrift tether takes **one extra curse stack** |
| **Silkbound** `silkbound` | Tethers | II | +30 s in the Seam | Table, books | Thrift | The Seam or the other worlds |
| **Veilsight** `veilsight` | Helmets | I | In the Seam and the Fray the fog keeps half as close; Seam Veins within 12 blocks shimmer while you sneak | Treasure: Seam barrels and Fray tangles only | none | Seeing further, but the Thinned notice you from twice as far |
| **Homebound** `homebound` | Any gear | I | If you die beyond the wound, this item comes home with you, even in a Severed world | Treasure: Rift Trial gold, Keeper drops | Curse of Vanishing | Each Homebound item you wear beyond the wound adds 5% drain |

Rules every one of them obeys:

- None of them makes Dimensional Collapse, the curses or the expedition loop irrelevant. Thrift and the woven tiers
  stack multiplicatively, and the whole stack is capped at the drain floor that exists today (0.5 of normal).
- Woundbane's lower bonus against bosses keeps the Elder fights as they are balanced.
- Homebound respects the Severed ending: it is a cost paid in advance, not a way around the ending.

Tests: each effect in a gametest; exclusivity and table eligibility through the vanilla registry; a Thrift Collapse
gives the extra stack; Homebound items survive a Severed death and nothing else does.

---

## 6. Recipe catalogue

All new recipes appear in the recipe book and unlock through advancements (2.33).

| Recipe | Type | Ingredients | Unlocked by | When | Economy note |
| --- | --- | --- | --- | --- | --- |
| Wound Lens | Shapeless | Spyglass, Echo Shard, 2 Seam Silk | Holding Seam Silk and a Spyglass | After the first Seam visit | One-off cost; no farm value |
| Plumb Line | Shapeless | Echo, Ember, Pale and Silk Thread, Iron Chain | Holding a thread | 2.39 investigation | Threads are unique per player |
| Weaver's Loom | Shaped | 4 Hush Pillars, 2 Seam Silk, Loom, 2 Tether Fragments | Slaying an Elder | 3.0 | One counts per world |
| Veil Lantern (Veil Wing use) | Shaped | Veil Wing, Soul Lantern, 2 Seam Silk | Holding a Veil Wing | Seam | A soul ward that also wards against storms (2.36); Veil Wings had one use |
| Echoing Journal (Echo Shard use) | Shapeless | Tether Journal, Echo Shard | Holding an Echo Shard | Any | The journal lets you hear its toasts again (re-reads pages aloud). Cheap and cosmetic |

Recipes refused: anything that turns Tether Fragments into Seam Silk or the other way round. They stay two economies
on purpose: fragments from beyond, silk from the Seam.

---

## 7. World-event catalogue (2.36)

Each event runs on the framework: a trigger, a warning, an effect, an end and a cleanup, all configurable, each with a
gametest hook (`/tether event <id>` for operators). No two events start for the same player at once, and none starts
during an Elder fight or within 2 minutes of another.

| Event | Trigger | Where and when | Warning | What happens | Options | Reward / consequence |
| --- | --- | --- | --- | --- | --- | --- |
| **Echoes of Collapse** | A player comes within 24 blocks of a spot where anyone collapsed, at least 20 min later | Nether, End | Chains rattle, whispers, the collapsed player's shape kneels in chains | Stand within 4 blocks to listen: after 15 s the echo lets go. While listening, drain ×2 within 16 blocks | Listen (costs time), or leave | The listener: 1 Tether Fragment and 30 atonement. The collapsed player: half of the atonement that death cost them (owed if offline). One echo per Collapse |
| **Unstable Anchors** | A Wound Storm, or a Lurker that drank, near an anchor without a soul ward | Overworld, after the seal; in a Severed world too | The anchor cracks and spits sparks | The anchor leaks 2 s of charge per second for each player | Use an Amethyst Shard on it to steady it | Unsteadied after 5 min: it loses a quarter of everyone's charge. A soul ward prevents it entirely |
| **The Unanchored Build** | 1% per minute per player on an End expedition, 30 min cooldown | Outer End islands | Endermen stop and turn to one spot | Three Endermen lay a ring of end stone round a pearl, block by block, over 60 s | Don't look at them and let them finish, or look and anger them | Finished: they step into the ring and vanish. Left behind: Ender Pearls, a Tether Fragment, a margin note on XIII |
| **Frayed Seam** | 0.5% per minute per player in the Seam, once per visit | One island in the Seam | The fog turns the green of the Fray; strands snap in the distance | For 3 min the island frays: strands hang, Frayed Rays rise, the Thinned hunt, drain ×1.5 on it | Stay for the tangle, or cross to another island | A tangle (Fray loot) surfaces on the island |

Mended and Severed:

| Event | Before the ending | Mended | Severed |
| --- | --- | --- | --- |
| Echoes of Collapse | Yes | Yes, softer: no drain while listening (the wound is closed but remembers) | Yes; the echo of a Severed death shows what was destroyed, and returns one item of it |
| Unstable Anchors | After the seal | No (the storms are over) | Yes |
| The Unanchored Build | Yes | Yes | Yes |
| Frayed Seam | Yes | Yes | Yes |

---

## 8. Journal and advancement plan

### 8.1 Rules

Every new page has a stable id in `JournalEntry` (appended, never reordered), a trigger, EN and NL text in the
Wanderer's voice (or, for the second hand, its own voice), and is stored per player like the existing pages. A page
already owned never toasts again. Existing saves keep their pages; new ids simply start locked.

### 8.2 The second hand (margin notes)

A note in another hand appears in the margin of an existing page once the player has found what it refers to. The
Wanderer's text is never changed. The notes disagree with it, and they do not explain themselves.

| Page | Note (first lines) | Unlocked by | Release |
| --- | --- | --- | --- |
| XVIII The Ruined Gates | "Still warm. You looked longer than they did." | Finding a warm chain | 2.34 |
| XXX The Wanderer | "First? I tied my cord before you were born." | Reading the Loose Leaf | 2.34 |
| XIX The First Wound | "A door. Yes. Ask who held it open." | Reading the Loose Leaf | 2.34 |
| XIII The Unanchored | "They nearly finished. I watched them nearly finish for a hundred years." | The Unanchored Build | 2.36 |
| XII The Weavers | "Not nothing. Unstrung." | Stringing the frame | 2.39 |

### 8.3 New pages

| Id | Title | Trigger | Release |
| --- | --- | --- | --- |
| `held` | XXXIX. The Held | Reading the Loose Leaf | 2.34 |
| `echoes` | XL. Echoes | Listening to an echo of Collapse | 2.36 |
| `frame` | XLI. The Frame | Stringing an Ancient City frame | 2.39 |
| `first_knot` | XLII. The First Knot | Reaching the First Knot | 2.39 |
| `continued_mended` / `continued_severed` | XLIII. In My Own Hand | After the ending, at the first loom | 3.0 |

### 8.4 The First Weaver: what the player observes, infers, and cannot know

| Step | Observe | Infer | Uncertain | Next clue | Changes understanding |
| --- | --- | --- | --- | --- | --- |
| Frame (Ancient City) | Silk strung on the frame shows figures stepping into a weave | The Weavers sealed something here, with themselves | Who stepped in first | An Echo Thread is left in the frame | The frame was not empty, only unstrung |
| Bastion | A ledger of gold paid in piglin hand, every line struck through but one | The piglins paid the Ferryman for someone's crossing | Whose crossing | An Ember Thread tied to the ledger | Someone bought a passage the other way |
| End city | A ring of end stone round an empty socket, where an anchor was nearly finished | The Endermen were building with someone's help | Whose help | A Pale Thread in the socket | The Unanchored had a teacher |
| Loom (Seam) | The half-woven tapestry shows a figure tying the first knot | A first Weaver tied the Tether's first knot | Whether that was the Wanderer, the Held, or neither | A Silk Thread | The Wanderer's "I was the first" is one claim among three |
| The First Knot (Fray) | A knot the size of a hall, and something tied into it | The Knot Mother keeps the first knot | Whether she is the First Weaver, what the Fray made of her, or the Held | None: the chapter ends here | The Tether was tied, not grown |

Mended world: the frame shows the weave closing. Severed world: it shows it fraying. Same steps, same rewards.

### 8.5 Advancements to add

| Id | Title | Parent | Release |
| --- | --- | --- | --- |
| `trial_medal` | Keeping Score | `seam_returned` | 2.33 |
| `relic_bound` | Each Gives, Each Takes | `keeper_slain` | 2.33 |
| `pact_kept` | Ask for More | `relic_bound` | 2.33 |
| `island_fell` | Struck by Stone | `seal_broken` | 2.33 |
| `warm_chain` | Still Warm | `first_expedition` | 2.34 |
| `held_found` | What the Chain Held | `warm_chain` | 2.34 |
| `echo_heard` | It Remembers | `collapse` | 2.36 |
| `unanchored` | A Way Home | `first_expedition` | 2.36 |
| `frame_strung` | Unstrung | `seam_returned` | 2.39 |
| `knot_mother` | The First Knot | `fray_risen` | 2.39 |
| `tapestry` | The Unfinished World | `ending_mended` / `ending_severed` (two) | 3.0 |

### 8.6 The Tapestry's panels (3.0)

| Panel | Thread from | Mended boon | Severed boon |
| --- | --- | --- | --- |
| Roots | The Matriarch | Hollowlings bring gifts twice as often | Keepers no longer hunt you when the Tribe hates you |
| Toll | The Ferryman | Nether drain −10% | One level back per safe return |
| Unravelling | The Last Weaver | +1 min in the Seam | The Thinned no longer move while any player looks at them, even late |
| Chain | The Held | Anchors hold 5 min more | Held's Chain grace +10 s |
| Knot | The Knot Mother | The Fray's time runs 1.5× instead of 2× | Strands crumble half as often |
| Heart | The ending itself | Echoes of Collapse return all, not half | Homebound costs no drain |

---

## 9. Balance and exploit analysis

### 9.1 Exploits and their answers

| Risk | Where | Answer |
| --- | --- | --- |
| Farming warm chains | 2.34 | One check per ruined portal, ever (recorded by structure start). One chamber per warm portal. The relic is in the chamber's barrel once |
| Reading the Loose Leaf for rewards | 2.34 | Reading gives a page, never items |
| Abusing the Held's Chain grace by logging out | 2.34 | Logging out during the grace counts as a Collapse (the existing rule for logging out at zero charge) |
| Thrift without its price | 2.35 | The extra stack is applied server-side at Collapse, from the item's enchantment at that moment, whatever slot it is in |
| Homebound duplication | 2.35 | Items are removed from the death drops before they spawn, then given back on respawn; one code path, tested with a full inventory |
| Echo farming by collapsing on purpose | 2.36 | An echo pays the listener only if they are not the one who collapsed, and pays the collapsed player at most what that death cost |
| Re-triggering events | 2.36 | Per-player cooldowns stored in `PlayerData`; events never start in creative or spectator |
| Unstable anchors as griefing | 2.36 | Only natural triggers; an Amethyst Shard always fixes it; soul wards prevent it |
| Multiplayer reward duplication | all | Rewards are granted server-side per player id, recorded before the reward is given |
| Unloaded chunks | 2.34, 2.36 | Chambers are placed only in loaded chunks next to a player; events end cleanly when their chunk unloads |

### 9.2 Balance targets (checked in 2.37)

| Measure | Target |
| --- | --- |
| A first Nether expedition at Tethered | 8 to 12 minutes without relics |
| Drain floor with every reduction | Never below 50% of normal |
| Elder time-to-kill with Woundbane V against with Sharpness V | Within 10% |
| Atonement per hour from echoes | At most a quarter of what active expeditions give |
| Relic pick rate in a two-player test | No relic is never worth binding |

---

## 10. Technical implementation plan

| Area | Approach |
| --- | --- |
| Recipe unlocks | Generated advancement JSONs under `advancement/recipes/` (vanilla layout), from a script in `tools/` |
| Loot | JSON loot tables under `loot_table/chests/`, `loot_table/entities/`, `loot_table/gameplay/`; code rolls them through `server.reloadableRegistries().getLootTable` |
| Warm chains | A map in `DataStore.WorldData` (structure start → state), saved with the rest of the world JSON; placement like `HollowService.wild`; ruined portals found through `structureManager().getStructureWithPieceAt` with the vanilla ruined-portal tag |
| Margin notes | `JournalEntry` gains notes; `PlayerData` keeps the set of note ids; the journal screen draws a note in a second ink |
| Relics | A tenth `Relic` value; effects in `RelicService` and the expedition's Collapse path |
| Enchantments | Data-driven JSON plus tags; code hooks in the drain calculation, the Collapse and death paths, the fog mixin |
| Events | `world/events/`: an `Event` interface and a scheduler driven from `ServerEvents.tick`; Wound Storms moved onto it with the same numbers |
| Persistence | New fields default to empty, so 2.x saves load unchanged; no migration step is needed until 3.0 (tested in the beta) |
| Networking | Reuse existing payloads (toasts, FX); one new payload for Lens readings |
| Config | Every number above is a config key with EN and NL names and descriptions |

---

## 11. First milestone: 2.33 Rooted in Vanilla

Chosen first because it is the foundation the later releases need (recipe book, loot tables), and because it was the
next step on the road.

1. Generate recipe-unlock advancements for all recipes; stop granting six at first join (existing players keep what
   they know); a config key restores the old behaviour.
2. Add the four advancements of 8.5 marked 2.33.
3. Gametests for shield, milk, peaceful and mobGriefing against the mod's creatures.
4. Move code-held loot into loot tables; test that a datapack can change a Seam barrel.
5. Fix the walkthrough discrepancies (1.4) in both walkthroughs.

The vertical slice (2.34 The Warm Chain) follows directly. It needs a visual proposal: the chain, the chamber, the
relic and Loose Leaf textures, and the margin note in the journal.

---

## 12. Acceptance criteria per release

Every release:

- CI green: all server gametests and the client gametest.
- Screenshots looked at, of every visible change.
- EN and NL lang complete (the existing check), every config key with name and description.
- CHANGELOG entry; both walkthroughs and the roadmap document updated; a download link.
- No change to existing registry ids, saved data keys, commands or config keys. Anything that would need one is
  flagged in the changelog with its migration.

Release-specific criteria are under each release in 3.1.

---

## 13. Audit inventory (2.32.1)

See `docs/inventory-2.32.md`.
