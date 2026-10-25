"""BattleHub's own sound effects (1.49), synthesised like its music (compose.py): the cannon of the Hunger Games when a
tribute falls, what the fallen hear themselves, and the sounds of the night's tribute (the Capitol's seal at its start,
the emblem rising, a funeral bell for each of the fallen). Writes src/main/resources/battlehub/sfx/<id>.ogg.

    python tools/music/sfx.py [id ...]

Without the resource pack the mod plays its own mix of game sounds instead (fx/Sfx.java).
"""
import math
import os
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from compose import RATE, additive, band, freq, master_eq, noise, reverb_ir, saw, vowel, write_ogg  # noqa: E402

OUT = os.path.join(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))), "src", "main", "resources",
                   "battlehub", "sfx")


def t_of(seconds):
    return np.arange(int(seconds * RATE)) / RATE


def lowpass(sig, hi):
    return band(sig, 20, hi)


def place(mix, sig, at):
    start = int(at * RATE)
    end = min(len(mix), start + len(sig))
    mix[start:end] += sig[: end - start]


def wet(mono, seconds=3.2, amount=0.4, seed=11):
    """The sound in a big space: stereo, with a reverb tail."""
    ir = reverb_ir(seconds, seed)
    n = len(mono) + ir.shape[1]
    size = 1 << int(math.ceil(math.log2(n)))
    out = []
    for c in range(2):
        tail = np.fft.irfft(np.fft.rfft(mono, size) * np.fft.rfft(ir[c], size), size)[:n]
        dry = np.zeros(n)
        dry[: len(mono)] = mono
        out.append(dry + amount * tail)
    return np.stack(out)


def finish(stereo, peak=0.9, limit=5.0):
    # mastered as the music: the deep lows down, so a boom carries on small speakers too
    stereo = master_eq(stereo)
    stereo = stereo / (np.max(np.abs(stereo)) or 1)
    stereo = np.tanh(stereo * 1.3) / math.tanh(1.3) * peak
    level = np.max(np.abs(stereo), axis=0)
    last = np.nonzero(level > 0.002)[0]
    end = (last[-1] + int(0.15 * RATE)) if len(last) else stereo.shape[1]
    end = min(end, stereo.shape[1], int(limit * RATE))
    fade = np.ones(end)
    k = min(end, int(0.6 * RATE))
    fade[-k:] = np.linspace(1, 0, k)
    return stereo[:, :end] * fade


def cannon():
    """A cannon far away over the arena: the crack, the boom that falls in pitch, and its echo off the hills."""
    mix = np.zeros(int(5.0 * RATE))
    t = t_of(1.2)
    sweep = 2 * math.pi * np.cumsum(38 + 45 * np.exp(-t * 9)) / RATE
    # driven hard, so the boom has overtones a small speaker can play
    place(mix, np.tanh(2.8 * np.sin(sweep)) * np.exp(-t * 3.2) * 0.8, 0.0)
    crack = lowpass(noise(int(0.3 * RATE), 3), 2500) * np.exp(-t_of(0.3) * 28) * 0.9
    place(mix, crack, 0.0)
    body = band(noise(int(1.5 * RATE), 4), 60, 900) * np.exp(-t_of(1.5) * 3.5) * 0.9
    place(mix, body, 0.01)
    # the echoes: later, darker, softer
    for at, g, hi in ((0.42, 0.35, 380), (0.95, 0.22, 300), (1.6, 0.12, 240)):
        place(mix, lowpass(body, hi) * g, at)
    return finish(wet(mix, 4.0, 0.45, 21), limit=4.5)


def fallen():
    """What the fallen hear: a heartbeat that stops, and a low chord sinking away."""
    mix = np.zeros(int(4.2 * RATE))
    for at in (0.0, 0.32, 1.0, 1.34):
        t = t_of(0.25)
        thump = np.tanh(2.5 * np.sin(2 * math.pi * np.cumsum(70 + 30 * np.exp(-t * 30)) / RATE)) * np.exp(-t * 18)
        thump[: int(0.004 * RATE)] += band(noise(int(0.004 * RATE), 5), 1000, 4000) * 0.3
        place(mix, thump * (1.0 if at < 0.5 else 0.6), at)
    dur = 2.6
    t = t_of(dur + 0.8)
    chord = np.zeros(len(t))
    for m, g in ((38, 1.0), (41, 0.7), (45, 0.5), (50, 0.35)):
        # sinking a semitone over the chord
        f = freq(m) * 2 ** (-np.clip(t / dur, 0, 1) / 12)
        phase = 2 * math.pi * np.cumsum(f) / RATE
        tone = sum(a * np.sin(k * phase) for k, a in enumerate(saw(10, 1.3), start=1))
        chord += g * tone
    chord *= np.clip(t / 0.6, 0, 1) * np.exp(-np.maximum(0, t - dur) * 4) * 0.25
    place(mix, lowpass(chord, 1800), 1.3)
    return finish(wet(mix, 3.0, 0.5, 23), peak=0.8)


def toll():
    """A funeral bell (a bell's partials: hum, prime, minor third, fifth, octave and above), struck once."""
    f0 = freq(55)  # G3
    dur = 5.0
    t = t_of(dur)
    sig = np.zeros(len(t))
    for ratio, amp, decay in ((0.5, 0.8, 0.6), (1.0, 1.0, 0.9), (1.19, 0.6, 1.2), (1.5, 0.4, 1.3), (2.0, 0.7, 1.6),
                              (2.51, 0.3, 2.2), (2.66, 0.25, 2.4), (3.01, 0.2, 2.8), (4.0, 0.15, 3.5)):
        sig += amp * np.sin(2 * math.pi * f0 * ratio * t + ratio) * np.exp(-t * decay)
    strike = band(noise(int(0.05 * RATE), 8), 800, 6000) * np.exp(-t_of(0.05) * 80)
    sig[: len(strike)] += strike * 0.6
    sig *= np.clip(t / 0.003, 0, 1) * 0.5
    return finish(wet(sig, 3.5, 0.45, 25), peak=0.8, limit=6.0)


def seal():
    """The Capitol's seal before the night's tribute: a roll of the timpani that swells into a deep brass chord."""
    mix = np.zeros(int(4.5 * RATE))
    roll = band(noise(int(1.2 * RATE), 9), 60, 260)
    t = t_of(1.2)
    roll *= (0.5 + 0.5 * np.sin(2 * math.pi * 14 * t) ** 2) * (t / 1.2) ** 1.5 * 0.9
    place(mix, roll, 0.0)
    hit = np.sin(2 * math.pi * np.cumsum(52 + 30 * np.exp(-t_of(1.0) * 12)) / RATE) * np.exp(-t_of(1.0) * 4)
    place(mix, hit, 1.2)
    for m in (36, 43, 48, 52, 55):
        brass = additive(freq(m), 1.6, saw(18, 1.0), 0.04, 0.9, vibrato=0.002, detune=(-5, 5),
                         bright=lambda tt: 0.03 + 0.05 * np.clip(tt / 1.0, 0, 1))
        place(mix, brass * 0.18, 1.2)
    return finish(wet(mix, 3.2, 0.4, 27))


def rise():
    """The emblem rising: a shimmer that climbs, voices fading in, and a bright chime when it stands."""
    mix = np.zeros(int(4.5 * RATE))
    dur = 2.2
    nz = noise(int(dur * RATE), 12)
    t = t_of(dur)
    # a climbing band of noise, done in short pieces
    shimmer = np.zeros(len(nz))
    pieces = 22
    step = len(nz) // pieces
    for i in range(pieces):
        lo = 400 * (12 ** (i / pieces))
        seg = band(nz[i * step:(i + 1) * step + step], lo, lo * 2.2)[:step]
        shimmer[i * step:(i + 1) * step] = seg[: len(shimmer[i * step:(i + 1) * step])]
    shimmer *= (t / dur) ** 2 * 0.5
    place(mix, shimmer, 0.0)
    for m in (60, 64, 67, 72):
        place(mix, additive(freq(m), 2.0, vowel(freq(m), 24), 1.2, 1.2, vibrato=0.004, detune=(-6, 0, 6)) * 0.12, 0.4)
    for k, m in enumerate((84, 88, 91, 96)):
        place(mix, additive(freq(m), 0.05, [1, 0, 0.3, 0, 0.1], 0.002, 1.6, decay=2.5) * 0.25, 2.2 + 0.06 * k)
    return finish(wet(mix, 3.5, 0.5, 29), peak=0.85)


SOUNDS = {"cannon": cannon, "fallen": fallen, "toll": toll, "seal": seal, "rise": rise}


def main(only=None):
    os.makedirs(OUT, exist_ok=True)
    for name, make in SOUNDS.items():
        if only and name not in only:
            continue
        stereo = make()
        path = os.path.join(OUT, name + ".ogg")
        write_ogg(path, stereo)
        print("%-8s %.1f s  %d bytes" % (name, stereo.shape[1] / RATE, os.path.getsize(path)))


if __name__ == "__main__":
    main(sys.argv[1:])
