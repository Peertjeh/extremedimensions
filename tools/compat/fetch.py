#!/usr/bin/env python3
"""Fetches the newest Fabric builds of Sodium, Iris, Mod Menu, Text Placeholder API, JEI, Jade, WTHIT, JourneyMap and
Xaero's Minimap for the game version in gradle.properties from Modrinth, into the client test's mods folder, for the
compatibility job in CI.

Usage: fetch.py <mods dir>
       fetch.py --shader <shaderpacks dir>
       fetch.py --check <slug>[,<slug>...]

Prints what it found, one line per mod; a mod with no build for this game version yet is left out (the job then tests
with the others). A mod that needs another mod beside it which neither it nor the others bring (WTHIT its packet
library) gets what Modrinth names as needed by that build, or is left out when that cannot be had, as the game would
not start without it. Of two mods Modrinth marks as not to be loaded together, the later in the list is left out. Where a
fabric.mod.json's "breaks" names the version of another that was fetched (Sodium 0.9.3 breaks Iris up to 1.12.0), the
mod that says so is taken at its newest build that does not, or else the other is left out. A mod that needs a newer Fabric API than the build's is left out too, as the game would not start. Exits 0 when at least one was found, 3 when none was (the job reports
that and skips its test).

With --shader it fetches a shader pack instead (3.51.1): the newest Solas, else Complementary Reimagined, for the test's
look at the Seam and the mod's lanterns under one. A shader pack is not built for one game version, so any will do.
Exits 0 with one, 3 with none.

With --check it only says, one line per mod, whether it has a Fabric build for this game version yet (for deciding
what the mod can work with); it downloads nothing and always exits 0."""

import io
import json
import os
import re
import sys
import urllib.parse
import urllib.request
import zipfile

# (Jade and WTHIT draw the same kind of overlay and players pick one, but neither's build says it cannot stand beside
# the other, so both are loaded, each with its own plugin to check)
# The mods every release is tested beside; and the ones a second run adds (COMPAT_SET=all), kept out of the release's
# own run since Fabric's client test harness sometimes deadlocks closing its world with them beside it (3.53.0)
CORE = ["sodium", "iris", "modmenu", "placeholder-api", "jei"]
EXTRA = ["jade", "wthit", "journeymap", "xaeros-minimap"]
MODS = CORE + EXTRA
SHADERS = ["solas-shader", "complementary-reimagined"]
# Fabric API's Modrinth project: the test runs with it already, so a mod's need of it fetches nothing
FABRIC_API = "P7dR8mSH"


def game_version():
    with open("gradle.properties", encoding="utf-8") as f:
        for line in f:
            m = re.match(r"\s*minecraft_version\s*=\s*(\S+)", line)
            if m:
                return m.group(1)
    raise SystemExit("no minecraft_version in gradle.properties")


def fabric_api_version():
    with open("gradle.properties", encoding="utf-8") as f:
        for line in f:
            m = re.match(r"\s*fabric_api_version\s*=\s*([0-9.]+)", line)
            if m:
                return m.group(1)
    return None


def numbers(v):
    return tuple(int(x) for x in v.strip(".").split(".") if x.isdigit())


def newest(slug, version, loader="fabric"):
    found = versions(slug, version, loader)
    return found[0] if found else None


def versions(slug, version, loader="fabric"):
    """Every build of a mod for this game version and loader: releases before betas before alphas, each newest first."""
    params = {"loaders": json.dumps([loader])}
    if version:
        params["game_versions"] = json.dumps([version])
    query = urllib.parse.urlencode(params)
    req = urllib.request.Request(f"https://api.modrinth.com/v2/project/{slug}/version?{query}",
                                 headers={"User-Agent": "extremedimensions-ci/compat"})
    with urllib.request.urlopen(req, timeout=30) as r:
        versions = json.load(r)
    # releases before betas before alphas, each newest first (the API lists newest first)
    order = {"release": 0, "beta": 1, "alpha": 2}
    versions.sort(key=lambda v: order.get(v.get("version_type"), 3))
    return versions


def download(f, out):
    req = urllib.request.Request(f["url"], headers={"User-Agent": "extremedimensions-ci/compat"})
    with urllib.request.urlopen(req, timeout=120) as r:
        data = r.read()
    with open(os.path.join(out, f["filename"]), "wb") as w:
        w.write(data)


def metadata(path):
    """A mod jar's fabric.mod.json ({} when it has none it can be read from)."""
    try:
        with zipfile.ZipFile(path) as z:
            return json.loads(z.read("fabric.mod.json")) if "fabric.mod.json" in z.namelist() else {}
    except (ValueError, zipfile.BadZipFile, KeyError):
        return {}


def mod_ids(path):
    """The mod ids a jar brings: its own, what it says it provides, and those of the jars nested in it."""
    ids = set()

    def read(z):
        if "fabric.mod.json" in z.namelist():
            try:
                fmj = json.loads(z.read("fabric.mod.json"))
            except ValueError:
                fmj = {}
            ids.add(fmj.get("id"))
            ids.update(fmj.get("provides", []))
        for n in z.namelist():
            if n.startswith("META-INF/jars/") and n.endswith(".jar"):
                with zipfile.ZipFile(io.BytesIO(z.read(n))) as inner:
                    read(inner)

    try:
        with zipfile.ZipFile(path) as z:
            read(z)
    except zipfile.BadZipFile:
        pass
    return ids


def base(v):
    """A version without its build or pre-release part: 1.11.7+mc26.3 is 1.11.7."""
    return re.split(r"[+-]", str(v), maxsplit=1)[0]


def matches(constraint, ver):
    """Whether a version meets a fabric.mod.json version predicate (a list: any of them; spaces: all of them)."""
    if isinstance(constraint, list):
        return any(matches(c, ver) for c in constraint)
    c = str(constraint).strip()
    if c in ("", "*"):
        return True
    have = numbers(base(ver))
    for part in c.split():
        m = re.match(r"(<=|>=|<|>|=|\^|~)?\s*(.+)", part)
        op, want = m.group(1) or "=", m.group(2)
        if want.endswith((".x", ".X", ".*")):
            pre = numbers(want[:-2])
            if have[:len(pre)] != pre:
                return False
            continue
        w = numbers(base(want))
        ok = {"<=": have <= w, "<": have < w, ">=": have >= w, ">": have > w, "=": have == w,
              "^": have >= w and have[:1] == w[:1], "~": have >= w and have[:2] == w[:2]}[op]
        if not ok:
            return False
    return True


def breaks(path, other):
    """Whether one mod jar's fabric.mod.json says it breaks the version of the other that is there."""
    them = metadata(other)
    said = metadata(path).get("breaks", {})
    return them.get("id") in said and matches(said[them.get("id")], them.get("version", ""))


def given(mod_id):
    """Whether the test brings this mod itself: the game, Java, Fabric Loader (and the MixinExtras in it), Fabric API."""
    return mod_id in ("minecraft", "java", "fabricloader", "mixinextras", "fabric", "fabric-api") or mod_id.startswith("fabric-")


def missing(path, have):
    """The mods a jar needs that neither the test, the jar itself nor the jars in have bring."""
    ids = set(mod_ids(path))
    for other in have:
        ids |= mod_ids(other)
    return sorted(m for m in metadata(path).get("depends", {}) if not given(m) and m not in ids)


def shader(out):
    os.makedirs(out, exist_ok=True)
    for slug in SHADERS:
        try:
            v = newest(slug, None, "iris")
            if v is None:
                print(f"SHADER|{slug}|none|no version for Iris")
                continue
            f = next((f for f in v["files"] if f.get("primary")), v["files"][0])
            download(f, out)
        except Exception as e:  # then the next one
            print(f"SHADER|{slug}|error|{e}")
            continue
        print(f"SHADER|{slug}|{v['version_number']}|{f['filename']}")
        sys.exit(0)
    sys.exit(3)


def check(slugs):
    version = game_version()
    for slug in slugs:
        try:
            v = newest(slug, version)
        except Exception as e:
            print(f"CHECK|{slug}|error|{e}")
            continue
        print(f"CHECK|{slug}|{v['version_number'] if v else 'none'}|{'has a Fabric build for ' + version if v else 'no Fabric build for ' + version + ' yet'}")
    sys.exit(0)


def main():
    if len(sys.argv) == 3 and sys.argv[1] == "--check":
        check([x for x in sys.argv[2].split(",") if x])
    if len(sys.argv) == 3 and sys.argv[1] == "--shader":
        shader(sys.argv[2])
    if len(sys.argv) != 2:
        raise SystemExit(__doc__)
    out = sys.argv[1]
    os.makedirs(out, exist_ok=True)
    version = game_version()
    # slug -> (Modrinth version, jar), in the order of MODS; and the jars of what they needed beside them
    got = {}
    extra = []
    projects = set()
    for slug in (MODS if os.environ.get("COMPAT_SET", "all") == "all" else CORE):
        if slug == "iris" and "sodium" not in got:
            # Iris needs Sodium under it
            print(f"COMPAT|{slug}|none|left out, as Sodium is")
            continue
        try:
            v = newest(slug, version)
        except Exception as e:  # the job goes on with the others
            print(f"COMPAT|{slug}|error|{e}")
            continue
        if v is None:
            print(f"COMPAT|{slug}|none|no Fabric build for {version} yet")
            continue
        if v.get("project_id") in projects:
            print(f"COMPAT|{slug}|{v['version_number']}|fetched already, as another mod needed it")
            continue
        f = next((f for f in v["files"] if f.get("primary")), v["files"][0])
        try:
            download(f, out)
        except Exception as e:
            print(f"COMPAT|{slug}|error|{e}")
            continue
        path = os.path.join(out, f["filename"])
        print(f"COMPAT|{slug}|{v['version_number']}|{f['filename']}")
        got[slug] = (v, path)
        projects.add(v.get("project_id"))
        if missing(path, [p for _, p in got.values()] + extra):
            # What Modrinth names as needed by this build, that the test does not bring and nothing fetched yet is
            for d in v.get("dependencies", []):
                pid = d.get("project_id")
                if d.get("dependency_type") != "required" or not pid or pid == FABRIC_API or pid in projects:
                    continue
                try:
                    dv = newest(pid, version)
                    if dv is None:
                        print(f"COMPAT|{slug}|needs|{pid}, which has no Fabric build for {version}")
                        continue
                    df = next((x for x in dv["files"] if x.get("primary")), dv["files"][0])
                    download(df, out)
                except Exception as e:
                    print(f"COMPAT|{slug}|needs|{pid}: {e}")
                    continue
                projects.add(pid)
                extra.append(os.path.join(out, df["filename"]))
                print(f"COMPAT|{slug}|needs|{df['filename']} ({dv['version_number']})")
            lacking = missing(path, [p for _, p in got.values()] + extra)
            if lacking:
                # (the game would not start with it)
                os.remove(path)
                del got[slug]
                print(f"COMPAT|{slug}|none|left out: it needs {', '.join(lacking)}, which could not be had")
    # A mod that needs a newer Fabric API than the one the build runs with stays out (only ">=x.y.z" is read)
    have = fabric_api_version()
    for slug in list(got):
        need = metadata(got[slug][1]).get("depends", {}).get("fabric-api")
        m = re.match(r"\s*>=\s*([0-9.]+)", need if isinstance(need, str) else "")
        if have and m and numbers(m.group(1)) > numbers(have):
            os.remove(got[slug][1])
            del got[slug]
            print(f"COMPAT|{slug}|none|left out: it needs Fabric API {m.group(1)} or newer, the build has {have}")
    # Of two mods Modrinth marks as not to be loaded together, the later one stays out
    order = list(got)
    for i, first in enumerate(order):
        for later in order[i + 1:]:
            if first not in got or later not in got:
                continue
            (v1, p1), (v2, p2) = got[first], got[later]
            id1, id2 = metadata(p1).get("id"), metadata(p2).get("id")
            clash = any(d.get("dependency_type") == "incompatible" and d.get("project_id") == other.get("project_id")
                        for this, other in ((v1, v2), (v2, v1)) for d in this.get("dependencies", []))
            if clash:
                os.remove(p2)
                del got[later]
                print(f"COMPAT|{later}|none|left out: it and {first} will not be loaded together")
    # A mod whose newest build says it breaks the build of another that is there (3.56: Sodium 0.9.3 breaks every Iris
    # up to 1.12.0, while 26.3 has Iris 1.11.7) is taken at its newest build that does not; with none, the other stays out
    for a in list(got):
        for b in list(got):
            if a == b or a not in got or b not in got:
                continue
            if not (breaks(got[a][1], got[b][1]) or breaks(got[b][1], got[a][1])):
                continue
            other = metadata(got[b][1])
            fixed = False
            try:
                older = versions(a, version)[1:9]
            except Exception as e:
                older = []
                print(f"COMPAT|{a}|error|{e}")
            for v in older:
                f = next((x for x in v["files"] if x.get("primary")), v["files"][0])
                path = os.path.join(out, f["filename"])
                if path == got[a][1]:
                    continue
                try:
                    download(f, out)
                except Exception as e:
                    print(f"COMPAT|{a}|error|{e}")
                    continue
                if breaks(path, got[b][1]) or breaks(got[b][1], path):
                    os.remove(path)
                    continue
                os.remove(got[a][1])
                got[a] = (v, path)
                print(f"COMPAT|{a}|{v['version_number']}|{f['filename']} (the newest will not load beside {b} "
                      f"{other.get('version')})")
                fixed = True
                break
            if not fixed:
                os.remove(got[b][1])
                del got[b]
                print(f"COMPAT|{b}|none|left out: {a} will not load beside it, at any of its recent builds")
    sys.exit(0 if got else 3)


if __name__ == "__main__":
    main()
