# Draws the 3.0 textures: the six threads of the Tapestry (a weaver's shuttle carrying a bobbin of thread in the colours
# of the deed that earned it), the Weaver's Loom (hush-pillar posts, a warp of seam silk, a band already woven) and the
# tapestry screen's sheet (six woven panels and the bare warp of one not yet woven, 64x64 each).
# Usage: python3 tools/items/tapestry.py   (from the repository root; reads the hush pillar's textures)
import math, os, random
from PIL import Image, ImageDraw

ROOT = "src/client/resources/assets/extremedimensions/textures"

# deed colours: k outline, d dark, m mid, l light
PANELS = {
    "roots": {"k": (22, 30, 12), "d": (62, 88, 30), "m": (118, 156, 58), "l": (196, 226, 128)},        # the Matriarch's roots
    "toll": {"k": (12, 30, 30), "d": (22, 104, 98), "m": (58, 182, 168), "l": (170, 246, 230)},        # the Ferryman's soul fire
    "unravelling": {"k": (34, 24, 52), "d": (110, 90, 168), "m": (186, 168, 240), "l": (244, 238, 255)},  # the Last Weaver's silk
    "chain": {"k": (26, 24, 28), "d": (96, 92, 100), "m": (162, 158, 166), "l": (224, 220, 214)},       # the Held's chain
    "knot": {"k": (10, 30, 26), "d": (40, 102, 86), "m": (110, 196, 168), "l": (196, 246, 228)},        # the Knot Mother's deep
    "heart": {"k": (40, 8, 14), "d": (128, 22, 40), "m": (210, 58, 78), "l": (255, 178, 186)},          # the ending
}
ITEMS = {"roots": "thread_of_roots", "toll": "thread_of_toll", "unravelling": "thread_of_unravelling",
         "chain": "thread_of_the_chain", "knot": "thread_of_the_knot", "heart": "thread_of_the_heart"}
WOOD = {"k": (36, 22, 12), "d": (92, 58, 30), "m": (146, 98, 54), "l": (196, 148, 92)}

SHUTTLE = [
    "................",
    "................",
    "...........kk...",
    ".........kklwk..",
    ".......kklwwmk..",
    ".....kkwmTTTmk..",
    "...kkwmTTTTTk...",
    "..kwmmTTTTTmk...",
    "..kmmTTTTTmk....",
    "..kdmTTTmmk.....",
    "...kdmmmmk.t....",
    "....kkddk..t....",
    "......kk....t...",
    "............t...",
    ".............t..",
    "................",
]


def shuttle(pal):
    """A weaver's shuttle seen from above, its bobbin wound with the deed's thread (T), a loose end trailing (t)."""
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = im.load()
    for y, row in enumerate(SHUTTLE):
        for x, ch in enumerate(row):
            if ch == ".":
                continue
            if ch in "Tt":
                # the thread: bands as wound thread lies, lit from the upper left
                if ch == "t":
                    c = pal["m"]
                else:
                    band = (x + y) % 3
                    light = (x - y) > 2
                    c = pal["l"] if band == 0 and light else pal["m"] if band != 2 else pal["d"]
            else:
                c = {"k": WOOD["k"], "d": WOOD["d"], "m": WOOD["m"], "l": WOOD["l"], "w": (232, 220, 196)}[ch]
            px[x, y] = c + (255,)
    return im


def loom_textures(out):
    side = Image.open(os.path.join(ROOT, "block", "hush_pillar.png")).convert("RGBA")
    top = Image.open(os.path.join(ROOT, "block", "hush_pillar_top.png")).convert("RGBA")
    silk = PANELS["unravelling"]
    # front and back: two posts and a beam of hush pillar, a warp of silk between, a band woven in the middle
    front = side.copy()
    fp = front.load()
    for y in range(3, 16):
        for x in range(3, 13):
            warp = x % 2 == 1
            if 7 <= y <= 10:
                # the woven band: the weft over and under the warp, in the six deeds' colours across
                deed = list(PANELS.values())[(x - 3) * 6 // 10]
                over = (x + y) % 2 == 0
                fp[x, y] = (deed["m"] if over else deed["d"]) + (255,)
            elif warp:
                fp[x, y] = (silk["l"] if y % 4 else silk["m"]) + (255,)
            else:
                fp[x, y] = (24, 18, 32, 255)
    for x in range(3, 13):
        fp[x, 2] = (58, 48, 70, 255)
        fp[x, 15] = (40, 32, 50, 255)
    front.save(os.path.join(ROOT, "block", "weavers_loom_front.png"))
    # sides: the pillar, a strip of silk running down where the warp is tied off
    s2 = side.copy()
    sp = s2.load()
    for y in range(16):
        sp[7, y] = (silk["m"] if y % 3 else silk["l"]) + (255,)
        sp[8, y] = (silk["d"]) + (255,)
    s2.save(os.path.join(ROOT, "block", "weavers_loom_side.png"))
    # top: the pillar's top crossed by the cloth beam, a roll of woven cloth
    t2 = top.copy()
    tp = t2.load()
    for x in range(16):
        for y in range(6, 10):
            deed = list(PANELS.values())[(x * 6) // 16]
            tp[x, y] = (deed["l"] if y == 6 else deed["m"] if y < 9 else deed["d"]) + (255,)
    t2.save(os.path.join(ROOT, "block", "weavers_loom_top.png"))
    b2 = top.copy()
    bp = b2.load()
    for y in range(16):
        for x in range(16):
            r, g, b, a = bp[x, y]
            bp[x, y] = (int(r * 0.7), int(g * 0.7), int(b * 0.7), a)
    b2.save(os.path.join(ROOT, "block", "weavers_loom_bottom.png"))


def cloth(pal, rnd, woven=True):
    """64x64 of cloth: warp and weft over and under; bare, only the warp on the dark."""
    im = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    px = im.load()
    base = (226, 212, 180) if woven else (20, 16, 28)
    for y in range(64):
        for x in range(64):
            if not woven:
                if x % 4 == 1:
                    k = 0.75 + 0.25 * math.sin(y * 0.3 + x)
                    px[x, y] = tuple(int(c * k) for c in (168, 150, 210)) + (255,)
                else:
                    px[x, y] = base + (230,)
                continue
            over = (x // 2 + y // 2) % 2 == 0
            k = 0.92 if over else 0.8
            k += rnd.uniform(-0.03, 0.03)
            px[x, y] = tuple(max(0, min(255, int(c * k))) for c in base) + (255,)
    return im


def motif(name, pal):
    """What the panel shows, drawn coarse, as a weave shows it."""
    im = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    k, dk, m, l = pal["k"], pal["d"], pal["m"], pal["l"]
    if name == "roots":
        # roots climbing into arches
        for i, x0 in enumerate((14, 32, 50)):
            d.line([(x0, 60), (x0 - 2, 44), (x0 + 3, 30), (32, 14)], fill=dk, width=4)
            d.line([(x0, 60), (x0 - 2, 44), (x0 + 3, 30), (32, 14)], fill=m, width=2)
        d.arc([10, 6, 54, 50], 200, 340, fill=l, width=3)
        d.ellipse([28, 10, 36, 18], fill=l, outline=k)
    elif name == "toll":
        # a ferry on a river of fire, a coin over it
        d.rectangle([4, 46, 60, 58], fill=(196, 92, 30))
        for x in range(4, 60, 6):
            d.line([(x, 48), (x + 3, 46)], fill=(255, 196, 80), width=1)
        d.polygon([(12, 42), (52, 42), (46, 50), (18, 50)], fill=dk, outline=k)
        d.line([(30, 42), (24, 14)], fill=k, width=2)
        d.ellipse([36, 8, 52, 24], fill=l, outline=k)
        d.ellipse([41, 13, 47, 19], outline=dk)
    elif name == "unravelling":
        # a halo with threads drawn out of it
        d.ellipse([16, 10, 48, 42], outline=dk, width=4)
        d.ellipse([18, 12, 46, 40], outline=l, width=2)
        for a in range(0, 360, 30):
            r = math.radians(a)
            d.line([(32 + math.cos(r) * 15, 26 + math.sin(r) * 15), (32 + math.cos(r) * 28, 26 + math.sin(r) * 28)], fill=m, width=1)
        d.line([(32, 42), (32, 60)], fill=m, width=2)
    elif name == "chain":
        # a chain of links, the last one broken
        for i in range(5):
            y = 8 + i * 10
            box = [24, y, 40, y + 12] if i % 2 == 0 else [28, y, 36, y + 12]
            d.ellipse(box, outline=k, width=4)
            d.ellipse(box, outline=m if i < 4 else l, width=2)
        d.line([(40, 56), (48, 60)], fill=l, width=2)
    elif name == "knot":
        # a great knot of loops
        for box in ([12, 12, 40, 40], [24, 12, 52, 40], [18, 24, 46, 52]):
            d.ellipse(box, outline=k, width=5)
            d.ellipse(box, outline=m, width=3)
        d.ellipse([28, 26, 36, 34], fill=l, outline=k)
    elif name == "heart":
        # a heart with a thread through it
        d.polygon([(32, 54), (10, 30), (12, 18), (22, 12), (32, 20), (42, 12), (52, 18), (54, 30)], fill=m, outline=k)
        d.polygon([(32, 48), (16, 30), (18, 22), (24, 18), (32, 26)], fill=l)
        d.line([(4, 50), (60, 14)], fill=(244, 238, 255), width=2)
    return im


def sheet(out):
    rnd = random.Random(30)
    tiles = []
    for name, pal in PANELS.items():
        base = cloth(pal, rnd)
        art = motif(name, pal)
        # woven: the motif takes the cloth's over-and-under
        bp, ap = base.load(), art.load()
        for y in range(64):
            for x in range(64):
                if ap[x, y][3] > 0:
                    over = (x // 2 + y // 2) % 2 == 0
                    k = 1.0 if over else 0.82
                    r, g, b, a = ap[x, y]
                    bp[x, y] = (int(r * k), int(g * k), int(b * k), 255)
        # a border in the deed's dark colour
        d = ImageDraw.Draw(base)
        d.rectangle([0, 0, 63, 63], outline=pal["d"], width=2)
        tiles.append(base)
    tiles.append(cloth(PANELS["unravelling"], rnd, woven=False))
    out_im = Image.new("RGBA", (64 * len(tiles), 64), (0, 0, 0, 0))
    for i, t in enumerate(tiles):
        out_im.paste(t, (i * 64, 0))
    os.makedirs(os.path.join(ROOT, "gui"), exist_ok=True)
    out_im.save(os.path.join(ROOT, "gui", "tapestry.png"))


def main():
    for name, item in ITEMS.items():
        shuttle(PANELS[name]).save(os.path.join(ROOT, "item", item + ".png"))
    loom_textures(ROOT)
    sheet(ROOT)


if __name__ == "__main__":
    main()
