"""The Last Weaver: a towering, crowned figure in a robe that rots into tatters, arms spread wide. Glowing threads
run from its long fingers, sagging into loops and down to hanging lanterns; a cross of light burns in its mask, and a
great halo turns behind its head."""
import math

from elderlib import Model, catenary


def build():
    m = Model("Weaver", 1024, 1024, seed=97)
    R = m.part("root", None, (0, 24, 0), anim=True)
    body = m.part("body", R, (0, 0, 0), anim=True)

    # ---- the robe: an inner column, a skirt of long tatters and a cloak from the shoulders
    m.box(body, -9, -120, -7, 18, 60, 14, "cloth")
    m.box(body, -11, -60, -9, 22, 58, 18, "cloth")
    for i in range(28):
        a = 2 * math.pi * i / 28 + 0.05 * (i % 3)
        length = 118 + (i * 11) % 14
        s = m.part(f"skirt_{i}", body, (10 * math.sin(a), -118, 8.5 * math.cos(a)), (0.11 + 0.02 * (i % 4), a, 0), group="robe")
        m.box(s, -3, 0, -1, 6 + (i % 2), length, 2, "cloth_ragged")
    for i in range(16):
        a = math.pi * 0.5 + math.pi * i / 15
        s = m.part(f"cloak_{i}", body, (13 * math.sin(a), -146, 9 * math.cos(a)), (0.09, a, 0), group="robe")
        m.box(s, -4, 0, -1, 8, 140 + (i * 7) % 16, 2, "cloth_ragged")

    # ---- the torso
    torso = m.part("torso", body, (0, -120, 0), anim=True)
    m.box(torso, -11, -28, -6.5, 22, 28, 13, "cloth")
    m.box(torso, -9, -26, -7.4, 18, 22, 1, "skin")
    for k in range(5):
        m.box(torso, -8, -23 + k * 4, -8, 16, 1.2, 1, "cloth")
    for i in range(16):
        a = 2 * math.pi * i / 16
        sm = m.part(f"wmantle_{i}", torso, (13 * math.sin(a), -27, 8 * math.cos(a)), (0.95, a, 0), group="mantle")
        m.box(sm, -4, 0, -0.8, 8, 22 + (i * 5) % 8, 1.6, "cloth_ragged")
    for i in range(10):
        a = -math.pi * 0.9 + 1.8 * math.pi * i / 9
        c = m.part(f"collar_{i}", torso, (9 * math.sin(a), -28, 6 * math.cos(a)), (-0.5, a, 0))
        m.box(c, -2, -9, -0.8, 4, 9, 1.6, "cloth")

    # ---- the head: a mask with a cross of light, a crown of thorns, a halo
    head = m.part("head", torso, (0, -28, 0), anim=True)
    m.box(head, -8.5, -22, -5, 17, 22, 13, "cloth")
    m.box(head, -9.5, -24, -7, 19, 5, 6, "cloth")
    m.box(head, -6.5, -18, -7.4, 13, 17, 2, "ash_mask")
    m.box(head, -0.9, -16.5, -8.2, 1.8, 13, 0.6, "eye")
    m.box(head, -5.2, -11.5, -8.2, 10.4, 1.8, 0.6, "eye")
    m.box(head, -1.8, -12.4, -8.5, 3.6, 3.6, 0.6, "glowcore")
    for sx in (1, -1):
        m.box(head, sx * 3.4 - 1.2, -15.5, -8.0, 2.4, 2.4, 0.5, "socket")
        m.box(head, sx * 3.4 - 1.2, -8.6, -8.0, 2.4, 2.4, 0.5, "socket")
    for k in range(11):
        a = -1.25 + 2.5 * k / 10
        lens = [14 - abs(k - 5) * 1.2, 11 - abs(k - 5), 8 - abs(k - 5) * 0.6]
        m.chain_of(f"wcrown{k}_", head, (8 * math.sin(a), -23, 1 + 5 * (1 - math.cos(a))),
                   [(-0.25, 0, a * 0.75), (0.1, 0, a * 0.18), (0.12, 0, a * 0.15)], lens, [2.8, 2.0, 1.2], ["bark", "bark", "bone_dark"])
    halo = m.part("halo", head, (0, -14, 14), anim=True)
    m.ring("halo_a", halo, (0, 0, 0), 46, 40, 7.6, 3.0, "halo")
    m.ring("halo_b", halo, (0, 0, 0), 38, 36, 6.8, 1.4, "halo")
    m.ring("halo_c", halo, (0, 0, 0), 55, 20, 2.0, 2.0, "halo", phase=0.08)
    for k in range(12):
        a = 2 * math.pi * k / 12
        sp = m.part(f"halospoke_{k}", halo, (42 * math.cos(a), 42 * math.sin(a), 0.5), (0, 0, a + math.pi / 2))
        m.box(sp, -0.7, -5, -0.7, 1.4, 10, 1.4, "halo")

    # ---- arms spread wide, long fingers, threads and lanterns
    lantern_no = 0
    for side, sx in (("l", 1), ("r", -1)):
        arm = m.part(f"arm_{side}", torso, (12 * sx, -25, 0), (0, 0, -1.72 * sx), anim=True)
        m.box(arm, -4.5, -1, -4.5, 9, 36, 9, "cloth")
        for j in range(6):
            m.strand(f"sleeve_{side}{j}", arm, (-3 + (j % 3) * 3, 6 + j * 5, 3.2 * (1 if j % 2 else -1)), 18 + (j * 7) % 16, 2.4, "cloth_ragged")
        fo = m.part(f"forearm_{side}", arm, (0, 35, 0), (0, 0, 0.22 * sx), anim=True)
        m.box(fo, -3, 0, -3, 6, 32, 6, "skin")
        for j in range(3):
            m.box(fo, -3.4, 6 + j * 9, -3.4, 6.8, 1.6, 6.8, "thread")
        ha = m.part(f"hand_{side}", fo, (0, 32, 0), (0, 0, 0.25 * sx), anim=True)
        m.box(ha, -4.5, 0, -2, 9, 9, 4, "skin")
        tips = []
        for f in range(5):
            spread = (-0.75 + 0.36 * f) * sx
            fr = m.part(f"wf_{side}{f}", ha, (sx * (-3.6 + 1.8 * f), 8.5, 0), (0.12, 0, spread))
            m.box(fr, -0.9, 0, -0.9, 1.8, 11, 1.8, "skin")
            fm = m.part(f"wfm_{side}{f}", fr, (0, 11, 0), (0.18, 0, 0))
            m.box(fm, -0.75, 0, -0.75, 1.5, 10, 1.5, "skin")
            ft = m.part(f"wft_{side}{f}", fm, (0, 10, 0), (0.22, 0, 0))
            m.box(ft, -0.6, 0, -0.6, 1.2, 8, 1.2, "bone_dark")
            tips.append(m.xf(ft).apply([0, 8, 0]))
        # threads, built in root space then hung from the hand
        def thread(prefix, pts):
            local = [m.to_local(ha, p) for p in pts]
            m.rope(prefix, ha, local, 0.9, "thread")

        for k, (f, drop, sag, inward, size) in enumerate([(0, 46, 6, 2, 1.7), (2, 74, 12, 14, 1.9), (4, 36, 4, -4, 1.4)]):
            p0 = tips[f]
            p1 = [p0[0] - sx * inward, p0[1] + drop, p0[2] - 4]
            thread(f"th_{side}{k}_", catenary(p0, p1, sag, 10))
            lantern_no += 1
            m.lantern(f"wlan_{side}{k}", ha, m.to_local(ha, p1), size=size, links=1, frame="iron", glass="glass")
        thread(f"thl_{side}_", catenary(tips[1], tips[3], 16, 8))
        thread(f"thc_{side}_", catenary(tips[1], [sx * 6, -104, -10], 34, 16))
        thread(f"thd_{side}_", catenary(tips[3], [sx * 9, -70, -11], 44, 16))
    return m
