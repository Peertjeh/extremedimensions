# Generates LurkerModel geometry (Java) and matching 128x128 textures (base + emissive glow).
from PIL import Image
import random, math
random.seed(26)
TW, TH = 128, 256

# part: (name, parent, pivot, rot, material, boxes[(x,y,z,w,h,d)])
P = []
def part(name, parent, pivot, rot, mat, boxes):
    P.append(dict(name=name, parent=parent, pivot=pivot, rot=rot, mat=mat, boxes=boxes))

part("root", None, (0, 24, 0), (0, 0, 0), None, [])
part("leg_l", "root", (5, -13, 3), (0, 0, 0), "limb", [(-3, 0, -3, 6, 13, 6)])
part("leg_r", "root", (-5, -13, 3), (0, 0, 0), "limb", [(-3, 0, -3, 6, 13, 6)])
part("hips", "root", (0, -13, 3), (0.62, 0, 0), "cloak", [(-9, -20, -6, 18, 20, 12)])
part("hump", "hips", (0, -14, 4), (-0.25, 0, 0), "cloak", [(-10, -12, -2, 20, 14, 8)])
skirt = [(-7, -6, 1, 9), (-2.5, -6.5, 1, 11), (3, -6, 1, 10), (7.5, -3, 1, 8),
         (-7, 5.5, 1, 11), (-2, 6.5, 1, 12), (3.5, 6, 1, 10), (-8, 0, 1, 8), (8, 1, 1, 9)]
for i, (x, z, w, l) in enumerate(skirt):
    ww = 4 if i % 3 != 1 else 5
    part(f"skirt{i}", "hips", (x, -1, z), (0, 0, 0), "cloak", [(-ww / 2, 0, -0.5, ww, l, 1)])
part("cape", "hips", (0, -19, 6), (0, 0, 0), "cloak", [(-10, 0, 0, 20, 24, 2)])
part("mantle_l", "hips", (8, -20, -1), (0, 0, 0.35), "cloak", [(-3, -2, -5, 8, 5, 11)])
part("mantle_r", "hips", (-8, -20, -1), (0, 0, -0.35), "cloak", [(-5, -2, -5, 8, 5, 11)])
part("neck", "hips", (0, -19, -3), (0.35, 0, 0), "cloak", [(-3.5, -7, -3.5, 7, 8, 7)])
part("head", "neck", (0, -6, -1), (-0.77, 0, 0), "mask", [(-6, -11, -10, 12, 12, 13)])
part("hood", "head", (0, 0, 0), (0, 0, 0), "cloak", [(-7, -12.5, -8, 14, 7, 14)])
tend = [(-5, -8, 1, 14), (-3, -9.5, 2, 10), (-1, -9, 1, 18), (1.5, -9.5, 2, 13),
        (3.5, -9, 1, 16), (5, -7.5, 1, 11), (-2, -6, 1, 9), (2, -6, 1, 12)]
for i, (x, z, w, l) in enumerate(tend):
    part(f"tendril{i}", "head", (x, 1, z), (0, 0, 0), "tendril", [(-w / 2, 0, -w / 2, w, l, w)])
    part(f"drip{i}", f"tendril{i}", (0, l, 0), (0, 0, 0), "tendril", [(-1, 0, -1, 2, 2, 2)])
for side, sx in (("l", 1), ("r", -1)):
    part(f"arm_{side}", "hips", (10 * sx, -18, -2), (0, 0, 0), "cloak", [(-3, -2, -3, 6, 14, 6)])
    part(f"rag_{side}", f"arm_{side}", (3 * sx, 3, 0), (0, 0, 0), "cloak", [(-0.5, 0, -1.5, 1, 13, 3)])
    part(f"forearm_{side}", f"arm_{side}", (0, 11, 0), (0, 0, 0), "limb", [(-2.5, 0, -2.5, 5, 13, 5)])
    part(f"hand_{side}", f"forearm_{side}", (0, 12, 0), (0, 0, 0), "limb",
         [(-3, 0, -4.5, 6, 5, 7), (-2.5, 4.5, -4.5, 1, 3, 1), (-0.5, 4.5, -5, 1, 4, 1), (1.5, 4.5, -4.5, 1, 3, 1)])

# --- UV packing (shelf packer) ---
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

# --- textures ---
base = Image.new("RGBA", (TW, TH), (0, 0, 0, 0)); bp = base.load()
glow = Image.new("RGBA", (TW, TH), (0, 0, 0, 0)); gp = glow.load()
PAL = {
    "cloak": [(22, 19, 27), (30, 26, 36), (38, 33, 45), (17, 14, 21), (46, 40, 53), (27, 23, 31)],
    "limb": [(26, 24, 29), (34, 32, 37), (20, 18, 23), (44, 41, 47), (30, 27, 34)],
    "mask": [(64, 58, 62), (72, 66, 69), (56, 51, 55), (80, 73, 75)],
    "tendril": [(14, 12, 18), (20, 17, 25), (11, 9, 14), (26, 21, 32)],
}
def paint(x0, y0, w, h, mat, cracks=0.0, cell=2):
    pal = PAL[mat]
    for cy_ in range(0, h, cell):
        for cx_ in range(0, w, cell):
            c = random.choice(pal)
            for yy in range(cy_, min(h, cy_ + cell)):
                for xx in range(cx_, min(w, cx_ + cell)):
                    jitter = random.randint(-3, 3)
                    bp[x0 + xx, y0 + yy] = tuple(max(0, min(255, v + jitter)) for v in c) + (255,)
    # glowing cracks: short random walks
    n = int(w * h * cracks)
    for _ in range(n):
        x, y = random.randrange(w), random.randrange(h)
        for _s in range(random.randint(2, 5)):
            if 0 <= x < w and 0 <= y < h:
                gp[x0 + x, y0 + y] = (190, 100, 255, 255)
                bp[x0 + x, y0 + y] = (90, 40, 130, 255)
            x += random.choice((-1, 0, 1)); y += random.choice((0, 1))

for p in P:
    for bi, b in enumerate(p["boxes"]):
        (u, v), (W, H, D) = uv[(p["name"], bi)]
        mat = p["mat"]
        cr = {"cloak": 0.006, "limb": 0.01, "mask": 0.0, "tendril": 0.0}[mat]
        paint(u, v, 2 * (W + D), D + H, mat, cr, 1 if min(W, H, D) <= 2 else 2)
        if mat == "tendril":
            # purple tint creeping up from the tips
            for yy in range(H):
                k = yy / max(1, H)
                if k > 0.7 and random.random() < 0.35:
                    for xx in range(2 * (W + D)):
                        if random.random() < 0.5:
                            bp[u + xx, v + D + yy] = (60, 25, 90, 255)
        if p["name"] == "head":
            fx, fy = u + D, v + D  # front face, W x H = 12 x 12
            # hood shadow around the mask
            for yy in range(H):
                for xx in range(W):
                    if yy < 2 or xx < 1 or xx > W - 2:
                        bp[fx + xx, fy + yy] = random.choice(PAL["cloak"]) + (255,)
            # eye sockets and eyes
            for ex in (2, 8):
                for yy in range(4, 7):
                    for xx in range(ex - 1, ex + 3):
                        bp[fx + xx, fy + yy] = (28, 22, 30, 255)
                for yy in (5,):
                    for xx in (ex, ex + 1):
                        gp[fx + xx, fy + yy] = (235, 170, 255, 255)
                        bp[fx + xx, fy + yy] = (150, 70, 220, 255)
                for xx in (ex - 1, ex + 2):
                    gp[fx + xx, fy + 5] = (150, 60, 230, 200)
            # hollow mouth seam
            for xx in range(3, 9):
                bp[fx + xx, fy + 9] = (24, 20, 26, 255)

base.save("lurker.png"); glow.save("lurker_glow.png")
prev = Image.new("RGBA", (TW, TH), (60, 60, 60, 255)); prev.alpha_composite(base); prev.alpha_composite(glow)
prev.resize((TW * 4, TH * 4), Image.NEAREST).save("prev.png")

# --- Java ---
def f(v):
    s = ("%.3f" % v).rstrip("0").rstrip(".")
    if s in ("-0", ""): s = "0"
    return s + "F"
lines = []
lines.append("\t\tMeshDefinition mesh = new MeshDefinition();")
lines.append("\t\tPartDefinition parts = mesh.getRoot();")
var = {None: "parts"}
for p in P:
    v = "p_" + p["name"]
    var[p["name"]] = v
    cube = "CubeListBuilder.create()"
    for bi, b in enumerate(p["boxes"]):
        (u, vv), _ = uv[(p["name"], bi)]
        x, y, z, w, h, d = b
        cube += f".texOffs({u}, {vv}).addBox({f(x)}, {f(y)}, {f(z)}, {f(w)}, {f(h)}, {f(d)})"
        if p["name"].startswith("arm_r") or p["name"].startswith("forearm_r") or p["name"].startswith("hand_r") or p["name"].startswith("rag_r") or p["name"] == "leg_r":
            pass
    px, py, pz = p["pivot"]; rx, ry, rz = p["rot"]
    pose = f"PartPose.offsetAndRotation({f(px)}, {f(py)}, {f(pz)}, {f(rx)}, {f(ry)}, {f(rz)})"
    lines.append(f"\t\tPartDefinition {v} = {var[p['parent']]}.addOrReplaceChild(\"{p['name']}\", {cube}, {pose});")
lines.append(f"\t\treturn LayerDefinition.create(mesh, {TW}, {TH});")
open("geometry.java.txt", "w").write("\n".join(lines) + "\n")
print(len(P), "parts; texture rows used:", cy + rowh)
