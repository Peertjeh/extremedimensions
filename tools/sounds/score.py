"""Instruments and helpers shared by the music discs, on top of the ambient voices (tools/sounds/ambient.py).

Each voice returns a mono note of its own length, to be laid into a master track with `add`. All of it is made from
tones and noise; nothing is sampled. Deterministic as long as the caller's seeds are.
"""
import os
import subprocess
import tempfile
import wave

import numpy as np

from ambient import (SR, band, bell_env, env, harm, lowpass, norm, partials, peaks, phase, t_, white)  # noqa: F401

# Equal temperament, A4 = 440 Hz: "C4", "Eb3", "F#2"...
_STEPS = {"C": -9, "D": -7, "E": -5, "F": -4, "G": -2, "A": 0, "B": 2}


def hz(name):
    """The frequency of a note name such as 'Eb3' or 'F#2'."""
    step = _STEPS[name[0]]
    rest = name[1:]
    while rest and rest[0] in "#b":
        step += 1 if rest[0] == "#" else -1
        rest = rest[1:]
    octave = int(rest)
    return 440.0 * 2 ** ((step + (octave - 4) * 12) / 12)


def add(master, at, x, gain=1.0):
    """Lays x into the master from second `at` on (what runs past the end is cut)."""
    i = int(at * SR)
    if i >= len(master) or i + len(x) <= 0:
        return
    if i < 0:
        x = x[-i:]
        i = 0
    n = min(len(x), len(master) - i)
    master[i:i + n] += x[:n] * gain


def mix(*xs):
    """Several notes of different lengths sounded together (the shorter ones padded with silence)."""
    n = max(len(x) for x in xs)
    out = np.zeros(n)
    for x in xs:
        out[:len(x)] += x
    return out


def vibrato(t, rate, depth, rng=None, delay=0.4):
    """A slow, human vibrato that grows in after `delay` seconds (depth as a fraction of the pitch)."""
    jitter = 0.0 if rng is None else rng.uniform(-0.6, 0.6)
    grow = np.clip(t / max(delay, 1e-3), 0, 1)
    return 1 + depth * grow * np.sin(phase(np.full(len(t), rate + jitter * 0.2)))


# Vowels as formants (centre Hz, width Hz, gain): a dark "oo", an open "ah" and a closed "mm"
VOWELS = {
    "oo": [(320, 80, 1.0), (800, 120, 0.45), (2300, 200, 0.12)],
    "ah": [(700, 110, 1.0), (1150, 130, 0.6), (2600, 220, 0.2)],
    "oh": [(480, 90, 1.0), (850, 110, 0.55), (2500, 200, 0.14)],
    "mm": [(260, 70, 1.0), (1900, 300, 0.05)],
}


def choir(rng, f, d, vowel="oo", voices=4, a=None, r=None, bright=1.0):
    """A small choir on one note: a few voices a little out of tune with each other, each with its own vibrato,
    shaped by a vowel's formants; it swells in and dies away slowly."""
    t = t_(d)
    a = d * 0.3 if a is None else a
    r = d * 0.35 if r is None else r
    x = np.zeros(len(t))
    for _ in range(voices):
        cents = rng.uniform(-9, 9)
        fv = f * 2 ** (cents / 1200) * vibrato(t, rng.uniform(4.6, 5.6), 0.006, rng)
        x += harm(fv, top=4200 * bright, tilt=1.05)
    x = peaks(x, VOWELS[vowel]) + lowpass(x, 400) * 0.25
    breath = band(white(rng, d), 600, 3500) * 0.04
    return norm(x + breath) * bell_env(t, 0.0, a, max(0.0, d - a - r), r)


def strings(rng, f, d, a=None, r=None, bright=1.0, voices=3):
    """Bowed strings in a section: slightly detuned, slow vibrato, the highs rounded off."""
    t = t_(d)
    a = d * 0.25 if a is None else a
    r = d * 0.35 if r is None else r
    x = np.zeros(len(t))
    for _ in range(voices):
        fv = f * 2 ** (rng.uniform(-6, 6) / 1200) * vibrato(t, rng.uniform(5.0, 6.0), 0.004, rng, 0.6)
        x += harm(fv, top=5000 * bright, tilt=1.0)
    x = lowpass(x, 1500 * bright + f, 2)
    return norm(x) * bell_env(t, 0.0, a, max(0.0, d - a - r), r)


def organ(f, d, a=1.5, r=2.5, stops=(1.0, 0.5, 0.25)):
    """A pipe organ's stops: the note and its octaves, steady, without vibrato."""
    t = t_(d)
    x = np.zeros(len(t))
    for k, g in enumerate(stops):
        x += harm(np.full(len(t), f * 2 ** k), top=3000, tilt=1.6) * g
    return norm(lowpass(x, 1800)) * bell_env(t, 0.0, a, max(0.0, d - a - r), r)


def horn(rng, f, d, a=0.25, r=None, bright=1.0):
    """A low horn: its brassiness opens as the note swells and closes as it fades."""
    t = t_(d)
    r = d * 0.4 if r is None else r
    e = bell_env(t, 0.0, a, max(0.0, d - a - r), r)
    fv = f * vibrato(t, rng.uniform(4.2, 5.0), 0.003, rng, 0.5)
    dark = lowpass(harm(fv, top=2500, tilt=1.4), 500 + f)
    brass = band(harm(fv, top=4000 * bright, tilt=0.8), 300, 2200)
    x = norm(dark) * (1 - 0.6 * e) + norm(brass) * 0.7 * e ** 1.5
    return norm(x) * e


def timpani(rng, f, d=3.0, gain=1.0, roll=0.0):
    """A kettle drum: a thump that settles onto its pitch, its partials a drum's, not a string's. A roll repeats it."""
    t = t_(d)
    hits = [0.0] if roll <= 0 else list(np.arange(0.0, roll, 0.07))
    x = np.zeros(len(t))
    for k, s in enumerate(hits):
        g = 1.0 if roll <= 0 else 0.35 + 0.65 * (k / max(1, len(hits) - 1))
        g *= rng.uniform(0.85, 1.0)
        pitch = f * (1 + 0.12 * np.exp(-np.maximum(t - s, 0) / 0.05))
        body = sum(np.sin(phase(pitch * r)) * gg * env(t, s, 0.003, dd)
                   for r, gg, dd in ((1.0, 1.0, 1.1), (1.5, 0.45, 0.6), (1.99, 0.3, 0.45), (2.44, 0.15, 0.3)))
        skin = lowpass(white(rng, d), 900) * env(t, s, 0.001, 0.03) * 0.5
        x += (body + skin) * g
    return norm(x) * gain


def marimba(f, d=1.6, gain=1.0):
    """A wooden bar struck soft: a strong fundamental and the bar's own high partials, dying fast."""
    t = t_(d)
    return partials(t, 0.0, f, [1.0, 3.93, 9.2], [0.55 * d / 1.6, 0.16, 0.05], [1.0, 0.35, 0.1]) * gain


def harp(f, d=3.0, gain=1.0):
    """A plucked string: harmonic partials, the upper ones dying first, and a soft pluck at the start."""
    t = t_(d)
    x = partials(t, 0.0, f, [1, 2, 3, 4, 5, 6], [d * 0.45, d * 0.3, d * 0.2, d * 0.14, d * 0.1, d * 0.07],
                 [1.0, 0.5, 0.3, 0.18, 0.1, 0.06])
    return x * np.clip(t / 0.004, 0, 1) * gain


def glass(f, d=4.0, gain=1.0):
    """A glass bell: near-harmonic partials that ring and beat slowly."""
    t = t_(d)
    return partials(t, 0.0, f, [1.0, 2.0, 2.99, 4.03, 5.41], [2.6, 1.6, 1.0, 0.7, 0.45], [1.0, 0.45, 0.25, 0.14, 0.08], beat=0.4) * gain


def sub(f, d, a=2.0, r=3.0):
    """A sine under everything, felt more than heard."""
    t = t_(d)
    return np.sin(phase(np.full(len(t), f))) * bell_env(t, 0.0, a, max(0.0, d - a - r), r)


def shepard(t, period, centre=200.0, octaves=7, base=20.0, down=True, sigma=1.3):
    """An endless glide: octave-spaced tones that slide one way forever, each fading in and out at the edges of
    hearing so the whole never arrives (Shepard-Risset). One octave every `period` seconds."""
    x = np.zeros(len(t))
    frac = (t / period) % 1.0
    if down:
        frac = 1.0 - frac
    lc = np.log2(centre / base)
    for k in range(octaves):
        oct_pos = k + frac
        f = base * 2 ** oct_pos
        a = np.exp(-0.5 * ((oct_pos - lc) / sigma) ** 2)
        x += np.sin(phase(f)) * a
    return x


def reverse_swell(rng, voice, tail=2.5):
    """A note played backwards with its echo: it comes up out of nothing and stops dead."""
    from ambient import reverb
    wet = reverb(rng, np.concatenate([voice, np.zeros(int(SR * tail))]), tail=tail, mix=0.7, bright=3000)
    return wet[::-1]


def export(x, out, mp3=False, quality=4):
    """Writes the master as mono 16-bit, then to Ogg Vorbis (and MP3 beside it with --mp3)."""
    with tempfile.TemporaryDirectory() as tmp:
        wav = os.path.join(tmp, "disc.wav")
        with wave.open(wav, "wb") as w:
            w.setnchannels(1)
            w.setsampwidth(2)
            w.setframerate(SR)
            w.writeframes((np.clip(x, -1, 1) * 32767).astype(np.int16).tobytes())
        os.makedirs(os.path.dirname(os.path.abspath(out)), exist_ok=True)
        subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", wav, "-c:a", "libvorbis", "-q:a", str(quality), out], check=True)
        if mp3:
            subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", wav, "-c:a", "libmp3lame", "-q:a", "3", out[:-4] + ".mp3"], check=True)
