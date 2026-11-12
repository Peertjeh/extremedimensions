#!/usr/bin/env python3
"""The canary (CI, on a release and on a commit marked [canary]): builds the mod against the newest Minecraft version
Fabric knows of, snapshots included, to see early what a new version breaks, rather than on the day it comes out.

Usage: canary.py

Reads the game version from gradle.properties; when Fabric knows a newer one and Fabric API has a build for it, writes
that version, that Fabric API and the newest loader into gradle.properties (in the job's checkout only) and compiles
the mod's code (main, client and the tests). The outcome goes to the job's summary page: up to date, nothing to build
against yet, compiles, or the first errors. It never fails the job: it tells, it does not block."""

import json
import os
import re
import subprocess
import sys
import urllib.parse
import urllib.request

UA = {"User-Agent": "extremedimensions-ci/canary"}


def get(url):
    with urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=30) as r:
        return json.load(r)


def summary(text):
    print(text)
    path = os.environ.get("GITHUB_STEP_SUMMARY")
    if path:
        with open(path, "a", encoding="utf-8") as f:
            f.write(text + "\n")


def prop(text, key):
    m = re.search(r"^\s*" + re.escape(key) + r"\s*=\s*(\S+)", text, re.M)
    return m.group(1) if m else None


def main():
    props = open("gradle.properties", encoding="utf-8").read()
    current = prop(props, "minecraft_version")
    try:
        games = get("https://meta.fabricmc.net/v2/versions/game")
        loaders = get("https://meta.fabricmc.net/v2/versions/loader")
    except Exception as e:
        summary(f"## Canary\n\nFabric's version list could not be read ({e}): nothing checked.")
        return
    newest = games[0]["version"] if games else current
    summary(f"## Canary\n\nBuilt for **{current}**; the newest game version Fabric knows is **{newest}**"
            f" ({'a snapshot' if games and not games[0].get('stable') else 'a release'}).")
    if newest == current:
        summary("\nUp to date: nothing to try.")
        return
    query = urllib.parse.urlencode({"loaders": json.dumps(["fabric"]), "game_versions": json.dumps([newest])})
    try:
        api = get(f"https://api.modrinth.com/v2/project/fabric-api/version?{query}")
    except Exception as e:
        summary(f"\nFabric API's builds could not be read ({e}): nothing tried.")
        return
    if not api:
        summary(f"\nFabric API has no build for {newest} yet: nothing to build against.")
        return
    fapi = api[0]["version_number"]
    loader = loaders[0]["version"] if loaders else prop(props, "loader_version")
    props = re.sub(r"^(\s*minecraft_version\s*=\s*)\S+", lambda m: m.group(1) + newest, props, flags=re.M)
    props = re.sub(r"^(\s*fabric_api_version\s*=\s*)\S+", lambda m: m.group(1) + fapi, props, flags=re.M)
    props = re.sub(r"^(\s*loader_version\s*=\s*)\S+", lambda m: m.group(1) + loader, props, flags=re.M)
    with open("gradle.properties", "w", encoding="utf-8") as f:
        f.write(props)
    summary(f"\nTrying {newest} with Fabric API {fapi} and loader {loader}.")
    run = subprocess.run(["./gradlew", "compileJava", "compileClientJava", "compileGametestJava", "--no-configuration-cache",
                          "--console=plain"], capture_output=True, text=True)
    out = run.stdout + run.stderr
    with open("canary.log", "w", encoding="utf-8") as f:
        f.write(out)
    if run.returncode == 0:
        summary(f"\n**The mod compiles against {newest}.** (Compiling is not playing: the tests did not run.)")
        return
    errors = [l for l in out.splitlines() if re.search(r"error:|FAILURE|Could not resolve|What went wrong", l)]
    files = sorted({m.group(1) for l in errors for m in [re.search(r"/src/\w+/java/(\S+?\.java)", l)] if m})
    summary(f"\n**It does not compile against {newest}**: {sum('error:' in l for l in errors)} errors"
            f" in {len(files)} files.\n\n```\n" + "\n".join(errors[:40]) + "\n```")
    if files:
        summary("\nFiles: " + ", ".join(files[:40]))


if __name__ == "__main__":
    try:
        main()
    except Exception as e:  # it tells, it never blocks
        summary(f"\nThe canary itself failed: {e}")
    sys.exit(0)
