"""Renders an Elder's model to a PNG without the game: textured boxes, a z-buffer, simple light and the glow layer.

usage: python3 tools/elders/preview.py <name> <out.png> [front|side|three|top|bottom|over|under] [scale] [crop: x0,y0,x1,y1 in model pixels]

Reads the preview JSON written by build.py (python3 tools/elders/build.py <dir>, or tools/lurker/build.py) from the
same directory as <out.png>, and the textures from the resource folder (the Elders' folder, else the entity folder). Model space is Minecraft's (pixels, Y down, the creature faces -Z); the
view looks at the creature's front.
"""
import json
import math
import os
import sys

import numpy as np
from PIL import Image

ROOT = os.path.join(os.path.dirname(__file__), "..", "..")
TEX = os.path.join(ROOT, "src/client/resources/assets/extremedimensions/textures/entity/elder")


def faces(box):
    """The six faces of a box as (corners[4] in model space, uv corners[4]) after Minecraft's cube layout."""
    x, y, z, w, h, d = box["b"]
    inf = box["inflate"]
    x0, y0, z0 = x - inf, y - inf, z - inf
    x1, y1, z1 = x + w + inf, y + h + inf, z + d + inf
    W, H, D = box["dims"]
    u, v = box["uv"]
    v1 = (x0, y0, z0); v2 = (x1, y0, z0); v3 = (x1, y1, z0); v4 = (x0, y1, z0)
    v5 = (x0, y0, z1); v6 = (x1, y0, z1); v7 = (x1, y1, z1); v8 = (x0, y1, z1)
    f4, f5, f6, f7, f8, f9 = u, u + D, u + D + W, u + D + W + W, u + D + W + D, u + D + W + D + W
    f10, f11, f12 = v, v + D, v + D + H
    polys = [
        ((v6, v5, v1, v2), (f5, f10, f6, f11)),
        ((v3, v4, v8, v7), (f6, f11, f7, f10)),
        ((v1, v5, v8, v4), (f4, f11, f5, f12)),
        ((v2, v1, v4, v3), (f5, f11, f6, f12)),
        ((v6, v2, v3, v7), (f6, f11, f8, f12)),
        ((v5, v6, v7, v8), (f8, f11, f9, f12)),
    ]
    out = []
    for verts, (ua, va, ub, vb) in polys:
        uvs = ((ub, va), (ua, va), (ua, vb), (ub, vb))
        out.append((verts, uvs))
    return out


def render(name, out, view="front", scale=3.0, crop=None):
    data = json.load(open(os.path.join(os.path.dirname(out) or ".", name + ".json")))
    tex = TEX if os.path.exists(os.path.join(TEX, name + ".png")) else os.path.dirname(TEX)
    base = np.asarray(Image.open(os.path.join(tex, name + ".png")).convert("RGBA"), dtype=np.float32) / 255
    glow = np.asarray(Image.open(os.path.join(tex, name + "_glow.png")).convert("RGBA"), dtype=np.float32) / 255
    th, tw = base.shape[:2]
    yaw = {"front": 0.0, "side": math.pi / 2, "three": math.pi / 5, "top": 0.0, "bottom": 0.0, "over": math.pi / 5,
           "under": math.pi / 5}[view]
    # over and under tilt the three-quarter view to look down on the creature or up at it
    pitch = {"over": -0.6, "under": 0.6}.get(view, 0.0)
    cy, sy = math.cos(yaw), math.sin(yaw)
    cp, sp = math.cos(pitch), math.sin(pitch)

    def view_of(p):
        # Turn the model about Y, then look along +Z from in front (screen x = -model x, so its left is on our right);
        # from above the screen runs along x and z (the front up), from below the same, mirrored
        x, y, z = p
        if view == "top":
            return (-x, z, y)
        if view == "bottom":
            return (x, z, -y)
        xr, zr = x * cy - z * sy, x * sy + z * cy
        return (-xr, y * cp - zr * sp, y * sp + zr * cp)

    quads = []
    for box in data["boxes"]:
        r, t = box["r"], box["t"]
        for verts, uvs in faces(box):
            world = []
            for p in verts:
                q = [r[i][0] * p[0] + r[i][1] * p[1] + r[i][2] * p[2] + t[i] for i in range(3)]
                world.append(view_of(q))
            quads.append((world, uvs))
    pts = np.array([p for q, _ in quads for p in q])
    if crop:
        x0, y0, x1, y1 = crop
    else:
        x0, y0 = pts[:, 0].min() - 4, pts[:, 1].min() - 4
        x1, y1 = pts[:, 0].max() + 4, pts[:, 1].max() + 4
    W, H = int((x1 - x0) * scale), int((y1 - y0) * scale)
    img = np.zeros((H, W, 3), dtype=np.float32)
    img[:] = (0.07, 0.06, 0.1)
    zbuf = np.full((H, W), np.inf, dtype=np.float32)
    light = np.array([0.35, -0.75, -0.55])
    light /= np.linalg.norm(light)
    for world, uvs in quads:
        P = np.array(world, dtype=np.float64)
        n = np.cross(P[1] - P[0], P[3] - P[0])
        nn = np.linalg.norm(n)
        if nn < 1e-9:
            continue
        n /= nn
        if n[2] > 1e-6:
            continue  # faces away from us
        S = np.stack([(P[:, 0] - x0) * scale, (P[:, 1] - y0) * scale], axis=1)
        bx0, by0 = max(0, int(np.floor(S[:, 0].min()))), max(0, int(np.floor(S[:, 1].min())))
        bx1, by1 = min(W, int(np.ceil(S[:, 0].max())) + 1), min(H, int(np.ceil(S[:, 1].max())) + 1)
        if bx1 <= bx0 or by1 <= by0:
            continue
        # Affine map from (s, t) on the quad (corner 1 -> 0 along s, corner 1 -> 2 along t) to the screen
        o = S[1]
        a = S[0] - S[1]
        b = S[2] - S[1]
        det = a[0] * b[1] - a[1] * b[0]
        if abs(det) < 1e-9:
            continue
        gx, gy = np.meshgrid(np.arange(bx0, bx1) + 0.5, np.arange(by0, by1) + 0.5)
        dx, dy = gx - o[0], gy - o[1]
        s = (dx * b[1] - dy * b[0]) / det
        tt = (a[0] * dy - a[1] * dx) / det
        inside = (s >= 0) & (s <= 1) & (tt >= 0) & (tt <= 1)
        if not inside.any():
            continue
        depth = P[1, 2] + (P[0, 2] - P[1, 2]) * s + (P[2, 2] - P[1, 2]) * tt
        (u1, v1), (u0, _), (_, v2) = uvs[0], uvs[1], uvs[2]
        tu = u0 + (u1 - u0) * s
        tv = v1 + (v2 - v1) * tt
        iu = np.clip(np.floor(np.minimum(tu, max(u0, u1) - 1e-3)).astype(int), 0, tw - 1)
        iv = np.clip(np.floor(np.minimum(tv, max(v1, v2) - 1e-3)).astype(int), 0, th - 1)
        texel = base[iv, iu]
        gl = glow[iv, iu]
        ok = inside & (texel[..., 3] > 0.5) & (depth < zbuf[by0:by1, bx0:bx1])
        if not ok.any():
            continue
        shade = 0.45 + 0.55 * max(0.0, float(-np.dot(n, light)))
        col = texel[..., :3] * shade + gl[..., :3] * gl[..., 3:4] * 0.9
        region = img[by0:by1, bx0:bx1]
        region[ok] = np.clip(col[ok], 0, 1)
        zbuf[by0:by1, bx0:bx1][ok] = depth[ok]
    Image.fromarray((img * 255).astype(np.uint8)).save(out)
    print(f"{out}: {W}x{H}")


if __name__ == "__main__":
    name, out = sys.argv[1], sys.argv[2]
    view = sys.argv[3] if len(sys.argv) > 3 else "front"
    sc = float(sys.argv[4]) if len(sys.argv) > 4 else 3.0
    cr = tuple(float(c) for c in sys.argv[5].split(",")) if len(sys.argv) > 5 else None
    render(name, out, view, sc, cr)
