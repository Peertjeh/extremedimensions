# Generates VeilRayModel geometry (Java) and matching textures (base + emissive glow) for the Veil Ray:
# a great manta of the Seam with wide segmented wings, a long whip tail and glowing seams of light.
from PIL import Image
import random, math
random.seed(7)
TW, TH = 128, 64

P = []
def part(name, parent, pivot, rot, mat, boxes):
    P.append(dict(name=name, parent=parent, pivot=pivot, rot=rot, mat=mat, boxes=boxes))

part("root", None, (0, 18, 0), (0, 0, 0), None, [])
part("body", "root", (0, 0, 0), (0, 0, 0), "back", [(-5, -2, -9, 10, 4, 18)])
part("head", "body", (0, 0, -9), (0, 0, 0), "back", [(-4, -1.5, -5, 8, 3, 5)])
part("horn_l", "head", (3, 0, -5), (0, -0.35, 0), "horn", [(-0.5, -0.5, -5, 1, 1, 5)])
part("horn_r", "head", (-3, 0, -5), (0, 0.35, 0), "horn", [(-0.5, -0.5, -5, 1, 1, 5)])
for side, sx in (("l", 1), ("r", -1)):
    x0 = 0 if sx > 0 else -12
    part(f"wing_{side}", "body", (5 * sx, 0, 0), (0, 0, 0), "wing", [(x0, -1, -8, 12, 2, 15)])
    x1 = 0 if sx > 0 else -11
    part(f"wingtip_{side}", f"wing_{side}", (12 * sx, 0, 0), (0, 0, 0), "wing", [(x1, -0.5, -6, 11, 1, 10)])
    x2 = 0 if sx > 0 else -7
    part(f"wingend_{side}", f"wingtip_{side}", (11 * sx, 0, 0), (0, 0, 0), "wing", [(x2, -0.5, -4, 7, 1, 5)])
part("tail0", "body", (0, 0, 9), (0, 0, 0), "back", [(-2, -1, 0, 4, 2, 8)])
part("tail1", "tail0", (0, 0, 8), (0, 0, 0), "back", [(-1, -0.5, 0, 2, 1, 10)])
part("tail2", "tail1", (0, 0, 10), (0, 0, 0), "horn", [(-0.5, -0.5, 0, 1, 1, 12)])
part("fin", "body", (0, -2, 2), (0, 0, 0), "horn", [(-0.5, -3, -3, 1, 3, 7)])

def boxsize(b):
    x, y, z, w, h, d = b
    W, H, D = math.ceil(w), math.ceil(h), math.ceil(d)
    return 2 * (W + D), D + H, (W, H, D)
items = []
for p in P:
    for bi, b in enumerate(p["boxes"]):
        uw, uh, dims = boxsize(b)
        items.append([p, bi, uw, uh, dims, None])
items.sort(key=lambda it: -it[3])
cx = cy = rowh = 0
for it in items:
    uw, uh = it[2], it[3]
    if cx + uw > TW:
        cx = 0; cy += rowh; rowh = 0
    it[5] = (cx, cy)
    cx += uw; rowh = max(rowh, uh)
assert cy + rowh <= TH, ("texture overflow", cy + rowh)
uv = {(it[0]["name"], it[1]): (it[5], it[4]) for it in items}

base = Image.new("RGBA", (TW, TH), (0, 0, 0, 0)); bp = base.load()
glow = Image.new("RGBA", (TW, TH), (0, 0, 0, 0)); gp = glow.load()
# Dark indigo backs fading to pale violet bellies; the wings carry rows of light
TOP = {"back": (58, 44, 96), "wing": (66, 50, 112), "horn": (96, 84, 130)}
BELLY = {"back": (150, 130, 200), "wing": (136, 116, 186), "horn": (175, 160, 205)}
def shade(c, k):
    return tuple(max(0, min(255, int(v * k + random.randint(-4, 4)))) for v in c) + (255,)

for p in P:
    for bi, b in enumerate(p["boxes"]):
        (u, v), (W, H, D) = uv[(p["name"], bi)]
        mat = p["mat"]
        # top face: (u+D, v) size W x D ; bottom face: (u+D+W, v) size W x D ; sides below
        for yy in range(D + H):
            for xx in range(2 * (W + D)):
                px, py = u + xx, v + yy
                if yy < D:
                    if D <= xx < D + W:
                        c = shade(TOP[mat], 0.9 + 0.25 * random.random())        # top
                    elif D + W <= xx < D + 2 * W:
                        c = shade(BELLY[mat], 0.92 + 0.12 * random.random())     # belly
                    else:
                        continue
                else:
                    c = shade(TOP[mat], 1.15 + 0.2 * random.random())            # edges
                bp[px, py] = c
        # glowing seams along the top of wings and back
        if mat in ("wing", "back"):
            tx, ty = u + D, v
            for yy in range(D):
                for xx in range(W):
                    stripe = (xx + yy * 0.5) % 4 < 0.9 if mat == "wing" else (xx == W // 2 and yy % 3 != 0)
                    spot = random.random() < 0.05
                    if stripe or spot:
                        a = 255 if spot else 210
                        gp[tx + xx, ty + yy] = (200, 150, 255, a)
                        bp[tx + xx, ty + yy] = (120, 80, 200, 255)
            # trailing wing edge glows faintly underneath too
            if mat == "wing":
                bx = u + D + W
                for xx in range(W):
                    gp[bx + xx, v + D - 1] = (230, 200, 255, 160)
        if mat == "horn":
            for yy in range(D + H):
                for xx in range(2 * (W + D)):
                    if random.random() < 0.15:
                        gp[u + xx, v + yy] = (190, 140, 255, 200)
        if p["name"] == "head":
            fx, fy = u + D, v + D  # front face W x H
            for ex in (1, W - 2):
                gp[fx + ex, fy + 1] = (255, 230, 255, 255)
                bp[fx + ex, fy + 1] = (230, 200, 255, 255)

import sys, os
out = sys.argv[1] if len(sys.argv) > 1 else "."
base.save(os.path.join(out, "veil_ray.png")); glow.save(os.path.join(out, "veil_ray_glow.png"))
prev = Image.new("RGBA", (TW, TH), (60, 60, 60, 255)); prev.alpha_composite(base); prev.alpha_composite(glow)


def f(v):
    s = ("%.3f" % v).rstrip("0").rstrip(".")
    if s in ("-0", ""): s = "0"
    return s + "F"
lines = []
lines.append("\t\tMeshDefinition mesh = new MeshDefinition();")
lines.append("\t\tPartDefinition parts = mesh.getRoot();")
var = {None: "parts"}
for p in P:
    vname = "p_" + p["name"]
    var[p["name"]] = vname
    cube = "CubeListBuilder.create()"
    for bi, b in enumerate(p["boxes"]):
        (u, vv), _ = uv[(p["name"], bi)]
        x, y, z, w, h, d = b
        cube += f".texOffs({u}, {vv}).addBox({f(x)}, {f(y)}, {f(z)}, {f(w)}, {f(h)}, {f(d)})"
    px, py, pz = p["pivot"]; rx, ry, rz = p["rot"]
    pose = f"PartPose.offsetAndRotation({f(px)}, {f(py)}, {f(pz)}, {f(rx)}, {f(ry)}, {f(rz)})"
    lines.append(f"\t\tPartDefinition {vname} = {var[p['parent']]}.addOrReplaceChild(\"{p['name']}\", {cube}, {pose});")
lines.append(f"\t\treturn LayerDefinition.create(mesh, {TW}, {TH});")
open(os.path.join(out, "geometry.java.txt"), "w").write("\n".join(lines) + "\n")
print(len(P), "parts; texture rows used:", cy + rowh)
