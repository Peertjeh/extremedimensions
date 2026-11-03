"""The Knot Mother: what is left of a Weaver tied into the First Knot. She has no gown and no feet: below the waist she
is one great knot of thread, loops of cord wound every way round a dark tangle, bulging with tight knots, two great
spools half sunk in its flanks, and from its underside cords run taut down to the floor she is tied to and frayed tails
hang. Out of the knot grows a Weaver's wasted body wound in cord, with four arms: the upper two raised high, holding
the ends of the threads that run down into the knot like a puppeteer's strings, bobbins swinging from them; the lower
two held before her, weaving a cradle between their long fingers. Where a Weaver's mask was, a hood full of dark with
two slits of light, veiled in frayed threads; on her head a crown of knots with crooked needles through them. Behind
her a halo that has come apart: two broken rings of cord, knotted where they still hold and fraying where they broke.
Bobbins circle the knot. Painted with the Weavers' own materials, then turned from violet to the sea-green of the
Fray (see `finish`); her gilt is left tarnished, her bone and wood as they are."""
import colorsys
import math
import random

import elderlib
from elderlib import Model, _h, align_y, catenary, mix, noise, shade

TAU = 2 * math.pi
KNOT = (0.0, -74.0, 0.0)  # the knot's heart, in the body's space


# ----------------------------------------------------------------------------- her own cord


def m_cord(ctx, tone="mid", frayed=False):
    """Twisted cord: diagonal plies of thread, light and dark, a dark groove between them, a glint of the Fray's light
    caught in it here and there; frayed, it comes apart towards its end."""
    gx, gy, s = ctx["gx"], ctx["gy"], ctx["seed"]
    px, py, fw, fh = ctx["px"], ctx["py"], ctx["fw"], ctx["fh"]
    lo, hi = {"dark": ((9, 5, 16), (38, 24, 56)), "mid": ((44, 26, 68), (156, 110, 204)),
              "light": ((84, 56, 118), (214, 176, 246))}[tone]
    ply = (px + py + int(2 * noise(gx, gy, s, 4.0))) % 4
    base = mix(lo, hi, (0.15, 0.55, 0.85, 0.6)[ply] * (0.75 + 0.25 * noise(gx, gy, s + 3, 3.0)))
    if ctx["face"] in ("top", "bottom"):
        base = shade(base, 0.75)
    a = 255
    glow = None
    if frayed and ctx["face"] not in ("top", "bottom"):
        k = py / max(1, fh - 1)
        if py > fh - 2 - int(4 * _h(px, 7, s + fw)) or (k > 0.6 and _h(gx, gy, s + 11) < (k - 0.6) * 0.9):
            a = 0
    elif a and _h(gx, gy, s + 29) > (0.997 if tone == "dark" else 0.992):
        base, glow = (170, 110, 230), (210, 160, 255, 210)
    return base + (a,), glow


elderlib.MATS.update({
    "cord": lambda c: m_cord(c),
    "cord_dark": lambda c: m_cord(c, "dark"),
    "cord_light": lambda c: m_cord(c, "light"),
    "cord_frayed": lambda c: m_cord(c, frayed=True),
    "cord_dark_frayed": lambda c: m_cord(c, "dark", frayed=True),
})


# ----------------------------------------------------------------------------- the model


def build():
    m = Model("KnotMother", 1024, 1024, seed=113)
    R = m.part("root", None, (0, 24, 0), anim=True)
    body = m.part("body", R, (0, 0, 0), anim=True)
    # body, torso and head keep their own boxes in a "core" child, as the Weaver's do, so she can be tied together
    # piece by piece as she forms (see KnotMotherModel)
    core = m.part("bodycore", body, (0, 0, 0), anim=True)
    _knot(m, body, core)
    _roots(m, body)
    _tails(m, body)
    torso = m.part("torso", body, (0, -120, 0), anim=True)
    _torso(m, torso, m.part("torsocore", torso, (0, 0, 0), anim=True))
    head = m.part("head", torso, (0, -28, 0), anim=True)
    _head(m, m.part("headcore", head, (0, 0, 0), anim=True))
    _halo(m, head)
    for side, sx in (("l", 1), ("r", -1)):
        _upper_arm(m, torso, side, sx)
        _lower_arm(m, torso, side, sx)
    _weft(m)
    _orbit(m, body)
    return m


def _dir_rot(m, parent, d):
    """The local rotation under `parent` whose +Y points along the model-space direction d."""
    return align_y(m.xf(parent).inv_dir(d))


def _knob(m, part, x, y, z, s, mat="wound_thread"):
    """A tight little knot: two cubes turned against each other."""
    k = m.part(f"{part}_knob{len(m.idx[part]['boxes'])}_{len(m.parts)}", part, (x, y, z), (math.pi / 4, math.pi / 4, 0))
    m.box(k, -s / 2, -s / 2, -s / 2, s, s, s, mat)
    m.box(k, -s * 0.35, -s * 0.65, -s * 0.35, s * 0.7, s * 1.3, s * 0.7, mat)
    return k


# ----------------------------------------------------------------------------- the knot


def _knot(m, body, core):
    """A dark tangle the size of a cottage, loops of cord wound round it every way (each loop turns on its own: group
    loops), belts pulled tight about it, knots bulging out of it, and a great spool half sunk in each flank."""
    knot = m.part("knot", core, KNOT, anim=True)
    m.box(knot, -15, -18, -14, 30, 36, 28, "cord_dark")
    for i, rot in enumerate([(0, math.pi / 4, 0), (math.pi / 4, 0, 0.3), (0.35, 0.6, math.pi / 4), (-0.5, 1.1, 0.2)]):
        lump = m.part(f"lump_{i}", knot, (0, 0, 0), rot)
        w = (28, 27, 25, 24)[i]
        m.box(lump, -w / 2, -w / 2 - 2, -w / 2, w, w + 4, w, "cord_dark")
    rnd = random.Random(5)
    for i in range(10):
        # loops wound every way round the tangle, hard against it (each turns on its own)
        rot = (rnd.uniform(-1.4, 1.4), rnd.uniform(0, math.pi), rnd.uniform(-1.4, 1.4))
        r = 21.5 + rnd.uniform(0, 4.5)
        loop = m.part(f"loop_{i}", knot, (0, 0, 0), rot, group="loops")
        mat, thick = (("cord", 3.4), ("wound_thread", 2.4), ("cord", 4.0), ("thread", 1.3))[i % 4]
        m.ring(f"loop{i}_", loop, (0, 0, 0), r, 16, TAU * r / 16 + 0.9, thick, mat)
    for i in range(4):
        # the lobes of the knot: great loops of heavy cord thrown out to one side of it, so it is no ball
        th, ph = TAU * i / 4 + 0.4, (0.9, 2.1, 1.3, 2.4)[i]
        c = (12 * math.sin(ph) * math.cos(th), 12 * math.cos(ph), 12 * math.sin(ph) * math.sin(th))
        lobe = m.part(f"lobe_{i}", knot, c, (rnd.uniform(-1.2, 1.2), th, rnd.uniform(-1.2, 1.2)), group="loops")
        m.ring(f"lobe{i}_", lobe, (0, 0, 0), 17 + 2 * (i % 2), 14, TAU * 18 / 14 + 1.2, 6.0, "cord_light")
        m.ring(f"lobel{i}_", lobe, (0, 0, -3.2), 17 + 2 * (i % 2), 14, TAU * 18 / 14 + 1.0, 1.0, "thread")
    # belts pulled tight round it
    for j, (y, r) in enumerate([(-11, 18.5), (2, 20.0), (13, 17.0)]):
        belt = m.part(f"belt_{j}", knot, (0, y, 0), (0, 0.13 * j, 0.08 * (j - 1)))
        m.ring(f"belt{j}_", belt, (0, 0, 0), r, 16, TAU * r / 16 + 0.8, 3.0, "cord", plane="xz")
    # tight knots bulging out of it
    for i in range(14):
        phi = math.acos(1 - 2 * (i + 0.5) / 14)
        th = i * 2.399963
        d = 19.5
        p = (d * math.sin(phi) * math.cos(th), d * math.cos(phi), d * math.sin(phi) * math.sin(th))
        _knob(m, knot, p[0], p[1], p[2], 4.2 + (i % 3) * 1.2, ("wound_thread", "cord", "cord")[i % 3])
    # the spools sunk in its flanks, wound thread running off them into the tangle
    for side, sx in (("l", 1), ("r", -1)):
        sp = m.part(f"spool_{side}", body, (sx * 27, -78, -6), anim=True)
        m.box(sp, -4.5, -9, -4.5, 9, 18, 9, "wound_thread")
        for y0 in (-11.5, 9.0):
            m.box(sp, -6.2, y0, -6.2, 12.4, 2.5, 12.4, "boatwood")
        o = m.part(f"spoolo_{side}", sp, (0, 0, 0), (0, math.pi / 4, 0))
        m.box(o, -4.5, -9, -4.5, 9, 18, 9, "wound_thread")
        for y0 in (-11.2, 9.3):
            m.box(o, -6.2, y0, -6.2, 12.4, 1.9, 12.4, "boatwood")
        m.box(sp, -1.0, -15, -1.0, 2, 30, 2, "iron")
        # a thread off the spool into the knot
        a = [sx * 4.2, -2, -2]
        b = m.to_local(sp, [sx * 10, KNOT[1] + 4, -16])
        m.rope(f"spoolth_{side}_", sp, catenary(a, b, 4, 5), 1.0, "thread")


def _roots(m, body):
    """Cords from the knot's underside down to the floor she is tied to, pulled taut, each pinned by a knot."""
    roots = m.part("roots", body, (0, 0, 0), anim=True)
    for k in range(7):
        a = TAU * (k + 0.3) / 7
        top = [16 * math.cos(a), KNOT[1] + 17, 14 * math.sin(a)]
        foot = [(44 + 8 * (k % 3)) * math.cos(a), 26, (40 + 8 * (k % 2)) * math.sin(a)]
        pts = catenary(top, foot, -3, 7)
        m.rope(f"root{k}_", roots, pts, 3.4 if k % 2 == 0 else 2.6, "cord")
        _knob(m, roots, foot[0], foot[1] - 2.5, foot[2], 6.5, "cord_dark")


def _tails(m, body):
    """Frayed tails of cord hanging from the knot's underside nearly to the floor (group robe), and threads of light
    among them (group strand)."""
    for i in range(18):
        a = TAU * i / 18 + 0.11 * (i % 3)
        r = 9 + 6 * (i % 3)
        length = 52 + (i * 13) % 22
        s = m.part(f"tail_{i}", body, (r * math.cos(a), KNOT[1] + 19 + (i % 2) * 2, r * math.sin(a)),
                   (0.12 * math.sin(a), 0, -0.12 * math.cos(a)), group="robe")
        w = 3 + (i % 3)
        m.box(s, -w / 2, 0, -w / 2, w, length, w, "cord_frayed" if i % 3 == 0 else "cord_dark_frayed")
    for i in range(10):
        a = TAU * (i + 0.5) / 10
        m.strand(f"tailth_{i}", body, (13 * math.cos(a), KNOT[1] + 18, 13 * math.sin(a)), 40 + (i * 7) % 18, 0.6, "thread")


# ----------------------------------------------------------------------------- the torso


def _torso(m, torso, t):
    """A wasted Weaver's body grown out of the knot: a waist of twisted cord, a bodice wound round with cord, ribs of
    thread, a seam of knots down its front, a ruff of knots at its neck and a torn shawl (group mantle)."""
    # the waist, twisting up out of the knot
    m.box(t, -7.5, -2, -5.5, 15, 24, 11, "cord_dark")
    for k in range(7):
        wr = m.part(f"waist_{k}", t, (0, 1 + 3 * k, 0), (0.1 * (1 if k % 2 else -1), 0.5 * k, 0.32 * (1 if k % 2 else -1)))
        m.box(wr, -8.6, -0.8, -6.4, 17.2, 1.6, 12.8, "cord")
    m.box(t, -9, -28, -6, 18, 27, 12, "weave")
    m.box(t, -9.3, -14, -6.3, 18.6, 4, 12.6, "weave_under")
    for k in range(5):
        # ribs of thread across the bodice
        rib = m.part(f"rib_{k}", t, (0, -24 + 4.2 * k, -6.2), (0, 0, 0.18 * (1 if k % 2 else -1)))
        m.box(rib, -8.5, -0.4, -0.3, 17, 0.8, 0.6, "thread" if k == 2 else "cord")
    for k in range(6):
        _knob(m, t, 0, -26 + 4.4 * k, -6.6, 2.4 if k % 2 else 3.0, "wound_thread" if k % 2 else "cord")
    # the heart of her: a tangle of light where the Weaver's spindle was
    m.box(t, -1.8, -20, -8.0, 3.6, 3.6, 2.0, "glowcore")
    # the ruff of knots round her neck
    for i in range(10):
        a = -math.pi / 2 + TAU * i / 10
        _knob(m, t, 8.4 * math.cos(a), -28.2, 6.0 * math.sin(a), 3.6, ("cord", "wound_thread")[i % 2])
    # the torn shawl
    n = 12
    for i in range(n):
        a = math.pi + 0.9 + (TAU - 1.8) * i / (n - 1)
        if abs(math.sin(a)) > 0.9:
            continue
        length = 20 + (i * 5) % 9
        sm = m.part(f"shawl_{i}", torso, (11 * math.sin(a), -27, 7 * math.cos(a)), (0.75, a, 0), group="mantle")
        m.box(sm, -3.5, 0, -0.7, 7, length, 1.4, "weave_ragged")


# ----------------------------------------------------------------------------- the head


def _head(m, head):
    """A deep hood full of dark, two narrow slits of light in it, veiled in frayed threads (group veil); a crown of
    knots round its top with crooked needles through them and one great knot on its crown."""
    m.box(head, -8, -22, -5, 16, 22, 13, "weave_under")
    m.box(head, -8.8, -21.4, -6.4, 17.6, 2.0, 2.0, "cord")
    for sx in (1, -1):
        m.box(head, 6.8 if sx > 0 else -8.8, -21.4, -6.4, 2.0, 21, 2.0, "cord")
    m.box(head, -6.8, -19.4, -5.5, 13.6, 19.2, 0.6, "socket")
    # the slits of light, tilted down at the middle
    for side, sx in (("l", 1), ("r", -1)):
        ey = m.part(f"eye_{side}", head, (sx * 3.0, -12.2, -5.75), (0, 0, -0.22 * sx))
        m.box(ey, -1.6, -0.45, -0.25, 3.2, 0.9, 0.3, "eye")
    # the crown of knots
    m.box(head, -8.6, -24.2, -5.6, 17.2, 2.4, 14.2, "cord")
    for i in range(9):
        a = -math.pi / 2 + TAU * i / 9
        x, z = 8.8 * math.cos(a), 1.5 + 7.6 * math.sin(a)
        _knob(m, head, x, -25.4, z, 3.6 if i % 2 else 4.4, ("wound_thread", "cord")[i % 2])
        if i in (1, 4, 6, 8):
            nd = m.part(f"crownneedle_{i}", head, (x, -25.5, z), (0.35 * math.sin(a) + 0.1 * (i % 3), 0, -0.45 * math.cos(a)))
            m.box(nd, -0.45, -14 - 2 * (i % 2), -0.45, 0.9, 12 + 2 * (i % 2), 0.9, "silver")
            m.box(nd, -0.25, -17 - 2 * (i % 2), -0.25, 0.5, 3, 0.5, "silver")
    top = m.part("topknot", head, (0, -27, 2), (0.2, 0.4, 0))
    m.box(top, -4, -6, -4, 8, 7, 8, "cord_dark")
    for k in range(3):
        lp = m.part(f"toploop_{k}", top, (0, -4, 0), (0, TAU * k / 3, 0.9))
        m.ring(f"toploop{k}_", lp, (0, -5, 0), 5.5, 10, 4.4, 1.6, "cord")
    # the veil: frayed threads from the hood's rim, parting before the slits of light
    front = [-6.3, -5.2, -1.0, 0.0, 1.0, 5.2, 6.3]
    for j, x in enumerate(front):
        glow = j in (1, 5)
        m.strand(f"veilf_{j}", head, (x, -20.4, -6.6 - 0.1 * (j % 2)), 20 + (j * 5) % 8, 0.5 if glow else 0.9,
                 "thread" if glow else "veil", group="veil")
    for j, x in enumerate([-4.4, -2.6, 2.6, 4.4]):
        # under the slits the threads hang on, frayed shorter
        m.strand(f"veilu_{j}", head, (x, -10.8, -6.2), 13 + (j * 3) % 5, 0.8, "veil", group="veil")
    pts = [(8.6 * sx, -4.0 + 10.6 * j / 6) for sx in (1, -1) for j in range(7)] + [(-7.2 + 14.4 * j / 7, 8.4) for j in range(8)]
    for i, (x, z) in enumerate(pts):
        glow = i % 5 == 2
        length = 15 + (i * 7) % 9
        m.strand(f"veil_{i}", head, (x, -21.6, z), length, 0.5 if glow else 1.0, "thread" if glow else "veil", group="veil")


def _halo(m, head):
    """A halo that has come apart: two rings of cord with gaps torn in them, knotted where they still hold, threads
    hanging from the broken ends, a few taut threads still tying it in to her head. One subtree under `halo`."""
    halo = m.part("halo", head, (0, -14, 24), anim=True)

    def broken(prefix, r, count, thick, mat, gaps, phase=0.0):
        seg = TAU * r / count + 0.7
        for i in range(count):
            if any(a <= i <= b for a, b in gaps):
                continue
            a = phase + TAU * i / count
            p = m.part(f"{prefix}{i}", halo, (r * math.cos(a), r * math.sin(a), 0), (0, 0, a))
            m.box(p, -thick / 2, -seg / 2, -thick / 2, thick, seg, thick, mat)
        for ga, gb in gaps:
            for edge in (ga - 1, gb + 1):
                a = phase + TAU * edge / count
                x, y = r * math.cos(a), r * math.sin(a)
                _knob(m, halo, x, y, 0, thick * 1.8, "wound_thread")
                m.strand(f"{prefix}fray{edge}", halo, (x, y, 0), 8 + (edge * 5) % 9, 0.6, "thread")

    broken("halo_a", 44, 36, 3.2, "halo", [(8, 12), (25, 27)])
    broken("halo_b", 35, 30, 1.8, "cord", [(3, 4), (16, 20)], phase=0.1)
    broken("halo_c", 52, 40, 1.2, "halo", [(0, 5), (14, 15), (28, 33)], phase=0.05)
    for k in range(8):
        a = TAU * (k + 0.5) / 8
        if 8 <= (k + 0.5) / 8 * 36 <= 12 or 25 <= (k + 0.5) / 8 * 36 <= 27:
            continue
        _knob(m, halo, 44 * math.cos(a), 44 * math.sin(a), 0, 5.4, "cord")
    # threads still tying it in to her head
    for k in range(5):
        a = TAU * k / 5 + 0.3
        p0 = [35 * math.cos(a), 35 * math.sin(a), 0]
        p1 = [11 * math.cos(a), 11 * math.sin(a), -6]
        m.rope(f"halotie{k}_", halo, catenary(p0, p1, 3, 4), 0.7, "thread")


# ----------------------------------------------------------------------------- the arms


def _sleeve(m, arm, length, side):
    m.box(arm, -3.6, -1, -3.6, 7.2, length, 7.2, "weave_ragged")
    m.box(arm, -4.1, length * 0.55, -4.1, 8.2, 2.0, 8.2, "cord")
    m.box(arm, -4.1, length * 0.8, -4.1, 8.2, 1.6, 8.2, "cord")


def _fingers(m, hand, side, n, spread, length=(9, 8, 6)):
    """Long fingers ringed with thread, their tips dark bone; the model-space tips."""
    tips = []
    for f in range(n):
        x = -2.7 + 5.4 * f / (n - 1)
        fr = m.part(f"kf_{side}{f}", hand, (x, 7.5, 0), (0.15, 0, (-0.5 + f / (n - 1)) * spread))
        m.box(fr, -0.8, 0, -0.8, 1.6, length[0], 1.6, "skin")
        m.box(fr, -1.05, length[0] * 0.6, -1.05, 2.1, 1.0, 2.1, "cord")
        fm = m.part(f"kfm_{side}{f}", fr, (0, length[0], 0), (0.2, 0, 0))
        m.box(fm, -0.7, 0, -0.7, 1.4, length[1], 1.4, "skin")
        ft = m.part(f"kft_{side}{f}", fm, (0, length[1], 0), (0.25, 0, 0))
        m.box(ft, -0.55, 0, -0.55, 1.1, length[2], 1.1, "bone_dark")
        tips.append(m.xf(ft).apply([0, length[2], 0]))
    return tips


def _bobbin(m, name, parent, at, length, size=1.0):
    """A bobbin swinging on a thread (group lantern): the thread, a flange, wound thread, a flange and a point."""
    n = m.part(name, parent, at, m.hang_rot(parent), group="lantern")
    s = size
    m.box(n, -0.3, 0, -0.3, 0.6, length, 0.6, "thread")
    y = length
    m.box(n, -2.4 * s, y, -2.4 * s, 4.8 * s, 1.1 * s, 4.8 * s, "boatwood")
    m.box(n, -1.7 * s, y + 1.1 * s, -1.7 * s, 3.4 * s, 5.6 * s, 3.4 * s, "wound_thread")
    m.box(n, -2.4 * s, y + 6.7 * s, -2.4 * s, 4.8 * s, 1.1 * s, 4.8 * s, "boatwood")
    m.box(n, -0.5 * s, y + 7.8 * s, -0.5 * s, 1.0 * s, 2.6 * s, 1.0 * s, "boatwood")
    return n


def _upper_arm(m, torso, side, sx):
    """Raised high, the fist closed on the ends of the threads that run down into the knot; bobbins swing from it."""
    arm = m.part(f"arm_{side}", torso, (10.5 * sx, -24, 0), (0, 0, -2.2 * sx), anim=True)
    _sleeve(m, arm, 28, side)
    _knob(m, arm, 0, 0, 0, 6.5, "cord")
    for j in range(4):
        m.strand(f"sleeve_{side}{j}", arm, (-2 + (j % 2) * 4, 6 + 6 * j, 3 * (1 if j % 2 else -1)), 12 + (j * 5) % 10, 1.4, "weave_under_ragged")
    fo = m.part(f"forearm_{side}", arm, (0, 27, 0), (0, 0, -0.6 * sx), anim=True)
    m.box(fo, -2.6, 0, -2.6, 5.2, 26, 5.2, "skin")
    for k in range(5):
        sg = 1 if k % 2 else -1
        wr = m.part(f"wrap_{side}{k}", fo, (0, 4 + 4 * k, 0), (0.14 * sg, 0, 0.3 * sg))
        m.box(wr, -3.1, -0.45, -3.1, 6.2, 0.9, 6.2, "cord")
    ha = m.part(f"hand_{side}", fo, (0, 26, 0), (0, 0, 0), anim=True)
    m.box(ha, -3.5, 0, -2.5, 7, 7, 5, "skin")
    # the fist: fingers curled over the threads
    for f in range(4):
        cf = m.part(f"fist_{side}{f}", ha, (-2.6 + 1.75 * f, 6.4, -2.4), (-0.9, 0, 0))
        m.box(cf, -0.8, 0, -0.8, 1.6, 4.2, 1.6, "skin")
        m.box(cf, -0.6, 4.0, -0.6, 1.2, 2.2, 1.2, "bone_dark")
    grip = m.xf(ha).apply([0, 4, -1])
    # the threads, from the fist down into the knot
    for k, (dx, dy, dz) in enumerate([(-14, -6, -12), (-4, -16, -14), (-20, 8, 4)]):
        end = [KNOT[0] + sx * (16 + dx * 0.3), KNOT[1] + dy, KNOT[2] + dz]
        pts = catenary(grip, end, 2.5, 9)
        m.rope(f"held_{side}{k}_", ha, [m.to_local(ha, p) for p in pts], 0.9 if k != 1 else 1.4, "thread")
    for k, (length, size) in enumerate([(14, 1.1), (24, 1.4)]):
        _bobbin(m, f"bob_{side}{k}", ha, (-1.5 + 3 * k, 6.5, 1.0), length, size)


def _lower_arm(m, torso, side, sx):
    """Held before her, the long fingers strung with a cradle of threads."""
    arm = m.part(f"arm_l{side}", torso, (9.2 * sx, -9, -1.5), (0, 0, 0), anim=True)
    m.idx[arm]["rot"] = list(_dir_rot(m, torso, [0.6 * sx, 0.75, -0.45]))
    _sleeve(m, arm, 17, side + "l")
    _knob(m, arm, 0, 0.5, 0, 5.2, "cord")
    m.strand(f"sleevel_{side}", arm, (0, 12, 3), 14, 1.4, "weave_under_ragged")
    fo = m.part(f"forearm_l{side}", arm, (0, 17, 0), (0, 0, 0), anim=True)
    m.idx[fo]["rot"] = list(_dir_rot(m, arm, [-0.55 * sx, -0.25, -0.8]))
    m.box(fo, -2.3, 0, -2.3, 4.6, 17, 4.6, "skin")
    for k in range(4):
        sg = 1 if k % 2 else -1
        wr = m.part(f"wrap_l{side}{k}", fo, (0, 3 + 4 * k, 0), (0.14 * sg, 0, 0.3 * sg))
        m.box(wr, -2.8, -0.45, -2.8, 5.6, 0.9, 5.6, "cord")
    ha = m.part(f"hand_l{side}", fo, (0, 17, 0), (0, 0, 0), anim=True)
    m.idx[ha]["rot"] = list(_dir_rot(m, fo, [-0.3 * sx, -0.6, -0.7]))
    m.box(ha, -3.4, 0, -1.8, 6.8, 7.5, 3.6, "skin")
    tips = _fingers(m, ha, "l" + side, 4, 0.9)
    m.idx[ha]["tips"] = tips
    # a cradle between its own fingers, beaded with light
    for k, (a, b, sag) in enumerate([(0, 2, 5), (1, 3, 5), (0, 3, 9)]):
        names = m.rope(f"cradle_{side}{k}_", ha, [m.to_local(ha, p) for p in catenary(tips[a], tips[b], sag, 6)], 0.6, "thread")
        if k == 2:
            m.box(names[3], -0.8, 0, -0.8, 1.6, 1.6, 1.6, "glowcore")


def _weft(m):
    """Threads strung between her lower hands: the work she weaves (hung from the left hand)."""
    left, right = m.idx["hand_ll"]["tips"], m.idx["hand_lr"]["tips"]
    for k, (a, b, sag) in enumerate([(0, 0, 6), (2, 1, 10), (3, 3, 14)]):
        names = m.rope(f"weft_{k}_", "hand_ll", [m.to_local("hand_ll", p) for p in catenary(left[a], right[b], sag, 10)], 0.7, "thread")
        if k == 1:
            m.box(names[5], -0.9, 0, -0.9, 1.8, 1.8, 1.8, "glowcore")
    for p in m.parts:
        p.pop("tips", None)


# ----------------------------------------------------------------------------- the bobbins circling


def _orbit(m, body):
    """Bobbins circling the knot, each trailing a thread. One subtree under `orbit`, which the animation turns."""
    ring = m.part("orbit", body, (0, KNOT[1], 0), anim=True)
    for k in range(8):
        phi = TAU * k / 8
        p = (40 * math.sin(phi), 7.0 * (1 if k % 2 else -1), 40 * math.cos(phi))
        b = m.part(f"orbit_{k}", ring, p, (0.3 * (1 if k % 2 else -1), phi, 0.5))
        s = 1.3 if k % 2 else 1.0
        m.box(b, -2.4 * s, -4.4 * s, -2.4 * s, 4.8 * s, 1.1 * s, 4.8 * s, "boatwood")
        m.box(b, -1.7 * s, -3.3 * s, -1.7 * s, 3.4 * s, 6.6 * s, 3.4 * s, "wound_thread")
        m.box(b, -2.4 * s, 3.3 * s, -2.4 * s, 4.8 * s, 1.1 * s, 4.8 * s, "boatwood")
        # its thread, trailing behind it round the circle
        tr = m.part(f"orbitth_{k}", ring, p, (0, phi + math.pi / 2, 0))
        m.box(tr, -0.3, -0.3, 0, 0.6, 0.6, 14, "thread")


# ----------------------------------------------------------------------------- her colours


def _turn(px, w, h, glow):
    rnd = random.Random(7)
    for y in range(h):
        for x in range(w):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            hh, ss, vv = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
            if 0.55 <= hh <= 0.98 or (ss < 0.25 and vv < 0.35):
                # violet (and the dark of her) to the sea-green of the Fray
                hh = (hh - 0.30) % 1.0
                ss = ss * 0.7 if glow else min(1.0, ss * 0.85)
                if not glow:
                    vv *= 0.86
                    if rnd.random() < 0.05:
                        vv *= 0.8
            elif not glow:
                # gilt tarnished, bone and wood and iron as they are, a little dulled
                ss *= 0.8
                vv *= 0.9
            r2, g2, b2 = colorsys.hsv_to_rgb(hh, ss, vv)
            px[x, y] = (int(r2 * 255), int(g2 * 255), int(b2 * 255), a)


def finish(img, glow):
    """Turns the painted textures to her colours."""
    _turn(img.load(), img.width, img.height, False)
    _turn(glow.load(), glow.width, glow.height, True)
    return img, glow
