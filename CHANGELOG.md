# Changelog

## 2.1.0: The Seam, Rewoven

*The Seam is not a place. It is a breath, and now it has a sky to breathe.*

### Added
- **A sky of its own**: the Seam now has a hand-built, high-resolution sky (six 1024 px faces): violet nebulae, a
  galaxy band, a vortex turning at the zenith, a red glow where the Nether bleeds through, pillars of light rising from
  a sea of clouds and the silhouettes of far islands on the horizon.
- **Colossal architecture**, built over a few seconds as you approach so the server never stalls:
  - **Great Spires**: three or four tiers of deepslate stepping inward, tall pointed windows of violet glass, a diamond
    rose window over the door, buttresses crowned with pinnacles, banners, chains and lanterns on every ledge, flying
    buttresses, a glowing core of silk and a spiral stair inside, up to a reliquary at the top.
  - **Giant Looms**: two pillars forty blocks high, a beam across the top, hundreds of threads of chain and silk and a
    half-woven tapestry of the Weaver's sign.
  - **Villages of the Thinned**: tall, narrow houses with steep roofs and warm windows around a plaza with a bell spire.
  - **Hanging Towers** that grow upside down beneath their islands, and **Halos**: tilted rings of stone turning
    around floating crystals.
  - **Chain bridges** between the islands: dark plank decks, lantern posts, sagging chain rails and soul lanterns
    swinging below. Some have fallen through.
- **Islands** are bigger and deeper: mossy tops with grass and flowers, vines, pale moss and roots hanging from their
  undersides, long stone fangs, soul lanterns on chains, and **light pouring off their edges**, visible from far away.
- **The Heart's crown**: four obelisks on floating plinths and a great halo high above the arena, and a beam of light
  rising from the Heart that can be seen from across the Seam.
- **Journal button** in the inventory, right of the recipe book: read the journal without carrying it.
- `/tether seam_showcase` (operators): builds one of every Seam structure in a row around x = 2880..3300, z = 2916.

### Changed
- The hoard's whispers are much quieter, never overlap themselves and play once per hoard however many players are
  near.
- The Seam's light and fog are violet instead of teal.
- Seam cells are now 72 blocks instead of 34, so structures have room. Parts of the Seam that were already explored
  in an older version stay as they were; for the full effect, try a world that has not visited the Seam yet.


## 2.0.0: The Mending

*Mend it, and the world forgets the wound. Sever it, and the world forgets the Tether.*

### Added
- **The Heart of the Seam**: once the seal is broken, every rift into the Seam opens near its centre, where a great
  island of hush brick waits around an altar of silk, ringed by four pedestals and pillars of soul light.
- **The Starved One**, the final boss: step onto the altar and it rises, tall, white as bone and thin as hunger.
  - It hunts with blinks through the shadows; wounded, it **bites through your thread** (every rift near the Heart is
    flung to the edge of the island); near death, it **drags everyone towards it**.
  - Feed **Seam Silk** to two of the four pedestals and the light pins it down: for eight seconds it is stunned and
    takes double damage.
  - **The Thinned fight beside you** if anyone in the fight has befriended a Hollowling.
  - Time in the Seam stands still near the Heart while it lives. Leave, and it sinks back, whole.
- **The choice**: it drops a **Mending Thread** and the **Severing Shears**. The first one used, by anyone, decides the
  world forever:
  - **Mended**: no more Wound Storms, the tribe brings offerings instead of stealing, and every expedition drains 20%
    slower.
  - **Severed**: no more timer beyond the wound at all, but whoever dies in the Nether or the End loses everything they
    carried, and the tribe goes back to stealing.
- **The Wanderer** is revealed in the journal's last pages (*XXIX. The Heart*, *XXX. The Wanderer*, *XXXI. The Ending*),
  and four new advancements (*The Lock Was a Dragon*, *The Hunger Ends*, *Mended*, *Severed*).

## 1.6.0: After the Dragon

### Added
- **The seal**: the Ender Dragon was the lock on the first wound. When it dies, every player sees the world flash white
  and crack, and the journal tells what was done (*XXVII. The Seal*).
- **The tribe turns**: Lurkers no longer steal. A watching Lurker that goes quietly may leave an offering behind: a
  Tether Fragment, a Wanderer's Page, Seam Silk or an Echo Shard.
- **The Pale Void starves**: End expeditions drain 25% faster while the seal is broken.
- **Wound Storms** in the Overworld (*XXVIII. Wound Storms*): the edges of your sight tear with violet light, the sky
  rips open in seams, darkness pulses, anchors gutter, and creatures from the Nether and the End leak through around
  you for a minute.
- `/tether break_seal` and `/tether storm` for testing; storm chance, cooldown and length in the config.

## 1.5.0: Weaver's Craft

### Added
- **Soul-Woven Tether** (Tether, 2 Seam Silk, a soul lantern): 15% slower drain, 3 more blocks of reach in the
  Overworld, one more minute in the Seam.
- **Seam-Woven Tether** (Soul-Woven Tether, 4 Seam Silk, an Echo Shard, a Breeze Rod): 25% slower drain, more reach, two
  more minutes in the Seam, and once per expedition it **unravels you home** instead of letting you collapse (the
  Tether is spent).
- **Scars**: every penalty stack you endure to the end leaves a scar; each scar makes you 3% harder to drain (up to
  five).
- Journal page *XXVI. Woven*.


## 1.4.0: The Seams

*Between the wounds there is a place that is not a place.*

### Added
- **The Seam**, a new dimension: islands of quiet hush stone hanging in a teal-black nothing, lit from everywhere and
  nowhere. Every island is built as you explore it:
  - **plain islands** with soul lanterns on crooked posts and old cobwebs;
  - **Weaver looms**: tall pillars of hush brick strung together with sagging strands of silk, rich in seam veins;
  - rare **villages of the Thinned**: low huts, cold soul lanterns, a field of graves, and a barrel of Seam Silk and
    lore.
  Narrow causeways of hush brick link many islands, and the Thinned (Lurkers that only watch) stand on the edges.
- **Seam Needle**: use it on crying obsidian (a ruined portal, say) to tear a rift into the Seam for fifteen
  seconds. Anyone who steps in goes through. Crafted from 2 Tether Fragments, a Wanderer's Page and an Echo Shard,
  or from 3 Seam Silk and a Tether Fragment.
- **The thread home**: you arrive beside a return rift with five minutes on the clock. The rift drifts a block at a
  time, and a thread of silk is laid behind you wherever you walk. Step back into the rift to return to where you
  came from. Run out of time, or fall into the nothing, and the Seam drags you out, cursed.
- **New blocks and items**: Hush Stone, Hush Bricks, Seam Vein (drops Seam Silk) and Seam Silk.
- The expedition HUD shows *The Seam* in soul teal, with the distance to your rift.
- Three journal pages (*XXIII. The Seam*, *XXIV. The Loom*, *XXV. The Thinned*), three advancements
  (*Between the Wounds*, *The Thread Holds*, *Too Late a Guest*) and two new sounds (the rift tearing open and the
  hum of the Seam).
- `/tether seam` sends you straight in; `seam_seconds` in the config.


## 1.3.0: What Was Taken

*It does not want your things. It wants to remember what having things was like.*

### Added
- **Lurker hoards**: a Lurker that escapes with your loot now runs it home, and you can follow it. Your hoard is a
  hidden nest 40-70 blocks away: trampled mud (or soul soil in the Nether), crooked posts, cobwebs, bones, a skull,
  black candles nobody lights, and a barrel with everything it took. Experience comes back as bottles. A column of
  ink rises from every hoard and it whispers when you are close. Old hoards full of trinkets (and sometimes a
  Wanderer's Page or a Tether Fragment) lie in dark forests, pale gardens and soul sand valleys.
- **Hollowlings**, the tribe's young: knee-high, ash-grey, big-headed and quick. They play around a hoard, freeze
  like statues when you look at them, giggle and run if you stare, and when you look away they sneak up and steal
  small things (never tools, weapons or armour) to carry back to the hoard. Kill one to get it back.
- **The Keeper**, the first boss: a huge old Lurker with a bone-white mask. Open or break a hoard, or hurt its young,
  and it comes roaring through the trees, with a boss bar, darkness, a hushing screech and blinks behind you. Leave
  what it keeps and it lets you go; slay it and the hoard is yours, plus two Tether Fragments and a Wanderer's Page.
- **A Hollowling friend**: offer a Hollowling a Tether Fragment in soul light (a soul lantern, torch, campfire or
  fire within 4 blocks) and it may come to trust you. It follows you everywhere, chitters towards monsters you have
  not seen, leaps in when a Lurker reaches for your things, and every 20 minutes brings you something it found.
- Three journal pages (*XX. The Hollows*, *XXI. Little Thieves*, *XXII. The Keeper*) and three advancements
  (*Finders Keepers*, *Losers Weepers*, *Bound in Soul Light*).
- New sounds: Hollowling chitters, giggles, squeals and a trill, the Keeper's roar and the hoard's whispers.
- `/tether hoard` builds an old hoard in front of you; new config options for hoards, Hollowlings and the Keeper.


## 1.2.0: Woven In

*The world was here before the wound. It remembers too.*

### Added
- **Four new particles of our own**: soul motes (teal light that spirals up from the Tether, journal pages and soul
  wards), ember motes (sparks rising from Nether anchors), void motes (violet twinkles above End anchors) and Lurker
  wisps (curls of ink in its trail and where it vanishes).
- **Soul wards**: a soul lantern, soul torch, soul campfire or soul fire within 4 blocks of an anchor keeps Lurkers
  from draining it. A warded anchor is circled by soul light, and a Lurker caught drinking when you light one shrieks
  and is gone. The tooltips of soul light items say so.
- **Wanderer's Pages** in the world: ruined portals, nether fortresses, bastions, stronghold libraries, ancient cities
  and end cities can hold a torn journal page (and sometimes a Tether Fragment). Using a page adds it to your journal.
- **Three new journal pages**: *XVII. Soul Light* (when you first see a Lurker drink), and *XVIII. The Ruined Gates*
  and *XIX. The First Wound*, which can only be found.
- **The world notices the Lurker first**: wolves growl, cats and ocelots hiss, foxes screech, horses shy, parrots
  mimic something they should not know, and grazing animals stop and stare in its direction.
- **Anchors hum**: charged anchors give off a low respawn-anchor drone (Nether) or a soft amethyst chime (End).
- **Breeze Rods** now fuel Nether anchors (0:30) in place of Blaze Rods, so blaze rods stay free for brewing and
  Eyes of Ender.
- **The chain clinks** quietly as it is laid (amethyst in the End), and beyond the wound a faint soul light clings to it.


## 1.1.0: The Lurker

*It sees. It waits. It takes.*

### Added
- **The Lurker**, a new mob: a member of an ancient tribe that watches you from the treetops, the ridges and the far
  bank of the lava. Appears rarely (with a cooldown per player), day or night, in the Overworld and the Nether, 50-72 blocks away on high ground and preferably half hidden behind leaves or a ridge. Stalkers and drainers are rarer still.
  - **Watching:** stands on high ground and stares. Come too close or stare back too long and it is gone.
  - **Stalking:** sometimes it comes closer, but only while you are not looking, slow at first and faster as it
    closes in.
  - **Taking:** it steals an item, part of your experience or a piece of armour, or lays a **curse** (one random
    penalty, forced). Then it **runs**: off on three limbs with the loot clutched to its chest, laughing, a trail of ink
    behind it, and only after a good stretch does it fold into the air. Kill it in time to get your loot back.
  - **Struck:** hit it and it shrieks and runs the same way.
  - **Draining:** it appears beside a charged anchor, holds the anchor's light in its arms with its jaw open and
    trembling, and drinks the anchor dry while the light streams into it. Come close to interrupt it.
- **Its own body**: a hunched, cloaked giant (about 3.5 blocks) on long knuckle-walking arms, with ragged cloak strips,
  strands dripping from its mask, violet eyes and glowing cracks. Fully procedural animation: breathing, head tracking
  with a curious tilt, swaying strands and cloth, a knuckle walk, a bounding three-limbed run with glances over its shoulder, an unfolding reach when it takes, and raised,
  trembling arms while a thin, flowing beam pours from the anchor into its hands.
- A full sound set: heavy dragging footsteps (softer while it stalks), slow rattling breaths and whispers, a guttural snarl when hit, a shriek when startled, a hissing laugh as it runs off, a death that frays into whispers, plus take, drain and vanish sounds, a new journal page (*XVI. The Tribe*) and three
  advancements (*It Sees*, *It Takes*, *Take It Back*).
- `/tether lurker <watch|stalk|drain>` for operators.
- Lurker settings in `config/extremedimensions.json`, including from how far it can take an item, experience or
  armour or lay a curse (`lurker_*_range`, 0 = never) and how likely each is (`lurker_*_weight`).

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
