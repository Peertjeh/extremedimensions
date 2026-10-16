"""Hush masonry: the Seam's stone family, drawn procedurally as 16 px pixel art.

Every texture starts as a field of lightness (0..1) shaped like the stone it shows (mottled rock, cobbles, coursed
bricks, tiles, flutes), lit from the top left, then coloured through a short ramp of the material's tones and snapped
to a few steps of lightness, so each block keeps a small, coherent palette the way vanilla stone does.

Built to sit well cut in half: brick courses are 4 px with their mortar in the bottom row of each course, cobbles and
tiles break along rows 7 and 15, so a slab, a stair or a wall shows whole courses and whole stones at its edges, and
the halves of a block, its slabs and the blocks above and below all meet on a joint. Every texture tiles seamlessly.

The tones climb in a ladder from the darkest foot of a building to its palest crown (cobbled, stone, bricks, polished,
tiles, pale bricks, polished pale); the Seam's builders pick from it by height (see SeamArchitect's gradients).

usage: python3 tools/hush/build.py [preview.png]
"""
import math
import os
import sys

import numpy as np
from PIL import Image

ROOT = os.path.join(os.path.dirname(__file__), "..", "..")
TEX = os.path.join(ROOT, "src/client/resources/assets/extremedimensions/textures/block")
N = 16

# ----------------------------------------------------------------------------- colour ramps

# Each ramp runs dark to light; the lightness field picks along it. Violet-grey for the hush stone, lilac-grey for the
# pale stone bleached by the Seam's light, a cool sage moss, and the silk's glow.
HUSH = [(14, 12, 20), (26, 23, 35), (39, 35, 52), (55, 50, 72), (74, 68, 95), (98, 91, 122)]
PALE = [(58, 54, 72), (84, 79, 100), (108, 102, 127), (134, 128, 153), (160, 154, 178), (190, 184, 205)]
MOSS = [(36, 46, 34), (54, 68, 46), (74, 92, 58), (96, 116, 72), (124, 142, 90)]
SILK = [(96, 52, 150), (150, 90, 220), (196, 132, 250), (232, 186, 255), (255, 232, 255)]


def ramp(colors, t):
    """Colours of a lightness field through a ramp (t 0..1, any shape)."""
    t = np.clip(t, 0, 1) * (len(colors) - 1)
    i = np.clip(np.floor(t).astype(int), 0, len(colors) - 2)
    f = (t - i)[..., None]
    c = np.array(colors, dtype=np.float64)
    return c[i] * (1 - f) + c[i + 1] * f


def snap(field, steps):
    """Lightness snapped to a few steps, so the palette stays small and the pixels read as pixel art."""
    return np.round(np.clip(field, 0, 1) * steps) / steps


# ----------------------------------------------------------------------------- fields

YS, XS = np.mgrid[0:N, 0:N]


def noise(rng, cells, octaves=1):
    """Smooth value noise in 0..1 that tiles across the 16 px edges (a wrapped lattice of `cells` per side)."""
    out = np.zeros((N, N))
    amp, total = 1.0, 0.0
    c = cells
    for _ in range(octaves):
        lat = rng.random((c, c))
        gx = XS * c / N
        gy = YS * c / N
        x0 = np.floor(gx).astype(int)
        y0 = np.floor(gy).astype(int)
        fx = gx - x0
        fy = gy - y0
        fx = fx * fx * (3 - 2 * fx)
        fy = fy * fy * (3 - 2 * fy)
        x1, y1 = (x0 + 1) % c, (y0 + 1) % c
        x0, y0 = x0 % c, y0 % c
        v = (lat[y0, x0] * (1 - fx) * (1 - fy) + lat[y0, x1] * fx * (1 - fy)
             + lat[y1, x0] * (1 - fx) * fy + lat[y1, x1] * fx * fy)
        out += v * amp
        total += amp
        amp *= 0.5
        c *= 2
    return out / total


def specks(rng, count, field, delta, size=1):
    """Scatters small darker or lighter flecks (a pixel, or a little cluster) over a field."""
    for _ in range(count):
        x, y = rng.integers(0, N), rng.integers(0, N)
        field[y, x] += delta
        if size > 1:
            for dx, dy in ((1, 0), (0, 1), (-1, 0), (0, -1))[: size - 1]:
                field[(y + dy) % N, (x + dx) % N] += delta * 0.6
    return field


def stone_field(rng):
    """Quiet natural stone: a soft mottle, faint strata, a few dark pits and pale flecks."""
    f = 0.5 + 0.30 * (noise(rng, 4, 2) - 0.5) + 0.16 * (noise(rng, 8) - 0.5)
    f += 0.05 * np.sin((YS + 2.0 * noise(rng, 2)) * math.pi / 4)
    specks(rng, 7, f, -0.22, 2)
    specks(rng, 6, f, 0.16)
    return f


def polished_field(rng):
    """Polished stone: smooth, a soft sheen falling from the top left, the faintest grain; cut anywhere, it still fits."""
    f = 0.52 + 0.12 * (noise(rng, 2, 2) - 0.5) + 0.07 * (noise(rng, 8) - 0.5)
    specks(rng, 3, f, -0.10)
    specks(rng, 3, f, 0.08)
    return f


def courses(height=4, offset=4, width=8):
    """Brick layout: per pixel, which brick it belongs to and where within it; mortar in the last row and column."""
    course = YS // height
    row = YS % height
    shift = (course % 2) * offset
    col = (XS + shift) % N
    brick = course * 4 + col // width
    within = col % width
    mortar = (row == height - 1) | (within == width - 1)
    return brick, row, within, mortar


def brick_field(rng, chips=3):
    """Coursed bricks: each brick its own tone, lit along its top edge and shaded along its bottom, with a grain."""
    brick, row, within, mortar = courses()
    tones = rng.normal(0, 0.075, 64)
    f = 0.56 + tones[brick] + 0.09 * (noise(rng, 8) - 0.5) + 0.06 * (noise(rng, 16) - 0.5)
    f += np.where(row == 0, 0.09, 0) + np.where(row == 2, -0.07, 0)
    f += np.where(within == 0, 0.04, 0) + np.where(within == 6, -0.05, 0)
    f = np.where(mortar, 0.24 + 0.05 * noise(rng, 8), f)
    # where a course's mortar meets a joint, a darker pit
    f = np.where((row == 3) & (within == 7), 0.18, f)
    # A few chipped corners, the mortar showing through
    for _ in range(chips):
        b = rng.integers(0, 16)
        ys, xs = np.nonzero((brick == b) & ~mortar)
        if len(ys):
            k = rng.integers(0, len(ys))
            f[ys[k], xs[k]] = 0.30
    return f, mortar, brick


def tile_field(rng):
    """Square tiles, four to a block: grout along rows and columns 7 and 15, each tile bevelled and of its own tone."""
    ty, tx = YS // 8, XS // 8
    tile = ty * 2 + tx
    ly, lx = YS % 8, XS % 8
    grout = (ly == 7) | (lx == 7)
    tones = rng.normal(0, 0.05, 4)
    f = 0.56 + tones[tile] + 0.10 * (noise(rng, 8) - 0.5)
    f += np.where((ly == 0) | (lx == 0), 0.10, 0) + np.where((ly == 6) | (lx == 6), -0.08, 0)
    f += 0.04 * np.cos((lx - ly) * math.pi / 8)
    f = np.where(grout, 0.18 + 0.04 * noise(rng, 8), f)
    return f, grout


def cobble_field(rng):
    """Cobbles in two bands, each ending in a joint on rows 7 and 15: rounded stones of uneven widths set side by side,
    domed and lit from the top left, with dark grit where their corners fall away."""
    f = np.full((N, N), 0.20) + 0.06 * noise(rng, 16)
    joint = np.ones((N, N), dtype=bool)
    counts = [(3, 2), (2, 3), (3, 3)][int(rng.integers(0, 3))]
    for band, n in zip((0, 8), counts):
        w = rng.uniform(0.75, 1.3, n)
        edges = np.concatenate([[0], np.cumsum(w / w.sum() * 16)])
        x0 = rng.random() * 16
        for i in range(n):
            cxs = x0 + (edges[i] + edges[i + 1]) / 2
            rx = (edges[i + 1] - edges[i]) / 2 - 0.35
            cy = band + 3.5 + rng.normal(0, 0.25)
            ry = 3.25 + rng.normal(0, 0.2)
            tone = rng.normal(0, 0.06)
            for wx in (-16, 0, 16):
                dx = (XS + 0.5 - (cxs + wx)) / rx
                dy = (YS + 0.5 - cy) / ry
                # a rounded rectangle (superellipse): flat-sided, soft-cornered
                e = (np.abs(dx) ** 2.6 + np.abs(dy) ** 2.6) ** (1 / 2.6)
                inside = (e <= 1.0) & (YS // 8 == band // 8)
                light = np.clip(-(dx * 0.7 + dy) * 0.5, -1, 1)
                dome = 0.56 + tone + 0.16 * light - 0.14 * np.clip(e - 0.6, 0, 1) * 2.5
                f = np.where(inside, dome + 0.06 * (noise(rng, 8) - 0.5), f)
                joint = joint & ~inside
    return f, joint


# ----------------------------------------------------------------------------- textures

def colour(field, colors, lift=0.0, steps=10):
    return ramp(colors, snap(field + lift, steps))


def save(name, rgb):
    a = np.clip(np.round(rgb), 0, 255).astype(np.uint8)
    img = Image.fromarray(a, "RGB").convert("RGBA")
    img.save(os.path.join(TEX, name + ".png"))
    return img


def cracks(rng, f, mortar, count):
    """Cracks wander across a few bricks: dark one-pixel paths with a lighter lip above them, and a few bricks sunk
    back where a piece has broken away."""
    for _ in range(count):
        x, y = int(rng.integers(0, N)), int(rng.integers(0, N))
        dx = 1 if rng.random() < 0.5 else -1
        for _ in range(int(rng.integers(7, 13))):
            if not mortar[y % N, x % N]:
                f[y % N, x % N] = 0.04
                up = (y - 1) % N
                if not mortar[up, x % N]:
                    f[up, x % N] = min(f[up, x % N] + 0.08, 1.0)
            if rng.random() < 0.5:
                y += 1
            else:
                x += dx
    brick, row, within, _ = courses()
    for b in rng.choice(64, 2, replace=False):
        sunk = (brick == b) & ~mortar
        f[sunk] -= 0.12
    return f


def build():
    out = {}
    rng = np.random.default_rng

    # The natural stone, the cobbles it breaks into, and the polished face
    out["hush_stone"] = save("hush_stone", colour(stone_field(rng(11)), HUSH, -0.04))
    f, joint = cobble_field(rng(23))
    out["cobbled_hush_stone"] = save("cobbled_hush_stone", colour(f, HUSH, -0.08))
    out["polished_hush_stone"] = save("polished_hush_stone", colour(polished_field(rng(31)), HUSH, 0.08, 12))

    # Bricks, and what time does to them
    f, mortar, brick = brick_field(rng(41))
    out["hush_bricks"] = save("hush_bricks", colour(f, HUSH, 0.0))
    fc, mc, _ = brick_field(rng(41), chips=6)
    fc = cracks(rng(43), fc, mc, 4)
    out["cracked_hush_bricks"] = save("cracked_hush_bricks", colour(fc, HUSH, -0.04))

    fm, mm, _ = brick_field(rng(47))
    base = colour(fm, HUSH, -0.01)
    g = rng(53)
    grow = 0.65 * noise(g, 4, 2) + 0.35 * noise(g, 8) + 0.10 * mm - 0.22 * (YS / N)
    moss = grow > 0.56
    # drips: moss creeping down a pixel or two below a patch
    drip = np.roll(moss, 1, axis=0) & (g.random((N, N)) < 0.35)
    drip2 = np.roll(drip, 1, axis=0) & (g.random((N, N)) < 0.4)
    moss = moss | drip | drip2
    # lit on top, shaded where it hangs over the stone
    mf = 0.50 + 0.22 * (noise(g, 8) - 0.5) + 0.16 * ~np.roll(moss, 1, axis=0) - 0.14 * ~np.roll(moss, -1, axis=0)
    base = np.where(moss[..., None], colour(mf, MOSS, 0.0, 8), base)
    # the stone just under a patch lies in its shadow
    shade = np.roll(moss, 1, axis=0) & ~moss
    base = np.where(shade[..., None], base * 0.8, base)
    out["mossy_hush_bricks"] = save("mossy_hush_bricks", base)

    # Silk in the mortar: the bricks of the Weavers' halls, warm with light
    fs, ms, _ = brick_field(rng(59))
    base = colour(fs, HUSH, -0.03)
    gs = rng(61)
    brick, row, within, _ = courses()
    # silk runs along whole courses and down some joints, as if threaded through the wall
    course_on = gs.random(4) < 0.6
    joint_on = gs.random(64) < 0.45
    silk = ms & (((row == 3) & course_on[YS // 4]) | ((within == 7) & joint_on[brick]))
    glow = 0.45 + 0.30 * noise(gs, 8)
    glow = np.where((row == 3) & (within == 7), glow + 0.35, glow)
    base = np.where(silk[..., None], ramp(SILK, snap(glow, 8)), base)
    # the glow spills a little onto the brick faces beside the silk
    near = (np.roll(silk, 1, 0) | np.roll(silk, -1, 0) | np.roll(silk, 1, 1) | np.roll(silk, -1, 1)) & ~ms
    base = np.where(near[..., None], base * 0.7 + np.array(SILK[0]) * 0.3, base)
    out["silked_hush_bricks"] = save("silked_hush_bricks", base)

    # Tiles, a shade lighter: the middle of the ladder
    ft, grout = tile_field(rng(67))
    out["hush_tiles"] = save("hush_tiles", colour(ft, HUSH, 0.30))

    # The pale stone, bleached by the Seam's light: the crowns of its buildings
    fp, _, _ = brick_field(rng(71))
    out["pale_hush_bricks"] = save("pale_hush_bricks", colour(fp, PALE, -0.10))
    fpp = polished_field(rng(73))
    out["polished_pale_hush_stone"] = save("polished_pale_hush_stone", colour(0.52 + (fpp - 0.52) * 0.85, PALE, 0.0, 14))

    # The pillar: flutes running up its sides, rings on its ends
    fl = 0.55 + 0.10 * (noise(rng(79), 8) - 0.5)
    flute = XS % 4
    fl += np.select([flute == 0, flute == 1, flute == 2, flute == 3], [0.14, 0.04, -0.06, -0.20])
    fl = np.where((XS == 0) | (XS == 15), fl - 0.08, fl)
    out["hush_pillar"] = save("hush_pillar", colour(fl, HUSH, 0.04))
    r = np.hypot(XS - 7.5, YS - 7.5)
    ft2 = 0.58 + 0.10 * np.cos(r * math.pi / 2.2) + 0.08 * (noise(rng(83), 8) - 0.5)
    edge = (XS == 0) | (YS == 0) | (XS == 15) | (YS == 15)
    ft2 = np.where(edge, 0.30, ft2)
    ft2 = np.where((XS == 1) | (YS == 1), ft2 + 0.08, ft2)
    out["hush_pillar_top"] = save("hush_pillar_top", colour(ft2, HUSH, 0.04))

    # Chiseled: a framed panel carved with the Seam, a rift standing open in a diamond
    fch = polished_field(rng(89)) * 0.9 + 0.05
    frame = (XS <= 1) | (YS <= 1) | (XS >= 14) | (YS >= 14)
    fch = np.where(frame, 0.50, fch)
    fch = np.where((XS == 0) | (YS == 0), 0.66, fch)
    fch = np.where((XS == 15) | (YS == 15), 0.26, fch)
    fch = np.where(((XS == 2) | (YS == 2)) & ~frame, fch - 0.14, fch)
    m = np.abs(XS - 7.5) + np.abs(YS - 7.5)
    fch = np.where((m >= 5) & (m < 6), 0.24, fch)
    fch = np.where((m >= 6) & (m < 7), 0.70, fch)
    rift = (np.abs(XS - 7.5) < 1) & (np.abs(YS - 7.5) < 4.5 - np.abs(XS - 7.5))
    fch = np.where(rift, 0.12, fch)
    fch = np.where((m < 2.2) & ~rift, 0.62, fch)
    out["chiseled_hush_bricks"] = save("chiseled_hush_bricks", colour(fch, HUSH, 0.0))

    # The vein: hush stone threaded with glowing silk
    fv = stone_field(rng(97))
    base = colour(fv, HUSH, -0.06)
    a = rng(101)
    starts = [a.random() * 16, None]
    starts[1] = (starts[0] + 7 + a.random() * 2) % 16
    for k, x in enumerate(starts):
        slope = 0.45 + a.random() * 0.25
        rows = range(N) if k == 0 else range(int(a.integers(3, 6)), int(a.integers(10, 13)))
        for y in rows:
            xx = int(round(x + slope * y + 0.8 * math.sin(y * 0.7 + k * 2))) % N
            core = SILK[4] if (y + k) % 5 == 0 else SILK[3]
            base[y, xx] = core
            for side in (-1, 1):
                q = (xx + side) % N
                base[y, q] = base[y, q] * 0.45 + np.array(SILK[1]) * 0.55
            q = (xx + 2) % N
            base[y, q] = base[y, q] * 0.75 + np.array(SILK[0]) * 0.25
    out["seam_vein"] = save("seam_vein", base)
    return out


# ----------------------------------------------------------------------------- preview

def iso_preview(tex, path):
    """Each material as a little build: a stack of blocks, a slab on top of one, a stair and a wall beside them, all
    in an isometric view with Minecraft's face shading, so the joints between blocks, halves and steps can be judged."""
    families = [
        ("cobbled_hush_stone", "cobbled_hush_stone"), ("hush_stone", "hush_stone"),
        ("hush_bricks", "hush_bricks"), ("mossy_hush_bricks", "mossy_hush_bricks"),
        ("polished_hush_stone", "polished_hush_stone"), ("hush_tiles", "hush_tiles"),
        ("pale_hush_bricks", "pale_hush_bricks"), ("polished_pale_hush_stone", "polished_pale_hush_stone"),
    ]
    singles = ["cracked_hush_bricks", "silked_hush_bricks", "chiseled_hush_bricks", "hush_pillar", "seam_vein"]
    S = 48  # pixels per block
    cw, ch = int(S * 4.6 * 1.732 / 2) + 40, int(S * 4.2)
    cols = 4
    rows = math.ceil(len(families) / cols) + 1
    sheet = Image.new("RGBA", (cw * cols, ch * rows), (24, 22, 30, 255))

    def proj(x, y, z, ox, oy):
        return (ox + (x - z) * S * 0.866, oy - y * S + (x + z) * S * 0.5)

    def face(img, texture, quad, uv, shade, ox, oy):
        # quad: 4 corners in 3D (top-left, bottom-left, bottom-right, top-right of the texture region)
        u0, v0, u1, v1 = uv
        src = texture.crop((int(u0), int(v0), int(u1), int(v1))).resize((int(u1 - u0) * 8, int(v1 - v0) * 8), Image.NEAREST)
        pts = [proj(*p, ox, oy) for p in quad]
        minx = min(p[0] for p in pts)
        miny = min(p[1] for p in pts)
        maxx = max(p[0] for p in pts)
        maxy = max(p[1] for p in pts)
        w, h = int(math.ceil(maxx - minx)) + 1, int(math.ceil(maxy - miny)) + 1
        # PIL QUAD maps output corners (NW, SW, SE, NE) to source corners; solve by inverse: use perspective coefficients
        coeffs = find_coeffs([(p[0] - minx, p[1] - miny) for p in pts], [(0, 0), (0, src.height), (src.width, src.height), (src.width, 0)])
        warped = src.transform((w, h), Image.PERSPECTIVE, coeffs, Image.NEAREST)
        arr = np.array(warped).astype(np.float64)
        arr[..., :3] *= shade
        warped = Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8), "RGBA")
        img.alpha_composite(warped, (int(round(minx)), int(round(miny))))

    def box(img, top_tex, side_tex, b, origin, ox, oy):
        (x0, y0, z0), (x1, y1, z1) = b
        bx, by, bz = origin
        X0, X1, Y0, Y1, Z0, Z1 = bx + x0, bx + x1, by + y0, by + y1, bz + z0, bz + z1
        # top (+y)
        face(img, top_tex, [(X0, Y1, Z0), (X0, Y1, Z1), (X1, Y1, Z1), (X1, Y1, Z0)], (x0 * 16, z0 * 16, x1 * 16, z1 * 16), 1.0, ox, oy)
        # south (+z): u = x, v = 1 - y
        face(img, side_tex, [(X0, Y1, Z1), (X0, Y0, Z1), (X1, Y0, Z1), (X1, Y1, Z1)], (x0 * 16, (1 - y1) * 16, x1 * 16, (1 - y0) * 16), 0.8, ox, oy)
        # east (+x): u = 1 - z, v = 1 - y
        face(img, side_tex, [(X1, Y1, Z1), (X1, Y0, Z1), (X1, Y0, Z0), (X1, Y1, Z0)], ((1 - z1) * 16, (1 - y1) * 16, (1 - z0) * 16, (1 - y0) * 16), 0.6, ox, oy)

    def scene(img, top_tex, side_tex, ox, oy, shapes=True):
        F = [((0, 0, 0), (1, 1, 1))]
        items = []
        # back row first: a two-high stack, a block beside it with a slab on top, then a stair, then a wall
        items.append(((0, 0, 0), F))
        items.append(((0, 1, 0), F))
        items.append(((1, 0, 0), F))
        if shapes:
            items.append(((1, 1, 0), [((0, 0, 0), (1, 0.5, 1))]))
            items.append(((2, 0, 0), [((0, 0, 0), (1, 0.5, 1)), ((0.5, 0.5, 0), (1, 1, 1))]))
            items.append(((3, 0, 0), [((0.25, 0, 0.25), (0.75, 1, 0.75)), ((0.3125, 0, 0.75), (0.6875, 0.875, 1))]))
            items.append(((1, 0, 1), [((0, 0.5, 0), (1, 1, 1))]))
        else:
            items.append(((1, 1, 0), F))
            items.append(((2, 0, 0), F))
        for origin, boxes in sorted(items, key=lambda it: (it[0][0] + it[0][2], it[0][1])):
            for b in boxes:
                box(img, top_tex, side_tex, b, origin, ox, oy)

    for i, (name, _) in enumerate(families):
        cx, cy = (i % cols) * cw, (i // cols) * ch
        scene(sheet, tex[name], tex[name], cx + int(S * 1.6), cy + int(S * 2.4))
    for i, name in enumerate(singles):
        cx, cy = (i % cols) * cw, (len(families) // cols) * ch
        if i >= cols:
            continue
        top = tex["hush_pillar_top"] if name == "hush_pillar" else tex[name]
        scene(sheet, top, tex[name], cx + int(S * 1.6), cy + int(S * 2.4), shapes=False)
    sheet.save(path)


def find_coeffs(pa, pb):
    """Perspective coefficients mapping output points pa onto source points pb (for Image.transform)."""
    matrix = []
    for p1, p2 in zip(pa, pb):
        matrix.append([p1[0], p1[1], 1, 0, 0, 0, -p2[0] * p1[0], -p2[0] * p1[1]])
        matrix.append([0, 0, 0, p1[0], p1[1], 1, -p2[1] * p1[0], -p2[1] * p1[1]])
    A = np.array(matrix, dtype=np.float64)
    B = np.array(pb, dtype=np.float64).reshape(8)
    res = np.linalg.lstsq(A, B, rcond=None)[0]
    return res.tolist()


if __name__ == "__main__":
    textures = build()
    for name, img in sorted(textures.items()):
        a = np.array(img)[..., :3].astype(np.float64)
        lum = (0.2126 * a[..., 0] + 0.7152 * a[..., 1] + 0.0722 * a[..., 2]).mean()
        print(f"{name:28s} mean luminance {lum:6.1f}  colours {len(set(map(tuple, a.reshape(-1, 3))))}")
    if len(sys.argv) > 1:
        iso_preview(textures, sys.argv[1])
