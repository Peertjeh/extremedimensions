"""What the model saw in training: per observation value the range the simulation produces (the 0.1% and 99.9%
quantiles over many fights at full breadth, between the shipped master and intermediate models, the scripted bot and
random play). The mod counts a decision's value as new to the model when it lies outside that range by more than the
file's tolerance, and tells the operators which values those were (bot/ModelReports, core/ml/ModelReport).

    python tools/make_ranges.py            # writes spec/combat_v3_ranges.json (v1, v2: their models are no longer shipped)
    python tools/make_ranges.py --steps 4000 --spec v3 [--models <dir with combat_v3_master.onnx and _intermediate.onnx>]

v3 is collected with four fighters per arena (team mates, a third enemy) and its history.
"""
import argparse
import json
import os
import sys

import numpy as np

HERE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, HERE)
MODELS = os.path.join(os.path.dirname(HERE), "src", "main", "resources", "assets", "battlehub", "ml")
TOLERANCE = 0.05
QUANTILES = (0.001, 0.999)


class Onnx:
    """A shipped model as a policy: the best choice per head, or a sample."""

    def __init__(self, path, spec, sample=False, seed=0):
        import onnxruntime as ort
        self.s = ort.InferenceSession(path, providers=["CPUExecutionProvider"])
        self.cuts = np.cumsum(spec.action_nvec)[:-1]
        self.sample = sample
        self.rng = np.random.default_rng(seed)

    def __call__(self, obs):
        logits = self.s.run(None, {"obs": np.asarray(obs, dtype=np.float32)})[0]
        out = []
        for part in np.split(logits, self.cuts, axis=1):
            if self.sample:
                p = np.exp(part - part.max(axis=1, keepdims=True))
                p /= p.sum(axis=1, keepdims=True)
                out.append((p.cumsum(axis=1) > self.rng.random((p.shape[0], 1))).argmax(axis=1))
            else:
                out.append(part.argmax(axis=1))
        return np.stack(out, axis=1)


def collect_v2(steps, n, seed):
    from pvp_sim2 import Batch2, Random2, Scripted2
    from pvp_sim2.spec import SPEC2
    master = Onnx(os.path.join(MODELS, "combat_v2_master.onnx"), SPEC2)
    mid = Onnx(os.path.join(MODELS, "combat_v2_intermediate.onnx"), SPEC2, sample=True, seed=seed)
    scripted, rnd = Scripted2(skill=0.8, seed=seed), Random2(seed=seed)
    # who plays which slot, a share of the steps each
    lineups = [(master, scripted, scripted), (master, master, master), (mid, scripted, rnd), (scripted, scripted, scripted), (master, mid, rnd)]
    rows = []
    for k, lineup in enumerate(lineups):
        b = Batch2(n, seed=seed + k, breadth=1.0)
        obs = b.reset()
        for _ in range(steps // len(lineups)):
            alive = b.w.f.alive
            rows.append(obs[alive])
            obs, _, _, _ = b.step(np.stack([lineup[i](obs[:, i]) for i in range(3)], axis=1))
    return SPEC2, np.concatenate(rows)


def collect_v3(steps, n, seed, models=MODELS):
    from pvp_sim2 import Batch2, Random2, Scripted2
    from pvp_sim2.spec import SPEC3
    master = Onnx(os.path.join(models, "combat_v3_master.onnx"), SPEC3)
    mid = Onnx(os.path.join(models, "combat_v3_intermediate.onnx"), SPEC3, sample=True, seed=seed)
    scripted, rnd = Scripted2(skill=0.8, seed=seed), Random2(seed=seed)
    lineups = [(master, scripted, scripted, scripted), (master, master, master, master), (mid, scripted, rnd, scripted),
               (scripted, scripted, scripted, scripted), (master, mid, rnd, master)]
    rows = []
    for k, lineup in enumerate(lineups):
        b = Batch2(n, k=4, seed=seed + k, breadth=1.0, spec=SPEC3)
        obs = b.reset()
        for _ in range(steps // len(lineups)):
            alive = b.w.f.alive & b.w.f.active
            rows.append(obs[alive])
            obs, _, _, _ = b.step(np.stack([lineup[i](obs[:, i]) for i in range(4)], axis=1))
    return SPEC3, np.concatenate(rows)


def collect_v1(steps, n, seed):
    from pvp_sim.env import PvpBatch
    from pvp_sim.physics import SWORD
    from pvp_sim.scripted import RandomPolicy, ScriptedPolicy
    from pvp_sim.spec import SPEC
    master = Onnx(os.path.join(MODELS, "combat_v1_master.onnx"), SPEC)
    mid = Onnx(os.path.join(MODELS, "combat_v1_intermediate.onnx"), SPEC, sample=True, seed=seed)
    scripted, rnd = ScriptedPolicy(skill=0.8, seed=seed), RandomPolicy(seed=seed)
    lineups = [(master, scripted), (master, master), (mid, rnd), (scripted, scripted)]
    rows = []
    for k, lineup in enumerate(lineups):
        b = PvpBatch(n, SWORD, seed=seed + k)
        obs = b.reset()
        for _ in range(steps // len(lineups)):
            rows.append(obs.reshape(-1, SPEC.obs_dim))
            obs, _, _, _ = b.step(np.stack([lineup[i](obs[:, i]) for i in range(2)], axis=1))
    return SPEC, np.concatenate(rows)


def ranges(spec, rows):
    lo = np.quantile(rows, QUANTILES[0], axis=0)
    hi = np.quantile(rows, QUANTILES[1], axis=0)
    return {"spec": spec.name, "spec_version": spec.version, "spec_sha256": spec.sha256, "tolerance": TOLERANCE,
            "quantiles": list(QUANTILES), "samples": int(rows.shape[0]),
            "doc": "per observation value (scaled and clipped, as the model takes it) the range the simulation produced in "
                   "training; written by tools/make_ranges.py",
            "ranges": {name: [round(float(a), 4), round(float(b), 4)] for name, a, b in zip(spec.obs_names, lo, hi)}}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--spec", choices=["v1", "v2", "v3", "all"], default="v3", help="v1 and v2 need their old models (--models)")
    ap.add_argument("--models", default=MODELS, help="where the v3 models are (default: the mod's)")
    ap.add_argument("--steps", type=int, default=3000, help="decisions per spec")
    ap.add_argument("--n", type=int, default=64, help="arenas")
    ap.add_argument("--seed", type=int, default=11)
    ap.add_argument("--out", help="write here instead of spec/combat_<v>_ranges.json (one spec)")
    a = ap.parse_args()
    for v, collect in (("v1", collect_v1), ("v2", collect_v2), ("v3", lambda *x: collect_v3(*x, models=a.models))):
        if a.spec not in (v, "all"):
            continue
        spec, rows = collect(a.steps, a.n, a.seed)
        out = a.out or os.path.join(HERE, "spec", "combat_%s_ranges.json" % v)
        with open(out, "w") as f:
            json.dump(ranges(spec, rows), f, indent=1)
        print("%s: %d samples -> %s" % (v, rows.shape[0], out))


if __name__ == "__main__":
    main()
