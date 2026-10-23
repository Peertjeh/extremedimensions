"""Spec v3 (phase 3C): v2's observations in their place, the team mate, the third enemy, memory, the history of earlier
decisions, four fighters with team modes, the team's end of a fight and the shared reward."""
import numpy as np
import pytest

from pvp_sim2 import Batch2, Scripted2
from pvp_sim2.env import TEAMS, Rewards2
from pvp_sim2.masks import MASKS2, dead_mask
from pvp_sim2.spec import SPEC2, SPEC3

ix = SPEC3.index


def test_v3_keeps_v2_in_place_and_its_history_points_at_observations_of_now():
    assert SPEC3.obs_names[:SPEC2.obs_dim] == SPEC2.obs_names
    assert np.array_equal(SPEC3.obs_scale[:SPEC2.obs_dim], SPEC2.obs_scale)
    assert SPEC3.obs_dim == 169 and SPEC3.action_names == SPEC2.action_names
    assert len(SPEC3.history) == 32 and SPEC3.max_ago == 3
    for k, of, ago in SPEC3.history:
        assert of in SPEC3.current and ago in (1, 3)
        assert SPEC3.obs_names[k] == "%s@%d" % (SPEC3.obs_names[of], ago)
    assert SPEC2.history == [] and SPEC2.max_ago == 0


def _play(b, steps, seed=1):
    sc = Scripted2(skill=0.8, seed=seed)
    obs = b.reset()
    seen = [obs.copy()]
    for _ in range(steps):
        obs, r, d, info = b.step(np.stack([sc(obs[:, i]) for i in range(b.k)], axis=1))
        seen.append(obs.copy())
        yield obs, r, d, info, seen


def test_the_history_is_the_same_fighters_earlier_observations_and_now_at_a_start():
    b = Batch2(16, k=4, seed=4, spec=SPEC3, auto_reset=False, fixed={"terrain": "walled", "family": "sword", "count": 4, "mode": "2v2"})
    obs0 = b.reset()
    for k, of, ago in SPEC3.history:
        assert np.array_equal(obs0[:, :, k], obs0[:, :, of])
    for t, (obs, r, d, info, seen) in enumerate(_play(b, 6)):
        if t < 3:
            continue
        for k, of, ago in SPEC3.history:
            assert np.allclose(obs[:, :, k], seen[-1 - ago][:, :, of]), SPEC3.obs_names[k]
    # a fight that starts again (auto reset) starts its history again
    b = Batch2(32, k=4, seed=5, spec=SPEC3, max_ticks=40)
    for obs, r, d, info, seen in _play(b, 30):
        if d.any():
            a = np.nonzero(d)[0]
            for k, of, ago in SPEC3.history:
                assert np.array_equal(obs[a, :, k], obs[a, :, of])
            break
    else:
        pytest.fail("no fight ended")


def test_two_against_two_starts_together_and_sees_its_team_mate():
    b = Batch2(24, k=4, seed=6, spec=SPEC3, fixed={"terrain": "walled", "family": "sword", "count": 4, "mode": "2v2"})
    obs = b.reset()
    f = b.w.f
    assert TEAMS["2v2"] == [0, 1, 0, 1]
    assert (f.team == np.array(TEAMS["2v2"])).all()
    mate = np.hypot(f.x[:, 2] - f.x[:, 0], f.z[:, 2] - f.z[:, 0])
    foe = np.hypot(f.x[:, 1] - f.x[:, 0], f.z[:, 1] - f.z[:, 0])
    assert (mate >= 1.5).all() and (mate <= 5.0).all() and (foe >= 4.0).all()
    dist = obs[:, 0, ix("ally_dist")] * SPEC3.obs_scale[ix("ally_dist")]
    assert np.allclose(dist, mate, atol=1e-4)
    assert (obs[:, :, ix("allies_near")] > 0).all()
    # the third enemy: there is none with two enemies; the second is the other enemy
    assert (obs[:, 0, ix("e3_dist")] == 1.0).all() and (obs[:, 0, ix("e2_dist")] < 1.0).all()


def test_alone_there_is_no_team_mate_and_four_against_each_other_has_a_third_enemy():
    b = Batch2(8, k=4, seed=7, spec=SPEC3, fixed={"terrain": "walled", "family": "sword", "count": 2})
    obs = b.reset()
    assert (obs[:, 0, ix("ally_dist")] == 1.0).all() and (obs[:, 0, ix("ally_fwd")] == 0).all()
    assert (obs[:, 0, ix("ally_health")] == 1.0).all() and (obs[:, 0, ix("allies_near")] == 0).all()
    b = Batch2(8, k=4, seed=8, spec=SPEC3, fixed={"terrain": "walled", "family": "sword", "count": 4, "mode": "ffa4"})
    obs = b.reset()
    assert (obs[:, 0, ix("e3_dist")] < 1.0).all() and (obs[:, 0, ix("ally_dist")] == 1.0).all()


def test_with_a_team_mate_the_fight_goes_on_after_the_learner_dies_and_it_may_only_do_nothing():
    b = Batch2(4, k=4, seed=9, spec=SPEC3, auto_reset=False, fixed={"terrain": "walled", "family": "sword", "count": 4, "mode": "2v2"})
    obs = b.reset()
    b.w.f.health[:, 0] = 0.5
    b.w.hurt(0, np.ones(4, dtype=bool), 50.0, np.full(4, 1))
    b.w.f.alive[:, 0] = b.w.f.health[:, 0] > 0
    assert not b.w.f.alive[:, 0].any()
    nothing = np.zeros((4, 4, len(SPEC3.action_nvec)), dtype=np.int64)
    obs, r, d, info = b.step(nothing)
    assert not d.any()                           # the team mate still fights
    m = b.action_masks()
    assert (m[:, 0] == dead_mask()).all() and (m[:, 0].sum(axis=1) == len(SPEC3.action_nvec)).all()
    assert (m[:, 2] == MASKS2(obs[:, 2, :SPEC2.obs_dim])).all()
    # its team mate dies too: the team lost
    b.w.f.alive[:, 2] = False
    obs, r, d, info = b.step(nothing)
    assert d.all() and (info["winner"] == 1).all()


def test_the_reward_is_shared_within_the_team():
    for tau in (0.0, 1.0):
        b = Batch2(16, k=4, seed=10, spec=SPEC3, rewards=Rewards2(team_spirit=tau),
                   fixed={"terrain": "walled", "family": "sword", "count": 4, "mode": "2v2"})
        same = 0
        for obs, r, d, info, seen in _play(b, 40):
            live = ~d
            same += np.allclose(r[live, 0], r[live, 2])
        assert (same == 40) == (tau == 1.0)


def test_memory_counts_the_ticks_since_the_target_was_seen_and_since_a_hit():
    b = Batch2(8, k=4, seed=11, spec=SPEC3, auto_reset=False, fixed={"terrain": "walled", "family": "sword", "count": 2})
    obs = b.reset()
    vis = obs[:, 0, ix("opp_visible")] > 0.5
    assert (obs[vis, 0, ix("opp_unseen")] == 0).all()
    assert (obs[:, 0, ix("self_since_hit")] == 1.0).all() and (obs[:, 0, ix("opp_since_hit")] == 1.0).all()
    b.w.hurt(0, np.ones(8, dtype=bool), 2.0, np.full(8, 1))
    b.w.hurt(1, np.ones(8, dtype=bool), 2.0, np.full(8, 0))
    nothing = np.zeros((8, 4, len(SPEC3.action_nvec)), dtype=np.int64)
    nothing[:, :, SPEC3.action_index("forward")] = 1
    obs, r, d, info = b.step(nothing)
    # hurt just before the decision's 2 ticks
    assert np.allclose(obs[:, 0, ix("self_since_hit")], 2 / 100)
    assert np.allclose(obs[:, 0, ix("opp_since_hit")], 2 / 100)
