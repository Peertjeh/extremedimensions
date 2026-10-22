"""1v1 melee in Minecraft, tick by tick, for many arenas at once (numpy, shape (n, 2): n arenas, two fighters).

The formulas are the game's own (Minecraft 1.21 / 26.x, as the mod's core/ai/PlayerSim.java):
  * input: forward/strafe scaled by 0.98, normalised when longer than 1, times the speed (ground 0.1, sprinting 0.13,
    air 0.02 / 0.026), rotated by the yaw;
  * after the move the horizontal speed keeps 0.6 * 0.91 on the ground (0.91 in the air), vertical (vy - 0.08) * 0.98;
    standing still on the ground the vertical speed is -0.0784, as in the game (it matters for knockback);
  * a jump sets 0.42 up and, sprinting, adds 0.2 along the look; holding jump waits 10 ticks between jumps;
  * a hit: charge = (ticks since the last swing + 0.5) / (20 / attack speed), damage = base * (0.2 + 0.8 * charge^2)
    + enchantment * charge, a critical hit (falling, not sprinting, charge > 0.9) times 1.5, then armor;
    the victim is pushed 0.4 away (velocity / 2 - 0.4 * direction, upwards min(0.4, vy / 2 + 0.4) on the ground) and
    can not be hurt in full for 10 ticks (more damage within them only adds the difference, without a push);
    a sprinting hit with charge > 0.9 pushes 0.5 more along the attacker's look, slows the attacker to 0.6 and ends
    its sprint;
  * a swing only lands when the look ray hits the hitbox (0.6 x 1.8) within reach 3 of the eyes; a miss also costs
    the charge;
  * health comes back like the game's: every 10 ticks while there is saturation, else every 80.
The arena is flat: a square with walls (half-size per arena), or a round platform to fall off (sumo, later).
"""
from dataclasses import dataclass

import numpy as np

from .spec import SPEC
from .obs import look_vector, ray_hits_box

GRAVITY = 0.08
DRAG_Y = 0.98
AIR_DRAG = 0.91
GROUND_KEEP = 0.6 * 0.91
JUMP = 0.42
SPRINT_JUMP_BOOST = 0.2
INPUT_SCALE = 0.98
REST_VY = -GRAVITY * DRAG_Y  # vertical speed of a player standing on the ground


@dataclass(frozen=True)
class Rules:
    """What kind of fight: the kit and how it is won."""
    name: str = "sword"
    base_damage: float = 7.0       # diamond sword
    enchant_damage: float = 0.0
    attack_speed: float = 1.6      # sword: full charge after 12.5 ticks
    armor: float = 15.0            # full iron
    toughness: float = 0.0
    walk_speed: float = 0.1        # movement speed attribute (Speed II: 0.14)
    damage: bool = True            # False: health stays full, hits are counted (boxing, sumo)
    hits_to_win: int = 0           # boxing: first to this many landed swings
    max_health: float = 20.0

    @property
    def charge_ticks(self):
        return 20.0 / self.attack_speed


SWORD = Rules()
BOXING = Rules(name="boxing", enchant_damage=1.0, armor=0.0, walk_speed=0.14, damage=False, hits_to_win=20)


def armor_reduce(damage, armor, toughness):
    """Damage after armor (the game's formula)."""
    if armor <= 0:
        return damage
    f = 2.0 + toughness / 4.0
    pts = np.clip(armor - damage / f, armor * 0.2, 20.0)
    return damage * (1.0 - pts / 25.0)


class Fighters:
    """The state of both fighters in n arenas; every array has shape (n, 2)."""

    FLOAT = ["x", "y", "z", "vx", "vy", "vz", "yaw", "pitch", "health", "last_hurt", "saturation"]
    INT = ["ticker", "invul", "jump_delay", "swing_age", "regen_timer", "hits", "hit_age"]
    BOOL = ["on_ground", "sprinting"]

    def __init__(self, n):
        self.n = n
        for k in self.FLOAT:
            setattr(self, k, np.zeros((n, 2)))
        for k in self.INT:
            setattr(self, k, np.zeros((n, 2), dtype=np.int64))
        for k in self.BOOL:
            setattr(self, k, np.zeros((n, 2), dtype=bool))

    def snapshot(self):
        return {k: getattr(self, k).copy() for k in self.FLOAT + self.INT + self.BOOL}


class Physics:
    """Steps n arenas one tick at a time. Inputs per tick, arrays (n, 2): forward, strafe (-1/0/1), jump, sprint,
    attack (bool), yaw_left, pitch_down (degrees this tick)."""

    def __init__(self, n, rules=SWORD, spec=SPEC, rng=None):
        self.n = n
        self.rules = rules
        self.spec = spec
        self.rng = rng if rng is not None else np.random.default_rng()
        self.f = Fighters(n)
        self.half = np.full(n, 10.0)        # square arena: walls at |x|, |z| = half
        self.platform = np.zeros(n)         # > 0: round platform of this radius, no walls (sumo)
        self.fell = np.zeros((n, 2), dtype=bool)
        # what happened this tick, for rewards and tests
        self.dealt = np.zeros((n, 2))
        self.landed = np.zeros((n, 2), dtype=bool)
        self.whiffed = np.zeros((n, 2), dtype=bool)

    # ------------------------------------------------------------------ setup

    def place(self, idx, x0, z0, x1, z1, yaw0, yaw1):
        f = self.f
        for side, (x, z, yaw) in enumerate(((x0, z0, yaw0), (x1, z1, yaw1))):
            f.x[idx, side] = x
            f.z[idx, side] = z
            f.y[idx, side] = 0.0
            f.vx[idx, side] = 0.0
            f.vz[idx, side] = 0.0
            f.vy[idx, side] = REST_VY
            f.yaw[idx, side] = yaw
            f.pitch[idx, side] = 0.0
            f.on_ground[idx, side] = True
            f.sprinting[idx, side] = False
            f.health[idx, side] = self.rules.max_health
            f.last_hurt[idx, side] = 0.0
            f.saturation[idx, side] = 5.0
            f.ticker[idx, side] = 100
            f.invul[idx, side] = 0
            f.jump_delay[idx, side] = 0
            f.swing_age[idx, side] = 100
            f.regen_timer[idx, side] = 0
            f.hits[idx, side] = 0
            f.hit_age[idx, side] = 1000
        self.fell[idx] = False

    # ------------------------------------------------------------------ one tick

    def tick(self, forward, strafe, jump, sprint, attack, yaw_left, pitch_down):
        f, r = self.f, self.rules
        self.dealt[:] = 0.0
        self.landed[:] = False
        self.whiffed[:] = False
        # turning
        f.yaw = (f.yaw - yaw_left + 180.0) % 360.0 - 180.0
        f.pitch = np.clip(f.pitch + pitch_down, -90.0, 90.0)
        # timers
        f.ticker += 1
        f.invul = np.maximum(0, f.invul - 1)
        f.swing_age += 1
        f.hit_age += 1
        # sprinting is set before the click (a sprint hit then ends it for this tick)
        f.sprinting = sprint & (forward > 0) & ~self.fell
        # clicks, both at the positions of the tick's start
        self._attacks(attack & ~self.fell)
        # the ground of the tick's start counts for the whole tick (also on the jump tick)
        grounded = f.on_ground.copy()
        # jumping
        can_jump = jump & f.on_ground & (f.jump_delay == 0) & ~self.fell
        ry = np.radians(f.yaw)
        fx, fz = -np.sin(ry), np.cos(ry)
        lx, lz = np.cos(ry), np.sin(ry)
        f.vy = np.where(can_jump, JUMP, f.vy)
        boost = can_jump & f.sprinting
        f.vx = f.vx + np.where(boost, fx * SPRINT_JUMP_BOOST, 0.0)
        f.vz = f.vz + np.where(boost, fz * SPRINT_JUMP_BOOST, 0.0)
        f.jump_delay = np.where(can_jump, 10, np.where(jump, np.maximum(0, f.jump_delay - 1), 0))
        # input
        speed = np.where(grounded, r.walk_speed * np.where(f.sprinting, 1.3, 1.0), np.where(f.sprinting, 0.026, 0.02))
        mf = forward * INPUT_SCALE
        ms = strafe * INPUT_SCALE
        ln = np.sqrt(mf * mf + ms * ms)
        norm = np.where(ln > 1.0, ln, 1.0)
        mf, ms = mf / norm * speed, ms / norm * speed
        f.vx = f.vx + ms * lx + mf * fx
        f.vz = f.vz + ms * lz + mf * fz
        self._push_apart()
        # move
        keep = np.where(grounded, GROUND_KEEP, AIR_DRAG)
        f.x = f.x + f.vx
        f.z = f.z + f.vz
        f.y = f.y + f.vy
        self._collide()
        # friction and gravity after the move
        f.vx = f.vx * keep
        f.vz = f.vz * keep
        f.vy = (f.vy - GRAVITY) * DRAG_Y
        self._regen()

    def _attacks(self, attack):
        f, r, spec = self.f, self.rules, self.spec
        for a in (0, 1):
            b = 1 - a
            who = attack[:, a]
            if not who.any():
                continue
            charge = np.clip((f.ticker[:, a] + 0.5) / r.charge_ticks, 0.0, 1.0)
            lx, ly, lz = look_vector(f.yaw[:, a], f.pitch[:, a])
            hw = spec.hitbox_width / 2
            hit = who & ray_hits_box(f.x[:, a], f.y[:, a] + spec.eye_height, f.z[:, a], lx, ly, lz,
                                     f.x[:, b] - hw, f.y[:, b], f.z[:, b] - hw, f.x[:, b] + hw, f.y[:, b] + spec.hitbox_height, f.z[:, b] + hw,
                                     spec.reach) & ~self.fell[:, b]
            f.ticker[:, a] = np.where(who, 0, f.ticker[:, a])
            f.swing_age[:, a] = np.where(who, 0, f.swing_age[:, a])
            self.whiffed[:, a] |= who & ~hit
            if not hit.any():
                continue
            crit = (charge > 0.9) & ~f.on_ground[:, a] & (f.vy[:, a] < 0) & ~f.sprinting[:, a]
            dmg = r.base_damage * (0.2 + charge * charge * 0.8) * np.where(crit, 1.5, 1.0) + r.enchant_damage * charge
            dmg = armor_reduce(dmg, r.armor, r.toughness)
            # being hurt: in full after the 10 ticks, else only what is more than the last hurt
            full = hit & (f.invul[:, b] <= 10)
            partial = hit & ~full & (dmg > f.last_hurt[:, b])
            taken = np.where(full, dmg, np.where(partial, dmg - f.last_hurt[:, b], 0.0))
            f.last_hurt[:, b] = np.where(full | partial, dmg, f.last_hurt[:, b])
            f.invul[:, b] = np.where(full, 20, f.invul[:, b])
            hurt = full | partial
            if r.damage:
                f.health[:, b] = f.health[:, b] - taken
                self.dealt[:, a] += taken
            # a landed swing (boxing counts every one, also within the 10 ticks)
            self.landed[:, a] |= hit
            f.hits[:, a] += hit.astype(np.int64)
            f.hit_age[:, b] = np.where(hit, 0, f.hit_age[:, b])
            # the push away from the attacker, only on a full hit
            dx = f.x[:, a] - f.x[:, b]
            dz = f.z[:, a] - f.z[:, b]
            self._knockback(b, full, 0.4, dx, dz)
            # sprint knockback along the attacker's look
            ry = np.radians(f.yaw[:, a])
            extra = hurt & f.sprinting[:, a] & (charge > 0.9)
            self._knockback(b, extra, 0.5, np.sin(ry), -np.cos(ry))
            f.vx[:, a] = np.where(extra, f.vx[:, a] * 0.6, f.vx[:, a])
            f.vz[:, a] = np.where(extra, f.vz[:, a] * 0.6, f.vz[:, a])
            f.sprinting[:, a] = f.sprinting[:, a] & ~extra

    def _knockback(self, b, mask, strength, dx, dz):
        """The game's LivingEntity.knockback: (dx, dz) points from the victim to the attacker."""
        if not mask.any():
            return
        f = self.f
        ln = np.hypot(dx, dz)
        tiny = ln < 1e-2
        ang = self.rng.uniform(0, 2 * np.pi, size=ln.shape)
        dx = np.where(tiny, np.cos(ang), dx)
        dz = np.where(tiny, np.sin(ang), dz)
        ln = np.hypot(dx, dz)
        nx, nz = dx / ln * strength, dz / ln * strength
        f.vx[:, b] = np.where(mask, f.vx[:, b] / 2 - nx, f.vx[:, b])
        f.vz[:, b] = np.where(mask, f.vz[:, b] / 2 - nz, f.vz[:, b])
        up = np.where(f.on_ground[:, b], np.minimum(0.4, f.vy[:, b] / 2 + strength), f.vy[:, b])
        f.vy[:, b] = np.where(mask, up, f.vy[:, b])

    def _push_apart(self):
        """Players standing in each other are pushed apart a little (the game's entity push)."""
        f = self.f
        dx = f.x[:, 1] - f.x[:, 0]
        dz = f.z[:, 1] - f.z[:, 0]
        d = np.hypot(dx, dz)
        close = (d < self.spec.hitbox_width) & (d > 1e-4)
        if not close.any():
            return
        k = np.where(close, 0.05 / np.maximum(d, 1e-4), 0.0)
        f.vx[:, 0] -= dx * k
        f.vz[:, 0] -= dz * k
        f.vx[:, 1] += dx * k
        f.vz[:, 1] += dz * k

    def _collide(self):
        f = self.f
        hw = self.spec.hitbox_width / 2
        walled = self.platform <= 0
        lim = (self.half - hw)[:, None]
        for pos, vel in (("x", "vx"), ("z", "vz")):
            p = getattr(f, pos)
            v = getattr(f, vel)
            out = walled[:, None] & (np.abs(p) > lim)
            setattr(f, pos, np.where(out, np.clip(p, -lim, lim), p))
            setattr(f, vel, np.where(out, 0.0, v))
        # the ground (or no ground beyond the platform's edge)
        r = np.hypot(f.x, f.z)
        over = walled[:, None] | (r <= self.platform[:, None] + hw)
        land = over & (f.y <= 0.0) & ~self.fell
        f.y = np.where(land, 0.0, f.y)
        f.vy = np.where(land & (f.vy < 0), 0.0, f.vy)
        f.on_ground = land
        self.fell |= f.y < -4.0

    def _regen(self):
        f, r = self.f, self.rules
        if not r.damage:
            f.health[:] = r.max_health
            return
        f.regen_timer += 1
        hurt = f.health < r.max_health
        fast = hurt & (f.saturation > 0) & (f.regen_timer >= 10)
        slow = hurt & (f.saturation <= 0) & (f.regen_timer >= 80)
        heal = (fast | slow) & (f.health > 0)
        # with saturation: min(saturation, 6) / 6 health for as much exhaustion (4 exhaustion take 1 saturation)
        use = np.minimum(f.saturation, 6.0)
        f.health = np.where(heal, np.minimum(r.max_health, f.health + np.where(fast, use / 6.0, 1.0)), f.health)
        f.saturation = np.where(fast, np.maximum(0.0, f.saturation - use / 4.0), f.saturation)
        f.regen_timer = np.where(heal | ~hurt, 0, f.regen_timer)

    # ------------------------------------------------------------------ what each fighter knows

    def charge(self):
        return np.clip((self.f.ticker + 0.5) / self.rules.charge_ticks, 0.0, 1.0)

    def rays(self, side):
        """Distances (n, 8) in the fighter's own directions (0 ahead, counter-clockwise from above: 1 = 45 left):
        ground before a drop, and room before a wall."""
        f, spec = self.f, self.spec
        m = spec.ray_max
        ry = np.radians(f.yaw[:, side])
        fx, fz = -np.sin(ry), np.cos(ry)
        lx, lz = np.cos(ry), np.sin(ry)
        x, z = f.x[:, side], f.z[:, side]
        edges = np.full((self.n, spec.ray_directions), m)
        walls = np.full((self.n, spec.ray_directions), m)
        walled = self.platform <= 0
        for k in range(spec.ray_directions):
            a = np.radians(k * 360.0 / spec.ray_directions)
            dx = fx * np.cos(a) + lx * np.sin(a)
            dz = fz * np.cos(a) + lz * np.sin(a)
            # walls of the square (room for the body, as far as the wall minus half the hitbox)
            hw = spec.hitbox_width / 2
            tx = np.where(np.abs(dx) > 1e-9, (np.sign(dx) * (self.half - hw) - x) / np.where(np.abs(dx) > 1e-9, dx, 1.0), np.inf)
            tz = np.where(np.abs(dz) > 1e-9, (np.sign(dz) * (self.half - hw) - z) / np.where(np.abs(dz) > 1e-9, dz, 1.0), np.inf)
            walls[:, k] = np.where(walled, np.clip(np.minimum(tx, tz), 0.0, m), m)
            # the edge of a round platform: x + t d on the circle of radius R
            R = self.platform
            b = x * dx + z * dz
            c = x * x + z * z - R * R
            t = -b + np.sqrt(np.maximum(0.0, b * b - c))
            edges[:, k] = np.where(walled, m, np.clip(t, 0.0, m))
        return edges, walls
