"""The Pale Mason's own sounds: an Enderman grown to a giant's size, and the weight of stone, made from noise and tones.

He is an eighteen-block Enderman-like giant in the far End (the Pale Reach), hooded, in a ragged mantle of pale end
stone hung with gold chains, a broken halo over him and a violet core: foreman of the Endermen, who were people once and
build their unfinished anchors trying to get home. So his palette is the Enderman's (a throat whose pitch never holds
still, words played backwards, the "vwoop" of a blink, the rising scream of the stare), grown huge and slow; stone
(grinding, cracking, crumbling, great blocks humming as they rise); gold (chains rattling, a cracked halo, bells far off);
and the lost (a choir of the people they were, far away and sagging out of tune); all in a vast hollow hall, in C sharp
minor (the key of his theme, tools/sounds/boss_pale_mason.py). Nothing is sampled: the Enderman family is only the
reference.

Every sound is mono (Minecraft places it in the world) and deterministic (fixed seeds). Builds on the tools of
tools/sounds/knot_mother.py (voice, choir, bronze, booms, braams, reverb, loudness). Needs numpy and ffmpeg (libvorbis).
With --preview DIR it also writes a spectrogram of each sound there (needs Pillow).

usage: python3 tools/sounds/pale_mason.py [--preview DIR] [pale_mason_<name>...]
"""
import os
import subprocess
import sys
import tempfile
import wave

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import knot_mother as km  # noqa: E402
from knot_mother import (SR, band, boom, braam, bronze, choir, compress, env, gate, lowpass, mix, noise, norm, phase,  # noqa: E402
                         reverb, riser, saturate, shatter, snap, sweep_filter, swell, t_, thump, voice, whisper, whoosh)
from score import shepard  # noqa: E402

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "src/client/resources/assets/extremedimensions/sounds/elder")

C = 138.59   # C#3, his key: C sharp minor (a semitone's rub above, D, for the void)
# the scale's steps over C#: D (the rub), D#, E, F#, G#, A, B
D_, DS, E_, FS, GS, A_, B_ = 1.0595, 1.1225, 1.1892, 1.3348, 1.4983, 1.5874, 1.7818


# ------------------------------------------------------------------ tools of his own

def lay(t, at, y, gain=1.0):
    """y laid into a silence as long as t from second `at` on."""
    x = np.zeros(len(t))
    i = int(at * SR)
    n = min(len(y), len(t) - i)
    if n > 0:
        x[i:i + n] = y[:n] * gain
    return x


def tones(f, n=8, tilt=1.0, top=12000.0):
    """A band-limited harmonic tone following f (an array): its harmonics fade out as they near `top`."""
    ph = phase(f)
    x = np.zeros(len(f))
    for h in range(1, n + 1):
        m = np.clip((top - f * h) / (top * 0.2), 0, 1)
        if not m.any():
            break
        x += m * np.sin(2 * np.pi * h * ph) / h ** tilt
    return x


def wobble(rng, n, rate=10.0, depth=0.04, wander=0.35):
    """The Enderman's warble: a fast, uneven wobble of pitch (a factor around 1). Its rate wanders, so it never settles
    into a singer's vibrato; rate and depth may be arrays (a warble that grows)."""
    drift = lowpass(rng.standard_normal(n), 3)
    drift = drift / (np.std(drift) + 1e-9)
    r = rate * (1 + wander * np.tanh(drift))
    w = np.sin(2 * np.pi * phase(r) + rng.random() * 6) + 0.45 * np.sin(2 * np.pi * phase(r * 1.73) + rng.random() * 6)
    return 1 + depth * w / 1.45


def ender(rng, t, f0, vowel="o", vowel2=None, morph=None, rate=10.0, depth=0.04, rough=0.25, ring=0.22, breath=0.1,
          harmonics=44):
    """His voice: a throat sung through a vowel (knot_mother.voice) whose pitch warbles as an Enderman's does, with a
    little ring modulation (a tone off the harmonics) for the sheen of something not of this world."""
    f0 = np.broadcast_to(np.asarray(f0, dtype=float), t.shape)
    x = voice(rng, t, f0 * wobble(rng, len(t), rate, depth), vowel, vowel2, morph, vib=5.0, vib_depth=0.003, breath=breath,
              harmonics=harmonics, rough=rough)
    if ring > 0:
        x = x * (1 - ring + ring * np.sin(2 * np.pi * phase(f0 * 0.707) + rng.random() * 6))
    return x


def leap(k, start, peak, end, at=0.2, rise=0.6, fall=1.2):
    """A cry's pitch over its length (k from 0 to 1): it leaps from start to peak by `at`, then falls away to end (both
    gliding in pitch, not in hertz)."""
    a = np.minimum(k, at) / at
    b = np.maximum(k - at, 0) / (1 - at)
    return np.where(k < at, start * (peak / start) ** (a ** rise), peak * (end / peak) ** (b ** fall))


def syllable(rng, d, f_from, f_to, vowel="o", vowel2="a", rate=10.0, depth=0.04, rough=0.3, low=0.5, back=False, tail=0.5):
    """One syllable of his speech: a vowel opening into another as the pitch glides, a growl an octave under it. Played
    backwards with its echo (back), it swells up out of nothing and stops dead: the Enderman's way of speaking."""
    t = t_(d)
    k = np.clip(t / d, 0, 1)
    f0 = f_from * (f_to / f_from) ** k
    x = norm(ender(rng, t, f0, vowel, vowel2, k, rate, depth, rough))
    if low > 0:
        x += low * norm(ender(rng, t, f0 / 2, "o", "u", k, rate * 0.7, depth, rough * 1.8, 0.0, 0.05, 70))
    x *= np.clip(t / 0.03, 0, 1) * np.exp(-t / (d * 0.6)) * np.clip((d - t) / 0.06, 0, 1)
    if back:
        x = reverb(rng, x, tail, 0.6, 5000)[::-1]
    return x


def vwoop(rng, d, f_hi=900.0, f_lo=110.0, rate=22.0, depth=0.07):
    """An Enderman's blink, forward: a warbling tone and a band of air falling from f_hi to f_lo and dying away.
    Backwards it is the sound of being drawn out of the world."""
    t = t_(d)
    f = f_lo + (f_hi - f_lo) * np.exp(-t / (d * 0.25))
    tone = tones(f * wobble(rng, len(t), rate, depth, 0.5), 8, 1.1)
    air = sweep_filter(rng, noise(rng, len(t)), np.minimum(f * 3.0, 12000), f * 1.5)
    return (norm(tone) + 0.7 * norm(air)) * env(t, 0.003, d * 0.28)


def stare(rng, t, at, dur, f_from=220.0, f_to=1500.0):
    """The stare: a cluster of tones rising together (a minor second and a tritone apart, an octave under), its warble
    ever faster and wider, torn by ring modulation, flickering, a hiss climbing with it."""
    n = len(t)
    k = np.clip((t - at) / dur, 0, 1)
    f = f_from * (f_to / f_from) ** (k ** 1.4)
    depth = 0.006 + 0.05 * k ** 2
    x = np.zeros(n)
    for r, a in [(1.0, 1.0), (1.0595, 0.7), (1.4142, 0.55), (2.0, 0.3), (0.5, 0.45)]:
        x += a * tones(f * r * wobble(rng, n, 7 + 28 * k ** 2, depth, 0.4), 6, 1.2)
    x *= 1 - 0.55 * k + 0.55 * k * np.sin(2 * np.pi * phase(f * 0.618))
    fl = lowpass(noise(rng, n), 30)
    gap = (fl < -0.4 * np.std(fl)).astype(float)
    gap = np.convolve(gap, np.ones(int(SR * 0.004)) / int(SR * 0.004), "same")
    flick = 1 - 0.7 * k ** 1.5 * gap
    hiss = sweep_filter(rng, noise(rng, n), np.minimum(f * 4, 14000), f * 2)
    shape = np.clip((t - at) / (dur * 0.6), 0, 1) ** 1.5 * (t >= at) * np.clip((at + dur + 0.04 - t) / 0.04, 0, 1)
    return (norm(x) * flick + 0.35 * norm(hiss) * k) * shape


def grind(rng, t, at, dur, lo=80.0, hi=2400.0, rate=6.0, groan=45.0, rise=0.0):
    """Stone dragged over stone: grit that catches and slips in uneven surges, crackling, a rumble under it and the low
    groan of the stone itself (rise: how many octaves the groan climbs, as a wall pushed up out of the floor)."""
    n = len(t)
    shape = gate(t, at, dur, min(0.3, dur * 0.25), dur * 0.3)
    k = np.clip((t - at) / dur, 0, 1)
    s = lowpass(noise(rng, n), rate)
    s = np.maximum(0, s / (np.std(s) + 1e-9) + 0.3) ** 1.5
    grit = band(noise(rng, n), lo, hi) * (0.3 + s)
    clicks = (rng.random(n) < 0.004 * (0.3 + s)) * rng.standard_normal(n)
    crackle = band(clicks, 1500, 8000)
    rumble = lowpass(noise(rng, n), 120)
    j = lowpass(noise(rng, n), 6)
    fg = groan * 2 ** (rise * k) * (1 + 0.05 * j / (np.std(j) + 1e-9))
    g = band(tones(fg, 24, 1.0), 50, 900) * (0.4 + 0.6 * s / (np.max(s) + 1e-9))
    return (norm(grit) + 0.5 * norm(crackle) + 0.8 * norm(rumble) + 0.45 * norm(g)) * shape


def crumble(rng, t, at, spread, count, lo=600.0, hi=6000.0, life=0.01, chunks=0, thin=2.2):
    """Stone breaking up: grit and pebbles falling, thick at first and thinning out, a few heavier chunks knocking low
    among them."""
    n = len(t)
    layers = [np.zeros(n) for _ in range(3)]
    for _ in range(count):
        s = at + spread * rng.random() ** thin
        i = int(s * SR)
        if i >= n:
            continue
        dd = life * (0.4 + 1.6 * rng.random())
        m = min(n - i, int(SR * dd * 5))
        tt = np.arange(m) / SR
        layers[rng.integers(3)][i:i + m] += rng.standard_normal(m) * np.exp(-tt / dd) * (0.2 + 0.8 * rng.random() ** 2)
    edges = [(lo, lo * 2.2), (lo * 1.8, hi * 0.6), (hi * 0.45, hi)]
    x = sum(band(layer, a, b) for layer, (a, b) in zip(layers, edges))
    knock = np.zeros(n)
    for _ in range(chunks):
        s = at + spread * 0.7 * rng.random() ** 1.5
        f = rng.uniform(70, 200)
        tt = t - s
        knock += (np.sin(2 * np.pi * f * tt) * env(tt, 0.001, rng.uniform(0.03, 0.07)) +
                  0.6 * np.sin(2 * np.pi * f * 2.31 * tt) * env(tt, 0.0005, 0.015)) * (0.4 + 0.6 * rng.random())
    if chunks:
        x = norm(x) + 0.9 * norm(band(knock, 50, 1500))
    return x


LINK = [(1.0, 1.0), (2.76, 0.55), (5.40, 0.3), (8.93, 0.15)]


def chains(rng, t, at, spread, count, lo=1300.0, hi=3800.0, life=0.06, bunch=(2, 6), thin=1.0):
    """Gold chains: links striking each other in rattles, each a small bar of metal ringing at a free bar's inharmonic
    partials (gold, so warmer and duller than iron)."""
    n = len(t)
    x = np.zeros(n)
    for _ in range(count):
        s0 = at + spread * rng.random() ** thin
        for j in range(rng.integers(bunch[0], bunch[1] + 1)):
            i = int((s0 + j * rng.uniform(0.006, 0.035)) * SR)
            if i >= n:
                break
            f = lo + (hi - lo) * rng.random()
            dd = life * (0.5 + rng.random())
            m = min(n - i, int(SR * dd * 4))
            tt = np.arange(m) / SR
            ping = sum(a * np.sin(2 * np.pi * f * r * tt + rng.random() * 6) * np.exp(-tt * r ** 0.7 / dd) for r, a in LINK if f * r < 15000)
            ping = ping + 0.3 * rng.standard_normal(m) * np.exp(-tt / 0.0015)
            x[i:i + m] += ping * (0.25 + 0.75 * rng.random())
    return x


def halo(rng, t, at, base, strength=1.0, decay=1.0, crack=0.5):
    """His broken halo of gold, struck: a bright bell with a second a little out of tune beside it (the two halves either
    side of the break), buzzing against each other where they touch."""
    tt = t - at
    a = bronze(rng, t, at, base, 1.0, decay, 1.25)
    b = bronze(rng, t, at + 0.004, base * 1.021, 0.55, decay * 0.6, 1.4)
    rattle = 0.5 + 0.5 * np.tanh(5 * np.sin(2 * np.pi * phase(np.full(len(t), 37.0) + 12 * lowpass(noise(rng, len(t)), 4))))
    buzz = band(a, base * 2, 9000) * rattle * env(tt, 0.002, 0.25 * decay)
    return strength * (a + b + crack * buzz)


def lost(rng, t, notes, dur, start=0.0, vowel="u", vowel2="o", voices=3, attack=0.8, release=1.0, sag=0.03, far=2800.0,
         breath=0.2):
    """The choir of the lost, the people the Endermen were: far off and breathy, each voice sagging a little out of tune
    as it holds, as if it had forgotten the note."""
    k = np.clip((t - start) / max(dur, 1e-3), 0, 1)
    fs = [f * 2 ** (-sag * k * rng.uniform(0.4, 1.6)) for f in notes]
    return lowpass(choir(rng, t, fs, dur, start, vowel, vowel2, voices, attack, release, 0.01, breath), far)


def crack(rng, t, at, strength=1.0, size=1.0):
    """Stone struck and split: a sharp crack, the knock of the stone's body, the split running through it."""
    tt = t - at
    c = noise(rng, len(t)) * env(tt, 0.0002, 0.003)
    knock = band(noise(rng, len(t)), 180 / size, 1800 / size) * env(tt, 0.0005, 0.035 * size)
    split = band(noise(rng, len(t)) * (rng.random(len(t)) > 0.6), 1500, 9000) * env(tt - 0.01, 0.001, 0.05)
    tone = np.sin(2 * np.pi * (260 / size) * tt) * env(tt, 0.0005, 0.04 * size)
    return strength * (2.0 * c + 1.0 * knock + 0.5 * split + 0.5 * tone)


def hollow(x, f, g=0.75):
    """A great empty shell of stone ringing on its own note: a feedback comb tuned to f (worked a delay's length at a
    time, so it stays quick)."""
    d = max(1, int(round(SR / f)))
    y = np.asarray(x, dtype=float).copy()
    for s in range(d, len(y), d):
        e = min(len(y), s + d)
        y[s:e] += g * y[s - d:e - d]
    return y * (1 - g)


def rush(rng, t, at, dur, f_from=200.0, f_to=2400.0, peak=0.8):
    """Air drawn through the Door: a roar of wind rising and swirling, gusting, a low rumble under it."""
    n = len(t)
    k = np.clip((t - at) / dur, 0, 1)
    centre = f_from * (f_to / f_from) ** k * (1 + 0.25 * np.sin(2 * np.pi * phase(0.7 + 2.3 * k)))
    wind = sweep_filter(rng, noise(rng, n), centre, centre * 0.8)
    gust = lowpass(noise(rng, n), 3)
    gust = 0.6 + 0.4 * np.tanh(gust / (np.std(gust) + 1e-9))
    low = lowpass(noise(rng, n), 140)
    return (norm(wind) * gust + 0.7 * norm(low)) * swell(t, at, at + dur * peak, at + dur)


def sub_hum(t, f, at, dur):
    """A sine under everything, swelling in and out: felt more than heard."""
    return np.sin(2 * np.pi * f * t) * gate(t, at, dur, dur * 0.3, dur * 0.4)


def glow(rng, t, f, shimmer=0.15):
    """A violet cube's hum (the Bound Stones, as the blocks he lifts): a deep tone beating against its twin, warbling a
    little as the Endermen's hold does, a shimmer of high partials over it. f may be an array (a hum that climbs or
    bends as it passes)."""
    n = len(t)
    f = np.broadcast_to(np.asarray(f, dtype=float), t.shape)
    x = norm(tones(f * wobble(rng, n, 5, 0.012), 10, 1.4) + tones(f * 1.006 * wobble(rng, n, 4.3, 0.012), 10, 1.4))
    sh = sum(np.sin(2 * np.pi * phase(f * r * wobble(rng, n, 9, 0.02))) for r in (8, 12, 16.2))
    return x + shimmer * norm(sh)


def passby(rng, t, tc, span, f_far, f_near, width=0.45):
    """Something huge swung close past: a roar of air whose band climbs as it nears and drops as it goes by (the turn of
    a Doppler), swelling to its height as it passes at tc (span: how long it is near)."""
    u = (t - tc) / span
    near = 1 / (1 + u ** 2)
    centre = (f_far + (f_near - f_far) * near) * (1 + 0.3 * np.tanh(-2.5 * u))
    return sweep_filter(rng, noise(rng, len(t)), centre, centre * width) * near ** 1.5


def column(rng, t, at, dur=1.4, base=C * 8):
    """A column of violet light tearing up out of the floor: a blast, a band of air ripping upward in a few tenths of a
    second, a tone shooting up with it into a ringing of glass high over C sharp (pure partials of the minor chord, each
    beating against its twin and fluttering, as of crystal), a fizzing roar inside it, all dying away over dur."""
    n = len(t)
    tt = t - at
    on = tt >= 0
    blast = saturate(band(noise(rng, n), 500, 12000) * env(tt, 0.001, 0.06), 3.0)
    k = np.clip(tt / 0.35, 0, 1)
    centre = 300 * (10000 / 300) ** (k ** 0.7)
    tear = sweep_filter(rng, noise(rng, n), centre, centre * 0.6) * np.clip(tt / 0.02, 0, 1) * np.exp(-np.maximum(tt, 0) / 0.25) * on
    glide = tones(C * 2 * 4 ** np.clip(tt / 0.3, 0, 1) ** 0.8 * wobble(rng, n, 14, 0.01), 6, 1.0) * env(tt, 0.01, dur * 0.35)
    ring = np.zeros(n)
    for r, a, d in [(1, 1.0, 1.0), (E_, 0.6, 0.8), (GS, 0.7, 0.75), (2, 0.6, 0.65), (2 * E_, 0.4, 0.5), (3, 0.35, 0.45),
                    (4.03, 0.25, 0.35), (5.41, 0.18, 0.25)]:
        flutter = 1 + 0.35 * np.sin(2 * np.pi * rng.uniform(5, 9) * t + rng.random() * 6)
        for det in (1.0, 1.0035):
            f = base * r * det * wobble(rng, n, 11, 0.003)
            ring += a * np.sin(2 * np.pi * phase(f) + rng.random() * 6) * flutter * env(tt, 0.004, d * dur * 0.5)
    fl = lowpass(noise(rng, n), 40)
    fizz = band(noise(rng, n), 2500, 12000) * np.maximum(0, fl / (np.std(fl) + 1e-9) + 0.5) * env(tt, 0.01, dur * 0.35)
    return 0.8 * norm(blast) + norm(tear) + 0.45 * norm(glide) + 0.8 * norm(ring) + 0.35 * norm(fizz)


def thunder(rng, t, at, roll=2.5, strength=1.0):
    """A crack of thunder: a sharp tearing clap, crackling, then the long uneven roll of it, bursts of rumble that come
    and go and sink away."""
    n = len(t)
    tt = t - at
    clap = noise(rng, n) * env(tt, 0.0003, 0.012) + 0.8 * band(noise(rng, n), 800, 9000) * env(tt, 0.001, 0.05)
    tear = band(noise(rng, n) * (rng.random(n) > 0.7), 1200, 9000) * env(tt - 0.005, 0.002, 0.09)
    bumps = np.zeros(n)
    for _ in range(int(roll * 6)):
        s = at + 0.04 + roll * rng.random() ** 1.6
        bumps += env(t - s, rng.uniform(0.03, 0.15), rng.uniform(0.15, 0.5)) * rng.uniform(0.3, 1.0) * np.exp(-(s - at) / roll)
    roll_ = band(noise(rng, n), 60, 600) * bumps + 0.8 * lowpass(noise(rng, n), 140) * env(tt, 0.04, roll * 0.45)
    return strength * (1.6 * norm(clap) + 0.7 * norm(tear) + 1.2 * norm(roll_))


def quake(rng, t, at, dur, rate=9.0):
    """The ground shaken from under: a deep rumble swelling and shuddering at an uneven rate, the floor rattling with it."""
    n = len(t)
    shape = gate(t, at, dur, dur * 0.15, dur * 0.5)
    j = lowpass(noise(rng, n), 2)
    shud = 0.5 + 0.5 * np.sin(2 * np.pi * phase(rate * (1 + 0.4 * np.tanh(j / (np.std(j) + 1e-9)))))
    low = lowpass(noise(rng, n), 90)
    body = band(noise(rng, n), 90, 400)
    rattle = band(noise(rng, n), 400, 3000) * np.maximum(0, shud - 0.55) ** 2
    return (norm(low) * (0.7 + 0.3 * shud) + 0.6 * norm(body) * shud + 0.25 * norm(rattle)) * shape


def master(rng, x, hall=3.0, wet=0.38, drive=1.6, squeeze=True, bright=6500, presence=0.6):
    """His hall (vast and hollow), then a little heat and a compressor, so every sound of his lands with weight."""
    x = norm(x).copy()
    f = int(len(x) * 0.12)
    x[-f:] *= np.linspace(1, 0, f) ** 1.5
    y = reverb(rng, x, hall, wet, bright)
    y = band(y, 34, 18000, 0.3)
    # presence: a lift over the bite of his blows and the edge of his voice, so he cuts through the music and the fight
    y = y + presence * band(y, 2200, 12000)
    y = saturate(y, drive)
    if squeeze:
        y = compress(y, 0.35, 3.5, 0.003, 0.15)
    return y


# ------------------------------------------------------------------ his body

def pm_voice(seed, pitch):
    """Before each blow: a word of his played backwards, then a warbling groan over a growl an octave down; the stone of
    his mantle shifting, his chains. Short, so it never hides the blow."""
    rng = np.random.default_rng(seed)
    t = t_(1.9)
    a = syllable(rng, 0.36, 118 * pitch, 172 * pitch, "o", "a", 12, 0.05, 0.3, 0.45, back=True, tail=0.32)
    s = len(a) / SR - 0.03
    b = syllable(rng, 0.95, 165 * pitch, 98 * pitch, "a", "u", 10, 0.045, 0.35, 0.6)
    x = mix(0.8 * norm(lay(t, 0.0, a)), norm(lay(t, s, b)), 0.1 * norm(grind(rng, t, s - 0.05, 0.7, 120, 1800, 8, 40)),
            0.16 * norm(chains(rng, t, s, 0.5, 5, life=0.05)), 0.45 * boom(rng, t, s, 80, 38, 0.6))
    return master(rng, x, 2.4, 0.3, 1.6), 2.4


def pm_hurt(seed, pitch, clang):
    """Struck: a warbling cry torn off short, his stone cracking and chipping, his halo clanging, his chains jumping, a
    dull thud."""
    rng = np.random.default_rng(seed)
    t = t_(1.5)
    d = 0.6
    tt = t_(d)
    k = np.clip(tt / d, 0, 1)
    f0 = leap(k, 170, 340, 190, 0.2) * pitch
    c = norm(ender(rng, tt, f0, "a", "e", k, 16, 0.06, 0.45, 0.3)) + 0.6 * norm(ender(rng, tt, f0 / 2, "ah_low", "o", k, 11, 0.05, 0.7,
                                                                                         0.0, 0.05, 60))
    c *= np.clip(tt / 0.02, 0, 1) * np.clip((d - tt) / (d * 0.45), 0, 1)
    x = mix(norm(lay(t, 0.0, c)), 0.5 * norm(crack(rng, t, 0.0, 1.0, 1.2)), 0.3 * norm(crumble(rng, t, 0.01, 0.5, 60, life=0.008)),
            0.3 * norm(halo(rng, t, 0.0, clang, 0.8, 0.5, 0.6)), 0.25 * norm(chains(rng, t, 0.01, 0.25, 6)),
            0.45 * boom(rng, t, 0.0, 85, 42, 0.5))
    return master(rng, x, 2.0, 0.3), 2.0


def pm_ambient(seed, root, words):
    """At rest: words of his muttered low and backwards, a hollow drone of stone, his chains swaying, the lost far off."""
    rng = np.random.default_rng(seed)
    t = t_(4.6)
    x = np.zeros(len(t))
    for at, f_from, f_to, back in words:
        y = syllable(rng, 0.7, f_from, f_to, "u", "o", 7, 0.04, 0.25, 0.6, back=back, tail=0.5)
        x += lay(t, at, norm(y))
    drone = hollow(lowpass(noise(rng, len(t)), 900), root, 0.88) * gate(t, 0.0, 4.4, 1.2, 1.6)
    ch = lost(rng, t, [root * 2, root * 2 * GS], 3.0, 0.8, "u", "o", 2, 1.4, 1.2, 0.04, 2200)
    x = mix(norm(x), 0.35 * norm(drone), 0.15 * norm(chains(rng, t, 0.6, 3.0, 5, 1100, 2600, 0.08, (1, 3))), 0.3 * norm(ch),
            0.12 * norm(grind(rng, t, 2.2, 1.5, 100, 1200, 3, 36)))
    return master(rng, x, 3.6, 0.45, 1.3, False, 6000, 0.35), 5.2


def pm_roar(seed):
    """His roar: the Enderman's scream torn up out of a giant's throat and held, a fifth over it, a growl two octaves
    down, the stare's piercing edge on top, a braam swelling under, his chains thrashing, his halo crashing, a boom."""
    rng = np.random.default_rng(seed)
    t = t_(3.8)
    d = 2.6
    tt = t_(d)
    k = np.clip(tt / d, 0, 1)
    f0 = leap(k, 105, 270, 180, 0.22, 0.6, 1.4)
    shape = np.clip(tt / 0.05, 0, 1) * np.clip((d - tt) / (d * 0.35), 0, 1)
    c = sum(norm(ender(rng, tt, f0 * (1 + 0.006 * i), "a", "o", k, 13, 0.05, 0.45, 0.3)) for i in range(3)) / 3 * shape
    c5 = norm(ender(rng, tt, f0 * 1.498, "e", "a", k, 15, 0.05, 0.3, 0.3)) * shape
    g = norm(ender(rng, tt, f0 / 4, "o", None, None, 6, 0.03, 0.9, 0.0, 0.1, 90)) * shape
    x = mix(norm(lay(t, 0.05, c)), 0.4 * norm(lay(t, 0.08, c5)), 0.55 * norm(saturate(lay(t, 0.05, g), 2.5)),
            0.18 * norm(stare(rng, t, 0.1, 1.2, 700, 1300)), 0.5 * norm(braam(rng, t, 0.0, C / 4, 3.0, 0.35, 7)),
            0.25 * norm(chains(rng, t, 0.05, 1.6, 22, thin=1.6)), 0.3 * norm(halo(rng, t, 0.05, 220, 1.0, 1.2)),
            0.2 * norm(grind(rng, t, 0.0, 2.0, 90, 1600, 7, 42)), 0.9 * boom(rng, t, 0.05, 68, 22, 2.0), 0.35 * crack(rng, t, 0.05))
    return master(rng, x, 3.6, 0.4, 1.8), 5.4


def pm_death(seed):
    """His death: a long warbling wail falling away; his mantle crumbling, his chains slithering down and piling, his
    halo cracking through and shattering, two booms; and the lost, freed, rising away into C sharp major, as he is drawn
    backwards out of the world."""
    rng = np.random.default_rng(seed)
    t = t_(8.4)
    d = 4.4
    tt = t_(d)
    k = np.clip(tt / d, 0, 1)
    f0 = leap(k, 260, 350, 70, 0.12)
    shape = np.clip(tt / 0.05, 0, 1) * np.clip((d - tt) / (d * 0.4), 0, 1)
    w = sum(norm(ender(rng, tt, f0 * (1 + 0.007 * i), "a", "u", k, 11 - 4 * k, 0.05 + 0.03 * k, 0.4, 0.3)) for i in range(3)) / 3
    w += 0.5 * norm(ender(rng, tt, f0 / 2, "ah_low", "o", k, 8, 0.04, 0.7, 0.0, 0.05, 70))
    w *= shape
    # the lost: C sharp minor, then the third lifts (E to E sharp) and the chord rises an octave away into the light
    lift = 2 ** np.clip((t - 4.6) / 3.2, 0, 1) ** 2
    third = np.where(t < 4.2, E_, E_ + (1.2599 - E_) * np.clip((t - 4.2) / 0.5, 0, 1))
    notes = [C * lift, C * third * lift, C * GS * lift, C * 2 * lift]
    ch = choir(rng, t, notes, 5.0, 2.4, "u", "a", 3, 1.6, 1.4, 0.008, 0.18)
    fall = chains(rng, t, 0.3, 2.6, 30, 900, 3200, 0.07, (3, 8), 0.8)
    pile = chains(rng, t, 2.9, 0.6, 14, 600, 1800, 0.05, (4, 9))
    rings = halo(rng, t, 1.0, 196, 1.0, 1.4, 0.8) + 0.8 * halo(rng, t, 2.6, 147, 1.0, 1.6, 1.0)
    sh = shatter(rng, t, 2.6, 1.2, 50, 1500, 6000, 0.12)
    out = vwoop(rng, 1.4, 1100, 90, 18, 0.08)
    out = reverb(rng, out, 1.2, 0.6, 5000)[::-1]
    x = mix(norm(lay(t, 0.1, w)), 0.4 * norm(ch), 0.45 * norm(crumble(rng, t, 0.2, 4.0, 900, 400, 5000, 0.012, 16, 1.4)),
            0.3 * norm(fall), 0.25 * norm(pile), 0.4 * norm(rings), 0.25 * norm(sh), 0.3 * norm(lay(t, 6.6 - len(out) / SR, out)),
            0.35 * norm(grind(rng, t, 0.3, 3.6, 70, 1400, 4, 38, -0.5)), 0.4 * norm(braam(rng, t, 3.2, C / 4, 4.6, 0.3, 3)),
            0.9 * boom(rng, t, 0.1, 70, 22, 2.2), 0.9 * boom(rng, t, 2.9, 60, 20, 3.0), 0.5 * crack(rng, t, 0.1, 1.0, 1.4))
    return master(rng, x, 4.2, 0.42, 1.7), 9.5


def pm_step(seed, pitch):
    """He treads: a giant's weight on end stone, the floor knocking low and grit jumping from it, his mantle dragging, a
    chain's clink."""
    rng = np.random.default_rng(seed)
    t = t_(1.3)
    x = mix(boom(rng, t, 0.0, 62 * pitch, 30, 0.7), 0.5 * norm(crack(rng, t, 0.0, 0.6, 1.8 / pitch)),
            0.25 * norm(crumble(rng, t, 0.01, 0.35, 40, 500, 4000, 0.008, 3)), 0.25 * norm(whoosh(rng, t, 0.0, 0.45, 180 * pitch, 600, 0.6)),
            0.1 * norm(chains(rng, t, 0.06, 0.2, 2, 1200, 2600, 0.05, (1, 3))), 0.3 * norm(thump(rng, t, 0.0, 44 * pitch)))
    return master(rng, x, 2.0, 0.3, 1.5, False), 1.8


# ------------------------------------------------------------------ his blows

def pm_blink(seed):
    """He blinks (out and in): drawn backwards out of the world, a pop, and a warbling whoosh where he comes back, the
    air closing behind him."""
    rng = np.random.default_rng(seed)
    t = t_(2.2)
    out = reverb(rng, vwoop(rng, 0.55, 1300, 160, 26, 0.08), 0.35, 0.55, 6000)[::-1]
    at = len(out) / SR
    back = vwoop(rng, 1.0, 1100, 120, 22, 0.08)
    x = mix(norm(lay(t, 0.0, out)), norm(lay(t, at, back)), 0.6 * snap(rng, t, at, 0.5), 0.5 * boom(rng, t, at, 110, 50, 0.45),
            0.3 * norm(whoosh(rng, t, at - 0.15, 0.6, 400, 4000, 0.4)), 0.12 * norm(chains(rng, t, at, 0.3, 4)))
    return master(rng, x, 2.4, 0.32, 1.7), 2.2


def pm_strike(seed):
    """His claws brought down on stone: air torn by the sweep, the crack of the floor splitting, a heavy boom, debris
    flying, his claws scraping, his chains jumping."""
    rng = np.random.default_rng(seed)
    t = t_(2.6)
    hit = 0.22
    x = mix(0.6 * norm(whoosh(rng, t, 0.0, 0.3, 300, 3500, 0.45)), norm(crack(rng, t, hit, 1.0, 1.3)),
            boom(rng, t, hit, 80, 26, 1.6, 1.25), 0.45 * norm(crumble(rng, t, hit + 0.01, 1.4, 260, 500, 6000, 0.01, 8)),
            0.25 * norm(grind(rng, t, hit, 0.35, 900, 5000, 18, 70)), 0.22 * norm(chains(rng, t, hit, 0.4, 8)),
            0.3 * norm(shatter(rng, t, hit, 0.4, 14, 1800, 4000, 0.04)))
    return master(rng, x, 2.6, 0.3, 2.0), 2.6


def pm_wall(seed):
    """Walls ground up out of the floor: the stone tearing loose with a crack, a rumble swelling, grinding as it climbs,
    and the heavy settle as it stands."""
    rng = np.random.default_rng(seed)
    t = t_(3.4)
    x = mix(0.6 * norm(crack(rng, t, 0.0, 1.0, 1.6)), 0.6 * boom(rng, t, 0.0, 70, 30, 0.9), norm(grind(rng, t, 0.05, 2.4, 70, 2600, 7, 40, 1.0)),
            0.4 * norm(lowpass(noise(rng, len(t)), 90) * swell(t, 0.0, 1.8, 2.6)), boom(rng, t, 2.45, 64, 24, 1.4, 1.1),
            0.5 * norm(crack(rng, t, 2.45, 0.9, 1.8)), 0.35 * norm(crumble(rng, t, 0.2, 3.0, 300, 500, 5000, 0.01, 6, 1.0)))
    return master(rng, x, 2.8, 0.32, 1.8), 3.4


def pm_lift(seed):
    """Great blocks raised: they tear loose from the floor, then hang in a deep hum that climbs a fifth, warbling as the
    Endermen's hold does, grit falling from them, a violet shimmer over it."""
    rng = np.random.default_rng(seed)
    t = t_(3.2)
    k = np.clip((t - 0.2) / 2.4, 0, 1)
    f = C / 2 * 1.498 ** (k ** 1.3)
    hm = norm(tones(f * wobble(rng, len(t), 5, 0.012), 10, 1.4) + tones(f * 1.006 * wobble(rng, len(t), 4.3, 0.012), 10, 1.4))
    hm *= gate(t, 0.15, 2.9, 0.5, 0.6)
    shim = sum(np.sin(2 * np.pi * phase(f * r * wobble(rng, len(t), 9, 0.02))) for r in (8, 12, 16.2)) * swell(t, 0.3, 2.2, 3.0)
    x = mix(0.6 * norm(crack(rng, t, 0.0, 1.0, 1.5)), 0.5 * boom(rng, t, 0.0, 75, 32, 0.8), norm(hm),
            0.4 * norm(grind(rng, t, 0.0, 0.7, 90, 2200, 9, 42)), 0.3 * norm(crumble(rng, t, 0.3, 2.6, 160, 500, 5000, 0.009, 4, 1.0)),
            0.12 * norm(shim), 0.4 * norm(sub_hum(t, C / 4, 0.1, 3.0)))
    return master(rng, x, 2.8, 0.34, 1.5), 3.2


def pm_throw(seed):
    """A great block hurled: a grunt of his, a heavy whoosh of air shoved aside, its rumble going by, grit trailing."""
    rng = np.random.default_rng(seed)
    t = t_(2.0)
    g = syllable(rng, 0.4, 150, 105, "a", "o", 12, 0.05, 0.45, 0.6)
    x = mix(0.4 * norm(lay(t, 0.0, g)), norm(whoosh(rng, t, 0.05, 1.0, 160, 1400, 0.6)), 0.6 * norm(whoosh(rng, t, 0.1, 0.9, 900, 250, 0.4)),
            0.5 * norm(lowpass(noise(rng, len(t)), 100) * swell(t, 0.05, 0.45, 1.2)), 0.2 * norm(crumble(rng, t, 0.1, 0.9, 80, 600, 4000, 0.008)),
            0.35 * boom(rng, t, 0.05, 70, 40, 0.6))
    return master(rng, x, 2.4, 0.3, 1.7), 2.0


def pm_impact(seed):
    """It lands: a great block crushing stone, a boom, the crack of it splitting, rubble crashing down and settling, a
    rumble rolling away."""
    rng = np.random.default_rng(seed)
    t = t_(3.2)
    x = mix(boom(rng, t, 0.0, 78, 22, 2.2, 1.35), norm(crack(rng, t, 0.0, 1.0, 1.6)) + 0.7 * norm(crack(rng, t, 0.025, 0.8, 1.1)),
            0.6 * norm(crumble(rng, t, 0.01, 2.4, 700, 400, 6000, 0.012, 18, 1.8)),
            0.4 * norm(lowpass(noise(rng, len(t)), 110) * env(t - 0.02, 0.05, 0.8)), 0.25 * norm(shatter(rng, t, 0.0, 0.6, 20, 1600, 3800, 0.05)),
            0.7 * boom(rng, t, 0.3, 50, 22, 1.5))
    return master(rng, x, 2.8, 0.3, 2.1), 3.2


def pm_gaze(seed):
    """His stare: a piercing cluster rising out of a low swell, warbling ever wider and faster, flickering, and cut off at
    its height, the hall left ringing."""
    rng = np.random.default_rng(seed)
    t = t_(3.0)
    s = stare(rng, t, 0.0, 2.3, 230, 1650)
    low = ender(rng, t, C / 2 * 2 ** (np.clip(t / 2.3, 0, 1) * 0.5), "u", "a", np.clip(t / 2.3, 0, 1), 6, 0.03, 0.6, 0.0, 0.1, 60)
    low *= np.clip(t / 1.5, 0, 1) * np.clip((2.33 - t) / 0.04, 0, 1)
    x = mix(norm(s), 0.45 * norm(low), 0.3 * norm(riser(rng, t, 0.0, 2.3, 300, 9000, C / 2)), 0.4 * snap(rng, t, 2.3, 0.6),
            0.4 * boom(rng, t, 2.3, 80, 40, 0.6))
    return master(rng, x, 2.8, 0.36, 1.6), 3.0


def pm_door(seed):
    """The Last Door opening: a seal of stone breaking, great halves grinding apart, the void breathing out of it (a sub
    swelling, a hollow ringing, a tone that falls for ever), a bell tolling far off, the lost humming in the dark."""
    rng = np.random.default_rng(seed)
    t = t_(6.4)
    void = shepard(t, 3.4, 180, 7, 20, True, 1.2) * gate(t, 0.8, 5.4, 1.6, 1.6)
    hol = hollow(lowpass(noise(rng, len(t)), 1200), C / 2, 0.9) * gate(t, 0.5, 5.6, 1.0, 1.8)
    ch = lost(rng, t, [C, C * GS, C * 2, C * 2 * D_], 4.0, 1.4, "u", "o", 2, 1.8, 1.4, 0.05, 2000)
    x = mix(0.7 * norm(crack(rng, t, 0.0, 1.0, 2.0)), boom(rng, t, 0.0, 60, 20, 2.6, 1.2), 0.7 * norm(grind(rng, t, 0.1, 3.6, 50, 1400, 3, 33, -0.3)),
            0.5 * norm(sub_hum(t, C / 4, 0.3, 5.8)), 0.35 * norm(void), 0.4 * norm(hol), 0.3 * norm(ch),
            0.3 * norm(lowpass(halo(rng, t, 1.2, 98, 1.0, 2.2, 0.2), 3000)), 0.22 * norm(lowpass(halo(rng, t, 3.8, 92.5, 1.0, 2.0, 0.2), 2600)),
            0.45 * norm(braam(rng, t, 0.2, C / 4, 5.4, 0.45, 3)))
    return master(rng, x, 4.4, 0.45, 1.6, True, 5500, 0.4), 6.6


def pm_pull(seed):
    """The Door draws: a long rushing roar of air sucked into the void, ever stronger, a tone climbing for ever under
    it, grit and the voices of the lost dragged along."""
    rng = np.random.default_rng(seed)
    t = t_(5.0)
    up = shepard(t, 2.2, 260, 7, 20, False, 1.2) * gate(t, 0.0, 4.6, 1.2, 0.8)
    x = mix(norm(rush(rng, t, 0.0, 4.8, 180, 2600, 0.82)), 0.5 * norm(rush(rng, t, 0.3, 4.4, 90, 700, 0.85)), 0.3 * norm(up),
            0.25 * norm(crumble(rng, t, 0.6, 3.8, 220, 600, 5000, 0.008, 0, 1.0)),
            0.2 * norm(whisper(rng, t, 0.8, 3.4, 1500, 6000)), 0.35 * norm(sub_hum(t, C / 4, 0.0, 4.8)),
            0.3 * norm(riser(rng, t, 1.6, 3.0, 200, 6000, C / 4)))
    return master(rng, x, 3.0, 0.32, 1.6), 5.0


def pm_snare(seed):
    """A flyer torn out of the air: a warble that grabs, a whoosh plunging down, a chain snapping taut, the thud below."""
    rng = np.random.default_rng(seed)
    t = t_(2.0)
    grab = vwoop(rng, 0.7, 700, 140, 20, 0.07)
    x = mix(0.7 * norm(lay(t, 0.0, grab)), norm(whoosh(rng, t, 0.05, 0.75, 3500, 220, 0.4)),
            0.55 * norm(chains(rng, t, 0.05, 0.12, 6, 1500, 4000, 0.05, (4, 8))), 0.6 * snap(rng, t, 0.06, 0.7),
            0.9 * boom(rng, t, 0.78, 80, 32, 1.0), 0.4 * norm(crack(rng, t, 0.78, 0.7, 1.4)))
    return master(rng, x, 2.4, 0.3, 1.8), 2.0


def pm_phase(seed):
    """A turning of the fight: drawn backwards into it, his halo struck like a gong, the lost rising to a cry, a braam, his
    stone groaning, a boom."""
    rng = np.random.default_rng(seed)
    t = t_(5.4)
    sw = reverb(rng, vwoop(rng, 0.6, 1100, 140, 22, 0.08), 0.5, 0.6, 6000)[::-1]
    at = len(sw) / SR
    g = halo(rng, t, at, 69.3, 1.0, 3.5, 0.4) + 0.5 * km.gong(rng, t, at, 69.3, 1.0, 3.5)
    ch = lost(rng, t, [C * 2, C * 2 * E_, C * 2 * GS, C * 4], 2.4, at, "o", "a", 3, 0.6, 1.6, -0.08, 4500, 0.14)
    x = mix(0.5 * norm(lay(t, 0.0, sw)), norm(g), 0.5 * norm(ch), 0.6 * norm(braam(rng, t, at, C / 4, 3.8, 0.4, 6)),
            0.35 * norm(grind(rng, t, at, 2.6, 60, 1400, 4, 36)), 0.25 * norm(chains(rng, t, at, 0.8, 14, thin=1.8)),
            boom(rng, t, at, 66, 22, 2.6, 1.2))
    return master(rng, x, 3.8, 0.42, 1.7), 6.5


def pm_ultimate(seed):
    """His ultimate begins: everything drawn up into his stare, the lost crying out, a braam at its height, and at the top
    his scream and a boom as it falls, his halo crashing, stone bursting."""
    rng = np.random.default_rng(seed)
    t = t_(5.2)
    top = 2.6
    s = stare(rng, t, 0.0, top, 160, 1200)
    d = 1.8
    tt = t_(d)
    k = np.clip(tt / d, 0, 1)
    f0 = leap(k, 160, 300, 210, 0.18, 0.6, 1.3)
    sc = sum(norm(ender(rng, tt, f0 * (1 + 0.006 * i), "a", "o", k, 14, 0.05, 0.45, 0.3)) for i in range(2)) / 2
    sc = (sc + 0.5 * norm(ender(rng, tt, f0 / 2, "ah_low", "o", k, 9, 0.04, 0.8, 0.0, 0.05, 70))) * np.clip((d - tt) / (d * 0.4), 0, 1)
    ch = choir(rng, t, [C, C * E_, C * GS, C * 2, C * 2 * E_, C * 3], 2.4, 0.4, "o", "a", 3, 1.8, 1.4)
    x = mix(0.45 * norm(s), 0.6 * norm(riser(rng, t, 0.0, top, 150, 8000, C / 4)), 0.45 * norm(ch), 0.6 * norm(braam(rng, t, 0.3, C / 4, 4.2, 0.55, 8)),
            0.7 * norm(lay(t, top - 0.02, sc)), boom(rng, t, top, 72, 22, 2.2, 1.3), 0.4 * norm(halo(rng, t, top, 98, 1.0, 2.2, 0.6)),
            0.35 * norm(crumble(rng, t, top, 1.6, 400, 500, 6000, 0.01, 10)), 0.5 * crack(rng, t, top))
    return master(rng, x, 3.8, 0.38, 1.8), 6.8


def pm_wake(seed):
    """His coming, before the fight (it sounds several times while he comes): stone grinding far off, chains swaying in
    the dark, a hollow call out of the void, a bell a long way off."""
    rng = np.random.default_rng(seed)
    t = t_(4.4)
    call = syllable(rng, 1.7, 92, 69.3, "u", "o", 6, 0.035, 0.3, 0.7)
    call = hollow(call, C / 2, 0.6)
    pre = syllable(rng, 0.5, 70, 104, "o", "u", 8, 0.04, 0.3, 0.5, back=True, tail=0.6)
    far = mix(0.5 * norm(grind(rng, t, 0.0, 3.6, 50, 900, 3, 34)), 0.25 * norm(chains(rng, t, 0.3, 3.4, 9, 900, 2400, 0.09, (2, 4))),
              0.35 * norm(lay(t, 0.0, pre)), norm(lay(t, len(pre) / SR - 0.02, call)), 0.3 * norm(halo(rng, t, 0.05, 92.5, 1.0, 1.8, 0.1)),
              0.4 * norm(sub_hum(t, C / 4, 0.0, 4.2)), 0.5 * boom(rng, t, 0.0, 55, 22, 2.0))
    x = lowpass(far, 3200)
    return master(rng, x, 4.4, 0.5, 1.4, True, 4500, 0.25), 4.6


# Bound Stones (cubes of violet light on his chains, swung round him and flung), Pillars of the Anchor, Golden Rings, the
# Unfinished Spiral, and the upheaval of the arena at his ultimate's break

def pm_chains(seed):
    """The Bound Stones hauled up: he yanks his chains and they come rattling in, heavy links clanking over each other
    ever faster, until they jerk taut and shiver; under them the cubes kindle, a deep violet hum swelling and climbing a
    minor third."""
    rng = np.random.default_rng(seed)
    t = t_(3.0)
    taut = 1.95
    hauled = np.zeros(len(t))
    for i in range(18):
        s = 0.15 + (taut - 0.2) * (i / 18) ** (1 / 1.6)
        hauled += chains(rng, t, s, 0.0, 1, 600, 1700, 0.09, (2, 4)) * (0.6 + 0.4 * i / 18)
    rattle = chains(rng, t, 0.1, taut, 40, 1100, 3400, 0.06, (2, 5), 0.8)
    jerk = chains(rng, t, taut, 0.05, 12, 800, 3000, 0.08, (3, 7))
    shiver = chains(rng, t, taut + 0.03, 0.6, 30, 1600, 4200, 0.04, (1, 3), 2.4)
    k = np.clip((t - 0.3) / 2.4, 0, 1)
    hm = glow(rng, t, C / 2 * E_ ** (k ** 1.4), 0.18) * swell(t, 0.2, 2.4, 3.4)
    x = mix(0.5 * norm(snap(rng, t, 0.0, 1.0)), 0.6 * norm(chains(rng, t, 0.0, 0.2, 10, 800, 3000, 0.07, (3, 7))), 0.4 * boom(rng, t, 0.0, 80, 40, 0.5),
            0.55 * norm(hauled), 0.4 * norm(rattle), 0.6 * norm(jerk), 0.45 * norm(snap(rng, t, taut, 1.0)), 0.3 * norm(shiver),
            0.45 * boom(rng, t, taut, 90, 45, 0.5), 0.75 * norm(hm), 0.35 * norm(sub_hum(t, C / 4, 0.3, 2.7)))
    return master(rng, x, 2.6, 0.32, 1.5), 3.0


def pm_swing(seed):
    """A Bound Stone swung close past on its chain (it sounds several times in the sweep): a massive whoosh rising as it
    nears and dropping as it goes, its violet hum bending with it, the rumble of its weight, and the chain whipping after
    it, links rattling."""
    rng = np.random.default_rng(seed)
    t = t_(2.0)
    tc = 0.6
    u = (t - tc) / 0.22
    near = 1 / (1 + u ** 2)
    hm = glow(rng, t, C / 2 * GS * (1 + 0.07 * np.tanh(-2 * u)), 0.25) * near ** 1.2
    whip = tc + 0.16
    x = mix(norm(passby(rng, t, tc, 0.22, 260, 1400, 0.5)), 0.8 * norm(passby(rng, t, tc, 0.3, 90, 380, 0.6)), 0.5 * norm(hm),
            0.6 * norm(lowpass(noise(rng, len(t)), 110) * near ** 1.5), 0.45 * norm(snap(rng, t, whip, 1.0)),
            0.3 * norm(whoosh(rng, t, whip - 0.12, 0.2, 1500, 6000, 0.3)), 0.35 * norm(chains(rng, t, tc - 0.15, 0.4, 14, 1100, 3600, 0.05, (2, 5))),
            0.3 * norm(chains(rng, t, whip, 0.6, 10, 1000, 3000, 0.06, (2, 4), 2.0)))
    return master(rng, x, 2.2, 0.3, 1.7), 2.0


def pm_fling(seed):
    """He flings the Bound Stones: a grunt, his chains snapping loose one after another and links flying, a huge whoosh as
    the cubes are hurled away, their hum falling as they go, and a violet whine rising after them, warbling."""
    rng = np.random.default_rng(seed)
    t = t_(2.5)
    n = len(t)
    g = syllable(rng, 0.35, 160, 110, "a", "o", 12, 0.05, 0.45, 0.6)
    snaps = sum(a * norm(snap(rng, t, s, 1.0)) for s, a in [(0.08, 1.0), (0.13, 0.8), (0.21, 0.9)])
    k = np.clip((t - 0.15) / 1.6, 0, 1)
    go = 1 / (1 + (np.maximum(t - 0.2, 0) / 0.35) ** 2) * np.clip((t - 0.08) / 0.05, 0, 1)
    hm = glow(rng, t, C / 2 * GS * (1 - 0.12 * k ** 0.6), 0.2) * go
    f = 520 * (2400 / 520) ** (k ** 1.3)
    whine = sum(a * tones(f * r * wobble(rng, n, 9 + 16 * k, 0.01 + 0.03 * k, 0.4), 5, 1.2)
                for r, a in [(1, 1.0), (D_, 0.5), (1.498, 0.45), (0.5, 0.35)])
    whine *= (0.7 + 0.3 * np.sin(2 * np.pi * phase(f * 0.618))) * swell(t, 0.15, 1.2, 2.2)
    x = mix(0.35 * norm(lay(t, 0.0, g)), 0.7 * snaps, 0.45 * norm(chains(rng, t, 0.08, 0.25, 16, 1400, 4200, 0.05, (2, 5), 1.5)),
            0.25 * norm(chains(rng, t, 0.3, 1.2, 14, 800, 2400, 0.06, (3, 7), 1.2)), norm(whoosh(rng, t, 0.12, 1.1, 1200, 180, 0.5)),
            0.7 * norm(whoosh(rng, t, 0.1, 0.9, 300, 120, 0.6)), 0.4 * norm(hm), 0.4 * norm(whine), 0.5 * boom(rng, t, 0.12, 80, 40, 0.6),
            0.4 * norm(lowpass(noise(rng, n), 100) * swell(t, 0.1, 0.35, 1.4)))
    return master(rng, x, 2.6, 0.32, 1.7), 2.5


def pm_pillar(seed):
    """A Pillar of the Anchor (it sounds at each of its three pulses): a deep thump under the floor, muffled by the stone,
    then the floor cracks and a column of violet light tears up out of it, bright, crystalline and violent, grit thrown up
    and raining back."""
    rng = np.random.default_rng(seed)
    t = t_(3.0)
    up = 0.2
    under = lowpass(mix(boom(rng, t, 0.0, 58, 24, 1.0, 1.0), 0.6 * norm(crack(rng, t, 0.0, 1.0, 2.4)), 0.5 * norm(thump(rng, t, 0.0, 40))), 380)
    x = mix(norm(under), 0.7 * norm(crack(rng, t, up, 1.0, 1.0)), norm(column(rng, t, up, 1.6)), 0.6 * boom(rng, t, up, 95, 40, 0.8),
            0.35 * norm(crumble(rng, t, up + 0.02, 2.2, 360, 700, 7000, 0.008, 6, 1.4)),
            0.3 * norm(shatter(rng, t, up, 0.5, 30, 2500, 7000, 0.06)), 0.3 * norm(sub_hum(t, C / 4, 0.15, 1.6)))
    return master(rng, x, 2.8, 0.34, 1.9, True, 8000, 0.7), 3.0


def pm_ring(seed):
    """A Golden Ring cast (it sounds at each of the three): his cracked halo struck hard, its two halves buzzing, and a
    ring of gold spreading out across the floor, its metal shimmering ever faster and singing ever thinner as it goes."""
    rng = np.random.default_rng(seed)
    t = t_(2.5)
    n = len(t)
    k = np.clip(t / 2.2, 0, 1)
    sh = 0.55 + 0.45 * np.sin(2 * np.pi * phase(5 + 12 * k))
    f0 = C * 8 * (1 - 0.03 * k)
    out = sum(a * np.exp(-k * r * 0.6) * (np.sin(2 * np.pi * phase(f0 * r) + rng.random() * 6) + np.sin(2 * np.pi * phase(f0 * r * 1.004) + rng.random() * 6))
              for r, a in LINK)
    out *= sh * env(t - 0.02, 0.12, 0.8)
    x = mix(norm(halo(rng, t, 0.0, C * 2, 1.0, 1.3, 0.9)), 0.6 * norm(halo(rng, t, 0.002, C, 1.0, 1.6, 0.5)),
            0.5 * norm(band(noise(rng, n), 1500, 14000) * env(t, 0.0005, 0.015)), 0.5 * boom(rng, t, 0.0, 100, 50, 0.5),
            0.45 * norm(out), 0.3 * norm(whoosh(rng, t, 0.02, 1.3, 3000, 700, 0.4)), 0.15 * norm(chains(rng, t, 0.01, 0.3, 5)))
    return master(rng, x, 3.0, 0.38, 1.6, True, 8000, 0.6), 2.5


def pm_spiral(seed):
    """The Unfinished Spiral: stone torn up and ground round him in a vortex, circling ever faster and climbing, a wind
    howling as it draws everyone in, the lost swelling up out of it, all rising to the moment it bursts."""
    rng = np.random.default_rng(seed)
    t = t_(5.0)
    n = len(t)
    k = np.clip(t / 4.6, 0, 1)
    turn = np.sin(2 * np.pi * phase(0.7 + 2.8 * k ** 1.5))
    g = grind(rng, t, 0.0, 4.8, 70, 2600, 8, 40, 1.0)
    centre = 700 * (1 + 0.8 * turn) * 2 ** k
    passing = sweep_filter(rng, grind(rng, t, 0.3, 4.4, 300, 4000, 12, 55, 1.2), centre, centre * 0.7)
    hc = (500 + 900 * k ** 1.2) * (1 + 0.08 * turn)
    howl = sweep_filter(rng, noise(rng, n), hc, hc * 0.05) * swell(t, 0.2, 4.3, 5.0)
    ch = lost(rng, t, [C, C * E_, C * GS, C * 2, C * 2 * E_], 4.0, 0.5, "u", "a", 3, 3.2, 0.8, -0.04, 3200, 0.2)
    x = mix(norm(g) * (0.75 + 0.25 * turn), 0.6 * norm(passing), 0.7 * norm(rush(rng, t, 0.0, 4.8, 160, 2000, 0.9)), 0.35 * norm(howl),
            0.45 * norm(ch), 0.3 * norm(riser(rng, t, 2.6, 2.2, 200, 6000, C / 4)), 0.3 * norm(crumble(rng, t, 0.3, 4.4, 300, 600, 5000, 0.008, 6, 0.9)),
            0.4 * norm(sub_hum(t, C / 4, 0.0, 4.9)), 0.4 * boom(rng, t, 0.0, 70, 30, 0.8), 0.4 * norm(crack(rng, t, 0.0, 1.0, 1.6)),
            0.25 * norm(braam(rng, t, 1.5, C / 4, 3.4, 0.9, 5)))
    return master(rng, x, 3.0, 0.36, 1.6), 5.0


def pm_burst(seed):
    """The Spiral bursts: a breath drawn in, then its stone blown outward, cracking and exploding, a huge low boom, a blast
    of air, the lost scattered with a cry, and debris raining down a long while, a rumble rolling away."""
    rng = np.random.default_rng(seed)
    t = t_(3.2)
    n = len(t)
    hit = 0.12
    inhale = band(noise(rng, n), 300, 4000) * np.clip(t / hit, 0, 1) ** 3 * (t < hit)
    blast = saturate(band(noise(rng, n), 200, 9000) * env(t - hit, 0.002, 0.09), 3.0)
    cracks = norm(crack(rng, t, hit, 1.0, 2.0)) + 0.8 * norm(crack(rng, t, hit + 0.018, 1.0, 1.2)) + 0.6 * norm(crack(rng, t, hit + 0.05, 1.0, 1.5))
    x = mix(0.4 * norm(inhale), boom(rng, t, hit, 72, 22, 2.8, 1.4), 0.8 * boom(rng, t, hit + 0.05, 50, 20, 2.2), cracks, 0.6 * norm(blast),
            0.55 * norm(crumble(rng, t, hit + 0.03, 2.8, 1000, 400, 6500, 0.012, 24, 1.7)), 0.35 * norm(shatter(rng, t, hit, 0.8, 30, 1500, 4000, 0.05)),
            0.5 * norm(whoosh(rng, t, hit, 1.0, 2200, 250, 0.5)), 0.45 * norm(lowpass(noise(rng, n), 110) * env(t - hit - 0.02, 0.06, 1.0)),
            0.2 * norm(lost(rng, t, [C * 2, C * 2 * E_, C * 2 * GS], 0.25, hit, "a", "u", 2, 0.01, 0.9, 0.25, 3200, 0.2)),
            0.2 * norm(chains(rng, t, hit, 0.6, 10, thin=1.6)))
    return master(rng, x, 3.2, 0.34, 2.1), 3.5


def pm_upheaval(seed):
    """The arena torn apart (the break of his ultimate): the ground splits with a crack that runs away through it, a quake
    swells and shudders, cliffs grind up out of the floor groaning, a crack of thunder over it all, and then the deep
    collapse, stone crashing down a long while, the hall left shaking. Stone, quake and thunder any Elder might share,
    tuned here to his C sharp and hung with his chains."""
    rng = np.random.default_rng(seed)
    t = t_(5.6)
    n = len(t)
    run = np.zeros(n)
    for i in range(14):
        s = 0.02 + 0.75 * (i / 14) ** 1.3
        run += lowpass(crack(rng, t, s, 1.0 - 0.05 * i, 1.0 + 0.12 * i), 9000 - 500 * i)
    clap, fall = 2.3, 3.4
    cliffs = norm(grind(rng, t, 0.5, 2.9, 50, 2200, 5, 36, 1.2)) + 0.7 * norm(grind(rng, t, 0.9, 2.5, 60, 1800, 6, 49, 0.8))
    x = mix(norm(crack(rng, t, 0.0, 1.0, 2.0)), boom(rng, t, 0.0, 64, 22, 2.0, 1.2), 0.5 * norm(run), 0.9 * norm(quake(rng, t, 0.0, 5.4, 9)),
            0.8 * norm(cliffs), 0.35 * norm(hollow(lowpass(noise(rng, n), 800), C / 2, 0.85) * gate(t, 0.3, 5.0, 1.0, 1.5)),
            0.4 * norm(braam(rng, t, 1.2, C / 4, 3.0, 0.75, 5)), 0.2 * norm(chains(rng, t, 0.2, 3.6, 24, 1000, 3000, 0.06, (2, 5), 1.0)),
            0.9 * norm(thunder(rng, t, clap, 2.4)), 0.8 * boom(rng, t, clap, 80, 30, 1.2), boom(rng, t, fall, 56, 20, 3.2, 1.3),
            0.7 * norm(crack(rng, t, fall, 1.0, 2.2)), 0.6 * norm(crumble(rng, t, fall + 0.02, 2.0, 1400, 300, 5500, 0.014, 30, 1.5)),
            0.5 * norm(lowpass(noise(rng, n), 100) * env(t - fall, 0.1, 1.2)))
    return master(rng, x, 4.2, 0.4, 1.8), 6.0


SOUNDS = {
    "pale_mason_voice1": lambda: pm_voice(301, 1.0),
    "pale_mason_voice2": lambda: pm_voice(302, 0.89),
    "pale_mason_hurt1": lambda: pm_hurt(311, 1.0, 277),
    "pale_mason_hurt2": lambda: pm_hurt(312, 0.9, 247),
    "pale_mason_hurt3": lambda: pm_hurt(313, 1.12, 311),
    "pale_mason_ambient1": lambda: pm_ambient(321, C / 2, [(0.3, 62, 88, True), (1.6, 82, 66, False), (2.7, 70, 58, False)]),
    "pale_mason_ambient2": lambda: pm_ambient(322, C / 2 * B_ / 2, [(0.2, 74, 58, False), (1.3, 55, 78, True), (2.9, 66, 52, False)]),
    "pale_mason_roar": lambda: pm_roar(331),
    "pale_mason_death": lambda: pm_death(341),
    "pale_mason_step1": lambda: pm_step(351, 1.0),
    "pale_mason_step2": lambda: pm_step(352, 0.88),
    "pale_mason_blink": lambda: pm_blink(361),
    "pale_mason_strike": lambda: pm_strike(371),
    "pale_mason_wall": lambda: pm_wall(381),
    "pale_mason_lift": lambda: pm_lift(391),
    "pale_mason_throw": lambda: pm_throw(401),
    "pale_mason_impact": lambda: pm_impact(411),
    "pale_mason_gaze": lambda: pm_gaze(421),
    "pale_mason_door": lambda: pm_door(431),
    "pale_mason_pull": lambda: pm_pull(441),
    "pale_mason_snare": lambda: pm_snare(451),
    "pale_mason_phase": lambda: pm_phase(461),
    "pale_mason_ultimate": lambda: pm_ultimate(471),
    "pale_mason_wake": lambda: pm_wake(481),
    "pale_mason_chains": lambda: pm_chains(491),
    "pale_mason_swing": lambda: pm_swing(501),
    "pale_mason_fling": lambda: pm_fling(511),
    "pale_mason_pillar": lambda: pm_pillar(521),
    "pale_mason_ring": lambda: pm_ring(531),
    "pale_mason_spiral": lambda: pm_spiral(541),
    "pale_mason_burst": lambda: pm_burst(551),
    "pale_mason_upheaval": lambda: pm_upheaval(561),
}

# How loud each kind of sound of his should be at its loudest (dB, short-term loudness, as knot_mother.py): his blows and
# his great moments land hardest, his voice a little under, his steps and his ambience well under
LOUDNESS = {"strike": -9, "impact": -8.5, "door": -9, "phase": -9, "ultimate": -9, "roar": -9, "death": -9.5, "pull": -9.5,
            "wall": -10, "gaze": -10, "wake": -10, "blink": -10.5, "throw": -10.5, "snare": -10.5, "lift": -11, "voice": -11,
            "hurt": -11, "step": -15, "ambient": -16,
            # his new blows: the bursts and the upheaval land as his strike does, the pillars and rings (heard three times
            # over) a little under, the chains and the swings (heard many times in the sweep) as his lift
            "upheaval": -9, "burst": -9, "spiral": -9.5, "pillar": -10, "ring": -10.5, "fling": -10.5, "swing": -11, "chains": -11}


def save(name, x, keep):
    x = x[: int(SR * keep)].copy()
    target = next((v for k, v in LOUDNESS.items() if k in name), -11)
    x = norm(x, 0.97)
    gain = min(2.0, 10 ** ((target - km.loudness(x)) / 20))
    x = x * gain
    # a soft limit over full scale: the peaks of a blow give a little rather than clip
    x = np.where(np.abs(x) > 0.8, np.sign(x) * (0.8 + 0.17 * np.tanh((np.abs(x) - 0.8) / 0.17)), x)
    f = int(SR * min(0.4, keep * 0.15))
    x[-f:] *= np.linspace(1, 0, f) ** 2
    x[:32] *= np.linspace(0, 1, 32)
    with tempfile.TemporaryDirectory() as tmp:
        wav = os.path.join(tmp, name + ".wav")
        with wave.open(wav, "wb") as w:
            w.setnchannels(1)
            w.setsampwidth(2)
            w.setframerate(SR)
            w.writeframes((x * 32767).astype(np.int16).tobytes())
        subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", wav, "-c:a", "libvorbis", "-q:a", "5", os.path.join(OUT, name + ".ogg")],
                       check=True)
    return x


def main():
    preview = sys.argv[sys.argv.index("--preview") + 1] if "--preview" in sys.argv else None
    only = [a for a in sys.argv[1:] if a.startswith("pale_mason_")]
    os.makedirs(OUT, exist_ok=True)
    for name, make in SOUNDS.items():
        if only and name not in only:
            continue
        x, keep = make()
        y = save(name, x, keep)
        print(f"{name}: {len(y) / SR:.2f} s, loudness {km.loudness(y):.1f}, peak {np.max(np.abs(y)):.2f}, bands {km.bands(y)}")
        if preview:
            os.makedirs(preview, exist_ok=True)
            km.spectrogram(y, os.path.join(preview, name + ".png"))


if __name__ == "__main__":
    main()
