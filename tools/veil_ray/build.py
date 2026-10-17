"""The Veil Ray: a great manta of the Seam. A flat body with a dorsal ridge and a horned head, three-jointed wings and a
long whip of a tail. The wings, the body's back and belly and the tail are thin fine plates (four pixels to a model
pixel) cut to a manta's swept outline: dark indigo above, veined and starred with light, pale beneath with a glowing
trailing edge (they are mostly seen from below). The part names and poses are the ones VeilRayModel animates; the wing
parts carry no boxes of their own, only their plates.

usage: python3 tools/veil_ray/build.py [preview-json-dir]
"""
import math
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "elders"))
import elderlib  # noqa: E402
from elderlib import Model, mix, noise, shade, _h  # noqa: E402

ROOT = os.path.join(os.path.dirname(__file__), "..", "..")
JAVA = os.path.join(ROOT, "src/client/java/com/extremedimensions/client/entity")
TEX = os.path.join(ROOT, "src/client/resources/assets/extremedimensions/textures/entity")

F = 0.25
SPAN = 30.0  # body edge to wing tip
# wing segments: (part suffix, x offset from the body edge, width, z from, z to, thickness)
SEGMENTS = [("wing", 0.0, 12.0, -9.0, 8.0, 1.4), ("wingtip", 12.0, 11.0, -7.0, 5.0, 1.0), ("wingend", 23.0, 7.0, -5.0, 2.0, 0.8)]


def front_edge(s):
    """The wing's leading edge at span position s (0 at the body, 1 at the tip), as z (front is -z)."""
    return -9.0 + 5.5 * s ** 1.6


def back_edge(s):
    """The trailing edge: a deep curve, the tip swept back to a point."""
    return 8.0 - 9.5 * s ** 0.75 + 2.2 * math.sin(s * math.pi) * (1 - s)


def build():
    m = Model("VeilRay", 512, 512, seed=7)
    R = m.part("root", None, (0, 18, 0))
    body = m.part("body", R, (0, 0, 0))
    m.box(body, -4.6, -1.6, -8.6, 9.2, 3.2, 17.2, "r_core")
    back = m.part("rback", body, (0, 0, 0), scale=F)
    m.box(back, -5.2, -2.0, -9.2, 10.4, 0.4, 18.4, "r_back")
    belly = m.part("rbelly", body, (0, 0, 0), scale=F)
    m.box(belly, -5.2, 1.6, -9.2, 10.4, 0.4, 18.4, "r_belly")
    ridge = m.part("rridge", body, (0, -2.0, 0), scale=F)
    m.box(ridge, -0.5, -1.0, -7.5, 1.0, 1.0, 15, "r_ridge")
    head = m.part("head", body, (0, 0, -9))
    m.box(head, -3.4, -1.0, -3.6, 6.8, 2.0, 3.6, "r_core")
    # the head steps down to a broad, flat snout with the mouth across its front
    hp = m.part("rhead", head, (0, 0, 0), scale=F)
    m.box(hp, -4.0, -1.4, -3.8, 8.0, 2.8, 3.8, "r_head")
    m.box(hp, -3.5, -1.1, -4.8, 7.0, 2.2, 1.0, "r_head")
    m.box(hp, -2.6, -0.8, -5.3, 5.2, 1.5, 0.5, "r_snout")
    for side, sx in (("l", 1), ("r", -1)):
        ey = m.part(f"reye_{side}", head, (4.05 * sx, -0.35, -2.4), (0, 0.5 * sx, 0), scale=F)
        m.box(ey, -0.15, -0.55, -0.7, 0.3, 1.1, 1.4, "r_eye")
        horn = m.part(f"horn_{side}", head, (2.8 * sx, 0, -4.6), (0.08, -0.35 * sx, 0))
        m.box(horn, -0.5, -0.5, -5.5, 1, 1, 5.5, "r_horn")
        tipn = m.part(f"rhorntip_{side}", horn, (0, 0, -5.5), (0.25, 0, 0), scale=F)
        m.box(tipn, -0.3, -0.3, -2.6, 0.6, 0.6, 2.6, "r_horn")
        parent = body
        for k, (name, x0, width, z0, z1, th) in enumerate(SEGMENTS):
            pivot = (5.2 * sx, 0, 0) if k == 0 else (SEGMENTS[k - 1][2] * sx, 0, 0)
            seg = m.part(f"{name}_{side}", parent, pivot)
            plate = m.part(f"rw{k}_{side}", seg, (0, 0, 0), scale=F)
            xa = 0 if sx > 0 else -width
            m.box(plate, xa, -th / 2, z0, width, th, z1 - z0, f"r_wing{k}_{side}")
            parent = seg
    t0 = m.part("tail0", body, (0, 0, 9))
    m.box(t0, -1.8, -0.9, 0, 3.6, 1.8, 8, "r_core")
    m.part("rtail0", t0, (0, 0, 0), scale=F)
    m.box("rtail0", -2.1, -1.1, 0, 4.2, 2.2, 8.2, "r_tail")
    t1 = m.part("tail1", t0, (0, 0, 8))
    m.part("rtail1", t1, (0, 0, 0), scale=F)
    m.box("rtail1", -1.1, -0.6, 0, 2.2, 1.2, 10.2, "r_tail")
    t2 = m.part("tail2", t1, (0, 0, 10))
    m.part("rtail2", t2, (0, 0, 0), scale=F)
    m.box("rtail2", -0.5, -0.5, 0, 1.0, 1.0, 12.5, "r_tailtip")
    fin = m.part("fin", body, (0, -2, 2))
    m.part("rfin", fin, (0, 0, 0), scale=F)
    m.box("rfin", -0.3, -3.4, -3.5, 0.6, 3.4, 7.5, "r_fin")
    return m


# ----------------------------------------------------------------------------- materials

TOP_LO, TOP_HI = (22, 16, 48), (70, 54, 122)
BELLY_LO, BELLY_HI = (132, 112, 186), (206, 192, 236)
LIGHT = (205, 160, 255)


def _plate_xz(ctx, x_from, width, z0, z1):
    """Where a pixel of a plate's top or bottom face lies, as (x across the plate from x_from, z): the faces run
    along x and from the back (z1) at the first row to the front (z0) at the last."""
    x = x_from + (ctx["px"] + 0.5) / ctx["fw"] * width
    z = z1 - (ctx["py"] + 0.5) / ctx["fh"] * (z1 - z0)
    return x, z


def _veins(out, z):
    """The dorsal veins fan out from the shoulder: the distance in model pixels from (out, z) to the nearest vein, the
    distance from the shoulder, and whether the point sits on one of the constellation's knots."""
    ox, oz = -4.0, -5.0
    r = math.hypot(out - ox, z - oz)
    theta = math.atan2(z - oz, out - ox)
    n = 7.0 / (math.pi / 2)
    a = theta * n + 0.35 * math.sin(r * 0.45)
    d = abs(a - round(a)) / n * r
    ring = abs(r / 4.2 - round(r / 4.2)) * 4.2
    return d, r, d < 0.32 and ring < 0.32 and r > 7


def _skin_top(ctx, out, z, s_pos, edge_px):
    gx, gy, sd = ctx["gx"], ctx["gy"], ctx["seed"]
    glow = None
    base = mix(TOP_LO, TOP_HI, 0.22 + 0.38 * noise(gx, gy, sd, 6.0) + 0.3 * s_pos)
    d, r, knot = _veins(out, z)
    width = 0.16 * (1.25 - 0.6 * s_pos)
    if knot:
        base, glow = (236, 220, 255), (248, 236, 255, 255)
    elif d < width and r > 5 and edge_px > 1.5:
        k = 1 - d / width
        base, glow = mix(base, LIGHT, 0.45 + 0.4 * k), LIGHT + (int(90 + 110 * k),)
    elif _h(gx, gy, sd + 9) > 0.993:
        base, glow = (220, 200, 255), (240, 225, 255, 230)
    # the pale shoulder chevrons of a manta, faint here
    fz = front_edge(s_pos)
    if 0.4 < z - fz < 3.4 - out * 0.35 and out < 9:
        base = mix(base, (128, 108, 196), 0.55)
    if edge_px < 1.5:
        base = mix(base, (150, 120, 220), 0.55)
    return base, glow


def _skin_bottom(ctx, out, z, s_pos, back_px):
    gx, gy, sd = ctx["gx"], ctx["gy"], ctx["seed"]
    glow = None
    base = mix(BELLY_LO, BELLY_HI, 0.35 + 0.4 * noise(gx, gy, sd + 2, 5.0) - 0.15 * s_pos)
    # the belly spots every ray is told apart by
    if noise(out * 4, z * 4, sd + 17, 6.0) > 0.77 and s_pos < 0.6:
        base = shade(base, 0.62)
    # a row of lights along the trailing edge, and the edge itself lit
    if 3.5 < back_px < 7.5:
        step = 2.4
        k = abs(out / step - round(out / step)) * step
        if k < 0.3 and out > 1.5:
            base, glow = (240, 225, 255), LIGHT + (235,)
    elif _h(gx, gy, sd + 11) > 0.995:
        base, glow = (240, 230, 255), (240, 220, 255, 170)
    return base, glow


def wing(k, side):
    name, x0, width, z0, z1, th = SEGMENTS[k]
    sgn = 1 if side == "l" else -1

    def paint(ctx):
        face = ctx["face"]
        if face not in ("top", "bottom"):
            return (0, 0, 0, 0), None
        x, z = _plate_xz(ctx, 0, width, z0, z1)
        # outward distance from the body edge (the right wing's plate runs from the tip inwards)
        out = x0 + (x if sgn > 0 else width - x)
        s_pos = out / SPAN
        fz, bz = front_edge(s_pos), back_edge(s_pos)
        if z < fz or z > bz:
            return (0, 0, 0, 0), None
        edge = min(z - fz, bz - z) * 4
        if face == "top":
            base, glow = _skin_top(ctx, out, z, s_pos, edge)
            if z - fz < 0.35:
                base = (120, 96, 180)
        else:
            base, glow = _skin_bottom(ctx, out, z, s_pos, (bz - z) * 4)
            if bz - z < 0.5:
                base, glow = (230, 210, 255), LIGHT + (220,)
            elif z - fz < 0.3:
                base, glow = (196, 176, 240), LIGHT + (110,)
        return base + (255,), glow
    return paint


def _body_half(z):
    """The half-width of the body plates: as wide as the wing roots along their length, narrowing to the tail."""
    if z < 8.0:
        return 5.2
    return 5.2 + (2.1 - 5.2) * min(1.0, (z - 8.0) / 1.2)


def body_plate(top):
    def paint(ctx):
        if ctx["face"] != ("top" if top else "bottom"):
            return (0, 0, 0, 0), None
        gx, gy, sd = ctx["gx"], ctx["gy"], ctx["seed"]
        x, z = _plate_xz(ctx, -5.2, 10.4, -9.2, 9.2)
        if abs(x) > _body_half(z):
            return (0, 0, 0, 0), None
        glow = None
        if top:
            base = mix(TOP_LO, TOP_HI, 0.2 + 0.35 * noise(gx, gy, sd, 6.0) + 0.12 * abs(x) / 5.2)
            # the spine: a dark groove with knots of light down it, and the shoulder chevrons begun on the wings
            if abs(x) < 0.45:
                base = shade(base, 0.6)
                if abs(z / 2.2 - round(z / 2.2)) * 2.2 < 0.3 and z < 7.5:
                    base, glow = (220, 196, 255), LIGHT + (230,)
            elif 2.2 < abs(x) and -8.4 < z < -8.4 + (abs(x) - 2.2) * 1.3:
                base = mix(base, (128, 108, 196), 0.55)
            elif _h(gx, gy, sd + 9) > 0.993:
                base, glow = (220, 200, 255), (240, 225, 255, 230)
        else:
            base = mix(BELLY_LO, BELLY_HI, 0.45 + 0.35 * noise(gx, gy, sd + 2, 5.0))
            # five gill slits on each side, curving with the body
            ax = abs(x)
            if 1.6 < ax < 3.4 and -6.6 < z < -1.0:
                slit = (z + 6.6 - (ax - 1.6) * 0.5) / 1.15
                if abs(slit - round(slit)) < 0.11 and 0 < round(slit) <= 5:
                    base = (70, 56, 110)
            if 0.5 < z < 6 and abs(x) < 0.25:
                base = shade(base, 0.85)
        return base + (255,), glow
    return paint


def m_core(ctx):
    return mix(TOP_LO, TOP_HI, 0.3 + 0.3 * noise(ctx["gx"], ctx["gy"], ctx["seed"], 2)) + (255,), None


def m_ridge(ctx):
    base = mix(TOP_LO, TOP_HI, 0.5 + 0.3 * noise(ctx["gx"], ctx["gy"], ctx["seed"], 2))
    glow = None
    if ctx["face"] == "top" and ctx["px"] % 2 == 0 and (ctx["py"] // 3) % 2 == 0:
        base, glow = (190, 150, 250), LIGHT + (220,)
    return base + (255,), glow


def m_head(ctx, snout=False):
    gx, gy, sd = ctx["gx"], ctx["gy"], ctx["seed"]
    face = ctx["face"]
    if face == "bottom":
        return mix(BELLY_LO, BELLY_HI, 0.5 + 0.3 * noise(gx, gy, sd, 3)) + (255,), None
    base = mix(TOP_LO, TOP_HI, 0.3 + 0.4 * noise(gx, gy, sd, 3))
    glow = None
    v = (ctx["py"] + 0.5) / ctx["fh"]
    if face == "top" and not snout:
        # a dark brow between the eyes and a seam of light running back into the spine
        u = (ctx["px"] + 0.5) / ctx["fw"] * 2 - 1
        if abs(u) < 0.06:
            base = shade(base, 0.6)
            if ctx["py"] % 4 == 1:
                base, glow = (220, 196, 255), LIGHT + (210,)
        elif abs(u) > 0.7:
            base = mix(base, (128, 108, 196), 0.35)
    elif face != "top":
        # the lower half of every side is the paler belly
        if v > 0.55:
            base = mix(base, mix(BELLY_LO, BELLY_HI, 0.4), 0.7)
        if snout and face == "north" and abs(v - 0.5) < 0.12:
            # a wide mouth along the front, faintly lit inside
            u = (ctx["px"] + 0.5) / ctx["fw"] * 2 - 1
            base = (14, 8, 24)
            if abs(u) < 0.8 and ctx["px"] % 3 == 1:
                base, glow = (90, 60, 140), LIGHT + (90,)
    return base + (255,), glow


def m_eye(ctx):
    if ctx["face"] not in ("east", "west"):
        return mix(TOP_LO, TOP_HI, 0.4) + (255,), None
    u = (ctx["px"] + 0.5) / ctx["fw"] * 2 - 1
    v = (ctx["py"] + 0.5) / ctx["fh"] * 2 - 1
    d = math.hypot(u, v)
    if d > 1:
        return (0, 0, 0, 0), None
    c = mix(LIGHT, (255, 250, 255), max(0.0, 1 - d * 1.5))
    return c + (255,), c + (int(160 + 95 * (1 - d)),)


def m_horn(ctx):
    k = noise(ctx["gx"], ctx["gy"], ctx["seed"], 1.5)
    base = mix((80, 66, 130), (170, 150, 220), k)
    glow = (200, 160, 255, 180) if _h(ctx["gx"], ctx["gy"], ctx["seed"] + 3) > 0.85 else None
    return base + (255,), glow


def m_tail(ctx, tip=False):
    gx, gy, sd = ctx["gx"], ctx["gy"], ctx["seed"]
    top = ctx["face"] != "bottom"
    base = mix(TOP_LO, TOP_HI, 0.4 + 0.3 * noise(gx, gy, sd, 3)) if top else mix(BELLY_LO, BELLY_HI, 0.5)
    glow = None
    k = ctx["py"] / max(1, ctx["fh"] - 1)
    if top and (ctx["py"] // 4) % 3 == 0 and abs(ctx["px"] - ctx["fw"] / 2) < 1.5:
        base, glow = (180, 140, 245), LIGHT + (200,)
    if tip and ctx["face"] in ("top", "bottom") and ctx["py"] < ctx["fh"] * 0.25:
        base, glow = (235, 215, 255), (245, 230, 255, 255)
    return base + (255,), glow


def m_fin(ctx):
    if ctx["face"] not in ("east", "west"):
        return mix(TOP_LO, TOP_HI, 0.5) + (255,), None
    # u runs from the fin's front to its back on both sides, v from its tip down to the body
    u = (ctx["px"] + 0.5) / ctx["fw"]
    if ctx["face"] == "west":
        u = 1 - u
    v = (ctx["py"] + 0.5) / ctx["fh"]
    # a swept fin: tall at the front, raking back
    edge = 1 - (1 - u) ** 0.6
    if v < edge:
        return (0, 0, 0, 0), None
    base = mix(TOP_LO, TOP_HI, 0.35 + 0.3 * noise(ctx["gx"], ctx["gy"], ctx["seed"], 2) + 0.2 * v)
    glow = None
    if (v - edge) * ctx["fh"] < 1.2:
        base, glow = (200, 170, 255), LIGHT + (210,)
    else:
        # thin rays of light raking back from the fin's root
        ray = (u * 5 + (1 - v) * 2.5) % 1
        if ray < 0.12 and v < 0.9:
            base, glow = mix(base, LIGHT, 0.5), LIGHT + (120,)
    return base + (255,), glow


MATERIALS = {
    "r_core": m_core,
    "r_back": body_plate(True),
    "r_belly": body_plate(False),
    "r_ridge": m_ridge,
    "r_head": m_head,
    "r_snout": lambda c: m_head(c, snout=True),
    "r_eye": m_eye,
    "r_horn": m_horn,
    "r_tail": lambda c: m_tail(c),
    "r_tailtip": lambda c: m_tail(c, tip=True),
    "r_fin": m_fin,
}
for _k in range(3):
    for _s in ("l", "r"):
        MATERIALS[f"r_wing{_k}_{_s}"] = wing(_k, _s)


if __name__ == "__main__":
    preview = sys.argv[1] if len(sys.argv) > 1 else None
    elderlib.MATS.update(MATERIALS)
    model = build()
    uv, used = elderlib.pack(model)
    img, glow = elderlib.paint(model, uv)
    img.save(os.path.join(TEX, "veil_ray.png"))
    glow.save(os.path.join(TEX, "veil_ray_glow.png"))
    elderlib.write_java(model, uv, "com.extremedimensions.client.entity", os.path.join(JAVA, "VeilRayGeometry.java"))
    if preview:
        elderlib.export_preview(model, uv, os.path.join(preview, "veil_ray.json"))
    boxes = sum(len(p["boxes"]) for p in model.parts)
    print(f"veil ray: {len(model.parts)} parts, {boxes} boxes, texture rows used {used}/{model.th}")
