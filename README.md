![Extreme Dimensions](docs/banner.jpg)

# Extreme Dimensions

*Every portal is a wound. Every wound remembers.*

A survival-horror overhaul of how you travel beyond the Overworld, for **Minecraft 26.3** on **Fabric**.
The Nether and the End become timed expeditions: you fuel anchors, bind yourself with a physical tether and
race the clock. Let your anchors run dry and the Starved take something of you.

---

## The loop

1. **Charge an anchor.** Place a lodestone within 5 blocks of a portal and feed it fuel. Its first fuel binds it to
   that dimension: it becomes an **Ember Anchor** (Nether) or a **Void Anchor** (End).
2. **Bind your Tether.** Craft a *Tether* (iron chain + 2 string) and use it on your charged anchor. From then on a
   real chain lies on the ground wherever you walk. In the Overworld it snaps beyond 5 blocks, so walk straight
   into the wound.
3. **Step through.** Your timer is the charge *you* stored in the anchors around that portal. It drains every
   second, and faster the further you wander from your arrival portal (+100% per 100 blocks).
4. **Come home.** Step back through the portal before it runs out. The chain vanishes and your Tether returns.
5. **Or don't.** At zero charge you suffer a **Dimensional Collapse**: you die, respawn in the Overworld and choose
   one of three curses. Dying beyond the wound by other means ends the expedition the same way.

## Curses

A Collapse or a death beyond the wound costs you a **curse**: pick one of three cards. Curses are **permanent** and
stack up to their own maximum. Sometimes the Starved are hungrier: the whole offer is multiplied and every card carries
its own seal (**2x, 3x or 4x**), adding that many stacks at once. Stacks a card cannot add because its curse hits the
maximum **spill over**: each lands on a random curse that still has room, in any set.

Three sets. Beyond curses act only in the Nether, the End and the Seam; Overworld curses (`/tether overworld_penalties
on`) only at home; **Everywhere** curses in every world. A death offers cards from its world's set and the Everywhere
set.

The **Curses** screen (the button beside the journal button in your inventory) shows every curse as a rune with its
stacks, effect, lore and atonement, in three tabs; a breathing mark shows which sets act where you stand. Players
within seven blocks are listed beside it: their head (name on hover) and one sigil per set with how many stacks they
carry there, never which curses.

| Beyond the wound | Per stack | Max |
| --- | --- | --- |
| Accelerated Drain | Charge drains 25% faster | 4 |
| Debt | Anchor fuel gives 15% less charge | 4 |
| Scar | Max health −2♥ | 3 |
| Cracked Armor | −2 armor | 4 |
| Heavy Legs | 8% slower | 3 |
| Clumsy Hands | 15% slower mining | 3 |
| Frailty | −1 attack damage | 4 |
| Famine | Constant hunger | 2 |
| Shadowed | Darkness follows you; slower from 2 stacks | 2 |
| Leaky Tank | Every expedition, your anchors lose 10% | 3 |
| Toll | 15% less experience | 3 |
| Brittle Bones | 15% more fall damage | 3 |
| Burning Blood | You burn 40% longer | 3 |
| Thin Skin | −1 armor toughness | 3 |
| Slow Hands | 10% slower attacks | 3 |
| Short Reach | −0.5 block reach | 2 |
| Weak Knees | 10% lower jumps | 2 |
| Heavy Soul | 10% more gravity | 2 |
| Short Chain | Distance drain starts 15 blocks sooner | 3 |
| Dim Anchors | Each anchor holds 3 minutes less of your time | 4 |
| Marked by the Tribe | Lurkers find you 50% more often in the Nether | 3 |
| Empty Pockets | 1% fewer Tether Fragments (elites 3%) | 3 |
| Worn Edges | Tools lose 1 extra durability per use | 3 |
| Ferryman's Fee | Every safe return costs 2 levels | 3 |
| Vertigo | Spells of nausea, more often per stack | 2 |
| Cursed Coin | −1 luck: worse loot in fortresses, bastions and end cities | 3 |

| Overworld | Per stack | Max |
| --- | --- | --- |
| Hollow Heart | Max health −1♥ | 4 |
| Leaden Steps | 5% slower | 3 |
| Gnawing | Constant hunger | 2 |
| Dull Pick | 10% slower mining | 3 |
| Deep Dread | Darkness below sea level | 1 |
| Ill Luck | −1 luck | 3 |
| Tithe | 15% less experience | 3 |
| Restless Sleep | 25% chance a nightmare wakes you | 3 |
| Bitter Tongue | Food fills 15% less | 3 |
| Rusted Tools | Tools lose 1 extra durability per use | 3 |
| Ill Repute | Villagers ask 10% more | 3 |
| Hunted Nights | Something rises near you at night | 3 |
| Weak Arm | −1 attack damage | 3 |
| Short Tether | Your Tether snaps 1 block sooner | 2 |
| Watched | Lurkers find you 50% more often | 3 |
| Old Bones | 15% more fall damage | 3 |
| Dusk Blindness | Darkness under the night sky | 1 |

| Everywhere | Per stack | Max |
| --- | --- | --- |
| Kindling | 20% more fire and lava damage | 3 |
| Frostbitten | 25% more freeze damage, and you freeze 25% faster | 3 |
| Sealed Pockets | 3 inventory slots sealed (the hotbar never) | 3 |
| Shrunken Stomach | Hunger bar holds 1 drumstick less | 3 |
| Fading Brews | Good effects end 15% sooner | 3 |
| Lingering Ills | Bad effects last 20% longer | 3 |
| Grounded | 8% lower jumps | 2 |
| Glass Jaw | 8% more damage from mobs | 3 |
| Volatile | 15% more explosion damage | 3 |
| Short Breath | Air runs out 20% faster | 3 |
| Fragile Footing | Fall damage starts 1 block sooner | 2 |
| Fumbling | 5% chance to drop your held item when hit | 3 |
| Brittle Gear | Armor loses 1 extra durability per hit | 3 |
| Hungry Wounds | Every hit you take drains extra hunger | 3 |
| Withering | Max health −1♥ | 2 |
| Lead Pockets | 5% slower while your inventory is over half full | 3 |

Every maximum and every value per stack can be changed in the config's `penalties` section. When a death has nothing
left to give, the Starved take all your levels instead. A Shrunken Stomach never stops healing outright: when full it
still heals slowly, at the vanilla pace and cost.

### Lifting curses

- **Atonement:** pick the curse to atone for in the Curses screen and play it off. Beyond: 1 point per active minute
  on an expedition, 3 per elite slain, 5 per safe return. At home: 10 per night survived without dying, 1 per monster
  slain after dark. Everywhere curses take points from both. The last stack takes 100 points, every stack above it 50
  more.
- **Tether Fragments:** offer them to an anchor, one fragment per stack the curse has.
- **Mending Cloth:** 4 Seam Silk, a Tether Fragment and a **Veil Wing** (dropped by Veil Rays). Use it to take one stack
  off.

Every stack you lift leaves a **scar** (up to five): each one makes the drain 3% weaker.

**Tether Fragments** occasionally drop from hostile mobs beyond the wound (4%, elites 12%).

## The Lurker

*It sees. It waits. It takes.* An ancient tribe, bound to the shadows between dimensions, keeps watch over you.
A Lurker appears rarely, day or night, in the Overworld and the Nether, always at a distance and on high ground.
Usually it only watches. Sometimes it creeps closer while you are not looking and takes something: an item,
experience, a piece of armour, or it lays a curse. Kill it before it vanishes to get your loot back.
Lurkers also drain charged anchors: catch one holding your anchor's light and chase it off.

**Soul wards:** the tribe will not drink where souls burn. A soul lantern, soul torch, soul campfire or soul fire
within 4 blocks of an anchor keeps it safe. Listen to your animals, too: wolves growl and cats hiss when something is
watching.

## What Was Taken

A Lurker that escapes with your loot runs it home to its **hoard**: follow it, or follow the column of ink in the
air. Hoards are guarded by **the Keeper**, a bone-masked giant that comes for anyone who opens one, and they are full
of **Hollowlings**, the tribe's young: little thieves that freeze when you look at them. Offer one a Tether Fragment
in soul light and it may become your friend.

## The Seam

Use a **Seam Needle** on crying obsidian and the air tears open into **the Seam**: floating islands in rolling fog under
a sky of nebulae and a turning vortex, colossal gothic spires, giant Weaver looms strung with silk, towers hanging upside
down into the void, arched stone bridges meeting paved streets, amethyst clusters, broken obelisks, fireflies of light, Veil Rays soaring
overhead and the villages of the Thinned, who only move while you are not looking. You have five minutes.
Your return rift drifts, and only the thread of silk you leave behind leads back to it.

## Weaver's Craft, the Dragon and the Mending

Weave Seam Silk into your Tether for a slower drain, more reach and (with the Weavers' own weave) one escape from a
Collapse. Lifted curses leave **scars** that make you harder to drain. Slay the Ender Dragon and you break the
seal on the first wound: Wound Storms tear the Overworld sky, the tribe turns from thieves into frightened
supplicants, and every rift now opens near the **Heart of the Seam**, where the **Starved One** waits. Defeat it,
and choose: **mend** the world or **sever** the Tether.

## In the world

Torn **Wanderer's Pages** turn up in ruined portals, nether fortresses, bastions, stronghold libraries, ancient cities
and end cities. Use one to add it to your journal; two pages can only be found this way. Ruined portals, bastions and
other structures can also hold **Tether Fragments**. Charged anchors hum, the chain clinks as you walk, and the mod's
own particles (soul, ember and void motes, Lurker wisps) tie it all together.

## Multiplayer

Every player has their **own time**. What you put into an anchor is yours, even when you share anchors with friends.
If one player dies beyond the wound, only *their* time is lost; everyone else carries on.

## Lore and discovery

The mod tells you very little up front. You start with the **Tether Journal**, the account of a nameless Wanderer.
Thirty-one pages surface as you play: your first anchor, the Ember Deep, the Pale Void, the Starved, the Weavers and more.
A tree of **15 advancements** charts your progress, and the title screen whispers things nobody remembers writing.

## Fuel

| Nether anchor | Charge | End anchor | Charge |
| --- | --- | --- | --- |
| Coal / Charcoal | 0:02 | Amethyst Shard | 0:03 |
| Magma Block | 0:06 | Chorus Fruit | 0:06 |
| Fire Charge | 0:12 | Ender Pearl | 0:20 |
| Blaze Powder | 0:15 | Eye of Ender | 0:45 |
| Coal Block | 0:18 | Shulker Shell | 1:30 |
| Breeze Rod | 0:30 | Echo Shard | 2:00 |
| **Ember Tether** | 10:00 | **Void Tether** | 10:00 |

Each player can store up to 30 minutes per anchor. Fuel values show in item tooltips.

## Controls

| Action | How |
| --- | --- |
| Charge an anchor | Right click it with fuel |
| Bind / untie the Tether | Right click with a Tether / with an empty hand |
| Anchor status | Sneak + right click with an empty hand |
| Mend a curse | Right click an anchor with Tether Fragments, or use a Mending Cloth |
| See your curses | The Curses button beside the journal button in your inventory |
| Read the journal | Use the Tether Journal, or the journal button beside the recipe book |
| Summon a Lurker (operators) | `/tether lurker <watch|stalk|drain>` |
| Build a hoard (operators) | `/tether hoard` |
| Enter the Seam (operators) | `/tether seam` |
| Build every Seam structure in a row (operators) | `/tether seam_showcase` |
| Break the seal / start a storm (operators) | `/tether break_seal`, `/tether storm` |
| Set or lift curses (operators) | `/tether curse <id> <stacks>`, `/tether curse clear` |
| Open a rift | Use a Seam Needle on crying obsidian |

## Installation

1. Install **Fabric Loader 0.19.5+** for **Minecraft 26.3** (Java 25).
2. Add **Fabric API 0.162.0+** and `extremedimensions-2.4.0.jar` to your `mods` folder.
3. Install it on the server **and** every client.

Play a normal Survival world. Vanilla Hardcore turns you into a spectator on death, which this mod is designed to avoid.

## Configuration

All balance values live in `config/extremedimensions.json` (created on first launch): anchor size and range, drain,
tether length, every curse's maximum and strength, the multiplier chance, atonement, fragment chances and more.

## Building

```
./gradlew build
```

The jar ends up in `build/libs/`. CI builds every push; download the jar from the run's **Artifacts**.

## Project layout

| Package | Responsibility |
| --- | --- |
| `anchor` | Anchors, fuel, the free path to the portal, leaking and particles |
| `expedition` | Entering, draining, the Leeching, Collapse, deaths and respawns |
| `tether` | The physical chain on the ground |
| `penalty` | Curse definitions, offers and multipliers, effects, atonement, mending |
| `journal` | Journal pages |
| `lurker` | The Lurker: entity and behaviour |
| `progression` | Advancements |
| `network` | Payloads and state sync |
| `data` | Per-world persistence |
| `config` | The config file |
| `client` | HUD, screens (Curses, cards, journal), tooltips, title screen, menu music and the shared UI style (`client.ui.Ui`) |

All text is translatable through `assets/extremedimensions/lang/en_us.json`.

---

### Nederlands, kort

Download de jar uit de nieuwste groene build: **Actions** → run → **Artifacts**. Zet hem samen met Fabric API in je
`mods`-map, op de server én bij iedere speler. Instellingen staan in `config/extremedimensions.json`.

## License

CC0-1.0
