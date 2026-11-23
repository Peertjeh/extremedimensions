"""The Knot Mother's own sounds: a boss's voice and the weight of her blows, made from noise and tones.

She is a tall veiled figure with a crown of bronze thorns, floating rings and a violet core, who binds the world with
amber threads. So her palette is a choir (a woman's voice multiplied into many, under it a growl an octave or two down),
bronze (inharmonic partials of a struck crown and rings, gongs for her phases), thick threads pulled taut and snapping
(plucked rope, creaks, whip cracks), and the weight of a boss: sub-bass booms that drop in pitch, swelling low "braams" of
detuned saws, risers that run up to the moment a blow lands, all in a long cathedral reverb and mastered loud.

Every sound is mono (Minecraft places it in the world) and deterministic (fixed seeds). Needs numpy and ffmpeg
(libvorbis). With --preview DIR it also writes a spectrogram of each sound there (needs Pillow).

usage: python3 tools/sounds/knot_mother.py [--preview DIR]
"""
import os
import subprocess
import sys
import tempfile
import wave

import numpy as np

SR = 44100
OUT = os.path.join(os.path.dirname(__file__), "..", "..", "src/client/resources/assets/extremedimensions/sounds/elder")


# ------------------------------------------------------------------ tools

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


def lowpass(x, hi):
    return band(x, 1, hi, 0.3)


def env(t, a, d):
    """Attack then exponential decay; zero before the start (t < 0)."""
    return np.where(t < 0, 0.0, np.minimum(1, t / max(a, 1e-4)) * np.exp(-np.maximum(0, t - a) / d))


def gate(t, start, length, fade_in=0.01, fade_out=0.05):
    """1 between start and start + length, with soft edges."""
    a = np.clip((t - start) / max(fade_in, 1e-4), 0, 1)
    b = np.clip((start + length - t) / max(fade_out, 1e-4), 0, 1)
    return a * b


def swell(t, start, peak, end):
    """Rises from start to peak (eased), falls to end."""
    up = np.clip((t - start) / max(peak - start, 1e-4), 0, 1) ** 2
    down = np.clip((end - t) / max(end - peak, 1e-4), 0, 1)
    return np.where(t < peak, up, down)


def noise(rng, n):
    return rng.standard_normal(n)


def phase(freq):
    """Phase (in cycles) of a tone whose frequency changes sample by sample."""
    return np.cumsum(freq) / SR


def fftconv(a, b):
    n = len(a) + len(b) - 1
    m = 1 << (n - 1).bit_length()
    return np.fft.irfft(np.fft.rfft(a, m) * np.fft.rfft(b, m), m)[:n]


def sweep_filter(rng, x, centre, width, nfft=2048):
    """Time-varying band-pass: centre(t) and width(t) in Hz (arrays as long as x), by short-time Fourier transform."""
    hop = nfft // 4
    win = np.hanning(nfft)
    pad = np.concatenate([np.zeros(nfft), x, np.zeros(nfft)])
    out = np.zeros(len(pad))
    norm = np.zeros(len(pad))
    f = np.fft.rfftfreq(nfft, 1 / SR)
    for s in range(0, len(pad) - nfft, hop):
        i = min(len(x) - 1, max(0, s - nfft + nfft // 2))
        c, w = centre[i], max(20.0, width[i])
        spec = np.fft.rfft(pad[s:s + nfft] * win) * np.exp(-0.5 * ((f - c) / w) ** 2)
        out[s:s + nfft] += np.fft.irfft(spec, nfft) * win
        norm[s:s + nfft] += win ** 2
    out = out / np.maximum(norm, 1e-3)
    return out[nfft:nfft + len(x)]


def saturate(x, drive=2.0):
    """Soft clipping: thicker and louder without hard edges."""
    peak = np.max(np.abs(x)) + 1e-9
    return np.tanh(x / peak * drive) / np.tanh(drive) * peak


def compress(x, threshold=0.3, ratio=4.0, attack=0.004, release=0.12):
    """A plain feed-forward compressor on the signal's envelope (relative to its peak)."""
    peak = np.max(np.abs(x)) + 1e-9
    y = np.abs(x) / peak
    a, r = np.exp(-1 / (SR * attack)), np.exp(-1 / (SR * release))
    e = np.empty_like(y)
    level = 0.0
    for i, v in enumerate(y):
        level = a * level + (1 - a) * v if v > level else r * level + (1 - r) * v
        e[i] = level
    gain = np.where(e > threshold, (threshold + (e - threshold) / ratio) / np.maximum(e, 1e-9), 1.0)
    return x * gain


def reverb(rng, x, tail=2.5, mix=0.35, bright=7000, pre=0.02):
    """A large stone hall: early reflections, then a long dense tail, darker as it fades (but kept out of the lowest
    octaves, so her booms stay a blow and do not turn to mud)."""
    n = int(SR * tail)
    tt = np.arange(n) / SR
    ir = band(rng.standard_normal(n), 220, bright) * np.exp(-tt / (tail / 6.5))
    ir += 0.45 * band(rng.standard_normal(n), 140, 1600) * np.exp(-tt / (tail / 6.0))
    ir[: int(SR * pre)] = 0
    for d, g in [(0.011, 0.6), (0.019, 0.45), (0.029, 0.4), (0.041, 0.3), (0.057, 0.25)]:
        k = int(SR * (d + pre * 0.5))
        if k < n:
            ir[k] += g * 3
    wet = fftconv(x, ir)
    out = np.zeros(len(x) + n)
    out[: len(x)] += x
    wet = np.pad(wet, (0, max(0, len(out) - len(wet))))[: len(out)]
    wet = wet / (np.sqrt(np.mean(wet ** 2)) + 1e-9) * np.sqrt(np.mean(x ** 2))
    return out + wet * mix


# ------------------------------------------------------------------ her voice: a choir, a cry, a growl

# Formants (Hz, relative gain, bandwidth) of the vowels a woman's choir sings in
VOWELS = {
    "a": [(800, 1.0, 80), (1150, 0.5, 90), (2900, 0.25, 120), (3900, 0.1, 130), (4950, 0.05, 140)],
    "o": [(450, 1.0, 70), (800, 0.35, 80), (2830, 0.12, 100), (3800, 0.06, 130), (4950, 0.03, 135)],
    "u": [(325, 1.0, 50), (700, 0.16, 60), (2700, 0.04, 170), (3800, 0.02, 180), (4950, 0.01, 200)],
    "e": [(400, 1.0, 60), (1600, 0.35, 80), (2700, 0.25, 120), (3300, 0.18, 150), (4950, 0.05, 200)],
    "ah_low": [(650, 1.0, 80), (1080, 0.45, 90), (2650, 0.2, 120), (2900, 0.1, 130), (3250, 0.06, 140)],
}


def formant_gain(freqs, vowel, k=0.0, vowel2=None):
    """Gain of each harmonic frequency under a vowel's formants (blended towards vowel2 by k), with the ring of a trained
    voice near 3 kHz that carries over a hall."""
    def g(v):
        ring = 0.4 * np.exp(-0.5 * ((freqs - 3000) / 420) ** 2)
        return sum(a * np.exp(-0.5 * ((freqs - f) / (bw * 1.6)) ** 2) for f, a, bw in VOWELS[v]) + ring + 0.015
    return g(vowel) if vowel2 is None else g(vowel) * (1 - k) + g(vowel2) * k


def voice(rng, t, f0, vowel="a", vowel2=None, morph=None, vib=5.2, vib_depth=0.012, breath=0.08, harmonics=48, rough=0.0):
    """One sung voice by additive synthesis: harmonics of f0(t) shaped by a vowel's formants, with vibrato and jitter."""
    f0 = np.broadcast_to(np.asarray(f0, dtype=float), t.shape)
    jitter = lowpass(rng.standard_normal(len(t)), 8)
    jitter = jitter / (np.std(jitter) + 1e-9) * 0.001
    vrate = vib * (1 + 0.05 * np.sin(2 * np.pi * 0.3 * t + rng.random() * 6))
    fv = f0 * (1 + vib_depth * np.sin(2 * np.pi * phase(vrate) + rng.random() * 6) + jitter)
    ph = phase(fv)
    k = np.zeros(len(t)) if morph is None else morph
    x = np.zeros(len(t))
    for h in range(1, harmonics + 1):
        fh = fv * h
        if np.min(fh) > 9000:
            break
        # the formant gain moves with the harmonic as the pitch and the vowel move (sampled every 256 samples)
        idx = np.arange(0, len(t), 256)
        gk = formant_gain(fh[idx], vowel, k[idx], vowel2)
        gain = np.interp(np.arange(len(t)), idx, gk) / h ** 0.6
        x += gain * np.sin(2 * np.pi * h * ph + rng.random() * 6)
    if rough > 0:
        # a growl: the voice beats against itself at a low rate, as a throat strained to tearing
        x *= 1 + rough * np.sin(2 * np.pi * phase(np.full(len(t), 31.0) + 9 * lowpass(rng.standard_normal(len(t)), 3)))
    if breath > 0:
        b = noise(rng, len(t))
        bv = sum(band(b, f * 0.8, f * 1.2) * a for f, a, _ in VOWELS[vowel][:3])
        x += breath * bv / (np.std(bv) + 1e-9) * np.std(x)
    return x


def choir(rng, t, notes, dur, start=0.0, vowel="a", vowel2=None, voices=5, attack=0.12, release=0.6, spread=0.008, breath=0.12):
    """Many voices on each note, a little out of tune with each other and breathing: a choir holding a chord."""
    x = np.zeros(len(t))
    k = np.clip((t - start) / max(dur, 1e-3), 0, 1)
    for f in notes:
        for v in range(voices):
            det = 1 + rng.uniform(-spread, spread)
            x += voice(rng, t, f * det, vowel, vowel2, k if vowel2 else None, vib=4.6 + rng.random() * 1.4,
                       vib_depth=0.008 + rng.random() * 0.008, breath=breath, harmonics=36)
    shape = np.clip((t - start) / attack, 0, 1) * np.clip((start + dur + release - t) / release, 0, 1) * (t >= start)
    return x * shape / (len(notes) * voices) ** 0.5


def cry(rng, t, at, f_start, f_peak, f_end, dur, vowel="a", vowel2="o", rough=0.25, layers=3):
    """Her cry: a voice that leaps up and falls away, opening from one vowel to another, doubled an octave down and torn."""
    k = np.clip((t - at) / dur, 0, 1)
    f0 = np.where(k < 0.25, f_start + (f_peak - f_start) * (np.minimum(k, 0.25) / 0.25) ** 0.6,
                  f_peak + (f_end - f_peak) * (np.maximum(k - 0.25, 0) / 0.75) ** 1.3)
    shape = np.clip((t - at) / 0.04, 0, 1) * np.clip((at + dur - t) / (dur * 0.35), 0, 1) * (t >= at)
    x = np.zeros(len(t))
    for i in range(layers):
        det = 1 + (i - layers // 2) * 0.006
        x += voice(rng, t, f0 * det, vowel, vowel2, k, vib=6.0, vib_depth=0.018, breath=0.15, rough=rough)
    low = voice(rng, t, f0 * 0.5, "ah_low", "o", k, vib=5.0, vib_depth=0.01, breath=0.05, rough=rough * 1.6, harmonics=60)
    return (x / layers + 0.55 * low) * shape


def growl(rng, t, at, f0, dur, rough=0.7):
    """A low throat under her voice: a fundamental an octave or two down, torn and beating."""
    shape = np.clip((t - at) / 0.06, 0, 1) * np.clip((at + dur - t) / (dur * 0.4), 0, 1) * (t >= at)
    x = voice(rng, t, f0, "o", None, None, vib=3.0, vib_depth=0.02, breath=0.1, rough=rough, harmonics=80)
    return saturate(x * shape, 2.5)


def whisper(rng, t, at, dur, lo=1800, hi=7000):
    """A breath of words that are not words: band noise fluttering at a syllable's rate."""
    shape = gate(t, at, dur, 0.1, dur * 0.5)
    syll = 0.55 + 0.45 * np.sin(2 * np.pi * phase(7 + 3 * np.sin(2 * np.pi * 0.7 * t)))
    return band(noise(rng, len(t)), lo, hi) * shape * syll


# ------------------------------------------------------------------ bronze, threads and weight

BELL = [(0.5, 0.8, 2.4), (1.0, 1.0, 1.6), (1.183, 0.7, 1.2), (1.506, 0.5, 1.0), (2.0, 0.55, 0.9), (2.514, 0.4, 0.6),
        (2.662, 0.35, 0.55), (3.011, 0.3, 0.45), (4.166, 0.3, 0.3), (5.433, 0.25, 0.22), (6.796, 0.22, 0.17),
        (8.215, 0.18, 0.13), (9.874, 0.14, 0.1), (11.62, 0.11, 0.08), (13.97, 0.08, 0.06), (16.4, 0.06, 0.05)]


def bronze(rng, t, at, base, strength=1.0, decay=1.0, bright=1.0):
    """A struck piece of bronze (her crown, her rings): inharmonic partials beating as they ring out, and the strike."""
    x = np.zeros(len(t))
    tt = t - at
    for r, a, d in BELL:
        f = base * r * (1 + rng.uniform(-0.003, 0.003))
        if f > 12000:
            continue
        beat = 1 + 0.25 * np.sin(2 * np.pi * rng.uniform(0.6, 2.4) * tt)
        x += a * (bright if r > 2 else 1) * np.sin(2 * np.pi * f * tt + rng.random() * 6) * env(tt, 0.001, d * decay) * beat
    strike = band(noise(rng, len(t)), base * 2, 14000) * env(tt, 0.0005, 0.012)
    return strength * (x + 0.8 * strike)


def gong(rng, t, at, base, strength=1.0, decay=3.0):
    """A great gong for a turning of the fight: it swells after it is struck, and hums low a long while."""
    tt = t - at
    x = bronze(rng, t, at, base, strength, decay, 1.2)
    bloom = band(noise(rng, len(t)), base * 1.5, base * 9) * env(tt - 0.15, 0.6, decay * 0.5)
    hum = np.sin(2 * np.pi * base * 0.5 * tt) * env(tt, 0.05, decay * 1.2)
    return x + 0.4 * strength * bloom / (np.std(bloom[t > at]) + 1e-9) * 0.05 + 0.6 * strength * hum


def pluck(rng, t, at, freq, decay=0.8, bright=0.5, length=None):
    """A thick thread plucked (Karplus-Strong): low and heavy, its tone falling dark as it rings."""
    n = max(2, int(SR / freq))
    length = length or decay * 3
    m = int(SR * length)
    buf = band(rng.standard_normal(n * 4), 40, 4000 + 8000 * bright)[:n]
    out = np.zeros(m)
    keep = np.exp(np.log(0.001) / (decay * freq))
    mixk = 0.5 + 0.45 * bright
    b = buf.copy()
    pos = 0
    for i in range(m):
        out[i] = b[pos]
        nxt = (pos + 1) % n
        b[pos] = keep * (mixk * b[pos] + (1 - mixk) * b[nxt])
        pos = nxt
    x = np.zeros(len(t))
    s0 = int(at * SR)
    if s0 < len(t):
        seg = min(m, len(t) - s0)
        x[s0:s0 + seg] = out[:seg]
    return x


def snap(rng, t, at, strength=1.0):
    """A thread snapping or a whip cracking: a sharp broadband crack and a short bright tail."""
    tt = t - at
    crack = noise(rng, len(t)) * env(tt, 0.0002, 0.004)
    tail = band(noise(rng, len(t)), 2500, 14000) * env(tt, 0.001, 0.035)
    body = band(noise(rng, len(t)), 300, 2400) * env(tt, 0.0005, 0.02)
    return strength * (2.2 * crack + 0.9 * tail + 0.7 * body)


def boom(rng, t, at, f0=72, f1=26, dur=1.4, strength=1.0):
    """A cinematic hit: a sub-bass tone dropping in pitch, a click on top, thickened."""
    tt = t - at
    f1 = max(f1, 40)
    f = f1 + (f0 - f1) * np.exp(-np.maximum(tt, 0) / (dur * 0.15))
    x = np.sin(2 * np.pi * phase(f)) * env(tt, 0.002, dur * 0.24)
    # its upper harmonics, so it is heard on small speakers too, not only felt
    x += 0.6 * np.sin(2 * np.pi * phase(f * 2)) * env(tt, 0.002, dur * 0.12)
    x += 0.3 * np.sin(2 * np.pi * phase(f * 3)) * env(tt, 0.002, dur * 0.07)
    click = band(noise(rng, len(t)), 120, 2500) * env(tt, 0.0005, 0.025)
    return strength * saturate(x * (t >= at) + 0.8 * click, 2.2)


def braam(rng, t, at, f, dur, peak=0.6, bright=6.0, voices=7):
    """A great low brass-like swell: detuned saws an octave and a fifth apart, brightening as it swells."""
    shape = swell(t, at, at + dur * peak, at + dur)
    x = np.zeros(len(t))
    brightness = 1 + bright * shape
    for v in range(voices):
        root = f * (1 + rng.uniform(-0.01, 0.01)) * (1, 1, 2, 1.5, 0.5, 2, 1)[v % 7]
        ph = phase(np.full(len(t), root)) + rng.random()
        for h in range(1, 40):
            if root * h > 8000:
                break
            x += np.sin(2 * np.pi * h * ph) / h * np.exp(-h / brightness)
    return saturate(x * shape / voices, 2.2)


def creak(rng, t, at, dur, rate0=12, rate1=60, lo=350, hi=1600):
    """A rope pulled ever tighter: stick-slip clicks, ever faster, ringing in the thread's wood and fibre."""
    tt = np.clip((t - at) / dur, 0, 1)
    rate = rate0 + (rate1 - rate0) * tt ** 1.5
    ph = phase(rate) * (t >= at)
    clicks = np.zeros(len(t))
    edges = np.nonzero(np.diff(np.floor(ph)) > 0)[0]
    for e in edges:
        if t[e] > at + dur:
            break
        clicks[e] = 0.5 + rng.random()
    ring = sum(band(clicks, f * 0.9, f * 1.1) for f in (lo, (lo + hi) / 2, hi))
    return ring * gate(t, at, dur, 0.05, 0.05) * 30


def knots(rng, t, at, dur, count, accel=2.0, lo=900, hi=3200):
    """Knots tied one after another: short cord clicks, closer together as the knot is drawn tight."""
    x = np.zeros(len(t))
    for i in range(count):
        s = at + dur * (i / count) ** (1 / accel)
        f = lo + (hi - lo) * rng.random()
        x += band(noise(rng, len(t)), f * 0.8, f * 1.25) * env(t - s, 0.0005, 0.018) * (0.6 + 0.4 * rng.random())
        x += 0.4 * np.sin(2 * np.pi * f * 0.25 * (t - s)) * env(t - s, 0.001, 0.03)
    return x


def whoosh(rng, t, at, dur, f_from, f_to, width=0.35):
    """Air torn by something swept through it: band noise whose centre runs from f_from to f_to."""
    k = np.clip((t - at) / dur, 0, 1)
    centre = f_from * (f_to / f_from) ** k
    shape = np.sin(np.pi * k) ** 1.5 * (t >= at) * (t <= at + dur)
    return sweep_filter(rng, noise(rng, len(t)), centre, centre * width) * shape


def riser(rng, t, at, dur, f_from=300, f_to=5000, tone=110):
    """Everything drawn up to the moment a blow lands: rising noise, a rising tone, a tremolo ever faster."""
    k = np.clip((t - at) / dur, 0, 1)
    shape = k ** 2 * (t >= at) * (t <= at + dur)
    centre = f_from * (f_to / f_from) ** k
    air = sweep_filter(rng, noise(rng, len(t)), centre, centre * 0.5)
    trem = 0.6 + 0.4 * np.sin(2 * np.pi * phase(4 + 26 * k ** 2))
    pitch = tone * 2 ** (2 * k)
    tones = sum(np.sin(2 * np.pi * phase(pitch * r)) * a for r, a in [(1, 1), (1.5, 0.5), (2, 0.4), (3, 0.2)])
    return shape * trem * (air / (np.std(air) + 1e-9) * 0.25 + 0.3 * tones)


def hum(rng, t, at, dur, f=110, beat=1.3):
    """A thread's taut hum: a low tone beating against itself."""
    shape = gate(t, at, dur, dur * 0.3, dur * 0.4)
    return shape * (np.sin(2 * np.pi * f * t) + np.sin(2 * np.pi * (f + beat) * t) + 0.3 * np.sin(2 * np.pi * f * 2.003 * t))


def shatter(rng, t, at, spread, count, lo=1500, span=6000, life=0.1):
    """A rain of shards: brief inharmonic pings scattered after the break."""
    x = np.zeros(len(t))
    for _ in range(count):
        s = at + rng.random() ** 2 * spread
        f = lo + rng.random() * span
        g = sum(a * np.sin(2 * np.pi * f * r * (t - s) + rng.random() * 6) for r, a in [(1, 1), (2.32, .5), (4.25, .3)])
        x += g * env(t - s, 0.0005, life * (0.5 + rng.random() * 1.5)) * (0.3 + rng.random() * 0.7)
    return x


def thump(rng, t, at, f=52, strength=1.0):
    """A heartbeat of her core: a soft low thud."""
    tt = t - at
    return strength * np.sin(2 * np.pi * f * tt * (1 - 0.15 * np.minimum(tt, 0.2))) * env(tt, 0.006, 0.09)


def mix(*parts):
    n = max(len(p) for p in parts)
    out = np.zeros(n)
    for p in parts:
        out[: len(p)] += p
    return out


def norm(x, peak=1.0):
    return x / (np.max(np.abs(x)) + 1e-9) * peak


def master(rng, x, hall=2.6, wet=0.32, drive=1.6, squeeze=True, bright=7000):
    """Her hall, then a little heat and a compressor, so every sound of hers lands with weight."""
    x = norm(x).copy()
    f = int(len(x) * 0.12)
    x[-f:] *= np.linspace(1, 0, f) ** 1.5
    y = reverb(rng, x, hall, wet, bright)
    y = band(y, 38, 18000, 0.3)
    # presence: a lift over the upper voice and the bite of her blows, so she cuts through the music and the fight
    y = y + 0.7 * band(y, 2200, 12000)
    y = saturate(y, drive)
    if squeeze:
        y = compress(y, 0.35, 3.5, 0.003, 0.15)
    return y


# ------------------------------------------------------------------ the sounds

D = 146.83   # D3, her key: D minor, a semitone's rub above for dread


def km_voice(seed, chord):
    """Before each blow: a choir's stab, a growl under it, bronze on top. Short, so it never hides the blow."""
    rng = np.random.default_rng(seed)
    t = t_(1.6)
    c = choir(rng, t, chord, 0.55, 0.0, "a", "o", voices=4, attack=0.05, release=0.45)
    g = growl(rng, t, 0.0, chord[0] / 4, 0.7)
    b = bronze(rng, t, 0.0, chord[0] * 2, 0.5, 0.6)
    w = whisper(rng, t, 0.0, 0.6)
    x = mix(norm(c), 0.5 * norm(g), 0.35 * norm(b), 0.15 * norm(w), 0.5 * boom(rng, t, 0.0, 90, 40, 0.6))
    return master(rng, x, 2.0, 0.3), 2.4


def km_hurt(seed, pitch, clang):
    """Struck: a cry cut short, the clang of her crown, a thread twanging, a dull thud."""
    rng = np.random.default_rng(seed)
    t = t_(1.4)
    c = cry(rng, t, 0.0, 420 * pitch, 640 * pitch, 330 * pitch, 0.55, "a", "e", rough=0.35, layers=2)
    b = bronze(rng, t, 0.0, clang, 0.9, 0.5, 1.6)
    p = pluck(rng, t, 0.01, 147 * pitch, 0.5, 0.8)
    x = mix(norm(c), 0.45 * norm(b), 0.3 * norm(p), 0.4 * boom(rng, t, 0.0, 90, 45, 0.5), 0.35 * snap(rng, t, 0.0, 0.6))
    return master(rng, x, 1.8, 0.28), 2.0


def km_ambient(seed, notes):
    """At rest: a low choir humming through closed lips, threads creaking, her rings humming far above."""
    rng = np.random.default_rng(seed)
    t = t_(4.2)
    c = choir(rng, t, notes, 2.8, 0.2, "u", "o", voices=3, attack=0.9, release=1.0, breath=0.18)
    h = hum(rng, t, 0.0, 4.0, notes[0] * 0.75, 0.9)
    cr = creak(rng, t, 0.8, 1.6, 8, 22, 300, 1200)
    b = bronze(rng, t, 1.6, notes[0] * 4, 0.25, 1.4, 0.6)
    x = mix(norm(c), 0.35 * norm(h), 0.25 * norm(cr), 0.2 * norm(b), 0.12 * norm(whisper(rng, t, 1.0, 2.0, 2500, 8000)))
    return master(rng, x, 2.8, 0.4, 1.3, False), 5.2


def km_roar(seed):
    """Her roar: a cry torn up and held, a growl two octaves down, a braam swelling under, a boom and bronze crashing."""
    rng = np.random.default_rng(seed)
    t = t_(3.6)
    c = cry(rng, t, 0.05, 300, 640, 420, 2.4, "a", "o", rough=0.45, layers=4)
    c2 = cry(rng, t, 0.08, 300 * 1.498, 640 * 1.498, 420 * 1.498, 2.2, "e", "a", rough=0.3, layers=2)
    g = growl(rng, t, 0.05, 73.4, 2.6, 0.9)
    br = braam(rng, t, 0.0, D / 4, 2.8, 0.35, 7)
    ch = choir(rng, t, [D * 2, D * 2 * 1.189, D * 3, D * 4], 1.8, 0.15, "a", "o", voices=3, attack=0.25, release=0.9)
    b = bronze(rng, t, 0.05, 110, 1.0, 1.6) + bronze(rng, t, 0.09, 164, 0.7, 1.2)
    x = mix(norm(c), 0.45 * norm(c2), 0.6 * norm(g), 0.55 * norm(br), 0.35 * norm(ch), 0.4 * norm(b),
            0.9 * boom(rng, t, 0.05, 70, 24, 2.0), 0.4 * snap(rng, t, 0.05))
    return master(rng, x, 3.2, 0.38, 1.8), 5.4


def km_death(seed):
    """Her death: a long wail falling away, the choir sinking with her, her rings crashing down one by one, every thread
    snapping, two great booms, and a low drone that fades into the hall."""
    rng = np.random.default_rng(seed)
    t = t_(8.0)
    c = cry(rng, t, 0.1, 520, 700, 150, 4.6, "a", "u", rough=0.4, layers=4)
    sink = D * 2 * 2 ** (-np.clip((t - 0.6) / 4.0, 0, 1) * 1.5)
    ch = np.zeros(len(t))
    for r in (1, 1.189, 1.498, 2):
        for v in range(3):
            ch += voice(rng, t, sink * r * (1 + rng.uniform(-0.008, 0.008)), "o", "u", np.clip((t - 0.6) / 4.5, 0, 1), breath=0.15, harmonics=30)
    ch *= swell(t, 0.3, 1.2, 5.4)
    rings = sum(bronze(rng, t, at, base, 1.0, 1.4) for at, base in [(1.6, 196), (2.3, 147), (3.1, 110), (3.9, 82)])
    snaps = sum(snap(rng, t, at, 0.7) + 0.6 * pluck(rng, t, at, f, 0.5, 0.8) for at, f in
                [(0.4, 130), (0.9, 116), (1.3, 98), (2.0, 87), (2.7, 73), (3.4, 65)])
    drone = braam(rng, t, 3.0, D / 4, 4.8, 0.3, 3)
    x = mix(norm(c), 0.4 * norm(ch), 0.5 * norm(rings), 0.45 * norm(snaps), 0.45 * norm(drone), 0.9 * boom(rng, t, 0.1, 70, 24, 2.2),
            0.9 * boom(rng, t, 3.9, 60, 20, 3.0), 0.3 * norm(shatter(rng, t, 3.9, 1.5, 40, 1200, 6000, 0.15)))
    return master(rng, x, 4.0, 0.42, 1.7), 9.5


def km_step(seed, pitch):
    """She drifts: her robes sweep the floor, her rings clink, and the floor answers low."""
    rng = np.random.default_rng(seed)
    t = t_(1.2)
    w = whoosh(rng, t, 0.0, 0.7, 250 * pitch, 900 * pitch, 0.6)
    b = bronze(rng, t, 0.12, 330 * pitch, 0.35, 0.4)
    x = mix(norm(w), 0.3 * norm(b), 0.7 * boom(rng, t, 0.02, 60, 30, 0.5), 0.2 * norm(creak(rng, t, 0.05, 0.4, 20, 30)))
    return master(rng, x, 1.8, 0.3, 1.4, False), 1.8


def km_threadline_draw(seed):
    """Her Threadline drawn: a thread pulled ever tighter until it sings, everything rising to the moment she sweeps it
    (about a second, as her hand goes up)."""
    rng = np.random.default_rng(seed)
    t = t_(1.3)
    cr = creak(rng, t, 0.0, 1.0, 10, 80, 400, 1800)
    r = riser(rng, t, 0.0, 1.0, 250, 6000, D / 2)
    h = hum(rng, t, 0.1, 0.95, D, 2.0) * np.clip(t / 1.0, 0, 1) ** 2
    sing = np.sin(2 * np.pi * phase(D * 2 * 2 ** (np.clip(t, 0, 1) * 1.0))) * swell(t, 0.3, 0.98, 1.05)
    g = growl(rng, t, 0.0, 73.4, 0.9, 0.6) * 0.5
    x = mix(0.6 * norm(cr), norm(r), 0.5 * norm(h), 0.35 * sing, 0.4 * norm(g), 0.4 * norm(whisper(rng, t, 0.0, 0.9)))
    return master(rng, x, 1.6, 0.25), 1.6


def km_threadline_lash(seed):
    """Her Threadline swept: the crack of the thread drawn through the air, a boom, the rope ringing low, air torn."""
    rng = np.random.default_rng(seed)
    t = t_(2.4)
    s = snap(rng, t, 0.0, 1.0) + 0.6 * snap(rng, t, 0.02, 0.8)
    w = whoosh(rng, t, 0.0, 0.7, 6000, 400, 0.45)
    p = pluck(rng, t, 0.0, 110, 0.9, 0.9) + 0.6 * pluck(rng, t, 0.0, 165, 0.7, 0.9)
    zing = sum(np.sin(2 * np.pi * phase(f * 2 ** (-np.clip(t, 0, 0.6) * 2.5))) for f in (1400, 2100, 2950)) * env(t, 0.001, 0.25)
    x = mix(norm(s), norm(w), 0.45 * norm(p), 0.2 * norm(zing), 0.8 * boom(rng, t, 0.0, 85, 40, 1.4),
            0.4 * norm(shatter(rng, t, 0.05, 0.6, 24, 1500, 5000, 0.06)))
    return master(rng, x, 2.4, 0.3, 1.9), 3.2


def km_snare_weave(seed):
    """Her Snare laid: knots tied faster and faster, a shimmer rising, a choir whispering, until it closes (about a
    second and a half)."""
    rng = np.random.default_rng(seed)
    t = t_(1.9)
    k = knots(rng, t, 0.0, 1.5, 34, 2.4)
    shim = sum(np.sin(2 * np.pi * phase(f * 2 ** (np.clip(t / 1.5, 0, 1) * 0.5))) * 0.3 for f in (1320, 1760, 2349, 2637))
    shim *= swell(t, 0.0, 1.5, 1.6) * (0.6 + 0.4 * np.sin(2 * np.pi * phase(6 + 18 * np.clip(t / 1.5, 0, 1))))
    c = choir(rng, t, [D * 2, D * 2 * 1.06], 1.3, 0.1, "u", "e", voices=3, attack=0.6, release=0.2, breath=0.3)
    r = riser(rng, t, 0.2, 1.35, 500, 7000, D)
    x = mix(norm(k), 0.3 * norm(shim), 0.35 * norm(c), 0.5 * norm(r), 0.25 * norm(whisper(rng, t, 0.0, 1.5)))
    return master(rng, x, 1.8, 0.28), 2.2


def km_snare_close(seed):
    """Her Snare closing: a rope cinched with a creak and a heavy twang, her bronze clanging, the floor thudding."""
    rng = np.random.default_rng(seed)
    t = t_(1.9)
    cr = creak(rng, t, 0.0, 0.12, 60, 140, 500, 2000)
    p = pluck(rng, t, 0.1, 61.7, 1.0, 0.9) + 0.7 * pluck(rng, t, 0.1, 92.5, 0.8, 0.8)
    b = bronze(rng, t, 0.1, 196, 1.0, 0.9)
    x = mix(0.5 * norm(cr), 0.8 * norm(p), 0.5 * norm(b), snap(rng, t, 0.1, 0.9), boom(rng, t, 0.1, 75, 28, 1.3, 1.1))
    return master(rng, x, 2.2, 0.3, 1.9), 2.8


def km_cast(seed):
    """Her thread cast or her cradle strung (the blows she shares with the Last Weaver): threads plucked in a chord."""
    rng = np.random.default_rng(seed)
    t = t_(1.5)
    p = sum(pluck(rng, t, 0.02 * i, f, 0.7, 0.7) for i, f in enumerate((73.4, 87.3, 110, 146.8)))
    c = choir(rng, t, [D * 2, D * 2 * 1.189], 0.5, 0.0, "o", None, voices=3, attack=0.05, release=0.5)
    x = mix(norm(p), 0.4 * norm(c), 0.4 * norm(snap(rng, t, 0.0, 0.6)), 0.5 * boom(rng, t, 0.0, 70, 35, 0.7))
    return master(rng, x, 2.0, 0.3), 2.2


def km_anchor_hit(seed, base):
    """An anchor of hers struck: a heavy bronze clang, the thread from it humming back to her."""
    rng = np.random.default_rng(seed)
    t = t_(1.3)
    b = bronze(rng, t, 0.0, base, 1.0, 0.8, 2.6)
    h = hum(rng, t, 0.02, 1.0, base / 2, 3.0) * env(t, 0.02, 0.5)
    x = mix(norm(b), 0.2 * norm(h), 0.4 * boom(rng, t, 0.0, 95, 48, 0.45), 0.35 * norm(snap(rng, t, 0.0, 0.5)))
    return master(rng, x, 1.8, 0.28, 1.7), 2.0


def km_anchor_break(seed):
    """An anchor broken: a huge snap, shards raining, bronze crashing low, the thread whipping back to her, two booms."""
    rng = np.random.default_rng(seed)
    t = t_(3.6)
    s = snap(rng, t, 0.0, 1.0) + snap(rng, t, 0.015, 0.8)
    sh = shatter(rng, t, 0.0, 1.2, 70, 1200, 7000, 0.12)
    b = bronze(rng, t, 0.0, 98, 1.0, 1.8) + bronze(rng, t, 0.03, 147, 0.7, 1.4)
    w = whoosh(rng, t, 0.05, 0.6, 400, 4000, 0.4)
    p = pluck(rng, t, 0.0, 49, 1.5, 1.0)
    x = mix(norm(s), 0.5 * norm(sh), 0.6 * norm(b), 0.6 * norm(w), 0.5 * norm(p), boom(rng, t, 0.0, 70, 22, 2.2, 1.3),
            0.7 * boom(rng, t, 0.45, 55, 20, 1.8))
    return master(rng, x, 3.0, 0.35, 2.0), 4.8


def km_exposed(seed):
    """She lies exposed: her core thrumming like a heart, a crack of glass, a violet hum wavering, a gasp."""
    rng = np.random.default_rng(seed)
    t = t_(3.0)
    beats = sum(thump(rng, t, at, 48) + 0.7 * thump(rng, t, at + 0.22, 44) for at in (0.0, 0.85, 1.7))
    sh = shatter(rng, t, 0.0, 0.3, 14, 2500, 5000, 0.08)
    v = sum(np.sin(2 * np.pi * phase(f * (1 + 0.01 * np.sin(2 * np.pi * 5 * t)))) for f in (293.7, 311.1, 440)) * swell(t, 0.0, 0.6, 2.8)
    gasp = band(noise(rng, len(t)), 700, 3200) * swell(t, 0.0, 0.25, 0.5) * 0.8
    x = mix(norm(beats), 0.35 * norm(sh), 0.3 * norm(v), 0.4 * gasp, 0.5 * boom(rng, t, 0.0, 60, 30, 0.8))
    return master(rng, x, 2.4, 0.32, 1.8), 4.0


def km_phase(seed):
    """A turning of the fight (at 70% and 35%): a great gong, the choir rising to a cry, a braam, a boom, her threads drawn
    back to her."""
    rng = np.random.default_rng(seed)
    t = t_(5.0)
    g = gong(rng, t, 0.0, 73.4, 1.0, 3.5)
    ch = choir(rng, t, [D * 2, D * 2 * 1.189, D * 3, D * 4, D * 4 * 1.189], 2.2, 0.05, "o", "a", voices=4, attack=0.5, release=1.6)
    br = braam(rng, t, 0.0, D / 4, 3.8, 0.4, 6)
    w = whoosh(rng, t, 0.2, 1.4, 6000, 200, 0.4)
    x = mix(norm(g), 0.55 * norm(ch), 0.6 * norm(br), 0.3 * norm(w), boom(rng, t, 0.0, 66, 22, 2.6, 1.2))
    return master(rng, x, 3.6, 0.4, 1.7), 6.5


def km_ultimate(seed):
    """Her ultimate begins: everything drawn up, a choir crying out, the braam at its height, and the boom as it falls."""
    rng = np.random.default_rng(seed)
    t = t_(5.0)
    r = riser(rng, t, 0.0, 2.6, 150, 8000, D / 4)
    ch = choir(rng, t, [D, D * 1.189, D * 1.498, D * 2, D * 2 * 1.189, D * 3], 2.4, 0.4, "o", "a", voices=4, attack=1.8, release=1.4)
    br = braam(rng, t, 0.3, D / 4, 4.2, 0.55, 8)
    c = cry(rng, t, 2.55, 400, 700, 520, 1.8, "a", "o", rough=0.4, layers=3)
    x = mix(0.7 * norm(r), 0.5 * norm(ch), 0.6 * norm(br), 0.6 * norm(c), boom(rng, t, 2.6, 72, 22, 2.2, 1.3),
            0.4 * norm(gong(rng, t, 2.6, 98, 1.0, 2.5)), 0.5 * snap(rng, t, 2.6))
    return master(rng, x, 3.6, 0.38, 1.8), 6.8


def km_wake(seed):
    """She forms (seven seconds): a drone out of nothing, threads tightening, a heartbeat ever faster, the choir rising,
    everything drawn up to the moment she tears free."""
    rng = np.random.default_rng(seed)
    t = t_(6.4)
    br = braam(rng, t, 0.0, D / 4, 6.2, 0.98, 6)
    ch = choir(rng, t, [D, D * 1.189, D * 1.498, D * 2], 5.4, 0.6, "u", "a", voices=3, attack=4.5, release=0.3)
    beats = sum(thump(rng, t, at, 46) for at in np.cumsum([0.0] + [0.9 * 0.86 ** i for i in range(14)]) if at < 6.0)
    cr = creak(rng, t, 1.0, 5.0, 4, 50, 300, 1500)
    r = riser(rng, t, 3.2, 2.9, 200, 8000, D / 2)
    x = mix(0.6 * norm(br), 0.45 * norm(ch), 0.6 * norm(beats), 0.25 * norm(cr), 0.6 * norm(r), 0.15 * norm(whisper(rng, t, 0.5, 5.0)))
    return master(rng, x, 3.0, 0.35, 1.5, True), 6.6


SOUNDS = {
    "knot_mother_voice1": lambda: km_voice(101, [D * 2, D * 2 * 1.189, D * 3]),
    "knot_mother_voice2": lambda: km_voice(102, [D * 2 * 1.059, D * 2 * 1.26, D * 3 * 1.059]),
    "knot_mother_hurt1": lambda: km_hurt(111, 1.0, 196),
    "knot_mother_hurt2": lambda: km_hurt(112, 0.9, 175),
    "knot_mother_hurt3": lambda: km_hurt(113, 1.1, 220),
    "knot_mother_ambient1": lambda: km_ambient(121, [D, D * 1.498]),
    "knot_mother_ambient2": lambda: km_ambient(122, [D * 0.891, D * 1.335]),
    "knot_mother_roar": lambda: km_roar(131),
    "knot_mother_death": lambda: km_death(141),
    "knot_mother_step1": lambda: km_step(151, 1.0),
    "knot_mother_step2": lambda: km_step(152, 0.88),
    "knot_mother_threadline_draw": lambda: km_threadline_draw(161),
    "knot_mother_threadline_lash": lambda: km_threadline_lash(162),
    "knot_mother_snare_weave": lambda: km_snare_weave(171),
    "knot_mother_snare_close": lambda: km_snare_close(172),
    "knot_mother_cast": lambda: km_cast(181),
    "knot_mother_anchor_hit1": lambda: km_anchor_hit(191, 220),
    "knot_mother_anchor_hit2": lambda: km_anchor_hit(192, 196),
    "knot_mother_anchor_break": lambda: km_anchor_break(201),
    "knot_mother_exposed": lambda: km_exposed(211),
    "knot_mother_phase": lambda: km_phase(221),
    "knot_mother_ultimate": lambda: km_ultimate(231),
    "knot_mother_wake": lambda: km_wake(241),
}


def kweight(x):
    """Roughly how loud a sound is to the ear (the K-weighting of loudness meters): less of the lowest bass, a lift up
    top."""
    spec = np.fft.rfft(x)
    f = np.fft.rfftfreq(len(x), 1 / SR)
    hp = (f / 60) ** 2 / (1 + (f / 60) ** 2)
    shelf = 1 + (10 ** (4 / 20) - 1) * f ** 2 / (f ** 2 + 1500 ** 2)
    return np.fft.irfft(spec * hp * shelf, len(x))


def loudness(x):
    """The loudest 400 ms of a sound, K-weighted, in dB (close to short-term LUFS)."""
    k = kweight(x) ** 2
    w = int(SR * 0.4)
    st = np.convolve(k, np.ones(w) / w, "valid") if len(k) > w else np.array([k.mean()])
    return 10 * np.log10(st.max() + 1e-12) - 0.691


# How loud each kind of sound of hers should be at its loudest (dB, short-term loudness): her blows and her great moments
# land hardest, her voice a little under, her steps and her ambience well under
LOUDNESS = {"lash": -9, "close": -9, "break": -8.5, "anchor_hit": -10, "phase": -9, "ultimate": -9, "roar": -9, "death": -9.5,
            "wake": -10, "voice": -11, "hurt": -11, "cast": -11, "draw": -11, "weave": -11.5, "exposed": -11, "step": -15,
            "ambient": -16}


def save(name, x, keep):
    x = x[: int(SR * keep)].copy()
    target = next((v for k, v in LOUDNESS.items() if k in name), -11)
    x = norm(x, 0.97)
    gain = min(2.0, 10 ** ((target - loudness(x)) / 20))
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


def spectrogram(x, path):
    """A picture of a sound for checking it without ears: time across, frequency up (log), loudness as brightness."""
    from PIL import Image
    nfft, hop = 2048, 256
    win = np.hanning(nfft)
    frames = [np.abs(np.fft.rfft(x[s:s + nfft] * win)) for s in range(0, max(1, len(x) - nfft), hop)]
    s = 20 * np.log10(np.array(frames).T + 1e-6)
    f = np.fft.rfftfreq(nfft, 1 / SR)
    rows = 256
    edges = np.geomspace(30, 16000, rows + 1)
    img = np.zeros((rows, s.shape[1]))
    for i in range(rows):
        m = (f >= edges[i]) & (f < edges[i + 1])
        img[rows - 1 - i] = s[m].max(axis=0) if m.any() else s[np.argmin(np.abs(f - edges[i]))]
    img = np.clip((img - (img.max() - 80)) / 80, 0, 1)
    rms = np.sqrt(np.convolve(x ** 2, np.ones(hop) / hop, "same"))[::hop][: s.shape[1]]
    lane = np.zeros((48, s.shape[1]))
    for c, v in enumerate(rms):
        h = int(min(1, v / 0.5) * 47)
        lane[47 - h:, c] = 0.8
    Image.fromarray((np.vstack([img, lane]) * 255).astype(np.uint8)).resize((min(1600, s.shape[1] * 2), 608)).save(path)


def main():
    preview = sys.argv[sys.argv.index("--preview") + 1] if "--preview" in sys.argv else None
    only = [a for a in sys.argv[1:] if a.startswith("knot_mother_")]
    os.makedirs(OUT, exist_ok=True)
    for name, make in SOUNDS.items():
        if only and name not in only:
            continue
        x, keep = make()
        y = save(name, x, keep)
        print(f"{name}: {len(y) / SR:.2f} s, loudness {loudness(y):.1f}, peak {np.max(np.abs(y)):.2f}, bands {bands(y)}")
        if preview:
            os.makedirs(preview, exist_ok=True)
            spectrogram(y, os.path.join(preview, name + ".png"))


def bands(y):
    """Shares of a sound's energy: under 120 Hz (felt), 120-500 (body), 500-2000 (voice), 2-6 kHz (bite), over 6 kHz (air)."""
    spec = np.abs(np.fft.rfft(y)) ** 2
    f = np.fft.rfftfreq(len(y), 1 / SR)
    total = spec.sum() + 1e-12
    return " ".join(f"{spec[(f >= lo) & (f < hi)].sum() / total:.2f}" for lo, hi in [(0, 120), (120, 500), (500, 2000), (2000, 6000), (6000, 30000)])


if __name__ == "__main__":
    main()
