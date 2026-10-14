# Changelog

## 2.13.0: Wrath of the Elders

*They were vast. Now they fight like it.*

### Added
- **Three new blows for every Elder**, each called out on screen as it comes:
  - The Matriarch: **Thornbound Charge** (she rears back and hurls herself at you, ploughing a furrow through the
    ground and throwing the earth into the air), **Cathedral of Thorns** (both hands into the ground, and three rings of
    thorns burst out of it one after another) and **A Hundred Lanterns** (her branches thrown wide, her lanterns hurled
    as burning bombs).
  - The Ferryman: **Crossing of the Dead** (the boat backs off, then charges straight through you, splitting the
    ground and leaving soul fire in its wake), **Gaol of Chains** (a cage of chains bursts out of the ground round you
    and crushes inwards: break out) and **Tide of the Styx** (two tides of soul fire roll out over the ground: jump them).
  - The Last Weaver: **Seraphic Lance** (its halo spins up and fires a beam that sweeps across the ground and carves a
    burning trench), **Cat's Cradle** (a web of threads strung across the ground round you, pulled taut until it cuts)
    and **A Thousand Needles** (rings of needles spiral down onto you, and a great needle last of all).
- **An ultimate for every Elder** at half its health, ten seconds long: black bars close in, its name is slashed
  across the screen, the world fog rolls in and the Elder shrugs off most harm while it builds.
  - The Matriarch's **Verdant Requiem**: hands into the ground, roots bursting from it in a spiral, light gathering over
    everyone she marks, her chest opening, and the bloom.
  - The Ferryman's **The Final Toll**: the boat rises into the air, soul fire rains while the bell tolls three times,
    and the boat comes down.
  - The Last Weaver's **The Last Tapestry**: it rises with its arms spread, threads run to everyone near while time
    slows for them and a dome of threads closes over the ground, and everything snaps.
  - When it breaks, **everyone within 150 blocks takes 2 random extreme curses**. Someone who has no room left for
    them has **everything they carry flung out around them** instead.
- **The world feels their blows**: great blows are real explosions; charges and beams tear furrows through the ground
  and throw the torn blocks into the air; the ground they strike turns to root and moss (the Matriarch), soul soil
  burning with soul fire (the Ferryman) or scorched earth and fused glass (the Weaver). Thorns and chains they raise
  sink back after a while. Containers and blocks a Wither cannot break are never touched; switched off with
  `elder_griefing` or the mobGriefing rule.
- **A real boss fight**: each Elder arrives with its name struck across the screen; the sky darkens under its health
  bar; the ground shakes under its steps and blows (the camera shakes, never your aim); shockwaves you can jump; the
  air fills with its leaves, ash or motes; it dies slowly, breaking apart in light.

### Changed
- **Faster**: the Elders move 1.5 times as fast and strike more often and more quickly (`elder_speed`,
  `elder_attack_speed`, `elder_windup_speed`).
- **Smoother**: they move with weight (speeding up, slowing down and turning with momentum, riding the ground with an
  eased height) and are sent to clients every tick, so they glide instead of jerking. Knockback is real now.
- **The Last Weaver is dressed far richer**: layered robes with glowing stitched runes, a sash with charms, spools and
  needles at its hips, a ring of floating needles, a veil and more ornament on its crown and halo.
- **The music never stops at the end of a track**: a few seconds before a track ends the next pass fades in under its
  last bars. With several Elders near, the track already playing keeps playing instead of restarting over and over.

### Settings
- New: `elder_speed`, `elder_attack_speed`, `elder_windup_speed`, `elder_griefing`, `elder_ultimate_at`,
  `elder_ultimate_seconds`, `elder_ultimate_radius`, `elder_ultimate_curses`, `elder_ultimate_scatter`,
  `elder_ultimate_guard`.

## 2.12.0: The Elders, Reborn

*They were never Keepers in other cloaks. Now they have their own shapes, and they are vast.*

### Changed
- **The Elders are colossal**, each with a model of its own (hundreds of parts, a texture and a layer of light that
  burns in the dark), built after the concept art:
  - **The Matriarch** stands seventeen blocks tall and nineteen wide: a trunk of bark in a double robe, rooted to the
    ground; a chest split by violet light; a great carved mask with slanted eyes under a crown of thorns; six
    branches arching from her back like antlers, hung with moss and lanterns; arms raised to reach, ending in hooked
    talons.
  - **The Ferryman** stands in a black boat nineteen blocks long, with a curling prow and a carved skull: hooded,
    eyes burning in the dark of the hood, chains across his robe and arms, a pole taller than he is with two lanterns
    on its crossbar, his other hand raised beside his hood with a vortex of violet light turning before the palm, and
    a great lantern hanging from his wrist.
  - **The Last Weaver** floats eighteen blocks tall, arms spread twenty blocks wide: a robe rotting into tatters, a
    mask with a cross of light under a crown of thorns, a great halo turning behind its head, and glowing threads
    running from its long fingers in loops and down to hanging lanterns.
- **New fights to match**: nothing that size walks a path, so each moves its own way and every blow is foretold by a
  ring of light on the ground (step out of it).
  - The Matriarch rises out of the ground by her hoard and drags herself after her prey. Close, she sweeps her talons
    across everything in front of her or wails; far, she shakes her lanterns down in fire onto everyone she hunts, and
    roots burst from the ground under them. Her brood still comes at 70% and 35%; below 35% she is faster.
  - The Ferryman sails over lava and stone alike, closing in and then circling his passenger with his head turned to
    them. He brings his pole down in a line of fire, drags his passenger back on a chain, looses soul lanterns that
    drift after them, and rings a knell that throws back anyone close. His toll, his drain and his fare still stand.
  - The Last Weaver floats above the ground and keeps its distance. Its threads hold players to the spot they stood
    on (and drain the Seam faster while they hold), it rains needles of light, and it flings back anyone who comes too
    close. Every hurt it does still unpicks Seam time, and its kin still come.
- **Each Elder has its own music** for the fight: "Cavernous Echoes" for the Matriarch, "Subterranean Pressure" for
  the Ferryman and "Final Collapse" for the Last Weaver. It plays for everyone near, holds vanilla music back, follows
  the Music slider and fades out when the fight ends or you leave it.
- The models and their textures come from `tools/elders` (run `python3 tools/elders/build.py`).

## 2.11.0: The Elders

*Older than the Keepers. Older than the hunger. Some of them were people once.*

### Added
- **The Matriarch** (Overworld and Nether): once you have slain three Keepers, the next hoard you wake is guarded by
  their mother. Taller than any Keeper, with a red boss bar everyone near can see. Below 70% and again below 35% of
  her health she calls her brood (Hollowlings that steal while she fights), and up close she wails: darkness,
  weakness and slowness for everyone within 16 blocks. She always leaves a relic, four Tether Fragments and two
  Wanderer's Pages. After she falls, hoards are guarded by Keepers again.
- **The Ferryman** (Nether, after the Ender Dragon falls): after five active minutes on a Nether expedition he may
  come to collect (one chance in five, more for every stack of Ferryman's Fee you carry). While he walks your Tether
  drains twice as fast (three times once he is below half health), each of his blows takes a level, and he pulls you
  back to him or rings a knell that weakens you. Go home with him still walking and he takes five levels anyway. Slay
  him for three Tether Fragments, an even chance at a relic, and one stack of Ferryman's Fee waived.
- **The Last Weaver** (the Seam, after the Starved One falls): the first visit to each village of the Thinned may wake
  its Weaver (two chances in five). Every blow it lands unpicks ten seconds of your Seam time, it draws you in on a
  thread, and below 60% and 30% of its health it calls its Thinned kin, who hunt everyone near it. Slain, it gives its
  thread back: a minute more in the Seam for everyone near, eight Seam Silk, three Tether Fragments and a relic.
- **A hungrier Starved One**: it gains half its health again for every player at the Heart beyond the first, and below
  30% of its health it turns ravenous: 30% faster, its tricks come sooner, and each one brings famine.
- Three journal pages (XXXII to XXXIV), three advancements (Mother of Masks, Fare Dodger, Unravelled) and
  `/tether boss matriarch|ferryman|weaver` for operators.
- Every number of the Elders is a world rule (Settings → World rules → The Elders), as is when the Ferryman and the
  Last Weaver first appear.

## 2.10.0: Rift Trials

*The Seam keeps score.*

### Added
- **Rift Trials**: every week the Seam asks one thing of a visit, with a twist that makes it harder. Take the trial
  up in the Trials screen (the rift beside the pact brand in the Curses screen, or a key you bind), then step through
  a rift with a Seam Needle. Only a safe return through the rift counts; a Collapse or a death loses the run.
  - Five trials, one a week in turn: **Vein-cutter** (break Seam Veins), **Hunter** (slay Veil Rays and Thinned),
    **Wanderer** (set foot on islands), **Far-walker** (get as far from your arrival as you can) and **Plunderer**
    (open Seam barrels nobody has opened before; each barrel counts once, ever).
  - Five twists: Still Seam (none), Short Thread (a quarter less time), Thin Hours (the Thinned hunt you from the
    start), Restless Rift (the way home drifts twice as often) and Heavy Silk (slowness for the whole visit).
  - **Medals**: bronze, silver and gold, each paying once a week: atonement and Tether Fragments, and a relic for gold.
    A trial can be run again as often as you like to climb the board, but a medal never pays twice in a week.
  - **The world's scoreboard**: every player's best run this week (ties go to whoever came back with more time to
    spare), and the best three of the last trial.
  - The trial under way shows under the Seam timer: its name, the score against the next medal and three medal pips.
- The trial changes every Monday (UTC), the same for every world. Every threshold, reward and the length of a trial
  are world rules (Settings → World rules → Rift Trials), as is when trials open (from the start by default).

## 2.9.0: Pacts

*Ask the wounds for more, and they will give it. For a price you name yourself.*

### Added
- **Pacts**: swear extra stacks of your curses before an expedition, for what a safe return brings. Open the Pact
  screen through the brand in the top-left corner of the Curses screen (gold while a pact is sworn).
  - Beyond, Everywhere and Extreme curses can be sworn (the Overworld set cannot), up to each curse's maximum.
  - Sworn stacks act only on expeditions. At home they do nothing, and they never count as curses you carry: they do
    not make atonement dearer and cannot be atoned, mended or lifted.
  - **Heat** is the pact's weight: one per sworn stack, two for an Extreme stack, up to 30 by default.
  - **A safe return** keeps the pact: 6 atonement per heat, a Tether Fragment for every 3 heat, and from heat 4 a
    chance at a relic (5% per heat, at most 80%). The full reward takes 10 active minutes beyond the wound; a shorter
    expedition pays its share, so stepping through the portal and straight back (or standing idle) earns nothing.
  - **A death or a Collapse** breaks it: that expedition brings nothing, but the pact stays sworn for the next.
  - A pact is sworn or renounced at home only, never on an expedition or in the Seam.
- **When pacts open** is a world rule: after the Starved One falls (default), after the Ender Dragon falls, or from
  the start. Every number of a pact is a world rule (Settings → World rules → Pacts).

### Fixed
- The current difficulty under "Difficulty" in the world rules overlapped the search box.

## 2.8.0: Relics

*Things the wounds left behind. Each gives, each takes.*

### Added
- **Eight relics**, bound to your Tether one at a time (use one to bind it; the one bound before comes back). A bound
  relic cannot be lost to a death, a Lurker or a full bag. The Curses screen shows it; click it to take it off.
  | Relic | Gift | Price |
  | --- | --- | --- |
  | Ember Heart | 20% less drain in the Nether | 20% more in the End |
  | Pale Shard | 20% less drain in the End | 20% more in the Nether |
  | Hungering Hand | +2 blocks of reach | one heart less |
  | Featherbone | falls 8 blocks further without harm | 2 armour less |
  | Keeper's Eye | twice the fragments from wild kills | half the atonement from kills |
  | Tithe Bell | half again the experience | a level on every safe return |
  | Lurker's Tooth | Lurkers cannot take items or armour | they come twice as often |
  | Seam Lantern | a minute more in the Seam | at night, monsters notice you a quarter farther |
- **Where they are found**: a slain Keeper leaves one about one time in three; the Starved One leaves one for every
  player who fought it; Seam chests sometimes hold one (more often the richer the chest).
- Every number of every relic is a world rule (Settings → World rules → Relics).

### Fixed
- The settings screen did not compile against a variable name clash (2.7.1 never reached a build).

## 2.7.1: Difficulty

### Added
- **Five difficulties** for the world's rules, applied by an operator in one go (Settings → World rules → Difficulty,
  with a confirming second click) or from the console with `/tether preset <name>`:
  - **Pilgrim**: a gentle story (long expeditions, cheap atonement, deaths undo no penance, no multiplied offers or
    Extreme cards, rare Lurkers and storms).
  - **Seeker**: forgiving.
  - **Tethered**: the standard.
  - **Starved**: hard.
  - **Severed**: merciless (short expeditions, two cards per offer, a death undoes most penance).

  A difficulty sets fifteen values (atonement, death loss, charge, drain, multiplied offers, cards per offer, Extreme
  cards, Lurkers, Wound Storms, fragments); every other setting stays. The screen shows which difficulty the rules
  match, or "Custom".
- **The first-expedition guide closes with a cross**: open the chat (T) and click the × on its card. It can be turned
  on again under Settings → Display.

### Notes
- Only operators (and the owner of a singleplayer world that is not open to LAN) can change the world's rules or its
  difficulty; the server checks every request, whatever the client sends.

## 2.7.0: Settled In

*Your game, your way: settings in the game, a guide for the first expedition, calmer screens on request, and Dutch.*

### Added
- **Settings in the game**, from the gear in the Curses screen, a key of your choice (Controls) or Mod Menu's
  "Configure" button.
  - **Display** (kept on your computer): reduce flashing, colour-blind colours, where the anchor meters sit (any
    corner), the expedition timer at the top or above the hotbar, HUD size (75 to 150%), and the first-expedition
    guide.
  - **World rules**: every value of the world's config and every curse's maximum and strength, with a search field.
    Everyone can read them; operators (and the owner of a world that is not open to LAN) can change them, which saves
    the server's config at once, with no restart. "Defaults" fills in the original values. Outside a world it edits
    this computer's own config.
  - Every player now plays by the server's numbers: the HUD and screens use the server's values, not your own file's.
- **First-expedition guide**: a small card names the next step (set a lodestone by a portal and feed it, bind a
  Tether, step through, come back in time) until you are home. Players with an expedition behind them never see it.
- **Dutch** (Nederlands): the whole mod, journal included.
- **Key mappings** (unbound by default) for the Curses, the Tether Journal and the settings.

### Changed
- **No big title on respawn.** Dying no longer shows "The Tether Tightens" or "Expedition Ended" across the screen;
  the dark haze and the sound remain. Servers that want the title back set `respawn_title` to 1.
- **Curses take longer to atone for**: 120 points for a curse's last stack (was 100) and 60 more for every stack above
  it (was 50), from a balance model of points earned per hour against deaths. A config that still holds the old
  defaults takes the new ones; values someone chose stay.
- **Tether Fragments only drop from wild kills**, by the same rules as atonement from kills: a blaze spawner no longer
  yields fragments that lift curses.
- **Reduce flashing** removes the strobes, shakes and glitches (the Collapse, the broken seal, Lurker thefts, Wound
  Storms, the last seconds of an expedition, the curse cards) and turns the Lurkers' glow into a soft swell.
- **Colour-blind colours** keep the four curse sets apart for every kind of colour blindness (the Beyond and Extreme
  colours were hard to tell apart with deuteranopia).

### Under the hood
- The build checks every language file against the English one (unknown keys, placeholders; shipped languages must
  be complete), so community translations can be added safely.
- New tests for the world rules (checked values, operators only) and the guide; the client test photographs the
  settings, the colour-blind colours and a moved HUD.

## 2.6.0: Extreme

*Some curses do not say what they do. You will find out.*

### Added
- **Extreme curses**, a fourth set of nine: Dwindling, Withered Grasp, Restless Gaze, The Grudge, Stormcalled,
  Feral Scent, Broken Oath, Unhealing and Echoing Steps. Their cards only hint at what they take; the rest is for you
  to discover. They act in every world.
  - Rare: now and then one card of an offer is an Extreme curse instead (`extreme_card_chance`, default 2.5%).
  - Never multiplied. Stacks that spill over from a multiplied card can land on them, though less often than on other
    curses (`extreme_spill_weight`, default 0.2).
  - Twice as hard to lift: atonement points and Tether Fragments both cost double (`extreme_atone_factor`). A Mending
    Cloth still lifts one stack.
  - Their own tab in the Curses screen, blood-red. The open tab now carries its name; the others show their sigil and
    stacks, with the name on hover. Players nearby show their Extreme stacks as well.
  - Every Extreme curse's maximum and strength are set in the config like any other curse's; the thunder of one of them
    with `storm_strike_min_seconds` and `storm_strike_max_seconds`.

### Changed
- **Atonement from kills is earned in the wild, not in a farm.** A kill only counts when the monster spawned naturally
  (in the dark, in a cave, outside, in a structure; not from a spawner, an egg or a command), when you dealt at least
  half of its health yourself (`atone_kill_min_share`; a farm's fall does not count), and while you have not already
  slain 5 monsters within 24 blocks of that spot in the last 10 minutes (`atone_kill_area_limit`,
  `atone_kill_area_radius`, `atone_kill_area_minutes`). Creatures a wound storm or a hunted night sends after you count
  as wild.

### Under the hood
- New automated tests for every Extreme curse, for rare single Extreme cards and their doubled cost, and for atonement
  from kills (spawner, too little damage, one spot saturated). The client test checks the client's stack limits and
  photographs the Extreme tab and an Extreme card.

## 2.5.0: Foundations

*Nothing new to find in the world this time. Everything under it got sturdier, faster and tested.*

### Changed
- **The Seam never stalls the server.** Islands are designed on their own thread and built within a few milliseconds
  per tick (`seam_build_budget_ms`, default 5), only in chunks someone has loaded. Unfinished islands far from everyone
  are put off and built again, the same way, when someone returns.
- **Sounds, particles, titles, effects and attribute curses** are sent directly instead of through server commands:
  lighter on the server, nothing in the server log, no clash with plugins that restrict commands.
- **World data is saved with the world** (autosave, `/save-all`, stopping the server, a player leaving) instead of on
  every change, so it never runs ahead of the inventories it belongs with.

### Fixed
- A crash while saving could leave a damaged `extremedimensions.json`, and the next start then reset all progress in
  that world. Saving is now crash-safe: the new file replaces the old one in a single step, the previous version stays
  as `extremedimensions.json.bak` and takes over if the main file is damaged, and an unreadable file is set aside,
  never overwritten.

### Under the hood
- Automated tests on every push: server tests on a headless dedicated server (curses, sealed slots, spill-over,
  atonement, recovery of damaged world data, every creature, every mixin, performance) and a client test in a real
  window with screenshots.
- Curse effects live in their own class (`CurseEffects`), apart from owing and lifting curses.
- Measured: curse upkeep for 20 players carrying every curse at its maximum costs about 0.1 ms per server tick.

## 2.4.1: A Heartbeat in the Dark

*The tribe has a pulse now, and death costs your penance.*

### Changed
- **The tribe's glow beats** like the Warden's heart: Lurkers, Hollowlings, Keepers, the Starved One and the Thinned
  flash and fade, every two seconds when calm, faster while they stalk, run or drink an anchor, fastest as they reach
  for you. Each body keeps its own rhythm.
- **Death undoes atonement**: every death takes half of the atonement gathered on every curse (rounded against you).
  The chat tells you how much on respawn. The share is set with `atone_death_loss` in the config.

### Fixed
- Sealed slots are crossed out in **every menu** that shows your inventory (villagers, chests, furnaces, crafting
  tables...), not only in the inventory itself.
- With only sealed slots free, items **stay on the ground**: no more pickup sound and animation for an item that then
  fell out again. Menus (clicks, shift-clicks, drags, number keys) and `/give` no longer put anything in a sealed slot.

## 2.4.0: The Hunger Spills Over

*Nothing the Starved take is wasted any more, and they follow you into every world.*

### Added
- **Everywhere curses**: a third set of 16 curses that act in every world, offered after any death that costs a curse
  (mixed in with the cards of that world's set): Kindling, Frostbitten, Sealed Pockets, Shrunken Stomach, Fading Brews,
  Lingering Ills, Grounded, Glass Jaw, Volatile, Short Breath, Fragile Footing, Fumbling, Brittle Gear, Hungry Wounds,
  Withering and Lead Pockets. Sealed slots are crossed out in the inventory, lost drumsticks on the hunger bar.
- **Spill-over**: stacks a multiplied card cannot add because its curse reaches the maximum are no longer lost. Each
  lands on a random curse that still has room, in any set, and the card says how many will spill.
- **Who stands near you**: the Curses screen lists every player within seven blocks, with their head (name on hover)
  and one sigil per set showing how many stacks they carry there. Never which curses. On small screens the list is a
  narrow column.

### Changed
- The Curses screen has three tabs (Beyond, Overworld, Everywhere), each with its sigil. A breathing mark shows which
  sets act where you stand.
- Everywhere curses can be atoned for anywhere: points earned beyond the wound and at home both count.
- The curse cards show Everywhere curses in violet ("Every world").

## 2.3.0: What the Starved Keep

*Curses no longer fade. They stay, they stack, and they have to be paid off.*

### Changed
- **Curses are permanent.** A Collapse or a death beyond the wound still offers three cards, but the curse you pick
  stays until you lift it. Every curse stacks up to its own maximum, and a card shows how many stacks you carry now and
  how many you will carry after picking it.
- **43 curses** instead of a handful: 26 beyond the wound (the Nether, the End and the Seam) and 17 at home, kept
  strictly apart. New ones include Cracked Armor, Brittle Bones, Burning Blood, Short Reach, Heavy Soul, Short Chain,
  Dim Anchors, Marked by the Tribe, Worn Edges, Ferryman's Fee, Vertigo and Cursed Coin beyond, and Restless Sleep,
  Bitter Tongue, Rusted Tools, Ill Repute, Hunted Nights, Watched and Dusk Blindness at home.
- Old timed curses carry over as permanent stacks (never past the new maximum).

### Added
- **Hungry offers**: now and then the whole offer is multiplied. Every card then carries its own seal (2x, 3x or 4x,
  rolled per card) and adds that many stacks at once. Some days all three roll high.
- **The Curses screen**: a button beside the journal button in your inventory opens it. Every curse is a rune socket
  with pips for its stacks; pick one for its effect, lore and atonement meter. Tabs for beyond and home. It replaces the
  marks along the sides of the inventory.
- **Lifting curses**, one stack at a time:
  - **Atonement**: choose a curse in the Curses screen and play it off: active minutes, elites and safe returns beyond;
    nights survived and monsters slain after dark at home.
  - **Tether Fragments**: offer them to an anchor, one per stack the curse has.
  - **Mending Cloth** (4 Seam Silk, a Tether Fragment and a Veil Wing): use it to take a stack off.
  - Every stack lifted leaves a **scar** (up to five) that weakens the drain a little.
- **Veil Wing**: Veil Rays now drop a wing when slain (sometimes two).
- `/tether curse <id> <stacks>` and `/tether curse clear` for operators.
- Config: a `penalties` section with the maximum and value per stack of every curse, plus the multiplier chance and
  range and every atonement number.

### Removed
- Curse timers, the Mourning curse and the side marks in the inventory.

## 2.2.1: Room to Breathe

*The islands grew, the streets found the bridges, and the rays learned to fly.*

### Changed
- **Veil Rays fly like they mean it**: they soar over the islands (twenty-five to forty-five blocks above you), glide from
  waypoint to waypoint in open sky and turn no faster than a glider can. They look ahead along their whole
  ten-block wingspan and swerve around spires, islands and hanging chains, preferring to climb over rather than dive
  under. They drift along with you as you explore, and climb away hard when struck.
- **Much bigger islands** for the buildings: villages, spires, looms and crystal halos stand on islands about half
  as wide again, with calmer rims so nothing hangs over the edge. Islands sit further apart on the grid, so bridges
  are longer and climb gently on slab ramps.
- **Villages are laid out like real ones**: a larger paved plaza around the bell spire, houses in the four quarters
  between the streets with a path from every door, and a walled graveyard with a gate, candles and a dead tree.
- **Streets lead to the bridges**: every island paves a street along each side that has a bridge, from its centre
  (or plaza) straight to where the bridge lands, with lamps along the way and a pair where it meets the bridge.
  Quieter islands get worn trails instead.
- Spires get a paved forecourt before the portal; looms a weaving floor and more height; open ground on every island
  is dressed with arches, dead trees, lamps and standing stones (never on a street or a building).
- **Bridges grew arches**: under every long run the deck rests on a row of shallow pointed arches springing from
  pendants that end in soul lanterns, and the kerbs now sit level with the walking surface on slab ramps.

### Added
- **Amethyst clusters**: shards of amethyst and violet glass breaking out of the ground, the tallest weeping light at
  its heart, with buds around their feet. Floating crystals now hover higher.

### Note
- The island grid changed. Start a new world or delete `dimensions/extremedimensions/the_seam` (with the server
  stopped) so new islands are not built through old ones.

## 2.2.0: The Living Seam

*Fog rolls between the islands, fireflies drift in it, and something sings out there.*

### Added
- **Rolling fog**: banks of mist drift through the Seam, so how far you can see wanders between about 50 and 100
  blocks. Thick banks close in; thin ones leave the near air clear.
- **Light in the air**: fireflies of violet, rose, gold and pale white drift everywhere, motes of light sift down and
  twinkles rise from the void. All full-bright, so shader packs make them glow.
- **Veil Rays**: great glowing mantas, wings six blocks wide, circle the islands and sing low. Harmless; struck, they
  climb away into the fog. They drop Seam Silk and glow ink.
- **The Thinned** walk their villages, pale and worn to threads. They only watch, until your thread runs thin (under a
  third of your time left): then they come for you, and they only move while nobody is looking at them.
- **Broken obelisks** around the Heart: over a hundred blocks tall, split into floating pieces that weep crying
  obsidian at every break, with runes of silk spiralling up their faces, halos turning about them and crystals over
  their points, on stepped plinths.
- More life on the islands: firefly bushes, eyeblossoms, pale moss patches, standing stones around soul fires, glow
  lichen and glow berries.

### Changed
- **Every building rebuilt for depth and gradients**:
  - Masonry runs from blackstone at the foot through deepslate to pale tuff at the crown, dithered and weathered.
  - Spires get pilasters, recessed stained-glass windows with arched heads and sills, corbels under the ledges, water
    tables, stepped buttresses with crocketed pinnacles, a stair roof with dormers, a crowned needle, and a portal of
    three receding arches.
  - Village houses are now jettied timber-framed houses with violet plaster over stone ground floors, steep slate
    roofs with eaves, doors, window sills and smoking chimneys.
  - Island undersides darken with depth and their fangs end in amethyst.
- **Bridges** are rebuilt: they start a couple of blocks inside each island's actual rim, run straight, turn on a
  landing halfway, climb with slab ramps or real stairs, and have kerbs with connected wall railings, lantern posts, a
  corbelled edge and a pier under the landing. No more diagonal staircases, floating chains or gaps at the ends.
- Island heights vary less, so more islands are joined by bridges.


## 2.1.0: The Seam, Rewoven

*The Seam is not a place. It is a breath, and now it has a sky to breathe.*

### Added
- **A sky of its own**: the Seam now has a hand-built, high-resolution sky (six 1024 px faces): violet nebulae, a
  galaxy band, a vortex turning at the zenith, a red glow where the Nether bleeds through, pillars of light rising from
  a sea of clouds and the silhouettes of far islands on the horizon.
- **Colossal architecture**, built over a few seconds as you approach so the server never stalls:
  - **Great Spires**: up to 120 blocks tall. Three or four tiers of deepslate stepping inward, tall pointed windows of
    violet glass, diamond rose windows, buttresses crowned with pinnacles, chains, lanterns and vines on every ledge, a
    glowing core of silk and a spiral stair inside up to a reliquary at the top. A long **nave** with banners and a
    great door in front, and three **satellite towers** joined to the first terrace by sky bridges.
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
- The Seam's light, fog and silk veins are violet instead of teal.
- Seam cells are now 72 blocks instead of 34, so structures have room. Parts of the Seam that were already explored
  in an older version stay as they were. For a clean Seam in an existing world, stop the server and delete the
  world's `dimensions/extremedimensions/the_seam` folder: islands, structures and the Heart rebuild themselves.


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
