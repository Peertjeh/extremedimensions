"""Evaluates a v2 checkpoint (or a policy) on a fixed set of scenarios against the scripted bot, and against a dummy on
a bridge; appends one JSON line to --out.

    python train/eval2.py runs/v2/ckpt_010000000.zip --out runs/v2/eval.jsonl [--episodes 48]
"""
import argparse
import json
import os
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from pvp_sim2 import Batch2, Dummy2, Scripted2  # noqa: E402

# map, kit, fighters, who against whom
SCENARIOS = [
    ("walled", "sword", 2, "1v1"),
    ("walled", "axe", 2, "1v1"),
    ("walled", "bow", 2, "1v1"),
    ("bridge", "bridge", 2, "1v1"),
    ("islands", "loot", 2, "1v1"),
    ("platform", "sumo", 2, "1v1"),
    ("town", "loot", 3, "1v2"),
    ("islands", "bedwars", 3, "ffa"),
]


def play(policy, opponent, terrain, family, count, mode, episodes=48, n=32, seed=0, max_ticks=1600):
    """Win, loss, draw of `policy` as slot 0 against `opponent` in the other slots."""
    b = Batch2(n, seed=seed, max_ticks=max_ticks, fixed={"terrain": terrain, "family": family, "count": count, "mode": mode})
    obs = b.reset()
    res = np.zeros(3, dtype=np.int64)
    while res.sum() < episodes:
        acts = np.stack([policy(obs[:, 0]), opponent(obs[:, 1]), opponent(obs[:, 2])], axis=1)
        obs, r, d, info = b.step(acts)
        for w in info["winner"][d]:
            res[0 if w == 0 else 1 if w == 1 else 2] += 1
    return res


def evaluate(policy, episodes=48, seed=0):
    out = {}
    wins = total = 0
    for k, (terrain, family, count, mode) in enumerate(SCENARIOS):
        res = play(policy, Scripted2(skill=0.8, seed=seed + k), terrain, family, count, mode, episodes, seed=seed + k)
        out["%s/%s/%s" % (terrain, family, mode)] = [int(v) for v in res]
        wins += res[0]
        total += res.sum()
    res = play(policy, Dummy2(seed=seed), "bridge", "bridge", 2, "1v1", episodes // 2, seed=seed + 99)
    out["dummy bridge"] = [int(v) for v in res]
    out["win_vs_scripted"] = round(wins / max(1, total), 3)
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("checkpoint")
    ap.add_argument("--out", default=None)
    ap.add_argument("--episodes", type=int, default=48)
    a = ap.parse_args()
    import torch
    from stable_baselines3 import PPO
    torch.set_num_threads(1)
    model = PPO.load(a.checkpoint, device="cpu")
    policy = lambda o: model.predict(o, deterministic=True)[0]
    row = {"checkpoint": os.path.basename(a.checkpoint), "steps": int(model.num_timesteps)}
    row.update(evaluate(policy, a.episodes))
    line = json.dumps(row)
    print(line)
    if a.out:
        with open(a.out, "a") as f:
            f.write(line + "\n")


if __name__ == "__main__":
    main()
