"""Boss textures for The Elders, recoloured from the Keeper (same 128x256 UV layout as every Lurker-bodied mob).

Usage: python3 tools/bosses/recolor.py <textures/entity dir>
Writes matriarch, ferryman and weaver (.png and _glow.png) next to keeper.png.
"""
import colorsys
import random
import sys

from PIL import Image

DIR = sys.argv[1].rstrip('/') + '/'
base = Image.open(DIR + 'keeper.png').convert('RGBA')
glow = Image.open(DIR + 'keeper_glow.png').convert('RGBA')
W, H = base.size


def is_mask(c):
    r, g, b, a = c
    if a == 0:
        return False
    h, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
    return v > 0.45 and 0.04 < h < 0.2 and s < 0.35


def lum(c):
    return (0.299 * c[0] + 0.587 * c[1] + 0.114 * c[2]) / 255


def ramp(stops, t):
    t = max(0.0, min(1.0, t))
    for i in range(len(stops) - 1):
        a, b = stops[i], stops[i + 1]
        if a[0] <= t <= b[0]:
            k = (t - a[0]) / max(1e-6, b[0] - a[0])
            return tuple(int(a[1][j] + (b[1][j] - a[1][j]) * k) for j in range(3))
    return stops[-1][1]


def make(name, cloak, mask, glow_rgb, lines, seed):
    rnd = random.Random(seed)
    out = Image.new('RGBA', (W, H))
    g_out = Image.new('RGBA', (W, H))
    src = base.load()
    o = out.load()
    for y in range(H):
        for x in range(W):
            c = src[x, y]
            if c[3] == 0:
                continue
            t = lum(c)
            rgb = ramp(mask, (t - 0.45) / 0.45) if is_mask(c) else ramp(cloak, t * 2.2)
            o[x, y] = rgb + (255,)
    # The Keeper's eyes, in the new light
    gsrc = glow.load()
    go = g_out.load()
    for y in range(H):
        for x in range(W):
            c = gsrc[x, y]
            if c[3] > 0:
                go[x, y] = glow_rgb + (c[3],)
    # Lines that glow across the cloak: veins, threads or the river's ripples
    kind, count, colour = lines
    for _ in range(count):
        x, y = rnd.randrange(W), rnd.randrange(H)
        length = rnd.randint(4, 14)
        dx, dy = rnd.choice([(1, 0), (0, 1), (1, 1), (1, -1)]) if kind != 'thread' else rnd.choice([(1, 1), (1, -1)])
        for i in range(length):
            if not (0 <= x < W and 0 <= y < H) or src[x, y][3] == 0 or is_mask(src[x, y]):
                break
            a = 200 if kind == 'thread' else 150 + rnd.randint(0, 80)
            go[x, y] = colour + (a,)
            if kind == 'vein' and rnd.random() < 0.3:
                dx, dy = rnd.choice([(1, 0), (0, 1), (1, 1), (-1, 1)])
            x += dx
            y += dy
    out.save(DIR + name + '.png')
    g_out.save(DIR + name + '_glow.png')


# The Matriarch: oldest of the Keepers, her cloak gone the colour of old blood, her mask bone-white and cracked
make('matriarch',
     cloak=[(0.0, (14, 6, 8)), (0.5, (52, 14, 20)), (1.0, (120, 46, 52))],
     mask=[(0.0, (196, 188, 172)), (1.0, (246, 240, 228))],
     glow_rgb=(255, 70, 50), lines=('vein', 70, (230, 40, 40)), seed=11)
# The Ferryman: river-black and green, a mask of tarnished gold, eyes like coins
make('ferryman',
     cloak=[(0.0, (6, 14, 12)), (0.5, (18, 44, 38)), (1.0, (70, 110, 92))],
     mask=[(0.0, (120, 92, 40)), (1.0, (214, 176, 92))],
     glow_rgb=(255, 205, 90), lines=('ripple', 40, (90, 220, 170)), seed=23)
# The Last Weaver: silver and violet, every seam of it a glowing thread
make('weaver',
     cloak=[(0.0, (20, 16, 30)), (0.5, (70, 60, 96)), (1.0, (176, 170, 204))],
     mask=[(0.0, (190, 184, 214)), (1.0, (236, 232, 250))],
     glow_rgb=(120, 240, 230), lines=('thread', 120, (140, 250, 240)), seed=37)
print('ok')
