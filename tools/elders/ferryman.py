"""The Ferryman: a hooded giant in a long black boat. Chains hang across his robe and from his arms; his right hand
holds a pole taller than he is, two lanterns swinging from its crossbar; his left is raised beside his hood, a violet vortex
turning before the palm and a great lantern hanging from the wrist. A skull is carved at the prow."""
import math

from elderlib import Model, catenary


def build():
    m = Model("Ferryman", 1024, 1024, seed=73)
    R = m.part("root", None, (0, 24, 0), anim=True)

    # ---- the boat
    boat = m.part("boat", R, (0, 0, 0), anim=True)
    m.box(boat, -3, -4, -72, 6, 4, 144, "boatwood")
    m.box(boat, -16, -7, -62, 32, 3, 124, "boatwood")
    for side, sx in (("l", 1), ("r", -1)):
        for k in range(4):
            half = 63 - k * 1.5
            p = m.part(f"strake_{side}{k}", boat, (sx * (15.5 + k * 1.8), -6 - k * 4.4, 0), (0, 0, sx * (0.26 + 0.07 * k)))
            m.box(p, -1, -4.8, -half, 2, 5, 2 * half, "boatwood")
        g = m.part(f"gunwale_{side}", boat, (sx * 24.5, -24.5, 0), (0, 0, sx * 0.45))
        m.box(g, -1.6, -1.6, -63, 3.2, 3.2, 126, "boatwood")
        for k in range(7):
            z = -54 + k * 18
            post = m.part(f"thole_{side}{k}", g, (0, -1.5, z), (0, 0, -sx * 0.45))
            m.box(post, -0.9, -5, -0.9, 1.8, 5, 1.8, "iron")
        for c in range(2):
            z0, z1 = -50 + c * 52, -16 + c * 52
            pts = catenary([sx * 25.5, -26, z0], [sx * 25.5, -26, z1], 9, 12)
            m.rope(f"bchain_{side}{c}_", boat, pts, 0.9, "rust", links=True)
    for z0, sgn in ((-62, -1), (62, 1)):
        tag = "bow" if sgn < 0 else "stern"
        m.box(boat, -13, -22, z0 - (8 if sgn < 0 else 0), 26, 20, 8, "boatwood")
        m.box(boat, -9, -24, z0 + sgn * 8 - (8 if sgn < 0 else 0), 18, 22, 8, "boatwood")
        m.box(boat, -5, -26, z0 + sgn * 16 - (6 if sgn < 0 else 0), 10, 24, 6, "boatwood")
        if sgn > 0:
            m.box(boat, -2.5, -27, z0 + 22, 5, 25, 4, "boatwood")
    for k in range(6):
        m.box(boat, -16, -10, -45 + k * 18, 32, 2, 3, "boatwood")
    m.box(boat, -16, -14, -32, 32, 2.5, 9, "boatwood")
    m.box(boat, -16, -14, 30, 32, 2.5, 9, "boatwood")
    # the prow rises in a curl, banded with iron, a lantern hung from its hook
    prow = m.chain_of("prow", boat, (0, -26, -82), [(0.45, 0, 0), (-0.35, 0, 0), (-0.6, 0, 0), (-0.8, 0, 0)],
                      [20, 16, 12, 8], [7, 6, 5, 4], "boatwood")
    m.box(prow[0], -4, -6, -4, 8, 2, 8, "iron")
    m.box(prow[1], -3.5, -10, -3.5, 7, 2, 7, "iron")
    hook = m.part("prowhook", prow[2], (0, -10, 0), (1.2, 0, 0))
    m.box(hook, -1, -12, -1, 2, 12, 2, "iron")
    m.lantern("lan_prow", hook, (0, -12, 0), size=1.5, links=6)
    skull = m.part("skull", boat, (0, -12, -86))
    m.box(skull, -6.5, -12, -7, 13, 12, 11, "skull")
    m.box(skull, -7, -10, -7.5, 14, 2.5, 3, "skull")
    m.box(skull, -6, -5, -7.6, 12, 8, 1, "skull")
    for sx in (1, -1):
        m.box(skull, sx * 4.6 - 1.6, -7.8, -8.3, 3.2, 3.6, 0.8, "socket")
        se = m.part(f"skeye_{'l' if sx > 0 else 'r'}", skull, (sx * 4.6, -6.0, -8.45), scale=0.25)
        m.box(se, -0.9, -0.9, -0.2, 1.8, 1.8, 0.3, "soul_eye")
        m.box(skull, sx * 6.2 - 1.2, -4.5, -7.9, 2.4, 2.4, 1.2, "skull")
    m.box(skull, -1.2, -3.4, -8.2, 2.4, 2.6, 0.8, "socket")
    m.box(skull, -5.5, -0.5, -6.5, 11, 4.5, 8, "skull")
    m.box(skull, -4.5, 3, -6.9, 9, 1.4, 0.8, "bone")
    for k in range(6):
        m.box(skull, -4.2 + k * 1.6, 1.6, -7.0, 0.5, 1.6, 0.5, "socket")
    for side, sx in (("l", 1), ("r", -1)):
        m.chain_of(f"skhorn_{side}", skull, (6 * sx, -9, -2), [(-0.9, 0, -1.2 * sx), (-0.5, 0, -0.3 * sx), (-0.5, 0, 0)],
                   [7, 6, 5], [3, 2.2, 1.4], ["bone", "bone", "bone_dark"])
    stern = m.part("sternpost", boat, (0, -26, 82), (-0.3, 0, 0))
    m.box(stern, -2.5, -30, -2.5, 5, 30, 5, "boatwood")
    shook = m.part("sternhook", stern, (0, -30, 0), (-1.1, 0, 0))
    m.box(shook, -1, -12, -1, 2, 12, 2, "iron")
    m.lantern("lan_stern", shook, (0, -12, 0), size=1.3, links=5)

    # ---- the figure in his robe
    fig = m.part("figure", R, (0, -14, 8), anim=True)
    m.box(fig, -9, -62, -8, 18, 62, 16, "robe")
    for i in range(22):
        a = 2 * math.pi * i / 22 + 0.04 * (i % 3)
        s = m.part(f"robe_{i}", fig, (9.5 * math.sin(a), -61, 9 * math.cos(a)), (0.16 + 0.02 * (i % 4), a, 0), group="robe")
        m.box(s, -3, 0, -1, 6 + (i % 2), 60 + (i * 5) % 6, 2, "robe_ragged")
    torso = m.part("torso", fig, (0, -62, 0), anim=True)
    m.box(torso, -12, -27, -7, 24, 27, 14, "robe")
    m.box(torso, -11, -27, -7.6, 22, 6, 1, "robe")
    for i in range(14):
        a = 2 * math.pi * i / 14
        s = m.part(f"mantle_{i}", torso, (11 * math.sin(a), -25, 8 * math.cos(a)), (0.7, a, 0), group="mantle")
        m.box(s, -3.5, 0, -0.8, 7, 18 + (i * 3) % 5, 1.6, "robe_ragged")
    m.rope("chestchain_", torso, catenary([11, -25, -8.2], [-10, -2, -8.2], 3, 18), 1.0, "rust", links=True)
    m.rope("chestchainb_", torso, catenary([-11, -25, -8.0], [9, -6, -8.0], 2, 14), 0.9, "rust", links=True)
    waist = [[13 * math.sin(2 * math.pi * k / 24), -1, 9 * math.cos(2 * math.pi * k / 24) - 0.5] for k in range(25)]
    m.rope("waistchain_", torso, waist, 0.9, "rust", links=True)
    hood = m.part("hood", torso, (0, -27, 0), anim=True)
    m.box(hood, -10, -23, -4, 20, 23, 14, "robe")
    m.box(hood, -10.5, -25, -9, 21, 4.5, 7, "robe")
    m.box(hood, -10.5, -22, -9, 3.6, 21, 6, "robe")
    m.box(hood, 6.9, -22, -9, 3.6, 21, 6, "robe")
    m.box(hood, -7, -20.5, -6, 14, 19, 1, "socket")
    # deep in the hood, a skull in fine pixels (four to a model pixel): bone darkening into the shadow, soul light
    # in its sockets, and the fare he was paid held in its teeth
    F = 0.25
    face = m.part("hskull", hood, (0, -11.5, -6.2), scale=F)
    m.box(face, -5.2, -7.5, -0.4, 10.4, 14, 0.5, "skull_face")
    jaw = m.part("hjaw", hood, (0, -4.9, -6.3), (0.18, 0, 0), scale=F)
    m.box(jaw, -2.9, -0.5, -0.25, 5.8, 1.3, 0.45, "skull")
    m.box(jaw, -2.0, 0.8, -0.25, 4.0, 0.7, 0.45, "skull")
    for side, sx in (("l", 1), ("r", -1)):
        e = m.part(f"heye_{side}", hood, (2.2 * sx, -11.0, -6.5), scale=F)
        m.box(e, -0.9, -0.9, -0.2, 1.8, 1.8, 0.3, "soul_eye")
    coin = m.part("hcoin", hood, (0.9, -7.6, -6.75), (0, 0, 0.2), scale=F)
    m.box(coin, -1.0, -1.0, -0.15, 2.0, 2.0, 0.3, "coin")
    # the frayed edge of the hood round the face
    for k in range(9):
        x = -6.4 + 1.6 * k
        tt = m.part(f"hfray_{k}", hood, (x, -20.4, -6.6), (0.1, 0, 0.08 * (k - 4)), scale=F)
        m.box(tt, -0.45, 0, -0.2, 0.9, 1.6 + (k * 7) % 5 * 0.5, 0.4, "robe")
    for k, (x, z) in enumerate([(-10, -6), (-10, 0), (-10, 6), (10, -6), (10, 0), (10, 6)]):
        d = m.part(f"hdrape{k}", hood, (x * 1.02, -6, z), (0, 0, -0.25 if x > 0 else 0.25), group="mantle")
        m.box(d, -1, 0, -3, 2, 16 + (k * 3) % 5, 6, "robe_ragged")
    peak = m.part("hoodpeak", hood, (0, -24, 4), (-0.6, 0, 0))
    m.box(peak, -5, -10, -5, 10, 10, 10, "robe")
    m.box(peak, -3, -15, -3, 6, 5, 6, "robe")
    for k in range(9):
        a = -1.2 + 2.4 * k / 8
        m.chain_of(f"hcrown{k}_", hood, (9 * math.sin(a), -24, -3 + 6 * (1 - math.cos(a))), [(-0.2, 0, a * 0.5), (0.08, 0, a * 0.2)],
                   [6 + (4 - abs(k - 4)), 5], [2.2, 1.2], ["iron", "iron"])

    # ---- the right arm and the pole
    arm_r = m.part("arm_r", torso, (-13, -23, 0), (-0.55, 0, 0.3), anim=True)
    m.box(arm_r, -4.5, -2, -4.5, 9, 24, 9, "robe")
    for j in range(4):
        m.strand(f"sleeve_r{j}", arm_r, (-4 + j * 2.7, 20, -1 + (j % 2) * 3), 10 + j * 3, 2.2, "robe_ragged")
    m.rope("armchain_r_", arm_r, [[4.6 * math.cos(t * 0.9), 2 + t * 1.5, 4.6 * math.sin(t * 0.9)] for t in range(13)], 0.8, "rust", links=True)
    fo_r = m.part("forearm_r", arm_r, (0, 21, 0), (-1.1, 0, -0.2), anim=True)
    m.box(fo_r, -3.8, 0, -3.8, 7.6, 18, 7.6, "robe")
    ha_r = m.part("hand_r", fo_r, (0, 18, 0), (0, 0, 0), anim=True)
    m.box(ha_r, -3.8, 0, -3.8, 7.6, 7.5, 7.6, "iron")
    pole = m.part("pole", ha_r, (0, 4, -1), m.hang_rot(ha_r), anim=True)
    m.box(pole, -1.9, -100, -1.9, 3.8, 156, 3.8, "boatwood")
    for k in range(8):
        m.box(pole, -2.3, -92 + k * 18, -2.3, 4.6, 2, 4.6, "iron")
    m.box(pole, -1.2, -112, -1.2, 2.4, 12, 2.4, "iron")
    m.box(pole, -2.6, -106, -2.6, 5.2, 3, 5.2, "iron")
    cross = m.part("crossbar", pole, (0, -94, 0))
    m.box(cross, -24, -1.5, -1.5, 48, 3, 3, "iron")
    for sx in (1, -1):
        m.box(cross, sx * 24 - 1.5, -6, -1.5, 3, 6, 3, "iron")
    m.lantern("lan_pole_l", cross, (22, 1.5, 0), size=1.6, links=9)
    m.lantern("lan_pole_r", cross, (-22, 1.5, 0), size=1.6, links=6)
    helix = [[2.9 * math.cos(t * 0.7), -80 + t * 2.4, 2.9 * math.sin(t * 0.7)] for t in range(24)]
    m.rope("polechain_", pole, helix, 0.8, "rust", links=True)

    # ---- the left arm, raised, the vortex in its palm
    arm_l = m.part("arm_l", torso, (13, -23, 0), (-0.3, 0, -1.35), anim=True)
    m.box(arm_l, -4.5, -2, -4.5, 9, 24, 9, "robe")
    for j in range(4):
        m.strand(f"sleeve_l{j}", arm_l, (-4 + j * 2.7, 18, 2), 12 + j * 4, 2.2, "robe_ragged")
    fo_l = m.part("forearm_l", arm_l, (0, 21, 0), (-0.3, 0, -1.7), anim=True)
    m.box(fo_l, -3.8, 0, -3.8, 7.6, 18, 7.6, "robe")
    m.rope("armchain_l_", fo_l, [[4.2 * math.cos(t * 0.9), 1 + t * 1.3, 4.2 * math.sin(t * 0.9)] for t in range(12)], 0.8, "rust", links=True)
    ha_l = m.part("hand_l", fo_l, (0, 18, 0), (-0.15, 0, 0), anim=True)
    m.box(ha_l, -4.5, 0, -2, 9, 8, 4, "iron")
    for f in range(4):
        fr = m.part(f"lfing{f}", ha_l, (-3.4 + f * 2.3, 7.5, 0), (0.15, 0, 0.35 - 0.23 * f))
        m.box(fr, -0.8, 0, -0.8, 1.6, 6, 1.6, "iron")
        ft = m.part(f"lfingt{f}", fr, (0, 6, 0), (0.25, 0, 0))
        m.box(ft, -0.65, 0, -0.65, 1.3, 5.5, 1.3, "iron")
    th = m.part("lthumb", ha_l, (4.5, 3, 0), (0.2, 0, -0.9))
    m.box(th, -0.9, 0, -0.9, 1.8, 6, 1.8, "iron")
    palm = [0, 12, -6]
    vort = m.part("vortex", ha_l, palm, m.world_rot(ha_l), anim=True)
    m.ring("vring_a", vort, (0, 0, 0), 9, 16, 3.4, 1.6, "vortex")
    vb = m.part("vortex_b", vort, (0, 0, 0), (0.5, 0, 0.3), anim=True)
    m.ring("vring_b", vb, (0, 0, 0), 13, 20, 3.8, 1.3, "vortex")
    vc = m.part("vortex_c", vort, (0, 0, 0), (-0.4, 0.6, 0), anim=True)
    m.ring("vring_c", vc, (0, 0, 0), 6, 12, 2.8, 1.2, "vortex")
    m.box(vort, -1.6, -1.6, -1.6, 3.2, 3.2, 3.2, "glowcore")
    wrist = m.part("wristchain", fo_l, (0, 16, 3), m.hang_rot(fo_l))
    m.rope("wchain_", wrist, [[0, k * 2.2, 0] for k in range(14)], 0.9, "rust", links=True)
    m.lantern("lan_hand", wrist, (0, 28, 0), size=2.0, links=2)
    return m
