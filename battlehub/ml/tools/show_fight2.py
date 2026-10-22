"""Draws simulated v2 fights on their maps (top view with the paths, arrows and pearls, and health over time) to a PNG.

    python tools/show_fight2.py out.png [terrain:family:mode ...] [--seed N] [--model checkpoint.zip]

e.g. python tools/show_fight2.py fights.png bridge:bridge:1v1 islands:loot:ffa town:loot:1v2 platform:sumo:1v1
Without a model every fighter is the scripted bot; with one, fighter A (slot 0) is the model.
"""
import os
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
import matplotlib  # noqa: E402

matplotlib.use("Agg")
import matplotlib.pyplot as plt  # noqa: E402

from pvp_sim2 import Batch2, Scripted2  # noqa: E402
from pvp_sim2.physics import ARROW, PEARL, WIND  # noqa: E402

COLORS = ["tab:blue", "tab:red", "tab:green"]


def fight(terrain, family, mode, seed, model=None):
    count = 2 if mode == "1v1" else 3
    b = Batch2(1, seed=seed, auto_reset=False, fixed={"terrain": terrain, "family": family, "count": count, "mode": mode})
    obs = b.reset()
    bots = [Scripted2(skill=0.8, seed=seed + k) for k in range(3)]
    ground = b.w.terrain.ground[0].copy()
    track, hp, shots = [], [], []
    while True:
        acts = []
        for k in range(3):
            if k == 0 and model is not None:
                acts.append(model.predict(obs[:, 0], deterministic=True)[0])
            else:
                acts.append(bots[k](obs[:, k]))
        obs, r, d, info = b.step(np.stack(acts, axis=1))
        f, p = b.w.f, b.w.p
        track.append(np.stack([f.x[0], f.z[0], f.y[0], f.alive[0]], axis=1).copy())
        hp.append((f.health[0] + f.absorption[0]).copy())
        for j in np.nonzero(p.active[0])[0]:
            shots.append((p.kind[0, j], p.x[0, j], p.z[0, j]))
        if d[0]:
            break
    return b, ground, np.array(track), np.array(hp), shots, info, count


def main():
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    out = args[0] if args else "fights.png"
    specs = args[1:] or ["bridge:bridge:1v1", "islands:loot:ffa", "town:loot:1v2", "platform:sumo:1v1"]
    seed = int(sys.argv[sys.argv.index("--seed") + 1]) if "--seed" in sys.argv else 3
    model = None
    if "--model" in sys.argv:
        from stable_baselines3 import PPO
        model = PPO.load(sys.argv[sys.argv.index("--model") + 1], device="cpu")
    fig, axes = plt.subplots(2, len(specs), figsize=(4.6 * len(specs), 8.6), squeeze=False)
    for c, sp in enumerate(specs):
        terrain, family, mode = sp.split(":")
        b, ground, track, hp, shots, info, count = fight(terrain, family, mode, seed + c, model)
        ax = axes[0, c]
        size = ground.shape[0]
        ext = [-size / 2, size / 2, -size / 2, size / 2]
        shown = np.where(np.isnan(ground), -2.0, ground).T
        ax.imshow(shown, origin="lower", extent=ext, cmap="gist_earth", vmin=-2, vmax=7)
        for k in range(count):
            alive = track[:, k, 3] > 0.5
            xs, zs = track[:, k, 0], track[:, k, 1]
            ax.plot(xs[alive], zs[alive], color=COLORS[k], lw=1.4, label="ABC"[k] + (" (model)" if k == 0 and model else ""))
            ax.plot(xs[0], zs[0], "o", color=COLORS[k], ms=6)
            if not alive[-1] or (len(alive) and not alive.all()):
                last = np.nonzero(alive)[0]
                if last.size:
                    ax.plot(xs[last[-1]], zs[last[-1]], "x", color=COLORS[k], ms=10, mew=2.5)
        for kind, x, z in shots:
            ax.plot(x, z, ".", color={ARROW: "k", PEARL: "purple", WIND: "cyan"}[int(kind)], ms=2)
        lo = np.nanmin(track[:, :count, :2][track[:, :count, 3] > 0.5].reshape(-1, 2), axis=0) - 6
        hi = np.nanmax(track[:, :count, :2][track[:, :count, 3] > 0.5].reshape(-1, 2), axis=0) + 6
        ax.set_xlim(lo[0], hi[0])
        ax.set_ylim(lo[1], hi[1])
        ax.set_aspect("equal")
        w = info["winner"][0]
        res = {0: "A wins", 1: "A loses", -1: "time"}[int(w)]
        ax.set_title("%s, %s kit, %s: %s" % (terrain, family, mode, res), fontsize=10)
        ax.legend(fontsize=8, loc="upper right")
        ax2 = axes[1, c]
        t = np.arange(len(hp)) * b.spec.decision_ticks / 20.0
        for k in range(count):
            ax2.plot(t, hp[:, k], color=COLORS[k], label="ABC"[k])
        ax2.set_xlabel("seconds")
        ax2.set_ylabel("health + absorption")
        ax2.set_ylim(0, 25)
        ax2.grid(alpha=0.3)
    fig.suptitle("BattleHub combat simulation v2 (x: died; dots: arrows black, pearls purple, wind charges cyan)")
    fig.tight_layout()
    fig.savefig(out, dpi=80)
    print("wrote", out)


if __name__ == "__main__":
    main()
