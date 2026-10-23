"""Evaluates a v2 or v3 checkpoint (or a policy) on a fixed set of scenarios against the scripted bot, and against a
dummy on a bridge; appends one JSON line to --out.

    python train/eval2.py runs/v3/ckpt_010000000.zip --out runs/v3/eval.jsonl [--episodes 48] [--no-masks] [--items-only]
    python train/eval2.py runs/v3/ckpt_010000000.zip --team-only   # only the team block
    python train/eval2.py scripted --items-only        # the scripted bot's own item use, for reference

Every fight is played in the v3 simulation (four slots); a v2 model reads the first 118 values (spec v3 keeps v2's in
their place), so v2 and v3 models are measured alike. The model chooses with the action masks (train/policies.py), as
in the mod; --no-masks chooses as before them.

The team block (phase 3C): the town 1 against 2 over 192 fights, and two against two (two of the model against two
scripted bots, with the sword in the arena and in Bed Wars in teams; and the model with a scripted team mate against
two scripted bots, which shows whether the model makes its team better than a scripted bot would: that team wins
about half). A team's result counts: it wins when the other team is dead, also when the model died first.
"""
import argparse
import copy
import json
import os
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from pvp_sim2 import Batch2, Dummy2, Scripted2  # noqa: E402
from pvp_sim2.spec import SPEC3  # noqa: E402

SPEC = SPEC3
K = 4

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


def play(policy, opponent, terrain, family, count, mode, episodes=48, n=32, seed=0, max_ticks=1600, mate=None):
    """Win, loss, draw of `policy`'s team (slot 0, and its team mates played by `mate`, by default itself) against
    `opponent` in the other slots."""
    from pvp_sim2.env import TEAMS
    b = Batch2(n, k=K, seed=seed, max_ticks=max_ticks, spec=SPEC, fixed={"terrain": terrain, "family": family, "count": count, "mode": mode})
    obs = b.reset()
    teams = TEAMS[mode] + [1] * (K - count)
    players = [policy] + [per_slot(mate or policy, i) if teams[i] == 0 else per_slot(opponent, i) for i in range(1, K)]
    res = np.zeros(3, dtype=np.int64)
    while res.sum() < episodes:
        acts = np.stack([players[i](obs[:, i]) for i in range(K)], axis=1)
        obs, r, d, info = b.step(acts)
        for w in info["winner"][d]:
            res[0 if w == 0 else 1 if w == 1 else 2] += 1
    return res


def per_slot(player, slot):
    """A bot that keeps a state per row (the scripted bot's strafe and shield) gets its own copy for each slot: one
    copy for several slots would see its state changed by every call (with four slots it then changed direction three
    times as often, which made the scripted bots harder to beat than with three)."""
    if not hasattr(player, "rng"):
        return player
    p = copy.deepcopy(player)
    p.rng = np.random.default_rng(int(player.rng.integers(1 << 30)) + slot)
    return p


# the team block: (map, kit, fighters, mode, the team mate, fights)
TEAM = [("town", "loot", 3, "1v2", None, 192), ("walled", "sword", 4, "2v2", "model", 96),
        ("islands", "bedwars", 4, "2v2", "model", 96), ("walled", "sword", 4, "2v2", "scripted", 96)]


def team(policy, seed=0):
    """The team block: per scenario win, loss, draw of the model's team, in parts of 48 fights with their own seeds."""
    out = {}
    for k, (terrain, family, count, mode, mate, episodes) in enumerate(TEAM):
        res = np.zeros(3, dtype=np.int64)
        for part in range(episodes // 48):
            s = seed + 1000 * part + 50 + k
            m = Scripted2(skill=0.8, seed=s + 500) if mate == "scripted" else None
            res += play(policy, Scripted2(skill=0.8, seed=s), terrain, family, count, mode, 48, seed=s, mate=m)
        key = "%s/%s/%s%s" % (terrain, family, mode, "" if mate in (None, "model") else " with " + mate)
        out[key] = [int(v) for v in res]
    out["share"] = {k: round(v[0] / max(1, sum(v)), 3) for k, v in out.items()}
    return out


# where item use is measured, against the scripted bot (map, kit)
ITEM_SCENARIOS = [("walled", "bow"), ("hills", "build_uhc"), ("walled", "sword"), ("walled", "axe"), ("walled", "mace"),
                  ("islands", "loot"), ("islands", "bedwars")]
# loot and the shop give pearls and wind charges to few fighters: more fights there
ITEM_EPISODES = {"islands/loot": 192, "islands/bedwars": 192}
ITEMS = ("bow", "eat", "pearl", "wind")
FAR_BOW = 9.0          # blocks: a target this far that I see is one for the bow ...
FAR_BOW_DECISIONS = 20  # ... for at least this long (2 s) in a fight
FAR_PEARL = 18.0
LOW = 0.45             # health share under which eating counts
WIND_HIT = 20          # decisions (2 s: a mace charges for 1.7 s) after a wind charge in which a hit makes it useful
PEARL_CLOSER = 3.0     # blocks a pearl must bring me closer to the target to be useful


def items(policy, terrain, family, episodes=64, n=32, seed=0, opponent=None):
    """How `policy` (slot 0) uses its items against the scripted bot, per fight. A fight counts for an item when the
    item made sense in it: the bow when it had a bow with arrows and saw the target 9 blocks or farther for 2 s; eating
    when it was under 45% health with food it could eat or a golden apple; a pearl when it had one and saw the target
    farther than 18 blocks or was over the void; wind charges when it had them. Per item: the fights in which it was
    used at least once of those that counted, uses per 100 decisions, and how much of it helped (arrows that hit;
    pearls that brought it 3 blocks closer to the target or out of the void onto ground; wind charges followed by a
    hit within two seconds; eating has no such measure). Plus the share of decisions blocking with the shield."""
    sp = SPEC
    g = lambda o, name: o[:, sp.index(name)] * sp.obs_scale[sp.index(name)]
    b = Batch2(n, k=K, seed=seed, spec=SPEC, fixed={"terrain": terrain, "family": family, "count": 2})
    obs = b.reset()
    opp = opponent or Scripted2(skill=0.8, seed=seed + 1)
    opps = [per_slot(opp, i) for i in range(1, K)]
    f = b.w.f
    used = {k: np.zeros(n, dtype=bool) for k in ITEMS}
    counts = {k: np.zeros(n, dtype=bool) for k in ITEMS}
    far = np.zeros(n, dtype=np.int64)
    pearl_from = np.full(n, np.nan)        # distance to the target when the last pearl was thrown
    pearl_void = np.zeros(n, dtype=bool)   # ... and whether it was over the void then
    wind_wait = np.zeros(n, dtype=np.int64)
    fights = {k: [0, 0] for k in ITEMS}
    total = {k: 0 for k in ITEMS}
    good = {k: 0 for k in ITEMS}
    decisions = blocking = done_eps = 0
    while done_eps < episodes:
        o = obs[:, 0]
        dist, seen = g(o, "opp_dist"), g(o, "opp_visible") > 0.5
        void = (g(o, "self_below") >= 8.0) & (g(o, "self_ground") < 0.5)
        far = np.where((g(o, "self_arrows") > 0) & seen & (dist >= FAR_BOW), far + 1, far)
        counts["bow"] |= far >= FAR_BOW_DECISIONS
        edible = ((g(o, "self_food_items") > 0) & (g(o, "self_food") < 20)) | (g(o, "self_gapples") > 0)
        counts["eat"] |= (g(o, "self_health") < LOW) & edible
        counts["pearl"] |= (g(o, "self_pearls") > 0) & ((seen & (dist > FAR_PEARL)) | void)
        counts["wind"] |= g(o, "self_winds") > 0
        pearls, winds = f.pearls[:, 0].copy(), f.winds[:, 0].copy()
        obs, r, d, info = b.step(np.stack([policy(o)] + [opps[i - 1](obs[:, i]) for i in range(1, K)], axis=1))
        decisions += n
        blocking += int(f.blocking()[:, 0].sum())
        live = ~d
        threw_pearl = live & (f.pearls[:, 0] < pearls)
        threw_wind = live & (f.winds[:, 0] < winds)
        step_use = {"bow": info["fired"][:, 0] > 0, "eat": info["ate"][:, 0] > 0, "pearl": threw_pearl, "wind": threw_wind}
        for k in ITEMS:
            used[k] |= step_use[k] & live
            total[k] += int((step_use[k] & live).sum())
        good["bow"] += int(info["arrow_hit"][live, 0].sum())
        pearl_from = np.where(threw_pearl, dist, pearl_from)
        pearl_void = np.where(threw_pearl, void, pearl_void)
        landed = live & (info["pearled"][:, 0] > 0) & ~np.isnan(pearl_from)
        if landed.any():
            o2 = obs[:, 0]
            closer = g(o2, "opp_dist") <= pearl_from - PEARL_CLOSER
            safe = pearl_void & (g(o2, "self_below") < 8.0)
            good["pearl"] += int((landed & (closer | safe)).sum())
            pearl_from = np.where(landed, np.nan, pearl_from)
        hit = info["landed"][:, 0] > 0
        good["wind"] += int((live & hit & (wind_wait > 0)).sum())
        wind_wait = np.where(live & hit, 0, np.where(threw_wind, WIND_HIT, np.maximum(0, wind_wait - 1)))
        for a in np.nonzero(d)[0]:
            done_eps += 1
            for k in ITEMS:
                if counts[k][a]:
                    fights[k][1] += 1
                    fights[k][0] += int(used[k][a])
                used[k][a] = counts[k][a] = False
            far[a], pearl_from[a], pearl_void[a], wind_wait[a] = 0, np.nan, False, 0
    out = {}
    for k in ITEMS:
        row = {"fights": fights[k], "per100": round(100.0 * total[k] / decisions, 2), "uses": total[k]}
        if total[k] and k != "eat":
            row["useful"] = round(good[k] / total[k], 2)
            row["good"] = good[k]
        out[k] = row
    out["blocking"] = round(blocking / decisions, 3)
    return out


def item_summary(per_scenario):
    """Over all item scenarios: per item the fights in which it was used / the fights in which it counted, as a share."""
    out = {}
    for k in ITEMS:
        u = sum(v[k]["fights"][0] for v in per_scenario.values())
        c = sum(v[k]["fights"][1] for v in per_scenario.values())
        uses = sum(v[k]["uses"] for v in per_scenario.values())
        good = sum(v[k].get("good", 0) for v in per_scenario.values())
        out[k] = {"used": u, "counted": c, "share": round(u / c, 3) if c else None, "uses": uses,
                  "useful": round(good / uses, 2) if uses and k != "eat" else None}
    return out


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
    out["items"] = {"%s/%s" % (t, f): items(policy, t, f, ITEM_EPISODES.get("%s/%s" % (t, f), 64), seed=seed) for t, f in ITEM_SCENARIOS}
    out["items_total"] = item_summary(out["items"])
    out["team"] = team(policy, seed)
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("checkpoint")
    ap.add_argument("--out", default=None)
    ap.add_argument("--episodes", type=int, default=48)
    ap.add_argument("--no-masks", action="store_true", help="choose as before the action masks")
    ap.add_argument("--items-only", action="store_true", help="only how the items are used")
    ap.add_argument("--hands", default=None, help="the hands the model may choose besides the weapon, as in the mod (e.g. food,gapple,pearl,wind)")
    ap.add_argument("--team-only", action="store_true", help="only the team block")
    a = ap.parse_args()
    if a.checkpoint.startswith("scripted"):
        # the scripted bot itself as the player, for reference ("scripted" or "scripted_latch")
        policy = Scripted2(skill=0.8, seed=5, latch=a.checkpoint.endswith("latch"))
        row = {"checkpoint": a.checkpoint, "steps": 0}
    else:
        import torch
        from train.policies import Chooser, load
        torch.set_num_threads(1)
        model = load(a.checkpoint)
        hands = a.hands.split(",") if a.hands else None
        policy = Chooser(model.policy, masks=not a.no_masks, hands=hands)
        row = {"checkpoint": os.path.basename(a.checkpoint), "steps": int(model.num_timesteps), "masks": not a.no_masks, "hands": a.hands}
    if a.team_only:
        row["team"] = team(policy)
    elif a.items_only:
        row["items"] = {"%s/%s" % (t, f): items(policy, t, f, ITEM_EPISODES.get("%s/%s" % (t, f), 64)) for t, f in ITEM_SCENARIOS}
        row["items_total"] = item_summary(row["items"])
    else:
        row.update(evaluate(policy, a.episodes))
    line = json.dumps(row)
    print(line)
    if a.out:
        with open(a.out, "a") as f:
            f.write(line + "\n")


if __name__ == "__main__":
    main()
