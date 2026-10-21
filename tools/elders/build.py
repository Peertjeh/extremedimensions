"""Builds the Elders' geometry (Java) and textures. usage: python3 tools/elders/build.py [preview-json-dir]"""
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
import elderlib  # noqa: E402
import matriarch  # noqa: E402
import ferryman  # noqa: E402
import weaver  # noqa: E402

ROOT = os.path.join(os.path.dirname(__file__), "..", "..")
JAVA = os.path.join(ROOT, "src/client/java/com/extremedimensions/client/entity/elder")
TEX = os.path.join(ROOT, "src/client/resources/assets/extremedimensions/textures/entity/elder")
os.makedirs(JAVA, exist_ok=True)
os.makedirs(TEX, exist_ok=True)
preview = sys.argv[1] if len(sys.argv) > 1 else None
for mod, name in ((matriarch, "matriarch"), (ferryman, "ferryman"), (weaver, "weaver")):
    m = mod.build()
    uv, used = elderlib.pack(m)
    img, glow = elderlib.paint(m, uv)
    img.save(os.path.join(TEX, name + ".png"))
    glow.save(os.path.join(TEX, name + "_glow.png"))
    elderlib.write_java(m, uv, "com.extremedimensions.client.entity.elder", os.path.join(JAVA, m.cls + "Geometry.java"))
    if preview:
        elderlib.export_preview(m, uv, os.path.join(preview, name + ".json"))
    boxes = sum(len(p["boxes"]) for p in m.parts)
    print(f"{name}: {len(m.parts)} parts, {boxes} boxes, texture rows used {used}/{m.th}")
