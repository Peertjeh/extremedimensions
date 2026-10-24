# Draws the 3.0.4 music disc at 16x16, after Minecraft's own: a dark record with its grooves and a glint, and a label in
# the middle. Its label is the Seam's: violet silk above, the deep of the Fray below, and the boundary between them, a
# bright tear straight through the hole.
# Usage: python3 tools/items/music_disc.py src/client/resources/assets/extremedimensions/textures/item
import math, os, sys
from PIL import Image

PAL = {
    "k": (16, 12, 22),     # the rim
    "d": (38, 34, 46),     # the record
    "g": (54, 50, 66),     # a groove catching the light
    "h": (104, 98, 122),   # the glint
    "v": (150, 106, 222),  # the label: violet silk
    "u": (96, 62, 160),    # ... in shadow
    "f": (24, 104, 112),   # the Fray's deep
    "e": (16, 70, 78),     # ... in shadow
    "w": (226, 250, 246),  # the boundary
    "t": (112, 196, 190),  # ... running on into the grooves
    "o": (8, 6, 12),       # the hole
}

# the record's outline, as vanilla's records have it
SHAPE = [
    "................",
    ".....######.....",
    "...##########...",
    "..############..",
    "..############..",
    ".##############.",
    ".##############.",
    ".##############.",
    ".##############.",
    ".##############.",
    ".##############.",
    "..############..",
    "..############..",
    "...##########...",
    ".....######.....",
    "................",
]

def disc():
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = im.load()
    cx = cy = 7.5
    inside = lambda x, y: 0 <= x < 16 and 0 <= y < 16 and SHAPE[y][x] == "#"
    for y in range(16):
        for x in range(16):
            if not inside(x, y):
                continue
            d = math.hypot(x - cx, y - cy)
            # angle from the centre, 0 to the right, counting up the picture
            a = math.degrees(math.atan2(cy - y, x - cx)) % 360
            edge = not all(inside(x + dx, y + dy) for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
            tear = x + y == 15
            if edge:
                c = "k"
            elif d < 1.0:
                c = "o"
            elif d < 3.4:
                # the label, cut in two along the diagonal by the boundary
                if tear:
                    c = "w"
                elif x + y < 15:
                    c = "u" if d > 2.7 and 90 < a < 180 else "v"
                else:
                    c = "e" if d > 2.7 and 270 < a < 360 else "f"
            elif tear and d < 4.6:
                c = "t"
            else:
                c = "d"
                if 4.6 < d < 5.3:
                    c = "g"
                # a glint across the grooves on the upper left, as on vanilla's records
                if 4.0 < d < 6.4 and 122 < a < 148:
                    c = "h"
            px[x, y] = PAL[c] + (255,)
    return im

def main(out):
    os.makedirs(out, exist_ok=True)
    disc().save(os.path.join(out, "music_disc_tick_of_the_boundary.png"))

if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else ".")
