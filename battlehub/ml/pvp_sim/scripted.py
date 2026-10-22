"""Opponents without learning: the scripted bot (a simple version of the mod's CombatSteering) and random play.

Both work on the observation vector only, so they know no more than the model does.
"""
import numpy as np

from .actions import encode
from .spec import SPEC


class ScriptedPolicy:
    """Turns towards the opponent's chest (no faster than its turn limit), sprints in, strafes and changes direction
    now and then, swings when the crosshair is on the opponent and the charge is up, jumps for a critical hit now and
    then, backs off when too close. `skill` 0..1 sets how sharp it is (aim, timing, strafing)."""

    def __init__(self, skill=0.7, seed=None, spec=SPEC):
        self.skill = skill
        self.spec = spec
        self.rng = np.random.default_rng(seed)
        self.strafe = None

    def __call__(self, obs):
        obs = np.asarray(obs, dtype=np.float64).reshape(-1, self.spec.obs_dim)
        m = obs.shape[0]
        sp, rng, sk = self.spec, self.rng, self.skill
        if self.strafe is None or self.strafe.shape[0] != m:
            self.strafe = rng.choice([-1.0, 1.0], size=m)
        g = lambda name: obs[:, sp.index(name)] * sp.obs_scale[sp.index(name)]
        angle = np.degrees(np.arctan2(g("aim_yaw_sin"), g("aim_yaw_cos")))
        dist = g("opp_dist")
        charge = g("self_charge")
        cross = g("on_crosshair") > 0.5
        visible = g("opp_visible") > 0.5
        # aim: most of the way there, with a little error
        yaw = angle * (0.6 + 0.35 * sk) + rng.normal(size=m) * (1 - sk) * 6
        pitch = g("aim_pitch") * 0.7
        # movement
        forward = np.where(dist > 2.7, 1.0, np.where(dist < 1.3, -1.0, np.where(rng.random(m) < 0.5, 1.0, 0.0)))
        sprint = (dist > 2.7) & (np.abs(angle) < 45)
        flip = rng.random(m) < 0.06 + 0.06 * sk
        self.strafe = np.where(flip, -self.strafe, self.strafe)
        strafe = np.where((dist < 6) & (rng.random(m) < 0.4 + 0.5 * sk), self.strafe, 0.0)
        # clicking: the charge it waits for depends on skill
        ready = 0.95 - 0.35 * (1 - sk) + rng.normal(size=m) * 0.05
        attack = cross & (charge >= ready) & (rng.random(m) < 0.5 + 0.5 * sk)
        jump = (dist < 3.4) & (charge > 0.8) & (rng.random(m) < 0.12 * sk)
        # nothing in view: turn to search
        yaw = np.where(visible, yaw, np.sign(angle + 1e-9) * 60)
        return encode(sp, forward=forward, strafe=strafe, jump=jump.astype(float), sprint=sprint.astype(float),
                      attack=attack.astype(float), yaw=yaw, pitch=pitch)


class RandomPolicy:
    def __init__(self, seed=None, spec=SPEC):
        self.rng = np.random.default_rng(seed)
        self.spec = spec

    def __call__(self, obs):
        m = np.asarray(obs).reshape(-1, self.spec.obs_dim).shape[0]
        return self.rng.integers(0, self.spec.action_nvec, size=(m, len(self.spec.action_nvec)))
