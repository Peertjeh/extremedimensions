"""Makes ml/spec/combat_v2_golden.json for spec v2: CombatStates with the observation they must give, action indices with
what they decode to, and small heightmaps with the terrain rays they must give. The mod's JUnit test computes the same
in Java (net.battlehub.core.ml: CombatObservation, CombatAction, TerrainRays) and must match.

Also ml/spec/combat_v2_masks_golden.json: observations (the golden cases' and some at the edges of the item rules)
with the action masks they must give (spec/combat_v2_masks.json) and, for given logits, the best allowed choice per
head; the mod's CombatMasks must give the same.

    python tools/make_golden2.py          # write the file
    python tools/make_golden2.py --check  # fail when the file is not what the spec gives now
"""
import json
import math
import os
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from pvp_sim.actions import decode  # noqa: E402
from pvp_sim2.obs import BOOLS, E2, INTS, OPP2, RAYS, SELF2, THREAT, observe  # noqa: E402
from pvp_sim2.spec import SPEC2  # noqa: E402
from pvp_sim2.terrain import Terrain  # noqa: E402

OUT = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "spec", "combat_v2_golden.json"))
OUT_MASKS = os.path.join(os.path.dirname(OUT), "combat_v2_masks_golden.json")


def states(n=150, seed=20261022):
    rng = np.random.default_rng(seed)
    u = lambda a, b: rng.uniform(a, b, n)
    ch = lambda *v: rng.choice(v, n)
    yes = lambda p: rng.random(n) < p
    s = {
        "self_x": u(-10, 10), "self_y": ch(0.0, 0.0, 0.5, 1.0, 3.0), "self_z": u(-10, 10),
        "self_vx": rng.normal(0, 0.2, n), "self_vy": ch(-0.0784, 0.42, -0.3, 0.1, -1.2), "self_vz": rng.normal(0, 0.2, n),
        "self_yaw": u(-180, 180), "self_pitch": u(-60, 90), "self_on_ground": yes(0.7), "self_sprinting": yes(0.4),
        "self_charge": u(0, 1), "self_hurt_ticks": rng.integers(0, 11, n).astype(float), "self_health": u(1, 20),
        "self_max_health": np.full(n, 20.0), "self_absorption": ch(0.0, 0.0, 4.0, 2.5), "self_food": u(0, 20),
        "self_saturation": u(0, 20), "self_regen": ch(0.0, 0.25, 2.0, 0.8), "self_fall": ch(0.0, 0.0, 1.0, 4.5, 12.0),
        "self_scale": ch(1.0, 1.0, 1.0, 0.5, 2.0), "self_reach": ch(3.0, 3.0, 4.5), "self_melee_damage": ch(1.0, 4.0, 7.0, 9.0, 13.0),
        "self_melee_bonus": ch(0.0, 0.0, 1.0, 1.5), "self_cooldown": ch(12.5, 20.0, 25.0, 5.0, 33.333333), "self_kb": ch(0.0, 0.0, 1.0, 2.0),
        "self_armor": ch(0.0, 7.0, 15.0, 20.0, 11.0), "self_toughness": ch(0.0, 0.0, 8.0, 12.0), "self_resistance": ch(0.0, 0.0, 1.0, 5.0),
        "self_walk_speed": ch(0.1, 0.1, 0.14, 0.085), "self_jump_power": ch(0.42, 0.42, 0.62), "self_gravity": ch(0.08, 0.08, 0.04, 0.12),
        "self_arrows": ch(0.0, 0.0, 1.0, 16.0, 32.0), "self_food_items": ch(0.0, 0.0, 8.0, 3.0), "self_gapples": ch(0.0, 3.0, 8.0),
        "self_pearls": ch(0.0, 0.0, 2.0), "self_winds": ch(0.0, 0.0, 16.0), "self_shield": yes(0.2),
        "self_pearl_cd": ch(0.0, 0.0, 12.0), "self_wind_cd": ch(0.0, 0.0, 5.0), "self_shield_cd": ch(0.0, 0.0, 60.0),
        "self_hand": rng.integers(0, 6, n), "self_use_ticks": ch(0.0, 0.0, 7.0, 25.0, 40.0), "self_blocking": yes(0.1),
        "opp_vx": rng.normal(0, 0.3, n), "opp_vy": rng.normal(0, 0.2, n), "opp_vz": rng.normal(0, 0.3, n),
        "opp_yaw": u(-180, 180), "opp_on_ground": yes(0.7), "opp_sprinting": yes(0.4), "opp_since_swing": rng.integers(0, 40, n).astype(float),
        "opp_hurt_ticks": rng.integers(0, 11, n).astype(float), "opp_health": u(1, 20), "opp_max_health": np.full(n, 20.0),
        "opp_absorption": ch(0.0, 0.0, 4.0), "opp_visible": yes(0.75), "opp_blocking": yes(0.15), "opp_draw": ch(0.0, 0.0, 9.0, 21.0),
        "opp_eating": yes(0.1), "opp_holds_bow": yes(0.15), "opp_holds_food": yes(0.1), "opp_holds_throwable": yes(0.1),
        "opp_melee_damage": ch(1.0, 5.0, 7.0, 9.0), "opp_melee_bonus": ch(0.0, 0.0, 1.5), "opp_cooldown": ch(12.5, 20.0, 5.0),
        "opp_kb": ch(0.0, 0.0, 1.0), "opp_armor": ch(0.0, 7.0, 15.0, 20.0), "opp_toughness": ch(0.0, 0.0, 8.0),
        "opp_resistance": ch(0.0, 0.0, 2.0, 5.0), "opp_scale": ch(1.0, 1.0, 1.0, 0.5, 1.5),
        "e2_present": yes(0.4), "e2_yaw": u(-180, 180), "e2_visible": yes(0.6), "e2_since_swing": rng.integers(0, 40, n).astype(float),
        "e2_holds_bow": yes(0.2), "threat_present": yes(0.3), "damage_on": yes(0.85), "enemies_near": rng.integers(0, 3, n).astype(float),
        "self_below": ch(0.0, 0.0, 0.0, 2.5, 8.0), "opp_below": ch(0.0, 0.0, 1.0, 8.0), "opp_drop_behind": u(0, 4),
        "opp_void_behind": u(0, 4),
    }
    for k in RAYS:
        s[k] = np.round(rng.uniform(0, 4, (n, 8)) * 4) / 4
    # the target near (in reach, often right in front, so the crosshair is on it), or far
    dist = np.where(rng.random(n) < 0.6, u(0.5, 3.4), u(3.4, 40))
    off = np.where(rng.random(n) < 0.5, rng.normal(0, 8, n), u(-180, 180))
    ang = np.radians(s["self_yaw"] - off)
    s["opp_x"] = s["self_x"] - np.sin(ang) * dist
    s["opp_z"] = s["self_z"] + np.cos(ang) * dist
    s["opp_y"] = s["self_y"] + ch(0.0, 0.0, 0.5, -0.4, 1.2, -5.0)
    s["e2_x"] = s["self_x"] + u(-15, 15)
    s["e2_y"] = s["self_y"] + ch(0.0, 0.0, 1.0)
    s["e2_z"] = s["self_z"] + u(-15, 15)
    # a projectile: towards the chest or not
    s["threat_x"] = s["self_x"] + u(-12, 12)
    s["threat_y"] = s["self_y"] + u(0, 4)
    s["threat_z"] = s["self_z"] + u(-12, 12)
    tx = s["self_x"] - s["threat_x"] + rng.normal(0, 1, n)
    ty = s["self_y"] + 1.2 - s["threat_y"]
    tz = s["self_z"] - s["threat_z"] + rng.normal(0, 1, n)
    ln = np.sqrt(tx * tx + ty * ty + tz * tz)
    sp = np.where(yes(0.8), u(0.5, 3.0), -u(0.5, 3.0))
    s["threat_vx"], s["threat_vy"], s["threat_vz"] = tx / ln * sp, ty / ln * sp, tz / ln * sp
    # exact cases: the target straight ahead at 2.5, the edge of reach, at the same spot; a still projectile
    for k, v in {"opp_x": 0, "opp_z": 2.5, "opp_y": 0, "self_x": 0, "self_z": 0, "self_y": 0, "self_yaw": 0, "self_pitch": 0,
                 "self_scale": 1.0, "opp_scale": 1.0, "self_reach": 3.0}.items():
        s[k][0] = v
    s["opp_x"][1], s["opp_z"][1] = s["self_x"][1], s["self_z"][1]
    s["threat_present"][2] = True
    s["threat_vx"][2] = s["threat_vy"][2] = s["threat_vz"][2] = 0.0
    s["e2_present"][3] = True
    s["e2_x"][3], s["e2_z"][3] = s["self_x"][3], s["self_z"][3]
    # short numbers keep the file small; the observation is computed from exactly these
    for k, v in s.items():
        if v.dtype.kind == "f":
            s[k] = np.round(v, 6)
    return s


def heightmaps(count=6, seed=99, size=16):
    """Small maps with steps, slabs, walls, drops and the void, and queries at many places and yaws."""
    rng = np.random.default_rng(seed)
    t = Terrain(count, size=size, spec=SPEC2)
    out = []
    for a in range(count):
        g = np.full((size, size), 0.0)
        for _ in range(10):
            i, j = rng.integers(0, size, 2)
            w, d = rng.integers(1, 5, 2)
            g[i:i + w, j:j + d] = rng.choice([np.nan, -1.0, -3.0, 0.5, 1.0, 1.5, 2.0, 6.0, -12.0])
        t.ground[a] = g
        qs = []
        for _ in range(12):
            x, z = np.round(rng.uniform(-6, 6, 2), 4)
            y = float(rng.choice([0.0, 0.5, 1.0, -1.0]))
            yaw = float(np.round(rng.uniform(-180, 180), 3))
            r = np.array([a])
            rays = t.rays(r, np.array([x]), np.array([y]), np.array([z]), np.array([yaw]))
            q = {"x": x, "y": y, "z": z, "yaw": yaw, "below": float(t.below(r, np.array([x]), np.array([y]), np.array([z]))[0])}
            for name, v in zip(RAYS, rays):
                q[name] = [float(e) for e in v[0]]
            ang = float(np.round(rng.uniform(0, 2 * np.pi), 4))
            drop, void = t.behind(r, np.array([x]), np.array([y]), np.array([z]), np.array([math.cos(ang)]), np.array([math.sin(ang)]))
            q.update({"dx": math.cos(ang), "dz": math.sin(ang), "behind_drop": float(drop[0]), "behind_void": float(void[0])})
            qs.append(q)
        out.append({"size": size, "ground": [[None if np.isnan(v) else float(v) for v in row] for row in g], "queries": qs})
    return out


def build():
    s = states()
    obs = observe(s)
    rng = np.random.default_rng(7)
    idx = rng.integers(0, SPEC2.action_nvec, size=(64, len(SPEC2.action_nvec)))
    idx[0] = 0
    idx[1] = SPEC2.action_nvec - 1
    dec = decode(idx, SPEC2)
    keys = ["self_" + k for k in SELF2] + ["opp_" + k for k in OPP2] + ["e2_" + k for k in E2] + ["threat_" + k for k in THREAT] + \
        ["damage_on", "enemies_near", "self_below", "opp_below", "opp_drop_behind", "opp_void_behind"]
    cases = []
    for i in range(obs.shape[0]):
        st = {}
        for k in keys:
            st[k] = bool(s[k][i]) if k in BOOLS else int(s[k][i]) if k in INTS else float(s[k][i])
        for k in RAYS:
            st[k] = [float(v) for v in s[k][i]]
        cases.append({"state": st, "observation": [float(v) for v in obs[i]]})
    actions = [{"index": [int(v) for v in idx[i]], "decoded": {k: float(dec[k][i]) for k in SPEC2.action_names}} for i in range(idx.shape[0])]
    return {"spec": SPEC2.name, "spec_version": SPEC2.version, "spec_sha256": SPEC2.sha256, "observation_names": SPEC2.obs_names,
            "tolerance": 1e-6, "cases": cases, "actions": actions, "terrain": heightmaps()}


def masks_golden(obs, masks=None, path=None):
    """The golden cases' observations and copies of 60 of them with the item values at the edges of the rules (none,
    one, full hunger, a shield with and without its cooldown), plus one with nothing at all; with random logits. The
    masks of spec v2 unless others are given (tools/make_golden3.py)."""
    import hashlib
    from pvp_sim2.masks import MASKS2, MASKS2_PATH
    if masks is None:
        masks, path = MASKS2, MASKS2_PATH
    sp = masks.spec
    rng = np.random.default_rng(31)
    edge = obs[:60].copy()
    choices = {"self_arrows": [0.0, 1 / 16, 1.0], "self_food_items": [0.0, 0.125, 1.0], "self_food": [0.0, 0.95, 1.0, 1.0],
               "self_gapples": [0.0, 0.25], "self_pearls": [0.0, 0.25, 1.0], "self_winds": [0.0, 0.125],
               "self_shield": [0.0, 1.0], "shield_cooldown": [0.0, 0.0, 0.01, 1.0]}
    for name, vals in choices.items():
        edge[:, sp.index(name)] = rng.choice(vals, edge.shape[0])
    nothing = obs[:1].copy()
    for name in choices:
        nothing[:, sp.index(name)] = 1.0 if name == "self_food" else 0.0
    all_obs = np.concatenate([obs, edge, nothing]).astype(np.float32)
    logits = np.round(rng.normal(0, 2, (all_obs.shape[0], int(sp.action_nvec.sum()))), 4)
    mask = masks(all_obs)
    best = masks.best(logits, mask)
    with open(path, "rb") as f:
        masks_sha = hashlib.sha256(f.read()).hexdigest()
    cases = [{"observation": [float(v) for v in all_obs[i]], "mask": "".join("1" if m else "0" for m in mask[i]),
              "logits": [float(v) for v in logits[i]], "best": [int(v) for v in best[i]]} for i in range(all_obs.shape[0])]
    return {"spec": sp.name, "spec_sha256": sp.sha256, "masks_sha256": masks_sha, "cases": cases}


def dump(data):
    """One case, action or map per line."""
    lines = ["{"]
    keys = list(data)
    for n, k in enumerate(keys):
        end = "," if n < len(keys) - 1 else ""
        v = data[k]
        if k in ("cases", "actions", "terrain") and isinstance(v, list):
            lines.append('  "%s": [' % k)
            lines += ["    " + json.dumps(e, separators=(",", ":")) + ("," if i < len(v) - 1 else "") for i, e in enumerate(v)]
            lines.append("  ]" + end)
        else:
            lines.append('  "%s": %s%s' % (k, json.dumps(v), end))
    return "\n".join(lines + ["}"]) + "\n"


def main():
    data = build()
    if "--check" in sys.argv:
        with open(OUT, encoding="utf-8") as f:
            old = json.load(f)
        if old["spec_sha256"] != data["spec_sha256"]:
            sys.exit("golden file was made from another spec: run tools/make_golden2.py")
        a = np.array([c["observation"] for c in old["cases"]])
        b = np.array([c["observation"] for c in data["cases"]])
        if a.shape != b.shape or not np.allclose(a, b, atol=1e-9):
            sys.exit("golden observations differ from the simulation: run tools/make_golden2.py")
        if json.dumps(old["terrain"]) != json.dumps(data["terrain"]):
            sys.exit("golden terrain rays differ from the simulation: run tools/make_golden2.py")
        print("golden file is up to date (%d cases, %d actions, %d maps)" % (len(old["cases"]), len(old["actions"]), len(old["terrain"])))
        with open(OUT_MASKS, encoding="utf-8") as f:
            old_m = json.load(f)
        new_m = masks_golden(np.array([c["observation"] for c in data["cases"]]))
        if json.dumps(old_m, sort_keys=True) != json.dumps(json.loads(dump(new_m)), sort_keys=True):
            sys.exit("golden masks differ from spec/combat_v2_masks.json: run tools/make_golden2.py")
        print("golden masks are up to date (%d cases)" % len(old_m["cases"]))
        return
    with open(OUT, "w", encoding="utf-8") as f:
        f.write(dump(data))
    print("wrote", OUT, len(data["cases"]), "cases")
    masks = masks_golden(np.array([c["observation"] for c in data["cases"]]))
    with open(OUT_MASKS, "w", encoding="utf-8") as f:
        f.write(dump(masks))
    print("wrote", OUT_MASKS, len(masks["cases"]), "cases")


if __name__ == "__main__":
    main()
