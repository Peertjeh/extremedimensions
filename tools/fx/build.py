"""The Elders' attack sprites: sigils on the ground, shockwave rings, cracks and flashes, the debris of each Elder's
blows, their projectiles and the threads, chains and beams drawn along a line. Pixel art drawn procedurally at sizes
well above vanilla particles (sigils and rings 256 px, projectiles 64, debris and line tiles 32), so the effects carry
the same fine detail as the Elders' faces.

Every family is one particle type (its sprite set): the client picks a sprite by index (see AttackFxParticle). Line
tiles repeat along their vertical axis (top and bottom edges meet seamlessly), so a thread or chain is laid down as a
row of them.

usage: python3 tools/fx/build.py [preview-sheet.png]
"""
import json
import math
import os
import sys

import numpy as np
from PIL import Image

ROOT = os.path.join(os.path.dirname(__file__), "..", "..")
TEX = os.path.join(ROOT, "src/client/resources/assets/extremedimensions/textures/particle")
JSON = os.path.join(ROOT, "src/client/resources/assets/extremedimensions/particles")

TAU = math.pi * 2


# ----------------------------------------------------------------------------- canvas helpers

class Canvas:
    """An RGBA float image with coordinate grids: u, v in [-1, 1] (v down), r and the angle t (0 to the right,
    clockwise on screen)."""

    def __init__(self, size, h=None):
        self.w, self.h = size, (h or size)
        self.img = np.zeros((self.h, self.w, 4), dtype=np.float64)
        ys, xs = np.mgrid[0:self.h, 0:self.w]
        self.px, self.py = xs, ys
        self.u = (xs + 0.5) / self.w * 2 - 1
        self.v = (ys + 0.5) / self.h * 2 - 1
        self.r = np.hypot(self.u, self.v)
        self.t = np.arctan2(self.v, self.u) % TAU

    def put(self, mask, color, alpha=1.0):
        """Paints color over mask (bool or 0..1 coverage) with the given alpha, over what is there."""
        a = np.clip(np.asarray(mask, dtype=np.float64) * alpha, 0, 1)
        col = np.array(color[:3], dtype=np.float64) / 255.0
        src_a = a[..., None]
        dst = self.img
        out_a = src_a[..., 0] + dst[..., 3] * (1 - src_a[..., 0])
        safe = np.maximum(out_a, 1e-6)[..., None]
        dst[..., :3] = (col * src_a + dst[..., :3] * dst[..., 3:4] * (1 - src_a)) / safe
        dst[..., 3] = out_a

    def under(self, mask, color, alpha=1.0):
        """Paints color only where the image is still (partly) clear, beneath what is there."""
        a = np.clip(np.asarray(mask, dtype=np.float64) * alpha, 0, 1) * (1 - self.img[..., 3])
        col = np.array(color[:3], dtype=np.float64) / 255.0
        out_a = self.img[..., 3] + a
        safe = np.maximum(out_a, 1e-6)[..., None]
        self.img[..., :3] = (self.img[..., :3] * self.img[..., 3:4] + col * a[..., None]) / safe
        self.img[..., 3] = out_a

    def save(self, name):
        arr = np.clip(self.img * 255 + 0.5, 0, 255).astype(np.uint8)
        # pixel art: alpha is stepped, never a long soft ramp of near-clear pixels
        a = arr[..., 3]
        a[a < 10] = 0
        Image.fromarray(arr, "RGBA").save(os.path.join(TEX, name + ".png"))
        return Image.fromarray(arr, "RGBA")


def h2(x, y, s=0):
    """A hash in [0, 1) per integer cell."""
    n = (np.asarray(x, dtype=np.int64) * 374761393 + np.asarray(y, dtype=np.int64) * 668265263 + s * 2147483647) & 0xFFFFFFFF
    n = (n ^ (n >> 13)) * 1274126177 & 0xFFFFFFFF
    return ((n ^ (n >> 16)) & 0xFFFF) / 65536.0


def vnoise(x, y, s=0):
    """Smooth value noise in [0, 1]."""
    x0, y0 = np.floor(x), np.floor(y)
    fx, fy = x - x0, y - y0
    fx, fy = fx * fx * (3 - 2 * fx), fy * fy * (3 - 2 * fy)
    a, b = h2(x0, y0, s), h2(x0 + 1, y0, s)
    c, d = h2(x0, y0 + 1, s), h2(x0 + 1, y0 + 1, s)
    return (a + (b - a) * fx) * (1 - fy) + (c + (d - c) * fx) * fy


def mix(a, b, k):
    k = np.clip(k, 0, 1)
    return tuple(a[i] + (b[i] - a[i]) * k for i in range(3))


def ang_diff(t, t0):
    """Signed angular distance from t0 to t, in (-pi, pi]."""
    return (t - t0 + math.pi) % TAU - math.pi


def seg_dist(px, py, ax, ay, bx, by):
    vx, vy = bx - ax, by - ay
    l2 = max(vx * vx + vy * vy, 1e-9)
    k = np.clip(((px - ax) * vx + (py - ay) * vy) / l2, 0, 1)
    return np.hypot(px - (ax + vx * k), py - (ay + vy * k))


def ellipse(c, cx, cy, a, b, rot=0.0):
    """Normalised ellipse distance (1 on the outline) of every pixel."""
    x, y = c.u - cx, c.v - cy
    cr, sr = math.cos(rot), math.sin(rot)
    xr, yr = x * cr + y * sr, -x * sr + y * cr
    return np.hypot(xr / a, yr / b)


def glow_line(c, dist, width, core, halo, halo_w=None, halo_a=0.55):
    """A lit line: a soft halo, then the core where the distance is under the width."""
    hw = halo_w or width * 3.5
    c.under(np.clip(1 - dist / hw, 0, 1) ** 1.5, halo, halo_a)
    c.put(dist < width, core)


# ----------------------------------------------------------------------------- palettes

VIOLET = (196, 128, 255)
VIOLET_DEEP = (96, 36, 160)
VIOLET_HOT = (246, 222, 255)
BARK = (74, 50, 34)
BARK_HI = (128, 90, 60)
MOSS = (70, 96, 52)

TEAL = (92, 255, 222)
TEAL_DEEP = (16, 104, 110)
TEAL_HOT = (226, 255, 248)
IRON = (40, 44, 52)
IRON_HI = (96, 104, 116)
BONE = (232, 222, 190)
GOLD = (236, 184, 72)
GOLD_DEEP = (150, 96, 30)

SILK = (248, 240, 255)
GILT = (236, 196, 112)
WEB_VIOLET = (178, 142, 255)
WEB_DEEP = (70, 48, 140)


# ----------------------------------------------------------------------------- sigils (256)

def sigil_matriarch():
    """A mandala of roots: a braided root ring with thorns turned inward, eight leaf-sigils, a ring of runes of
    bark and, in the heart, her almond eye with its slit of a pupil."""
    c = Canvas(256)
    r, t = c.r, c.t
    # a faint haze so the circle reads on any ground
    c.put((r < 0.97) * np.clip((r - 0.2) / 0.8, 0, 1) ** 2, VIOLET_DEEP, 0.28)
    # the braid: two strands crossing 36 times
    for k, sgn in enumerate((1, -1)):
        s = 0.935 + 0.024 * np.sin(t * 18) * sgn
        d = np.abs(r - s)
        over = (np.sin(t * 36 + k * math.pi) > 0)
        col = np.where(over[..., None], np.array(BARK_HI) / 255.0, np.array(BARK) / 255.0)
        m = d < 0.016
        c.under(np.clip(1 - d / 0.05, 0, 1), VIOLET, 0.35)
        c.img[m, :3] = col[m]
        c.img[m, 3] = 1.0
    # glowing seams at the braid's edges
    for edge in (0.895, 0.975):
        c.put(np.abs(r - edge) < 0.006, VIOLET)
    # thorns pointing inward
    n = 24
    for i in range(n):
        t0 = (i + 0.5) / n * TAU
        depth = np.clip((r - 0.79) / 0.105, 0, 1)
        w = depth * 0.075
        m = (np.abs(ang_diff(t, t0)) < w) & (r > 0.79) & (r < 0.895)
        c.put(m, mix(BARK, BARK_HI, 0.3))
        c.put(m & (np.abs(ang_diff(t, t0)) < w * 0.35), BARK_HI)
        c.put((np.abs(ang_diff(t, t0)) < 0.012) & (r > 0.79) & (r < 0.81), VIOLET_HOT)
    # the rune ring
    for edge in (0.70, 0.735):
        glow_line(c, np.abs(r - edge), 0.006, VIOLET, VIOLET_DEEP)
    runes = 32
    cell = (t / TAU * runes)
    ci = np.floor(cell)
    fx = cell - ci
    fy = (r - 0.705) / 0.03
    inband = (r > 0.705) & (r < 0.73)
    pattern = h2(ci, 0, 5)
    strokes = ((np.abs(fx - 0.5) < 0.09) | ((pattern > 0.5) & (np.abs(fy - 0.5) < 0.14))
               | ((pattern < 0.3) & (np.abs(fx - fy) < 0.12)) | ((pattern > 0.75) & (np.abs(fx + fy - 1) < 0.12)))
    c.put(inband & strokes, VIOLET_HOT)
    # eight leaf-sigils, each with a vein, between dotted spokes
    for i in range(8):
        a = i / 8 * TAU
        cx, cy = math.cos(a) * 0.5, math.sin(a) * 0.5
        e = ellipse(c, cx, cy, 0.13, 0.055, a)
        glow_line(c, np.abs(e - 1) * 0.055, 0.008, VIOLET, VIOLET_DEEP, halo_w=0.04)
        c.put(e < 0.92, MOSS, 0.75)
        vein = seg_dist(c.u, c.v, cx - math.cos(a) * 0.12, cy - math.sin(a) * 0.12, cx + math.cos(a) * 0.12, cy + math.sin(a) * 0.12)
        c.put((vein < 0.006) & (e < 1), VIOLET_HOT)
        for side in (-1, 1):
            for j in (-0.06, 0.0, 0.06):
                bx, by = cx + math.cos(a) * j, cy + math.sin(a) * j
                ex = bx + math.cos(a + side * 1.0) * 0.045
                ey = by + math.sin(a + side * 1.0) * 0.045
                c.put((seg_dist(c.u, c.v, bx, by, ex, ey) < 0.004) & (e < 1), VIOLET)
        b = a + TAU / 16
        for j in range(6):
            rr = 0.27 + j * 0.07
            d = np.hypot(c.u - math.cos(b) * rr, c.v - math.sin(b) * rr)
            c.put(d < 0.011, VIOLET if j % 2 else VIOLET_HOT)
    # inner rings and the eye
    glow_line(c, np.abs(r - 0.24), 0.007, VIOLET, VIOLET_DEEP)
    c.put(r < 0.235, BARK, 0.85)
    eye = np.abs(c.v) < 0.11 * np.clip(1 - (c.u / 0.19) ** 2, 0, 1) ** 1.1
    c.put(eye & (np.abs(c.u) < 0.19), mix(VIOLET_DEEP, VIOLET, 0.6))
    c.put(eye & (np.abs(c.u) < 0.19) & (np.hypot(c.u, c.v) < 0.09), VIOLET)
    c.put(eye & (np.abs(c.u) < 0.025 * (1 - np.abs(c.v) / 0.11) + 0.006), (40, 8, 60))
    c.put((np.hypot(c.u + 0.07, c.v + 0.04) < 0.018), VIOLET_HOT)
    # tears of light run from the eye's corners
    for sx in (-1, 1):
        d = seg_dist(c.u, c.v, sx * 0.19, 0.0, sx * 0.205, 0.17)
        c.put(d < 0.008, VIOLET_HOT)
    return c


def _glyph(c, cx, cy, size, seed, color):
    """A rune: strokes on a 3 x 4 lattice chosen by the seed, drawn as pixel lines."""
    pts = [(x, y) for y in range(4) for x in range(3)]
    rng = np.random.default_rng(seed)
    edges = []
    for _ in range(4):
        a, b = rng.choice(len(pts), 2, replace=False)
        edges.append((pts[a], pts[b]))
    edges.append(((1, 0), (1, 3)))
    for (x0, y0), (x1, y1) in edges:
        ax, ay = cx + (x0 - 1) * size * 0.5, cy + (y0 - 1.5) * size * 0.45
        bx, by = cx + (x1 - 1) * size * 0.5, cy + (y1 - 1.5) * size * 0.45
        c.put(seg_dist(c.u, c.v, ax, ay, bx, by) < size * 0.09, color)


def _skull(c, cx, cy, s, bone, glow):
    """A small skull glyph: dome, sockets lit, nose, teeth."""
    x, y = (c.u - cx) / s, (c.v - cy) / s
    dome = (x * x + (y + 0.15) ** 2 < 0.55) | ((np.abs(x) < 0.5) & (y > -0.1) & (y < 0.55))
    jaw = (np.abs(x) < 0.38) & (y >= 0.55) & (y < 0.85)
    c.put(dome | jaw, bone)
    for sx in (-1, 1):
        c.put(np.hypot(x - sx * 0.3, y - 0.12) < 0.17, glow)
    c.put((np.abs(x) < 0.08) & (y > 0.33) & (y < 0.48), (30, 30, 34))
    c.put(jaw & (np.abs((x * 5) % 1 - 0.5) > 0.38), (30, 30, 34))


def sigil_ferryman():
    """The toll: a ring of chain links, a band of runes between lit rings, six skulls keeping watch and, at the
    heart, the obol, embossed with the boat."""
    c = Canvas(256)
    r, t = c.r, c.t
    c.put((r < 0.98) * np.clip((r - 0.15) / 0.85, 0, 1) ** 2, TEAL_DEEP, 0.3)
    # the chain: alternating flat links (rings) and edge-on links (bars)
    n = 30
    for i in range(n):
        a = (i + 0.5) / n * TAU
        cx, cy = math.cos(a) * 0.925, math.sin(a) * 0.925
        rot = a + math.pi / 2
        if i % 2 == 0:
            e = ellipse(c, cx, cy, 0.068, 0.036, rot)
            ring = (e > 0.62) & (e < 1.0)
            c.under(np.clip(1.25 - e, 0, 1), TEAL, 0.35)
            c.put(ring, IRON)
            c.put(ring & (e > 0.62) & (e < 0.75), IRON_HI)
        else:
            x, y = c.u - cx, c.v - cy
            cr, sr = math.cos(rot), math.sin(rot)
            xr, yr = x * cr + y * sr, -x * sr + y * cr
            bar = (np.abs(xr) < 0.07) & (np.abs(yr) < 0.012)
            c.under((np.abs(xr) < 0.09) & (np.abs(yr) < 0.03), TEAL, 0.3)
            c.put(bar, IRON_HI)
            c.put(bar & (yr < -0.004), (150, 160, 170))
    # lit rings round the rune band
    for edge in (0.845, 0.715):
        glow_line(c, np.abs(r - edge), 0.007, TEAL, TEAL_DEEP)
    runes = 22
    for i in range(runes):
        a = (i + 0.5) / runes * TAU
        _glyph(c, math.cos(a) * 0.78, math.sin(a) * 0.78, 0.075, 100 + i, TEAL_HOT)
    # six skulls keep watch, joined by a thin ring
    glow_line(c, np.abs(r - 0.55), 0.004, TEAL, TEAL_DEEP, halo_w=0.02)
    for i in range(6):
        a = i / 6 * TAU - math.pi / 2
        cx, cy = math.cos(a) * 0.55, math.sin(a) * 0.55
        c.put(np.hypot(c.u - cx, c.v - cy) < 0.085, (12, 30, 34), 0.9)
        _skull(c, cx, cy, 0.075, BONE, TEAL)
    # rays from the obol to the skulls
    for i in range(6):
        a = i / 6 * TAU - math.pi / 2 + math.pi / 6
        d = seg_dist(c.u, c.v, math.cos(a) * 0.33, math.sin(a) * 0.33, math.cos(a) * 0.66, math.sin(a) * 0.66)
        glow_line(c, d, 0.004, TEAL, TEAL_DEEP, halo_w=0.025, halo_a=0.4)
    # the obol: a gold coin with a raised rim and the boat on it
    glow_line(c, np.abs(r - 0.3), 0.01, TEAL, TEAL_DEEP)
    coin = r < 0.28
    shade = 0.55 + 0.45 * np.clip(0.5 - (c.u + c.v) * 0.9, 0, 1)
    c.put(coin, GOLD_DEEP)
    c.put(coin & (r < 0.25), mix(GOLD_DEEP, GOLD, 0.7))
    c.put(coin & (r > 0.235) & (r < 0.255), GOLD)
    hull = (c.v > 0.02) & (c.v < 0.09) & (np.abs(c.u) < 0.16 - (c.v - 0.02) * 0.9)
    c.put(hull, GOLD_DEEP)
    c.put((np.abs(c.u - 0.0) < 0.012) & (c.v > -0.16) & (c.v < 0.03), GOLD_DEEP)
    sail = (c.v > -0.15) & (c.v < 0.0) & (c.u > 0.012) & (c.u < 0.012 + (c.v + 0.15) * 0.75)
    c.put(sail, mix(GOLD_DEEP, GOLD, 0.3))
    c.put((c.v > 0.11) & (c.v < 0.125) & (np.abs(c.u) < 0.14) & (np.sin(c.u * 70) > 0), GOLD_DEEP)
    c.put(coin & (np.hypot(c.u + 0.12, c.v + 0.12) < 0.03), (255, 240, 190), 0.8)
    return c


def sigil_weaver():
    """The web: twelve spokes and a spiral of threads sagging between them, dew of light where they cross, lozenges
    on the rim and the gem of an eye at the heart, set in gilt."""
    c = Canvas(256)
    r, t = c.r, c.t
    c.put((r < 0.97) * np.clip((r - 0.1) / 0.9, 0, 1) ** 2, WEB_DEEP, 0.26)
    spokes = 12
    seg = TAU / spokes
    for i in range(spokes):
        a = i * seg
        d = seg_dist(c.u, c.v, math.cos(a) * 0.12, math.sin(a) * 0.12, math.cos(a) * 0.95, math.sin(a) * 0.95)
        glow_line(c, d, 0.0055, SILK, WEB_VIOLET, halo_w=0.025, halo_a=0.45)
    frac = (t % seg) / seg
    sag = np.sin(frac * math.pi)
    for k, rk in enumerate((0.22, 0.33, 0.44, 0.55, 0.66, 0.77, 0.87)):
        rr = rk - 0.025 * sag * (rk / 0.87)
        glow_line(c, np.abs(r - rr), 0.005, SILK if k % 2 else GILT, WEB_VIOLET, halo_w=0.022, halo_a=0.4)
        for i in range(spokes):
            a = i * seg
            d = np.hypot(c.u - math.cos(a) * rk, c.v - math.sin(a) * rk)
            c.put(d < 0.016, (255, 255, 255))
            c.put((d >= 0.016) & (d < 0.024), WEB_VIOLET)
    glow_line(c, np.abs(r - 0.95), 0.008, GILT, WEB_VIOLET)
    for i in range(spokes):
        a = i * seg + seg / 2
        cx, cy = math.cos(a) * 0.95, math.sin(a) * 0.95
        x, y = c.u - cx, c.v - cy
        cr, sr = math.cos(a), math.sin(a)
        xr, yr = x * cr + y * sr, -x * sr + y * cr
        loz = np.abs(xr) / 0.05 + np.abs(yr) / 0.03
        c.put(loz < 1, GILT)
        c.put(loz < 0.5, VIOLET_HOT)
    # the gem: a tall lozenge, faceted, in gilt
    x, y = c.u, c.v
    loz = np.abs(x) / 0.11 + np.abs(y) / 0.2
    c.put(loz < 1.25, GILT)
    c.put(loz < 1.1, GOLD_DEEP)
    c.put(loz < 1.0, WEB_DEEP)
    facet = np.where(x * y > 0, 0.75, 1.0) * np.where(y < 0, 1.0, 0.8)
    inner = loz < 0.95
    c.img[inner, :3] = (np.array(mix(WEB_VIOLET, VIOLET_HOT, 0.4)) / 255.0)[None, :] * facet[inner, None]
    c.put(loz < 0.35, (255, 250, 255))
    c.put((np.abs(x) < 0.006) & (loz < 0.95), VIOLET_HOT)
    return c


# ----------------------------------------------------------------------------- shockwave rings (256)

def wave_matriarch():
    """A ring of torn earth: clods and roots in a band, thorns breaking outward, violet light in its cracks."""
    c = Canvas(256)
    r, t = c.r, c.t
    band = (r > 0.8) & (r < 0.97)
    n = vnoise(c.t * 26, r * 30, 3)
    inner_fade = np.clip((r - 0.8) / 0.12, 0, 1)
    c.put(band * inner_fade, mix(BARK, BARK_HI, 0.0), 0.95)
    c.put(band & (n > 0.6), BARK_HI, 0.9)
    c.put(band & (np.abs(n - 0.42) < 0.03), VIOLET)
    c.under(np.clip(1 - np.abs(r - 0.89) / 0.12, 0, 1), VIOLET_DEEP, 0.45)
    for i in range(40):
        t0 = h2(i, 1, 9) * TAU
        depth = np.clip((1.0 - r) / 0.09, 0, 1)
        m = (np.abs(ang_diff(t, t0)) < depth * 0.03) & (r > 0.92) & (r < 1.0)
        c.put(m, BARK_HI)
    return c


def wave_ferryman():
    """A ring of soul fire: flame tongues licking outward, white at the root, teal, then dark at their tips."""
    c = Canvas(256)
    r, t = c.r, c.t
    tongue = 0.86 + 0.1 * np.clip(vnoise(t * 22, 0, 4) * 0.7 + 0.3 * np.abs(np.sin(t * 40)), 0, 1)
    base = 0.82
    k = (r - base) / np.maximum(tongue - base, 1e-3)
    m = (r > base) & (k < 1)
    col_k = np.clip(k, 0, 1)
    for lo, hi, col in ((0.0, 0.25, TEAL_HOT), (0.25, 0.7, TEAL), (0.7, 1.0, TEAL_DEEP)):
        c.put(m & (col_k >= lo) & (col_k < hi), col)
    c.under(np.clip(1 - np.abs(r - 0.86) / 0.13, 0, 1), TEAL_DEEP, 0.5)
    # embers above the flames
    em = (h2(np.floor(c.px / 3), np.floor(c.py / 3), 21) > 0.985) & (r > 0.9) & (r < 1.0)
    c.put(em, TEAL_HOT)
    return c


def wave_weaver():
    """A band of woven light: two threads braided round, stitched across, with a bright leading edge."""
    c = Canvas(256)
    r, t = c.r, c.t
    c.under(np.clip(1 - np.abs(r - 0.9) / 0.1, 0, 1), WEB_DEEP, 0.55)
    for sgn in (1, -1):
        s = 0.895 + 0.035 * np.sin(t * 30) * sgn
        glow_line(c, np.abs(r - s), 0.008, SILK, WEB_VIOLET, halo_w=0.03, halo_a=0.4)
    stitch = (np.abs(((t / TAU) * 60) % 1 - 0.5) < 0.06) & (r > 0.86) & (r < 0.93)
    c.put(stitch, GILT)
    glow_line(c, np.abs(r - 0.955), 0.007, (255, 255, 255), WEB_VIOLET)
    return c


# ----------------------------------------------------------------------------- cracks and flashes

def crack():
    """Cracks running out from a blow, lit from within (white, tinted by the particle), dark at their lips."""
    c = Canvas(128)
    rng = np.random.default_rng(42)
    lines = []

    def branch(x, y, a, length, depth):
        steps = int(length / 0.06)
        for _ in range(steps):
            a += rng.normal(0, 0.35)
            nx, ny = x + math.cos(a) * 0.06, y + math.sin(a) * 0.06
            lines.append((x, y, nx, ny, depth))
            x, y = nx, ny
            if depth < 2 and rng.random() < 0.18:
                branch(x, y, a + rng.choice((-1, 1)) * rng.uniform(0.5, 1.0), length * 0.45, depth + 1)
            if math.hypot(x, y) > 0.95:
                break

    for i in range(9):
        branch(0, 0, i / 9 * TAU + rng.uniform(-0.2, 0.2), rng.uniform(0.6, 0.95), 0)
    dist = np.full(c.r.shape, 9.0)
    width = np.full(c.r.shape, 0.0)
    for x0, y0, x1, y1, d in lines:
        dd = seg_dist(c.u, c.v, x0, y0, x1, y1)
        w = (0.03 - 0.008 * d) * (1 - math.hypot(x1, y1) * 0.6)
        closer = dd < dist
        dist = np.where(closer, dd, dist)
        width = np.where(closer, w, width)
    c.put(dist < width * 1.9, (20, 16, 18), 0.85)
    c.put(dist < width, (255, 255, 255))
    c.put(c.r < 0.12, (20, 16, 18), 0.7)
    c.put(c.r < 0.07, (255, 255, 255))
    return c


def flash(frame):
    """A starburst of light: eight long rays and eight short, shrinking and thinning over four frames."""
    c = Canvas(64)
    k = 1 - frame / 4
    for i in range(16):
        a = i / 16 * TAU
        length = (0.95 if i % 2 == 0 else 0.55) * (0.5 + 0.5 * k)
        d = np.abs(ang_diff(c.t, a))
        ray = (d < 0.09 * k * np.clip(1 - c.r / length, 0, 1)) & (c.r < length)
        c.put(ray, (255, 255, 255))
    c.put(c.r < 0.28 * k, (255, 255, 255))
    c.under(np.clip(1 - c.r / (0.6 * k + 0.1), 0, 1), (255, 255, 255), 0.4 * k)
    return c


# ----------------------------------------------------------------------------- debris (32)

def thorn(variant):
    """A curved thorn of dark bark with a pale tip."""
    c = Canvas(32)
    bend = 0.35 if variant == 0 else -0.25
    y = c.v
    centre = bend * (y + 1) ** 2 / 4
    width = 0.32 * np.clip((y + 0.9) / 1.7, 0, 1)
    m = (np.abs(c.u - centre) < width) & (y > -0.9) & (y < 0.85)
    c.put(m, BARK)
    c.put(m & (c.u - centre < -width * 0.3), BARK_HI)
    c.put(m & (y < -0.45), (196, 176, 150))
    return c


def bark(variant):
    """A splinter of bark, ragged, with a light inner face."""
    c = Canvas(32)
    rot = [0.4, -0.8, 1.3][variant]
    x, y = c.u, c.v
    cr, sr = math.cos(rot), math.sin(rot)
    xr, yr = x * cr + y * sr, -x * sr + y * cr
    jag = 0.22 + 0.1 * np.sin(yr * 9 + variant)
    m = (np.abs(xr) < jag) & (np.abs(yr) < 0.85)
    c.put(m, BARK)
    c.put(m & (xr > jag * 0.2), BARK_HI)
    c.put(m & (np.abs(np.sin(yr * 14)) < 0.15), (52, 34, 22))
    return c


def leaf(frame):
    """A leaf of the Matriarch's branches, its veins faintly violet, turning as it falls (its width over frames)."""
    c = Canvas(32)
    sx = [1.0, 0.6, 0.18, 0.6][frame]
    x, y = c.u / max(sx, 0.12), c.v
    shape = np.abs(x) < 0.5 * np.clip(1 - (y / 0.85) ** 2, 0, 1) ** 0.8
    c.put(shape, (54, 80, 44) if frame != 2 else (40, 60, 34))
    c.put(shape & (x > 0.1), (74, 104, 58))
    c.put(shape & (np.abs(x) < 0.07), (150, 110, 210))
    for j in (-0.4, 0.0, 0.4):
        c.put(shape & (np.abs(np.abs(x) - (c.v - j) * 0.9) < 0.07) & (c.v > j), (120, 92, 180))
    c.put((np.abs(c.u) < 0.06) & (c.v > 0.75) & (c.v < 0.98), BARK_HI)
    return c


def coin(frame):
    """The obol spinning: eight frames, its face turning edge-on and back, the boat on one face, an eye on the
    other."""
    c = Canvas(32)
    a = frame / 8 * TAU
    w = math.cos(a)
    sx = max(abs(w), 0.12)
    x, y = c.u / sx, c.v
    disc = np.hypot(x, y) < 0.8
    c.put(disc, GOLD_DEEP)
    if abs(w) > 0.3:
        face = np.hypot(x, y) < 0.66
        c.put(face, GOLD if w > 0 else mix(GOLD, GOLD_DEEP, 0.3))
        if w > 0:
            c.put(face & (y > 0.05) & (y < 0.25) & (np.abs(x) < 0.45 - (y - 0.05) * 1.2), GOLD_DEEP)
            c.put(face & (np.abs(x) < 0.06) & (y > -0.45) & (y < 0.08), GOLD_DEEP)
        else:
            eye = np.abs(y) < 0.2 * np.clip(1 - (x / 0.45) ** 2, 0, 1)
            c.put(face & eye, GOLD_DEEP)
            c.put(face & (np.hypot(x, y) < 0.1), (255, 236, 170))
        c.put(disc & (x < -0.3) & (y < -0.2), (255, 240, 190), 0.7)
    else:
        c.put(disc & (np.abs(c.u) < 0.09), GOLD)
    return c


def soulflame(frame):
    """A tongue of spirit fire: a teardrop flickering upward, white within and darker at its edges; in two of the
    frames a face shows in it for a moment."""
    c = Canvas(32)
    ph = frame / 8 * TAU
    x = c.u + 0.12 * np.sin(c.v * 4 + ph) * (1 - c.v) / 2
    y = c.v
    h = 0.95 - 0.15 * math.sin(ph * 2)
    width = 0.55 * np.clip((y + h) / (h + 0.7), 0, 1) ** 0.7 * np.clip((0.85 - y) / 0.3, 0, 1)
    k = np.abs(x) / np.maximum(width, 1e-3)
    m = (k < 1) & (y > -h) & (y < 0.85)
    # drawn in greys: the particle tints it (teal for the Ferryman, violet for the Matriarch, gold for the Weaver)
    c.put(m, (104, 104, 104))
    c.put(m & (k < 0.75) & (y > -h * 0.6), (196, 196, 196))
    c.put(m & (k < 0.4) & (y > -h * 0.15), (255, 255, 255))
    if frame in (2, 3):
        for sx in (-1, 1):
            c.put(np.hypot(c.u - sx * 0.16, c.v - 0.18) < 0.09, (60, 60, 60))
        c.put((np.abs(c.u) < 0.18) & (np.abs(c.v - 0.45) < 0.04), (60, 60, 60))
    return c


def ash(variant):
    c = Canvas(32)
    rng = np.random.default_rng(variant + 7)
    pts = rng.uniform(-0.5, 0.5, (5, 2))
    m = np.zeros(c.r.shape, bool)
    for (x, y) in pts:
        m |= np.hypot(c.u - x, c.v - y) < 0.22
    c.put(m, (116, 112, 110))
    c.put(m & (c.u + c.v < -0.2), (176, 170, 164))
    c.put(m & (h2(c.px, c.py, variant) > 0.8), (60, 58, 58))
    return c


def shard(variant):
    """A shard of the Weaver's glass needles: a faceted sliver, pale violet-white, a bright edge."""
    c = Canvas(32)
    rng = np.random.default_rng(variant + 30)
    a = rng.uniform(0, TAU)
    pts = [(math.cos(a + k * 2.2 + rng.uniform(-0.3, 0.3)) * rng.uniform(0.5, 0.9),
            math.sin(a + k * 2.2 + rng.uniform(-0.3, 0.3)) * rng.uniform(0.5, 0.9)) for k in range(3)]
    (ax, ay), (bx, by), (cx, cy) = pts

    def side(px, py, x0, y0, x1, y1):
        return (x1 - x0) * (py - y0) - (y1 - y0) * (px - x0)
    s1, s2, s3 = side(c.u, c.v, ax, ay, bx, by), side(c.u, c.v, bx, by, cx, cy), side(c.u, c.v, cx, cy, ax, ay)
    inside = ((s1 >= 0) & (s2 >= 0) & (s3 >= 0)) | ((s1 <= 0) & (s2 <= 0) & (s3 <= 0))
    c.put(inside, (190, 170, 240), 0.85)
    c.put(inside & (seg_dist(c.u, c.v, ax, ay, (bx + cx) / 2, (by + cy) / 2) < 0.08), (250, 244, 255))
    edge = np.minimum(np.minimum(seg_dist(c.u, c.v, ax, ay, bx, by), seg_dist(c.u, c.v, bx, by, cx, cy)), seg_dist(c.u, c.v, cx, cy, ax, ay))
    c.put(inside & (edge < 0.07), (255, 255, 255))
    return c


def glint(frame):
    """A four-pointed star of light, opening and closing."""
    c = Canvas(32)
    k = [0.4, 1.0, 0.7, 0.25][frame]
    for a in (0, math.pi / 2):
        d = np.abs(c.u * math.sin(a) - c.v * math.cos(a))
        along = np.abs(c.u * math.cos(a) + c.v * math.sin(a))
        c.put((d < 0.09 * (1 - along / (0.95 * k + 0.01))) & (along < 0.95 * k), (255, 255, 255))
    c.under(np.clip(1 - c.r / (0.45 * k + 0.05), 0, 1), (255, 255, 255), 0.5)
    return c


# ----------------------------------------------------------------------------- projectiles (64)

def lantern(frame):
    """One of the Matriarch's lanterns: a black iron cage with a pointed cap and a ring, violet fire within, its
    light spilling out between the bars."""
    c = Canvas(64)
    x, y = c.u, c.v
    ph = frame / 4 * TAU
    c.under(np.clip(1 - np.hypot(x, y * 0.8) / 0.95, 0, 1), VIOLET_DEEP, 0.55)
    body = (np.abs(x) < 0.36) & (y > -0.42) & (y < 0.5)
    c.put(body, (36, 20, 54))
    flame_h = 0.42 + 0.06 * math.sin(ph)
    fx = x + 0.04 * np.sin(y * 9 + ph)
    flame = (np.abs(fx) < 0.24 * np.clip((0.42 - y) / (flame_h + 0.42), 0, 1) ** 0.6) & (y > -flame_h) & (y < 0.42)
    c.put(body & flame, VIOLET)
    c.put(body & flame & (np.abs(fx) < 0.1) & (y > -0.1), VIOLET_HOT)
    bars = body & ((np.abs(np.abs(x) - 0.36) < 0.05) | (np.abs(x) < 0.03) | (np.abs(np.abs(x) - 0.18) < 0.025))
    c.put(bars, IRON)
    c.put(bars & (x < 0), IRON_HI)
    for yy in (-0.45, 0.5):
        c.put((np.abs(x) < 0.44) & (np.abs(y - yy) < 0.06), IRON)
    cap = (y < -0.45) & (y > -0.75) & (np.abs(x) < 0.44 * (y + 0.75) / 0.3)
    c.put(cap, IRON)
    c.put(cap & (x < 0), IRON_HI)
    ring = np.abs(np.hypot(x, y + 0.85) - 0.1) < 0.035
    c.put(ring, IRON_HI)
    return c


def wisp(frame):
    """A soul wisp of the Ferryman: a teal flame with a skull's face in it, its tail streaming behind."""
    c = Canvas(64)
    ph = frame / 6 * TAU
    x = c.u + 0.08 * np.sin(c.v * 5 + ph)
    y = c.v
    c.under(np.clip(1 - np.hypot(c.u, c.v) / 0.9, 0, 1), TEAL_DEEP, 0.55)
    head = np.hypot(x, (y - 0.1) * 1.1) < 0.45
    tail = (y < 0.1) & (y > -0.95) & (np.abs(x) < 0.45 * np.clip((y + 0.95) / 1.05, 0, 1) ** 1.4)
    m = head | tail
    c.put(m, TEAL_DEEP)
    c.put(m & (np.hypot(x, (y - 0.1) * 1.1) < 0.36) | (tail & (np.abs(x) < 0.25 * np.clip((y + 0.7) / 0.8, 0, 1))), TEAL)
    c.put(np.hypot(x, (y - 0.12) * 1.1) < 0.24, TEAL_HOT)
    for sx in (-1, 1):
        c.put(np.hypot(c.u - sx * 0.13, c.v - 0.08) < 0.075 + 0.01 * math.sin(ph + sx), (10, 40, 44))
    c.put((np.abs(c.u) < 0.03) & (c.v > 0.18) & (c.v < 0.26), (10, 40, 44))
    c.put((np.abs(c.u) < 0.12) & (np.abs(c.v - 0.33) < 0.025) & (np.sin(c.u * 60) > -0.3), (10, 40, 44))
    return c


def needle(variant):
    """A needle of light (and, larger, the lance): a crystal shaft with a gilt eye at its head, falling point
    down."""
    c = Canvas(64)
    x, y = c.u, c.v
    w = 0.07 if variant == 0 else 0.13
    c.under(np.clip(1 - np.abs(x) / (w * 5), 0, 1) * np.clip(1 - np.abs(y) / 1.0, 0, 1) ** 0.5, WEB_VIOLET, 0.45)
    shaft = (np.abs(x) < w * np.clip((0.98 - y) / 0.35, 0, 1)) & (y > -0.7) & (y < 0.98)
    c.put(shaft, (200, 180, 250))
    c.put(shaft & (x < 0), (255, 252, 255))
    eye = np.hypot(x / (w * 1.9), (y + 0.82) / 0.16)
    c.put((eye < 1.0) & (eye > 0.45), GILT)
    c.put(eye <= 0.45, (40, 20, 80))
    if variant == 1:
        for k in range(4):
            yy = -0.4 + k * 0.3
            c.put((np.abs(y - yy) < 0.025) & (np.abs(x) < w * 1.5), GILT)
    return c


# ----------------------------------------------------------------------------- line tiles (32, repeat vertically)

def thread(variant):
    """A thread of the Weaver's silk, along the tile: a lit core, twisted fibres crossing it, a soft halo (dim and
    violet while it is slack, white and gold when it is pulled taut)."""
    c = Canvas(32)
    core, halo = ((210, 180, 255), WEB_VIOLET) if variant == 0 else ((255, 252, 240), GILT)
    x, y = c.u, (c.v + 1) / 2
    c.under(np.clip(1 - np.abs(x) / 0.7, 0, 1) ** 2, halo, 0.5 if variant else 0.35)
    c.put(np.abs(x) < 0.09, core)
    for k in range(2):
        fib = 0.16 * np.sin((y + k * 0.5) * TAU * 2)
        c.put(np.abs(x - fib) < 0.05, (255, 255, 255) if variant else (236, 220, 255))
    if variant == 1:
        c.put((np.abs(x) < 0.25) & (np.abs(y - 0.5) < 0.04), (255, 255, 255))
    return c


def chain(variant):
    """A link of the Ferryman's chains of fire, along the tile: a flat link and an edge-on one by turns, soul fire
    licking off the iron."""
    c = Canvas(32)
    x, y = c.u, c.v
    c.under(np.clip(1 - np.abs(x) / 0.85, 0, 1) ** 1.5, TEAL_DEEP, 0.55)
    if variant == 0:
        e = np.hypot(x / 0.48, y / 0.98)
        ring = (e > 0.62) & (e < 1.0)
        c.put(ring, IRON)
        c.put(ring & (x < 0), IRON_HI)
    else:
        bar = (np.abs(x) < 0.12) & (np.abs(y) < 0.98)
        c.put(bar, IRON_HI)
        c.put(bar & (x > 0.03), IRON)
    flick = (h2(np.floor(c.px / 2), np.floor(c.py / 3), 40 + variant) > 0.82) & (np.abs(x) > 0.4) & (np.abs(x) < 0.75)
    c.put(flick, TEAL)
    c.put(flick & (h2(c.px, c.py, 9) > 0.6), TEAL_HOT)
    return c


def beam(frame):
    """The lance's beam, along the tile: a white core, gold and violet round it, and arcs of light crackling off
    it that change every frame."""
    c = Canvas(32)
    x, y = c.u, (c.v + 1) / 2
    c.under(np.clip(1 - np.abs(x) / 0.95, 0, 1) ** 1.6, WEB_VIOLET, 0.6)
    c.put(np.abs(x) < 0.42, GILT, 0.85)
    c.put(np.abs(x) < 0.26, (255, 244, 220))
    c.put(np.abs(x) < 0.12, (255, 255, 255))
    rng = np.random.default_rng(frame + 70)
    for _ in range(2):
        y0 = rng.uniform(0, 1)
        sgn = rng.choice((-1, 1))
        pts = [(0.3 * sgn, y0)]
        for k in range(4):
            px, py = pts[-1]
            pts.append((px + sgn * rng.uniform(0.08, 0.18), py + rng.uniform(-0.15, 0.15)))
        for (x0, y0_), (x1, y1) in zip(pts, pts[1:]):
            c.put(seg_dist(x, y, x0, y0_, x1, y1) < 0.035, (255, 255, 255))
    return c


def stream(frame):
    """A stream of soul fire along the tile, flowing (its blobs move down a quarter of the tile each frame)."""
    c = Canvas(32)
    x, y = c.u, (c.v + 1) / 2
    c.under(np.clip(1 - np.abs(x) / 0.8, 0, 1) ** 1.5, TEAL_DEEP, 0.55)
    wob = 0.15 * np.sin((y + frame / 4) * TAU)
    c.put(np.abs(x - wob) < 0.32, TEAL)
    c.put(np.abs(x - wob) < 0.14, TEAL_HOT)
    blob = np.hypot(x - wob * 1.5, ((y + frame / 4) % 0.5 - 0.25) * 2.5) < 0.22
    c.put(blob, (255, 255, 255))
    return c


# ----------------------------------------------------------------------------- families

FAMILIES = {
    "fx_sigil": [sigil_matriarch, sigil_ferryman, sigil_weaver],
    "fx_wave": [wave_matriarch, wave_ferryman, wave_weaver],
    "fx_crack": [crack],
    "fx_flash": [lambda k=k: flash(k) for k in range(4)],
    "fx_thorn": [lambda k=k: thorn(k) for k in range(2)],
    "fx_bark": [lambda k=k: bark(k) for k in range(3)],
    "fx_leaf": [lambda k=k: leaf(k) for k in range(4)],
    "fx_coin": [lambda k=k: coin(k) for k in range(8)],
    "fx_soulflame": [lambda k=k: soulflame(k) for k in range(8)],
    "fx_ash": [lambda k=k: ash(k) for k in range(4)],
    "fx_shard": [lambda k=k: shard(k) for k in range(4)],
    "fx_glint": [lambda k=k: glint(k) for k in range(4)],
    "fx_lantern": [lambda k=k: lantern(k) for k in range(4)],
    "fx_wisp": [lambda k=k: wisp(k) for k in range(6)],
    "fx_needle": [lambda k=k: needle(k) for k in range(2)],
    "fx_thread": [lambda k=k: thread(k) for k in range(2)],
    "fx_chain": [lambda k=k: chain(k) for k in range(2)],
    "fx_beam": [lambda k=k: beam(k) for k in range(4)],
    "fx_stream": [lambda k=k: stream(k) for k in range(4)],
}


def main():
    os.makedirs(TEX, exist_ok=True)
    sheet = []
    for fam, makers in FAMILIES.items():
        names = []
        for i, make in enumerate(makers):
            name = f"{fam}_{i}"
            img = make().save(name)
            names.append(f"extremedimensions:{name}")
            sheet.append(img)
        with open(os.path.join(JSON, fam + ".json"), "w") as f:
            json.dump({"textures": names}, f, indent=2)
            f.write("\n")
    print(f"fx: {len(FAMILIES)} families, {len(sheet)} sprites")
    if len(sys.argv) > 1:
        cell = 128
        cols = 12
        rows = (len(sheet) + cols - 1) // cols
        out = Image.new("RGBA", (cols * cell, rows * cell), (40, 44, 36, 255))
        for i, img in enumerate(sheet):
            im = img.resize((cell, cell), Image.NEAREST)
            out.alpha_composite(im, ((i % cols) * cell, (i // cols) * cell))
        out.save(sys.argv[1])


if __name__ == "__main__":
    main()
