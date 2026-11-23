#!/usr/bin/env python3
"""The trailer and the store gallery from the [gallery] screenshots (3.56).

  git fetch origin gallery && mkdir -p /tmp/g && git archive origin/gallery | tar -x -C /tmp/g
  python3 tools/trailer/trailer.py /tmp/g out/            # trailer.mp4, gallery/*.jpg and gallery_sheet.jpg

The cut is the list below: title cards and shots, each shot slowly pushed in or panned, with the mod's own music
(Wound Remembers, then the Starved One's fight). A shot is found by a part of its name, so the counter prefix the test
puts in front does not matter; one that is missing is left out and said. Needs Pillow and ffmpeg.
"""
import glob
import math
import os
import subprocess
import sys

from PIL import Image, ImageDraw, ImageFilter, ImageFont

W, H, FPS = 1920, 1080, 30
XFADE = 0.45
ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
MUSIC = os.path.join(ROOT, "src/client/resources/assets/extremedimensions/sounds/music")
SERIF = "/usr/share/fonts/truetype/dejavu/DejaVuSerif-Bold.ttf"
SANS = "/usr/share/fonts/opentype/inter/Inter-SemiBold.otf"
SANS_LIGHT = "/usr/share/fonts/opentype/inter/Inter-Light.otf"

# (kind, what, seconds, caption or motion)
CUT = [
    ("card", ["Every portal is a wound."], 2.6, None),
    ("card", ["Every wound remembers."], 2.4, None),
    ("shot", "00_title", 2.4, "hold"),
    ("card", ["Your time beyond", "is the charge you stored."], 2.4, None),
    ("shot", "25_portal_night", 2.0, "in"),
    ("shot", "03_curses_beyond", 2.4, "hold"),
    ("card", ["Run out,", "and you come home cursed."], 2.2, None),
    ("card", ["A fourth world waits", "behind a needle's tear."], 2.4, None),
    ("shot", "17_seam_risen", 2.2, "in"),
    ("shot", "19_gate_front", 2.0, "left"),
    ("shot", "19_gate_far", 2.0, "right"),
    ("shot", "19_island_under", 1.8, "in"),
    ("shot", "18_colossus_cathedral_close", 1.7, "in"),
    ("shot", "18_colossus_citadel_corner", 1.6, "left"),
    ("shot", "20_island_halo", 1.6, "in"),
    ("shot", "20_island_village_close", 1.6, "right"),
    ("shot", "15_fray_knot", 1.8, "in"),
    ("shot", "26_frayed_ray_dive", 2.0, "left"),
    ("card", ["Something watches", "from the dark."], 2.2, None),
    ("shot", "06_tribe_0", 1.8, "in"),
    ("shot", "06_tribe_2", 1.4, "in"),
    ("shot", "06_face_keeper", 1.2, "in"),
    ("card", ["Four Elders.", "One Starved One."], 2.0, None),
    ("shot", "12_fight_matriarch_arrives", 1.3, "in"),
    ("shot", "12_fight_matriarch_barrage", 1.2, "left"),
    ("shot", "12_fight_ferryman_tide", 1.3, "in"),
    ("shot", "12_fight_ferryman_bells", 1.2, "right"),
    ("shot", "12_fight_weaver_arrives", 1.2, "in"),
    ("shot", "12_fight_weaver_lance", 1.2, "left"),
    ("shot", "12_fight_pale_mason_arrives", 1.2, "in"),
    ("shot", "12_fight_pale_mason_pillars", 1.2, "right"),
    ("shot", "35_heart_rise_claws", 1.4, "in"),
    ("shot", "35_heart_risen", 1.8, "in"),
    ("card", ["An ending that changes", "your world for good."], 2.4, None),
    ("shot", "35_heart_crown", 2.2, "left"),
    ("shot", "43_tapestry_finished", 2.2, "hold"),
    ("end", ["EXTREME DIMENSIONS"], 5.0, None),
]

# The store gallery: (part of the name, title)
GALLERY = [
    ("17_seam_risen", "The Seam: a fourth world under a sky of nebulae"),
    ("19_gate_front", "A monumental gate holds your rift"),
    ("19_gate_far", "Floating islands as far as the eye can see"),
    ("18_colossus_cathedral_close", "Colossal spires of the Thinned"),
    ("20_island_village_close", "Villages of the Thinned, who move only unseen"),
    ("26_frayed_ray_dive", "Veil Rays over the Fray"),
    ("06_tribe_0", "The tribe watches from the dark"),
    ("12_fight_matriarch_arrives", "The Matriarch"),
    ("12_fight_ferryman_tide", "The Ferryman"),
    ("12_fight_weaver_lance", "The Last Weaver"),
    ("12_fight_pale_mason_arrives", "The Pale Mason"),
    ("35_heart_risen", "The Starved One wakes in the Heart of the Seam"),
    ("35_heart_crown", "The Heart's crown"),
    ("03_curses_beyond", "92 curses, and 24 Abyssal ones beneath"),
    ("00_title", "Its own title screen and music"),
]


def font(path, size):
    try:
        return ImageFont.truetype(path, size)
    except OSError:
        return ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", size)


def find(shots, part):
    hits = [f for f in shots if part in os.path.basename(f)]
    return hits[0] if hits else None


def cover(im, w, h):
    s = max(w / im.width, h / im.height)
    im = im.resize((math.ceil(im.width * s), math.ceil(im.height * s)), Image.LANCZOS)
    x, y = (im.width - w) // 2, (im.height - h) // 2
    return im.crop((x, y, x + w, y + h))


def vignette():
    v = Image.new("L", (W, H), 0)
    d = ImageDraw.Draw(v)
    d.ellipse((W * 0.18, H * 0.22, W * 0.82, H * 0.78), fill=150)
    return v.filter(ImageFilter.GaussianBlur(260))


VIG = None


def card(lines, t, dur, end=False):
    """A title card: pale serif words on near-black, a dim wound-red glow, each line rising in."""
    im = Image.new("RGB", (W, H), (5, 4, 7))
    glow = Image.new("RGB", (W, H), (46, 8, 14))
    im = Image.composite(glow, im, VIG)
    d = ImageDraw.Draw(im)
    if end:
        f1, f2, f3 = font(SERIF, 112), font(SANS, 40), font(SANS_LIGHT, 34)
        a = min(1.0, t / 0.8)
        col = tuple(int(c * a) for c in (236, 226, 214))
        tw = d.textlength(lines[0], font=f1)
        d.text(((W - tw) / 2, H * 0.34), lines[0], font=f1, fill=col)
        sub = "Every portal is a wound. Every wound remembers."
        a2 = max(0.0, min(1.0, (t - 0.6) / 0.8))
        sw = d.textlength(sub, font=f3)
        d.text(((W - sw) / 2, H * 0.34 + 150), sub, font=f3, fill=tuple(int(c * a2) for c in (200, 120, 120)))
        a3 = max(0.0, min(1.0, (t - 1.2) / 0.8))
        for i, s in enumerate(("Fabric  ·  Minecraft 26.3  ·  Free on Modrinth and CurseForge",
                               "peertjeh.github.io/extremedimensions")):
            fw = d.textlength(s, font=f2)
            d.text(((W - fw) / 2, H * 0.68 + i * 62), s, font=f2, fill=tuple(int(c * a3) for c in (220, 212, 200)))
        return im
    f = font(SERIF, 84)
    total = len(lines)
    for i, s in enumerate(lines):
        start = 0.15 + i * 0.35
        a = max(0.0, min(1.0, (t - start) / 0.6))
        rise = (1 - a) * 18
        tw = d.textlength(s, font=f)
        y = H / 2 - total * 56 + i * 112 + rise
        d.text(((W - tw) / 2, y), s, font=f, fill=tuple(int(c * a) for c in (236, 226, 214)))
    return im


def shot(base, t, dur, motion):
    """Ken Burns: base is 10% larger than the frame; push in, or pan left or right."""
    p = t / dur
    p = p * p * (3 - 2 * p) * 0.6 + p * 0.4  # gentle ease
    bw, bh = base.size
    if motion == "hold":  # a screen or the HUD: all of it in view, barely moving
        z = 1.1 * (1.0 + 0.03 * p)
        cw, ch = bw / z, bh / z
        x, y = (bw - cw) / 2, (bh - ch) / 2
    elif motion == "in":
        z = 1.0 + 0.09 * p
        cw, ch = bw / z, bh / z
        x, y = (bw - cw) / 2, (bh - ch) / 2
    else:
        cw, ch = W * 1.0, H * 1.0
        span = bw - cw
        x = span * (p if motion == "right" else 1 - p)
        y = (bh - ch) / 2
    return base.transform((W, H), Image.EXTENT, (x, y, x + cw, y + ch), Image.BILINEAR)


def main():
    global VIG
    src, out = sys.argv[1], sys.argv[2]
    os.makedirs(out, exist_ok=True)
    shots = sorted(glob.glob(os.path.join(src, "*.jpg")) + glob.glob(os.path.join(src, "*.png")))
    VIG = vignette()

    # the store gallery, and a sheet of it to look over at once
    gdir = os.path.join(out, "gallery")
    os.makedirs(gdir, exist_ok=True)
    picked = []
    for part, title in GALLERY:
        f = find(shots, part)
        if not f:
            print("gallery: no shot for", part)
            continue
        im = cover(Image.open(f).convert("RGB"), W, H)
        name = f"{len(picked) + 1:02d}_{part}.jpg"
        im.save(os.path.join(gdir, name), "JPEG", quality=92)
        picked.append((im, title))
    cols, tw, th, pad = 3, 620, 349, 16
    rows = math.ceil(len(picked) / cols)
    sheet = Image.new("RGB", (cols * (tw + pad) + pad, rows * (th + 56 + pad) + pad + 70), (14, 11, 16))
    d = ImageDraw.Draw(sheet)
    d.text((pad, 18), "Extreme Dimensions · store gallery (1920x1080 each)", font=font(SANS, 30), fill=(236, 226, 214))
    for i, (im, title) in enumerate(picked):
        x, y = pad + (i % cols) * (tw + pad), 70 + pad + (i // cols) * (th + 56 + pad)
        sheet.paste(im.resize((tw, th), Image.LANCZOS), (x, y))
        label, f24 = f"{i + 1}. {title}", font(SANS, 24)
        while d.textlength(label, font=f24) > tw and len(label) > 4:
            label = label[:-2].rstrip() + "…"
        d.text((x, y + th + 10), label, font=f24, fill=(214, 200, 190))
    sheet.save(os.path.join(out, "gallery_sheet.jpg"), "JPEG", quality=88)
    print("gallery:", len(picked), "shots")

    # the trailer's segments, with a short cross-fade between each
    segs = []
    for kind, what, dur, extra in CUT:
        if kind == "shot":
            f = find(shots, what)
            if not f:
                print("trailer: no shot for", what)
                continue
            base = cover(Image.open(f).convert("RGB"), int(W * 1.1), int(H * 1.1))
            segs.append((kind, base, dur, extra))
        else:
            segs.append((kind, what, dur, extra))
    starts, t = [], 0.0
    for s in segs:
        starts.append(t)
        t += s[2] - XFADE
    total = t + XFADE

    def render(i, lt):
        kind, what, dur, extra = segs[i]
        if kind == "shot":
            return shot(what, lt, dur, extra)
        return card(what, lt, dur, end=(kind == "end"))

    video = os.path.join(out, "video.mp4")
    enc = subprocess.Popen(["ffmpeg", "-v", "error", "-y", "-f", "rawvideo", "-pix_fmt", "rgb24", "-s", f"{W}x{H}",
                            "-r", str(FPS), "-i", "-", "-c:v", "libx264", "-preset", "medium", "-crf", "19",
                            "-pix_fmt", "yuv420p", video], stdin=subprocess.PIPE)
    frames = int(total * FPS)
    for n in range(frames):
        ft = n / FPS
        live = [i for i in range(len(segs)) if starts[i] <= ft < starts[i] + segs[i][2]]
        if not live:
            live = [len(segs) - 1]
        a = live[-1]
        img = render(a, ft - starts[a])
        if len(live) > 1:
            b = live[0]
            k = (ft - starts[a]) / XFADE
            img = Image.blend(render(b, ft - starts[b]), img, max(0.0, min(1.0, k)))
        if ft < 0.6:
            img = Image.blend(Image.new("RGB", (W, H)), img, ft / 0.6)
        if ft > total - 1.2:
            img = Image.blend(Image.new("RGB", (W, H)), img, max(0.0, (total - ft) / 1.2))
        enc.stdin.write(img.tobytes())
        if n % 150 == 0:
            print(f"frame {n}/{frames}", flush=True)
    enc.stdin.close()
    enc.wait()

    # the music: Wound Remembers under the first half, the Starved One's fight under the Elders, faded at the end
    cut = next((starts[i] for i, s in enumerate(segs) if s[0] == "card" and s[1][0] == "Four Elders."), total / 2)
    a1, a2 = os.path.join(MUSIC, "wound_remembers.ogg"), os.path.join(MUSIC, "boss_starved_one.ogg")
    flt = (f"[0:a]atrim=0:{cut + 1.5:.2f},asetpts=PTS-STARTPTS[a];"
           f"[1:a]atrim=104:{104 + total - cut + 1.5:.2f},asetpts=PTS-STARTPTS[b];"
           f"[a][b]acrossfade=d=1.5[m];[m]afade=t=in:d=1.0,afade=t=out:st={total - 2.5:.2f}:d=2.5,"
           f"atrim=0:{total:.2f}[out]")
    final = os.path.join(out, "trailer.mp4")
    subprocess.run(["ffmpeg", "-v", "error", "-y", "-i", a1, "-i", a2, "-i", video, "-filter_complex", flt,
                    "-map", "2:v", "-map", "[out]", "-c:v", "copy", "-c:a", "aac", "-b:a", "192k", "-shortest",
                    "-movflags", "+faststart", final], check=True)
    os.remove(video)
    print(f"trailer: {total:.1f} s, {len(segs)} parts -> {final}")


if __name__ == "__main__":
    main()
