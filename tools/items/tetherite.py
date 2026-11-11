"""Tetherite, drawn as vanilla draws its gear: 16 px items, the tools on a plain wooden stick, the metal in a short ramp
lit from the top left and edged in its own darkest tone.

Tetherite is the metal the Fray keeps of every Tether it swallowed: a deep violet-black, cooler than netherite. The
Tether's cyan thread shows in it only here and there, as a gem would (a thread down a blade's fuller, a bead where the
head meets the haft, a stud on a plate), never as a stripe. Shapes follow vanilla's own silhouettes (the pick's arc,
the axe's blade, the spade, the hoe's bar, the sword's guard and pommel, the armour pieces), so the set sits beside
diamond and netherite in an inventory as one of them. The ingot, the scrap, the Tether Cord and the three smithing
templates are drawn after vanilla's in tools/items/after_vanilla.py.

Each item is drawn as its interior by hand (or from a little geometry along the diagonal), and the outline added round
it: dark brown beside wood, the metal's darkest tone beside everything else.

usage: python3 tools/items/tetherite.py [preview.png]
"""
import os
import sys

from PIL import Image

ROOT = os.path.join(os.path.dirname(__file__), "..", "..")
ASSETS = os.path.join(ROOT, "src/client/resources/assets/extremedimensions/textures")
BLOCKS = os.path.join(ASSETS, "block")

PAL = {
    # the metal: netherite's dark and muted range, turned a little cool and violet (no lavender in the lights)
    '0': (22, 19, 27), '1': (35, 31, 43), '2': (49, 44, 60), '3': (64, 58, 78), '4': (82, 75, 98), '5': (106, 99, 124),
    '6': (140, 133, 158),
    # the Tether's thread, a deep teal that only glints where it catches the light
    'a': (27, 86, 92), 'b': (42, 140, 142), 'c': (96, 200, 190),
    'p': (186, 158, 210), 'q': (152, 122, 182), 'r': (118, 90, 148), 's': (86, 62, 114), 't': (62, 42, 86),
    # the haft: a dark violet, as netherite's is a dark wine; edged in the metal's own darkest tone
    'h': (44, 32, 58), 'i': (66, 48, 88), 'j': (92, 70, 118), 'w': (24, 16, 30),
}
WOOD = 'hij'

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

def haft(g, cells):
    for k, (x, y) in enumerate(cells):
        if g[y][x] == '.':
            g[y][x] = 'j' if k % 2 == 0 else 'i'

def line(x0, y0, n):
    """n cells down the diagonal toward the lower left from (x0, y0)."""
    return [(x0 - k, y0 + k) for k in range(n)]

def put(g, s):
    for y, r in enumerate(s):
        for x, c in enumerate(r):
            if c != '.':
                g[y][x] = c

def sword():
    return drawn(SWORD)

def pickaxe():
    return drawn(PICKAXE)

def axe():
    return drawn(AXE)

def shovel():
    return drawn(SHOVEL)

def hoe():
    return drawn(HOE)

# The tools and the leggings follow vanilla's netherite ones pixel for pixel (3.49.4), as the spear does: the metal in
# Tetherite's ramp, edged in violet on its lit side where netherite's is in wine, the haft in the dark violet, and
# one bead of the thread where the head meets the haft (on the sword, where the guard crosses the blade; on the
# leggings, at the belt).
SWORD = [
    ".............ttt",
    "............t660",
    "...........t6560",
    "..........t6360.",
    ".........t5350..",
    "........t4340...",
    "..tt...t3130....",
    "..t4t.t3130.....",
    "...t5t3130......",
    "...t55130.......",
    "....tb30........",
    "...hi0310.......",
    "..hj0.0010......",
    "tt20....00......",
    "t10.............",
    "000.............",
]

PICKAXE = [
    "................",
    "................",
    "......ttttt.....",
    ".....t66654tth..",
    "......t00034j0..",
    "..........h340..",
    ".........hb0340.",
    "........hj0.050.",
    ".......hi0..060.",
    "......hj0...060.",
    ".....h20....060.",
    "....h30......0..",
    "...h20..........",
    "..hj0...........",
    "..00............",
    "................",
]

AXE = [
    "................",
    ".........tt.....",
    "........t66t....",
    ".......t633t....",
    "......t53334i...",
    "......0533430...",
    ".......0043130..",
    "........hb0330..",
    ".......hj0.00...",
    "......hi0.......",
    ".....h30........",
    "....h20.........",
    "...h30..........",
    "..hj0...........",
    "..00............",
    "................",
]

SHOVEL = [
    "................",
    "................",
    "...........ttt..",
    "..........t6660.",
    ".........t64450.",
    "........t543450.",
    ".........h3450..",
    "........hb050...",
    ".......hj0.0....",
    "......hi0.......",
    ".....h20........",
    "....h20.........",
    "..hh30..........",
    "..hj0...........",
    "...00...........",
    "................",
]

HOE = [
    "................",
    ".......ttt......",
    "......t666t.....",
    ".......00561hi..",
    ".........045j0..",
    "..........h430..",
    ".........hb00...",
    "........hj0.....",
    ".......hi0......",
    "......hj0.......",
    ".....h30........",
    "....h20.........",
    "...h30..........",
    "..hj0...........",
    "..00............",
    "................",
]

LEGGINGS = [
    "................",
    "................",
    "....11111110....",
    "...15665b4420...",
    "...1554444430...",
    "...1544334430...",
    "...1543002430...",
    "...1440..0430...",
    "...1430..1430...",
    "...1430..1430...",
    "...1330..0330...",
    "...1320..0320...",
    "...0220..0220...",
    "...0000..0000...",
    "................",
    "................",
]

# The helmet, the chestplate and the boots follow vanilla's netherite ones too (3.49.5), with a stud of the thread at
# the brow, over the heart and at each boot's strap.
HELMET = [
    "................",
    "................",
    "................",
    ".....111111.....",
    "....13444420....",
    "...1346544320...",
    "...1444b43330...",
    "...1411541130...",
    "...1410440130...",
    "...1441000420...",
    "...1341000320...",
    "....10....00....",
    "................",
    "................",
    "................",
    "................",
]

CHESTPLATE = [
    "................",
    "................",
    ".11111....11111.",
    ".14321....12341.",
    ".132411..114231.",
    ".12234111143221.",
    ".15625611652651.",
    ".10223566532200.",
    "...0354554530...",
    "...0225665b20...",
    "...0332222330...",
    "...0235665320...",
    "...0223553220...",
    "....00222200....",
    ".....000000.....",
    "................",
]

BOOTS = [
    "................",
    "................",
    "................",
    "...21......12...",
    "...161....160...",
    "...151....140...",
    "...1530..1340...",
    "...1440..1440...",
    "...14b0..14b0...",
    "..14430..13430..",
    ".144320..123430.",
    ".133200..003320.",
    ".1000......0000.",
    "................",
    "................",
    "................",
]

# The spear follows vanilla's netherite spear pixel for pixel (3.49.3), its tones turned to Tetherite's: the leaf-shaped
# head in the metal's ramp, edged in violet on its lit side as netherite's is in wine; the haft in the dark violet; the
# grip in the metal's darker tones; a bead of the thread where the head meets the haft. Two drawings, as vanilla's: the
# icon (16 px, its head to the upper right) and the one held in the hand (32 px, its head to the upper LEFT, as the
# vanilla model for a spear in hand turns it so that it points ahead; drawn the other way it is held across the body).
SPEAR = [
    ".............ttt",
    "...........tt560",
    ".........tt45620",
    ".......tt354620.",
    ".......t3456210.",
    "........t46210..",
    "........h61110..",
    ".......hb0010...",
    "......hj0..00...",
    ".....hj0........",
    "....hj0.........",
    "...hj0..........",
    "..hj0...........",
    ".h30............",
    "h20.............",
    "20..............",
]

SPEAR_IN_HAND = [
    "ttt.............................",
    "065tt...........................",
    "02654tt.........................",
    ".026453tt.......................",
    ".0126543t.......................",
    "..01264t........................",
    "..01116h........................",
    "...0100bh.......................",
    "...00..0ah......................",
    "........0jh.....................",
    ".........0jh....................",
    "..........0jh...................",
    "...........0ih..................",
    "............0jh.................",
    ".............0jh................",
    "..............0ih...............",
    "...............03h..............",
    "................02h.............",
    ".................02h............",
    "..................03h...........",
    "...................03h..........",
    "....................03h.........",
    ".....................0ih........",
    "......................0jh.......",
    ".......................0jh......",
    "........................0jh.....",
    ".........................0ih....",
    "..........................0jh...",
    "...........................0ih..",
    "............................0ia.",
    ".............................0ba",
    "..............................00",
]


def drawn(rows):
    """An image of rows as they are (any size, the edge already drawn)."""
    im = Image.new('RGBA', (len(rows[0]), len(rows)), (0, 0, 0, 0))
    px = im.load()
    for y, r in enumerate(rows):
        for x, c in enumerate(r):
            if c != '.':
                px[x, y] = PAL[c] + (255,)
    return im


def spear(long=False):
    return drawn(SPEAR_IN_HAND if long else SPEAR)

def helmet():
    return drawn(HELMET)

def chestplate():
    return drawn(CHESTPLATE)

def leggings():
    return drawn(LEGGINGS)

def boots():
    return drawn(BOOTS)

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
    "tetherite_helmet": helmet, "tetherite_chestplate": chestplate, "tetherite_leggings": leggings, "tetherite_boots": boots,
}


def main():
    out = {}
    for name, fn in ITEMS.items():
        made = fn()
        out[name] = made if isinstance(made, Image.Image) else img(made)
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
