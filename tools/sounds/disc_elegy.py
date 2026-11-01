"""Elegy of the Elders: the fourth music disc, pressed from fragments the Elders leave when they fall.

C minor, three minutes, a requiem for the Matriarch, the Ferryman and the Last Weaver. An organ's pedal under the
wind and a funeral bell. A low choir sings the elegy over a slow march of kettle drums. Then each Elder in turn: the
Matriarch in wood and roots (a marimba's low bars, the earth groaning), the Ferryman in his bells and the undertow, the
Last Weaver in glass and the shuttle's quick ticking on harp. At the last all of it together, the choir at full
voice and the drums rolling, and the requiem comes to rest on C major: they are at peace.

Deterministic (fixed seeds). Builds on tools/sounds/ambient.py and score.py. Needs numpy and ffmpeg.
usage: python3 tools/sounds/disc_elegy.py [--out=FILE.ogg] [--mp3]
"""
import os
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from ambient import (SR, bell_env, bell_toll, brown, deep_groan, finish, link, lowpass, lp_resp, norm, reverb,  # noqa: E402
                     sweep, t_, undertow)
from score import add, choir, export, mix, glass, harp, horn, hz, marimba, organ, strings, sub, timpani  # noqa: E402

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..",
                   "src/client/resources/assets/extremedimensions/sounds/music/elegy_of_the_elders.ogg")
LENGTH = 182.0
BEAT = 1.0  # 60 to the minute: a funeral's pace

# The elegy: eight bars of four beats; the choir sings it, then each Elder in their own voice
ELEGY = [("C4", 2), ("Eb4", 1), ("D4", 1), ("C4", 2), ("G3", 2),
         ("Ab3", 2), ("G3", 1), ("F3", 1), ("G3", 4),
         ("C4", 2), ("Eb4", 1), ("F4", 1), ("G4", 2), ("Ab4", 2),
         ("G4", 2), ("F4", 1), ("Eb4", 1), ("D4", 4)]
# The last line, ending on C (its third raised in the final chord)
AMEN = [("Eb4", 2), ("D4", 2), ("C4", 4), ("B3", 2), ("C4", 6)]
CHORDS = {
    "Cm": ["C2", "G2", "C3", "Eb3", "G3"], "Ab": ["Ab1", "Eb2", "Ab2", "C3", "Eb3"], "Fm": ["F1", "C2", "F2", "Ab2", "C3"],
    "G": ["G1", "D2", "G2", "B2", "D3"], "Eb": ["Eb2", "Bb2", "Eb3", "G3", "Bb3"], "Db": ["Db2", "Ab2", "Db3", "F3", "Ab3"],
    "C": ["C2", "G2", "C3", "E3", "G3"], "Gsus": ["G1", "D2", "G2", "C3", "D3"],
}
# One chord a bar under the elegy
UNDER = ["Cm", "Ab", "Fm", "G", "Cm", "Eb", "Fm", "G"]


def melody(master, at, line, voice, beat=BEAT):
    s = at
    for name, beats in line:
        add(master, s, voice(hz(name), beats * beat))
        s += beats * beat
    return s


def choir_chord(rng, master, at, name, d, gain, vowel="oo"):
    for k, n in enumerate(CHORDS[name]):
        add(master, at, choir(rng, hz(n), d + 1.5, vowel=vowel, voices=3, a=1.2, r=2.0), gain * (1.0 if k == 0 else 0.6))


def string_chord(rng, master, at, name, d, gain):
    for k, n in enumerate(CHORDS[name]):
        add(master, at, strings(rng, hz(n), d + 1.5, a=1.0, r=2.0, bright=0.7), gain * (1.0 if k == 0 else 0.55))


def march(rng, master, start, bars, gain, low="C2", high="G2"):
    """The kettle drums' slow march: the first beat of each bar, a softer answer on the third."""
    for b in range(bars):
        s = start + b * 4 * BEAT
        add(master, s, timpani(rng, hz(low), 3.0), gain)
        add(master, s + 2 * BEAT, timpani(rng, hz(high), 2.4), gain * 0.55)


def roots(rng, d):
    """The earth groaning as old roots shift."""
    return norm(deep_groan(rng, 1))[:int(SR * d)]


def main():
    mp3 = "--mp3" in sys.argv
    out = next((a[len("--out="):] for a in sys.argv[1:] if a.startswith("--out=")), OUT)
    rng = np.random.default_rng(5501)
    total = LENGTH + 6.0
    t = t_(total)

    bed = np.zeros(len(t))
    voices = np.zeros(len(t))
    keys = np.zeros(len(t))   # bells, bars, glass, harp
    drums = np.zeros(len(t))

    # The wind and the organ's pedal under everything
    wind = sweep(brown(rng, total), lambda s, f: lp_resp(110 + 300 * (0.5 + 0.5 * np.sin(2 * np.pi * s / 27.0)) ** 2, 3)(f))
    bed += norm(wind) * 0.22 * bell_env(t, 0.0, 8.0, LENGTH - 18.0, 10.0)
    add(bed, 0.0, organ(hz("C2"), 50.0, a=8.0, r=6.0), 0.3)
    add(bed, 0.0, sub(hz("C1"), 50.0, a=8.0, r=6.0), 0.18)

    # Intro: a funeral bell, three times, far off
    for s in (3.0, 9.0, 15.0):
        add(keys, s, lowpass(bell_toll(rng, t_(8.0), 0.0, hz("C3"), 8.0), 2600), 0.32)

    # I. The elegy, on the choir (20 s - 52 s)
    s = 20.0
    for k, ch in enumerate(UNDER):
        choir_chord(rng, voices, s + k * 4 * BEAT, ch, 4 * BEAT, 0.16)
    melody(voices, 20.0, ELEGY, lambda f, d: choir(rng, f, d + 0.6, vowel="oo", voices=4, a=0.35, r=0.7))
    march(rng, drums, 20.0, 8, 0.32)
    add(bed, 36.0, organ(hz("C2"), 18.0, a=4.0, r=6.0), 0.2)

    # II. The Matriarch: roots and wood (52 s - 84 s). The elegy low on the bars, the earth groaning under it
    add(bed, 52.0, roots(np.random.default_rng(5510), 12.0), 0.3)
    for k, ch in enumerate(["Fm", "Cm", "Db", "G", "Fm", "Cm", "Db", "Gsus"]):
        string_chord(rng, voices, 52.0 + k * 4 * BEAT, ch, 4 * BEAT, 0.17)
    melody(keys, 52.0, ELEGY, lambda f, d: mix(marimba(f / 2, 1.8, 0.95), marimba(f, 1.4, 0.4)))
    for b in range(8):  # her steps: the bars, low, on every beat
        for q in range(4):
            add(keys, 52.0 + b * 4 + q, marimba(hz("C2") if q % 2 == 0 else hz("G2"), 1.2, 0.35 if q == 0 else 0.22))
    add(bed, 70.0, roots(np.random.default_rng(5511), 12.0), 0.25)
    march(rng, drums, 52.0, 8, 0.22, low="F1", high="C2")

    # III. The Ferryman: bells and the undertow (84 s - 116 s)
    add(bed, 84.0, norm(undertow(np.random.default_rng(5520), 2)), 0.2)
    add(bed, 100.0, norm(undertow(np.random.default_rng(5521), 2)), 0.18)
    for k, ch in enumerate(UNDER):
        choir_chord(rng, voices, 84.0 + k * 4 * BEAT, ch, 4 * BEAT, 0.11, vowel="oh")
    melody(keys, 84.0, ELEGY, lambda f, d: lowpass(bell_toll(rng, t_(max(d, 1.0) + 4.0), 0.0, f / 2, max(d, 1.0) + 4.0), 3000) * 0.28)
    for k in range(4):  # chains as the boat comes
        for j, dt in enumerate((0.0, 0.15, 0.32)):
            add(keys, 92.0 + k * 6 + dt, link(t_(0.6), 0.0, 2100 + 180 * j, 0.12))
    add(voices, 100.0, horn(rng, hz("C2"), 8.0), 0.3)
    add(voices, 108.0, horn(rng, hz("G1"), 8.0), 0.3)
    march(rng, drums, 84.0, 8, 0.26)

    # IV. The Last Weaver: glass and the shuttle (116 s - 148 s)
    for k, ch in enumerate(["Ab", "Eb", "Fm", "G", "Ab", "Eb", "Fm", "Gsus"]):
        string_chord(rng, voices, 116.0 + k * 4 * BEAT, ch, 4 * BEAT, 0.14)
    melody(keys, 116.0, ELEGY, lambda f, d: glass(f * 2, 4.0, 0.45))
    # the shuttle: harp in quick eighths up and down each chord
    for b, ch in enumerate(["Ab", "Eb", "Fm", "G", "Ab", "Eb", "Fm", "Gsus"]):
        notes = CHORDS[ch][1:] + CHORDS[ch][-2:0:-1]
        for q in range(8):
            add(keys, 116.0 + b * 4 + q * 0.5, harp(hz_up(notes[q % len(notes)]), 2.0, 0.22 if q % 4 == 0 else 0.15))
    for b in range(16):  # and the loom's tick
        add(drums, 116.0 + b * 2, link(t_(0.4), 0.0, 3200, 0.1))
        add(drums, 117.0 + b * 2, link(t_(0.4), 0.0, 2700, 0.08))

    # V. All of it: the choir at full voice, the drums rolling, and the Amen on C major (148 s - end)
    # (the elegy's first half, a little quicker: a bar is three and a half seconds now, its chords with it)
    for k, ch in enumerate(UNDER[:4]):
        choir_chord(rng, voices, 148.0 + k * 3.5, ch, 3.5, 0.28, vowel="ah")
    melody(voices, 148.0, ELEGY[:9], lambda f, d: choir(rng, f, d + 0.6, vowel="ah", voices=5, a=0.25, r=0.6) * 1.25, beat=0.875)
    melody(keys, 148.0, ELEGY[:9], lambda f, d: mix(marimba(f / 2, 1.6, 0.3), glass(f * 2, 3.0, 0.18)), beat=0.875)
    add(bed, 148.0, organ(hz("C2"), 34.0, a=3.0, r=8.0, stops=(1.0, 0.6, 0.4)), 0.4)
    add(drums, 158.0, timpani(rng, hz("G1"), 6.0, roll=5.0), 0.45)
    s = 163.0
    for name, beats in AMEN:
        add(voices, s, choir(rng, hz(name), beats * BEAT * 1.25 + 1.0, vowel="ah", voices=5, a=0.4, r=1.2), 1.15)
        s += beats * BEAT * 1.25
    for at, ch, d in ((163.0, "Ab", 2.5), (165.5, "G", 2.5), (168.0, "Fm", 2.5), (170.5, "G", 2.5), (173.0, "C", 12.0)):
        choir_chord(rng, voices, at, ch, d, 0.3, vowel="ah")
        string_chord(rng, voices, at, ch, d, 0.16)
    add(drums, 173.0, timpani(rng, hz("C2"), 6.0), 0.55)
    add(keys, 173.0, lowpass(bell_toll(rng, t_(10.0), 0.0, hz("C3"), 10.0), 3000), 0.35)
    add(keys, 175.0, glass(hz("E5"), 8.0, 0.2))

    wet = reverb(rng, voices + keys, tail=6.0, mix=0.5, bright=4500) + reverb(rng, drums, tail=4.0, mix=0.45, bright=1800)
    x = bed + wet
    x *= bell_env(t, 0.0, 1.5, LENGTH + 1.0, 5.0)
    x = finish(x, rms_db=-19.0)
    export(x, out, mp3)
    print(f"{out}: {len(x) / SR:.1f} s")


def hz_up(name):
    """A chord note an octave higher, for the harp."""
    return hz(name) * 2


if __name__ == "__main__":
    main()
