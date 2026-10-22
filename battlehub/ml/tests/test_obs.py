"""Observations: geometry in the bot's own frame, masking of what it cannot see, scaling and clipping."""
import numpy as np
import pytest

from pvp_sim.actions import decode, encode
from pvp_sim.obs import STATE_KEYS, observe, raw_features
from pvp_sim.spec import SPEC


def state(**kw):
    s = {k: np.zeros(1) for k in STATE_KEYS if k not in ("edges", "walls")}
    s.update({"self_max_health": np.full(1, 20.0), "opp_max_health": np.full(1, 20.0), "self_health": np.full(1, 20.0),
              "opp_health": np.full(1, 20.0), "opp_visible": np.ones(1), "self_on_ground": np.ones(1, dtype=bool),
              "self_sprinting": np.zeros(1, dtype=bool), "opp_on_ground": np.ones(1), "opp_sprinting": np.zeros(1),
              "edges": np.full((1, 8), 4.0), "walls": np.full((1, 8), 4.0)})
    for k, v in kw.items():
        s[k] = np.atleast_1d(np.asarray(v, dtype=np.float64)) if k not in ("edges", "walls") else np.asarray(v, dtype=np.float64)
    return s


def feat(s, name):
    return raw_features(s)[0, SPEC.index(name)]


def test_the_spec_is_consistent():
    assert len(set(SPEC.obs_names)) == SPEC.obs_dim == 43
    assert list(SPEC.action_nvec) == [3, 3, 2, 2, 2, 9, 5]
    assert all(SPEC.obs_scale > 0)


def test_an_opponent_straight_ahead_in_reach():
    s = state(opp_z=2.5, opp_yaw=180.0)
    assert feat(s, "opp_fwd") == pytest.approx(2.5)
    assert feat(s, "opp_left") == pytest.approx(0.0, abs=1e-12)
    assert feat(s, "aim_yaw_sin") == pytest.approx(0.0, abs=1e-12)
    assert feat(s, "aim_yaw_cos") == pytest.approx(1.0)
    assert feat(s, "on_crosshair") == 1.0
    # the chest is 0.42 below the eyes: look down 9.5 degrees
    assert feat(s, "aim_pitch") == pytest.approx(np.degrees(np.arctan2(0.42, 2.5)))
    # it looks straight at me
    assert feat(s, "opp_look_cos") == pytest.approx(1.0)


def test_left_is_left_whatever_the_yaw():
    # yaw 0 looks along +z: +x is to the left
    s = state(opp_x=3.0)
    assert feat(s, "opp_left") == pytest.approx(3.0)
    assert feat(s, "aim_yaw_sin") == pytest.approx(1.0)
    # yaw 90 looks along -x (west): an opponent at -x is straight ahead, one at +z (south) to the left
    s = state(self_yaw=90.0, opp_x=-2.0)
    assert feat(s, "opp_fwd") == pytest.approx(2.0)
    s = state(self_yaw=90.0, opp_z=2.0)
    assert feat(s, "opp_left") == pytest.approx(2.0)
    # velocities in the same frame
    s = state(self_yaw=90.0, self_vx=-0.3, opp_x=-2.0, opp_vz=0.1)
    assert feat(s, "self_vel_fwd") == pytest.approx(0.3)
    assert feat(s, "opp_vel_left") == pytest.approx(0.1)


def test_out_of_reach_no_crosshair():
    assert feat(state(opp_z=3.5), "on_crosshair") == 0.0
    assert feat(state(opp_z=3.1), "on_crosshair") == 1.0
    assert feat(state(opp_z=2.5, self_pitch=-60.0), "on_crosshair") == 0.0


def test_what_it_cannot_see_is_masked():
    s = state(opp_z=-3.0, opp_visible=0.0, opp_vx=0.5, opp_health=4.0, opp_on_ground=1.0, opp_sprinting=1.0, opp_hurt_ticks=7.0, opp_since_swing=2.0)
    assert feat(s, "opp_vel_left") == 0.0
    assert feat(s, "opp_health") == 1.0
    assert feat(s, "opp_ground") == 0.0
    assert feat(s, "opp_sprint") == 0.0
    assert feat(s, "opp_hurt") == 0.0
    assert feat(s, "opp_since_swing") == 20.0
    # but where it was last known still counts
    assert feat(s, "opp_fwd") == pytest.approx(-3.0)


def test_scaled_and_clipped():
    o = observe(state(opp_z=20.0, self_charge=0.5, self_hurt_ticks=5.0, edges=np.array([[4, 3, 2, 1, 0, 9, 4, 4]])))[0]
    assert o[SPEC.index("opp_fwd")] == 1.0
    assert o[SPEC.index("opp_dist")] == pytest.approx(20 / 32)
    assert o[SPEC.index("self_charge")] == 0.5
    assert o[SPEC.index("self_hurt")] == 0.5
    assert o[SPEC.index("edge_3")] == 0.25
    assert o[SPEC.index("edge_5")] == 1.0
    assert np.all(np.abs(o) <= 1.0)


def test_actions_decode_and_encode():
    idx = encode(forward=1, strafe=-1, jump=1, sprint=1, attack=0, yaw=13, pitch=-4)
    d = decode(idx)
    assert (d["forward"][0], d["strafe"][0], d["jump"][0], d["sprint"][0], d["attack"][0], d["yaw"][0], d["pitch"][0]) == (1, -1, 1, 1, 0, 12, -5)
