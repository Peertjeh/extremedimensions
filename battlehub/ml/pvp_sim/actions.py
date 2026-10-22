"""Actions of the combat spec: a MultiDiscrete index per head, decoded to what the bot does (same as the mod)."""
import numpy as np

from .spec import SPEC


def decode(idx, spec=SPEC):
    """Action indices (m, heads) to values: forward, strafe (-1/0/1), jump, sprint, attack (0/1), yaw (degrees to the
    left over the decision), pitch (degrees down over the decision)."""
    idx = np.asarray(idx, dtype=np.int64).reshape(-1, len(spec.action_names))
    out = {}
    for h, name in enumerate(spec.action_names):
        values = np.asarray(spec.action_values[h], dtype=np.float64)
        out[name] = values[np.clip(idx[:, h], 0, len(values) - 1)]
    return out


def encode(spec=SPEC, **values):
    """The index of each head nearest to the given values (missing heads: the value 0); for tests and scripted play."""
    m = None
    for v in values.values():
        m = np.size(v) if m is None else max(m, np.size(v))
    m = m or 1
    idx = np.zeros((m, len(spec.action_names)), dtype=np.int64)
    for h, name in enumerate(spec.action_names):
        table = np.asarray(spec.action_values[h], dtype=np.float64)
        want = np.broadcast_to(np.asarray(values.get(name, 0.0), dtype=np.float64), (m,))
        idx[:, h] = np.abs(want[:, None] - table[None, :]).argmin(axis=1)
    return idx
