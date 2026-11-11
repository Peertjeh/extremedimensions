"""The third set of relics, drawn as 16 px items in vanilla's way to sit beside the first eighteen: hard edges, each in its
own darkest tone, lit from the top left, a handful of colours. As before the commons are plain, an uncommon gets one
accent, a rare a glow, an epic gold and violet.

Ashen Locket (common): a small round locket of tarnished brass, its lid swung half open on the hinge to the left, grey
    ash and a dull ember in its dish, the bail on top.
Miner's Candle (common): a stubby tallow stub, drips down its side, in a little iron dish whose arm rises into a hook,
    a small warm flame on the wick.
Magpie's Glint (uncommon): the tip of a magpie's feather, black along its outer vane and its end, white on the inner,
    a stolen gold bead tied to its quill on a short thread.
Anchor Rivet (uncommon): a heavy iron rivet, slightly bent, its domed head of lodestone grey banded dark, one soul-blue
    fleck of light caught in it.
Muffled Bell (rare): a dark iron bell bound round with a strip of sculk-teal cloth, its tail loose, a faint cyan glow
    welling at its mouth.
Veil Scale (rare): one iridescent scale of a Veil Ray, a pale violet-blue shield, a vein of light glowing across it.
Ferryman's Obol (epic): a worn gold coin stamped with a boat and its oar, a soul-fire glint on its rim, a dark violet
    thread through the hole by its edge.
Hollow Crown (epic): a thin crown of dark iron seen from a little above, empty inside, gold inlaid round its band and
    violet stones set in it, one prong snapped short.

Retired: tools/items/after_vanilla.py draws these eight now, after vanilla's own textures. This script no longer writes
them; given a path it previews these old drawings there.

usage: python3 tools/items/relics_3.py [preview.png]
"""
import os
import sys

from PIL import Image, ImageDraw, ImageFont

sys.dont_write_bytecode = True
from relics_2 import ITEMS, draw  # noqa: E402  (one outline and one look for every set)

LOCKET_PAL = {
    'k': (34, 26, 18),
    'A': (214, 192, 140),
    'B': (168, 144, 96),
    'C': (120, 100, 64),
    'D': (82, 66, 42),
    't': (122, 128, 96),
    'a': (160, 154, 148),
    's': (118, 114, 110),
    'd': (84, 80, 78),
    'e': (168, 70, 34),
    'E': (230, 130, 58),
}
LOCKET = [
    "................",
    "................",
    ".........CCC....",
    ".........C.C....",
    "..........B.....",
    "..AB....AAABB...",
    ".ABBC..ABBBBBC..",
    ".ADDC.ABDdddsBC.",
    ".ADDCCABdsasaCD.",
    ".BDDCCBBdaEeatD.",
    ".BDDD.BCsdeeaCD.",
    ".CDDD.CCsaadsCD.",
    "..CD...CtCCCCD..",
    "........DDDDD...",
    "................",
    "................",
]

CANDLE_PAL = {
    'k': (30, 22, 18),
    'T': (242, 232, 196),
    't': (216, 198, 152),
    'u': (170, 148, 104),
    'w': (60, 44, 34),
    'I': (176, 176, 186),
    'i': (120, 120, 132),
    'j': (76, 76, 88),
    'Y': (255, 244, 180),
    'O': (255, 178, 64),
    'R': (226, 106, 38),
}
CANDLE = [
    "................",
    "......O.........",
    ".....OY....iII..",
    ".....RO...i...I.",
    "......w...j...I.",
    "...TTtTu......I.",
    "...TttTuu.....I.",
    "...Ttttuu.....i.",
    "...TtttTu.....i.",
    "..tTtttut.....i.",
    ".IIIIIIIIIIIIIi.",
    "..iiiiiiiiiiij..",
    "...jjjjjjjjjj...",
    "................",
    "................",
    "................",
]

MAGPIE_PAL = {
    'k': (14, 14, 20),
    'W': (244, 244, 240),
    'w': (206, 206, 212),
    'g': (150, 150, 164),
    'N': (62, 68, 92),
    'n': (40, 42, 58),
    'q': (110, 128, 168),
    'r': (84, 62, 50),
    'Y': (255, 242, 160),
    'y': (238, 186, 58),
    'o': (166, 112, 30),
}
MAGPIE = [
    "................",
    "...........nqn..",
    ".........NNqnnn.",
    "........NNqnnn..",
    ".......NNqnnn...",
    "......NNgWnn....",
    ".....NNgWWn.....",
    "....NNgWWw......",
    "...NNgWW.w......",
    "...NgWWw........",
    "...gWw..........",
    "..gr...Yy.......",
    ".g..r.Yyyo......",
    ".....ryyoo......",
    ".......oo.......",
    "................",
]

RIVET_PAL = {
    'k': (18, 18, 24),
    'A': (210, 210, 216),
    'B': (170, 170, 176),
    'C': (132, 130, 140),
    'D': (98, 96, 106),
    't': (86, 70, 72),
    'n': (60, 48, 52),
    'I': (190, 190, 200),
    'i': (136, 136, 148),
    'j': (90, 90, 104),
    'b': (110, 190, 255),
    'w': (214, 240, 255),
}
RIVET = [
    "................",
    ".....AAABB......",
    "...AAAwbBBCC....",
    "...BBBBBCCCD....",
    "....tttttnn.....",
    "......Iij.......",
    "......Iij.......",
    "......Iij.......",
    "......Iij.......",
    ".......Iij......",
    ".......Iij......",
    "........Iij.....",
    "........Iij.....",
    ".........Ij.....",
    "..........j.....",
    "................",
]

BELL_PAL = {
    'k': (8, 14, 20),
    'I': (118, 122, 138),
    'i': (78, 82, 96),
    'j': (50, 54, 66),
    'h': (24, 30, 38),
    'T': (52, 160, 160),
    't': (16, 100, 108),
    'd': (10, 62, 72),
    'c': (60, 210, 214),
    'C': (170, 255, 248),
}
BELL = [
    "................",
    "................",
    ".......ij.......",
    "......i..j......",
    ".....IIiijj.....",
    "....IIiiiijj....",
    "....IiiiiijjT...",
    "....TTTiiTTtd...",
    "....ttdTTttdTd..",
    "....IiitttijdTd.",
    "....Iiiiiiijj.t.",
    "...IIiiiiiiijj..",
    ".IIIiiiiiiiijjj.",
    "...hcccCCccch...",
    "................",
    "................",
]

SCALE_PAL = {
    'k': (30, 24, 64),
    'P': (236, 232, 255),
    'p': (198, 192, 246),
    'r': (214, 182, 240),
    'm': (160, 156, 230),
    'g': (140, 134, 220),
    'M': (118, 108, 204),
    'b': (128, 162, 232),
    'd': (84, 72, 164),
    'V': (255, 255, 255),
    'v': (172, 234, 255),
}
SCALE = [
    "................",
    "......pppm......",
    "....pPPrppmm....",
    "...pPrrppmmmM...",
    "..ppPrppmmmmMM..",
    "..pprpmmmmmmbM..",
    "..vVvmmmvvvbbM..",
    "..mmVVvvVVVvbd..",
    "..pmmmmmMMbVvd..",
    "...mmgMMMMbbd...",
    "...pmmmmmmbbd...",
    "....mmgMMMbd....",
    ".....mmmmbd.....",
    "......mMbd......",
    ".......dd.......",
    "................",
]

OBOL_PAL = {
    'k': (38, 22, 12),
    'Y': (255, 236, 150),
    'y': (232, 182, 70),
    'o': (176, 122, 40),
    'O': (120, 76, 24),
    'v': (150, 92, 200),
    'V': (78, 40, 110),
    'c': (110, 230, 236),
    'C': (236, 255, 255),
}
OBOL = [
    "................",
    "................",
    "..vV............",
    ".v..V...........",
    "..V..YYYyy......",
    ".....YYyyyyo....",
    "....Y.yyyyOyo...",
    "...YyyyyyOyyyo..",
    "...YOyyyOyyyyo..",
    "...yOOOOOOOOyo..",
    "...yyOOOOOOyyc..",
    "...yyOOyyyyycCc.",
    "....OOyyyyyyc...",
    ".....oyyyyoo....",
    "......ooooo.....",
    "................",
]

CROWN_PAL = {
    'k': (16, 12, 22),
    'I': (150, 146, 164),
    'i': (104, 100, 120),
    'j': (70, 66, 84),
    'h': (34, 30, 44),
    'Y': (255, 230, 140),
    'y': (216, 164, 58),
    'P': (210, 140, 255),
    'p': (128, 60, 196),
}
CROWN = [
    "................",
    ".......I........",
    "..I....Ii.......",
    "..Ii..IPpj......",
    "..Ii..Ippj......",
    ".IIij.Iiij...j..",
    ".IiijjIiijj.ij..",
    ".IiihhIiijhhIij.",
    ".YyyyyyyyyyyyyY.",
    ".iPpiiiPpiiiPpj.",
    ".iiiiiiiiiiiiij.",
    "..iiiiiiiiiiij..",
    "....jjjjjjjj....",
    "................",
    "................",
    "................",
]

# (id, tier, palette, rows, chars that cast no edge of their own)
NEW = [
    ('ashen_locket', 'common', LOCKET_PAL, LOCKET, ''),
    ('miners_candle', 'common', CANDLE_PAL, CANDLE, ''),
    ('magpies_glint', 'uncommon', MAGPIE_PAL, MAGPIE, ''),
    ('anchor_rivet', 'uncommon', RIVET_PAL, RIVET, ''),
    ('muffled_bell', 'rare', BELL_PAL, BELL, 'cC'),
    ('veil_scale', 'rare', SCALE_PAL, SCALE, ''),
    ('ferrymans_obol', 'epic', OBOL_PAL, OBOL, 'cC'),
    ('hollow_crown', 'epic', CROWN_PAL, CROWN, ''),
]
# the eighteen before, by tier
OLD = {
    'common': ['ember_heart', 'pale_shard', 'tithe_bell', 'pilgrims_sandal', 'lodestone_chip', 'kindred_knot'],
    'uncommon': ['hungering_hand', 'featherbone', 'thornward_bark', 'hollow_whistle'],
    'rare': ['keepers_eye', 'seam_lantern', 'lurkers_tooth', 'last_stitch', 'hungers_fang'],
    'epic': ['helds_chain', 'debtors_seal', 'unbound_pinion'],
}


def preview(path, ours):
    """Every relic at 8x on an inventory slot's grey and on a dark field, a row to a tier with the new ones last (marked
    with a star), and every one at 1x and 2x along the bottom."""
    rows = []
    for tier, names in OLD.items():
        row = [(n, Image.open(os.path.join(ITEMS, n + '.png')).convert('RGBA')) for n in names]
        row += [('* ' + n, im) for n, t, im in ours if t == tier]
        rows.append(row)
    S, gap, label = 8, 10, 16
    cell = 16 * S + gap
    cols = max(len(r) for r in rows)
    w = gap + cols * cell
    band = gap + len(rows) * (16 * S + label + gap)
    strip = 130
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
    every = [im for row in rows for _, im in row]
    y = 2 * band + 10
    for i, im in enumerate(every):
        sheet.alpha_composite(im, (gap + i * 22, y))
        sheet.alpha_composite(im.resize((32, 32), Image.NEAREST), (gap + i * 38, y + 24))
    # and the same on the dark, as they'd sit in a tooltip or a dark inventory theme
    dark = Image.new('RGBA', (w, 60), (32, 30, 40, 255))
    sheet.alpha_composite(dark, (0, y + 64))
    for i, im in enumerate(every):
        sheet.alpha_composite(im, (gap + i * 22, y + 70))
        sheet.alpha_composite(im.resize((32, 32), Image.NEAREST), (gap + i * 38, y + 86))
    sheet.save(path)


def main():
    ours = [(name, tier, draw(rows, pal, skip)) for name, tier, pal, rows, skip in NEW]
    if len(sys.argv) > 1:
        preview(sys.argv[1], ours)
        print('wrote', sys.argv[1])


if __name__ == '__main__':
    main()
