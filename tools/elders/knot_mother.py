# Draws the Knot Mother's textures (2.39) from the Last Weaver's: the same shape, frayed to the colours of the deep.
# The violet of the Weaver turns to the sea-green of the Fray, the body a little darker and worn, the light pale.
# Usage: python3 tools/elders/knot_mother.py src/client/resources/assets/extremedimensions/textures/entity/elder
import colorsys, os, random, sys
from PIL import Image

SHIFT = -0.30  # violet (about 0.75) to sea-green (about 0.45)

def recolour(src, dst, darker, glow):
    im = Image.open(src).convert("RGBA")
    px = im.load()
    rnd = random.Random(7)
    w, h = im.size
    for y in range(h):
        for x in range(w):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            hh, ss, vv = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
            hh = (hh + SHIFT) % 1.0
            if glow:
                ss *= 0.7
            else:
                ss = min(1.0, ss * 0.85)
                vv *= darker
                # worn: here and there a thread of it frayed darker
                if rnd.random() < 0.06:
                    vv *= 0.8
            r2, g2, b2 = colorsys.hsv_to_rgb(hh, ss, vv)
            px[x, y] = (int(r2 * 255), int(g2 * 255), int(b2 * 255), a)
    im.save(dst)

def main(folder):
    recolour(os.path.join(folder, "weaver.png"), os.path.join(folder, "knot_mother.png"), 0.82, False)
    for suffix in ["_glow", "_glow_blue", "_glow_crimson", "_glow_silver"]:
        recolour(os.path.join(folder, "weaver" + suffix + ".png"), os.path.join(folder, "knot_mother" + suffix + ".png"), 1.0, True)

if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else "src/client/resources/assets/extremedimensions/textures/entity/elder")
