# Draws the 2.39 item textures at 16x16: the four threads of the First Weaver's trail (each a loose skein in the
# colours of the world it was found in) and the Plumb Line they twist into.
# Usage: python3 tools/items/first_weaver.py src/client/resources/assets/extremedimensions/textures/item
import math, os, sys
from PIL import Image

def ball(pal):
    """A ball of thread lit from the upper left, wound round twice (two crossing sets of wraps), its loose end trailing."""
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = im.load()
    cx, cy, r = 7.0, 7.0, 5.6
    inside = lambda x, y: (x - cx) ** 2 + (y - cy) ** 2 <= r * r
    for y in range(16):
        for x in range(16):
            if not inside(x, y):
                continue
            edge = not all(inside(x + dx, y + dy) for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
            if edge:
                c = "k"
            else:
                light = ((cx - 1.8 - x) + (cy - 1.8 - y)) / (2 * r)
                c = "l" if light > 0.22 else "m" if light > -0.18 else "d"
                # the wraps: curved bands round the ball, tilted, as thread wound on lies
                along = (x - cx) * 0.87 - (y - cy) * 0.5
                across = (y - cy) * 0.87 + (x - cx) * 0.5 + 0.14 * along * along
                if int(math.floor(across + 20)) % 3 == 0:
                    c = {"l": "m", "m": "d", "d": "k"}[c]
            px[x, y] = pal[c] + (255,)
    # the loose end, out from the lower right and away
    for x, y, c in ((11, 12, "m"), (12, 12, "m"), (12, 13, "l"), (13, 13, "m"), (14, 13, "d"), (14, 14, "m"), (15, 14, "d")):
        px[x, y] = pal[c] + (255,)
    return im

PLUMB = [
    "......kkk.......",
    "......kek.......",
    ".......k........",
    ".......e........",
    ".......p........",
    ".......s........",
    ".......c........",
    ".......e........",
    ".......p........",
    ".......s........",
    "......kck.......",
    ".....kmllk......",
    "....kmlwlmk.....",
    "....kdmlmdk.....",
    ".....kdmdk......",
    "......kkk.......",
]

THREADS = {
    # found in the frame of an Ancient City: sculk teal
    "echo_thread": {"k": (8, 24, 30), "d": (14, 82, 92), "m": (34, 150, 160), "l": (150, 238, 236)},
    # tied round a piglin ledger: ember gold
    "ember_thread": {"k": (40, 14, 6), "d": (128, 46, 14), "m": (218, 112, 34), "l": (255, 206, 104)},
    # in the socket of an End city: pale end stone
    "pale_thread": {"k": (40, 34, 50), "d": (124, 112, 140), "m": (212, 206, 168), "l": (250, 248, 222)},
    # pulled from a half-woven tapestry in the Seam: violet silk
    "silk_thread": {"k": (30, 18, 46), "d": (96, 64, 150), "m": (176, 146, 232), "l": (240, 232, 255)},
}

PLUMB_PAL = {"k": (18, 16, 22), "d": (52, 50, 60), "m": (96, 94, 108), "l": (150, 148, 162), "w": (230, 236, 240),
             "e": THREADS["echo_thread"]["m"], "p": THREADS["pale_thread"]["m"], "s": THREADS["silk_thread"]["m"],
             "c": THREADS["ember_thread"]["m"]}

def draw(rows, pal):
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = im.load()
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch != ".":
                px[x, y] = pal[ch] + (255,)
    return im

def main(out):
    os.makedirs(out, exist_ok=True)
    for name, pal in THREADS.items():
        ball(pal).save(os.path.join(out, name + ".png"))
    draw(PLUMB, PLUMB_PAL).save(os.path.join(out, "plumb_line.png"))

if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else "src/client/resources/assets/extremedimensions/textures/item")
