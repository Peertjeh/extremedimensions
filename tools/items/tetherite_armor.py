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

# The armor layer follows vanilla's netherite armor pixel for pixel (3.49.5), in the metal's ramp (0 dark .. 6 light,
# and m between 2 and 3 for the one more tone vanilla's plates have), with a stud of the thread at the brow, over the
# heart and on the boots' straps
ARMOR = [
    "........22m64m22................................................",
    "........2m3553m2................................................",
    "........12m65m21................................................",
    "........12265221................................................",
    "........m3m54m3m................................................",
    "........m336533m................................................",
    "........13465431................................................",
    "........24455442................................................",
    "m33m34mmm456554mmm43m33m11145111................................",
    "45555665556b665556655554m3556543................................",
    "2m4m45542m3663m24554m4m22m4554mm................................",
    "2mm2m3mm2..65..2mm3m2mm22mm33mm2................................",
    "1111..2mm..53..mm2..111122mmmm22................................",
    ".......33......33.......11222211................................",
    ".......m45....54m.........1111..................................",
    ".......034....430...............................................",
    "........1111................................55m5................",
    "........1001................................6635................",
    "........1001................................3435................",
    "........1111................................55m5................",
    "................343m1m....m134331m4554m1564555m511115m55........",
    "................4543m1....1m4543m166661m463554m411114m45........",
    "................343m43m..m34343m445665444544432411114234........",
    "................m33m34544b43m33m354554533443m2151111512m........",
    "................2mm2m566665m2mm2m566665m5665545511115545........",
    "................343mm356653m343mm356653m121121211..11211........",
    "...212m22.......2mm2354554532mm234455443.2m.2.2......2..........",
    "m3mmm442m3mmm3mmm33mmm5665mmm33mmm3443mm........................",
    "343m4b4m343m343m2mm233mmmm332mm233mmmm33........................",
    "2m2146532m212m21....m356653m....m344443m........................",
    "1221255212211221.....235532......234432.........................",
    "1111122111111111......2mm2........2mm2..........................",
]


def armor():
    return layer(ARMOR)


def layer(rows):
    im = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    px = im.load()
    colours = {str(k): c for k, c in enumerate(M)}
    colours.update({"m": (56, 51, 69), "a": A[0], "b": A[1], "c": A[2]})
    for y, r in enumerate(rows):
        for x, ch in enumerate(r):
            if ch != ".":
                px[x, y] = colours[ch] + (255,)
    return im

# The leggings layer follows vanilla's netherite leggings pixel for pixel (3.49.4), in the metal's ramp (0 dark .. 6
# light), with a buckle of the thread at the front of the belt
LEGGINGS = [
    "................................................................",
    "................................................................",
    "................................................................",
    "................................................................",
    "................................................................",
    "................................................................",
    "................................................................",
    "................................................................",
    "................................................................",
    "................................................................",
    "................................................................",
    "................................................................",
    "................................................................",
    "................................................................",
    "................................................................",
    "................................................................",
    "....2222........................................................",
    "....2222........................................................",
    "....2222........................................................",
    "....2222........................................................",
    "1200521012001125................................................",
    "2221562122212265................................................",
    "4522256245224652................................................",
    "2454356424542653................................................",
    "2442335224422533................................................",
    "2242334222422433................................................",
    "1222122212221222................................................",
    "022102210221022166....6666....6666666666........................",
    "0110011001100110456666544566665423444432........................",
    "................4222234ba432222422344322........................",
    "................222222344322222212222221........................",
    "................110011000011001101111110........................",
]


def leggings():
    return layer(LEGGINGS)


def covered(trim, base):
    """A trim drawn only where the layer under it is: none of it floats over a bare leg."""
    out = trim.copy()
    tp, bp = out.load(), base.load()
    for y in range(base.height):
        for x in range(base.width):
            if bp[x, y][3] == 0:
                tp[x, y] = (0, 0, 0, 0)
    return out

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
        "trims/entity/humanoid/weaver": covered(weaver(False), armor()),
        "trims/entity/humanoid_leggings/weaver": covered(weaver(True), leggings()),
        "trims/entity/humanoid/fray": covered(fray(False), armor()),
        "trims/entity/humanoid_leggings/fray": covered(fray(True), leggings()),
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
