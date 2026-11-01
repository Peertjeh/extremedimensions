"""Tetherite, drawn as vanilla draws its gear: 16 px items, the tools on a plain wooden stick, the metal in a short ramp
lit from the top left and edged in its own darkest tone.

Tetherite is the metal the Fray keeps of every Tether it swallowed: a deep violet-black, cooler than netherite. The
Tether's cyan thread shows in it only here and there, as a gem would (a thread down a blade's fuller, a bead where the
head meets the haft, a stud on a plate), never as a stripe. Shapes follow vanilla's own silhouettes (the pick's arc,
the axe's blade, the spade, the hoe's bar, the sword's guard and pommel, the slanted ingot, the armour pieces, the
smithing templates' slab), so the set sits beside diamond and netherite in an inventory as one of them.

Each item is drawn as its interior by hand (or from a little geometry along the diagonal), and the outline added round
it: dark brown beside wood, the metal's darkest tone beside everything else.

usage: python3 tools/items/tetherite.py [preview.png]
"""
import math
import os
import sys

from PIL import Image

ROOT = os.path.join(os.path.dirname(__file__), "..", "..")
ASSETS = os.path.join(ROOT, "src/client/resources/assets/extremedimensions/textures")
BLOCKS = os.path.join(ASSETS, "block")

PAL = {
    '0': (24, 18, 34), '1': (38, 30, 52), '2': (52, 43, 70), '3': (68, 58, 90), '4': (88, 77, 114), '5': (114, 103, 143),
    '6': (158, 149, 186),
    'a': (28, 92, 100), 'b': (45, 158, 160), 'c': (112, 228, 214),
    'p': (186, 158, 210), 'q': (152, 122, 182), 'r': (118, 90, 148), 's': (86, 62, 114), 't': (62, 42, 86),
    'w': (40, 30, 11), 'x': (73, 54, 21), 'y': (104, 78, 30), 'z': (137, 103, 39),
}
WOOD = 'xyz'

def outline(rows):
    """Every empty pixel beside a filled one becomes the edge: dark brown beside wood only, else the metal's edge."""
    g = [list(r) for r in rows]
    out = [r[:] for r in g]
    for y in range(16):
        for x in range(16):
            if g[y][x] != '.':
                continue
            nb = [g[y + dy][x + dx] for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)) if 0 <= x + dx < 16 and 0 <= y + dy < 16]
            nb = [c for c in nb if c not in '.0w']
            if nb:
                out[y][x] = 'w' if all(c in WOOD for c in nb) else '0'
    return [''.join(r) for r in out]

def img(rows, edge=True):
    rows = outline(rows) if edge else rows
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    px = im.load()
    for y, r in enumerate(rows):
        for x, c in enumerate(r):
            if c != '.':
                px[x, y] = PAL[c] + (255,)
    return im

def grid():
    return [['.'] * 16 for _ in range(16)]

def put(g, cells):
    for (x, y, c) in cells:
        g[y][x] = c

def rows(g):
    return [''.join(r) for r in g]

def sword():
    g = grid()
    put(g, [(14, 1, '6')])
    for y in range(2, 11):
        c = 15 - y
        mid = 'c' if y == 5 else ('a' if 3 <= y <= 8 else '4')
        put(g, [(c - 1, y, '5' if y > 2 else '6'), (c, y, mid), (c + 1, y, '2')])
    # the guard, across the blade's foot, the thread's bead at its middle
    put(g, [(2, 9, '5'), (3, 10, '4'), (4, 11, 'c'), (5, 12, '3'), (6, 13, '2'),
            (3, 9, '4'), (4, 10, '3'), (5, 11, '3'), (6, 12, '2'), (7, 13, '1')])
    # the grip, and the pommel at the corner
    put(g, [(3, 12, 'z'), (2, 13, 'y'), (3, 13, 'x'), (2, 12, 'z')])
    put(g, [(1, 14, '5'), (0, 15, '3'), (1, 15, '2'), (0, 14, '4')])
    return rows(g)


def stick(g, y0, y1):
    """A vanilla stick down the diagonal, from row y0 to row y1: lit on its upper left, shaded on its lower right."""
    for y in range(y0, y1 + 1):
        x = 14 - y
        if 0 <= x < 16 and g[y][x] == '.':
            g[y][x] = 'z' if y % 3 else 'y'
        if 0 <= x + 1 < 16 and g[y][x + 1] == '.':
            g[y][x + 1] = 'x' if y % 3 else 'y'

def pickaxe():
    """The pick's head arcs over the top right corner from a point at the upper left to one at the lower right."""
    g = grid()
    rowsx = {1: (4, 9), 2: (2, 11), 3: (1, 4), 4: (1, 2)}
    head = {
        1: "....######......",
        2: "..##########....",
        3: ".####....#####..",
        4: ".##.........###.",
        5: "............###.",
        6: ".............##.",
        7: ".............##.",
        8: ".............##.",
        9: ".............##.",
        10: "............###.",
        11: "............##..",
        12: "............#...",
    }
    for y, r in head.items():
        for x, c in enumerate(r):
            if c == '#':
                g[y][x] = '3'
    # lit on the outer (upper, right) face of the arc, shaded on the inner one
    for y in range(16):
        for x in range(16):
            if g[y][x] == '3':
                up = y == 0 or g[y - 1][x] == '.'
                down = y == 15 or g[y + 1][x] == '.'
                right = x == 15 or g[y][x + 1] == '.'
                left = x == 0 or g[y][x - 1] == '.'
                if up and not down:
                    g[y][x] = '5'
                elif right and not left and y >= 4:
                    g[y][x] = '4'
                elif down or left:
                    g[y][x] = '2'
    g[1][5] = '6'
    stick(g, 4, 14)
    g[4][10] = 'c'
    return rows(g)

def axe():
    """The axe's head on the upper left of the haft's top, its blade edge facing up and left."""
    g = grid()
    head = {
        1: ".....####.......",
        2: "....#######.....",
        3: "...########.....",
        4: "...#######......",
        5: "...######.......",
        6: "....###.........",
        7: ".....#..........",
    }
    for y, r in head.items():
        for x, c in enumerate(r):
            if c == '#':
                left = g[y][x - 1] == '.' if x else True
                first = r.index('#')
                g[y][x] = '6' if x == first and y in (2, 3) else '5' if x <= first + 1 else ('2' if y >= 5 or x >= len(r.rstrip('.')) - 2 else '3')
    stick(g, 2, 14)
    g[3][10] = 'c'
    return rows(g)

def shovel():
    """A spade at the top of the haft, a rounded blade along the diagonal."""
    g = grid()
    head = {
        1: "...........###..",
        2: "..........#####.",
        3: ".........######.",
        4: "........######..",
        5: "........#####...",
        6: ".........###....",
    }
    for y, r in head.items():
        for x, c in enumerate(r):
            if c == '#':
                first, last = r.index('#'), len(r.rstrip('.')) - 1
                g[y][x] = '5' if x == first or y == 1 else ('2' if x == last or y == 6 else '3')
    g[2][11] = '6'
    stick(g, 7, 14)
    g[6][9] = 'c'
    return rows(g)

def hoe():
    g = grid()
    for x in range(6, 13):
        g[2][x] = '5' if x > 6 else '6'
        g[3][x] = '3' if x < 12 else '2'
    g[3][5] = '5'
    g[4][5] = '4'
    g[4][6] = '2'
    stick(g, 4, 14)
    g[4][11] = 'c'
    return rows(g)

def spear(long=False):
    g = grid()
    cx, cy = (12.4, 3.6) if not long else (12.0, 4.0)
    L = 3.4 if not long else 3.9
    for y in range(16):
        for x in range(16):
            dx, dy = x + 0.5 - cx, y + 0.5 - cy
            u, v = (dx - dy) / math.sqrt(2), (dx + dy) / math.sqrt(2)
            w = 1.6 * (1 - max(0.0, u) / L) if u >= 0 else 1.6 * (1 + u / (L * 0.55))
            if -L * 0.55 <= u <= L and abs(v) <= w:
                g[y][x] = '6' if (v < -0.6 and u > 1.4) else '5' if v < -0.35 else ('2' if v > 0.35 else '3')
    stick(g, 6, 15 if long else 14)
    # the thread wound round the foot of the head
    base = (8, 6) if not long else (8, 7)
    g[base[1]][base[0]] = 'b'
    g[base[1] + 1][base[0] - 1] = 'a'
    return rows(g)

def ingot():
    """A cast bar laid at a slant, as vanilla draws its ingots: the lit top face, the front face below a sharp edge, the
    dark end; a thread of cyan runs in the front face."""
    return [
        "................",
        "................",
        "................",
        "................",
        "................",
        "........666.....",
        "......66655554..",
        "....665555554432",
        "..6655555544432.",
        "..33333333332221",
        "..2223b33322211.",
        "...112a222211...",
        "................",
        "................",
        "................",
        "................",
    ]

def scrap():
    """Scrap: a rough lump of the metal with the thread showing through in specks."""
    return [
        "................",
        "................",
        "................",
        "................",
        ".......55.......",
        ".....5543.4.....",
        "....554332443...",
        "...5443a2232a...",
        "...4432c3322....",
        "...3322a22321...",
        "....3221a221....",
        "....222112211...",
        ".....11.111.....",
        "................",
        "................",
        "................",
    ]

def cord():
    """Tether Cord: a hank of twisted thread, two loops laid over each other, the twist in light and dark turns as vanilla
    draws its lead, the ends tied off below."""
    g = grid()
    for (cx, cy, rx, ry) in ((6.0, 6.5, 4.6, 3.6), (9.6, 8.6, 4.6, 3.6)):
        for y in range(16):
            for x in range(16):
                dx, dy = (x + 0.5 - cx) / rx, (y + 0.5 - cy) / ry
                r = math.hypot(dx, dy)
                if 0.6 <= r <= 1.0:
                    t = (x + y) % 3
                    g[y][x] = 'c' if (t == 0 and dy < 0.3) else ('a' if t == 2 or dy > 0.6 else 'b')
    for (x, y, c) in ((4, 12, 'b'), (3, 13, 'c'), (5, 12, 'a'), (4, 13, 'a'), (3, 14, 'b')):
        g[y][x] = c
    return rows(g)

def helmet():
    return [
        "................",
        "................",
        "................",
        "................",
        "....66665555....",
        "...6555555544...",
        "...54333b33322..",
        "...532......32..",
        "...53........2..",
        "...42........2..",
        "...31........1..",
        "................",
        "................",
        "................",
        "................",
        "................",
    ]

def chestplate():
    return [
        "................",
        "................",
        "..665.....5544..",
        ".65554...54443..",
        ".5544433344432..",
        ".54443333b33322.",
        ".432.5433333.21.",
        ".....5433332....",
        ".....5433322....",
        ".....5433322....",
        ".....4332221....",
        ".....3222211....",
        "................",
        "................",
        "................",
        "................",
    ]

def leggings():
    return [
        "................",
        "................",
        "................",
        "....65555554....",
        "....5433b332....",
        "....543..332....",
        "....43....32....",
        "....43....32....",
        "....43....32....",
        "....43....22....",
        "....32....21....",
        "................",
        "................",
        "................",
        "................",
        "................",
    ]

def boots():
    return [
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
        "...54.....54....",
        "...53.....43....",
        "...4b.....3b....",
        ".5443...5332....",
        ".3322...3221....",
        "................",
        "................",
        "................",
        "................",
        "................",
    ]

PLATE = [
    "................",
    "....LLLLLLLL....",
    "...LLMMMMMMM....",
    "...LMMMMMMMMD...",
    "...LMMMMMMMMD...",
    "...LMMMMMMMMD...",
    "...LMMMMMMMMD...",
    "...LMMMMMMMMD...",
    "...LMMMMMMMMD...",
    "...LMMMMMMMMD...",
    "...LMMMMMMMMD...",
    "...LMMMMMMMMD...",
    "...LMMMMMMMMD...",
    "....MMMMMMMMD...",
    "....DDDDDDDDD...",
    "................",
]

def template(light, mid, dark, emblem):
    """A smithing template as vanilla shapes them: a worn slab lit on its top and left edges, the pattern pressed in."""
    g = grid()
    for y, r in enumerate(PLATE):
        for x, c in enumerate(r):
            if c != '.':
                g[y][x] = {'L': light, 'M': mid, 'D': dark}[c]
    for y, r in enumerate(emblem):
        for x, c in enumerate(r):
            if c != '.':
                g[y][x] = c
    return rows(g)

def upgrade():
    # the Tether's knot, in the thread, pressed into a slab of the dark metal
    return template('4', '2', '1', [
        "................",
        "................",
        "................",
        ".....b....b.....",
        "......b..b......",
        "......cbbc......",
        ".....b.ab.b.....",
        ".....b.ba.b.....",
        "......cbbc......",
        "......a..a......",
        ".....a....a.....",
        ".....a....a.....",
        "................",
        "................",
        "................",
        "................",
    ])

def weaver_template():
    # the Weaver's weave: warp and weft pressed into a slab of Seam silk
    return template('p', 'q', 'r', [
        "................",
        "................",
        "................",
        "................",
        ".....t.t.t......",
        "....ssssssss....",
        ".....t.t.t......",
        "....ssssssss....",
        ".....t.t.t......",
        "....ssssssss....",
        ".....t.t.t......",
        "................",
        "................",
        "................",
        "................",
        "................",
    ])

def fray_template():
    # the Fray: threads coming loose from a slab of the dark metal
    return template('3', '2', '1', [
        "................",
        "................",
        "................",
        "................",
        ".....cbbbbc.....",
        "......b.b.......",
        ".....b..b.a.....",
        ".....b...b.a....",
        "....a....b......",
        "....a.....a.....",
        "..........a.....",
        "................",
        "................",
        "................",
        "................",
        "................",
    ])

# --------------------------------------------------------------------------- blocks

def ore():
    """Hush stone with the ore set in it as vanilla sets its ores: irregular clusters lit on the upper left and shaded on
    the lower right, no outline round them; dark metal with the thread glinting in it."""
    base = Image.open(os.path.join(BLOCKS, "hush_stone.png")).convert("RGBA").crop((0, 0, 16, 16))
    px = base.load()
    clusters = [
        (2, 2, ["5c.", "3ba", ".31"]),
        (10, 1, [".54", "5b3", "3a2", ".1."]),
        (6, 6, ["54..", "4c3.", "3ba2", ".21."]),
        (12, 8, ["5.", "c3", "a1"]),
        (1, 10, [".5..", "53c.", "3b21", ".1.."]),
        (8, 12, ["54b", "3a2", "21."]),
    ]
    for (x0, y0, shape) in clusters:
        for dy, r in enumerate(shape):
            for dx, c in enumerate(r):
                if c != ".":
                    px[x0 + dx, y0 + dy] = PAL[c] + (255,)
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
    thread = {"g": (196, 255, 246), "c": (70, 226, 214), "t": (30, 160, 168), "e": (14, 84, 96)}
    silk = {"a": (232, 186, 255), "b": (196, 132, 250), "c": (150, 90, 220), "d": (96, 52, 150)}
    rod = {"a": (226, 220, 236), "b": (190, 182, 206), "c": (150, 140, 170)}
    return {
        "spindle_base": base,
        "spindle_thread": stripes(thread, ["ccccccgcccccccct", "tttttttttttttttt", "ccccctccccccccce", "eeeeeeeeeeeeeeee"]),
        "spindle_silk": stripes(silk, ["aabbbbbbaabbbbbb", "bbbbccbbbbbbccbb", "cccccccdcccccccd", "ddddcdddddddcddd"]),
        "spindle_rod": stripes(rod, ["abbbbbbbbbbbbbbc"] * 2 + ["bbbbbbbbbbbbbbbc"]),
    }



ITEMS = {
    "tetherite_sword": sword, "tetherite_pickaxe": pickaxe, "tetherite_axe": axe, "tetherite_shovel": shovel,
    "tetherite_hoe": hoe, "tetherite_spear": spear, "tetherite_spear_in_hand": lambda: spear(True),
    "tetherite_ingot": ingot, "tetherite_scrap": scrap, "tether_cord": cord,
    "tetherite_helmet": helmet, "tetherite_chestplate": chestplate, "tetherite_leggings": leggings, "tetherite_boots": boots,
    "tetherite_upgrade_smithing_template": upgrade, "weaver_armor_trim_smithing_template": weaver_template,
    "fray_armor_trim_smithing_template": fray_template,
}


def main():
    out = {name: img(fn()) for name, fn in ITEMS.items()}
    preview = sys.argv[1] if len(sys.argv) > 1 else None
    if preview:
        out["tetherite_ore"] = ore()
        out.update(spindle_parts())
        S = 10
        sheet = Image.new("RGBA", (len(out) * (16 * S + 6), 16 * S), (139, 139, 139, 255))
        for i, im in enumerate(out.values()):
            sheet.alpha_composite(im.resize((16 * S, 16 * S), Image.NEAREST), (i * (16 * S + 6), 0))
        sheet.save(preview)
        return
    for name, im in out.items():
        im.save(os.path.join(ASSETS, "item", name + ".png"))
    ore().save(os.path.join(BLOCKS, "tetherite_ore.png"))
    for name, im in spindle_parts().items():
        im.save(os.path.join(BLOCKS, name + ".png"))


if __name__ == "__main__":
    main()
