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
   one of three penalties. Dying beyond the wound by other means ends the expedition the same way.

## Penalties: Tether Marks

Penalties stack and wear off only through **active play** where they count. Nether/End marks count down on
expeditions, Overworld marks in the Overworld. Idling pauses the clock: it takes both looking around and moving.
Open your inventory to see your **Tether Marks** beside it, each with its own rune, its stacks and the time left.

| Beyond the wound | Effect per stack |
| --- | --- |
| Accelerated Drain | Charge drains 75% faster |
| Debt | Anchor fuel gives 40% less charge |
| Scar | −3 hearts |
| Cracked Armor | −4 armor |
| Heavy Legs | 15% slower |
| Clumsy Hands | 35% slower mining |
| Frailty / Famine | Constant Weakness / Hunger |
| Shadowed | Darkness follows you beyond |
| Leaky Tank | Next expedition: your anchors lose 50% |
| Mourning | Weakness, Hunger and Mining Fatigue for 10 minutes |
| Toll | Your next 10 XP levels go to the Tether |

Optional **Overworld penalties** (`/tether overworld_penalties on`) add a separate set for deaths in the Overworld:
Hollow Heart, Leaden Steps, Gnawing, Dull Pick, Deep Dread, Ill Luck and Tithe.

**Tether Fragments** occasionally drop from hostile mobs beyond the wound (4%, elites 12%). Offer one to an anchor to
mend your longest-running mark.

## The Lurker

*It sees. It waits. It takes.* An ancient tribe, bound to the shadows between dimensions, keeps watch over you.
A Lurker appears rarely, day or night, in the Overworld and the Nether, always at a distance and on high ground.
Usually it only watches. Sometimes it creeps closer while you are not looking and takes something: an item,
experience, a piece of armour, or it lays a curse (a random penalty). Kill it before it vanishes to get your loot back.
Lurkers also drain charged anchors: catch one holding your anchor's light and chase it off.

## Multiplayer

Every player has their **own time**. What you put into an anchor is yours, even when you share anchors with friends.
If one player dies beyond the wound, only *their* time is lost; everyone else carries on.

## Lore and discovery

The mod tells you very little up front. You start with the **Tether Journal**, the account of a nameless Wanderer.
Sixteen pages surface as you play: your first anchor, the Ember Deep, the Pale Void, the Starved, the Weavers and more.
A tree of **15 advancements** charts your progress, and the title screen whispers things nobody remembers writing.

## Fuel

| Nether anchor | Charge | End anchor | Charge |
| --- | --- | --- | --- |
| Coal / Charcoal | 0:02 | Amethyst Shard | 0:03 |
| Magma Block | 0:06 | Chorus Fruit | 0:06 |
| Fire Charge | 0:12 | Ender Pearl | 0:20 |
| Blaze Powder | 0:15 | Eye of Ender | 0:45 |
| Coal Block | 0:18 | Shulker Shell | 1:30 |
| Blaze Rod | 0:30 | Echo Shard | 2:00 |
| **Ember Tether** | 10:00 | **Void Tether** | 10:00 |

Each player can store up to 30 minutes per anchor. Fuel values show in item tooltips.

## Controls

| Action | How |
| --- | --- |
| Charge an anchor | Right click it with fuel |
| Bind / untie the Tether | Right click with a Tether / with an empty hand |
| Anchor status | Sneak + right click with an empty hand |
| Mend a mark | Right click an anchor with a Tether Fragment |
| Read the journal | Use the Tether Journal |
| Summon a Lurker (operators) | `/tether lurker <watch|stalk|drain>` |

## Installation

1. Install **Fabric Loader 0.19.5+** for **Minecraft 26.3** (Java 25).
2. Add **Fabric API 0.162.0+** and `extremedimensions-1.1.0.jar` to your `mods` folder.
3. Install it on the server **and** every client.

Play a normal Survival world. Vanilla Hardcore turns you into a spectator on death, which this mod is designed to avoid.

## Configuration

All balance values live in `config/extremedimensions.json` (created on first launch): anchor size and range, drain,
tether length, penalty strength and duration, fragment chances and more.

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
| `penalty` | Penalty definitions, choosing, effects, wearing off, mending |
| `journal` | Journal pages |
| `lurker` | The Lurker: entity and behaviour |
| `progression` | Advancements |
| `network` | Payloads and state sync |
| `data` | Per-world persistence |
| `config` | The config file |
| `client` | HUD, screens, Tether Marks, tooltips, title screen, menu music and the shared UI style (`client.ui.Ui`) |

All text is translatable through `assets/extremedimensions/lang/en_us.json`.

---

### Nederlands, kort

Download de jar uit de nieuwste groene build: **Actions** → run → **Artifacts**. Zet hem samen met Fabric API in je
`mods`-map, op de server én bij iedere speler. Instellingen staan in `config/extremedimensions.json`.

## License

CC0-1.0
