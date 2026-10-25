"""Tetherite worn, and the Seam's armor trims, drawn on the humanoid armor layout (64 x 32).

The armor: each face of the head, body, arms and legs filled with the dark violet metal, lit from above, edged, the
Tether's cyan run down its seams (a fuller down the breastplate, bands round the arms, a band round the helm, straps
on the boots). The leggings layer carries the belt and the legs.

The trims are drawn in the eight greys of vanilla's trim key (textures/palettes/trim_base, light to dark) and the
game colours them in whatever material they are put on with; the palettes for the Seam's two materials are written
here too, light to dark like vanilla's, and Tetherite's darker one for Tetherite armor trimmed in Tetherite.

    Weaver: a woven band across the chest and round the helm, warp lines down the legs, a knot at the brow.
    Fray: threads coming loose, hanging in ragged lines from the shoulders, the hems and the knees.

usage: python3 tools/items/tetherite_armor.py [preview.png]
"""
import os
import sys

from PIL import Image

ROOT = os.path.join(os.path.dirname(__file__), "..", "..")
TEX = os.path.join(ROOT, "src/client/resources/assets/extremedimensions/textures")

METAL = [(14, 9, 22), (36, 26, 54), (58, 45, 86), (88, 72, 124), (146, 132, 190)]
THREAD = [(14, 84, 96), (30, 160, 168), (70, 226, 214), (196, 255, 246)]

# vanilla's trim key (textures/palettes/trim_base), light to dark, and the Seam's palettes in the same order
KEY = [(241, 241, 241), (225, 225, 225), (208, 208, 208), (188, 188, 188), (165, 165, 165), (137, 137, 137), (99, 99, 99), (0, 0, 0)]
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


def metal(px, x0, y0, w, h, rows=None, light=True):
    """A face of plate: lit from the top, edged, the lower rows in shadow."""
    rows = rows or (0, h)
    for y in range(rows[0], rows[1]):
        for x in range(w):
            t = y / max(1, h - 1)
            c = 3 if t < 0.25 and light else 2 if t < 0.75 else 1
            if x == 0 or x == w - 1 or y == rows[0] or y == rows[1] - 1:
                c = 0 if (y == rows[1] - 1 or x == w - 1) else max(1, c - 1)
            if y == rows[0] + 1 and 0 < x < w - 1 and light:
                c = 4 if (x + y) % 5 == 1 else 3
            px[x0 + x, y0 + y] = METAL[c] + (255,)


def thread(px, x, y, k=2):
    px[x, y] = THREAD[k] + (255,)


def armor():
    """Layer one: the helm, the breastplate with its arms, and the boots."""
    im = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    px = im.load()
    # the helm: plates all round, a cyan band at the brow, the face left open
    for name, (x, y, w, h) in HEAD.items():
        metal(px, x, y, w, h)
        if name in ("front", "right", "left", "back"):
            for i in range(1, w - 1):
                thread(px, x + i, y + 3, 2 if i % 3 else 3)
        if name == "front":
            for yy in range(4, 8):
                for xx in range(2, 6):
                    px[x + xx, y + yy] = (0, 0, 0, 0)
    # the breastplate: a cyan fuller down the front, a thread at the collar
    for name, (x, y, w, h) in BODY.items():
        metal(px, x, y, w, h)
        if name == "front":
            for yy in range(1, h - 1):
                thread(px, x + 3, y + yy, 1)
                thread(px, x + 4, y + yy, 2 if yy % 4 else 3)
            for xx in range(1, w - 1):
                thread(px, x + xx, y + 1, 1)
        if name == "back":
            for yy in range(2, h - 2, 3):
                thread(px, x + 3, y + yy, 1)
                thread(px, x + 4, y + yy, 1)
    # the arms: pauldrons on top, two bands of thread
    for name, (x, y, w, h) in ARM.items():
        metal(px, x, y, w, h)
        if name not in ("top", "bottom"):
            for xx in range(w):
                thread(px, x + xx, y + 3, 1)
                thread(px, x + xx, y + 7, 2)
    # the boots: the lower rows of the legs, a strap across
    for name, (x, y, w, h) in LEG.items():
        if name in ("top",):
            continue
        if name == "bottom":
            metal(px, x, y, w, h, light=False)
            continue
        metal(px, x, y, w, h, rows=(h - 5, h))
        for xx in range(w):
            thread(px, x + xx, y + h - 3, 2 if xx % 2 else 1)
    return im


def leggings():
    """Layer two: the belt round the body and the legs."""
    im = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    px = im.load()
    for name, (x, y, w, h) in BODY.items():
        if name in ("top",):
            continue
        if name == "bottom":
            metal(px, x, y, w, h, light=False)
            continue
        metal(px, x, y, w, h, rows=(h - 5, h))
        for xx in range(w):
            thread(px, x + xx, y + h - 4, 3 if xx == w // 2 else 2)
    for name, (x, y, w, h) in LEG.items():
        if name in ("top", "bottom"):
            continue
        metal(px, x, y, w, h, rows=(0, h - 2))
        for yy in range(1, h - 3, 3):
            thread(px, x + (1 if name in ("front", "back") else 2), y + yy, 1)
        thread(px, x + 1, y + 6, 2)
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
