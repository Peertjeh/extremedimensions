"""The mod's fight logs, for training. BattleHub records every fight in config/battlehub/fights (a .jsonl.gz per
fight; /battlehub fights export zips the new ones): every decision (two ticks), for every fighter with a target, the
state it was in (the simulation's state values, ml/pvp_sim2/obs.py STATE2_KEYS) and what it did over the decision in
the spec's action terms; for a bot also what its model chose and how the tick went against it; hits; and the result.
The observation is computed here from the stored state with the simulation's own code, so the logs stay useful when
the spec changes.

    python train/fights.py export-2026-10-23.zip            # what is in it
    python train/fights.py fights/ --who human              # only people

Used by train/imitate.py (learning to do what people did) and tools that compare the game with the simulation.
"""
import argparse
import gzip
import io
import json
import os
import sys
import zipfile
from dataclasses import dataclass, field

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from pvp_sim.actions import encode  # noqa: E402
from pvp_sim2.obs import BOOLS, INTS, STATE2_KEYS, observe  # noqa: E402
from pvp_sim2.spec import SPEC2  # noqa: E402

FORMAT = "battlehub-fights"


@dataclass
class Fight:
    """One recorded fight: its first line, the fighters' decisions, the hits and the last line (None when it was cut off)."""
    name: str
    header: dict
    rows: list = field(default_factory=list)
    hits: list = field(default_factory=list)
    end: dict = None

    def winners(self):
        return set(self.end.get("winners", [])) if self.end else set()

    def kind(self, fighter):
        return self.header["fighters"][fighter]["kind"]


def _parse(name, lines):
    it = iter(lines)
    header = json.loads(next(it))
    if header.get("format") != FORMAT:
        raise ValueError("%s is no fight log" % name)
    fight = Fight(name, header)
    for line in it:
        line = line.strip()
        if not line:
            continue
        d = json.loads(line)
        if "s" in d:
            fight.rows.append(d)
        elif "hit" in d:
            fight.hits.append(d)
        elif d.get("end"):
            fight.end = d
    return fight


def _text(data, name):
    if name.endswith(".gz"):
        data = gzip.decompress(data)
    return io.StringIO(data.decode("utf-8"))


def read(path):
    """The fights in a log file (.jsonl or .jsonl.gz), a zip of them, or a folder of either."""
    if os.path.isdir(path):
        for f in sorted(os.listdir(path)):
            if f.endswith((".jsonl", ".jsonl.gz", ".zip")):
                yield from read(os.path.join(path, f))
    elif path.endswith(".zip"):
        with zipfile.ZipFile(path) as z:
            for n in sorted(z.namelist()):
                if n.endswith((".jsonl", ".jsonl.gz")):
                    yield _parse(n, _text(z.read(n), n))
    else:
        with open(path, "rb") as f:
            yield _parse(os.path.basename(path), _text(f.read(), path))


def states(fight, rows=None):
    """The rows' states as the simulation keeps them: a dict of arrays (n,), the terrain rays (n, 8)."""
    rows = fight.rows if rows is None else rows
    keys, rays, ln = fight.header["state_keys"], fight.header["rays"], fight.header["ray_length"]
    v = np.array([r["s"] for r in rows], dtype=np.float64).reshape(len(rows), -1)
    scalars = [k for k in keys if k not in rays]
    s = {}
    for k, name in enumerate(scalars):
        col = v[:, k]
        s[name] = col > 0.5 if name in BOOLS else np.rint(col).astype(np.int64) if name in INTS else col
    base = len(scalars)
    for j, name in enumerate(rays):
        s[name] = v[:, base + j * ln: base + (j + 1) * ln]
    missing = [k for k in STATE2_KEYS if k not in s]
    if missing:
        raise ValueError("%s lacks %s" % (fight.name, ", ".join(missing)))
    return s


def actions(fight, rows=None, spec=SPEC2):
    """What the fighters did, as the spec's action indices (n, heads): the nearest value of each head."""
    rows = fight.rows if rows is None else rows
    names = fight.header["action_names"]
    a = np.array([r["a"] for r in rows], dtype=np.float64).reshape(len(rows), len(names))
    return encode(spec, **{n: a[:, k] for k, n in enumerate(names) if n in spec.action_names})


def arrays(fights, who="human", winners_only=False, spec=SPEC2):
    """Observations (n, obs_dim) and action indices (n, heads) of the chosen fighters ("human", "bot" or "all") over
    many fights, with per row the mode, whether the fighter won and the fight's name."""
    obs, act, mode, won, name = [], [], [], [], []
    for f in fights:
        win = f.winners()
        rows = [r for r in f.rows if (who == "all" or f.kind(r["f"]) == who) and (not winners_only or r["f"] in win)]
        if not rows:
            continue
        obs.append(observe(states(f, rows), spec).astype(np.float32))
        act.append(actions(f, rows, spec))
        mode += [f.header["mode"]] * len(rows)
        won += [r["f"] in win for r in rows]
        name += [f.name] * len(rows)
    if not obs:
        return np.zeros((0, spec.obs_dim), np.float32), np.zeros((0, len(spec.action_nvec)), np.int64), {"mode": [], "won": [], "fight": []}
    return np.concatenate(obs), np.concatenate(act), {"mode": mode, "won": np.array(won), "fight": name}


def summary(fights):
    """Per fight: mode, fighters (people and bots), decisions of each, hits, length, winners."""
    out = []
    for f in fights:
        kinds = [x["kind"] for x in f.header["fighters"]]
        per = {"human": 0, "bot": 0}
        for r in f.rows:
            per[kinds[r["f"]]] = per.get(kinds[r["f"]], 0) + 1
        out.append({"fight": f.name, "mode": f.header["mode"], "people": kinds.count("human"), "bots": kinds.count("bot"),
                    "decisions_people": per["human"], "decisions_bots": per["bot"], "hits": len(f.hits),
                    "seconds": round((f.end or {}).get("t", 0) / 20.0, 1), "winners": sorted(f.winners()),
                    "complete": f.end is not None})
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("paths", nargs="+")
    ap.add_argument("--who", default="all", choices=["human", "bot", "all"])
    a = ap.parse_args()
    fights = [f for p in a.paths for f in read(p)]
    rows = summary(fights)
    for r in rows:
        print(json.dumps(r))
    obs, act, info = arrays(fights, who=a.who)
    modes = {}
    for m in info["mode"]:
        modes[m] = modes.get(m, 0) + 1
    print("%d fights, %d decisions of %s (%.0f minutes), by mode: %s" % (len(fights), len(obs), a.who, len(obs) / 600.0, json.dumps(modes)))


if __name__ == "__main__":
    main()
