"""The environments: the Gymnasium contract, determinism, rewards and the end of a fight."""
import numpy as np
from gymnasium.utils.env_checker import check_env

from pvp_sim import BOXING, PvpBatch, PvpEnv, RandomPolicy, Rewards, ScriptedPolicy
from pvp_sim.spec import SPEC


def test_the_gymnasium_contract():
    check_env(PvpEnv(seed=1), skip_render_check=True)


def test_the_same_seed_gives_the_same_fight():
    def play(seed):
        env = PvpEnv(opponent=ScriptedPolicy(seed=5), seed=seed)
        obs, _ = env.reset(seed=seed)
        rng = np.random.default_rng(9)
        out = [obs]
        for _ in range(60):
            obs, r, term, trunc, _ = env.step(rng.integers(0, SPEC.action_nvec))
            out.append(obs)
            if term or trunc:
                break
        return np.array(out)
    a, b = play(3), play(3)
    assert a.shape == b.shape and np.allclose(a, b)


def test_without_penalties_the_rewards_are_zero_sum():
    b = PvpBatch(64, seed=2, rewards=Rewards(whiff=0, time=0, unseen=0))
    obs = b.reset()
    p0, p1 = ScriptedPolicy(seed=1), ScriptedPolicy(seed=2)
    for _ in range(300):
        obs, r, d, info = b.step(np.stack([p0(obs[:, 0]), p1(obs[:, 1])], axis=1))
        assert np.allclose(r.sum(axis=1), 0.0, atol=1e-5)


def test_the_scripted_bot_beats_random_play():
    b = PvpBatch(128, seed=3)
    obs = b.reset()
    bot, rnd = ScriptedPolicy(seed=1), RandomPolicy(seed=2)
    wins = np.zeros(3)
    while wins.sum() < 300:
        obs, r, d, info = b.step(np.stack([bot(obs[:, 0]), rnd(obs[:, 1])], axis=1))
        for w in info["winner"][d]:
            wins[w if w >= 0 else 2] += 1
    assert wins[0] / wins.sum() > 0.9, wins


def test_a_finished_arena_starts_again_with_fresh_fighters():
    b = PvpBatch(8, seed=4, max_ticks=40)
    obs = b.reset()
    p = ScriptedPolicy(seed=1)
    for _ in range(20):
        obs, r, d, info = b.step(np.stack([p(obs[:, 0]), p(obs[:, 1])], axis=1))
    assert d.all() and "terminal_obs" in info
    assert (b.ticks == 0).all()
    assert np.allclose(b.ph.f.health, 20.0)


def test_boxing_ends_at_the_hits_and_keeps_health_full():
    b = PvpBatch(32, rules=BOXING, seed=5)
    obs = b.reset()
    p0, p1 = ScriptedPolicy(0.9, seed=1), ScriptedPolicy(0.3, seed=2)
    ended = 0
    while ended < 20:
        before = b.ph.f.hits.copy()
        obs, r, d, info = b.step(np.stack([p0(obs[:, 0]), p1(obs[:, 1])], axis=1))
        decided = d & (info["winner"] >= 0)
        if decided.any():
            w = info["winner"][decided]
            hits = before[decided, w] + info["landed"][decided, w]
            assert (hits >= BOXING.hits_to_win).all()
        ended += decided.sum()
        assert np.allclose(b.ph.f.health, BOXING.max_health)
