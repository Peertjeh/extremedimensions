"""The Starved One's own sounds: the hunger that came through the first wound, made from noise and tones.

It is no Weaver but the hunger itself: a starved, hooded thing some six blocks tall that hangs in the air over the Heart
of the Seam, with no face but a maw of embers, its ribs open on an empty belly of fire, a crown of broken iron on its
brow and the snapped chains of a binding that could not hold it; it drinks time itself. So its palette is a maw (not
one throat but many, the souls that stayed too long where they were not anchored, droning and growling together a
little apart, an octave and a tritone among them, rung through the hollow of an empty belly and breathed through
embers); fire (the wet crackle of embers, a guttering roar); a starving breath, wheezing and rattling; iron (the
crown, cracked and buzzing, and its chains, brighter and harder than the Pale Mason's gold); the souls of the Starved,
a thin chorus far off; and time (a clock's tick, as in the Seam's own Tick of the Boundary, that stumbles and slows as
it is drunk). All in the vast hall of the Heart, in E minor (the key of its theme, tools/sounds/boss_starved_one.py, and
of the Seam's Tick of the Boundary), with a semitone's rub above, F, for the hunger. Nothing is sampled.

Every sound is mono (Minecraft places it in the world) and deterministic (fixed seeds), made at the pitch it is meant
to be played at (1.0, a little either side for variety). Builds on the tools of tools/sounds/knot_mother.py (voice,
choir, booms, braams, reverb, loudness) and tools/sounds/pale_mason.py (stone, chains, the broken halo, quakes). Needs
numpy and ffmpeg (libvorbis). With --preview DIR it also writes a spectrogram of each sound there (needs Pillow).

usage: python3 tools/sounds/starved_one.py [--preview DIR] [starved_one_<name>...]
"""
import os
import re
import subprocess
import sys
import tempfile
import wave

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import knot_mother as km  # noqa: E402
import pale_mason as pm  # noqa: E402
from knot_mother import (SR, band, boom, braam, choir, compress, env, gate, lowpass, mix, noise, norm, phase, reverb,  # noqa: E402
                         riser, saturate, shatter, snap, sweep_filter, swell, t_, thump, voice, whoosh)
from pale_mason import crack, crumble, grind, halo, hollow, lay, leap, passby, quake, tones, wobble  # noqa: E402
from score import shepard  # noqa: E402

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "src/client/resources/assets/extremedimensions/sounds/elder")
PREFIX = "starved_one_"

E = 82.41    # E2, its key: E minor (a semitone's rub above, F, for the hunger)
# the scale's steps over E: F (the rub), F#, G, A, A# (the tritone), B, C, D, D# (the clock's lower step)
F_, FS, G_, A_, AS, B_, C_, D_, DS = 1.0595, 1.1225, 1.1892, 1.3348, 1.4142, 1.4983, 1.5874, 1.7818, 1.8877


# ------------------------------------------------------------------ tools of its own

def follow(x, hz=18.0):
    """The loudness of x as it moves (an envelope follower)."""
    return np.sqrt(np.maximum(lowpass(x ** 2, hz), 0))


# The throats of its maw: (pitch over the note, gain). The same note three times a little apart, the octave, the tritone
# and the fifth among them, the twelfth over all: a crowd in one mouth.
THROATS = [(1.0, 1.0), (1.0, 0.8), (2.0, 0.45), (1.0, 0.7), (AS, 0.32), (B_, 0.3), (2.0, 0.3), (3.0, 0.16)]


def maw(rng, t, f0, vowel="u", vowel2="a", morph=None, throats=6, spread=0.022, rough=0.45, low=0.6, belly=0.45, fire=0.3,
        harmonics=40, fade=None):
    """Its voice: many throats droning one note together a little apart (each drifting on its own, opening its own
    vowel a little early or late), a growl an octave under them all, rung through the hollow of its empty belly and
    breathed out through embers. fade: when each throat falls silent (seconds, one for each), for a hunger that comes
    apart."""
    n = len(t)
    f0 = np.broadcast_to(np.asarray(f0, dtype=float), t.shape)
    k = np.zeros(n) if morph is None else morph
    x = np.zeros(n)
    for i, (r, g) in enumerate(THROATS[:throats]):
        det = 1 + rng.uniform(-spread, spread)
        drift = lowpass(rng.standard_normal(n), 2)
        drift = 1 + 0.006 * drift / (np.std(drift) + 1e-9)
        ki = np.clip(k * rng.uniform(0.8, 1.25), 0, 1)
        y = voice(rng, t, f0 * r * det * drift, vowel, vowel2, ki if vowel2 else None, vib=rng.uniform(3.0, 6.0),
                  vib_depth=0.006, breath=0.08, harmonics=harmonics, rough=rough * rng.uniform(0.6, 1.2))
        y = norm(y) * g
        if fade is not None and i < len(fade):
            y *= np.clip((fade[i] - t) / 0.5, 0, 1)
        x += y
    x = norm(x)
    if low > 0:
        g = voice(rng, t, f0 / 2, "o", None, None, vib=3.0, vib_depth=0.02, breath=0.1, rough=0.9, harmonics=70)
        x += low * norm(saturate(g, 2.5))
    if belly > 0:
        x = norm(x) + belly * norm(hollow(x, E / 2, 0.78))
    if fire > 0:
        fl = lowpass(noise(rng, n), 14)
        fl = 0.5 + 0.5 * np.tanh(1.5 * fl / (np.std(fl) + 1e-9))
        breath_ = band(noise(rng, n), 500, 3800) * follow(x) * fl
        x = norm(x) + fire * norm(breath_)
    return x


def embers(rng, t, at, dur, rate=18.0, wet=0.5, hiss=0.3, die=0.0, grow=0.0):
    """Embers: a wet crackle of sharp pops, the bubbling of something wet in the fire, and short sizzles, `rate` a
    second; thinning out over the length with `die` (to nothing at 1), thickening with `grow` (that many times over)."""
    n = len(t)
    x = np.zeros(n)
    s = at
    while True:
        k = np.clip((s - at) / dur, 0, 1)
        r = rate * (1 - die * k) ** 2 * (1 + grow * k ** 1.5)
        s += rng.exponential(1 / max(r, 0.4))
        if s >= at + dur:
            break
        i = int(s * SR)
        if i >= n:
            break
        kind = rng.random()
        if kind < wet:
            # a wet pop: sap boiling out, a little blip of a tone falling
            f = rng.uniform(300, 1400)
            d = rng.uniform(0.006, 0.025)
            m = int(SR * d * 4)
            tt = np.arange(m) / SR
            y = np.sin(2 * np.pi * f * tt * (1 - 0.35 * np.minimum(tt / d, 1))) * np.exp(-tt / d) + \
                0.5 * rng.standard_normal(m) * np.exp(-tt / 0.0015)
        elif kind < wet + hiss:
            # a sizzle
            d = rng.uniform(0.04, 0.16)
            m = int(SR * d)
            y = band(rng.standard_normal(m), 3500, 11000) * np.sin(np.pi * np.arange(m) / m) ** 2 * 0.4
        else:
            # a sharp pop
            m = int(SR * 0.02)
            y = rng.standard_normal(m) * np.exp(-np.arange(m) / (SR * rng.uniform(0.0006, 0.0025)))
        m = min(len(y), n - i)
        x[i:i + m] += y[:m] * rng.uniform(0.2, 1.0) ** 1.5
    return band(x, 180, 14000)


def fire(rng, t, at, dur, lo=180.0, hi=2600.0, flicker=9.0, peak=0.3, low=0.6):
    """A fire's roar, guttering: a band of noise surging and dropping at an uneven flicker, a deep rumble under it;
    swelling to its height at `peak` of its length and sinking away."""
    n = len(t)
    fl = lowpass(noise(rng, n), flicker)
    fl = 0.55 + 0.45 * np.tanh(1.5 * fl / (np.std(fl) + 1e-9))
    body = band(noise(rng, n), lo, hi) * fl
    rumble = lowpass(noise(rng, n), 130) * (0.7 + 0.3 * fl)
    return (norm(body) + low * norm(rumble)) * swell(t, at, at + dur * peak, at + dur)


def breath(rng, t, at, dur, inhale=True, f=(600.0, 1150.0, 2500.0), wheeze=1400.0, rattle=0.35, glide=0.25):
    """A starving breath: air drawn in (or let go) through a throat too dry for it, its vowel opening as it goes, a thin
    wheeze whistling in it, rattling in the chest."""
    n = len(t)
    k = np.clip((t - at) / dur, 0, 1)
    b = noise(rng, n)
    x = sum(g * band(b, fc * 0.8, fc * 1.25) for fc, g in zip(f, (1.0, 0.6, 0.3)))
    move = 1 + glide * (k if inhale else 1 - k)
    wh = sweep_filter(rng, noise(rng, n), wheeze * move, np.full(n, 45.0))
    x = norm(x) + 0.35 * norm(wh)
    j = lowpass(noise(rng, n), 3)
    r = 0.5 + 0.5 * np.sin(2 * np.pi * phase(26 * (1 + 0.3 * np.tanh(j / (np.std(j) + 1e-9)))))
    x *= 1 - rattle + rattle * r ** 3
    if inhale:
        shape = k ** 1.6 * gate(t, at, dur, 0.01, 0.06)
    else:
        shape = np.clip((t - at) / 0.06, 0, 1) * (1 - k) ** 1.2 * (t >= at) * (t <= at + dur)
    return x * shape


def irons(rng, t, at, spread, count, lo=1900.0, hi=5200.0, life=0.09, bunch=(2, 6), thin=1.0):
    """Its snapped chains of iron: links striking in rattles, ringing brighter and longer than gold."""
    return pm.chains(rng, t, at, spread, count, lo, hi, life, bunch, thin)


def crown(rng, t, at, base=620.0, strength=1.0, decay=0.8, buzz=0.9):
    """Its crown of broken iron struck: the two halves either side of the break ringing a little apart, buzzing where
    they touch."""
    return pm.halo(rng, t, at, base, strength, decay, buzz)


def tick(rng, t, at, f=2637.0, strength=1.0, body=0.6):
    """A clock's tick: the click of the escapement, a small ring of its brass at f, the knock of the case."""
    tt = t - at
    on = (tt >= 0) & (tt < 0.25)
    x = np.zeros(len(t))
    if not on.any():
        return x
    u = tt[on]
    m = int(on.sum())
    click = rng.standard_normal(m) * env(u, 0.0001, 0.0006)
    ring = sum(a * np.sin(2 * np.pi * f * r * u + rng.random() * 6) * env(u, 0.0003, d)
               for r, a, d in [(1, 1.0, 0.03), (2.76, 0.5, 0.012), (5.4, 0.25, 0.006)] if f * r < 16000)
    knock = band(rng.standard_normal(m), 500, 1800) * env(u, 0.0003, 0.008)
    x[on] = strength * (1.4 * click + ring + body * knock)
    return x


def souls(rng, t, notes, start, dur, rise=1.0, vowel="u", vowel2="a", voices=3, attack=1.0, release=0.8, far=4200.0,
          breath_=0.25):
    """The souls of the Starved rising: a thin chorus far off, every voice gliding up (rise: octaves, over dur) and
    opening from one vowel to another."""
    k = np.clip((t - start) / max(dur, 1e-3), 0, 1)
    lift = 2 ** (rise * k ** 1.4)
    fs = [f * lift * (1 + rng.uniform(-0.004, 0.004)) for f in notes]
    return lowpass(choir(rng, t, fs, dur, start, vowel, vowel2, voices, attack, release, 0.012, breath_), far)


def suck(rng, t, at, dur, lo=250.0, hi=5000.0):
    """Air (and time) drawn in: a hiss swelling up out of nothing to a sharp stop, its band climbing, an echo played
    backwards into it."""
    n = len(t)
    k = np.clip((t - at) / dur, 0, 1)
    inside = (t >= at) & (t <= at + dur)
    centre = lo * (hi / lo) ** k
    air = sweep_filter(rng, noise(rng, n), centre, centre * 0.6) * k ** 2.2 * inside
    burst = band(noise(rng, int(SR * 0.05)), 400, 7000) * np.hanning(int(SR * 0.05))
    back = reverb(rng, burst, dur, 0.95, 6000)[::-1][-int(SR * dur):]
    return norm(air) + 0.6 * norm(lay(t, at, back))


def flap(rng, t, rate, lo=200.0, hi=2500.0):
    """Rags and tatters flapping in a rush of air: band noise cut into flutters (rate: flaps a second, an array)."""
    f = 0.5 + 0.5 * np.sign(np.sin(2 * np.pi * phase(rate)))
    f = np.convolve(f, np.ones(64) / 64, "same")
    return band(noise(rng, len(t)), lo, hi) * f


def shout(rng, d, f_from, f_peak, f_to, at=0.2, vowel="a", vowel2="o", throats=7, rough=0.55, low=0.6, tail=0.35):
    """A roar of its maw (d seconds): its throats leaping up and falling away together."""
    tt = t_(d)
    k = np.clip(tt / d, 0, 1)
    f0 = leap(k, f_from, f_peak, f_to, at, 0.6, 1.3)
    shape = np.clip(tt / 0.04, 0, 1) * np.clip((d - tt) / (d * tail), 0, 1)
    return maw(rng, tt, f0, vowel, vowel2, k, throats, 0.02, rough, low, 0.4, 0.45) * shape


def master(rng, x, hall=3.4, wet=0.38, drive=1.6, squeeze=True, bright=6500, presence=0.6, fade=0.12):
    """The hall of the Heart (vast, its stone hung with silk), then a little heat and a compressor, so every sound of it
    lands with weight. As pale_mason.master, but the end of the dry sound faded over `fade` of it (less for a sound
    whose blow lands late)."""
    x = norm(x).copy()
    f = int(len(x) * fade)
    if f > 0:
        x[-f:] *= np.linspace(1, 0, f) ** 1.5
    y = reverb(rng, x, hall, wet, bright)
    y = band(y, 34, 18000, 0.3)
    y = y + presence * band(y, 2200, 12000)
    y = saturate(y, drive)
    if squeeze:
        y = compress(y, 0.35, 3.5, 0.003, 0.15)
    return y


# ------------------------------------------------------------------ its body

def so_voice(seed, pitch, vowel2):
    """Before each blow (its tell): a breath drawn through the embers, then its many throats droning up out of it into a
    hungry growl as the maw opens, the hollow of its belly ringing with it, embers spitting, its crown and its chains
    rattling. Short, so it never hides the blow."""
    rng = np.random.default_rng(seed)
    t = t_(2.3)
    s, d = 0.24, 1.55
    tt = t_(d)
    k = np.clip(tt / d, 0, 1)
    f0 = E * pitch * leap(k, 0.94, 1.06, 0.89, 0.3, 0.7, 1.2)
    v = maw(rng, tt, f0, "u", vowel2, k, 6, 0.024, 0.5, 0.65, 0.5, 0.35) * np.clip(tt / 0.12, 0, 1) * np.clip((d - tt) / (d * 0.45), 0, 1)
    x = mix(0.35 * norm(breath(rng, t, 0.0, s + 0.02, True, wheeze=1700, rattle=0.3)), norm(lay(t, s, v)),
            0.22 * norm(embers(rng, t, s, 1.2, 28, 0.5, 0.25, die=0.6)), 0.12 * norm(crown(rng, t, s, 740 * pitch, 1.0, 0.5)),
            0.12 * norm(irons(rng, t, s, 0.6, 6)), 0.45 * boom(rng, t, s, 75, 36, 0.7))
    return master(rng, x, 2.4, 0.3, 1.7), 2.3


def so_hurt(seed, pitch, clang):
    """Struck: a yelp of its throats torn off short, embers bursting out of its ribs in a puff of fire, its iron crown
    clanging, its chains jumping, the dull knock of its hollow body."""
    rng = np.random.default_rng(seed)
    t = t_(1.8)
    c = shout(rng, 0.62, 150 * pitch, 310 * pitch, 175 * pitch, 0.2, "a", "e", 5, 0.55, 0.5, 0.45)
    knock = hollow(band(noise(rng, len(t)), 60, 900) * env(t, 0.001, 0.03), E, 0.85)
    x = mix(norm(lay(t, 0.0, c)), 0.35 * norm(embers(rng, t, 0.0, 0.5, 90, 0.4, 0.3, die=1.0)),
            0.22 * norm(fire(rng, t, 0.0, 0.5, 300, 3000, 14, 0.15)), 0.3 * norm(crown(rng, t, 0.0, clang, 0.9, 0.45)),
            0.22 * norm(irons(rng, t, 0.01, 0.3, 7)), 0.45 * boom(rng, t, 0.0, 88, 42, 0.5), 0.3 * norm(knock))
    return master(rng, x, 2.0, 0.3), 1.8


def so_ambient(seed, root, breaths, mutter):
    """At rest: the wet crackle of the fire in its belly, a starving breath drawn and let go, its snapped chains swaying,
    the hollow of it droning low, and its throats muttering."""
    rng = np.random.default_rng(seed)
    t = t_(5.2)
    b = sum(breath(rng, t, at, d, inh, wheeze=wz, rattle=0.4) for at, d, inh, wz in breaths)
    drone = hollow(lowpass(noise(rng, len(t)), 700), root, 0.9) * gate(t, 0.0, 5.0, 1.2, 1.6)
    at, d = mutter
    tt = t_(d)
    k = np.clip(tt / d, 0, 1)
    mu = maw(rng, tt, root * leap(k, 1.0, 1.06, 0.9, 0.3), "u", "o", k, 4, 0.03, 0.35, 0.5, 0.5, 0.2) * np.sin(np.pi * k) ** 1.5
    x = mix(norm(embers(rng, t, 0.0, 5.2, 14, 0.55, 0.25)), 0.55 * norm(b), 0.22 * norm(irons(rng, t, 0.4, 4.2, 6, 1500, 4200, 0.1, (1, 3))),
            0.3 * norm(drone), 0.25 * norm(fire(rng, t, 0.0, 5.2, 150, 1500, 4, 0.5, 0.8)), 0.3 * norm(lay(t, at, mu)))
    return master(rng, x, 3.2, 0.42, 1.3, False, 6000, 0.35), 5.2


def so_roar(seed):
    """Its roar: all its throats torn up out of the maw and held, a growl two octaves down, the embers of its maw flaring
    into a roar of fire, the metal of it (its throat rung as a pipe of iron, a tone off the harmonics beating in it), its
    crown crashing, its chains thrashing, a braam under it all and a boom."""
    rng = np.random.default_rng(seed)
    t = t_(4.0)
    d = 2.8
    tt = t_(d)
    k = np.clip(tt / d, 0, 1)
    f0 = leap(k, 70, 190, 120, 0.22, 0.6, 1.4)
    shape = np.clip(tt / 0.05, 0, 1) * np.clip((d - tt) / (d * 0.35), 0, 1)
    c = maw(rng, tt, f0, "a", "o", k, 8, 0.02, 0.55, 0.0, 0.45, 0.4) * shape
    c = c * (0.72 + 0.28 * np.sin(2 * np.pi * phase(f0 * 0.707)))
    metal = hollow(c, E * 4, 0.86)
    g = voice(rng, tt, f0 / 4, "o", None, None, vib=3.0, vib_depth=0.02, breath=0.1, rough=0.95, harmonics=90) * shape
    x = mix(norm(lay(t, 0.05, c)), 0.35 * norm(lay(t, 0.05, metal)), 0.55 * norm(saturate(lay(t, 0.05, g), 2.5)),
            0.4 * norm(fire(rng, t, 0.05, 3.0, 250, 4000, 16, 0.3, 0.5)), 0.2 * norm(embers(rng, t, 0.1, 2.0, 60, 0.4, 0.4, die=0.8)),
            0.3 * norm(crown(rng, t, 0.05, 620, 1.0, 1.0)), 0.25 * norm(irons(rng, t, 0.05, 1.8, 26, thin=1.5)),
            0.5 * norm(braam(rng, t, 0.0, E / 2, 3.2, 0.35, 7)), 0.9 * boom(rng, t, 0.05, 68, 22, 2.0), 0.35 * snap(rng, t, 0.05))
    return master(rng, x, 3.6, 0.4, 1.8), 5.4


def so_death(seed):
    """Its death, the hunger collapsing: a long wail of all its throats falling away, the throats dropping out one by one
    (the souls it was made of leaving it); its chains snapping one after another, its crown cracking and crashing down;
    the fire of its belly roaring up and guttering out, the embers dying to a last few pops; two booms; the souls rising
    away; and at the end, after a silence, one last breath drawn in."""
    rng = np.random.default_rng(seed)
    t = t_(11.2)
    d = 5.4
    tt = t_(d)
    k = np.clip(tt / d, 0, 1)
    f0 = leap(k, 230, 320, 48, 0.12, 0.6, 1.1)
    shape = np.clip(tt / 0.05, 0, 1) * np.clip((d - tt) / (d * 0.3), 0, 1)
    w = maw(rng, tt, f0, "a", "u", k, 8, 0.03, 0.45, 0.6, 0.45, 0.35, fade=[5.4, 1.6, 2.2, 2.7, 3.2, 3.7, 4.2, 4.7]) * shape
    snaps = np.zeros(len(t))
    for i, at in enumerate([0.5, 1.0, 1.6, 2.1, 2.9, 3.6, 4.4]):
        snaps += (0.6 + 0.4 * rng.random()) * (snap(rng, t, at, 0.9) + 0.7 * irons(rng, t, at, 0.18, 6, 1600, 5200, 0.08, (2, 5)))
    rings = crown(rng, t, 2.4, 620, 1.0, 1.4) + 0.9 * crown(rng, t, 3.3, 410, 1.0, 1.8, 1.0)
    gutter = fire(rng, t, 0.0, 6.8, 200, 3200, 10, 0.15)
    gutter *= 1 - 0.6 * np.clip((t - 2.5) / 4.0, 0, 1) * (0.5 + 0.5 * np.sign(np.sin(2 * np.pi * phase(np.full(len(t), 5.0) + 3 * np.sin(t)))))
    freed = souls(rng, t, [E * 2, E * 2 * B_, E * 4], 3.6, 3.6, 1.0, "u", "a", 2, 1.4, 1.2, 4000)
    last = breath(rng, t, 8.3, 1.3, True, (550, 1100, 2400), 1200, 0.5, 0.3)
    shut = crack(rng, t, 9.62, 0.4, 0.6)
    x = mix(norm(lay(t, 0.1, w)), 0.45 * norm(snaps), 0.4 * norm(rings), 0.35 * norm(shatter(rng, t, 3.3, 1.2, 40, 1500, 6000, 0.12)),
            0.45 * norm(gutter), 0.3 * norm(embers(rng, t, 0.2, 8.4, 40, 0.5, 0.25, die=1.0)), 0.25 * norm(freed),
            0.45 * norm(braam(rng, t, 2.6, E / 2, 4.6, 0.3, 3)), 0.9 * boom(rng, t, 0.1, 70, 22, 2.2), 0.9 * boom(rng, t, 3.3, 58, 20, 3.0),
            0.8 * norm(last), 0.12 * norm(shut), 0.06 * norm(embers(rng, t, 9.9, 0.3, 4, 0.0, 0.0)))
    return master(rng, x, 4.2, 0.42, 1.7, True, 6500, 0.6, 0.1), 11.0


# ------------------------------------------------------------------ its rise and its blows

def so_wake(seed):
    """The rise, as it claws up out of the Heart (it plays once, about ten seconds): out of nothing a swell in the lowest
    octave, the stone of the Heart cracking ever more often and grinding, the souls of the Starved rising in a chorus, its
    heart beating ever faster, embers kindling, chains dragged up after it, all drawn up to the moment it tears free:
    a roar, a boom, a burst of fire."""
    rng = np.random.default_rng(seed)
    t = t_(10.6)
    hit = 8.0
    sub = np.sin(2 * np.pi * E / 2 * t) * np.clip(t / 7.5, 0, 1) ** 2 * np.clip((hit + 0.3 - t) / 0.3, 0, 1)
    cracks = np.zeros(len(t))
    for j in range(18):
        at = 0.6 + (hit - 0.8) * (1 - (1 - j / 18) ** 1.8)
        cracks += lowpass(crack(rng, t, at, 0.4 + 0.6 * j / 18, rng.uniform(1.2, 2.4)), 2500 + 300 * j)
    beats = sum(thump(rng, t, at, 44) + 0.7 * thump(rng, t, at + 0.24, 40)
                for at in np.cumsum([0.4] + [1.15 * 0.87 ** i for i in range(20)]) if at < hit - 0.2)
    rise = souls(rng, t, [E * 2, E * 2 * G_, E * 2 * B_, E * 4], 1.2, hit - 1.2, 1.0, "u", "a", 3, 4.0, 0.3, 4500)
    roar = shout(rng, 1.5, 90, 200, 140, 0.15, "a", "o", 7, 0.6)
    build = mix(0.5 * norm(sub), 0.5 * norm(braam(rng, t, 0.3, E / 2, hit - 0.2, 0.97, 6)), 0.4 * norm(cracks),
                0.35 * norm(grind(rng, t, 1.0, hit - 1.0, 50, 1800, 4, 36, 0.7)), 0.25 * norm(crumble(rng, t, 2.0, 6.0, 300, 500, 5000, 0.01, 8, 0.8)),
                0.45 * norm(rise), 0.55 * norm(beats), 0.2 * norm(embers(rng, t, 3.0, hit - 3.0, 10, 0.5, 0.3, grow=4.0)),
                0.2 * norm(irons(rng, t, 4.5, hit - 4.5, 16, 1200, 3600, 0.08, (2, 5), 0.7)), 0.5 * norm(riser(rng, t, 5.2, hit - 5.2, 200, 8000, E / 2)))
    # a breath's hush just before it tears free, so the blow lands out of it
    build *= 1 - 0.7 * np.clip((t - hit + 0.16) / 0.1, 0, 1)
    x = mix(0.8 * build, 0.3 * norm(breath(rng, t, hit - 0.45, 0.45, True, wheeze=1500, rattle=0.4)),
            0.95 * norm(lay(t, hit, roar)), boom(rng, t, hit, 72, 22, 2.2, 1.3), 0.6 * norm(crack(rng, t, hit, 1.0, 2.0)),
            0.3 * norm(crown(rng, t, hit, 620, 1.0, 1.2)), 0.45 * norm(fire(rng, t, hit - 0.05, 1.5, 300, 5000, 18, 0.08)),
            0.35 * snap(rng, t, hit))
    return master(rng, x, 3.6, 0.38, 1.6, True, 6500, 0.6, 0.08), 10.0


def so_swoop(seed, pitch):
    """Its dive through the air: a great rush of air rising as it comes and dropping as it goes by, its rags and
    tatters flapping in it, a hiss of its throats sweeping down, embers streaming after it, its chains whipping."""
    rng = np.random.default_rng(seed)
    t = t_(2.4)
    tc = 0.75
    u = (t - tc) / 0.3
    near = 1 / (1 + u ** 2)
    d = 1.1
    tt = t_(d)
    k = np.clip(tt / d, 0, 1)
    hiss = maw(rng, tt, 520 * pitch * (160 / 520) ** k, "e", "u", k, 4, 0.03, 0.3, 0.0, 0.2, 0.6) * np.sin(np.pi * k) ** 1.2
    x = mix(norm(passby(rng, t, tc, 0.28, 300 * pitch, 1600 * pitch, 0.5)), 0.8 * norm(passby(rng, t, tc, 0.36, 90, 420, 0.6)),
            0.35 * norm(flap(rng, t, 14 + 22 * near) * near ** 1.2), 0.3 * norm(lay(t, tc - 0.4, hiss)),
            0.25 * norm(embers(rng, t, tc - 0.1, 1.2, 50, 0.3, 0.5, die=1.0)), 0.25 * norm(irons(rng, t, tc - 0.15, 0.5, 12)),
            0.3 * norm(snap(rng, t, tc + 0.15, 0.6)), 0.5 * norm(lowpass(noise(rng, len(t)), 110) * near ** 1.5))
    return master(rng, x, 2.4, 0.3, 1.7), 2.4


def so_call(seed):
    """It calls the tribe's red young: rearing up, its maw to the sky, a shriek of all its throats at once in a chord (E,
    the tritone, B, and the rub, F, above), shaking, a growl under it; the embers of its maw flaring; and far off, the
    young answering."""
    rng = np.random.default_rng(seed)
    t = t_(3.6)
    d = 1.9
    tt = t_(d)
    k = np.clip(tt / d, 0, 1)
    shake = 0.65 + 0.35 * np.sin(2 * np.pi * phase(np.full(len(tt), 7.0)) + rng.random() * 6)
    bend = leap(k, 0.85, 1.12, 0.92, 0.18, 0.6, 1.3)
    shriek = np.zeros(len(tt))
    for r, g in [(1.0, 1.0), (AS, 0.7), (B_, 0.75), (2.0, 0.6), (2 * F_, 0.45), (2 * B_, 0.3)]:
        shriek += g * norm(voice(rng, tt, E * 2 * r * bend * (1 + rng.uniform(-0.006, 0.006)), "a", "e", k, vib=6.5, vib_depth=0.025,
                                 breath=0.15, harmonics=36, rough=0.4))
    shriek *= shake * np.clip(tt / 0.05, 0, 1) * np.clip((d - tt) / (d * 0.4), 0, 1)
    growl = maw(rng, tt, E * bend, "o", "a", k, 3, 0.02, 0.6, 0.6, 0.5, 0.3) * np.clip((d - tt) / (d * 0.5), 0, 1)
    young = np.zeros(len(t))
    for at, f in [(2.05, 1318.5), (2.4, 1567.98), (2.7, 1244.5), (2.95, 1480.0)]:
        dd = 0.2
        ty = t_(dd)
        ky = ty / dd
        y = tones(f * leap(ky, 0.9, 1.15, 0.8, 0.3), 5, 1.2) * np.sin(np.pi * ky)
        young += lay(t, at, y, 0.5 + 0.5 * rng.random())
    young = reverb(rng, lowpass(young, 3000), 1.6, 0.7, 3500)[:len(t)]
    x = mix(norm(lay(t, 0.05, shriek)), 0.5 * norm(lay(t, 0.05, growl)), 0.3 * norm(fire(rng, t, 0.05, 1.4, 400, 4000, 16, 0.2)),
            0.2 * norm(embers(rng, t, 0.05, 1.6, 60, 0.4, 0.4, die=0.9)), 0.15 * norm(young), 0.4 * boom(rng, t, 0.05, 80, 40, 0.8),
            0.2 * norm(irons(rng, t, 0.05, 1.2, 14)))
    return master(rng, x, 3.2, 0.4, 1.7), 3.6


def so_ray(seed):
    """It turns the light of the Veil Rays into a hunting ray: a hum gathering (two tones beating, climbing an octave, a
    tremolo ever faster, a crackle of light gathering round it), then the release: a blast, and the ray itself, a
    buzzing beam that rings and sinks a little away, sizzling."""
    rng = np.random.default_rng(seed)
    t = t_(3.6)
    n = len(t)
    rel = 1.6
    k = np.clip(t / rel, 0, 1)
    hm = pm.glow(rng, t, E * 2 * 2 ** (k ** 1.5), 0.2) * (0.6 + 0.4 * np.sin(2 * np.pi * phase(4 + 26 * k ** 2))) * k ** 1.5
    hm *= np.clip((rel + 0.02 - t) / 0.02, 0, 1)
    tb = np.clip((t - rel) / 1.6, 0, 1)
    fb = E * 4 * (1 - 0.06 * tb)
    beam = tones(fb, 12, 0.8) + 0.5 * tones(fb * B_, 8, 0.9)
    beam = beam * (0.6 + 0.4 * np.sin(2 * np.pi * phase(fb * 0.707))) * env(t - rel, 0.005, 0.7)
    sizzle = band(noise(rng, n), 3500, 11000) * env(t - rel, 0.01, 0.6) * (0.5 + 0.5 * np.tanh(lowpass(noise(rng, n), 40) * 20))
    blast = saturate(band(noise(rng, n), 400, 12000) * env(t - rel, 0.001, 0.05), 3.0)
    x = mix(norm(hm), 0.35 * norm(embers(rng, t, 0.2, rel - 0.2, 25, 0.0, 0.35, grow=3.0)), 0.4 * norm(riser(rng, t, 0.0, rel, 400, 9000, E)),
            0.7 * norm(blast), 0.7 * norm(beam), 0.3 * norm(sizzle), 0.5 * norm(whoosh(rng, t, rel, 0.9, 7000, 600, 0.3)),
            0.6 * boom(rng, t, rel, 95, 40, 0.8))
    return master(rng, x, 2.8, 0.34, 1.8, True, 8000, 0.7), 3.6


def so_drink(seed):
    """It drinks time: a long breath drawn in through its maw, and with it the air and the light (a hiss swelling up out
    of nothing, a tone falling for ever), while a clock's tick slows, stumbles, stretches and stops; then a gulp in the
    hollow of it."""
    rng = np.random.default_rng(seed)
    t = t_(4.6)
    n = len(t)
    dur = 3.4
    ticks = np.zeros(n)
    s, iv, i = 0.12, 0.11, 0
    while s < dur + 0.1:
        k = s / dur
        ticks += tick(rng, t, s, (2637.0 if i % 2 == 0 else 2489.0) * (1 - 0.4 * k), 1.0 - 0.3 * k)
        i += 1
        # it stumbles: one tick comes twice, another is lost
        s += iv * (0.5 if i == 7 else 2.0 if i == 10 else 1.0)
        iv *= 1.27
    wet = reverb(rng, ticks, 1.8, 0.9, 5000)[:n]
    kt = np.clip(t / dur, 0, 1)
    ticks = norm(ticks) * (1 - 0.6 * kt) + 0.8 * norm(wet) * kt
    fall = shepard(t, 1.8, 300, 7, 20, True, 1.2) * swell(t, 0.0, 2.8, 3.6)
    gulp = thump(rng, t, dur + 0.05, 55) + 0.6 * np.sin(2 * np.pi * phase(80 + 160 * np.exp(-np.maximum(t - dur - 0.05, 0) / 0.04))) * env(t - dur - 0.05, 0.003, 0.08)
    x = mix(0.8 * norm(suck(rng, t, 0.0, dur, 250, 5200)), 0.5 * norm(breath(rng, t, 0.2, dur - 0.2, True, (500, 1000, 2300), 1100, 0.4)),
            0.25 * norm(fall), 0.45 * ticks, 0.5 * norm(hollow(gulp, E, 0.8)), 0.15 * norm(maw(rng, t, E * 2 ** (-0.3 * kt), "a", "u", kt, 3, 0.02,
                                                                                               0.4, 0.5, 0.6, 0.2) * kt ** 2 * (t < dur)))
    return master(rng, x, 3.0, 0.36, 1.5), 4.6


def so_heal(seed, root):
    """A pillar's heart feeding it: a hum of souls drawn along the beam (a chord hummed through closed lips, swelling
    twice as a heart beats), a deep thump under each swell, a shimmer climbing from the pillar to it."""
    rng = np.random.default_rng(seed)
    t = t_(2.8)
    pulses = (0.05, 1.2)
    pulse = sum(env(t - p, 0.12, 0.5) for p in pulses)
    hm = choir(rng, t, [root, root * B_, root * 2], 2.4, 0.0, "u", "o", 3, 0.3, 0.8, 0.008, 0.2) * (0.35 + 0.65 * pulse)
    beats = sum(thump(rng, t, p, 46) + 0.6 * thump(rng, t, p + 0.22, 42) for p in pulses)
    k = np.clip(t / 2.4, 0, 1)
    shim = sum(np.sin(2 * np.pi * phase(root * r * 2 ** (0.5 * k))) for r in (8, 12, 16)) * swell(t, 0.1, 1.6, 2.6)
    x = mix(norm(hm), 0.5 * norm(beats), 0.12 * norm(shim), 0.3 * norm(pm.glow(rng, t, root, 0.15) * pulse))
    return master(rng, x, 2.8, 0.38, 1.3, False, 6500, 0.4), 2.8


def so_quench(seed, pitch):
    """A pillar's heart quenched: a hiss of steam as its fire goes out, the glassy crack of the heart splitting, a
    tinkle of shards, the hum of the souls in it sinking away, a dull thud."""
    rng = np.random.default_rng(seed)
    t = t_(2.4)
    n = len(t)
    fl = 0.6 + 0.4 * np.tanh(lowpass(noise(rng, n), 25) * 15)
    hiss = band(noise(rng, n), 2500, 11000) * env(t, 0.005, 0.55) * fl + 0.6 * band(noise(rng, n), 800, 3000) * env(t, 0.003, 0.3)
    glass = sum(a * np.sin(2 * np.pi * 1760 * pitch * r * t + rng.random() * 6) * env(t - 0.02, 0.0005, dd)
                for r, a, dd in [(1, 1.0, 0.5), (2.32, 0.5, 0.3), (4.25, 0.3, 0.18), (6.1, 0.15, 0.1)])
    k = np.clip(t / 1.2, 0, 1)
    sink = pm.glow(rng, t, E * 2 * pitch * 2 ** (-k), 0.1) * env(t, 0.01, 0.5)
    x = mix(norm(hiss), 0.8 * norm(crack(rng, t, 0.02, 1.0, 0.8)), 0.45 * norm(shatter(rng, t, 0.02, 0.6, 36, 2600, 6000, 0.09)),
            0.3 * norm(glass), 0.4 * norm(sink), 0.6 * boom(rng, t, 0.02, 90, 45, 0.5))
    return master(rng, x, 2.4, 0.32, 1.7, True, 8000, 0.6), 2.4


def so_bound(seed):
    """All four hearts quenched, it is bound: four chains slam taut one after another (iron clanking in, a crack as each
    jerks taut, a boom), and hold, shivering and creaking under its strain, while it roars in pain, strangled and
    shuddering, and sinks."""
    rng = np.random.default_rng(seed)
    t = t_(5.0)
    slams = np.zeros(len(t))
    for i, s in enumerate([0.0, 0.11, 0.2, 0.32]):
        slams += irons(rng, t, max(0.0, s - 0.1), 0.1, 10, 800, 3200, 0.09, (3, 7)) + 1.2 * snap(rng, t, s, 1.0) + 0.6 * boom(rng, t, s, 85, 30, 1.0)
    shud = 0.5 + 0.5 * np.sin(2 * np.pi * phase(np.full(len(t), 9.0)))
    taut = (np.sin(2 * np.pi * E * 2 * t) + np.sin(2 * np.pi * (E * 2 + 1.3) * t) + 0.4 * np.sin(2 * np.pi * E * 6.02 * t)) * (0.6 + 0.4 * shud)
    taut *= gate(t, 0.33, 3.6, 0.02, 1.4)
    d = 3.0
    tt = t_(d)
    k = np.clip(tt / d, 0, 1)
    pain = shout(rng, d, 200, 280, 70, 0.15, "a", "u", 7, 0.7, 0.5, 0.3) * (0.65 + 0.35 * np.sin(2 * np.pi * phase(np.full(len(tt), 7.0))))
    x = mix(norm(slams), 0.35 * norm(irons(rng, t, 0.35, 2.4, 30, 1600, 4200, 0.04, (1, 3), 2.0)), 0.25 * norm(taut),
            0.25 * norm(km.creak(rng, t, 0.4, 3.6, 6, 18, 300, 1500)), 0.75 * norm(lay(t, 0.25, pain)), 0.3 * norm(crown(rng, t, 0.0, 560, 1.0, 1.0)),
            boom(rng, t, 0.0, 70, 22, 2.2, 1.2), 0.35 * norm(fire(rng, t, 0.3, 2.5, 200, 2000, 6, 0.1)))
    return master(rng, x, 3.2, 0.36, 1.8), 5.0


def so_break_free(seed):
    """It tears loose again: its chains straining (a creak ever faster and tighter, its throats growling up with it),
    then snapping one after another, links flying, a burst of fire out of its ribs, a boom, and its roar let out."""
    rng = np.random.default_rng(seed)
    t = t_(4.2)
    brk = 1.4
    d0 = brk + 0.1
    t0 = t_(d0)
    k0 = np.clip(t0 / brk, 0, 1)
    strain = maw(rng, t0, E * 2 ** (0.6 * k0), "u", "a", k0, 5, 0.02, 0.6, 0.6, 0.4, 0.3) * k0 ** 1.5
    snaps = np.zeros(len(t))
    for s in (0.0, 0.07, 0.15, 0.26):
        snaps += snap(rng, t, brk + s, 1.0) + 0.8 * irons(rng, t, brk + s, 0.6, 8, 1800, 5200, 0.07, (2, 5), 1.6)
    roar = shout(rng, 2.0, 110, 230, 150, 0.2, "a", "o", 7, 0.5)
    x = mix(0.45 * norm(km.creak(rng, t, 0.0, brk, 10, 90, 400, 1800)), 0.6 * norm(lay(t, 0.0, strain)), norm(snaps),
            0.35 * norm(shatter(rng, t, brk, 0.8, 30, 1500, 5000, 0.07)), 0.5 * norm(fire(rng, t, brk - 0.02, 1.6, 300, 4500, 16, 0.08)),
            boom(rng, t, brk, 80, 26, 1.8, 1.2), 0.85 * norm(lay(t, brk + 0.05, roar)), 0.4 * norm(whoosh(rng, t, brk, 0.8, 400, 5000, 0.4)),
            0.2 * norm(embers(rng, t, brk, 2.0, 50, 0.4, 0.4, die=0.9)))
    return master(rng, x, 3.0, 0.34, 1.9), 4.0


def so_phase(seed):
    """It changes phase: a deep impact (a boom, the crack of the Heart's stone, its crown struck like a gong), then a
    shriek of all its throats rising and rising (two octaves), a braam swelling under it, the fire of its belly flaring
    up, its chains thrashing."""
    rng = np.random.default_rng(seed)
    t = t_(6.0)
    d = 3.0
    tt = t_(d)
    k = np.clip(tt / d, 0, 1)
    sh = maw(rng, tt, E * 2 ** (2 * k ** 1.3), "o", "a", k, 7, 0.02, 0.5, 0.5, 0.35, 0.4)
    sh *= np.clip(tt / 0.3, 0, 1) * np.clip((d - tt) / 0.25, 0, 1)
    cluster = sum(a * tones(E * 4 * r * 2 ** (1.0 * k ** 1.3) * wobble(rng, len(tt), 8 + 20 * k, 0.004 + 0.02 * k), 6, 1.2)
                  for r, a in [(1.0, 1.0), (F_, 0.6), (AS, 0.5)]) * k ** 1.5 * np.clip((d - tt) / 0.25, 0, 1)
    x = mix(boom(rng, t, 0.0, 64, 20, 2.8, 1.3), 0.7 * norm(crack(rng, t, 0.0, 1.0, 2.0)), 0.6 * norm(km.gong(rng, t, 0.0, E, 1.0, 3.0)),
            0.35 * norm(crown(rng, t, 0.0, 410, 1.0, 2.0, 0.6)), 0.8 * norm(lay(t, 0.25, sh)), 0.2 * norm(lay(t, 0.25, cluster)),
            0.55 * norm(braam(rng, t, 0.0, E / 2, 4.5, 0.55, 7)), 0.35 * norm(fire(rng, t, 0.2, 4.0, 200, 3500, 10, 0.6)),
            0.25 * norm(irons(rng, t, 0.0, 2.4, 24, thin=1.4)))
    return master(rng, x, 3.8, 0.4, 1.7), 6.4


def so_ultimate(seed):
    """Its strongest blow: everything drawn up into it (a riser; its throats and the souls of the Starved crying up
    together; the fire roaring ever higher; a clock's tick racing), and the release: a huge boom, its roar, a blast of
    fire, its crown crashing, shards."""
    rng = np.random.default_rng(seed)
    t = t_(6.6)
    top = 3.2
    tt = t_(top)
    k = np.clip(tt / top, 0, 1)
    up = maw(rng, tt, E * 2 ** (1.5 * k), "u", "a", k, 6, 0.02, 0.4, 0.5, 0.4, 0.3) * k ** 1.5
    ticks = np.zeros(len(t))
    s, iv, i = 0.0, 0.5, 0
    while s < top - 0.02:
        ticks += tick(rng, t, s, 2637.0 if i % 2 == 0 else 2489.0, 0.4 + 0.6 * s / top)
        s += iv
        iv = max(0.045, iv * 0.86)
        i += 1
    roar = shout(rng, 1.8, 100, 240, 160, 0.18, "a", "o", 8, 0.55)
    x = mix(0.6 * norm(riser(rng, t, 0.0, top, 150, 8000, E / 2)), 0.45 * norm(souls(rng, t, [E * 2, E * 2 * G_, E * 2 * B_, E * 4], 0.3, top - 0.3,
                                                                                         1.0, "u", "a", 3, 2.4, 0.15, 5000)),
            0.5 * norm(lay(t, 0.0, up)), 0.4 * norm(fire(rng, t, 0.0, top + 0.1, 200, 3500, 10, 0.97)), 0.25 * norm(ticks),
            0.55 * norm(braam(rng, t, 0.3, E / 2, top + 2.0, 0.6, 8)), boom(rng, t, top, 72, 22, 2.4, 1.4), 0.8 * norm(lay(t, top - 0.02, roar)),
            0.5 * norm(fire(rng, t, top - 0.02, 1.6, 300, 5000, 18, 0.06)), 0.35 * norm(crown(rng, t, top, 620, 1.0, 1.4) + crown(rng, t, top + 0.01, 430, 0.8, 1.8)),
            0.3 * norm(shatter(rng, t, top, 1.0, 30, 1500, 5000, 0.08)), 0.5 * snap(rng, t, top), 0.4 * norm(km.gong(rng, t, top, E, 1.0, 2.5)))
    return master(rng, x, 3.8, 0.38, 1.8), 7.0


def so_upheaval(seed):
    """The ground erupting: the floor of the Heart splits with a crack that runs away through it, a quake swells and
    shudders, stone grinds up, fire bursts up out of the cracks in gouts one after another, embers raining, and a great
    boom as it all comes down, stone crashing and settling. Tuned to its E, hung with its chains."""
    rng = np.random.default_rng(seed)
    t = t_(6.0)
    n = len(t)
    run = np.zeros(n)
    for i in range(12):
        s = 0.02 + 0.7 * (i / 12) ** 1.3
        run += lowpass(crack(rng, t, s, 1.0 - 0.05 * i, 1.0 + 0.12 * i), 9000 - 500 * i)
    gouts = np.zeros(n)
    for g in (0.6, 1.2, 1.7, 2.3, 2.9):
        gouts += fire(rng, t, g, 0.9, 250, 4500, 20, 0.12) + 0.5 * norm(whoosh(rng, t, g, 0.6, 300, 3000, 0.5)) + 0.5 * boom(rng, t, g, 90, 40, 0.6)
    fall = 3.4
    x = mix(0.8 * norm(crack(rng, t, 0.0, 1.0, 2.0)), boom(rng, t, 0.0, 64, 22, 2.0, 0.9), 0.45 * norm(run), 0.65 * norm(quake(rng, t, 0.0, 5.6, 8)),
            0.5 * norm(grind(rng, t, 0.4, 3.0, 50, 2000, 5, 36, 1.0)), 0.7 * norm(gouts), 0.3 * norm(embers(rng, t, 0.8, 4.6, 40, 0.4, 0.3, die=0.7)),
            0.35 * norm(braam(rng, t, 1.0, E / 2, 3.2, 0.75, 5)), 0.3 * norm(hollow(lowpass(noise(rng, n), 800), E, 0.85) * gate(t, 0.3, 5.0, 1.0, 1.5)),
            0.2 * norm(irons(rng, t, 0.2, 3.6, 20, 1200, 3600, 0.06, (2, 5))), boom(rng, t, fall, 56, 20, 3.2, 1.5),
            0.9 * norm(crack(rng, t, fall, 1.0, 2.2)), 0.7 * norm(crumble(rng, t, fall + 0.02, 2.0, 1400, 300, 5500, 0.014, 30, 1.5)),
            0.6 * norm(lowpass(noise(rng, n), 100) * env(t - fall, 0.1, 1.2)))
    return master(rng, x, 4.0, 0.4, 1.8), 6.2


SOUNDS = {
    "starved_one_voice1": lambda: so_voice(601, 1.0, "a"),
    "starved_one_voice2": lambda: so_voice(602, F_, "o"),
    "starved_one_ambient1": lambda: so_ambient(611, E, [(0.3, 1.5, True, 1500), (1.9, 1.7, False, 1300), (3.8, 1.2, True, 1700)], (2.6, 1.6)),
    "starved_one_ambient2": lambda: so_ambient(612, E * F_, [(0.2, 1.8, False, 1250), (2.2, 1.4, True, 1600), (3.7, 1.4, False, 1400)], (0.9, 1.8)),
    "starved_one_hurt1": lambda: so_hurt(621, 1.0, 620),
    "starved_one_hurt2": lambda: so_hurt(622, 0.9, 540),
    "starved_one_hurt3": lambda: so_hurt(623, 1.12, 700),
    "starved_one_roar": lambda: so_roar(631),
    "starved_one_death": lambda: so_death(641),
    "starved_one_wake": lambda: so_wake(651),
    "starved_one_swoop1": lambda: so_swoop(661, 1.0),
    "starved_one_swoop2": lambda: so_swoop(662, 0.85),
    "starved_one_call": lambda: so_call(671),
    "starved_one_ray": lambda: so_ray(681),
    "starved_one_drink": lambda: so_drink(691),
    "starved_one_heal1": lambda: so_heal(701, E * 2),
    "starved_one_heal2": lambda: so_heal(702, E * B_),
    "starved_one_quench1": lambda: so_quench(711, 1.0),
    "starved_one_quench2": lambda: so_quench(712, 0.89),
    "starved_one_bound": lambda: so_bound(721),
    "starved_one_break_free": lambda: so_break_free(731),
    "starved_one_phase": lambda: so_phase(741),
    "starved_one_ultimate": lambda: so_ultimate(751),
    "starved_one_upheaval": lambda: so_upheaval(761),
}

# How loud each kind of sound of it should be at its loudest (dB, short-term loudness, as knot_mother.py): its great moments
# land hardest, its blows as the Elders' blows, its voice a little under, a pillar's heart feeding it (heard again and
# again) further under, its ambience well under
LOUDNESS = {"roar": -9, "death": -9.5, "wake": -10, "phase": -9, "ultimate": -9, "upheaval": -9, "bound": -9, "break_free": -9.5,
            "call": -10, "ray": -10, "swoop": -10.5, "drink": -10.5, "quench": -11, "voice": -11, "hurt": -11, "heal": -13.5,
            "ambient": -16}


def kind(name):
    return re.sub(r"\d+$", "", name[len(PREFIX):])


def save(name, x, keep):
    x = x[: int(SR * keep)].copy()
    target = LOUDNESS[kind(name)]
    x = norm(x, 0.97)
    gain = min(2.0, 10 ** ((target - km.loudness(x)) / 20))
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


def main():
    preview = sys.argv[sys.argv.index("--preview") + 1] if "--preview" in sys.argv else None
    only = [a for a in sys.argv[1:] if a.startswith(PREFIX)]
    os.makedirs(OUT, exist_ok=True)
    for name, make in SOUNDS.items():
        if only and name not in only:
            continue
        x, keep = make()
        y = save(name, x, keep)
        print(f"{name}: {len(y) / SR:.2f} s, loudness {km.loudness(y):.1f}, peak {np.max(np.abs(y)):.2f}, bands {km.bands(y)}")
        if preview:
            os.makedirs(preview, exist_ok=True)
            km.spectrogram(y, os.path.join(preview, name + ".png"))


if __name__ == "__main__":
    main()
