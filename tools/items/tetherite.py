"""Tetherite, drawn as 16 px pixel art: the metal the Fray keeps of every Tether it swallowed.

A deep violet-black metal, darker than netherite and cooler, with the Tether's cyan thread run through it: along the
fuller of a blade, round the eye of a pick, in the seams of a plate. Tools are laid out the way vanilla lays its own
(handle at the lower left, head at the upper right), each built from a few shapes along the diagonal, lit from the top
left, edged in the darkest tone; the handles are dark hush-wood wrapped in cord.

usage: python3 tools/items/tetherite.py [preview.png]
"""
import math
import os
import sys

from PIL import Image

ROOT = os.path.join(os.path.dirname(__file__), "..", "..")
ASSETS = os.path.join(ROOT, "src/client/resources/assets/extremedimensions/textures")

PAL = {
    "k": (14, 9, 22),       # the edge
    "d": (36, 26, 54),      # metal in shadow
    "m": (58, 45, 86),      # metal
    "l": (88, 72, 124),     # metal in light
    "h": (146, 132, 190),   # the glint
    "e": (14, 84, 96),      # thread in shadow
    "t": (30, 160, 168),    # thread
    "c": (70, 226, 214),    # thread in light
    "g": (196, 255, 246),   # thread's glow
    "o": (22, 14, 20),      # handle edge
    "b": (72, 50, 62),      # handle
    "n": (110, 82, 94),     # handle in light
    "w": (30, 160, 168),    # cord round the handle
}

U = (1 / math.sqrt(2), -1 / math.sqrt(2))   # along the tool, toward the head (upper right)
V = (1 / math.sqrt(2), 1 / math.sqrt(2))    # across it (lower right is +)


def frame(origin):
    """(s, w) of every pixel centre: s along the tool from the origin, w across it."""
    out = {}
    for y in range(16):
        for x in range(16):
            px, py = x + 0.5 - origin[0], y + 0.5 - origin[1]
            out[(x, y)] = (px * U[0] + py * U[1], px * V[0] + py * V[1])
    return out


def canvas():
    return [[None] * 16 for _ in range(16)]


def edge_pass(img, metal="k", wood="o"):
    """The outermost pixels of every part go to its darkest tone, as vanilla edges its items."""
    filled = {(x, y) for y in range(16) for x in range(16) if img[y][x]}
    out = [row[:] for row in img]
    for (x, y) in filled:
        if any((x + dx, y + dy) not in filled for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
            out[y][x] = wood if img[y][x] in "obnw" else metal
    return out


def handle(img, f, s0, s1, half=0.75, wraps=(2.0, 3.6)):
    """A dark hush-wood haft, lit on its upper left, with cord wound round it in bands."""
    for (x, y), (s, w) in f.items():
        if s0 <= s <= s1 and abs(w) <= half:
            c = "n" if w < -0.1 else "b"
            if any(abs(s - k) < 0.45 for k in wraps):
                c = "w"
            img[y][x] = c


def shade(w, s, glint_at=None):
    """Metal lit from the top left: light on the upper-left side of the part, shadow on the lower right."""
    if w < -0.55:
        return "l"
    if w > 0.55:
        return "d"
    return "m"


def sword():
    img = canvas()
    f = frame((1.6, 14.4))
    for (x, y), (s, w) in f.items():
        # the blade: wide, a cyan thread down its fuller, a point at the top right
        if 6.0 <= s <= 19.2:
            half = 1.55 if s < 16.6 else 1.55 * (19.2 - s) / 2.6
            if abs(w) <= half:
                c = "h" if w < -0.7 else "l" if w < -0.2 else "c" if w < 0.25 and s < 17.5 else "m" if w < 0.8 else "d"
                img[y][x] = c
        # the guard, across the blade, threaded
        elif 4.7 <= s < 6.0 and abs(w) <= 3.1:
            img[y][x] = "t" if abs(w) < 0.6 else "l" if w < 0 else "d"
    handle(img, f, 1.0, 4.7, wraps=(2.3,))
    for (x, y), (s, w) in f.items():
        if 0.0 <= s < 1.0 and abs(w) <= 1.1:
            img[y][x] = "m"
    img = edge_pass(img)
    img[12][3] = "g" if img[12][3] == "t" else img[12][3]
    return img


def pickaxe():
    img = canvas()
    f = frame((1.6, 14.4))
    handle(img, f, 0.0, 12.0, wraps=(2.4, 5.0))
    # the head: an arc round the top of the haft, its points reaching down either side
    cx, cy, r0, r1 = 1.0, 15.0, 12.6, 15.0
    for (x, y), _ in f.items():
        px, py = x + 0.5 - cx, y + 0.5 - cy
        r = math.hypot(px, py)
        a = math.degrees(math.atan2(-py, px))  # 45 = straight up the haft
        if r0 <= r <= r1 and 8 <= a <= 82:
            taper = min(a - 8, 82 - a) / 12.0
            if r1 - r <= (r1 - r0) * min(1.0, 0.35 + taper):
                mid = (r0 + r1) / 2
                c = "h" if r > r1 - 0.7 and 30 < a < 62 else "l" if r > mid + 0.3 else "c" if abs(r - mid) <= 0.35 and 20 < a < 72 else "d" if r < r0 + 0.6 else "m"
                img[y][x] = c
    img = edge_pass(img)
    return img


def axe():
    img = canvas()
    f = frame((1.6, 14.4))
    handle(img, f, 0.0, 13.0, wraps=(2.4, 5.0))
    # the head: a broad bit on the upper-left side of the haft's end, a short poll on the other
    for (x, y), (s, w) in f.items():
        if 8.6 <= s <= 14.2 and -5.0 <= w <= 1.2:
            # the bit flares toward its cutting edge (w very negative)
            spread = 2.6 + max(0.0, -w - 1.0) * 0.55
            if abs(s - 11.4) <= spread and not (w > 0.2 and abs(s - 11.4) > 1.5):
                c = "h" if w < -4.0 else "l" if w < -2.6 else "c" if -1.3 < w < -0.4 else "m" if w < 0.4 else "d"
                img[y][x] = c
    img = edge_pass(img)
    return img


def shovel():
    img = canvas()
    f = frame((1.6, 14.4))
    handle(img, f, 0.0, 11.0, wraps=(2.4, 5.0))
    for (x, y), (s, w) in f.items():
        if 10.0 <= s <= 17.6:
            half = 2.4 if s < 15.6 else 2.4 * math.sqrt(max(0.0, 1 - ((s - 15.6) / 2.0) ** 2))
            if abs(w) <= half:
                c = "h" if w < -1.5 and s > 12 else "l" if w < -0.4 else "c" if w < 0.3 and 11 < s < 16 else "m" if w < 1.4 else "d"
                img[y][x] = c
        elif 9.0 <= s < 10.0 and abs(w) <= 1.2:
            img[y][x] = "t"
    img = edge_pass(img)
    return img


def hoe():
    img = canvas()
    f = frame((1.6, 14.4))
    handle(img, f, 0.0, 13.6, wraps=(2.4, 5.0))
    for (x, y), (s, w) in f.items():
        # a blade off the upper-left side of the haft's end, bent down at its tip
        if 11.4 <= s <= 14.4 and -5.4 <= w <= 0.9:
            c = "h" if s > 13.4 else "l" if s > 12.6 else "c" if s > 11.9 and w < -0.8 else "m"
            if w > 0.2:
                c = "d"
            img[y][x] = c
        elif 9.6 <= s < 11.4 and -5.4 <= w <= -3.6:
            img[y][x] = "m" if s > 10.4 else "d"
    img = edge_pass(img)
    return img


def spear(long=False):
    """The spear: a long haft and a leaf-shaped head; the in-hand texture is the same, drawn longer."""
    img = canvas()
    f = frame((0.6, 15.4) if long else (1.6, 14.4))
    end = 21.0 if long else 19.2
    head0 = end - 6.4
    handle(img, f, 0.0, head0, half=0.7, wraps=(3.0, 6.0, 9.0) if long else (2.4, 5.0, 7.6))
    for (x, y), (s, w) in f.items():
        if head0 - 0.9 <= s < head0 and abs(w) <= 1.3:
            img[y][x] = "t"
        if head0 <= s <= end:
            t = (s - head0) / (end - head0)
            half = 2.2 * t / 0.3 if t < 0.3 else 2.2 * (1 - t) / 0.7
            if abs(w) <= half:
                img[y][x] = "h" if w < -0.9 else "l" if w < -0.25 else "c" if w < 0.3 and t < 0.85 else "m" if w < 0.9 else "d"
    img = edge_pass(img)
    return img


# --------------------------------------------------------------------------- hand drawn

PICKAXE = [
    "................",
    "....kkkkkk......",
    "...khhllllkkk...",
    "..khlcccclllmk..",
    "..kkkkkkmmctlmk.",
    "........kkkmcmdk",
    ".......knk.kmcdk",
    "......knbk.kmcdk",
    ".....kwnk..kdcdk",
    "....knbk...kmtdk",
    "...knbk.....kddk",
    "..kwnk......kdk.",
    ".knbk.......kdk.",
    "kbnk.........k..",
    "kbk.............",
    "kk..............",
]

AXE = [
    "................",
    "....kkkkk.......",
    "...khhlllkk.....",
    "..khlllmmmdk....",
    "..khlctmmmddk...",
    "..klcttmmdkknk..",
    "..klctmmdk.knbk.",
    "...klcmdkknbkdk.",
    "....kkkkknbk.kk.",
    "........knbk....",
    ".......kwnk.....",
    "......knbk......",
    ".....knbk.......",
    "....kwnk........",
    "...knbk.........",
    "...kkk..........",
]

HOE = [
    "................",
    "....kkkkkkk.....",
    "...khhllllmkk...",
    "...kdcttttmmdk..",
    "....kkkkkkkmdk..",
    "..........knbk..",
    ".........knbk...",
    "........knbk....",
    ".......kwnk.....",
    "......knbk......",
    ".....knbk.......",
    "....kwnk........",
    "...knbk.........",
    "..knbk..........",
    ".kbk............",
    ".kk.............",
]

SHOVEL = [
    "..........kkkk..",
    ".........khhlmk.",
    "........khlcmmdk",
    "........klccmmdk",
    "........klcmmddk",
    ".........kmmddk.",
    "........ktkkkk..",
    ".......knk......",
    "......knbk......",
    ".....kwnk.......",
    "....knbk........",
    "...knbk.........",
    "..kwnk..........",
    ".knbk...........",
    "kbk.............",
    "kk..............",
]

# the spear: a leaf-shaped head threaded down its middle, a cord-wound haft
SPEAR = [
    "..........kkkkkk",
    ".........khhllck",
    "........khllcmdk",
    ".......khlccmdk.",
    ".......klcmmdk..",
    ".......kcmddk...",
    "......kttkkk....",
    ".....knbk.......",
    "....kwnk........",
    "...knbk.........",
    "..knbk..........",
    ".kwnk...........",
    "knbk............",
    "kbk.............",
    "kk..............",
    "................",
]

# held, it reaches corner to corner, the head longer
SPEAR_IN_HAND = [
    ".........kkkkkkk",
    "........khhlllck",
    ".......khllccmdk",
    "......khlcccmddk",
    "......klccmmddk.",
    "......kcmmddkk..",
    ".....kttkkkk....",
    "....knbk........",
    "...kwnk.........",
    "..knbk..........",
    ".knbk...........",
    "kwnk............",
    "nbk.............",
    "bk..............",
    "k...............",
    "................",
]

# --------------------------------------------------------------------------- armor icons, hand drawn

HELMET = [
    "................",
    "................",
    "................",
    "....kkkkkkkk....",
    "...khlllllmmk...",
    "..khlcttttcmdk..",
    "..klmkkkkkkmdk..",
    "..klk......kdk..",
    "..kmk......kdk..",
    "..kkk......kkk..",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
]

CHESTPLATE = [
    "................",
    "..kkkk....kkkk..",
    ".khllkkkkkkmmdk.",
    ".klmlhlllllmmdk.",
    ".kkkmlctttcmkkk.",
    "...kmllcclmmk...",
    "...klmmttmmdk...",
    "...kllmccmmdk...",
    "...klmmttmmdk...",
    "...klmmccmddk...",
    "...kllmttmmdk...",
    "...klmmmmmddk...",
    "...kkkkkkkkkk...",
    "................",
    "................",
    "................",
]

LEGGINGS = [
    "................",
    "................",
    "...kkkkkkkkkk...",
    "...khlctttcmk...",
    "...kllmmmmmdk...",
    "...klmkkkkmdk...",
    "...klmk..kmdk...",
    "...klck..kcdk...",
    "...klmk..kmdk...",
    "...kltk..ktdk...",
    "...klmk..kmdk...",
    "...klmk..kmdk...",
    "...kkkk..kkkk...",
    "................",
    "................",
    "................",
]

BOOTS = [
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "...kkkk..kkkk...",
    "...klck..kcdk...",
    "...klmk..kmdk...",
    "...kltk..ktdk...",
    "..khlmk..kmmdk..",
    ".khllmk..kmmddk.",
    ".kkkkkk..kkkkkk.",
    "................",
    "................",
    "................",
]

INGOT = [
    "................",
    "................",
    "................",
    "................",
    "................",
    "......kkkkkkk...",
    "....kkhhllllmk..",
    "..kkhlctttttcdk.",
    ".khlllmmmmmmmdk.",
    ".klctttttcmmddk.",
    ".kmmmmmmmmmddk..",
    "..kddddddddkk...",
    "...kkkkkkkk.....",
    "................",
    "................",
    "................",
]

SCRAP = [
    "................",
    "................",
    "................",
    "................",
    ".......kkk......",
    ".....kkldmk.....",
    "....khlmdtdk....",
    "...khlmtcmmdk...",
    "...klmctmmddk...",
    "..kkmmtmmmdkk...",
    "..klmdmkdmmdk...",
    "..kmmdkcdmddk...",
    "...kddkkddkk....",
    "....kkk.kkk.....",
    "................",
    "................",
]

# a coil of cord spun from fragments: cyan thread wound tight, a dark knot at each end
CORD = [
    "................",
    "................",
    "....kkkkkk......",
    "...kcgcttek.....",
    "..kcttkkkttk....",
    "..ktek...kte....",
    "..ktk.....kt....",
    "..kte.....kte...",
    "...kte...kcte...",
    "....kttcctte....",
    ".....kkeeekkk...",
    "..........kdk...",
    "...........kdk..",
    "............kk..",
    "................",
    "................",
]


# --------------------------------------------------------------------------- smithing templates

TEMPLATE_PAL = {"K": (24, 22, 32), "S": (46, 44, 58), "Q": (72, 70, 88), "R": (104, 102, 122), "W": (142, 140, 160)}

TEMPLATE = [
    "................",
    "...KKKKKKKKKK...",
    "..KWRRRRRRRRQK..",
    "..KRQQQQQQQQSK..",
    "..KRQQQQQQQQSK..",
    "..KRQQQQQQQQSK..",
    "..KRQQQQQQQQSK..",
    "..KRQQQQQQQQSK..",
    "..KRQQQQQQQQSK..",
    "..KRQQQQQQQQSK..",
    "..KRQQQQQQQQSK..",
    "..KRQQQQQQQQSK..",
    "..KRQQQQQQQQSK..",
    "..KQSSSSSSSSSK..",
    "...KKKKKKKKKK...",
    "................",
]

# what each template carries in its middle (rows 3 to 12, columns 4 to 11)
EMBLEMS = {
    # two threads crossed through a ring: the Tether, bound again
    "tetherite_upgrade_smithing_template": [
        "e......e",
        ".t....t.",
        "..tcct..",
        "..cggc..",
        "..cggc..",
        "..tcct..",
        ".t....t.",
        "e......e",
        "........",
        "..dmmd..",
    ],
    # warp and weft: a loom's weave
    "weaver_armor_trim_smithing_template": [
        ".v.v.v.v",
        "uuuuuuuu",
        ".v.v.v.v",
        ".v.v.v.v",
        "uuuuuuuu",
        ".v.v.v.v",
        ".v.v.v.v",
        "uuuuuuuu",
        ".v.v.v.v",
        "........",
    ],
    # one thread, coming apart into many
    "fray_armor_trim_smithing_template": [
        "...vv...",
        "...vv...",
        "...vu...",
        "..v.uv..",
        "..u..v..",
        ".v...u..",
        ".u.v..v.",
        "v..u..u.",
        "u..v...v",
        "...u....",
    ],
}

EMBLEM_PAL = {"v": (196, 132, 250), "u": (120, 70, 186)}


def template(name):
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = im.load()
    for y, row in enumerate(TEMPLATE):
        for x, ch in enumerate(row):
            if ch != ".":
                px[x, y] = TEMPLATE_PAL[ch] + (255,)
    for y, row in enumerate(EMBLEMS[name]):
        for x, ch in enumerate(row):
            if ch != ".":
                px[4 + x, 3 + y] = (EMBLEM_PAL.get(ch) or PAL[ch]) + (255,)
    return im


def draw(rows):
    return [[None if ch == "." else ch for ch in row] for row in rows]


def png(img):
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = im.load()
    for y in range(16):
        for x in range(16):
            if img[y][x]:
                px[x, y] = PAL[img[y][x]] + (255,)
    return im


ITEMS = {
    "tetherite_sword": sword,
    "tetherite_pickaxe": lambda: draw(PICKAXE),
    "tetherite_axe": lambda: draw(AXE),
    "tetherite_shovel": lambda: draw(SHOVEL),
    "tetherite_hoe": lambda: draw(HOE),
    "tetherite_spear": lambda: draw(SPEAR),
    "tetherite_spear_in_hand": lambda: draw(SPEAR_IN_HAND),
    "tetherite_helmet": lambda: draw(HELMET),
    "tetherite_chestplate": lambda: draw(CHESTPLATE),
    "tetherite_leggings": lambda: draw(LEGGINGS),
    "tetherite_boots": lambda: draw(BOOTS),
    "tetherite_ingot": lambda: draw(INGOT),
    "tetherite_scrap": lambda: draw(SCRAP),
    "tether_cord": lambda: draw(CORD),
}


# --------------------------------------------------------------------------- blocks

BLOCKS = os.path.join(ASSETS, "block")


def ore():
    """Hush stone with knots of dark metal set in it, each threaded with the Tether's cyan."""
    base = Image.open(os.path.join(BLOCKS, "hush_stone.png")).convert("RGBA").crop((0, 0, 16, 16))
    px = base.load()
    knots = [
        [".kkk.", "kdmlk", "kmcgk", "kltck", ".kkk."],
        ["kkk.", "kmck", "ktgk", ".kk."],
        ["kkkk.", "kdlck", "kmcgk", ".kkkk"],
        ["kk.", "kck", ".kk"],
    ]
    for (ox, oy), shape in zip(((1, 1), (11, 2), (8, 9), (1, 11)), knots):
        for y, row in enumerate(shape):
            for x, ch in enumerate(row):
                if ch != ".":
                    px[(ox + x) % 16, (oy + y) % 16] = PAL[ch] + (255,)
    return base


def stripes(cols, rows_of, seed=0):
    """A 16 px square of horizontal bands (thread wound round a spool), each band a run of colours."""
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = im.load()
    for y in range(16):
        band = rows_of[y % len(rows_of)]
        for x in range(16):
            c = cols[band[(x + y * 3 + seed) % len(band)]]
            px[x, y] = c + (255,)
    return im


def spindle_parts():
    base = Image.open(os.path.join(BLOCKS, "polished_hush_stone.png")).convert("RGBA").crop((0, 0, 16, 16))
    px = base.load()
    for i in range(16):
        for (x, y) in ((i, 0), (i, 15), (0, i), (15, i)):
            r, g, b, _ = px[x, y]
            px[x, y] = (int(r * 0.6), int(g * 0.6), int(b * 0.6), 255)
    thread = {"g": PAL["g"], "c": PAL["c"], "t": PAL["t"], "e": PAL["e"]}
    silk = {"a": (232, 186, 255), "b": (196, 132, 250), "c": (150, 90, 220), "d": (96, 52, 150)}
    rod = {"a": (226, 220, 236), "b": (190, 182, 206), "c": (150, 140, 170)}
    return {
        "spindle_base": base,
        "spindle_thread": stripes(thread, ["ccccccgcccccccct", "tttttttttttttttt", "ccccctccccccccce", "eeeeeeeeeeeeeeee"]),
        "spindle_silk": stripes(silk, ["aabbbbbbaabbbbbb", "bbbbccbbbbbbccbb", "cccccccdcccccccd", "ddddcdddddddcddd"]),
        "spindle_rod": stripes(rod, ["abbbbbbbbbbbbbbc"] * 2 + ["bbbbbbbbbbbbbbbc"]),
    }


def main():
    out = {name: png(fn()) for name, fn in ITEMS.items()}
    for name in EMBLEMS:
        out[name] = template(name)
    preview = sys.argv[1] if len(sys.argv) > 1 else None
    if preview:
        out["tetherite_ore"] = ore()
        out.update(spindle_parts())
        S = 10
        sheet = Image.new("RGBA", (len(out) * (16 * S + 6), 16 * S), (60, 60, 64, 255))
        for i, im in enumerate(out.values()):
            sheet.paste(im.resize((16 * S, 16 * S), Image.NEAREST), (i * (16 * S + 6), 0), im.resize((16 * S, 16 * S), Image.NEAREST))
        sheet.save(preview)
        return
    for name, im in out.items():
        im.save(os.path.join(ASSETS, "item", name + ".png"))
    ore().save(os.path.join(BLOCKS, "tetherite_ore.png"))
    for name, im in spindle_parts().items():
        im.save(os.path.join(BLOCKS, name + ".png"))


if __name__ == "__main__":
    main()
