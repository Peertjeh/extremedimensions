"""Opponents without learning for v2, on the observation only (they know no more than the model):

  Scripted2  v1's scripted bot (turn to the chest, sprint in, strafe, swing charged, back off when too close) with what
             the modes add: it keeps off drops and the void, shoots its bow from afar (leading the pitch for the drop of
             the arrow), eats a golden apple or food when hurt, holds its shield up now and then when the target is
             charged and close, throws a pearl at a far target, and with a mace sends itself up with a wind charge.
             With latch=True (the teacher for behavior cloning, train/bc.py) it keeps a pearl or wind charge in hand
             until it is thrown, aiming first: without it, switching to one costs the charge the wind charge rule
             asks for and the pearl's chance must come up twice in a row, so it hardly ever throws either;
  Random2    random actions;
  Dummy2     walks about without swinging, and stays on its ground.
"""
import numpy as np

from pvp_sim.actions import encode

from .spec import SPEC2

WEAPON, BOW, FOOD, GAPPLE, PEARL, WIND = range(6)


class Scripted2:
    def __init__(self, skill=0.7, seed=None, spec=SPEC2, latch=False):
        self.skill = skill
        self.spec = spec
        self.latch = latch
        self.rng = np.random.default_rng(seed)
        self.strafe = None
        self.block = None

    def __call__(self, obs, skill=None):
        obs = np.asarray(obs, dtype=np.float64).reshape(-1, self.spec.obs_dim)
        m = obs.shape[0]
        sp, rng = self.spec, self.rng
        sk = self.skill if skill is None else np.asarray(skill, dtype=np.float64)
        if self.strafe is None or self.strafe.shape[0] != m:
            self.strafe = rng.choice([-1.0, 1.0], size=m)
            self.block = np.zeros(m)
        g = lambda name: obs[:, sp.index(name)] * sp.obs_scale[sp.index(name)]
        angle = np.degrees(np.arctan2(g("aim_yaw_sin"), g("aim_yaw_cos")))
        dist = g("opp_dist")
        charge = g("self_charge")
        cross = g("on_crosshair") > 0.5
        visible = g("opp_visible") > 0.5
        health = g("self_health")
        hand = np.argmax(np.stack([g("hand_" + h) for h in sp.hands], axis=1), axis=1)
        use_ticks = g("self_use")
        reach = (g("self_reach") + 1.0) * 3.0
        # what to hold
        want = np.full(m, WEAPON)
        bow_ok = (g("self_arrows") > 0) & (dist > 9) & visible & (rng.random(m) < 0.3 + 0.6 * sk)
        want = np.where(bow_ok, BOW, want)
        hurt = (health < 0.45) & (dist > 3.5)
        want = np.where(hurt & (g("self_gapples") > 0), GAPPLE, np.where(hurt & (g("self_food_items") > 0) & (g("self_food") < 20), FOOD, want))
        # the teacher (latch) goes for the throw more often: it is there to show it
        pearl = (g("self_pearls") > 0) & (dist > 18) & visible & (g("pearl_cooldown") <= 0) & (rng.random(m) < (0.25 if self.latch else 0.05))
        want = np.where(pearl, PEARL, want)
        launch = (g("self_winds") > 0) & (g("self_cooldown") > 30) & (dist < 4) & (charge > 0.9) & (g("self_ground") > 0.5) & (rng.random(m) < 0.15)
        want = np.where(launch, WIND, want)
        # keep eating or drawing what was started
        busy = ((hand == FOOD) | (hand == GAPPLE) | (hand == BOW)) & (use_ticks > 0)
        want = np.where(busy, hand, want)
        if self.latch:
            # what it holds to throw it stays in hand until thrown, while the reason still holds (all from the
            # observation: the same bot plays several slots); a pearl it decided on goes before a drawn bow
            keep_pearl = (hand == PEARL) & (g("self_pearls") > 0) & (g("pearl_cooldown") <= 0) & visible & (dist > 12)
            keep_wind = (hand == WIND) & (g("self_winds") > 0) & (g("wind_cooldown") <= 0) & (dist < 6) & (g("self_ground") > 0.5)
            want = np.where(pearl | keep_pearl, PEARL, np.where(keep_wind, WIND, want))
        # aim
        yaw = angle * (0.6 + 0.35 * sk) + rng.normal(size=m) * (1 - sk) * 6
        pitch = g("aim_pitch") * 0.7
        lead = np.where(hand == BOW, -(dist * 0.55 + 0.004 * dist * dist), 0.0)
        pitch = np.where(hand == BOW, g("aim_pitch") + lead, pitch)
        pitch = np.where(want == PEARL, g("aim_pitch") - 6 - dist * 0.6, pitch)
        pitch = np.where(want == WIND, 90.0, pitch)
        # my own pitch, from the target's: the pitch to its chest minus what is left to turn (positive: down)
        my_pitch = np.degrees(np.arctan2(sp.eye_height - (g("opp_up") + sp.chest_height), np.maximum(dist, 0.3))) - g("aim_pitch")
        # movement
        forward = np.where(dist > reach - 0.3, 1.0, np.where(dist < 1.3, -1.0, np.where(rng.random(m) < 0.5, 1.0, 0.0)))
        forward = np.where(hand == BOW, 0.0, forward)
        forward = np.where(hurt & ((hand == FOOD) | (hand == GAPPLE)), -1.0, forward)
        sprint = (forward > 0) & (np.abs(angle) < 45)
        flip = rng.random(m) < 0.06 + 0.06 * sk
        self.strafe = np.where(flip, -self.strafe, self.strafe)
        strafe = np.where((dist < 6) & (rng.random(m) < 0.4 + 0.5 * sk), self.strafe, 0.0)
        # off drops and the void: no step towards an edge that is near
        ahead_drop = np.minimum(g("drop_0"), g("void_0"))
        back_drop = np.minimum(g("drop_4"), g("void_4"))
        left_drop = np.minimum(g("drop_2"), g("void_2"))
        right_drop = np.minimum(g("drop_6"), g("void_6"))
        forward = np.where((forward > 0) & (ahead_drop < 1.0), 0.0, forward)
        forward = np.where((forward < 0) & (back_drop < 1.0), 0.0, forward)
        strafe = np.where((strafe > 0) & (left_drop < 1.0), 0.0, strafe)
        strafe = np.where((strafe < 0) & (right_drop < 1.0), 0.0, strafe)
        sprint &= forward > 0
        # the click
        ready = 0.95 - 0.35 * (1 - sk) + rng.normal(size=m) * 0.05
        attack = (hand == WEAPON) & cross & (charge >= ready) & (rng.random(m) < 0.5 + 0.5 * sk)
        jump = (dist < 3.4) & (charge > 0.8) & (rng.random(m) < 0.12 * sk) & (ahead_drop >= 1.0)
        # the right button
        draw_done = (hand == BOW) & (use_ticks >= 18 + rng.integers(0, 4, size=m)) & (np.abs(angle) < 4)
        use = np.zeros(m, dtype=bool)
        use |= (hand == BOW) & (want == BOW) & ~draw_done
        use |= ((hand == FOOD) | (hand == GAPPLE)) & (want == hand)
        if self.latch:
            use |= (hand == PEARL) & (want == PEARL) & (np.abs(pitch) < 5) & (np.abs(angle) < 8)
            use |= (hand == WIND) & (want == WIND) & (my_pitch > 55)
        else:
            use |= (hand == want) & ((want == PEARL) | (want == WIND)) & (use_ticks <= 0) & (rng.random(m) < 0.5)
        shield = (g("self_shield") > 0.5) & (g("shield_cooldown") <= 0) & (hand == WEAPON)
        self.block = np.where(shield & (dist < 3.5) & visible & (g("opp_since_swing") > 10) & (charge < 0.6) & (rng.random(m) < 0.15 * sk), 8, np.maximum(0, self.block - 1))
        use |= shield & (self.block > 0)
        # nothing in view: turn to look for it
        yaw = np.where(visible, yaw, np.sign(angle + 1e-9) * 60)
        return encode(sp, forward=forward, strafe=strafe, jump=jump.astype(float), sprint=sprint.astype(float),
                      attack=attack.astype(float), yaw=yaw, pitch=np.clip(pitch, -15, 15), hand=want.astype(float),
                      use=use.astype(float))


class Random2:
    def __init__(self, seed=None, spec=SPEC2):
        self.rng = np.random.default_rng(seed)
        self.spec = spec

    def __call__(self, obs):
        m = np.asarray(obs).reshape(-1, self.spec.obs_dim).shape[0]
        return self.rng.integers(0, self.spec.action_nvec, size=(m, len(self.spec.action_nvec)))


class Dummy2:
    """A target to learn on: walks about (changing direction now and then), keeps off edges and never swings."""

    def __init__(self, seed=None, spec=SPEC2):
        self.rng = np.random.default_rng(seed)
        self.spec = spec
        self.move = None

    def __call__(self, obs):
        obs = np.asarray(obs, dtype=np.float64).reshape(-1, self.spec.obs_dim)
        m = obs.shape[0]
        sp, rng = self.spec, self.rng
        if self.move is None or self.move.shape[0] != m:
            self.move = rng.integers(0, 3, size=(m, 2)) - 1
        change = rng.random(m) < 0.05
        self.move[change] = rng.integers(0, 3, size=(int(change.sum()), 2)) - 1
        g = lambda name: obs[:, sp.index(name)] * sp.obs_scale[sp.index(name)]
        fwd = np.where((self.move[:, 0] > 0) & (np.minimum(g("drop_0"), g("void_0")) < 1.5), -1, self.move[:, 0])
        fwd = np.where((fwd < 0) & (np.minimum(g("drop_4"), g("void_4")) < 1.5), 1, fwd)
        st = np.where((self.move[:, 1] > 0) & (np.minimum(g("drop_2"), g("void_2")) < 1.5), -1, self.move[:, 1])
        st = np.where((st < 0) & (np.minimum(g("drop_6"), g("void_6")) < 1.5), 1, st)
        self.move[:, 0], self.move[:, 1] = fwd, st
        return encode(sp, forward=fwd, strafe=st, yaw=rng.choice([-4.0, 0.0, 0.0, 4.0], size=m))
