"""The Starved One: what came through the first wound. A gaunt, hooded thing some seven blocks tall that hangs in the
air over the Heart: a robe of black tatters split by violet cracks, frayed to shreds where its legs would be; a hood
with nothing in it but a single burning slit; a halo of rusted spikes standing behind its head; a
wound of light down its chest; long, starved arms in torn sleeves ending in claws, and rusted chains wound round it
and hanging from its wrists (the Tether that could not hold it). Sized for the Heart's island (see MendingService)."""
import math

from elderlib import MATS, Model, _edge, _h, catenary, mix, noise, shade

TAU = 2 * math.pi
SIDES = ("north", "south", "east", "west")


# ----------------------------------------------------------------------------- materials


def m_starved_robe(ctx, ragged=False, dark=False):
    """Black cloth in long folds, split by thin violet cracks that glow, frayed to nothing at the hem."""
    gx, gy, s = ctx["gx"], ctx["gy"], ctx["seed"]
    fold = 0.5 + 0.5 * math.sin(ctx["px"] * 1.4 + noise(gx, gy * 0.1, s, 4) * 3)
    lo, hi = ((4, 3, 8), (30, 22, 40)) if dark else ((8, 6, 12), (48, 36, 62))
    base = mix(lo, hi, fold * 0.6 + noise(gx, gy, s + 3, 2) * 0.4)
    if _h(gx, gy, s + 1) > 0.994:
        base = (58, 34, 80)
    a = 255
    if ragged and ctx["face"] in SIDES:
        k = ctx["py"] / max(1, ctx["fh"] - 1)
        jag = 2 + int(6 * _h(ctx["px"], 11, s + ctx["fw"]))
        if ctx["py"] > ctx["fh"] - 1 - jag or (k > 0.55 and _h(gx, gy, s + 13) < (k - 0.55) * 0.8):
            a = 0
    glow = None
    if not dark and abs(noise(gx * 0.6, gy * 0.08, s + 21, 3) - 0.5) < 0.011:
        base = (124, 62, 186)
        glow = (176, 76, 255, 225)
    return base + (a,), glow


def m_hood(ctx):
    gx, gy, s = ctx["gx"], ctx["gy"], ctx["seed"]
    base = mix((10, 8, 14), (52, 42, 64), noise(gx, gy, s, 2.5) * 0.7 + 0.15 * math.sin(ctx["px"] * 0.9))
    if _edge(ctx) == 0:
        base = shade(base, 1.3)
    return base + (255,), None


def m_void(ctx):
    """The inside of the hood: nothing, but for a faint violet haze at its edge."""
    e = _edge(ctx)
    if e == 0:
        return (22, 8, 34, 255), (60, 16, 96, 110)
    return (3, 2, 6, 255), None


def m_slit(ctx):
    """Its eye: one slit of white-violet light."""
    e = _edge(ctx)
    core = e >= 1 or min(ctx["fw"], ctx["fh"]) <= 2
    return ((236, 190, 255, 255), (255, 236, 255, 255)) if core else ((196, 92, 255, 255), (214, 120, 255, 240))


def m_core(ctx):
    """The wound down its chest: forks of violet light, white at the heart of them."""
    gx, gy, s = ctx["gx"], ctx["gy"], ctx["seed"]
    k = noise(gx * 2, gy * 2, s, 1.5)
    if _edge(ctx) >= 1 and k > 0.35:
        return (240, 200, 255, 255), (255, 238, 255, 255)
    return (150, 60, 220, 255), (int(190 + 50 * k), int(90 + 70 * k), 255, 245)


def m_starved_skin(ctx):
    """Gaunt, ash-violet skin over bone, with veins of light."""
    gx, gy, s = ctx["gx"], ctx["gy"], ctx["seed"]
    base = mix((26, 20, 32), (74, 62, 88), noise(gx, gy, s, 2.0))
    if _edge(ctx) == 0:
        base = shade(base, 0.7)
    glow = None
    if abs(noise(gx * 0.7, gy * 0.15, s + 5, 3) - 0.5) < 0.014:
        base = (110, 56, 160)
        glow = (160, 70, 240, 200)
    return base + (255,), glow


def m_claw(ctx, tip=False):
    gx, gy, s = ctx["gx"], ctx["gy"], ctx["seed"]
    if tip:
        return (168, 100, 228, 255), (220, 150, 255, 235)
    base = mix((34, 28, 38), (104, 90, 108), noise(gx, gy, s, 1.5))
    if _edge(ctx) == 0:
        base = shade(base, 1.25)
    return base + (255,), None


MATS.update({
    "starved_robe": lambda c: m_starved_robe(c),
    "starved_robe_ragged": lambda c: m_starved_robe(c, ragged=True),
    "starved_shred": lambda c: m_starved_robe(c, ragged=True, dark=True),
    "starved_hood": m_hood,
    "starved_void": m_void,
    "starved_slit": m_slit,
    "starved_core": m_core,
    "starved_skin": m_starved_skin,
    "starved_claw": lambda c: m_claw(c),
    "starved_claw_tip": lambda c: m_claw(c, tip=True),
})


# ----------------------------------------------------------------------------- the body


def build():
    m = Model("StarvedOne", 512, 512, seed=131)
    R = m.part("root", None, (0, 24, 0), anim=True)
    body = m.part("body", R, (0, 0, 0), anim=True)
    _robe(m, body)
    _chest(m, body)
    head = m.part("head", body, (0, -78, 0), anim=True)
    _hood(m, head)
    _halo(m, head)
    for side, sx in (("l", 1), ("r", -1)):
        _arm(m, body, side, sx)
    _chains(m, body)
    return m


def _robe(m, body):
    """The torso under the robe, a ring of tatters from the waist that hangs short of the ground (it floats), black
    shreds under them, and a mantle of longer tatters from the shoulders, round the back and sides."""
    m.box(body, -9, -77, -6, 18, 37, 12, "starved_robe")
    m.box(body, -10, -44, -7, 20, 8, 14, "starved_robe")
    for i in range(24):
        a = TAU * i / 24 + 0.04 * (i % 3)
        length = 30 + (i * 7) % 8
        s = m.part(f"skirt_{i}", body, (10 * math.sin(a), -42, 7.5 * math.cos(a)), (0.12 + 0.02 * (i % 3), a, 0), group="robe")
        m.box(s, -2.6, 0, -0.8, 5.2 + (i % 2), length, 1.6, "starved_robe_ragged")
    for i in range(14):
        a = TAU * i / 14 + 0.2
        length = 34 + (i * 5) % 7
        s = m.part(f"shred_{i}", body, (6.5 * math.sin(a), -40, 4.5 * math.cos(a)), (0.05, a, 0), group="robe")
        m.box(s, -2.2, 0, -0.7, 4.4, length, 1.4, "starved_shred")
    for i in range(16):
        # round the back and the sides, open at the front of the chest
        a = -(math.pi - 0.95) + 2 * (math.pi - 0.95) * i / 15
        length = 22 + (i * 5) % 9
        s = m.part(f"mantle_{i}", body, (13 * math.sin(a), -76, 9 * math.cos(a)), (0.28 + 0.03 * (i % 2), a, 0), group="robe")
        m.box(s, -3, 0, -0.8, 6, length, 1.6, "starved_robe_ragged")


def _chest(m, body):
    """The wound: a crack of light down the chest, forking."""
    m.box(body, -1.2, -72, -6.7, 2.4, 26, 1.0, "starved_core")
    for j, (y, zr, length) in enumerate([(-68, 0.7, 7), (-64, -0.6, 8), (-57, 0.55, 6), (-52, -0.75, 5)]):
        c = m.part(f"crack_{j}", body, (0, y, -6.75), (0, 0, zr))
        m.box(c, -0.55, 0, -0.1, 1.1, length, 0.9, "starved_core")


def _hood(m, head):
    """A deep hood, its brim peaked over a face of nothing but a burning slit; tatters fall from its back."""
    m.box(head, -8, -19, -8, 16, 19, 16, "starved_hood")
    m.box(head, -8.6, -20.5, -9.6, 17.2, 3, 2.6, "starved_hood")
    for sx in (-1, 1):
        m.box(head, 7.4 if sx > 0 else -9.2, -16, -9.4, 1.8, 18, 6, "starved_hood")
    peak = m.part("hoodpeak", head, (0, -19, 1), (-0.35, 0, 0))
    m.box(peak, -6, -5, -6, 12, 5, 12, "starved_hood")
    m.box(peak, -3.5, -9, -3, 7, 4, 7, "starved_hood")
    m.box(peak, -1.5, -12, -0.5, 3, 3, 3, "starved_hood")
    m.box(head, -6.2, -16.5, -8.5, 12.4, 15, 1.0, "starved_void")
    m.box(head, -0.7, -15.0, -9.0, 1.4, 12.0, 0.8, "starved_slit")
    for i in range(6):
        a = -0.9 + 1.8 * i / 5
        t = m.part(f"hoodtail_{i}", head, (7 * math.sin(a), -6, 7.5 * math.cos(a)), (0.22, a, 0), group="robe")
        m.box(t, -2.5, 0, -0.7, 5, 16 + (i * 3) % 6, 1.4, "starved_robe_ragged")


def _halo(m, head):
    """A halo of rusted iron standing behind its head, spiked all round, an inner ring within it (it turns slowly)."""
    halo = m.part("halo", head, (0, -14, 11), anim=True)
    m.ring("haloring_", halo, (0, 0, 0), 15, 30, 3.4, 1.8, "rust")
    m.ring("haloinner_", halo, (0, 0, 0), 11, 22, 3.4, 1.0, "iron", phase=0.15)
    for i in range(14):
        a = TAU * i / 14
        length = 9 if i % 2 == 0 else 6
        sp = m.part(f"spike_{i}", halo, (15 * math.cos(a), 15 * math.sin(a), 0), (0, 0, a + math.pi / 2))
        m.box(sp, -0.8, -length, -0.8, 1.6, length, 1.6, "rust")
        m.box(sp, -0.45, -length - 2, -0.45, 0.9, 2, 0.9, "iron")


def _arm(m, body, side, sx):
    """A long, starved arm in a torn sleeve, reaching: shoulder, forearm in a rusted cuff, a hand of five long claws."""
    arm = m.part(f"arm_{side}", body, (sx * 12, -73, 0), (-0.22, 0, -sx * 0.34), anim=True)
    m.box(arm, -2.5, 0, -2.5, 5, 27, 5, "starved_skin")
    m.box(arm, -3.6, -1, -3.6, 7.2, 15, 7.2, "starved_robe_ragged")
    for k in range(4):
        a = TAU * k / 4 + 0.4
        t = m.part(f"sleeve_{side}{k}", arm, (3.4 * math.cos(a), 12, 3.4 * math.sin(a)), (0.15, -a, 0), group="sleeve")
        m.box(t, -1.8, 0, -0.5, 3.6, 12 + (k * 5) % 7, 1.0, "starved_robe_ragged")
    fore = m.part(f"fore_{side}", arm, (0, 26, 0), (-0.42, 0, sx * 0.1), anim=True)
    m.box(fore, -2, 0, -2, 4, 25, 4, "starved_skin")
    m.box(fore, -2.7, 15, -2.7, 5.4, 4.5, 5.4, "rust")
    hand = m.part(f"hand_{side}", fore, (0, 25, 0), anim=True)
    m.box(hand, -3, 0, -2.2, 6, 6, 4.4, "starved_skin")
    for j, (x, spread, length) in enumerate([(-2.2, -0.18, 12), (-0.75, -0.06, 14), (0.75, 0.06, 14), (2.2, 0.18, 12)]):
        f = m.part(f"finger_{side}{j}", hand, (x * sx, 5.5, -0.6), (-0.3, 0, spread * sx))
        m.box(f, -0.7, 0, -0.7, 1.4, length, 1.4, "starved_claw")
        tip = m.part(f"tip_{side}{j}", f, (0, length, 0), (-0.55, 0, 0))
        m.box(tip, -0.5, 0, -0.5, 1.0, 6, 1.0, "starved_claw_tip")
    th = m.part(f"thumb_{side}", hand, (sx * -3.0, 3.5, -1.6), (-0.6, 0, sx * -0.7))
    m.box(th, -0.65, 0, -0.65, 1.3, 9, 1.3, "starved_claw")
    tip = m.part(f"thumbtip_{side}", th, (0, 9, 0), (-0.5, 0, 0))
    m.box(tip, -0.45, 0, -0.45, 0.9, 5, 0.9, "starved_claw_tip")
    # a broken length of chain swinging from the cuff
    ch = m.part(f"wristchain_{side}", fore, (sx * 2.7, 18, 0), m.hang_rot(fore), group="chain")
    pts = [(0, 3.2 * k, 0) for k in range(9)]
    m.rope(f"wristlink_{side}", ch, pts, 0.9, "rust", links=True)


def _chains(m, body):
    """Chains wound round it: one across the chest from shoulder to hip and round the back, and two loops sagging at
    the waist."""
    loop = [(8.5, -71, -6.6), (-9.6, -47, -7.4), (-10.6, -45, 0), (-9.0, -47, 7.0), (9.4, -70, 6.6), (9.8, -72, 0), (8.5, -71, -6.6)]
    pts = []
    for a, b in zip(loop, loop[1:]):
        n = max(2, int(math.dist(a, b) / 3.0))
        for k in range(n):
            pts.append(tuple(a[i] + (b[i] - a[i]) * k / n for i in range(3)))
    pts.append(loop[-1])
    m.rope("wrap_", body, pts, 0.9, "rust", links=True)
    for j, (x0, x1, sag) in enumerate([(-8.5, 3.0, 9), (-2.0, 9.0, 12)]):
        hang = catenary((x0, -40, -7.6 - 0.3 * j), (x1, -38, -7.6 - 0.3 * j), sag, 7)
        m.rope(f"sag{j}_", body, hang, 0.85, "rust", links=True)
