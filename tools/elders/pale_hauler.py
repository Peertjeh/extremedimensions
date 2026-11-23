"""The Pale Hauler, after its concept sheet: one of the Endermen of the Pale Reach, an Enderman's height and lankier
still, a little hunched. Its body and limbs are the near-black violet of the End's own, with faint cracks of void light
in them; its arms hang to its knees and end in big hands of four long fingers, each in two joints, curled and clawed;
its legs are long and thin. A deep hood of pale end-stone cloth hides its head, its peak falling back, and inside it is
nothing but shadow and two bars of violet light for eyes. Over its shoulders, breast and back hangs a shaggy mantle of
that cloth torn into many ragged strips, short over the shoulders, to the hips in front and to the knees behind, with
cracks of violet light running through it and violet light at the ends of many of the strips; every strip is a part of
its own, so it can sway.

At Minecraft's own size (one pixel to a model pixel; only the eyes are fine parts, for their white-hot cores), so it
stands with the game's own creatures. The part names and the poses at rest are the ones PaleHaulerModel animates; its
glow is recoloured for its moods by tools/lurker/moods.py (blue while it hauls, red as it leaps).

Colours (from the concept sheet): cloth #968C70 to #E8E0BE, body #0E0816 to #4E366C, cracks in the cloth #BE50FF,
eyes #D678FF with a #FAE2FF core, the ends of the strips #B05AF0."""
import math

import elderlib
from elderlib import Model, _edge, _h, align_y, mix, noise, shade

CLOTH_LO, CLOTH_HI = (150, 140, 112), (232, 224, 190)
GRIME, CREASE = (104, 94, 82), (122, 108, 122)
VOID = ((14, 8, 22), (22, 12, 34), (30, 18, 44), (46, 30, 64), (64, 40, 92), (78, 54, 108))
CRACK = (190, 80, 255)
EYE, EYE_HOT = (214, 120, 255), (250, 226, 255)
TIP, TIP_HOT = (176, 90, 240), (214, 150, 255)
SIDES = ("north", "south", "east", "west")

# The stance at rest (radians): how far the knees bend, the lean of the belly and of the chest on it (a hunch), how far
# the neck reaches forward. The length of a thigh and of a shin, the height of the hips over the ground and how far
# back they sit (pixels).
KNEE = 0.2
BELLY, CHEST, NECK = 0.12, 0.22, 0.55
LEG = 10.75
FOOT = 1.5
HIP = 2 * LEG * math.cos(KNEE) + FOOT
HIP_Z = 1.5


# ----------------------------------------------------------------------------- materials


def _jag(ctx, deep=3):
    """How many pixels are torn off the end of a column of a ragged strip."""
    return int((deep + 1) * _h(ctx["px"], 11, ctx["seed"] + ctx["fw"])) if deep else 0


def m_cloth(ctx, ragged=0, lit=False, inside=None):
    """Coarse cloth the pale of end stone: long folds down it, darker and a little violet in their creases, a grain of
    lighter and darker threads, the pits end stone has, grime towards a torn end, and now and then a crack of violet
    light running down it. Ragged, its end is torn into columns of different lengths with holes eaten into it above;
    lit, the end of each column burns violet. The faces named in `inside` are the inside of the hood, in shadow."""
    gx, gy, s = ctx["gx"], ctx["gy"], ctx["seed"]
    px, py, fw, fh, face = ctx["px"], ctx["py"], ctx["fw"], ctx["fh"], ctx["face"]
    if inside and face in inside:
        return mix((10, 6, 16), (26, 17, 32), 0.5 * noise(gx, gy, s, 2.0)) + (255,), None
    if ragged and face == "bottom":
        return (0, 0, 0, 0), None
    fold = 0.5 + 0.5 * math.sin(px * 1.3 + 3.2 * noise(gx * 0.3, gy * 0.06, s, 4.0))
    t = 0.3 + 0.5 * fold + 0.3 * noise(gx, gy, s + 3, 1.6)
    base = mix(CLOTH_LO, CLOTH_HI, t)
    if t < 0.42:
        base = mix(base, CREASE, 0.45)
    g = _h(gx, gy, s + 5)
    if g > 0.88:
        base = shade(base, 1.06)
    elif g < 0.12:
        base = shade(base, 0.9)
    if _h(gx, gy, s + 7) > 0.965:
        base = shade(base, 0.72)
    k = py / max(1, fh - 1)
    if face == "top":
        base = shade(base, 1.03)
    elif face == "bottom":
        base = shade(base, 0.75)
    elif ragged:
        base = mix(base, GRIME, 0.55 * k * k)
    glow = None
    if ragged and face in SIDES:
        cut = fh - 1 - _jag(ctx, ragged)
        if py > cut or (k > 0.7 and py < cut - 1 and _h(gx, gy, s + 13) < (k - 0.7) * 0.5):
            return (0, 0, 0, 0), None
        if lit and py >= cut - 1:
            hot = py == cut
            return (TIP_HOT if hot else TIP) + (255,), (TIP_HOT if hot else TIP) + (240 if hot else 200,)
    # now and then a crack of void light running down the cloth
    if face in SIDES and abs(noise(gx * 1.1, gy * 0.22, s + 21, 3.0) - 0.5) < 0.02 and noise(gx * 0.6, gy * 0.6, s + 23, 6.0) > 0.64:
        base, glow = (124, 58, 172), CRACK + (215,)
    return base + (255,), glow


def m_void(ctx, veins=0.0):
    """Its body: the near-black violet of the End's own, mottled in blocks of a pixel or two, a little lighter where
    the light catches its edges, and veined with faint cracks of void light."""
    gx, gy, s = ctx["gx"], ctx["gy"], ctx["seed"]
    px, py = ctx["px"], ctx["py"]
    block = _h(px // 2 + gx // 8, py // 2 + gy // 8, s)
    base = mix(VOID[1], VOID[4], 0.1 + 0.45 * block + 0.3 * noise(gx, gy, s + 1, 2.5))
    f = _h(gx, gy, s + 4)
    if f > 0.94:
        base = VOID[5]
    elif f < 0.08:
        base = VOID[0]
    if ctx["face"] in ("top", "bottom"):
        base = shade(base, 0.8)
    if _edge(ctx) == 0:
        base = mix(base, (78, 52, 112), 0.25)
    glow = None
    if veins and abs(noise(gx * 0.8, gy * 0.3, s + 29, 4.0) - 0.5) < 0.02 and noise(gx, gy, s + 23, 7.0) > 1 - veins:
        base, glow = (96, 40, 140), CRACK + (160,)
    return base + (255,), glow


def m_face(ctx):
    """The face in the hood: nothing but shadow (its eyes are parts of their own)."""
    if ctx["face"] != "north":
        return m_void(ctx)
    return mix((5, 2, 9), (12, 6, 18), 0.5 * noise(ctx["gx"], ctx["gy"], ctx["seed"], 2.0)) + (255,), None


def m_eye(ctx):
    """An eye: a bar of violet light, white-hot along its middle."""
    if ctx["face"] not in ("north", "south"):
        return EYE + (255,), EYE + (200,)
    if _edge(ctx) == 0:
        return EYE + (255,), EYE + (240,)
    return EYE_HOT + (255,), EYE_HOT + (255,)


def m_claw(ctx):
    """A claw: dark, a little lighter along its edges and towards its point."""
    k = ctx["py"] / max(1, ctx["fh"] - 1) if ctx["face"] in SIDES else 0.5
    base = mix((18, 10, 28), (66, 44, 94), 0.3 * k + 0.45 * noise(ctx["gx"], ctx["gy"], ctx["seed"], 1.5))
    if _edge(ctx) == 0:
        base = mix(base, (104, 74, 144), 0.3)
    return base + (255,), None


elderlib.MATS.update({
    "ph_cloth": lambda c: m_cloth(c),
    "ph_hood": lambda c: m_cloth(c, ragged=1),
    "ph_hood_in_e": lambda c: m_cloth(c, ragged=1, inside=("east",)),
    "ph_hood_in_w": lambda c: m_cloth(c, ragged=1, inside=("west",)),
    "ph_hood_in_b": lambda c: m_cloth(c, inside=("bottom",)),
    "ph_brim": lambda c: m_cloth(c, inside=("south", "bottom")),
    "ph_strip": lambda c: m_cloth(c, ragged=3),
    "ph_strip_lit": lambda c: m_cloth(c, ragged=3, lit=True),
    "ph_void": lambda c: m_void(c),
    "ph_void_veins": lambda c: m_void(c, veins=0.55),
    "ph_face": m_face,
    "ph_eye": m_eye,
    "ph_claw": m_claw,
})


# ----------------------------------------------------------------------------- helpers


def hang(m, parent, flare_x=0.0, flare_z=0.0):
    """A rotation that hangs a part from `parent` plumb, swung out a little by (flare_x, flare_z) in the model."""
    d = [flare_x, 1.0, flare_z]
    n = math.sqrt(sum(c * c for c in d))
    return align_y(m.xf(parent).inv_dir([c / n for c in d]))


def strip(m, name, parent, at, width, length, faces="z", lit=False, flare=(0.0, 0.0), turn=0.0, depth=1.0, group="strip"):
    """A ragged strip of the mantle hanging from `at` (in the parent's space) along its own +Y, its broad side out to
    the front or back ("z") or to the side ("x"), turned a little (`turn`) so they do not lie in rows; each a part of its
    own, so that each sways on its own."""
    hx, _, hz = hang(m, parent, *flare)
    p = m.part(name, parent, at, (hx, turn, hz), group=group)
    mat = "ph_strip_lit" if lit else "ph_strip"
    if faces == "z":
        m.box(p, -width / 2, 0, -depth / 2, width, length, depth, mat)
    else:
        m.box(p, -depth / 2, 0, -width / 2, depth, length, width, mat)
    return p


# ----------------------------------------------------------------------------- the body


def build():
    m = Model("PaleHauler", 256, 128, seed=339)
    R = m.part("root", None, (0, 24, 0), anim=True)
    for side, sx in (("l", 1), ("r", -1)):
        _leg(m, R, side, sx)
    body = m.part("body", R, (0, -HIP, HIP_Z), anim=True)
    m.box(body, -3.5, -4, -2, 7, 5, 4, "ph_void")
    belly = m.part("belly", body, (0, -4, 0), (BELLY, 0, 0), anim=True)
    m.box(belly, -2.5, -4.25, -1.5, 5, 4.75, 3, "ph_void_veins")
    chest = m.part("chest", belly, (0, -4.0, 0), (CHEST, 0, 0), anim=True)
    m.box(chest, -4, -7, -2, 8, 7.5, 4, "ph_void_veins")
    neck = m.part("neck", chest, (0, -6.8, -0.6), (NECK, 0, 0), anim=True)
    m.box(neck, -1.25, -3.6, -1.25, 2.5, 4.1, 2.5, "ph_void")
    for side, sx in (("l", 1), ("r", -1)):
        _arm(m, chest, side, sx)
    # The mantle sits on the shoulders, level at rest, so its strips are laid out round it and hang from it; the head
    # turns on it too, so it turns about the upright however far the neck reaches forward
    mantle = m.part("mantle", chest, (0, -7, 0.2), m.world_rot(chest), anim=True)
    _mantle(m, mantle)
    top = m.to_local(mantle, m.xf(neck).apply((0, -2.1, 0)))
    head = m.part("head", mantle, top, (0, 0, 0), anim=True)
    _head(m, head)
    return m


def _leg(m, root, side, sx):
    """A long thin leg, the knee a little bent, a long foot."""
    th = m.part(f"thigh_{side}", root, (2.3 * sx, -HIP, HIP_Z), (-KNEE, 0, -0.03 * sx), anim=True)
    m.box(th, -1.5, -0.5, -1.5, 3, LEG + 0.5, 3, "ph_void")
    sh = m.part(f"shin_{side}", th, (0, LEG, 0), (2 * KNEE, 0, 0.03 * sx), anim=True)
    m.box(sh, -1.25, -0.5, -1.25, 2.5, LEG + 0.5, 2.5, "ph_void_veins")
    ft = m.part(f"foot_{side}", sh, (0, LEG, 0), (-KNEE, 0, 0), anim=True)
    m.box(ft, -1.5, -0.25, -3.75, 3, FOOT + 0.25, 5, "ph_void")


def _arm(m, chest, side, sx):
    """A long thin arm hanging to the knee, the elbow a little bent, a big hand of four long fingers in two joints each,
    curled, the palm turned in and forward; three strips of the mantle hang over the shoulder."""
    arm = m.part(f"arm_{side}", chest, (5.6 * sx, -6.0, 0.2), (-(BELLY + CHEST) - 0.08, 0, -0.14 * sx), anim=True)
    m.box(arm, -1.5, -1.5, -1.5, 3, 13.5, 3, "ph_void_veins")
    fore = m.part(f"fore_{side}", arm, (0, 12, 0), (-0.22, 0, 0.06 * sx), anim=True)
    m.box(fore, -1.25, -0.5, -1.25, 2.5, 11, 2.5, "ph_void")
    hand = m.part(f"hand_{side}", fore, (0, 10.5, 0), (0.1, -0.45 * sx, 0), anim=True)
    m.box(hand, -0.75, -0.5, -2.0, 1.5, 4.5, 4, "ph_void")
    m.box(hand, -1.1, -0.9, -1.4, 2.2, 1.6, 2.8, "ph_void")
    for k, (z, spread, length) in enumerate(((-1.55, -0.24, 3.0), (-0.52, -0.08, 3.5), (0.52, 0.08, 3.3), (1.55, 0.24, 2.7))):
        f = m.part(f"finger_{side}{k}", hand, (-0.15 * sx, 3.8, z), (spread, 0, 0.32 * sx), anim=True)
        m.box(f, -0.5, -0.3, -0.5, 1, length + 0.3, 1, "ph_void")
        c = m.part(f"claw_{side}{k}", f, (0, length, 0), (0, 0, 0.55 * sx), anim=True)
        m.box(c, -0.45, -0.2, -0.45, 0.9, 3.0, 0.9, "ph_claw")
    for j, (at, faces, length, width, lit, turn) in enumerate((((1.9 * sx, 0.2, -0.6), "x", 10, 3.0, True, 0.15 * sx),
                                                                ((1.8 * sx, 1.2, 1.1), "x", 13, 2.6, False, -0.15 * sx),
                                                                ((0.9 * sx, -0.2, -1.9), "z", 9, 2.8, True, 0.3 * sx))):
        strip(m, f"sleeve_{side}{j}", arm, at, width, length, faces, lit, flare=(0.2 * sx, -0.05 if faces == "z" else 0.04), turn=turn,
              depth=1.25, group="sleeve")


# The strips of the mantle, in its own level frame: where each hangs from (x, z), which way its broad side faces, its
# width and length, whether its end burns, how it flares out (x, z) and how far it is turned. Short over the shoulders
# all round and a pair at the collar, to the hips in front (open down the breast, where the body shows), and behind in
# two layers, the under one to the knees.
SHORT = [
    (-6.1, -4.5, "z", 3.0, 7, True, (-0.22, -0.22), 0.15), (-3.8, -4.6, "z", 2.6, 9, False, (-0.12, -0.2), -0.15),
    (-1.4, -4.0, "z", 1.8, 5, True, (-0.03, -0.12), 0.2), (1.5, -4.0, "z", 1.8, 6, False, (0.03, -0.12), -0.2),
    (3.7, -4.6, "z", 2.6, 8, True, (0.12, -0.2), 0.12), (6.1, -4.5, "z", 3.2, 7, False, (0.22, -0.22), -0.12),
    (7.6, -1.6, "x", 3.0, 10, True, (0.24, -0.05), 0.15), (7.6, 1.9, "x", 3.2, 9, False, (0.22, 0.08), -0.2),
    (-7.6, -1.4, "x", 3.2, 9, True, (-0.24, -0.05), -0.12), (-7.6, 2.0, "x", 3.0, 11, True, (-0.22, 0.08), 0.18),
    (-5.6, 4.8, "z", 3.0, 10, False, (-0.2, 0.24), -0.2), (-2.0, 4.9, "z", 3.2, 12, True, (-0.06, 0.24), 0.1),
    (2.0, 4.9, "z", 3.0, 10, True, (0.06, 0.24), -0.1), (5.6, 4.8, "z", 3.2, 12, True, (0.2, 0.24), 0.2),
]
FRONT = [
    (-6.3, -3.8, "z", 2.4, 12, True, (-0.18, -0.12), 0.25), (-4.4, -3.9, "z", 2.8, 16, True, (-0.1, -0.1), -0.1),
    (-2.5, -3.9, "z", 2.0, 13, False, (-0.04, -0.1), 0.15), (2.5, -3.9, "z", 2.2, 14, True, (0.04, -0.1), -0.15),
    (4.4, -3.9, "z", 2.8, 17, False, (0.1, -0.1), 0.1), (6.3, -3.8, "z", 2.4, 11, True, (0.18, -0.12), -0.25),
]
BACK = [
    (-6.0, 4.0, "z", 2.6, 19, True, (-0.2, 0.2), 0.15), (-4.0, 4.1, "z", 2.4, 23, False, (-0.12, 0.2), -0.1),
    (-2.0, 4.2, "z", 2.2, 25, True, (-0.05, 0.2), 0.1), (0.0, 4.2, "z", 2.8, 22, True, (0.0, 0.2), -0.12),
    (2.0, 4.2, "z", 2.4, 24, False, (0.05, 0.2), 0.08), (4.0, 4.1, "z", 2.2, 21, True, (0.12, 0.2), -0.15),
    (6.0, 4.0, "z", 2.6, 20, True, (0.2, 0.2), 0.12),
]
MIDDLE = [
    (-4.8, 4.5, "z", 2.8, 15, False, (-0.14, 0.22), -0.1), (0.4, 4.6, "z", 3.0, 17, True, (0.0, 0.22), 0.12),
    (4.9, 4.5, "z", 2.6, 14, True, (0.14, 0.22), -0.15),
]


def _mantle(m, mantle):
    """The mantle: a yoke of cloth over the shoulders, bunched up on them, a collar round the neck, and its strips (the
    long ones behind thicker, so the cape is heavy from the side as well)."""
    m.box(mantle, -7.25, -0.75, -4.25, 14.5, 2.75, 8.75, "ph_cloth")
    m.box(mantle, -4.5, -2.25, -3.5, 9, 1.75, 7.5, "ph_cloth")
    for sx in (-1, 1):
        m.box(mantle, 4.25 if sx > 0 else -7.75, -1.75, -3.25, 3.5, 1.25, 6.75, "ph_cloth")
    n = 0
    for row, y, depth in ((BACK, 1.0, 1.5), (MIDDLE, 1.2, 1.5), (FRONT, 1.2, 1.0), (SHORT, 1.5, 1.25)):
        for x, z, faces, width, length, lit, flare, turn in row:
            strip(m, f"strip{n}", mantle, (x, y, z), width, length, faces, lit, flare, turn, depth)
            n += 1


def _head(m, head):
    """The head in its deep hood: a face of shadow with two bars of light for eyes. The hood rises to a peak at the
    back, whose tail falls back; its ragged sides and back hang down to the shoulders."""
    m.box(head, -3, -3.5, -3.5, 6, 6, 6, "ph_face")
    for side, sx in (("l", 1), ("r", -1)):
        eye = m.part(f"eye_{side}", head, (1.55 * sx, -1.0, -3.55), scale=0.25)
        m.box(eye, -1.2, -0.4, -0.25, 2.4, 0.8, 0.3, "ph_eye")
    hood = m.part("hood", head, (0, 0, 0), anim=True)
    m.box(hood, -4.25, -4.75, -5.5, 8.5, 1.25, 9.5, "ph_hood_in_b")
    m.box(hood, 3, -3.5, -5.5, 1.25, 8, 9.5, "ph_hood_in_e")
    m.box(hood, -4.25, -3.5, -5.5, 1.25, 8, 9.5, "ph_hood_in_w")
    m.box(hood, -3, -3.5, 2.5, 6, 9, 1.5, "ph_hood")
    # the brim hangs a little lower over the face, a few threads hanging from it
    m.box(hood, -3, -3.6, -5.75, 6, 1.1, 1.5, "ph_brim")
    for x, h in ((-2.95, 1.0), (-0.4, 1.4), (2.3, 0.7)):
        m.box(hood, x, -2.5, -5.6, 0.8, h, 0.8, "ph_cloth")
    # its crown rises in layers, each narrower and further back, to a peak at the back, whose tail falls back
    for x, y0, y1, z0, z1 in ((3.25, -6.0, -4.75, -4.0, 3.5), (2.25, -7.25, -6.0, -2.25, 3.0), (1.25, -8.25, -7.25, -0.5, 2.75)):
        m.box(hood, -x, y0, z0, 2 * x, y1 - y0, z1 - z0, "ph_hood")
    p = hood
    for i, (at, rot, w, h) in enumerate((((0, -7.75, 1.9), -1.45, 2.2, 3.0), ((0, -2.75, 0), -0.75, 1.4, 3.0))):
        p = m.part(f"peak{i}", p, at, (rot, 0, 0), anim=True)
        m.box(p, -w / 2, -h, -w / 2, w, h + 0.25, w, "ph_hood")
    for k, (at, length, width) in enumerate((((3.75, 4.2, -4.6), 5, 1.6), ((3.8, 4.3, -2.0), 7, 2.2),
                                             ((-3.75, 4.2, -4.6), 6, 1.6), ((-3.8, 4.3, -1.8), 5, 2.2))):
        strip(m, f"fringe{k}", head, at, width, length, faces="x", lit=k % 2 == 1, flare=(0.1 if at[0] > 0 else -0.1, 0.0),
              group="fringe")
