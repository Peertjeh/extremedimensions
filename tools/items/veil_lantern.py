# Draws the Veil Lantern (2.36): the block texture (laid out for models/block/veil_lantern*.json) and the item icon.
# Usage: python3 tools/items/veil_lantern.py src/client/resources/assets/extremedimensions/textures
# Every pixel the block's model uses is opaque, so it renders without a cutout layer.
import os, sys
from PIL import Image

K = (26, 24, 32)      # iron, darkest
D = (52, 50, 62)      # iron
M = (84, 82, 96)      # iron, lit
L = (124, 122, 138)   # iron, highlight
G = (60, 98, 112)     # glass, dark
C = (120, 214, 220)   # veil glow
P = (196, 168, 244)   # wing, pale violet
W = (236, 246, 250)   # glow core

def block():
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = im.load()
    def put(x, y, c):
        px[x, y] = c + (255,)
    # body side (0,0)-(6,7): iron frame round glass, a veil wing glowing inside
    side = ["KDDDDK",
            "DGCCGD",
            "DCPWCD",
            "DCWPCD",
            "DGPCGD",
            "DGCCGD",
            "KDMMDK"]
    # body top/bottom (6,0)-(12,6): a grate
    top = ["KDDDDK",
           "DMKMKD",
           "DKMKMD",
           "DMKMKD",
           "DKMKMD",
           "KDDDDK"]
    # cap side (0,7)-(4,9) and cap top (4,7)-(8,11)
    cap_side = ["DMMD", "KDDK"]
    cap_top = ["DMMD", "MLLM", "MLLM", "DMMD"]
    # handle / chain (12,0)-(14,8): links
    chain = ["DM", "KD", "MD", "DK", "DM", "KD", "MD", "DK"]
    pal = {"K": K, "D": D, "M": M, "L": L, "G": G, "C": C, "P": P, "W": W}
    for (ox, oy, rows) in [(0, 0, side), (6, 0, top), (0, 7, cap_side), (4, 7, cap_top), (12, 0, chain)]:
        for y, row in enumerate(rows):
            for x, ch in enumerate(row):
                put(ox + x, oy + y, pal[ch])
    return im

def item():
    rows = ["................",
            "......KDDK......",
            "......D..D......",
            ".....KDMMDK.....",
            ".....DMLLMD.....",
            "....KDDDDDDK....",
            "....DGCCCCGD....",
            "....DCCPPCCD....",
            "....DCPWWPCD....",
            "....DCWPPWCD....",
            "....DCPWWPCD....",
            "....DGCPPCGD....",
            "....DGCCCCGD....",
            "....KDMMMMDK....",
            ".....KDDDDK.....",
            "................"]
    pal = {"K": K, "D": D, "M": M, "L": L, "G": G, "C": C, "P": P, "W": W}
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = im.load()
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch != ".":
                px[x, y] = pal[ch] + (255,)
    return im

if __name__ == "__main__":
    out = sys.argv[1]
    block().save(os.path.join(out, "block", "veil_lantern.png"))
    item().save(os.path.join(out, "item", "veil_lantern.png"))
    if len(sys.argv) > 2:
        prev = Image.new("RGBA", (16 * 2 * 12 + 12, 16 * 12), (40, 40, 48, 255))
        prev.paste(block().resize((192, 192), Image.NEAREST), (0, 0))
        prev.paste(item().resize((192, 192), Image.NEAREST), (204, 0))
        prev.save(sys.argv[2])
