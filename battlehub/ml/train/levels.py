"""ML phase 3E: the difficulty levels as the mod plays them, and how strong they are against people.

The league of earlier phases rated the bare checkpoints, choosing their best action. In the mod a level is more than
its checkpoint: it perceives its target a few ticks late (extra_delay), its aim wobbles (aim_noise), it turns no more
than so far per decision (turn_cap) and the lower ones sample their choices instead of taking the best (sample). This
plays the levels with all of that, in the simulation (Batch2's knobs per slot), and rates them; and it reads the fight
logs for how the levels did against people.

    python train/levels.py rate --difficulties ../src/main/resources/assets/battlehub/ml/difficulties_v3.json \
        --ckpt beginner=faseC/levels/beginner/ckpt_006000192.zip ... --out levels_e [--episodes 12] [--procs 4]
    python train/levels.py people data/export-*.zip [--json people.json]
    python train/levels.py scale --league levels_e/levels.json --difficulties ../src/main/.../difficulties_v3.json data/export-*.zip

rate writes <out>/levels.json: every pair's result and hits, the ratings (Elo, the scripted bot of skill 0.5 at 1000),
and how a share of the hits turns into a share of the fights (the slope k of logit(fight score) on logit(hit share)).
A level can be tried with other knobs: --try "advanced=ckpt.zip,delay=1,noise=0.6,cap=45,sample=0" (any name; it is rated
with the others).

people prints, per mode family and level, the people's share of the melee hits between them and the bots, the fights and
who won, from fights of a bot version still current for that family (the learned fight since 1.41, the mace since 1.46).

scale writes each level's rating on the league's scale into the difficulties file ("rating", what the mod's adaptive
level uses), places the people of the logs on it (by their share of the hits on the levels they fought, read against the
other levels' shares on those levels in the league), and puts the scripted mace bot's levels on the same scale from
the people's share of the hits on them ("rating_mace").
"""
import argparse
import itertools
import json
import math
import multiprocessing as mp
import os
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from pvp_sim2 import Batch2, Scripted2  # noqa: E402
from pvp_sim2.spec import SPEC3  # noqa: E402
from train.eval2 import K, per_slot  # noqa: E402
from train.ratings import elo  # noqa: E402

SPEC = SPEC3
LEVELS = ["beginner", "casual", "intermediate", "advanced", "expert", "master"]

# mode -> family whose bots fight alike (the learned fight in all melee modes, the scripted mace bot)
FAMILY = {"mace": "mace"}
# the first mod version whose bots each family's logs say something about
SINCE = {"learned": (1, 41, 0), "mace": (1, 46, 0)}
# the 1v1 scenarios of the league (train/league2.MIX; not imported, it needs stable-baselines3) where the mod lets the
# model fight (bots.learnedModes: no bow, and the mace bot is scripted)
MIX = [("walled", "sword"), ("walled", "axe"), ("walled", "bow"), ("bridge", "bridge"), ("islands", "loot"), ("town", "loot"),
       ("platform", "sumo"), ("islands", "bedwars"), ("hills", "build_uhc"), ("walled", "mace")]
MIX_E = [m for m in MIX if m[1] not in ("bow", "mace")]


# ------------------------------------------------------------------ the levels in the simulation

def entry(name, path, delay=0, noise=0.0, cap=60.0, sample=False, hands=None):
    """A level as the mod plays it: its checkpoint and its knobs. hands: the hands it may choose besides the weapon
    (None all, [] only the weapon, as a model without an "items" list in the mod)."""
    return {"name": name, "path": path, "delay": int(delay), "noise": float(noise), "cap": float(cap), "sample": bool(sample),
            "hands": hands}


def from_difficulties(diff, ckpts):
    """The levels of a difficulties_v3.json with their checkpoints ({level: path})."""
    out = []
    for level in LEVELS:
        if level not in diff or level not in ckpts:
            continue
        d = diff[level]
        hands = d["items"].split(",") if d.get("items") else []
        out.append(entry(level, ckpts[level], d["extra_delay"], d["aim_noise"], d["turn_cap"], d["sample"], hands))
    return out


def parse_try(text):
    """'name=path,delay=1,noise=0.6,cap=45,sample=0[,hands=food;gapple]' -> an entry."""
    head, *rest = text.split(",")
    name, path = head.split("=", 1)
    kw = dict(r.split("=", 1) for r in rest)
    hands = None
    if "hands" in kw:
        hands = [h for h in kw["hands"].split(";") if h]
    return entry(name, path, int(kw.get("delay", 0)), float(kw.get("noise", 0)), float(kw.get("cap", 60)),
                 kw.get("sample", "0") in ("1", "true"), hands if "hands" in kw else [])


def knobs(e):
    return {"delay": e["delay"], "noise": e["noise"], "cap": e["cap"]}


def player(e, seed=0):
    """obs -> actions for an entry (made in the process that plays it)."""
    if e["path"] is None:
        return Scripted2(skill=float(e["name"].split("_")[1]), seed=7 + seed)
    import torch
    from train.policies import Chooser, load
    torch.set_num_threads(1)
    return Chooser(load(e["path"]).policy, masks=True, deterministic=not e["sample"], seed=seed, hands=e["hands"])


def scripted(skill):
    return {"name": "scripted_%s" % skill, "path": None, "delay": 0, "noise": 0.0, "cap": 60.0, "sample": False, "hands": None}


def fight(a, b, terrain, family, episodes, n=32, seed=0):
    """a against b one on one, each in slot 0 for half the episodes, each with its own knobs: (wins, losses, draws) of a,
    and the hits each landed over the whole run."""
    res = np.zeros(3, dtype=np.int64)
    hits = np.zeros(2)
    for side in (0, 1):
        first, second = (a, b) if side == 0 else (b, a)
        kn = {0: knobs(first)}
        kn.update({i: knobs(second) for i in range(1, K)})
        bt = Batch2(n, k=K, seed=seed * 2 + side, spec=SPEC, knobs=kn,
                    fixed={"terrain": terrain, "family": family, "count": 2, "mode": "1v1"})
        p1, p2 = player(first, seed), player(second, seed + 1)
        players = [p1] + [per_slot(p2, i) for i in range(1, K)]
        obs = bt.reset()
        got = np.zeros(3, dtype=np.int64)
        landed = np.zeros(2)
        while got.sum() < episodes // 2:
            obs, r, d, info = bt.step(np.stack([players[i](obs[:, i]) for i in range(K)], axis=1))
            landed += info["landed"][:, :2].sum(axis=0)
            for w in info["winner"][d]:
                got[0 if w == 0 else 1 if w == 1 else 2] += 1
        if side == 0:
            res += got
            hits += landed
        else:
            res += got[[1, 0, 2]]
            hits += landed[::-1]
    return res, hits


def _pair(job):
    a, b, episodes, seed = job
    res, hits = np.zeros(3, dtype=np.int64), np.zeros(2)
    for i, (terrain, family) in enumerate(MIX_E):
        r, h = fight(a, b, terrain, family, episodes, seed=seed * 31 + i)
        res += r
        hits += h
    return a["name"], b["name"], res, hits


def slope(pairs):
    """How a share of the hits turns into a share of the fights: k in logit(score) = k * logit(hit share), fitted on the
    pairs (weighted by their fights), with pairs that won or lost everything left out."""
    num = den = 0.0
    for p in pairs:
        w, l, d = p["result"]
        n = w + l + d
        h = p["hits"]
        if n == 0 or sum(h) == 0:
            continue
        score = (w + 0.5 * d) / n
        share = h[0] / sum(h)
        if not (0.02 < score < 0.98) or not (0.02 < share < 0.98):
            continue
        x, y = logit(share), logit(score)
        num += n * x * y
        den += n * x * x
    return num / den if den > 0 else float("nan")


def logit(p):
    return math.log(p / (1 - p))


def elo_gap(share, k):
    """The fight-Elo difference that a share of the hits stands for (positive: the first is stronger)."""
    return k * 400 / math.log(10) * logit(share)


def rate(entries, episodes, procs, seed=5):
    jobs = [(a, b, episodes, seed + i) for i, (a, b) in enumerate(itertools.combinations(entries, 2))]
    pairs = []
    with mp.get_context("spawn").Pool(procs) as pool:
        for x, y, res, hits in pool.imap_unordered(_pair, jobs):
            pairs.append({"a": x, "b": y, "result": [int(v) for v in res], "hits": [float(v) for v in hits]})
            print("%-14s vs %-14s %s  hits %d:%d" % (x, y, pairs[-1]["result"], hits[0], hits[1]), flush=True)
    names = [e["name"] for e in entries]
    ratings = elo(names, {(p["a"], p["b"]): tuple(p["result"]) for p in pairs})
    return {"ratings": ratings, "pairs": pairs, "slope": slope(pairs), "entries": entries, "mix": MIX_E}


# ------------------------------------------------------------------ the levels against people

def version(text):
    parts = []
    for p in str(text).split(".")[:3]:
        digits = "".join(ch for ch in p if ch.isdigit())
        parts.append(int(digits) if digits else 0)
    while len(parts) < 3:
        parts.append(0)
    return tuple(parts)


def family_of(mode):
    return FAMILY.get(mode, "learned")


def people_hits(fights):
    """Per (family, level): the melee hits of people on bots and of bots on people, per fight, from fights of a bot
    version still current for that family; and the fights that ended with a winner among them."""
    out = {}
    for f in fights:
        fam = family_of(f.header["mode"])
        if version(f.header.get("mod", "0")) < SINCE[fam]:
            continue
        fs = f.header["fighters"]
        people = {x["i"] for x in fs if x["kind"] == "human"}
        if not people:
            continue
        per = {}
        for h in f.hits:
            if h.get("kind", "melee") != "melee":
                continue
            a, v = h["hit"]
            if (a in people) == (v in people):
                continue
            bot = v if a in people else a
            level = fs[bot].get("level")
            c = per.setdefault(level, [0, 0])
            c[0 if a in people else 1] += 1
        for level, c in per.items():
            out.setdefault((fam, level), []).append({"fight": f.name, "people": c[0], "bots": c[1]})
    return out


def share_interval(rows, rng, n=2000):
    """The people's share of the hits over these fights, and a 90% interval (bootstrap over the fights)."""
    p = np.array([r["people"] for r in rows], dtype=np.float64)
    b = np.array([r["bots"] for r in rows], dtype=np.float64)
    share = p.sum() / max(1.0, p.sum() + b.sum())
    if len(rows) < 2:
        return share, (float("nan"), float("nan"))
    idx = rng.integers(0, len(rows), size=(n, len(rows)))
    s = p[idx].sum(axis=1) / np.maximum(1.0, p[idx].sum(axis=1) + b[idx].sum(axis=1))
    return share, (float(np.quantile(s, 0.05)), float(np.quantile(s, 0.95)))


def people_report(fights, seed=1):
    rng = np.random.default_rng(seed)
    rows = []
    for (fam, level), per in sorted(people_hits(fights).items(), key=lambda kv: (kv[0][0], LEVELS.index(kv[0][1]) if kv[0][1] in LEVELS else 9)):
        share, (lo, hi) = share_interval(per, rng)
        rows.append({"family": fam, "level": level, "fights": len(per), "people_hits": int(sum(r["people"] for r in per)),
                     "bot_hits": int(sum(r["bots"] for r in per)), "share": share, "low": lo, "high": hi})
    return rows


def place(league, rows, family="learned"):
    """Where the people of the logs sit on the league's scale: for each level they fought (rows of people_report), the
    other levels' ratings against their share of the hits on that level in the league, read at the people's share;
    the estimates weighted by the hits they rest on. Returns (rating, [(level, share, estimate, hits)])."""
    ratings, pairs = league["ratings"], league["pairs"]
    out = []
    for r in rows:
        if r["family"] != family or r["level"] not in ratings:
            continue
        lv = r["level"]
        pts = []
        for p in pairs:
            if lv not in (p["a"], p["b"]) or p["a"] == p["b"]:
                continue
            other = p["b"] if p["a"] == lv else p["a"]
            h = p["hits"] if p["b"] == lv else p["hits"][::-1]
            if sum(h) > 0:
                pts.append((h[0] / sum(h), ratings[other]))
        if len(pts) < 2:
            continue
        pts.sort()
        xs, ys = zip(*pts)
        est = float(np.interp(r["share"], xs, ys))
        out.append((lv, r["share"], est, r["people_hits"] + r["bot_hits"]))
    if not out:
        return float("nan"), out
    w = np.array([o[3] for o in out], dtype=np.float64)
    return float(np.dot(w, [o[2] for o in out]) / w.sum()), out


def mace_scale(you, rows, k):
    """The scripted mace bot's levels on the same scale: each level people fought sits where its share of the hits
    puts it below or above them (the league's slope k), the others spread evenly between and beyond (by level)."""
    known = {}
    for r in rows:
        if r["family"] == "mace" and r["level"] in LEVELS:
            known[LEVELS.index(r["level"])] = you - elo_gap(r["share"], k)
    if len(known) < 2:
        return None
    idx = sorted(known)
    a, b = idx[0], idx[-1]
    step = (known[b] - known[a]) / (b - a)
    return [known[i] if i in known else known[a] + (i - a) * step for i in range(len(LEVELS))]


def scale(league_path, export_paths, difficulties_path):
    from train.fights import read
    with open(league_path) as f:
        league = json.load(f)
    rows = people_report([x for path in export_paths for x in read(path)])
    you, parts = place(league, rows)
    for lv, share, est, n in parts:
        print("people against %-12s share %.2f (%d hits): %.0f" % (lv, share, n, est))
    print("the people of the logs on the league's scale: %.0f" % you)
    mace = mace_scale(you, rows, league["slope"])
    with open(difficulties_path) as f:
        diff = json.load(f)
    for i, level in enumerate(LEVELS):
        if level not in diff or level not in league["ratings"]:
            continue
        diff[level]["rating"] = int(round(league["ratings"][level] / 10.0) * 10)
        if mace:
            diff[level]["rating_mace"] = int(round(mace[i] / 10.0) * 10)
        print("%-12s rating %5d  mace %s" % (level, diff[level]["rating"], diff[level].get("rating_mace")))
    with open(difficulties_path, "w") as f:
        json.dump(diff, f, indent=1)
    return you, mace


def main():
    ap = argparse.ArgumentParser()
    sub = ap.add_subparsers(dest="cmd", required=True)
    r = sub.add_parser("rate")
    r.add_argument("--difficulties", required=True)
    r.add_argument("--ckpt", nargs="*", default=[], help="level=path of each level's checkpoint")
    r.add_argument("--try", dest="tries", nargs="*", default=[], help="more entries: name=path,delay=..,noise=..,cap=..,sample=..")
    r.add_argument("--only", default=None, help="comma list of the difficulties' levels to rate (default all)")
    r.add_argument("--scripted", default="0.5,1.0", help="scripted bots to rate with them (0.5 is the anchor)")
    r.add_argument("--episodes", type=int, default=12, help="per scenario and pair")
    r.add_argument("--procs", type=int, default=4)
    r.add_argument("--out", default="levels_e")
    sc = sub.add_parser("scale")
    sc.add_argument("--league", required=True, help="levels.json of the final rate")
    sc.add_argument("--difficulties", required=True, help="difficulties_v3.json to write the ratings into")
    sc.add_argument("paths", nargs="+", help="the fight logs")
    p = sub.add_parser("people")
    p.add_argument("paths", nargs="+")
    p.add_argument("--json", default=None)
    a = ap.parse_args()
    if a.cmd == "scale":
        scale(a.league, a.paths, a.difficulties)
        return
    if a.cmd == "people":
        from train.fights import read
        fights = [f for path in a.paths for f in read(path)]
        rows = people_report(fights)
        for x in rows:
            print("%-8s %-13s fights %2d  hits people %4d bots %4d  share %.2f (90%%: %.2f-%.2f)" % (
                x["family"], x["level"], x["fights"], x["people_hits"], x["bot_hits"], x["share"], x["low"], x["high"]))
        if a.json:
            with open(a.json, "w") as f:
                json.dump(rows, f, indent=1)
        return
    with open(a.difficulties) as f:
        diff = json.load(f)
    ckpts = dict(c.split("=", 1) for c in a.ckpt)
    entries = from_difficulties(diff, ckpts)
    if a.only:
        keep = set(a.only.split(","))
        entries = [e for e in entries if e["name"] in keep]
    entries += [parse_try(t) for t in a.tries]
    entries += [scripted(s) for s in a.scripted.split(",") if s]
    os.makedirs(a.out, exist_ok=True)
    out = rate(entries, a.episodes, a.procs)
    for n, v in sorted(out["ratings"].items(), key=lambda kv: -kv[1]):
        print("%-16s %7.0f" % (n, v))
    print("hits -> fights: k = %.2f" % out["slope"])
    with open(os.path.join(a.out, "levels.json"), "w") as f:
        json.dump(out, f, indent=1)


if __name__ == "__main__":
    main()
