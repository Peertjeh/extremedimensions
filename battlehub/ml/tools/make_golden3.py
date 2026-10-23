"""Makes ml/spec/combat_v3_golden.json for spec v3: CombatStates with v3's team mate, third enemy and memory, the earlier
observations their history comes from, and the observation they must give; the mod's JUnit test (CombatObservationV3Test)
computes the same in Java and must match. The states are v2's golden states (tools/make_golden2.py) with v3's values
added. A case's "past" names the cases whose observations were 1, 2 and 3 decisions earlier (null: the start of a fight,
where an earlier decision's value is the value now); the history only reads values of now, so any case will do.

Also ml/spec/combat_v3_masks_golden.json: the masks (spec/combat_v3_masks.json, v2's rules) of these observations and
of copies at the edges of the item rules, with the best allowed choice for given logits.

    python tools/make_golden3.py          # write the files
    python tools/make_golden3.py --check  # fail when the files are not what the spec gives now
"""
import json
import os
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from make_golden2 import dump, masks_golden, states  # noqa: E402
from pvp_sim2.obs import ALLY, BOOLS, E2, INTS, MEMORY, OPP2, RAYS, SELF2, THREAT, observe  # noqa: E402
from pvp_sim2.spec import SPEC3  # noqa: E402

OUT = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "spec", "combat_v3_golden.json"))
OUT_MASKS = os.path.join(os.path.dirname(OUT), "combat_v3_masks_golden.json")


def states3(seed=20261023):
    """v2's golden states with a team mate (often), a third enemy (sometimes) and memory."""
    s = states()
    n = len(s["self_x"])
    rng = np.random.default_rng(seed)
    u = lambda a, b: rng.uniform(a, b, n)
    ch = lambda *v: rng.choice(v, n)
    yes = lambda p: rng.random(n) < p
    s.update({
        "ally_present": yes(0.6), "ally_x": s["self_x"] + u(-12, 12), "ally_y": s["self_y"] + ch(0.0, 0.0, 1.0, -2.0),
        "ally_z": s["self_z"] + u(-12, 12), "ally_visible": yes(0.6), "ally_health": u(1, 20), "ally_max_health": np.full(n, 20.0),
        "allies_near": rng.integers(0, 3, n).astype(float),
        "e3_present": yes(0.3), "e3_x": s["self_x"] + u(-20, 20), "e3_y": s["self_y"] + ch(0.0, 0.0, 1.5), "e3_z": s["self_z"] + u(-20, 20),
        "e3_yaw": u(-180, 180), "e3_visible": yes(0.5), "e3_since_swing": rng.integers(0, 40, n).astype(float), "e3_holds_bow": yes(0.2),
        "opp_unseen": ch(0.0, 0.0, 0.0, 6.0, 44.0, 100.0, 250.0), "self_since_hit": ch(0.0, 2.0, 15.0, 100.0, 1000.0),
        "opp_since_hit": ch(0.0, 4.0, 30.0, 100.0, 1000.0),
    })
    # exact cases: the team mate on the same spot, the third enemy on the same spot, the team mate on the target
    s["ally_present"][4] = True
    s["ally_x"][4], s["ally_z"][4] = s["self_x"][4], s["self_z"][4]
    s["e3_present"][5] = True
    s["e3_x"][5], s["e3_z"][5] = s["self_x"][5], s["self_z"][5]
    s["ally_present"][6] = True
    s["ally_x"][6], s["ally_z"][6] = s["opp_x"][6], s["opp_z"][6]
    for k, v in s.items():
        if v.dtype.kind == "f":
            s[k] = np.round(v, 6)
    return s


def build():
    s = states3()
    n = len(s["self_x"])
    now = observe(s, SPEC3)                      # every history value is the value now: the start of a fight
    rng = np.random.default_rng(8)
    past = [None if i < 20 else [int(j) for j in rng.integers(0, n, SPEC3.max_ago)] for i in range(n)]
    obs = now.copy()
    rows = [i for i in range(n) if past[i] is not None]
    hist = [now[[past[i][a] for i in rows]] for a in range(SPEC3.max_ago)]
    obs[rows] = observe({k: v[rows] for k, v in s.items()}, SPEC3, past=hist)
    keys = (["self_" + k for k in SELF2] + ["opp_" + k for k in OPP2] + ["e2_" + k for k in E2] + ["threat_" + k for k in THREAT]
            + ["damage_on", "enemies_near", "self_below", "opp_below", "opp_drop_behind", "opp_void_behind"]
            + ["ally_" + k for k in ALLY] + ["e3_" + k for k in E2] + ["allies_near"] + MEMORY)
    bools = BOOLS | {"ally_present", "ally_visible", "e3_present", "e3_visible", "e3_holds_bow"}
    cases = []
    for i in range(n):
        st = {}
        for k in keys:
            st[k] = bool(s[k][i]) if k in bools else int(s[k][i]) if k in INTS else float(s[k][i])
        for k in RAYS:
            st[k] = [float(v) for v in s[k][i]]
        cases.append({"state": st, "past": past[i], "observation": [float(v) for v in obs[i]]})
    return {"spec": SPEC3.name, "spec_version": SPEC3.version, "spec_sha256": SPEC3.sha256, "observation_names": SPEC3.obs_names,
            "tolerance": 1e-6, "cases": cases}


def masks3(obs):
    from pvp_sim2.masks import MASKS3, MASKS3_PATH
    return masks_golden(obs, MASKS3, MASKS3_PATH)


def main():
    data = build()
    obs = np.array([c["observation"] for c in data["cases"]])
    if "--check" in sys.argv:
        with open(OUT, encoding="utf-8") as f:
            old = json.load(f)
        if old["spec_sha256"] != data["spec_sha256"]:
            sys.exit("golden file was made from another spec: run tools/make_golden3.py")
        a = np.array([c["observation"] for c in old["cases"]])
        if a.shape != obs.shape or not np.allclose(a, obs, atol=1e-9) or [c["past"] for c in old["cases"]] != [c["past"] for c in data["cases"]]:
            sys.exit("golden observations differ from the simulation: run tools/make_golden3.py")
        print("golden file is up to date (%d cases)" % len(old["cases"]))
        with open(OUT_MASKS, encoding="utf-8") as f:
            old_m = json.load(f)
        if json.dumps(old_m, sort_keys=True) != json.dumps(json.loads(dump(masks3(obs))), sort_keys=True):
            sys.exit("golden masks differ from spec/combat_v3_masks.json: run tools/make_golden3.py")
        print("golden masks are up to date (%d cases)" % len(old_m["cases"]))
        return
    with open(OUT, "w", encoding="utf-8") as f:
        f.write(dump(data))
    print("wrote", OUT, len(data["cases"]), "cases")
    m = masks3(obs)
    with open(OUT_MASKS, "w", encoding="utf-8") as f:
        f.write(dump(m))
    print("wrote", OUT_MASKS, len(m["cases"]), "cases")


if __name__ == "__main__":
    main()
