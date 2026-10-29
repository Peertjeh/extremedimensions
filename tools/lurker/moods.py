"""
The Lurker's moods, as recoloured glow textures beside its own (violet) one: blue while it comes to steal
(lurker_glow_thief.png) and red while it hunts to hurt (lurker_glow_hunter.png). The cracks take the mood's colour, the
eyes (the brightest pixels) a pale tint of it; the alpha, and so the shape of the glow, stays as it is.

    python3 tools/lurker/moods.py
"""
import os

from PIL import Image

TEX = os.path.join(os.path.dirname(__file__), "..", "..", "src", "client", "resources", "assets", "extremedimensions", "textures", "entity")

MOODS = {
    "thief": ((80, 170, 255), (215, 240, 255)),
    "hunter": ((255, 52, 36), (255, 214, 190)),
}


def main():
    src = Image.open(os.path.join(TEX, "lurker_glow.png")).convert("RGBA")
    for mood, (crack, eye) in MOODS.items():
        out = Image.new("RGBA", src.size, (0, 0, 0, 0))
        px, op = src.load(), out.load()
        for y in range(src.size[1]):
            for x in range(src.size[0]):
                r, g, b, a = px[x, y]
                if a == 0:
                    continue
                # the eyes are the near-white pixels; the cracks, everything else
                light = (r + g + b) / 3
                base = eye if light > 225 else crack
                # keep the small differences of brightness between the cracks
                k = min(1.15, max(0.85, light / 188.0)) if light <= 225 else 1.0
                op[x, y] = tuple(min(255, int(round(c * k))) for c in base) + (a,)
        path = os.path.join(TEX, "lurker_glow_" + mood + ".png")
        out.save(path, optimize=True)
        print(path)


if __name__ == "__main__":
    main()
