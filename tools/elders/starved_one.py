"""The Starved One: what came through the first wound. Not a Weaver (the Weavers' signs are the halo, the gown and the
violet light): it is the hunger itself, and nothing about it is woven. A starved, hunched thing some six blocks tall
that hangs in the air, leaning in towards what it wants: an open ribcage of bone over a belly of ember light (it is
always empty), a hood with no face in it but a wide burning maw lined with teeth, a crown of broken iron spikes on its
brow and the snapped chains of a binding that could not hold it (the Tether, the seal), wound round it and dragging
from its wrists. Its arms are long and bony, its claws longer; below the waist it has no legs, only grave-rags that
thin into a trailing tail of tatters. Ash-grey skin, charcoal rags, rust and ember. Sized for the Heart's island (see
MendingService)."""
import math

from elderlib import MATS, Model, _edge, _h, align_y, catenary, mix, noise, shade

TAU = 2 * math.pi
SIDES = ("north", "south", "east", "west")
LEAN = 0.45  # how far its body leans in towards its prey


# ----------------------------------------------------------------------------- materials


def m_rag(ctx, dark=False):
    """Charcoal grave-cloth in folds, singed rust-red at its torn edges, frayed to nothing at the bottom."""
    gx, gy, s = ctx["gx"], ctx["gy"], ctx["seed"]
    fold = 0.5 + 0.5 * math.sin(ctx["px"] * 1.3 + noise(gx, gy * 0.12, s, 4) * 3)
    lo, hi = ((6, 5, 5), (30, 26, 25)) if dark else ((11, 9, 9), (50, 44, 41))
    base = mix(lo, hi, fold * 0.55 + noise(gx, gy, s + 3, 2) * 0.45)
    a = 255
    if ctx["face"] in SIDES:
        k = ctx["py"] / max(1, ctx["fh"] - 1)
        jag = 2 + int(6 * _h(ctx["px"], 11, s + ctx["fw"]))
        cut = ctx["fh"] - 1 - jag
        if ctx["py"] > cut or (k > 0.5 and _h(gx, gy, s + 13) < (k - 0.5) * 0.9):
            a = 0
        elif ctx["py"] > cut - 2:
            base = mix(base, (74, 30, 18), 0.6)
    if _h(gx, gy, s + 17) > 0.992:
        base = (66, 58, 52)
    return base + (a,), None


def m_hood(ctx):
    gx, gy, s = ctx["gx"], ctx["gy"], ctx["seed"]
    base = mix((9, 8, 8), (46, 40, 38), noise(gx, gy, s, 2.5) * 0.7 + 0.15 * math.sin(ctx["px"] * 0.8))
    if _edge(ctx) == 0:
        base = shade(base, 1.25)
    return base + (255,), None


def m_void(ctx):
    """The inside of the hood: nothing, but for an ember haze at its edge."""
    if _edge(ctx) == 0:
        return (30, 10, 4, 255), (110, 34, 10, 110)
    return (2, 1, 1, 255), None


def m_maw(ctx):
    """Its mouth: a wide slit of ember light, white-hot along its middle."""
    core = _edge(ctx) >= 1 or (min(ctx["fw"], ctx["fh"]) <= 2 and ctx["py"] == ctx["fh"] // 2)
    return ((255, 228, 176, 255), (255, 242, 210, 255)) if core else ((236, 96, 30, 255), (255, 122, 40, 240))


def m_core(ctx):
    """The hunger in its empty belly: embers that never go out, hottest in their hearts."""
    gx, gy, s = ctx["gx"], ctx["gy"], ctx["seed"]
    k = noise(gx * 1.6, gy * 1.6, s, 1.8)
    if k > 0.62:
        return (255, 214, 150, 255), (255, 232, 180, 255)
    if k > 0.38:
        return (220, 84, 24, 255), (255, int(90 + 80 * k), 34, 240)
    return (96, 26, 8, 255), (170, 46, 14, 200)


def m_starved_bone(ctx):
    gx, gy, s = ctx["gx"], ctx["gy"], ctx["seed"]
    base = mix((128, 118, 106), (212, 204, 188), noise(gx, gy, s, 1.6))
    if _edge(ctx) == 0:
        base = shade(base, 0.72)
    if _h(gx, gy, s + 5) > 0.93:
        base = shade(base, 0.6)
    return base + (255,), None


def m_starved_skin(ctx):
    """Ash-grey skin drawn tight over bone, darker in its hollows, now and then a vein of ember."""
    gx, gy, s = ctx["gx"], ctx["gy"], ctx["seed"]
    base = mix((44, 41, 40), (108, 102, 96), noise(gx, gy, s, 2.2))
    if _edge(ctx) == 0:
        base = shade(base, 0.7)
    glow = None
    if abs(noise(gx * 0.7, gy * 0.18, s + 5, 3) - 0.5) < 0.012:
        base = (180, 64, 22)
        glow = (255, 96, 30, 190)
    return base + (255,), glow


def m_claw(ctx, tip=False):
    gx, gy, s = ctx["gx"], ctx["gy"], ctx["seed"]
    if tip:
        return (232, 110, 44, 255), (255, 146, 70, 235)
    base = mix((26, 22, 20), (88, 78, 70), noise(gx, gy, s, 1.5))
    if _edge(ctx) == 0:
        base = shade(base, 1.25)
    return base + (255,), None


MATS.update({
    "starved_rag": lambda c: m_rag(c),
    "starved_rag_dark": lambda c: m_rag(c, dark=True),
    "starved_hood": m_hood,
    "starved_void": m_void,
    "starved_maw": m_maw,
    "starved_core": m_core,
    "starved_bone": m_starved_bone,
    "starved_skin": m_starved_skin,
    "starved_claw": lambda c: m_claw(c),
    "starved_claw_tip": lambda c: m_claw(c, tip=True),
})


# ----------------------------------------------------------------------------- helpers


def rot_from(m, parent, direction):
    """The local rotation under `parent` that points a part's +Y along a model-space direction."""
    return align_y(m.xf(parent).inv_dir(list(direction)))


def hang(m, parent, flare_x=0.0, flare_z=0.0):
    """A rotation that hangs a part from `parent` roughly plumb, swung a little by (flare_x, flare_z) in the model."""
    d = [flare_x, 1.0, flare_z]
    n = math.sqrt(sum(c * c for c in d))
    return rot_from(m, parent, [c / n for c in d])


# ----------------------------------------------------------------------------- the body


def build():
    m = Model("StarvedOne", 512, 512, seed=131)
    R = m.part("root", None, (0, 24, 0), anim=True)
    _tail(m, R)
    body = m.part("body", R, (0, -36, 0), (LEAN, 0, 0), anim=True)
    _torso(m, body)
    _shroud(m, body)
    head = m.part("head", body, (0, -34, -3), (-0.38, 0, 0), anim=True)
    _hood(m, head)
    _crown(m, head)
    for side, sx in (("l", 1), ("r", -1)):
        _arm(m, body, side, sx)
    _chains(m, body)
    return m


def _torso(m, body):
    """A starved body: a narrow waist, the back of a ribcage, ribs open at the front over a belly of embers, a sternum,
    bony shoulders and a spine standing out along its back."""
    m.box(body, -4.5, -10, -3.2, 9, 10, 6.6, "starved_skin")
    m.box(body, -7.5, -31, -0.8, 15, 22, 6, "starved_skin")
    m.box(body, -5.6, -28.5, -3.4, 11.2, 18, 2.8, "starved_core")
    m.box(body, -0.8, -31, -4.3, 1.6, 18, 1.5, "starved_bone")
    for j in range(6):
        y = -28.6 + j * 3.1
        half = 7.2 - abs(j - 2) * 0.45
        for side, sx in (("l", 1), ("r", -1)):
            r = m.part(f"rib_{side}{j}", body, (sx * 0.7, y, -3.7), (0, 0, sx * (0.16 + 0.025 * j)))
            x0 = 0 if sx > 0 else -half
            m.box(r, x0, -0.6, -0.7, half, 1.2, 1.4, "starved_bone")
            m.box(r, (half - 1.2) if sx > 0 else -half, -0.6, -0.6, 1.2, 1.2, 5.2, "starved_bone")
    m.box(body, -10.5, -34, -2.6, 21, 4, 6.8, "starved_skin")
    for sx in (-1, 1):
        m.box(body, sx * 10.2 - 2.4, -35.5, -2.4, 4.8, 4.2, 5.6, "starved_bone")
    for k in range(8):
        m.box(body, -1.1, -32 + k * 4, 4.9, 2.2, 2.2, 1.6, "starved_bone")


def _shroud(m, body):
    """Grave-rags over its shoulders, round its back and sides, open at the front where the ribs show."""
    for i in range(15):
        a = -(math.pi / 2 + 0.55) + (math.pi + 1.1) * i / 14
        at = (9.8 * math.sin(a), -32.5, 3.0 + 4.0 * math.cos(a))
        flare = 0.16 + 0.05 * (i % 3)
        r = hang(m, body, math.sin(a) * flare, 0.22 + math.cos(a) * flare)
        s = m.part(f"shroud_{i}", body, at, r, group="shroud")
        length = 26 + (i * 7) % 10
        m.box(s, -2.7, 0, -0.7, 5.4 + (i % 2) * 0.8, length, 1.4, "starved_rag")
    for i, sx in enumerate((-1, 1)):
        # a torn flap over each shoulder
        s = m.part(f"shroud_sh{i}", body, (sx * 9.5, -35, -1.5), hang(m, body, sx * 0.35, -0.1), group="shroud")
        m.box(s, -3.2, 0, -0.7, 6.4, 13, 1.4, "starved_rag")


def _tail(m, root):
    """No legs: below the waist the rags gather into a tail of tatters that trails behind and thins to wisps."""
    segs = [("tail_0", (0, -36, 1.5), 0.34, 4.6, 7.2, 11, 9, 12),
            ("tail_1", (0, 10.5, 0), 0.2, 3.5, 5.4, 10, 8, 11),
            ("tail_2", (0, 9.5, 0), 0.22, 2.4, 3.8, 9, 7, 10),
            ("tail_3", (0, 8.5, 0), 0.28, 1.4, 0, 0, 6, 10)]
    parent = root
    for name, pivot, rx, r, core, core_h, n, length in segs:
        t = m.part(name, parent, pivot, (rx, 0, 0), anim=True)
        if core > 0:
            m.box(t, -core / 2, 0, -core / 2 * 0.8, core, core_h, core * 0.8, "starved_rag_dark")
        for k in range(n):
            a = TAU * k / n + 0.3 * (k % 2)
            st = m.part(f"{name}_rag{k}", t, (r * math.sin(a), 0.5, r * 0.8 * math.cos(a)), (0.14, a, 0))
            m.box(st, -1.9, 0, -0.6, 3.8 + (k % 2) * 0.6, length - (k % 3), 1.2, "starved_rag" if k % 2 else "starved_rag_dark")
        parent = t


def _hood(m, head):
    """A long cowl with nothing in it but a wide maw of ember light, lined with teeth."""
    m.box(head, -2.2, -3, -1.6, 4.4, 5, 4.2, "starved_skin")
    # a collar of rags where the cowl meets the shoulders
    m.box(head, -6, -2.5, -5, 12, 6, 11, "starved_rag_dark")
    m.box(head, -7, -15, -9, 14, 15, 15, "starved_hood")
    # the cowl rises to a peak over its brow and droops forward
    m.box(head, -5.2, -18.5, -9.6, 10.4, 4, 12, "starved_hood")
    m.box(head, -3, -21, -10.4, 6, 3, 8, "starved_hood")
    m.box(head, -1.4, -22.5, -11.2, 2.8, 2, 4, "starved_hood")
    m.box(head, -5, -13.5, 5.5, 10, 11, 4, "starved_hood")
    m.box(head, -3, -11.5, 9, 6, 8, 3, "starved_hood")
    m.box(head, -7.6, -16.2, -10.4, 15.2, 3, 2.4, "starved_hood")
    for sx in (-1, 1):
        m.box(head, 6.0 if sx > 0 else -7.8, -14, -10.2, 1.8, 14, 4, "starved_hood")
    m.box(head, -4.8, -13.4, -9.4, 9.6, 12.4, 0.8, "starved_void")
    m.box(head, -4.4, -6.6, -9.8, 8.8, 1.4, 0.6, "starved_maw")
    for t in range(7):
        x = -3.9 + t * 1.25
        m.box(head, x, -7.3, -9.95, 0.55, 0.9, 0.5, "starved_bone")
        m.box(head, x + 0.6, -5.4, -9.95, 0.55, 0.9, 0.5, "starved_bone")


def _crown(m, head):
    """A crown of broken iron on its brow: a band snapped open, spikes jutting at odd angles, two of them broken off,
    and the snapped ends of chains swinging from it."""
    crown = m.part("crown", head, (0, -15.6, -1.2), anim=True)
    for i in range(14):
        if i in (4, 5):
            continue
        a = TAU * i / 14
        seg = m.part(f"band_{i}", crown, (7.5 * math.sin(a), 0.4 * (i % 3) - 0.4, 8.2 * math.cos(a)),
                     (0.08 * (i % 2), a, 0.05 * ((i % 3) - 1)))
        m.box(seg, -2.0, -1.2, -0.6, 4.0, 2.4, 1.2, "rust")
    spikes = [(0.0, 13, 0.25), (0.55, 9, 0.35), (-0.6, 11, 0.3), (1.35, 6, 0.5), (-1.5, 10, 0.45),
              (2.4, 8, 0.6), (-2.5, 5, 0.55), (3.05, 12, 0.7)]
    for k, (a, length, tilt) in enumerate(spikes):
        sp = m.part(f"spike_{k}", crown, (7.5 * math.sin(a), -0.8, 8.2 * math.cos(a)), (-tilt, a, 0.12 * ((k % 3) - 1)))
        m.box(sp, -0.9, -length * 0.62, -0.9, 1.8, length * 0.62, 1.8, "rust")
        if length >= 8:
            tip = m.part(f"spiketip_{k}", sp, (0, -length * 0.62, 0), (0.2 * ((k % 2) * 2 - 1), 0, 0.25 * ((k % 3) - 1)))
            m.box(tip, -0.5, -length * 0.38, -0.5, 1.0, length * 0.38, 1.0, "iron")
    for j, (a, n) in enumerate(((TAU * 3.6 / 14, 6), (TAU * 6.3 / 14, 4))):
        ch = m.part(f"crownchain_{j}", crown, (7.6 * math.sin(a), 0.8, 8.3 * math.cos(a)), hang(m, crown, 0.05, 0.05), group="chain")
        m.rope(f"crownlink_{j}", ch, [(0, 2.6 * k, 0) for k in range(n + 1)], 0.8, "rust", links=True)


def _arm(m, body, side, sx):
    """A long, starved arm reaching in towards its prey: bone under ash skin, rags at the shoulder, a rusted shackle at
    the wrist with its chain snapped, a hand of five long claws."""
    arm = m.part(f"arm_{side}", body, (sx * 10.4, -31.5, 0.4), rot_from(m, body, (sx * 0.3, 0.88, -0.36)), anim=True)
    m.box(arm, -1.8, 0, -1.8, 3.6, 24, 3.6, "starved_skin")
    m.box(arm, -2.4, -1.5, -2.4, 4.8, 5, 4.8, "starved_bone")
    m.box(arm, -2.3, 21.5, -2.3, 4.6, 3.6, 4.6, "starved_bone")
    for k in range(3):
        a = TAU * k / 3 + 0.5
        t = m.part(f"sleeve_{side}{k}", arm, (2.2 * math.cos(a), 2 + k, 2.2 * math.sin(a)),
                   hang(m, arm, 0.1 * math.cos(a), 0.1 * math.sin(a)), group="sleeve")
        m.box(t, -1.7, 0, -0.5, 3.4, 10 + (k * 5) % 6, 1.0, "starved_rag")
    fore = m.part(f"fore_{side}", arm, (0, 24, 0), (-0.32, 0, sx * 0.12), anim=True)
    m.box(fore, -1.5, 0, -1.5, 3, 22, 3, "starved_skin")
    m.box(fore, -2.5, 13.5, -2.5, 5, 4.5, 5, "rust")
    hand = m.part(f"hand_{side}", fore, (0, 22, 0), anim=True)
    m.box(hand, -2.7, 0, -2.0, 5.4, 5.2, 4.0, "starved_skin")
    for j, (x, spread, length) in enumerate([(-2.2, -0.2, 9), (-0.75, -0.07, 10), (0.75, 0.07, 10), (2.2, 0.2, 9)]):
        f = m.part(f"finger_{side}{j}", hand, (x * sx, 5.0, -0.5), (-0.35, 0, spread * sx))
        m.box(f, -0.65, 0, -0.65, 1.3, length, 1.3, "starved_skin")
        c = m.part(f"claw_{side}{j}", f, (0, length, 0), (-0.5, 0, 0))
        m.box(c, -0.6, 0, -0.6, 1.2, 7, 1.2, "starved_claw")
        tip = m.part(f"tip_{side}{j}", c, (0, 7, 0), (-0.35, 0, 0))
        m.box(tip, -0.45, 0, -0.45, 0.9, 4, 0.9, "starved_claw_tip")
    th = m.part(f"thumb_{side}", hand, (sx * -2.9, 3.2, -1.5), (-0.6, 0, sx * -0.7))
    m.box(th, -0.6, 0, -0.6, 1.2, 7, 1.2, "starved_skin")
    tc = m.part(f"thumbclaw_{side}", th, (0, 7, 0), (-0.5, 0, 0))
    m.box(tc, -0.5, 0, -0.5, 1.0, 6, 1.0, "starved_claw")
    tt = m.part(f"thumbtip_{side}", tc, (0, 6, 0), (-0.3, 0, 0))
    m.box(tt, -0.4, 0, -0.4, 0.8, 3, 0.8, "starved_claw_tip")
    ch = m.part(f"wristchain_{side}", fore, (sx * 2.5, 17.5, 0), hang(m, fore), group="chain")
    m.rope(f"wristlink_{side}", ch, [(0, 3.0 * k, 0) for k in range(6)], 0.9, "rust", links=True)


def _chains(m, body):
    """The binding that failed: a chain across its ribs from shoulder to hip and round its back, and a broken length
    sagging from its waist."""
    loop = [(8.8, -33, -3.4), (-6.6, -12, -4.4), (-7.8, -9, 0.5), (-6.4, -12, 5.4), (8.4, -31, 5.8), (9.6, -33, 1.0),
            (8.8, -33, -3.4)]
    pts = []
    for a, b in zip(loop, loop[1:]):
        n = max(2, int(math.dist(a, b) / 3.0))
        for k in range(n):
            pts.append(tuple(a[i] + (b[i] - a[i]) * k / n for i in range(3)))
    pts.append(loop[-1])
    m.rope("wrap_", body, pts, 0.9, "rust", links=True)
    hang_pts = catenary((-4.5, -4, -3.6), (4.0, -2, -3.6), 9, 7)
    m.rope("sag_", body, hang_pts, 0.85, "rust", links=True)
