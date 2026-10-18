"""The Elders' spells as status-effect icons (18x18, the vanilla size), drawn pixel by pixel.

usage: python3 tools/effects/icons.py [preview.png]
"""
import os
import sys

from PIL import Image

ROOT = os.path.join(os.path.dirname(__file__), "..", "..")
OUT = os.path.join(ROOT, "src/client/resources/assets/extremedimensions/textures/mob_effect")

# The Matriarch's hex: a ring of thorns round her violet eye
HEX = (
    "..................",
    "......k....k......",
    ".....kGk..kGk.....",
    "..kk.kgGkkGgk.kk..",
    "..kGkkgggggggkGk..",
    "...kGgk.....kgGk..",
    "..kggk..kk..kggk..",
    ".kGgk..kvvk..kgGk.",
    "kGggk.kvVVvk.kggGk",
    "kGggk.kvVwvk.kggGk",
    ".kGgk..kvvk..kgGk.",
    "..kggk..kk..kggk..",
    "...kGgk.....kgGk..",
    "..kGkkgggggggkGk..",
    "..kk.kgGkkGgk.kk..",
    ".....kGk..kGk.....",
    "......k....k......",
    "..................",
)
HEX_COLOURS = {"k": (22, 18, 14), "g": (47, 90, 30), "G": (112, 170, 62), "v": (106, 52, 160), "V": (176, 120, 240),
               "w": (244, 228, 255)}

# The Ferryman's weight: an iron weight of the Styx on a chain, soul light in its seam
WEIGHT = (
    "........kk........",
    ".......kcck.......",
    "........kk........",
    ".......kcck.......",
    "........kk........",
    ".......kkkk.......",
    "......kSddSk......",
    ".....kkkkkkkk.....",
    ".....kTTTTTdk.....",
    "....kTtTTTTTdk....",
    "....kTTTTTTTdk....",
    "...ksssssssssk....",
    "...kTTTTTTTTTdk...",
    "..kTtTTTTTTTTdk...",
    "..kTTTTTTTTTTTdk..",
    "..kddddddddddddk..",
    "...kkkkkkkkkkkk...",
    "..................",
)
WEIGHT_COLOURS = {"k": (12, 22, 24), "c": (120, 128, 136), "S": (58, 74, 80), "d": (22, 52, 56), "T": (42, 104, 110),
                  "t": (118, 200, 196), "s": (110, 236, 214)}

# Bound wings: folded elytra with a chain across them
BOUND = (
    "..................",
    "....kk......kk....",
    "...kEek....keEk...",
    "...kEeek..keeEk...",
    "..kEeeek..keeeEk..",
    "..kEeeeekkeeeeEk..",
    "..kEeeeekkeeeeEk..",
    "kkkkkkkkkkkkkkkkkk",
    "cCkcCkcCkcCkcCkcCk",
    "kkkkkkkkkkkkkkkkkk",
    "..kEeeeekkeeeeEk..",
    "..kEeeedkkdeeeEk..",
    "...kEeedkkdeeEk...",
    "...kEedk..kdeEk...",
    "....kEdk..kdEk....",
    "....kEk....kEk....",
    ".....k......k.....",
    "..................",
)
BOUND_COLOURS = {"k": (20, 16, 26), "E": (176, 166, 196), "e": (118, 104, 140), "d": (78, 66, 98), "c": (98, 104, 112),
                 "C": (176, 182, 190)}


def draw(rows, colours):
    assert len(rows) == 18 and all(len(r) == 18 for r in rows), [len(r) for r in rows]
    img = Image.new("RGBA", (18, 18), (0, 0, 0, 0))
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch != ".":
                img.putpixel((x, y), colours[ch] + (255,))
    return img


ICONS = {"matriarch_hex": (HEX, HEX_COLOURS), "styx_weight": (WEIGHT, WEIGHT_COLOURS), "bound_wings": (BOUND, BOUND_COLOURS)}

if __name__ == "__main__":
    os.makedirs(OUT, exist_ok=True)
    imgs = []
    for name, (rows, colours) in ICONS.items():
        img = draw(rows, colours)
        img.save(os.path.join(OUT, name + ".png"))
        imgs.append(img)
    if len(sys.argv) > 1:
        sheet = Image.new("RGBA", (18 * 3 * 10 + 40, 18 * 10 + 20), (48, 48, 56, 255))
        for i, img in enumerate(imgs):
            sheet.alpha_composite(img.resize((180, 180), Image.NEAREST), (10 + i * 190, 10))
        sheet.save(sys.argv[1])
