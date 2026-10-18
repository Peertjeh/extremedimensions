"""The Elders' tells: their light recoloured for each kind of blow, made from the glow texture build.py paints.

Each pixel keeps its alpha and its brightness; its colour becomes the tint, deepened in the darker parts and pale towards
white in the brightest. usage: python3 tools/elders/tints.py (after build.py)
"""
import os

import numpy as np
from PIL import Image

ROOT = os.path.join(os.path.dirname(__file__), "..", "..")
TEX = os.path.join(ROOT, "src/client/resources/assets/extremedimensions/textures/entity/elder")

# Elder -> tint name -> colour. The order is the one ElderTints (client) indexes them by.
TINTS = {
    "matriarch": {"red": (255, 64, 44), "green": (104, 232, 84), "amber": (255, 176, 52), "white": (238, 242, 255)},
    "ferryman": {"red": (255, 56, 64), "sea": (52, 226, 176), "gold": (255, 206, 72), "white": (236, 244, 255), "soul": (84, 196, 255)},
    "weaver": {"blue": (104, 196, 255), "crimson": (232, 36, 76), "silver": (200, 208, 222)},
}


def tint(glow, colour):
    rgb = glow[..., :3].astype(np.float32) / 255
    alpha = glow[..., 3:4]
    lum = (rgb * np.array([0.3, 0.59, 0.11], dtype=np.float32)).sum(axis=2, keepdims=True)
    base = np.array(colour, dtype=np.float32).reshape(1, 1, 3) / 255
    out = base * np.clip(0.25 + 0.95 * lum, 0, 1.2)
    pale = np.clip((lum - 0.7) / 0.3, 0, 1) * 0.6
    out = out * (1 - pale) + pale
    out = np.clip(np.round(out * 255), 0, 255).astype(np.uint8)
    return np.concatenate([out, alpha], axis=2)


def write(name):
    glow = np.asarray(Image.open(os.path.join(TEX, name + "_glow.png")).convert("RGBA"))
    for t, colour in TINTS[name].items():
        Image.fromarray(tint(glow, colour), "RGBA").save(os.path.join(TEX, f"{name}_glow_{t}.png"), optimize=True)


if __name__ == "__main__":
    for n in TINTS:
        write(n)
        print(n, ", ".join(TINTS[n]))
