"""ML phase 3E: a difficulty level as the mod plays it (fixed knobs per slot in the simulation), and the levels against
people in the fight logs (the people's share of the hits, from fights of a bot version still current)."""
import math

import numpy as np

from pvp_sim2 import Batch2, Scripted2
from pvp_sim2.env import Scenario
from pvp_sim2.spec import SPEC3
from train import fights as F
from train import levels as L


def test_fixed_knobs_per_slot_replace_the_scenarios_random_ones_at_every_start():
    kn = {0: {"delay": 4, "noise": 2.5, "cap": 24}, 1: {"delay": 0, "noise": 0.2, "cap": 60}}
    b = Batch2(8, k=4, seed=3, spec=SPEC3, knobs=kn, fixed={"terrain": "walled", "family": "sword", "count": 2, "mode": "1v1"})
    sc = Scripted2(skill=0.8, seed=1)
    obs = b.reset()
    for _ in range(300):
        obs, r, d, info = b.step(np.stack([sc(obs[:, i]) for i in range(b.k)], axis=1))
    # arenas have finished and started again meanwhile: the knobs are still the level's
    assert (b.opp_delay[:, 0] == 4).all() and (b.opp_delay[:, 1] == 0).all()
    assert np.allclose(b.aim_noise[:, 0], 2.5) and np.allclose(b.turn_cap[:, 1], 60)
    # the other slots keep the scenario's random ones
    free = Batch2(8, k=4, seed=3, spec=SPEC3, scenario=Scenario(), fixed={"terrain": "walled", "family": "sword", "count": 2, "mode": "1v1"})
    free.reset()
    assert free.opp_delay.max() <= 2 and free.aim_noise.max() <= 1.5


def test_a_level_from_the_difficulties_file_has_its_knobs_and_hands():
    diff = {"intermediate": {"extra_delay": 2, "aim_noise": 1.2, "turn_cap": 36, "sample": True, "model": "x.onnx"},
            "master": {"extra_delay": 0, "aim_noise": 0.2, "turn_cap": 60, "sample": False, "model": "y.onnx", "items": "food,gapple,wind"}}
    es = L.from_difficulties(diff, {"intermediate": "a.zip", "master": "b.zip"})
    assert [e["name"] for e in es] == ["intermediate", "master"]
    assert L.knobs(es[0]) == {"delay": 2, "noise": 1.2, "cap": 36.0} and es[0]["sample"] and es[0]["hands"] == []
    assert es[1]["hands"] == ["food", "gapple", "wind"] and not es[1]["sample"]
    t = L.parse_try("adv=c.zip,delay=1,noise=0.8,cap=45,sample=1")
    assert t["name"] == "adv" and t["path"] == "c.zip" and t["delay"] == 1 and t["sample"] and t["hands"] == []


def test_a_share_of_the_hits_becomes_a_share_of_the_fights():
    # pairs where fights follow hits with k = 2 exactly
    pairs = []
    for share in (0.3, 0.4, 0.6, 0.7):
        score = 1 / (1 + math.exp(-2 * L.logit(share)))
        pairs.append({"result": [int(round(1000 * score)), 1000 - int(round(1000 * score)), 0], "hits": [1000 * share, 1000 * (1 - share)]})
    # a pair that won everything says nothing about the slope
    pairs.append({"result": [50, 0, 0], "hits": [900, 100]})
    assert abs(L.slope(pairs) - 2) < 0.02
    assert L.elo_gap(0.5, 2.5) == 0
    assert L.elo_gap(0.29, 2.5) < -300 < L.elo_gap(0.4, 2.5) < 0


def _fight(name, mod, mode, fighters, hits):
    f = F.Fight(name, {"mod": mod, "mode": mode, "fighters": fighters})
    f.hits = [{"t": i, "hit": list(h), "dmg": 5, "kind": k} for i, (h, k) in enumerate(hits)]
    return f


def test_the_people_share_of_hits_counts_current_bots_and_melee_only():
    people = [{"i": 0, "kind": "human"}, {"i": 1, "kind": "bot", "level": "master"}]
    fights = [
        _fight("a", "1.47.0", "sword", people, [((0, 1), "melee"), ((1, 0), "melee"), ((1, 0), "melee"), ((0, 1), "arrow")]),
        # a learned fight of 1.40 (an older master) does not count
        _fight("b", "1.40.0", "sword", people, [((0, 1), "melee")] * 5),
        # the mace bot only since 1.46
        _fight("c", "1.45.0", "mace", people, [((0, 1), "melee")] * 5),
        _fight("d", "1.46.0", "mace", people, [((0, 1), "melee"), ((1, 0), "melee")]),
    ]
    got = L.people_hits(fights)
    assert got[("learned", "master")] == [{"fight": "a", "people": 1, "bots": 2}]
    assert got[("mace", "master")] == [{"fight": "d", "people": 1, "bots": 1}]
    rows = L.people_report(fights)
    learned = [r for r in rows if r["family"] == "learned"][0]
    assert learned["fights"] == 1 and abs(learned["share"] - 1 / 3) < 1e-9
    assert L.version("1.47.1") == (1, 47, 1) and L.version("test") == (0, 0, 0)
