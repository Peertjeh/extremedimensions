"""The Last Weaver: a towering, crowned figure in layered robes, arms spread wide. A black under-robe rots into tatters
beneath a violet over-robe hemmed in gilt and open at the front on an embroidered tabard; a cape falls from its
shoulders under a mantle and lacquered pauldrons, and a high pointed collar frames its head: a mask of dark carved stone
coming to a point at the chin, a tall diamond of an eye cut like a gem and set in gilt, brows swept up from it and
plates over the cheeks (at Minecraft's own size, painted finer and brought down), under a gothic mitre and a fan of spires and needles,
veiled. A spindle burns at its breast where the mantle is clasped. Glowing threads run from its long, ringed fingers,
sagging into loops, back to its heart and down to hanging lanterns; spools turn at its hips, a ring of great needles
circles it, and a halo of runes and needles turns behind its head."""
import math

from elderlib import Model, catenary, rot_zyx, transpose

TAU = 2 * math.pi


def _mul(a, b):
    return [[sum(a[i][k] * b[k][j] for k in range(3)) for j in range(3)] for i in range(3)]


def orient(m, parent, rot=(0.0, 0.0, 0.0)):
    """The local rotation under `parent` that gives the model-space rotation `rot` (xRot, yRot, zRot): pieces built
    with it keep a fixed bearing in the model however their parent is turned (a drape hanging plumb from an arm)."""
    r = _mul(transpose(m.xf(parent).r), rot_zyx(rot[2], rot[1], rot[0]))
    y = math.asin(max(-1.0, min(1.0, -r[2][0])))
    return (math.atan2(r[2][1], r[2][2]), y, math.atan2(r[1][0], r[0][0]))


def build():
    m = Model("Weaver", 1024, 1024, seed=97)
    R = m.part("root", None, (0, 24, 0), anim=True)
    body = m.part("body", R, (0, 0, 0), anim=True)
    # Each of body, torso and head keeps its own boxes in a "core" child, so the halo can show before the rest as it
    # is woven (see WeaverModel)
    core = m.part("bodycore", body, (0, 0, 0), anim=True)
    _robe(m, body, core)
    _sash(m, body, core)
    torso = m.part("torso", body, (0, -120, 0), anim=True)
    _torso(m, torso, m.part("torsocore", torso, (0, 0, 0), anim=True))
    head = m.part("head", torso, (0, -28, 0), anim=True)
    _head(m, m.part("headcore", head, (0, 0, 0), anim=True))
    _halo(m, head)
    for side, sx in (("l", 1), ("r", -1)):
        _arm(m, torso, side, sx)
    _needles(m, body)
    return m


# ----------------------------------------------------------------------------- the robes


def _robe(m, body, core):
    """An under-robe of black tatters, a violet over-robe open at the front on an embroidered tabard, and a cape from
    the shoulders: long strips that sway (group robe)."""
    m.box(core, -9, -120, -7, 18, 60, 14, "weave_under")
    m.box(core, -11, -60, -9, 22, 58, 18, "weave_under")
    for i in range(28):
        a = TAU * i / 28 + 0.05 * (i % 3)
        length = 118 + (i * 11) % 14
        front = abs(math.cos(a)) > 0.8 and math.cos(a) < 0
        tilt = 0.09 + 0.01 * (i % 3) if front else 0.11 + 0.015 * (i % 4)
        s = m.part(f"skirt_{i}", body, (10.5 * math.sin(a), -119, 7.5 * math.cos(a)), (tilt, a, 0), group="robe")
        m.box(s, -3, 0, -1, 6 + i % 2, length, 2, "weave_under_ragged")
    # the over-robe: a gilt hem at one height all round, ragged below it, embroidered bands down the opening
    n, hem = 20, 76
    for k in range(n):
        a = math.pi + 0.75 + (TAU - 1.5) * k / (n - 1)
        length = hem + 15 + (k * 7) % 12
        s = m.part(f"orobe_{k}", body, (11.6 * math.sin(a), -121, 8.2 * math.cos(a)), (0.17, a, 0), group="robe")
        m.box(s, -3.5, 0, -0.8, 7, length, 1.6, "weave_ragged")
        m.box(s, -3.8, hem, -1.1, 7.6, 5, 2.2, "weave_trim")
        if k in (0, n - 1):
            m.box(s, -3.8 if k == 0 else -0.2, 0, -1.0, 4, hem, 2.0, "weave_runes")
    tab = m.part("tabard", body, (0, -121, -8.8), (-0.15, 0, 0), group="robe")
    m.box(tab, -5, 0, -0.6, 10, 104, 1.2, "weave_runes_ragged")
    m.box(tab, -5.3, hem, -0.9, 10.6, 5, 1.8, "weave_trim")
    # the cape: falls plumb past the halo, then flares over the over-robe
    for k in range(12):
        a = -0.9 + 1.8 * k / 11
        top = m.part(f"cape_{k}", body, (14.5 * math.sin(a), -146, 10.5 * math.cos(a)), (0.03, a, 0), group="robe")
        m.box(top, -4, 0, -1, 8, 33, 2, "weave")
        lo = m.part(f"capelo_{k}", top, (0, 32, 0), (0.2 + 0.02 * (k % 3), 0, 0))
        length = hem + 25 + (k * 5) % 12
        m.box(lo, -4, 0, -1, 8, length, 2, "weave_ragged")
        m.box(lo, -4.3, hem, -1.3, 8.6, 5, 2.6, "weave_trim")


def _sash(m, body, core):
    """A sash with a glowing cord, knotted at the front with embroidered tails, charms on short threads, and a spool
    turning on a bracket at each hip."""
    m.box(core, -13, -125, -9.5, 26, 6, 19, "weave_trim")
    m.box(core, -13.4, -119.5, -9.9, 26.8, 1.0, 19.8, "thread")
    m.box(core, -7.4, -126.2, -11.6, 4.8, 5.8, 2.4, "weave_lacquer")
    m.box(core, -8.6, -125.0, -11.0, 7.2, 3.4, 1.6, "weave_lacquer")
    for j, (x, length, zr) in enumerate([(-6.6, 58, 0.06), (-3.6, 47, -0.05)]):
        t = m.part(f"sash_{j}", body, (x, -122, -10.4), (-0.16, 0, zr), group="robe")
        m.box(t, -1.7, 0, -0.5, 3.4, length, 1.0, "weave_lacquer")
        m.box(t, -2.0, length - 5, -0.8, 4.0, 5, 1.6, "weave_trim")
        m.strand(f"tassel_{j}", t, (0, length, 0), 5 + 2 * j, 0.6, "thread")
    for j, (x, length, charm) in enumerate([(6.6, 4.5, "glass"), (9.2, 3.0, "bone"), (11.6, 4.0, "glass")]):
        c = m.strand(f"charm_{j}", core, (x, -119.2, -10.6), length, 0.5, "thread")
        m.box(c, -0.6, length - 0.3, -0.6, 1.2, 0.6, 1.2, "gild")
        m.box(c, -0.9, length + 0.3, -0.9, 1.8, 2.2, 1.8, charm)
    for side, sx in (("l", 1), ("r", -1)):
        m.box(core, 12.6 if sx > 0 else -22.6, -122.2, -4.6, 10, 1.2, 1.2, "iron")
        m.box(core, sx * 22 - 0.3, -122.2, -4.3, 0.6, 3.6, 0.6, "iron")
        sp = m.part(f"spool_{side}", body, (sx * 22, -110, -4), anim=True)
        # a great needle threaded through the spool: its eye hangs on the hook, its point juts out below
        m.box(sp, -0.85, -10.2, -0.6, 0.5, 3.4, 1.2, "silver")
        m.box(sp, 0.35, -10.2, -0.6, 0.5, 3.4, 1.2, "silver")
        m.box(sp, -0.85, -10.7, -0.6, 1.7, 0.5, 1.2, "silver")
        _needle(m, sp, -6.8, 24.5, 1.5, point_up=False)
        m.box(sp, -2.8, -5.8, -2.8, 5.6, 11.6, 5.6, "wound_thread")
        for y0 in (-7.2, 5.6):
            m.box(sp, -3.8, y0, -3.8, 7.6, 1.6, 7.6, "boatwood")
        o = m.part(f"spoolo_{side}", sp, (0, 0, 0), (0, math.pi / 4, 0))
        m.box(o, -2.8, -5.8, -2.8, 5.6, 11.6, 5.6, "wound_thread")
        for y0 in (-6.9, 5.9):
            m.box(o, -3.8, y0, -3.8, 7.6, 1.0, 7.6, "boatwood")


# ----------------------------------------------------------------------------- the torso


def _torso(m, torso, t):
    """A laced bodice between the bands of a stole, a spindle brooch clasping the mantle, swags of beads, a high
    pointed collar (all in the torso's core but the mantle, which ripples on its own)."""
    m.box(t, -11, -28, -6.5, 22, 28, 13, "weave")
    for sx in (1, -1):
        m.box(t, 4.75 if sx > 0 else -9.75, -28.4, -7.5, 5, 27, 1.1, "weave_runes")
    m.box(t, -4.8, -27, -6.9, 9.6, 26, 0.5, "weave_lacquer")
    for y in (-25.6, -23.8):
        m.box(t, -4.9, y, -7.25, 9.8, 0.6, 0.45, "thread")
    for k in range(3):
        for j, sgn in enumerate((1, -1)):
            lace = m.part(f"lace_{k}{j}", t, (0, -9.6 + 3.2 * k, -7.15 - 0.15 * j), (0, 0, 0.36 * sgn))
            m.box(lace, -5.0, -0.3, -0.22, 10, 0.6, 0.44, "thread")
    # the spindle brooch: a gilt lozenge, a spindle wound with light through it
    br = m.part("brooch", t, (0, -18, -6.9), (0, 0, math.pi / 4))
    m.box(br, -3.5, -3.5, -1.2, 7, 7, 1.2, "gild")
    m.box(br, -2.5, -2.5, -1.7, 5, 5, 0.6, "socket")
    m.box(t, -0.45, -25.5, -9.45, 0.9, 15.5, 0.9, "silver")
    for w, y0, h in ((1.6, -21.6, 1.0), (2.8, -20.6, 1.2), (3.6, -19.4, 2.8), (2.8, -16.6, 1.2), (1.6, -15.4, 1.0)):
        m.box(t, -w / 2, y0, -9 - w / 2, w, h, w, "glowcore" if w > 3 else "wound_thread")
    m.box(t, -1.4, -12.2, -10.4, 2.8, 1.0, 2.8, "gild")
    # swags of beads, bone and gilt and glass
    for x0, y0, sag, count, z in ((10.0, -27.2, 4.5, 14, -8.2), (10.4, -25.0, 15.5, 26, -8.3)):
        for i, p in enumerate(catenary([-x0, y0, z], [x0, y0, z], sag, count)):
            mat = ("bone", "glass", "gild")[i % 3]
            m.box(t, p[0] - 0.65, p[1] - 0.65, p[2] - 0.65, 1.3, 1.3, 1.3, mat)
    m.box(t, -0.6, -8.9, -8.9, 1.2, 0.6, 1.2, "gild")
    m.box(t, -0.8, -8.3, -9.1, 1.6, 2.4, 1.6, "glowcore")
    # the mantle, open at the front where the brooch clasps it (group mantle)
    n = 18
    for i in range(n):
        a = math.pi + 0.95 + (TAU - 1.9) * i / (n - 1)
        if abs(math.sin(a)) > 0.88:
            continue  # the pauldrons cover the shoulders
        length = 23 + (i * 5) % 8 if math.cos(a) < 0.3 else 15 + (i * 3) % 4
        sm = m.part(f"wmantle_{i}", torso, (13 * math.sin(a), -27.5, 8 * math.cos(a)), (0.8, a, 0), group="mantle")
        m.box(sm, -4, 0, -0.8, 8, length, 1.6, "weave_ragged")
        m.box(sm, -4.3, length - 11, -1.1, 8.6, 5, 2.2, "weave_trim")
    # a high collar of lacquered points round the back of the head
    for i in range(9):
        a = -1.55 + 3.1 * i / 8
        h = 15 + 7 * min(1.0, abs(a) / 1.2)
        c = m.part(f"collar_{i}", t, (10 * math.sin(a), -27.8, 1.5 + 8 * math.cos(a)), (-0.32, a, 0))
        m.box(c, -2.6, -h, -0.6, 5.2, h, 1.2, "weave_runes")
        tip = m.part(f"collartip_{i}", c, (0, -h, 0), (0, 0, math.pi / 4))
        m.box(tip, -1.8, -1.8, -0.45, 3.6, 3.6, 0.9, "gild")


# ----------------------------------------------------------------------------- the head


def _needle(m, part, y0, length, thick, point_up=True):
    """A sewing needle along the part's Y: a slotted eye at one end, a taper to the point at the other."""
    t = thick
    if point_up:
        # eye at y0 (the bottom), point at y0 - length
        m.box(part, -t / 2, y0 - length * 0.72, -t / 2, t, length * 0.72, t, "silver")
        m.box(part, -t * 0.32, y0 - length * 0.9, -t * 0.32, t * 0.64, length * 0.18, t * 0.64, "silver")
        m.box(part, -t * 0.16, y0 - length, -t * 0.16, t * 0.32, length * 0.1, t * 0.32, "silver")
    else:
        m.box(part, -t / 2, y0, -t / 2, t, length * 0.72, t, "silver")
        m.box(part, -t * 0.32, y0 + length * 0.72, -t * 0.32, t * 0.64, length * 0.18, t * 0.64, "silver")
        m.box(part, -t * 0.16, y0 + length * 0.9, -t * 0.16, t * 0.32, length * 0.1, t * 0.32, "silver")


def _head(m, head):
    """A hood with a lacquered rim round a mask of dark carved stone, shaped like a shield coming to a point at the
    chin. On it a tall diamond of an eye cut like a gem and set in gilt, brows swept up from it, plates laid over the
    cheeks, a ridge lit by a thread of light; a slit of light runs up from the eye to the crown. Over the gilt band of
    the crown a fan of gothic spires and needles, the middle one tallest; a veil of gauze and thread falls from it.
    The mask, the eye and the spires are painted four times finer and brought down to Minecraft's own size.
    Everything here is in the head's core."""
    F = 4  # the face is designed this many times finer than it is worn
    m.box(head, -8.5, -22, -5, 17, 22, 13, "weave")
    for sx in (1, -1):
        m.box(head, 7.0 if sx > 0 else -9.4, -21.6, -6.8, 2.4, 21.6, 2.4, "weave_lacquer")
    m.box(head, -9.4, -21.6, -6.8, 18.8, 2.2, 2.4, "weave_lacquer")
    # the dark inside of the hood round the mask
    m.box(head, -7.0, -19.6, -5.7, 14, 19.4, 0.7, "socket")
    # the mask
    mask = m.part("mask", head, (0, 0, 0), detail=1)
    m.box(mask, -6.6, -19.4, -6.6, 13.2, 19.2, 0.75, "mask_carved_px")
    # brows swept up and out from the eye
    for side, sx in (("l", 1), ("r", -1)):
        br = m.part(f"brow_{side}", head, (sx * 4.1, -16.0, -6.95), (0, 0.18 * sx, -0.42 * sx), detail=F)
        m.box(br, -3.3, -0.75, -0.45, 6.6, 1.5, 0.9, "mask_ridge")
        br2 = m.part(f"browh_{side}", head, (sx * 5.6, -17.6, -6.85), (0, 0.24 * sx, -0.62 * sx), detail=F)
        m.box(br2, -1.9, -0.5, -0.35, 3.8, 1.0, 0.7, "mask_ridge")
        # plates over the cheeks, one under another, turned back along the face
        for j in range(3):
            cl = m.part(f"cheekl_{side}{j}", head, (sx * (4.4 - 0.35 * j), -8.4 + 2.4 * j, -6.75 + 0.12 * j),
                        (0, -0.38 * sx, 0.2 * sx), detail=F)
            m.box(cl, -2.2 + 0.2 * j, -0.85, -0.4, 4.4 - 0.4 * j, 1.7, 0.8, "mask_ridge")
        # a fang of carved stone at each side of the chin
        fg = m.part(f"fang_{side}", head, (sx * 2.2, -3.2, -6.6), (0, -0.2 * sx, 0.3 * sx), detail=F)
        m.box(fg, -0.45, -1.6, -0.4, 0.9, 3.6, 0.8, "mask_ridge")
    # a ridge lit by a thread of light, from under the eye to the chin
    rd = m.part("ridge", head, (0, -2.9, -6.85), detail=F)
    m.box(rd, -0.5, -2.6, -0.4, 1.0, 5.0, 0.8, "mask_ridge_lit")
    # the eye, in a gilt frame along the diamond's edges
    ey = m.part("eye", head, (0, -10.4, -6.95), detail=1)
    m.box(ey, -3.0, -4.8, -0.35, 6.0, 9.6, 0.4, "eye_lozenge_px")
    a, b = 3.25, 5.2
    side_len = math.hypot(a, b)
    tilt = math.atan2(a, b)
    for k, (sx, sy) in enumerate(((1, 1), (1, -1), (-1, 1), (-1, -1))):
        fr = m.part(f"eyeframe_{k}", head, (sx * a / 2, -10.4 + sy * b / 2, -7.0), (0, 0, sx * sy * tilt), detail=F)
        m.box(fr, -0.3, -side_len / 2 - 0.2, -0.35, 0.6, side_len + 0.4, 0.7, "gild")
    # a slit of light from the eye up to the crown
    ray = m.part("eyeray", head, (0, 0, 0), detail=F)
    m.box(ray, -0.25, -19.4, -6.95, 0.5, 3.6, 0.35, "eye")
    m.box(ray, -0.6, -19.8, -7.05, 1.2, 0.6, 0.5, "gild")
    # the crown: a gilt band with gems, then a fan of gothic spires with needles between them
    m.box(head, -9.3, -24.6, -5.9, 18.6, 3.8, 14.8, "gild")
    gem = m.part("crowngem", head, (0, -23.2, -5.9), (0, 0, math.pi / 4))
    m.box(gem, -1.2, -1.2, -0.7, 2.4, 2.4, 0.9, "glass")
    for sx in (1, -1):
        m.box(head, sx * 5 - 0.7, -23.9, -6.5, 1.4, 1.4, 0.8, "glass")
    # a tall pointed mitre of carved stone behind the spires, a slit of light up its middle
    mitre = m.part("mitre", head, (0, -24.0, 2.6), (-0.1, 0, 0), detail=F)
    m.box(mitre, -7.5, -24, -0.4, 15, 24, 0.8, "mitre")
    for k in range(9):
        a = -1.2 + 2.4 * k / 8
        h = 30 - abs(k - 4) * 4.6
        sp = m.part(f"spire_{k}", head, (8.2 * math.sin(a), -24.2, 0.5 + 5.4 * (1 - math.cos(a))), (-0.14, 0, a * 0.42), detail=F)
        w = 3.5 - abs(k - 4) * 0.24
        y = 0.0
        for frac, ww in ((0.16, 1.0), (0.42, 0.72), (0.26, 0.48), (0.16, 0.22)):
            seg = h * frac
            m.box(sp, -w * ww / 2, y - seg, -w * ww / 2, w * ww, seg, w * ww, "spire")
            y -= seg
            if ww > 0.3:
                m.box(sp, -w * ww * 0.62, y - 0.35, -w * ww * 0.62, w * ww * 1.24, 0.7, w * ww * 1.24, "gild")
        # buttresses at its foot
        m.box(sp, -w * 0.75, -h * 0.1, -w * 0.3, w * 1.5, h * 0.1, w * 0.6, "spire")
        if k % 2 == 1:
            nd = m.part(f"crownneedle_{k}", head, (8.6 * math.sin(a + 0.15), -23.6, 1.5 + 5.6 * (1 - math.cos(a + 0.15))),
                        (-0.22, 0, (a + 0.15) * 0.55), detail=F)
            _needle(m, nd, 0, h * 0.8, 0.9)
    # the veil: gauze and glowing thread from the band round the sides and back (group veil)
    pts = [(9.0 * sx, -4.2 + 10.2 * j / 7) for sx in (1, -1) for j in range(8)] + [(-7.6 + 15.2 * j / 9, 8.6) for j in range(10)]
    for i, (x, z) in enumerate(pts):
        back = z > 8
        length = (14 + (i * 7) % 7) if back else (13 + (i * 5) % 7)
        glow = i % 4 == 1
        m.strand(f"veil_{i}", head, (x, -20.9, z), length, 0.5 if glow else 1.0, "thread" if glow else "veil", group="veil")
    # in front, the veil parts round the mask and falls to the collar
    for j in range(10):
        sx, r = (1, -1)[j % 2], j // 2
        glow = r == 3
        m.strand(f"veilf_{j}", head, (sx * (8.0 + 0.55 * r), -20.6, -7.2 + 0.25 * r), 15 + 1.0 * r, 0.5 if glow else 0.9,
                 "thread" if glow else "veil", group="veil")
    for j, sx in enumerate((1, -1)):
        c = m.strand(f"crowncharm_{j}", head, (9.9 * sx, -21.4, -5.4), 6, 0.5, "thread")
        m.box(c, -0.8, 6, -0.8, 1.6, 2.2, 1.6, "glass")


def _halo(m, head):
    """The halo: four rings, spokes, needles pointing in to the head, rune plates and crosses of light. One subtree
    under `halo`, which the animation turns and swells."""
    halo = m.part("halo", head, (0, -14, 24), anim=True)
    m.ring("halo_a", halo, (0, 0, 0), 46, 40, 7.6, 3.0, "halo")
    m.ring("halo_b", halo, (0, 0, 0), 38, 36, 6.8, 1.4, "halo")
    m.ring("halo_c", halo, (0, 0, 0), 55, 36, 9.9, 1.5, "halo", phase=0.08)
    m.ring("halo_d", halo, (0, 0, 0), 29, 28, 6.8, 0.9, "halo")
    for k in range(12):
        a = TAU * k / 12
        sp = m.part(f"halospoke_{k}", halo, (42 * math.cos(a), 42 * math.sin(a), 0.5), (0, 0, a + math.pi / 2))
        m.box(sp, -0.7, -5, -0.7, 1.4, 10, 1.4, "halo")
        hn = m.part(f"halo_needle_{k}", halo, (37.4 * math.cos(a), 37.4 * math.sin(a), 0), (0, 0, a + math.pi / 2))
        m.box(hn, -0.75, -0.4, -0.3, 0.5, 2.4, 0.6, "silver")
        m.box(hn, 0.25, -0.4, -0.3, 0.5, 2.4, 0.6, "silver")
        _needle(m, hn, 1.6, 6.6, 0.9, point_up=False)
        ra = TAU * (k + 0.5) / 12
        rp = m.part(f"halo_rune_{k}", halo, (51 * math.cos(ra), 51 * math.sin(ra), 0), (0, 0, ra + math.pi / 2))
        m.box(rp, -0.4, -3.6, -0.4, 0.8, 7.4, 0.8, "halo")
        m.box(rp, -2.0, -3.0, -0.7, 4.0, 6.0, 1.4, "rune_plate")
    for k in range(4):
        a = TAU * k / 4
        cr = m.part(f"halo_cross_{k}", halo, (46 * math.cos(a), 46 * math.sin(a), 0), (0, 0, a + math.pi / 2))
        m.box(cr, -0.9, -4.5, -1.8, 1.8, 9, 3.6, "eye")
        m.box(cr, -3.4, -2.4, -1.8, 6.8, 1.8, 3.6, "eye")


# ----------------------------------------------------------------------------- the arms


def _arm(m, torso, side, sx):
    """A bell sleeve under lacquered pauldrons with drapes hanging plumb, a forearm bound in thread, a ringed hand with
    five long fingers, and threads from them to the lanterns, between the fingers and back to the body."""
    arm = m.part(f"arm_{side}", torso, (12 * sx, -25, 0), (0, 0, -1.72 * sx), anim=True)
    m.box(arm, -4.5, -1, -4.5, 9, 36, 9, "weave")
    m.box(arm, -5.5, 16, -5.5, 11, 13, 11, "weave")
    m.box(arm, -5.8, 15.6, -5.8, 11.6, 2.4, 11.6, "weave_trim")
    m.box(arm, -6.6, 26, -6.6, 13.2, 8, 13.2, "weave")
    m.box(arm, -7.1, 32, -7.1, 14.2, 5, 14.2, "weave_trim")

    def up(u0, u1):
        # an interval of height above the arm (arm-local +x for the left arm, -x for the right) as (x0, width)
        return (min(sx * u0, sx * u1), abs(u1 - u0))

    # pauldrons: four lacquered lames, each tucked under the one before, and bone spikes
    for k in range(4):
        y0, top, zz = 0.5 + 4.4 * k, 7.6 - 0.8 * k, 8.6 - 0.7 * k
        x0, w = up(top - 2.4, top)
        m.box(arm, x0, y0, -zz, w, 7, 2 * zz, "weave_scale")
        x1, w1 = up(-1.5 - 0.5 * k, top - 2.4)
        m.box(arm, x1, y0, zz - 1.6, w1, 7, 1.6, "weave_scale")
        m.box(arm, x1, y0, -zz, w1, 7, 1.6, "weave_scale")
        # a gilt lip along the lame's outer edge
        x2, w2 = up(-1.7 - 0.5 * k, top + 0.2)
        m.box(arm, x2, y0 + 6.0, -zz - 0.2, w2, 1.2, 2 * zz + 0.4, "gild")
    for k in range(3):
        y0, zz = 0.5 + 4.4 * k, 8.6 - 0.7 * k
        c = m.strand(f"ptassel_{side}{k}", arm, (sx * (-1.2 - 0.5 * k), y0 + 3.5, -zz + 0.8), 5 + 2 * k, 0.5, "thread")
        m.box(c, -0.8, 5 + 2 * k, -0.8, 1.6, 1.6, 1.6, "glass")
    loop = catenary(m.xf(arm).apply([-sx * 4.8, 4, -7.4]), m.xf(arm).apply([-sx * 6.8, 33, -7.4]), 16, 10)
    names = m.rope(f"thu_{side}_", arm, [m.to_local(arm, p) for p in loop], 0.8, "thread")
    m.box(names[5], -0.9, 0, -0.9, 1.8, 1.8, 1.8, "glowcore")
    for j in range(3):
        sp = m.part(f"pspike_{side}{j}", arm, (sx * (7.3 - 0.8 * (j > 0)), 3 + 3.5 * j, -3 + 3 * j),
                    orient(m, arm, (-0.35 + 0.35 * j, 0, 0.45 * sx)))
        m.box(sp, -0.9, -6, -0.9, 1.8, 6, 1.8, "bone")
        m.box(sp, -0.45, -10, -0.45, 0.9, 4, 0.9, "bone_dark")
    # drapes hanging plumb from under the sleeve, longest at the cuff (group robe), and thin tatters (group strand)
    for j, (ya, length) in enumerate([(6, 18), (12, 22), (18, 30), (27, 48), (33, 62)]):
        zo = (-1.0, 0.8, -0.6, 1.0, -0.4)[j]
        dp = m.part(f"drape_{side}{j}", arm, (-sx * 4.4, ya, zo), orient(m, arm, (0, 0.14 * (1 if j % 2 else -1), 0)), group="robe")
        m.box(dp, -3, 0, -0.6, 6, length, 1.2, "weave_ragged")
        if length > 40:
            m.box(dp, -3.3, length - 20, -0.9, 6.6, 5, 1.8, "weave_trim")
    for j in range(6):
        m.strand(f"sleeve_{side}{j}", arm, (-3 + (j % 3) * 3, 6 + j * 5, 3.4 * (1 if j % 2 else -1)), 16 + (j * 7) % 14, 1.6, "weave_under_ragged")

    fo = m.part(f"forearm_{side}", arm, (0, 35, 0), (0, 0, 0.22 * sx), anim=True)
    m.box(fo, -3, 0, -3, 6, 32, 6, "skin")
    for k in range(7):
        sg = 1 if k % 2 else -1
        wr = m.part(f"wrap_{side}{k}", fo, (0, 4 + 3.4 * k, 0), (0.14 * sg, 0, 0.3 * sg))
        m.box(wr, -3.5, -0.45, -3.5, 7, 0.9, 7, "thread")
    m.box(fo, -3.6, 27.4, -3.6, 7.2, 1.5, 7.2, "gild")
    m.box(fo, -3.4, 29.6, -3.4, 6.8, 1.0, 6.8, "gild")

    ha = m.part(f"hand_{side}", fo, (0, 32, 0), (0, 0, 0.25 * sx), anim=True)
    m.box(ha, -4.5, 0, -2, 9, 9, 4, "skin")
    m.box(ha, -0.4, 1.6, -2.4, 0.8, 5.6, 0.5, "thread")
    m.box(ha, -1.9, 3.2, -2.4, 3.8, 0.8, 0.5, "thread")
    tips = []
    for f in range(5):
        spread = (-0.75 + 0.36 * f) * sx
        fr = m.part(f"wf_{side}{f}", ha, (sx * (-3.6 + 1.8 * f), 8.5, 0), (0.12, 0, spread))
        m.box(fr, -0.9, 0, -0.9, 1.8, 11, 1.8, "skin")
        m.box(fr, -1.15, 6.6, -1.15, 2.3, 1.5, 2.3, "gild")
        if f == 2:
            m.box(fr, -0.6, 6.75, -1.6, 1.2, 1.2, 0.6, "glass")
        fm = m.part(f"wfm_{side}{f}", fr, (0, 11, 0), (0.18, 0, 0))
        m.box(fm, -0.75, 0, -0.75, 1.5, 10, 1.5, "skin")
        if f in (1, 3):
            m.box(fm, -0.95, 3.0, -0.95, 1.9, 1.1, 1.9, "gild")
        ft = m.part(f"wft_{side}{f}", fm, (0, 10, 0), (0.22, 0, 0))
        m.box(ft, -0.6, 0, -0.6, 1.2, 8, 1.2, "bone_dark")
        tips.append(m.xf(ft).apply([0, 8, 0]))

    def thread(prefix, pts, bead=False):
        # built in model space, then hung from the hand
        names = m.rope(prefix, ha, [m.to_local(ha, p) for p in pts], 0.9, "thread")
        if bead:
            mid = names[len(names) // 2]
            m.box(mid, -0.9, 0, -0.9, 1.8, 1.8, 1.8, "glowcore")
        return names

    for k, (f, drop, sag, inward, size) in enumerate([(0, 46, 6, 2, 1.7), (2, 74, 12, 14, 1.9), (4, 36, 4, -4, 1.4)]):
        p0 = tips[f]
        p1 = [p0[0] - sx * inward, p0[1] + drop, p0[2] - 4]
        thread(f"th_{side}{k}_", catenary(p0, p1, sag, 10))
        m.lantern(f"wlan_{side}{k}", ha, m.to_local(ha, p1), size=size, links=1, frame="iron", glass="glass")
    # a cat's cradle between the fingers, beaded with light
    for k, (a, b, sag) in enumerate([(1, 3, 16), (0, 2, 8), (2, 4, 9), (0, 4, 26)]):
        thread(f"thl_{side}{k}_", catenary(tips[a], tips[b], sag, 8), bead=True)
    # threads back to the spindle at its heart, and down to wind onto the spool at its hip
    thread(f"thc_{side}_", catenary(tips[1], [sx * 3.2, -114, -8.6], 26, 16))
    thread(f"thd_{side}_", catenary(tips[3], [sx * 22, -87, -7.0], 26, 16))


# ----------------------------------------------------------------------------- the needles


def _needles(m, body):
    """A ring of great needles circling the robe, points first, trailing threads of light. One subtree under
    `needles`, which the animation turns."""
    ring = m.part("needles", body, (0, -92, 0), anim=True)
    for k in range(10):
        phi = TAU * k / 10
        p = (30 * math.sin(phi), 2.0 * (1 if k % 2 else -1), 30 * math.cos(phi))
        nd = m.part(f"needle_{k}", ring, p, (-0.22, phi + math.pi / 2, 0))
        # along local +Z: an eye at the back, the point in front, a thread through the eye
        m.box(nd, -1.0, -0.8, -13.4, 0.6, 1.6, 4.4, "silver")
        m.box(nd, 0.4, -0.8, -13.4, 0.6, 1.6, 4.4, "silver")
        m.box(nd, -1.0, -0.8, -13.9, 2.0, 1.6, 0.5, "silver")
        m.box(nd, -0.8, -0.8, -9.0, 1.6, 1.6, 16, "silver")
        m.box(nd, -0.55, -0.55, 7.0, 1.1, 1.1, 4, "silver")
        m.box(nd, -0.3, -0.3, 11.0, 0.6, 0.6, 3, "silver")
        m.box(nd, -0.3, -0.3, -19.0, 0.6, 0.6, 8.0, "thread")
