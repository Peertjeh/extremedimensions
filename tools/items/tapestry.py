# Draws the 3.0 textures: the six threads of the Tapestry (a weaver's shuttle carrying a bobbin of thread in the colours
# of the deed that earned it), the Weaver's Loom (hush-pillar posts, a warp of seam silk, a band already woven) and the
# tapestry screen's sheet (six woven panels and the bare warp of one not yet woven, 40x40 each, drawn 1:1).
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


T = 40  # a panel of the tapestry screen, drawn 1:1 in the screen's own pixels

# what each panel shows, lit from the upper left: k outline, d shade, m body, l light, h highlight
PALETTES = {
    "bark": {"k": (26, 18, 10), "d": (72, 48, 26), "m": (112, 78, 44), "l": (156, 116, 70), "h": (196, 160, 110)},
    "leaf": {"k": (14, 30, 10), "d": (52, 98, 34), "m": (92, 150, 52), "l": (150, 206, 92), "h": (210, 244, 160)},
    "hull": {"k": (20, 14, 10), "d": (58, 40, 28), "m": (92, 66, 44), "l": (128, 96, 64), "h": (170, 132, 92)},
    "soul": {"k": (6, 34, 34), "d": (18, 110, 104), "m": (52, 188, 172), "l": (150, 240, 222), "h": (230, 255, 250)},
    "gold": {"k": (48, 30, 6), "d": (148, 98, 22), "m": (212, 160, 48), "l": (248, 214, 112), "h": (255, 246, 200)},
    "silk": {"k": (36, 24, 60), "d": (112, 88, 176), "m": (172, 150, 232), "l": (220, 206, 255), "h": (255, 252, 255)},
    "wood": {"k": (34, 22, 12), "d": (96, 62, 32), "m": (146, 100, 56), "l": (192, 146, 92), "h": (226, 190, 140)},
    "iron": {"k": (18, 18, 24), "d": (70, 70, 82), "m": (124, 124, 138), "l": (186, 186, 198), "h": (240, 240, 246)},
    "deep": {"k": (6, 26, 22), "d": (26, 94, 78), "m": (62, 162, 136), "l": (130, 222, 192), "h": (210, 255, 238)},
    "blood": {"k": (40, 6, 12), "d": (122, 18, 36), "m": (196, 44, 64), "l": (240, 104, 116), "h": (255, 212, 214)},
    "thread": {"k": (60, 40, 20), "d": (190, 160, 110), "m": (236, 220, 184), "l": (255, 248, 228), "h": (255, 255, 255)},
}
# the woven ground behind each panel's picture, from the deed's own colour
GROUNDS = {"roots": (34, 44, 26), "toll": (20, 40, 44), "unravelling": (40, 32, 62), "chain": (54, 32, 26), "knot": (18, 42, 40),
           "heart": (54, 22, 30)}
FRAME_DARK, GOLD_D, GOLD_L = (14, 10, 20), (150, 112, 44), (226, 190, 104)


def clamp(c):
    return tuple(max(0, min(255, int(round(v)))) for v in c)


def scale(c, k):
    return clamp(tuple(v * k for v in c))


class Panel:
    """A 40x40 panel: a woven ground in a braided frame, and what is drawn on it, layer over layer."""

    def __init__(self, ground, woven=True):
        self.px = [[None] * T for _ in range(T)]
        for y in range(T):
            for x in range(T):
                if woven:
                    # a twill: the weft rides over two warp threads and steps one along each row
                    k = 1.06 if (x + y) % 4 < 2 else 0.92
                    k *= 0.96 if y % 2 else 1.0
                    # darker toward the frame, as a hanging gathers shadow at its edges
                    e = min(x, y, T - 1 - x, T - 1 - y)
                    k *= 0.78 + 0.22 * min(1.0, e / 9.0)
                    self.px[y][x] = scale(ground, k)
                else:
                    # only the warp, strung on the dark: threads every third column, catching the light unevenly
                    if x % 3 == 1:
                        k = 0.72 + 0.2 * math.sin(y * 0.45 + x * 1.7)
                        self.px[y][x] = scale((176, 160, 214), k)
                    else:
                        self.px[y][x] = (16, 12, 22)
        self.frame(woven)

    def frame(self, woven):
        for i in range(T):
            for (x, y) in ((i, 0), (i, T - 1), (0, i), (T - 1, i)):
                self.px[y][x] = FRAME_DARK
        for i in range(1, T - 1):
            # a braided cord of gold thread: light and shade by turns, as the strands twist
            c = (GOLD_L if (i // 2) % 2 == 0 else GOLD_D) if woven else ((110, 104, 120) if (i // 2) % 2 == 0 else (70, 66, 80))
            for (x, y) in ((i, 1), (i, T - 2), (1, i), (T - 2, i)):
                self.px[y][x] = c
        for (x, y) in ((1, 1), (T - 2, 1), (1, T - 2), (T - 2, T - 2)):
            # a knot at each corner
            self.px[y][x] = (255, 236, 170) if woven else (150, 144, 160)
        for i in range(2, T - 2):
            for (x, y) in ((i, 2), (2, i)):
                self.px[y][x] = scale(self.px[y][x], 0.7)

    def layer(self, mask, pal, weave=True, shade=True, outline=True, sparkle=()):
        """Draws one shape: its outline outside it, then its body lit from the upper left."""
        def inside(x, y):
            return 0 <= x < T and 0 <= y < T and mask[y][x]
        if outline:
            for y in range(T):
                for x in range(T):
                    if not mask[y][x] and any(inside(x + dx, y + dy) for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                        if 3 <= x < T - 3 and 3 <= y < T - 3:
                            self.px[y][x] = pal["k"]
        for y in range(T):
            for x in range(T):
                if not mask[y][x]:
                    continue
                if not shade:
                    c = pal["m"]
                elif not inside(x, y - 1) or not inside(x - 1, y):
                    c = pal["l"]
                elif not inside(x, y + 1) or not inside(x + 1, y):
                    c = pal["d"]
                elif not inside(x + 1, y + 1) or not inside(x, y + 2) or not inside(x + 2, y):
                    # a second, softer band of shade inside the lower right edge
                    c = tuple((a + b) // 2 for a, b in zip(pal["m"], pal["d"]))
                else:
                    c = pal["m"]
                if weave and y % 2 == 1:
                    c = scale(c, 0.94)
                self.px[y][x] = c
        for (x, y) in sparkle:
            if inside(x, y):
                self.px[y][x] = pal["h"]

    def image(self):
        im = Image.new("RGBA", (T, T))
        im.putdata([c + (255,) for row in self.px for c in row])
        return im


def mask_of(draw):
    """A 1-bit shape drawn with PIL (no smoothing), as rows of booleans."""
    im = Image.new("1", (T, T), 0)
    draw(ImageDraw.Draw(im))
    px = im.load()
    return [[bool(px[x, y]) for x in range(T)] for y in range(T)]


def union(*masks):
    return [[any(m[y][x] for m in masks) for x in range(T)] for y in range(T)]


def minus(a, b):
    return [[a[y][x] and not b[y][x] for x in range(T)] for y in range(T)]


def roots_panel():
    """The Matriarch's roots: a sapling over the ground line, its roots spreading wide beneath."""
    p = Panel(GROUNDS["roots"])
    # the earth: a band of darker weave under the ground line
    for y in range(17, T - 3):
        for x in range(3, T - 3):
            p.px[y][x] = scale(p.px[y][x], 0.72)
    def roots(d):
        d.line([(19, 15), (19, 19)], fill=1, width=3)
        for path in ([(19, 18), (15, 22), (11, 25), (7, 30), (6, 34)], [(19, 18), (17, 24), (14, 29), (13, 34)],
                     [(19, 18), (20, 25), (21, 30), (20, 35)], [(20, 18), (24, 23), (27, 28), (29, 34)],
                     [(20, 18), (25, 21), (30, 24), (33, 29), (34, 33)]):
            d.line(path, fill=1, width=2)
        for a, b in (((11, 25), (9, 23)), ((14, 29), (10, 30)), ((27, 28), (31, 27)), ((21, 30), (24, 32)), ((30, 24), (32, 21))):
            d.line([a, b], fill=1, width=1)
    p.layer(mask_of(roots), PALETTES["bark"], sparkle=((18, 16), (16, 21), (24, 22)))
    def trunk(d):
        d.rectangle([18, 9, 20, 16], fill=1)
    p.layer(mask_of(trunk), PALETTES["bark"])
    def leaves(d):
        d.polygon([(19, 9), (13, 6), (10, 8), (13, 11), (18, 10)], fill=1)
        d.polygon([(20, 8), (24, 4), (29, 4), (27, 8), (21, 10)], fill=1)
    p.layer(mask_of(leaves), PALETTES["leaf"], sparkle=((12, 7), (25, 5)))
    # the ground line itself, a row of lighter weave
    for x in range(4, T - 4):
        if p.px[16][x] not in (PALETTES["bark"]["k"],) and not (17 <= x <= 21):
            p.px[16][x] = scale(GROUNDS["roots"], 1.7)
    return p


def toll_panel():
    """The Ferryman's toll: his boat on a river of soul fire, the fare, a coin, hung above."""
    p = Panel(GROUNDS["toll"])
    soul = PALETTES["soul"]
    for y in range(27, T - 3):
        for x in range(3, T - 3):
            # the river: dark water lit from within, crests of pale fire running along it
            depth_k = 1.0 - (y - 27) * 0.04
            c = scale(soul["d"], 0.55 + 0.25 * depth_k)
            wave = math.sin(x * 0.7 + y * 1.3) + math.sin(x * 0.31 - y * 0.9)
            if wave > 1.45:
                c = soul["m"]
            if wave > 1.8 or (y == 27 and x % 5 in (0, 1)):
                c = soul["l"]
            p.px[y][x] = c
    def hull(d):
        d.polygon([(6, 23), (32, 23), (28, 29), (10, 29)], fill=1)
        d.polygon([(31, 23), (35, 18), (35, 20), (33, 23)], fill=1)
    p.layer(mask_of(hull), PALETTES["hull"], sparkle=((8, 23), (9, 23), (21, 23)))
    # a plank line along the hull
    for x in range(10, 29):
        p.px[26][x] = PALETTES["hull"]["d"]
    def pole(d):
        d.line([(14, 22), (14, 7)], fill=1, width=1)
        d.line([(14, 7), (19, 7)], fill=1, width=1)
    p.layer(mask_of(pole), PALETTES["wood"], shade=False)
    # the lantern hung from it, and the glow round it on the weave
    for y in range(6, 16):
        for x in range(15, 24):
            r = math.hypot(x - 19, y - 11)
            if r < 4.5:
                p.px[y][x] = tuple(int(a + (b - a) * (0.45 * (1 - r / 4.5))) for a, b in zip(p.px[y][x], soul["l"]))
    def lantern(d):
        d.rectangle([18, 9, 20, 13], fill=1)
    p.layer(mask_of(lantern), soul, sparkle=((18, 10), (19, 10)))
    p.px[8][19] = PALETTES["iron"]["k"]
    def coin(d):
        d.ellipse([25, 4, 35, 14], fill=1)
    def hole(d):
        d.rectangle([29, 8, 31, 10], fill=1)
    p.layer(minus(mask_of(coin), mask_of(hole)), PALETTES["gold"], sparkle=((27, 6), (28, 6), (27, 7)))
    return p


def unravelling_panel():
    """The Last Weaver's unmaking: a spool of silk, its thread running loose in a long fraying curve."""
    p = Panel(GROUNDS["unravelling"])
    def thread(d):
        pts = []
        for i in range(80):
            t = i / 79
            x = 24 + 5 * t + 3.5 * math.sin(t * 7.5)
            y = 17 + 15 * t
            pts.append((x, y))
        d.line(pts, fill=1, width=1)
        # the frayed end, its strands parting
        ex, ey = pts[-1]
        for b in ((ex + 2, ey + 2), (ex - 2, ey + 2), (ex, ey + 2), (ex + 3, ey)):
            d.line([(ex, ey), b], fill=1, width=1)
    p.layer(mask_of(thread), PALETTES["silk"], shade=False)
    def flanges(d):
        d.rectangle([7, 7, 25, 9], fill=1)
        d.rectangle([7, 25, 25, 27], fill=1)
    def barrel(d):
        d.rectangle([9, 10, 23, 24], fill=1)
    p.layer(mask_of(barrel), PALETTES["silk"], sparkle=((10, 11), (11, 11), (10, 12)))
    # the silk wound round it: a line of shade every other row
    for y in range(10, 25):
        for x in range(9, 24):
            if (y + (x // 5)) % 3 == 0:
                p.px[y][x] = PALETTES["silk"]["d"] if x > 10 else p.px[y][x]
    p.layer(mask_of(flanges), PALETTES["wood"], sparkle=((8, 7), (8, 25)))
    return p


def chain_panel():
    """The Held's chain: links hanging from the top, the lowest one broken open."""
    p = Panel(GROUNDS["chain"])
    iron = PALETTES["iron"]
    def ring(outer, inner):
        def f(d):
            d.ellipse(outer, fill=1)
            d.ellipse(inner, fill=0)
        return f
    top = mask_of(ring([14, 3, 25, 18], [17, 6, 22, 15]))
    # the middle link seen edge on: a narrow ring, its two sides with a slit of the weave between
    def edge(d):
        d.rounded_rectangle([17, 13, 22, 28], radius=2, fill=1)
        d.rectangle([19, 16, 20, 25], fill=0)
    middle = mask_of(edge)
    def gap(d):
        d.rectangle([22, 27, 26, 31], fill=1)
    low = minus(mask_of(ring([14, 22, 25, 36], [17, 25, 22, 33])), mask_of(gap))
    # the Held's chain is warm: a glow of embers on the weave round it
    for y in range(3, T - 3):
        for x in range(3, T - 3):
            r = math.hypot((x - 19.5) * 1.3, y - 20)
            if r < 17:
                p.px[y][x] = tuple(int(a + (b - a) * (0.32 * (1 - r / 17))) for a, b in zip(p.px[y][x], (255, 140, 60)))
    # a shadow on the weave behind the whole chain, so it hangs in front of the hanging
    whole = union(top, middle, low)
    for y in range(3, T - 3):
        for x in range(3, T - 3):
            if whole[y - 2][x - 1] and not whole[y][x]:
                p.px[y][x] = scale(p.px[y][x], 0.55)
    # back to front, so the links lock: the edge link lies over the broken one's top and under the top one's bottom
    p.layer(low, iron, sparkle=((16, 24), (15, 25)))
    p.layer(middle, iron, sparkle=((17, 15), (17, 16)))
    p.layer(top, iron, sparkle=((16, 5), (15, 6)))
    # the broken ends glint, and a splinter falls away
    for (x, y) in ((24, 26), (24, 32)):
        p.px[y][x] = (255, 244, 220)
    for (x, y) in ((28, 33), (29, 35)):
        p.px[y][x] = iron["l"]
    return p


def knot_panel():
    """The First Knot: a trefoil of sea-green cord that passes over and under itself as a true knot does."""
    p = Panel(GROUNDS["knot"])
    pal = PALETTES["deep"]
    depth = [[None] * T for _ in range(T)]
    dist = [[9.0] * T for _ in range(T)]
    side = [[0.0] * T for _ in range(T)]
    n = 2400
    for i in range(n):
        t = 2 * math.pi * i / n
        x = math.sin(t) + 2 * math.sin(2 * t)
        y = math.cos(t) - 2 * math.cos(2 * t)
        z = -math.sin(3 * t)
        cx, cy = 19.5 + x * 4.7, 20.0 + y * 4.7
        for dy in range(-3, 4):
            for dx in range(-3, 4):
                px, py = int(math.floor(cx + dx)), int(math.floor(cy + dy))
                if not (3 <= px < T - 3 and 3 <= py < T - 3):
                    continue
                r = math.hypot(px + 0.5 - cx, py + 0.5 - cy)
                if r > 2.05:
                    continue
                if depth[py][px] is None or z > depth[py][px] + 0.05 or (abs(z - depth[py][px]) <= 0.05 and r < dist[py][px]):
                    depth[py][px] = z
                    dist[py][px] = r
                    # which side of the cord's middle the pixel lies on, toward the light (upper left) or away from it
                    side[py][px] = (cx - (px + 0.5)) + (cy - (py + 0.5))
    def covered(x, y):
        return 0 <= x < T and 0 <= y < T and depth[y][x] is not None
    for y in range(T):
        for x in range(T):
            if covered(x, y):
                # where a strand passes under another, a dark edge parts them
                under = any(covered(x + dx, y + dy) and depth[y + dy][x + dx] > depth[y][x] + 0.6
                            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
                if under:
                    c = pal["k"]
                elif side[y][x] > 0.8:
                    c = pal["l"]
                elif side[y][x] < -0.9:
                    c = pal["d"]
                else:
                    c = pal["m"]
                if depth[y][x] < -0.5 and c not in (pal["k"],):
                    c = scale(c, 0.86)
                if y % 2 == 1 and c != pal["k"]:
                    c = scale(c, 0.95)
                p.px[y][x] = c
            elif any(covered(x + dx, y + dy) for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                if 3 <= x < T - 3 and 3 <= y < T - 3:
                    p.px[y][x] = pal["k"]
    # the crown of each loop catches the light
    for y in range(T):
        for x in range(T):
            if covered(x, y) and depth[y][x] > 0.7 and abs(side[y][x] - 1.2) < 0.35:
                p.px[y][x] = pal["h"]
    return p


def heart_panel():
    """The ending: a heart split down its middle, sewn shut with cross-stitches of pale thread."""
    p = Panel(GROUNDS["heart"])
    def heart(d):
        pts = []
        for i in range(120):
            t = 2 * math.pi * i / 120
            x = 16 * math.sin(t) ** 3
            y = -(13 * math.cos(t) - 5 * math.cos(2 * t) - 2 * math.cos(3 * t) - math.cos(4 * t))
            pts.append((19.5 + x * 0.92, 19.5 + y * 0.92))
        d.polygon(pts, fill=1)
    hm = mask_of(heart)
    p.layer(hm, PALETTES["blood"], sparkle=((10, 11), (11, 10), (10, 12), (12, 10)))
    # a soft gleam on its upper left lobe
    for (x, y) in ((11, 12), (12, 11), (11, 13), (13, 11), (12, 12)):
        if hm[y][x]:
            p.px[y][x] = PALETTES["blood"]["l"]
    # the seam: a dark split down the middle, crossed by stitches
    for y in range(13, 32):
        x = 19 + (1 if y % 4 < 2 else 0)
        if hm[y][x]:
            p.px[y][x] = PALETTES["blood"]["k"]
    for y in range(14, 31, 3):
        for (dx, dy) in ((-2, -1), (-1, 0), (1, 0), (2, 1), (2, -1), (1, 0), (-1, 0), (-2, 1)):
            x = 19 + dx
            if hm[y + dy][x]:
                p.px[y + dy][x] = PALETTES["thread"]["l"] if dy <= 0 else PALETTES["thread"]["d"]
    return p


def sheet(out):
    tiles = [roots_panel(), toll_panel(), unravelling_panel(), chain_panel(), knot_panel(), heart_panel(),
             Panel((0, 0, 0), woven=False)]
    out_im = Image.new("RGBA", (T * len(tiles), T), (0, 0, 0, 0))
    for i, t in enumerate(tiles):
        out_im.paste(t.image(), (i * T, 0))
    os.makedirs(os.path.join(ROOT, "gui"), exist_ok=True)
    out_im.save(os.path.join(ROOT, "gui", "tapestry.png"))
    return out_im


def main():
    import sys
    if "sheet" not in sys.argv[1:]:
        for name, item in ITEMS.items():
            shuttle(PANELS[name]).save(os.path.join(ROOT, "item", item + ".png"))
        loom_textures(ROOT)
    im = sheet(ROOT)
    if "preview" in sys.argv[1:]:
        im.resize((im.width * 8, im.height * 8), Image.NEAREST).save(sys.argv[sys.argv.index("preview") + 1])


if __name__ == "__main__":
    main()
