# Draws the mod's music discs at 16x16, after Minecraft's own: a dark record with its grooves and a glint, and a label in
# the middle. Tick of the Boundary's label is the Seam's: violet silk above, the deep of the Fray below, and the boundary
# between them, a bright tear straight through the hole. What the Wound Remembers has the wound's: raw crimson, and a
# pale scar across it the other way, stitched shut, that runs on dark red into the grooves. What Listens is sculk teal
# with a cyan ring of sound; Elegy of the Elders is moss, bronze and silver in a ring of gold; The Abyss Answers is
# abyssal purple, its pale ring broken by a crack. The two disc fragments are shards of the last two.
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
    "C": (14, 74, 82),     # What Listens' label: sculk teal
    "z": (8, 44, 50),      # ... in shadow
    "c": (40, 220, 226),   # the ring of a sound, sculk cyan
    "y": (34, 120, 128),   # ... its echo out in the grooves
    "m": (78, 116, 58),    # the Elegy's label: the Matriarch's moss
    "b": (168, 112, 50),   # the Ferryman's bronze
    "l": (198, 202, 216),  # the Last Weaver's silver
    "j": (228, 198, 122),  # the gold ring round them
    "p": (70, 22, 100),    # The Abyss Answers' label: abyssal purple
    "P": (30, 8, 46),      # ... in shadow, and the crack
    "i": (176, 104, 236),  # the ring that the answer breaks
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


def listens(x, y, d, a):
    """What Listens: sculk teal, a cyan ring of sound round the hole, and its echo out in the grooves."""
    if d < 3.4:
        if 1.9 < d < 2.7:
            return "c"
        return "z" if d > 2.7 and 200 < a < 300 else "C"
    return "y" if 4.7 < d < 5.4 and 20 < a < 75 else None


def elegy(x, y, d, a):
    """Elegy of the Elders: the label in three, moss, bronze and silver, one for each Elder, in a ring of gold."""
    if d < 3.4:
        if d > 2.75:
            return "j"
        if 90 <= a < 210:
            return "m"
        if 210 <= a < 330:
            return "b"
        return "l"
    return None


def abyss(x, y, d, a):
    """The Abyss Answers: abyssal purple, a pale ring round the hole broken where the answer comes through, and the
    crack it leaves running out into the grooves."""
    crack = 292 < a < 318
    if d < 3.4:
        if crack and d > 1.2:
            return "P"
        if 1.5 < d < 2.4:
            return "i"
        return "P" if d > 2.75 and 160 < a < 260 else "p"
    return "P" if crack and d < 5.6 else None


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

def fragment(label, sector=(10, 115)):
    """A shard of a record, as vanilla's disc fragment: a jagged piece of the disc from the hole out to the rim, with a
    corner of its label, moved to the middle of the picture."""
    whole = disc(label)
    src = whole.load()
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = im.load()
    cx = cy = 7.5
    lo, hi = sector
    keep = set()
    for y in range(16):
        for x in range(16):
            if src[x, y][3] == 0:
                continue
            d = math.hypot(x - cx, y - cy)
            a = math.degrees(math.atan2(cy - y, x - cx)) % 360
            # the break is jagged: the edges step in and out with the distance from the hole
            wob = 7 * math.sin(d * 2.1)
            if d > 1.3 and lo + wob < a < hi - wob * 0.6:
                keep.add((x, y))
    # (moved so the shard sits in the middle of its picture)
    mx = sum(x for x, _ in keep) / len(keep)
    my = sum(y for _, y in keep) / len(keep)
    shift = (round(7.5 - mx), round(7.5 - my))
    for (x, y) in keep:
        nx, ny = x + shift[0], y + shift[1]
        if not (0 <= nx < 16 and 0 <= ny < 16):
            continue
        edge = not all((x + dx, y + dy) in keep for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
        px[nx, ny] = (PAL["k"] + (255,)) if edge else src[x, y]
    return im


def main(out):
    os.makedirs(out, exist_ok=True)
    disc(boundary).save(os.path.join(out, "music_disc_tick_of_the_boundary.png"))
    disc(wound).save(os.path.join(out, "music_disc_wound_remembers.png"))
    disc(listens).save(os.path.join(out, "music_disc_what_listens.png"))
    disc(elegy).save(os.path.join(out, "music_disc_elegy_of_the_elders.png"))
    disc(abyss).save(os.path.join(out, "music_disc_the_abyss_answers.png"))
    fragment(elegy).save(os.path.join(out, "disc_fragment_elegy.png"))
    fragment(abyss, sector=(250, 350)).save(os.path.join(out, "disc_fragment_abyss.png"))

if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else ".")
