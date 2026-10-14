"""Shared machinery for the Elders' models: parts and boxes, forward kinematics, ropes, chains and lanterns,
UV packing, procedural materials (base and emissive textures) and the Java geometry writer.

Model space is Minecraft's: pixels, Y down, the creature faces -Z, the ground is at y = 24 under the root.
"""
import math
import random
import zlib

from PIL import Image

# ----------------------------------------------------------------------------- 3x4 transforms


def _rx(a):
    c, s = math.cos(a), math.sin(a)
    return [[1, 0, 0], [0, c, -s], [0, s, c]]


def _ry(a):
    c, s = math.cos(a), math.sin(a)
    return [[c, 0, s], [0, 1, 0], [-s, 0, c]]


def _rz(a):
    c, s = math.cos(a), math.sin(a)
    return [[c, -s, 0], [s, c, 0], [0, 0, 1]]


def _mm(a, b):
    return [[sum(a[i][k] * b[k][j] for k in range(3)) for j in range(3)] for i in range(3)]


def rot_zyx(z, y, x):
    """JOML's rotationZYX(z, y, x): the matrix Rz * Ry * Rx (what ModelPart applies)."""
    return _mm(_mm(_rz(z), _ry(y)), _rx(x))


def mv(m, v):
    return [m[i][0] * v[0] + m[i][1] * v[1] + m[i][2] * v[2] for i in range(3)]


def transpose(m):
    return [[m[j][i] for j in range(3)] for i in range(3)]


class Xf:
    """A rigid transform: rotation r (3x3) and translation t."""

    def __init__(self, r=None, t=None):
        self.r = r or [[1, 0, 0], [0, 1, 0], [0, 0, 1]]
        self.t = t or [0.0, 0.0, 0.0]

    def then(self, pivot, rot):
        # this * T(pivot) * R(rot)
        t = [self.t[i] + mv(self.r, pivot)[i] for i in range(3)]
        return Xf(_mm(self.r, rot_zyx(rot[2], rot[1], rot[0])), t)

    def apply(self, p):
        q = mv(self.r, p)
        return [q[i] + self.t[i] for i in range(3)]

    def inv_apply(self, p):
        d = [p[i] - self.t[i] for i in range(3)]
        return mv(transpose(self.r), d)

    def inv_dir(self, v):
        return mv(transpose(self.r), v)


def align_y(t):
    """Rotation (xRot, yRot, zRot) whose local +Y points along unit vector t (with yRot = 0)."""
    n = math.sqrt(sum(c * c for c in t)) or 1.0
    tx, ty, tz = (c / n for c in t)
    x = math.asin(max(-1.0, min(1.0, tz)))
    z = math.atan2(-tx, ty)
    return (x, 0.0, z)


def norm(v):
    n = math.sqrt(sum(c * c for c in v)) or 1.0
    return [c / n for c in v]


def lerp3(a, b, k):
    return [a[i] + (b[i] - a[i]) * k for i in range(3)]


def catenary(p0, p1, sag, n):
    """n+1 points from p0 to p1 sagging by `sag` pixels (downwards, +Y) at the middle."""
    pts = []
    for i in range(n + 1):
        k = i / n
        p = lerp3(p0, p1, k)
        p[1] += sag * 4 * k * (1 - k)
        pts.append(p)
    return pts


# ----------------------------------------------------------------------------- the model


class Model:
    def __init__(self, cls, tw, th, seed):
        self.cls = cls
        self.tw, self.th = tw, th
        self.parts = []
        self.idx = {}
        self.rnd = random.Random(seed)
        self.seed = seed

    def part(self, name, parent, pivot, rot=(0, 0, 0), anim=False, group=None):
        assert name not in self.idx, name
        assert parent is None or parent in self.idx, (name, parent)
        p = dict(name=name, parent=parent, pivot=[float(c) for c in pivot], rot=[float(c) for c in rot], boxes=[],
                 anim=anim or group is not None, group=group)
        self.idx[name] = p
        self.parts.append(p)
        return name

    def box(self, part, x, y, z, w, h, d, mat, inflate=0.0, tag=None):
        self.idx[part]["boxes"].append(dict(b=(x, y, z, w, h, d), mat=mat, inflate=inflate, tag=tag))

    def xf(self, name):
        chain = []
        while name is not None:
            chain.append(self.idx[name])
            name = self.idx[name]["parent"]
        x = Xf()
        for p in reversed(chain):
            x = x.then(p["pivot"], p["rot"])
        return x

    # --- composite helpers -------------------------------------------------

    def chain_of(self, prefix, parent, pivot, rots, lens, thick, mat, up=True, anim=False, taper=None, tips=None, group=None):
        """Nested segments, each starting where the last ended; up = segments run along -Y (else +Y)."""
        names = []
        prev = parent
        for i, (r, L, t) in enumerate(zip(rots, lens, thick)):
            pv = pivot if i == 0 else (0, -lens[i - 1] if up else lens[i - 1], 0)
            n = self.part(f"{prefix}{i}", prev, pv, r, anim=anim, group=group if i == 0 else None)
            y0 = -L if up else 0
            self.box(n, -t / 2, y0, -t / 2, t, L, t, mat[i] if isinstance(mat, (list, tuple)) else mat)
            names.append(n)
            prev = n
        return names

    def world_rot(self, parent):
        """Local rotation that cancels the parent's: children of the result stay level with the model."""
        r = transpose(self.xf(parent).r)
        y = math.asin(max(-1.0, min(1.0, -r[2][0])))
        x = math.atan2(r[2][1], r[2][2])
        z = math.atan2(r[1][0], r[0][0])
        return (x, y, z)

    def to_local(self, parent, p):
        return self.xf(parent).inv_apply(p)

    def hang_rot(self, parent):
        """Local rotation that makes +Y point straight down (model +Y) under this parent."""
        down = self.xf(parent).inv_dir([0, 1, 0])
        return align_y(down)

    def rope(self, prefix, parent, pts, thick, mat, links=False):
        """Segments along points given in the parent's space; chain links alternate their flat side."""
        names = []
        for i in range(len(pts) - 1):
            a, b = pts[i], pts[i + 1]
            d = [b[k] - a[k] for k in range(3)]
            L = math.sqrt(sum(c * c for c in d))
            n = self.part(f"{prefix}{i}", parent, a, align_y(d))
            if links:
                if i % 2 == 0:
                    self.box(n, -thick, -0.3, -thick * 0.35, thick * 2, L + 0.6, thick * 0.7, mat)
                else:
                    self.box(n, -thick * 0.35, -0.3, -thick, thick * 0.7, L + 0.6, thick * 2, mat)
            else:
                self.box(n, -thick / 2, -0.15, -thick / 2, thick, L + 0.3, thick, mat)
            names.append(n)
        return names

    def lantern(self, name, parent, at, size=1.0, links=3, anim=True, frame="iron", glass="glass", group="lantern"):
        """A hanging lantern: a short chain, a peaked cap, four posts round a glowing core, a base and a spike."""
        n = self.part(name, parent, at, self.hang_rot(parent), anim=anim, group=group)
        s = size
        y = 0.0
        for i in range(links):
            if i % 2 == 0:
                self.box(n, -0.7 * s, y, -0.25 * s, 1.4 * s, 2.4 * s, 0.5 * s, "rust")
            else:
                self.box(n, -0.25 * s, y, -0.7 * s, 0.5 * s, 2.4 * s, 1.4 * s, "rust")
            y += 2.0 * s
        self.box(n, -0.6 * s, y, -0.6 * s, 1.2 * s, 1.0 * s, 1.2 * s, frame)
        y += 1.0 * s
        self.box(n, -2.0 * s, y, -2.0 * s, 4.0 * s, 1.2 * s, 4.0 * s, frame)
        y += 1.2 * s
        self.box(n, -3.2 * s, y, -3.2 * s, 6.4 * s, 1.4 * s, 6.4 * s, frame)
        y += 1.4 * s
        body = 7.5 * s
        for sx in (-1, 1):
            for sz in (-1, 1):
                self.box(n, sx * 2.4 * s - 0.5 * s, y, sz * 2.4 * s - 0.5 * s, 1.0 * s, body, 1.0 * s, frame)
        self.box(n, -2.0 * s, y + 0.3 * s, -2.0 * s, 4.0 * s, body - 0.6 * s, 4.0 * s, glass)
        self.box(n, -1.0 * s, y + 1.5 * s, -1.0 * s, 2.0 * s, body - 3.0 * s, 2.0 * s, "glowcore")
        y += body
        self.box(n, -3.2 * s, y, -3.2 * s, 6.4 * s, 1.3 * s, 6.4 * s, frame)
        y += 1.3 * s
        self.box(n, -1.6 * s, y, -1.6 * s, 3.2 * s, 1.2 * s, 3.2 * s, frame)
        y += 1.2 * s
        self.box(n, -0.6 * s, y, -0.6 * s, 1.2 * s, 2.6 * s, 1.2 * s, frame)
        return n

    def strand(self, name, parent, at, length, width, mat, anim=True, clumps=0, group="strand"):
        """Something that hangs straight down from a point: moss, a tatter, a thread."""
        n = self.part(name, parent, at, self.hang_rot(parent), anim=anim, group=group)
        self.box(n, -width / 2, 0, -width / 2, width, length, width, mat)
        for i in range(clumps):
            y = length * (0.3 + 0.6 * self.rnd.random())
            c = width * 2.2
            self.box(n, -c / 2, y, -c / 2, c, c * 0.8, c, mat)
        return n

    def ring(self, prefix, parent, center, radius, count, seg, thick, mat, plane="xy", phase=0.0):
        """Segments round a circle (a halo, a vortex) in the given plane."""
        names = []
        for i in range(count):
            a = phase + 2 * math.pi * i / count
            if plane == "xy":
                p = [center[0] + radius * math.cos(a), center[1] + radius * math.sin(a), center[2]]
                r = (0, 0, a)
                n = self.part(f"{prefix}{i}", parent, p, r)
                self.box(n, -thick / 2, -seg / 2, -thick / 2, thick, seg, thick, mat)
            else:  # xz
                p = [center[0] + radius * math.cos(a), center[1], center[2] + radius * math.sin(a)]
                n = self.part(f"{prefix}{i}", parent, p, (0, -a, 0))
                self.box(n, -thick / 2, -thick / 2, -seg / 2, thick, thick, seg, mat)
            names.append(n)
        return names


# ----------------------------------------------------------------------------- noise and colour


def _h(x, y, s):
    n = (x * 374761393 + y * 668265263 + s * 2147483647) & 0xFFFFFFFF
    n = ((n ^ (n >> 13)) * 1274126177) & 0xFFFFFFFF
    return ((n ^ (n >> 16)) & 0xFFFF) / 65535.0


def noise(x, y, s, scale=4.0):
    fx, fy = x / scale, y / scale
    x0, y0 = int(math.floor(fx)), int(math.floor(fy))
    tx, ty = fx - x0, fy - y0
    tx, ty = tx * tx * (3 - 2 * tx), ty * ty * (3 - 2 * ty)
    a = _h(x0, y0, s) + (_h(x0 + 1, y0, s) - _h(x0, y0, s)) * tx
    b = _h(x0, y0 + 1, s) + (_h(x0 + 1, y0 + 1, s) - _h(x0, y0 + 1, s)) * tx
    return a + (b - a) * ty


def mix(a, b, k):
    k = max(0.0, min(1.0, k))
    return tuple(int(a[i] + (b[i] - a[i]) * k) for i in range(3))


def shade(c, k):
    return tuple(max(0, min(255, int(v * k))) for v in c)


# ----------------------------------------------------------------------------- materials
# Each material paints one face pixel: f(ctx) -> (rgba_base, rgba_glow or None).
# ctx: face ('top','bottom','east','north','west','south'), px, py, fw, fh, gx, gy (texture pos), seed, box dims


def _edge(ctx):
    return min(ctx["px"], ctx["py"], ctx["fw"] - 1 - ctx["px"], ctx["fh"] - 1 - ctx["py"])


def m_bark(ctx, crack=False, veins=0.0):
    gx, gy, s = ctx["gx"], ctx["gy"], ctx["seed"]
    streak = noise(gx * 3, gy * 0.35, s, 3.0)
    fine = noise(gx, gy, s + 7, 1.5)
    base = mix((34, 20, 36), (104, 60, 40), streak * 0.85 + fine * 0.25)
    if noise(gx * 2, gy * 0.5, s + 3, 5.0) > 0.72:
        base = mix(base, (176, 102, 52), 0.55)
    if noise(gx * 6, gy * 0.6, s + 11, 2.0) < 0.12:
        base = shade(base, 0.45)
    if ctx["face"] in ("top", "bottom"):
        base = shade(base, 0.75)
    if _edge(ctx) == 0:
        base = shade(base, 0.7)
    glow = None
    if crack:
        # zigzag cracks running up the face, bright in the middle
        cx = ctx["fw"] * (0.25 + 0.5 * noise(gy * 0.2, 3, s + 21, 6.0))
        for k, off in enumerate((0.0, 0.33, 0.66)):
            x = ctx["fw"] * off + cx * 0.5 + math.sin(ctx["py"] * 0.45 + k * 2) * 2.2
            dd = abs(ctx["px"] - x)
            if dd < 1.2 and noise(k * 31, ctx["py"], s, 3.0) > 0.25:
                glow = (225, 120, 255, 255) if dd < 0.6 else (170, 70, 230, 200)
                base = (120, 50, 160)
    if veins and glow is None and noise(gx * 0.7, gy * 0.15, s + 41, 2.5) > 1 - veins and abs(noise(gx, gy * 0.2, s + 5, 3) - 0.5) < 0.05:
        glow = (190, 90, 255, 210)
        base = (90, 40, 120)
    return base + (255,), glow


def m_moss(ctx):
    gx, gy, s = ctx["gx"], ctx["gy"], ctx["seed"]
    base = mix((22, 16, 30), (70, 46, 86), noise(gx, gy, s, 2.0))
    if _h(gx, gy, s + 5) > 0.93:
        base = (150, 80, 200)
    a = 255
    if ctx["face"] in ("north", "south", "east", "west"):
        # frayed: holes towards the bottom
        k = ctx["py"] / max(1, ctx["fh"] - 1)
        if _h(gx, gy, s + 9) < k * 0.35:
            a = 0
    return base + (a,), None


def m_bone(ctx, dark=False):
    k = ctx["py"] / max(1, ctx["fh"] - 1)
    base = mix((214, 164, 102), (122, 72, 38), k * 0.9)
    if dark:
        base = mix((120, 70, 36), (46, 24, 16), k)
    if noise(ctx["gx"] * 3, ctx["gy"], ctx["seed"], 2.0) < 0.2:
        base = shade(base, 0.8)
    if _edge(ctx) == 0:
        base = shade(base, 0.82)
    return base + (255,), None


def m_mask(ctx):
    gx, gy, s = ctx["gx"], ctx["gy"], ctx["seed"]
    base = mix((150, 104, 66), (212, 168, 118), noise(gx * 0.5, gy * 2, s, 3.0))
    if noise(gx * 0.4, gy * 3, s + 2, 2.0) < 0.18:
        base = shade(base, 0.72)
    e = _edge(ctx)
    if e == 0:
        base = shade(base, 0.6)
    elif e == 1:
        base = shade(base, 0.85)
    return base + (255,), None


def m_ash_mask(ctx):
    gx, gy, s = ctx["gx"], ctx["gy"], ctx["seed"]
    base = mix((120, 112, 132), (196, 190, 210), noise(gx, gy * 1.5, s, 3.0))
    if noise(gx * 2, gy * 2, s + 2, 2.0) < 0.15:
        base = shade(base, 0.7)
    if _edge(ctx) == 0:
        base = shade(base, 0.6)
    return base + (255,), None


def m_socket(ctx):
    return (10, 4, 16, 255), None


def m_eye(ctx):
    e = _edge(ctx)
    core = e >= 1 or min(ctx["fw"], ctx["fh"]) <= 2
    g = (255, 200, 255, 255) if core else (210, 110, 255, 230)
    return (200, 110, 250, 255), g


def m_glowcore(ctx):
    return (230, 170, 255, 255), (255, 235, 255, 255)


def m_glass(ctx):
    e = _edge(ctx)
    if e == 0:
        return (40, 20, 60, 255), (120, 50, 190, 160)
    k = noise(ctx["gx"], ctx["gy"], ctx["seed"], 2.0)
    return (170, 80, 240, 255), (int(200 + 40 * k), int(110 + 60 * k), 255, 235)


def m_thread(ctx):
    return (200, 140, 255, 255), (245, 210, 255, 255)


def m_halo(ctx):
    k = noise(ctx["gx"], ctx["gy"], ctx["seed"], 2.0)
    return (150, 90, 230, 255), (int(205 + 40 * k), int(150 + 50 * k), 255, 240)


def m_vortex(ctx):
    k = noise(ctx["gx"] * 2, ctx["gy"] * 2, ctx["seed"], 1.5)
    return (130, 60, 220, 255), (int(170 + 70 * k), int(90 + 80 * k), 255, int(170 + 70 * k))


def m_iron(ctx):
    gx, gy, s = ctx["gx"], ctx["gy"], ctx["seed"]
    base = mix((26, 24, 34), (84, 80, 98), noise(gx, gy, s, 2.5))
    if _edge(ctx) == 0:
        base = shade(base, 1.25)
    if _h(gx, gy, s) > 0.96:
        base = (130, 110, 150)
    return base + (255,), None


def m_rust(ctx):
    gx, gy, s = ctx["gx"], ctx["gy"], ctx["seed"]
    base = mix((86, 46, 24), (176, 106, 56), noise(gx, gy, s, 1.5))
    if _h(gx, gy, s + 3) > 0.85:
        base = shade(base, 0.6)
    return base + (255,), None


def m_boatwood(ctx):
    gx, gy, s = ctx["gx"], ctx["gy"], ctx["seed"]
    horiz = ctx["fw"] >= ctx["fh"]
    a, b = (gx, gy) if horiz else (gy, gx)
    grain = noise(a * 0.25, b * 3, s, 2.0)
    base = mix((30, 18, 18), (86, 52, 38), grain)
    if (b % 7) == 0:
        base = shade(base, 0.55)
    if _h(gx, gy, s + 7) > 0.985:
        base = (120, 110, 120)
    if ctx["face"] in ("top", "bottom"):
        base = shade(base, 0.85)
    return base + (255,), None


def m_robe(ctx, ragged=True, glowline=0.0):
    """The Ferryman's robe: blue-black cloth in long folds, lighter on the ridges, frayed at the hem."""
    gx, gy, s = ctx["gx"], ctx["gy"], ctx["seed"]
    fold = 0.5 + 0.5 * math.sin(ctx["px"] * 1.3 + noise(gx, gy * 0.1, s, 4) * 3)
    base = mix((10, 10, 22), (52, 48, 86), fold * 0.7 + noise(gx, gy, s + 3, 2) * 0.3)
    if _h(gx, gy, s + 1) > 0.97:
        base = (90, 70, 140)
    a = 255
    if ragged and ctx["face"] in ("north", "south", "east", "west"):
        jag = 1 + int(4 * _h(ctx["px"] // 1, 7, s + ctx["fw"]))
        if ctx["py"] > ctx["fh"] - 1 - jag:
            a = 0
    glow = None
    if glowline and abs(ctx["px"] - ctx["fw"] / 2) < 0.6 and noise(gx, gy, s + 9, 6) > 1 - glowline:
        glow = (170, 80, 255, 200)
    return base + (a,), glow


def m_cloth(ctx, ragged=True, veins=0.0):
    """The Weaver's robe: violet-brown, rotting at the hem, threaded with faint light."""
    gx, gy, s = ctx["gx"], ctx["gy"], ctx["seed"]
    fold = 0.5 + 0.5 * math.sin(ctx["px"] * 1.1 + noise(gx, gy * 0.08, s, 5) * 4)
    base = mix((26, 14, 30), (104, 64, 88), fold * 0.65 + noise(gx, gy, s + 3, 2) * 0.35)
    if noise(gx * 2, gy * 0.3, s + 4, 3) > 0.78:
        base = mix(base, (150, 96, 70), 0.4)
    a = 255
    if ragged and ctx["face"] in ("north", "south", "east", "west"):
        k = ctx["py"] / max(1, ctx["fh"] - 1)
        jag = 2 + int(7 * _h(ctx["px"], 11, s + ctx["fw"]))
        if ctx["py"] > ctx["fh"] - 1 - jag or (k > 0.7 and _h(gx, gy, s + 13) < (k - 0.7) * 0.6):
            a = 0
    glow = None
    if veins and noise(gx, gy * 0.12, s + 17, 3) > 1 - veins and abs(noise(gx * 0.5, gy * 0.05, s + 19, 2) - 0.5) < 0.04:
        glow = (180, 100, 255, 210)
    return base + (a,), glow


def m_skin(ctx):
    gx, gy, s = ctx["gx"], ctx["gy"], ctx["seed"]
    base = mix((44, 34, 54), (110, 96, 124), noise(gx, gy, s, 2.0))
    if _edge(ctx) == 0:
        base = shade(base, 0.7)
    return base + (255,), None


def m_skull(ctx):
    gx, gy, s = ctx["gx"], ctx["gy"], ctx["seed"]
    base = mix((150, 140, 124), (226, 218, 198), noise(gx, gy, s, 2.0))
    if noise(gx * 3, gy * 3, s + 5, 1.5) < 0.15:
        base = shade(base, 0.6)
    return base + (255,), None


MATS = {
    "bark": lambda c: m_bark(c),
    "bark_veins": lambda c: m_bark(c, veins=0.45),
    "bark_crack": lambda c: m_bark(c, crack=True),
    "moss": m_moss,
    "bone": lambda c: m_bone(c),
    "bone_dark": lambda c: m_bone(c, dark=True),
    "mask": m_mask,
    "ash_mask": m_ash_mask,
    "socket": m_socket,
    "eye": m_eye,
    "glowcore": m_glowcore,
    "glass": m_glass,
    "thread": m_thread,
    "halo": m_halo,
    "vortex": m_vortex,
    "iron": m_iron,
    "rust": m_rust,
    "boatwood": m_boatwood,
    "robe": lambda c: m_robe(c, ragged=False),
    "robe_ragged": lambda c: m_robe(c, ragged=True, glowline=0.15),
    "cloth": lambda c: m_cloth(c, ragged=False, veins=0.25),
    "cloth_ragged": lambda c: m_cloth(c, ragged=True, veins=0.2),
    "skin": m_skin,
    "skull": m_skull,
}

# ----------------------------------------------------------------------------- packing, painting, writing


def _dims(b):
    x, y, z, w, h, d = b
    W, H, D = max(1, math.ceil(w - 1e-6)), max(1, math.ceil(h - 1e-6)), max(1, math.ceil(d - 1e-6))
    return W, H, D


def pack(model):
    items = []
    for p in model.parts:
        for bi, bx in enumerate(p["boxes"]):
            W, H, D = _dims(bx["b"])
            items.append([p["name"], bi, 2 * (W + D), D + H, (W, H, D)])
    items.sort(key=lambda it: (-it[3], -it[2]))
    cx = cy = rowh = 0
    uv = {}
    for name, bi, uw, uh, dims in items:
        if cx + uw > model.tw:
            cx = 0
            cy += rowh
            rowh = 0
        uv[(name, bi)] = (cx, cy, dims)
        cx += uw
        rowh = max(rowh, uh)
    used = cy + rowh
    assert used <= model.th, f"{model.cls}: texture overflow, {used} > {model.th}"
    return uv, used


def paint(model, uv):
    img = Image.new("RGBA", (model.tw, model.th), (0, 0, 0, 0))
    glow = Image.new("RGBA", (model.tw, model.th), (0, 0, 0, 0))
    pi, pg = img.load(), glow.load()
    for p in model.parts:
        for bi, bx in enumerate(p["boxes"]):
            u, v, (W, H, D) = uv[(p["name"], bi)]
            f = MATS[bx["mat"]]
            seed = model.seed * 131 + zlib.crc32(p["name"].encode()) % 9973 + bi * 17
            faces = {
                "top": (u + D, v, W, D), "bottom": (u + D + W, v, W, D),
                "east": (u, v + D, D, H), "north": (u + D, v + D, W, H),
                "west": (u + D + W, v + D, D, H), "south": (u + 2 * D + W, v + D, W, H),
            }
            for face, (x0, y0, fw, fh) in faces.items():
                for py in range(fh):
                    for px in range(fw):
                        ctx = dict(face=face, px=px, py=py, fw=fw, fh=fh, gx=x0 + px, gy=y0 + py, seed=seed & 0xFFFF)
                        b, g = f(ctx)
                        pi[x0 + px, y0 + py] = b
                        if g is not None:
                            pg[x0 + px, y0 + py] = g
    return img, glow


def _f(v):
    s = f"{v:.4f}".rstrip("0").rstrip(".")
    if s in ("-0", ""):
        s = "0"
    return s + "F"


def write_java(model, uv, package, path):
    lines = []
    A = lines.append
    A(f"package {package};")
    A("")
    A("import net.minecraft.client.model.geom.ModelPart;")
    A("import net.minecraft.client.model.geom.PartPose;")
    A("import net.minecraft.client.model.geom.builders.CubeDeformation;")
    A("import net.minecraft.client.model.geom.builders.CubeListBuilder;")
    A("import net.minecraft.client.model.geom.builders.LayerDefinition;")
    A("import net.minecraft.client.model.geom.builders.MeshDefinition;")
    A("import net.minecraft.client.model.geom.builders.PartDefinition;")
    A("")
    A(f"/** Geometry of {model.cls}, written by tools/elders (do not edit by hand: run the generator). */")
    A(f"public final class {model.cls}Geometry {{")
    A(f"\tprivate {model.cls}Geometry() {{}}")
    A("")
    chunks = [model.parts[i:i + 40] for i in range(0, len(model.parts), 40)]
    A("\tpublic static LayerDefinition create() {")
    A("\t\tMeshDefinition mesh = new MeshDefinition();")
    A("\t\tPartDefinition r = mesh.getRoot();")
    for i in range(len(chunks)):
        A(f"\t\tpart{i}(r);")
    A(f"\t\treturn LayerDefinition.create(mesh, {model.tw}, {model.th});")
    A("\t}")

    def path_of(name):
        chain = []
        while name is not None:
            chain.append(name)
            name = model.idx[name]["parent"]
        return list(reversed(chain))

    for ci, chunk in enumerate(chunks):
        A("")
        A(f"\tprivate static void part{ci}(PartDefinition r) {{")
        local = set()
        for p in chunk:
            parent = p["parent"]
            if parent is None:
                pexpr = "r"
            elif parent in local:
                pexpr = "p_" + parent
            else:
                pexpr = "r" + "".join(f'.getChild("{n}")' for n in path_of(parent))
            cubes = "CubeListBuilder.create()"
            for bi, bx in enumerate(p["boxes"]):
                u, v, _ = uv[(p["name"], bi)]
                x, y, z, w, h, d = bx["b"]
                defo = f", new CubeDeformation({_f(bx['inflate'])})" if bx["inflate"] else ""
                cubes += f".texOffs({u}, {v}).addBox({_f(x)}, {_f(y)}, {_f(z)}, {_f(w)}, {_f(h)}, {_f(d)}{defo})"
            pv, rt = p["pivot"], p["rot"]
            pose = f"PartPose.offsetAndRotation({_f(pv[0])}, {_f(pv[1])}, {_f(pv[2])}, {_f(rt[0])}, {_f(rt[1])}, {_f(rt[2])})"
            A(f'\t\tPartDefinition p_{p["name"]} = {pexpr}.addOrReplaceChild("{p["name"]}", {cubes}, {pose});')
            local.add(p["name"])
        A("\t}")
    groups = {}
    for p in model.parts:
        if p.get("group"):
            groups.setdefault(p["group"], []).append(p["name"])
    for g, names in sorted(groups.items()):
        A("")
        A(f"\t/** Parts that move together: {g}. */")
        A(f"\tpublic static final String[] {g.upper()} = {{" + ", ".join(f'"{n}"' for n in names) + "};")
    A("")
    A("\t/** The animated parts, by name. */")
    A("\tpublic static ModelPart find(ModelPart root, String name) {")
    A("\t\treturn switch (name) {")
    for p in model.parts:
        if p["anim"]:
            expr = "root" + "".join(f'.getChild("{n}")' for n in path_of(p["name"]))
            A(f'\t\t\tcase "{p["name"]}" -> {expr};')
    A('\t\t\tdefault -> throw new IllegalArgumentException("no animated part " + name);')
    A("\t\t};")
    A("\t}")
    A("}")
    with open(path, "w") as fh:
        fh.write("\n".join(lines) + "\n")
    # Unused-variable warnings are harmless; javac does not fail on them.


def export_preview(model, uv, path):
    """A JSON of world-space boxes for the preview renderer."""
    import json
    out = []
    for p in model.parts:
        x = model.xf(p["name"])
        for bi, bx in enumerate(p["boxes"]):
            u, v, dims = uv[(p["name"], bi)]
            out.append(dict(r=x.r, t=x.t, b=bx["b"], inflate=bx["inflate"], uv=[u, v], dims=dims, mat=bx["mat"]))
    with open(path, "w") as fh:
        json.dump(dict(tw=model.tw, th=model.th, boxes=out), fh)
