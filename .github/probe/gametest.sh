#!/bin/bash
# TEMP: run a headless client game test that takes screenshots of the Seam
set -u
cd $GITHUB_WORKSPACE
sudo apt-get update -qq >/dev/null && sudo apt-get install -y -qq xvfb libgl1-mesa-dri mesa-vulkan-drivers libegl1 >/dev/null
mkdir -p src/gametest/java/com/extremedimensions/gametest src/gametest/resources
cp .github/probe/gametest/SeamShots.java src/gametest/java/com/extremedimensions/gametest/
cat > src/gametest/resources/fabric.mod.json <<'J'
{"schemaVersion":1,"id":"extremedimensions-gametest","version":"1","environment":"*",
 "entrypoints":{"fabric-client-gametest":["com.extremedimensions.gametest.SeamShots"]}}
J
cat >> build.gradle <<'G'

fabricApi {
	configureTests {
		createSourceSet = true
		modId = "extremedimensions-gametest"
		enableGameTests = false
		enableClientGameTests = true
		eula = true
	}
}
G
# the server-context API, for reference if the test fails to compile
J=$(find ~/.gradle -name "fabric-client-gametest-api-v1*.jar" | grep -v sources | head -1); echo "GTJAR $J"
[ -n "$J" ] && javap -cp "$J" net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext
export LIBGL_ALWAYS_SOFTWARE=1
timeout 1500 xvfb-run -a -s "-screen 0 1280x720x24" ./gradlew runClientGameTest --no-configuration-cache > gt.log 2>&1
echo "exit $?"
grep -iE "error|exception|mixin|seam|screenshot|extremedimensions|OpenGL|GL_|vulkan|renderer" gt.log | grep -v "^\s*at " | head -120
tail -40 gt.log
grep -E "RAY|ARCH" gt.log | sed 's/.*\(RAY\|ARCH\)/\1/' | tail -220
python3 -m pip install -q --break-system-packages pillow >/dev/null 2>&1 || pip install -q pillow
python3 - <<'P'
import glob, base64, io, os
from PIL import Image
files = sorted(set(glob.glob("**/screenshots/**/*.png", recursive=True)))
print("SHOTS", files)
for f in files:
    im = Image.open(f).convert("RGB")
    im.thumbnail((800, 450))
    b = io.BytesIO(); im.save(b, "JPEG", quality=72)
    s = base64.b64encode(b.getvalue()).decode()
    name = os.path.basename(f)
    for i in range(0, len(s), 3000):
        print(f"IMG|{name}|{i//3000}|{s[i:i+3000]}")
P
