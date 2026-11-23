"""The second set of relics, drawn as 16 px items in vanilla's way, to sit beside the first ten (Ember Heart to Held's
Chain): hard edges, each in its own darkest tone, lit from the top left, a handful of colours. The rarer ones get a
touch more: a glow, gold, a vein of violet.

Pilgrim's Sandal (common): a worn leather sandal seen from the side, its straps crossing over the instep, the tie at
    the ankle fraying loose, the footbed rubbed dark at heel and ball.
Lodestone Chip (common): a chipped corner of lodestone, its chiselled top flecked with iron filings and a cold glint
    along its edge, the dark band of netherite running round its flanks.
Thornward Bark (uncommon): a curl of dark bark, thorns standing off its back, a line of sap running green to red
    down the pale wood inside.
Hollow Whistle (uncommon): a little bone whistle of the Hollowlings, a cord through its loop and their soul-blue
    light deep in its hollow.
Last Stitch (rare): a steel needle, one silver-white thread through its eye, the two ends knotted with a faint glow.
Hunger's Fang (rare): a long curved fang, dark gum still on its root, a drop of red hanging from its point.
Debtor's Seal (epic): a heavy iron stamp seen face on, a gold ring round its face and a cracked crimson sigil on
    purple, its knob behind.
Unbound Pinion (epic): one long pale wing feather like an elytron's, veined in violet, its quill through a snapped
    chain link.

Retired: tools/items/after_vanilla.py draws these eight now, after vanilla's own textures. This script no longer writes
them; given a path it previews these old drawings there.

usage: python3 tools/items/relics_2.py [preview.png]
"""
import os
import sys

from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.join(os.path.dirname(__file__), "..", "..")
ITEMS = os.path.join(ROOT, "src/client/resources/assets/extremedimensions/textures/item")

SANDAL_PAL = {
    'k': (38, 24, 14),
    'S': (222, 186, 134),
    'w': (176, 132, 84),
    'e': (124, 80, 44),
    'q': (86, 52, 28),
    'R': (160, 94, 52),
    'r': (110, 60, 34),
    'f': (216, 188, 142),
}
SANDAL = [
    "................",
    "................",
    "................",
    "..RRRR..........",
    ".Rr..rR.........",
    ".Rr..rRrr.......",
    ".RrRRRrR.r......",
    ".Rr...rR..f.f...",
    ".Rr..R.rR..f....",
    ".RrrR...rR..RR..",
    ".Rr.rR...rRRrrR.",
    ".SSSSwwSSSSSwSS.",
    ".eeeeeeeeeeeeee.",
    "..qqqqqqqqqqqq..",
    "................",
    "................",
]

LODE_PAL = {
    'k': (18, 16, 22),
    's': (232, 240, 252),
    'A': (206, 206, 210),
    'B': (170, 170, 176),
    'C': (132, 130, 140),
    'D': (98, 96, 106),
    'E': (72, 70, 80),
    'T': (124, 104, 104),
    't': (86, 70, 72),
    'n': (60, 48, 52),
    'j': (70, 72, 84),
}
LODE = [
    "................",
    "................",
    "................",
    "......sAAA......",
    "....ssAjAAjA....",
    "..ssAAAAjAAAA...",
    "..BBAjAAAAAADD..",
    "..BBBBAAAADDDD..",
    "..BBBBBBDDDDDD..",
    "..TTBBBBDDDDtt..",
    "..ttTTBBDDttnn..",
    "...CttTTttnnE...",
    ".....CttnnE.....",
    ".......CE.......",
    "................",
    "................",
]

BARK_PAL = {
    'k': (22, 14, 10),
    'L': (116, 86, 60),
    'M': (84, 60, 42),
    'N': (58, 40, 30),
    'W': (166, 128, 86),
    'w': (126, 92, 58),
    'T': (214, 202, 170),
    't': (140, 120, 90),
    'G': (126, 184, 76),
    'o': (184, 136, 62),
    'R': (204, 56, 56),
}
BARK = [
    "................",
    "...T............",
    "...tt.LLLMMMN...",
    "....LLLMNMNNN...",
    "....LMMwWWWwN...",
    "...LMMwG....N...",
    "..tLNwG.........",
    ".TtLMwo.........",
    "...LMwo.........",
    "...LNwR.........",
    "..tLMMwR........",
    ".TtLMMMRwWWw....",
    "....LMMNMMNN....",
    "....tMNNMNNN....",
    "...T...NNNN.....",
    "................",
]

WHISTLE_PAL = {
    'k': (26, 24, 30),
    'A': (234, 228, 212),
    'B': (204, 196, 178),
    'C': (160, 152, 138),
    'D': (118, 110, 102),
    'h': (28, 34, 48),
    'b': (110, 190, 255),
    'w': (200, 236, 255),
    'c': (46, 54, 72),
    'O': (116, 128, 152),
}
WHISTLE = [
    "................",
    "................",
    "...........cOc..",
    "..........O...c.",
    "..........c...O.",
    "...AAhhAA..O.c..",
    "..hBBBBBAAABc...",
    "..CCCCCABBBBBC..",
    "......ABBhhBBC..",
    "......ABhbbhBC..",
    "......ABhbwhBD..",
    "......CBBhhBBD..",
    ".......CBBBCD...",
    "........DDDD....",
    "................",
    "................",
]

STITCH_PAL = {
    'k': (20, 20, 30),
    'N': (184, 192, 208),
    'n': (124, 132, 152),
    'm': (82, 88, 106),
    't': (255, 255, 255),
    'K': (255, 255, 255),
    'g': (176, 196, 255),
}
STITCH = [
    "................",
    "...........NN...",
    "..........NtNn..",
    ".........N.nt...",
    ".........Nnt.t..",
    "........Nnt...t.",
    ".......Nn.t...t.",
    "......Nn..t...t.",
    ".....Nn....t.t..",
    "....Nm.....KK...",
    "...Nm.....gKKg..",
    "..Nm.........t..",
    "..m...........t.",
    ".m..............",
    "................",
    "................",
]

FANG_PAL = {
    'k': (30, 12, 14),
    'G': (176, 60, 72),
    'g': (118, 32, 46),
    'd': (74, 20, 32),
    'I': (246, 240, 224),
    'i': (214, 204, 182),
    's': (160, 146, 124),
    'r': (214, 40, 44),
    'D': (186, 24, 32),
    'H': (255, 132, 128),
}
FANG = [
    "................",
    ".........dgGg...",
    "........gGGGGgd.",
    "........gIIIis..",
    "........IIIiss..",
    "........IIiss...",
    ".......IIiss....",
    "......IIiss.....",
    ".....IIis.......",
    "....IIis........",
    "...IIs..........",
    "..Is............",
    "..r.............",
    ".HD.............",
    ".DD.............",
    "................",
]

SEAL_PAL = {
    'k': (20, 10, 18),
    'I': (132, 124, 136),
    'i': (90, 84, 96),
    'j': (56, 50, 62),
    'Y': (255, 236, 150),
    'y': (232, 182, 70),
    'o': (160, 110, 36),
    'P': (100, 30, 92),
    'p': (68, 18, 64),
    'Q': (228, 60, 104),
    'q': (255, 156, 180),
    'x': (20, 6, 16),
}
SEAL = [
    "................",
    "..II............",
    ".IIIi...........",
    ".IiijYIIiii.....",
    "..jjyiiYyyyy....",
    "....ijYPPPPPy...",
    "...ijyPQPQPQxo..",
    "...ijyPQPQPxPpo.",
    "...iyPPPQQxpppo.",
    "...iyPPppxpppPo.",
    "...jyPPpxQppppo.",
    "....yPpxQQQpppo.",
    ".....yPpppppppo.",
    "......opppppo...",
    ".......ooooo....",
    "................",
]

PINION_PAL = {
    'k': (24, 20, 34),
    'W': (234, 234, 240),
    'M': (198, 198, 210),
    'm': (158, 156, 174),
    'd': (116, 112, 136),
    'V': (176, 110, 236),
    'v': (128, 80, 186),
    'Q': (236, 230, 214),
    'L': (188, 186, 198),
    'C': (126, 124, 136),
    'c': (80, 78, 90),
}
PINION = [
    "................",
    "............WW..",
    "...........WVMd.",
    "..........WVMmd.",
    ".........WVvvvd.",
    "........WVMMmd..",
    ".......WVMMMm...",
    "......WVvvvvd...",
    ".....WVMMMmd....",
    ".....VMMmd......",
    "....QVvd........",
    "..LLQL..........",
    ".L.Q..c.........",
    "..cQc...........",
    "..Q.............",
    "................",
]

# (id, palette, rows, chars that cast no edge of their own)
NEW = [
    ('pilgrims_sandal', SANDAL_PAL, SANDAL, ''),
    ('lodestone_chip', LODE_PAL, LODE, ''),
    ('thornward_bark', BARK_PAL, BARK, ''),
    ('hollow_whistle', WHISTLE_PAL, WHISTLE, 'cO'),
    ('last_stitch', STITCH_PAL, STITCH, 'tg'),
    ('hungers_fang', FANG_PAL, FANG, ''),
    ('debtors_seal', SEAL_PAL, SEAL, ''),
    ('unbound_pinion', PINION_PAL, PINION, ''),
]
OLD = ['ember_heart', 'pale_shard', 'hungering_hand', 'featherbone', 'keepers_eye', 'tithe_bell', 'lurkers_tooth',
       'seam_lantern', 'kindred_knot', 'helds_chain']


def outline(rows, skip=''):
    """Every empty pixel beside a filled one (left, right, above or below) becomes the edge, unless that one is in skip."""
    out = [list(r) for r in rows]
    for y in range(16):
        for x in range(16):
            if rows[y][x] != '.':
                continue
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                if 0 <= x + dx < 16 and 0 <= y + dy < 16 and rows[y + dy][x + dx] not in '.k' + skip:
                    out[y][x] = 'k'
                    break
    return [''.join(r) for r in out]


def draw(rows, pal, skip=''):
    assert len(rows) == 16 and all(len(r) == 16 for r in rows), [len(r) for r in rows]
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    px = im.load()
    for y, r in enumerate(outline(rows, skip)):
        for x, c in enumerate(r):
            if c != '.':
                px[x, y] = pal[c] + (255,)
    return im


def preview(path, ours):
    """All the relics at 8x on an inventory slot's grey and on a dark field, the first ten above the new eight, labelled,
    and every one at 1x and 2x along the bottom."""
    old = [(n, Image.open(os.path.join(ITEMS, n + '.png')).convert('RGBA')) for n in OLD]
    rows = [old, ours]
    S, gap, label = 8, 10, 16
    cell = 16 * S + gap
    cols = max(len(r) for r in rows)
    w = gap + cols * cell
    band = gap + len(rows) * (16 * S + label + gap)
    strip = 90
    sheet = Image.new('RGBA', (w, 2 * band + strip), (139, 139, 139, 255))
    sheet.alpha_composite(Image.new('RGBA', (w, band), (32, 30, 40, 255)), (0, band))
    d = ImageDraw.Draw(sheet)
    font = ImageFont.load_default(size=12)
    for b, ink in ((0, (24, 24, 24)), (1, (220, 216, 230))):
        for j, row in enumerate(rows):
            for i, (name, im) in enumerate(row):
                x = gap + i * cell
                y = b * band + gap + j * (16 * S + label + gap)
                sheet.alpha_composite(im.resize((16 * S, 16 * S), Image.NEAREST), (x, y))
                d.text((x, y + 16 * S + 2), name, fill=ink + (255,), font=font)
    every = old + ours
    y = 2 * band + 10
    for i, (_, im) in enumerate(every):
        sheet.alpha_composite(im, (gap + i * 22, y + 8))
        sheet.alpha_composite(im.resize((32, 32), Image.NEAREST), (gap + i * 38, y + 34))
    sheet.save(path)


def main():
    ours = [(name, draw(rows, pal, skip)) for name, pal, rows, skip in NEW]
    if len(sys.argv) > 1:
        preview(sys.argv[1], ours)
        print('wrote', sys.argv[1])


if __name__ == '__main__':
    main()
