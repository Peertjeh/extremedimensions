"""The v2 league: checkpoints of a v2 run and scripted bots fight each other 1v1 over a mix of the modes' scenarios,
Elo ratings from the results, the checkpoints for the six difficulty levels picked by rating, exported to ONNX. It
also checks the rule of phase 2: on flat ground with the sword kit v2 must be at least as good as v1's best model
(played through an adapter that gives it v1's observation in the v2 simulation).

    python train/league2.py runs/v2 --out league_v2 [--every 3] [--episodes 12] [--v1 league_v1b/combat_v1_master... .zip]

Writes <out>/league.json, <out>/difficulties_v2.json and <out>/combat_v2_<level>.onnx (+ .check.json).

Every model chooses with the action masks (train/policies.py). Several runs can be rated together, and single
checkpoints added (--include); the checkpoints of --items-from are exported with the "items" mark. --levels picks only
those levels anew and keeps the others of --keep (a difficulties_v2.json), with their rating in this league:

    python train/league2.py runs/v3a --include runs/v2/ckpt_006000000.zip ... --items-from runs/v3a \
        --levels master --keep ../src/main/resources/assets/battlehub/ml/difficulties_v2.json --out league_v3a
    python train/league2.py --versus runs/v3a/ckpt_020000000.zip runs/v2/ckpt_040000032.zip --episodes 120

--versus plays two checkpoints against each other over the mix (episodes per scenario, both sides) and stops. A
checkpoint written as "ckpt.zip#hands=food,gapple,pearl,wind" chooses only those hands besides the weapon, as the mod
plays a model with that "items" list.
"""
import argparse
import glob
import itertools
import json
import multiprocessing as mp
import os
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from pvp_sim2 import Batch2, Random2, Scripted2  # noqa: E402
from pvp_sim2.spec import SPEC2  # noqa: E402
from train.league import EXTRA, LEVELS  # noqa: E402
from train.ratings import elo  # noqa: E402

# 1v1 scenarios the rating is made of (map, kit)
MIX = [("walled", "sword"), ("walled", "axe"), ("walled", "bow"), ("bridge", "bridge"), ("islands", "loot"), ("town", "loot"),
       ("platform", "sumo"), ("islands", "bedwars"), ("hills", "build_uhc"), ("walled", "mace")]


def split_path(path):
    """A player's checkpoint and the hands it may choose besides the weapon: "ckpt.zip#hands=food,gapple" (None: all)."""
    if path and "#hands=" in path:
        path, hands = path.split("#hands=", 1)
        return path, hands.split(",")
    return path, None


def make_player(name, path=None):
    """A callable obs -> actions for a league entry (made in the process that plays it)."""
    path, hands = split_path(path)
    if name.startswith("scripted_"):
        return Scripted2(skill=float(name.split("_")[1]), seed=7)
    if name == "random":
        return Random2(seed=3)
    import torch
    from train.policies import Chooser, load
    torch.set_num_threads(1)
    if name == "v1":
        return V1Adapter(path)
    return Chooser(load(path).policy, masks=True, hands=hands)


class V1Adapter:
    """v1's model in the v2 simulation: v1's observation from v2's (the same fields; v1's edges are v2's drops), and its
    7 heads mapped to v2's (nearest turn and pitch; the weapon in hand, no right button)."""

    def __init__(self, path):
        from stable_baselines3 import PPO
        from pvp_sim.spec import SPEC
        self.model = PPO.load(path, device="cpu")
        self.v1 = SPEC

    def __call__(self, obs2):
        sp1, sp2 = self.v1, SPEC2
        raw2 = obs2 * sp2.obs_scale
        m = obs2.shape[0]
        raw1 = np.zeros((m, sp1.obs_dim))
        for k, name in enumerate(sp1.obs_names):
            src = name.replace("edge_", "drop_") if name.startswith("edge_") else name
            raw1[:, k] = raw2[:, sp2.index(src)]
        o1 = np.clip(raw1 / sp1.obs_scale, -sp1.obs_clip, sp1.obs_clip).astype(np.float32)
        a1 = self.model.predict(o1, deterministic=True)[0]
        out = np.zeros((m, len(sp2.action_nvec)), dtype=np.int64)
        for h, name in enumerate(sp1.action_names):
            vals1 = np.asarray(sp1.action_values[h], dtype=np.float64)[a1[:, h]]
            vals2 = np.asarray(sp2.action_values[sp2.action_index(name)], dtype=np.float64)
            out[:, sp2.action_index(name)] = np.abs(vals1[:, None] - vals2[None, :]).argmin(axis=1)
        out[:, sp2.action_index("hand")] = 0
        out[:, sp2.action_index("use")] = 0
        return out


def duel(args):
    """Wins, losses, draws of x against y over the mix (each side once slot 0 per scenario)."""
    x, px, y, py, episodes, seed = args
    a, b = make_player(x, px), make_player(y, py)
    res = np.zeros(3, dtype=np.int64)
    for k, (terrain, family) in enumerate(MIX):
        for swap in (False, True):
            first, second = (b, a) if swap else (a, b)
            bt = Batch2(16, seed=seed * 100 + k * 2 + swap, fixed={"terrain": terrain, "family": family, "count": 2})
            obs = bt.reset()
            got = np.zeros(3, dtype=np.int64)
            while got.sum() < episodes // 2:
                acts = np.stack([first(obs[:, 0]), second(obs[:, 1]), second(obs[:, 2])], axis=1)
                obs, r, d, info = bt.step(acts)
                for w in info["winner"][d]:
                    got[0 if w == 0 else 1 if w == 1 else 2] += 1
            res += got[[1, 0, 2]] if swap else got
    return x, y, res


def flat(args):
    """x against y with the sword kit in the walled arena only."""
    x, px, y, py, episodes, seed = args
    a, b = make_player(x, px), make_player(y, py)
    res = np.zeros(3, dtype=np.int64)
    for swap in (False, True):
        first, second = (b, a) if swap else (a, b)
        bt = Batch2(32, seed=seed + swap, fixed={"terrain": "walled", "family": "sword", "count": 2})
        obs = bt.reset()
        got = np.zeros(3, dtype=np.int64)
        while got.sum() < episodes // 2:
            obs, r, d, info = bt.step(np.stack([first(obs[:, 0]), second(obs[:, 1]), second(obs[:, 2])], axis=1))
            for w in info["winner"][d]:
                got[0 if w == 0 else 1 if w == 1 else 2] += 1
        res += got[[1, 0, 2]] if swap else got
    return x, y, res


def scenario_duel(args):
    """x against y in one scenario of the mix, each side slot 0 for half the episodes."""
    x, px, y, py, terrain, family, episodes, seed = args
    a, b = make_player(x, px), make_player(y, py)
    res = np.zeros(3, dtype=np.int64)
    for swap in (False, True):
        first, second = (b, a) if swap else (a, b)
        bt = Batch2(32, seed=seed + swap, fixed={"terrain": terrain, "family": family, "count": 2})
        obs = bt.reset()
        got = np.zeros(3, dtype=np.int64)
        while got.sum() < episodes // 2:
            obs, r, d, info = bt.step(np.stack([first(obs[:, 0]), second(obs[:, 1]), second(obs[:, 2])], axis=1))
            for w in info["winner"][d]:
                got[0 if w == 0 else 1 if w == 1 else 2] += 1
        res += got[[1, 0, 2]] if swap else got
    return "%s/%s" % (terrain, family), res


def versus(a_path, b_path, episodes, procs, seed=11):
    """Two checkpoints over the mix: per scenario and in total (wins, losses, draws of the first)."""
    jobs = [("a", a_path, "b", b_path, t, f, episodes, seed + 10 * k) for k, (t, f) in enumerate(MIX)]
    out, total = {}, np.zeros(3, dtype=np.int64)
    with mp.get_context("spawn").Pool(procs) as pool:
        for sc, res in pool.imap_unordered(scenario_duel, jobs):
            out[sc] = [int(v) for v in res]
            total += res
            print("%-22s %s" % (sc, out[sc]), flush=True)
    decided = max(1, total[0] + total[1])
    row = {"a": a_path, "b": b_path, "per_scenario": out, "total": [int(v) for v in total],
           "win_share": round(float(total[0] / decided), 3), "fights": int(total.sum())}
    print(json.dumps(row))
    return row


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("runs", nargs="*")
    ap.add_argument("--include", nargs="*", default=[], help="single checkpoints to rate too")
    ap.add_argument("--items-from", default=None, help="checkpoints of this run learned their items (exported with the mark)")
    ap.add_argument("--item-hands", default="true", help="which hands they learned: true (all) or a list, e.g. food,gapple,pearl,wind")
    ap.add_argument("--levels", default=None, help="comma list: pick only these levels anew (the rest from --keep)")
    ap.add_argument("--keep", default=None, help="difficulties_v2.json with the levels that stay")
    ap.add_argument("--versus", nargs=2, default=None, help="two checkpoints against each other over the mix, then stop")
    ap.add_argument("--out", default="league_v2")
    ap.add_argument("--every", type=int, default=3)
    ap.add_argument("--episodes", type=int, default=12, help="per scenario and pair")
    ap.add_argument("--v1", default=None, help="v1's best checkpoint, for the comparison on flat ground")
    ap.add_argument("--procs", type=int, default=4)
    a = ap.parse_args()
    if a.versus:
        row = versus(a.versus[0], a.versus[1], a.episodes, a.procs)
        if a.out:
            os.makedirs(a.out, exist_ok=True)
            with open(os.path.join(a.out, "versus.jsonl"), "a") as f:
                f.write(json.dumps(row) + "\n")
        return
    os.makedirs(a.out, exist_ok=True)
    players = {}
    for run in a.runs:
        ckpts = sorted(glob.glob(os.path.join(run, "ckpt_*.zip")))
        picked = ckpts[::a.every] + ([ckpts[-1]] if ckpts and ckpts[-1] not in ckpts[::a.every] else [])
        tag = "" if len(a.runs) == 1 else os.path.basename(os.path.normpath(run)) + ":"
        players.update({"%sckpt_%d" % (tag, int(os.path.basename(p)[5:14])): p for p in picked})
    items_dir = os.path.abspath(a.items_from) if a.items_from else None
    if items_dir and a.item_hands != "true":
        for n, p in list(players.items()):
            if os.path.dirname(os.path.abspath(p)) == items_dir:
                players[n] = p + "#hands=" + a.item_hands
    for p in a.include:
        players["%s:ckpt_%d" % (os.path.basename(os.path.dirname(os.path.abspath(p))), int(os.path.basename(p)[5:14]))] = p
    for skill in ("0.3", "0.5", "0.8", "1.0"):
        players["scripted_" + skill] = None
    players["random"] = None
    names = list(players)
    jobs = [(x, players[x], y, players[y], a.episodes, i) for i, (x, y) in enumerate(itertools.combinations(names, 2))]
    results = {}
    with mp.get_context("spawn").Pool(a.procs) as pool:
        for x, y, res in pool.imap_unordered(duel, jobs):
            results[(x, y)] = tuple(int(v) for v in res)
            print("%-16s vs %-16s %s" % (x, y, results[(x, y)]), flush=True)
    ratings = elo(names, results)
    table = sorted(ratings.items(), key=lambda kv: -kv[1])
    for n, v in table:
        print("%-16s %7.0f" % (n, v))
    cks = {n: v for n, v in ratings.items() if players.get(n)}
    best = max(cks, key=cks.get)
    flat_result = None
    if a.v1:
        _, _, res = flat((best, players[best], "v1", a.v1, 200, 5))
        flat_result = {"v2": best, "v1": a.v1, "v2_wins_losses_draws": [int(v) for v in res]}
        print("flat sword duel, %s against v1: %s" % (best, flat_result["v2_wins_losses_draws"]), flush=True)
    low, high = ratings["scripted_0.3"], cks[best]
    picks = {}
    anew = set(a.levels.split(",")) if a.levels else set(LEVELS)
    kept = {}
    if a.keep:
        with open(a.keep) as f:
            kept = json.load(f)
    for k, level in enumerate(LEVELS):
        target = low + (high - low) * (k + 1) / len(LEVELS)
        if level not in anew and level in kept:
            p = dict(kept[level])
            # the kept level's model, with its rating in this league when its checkpoint played
            same = [n for n in cks if n.endswith(":" + p["checkpoint"]) or n == p["checkpoint"]]
            if same:
                p["elo"] = round(cks[same[0]])
            p["target_elo"] = round(target)
            p["kept"] = True
            picks[level] = p
            continue
        pick = min(cks, key=lambda n: abs(cks[n] - target))
        picks[level] = {"checkpoint": pick, "elo": round(cks[pick]), "target_elo": round(target), **EXTRA[level],
                        "model": "combat_v2_%s.onnx" % level}
    from train.export_onnx import export
    for level, p in picks.items():
        if p.get("kept"):
            continue
        path = split_path(players[p["checkpoint"]])[0]
        meta = {"elo": str(p["elo"]), "level": level}
        if items_dir and os.path.dirname(os.path.abspath(path)) == items_dir:
            meta["items"] = a.item_hands
            p["items"] = a.item_hands
        export(path, os.path.join(a.out, p["model"]), name=level, meta=meta, spec=SPEC2)
    with open(os.path.join(a.out, "league.json"), "w") as f:
        json.dump({"ratings": dict(table), "results": {"%s|%s" % k: v for k, v in results.items()}, "flat_vs_v1": flat_result,
                   "mix": MIX}, f, indent=1)
    with open(os.path.join(a.out, "difficulties_v2.json"), "w") as f:
        json.dump(picks, f, indent=1)
    print(json.dumps(picks, indent=1))


if __name__ == "__main__":
    main()
