"""
Textures of the rift portal: a tear in the world with a jagged rim of magenta energy, cracks running out into
the air around it like shattered glass, a tunnel of streaking light and, at its far end, a hazy view of the world
on the other side.

Writes to src/client/resources/assets/extremedimensions/textures/entity/rift/:
  rim_a.png, rim_b.png   the burning rim (two, drawn over each other turning opposite ways, so it flickers)
  cracks.png             cracks in the air around the rim
  tunnel.png             streaks of light running down the tunnel
  haze.png               the faint shimmering skin across the opening
  shard.png              a chip of rock torn loose, with glowing cracks (the shards that orbit it)
  view_overworld_day.png, view_overworld_night.png, view_nether.png, view_seam.png
                         the far end of the tunnel: the world it leads to, soft-edged and hazy
Run from the repository root: python3 tools/rift/build.py [--preview out.png]
"""
import math
import os
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

OUT = "src/client/resources/assets/extremedimensions/textures/entity/rift"


# ---------------------------------------------------------------------------- helpers

def grid(n):
    """Normalised coordinates of an n by n texture: x, y in -1..1, radius and angle."""
    c = (np.arange(n) + 0.5) / n * 2 - 1
    x, y = np.meshgrid(c, c)
    return x, y, np.sqrt(x * x + y * y), np.arctan2(y, x)


def angular_noise(theta, rng, octaves=((8, 1.0), (19, 0.5), (41, 0.25))):
    """Smooth periodic noise around the circle: a sum of sines with random phases, in -1..1."""
    out = np.zeros_like(theta)
    total = 0
    for freq, amp in octaves:
        for _ in range(2):
            out += amp * np.sin(theta * freq + rng.uniform(0, 2 * math.pi)) * 0.5
        total += amp
    return out / total


def value_noise(n, cells, rng):
    """Smooth 2D value noise in 0..1 over an n by n texture."""
    g = rng.random((cells + 1, cells + 1))
    c = np.linspace(0, cells, n, endpoint=False)
    x, y = np.meshgrid(c, c)
    x0, y0 = x.astype(int), y.astype(int)
    fx, fy = x - x0, y - y0
    fx, fy = fx * fx * (3 - 2 * fx), fy * fy * (3 - 2 * fy)
    a, b = g[y0, x0], g[y0, x0 + 1]
    c2, d = g[y0 + 1, x0], g[y0 + 1, x0 + 1]
    return a + (b - a) * fx + (c2 - a) * fy + (a - b - c2 + d) * fx * fy


def ramp(v, stops):
    """Colours along a ramp of (position, (r, g, b)) stops, for values v in 0..1."""
    out = np.zeros(v.shape + (3,))
    pos = [s[0] for s in stops]
    for ch in range(3):
        out[..., ch] = np.interp(v, pos, [s[1][ch] for s in stops])
    return out


def save(rgb, alpha, name):
    rgba = np.dstack([np.clip(rgb, 0, 255), np.clip(alpha, 0, 1) * 255]).astype(np.uint8)
    Image.fromarray(rgba, "RGBA").save(os.path.join(OUT, name))
    return Image.fromarray(rgba, "RGBA")


ENERGY = [(0.0, (90, 20, 160)), (0.3, (170, 30, 210)), (0.55, (235, 60, 235)), (0.8, (255, 150, 245)), (1.0, (255, 245, 255))]


def lightning(draw, rng, x, y, ang, length, width, n, scale, branch=0.35, depth=0):
    """A jagged crack or bolt from (x, y) heading ang, forking now and then."""
    step = length / n
    for i in range(n):
        ang += rng.normal(0, 0.45)
        nx, ny = x + math.cos(ang) * step, y + math.sin(ang) * step
        draw.line([(x * scale, y * scale), (nx * scale, ny * scale)], fill=255, width=max(1, int(round(width))))
        if depth < 2 and rng.random() < branch / (1 + depth):
            lightning(draw, rng, nx, ny, ang + rng.choice([-1, 1]) * rng.uniform(0.4, 1.0), length * (n - i) / n * 0.6,
                      width * 0.6, max(2, (n - i) // 2), scale, branch, depth + 1)
        x, y = nx, ny
        width *= 0.93


def glow_layer(mask, radii=((1.5, 1.0), (5, 0.55), (14, 0.3))):
    """A mask of thin lines turned into lines with a glow: sharp core plus blurred halos, in 0..1."""
    img = Image.fromarray(mask)
    out = np.asarray(img, float) / 255
    for r, k in radii:
        out = np.maximum(out, np.asarray(img.filter(ImageFilter.GaussianBlur(r)), float) / 255 * k * 2.2)
    return np.clip(out, 0, 1)


# ---------------------------------------------------------------------------- the rim

def rim(name, seed, n=512):
    rng = np.random.default_rng(seed)
    x, y, r, th = grid(n)
    # the ring's radius wobbles; flames lick outward from it (sharper spikes from the square of the noise)
    edge = 0.6 + 0.025 * angular_noise(th, rng) + 0.01 * angular_noise(th, rng, ((63, 1.0), (97, 0.6)))
    flame = np.maximum(0, angular_noise(th, rng, ((13, 1.0), (29, 0.7), (53, 0.4)))) ** 2 * 0.22
    d = r - edge
    out_side = np.where(d > 0, d / (0.07 + flame), 0)
    in_side = np.where(d < 0, -d / 0.05, 0)
    band = np.exp(-(out_side + in_side) ** 1.2)
    core = np.exp(-(np.abs(d) / 0.018) ** 2)
    # a slow mottling along the ring, so it is not even all round
    mottle = 0.7 + 0.3 * (0.5 + 0.5 * angular_noise(th, rng, ((5, 1.0), (11, 0.6))))
    level = np.clip(band * 0.78 * mottle + core * 0.55, 0, 1)
    # bolts running out of the ring into the air, a few in towards the middle: thick, few, forking little
    lines = Image.new("L", (n, n), 0)
    dr = ImageDraw.Draw(lines)
    for i in range(16):
        a = rng.uniform(0, 2 * math.pi)
        out = rng.random() < 0.8
        sx, sy = 0.5 + math.cos(a) * 0.3, 0.5 + math.sin(a) * 0.3
        lightning(dr, rng, sx, sy, a if out else a + math.pi, rng.uniform(0.08, 0.19) if out else rng.uniform(0.03, 0.07),
                  rng.uniform(3.0, 5.0), int(rng.integers(5, 8)), n, 0.25)
    crack = glow_layer(np.asarray(lines), ((1.5, 0.85), (5, 0.5), (12, 0.28)))
    level = np.clip(np.maximum(level, crack * 0.9), 0, 1)
    colour = ramp(level, ENERGY)
    alpha = np.clip(level * 1.3, 0, 1)
    # inside the ring, a band of deep violet that frames the opening (the mouth of the tunnel)
    mouth = np.clip((r - 0.36) / (edge - 0.36), 0, 1) ** 2 * (r < edge)
    dark = np.array([46, 10, 78])
    k = np.clip(mouth * 0.75 * (1 - level), 0, 1)
    colour = colour * (1 - k[..., None]) + dark * k[..., None]
    alpha = np.clip(np.maximum(alpha, k), 0, 1) * (r < 0.99)
    save(colour, alpha, name)


def cracks(n=512, seed=7):
    """Cracks in the air around the rim, running out like breaks in glass, dimmer the farther out."""
    rng = np.random.default_rng(seed)
    lines = Image.new("L", (n, n), 0)
    dr = ImageDraw.Draw(lines)
    for i in range(22):
        a = rng.uniform(0, 2 * math.pi)
        sx, sy = 0.5 + math.cos(a) * 0.3, 0.5 + math.sin(a) * 0.3
        lightning(dr, rng, sx, sy, a + rng.normal(0, 0.2), rng.uniform(0.12, 0.2), rng.uniform(1.2, 2.2), int(rng.integers(6, 11)), n, 0.5)
    x, y, r, th = grid(n)
    level = glow_layer(np.asarray(lines), ((1.2, 1.0), (4, 0.45), (10, 0.2)))
    fade = np.clip((1 - r) / 0.4, 0, 1) * np.clip((r - 0.55) / 0.08, 0, 1)
    level = level * fade
    save(ramp(np.clip(level * 0.9 + 0.1, 0, 1), ENERGY), level * 0.9, "cracks.png")


def tunnel(n=512, seed=11):
    """Streaks of light down the tunnel's walls: radial lines twisted into a slow spiral, open in the middle."""
    rng = np.random.default_rng(seed)
    x, y, r, th = grid(n)
    twist = th + 1.6 * np.log(np.maximum(r, 0.02))
    streak = np.zeros_like(r)
    for freq, amp in ((23, 1.0), (47, 0.6), (89, 0.35), (151, 0.2)):
        streak += amp * (0.5 + 0.5 * np.sin(twist * freq + rng.uniform(0, 6.28)))
    streak = (streak / 2.15) ** 3
    wall = np.clip((r - 0.34) / 0.55, 0, 1) ** 1.8 * np.clip((1.0 - r) / 0.08, 0, 1)
    level = np.clip(streak * 1.4, 0, 1) * wall
    colour = ramp(np.clip(0.2 + level * 0.8, 0, 1), [(0.0, (60, 20, 140)), (0.4, (150, 50, 220)), (0.75, (240, 120, 250)), (1.0, (255, 240, 255))])
    save(colour, np.clip(level * 0.9 + wall * 0.12, 0, 1), "tunnel.png")


def haze(n=256, seed=13):
    """The skin across the opening: faint at the middle (the far world shows through), thicker towards the rim."""
    rng = np.random.default_rng(seed)
    x, y, r, th = grid(n)
    mottle = value_noise(n, 6, rng) * 0.6 + value_noise(n, 13, rng) * 0.4
    a = (0.06 + 0.32 * np.clip(r / 0.62, 0, 1) ** 3) * (0.7 + 0.6 * mottle) * (r < 0.64)
    save(ramp(np.clip(0.55 + mottle * 0.45, 0, 1), ENERGY), a, "haze.png")


def shard(n=32, seed=17):
    """A chip of dark rock with magenta glowing cracks across it."""
    rng = np.random.default_rng(seed)
    base = value_noise(n, 4, rng) * 0.6 + value_noise(n, 9, rng) * 0.4
    rgb = ramp(base, [(0.0, (22, 16, 34)), (0.5, (44, 34, 62)), (1.0, (78, 62, 98))])
    lines = Image.new("L", (n, n), 0)
    dr = ImageDraw.Draw(lines)
    for i in range(3):
        lightning(dr, rng, rng.uniform(0.1, 0.9), rng.uniform(0.1, 0.9), rng.uniform(0, 6.28), 0.6, 1, 5, n, 0.3)
    crack = np.asarray(lines, float) / 255
    glow = np.asarray(lines.filter(ImageFilter.GaussianBlur(1.0)), float) / 255
    k = np.clip(crack + glow * 0.8, 0, 1)[..., None]
    rgb = rgb * (1 - k) + np.array([255, 110, 245]) * k
    save(rgb, np.ones((n, n)), "shard.png")


# ---------------------------------------------------------------------------- the worlds at the far end

def blocky_ridge(w, rng, base, amp, step, rough=0.5):
    """A blocky skyline: heights in whole steps, as terrain looks from afar in a world of blocks."""
    h, out = base, []
    for i in range(0, w, step):
        h += rng.normal(0, amp * rough)
        h = base + (h - base) * 0.85
        out += [int(round(h))] * step
    return out[:w]


def view(kind, n=256, low=128, seed=23):
    """A painted view of a world, low resolution and blocky, then softened: what shows at the tunnel's far end."""
    rng = np.random.default_rng(seed)
    img = Image.new("RGB", (low, low))
    d = ImageDraw.Draw(img)
    def sky(top, bottom):
        for yy in range(low):
            t = yy / (low - 1)
            d.line([(0, yy), (low, yy)], fill=tuple(int(top[i] + (bottom[i] - top[i]) * t) for i in range(3)))
    if kind == "overworld_day":
        sky((92, 140, 255), (200, 222, 255))
        d.rectangle([84, 14, 98, 28], fill=(255, 248, 200))
        for i in range(7):
            cx, cy = int(rng.integers(0, low)), int(rng.integers(8, 44))
            for j in range(3):
                d.rectangle([cx + j * 6, cy - (j % 2) * 3, cx + j * 6 + 10, cy + 4], fill=(250, 252, 255))
        layers = [(70, 7, 6, (128, 152, 190)), (82, 5, 4, (84, 130, 96)), (96, 4, 3, (78, 150, 66))]
        for base, amp, step, col in layers:
            ridge = blocky_ridge(low, rng, base, amp, step)
            for xx, hh in enumerate(ridge):
                d.line([(xx, hh), (xx, low)], fill=col)
        for i in range(9):
            tx, ty = int(rng.integers(0, low)), int(rng.integers(86, 104))
            d.rectangle([tx, ty, tx + 1, ty + 6], fill=(92, 66, 40))
            d.rectangle([tx - 3, ty - 6, tx + 4, ty + 1], fill=(48, 104, 40))
        d.rectangle([0, 112, low, low], fill=(96, 162, 70))
    elif kind == "overworld_night":
        sky((8, 12, 40), (34, 44, 88))
        for i in range(70):
            sx, sy = int(rng.integers(0, low)), int(rng.integers(0, 70))
            d.point((sx, sy), fill=(230, 230, 255) if rng.random() < 0.6 else (180, 180, 230))
        d.rectangle([30, 16, 41, 27], fill=(225, 230, 245))
        d.rectangle([33, 19, 35, 21], fill=(190, 196, 214))
        layers = [(72, 7, 6, (30, 38, 70)), (84, 5, 4, (22, 40, 40)), (98, 4, 3, (20, 46, 30))]
        for base, amp, step, col in layers:
            ridge = blocky_ridge(low, rng, base, amp, step)
            for xx, hh in enumerate(ridge):
                d.line([(xx, hh), (xx, low)], fill=col)
        for i in range(5):
            wx, wy = int(rng.integers(10, low - 10)), int(rng.integers(92, 110))
            d.rectangle([wx, wy, wx + 1, wy + 1], fill=(255, 200, 110))
        d.rectangle([0, 114, low, low], fill=(18, 40, 26))
    elif kind == "nether":
        sky((70, 12, 12), (176, 62, 30))
        layers = [(52, 10, 5, (110, 30, 26)), (74, 8, 4, (86, 20, 20)), (100, 5, 3, (64, 14, 16))]
        for base, amp, step, col in layers:
            ridge = blocky_ridge(low, rng, base, amp, step, 0.8)
            for xx, hh in enumerate(ridge):
                d.line([(xx, hh), (xx, low)], fill=col)
        for i in range(4):
            lx = int(rng.integers(10, low - 10))
            d.rectangle([lx, 40 + int(rng.integers(0, 20)), lx + 2, 118], fill=(255, 140, 30))
        for i in range(6):
            gx, gy = int(rng.integers(0, low)), int(rng.integers(4, 30))
            d.rectangle([gx, gy, gx + 3, gy + 3], fill=(250, 214, 120))
        d.rectangle([0, 118, low, low], fill=(255, 120, 26))
    else:  # the Seam
        sky((34, 16, 70), (130, 64, 150))
        for i in range(60):
            sx, sy = int(rng.integers(0, low)), int(rng.integers(0, 80))
            d.point((sx, sy), fill=(255, 220, 255))
        for i in range(7):
            ix, iy, w = int(rng.integers(-10, low)), int(rng.integers(26, 92)), int(rng.integers(12, 30))
            dep = w // 2 + int(rng.integers(2, 8))
            d.line([(ix + w // 3, iy + 3), (ix + w // 3, min(low, iy + 46))], fill=(200, 186, 240))
            d.polygon([(ix, iy + 2), (ix + w, iy + 2), (ix + w * 2 // 3, iy + dep * 2 // 3), (ix + w // 2, iy + dep),
                       (ix + w // 3, iy + dep * 2 // 3)], fill=(40, 26, 62))
            d.rectangle([ix, iy, ix + w, iy + 2], fill=(84, 110, 92))
        d.rectangle([0, 112, low, low], fill=(64, 40, 100))
    big = img.resize((n, n), Image.NEAREST).filter(ImageFilter.GaussianBlur(1.6))
    rgb = np.asarray(big, float)
    # a breath of haze over it, brighter towards the middle where the tunnel opens onto it
    x, y, r, th = grid(n)
    hz = np.clip(1 - r, 0, 1) ** 2 * 0.18
    rgb = rgb * (1 - hz[..., None]) + np.array([255, 230, 255]) * hz[..., None]
    alpha = np.clip((1.0 - r) / 0.22, 0, 1) ** 1.5
    save(rgb, alpha, "view_" + kind + ".png")


def main():
    os.makedirs(OUT, exist_ok=True)
    rim("rim_a.png", 101)
    rim("rim_b.png", 202)
    cracks()
    tunnel()
    haze()
    shard()
    for k in ("overworld_day", "overworld_night", "nether", "seam"):
        view(k)
    if "--preview" in sys.argv:
        preview(sys.argv[sys.argv.index("--preview") + 1])


def preview(path):
    """The layers drawn over each other as the renderer stacks them, over a dark violet ground."""
    n = 512
    bg = Image.new("RGBA", (n * 2, n), (40, 24, 70, 255))
    def lay(name, size, angle=0, alpha=1.0, at=(0, 0)):
        im = Image.open(os.path.join(OUT, name)).convert("RGBA").resize((size, size)).rotate(angle)
        if alpha < 1:
            a = np.asarray(im, float)
            a[..., 3] *= alpha
            im = Image.fromarray(a.astype(np.uint8), "RGBA")
        bg.alpha_composite(im, (at[0] + (n - size) // 2, at[1] + (n - size) // 2))
    lay("view_overworld_day.png", 250)
    lay("tunnel.png", 300, 20, 0.8)
    lay("tunnel.png", 380, -15, 0.7)
    lay("haze.png", 440, 0, 1.0)
    lay("cracks.png", 512, 10, 0.8)
    lay("rim_a.png", 512, 0)
    lay("rim_b.png", 512, 40, 0.8)
    for i, k in enumerate(("overworld_day", "overworld_night", "nether", "seam")):
        im = Image.open(os.path.join(OUT, "view_" + k + ".png")).resize((240, 240))
        bg.alpha_composite(im, (n + 10 + (i % 2) * 250, 10 + (i // 2) * 250))
    bg.save(path)


if __name__ == "__main__":
    main()
