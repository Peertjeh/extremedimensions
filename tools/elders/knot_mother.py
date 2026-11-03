"""The Knot Mother, after her concept sheets: a tall, narrow, veiled figure some eighteen blocks high. A deep pointed hood
full of dark, a vertical crack of violet light where a face would be; behind her head a crown of aged bronze, a ring of
thorns with knots of light on it and one tall spire, and a circlet of thorns round the hood. Two great rings float
about her, a tilted ring of thorns round her shoulders and a loom wheel behind her back. Her robes are charcoal and deep
violet, edged in ash lilac, layer over layer of long tatters flaring wide to the floor; a mantle of tatters on her
shoulders; bronze chains in swags across her front. At her breast a thorned bronze frame holds a void-violet core, her
brightest light. Her arms are long, her hands skeletal and clawed: one raised, threads of amber light hanging from its
fingers and looping across to the other, held low with its claws open and threads running down to the floor.

Colour hierarchy (from her design document): void black and charcoal for the mass, deep violet for the cloth, Seam
purple for the core and the crown's knots, amber only for threads, ash lilac for the rim of the cloth; bronze, aged and
dark, for her metal (never gold)."""
import math

import elderlib
from elderlib import Model, _h, align_y, catenary, mix, noise, pixmap, shade

TAU = 2 * math.pi


# ----------------------------------------------------------------------------- her materials


def m_robe(ctx, dark=False, ragged=False):
    """Her cloth: charcoal shading into deep violet along the folds, its edges caught by an ash-lilac rim, a faint
    vein of violet light here and there; ragged, it is torn into points at its end."""
    gx, gy, s = ctx["gx"], ctx["gy"], ctx["seed"]
    px, py, fw, fh = ctx["px"], ctx["py"], ctx["fw"], ctx["fh"]
    lo, hi = ((14, 10, 20), (54, 34, 70)) if dark else ((30, 22, 40), (104, 60, 136))
    fold = 0.5 + 0.5 * math.sin(px * 1.1 + noise(gx, gy * 0.07, s, 5) * 4)
    base = mix(lo, hi, fold * 0.65 + noise(gx, gy, s + 3, 2) * 0.35)
    if (gx + gy) % 3 == 0:
        base = shade(base, 0.85)
    face = ctx["face"]
    if face not in ("top", "bottom") and (px == 0 or px == fw - 1) and not dark:
        base = mix(base, (168, 148, 192), 0.65)
    elif face not in ("top", "bottom") and not dark and _h(px, 3, s) > 0.8:
        base = mix(base, (150, 128, 176), 0.35)
    if face in ("top", "bottom"):
        base = shade(base, 0.75)
    a = 255
    if ragged and face not in ("top", "bottom"):
        k = py / max(1, fh - 1)
        jag = 2 + int(9 * _h(px, 13, s + fw))
        if py > fh - 1 - jag or (k > 0.7 and _h(gx, gy, s + 17) < (k - 0.7) * 0.8):
            a = 0
    glow = None
    if a and noise(gx, gy * 0.1, s + 23, 3) > 0.86 and abs(noise(gx * 0.5, gy * 0.04, s + 29, 2) - 0.5) < 0.035:
        base, glow = (120, 60, 170), (176, 104, 240, 190)
    return base + (a,), glow


def m_bronze(ctx, thorn=False):
    """Aged bronze, dark and worn, warmer where the light catches its edges; a thorn darkens to black at its point."""
    gx, gy, s = ctx["gx"], ctx["gy"], ctx["seed"]
    k = noise(gx, gy, s, 2.5)
    base = mix((46, 32, 24), (124, 86, 52), 0.35 + 0.5 * k)
    if thorn:
        base = mix(base, (20, 14, 14), min(1.0, ctx["py"] / max(1, ctx["fh"] - 1)) * 0.8)
    if ctx["face"] == "top" or ctx["px"] == 0:
        base = mix(base, (176, 124, 72), 0.35)
    if _h(gx, gy, s + 5) > 0.93:
        base = mix(base, (64, 84, 70), 0.5)  # verdigris
    return base + (255,), None


def m_amber(ctx):
    """A thread of her light: amber, bright."""
    k = noise(ctx["gx"], ctx["gy"], ctx["seed"], 2.0)
    return (226, 122, 40, 255), (255, int(140 + 40 * k), 56, 245)


def m_core(ctx):
    """The void core at her breast: deep violet, its middle burning white-violet."""
    fw, fh, px, py = ctx["fw"], ctx["fh"], ctx["px"], ctx["py"]
    d = math.hypot((px + 0.5) / fw - 0.5, (py + 0.5) / fh - 0.5) * 2
    if d < 0.45:
        return (230, 200, 255, 255), (250, 230, 255, 255)
    if d < 0.8:
        return (150, 70, 220, 255), (190, 110, 255, 240)
    return (60, 14, 90, 255), (110, 40, 170, 200)


def m_claw(ctx):
    """Her fingers: grey-violet skin over bone, lilac where the light falls."""
    gx, gy, s = ctx["gx"], ctx["gy"], ctx["seed"]
    base = mix((42, 34, 50), (96, 84, 108), noise(gx, gy, s, 2.0))
    if ctx["px"] == 0:
        base = mix(base, (150, 132, 172), 0.4)
    return base + (255,), None


# The dark inside her hood: no face, a crack of violet light down where a face would be, two glints for eyes
VOID_FACE = [
    "kkkkkkkk",
    "kkkkkkkk",
    "kkkvkkkk",
    "kkkVkkkk",
    "kkkVvkkk",
    "kevkVvek",
    "kkkkVkkk",
    "kkkVvkkk",
    "kkkVkkkk",
    "kkkvVkkk",
    "kkkkVkkk",
    "kkkkvkkk",
    "kkkkkkkk",
    "kkkkkkkk",
    "kkkkkkkk",
]

elderlib.MATS.update({
    "km_robe": lambda c: m_robe(c),
    "km_robe_ragged": lambda c: m_robe(c, ragged=True),
    "km_robe_dark": lambda c: m_robe(c, dark=True),
    "km_robe_dark_ragged": lambda c: m_robe(c, dark=True, ragged=True),
    "km_bronze": lambda c: m_bronze(c),
    "km_thorn": lambda c: m_bronze(c, thorn=True),
    "km_amber": m_amber,
    "km_core": m_core,
    "km_claw": m_claw,
    "km_void_px": pixmap(VOID_FACE, {
        "k": ((8, 5, 12), None), "v": ((120, 50, 180), (170, 90, 240, 200)), "V": ((220, 180, 255), (235, 205, 255, 250)),
        "e": ((150, 70, 220), (200, 130, 255, 230))}, grain=0),
})


# ----------------------------------------------------------------------------- the model


def build():
    m = Model("KnotMother", 1024, 1024, seed=131)
    R = m.part("root", None, (0, 24, 0), anim=True)
    body = m.part("body", R, (0, 0, 0), anim=True)
    # body, torso and head keep their own boxes in a "core" child, so she can be drawn together piece by piece as she
    # forms (see KnotMotherModel)
    core = m.part("bodycore", body, (0, 0, 0), anim=True)
    _robes(m, body, core)
    _chains(m, core)
    torso = m.part("torso", body, (0, -120, 0), anim=True)
    _torso(m, torso, m.part("torsocore", torso, (0, 0, 0), anim=True))
    head = m.part("head", torso, (0, -28, 0), anim=True)
    _head(m, m.part("headcore", head, (0, 0, 0), anim=True))
    _crown(m, head)
    _rings(m, body)
    _arm_raised(m, torso)
    _arm_low(m, torso)
    _threads(m)
    return m


def _dir_rot(m, parent, d):
    """The local rotation under `parent` whose +Y points along the model-space direction d."""
    return align_y(m.xf(parent).inv_dir(d))


def _spike(m, name, parent, at, rot, length, base, mat="km_thorn"):
    """A thorn along local -Y: three tapering boxes to a point."""
    sp = m.part(name, parent, at, rot)
    y = 0.0
    for frac, w in ((0.45, 1.0), (0.35, 0.62), (0.2, 0.3)):
        seg = length * frac
        m.box(sp, -base * w / 2, y - seg, -base * w / 2, base * w, seg, base * w, mat)
        y -= seg
    return sp


def _knot(m, name, parent, at, s):
    """A knot of her light on bronze: a bronze lump turned against itself round a violet glow."""
    k = m.part(name, parent, at, (math.pi / 4, math.pi / 4, 0))
    m.box(k, -s / 2, -s / 2, -s / 2, s, s, s, "km_bronze")
    m.box(k, -s * 0.3, -s * 0.66, -s * 0.3, s * 0.6, s * 1.32, s * 0.6, "km_core")
    return k


# ----------------------------------------------------------------------------- the robes


def _robes(m, body, core):
    """Long tatters of charcoal in under, flaring wide to the floor; an over-robe of deep violet edged in ash lilac,
    open at the front on a long panel; layer over layer (group robe)."""
    m.box(core, -9, -120, -7, 18, 62, 14, "km_robe_dark")
    m.box(core, -12, -60, -10, 24, 60, 20, "km_robe_dark")
    for i in range(24):
        a = TAU * i / 24 + 0.04 * (i % 3)
        length = 136 + (i * 7) % 12
        s = m.part(f"under_{i}", body, (9.5 * math.sin(a), -117, 7 * math.cos(a)), (0.26 + 0.02 * (i % 3), a, 0), group="robe")
        m.box(s, -4, 0, -1, 8 + i % 2, length, 2, "km_robe_dark_ragged")
    n = 18
    for k in range(n):
        a = math.pi + 0.55 + (TAU - 1.1) * k / (n - 1)
        length = 110 + (k * 9) % 18
        s = m.part(f"over_{k}", body, (11 * math.sin(a), -121, 8 * math.cos(a)), (0.23 + 0.015 * (k % 3), a, 0), group="robe")
        m.box(s, -4.5, 0, -0.9, 9, length, 1.8, "km_robe_ragged")
    panel = m.part("panel", body, (0, -121, -8.6), (-0.14, 0, 0), group="robe")
    m.box(panel, -5, 0, -0.6, 10, 128, 1.2, "km_robe_ragged")
    # a short skirt of tatters at the hips, over the rest
    for k in range(12):
        a = TAU * k / 12 + 0.13
        s = m.part(f"hip_{k}", body, (12.5 * math.sin(a), -118, 9 * math.cos(a)), (0.42, a, 0), group="robe")
        m.box(s, -3.5, 0, -0.7, 7, 34 + (k * 5) % 10, 1.4, "km_robe_ragged")


def _chains(m, core):
    """Bronze chains in swags across her front (sparingly: the threads are the light)."""
    for j, (y0, y1, x, sag, z) in enumerate([(-115, -112, 10.5, 22, -9.4), (-92, -84, 15.5, 26, -13.2)]):
        pts = catenary([-x, y0, z], [x, y1, z], sag, 14)
        m.rope(f"chain{j}_", core, pts, 0.75, "km_bronze", links=True)


# ----------------------------------------------------------------------------- the torso


def _torso(m, torso, t):
    """A narrow body under a mantle of tatters (group mantle), and at the breast a thorned bronze frame round the void
    core (`chest`, which the animation pulses)."""
    m.box(t, -8, -28, -5.5, 16, 28, 11, "km_robe")
    m.box(t, -8.3, -6, -5.8, 16.6, 4, 11.6, "km_robe_dark")
    chest = m.part("chest", t, (0, -16, -6.4), anim=True)
    m.box(chest, -3.2, -3.2, -2.6, 6.4, 6.4, 3.2, "km_core")
    for k in range(8):
        a = TAU * k / 8 + math.pi / 8
        length = 7.5 if k % 2 == 0 else 4.5
        _spike(m, f"chestthorn_{k}", chest, (3.6 * math.sin(a), -3.6 * math.cos(a), 0.4), (0, 0, a), length, 1.4)
    rim = m.part("chestrim", chest, (0, 0, 0), (0, 0, math.pi / 4))
    m.box(rim, -4.2, -4.2, -0.6, 8.4, 8.4, 1.4, "km_bronze")
    # the mantle: tatters from the shoulders and the back of the neck
    for i in range(16):
        a = math.pi + 0.7 + (TAU - 1.4) * i / 15
        length = 26 + (i * 7) % 12
        sm = m.part(f"mantle_{i}", torso, (11 * math.sin(a), -27.5, 7.5 * math.cos(a)), (0.4, a, 0), group="mantle")
        m.box(sm, -3.8, 0, -0.7, 7.6, length, 1.4, "km_robe_ragged")


# ----------------------------------------------------------------------------- the head


def _head(m, head):
    """A deep hood rising to a point, its rim edged in lilac, dark inside but for the crack of light; tatters of veil
    hanging from its rim (group veil); a circlet of thorns round its crown."""
    m.box(head, -8, -23, -5.5, 16, 24, 14, "km_robe_dark")
    for k, (w, h, y, z) in enumerate([(12, 6, -29, -3), (8, 5, -34, -0.5), (4, 5, -38.5, 1.5)]):
        pk = m.part(f"hoodpeak_{k}", head, (0, y + h, z + 5.5), (-0.12 * (k + 1), 0, 0))
        m.box(pk, -w / 2, -h, -5.5 + 1.5 * k, w, h, 11 - 3 * k, "km_robe_dark")
    # the rim of the hood round its narrow opening, and the dark inside
    m.box(head, -8.6, -22.6, -6.6, 17.2, 3.4, 2.2, "km_robe")
    for sx in (1, -1):
        m.box(head, 4.2 if sx > 0 else -8.6, -19.6, -6.6, 4.4, 20.4, 2.2, "km_robe")
    m.box(head, -4.2, -19.5, -5.7, 8.4, 16.5, 0.6, "socket")
    face = m.part("face", head, (0, 0, 0), detail=1)
    m.box(face, -4, -19, -5.95, 8, 15, 0.5, "km_void_px")
    # the circlet of thorns round the hood's crown
    for k in range(8):
        a = TAU * k / 8
        x, z = 8.6 * math.sin(a), 1.5 + 7.2 * math.cos(a)
        _spike(m, f"circlet_{k}", head, (x, -23, z), (0.35 * math.cos(a), 0, -0.35 * math.sin(a)), 6 + 2 * (k % 2), 1.4)
    m.ring("circlet", head, (0, -23, 1.5), 8.6, 16, 3.8, 1.2, "km_bronze", plane="xz")
    # tatters of veil from the hood's rim, down over the shoulders and the breast
    for j in range(14):
        sx = 1 if j % 2 else -1
        r = j // 2
        x = sx * (6.6 + 0.4 * r) if r < 5 else sx * (5.8 + r * 0.3)
        z = -6.4 if r < 5 else -6.0
        length = 18 + (j * 5) % 12
        m.strand(f"veil_{j}", head, (x, -21 + 2.6 * (r % 5), z), length, 1.6, "veil", group="veil")
    for j in range(10):
        a = math.pi / 2 + math.pi * j / 9
        m.strand(f"veilb_{j}", head, (8.4 * math.cos(a), -20, 1.5 + 7.6 * math.sin(a)), 24 + (j * 7) % 10, 2.0, "km_robe_dark_ragged",
                 group="veil")


def _crown(m, head):
    """The knot crown: a ring of aged bronze behind her head, thorns standing out of it, knots of light on it, a thin
    inner ring of thorny vine, and one tall spire (`halo`, which the animation turns)."""
    halo = m.part("halo", head, (0, -15, 9), anim=True)
    m.ring("crown_a", halo, (0, 0, 0), 21, 32, 4.6, 3.0, "km_bronze")
    m.ring("crown_b", halo, (0, 0, 0), 13.5, 22, 4.4, 1.1, "km_thorn")
    for k in range(12):
        a = TAU * k / 12 + math.pi / 12
        length = 9.5 if k % 2 == 0 else 5.5
        _spike(m, f"crownthorn_{k}", halo, (22 * math.cos(a), 22 * math.sin(a), 0), (0, 0, a + math.pi / 2), length, 1.8)
    for k in range(6):
        a = TAU * k / 6
        _spike(m, f"crownbarb_{k}", halo, (13 * math.cos(a), 13 * math.sin(a), 0), (0, 0, a + math.pi / 2), 4.5, 1.0)
    for k, a in enumerate((math.pi / 4, 3 * math.pi / 4, 5 * math.pi / 4, 7 * math.pi / 4)):
        _knot(m, f"crownknot_{k}", halo, (21 * math.cos(a), 21 * math.sin(a), -0.5), 4.2)
    # the spire, up from the top of the ring
    _spike(m, "spire", halo, (0, -22, 0), (0, 0, 0), 26, 3.0)
    _knot(m, "spireknot", halo, (0, -23.5, -0.6), 4.8)


# ----------------------------------------------------------------------------- the rings


def _rings(m, body):
    """The rings that float about her: a tilted ring of thorns round her shoulders (`ring_a`) and a loom wheel behind her
    back with spokes to its hub (`ring_b`), each with knots of light."""
    ra = m.part("ring_a", body, (0, -133, 0), (0.24, 0, 0.12), anim=True)
    m.ring("ringa_", ra, (0, 0, 0), 31, 36, 5.8, 3.2, "km_bronze", plane="xz")
    m.ring("ringav_", ra, (0, -1.9, 0), 31, 24, 8.3, 0.7, "km_core", plane="xz")
    for k in range(10):
        a = TAU * k / 10 + 0.2
        # thorns standing out and up from it
        _spike(m, f"ringathorn_{k}", ra, (32 * math.cos(a), 0, 32 * math.sin(a)), (0, -a, -math.pi / 2 + 0.5), 7 if k % 2 else 4.5, 1.6)
    for k in range(4):
        a = TAU * k / 4 + 0.6
        _knot(m, f"ringaknot_{k}", ra, (31 * math.cos(a), 0, 31 * math.sin(a)), 4.0)
    rb = m.part("ring_b", body, (0, -104, 19), anim=True)
    m.ring("ringb_", rb, (0, 0, 0), 25, 30, 5.6, 3.0, "km_bronze")
    m.box(rb, -2.4, -2.4, -1.6, 4.8, 4.8, 3.2, "km_bronze")
    for k in range(6):
        a = TAU * k / 6
        sp = m.part(f"ringbspoke_{k}", rb, (0, 0, 0), (0, 0, a))
        m.box(sp, -0.6, 2, -0.6, 1.2, 22, 1.2, "km_thorn")
        _knot(m, f"ringbknot_{k}", rb, (25 * math.cos(a + math.pi / 6), 25 * math.sin(a + math.pi / 6), 0), 3.4)


# ----------------------------------------------------------------------------- the arms


def _sleeve(m, arm, length, side):
    m.box(arm, -3.6, -1, -3.6, 7.2, length, 7.2, "km_robe")
    m.box(arm, -5.0, length * 0.62, -5.0, 10, length * 0.42, 10, "km_robe")
    for j in range(5):
        m.strand(f"sleeve_{side}{j}", arm, (-3.5 + 1.75 * j, length * 0.95, 3.5 * (1 if j % 2 else -1)), 14 + (j * 7) % 16, 2.2,
                 "km_robe_dark_ragged")


def _hand(m, fore, side, at, d, spread, curl):
    """A skeletal hand: a narrow palm, four long fingers of three joints and a thumb, each ending in a black claw. The
    model-space fingertips are kept on the hand for the threads."""
    ha = m.part(f"hand_{side}", fore, at, (0, 0, 0), anim=True)
    m.idx[ha]["rot"] = list(_dir_rot(m, fore, d))
    m.box(ha, -3.2, 0, -1.4, 6.4, 7, 2.8, "km_claw")
    tips = []
    for f in range(4):
        fr = m.part(f"kf_{side}{f}", ha, (-2.4 + 1.6 * f, 6.5, 0), (curl, 0, (-0.5 + f / 3) * spread))
        m.box(fr, -0.6, 0, -0.6, 1.2, 10, 1.2, "km_claw")
        m.box(fr, -0.8, 9.2, -0.8, 1.6, 1.6, 1.6, "km_claw")
        fm = m.part(f"kfm_{side}{f}", fr, (0, 10, 0), (curl, 0, 0))
        m.box(fm, -0.5, 0, -0.5, 1.0, 8.5, 1.0, "km_claw")
        ft = m.part(f"kft_{side}{f}", fm, (0, 8.5, 0), (curl * 1.3, 0, 0))
        m.box(ft, -0.45, 0, -0.45, 0.9, 5, 0.9, "km_thorn")
        tips.append(m.xf(ft).apply([0, 5, 0]))
    sg = 1 if side == "l" else -1
    th = m.part(f"kthumb_{side}", ha, (3.0 * sg, 2.5, -0.6), (curl, 0, 0.8 * sg))
    m.box(th, -0.6, 0, -0.6, 1.2, 8, 1.2, "km_claw")
    m.box(th, -0.45, 8, -0.45, 0.9, 4, 0.9, "km_thorn")
    m.idx[ha]["tips"] = tips
    return ha


def _arm(m, torso, side, sx, up_d, fore_d, hand_d, spread, curl):
    arm = m.part(f"arm_{side}", torso, (10.5 * sx, -25, 0), (0, 0, 0), anim=True)
    m.idx[arm]["rot"] = list(_dir_rot(m, torso, up_d))
    _sleeve(m, arm, 30, side)
    fo = m.part(f"forearm_{side}", arm, (0, 30, 0), (0, 0, 0), anim=True)
    m.idx[fo]["rot"] = list(_dir_rot(m, arm, fore_d))
    m.box(fo, -2.0, 0, -2.0, 4.0, 26, 4.0, "km_claw")
    for k in range(3):
        wr = m.part(f"wrap_{side}{k}", fo, (0, 6 + 7 * k, 0), (0.15 * (1 if k % 2 else -1), 0, 0.3))
        m.box(wr, -2.5, -0.4, -2.5, 5, 0.8, 5, "km_amber" if k == 1 else "km_bronze")
    return _hand(m, fo, side, (0, 26, 0), hand_d, spread, curl)


def _arm_raised(m, torso):
    """Her right arm, raised: the hand up at her head, palm out, threads hanging from its fingers."""
    _arm(m, torso, "r", -1, [-0.8, 0.15, -0.58], [-0.12, -0.9, -0.42], [0.0, -0.96, -0.28], 1.0, 0.18)


def _arm_low(m, torso):
    """Her left arm, low and forward, claws open, threads running from them down to the floor."""
    _arm(m, torso, "l", 1, [0.55, 0.72, -0.42], [0.28, 0.3, -0.91], [0.12, 0.5, -0.86], 0.9, 0.3)


# ----------------------------------------------------------------------------- the threads


def _threads(m):
    """Amber threads: hanging from the raised hand's fingers, strung between its fingers, looping across to the low
    hand, and running down from the low hand's claws to the floor."""
    hr, hl = "hand_r", "hand_l"
    tr, tl = m.idx[hr]["tips"], m.idx[hl]["tips"]

    def rope(prefix, parent, pts, thick=0.7):
        return m.rope(prefix, parent, [m.to_local(parent, p) for p in pts], thick, "km_amber")

    for f, length in enumerate((46, 70, 58, 34)):
        m.strand(f"rhang_{f}", hr, m.to_local(hr, tr[f]), length, 0.6, "km_amber")
    for k, (a, b, sag) in enumerate([(0, 2, 7), (1, 3, 9)]):
        rope(f"rweb_{k}_", hr, catenary(tr[a], tr[b], sag, 6))
    rope("rloop_", hr, catenary(tr[3], tl[0], 34, 14), 0.8)
    rope("rloopb_", hr, catenary(tr[2], tl[2], 52, 16), 0.7)
    for f in range(4):
        tip = tl[f]
        m.strand(f"lhang_{f}", hl, m.to_local(hl, tip), max(10.0, 48 - tip[1] - 6 * (f % 2)), 0.6, "km_amber")
    rope("lweb_", hl, catenary(tl[0], tl[3], 6, 6))
    for p in m.parts:
        p.pop("tips", None)
