# BattleHub combat model (machine learning)

The bots' strategy stays scripted (looting, beds, bridges, parkour); only the melee fight is learned: one model,
trained here outside Minecraft, used by the mod in every game where a bot fights. Phase 1 (spec v1, `pvp_sim`, in the
mod since 1.33): a 1v1 sword duel (and boxing) on flat ground. Phase 2 (spec v2, `pvp_sim2`): every melee mode, with
the terrain and the void, kits and effects, the bow, food, pearls, wind charges and the shield, and up to two enemies.

## Layout

| Path | What |
|---|---|
| `spec/combat_v1.json` | **The** definition of what the model sees (43 numbers) and does (7 action heads). Read by the simulation and by the mod. |
| `spec/combat_v1_golden.json` | States with the observation they must give, and actions with what they decode to. The mod's JUnit test must match it; `tools/make_golden.py --check` keeps it in step with the spec. |
| `pvp_sim/physics.py` | The fight, tick by tick, for many arenas at once (numpy): movement, sprinting, jumping, knockback, attack charge, damage, armor, critical hits, the 10 ticks after a hit, reach on the hitbox, health regeneration. |
| `pvp_sim/obs.py`, `actions.py` | Observations and actions as the spec defines them (the mod computes the same in `net.battlehub.core.ml`). |
| `pvp_sim/env.py` | `PvpBatch` (n arenas, both fighters act) and `PvpEnv` (Gymnasium, one learner against an opponent policy). |
| `pvp_sim/scripted.py` | The scripted bot (benchmark and early opponent) and random play. |
| `tests/` | The simulation against the game's numbers, the observation geometry, the environments, the golden file. |
| `tools/` | `make_golden.py`, `show_fight.py` (a fight drawn to a PNG). |
| `train/selfplay.py` | PPO (Stable-Baselines3) with self-play: an opponent pool of earlier versions, the scripted bot, random play and a dummy target, with a curriculum from easy targets to the pool. Checkpoints with an evaluation every million steps; `--resume` continues a run. |
| `train/league.py`, `ratings.py` | Everyone against everyone, Elo ratings, the checkpoints for the six difficulty levels picked by rating, exported to ONNX. |
| `train/export_onnx.py` | A checkpoint to ONNX (`obs` -> `logits`), checked against PyTorch with ONNX Runtime; the spec's sha256 in the metadata. |
| `INTEGRATIE.md` | How the model goes into the mod (in Dutch). |
| `spec/combat_v2.json` | Spec v2: 118 observations (the target, me, what I see of it, kit and effects, what I carry and hold, an incoming projectile, a second enemy, terrain rays) and 9 action heads (v1's plus what to hold and the right mouse button). |
| `spec/combat_v2_golden.json` | v2's golden file: states, actions and small heightmaps with their terrain rays (`tools/make_golden2.py`). |
| `pvp_sim2/terrain.py` | Heightmaps (columns of 1x1, NaN the void), the spec's terrain rays, line of sight, and the maps of the modes: walled arena, sumo platform, bridge, islands, town, hills. |
| `pvp_sim2/kits.py` | The mod's kits (weapons, armor, effects, items), the Bed Wars shop, SkyWars and Hunger Games loot, The Bridge: Chaos' effects. |
| `pvp_sim2/physics.py` | Up to three fighters per arena: moving against the terrain (step up, jump onto blocks, fall damage, the void), the hand and the right button (bow and arrows, eating, golden apples, pearls, wind charges, the shield), the mace's smash, hunger and regeneration, teams and kill credit. |
| `pvp_sim2/env.py`, `obs.py`, `scripted.py` | `Batch2` (n arenas, a scenario per episode: map, kits, 1v1 / 1v2 / three-way / 2v1), what each fighter perceives (view, line of sight, hearing, its target and second enemy), rewards; `PvpEnv2` for Gymnasium; a scripted bot that uses its items. |
| `tools/show_fight2.py` | v2 fights on their maps drawn to a PNG. |
| `train/selfplay2.py` | PPO self-play on v2 with the arenas in worker processes (3 workers x 96 arenas): a pool of earlier versions, the scripted bot, random play and dummies as opponents per slot; a curriculum that widens the scenarios (flat sword duels to everything), moves from easy opponents to the pool and fades the shaping later on. Each checkpoint is evaluated by `train/eval2.py` in a low-priority process. |
| `train/eval2.py` | A checkpoint against the scripted bot on eight fixed scenarios (sword, axe and bow duels, bridge, SkyWars loot, sumo, the town 1v2, Bed Wars three-way) and against a dummy on a bridge. |
| `train/league2.py` | v2's league over a mix of the modes' 1v1 scenarios, Elo, the six levels, ONNX export; and v2's best against v1's best on flat ground (v1 through an adapter). |

## Run

```
python -m venv .venv && . .venv/bin/activate
pip install -r requirements-sim.txt
python -m pytest tests
python tools/show_fight.py fight.png 0.8 0.5

# training (a GPU is not needed; about 5000 steps a second on 4 cores)
pip install -r requirements-train.txt
python train/selfplay.py --run runs/sword --steps 40000000      # --resume to continue
python train/league.py runs/sword --out league                  # ratings, levels, ONNX files

# v2 (every melee mode)
python train/selfplay2.py --run runs/v2 --steps 60000000        # about 6500 steps a second on 4 cores
python train/league2.py runs/v2 --out league_v2 --v1 runs/sword/ckpt_016000000.zip
```

Rewards: damage done minus damage taken (per max health), +-10 for winning or losing, at the time limit the health
difference and -1 for both (running away does not pay), a little for the crosshair on the opponent, a little less
for every decision and for not seeing the opponent, plus potential-based shaping for getting closer and turning
towards it (it shows the way without changing what the best play is). Without shaping the first runs learned to keep
away: hitting needs a charged swing on the hitbox, too rare to find by chance against opponents who punish coming
close.

## The spec in short

Observations, in the bot's own frame (forward, left, up), each `value / scale` clipped to `[-1, 1]`:
the opponent's position, distance and velocity; my velocity; the angle and pitch from my look to the opponent's chest;
whether the opponent looks at me; whether my crosshair is on it within reach; my ground, sprint, attack charge,
protection ticks and health; whether I see the opponent and, only then, its ground, sprint, protection, health and
how long ago it swung; ground before a drop and room before a wall in 8 directions.

Actions, chosen every 2 ticks: forward (-1/0/1), strafe (-1/0/1), jump, sprint, attack (one click), turn
(-60..60 degrees, positive is left) and look up/down (-15..15 degrees).

What the bot may know follows the mod's senses: the opponent is visible within 65 degrees of the look; otherwise only
its last known place counts. Per episode the simulation draws a perception delay (0-2 ticks), aim noise and a turn
limit, so the model copes with them when the mod's difficulty levels add them.

Minecraft's angles: yaw 0 looks along +z, turning left lowers the yaw; pitch positive looks down.
