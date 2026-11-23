"""Mother of Knots: the Knot Mother's own fight music (about three minutes, stereo, made from tones and noise).

D minor at 128 beats a minute, in 3+3+2 (the accents of every bar fall on its first, fourth and seventh eighth, a knot
pulled in three tugs). Her theme circles its root and winds back like a knot being tied (D F E D, A, B flat A G A), and
its answer climbs and then draws tight down a chromatic step (D F G A G F, E E flat D).

- Intro: out of a low drone her heart beats; threads are plucked in her theme; a choir hums; bronze rings.
- A: a low string ostinato in 3+3+2, great drums under it, her theme rung on bronze, the choir on the chords.
- B: the choir sings her theme; a braam swells at every change of chord; the drums double.
- Break: the drums drop away; only her heart, a creaking thread, whispers and her theme plucked high.
- Build: everything returns, brighter bar by bar, drums ever denser, a snare roll and a riser to the top.
- Climax: through the Neapolitan (D minor, E flat, B flat, A), the choir an octave higher, bronze doubling it, knots
  ticking in sixteenths, a gong every eight bars.
- A again, lighter, the strings singing her theme; then a last great hit, and the choir, her threads and her heart fade.

It swells in from near silence and fades out to it, so the fight's music can lay one pass over the next (see the
client's BossMusic). Loudness matched to the other Elders' tracks. Deterministic (fixed seeds). Builds on
tools/sounds/knot_mother.py (her choir, bronze, threads, booms) and score.py. Needs numpy and ffmpeg.

usage: python3 tools/sounds/boss_knot_mother.py [--out=FILE.ogg] [--mp3] [--preview=DIR]
"""
import os
import subprocess
import sys
import tempfile
import wave

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import knot_mother as km  # noqa: E402
from score import hz  # noqa: E402

SR = km.SR
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..",
                   "src/client/resources/assets/extremedimensions/sounds/music/boss_knot_mother.ogg")

BPM = 128
BEAT = 60 / BPM
BAR = 4 * BEAT
EIGHTH = BEAT / 2
SIXTEENTH = BEAT / 4
BARS = 100
LENGTH = BARS * BAR + 1.5

# Chords: the notes the choir holds, and the root the strings drive on
CHORDS = {
    "Dm": (["D3", "A3", "D4", "F4"], "D2"),
    "Bb": (["Bb2", "F3", "Bb3", "D4"], "Bb1"),
    "Gm": (["G2", "D3", "G3", "Bb3"], "G1"),
    "A": (["A2", "E3", "A3", "C#4"], "A1"),
    "Eb": (["Eb3", "Bb3", "Eb4", "G4"], "Eb2"),
    "F": (["F3", "C4", "F4", "A4"], "F2"),
}
CHORD_TONES = {"Dm": ["D", "F", "A"], "Bb": ["Bb", "D", "F"], "Gm": ["G", "Bb", "D"], "A": ["A", "C#", "E"],
               "Eb": ["Eb", "G", "Bb"], "F": ["F", "A", "C"]}
VERSE = ["Dm", "Bb", "Gm", "A"]          # two bars each
NEAPOLITAN = ["Dm", "Eb", "Bb", "A"]

# Her theme (note, beats): four bars each
THEME_A = [("D4", 2), ("F4", 1), ("E4", 1), ("D4", 2), ("A3", 2), ("Bb3", 2), ("A3", 1), ("G3", 1), ("A3", 4)]
THEME_B = [("D4", 2), ("F4", 1), ("G4", 1), ("A4", 2), ("G4", 1), ("F4", 1), ("E4", 2), ("Eb4", 2), ("D4", 4)]
# Over the Neapolitan's E flat the theme bends down to its chord
THEME_A2 = [("D4", 2), ("F4", 1), ("E4", 1), ("D4", 2), ("A3", 2), ("Bb3", 2), ("G3", 1), ("F3", 1), ("G3", 4)]


def up(note, octaves=1):
    return note[:-1] + str(int(note[-1]) + octaves)


def bar_at(b):
    return b * BAR


# ------------------------------------------------------------------ the mix: two buses (dry and hall), stereo

N = int(SR * LENGTH)
DRY = np.zeros((2, N))
HALL = np.zeros((2, N))


def place(bus, at, x, gain=1.0, pan=0.0, width=0.0):
    """Lays a mono sound into a bus at second `at`, panned (-1 left, 1 right); `width` (ms) delays the far side a
    little for breadth."""
    i = int(at * SR)
    if i >= N or len(x) == 0:
        return
    n = min(len(x), N - i)
    a = (pan + 1) * np.pi / 4
    gl, gr = np.cos(a) * gain, np.sin(a) * gain
    bus[0, i:i + n] += x[:n] * gl
    d = int(SR * width / 1000)
    j = i + d
    if j < N:
        m = min(len(x), N - j)
        bus[1, j:j + m] += x[:m] * gr


CACHE = {}


def cached(key, make):
    if key not in CACHE:
        CACHE[key] = make()
    return CACHE[key]


# ------------------------------------------------------------------ instruments

def stab(f, d, bright=1.0, seed=0):
    """A short bowed-and-bitten string note for the ostinato: three detuned saws whose upper harmonics close fast, as a
    filter snapping shut."""
    def make():
        rng = np.random.default_rng(seed)
        t = km.t_(d + 0.08)
        x = np.zeros(len(t))
        for _ in range(3):
            fv = f * 2 ** (rng.uniform(-9, 9) / 1200)
            ph = 2 * np.pi * fv * t + rng.random() * 6
            for k in range(1, int(min(60, 6500 * bright / fv)) + 1):
                x += np.sin(k * ph) / k * np.exp(-t * (5 + 10 * k / (2.2 * bright)))
        x *= np.clip(t / 0.004, 0, 1) * np.clip((d + 0.08 - t) / 0.06, 0, 1)
        return x / 3
    return cached(("stab", round(f, 2), round(d, 3), round(bright, 2), seed), make)


def taiko(size=1.0, seed=0):
    """A great drum: a skin dropping onto its pitch, its body booming, a slap on top."""
    def make():
        rng = np.random.default_rng(seed + 50)
        t = km.t_(1.4)
        f0 = 56 / size
        f = f0 * (1 + 0.7 * np.exp(-t / 0.025))
        body = np.sin(2 * np.pi * km.phase(f)) * km.env(t, 0.002, 0.32 * size) + 0.45 * np.sin(2 * np.pi * km.phase(f * 1.58)) * km.env(t, 0.002, 0.12)
        slap = km.band(rng.standard_normal(len(t)), 120, 2200) * km.env(t, 0.0005, 0.018)
        return km.saturate(body + 0.55 * slap, 1.8)
    return cached(("taiko", size, seed), make)


def tom(f, seed=0):
    def make():
        rng = np.random.default_rng(seed + 70)
        t = km.t_(0.7)
        p = f * (1 + 0.4 * np.exp(-t / 0.02))
        body = np.sin(2 * np.pi * km.phase(p)) * km.env(t, 0.001, 0.16)
        slap = km.band(rng.standard_normal(len(t)), 300, 3500) * km.env(t, 0.0005, 0.012)
        return km.saturate(body + 0.5 * slap, 1.5)
    return cached(("tom", f, seed), make)


def frame(seed=0, bright=1.0):
    """A frame drum struck hard (the snare's place): a dry crack of skin and a short tone."""
    def make():
        rng = np.random.default_rng(seed + 90)
        t = km.t_(0.5)
        crack = km.band(rng.standard_normal(len(t)), 400, 7000 * bright) * km.env(t, 0.0005, 0.07)
        tone = np.sin(2 * np.pi * 185 * t) * km.env(t, 0.001, 0.05)
        return crack + 0.5 * tone
    return cached(("frame", seed, bright), make)


def knot_tick(seed=0):
    """A knot drawn tight, in the place of a hi-hat: a short bright cord click."""
    def make():
        rng = np.random.default_rng(seed + 110)
        t = km.t_(0.12)
        f = 2600 + 900 * rng.random()
        return km.band(rng.standard_normal(len(t)), f * 0.7, f * 1.6) * km.env(t, 0.0003, 0.012) + \
            0.3 * np.sin(2 * np.pi * f * 0.5 * t) * km.env(t, 0.0005, 0.02)
    return cached(("tick", seed), make)


def pluck(note, decay=1.0):
    """One of her threads plucked."""
    return cached(("pluck", note, decay), lambda: km.pluck(np.random.default_rng(sum(map(ord, note))), km.t_(decay * 2.2), 0.0, hz(note),
                                                            decay, 0.75))


def bell(note, strength=1.0, decay=1.0):
    """Her theme rung on bronze."""
    return cached(("bell", note, strength, decay), lambda: km.bronze(np.random.default_rng(7 + len(note)), km.t_(3.2 * decay), 0.0, hz(note),
                                                                     strength, decay, 1.1))


def pad(chord, d, vowel="o", vowel2=None, attack=0.6, release=1.2, seed=0):
    """The choir holding a chord."""
    def make():
        rng = np.random.default_rng(seed + 300)
        t = km.t_(d + release + 0.2)
        return km.choir(rng, t, [hz(n) for n in CHORDS[chord][0]], d, 0.0, vowel, vowel2, voices=3, attack=attack, release=release,
                        breath=0.14)
    return cached(("pad", chord, round(d, 2), vowel, vowel2, round(attack, 2), round(release, 2), seed), make)


def sung(phrase, octave=0, vowel="a", vowel2="o", voices=5, seed=0, legato=0.05):
    """The choir singing a phrase of her theme: voices gliding from note to note, swelling on each, a little apart."""
    def make():
        rng = np.random.default_rng(seed + 500)
        total = sum(b for _, b in phrase) * BEAT
        t = km.t_(total + 1.6)
        f0 = np.zeros(len(t))
        amp = np.zeros(len(t))
        s = 0.0
        prev = None
        for note, beats in phrase:
            d = beats * BEAT
            f = hz(up(note, octave))
            i0, i1 = int(s * SR), int((s + d) * SR)
            seg = np.full(i1 - i0, f)
            if prev is not None:
                g = min(len(seg), int(legato * SR))
                seg[:g] = prev * (f / prev) ** np.linspace(0, 1, g)
            f0[i0:i1] = seg
            tt = np.arange(i1 - i0) / SR
            amp[i0:i1] = (0.75 + 0.25 * np.clip(tt / 0.08, 0, 1)) * (1 - 0.12 * np.clip((tt - d + 0.12) / 0.12, 0, 1))
            prev = f
            s += d
        end = int(s * SR)
        f0[end:] = prev
        amp[end:] = np.exp(-np.arange(len(t) - end) / (SR * 0.45))
        morph = np.clip(t / total, 0, 1)
        x = np.zeros(len(t))
        for v in range(voices):
            det = 2 ** (rng.uniform(-7, 7) / 1200)
            x += km.voice(rng, t, f0 * det, vowel, vowel2, morph, vib=4.8 + rng.random(), vib_depth=0.009, breath=0.12, harmonics=34)
        x *= amp * np.clip(t / 0.06, 0, 1)
        return x / voices ** 0.5
    return cached(("sung", tuple(phrase), octave, vowel, vowel2, voices, seed), make)


def bowed(phrase, octave=1, seed=0):
    """The strings singing her theme legato: a band of saws, rounded off, swelling into each note."""
    def make():
        rng = np.random.default_rng(seed + 700)
        total = sum(b for _, b in phrase) * BEAT
        t = km.t_(total + 1.2)
        f0 = np.zeros(len(t))
        s = 0.0
        prev = None
        for note, beats in phrase:
            d = beats * BEAT
            f = hz(up(note, octave))
            i0, i1 = int(s * SR), int((s + d) * SR)
            seg = np.full(i1 - i0, f)
            if prev is not None:
                g = min(len(seg), int(0.06 * SR))
                seg[:g] = prev * (f / prev) ** np.linspace(0, 1, g)
            f0[i0:i1] = seg
            prev, s = f, s + d
        f0[int(s * SR):] = prev
        x = np.zeros(len(t))
        for _ in range(4):
            fv = f0 * 2 ** (rng.uniform(-8, 8) / 1200) * (1 + 0.004 * np.sin(2 * np.pi * km.phase(np.full(len(t), 5.3 + rng.random()))))
            ph = 2 * np.pi * km.phase(fv) + rng.random() * 6
            for k in range(1, 24):
                x += np.sin(k * ph) / k ** 1.1
        x = km.lowpass(x, 3200)
        e = np.clip(t / 0.25, 0, 1) * np.clip((total + 1.0 - t) / 1.0, 0, 1)
        return x * e / 4
    return cached(("bowed", tuple(phrase), octave, seed), make)


def braam(root, d, seed=0):
    return cached(("braam", root, round(d, 2), seed), lambda: km.braam(np.random.default_rng(seed + 900), km.t_(d + 0.3), 0.0, hz(root), d, 0.18, 5.0, 6))


def gong(seed=0, base=73.4):
    return cached(("gong", seed, base), lambda: km.gong(np.random.default_rng(seed + 1100), km.t_(7.0), 0.0, base, 1.0, 3.5))


def boom(seed=0, strength=1.0):
    return cached(("boom", seed, strength), lambda: km.boom(np.random.default_rng(seed + 1300), km.t_(2.0), 0.0, 80, 40, 1.6, strength))


def heart():
    def make():
        t = km.t_(0.8)
        return km.thump(None, t, 0.0, 50) + 0.7 * km.thump(None, t, 0.22, 46)
    return cached(("heart",), make)


def swell_noise(d, seed=0):
    """A reversed cymbal: air rushing up into the next downbeat."""
    def make():
        rng = np.random.default_rng(seed + 1500)
        t = km.t_(d)
        k = t / d
        return km.band(rng.standard_normal(len(t)), 2500, 12000) * k ** 3 * (1 - np.clip((t - d + 0.02) / 0.02, 0, 1))
    return cached(("swell", round(d, 2), seed), make)


def sub(note, d):
    t = km.t_(d)
    return np.sin(2 * np.pi * hz(note) * t) * np.clip(t / 0.8, 0, 1) * np.clip((d - t) / 1.2, 0, 1)


# ------------------------------------------------------------------ the parts

def ostinato(b0, bars, chords, bright=1.0, octave=False, gain=0.5, rising=0.0):
    """The strings driving in 3+3+2: the root on every eighth, an octave up on the accents."""
    for bb in range(bars):
        chord = chords[(bb // 2) % len(chords)]
        root = CHORDS[chord][1]
        br = bright + rising * bb / max(1, bars - 1)
        for e in range(8):
            accent = e in (0, 3, 6)
            note = up(root) if accent else root
            at = bar_at(b0 + bb) + e * EIGHTH
            g = gain * (1.0 if accent else 0.62)
            place(DRY, at, stab(hz(note), EIGHTH * 0.9, br, e % 2), g, -0.25, 6)
            place(DRY, at, stab(hz(note) * 1.003, EIGHTH * 0.9, br, 2 + e % 2), g * 0.8, 0.25, 0)
            if octave:
                place(DRY, at, stab(hz(up(note)), EIGHTH * 0.8, br * 1.1, 4 + e % 2), g * 0.45, 0.0, 9)


def drums(b0, bars, level=1.0, backbeat=False, ticks=False, double=False, fill=True):
    """Her drums: great drums on the 3+3+2, toms between, a frame drum's crack on the backbeat, knots ticking."""
    for bb in range(bars):
        base = bar_at(b0 + bb)
        for e in (0, 3, 6):
            place(DRY, base + e * EIGHTH, taiko(1.0, (bb + e) % 3), level * (1.0 if e == 0 else 0.8), 0.0)
        if double:
            for e in (1, 4, 7):
                place(DRY, base + e * EIGHTH, taiko(1.25, (bb + e) % 3), level * 0.45, 0.15)
        for e, f, p in ((2, 150, -0.4), (5, 120, 0.4)):
            place(DRY, base + e * EIGHTH, tom(f, bb % 2), level * 0.35, p)
        if backbeat:
            for e in (2, 6):
                place(DRY, base + e * EIGHTH, frame(bb % 3), level * 0.55, 0.05)
        if ticks:
            for s in range(16):
                acc = 1.0 if s % 4 == 2 else 0.55
                place(DRY, base + s * SIXTEENTH, knot_tick(s % 4), level * 0.22 * acc, -0.5 if s % 2 else 0.5)
        if fill and bb % 8 == 7:
            # a run down the toms into the next phrase
            for s, f in zip(range(8, 16), (220, 200, 180, 160, 140, 125, 110, 100)):
                place(DRY, base + s * SIXTEENTH, tom(f, s % 2), level * 0.4, -0.6 + 0.15 * (s - 8))


def pads(b0, bars, chords, vowel="o", vowel2=None, gain=0.32, release=1.2):
    for bb in range(0, bars, 2):
        chord = chords[(bb // 2) % len(chords)]
        x = pad(chord, 2 * BAR, vowel, vowel2, 0.5, release, bb % 2)
        place(HALL, bar_at(b0 + bb), x, gain, -0.45, 0)
        place(HALL, bar_at(b0 + bb) + 0.013, x, gain, 0.45, 0)


def braams(b0, bars, chords, gain=0.32):
    for bb in range(0, bars, 2):
        chord = chords[(bb // 2) % len(chords)]
        root = CHORDS[chord][1]
        place(HALL, bar_at(b0 + bb), braam(up(root), 2 * BAR * 0.9, bb % 2), gain, 0.0, 12)


def theme_on(where, b0, phrase, octave=0, gain=0.4, pan=0.0, kind="bell"):
    s = bar_at(b0)
    for note, beats in phrase:
        n = up(note, octave)
        if kind == "bell":
            place(HALL, s, bell(n, 1.0, 0.8), gain, pan)
        else:
            place(HALL, s, pluck(n, 0.9), gain, pan)
        s += beats * BEAT


def arpeggio(b0, bars, chords, gain=0.18, octave=4):
    """Threads plucked in sixteenths through each chord, up and back: her loom at work."""
    for bb in range(bars):
        chord = chords[(bb // 2) % len(chords)]
        tones = CHORD_TONES[chord]
        seq = [tones[0] + str(octave), tones[1] + str(octave), tones[2] + str(octave), tones[0] + str(octave + 1)]
        order = [0, 1, 2, 3, 2, 1, 2, 1] * 2
        for s, k in enumerate(order):
            place(HALL, bar_at(b0 + bb) + s * SIXTEENTH, pluck(seq[k], 0.5), gain * (1.0 if s % 4 == 0 else 0.7), -0.35 + 0.1 * (s % 4))


def compose():
    rng = np.random.default_rng(2401)

    # Intro (0-8): a drone out of nothing, her heart, her theme plucked, the choir humming, bronze
    place(DRY, 0.0, sub("D1", 8 * BAR + 2), 0.35)
    place(HALL, 0.0, braam("D2", 8 * BAR, 7), 0.16, 0.0, 14)
    for bb in range(8):
        place(DRY, bar_at(bb), heart(), 0.5 + 0.05 * bb)
    theme_on(HALL, 0, THEME_A, 1, 0.28, -0.35, "pluck")
    theme_on(HALL, 4, THEME_B, 1, 0.3, -0.35, "pluck")
    pads(2, 6, ["Dm", "Dm", "Eb"], "u", "o", 0.22, 1.8)
    place(HALL, 0.0, bell("D3", 0.8, 1.6), 0.3, 0.4)
    place(HALL, bar_at(4), bell("A3", 0.8, 1.6), 0.28, 0.4)
    place(HALL, bar_at(6), km.riser(rng, km.t_(2 * BAR), 0.0, 2 * BAR, 200, 7000, 73.4), 0.35, 0.0, 10)
    place(DRY, bar_at(7) + 4 * EIGHTH, swell_noise(4 * EIGHTH, 1), 0.3, 0.0, 8)

    # A (8-24): the ostinato and the drums, her theme rung on bronze, the choir on the chords
    place(DRY, bar_at(8), boom(1), 0.7)
    ostinato(8, 16, VERSE, 0.9)
    drums(8, 16, 0.85)
    pads(8, 16, VERSE, "o", None, 0.26)
    theme_on(HALL, 8, THEME_A, 1, 0.3, 0.35)
    theme_on(HALL, 12, THEME_B, 1, 0.32, 0.35)
    arpeggio(16, 8, VERSE, 0.12)
    theme_on(HALL, 16, THEME_A, 1, 0.26, 0.35)
    theme_on(HALL, 20, THEME_B, 1, 0.28, 0.35)
    place(DRY, bar_at(23) + 4 * EIGHTH, swell_noise(4 * EIGHTH, 2), 0.35, 0.0, 8)

    # B (24-40): the choir sings her theme, braams at every change, the drums double
    place(DRY, bar_at(24), boom(2), 0.8)
    place(HALL, bar_at(24), gong(1), 0.35, 0.0, 10)
    ostinato(24, 16, VERSE, 1.05, octave=True)
    drums(24, 16, 0.95, backbeat=True, double=True)
    braams(24, 16, VERSE, 0.26)
    pads(24, 16, VERSE, "o", None, 0.18)
    for k, (phrase, sd) in enumerate([(THEME_A, 1), (THEME_B, 2), (THEME_A, 3), (THEME_B, 4)]):
        x = sung(phrase, 0, "a", "o", 5, sd)
        place(HALL, bar_at(24 + 4 * k), x, 0.42, -0.2, 0)
        place(HALL, bar_at(24 + 4 * k) + 0.011, x, 0.42, 0.2, 0)
    place(DRY, bar_at(39) + 4 * EIGHTH, swell_noise(4 * EIGHTH, 3), 0.4, 0.0, 8)

    # Break (40-48): only her heart, a thread creaking, whispers, the choir low, her theme plucked high
    place(DRY, bar_at(40), boom(3, 0.8), 0.6)
    place(DRY, bar_at(40), sub("D1", 8 * BAR), 0.3)
    for bb in range(8):
        place(DRY, bar_at(40 + bb), heart(), 0.6)
        place(DRY, bar_at(40 + bb) + 2 * BEAT, heart(), 0.35)
    pads(40, 8, ["Dm", "Eb"], "u", None, 0.24, 1.6)
    theme_on(HALL, 40, THEME_A, 2, 0.22, -0.4, "pluck")
    theme_on(HALL, 44, THEME_B, 2, 0.22, 0.4, "pluck")
    place(HALL, bar_at(41), km.creak(rng, km.t_(6.0), 0.0, 5.0, 4, 18, 300, 1400), 0.12, 0.5)
    place(HALL, bar_at(44), km.whisper(rng, km.t_(7.0), 0.0, 6.5, 1800, 7000), 0.08, -0.5)

    # Build (48-56): everything returns, brighter bar by bar, the drums ever denser, a roll and a riser to the top
    ostinato(48, 8, ["Dm", "Eb", "F", "A"], 0.45, rising=0.75, gain=0.45)
    for bb in range(8):
        base = bar_at(48 + bb)
        hits = [0, 4] if bb < 2 else [0, 2, 4, 6] if bb < 4 else list(range(8)) if bb < 6 else []
        for e in hits:
            place(DRY, base + e * EIGHTH, taiko(1.0, e % 3), 0.55 + 0.05 * bb, 0.0)
    for k in range(32):
        # the roll: closer and louder into the climax
        at = bar_at(54) + 2 * BAR * (1 - (1 - k / 32) ** 1.6)
        place(DRY, at, frame(k % 3), 0.15 + 0.5 * k / 32, 0.0)
    pads(48, 8, ["Dm", "Eb", "F", "A"], "o", "a", 0.26)
    place(HALL, bar_at(48), km.riser(rng, km.t_(8 * BAR), 0.0, 8 * BAR, 150, 9000, 36.7), 0.45, 0.0, 12)
    place(DRY, bar_at(55), swell_noise(BAR, 4), 0.45, 0.0, 8)

    # Climax (56-80): the Neapolitan; the choir an octave up, bronze doubling it, knots ticking, a gong every eight bars
    for k in range(3):
        b0 = 56 + 8 * k
        place(DRY, bar_at(b0), boom(4 + k), 0.9)
        place(HALL, bar_at(b0), gong(2 + k), 0.42, 0.0, 10)
        a, b = sung(THEME_A2, 1, "a", "o", 6, 10 + k), sung(THEME_B, 1, "a", "e", 6, 20 + k)
        place(HALL, bar_at(b0), a, 0.42, -0.25)
        place(HALL, bar_at(b0) + 0.012, a, 0.42, 0.25)
        place(HALL, bar_at(b0 + 4), b, 0.42, -0.25)
        place(HALL, bar_at(b0 + 4) + 0.012, b, 0.42, 0.25)
        theme_on(HALL, b0, THEME_A2, 1, 0.22, 0.45)
        theme_on(HALL, b0 + 4, THEME_B, 1, 0.22, 0.45)
    ostinato(56, 24, NEAPOLITAN, 1.2, octave=True, gain=0.52)
    drums(56, 24, 1.0, backbeat=True, ticks=True, double=True)
    braams(56, 24, NEAPOLITAN, 0.3)
    pads(56, 24, NEAPOLITAN, "a", None, 0.16)
    arpeggio(64, 16, NEAPOLITAN, 0.1, 5)
    place(DRY, bar_at(79) + 4 * EIGHTH, swell_noise(4 * EIGHTH, 5), 0.4, 0.0, 8)

    # A again (80-92): lighter; the strings sing her theme
    place(DRY, bar_at(80), boom(8), 0.7)
    ostinato(80, 12, VERSE, 0.9, gain=0.45)
    drums(80, 12, 0.75)
    pads(80, 12, VERSE, "o", None, 0.22)
    for k, phrase in enumerate([THEME_A, THEME_B, THEME_A]):
        x = bowed(phrase, 1, k)
        place(HALL, bar_at(80 + 4 * k), x, 0.3, -0.3)
        place(HALL, bar_at(80 + 4 * k) + 0.009, x, 0.3, 0.3)

    # Outro (92-100): one last great hit; the choir, her threads and her heart fade into the hall
    place(DRY, bar_at(92), boom(9, 1.1), 1.0)
    place(DRY, bar_at(92), taiko(0.9, 1), 0.9)
    place(HALL, bar_at(92), gong(9), 0.5, 0.0, 10)
    place(HALL, bar_at(92), braam("D2", 3 * BAR, 9), 0.34, 0.0, 12)
    place(HALL, bar_at(92), pad("Dm", 6 * BAR, "o", "u", 0.4, 3.0, 9), 0.3, -0.4)
    place(HALL, bar_at(92) + 0.013, pad("Dm", 6 * BAR, "o", "u", 0.4, 3.0, 9), 0.3, 0.4)
    s = bar_at(93)
    for note, beats in THEME_B:
        place(HALL, s, pluck(up(note), 1.2), 0.24, -0.3)
        s += beats * BEAT * 1.5
    for bb in range(93, 99):
        place(DRY, bar_at(bb), heart(), 0.5 * (99 - bb) / 6)
    place(DRY, bar_at(92), sub("D1", 7 * BAR), 0.3)


# ------------------------------------------------------------------ mastering

def glue(x, threshold_db=-16.0, ratio=2.0, window=0.05, release=0.3):
    """A slow bus compressor over both channels together (by blocks), so the whole sits as one."""
    hop = int(SR * 0.01)
    mono = np.sqrt((x[0] ** 2 + x[1] ** 2) / 2)
    n = len(mono) // hop
    rms = np.sqrt(np.mean(mono[:n * hop].reshape(n, hop) ** 2, axis=1) + 1e-12)
    level = 20 * np.log10(rms)
    smooth = np.empty_like(level)
    a, r = np.exp(-0.01 / window), np.exp(-0.01 / release)
    s = level[0]
    for i, v in enumerate(level):
        s = a * s + (1 - a) * v if v > s else r * s + (1 - r) * v
        smooth[i] = s
    over = np.maximum(0, smooth - threshold_db)
    gain_db = -over * (1 - 1 / ratio)
    g = 10 ** (np.interp(np.arange(x.shape[1]) / hop, np.arange(n), gain_db) / 20)
    return x * g


def kweighted_loudness(x, start=None, end=None):
    k = sum(km.kweight(c) ** 2 for c in x) / 2
    if start is not None:
        k = k[int(start * SR):int(end * SR)]
    return 10 * np.log10(np.mean(k) + 1e-12) - 0.691


def master():
    rng = np.random.default_rng(77)
    out = np.zeros((2, N))
    for c in range(2):
        # the drums and strings in a stone room, the choir, bronze and threads in a great hall (each side its own)
        room = km.reverb(np.random.default_rng(10 + c), DRY[c], 1.3, 0.18, 6000, 0.012)[:N]
        hall = km.reverb(np.random.default_rng(20 + c), HALL[c], 3.6, 0.42, 6500, 0.03)[:N]
        out[c] = room + hall
    out = np.array([km.band(c, 32, 18000, 0.3) for c in out])
    out = glue(out / (np.max(np.abs(out)) + 1e-9))
    # swell in from near silence, fade out to it: one pass of the fight's music lies over the next
    t = np.arange(N) / SR
    fade_in = np.clip(t / 4.0, 0, 1) ** 1.5
    fade_out = np.clip((LENGTH - t) / 6.0, 0, 1) ** 1.5
    out *= fade_in * fade_out
    # loudness as the other Elders' tracks (about -20.5 integrated), the peaks held gently under the ceiling
    gain = 10 ** ((-20.5 - kweighted_loudness(out)) / 20)
    out *= gain
    knee, ceil = 0.6, 0.9
    out = np.where(np.abs(out) > knee, np.sign(out) * (knee + (ceil - knee) * np.tanh((np.abs(out) - knee) / (ceil - knee))), out)
    return out


def export(x, out, mp3=False):
    with tempfile.TemporaryDirectory() as tmp:
        wav = os.path.join(tmp, "theme.wav")
        with wave.open(wav, "wb") as w:
            w.setnchannels(2)
            w.setsampwidth(2)
            w.setframerate(SR)
            w.writeframes((np.clip(x.T, -1, 1) * 32767).astype(np.int16).tobytes())
        os.makedirs(os.path.dirname(os.path.abspath(out)), exist_ok=True)
        subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", wav, "-c:a", "libvorbis", "-q:a", "3", out], check=True)
        if mp3:
            subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", wav, "-c:a", "libmp3lame", "-b:a", "128k", out[:-4] + ".mp3"], check=True)


def main():
    mp3 = "--mp3" in sys.argv
    out = next((a[len("--out="):] for a in sys.argv[1:] if a.startswith("--out=")), OUT)
    preview = next((a[len("--preview="):] for a in sys.argv[1:] if a.startswith("--preview=")), None)
    compose()
    x = master()
    export(x, out, mp3)
    print(f"{out}: {LENGTH:.1f} s, integrated {kweighted_loudness(x):.1f}, peak {np.max(np.abs(x)):.2f}")
    for name, a, b in [("intro", 0, 5), ("intro", 5, 15), ("A", 15, 45), ("B", 45, 75), ("break", 75, 90), ("build", 90, 105),
                       ("climax", 105, 150), ("A again", 150, 172), ("outro", 172, 182), ("end", LENGTH - 5, LENGTH)]:
        print(f"  {name:8s} {a:6.1f}-{b:6.1f} s: {kweighted_loudness(x, a, b):6.1f}")
    if preview:
        os.makedirs(preview, exist_ok=True)
        km.spectrogram((x[0] + x[1])[::4] / 2, os.path.join(preview, "boss_knot_mother.png"))


if __name__ == "__main__":
    main()
