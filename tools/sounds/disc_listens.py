"""What Listens: the third music disc, the deep's song (the Cave Lurker and the Sculk Lurker).

E Phrygian, about two and a half minutes. Down where the torches give out, something listens. A drone in the stone and
water dripping; dry clicks in the dark. A bowed bass treads E-F-E, the half step that never settles, and above it a
sculk-cyan bell asks its question and waits. The heart of something vast begins to beat; the clicks quicken the way the
Cave Lurker's do as it comes; the strings climb, and a shrieker screams. Silence. Then only the heartbeat, and the
question again, low and slow, until it rests on E.

Deterministic (fixed seeds). Builds on tools/sounds/ambient.py and score.py. Needs numpy and ffmpeg.
usage: python3 tools/sounds/disc_listens.py [--out=FILE.ogg] [--mp3]
"""
import os
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from ambient import (SR, band, bell_env, brown, crackle, env, finish, harm, heartbeat, lowpass, lp_resp, norm,  # noqa: E402
                     peaks, phase, reverb, sweep, t_, white, far_shriek, deep_groan)
from score import add, export, glass, hz, strings, sub, timpani  # noqa: E402

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..",
                   "src/client/resources/assets/extremedimensions/sounds/music/what_listens.ogg")
LENGTH = 146.0
BEAT = 0.75  # 80 to the minute

# The question (on the sculk bell) and its low answer
QUESTION = [("E4", 2), ("F4", 1), ("E4", 3), ("B3", 4)]
ANSWER = [("G3", 2), ("F3", 2), ("E3", 6)]
# Chords: E minor and the Phrygian F above it; D minor leans back into it
CHORDS = {"Em": ["E2", "B2", "E3", "G3"], "F": ["F2", "C3", "F3", "A3"], "Dm": ["D2", "A2", "D3", "F3"],
          "Em_hi": ["E3", "B3", "E4", "G4"], "F_hi": ["F3", "C4", "F4", "A4"], "C": ["C3", "G3", "C4", "E4"]}


def bowed_bass(rng, f, d):
    """A low bowed string, rosin and all: the attack scrapes a moment before the note blooms."""
    t = t_(d)
    tone = strings(rng, f, d, a=0.12, r=d * 0.45, bright=0.6, voices=2)
    scrape = band(white(rng, d), 300, 1800) * env(t, 0.0, 0.01, 0.09) * 0.25
    return tone + scrape


def sculk_bell(f, d=5.0, gain=1.0):
    """A sculk-cyan bell: glassy, with a faint second strike a hair later, as a sensor clicks twice."""
    x = glass(f, d, 1.0) + np.concatenate([np.zeros(int(SR * 0.045)), glass(f * 2.0, d, 0.18)])[:int(SR * d)]
    return x * gain


def clicks(rng, d, rate_at):
    """Dry clicks in the dark, at a rate that changes over time (rate_at: seconds -> clicks per second)."""
    t = t_(d)
    n = len(t)
    x = np.zeros(n)
    s = 0.0
    while s < d:
        r = max(0.05, rate_at(s))
        s += rng.exponential(1.0 / r) * 0.6 + 0.4 / r
        i = int(s * SR)
        if i < n:
            x[i] = rng.uniform(0.5, 1.0) * (1 if rng.random() < 0.5 else -1)
    k = int(SR * 0.03)
    kern = np.exp(-np.arange(k) / (SR * 0.0035)) * rng.standard_normal(k)
    return peaks(np.convolve(x, kern)[:n], [(1900, 500, 1.0), (3400, 700, 0.5), (900, 200, 0.4)])


def drips(rng, d):
    """Water finding its way through the stone: a drop now and then, ringing in a small hollow."""
    t = t_(d)
    x = np.zeros(len(t))
    s = rng.uniform(0.5, 2.0)
    while s < d - 1:
        f = rng.uniform(900, 1700)
        u = np.maximum(t - s, 0)
        x += np.sin(phase(f * (1 + 0.6 * np.exp(-u / 0.012)))) * env(t, s, 0.001, 0.05) * rng.uniform(0.3, 0.8)
        s += rng.uniform(1.6, 4.5)
    return x


def play(master, at, line, voice, beat=BEAT):
    s = at
    for name, beats in line:
        add(master, s, voice(hz(name), beats * beat))
        s += beats * beat


def chord(rng, master, at, name, d, gain, bright=0.8):
    for k, n in enumerate(CHORDS[name]):
        add(master, at, strings(rng, hz(n), d, a=d * 0.3, r=d * 0.4, bright=bright), gain * (1.0 if k == 0 else 0.7))


def main():
    mp3 = "--mp3" in sys.argv
    out = next((a[len("--out="):] for a in sys.argv[1:] if a.startswith("--out=")), OUT)
    rng = np.random.default_rng(4401)
    total = LENGTH + 6.0
    t = t_(total)

    bed = np.zeros(len(t))      # drone, stone, water
    dark = np.zeros(len(t))     # clicks and the deep's sounds
    music = np.zeros(len(t))
    pulse = np.zeros(len(t))

    # The stone's drone and the air moving in the tunnels (the drone thins to its root after the silence)
    air = sweep(brown(rng, total), lambda s, f: lp_resp(90 + 220 * (0.5 + 0.5 * np.sin(2 * np.pi * s / 19.0)) ** 2, 3)(f))
    bed += norm(air) * 0.2 * bell_env(t, 0.0, 8.0, LENGTH - 16.0, 8.0)
    # (the drone grows with the dread, from a breath at the start to its full weight at the shriek)
    grow = 0.45 + 0.55 * np.clip((t_(98.0) - 10.0) / 85.0, 0, 1) ** 1.5
    add(bed, 0.0, sub(hz("E1"), 98.0, a=10.0, r=4.0) * grow, 0.22)
    add(bed, 0.0, norm(lowpass(harm(np.full(int(98 * SR), hz("E1")), top=500, tilt=1.5), 220)) * bell_env(t_(98.0), 0.0, 10.0, 84.0, 4.0) * grow, 0.16)
    add(bed, 0.0, norm(drips(np.random.default_rng(4410), 100.0)), 0.18)
    add(dark, 1.0, norm(deep_groan(np.random.default_rng(4411), 2)), 0.4)

    # The clicks: sparse at first, quickening from 60 s as something comes, cut off at the shriek
    rate = lambda s: 0.25 if s < 40 else (0.25 + 0.6 * (s - 40) / 20 if s < 60 else 0.85 + 10.0 * ((s - 60) / 36) ** 2)
    cl = clicks(np.random.default_rng(4412), 97.0, rate)
    cl *= np.clip((97.0 - t_(97.0)) / 0.3, 0, 1)
    add(dark, 0.0, norm(cl), 0.32)

    # The bass treads E-F-E from 12 s: two beats each, a beat's rest
    s = 12.0
    while s < 94.0:
        for name, beats in (("E2", 2), ("F2", 1), ("E2", 3)):
            add(music, s, bowed_bass(rng, hz(name), beats * BEAT + 0.4), 0.4 if s < 60 else 0.6)
            s += beats * BEAT
        s += 2 * BEAT

    # The question on the sculk bell, and its low answer
    play(music, 21.0, QUESTION, lambda f, d: sculk_bell(f, 5.0, 0.42))
    play(music, 30.0, ANSWER, lambda f, d: sculk_bell(f, 6.0, 0.36))
    play(music, 39.0, QUESTION, lambda f, d: sculk_bell(f, 5.0, 0.42))

    # Strings come in under it: Em - F - Dm - Em, then higher and closer
    for at, name in ((42.0, "Em"), (48.0, "F"), (54.0, "Dm"), (60.0, "Em")):
        chord(rng, music, at, name, 7.0, 0.22)
    for k, (at, name) in enumerate(((66.0, "Em"), (72.0, "F"), (78.0, "C"), (84.0, "F_hi"), (90.0, "F_hi"))):
        chord(rng, music, at, name, 7.0, 0.26 + 0.03 * k, bright=1.0)
    play(music, 66.0, [("B4", 2), ("C5", 1), ("B4", 3), ("E4", 4)], lambda f, d: sculk_bell(f, 4.5, 0.36))
    play(music, 75.0, QUESTION, lambda f, d: sculk_bell(f * 2, 4.0, 0.3))

    # The heart of something vast, from 50 s; it beats faster as the clicks do
    k = 50.0
    while k < 96.0:
        add(pulse, k, heartbeat(rng, t_(1.2), 0.0, 34, 1.2), 0.55 if k < 70 else 0.75)
        k += 1.6 if k < 70 else max(0.85, 1.6 - (k - 70) * 0.03)
    # Drums on the climb
    for at in np.arange(78.0, 96.0, 3 * BEAT):
        add(pulse, at, timpani(rng, hz("E2"), 2.4), 0.35 + 0.25 * (at - 78) / 18)
    add(pulse, 92.0, timpani(rng, hz("E2"), 4.0, roll=4.0), 0.5)

    # The shriek, and the silence after it
    shriek = far_shriek(np.random.default_rng(4413), 1)
    add(dark, 95.2, norm(highpass_like(shriek)), 0.65)
    fall = np.clip((t - 96.0) / 0.25, 0, 1)
    back = np.clip((t - 104.0) / 6.0, 0, 1)
    music *= 1.0 - fall
    pulse *= 1.0 - fall
    bed *= np.where(t < 104.0, 1.0 - fall * 0.9, 0.1 + 0.9 * back)

    # After it: only the heartbeat, slow; then the question again, low and slow, coming to rest on E
    for k in (104.0, 106.4, 108.8, 111.2, 113.6):
        add(pulse, k, heartbeat(rng, t_(1.2), 0.0, 32, 1.2), 0.7)
    add(bed, 104.0, sub(hz("E1"), 40.0, a=6.0, r=10.0), 0.2)
    play(music, 112.0, QUESTION, lambda f, d: sculk_bell(f / 2, 6.0, 0.36), beat=1.0)
    play(music, 123.0, [("G3", 2), ("F3", 2), ("E3", 8)], lambda f, d: sculk_bell(f, 7.0, 0.3), beat=1.0)
    chord(rng, music, 124.0, "Em", 16.0, 0.2, bright=0.6)
    add(music, 131.0, sculk_bell(hz("E5"), 9.0, 0.16))
    add(dark, 132.0, norm(clicks(np.random.default_rng(4414), 6.0, lambda s: 0.6)), 0.12)

    wet = reverb(rng, music, tail=5.5, mix=0.5, bright=3800) + reverb(rng, pulse, tail=2.6, mix=0.35, bright=1400)
    x = bed + reverb(rng, dark, tail=3.5, mix=0.45, bright=4000) + wet
    x *= bell_env(t, 0.0, 1.5, LENGTH + 1.0, 5.0)
    x = finish(x, rms_db=-19.0)
    export(x, out, mp3)
    print(f"{out}: {len(x) / SR:.1f} s")


def highpass_like(x):
    """The shriek, its rumble taken off so it cuts through."""
    from ambient import highpass
    return highpass(x, 400)


if __name__ == "__main__":
    main()
