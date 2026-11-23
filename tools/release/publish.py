#!/usr/bin/env python3
"""Publish a released jar to Modrinth and CurseForge (3.56).

The release job runs this right after the GitHub release is made; the publish workflow runs it by hand for a release
that is already out. Each store is skipped, with a notice, while its secrets are not set:

  MODRINTH_TOKEN      a Modrinth personal access token with the "Create versions" and "Write projects" scopes
  MODRINTH_PROJECT    the project's id or slug on Modrinth
  CURSEFORGE_TOKEN    a CurseForge API token (authors.curseforge.com, My API Tokens)
  CURSEFORGE_PROJECT  the project's numeric id on CurseForge

Modrinth is safe to run twice: a version number already there is left alone. Its project page text is also brought in
line with docs/modrinth/description.md (--body). CurseForge has no way to ask what is already up, so it is only called
for a version that was just released, or when asked by hand.

Only the standard library: it runs on a bare runner.
"""
import argparse
import json
import os
import sys
import urllib.error
import urllib.request
import uuid

UA = "extremedimensions-release-ci/1.0 (+https://github.com/Peertjeh/extremedimensions)"
MODRINTH = "https://api.modrinth.com/v2"
CURSEFORGE = "https://minecraft.curseforge.com/api"
FABRIC_API_MODRINTH = "P7dR8mSH"


def request(method, url, headers=None, data=None, content_type=None):
    h = {"User-Agent": UA}
    h.update(headers or {})
    if content_type:
        h["Content-Type"] = content_type
    req = urllib.request.Request(url, data=data, headers=h, method=method)
    try:
        with urllib.request.urlopen(req, timeout=120) as r:
            body = r.read()
            return r.status, (json.loads(body) if body else None)
    except urllib.error.HTTPError as e:
        text = e.read().decode("utf-8", "replace")
        raise RuntimeError(f"{method} {url} -> HTTP {e.code}: {text[:800]}") from None


def multipart(fields, files):
    """fields: name -> str; files: name -> (filename, bytes, type). Returns (body, content type)."""
    boundary = "----ed" + uuid.uuid4().hex
    out = []
    for name, value in fields.items():
        out.append(f"--{boundary}\r\nContent-Disposition: form-data; name=\"{name}\"\r\n"
                   f"Content-Type: application/json\r\n\r\n".encode())
        out.append(value.encode("utf-8"))
        out.append(b"\r\n")
    for name, (filename, data, ctype) in files.items():
        out.append(f"--{boundary}\r\nContent-Disposition: form-data; name=\"{name}\"; filename=\"{filename}\"\r\n"
                   f"Content-Type: {ctype}\r\n\r\n".encode())
        out.append(data)
        out.append(b"\r\n")
    out.append(f"--{boundary}--\r\n".encode())
    return b"".join(out), f"multipart/form-data; boundary={boundary}"


def modrinth(args, notes, jar_bytes, jar_name):
    token = os.environ.get("MODRINTH_TOKEN", "").strip()
    project = os.environ.get("MODRINTH_PROJECT", "").strip()
    if not token or not project:
        print("::notice::Modrinth skipped: set the MODRINTH_TOKEN and MODRINTH_PROJECT secrets to publish there")
        return True
    auth = {"Authorization": token}
    _, info = request("GET", f"{MODRINTH}/project/{project}", auth)
    pid = info["id"]
    _, versions = request("GET", f"{MODRINTH}/project/{pid}/version", auth)
    if any(v.get("version_number") == args.version for v in versions or []):
        print(f"Modrinth: {args.version} is already up")
    else:
        data = {
            "name": args.title or f"Extreme Dimensions {args.version}",
            "version_number": args.version,
            "changelog": notes,
            "dependencies": [{"project_id": FABRIC_API_MODRINTH, "dependency_type": "required"}],
            "game_versions": [args.mc],
            "version_type": args.channel,
            "loaders": ["fabric"],
            "featured": True,
            "project_id": pid,
            "file_parts": ["file"],
            "primary_file": "file",
        }
        body, ctype = multipart({"data": json.dumps(data)}, {"file": (jar_name, jar_bytes, "application/java-archive")})
        _, made = request("POST", f"{MODRINTH}/version", auth, body, ctype)
        print(f"Modrinth: published {args.version} (version {made.get('id')})")
    if args.body and os.path.isfile(args.body):
        text = open(args.body, encoding="utf-8").read()
        if text.strip() and text != info.get("body"):
            request("PATCH", f"{MODRINTH}/project/{pid}", auth, json.dumps({"body": text}).encode(), "application/json")
            print("Modrinth: project page brought in line with " + args.body)
        else:
            print("Modrinth: project page already matches " + args.body)
    return True


def curseforge(args, notes, jar_bytes, jar_name):
    token = os.environ.get("CURSEFORGE_TOKEN", "").strip()
    project = os.environ.get("CURSEFORGE_PROJECT", "").strip()
    if not token or not project:
        print("::notice::CurseForge skipped: set the CURSEFORGE_TOKEN and CURSEFORGE_PROJECT secrets to publish there")
        return True
    auth = {"X-Api-Token": token}
    _, types = request("GET", f"{CURSEFORGE}/game/version-types", auth)
    _, versions = request("GET", f"{CURSEFORGE}/game/versions", auth)
    by_type = {}
    for t in types:
        by_type.setdefault(t["slug"].split("-")[0], set()).add(t["id"])

    def find(kind, name):
        ids = by_type.get(kind, set())
        return [v["id"] for v in versions if v["gameVersionTypeID"] in ids and v["name"].lower() == name.lower()]

    mc = find("minecraft", args.mc)
    if not mc:
        print(f"::error::CurseForge does not list Minecraft {args.mc} yet; upload this version there by hand")
        return False
    game = mc + find("modloader", "Fabric") + find("java", "Java 25")
    game += find("environment", "Client") + find("environment", "Server")
    meta = {
        "changelog": notes,
        "changelogType": "markdown",
        "displayName": args.title or f"Extreme Dimensions {args.version}",
        "gameVersions": game,
        "releaseType": args.channel,
        "relations": {"projects": [{"slug": "fabric-api", "type": "requiredDependency"}]},
    }
    body, ctype = multipart({"metadata": json.dumps(meta)}, {"file": (jar_name, jar_bytes, "application/java-archive")})
    _, made = request("POST", f"{CURSEFORGE}/projects/{project}/upload-file", auth, body, ctype)
    print(f"CurseForge: published {args.version} (file {made.get('id') if isinstance(made, dict) else made})")
    return True


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--version", required=True)
    p.add_argument("--mc", required=True)
    p.add_argument("--jar", required=True)
    p.add_argument("--notes", required=True, help="a Markdown file: the release notes")
    p.add_argument("--title", default="")
    p.add_argument("--channel", default="release", choices=["release", "beta", "alpha"])
    p.add_argument("--body", default="", help="the Modrinth project page text to keep in line")
    p.add_argument("--only", default="", choices=["", "modrinth", "curseforge"])
    args = p.parse_args()
    notes = open(args.notes, encoding="utf-8").read()
    jar_bytes = open(args.jar, "rb").read()
    jar_name = os.path.basename(args.jar)
    ok = True
    for name, fn in (("modrinth", modrinth), ("curseforge", curseforge)):
        if args.only and args.only != name:
            continue
        try:
            ok = fn(args, notes, jar_bytes, jar_name) and ok
        except Exception as e:  # one store down must not keep the other from its version
            print(f"::error::{name}: {e}")
            ok = False
    sys.exit(0 if ok else 1)


if __name__ == "__main__":
    main()
