"""Draws one simulated fight (top view, health, distance) to a PNG, to see what the simulation does.

    python tools/show_fight.py out.png [skill0] [skill1] [seed]
"""
import os
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
import matplotlib  # noqa: E402

matplotlib.use("Agg")
import matplotlib.pyplot as plt  # noqa: E402

from pvp_sim import PvpBatch, ScriptedPolicy  # noqa: E402


def main():
    out = sys.argv[1] if len(sys.argv) > 1 else "fight.png"
    s0 = float(sys.argv[2]) if len(sys.argv) > 2 else 0.8
    s1 = float(sys.argv[3]) if len(sys.argv) > 3 else 0.5
    seed = int(sys.argv[4]) if len(sys.argv) > 4 else 11
    b = PvpBatch(1, seed=seed, auto_reset=False)
    obs = b.reset()
    p0, p1 = ScriptedPolicy(s0, seed=seed + 1), ScriptedPolicy(s1, seed=seed + 2)
    pos, hp, hits, yaw = [], [], [], []
    while True:
        obs, r, d, info = b.step(np.stack([p0(obs[:, 0]), p1(obs[:, 1])], axis=1))
        f = b.ph.f
        pos.append((f.x[0].copy(), f.z[0].copy()))
        hp.append(f.health[0].copy())
        yaw.append(f.yaw[0].copy())
        hits.append(info["landed"][0].copy())
        if d[0]:
            break
    pos = np.array(pos)
    hp = np.array(hp)
    hits = np.array(hits)
    t = np.arange(len(hp)) * b.spec.decision_ticks / 20.0
    winner = info["winner"][0]
    fig, ax = plt.subplots(1, 3, figsize=(15, 4.8))
    half = b.ph.half[0]
    ax[0].add_patch(plt.Rectangle((-half, -half), 2 * half, 2 * half, fill=False, lw=2, color="#555"))
    colours = ["#d62728", "#1f77b4"]
    names = ["bot A (skill %.1f)" % s0, "bot B (skill %.1f)" % s1]
    for s in (0, 1):
        ax[0].plot(pos[:, 0, s], pos[:, 1, s], color=colours[s], lw=1.2, label=names[s])
        ax[0].scatter(pos[0, 0, s], pos[0, 1, s], color=colours[s], marker="o", s=40)
        k = np.nonzero(hits[:, s] > 0)[0]
        ax[0].scatter(pos[k, 0, s], pos[k, 1, s], color=colours[s], marker="x", s=30)
        ax[1].plot(t, hp[:, s], color=colours[s], label=names[s])
    ax[0].set_aspect("equal")
    ax[0].set_title("top view: paths (o start, x landed hit)")
    ax[0].legend(loc="upper right", fontsize=8)
    ax[1].set_title("health (iron armor, diamond sword)")
    ax[1].set_xlabel("seconds")
    ax[1].set_ylim(0, 21)
    ax[1].legend(fontsize=8)
    dist = np.hypot(pos[:, 0, 0] - pos[:, 0, 1], pos[:, 1, 0] - pos[:, 1, 1])
    ax[2].plot(t, dist, color="#444")
    ax[2].axhline(3.0, color="#999", ls="--", lw=1)
    ax[2].text(t[-1] * 0.02, 3.1, "reach 3", color="#777", fontsize=8)
    ax[2].set_title("distance between them")
    ax[2].set_xlabel("seconds")
    fig.suptitle("Simulated sword duel: %s wins after %.1f s, %d / %d hits landed" % (
        "nobody" if winner < 0 else names[winner], t[-1], hits[:, 0].sum(), hits[:, 1].sum()))
    fig.tight_layout()
    fig.savefig(out, dpi=110)
    print("saved", out)


if __name__ == "__main__":
    main()
