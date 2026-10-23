"""train/simfit.py replays the game's fight logs in the simulation. A log the simulation wrote itself (two fighters on
flat ground, rows written at the end of their tick as the mod does, a click that lands) must replay without error:
the moves, the damage and the push."""
import numpy as np

from pvp_sim2.kits import base, weapon
from pvp_sim2.obs import RAYS, STATE3_KEYS
from pvp_sim2.physics import World
from pvp_sim2.spec import SPEC3
from train import fights
from train import simfit


def _state(w, i, j):
    """The log's state of slot i (target j) of arena 0, as the mod stores it."""
    f = w.f
    v = {k: 0.0 for k in STATE3_KEYS if k not in RAYS}
    v.update({"self_x": f.x[0, i], "self_y": f.y[0, i], "self_z": f.z[0, i], "self_vx": f.vx[0, i], "self_vy": f.vy[0, i],
              "self_vz": f.vz[0, i], "self_yaw": f.yaw[0, i], "self_pitch": f.pitch[0, i], "self_on_ground": float(f.on_ground[0, i]),
              "self_sprinting": float(f.sprinting[0, i]), "self_charge": f.charge()[0, i],
              "self_hurt_ticks": float(max(0, f.invul[0, i] - 10)), "self_health": f.health[0, i], "self_max_health": 20.0,
              "self_food": f.food[0, i], "self_fall": f.fall[0, i], "self_scale": f.scale[0, i], "self_reach": f.reach[0, i],
              "self_melee_damage": f.atk_damage[0, i] + f.strength[0, i], "self_melee_bonus": f.sharp[0, i],
              "self_cooldown": 20.0 / f.atk_speed[0, i], "self_kb": float(f.kb_level[0, i]), "self_armor": f.armor[0, i],
              "self_toughness": f.toughness[0, i], "self_walk_speed": f.walk_speed[0, i], "self_jump_power": f.jump_power[0, i],
              "self_gravity": f.gravity[0, i], "self_hand": float(f.hand[0, i]), "self_below": f.y[0, i] - w.terrain.ground[0, 0, 0],
              "opp_x": f.x[0, j], "opp_y": f.y[0, j], "opp_z": f.z[0, j], "opp_vx": f.vx[0, j], "opp_vy": f.vy[0, j],
              "opp_vz": f.vz[0, j], "opp_yaw": f.yaw[0, j], "opp_on_ground": float(f.on_ground[0, j]), "opp_visible": 1.0,
              "opp_health": f.health[0, j], "opp_max_health": 20.0, "opp_scale": 1.0, "opp_cooldown": 12.5, "opp_melee_damage": 7.0,
              "opp_since_swing": 100.0, "e2_since_swing": 100.0, "e3_since_swing": 100.0, "ally_health": 20.0,
              "ally_max_health": 20.0, "opp_unseen": 0.0, "self_since_hit": 100.0, "opp_since_hit": 100.0, "damage_on": 1.0})
    vals = [float(v[k]) for k in STATE3_KEYS if k not in RAYS]
    for _ in RAYS:
        vals += [SPEC3.ray_max] * 8
    return vals


def _fight():
    """Two fighters on flat ground at y 64: A (slot 0, sword) walks at B and clicks once with a full charge at tick 11;
    B strafes from tick 14 on and jumps twice. A row per fighter every second tick, written after the tick."""
    w = World(1, 2, spec=SPEC3, rng=np.random.default_rng(0))
    w.terrain.ground[0] = 64.0
    w.terrain.floor[0] = 64.0
    w.place(0, 0, -1.2, 64.0, 0.0, -90.0, weapon(base(), "diamond_sword"), 0)
    w.place(0, 1, 1.2, 64.0, 0.0, 90.0, base(), 1)
    rows, hits = [], []
    plan = {0: dict(forward=1, strafe=0, jump=0, sprint=1, yaw=0.0), 1: dict(forward=0, strafe=0, jump=0, sprint=0, yaw=4.0)}
    for t in range(0, 40, 2):
        plan[1]["strafe"] = 1 if t >= 14 else 0
        for i, j in ((0, 1), (1, 0)):
            p = plan[i]
            jump = 1 if (i == 1 and t in (20, 30)) else 0
            attack = 1 if (i == 0 and t == 10) else 0
            rows.append({"t": t, "f": i, "o": j, "s": _state(w, i, j),
                         "a": [p["forward"], p["strafe"], jump, p["sprint"], attack, p["yaw"], 0.0, 0, 0]})
        for k in range(2):
            z = lambda: np.zeros((1, 2))
            fw, st, yl = z(), z(), z()
            ju, sp, at = np.zeros((1, 2), bool), np.zeros((1, 2), bool), np.zeros((1, 2), bool)
            for i in (0, 1):
                fw[0, i], st[0, i], yl[0, i] = plan[i]["forward"], plan[i]["strafe"], plan[i]["yaw"] / 2
                sp[0, i] = plan[i]["sprint"]
                ju[0, i] = i == 1 and t in (20, 30)
            at[0, 0] = t == 10 and k == 0
            before = w.f.health[0, 1]
            charge, ticker = w.f.charge()[0, 0], w.f.ticker[0, 0]
            w.tick(fw, st, ju, sp, at, np.zeros((1, 2), bool), yl, z(), np.zeros((1, 2), np.int64))
            if w.f.health[0, 1] < before:
                dmg = w.melee_damage(0, np.array([charge]), np.array([False]), np.array([False]))[0]
                hits.append({"t": t + k + 1, "hit": [0, 1], "dmg": float(dmg), "kind": "melee"})
    header = {"format": fights.FORMAT, "version": 1, "mod": "test", "decision_ticks": 2, "state_keys": STATE3_KEYS,
              "rays": list(RAYS), "ray_length": 8, "action_names": SPEC3.action_names, "velocities": "game", "mode": "sword",
              "fighters": [{"i": 0, "kind": "bot", "team": 0}, {"i": 1, "kind": "human", "team": 1}]}
    return fights.Fight("sim.jsonl", header, rows, hits, {"end": True, "t": 40, "winners": [0]})


def test_a_log_the_simulation_wrote_replays_without_error():
    f = _fight()
    assert len(f.hits) == 1, "the click landed once"
    logs = [simfit.Log(f)]
    moves = simfit.measure_moves(logs)
    for kind in ("bot", "human"):
        assert moves[kind]["position"]["n"] >= 10
        assert moves[kind]["position"]["p90"] < 1e-6 and moves[kind]["velocity"]["p90"] < 1e-6
    hits = simfit.measure_hits(logs)
    assert abs(hits["damage_ratio"]["bot"]["median"] - 1.0) < 1e-6
    assert hits["reach"]["bot"]["median"] < 3.0
    push = simfit.measure_push(logs)
    assert push["human"]["velocity_error"]["n"] == 1
    assert push["human"]["velocity_error"]["median"] < 1e-6 and push["human"]["speed_sim"]["median"] > 0.1


def test_the_report_has_every_measure_and_a_fixed_test_part():
    f = _fight()
    r = simfit.report([f])
    assert set(r) >= {"move", "hits", "push", "coverage", "behaviour"}
    assert r["behaviour"]["bot"]["sprint_share"] > 0.5 and r["behaviour"]["human"]["jumps_per_s"] > 0
    assert "move bot" in simfit.text(r)
    assert simfit.part_of("a.jsonl.gz") == simfit.part_of("dir/a.jsonl.gz") and {simfit.part_of("f%d" % i) for i in range(20)} == {"fit", "test"}
