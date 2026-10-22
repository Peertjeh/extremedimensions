"""Elo ratings from fight results (no PyTorch needed, so it is tested with the simulation)."""
import numpy as np


def elo(names, results, anchor="scripted_0.5", iters=3000):
    """Bradley-Terry ratings (Elo scale) from {(a, b): (wins_a, losses_a, draws)}."""
    idx = {n: i for i, n in enumerate(names)}
    r = np.zeros(len(names))
    for _ in range(iters):
        grad = np.zeros(len(names))
        for (a, b), (w, l, d) in results.items():
            ia, ib = idx[a], idx[b]
            n = w + l + d
            if n == 0:
                continue
            p = 1.0 / (1.0 + 10 ** ((r[ib] - r[ia]) / 400.0))
            score = (w + 0.5 * d) / n
            g = (score - p) * n
            grad[ia] += g
            grad[ib] -= g
        r += 2.0 * grad / max(1, max(sum(v) for v in results.values()))
        r -= r[idx[anchor]] - 1000.0
    return {n: float(r[idx[n]]) for n in names}
