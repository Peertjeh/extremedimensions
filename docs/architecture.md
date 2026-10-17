# How the Seam is built

This note describes the procedural architecture of the Seam: what exists, how it fits together, how materials and
gradients are planned, how to extend it, and what it cannot do yet. It was written for 2.25.0, when building-wide
material planning arrived. File and method names are in `src/main/java/com/extremedimensions/seam/`.

## A. Assessment: where it stood

The Seam was already generated as a hierarchy, though the hierarchy lived in code rather than in data:

| Level | Where it lives | What it decides |
| --- | --- | --- |
| Dimension | `SeamService` | the island grid (`CELL` = 100 blocks), arrival, the build queue, the seal |
| Region | `SeamArchitect.design...` | one island (or none) per cell, its kind, bridges between neighbours |
| Island | `SeamArchitect.island`, `Shape` | the rock: an upside-down mountain with summits, ribs, strata, turf |
| Building | `Colossus.build`, `ReturnGate.build`, `village`, `loom`, ... | the plan of one structure |
| Assembly | `Colossus.tower`, `hall`, `apse`, the citadel's curtain wall | a tower of tiers, a nave, a wall |
| Module | tier bodies, corners, crowns, `portal`, `bridge`, `parapet` | one recognisable architectural part |
| Block | `Frame.set` / `Plan.set` | a block of the world, in a rotated local frame |

Strengths at that point: a working kit of parts (five plans of colossal building, six kinds of tier body, two kinds
of corner, five crowns, four schemes of material), rotation through `Frame`, every structure planned before a single
block is placed (so plans can be tested on a server with no world), and a build queue that flies blocks in.

The weaknesses that mattered most, measured against a professional procedural pipeline:

1. **Gradients restarted at every part.** A tower shaded its masonry by `height / building height`, a hall by
   `height / 48`, the citadel's curtain wall by `height / (2.5 × wall height)`. Where a hall met a tower they showed
   different stone at the same height: a visible seam at every junction.
2. **Per-block randomness.** Each block's rung on its ladder was moved by `±0.575` of a rung drawn from the layout's
   random stream, and wear (moss, cracks) was a second coin per block. The result read as speckle, not as a gradient.
3. **One random stream for everything.** Choosing stone consumed the same stream as choosing towers, so any change to
   how stone is chosen moved towers too.
4. **No briefs.** A building's plan and scheme could only be drawn at random; there was no way to ask for, say, a
   ruined citadel.
5. **Thin validation of materials.** Tests covered fit, height, gates and bridges, not determinism or gradients.

## B. Architecture: what changed

Materials now have their own layer between a building's scheme and its blocks:

- **`Colossus.Scheme`** holds a building's *roles*: the wall ladder (dark to pale), the trim ladder, roof, roof fill,
  corbels, caps, rails, floors, glass, accent, band and pier. Which role a block plays is decided by the module that
  lays it; the scheme only says what that role is made of.
- **`MaterialContext`** is the material plan of one building, made once in `Colossus.build` from where the building
  stands. It decides only *where on a ladder* a block falls, from the block's world coordinates. Every module of the
  building asks the same context, so the same world block gets the same answer whichever module asks.

Modules call `sc.masonryAt(frame, x, y, z, bias)` and `sc.trimAt(frame, x, y, z, bias)` with their local
coordinates; the scheme converts them through the frame (`p_world = R · p_local + o`) and asks the context.

Materials draw on a stream of their own (the context's seed comes from the building's position, not from the layout
stream), so changing how stone is chosen changes no geometry.

## C. Implementation (2.25.0)

- `MaterialContext` (new): height easing, smooth streak and patch fields, positional dither, wear in patches.
- `Colossus`: every masonry and trim site that followed a gradient now goes through the context (29 sites); the three
  separate gradient functions are gone. Fixed rungs remain where the role demands them (a plinth course is always the
  darkest trim, ribs and finials the palest).
- `Colossus.build(..., plan, scheme)` and `SeamService.colossus(..., layout, scheme, ...)`: a building can be told its
  scheme (`TWILIGHT`, `BONE`, `GLOAM`, `RUIN`). The scheme's draw from the stream is kept either way, so a building
  told its scheme keeps the shape its seed gives (except a ruin, whose crowns break and battlements rag).
- `Plan.fingerprint()`: an order-independent hash of every block a plan lays, for determinism tests.

## D. Module library

| Kind | Modules |
| --- | --- |
| Tier bodies | `lancet` (tall pointed windows), `arcade` (open arches round an inner core), `colonnade` (piers round a core), `banded` (pale and dark courses with blind arches), `belfry` (open bell stage), `glasshouse` (lantern of glass) |
| Corners | `buttresses` (stepped, with bands), `turrets` (corbelled, with slits) |
| Crowns | spire (`cone` + `needle`), dome, crown of pinnacles, terrace, broken (ruins) |
| Building parts | `parapet` (with or without merlons), `floor`, `core` (spiral stair in a glowing well), `hall` (nave, aisles, flying buttresses, clerestory), `apse`, `portal`, `bridge` (sky bridge between towers), `forecourt`, curtain wall, gatehouse |
| Plans | cathedral, citadel, cluster, ziggurat, gateway |
| Other structures | the Return Gate (`ReturnGate`), villages, looms, spires, crystals, lamps, arches, obelisks, graveyards, bridges with gateways and hung decks (`SeamArchitect`) |

## E. Materials and gradients

For a building with foot `f` and crown `c` (world heights) and a block at world `(x, y, z)`:

```
t       = clamp((y − f) / (c − f), 0, 1)
height  = 1 − (1 − t)²                                  eased out: the dark foot is a plinth, the crown long
streak  = 2·noise(x/2.3 + z/2.9, y/12) − 1              narrow across a face, about a dozen blocks long down it
patch   = 2·noise((x + z)/9, y/7) − 1                   broad patches across a face
place   = clamp(height + bias + 0.11·streak + 0.08·patch, 0, 1)
rung    = round(place · (n − 1) + 0.5·(hash(x, y, z) − 0.5))   a quarter rung of dither, from the position
worn    = noise(x/4 + z/5, y/4) < wear · (1.6 − height)  and  hash₂(x, y, z) < 0.75
```

Noise moves the *place on the ladder*, never the block directly, and the only per-block term is a quarter-rung
dither drawn from the position, so band edges are soft but neighbouring stones never jump. The ease-out exists
because most of a building's stone is low down (halls, walls); laid evenly by height, nearly all of it would be the
dark of the foot.

Only the vertical gradient is used so far. Horizontal, radial and structural gradients fit the same shape (another
term added to `place`) but are not implemented.

## F. Validation and tests

Gametests that run on every push (`src/gametest/java/.../gametest/`):

- `ArchitectureTests.aSeedAlwaysBuildsTheSameBuilding`: every plan built twice from one seed has the same
  fingerprint; the next seed builds another.
- `ArchitectureTests.aBuildingToldItsSchemeKeepsItsShape`: a building told its scheme is built in it, to the same
  shape its seed gives in any other (ruins excepted).
- `ArchitectureTests.aBlockIsTheSameStoneWhicheverPartLaysIt`: two contexts made alike agree on every block, worn
  or not.
- `ArchitectureTests.theGradientClimbsFromFootToCrown`: the mean rung climbs with height, the foot is dark, the crown
  pale, and a course asked to be paler is.
- `ArchitectureTests.neighbouringStonesShadeSmoothly`: the place on the ladder moves by less than 0.2 between side
  neighbours and 0.12 between stacked ones; fewer than 1 in 200 neighbouring pairs differ by more than one rung.
- `MasonryTests.colossiFitTheirIslandsAndDiffer`, `GateTests`: fit within the island, below the build limit,
  variety, the gate's geometry in every direction, walkable bridges.

The client test photographs every plan from a corner and from the front, two of them up close and one at night, plus
a ruined citadel (`18_colossus_*`).

## G. Example buildings

There are no brief files yet; these are the plan and scheme to ask for:

| Example | Call |
| --- | --- |
| Castle | `SeamService.colossus(seed, x, top, z, r, Colossus.CITADEL, Colossus.GLOAM, null)` |
| Cathedral | `SeamService.colossus(seed, x, top, z, r, Colossus.CATHEDRAL, Colossus.BONE, null)` |
| Magical tower | `SeamService.colossus(seed, x, top, z, r, Colossus.CLUSTER, Colossus.TWILIGHT, null)` (a great spire with lantern tiers and sky bridges) |
| Ruined complex | `SeamService.colossus(seed, x, top, z, r, Colossus.CITADEL, Colossus.RUIN, null)` |

`plan.placeNow(level)` places the result at once; `plan.queue(...)` flies it in.

## H. Extending it

- **A new module**: write a static method in `Colossus` that takes the `Tower` (or a `Frame` and `Scheme`) and lays
  blocks through `f.set` / `f.soft`. Use `sc.masonryAt` / `sc.trimAt` for anything on a ladder, and the scheme's roles
  (`sc.floor`, `sc.band`, `sc.pier`, ...) for the rest. Never pick a ladder rung from a height of your own.
- **A new tier body**: add a constant next to `LANCET`..`LANTERN`, a case in `tier`, and a weight where `tower` rolls
  bodies.
- **A new scheme**: add a case to `Colossus.scheme` filling every role, a constant next to `RUIN`, and its id in the
  summary.
- **Materials elsewhere**: any structure can make its own `MaterialContext.of(seed, foot, crown, wear)` and ask it
  for `masonry` / `pick` with world coordinates.

## I. Known limitations

- **The rift's view is painted, not live.** The far end of a rift's tunnel shows a painted, blurred view of where it
  leads (the Overworld by day or night, the Nether, the Seam), not the actual world. Rendering another dimension
  live needs a second world renderer, which a mod cannot add safely.
- Only colossal buildings use the material context. The Return Gate, islands, villages, bridges and paving still
  shade by part with per-block dither.
- No data-driven briefs, no constraint solver: plans are code, and their proportions are fixed rules with random
  ranges.
- A building's stone pattern belongs to where it stands; the same plan built elsewhere shows a different (equally
  valid) pattern.
- Only vertical gradients.

## J. Roadmap

1. Move the Return Gate, villages and bridges onto `MaterialContext`, with one context per settlement.
2. Horizontal and radial gradient terms (a darker side facing the void, paler towards a building's heart).
3. Briefs as data (JSON in `data/extremedimensions/briefs/`): plan, scheme, height band, required modules.
4. Structural checks in the tests: no floating masonry, every floor reachable from the door.
5. Weathering by exposure (moss on faces that look down, cracks near edges) instead of noise alone.
