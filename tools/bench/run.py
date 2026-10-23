#!/usr/bin/env python3
"""The benchmark: a real Fabric dedicated server, run through the same play with and without Extreme Dimensions.

Usage: run.py <results dir> [rounds]

Fetches the Fabric server launcher and Fabric API for the versions in gradle.properties, then runs the server in turn
without the mod and with it (Fabric API and the benchmark mod both times), `rounds` times each (2 by default), every run
on a fresh world from the same seed. The benchmark mod (src/bench, built by `./gradlew benchJar`) takes each run through
its phases and writes what it measured; this script sets the runs side by side.

Writes into the results dir: every run's measurements (<round>-<without|with>.json) and server log, results.json (the
mean of the rounds) and summary.md (the table), which it also prints, and adds to the job's summary page in GitHub
Actions. Exits 1 if a run wrote no measurements."""

import glob
import json
import os
import re
import shutil
import subprocess
import sys
import time
import urllib.request

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
WORK = os.path.join(ROOT, "build", "bench")
SERVER = os.path.join(WORK, "server")
SEED = "3141592653"
HEAP = "3G"
TIMEOUT = 900

PHASES = {
    "idle": "the server alone",
    "players": "players standing about",
    "exploring": "players sprinting outwards over new land",
    "seam": "players in the Seam (only with the mod)",
}


def props():
    out = {}
    with open(os.path.join(ROOT, "gradle.properties"), encoding="utf-8") as f:
        for line in f:
            m = re.match(r"\s*([\w.]+)\s*=\s*(\S+)", line)
            if m:
                out[m.group(1)] = m.group(2)
    return out


def fetch(url, path):
    """Downloads url to path, trying again on a hiccup (2, 4, 8, 16 seconds apart)."""
    for attempt in range(5):
        try:
            req = urllib.request.Request(url, headers={"User-Agent": "extremedimensions-ci/bench"})
            with urllib.request.urlopen(req, timeout=60) as r, open(path + ".part", "wb") as f:
                shutil.copyfileobj(r, f)
            os.replace(path + ".part", path)
            return
        except Exception as e:
            if attempt == 4:
                raise SystemExit(f"could not fetch {url}: {e}")
            print(f"BENCH|fetch|{url}|{e}; again in {2 ** (attempt + 1)} s", flush=True)
            time.sleep(2 ** (attempt + 1))


def fetch_json(url):
    path = os.path.join(WORK, "meta.json")
    fetch(url, path)
    with open(path, encoding="utf-8") as f:
        return json.load(f)


def prepare(p):
    os.makedirs(SERVER, exist_ok=True)
    mc, loader, api = p["minecraft_version"], p["loader_version"], p["fabric_api_version"]
    launcher = os.path.join(SERVER, "fabric-server-launch.jar")
    if not os.path.exists(launcher):
        installer = next(v["version"] for v in fetch_json("https://meta.fabricmc.net/v2/versions/installer") if v.get("stable"))
        fetch(f"https://meta.fabricmc.net/v2/versions/loader/{mc}/{loader}/{installer}/server/jar", launcher)
    fabric_api = os.path.join(WORK, f"fabric-api-{api}.jar")
    if not os.path.exists(fabric_api):
        fetch(f"https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/{api}/fabric-api-{api}.jar", fabric_api)
    with open(os.path.join(SERVER, "eula.txt"), "w", encoding="utf-8") as f:
        f.write("eula=true\n")
    # The game's defaults, but for the seed (the same world every run), no login (the players are the benchmark's), and
    # no watchdog (a slow tick on a busy runner must not stop the server)
    with open(os.path.join(SERVER, "server.properties"), "w", encoding="utf-8") as f:
        f.write(f"level-seed={SEED}\nonline-mode=false\nmax-tick-time=-1\nview-distance=10\nsimulation-distance=10\n"
                "spawn-protection=0\nmotd=Extreme Dimensions benchmark\n")
    return fabric_api


def run(name, mods, out, warmup=False):
    """One run of the server on a fresh world; its measurements, or None."""
    for d in ("world", "logs", "mods"):
        shutil.rmtree(os.path.join(SERVER, d), ignore_errors=True)
    os.makedirs(os.path.join(SERVER, "mods"))
    for m in mods:
        shutil.copy(m, os.path.join(SERVER, "mods"))
    result = os.path.join(out, f"{name}.json")
    if os.path.exists(result):
        os.remove(result)
    cmd = ["java", f"-Xms{HEAP}", f"-Xmx{HEAP}", f"-Dextremedimensions.bench.out={result}"]
    if warmup:
        cmd.append("-Dextremedimensions.bench.warmup=true")
    cmd += ["-jar", "fabric-server-launch.jar", "nogui"]
    print(f"BENCH|run|{name}|{', '.join(os.path.basename(m) for m in mods)}", flush=True)
    start = time.time()
    with open(os.path.join(out, f"{name}.log"), "w", encoding="utf-8") as log:
        proc = subprocess.Popen(cmd, cwd=SERVER, stdin=subprocess.DEVNULL, stdout=log, stderr=subprocess.STDOUT)
        try:
            proc.wait(timeout=TIMEOUT)
        except subprocess.TimeoutExpired:
            proc.kill()
            proc.wait()
            print(f"BENCH|run|{name}|stopped after {TIMEOUT} s", flush=True)
    print(f"BENCH|run|{name}|done in {time.time() - start:.0f} s, exit code {proc.returncode}", flush=True)
    if not os.path.exists(result):
        tail = open(os.path.join(out, f"{name}.log"), encoding="utf-8", errors="replace").read().splitlines()[-40:]
        print("\n".join(f"BENCH|log|{name}|{line}" for line in tail), flush=True)
        return None
    with open(result, encoding="utf-8") as f:
        return json.load(f)


def mean(runs):
    """The rounds of one setup averaged: every number the mean of its rounds (the slowest ticks of all of them kept)."""
    def avg(values):
        nums = [v for v in values if isinstance(v, (int, float)) and not isinstance(v, bool)]
        if not nums or all(v == nums[0] for v in nums):
            return values[0]
        return round(sum(nums) / len(nums), 3)
    out = {k: avg([r[k] for r in runs]) for k in runs[0] if k != "phases"}
    out["phases"] = []
    for i, ph in enumerate(runs[0]["phases"]):
        merged = {k: avg([r["phases"][i][k] for r in runs]) for k in ph if k != "slowest"}
        merged["slowest"] = sorted((t for r in runs for t in r["phases"][i].get("slowest", [])), key=lambda t: -t["ms"])[:4]
        out["phases"].append(merged)
    return out


def report(name, got):
    """One run's figures, a line a phase, into the job's log."""
    print(f"BENCH|round|{name}|start-up {got['startup_ms'] / 1000:.1f} s, memory {got['heap_after_start_mb']:.0f} MB", flush=True)
    for ph in got["phases"]:
        print(f"BENCH|round|{name}|{ph['name']}|MSPT {ph['mspt_avg']:.2f} mean, {ph['mspt_p95']:.2f} p95, {ph['mspt_max']:.1f} worst; "
              f"server thread {ph['server_thread_busy'] * 100:.1f}% busy; CPU {ph['process_cpu_s']} s; {ph['entities']} entities, "
              f"{ph['loaded_chunks']} chunks, {ph['heap_after_mb']:.0f} MB; players in {ph.get('players_in')}", flush=True)
        for t in ph.get("slowest", []):
            print(f"BENCH|slow|{name}|{ph['name']}|{t['ms']:.0f} ms|mod: {t['mod_frame']}|{t['at']}", flush=True)


def warnings(path):
    """The warnings and errors in a server log, without their times (so two runs' can be compared)."""
    found = []
    with open(path, encoding="utf-8", errors="replace") as f:
        for line in f:
            m = re.match(r"\[[\d:]+\] \[([^/\]]+)/(WARN|ERROR)\]:? ?(.*)", line.rstrip())
            if m:
                found.append(f"{m.group(2)} [{m.group(1)}] {m.group(3)}")
    return found


def cell(a, b, unit, digits=1, pct=True):
    if b is None:
        return "–"
    if a is None:
        # (a phase only the mod has: nothing to set it against)
        return f"{b:.{digits}f}{unit if unit in ('', '%') else ' ' + unit}"
    d = b - a
    unit = unit if unit in ("", "%") else " " + unit
    text = f"{a:.{digits}f} → {b:.{digits}f}{unit} ({'+' if d >= 0 else '−'}{abs(d):.{digits}f}"
    if pct and a > 0:
        text += f", {'+' if d >= 0 else '−'}{abs(d) / a * 100:.0f}%"
    return text + ")"


def summary(without, with_, rounds, version, notes):
    lines = [f"## Extreme Dimensions {version}: a dedicated server without the mod → with it", ""]
    lines.append(f"The same play on a fresh world from one seed, {rounds} round(s) each way, without and with the mod in turn "
                 f"(Fabric API both times); {with_['players']} players, view distance 10, {HEAP} of memory. Each figure is "
                 "the mean of the rounds. MSPT is the server tick by the clock (50 ms is the most a tick may take at 20 TPS); "
                 "the server thread's share counts its work between ticks too; CPU, all threads takes in world generation.")
    lines.append("")
    lines.append(f"- **Start-up** (Java started → server ready): {cell(without['startup_ms'] / 1000, with_['startup_ms'] / 1000, 's')}")
    lines.append(f"- **Memory in use after start-up** (after garbage collection): {cell(without['heap_after_start_mb'], with_['heap_after_start_mb'], 'MB', 0)}")
    lines.append("")
    lines.append("| Phase | MSPT, mean | MSPT, 95% of ticks within | MSPT, worst | server thread busy | CPU, all threads | memory in use after | entities |")
    lines.append("|---|---|---|---|---|---|---|---|")
    vanilla = {ph["name"]: ph for ph in without["phases"]}
    for b in with_["phases"]:
        a = vanilla.get(b["name"], {})
        g = lambda ph, k, f=1: None if ph.get(k) is None else ph[k] * f
        name = f"**{b['name']}**: {PHASES.get(b['name'], '')}, {b['ticks'] // 20} s"
        busy = cell(g(a, "server_thread_busy", 100), g(b, "server_thread_busy", 100), "%", 1, False)
        lines.append(f"| {name} | {cell(g(a, 'mspt_avg'), b['mspt_avg'], 'ms', 2)} | {cell(g(a, 'mspt_p95'), b['mspt_p95'], 'ms', 2)} "
                     f"| {cell(g(a, 'mspt_max'), b['mspt_max'], 'ms', 1)} | {busy} | {cell(g(a, 'process_cpu_s'), b['process_cpu_s'], 's', 1)} "
                     f"| {cell(g(a, 'heap_after_mb'), b['heap_after_mb'], 'MB', 0)} | {cell(g(a, 'entities'), b['entities'], '', 0)} |")
    lines.append("")
    slow = [(ph["name"], t) for ph in with_["phases"] for t in ph.get("slowest", []) if t.get("mod_frame")]
    if slow:
        lines.append("Slow ticks (over 100 ms) in which the mod's code was running, and where:")
        lines.append("")
        for phase, t in sorted(slow, key=lambda x: -x[1]["ms"])[:5]:
            wait = ", **waiting for a chunk to load**" if "ServerChunkCache.getChunk" in t["at"] else ""
            lines.append(f"- {phase}: {t['ms']:.0f} ms, in `{t['mod_frame']}`{wait}")
        lines.append("")
    else:
        lines.append("No slow tick (over 100 ms) found the mod's code running.")
        lines.append("")
    lines.extend(notes)
    lines.append("A shared CI runner is a noisy place to measure: compare the rounds in results.json before reading much into a "
                 "few percent. The fights only the mod has (an Elder called, its arena rising) are measured by the game "
                 "tests' PERF lines, not here.")
    return "\n".join(lines) + "\n"


def main():
    if len(sys.argv) not in (2, 3):
        raise SystemExit(__doc__)
    out = os.path.abspath(sys.argv[1])
    rounds = int(sys.argv[2]) if len(sys.argv) == 3 else 2
    os.makedirs(out, exist_ok=True)
    p = props()
    mod = [j for j in glob.glob(os.path.join(ROOT, "build", "libs", "extremedimensions-*.jar")) if "sources" not in j]
    bench = os.path.join(WORK, "extremedimensions-bench.jar")
    if not mod or not os.path.exists(bench):
        raise SystemExit("build the mod and the benchmark first: ./gradlew assemble benchJar")
    fabric_api = prepare(p)
    # A first start fetches the game and its libraries and warms the disk; it is not counted
    if run("warmup", [fabric_api, bench], out, warmup=True) is None:
        raise SystemExit("the server did not start")
    runs = {"without": [], "with": []}
    for r in range(1, rounds + 1):
        for setup in ("without", "with"):
            mods = [fabric_api, bench] + (mod[:1] if setup == "with" else [])
            got = run(f"{r}-{setup}", mods, out)
            if got is None:
                raise SystemExit(f"round {r} {setup} the mod wrote no measurements (its log is in {out})")
            if got["mod"] != (setup == "with"):
                raise SystemExit(f"round {r} {setup} the mod: the server had it {'loaded' if got['mod'] else 'missing'}")
            runs[setup].append(got)
            report(f"{r}-{setup}", got)
    without, with_ = mean(runs["without"]), mean(runs["with"])
    # Warnings and errors only the runs with the mod logged (the same message in a run without it is the game's own)
    theirs = set(w for r in range(1, rounds + 1) for w in warnings(os.path.join(out, f"{r}-without.log")))
    ours = []
    for r in range(1, rounds + 1):
        for w in warnings(os.path.join(out, f"{r}-with.log")):
            if w not in theirs and w not in ours:
                ours.append(w)
    for w in ours[:30]:
        print(f"BENCH|warning|with the mod|{w}", flush=True)
    notes = [f"Warnings and errors in the server log that only the runs with the mod had: {len(ours)}"
             + (" (listed in the job's log, BENCH|warning lines)." if ours else "."), ""]
    seam = next((ph for ph in with_["phases"] if ph["name"] == "seam"), None)
    if seam is None or not any("seam" in k for k in (seam.get("players_in") or {})):
        notes.append(f"**The players did not reach the Seam** (they were in {seam.get('players_in') if seam else 'no Seam phase'}): "
                     "its row measures something else.")
        notes.append("")
    with open(os.path.join(out, "results.json"), "w", encoding="utf-8") as f:
        json.dump({"without": without, "with": with_, "rounds": runs}, f, indent=2)
    text = summary(without, with_, rounds, with_.get("mod_version") or p.get("version"), notes)
    with open(os.path.join(out, "summary.md"), "w", encoding="utf-8") as f:
        f.write(text)
    for line in text.splitlines():
        print(f"BENCH|{line}")
    if os.environ.get("GITHUB_STEP_SUMMARY"):
        with open(os.environ["GITHUB_STEP_SUMMARY"], "a", encoding="utf-8") as f:
            f.write(text)


if __name__ == "__main__":
    main()
