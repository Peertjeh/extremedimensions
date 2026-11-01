"""The Abyss Answers: the fifth music disc, pressed from fragments the Abyssal curses leave as they break.

Two and a half minutes in no key at all (around B flat, leaning on the tritone). Out of silence a tone begins to fall
and never lands: an endless glide downward under everything. A low horn calls, B flat up to E; from far below
something answers it, an octave lower, played backwards. A slow pulse starts and quickens; strings press together
in clusters; swells rush up out of nothing into each beat. The call and its answer overlap, the glide pulls harder,
the dread rises to a chord of every wrong note, and then everything stops. In the silence the call comes once more,
high and thin and alone. And the abyss answers: close now, and very low.

Deterministic (fixed seeds). Builds on tools/sounds/ambient.py and score.py. Needs numpy and ffmpeg.
usage: python3 tools/sounds/disc_abyss.py [--out=FILE.ogg] [--mp3]
"""
import os
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from ambient import (SR, abyss_call, bell_env, brown, dread_rise, finish, lowpass, lp_resp, norm, reverb, sweep,  # noqa: E402
                     t_, thud, white)
from score import add, export, horn, hz, reverse_swell, shepard, strings, sub, timpani  # noqa: E402

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..",
                   "src/client/resources/assets/extremedimensions/sounds/music/the_abyss_answers.ogg")
LENGTH = 160.0

# The call: up a tritone and down a half step. The answer is the call an octave lower, backwards.
CALL = [("Bb2", 1.6), ("E3", 2.4), ("Eb3", 3.0)]
# The clusters the strings press into: half steps stacked on the tritone
CLUSTERS = [["Bb2", "B2", "E3", "F3"], ["A2", "Bb2", "Eb3", "E3"], ["B2", "C3", "F3", "Gb3"], ["Bb2", "B2", "E3", "F3", "Bb3", "B3"]]


def call(rng, at_pitch=1.0, gain=1.0):
    """The horn's call, its three notes slurred into one breath."""
    parts = []
    for name, d in CALL:
        parts.append(horn(rng, hz(name) * at_pitch, d + 0.5, a=0.18, r=0.6))
    out = np.zeros(int(SR * (sum(d for _, d in CALL) + 1.0)))
    s = 0.0
    for (name, d), x in zip(CALL, parts):
        i = int(s * SR)
        out[i:i + len(x)] += x[:len(out) - i]
        s += d
    return out * gain


def answer(rng, gain=1.0):
    """The answer: the call an octave lower, backwards, with its echo coming before it."""
    return reverse_swell(rng, call(rng, 0.5), tail=3.0) * gain


def cluster(rng, master, at, notes, d, gain):
    for n in notes:
        add(master, at, strings(rng, hz(n), d, a=d * 0.45, r=d * 0.3, bright=0.9, voices=3), gain)


def main():
    mp3 = "--mp3" in sys.argv
    out = next((a[len("--out="):] for a in sys.argv[1:] if a.startswith("--out=")), OUT)
    rng = np.random.default_rng(6601)
    total = LENGTH + 6.0
    t = t_(total)

    glide = np.zeros(len(t))
    bed = np.zeros(len(t))
    music = np.zeros(len(t))
    pulse = np.zeros(len(t))

    # The endless fall: one octave every 14 s at first, faster as the dread rises, gone at the cut (122 s)
    speed = np.clip(1.0 + (t - 50.0) / 70.0 * 1.4, 1.0, 2.4)
    warped = np.cumsum(speed) / SR
    fall = shepard(warped, 14.0, centre=180.0, octaves=8, base=18.0, down=True, sigma=1.2)
    glide += lowpass(fall, 2400) * bell_env(t, 0.0, 14.0, 106.0, 2.0) * (0.55 + 0.45 * np.clip((t - 40) / 70, 0, 1))
    cut = np.clip((t - 122.0) / 0.05, 0, 1)

    # The dark and the deep's call under it
    dark = sweep(brown(rng, total), lambda s, f: lp_resp(70 + 160 * (0.5 + 0.5 * np.sin(2 * np.pi * s / 31.0)) ** 2, 3)(f))
    bed += norm(dark) * 0.25 * bell_env(t, 0.0, 6.0, LENGTH - 12.0, 6.0)
    add(bed, 4.0, norm(abyss_call(np.random.default_rng(6610), 1)), 0.4)

    # The call, and the answer from below (20 s - 50 s)
    add(music, 20.0, call(rng), 0.42)
    add(music, 28.5, answer(rng), 0.5)
    add(music, 38.0, call(rng), 0.42)
    add(music, 45.0, answer(rng), 0.55)
    add(bed, 30.0, sub(hz("Bb0"), 30.0, a=6.0, r=6.0), 0.2)

    # The pulse (from 50 s): a heavy thud, 70 to the minute quickening to 120; a swell rushes into every fourth
    k, n = 50.0, 0
    while k < 121.5:
        gap = 60.0 / (70.0 + 50.0 * min(1.0, (k - 50.0) / 66.0))
        add(pulse, k, thud(rng, t_(1.0), 0.0, 120, 38, 0.32, 1.0), 0.55 + 0.35 * min(1.0, (k - 50) / 66))
        if n % 4 == 0 and k > 58:
            sw = reverse_swell(rng, lowpass(white(rng, 0.6), 3000) * np.exp(-np.arange(int(0.6 * SR)) / (0.08 * SR)), tail=1.8)
            add(pulse, k - len(sw) / SR, sw, 0.22)
        k += gap
        n += 1
    # Strings press together
    for at, notes, d in ((56.0, CLUSTERS[0], 14.0), (70.0, CLUSTERS[1], 14.0), (84.0, CLUSTERS[2], 14.0), (98.0, CLUSTERS[3], 24.0)):
        cluster(rng, music, at, notes, d, 0.12 + 0.03 * (at - 56) / 14)
    # Call and answer overlap, nearer each time
    for at, g in ((64.0, 0.45), (78.0, 0.5), (90.0, 0.55), (104.0, 0.65), (112.0, 0.7)):
        add(music, at, call(rng, 1.0 if at < 100 else 2.0), g)
        add(music, at + 4.0, answer(rng), g)
    add(pulse, 114.0, timpani(rng, hz("Bb1"), 8.0, roll=8.0), 0.55)
    # The dread swells into the cut
    add(bed, 113.4, norm(dread_rise(np.random.default_rng(6611), 1)), 0.75)

    # Everything stops at 122 s
    music *= 1 - cut
    pulse *= 1 - cut
    glide *= 1 - cut
    bed *= np.where(t < 122.0, 1.0, np.clip((t - 126.0) / 4.0, 0, 1) * 0.6)

    # After the silence: the call alone, high and thin; and the answer, close now, very low
    add(music, 128.0, call(np.random.default_rng(6620), 2.0) * np.linspace(1, 0.6, int(SR * 8.0)), 0.3)
    add(music, 138.0, answer(np.random.default_rng(6621)), 0.75)
    add(bed, 140.0, sub(hz("Bb0"), 18.0, a=1.0, r=8.0), 0.38)
    add(bed, 141.0, norm(abyss_call(np.random.default_rng(6622), 2)), 0.45)

    wet = reverb(rng, music, tail=6.0, mix=0.55, bright=3000) + reverb(rng, pulse, tail=3.0, mix=0.4, bright=1500)
    x = bed + reverb(rng, glide, tail=4.0, mix=0.4, bright=2500) * 0.8 + wet
    x *= bell_env(t, 0.0, 1.5, LENGTH + 1.0, 5.0)
    x = finish(x, rms_db=-19.0)
    export(x, out, mp3)
    print(f"{out}: {len(x) / SR:.1f} s")


if __name__ == "__main__":
    main()
