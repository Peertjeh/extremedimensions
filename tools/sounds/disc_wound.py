"""What the Wound Remembers: the second music disc, made from the wound's own ambient sounds.

A slow piece in D minor, about two and a half minutes. Wind and a low drone; the deep groans. Chords rise under a
motif on glass bells (D F E C D), answered lower; a knot's heartbeat keeps time. The undertow pulls, something giggles
far off and heavy steps pass below. Then the sky tears, basalt cracks, something calls from beneath, and the dread
swells into silence. After it one heartbeat, and the motif comes back alone, high and thin, and comes to rest.

Deterministic (fixed seeds). Builds on tools/sounds/ambient.py. Needs numpy and ffmpeg (libvorbis, libmp3lame for --mp3).
usage: python3 tools/sounds/disc_wound.py [--out=FILE.ogg] [--mp3]
"""
import os
import subprocess
import sys
import tempfile
import wave

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from ambient import (SR, abyss_call, band, basalt_deep, bell_env, brown, deep_groan, dread_rise, env, far_steps,  # noqa: E402
                     finish, harm, heartbeat, hollow_far, lowpass, norm, partials, phase, reverb, storm_tear, sweep,
                     t_, undertow, lp_resp)

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..",
                   "src/client/resources/assets/extremedimensions/sounds/music/wound_remembers.ogg")

LENGTH = 150.0
# Notes (Hz)
N = {"D1": 36.71, "A1": 55.0, "Bb1": 58.27, "G1": 49.0, "C#2": 69.3, "D2": 73.42, "E2": 82.41, "F2": 87.31, "G2": 98.0,
     "A2": 110.0, "Bb2": 116.54, "C3": 130.81, "C#3": 138.59, "D3": 146.83, "E3": 164.81, "F3": 174.61, "G3": 196.0,
     "A3": 220.0, "Bb3": 233.08, "C4": 261.63, "D4": 293.66, "E4": 329.63, "F4": 349.23, "A4": 440.0, "D5": 587.33,
     "E5": 659.26, "F5": 698.46, "C5": 523.25}
CHORDS = {"Dm": ["D2", "A2", "D3", "F3"], "Bb": ["Bb1", "F2", "Bb2", "D3"], "Gm": ["G1", "D2", "G2", "Bb2"],
          "A": ["A1", "E2", "A2", "C#3"]}
MOTIF = [("D4", 2), ("F4", 1), ("E4", 1), ("C4", 2), ("D4", 2)]
ANSWER = [("A3", 2), ("Bb3", 1), ("A3", 1), ("G3", 2), ("E3", 1), ("C#3", 1)]


def add(master, at, x, gain=1.0):
    """Lays x into the master from second `at` on."""
    i = int(at * SR)
    if i >= len(master):
        return
    n = min(len(x), len(master) - i)
    master[i:i + n] += x[:n] * gain


def glass(f, d=4.0, gain=1.0):
    """A glass bell: near-harmonic partials that ring and beat slowly."""
    t = t_(d)
    x = partials(t, 0.0, f, [1.0, 2.0, 2.99, 4.03, 5.41], [2.6, 1.6, 1.0, 0.7, 0.45], [1.0, 0.45, 0.25, 0.14, 0.08], beat=0.4)
    return x * gain


def low_bell(rng, f, d=7.0):
    """The Ferryman's kind of bell, low and dark."""
    t = t_(d)
    ratios = [0.5, 1.0, 1.19, 1.56, 2.0, 2.51, 3.01]
    decays = [6.0, 4.0, 3.2, 2.4, 2.0, 1.3, 0.9]
    gains = [0.8, 1.0, 0.65, 0.4, 0.4, 0.22, 0.12]
    return lowpass(partials(t, 0.0, f, ratios, decays, gains, beat=0.3), 2400)


def pad(chord, d):
    """Strings in the dark: each note a slow-breathing harmonic tone, the highs taken off."""
    t = t_(d)
    x = np.zeros(len(t))
    for k, name in enumerate(CHORDS[chord]):
        f = N[name] * (1 + 0.0025 * np.sin(phase(np.full(len(t), 4.6 + 0.3 * k))))
        x += norm(lowpass(harm(f, top=1800, tilt=1.25), 700 + 120 * k, 2)) * (0.9 if k == 0 else 0.6)
    return norm(x) * bell_env(t, 0.0, d * 0.35, d * 0.3, d * 0.35)


def play(master, at, line, beat, voice):
    s = at
    for name, beats in line:
        add(master, s, voice(N[name], beats * beat))
        s += beats * beat


def main():
    mp3 = "--mp3" in sys.argv
    out = next((a[len("--out="):] for a in sys.argv[1:] if a.startswith("--out=")), OUT)
    rng = np.random.default_rng(31337)
    total = LENGTH + 6.0
    t = t_(total)
    beat = 1.0

    drone = np.zeros(len(t))
    texture = np.zeros(len(t))
    music = np.zeros(len(t))
    pulse = np.zeros(len(t))

    # The wind and the drone under everything (the drone thins to its root after the silence)
    wind = sweep(brown(rng, total), lambda s, f: lp_resp(120 + 380 * (0.5 + 0.5 * np.sin(2 * np.pi * s / 23.0)) ** 2, 3)(f))
    texture += norm(wind) * 0.35 * bell_env(t, 0.0, 10.0, LENGTH - 22.0, 10.0)
    root = norm(lowpass(harm(np.full(len(t), N["D1"]) * (1 + 0.002 * np.sin(phase(np.full(len(t), 0.11)))), top=600, tilt=1.4), 300))
    fifth = norm(lowpass(harm(np.full(len(t), N["A1"]), top=600, tilt=1.4), 260))
    drone += root * 0.55 * bell_env(t, 0.0, 12.0, LENGTH - 20.0, 8.0)
    drone += fifth * 0.3 * bell_env(t, 8.0, 8.0, 92.0, 6.0)

    # Intro: the deep groans
    add(texture, 2.0, norm(deep_groan(np.random.default_rng(9101), 1)), 0.55)

    # Chords, eight beats each, from 16 s to the silence (104 s)
    progression = ["Dm", "Bb", "Gm", "A"] * 2 + ["Gm", "A", "A"]
    s = 16.0
    for ch in progression:
        add(music, s, pad(ch, 8.0 * beat + 2.0), 0.42)
        s += 8.0 * beat

    # The motif on glass, answered lower; again with the heartbeat under it
    play(music, 24.0, MOTIF, beat, lambda f, d: glass(f, 4.0, 0.5))
    play(music, 32.0, ANSWER, beat, lambda f, d: glass(f, 4.0, 0.42))
    play(music, 40.0, MOTIF, beat, lambda f, d: glass(f, 4.0, 0.5))
    for k in range(int(32.0), int(104.0), 2):
        g = 0.5 if k < 48 else 0.7
        add(pulse, k, heartbeat(rng, t_(1.2), 0.0, 38, 1.2), g * (0.8 + 0.2 * rng.random()))

    # Low bells toll on the chord changes; the undertow pulls; giggling far off; steps below
    for at, note in ((48.0, "D3"), (56.0, "Bb2"), (64.0, "G2"), (72.0, "A2")):
        add(music, at, low_bell(rng, N[note]), 0.5)
    add(texture, 46.0, norm(undertow(np.random.default_rng(9161), 1)), 0.5)
    add(texture, 60.0, norm(undertow(np.random.default_rng(9701), 1)), 0.45)
    add(texture, 58.0, norm(hollow_far(np.random.default_rng(9113), 2)), 0.32)
    add(texture, 70.0, norm(far_steps(np.random.default_rng(9181), 1)), 0.5)
    play(music, 64.0, MOTIF, beat, lambda f, d: glass(f / 2, 5.0, 0.42))

    # The storm: the sky tears, basalt cracks, something calls from beneath, and the dread swells into silence
    add(texture, 82.0, norm(storm_tear(np.random.default_rng(9121), 1)), 0.55)
    add(texture, 88.0, norm(basalt_deep(np.random.default_rng(9152), 2)), 0.5)
    add(texture, 94.0, norm(abyss_call(np.random.default_rng(9211), 1)), 0.5)
    play(music, 88.0, ANSWER, beat, lambda f, d: glass(f, 4.0, 0.38))
    # (everything falls away into the silence; the wind and the drone come back after it, the rest does not)
    fall = np.clip((t - 104.0) / 2.5, 0, 1)
    back = np.clip((t - 110.0) / 8.0, 0, 1)
    music *= 1.0 - fall
    pulse *= 1.0 - fall
    drone *= np.where(t < 110.0, 1.0 - fall, back)
    texture *= np.where(t < 110.0, 1.0 - fall, back)
    add(texture, 96.0, norm(dread_rise(np.random.default_rng(9221), 1)), 0.9)

    # After it: one heartbeat, and the motif alone, high and thin, coming to rest on D
    add(pulse, 110.0, heartbeat(rng, t_(1.2), 0.0, 36, 1.2), 0.9)
    add(drone, 109.5, norm(lowpass(harm(np.full(int(42 * SR), N["D2"]), top=500, tilt=1.4), 240)) * bell_env(t_(42.0), 0.0, 6.0, 26.0, 10.0), 0.35)
    play(music, 112.0, MOTIF, 1.25, lambda f, d: glass(f * 2, 5.0, 0.34))
    play(music, 122.0, [("A4", 2), ("F4", 1), ("E4", 1), ("D4", 4)], 1.25, lambda f, d: glass(f, 6.0, 0.32))
    add(music, 132.0, pad("Dm", 14.0), 0.3)
    add(music, 134.0, glass(N["D5"], 8.0, 0.22))
    add(texture, 138.0, norm(hollow_far(np.random.default_rng(9111), 1)), 0.18)

    wet = reverb(rng, music, tail=5.0, mix=0.45, bright=4200) + reverb(rng, pulse, tail=3.0, mix=0.3, bright=1500)
    x = drone * 0.9 + texture + wet
    x *= bell_env(t, 0.0, 1.5, LENGTH + 1.0, 5.0)
    x = finish(x, rms_db=-19.0)
    with tempfile.TemporaryDirectory() as tmp:
        wav = os.path.join(tmp, "disc.wav")
        with wave.open(wav, "wb") as w:
            w.setnchannels(1)
            w.setsampwidth(2)
            w.setframerate(SR)
            w.writeframes((np.clip(x, -1, 1) * 32767).astype(np.int16).tobytes())
        os.makedirs(os.path.dirname(os.path.abspath(out)), exist_ok=True)
        subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", wav, "-c:a", "libvorbis", "-q:a", "4", out], check=True)
        if mp3:
            subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", wav, "-c:a", "libmp3lame", "-q:a", "3", out[:-4] + ".mp3"], check=True)
    print(f"{out}: {len(x) / SR:.1f} s")


if __name__ == "__main__":
    main()
