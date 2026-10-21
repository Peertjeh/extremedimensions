"""The Elders' own sounds: hurt (3), ambient (2), death, steps (2) and roar for each, made from noise and tones.

The Matriarch is living wood: splitting bark, a deep groan through her mask, leaves. The Ferryman is a cracked bell,
chains and the river under his boat, and a hollow moan. The Last Weaver is glass and thread: shards, snapping strings,
an airy breath, a chorus of thin high voices. Deterministic (fixed seeds). Needs numpy and ffmpeg (libvorbis).

usage: python3 tools/sounds/elders.py
"""
import os
import subprocess
import tempfile
import wave

import numpy as np

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


def save(name, x, keep):
    x = x[: int(SR * keep)].copy()
    x = x / (np.max(np.abs(x)) + 1e-9) * 0.9
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


def main():
    os.makedirs(OUT, exist_ok=True)
    made = {
        "matriarch_hurt1": matriarch_hurt(11, 1.0), "matriarch_hurt2": matriarch_hurt(12, 0.9), "matriarch_hurt3": matriarch_hurt(13, 1.12),
        "matriarch_ambient1": matriarch_ambient(21), "matriarch_ambient2": matriarch_ambient(22),
        "matriarch_death": matriarch_death(31), "matriarch_step1": matriarch_step(41, 1.0), "matriarch_step2": matriarch_step(42, 0.88),
        "matriarch_roar": matriarch_roar(51),
        "ferryman_hurt1": ferryman_hurt(11, 196), "ferryman_hurt2": ferryman_hurt(12, 185), "ferryman_hurt3": ferryman_hurt(13, 208),
        "ferryman_ambient1": ferryman_ambient(21), "ferryman_ambient2": ferryman_ambient(22),
        "ferryman_death": ferryman_death(31), "ferryman_step1": ferryman_step(41, 1.0), "ferryman_step2": ferryman_step(42, 0.9),
        "ferryman_roar": ferryman_roar(51),
        "weaver_hurt1": weaver_hurt(11, 1.0), "weaver_hurt2": weaver_hurt(12, 0.92), "weaver_hurt3": weaver_hurt(13, 1.1),
        "weaver_ambient1": weaver_ambient(21), "weaver_ambient2": weaver_ambient(22),
        "weaver_death": weaver_death(31), "weaver_step1": weaver_step(41, 1.0), "weaver_step2": weaver_step(42, 0.9),
        "weaver_roar": weaver_roar(51),
    }
    for name, (x, keep) in made.items():
        save(name, x, keep)
        print(name)


if __name__ == "__main__":
    main()
