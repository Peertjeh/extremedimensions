# Draws the mod's music discs at 16x16, after Minecraft's own: a dark record with its grooves and a glint, and a label in
# the middle. Tick of the Boundary's label is the Seam's: violet silk above, the deep of the Fray below, and the boundary
# between them, a bright tear straight through the hole. What the Wound Remembers has the wound's: raw crimson, and a
# pale scar across it the other way, stitched shut, that runs on dark red into the grooves.
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
    "r": (176, 36, 48),    # the wound's label: raw crimson
    "q": (110, 20, 32),    # ... in shadow
    "s": (240, 206, 196),  # the scar
    "x": (58, 14, 22),     # its stitches
    "n": (126, 26, 36),    # the scar running on into the grooves
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

def boundary(x, y, d, a):
    """Tick of the Boundary: the label cut in two along the diagonal by the boundary, which runs on into the grooves."""
    tear = x + y == 15
    if d < 3.4:
        if tear:
            return "w"
        if x + y < 15:
            return "u" if d > 2.7 and 90 < a < 180 else "v"
        return "e" if d > 2.7 and 270 < a < 360 else "f"
    return "t" if tear and d < 4.6 else None


def wound(x, y, d, a):
    """What the Wound Remembers: crimson, a stitched scar the other way across, running on dark red into the grooves."""
    scar = x == y
    if d < 3.4:
        if scar:
            return "s"
        # the stitches cross the scar every other step
        if abs(x - y) == 1 and (x + y) % 4 == 1:
            return "x"
        return "q" if d > 2.7 and 180 < a < 270 else "r"
    return "n" if scar and d < 4.6 else None


def disc(label=boundary):
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
            mark = None if edge or d < 1.0 else label(x, y, d, a)
            if edge:
                c = "k"
            elif d < 1.0:
                c = "o"
            elif mark:
                c = mark
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
    disc(boundary).save(os.path.join(out, "music_disc_tick_of_the_boundary.png"))
    disc(wound).save(os.path.join(out, "music_disc_wound_remembers.png"))

if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else ".")
