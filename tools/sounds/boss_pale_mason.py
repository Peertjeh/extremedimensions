"""The Unfinished Anchor: the Pale Mason's own fight music (about three minutes, stereo, made from tones and noise).

C sharp minor at 80 beats a minute: a slow, massive pulse, a giant's gait (his great drum on the first beat and the "and"
of the second, a mason's hammer on stone on the third, a soft pickup into the next bar), over a lament: the bass falls C
sharp, B, A, G sharp a bar at a time, as the Endermen keep building and never arrive. The motif of the lost (a choir of
the people the Endermen were) reaches up an arpeggio and falls back (C# E G#, F# E D#, C# E A, G# D# B#); its answer
reaches higher and falls further (E G# C#, B A F#, A E C#, D# B#).

- Intro: out of a low drone the lost hum far off; stone grinds in the distance, gold chains sway, a bell; an Enderman's
  voice glides down through the dark; his drum begins; a low horn states the motif.
- A: the pulse (drum, hammer, chains, low brass stabs), an organ on the lament, the motif rung on gold bells.
- B: the lost sing the motif; a braam swells at every chord; the drums double.
- Break: the drums drop away; the void (a tone that falls for ever), his violet core beating, the chords turning to A
  minor and D (the void's rub); an Enderman's warbling voice sings the answer, sliding from note to note.
- Build: the pulse returns, denser bar by bar, a riser and the blink's reversed whoosh into the climax.
- Climax: C sharp minor, A minor, E, G sharp; the lost sing higher, gold bells doubling them, the chisel in sixteenths,
  his halo struck like a gong every four bars, Enderman glissandi sweeping over it all.
- Outro: one last great hit; the lost hold C sharp minor and sag out of tune, the horn begins the motif once more, his
  core slows, and the drone fades.

It swells in from near silence and fades out to it, so the fight's music can lay one pass over the next (see the
client's BossMusic); both ends sit on C sharp minor. Loudness matched to the other Elders' tracks. Deterministic (fixed
seeds). Builds on tools/sounds/pale_mason.py (his voice, stone, chains, halo), knot_mother.py (choir, bronze, braams,
booms, reverb) and score.py (organ, horn, the endless glide). Needs numpy and ffmpeg.

usage: python3 tools/sounds/boss_pale_mason.py [--out=FILE.ogg] [--mp3] [--preview=DIR]
"""
import os
import subprocess
import sys
import tempfile
import wave

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import knot_mother as km  # noqa: E402
import pale_mason as pm  # noqa: E402
import score  # noqa: E402
from score import hz  # noqa: E402

SR = km.SR
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..",
                   "src/client/resources/assets/extremedimensions/sounds/music/boss_pale_mason.ogg")

BPM = 80
BEAT = 60 / BPM
BAR = 4 * BEAT
EIGHTH = BEAT / 2
SIXTEENTH = BEAT / 4
BARS = 60
LENGTH = BARS * BAR + 1.5

# Chords: the notes the choir and the organ hold, and the root the bass drives on
CHORDS = {
    "C#m": (["C#3", "G#3", "C#4", "E4"], "C#2"),
    "B": (["B2", "F#3", "B3", "D#4"], "B1"),
    "A": (["A2", "E3", "A3", "C#4"], "A1"),
    "G#": (["G#2", "D#3", "G#3", "B#3"], "G#1"),
    "Am": (["A2", "E3", "A3", "C4"], "A1"),
    "E": (["E3", "B3", "E4", "G#4"], "E2"),
    "D": (["D3", "A3", "D4", "F#4"], "D2"),
}
LAMENT = ["C#m", "B", "A", "G#"]      # a bar each: the bass falls C# B A G#
VOID = ["C#m", "Am", "C#m", "D"]       # the break, two bars each: the chromatic shadow and the Neapolitan rub
CLIMAX = ["C#m", "Am", "E", "G#"]      # a bar each

# The motif of the lost (note, beats): four bars each
THEME_A = [("C#4", 1), ("E4", 1), ("G#4", 2), ("F#4", 1), ("E4", 1), ("D#4", 2), ("C#4", 1), ("E4", 1), ("A4", 2),
           ("G#4", 2), ("D#4", 1), ("B#3", 1)]
THEME_B = [("E4", 1), ("G#4", 1), ("C#5", 2), ("B4", 1), ("A4", 1), ("F#4", 2), ("A4", 2), ("E4", 1), ("C#4", 1),
           ("D#4", 2), ("B#3", 2)]
# Over the climax's A minor the motif falls to C natural; over its E it finds the major, the nearest it comes to home
THEME_A2 = [("C#4", 1), ("E4", 1), ("G#4", 2), ("A4", 1), ("E4", 1), ("C4", 2), ("B4", 1), ("G#4", 1), ("E4", 2),
            ("D#4", 2), ("B#3", 2)]
THEME_B2 = [("E4", 1), ("G#4", 1), ("C#5", 2), ("C5", 2), ("B4", 1), ("A4", 1), ("G#4", 1), ("B4", 1), ("E5", 2),
            ("D#5", 1), ("B#4", 1), ("G#4", 2)]


def up(note, octaves=1):
    return note[:-1] + str(int(note[-1]) + octaves)


def bar_at(b):
    return b * BAR


# ------------------------------------------------------------------ the mix: three buses (dry, hall, far), stereo

N = int(SR * LENGTH)
DRY = np.zeros((2, N))
HALL = np.zeros((2, N))
FAR = np.zeros((2, N))


def place(bus, at, x, gain=1.0, pan=0.0, width=0.0):
    """Lays a mono sound into a bus at second `at`, panned (-1 left, 1 right); `width` (ms) delays the far side a
    little for breadth."""
    i = int(at * SR)
    if i < 0:
        x = x[-i:]
        i = 0
    if i >= N or len(x) == 0:
        return
    n = min(len(x), N - i)
    a = (pan + 1) * np.pi / 4
    gl, gr = np.cos(a) * gain, np.sin(a) * gain
    bus[0, i:i + n] += x[:n] * gl
    j = i + int(SR * width / 1000)
    if j < N:
        m = min(len(x), N - j)
        bus[1, j:j + m] += x[:m] * gr


def wide(bus, at, x, gain=1.0, spread=0.45, delay=0.013):
    """A sound on both sides, the right a breath late: a choir or a pad filling the hall."""
    place(bus, at, x, gain, -spread)
    place(bus, at + delay, x, gain, spread)


CACHE = {}


def cached(key, make):
    if key not in CACHE:
        CACHE[key] = make()
    return CACHE[key]


# ------------------------------------------------------------------ instruments

def drum(size=1.0, seed=0):
    """His great drum: a skin dropping onto a low pitch, a long body, a slap of stone on top."""
    def make():
        rng = np.random.default_rng(seed + 50)
        t = km.t_(1.8)
        f0 = 44 / size
        f = f0 * (1 + 0.8 * np.exp(-t / 0.03))
        body = np.sin(2 * np.pi * km.phase(f)) * km.env(t, 0.002, 0.45 * size) + \
            0.4 * np.sin(2 * np.pi * km.phase(f * 1.52)) * km.env(t, 0.002, 0.15)
        slap = km.band(rng.standard_normal(len(t)), 100, 1800) * km.env(t, 0.0005, 0.02)
        return km.saturate(body + 0.5 * slap, 2.0)
    return cached(("drum", size, seed), make)


def hammer(seed=0, size=1.0):
    """A mason's hammer on stone (the snare's place): a sharp crack, the block's knock, a short low thud."""
    def make():
        rng = np.random.default_rng(seed + 70)
        t = km.t_(0.6)
        return pm.crack(rng, t, 0.0, 1.0, size) + 0.8 * np.sin(2 * np.pi * (95 / size) * t) * km.env(t, 0.001, 0.07)
    return cached(("hammer", seed, size), make)


def jingle(seed=0):
    """A shake of his gold chains (the hi-hat's place)."""
    return cached(("jingle", seed), lambda: pm.chains(np.random.default_rng(seed + 90), km.t_(0.25), 0.0, 0.02, 1, 1700, 4600, 0.025, (2, 4)))


def chisel(seed=0):
    """The chisel's tick, in sixteenths in the climax: a small bright chip of stone."""
    def make():
        rng = np.random.default_rng(seed + 110)
        t = km.t_(0.1)
        f = 3000 + 1500 * rng.random()
        return km.band(rng.standard_normal(len(t)), f * 0.6, f * 1.5) * km.env(t, 0.0003, 0.008) + \
            0.35 * np.sin(2 * np.pi * f * 0.5 * t) * km.env(t, 0.0005, 0.015)
    return cached(("chisel", seed), make)


def stab(f, d, bright=1.0, seed=0):
    """A low brass-and-bow note for the pulse: detuned saws whose upper harmonics close fast, a sine under them."""
    def make():
        rng = np.random.default_rng(seed)
        t = km.t_(d + 0.1)
        x = np.zeros(len(t))
        for _ in range(3):
            fv = f * 2 ** (rng.uniform(-10, 10) / 1200)
            ph = 2 * np.pi * fv * t + rng.random() * 6
            for k in range(1, int(min(48, 4500 * bright / fv)) + 1):
                x += np.sin(k * ph) / k * np.exp(-t * (3 + 7 * k / (2.0 * bright)))
        x = x / 3 + 0.6 * np.sin(2 * np.pi * f * t) * np.exp(-t * 2)
        return x * np.clip(t / 0.006, 0, 1) * np.clip((d + 0.1 - t) / 0.08, 0, 1)
    return cached(("stab", round(f, 2), round(d, 3), round(bright, 2), seed), make)


def organ_pad(chord, d, attack=0.8, release=1.6):
    """The organ holding a chord, with a pedal under it."""
    def make():
        notes, root = CHORDS[chord]
        x = sum(score.organ(hz(n), d + release, attack, release, (1.0, 0.5, 0.25)) for n in notes)
        return x / len(notes) + 0.5 * score.organ(hz(root), d + release, attack, release, (1.0, 0.5))
    return cached(("organ", chord, round(d, 2), attack, release), make)


def pad(chord, d, vowel="u", vowel2=None, attack=0.8, release=1.6, seed=0, far=3200):
    """The lost humming a chord, a little apart and far off."""
    def make():
        rng = np.random.default_rng(seed + 300)
        t = km.t_(d + release + 0.2)
        x = km.choir(rng, t, [hz(n) for n in CHORDS[chord][0]], d, 0.0, vowel, vowel2, voices=3, attack=attack, release=release, breath=0.16)
        return km.lowpass(x, far)
    return cached(("pad", chord, round(d, 2), vowel, vowel2, attack, release, seed, far), make)


def sung(phrase, octave=0, vowel="o", vowel2="a", voices=5, seed=0, legato=0.07, sag=0.12, far=5000):
    """The lost singing a phrase of the motif: voices gliding from note to note, swelling on each and sagging a little
    flat as they hold it (as if they had forgotten the note), a little apart from each other."""
    def make():
        rng = np.random.default_rng(seed + 500)
        total = sum(b for _, b in phrase) * BEAT
        t = km.t_(total + 1.8)
        f0 = np.zeros(len(t))
        amp = np.zeros(len(t))
        s = 0.0
        prev = None
        for note, beats in phrase:
            d = beats * BEAT
            f = hz(up(note, octave))
            i0, i1 = int(s * SR), int((s + d) * SR)
            tt = np.arange(i1 - i0) / SR
            seg = f * 2 ** (-sag / 12 * tt / d)
            if prev is not None:
                g = min(len(seg), int(legato * SR))
                seg[:g] = prev * (seg[g - 1] / prev) ** np.linspace(0, 1, g)
            f0[i0:i1] = seg
            amp[i0:i1] = (0.72 + 0.28 * np.clip(tt / 0.15, 0, 1)) * (1 - 0.15 * np.clip((tt - d + 0.15) / 0.15, 0, 1))
            prev = seg[-1]
            s += d
        end = int(s * SR)
        f0[end:] = prev
        amp[end:] = np.exp(-np.arange(len(t) - end) / (SR * 0.6))
        morph = np.clip(t / total, 0, 1)
        x = np.zeros(len(t))
        for _ in range(voices):
            det = 2 ** (rng.uniform(-8, 8) / 1200)
            x += km.voice(rng, t, f0 * det, vowel, vowel2, morph, vib=4.6 + rng.random(), vib_depth=0.009, breath=0.14, harmonics=32)
        x *= amp * np.clip(t / 0.08, 0, 1)
        return km.lowpass(x / voices ** 0.5, far)
    return cached(("sung", tuple(phrase), octave, vowel, vowel2, voices, seed, far), make)


def ender_line(phrase, octave=0, seed=0, slide=0.28, stretch=1.0):
    """An Enderman's voice made an instrument: a warbling hollow voice sliding through a phrase, each note reached by a
    long glide, the way they never quite land on anything."""
    def make():
        rng = np.random.default_rng(seed + 600)
        total = sum(b for _, b in phrase) * BEAT * stretch
        t = km.t_(total + 1.5)
        f0 = np.zeros(len(t))
        s = 0.0
        prev = None
        for note, beats in phrase:
            d = beats * BEAT * stretch
            f = hz(up(note, octave))
            i0, i1 = int(s * SR), int((s + d) * SR)
            seg = np.full(i1 - i0, f)
            if prev is not None:
                g = min(len(seg), int(slide * SR))
                k = np.linspace(0, 1, g)
                seg[:g] = prev * (f / prev) ** (k * k * (3 - 2 * k))
            f0[i0:i1] = seg
            prev, s = f, s + d
        f0[int(s * SR):] = prev
        k = np.clip(t / total, 0, 1)
        x = pm.ender(rng, t, f0, "u", "o", k, 7.5, 0.028, 0.12, 0.2, 0.08, 30)
        x += 0.25 * pm.tones(f0 * 2 * pm.wobble(rng, len(t), 7.5, 0.028), 4, 1.5)
        e = np.clip(t / 0.4, 0, 1) * np.clip((total + 1.2 - t) / 1.2, 0, 1)
        return km.lowpass(x, 3500) * e
    return cached(("eline", tuple(phrase), octave, seed, slide, stretch), make)


def gliss(f_from, f_to, d, seed=0):
    """An Enderman glissando: a warbling voice sweeping between two far notes, swelling and fading."""
    def make():
        rng = np.random.default_rng(seed + 700)
        t = km.t_(d)
        k = np.clip(t / d, 0, 1)
        f = f_from * (f_to / f_from) ** (k * k * (3 - 2 * k))
        x = pm.ender(rng, t, f, "o", "u", k, 9, 0.035, 0.15, 0.25, 0.1, 30)
        x += 0.35 * pm.tones(f * 2 * pm.wobble(rng, len(t), 9, 0.035), 4, 1.5)
        return km.lowpass(x, 4000) * np.sin(np.pi * k) ** 1.2
    return cached(("gliss", f_from, f_to, d, seed), make)


def horn_line(phrase, octave=-1, seed=0, gain=1.0):
    """A low horn stating the motif, note by note (score.horn)."""
    def make():
        rng = np.random.default_rng(seed + 800)
        total = sum(b for _, b in phrase) * BEAT
        x = np.zeros(int(SR * (total + 2.0)))
        s = 0.0
        for note, beats in phrase:
            d = beats * BEAT
            y = score.horn(rng, hz(up(note, octave)), d + 0.6, 0.12, 0.6, 0.9)
            i = int(s * SR)
            x[i:i + len(y)] += y[: len(x) - i]
            s += d
        return x * gain
    return cached(("horn", tuple(phrase), octave, seed), make)


def bell(note, strength=1.0, decay=1.0):
    """A gold bell."""
    return cached(("bell", note, strength, decay), lambda: km.bronze(np.random.default_rng(11 + len(note)), km.t_(3.4 * decay), 0.0, hz(note),
                                                                     strength, decay, 1.3))


def halo_gong(seed=0):
    """His broken halo struck like a gong."""
    def make():
        rng = np.random.default_rng(seed + 1100)
        t = km.t_(7.0)
        return pm.halo(rng, t, 0.0, 69.3, 1.0, 3.2, 0.3) + 0.6 * km.gong(rng, t, 0.0, 69.3, 1.0, 3.5)
    return cached(("gong", seed), make)


def braam(root, d, seed=0):
    return cached(("braam", root, round(d, 2), seed), lambda: km.braam(np.random.default_rng(seed + 900), km.t_(d + 0.3), 0.0, hz(root), d, 0.4, 4.5, 6))


def boom(seed=0, strength=1.0):
    return cached(("boom", seed, strength), lambda: km.boom(np.random.default_rng(seed + 1300), km.t_(2.2), 0.0, 72, 36, 1.8, strength))


def heart():
    """His violet core beating."""
    def make():
        t = km.t_(0.9)
        return km.thump(None, t, 0.0, 46) + 0.7 * km.thump(None, t, 0.26, 42)
    return cached(("heart",), make)


def vwoop_in(d, seed=0):
    """The blink's whoosh played backwards: it swells up out of nothing into the next downbeat and stops dead."""
    def make():
        rng = np.random.default_rng(seed + 1500)
        v = pm.vwoop(rng, 0.7, 1400, 150, 22, 0.08)
        return km.reverb(rng, v, d, 0.7, 6000)[::-1]
    return cached(("vin", round(d, 2), seed), make)


def into(bus, b, d, gain, seed=0):
    """A reversed blink whose end lands on the downbeat of bar b."""
    x = vwoop_in(d, seed)
    place(bus, bar_at(b) - len(x) / SR, x, gain, 0.0, 10)


def grind(d, seed=0):
    """Stone grinding far off."""
    return cached(("grind", round(d, 2), seed), lambda: pm.grind(np.random.default_rng(seed + 1700), km.t_(d), 0.0, d, 50, 900, 3, 34))


def chains_far(d, count, seed=0):
    return cached(("chains", round(d, 2), count, seed), lambda: pm.chains(np.random.default_rng(seed + 1900), km.t_(d), 0.2, d - 0.6, count, 900,
                                                                         2600, 0.09, (1, 3)))


def void(d, seed=0):
    """The void: a tone that falls for ever (score.shepard), dark and slow."""
    def make():
        t = km.t_(d)
        return score.shepard(t, 7.0, 150, 7, 20, True, 1.1) * np.clip(t / 3.0, 0, 1) * np.clip((d - t) / 3.0, 0, 1)
    return cached(("void", round(d, 2), seed), make)


def sub(note, d):
    t = km.t_(d)
    return np.sin(2 * np.pi * hz(note) * t) * np.clip(t / 1.2, 0, 1) * np.clip((d - t) / 1.6, 0, 1)


# ------------------------------------------------------------------ the parts

def pulse(b0, bars, chords, bright=0.8, octave=False, gain=0.5, rising=0.0):
    """The low brass driving the gait: the root on the first eighth, the "and" of two (an octave up), the third beat,
    and a soft pickup on the last eighth."""
    for bb in range(bars):
        root = CHORDS[chords[bb % len(chords)]][1]
        br = bright + rising * bb / max(1, bars - 1)
        for e, g, o in ((0, 1.0, 0), (3, 0.7, 1), (4, 0.85, 0), (7, 0.45, 0)):
            note = up(root, o)
            at = bar_at(b0 + bb) + e * EIGHTH
            d = EIGHTH * (2.6 if e in (0, 4) else 0.9)
            place(DRY, at, stab(hz(note), d, br, e % 2), gain * g, -0.2, 7)
            place(DRY, at, stab(hz(note) * 1.004, d, br, 2 + e % 2), gain * g * 0.8, 0.2, 0)
            if octave:
                place(DRY, at, stab(hz(up(note)), d * 0.8, br * 1.1, 4), gain * g * 0.35, 0.0, 9)


def drums(b0, bars, level=1.0, hammers=False, double=False, shake=False, chisels=False, fill=True):
    """His drums: the great drum on the first beat and the "and" of two, a hammer on stone on the third, a soft pickup;
    doubled, the hammer on two and four as well; chains shaken in eighths; the chisel in sixteenths."""
    for bb in range(bars):
        base = bar_at(b0 + bb)
        place(DRY, base, drum(1.0, bb % 3), level, 0.0)
        place(DRY, base + 3 * EIGHTH, drum(1.1, (bb + 1) % 3), level * 0.7, 0.05)
        place(DRY, base + 7 * EIGHTH, drum(1.25, (bb + 2) % 3), level * 0.35, -0.1)
        if hammers:
            place(DRY, base + 4 * EIGHTH, hammer(bb % 3), level * 0.6, 0.08)
        if double:
            place(DRY, base + 2 * EIGHTH, hammer(3 + bb % 2, 1.2), level * 0.35, -0.3)
            place(DRY, base + 6 * EIGHTH, hammer(5 + bb % 2, 1.2), level * 0.4, 0.3)
            place(DRY, base + 5 * EIGHTH, drum(1.3, bb % 3), level * 0.3, 0.15)
        if shake:
            for e in range(8):
                place(DRY, base + e * EIGHTH, jingle((bb + e) % 6), level * (0.2 if e % 2 else 0.12), 0.45 if e % 2 else -0.45)
        if chisels:
            for s in range(16):
                acc = 1.0 if s % 4 == 2 else 0.5
                place(DRY, base + s * SIXTEENTH, chisel(s % 5), level * 0.16 * acc, -0.55 if s % 2 else 0.55)
        if fill and bb % 4 == 3:
            # hammers running down into the next phrase
            for s, sz in zip(range(12, 16), (0.8, 0.9, 1.0, 1.15)):
                place(DRY, base + s * SIXTEENTH, hammer(7 + s % 3, sz), level * 0.35, -0.4 + 0.25 * (s - 12))


def organ(b0, bars, chords, gain=0.3, per=1, release=1.6):
    for bb in range(0, bars, per):
        x = organ_pad(chords[(bb // per) % len(chords)], per * BAR, 0.6, release)
        wide(HALL, bar_at(b0 + bb), x, gain, 0.35)


def pads(b0, bars, chords, vowel="u", vowel2=None, gain=0.3, per=1, release=1.6, bus=None, far=3200):
    for bb in range(0, bars, per):
        x = pad(chords[(bb // per) % len(chords)], per * BAR, vowel, vowel2, 0.7, release, 0, far)
        wide(HALL if bus is None else bus, bar_at(b0 + bb), x, gain, 0.5)


def braams(b0, bars, chords, gain=0.3):
    for bb in range(bars):
        root = CHORDS[chords[bb % len(chords)]][1]
        place(HALL, bar_at(b0 + bb), braam(up(root), BAR * 0.95, bb % 2), gain, 0.0, 12)


def bells_on(b0, phrase, octave=1, gain=0.3, pan=0.35, bus=None, decay=0.8):
    s = bar_at(b0)
    for note, beats in phrase:
        place(HALL if bus is None else bus, s, bell(up(note, octave), 1.0, decay), gain, pan)
        s += beats * BEAT


def choir_sings(b0, phrase, octave=0, gain=0.42, seed=0, vowel="o", vowel2="a", voices=5):
    wide(HALL, bar_at(b0), sung(phrase, octave, vowel, vowel2, voices, seed), gain, 0.22, 0.011)


def compose():
    rng = np.random.default_rng(3301)

    # Intro (0-8): a drone, the lost far off, stone grinding, chains, a bell, a glide down through the dark; then his
    # drum, and the horn states the motif
    place(DRY, 0.0, sub("C#1", 8 * BAR + 2), 0.35)
    pads(0, 4, ["C#m"], "u", None, 0.3, 4, 2.2, FAR, 2600)
    pads(4, 4, LAMENT, "u", None, 0.3, 1, 2.2, FAR, 2600)
    place(FAR, 0.0, grind(10.0, 1), 0.12, -0.5)
    place(FAR, bar_at(4), grind(10.0, 2), 0.12, 0.5)
    place(FAR, 0.5, chains_far(9.0, 7, 1), 0.1, 0.6)
    place(FAR, bar_at(3), chains_far(9.0, 7, 2), 0.1, -0.6)
    place(FAR, 0.0, bell("C#5", 0.8, 1.8), 0.22, 0.4)
    place(FAR, bar_at(4), bell("G#4", 0.8, 1.8), 0.2, -0.4)
    place(FAR, bar_at(2), gliss(hz("G#4"), hz("C#3"), 2 * BAR, 1), 0.2, -0.3)
    place(FAR, bar_at(6), gliss(hz("C#3"), hz("E4"), 1.5 * BAR, 2), 0.18, 0.3)
    for bb in range(4, 8):
        place(DRY, bar_at(bb), drum(1.0, bb % 3), 0.45 + 0.08 * (bb - 4))
        if bb >= 6:
            place(DRY, bar_at(bb) + 3 * EIGHTH, drum(1.1, 1), 0.35)
    place(HALL, bar_at(4), horn_line(THEME_A, -1, 1), 0.34, -0.15)
    place(HALL, bar_at(6), km.riser(rng, km.t_(2 * BAR), 0.0, 2 * BAR, 180, 6500, 69.3), 0.3, 0.0, 10)
    into(DRY, 8, 1.4, 0.32, 1)

    # A (8-20): the pulse, the organ on the lament, the motif rung on gold bells, then the horn and the lost humming
    place(DRY, bar_at(8), boom(1), 0.7)
    pulse(8, 12, LAMENT, 0.75)
    drums(8, 4, 0.85, hammers=True)
    drums(12, 8, 0.9, hammers=True, shake=True)
    organ(8, 12, LAMENT, 0.22)
    bells_on(8, THEME_A, 1, 0.26, 0.35)
    bells_on(12, THEME_B, 1, 0.26, -0.35)
    place(HALL, bar_at(16), horn_line(THEME_A, 0, 2), 0.3, 0.2)
    pads(16, 4, LAMENT, "u", "o", 0.2)
    place(FAR, bar_at(15) + 2 * BEAT, gliss(hz("C#5"), hz("G#3"), 1.6 * BAR, 3), 0.16, 0.5)
    place(FAR, bar_at(19) + 2 * BEAT, gliss(hz("E3"), hz("C#5"), 1.4 * BAR, 4), 0.16, -0.5)
    into(DRY, 20, 1.6, 0.36, 2)

    # B (20-32): the lost sing the motif, a braam at every chord, the drums doubled
    place(DRY, bar_at(20), boom(2), 0.8)
    place(HALL, bar_at(20), halo_gong(1), 0.32, 0.0, 10)
    pulse(20, 12, LAMENT, 0.9, octave=True)
    drums(20, 12, 0.95, hammers=True, double=True, shake=True)
    braams(20, 12, LAMENT, 0.22)
    organ(20, 12, LAMENT, 0.14)
    pads(20, 12, LAMENT, "o", None, 0.14)
    for k, (phrase, sd) in enumerate([(THEME_A, 1), (THEME_B, 2), (THEME_A, 3)]):
        choir_sings(20 + 4 * k, phrase, 0, 0.44, sd)
        place(HALL, bar_at(20 + 4 * k), bell(up(phrase[0][0]), 1.0, 1.2), 0.22, 0.45)
    place(FAR, bar_at(27) + 2 * BEAT, gliss(hz("G#4"), hz("C#3"), 1.6 * BAR, 5), 0.16, 0.4)
    into(DRY, 32, 2.0, 0.4, 3)

    # Break (32-40): the drums drop away; the void, his core beating, the chords darkening; an Enderman's voice sings
    # the answer, sliding from note to note
    place(DRY, bar_at(32), boom(3, 0.8), 0.6)
    place(DRY, bar_at(32), sub("C#1", 8 * BAR), 0.3)
    place(FAR, bar_at(32), void(8 * BAR + 2, 1), 0.12, 0.0, 15)
    for bb in range(8):
        place(DRY, bar_at(32 + bb), heart(), 0.55)
        place(DRY, bar_at(32 + bb) + 2 * BEAT, heart(), 0.3)
    organ(32, 8, VOID, 0.16, 2, 2.0)
    pads(32, 8, VOID, "u", None, 0.22, 2, 2.0, FAR, 2400)
    place(HALL, bar_at(33), ender_line(THEME_B, 0, 1, 0.3, 1.5), 0.3, -0.25)
    for bb, note in ((32, "C#5"), (34, "C5"), (36, "C#5"), (38, "D5")):
        place(FAR, bar_at(bb), bell(note, 0.8, 1.6), 0.16, 0.5 if bb % 4 else -0.5)
    place(FAR, bar_at(32), grind(12.0, 3), 0.14, -0.6)
    place(FAR, bar_at(36), chains_far(10.0, 8, 3), 0.12, 0.6)
    place(HALL, bar_at(36), km.whisper(rng, km.t_(9.0), 0.0, 8.5, 1500, 6000), 0.05, -0.5)

    # Build (40-44): the pulse returns, denser bar by bar; a riser and the blink reversed into the climax
    pulse(40, 4, LAMENT, 0.5, gain=0.45, rising=0.6)
    for bb in range(4):
        base = bar_at(40 + bb)
        hits = [0, 4] if bb < 1 else [0, 3, 4] if bb < 2 else [0, 2, 3, 4, 6] if bb < 3 else list(range(8))
        for e in hits:
            place(DRY, base + e * EIGHTH, drum(1.0, e % 3), 0.55 + 0.1 * bb, 0.0)
    for k in range(24):
        # a roll of hammers, closer and louder into the climax
        at = bar_at(42) + 2 * BAR * (1 - (1 - k / 24) ** 1.5)
        place(DRY, at, hammer(k % 3, 1.1), 0.12 + 0.4 * k / 24, 0.0)
    pads(40, 4, LAMENT, "o", "a", 0.24)
    organ(40, 4, LAMENT, 0.18)
    place(HALL, bar_at(40), km.riser(rng, km.t_(4 * BAR), 0.0, 4 * BAR, 150, 8000, 34.6), 0.42, 0.0, 12)
    place(FAR, bar_at(42), gliss(hz("C#3"), hz("C#5"), 2 * BAR, 6), 0.18, 0.0)
    into(DRY, 44, 2.4, 0.45, 4)

    # Climax (44-56): the lost sing higher, bells doubling them, the chisel, his halo a gong every four bars
    for k, (phrase, sd) in enumerate([(THEME_A2, 10), (THEME_B2, 11), (THEME_A2, 12)]):
        b0 = 44 + 4 * k
        place(DRY, bar_at(b0), boom(4 + k), 0.9)
        place(HALL, bar_at(b0), halo_gong(2 + k), 0.38, 0.0, 10)
        choir_sings(b0, phrase, 0, 0.46, sd, "a", "o", 6)
        bells_on(b0, phrase, 1, 0.2, 0.45)
    pulse(44, 12, CLIMAX, 1.05, octave=True, gain=0.52)
    drums(44, 12, 1.0, hammers=True, double=True, shake=True, chisels=True)
    braams(44, 12, CLIMAX, 0.28)
    organ(44, 12, CLIMAX, 0.16)
    pads(44, 12, CLIMAX, "a", None, 0.14)
    place(FAR, bar_at(47) + 2 * BEAT, gliss(hz("C#5"), hz("C#3"), 1.8 * BAR, 7), 0.2, -0.5)
    place(FAR, bar_at(51) + 2 * BEAT, gliss(hz("G#3"), hz("E5"), 1.8 * BAR, 8), 0.2, 0.5)
    into(DRY, 56, 2.0, 0.42, 5)

    # Outro (56-60): one last great hit; the lost hold C sharp minor and sag, the horn begins the motif once more, his
    # core slows, the drone fades
    place(DRY, bar_at(56), boom(9, 1.1), 1.0)
    place(DRY, bar_at(56), drum(0.9, 1), 0.9)
    place(HALL, bar_at(56), halo_gong(9), 0.45, 0.0, 10)
    place(HALL, bar_at(56), braam("C#2", 2 * BAR, 9), 0.3, 0.0, 12)
    wide(HALL, bar_at(56), pad("C#m", 4 * BAR, "o", "u", 0.4, 3.0, 9), 0.3, 0.45)
    wide(FAR, bar_at(56), organ_pad("C#m", 4 * BAR, 0.5, 3.0), 0.2, 0.35)
    place(HALL, bar_at(57), horn_line(THEME_A[:3], -1, 3), 0.3, -0.2)
    place(FAR, bar_at(57), bell("C#5", 0.8, 1.8), 0.2, 0.4)
    for bb in range(57, 60):
        place(DRY, bar_at(bb), heart(), 0.5 * (60 - bb) / 3)
    place(DRY, bar_at(56), sub("C#1", 4 * BAR + 1.5), 0.3)
    place(FAR, bar_at(56), chains_far(9.0, 6, 9), 0.1, 0.6)


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
    out = np.zeros((2, N))
    for c in range(2):
        # the drums and brass in a stone room, the choir, organ and bells in a vast hall, the far things further still
        # (each side its own)
        room = km.reverb(np.random.default_rng(10 + c), DRY[c], 1.4, 0.2, 6000, 0.012)[:N]
        hall = km.reverb(np.random.default_rng(20 + c), HALL[c], 4.2, 0.45, 6000, 0.03)[:N]
        far = km.reverb(np.random.default_rng(30 + c), km.lowpass(FAR[c], 4500), 6.0, 0.65, 4000, 0.06)[:N]
        out[c] = room + hall + far
    out = np.array([km.band(c, 30, 18000, 0.3) for c in out])
    out = glue(out / (np.max(np.abs(out)) + 1e-9))
    # swell in from near silence, fade out to it: one pass of the fight's music lies over the next
    t = np.arange(N) / SR
    fade_in = np.clip(t / 4.0, 0, 1) ** 1.5
    fade_out = np.clip((LENGTH - t) / 6.0, 0, 1) ** 1.5
    out *= fade_in * fade_out
    # loudness as the other Elders' tracks (about -20.5 integrated), the peaks held gently under the ceiling
    out *= 10 ** ((-20.5 - kweighted_loudness(out)) / 20)
    knee, ceil = 0.6, 0.9
    return np.where(np.abs(out) > knee, np.sign(out) * (knee + (ceil - knee) * np.tanh((np.abs(out) - knee) / (ceil - knee))), out)


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
    for name, b0, b1 in [("intro", 0, 4), ("intro", 4, 8), ("A", 8, 20), ("B", 20, 32), ("break", 32, 40), ("build", 40, 44),
                         ("climax", 44, 56), ("outro", 56, 58)]:
        print(f"  {name:8s} {bar_at(b0):6.1f}-{bar_at(b1):6.1f} s: {kweighted_loudness(x, bar_at(b0), bar_at(b1)):6.1f}")
    print(f"  {'end':8s} {LENGTH - 5:6.1f}-{LENGTH:6.1f} s: {kweighted_loudness(x, LENGTH - 5, LENGTH):6.1f}")
    if preview:
        os.makedirs(preview, exist_ok=True)
        km.spectrogram((x[0] + x[1])[::4] / 2, os.path.join(preview, "boss_pale_mason.png"))


if __name__ == "__main__":
    main()
