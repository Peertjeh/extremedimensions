"""Curse knots, drawn as 16 px block textures: the knots the Weaver's Loom ties while a curse set is unwoven.

Each set has its own colour (Beyond ember-red, Overworld soul-cyan, Everywhere void-magenta, Extreme blood-red), the same
colours the Curses screen gives the sets. A knot is two pairs of rope crossing on the diagonal over a near-black weave,
over and under in turn so the pattern tiles, with a swollen knot at the heart and a few sparks of the curse in the rope.
Lit from the top left; every strand is edged in the darkest tone.

usage: python3 tools/items/curse_knot.py [preview.png]
"""
import math
import os
import sys

from PIL import Image

ROOT = os.path.join(os.path.dirname(__file__), "..", "..")
ASSETS = os.path.join(ROOT, "src/client/resources/assets/extremedimensions")

# set -> (rope in light, rope, rope in shadow), in the order of Unweaving.SETS
SETS = {
    "beyond": ((255, 150, 120), (226, 72, 64), (110, 22, 30)),
    "overworld": ((190, 255, 250), (70, 214, 222), (16, 84, 104)),
    "everywhere": ((255, 176, 255), (214, 74, 214), (88, 20, 104)),
    "extreme": ((236, 92, 72), (164, 18, 22), (64, 6, 12)),
}
EDGE = (12, 6, 14)
WEAVE = ((20, 12, 24), (28, 18, 34))
PERIOD = 8
RADIUS = 1.75


def mix(a, b, t):
    t = max(0.0, min(1.0, t))
    return tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(3))


def strand(u):
    """Distance from u (along the axis across the strands) to the nearest strand centre, and that strand's index."""
    k = round(u / PERIOD)
    return abs(u - k * PERIOD), k


def texture(name):
    light, rope, shadow = SETS[name]
    img = Image.new("RGB", (16, 16))
    for y in range(16):
        for x in range(16):
            px, py = x + 0.5, y + 0.5
            # the weave behind: a fine twill
            colour = WEAVE[(x + 2 * y) % 3 == 0]
            a = px - py          # '/' strands lie along a = const
            b = px + py          # '\' strands lie along b = const
            da, ia = strand(a)
            db, ib = strand(b)
            da /= math.sqrt(2)
            db /= math.sqrt(2)
            on_a, on_b = da < RADIUS + 0.6, db < RADIUS + 0.6
            if on_a or on_b:
                if on_a and on_b:
                    top_a = (ia + ib) % 2 == 0
                elif on_a:
                    top_a = True
                else:
                    top_a = False
                d = da if top_a else db
                if d >= RADIUS:
                    colour = EDGE
                else:
                    t = d / RADIUS
                    base = mix(light, rope, t * 1.4) if t < 0.5 else mix(rope, shadow, (t - 0.5) * 2)
                    # twist: bands across the rope, every third pixel along it a shade darker
                    along = (px + py) if top_a else (px - py)
                    if int(math.floor(along)) % 3 == 0:
                        base = mix(base, shadow, 0.35)
                    colour = base
                    # the strand under a crossing lies in the top one's shadow
                    if not top_a and on_a and on_b:
                        colour = mix(colour, shadow, 0.25)
            # the knot at the heart of the tile
            r = math.hypot(px - 8, py - 8)
            if r < 2.6:
                colour = EDGE if r > 2.1 else mix(light, rope, r / 2.6 * 1.2) if r > 0.9 else light
            img.putpixel((x, y), colour)
    # sparks of the curse, a glint on the knot
    for sx, sy in ((7, 7), (3, 12), (12, 3)):
        img.putpixel((sx, sy), mix(light, (255, 255, 255), 0.55))
    return img


def write_json(path, text):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        f.write(text)


def main():
    names = list(SETS)
    tiles = []
    for name in names:
        img = texture(name)
        tiles.append(img)
        img.save(os.path.join(ASSETS, "textures/block/curse_knot_%s.png" % name))
        write_json(os.path.join(ASSETS, "models/block/curse_knot_%s.json" % name),
                   '{\n  "parent": "minecraft:block/cube_all",\n  "textures": {\n    "all": "extremedimensions:block/curse_knot_%s"\n  }\n}\n' % name)
    variants = ",\n".join('    "set=%d": {\n      "model": "extremedimensions:block/curse_knot_%s"\n    }' % (i, n) for i, n in enumerate(names))
    write_json(os.path.join(ASSETS, "blockstates/curse_knot.json"), '{\n  "variants": {\n%s\n  }\n}\n' % variants)
    if len(sys.argv) > 1:
        scale = 12
        sheet = Image.new("RGB", (len(tiles) * (32 * scale + 12), 32 * scale), (40, 40, 40))
        for i, t in enumerate(tiles):
            tiled = Image.new("RGB", (32, 32))
            for ox in (0, 16):
                for oy in (0, 16):
                    tiled.paste(t, (ox, oy))
            sheet.paste(tiled.resize((32 * scale, 32 * scale), Image.NEAREST), (i * (32 * scale + 12), 0))
        sheet.save(sys.argv[1])


if __name__ == "__main__":
    main()
