"""The voices of the Lurkers of the deep: the Cave Lurker and the Sculk Lurker, each in a sound of its own place.

The Cave Lurker lives in stone and hunts by ear: dry clicks thrown into the tunnels to hear what answers, a wet rasp
of breath, a sharp hiss before it strikes, a hoarse screech that runs off down the tunnels when it starts to hunt,
claws ticking on rock, the crunch of its grab, and a rattle of slowing clicks as it dies.

The Sculk Lurker lives in the Deep Dark and serves what sleeps there: wet sculk chirps with a cyan, glassy ring, the
scratching rattle it makes at a shrieker, the low double pulse and rising chirps with which it calls the Warden, a
soft pulse as it leads it, squelching steps, and a wet crackle dissolving into the sculk as it dies.

Deterministic (fixed seeds). Builds on tools/sounds/ambient.py. Needs numpy and ffmpeg.
usage: python3 tools/sounds/lurker_voices.py [--out=DIR] [--mp3] [names...]
"""
import os
import subprocess
import sys
import tempfile
import wave

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from ambient import (SR, band, bell_env, env, harm, highpass, lowpass, norm, partials, peaks, phase, pink,  # noqa: E402
                     reverb, t_, thud, white)

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "src/client/resources/assets/extremedimensions/sounds/lurker")


# ------------------------------------------------------------------ shared pieces

def level(x, peak=0.85):
    """A short sound brought to a set peak, its very start and end eased so nothing clicks at the edges."""
    x = x - np.mean(x)
    x = norm(x) * peak
    n = len(x)
    a, b = min(n // 4, int(SR * 0.004)), min(n // 4, int(SR * 0.05))
    if a:
        x[:a] *= np.linspace(0, 1, a)
    if b:
        x[-b:] *= np.linspace(1, 0, b)
    return x


def tunnel(rng, x, wet=0.45):
    """Stone around it: a short bright room and two echoes off the tunnel walls, the later one darker."""
    n = len(x)
    out = reverb(rng, x, tail=1.3, mix=wet, bright=4800, pre=0.012)
    for delay, gain, cut in ((0.075, 0.32, 3800), (0.155, 0.2, 2200), (0.29, 0.1, 1400)):
        k = int(SR * delay)
        echo = np.zeros(n)
        echo[k:] = lowpass(x, cut)[:n - k] * gain
        out += echo
    return out


def deep_dark(rng, x, wet=0.4):
    """The Deep Dark: a large hall, but the sculk soaks up the highs."""
    return reverb(rng, x, tail=2.4, mix=wet, bright=2400, pre=0.03)


def click(rng, t, s, f=2600.0, gain=1.0):
    """One dry click: a tick of breath and tongue, rung through a small hollow."""
    burst = rng.standard_normal(len(t)) * env(t, s, 0.0002, 0.0025)
    ring = np.sin(2 * np.pi * f * np.maximum(t - s, 0)) * env(t, s, 0.0003, 0.006) * 0.6
    body = np.sin(2 * np.pi * (f * 0.36) * np.maximum(t - s, 0)) * env(t, s, 0.0005, 0.012) * 0.35
    return (band(burst, f * 0.5, f * 2.2) + ring + body) * gain


def chirp(rng, t, s, f0, d=0.06, gain=1.0):
    """A wet sculk chirp: a blip that drops fast in pitch, with a little pop of something soft."""
    u = np.maximum(t - s, 0)
    f = f0 * (1 + 0.7 * np.exp(-u / 0.012))
    tone = (np.sin(phase(f)) + 0.25 * np.sin(phase(f * 2.02))) * env(t, s, 0.002, d)
    pop = lowpass(rng.standard_normal(len(t)), 1600) * env(t, s, 0.001, 0.008) * 0.6
    return (tone + pop) * gain


def glass_ring(t, s, f, d=0.7, gain=1.0):
    """The cyan of sculk: a small glassy ring, its partials a bell's."""
    return partials(t, s, f, [1.0, 2.76, 5.4], [d, d * 0.45, d * 0.2], [1.0, 0.4, 0.15], beat=0.6) * gain


def crackle_wet(rng, t, rate, lo, hi, decay=0.004):
    """Sparse wet pops: bubbles in something living."""
    n = len(t)
    x = np.zeros(n)
    count = rng.poisson(rate * n / SR)
    idx = rng.integers(0, n, count)
    x[idx] = rng.uniform(-1, 1, count)
    k = int(SR * decay * 6)
    kern = np.exp(-np.arange(k) / (SR * decay)) * np.sin(2 * np.pi * rng.uniform(lo, hi) * np.arange(k) / SR)
    return np.convolve(x, kern)[:n]


# ------------------------------------------------------------------ the Cave Lurker

def cave_click(rng, v):
    """Clicks thrown into the dark to hear what answers. 1: steady. 2: quickening. 3: in pairs."""
    d = 1.6
    t = t_(d)
    x = np.zeros(len(t))
    if v == 1:
        times = [0.05 + 0.095 * i for i in range(5)]
    elif v == 2:
        times, s, gap = [], 0.05, 0.15
        for _ in range(8):
            times.append(s)
            s += gap
            gap *= 0.8
    else:
        times = []
        for i in range(3):
            times += [0.05 + 0.28 * i, 0.05 + 0.28 * i + 0.045]
    for i, s in enumerate(times):
        x += click(rng, t, s, f=rng.uniform(2300, 3000), gain=rng.uniform(0.75, 1.0))
    return level(tunnel(rng, x, 0.5))


def cave_breath(rng, v):
    """A wet rasp of breath through teeth, in and out. 2: slower and deeper."""
    d = 3.0 if v == 1 else 3.6
    t = t_(d)
    inhale = (0.15, 1.0) if v == 1 else (0.2, 1.3)
    exhale = (1.45, 1.3) if v == 1 else (1.8, 1.6)
    shape = bell_env(t, inhale[0], inhale[1] * 0.6, 0.0, inhale[1] * 0.4) * 0.7 + bell_env(t, exhale[0], exhale[1] * 0.25, 0.1, exhale[1] * 0.65)
    air = peaks(pink(rng, d), [(520 if v == 1 else 420, 120, 1.0), (1150, 220, 0.6), (2600, 400, 0.25)])
    throat = crackle_wet(rng, t, 28, 1200, 2600) * 0.25
    growl = lowpass(harm(np.full(len(t), 62.0 if v == 1 else 52.0) * (1 + 0.04 * np.sin(phase(np.full(len(t), 23.0)))), top=900, tilt=1.2), 420)
    x = norm(air) * shape + throat * shape + norm(growl) * shape * 0.35
    return level(tunnel(rng, x, 0.4), 0.7)


def cave_hiss(rng, v):
    """The sharp breath in before it strikes, and two clicks."""
    d = 0.9
    t = t_(d)
    rise = np.clip(t / 0.45, 0, 1) ** 2 * (t < 0.47)
    hiss = band(white(rng, d), 2200, 8000) * rise
    x = norm(hiss) * 0.8 + click(rng, t, 0.5, 3100) + click(rng, t, 0.56, 2700)
    return level(tunnel(rng, x, 0.35))


def cave_hunt(rng, v):
    """A hoarse screech that runs off down the tunnels: it has heard enough. 2: lower, with a catch in it."""
    d = 2.6
    t = t_(d)
    if v == 1:
        f = np.interp(t, [0, 0.08, 0.38, 1.4, 2.6], [380, 520, 1300, 640, 600])
    else:
        f = np.interp(t, [0, 0.12, 0.3, 0.55, 1.6, 2.6], [300, 420, 980, 760, 520, 500])
    f = f * (1 + 0.03 * np.sin(phase(np.full(len(t), 17.0))))
    e = bell_env(t, 0.02, 0.06, 0.5, 0.9)
    growl = 1 + 0.55 * np.sin(phase(np.full(len(t), 44.0)))
    voice = harm(f, top=6500, tilt=0.8) * growl
    rasp = band(white(rng, d), 900, 5200) * growl
    x = peaks(voice, [(900, 220, 1.0), (2300, 420, 0.7), (3500, 500, 0.4)]) + norm(rasp) * 0.35 * np.max(np.abs(voice))
    x = np.tanh(norm(x) * 2.2) * e
    return level(tunnel(rng, x, 0.55))


def cave_hurt(rng, v):
    """A burst of clicks and a squeal, cut short."""
    d = 0.7
    t = t_(d)
    x = np.zeros(len(t))
    for i in range(6 if v == 1 else 4):
        x += click(rng, t, 0.01 + 0.024 * i, rng.uniform(2600, 3400), 0.8)
    u = np.maximum(t - 0.12, 0)
    f = (950 if v == 1 else 780) * np.exp(-u / 0.35)
    squeal = peaks(harm(np.maximum(f, 300), top=5000, tilt=0.9), [(1100, 300, 1.0), (2600, 500, 0.5)]) * env(t, 0.12, 0.01, 0.12)
    x += norm(squeal) * 0.8 + band(white(rng, d), 2000, 7000) * env(t, 0.1, 0.005, 0.08) * 0.3
    return level(tunnel(rng, x, 0.3))


def cave_death(rng, v):
    """Its clicks slow and sink, a last breath goes out, and it settles into the stone."""
    d = 3.2
    t = t_(d)
    x = np.zeros(len(t))
    s, gap, f = 0.02, 0.035, 3000.0
    while s < 1.7:
        x += click(rng, t, s, f, 0.9 * (1 - s / 2.0))
        s += gap
        gap *= 1.22
        f *= 0.95
    breath = band(pink(rng, d), 300, 1600) * bell_env(t, 0.6, 0.2, 0.3, 1.4)
    x += norm(breath) * 0.4
    x += norm(thud(rng, t, 1.75, 160, 38, 0.3, d)) * 0.7
    x += band(white(rng, d) * np.exp(-np.maximum(t - 1.8, 0) / 0.5) * (t > 1.8), 700, 3200) * 0.12
    return level(tunnel(rng, x, 0.5))


def cave_step(rng, v):
    """Claws ticking on rock: two quick taps."""
    d = 0.3
    t = t_(d)
    gap = (0.045, 0.06, 0.035)[v - 1]
    x = click(rng, t, 0.005, rng.uniform(3200, 4200), 0.7) + click(rng, t, 0.005 + gap, rng.uniform(3000, 4000), 0.55)
    x += np.sin(2 * np.pi * 190 * np.maximum(t - 0.004, 0)) * env(t, 0.004, 0.001, 0.02) * 0.4
    return level(reverb(rng, x, tail=0.5, mix=0.2, bright=5000), 0.6)


def cave_grab(rng, v):
    """It lunges and takes hold: a rush of air, a crunch, a deep blow."""
    d = 1.6
    t = t_(d)
    from ambient import sweep
    whoosh = sweep(white(rng, d), lambda s, fr: np.exp(-0.5 * (np.log2(np.maximum(fr, 1) / (400 + 2600 * min(1, s / 0.25))) / 0.5) ** 2))
    whoosh *= bell_env(t, 0.0, 0.18, 0.02, 0.08)
    crunch = band(crackle_wet(rng, t, 900, 600, 3800, decay=0.002), 500, 4500) * bell_env(t, 0.22, 0.01, 0.08, 0.06)
    blow = thud(rng, t, 0.24, 110, 36, 0.35, d)
    x = norm(whoosh) * 0.6 + norm(crunch) * 0.8 + norm(blow) * 1.0
    return level(tunnel(rng, x, 0.4))


# ------------------------------------------------------------------ the Sculk Lurker

def sculk_chirp(rng, v):
    """Wet chirps on the sculk, a glassy cyan ring under them, something bubbling. Three ways."""
    d = 1.8
    t = t_(d)
    x = np.zeros(len(t))
    count = (4, 3, 6)[v - 1]
    s = 0.05
    for i in range(count):
        x += chirp(rng, t, s, rng.uniform(1900, 3200), rng.uniform(0.04, 0.08), rng.uniform(0.6, 1.0))
        s += rng.uniform(0.07, 0.16)
    x += glass_ring(t, s + 0.02, (2900, 2640, 3150)[v - 1], 0.7, 0.35)
    x += crackle_wet(rng, t, 18, 300, 1100) * 0.18
    return level(deep_dark(rng, x, 0.38), 0.75)


def sculk_rattle(rng, v):
    """It scratches at a shrieker: claws on bone and a dry rattle, the sculk ringing."""
    d = 1.7
    t = t_(d)
    from ambient import scrape
    scratch = scrape(rng, t, d, bell_env(t, 0.02, 0.05, 0.35, 0.15), base=1800 if v == 1 else 1500)
    rate = 32.0 if v == 1 else 26.0
    gate = (np.sin(phase(np.full(len(t), rate))) > 0.6).astype(float)
    rattle = peaks(white(rng, d) * gate, [(1250, 120, 1.0), (2750, 260, 0.6)]) * bell_env(t, 0.45, 0.05, 0.55, 0.3)
    x = norm(highpass(scratch, 900)) * 0.6 + norm(rattle) * 0.8 + glass_ring(t, 0.5, 2300, 0.9, 0.25)
    return level(deep_dark(rng, x, 0.32), 0.8)


def sculk_call(rng, v):
    """It calls the Warden: a low double pulse, felt in the chest, and chirps that rise. 2: two pulses, then the bell."""
    d = 2.8
    t = t_(d)
    x = np.zeros(len(t))
    for k, s in enumerate((0.05, 0.85) if v == 2 else (0.05,)):
        for j, dt in enumerate((0.0, 0.26)):
            u = np.maximum(t - s - dt, 0)
            x += np.sin(phase(55 * (1 + 0.6 * np.exp(-u / 0.05)))) * env(t, s + dt, 0.01, 0.22) * (1.0 if j == 0 else 0.65)
    start = 0.6 if v == 1 else 1.45
    for i, f in enumerate((1500, 1900, 2400, 3000)):
        x += chirp(rng, t, start + 0.12 * i, f, 0.07, 0.5)
    x += glass_ring(t, start + 0.55, 1318.5, 1.2, 0.45) + glass_ring(t, start + 0.8, 1760.0, 1.2, 0.35)
    return level(deep_dark(rng, x, 0.45), 0.8)


def sculk_pulse(rng, v):
    """As it leads the Warden: one soft pulse and a muted chirp."""
    d = 0.5
    t = t_(d)
    u = np.maximum(t - 0.01, 0)
    x = np.sin(phase(60 * (1 + 0.5 * np.exp(-u / 0.04)))) * env(t, 0.01, 0.008, 0.12)
    x += lowpass(chirp(rng, t, 0.08 if v == 1 else 0.11, 2200 if v == 1 else 2500, 0.05, 0.5), 3500)
    return level(deep_dark(rng, x, 0.3), 0.55)


def sculk_hurt(rng, v):
    """A squelch, and a chirp that falls."""
    d = 0.7
    t = t_(d)
    squelch = peaks(white(rng, d), [(700 if v == 1 else 560, 180, 1.0), (1500, 300, 0.5)]) * env(t, 0.0, 0.004, 0.06)
    u = np.maximum(t - 0.04, 0)
    f = (2600 if v == 1 else 2300) * np.exp(-u / 0.25)
    fall = (np.sin(phase(np.maximum(f, 600))) + 0.3 * np.sin(phase(np.maximum(f, 600) * 2))) * env(t, 0.04, 0.005, 0.14)
    x = norm(squelch) * 0.8 + fall * 0.8 + crackle_wet(rng, t, 60, 400, 1200) * env(t, 0.0, 0.01, 0.2) * 0.3
    return level(deep_dark(rng, x, 0.28))


def sculk_death(rng, v):
    """A wet crackle that thins away, a sigh into the sculk, and the bloom's soft chime."""
    d = 3.2
    t = t_(d)
    fade = np.exp(-t / 0.9)
    wet = crackle_wet(rng, t, 70, 300, 1400) * fade
    from ambient import sweep
    sigh = sweep(pink(rng, d), lambda s, fr: 1 / np.sqrt(1 + (fr / (1600 - 1200 * min(1, s / 1.6))) ** 4)) * bell_env(t, 0.25, 0.3, 0.3, 1.2)
    x = norm(wet) * 0.5 + norm(sigh) * 0.6
    for i, f in enumerate((2600, 2100, 1700)):
        x += chirp(rng, t, 0.1 + 0.18 * i, f, 0.07, 0.5 - 0.12 * i)
    x += glass_ring(t, 1.2, 880.0, 1.4, 0.4)
    return level(deep_dark(rng, x, 0.45), 0.8)


def sculk_step(rng, v):
    """Soft, wet steps on the sculk."""
    d = 0.32
    t = t_(d)
    f = (380, 470, 330)[v - 1]
    x = peaks(white(rng, d), [(f, 90, 1.0), (f * 2.4, 200, 0.35)]) * env(t, 0.005, 0.006, 0.05)
    x += crackle_wet(rng, t, 40, 500, 1300) * env(t, 0.0, 0.01, 0.08) * 0.4
    return level(reverb(rng, x, tail=0.6, mix=0.2, bright=2400), 0.5)


# (name, voice, variants, seed)
SOUNDS = [
    ("cave_click", cave_click, 3, 7101), ("cave_breath", cave_breath, 2, 7102), ("cave_hiss", cave_hiss, 1, 7103),
    ("cave_hunt", cave_hunt, 2, 7104), ("cave_hurt", cave_hurt, 2, 7105), ("cave_death", cave_death, 1, 7106),
    ("cave_step", cave_step, 3, 7107), ("cave_grab", cave_grab, 1, 7108),
    ("sculk_chirp", sculk_chirp, 3, 7201), ("sculk_rattle", sculk_rattle, 2, 7202), ("sculk_call", sculk_call, 2, 7203),
    ("sculk_pulse", sculk_pulse, 2, 7204), ("sculk_hurt", sculk_hurt, 2, 7205), ("sculk_death", sculk_death, 1, 7206),
    ("sculk_step", sculk_step, 3, 7207),
]


def write(out_dir, name, x, mp3):
    with tempfile.TemporaryDirectory() as tmp:
        wav = os.path.join(tmp, name + ".wav")
        with wave.open(wav, "wb") as w:
            w.setnchannels(1)
            w.setsampwidth(2)
            w.setframerate(SR)
            w.writeframes((np.clip(x, -1, 1) * 32767).astype(np.int16).tobytes())
        subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", wav, "-c:a", "libvorbis", "-q:a", "4", os.path.join(out_dir, name + ".ogg")], check=True)
        if mp3:
            subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", wav, "-c:a", "libmp3lame", "-q:a", "3", os.path.join(out_dir, name + ".mp3")], check=True)


def main():
    mp3 = "--mp3" in sys.argv
    out = next((a[len("--out="):] for a in sys.argv[1:] if a.startswith("--out=")), OUT)
    only = [a for a in sys.argv[1:] if not a.startswith("--")]
    os.makedirs(out, exist_ok=True)
    for name, voice, variants, seed in SOUNDS:
        if only and name not in only:
            continue
        for v in range(1, variants + 1):
            x = voice(np.random.default_rng(seed * 10 + v), v)
            write(out, f"{name}{v}", x, mp3)
            print(f"{name}{v}: {len(x) / SR:.2f} s")


if __name__ == "__main__":
    main()
