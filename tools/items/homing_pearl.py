"""The Homing Pearl's item texture (16x16): a pearl of the void, violet, a gold band round it and a pale glint."""
import math
import os

from PIL import Image

OUT = os.path.join(os.path.dirname(__file__), "..", "..", "src/client/resources/assets/extremedimensions/textures/item/homing_pearl.png")

DARK = (34, 14, 58)
DEEP = (70, 28, 120)
MID = (122, 58, 196)
LIGHT = (176, 120, 238)
GLINT = (236, 220, 255)
GOLD = (222, 178, 74)
GOLD_DARK = (150, 106, 40)
OUTLINE = (20, 8, 34)


def main():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    cx, cy, r = 7.5, 7.5, 6.2
    for y in range(16):
        for x in range(16):
            dx, dy = x - cx, y - cy
            d = math.hypot(dx, dy)
            if d > r + 0.6:
                continue
            if d > r - 0.4:
                px[x, y] = OUTLINE + (255,)
                continue
            # light from the upper left
            shade = (-dx * 0.55 - dy * 0.75) / r
            k = 0.5 + 0.5 * shade - 0.25 * (d / r) ** 2
            col = DARK if k < 0.18 else DEEP if k < 0.38 else MID if k < 0.62 else LIGHT
            px[x, y] = col + (255,)
    # a gold band slanting across it, broken (as the halo is)
    for x in range(16):
        y = round(7.5 + (x - 7.5) * 0.35)
        for yy in (y, y + 1):
            if 0 <= yy < 16 and px[x, yy][3] and px[x, yy][:3] != OUTLINE and x not in (5, 6):
                px[x, yy] = (GOLD if yy == y else GOLD_DARK) + (255,)
    # the glint, and a mote of the void at its heart
    for gx, gy in ((5, 4), (4, 5), (5, 5)):
        px[gx, gy] = GLINT + (255,)
    px[9, 9] = (12, 4, 22, 255)
    px[10, 9] = (44, 20, 80, 255)
    img.save(OUT)
    print("wrote", os.path.normpath(OUT))


if __name__ == "__main__":
    main()
