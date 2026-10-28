"""
The project icon for Modrinth (and other listings): the Seam as a glowing tear between worlds, held shut by golden
stitches. The lowest stitch has snapped, the tear gapes beneath it, and the thread hangs loose with its needle,
waiting for someone to mend it. Blocks torn from the world drift round it. Drawn as 64 x 64 pixel art and scaled up
without smoothing, so it stays sharp in a list at 32 pixels as well as on the project page.

    python3 tools/icon/modrinth_icon.py [out.png] [scale]
"""
import math
import sys

from PIL import Image

N = 64

NIGHT = [(8, 4, 18), (14, 8, 31), (22, 12, 46), (32, 18, 63), (44, 24, 84)]
GLOW = (64, 20, 104)
RIM = (128, 46, 198)
BODY = (186, 110, 255)
CORE = (238, 216, 255)
HOT = (255, 252, 255)
SPARKS = [(214, 160, 255), (255, 214, 250), (156, 96, 236)]
GOLD_HI = (255, 242, 176)
GOLD = (255, 198, 66)
GOLD_LO = (190, 120, 24)
LINE = (52, 24, 8)
HOLE = (24, 8, 38)
STEEL_HI = (240, 240, 252)
STEEL = (178, 182, 206)
STEEL_LO = (96, 98, 128)
CUBE_TOP = (98, 80, 140)
CUBE_LEFT = (62, 48, 96)
CUBE_RIGHT = (38, 28, 64)
CUBE_LIT = (176, 104, 255)
CUBE_LINE = (12, 7, 24)

BAYER = [[0, 8, 2, 10], [12, 4, 14, 6], [3, 11, 1, 9], [15, 7, 13, 5]]


def lerp(a, b, t):
    return tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(3))


def rng(seed):
    """A small fixed random sequence, so the icon comes out the same on every run."""
    state = seed
    while True:
        state = (state * 1103515245 + 12345) & 0x7FFFFFFF
        yield state / 0x7FFFFFFF


def inside(poly, x, y):
    hit = False
    j = len(poly) - 1
    for i in range(len(poly)):
        xi, yi = poly[i]
        xj, yj = poly[j]
        if (yi > y) != (yj > y) and x < (xj - xi) * (y - yi) / (yj - yi) + xi:
            hit = not hit
        j = i
    return hit


def draw():
    px = [[NIGHT[0]] * N for _ in range(N)]

    def put(x, y, c):
        if 0 <= x < N and 0 <= y < N:
            px[y][x] = c

    def fill(poly, c):
        for y in range(N):
            for x in range(N):
                if inside(poly, x + 0.5, y + 0.5):
                    put(x, y, c)

    # Night behind it: a dithered glow round the tear
    for y in range(N):
        for x in range(N):
            d = math.hypot(x - 31.5, (y - 34) / 1.2) / 38.0
            v = max(0.0, 1.0 - d) ** 1.3 * (len(NIGHT) - 1)
            i = int(v)
            if i < len(NIGHT) - 1 and (v - i) * 16 > BAYER[y % 4][x % 4]:
                i += 1
            px[y][x] = NIGHT[i]

    r = rng(7)
    for _ in range(30):
        x, y = int(next(r) * N), int(next(r) * N)
        if abs(x - 32) > 13:
            put(x, y, (66, 50, 104) if next(r) < 0.75 else (124, 100, 176))

    # The tear: narrow where it is stitched, gaping below the snapped stitch
    top, bottom = 4, 60
    # (each edge torn on its own: a ragged zigzag rather than a smooth lens)
    cx, half, left, right = {}, {}, {}, {}
    r = rng(21)
    for y in range(top, bottom + 1):
        t = (y - top) / (bottom - top)
        w = 11.5 * math.sin(math.pi * t) ** 0.8
        if t < 0.58:
            w *= 0.46 + 0.54 * (t / 0.58) ** 2.4
        cx[y] = 32 + math.sin(y * 0.5) * 1.1
        rag = min(1.0, w / 3.0)
        left[y] = max(0.0, w + ((1.3 if y % 3 == 0 else -0.4) + (next(r) - 0.5) * 1.4) * rag)
        right[y] = max(0.0, w + ((1.3 if y % 3 == 1 else -0.4) + (next(r) - 0.5) * 1.4) * rag)
        half[y] = (left[y] + right[y]) / 2

    def edge(x, y):
        return left[y] if x + 0.5 < cx[y] else right[y]

    mask = [(x, y) for y in range(top, bottom + 1) for x in range(N) if edge(x, y) > 0.35 and abs(x + 0.5 - cx[y]) <= edge(x, y) + 1.0]
    for y in range(N):
        for x in range(N):
            near = min((abs(x - mx) ** 2 + abs(y - my) ** 2 for mx, my in mask if abs(y - my) <= 4 and abs(x - mx) <= 4), default=99)
            if near <= 4.0 ** 2:
                put(x, y, lerp(px[y][x], GLOW, 1.0 if near <= 2.0 ** 2 else 0.55))
    for y in range(top, bottom + 1):
        for x in range(N):
            h = edge(x, y)
            if h <= 0.35:
                continue
            dx = abs(x + 0.5 - cx[y])
            if dx <= h + 1.0:
                put(x, y, RIM)
            if dx <= h:
                put(x, y, BODY)
            if dx <= h * 0.52:
                put(x, y, CORE)
            if dx <= max(0.6, h * 0.17):
                put(x, y, HOT)

    # Sparks drifting off it
    r = rng(3)
    for _ in range(34):
        y = top + int(next(r) * (bottom - top))
        side = 1 if next(r) < 0.5 else -1
        x = int(cx[y] + side * (half[y] + 3 + next(r) * 13))
        put(x, y, SPARKS[int(next(r) * len(SPARKS))])

    # Blocks torn from the world, lit on the side that faces the tear
    def cube(x0, y0, a):
        top_face = [(x0 + a, y0), (x0 + 2 * a, y0 + a / 2), (x0 + a, y0 + a), (x0, y0 + a / 2)]
        left = [(x0, y0 + a / 2), (x0 + a, y0 + a), (x0 + a, y0 + 2 * a), (x0, y0 + 1.5 * a)]
        right = [(x0 + a, y0 + a), (x0 + 2 * a, y0 + a / 2), (x0 + 2 * a, y0 + 1.5 * a), (x0 + a, y0 + 2 * a)]
        outline = [(x0 + a, y0 - 1), (x0 + 2 * a + 1, y0 + a / 2), (x0 + 2 * a + 1, y0 + 1.5 * a + 0.5),
                   (x0 + a, y0 + 2 * a + 1), (x0 - 1, y0 + 1.5 * a + 0.5), (x0 - 1, y0 + a / 2)]
        fill(outline, CUBE_LINE)
        fill(top_face, CUBE_TOP)
        fill(left, CUBE_LEFT)
        fill(right, CUBE_RIGHT)
        lit_x = x0 + 2 * a - 1 if x0 + a < 32 else x0
        for y in range(int(y0 + a / 2) + 1, int(y0 + 1.5 * a)):
            put(lit_x, y, CUBE_LIT)

    cube(5, 6, 5)
    cube(48, 13, 4)
    cube(49, 45, 5)
    cube(15, 25, 2)
    cube(46, 31, 2)

    # Thread: a thick line, lit along its upper edge, outlined dark
    def thread(points, core=1.05, edge=1.95):
        grid = {}
        for (ax, ay), (bx, by) in zip(points, points[1:]):
            length = max(1e-6, math.hypot(bx - ax, by - ay))
            nx, ny = -(by - ay) / length, (bx - ax) / length
            if ny > 0:
                nx, ny = -nx, -ny
            for y in range(N):
                for x in range(N):
                    qx, qy = x + 0.5 - ax, y + 0.5 - ay
                    t = max(0.0, min(1.0, (qx * (bx - ax) + qy * (by - ay)) / length ** 2))
                    d = math.hypot(qx - t * (bx - ax), qy - t * (by - ay))
                    side = qx * nx + qy * ny
                    if d <= core:
                        c = GOLD_HI if side > 0.25 else GOLD if side > -0.55 else GOLD_LO
                        if grid.get((x, y), (9, None))[0] > d:
                            grid[(x, y)] = (d, c)
                    elif d <= edge and (x, y) not in grid:
                        grid[(x, y)] = (9, LINE)
        for (x, y), (d, c) in sorted(grid.items(), key=lambda kv: kv[1][0] < 9):
            put(x, y, c)

    def hole(x, y):
        put(x, y, HOLE)
        put(x + 1, y, HOLE)
        put(x, y + 1, HOLE)
        put(x + 1, y + 1, LINE)

    def stitch(y, rise=5.0, span=4.4):
        h = half[y]
        l, rr = cx[y] - h - span, cx[y] + h + span
        thread([(l, y + rise / 2), (rr, y - rise / 2)])
        hole(int(l) - 1, int(y + rise / 2) - 1)
        hole(int(rr) - 1, int(y - rise / 2) - 1)

    stitch(11)
    stitch(20)

    # The snapped stitch: its right end still holds a frayed stub; the rest hangs loose down the left with its needle
    y = 30
    h = half[y]
    l, rr = cx[y] - h - 4.4, cx[y] + h + 4.4
    thread([(rr, y - 2.5), (rr - 4.0, y - 0.5)], core=0.9, edge=1.7)
    hole(int(rr) - 1, int(y - 2.5) - 1)
    hang = [(l, y + 2.5), (l - 3.0, y + 4.5), (l - 4.5, y + 9.5), (l - 3.0, y + 14.0), (l - 4.5, y + 18.0)]
    thread(hang)
    hole(int(l) - 1, int(y + 2.5) - 1)

    # The needle at the end of the loose thread, point down: a head with its eye, a body, a point
    nx0, ny0 = hang[-1]
    nx0, ny0 = int(nx0) - 1, int(ny0)
    shape = [(0, 3), (0, 3), (0, 3), (0, 3), (1, 2), (1, 2), (1, 2), (1, 2), (1, 2), (1, 1), (1, 1), (1, 1)]
    for i, (a, w) in enumerate(shape):
        for dx in range(a - 1, a + w + 1):
            put(nx0 + dx, ny0 + i, LINE)
    for dx in range(-1, 4):
        put(nx0 + dx, ny0 - 1, LINE)
    put(nx0 + 1, ny0 + len(shape), LINE)
    for i, (a, w) in enumerate(shape):
        for dx in range(a, a + w):
            c = STEEL_HI if dx == a and i < 9 else STEEL if i < 9 else STEEL_LO
            put(nx0 + dx, ny0 + i, c)
    put(nx0 + 1, ny0 + 1, HOLE)  # the eye
    put(nx0 + 1, ny0 + 2, HOLE)

    img = Image.new("RGB", (N, N))
    img.putdata([c for row in px for c in row])
    return img


def main():
    out = sys.argv[1] if len(sys.argv) > 1 else "icon_modrinth.png"
    scale = int(sys.argv[2]) if len(sys.argv) > 2 else 8
    img = draw()
    img.resize((N * scale, N * scale), Image.NEAREST).save(out, optimize=True)
    print(out, N * scale)


if __name__ == "__main__":
    main()
