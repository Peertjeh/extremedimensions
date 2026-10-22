"""A league of checkpoints and scripted bots: everyone fights everyone, Elo ratings from the results, and the
checkpoints for the mod's six difficulty levels picked by rating (not by training time).

    python train/league.py runs/sword --out league --every 2 [--episodes 200]

Writes <out>/league.json (ratings and the results table) and <out>/difficulties.json, and exports the picked
checkpoints to <out>/combat_v1_<level>.onnx. Ratings are anchored on the scripted bot of skill 0.5 at 1000.
"""
import argparse
import glob
import itertools
import json
import os
import sys

import numpy as np
from stable_baselines3 import PPO

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from pvp_sim import SWORD, RandomPolicy, ScriptedPolicy  # noqa: E402
from train.export_onnx import export  # noqa: E402
from train.ratings import elo  # noqa: E402
from train.selfplay import model_fn, play  # noqa: E402

LEVELS = ["beginner", "casual", "intermediate", "advanced", "expert", "master"]
# what the mod adds on top of each level's model (ticks of extra perception delay, degrees of aim noise per tick,
# the most it turns per decision, and whether it samples its actions instead of taking the best)
EXTRA = {
    "beginner":     {"extra_delay": 4, "aim_noise": 2.5, "turn_cap": 24, "sample": True},
    "casual":       {"extra_delay": 3, "aim_noise": 1.8, "turn_cap": 30, "sample": True},
    "intermediate": {"extra_delay": 2, "aim_noise": 1.2, "turn_cap": 36, "sample": True},
    "advanced":     {"extra_delay": 1, "aim_noise": 0.8, "turn_cap": 45, "sample": False},
    "expert":       {"extra_delay": 0, "aim_noise": 0.5, "turn_cap": 54, "sample": False},
    "master":       {"extra_delay": 0, "aim_noise": 0.2, "turn_cap": 60, "sample": False},
}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("run")
    ap.add_argument("--out", default="league")
    ap.add_argument("--every", type=int, default=2, help="use every k-th checkpoint")
    ap.add_argument("--episodes", type=int, default=200)
    a = ap.parse_args()
    os.makedirs(a.out, exist_ok=True)
    ckpts = sorted(glob.glob(os.path.join(a.run, "ckpt_*.zip")))
    ckpts = ckpts[::a.every] + ([ckpts[-1]] if ckpts and ckpts[-1] not in ckpts[::a.every] else [])
    players = {}
    for path in ckpts:
        players["ckpt_%d" % int(os.path.basename(path)[5:14])] = model_fn(PPO.load(path, device="cpu"))
    for skill in (0.3, 0.5, 0.8, 1.0):
        players["scripted_%.1f" % skill] = ScriptedPolicy(skill, seed=int(skill * 100))
    players["random"] = RandomPolicy(seed=1)
    names = list(players)
    results = {}
    for i, (x, y) in enumerate(itertools.combinations(names, 2)):
        res = play(players[x], players[y], episodes=a.episodes, rules=SWORD, seed=i)
        results[(x, y)] = tuple(int(v) for v in res)
        print("%-16s vs %-16s %s" % (x, y, results[(x, y)]), flush=True)
    ratings = elo(names, results)
    table = sorted(ratings.items(), key=lambda kv: -kv[1])
    for n, v in table:
        print("%-16s %7.0f" % (n, v))
    # the levels: from the scripted bot of skill 0.3 to the best checkpoint, evenly in rating
    cks = {n: v for n, v in ratings.items() if n.startswith("ckpt_")}
    low, high = ratings["scripted_0.3"], max(cks.values())
    picks = {}
    for k, level in enumerate(LEVELS):
        target = low + (high - low) * (k + 1) / len(LEVELS)
        best = min(cks, key=lambda n: abs(cks[n] - target))
        picks[level] = {"checkpoint": best, "elo": round(cks[best]), "target_elo": round(target), **EXTRA[level],
                        "model": "combat_v1_%s.onnx" % level}
    path_of = {"ckpt_%d" % int(os.path.basename(p)[5:14]): p for p in ckpts}
    for level, p in picks.items():
        export(path_of[p["checkpoint"]], os.path.join(a.out, p["model"]), name=level,
               meta={"elo": str(p["elo"]), "level": level})
    with open(os.path.join(a.out, "league.json"), "w") as f:
        json.dump({"ratings": dict(table), "results": {"%s|%s" % k: v for k, v in results.items()}}, f, indent=1)
    with open(os.path.join(a.out, "difficulties.json"), "w") as f:
        json.dump(picks, f, indent=1)
    print(json.dumps(picks, indent=1))


if __name__ == "__main__":
    main()
