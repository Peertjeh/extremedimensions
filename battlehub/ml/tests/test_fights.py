"""The mod's fight logs (spec/fightlog_sample.jsonl is written by the mod's FightLogTest from states of the golden file,
with the observation the mod computed): the loader gives the simulation's state back, and the simulation's observation
of it is the mod's."""
import gzip
import json
import os
import zipfile

import numpy as np

from pvp_sim2.obs import STATE2_KEYS, STATE3_KEYS, observe
from pvp_sim2.spec import SPEC2, SPEC3
from train import fights

SAMPLE = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "spec", "fightlog_sample.jsonl")


def sample():
    return next(fights.read(SAMPLE))


def test_the_header_names_the_simulations_state_and_the_specs_actions():
    f = sample()
    h = f.header
    assert h["spec_sha256"] == SPEC3.sha256
    assert h["state_keys"] == STATE3_KEYS
    assert h["action_names"] == SPEC3.action_names
    assert [x["kind"] for x in h["fighters"]] == ["human", "bot"]
    assert len(f.rows) == 20 and len(f.hits) == 1 and f.end["winners"] == [1]


def test_the_simulations_observation_of_a_stored_state_is_the_mods():
    f = sample()
    obs = observe(fights.states(f), SPEC3)
    want = np.array([r["obs"] for r in f.rows])
    assert obs.shape == want.shape == (20, SPEC3.obs_dim)
    np.testing.assert_allclose(obs, want, atol=2e-5)


def test_the_history_comes_from_the_fighters_earlier_rows_and_starts_again_after_a_pause():
    f = sample()
    obs = fights.observations(f)
    now = observe(fights.states(f), SPEC3)
    one = [(k, of) for k, of, ago in SPEC3.history if ago == 1]
    for who in (0, 1):
        rows = [i for i, r in enumerate(f.rows) if r["f"] == who]
        k, of = one[0]
        assert obs[rows[0], k] == now[rows[0], of]          # the first row: the value now
        for a, b in zip(rows, rows[1:]):                     # every 4 ticks: the row before
            assert all(obs[b, k] == obs[a, of] for k, of in one)
    # a pause of more than two decisions starts it again
    late = fights.Fight(f.name, f.header, [dict(r) for r in f.rows[:2]] + [dict(f.rows[2], t=f.rows[0]["t"] + 40)])
    o2 = fights.observations(late)
    assert all(o2[2, k] == o2[2, of] for k, of in one)


def test_a_log_of_spec_v2_reads_with_nobody_else_and_nothing_remembered():
    f = sample()
    keys = [k for k in f.header["state_keys"] if k in STATE2_KEYS]
    idx = [f.header["state_keys"].index(k) for k in keys if k not in f.header["rays"]]
    rays_at = len([k for k in f.header["state_keys"] if k not in f.header["rays"]])
    old = fights.Fight(f.name, dict(f.header, state_keys=keys, spec_sha256=SPEC2.sha256),
                       [dict(r, s=[r["s"][i] for i in idx] + r["s"][rays_at:]) for r in f.rows])
    s = fights.states(old)
    assert not s["ally_present"].any() and (s["opp_unseen"] == 100).all()
    assert fights.observations(old).shape == (20, SPEC3.obs_dim)


def test_actions_become_the_nearest_choice_of_each_head():
    f = sample()
    act = fights.actions(f)
    assert act.shape == (20, len(SPEC3.action_nvec))
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


def test_people_s_steps_in_logs_before_1_41_become_the_game_s_velocities(tmp_path):
    """Logs of 1.37 to 1.40 store a person's velocity as their step of a tick: read as the velocity the game keeps (the
    mod's core/ml/Velocity, VelocityTest's numbers), for the person's own rows and for rows whose target is a person
    and is seen; a bot's velocities stay as they are, and logs of 1.41 on are read as stored."""
    # VelocityTest's numbers: a steady sprint step, standing still, a jump's first tick, the air
    step = 0.13 / (1 - 0.546)
    vx, vy, _ = fights.game_velocity(np.array([step, 0.0, 0.0, 0.3]), np.array([0.0, 0.0, 0.42, 0.0]), np.zeros(4),
                                     np.array([True, True, False, False]))
    np.testing.assert_allclose(vx, [step * 0.546, 0.0, 0.0, 0.91 * 0.3], atol=1e-12)
    np.testing.assert_allclose(vy[:3], [-0.0784, -0.0784, (0.42 - 0.08) * 0.98], atol=1e-12)
    with open(SAMPLE, encoding="utf-8") as src:
        lines = src.read().splitlines()
    head = json.loads(lines[0])
    assert head["velocities"] == "game"
    del head["velocities"]
    old = tmp_path / "old.jsonl"
    old.write_text("\n".join([json.dumps(head)] + lines[1:]) + "\n", encoding="utf-8")
    new, was = sample(), next(fights.read(str(old)))
    a, b = fights.states(new), fights.states(was)
    person = np.array([r["f"] == 0 for r in new.rows])
    sees_person = np.array([r["o"] == 0 for r in new.rows]) & a["opp_visible"]
    ex, ey, ez = fights.game_velocity(a["self_vx"], a["self_vy"], a["self_vz"], a["self_on_ground"], a["self_gravity"])
    np.testing.assert_allclose(b["self_vx"], np.where(person, ex, a["self_vx"]))
    np.testing.assert_allclose(b["self_vy"], np.where(person, ey, a["self_vy"]))
    np.testing.assert_allclose(b["self_vz"], np.where(person, ez, a["self_vz"]))
    ox, oy, oz = fights.game_velocity(a["opp_vx"], a["opp_vy"], a["opp_vz"], a["opp_on_ground"])
    np.testing.assert_allclose(b["opp_vx"], np.where(sees_person, ox, a["opp_vx"]))
    np.testing.assert_allclose(b["opp_vy"], np.where(sees_person, oy, a["opp_vy"]))
    assert person.any() and (~person).any() and sees_person.any()


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
