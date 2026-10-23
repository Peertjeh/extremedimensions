"""Action masks (pvp_sim2/masks.py, spec/combat_v2_masks.json), the item counters of a decision, the scripted teacher
with latch and the extra items of the training."""
import json
import os

import numpy as np
import pytest

from pvp_sim2 import Batch2, Scenario, Scripted2
from pvp_sim2 import kits as K
from pvp_sim2.masks import MASKS2, MASKS2_PATH, Masks
from pvp_sim2.spec import SPEC2

HERE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
HAND = SPEC2.action_index("hand")
USE = SPEC2.action_index("use")


def _items_batch(seed=3, n=48):
    sc = Scenario(extra_items=1.0)
    b = Batch2(n, seed=seed, scenario=sc, fixed={"terrain": "islands", "family": "loot", "count": 3, "mode": "ffa"})
    return b, b.reset()


def test_rules_are_bound_to_the_spec_and_keep_a_choice():
    with open(MASKS2_PATH) as f:
        d = json.load(f)
    assert d["spec_sha256"] == SPEC2.sha256
    assert MASKS2.size == int(SPEC2.action_nvec.sum())
    # the weapon and not using stay allowed whatever the observation
    obs = np.random.default_rng(0).uniform(-1, 1, (500, SPEC2.obs_dim))
    heads = MASKS2.split(MASKS2(obs))
    assert heads[HAND][:, 0].all() and heads[USE][:, 0].all()
    for h, m in enumerate(heads):
        assert m.any(axis=1).all(), SPEC2.action_names[h]
        if h not in (HAND, USE):
            assert m.all()


def test_a_mask_for_another_spec_is_refused(tmp_path):
    with open(MASKS2_PATH) as f:
        d = json.load(f)
    d["spec_sha256"] = "0" * 64
    p = tmp_path / "m.json"
    p.write_text(json.dumps(d))
    with pytest.raises(ValueError):
        Masks(path=str(p))


def test_hand_mask_is_what_the_fighter_carries():
    """An allowed hand is one the simulation would switch to (physics.carried), food only below full hunger."""
    b, obs = _items_batch()
    rng = np.random.default_rng(1)
    for _ in range(30):
        f = b.w.f
        heads = MASKS2.split(b.action_masks())
        live = f.alive & f.active
        for h in range(6):
            want = b.w.carried(np.full(f.alive.shape, h))
            if h == 2:
                want = want & (f.food < 20)
            assert (heads[HAND][..., h] == want)[live].all(), SPEC2.hands[h]
        usable = (b.w.carried(np.full(f.alive.shape, 1)) | (b.w.carried(np.full(f.alive.shape, 2)) & (f.food < 20))
                  | b.w.carried(np.full(f.alive.shape, 3)) | b.w.carried(np.full(f.alive.shape, 4))
                  | b.w.carried(np.full(f.alive.shape, 5)) | (f.shield & (f.shield_cd == 0)))
        assert (heads[USE][..., 1] == usable)[live].all()
        obs, _, _, _ = b.step(rng.integers(0, SPEC2.action_nvec, size=(b.n, b.k, len(SPEC2.action_nvec))))
    assert heads[HAND][..., 1:].any() and not heads[HAND][..., 1:].all()


def test_golden_masks_are_what_the_rules_give():
    with open(os.path.join(HERE, "spec", "combat_v2_masks_golden.json")) as f:
        g = json.load(f)
    assert g["spec_sha256"] == SPEC2.sha256
    obs = np.array([c["observation"] for c in g["cases"]], dtype=np.float32)
    mask = MASKS2(obs)
    want = np.array([[ch == "1" for ch in c["mask"]] for c in g["cases"]])
    assert (mask == want).all()
    best = MASKS2.best(np.array([c["logits"] for c in g["cases"]]), mask)
    assert (best == np.array([c["best"] for c in g["cases"]])).all()
    # the edge cases are there: full hunger forbids food, nothing carried forbids the right button
    assert (~want[:, MASKS2.offsets[HAND] + 2]).any() and (~want[:, MASKS2.offsets[USE] + 1]).any()


def test_best_never_picks_what_is_masked():
    rng = np.random.default_rng(2)
    obs = rng.uniform(-1, 1, (400, SPEC2.obs_dim))
    for name in ("self_arrows", "self_food_items", "self_gapples", "self_pearls", "self_winds"):
        obs[:, SPEC2.index(name)] = np.where(rng.random(400) < 0.5, 0.0, np.abs(obs[:, SPEC2.index(name)]))
    mask = MASKS2(obs)
    logits = rng.normal(0, 3, (400, MASKS2.size))
    best = MASKS2.best(logits, mask)
    for h, m in enumerate(MASKS2.split(mask)):
        assert m[np.arange(400), best[:, h]].all()


def test_item_counters_cover_every_tick_of_a_decision():
    """info counts arrows, food and throws over the two ticks of a decision: what runs out is what was counted."""
    b, obs = _items_batch(seed=5)
    teacher = Scripted2(skill=0.9, seed=4, latch=True)
    f = b.w.f
    fired = ate = threw = 0
    spent = {"arrows": 0, "eat": 0, "throw": 0}
    for _ in range(300):
        before = (f.arrows.copy(), (f.food_items + f.gapples).copy(), (f.pearls + f.winds).copy())
        acts = np.stack([teacher(obs[:, i]) for i in range(3)], axis=1)
        obs, _, d, info = b.step(acts)
        live = ~d[:, None] & np.ones((1, 3), dtype=bool)
        fired += int(info["fired"][live].sum())
        ate += int(info["ate"][live].sum())
        threw += int(info["threw"][live].sum())
        spent["arrows"] += int(np.maximum(0, before[0] - f.arrows)[live].sum())
        spent["eat"] += int(np.maximum(0, before[1] - (f.food_items + f.gapples))[live].sum())
        spent["throw"] += int(np.maximum(0, before[2] - (f.pearls + f.winds))[live].sum())
    assert fired > 0 and ate > 0 and threw > 0
    assert (fired, ate, threw) == (spent["arrows"], spent["eat"], spent["throw"])


def test_the_teacher_throws_wind_charges_with_the_mace():
    """With latch the scripted bot keeps the wind charge in hand, looks down and throws it; without, switching costs
    the charge its rule asks for and it never does (the simulation's scripted bot, not the mod's)."""
    thrown = {}
    for latch in (False, True):
        b = Batch2(32, seed=8, fixed={"terrain": "walled", "family": "mace", "count": 2})
        obs = b.reset()
        bot = Scripted2(skill=0.8, seed=3, latch=latch)
        n = 0
        for _ in range(400):
            obs, _, _, info = b.step(np.stack([bot(obs[:, 0]), bot(obs[:, 1]), bot(obs[:, 2])], axis=1))
            n += int(info["threw"].sum())
        thrown[latch] = n
    assert thrown[False] == 0
    assert thrown[True] >= 20


def test_extra_items_only_where_chests_or_the_shop_give_them():
    rng = np.random.default_rng(0)
    k = K.extra_items(K.loot(rng), np.random.default_rng(1))
    assert k["bow_power"] >= 0 or k["food_items"] > 0 or k["pearls"] > 0
    b, _ = _items_batch(seed=9)
    f = b.w.f
    assert (f.pearls[:, :3] > 0).mean() > 0.4
    # a duel kit gets nothing more, and without extra_items the stream of random numbers is the one it was
    d1 = Batch2(8, seed=4, scenario=Scenario(extra_items=1.0), fixed={"terrain": "walled", "family": "sword", "count": 2})
    d1.reset()
    assert (d1.w.f.pearls == 0).all()
    a, c = Batch2(8, seed=4), Batch2(8, seed=4, scenario=Scenario(extra_items=0.0))
    a.reset()
    c.reset()
    assert np.array_equal(a.w.f.x, c.w.f.x) and np.array_equal(a.w.f.arrows, c.w.f.arrows)


def test_chooser_with_masks_picks_only_allowed_choices():
    torch = pytest.importorskip("torch")
    pytest.importorskip("stable_baselines3")
    from stable_baselines3 import PPO
    from pvp_sim2 import PvpEnv2
    from train.policies import Chooser
    torch.manual_seed(0)
    model = PPO("MlpPolicy", PvpEnv2(seed=0), n_steps=64, batch_size=64, device="cpu")
    b, obs = _items_batch(seed=11, n=16)
    o = obs.reshape(-1, SPEC2.obs_dim)
    mask = MASKS2(o)
    for det in (True, False):
        act = Chooser(model.policy, masks=True, deterministic=det, seed=1)(o)
        for h, m in enumerate(MASKS2.split(mask)):
            assert m[np.arange(len(o)), act[:, h]].all()
