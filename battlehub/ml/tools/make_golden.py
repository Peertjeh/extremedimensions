"""Makes ml/spec/combat_v1_golden.json: CombatStates with the observation they must give, and action indices with what
they decode to. The mod's JUnit test computes the same in Java (net.battlehub.core.ml) and must match.

    python tools/make_golden.py          # write the file
    python tools/make_golden.py --check  # fail when the file is not what the spec gives now
"""
import json
import os
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from pvp_sim.actions import decode  # noqa: E402
from pvp_sim.obs import OPP_FIELDS, SELF_FIELDS, observe  # noqa: E402
from pvp_sim.spec import SPEC  # noqa: E402

OUT = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "spec", "combat_v1_golden.json"))
BOOL_FIELDS = {"self_on_ground", "self_sprinting", "opp_on_ground", "opp_sprinting", "opp_visible"}


def states(n=160, seed=20261022):
    rng = np.random.default_rng(seed)
    s = {
        "self_x": rng.uniform(-10, 10, n), "self_y": rng.choice([0.0, 0.0, 0.6, 1.1], n), "self_z": rng.uniform(-10, 10, n),
        "self_vx": rng.normal(0, 0.2, n), "self_vy": rng.choice([-0.0784, 0.42, -0.3, 0.1], n), "self_vz": rng.normal(0, 0.2, n),
        "self_yaw": rng.uniform(-180, 180, n), "self_pitch": rng.uniform(-30, 40, n),
        "self_on_ground": rng.random(n) < 0.7, "self_sprinting": rng.random(n) < 0.4,
        "self_charge": rng.uniform(0, 1, n), "self_hurt_ticks": rng.integers(0, 11, n).astype(float),
        "self_health": rng.uniform(1, 20, n), "self_max_health": np.full(n, 20.0),
        "opp_vx": rng.normal(0, 0.3, n), "opp_vy": rng.normal(0, 0.2, n), "opp_vz": rng.normal(0, 0.3, n),
        "opp_yaw": rng.uniform(-180, 180, n), "opp_on_ground": rng.random(n) < 0.7, "opp_sprinting": rng.random(n) < 0.4,
        "opp_since_swing": rng.integers(0, 40, n).astype(float), "opp_hurt_ticks": rng.integers(0, 11, n).astype(float),
        "opp_health": rng.uniform(1, 20, n), "opp_max_health": np.full(n, 20.0), "opp_visible": rng.random(n) < 0.75,
        "edges": rng.uniform(0, 5, (n, 8)), "walls": rng.uniform(0, 5, (n, 8)),
    }
    # the opponent near (in reach, often right in front, so the crosshair is on it), or far
    dist = np.where(rng.random(n) < 0.6, rng.uniform(0.5, 3.4, n), rng.uniform(3.4, 40, n))
    off = np.where(rng.random(n) < 0.5, rng.normal(0, 8, n), rng.uniform(-180, 180, n))
    ang = np.radians(s["self_yaw"] - off)
    s["opp_x"] = s["self_x"] - np.sin(ang) * dist
    s["opp_z"] = s["self_z"] + np.cos(ang) * dist
    s["opp_y"] = s["self_y"] + rng.choice([0.0, 0.0, 0.5, -0.4, 1.2], n)
    # an exact case: straight ahead at 2.5, the edge of reach, same spot
    s["opp_x"][0], s["opp_z"][0], s["opp_y"][0], s["self_x"][0], s["self_z"][0], s["self_y"][0], s["self_yaw"][0], s["self_pitch"][0] = 0, 2.5, 0, 0, 0, 0, 0, 0
    s["opp_x"][1], s["opp_z"][1] = s["self_x"][1], s["self_z"][1]
    return s


def build():
    s = states()
    obs = observe(s)
    rng = np.random.default_rng(7)
    idx = rng.integers(0, SPEC.action_nvec, size=(64, len(SPEC.action_nvec)))
    idx[0] = 0
    idx[1] = SPEC.action_nvec - 1
    dec = decode(idx)
    keys = ["self_" + f for f in SELF_FIELDS] + ["opp_" + f for f in OPP_FIELDS]
    cases = []
    for i in range(obs.shape[0]):
        st = {k: (bool(s[k][i]) if k in BOOL_FIELDS else float(s[k][i])) for k in keys}
        st["edges"] = [float(v) for v in s["edges"][i]]
        st["walls"] = [float(v) for v in s["walls"][i]]
        cases.append({"state": st, "observation": [float(v) for v in obs[i]]})
    actions = [{"index": [int(v) for v in idx[i]], "decoded": {k: float(dec[k][i]) for k in SPEC.action_names}} for i in range(idx.shape[0])]
    return {"spec": SPEC.name, "spec_version": SPEC.version, "spec_sha256": SPEC.sha256, "observation_names": SPEC.obs_names,
            "tolerance": 1e-6, "cases": cases, "actions": actions}


def main():
    data = build()
    text = json.dumps(data, indent=1, sort_keys=False) + "\n"
    if "--check" in sys.argv:
        with open(OUT, encoding="utf-8") as f:
            old = json.load(f)
        if old["spec_sha256"] != data["spec_sha256"]:
            sys.exit("golden file was made from another spec: run tools/make_golden.py")
        a = np.array([c["observation"] for c in old["cases"]])
        b = np.array([c["observation"] for c in data["cases"]])
        if a.shape != b.shape or not np.allclose(a, b, atol=1e-9):
            sys.exit("golden observations differ from the simulation: run tools/make_golden.py")
        print("golden file is up to date (%d cases, %d actions)" % (len(old["cases"]), len(old["actions"])))
        return
    with open(OUT, "w", encoding="utf-8") as f:
        f.write(text)
    print("wrote", OUT, len(data["cases"]), "cases")


if __name__ == "__main__":
    main()
