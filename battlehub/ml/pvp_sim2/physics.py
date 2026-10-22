"""Melee with everything round it, tick by tick, for n arenas with up to K fighters each (numpy, arrays (n, K)).

On top of v1's fight (pvp_sim/physics.py: the same movement, charge, damage, armor, critical hits, the 10 ticks
after a hit and knockback, as the game computes them):
  * terrain (terrain.py): moving one axis at a time against the columns, stepping up 0.6 on the ground, jumping onto
    a block, falling off edges with fall damage beyond 3 blocks (jump boost adds to that), the void below the map;
  * kits (kits.py): weapon damage and attack speed, sharpness, the knockback enchantment, armor and toughness,
    knockback resistance, resistance (level 5: no damage, sumo and boxing), strength, speed and slowness, jump boost,
    gravity, size (hitbox, eyes) and reach;
  * the hand and the right mouse button: switching what is held costs the charge, using something walks at 20% and
    ends the sprint;
      bow       drawing while held, the arrow leaves on letting go: power ((t/20)^2 + 2 t/20) / 3, speed 3 * power, the
                game's gravity 0.05 and drag 0.99, damage ceil(speed * (2 + power level bonus)), a full draw a
                critical one (up to half of it more);
      food      eaten after 32 ticks when not full: food and saturation; one press starts it and it goes on until
                done (unless the hand changes);
      golden    the same, 32 ticks: 4 absorption, regeneration II for 5 seconds;
      apple
      pearl     thrown at 1.5 (gravity 0.03): where it lands the thrower is put, with 5 damage; lost in the void;
                20 ticks until the next;
      wind      thrown at 1.5 in a straight line: where it hits, everyone within 2.4 blocks is pushed away from it
      charge    ((1 - distance / 2.4) * 1.22, the thrower too: a wind charge at the feet sends one up), and falling
                after that only hurts below the height it pushed one from; 10 ticks until the next;
      shield    held up with the weapon in hand: after 5 ticks it blocks hits and arrows from the front (the attacker
                is pushed off 0.5); an axe disables it for 5 seconds;
  * the mace: falling more than 1.5 blocks adds 4 damage a block up to 3 blocks, 2 up to 8, then 1;
  * hunger as the game's food data: exhaustion (sprinting, jumping, attacking, being hurt), saturation and food,
    natural regeneration (fast with saturation and full food, slow above 18), the saturation effect of many kits;
  * teams: a hit on a team mate does nothing; a kill counts for whoever hit last within 5 seconds (also a fall into
    the void after a hit).
"""
import numpy as np

from pvp_sim.obs import look_vector, ray_hits_box
from pvp_sim.physics import AIR_DRAG, DRAG_Y, GROUND_KEEP, INPUT_SCALE, SPRINT_JUMP_BOOST

from .kits import FIELDS, base
from .obs import armor_reduce
from .spec import SPEC2
from .terrain import Terrain

HANDS = {"weapon": 0, "bow": 1, "food": 2, "gapple": 3, "pearl": 4, "wind": 5}
ARROW, PEARL, WIND = 0, 1, 2
GRAVITY_OF = {ARROW: 0.05, PEARL: 0.03, WIND: 0.0}
DRAG_OF = {ARROW: 0.99, PEARL: 0.99, WIND: 1.0}
EAT_TICKS = 32
BLOCK_DELAY = 5
SHIELD_DISABLED = 100
PEARL_COOLDOWN = 20
WIND_COOLDOWN = 10
PEARL_DAMAGE = 5.0
WIND_RADIUS = 2.4
WIND_KNOCKBACK = 1.22
KILL_CREDIT = 100
STEP_UP = 0.6
VOID_BELOW = 12.0     # this far below the map's lowest ground is the void's death


def bow_power(ticks):
    f = np.asarray(ticks, dtype=np.float64) / 20.0
    return np.minimum(1.0, (f * f + 2 * f) / 3.0)


def mace_bonus(fall):
    return np.where(fall <= 3, 4 * fall, np.where(fall <= 8, 12 + 2 * (fall - 3), 22 + (fall - 8)))


class Fighters:
    FLOAT = ["x", "y", "z", "vx", "vy", "vz", "yaw", "pitch", "health", "absorption", "last_hurt", "food", "saturation",
             "exhaustion", "fall", "impulse_y", "atk_damage", "strength", "atk_speed", "sharp", "armor", "toughness",
             "kb_resist", "walk_speed", "jump_power", "gravity", "scale", "reach"]
    INT = ["ticker", "invul", "jump_delay", "swing_age", "regen_timer", "hits", "hit_age", "use_ticks", "pearl_cd",
           "wind_cd", "shield_cd", "regen_effect", "hand", "kb_level", "resistance", "arrows", "bow_power", "food_items",
           "gapples", "pearls", "winds", "last_attacker", "last_attacker_age", "team"]
    BOOL = ["on_ground", "sprinting", "using", "use_held", "alive", "ignore_fall", "axe", "mace", "shield",
            "saturation_effect", "active"]

    def __init__(self, n, k):
        self.n, self.k = n, k
        for name in self.FLOAT:
            setattr(self, name, np.zeros((n, k)))
        for name in self.INT:
            setattr(self, name, np.zeros((n, k), dtype=np.int64))
        for name in self.BOOL:
            setattr(self, name, np.zeros((n, k), dtype=bool))

    def snapshot(self):
        return {name: getattr(self, name).copy() for name in self.FLOAT + self.INT + self.BOOL}

    # derived
    def width(self):
        return 0.6 * self.scale

    def height(self):
        return 1.8 * self.scale

    def eye(self):
        return 1.62 * self.scale

    def blocking(self):
        return self.using & (self.hand == 0) & self.shield & (self.use_ticks >= BLOCK_DELAY) & (self.shield_cd == 0)

    def charge_ticks(self):
        weapon = self.hand == 0
        return 20.0 / np.maximum(1e-3, np.where(weapon, self.atk_speed, 4.0))

    def charge(self):
        return np.clip((self.ticker + 0.5) / self.charge_ticks(), 0.0, 1.0)


class Projectiles:
    def __init__(self, n, p):
        self.active = np.zeros((n, p), dtype=bool)
        self.kind = np.zeros((n, p), dtype=np.int64)
        self.owner = np.zeros((n, p), dtype=np.int64)
        self.age = np.zeros((n, p), dtype=np.int64)
        self.crit = np.zeros((n, p), dtype=bool)
        self.base = np.zeros((n, p))
        for name in ("x", "y", "z", "vx", "vy", "vz"):
            setattr(self, name, np.zeros((n, p)))


class World:
    """n arenas, K fighter slots each (a slot that is not active plays no part), the terrain and projectiles in
    flight. tick() takes the inputs of one tick, arrays (n, K): forward, strafe (-1/0/1), jump, sprint, attack, use
    (bool), yaw_left, pitch_down (degrees this tick), hand (index in HANDS)."""

    def __init__(self, n, k=3, projectiles=12, spec=SPEC2, rng=None, terrain_size=64):
        self.n, self.k = n, k
        self.spec = spec
        self.rng = rng if rng is not None else np.random.default_rng()
        self.f = Fighters(n, k)
        self.p = Projectiles(n, projectiles)
        self.terrain = Terrain(n, terrain_size, spec)
        self.natural_regen = np.ones(n, dtype=bool)
        self.hits_to_win = np.zeros(n, dtype=np.int64)
        # who was hit by whom how long ago (n, victim, attacker), for what each knows of the others
        self.hit_by_age = np.full((n, k, k), 1000, dtype=np.int64)
        # what happened this tick, for rewards and tests: damage done by a to b (n, a, b), health won back
        self.dealt = np.zeros((n, k, k))
        self.taken = np.zeros((n, k))
        self.healed = np.zeros((n, k))
        self.landed = np.zeros((n, k), dtype=bool)
        self.whiffed = np.zeros((n, k), dtype=bool)
        self.died = np.zeros((n, k), dtype=bool)
        self.killer = np.full((n, k), -1, dtype=np.int64)
        self.fired = np.zeros((n, k), dtype=bool)
        self.ate = np.zeros((n, k), dtype=bool)
        self.threw = np.zeros((n, k), dtype=bool)

    # ------------------------------------------------------------------ setup

    def place(self, a, slot, x, y, z, yaw, kit, team):
        """Puts a fighter into slot `slot` of arena `a` with a kit (kits.FIELDS) and a team."""
        f = self.f
        for name in FIELDS:
            getattr(f, name)[a, slot] = kit[name]
        f.x[a, slot], f.y[a, slot], f.z[a, slot] = x, y, z
        f.vx[a, slot] = f.vz[a, slot] = 0.0
        f.vy[a, slot] = -f.gravity[a, slot] * DRAG_Y
        f.yaw[a, slot], f.pitch[a, slot] = yaw, 0.0
        f.health[a, slot] = 20.0
        f.absorption[a, slot] = f.last_hurt[a, slot] = 0.0
        f.food[a, slot], f.saturation[a, slot], f.exhaustion[a, slot] = 20.0, 5.0, 0.0
        f.fall[a, slot] = f.impulse_y[a, slot] = 0.0
        f.ticker[a, slot] = 100
        for name in ("invul", "jump_delay", "regen_timer", "hits", "use_ticks", "pearl_cd", "wind_cd", "shield_cd",
                     "regen_effect", "hand", "last_attacker_age"):
            getattr(f, name)[a, slot] = 0
        f.swing_age[a, slot] = f.hit_age[a, slot] = 100
        f.last_attacker[a, slot] = -1
        f.last_attacker_age[a, slot] = 1000
        f.team[a, slot] = team
        f.on_ground[a, slot] = True
        for name in ("sprinting", "using", "use_held", "ignore_fall"):
            getattr(f, name)[a, slot] = False
        f.alive[a, slot] = f.active[a, slot] = True
        self.hit_by_age[a, slot, :] = 1000
        self.hit_by_age[a, :, slot] = 1000

    def remove(self, a, slot):
        f = self.f
        for name, v in base().items():
            getattr(f, name)[a, slot] = v
        f.alive[a, slot] = f.active[a, slot] = False
        f.x[a, slot] = f.z[a, slot] = 0.0
        f.y[a, slot] = -1000.0

    def clear_projectiles(self, a):
        self.p.active[a] = False

    # ------------------------------------------------------------------ one tick

    def tick(self, forward, strafe, jump, sprint, attack, use, yaw_left, pitch_down, hand):
        f, n = self.f, self.n
        self.dealt[:] = 0.0
        self.taken[:] = 0.0
        self.healed[:] = 0.0
        for name in ("landed", "whiffed", "died", "fired", "ate", "threw"):
            getattr(self, name)[:] = False
        self.killer[:] = -1
        alive = f.alive.copy()
        # turning
        f.yaw = np.where(alive, (f.yaw - yaw_left + 180.0) % 360.0 - 180.0, f.yaw)
        f.pitch = np.where(alive, np.clip(f.pitch + pitch_down, -90.0, 90.0), f.pitch)
        # timers
        f.ticker += 1
        f.invul = np.maximum(0, f.invul - 1)
        f.swing_age += 1
        f.hit_age += 1
        f.last_attacker_age += 1
        self.hit_by_age += 1
        for cd in ("pearl_cd", "wind_cd", "shield_cd"):
            setattr(f, cd, np.maximum(0, getattr(f, cd) - 1))
        self._hand(np.asarray(hand, dtype=np.int64), alive)
        self._use(np.asarray(use, dtype=bool) & alive, alive)
        # sprinting is set before the click (a sprint hit then ends it)
        f.sprinting = np.asarray(sprint, dtype=bool) & (np.asarray(forward) > 0) & ~f.using & (f.food > 6) & alive
        self._attacks(np.asarray(attack, dtype=bool) & alive & ~f.using)
        self._move(np.asarray(forward, dtype=np.float64), np.asarray(strafe, dtype=np.float64), np.asarray(jump, dtype=bool) & alive)
        self._projectiles()
        self._hunger()
        self._deaths()

    # ------------------------------------------------------------------ the hand and the right button

    def carried(self, hand):
        f = self.f
        return np.select([hand == 0, hand == 1, hand == 2, hand == 3, hand == 4, hand == 5],
                         [np.ones_like(f.alive), (f.bow_power >= 0) & (f.arrows > 0), f.food_items > 0, f.gapples > 0,
                          f.pearls > 0, f.winds > 0], False)

    def _hand(self, want, alive):
        f = self.f
        new = np.where(self.carried(want) & alive, want, f.hand)
        changed = new != f.hand
        f.ticker = np.where(changed, 0, f.ticker)
        f.using &= ~changed
        f.use_ticks = np.where(changed, 0, f.use_ticks)
        f.hand = new

    def _use(self, use, alive):
        f = self.f
        press = use & ~f.use_held
        release = ~use & f.use_held
        f.use_held = use
        h = f.hand
        # bow: draw while held, shoot on letting go
        bow = (h == 1) & (f.bow_power >= 0)
        shoot = bow & release & f.using & (bow_power(f.use_ticks) >= 0.1) & (f.arrows > 0)
        if shoot.any():
            self._shoot(shoot)
        # food and golden apples: eaten after 32 ticks; once started it goes on until done (one press, as a player who
        # means to eat holds the button), unless the hand changes
        edible = ((h == 2) & (f.food_items > 0) & (f.food < 20)) | ((h == 3) & (f.gapples > 0))
        eating = f.using & ((h == 2) | (h == 3)) & edible
        # the shield: up while held with the weapon in hand
        shield = (h == 0) & f.shield & (f.shield_cd == 0)
        holding = (use & ((bow & (f.arrows > 0)) | edible | shield)) | eating
        f.use_ticks = np.where(holding, np.where(f.using, f.use_ticks + 1, 1), 0)
        f.using = holding
        done = holding & ((h == 2) | (h == 3)) & (f.use_ticks >= EAT_TICKS)
        if done.any():
            self._eat(done)
            f.use_ticks = np.where(done, 0, f.use_ticks)
            # the next only while the button is still down
            f.using = np.where(done, use, f.using)
        # pearls and wind charges: thrown on pressing
        pearl = press & (h == 4) & (f.pearls > 0) & (f.pearl_cd == 0)
        wind = press & (h == 5) & (f.winds > 0) & (f.wind_cd == 0)
        if pearl.any():
            self._throw(pearl, PEARL)
            f.pearls -= pearl
            f.pearl_cd = np.where(pearl, PEARL_COOLDOWN, f.pearl_cd)
        if wind.any():
            self._throw(wind, WIND)
            f.winds -= wind
            f.wind_cd = np.where(wind, WIND_COOLDOWN, f.wind_cd)

    def _eat(self, done):
        f = self.f
        food = done & (f.hand == 2)
        gap = done & (f.hand == 3)
        f.food = np.where(food, np.minimum(20.0, f.food + 8), np.where(gap, np.minimum(20.0, f.food + 4), f.food))
        f.saturation = np.where(food, np.minimum(f.food, f.saturation + 12.8), np.where(gap, np.minimum(f.food, f.saturation + 9.6), f.saturation))
        before = f.absorption.copy()
        f.absorption = np.where(gap, np.maximum(f.absorption, 4.0), f.absorption)
        self.healed += f.absorption - before
        f.regen_effect = np.where(gap, 100, f.regen_effect)
        f.food_items -= food
        f.gapples -= gap
        self.ate |= done

    def _spawn(self, who, kind, speed, base=0.0, crit=None):
        """New projectiles from the eyes of the fighters in `who` (n, K), along their look, with their motion."""
        f, p = self.f, self.p
        lx, ly, lz = look_vector(f.yaw, f.pitch)
        noise = self.rng.normal(size=(3,) + f.yaw.shape) * 0.0172275
        sp = np.broadcast_to(speed, f.yaw.shape)
        vx = (lx + noise[0]) * sp + f.vx
        vy = (ly + noise[1]) * sp + np.where(f.on_ground, 0.0, f.vy)
        vz = (lz + noise[2]) * sp + f.vz
        for a, k in zip(*np.nonzero(who)):
            free = np.nonzero(~p.active[a])[0]
            if free.size == 0:
                continue
            j = free[0]
            p.active[a, j] = True
            p.kind[a, j] = kind
            p.owner[a, j] = k
            p.age[a, j] = 0
            p.x[a, j], p.y[a, j], p.z[a, j] = f.x[a, k], f.y[a, k] + f.eye()[a, k] - 0.1, f.z[a, k]
            p.vx[a, j], p.vy[a, j], p.vz[a, j] = vx[a, k], vy[a, k], vz[a, k]
            p.base[a, j] = base if np.ndim(base) == 0 else base[a, k]
            p.crit[a, j] = False if crit is None else crit[a, k]

    def _shoot(self, shoot):
        f = self.f
        power = bow_power(f.use_ticks)
        base = 2.0 + np.where(f.bow_power > 0, 0.5 * f.bow_power + 0.5, 0.0)
        self._spawn(shoot, ARROW, 3.0 * power, base, crit=power >= 1.0)
        f.arrows -= shoot
        f.use_ticks = np.where(shoot, 0, f.use_ticks)
        self.fired |= shoot

    def _throw(self, who, kind):
        self._spawn(who, kind, 1.5)
        self.threw |= who

    # ------------------------------------------------------------------ hurting

    def hurt(self, b, mask, amount, attacker, armor=True, knock=None, strength=0.4):
        """Damage to slot b in the arenas of `mask` (n,): resistance, armor, the 10 ticks after a hit, absorption first;
        a full hit pushes `strength` along knock = (dx, dz) (the direction of the push) when given. attacker: slot (n,)
        or -1. Returns the full and partial hits."""
        f = self.f
        mask = mask & f.alive[:, b]
        if not mask.any():
            return mask, mask
        amount = amount * np.maximum(0.0, 1.0 - 0.2 * f.resistance[:, b])
        if armor:
            amount = armor_reduce(amount, f.armor[:, b], f.toughness[:, b])
        full = mask & (f.invul[:, b] <= 10)
        partial = mask & ~full & (amount > f.last_hurt[:, b])
        taken = np.where(full, amount, np.where(partial, amount - f.last_hurt[:, b], 0.0))
        f.last_hurt[:, b] = np.where(full | partial, amount, f.last_hurt[:, b])
        f.invul[:, b] = np.where(full, 20, f.invul[:, b])
        soak = np.minimum(f.absorption[:, b], taken)
        f.absorption[:, b] -= soak
        f.health[:, b] -= taken - soak
        self.taken[:, b] += taken
        hit = full | partial
        att = np.broadcast_to(np.asarray(attacker), mask.shape)
        has = hit & (att >= 0)
        if has.any():
            rows = np.nonzero(has)[0]
            self.dealt[rows, att[rows], b] += taken[rows]
            self.hit_by_age[rows, b, att[rows]] = 0
            f.last_attacker[rows, b] = att[rows]
            f.last_attacker_age[rows, b] = 0
        f.exhaustion[:, b] += np.where(hit, 0.1, 0.0)
        f.hit_age[:, b] = np.where(hit, 0, f.hit_age[:, b])
        if knock is not None:
            self.knockback(b, full, strength, knock[0], knock[1])
        return full, partial

    def knockback(self, b, mask, strength, dx, dz):
        """The game's LivingEntity.knockback, as a push along (dx, dz), less by the knockback resistance."""
        if not np.any(mask):
            return
        f = self.f
        strength = strength * (1.0 - f.kb_resist[:, b])
        ln = np.hypot(dx, dz)
        tiny = ln < 1e-2
        ang = self.rng.uniform(0, 2 * np.pi, size=ln.shape)
        dx = np.where(tiny, np.cos(ang), dx)
        dz = np.where(tiny, np.sin(ang), dz)
        ln = np.hypot(dx, dz)
        nx, nz = dx / ln * strength, dz / ln * strength
        f.vx[:, b] = np.where(mask, f.vx[:, b] / 2 + nx, f.vx[:, b])
        f.vz[:, b] = np.where(mask, f.vz[:, b] / 2 + nz, f.vz[:, b])
        up = np.where(f.on_ground[:, b], np.minimum(0.4, f.vy[:, b] / 2 + strength), f.vy[:, b])
        f.vy[:, b] = np.where(mask, up, f.vy[:, b])

    # ------------------------------------------------------------------ clicks

    def _block_distance(self, a, ex, ey, ez, lx, ly, lz, reach):
        """How far along the look the terrain is (inf: not within reach)."""
        s = np.arange(0.1, float(np.max(reach)) + 0.1, 0.1)[None, :]
        g = self.terrain.column(np.arange(self.n)[:, None], ex[:, None] + lx[:, None] * s, ez[:, None] + lz[:, None] * s)
        inside = ((ey[:, None] + ly[:, None] * s) < g) & (s <= reach[:, None])
        return np.where(inside.any(axis=1), s[0][np.argmax(inside, axis=1)], np.inf)

    def _attacks(self, attack):
        f, K = self.f, self.k
        charge = f.charge()
        weapon = f.hand == 0
        lx, ly, lz = look_vector(f.yaw, f.pitch)
        eye = f.eye()
        for a in range(K):
            who = attack[:, a]
            if not who.any():
                continue
            ex, ey, ez = f.x[:, a], f.y[:, a] + eye[:, a], f.z[:, a]
            best = np.full(self.n, np.inf)
            pick = np.full(self.n, -1)
            for b in range(K):
                if b == a:
                    continue
                hw = f.width()[:, b] / 2
                hh = f.height()[:, b]
                tmin = _ray_entry(ex, ey, ez, lx[:, a], ly[:, a], lz[:, a], f.x[:, b] - hw, f.y[:, b], f.z[:, b] - hw,
                                  f.x[:, b] + hw, f.y[:, b] + hh, f.z[:, b] + hw)
                ok = f.alive[:, b] & (tmin <= f.reach[:, a]) & (tmin < best)
                best = np.where(ok, tmin, best)
                pick = np.where(ok, b, pick)
            if (pick >= 0).any():
                wall = self._block_distance(a, ex, ey, ez, lx[:, a], ly[:, a], lz[:, a], f.reach[:, a])
                pick = np.where(wall < best, -1, pick)
            hit = who & (pick >= 0)
            c = charge[:, a]
            crit = (c > 0.9) & ~f.on_ground[:, a] & (f.vy[:, a] < 0) & ~f.sprinting[:, a] & (f.fall[:, a] > 0)
            smash = weapon[:, a] & f.mace[:, a] & (f.fall[:, a] > 1.5)
            f.ticker[:, a] = np.where(who, 0, f.ticker[:, a])
            f.swing_age[:, a] = np.where(who, 0, f.swing_age[:, a])
            f.exhaustion[:, a] += np.where(who, 0.1, 0.0)
            self.whiffed[:, a] |= who & ~hit
            if not hit.any():
                continue
            for b in range(K):
                on = hit & (pick == b)
                if not on.any():
                    continue
                enemy = on & (f.team[:, a] != f.team[:, b])
                if not enemy.any():
                    continue
                # from b towards a, horizontally
                dx, dz = f.x[:, a] - f.x[:, b], f.z[:, a] - f.z[:, b]
                bl = np.radians(f.yaw[:, b])
                front = (-np.sin(bl) * dx + np.cos(bl) * dz) > 0
                blocked = enemy & f.blocking()[:, b] & front
                if blocked.any():
                    disable = blocked & weapon[:, a] & f.axe[:, a]
                    f.shield_cd[:, b] = np.where(disable, SHIELD_DISABLED, f.shield_cd[:, b])
                    f.using[:, b] &= ~disable
                    f.use_ticks[:, b] = np.where(disable, 0, f.use_ticks[:, b])
                    self.knockback(a, blocked, 0.5, dx, dz)
                land = enemy & ~blocked
                if not land.any():
                    continue
                dmg = np.where(weapon[:, a], f.atk_damage[:, a], 1.0) + f.strength[:, a]
                dmg = np.maximum(0.0, dmg) * (0.2 + 0.8 * c * c) * np.where(crit, 1.5, 1.0)
                dmg += np.where(weapon[:, a], f.sharp[:, a], 0.0) * c
                dmg += np.where(smash, mace_bonus(f.fall[:, a]), 0.0)
                full, partial = self.hurt(b, land, dmg, a, knock=(-dx, -dz), strength=0.4)
                hurt = full | partial
                self.landed[:, a] |= land
                f.hits[:, a] += land
                # the knockback enchantment and a charged sprint hit push along the attacker's look
                extra = np.where(weapon[:, a], f.kb_level[:, a], 0) + (f.sprinting[:, a] & (c > 0.9))
                push = hurt & (extra > 0)
                ry = np.radians(f.yaw[:, a])
                self.knockback(b, push, 0.5 * extra, -np.sin(ry), np.cos(ry))
                f.vx[:, a] = np.where(push, f.vx[:, a] * 0.6, f.vx[:, a])
                f.vz[:, a] = np.where(push, f.vz[:, a] * 0.6, f.vz[:, a])
                f.sprinting[:, a] &= ~push
                # after a smash the mace wielder stops falling
                sm = land & smash
                f.vy[:, a] = np.where(sm, 0.01, f.vy[:, a])
                f.fall[:, a] = np.where(sm, 0.0, f.fall[:, a])

    # ------------------------------------------------------------------ moving

    def _move(self, forward, strafe, jump):
        f, t = self.f, self.terrain
        alive = f.alive
        grounded = f.on_ground.copy()
        can_jump = jump & f.on_ground & (f.jump_delay == 0)
        ry = np.radians(f.yaw)
        fx, fz = -np.sin(ry), np.cos(ry)
        lx, lz = np.cos(ry), np.sin(ry)
        f.vy = np.where(can_jump, f.jump_power, f.vy)
        boost = can_jump & f.sprinting
        f.vx = f.vx + np.where(boost, fx * SPRINT_JUMP_BOOST, 0.0)
        f.vz = f.vz + np.where(boost, fz * SPRINT_JUMP_BOOST, 0.0)
        f.exhaustion += np.where(can_jump, np.where(f.sprinting, 0.2, 0.05), 0.0)
        f.jump_delay = np.where(can_jump, 10, np.where(jump, np.maximum(0, f.jump_delay - 1), 0))
        speed = np.where(grounded, f.walk_speed * np.where(f.sprinting, 1.3, 1.0), np.where(f.sprinting, 0.026, 0.02))
        slow = np.where(f.using, 0.2, 1.0)
        mf = forward * INPUT_SCALE * slow
        ms = strafe * INPUT_SCALE * slow
        ln = np.sqrt(mf * mf + ms * ms)
        norm = np.where(ln > 1.0, ln, 1.0)
        mf, ms = mf / norm * speed, ms / norm * speed
        f.vx = np.where(alive, f.vx + ms * lx + mf * fx, f.vx)
        f.vz = np.where(alive, f.vz + ms * lz + mf * fz, f.vz)
        self._push_apart()
        x0, z0 = f.x.copy(), f.z.copy()
        self._collide(grounded)
        moved = np.hypot(f.x - x0, f.z - z0)
        f.exhaustion += np.where(f.sprinting, 0.1 * moved, 0.0)
        keep = np.where(grounded, GROUND_KEEP, AIR_DRAG)
        f.vx = np.where(alive, f.vx * keep, 0.0)
        f.vz = np.where(alive, f.vz * keep, 0.0)
        f.vy = np.where(alive, (f.vy - f.gravity) * DRAG_Y, 0.0)

    def _push_apart(self):
        f, K = self.f, self.k
        for a in range(K):
            for b in range(a + 1, K):
                dx = f.x[:, b] - f.x[:, a]
                dz = f.z[:, b] - f.z[:, a]
                d = np.hypot(dx, dz)
                reach = (f.width()[:, a] + f.width()[:, b]) / 2
                close = f.alive[:, a] & f.alive[:, b] & (d < reach) & (d > 1e-4) & (np.abs(f.y[:, a] - f.y[:, b]) < 1.8)
                if not close.any():
                    continue
                k = np.where(close, 0.05 / np.maximum(d, 1e-4), 0.0)
                f.vx[:, a] -= dx * k
                f.vz[:, a] -= dz * k
                f.vx[:, b] += dx * k
                f.vz[:, b] += dz * k

    def _collide(self, grounded):
        """Moves everyone by its velocity against the columns: up/down first, then x, then z (the game's order for a
        step), stepping up to 0.6 when on the ground; fall distance and fall damage on landing."""
        f, t = self.f, self.terrain
        alive = f.alive
        rows = np.repeat(np.arange(self.n)[:, None], self.k, axis=1)
        hw = f.width() / 2
        # vertical
        under = t.support(rows, f.x, f.z, hw, f.y)
        ny = f.y + f.vy
        land = alive & (f.vy <= 0) & (ny <= under) & (f.y >= under - 1e-6)
        # standing inside ground (pushed into a step): up onto it
        inside = alive & (f.y < under - 1e-6) & (under - f.y <= STEP_UP + 1e-6)
        falling = alive & ~land & (ny < f.y)
        f.fall = np.where(falling, f.fall + (f.y - ny), np.where(land, f.fall + np.maximum(0.0, f.y - under), f.fall))
        f.y = np.where(land | inside, under, np.where(alive, ny, f.y))
        f.vy = np.where(land, 0.0, f.vy)
        was_ground = grounded
        f.on_ground = land | inside
        # horizontal, one axis at a time, in steps of at most 0.45 blocks (a hard push must not skip a wall)
        sub = int(np.clip(np.ceil(np.max(np.where(alive, np.maximum(np.abs(f.vx), np.abs(f.vz)), 0.0)) / 0.45), 1, 8))
        for _ in range(sub):
            for pos, vel in (("x", "vx"), ("z", "vz")):
                p, v = getattr(f, pos), getattr(f, vel) / sub
                np_ = p + v
                if pos == "x":
                    g = t.footprint(rows, np_, f.z, hw)
                else:
                    g = t.footprint(rows, f.x, np_, hw)
                free = g <= f.y + 1e-6
                step = ~free & (was_ground | f.on_ground) & (g <= f.y + STEP_UP + 1e-6)
                blocked = alive & ~free & ~step
                # against the face of the blocking column
                edge = np.where(v > 0, np.floor(np_ + hw) - hw - 1e-4, np.ceil(np_ - hw) + hw + 1e-4)
                edge = np.where(v > 0, np.maximum(p, np.minimum(edge, np_)), np.minimum(p, np.maximum(edge, np_)))
                setattr(f, pos, np.where(blocked, edge, np.where(alive, np_, p)))
                f.y = np.where(alive & step, g, f.y)
                setattr(f, vel, np.where(blocked, 0.0, getattr(f, vel)))
        # landing: fall damage beyond the safe distance (3, and one more per jump boost level); after a wind charge
        # only what lies below the height it pushed from
        under = t.support(rows, f.x, f.z, hw, f.y)
        f.on_ground = alive & (np.abs(f.y - under) < 1e-6) & (f.vy <= 0)
        landed = f.on_ground & ~was_ground
        dist = np.where(f.ignore_fall, np.minimum(f.fall, f.impulse_y - f.y), f.fall)
        safe = 3.0 + np.round((f.jump_power - 0.42) / 0.1)
        dmg = np.ceil(np.maximum(0.0, dist - safe))
        hurt = landed & (dmg > 0)
        if hurt.any():
            for b in range(self.k):
                self.hurt(b, hurt[:, b], dmg[:, b], -1, armor=False)
        f.fall = np.where(f.on_ground, 0.0, f.fall)
        f.ignore_fall &= ~f.on_ground

    # ------------------------------------------------------------------ projectiles

    def _projectiles(self):
        p, f, t = self.p, self.f, self.terrain
        if not p.active.any():
            return
        n, P = p.active.shape
        rows = np.repeat(np.arange(n)[:, None], P, axis=1)
        # where along this tick's move it hits a fighter (0..1) and whom
        best = np.full((n, P), np.inf)
        whom = np.full((n, P), -1)
        for k in range(self.k):
            hw = (f.width()[:, k] / 2 + 0.3)[:, None]
            top = (f.height()[:, k] + 0.3)[:, None]
            tk = _segment_entry(p.x, p.y, p.z, p.vx, p.vy, p.vz, f.x[:, k][:, None] - hw, f.y[:, k][:, None] - 0.3,
                                f.z[:, k][:, None] - hw, f.x[:, k][:, None] + hw, f.y[:, k][:, None] + top, f.z[:, k][:, None] + hw)
            ok = p.active & f.alive[:, k][:, None] & (p.owner != k) & (tk < best)
            best = np.where(ok, tk, best)
            whom = np.where(ok, k, whom)
        # and where it goes into the ground (the last point before, for pearls and wind charges)
        speed = np.sqrt(p.vx ** 2 + p.vy ** 2 + p.vz ** 2)
        steps = int(np.clip(np.ceil(np.max(np.where(p.active, speed, 0.0)) / 0.25), 1, 16))
        ground = np.full((n, P), np.inf)
        for s in range(1, steps + 1):
            u = s / steps
            g = t.column(rows, p.x + p.vx * u, p.z + p.vz * u)
            inside = (p.y + p.vy * u) < g
            ground = np.where(np.isinf(ground) & inside & p.active, u, ground)
        on_fighter = p.active & (whom >= 0) & (best <= ground) & (best <= 1.0)
        on_ground = p.active & ~on_fighter & (ground <= 1.0)
        u_hit = np.where(on_fighter, best, np.where(on_ground, np.maximum(0.0, ground - 1.0 / steps), 1.0))
        hx, hy, hz = p.x + p.vx * u_hit, p.y + p.vy * u_hit, p.z + p.vz * u_hit
        for a, j in zip(*np.nonzero(on_fighter | on_ground)):
            self._impact(a, j, int(whom[a, j]) if on_fighter[a, j] else -1, hx[a, j], hy[a, j], hz[a, j])
        flying = p.active & ~on_fighter & ~on_ground
        p.x = np.where(flying, p.x + p.vx, p.x)
        p.y = np.where(flying, p.y + p.vy, p.y)
        p.z = np.where(flying, p.z + p.vz, p.z)
        drag = np.select([p.kind == ARROW, p.kind == PEARL], [DRAG_OF[ARROW], DRAG_OF[PEARL]], DRAG_OF[WIND])
        grav = np.select([p.kind == ARROW, p.kind == PEARL], [GRAVITY_OF[ARROW], GRAVITY_OF[PEARL]], GRAVITY_OF[WIND])
        p.vx = np.where(flying, p.vx * drag, p.vx)
        p.vz = np.where(flying, p.vz * drag, p.vz)
        p.vy = np.where(flying, p.vy * drag - grav, p.vy)
        p.age += flying
        lost = flying & ((p.age > 200) | (p.y < (t.floor[:, None] - VOID_BELOW - 8)))
        p.active &= ~(on_fighter | on_ground | lost)

    def _impact(self, a, j, k, x, y, z):
        """Projectile j of arena a hits fighter k (-1: the ground) at x, y, z."""
        p, f = self.p, self.f
        kind, owner = p.kind[a, j], p.owner[a, j]
        mask = np.zeros(self.n, dtype=bool)
        mask[a] = True
        if kind == ARROW and k >= 0:
            if f.team[a, k] == f.team[a, owner]:
                return
            dx, dz = f.x[a, owner] - f.x[a, k], f.z[a, owner] - f.z[a, k]
            yl = np.radians(f.yaw[a, k])
            if f.blocking()[a, k] and (-np.sin(yl) * dx + np.cos(yl) * dz) > 0:
                return
            speed = np.sqrt(p.vx[a, j] ** 2 + p.vy[a, j] ** 2 + p.vz[a, j] ** 2)
            dmg = float(np.ceil(speed * p.base[a, j]))
            if p.crit[a, j]:
                dmg += float(self.rng.integers(0, int(dmg) // 2 + 2))
            amount = np.zeros(self.n)
            amount[a] = dmg
            kx, kz = np.zeros(self.n), np.zeros(self.n)
            kx[a], kz[a] = p.vx[a, j], p.vz[a, j]
            self.hurt(k, mask, amount, owner, knock=(kx, kz), strength=0.4)
        elif kind == PEARL:
            if not f.alive[a, owner]:
                return
            # where it landed, backed off along its path until the body is clear of anything it cannot step onto
            hw = f.width()[a, owner] / 2
            v = np.array([p.vx[a, j], p.vy[a, j], p.vz[a, j]])
            back = -v / max(np.linalg.norm(v), 1e-9) * 0.3
            for _ in range(6):
                under = self.terrain.footprint(np.array([a]), np.array([x]), np.array([z]), np.array([hw]))[0]
                if under <= y + STEP_UP:
                    break
                x, y, z = x + back[0], y + back[1], z + back[2]
            f.x[a, owner], f.y[a, owner], f.z[a, owner] = x, y, z
            f.vx[a, owner] = f.vy[a, owner] = f.vz[a, owner] = 0.0
            f.fall[a, owner] = 0.0
            f.on_ground[a, owner] = False
            amount = np.zeros(self.n)
            amount[a] = PEARL_DAMAGE
            self.hurt(owner, mask, amount, -1, armor=False)
        elif kind == WIND:
            if k >= 0 and f.team[a, k] != f.team[a, owner]:
                amount = np.zeros(self.n)
                amount[a] = 1.0
                kx, kz = np.zeros(self.n), np.zeros(self.n)
                kx[a], kz[a] = p.vx[a, j], p.vz[a, j]
                self.hurt(k, mask, amount, owner, knock=(kx, kz), strength=0.4)
            self._burst(a, x, y, z)

    def _burst(self, a, cx, cy, cz):
        """A wind charge's explosion: everyone within 2.4 blocks is pushed away from it (towards the eyes)."""
        f = self.f
        for k in range(self.k):
            if not f.alive[a, k]:
                continue
            d = np.sqrt((f.x[a, k] - cx) ** 2 + (f.y[a, k] - cy) ** 2 + (f.z[a, k] - cz) ** 2) / WIND_RADIUS
            if d > 1.0:
                continue
            dx, dy, dz = f.x[a, k] - cx, f.y[a, k] + f.eye()[a, k] - cy, f.z[a, k] - cz
            ln = np.sqrt(dx * dx + dy * dy + dz * dz)
            if ln < 1e-9:
                continue
            push = (1.0 - d) * WIND_KNOCKBACK
            f.vx[a, k] += dx / ln * push
            f.vy[a, k] += dy / ln * push
            f.vz[a, k] += dz / ln * push
            f.on_ground[a, k] = False
            f.ignore_fall[a, k] = True
            f.impulse_y[a, k] = f.y[a, k]
            f.fall[a, k] = 0.0

    # ------------------------------------------------------------------ food, healing, deaths

    def _hunger(self):
        f = self.f
        alive = f.alive
        sat = f.saturation_effect & alive
        f.food = np.where(sat, 20.0, f.food)
        f.saturation = np.where(sat, 20.0, f.saturation)
        over = f.exhaustion > 4.0
        f.exhaustion = np.where(over, f.exhaustion - 4.0, f.exhaustion)
        f.food = np.where(over & (f.saturation <= 0), np.maximum(0.0, f.food - 1), f.food)
        f.saturation = np.where(over & (f.saturation > 0), np.maximum(0.0, f.saturation - 1), f.saturation)
        hurt = alive & (f.health < 20.0)
        regen = self.natural_regen[:, None] & hurt
        fast = regen & (f.saturation > 0) & (f.food >= 20)
        slow = regen & ~fast & (f.food >= 18)
        f.regen_timer = np.where(fast | slow, f.regen_timer + 1, 0)
        use = np.minimum(f.saturation, 6.0)
        heal_fast = fast & (f.regen_timer >= 10)
        heal_slow = slow & (f.regen_timer >= 80)
        amount = np.where(heal_fast, use / 6.0, np.where(heal_slow, 1.0, 0.0))
        f.exhaustion += np.where(heal_fast, use, np.where(heal_slow, 6.0, 0.0))
        f.regen_timer = np.where(heal_fast | heal_slow, 0, f.regen_timer)
        # regeneration II (a golden apple): one every 25 ticks
        f.regen_effect = np.maximum(-1, f.regen_effect - 1)
        eff = alive & (f.regen_effect >= 0) & (f.regen_effect % 25 == 0) & hurt & (f.regen_effect < 100)
        amount = amount + np.where(eff, 1.0, 0.0)
        new = np.minimum(20.0, f.health + amount)
        self.healed += np.where(alive, new - f.health, 0.0)
        f.health = np.where(alive, new, f.health)

    def _deaths(self):
        f = self.f
        fell = f.alive & (f.y < self.terrain.floor[:, None] - VOID_BELOW)
        dead = f.alive & ((f.health <= 0) | fell)
        if not dead.any():
            return
        self.died |= dead
        credit = dead & (f.last_attacker >= 0) & (f.last_attacker_age <= KILL_CREDIT)
        self.killer = np.where(credit, f.last_attacker, -1)
        f.health = np.where(dead, 0.0, f.health)
        f.alive &= ~dead
        f.using &= ~dead
        f.sprinting &= ~dead


def _ray_entry(ex, ey, ez, lx, ly, lz, minx, miny, minz, maxx, maxy, maxz):
    """Distance along the look where the ray enters the box (0 inside, inf: it misses)."""
    tmin = np.full(np.shape(ex), -np.inf)
    tmax = np.full(np.shape(ex), np.inf)
    ok = np.ones(np.shape(ex), dtype=bool)
    for e, l, lo, hi in ((ex, lx, minx, maxx), (ey, ly, miny, maxy), (ez, lz, minz, maxz)):
        par = np.abs(l) < 1e-12
        ok &= ~(par & ((e < lo) | (e > hi)))
        safe = np.where(par, 1.0, l)
        t1, t2 = (lo - e) / safe, (hi - e) / safe
        tmin = np.maximum(tmin, np.where(par, -np.inf, np.minimum(t1, t2)))
        tmax = np.minimum(tmax, np.where(par, np.inf, np.maximum(t1, t2)))
    hit = ok & (tmax >= np.maximum(tmin, 0.0))
    return np.where(hit, np.maximum(tmin, 0.0), np.inf)


def _segment_entry(x, y, z, vx, vy, vz, minx, miny, minz, maxx, maxy, maxz):
    """Where along the move (0..1) a point moving by v enters the box (inf: not this tick)."""
    t = _ray_entry(x, y, z, vx, vy, vz, minx, miny, minz, maxx, maxy, maxz)
    return np.where(t <= 1.0, t, np.inf)


__all__ = ["World", "Fighters", "Projectiles", "HANDS", "ARROW", "PEARL", "WIND", "bow_power", "mace_bonus", "ray_hits_box"]
