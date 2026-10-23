"""What the mod's model reports say to practise. BattleHub writes config/battlehub/ml_feedback.json after every match
with learned bots: per spec how much of their fights went as the model chose, why the rest did not, which values were
new to the model, and the lessons that follow (ml/spec/lessons.json). This turns those lessons into the scenario
weights of the v2/v3 simulation (pvp_sim2.Scenario): each lesson's focus multiplies terrains, families, modes3 and
modes4 by key, and three, four and chaos as numbers; a lesson that explains a fifth of the fights or more gets its whole focus, a smaller
one part of it.

    python train/focus.py ml_feedback.json        # what the next training would practise more
    python train/selfplay2.py --run runs/v2focus --init runs/v2/ckpt_040000032.zip --pool-from runs/v2 --focus ml_feedback.json
"""
import argparse
import copy
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from pvp_sim2.env import Scenario  # noqa: E402

LESSONS_PATH = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "spec", "lessons.json")
FULL = 0.2           # a lesson explaining this share of the fights (or more) gets its whole focus
MAPS = ("terrains", "families", "modes3", "modes4")
NUMBERS = {"three": 0.9, "four": 0.6, "chaos": 0.9}   # scalar weights, with the most they may become


def lessons_table(path=LESSONS_PATH):
    with open(path) as f:
        return json.load(f)


def load(path, spec="v2"):
    """The lessons of one spec in a feedback file, biggest first: [{"id", "weight", "items"}, ...]."""
    with open(path) as f:
        d = json.load(f)
    s = d.get("specs", {}).get(spec)
    if not s:
        return []
    return [{"id": x["id"], "weight": float(x["weight"]), "items": bool(x.get("items"))} for x in s.get("lessons", [])]


def apply(scenario, lessons, table=None):
    """A copy of the scenario with the focus of these lessons (the focus comes from lessons.json, by lesson id), and
    notes on what changed."""
    table = table or lessons_table()
    sc = copy.deepcopy(scenario)
    notes = []
    for lesson in lessons:
        spec = table["lessons"].get(lesson["id"])
        if spec is None:
            notes.append("%s: unknown here, skipped" % lesson["id"])
            continue
        k = min(1.0, lesson["weight"] / FULL)
        for key, value in spec.get("focus", {}).items():
            if key in MAPS:
                d = getattr(sc, key)
                for name, factor in value.items():
                    if name not in d:
                        raise KeyError("%s has no %s %r" % (type(sc).__name__, key, name))
                    d[name] *= 1 + (factor - 1) * k
            elif key in NUMBERS:
                setattr(sc, key, min(NUMBERS[key], getattr(sc, key) * (1 + (value - 1) * k)))
            else:
                raise KeyError("unknown focus %r in lesson %s" % (key, lesson["id"]))
        notes.append("%s (%.0f%% of the fights): %s%s" % (lesson["id"], lesson["weight"] * 100, json.dumps(spec.get("focus", {})),
                                                        ", needs --items" if lesson["items"] else ""))
    return sc, notes


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("feedback")
    ap.add_argument("--spec", default="v2")
    a = ap.parse_args()
    lessons = load(a.feedback, a.spec)
    if not lessons:
        print("no lessons for %s in %s" % (a.spec, a.feedback))
        return
    base = Scenario()
    sc, notes = apply(base, lessons)
    for n in notes:
        print(n)
    for key in MAPS:
        for name, v in getattr(sc, key).items():
            if abs(v - getattr(base, key)[name]) > 1e-9:
                print("  %s.%s: %.2f -> %.2f" % (key, name, getattr(base, key)[name], v))
    for key in NUMBERS:
        if abs(getattr(sc, key) - getattr(base, key)) > 1e-9:
            print("  %s: %.2f -> %.2f" % (key, getattr(base, key), getattr(sc, key)))


if __name__ == "__main__":
    main()
