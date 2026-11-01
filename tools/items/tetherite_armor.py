"""Tetherite worn, and the Seam's armor trims, drawn on the humanoid armor layout (64 x 32).

The armor, in vanilla's manner: every face a bevelled plate (a lit top and left edge, the right and bottom edges in
shadow, a little grain, darker toward the foot), plates joined at seams (a dark line with a lit line under it) and held
with rivets; the Tether's cyan only as a stud here and there (at the brow, over the heart, on the belt buckle, at the
boot straps). The helm leaves the face open; the leggings layer carries the belt and the legs.

The trims are drawn in the eight greys of vanilla's trim key (textures/palettes/trim_base, light to dark) and the
game colours them in whatever material they are put on with; the palettes for the Seam's two materials are written
here too, light to dark like vanilla's, and Tetherite's darker one for Tetherite armor trimmed in Tetherite.

    Weaver: a woven band across the chest and round the helm, warp lines down the legs, a knot at the brow.
    Fray: threads coming loose, hanging in ragged lines from the shoulders, the hems and the knees.

usage: python3 tools/items/tetherite_armor.py [preview.png]
"""
import os
import random
import sys

from PIL import Image

ROOT = os.path.join(os.path.dirname(__file__), "..", "..")
TEX = os.path.join(ROOT, "src/client/resources/assets/extremedimensions/textures")

# the metal, dark to light (the same ramp as the items), and the thread
M = [(22, 19, 27), (35, 31, 43), (49, 44, 60), (64, 58, 78), (82, 75, 98), (106, 99, 124), (140, 133, 158)]
A = [(27, 86, 92), (42, 140, 142), (96, 200, 190)]

# vanilla's trim key (textures/palettes/trim_base), light to dark, as the game reads it (a grey PNG: read through a
# colour-managed decoder its greys come out brighter, 241, 225, 208..., and no longer match), and the Seam's palettes
# in the same order
KEY = [(224, 224, 224), (192, 192, 192), (160, 160, 160), (128, 128, 128), (96, 96, 96), (64, 64, 64), (32, 32, 32), (0, 0, 0)]
PALETTES = {
    "tetherite": [(178, 250, 240), (112, 220, 214), (66, 178, 184), (52, 128, 150), (66, 84, 128), (58, 58, 104), (44, 38, 78), (28, 22, 50)],
    "tetherite_darker": [(112, 220, 214), (66, 178, 184), (46, 130, 148), (40, 96, 120), (48, 58, 96), (42, 40, 78), (32, 26, 58), (20, 15, 38)],
    "seam_silk": [(255, 232, 255), (232, 186, 255), (212, 156, 252), (196, 132, 250), (170, 110, 236), (150, 90, 220), (120, 70, 186), (96, 52, 150)],
}


def faces(u, v, w, h, d):
    """The faces of a box laid out at (u, v): name to (x, y, width, height)."""
    return {
        "top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d),
        "right": (u, v + d, d, h), "front": (u + d, v + d, w, h),
        "left": (u + d + w, v + d, d, h), "back": (u + d + w + d, v + d, w, h),
    }


HEAD = faces(0, 0, 8, 8, 8)
BODY = faces(16, 16, 8, 12, 4)
ARM = faces(40, 16, 4, 12, 4)
LEG = faces(0, 16, 4, 12, 4)


def plate(px, x0, y0, w, h, rows=(0, None), seams=(), rnd=None, top_lit=True):
    """A face of plate: a lit top edge and left edge, the right and bottom edges in shadow, the body a little grained,
    darker toward the bottom; seams are a dark line with a lit line under it."""
    rnd = rnd or random.Random(x0 * 131 + y0)
    r0, r1 = rows[0], (rows[1] if rows[1] is not None else h)
    for y in range(r0, r1):
        for x in range(w):
            t = (y - r0) / max(1, r1 - r0 - 1)
            c = 4 if t < 0.2 else 3 if t < 0.6 else 2
            if rnd.random() < 0.18:
                c += rnd.choice((-1, 1))
            if y in seams:
                c = 1
            elif y - 1 in seams:
                c = 4
            if y == r0 and top_lit:
                c = 5
            if x == 0 and y > r0:
                c = max(c, 4) if t < 0.5 else 3
            if x == w - 1 or y == r1 - 1:
                c = 1
            px[x0 + x, y0 + y] = M[max(1, min(6, c))] + (255,)

def stud(px, x, y, k=2):
    px[x, y] = A[k] + (255,)

def rivet(px, x, y):
    px[x, y] = M[5] + (255,)
    px[x, y + 1] = M[1] + (255,)

def armor():
    im = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    px = im.load()
    for name, (x, y, w, h) in HEAD.items():
        plate(px, x, y, w, h, seams=(5,) if name not in ("top", "bottom") else ())
        if name == "front":
            # the face shows through the visor
            for yy in range(3, 7):
                for xx in range(1, 7):
                    if not (yy == 3 and xx in (1, 6)):
                        px[x + xx, y + yy] = (0, 0, 0, 0)
            for xx in range(1, 7):
                px[x + xx, y + 7] = M[2] + (255,)
            stud(px, x + 3, y + 1, 2)
            stud(px, x + 4, y + 1, 1)
        if name in ("right", "left"):
            rivet(px, x + 3, y + 2)
    for name, (x, y, w, h) in BODY.items():
        plate(px, x, y, w, h, seams=(4, 9) if name in ("front", "back") else (4,))
        if name == "front":
            stud(px, x + 3, y + 2, 1)
            stud(px, x + 4, y + 2, 2)
            rivet(px, x + 1, y + 6)
            rivet(px, x + 6, y + 6)
    for name, (x, y, w, h) in ARM.items():
        if name == "bottom":
            continue
        plate(px, x, y, w, h, rows=(0, 10) if name != "top" else (0, None), seams=(3,) if name != "top" else ())
    for name, (x, y, w, h) in LEG.items():
        if name == "top":
            continue
        if name == "bottom":
            plate(px, x, y, w, h, top_lit=False)
            continue
        plate(px, x, y, w, h, rows=(h - 5, h), seams=(h - 2,))
        if name == "front":
            stud(px, x + 1, y + h - 4, 1)
    return im

def leggings():
    im = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    px = im.load()
    for name, (x, y, w, h) in BODY.items():
        if name == "top":
            continue
        if name == "bottom":
            plate(px, x, y, w, h, top_lit=False)
            continue
        plate(px, x, y, w, h, rows=(h - 4, h))
        if name == "front":
            stud(px, x + 3, y + h - 3, 1)
            stud(px, x + 4, y + h - 3, 2)
    for name, (x, y, w, h) in LEG.items():
        if name in ("top", "bottom"):
            continue
        plate(px, x, y, w, h, rows=(0, h - 2), seams=(5,))
        if name == "front":
            rivet(px, x + 1, y + 6)
    return im

# --------------------------------------------------------------------------- trims (key levels 0 light .. 7 dark)

def put(px, x, y, level):
    px[x, y] = KEY[max(0, min(7, level))] + (255,)


def weaver(leggings_layer):
    im = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    px = im.load()
    if not leggings_layer:
        # round the helm: a woven band, and a knot at the brow
        for name, (x, y, w, h) in HEAD.items():
            if name in ("front", "right", "left", "back"):
                for i in range(w):
                    put(px, x + i, y + 2, 1 if i % 2 else 3)
                    put(px, x + i, y + 3, 3 if i % 2 else 1)
            if name == "front":
                for (dx, dy, lv) in ((3, 1, 0), (4, 1, 0), (3, 0, 2), (4, 0, 2)):
                    put(px, x + dx, y + dy, lv)
        # across the chest: three courses of weave; down the back: a warp line
        for name, (x, y, w, h) in BODY.items():
            if name == "front":
                for yy in (2, 3, 4):
                    for xx in range(w):
                        put(px, x + xx, y + yy, 1 if (xx + yy) % 2 else 4)
                for yy in range(5, h - 1):
                    put(px, x + 1, y + yy, 2)
                    put(px, x + w - 2, y + yy, 2)
            if name == "back":
                for yy in range(1, h - 1):
                    put(px, x + 3, y + yy, 2 if yy % 2 else 4)
                    put(px, x + 4, y + yy, 4 if yy % 2 else 2)
        for name, (x, y, w, h) in ARM.items():
            if name not in ("top", "bottom"):
                for xx in range(w):
                    put(px, x + xx, y + 1, 1 if xx % 2 else 3)
            if name == "top":
                for xx in range(w):
                    put(px, x + xx, y + 1, 2)
        for name, (x, y, w, h) in LEG.items():
            if name not in ("top", "bottom"):
                for xx in range(w):
                    put(px, x + xx, y + h - 4, 1 if xx % 2 else 3)
    else:
        for name, (x, y, w, h) in BODY.items():
            if name not in ("top", "bottom"):
                for xx in range(w):
                    put(px, x + xx, y + h - 3, 1 if xx % 2 else 3)
        for name, (x, y, w, h) in LEG.items():
            if name not in ("top", "bottom"):
                for yy in range(0, h - 2):
                    put(px, x + 1, y + yy, 2 if yy % 2 else 4)
                for xx in range(w):
                    put(px, x + xx, y + 5, 1 if xx % 2 else 3)
    return im


def fray(leggings_layer):
    im = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    px = im.load()

    def loose(x, y, length, step):
        # a thread hanging, wandering a pixel now and then, darkening as it goes
        cx = x
        for i in range(length):
            put(px, cx, y + i, 1 + i * 6 // max(1, length))
            if i % step == step - 1:
                cx += 1 if (x + i) % 2 else -1

    if not leggings_layer:
        for name, (x, y, w, h) in HEAD.items():
            if name in ("right", "left", "back"):
                for xx in range(0, w, 3):
                    loose(x + xx, y + 5, 3, 2)
            if name == "front":
                loose(x + 1, y + 1, 3, 2)
                loose(x + 6, y + 1, 3, 2)
        for name, (x, y, w, h) in BODY.items():
            if name in ("front", "back"):
                for xx in range(w):
                    put(px, x + xx, y + 1, 2)
                for xx in (1, 3, 6):
                    loose(x + xx, y + 2, 5 + (xx % 3), 2)
            if name in ("right", "left"):
                loose(x + 1, y + 1, 6, 3)
        for name, (x, y, w, h) in ARM.items():
            if name in ("front", "back", "right", "left"):
                for xx in range(w):
                    put(px, x + xx, y, 2)
                loose(x + 1, y + 1, 5, 2)
        for name, (x, y, w, h) in LEG.items():
            if name in ("front", "back", "right", "left"):
                loose(x + 1, y + h - 5, 4, 2)
    else:
        for name, (x, y, w, h) in BODY.items():
            if name in ("front", "back", "right", "left"):
                for xx in range(0, w, 2):
                    loose(x + xx, y + h - 4, 3, 2)
        for name, (x, y, w, h) in LEG.items():
            if name in ("front", "back", "right", "left"):
                for xx in range(w):
                    put(px, x + xx, y + 5, 2)
                loose(x + 1, y + 6, 5, 2)
                loose(x + 2, y + 1, 3, 2)
    return im


def palette(colors):
    im = Image.new("RGBA", (len(colors), 1))
    for i, c in enumerate(colors):
        im.putpixel((i, 0), c + (255,))
    return im


def main():
    files = {
        "entity/equipment/humanoid/tetherite": armor(),
        "entity/equipment/humanoid_leggings/tetherite": leggings(),
        "trims/entity/humanoid/weaver": weaver(False),
        "trims/entity/humanoid_leggings/weaver": weaver(True),
        "trims/entity/humanoid/fray": fray(False),
        "trims/entity/humanoid_leggings/fray": fray(True),
    }
    for name, colors in PALETTES.items():
        files["palettes/trim/" + name] = palette(colors)
    if len(sys.argv) > 1:
        S = 6
        shown = [im for k, im in files.items() if not k.startswith("palettes")]
        sheet = Image.new("RGBA", (64 * S * 2 + 12, 32 * S * 3 + 12), (70, 70, 76, 255))
        for i, im in enumerate(shown):
            big = im.resize((64 * S, 32 * S), Image.NEAREST)
            sheet.paste(big, ((i % 2) * (64 * S + 12), (i // 2) * (32 * S + 6)), big)
        sheet.save(sys.argv[1])
        return
    for name, im in files.items():
        path = os.path.join(TEX, name + ".png")
        os.makedirs(os.path.dirname(path), exist_ok=True)
        im.save(path)


if __name__ == "__main__":
    main()
