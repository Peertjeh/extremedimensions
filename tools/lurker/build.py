"""The tribe's body: the Lurker, the Hollowling, the Keeper, the Starved One and the Thinned all wear it, each with its
own texture. A hunched, cloaked giant on long knuckle-walking arms, ragged strips of cloak, strands dripping from a
mask. The claws are fine parts (four pixels to a model pixel); the mask, its eyes, its brow and the fringe of the hood
are at Minecraft's own size, painted finer and brought down;
the part names and poses are the ones LurkerModel animates.

usage: python3 tools/lurker/build.py [preview-json-dir]
Writes LurkerGeometry.java and a base and glow texture for each of the five.
"""
import math
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "elders"))
import elderlib  # noqa: E402
from elderlib import Model, mix, noise, shade, _h, _edge, _front, _uv, _mask_edge  # noqa: E402

ROOT = os.path.join(os.path.dirname(__file__), "..", "..")
JAVA = os.path.join(ROOT, "src/client/java/com/extremedimensions/client/entity")
TEX = os.path.join(ROOT, "src/client/resources/assets/extremedimensions/textures/entity")

F = 0.25
# the mask is designed this many times finer than it is worn
DETAIL = 4


def build():
    m = Model("Lurker", 256, 128, seed=26)
    R = m.part("root", None, (0, 24, 0))
    m.part("leg_l", R, (5, -13, 3))
    m.box("leg_l", -3, 0, -3, 6, 13, 6, "t_limb")
    m.part("leg_r", R, (-5, -13, 3))
    m.box("leg_r", -3, 0, -3, 6, 13, 6, "t_limb")
    hips = m.part("hips", R, (0, -13, 3), (0.62, 0, 0))
    m.box(hips, -9, -20, -6, 18, 20, 12, "t_cloak")
    m.part("hump", hips, (0, -14, 4), (-0.25, 0, 0))
    m.box("hump", -10, -12, -2, 20, 14, 8, "t_cloak")
    skirt = [(-7, -6, 1, 9), (-2.5, -6.5, 1, 11), (3, -6, 1, 10), (7.5, -3, 1, 8),
             (-7, 5.5, 1, 11), (-2, 6.5, 1, 12), (3.5, 6, 1, 10), (-8, 0, 1, 8), (8, 1, 1, 9)]
    for i, (x, z, w, length) in enumerate(skirt):
        ww = 4 if i % 3 != 1 else 5
        m.part(f"skirt{i}", hips, (x, -1, z))
        m.box(f"skirt{i}", -ww / 2, 0, -0.5, ww, length, 1, "t_cloak_ragged")
    m.part("cape", hips, (0, -19, 6))
    m.box("cape", -10, 0, 0, 20, 24, 2, "t_cloak_ragged")
    m.part("mantle_l", hips, (8, -20, -1), (0, 0, 0.35))
    m.box("mantle_l", -3, -2, -5, 8, 5, 11, "t_cloak")
    m.part("mantle_r", hips, (-8, -20, -1), (0, 0, -0.35))
    m.box("mantle_r", -5, -2, -5, 8, 5, 11, "t_cloak")
    neck = m.part("neck", hips, (0, -19, -3), (0.35, 0, 0))
    m.box(neck, -3.5, -7, -3.5, 7, 8, 7, "t_cloak")
    head = m.part("head", neck, (0, -6, -1), (-0.77, 0, 0))
    m.box(head, -6, -11, -10, 12, 12, 13, "t_head")
    hood = m.part("hood", head, (0, 0, 0))
    m.box(hood, -7, -12.5, -8, 14, 7, 14, "t_cloak")
    m.box(hood, -7.2, -12.7, -10.4, 14.4, 1.6, 2.6, "t_cloak")
    _mask_parts(m, head)
    tend = [(-5, -8, 1, 14), (-3, -9.5, 2, 10), (-1, -9, 1, 18), (1.5, -9.5, 2, 13),
            (3.5, -9, 1, 16), (5, -7.5, 1, 11), (-2, -6, 1, 9), (2, -6, 1, 12)]
    for i, (x, z, w, length) in enumerate(tend):
        m.part(f"tendril{i}", head, (x, 1, z))
        m.box(f"tendril{i}", -w / 2, 0, -w / 2, w, length, w, "t_tendril")
        m.part(f"drip{i}", f"tendril{i}", (0, length, 0))
        m.box(f"drip{i}", -1, 0, -1, 2, 2, 2, "t_drip")
    for side, sx in (("l", 1), ("r", -1)):
        arm = m.part(f"arm_{side}", hips, (10 * sx, -18, -2))
        m.box(arm, -3, -2, -3, 6, 14, 6, "t_cloak")
        m.part(f"rag_{side}", arm, (3 * sx, 3, 0))
        m.box(f"rag_{side}", -0.5, 0, -1.5, 1, 13, 3, "t_cloak_ragged")
        fo = m.part(f"forearm_{side}", arm, (0, 11, 0))
        m.box(fo, -2.5, 0, -2.5, 5, 13, 5, "t_limb")
        ha = m.part(f"hand_{side}", fo, (0, 12, 0))
        m.box(ha, -3, 0, -4.5, 6, 5, 7, "t_limb")
        for k, (x, length, z) in enumerate([(-2.0, 3, -4.0), (0.0, 4, -4.5), (2.0, 3, -4.0)]):
            m.box(ha, x - 0.5, 4.5, z - 0.5, 1, length, 1, "t_limb")
            # a long hooked claw at each finger (fine)
            cl = m.part(f"claw_{side}{k}", ha, (x, 4.5 + length, z), (-0.35, 0, 0.08 * (k - 1)), scale=F)
            m.box(cl, -0.32, -0.2, -0.32, 0.64, 2.6, 0.64, "t_claw")
    return m


def _mask_parts(m, head):
    """The mask and what is round it on the front of the head, at Minecraft's own size: painted in fine pixels and
    brought down to one pixel to a model pixel (shared by all five; each one's texture paints its own face on it)."""
    face = m.part("tmask", head, (0, 0, 0), detail=1)
    m.box(face, -5.4, -10.4, -10.45, 10.8, 10.8, 0.5, "t_face_px")
    for side, sx in (("l", 1), ("r", -1)):
        # a heavy brow, low over the eye where it meets the nose
        brow = m.part(f"tbrow_{side}", head, (2.45 * sx, -6.75, -10.5), (0, 0, -0.22 * sx), detail=DETAIL)
        m.box(brow, -1.9, -0.45, -0.3, 3.8, 0.9, 0.6, "t_brow")
        ey = m.part(f"teye_{side}", head, (2.35 * sx, -5.05, -10.55), detail=1)
        m.box(ey, -1.3, -0.9, -0.15, 2.6, 1.8, 0.3, "t_eye_px")
        # the hood's frayed edge hanging down beside the mask
        for j in range(3):
            fr = m.part(f"tfray_{side}{j}", head, (sx * (5.7 + 0.45 * j), -10.9, -9.6 + 0.9 * j), (0.05, 0, -0.08 * sx), detail=DETAIL)
            m.box(fr, -0.3, 0, -0.3, 0.6, 3.6 + 1.3 * ((j * 2 + (sx > 0)) % 3), 0.6, "t_fray")
    for k in range(7):
        x = -4.2 + 1.4 * k
        fr = m.part(f"tfringe_{k}", head, (x, -11.15, -10.5), (0.15, 0, 0), detail=DETAIL)
        m.box(fr, -0.35, 0, -0.25, 0.7, 0.9 + 0.5 * ((k * 3) % 4), 0.5, "t_fray")


# ----------------------------------------------------------------------------- the five

SPECIES = {
    "lurker": dict(cloak=((14, 12, 20), (54, 46, 66)), limb=((22, 20, 26), (60, 56, 66)), skin=((40, 36, 44), (86, 80, 90)),
                   mask=((70, 64, 72), (140, 132, 138)), crack=(200, 110, 255), eye=(206, 120, 255), eye_hot=(250, 220, 255),
                   tendril=((10, 8, 14), (34, 26, 44)), tip=(90, 40, 140), claw=((40, 36, 40), (120, 110, 116)), face="lurker"),
    "hollowling": dict(cloak=((20, 24, 36), (64, 74, 98)), limb=((28, 32, 44), (72, 80, 98)), skin=((52, 58, 72), (110, 118, 134)),
                       mask=((110, 118, 134), (196, 202, 214)), crack=(110, 190, 255), eye=(110, 200, 255), eye_hot=(225, 250, 255),
                       tendril=((14, 16, 24), (40, 46, 62)), tip=(60, 120, 200), claw=((60, 64, 72), (150, 156, 166)), face="hollowling"),
    "keeper": dict(cloak=((8, 8, 10), (38, 36, 40)), limb=((18, 17, 19), (54, 52, 56)), skin=((30, 28, 30), (70, 66, 68)),
                   mask=((176, 160, 122), (242, 232, 204)), crack=(255, 240, 210), eye=(255, 226, 150), eye_hot=(255, 252, 230),
                   tendril=((6, 6, 8), (28, 26, 30)), tip=(120, 110, 90), claw=((150, 136, 104), (230, 218, 186)), face="keeper"),
    "starved_one": dict(cloak=((46, 32, 52), (130, 118, 132)), limb=((60, 50, 64), (150, 142, 150)), skin=((80, 66, 82), (160, 150, 160)),
                        mask=((120, 112, 118), (190, 184, 186)), crack=(255, 110, 40), eye=(255, 120, 40), eye_hot=(255, 230, 170),
                        tendril=((36, 24, 40), (96, 80, 100)), tip=(200, 70, 30), claw=((96, 84, 90), (190, 180, 182)), face="starved"),
    "thinned": dict(cloak=((150, 150, 170), (232, 232, 246)), limb=((160, 160, 178), (236, 236, 248)), skin=((180, 180, 196), (244, 244, 252)),
                    mask=((206, 204, 220), (252, 252, 255)), crack=(216, 196, 255), eye=(214, 190, 255), eye_hot=(255, 250, 255),
                    tendril=((130, 128, 150), (210, 208, 226)), tip=(190, 170, 240), claw=((170, 168, 186), (246, 246, 252)), face="thinned"),
}
P = {}


def _cloth(ctx, ragged=False):
    """Cloak: coarse cloth in long folds, darker in the creases, a few cracks of the light inside it, frayed below."""
    gx, gy, s = ctx["gx"], ctx["gy"], ctx["seed"]
    lo, hi = P["cloak"]
    fold = 0.5 + 0.5 * math.sin(ctx["px"] * 0.9 + noise(gx, gy * 0.1, s, 5) * 4)
    base = mix(lo, hi, 0.2 + 0.5 * fold * (0.7 + 0.3 * noise(gx, gy, s + 3, 2.0)))
    if (gx * 3 + gy) % 5 == 0:
        base = shade(base, 0.86)
    if ctx["face"] in ("top", "bottom"):
        base = shade(base, 0.8)
    a = 255
    if ragged and ctx["face"] not in ("top", "bottom"):
        k = ctx["py"] / max(1, ctx["fh"] - 1)
        jag = 1 + int(3 * _h(ctx["px"], 11, s + ctx["fw"]))
        if ctx["py"] > ctx["fh"] - 1 - jag or (k > 0.7 and _h(gx, gy, s + 13) < (k - 0.7) * 0.6):
            a = 0
    glow = None
    if a and abs(noise(gx * 0.8, gy * 0.8, s + 21, 5.0) - 0.5) < 0.018 and noise(gx, gy, s + 23, 9) > 0.55:
        base, glow = shade(P["crack"], 0.55), P["crack"] + (220,)
    return base + (a,), glow


def _sinew(ctx, key="limb"):
    """Limbs and the head under the mask: grey hide over sinew, ridged, with a dark seam at the edges."""
    gx, gy, s = ctx["gx"], ctx["gy"], ctx["seed"]
    lo, hi = P[key]
    base = mix(lo, hi, 0.25 + 0.45 * noise(gx * 0.6, gy * 1.6, s, 2.5) + 0.2 * noise(gx, gy, s + 5, 1.5))
    if abs(noise(gx * 2, gy * 0.3, s + 7, 3) - 0.5) < 0.04:
        base = shade(base, 0.7)
    if _edge(ctx) == 0:
        base = shade(base, 0.75)
    glow = None
    if abs(noise(gx, gy, s + 31, 7.0) - 0.5) < 0.012 and noise(gx, gy, s + 33, 11) > 0.6:
        base, glow = shade(P["crack"], 0.6), P["crack"] + (200,)
    return base + (255,), glow


def _head(ctx):
    """The head under the mask: hide on the sides and behind, and in front the deep shadow of the hood, so the mask's
    own outline stands out against it."""
    if ctx["face"] != "north":
        return _sinew(ctx, "skin")
    gx, gy, s = ctx["gx"], ctx["gy"], ctx["seed"]
    lo, hi = P["cloak"]
    base = shade(mix(lo, hi, 0.2 + 0.3 * noise(gx, gy, s + 3, 2.0)), 0.42)
    if _edge(ctx) == 0:
        base = shade(base, 0.8)
    return base + (255,), None


def _tendril(ctx, drip=False):
    lo, hi = P["tendril"]
    k = ctx["py"] / max(1, ctx["fh"] - 1)
    base = mix(lo, hi, noise(ctx["gx"], ctx["gy"], ctx["seed"], 1.5))
    glow = None
    if drip or (k > 0.72 and _h(ctx["gx"], ctx["gy"], ctx["seed"] + 3) < (k - 0.6)):
        base = P["tip"]
        if drip and ctx["face"] == "bottom":
            glow = P["crack"] + (160,)
    return base + (255,), glow


def _claw(ctx):
    lo, hi = P["claw"]
    k = ctx["py"] / max(1, ctx["fh"] - 1)
    base = mix(hi, lo, k * 0.8 + 0.2 * noise(ctx["gx"], ctx["gy"], ctx["seed"], 2))
    if _edge(ctx) == 0:
        base = shade(base, 1.1)
    return base + (255,), None


def _fray(ctx):
    (r, g, b, a), glow = _cloth(ctx, ragged=False)
    k = ctx["py"] / max(1, ctx["fh"] - 1)
    if ctx["face"] not in ("top", "bottom") and _h(ctx["gx"], ctx["gy"], ctx["seed"] + 9) < k * 0.5:
        a = 0
    return (r, g, b, a), None


def _brow(ctx):
    lo, hi = P["mask"]
    base = shade(mix(lo, hi, 0.3 + 0.4 * noise(ctx["gx"], ctx["gy"], ctx["seed"], 2)), 0.85)
    if ctx["py"] == 0 or ctx["face"] == "top":
        base = shade(base, 1.2)
    elif ctx["py"] >= ctx["fh"] - 1 or ctx["face"] == "bottom":
        base = shade(base, 0.5)
    return base + (255,), None


# The face: where its eyes sit on the mask (u across, v down), matching _face
EYES = ((-0.435, 0.47), (0.435, 0.47))


def _face_inside(kind, u, v):
    if kind == "hollowling":
        return u * u + ((v - 0.5) / 0.52) ** 2 <= 1.02
    if kind == "keeper":
        if v < 0.55:
            return u * u + ((v - 0.55) / 0.57) ** 2 <= 1.0
        return abs(u) <= 0.86 - (v - 0.55) * 0.5
    if kind == "starved":
        return abs(u) <= 1 - 0.45 * max(0.0, v - 0.45) / 0.55 and not (v < 0.06 and abs(u) > 0.8)
    if kind == "thinned":
        return abs(u) <= 0.92 - 0.5 * max(0.0, v - 0.5) and v > 0.02
    # the Lurker: a long shield of stone
    if v > 0.6:
        return abs(u) <= 1 - 0.55 * ((v - 0.6) / 0.4) ** 1.4
    return True


def _face_paint(ctx):
    """Each one's mask: the Lurker's long stone shield split by a crack of light, the Hollowling's round, soft face,
    the Keeper's skull of bone, the Starved One's gaunt mask with a jagged maw, the Thinned's faint porcelain."""
    gx, gy, s = ctx["gx"], ctx["gy"], ctx["seed"]
    if not _front(ctx):
        return (0, 0, 0, 0), None
    kind = P["face"]
    u, v = _uv(ctx)
    if not _face_inside(kind, u, v):
        return (0, 0, 0, 0), None
    lo, hi = P["mask"]
    base = mix(lo, hi, 0.35 + 0.4 * noise(gx, gy, s, 4.0) + 0.25 * noise(gx, gy, s + 1, 1.5))
    # rounding: darker towards the edges, lighter on the brow and down the nose
    base = shade(base, 1.08 - 0.3 * abs(u) ** 2 + 0.08 * max(0.0, 1 - abs(u) / 0.12) * (0.3 < v < 0.7))
    glow = None
    for ex, ey in EYES:
        d = math.hypot((u - ex) / 0.3, (v - ey) / (0.2 if kind != "hollowling" else 0.24))
        if d < 1:
            base = mix((8, 6, 12), base, d ** 2.5 * 0.6)
    if kind == "lurker":
        if v < 0.4 and abs(u - 0.04 * math.sin(v * 30)) < 0.03:
            base, glow = shade(P["crack"], 0.6), P["crack"] + (230,)
        if abs(v - 0.8) < 0.018 and abs(u) < 0.32:
            base = (20, 16, 22)
    elif kind == "hollowling":
        d = math.hypot(u / 0.13, (v - 0.79) / 0.07)
        if d < 1:
            base = mix((10, 8, 16), (40, 40, 56), d ** 2)
        if 0.6 < v < 0.68 and abs(abs(u) - 0.6) < 0.1:
            base = mix(base, (150, 160, 200), 0.4)
    elif kind == "keeper":
        if 0.58 < v < 0.68 and abs(u) < 0.12 * (v - 0.56) / 0.12 + 0.02:
            base = (30, 22, 18)
        if 0.76 < v < 0.86 and abs(u) < 0.5:
            if ((u + 0.5) / 0.1) % 1 < 0.2 or v > 0.845:
                base = (40, 30, 24)
            else:
                base = shade(base, 1.1)
        if abs(noise(gx, gy, s + 21, 12.0) - 0.5) < 0.012:
            base = shade(base, 0.6)
    elif kind == "starved":
        # sunken cheeks and a wide, jagged maw
        if 0.55 < v < 0.72 and abs(abs(u) - 0.55) < 0.12:
            base = shade(base, 0.62)
        if 0.74 < v < 0.92 and abs(u) < 0.62 - (v - 0.74) * 0.8:
            tooth = ((u + 0.62) / 0.12) % 1
            jag = abs(tooth - 0.5) * 2
            if (v < 0.79 and jag > 0.35 + (v - 0.74) * 10) or (v > 0.87 and jag > 0.35 + (0.92 - v) * 10):
                base = mix((210, 200, 180), (240, 236, 220), noise(gx, gy, s, 1))
            else:
                base, glow = (60, 14, 10), P["crack"] + (120,)
    elif kind == "thinned":
        if abs(v - 0.78) < 0.01 and abs(u) < 0.2:
            base = shade(base, 0.8)
        base = mix(base, (255, 255, 255), 0.15)
    if glow is None and kind in ("lurker", "starved") and abs(noise(gx, gy, s + 41, 10.0) - 0.5) < 0.01 and v > 0.2:
        base, glow = shade(P["crack"], 0.6), P["crack"] + (200,)
    e = _mask_edge(ctx, lambda uu, vv: _face_inside(kind, uu, vv))
    if e == 0:
        base = shade(base, 0.6)
    elif e == 1:
        base = shade(base, 0.82)
    return base + (255,), glow


def _eye(ctx):
    """Each one's eyes: slanted almonds (the Lurker), wide round lamps (the Hollowling), pinpoints in rings (the
    Keeper), burning embers (the Starved One), thin pale slits (the Thinned)."""
    if not _front(ctx):
        return (0, 0, 0, 0), None
    kind = P["face"]
    u, v = _uv(ctx)
    v = v * 2 - 1
    if kind == "hollowling":
        d = math.hypot(u, v)
    elif kind == "keeper":
        d = math.hypot(u, v) * 1.1
    elif kind == "starved":
        d = math.hypot(u * 1.4, v * 1.2)
    elif kind == "thinned":
        d = math.hypot(u, v * 2.6)
    else:
        d = abs(v) / max(1e-6, max(0.0, 1 - u * u) ** 1.1) if abs(u) < 1 else 2
    if d > 1:
        return (0, 0, 0, 0), None
    k = 1 - d
    if kind == "keeper" and 0.35 < d < 0.55:
        return (40, 30, 20, 255), None
    c = mix(P["eye"], P["eye_hot"], k ** 0.8)
    if kind == "hollowling" and -0.55 < u < -0.2 and -0.6 < v < -0.2:
        c = (255, 255, 255)
    return c + (255,), c + (int(170 + 85 * k),)


# The masks and eyes at Minecraft's own size, drawn pixel for pixel: the mask is 10 by 10, the eyes (laid over it,
# columns 1-3 and 6-8 of rows 4-5) 3 by 2. M mask, L light, S shade, D socket, d half-shadow, K mouth, C crack of
# light, T tooth, R maw; on the eyes E the hot middle, e the light, W a catchlight, K dark.
FACE_PX = {
    "lurker": ["SMMMMCMMMS",
               "MMLMCMMLMM",
               "MLMMCMMMLM",
               "MMMCMMMMMM",
               "MDDDMMDDDM",
               "MDDDMMDDDM",
               "MMdMMMMdCM",
               "SMMMMMMMCS",
               "SMMKKKKMMS",
               ".SMMMMMMS."],
    "hollowling": [".SLLLLLLS.",
                   "SLLMLLMLLS",
                   "LMMMMMMMML",
                   "MMMMMMMMMM",
                   "MDDDMMDDDM",
                   "MDDDMMDDDM",
                   "SdDdMMdDdS",
                   "SMMMMMMMMS",
                   ".SMMKKMMS.",
                   "..SSMMSS.."],
    "keeper": [".SLLLLLLS.",
               "SLLMLLMLLS",
               "LLMMLLMMLL",
               "MMMMMMMMMM",
               "MDDDMMDDDM",
               "MDDDMMDDDM",
               "SMdMKKMdMS",
               "SMMMKKMMMS",
               ".KTKTKTKT.",
               ".SMSMSMSS."],
    "starved": ["SMMMMMMMMS",
                "MMCMMMMLMM",
                "MLMCMMMMCM",
                "MMMMMMMMMM",
                "MDDDMMDDDM",
                "MDDDMMDDDM",
                "SdMdMMdMCS",
                "STRTRTRTRS",
                "SRRRRRRRRS",
                ".SRTRTRTS."],
    "thinned": ["SLLLLLLLLS",
                "LLLLLLLLLL",
                "LLMLLLLMLL",
                "LMMLLLLMML",
                "LSSSLLSSSL",
                "LSSSLLSSSL",
                "LLSLLLLSLL",
                "LLLLLLLLLL",
                "SLLLSSLLLS",
                ".SLLLLLLS."],
}
EYE_PX = {
    "lurker": ["eEe", ".e."],
    "hollowling": ["WEe", "eee"],
    "keeper": ["eEe", "KeK"],
    "starved": ["eEe", "eee"],
    "thinned": ["eEe", "..."],
}
_PX = {}


def _px_colours():
    lo, hi = P["mask"]
    shine = lambda c, a=255: (c, tuple(c) + (a,))  # noqa: E731
    return {
        "M": (mix(lo, hi, 0.62), None), "L": (mix(lo, hi, 0.88), None), "S": (mix(lo, hi, 0.36), None),
        "D": ((12, 10, 16), None), "d": (mix((12, 10, 16), lo, 0.55), None), "K": ((20, 16, 22), None),
        "C": (shade(P["crack"], 0.6), tuple(P["crack"]) + (230,)), "T": ((226, 218, 200), None),
        "R": ((60, 14, 10), tuple(P["crack"]) + (120,)),
        "E": shine(P["eye_hot"]), "e": shine(P["eye"], 220), "W": shine((255, 255, 255)),
    }


def _face_px(ctx):
    key = ("face", P["face"])
    if key not in _PX:
        _PX[key] = elderlib.pixmap(FACE_PX[P["face"]], _px_colours())
    return _PX[key](ctx)


def _eye_px(ctx):
    key = ("eye", P["face"])
    if key not in _PX:
        _PX[key] = elderlib.pixmap(EYE_PX[P["face"]], _px_colours(), grain=0)
    return _PX[key](ctx)


MATERIALS = {
    "t_cloak": lambda c: _cloth(c),
    "t_cloak_ragged": lambda c: _cloth(c, ragged=True),
    "t_limb": lambda c: _sinew(c, "limb"),
    "t_skin": lambda c: _sinew(c, "skin"),
    "t_head": _head,
    "t_tendril": lambda c: _tendril(c),
    "t_drip": lambda c: _tendril(c, drip=True),
    "t_claw": _claw,
    "t_fray": _fray,
    "t_brow": _brow,
    "t_face": _face_paint,
    "t_face_px": _face_px,
    "t_eye_px": _eye_px,
    "t_eye": _eye,
}


if __name__ == "__main__":
    preview = sys.argv[1] if len(sys.argv) > 1 else None
    elderlib.MATS.update(MATERIALS)
    model = build()
    uv, used = elderlib.pack(model)
    for name, pal in SPECIES.items():
        P.clear()
        P.update(pal)
        img, glow = elderlib.paint(model, uv)
        img.save(os.path.join(TEX, name + ".png"))
        glow.save(os.path.join(TEX, name + "_glow.png"))
    elderlib.write_java(model, uv, "com.extremedimensions.client.entity", os.path.join(JAVA, "LurkerGeometry.java"))
    if preview:
        elderlib.export_preview(model, uv, os.path.join(preview, "lurker.json"))
    boxes = sum(len(p["boxes"]) for p in model.parts)
    print(f"tribe: {len(model.parts)} parts, {boxes} boxes, texture rows used {used}/{model.th}")
