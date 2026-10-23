"""train/people.py: the rows it learns from are the people's fighting ones, and the people opponent keeps its choices
per row (stickiness), shifts the logits it is told to, and copies per slot."""
import copy

import numpy as np
import pytest

from pvp_sim2.spec import SPEC3
from train import fights, people


def _sample():
    import os
    return next(fights.read(os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "spec", "fightlog_sample.jsonl")))


def test_fighting_rows_have_the_target_in_sight_within_eight_blocks():
    f = _sample()
    s = fights.states(f)
    m = people.fighting(s)
    d = np.hypot(s["opp_x"] - s["self_x"], s["opp_z"] - s["self_z"])
    assert (m == (s["opp_visible"] & (d < 8))).all()
    rates = people.change_rates_logs([f])
    assert rates.shape == (len(SPEC3.action_nvec),) and ((rates >= 0) & (rates <= 1)).all()


def _tiny_policy():
    pytest.importorskip("torch")
    import gymnasium as gym
    from gymnasium import spaces
    from stable_baselines3 import PPO
    from stable_baselines3.common.env_util import DummyVecEnv

    class Tiny(gym.Env):
        observation_space = spaces.Box(-1.0, 1.0, shape=(SPEC3.obs_dim,), dtype=np.float32)
        action_space = spaces.MultiDiscrete(SPEC3.action_nvec)

        def reset(self, seed=None, options=None):
            return np.zeros(SPEC3.obs_dim, dtype=np.float32), {}

        def step(self, a):
            return np.zeros(SPEC3.obs_dim, dtype=np.float32), 0.0, True, False, {}

    return PPO("MlpPolicy", DummyVecEnv([Tiny]), policy_kwargs=dict(net_arch=dict(pi=[16], vf=[16])), seed=1, device="cpu").policy


def test_the_people_opponent_keeps_its_choices_per_row_and_copies_per_slot():
    policy = _tiny_policy()
    rng = np.random.default_rng(0)
    obs = rng.uniform(-1, 1, (6, SPEC3.obs_dim)).astype(np.float32)
    stuck = np.ones(len(SPEC3.action_nvec)) * 0.999
    p = people.People(policy, stuck, seed=3)
    first = p(obs)
    hand = SPEC3.action_index("hand")
    keep = [h for h in range(len(SPEC3.action_nvec)) if h != hand]
    assert (p(obs)[:, keep] == first[:, keep]).all(), "a stickiness near 1 keeps every head but the hand"
    # a subset of a batch of 10: its rows keep their own last choices
    q = people.People(policy, stuck, seed=4)
    a = q.rows(obs[:3], np.array([0, 4, 7]), 10)
    b = q.rows(obs[:3], np.array([0, 4, 7]), 10)
    assert (a[:, keep] == b[:, keep]).all()
    # never sticky: a fresh sample each time, and a shifted logit makes that choice the usual one
    free = people.People(policy, np.zeros(len(SPEC3.action_nvec)), {"jump": 20.0}, seed=5)
    assert (free(obs)[:, SPEC3.action_index("jump")] == 1).all()
    c = copy.deepcopy(free)
    assert c.policy is free.policy and c.shifts == {"jump": 20.0} and c.last is None
