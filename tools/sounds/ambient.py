"""The wound's ambient sounds, made from noise and tones like the vanilla cave moods and biome sounds.

Deep, slow and something wrong at the edge of hearing: the deep's groan, giggling in the distance, the sky tearing,
the Nether's glowing deep, the Ferryman's bell, basalt cracking far down, the undertow (in the Fray, the Nether and
the End), a knot's heartbeat, steps in the deep, a shriek far off, a call from the abyss, and the dread that swells
before a swarm. Each family comes in variants so the game rarely plays the same one twice in a row.

Deterministic (fixed seeds). Needs numpy and ffmpeg (libvorbis, and libmp3lame for --mp3).
usage: python3 tools/sounds/ambient.py [--out=DIR] [--mp3] [families...]
"""
import os
import subprocess
import sys
import tempfile
import wave

import numpy as np

SR = 44100


# ------------------------------------------------------------------ building blocks

def t_(d):
    return np.arange(int(SR * d)) / SR


def freqs(n):
    return np.fft.rfftfreq(n, 1 / SR)


def shape(x, resp):
    """Filters x by a frequency response resp(f) (zero phase)."""
    n = len(x)
    return np.fft.irfft(np.fft.rfft(x) * resp(freqs(n)), n)


def lp_resp(fc, order=2):
    return lambda f: 1 / np.sqrt(1 + (f / fc) ** (2 * order))


def hp_resp(fc, order=2):
    return lambda f: 1 / np.sqrt(1 + (fc / np.maximum(f, 1e-3)) ** (2 * order))


def lowpass(x, fc, order=2):
    return shape(x, lp_resp(fc, order))


def highpass(x, fc, order=2):
    return shape(x, hp_resp(fc, order))


def band(x, lo, hi, order=2):
    return shape(x, lambda f: lp_resp(hi, order)(f) * hp_resp(lo, order)(f))


def peaks(x, ps):
    """Resonances: (centre Hz, width Hz, gain) each, summed."""
    return shape(x, lambda f: sum(g * np.exp(-0.5 * ((f - c) / w) ** 2) for c, w, g in ps))


def white(rng, d):
    return rng.standard_normal(int(SR * d))


def pink(rng, d):
    x = white(rng, d)
    return shape(x, lambda f: 1 / np.sqrt(np.maximum(f, 20)))


def brown(rng, d):
    x = white(rng, d)
    return shape(x, lambda f: 1 / np.maximum(f, 15))


def norm(x):
    return x / (np.max(np.abs(x)) + 1e-12)


def sweep(x, mask_at, n=2048, hop=512):
    """A filter that changes over time: mask_at(seconds, f) gives the response at each moment (short-time FFT)."""
    win = np.hanning(n)
    xp = np.concatenate([x, np.zeros(n)])
    out = np.zeros(len(xp))
    acc = np.zeros(len(xp))
    f = np.fft.rfftfreq(n, 1 / SR)
    for i in range(0, len(x), hop):
        seg = xp[i:i + n] * win
        out[i:i + n] += np.fft.irfft(np.fft.rfft(seg) * mask_at((i + n / 2) / SR, f), n) * win
        acc[i:i + n] += win ** 2
    return (out / np.maximum(acc, 1e-6))[:len(x)]


def fftconv(a, b):
    n = len(a) + len(b) - 1
    m = 1 << (n - 1).bit_length()
    return np.fft.irfft(np.fft.rfft(a, m) * np.fft.rfft(b, m), m)[:n]


def reverb(rng, x, tail=3.0, mix=0.4, bright=5000, pre=0.02):
    """A dark hall: noise decaying to -60 dB over `tail` seconds, its highs dying first."""
    n = int(SR * tail)
    tt = np.arange(n) / SR
    early = rng.standard_normal(n) * np.exp(-tt * 6.9 / tail)
    late = lowpass(rng.standard_normal(n), bright * 0.35) * np.exp(-tt * 6.9 / tail)
    blend = np.clip(tt / (tail * 0.5), 0, 1)
    ir = lowpass(early, bright) * (1 - blend) + late * blend
    ir[:int(SR * pre)] = 0
    ir /= np.sqrt(np.sum(ir ** 2)) + 1e-12
    wet = fftconv(x, ir)[:len(x)]
    rx = np.sqrt(np.mean(x ** 2)) + 1e-12
    wet *= rx / (np.sqrt(np.mean(wet ** 2)) + 1e-12)
    return x * (1 - mix) + wet * mix


def env(t, t0, a, d):
    """Attack then exponential decay from t0; silent before."""
    u = t - t0
    return np.where(u < 0, 0.0, np.minimum(1, u / max(a, 1e-4)) * np.exp(-np.maximum(0, u - a) / d))


def bell_env(t, t0, a, hold, r):
    """Rises over a, holds, falls over r (raised cosine edges)."""
    u = t - t0
    up = np.clip(u / max(a, 1e-4), 0, 1)
    down = np.clip(1 - (u - a - hold) / max(r, 1e-4), 0, 1)
    return np.where(u < 0, 0.0, (0.5 - 0.5 * np.cos(np.pi * up)) * (0.5 - 0.5 * np.cos(np.pi * down)))


def swell(t, centre, width):
    return np.exp(-0.5 * ((t - centre) / width) ** 2)


def phase(freq):
    return 2 * np.pi * np.cumsum(freq) / SR


def harm(freq, top=5000.0, tilt=1.0, inharm=0.0):
    """A band-limited harmonic tone following the frequency array `freq` (no aliasing)."""
    ph = phase(freq)
    k_max = max(1, int(top / max(np.max(freq), 1)))
    out = np.zeros(len(freq))
    for k in range(1, k_max + 1):
        out += np.sin(ph * k * (1 + inharm * k * k)) / k ** tilt
    return out


def partials(t, t0, f0, ratios, decays, gains, beat=0.0):
    """A struck thing: inharmonic partials, each with its own decay (bells, glass, chain links)."""
    u = t - t0
    on = u >= 0
    out = np.zeros(len(t))
    for r, d, g in zip(ratios, decays, gains):
        f = f0 * r
        tone = np.sin(2 * np.pi * f * u) + (0.6 * np.sin(2 * np.pi * (f + beat) * u) if beat else 0)
        out += np.where(on, g * tone * np.exp(-np.maximum(u, 0) / d), 0.0)
    return out


def crackle(rng, t, rate, lo, hi, decay=0.008, amp_spread=1.0):
    """Sparse clicks (embers, ticking stone): Poisson impulses rung through a band."""
    n = len(t)
    x = np.zeros(n)
    count = rng.poisson(rate * n / SR)
    idx = rng.integers(0, n, count)
    x[idx] = rng.standard_normal(count) * np.exp(rng.standard_normal(count) * amp_spread)
    k = int(SR * decay * 6)
    kern = np.exp(-np.arange(k) / (SR * decay)) * rng.standard_normal(k)
    return band(fftconv(x, kern)[:n], lo, hi)


def fade(x, fin=0.6, fout=1.2):
    x = x.copy()
    a, b = int(SR * fin), int(SR * fout)
    if a:
        x[:a] *= np.linspace(0, 1, a) ** 2
    if b:
        x[-b:] *= np.linspace(1, 0, b) ** 2
    return x


def finish(x, rms_db=-21.0, peak=0.89):
    """Even loudness across the set: a target RMS, with the peaks kept under a ceiling (soft knee)."""
    x = x - np.mean(x)
    rms = np.sqrt(np.mean(x ** 2)) + 1e-12
    x = x * (10 ** (rms_db / 20) / rms)
    over = np.abs(x) > peak * 0.7
    if over.any():
        knee = peak * 0.7
        x = np.where(over, np.sign(x) * (knee + (peak - knee) * np.tanh((np.abs(x) - knee) / (peak - knee))), x)
    return x


# ------------------------------------------------------------------ the sounds


def write(out_dir, name, x, mp3):
    with tempfile.TemporaryDirectory() as tmp:
        wav = os.path.join(tmp, name + ".wav")
        with wave.open(wav, "wb") as w:
            w.setnchannels(1)
            w.setsampwidth(2)
            w.setframerate(SR)
            w.writeframes((np.clip(x, -1, 1) * 32767).astype(np.int16).tobytes())
        subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", wav, "-c:a", "libvorbis", "-q:a", "3",
                        os.path.join(out_dir, name + ".ogg")], check=True)
        if mp3:
            subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", wav, "-c:a", "libmp3lame", "-q:a", "4",
                            os.path.join(out_dir, name + ".mp3")], check=True)


# ------------------------------------------------------------------ the voices
def const(t, f):
    return np.full(len(t), float(f))


def sine(t, f):
    return np.sin(phase(const(t, f)))


def far(x, cutoff, gain):
    """Distance: the highs go first, then the level."""
    return lowpass(x, cutoff, 3) * gain


# ------------------------------------------------------------------ shared voices

def groan(rng, t, start, length, f0, f1, wobble=1.2):
    f = f0 + (f1 - f0) * np.clip((t - start) / length, 0, 1) + wobble * np.sin(phase(const(t, rng.uniform(0.15, 0.3))))
    tone = lowpass(harm(np.maximum(f, 20), top=900, tilt=1.1), 260, 3)
    grind = band(brown(rng, t[-1] + 1 / SR), 60, 420)[:len(t)] * (0.6 + 0.4 * norm(lowpass(white(rng, t[-1] + 1 / SR), 3))[:len(t)])
    e = bell_env(t, start, length * 0.3, length * 0.35, length * 0.35)
    return (norm(tone) * 0.8 + norm(grind) * 0.55) * e


def giggle(rng, t, start, count, f0, gap=(0.06, 0.09)):
    x = np.zeros(len(t))
    s = start
    d = t[-1] + 1 / SR
    for i in range(count):
        dur = rng.uniform(0.09, 0.13)
        f0i = f0 - i * 26 + rng.uniform(-20, 20)
        e = bell_env(t, s, 0.012, dur * 0.3, dur * 0.7)
        u = np.maximum(t - s, 0)
        f = f0i * (1.08 - 0.16 * np.clip(u / dur, 0, 1))
        voice = peaks(harm(f, top=6000, tilt=1.35), [(f0i, 420, 1.0), (2300, 520, 0.55), (3300, 600, 0.22)])
        breath = band(white(rng, d), 900, 3500) * bell_env(t, s - 0.035, 0.015, 0.01, 0.03) * 0.5
        x += voice * e + breath
        s += dur + rng.uniform(*gap)
    return x


def thud(rng, t, s, f_hi, f_lo, decay, d):
    f = f_hi * np.exp(-np.maximum(t - s, 0) / (decay * 0.35)) + f_lo
    return np.sin(phase(f)) * env(t, s, 0.006, decay) + lowpass(white(rng, d), 260) * env(t, s, 0.003, decay * 0.3) * 0.5


def bell_toll(rng, t, s, base, d, crack=0.0):
    ratios = [0.5, 1.0, 1.19, 1.56, 2.0, 2.51, 2.66, 3.01, 4.1]
    decays = [7.0, 4.5, 3.6, 2.8, 2.4, 1.6, 1.4, 1.1, 0.7]
    gains = [0.8, 1.0, 0.7, 0.45, 0.5, 0.3, 0.25, 0.2, 0.12]
    if crack:
        ratios = [r * (1 + crack * rng.uniform(-0.03, 0.03)) for r in ratios]
    b = partials(t, s, base, ratios, decays, gains, beat=0.35 + crack * 2.5)
    if crack:
        upper = partials(t, s, base, ratios[4:], decays[4:], gains[4:])
        b += upper * (0.5 + 0.5 * np.sign(np.sin(phase(const(t, 31.0))))) * crack
    b += band(white(rng, d) * env(t, s, 0.001, 0.02), 400, 3000) * 0.3
    return b


def heartbeat(rng, t, s, f_lub, d, gain=1.0):
    x = np.sin(phase(70 * np.exp(-np.maximum(t - s, 0) / 0.06) + f_lub)) * env(t, s, 0.008, 0.18)
    x += lowpass(white(rng, d), 200) * env(t, s, 0.004, 0.05) * 0.4
    s2 = s + 0.28
    x += np.sin(phase(62 * np.exp(-np.maximum(t - s2, 0) / 0.05) + f_lub + 6)) * env(t, s2, 0.008, 0.14) * 0.6
    return x * gain


def link(t, s, f0, g=1.0):
    return partials(t, s, f0, [1.0, 2.76, 5.40, 8.93], [0.22, 0.12, 0.07, 0.04], [1, 0.6, 0.35, 0.2]) * g


def scrape(rng, t, d, e, base=420):
    stick = 0.55 + 0.45 * np.sign(np.sin(phase(const(t, rng.uniform(14, 22)) + 6 * np.sin(phase(const(t, 0.7))))))
    metal = peaks(white(rng, d), [(base, 18, 1.0), (base * 2.15, 25, 0.7), (base * 3.64, 35, 0.45)])
    grit = band(white(rng, d), 200, 1800) * 0.35
    return (norm(metal) + norm(grit) * 0.4) * stick * e


# ------------------------------------------------------------------ the eight that stayed, in variants

def deep_groan(rng, v):
    d = 12.0
    t = t_(d)
    if v == 1:
        x = groan(rng, t, 0.3, 10.5, 40, 31)
    elif v == 2:
        x = groan(rng, t, 0.3, 10.0, 34, 29, wobble=0.8)
        crack_t = 5.2
        x += norm(thud(rng, t, crack_t, 90, 38, 0.35, d)) * 0.7
        x += band(white(rng, d) * env(t, crack_t, 0.002, 0.6), 150, 1200) * np.abs(lowpass(white(rng, d), 40)) * 2.0
    else:
        x = groan(rng, t, 0.3, 9.0, 38, 33) + groan(rng, t, 2.6, 8.8, 25.3, 22.5) * 0.8
    x = norm(x) + norm(lowpass(brown(rng, d), 45, 4)) * 0.35
    return fade(reverb(rng, x, tail=3.4, mix=0.38, bright=1400), 0.2, 1.4)


def hollow_far(rng, v):
    d = 11.0
    t = t_(d)
    if v == 1:
        x = far(giggle(rng, t, 1.0, 5, 760), 3200, 1.0) + far(giggle(rng, t, 6.2, 3, 740), 3000, 0.9)
        x = reverb(rng, x, tail=3.0, mix=0.66, bright=2800, pre=0.04)
    elif v == 2:
        # running off: each laugh a little further
        x = np.zeros(len(t))
        s = 0.9
        for i in range(7):
            x += far(giggle(rng, t, s, 2, 780 - i * 6, gap=(0.05, 0.07)), 3400 - i * 330, 1.0 - i * 0.11)
            s += rng.uniform(0.55, 0.85)
        x = reverb(rng, x, tail=3.4, mix=0.6, bright=2800, pre=0.04)
    else:
        # one laughs, another answers from far off, and then one is suddenly much nearer
        a = far(giggle(rng, t, 0.8, 4, 780), 2600, 0.8)
        b = far(giggle(rng, t, 3.4, 4, 690), 1300, 0.45)
        near = far(giggle(rng, t, 8.6, 2, 820, gap=(0.04, 0.05)), 5200, 1.0)
        x = reverb(rng, a + b, tail=3.6, mix=0.7, bright=2600, pre=0.05) + reverb(rng, near, tail=0.9, mix=0.2, bright=6000)
    x = norm(x) + norm(lowpass(pink(rng, d), 900, 3)) * 0.04
    return fade(x, 0.5, 1.2)


def storm_tear(rng, v):
    d = 12.0
    t = t_(d)

    def tear(s, gain, cutoff):
        rip = crackle(rng, t, 900, 300, 4200, decay=0.004) * env(t, s, 0.01, 0.9)
        f = np.where(t >= s, 900 * np.exp(-np.maximum(t - s, 0) / 0.6) + 110, 900)
        sweep_tone = np.sin(phase(f)) * env(t, s, 0.02, 0.8) * 0.5 + band(white(rng, d), 400, 1600) * env(t, s, 0.01, 0.5) * 0.6
        return far(norm(rip) * 0.6 + norm(sweep_tone) * 0.45, cutoff, gain)

    if v == 1:
        rise = np.clip((t - 0.8) / 3.0, 0, 1) ** 3 * (t < 3.9)
        x = norm(band(white(rng, d), 250, 2200) * rise) * 0.5 + tear(3.9, 1.0, 2600)
        x += norm(lowpass(brown(rng, d), 120, 3) * env(t, 4.0, 0.4, 3.5)) * 0.9
    elif v == 2:
        x = tear(2.0, 1.0, 2600) + tear(4.4, 0.55, 1500)
        x += norm(lowpass(brown(rng, d), 110, 3) * (env(t, 2.1, 0.4, 3.0) + env(t, 4.5, 0.5, 4.5) * 0.8)) * 0.9
    else:
        f = 60 - 15 * np.clip((t - 0.5) / 5, 0, 1)
        sky = lowpass(harm(f, top=700, tilt=1.0), 400, 3) * bell_env(t, 0.5, 3.0, 1.5, 2.0)
        sky += band(brown(rng, d), 50, 300) * bell_env(t, 0.5, 3.0, 1.5, 2.5) * 0.6
        x = norm(sky) * 0.9 + tear(5.6, 0.5, 1800)
        x += norm(lowpass(brown(rng, d), 100, 3) * env(t, 5.7, 0.5, 4.0)) * 0.7
    return fade(reverb(rng, x, tail=4.2, mix=0.5, bright=2400), 0.2, 1.6)


def ember_pulse(rng, v):
    d = 15.0
    t = t_(d)
    f_sub, period, crackles = {1: (42.0, 1.9, 6), 2: (36.0, 2.4, 5), 3: (40.0, 2.0, 11)}[v]
    beat = np.zeros(len(t))
    s = 0.4
    while s < d - 1:
        beat += bell_env(t, s, 0.25, 0.1, 0.9)
        s += period if v != 3 else period * (1 - 0.3 * s / d)
    f = const(t, f_sub) if v != 3 else f_sub + 6 * t / d
    sub = np.sin(phase(f)) * beat + np.sin(phase(f * 1.5)) * beat * 0.3
    shimmer = band(pink(rng, d), 300, 1500) * (0.5 + 0.5 * np.sin(phase(const(t, 0.13))) ** 2)
    embers = crackle(rng, t, crackles, 1500, 7000, decay=0.006, amp_spread=0.8)
    bloops = np.zeros(len(t))
    for s in ((4.3, 10.8) if v != 2 else (2.6, 6.1, 9.3, 12.2)):
        fb = 180 * np.exp(-np.maximum(t - s, 0) / 0.35) + 85
        bloops += np.sin(phase(fb)) * env(t, s, 0.03, 0.35)
    x = norm(sub) * 0.9 + norm(shimmer) * 0.28 + norm(embers) * 0.22 + norm(bloops) * 0.35
    if v == 2:
        x += norm(band(pink(rng, d), 150, 700) * swell(t, 7.5, 2.0)) * 0.3
    return fade(reverb(rng, x, tail=2.6, mix=0.3, bright=3500), 1.0, 1.5)


def ferryman_bell(rng, v):
    d = 15.0
    t = t_(d)
    lava = lowpass(brown(rng, d), 110, 3) * 0.6 + crackle(rng, t, 3, 900, 4000, decay=0.007) * 0.15
    if v == 1:
        b = far(bell_toll(rng, t, 1.2, 116.5, d), 2400, 1.0)
        x = reverb(rng, norm(b) + norm(lava) * 0.25, tail=5.5, mix=0.55, bright=2600, pre=0.06)
    elif v == 2:
        # two tolls: the second nearer. He is coming.
        b1 = far(bell_toll(rng, t, 0.8, 116.5, d), 1500, 0.55)
        b2 = far(bell_toll(rng, t, 7.0, 116.5, d), 4200, 1.0)
        x = reverb(rng, b1, tail=6.0, mix=0.7, bright=2000, pre=0.07) + reverb(rng, b2, tail=4.5, mix=0.4, bright=4000, pre=0.03)
        x = norm(x) + norm(lava) * 0.2
    else:
        b = far(bell_toll(rng, t, 1.0, 98.0, d, crack=1.0), 2600, 1.0)
        chains = sum(link(t, s, rng.uniform(1900, 2600), rng.uniform(0.15, 0.3)) for s in (6.4, 6.55, 6.8, 7.05))
        x = reverb(rng, norm(b) + far(chains, 3000, 0.6) + norm(lava) * 0.25, tail=5.5, mix=0.55, bright=2500, pre=0.06)
    return fade(x, 0.4, 2.0)


def basalt_deep(rng, v):
    """Black stone cracking, deep down: low cracks that ring in the rock, and the floor settling."""
    d = 12.0
    t = t_(d)

    def crack(s, f, g):
        hit = white(rng, d) * env(t, s, 0.001, 0.012)
        ring = peaks(hit, [(f, f * 0.08, 1.0), (f * 2.3, f * 0.12, 0.5), (f * 3.9, f * 0.2, 0.2)])
        return norm(ring) * g + norm(thud(rng, t, s, 60, f * 0.35, 0.12, d)) * g * 0.5

    bed = norm(lowpass(brown(rng, d), 140, 4)) * 0.45 + norm(band(pink(rng, d), 60, 400)) * 0.08
    x = bed
    if v == 1:
        for s in np.sort(rng.uniform(0.6, d - 2.5, 7)):
            x = x + crack(s, rng.uniform(140, 420), rng.uniform(0.35, 0.8))
        x = x + norm(thud(rng, t, 6.3, 50, 32, 0.6, d)) * 0.9
    elif v == 2:
        s = 1.2
        for k in range(9):
            x = x + crack(s, 480 * 0.86 ** k, 0.5 + 0.05 * k)
            s += 0.18 + 0.03 * k
        x = x + norm(thud(rng, t, s + 0.4, 45, 28, 0.9, d)) * 1.0
    else:
        # a clock that lost count: deep tocks, nearly even, then a long wait, then one more
        s = 0.8
        for k in range(8):
            x = x + crack(s, 210 + rng.uniform(-15, 15), 0.6)
            s += 0.85 + rng.uniform(-0.12, 0.2) + (0.15 * k if k > 4 else 0)
        x = x + crack(s + 1.8, 180, 0.75)
    x = lowpass(x, 2200, 3)
    return fade(reverb(rng, x, tail=3.0, mix=0.4, bright=1800), 0.4, 1.4)


def undertow(rng, v):
    """1: the Fray. 2: the Nether, hot and deeper. 3: the End, cold and hollow."""
    d = 15.0
    t = t_(d)
    if v == 1:
        roll = sweep(brown(rng, d), lambda s, f: lp_resp(80 + 320 * (0.5 + 0.5 * np.sin(2 * np.pi * 0.15 * s)) ** 2, 3)(f))
        x = norm(roll) * 0.8
        groan_f, heat, void = (68, 92), False, False
    elif v == 2:
        roll = sweep(brown(rng, d), lambda s, f: lp_resp(60 + 240 * (0.5 + 0.5 * np.sin(2 * np.pi * 0.11 * s)) ** 2, 4)(f))
        x = norm(roll) * 0.9
        groan_f, heat, void = (52, 66), True, False
    else:
        x = norm(np.sin(phase(const(t, 41.2))) + 0.7 * np.sin(phase(const(t, 61.8))) + 0.5 * np.sin(phase(const(t, 62.05)))) * 0.5
        x *= bell_env(t, 0, 3.0, d - 6, 3.0)
        x += norm(sweep(white(rng, d), lambda s, f: np.exp(-0.5 * (np.log2(np.maximum(f, 1) / (2500 + 1200 * np.sin(s * 0.4))) / 0.45) ** 2))) * 0.12
        groan_f, heat, void = (110, 140), False, True
    for s in (2.2, 6.9, 11.0):
        length = rng.uniform(1.0, 1.6)
        f = rng.uniform(*groan_f) * (1 + 0.04 * np.sin(phase(const(t, 0.9))))
        stick = 0.5 + 0.5 * np.sign(np.sin(phase(const(t, rng.uniform(12, 24)))))
        g = lowpass(harm(f, top=1500, tilt=0.9), 900) * stick * bell_env(t, s, 0.2, length - 0.5, 0.3)
        x += norm(g) * (0.4 if not void else 0.22)
    if not void:
        for s in (4.6, 5.0, 12.4, 12.8):
            x += np.sin(2 * np.pi * (50 if not heat else 42) * np.maximum(t - s, 0)) * env(t, s, 0.01, 0.16) * 0.5
    if heat:
        x += norm(crackle(rng, t, 4, 1200, 4000, decay=0.006)) * 0.12
        fb = 160 * np.exp(-np.maximum(t - 8.4, 0) / 0.4) + 70
        x += np.sin(phase(fb)) * env(t, 8.4, 0.04, 0.4) * 0.4
    if v == 1:
        x += np.sin(phase(2400 * (1 + 0.01 * np.sin(phase(const(t, 0.6)))))) * swell(t, 9.0, 2.5) * 0.02
    tail, mix = (4.0, 0.5) if v != 3 else (6.0, 0.6)
    return fade(reverb(rng, x, tail=tail, mix=mix, bright=2500 if v != 3 else 4000), 1.2, 1.8)


def knot_heartbeat(rng, v):
    d = 13.0
    t = t_(d)
    x = np.zeros(len(t))
    if v == 1:
        for i, s in enumerate(np.arange(0.8, d - 1.5, 1.65)):
            x += heartbeat(rng, t, s, 46, d, min(1.0, 0.5 + i * 0.12))
    elif v == 2:
        for i, s in enumerate(np.arange(0.8, d - 1.5, 2.1)):
            x += heartbeat(rng, t, s, 38, d, min(1.0, 0.55 + i * 0.1))
            # the thread tightens between beats
            cs = s + 0.9
            fcr = 260 + 120 * np.clip((t - cs) / 0.7, 0, 1)
            x += peaks(white(rng, d), [(300, 14, 1.0), (620, 20, 0.5)]) * bell_env(t, cs, 0.2, 0.3, 0.3) * 0.04 * (fcr / 300)
    else:
        beats = [0.8, 2.4, 4.0, 5.6, 9.6, 10.0, 11.6]
        for i, s in enumerate(beats):
            x += heartbeat(rng, t, s, 44 if i < 4 else 40, d, 0.8 if i < 4 else 1.15)
    hum = harm(const(t, 110.0) * (1 + 0.001 * np.sin(phase(const(t, 0.2)))), top=1200, tilt=1.5)
    hum = lowpass(hum, 900) * bell_env(t, 0.5, 4.0, d - 8, 3.5)
    x = norm(x) + norm(hum) * 0.18
    return fade(reverb(rng, x, tail=2.8, mix=0.35, bright=2200), 0.4, 1.6)


# ------------------------------------------------------------------ new: deep, slow, wrong

def far_steps(rng, v):
    """Something very heavy walks, far below. 1: it comes nearer and stops. 2: it walks away, and drags something."""
    d = 13.0
    t = t_(d)
    x = norm(lowpass(brown(rng, d), 60, 4)) * 0.25
    s = 1.0
    n = 6 if v == 1 else 5
    for k in range(n):
        g = (0.45 + 0.11 * k) if v == 1 else (1.0 - 0.15 * k)
        step = norm(thud(rng, t, s, 70, 34, 0.45, d))
        debris = band(white(rng, d) * env(t, s + 0.05, 0.02, 0.35), 500, 2500) * np.abs(lowpass(white(rng, d), 30)) * 1.5
        x += (step + norm(debris) * 0.15) * g
        s += rng.uniform(1.15, 1.6)
    if v == 2:
        x += far(scrape(rng, t, d, bell_env(t, s + 0.5, 0.4, 1.4, 1.0), base=300), 1800, 0.35)
    x = lowpass(x, 1600, 3)
    return fade(reverb(rng, x, tail=3.8, mix=0.45, bright=1500, pre=0.04), 0.3, 1.5)




def far_shriek(rng, v):
    """Far away, something shrieks, and the hills throw it back. 2: and something answers, further still."""
    d = 13.0
    t = t_(d)

    def shriek(s, f0, length, cutoff, g):
        u = np.maximum(t - s, 0)
        f = f0 * (1.15 - 0.4 * np.clip(u / length, 0, 1)) * (1 + 0.02 * np.sin(phase(const(t, 6.0))))
        src = harm(f, top=7000, tilt=1.0) + band(white(rng, d), 1500, 5000) * 0.25
        voice = peaks(src, [(f0 * 1.2, 300, 0.7), (1300, 260, 1.0), (2600, 380, 0.6)])
        return far(voice * bell_env(t, s, 0.12, length * 0.4, length * 0.6), cutoff, g)

    x = shriek(1.0, 640, 1.8, 2200, 1.0)
    if v == 2:
        x += shriek(5.4, 520, 2.2, 1100, 0.5)
    x = reverb(rng, x, tail=5.0, mix=0.7, bright=2200, pre=0.08)
    x = norm(x) + norm(lowpass(pink(rng, d), 400, 3)) * 0.05
    return fade(x, 0.3, 2.0)


def abyss_call(rng, v):
    """From far beneath the islands, a call too low for anything that should be alive. 2: something answers, deeper."""
    d = 16.0
    t = t_(d)

    def call(s, f0, length, g):
        u = np.clip((t - s) / length, 0, 1)
        f = f0 * (1 + 0.25 * np.sin(np.pi * u) - 0.12 * u)
        src = harm(f, top=1200, tilt=0.9)
        voice = peaks(src, [(200, 60, 1.0), (450, 90, 0.6), (f0 * 2, 25, 0.5)])
        am = 0.8 + 0.2 * np.sin(phase(const(t, 2.3)))
        return norm(voice * am * bell_env(t, s, length * 0.3, length * 0.3, length * 0.4)) * g

    x = call(1.0, 52, 6.0, 1.0)
    if v == 2:
        x += call(7.5, 38, 7.0, 0.9)
    else:
        x += call(8.2, 58, 4.5, 0.55)
    x = lowpass(x, 900, 3) + norm(lowpass(brown(rng, d), 50, 4)) * 0.25
    return fade(reverb(rng, x, tail=7.0, mix=0.6, bright=1200, pre=0.08), 0.6, 2.2)


def dread_rise(rng, v):
    """Something swells under everything, closer and closer, and then the world holds its breath."""
    d = 13.0
    t = t_(d)
    cut = 8.6
    # (cut short in 40 ms, not at once: a click would sound like the speaker, not the world)
    gate = np.clip((cut - t) / 0.04, 0, 1)
    grow = np.clip(t / cut, 0, 1) ** 2 * gate
    f = 30 + 18 * np.clip(t / cut, 0, 1)
    drone = np.tanh((np.sin(phase(f)) + np.sin(phase(f * 1.006))) * (0.6 + 2.2 * grow)) * grow
    riser = sweep(white(rng, d), lambda s, fr: np.exp(-0.5 * (np.log2(np.maximum(fr, 1) / (200 + 1800 * min(1, s / cut) ** 2)) / 0.6) ** 2))
    riser *= grow ** 1.5
    x = norm(lowpass(drone, 600, 3)) * 0.9 + norm(riser) * 0.35
    tail = reverb(rng, x * gate, tail=1.4, mix=0.6, bright=2000)
    x = x * 0.6 + tail * 0.4
    if v == 2:
        k = 11.3
        hit = white(rng, d) * env(t, k, 0.0008, 0.01)
        knock = peaks(hit, [(180, 25, 1.0), (420, 40, 0.7)])
        x += reverb(rng, far(norm(knock), 1400, 0.5), tail=2.2, mix=0.6, bright=1400)
    return fade(x, 0.3, 0.8)

# Sounds made of a few sharp events sit a little lower, so the peaks are not squeezed
QUIETER = {"hollow_far", "far_shriek"}

# (name, voice, variants, seed index): the seed index keeps each sound as it was chosen
FAMILIES = [
    ("deep_groan", deep_groan, 3, 0), ("hollow_far", hollow_far, 3, 1), ("storm_tear", storm_tear, 3, 2),
    ("ember_pulse", ember_pulse, 3, 3), ("ferryman_bell", ferryman_bell, 3, 4), ("basalt_deep", basalt_deep, 3, 5),
    ("knot_heartbeat", knot_heartbeat, 3, 7), ("far_steps", far_steps, 2, 8), ("far_shriek", far_shriek, 2, 10),
    ("abyss_call", abyss_call, 2, 11), ("dread_rise", dread_rise, 2, 12),
]
# The undertow, one voice per world, two takes each: the first as chosen, the second on a seed of its own
UNDERTOW = [("undertow_fray", 1), ("undertow_nether", 2), ("undertow_end", 3)]

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "src/client/resources/assets/extremedimensions/sounds/ambient")


def main():
    mp3 = "--mp3" in sys.argv
    out_dir = next((a[len("--out="):] for a in sys.argv[1:] if a.startswith("--out=")), OUT)
    only = [a for a in sys.argv[1:] if not a.startswith("--")]
    os.makedirs(out_dir, exist_ok=True)
    jobs = []
    for name, make, variants, i in FAMILIES:
        for v in range(1, variants + 1):
            jobs.append((name, f"{name}{v}", lambda rng, make=make, v=v: make(rng, v), 9100 + i * 10 + v))
    for name, v in UNDERTOW:
        jobs.append((name, f"{name}1", lambda rng, v=v: undertow(rng, v), 9100 + 6 * 10 + v))
        jobs.append((name, f"{name}2", lambda rng, v=v: undertow(rng, v), 9700 + v))
    for family, file, make, seed in jobs:
        if only and family not in only:
            continue
        x = finish(make(np.random.default_rng(seed)), rms_db=-23.0 if family in QUIETER else -21.0)
        write(out_dir, file, x, mp3)
        print(f"{file}: {len(x) / SR:.1f} s")


if __name__ == "__main__":
    main()
