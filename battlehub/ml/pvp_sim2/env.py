"""The v2/v3 environments: n arenas with 2 to 4 fighters each, on the maps of the modes, with their kits and items.

Every decision lasts spec.decision_ticks ticks: movement, jump, the hand and the right button are held, the click
happens on the first tick (an opponent's, now and then, on a later one: Scenario.late_click), the turn is spread over
the ticks.

What a fighter knows follows the mod's senses, for every other fighter: visible within the view half-angle (head,
middle or feet) with nothing of the terrain in between; otherwise known where it was last located (seen, heard within
12 blocks while it moved, or just hit me). Its target is the nearest enemy it knows of (it sticks to it unless another
is clearly nearer, as the mod's targeting does); the next nearest is the second enemy of the observation, the one
after that the third (v3). Team mates are always known where they are (their name tags show through walls); the
nearest is the team mate of the observation (v3).

Per episode a scenario is drawn (Scenario): the map (terrain.GENERATORS), the kits (kits.FAMILIES) with now and then
Chaos' effects, and who fights (TEAMS): 1v1, the learner against two, three against each other, the learner with a
team mate against one, and with four fighters (k=4) two against two, the learner in a team of three against one, or
four against each other. Team mates start close together. `breadth` (0..1) widens it from flat duels to everything,
for a curriculum. Also per fighter: a perception delay of the others (0..2 ticks), aim noise and a turn limit.

Slot 0 is the learner; an arena's episode ends when its team is dead (with team mates it goes on after the learner's
death: a dead fighter's choices do nothing and its masks allow only "nothing"), when its enemies are all dead, or at
the time limit. Rewards are shared within a team (Rewards2.team_spirit).

With a history in the spec (v3), the observations of earlier decisions come from the observations this Batch2 gave
before (the same fighter); at the start of a fight they are the values now.
"""
from dataclasses import dataclass, field

import gymnasium as gym
import numpy as np

from pvp_sim.actions import decode
from pvp_sim.obs import in_view

from . import kits as K
from .obs import observe
from .physics import ARROW, World
from .spec import SPEC2
from .terrain import generate

HEARING = 12.0
HIT_MEMORY = 30
HISTORY = 6
MEMORY_CAP = 1000
# who is in which team, per mode (slot 0 is the learner); the number of fighters is the length
TEAMS = {"1v1": [0, 1], "1v2": [0, 1, 1], "ffa": [0, 1, 2], "2v1": [0, 1, 0],
         "2v2": [0, 1, 0, 1], "3v1": [0, 1, 0, 0], "ffa4": [0, 1, 2, 3]}


@dataclass
class Rewards2:
    damage: float = 1.0       # per 20 health: damage done to enemies, minus damage taken, plus health won back
    kill: float = 4.0         # an enemy died that I hit last
    win: float = 8.0          # my team is the one left
    death: float = 10.0
    hit: float = 0.05         # boxing: per landed swing (minus per swing taken)
    draw_health: float = 5.0  # at the time limit: times the health difference to the enemies / 20
    timeout: float = 1.0
    time: float = 0.001
    unseen: float = 0.005
    aim: float = 0.002
    close: float = 0.5        # potential-based shaping: getting closer to the target ...
    face: float = 0.2         # ... and turning towards it
    team_spirit: float = 0.3  # share of a fighter's reward that is its team's average (alone: its own)


@dataclass
class Scenario:
    terrains: dict = field(default_factory=lambda: {"walled": 1.0, "platform": 0.6, "bridge": 1.0, "islands": 1.0, "town": 1.0, "hills": 0.5})
    families: dict = field(default_factory=lambda: {"sword": 1.0, "axe": 0.5, "mace": 0.35, "bow": 0.35, "koth": 0.3, "ctf": 0.3,
                                                    "lms": 0.4, "ffa": 0.5, "bridge": 0.8, "boxing": 0.3, "build_uhc": 0.4,
                                                    "sumo": 0.4, "bedwars": 0.9, "loot": 1.5})
    three: float = 0.3         # share of arenas with three fighters (at full breadth)
    modes3: dict = field(default_factory=lambda: {"1v2": 0.4, "ffa": 0.4, "2v1": 0.2})
    four: float = 0.25         # share of arenas with four fighters (at full breadth, k >= 4)
    modes4: dict = field(default_factory=lambda: {"2v2": 0.6, "ffa4": 0.25, "3v1": 0.15})
    chaos: float = 0.2         # share of fighters with Chaos effects (kits.chaos: the game's own events and profiles)
    game_effects: bool = True  # False: Chaos as the simulation of 1.40 dealt it (scenario_1_40)
    opp_delay: tuple = (0, 2)
    # share of the other fighters' clicks (slots 1..) that fall on a later tick of the decision, as a person's click can
    # (the model's own, as the mod does it, is on the first)
    late_click: float = 0.5
    aim_noise: tuple = (0.0, 1.5)
    turn_cap: tuple = (30.0, 60.0)
    yaw_noise: float = 60.0
    # maps that go with a kit, most of the time
    paired: dict = field(default_factory=lambda: {"sumo": ["platform"], "boxing": ["walled", "platform"], "bridge": ["bridge"],
                                                  "bedwars": ["bridge", "islands"], "loot": ["islands", "town"],
                                                  "mace": ["walled", "hills"], "build_uhc": ["walled", "hills"]})
    paired_share: float = 0.6
    # share of the arenas with a kit from chests or the shop (kits.ITEM_KITS) in which everyone gets more items on top
    # (kits.extra_items), for learning to use them; 0 deals the mod's kits only
    extra_items: float = 0.0


def scenario_1_40():
    """The simulation as it was in 1.40 (before ML phase 3D): Chaos for an eighth of the fighters with its own random
    effects, and every click on a decision's first tick. For comparing models in the simulation they trained in."""
    return Scenario(chaos=0.12, game_effects=False, late_click=0.0)


def _pick(rng, weights):
    keys = list(weights)
    w = np.array([weights[k] for k in keys], dtype=np.float64)
    return keys[int(rng.choice(len(keys), p=w / w.sum()))]


class Batch2:
    """n arenas stepped together. step() takes action indices (n, K, heads) and returns observations (n, K, obs_dim),
    rewards (n, K), done (n,) and info; finished arenas start again at once (info['terminal_obs'] holds their last
    observation) unless auto_reset is False."""

    def __init__(self, n, k=3, seed=None, max_ticks=1600, rewards: Rewards2 = None, scenario: Scenario = None, breadth=1.0,
                 auto_reset=True, spec=SPEC2, fixed=None):
        self.n, self.k = n, k
        self.spec = spec
        self.rng = np.random.default_rng(seed)
        self.max_ticks = max_ticks
        self.rewards = rewards or Rewards2()
        self.scenario = scenario or Scenario()
        self.breadth = breadth
        self.shaping = 1.0
        self.auto_reset = auto_reset
        self.fixed = fixed or {}   # e.g. {"terrain": "walled", "family": "sword", "count": 2}: for tests and evaluation
        self.w = World(n, k, spec=spec, rng=self.rng)
        self.ticks = np.zeros(n, dtype=np.int64)
        self.opp_delay = np.zeros((n, k), dtype=np.int64)
        self.aim_noise = np.zeros((n, k))
        self.turn_cap = np.full((n, k), 60.0)
        self.known = np.zeros((n, k, k, 3))
        self.located = np.zeros((n, k, k), dtype=bool)
        self.visible = np.zeros((n, k, k), dtype=bool)
        self.target = np.full((n, k), -1, dtype=np.int64)
        self.second = np.full((n, k), -1, dtype=np.int64)
        self.third = np.full((n, k), -1, dtype=np.int64)
        self.ally = np.full((n, k), -1, dtype=np.int64)
        self.unseen = np.full((n, k, k), 100, dtype=np.int64)   # ticks since i last saw j
        self.v3 = "ally_fwd" in spec.obs_names
        self.hist = []      # the observations of the last decisions (newest first), for the spec's history
        self.history = []
        self.episode_return = np.zeros((n, k))
        self.phi = np.zeros((n, k))
        self.info_terrain = [""] * n
        self.info_family = [""] * n
        self.info_mode = [""] * n
        self.damage_on = np.ones(n, dtype=bool)

    # ------------------------------------------------------------------ episodes

    def reset(self, idx=None):
        start = np.ones(self.n, dtype=bool)
        if idx is None or not self.history:
            for a in range(self.n):
                self._start(a)
            self.history = [self.w.f.snapshot() for _ in range(HISTORY)]
            self.ticks[:] = 0
            self.hist = []
        else:
            start = np.zeros(self.n, dtype=bool)
            start[np.asarray(idx)] = True
            self._restart(np.asarray(idx))
        self._perceive()
        obs = self.observe(start)
        self._remember(obs, start, push=False)
        self.phi = self.potential(obs)
        self.last_obs = obs
        return obs

    def action_masks(self, obs=None):
        """Which choices each fighter may make now (pvp_sim2/masks.py): booleans (n, K, sum of the heads' sizes); a
        dead fighter (whose team fights on) may only do nothing."""
        from .masks import MASKS2, dead_mask
        obs = self.last_obs if obs is None else obs
        m = MASKS2(obs[..., :MASKS2.spec.obs_dim])
        dead = self.w.f.active & ~self.w.f.alive
        if dead.any():
            m[dead] = dead_mask()
        return m

    def _remember(self, obs, start, push=True):
        """The spec's history: this decision's observations become the earlier ones (push); where `start` (arenas) a
        fight starts, and every earlier one is the observation now."""
        ago = self.spec.max_ago
        if not ago:
            return
        if not self.hist:
            self.hist = [obs.copy() for _ in range(ago)]
            return
        if push:
            self.hist = [obs.copy()] + self.hist[:-1]
        for h in self.hist:
            h[start] = obs[start]

    def _restart(self, idx):
        if idx.size == 0:
            return
        for a in idx:
            self._start(int(a))
        snap = self.w.f.snapshot()
        for h in self.history:
            for key, v in h.items():
                v[idx] = snap[key][idx]

    def _start(self, a):
        sc, rng, w = self.scenario, self.rng, self.w
        b = self.breadth
        fam_w = {k: (v if k == "sword" else v * b) for k, v in sc.families.items()}
        family = self.fixed.get("family") or _pick(rng, fam_w)
        if "terrain" in self.fixed:
            terrain = self.fixed["terrain"]
        elif family in sc.paired and rng.random() < sc.paired_share * b:
            terrain = str(rng.choice(sc.paired[family]))
        else:
            terrain = _pick(rng, {k: (v if k == "walled" else v * b) for k, v in sc.terrains.items()})
        count = self.fixed.get("count")
        if not count:
            u = rng.random()
            four = sc.four * b if self.k >= 4 else 0.0
            count = 4 if u < four else 3 if (self.k >= 3 and u < four + sc.three * b) else 2
        mode = "1v1" if count == 2 else (self.fixed.get("mode") or _pick(rng, sc.modes3 if count == 3 else sc.modes4))
        teams = TEAMS[mode]
        kits = K.deal(family, count, rng, chaos_p=sc.chaos * b, game=sc.game_effects)
        if sc.extra_items > 0 and family in K.ITEM_KITS and rng.random() < sc.extra_items:
            kits = [K.extra_items(dict(k), rng) for k in kits]
        far = family in ("bow", "lms", "ctf", "build_uhc", "ffa")
        # team mates start together (the old way for the modes without them, so their fights stay as they were)
        together = mode in ("2v1", "2v2", "3v1", "ffa4")
        spawns = (lambda d: self._team_spawns(a, teams, d)) if together else (lambda d: self._spawns(a, count, d))
        for attempt in range(40):
            generate(w.terrain, a, terrain, rng)
            spots = spawns((6.0, 24.0) if far else (4.0, 14.0))
            if spots is not None:
                break
        else:
            generate(w.terrain, a, "walled", rng)
            terrain = "walled"
            spots = spawns((4.0, 10.0))
        w.clear_projectiles(a)
        for slot in range(self.k):
            if slot < count:
                x, y, z = spots[slot]
                w.place(a, slot, x, y, z, 0.0, kits[slot], teams[slot])
            else:
                w.remove(a, slot)
        # face the nearest enemy, roughly
        for slot in range(count):
            others = [o for o in range(count) if teams[o] != teams[slot]]
            o = min(others, key=lambda o: np.hypot(spots[o][0] - spots[slot][0], spots[o][2] - spots[slot][2]))
            dx, dz = spots[o][0] - spots[slot][0], spots[o][2] - spots[slot][2]
            w.f.yaw[a, slot] = np.degrees(np.arctan2(-dx, dz)) + rng.uniform(-1, 1) * sc.yaw_noise
        w.natural_regen[a] = family != "build_uhc"
        w.hits_to_win[a] = 20 if family == "boxing" else 0
        self.damage_on[a] = family not in ("boxing", "sumo")
        self.ticks[a] = 0
        self.opp_delay[a] = rng.integers(sc.opp_delay[0], sc.opp_delay[1] + 1, size=self.k)
        self.aim_noise[a] = rng.uniform(*sc.aim_noise, size=self.k)
        self.turn_cap[a] = rng.uniform(*sc.turn_cap, size=self.k)
        for i in range(self.k):
            for j in range(self.k):
                self.known[a, i, j] = (w.f.x[a, j], w.f.y[a, j], w.f.z[a, j])
        self.located[a] = True
        self.target[a] = -1
        self.second[a] = -1
        self.third[a] = -1
        self.ally[a] = -1
        self.unseen[a] = 100
        self.episode_return[a] = 0.0
        self.info_terrain[a], self.info_family[a], self.info_mode[a] = terrain, family, mode

    def _spawns(self, a, count, dist):
        t, rng = self.w.terrain, self.rng
        ok = t.standable(a)
        cells = np.argwhere(ok)
        if len(cells) < count * 4:
            return None
        hs = t.size // 2
        for _ in range(60):
            pick = cells[rng.choice(len(cells), size=count, replace=False)]
            pts = [(i - hs + 0.5, t.ground[a, i, j], j - hs + 0.5) for i, j in pick]
            d01 = np.hypot(pts[0][0] - pts[1][0], pts[0][2] - pts[1][2])
            if not dist[0] <= d01 <= dist[1]:
                continue
            if count == 3:
                d = [np.hypot(pts[2][0] - p[0], pts[2][2] - p[2]) for p in pts[:2]]
                if min(d) < 4.0 or min(d) > dist[1] + 6:
                    continue
            return pts
        return None

    def _team_spawns(self, a, teams, dist):
        """Spots for fighters in teams: each team's first fighter apart from the others (the learner's nearest enemy
        `dist` away, the rest at least 4 blocks from each other), its team mates 1.5 to 5 blocks from it."""
        t, rng = self.w.terrain, self.rng
        cells = np.argwhere(t.standable(a))
        count = len(teams)
        if len(cells) < count * 4:
            return None
        hs = t.size // 2
        xz = cells[:, [0, 1]].astype(np.float64) - hs + 0.5
        for _ in range(80):
            at = [None] * count
            lead = {}
            ok = True
            for slot, tm in enumerate(teams):
                if tm in lead:
                    d = np.hypot(*(xz - xz[at[lead[tm]]]).T)
                    near = (d >= 1.5) & (d <= 5.0)
                    for o in at:
                        if o is not None:
                            near &= np.hypot(*(xz - xz[o]).T) >= 1.0
                    pick = np.nonzero(near)[0]
                    if pick.size == 0:
                        ok = False
                        break
                    at[slot] = int(rng.choice(pick))
                    continue
                c = int(rng.integers(len(cells)))
                others = [at[lead[o]] for o in lead]
                if others:
                    d = [np.hypot(*(xz[c] - xz[o])) for o in others]
                    first_enemy = len(lead) == 1
                    if min(d) < 4.0 or d[0] > dist[1] + 6 or (first_enemy and not dist[0] <= d[0] <= dist[1]):
                        ok = False
                        break
                lead[tm] = slot
                at[slot] = c
            if ok:
                return [(xz[c][0], t.ground[a, cells[c][0], cells[c][1]], xz[c][1]) for c in at]
        return None

    # ------------------------------------------------------------------ what each fighter knows

    def _seen(self, i):
        """The others as fighter i perceives them, its delay late: dict of arrays (n, K)."""
        rows = np.arange(self.n)
        d = self.opp_delay[:, i]
        out = {}
        for key in ("x", "y", "z", "vx", "vy", "vz", "yaw", "on_ground", "sprinting", "swing_age", "invul", "health",
                    "absorption", "hand", "using", "use_ticks", "shield", "shield_cd", "scale", "alive"):
            stack = np.stack([self.history[-1 - j][key] for j in range(HISTORY)], axis=1)  # (n, H, K)
            out[key] = stack[rows, d]
        return out

    def _perceive(self):
        """Who sees and knows where whom is, and everyone's target and second enemy."""
        f, t, sp = self.w.f, self.w.terrain, self.spec
        rows = np.arange(self.n)
        self.seen = [self._seen(i) for i in range(self.k)]
        for i in range(self.k):
            s = self.seen[i]
            ex, ey, ez = f.x[:, i], f.y[:, i] + f.eye()[:, i], f.z[:, i]
            for j in range(self.k):
                if j == i:
                    continue
                vis = np.zeros(self.n, dtype=bool)
                there = f.active[:, j] & s["alive"][:, j] & f.alive[:, i]
                for frac in (0.9, 0.5, 0.0):
                    py = s["y"][:, j] + 1.8 * s["scale"][:, j] * frac
                    view = there & ~vis & in_view(ex, ey, ez, f.yaw[:, i], f.pitch[:, i], s["x"][:, j], py, s["z"][:, j], sp)
                    r = np.nonzero(view)[0]
                    if r.size:
                        vis[r] = t.sight(r, ex[r], ey[r], ez[r], s["x"][r, j], py[r], s["z"][r, j])
                dist = np.hypot(s["x"][:, j] - f.x[:, i], s["z"][:, j] - f.z[:, i])
                moving = np.hypot(s["vx"][:, j], s["vz"][:, j]) > 0.01
                loc = vis | ((dist <= HEARING) & moving & s["alive"][:, j]) | (self.w.hit_by_age[:, i, j] <= HIT_MEMORY)
                pos = np.stack([s["x"][:, j], s["y"][:, j], s["z"][:, j]], axis=1)
                self.known[:, i, j] = np.where(loc[:, None], pos, self.known[:, i, j])
                self.visible[:, i, j] = vis
                self.located[:, i, j] = loc
                self.unseen[:, i, j] = np.where(vis, 0, self.unseen[:, i, j])
        # targets
        for i in range(self.k):
            score = np.full((self.n, self.k), np.inf)
            for j in range(self.k):
                if j == i:
                    continue
                enemy = f.active[:, j] & f.alive[:, j] & (f.team[:, j] != f.team[:, i])
                d = np.hypot(self.known[:, i, j, 0] - f.x[:, i], self.known[:, i, j, 2] - f.z[:, i])
                score[:, j] = np.where(enemy, d + np.where(self.located[:, i, j], 0.0, 6.0), np.inf)
            best = np.argmin(score, axis=1)
            cur = self.target[:, i]
            cur_score = np.where(cur >= 0, score[rows, np.maximum(cur, 0)], np.inf)
            keep = np.isfinite(cur_score) & (cur_score <= score[rows, best] + 3.0)
            tgt = np.where(keep, cur, np.where(np.isfinite(score[rows, best]), best, -1))
            self.target[:, i] = tgt
            sc2 = score.copy()
            sc2[rows, np.maximum(tgt, 0)] = np.inf
            sec = np.argmin(sc2, axis=1)
            self.second[:, i] = np.where(np.isfinite(sc2[rows, sec]), sec, -1)
            sc2[rows, sec] = np.inf
            third = np.argmin(sc2, axis=1)
            self.third[:, i] = np.where(np.isfinite(sc2[rows, third]), third, -1)
            # the nearest team mate (always known where it is)
            mate = np.full((self.n, self.k), np.inf)
            for j in range(self.k):
                if j != i:
                    there = f.active[:, j] & f.alive[:, j] & (f.team[:, j] == f.team[:, i])
                    mate[:, j] = np.where(there, np.hypot(f.x[:, j] - f.x[:, i], f.z[:, j] - f.z[:, i]), np.inf)
            best_mate = np.argmin(mate, axis=1)
            self.ally[:, i] = np.where(np.isfinite(mate[rows, best_mate]), best_mate, -1)

    def state(self, i):
        """The CombatState (v2) of fighter i in every arena (the mod builds the same from its senses)."""
        f, w, sp = self.w.f, self.w, self.spec
        rows = np.arange(self.n)
        tg = np.maximum(self.target[:, i], 0)
        s = self.seen[i]
        g = lambda arr: arr[rows, tg]
        vis = self.visible[rows, i, tg] & (self.target[:, i] >= 0)
        weapon = f.hand == 0
        # what i perceives of its target (perceived values, their delay late)
        o_hand = g(s["hand"])
        o_using = g(s["using"])
        o_block = o_using & (o_hand == 0) & g(s["shield"]) & (g(s["use_ticks"]) >= 5) & (g(s["shield_cd"]) == 0)
        st = {
            "self_x": f.x[:, i], "self_y": f.y[:, i], "self_z": f.z[:, i],
            "self_vx": f.vx[:, i], "self_vy": f.vy[:, i], "self_vz": f.vz[:, i],
            "self_yaw": f.yaw[:, i], "self_pitch": f.pitch[:, i],
            "self_on_ground": f.on_ground[:, i], "self_sprinting": f.sprinting[:, i],
            "self_charge": f.charge()[:, i], "self_hurt_ticks": np.maximum(0, f.invul[:, i] - 10).astype(np.float64),
            "self_health": f.health[:, i], "self_max_health": np.full(self.n, 20.0), "self_absorption": f.absorption[:, i],
            "self_food": f.food[:, i], "self_saturation": f.saturation[:, i], "self_regen": self.regen_rate(i),
            "self_fall": f.fall[:, i], "self_scale": f.scale[:, i], "self_reach": f.reach[:, i],
            "self_melee_damage": f.atk_damage[:, i] + f.strength[:, i], "self_melee_bonus": f.sharp[:, i],
            "self_cooldown": 20.0 / f.atk_speed[:, i], "self_kb": f.kb_level[:, i].astype(np.float64),
            "self_armor": f.armor[:, i], "self_toughness": f.toughness[:, i], "self_resistance": f.resistance[:, i].astype(np.float64),
            "self_walk_speed": f.walk_speed[:, i], "self_jump_power": f.jump_power[:, i], "self_gravity": f.gravity[:, i],
            "self_arrows": np.where(f.bow_power[:, i] >= 0, f.arrows[:, i], 0).astype(np.float64),
            "self_food_items": f.food_items[:, i].astype(np.float64), "self_gapples": f.gapples[:, i].astype(np.float64),
            "self_pearls": f.pearls[:, i].astype(np.float64), "self_winds": f.winds[:, i].astype(np.float64),
            "self_shield": f.shield[:, i], "self_pearl_cd": f.pearl_cd[:, i].astype(np.float64),
            "self_wind_cd": f.wind_cd[:, i].astype(np.float64), "self_shield_cd": f.shield_cd[:, i].astype(np.float64),
            "self_hand": f.hand[:, i], "self_use_ticks": np.where(f.using[:, i], f.use_ticks[:, i], 0).astype(np.float64),
            "self_blocking": f.blocking()[:, i],
            "opp_x": self.known[rows, i, tg, 0], "opp_y": self.known[rows, i, tg, 1], "opp_z": self.known[rows, i, tg, 2],
            "opp_vx": g(s["vx"]), "opp_vy": g(s["vy"]), "opp_vz": g(s["vz"]), "opp_yaw": g(s["yaw"]),
            "opp_on_ground": g(s["on_ground"]), "opp_sprinting": g(s["sprinting"]),
            "opp_since_swing": g(s["swing_age"]).astype(np.float64),
            "opp_hurt_ticks": np.maximum(0, g(s["invul"]) - 10).astype(np.float64),
            "opp_health": g(s["health"]), "opp_max_health": np.full(self.n, 20.0), "opp_absorption": g(s["absorption"]),
            "opp_visible": vis, "opp_blocking": o_block,
            "opp_draw": np.where(o_using & (o_hand == 1), g(s["use_ticks"]), 0).astype(np.float64),
            "opp_eating": o_using & ((o_hand == 2) | (o_hand == 3)),
            "opp_holds_bow": o_hand == 1, "opp_holds_food": (o_hand == 2) | (o_hand == 3),
            "opp_holds_throwable": (o_hand == 4) | (o_hand == 5),
            "opp_melee_damage": g(f.atk_damage) + g(f.strength), "opp_melee_bonus": g(f.sharp),
            "opp_cooldown": 20.0 / g(f.atk_speed), "opp_kb": g(f.kb_level).astype(np.float64),
            "opp_armor": g(f.armor), "opp_toughness": g(f.toughness), "opp_resistance": g(f.resistance).astype(np.float64),
            "opp_scale": g(f.scale),
            "damage_on": self.damage_on,
        }
        # the second enemy
        sec = self.second[:, i]
        s2 = np.maximum(sec, 0)
        e2v = self.visible[rows, i, s2] & (sec >= 0)
        st.update({"e2_present": sec >= 0, "e2_x": self.known[rows, i, s2, 0], "e2_y": self.known[rows, i, s2, 1],
                   "e2_z": self.known[rows, i, s2, 2], "e2_yaw": s["yaw"][rows, s2], "e2_visible": e2v,
                   "e2_since_swing": s["swing_age"][rows, s2].astype(np.float64), "e2_holds_bow": s["hand"][rows, s2] == 1})
        near = np.zeros(self.n)
        for j in range(self.k):
            enemy = f.active[:, j] & f.alive[:, j] & (f.team[:, j] != f.team[:, i])
            d = np.hypot(self.known[:, i, j, 0] - f.x[:, i], self.known[:, i, j, 2] - f.z[:, i])
            near += enemy & (d <= sp.near_enemies)
        st["enemies_near"] = near
        st.update(self._threat(i))
        if self.v3:
            st.update(self._v3_state(i, st))
        # terrain
        t = w.terrain
        voids, drops, steps, walls = t.rays(rows, f.x[:, i], f.y[:, i], f.z[:, i], f.yaw[:, i])
        ox, oy, oz = st["opp_x"], st["opp_y"], st["opp_z"]
        dx, dz = ox - f.x[:, i], oz - f.z[:, i]
        ln = np.maximum(np.hypot(dx, dz), 1e-9)
        drop_b, void_b = t.behind(rows, ox, oy, oz, dx / ln, dz / ln)
        st.update({"voids": voids, "drops": drops, "steps": steps, "walls": walls,
                   "self_below": t.below(rows, f.x[:, i], f.y[:, i], f.z[:, i]), "opp_below": t.below(rows, ox, oy, oz),
                   "opp_drop_behind": drop_b, "opp_void_behind": void_b})
        return st

    def _v3_state(self, i, st):
        """v3's part of fighter i's state: its nearest team mate, the third enemy and its memory."""
        f, sp = self.w.f, self.spec
        rows = np.arange(self.n)
        s = self.seen[i]
        al = self.ally[:, i]
        a = np.maximum(al, 0)
        mates = np.zeros(self.n)
        for j in range(self.k):
            if j != i:
                there = f.active[:, j] & f.alive[:, j] & (f.team[:, j] == f.team[:, i])
                mates += there & (np.hypot(f.x[:, j] - f.x[:, i], f.z[:, j] - f.z[:, i]) <= sp.near_allies)
        th = self.third[:, i]
        e = np.maximum(th, 0)
        tg = self.target[:, i]
        enemy = f.active & (f.team != f.team[:, i:i + 1])
        enemy[:, i] = False
        hurt_me = np.where(enemy, self.w.hit_by_age[:, i, :], MEMORY_CAP).min(axis=1)
        return {
            "ally_present": al >= 0, "ally_x": f.x[rows, a], "ally_y": f.y[rows, a], "ally_z": f.z[rows, a],
            "ally_visible": self.visible[rows, i, a] & (al >= 0), "ally_health": f.health[rows, a],
            "ally_max_health": np.full(self.n, 20.0), "allies_near": mates,
            "e3_present": th >= 0, "e3_x": self.known[rows, i, e, 0], "e3_y": self.known[rows, i, e, 1],
            "e3_z": self.known[rows, i, e, 2], "e3_yaw": s["yaw"][rows, e], "e3_visible": self.visible[rows, i, e] & (th >= 0),
            "e3_since_swing": s["swing_age"][rows, e].astype(np.float64), "e3_holds_bow": s["hand"][rows, e] == 1,
            "opp_unseen": np.where(tg >= 0, self.unseen[rows, i, np.maximum(tg, 0)], 100).astype(np.float64),
            "self_since_hit": np.minimum(hurt_me, 100).astype(np.float64),
            "opp_since_hit": np.where(tg >= 0, np.minimum(self.w.hit_by_age[rows, np.maximum(tg, 0), i], 100), 100).astype(np.float64),
        }

    def regen_rate(self, i):
        """Health a second fighter i gets back now: natural regeneration and regeneration II."""
        f, w = self.w.f, self.w
        nat = w.natural_regen & (f.health[:, i] < 20)
        fast = nat & (f.saturation[:, i] > 0) & (f.food[:, i] >= 20)
        slow = nat & ~fast & (f.food[:, i] >= 18)
        rate = np.where(fast, np.minimum(f.saturation[:, i], 6.0) / 6.0 * 2.0, np.where(slow, 0.25, 0.0))
        return rate + np.where(f.regen_effect[:, i] > 0, 0.8, 0.0)

    def _threat(self, i):
        """The enemy projectile that comes closest soonest (passing within 1.5 blocks of i's chest)."""
        f, p = self.w.f, self.w.p
        cy = f.y[:, i] + self.spec.chest_height * f.scale[:, i]
        px, py, pz = p.x - f.x[:, i][:, None], p.y - cy[:, None], p.z - f.z[:, i][:, None]
        vv = p.vx ** 2 + p.vy ** 2 + p.vz ** 2
        t = -(px * p.vx + py * p.vy + pz * p.vz) / np.maximum(vv, 1e-9)
        cx, cyy, cz = px + p.vx * t, py + p.vy * t, pz + p.vz * t
        miss = np.sqrt(cx ** 2 + cyy ** 2 + cz ** 2)
        rows = np.arange(self.n)
        enemy = f.team[rows[:, None], p.owner] != f.team[:, i][:, None]
        ok = p.active & enemy & (t > 0) & (t <= 40) & (miss < 1.5)
        score = np.where(ok, t, np.inf)
        j = np.argmin(score, axis=1)
        has = np.isfinite(score[rows, j])
        return {"threat_present": has, "threat_x": p.x[rows, j], "threat_y": p.y[rows, j], "threat_z": p.z[rows, j],
                "threat_vx": p.vx[rows, j], "threat_vy": p.vy[rows, j], "threat_vz": p.vz[rows, j]}

    def observe(self, start=None):
        """Every fighter's observation (n, K, obs_dim); the earlier decisions' from the history, except in the arenas
        where `start` (a fight starts: the values now)."""
        out = np.zeros((self.n, self.k, self.spec.obs_dim), dtype=np.float32)
        for i in range(self.k):
            past = [h[:, i] for h in self.hist] if self.hist else None
            out[:, i] = observe(self.state(i), self.spec, past, start)
        return out

    def potential(self, obs):
        sp, rw = self.spec, self.rewards
        dist = obs[:, :, sp.index("opp_dist")] * sp.obs_scale[sp.index("opp_dist")]
        face = obs[:, :, sp.index("aim_yaw_cos")] * (obs[:, :, sp.index("opp_visible")] > 0.5)
        return self.shaping * (-rw.close * np.minimum(dist, 20.0) / 20.0 + rw.face * face)

    # ------------------------------------------------------------------ one decision

    def step(self, actions):
        actions = np.asarray(actions, dtype=np.int64).reshape(self.n, self.k, -1)
        sp, w, f, rw = self.spec, self.w, self.w.f, self.rewards
        dec = [decode(actions[:, i], sp) for i in range(self.k)]
        get = lambda key: np.stack([dec[i][key] for i in range(self.k)], axis=1)
        T = sp.decision_ticks
        yaw = np.clip(get("yaw"), -self.turn_cap, self.turn_cap)
        pitch = get("pitch")
        forward, strafe = get("forward"), get("strafe")
        jump, sprint, attack, use = get("jump") > 0.5, get("sprint") > 0.5, get("attack") > 0.5, get("use") > 0.5
        hand = get("hand").astype(np.int64)
        dealt = np.zeros((self.n, self.k, self.k))
        taken = np.zeros((self.n, self.k))
        healed = np.zeros((self.n, self.k))
        landed = np.zeros((self.n, self.k))
        kills = np.zeros((self.n, self.k))
        died = np.zeros((self.n, self.k), dtype=bool)
        # items over the decision's ticks (the world's flags are per tick)
        used = {name: np.zeros((self.n, self.k), dtype=np.int64) for name in ("fired", "ate", "threw", "arrow_hit", "pearled")}
        alive0 = f.alive.copy()
        late = (self.rng.random((self.n, self.k)) < self.scenario.late_click) & (np.arange(self.k) >= 1)[None, :]
        click_tick = np.where(late, self.rng.integers(0, T, size=(self.n, self.k)), 0)
        for t in range(T):
            noise = self.rng.normal(size=(self.n, self.k, 2)) * self.aim_noise[:, :, None]
            w.tick(forward, strafe, jump, sprint, attack & (click_tick == t), use, yaw / T + noise[:, :, 0], pitch / T + noise[:, :, 1], hand)
            dealt += w.dealt
            taken += w.taken
            healed += w.healed
            landed += w.landed
            died |= w.died
            for name, v in used.items():
                v += getattr(w, name)
            for j in range(self.k):
                kj = w.killer[:, j]
                for i in range(self.k):
                    kills[:, i] += (kj == i) & w.died[:, j]
            self.history.append(f.snapshot())
            if len(self.history) > HISTORY:
                self.history.pop(0)
        self.ticks += T
        self.unseen = np.minimum(self.unseen + T, MEMORY_CAP)
        self._perceive()
        obs = self.observe()
        # rewards, for every slot (the learner is slot 0)
        team = f.team
        enemy = (team[:, :, None] != team[:, None, :]) & f.active[:, None, :]
        to_enemies = (dealt * enemy).sum(axis=2)
        n_enemies = np.maximum(1, enemy.sum(axis=2))
        enemy_heal = (healed[:, None, :] * enemy).sum(axis=2) / n_enemies
        reward = rw.damage * (to_enemies - taken + healed - enemy_heal) / 20.0
        if (w.hits_to_win > 0).any():
            boxing = (w.hits_to_win > 0)[:, None]
            got = (landed[:, None, :] * enemy).sum(axis=2)
            reward += np.where(boxing, rw.hit * (landed - got), 0.0)
        reward += rw.kill * kills - rw.death * died
        vis = obs[:, :, sp.index("opp_visible")] > 0.5
        cross = obs[:, :, sp.index("on_crosshair")] > 0.5
        # what a fighter does each decision counts while it lives (a dead one whose team fights on does nothing)
        reward += np.where(alive0, -rw.time - rw.unseen * ~vis + rw.aim * cross, 0.0)
        phi = self.potential(obs)
        reward += np.where(f.alive, phi - self.phi, 0.0)
        # the end of an episode: the learner is dead, its enemies are, or the time is up
        boxed = (w.hits_to_win > 0)[:, None] & (f.hits >= w.hits_to_win[:, None]) & (w.hits_to_win[:, None] > 0)
        lost_box = (boxed[:, None, :] & enemy).any(axis=2)
        if lost_box.any():
            f.alive &= ~lost_box
            died |= lost_box
            reward -= rw.death * lost_box
        teams_alive = np.stack([(f.alive & f.active & (team == tm)).any(axis=1) for tm in range(self.k)], axis=1)
        left = teams_alive.sum(axis=1)
        my_team_alive = (f.alive & f.active & (team == team[:, :1])).any(axis=1)
        enemies_alive = (f.alive & f.active & (team != team[:, :1])).any(axis=1)
        timeout = self.ticks >= self.max_ticks
        done = ~my_team_alive | ~enemies_alive | timeout
        # the team left wins
        winners = f.active & (left[:, None] == 1) & teams_alive[np.arange(self.n)[:, None], team]
        reward += rw.win * (done[:, None] & winners)
        draw = done & (left > 1)
        hp = f.health + f.absorption
        enemy_hp = (hp[:, None, :] * enemy).sum(axis=2) / n_enemies
        margin = rw.draw_health * (hp - enemy_hp) / 20.0
        reward += np.where((draw & timeout)[:, None] & f.alive, margin - rw.timeout, 0.0)
        reward = self._share(reward)
        self.episode_return += reward
        # the learner's team: 0 won, 1 lost (also when the time ran out with only the enemies' team alive), -1 a draw
        winner = np.where(done & ~my_team_alive, 1, np.where(done & ~enemies_alive & my_team_alive, 0, -1))
        info = {"winner": winner, "done": done, "ticks": self.ticks.copy(), "landed": landed, "dealt": dealt,
                "episode_return": self.episode_return.copy(), "kills": kills, "died": died, **used}
        start = np.zeros(self.n, dtype=bool)
        if self.auto_reset and done.any():
            info["terminal_obs"] = obs.copy()
            info["terrain"] = [self.info_terrain[a] for a in np.nonzero(done)[0]]
            info["family"] = [self.info_family[a] for a in np.nonzero(done)[0]]
            info["mode"] = [self.info_mode[a] for a in np.nonzero(done)[0]]
            idx = np.nonzero(done)[0]
            start[idx] = True
            self._restart(idx)
            self._perceive()
            fresh = self.observe(start)
            obs[idx] = fresh[idx]
            phi[idx] = self.potential(obs)[idx]
        self._remember(obs, start)
        info["alive"] = f.alive & f.active
        self.phi = phi
        self.last_obs = obs
        return obs, reward.astype(np.float32), done, info

    def _share(self, reward):
        """Each fighter's reward: (1 - team_spirit) its own plus team_spirit its team's average (itself included)."""
        tau = self.rewards.team_spirit
        if tau <= 0:
            return reward
        f = self.w.f
        same = (f.team[:, :, None] == f.team[:, None, :]) & f.active[:, None, :] & f.active[:, :, None]
        mean = (same * reward[:, None, :]).sum(axis=2) / np.maximum(1, same.sum(axis=2))
        return (1 - tau) * reward + tau * mean


class PvpEnv2(gym.Env):
    """One learner (slot 0) against opponents (the other slots) played by a policy: a callable from observations
    (m, obs_dim) to action indices (m, heads); by default the scripted bot."""

    metadata = {"render_modes": []}

    def __init__(self, opponent=None, seed=None, max_ticks=1600, breadth=1.0, fixed=None, spec=SPEC2):
        super().__init__()
        from .scripted import Scripted2
        self.combat_spec = spec
        self.batch = Batch2(1, seed=seed, max_ticks=max_ticks, breadth=breadth, auto_reset=False, spec=spec, fixed=fixed)
        self.opponent = opponent if opponent is not None else Scripted2(seed=seed)
        self.observation_space = gym.spaces.Box(-1.0, 1.0, shape=(spec.obs_dim,), dtype=np.float32)
        self.action_space = gym.spaces.MultiDiscrete(spec.action_nvec)
        self._obs = None

    def reset(self, seed=None, options=None):
        super().reset(seed=seed)
        if seed is not None:
            self.batch.rng = np.random.default_rng(seed)
            self.batch.w.rng = self.batch.rng
        self._obs = self.batch.reset()
        return self._obs[0, 0].copy(), {}

    def step(self, action):
        k = self.batch.k
        others = np.asarray(self.opponent(self._obs[0, 1:]), dtype=np.int64).reshape(k - 1, -1)
        acts = np.concatenate([np.asarray(action, dtype=np.int64).reshape(1, -1), others], axis=0)[None]
        obs, reward, done, info = self.batch.step(acts)
        self._obs = obs
        timeout = bool(info["ticks"][0] >= self.batch.max_ticks)
        terminated = bool(done[0]) and not (timeout and info["winner"][0] < 0)
        truncated = bool(done[0]) and not terminated
        return obs[0, 0].copy(), float(reward[0, 0]), terminated, truncated, {"winner": int(info["winner"][0])}
