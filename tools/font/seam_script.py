"""The Seam script: the invented letters an unknown curse is written in (see CurseRunes), drawn pixel by pixel.

Every letter is a thread hanging the height of a capital, with what the Weavers tied into it: knots (a ring the thread
runs through), beads (where it thickens), side knots, loose dots and cuts. No real alphabet, Futhark least of all.

The letters go into the game's own font at Private Use Area code points, so curse text stays a plain string: a to z
from FIRST, then 0 to 9. Each sits in an 8x8 cell, 5 wide and 7 tall on the baseline; a pixel of alpha 1 (the text
shader drops it) in the cell's last column keeps every letter 6 wide, so the threads stand evenly spaced.

usage: python3 tools/font/seam_script.py [preview.png]
"""
import json
import os
import sys

from PIL import Image

ROOT = os.path.join(os.path.dirname(__file__), "..", "..")
PNG = os.path.join(ROOT, "src/client/resources/assets/extremedimensions/textures/font/seam_script.png")
FONT = os.path.join(ROOT, "src/client/resources/assets/minecraft/font/default.json")

# Must match CurseRunes.FIRST
FIRST = 0xEA00
INK = (255, 255, 255, 255)
SPACER = (255, 255, 255, 1)

# a to z, then 0 to 9; ten letters to a band, side by side
LETTERS = """
.###.|..#..|..#..|..#..|#####|..#..|..#..|.##..|..#..|..#..
.###.|..#..|..#..|#.#.#|#.#.#|..#..|..#..|.##..|..##.|..#..
..#..|..##.|.##..|..#..|#####|..#..|#####|..#..|..##.|.###.
#.#.#|..##.|.##..|.....|..#..|..#..|#.#.#|..#..|..#..|.###.
..#..|..#..|..#..|..#..|..#..|#####|#####|..##.|..#..|..#..
..#..|..#..|..#..|#.#.#|..#..|#.#.#|..#..|..##.|.##..|..#..
..#..|..#..|..#..|..#..|..#..|#####|..#..|..#..|.##..|..#..

.###.|..#..|#.#..|..#.#|..#..|.##..|.###.|..#..|#####|..#..
.###.|..#..|..#..|..#..|..##.|.##..|.###.|#.#.#|#.#.#|#.#.#
..#..|..#..|#.#..|..#.#|..##.|..#..|..#..|..#..|#####|..#..
..#..|#.#.#|..#..|..#..|..#..|.....|..#..|#####|..#..|.....
..#..|..#..|#.#..|..#.#|..##.|..#..|..#..|#.#.#|..#..|..#..
.###.|.###.|..#..|..#..|..##.|..##.|..#..|#####|.###.|..##.
.###.|.###.|..#..|..#..|..#..|..##.|#.#.#|..#..|.###.|..##.

.###.|..#..|#####|#...#|.##..|..#..|..#..|..#..|.###.|..#..
.###.|.##..|#.#.#|..#..|.##..|#.#.#|..#..|.###.|..#..|.###.
..#..|.##..|#####|..#..|..#..|..#..|.###.|..#..|.###.|..#..
.....|..#..|.....|.##..|.###.|..#..|..#..|..#..|..#..|.###.
..#..|..##.|..#..|.##..|..#..|#.#.#|.###.|..#..|.###.|..#..
#.#.#|..##.|.###.|..#..|..##.|..#..|..#..|.###.|..#..|.###.
..#..|..#..|.###.|#...#|..##.|..#..|..#..|..#..|.###.|..#..

..#..|#.#.#|..#..|#.#.#|.###.|..#..
..#..|..#..|#####|..#..|.###.|..##.
#####|#.#.#|#.#.#|..#..|.....|..##.
#.#.#|..#..|#####|.###.|.###.|..#..
#####|.....|..#..|..#..|.###.|#.#.#
..#..|..#..|..#..|..#..|..#..|..#..
.###.|..#..|.###.|#.#.#|..#..|..#..
"""


def letters():
    out = []
    for band in LETTERS.strip().split("\n\n"):
        rows = [r.split("|") for r in band.strip().split("\n")]
        for i in range(len(rows[0])):
            out.append([r[i] for r in rows])
    assert len(out) == 36, len(out)
    assert len({"".join(g) for g in out}) == 36, "two letters are drawn alike"
    return out


def atlas(glyphs):
    img = Image.new("RGBA", (6 * 8, 6 * 8), (0, 0, 0, 0))
    for i, g in enumerate(glyphs):
        ox, oy = (i % 6) * 8, (i // 6) * 8
        for y, row in enumerate(g):
            for x, c in enumerate(row):
                if c == "#":
                    img.putpixel((ox + x, oy + y), INK)
        if img.getpixel((ox + 4, oy + 7))[3] == 0:
            img.putpixel((ox + 4, oy + 7), SPACER)
    return img


if __name__ == "__main__":
    glyphs = letters()
    os.makedirs(os.path.dirname(PNG), exist_ok=True)
    os.makedirs(os.path.dirname(FONT), exist_ok=True)
    atlas(glyphs).save(PNG)
    chars = ["".join(chr(FIRST + r * 6 + c) for c in range(6)) for r in range(6)]
    provider = {"type": "bitmap", "file": "extremedimensions:font/seam_script.png", "ascent": 7, "height": 8, "chars": chars}
    with open(FONT, "w") as f:
        json.dump({"providers": [provider]}, f, indent=2)
        f.write("\n")
    if len(sys.argv) > 1:
        sheet = Image.new("RGBA", (10 * 7 * 8 + 8, 4 * 10 * 8), (30, 26, 34, 255))
        for i, g in enumerate(glyphs):
            ox, oy = 8 + (i % 10) * 56, 8 + (i // 10) * 80
            for y, row in enumerate(g):
                for x, c in enumerate(row):
                    if c == "#":
                        for dy in range(8):
                            for dx in range(8):
                                sheet.putpixel((ox + x * 8 + dx, oy + y * 8 + dy), (230, 220, 200, 255))
        sheet.save(sys.argv[1])
