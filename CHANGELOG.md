# Changelog

## 1.1.0: The Lurker

*It sees. It waits. It takes.*

### Added
- **The Lurker**, a new mob: a member of an ancient tribe that watches you from the treetops, the ridges and the far
  bank of the lava. Appears rarely (with a cooldown per player), day or night, in the Overworld and the Nether, 36-60 blocks away on high ground and preferably half hidden behind leaves or a ridge. Stalkers and drainers are rarer still.
  - **Watching:** stands on high ground 24-40 blocks away and stares. Come too close or stare back too long and it is gone.
  - **Stalking:** sometimes it comes closer, but only while you are not looking.
  - **Taking:** it steals an item, part of your experience or a piece of armour, or lays a **curse** (one random
    penalty, forced). Then it waits at a distance: kill it in time to get your loot back.
  - **Draining:** it appears beside a charged anchor, holds the anchor's light in its arms with its jaw open and
    trembling, and drinks the anchor dry while the light streams into it. Come close to interrupt it.
- **Its own body**: a hunched, cloaked giant (about 3.5 blocks) on long knuckle-walking arms, with ragged cloak strips,
  strands dripping from its mask, violet eyes and glowing cracks. Fully procedural animation: breathing, head tracking
  with a curious tilt, swaying strands and cloth, a knuckle walk, an unfolding reach when it takes, and raised,
  trembling arms while a thin, flowing beam pours from the anchor into its hands.
- A full sound set: heavy dragging footsteps (softer while it stalks), slow rattling breaths and whispers, a hiss when hurt, a death that frays into whispers, plus take, drain and vanish sounds, a new journal page (*XVI. The Tribe*) and three
  advancements (*It Sees*, *It Takes*, *Take It Back*).
- `/tether lurker <watch|stalk|drain>` for operators.
- Lurker settings in `config/extremedimensions.json`.

## 1.0.0: The Professional Pass

### Added
- **Collapse sequence**: when the charge runs out the Tether drags you out over three seconds (souls streaking past, tunnel vision, a fade to black), skips the death screen and fades you back in at home. New *collapse* sound instead of the explosion.
- **Advancements**: a tree of 12 milestones, from *The Tether Knows* to *The Wanderer's Account*.
- **Creative tab** "Extreme Dimensions" with every item.
- **Item tooltips** for all mod items, and fuel values on every anchor fuel.
- **Config file** `config/extremedimensions.json` with every balance value.
- **Tether Marks**: penalties shown as cards beside the inventory, with runes, stacks, time left and lore on hover.
- New mod icon from the key art.
- Audio feedback (a low note) when an action is refused, now that on-screen messages are gone.

### Changed
- **One visual style** for the HUD, the anchor screen, the penalty cards, the journal and the Tether Marks.
- The **journal** is now a two-page book spread.
- The **expedition timer** is a framed panel with the dimension, time, distance and drain.
- **Every player has their own time** in shared anchors; a death beyond the wound only costs that player's time.
- The physical tether lies on the ground wherever you walk. It snaps beyond 5 blocks in the Overworld and vanishes
  on a safe return or a death.
- All text is translatable.
- Rarity colours on items.
- The server code is split into focused subsystems (anchor, expedition, tether, penalty, journal, network).

### Removed
- On-screen text notifications. Feedback comes through the HUD, animations, particles and sound.
- The full-screen portal transition.

### Fixed
- "Format error" on penalty cards with a percentage.
- Penalties no longer use vanilla effects (except Darkness), so they never show twice beside the inventory.
- Tether Marks no longer overlap vanilla status effects beside the inventory.
- Tether chain segments were invisible on 26.3 (block displays take the block as a plain id there).
