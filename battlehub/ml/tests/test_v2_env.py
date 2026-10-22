"""Spec v2's terrain rays and observations, and the v2 environments."""
import numpy as np
import pytest
from gymnasium.utils.env_checker import check_env

from pvp_sim2 import Batch2, Dummy2, PvpEnv2, Random2, Scripted2, SPEC2
from pvp_sim2.env import Scenario
from pvp_sim2.obs import observe
from pvp_sim2.terrain import WALL, Terrain, generate, GENERATORS

R0 = np.array([0])


def one(v):
    return np.array([float(v)])


def test_the_rays_see_walls_steps_drops_and_the_void_in_the_bots_own_directions():
    t = Terrain(1)
    t.fill_box(0, -10, -10, 10, 10, 0.0)
    t.fill_box(0, -10, 2, 10, 3, 1.0)       # a block 1.5 ahead (z 2..3): a step, not a wall
    t.fill_box(0, -10, -3, 10, -2, WALL)    # a wall 2.5 behind
    t.ground[0][t.size // 2 + 3:, :] = np.nan  # the void from x = 3 on: to the left when looking along +z
    t.fill_box(0, -10, -10, -3, 10, -3.0)   # a drop of 3 from x = -3 on (to the right)
    voids, drops, steps, walls = t.rays(R0, one(0.5), one(0.0), one(0.5), one(0.0))
    s = SPEC2.sample
    assert steps[0, 0] == pytest.approx(1.25, abs=1e-9)        # the last sample before the block at 1.5
    assert walls[0, 0] == SPEC2.ray_max            # a 1-block step is not a wall
    assert walls[0, 4] == pytest.approx(2.5, abs=s)
    assert voids[0, 2] == pytest.approx(2.5, abs=s)  # left: +x, the void at x = 3
    assert drops[0, 2] == pytest.approx(2.5, abs=s)
    assert voids[0, 6] == SPEC2.ray_max            # right: the drop of 3 is not the void
    assert drops[0, 6] == pytest.approx(3.5, abs=s)
    # below, and behind someone
    assert t.below(R0, one(0.5), one(2.0), one(0.5))[0] == pytest.approx(2.0)
    assert t.below(R0, one(5.5), one(0.0), one(0.5))[0] == SPEC2.depth
    drop_b, void_b = t.behind(R0, one(1.5), one(0.0), one(0.5), one(1.0), one(0.0))
    assert void_b[0] == pytest.approx(1.5, abs=s)


def test_a_wall_blocks_the_sight():
    t = Terrain(1)
    t.fill_box(0, -10, -10, 10, 10, 0.0)
    assert t.sight(R0, one(0.5), one(1.6), one(0.5), one(0.5), one(1.6), one(8.5))[0]
    t.fill_box(0, -10, 4, 10, 5, WALL)
    assert not t.sight(R0, one(0.5), one(1.6), one(0.5), one(0.5), one(1.6), one(8.5))[0]


def test_every_map_has_room_to_stand():
    rng = np.random.default_rng(5)
    t = Terrain(1)
    for kind in GENERATORS:
        for _ in range(5):
            generate(t, 0, kind, rng)
            assert t.standable(0).sum() >= 20, kind


def _batch(**fixed):
    b = Batch2(8, seed=2, fixed=fixed)
    return b, b.reset()


def test_observations_are_finite_and_scaled_in_every_scenario():
    b = Batch2(32, seed=1)
    obs = b.reset()
    pol = Scripted2(seed=3)
    for _ in range(80):
        assert np.isfinite(obs).all()
        assert (np.abs(obs) <= SPEC2.obs_clip + 1e-6).all()
        obs, r, d, info = b.step(np.stack([pol(obs[:, k]) for k in range(3)], axis=1))
        assert np.isfinite(r).all()


def test_the_kit_shows_in_the_observation():
    b, obs = _batch(terrain="walled", family="sword", count=2)
    sp = SPEC2
    g = lambda name: obs[0, 0, sp.index(name)] * sp.obs_scale[sp.index(name)]
    assert g("self_dmg") == pytest.approx(3.78 / 20, abs=1e-3)   # a diamond sword on full iron
    assert g("opp_dmg") == pytest.approx(3.78 / 20, abs=1e-3)
    assert g("self_cooldown") == pytest.approx(12.5)
    assert g("self_gapples") == pytest.approx(3)
    assert g("hand_weapon") == 1.0 and g("hand_bow") == 0.0
    assert g("damage_on") == 1.0
    assert g("e2_dist") == pytest.approx(32.0)                  # no second enemy in a duel
    assert g("threat_time") == pytest.approx(20.0)


def test_against_two_the_second_enemy_is_known():
    b, obs = _batch(terrain="walled", family="sword", count=3, mode="1v2")
    sp = SPEC2
    assert (b.target[:, 0] > 0).all()
    assert (b.second[:, 0] > 0).all()
    assert (b.second[:, 0] != b.target[:, 0]).all()
    # team mates are nobody's target
    assert (b.target[:, 1] == 0).all() and (b.target[:, 2] == 0).all()
    assert (obs[:, 0, sp.index("e2_dist")] < 1.0).all()


def test_an_arrow_coming_at_me_is_the_threat():
    b, obs = _batch(terrain="walled", family="bow", count=2)
    f, p = b.w.f, b.w.p
    # an arrow 6 blocks in front of fighter 0's chest, coming straight at it
    yaw = np.radians(f.yaw[0, 0])
    fx, fz = -np.sin(yaw), np.cos(yaw)
    p.active[0, 0] = True
    p.owner[0, 0] = 1
    p.kind[0, 0] = 0
    p.x[0, 0], p.y[0, 0], p.z[0, 0] = f.x[0, 0] + 6 * fx, f.y[0, 0] + 1.2, f.z[0, 0] + 6 * fz
    p.vx[0, 0], p.vy[0, 0], p.vz[0, 0] = -2 * fx, 0.0, -2 * fz
    st = b.state(0)
    o = observe(st)
    sp = SPEC2
    g = lambda name: o[0, sp.index(name)] * sp.obs_scale[sp.index(name)]
    assert g("threat_time") == pytest.approx(3.0, abs=1e-6)
    assert g("threat_miss") == pytest.approx(0.0, abs=1e-6)
    assert g("threat_fwd") == pytest.approx(1.0, abs=1e-6)


def test_the_scripted_bot_beats_random_play_and_a_duel_ends():
    b = Batch2(64, seed=4, fixed={"terrain": "walled", "family": "sword", "count": 2})
    obs = b.reset()
    bot, rnd = Scripted2(seed=1), Random2(seed=2)
    res = np.zeros(3)
    for _ in range(400):
        acts = np.stack([bot(obs[:, 0]), rnd(obs[:, 1]), rnd(obs[:, 2])], axis=1)
        obs, r, d, info = b.step(acts)
        for w in info["winner"][d]:
            res[0 if w == 0 else 1 if w == 1 else 2] += 1
    assert res.sum() >= 30
    assert res[0] / res.sum() > 0.8


def test_the_dummy_stays_on_a_bridge():
    b = Batch2(32, seed=5, fixed={"terrain": "bridge", "family": "sword", "count": 2})
    obs = b.reset()
    dummy = Dummy2(seed=1)
    falls = 0
    for _ in range(150):
        obs, r, d, info = b.step(np.stack([dummy(obs[:, k]) for k in range(3)], axis=1))
        falls += int(info["died"].sum())
    assert falls <= 6


def test_the_gym_env_passes_the_checker():
    env = PvpEnv2(seed=0)
    check_env(env, skip_render_check=True)
    obs, _ = env.reset(seed=1)
    assert obs.shape == (SPEC2.obs_dim,)
