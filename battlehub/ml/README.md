# BattleHub combat model (machine learning)

The bots' strategy stays scripted (looting, beds, bridges, parkour); only the melee fight is learned: one model,
trained here outside Minecraft, used by the mod in every game where a bot fights. Phase 1: a 1v1 sword duel (and
boxing) on flat ground.

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

## Run

```
python -m venv .venv && . .venv/bin/activate
pip install -r requirements-sim.txt
python -m pytest tests
python tools/show_fight.py fight.png 0.8 0.5
```

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
