"""The First Wound: the Starved One's own fight music (about three minutes, stereo, made from tones and noise).

E minor at 60 beats a minute, a beat a second. The Heart lies in the Seam, and the Seam's own Tick of the Boundary swings
its pendulum between E and the D sharp under it, a tick a second; here that clock is broken. It ticks and tocks (E, D
sharp) and stumbles, a tick lost, a tock come late, and in the break the hunger drinks its time and it slows to a stop,
then is spat out racing. Under it a slow heartbeat, a starving one, quickening as the fight does; and the bass swings
like a pendulum both ways from E (E F E D#: the hunger's rub above, the clock's step below). The hunger's motif reaches
up an arpeggio and falls back by step (B E G, A G F, E G B, A F# D#); its answer reaches higher (E G B, C B G, A F C,
B A F# D#). A broken chime (E D# B, and the note after never comes) marks the turns.

- Intro: out of a low drone a low choir hums far off, embers crackle, iron chains sway; the clock begins, and stumbles;
  a starving breath; the heart; a low horn states the motif; the thing stirs (its many throats growl).
- A: the pendulum (Em F Em B/D#): the hunger drum, the heart on one and three, the clock; the low choir on the chords,
  the motif rung on a cracked iron bell, then sung.
- B: the hunger (Em C F B): the choir sings the answer, a braam at every chord, iron struck on the backbeat, chains in
  eighths, the heart on every beat, its roars far off.
- Break: the drums drop away; the hunger drinks the clock's time: it slows and stops, a tone falls for ever, a lone soul
  sings the tick-tock ever slower, the throats growl low; then the time is spat back and the clock races.
- Build: the drum rolling in, the pendulum swung in eighths, a riser and a breath drawn in to the top.
- Climax, twice through (Em C F B, then Em F Am B), the time doubled: the drum in eighths and sixteenths, the clock
  racing, the heart pounding, the choir an octave higher over a low choir doubling it, the bells with it, its crown
  struck like a gong, its roars sweeping over it all.
- Outro: one last great hit; the choir holds E minor and sinks, the heart slows, the clock ticks on alone, and the
  drone fades.

It swells in from near silence and fades out to it, so the fight's music can lay one pass over the next (see the
client's BossMusic); both ends sit on E minor, the clock ticking. Loudness matched to the Elders' tracks. Deterministic
(fixed seeds). Builds on tools/sounds/starved_one.py (its maw, embers, iron, breath, the clock), knot_mother.py (choir,
braams, booms, reverb), pale_mason.py (the cracked halo) and score.py (horn, the endless glide). Needs numpy and ffmpeg.

usage: python3 tools/sounds/boss_starved_one.py [--out=FILE.ogg] [--mp3] [--preview=DIR]
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
import starved_one as so  # noqa: E402
from score import hz  # noqa: E402

SR = km.SR
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..",
                   "src/client/resources/assets/extremedimensions/sounds/music/boss_starved_one.ogg")

BPM = 60
BEAT = 60 / BPM
BAR = 4 * BEAT
EIGHTH = BEAT / 2
SIXTEENTH = BEAT / 4
BARS = 46
LENGTH = BARS * BAR + 1.5

# Chords: the notes the choir holds, and the root the bass drives on
CHORDS = {
    "Em": (["E3", "B3", "E4", "G4"], "E2"),
    "F": (["F3", "C4", "F4", "A4"], "F2"),
    "B/D#": (["D#3", "B3", "F#4", "B4"], "D#2"),
    "C": (["C3", "G3", "C4", "E4"], "C2"),
    "B": (["B2", "F#3", "B3", "D#4"], "B1"),
    "Am": (["A2", "E3", "A3", "C4"], "A1"),
}
PENDULUM = ["Em", "F", "Em", "B/D#"]   # a bar each: the bass swings E F E D#
HUNGER = ["Em", "C", "F", "B"]         # a bar each
DRUNK = ["Em", "F", "Em", "F"]         # the break
CLIMAX2 = ["Em", "F", "Am", "B"]       # the climax's second time through

# The hunger's motif (note, beats): four bars each
THEME_A = [("B3", 1), ("E4", 1), ("G4", 2), ("A4", 1), ("G4", 1), ("F4", 2), ("E4", 1), ("G4", 1), ("B4", 2), ("A4", 1), ("F#4", 1),
           ("D#4", 2)]
THEME_B = [("E4", 1), ("G4", 1), ("B4", 2), ("C5", 1), ("B4", 1), ("G4", 2), ("A4", 1), ("F4", 1), ("C5", 2), ("B4", 1), ("A4", 1),
           ("F#4", 1), ("D#4", 1)]
# In the climax it reaches higher still (up to F, the rub, over the F chord), and the second time falls through A minor
THEME_A2 = [("B3", 1), ("E4", 1), ("G4", 1), ("B4", 1), ("C5", 2), ("B4", 1), ("G4", 1), ("A4", 1), ("C5", 1), ("F5", 2), ("D#5", 2),
            ("B4", 1), ("F#4", 1)]
THEME_B2 = [("G4", 1), ("F#4", 1), ("E4", 1), ("B4", 1), ("C5", 1), ("A4", 1), ("F4", 2), ("E5", 1), ("C5", 1), ("A4", 2), ("B4", 2),
            ("A4", 1), ("D#5", 1)]
# A lone soul in the break singing the clock's tick-tock as it slows
CLOCK_SONG = [("E5", 1), ("D#5", 1), ("E5", 1), ("D#5", 1), ("E5", 2), ("D#5", 2), ("E5", 3), ("B4", 5)]
# The broken chime: the hour struck on a cracked bell, and the note after never comes
CHIME = [("E5", 1), ("D#5", 1), ("B4", 2)]


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


def faded(x, d=0.4):
    """A note whose ring would still be sounding at the end of its buffer, faded out over its last d seconds so it ends
    without a click."""
    n = min(len(x), int(SR * d))
    x = x.copy()
    x[len(x) - n:] *= np.cos(np.linspace(0, np.pi / 2, n)) ** 2
    return x


# ------------------------------------------------------------------ instruments

def tick(tock=False, seed=0, ring=1.0):
    """The clock: its tick on E, its tock a semitone under on D sharp (the Seam's Tick of the Boundary swinging): the
    click of the escapement, a small ring of brass, the knock of the case."""
    def make():
        rng = np.random.default_rng(seed + (200 if tock else 100))
        t = km.t_(0.5)
        f = hz("D#6") if tock else hz("E6")
        click = rng.standard_normal(len(t)) * km.env(t, 0.0001, 0.0007)
        tone = sum(a * np.sin(2 * np.pi * f * r * t + rng.random() * 6) * km.env(t, 0.0004, d * ring)
                   for r, a, d in [(1, 1.0, 0.09), (2.76, 0.4, 0.03), (5.4, 0.2, 0.012)])
        knock = km.band(rng.standard_normal(len(t)), 400, 1600) * km.env(t, 0.0003, 0.012)
        return faded(1.2 * click + tone + 0.7 * knock, 0.1)
    return cached(("tick", tock, seed, ring), make)


def heart():
    """Its heart: a starving one, a deep lub and a dub, its upper harmonic so small speakers hear it too."""
    def make():
        t = km.t_(1.0)
        x = km.thump(None, t, 0.0, 46) + 0.7 * km.thump(None, t, 0.26, 42)
        x += 0.25 * np.sin(2 * np.pi * 92 * t) * (km.env(t, 0.004, 0.05) + 0.7 * km.env(t - 0.26, 0.004, 0.05))
        return faded(x, 0.2)
    return cached(("heart",), make)


def drum(size=1.0, seed=0):
    """The hunger drum: a skin dropping onto a low E, a long hollow body, a slap on top."""
    def make():
        rng = np.random.default_rng(seed + 50)
        t = km.t_(1.8)
        f0 = 41.2 / size
        f = f0 * (1 + 0.9 * np.exp(-t / 0.028))
        body = np.sin(2 * np.pi * km.phase(f)) * km.env(t, 0.002, 0.42 * size) + \
            0.4 * np.sin(2 * np.pi * km.phase(f * 1.52)) * km.env(t, 0.002, 0.14)
        slap = km.band(rng.standard_normal(len(t)), 90, 1600) * km.env(t, 0.0005, 0.022)
        return faded(km.saturate(body + 0.5 * slap, 2.0), 0.3)
    return cached(("drum", size, seed), make)


def tom(f, seed=0):
    def make():
        rng = np.random.default_rng(seed + 60)
        t = km.t_(0.7)
        p = f * (1 + 0.4 * np.exp(-t / 0.02))
        body = np.sin(2 * np.pi * km.phase(p)) * km.env(t, 0.001, 0.16)
        slap = km.band(rng.standard_normal(len(t)), 300, 3500) * km.env(t, 0.0005, 0.012)
        return faded(km.saturate(body + 0.5 * slap, 1.5), 0.15)
    return cached(("tom", f, seed), make)


def iron(seed=0, size=1.0):
    """A chain slammed on iron (the snare's place): the crack of it, a clang of its crown, links jumping, a knock."""
    def make():
        rng = np.random.default_rng(seed + 70)
        t = km.t_(0.8)
        clang = so.crown(rng, t, 0.0, 560 / size, 1.0, 0.25, 0.9)
        crack = rng.standard_normal(len(t)) * km.env(t, 0.0002, 0.004) + km.band(rng.standard_normal(len(t)), 600, 7000) * km.env(t, 0.0005, 0.06)
        links = so.irons(rng, t, 0.0, 0.03, 3, 1500, 4500, 0.05, (3, 6))
        knock = np.sin(2 * np.pi * (110 / size) * t) * km.env(t, 0.001, 0.06)
        return faded(0.5 * km.norm(clang) + 0.9 * km.norm(crack) + 0.5 * km.norm(links) + 0.6 * knock, 0.2)
    return cached(("iron", seed, size), make)


def rattle(seed=0):
    """A shake of its snapped chains (the hi-hat's place)."""
    return cached(("rattle", seed), lambda: so.irons(np.random.default_rng(seed + 90), km.t_(0.25), 0.0, 0.02, 1, 2200, 5600, 0.03, (2, 4)))


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


def pad(chord, d, vowel="u", vowel2=None, attack=0.8, release=1.6, seed=0, far=3200, low=0.5, sink=0.0):
    """The low choir holding a chord, its basses on the root an octave under (low: how loud); sinking `sink` semitones
    as it holds."""
    def make():
        rng = np.random.default_rng(seed + 300)
        t = km.t_(d + release + 0.2)
        bend = 2 ** (-sink / 12 * np.clip(t / (d + release), 0, 1) ** 1.5)
        notes, root = CHORDS[chord]
        x = km.choir(rng, t, [hz(n) * bend for n in notes], d, 0.0, vowel, vowel2, voices=3, attack=attack, release=release, breath=0.16)
        if low > 0:
            x = x + low * km.choir(rng, t, [hz(up(root)) * bend], d, 0.0, "o", None, voices=3, attack=attack, release=release, breath=0.1)
        return km.lowpass(x, far)
    return cached(("pad", chord, round(d, 2), vowel, vowel2, attack, release, seed, far, low, sink), make)


def sung(phrase, octave=0, vowel="o", vowel2="a", voices=5, seed=0, legato=0.08, sag=0.06, far=5000):
    """The choir of the Starved singing a phrase: voices gliding from note to note, swelling on each and sagging a
    little flat as they hold it (starved of breath), a little apart from each other."""
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
            amp[i0:i1] = (0.72 + 0.28 * np.clip(tt / 0.18, 0, 1)) * (1 - 0.15 * np.clip((tt - d + 0.18) / 0.18, 0, 1))
            prev = seg[-1]
            s += d
        end = int(s * SR)
        f0[end:] = prev
        amp[end:] = np.exp(-np.arange(len(t) - end) / (SR * 0.6))
        morph = np.clip(t / total, 0, 1)
        x = np.zeros(len(t))
        for _ in range(voices):
            det = 2 ** (rng.uniform(-9, 9) / 1200)
            x += km.voice(rng, t, f0 * det, vowel, vowel2, morph, vib=4.6 + rng.random(), vib_depth=0.009, breath=0.14, harmonics=32)
        x *= amp * np.clip(t / 0.08, 0, 1)
        return faded(km.lowpass(x / voices ** 0.5, far), 0.6)
    return cached(("sung", tuple(phrase), octave, vowel, vowel2, voices, seed, far, sag), make)


def horn_line(phrase, octave=-1, seed=0):
    """A low horn stating the motif, note by note (score.horn)."""
    def make():
        rng = np.random.default_rng(seed + 800)
        total = sum(b for _, b in phrase) * BEAT
        x = np.zeros(int(SR * (total + 2.0)))
        s = 0.0
        for note, beats in phrase:
            d = beats * BEAT
            y = score.horn(rng, hz(up(note, octave)), d + 0.5, 0.15, 0.6, 0.9)
            i = int(s * SR)
            x[i:i + len(y)] += y[: len(x) - i]
            s += d
        return x
    return cached(("horn", tuple(phrase), octave, seed), make)


def bell(note, strength=1.0, decay=1.0):
    """A cracked iron bell: the two halves either side of the crack ringing a little apart."""
    return cached(("bell", note, strength, decay), lambda: faded(pm.halo(np.random.default_rng(13 + len(note)), km.t_(4.0 * decay), 0.0, hz(note),
                                                                         strength, decay, 0.25), 1.4 * decay))


def gong(seed=0):
    """Its crown of broken iron struck like a gong, a gong's low bloom under it."""
    def make():
        rng = np.random.default_rng(seed + 1100)
        t = km.t_(9.0)
        return faded(so.crown(rng, t, 0.0, hz("E3"), 1.0, 3.0, 0.4) + 0.6 * km.gong(rng, t, 0.0, hz("E2"), 1.0, 3.5), 3.5)
    return cached(("gong", seed), make)


def braam(root, d, seed=0):
    return cached(("braam", root, round(d, 2), seed), lambda: km.braam(np.random.default_rng(seed + 900), km.t_(d + 0.3), 0.0, hz(root), d, 0.4, 4.5, 6))


def boom(seed=0, strength=1.0):
    return cached(("boom", seed, strength), lambda: km.boom(np.random.default_rng(seed + 1300), km.t_(2.2), 0.0, 70, 34, 1.9, strength))


def roar(f_from, f_peak, f_to, d, seed=0):
    """One of its roars, far off: its many throats leaping up and falling away."""
    def make():
        rng = np.random.default_rng(seed + 1400)
        return faded(km.lowpass(so.shout(rng, d, f_from, f_peak, f_to, 0.2, "a", "o", 6, 0.5, 0.5, 0.4), 4500), 0.3)
    return cached(("roar", f_from, f_peak, f_to, d, seed), make)


def growl(note, d, seed=0):
    """Its throats droning low on a note, the maw opening and closing."""
    def make():
        rng = np.random.default_rng(seed + 1450)
        t = km.t_(d)
        k = np.clip(t / d, 0, 1)
        f = hz(note) * (1 + 0.03 * np.sin(np.pi * k))
        x = so.maw(rng, t, f, "u", "o", np.sin(np.pi * k), 5, 0.02, 0.45, 0.5, 0.5, 0.25)
        return x * np.sin(np.pi * k) ** 1.2
    return cached(("growl", note, round(d, 2), seed), make)


def suck(d, seed=0):
    """A breath drawn in (air and time with it), swelling out of nothing to a sharp stop."""
    def make():
        rng = np.random.default_rng(seed + 1500)
        t = km.t_(d)
        x = so.suck(rng, t, 0.0, d, 250, 5000) + 0.5 * km.norm(so.breath(rng, t, 0.0, d, True, (550, 1100, 2400), 1300, 0.35))
        return x * np.clip((d - t) / 0.01, 0, 1)
    return cached(("suck", round(d, 2), seed), make)


def into(bus, b, d, gain, seed=0):
    """A breath drawn in whose end lands on the downbeat of bar b."""
    x = suck(d, seed)
    place(bus, bar_at(b) - len(x) / SR, x, gain, 0.0, 10)


def embers(d, rate=8.0, seed=0):
    return cached(("embers", round(d, 2), rate, seed), lambda: so.embers(np.random.default_rng(seed + 1600), km.t_(d), 0.0, d, rate, 0.55, 0.25))


def chains_far(d, count, seed=0):
    return cached(("chains", round(d, 2), count, seed), lambda: so.irons(np.random.default_rng(seed + 1900), km.t_(d), 0.2, d - 0.6, count, 1100,
                                                                         3400, 0.09, (1, 3)))


def breath(d, inhale=True, seed=0):
    return cached(("breath", round(d, 2), inhale, seed), lambda: so.breath(np.random.default_rng(seed + 2000), km.t_(d + 0.2), 0.0, d, inhale,
                                                                           (550, 1100, 2400), 1300, 0.4))


def void(d, seed=0):
    """Time running out: a tone that falls for ever (score.shepard), dark and slow."""
    def make():
        t = km.t_(d)
        return score.shepard(t, 6.0, 160, 7, 20, True, 1.1) * np.clip(t / 3.0, 0, 1) * np.clip((d - t) / 2.5, 0, 1)
    return cached(("void", round(d, 2), seed), make)


def sub(note, d):
    t = km.t_(d)
    return np.sin(2 * np.pi * hz(note) * t) * np.clip(t / 1.2, 0, 1) * np.clip((d - t) / 1.6, 0, 1)


# ------------------------------------------------------------------ the parts

def pulse(b0, bars, chords, bright=0.8, octave=False, gain=0.5, eighths=False, rising=0.0):
    """The low brass driving: slow, the root on the first beat, the "and" of two (an octave up), the third beat and a
    soft pickup; in eighths (the time doubled), every eighth, the rub a semitone up on the "and" of three, as a pendulum
    swung hard."""
    for bb in range(bars):
        root = CHORDS[chords[bb % len(chords)]][1]
        br = bright + rising * bb / max(1, bars - 1)
        if eighths:
            hits = [(e, g, 0) for e, g in enumerate((1.0, 0.45, 0.6, 0.85, 0.9, 0.5, 0.7, 0.55))]
        else:
            hits = [(0, 1.0, 0), (3, 0.7, 1), (4, 0.85, 0), (7, 0.45, 0)]
        for e, g, o in hits:
            f = hz(up(root, o)) * (so.F_ if eighths and e == 5 else 1.0)
            at = bar_at(b0 + bb) + e * EIGHTH
            d = EIGHTH * (0.85 if eighths else 2.6 if e in (0, 4) else 0.9)
            place(DRY, at, stab(f, d, br, e % 2), gain * g, -0.2, 7)
            place(DRY, at, stab(f * 1.004, d, br, 2 + e % 2), gain * g * 0.8, 0.2, 0)
            if octave:
                place(DRY, at, stab(f * 2, d * 0.8, br * 1.1, 4), gain * g * 0.35, 0.0, 9)


def drums(b0, bars, level=1.0, style="slow", fill=True):
    """The hunger drum. slow: on the first beat, the "and" of two and the third, iron on the fourth; full: iron on the
    backbeat, chains in eighths, a pickup; climax: the drum in eighths and sixteenths, iron on the backbeat with ghosts,
    chains in sixteenths; climax2: all of it, the iron doubled."""
    for bb in range(bars):
        base = bar_at(b0 + bb)
        place(DRY, base, drum(1.0, bb % 3), level, 0.0)
        place(DRY, base + 3 * EIGHTH, drum(1.1, (bb + 1) % 3), level * 0.7, 0.05)
        place(DRY, base + 4 * EIGHTH, drum(1.0, (bb + 2) % 3), level * 0.85, -0.05)
        if style == "slow":
            place(DRY, base + 6 * EIGHTH, iron(bb % 3), level * 0.32, 0.15)
            continue
        place(DRY, base + 2 * EIGHTH, iron(bb % 3), level * 0.55, -0.12)
        place(DRY, base + 6 * EIGHTH, iron(3 + bb % 3), level * 0.6, 0.12)
        place(DRY, base + 7 * EIGHTH, drum(1.25, bb % 3), level * 0.4, -0.1)
        if style == "full":
            for e in range(8):
                place(DRY, base + e * EIGHTH, rattle((bb + e) % 6), level * (0.22 if e % 2 else 0.14), 0.45 if e % 2 else -0.45)
        else:
            for s, g in ((1, 0.35), (5, 0.3), (9, 0.4), (11, 0.3), (13, 0.35)):
                place(DRY, base + s * SIXTEENTH, drum(1.15, s % 3), level * g, 0.1 if s % 4 == 1 else -0.1)
            for s in range(16):
                acc = 1.0 if s % 4 == 2 else 0.55
                place(DRY, base + s * SIXTEENTH, rattle(s % 6), level * 0.16 * acc, -0.5 if s % 2 else 0.5)
            for s in (3, 7, 15):
                place(DRY, base + s * SIXTEENTH, iron(6 + s % 3, 1.25), level * 0.2, 0.35 if s == 7 else -0.35)
            if style == "climax2":
                place(DRY, base + 7 * SIXTEENTH, iron(9 + bb % 2, 1.1), level * 0.35, 0.3)
                place(DRY, base + 14 * SIXTEENTH, iron(11 + bb % 2, 1.1), level * 0.3, -0.3)
        if fill and bb % 4 == 3:
            # toms running down into the next phrase (in sixteenths, then thirty-seconds)
            steps = [12, 13, 14, 15] if style == "full" else [12, 12.5, 13, 13.5, 14, 14.5, 15, 15.5]
            for i, s in enumerate(steps):
                place(DRY, base + s * SIXTEENTH, tom(110 * 0.93 ** i, i % 3), level * (0.35 + 0.04 * i), -0.4 + 0.8 * i / len(steps))


def clock(b0, bars, per_beat=1, gain=0.15, bus=None, stumble=0.08, seed=0, pan=0.3):
    """The broken clock: tick and tock (E, D sharp) `per_beat` times a beat; now and then (stumble: how often) a tick
    lost, one come twice, a tock a little late."""
    rng = np.random.default_rng(seed + 2100)
    bus = HALL if bus is None else bus
    step = BEAT / per_beat
    for i in range(int(round(bars * BAR / step))):
        at = bar_at(b0) + i * step
        r = rng.random()
        if r < stumble * 0.5:
            continue
        late = rng.uniform(0.04, 0.12) * step if r > 1 - stumble * 0.5 else 0.0
        tock = i % 2 == 1
        g = gain * (0.8 if tock else 1.0) * (1.15 if i % (4 * per_beat) == 0 else 1.0)
        place(bus, at + late, tick(tock, i % 4), g, pan if tock else -pan)
        if stumble * 0.5 <= r < stumble * 0.8:
            place(bus, at + step * 0.27, tick(tock, (i + 1) % 4), g * 0.6, pan if tock else -pan)


def drunk_clock(at, count, first=1.0, grow=1.22, gain=0.18, bus=None):
    """The clock's time drunk: its ticks ever further apart, softer and longer ringing, until it stops."""
    bus = HALL if bus is None else bus
    s, iv = at, first
    for i in range(count):
        place(bus, s, tick(i % 2 == 1, i % 4, 1.0 + 0.6 * i), gain * (1 - 0.6 * i / count), 0.3 if i % 2 else -0.3)
        s += iv
        iv *= grow
    return s


def racing_clock(at, d, first=0.5, last=SIXTEENTH / 2, gain=0.16, bus=None):
    """The clock's time spat back: its ticks ever closer, ever louder, stumbling, up to the next downbeat."""
    bus = HALL if bus is None else bus
    rng = np.random.default_rng(2200)
    s, i = at, 0
    while s < at + d - 0.02:
        k = (s - at) / d
        place(bus, s + rng.uniform(0, 0.03), tick(i % 2 == 1, i % 4), gain * (0.5 + 0.5 * k), 0.3 if i % 2 else -0.3)
        s += first * (last / first) ** k
        i += 1


def hearts(b0, bars, per_bar=2, gain=0.5, fade=None):
    """The heart: per_bar beats of it in every bar (a starving heart is slow: two a bar; it quickens with the fight)."""
    for bb in range(bars):
        for j in range(per_bar):
            g = gain * (1.0 if j % 2 == 0 else 0.75)
            if fade is not None:
                g *= max(0.0, 1 - (bb + j / per_bar) / fade)
            place(DRY, bar_at(b0 + bb) + j * BAR / per_bar, heart(), g)


def pads(b0, bars, chords, vowel="u", vowel2=None, gain=0.3, per=1, release=1.6, bus=None, far=3200, low=0.5):
    for bb in range(0, bars, per):
        x = pad(chords[(bb // per) % len(chords)], per * BAR, vowel, vowel2, 0.8, release, 0, far, low)
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


def chime(at, gain=0.2, bus=None, pan=0.4):
    """The broken chime: the hour struck on a cracked bell, and the note after never comes."""
    s = at
    for note, beats in CHIME:
        place(FAR if bus is None else bus, s, bell(note, 0.9, 1.6), gain, pan)
        s += beats * BEAT * 0.75


def choir_sings(b0, phrase, octave=0, gain=0.42, seed=0, vowel="o", vowel2="a", voices=5):
    wide(HALL, bar_at(b0), sung(phrase, octave, vowel, vowel2, voices, seed), gain, 0.22, 0.011)


def compose():
    rng = np.random.default_rng(3401)

    # Intro (0-5): a drone; a low choir humming far off; embers, chains; the clock begins and stumbles; a starving
    # breath; the heart; a low horn states the motif; the thing stirs; the drum
    place(DRY, 0.0, sub("E1", 5 * BAR + 2), 0.35)
    pads(0, 1, ["Em"], "u", None, 0.3, 1, 2.4, FAR, 2600, 0.6)
    pads(1, 4, PENDULUM, "u", None, 0.28, 1, 2.2, FAR, 2600, 0.6)
    place(FAR, 0.0, embers(22.0, 7, 1), 0.08, -0.3)
    place(FAR, 0.4, chains_far(9.0, 6, 1), 0.1, 0.6)
    place(FAR, bar_at(3), chains_far(8.0, 6, 2), 0.1, -0.6)
    clock(0.5, 4.5, 1, 0.12, FAR, 0.12, 1)
    place(FAR, bar_at(1) + 2 * BEAT, breath(2.0, True, 1), 0.12, 0.3)
    place(FAR, bar_at(2) + 2.2 * BEAT, breath(2.4, False, 2), 0.1, -0.3)
    hearts(1, 2, 1, 0.4)
    hearts(3, 2, 2, 0.45)
    place(HALL, bar_at(1), horn_line(THEME_A, -1, 1), 0.34, -0.15)
    place(FAR, bar_at(3), growl("E2", 2 * BAR, 1), 0.18, 0.2)
    chime(bar_at(0) + 1.0, 0.16)
    for bb in range(3, 5):
        place(DRY, bar_at(bb), drum(1.0, bb % 3), 0.5 + 0.1 * (bb - 3))
        place(DRY, bar_at(bb) + 4 * EIGHTH, drum(1.0, 1), 0.35 + 0.1 * (bb - 3))
    place(HALL, bar_at(4), km.riser(rng, km.t_(BAR), 0.0, BAR, 180, 6500, 82.4), 0.3, 0.0, 10)
    into(DRY, 5, 1.4, 0.3, 1)

    # A (5-13): the pendulum; the drum, the heart on one and three, the clock; the low choir; the motif rung on a
    # cracked bell, then sung
    place(DRY, bar_at(5), boom(1), 0.7)
    pulse(5, 8, PENDULUM, 0.7, gain=0.45)
    drums(5, 8, 0.85, "slow")
    hearts(5, 8, 2, 0.48)
    clock(5, 8, 1, 0.14, HALL, 0.08, 2)
    pads(5, 8, PENDULUM, "u", None, 0.24)
    bells_on(5, THEME_A, 1, 0.22, 0.35)
    choir_sings(9, THEME_A, 0, 0.42, 1)
    bells_on(9, THEME_A, 1, 0.12, -0.35)
    chime(bar_at(9) - 0.5, 0.14)
    place(FAR, bar_at(5), chains_far(10.0, 7, 3), 0.1, 0.6)
    place(FAR, bar_at(8) + 2 * BEAT, roar(110, 180, 120, 2.2, 1), 0.12, -0.5)
    into(DRY, 13, 1.8, 0.36, 2)

    # B (13-21): the hunger; the choir sings the answer; a braam at every chord; iron on the backbeat, chains in
    # eighths, the heart on every beat; its roars far off
    place(DRY, bar_at(13), boom(2), 0.8)
    place(HALL, bar_at(13), gong(1), 0.3, 0.0, 10)
    pulse(13, 8, HUNGER, 0.9, octave=True)
    drums(13, 8, 0.95, "full")
    hearts(13, 8, 4, 0.42)
    clock(13, 8, 1, 0.11, HALL, 0.1, 3)
    braams(13, 8, HUNGER, 0.22)
    pads(13, 8, HUNGER, "o", None, 0.14)
    choir_sings(13, THEME_B, 0, 0.44, 2)
    choir_sings(17, THEME_B, 0, 0.44, 3, "a", "o", 6)
    bells_on(17, THEME_B, 1, 0.18, 0.4)
    place(FAR, bar_at(16) + 2 * BEAT, roar(100, 220, 130, 2.4, 2), 0.16, 0.5)
    place(FAR, bar_at(20) + 1 * BEAT, roar(120, 240, 150, 2.6, 3), 0.16, -0.5)
    into(DRY, 21, 2.2, 0.4, 3)

    # Break (21-25): the drums drop away; the hunger drinks the clock's time; a tone falls for ever; a lone soul sings
    # the tick-tock ever slower; the throats growl low; then the time is spat back and the clock races
    place(DRY, bar_at(21), boom(3, 0.8), 0.6)
    place(DRY, bar_at(21), sub("E1", 4 * BAR), 0.3)
    place(FAR, bar_at(21), void(4 * BAR + 2, 1), 0.14, 0.0, 15)
    hearts(21, 3, 1, 0.5)
    hearts(24, 1, 2, 0.5)
    pads(21, 4, DRUNK, "u", None, 0.22, 1, 2.0, FAR, 2400)
    drunk_clock(bar_at(21), 7, 1.0, 1.22, 0.2)
    racing_clock(bar_at(24), BAR, 0.5, SIXTEENTH / 2, 0.16)
    wide(HALL, bar_at(21), sung(CLOCK_SONG, 0, "u", "o", 2, 4, 0.06, 0.0, 4000), 0.26, 0.3, 0.017)
    place(FAR, bar_at(21), growl("E2", 3 * BAR, 2), 0.16, -0.2)
    place(FAR, bar_at(22), breath(5.5, True, 3), 0.1, 0.3)
    place(FAR, bar_at(21), embers(16.0, 10, 2), 0.1, 0.4)
    place(FAR, bar_at(23), chains_far(8.0, 6, 4), 0.1, -0.6)
    chime(bar_at(21) + 0.5, 0.16)

    # Build (25-27): the drum rolling in, the pendulum in eighths, a riser and a breath drawn in to the top
    place(DRY, bar_at(25), boom(10, 0.8), 0.6)
    pulse(25, 2, ["Em", "B"], 0.6, gain=0.5, eighths=True, rising=0.6)
    for bb in range(2):
        base = bar_at(25 + bb)
        hits = [0, 3, 4, 6, 7] if bb == 0 else list(range(8))
        for e in hits:
            place(DRY, base + e * EIGHTH, drum(1.0, e % 3), 0.7 + 0.2 * bb, 0.0)
    braams(25, 2, ["Em", "B"], 0.24)
    for k in range(32):
        # a roll of iron, closer and louder into the climax
        at = bar_at(25) + 2 * BAR * (1 - (1 - k / 32) ** 1.6)
        place(DRY, at, iron(k % 3, 1.2), 0.1 + 0.35 * k / 32, 0.3 if k % 2 else -0.3)
    clock(25, 1, 2, 0.13, HALL, 0.15, 5)
    clock(26, 1, 4, 0.12, HALL, 0.2, 6)
    pads(25, 2, ["Em", "B"], "o", "a", 0.3)
    hearts(25, 2, 4, 0.5)
    place(HALL, bar_at(25), km.riser(rng, km.t_(2 * BAR), 0.0, 2 * BAR, 150, 8000, 41.2), 0.42, 0.0, 12)
    place(FAR, bar_at(25), roar(80, 200, 160, 2 * BAR - 0.5, 4), 0.14, 0.0)
    into(DRY, 27, 2.6, 0.45, 4)

    # Climax (27-35): the hunger, the time doubled; the choir an octave higher, the bells with it; the clock racing in
    # eighths; its crown struck like a gong every four bars; its roars
    for k in range(2):
        b0 = 27 + 4 * k
        place(DRY, bar_at(b0), boom(4 + k), 0.9)
        place(HALL, bar_at(b0), gong(2 + k), 0.36, 0.0, 10)
        choir_sings(b0, THEME_A2, 0, 0.46, 10 + k, "a", "o", 6)
        bells_on(b0, THEME_A2, 1, 0.18, 0.45)
    pulse(27, 8, HUNGER, 1.0, octave=True, gain=0.42, eighths=True)
    drums(27, 8, 1.0, "climax")
    hearts(27, 8, 4, 0.5)
    clock(27, 8, 2, 0.1, HALL, 0.1, 7)
    braams(27, 8, HUNGER, 0.26)
    pads(27, 8, HUNGER, "a", None, 0.13)
    place(FAR, bar_at(30) + 2 * BEAT, roar(110, 260, 140, 2.4, 5), 0.18, -0.5)
    place(FAR, bar_at(34) + 1 * BEAT, roar(100, 240, 170, 2.6, 6), 0.18, 0.5)
    into(DRY, 35, 2.0, 0.42, 5)

    # Climax, again (35-43): through A minor; all of it, the heart pounding in eighths, the clock in sixteenths, a low
    # choir doubling the high one, the gong every two bars, its roars nearer
    for k in range(2):
        b0 = 35 + 4 * k
        place(DRY, bar_at(b0), boom(6 + k, 1.05), 0.95)
        choir_sings(b0, THEME_B2, 0, 0.46, 20 + k, "a", "o", 6)
        wide(HALL, bar_at(b0), sung(THEME_B2, -1, "o", "a", 4, 30 + k), 0.24, 0.35, 0.019)
        bells_on(b0, THEME_B2, 1, 0.2, -0.45)
    for k in range(4):
        place(HALL, bar_at(35 + 2 * k), gong(4 + k), 0.34, 0.0, 10)
    pulse(35, 8, CLIMAX2, 1.1, octave=True, gain=0.44, eighths=True)
    drums(35, 8, 1.0, "climax2")
    hearts(35, 8, 8, 0.4)
    clock(35, 8, 4, 0.08, HALL, 0.14, 8)
    braams(35, 8, CLIMAX2, 0.3)
    pads(35, 8, CLIMAX2, "a", None, 0.14)
    for i, (bb, beat, f) in enumerate([(36, 2, (120, 260, 150)), (38, 2, (100, 230, 140)), (40, 1, (130, 280, 160)), (42, 0, (110, 260, 120))]):
        place(HALL, bar_at(bb) + beat * BEAT, roar(*f, 2.4, 7 + i), 0.16, 0.5 if i % 2 else -0.5, 9)
    place(FAR, bar_at(35), growl("E2", 2 * BAR, 3), 0.14, 0.0)
    into(DRY, 43, 2.4, 0.42, 6)

    # Outro (43-46): one last great hit; the choir holds E minor and sinks; the horn begins the motif once more; the
    # heart slows; the clock ticks on alone; the drone fades
    place(DRY, bar_at(43), boom(9, 1.1), 1.0)
    place(DRY, bar_at(43), drum(0.9, 1), 0.9)
    place(HALL, bar_at(43), gong(9), 0.42, 0.0, 10)
    place(HALL, bar_at(43), braam("E2", 2 * BAR, 9), 0.3, 0.0, 12)
    wide(HALL, bar_at(43), pad("Em", 3 * BAR, "o", "u", 0.4, 3.0, 9, 3200, 0.6, 0.6), 0.3, 0.45)
    place(HALL, bar_at(44), horn_line(THEME_A[:3], -1, 3), 0.28, -0.2)
    hearts(44, 2, 2, 0.45, 2.2)
    clock(43, 3, 1, 0.12, HALL, 0.1, 9)
    chime(bar_at(44) + 2 * BEAT, 0.14)
    place(DRY, bar_at(43), sub("E1", 3 * BAR + 1.5), 0.3)
    place(FAR, bar_at(43), chains_far(9.0, 6, 9), 0.1, 0.6)
    place(FAR, bar_at(43), embers(12.0, 6, 3), 0.08, -0.3)


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
        # the drums and brass in the stone of the Heart, the choir, bells and the clock in its vast hall, the far
        # things further still (each side its own)
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
    # loudness as the Elders' tracks (about -20.5 integrated), the peaks held gently under the ceiling
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
    for name, b0, b1 in [("intro", 0, 5), ("A", 5, 13), ("B", 13, 21), ("break", 21, 25), ("build", 25, 27), ("climax", 27, 35),
                         ("climax 2", 35, 43), ("outro", 43, 45)]:
        print(f"  {name:9s} {bar_at(b0):6.1f}-{bar_at(b1):6.1f} s: {kweighted_loudness(x, bar_at(b0), bar_at(b1)):6.1f}")
    print(f"  {'end':9s} {LENGTH - 5:6.1f}-{LENGTH:6.1f} s: {kweighted_loudness(x, LENGTH - 5, LENGTH):6.1f}")
    if preview:
        os.makedirs(preview, exist_ok=True)
        km.spectrogram((x[0] + x[1])[::4] / 2, os.path.join(preview, "boss_starved_one.png"))


if __name__ == "__main__":
    main()
