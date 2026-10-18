# How the Seam is built

This note describes how the Seam is generated: its islands, what stands on them, how materials and gradients are
planned, how age and damage are applied, how to extend it, and what it cannot do yet. It covers 2.26.0, when the
whole of the Seam's world generation moved onto one pipeline (ground planned first, structures designed apart and set
down on it, then aged). File and method names are in `src/main/java/com/extremedimensions/seam/`.

## A. Assessment: where it stood

Before 2.26 the Seam was generated as a hierarchy that lived in code, and building-wide material planning existed for
the colossal buildings only (2.25). What players saw, and what the code explained:

| What was seen | Why |
| --- | --- |
| Islands like pucks: a flat top, a cut edge, a ring of grey stone round the rim, columns of rock beneath | Every island's top was one height, its coast three sine waves, its rim a fixed band of stone, and every column of its underside a random block longer or shorter than the next |
| Buildings whose foot did not meet the ground | Buildings were laid out against the island's radius, not its coast: on a bay they hung over the nothing. Nothing tied a wall's foot to the ground |
| Great buildings that were not one building | Each part used its own heights: a hall met a tower at any height, an octagonal or round crossing tower left gaps where the nave met it, west towers stood a block off the front, the towers of a cluster stood apart on the turf |
| Too little variety | One plan per layout, symmetric towers, one look per scheme |
| Nothing damaged but a whole "ruin" scheme | Damage existed only as broken crowns of that one scheme |
| Timber bridges and a gold column and line of gold at the rift | Hung bridges of dark oak; a beam effect and a line of froglights |

## B. Architecture: the pipeline

```
SeamService.plan(cell)
  Shape            the island's footprint and coast (predictable by neighbours, for bridges)
  Terrain.of       the ground, planned as a surface: relief, shoulder, coast, underside; corridors to bridges
  Terrain.emit     the island laid; each column's surface recorded on the Plan (ground map)
  build(kind)      every structure designed on a Plan of its own (Colossus, loom, village, halo)
    Ruin.apply     its age and damage, on its own blocks only
    Terrain.settle the ground fitted to it: levelled, blended, a ledge grown under any overhang
    Plan.merge     the structure set down; the paving it asked for laid on the real ground
  streets, grounds the island dressed, following the ground
  bridges          stone only
```

- **Terrain** holds the island as arrays (land, surface, underside, built) before a block is laid, so structures can be
  fitted to it and it to them.
- **Plan** gained a ground map (`ground(x, z, fallback)`), deferred paving (asked for on a structure's own plan, laid
  on merge) and `merge`.
- **Ruin** knows nothing of buildings beyond their blocks: it works on any structure's plan.
- **Colossus** keeps the material plan of 2.25 (`MaterialContext`, one per building, world coordinates) and now
  closes its massing (datum lines, square crossings, engaged towers, a podium) and lays a plinth round every wall.

Random streams stay separate: layout (the cell's stream), materials and look (from the building's position), age (from
the building's position), terrain (one number drawn, then its own stream).

## C. Implementation (2.26.0)

- `Terrain` (new): relief (mounds up to four blocks on the largest islands, a crown), a two-block shoulder at the coast,
  level corridors to bridges, a smooth underside (summit, lobes, ribs, a slow wander; no random per column),
  conical fangs, growth, falls; `settle` with a chamfer distance field (margin 2, blend 5), ledges under overhangs,
  masonry faces where the levelled ground drops.
- `Shape.edge`: five harmonics instead of three (bays, headlands, coves); `nearest()` for fitting.
- `Ruin` (new): conditions, bites, fallen crowns, loose blocks dropped, broken edges worn, glass broken, vines,
  rubble.
- `Colossus`: `tower(..., firstTier)`; square crossing tower rising past the ridges; engaged west towers on the eaves
  line (pair, two heights, or one); cluster on a podium with wings and doors; hall gables closed at both ends; broad
  tiers no longer glass lanterns; `socle` (the plinth); `look` (glazing and roof per building); Gloam without cobbles.
- `SeamService`: the pipeline above for cells, the showcase, `colossus` and `showpiece`; the arrival gate set down on
  the arrival island and its rock together.
- Bridges: stone only. Rift: a frame of blocks and pixel-art textures (client); no beam, no line of gold.

## D. Module library

| Kind | Modules |
| --- | --- |
| Ground | island surface, shoulder, corridors, underside, fangs, growth, ledges, retaining masonry |
| Tier bodies | `lancet`, `arcade`, `colonnade`, `banded`, `belfry`, `glasshouse` (slender tiers only) |
| Corners | `buttresses`, `turrets` |
| Crowns | spire, dome, crown of pinnacles, terrace, broken |
| Building parts | `parapet`, `floor`, `core`, `hall` (both gables closed), `apse`, `portal`, `bridge`, `forecourt`, curtain wall, gatehouse, `podium`, `socle` |
| Plans | cathedral, citadel, cluster, ziggurat, gateway |
| Other structures | the Return Gate, villages, looms, halos, hanging towers, arches, obelisks, graveyards, stone bridges with gateways |
| Age | `Ruin.apply`: bites, fallen crowns, worn edges, broken glass, vines, rubble |

## E. Materials and gradients

Unchanged in principle from 2.25: for a building with foot `f` and crown `c` and a block at `(x, y, z)`,

```
t      = clamp((y − f) / (c − f), 0, 1)
height = 1 − (1 − t)²
place  = clamp(height + bias + 0.11·streak + 0.08·patch, 0, 1)
rung   = round(place · (n − 1) + 0.5·(hash − 0.5))
```

New in 2.26: the plinth uses the scheme's darkest stairs (a structural gradient: foundation to wall); retaining faces
under levelled ground are polished blackstone over blackstone bricks and hush bricks; the island's rock keeps its
streaks and strata; a building's glazing and roofing are drawn per building (`look`) within its scheme.

## F. Validation and tests

Gametests run on every push (`src/gametest/java/.../gametest/`):

- `GroundTests.theGroundRollsButNeverJumps`: a great island's top rises and falls three blocks or more, fewer than one
  in fifty neighbouring columns step two blocks or more, its underside rarely jumps, its coast is no circle.
- `GroundTests.whatIsBuiltStandsOnGround`: villages, looms and colossal buildings from several seeds: no solid block at
  floor level stands over the nothing; a colossal building has its plinth.
- `GroundTests.anOverhangStandsOnALedge`: a wall reaching past a small island's coast stands on a grown ledge faced in
  masonry.
- `GroundTests.aRuinLeavesNothingHanging`: every plan as a ruin loses much, leaves nothing that stood hanging, keeps
  its foot, and falls the same way from the same seed.
- `GroundTests.damageFollowsTheSetting`: none damaged at 0, all at 100, the ruined scheme always a ruin.
- `GateTests.bridgesAreOfStoneAndPassUnderGateways`: no timber in any bridge; gateways; the walk clear.
- `ArchitectureTests`, `MasonryTests`: determinism, schemes, gradients, fit within the island, variety.

The client test photographs every plan of colossal building (`18_colossus_*`), a village, a loom, a plain island
(and its underside) and a halo island (`20_island_*`), the gate, and both kinds of rift (`25_*`).

## G. Example buildings

| Example | Call |
| --- | --- |
| Castle | `SeamService.colossus(seed, x, top, z, r, Colossus.CITADEL, Colossus.GLOAM, null)` |
| Cathedral | `SeamService.colossus(seed, x, top, z, r, Colossus.CATHEDRAL, Colossus.BONE, null)` |
| Magical tower | `SeamService.colossus(seed, x, top, z, r, Colossus.CLUSTER, Colossus.TWILIGHT, null)` |
| Ruined complex | `SeamService.colossus(seed, x, top, z, r, Colossus.CITADEL, Colossus.RUIN, null)` |
| A village, a loom, ... | `SeamService.showpiece(seed, x, top, z, kind)`, kinds in `SeamService.SHOWPIECES` |

`plan.placeNow(level)` places the result at once; `plan.queue(...)` flies it in.

## H. Extending it

- **A new structure**: design it on a `Plan` of its own, from `top + 1` up (pave with `pave`, which waits for the
  merge), then `ground.settle(plan, structure, top)` and `plan.merge(structure)` (`SeamService.standOn` does both). It
  stands on levelled ground, overhang or not.
- **Age**: `Ruin.apply(structure, rnd, condition, top)` before settling; draw the condition with `Ruin.condition`.
- **A new module, tier body or scheme**: as before (see `Colossus`); use `sc.masonryAt` / `sc.trimAt` for anything on
  a ladder; `socle` gives it a plinth.
- **Ground of a new kind**: give `Terrain` another term in `plan` (it is one surface and one underside per column).

## I. Known limitations

- **The rift's view is painted, not live** (a second world renderer is not something a mod can add safely).
- Terrain is one surface per column: no caves, arches of rock or overhangs within an island.
- Bridges and streets meet islands at their height; an arrival bridge lands on its far island on a corridor of its
  own (2.27) only when that island was designed after the arrival (one built before keeps the step of a block).
- Damage is geometric (it knows blocks and height, not which part is a pier or an ornament).
- Only the colossal buildings use the building-wide material plan.

## J. Roadmap

1. Damage by role: give modules a structural tag so piers resist and ornaments fall first.
2. Terrain features: rock arches, terraces cut into the larger islands, paths that follow contours.
3. Villages laid out on the ground's contours, houses stepping with it.
4. Material plan for villages, the gate and bridges (one `MaterialContext` per settlement).
5. Briefs as data (plan, scheme, condition, height band) in `data/extremedimensions/briefs/`.
