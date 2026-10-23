"""The lessons of the model reports (spec/lessons.json, read by the mod and by train/focus.py) and the training ranges
(spec/combat_v*_ranges.json, from tools/make_ranges.py): every observation of every spec belongs to exactly one
lesson, every focus names something the simulation has, the ranges fit their spec, and a feedback file turns into the
right scenario weights."""
import json
import os

import pytest

from pvp_sim.spec import SPEC
from pvp_sim2.env import Scenario
from pvp_sim2.spec import SPEC2, SPEC3
from train import focus

HERE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TABLE = focus.lessons_table()
# ModelReport.Why in the mod
REASONS = ["edge", "jump", "air", "stuck", "recover", "evade", "heal", "shoot", "objective", "search", "react", "other"]


def claims(name):
    name = name.split("@")[0]   # an observation of an earlier decision: the group of the observation it was
    n = 0
    for g in TABLE["features"]:
        n += name in g.get("names", [])
        n += sum(name.startswith(p) for p in g.get("prefixes", []))
    return n


@pytest.mark.parametrize("spec", [SPEC, SPEC2, SPEC3], ids=["v1", "v2", "v3"])
def test_every_observation_belongs_to_exactly_one_lesson(spec):
    for name in spec.obs_names:
        assert claims(name) == 1, name


def test_every_reason_is_declared_and_every_lesson_described():
    assert sorted(TABLE["reasons"]) == sorted(REASONS)
    used = {v for v in TABLE["reasons"].values() if v} | {g["lesson"] for g in TABLE["features"]}
    assert used <= set(TABLE["lessons"])


def test_every_focus_names_what_the_simulation_has():
    sc = Scenario()
    for lesson, spec in TABLE["lessons"].items():
        for key, value in spec.get("focus", {}).items():
            if key in focus.MAPS:
                assert set(value) <= set(getattr(sc, key)), (lesson, key)
                assert all(f >= 1 for f in value.values())
            else:
                assert key in focus.NUMBERS, (lesson, key)


@pytest.mark.parametrize("spec,v", [(SPEC, "v1"), (SPEC2, "v2"), (SPEC3, "v3")])
def test_the_ranges_fit_their_spec(spec, v):
    with open(os.path.join(HERE, "spec", "combat_%s_ranges.json" % v)) as f:
        d = json.load(f)
    assert d["spec_sha256"] == spec.sha256, "run tools/make_ranges.py"
    assert list(d["ranges"]) == spec.obs_names
    for name, (lo, hi) in d["ranges"].items():
        i = spec.index(name)
        assert -spec.obs_clip[i] <= lo <= hi <= spec.obs_clip[i], name
    assert 0 < d["tolerance"] < 0.5


def test_a_feedback_file_shifts_the_scenarios(tmp_path):
    fb = {"specs": {"v2": {"lessons": [{"id": "edges", "weight": 0.25, "items": False},
                                       {"id": "eating", "weight": 0.05, "items": True},
                                       {"id": "crowds", "weight": 0.4, "items": False},
                                       {"id": "from_a_newer_mod", "weight": 0.3, "items": False}]}}}
    path = tmp_path / "ml_feedback.json"
    path.write_text(json.dumps(fb))
    lessons = focus.load(str(path))
    assert [x["id"] for x in lessons] == ["edges", "eating", "crowds", "from_a_newer_mod"]
    base = Scenario()
    sc, notes = focus.apply(base, lessons)
    # a fifth of the fights or more: the whole focus
    assert sc.terrains["bridge"] == pytest.approx(base.terrains["bridge"] * 2.0)
    assert sc.terrains["islands"] == pytest.approx(base.terrains["islands"] * 1.5)
    # 5%: a quarter of it
    assert sc.families["build_uhc"] == pytest.approx(base.families["build_uhc"] * (1 + 0.5 * 0.25))
    assert sc.three == pytest.approx(min(0.9, base.three * 2.0))
    assert sc.modes3["ffa"] == pytest.approx(base.modes3["ffa"] * 1.5)
    assert sc.terrains["walled"] == base.terrains["walled"], "nothing asked for it"
    assert any("needs --items" in n for n in notes)
    assert any("unknown here" in n for n in notes)
    assert base.terrains["bridge"] == Scenario().terrains["bridge"], "the scenario given stays as it was"
    assert focus.load(str(path), "v1") == []
