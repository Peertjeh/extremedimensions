"""The 1v1 environments: a batch of arenas (both fighters observe and act) and a Gymnasium env for one learner.

Every decision lasts spec.decision_ticks ticks: movement and jump are held, the click happens on the first tick, the
turn is spread over the ticks. What a fighter sees follows the mod's senses: the opponent is "visible" within the view
half-angle (head, middle or feet) and otherwise known only where it was last located (seen, heard within 12 blocks
while moving, or just hit me); health, velocity and the like only while visible.

Per episode some things are drawn at random so the model does not depend on them (and so the difficulty layer of the
mod can later add them): the arena size, the start, a perception delay of the opponent (0..2 ticks), aim noise and a
turn limit.
"""
from dataclasses import dataclass, field

import gymnasium as gym
import numpy as np

from .actions import decode
from .obs import STATE_KEYS, in_view, observe
from .physics import SWORD, Physics, Rules
from .spec import SPEC

HEARING = 12.0
HIT_MEMORY = 30
HISTORY = 6


@dataclass
class Rewards:
    damage: float = 1.0          # per max health of damage done (and minus for damage taken)
    hit: float = 0.05            # per landed swing more than the opponent (boxing)
    win: float = 10.0
    draw_health: float = 5.0     # at the time limit: times the health difference / max health
    draw_hits: float = 0.1       # at the time limit (boxing): per hit more, at most draw_health
    whiff: float = 0.02          # per swing that missed (the charge is gone)
    time: float = 0.001          # per decision
    unseen: float = 0.005        # per decision without the opponent in view


@dataclass
class Randomize:
    arena_half: tuple = (7.0, 14.0)
    start_distance: tuple = (5.0, 10.0)
    start_yaw_noise: float = 60.0
    opp_delay: tuple = (0, 2)        # ticks
    aim_noise: tuple = (0.0, 1.5)    # degrees per tick (std)
    turn_cap: tuple = (30.0, 60.0)   # degrees per decision


class PvpBatch:
    """n arenas stepped together. step() takes action indices (n, 2, heads) and returns observations (n, 2, obs_dim),
    rewards (n, 2), done (n,) and info; finished arenas start again at once (info['terminal_obs'] holds their last
    observation) unless auto_reset is False."""

    def __init__(self, n, rules: Rules = SWORD, seed=None, max_ticks=1200, rewards: Rewards = None, randomize: Randomize = None,
                 auto_reset=True, spec=SPEC):
        self.n = n
        self.rules = rules
        self.spec = spec
        self.rng = np.random.default_rng(seed)
        self.max_ticks = max_ticks
        self.rewards = rewards or Rewards()
        self.rand = randomize or Randomize()
        self.auto_reset = auto_reset
        self.ph = Physics(n, rules, spec, self.rng)
        self.ticks = np.zeros(n, dtype=np.int64)
        self.opp_delay = np.zeros((n, 2), dtype=np.int64)
        self.aim_noise = np.zeros((n, 2))
        self.turn_cap = np.full((n, 2), 60.0)
        self.known = np.zeros((n, 2, 3))     # where each fighter last located the other
        self.history = []
        self.episode_return = np.zeros((n, 2))

    # ------------------------------------------------------------------ episodes

    def reset(self, idx=None):
        """Starts the given arenas (all by default) again; returns everyone's observations."""
        if idx is None or not self.history:
            self._start(np.arange(self.n))
            self.history = [self.ph.f.snapshot() for _ in range(HISTORY)]
        else:
            self._restart(np.asarray(idx))
        return self.observe()

    def _restart(self, idx):
        if idx.size == 0:
            return
        self._start(idx)
        snap = self.ph.f.snapshot()
        for h in self.history:
            for k, v in h.items():
                v[idx] = snap[k][idx]

    def _start(self, idx):
        r, rnd = self.rand, self.rng
        m = idx.size
        half = rnd.uniform(*r.arena_half, size=m)
        sep = np.minimum(rnd.uniform(*r.start_distance, size=m), 2 * (half - 1.5))
        ang = rnd.uniform(0, 2 * np.pi, size=m)
        room = np.maximum(0.0, half - 1.0 - sep / 2)
        cx = rnd.uniform(-1, 1, size=m) * room
        cz = rnd.uniform(-1, 1, size=m) * room
        x0, z0 = cx - np.cos(ang) * sep / 2, cz - np.sin(ang) * sep / 2
        x1, z1 = cx + np.cos(ang) * sep / 2, cz + np.sin(ang) * sep / 2
        face0 = np.degrees(np.arctan2(-(x1 - x0), z1 - z0))
        face1 = np.degrees(np.arctan2(-(x0 - x1), z0 - z1))
        yaw0 = face0 + rnd.uniform(-1, 1, size=m) * r.start_yaw_noise
        yaw1 = face1 + rnd.uniform(-1, 1, size=m) * r.start_yaw_noise
        self.ph.half[idx] = half
        self.ph.platform[idx] = 0.0
        self.ph.place(idx, x0, z0, x1, z1, yaw0, yaw1)
        self.ticks[idx] = 0
        self.opp_delay[idx] = rnd.integers(r.opp_delay[0], r.opp_delay[1] + 1, size=(m, 2))
        self.aim_noise[idx] = rnd.uniform(*r.aim_noise, size=(m, 2))
        self.turn_cap[idx] = rnd.uniform(*r.turn_cap, size=(m, 2))
        self.known[idx, 0] = np.stack([x1, np.zeros(m), z1], axis=1)
        self.known[idx, 1] = np.stack([x0, np.zeros(m), z0], axis=1)
        self.episode_return[idx] = 0.0

    # ------------------------------------------------------------------ what each fighter knows

    def state(self, side):
        """The CombatState of fighter `side` in every arena (the mod builds the same from its senses)."""
        f, ph, spec = self.ph.f, self.ph, self.spec
        o = 1 - side
        rows = np.arange(self.n)
        d = self.opp_delay[:, side]
        # the opponent as perceived `d` ticks late
        seen_of = {k: np.stack([self.history[-1 - j][k][rows, o] for j in range(HISTORY)], axis=1)[rows, d]
                   for k in ("x", "y", "z", "vx", "vy", "vz", "yaw", "on_ground", "sprinting", "swing_age", "invul", "health")}
        ex, ey, ez = f.x[:, side], f.y[:, side] + spec.eye_height, f.z[:, side]
        visible = np.zeros(self.n, dtype=bool)
        for h in (spec.eye_height, spec.hitbox_height / 2, 0.0):
            visible |= in_view(ex, ey, ez, f.yaw[:, side], f.pitch[:, side], seen_of["x"], seen_of["y"] + h, seen_of["z"], spec)
        dist = np.hypot(seen_of["x"] - f.x[:, side], seen_of["z"] - f.z[:, side])
        moving = np.hypot(seen_of["vx"], seen_of["vz"]) > 0.01
        located = visible | ((dist <= HEARING) & moving) | (f.hit_age[:, side] <= HIT_MEMORY)
        pos = np.stack([seen_of["x"], seen_of["y"], seen_of["z"]], axis=1)
        self.known[:, side] = np.where(located[:, None], pos, self.known[:, side])
        edges, walls = ph.rays(side)
        return {
            "self_x": f.x[:, side], "self_y": f.y[:, side], "self_z": f.z[:, side],
            "self_vx": f.vx[:, side], "self_vy": f.vy[:, side], "self_vz": f.vz[:, side],
            "self_yaw": f.yaw[:, side], "self_pitch": f.pitch[:, side],
            "self_on_ground": f.on_ground[:, side], "self_sprinting": f.sprinting[:, side],
            "self_charge": ph.charge()[:, side], "self_hurt_ticks": np.maximum(0, f.invul[:, side] - 10).astype(np.float64),
            "self_health": f.health[:, side], "self_max_health": np.full(self.n, self.rules.max_health),
            "opp_x": self.known[:, side, 0], "opp_y": self.known[:, side, 1], "opp_z": self.known[:, side, 2],
            "opp_vx": seen_of["vx"], "opp_vy": seen_of["vy"], "opp_vz": seen_of["vz"], "opp_yaw": seen_of["yaw"],
            "opp_on_ground": seen_of["on_ground"], "opp_sprinting": seen_of["sprinting"],
            "opp_since_swing": seen_of["swing_age"].astype(np.float64), "opp_hurt_ticks": np.maximum(0, seen_of["invul"] - 10).astype(np.float64),
            "opp_health": seen_of["health"], "opp_max_health": np.full(self.n, self.rules.max_health),
            "opp_visible": visible.astype(np.float64),
            "edges": edges, "walls": walls,
        }

    def observe(self):
        return np.stack([observe(self.state(s), self.spec) for s in (0, 1)], axis=1).astype(np.float32)

    # ------------------------------------------------------------------ one decision

    def step(self, actions):
        actions = np.asarray(actions, dtype=np.int64).reshape(self.n, 2, -1)
        a = [decode(actions[:, s], self.spec) for s in (0, 1)]
        ph, f, rw = self.ph, self.ph.f, self.rewards
        T = self.spec.decision_ticks
        yaw = np.stack([np.clip(a[s]["yaw"], -self.turn_cap[:, s], self.turn_cap[:, s]) for s in (0, 1)], axis=1)
        pitch = np.stack([a[s]["pitch"] for s in (0, 1)], axis=1)
        get = lambda k: np.stack([a[0][k], a[1][k]], axis=1)
        forward, strafe = get("forward"), get("strafe")
        jump, sprint, attack = get("jump") > 0.5, get("sprint") > 0.5, get("attack") > 0.5
        dealt = np.zeros((self.n, 2))
        landed = np.zeros((self.n, 2))
        whiffs = np.zeros((self.n, 2))
        for t in range(T):
            noise = self.rng.normal(size=(self.n, 2, 2)) * self.aim_noise[:, :, None]
            ph.tick(forward, strafe, jump, sprint, attack & (t == 0), yaw / T + noise[:, :, 0], pitch / T + noise[:, :, 1])
            dealt += ph.dealt
            landed += ph.landed
            whiffs += ph.whiffed
            self.history.append(f.snapshot())
            if len(self.history) > HISTORY:
                self.history.pop(0)
        self.ticks += T
        obs = self.observe()
        visible = np.stack([obs[:, s, self.spec.index("opp_visible")] > 0.5 for s in (0, 1)], axis=1)
        mh = self.rules.max_health
        reward = rw.damage * (dealt - dealt[:, ::-1]) / mh + rw.hit * (landed - landed[:, ::-1]) * (0.0 if self.rules.damage else 1.0)
        reward -= rw.whiff * whiffs + rw.time + rw.unseen * (~visible)
        # the end: dead, fallen, enough hits, or out of time
        dead = (f.health <= 0) | ph.fell
        if self.rules.hits_to_win > 0:
            dead |= f.hits[:, ::-1] >= self.rules.hits_to_win
        timeout = self.ticks >= self.max_ticks
        done = dead.any(axis=1) | timeout
        winner = np.full(self.n, -1)
        winner = np.where(dead[:, 1] & ~dead[:, 0], 0, winner)
        winner = np.where(dead[:, 0] & ~dead[:, 1], 1, winner)
        both = dead[:, 0] & dead[:, 1]
        winner = np.where(both & (f.health[:, 0] > f.health[:, 1]), 0, np.where(both & (f.health[:, 1] > f.health[:, 0]), 1, winner))
        decided = winner >= 0
        sign = np.stack([np.where(winner == 0, 1.0, -1.0), np.where(winner == 1, 1.0, -1.0)], axis=1)
        reward += np.where(decided[:, None], rw.win * sign, 0.0)
        draw = done & ~decided
        if self.rules.damage:
            margin = rw.draw_health * (f.health - f.health[:, ::-1]) / mh
        else:
            margin = np.clip(rw.draw_hits * (f.hits - f.hits[:, ::-1]), -rw.draw_health, rw.draw_health)
        reward += np.where(draw[:, None], margin, 0.0)
        self.episode_return += reward
        info = {"winner": winner, "done": done, "ticks": self.ticks.copy(), "landed": landed, "dealt": dealt,
                "episode_return": self.episode_return.copy()}
        if self.auto_reset and done.any():
            info["terminal_obs"] = obs.copy()
            idx = np.nonzero(done)[0]
            self._restart(idx)
            obs[idx] = self.observe()[idx]
        return obs, reward.astype(np.float32), done, info


class PvpEnv(gym.Env):
    """One learner (fighter 0) against an opponent policy (fighter 1): a callable from observations (m, obs_dim) to
    action indices (m, heads); by default the scripted bot."""

    metadata = {"render_modes": []}

    def __init__(self, opponent=None, rules: Rules = SWORD, seed=None, max_ticks=1200, rewards=None, randomize=None, spec=SPEC):
        super().__init__()
        from .scripted import ScriptedPolicy
        self.combat_spec = spec
        self.batch = PvpBatch(1, rules, seed, max_ticks, rewards, randomize, auto_reset=False, spec=spec)
        self.opponent = opponent if opponent is not None else ScriptedPolicy(seed=seed)
        self.observation_space = gym.spaces.Box(-1.0, 1.0, shape=(spec.obs_dim,), dtype=np.float32)
        self.action_space = gym.spaces.MultiDiscrete(spec.action_nvec)
        self._obs = None

    def reset(self, seed=None, options=None):
        super().reset(seed=seed)
        if seed is not None:
            self.batch.rng = np.random.default_rng(seed)
            self.batch.ph.rng = self.batch.rng
        self._obs = self.batch.reset()
        return self._obs[0, 0].copy(), {}

    def step(self, action):
        opp = np.asarray(self.opponent(self._obs[:, 1]), dtype=np.int64).reshape(1, -1)
        acts = np.stack([np.asarray(action, dtype=np.int64).reshape(1, -1), opp], axis=1)
        obs, reward, done, info = self.batch.step(acts)
        self._obs = obs
        timeout = bool(info["ticks"][0] >= self.batch.max_ticks)
        terminated = bool(done[0]) and not (timeout and info["winner"][0] < 0)
        truncated = bool(done[0]) and not terminated
        return obs[0, 0].copy(), float(reward[0, 0]), terminated, truncated, {"winner": int(info["winner"][0])}
