# BattleHub combat model (machine learning)

The bots' strategy stays scripted (looting, beds, bridges, parkour); only the melee fight is learned: one model,
trained here outside Minecraft, used by the mod in every game where a bot fights. Phase 1 (spec v1, `pvp_sim`, in the
mod from 1.33 to 1.38): a 1v1 sword duel (and boxing) on flat ground. Phase 2 (spec v2, `pvp_sim2`): every melee mode,
with the terrain and the void, kits and effects, the bow, food, pearls, wind charges and the shield, and up to two
enemies. Phase 3: action masks, learning items (1.38), and one model for every mode (1.39: v2 now beats v1 in its own
sword duel, so v1 left the mod; its spec, golden file and simulation stay here). Spec v3 (1.40): v2's observations in
their place plus a team mate, a third enemy, memory and the last decisions (frame stacking); up to four fighters with
team modes (2v2, the learner with team mates, four against each other) and a shared team reward. v2's models became v3
models that play the same (`train/upgrade_v3.py`), so v2 left the mod as well. Phase 3D (1.41): the simulation
fitted on the mod's fight logs (`train/simfit.py` measures it): people's velocities as the game keeps them, the game's
own Chaos effects, clicks that land late, and an opponent that fights like the people in the logs (`train/people.py`);
the master was trained in it with the master of 1.40 as a fixed rival (`selfplay2.py --rival`).

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
| `pvp_sim2/env.py`, `obs.py`, `scripted.py` | `Batch2` (n arenas, a scenario per episode: map, kits, 1v1 / 1v2 / three-way / 2v1, with four slots 2v2 / 3v1 / four-way), what each fighter perceives (view, line of sight, hearing, its target, second and third enemy, its team mate), memory and the history of v3, rewards shared within a team; a fight with team mates goes on until a team is left; `PvpEnv2` for Gymnasium; a scripted bot that uses its items. |
| `tools/show_fight2.py` | v2 fights on their maps drawn to a PNG. |
| `train/selfplay2.py` | PPO self-play on v2 with the arenas in worker processes (3 workers x 96 arenas): a pool of earlier versions, the scripted bot, random play and dummies as opponents per slot; a curriculum that widens the scenarios (flat sword duels to everything), moves from easy opponents to the pool and fades the shaping later on. Each checkpoint is evaluated by `train/eval2.py` in a low-priority process. |
| `train/eval2.py` | A checkpoint against the scripted bot on eight fixed scenarios (sword, axe and bow duels, bridge, SkyWars loot, sumo, the town 1v2, Bed Wars three-way) and against a dummy on a bridge; item use per fight; the team block (the town 1v2 over 192 fights, 2v2 with the sword and Bed Wars in teams, the model with a scripted team mate). Played in the v3 simulation; v2 models read its first 118 values. |
| `train/league2.py` | The league over a mix of the modes' 1v1 scenarios, Elo, the six levels, ONNX export for v3 (`--levels`/`--keep`: only some levels anew); `--versus` two checkpoints over the mix; `--versus-team` two checkpoints in team fights (two against two, one against two); `--versus-v1` a checkpoint against v1's best in sword and boxing on flat ground (v1 through an adapter). |
| `train/policies.py` | Loading a checkpoint (PPO or MaskablePPO) and choosing with the action masks, as the mod does; used by eval, league, pool and copying. |
| `pvp_sim2/masks.py`, `spec/combat_v2_masks.json` | The action masks: which choices of each head a fighter may make, from its observation (golden: `spec/combat_v2_masks_golden.json`). |
| `spec/combat_v3.json` | Spec v3: v2's 118 observations unchanged, then the nearest team mate (8), a third enemy (8), memory (3: since I saw my target, since I was hurt, since I hurt it) and 16 values of 1 and 3 decisions ago (32, each with `of` and `ago`): 169. The same 9 action heads; `combat_v3_masks.json` has v2's rules for v3's sha. |
| `spec/combat_v3_golden.json`, `combat_v3_masks_golden.json` | v3's golden files: states with a team mate, a third enemy and memory, the earlier observations their history comes from, the observation and the masks (`tools/make_golden3.py`). |
| `train/upgrade_v3.py` | A v2 checkpoint as a v3 checkpoint that plays exactly the same (the new inputs get weight 0): the start of v3's training and the lower levels' v3 models. |
| `tools/make_bc_data.py`, `train/bc.py` | Copying the scripted teacher's item choices (behavior cloning) before PPO. |
| `spec/combat_v1_ranges.json`, `combat_v2_ranges.json`, `combat_v3_ranges.json` | What each model saw in training: per observation value the range the simulation produced (0.1% and 99.9% quantiles over about 400,000 decisions between the shipped models, the scripted bot and random play; `tools/make_ranges.py`). The mod counts a value outside it (by more than 0.05) as new to the model. |
| `spec/lessons.json` | **The** table of what a bot should practise, read by the mod and by `train/focus.py`: the lesson behind each reason a fight left the model's choice and behind each observation value, and per lesson the scenario weights it raises. |
| `train/fights.py` | Reads the mod's fight logs (`config/battlehub/fights`, `/battlehub fights export` zips): per decision and fighter the stored state (the observation is computed here, v3's history from the fighter's earlier decisions; logs of spec v2 read with nobody else and nothing remembered) and what it did as action indices; a summary per fight. |
| `train/imitate.py` | Behavior cloning: a checkpoint's policy learns to do what the people in the fight logs did; saves `ckpt_bc.zip` for `selfplay2.py --init`. |
| `spec/fightlog_sample.jsonl` | A short fight log written by the mod's `FightLogTest` from golden states, with the observation the mod computed; `tests/test_fights.py` checks the simulation's observation of each stored state is the same. |
| `train/simfit.py` | Plays the fight logs back in the simulation (phase 3D): a fighter's stored state and action two ticks on flat ground against its next decision (movement), the push of a hit timed by how the victim rose, damage against `World.melee_damage`, the reach of hits, which observation values were outside the training ranges (coverage), and how bots and people behave (sprinting, strafe changes, jumps, clicks, charge at a hit, distance). Fights split fixed into fit and test by name; `--effects` writes the Chaos profiles of the logs. |
| `tools/chaos_effects.py`, `spec/chaos_effects.json` | The Chaos events that change a body (speed, jump strength, size, gravity, strength, resistance, attack speed, reach, freezing), read from the mod's `ChaosCatalog.java` with the game's attribute rules; `--check` keeps the file in step with the catalogue. `pvp_sim2/kits.py` deals them in Chaos arenas. |
| `spec/effects_game.json` | The effect profiles that were on in the fight logs (`simfit.py --effects`); half of the Chaos arenas take one of these instead of drawing from the catalogue. |
| `train/people.py` | An opponent that fights like people: behavior cloning on the decisions where people really fought (target in sight within 8 blocks), starting from the master with a pull back to it (KL), and per head as sticky as the people were (they change their keys less often than they decide) with offsets so sprinting, jumping and clicking come as often as in the logs. `selfplay2.py --people` puts it in the pool, `eval2.py --people` adds a block against it. |
| `train/focus.py` | Reads the mod's `config/battlehub/ml_feedback.json` and turns its lessons into the scenario weights of `pvp_sim2` (`selfplay2.py --focus`). |

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

# v2 (every melee mode; how 1.34 to 1.39 were trained, now history)
python train/selfplay2.py --run runs/v2 --spec 2 --steps 60000000   # about 4000 steps a second on 4 cores
python train/league2.py --versus-v1 runs/v3b/ckpt_002000160.zip --v1 runs/sword/ckpt_016000000.zip --episodes 400

# phase 3C: spec v3 from the v2 master (team mates, a third enemy, memory, four slots)
python train/upgrade_v3.py runs/v3b/ckpt_004000032.zip levels/v3b/ckpt_004000032.zip
python train/selfplay2.py --run runs/v3c --init levels/v3b/ckpt_004000032.zip --pool-from runs/v3b runs/ab_nobc --masks \
    --extra-items 1.0 --scripted-latch --v1 runs/sword/ckpt_016000000.zip --v1-share 0.1 --eval-hands food,gapple,wind --steps 20000000
python train/eval2.py runs/v3c/ckpt_010000000.zip --team-only --hands food,gapple,wind
python train/league2.py --versus-team "runs/v3c/ckpt_010000000.zip#hands=food,gapple,wind" "levels/v3b/ckpt_004000032.zip#hands=food,gapple,wind"

# phase 3 (see PLAN.md, ML fase 3): masks, more items, v1 as an opponent in the pool (spec v2: --spec 2)
python train/selfplay2.py --run runs/v3b --init <master> --pool-from <its run> --masks --extra-items 1.0 \
    --scripted-latch --v1 runs/sword/ckpt_016000000.zip --v1-share 0.25 --family-weight sword=3 --pair sword=walled

# what the bots lacked on the server: practise more of it
python train/focus.py ml_feedback.json                           # shows what it would change
python train/selfplay2.py --run runs/v2focus --init runs/v2/ckpt_040000032.zip --pool-from runs/v2 --focus ml_feedback.json --steps 16000000
python tools/make_ranges.py                                      # after a new model: what it saw in training

# learn from recorded fights (the mod: /battlehub fights export)
python train/fights.py export-20261023-201500.zip                # what is in it
python train/imitate.py exports/ --init runs/v2/ckpt_040000032.zip --out runs/v2bc
python train/selfplay2.py --run runs/v2bc_sp --init runs/v2bc/ckpt_bc.zip --pool-from runs/v2 --items --steps 16000000

# phase 3D: a simulation fitted on the fight logs (see PLAN.md, Fase D)
python train/simfit.py exports/ --part test                       # sim against game; --json report.json
python train/simfit.py exports/ --part fit --effects spec/effects_game.json
python tools/chaos_effects.py --check                            # Chaos events from the catalogue
python train/people.py exports/ --init runs/v3d/ckpt_006000192.zip --out runs/people
python train/selfplay2.py --run runs/v3e --init runs/v3d/ckpt_006000192.zip --pool-from runs/v3d runs/v3c --masks \
    --extra-items 1.0 --scripted-latch --people runs/people --people-share 0.15 --eval-hands food,gapple,wind --steps 16000000
python train/selfplay2.py --run runs/v3f --init runs/v3e/ckpt_010000224.zip --pool-from runs/v3e runs/v3d --masks \
    --extra-items 1.0 --scripted-latch --people runs/people --rival "runs/v3d/ckpt_006000192.zip#hands=food,gapple,wind" \
    --rival-share 0.35 --three 0.35 --four 0.15 --eval-hands food,gapple,wind --steps 20000000   # the master of 1.41: 14M
python train/league2.py --versus "runs/v3f/ckpt_014000256.zip#hands=food,gapple,wind" \
    "runs/v3d/ckpt_006000192.zip#hands=food,gapple,wind" --episodes 128 --sim 1.40     # or the fitted sim (default)
```

From the mod back to training: after every match with learned bots the mod counts per bot how many fight ticks went
as the model chose (kept), how many its own rules changed (an edge, a jump into the void, steering back in the air,
being stuck, eating, the objective) and how many the scripted mind fought instead (away from an edge, dodging,
healing, the bow, searching, reacting), and which observation values were new to the model. Operators get that in
chat with the lessons that follow (`spec/lessons.json`); the totals per spec and mode go to
`config/battlehub/ml_feedback.json` on the server. Copy that file here and pass it to `--focus`: each lesson multiplies
the scenario weights it names (a lesson explaining a fifth of the fights or more fully, a smaller one partly). A lesson
marked `items` (eating, the bow) needs `--items` as well; `aim` and `timing` have no focus, they mean the simulation
itself differs from the game there.

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
