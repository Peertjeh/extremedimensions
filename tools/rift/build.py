"""
Textures of the rift portal, drawn the way Minecraft draws its own: pixel art at about sixteen pixels to a block, a
few flat colours per material, hard pixel edges and no blur.

The rift is framed by blocks (the renderer builds a ring of them round the opening, like a nether portal's frame
built round), and the opening holds a swirling skin like a nether portal's over a tunnel to the world on the other
side.

Writes to src/client/resources/assets/extremedimensions/textures/entity/rift/:
  frame.png        the frame's blocks, four 16x16 faces side by side: obsidian, crying rift stone, rift stone bricks
                   and cracked rift stone
  frame_glow.png   the pixels of those faces that give light (drawn again over them, at full brightness)
  edge.png         the glowing lip of the opening, just inside the frame
  membrane.png     the skin across the opening, eight frames of a swirl one under the other (64x64 each)
  tunnel.png       a ring of streaking light down the tunnel
  view_overworld_day.png, view_overworld_night.png, view_nether.png, view_seam.png
                   the far end of the tunnel: the world it leads to, as a small pixel painting
Run from the repository root: python3 tools/rift/build.py [--preview out.png]
"""
import math
import os
import sys

import numpy as np
from PIL import Image, ImageDraw

OUT = "src/client/resources/assets/extremedimensions/textures/entity/rift"

# The rift's colours: obsidian, the violet of the Seam and the magenta of its tears
OBSIDIAN = [(9, 6, 16), (18, 12, 30), (30, 20, 48), (44, 30, 70), (62, 44, 96)]
RIFT = [(28, 14, 46), (46, 22, 74), (70, 34, 108), (98, 48, 140), (130, 70, 170)]
GLOW = [(150, 40, 200), (205, 70, 235), (245, 130, 250), (255, 210, 255)]
SWIRL = [(48, 8, 92), (86, 20, 150), (130, 40, 205), (176, 80, 236), (222, 150, 255)]


def save(rgba, name):
    Image.fromarray(np.clip(rgba, 0, 255).astype(np.uint8), "RGBA").save(os.path.join(OUT, name))


def pick(v, palette):
    """Flat colours from a palette for values v in 0..1 (no blending between them: pixel art)."""
    idx = np.clip((v * len(palette)).astype(int), 0, len(palette) - 1)
    return np.array(palette, float)[idx]


def value_noise(w, h, cells, rng):
    """Smooth value noise in 0..1, tiling across the texture (so faces and frames repeat seamlessly)."""
    g = rng.random((cells, cells))
    cx = np.arange(w) / w * cells
    cy = np.arange(h) / h * cells
    x, y = np.meshgrid(cx, cy)
    x0, y0 = x.astype(int) % cells, y.astype(int) % cells
    x1, y1 = (x0 + 1) % cells, (y0 + 1) % cells
    fx, fy = x - np.floor(x), y - np.floor(y)
    fx, fy = fx * fx * (3 - 2 * fx), fy * fy * (3 - 2 * fy)
    a, b, c, d = g[y0, x0], g[y0, x1], g[y1, x0], g[y1, x1]
    return a + (b - a) * fx + (c - a) * fy + (a - b - c + d) * fx * fy


# ---------------------------------------------------------------------------- the frame's blocks

def obsidian_face(rng):
    """Obsidian as Minecraft draws it: near black, with lighter violet facets catching the light diagonally."""
    v = value_noise(16, 16, 4, rng) * 0.55 + value_noise(16, 16, 8, rng) * 0.45
    y, x = np.mgrid[0:16, 0:16]
    facet = ((x + y + rng.integers(0, 4)) % 7 == 0) & (rng.random((16, 16)) < 0.55)
    v = np.where(facet, v + 0.35, v * 0.8)
    return pick(np.clip(v, 0, 0.999), OBSIDIAN), np.zeros((16, 16))


def crying_face(rng):
    """Obsidian weeping light: drips of magenta running down from cracks."""
    rgb, _ = obsidian_face(rng)
    glow = np.zeros((16, 16))
    for i in range(5):
        x = int(rng.integers(0, 16))
        y = int(rng.integers(0, 10))
        n = int(rng.integers(2, 7))
        for k in range(n):
            if y + k < 16:
                glow[y + k, x] = 1.0 - k / (n + 1)
        if x + 1 < 16:
            glow[y, x + 1] = max(glow[y, x + 1], 0.6)
    return rgb, glow


def brick_face(rng, cracked):
    """Rift stone bricks: courses of four pixels, bricks of eight, mortar a shade darker; cracked ones split with light."""
    v = value_noise(16, 16, 4, rng) * 0.5 + rng.random((16, 16)) * 0.25 + 0.25
    y, x = np.mgrid[0:16, 0:16]
    course = y // 4
    joint = (y % 4 == 3) | (((x + (course % 2) * 4) % 8) == 7)
    v = np.where(joint, 0.08, v)
    rgb = pick(np.clip(v, 0, 0.999), RIFT)
    glow = np.zeros((16, 16))
    if cracked:
        px, py = int(rng.integers(3, 13)), 0
        while py < 16:
            glow[py, px] = 1.0
            px = int(np.clip(px + rng.integers(-1, 2), 0, 15))
            py += 1
        for k in range(3):
            gx, gy = int(rng.integers(0, 16)), int(rng.integers(0, 16))
            glow[gy, gx] = 0.7
    return rgb, glow


def frame():
    rng = np.random.default_rng(31)
    faces = [obsidian_face(rng), crying_face(rng), brick_face(rng, False), brick_face(rng, True)]
    rgb = np.concatenate([f[0] for f in faces], axis=1)
    glow = np.concatenate([f[1] for f in faces], axis=1)
    # the glowing pixels show on the face itself too (the overlay adds their light)
    lit = glow > 0
    g = pick(np.clip(glow, 0, 0.999), GLOW)
    rgb[lit] = g[lit]
    save(np.dstack([rgb, np.full(glow.shape, 255)]), "frame.png")
    save(np.dstack([g, np.where(lit, 255, 0)]), "frame_glow.png")


# ---------------------------------------------------------------------------- the opening

def grid(n):
    """Pixel centres of an n by n texture: x, y in -1..1, radius and angle."""
    c = (np.arange(n) + 0.5) / n * 2 - 1
    x, y = np.meshgrid(c, c)
    return x, y, np.sqrt(x * x + y * y), np.arctan2(y, x)


def edge(n=64, seed=41):
    """The lip of the opening: a band of light two pixels wide at the rim, stepping down to violet within."""
    rng = np.random.default_rng(seed)
    x, y, r, th = grid(n)
    wobble = 0.012 * np.sin(th * 9 + rng.uniform(0, 6.28)) + 0.01 * np.sin(th * 17 + rng.uniform(0, 6.28))
    d = 0.97 + wobble - r
    lip = (d >= 0) & (d < 2.5 / n * 2)
    inner = (d >= 0) & (d < 0.2)
    v = np.clip(1 - d / 0.2, 0, 0.999)
    rgb = pick(v * 0.85, SWIRL)
    rgb[lip] = np.array(GLOW[2], float)
    alpha = np.where(lip, 255, np.where(inner, np.floor(v * 4) / 4 * 200, 0))
    save(np.dstack([rgb, alpha]), "edge.png")


def membrane(n=64, frames=8, seed=43):
    """The skin across the opening, like a nether portal's: bands of violet swirling round, frame by frame."""
    rng = np.random.default_rng(seed)
    x, y, r, th = grid(n)
    base = value_noise(n, n, 8, rng)
    out = []
    for f in range(frames):
        t = f / frames * 2 * math.pi
        swirl = np.sin(th * 3 + r * 9 - t + base * 4) * 0.5 + 0.5
        v = np.clip(swirl * 0.7 + np.sin(r * 14 - t * 2) * 0.15 + 0.15, 0, 0.999)
        rgb = pick(v, SWIRL)
        # see-through in the middle (the world beyond shows), thicker towards the rim; stepped, not smooth
        a = np.clip(0.12 + 0.6 * r ** 2 + (v > 0.8) * 0.18, 0, 1)
        a = np.floor(a * 5) / 5 * 230 * (r < 0.98)
        out.append(np.dstack([rgb, a]))
    save(np.concatenate(out, axis=0), "membrane.png")


def tunnel(n=64, seed=47):
    """A ring of light down the tunnel: streaks twisting round it, in a few flat shades."""
    rng = np.random.default_rng(seed)
    x, y, r, th = grid(n)
    twist = th + 1.6 * np.log(np.maximum(r, 0.05))
    streak = np.zeros_like(r)
    for freq, amp in ((11, 1.0), (23, 0.6), (37, 0.35)):
        streak += amp * (0.5 + 0.5 * np.sin(twist * freq + rng.uniform(0, 6.28)))
    streak = (streak / 1.95) ** 2
    wall = (r > 0.55) & (r < 0.98)
    v = np.clip(streak, 0, 0.999)
    rgb = pick(v, SWIRL)
    alpha = np.where(wall, np.floor((0.2 + streak * 0.8) * 4) / 4 * 210, 0)
    save(np.dstack([rgb, alpha]), "tunnel.png")


# ---------------------------------------------------------------------------- the worlds at the far end

def blocky_ridge(w, rng, base, amp, step, rough=0.5):
    """A blocky skyline: heights in whole steps, as terrain looks from afar in a world of blocks."""
    h, out = base, []
    for i in range(0, w, step):
        h += rng.normal(0, amp * rough)
        h = base + (h - base) * 0.85
        out += [int(round(h))] * step
    return out[:w]


def view(kind, n=64, seed=23):
    """A small pixel painting of a world, faded at its edge in steps: what shows at the tunnel's far end."""
    rng = np.random.default_rng(seed)
    low = n
    img = Image.new("RGB", (low, low))
    d = ImageDraw.Draw(img)

    def sky(top, bottom, bands=6):
        for yy in range(low):
            t = min(bands - 1, int(yy / low * bands)) / (bands - 1)
            d.line([(0, yy), (low, yy)], fill=tuple(int(top[i] + (bottom[i] - top[i]) * t) for i in range(3)))

    def ridges(layers, rough=0.5):
        for base, amp, step, col in layers:
            for xx, hh in enumerate(blocky_ridge(low, rng, base, amp, step, rough)):
                d.line([(xx, hh), (xx, low)], fill=col)

    if kind == "overworld_day":
        sky((92, 140, 255), (190, 214, 255))
        d.rectangle([42, 7, 48, 13], fill=(255, 248, 200))
        for i in range(4):
            cx, cy = int(rng.integers(0, low)), int(rng.integers(4, 20))
            d.rectangle([cx, cy, cx + 9, cy + 2], fill=(250, 252, 255))
            d.rectangle([cx + 2, cy - 1, cx + 6, cy], fill=(250, 252, 255))
        ridges([(34, 4, 4, (128, 152, 190)), (40, 3, 3, (84, 130, 96)), (47, 2, 2, (78, 150, 66))])
        for i in range(6):
            tx, ty = int(rng.integers(0, low)), int(rng.integers(42, 52))
            d.rectangle([tx, ty, tx, ty + 3], fill=(92, 66, 40))
            d.rectangle([tx - 2, ty - 3, tx + 2, ty], fill=(48, 104, 40))
        d.rectangle([0, 56, low, low], fill=(96, 162, 70))
    elif kind == "overworld_night":
        sky((8, 12, 40), (34, 44, 88))
        for i in range(30):
            d.point((int(rng.integers(0, low)), int(rng.integers(0, 34))), fill=(230, 230, 255) if rng.random() < 0.6 else (170, 170, 220))
        d.rectangle([15, 8, 20, 13], fill=(225, 230, 245))
        d.point((17, 10), fill=(190, 196, 214))
        ridges([(36, 4, 4, (30, 38, 70)), (42, 3, 3, (22, 40, 40)), (49, 2, 2, (20, 46, 30))])
        for i in range(4):
            wx, wy = int(rng.integers(5, low - 5)), int(rng.integers(46, 55))
            d.point((wx, wy), fill=(255, 200, 110))
        d.rectangle([0, 57, low, low], fill=(18, 40, 26))
    elif kind == "nether":
        sky((70, 12, 12), (176, 62, 30))
        ridges([(26, 6, 3, (110, 30, 26)), (37, 5, 2, (86, 20, 20)), (50, 3, 2, (64, 14, 16))], 0.8)
        for i in range(3):
            lx = int(rng.integers(5, low - 5))
            d.rectangle([lx, 20 + int(rng.integers(0, 10)), lx + 1, 59], fill=(255, 140, 30))
        for i in range(4):
            gx, gy = int(rng.integers(0, low)), int(rng.integers(2, 15))
            d.rectangle([gx, gy, gx + 1, gy + 1], fill=(250, 214, 120))
        d.rectangle([0, 59, low, low], fill=(255, 120, 26))
    else:  # the Seam
        sky((34, 16, 70), (130, 64, 150))
        for i in range(25):
            d.point((int(rng.integers(0, low)), int(rng.integers(0, 40))), fill=(255, 220, 255))
        for i in range(5):
            ix, iy, w = int(rng.integers(-5, low)), int(rng.integers(13, 46)), int(rng.integers(6, 15))
            dep = w // 2 + int(rng.integers(1, 4))
            d.line([(ix + w // 3, iy + 2), (ix + w // 3, min(low, iy + 23))], fill=(200, 186, 240))
            d.polygon([(ix, iy + 1), (ix + w, iy + 1), (ix + w // 2, iy + dep)], fill=(40, 26, 62))
            d.rectangle([ix, iy, ix + w, iy + 1], fill=(84, 110, 92))
        d.rectangle([0, 56, low, low], fill=(64, 40, 100))
    rgb = np.asarray(img, float)
    x, y, r, th = grid(n)
    # a haze over it in steps, brighter towards the middle where the tunnel opens onto it, faded at the rim
    hz = np.floor(np.clip(1 - r, 0, 1) ** 2 * 4) / 4 * 0.2
    rgb = rgb * (1 - hz[..., None]) + np.array([255, 230, 255]) * hz[..., None]
    alpha = np.floor(np.clip((1.0 - r) / 0.25, 0, 1) * 4) / 4 * 255
    save(np.dstack([rgb, alpha]), "view_" + kind + ".png")


def main():
    os.makedirs(OUT, exist_ok=True)
    for old in ("rim_a.png", "rim_b.png", "cracks.png", "haze.png", "shard.png"):
        path = os.path.join(OUT, old)
        if os.path.exists(path):
            os.remove(path)
    frame()
    edge()
    membrane()
    tunnel()
    for k in ("overworld_day", "overworld_night", "nether", "seam"):
        view(k)
    if "--preview" in sys.argv:
        preview(sys.argv[sys.argv.index("--preview") + 1])


def preview(path):
    """A rough picture of the rift as the renderer stacks it: view, tunnel, skin and lip, and a ring of frame blocks."""
    n = 384
    bg = Image.new("RGBA", (n * 2, n), (40, 24, 70, 255))

    def lay(img, size, at=(0, 0)):
        im = img.convert("RGBA").resize((size, size), Image.NEAREST)
        bg.alpha_composite(im, (at[0] + (n - size) // 2, at[1] + (n - size) // 2))

    lay(Image.open(os.path.join(OUT, "view_overworld_day.png")), 200)
    lay(Image.open(os.path.join(OUT, "tunnel.png")), 230)
    lay(Image.open(os.path.join(OUT, "tunnel.png")).rotate(40), 260)
    mem = Image.open(os.path.join(OUT, "membrane.png")).crop((0, 0, 64, 64))
    lay(mem, 270)
    lay(Image.open(os.path.join(OUT, "edge.png")), 280)
    face = Image.open(os.path.join(OUT, "frame.png"))
    cell = 24
    for i in range(-8, 8):
        for j in range(-8, 8):
            cx, cy = (i + 0.5) * cell, (j + 0.5) * cell
            d = math.hypot(cx, cy)
            if 140 <= d <= 140 + cell * (1.2 + 0.8 * math.sin(math.atan2(cy, cx) * 5) ** 2):
                tile = face.crop((16 * ((i * 7 + j * 3) % 4), 0, 16 * ((i * 7 + j * 3) % 4) + 16, 16)).resize((cell, cell), Image.NEAREST)
                bg.alpha_composite(tile.convert("RGBA"), (int(n / 2 + cx - cell / 2), int(n / 2 + cy - cell / 2)))
    for i, k in enumerate(("overworld_day", "overworld_night", "nether", "seam")):
        im = Image.open(os.path.join(OUT, "view_" + k + ".png")).resize((180, 180), Image.NEAREST)
        bg.alpha_composite(im.convert("RGBA"), (n + 10 + (i % 2) * 190, 10 + (i // 2) * 190))
    bg.save(path)


if __name__ == "__main__":
    main()
