"""The Blood Cord, drawn as a 16 px item: the tribe's cord, a dark red rope looped once round on itself and tied in a
swollen knot, its two ends hanging free and frayed, a drop of blood at one of them. Lit from the top left; every strand
is edged in the darkest tone, as Minecraft's own items are.

Retired: tools/items/after_vanilla.py draws the cord now, after vanilla's lead. This script no longer writes it; given
a path it previews this old drawing there.

usage: python3 tools/items/blood_cord.py [preview.png]
"""
import math
import sys

from PIL import Image


EDGE = (38, 6, 12)
DARK = (92, 14, 24)
ROPE = (148, 26, 38)
LIT = (196, 58, 64)
GLINT = (236, 128, 120)
BLOOD = (170, 10, 22)
BLOOD_LIT = (232, 60, 60)


def main(preview=None):
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = im.load()
    rope = set()
    # the loop: an ellipse round the upper middle
    for i in range(160):
        a = i / 160 * math.tau
        x = 7.6 + 4.6 * math.cos(a)
        y = 6.2 + 3.9 * math.sin(a)
        rope.add((round(x), round(y)))
    # the two ends, hanging from the knot at the bottom of the loop, down to the left and the right
    for t in range(9):
        rope.add((round(7 - t * 0.55), round(10 + t * 0.62)))
        rope.add((round(9 + t * 0.5), round(10 + t * 0.6)))
    # the knot: a swollen lump where the loop closes
    knot = {(x, y) for x in range(6, 11) for y in range(9, 12) if (x - 8) ** 2 / 6.5 + (y - 10) ** 2 / 2.2 <= 1.0}
    body = rope | knot
    for (x, y) in body:
        if 0 <= x < 16 and 0 <= y < 16:
            px[x, y] = ROPE + (255,)
    # shade: lit where the outside lies up and to the left, dark where it lies down and to the right
    for (x, y) in body:
        if not (0 <= x < 16 and 0 <= y < 16):
            continue
        up_left = (x - 1, y) not in body or (x, y - 1) not in body
        down_right = (x + 1, y) not in body or (x, y + 1) not in body
        if up_left and not down_right:
            px[x, y] = LIT + (255,)
        elif down_right and not up_left:
            px[x, y] = DARK + (255,)
    # twists along the rope, one in three
    for (x, y) in rope - knot:
        if (x * 3 + y * 5) % 3 == 0 and 0 <= x < 16 and 0 <= y < 16:
            px[x, y] = DARK + (255,)
    # the knot's glint
    px[7, 9] = GLINT + (255,)
    px[8, 9] = LIT + (255,)
    # the outline round everything
    for y in range(16):
        for x in range(16):
            if px[x, y][3]:
                continue
            if any((x + dx, y + dy) in body for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                px[x, y] = EDGE + (255,)
    # frayed ends and a drop of blood under the right one
    for (x, y) in ((2, 15), (12, 15)):
        if 0 <= x < 16:
            px[x, y] = DARK + (255,)
    px[13, 13] = BLOOD + (255,)
    px[13, 14] = BLOOD_LIT + (255,)
    if preview:
        im.resize((256, 256), Image.NEAREST).save(preview)


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else None)
