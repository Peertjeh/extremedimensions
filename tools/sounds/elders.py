"""The Elders' own sounds: hurt (3), ambient (2), death, steps (2) and roar for each, made from noise and tones; and,
since 3.43, the Ferryman's and the Last Weaver's own blows (four each).

The Matriarch is living wood: splitting bark, a deep groan through her mask, leaves. The Ferryman is a cracked bell,
chains and the river under his boat, and a hollow moan. The Last Weaver is glass and thread: shards, snapping strings,
an airy breath, a chorus of thin high voices. Deterministic (fixed seeds). Needs numpy and ffmpeg (libvorbis). With
--preview DIR it also writes a spectrogram of each sound there (needs Pillow). Name sounds to make only those.

usage: python3 tools/sounds/elders.py [--preview DIR] [<elder>_<name>...]
"""
import os
import subprocess
import sys
import tempfile
import wave

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import knot_mother as km  # noqa: E402  (a few general tools: a moving band-pass, loudness, spectrograms)

SR = 44100
OUT = os.path.join(os.path.dirname(__file__), "..", "..", "src/client/resources/assets/extremedimensions/sounds/elder")


def t_(d):
    return np.arange(int(SR * d)) / SR


def band(x, lo, hi, soft=0.15):
    """FFT band-pass with soft edges."""
    n = len(x)
    spec = np.fft.rfft(x)
    f = np.fft.rfftfreq(n, 1 / SR)
    lo = max(lo, 1.0)
    m = np.clip((f - lo * (1 - soft)) / (lo * soft), 0, 1) * np.clip((hi * (1 + soft) - f) / (hi * soft), 0, 1)
    return np.fft.irfft(spec * m, n)


def env(t, a, d):
    """Attack then exponential decay; zero before the start (t < 0)."""
    return np.where(t < 0, 0.0, np.minimum(1, t / max(a, 1e-4)) * np.exp(-np.maximum(0, t - a) / d))


def formant(x, freqs):
    return sum(band(x, f * 0.85, f * 1.15) * g for f, g in freqs)


def saw(freq):
    return 2 * ((np.cumsum(freq) / SR) % 1) - 1


def reverb(rng, x, tail=1.4, mix=0.3, bright=6000):
    x = x.copy()
    f = int(SR * 0.2)
    x[-f:] *= np.linspace(1, 0, f) ** 2
    n = int(SR * tail)
    ir = rng.standard_normal(n) * np.exp(-np.arange(n) / (SR * tail / 5))
    ir = band(ir, 80, bright)
    ir[: int(SR * 0.012)] = 0
    wet = np.convolve(x, ir)[: len(x) + n]
    wet = np.pad(wet, (0, len(x) + n - len(wet)))
    wet /= np.max(np.abs(wet)) + 1e-9
    out = np.zeros(len(x) + n)
    out[: len(x)] = x
    return out * (1 - mix) + wet * mix * np.max(np.abs(x))


def save(name, x, keep, target=None):
    """Peaks at 0.9. With a target (dB, short-term loudness as knot_mother.loudness), it is brought to that loudness
    instead, its peaks softly held under 0.9, so a blow with one great transient sits as loud as the rest of the set."""
    x = x[: int(SR * keep)].copy()
    x = x / (np.max(np.abs(x)) + 1e-9) * 0.9
    if target is not None:
        x = x * min(2.0, 10 ** ((target - km.loudness(x)) / 20))
        x = np.where(np.abs(x) > 0.75, np.sign(x) * (0.75 + 0.15 * np.tanh((np.abs(x) - 0.75) / 0.15)), x)
    f = int(SR * min(0.3, keep * 0.2))
    x[-f:] *= np.linspace(1, 0, f) ** 2
    with tempfile.TemporaryDirectory() as tmp:
        wav = os.path.join(tmp, name + ".wav")
        with wave.open(wav, "wb") as w:
            w.setnchannels(1)
            w.setsampwidth(2)
            w.setframerate(SR)
            w.writeframes((x * 32767).astype(np.int16).tobytes())
        subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", wav, "-c:a", "libvorbis", "-q:a", "4", os.path.join(OUT, name + ".ogg")],
                       check=True)
    return x


def noise(rng, n):
    return rng.standard_normal(n)


# ------------------------------------------------------------------ the Matriarch

def m_crack(rng, t, at, bright=1.0):
    c = band(noise(rng, len(t)), 700 * bright, 4200 * bright) * env(t - at, 0.002, 0.035)
    return c + 0.6 * band(noise(rng, len(t)), 1500, 6000) * env(t - at - 0.05, 0.002, 0.02)


def m_groan(rng, t, at, f0, fall, dur):
    f = f0 * np.exp(-np.maximum(0, t - at) * fall)
    return formant(saw(f), [(380, 1.0), (720, 0.6), (2400, 0.15)]) * env(t - at, 0.12, dur)


def m_creak(rng, t, at, rate, dur):
    r = rate + 20 * t
    gate = 0.5 + 0.5 * np.sign(np.sin(2 * np.pi * np.cumsum(r) / SR))
    return band(noise(rng, len(t)), 180, 900) * gate * env(t - at, 0.05, dur)


def m_leaves(rng, t, at, dur):
    return band(noise(rng, len(t)), 2500, 9000) * env(t - at, 0.08, dur) * (0.6 + 0.4 * np.sin(2 * np.pi * 9 * t))


def matriarch_hurt(seed, pitch):
    rng = np.random.default_rng(seed)
    t = t_(1.5)
    x = 1.0 * m_crack(rng, t, 0.0) + 0.55 * m_creak(rng, t, 0.06, 38 * pitch, 0.35) + 0.9 * m_groan(rng, t, 0.08, 74 * pitch, 0.35, 0.55)
    x += 0.18 * m_leaves(rng, t, 0.1, 0.4)
    return reverb(rng, x, 1.2, 0.28, 5000), 1.9


def matriarch_ambient(seed):
    rng = np.random.default_rng(seed)
    t = t_(3.0)
    breath = band(noise(rng, len(t)), 120, 700) * np.sin(np.pi * np.clip(t / 2.6, 0, 1)) ** 2
    x = 0.7 * m_creak(rng, t, 0.3, 14, 1.2) + 0.6 * breath + 0.25 * m_leaves(rng, t, 0.8, 1.0) + 0.35 * m_groan(rng, t, 0.9, 58, 0.1, 1.0)
    return reverb(rng, x, 1.6, 0.35, 4500), 3.4


def matriarch_death(seed):
    rng = np.random.default_rng(seed)
    t = t_(4.5)
    x = sum(m_crack(rng, t, at, 0.8) * g for at, g in [(0, 1), (0.35, 0.8), (0.9, 0.9), (1.6, 0.7), (2.2, 0.6)])
    x += 1.0 * m_groan(rng, t, 0.05, 70, 0.28, 2.2) + 0.6 * m_creak(rng, t, 0.2, 24, 1.8)
    x += 0.5 * band(noise(rng, len(t)), 30, 160) * env(t - 0.8, 0.4, 1.5)
    x += 0.35 * m_leaves(rng, t, 0.6, 2.0)
    return reverb(rng, x, 2.6, 0.4, 4000), 5.5


def matriarch_step(seed, pitch):
    rng = np.random.default_rng(seed)
    t = t_(0.8)
    thud = np.sin(2 * np.pi * 46 * pitch * np.exp(-t * 3) * t) * env(t, 0.004, 0.18)
    thud += 0.6 * band(noise(rng, len(t)), 40, 300) * env(t, 0.002, 0.08)
    crunch = band(noise(rng, len(t)), 900, 3500) * env(t - 0.02, 0.002, 0.12) * (rng.random(len(t)) > 0.7)
    return reverb(rng, 1.0 * thud + 0.35 * crunch, 0.8, 0.2, 3000), 1.0


def matriarch_roar(seed):
    rng = np.random.default_rng(seed)
    t = t_(2.6)
    f = 60 + 38 * np.sin(np.pi * np.clip(t / 2.2, 0, 1)) ** 1.5
    v = formant(saw(f) + 0.5 * saw(f * 1.01), [(330, 1.0), (680, 0.8), (1100, 0.4), (2600, 0.12)]) * env(t, 0.25, 1.4)
    x = 1.0 * v + 0.7 * m_crack(rng, t, 0.0, 0.7) + 0.3 * band(noise(rng, len(t)), 200, 2000) * env(t, 0.3, 1.0) + 0.2 * m_leaves(rng, t, 0.2, 1.2)
    return reverb(rng, x, 2.0, 0.35, 4500), 3.4


# ------------------------------------------------------------------ the Ferryman

PARTIALS = [(0.5, .55, 1.4), (1.0, .8, 1.0), (1.183, .5, .8), (1.506, .35, .6), (2.0, .4, .5), (2.74, .25, .35), (3.76, .18, .25), (5.4, .1, .15)]


def f_bell(rng, t, at, base, strength=1.0, ring=1.0):
    x = sum(a * np.sin(2 * np.pi * base * r * t + rng.random() * 6) * env(t - at, 0.002, k * ring) for r, a, k in PARTIALS)
    return strength * x * (1 + 0.25 * np.sin(2 * np.pi * 5.5 * t))


def f_chain(rng, t, start, spread, count, low=2200):
    x = np.zeros(len(t))
    for _ in range(count):
        s = start + rng.random() * spread
        i = int(s * SR)
        n = int(0.03 * SR)
        if i >= len(x):
            continue
        ping = np.sin(2 * np.pi * (low + rng.random() * 2600) * t_(0.03)) * np.exp(-t_(0.03) / 0.006)
        seg = x[i:i + n]
        seg += ping[: len(seg)] * (0.4 + rng.random() * 0.6)
    return x


def f_moan(rng, t, at, f0, dur):
    m = np.sin(2 * np.pi * f0 * np.exp(-np.maximum(0, t - at) * 0.25) * t) * env(t - at, 0.25, dur) * (0.6 + 0.4 * np.sin(2 * np.pi * 4 * t))
    return m + 0.4 * formant(noise(rng, len(t)), [(300, 1), (600, .5)]) * env(t - at, 0.25, dur * 0.8)


def f_water(rng, t, at, dur, rate=1.3):
    w = band(noise(rng, len(t)), 150, 1400) * (0.5 + 0.5 * np.sin(2 * np.pi * rate * t + rng.random() * 6)) ** 2
    return w * env(t - at, 0.3, dur)


def ferryman_hurt(seed, base):
    rng = np.random.default_rng(seed)
    t = t_(1.8)
    x = f_bell(rng, t, 0, base) + 0.55 * f_chain(rng, t, 0.03, 0.45, 14) + 0.5 * f_moan(rng, t, 0.15, 92 * base / 196, 0.6)
    return reverb(rng, x, 2.2, 0.38, 4000), 2.3


def ferryman_ambient(seed):
    rng = np.random.default_rng(seed)
    t = t_(3.2)
    x = 0.35 * f_bell(rng, t, 0.4, 130, ring=1.6) + 0.8 * f_water(rng, t, 0, 2.5) + 0.3 * f_chain(rng, t, 1.2, 1.2, 5, 1600)
    x += 0.25 * f_moan(rng, t, 1.0, 80, 1.2)
    return reverb(rng, x, 2.6, 0.45, 3500), 3.6


def ferryman_death(seed):
    rng = np.random.default_rng(seed)
    t = t_(5.0)
    x = f_bell(rng, t, 0, 196) + 0.8 * f_bell(rng, t, 1.3, 174, ring=1.4) + 0.65 * f_bell(rng, t, 2.9, 146, ring=1.8)
    x += 0.6 * f_chain(rng, t, 0.2, 2.6, 40, 1200) + 0.7 * f_moan(rng, t, 0.3, 88, 2.6) + 0.6 * f_water(rng, t, 2.0, 2.0, 0.8)
    return reverb(rng, x, 3.0, 0.45, 3500), 6.0


def ferryman_step(seed, pitch):
    rng = np.random.default_rng(seed)
    t = t_(0.9)
    creak = band(noise(rng, len(t)), 200 * pitch, 800 * pitch) * env(t, 0.03, 0.2) * (0.5 + 0.5 * np.sign(np.sin(2 * np.pi * 30 * t)))
    slosh = band(noise(rng, len(t)), 200, 1800) * env(t - 0.05, 0.08, 0.3)
    thud = np.sin(2 * np.pi * 55 * pitch * t) * env(t, 0.005, 0.15)
    return reverb(rng, 0.6 * creak + 0.8 * slosh + 0.7 * thud, 1.0, 0.3, 3000), 1.1


def ferryman_roar(seed):
    rng = np.random.default_rng(seed)
    t = t_(2.8)
    f = 68 + 10 * np.sin(np.pi * np.clip(t / 2.4, 0, 1))
    horn = formant(saw(f) + 0.6 * saw(f * 0.995) + 0.4 * saw(f * 2.003), [(280, 1.0), (560, 0.7), (900, 0.3)]) * env(t, 0.3, 1.5)
    x = 1.0 * horn + 0.7 * f_bell(rng, t, 0.0, 160) + 0.4 * f_chain(rng, t, 0.1, 0.8, 18)
    return reverb(rng, x, 2.8, 0.42, 3500), 3.6


# ------------------------------------------------------------------ the Last Weaver

def w_glass(rng, t, at, spread, count, lo=1800, span=4200, life=0.08):
    x = np.zeros(len(t))
    for _ in range(count):
        s = at + rng.random() * spread
        f = lo + rng.random() * span
        g = sum(a * np.sin(2 * np.pi * f * r * t + rng.random() * 6) for r, a in [(1, 1), (2.32, .5), (4.25, .3)])
        x += g * env(t - s, 0.001, life + rng.random() * life * 2) * (0.4 + rng.random() * 0.6)
    return x


def w_snap(rng, t, at, freq):
    n = int(SR / freq)
    buf = rng.standard_normal(n)
    pl = np.zeros(int(SR * 0.5))
    for i in range(len(pl)):
        pl[i] = buf[i % n]
        buf[i % n] = 0.5 * (buf[i % n] + buf[(i + 1) % n]) * 0.996
    x = np.zeros(len(t))
    s0 = int(at * SR)
    seg = x[s0:s0 + len(pl)]
    seg += pl[: len(seg)]
    return x * env(t - at, 0.001, 0.12)


def w_breath(rng, t, at, dur, lo=900, hi=5000):
    return band(noise(rng, len(t)), lo, hi) * env(t - at, 0.15, dur) * (0.6 + 0.4 * np.sin(2 * np.pi * (6 - 3 * t) * t))


def weaver_hurt(seed, pitch):
    rng = np.random.default_rng(seed)
    t = t_(1.3)
    x = 0.9 * w_glass(rng, t, 0, 0.18, 9, 1800 * pitch) + 0.5 * band(noise(rng, len(t)), 3000, 11000) * env(t, 0.001, 0.05)
    x += 0.7 * w_snap(rng, t, 0.04, 330 * pitch) + 0.3 * w_breath(rng, t, 0.12, 0.35)
    return reverb(rng, x, 1.8, 0.4, 9000), 1.8


def weaver_ambient(seed):
    rng = np.random.default_rng(seed)
    t = t_(3.0)
    x = 0.6 * w_glass(rng, t, 0.2, 2.2, 7, 1400, 2600, life=0.4) + 0.5 * w_breath(rng, t, 0.3, 1.4, 600, 3500)
    return reverb(rng, x, 2.2, 0.5, 9000), 3.4


def weaver_death(seed):
    rng = np.random.default_rng(seed)
    t = t_(4.5)
    x = 1.0 * w_glass(rng, t, 0, 1.6, 60, 1200, 6000, life=0.12) + 0.5 * band(noise(rng, len(t)), 2500, 12000) * env(t, 0.01, 0.6)
    x += sum(0.5 * w_snap(rng, t, at, f) for at, f in [(0.1, 330), (0.5, 294), (0.9, 262), (1.4, 220)])
    sweep = band(noise(rng, len(t)), 500, 6000) * env(t - 1.0, 1.2, 1.2)
    x += 0.4 * sweep
    return reverb(rng, x, 3.0, 0.5, 9000), 5.5


def weaver_step(seed, pitch):
    rng = np.random.default_rng(seed)
    t = t_(0.7)
    x = 0.8 * w_glass(rng, t, 0, 0.03, 3, 2400 * pitch, 1600, life=0.06) + 0.5 * np.sin(2 * np.pi * 120 * pitch * t) * env(t, 0.004, 0.08)
    return reverb(rng, x, 1.0, 0.35, 8000), 0.9


def weaver_roar(seed):
    rng = np.random.default_rng(seed)
    t = t_(2.6)
    x = np.zeros(len(t))
    for k, base in enumerate([620, 780, 930, 1170]):
        vib = 1 + 0.012 * np.sin(2 * np.pi * (5.5 + k * 0.7) * t)
        f = base * vib * (1 + 0.15 * np.sin(np.pi * np.clip(t / 2.2, 0, 1)))
        x += np.sin(2 * np.pi * np.cumsum(f) / SR) * 0.3
    x *= env(t, 0.2, 1.3)
    x += 0.6 * w_glass(rng, t, 0, 0.5, 14) + 0.3 * w_breath(rng, t, 0, 1.2, 1500, 7000)
    return reverb(rng, x, 2.4, 0.45, 9000), 3.3


# ------------------------------------------------------------------ their blows of 3.43
# The Last Weaver's warp and its fall, her spindle and her needles; the Ferryman's undertow, his geyser of soul fire, his
# oar and his drowned bell. Each is built from the Elder's own palette above, with a few more tools: a moving band-pass,
# a low blow, bubbles and soul fire for him, threads drawn taut and struck strings for her.

def nrm(x):
    return x / (np.max(np.abs(x)) + 1e-9)


def put(x, at, y, gain=1.0):
    """y laid into x from second `at` on (cut at x's end)."""
    i = int(at * SR)
    n = min(len(y), len(x) - i)
    if n > 0:
        x[i:i + n] += y[:n] * gain
    return x


def ramp(t, times, levels):
    """A shape drawn through points (times in seconds, levels), flat outside them."""
    return np.interp(t, times, levels)


def sweep(rng, t, at, dur, f_from, f_to, width=0.3):
    """Band noise whose centre glides from f_from to f_to over dur seconds (air or water swept along); its width is a
    share of the centre."""
    k = np.clip((t - at) / dur, 0, 1)
    centre = f_from * (f_to / f_from) ** k
    return km.sweep_filter(rng, noise(rng, len(t)), centre, centre * width)


def boom(rng, t, at, f0=70, f1=34, dur=1.0):
    """A heavy low blow: a tone dropping in pitch, its overtones (so it is heard on small speakers too) and a dull
    click."""
    tt = t - at
    f = f1 + (f0 - f1) * np.exp(-np.maximum(tt, 0) / (dur * 0.15))
    ph = np.cumsum(f) / SR
    x = np.sin(2 * np.pi * ph) * env(tt, 0.003, dur * 0.3) + 0.5 * np.sin(4 * np.pi * ph) * env(tt, 0.003, dur * 0.14)
    x += 0.25 * np.sin(6 * np.pi * ph) * env(tt, 0.003, dur * 0.08)
    return x + 0.5 * band(noise(rng, len(t)), 100, 1500) * env(tt, 0.001, 0.02)


# the Weaver's own: threads drawn taut, strings struck, glass that rings, her choir

W_CHORD = [622.3, 784.0, 932.3, 1174.7]   # her choir's chord (as her roar: E flat, G, B flat, D)


def w_thread(rng, d, f0, f1, ring=0.12):
    """One thread drawn taut (d seconds, then its ring; laid with put): a thin tone rising from f0 to f1 as it tightens,
    rubbed (a flutter), with the glassy overtone of her threads; taut, it sings on a moment at f1."""
    t = t_(d + ring * 4)
    k = np.clip(t / d, 0, 1)
    f = f0 * (f1 / f0) ** (k ** 0.6)
    ph = np.cumsum(f) / SR
    x = np.sin(2 * np.pi * ph) + 0.3 * np.sin(4 * np.pi * ph + 1) + 0.22 * np.sin(2 * np.pi * 2.32 * ph + 2)
    rub = 0.7 + 0.3 * np.sin(2 * np.pi * (18 + 27 * rng.random()) * t)
    shape = np.where(t < d, np.clip(t / (d * 0.6), 0, 1) ** 1.5, np.exp(-(t - d) / ring))
    return x * np.where(t < d, rub, 1.0) * shape


def w_string(rng, t, at, f, decay=0.9, tilt=1.1):
    """A taut thread struck hard: a string's harmonics (the high ones dying first), a little stretched, as glass rings."""
    tt = t - at
    x = np.zeros(len(t))
    for h in range(1, 14):
        fh = f * h * (1 + 0.0007 * h * h)
        if fh > 14000:
            break
        x += np.sin(2 * np.pi * fh * tt + rng.random() * 6) * env(tt, 0.0008, decay / h ** 0.6) / h ** tilt
    return x


def w_ring(rng, t, at, base, life=0.9):
    """A great glass needle ringing: the inharmonic partials of her glass, each a pair a little apart, beating."""
    tt = t - at
    x = np.zeros(len(t))
    for r, a in [(1, 1), (2.32, .55), (4.25, .35), (6.1, .2)]:
        for det in (1.0, 1.0 + rng.uniform(0.002, 0.005)):
            x += a * np.sin(2 * np.pi * base * r * det * tt + rng.random() * 6) * env(tt, 0.001, life / r ** 0.5)
    return x


def w_choir(rng, t, notes, bend, rates=(5.5, 6.2, 6.9, 7.6)):
    """Her thin high voices, as in her roar: pure tones on a chord, each with its own vibrato, all bent by `bend` (a
    factor over time)."""
    x = np.zeros(len(t))
    for k, base in enumerate(notes):
        vib = 1 + 0.012 * np.sin(2 * np.pi * rates[k % len(rates)] * t + rng.random() * 6)
        x += np.sin(2 * np.pi * np.cumsum(base * vib * bend) / SR) * 0.3
    return x


def w_whistle(rng, t, at, dur, f_hi, f_lo):
    """A needle falling: a whistle and its rush of air, dropping from f_hi to f_lo and ever louder till it strikes."""
    k = np.clip((t - at) / dur, 0, 1)
    f = f_hi * (f_lo / f_hi) ** (k ** 1.4) * (1 + 0.01 * np.sin(2 * np.pi * 7 * t))
    tone = np.sin(2 * np.pi * np.cumsum(f) / SR)
    air = km.sweep_filter(rng, noise(rng, len(t)), f, f * 0.15)
    return (tone + 1.6 * air / (np.std(air) + 1e-9) * 0.35) * k ** 2.5 * (t >= at) * (t < at + dur)


def weaver_warp(seed):
    """The warp drawn (2.5 s): threads drawn taut by the hundred, each a thin rubbed tone rising as it tightens, ever
    more of them and ever higher; a glassy weave building among them, her choir and a rush of air rising to the moment it
    falls."""
    rng = np.random.default_rng(seed)
    t = t_(2.5)
    top = 2.2
    threads = np.zeros(len(t))
    for _ in range(150):
        s = top * 0.95 * rng.random() ** 0.6   # ever more of them as it builds
        k = s / top
        d = 0.16 + rng.random() * 0.3
        f0 = (520 + 1500 * k) * (0.8 + 0.5 * rng.random())
        put(threads, s, w_thread(rng, d, f0, f0 * (1.25 + 0.5 * rng.random())), (0.3 + 0.7 * rng.random()) * (0.35 + 0.65 * k))
    weave = sum(w_glass(rng, t, a, 0.55, c, 1600 + 900 * i, 4200, life=0.05) for i, (a, c) in enumerate([(0.3, 5), (0.85, 10), (1.4, 18), (1.85, 30)]))
    rise = 0.72 * (1 / 0.72) ** (np.clip(t / top, 0, 1) ** 1.3)
    voices = w_choir(rng, t, W_CHORD, rise) * ramp(t, [0, top, top + 0.12], [0, 1, 0]) ** 2
    trem = 0.75 + 0.25 * np.sin(2 * np.pi * np.cumsum(4 + 22 * np.clip(t / top, 0, 1) ** 2) / SR)
    air = sweep(rng, t, 0, top, 900, 7000, 0.35) * ramp(t, [0, top, top + 0.1], [0, 1, 0]) ** 2
    x = (1.0 * nrm(threads) + 0.45 * nrm(weave) + 0.5 * nrm(voices) * trem + 0.35 * nrm(air)) * ramp(t, [top, top + 0.15], [1, 0.4])
    return reverb(rng, x, 1.8, 0.4, 9000), 2.5


def weaver_warpfall(seed):
    """The warp falls (2.5 s): the grid of threads slams onto the floor. A rush down, then a hard crack flammed wide
    over many strings at once, the strings thrumming low and rattling against the stone, snaps among them, a thud of
    the floor taking it, and shards of glass."""
    rng = np.random.default_rng(seed)
    t = t_(2.5)
    at = 0.12
    rush = sweep(rng, t, 0, at, 6000, 1800, 0.5) * ramp(t, [0, at, at + 0.01], [0, 1, 0]) ** 2
    crack = np.zeros(len(t))
    for i in range(7):
        s = at + 0.035 * rng.random() ** 1.5
        crack += noise(rng, len(t)) * env(t - s, 0.0002, 0.004 + 0.005 * rng.random()) * (1.0 if i == 0 else 0.4 + 0.5 * rng.random())
    crack += 0.5 * band(noise(rng, len(t)), 2500, 12000) * env(t - at, 0.001, 0.05) + 0.6 * band(noise(rng, len(t)), 300, 2400) * env(t - at, 0.0005, 0.025)
    strings = np.zeros(len(t))
    for f in [77.8, 116.5, 155.6, 196.0, 233.1, 293.7, 311.1, 392.0, 466.2, 587.3, 622.3, 784.0]:
        for det in (0.996, 1.004):
            strings += w_string(rng, t, at + 0.03 * rng.random(), f * det, 0.3 + 60 / f, 1.0)
    rattle = 1 + 0.75 * env(t - at, 0.001, 0.4) * np.sin(2 * np.pi * 31 * t)
    snaps = sum(w_snap(rng, t, at + d, f) for d, f in [(0.004, 330), (0.014, 440), (0.026, 262), (0.33, 494), (0.52, 370)])
    shards = w_glass(rng, t, at + 0.02, 0.55, 30, 1800, 5000, life=0.06)
    x = (1.0 * nrm(crack) + 0.9 * nrm(strings * rattle) + 0.45 * nrm(boom(rng, t, at, 80, 44, 0.8)) + 0.4 * nrm(snaps)
         + 0.35 * nrm(shards) + 0.3 * nrm(rush))
    return reverb(rng, x, 2.0, 0.4, 9000), 2.5


def weaver_spindle(seed):
    """Her spindle (4 s): a whirling storm of thread. A spinning wheel's whir, its spokes ticking ever faster till they
    blur into a rising buzz, a whump each turn; a vortex of wind rising out of it into a howl (narrow, wobbling bands,
    circling); glass shimmering in it and her voices trembling high over it."""
    rng = np.random.default_rng(seed)
    t = t_(4.0)
    rate = 2 + 14 * np.clip(t / 3.0, 0, 1) ** 1.6          # turns a second
    turn = np.cumsum(rate) / SR
    spokes = 6 * rate
    tick = band(noise(rng, len(t)), 400, 3000) * (0.5 + 0.5 * np.cos(2 * np.pi * 6 * turn)) ** 3
    whir = band(saw(spokes), 250, 4000)
    whump = band(noise(rng, len(t)), 150, 600) * (0.5 + 0.5 * np.cos(2 * np.pi * turn)) ** 10
    wheel = (nrm(tick) + 0.6 * nrm(whir) * np.clip(t / 2.0, 0, 1) + 0.5 * nrm(whump)) * ramp(t, [0, 0.2, 2.3, 3.3, 3.9], [0, 0.7, 1, 0.5, 0])
    wob = 1 + 0.07 * np.sin(2 * np.pi * 0.8 * t) + 0.04 * np.sin(2 * np.pi * 2.1 * t + 1)
    centre = 450 * 2.6 ** np.clip((t - 1.0) / 2.4, 0, 1) * wob
    howl = sum(g * nrm(km.sweep_filter(rng, noise(rng, len(t)), centre * r, centre * r * 0.03)) for r, g in [(1, 1), (1.5, 0.7), (2.25, 0.5)])
    wind = band(noise(rng, len(t)), 500, 7000)
    circle = 0.7 + 0.3 * np.sin(2 * np.pi * turn)
    vortex = (nrm(howl) + 0.25 * nrm(wind)) * circle * ramp(t, [0.9, 3.3, 3.95], [0, 1, 0]) ** 2
    shimmer = sum(w_glass(rng, t, a, 0.8, c, 2200, 5000, life=0.05) for a, c in [(1.0, 12), (1.8, 24), (2.6, 40)])
    voices = w_choir(rng, t, [f * 2 for f in W_CHORD], 1 + 0.06 * np.clip((t - 1.6) / 1.7, 0, 1))
    voices *= (0.6 + 0.4 * np.sin(2 * np.pi * 11 * t)) * ramp(t, [1.6, 3.3, 3.9], [0, 1, 0])
    x = 0.8 * wheel + 1.3 * vortex + 0.4 * nrm(shimmer) * ramp(t, [1.0, 3.3, 3.9], [0.3, 1, 0]) + 0.25 * nrm(voices)
    return reverb(rng, x, 2.4, 0.45, 9000), 4.0


def weaver_needlefall(seed):
    """Her needles fall (2.5 s): great needles of glass whistle down out of the sky one after another and drive into
    the stone (a deep thud, a crack, the stone crunching, a dull thunk), each ringing as glass when it stands; their
    rings together, at the last, her chord."""
    rng = np.random.default_rng(seed)
    t = t_(2.5)
    hits = [(0.72, 1174.7), (0.9, 932.3), (1.06, 1244.5), (1.32, 784.0)]
    x = np.zeros(len(t))
    for i, (at, base) in enumerate(hits):
        g = 1.0 - 0.1 * i
        whistle = w_whistle(rng, t, at - 0.65, 0.65, 3400 - 250 * i, 1100 - 80 * i)
        crack = noise(rng, len(t)) * env(t - at, 0.0003, 0.006)
        crunch = band(noise(rng, len(t)), 700, 4000) * (rng.random(len(t)) > 0.8) * env(t - at, 0.001, 0.08)
        thunk = np.sin(2 * np.pi * 170 * (t - at)) * env(t - at, 0.002, 0.06)
        x += g * (0.35 * nrm(whistle) + 0.7 * nrm(boom(rng, t, at, 72, 40, 0.7)) + 0.7 * nrm(crack) + 0.45 * nrm(crunch)
                  + 0.4 * thunk + 0.35 * nrm(w_ring(rng, t, at + 0.005, base, 0.9)))
    chord = sum(w_ring(rng, t, 1.36, f, 1.4) for f in W_CHORD) * ramp(t, [1.36, 1.6], [0, 1])
    x += 0.3 * nrm(chord) + 0.2 * nrm(w_glass(rng, t, 0.72, 0.7, 16, 2000, 4500, life=0.05))
    return reverb(rng, x, 2.2, 0.42, 9000), 2.5


# the Ferryman's own: churning water, bubbles, soul fire, wood, and his bell drowned

def f_churn(rng, t, lo=60, hi=900, rates=(0.9, 1.7, 2.9)):
    """Deep water churning: low band noise heaving at a few slow, uneven rates."""
    w = band(noise(rng, len(t)), lo, hi)
    return w * sum((0.5 + 0.5 * np.sin(2 * np.pi * r * t + rng.random() * 6)) ** 2 for r in rates) / len(rates)


def f_bubbles(rng, t, at, spread, count, lo=90, hi=420, life=0.06, early=1.0):
    """Bubbles bursting up through water: each a short tone that rises as it closes (early > 1 crowds them to the
    start)."""
    x = np.zeros(len(t))
    for _ in range(count):
        s = at + spread * rng.random() ** early
        d = life * (0.5 + rng.random())
        tt = t_(d * 4)
        f = (lo + rng.random() * (hi - lo)) * (1 + 0.8 * np.minimum(tt, 2 * d) / d)
        put(x, s, np.sin(2 * np.pi * np.cumsum(f) / SR) * env(tt, 0.002, d), 0.3 + 0.7 * rng.random())
    return x


def f_soulfire(rng, t, lo=1800, hi=6000, pops=30):
    """Soul fire: a breathy hiss, flickering, and the soft pops of a flame that burns cold and blue."""
    n = len(t)
    flick = km.lowpass(rng.standard_normal(n), 14)
    flick = 0.6 + 0.4 * np.tanh(flick / (np.std(flick) + 1e-9))
    p = np.zeros(n)
    idx = rng.integers(0, n, int(n / SR * pops))
    p[idx] = rng.standard_normal(len(idx)) * 6
    return band(noise(rng, n), lo, hi) * flick + band(p, 900, 5000)


def f_splat(rng, t, at, spread, count, lo=600, hi=3500):
    """Water falling back: short splats scattered, more of them at first."""
    x = np.zeros(len(t))
    for _ in range(count):
        s = at + spread * rng.random() ** 1.6
        f = lo + rng.random() * (hi - lo)
        put(x, s, band(noise(rng, int(SR * 0.08)), f * 0.6, f * 1.4) * env(t_(0.08), 0.001, 0.012 + 0.012 * rng.random()),
            0.3 + 0.7 * rng.random())
    return x


def f_creak(rng, t, at, dur, rate0, rate1, body=(210, 470, 900)):
    """Wood creaking under strain: stick-slip clicks (their rate gliding from rate0 to rate1, unevenly) ringing in the
    wood's body."""
    k = np.clip((t - at) / dur, 0, 1)
    drift = km.lowpass(rng.standard_normal(len(t)), 6)
    rate = (rate0 + (rate1 - rate0) * k) * (1 + 0.3 * np.tanh(drift / (np.std(drift) + 1e-9)))
    ph = np.cumsum(rate) / SR
    clicks = np.zeros(len(t))
    for e in np.nonzero(np.diff(np.floor(ph)) > 0)[0]:
        if at <= t[e] <= at + dur:
            clicks[e] = 0.5 + rng.random()
    ring = sum(g * band(clicks, f * 0.92, f * 1.08) for f, g in zip(body, (1.0, 0.7, 0.4)))
    return ring * np.clip((t - at) / 0.04, 0, 1) * np.clip((at + dur - t) / 0.06, 0, 1)


def f_drowned_bell(rng, t, at, base, ring=2.0, detune=0.03, drift=0.006):
    """His bell drowned: its partials put out of tune and wavering slowly, as heard through deep water."""
    tt = t - at
    x = np.zeros(len(t))
    for r, a, k in PARTIALS:
        f = base * r * (1 + rng.uniform(-detune, detune)) * (1 + drift * np.sin(2 * np.pi * rng.uniform(0.3, 0.9) * t + rng.random() * 6))
        x += a * np.sin(2 * np.pi * np.cumsum(f) / SR) * env(tt, 0.004, k * ring)
    return x


def ferryman_undertow(seed):
    """His undertow (4 s): a whirlpool opening. Deep water churning ever harder with bubbles gurgling up through it, a
    sucking roar (air and water drawn down and down, the whirlpool's hollow throat under it), soul fire hissing on the
    water, and a last deep gulp as it opens wide."""
    rng = np.random.default_rng(seed)
    t = t_(4.0)
    n = len(t)
    opening = ramp(t, [0, 2.7, 3.4, 3.95], [0.15, 1, 1, 0])
    churn = f_churn(rng, t, 50, 800, (0.8, 1.6, 2.7)) * opening
    gurgle = f_bubbles(rng, t, 0.1, 3.4, 150, 70, 340, 0.07) * opening
    suck = sweep(rng, t, 0.3, 3.2, 1400, 140, 0.45) * ramp(t, [0.3, 2.9, 3.6], [0, 1, 0]) ** 1.5
    roar = band(noise(rng, n), 30, 260) * ramp(t, [0, 2.8, 3.6, 3.95], [0, 1, 0.8, 0])
    f = 52 * (1 - 0.15 * np.clip(t / 3.5, 0, 1)) * (1 + 0.03 * np.sin(2 * np.pi * 0.5 * t))
    throat = formant(saw(f) + 0.6 * saw(f * 1.007), [(160, 1.0), (330, 0.6), (620, 0.25)]) * ramp(t, [0.4, 2.8, 3.8], [0, 1, 0])
    hiss = f_soulfire(rng, t, 2000, 6000) * ramp(t, [0.8, 2.6, 3.8], [0, 1, 0])
    gulp = np.zeros(n)
    tt = t_(0.5)
    put(gulp, 3.05, np.sin(2 * np.pi * np.cumsum(60 * (1 + 1.2 * np.clip(tt / 0.2, 0, 1))) / SR) * env(tt, 0.01, 0.12))
    x = (0.9 * nrm(churn) + 0.5 * nrm(gurgle) + 0.7 * nrm(suck) + 0.8 * nrm(roar) + 0.45 * nrm(throat) + 0.35 * nrm(hiss)
         + 0.5 * gulp)
    return reverb(rng, x, 2.6, 0.42, 3500), 4.0


def ferryman_geyser(seed):
    """His geyser (3 s): soul fire bursting up out of the river. A boil and a rumble gathering, then the eruption, a
    boom under a great whoosh and a burst of water, a roaring column flickering, the boiling hiss of soul fire on the
    water and the bubbles of it, and the water falling back."""
    rng = np.random.default_rng(seed)
    t = t_(3.0)
    n = len(t)
    at = 0.42
    boil = f_bubbles(rng, t, 0.0, at, 45, 110, 480, 0.05, 0.5) + 0.6 * nrm(band(noise(rng, n), 30, 220)) * ramp(t, [0, at, at + 0.05], [0, 1, 0]) ** 2
    whoosh = sweep(rng, t, at - 0.06, 0.9, 160, 2200, 0.8) * env(t - at + 0.06, 0.06, 0.45)
    burst = band(noise(rng, n), 70, 4500) * env(t - at, 0.004, 0.16)
    flick = km.lowpass(rng.standard_normal(n), 10)
    column = band(noise(rng, n), 50, 900) * env(t - at, 0.03, 0.9) * (0.7 + 0.3 * np.tanh(flick / (np.std(flick) + 1e-9)))
    hiss = f_soulfire(rng, t, 1800, 7000, 50) * env(t - at - 0.03, 0.08, 1.1)
    boiling = f_bubbles(rng, t, at + 0.15, 1.9, 110, 150, 650, 0.04, 1.8)
    fall = f_splat(rng, t, at + 0.7, 1.6, 60)
    x = (0.5 * nrm(boil) + 1.0 * nrm(boom(rng, t, at, 78, 30, 1.8)) + 0.8 * nrm(whoosh) + 0.6 * nrm(burst) + 0.6 * nrm(column)
         + 0.4 * nrm(hiss) + 0.3 * nrm(boiling) + 0.25 * nrm(fall))
    return reverb(rng, x, 2.4, 0.38, 3500), 3.0


def ferryman_oars(seed):
    """His oar (2 s): one huge stroke. The wood creaks as it takes the strain, the blade bites into the river with a
    heavy splash and sweeps it, churning, the oarlock's iron rattling; the blade lifts out in a spray and the wood
    groans as it lets go."""
    rng = np.random.default_rng(seed)
    t = t_(2.0)
    n = len(t)
    bite, lift = 0.42, 1.2
    strain = f_creak(rng, t, 0.0, 0.62, 26, 64) * ramp(t, [0, 0.45, 0.62], [0.3, 1, 0.6]) + 0.6 * f_creak(rng, t, 0.55, 0.6, 40, 22)
    strain += 0.5 * f_creak(rng, t, lift - 0.05, 0.3, 50, 30, (260, 560, 1100))
    splash = band(noise(rng, n), 150, 3000) * env(t - bite, 0.003, 0.09) + 0.6 * band(noise(rng, n), 400, 2500) * env(t - bite, 0.001, 0.012)
    k = np.clip((t - bite) / (lift - bite), 0, 1)
    centre = 300 + 500 * np.sin(np.pi * k) ** 1.5
    swept = km.sweep_filter(rng, noise(rng, n), centre, centre * 0.7) * np.sin(np.pi * k) ** 1.2 * (0.6 + 0.4 * np.sin(2 * np.pi * 7 * t) ** 2)
    churn = f_bubbles(rng, t, bite, lift - bite, 30, 100, 450)
    grain = km.lowpass((rng.random(n) > 0.6).astype(float), 3000)
    spray = band(noise(rng, n), 1200, 5500) * grain * env(t - lift, 0.02, 0.25) + 0.5 * f_bubbles(rng, t, lift + 0.08, 0.5, 25, 600, 1600, 0.02)
    iron = f_chain(rng, t, bite, 0.25, 5, 900)
    x = (0.9 * nrm(np.tanh(2.5 * nrm(strain))) + 0.7 * nrm(boom(rng, t, bite, 60, 40, 0.5)) + 0.9 * nrm(splash) + 0.8 * nrm(swept) + 0.3 * nrm(churn)
         + 0.45 * nrm(spray) + 0.12 * nrm(iron))
    return reverb(rng, x, 1.6, 0.32, 3500), 2.0


def ferryman_bell(seed):
    """His drowned bell (3.5 s): it tolls once, deep (an A, under all his other bells), waterlogged and out of tune (a second bell a little off beating
    against it, its ring wavering through the water), with a low shockwave thump through the river, its chains shaken
    and bubbles rising after."""
    rng = np.random.default_rng(seed)
    t = t_(3.5)
    n = len(t)
    bell = f_drowned_bell(rng, t, 0.02, 110, 2.0) + 0.55 * f_drowned_bell(rng, t, 0.03, 110 * 1.018, 1.7)
    bell += 0.6 * f_drowned_bell(rng, t, 0.02, 220 * 0.991, 1.3, 0.02)
    bell *= 1 + 0.2 * np.sin(2 * np.pi * 1.3 * t)
    wah = km.sweep_filter(rng, bell, 480 * (1 + 0.4 * np.sin(2 * np.pi * 0.6 * t)), np.full(n, 220.0))
    bell = band(bell, 30, 2000) + 0.6 * wah / (np.std(wah) + 1e-9) * np.std(bell)
    clunk = band(noise(rng, n), 80, 900) * env(t - 0.02, 0.002, 0.05)
    thump = boom(rng, t, 0.0, 60, 30, 1.4) + 0.6 * nrm(band(noise(rng, n), 20, 160)) * env(t, 0.008, 0.3)
    thump += 0.3 * boom(rng, t, 0.18, 52, 30, 1.0) + 0.2 * nrm(band(noise(rng, n), 100, 600)) * env(t, 0.02, 0.4)
    rising = f_bubbles(rng, t, 0.2, 2.4, 35, 120, 380)
    chains = f_chain(rng, t, 0.05, 0.4, 6, 900)
    x = 1.0 * nrm(bell) + 0.5 * nrm(clunk) + 0.6 * nrm(thump) + 0.25 * nrm(rising) + 0.12 * nrm(chains)
    return reverb(rng, x, 3.0, 0.48, 3000), 3.5


SOUNDS = {
    "matriarch_hurt1": lambda: matriarch_hurt(11, 1.0), "matriarch_hurt2": lambda: matriarch_hurt(12, 0.9),
    "matriarch_hurt3": lambda: matriarch_hurt(13, 1.12),
    "matriarch_ambient1": lambda: matriarch_ambient(21), "matriarch_ambient2": lambda: matriarch_ambient(22),
    "matriarch_death": lambda: matriarch_death(31), "matriarch_step1": lambda: matriarch_step(41, 1.0),
    "matriarch_step2": lambda: matriarch_step(42, 0.88), "matriarch_roar": lambda: matriarch_roar(51),
    "ferryman_hurt1": lambda: ferryman_hurt(11, 196), "ferryman_hurt2": lambda: ferryman_hurt(12, 185),
    "ferryman_hurt3": lambda: ferryman_hurt(13, 208),
    "ferryman_ambient1": lambda: ferryman_ambient(21), "ferryman_ambient2": lambda: ferryman_ambient(22),
    "ferryman_death": lambda: ferryman_death(31), "ferryman_step1": lambda: ferryman_step(41, 1.0),
    "ferryman_step2": lambda: ferryman_step(42, 0.9), "ferryman_roar": lambda: ferryman_roar(51),
    "weaver_hurt1": lambda: weaver_hurt(11, 1.0), "weaver_hurt2": lambda: weaver_hurt(12, 0.92), "weaver_hurt3": lambda: weaver_hurt(13, 1.1),
    "weaver_ambient1": lambda: weaver_ambient(21), "weaver_ambient2": lambda: weaver_ambient(22),
    "weaver_death": lambda: weaver_death(31), "weaver_step1": lambda: weaver_step(41, 1.0), "weaver_step2": lambda: weaver_step(42, 0.9),
    "weaver_roar": lambda: weaver_roar(51),
    # the blows of 3.43
    "weaver_warp": lambda: weaver_warp(61), "weaver_warpfall": lambda: weaver_warpfall(62),
    "weaver_spindle": lambda: weaver_spindle(63), "weaver_needlefall": lambda: weaver_needlefall(64),
    "ferryman_undertow": lambda: ferryman_undertow(61), "ferryman_geyser": lambda: ferryman_geyser(62),
    "ferryman_oars": lambda: ferryman_oars(63), "ferryman_bell": lambda: ferryman_bell(64),
}

# The blows are brought to a loudness (dB, short-term, as knot_mother.loudness) inside the range of their Elder's own set
# (the Weaver's from about -9 to -14, the Ferryman's from about -11.5 to -15.5): the slams as loud as a hurt, the
# build-ups and the long ones a little under. The older sounds only peak at 0.9.
LOUDNESS = {"weaver_warp": -11, "weaver_warpfall": -10, "weaver_spindle": -10.5, "weaver_needlefall": -10,
            "ferryman_undertow": -12, "ferryman_geyser": -11.5, "ferryman_oars": -12, "ferryman_bell": -11.5}


def main():
    args = sys.argv[1:]
    preview = None
    if "--preview" in args:
        i = args.index("--preview")
        preview = args[i + 1] if i + 1 < len(args) else sys.exit("--preview needs a directory")
        del args[i:i + 2]
    # every other argument names a sound to make; a name that is not one stops it (rather than making them all)
    unknown = [a for a in args if a not in SOUNDS]
    if unknown:
        sys.exit("no such sound: " + ", ".join(unknown))
    only = args
    os.makedirs(OUT, exist_ok=True)
    for name, make in SOUNDS.items():
        if only and name not in only:
            continue
        x, keep = make()
        y = save(name, x, keep, LOUDNESS.get(name))
        print(f"{name}: {len(y) / SR:.2f} s, loudness {km.loudness(y):.1f}, peak {np.max(np.abs(y)):.2f}, bands {km.bands(y)}")
        if preview:
            os.makedirs(preview, exist_ok=True)
            km.spectrogram(y, os.path.join(preview, name + ".png"))


if __name__ == "__main__":
    main()
