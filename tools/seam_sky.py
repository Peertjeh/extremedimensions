# Generates the Seam sky cubemap (six faces). Usage: python3 seam_sky.py <out_dir> [face_size]
# Faces are packed into textures/environment/seam_sky.png as a 3x2 atlas: east west up / down south north.
import numpy as np, sys, os
from PIL import Image
N = int(sys.argv[2]) if len(sys.argv) > 2 else 1024
OUT = sys.argv[1]
rng = np.random.default_rng(1337)
# face: name, forward, right, up
FACES = [
 ("east",  ( 1,0,0), (0,0, 1), (0,1,0)),
 ("west",  (-1,0,0), (0,0,-1), (0,1,0)),
 ("up",    (0, 1,0), (-1,0,0), (0,0,-1)),
 ("down",  (0,-1,0), (-1,0,0), (0,0, 1)),
 ("south", (0,0, 1), (-1,0,0), (0,1,0)),
 ("north", (0,0,-1), ( 1,0,0), (0,1,0)),
]
PERM = rng.permutation(4096).astype(np.int64)
GRAD = rng.random(4096)
def h3(x, y, z):
    return GRAD[(PERM[(PERM[(x * 73856093 & 4095)] + y * 19349663 & 4095) ^ (z * 83492791 & 4095)]) & 4095]
def vnoise(p):
    x, y, z = p[..., 0], p[..., 1], p[..., 2]
    xi, yi, zi = np.floor(x).astype(np.int64), np.floor(y).astype(np.int64), np.floor(z).astype(np.int64)
    xf, yf, zf = x - xi, y - yi, z - zi
    u, v, w = xf*xf*(3-2*xf), yf*yf*(3-2*yf), zf*zf*(3-2*zf)
    r = 0
    for dx in (0, 1):
        for dy in (0, 1):
            for dz in (0, 1):
                wt = (u if dx else 1-u) * (v if dy else 1-v) * (w if dz else 1-w)
                r = r + wt * h3(xi+dx, yi+dy, zi+dz)
    return r
def fbm(p, oct=6, lac=2.03, g=0.5):
    a, s, t = 1.0, 0, 0
    for i in range(oct):
        s = s + a * vnoise(p * (lac ** i) + i * 17.3)
        t += a; a *= g
    return s / t
def smooth(e0, e1, x):
    t = np.clip((x - e0) / (e1 - e0), 0, 1); return t*t*(3-2*t)
def norm(v): return v / np.linalg.norm(v)

def shade(d):
    x, y, z = d[..., 0], d[..., 1], d[..., 2]
    el = np.arcsin(np.clip(y, -1, 1))
    az = np.arctan2(z, x)
    col = np.zeros(d.shape[:-1] + (3,))
    # base gradient: deep indigo zenith, violet haze at the horizon
    zen = np.array([0.020, 0.015, 0.060]); hor = np.array([0.22, 0.14, 0.40])
    t = smooth(0.0, 0.9, np.abs(el))[..., None]
    col += hor * (1 - t) + zen * t
    # domain-warped nebula
    w = np.stack([fbm(d*1.7+3.1,4), fbm(d*1.7+7.7,4), fbm(d*1.7+1.3,4)], -1)
    n = fbm(d*2.4 + w*1.6, 6)
    neb = smooth(0.45, 0.85, n)[..., None]
    purple = np.array([0.55, 0.22, 0.95]); blue = np.array([0.20, 0.35, 0.95]); mag = np.array([0.85, 0.25, 0.70])
    mixc = fbm(d*1.1+11, 3)[..., None]
    nebc = purple * (1-mixc) + blue * mixc
    nebc = nebc * 0.7 + mag * 0.3 * smooth(0.6, 0.8, fbm(d*3+5, 3))[..., None]
    col += nebc * neb * 0.75
    # red rift-light on one horizon (the Nether side)
    red_dir = norm(np.array([0.75, 0.15, -0.65]))
    rd = np.clip(d @ red_dir, 0, 1)
    redn = smooth(0.35, 0.8, fbm(d*3.3+w, 5)) * rd**4
    col += np.array([1.0, 0.18, 0.12])[None] * redn[..., None] * 0.9
    col += np.array([0.5, 0.06, 0.10])[None] * (rd**8)[..., None] * 0.35
    # galaxy band
    gn = norm(np.array([0.35, 0.55, 0.75]))
    gb = np.exp(-((d @ gn) ** 2) / 0.018)
    gdet = smooth(0.35, 0.75, fbm(d*6+2, 6))
    dust = smooth(0.55, 0.7, fbm(d*9+40, 4))
    col += np.array([0.75, 0.70, 1.0])[None] * (gb * gdet * (1 - 0.8*dust))[..., None] * 0.55
    # the vortex at the zenith
    vc = norm(np.array([0.08, 1.0, 0.05]))
    rho = np.arccos(np.clip(d @ vc, -1, 1))
    ax1 = norm(np.cross(vc, [1, 0, 0])); ax2 = np.cross(vc, ax1)
    th = np.arctan2(d @ ax2, d @ ax1)
    spiral = 0.5 + 0.5*np.sin(4*(th + 2.6*np.log(rho + 0.02)) + 3*fbm(d*5, 3))
    vmask = np.exp(-rho / 0.38) * smooth(0.0, 0.05, rho)
    vt = spiral ** 2 * vmask * (0.6 + 0.8*fbm(d*7+9, 4))
    col += np.array([0.65, 0.45, 1.0])[None] * vt[..., None] * 1.3
    col += np.array([0.95, 0.85, 1.0])[None] * np.exp(-rho / 0.05)[..., None] * 1.1
    # light pillars rising from the cloud sea
    for paz, wid, strength in ((0.4, 0.018, 1.0), (2.3, 0.010, 0.6), (-2.0, 0.007, 0.45), (3.6, 0.008, 0.5)):
        da = np.angle(np.exp(1j*(az - paz)))
        pil = np.exp(-(da**2) / wid**2 * 0.5) * smooth(-0.15, 0.0, el) * np.exp(-np.maximum(el, 0) / 0.55)
        flick = 0.75 + 0.5*fbm(np.stack([da*30, el*12, np.full_like(el, paz)], -1), 3)
        col += np.array([0.70, 0.55, 1.0])[None] * (pil * flick * strength)[..., None] * 1.2
        halo = np.exp(-(da**2) / (wid*6)**2 * 0.5) * np.exp(-np.abs(el) / 0.3) * strength
        col += np.array([0.35, 0.20, 0.7])[None] * halo[..., None] * 0.35
    # distant floating islands as silhouettes
    isl = np.zeros_like(el); rim = np.zeros_like(el)
    irng = np.random.default_rng(7)
    for i in range(34):
        a0 = irng.uniform(-np.pi, np.pi); e0 = irng.uniform(-0.04, 0.28) * (1 if i % 3 else 0.5)
        size = irng.uniform(0.02, 0.11) * (1.0 - e0)
        da = np.angle(np.exp(1j*(az - a0)))
        u = da / size
        top = e0 + size*0.08*np.sin(u*9 + i) + size*0.05*fbm(np.stack([u*4, np.full_like(u, i), np.zeros_like(u)], -1), 3)
        prof = np.clip(1 - u**2, 0, None)
        bot = top - size * (0.25 + 1.5 * prof**3) * (0.7 + 0.6*fbm(np.stack([u*14, np.full_like(u, i*3.1), np.ones_like(u)], -1), 3))
        inside = (np.abs(u) < 1) & (el < top) & (el > bot)
        # spires on top of some islands
        if i % 2 == 0:
            for k in range(irng.integers(1, 4)):
                sx = irng.uniform(-0.6, 0.6); sw = irng.uniform(0.03, 0.08); sh = size * irng.uniform(0.6, 2.2)
                sp = (np.abs(u - sx) < sw * (1 - (el - top) / sh)) & (el >= top - 0.002) & (el < top + sh)
                inside |= sp & (np.abs(u) < 1)
        fade = 1 - smooth(0.05, 0.35, e0 - 0.0) * 0.4
        isl = np.maximum(isl, inside * (0.55 + 0.45 * (1 - size / 0.11)) * fade)
        rim = np.maximum(rim, np.where(inside, np.exp(-np.clip((top - el) / (size*0.08 + 1e-4), 0, 50)), 0))
    sil = np.array([0.05, 0.03, 0.10])
    a = np.clip(isl, 0, 1)[..., None]
    col = col * (1 - a) + (sil + col * 0.25) * a
    col += np.array([0.6, 0.4, 1.0])[None] * (rim * a[..., 0])[..., None] * 0.25
    # tiny warm windows on the silhouettes
    win = (fbm(d*180, 2) > 0.80) * a[..., 0]
    col += np.array([1.0, 0.6, 0.25])[None] * win[..., None] * 0.6
    # the cloud sea below the horizon
    below = el < 0.02
    cp = d / np.maximum(-y[..., None], 0.02)  # project onto a plane below
    cl = fbm(np.stack([cp[..., 0]*0.9, cp[..., 2]*0.9, np.zeros_like(el)+3], -1), 6)
    cloud = smooth(0.35, 0.7, cl)
    lit = 0.4 + 0.6 * smooth(0.4, 0.8, fbm(np.stack([cp[..., 0]*0.9+0.05, cp[..., 2]*0.9+0.05, np.zeros_like(el)+3], -1), 6))
    depth = smooth(0.0, -0.9, el)
    cloudc = np.array([0.42, 0.33, 0.70]) * (1 - depth[..., None]) + np.array([0.06, 0.04, 0.14]) * depth[..., None]
    cmix = (cloud * (1 - smooth(-0.02, 0.03, el)))[..., None]
    col = col * (1 - cmix * 0.92) + cloudc * lit[..., None] * cmix * 0.92
    col *= (1 - 0.65 * depth)[..., None]
    return col

def face_dirs(f, r, u, n):
    s = (np.arange(n) + 0.5) / n * 2 - 1
    U, V = np.meshgrid(s, -s)
    d = np.array(f)[None, None] + U[..., None]*np.array(r) + V[..., None]*np.array(u)
    return d / np.linalg.norm(d, axis=-1, keepdims=True)

# stars as 3D directions, splatted per face
NS = 16000
sd = rng.normal(size=(NS, 3)); sd /= np.linalg.norm(sd, axis=1, keepdims=True)
gn = norm(np.array([0.35, 0.55, 0.75]))
keep = rng.random(NS) < (0.35 + 0.65*np.exp(-((sd @ gn)**2)/0.02))
sd = sd[keep]
mag = rng.random(len(sd)) ** 6
tint = np.stack([0.8+0.2*rng.random(len(sd)), 0.8+0.15*rng.random(len(sd)), np.ones(len(sd))], 1)
os.makedirs(OUT, exist_ok=True)
for name, f, r, u in FACES:
    d = face_dirs(f, r, u, N)
    col = shade(d)
    # stars (only above the cloud sea)
    fa, ra, ua = np.array(f), np.array(r), np.array(u)
    df = sd @ fa
    m = df > 0.3
    pu = (sd[m] @ ra) / df[m]; pv = (sd[m] @ ua) / df[m]
    ok = (np.abs(pu) < 1) & (np.abs(pv) < 1) & (sd[m][:, 1] > 0.02)
    px = ((pu[ok] + 1) / 2 * N).astype(int); py = ((1 - pv[ok]) / 2 * N).astype(int)
    mg = mag[m][ok]; tc = tint[m][ok]
    for x0, y0, b, c in zip(px, py, mg, tc):
        rad = 1 + int(b * 3)
        ys, xs = slice(max(0, y0-rad), min(N, y0+rad+1)), slice(max(0, x0-rad), min(N, x0+rad+1))
        yy, xx = np.mgrid[ys, xs]
        g = np.exp(-((yy-y0)**2 + (xx-x0)**2) / (0.35 + b*1.6))
        col[ys, xs] += (0.25 + 1.8*b) * g[..., None] * c
        if b > 0.75:
            L = int(4 + b*10)
            for k in range(-L, L+1):
                fall = (1 - abs(k)/L) ** 2 * b * 0.8
                if 0 <= y0+k < N: col[y0+k, x0] += fall * c
                if 0 <= x0+k < N: col[y0, x0+k] += fall * c
    # gentle tonemap + dither
    col = 1 - np.exp(-col * 1.35)
    col = col ** (1/1.1)
    img = np.clip(col * 255 + rng.random(col.shape) - 0.5, 0, 255).astype(np.uint8)
    Image.fromarray(img).save(f"{OUT}/seam_sky_{name}.png", optimize=True)
    print(name, flush=True)
