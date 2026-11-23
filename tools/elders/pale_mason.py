"""The Pale Mason, after his concept sheet: some eighteen blocks of dark Enderman, long-limbed, under a hood and a
ragged mantle of pale end stone. His head is a tall pillar of black, its face dark but for two slanted eyes of violet
light, capped and framed by a hood of end-stone blocks whose sides hang down over his shoulders in dripping strips.
The mantle is end stone too, heaped in blocks about his neck and on his shoulders and hanging in long ragged strips,
open at the breast on a crack of violet light shaped like a cross: his core. Below the belt a robe of the same stone
hangs in layers to the ground, ragged at every hem, over long dark legs; under it a skirt of his own black. Chains of
gold hang in swags across his breast and hips and down from a golden bar behind his head, and behind his head turns a
great broken halo: a wheel of pale stone with gold spokes and gold spikes standing out of it, open where pieces of it
have broken away. His arms are long, wrapped in bands of stone, his hands great dark claws tipped with violet crystal.
A few pale blocks float about him.

Colour hierarchy (from the concept sheet): void black and deep violet for his body, pale cream end stone for all he
wears, gold only for chains, spokes and spikes, violet light for his eyes, his core and the crystal of his claws."""
import math

import elderlib
from elderlib import Model, _edge, _h, align_y, catenary, lerp3, mix, noise, pixmap, shade

TAU = 2 * math.pi


# ----------------------------------------------------------------------------- his materials

STONE_LO, STONE_HI = (188, 172, 162), (250, 242, 222)
STONE_SHADOW = (168, 146, 172)


def _ragged(ctx, k, cell=4):
    """Whether a pixel of a ragged strip is torn away: the strip ends in columns two pixels wide, each a whole number
    of blocks long, and blocks have fallen out of it towards the end (k: 0 at the top of the face, 1 at its end)."""
    s, px, py, fw, fh, face = ctx["seed"], ctx["px"], ctx["py"], ctx["fw"], ctx["fh"], ctx["face"]
    if face == "bottom":
        return True
    if face == "top":
        return False
    col = px // 2
    jag = min(cell * int(1 + 5 * _h(col, 13, s + fw)), int(fh * 0.4))
    if py > fh - 1 - jag:
        return True
    return k > 0.62 and _h(col, py // cell, s + 17) < (k - 0.62) * 1.1


def m_stone(ctx, ragged=False, tone=1.0, grime=0.0):
    """End stone laid in small blocks: each block its own shade of cream and bone, a dark seam under it and down its
    side, a lit lip along its top and the pits end stone has; lilac-grey where a block lies in shadow. Ragged, it hangs
    in strips of blocks ending at different lengths, blocks fallen out towards the end, grimed darker there."""
    s = ctx["seed"]
    px, py, fh, face = ctx["px"], ctx["py"], ctx["fh"], ctx["face"]
    row = py // 4
    off = 2 if row % 2 else 0
    col = (px + off) // 5
    ix, iy = (px + off) % 5, py % 4
    base = mix(STONE_LO, STONE_HI, 0.3 + 0.58 * _h(col, row, s) + 0.12 * noise(ctx["gx"], ctx["gy"], s + 3, 2.0))
    if _h(col, row, s + 9) > 0.84:
        base = mix(base, STONE_SHADOW, 0.5)
    if iy == 3:
        base = shade(base, 0.7)
    elif ix == 4:
        base = shade(base, 0.82)
    elif iy == 0:
        base = mix(base, (252, 248, 236), 0.25)
    if _h(px, py, s + 5) > 0.93:
        base = shade(base, 0.7)
    if face in ("top", "bottom"):
        base = shade(base, 0.82)
    k = py / max(1, fh - 1)
    if grime:
        base = mix(base, (88, 66, 100), grime * k * k)
    base = shade(base, tone)
    if ragged and _ragged(ctx, k):
        return (0, 0, 0, 0), None
    glow = None
    if iy == 3 and ix < 4 and _h(col, row, s + 31) > 0.985:
        # the void showing through a seam
        base, glow = (120, 60, 170), (170, 90, 255, 150)
    return base + (255,), glow


def m_void(ctx, veins=0.0, ragged=False, dark=False):
    """His body: the black-violet of an Enderman, a little lighter at the edges where violet light catches it, motes
    of violet in it; veined, cracks of violet light run through it here and there; ragged, it hangs in strips."""
    gx, gy, s = ctx["gx"], ctx["gy"], ctx["seed"]
    lo, hi = ((10, 4, 18), (40, 18, 60)) if dark else ((16, 8, 28), (66, 34, 98))
    base = mix(lo, hi, 0.2 + 0.55 * noise(gx, gy, s, 3.0) + 0.25 * noise(gx, gy, s + 1, 1.2))
    if _h(gx, gy, s + 4) > 0.955:
        base = (96, 56, 132)
    if ctx["face"] in ("top", "bottom"):
        base = shade(base, 0.8)
    if _edge(ctx) == 0:
        base = mix(base, (112, 66, 156), 0.5)
    k = ctx["py"] / max(1, ctx["fh"] - 1)
    if ragged and _ragged(ctx, k, 2):
        return (0, 0, 0, 0), None
    glow = None
    if veins and noise(gx, gy, s + 23, 6.0) > 1 - veins and abs(noise(gx * 0.7, gy * 0.7, s + 29, 5.0) - 0.5) < 0.03:
        base, glow = (120, 50, 180), (180, 96, 255, 210)
    return base + (255,), glow


def m_gold(ctx):
    """Gold, old but bright: warm where the light catches it, dark in the hollows, glints here and there."""
    gx, gy, s = ctx["gx"], ctx["gy"], ctx["seed"]
    base = mix((112, 70, 22), (234, 184, 78), 0.25 + 0.65 * noise(gx, gy, s, 1.6))
    if _h(gx, gy, s + 3) > 0.88:
        base = (255, 228, 142)
    if ctx["face"] == "top" or ctx["px"] == 0:
        base = mix(base, (255, 214, 120), 0.35)
    elif ctx["face"] == "bottom":
        base = shade(base, 0.7)
    return base + (255,), None


def m_crystal(ctx):
    """A claw of violet crystal lit from within: deep at its root, white towards the point, one facet bright."""
    fw, fh, px, py = ctx["fw"], ctx["fh"], ctx["px"], ctx["py"]
    k = py / max(1, fh - 1) if ctx["face"] not in ("top", "bottom") else 0.5
    c = mix((104, 36, 172), (236, 204, 255), k ** 1.3)
    if px == 0:
        c = mix(c, (250, 236, 255), 0.3)
    elif px == fw - 1:
        c = shade(c, 0.72)
    return c + (255,), c + (int(170 + 80 * k),)


# His face, deep in the hood: black, the shadow of the brim over its top, a spill of violet light round his eyes
# (which are parts of their own, over rows 8-9: see _head)
FACE = [
    "ssssssssssss",
    "ssssssssssss",
    "sskkkkkkkkss",
    "skkkkkkkkkks",
    "kkkdkkkkdkkk",
    "kkkkkkkkkkkk",
    "kkkkkkkkkkkk",
    "kggkkkkkkggk",
    "kgggekkegggk",
    "kegggkkgggek",
    "kkgggkkgggkk",
    "kkkggkkggkkk",
    "kkkkkkkkkkkk",
    "kdkkkkkkkkdk",
    "kkkkkkkkkkkk",
    "kkkkmkkkkkkk",
    "kkkkkkkkkkkk",
    "kkdkkkkkkdkk",
    "kkkkkkkkkkkk",
    "kkkkkkkkkkkk",
    "kkkkkkkmkkkk",
    "kdkkkkkkkkdk",
    "kkkkkkkkkkkk",
    "kkkkkkkkkkkk",
    "kkkdkkkkdkkk",
    "kkkkkkkkkkkk",
    "kkkkkkkkkkkk",
    "kkkkkkkkkkkk",
    "kdkkkkkkkkdk",
    "kkkkkkkkkkkk",
]
# His eyes: slits of violet light slanting down towards each other, white at their inner ends (as seen from the front:
# EYE_R is his right eye, on the viewer's left)
EYE_R = ["VVv.", ".vWW"]
EYE_L = [".vVV", "WWv."]
# The core at his breast: a crack of violet light shaped like a cross, white-hot where its arms meet, dark cracked
# edges round it
CORE = [
    "....cv....",
    "....Vvc...",
    "...cVV....",
    "....VWvc..",
    "...cvWVc..",
    "..cvVWWvc.",
    "cvvVWWWVvc",
    ".cvVWWWVvc",
    "..cvWWVc..",
    "...cVWv...",
    "...cVVc...",
    "....Vvc...",
    "...cVc....",
    "....vc....",
]

_VIOLET = {"v": ((140, 60, 220), (186, 104, 255, 225)), "V": ((204, 136, 255), (222, 168, 255, 245)),
           "W": ((250, 238, 255), (255, 246, 255, 255))}

elderlib.MATS.update({
    "pm_stone": lambda c: m_stone(c),
    "pm_stone_ragged": lambda c: m_stone(c, ragged=True, grime=0.4),
    "pm_stone_shade": lambda c: m_stone(c, tone=0.8),
    "pm_stone_shade_ragged": lambda c: m_stone(c, ragged=True, tone=0.8, grime=0.45),
    "pm_void": lambda c: m_void(c),
    "pm_void_veins": lambda c: m_void(c, veins=0.35),
    "pm_void_dark": lambda c: m_void(c, dark=True),
    "pm_void_ragged": lambda c: m_void(c, ragged=True, dark=True, veins=0.15),
    "pm_gold": m_gold,
    "pm_crystal": m_crystal,
    "pm_face_px": pixmap(FACE, {
        "s": ((5, 2, 9), None), "k": ((15, 8, 23), None), "d": ((9, 5, 15), None), "m": ((70, 36, 100), None),
        "g": ((44, 18, 66), (120, 50, 190, 120)), "e": ((70, 26, 110), (150, 70, 230, 170))}),
    "pm_eye_r_px": pixmap(EYE_R, _VIOLET, grain=0),
    "pm_eye_l_px": pixmap(EYE_L, _VIOLET, grain=0),
    "pm_core_px": pixmap(CORE, dict(_VIOLET, c=((36, 12, 54), None)), grain=0),
})


# ----------------------------------------------------------------------------- the model


def build():
    m = Model("PaleMason", 1024, 512, seed=173)
    R = m.part("root", None, (0, 24, 0), anim=True)
    body = m.part("body", R, (0, 0, 0), anim=True)
    # body, torso and head keep their own boxes in a "core" child, so he can be laid together block by block as he
    # forms (see PaleMasonModel)
    core = m.part("bodycore", body, (0, 0, 0), anim=True)
    _hips(m, core)
    for side, sx in (("l", 1), ("r", -1)):
        _leg(m, body, side, sx)
    _skirt(m, body)
    torso = m.part("torso", body, (0, -88, 0), anim=True)
    _torso(m, m.part("torsocore", torso, (0, 0, 0), anim=True))
    _mantle(m, torso)
    _chains(m, torso, core)
    head = m.part("head", torso, (0, -60, 0), anim=True)
    _head(m, head)
    _halo(m, head)
    for side, sx in (("l", 1), ("r", -1)):
        _arm(m, torso, side, sx)
    _orbit(m, R)
    return m


def _spike(m, name, parent, at, rot, length, base, mat="pm_gold"):
    """A spike along local -Y: three tapering boxes to a point."""
    sp = m.part(name, parent, at, rot)
    y = 0.0
    for frac, w in ((0.45, 1.0), (0.35, 0.62), (0.2, 0.3)):
        seg = length * frac
        m.box(sp, -base * w / 2, y - seg, -base * w / 2, base * w, seg, base * w, mat)
        y -= seg
    return sp


def _strip(m, name, parent, at, rot, width, length, depth, mat, group):
    """A strip hanging along local +Y from its top edge."""
    p = m.part(name, parent, at, rot, group=group)
    m.box(p, -width / 2, 0, -depth / 2, width, length, depth, mat)
    return p


def _chain(m, name, parent, at, length, group="chains", thick=0.8, link=2.2, weight=0.0):
    """A chain of gold hanging straight down from `at` (in the parent's space), its links turning their flat sides;
    with a weight, a block of stone hangs on its end."""
    c = m.part(name, parent, at, m.hang_rot(parent), group=group)
    y, i = 0.0, 0
    while y < length - 0.5:
        if i % 2 == 0:
            m.box(c, -thick, y - 0.3, -thick * 0.35, thick * 2, link + 0.6, thick * 0.7, "pm_gold")
        else:
            m.box(c, -thick * 0.35, y - 0.3, -thick, thick * 0.7, link + 0.6, thick * 2, "pm_gold")
        y += link
        i += 1
    if weight:
        m.box(c, -weight / 2, y, -weight / 2, weight, weight, weight, "pm_stone")
    return c


def _swag(m, name, parent, p0, p1, sag, n, thick=0.8):
    """A chain of gold hung between two points (in the parent's space), on a part at their middle that swings it."""
    mid = [(p0[i] + p1[i]) / 2 for i in range(3)]
    h = m.part(name, parent, mid, group="swag")
    pts = [[q[i] - mid[i] for i in range(3)] for q in catenary(p0, p1, sag, n)]
    m.rope(f"{name}_", h, pts, thick, "pm_gold", links=True)
    return h


def _drape(m, name, parent, p0, p1, sag, n, lean, width=4.4, depth=2.2, mat="pm_stone"):
    """A fold of stone hung between two points (in the parent's space) like a swag of cloth, a band of blocks along the
    curve leaning out by `lean` for every pixel it sags, to lie over what hangs under it (group drape)."""
    mid = [(p0[i] + p1[i]) / 2 for i in range(3)]
    h = m.part(name, parent, mid, group="drape")
    pts = []
    for i in range(n + 1):
        k = i / n
        p = lerp3(p0, p1, k)
        d = sag * 4 * k * (1 - k)
        p[1] += d
        p[2] += lean * d
        pts.append([p[j] - mid[j] for j in range(3)])
    for i in range(n):
        a, b = pts[i], pts[i + 1]
        d = [b[j] - a[j] for j in range(3)]
        seg = m.part(f"{name}_{i}", h, a, align_y(d))
        m.box(seg, -width / 2, -0.4, -depth / 2, width, math.sqrt(sum(c * c for c in d)) + 0.8, depth, mat)
    return h


def _front_dist(a):
    """How far round from his front (-Z) the angle a is, where a = 0 is his back (+Z)."""
    return abs((a - math.pi + math.pi) % TAU - math.pi)


# ----------------------------------------------------------------------------- the hips, legs and robe


def _hips(m, core):
    """His hips, a belt of end-stone blocks, and a dark skirt of his own under the robe."""
    m.box(core, -10, -97, -6.5, 20, 13, 13, "pm_void")
    m.box(core, -9.5, -86, -6, 19, 18, 12, "pm_void_dark")
    m.box(core, -11.5, -98, -8, 23, 7, 16, "pm_stone")
    for k in range(7):
        a = TAU * k / 7 + 0.3
        b = m.part(f"belt_{k}", core, (11.6 * math.sin(a), -94.5, 8 * math.cos(a)), (0.1 * (k % 2), a, 0.12 * (k % 3 - 1)))
        sz = 4.5 + (k * 3) % 3
        m.box(b, -sz / 2, -sz / 2, -sz / 2, sz, sz, sz, "pm_stone")


def _leg(m, body, side, sx):
    """A long dark leg, end-stone guards at the knee and bands round the shin, a long foot."""
    leg = m.part(f"leg_{side}", body, (6.5 * sx, -86, 0), (0, 0, -0.03 * sx), anim=True)
    m.box(leg, -4, -2, -4, 8, 46, 8, "pm_void")
    m.box(leg, -4.6, 40, -5.4, 9.2, 8, 9.8, "pm_stone")
    m.box(leg, -3.5, 46, -3.5, 7, 37, 7, "pm_void")
    m.box(leg, -4.1, 61, -4.1, 8.2, 3.5, 8.2, "pm_stone_shade")
    m.box(leg, -5, 73.5, -5.2, 10, 7.5, 10.4, "pm_stone")
    m.box(leg, -4.5, 81, -11, 9, 5, 15, "pm_void")
    m.box(leg, -4.8, 79.5, -11.6, 9.6, 3, 6.5, "pm_stone")


def _skirt(m, body):
    """The robe from the belt to the ground (group robe): a dark under-skirt open at the front, over it long strips of
    end stone, longest at the back and short at the front over the knees, and over those a short layer of tatters."""
    n = 14
    for i in range(n):
        a = TAU * i / n + 0.1
        if _front_dist(a) < 0.62:
            continue
        length = 84 + (i * 5) % 7
        _strip(m, f"under_{i}", body, (10 * math.sin(a), -90, 7.5 * math.cos(a)), (0.13 + 0.02 * (i % 3), a, 0), 9, length, 2,
               "pm_void_ragged", "robe")
    n = 12
    for k in range(n):
        a = TAU * k / n
        front = (1 - math.cos(a)) / 2
        length = 89 - 50 * front ** 1.2 + (k * 7) % 9 - 4
        _strip(m, f"over_{k}", body, (12 * math.sin(a), -93, 9 * math.cos(a)), (0.16 + 0.025 * (k % 3), a, 0), 8, length, 2.4,
               "pm_stone_ragged", "robe")
    for k in range(12):
        a = TAU * k / 12 + 0.26
        if _front_dist(a) < 0.5:
            continue
        _strip(m, f"hip_{k}", body, (13.4 * math.sin(a), -96, 9.8 * math.cos(a)), (0.42, a, 0), 8, 22 + (k * 5) % 11, 1.8,
               "pm_stone_shade_ragged", "robe")


# ----------------------------------------------------------------------------- the torso and mantle


def _torso(m, t):
    """A long, narrow body of black, broad at the shoulders, and at the breast the core (`chest`, which the animation
    pulses): a crack of violet light shaped like a cross."""
    m.box(t, -8, -24, -6, 16, 26, 12, "pm_void")
    m.box(t, -11, -46, -7, 22, 24, 14, "pm_void_veins")
    m.box(t, -16.5, -57, -7.5, 33, 12, 15, "pm_void")
    m.box(t, -4.5, -63, -4.5, 9, 8, 9, "pm_void")
    # the ribs, standing out down his flanks
    for k in range(3):
        for sx in (1, -1):
            rb = m.part(f"rib_{'l' if sx > 0 else 'r'}{k}", t, (sx * 10.2, -42 + 6 * k, -2), (0, 0, sx * (0.5 + 0.12 * k)))
            m.box(rb, -1, -1, -5.5, 2, 9 - k, 11, "pm_void")
    chest = m.part("chest", t, (0, -34, -7.1), anim=True, detail=1)
    m.box(chest, -5, -7, -0.5, 10, 14, 0.5, "pm_core_px")


def _mantle(m, torso):
    """End stone heaped in blocks round his neck and on his shoulders, and hanging from them in two layers of ragged
    strips (group mantle), long down his back like a cape, open at the breast on his core."""
    m.box(torso, -13, -64, -9, 26, 9, 19, "pm_stone")
    for k in range(10):
        a = TAU * k / 10 + 0.2
        ch = m.part(f"collar_{k}", torso, (12.2 * math.sin(a), -63.5, 0.5 + 8.6 * math.cos(a)),
                    (0.22 * math.cos(a), a, 0.18 * math.sin(a)))
        sz = 5 + (k * 3) % 4
        m.box(ch, -sz / 2, -sz * 0.7, -sz / 2, sz, sz, sz, "pm_stone")
    for sx in (1, -1):
        side = "l" if sx > 0 else "r"
        sc = m.part(f"shoulder_{side}", torso, (14.5 * sx, -58.5, 0), (0, 0, 0.3 * sx))
        m.box(sc, -8, -4, -8.6, 16, 8, 17.2, "pm_stone")
        m.box(sc, -5.5, -7.5, -6.5, 11, 4, 12, "pm_stone")
        m.box(sc, -2.5 + 4 * sx, -10, -3, 6, 3, 6, "pm_stone")
        m.box(sc, -9 + (9 if sx > 0 else 0), 3, -9, 9, 5, 18, "pm_stone_shade")
    n = 18
    for i in range(n):
        a = TAU * i / n + 0.09
        if _front_dist(a) < 0.8:
            continue
        back = (1 + math.cos(a)) / 2
        length = 28 + 40 * back ** 1.6 + (i * 7) % 11
        _strip(m, f"mantle_{i}", torso, (16.5 * math.sin(a), -60, 9.2 * math.cos(a)), (0.36 - 0.12 * back, a, 0), 8, length, 2.4,
               "pm_stone_ragged", "mantle")
    n = 16
    for i in range(n):
        a = TAU * (i + 0.5) / n
        if _front_dist(a) < 0.72:
            continue
        back = (1 + math.cos(a)) / 2
        length = 26 + 56 * back + (i * 5) % 8
        _strip(m, f"mantlein_{i}", torso, (14.6 * math.sin(a), -57, 8.2 * math.cos(a)), (0.22 - 0.1 * back, a, 0), 8, length, 2,
               "pm_void_ragged", "mantle")
    # folds of stone: one across his breast over the core, three down his back
    _drape(m, "drape_f", torso, [-12.5, -61.5, -8.6], [12.5, -61.5, -8.6], 9, 12, -0.14)
    for k, (sag, z) in enumerate(((9, 9.4), (19, 9.8), (30, 10.2))):
        _drape(m, f"drape_b{k}", torso, [-15.5 + k, -61 + k, z], [15.5 - k, -61 + k, z], sag, 14, 0.3, 4.4 - 0.4 * k)


def _chains(m, torso, core):
    """Chains of gold: a swag under the collar and one slung under his core like a necklace (group swag), one across
    the belt, and the loose ends of chains hanging from his hips (group chains)."""
    _swag(m, "swag_0", torso, [-12.5, -52, -10.2], [12.5, -52, -10.2], 10, 14)
    _swag(m, "swag_1", torso, [-15.5, -53, -9.2], [12.5, -49, -9.2], 25.5, 18)
    for sx in (1, -1):
        m.box(core, 10 * sx - 1.6, -96.6, -10.4, 3.2, 3.2, 2.4, "pm_gold")
    _swag(m, "swag_2", core, [-10, -95, -10.6], [10, -95, -10.6], 7, 12)
    _swag(m, "swag_3", core, [-10, -94.5, -10.9], [10, -94.5, -10.9], 15, 14)
    for k, (x, length) in enumerate(((-10.6, 30), (-9.2, 18), (10.4, 24), (9.0, 36))):
        _chain(m, f"hipchain_{k}", core, (x, -94.5, -10.6), length)


# ----------------------------------------------------------------------------- the head and halo


def _head(m, head):
    """A tall pillar of black, its face dark under the brim of the hood but for two slanted eyes of violet light
    (`eye_l`, `eye_r`, which blaze in the animation); the hood (`hood`) is end stone, stepped on top, framing the face
    and hanging in dripping strips over his shoulders and down his back (group veil)."""
    hc = m.part("headcore", head, (0, 0, 0), anim=True)
    m.box(hc, -7, -38, -7, 14, 38, 14, "pm_void")
    face = m.part("face", hc, (0, 0, 0), detail=1)
    m.box(face, -6, -31, -7.5, 12, 30, 0.5, "pm_face_px")
    for side, sx in (("l", 1), ("r", -1)):
        e = m.part(f"eye_{side}", hc, (3 * sx, -22, -7.8), anim=True, detail=1)
        m.box(e, -2, -1, 0, 4, 2, 0.5, f"pm_eye_{side}_px")
    hood = m.part("hood", head, (0, 0, 0), anim=True)
    m.box(hood, -9.5, -45, -9, 19, 8, 19.5, "pm_stone")
    m.box(hood, -7, -49, -6.5, 14, 4, 14.5, "pm_stone")
    m.box(hood, -4.5, -52.5, -3.5, 9, 3.5, 10, "pm_stone_shade")
    m.box(hood, -9.6, -38, -9.6, 19.2, 7, 3, "pm_stone")
    for sx in (1, -1):
        m.box(hood, 7.2 if sx > 0 else -10, -38, -9.2, 2.8, 24, 19.6, "pm_stone_ragged")
        m.box(hood, 5.8 if sx > 0 else -9.5, -31, -9.6, 3.7, 13, 3, "pm_stone_ragged")
    m.box(hood, -10.3, -38, 7.8, 20.6, 30, 3, "pm_stone_ragged")
    # blocks loose on the hood: on the brim, on its crown and its shoulders
    for k, (x, y, z, sz, r) in enumerate(((-6.5, -38.5, -9.8, 4, 0.2), (2.5, -38.6, -9.9, 4.5, -0.15), (-8.2, -45, 2, 5, 0.3),
                                          (8.0, -44.5, -3, 4.5, -0.25), (3.5, -50, 3, 4, 0.4), (-3, -49.5, -4.5, 3.5, -0.3))):
        b = m.part(f"hoodblock_{k}", hood, (x, y, z), (r * 0.5, r, r * 0.7))
        m.box(b, -sz / 2, -sz / 2, -sz / 2, sz, sz, sz, "pm_stone")
    # strips hanging from the hood's sides and back, over his shoulders
    for j in range(14):
        if j < 8:
            sx = 1 if j % 2 else -1
            r = j // 2
            at = (sx * 9.6, -9 + (r % 2) * 1.5, -7 + 4.8 * r)
            rot = (0.1, 0, -0.12 * sx)
            width = 3.6
        else:
            r = j - 8
            at = (-8 + 3.2 * r, 4, 10.4)
            rot = (0.14, 0, 0.04 * (r - 2.5))
            width = 3.2
        length = 16 + (j * 7) % 14
        _strip(m, f"veil_{j}", hood, at, rot, width, length, 2.2, "pm_stone_ragged", "veil")


def _halo(m, head):
    """The broken halo behind his head: a golden bar across it with spiked ends, chains hanging from it (group
    chains), and the wheel itself (`halo`, which the animation turns): a ring of pale stone blocks broken open in two
    places, a few blocks still sitting on its rim and fragments of it hanging in the gaps, an inner ring of gold, gold
    spokes to a hub and gold spikes standing out round it."""
    fr = m.part("halo_frame", head, (0, -30, 20), (-0.42, 0, 0), anim=True)
    m.box(fr, -33, -1.3, -1.3, 66, 2.6, 2.6, "pm_gold")
    m.box(fr, -3.4, -3.4, -2.2, 6.8, 6.8, 4.4, "pm_gold")
    for sx in (1, -1):
        side = "l" if sx > 0 else "r"
        _spike(m, f"barspike_{side}", fr, (33 * sx, 0, 0), (0, 0, sx * math.pi / 2), 10, 2.6)
        m.box(fr, 28 * sx - 0.9, -5, -0.9, 1.8, 10, 1.8, "pm_gold")
        _spike(m, f"barspike_{side}u", fr, (28 * sx, -5, 0), (0, 0, 0), 4, 1.6)
        _spike(m, f"barspike_{side}d", fr, (28 * sx, 5, 0), (0, 0, math.pi), 4, 1.6)
    for k, (x, length) in enumerate(((-30.5, 42), (-22, 26), (22, 32), (30.5, 48))):
        _chain(m, f"halochain_{k}", fr, (x, 1.3, 0), length, weight=5 if k == 0 else 0)
    halo = m.part("halo", fr, (0, 0, 2.6), anim=True)
    R, n = 24.0, 22
    gaps = {3, 4, 14}
    seg = TAU * R / n + 0.9
    for i in range(n):
        if i in gaps:
            continue
        a = TAU * i / n
        p = m.part(f"halo_{i}", halo, (R * math.cos(a), R * math.sin(a), 0), (0, 0, a))
        gap_hi, gap_lo = (i + 1) % n in gaps, (i - 1) % n in gaps
        # broken off short at a gap, its end chipped
        y0 = -seg / 2 + (2.2 if gap_lo else 0)
        y1 = seg / 2 - (2.2 if gap_hi else 0)
        m.box(p, -2.6, y0, -2.3, 5.2, y1 - y0, 4.6, "pm_stone")
        if gap_hi:
            m.box(p, -1.4, y1, -1.5, 2.6, 1.6, 3.0, "pm_stone_shade")
        if gap_lo:
            m.box(p, -1.2, y0 - 1.6, -1.5, 2.6, 1.6, 3.0, "pm_stone_shade")
    # fragments of it, hanging in the gaps
    for k, (a, dr, sz) in enumerate(((TAU * 3.3 / 22, 1.5, 3.6), (TAU * 4.1 / 22, -1.2, 2.8), (TAU * 14 / 22, 2.2, 3.2))):
        f = m.part(f"halofrag_{k}", halo, ((R + dr) * math.cos(a), (R + dr) * math.sin(a), 0.6 * (k - 1)), (0.5 * k, 0.7, a + 0.6))
        m.box(f, -sz / 2, -sz / 2, -sz / 2, sz, sz, sz, "pm_stone")
    # blocks sitting on the rim, turned on their corners
    for k, a in enumerate((0.55, 2.3, 4.05, 5.45)):
        b = m.part(f"haloblock_{k}", halo, (R * math.cos(a), R * math.sin(a), -0.4), (0, 0, a + math.pi / 4))
        m.box(b, -3.6, -3.6, -3.1, 7.2, 7.2, 6.2, "pm_stone")
    # the inner ring of gold, broken too, and the spokes
    for i in range(24):
        if i in (5, 6, 17):
            continue
        a = TAU * i / 24
        p = m.part(f"haloin_{i}", halo, (17 * math.cos(a), 17 * math.sin(a), -0.3), (0, 0, a))
        m.box(p, -0.7, -2.4, -0.7, 1.4, 4.8, 1.4, "pm_gold")
    for k in range(8):
        a = TAU * k / 8 + math.pi / 8
        sp = m.part(f"halospoke_{k}", halo, (0, 0, -0.3), (0, 0, a))
        m.box(sp, -0.8, 3, -0.8, 1.6, R - 5.5, 1.6, "pm_gold")
    m.box(halo, -2.6, -2.6, -1.6, 5.2, 5.2, 3.2, "pm_gold")
    # spikes standing out round it, the long ones crossed near their points
    for k in range(12):
        a = TAU * k / 12 + math.pi / 12
        if abs(((a - TAU * 3.5 / 22) + math.pi) % TAU - math.pi) < 0.25:
            continue  # broken away with the stone
        long = k % 2 == 0
        length = 13 if long else 7.5
        r0 = R + 2.4
        _spike(m, f"halospike_{k}", halo, (r0 * math.cos(a), r0 * math.sin(a), 0), (0, 0, a + math.pi / 2), length, 2.2)
        if long:
            cg = m.part(f"halocross_{k}", halo, ((r0 + 6.5) * math.cos(a), (r0 + 6.5) * math.sin(a), 0), (0, 0, a))
            m.box(cg, -0.7, -2.6, -0.7, 1.4, 5.2, 1.4, "pm_gold")


# ----------------------------------------------------------------------------- the arms


def _hand(m, fore, side, sx, at):
    """A great hand of black, the palm turned back, four long fingers of three joints and a thumb (their roots in group
    claw_l or claw_r), every one ending in a claw of violet crystal."""
    ha = m.part(f"hand_{side}", fore, at, (0.12, 0, 0), anim=True)
    m.box(ha, -5, 0, -2.2, 10, 11, 4.4, "pm_void")
    m.box(ha, -5.4, 8.6, -2.7, 10.8, 3, 5.4, "pm_void")
    m.box(ha, -4.2, 1.5, -2.9, 8.4, 4, 1, "pm_stone_shade")
    for f in range(4):
        x = -3.75 + 2.5 * f
        long = 1.0 if f in (1, 2) else 0.82
        fr = m.part(f"claw_{side}{f}", ha, (x, 10.6, 0), (0.22, 0, -x / 3.75 * 0.2), group=f"claw_{side}")
        m.box(fr, -1.1, 0, -1.1, 2.2, 7.5 * long, 2.2, "pm_void")
        m.box(fr, -1.35, 6.2 * long, -1.35, 2.7, 2, 2.7, "pm_void")
        fm = m.part(f"clawm_{side}{f}", fr, (0, 7.5 * long, 0), (0.25, 0, 0))
        m.box(fm, -0.95, 0, -0.95, 1.9, 6 * long, 1.9, "pm_void")
        ft = m.part(f"clawt_{side}{f}", fm, (0, 6 * long, 0), (0.3, 0, 0))
        m.box(ft, -1.05, -0.4, -1.05, 2.1, 4, 2.1, "pm_crystal")
        m.box(ft, -0.65, 3.5, -0.65, 1.3, 3.6, 1.3, "pm_crystal")
        m.box(ft, -0.3, 7, -0.3, 0.6, 1.8, 0.6, "pm_crystal")
    th = m.part(f"claw_{side}t", ha, (-5.2 * sx, 2.5, -0.8), (0.3, 0, 0.75 * sx), group=f"claw_{side}")
    m.box(th, -1.1, 0, -1.1, 2.2, 6.5, 2.2, "pm_void")
    tt = m.part(f"clawt_{side}t", th, (0, 6.5, 0), (0.3, 0, 0))
    m.box(tt, -0.95, -0.4, -0.95, 1.9, 3.6, 1.9, "pm_crystal")
    m.box(tt, -0.55, 3.2, -0.55, 1.1, 3.2, 1.1, "pm_crystal")
    return ha


def _arm(m, torso, side, sx):
    """A long arm hanging a little forward: an end-stone guard on the shoulder with tatters of stone hanging from it
    (group sleeve), bands of stone wound round the upper arm and the forearm, a band of gold, a cuff at the wrist."""
    arm = m.part(f"arm_{side}", torso, (18.5 * sx, -52, 0), (-0.08, 0, -0.2 * sx), anim=True)
    m.box(arm, -4, -2, -4, 8, 40, 8, "pm_void")
    m.box(arm, -6.2, -6, -6.2, 12.4, 11, 12.4, "pm_stone")
    m.box(arm, -4.5 + 1.5 * sx, -9.5, -4.5, 8, 4, 9, "pm_stone_shade")
    for j, (x, z, length) in enumerate(((6.4, -3.2, 18), (6.6, 1.0, 26), (2.5, 6.6, 20), (-2, 6.6, 12))):
        _strip(m, f"sleeve_{side}{j}", arm, (x * sx, 3.5, z), (0.06 * (1 if z > 0 else -1), 0, -0.1 * sx * (1 if x > 4 else 0.3)),
               3.6, length, 1.8, "pm_stone_ragged", "sleeve")
    _swag(m, f"armchain_{side}", arm, [6.8 * sx, 1, -5], [6.8 * sx, 3, 5], 22, 14)
    fo = m.part(f"forearm_{side}", arm, (0, 38, 0), (-0.32, 0, 0.04 * sx), anim=True)
    m.box(fo, -3.6, -1, -3.6, 7.2, 36, 7.2, "pm_void")
    m.box(fo, -4.6, -3, -4.6, 9.2, 6, 9.2, "pm_stone")
    for k, (y, h, r) in enumerate(((13, 3.5, 0.12),)):
        w = m.part(f"wrap_{side}{k}", fo, (0, y, 0), (r, 0, -r * 0.8))
        m.box(w, -4.2, -h / 2, -4.2, 8.4, h, 8.4, "pm_stone_shade" if k == 1 else "pm_stone")
    m.box(fo, -4.3, 22.5, -4.3, 8.6, 1.8, 8.6, "pm_gold")
    m.box(fo, -5.4, 25.5, -5.4, 10.8, 8.5, 10.8, "pm_stone")
    m.box(fo, -2.5 + 4.6 * sx, 23.5, -3, 5, 5, 6, "pm_stone_shade")
    return _hand(m, fo, side, sx, (0, 34.5, 0))


# ----------------------------------------------------------------------------- the floating blocks


def _orbit(m, root):
    """Blocks of pale stone floating about him (each in group float, all on `orbit`, which turns them about him)."""
    orb = m.part("orbit", root, (0, 0, 0), anim=True)
    for k, (r, a, y, sz) in enumerate(((40, 0.9, -128, 6), (44, 2.6, -96, 4.5), (38, 4.1, -158, 5), (46, 5.4, -112, 7),
                                       (42, 3.4, -176, 4), (48, 1.8, -70, 5.5), (36, 5.9, -186, 3.5))):
        b = m.part(f"float_{k}", orb, (r * math.sin(a), y, r * math.cos(a)), (0.6 * k, 0.9 * k, 0.4 * k), group="float")
        m.box(b, -sz / 2, -sz / 2, -sz / 2, sz, sz, sz, "pm_stone")
        if k % 3 == 0:
            m.box(b, -sz / 2 - 0.3, -0.7, -sz / 2 - 0.3, sz + 0.6, 1.4, sz + 0.6, "pm_gold")
