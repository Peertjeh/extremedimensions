"""Data for learning items by copying (behavior cloning, train/bc.py): decisions with what the teacher would do, the
scripted bot with latch=True (pvp_sim2/scripted.py: it eats, shoots, blocks and throws pearls and wind charges).

Three of four steps in arenas with items (loot and the Bed Wars shop with extra items, Build UHC, the bow duel, the
mace), one in arenas of every mode (states for the term that keeps the rest of the model as it was):
  * slot 0 is played by a trained model (the master by default, sampling among the allowed choices) and labelled with
    what the teacher would do there: the states the model reaches itself;
  * slots 1 and 2 are the teacher itself (skills 0.5..1): its own states and actions.
Kept: the decisions in which the fighter carries something to use, and a quarter of the others.
Per decision: the observation (float16), the teacher's action indices, the action mask (bit-packed, from the float32
observation), whether the teacher aims a bow or a throw (its pitch is copied only then) and the source (0: teacher,
1: model).

    python tools/make_bc_data.py --model runs/v2/ckpt_040000032.zip --out bc.npz [--rows 1000000] [--procs 4]
"""
import argparse
import multiprocessing as mp
import os
import sys
import time

import numpy as np

HERE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, HERE)

# the kits in which items come up, and how often
FAMILIES = {"loot": 2.0, "bedwars": 1.5, "build_uhc": 1.0, "bow": 1.0, "mace": 1.0, "lms": 0.5, "ffa": 0.3}
AIMED = (1, 4, 5)  # bow, pearl, wind charge: the teacher's pitch is copied when it holds one of these to use it


def chunk(args):
    model_path, rows, seed, n = args
    import torch
    from pvp_sim2 import Batch2, Scenario, Scripted2
    from pvp_sim2.masks import MASKS2
    from pvp_sim2.spec import SPEC2
    from train.policies import Chooser, load
    torch.set_num_threads(1)
    # arenas with items, and arenas of every mode (for the term that keeps the rest near the model)
    batches = [Batch2(n, seed=seed, scenario=Scenario(families=dict(FAMILIES), extra_items=1.0)), Batch2(n, seed=seed + 7)]
    states = [bt.reset() for bt in batches]
    turn = 0
    rng = np.random.default_rng(seed)
    model = Chooser(load(model_path).policy, masks=True, deterministic=False, seed=seed) if model_path else None
    labeller = Scripted2(skill=0.9, seed=seed + 1, latch=True)
    teacher = Scripted2(seed=seed + 2, latch=True)
    hand, use = SPEC2.action_index("hand"), SPEC2.action_index("use")
    hand_at = MASKS2.offsets[hand]
    out = {"obs": [], "act": [], "mask": [], "aim": [], "source": []}
    got = 0
    while got < rows:
        # three steps with items for each step of every mode
        k = 0 if turn % 4 else 1
        turn += 1
        b, obs = batches[k], states[k]
        f = b.w.f
        live = f.alive & f.active
        skill = rng.uniform(0.5, 1.0, size=n)
        a1, a2 = teacher(obs[:, 1], skill), teacher(obs[:, 2], skill)
        label0 = labeller(obs[:, 0])
        a0 = model(obs[:, 0]) if model is not None else label0
        for slot, act, src in ((0, label0, 1 if model is not None else 0), (1, a1, 0), (2, a2, 0)):
            o = obs[:, slot].astype(np.float32)
            m = MASKS2(o)
            carries = m[:, hand_at + 1:hand_at + 6].any(axis=1) | m[:, MASKS2.offsets[use] + 1]
            # a quarter of the decisions without items too: there the copying keeps the weapon and the rest near the model
            keep = live[:, slot] & (carries | (rng.random(n) < 0.25))
            if not keep.any():
                continue
            act = act[keep].copy()
            mk = m[keep]
            # a label the masks forbid (it cannot happen with the teacher's rules) falls back to the weapon / no use
            for h in (hand, use):
                ok = mk[np.arange(len(act)), MASKS2.offsets[h] + act[:, h]]
                act[~ok, h] = 0
            out["obs"].append(o[keep].astype(np.float16))
            out["act"].append(act.astype(np.int8))
            out["mask"].append(np.packbits(mk, axis=1))
            held = np.argmax(o[keep][:, [SPEC2.index("hand_" + h) for h in SPEC2.hands]], axis=1)
            out["aim"].append(np.isin(act[:, hand], AIMED) & (held == act[:, hand]))
            out["source"].append(np.full(int(keep.sum()), src, dtype=np.int8))
            got += int(keep.sum())
        states[k], _, _, _ = b.step(np.stack([a0, a1, a2], axis=1))
    return {k: np.concatenate(v) for k, v in out.items()}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--model", default=None, help="the model whose states are labelled (slot 0); none: the teacher plays it")
    ap.add_argument("--out", default="bc.npz")
    ap.add_argument("--rows", type=int, default=1_000_000)
    ap.add_argument("--procs", type=int, default=4)
    ap.add_argument("--arenas", type=int, default=96)
    ap.add_argument("--seed", type=int, default=2026)
    a = ap.parse_args()
    t0 = time.time()
    per = a.rows // a.procs // 4 + 1
    jobs = [(a.model, per, a.seed + 1000 * i, a.arenas) for i in range(a.procs * 4)]
    parts = []
    with mp.get_context("spawn").Pool(a.procs) as pool:
        for part in pool.imap_unordered(chunk, jobs):
            parts.append(part)
            print("%d / %d rows, %.0f s" % (sum(len(p["act"]) for p in parts), per * len(jobs), time.time() - t0), flush=True)
    data = {k: np.concatenate([p[k] for p in parts]) for k in parts[0]}
    np.savez_compressed(a.out, **data, mask_size=np.array(38))
    from pvp_sim2.spec import SPEC2
    hand = data["act"][:, SPEC2.action_index("hand")]
    use = data["act"][:, SPEC2.action_index("use")]
    print("saved %s: %d rows (%.0f%% from the model's states), hands %s, use %.1f%%, aiming %.1f%%" % (
        a.out, len(hand), 100 * (data["source"] == 1).mean(), np.bincount(hand, minlength=6).tolist(), 100 * use.mean(),
        100 * data["aim"].mean()))


if __name__ == "__main__":
    main()
