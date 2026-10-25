"""
BattleHub's own Hunger Games music (1.47), composed here: every piece is written once as a score and then

  * saved as JSON (src/main/resources/battlehub/music/<id>.json), which the server plays with vanilla note block
    sounds for players without the resource pack (core/music/Score, core/music/NoteBlocks), and
  * synthesised to .ogg (same folder) for the resource pack: strings, brass, choir, bass, bells, harp, timpani,
    drums and a gong, with hall reverb.

All music is original. Run from the battlehub folder:  python3 tools/music/compose.py
Needs numpy and ffmpeg (with libvorbis).
"""

import json
import math
import os
import subprocess
import sys
import tempfile

import numpy as np

OUT = os.path.join(os.path.dirname(__file__), "..", "..", "src", "main", "resources", "battlehub", "music")
RATE = 44100

NAMES = {"C": 0, "D": 2, "E": 4, "F": 5, "G": 7, "A": 9, "B": 11}


def n(name):
    """'D4', 'F#3', 'Bb2' -> MIDI number (C4 = 60)."""
    base = NAMES[name[0]]
    i = 1
    while i < len(name) and name[i] in "#b":
        base += 1 if name[i] == "#" else -1
        i += 1
    return base + 12 * (int(name[i:]) + 1)


class Part:
    def __init__(self, name, family, volume=1.0, pan=0.0):
        self.name, self.family, self.volume, self.pan = name, family, volume, pan
        self.notes = []

    def add(self, beat, pitch, beats, vel=0.8):
        if isinstance(pitch, str):
            pitch = n(pitch)
        self.notes.append((float(beat), int(pitch), float(beats), float(vel)))
        return self

    def chord(self, beat, pitches, beats, vel=0.7):
        for p in pitches:
            self.add(beat, p, beats, vel)
        return self

    def line(self, beat, items, vel=0.8):
        """A melody: (pitch or None for a rest, beats) one after the other. Returns the beat after it."""
        for pitch, beats in items:
            if pitch is not None:
                self.add(beat, pitch, beats * 0.95, vel)
            beat += beats
        return beat


class Song:
    def __init__(self, sid, bpm, beats):
        assert 1200 % bpm == 0, "a beat must be a whole number of ticks"
        self.id, self.bpm, self.beats = sid, bpm, beats
        self.parts = []

    def part(self, *a, **k):
        p = Part(*a, **k)
        self.parts.append(p)
        return p

    def seconds(self):
        return self.beats * 60.0 / self.bpm

    def to_json(self):
        return {
            "id": self.id, "bpm": self.bpm, "beats": self.beats,
            "parts": [{"name": p.name, "family": p.family, "volume": round(p.volume, 3),
                       "notes": [[round(b, 4), pi, round(d, 4), round(v, 3)] for b, pi, d, v in sorted(p.notes)]}
                      for p in self.parts],
        }


def triad(root, kind="m"):
    r = n(root) if isinstance(root, str) else root
    third = 3 if kind == "m" else 4
    return [r, r + third, r + 7]


# ---------------------------------------------------------------------------------------------- the pieces

def opening():
    """The tributes rise on their plates: a drone, a heartbeat that quickens, a rising ostinato and the Capitol's
    motif, up to the moment of the gong (which is its own piece, at the start)."""
    s = Song("hg_opening", 75, 24)
    drone = s.part("drone", "strings", 0.55, -0.2)
    for b in range(0, 24, 4):
        v = 0.3 + 0.08 * (b // 4)
        drone.chord(b, [n("D2"), n("A2")], 4, v)
    timp = s.part("timpani", "timpani", 0.9, 0.0)
    beats = [0, 2, 4, 6, 8, 9, 10, 11, 12, 13, 14, 15, 16, 16.5, 17, 17.5, 18, 18.5, 19, 19.5]
    for b in beats:
        timp.add(b, "D2", 1, 0.45 + b / 40)
    for i in range(16):
        timp.add(20 + i * 0.25, "D2" if i % 2 == 0 else "A1", 0.25, 0.55 + i * 0.025)
    osti = s.part("ostinato", "strings", 0.6, 0.25)
    chords = [("D3", "m"), ("Bb2", "M"), ("G2", "m"), ("A2", "M"), ("A2", "M")]
    for bar, (root, kind) in enumerate(chords):
        t = triad(root, kind)
        pat = [t[0], t[1], t[2], t[1]] * 2
        for i, p in enumerate(pat):
            osti.add(4 + bar * 4 + i * 0.5, p + (12 if bar == 4 and i >= 4 else 0), 0.45, 0.45 + bar * 0.08)
    horn = s.part("horns", "brass", 0.75, -0.1)
    horn.line(8, [("D4", 2), ("A4", 1), ("Bb4", 1), ("A4", 3), ("G4", 1), ("F4", 2), ("E4", 1), ("F4", 1), ("E4", 4)], 0.7)
    horn.add(20, "E3", 4, 0.75).add(20, "A3", 4, 0.7)
    choir = s.part("choir", "choir", 0.5, 0.15)
    choir.chord(16, [n("A3"), n("C#4"), n("E4")], 4, 0.45)
    choir.chord(20, [n("A3"), n("C#4"), n("E4"), n("A4")], 4, 0.65)
    bells = s.part("bells", "bells", 0.35, 0.3)
    bells.add(0, "D6", 2, 0.4).add(8, "A5", 2, 0.35).add(16, "D6", 2, 0.4)
    for i in range(8):
        bells.add(20 + i * 0.5, "A5" if i % 2 == 0 else "E6", 0.4, 0.3 + i * 0.03)
    snare = s.part("snare", "snare", 0.45, 0.0)
    for i in range(16):
        snare.add(20 + i * 0.25, 0, 0.2, 0.25 + i * 0.04)
    return s


def gong():
    """Zero: the gong, a low brass blow and the strings falling away."""
    s = Song("hg_gong", 60, 5)
    s.part("gong", "gong", 1.0, 0.0).add(0, "D2", 5, 1.0)
    s.part("timpani", "timpani", 0.9).add(0, "D2", 2, 1.0)
    s.part("brass", "brass", 0.8, -0.1).chord(0, [n("D2"), n("A2"), n("D3"), n("F3")], 2.5, 0.9)
    s.part("strings", "strings", 0.5, 0.2).chord(0, [n("D3"), n("A3"), n("D4")], 4, 0.6)
    return s


def bloodbath():
    """The rush to the cornucopia: driving drums, a bass ostinato, brass blows."""
    s = Song("hg_bloodbath", 150, 40)
    kick = s.part("kick", "kick", 0.6)
    snare = s.part("snare", "snare", 0.6)
    hat = s.part("hat", "hat", 0.35)
    timp = s.part("toms", "timpani", 0.7, 0.1)
    bass = s.part("bass", "bass", 0.55, -0.15)
    low = s.part("strings", "strings", 0.75, 0.2)
    brass = s.part("brass", "brass", 0.9, -0.05)
    osti = ["D2", "D2", "F2", "D2", "E2", "D2", "C2", "D2"]
    shift = [0, 0, -4, -2, 0, 0, -7, -5, 0, -5]
    stabs = [("D3", "m"), ("D3", "m"), ("Bb2", "M"), ("C3", "M"), ("D3", "m"), ("D3", "m"), ("G2", "m"), ("A2", "M"), ("D3", "m"), ("A2", "M")]
    for bar in range(10):
        b0 = bar * 4
        for k in (0, 2, 3.5):
            kick.add(b0 + k, 0, 0.5, 0.85)
        for k in (1, 3):
            snare.add(b0 + k, 0, 0.3, 0.7)
        for i in range(8):
            hat.add(b0 + i * 0.5, 0, 0.2, 0.35 if i % 2 else 0.5)
            p = n(osti[i]) + shift[bar]
            bass.add(b0 + i * 0.5, p, 0.45, 0.75)
            if bar >= 2:
                low.add(b0 + i * 0.5, p + 12, 0.45, 0.5)
        if bar % 2 == 1:
            for i in range(4):
                timp.add(b0 + 2 + i * 0.5, "D2" if i % 2 == 0 else "A1", 0.5, 0.6)
        root, kind = stabs[bar]
        brass.chord(b0, triad(root, kind), 1.2, 0.75)
        if bar >= 4:
            brass.chord(b0 + 2.5, triad(root, kind), 0.5, 0.6)
    for i in range(8):
        snare.add(38 + i * 0.25, 0, 0.2, 0.5 + i * 0.05)
    return s


def alarm():
    """The Gamemakers act: blows a minor second apart, a siren of strings, a timpani roll and a hit."""
    s = Song("hg_alarm", 120, 8)
    brass = s.part("brass", "brass", 0.85, -0.1)
    for b, top in ((0, "Eb4"), (1.5, "Eb4"), (3, "F4")):
        brass.chord(b, [n("A3"), n("D4"), n(top)], 0.8, 0.85)
    brass.chord(7, [n("D3"), n("A3"), n("D4"), n("Eb4")], 1, 1.0)
    siren = s.part("siren", "strings", 0.6, 0.25)
    for i in range(13):
        siren.add(i * 0.25, n("D4") + i, 0.3, 0.5 + i * 0.03)
    for i in range(12):
        siren.add(3.5 + i * 0.25, n("D5") - i, 0.3, 0.8 - i * 0.03)
    timp = s.part("timpani", "timpani", 0.9)
    for i in range(12):
        timp.add(4 + i * 0.25, "D2", 0.25, 0.4 + i * 0.05)
    timp.add(7, "D2", 1, 1.0)
    s.part("gong", "gong", 0.6).add(7, "D2", 1, 0.8)
    return s


def feast():
    """The Feast: a bright fanfare in D major."""
    s = Song("hg_feast", 100, 10)
    tr = s.part("trumpets", "brass", 0.85, -0.1)
    tr.line(0, [("D4", 0.5), ("F#4", 0.5), ("A4", 0.5), ("D5", 1.5), ("A4", 0.5), ("B4", 0.5), ("C#5", 0.5), ("D5", 3)], 0.85)
    tr.chord(8, [n("D4"), n("F#4"), n("A4"), n("D5")], 2, 0.9)
    s.part("strings", "strings", 0.5, 0.2).chord(0, [n("D3"), n("A3"), n("F#4")], 8, 0.5).chord(8, [n("D3"), n("A3"), n("D4")], 2, 0.6)
    snare = s.part("snare", "snare", 0.5)
    for i in range(16):
        snare.add(4 + i * 0.25, 0, 0.2, 0.3 + i * 0.03)
    timp = s.part("timpani", "timpani", 0.8)
    timp.add(0, "D2", 1, 0.8).add(2, "A1", 1, 0.7).add(8, "D2", 2, 1.0)
    bells = s.part("bells", "bells", 0.4, 0.3)
    for i, p in enumerate(["D6", "F#6", "A6", "D7"]):
        bells.add(8 + i * 0.25, p, 1, 0.5)
    return s


def anthem():
    """The anthem of the night: a hymn in D minor for brass and choir over strings, eleven bars at 60 a minute."""
    s = Song("hg_anthem", 60, 44)
    pad = s.part("strings", "strings", 0.55, 0.2)
    bass = s.part("bass", "bass", 0.6, -0.2)
    choir = s.part("choir", "choir", 0.55, 0.1)
    brass = s.part("melody", "brass", 0.8, -0.05)
    timp = s.part("timpani", "timpani", 0.7)
    bells = s.part("bells", "bells", 0.35, 0.3)
    harmony = [("D3", "m"), ("D3", "m"), ("D3", "m"), ("Bb2", "M"), ("F3", "M"), ("A2", "M"),
               ("G2", "m"), ("D3", "m"), ("Bb2", "M"), ("A2", "M"), ("D3", "m")]
    for bar, (root, kind) in enumerate(harmony):
        t = triad(root, kind)
        b0 = bar * 4
        vel = 0.35 if bar < 2 else 0.5 if bar < 6 else 0.6
        pad.chord(b0, [p + 12 for p in t], 4, vel)
        bass.add(b0, t[0] - 12, 4, vel + 0.1)
        if bar >= 2:
            choir.chord(b0, [t[1] + 12, t[2] + 12], 4, vel)
        timp.add(b0, t[0] - 12 if t[0] - 12 >= n("A1") else t[0], 1, 0.4 + 0.04 * bar)
    brass.line(8, [("A4", 2), ("A4", 1), ("G4", 1), ("F4", 2), ("D4", 1), ("E4", 1), ("F4", 1), ("G4", 1), ("A4", 1), ("C5", 1),
                   ("A4", 3), (None, 1)], 0.7)
    brass.line(24, [("Bb4", 2), ("A4", 1), ("G4", 1), ("F4", 2), ("A4", 2), ("D5", 2), ("C5", 1), ("Bb4", 1), ("A4", 2), ("G4", 1),
                    ("E4", 1), ("D4", 4)], 0.8)
    for b in (0, 8, 24, 40):
        bells.add(b, "D6", 2, 0.45)
    for i in range(8):
        timp.add(39 + i * 0.125, "A1", 0.125, 0.3 + i * 0.05)
    return s


def victory():
    """The last tribute standing: a triumphant fanfare in D major."""
    s = Song("hg_victory", 100, 20)
    tr = s.part("brass", "brass", 0.85, -0.1)
    tr.line(0, [("D4", 0.5), ("D4", 0.5), ("D4", 0.5), ("F#4", 1.5), ("A4", 2), ("F#4", 1), ("A4", 1), ("D5", 4),
                ("C#5", 1), ("B4", 1), ("A4", 1), ("B4", 1), ("A4", 2), ("F#4", 2)], 0.85)
    tr.chord(16, [n("D4"), n("F#4"), n("A4"), n("D5")], 4, 0.95)
    strings = s.part("strings", "strings", 0.5, 0.2)
    prog = [("D3", "M"), ("D3", "M"), ("G2", "M"), ("A2", "M"), ("D3", "M")]
    for bar, (root, kind) in enumerate(prog):
        t = triad(root, kind)
        for i in range(8):
            strings.add(bar * 4 + i * 0.5, t[i % 3] + 12, 0.45, 0.45)
    s.part("bass", "bass", 0.6, -0.2).line(0, [("D2", 4), ("D2", 4), ("G2", 4), ("A2", 4), ("D2", 4)], 0.7)
    timp = s.part("timpani", "timpani", 0.8)
    for b in (0, 4, 8, 12):
        timp.add(b, "D2", 1, 0.7)
    for i in range(8):
        timp.add(14 + i * 0.25, "A1" if i % 2 else "D2", 0.25, 0.5 + i * 0.05)
    timp.add(16, "D2", 4, 1.0)
    bells = s.part("bells", "bells", 0.4, 0.3)
    for i, p in enumerate(["D6", "F#6", "A6", "D7", "A6", "F#6", "D6"]):
        bells.add(16 + i * 0.25, p, 1, 0.5)
    s.part("gong", "gong", 0.5).add(16, "D2", 4, 0.6)
    return s


SONGS = [opening, gong, bloodbath, alarm, feast, anthem, victory]

# ---------------------------------------------------------------------------------------------- synthesis


def freq(m):
    return 440.0 * 2 ** ((m - 69) / 12.0)


def env(nsamp, attack, release, hold_frac=1.0):
    t = np.arange(nsamp) / RATE
    a = np.clip(t / max(attack, 1e-4), 0, 1)
    dur = nsamp / RATE
    r = np.clip((dur - t) / max(release, 1e-4), 0, 1)
    return a * r


def additive(f, dur, harmonics, attack, release, vibrato=0.0, detune=(0.0,), bright=None, decay=None):
    """Sum of harmonics (amplitudes given), band-limited below 12 kHz, for each detuned voice."""
    total = int((dur + release) * RATE)
    t = np.arange(total) / RATE
    out = np.zeros(total)
    vib = 1 + vibrato * np.sin(2 * math.pi * 5.2 * t) * np.clip(t / 0.4, 0, 1)
    for cents in detune:
        f0 = f * 2 ** (cents / 1200.0)
        phase = 2 * math.pi * np.cumsum(f0 * vib) / RATE
        for k, amp in enumerate(harmonics, start=1):
            if f0 * k > 12000 or amp == 0:
                break
            a = amp
            part = np.sin(k * phase + 0.37 * k * cents)
            if bright is not None:
                part *= np.clip(1 - (k - 1) * bright(t), 0, 1)
            if decay is not None:
                part *= np.exp(-t * decay * (0.6 + 0.4 * k))
            out += a * part
    out /= max(1, len(detune))
    e = np.ones(total)
    e *= np.clip(t / max(attack, 1e-4), 0, 1)
    rel_start = dur
    e *= np.where(t > rel_start, np.clip(1 - (t - rel_start) / max(release, 1e-4), 0, 1), 1)
    return out * e


def saw(count, roll=1.0):
    return [1.0 / k ** roll for k in range(1, count + 1)]


def vowel(f0, count=30):
    """Harmonic amplitudes shaped by the formants of an 'ah'."""
    amps = []
    for k in range(1, count + 1):
        fk = f0 * k
        a = 0
        for fc, bw, g in ((700, 110, 1.0), (1220, 120, 0.5), (2600, 160, 0.25)):
            a += g / (1 + ((fk - fc) / bw) ** 2)
        amps.append(a / k ** 0.3)
    return amps


def noise(nsamp, seed):
    return np.random.default_rng(seed).standard_normal(nsamp)


def band(sig, lo, hi):
    spec = np.fft.rfft(sig)
    fr = np.fft.rfftfreq(len(sig), 1 / RATE)
    spec[(fr < lo) | (fr > hi)] = 0
    return np.fft.irfft(spec, len(sig))


def voice(family, m, dur, vel, seed):
    f = freq(m)
    if family == "strings":
        return 0.5 * additive(f, dur, saw(24, 1.45), 0.25, 0.6, vibrato=0.004, detune=(-9, -3, 4, 10))
    if family == "brass":
        return 0.55 * additive(f, dur, saw(18, 1.0), 0.05, 0.25, vibrato=0.003, detune=(-4, 4),
                               bright=lambda t: np.clip(0.12 - 0.08 * np.clip(t / 0.3, 0, 1), 0.02, 0.2))
    if family == "choir":
        return 0.5 * additive(f, dur, vowel(f), 0.4, 0.8, vibrato=0.006, detune=(-12, -4, 5, 13))
    if family == "bass":
        return 0.7 * additive(f, dur, [0.7, 0.6, 0.4, 0.25, 0.12, 0.06], 0.02, 0.2)
    if family == "harp":
        return 0.6 * additive(f, max(dur, 1.2), [1, 0.5, 0.33, 0.2, 0.12, 0.08], 0.003, 0.5, decay=1.6)
    if family == "bells":
        total = int((dur + 2.0) * RATE)
        t = np.arange(total) / RATE
        out = np.zeros(total)
        for ratio, amp, dec in ((1, 1, 1.2), (2.76, 0.5, 2.0), (5.4, 0.25, 3.5), (8.93, 0.12, 5.0), (0.5, 0.2, 0.9)):
            out += amp * np.sin(2 * math.pi * f * ratio * t) * np.exp(-t * dec)
        return 0.35 * out * np.clip(t / 0.002, 0, 1)
    if family == "timpani":
        total = int(2.0 * RATE)
        t = np.arange(total) / RATE
        f0 = f * (1 + 0.04 * np.exp(-t * 20))
        ph = 2 * math.pi * np.cumsum(f0) / RATE
        out = np.sin(ph) * np.exp(-t * 2.2) + 0.4 * np.sin(1.5 * ph) * np.exp(-t * 3.5) + 0.2 * np.sin(1.99 * ph) * np.exp(-t * 4)
        out += 0.3 * band(noise(total, seed), 60, 900) * np.exp(-t * 25)
        return 0.9 * out
    if family == "kick":
        total = int(0.5 * RATE)
        t = np.arange(total) / RATE
        f0 = 45 + 80 * np.exp(-t * 30)
        return 1.0 * np.sin(2 * math.pi * np.cumsum(f0) / RATE) * np.exp(-t * 7)
    if family == "snare":
        total = int(0.3 * RATE)
        t = np.arange(total) / RATE
        out = 0.6 * band(noise(total, seed), 1500, 9000) * np.exp(-t * 14) + 0.5 * np.sin(2 * math.pi * 185 * t) * np.exp(-t * 25)
        return 0.6 * out
    if family == "hat":
        total = int(0.08 * RATE)
        t = np.arange(total) / RATE
        return 0.35 * band(noise(total, seed), 7000, 15000) * np.exp(-t * 60)
    if family == "gong":
        total = int(7.0 * RATE)
        t = np.arange(total) / RATE
        out = np.zeros(total)
        for i, (ratio, amp) in enumerate(((1, 1), (1.48, 0.7), (2.1, 0.6), (2.69, 0.5), (3.25, 0.4), (4.1, 0.35), (5.3, 0.25), (6.7, 0.18))):
            swell = 1 - np.exp(-t * (8 - i * 0.6))
            out += amp * np.sin(2 * math.pi * f * ratio * t + i) * swell * np.exp(-t * (0.45 + i * 0.12))
        out += 0.25 * band(noise(total, seed), 40, 3000) * np.exp(-t * 3)
        return 0.6 * out
    raise ValueError(family)


def reverb_ir(seconds=2.8, seed=7):
    nsamp = int(seconds * RATE)
    t = np.arange(nsamp) / RATE
    irs = []
    for ch in range(2):
        nz = noise(nsamp, seed + ch)
        # darker as it decays
        early = band(nz, 80, 9000) * np.exp(-t * 6.9 / seconds * 3)
        late = band(nz, 80, 3500) * np.exp(-t * 6.9 / seconds * 2)
        irs.append((0.4 * early + 0.6 * late) * np.clip(t / 0.01, 0, 1))
    ir = np.stack(irs)
    return ir / np.sqrt((ir ** 2).sum(axis=1, keepdims=True))


def master_eq(stereo):
    """Mastering: a high pass at 45 Hz, the lows below 120 Hz down 6 dB and some presence round 3 kHz, so the music
    carries on small speakers and headphones alike instead of booming."""
    size = stereo.shape[1]
    fr = np.fft.rfftfreq(size, 1 / RATE)
    f = np.maximum(fr, 1.0)
    highpass = (f / 45.0) ** 4 / (1 + (f / 45.0) ** 4)
    shelf = 10 ** (-6 / 20) + (1 - 10 ** (-6 / 20)) * (f / 120.0) ** 2 / (1 + (f / 120.0) ** 2)
    presence = 1 + (10 ** (3 / 20) - 1) * np.exp(-np.log(f / 3000.0) ** 2 / 0.5)
    gain = np.sqrt(highpass) * shelf * presence
    return np.stack([np.fft.irfft(np.fft.rfft(stereo[c]) * gain, size) for c in range(2)])


def render(song):
    beat = 60.0 / song.bpm
    length = song.seconds() + 4.0
    mix = np.zeros((2, int(length * RATE)))
    seed = 1
    for p in song.parts:
        left = math.cos((p.pan + 1) * math.pi / 4)
        right = math.sin((p.pan + 1) * math.pi / 4)
        for b, m, d, v in p.notes:
            seed += 1
            sig = voice(p.family, m, d * beat, v, seed) * v * p.volume
            start = int(b * beat * RATE)
            end = min(mix.shape[1], start + len(sig))
            sig = sig[: end - start]
            mix[0, start:end] += sig * left
            mix[1, start:end] += sig * right
    ir = reverb_ir()
    size = 1 << int(math.ceil(math.log2(mix.shape[1] + ir.shape[1])))
    wet = np.stack([np.fft.irfft(np.fft.rfft(mix[c], size) * np.fft.rfft(ir[c], size), size)[: mix.shape[1]] for c in range(2)])
    out = master_eq(0.8 * mix + 0.35 * wet)
    peak = np.max(np.abs(out)) or 1
    out = np.tanh(out / peak * 1.2) / math.tanh(1.2) * 0.9
    # cut the tail where it has died away
    level = np.max(np.abs(out), axis=0)
    last = np.nonzero(level > 0.002)[0]
    end = min(out.shape[1], (last[-1] + int(0.2 * RATE)) if len(last) else out.shape[1])
    fade = np.ones(end)
    fade[-int(0.2 * RATE):] = np.linspace(1, 0, int(0.2 * RATE))
    return out[:, :end] * fade


def write_ogg(path, stereo):
    pcm = (np.clip(stereo.T, -1, 1) * 32767).astype("<i2").tobytes()
    with tempfile.NamedTemporaryFile(suffix=".raw", delete=False) as f:
        f.write(pcm)
        raw = f.name
    try:
        subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-f", "s16le", "-ar", str(RATE), "-ac", "2", "-i", raw,
                        "-c:a", "libvorbis", "-q:a", "3", path], check=True)
    finally:
        os.unlink(raw)


def main(only=None):
    os.makedirs(OUT, exist_ok=True)
    for make in SONGS:
        song = make()
        if only and song.id not in only:
            continue
        with open(os.path.join(OUT, song.id + ".json"), "w") as f:
            json.dump(song.to_json(), f, separators=(",", ":"))
        audio = render(song)
        write_ogg(os.path.join(OUT, song.id + ".ogg"), audio)
        print(f"{song.id}: {song.seconds():.1f} s score, {audio.shape[1] / RATE:.1f} s audio, "
              f"{sum(len(p.notes) for p in song.parts)} notes, {os.path.getsize(os.path.join(OUT, song.id + '.ogg')) // 1024} KB")


if __name__ == "__main__":
    main(set(sys.argv[1:]) or None)
