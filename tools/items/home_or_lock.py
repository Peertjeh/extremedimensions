"""The End's ending pair (3.44, "Home or Lock"), drawn as 16 px items in vanilla's way.

The Homeward Keystone: the last stone the Endermen of the Pale Reach needed to close their anchor's arch. A wedge of
pale stone (end stone's cream in the light, the Reach's lavender grey in the shade), a thin gold inlay set across its
brow, and a keyhole-shaped slit of soft violet-white light through its middle.

The Pale Lock: the means to seal the anchor shut for good. A heavy padlock of obsidian-black metal, crying obsidian's
violet running in it as veins, a pale gold shackle, and a keyhole that burns a deep purple.

Both are drawn as their interiors by hand and edged in their own darkest tone, lit from the top left.

usage: python3 tools/items/home_or_lock.py [preview.png]
"""
import os
import sys

from PIL import Image

ROOT = os.path.join(os.path.dirname(__file__), "..", "..")
ITEMS = os.path.join(ROOT, "src/client/resources/assets/extremedimensions/textures/item")

KEYSTONE_PAL = {
    'k': (46, 38, 58),     # edge
    'T': (246, 242, 230),  # brow, lit
    'A': (230, 224, 214),  # left flank, lit
    'B': (208, 202, 200),  # face
    'b': (190, 184, 190),  # face, a fleck
    'C': (168, 160, 178),  # right flank, shade
    'D': (136, 128, 154),  # foot, shade
    'y': (255, 236, 158),  # gold, glint
    'g': (236, 190, 78),   # gold
    'h': (170, 120, 44),   # gold, shade
    'w': (252, 248, 255),  # the light, core
    'v': (218, 196, 255),  # the light
    'q': (222, 212, 240),  # stone the light falls on
}
KEYSTONE = [
    "................",
    "................",
    "..TTTTTTTTTTT...",
    "..ABBBBBBBBBC...",
    "..AByggggghBC...",
    "...ABBqvqBBC....",
    "...ABBvwvBBC....",
    "...AbBqwqBbC....",
    "....ABqwqBC.....",
    "....ABBwBBC.....",
    "....AbBvBBC.....",
    ".....ABqBC......",
    ".....ABBbC......",
    ".....DDDDD......",
    "................",
    "................",
]

LOCK_PAL = {
    'k': (12, 6, 20),       # edge
    'Y': (252, 240, 184),   # shackle, lit
    'y': (224, 200, 128),   # shackle
    'S': (164, 128, 66),    # shackle, shade
    's': (112, 84, 44),     # shackle, deep in the body
    'L': (78, 58, 108),     # body, lit rim
    'M': (42, 30, 62),      # body
    'm': (32, 22, 48),      # body, darker
    'D': (24, 16, 36),      # body, shade
    'U': (98, 40, 164),     # vein
    'V': (150, 70, 232),    # vein, bright
    'P': (124, 36, 200),    # keyhole, rim
    'K': (204, 116, 255),   # keyhole, burning
    'H': (66, 30, 104),     # body the keyhole lights
}
LOCK = [
    "................",
    "....YYYYYYYY....",
    "...YyyyyyyyyS...",
    "...Yy......yS...",
    "...Yy......yS...",
    "...Yy......yS...",
    "...Ys......sS...",
    "...ss......ss...",
    "..LLLLLLLLLLLL..",
    "..LMMMMPPMMMVD..",
    "..LMMVPKKPMUMD..",
    "..LMMUPKKPMMMD..",
    "..LMmMHKKHMMmD..",
    "..LVMMMPPMMMUD..",
    "..DDDDDDDDDDDD..",
    "................",
]


def outline(rows):
    """Every empty pixel beside a filled one (left, right, above or below) becomes the edge."""
    out = [list(r) for r in rows]
    for y in range(16):
        for x in range(16):
            if rows[y][x] != '.':
                continue
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                if 0 <= x + dx < 16 and 0 <= y + dy < 16 and rows[y + dy][x + dx] not in '.k':
                    out[y][x] = 'k'
                    break
    return [''.join(r) for r in out]


def draw(rows, pal):
    assert len(rows) == 16 and all(len(r) == 16 for r in rows), rows
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    px = im.load()
    for y, r in enumerate(outline(rows)):
        for x, c in enumerate(r):
            if c != '.':
                px[x, y] = pal[c] + (255,)
    return im


def preview(path, ours):
    """Ours beside the pair from the Seam and the Homing Pearl, at 8x, on an inventory slot's grey and on a dark field."""
    others = [Image.open(os.path.join(ITEMS, n + '.png')).convert('RGBA')
              for n in ('homing_pearl', 'mending_thread', 'severing_shears')]
    icons = others + ours
    S, gap = 8, 8
    w = len(icons) * (16 * S + gap) + gap
    sheet = Image.new('RGBA', (w, 2 * (16 * S + gap) + gap + 40), (139, 139, 139, 255))
    dark = Image.new('RGBA', (w, 16 * S + 2 * gap), (32, 30, 40, 255))
    sheet.alpha_composite(dark, (0, 16 * S + gap + 4))
    for i, im in enumerate(icons):
        x = gap + i * (16 * S + gap)
        sheet.alpha_composite(im.resize((16 * S, 16 * S), Image.NEAREST), (x, gap))
        sheet.alpha_composite(im.resize((16 * S, 16 * S), Image.NEAREST), (x, 16 * S + 2 * gap + 4))
    # and at 1x and 2x along the bottom, as they show in a slot
    for i, im in enumerate(icons):
        sheet.alpha_composite(im, (gap + i * 20, 2 * (16 * S + gap) + gap + 6))
        sheet.alpha_composite(im.resize((32, 32), Image.NEAREST), (gap + len(icons) * 20 + 10 + i * 36, 2 * (16 * S + gap) + gap + 4))
    sheet.save(path)


def main():
    keystone = draw(KEYSTONE, KEYSTONE_PAL)
    lock = draw(LOCK, LOCK_PAL)
    for name, im in (('homeward_keystone', keystone), ('pale_lock', lock)):
        path = os.path.join(ITEMS, name + '.png')
        im.save(path)
        print('wrote', os.path.normpath(path))
    if len(sys.argv) > 1:
        preview(sys.argv[1], [keystone, lock])
        print('wrote', sys.argv[1])


if __name__ == '__main__':
    main()
