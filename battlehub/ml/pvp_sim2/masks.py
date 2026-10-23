"""Action masks (ml/spec/combat_v2_masks.json, combat_v3_masks.json: the same rules): which choices of each action head a fighter may make, from its
observation alone. A masked choice gets minus infinity as its logit, so the hand head no longer picks something the
fighter does not carry (which did nothing, and was therefore a safe choice that taught the model to keep its weapon).

The same rules are read by the mod (core/ml/CombatMasks); tools/make_golden2.py writes the masks of the golden cases so
both sides are checked against each other.
"""
import json
import os

import numpy as np

from .spec import SPEC2, SPEC3

MASKS2_PATH = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "spec", "combat_v2_masks.json")
MASKS3_PATH = os.path.join(os.path.dirname(MASKS2_PATH), "combat_v3_masks.json")
OPS = {">": np.greater, "<": np.less, ">=": np.greater_equal, "<=": np.less_equal}


class Masks:
    """mask(obs) for observations (..., obs_dim): booleans (..., sum of the heads' sizes), the heads one after the other
    in the spec's order (the flat layout MaskablePPO takes)."""

    def __init__(self, spec=SPEC2, path=MASKS2_PATH):
        with open(path, encoding="utf-8") as f:
            d = json.load(f)
        if d["spec_sha256"] != spec.sha256:
            raise ValueError("%s is for spec %s %s, not %s" % (path, d["spec"], d["spec_sha256"][:12], spec.sha256[:12]))
        self.spec = spec
        self.raw = d
        self.offsets = np.concatenate([[0], np.cumsum(spec.action_nvec)])
        self.size = int(self.offsets[-1])
        self.rules = []
        for r in d["rules"]:
            head = spec.action_index(r["head"])
            if not 0 <= r["choice"] < spec.action_nvec[head]:
                raise ValueError("no choice %d in head %s" % (r["choice"], r["head"]))
            alts = [[(spec.index(name), OPS[op], float(v)) for name, op, v in alt] for alt in r["any"]]
            self.rules.append((int(self.offsets[head] + r["choice"]), alts))
        for h in range(len(spec.action_nvec)):
            ruled = {flat for flat, _ in self.rules}
            if all(self.offsets[h] + c in ruled for c in range(spec.action_nvec[h])):
                raise ValueError("head %s has no choice that is always allowed" % spec.action_names[h])

    def __call__(self, obs):
        obs = np.asarray(obs)
        out = np.ones(obs.shape[:-1] + (self.size,), dtype=bool)
        for flat, alts in self.rules:
            ok = np.zeros(obs.shape[:-1], dtype=bool)
            for alt in alts:
                cond = np.ones(obs.shape[:-1], dtype=bool)
                for k, op, v in alt:
                    cond &= op(obs[..., k], v)
                ok |= cond
            out[..., flat] = ok
        return out

    def split(self, mask):
        """The flat mask cut per head: a list of (..., head size)."""
        return np.split(mask, self.offsets[1:-1], axis=-1)

    def apply(self, logits, mask):
        """Logits (..., size) with the choices that are not allowed at minus infinity."""
        return np.where(mask, logits, -np.inf)

    def best(self, logits, mask):
        """The most likely allowed choice per head: (..., heads)."""
        z = self.apply(np.asarray(logits, dtype=np.float64), mask)
        return np.stack([p.argmax(axis=-1) for p in np.split(z, self.offsets[1:-1], axis=-1)], axis=-1)


MASKS2 = Masks()
MASKS3 = Masks(SPEC3, MASKS3_PATH)


def dead_mask(spec=SPEC2):
    """The mask of a dead fighter whose team fights on: in every head only the value 0 (no movement, no click or turn,
    the weapon, no use), so its choice is certain and teaches the policy nothing."""
    out = np.zeros(int(spec.action_nvec.sum()), dtype=bool)
    at = 0
    for values in spec.action_values:
        out[at + list(values).index(0)] = True
        at += len(values)
    return out
