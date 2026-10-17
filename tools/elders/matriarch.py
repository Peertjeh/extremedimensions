"""The Matriarch: a colossal, crowned mother of the hollows. A trunk of bark in a double robe of strips, rooted to
the ground; a chest split by violet light; a great carved wooden mask with slanted eyes under a crown of thorns;
six branches arching from her back like antlers, hung with moss and lanterns; and two long arms raised to reach,
ending in hooked talons."""
import math

from elderlib import Model


def build():
    m = Model("Matriarch", 512, 512, seed=41)
    R = m.part("root", None, (0, 24, 0), anim=True)
    rnd = m.rnd

    # ---- the trunk, robed in bark and rooted
    trunk = m.part("trunk", R, (0, 0, 0))
    m.box(trunk, -9, -60, -8, 18, 20, 16, "bark")
    m.box(trunk, -11, -42, -9, 22, 22, 18, "bark_veins")
    m.box(trunk, -13, -21, -11, 26, 21, 22, "bark")
    n = 26
    for i in range(n):
        a = 2 * math.pi * i / n + 0.05 * (i % 3)
        length = 60 + (i * 7) % 9
        w = 5 + (i % 3)
        tilt = 0.22 + 0.025 * ((i * 5) % 4)
        s = m.part(f"strip_{i}", trunk, (10.5 * math.sin(a), -60, 10.5 * math.cos(a)), (tilt, a, 0), group="robe")
        m.box(s, -w / 2, 0, -1, w, length, 2, "bark_veins" if i % 3 == 0 else "bark")
        if i % 2 == 0:
            m.box(s, -w / 2 + 0.5, 8 + (i % 5) * 6, -1.7, w - 1, 10, 0.8, "bark")
    # an outer, shorter layer of bark scales over the hips
    for i in range(16):
        a = 2 * math.pi * (i + 0.5) / 16
        s = m.part(f"scale_{i}", trunk, (12.5 * math.sin(a), -58, 12.5 * math.cos(a)), (0.42, a, 0))
        m.box(s, -3.5, 0, -1.2, 7, 18 + (i * 5) % 7, 2.4, "bark")
    for i in range(9):
        a = 2 * math.pi * (i + 0.3) / 9
        m.chain_of(f"rootl_{i}_", trunk, (16 * math.sin(a), -5, 16 * math.cos(a)),
                   [(math.pi / 2 - 0.32, a, 0), (0.18, 0, 0.12), (0.2, 0, -0.12), (0.18, 0, 0.1)], [16, 14, 11, 8], [8, 6, 4, 2.4], "bark", up=False)

    # ---- the chest, split by light
    chest = m.part("chest", R, (0, -60, 0), anim=True)
    m.box(chest, -15, -36, -9, 30, 36, 17, "bark_veins")
    # her breast in fine pixels: ribs of root round a seed of light (it opens in her ultimate)
    heart = m.part("mheart", chest, (0, -21, -9.05), scale=0.25)
    m.box(heart, -8.5, -10.5, -0.5, 17, 21, 0.6, "mother_heart")
    m.box(chest, -12, -11, -10.5, 24, 11, 2, "bark")
    for k in range(6):
        y = -33 + k * 5
        m.box(chest, 7, y, -10.3, 8.5, 2.2, 1.6, "bark")
        m.box(chest, -15.5, y, -10.3, 8.5, 2.2, 1.6, "bark")
    m.box(chest, -2.5, -38, 7, 5, 38, 4, "bark")
    m.box(chest, -13, -34, 7.5, 26, 20, 3, "bark_veins")
    for k in range(5):
        sp = m.part(f"backspike_{k}", chest, (0, -34 + k * 7, 10), (-0.9, 0, 0))
        m.box(sp, -1.5, -8 + k, -1.5, 3, 8 - k, 3, "bark")
    for side, sx in (("l", 1), ("r", -1)):
        sh = m.part(f"shoulder_{side}", chest, (15 * sx, -31, 0), (0, 0, -0.25 * sx))
        m.box(sh, -7, -7, -8, 14, 14, 16, "bark")
        m.box(sh, -8, -3, -9, 16, 5, 18, "bark_veins")
        for j in range(4):
            sp = m.part(f"shspike_{side}{j}", sh, (sx * (-4 + j * 3), -7, -4 + j * 3), (-0.3, 0, sx * (0.45 + 0.18 * j)))
            m.box(sp, -1.6, -8, -1.6, 3.2, 8, 3.2, "bark")
            m.box(sp, -0.8, -13, -0.8, 1.6, 5, 1.6, "bone_dark")

    # ---- the head: a great carved mask under a crown of thorns
    neck = m.part("neck", chest, (0, -36, -1), anim=True)
    m.box(neck, -6, -11, -6, 12, 12, 12, "bark")
    head = m.part("head", neck, (0, -10, -1), anim=True)
    m.box(head, -10, -25, -3, 20, 25, 13, "bark")
    mask = m.part("mask", head, (0, -12.5, -3))
    # the face itself is carved in fine pixels (four to a model pixel): pale wood with sockets, tears of light and a
    # sigil on the brow (m_mother_mask paints them where the parts below put the eyes and the mouth)
    F = 0.25
    m.box(mask, -8.6, -12, -1.6, 17.2, 24, 1.6, "bark")
    face = m.part("mface", mask, (0, 0, 0), scale=F)
    m.box(face, -9, -12.5, -2.5, 18, 25, 0.9, "mother_mask")
    nose = m.part("mnose", mask, (0, 0.6, -2.45), scale=F)
    m.box(nose, -1.0, -5.2, -1.2, 2.0, 10, 1.2, "mask")
    for side, sx in (("l", 1), ("r", -1)):
        ch = m.part(f"cheek_{side}", mask, (8.5 * sx, 1, -1), (0, 0.55 * sx, 0))
        m.box(ch, -2.5, -8, -1, 5, 17, 2, "mask")
        # a sorrowing brow, raised where it meets the nose, over an almond of violet light
        br = m.part(f"mbrow_{side}", mask, (4.5 * sx, -6.9, -2.55), (0, 0, 0.26 * sx), scale=F)
        m.box(br, -4.4, -0.9, -0.75, 8.8, 1.8, 1.5, "mask")
        ey = m.part(f"meye_{side}", mask, (4.6 * sx, -4.0, -2.55), (0, 0, -0.1 * sx), scale=F)
        m.box(ey, -3.3, -1.1, -0.2, 6.6, 2.2, 0.3, "mother_eye")
        hs = m.part(f"mhorn_{side}", mask, (9 * sx, -8, -1), (0.2, 0, -1.0 * sx))
        m.box(hs, -1.2, -9, -1.2, 2.4, 9, 2.4, "mask")
        m.box(hs, -0.6, -14, -0.6, 1.2, 5, 1.2, "bone_dark")
    # roots and moss trailing from the carved mouth
    for k, (x, length) in enumerate([(-3.2, 3.0), (-1.9, 5.2), (-0.6, 3.6), (0.7, 6.0), (2.0, 4.0), (3.3, 2.6)]):
        rt = m.part(f"mroot_{k}", mask, (x, 7.6, -2.3), (0.08 * (1 if k % 2 else -1), 0, 0.06 * (k - 2.5)), scale=F)
        m.box(rt, -0.25, 0, -0.25, 0.5, length, 0.5, "bark" if k % 2 else "moss")
    chin = m.part("chin", mask, (0, 13, -3), (0, 0, math.pi / 4))
    m.box(chin, -3.8, -3.8, -1, 7.6, 7.6, 2.2, "mask")
    crown = m.part("crown", head, (0, -25, 3), anim=True)
    m.box(crown, -11, -3.5, -7, 22, 3.5, 12, "bark")
    spikes = [(-10, -5, -0.85, [9, 7, 5]), (-7.5, -5.5, -0.55, [12, 10, 7]), (-5, -6, -0.32, [15, 12, 8]),
              (-2.5, -6.5, -0.12, [18, 14, 10]), (0, -6.8, 0, [22, 17, 12]), (2.5, -6.5, 0.12, [18, 14, 10]),
              (5, -6, 0.32, [15, 12, 8]), (7.5, -5.5, 0.55, [12, 10, 7]), (10, -5, 0.85, [9, 7, 5]),
              (-6, 2, -0.35, [9, 6, 5]), (6, 2, 0.35, [9, 6, 5]), (-2, 3, -0.1, [11, 8, 6]), (2, 3, 0.1, [11, 8, 6])]
    for k, (x, z, zr, lens) in enumerate(spikes):
        th = [3.6 - abs(zr), 2.6 - abs(zr) * 0.6, 1.4]
        segs = m.chain_of(f"crown{k}_", crown, (x, -3.5, z), [(-0.1 - (0.3 if z > 0 else 0), 0, zr), (0.07, 0, zr * 0.35), (0.09, 0, zr * 0.3)],
                          lens, th, ["bark", "bark", "bone_dark"])
        if lens[0] >= 12:
            # thorns along the tall spikes
            t = m.part(f"thorn{k}", segs[0], (0, -lens[0] * 0.6, 0), (0, 0, 1.0 if zr >= 0 else -1.0))
            m.box(t, -0.6, -4, -0.6, 1.2, 4, 1.2, "bone_dark")

    # ---- branches from her back like antlers: moss and lanterns hang from them
    branch_defs = [
        (7, -36, 6, -0.3, [0.22, 0.25, 0.32, 0.4, 0.45, 0.5], [24, 22, 20, 18, 16, 12], [7, 6, 5, 4, 3, 2]),
        (10, -30, 5, -0.4, [0.62, 0.34, 0.34, 0.42, 0.5], [26, 24, 22, 20, 16], [7, 6, 5, 4, 2.6]),
        (13, -22, 3, -0.3, [1.1, 0.26, 0.3, 0.38], [24, 22, 20, 16], [6, 5, 4, 2.6]),
    ]
    lantern_at = {(0, 2): 1.35, (0, 5): 1.75, (1, 1): 1.3, (1, 3): 1.55, (1, 4): 1.9, (2, 2): 1.45, (2, 3): 1.8}
    for side, sx in (("l", 1), ("r", -1)):
        for b, (px, py, pz, xr, zs, lens, th) in enumerate(branch_defs):
            rots = [(xr if i == 0 else -0.05, 0.18 * sx if i == 0 else 0, z * sx) for i, z in enumerate(zs)]
            segs = m.chain_of(f"br_{side}{b}_", chest, (px * sx, py, pz), rots, lens, th, "bark", up=True, group="branch")
            for i, seg in enumerate(segs):
                length = lens[i]
                if 1 <= i <= len(segs) - 2:
                    tw = m.part(f"tw_{side}{b}{i}", seg, (0, -length * 0.55, 0), (0.35 * (1 if i % 2 else -1), 0, -0.85 * sx))
                    m.box(tw, -0.9, -10, -0.9, 1.8, 10, 1.8, "bark")
                    tw2 = m.part(f"twb_{side}{b}{i}", tw, (0, -10, 0), (0.2, 0, 0.5 * sx))
                    m.box(tw2, -0.5, -6, -0.5, 1, 6, 1, "bark")
                    m.strand(f"twmoss_{side}{b}{i}", tw, (0, -6, 0), 8 + (i * 5) % 9, 1.0, "moss", group="moss")
                count = 1 if i == 0 else 4
                for j in range(count):
                    at = ((j - 1.5) * 1.1, -length * (0.15 + 0.22 * j), 0.6 * ((j % 2) * 2 - 1))
                    ln = 9 + ((i * 7 + j * 11 + b * 5) % 24)
                    m.strand(f"moss_{side}{b}{i}{j}", seg, at, ln, 1.4 if j % 2 == 0 else 0.9, "moss", clumps=1 if ln > 18 else 0, group="moss")
            for (bb, si), size in lantern_at.items():
                if bb == b and si < len(segs):
                    at = (0, -lens[si], 0) if si == len(segs) - 1 else (0, -lens[si] * 0.7, 0)
                    m.lantern(f"lan_{side}{b}{si}", segs[si], at, size=size, links=4 + 3 * ((si + b) % 2))

    # ---- arms, raised to reach, with hooked talons
    for side, sx in (("l", 1), ("r", -1)):
        arm = m.part(f"arm_{side}", chest, (18 * sx, -28, -1), (-0.5, 0.1 * sx, -1.0 * sx), anim=True)
        m.box(arm, -6, -2, -6, 12, 32, 12, "bark")
        m.box(arm, -6.6, 6, -6.6, 13.2, 7, 13.2, "bark_veins")
        for j in range(3):
            sp = m.part(f"armspike_{side}{j}", arm, (sx * 5, 6 + j * 8, 4), (0.5, 0, -1.2 * sx))
            m.box(sp, -1, -6, -1, 2, 6, 2, "bone_dark")
        fo = m.part(f"forearm_{side}", arm, (0, 30, 0), (-1.25, 0, 0.55 * sx), anim=True)
        m.box(fo, -5, 0, -5, 10, 28, 10, "bark")
        m.box(fo, -5.6, 18, -5.6, 11.2, 6, 11.2, "bark")
        for j in range(4):
            m.strand(f"armmoss_{side}{j}", fo, (-4 + 2.6 * j, 6 + 4 * j, 4.5), 10 + 5 * j, 1.2, "moss", group="moss")
        ha = m.part(f"hand_{side}", fo, (0, 28, 0), (-0.15, 0, 0.1 * sx), anim=True)
        m.box(ha, -7.5, 0, -5, 15, 8, 10, "bark")
        for f, (fx, fz) in enumerate([(-5.6, 0.45), (-1.9, 0.15), (1.9, -0.15), (5.6, -0.45)]):
            fr = m.part(f"fing_{side}{f}", ha, (fx, 7.5, -1.5), (0.35, 0, fz), anim=True)
            m.box(fr, -1.8, 0, -1.8, 3.6, 10, 3.6, "bark")
            mid = m.part(f"fmid_{side}{f}", fr, (0, 9.5, 0), (0.45, 0, 0))
            m.box(mid, -1.45, 0, -1.45, 2.9, 9, 2.9, "bark")
            cl = m.part(f"claw_{side}{f}", mid, (0, 8.5, 0), (0.45, 0, 0))
            m.box(cl, -1.35, 0, -1.35, 2.7, 8, 2.7, "bone")
            ct = m.part(f"clawt_{side}{f}", cl, (0, 8, 0), (0.4, 0, 0))
            m.box(ct, -0.95, 0, -0.95, 1.9, 8, 1.9, "bone")
            m.box(ct, -0.5, 8, -0.5, 1, 5, 1, "bone_dark")
        th = m.part(f"thumb_{side}", ha, (-7.5 * sx, 3, -2), (0.2, 0, 0.95 * sx), anim=True)
        m.box(th, -1.7, 0, -1.7, 3.4, 8, 3.4, "bark")
        tc = m.part(f"thumbc_{side}", th, (0, 8, 0), (0.5, 0, 0))
        m.box(tc, -1.2, 0, -1.2, 2.4, 8, 2.4, "bone")
        m.box(tc, -0.55, 8, -0.55, 1.1, 5, 1.1, "bone_dark")
    return m
