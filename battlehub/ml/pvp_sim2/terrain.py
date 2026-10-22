"""The ground of n arenas as heightmaps, the terrain rays of the spec, and generators for the kinds of maps the modes
are played on.

`ground[a, i, j]` is the top of the ground in the 1x1 column i, j of arena a (NaN: none, the void); column i covers
x in [i - G/2, i - G/2 + 1), likewise j and z. A heightmap has no overhangs, which is enough for a fight: houses are
walls, bridges and islands float over the void.

The rays (rays(), behind(), below()) are the spec's: a column's ground as the bot finds it (top()) is NaN when it is
more than `depth` below the feet; every `sample` blocks along a line, up to ray_max:
  void  the column has no ground,
  drop  no ground or more than `drop` below the feet,
  step  more than `step_up` above the feet (a jump is needed),
  wall  more than `jump_over` above the feet (no jumping over it);
a distance is that of the last sample before the condition (0 when the first meets it, ray_max when none does).
The mod computes the same in net.battlehub.core.ml.TerrainRays over the blocks of the world.
"""
import numpy as np

from .spec import SPEC2

WALL = 6.0  # height of an arena wall: nothing gets over it


class Terrain:
    def __init__(self, n, size=64, spec=SPEC2):
        self.n = n
        self.size = size
        self.spec = spec
        self.ground = np.full((n, size, size), np.nan)
        self.floor = np.zeros(n)  # the lowest ground of each arena (falling far below it is the void's death)

    # ------------------------------------------------------------------ columns

    def cells(self, x, z):
        h, size = self.size // 2, self.size
        i = np.floor(np.asarray(x) + h).astype(np.int64)
        j = np.floor(np.asarray(z) + h).astype(np.int64)
        inside = (i >= 0) & (i < size) & (j >= 0) & (j < size)
        return np.minimum(np.maximum(i, 0), size - 1), np.minimum(np.maximum(j, 0), size - 1), inside

    def column(self, rows, x, z):
        """The ground of the columns under points (NaN for the void and outside the map); rows, x, z broadcast."""
        i, j, inside = self.cells(x, z)
        g = self.ground[rows, i, j]
        return np.where(inside, g, np.nan)

    def top(self, rows, x, z, y):
        """A column's ground as the rays see it: NaN when there is none within `depth` below the feet at y."""
        g = self.column(rows, x, z)
        return np.where(g >= np.asarray(y) - self.spec.depth, g, np.nan)

    def footprint(self, rows, x, z, hw):
        """The highest ground under a body of half-width hw (< 1) at x, z: its corners, edges and middle."""
        rows, x, z, hw = np.broadcast_arrays(rows, x, z, hw)
        o = np.array([-1.0, 0.0, 1.0])
        ox = np.repeat(o, 3)
        oz = np.tile(o, 3)
        # just inside the body's edge, so a body touching a column's side does not stand on it
        e = (hw - 1e-7)[..., None]
        g = self.column(rows[..., None], x[..., None] + ox * e, z[..., None] + oz * e)
        best = np.fmax.reduce(g, axis=-1)
        return np.where(np.isnan(best), -np.inf, best)  # -inf: nothing at all under it

    def support(self, rows, x, z, hw, y, step=0.6):
        """The highest ground under a body that holds it up: what is no more than `step` above its feet (a wall it
        overlaps, after a teleport or at a size of 2, does not count)."""
        rows, x, z, hw, y = np.broadcast_arrays(rows, x, z, hw, y)
        o = np.array([-1.0, 0.0, 1.0])
        e = (hw - 1e-7)[..., None]
        g = self.column(rows[..., None], x[..., None] + np.repeat(o, 3) * e, z[..., None] + np.tile(o, 3) * e)
        g = np.where(g <= y[..., None] + step + 1e-6, g, np.nan)
        best = np.fmax.reduce(g, axis=-1)
        return np.where(np.isnan(best), -np.inf, best)

    # ------------------------------------------------------------------ the spec's rays

    def _samples(self):
        s = self.spec
        count = int(round(s.ray_max / s.sample))
        return s.sample * np.arange(1, count + 1, dtype=np.float64)

    def _first(self, cond):
        """Distance of the last sample before the first that meets cond (along the last axis)."""
        s = self.spec
        hit = cond.any(axis=-1)
        idx = np.argmax(cond, axis=-1)
        return np.where(hit, idx * s.sample, s.ray_max)

    def rays(self, rows, x, y, z, yaw):
        """void, drop, step and wall distances (m, 8) in the bot's own directions: 0 ahead, then every 45 degrees to the
        left."""
        s = self.spec
        ry = np.radians(yaw)
        fx, fz = -np.sin(ry), np.cos(ry)
        lx, lz = np.cos(ry), np.sin(ry)
        ts = self._samples()
        a = np.radians(np.arange(s.ray_directions) * 360.0 / s.ray_directions)
        ca, sa = np.cos(a), np.sin(a)
        dx = fx[:, None] * ca[None, :] + lx[:, None] * sa[None, :]
        dz = fz[:, None] * ca[None, :] + lz[:, None] * sa[None, :]
        px = x[:, None, None] + dx[:, :, None] * ts[None, None, :]
        pz = z[:, None, None] + dz[:, :, None] * ts[None, None, :]
        yy = y[:, None, None]
        g = self.top(np.asarray(rows)[:, None, None], px, pz, yy)
        none = np.isnan(g)
        return (self._first(none), self._first(none | (g < yy - s.drop)), self._first(~none & (g > yy + s.step_up)),
                self._first(~none & (g > yy + s.jump_over)))

    def behind(self, rows, x, y, z, dx, dz):
        """From a body at x, y, z along the horizontal unit direction dx, dz: ground before a drop, and before the
        void."""
        s = self.spec
        ts = self._samples()
        px = x[:, None] + dx[:, None] * ts[None, :]
        pz = z[:, None] + dz[:, None] * ts[None, :]
        g = self.top(rows[:, None], px, pz, y[:, None])
        none = np.isnan(g)
        return self._first(none | (g < y[:, None] - s.drop)), self._first(none)

    def below(self, rows, x, y, z):
        """How far the ground is below the feet (depth when there is none within it)."""
        g = self.top(rows, x, z, y)
        return np.where(np.isnan(g), self.spec.depth, np.clip(y - g, 0.0, self.spec.depth))

    def sight(self, rows, ax, ay, az, bx, by, bz, step=0.5):
        """Nothing of the ground in between the two points (a heightmap blocks a line that passes below its top)."""
        d = np.hypot(bx - ax, bz - az)
        clear = np.ones(np.shape(ax), dtype=bool)
        if np.size(d) == 0:
            return clear
        count = int(np.ceil(np.max(d) / step))
        if count <= 1:
            return clear
        s = step * np.arange(1, count, dtype=np.float64)[None, :]
        f = np.minimum(1.0, s / np.maximum(d, 1e-9)[:, None])
        g = self.column(np.asarray(rows)[:, None], (ax + (bx - ax) * f.T).T, (az + (bz - az) * f.T).T)
        y = (ay + (by - ay) * f.T).T
        blocked = (s < d[:, None]) & (g > y)
        return ~blocked.any(axis=1)

    # ------------------------------------------------------------------ maps

    def clear(self, a):
        self.ground[a] = np.nan

    def fill_box(self, a, x0, z0, x1, z1, h):
        """Columns with x in [x0, x1), z in [z0, z1) get ground at h (block coordinates)."""
        hs = self.size // 2
        i0, i1 = int(np.clip(np.floor(x0) + hs, 0, self.size)), int(np.clip(np.ceil(x1) + hs, 0, self.size))
        j0, j1 = int(np.clip(np.floor(z0) + hs, 0, self.size)), int(np.clip(np.ceil(z1) + hs, 0, self.size))
        self.ground[a, i0:i1, j0:j1] = h

    def fill_disc(self, a, cx, cz, r, h, rng=None, rough=0.0):
        hs = self.size // 2
        c = np.arange(self.size) - hs + 0.5
        X, Z = np.meshgrid(c, c, indexing="ij")
        rr = r + (rough * rng.uniform(-1, 1, size=X.shape) if rng is not None and rough > 0 else 0.0)
        self.ground[a][np.hypot(X - cx, Z - cz) <= rr] = h

    def fill_line(self, a, x0, z0, x1, z1, width, h):
        """A straight bridge of the given width from (x0, z0) to (x1, z1)."""
        hs = self.size // 2
        c = np.arange(self.size) - hs + 0.5
        X, Z = np.meshgrid(c, c, indexing="ij")
        dx, dz = x1 - x0, z1 - z0
        ln = max(np.hypot(dx, dz), 1e-9)
        t = np.clip(((X - x0) * dx + (Z - z0) * dz) / (ln * ln), 0.0, 1.0)
        d = np.hypot(X - (x0 + t * dx), Z - (z0 + t * dz))
        self.ground[a][d <= width / 2] = h

    def standable(self, a):
        """Columns a body can stand on: ground, not a wall top, with the same ground in the 4 neighbours within 0.6."""
        g = self.ground[a]
        ok = np.isfinite(g) & (g < WALL - 0.5)
        for di, dj in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            nb = np.roll(np.roll(g, di, axis=0), dj, axis=1)
            ok &= np.isfinite(nb) & (np.abs(nb - g) <= 0.6)
        ok[0, :] = ok[-1, :] = ok[:, 0] = ok[:, -1] = False
        return ok


# ---------------------------------------------------------------------------------------------- generators

def walled(t, a, rng):
    """A square arena with walls (the duel arenas), now and then with pillars and low cover."""
    half = int(rng.integers(7, 15))
    t.clear(a)
    t.fill_box(a, -half - 1, -half - 1, half + 1, half + 1, WALL)
    t.fill_box(a, -half, -half, half, half, 0.0)
    for _ in range(int(rng.integers(0, 4))):
        x, z = rng.integers(-half + 2, half - 2, size=2)
        w, d = rng.integers(1, 3, size=2)
        t.fill_box(a, x, z, x + w, z + d, float(rng.choice([1.0, 2.0, WALL])))
    return "walled"


def platform(t, a, rng):
    """A platform over the void (sumo): round or square."""
    t.clear(a)
    r = rng.uniform(4.5, 10.0)
    if rng.random() < 0.6:
        t.fill_disc(a, 0.0, 0.0, r, 0.0)
    else:
        t.fill_box(a, -r, -r, r, r, 0.0)
    return "platform"


def bridge(t, a, rng):
    """Two islands joined by a narrow bridge over the void (The Bridge, Bed Wars)."""
    t.clear(a)
    length = rng.uniform(12.0, 26.0)
    ang = rng.uniform(0, 2 * np.pi)
    ux, uz = np.cos(ang), np.sin(ang)
    r = rng.uniform(3.5, 6.5)
    h = float(rng.choice([0.0, 0.0, 1.0]))
    for s in (-1, 1):
        cx, cz = s * ux * (length / 2 + r - 1), s * uz * (length / 2 + r - 1)
        t.fill_disc(a, cx, cz, r, h + (0.0 if s < 0 else float(rng.choice([0.0, 0.0, 1.0, -1.0]))), rng, rough=0.6)
    width = float(rng.choice([1.0, 1.0, 2.0, 3.0]))
    t.fill_line(a, -ux * length / 2, -uz * length / 2, ux * length / 2, uz * length / 2, width, h)
    if rng.random() < 0.3:
        # a side bridge of one block, or a gap that has to be jumped
        t.fill_line(a, -ux * 3 - uz * 3, -uz * 3 + ux * 3, ux * 3 - uz * 3, uz * 3 + ux * 3, 1.0, h)
    return "bridge"


def islands(t, a, rng):
    """Islands at different heights with bridges of one or two blocks between them (SkyWars); a higher island has slab
    steps round it down to its bridges."""
    t.clear(a)
    count = int(rng.integers(3, 6))
    pts = []
    for _ in range(count):
        for _ in range(20):
            p = rng.uniform(-20, 20, size=2)
            if all(np.hypot(*(p - q)) > 10 for q in pts):
                break
        pts.append(p)
    hs = [float(rng.integers(0, 3)) for _ in pts]
    rs = [rng.uniform(3.0, 7.0) for _ in pts]
    order = np.argsort([np.hypot(*p) for p in pts])
    low = {}
    for k in range(1, count):
        i, j = order[k - 1], order[k]
        h = min(hs[i], hs[j])
        t.fill_line(a, pts[i][0], pts[i][1], pts[j][0], pts[j][1], float(rng.choice([1.0, 1.0, 2.0])), h)
        low[i] = min(low.get(i, hs[i]), h)
        low[j] = min(low.get(j, hs[j]), h)
    for i, ((cx, cz), h, r) in enumerate(zip(pts, hs, rs)):
        levels = int(round((h - low.get(i, h)) / 0.5))
        for k in range(levels, 0, -1):
            t.fill_disc(a, cx, cz, r + k * 0.7, h - 0.5 * k)
        t.fill_disc(a, cx, cz, r, h, rng, rough=0.8)
        if rng.random() < 0.6:
            # a tree or a chest pile
            ox, oz = rng.integers(-2, 2, size=2)
            t.fill_box(a, cx + ox, cz + oz, cx + ox + 1, cz + oz + 1, h + float(rng.choice([1.0, 4.0])))
    return "islands"


def town(t, a, rng):
    """Streets between houses, low walls, steps and slabs (the Hunger Games' lobby city)."""
    t.clear(a)
    half = int(rng.integers(14, 26))
    t.fill_box(a, -half, -half, half, half, 0.0)
    for _ in range(int(rng.integers(4, 10))):
        x, z = rng.integers(-half, half - 4, size=2)
        w, d = rng.integers(3, 8, size=2)
        t.fill_box(a, x, z, x + w, z + d, WALL)
    for _ in range(int(rng.integers(3, 9))):
        x, z = rng.integers(-half, half - 2, size=2)
        if rng.random() < 0.5:
            t.fill_box(a, x, z, x + int(rng.integers(3, 9)), z + 1, float(rng.choice([1.0, 1.5, 2.0])))
        else:
            t.fill_box(a, x, z, x + 1, z + int(rng.integers(3, 9)), float(rng.choice([1.0, 1.5, 2.0])))
    for _ in range(int(rng.integers(1, 4))):
        # a raised square with slab steps
        x, z = rng.integers(-half + 3, half - 8, size=2)
        w = int(rng.integers(3, 7))
        t.fill_box(a, x - 1, z - 1, x + w + 1, z + w + 1, 0.5)
        t.fill_box(a, x, z, x + w, z + w, 1.0)
    if rng.random() < 0.5:
        # the edge of the map is a wall, else the void
        t.fill_box(a, -half - 1, -half - 1, half + 1, -half, WALL)
        t.fill_box(a, -half - 1, half, half + 1, half + 1, WALL)
        t.fill_box(a, -half - 1, -half, -half, half, WALL)
        t.fill_box(a, half, -half, half + 1, half, WALL)
    return "town"


def hills(t, a, rng):
    """Rolling ground in half blocks, with the void or walls round it."""
    t.clear(a)
    half = int(rng.integers(10, 22))
    hs = t.size // 2
    c = np.arange(t.size) - hs + 0.5
    X, Z = np.meshgrid(c, c, indexing="ij")
    h = np.zeros_like(X)
    for _ in range(4):
        kx, kz = rng.uniform(0.08, 0.3, size=2)
        ph = rng.uniform(0, 2 * np.pi, size=2)
        h += rng.uniform(0.4, 1.2) * np.sin(kx * X + ph[0]) * np.sin(kz * Z + ph[1])
    h = np.round((h - h.min()) * 2) / 2
    h = np.minimum(h, 4.0)
    inside = (np.abs(X) < half) & (np.abs(Z) < half)
    t.ground[a] = np.where(inside, h, np.nan)
    return "hills"


GENERATORS = {"walled": walled, "platform": platform, "bridge": bridge, "islands": islands, "town": town, "hills": hills}


def generate(t, a, kind, rng):
    GENERATORS[kind](t, a, rng)
    g = t.ground[a]
    t.floor[a] = np.nanmin(g) if np.isfinite(g).any() else 0.0
    return kind
