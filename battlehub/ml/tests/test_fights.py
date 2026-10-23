"""The mod's fight logs (spec/fightlog_sample.jsonl is written by the mod's FightLogTest from states of the golden file,
with the observation the mod computed): the loader gives the simulation's state back, and the simulation's observation
of it is the mod's."""
import gzip
import json
import os
import zipfile

import numpy as np

from pvp_sim2.obs import STATE2_KEYS, observe
from pvp_sim2.spec import SPEC2
from train import fights

SAMPLE = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "spec", "fightlog_sample.jsonl")


def sample():
    return next(fights.read(SAMPLE))


def test_the_header_names_the_simulations_state_and_the_specs_actions():
    f = sample()
    h = f.header
    assert h["spec_sha256"] == SPEC2.sha256
    assert [k for k in h["state_keys"] if k not in h["rays"]] + list(h["rays"]) == STATE2_KEYS
    assert h["action_names"] == SPEC2.action_names
    assert [x["kind"] for x in h["fighters"]] == ["human", "bot"]
    assert len(f.rows) == 20 and len(f.hits) == 1 and f.end["winners"] == [1]


def test_the_simulations_observation_of_a_stored_state_is_the_mods():
    f = sample()
    obs = observe(fights.states(f))
    want = np.array([r["obs"] for r in f.rows])
    assert obs.shape == want.shape == (20, SPEC2.obs_dim)
    np.testing.assert_allclose(obs, want, atol=2e-5)


def test_actions_become_the_nearest_choice_of_each_head():
    f = sample()
    act = fights.actions(f)
    assert act.shape == (20, len(SPEC2.action_nvec))
    yaw = SPEC2.action_names.index("yaw")
    for r, a in zip(f.rows, act):
        values = np.array(SPEC2.action_values[yaw], dtype=float)
        assert a[yaw] == np.abs(values - r["a"][yaw]).argmin()
        assert SPEC2.action_values[SPEC2.action_names.index("forward")][a[0]] == r["a"][0]


def test_people_bots_and_winners_and_zips_of_gzip_files(tmp_path):
    with open(SAMPLE, "rb") as src:
        data = src.read()
    z = tmp_path / "export.zip"
    with zipfile.ZipFile(z, "w") as out:
        out.writestr("a.jsonl.gz", gzip.compress(data))
        out.writestr("b.jsonl.gz", gzip.compress(data))
    all_fights = list(fights.read(str(z)))
    assert len(all_fights) == 2
    obs, act, info = fights.arrays(all_fights, who="human")
    assert obs.shape[0] == 20 and act.shape[0] == 20
    _, _, bots = fights.arrays(all_fights, who="bot", winners_only=True)
    assert len(bots["won"]) == 20 and bots["won"].all()
    rows = fights.summary(all_fights)
    assert rows[0]["people"] == 1 and rows[0]["bots"] == 1 and rows[0]["complete"]
    assert json.dumps(rows)


def test_imitation_learns_what_was_done():
    """Behavior cloning on made-up decisions (attack exactly when the target is in front) learns that rule."""
    import pytest
    pytest.importorskip("stable_baselines3")
    from stable_baselines3 import PPO

    from pvp_sim2 import PvpEnv2
    from train.imitate import clone, heads_accuracy

    rng = np.random.default_rng(0)
    obs = rng.uniform(-1, 1, (3000, SPEC2.obs_dim)).astype(np.float32)
    act = np.zeros((3000, len(SPEC2.action_nvec)), dtype=np.int64)
    attack = SPEC2.action_names.index("attack")
    act[:, attack] = (obs[:, SPEC2.index("aim_yaw_cos")] > 0).astype(np.int64)
    model = PPO("MlpPolicy", PvpEnv2(seed=1), n_steps=64, batch_size=64, policy_kwargs=dict(net_arch=[64, 64]), device="cpu", seed=1)
    before = heads_accuracy(model.policy, obs, act)[attack]
    clone(model, obs, act, epochs=20, batch=256, lr=3e-3, log=lambda s: None)
    after = heads_accuracy(model.policy, obs, act)[attack]
    assert after > 0.9 and after > before
