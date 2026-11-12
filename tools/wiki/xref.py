#!/usr/bin/env python3
"""JEI-style cross-references for the Extreme Dimensions wiki page.

Reads the mod's own data from this repository (the recipes, the loot tables, the item tags, the Hollowlings' wares in
BarterService, and the few drops the code hands out besides a table: the Elders' threads and elegy fragments, relics,
the music discs, Tether Fragments from monsters beyond the wound) and the item and block textures, and patches the
single-page wiki (its data and its renderer) so that:

- every item, block and relic page shows how to get it (each recipe drawn as a recipe viewer draws it: a 3x3 grid, a
  row, template + base + addition, input -> output; and where it is found, dropped, given or sold) and what it is used
  in, with item icons and links between the pages;
- creature pages show what they drop (the Hollowling's also what it sells and takes), block pages what they drop when
  mined, and the pages of places (the Seam, the Fray, the hoards...) what their chests hold; enchantment pages where
  their books and gear lie;
- the search finds a page by its ingredients and sources, and the "ask" box gets a short text form of them.

The old generated facts these sections replace ("Crafted from", "Found in", "Drops when mined", a creature's "Drops")
are removed; hand-written facts stay.

    python3 tools/wiki/xref.py <wiki.html> [out.html] [--vanilla-items DIR] [--vanilla-blocks DIR]

Without out.html the page is patched in place. --vanilla-items / --vanilla-blocks are directories of extracted vanilla
textures (assets/minecraft/textures/item and .../block); with them the vanilla items in recipes and sources get their
icons too (only those used are embedded); without them they show as text chips. Nothing is copied into the repository.
Running the script on its own output gives the same page: it takes out what it added before adding it again.
"""

import argparse
import base64
import io
import json
import math
import re
import sys
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
NS = "extremedimensions"
DATA = ROOT / "src/main/resources/data"
ASSETS = ROOT / "src/client/resources/assets" / NS
JAVA = ROOT / "src/main/java/com/extremedimensions"
VIEWER = ROOT / "src/client/java/com/extremedimensions/client/compat/ViewerContent.java"

DATA_MARK = "const WIKI = "
JS_BEGIN, JS_END = "// xref:begin", "// xref:end"
JS_ANCHOR = "render();\nfromHash();"
STYLE_RE = re.compile(r'<style id="xref-(?:style|icons)">.*?</style>\n', re.S)
JS_RE = re.compile(re.escape(JS_BEGIN) + r".*?" + re.escape(JS_END) + r"\n", re.S)

# The renderer's one textual patch: the "ask" context gets each page's text form of its recipes and sources.
CONTEXT_OLD = '(e.facts.length ? "\\n" + e.facts.map(([a, b]) => `${a}: ${b}`).join("\\n") : "")).join("\\n\\n")'
CONTEXT_NEW = ('(e.facts.length ? "\\n" + e.facts.map(([a, b]) => `${a}: ${b}`).join("\\n") : "") + '
               '(e.xt ? "\\n" + e.xt : "") /* xref */).join("\\n\\n")')

KIND_ORDER = ["found", "drop", "given", "mined", "world", "sold", "other"]


def die(msg):
    sys.exit("xref: " + msg)


def load(p):
    with open(p, encoding="utf-8") as f:
        return json.load(f)


def short(i):
    return i.split(":", 1)[1] if ":" in i else i


# ------------------------------------------------------------------------------------------------ the mod's Java
def java(path):
    return path.read_text(encoding="utf-8")


def config():
    """The defaults in Config.java (public static int/double NAME = value;)."""
    out = {}
    for t, name, v in re.findall(r"public static (int|double) ([A-Z0-9_]+) = (-?[0-9.]+);", java(JAVA / "config/Config.java")):
        out[name] = int(v) if t == "int" else float(v)
    return out


def pct(p):
    if p >= 0.995:
        return ""
    v = p * 100
    if v >= 9.95:
        return f"{round(v)}%"
    if v >= 0.995:
        s = f"{v:.1f}".rstrip("0").rstrip(".")
        return s + "%"
    return f"{v:.2g}%"


def count_text(lo, hi):
    if lo == hi:
        return "" if lo == 1 else str(lo)
    return f"{lo}–{hi}"


# ------------------------------------------------------------------------------------------------ names
VANILLA_NAMES = {
    "experience_bottle": "Bottle o' Enchanting", "ender_eye": "Eye of Ender", "music_disc_13": "Music Disc (13)",
    "enchanted_book": "Enchanted Book", "netherite_upgrade_smithing_template": "Netherite Upgrade",
}
SMALL = {"of", "the", "o'", "and", "a"}


def title_case(path):
    w = path.split("/")[-1].split("_")
    return " ".join(x if (k and x in SMALL) else x[:1].upper() + x[1:] for k, x in enumerate(w))


def roman(n):
    return {1: "", 2: " II", 3: " III", 4: " IV", 5: " V"}.get(n, f" {n}")


# ------------------------------------------------------------------------------------------------ the wiki page
class Page:
    def __init__(self, path):
        self.src = Path(path).read_text(encoding="utf-8")
        i = self.src.find(DATA_MARK)
        if i < 0:
            die("no 'const WIKI = ' in the page")
        self.start = i + len(DATA_MARK)
        self.wiki, n = json.JSONDecoder().raw_decode(self.src[self.start:])
        self.end = self.start + n
        self.by_id = {e["id"]: e for e in self.wiki["entries"]}

    def clean(self):
        """Takes out the fields an earlier run added (parts() takes out its style, script and context patch)."""
        self.wiki.pop("xref", None)
        for e in self.wiki["entries"]:
            e.pop("xr", None)
            e.pop("xt", None)

    def parts(self):
        head, tail = self.src[:self.start], self.src[self.end:]
        head = STYLE_RE.sub("", head)
        tail = JS_RE.sub("", tail).replace(CONTEXT_NEW, CONTEXT_OLD)
        return head, json.dumps(self.wiki, ensure_ascii=False, separators=(",", ":")), tail


# ------------------------------------------------------------------------------------------------ icons
class Icons:
    """Item icons as 16x16 (or 32x32) PNGs, by item id; vanilla only from the directories given."""

    def __init__(self, vanilla_items, vanilla_blocks):
        self.vi = Path(vanilla_items) if vanilla_items else None
        self.vb = Path(vanilla_blocks) if vanilla_blocks else None
        self.cache = {}
        self.missing = []

    @staticmethod
    def first_frame(im):
        im = im.convert("RGBA")
        if im.height > im.width:
            im = im.crop((0, 0, im.width, im.width))
        return im

    def texture(self, ref):
        ns, path = ref.split(":", 1) if ":" in ref else ("minecraft", ref)
        if ns == NS:
            p = ASSETS / "textures" / (path + ".png")
            return self.first_frame(Image.open(p)) if p.exists() else None
        kind, _, name = path.partition("/")
        d = self.vi if kind == "item" else self.vb if kind == "block" else None
        if d and (d / (name + ".png")).exists():
            return self.first_frame(Image.open(d / (name + ".png")))
        return None

    # -- the mod's models
    def model(self, ref):
        """A model's textures (parents' merged in), the vanilla parent it ends at, and its elements if it has any."""
        textures, elements, parent = {}, None, None
        while ref:
            ns, path = ref.split(":", 1) if ":" in ref else ("minecraft", ref)
            if ns != NS:
                parent = ns + ":" + path
                break
            d = load(ASSETS / "models" / (path + ".json"))
            for k, v in d.get("textures", {}).items():
                textures.setdefault(k, v)
            if elements is None and "elements" in d:
                elements = d["elements"]
            ref = d.get("parent")
        for _ in range(4):
            for k, v in list(textures.items()):
                if v.startswith("#"):
                    textures[k] = textures.get(v[1:], v)
        return textures, parent, elements

    @staticmethod
    def item_model_ref(node):
        t = node.get("type", "minecraft:model")
        if t == "minecraft:model":
            return node["model"]
        if t == "minecraft:select":
            for c in node.get("cases", []):
                when = c["when"] if isinstance(c["when"], list) else [c["when"]]
                if "gui" in when:
                    return Icons.item_model_ref(c["model"])
            return Icons.item_model_ref(node.get("fallback") or node["cases"][0]["model"])
        if t == "minecraft:condition":
            return Icons.item_model_ref(node.get("on_false") or node["on_true"])
        if t == "minecraft:range_dispatch":
            return Icons.item_model_ref(node.get("fallback") or node["entries"][0]["model"])
        if t == "minecraft:composite":
            return Icons.item_model_ref(node["models"][0])
        return None

    def front_view(self, textures, elements):
        """An element model seen from the north, at twice the resolution (half-pixel elements)."""
        out = Image.new("RGBA", (32, 32))
        for el in sorted(elements, key=lambda e: -e["from"][2]):
            face = el.get("faces", {}).get("north")
            if not face:
                continue
            tex = self.texture(textures.get(face["texture"].lstrip("#"), ""))
            if tex is None:
                continue
            k = tex.width / 16
            u0, v0, u1, v1 = face.get("uv", [el["from"][0], 16 - el["to"][1], el["to"][0], 16 - el["from"][1]])
            src = tex.crop((round(u0 * k), round(v0 * k), max(round(u1 * k), round(u0 * k) + 1), max(round(v1 * k), round(v0 * k) + 1)))
            x0, x1 = round(el["from"][0] * 2), round(el["to"][0] * 2)
            y0, y1 = round((16 - el["to"][1]) * 2), round((16 - el["from"][1]) * 2)
            if x1 > x0 and y1 > y0:
                out.alpha_composite(src.resize((x1 - x0, y1 - y0), Image.NEAREST), (x0, y0))
        return out

    @staticmethod
    def shape(im, parent):
        """A flat face cut to the shape of a slab, stairs or a wall, so the family's icons tell apart."""
        keep = None
        if parent == "minecraft:block/slab":
            keep = lambda x, y: y >= 8
        elif parent == "minecraft:block/stairs":
            keep = lambda x, y: y >= 8 or x < 8
        elif parent == "minecraft:block/wall_inventory":
            keep = lambda x, y: 4 <= x < 12 or y >= 3
        if keep is None:
            return im
        im = im.copy()
        px = im.load()
        for y in range(16):
            for x in range(16):
                if not keep(x, y):
                    px[x, y] = (0, 0, 0, 0)
        return im

    def block_model(self, ref):
        textures, parent, elements = self.model(ref)
        if elements:
            return self.front_view(textures, elements)
        for key in ("layer0", "all", "side", "front", "north", "wall", "end", "texture", "top", "particle"):
            if key in textures:
                im = self.texture(textures[key])
                return self.shape(im, parent) if im is not None else None
        return self.texture(next(iter(textures.values()))) if textures else None

    def mod_item(self, path):
        p = ASSETS / "items" / (path + ".json")
        if p.exists():
            return self.block_model(self.item_model_ref(load(p)["model"]))
        bs = ASSETS / "blockstates" / (path + ".json")
        if bs.exists():
            d = load(bs)
            v = d.get("variants") or {}
            first = next(iter(v.values())) if v else d["multipart"][0]["apply"]
            first = first[0] if isinstance(first, list) else first
            return self.block_model(first["model"])
        return None

    def vanilla(self, name):
        alias = {"enchanted_golden_apple": "golden_apple", "clock": "clock_00", "compass": "compass_00"}.get(name, name)
        if self.vi and (self.vi / (alias + ".png")).exists():
            return self.first_frame(Image.open(self.vi / (alias + ".png")))
        if not self.vb:
            return None
        if name == "end_rod":
            t = self.first_frame(Image.open(self.vb / "end_rod.png"))
            im = Image.new("RGBA", (16, 16))
            im.alpha_composite(t.crop((0, 0, 2, 15)), (7, 0))
            im.alpha_composite(t.crop((2, 2, 6, 4)), (6, 14))
            return im
        for suffix in ("", "_front", "_side", "_top"):
            p = self.vb / (name + suffix + ".png")
            if p.exists():
                im = self.first_frame(Image.open(p))
                if name == "vine":
                    # (the vine is grey in its texture and tinted the foliage's colour in the hand)
                    r, g, b, a = im.split()
                    im = Image.merge("RGBA", (r.point(lambda v: v * 0x48 // 255), g.point(lambda v: v * 0xB5 // 255), b.point(lambda v: v * 0x18 // 255), a))
                return im
        return None

    def png(self, item_id):
        if item_id in self.cache:
            return self.cache[item_id]
        ns, path = item_id.split(":", 1)
        try:
            im = self.mod_item(path) if ns == NS else self.vanilla(path)
        except (OSError, KeyError, ValueError, StopIteration) as ex:
            print(f"xref: no icon for {item_id}: {ex}", file=sys.stderr)
            im = None
        data = None
        if im is not None:
            buf = io.BytesIO()
            im.save(buf, "PNG", optimize=True)
            data = buf.getvalue()
        elif ns == NS or self.vi or self.vb:
            self.missing.append(item_id)
        self.cache[item_id] = data
        return data


# ------------------------------------------------------------------------------------------------ the build
class Build:
    def __init__(self, page, icons):
        self.page = page
        self.icons = icons
        self.cfg = config()
        self.lang = load(ASSETS / "lang/en_us.json")
        self.recipes = []
        self.counts = {}
        self.facts = []  # (item or "#relic", source key, count text, chance, note, extra)
        self.ench = []   # (enchantment id, source key, chance, carrier item id, enchantments)
        self.sources = {}
        self.groups = {}  # source key -> [(page, heading, collapsed)]
        self.unmapped = []
        self.relics = [e["id"][len("relic-"):] for e in page.wiki["entries"] if e["cat"] == "relic"]
        rel = java(JAVA / "relic/Relic.java")
        m = re.search(r"boolean findable\(\) \{\s*return this != ([A-Z_]+);", rel)
        self.findable = [r for r in self.relics if not (m and r == m.group(1).lower())]

    # -- ids, names, pages
    def page_of(self, item_id):
        ns, path = item_id.split(":", 1) if ":" in item_id else ("minecraft", item_id)
        if ns != NS:
            return None
        for p in ("item-", "block-", "relic-"):
            if p + path in self.page.by_id:
                return p + path
        return None

    def name(self, item_id):
        pg = self.page_of(item_id)
        if pg:
            return self.page.by_id[pg]["name"]
        ns, path = item_id.split(":", 1)
        if ns == NS:
            return self.lang.get(f"item.{NS}.{path}") or self.lang.get(f"block.{NS}.{path}") or title_case(path)
        return VANILLA_NAMES.get(path) or title_case(path)

    def ench_name(self, eid, lvl):
        ns, path = eid.split(":", 1)
        pg = f"enchant-{path}"
        base = self.page.by_id[pg]["name"] if ns == NS and pg in self.page.by_id else title_case(path)
        return base + ("" if path.endswith("_curse") or lvl == 1 else roman(lvl))

    # -- tags
    def tag(self, tag):
        """A tag's members as far as this repository knows them, and whether that is all of them."""
        ns, path = tag.split(":", 1)
        p = DATA / ns / "tags/item" / (path + ".json")
        if not p.exists():
            return [], False
        d = load(p)
        out, complete = [], ns == NS or d.get("replace", False)
        for v in d["values"]:
            v = v["id"] if isinstance(v, dict) else v
            if v.startswith("#"):
                sub, ok = self.tag(v[1:])
                out += sub
                complete = complete and ok
            else:
                out.append(v)
        return list(dict.fromkeys(out)), complete

    TAG_NAMES = {"minecraft:trimmable_armor": "Any armour piece", "minecraft:trim_materials": "Any trim material"}
    TAG_HINTS = {"minecraft:trimmable_armor": "minecraft:iron_chestplate"}

    def slot(self, x, count=1):
        if x is None or x == "":
            return None
        if isinstance(x, dict):
            x = x.get("item") or x.get("id") or ("#" + x["tag"])
        if isinstance(x, list):
            ids = []
            for y in x:
                s = self.slot(y)
                ids += [s] if isinstance(s, str) else s.get("o", [])
            return ids[0] if len(ids) == 1 and count == 1 else {"o": list(dict.fromkeys(ids)), **({"c": count} if count > 1 else {})}
        if x.startswith("#"):
            members, complete = self.tag(x[1:])
            s = {"o": members, "tag": x, "tn": self.TAG_NAMES.get(x[1:]) or title_case(short(x[1:])) + " (tag)"}
            if not complete:
                s["more"] = True
                if not members:
                    self.unmapped.append(f"tag {x}: members not in the repository (shown by its name)")
            if x[1:] in self.TAG_HINTS:
                s["h"] = self.TAG_HINTS[x[1:]]
            return s
        return x if count == 1 else {"o": [x], "c": count}

    # -- recipes
    STATION_ORDER = ["shaped", "shapeless", "smithing", "trim", "spindle", "blasting", "smelting", "stonecutting"]

    def read_recipes(self):
        files = sorted((DATA / NS / "recipe").glob("*.json")) + sorted((DATA / "minecraft/recipe").glob("*.json"))
        for f in files:
            d = load(f)
            t = d["type"].split(":", 1)[1]
            rid = f.parent.parent.name + ":" + f.stem
            res = d.get("result")
            out = None
            if isinstance(res, dict):
                out = self.slot(res.get("id") or res.get("item"), res.get("count", 1))
            elif isinstance(res, str):
                out = res
            r = {"f": rid}
            if t == "crafting_shaped":
                pat = d["pattern"]
                grid = []
                for y in range(3):
                    for x in range(3):
                        ch = pat[y][x] if y < len(pat) and x < len(pat[y]) else " "
                        grid.append(None if ch == " " else self.slot(d["key"][ch]))
                r.update(t="shaped", **{"in": grid})
            elif t == "crafting_shapeless":
                r.update(t="shapeless", **{"in": [self.slot(i) for i in d["ingredients"]]})
            elif t == "smithing_transform":
                r.update(t="smithing", **{"in": [self.slot(d["template"]), self.slot(d["base"]), self.slot(d["addition"])]})
            elif t == "smithing_trim":
                pattern = d["pattern"]
                pname = self.lang.get("trim_pattern.%s.%s" % tuple(pattern.split(":", 1))) or title_case(short(pattern)) + " Armor Trim"
                r.update(t="trim", **{"in": [self.slot(d["template"]), self.slot(d["base"]), self.slot(d["addition"])]})
                base = re.sub(r" Armou?r Trim$", "", pname)
                out = {"o": [], "tn": "Trimmed armour", "tip": f"The armour piece, with the {base} trim", "pat": f"{base} armour trim"}
                if "h" in r["in"][1]:
                    out["h"] = r["in"][1]["h"]
            elif t in ("stonecutting", "smelting", "blasting", "smoking", "campfire_cooking"):
                r.update(t=t, **{"in": [self.slot(d["ingredient"])]})
                if t != "stonecutting":
                    secs = d.get("cookingtime", 200 if t == "smelting" else 100) / 20
                    r["x"] = f"{secs:g} s · {d.get('experience', 0):g} XP"
            else:
                self.unmapped.append(f"recipe {rid}: type {d['type']} not drawn")
                continue
            r["out"] = out
            if rid.startswith("minecraft:"):
                r["n"] = f"The mod's own recipe for the {self.name(self.slot_ids(out)[0])}, in place of vanilla's."
            self.recipes.append(r)
            self.counts[r["t"]] = self.counts.get(r["t"], 0) + 1
        # The Spindle: Tether Fragments and Seam Silk, turned into a Tether Cord (SpindleBlock, as the settings have it)
        c = self.cfg
        frag = max(1, min(8, c["SPINDLE_FRAGMENTS"]))
        silk = max(0, min(4, c["SPINDLE_SILK"]))
        turns = max(1, min(15, c["SPINDLE_TURNS"]))
        ins = [self.slot(f"{NS}:tether_fragment", frag)] + ([self.slot(f"{NS}:seam_silk", silk)] if silk else [])
        self.recipes.append({"f": f"{NS}:spindle", "t": "spindle", "in": ins, "out": f"{NS}:tether_cord", "x": f"{turns} turns"})
        self.counts["spindle"] = 1

    @staticmethod
    def slot_ids(s):
        if s is None:
            return []
        if isinstance(s, str):
            return [s]
        return list(s.get("o", []))

    # -- loot tables
    @staticmethod
    def rolls(v):
        if isinstance(v, (int, float)):
            return [(int(v), 1.0)]
        t = v.get("type", "minecraft:uniform")
        if t == "minecraft:constant":
            return [(int(v["value"]), 1.0)]
        if t == "minecraft:uniform":
            lo, hi = math.floor(v["min"]), math.floor(v["max"])
            return [(r, 1 / (hi - lo + 1)) for r in range(lo, hi + 1)]
        return [(1, 1.0)]

    @staticmethod
    def conditions(c):
        """The chance a condition (list) lets through, and whether something else decides (a block state)."""
        chance, other = 1.0, False
        for x in (c if isinstance(c, list) else [c] if c else []):
            t = x.get("condition") or x.get("type")
            if t == "minecraft:random_chance":
                chance *= x["chance"]
            elif t == "minecraft:survives_explosion":
                pass
            else:
                other = True
        return chance, other

    def functions(self, fs):
        lo = hi = 1
        notes, ench = [], {}
        for f in (fs if isinstance(fs, list) else [fs] if fs else []):
            t = f.get("function") or f.get("type")
            if t == "minecraft:set_count":
                cond = f.get("conditions") or f.get("condition")
                c = f["count"]
                if isinstance(c, (int, float)):
                    a = b = int(c)
                elif c.get("type", "minecraft:uniform") == "minecraft:uniform":
                    a, b = int(c["min"]), int(c["max"])
                elif c.get("type") == "minecraft:constant":
                    a = b = int(c["value"])
                else:
                    a, b = 0, int(c.get("n", 1))
                if cond:
                    # (a double slab's two; the Spindle's counts follow what was loaded, said by its pool)
                    if '"double"' in json.dumps(cond):
                        notes.append(f"{a} from a double slab")
                    continue
                lo, hi = a, b
            elif t == "minecraft:apply_bonus":
                notes.append("more with Fortune")
            elif t == "minecraft:set_enchantments":
                ench = f["enchantments"]
            elif t in ("minecraft:enchant_randomly", "minecraft:enchant_with_levels"):
                notes.append("enchanted")
        return lo, hi, notes, ench

    def read_table(self, key):
        """(item, count text, chance, notes, enchantments, conditional) for each thing a table gives."""
        d = load(DATA / NS / "loot_table" / (key + ".json"))
        out = []
        for pool in d.get("pools", []):
            rolls = self.rolls(pool.get("rolls", 1))
            pc, pother = self.conditions(pool.get("conditions") or pool.get("condition"))
            entries = [e for e in pool["entries"] if e.get("type") in ("minecraft:item", "minecraft:empty", "minecraft:tag")]
            total = sum(e.get("weight", 1) for e in entries)
            for e in entries:
                if e["type"] != "minecraft:item":
                    if e["type"] == "minecraft:tag":
                        self.unmapped.append(f"loot {key}: tag entry {e.get('name')} skipped")
                    continue
                ec, eother = self.conditions(e.get("conditions") or e.get("condition"))
                p1 = e.get("weight", 1) / total
                chance = pc * ec * sum(pr * (1 - (1 - p1) ** r) for r, pr in rolls)
                lo, hi, notes, ench = self.functions(e.get("functions") or e.get("modifier") or e.get("modifiers"))
                out.append((e["name"], count_text(lo, hi), chance, notes, ench, pother or eother))
        return out

    # -- sources: where each thing comes from
    def source(self, key, label, page, kind, groups=()):
        if page and page not in self.page.by_id:
            self.unmapped.append(f"source {key}: page {page} missing, shown without a link")
            page = None
        self.sources[key] = {"s": label, "p": page, "k": kind}
        self.groups[key] = [g for g in groups if g[0] in self.page.by_id]

    def creature(self, key, cid, heading="Drops", label=None):
        pg = "creature-" + cid
        if pg not in self.page.by_id:
            die(f"no creature page {pg}")
        self.source(key, label or self.page.by_id[pg]["name"], pg, "drop", [(pg, heading, False)])

    def define_sources(self):
        S = self.source
        for t, (label, page) in {
            "chests/fray_tangle": ("Tangles in the Fray", "concept-fray"),
            "chests/seam_reliquary": ("Reliquaries in the Seam", "concept-seam"),
            "chests/seam_village": ("Barrels in villages of the Thinned", "concept-seam"),
            "chests/seam_loom": ("Looms in the Seam", "concept-seam"),
            "chests/seam_storeroom": ("Storerooms of the Thinned", "concept-seam"),
            "chests/old_hoard": ("Old tribe hoards", "concept-hoards"),
            "chests/raided_hoard": ("Raided tribe hoards", "concept-hoards"),
            "gameplay/pale_anchor": ("Barrels of an Unfinished Anchor", "guide-pale-reach"),
        }.items():
            S(t, label, page, "found", [(page, label, True)])
        S("chests/helds_chamber", "The Held's chamber (the first warm chain)", None, "found")
        S("chests/held_remains", "The Held's remains (later warm chains)", None, "found")
        for t, label in {"ancient_city": "Ancient City chests", "bastion_treasure": "Bastion treasure chests",
                         "end_city_treasure": "End City chests", "nether_bridge": "Nether fortress chests",
                         "ruined_portal": "Ruined portal chests", "stronghold_library": "Stronghold library chests"}.items():
            S("inject/" + t, label, None, "found")
        S("gameplay/pale_thanks", "The Endermen's thanks, when they go home", "guide-pale-reach", "given",
          [("guide-pale-reach", "The Endermen's thanks (Homeward Keystone)", True)])
        S("gameplay/pale_hoard", "The Endermen's hoard, when the End is locked", "guide-pale-reach", "given",
          [("guide-pale-reach", "Their hoard (Pale Lock)", True)])
        S("gameplay/unanchored", "An Unanchored build finished unwatched", "concept-events", "given",
          [("concept-events", "What an Unanchored build leaves", True)])
        S("gameplay/trial_gold", "A Rift Trial's first gold medal of the week", "concept-trials", "given",
          [("concept-trials", "A first gold medal of the week, besides its fragments", True)])
        S("gameplay/hollowling_gift", "A Hollowling companion's gifts", "concept-companion", "given",
          [("creature-hollowling", "A companion's gifts", False), ("concept-companion", "Its gifts", True)])
        S("gameplay/lurker_offering", "A Lurker's offering", "creature-lurker", "given",
          [("creature-lurker", "Offerings (when it goes quietly)", False)])
        for t, cid in {"keeper_spoils": "keeper", "matriarch_spoils": "matriarch", "ferryman_spoils": "ferryman",
                       "weaver_spoils": "weaver", "knot_mother_spoils": "knot_mother", "pale_mason_spoils": "pale_mason",
                       "pale_hauler_spoils": "pale_hauler", "gatebreaker_spoils": "gatebreaker", "lockwarden_spoils": "lockwarden",
                       "tidewalker_haul": "tidewalker", "edgecrawler_spoils": "edgecrawler", "cragback_spoils": "cragback",
                       "veil_ray": "veil_ray", "frayed_ray": "frayed_ray", "thinned": "thinned"}.items():
            self.creature("gameplay/" + t, cid)
        self.creature("gameplay/thinned_fray", "thinned", "Slain in the Fray, also", "Thinned in the Fray")
        for cid in ("cave_lurker", "sculk_lurker", "starved_one"):
            self.creature(cid, cid)
        S("beyond", "Monsters slain beyond the wound", "concept-expedition", "drop",
          [("concept-expedition", "Monsters slain beyond the wound", True)])
        S("abyssal", "An Abyssal curse, as it breaks", "concept-abyssal", "given", [("concept-abyssal", "As one breaks", True)])
        S("heart", "The Heart of the Seam, once the Starved One is slain", "creature-starved_one", "found",
          [("creature-starved_one", "At the Heart, once it is slain", False)])
        S("pacts", "A pact kept at heat 4 or more", "concept-pacts", "given", [("concept-pacts", "A kept pact", True)])
        S("barter", "Hollowlings", "creature-hollowling", "sold")
        S("seam_rock", "Deep in the rock of the Seam's islands", "concept-seam", "world")
        S("fray_knots", "The stone at the heart of the Fray's knots", "concept-fray", "world")
        # (a known source must have a label: every table of the mod is named above)
        for p in sorted((DATA / NS / "loot_table").rglob("*.json")):
            key = str(p.relative_to(DATA / NS / "loot_table"))[:-5]
            if not key.startswith("blocks/") and key not in self.sources:
                self.unmapped.append(f"loot table {key}: no source name, shown by its id")
                self.source(key, key, None, "other")

    def fact(self, item, key, count="", chance=1.0, notes=(), extra=None):
        self.facts.append([item, key, count, chance, list(notes), extra])

    def read_loot(self):
        root = DATA / NS / "loot_table"
        for p in sorted(root.rglob("*.json")):
            key = str(p.relative_to(root))[:-5]
            for item, cnt, chance, notes, ench, conditional in self.read_table(key):
                if key.startswith("blocks/"):
                    block = key[len("blocks/"):]
                    if key not in self.sources:
                        bp = self.page_of(f"{NS}:{block}")
                        self.source(key, self.name(f"{NS}:{block}"), bp, "mined", [(bp, "When mined", False)] if bp else [])
                    if conditional:
                        notes = notes + ["what was loaded in it"]
                if ench:
                    for eid, lvl in ench.items():
                        if eid.startswith(NS + ":"):
                            self.ench.append((eid, key, chance, item, ench))
                    extra = {"ench": ench}
                else:
                    extra = None
                self.fact(item, key, cnt, chance, notes, dict(extra or {}, back=True) if conditional else extra)

    def read_besides(self):
        """What the game gives besides a table (ViewerContent.BESIDES, and the code it mirrors), as the defaults have it."""
        c = self.cfg
        src = VIEWER.read_text(encoding="utf-8")
        relic_detail = {"keeper_spoils": pct(c["RELIC_KEEPER_CHANCE"]) or "1", "matriarch_spoils": str(c["MATRIARCH_RELICS"]),
                        "ferryman_spoils": pct(c["FERRYMAN_RELIC_CHANCE"]) or "1", "weaver_spoils": str(c["WEAVER_RELICS"]),
                        "knot_mother_spoils": str(c["KNOT_MOTHER_RELICS"]), "starved_one": str(c["RELIC_STARVED_ONE"])}
        for key, vals in re.findall(r'"(\w+)", new String\[\] \{([^}]*)\}', src):
            skey = "gameplay/" + key if (DATA / NS / "loot_table/gameplay" / (key + ".json")).exists() else key
            if skey not in self.sources:
                self.unmapped.append(f"viewer source {key}: not known here")
                continue
            for v in re.findall(r'"([^"]+)"', vals):
                if v == "relic":
                    d = relic_detail.get(key, "1")
                    self.fact("#relic", skey, d if not d.endswith("%") else "", float(d[:-1]) / 100 if d.endswith("%") else 1.0)
                    continue
                item, _, n = v.partition("*")
                notes = []
                if item == "disc_fragment_elegy":
                    n = str(c["DISC_FRAGMENT_ELEGY_COUNT"])
                    notes = ["to each player who fought it"]
                chance = 1.0
                if item == "music_disc_what_listens":
                    chance = c["MUSIC_DISC_LISTENS_CHANCE"]
                    notes = ["slain by a player"]
                if item.startswith("thread_of_"):
                    notes = ["to each player who fought it"]
                self.fact(f"{NS}:{item}", skey, n if n not in ("", "1") else "", chance, notes)
        # Monsters beyond the wound (ExpeditionService.onMobKilled)
        ex = java(JAVA / "expedition/ExpeditionService.java")
        elites = re.search(r"ELITES = Set\.of\(([^)]*)\)", ex)
        el = ", ".join(title_case(x) for x in re.findall(r'"(\w+)"', elites.group(1))) if elites else "the strongest"
        self.fact(f"{NS}:tether_fragment", "beyond", "", c["FRAGMENT_CHANCE"], [f"{pct(c['FRAGMENT_CHANCE_ELITE'])} for {el}"])
        # The Thinned (SeamFauna.onDeath): a fragment now and then, more often in the Fray; a second Veil Wing from a ray
        self.fact(f"{NS}:tether_fragment", "gameplay/thinned", "", c["SEAM_THINNED_FRAGMENT_CHANCE"], ["on the islands"])
        self.fact(f"{NS}:tether_fragment", "gameplay/thinned_fray", "", c["SEAM_FRAY_FRAGMENT_CHANCE"])
        for ray in ("gameplay/veil_ray", "gameplay/frayed_ray"):
            self.fact(f"{NS}:veil_wing", ray, "", 1.0, [f"{pct(c['VEIL_WING_EXTRA_CHANCE'])} a second"])
        # The discs (Discs, Loot.woundDisc, SeamService.fillLoot)
        self.fact(f"{NS}:disc_fragment_abyss", "abyssal", str(c["DISC_FRAGMENT_ABYSS_BROKEN"]) if c["DISC_FRAGMENT_ABYSS_BROKEN"] > 1 else "")
        self.fact(f"{NS}:disc_fragment_abyss", "chests/fray_tangle", "", c["DISC_FRAGMENT_ABYSS_TANGLE_CHANCE"])
        self.fact(f"{NS}:disc_fragment_abyss", "gameplay/frayed_ray", "", c["DISC_FRAGMENT_ABYSS_RAY_CHANCE"], ["slain by a player"])
        for t in ("chests/fray_tangle", "chests/old_hoard", "chests/raided_hoard"):
            self.fact(f"{NS}:music_disc_wound_remembers", t, "", c["MUSIC_DISC_WOUND_CHANCE"])
        for t, rich in (("chests/seam_loom", 1), ("chests/seam_village", 2), ("chests/seam_reliquary", 3), ("chests/fray_tangle", 4)):
            self.fact(f"{NS}:music_disc_tick_of_the_boundary", t, "", c["SEAM_MUSIC_DISC_CHANCE"] * rich)
            # and a relic among the Weavers' leavings, likelier the richer the barrel
            self.fact("#relic", t, "", min(1.0, c["RELIC_SEAM_CHEST_CHANCE"] * (1 + rich)))
        self.fact("#relic", "chests/raided_hoard", "", c["TRIBE_RAID_RELIC_CHANCE"])
        self.fact("#relic", "gameplay/trial_gold", str(c["TRIAL_GOLD_RELICS"]) if c["TRIAL_GOLD_RELICS"] > 1 else "")
        self.fact("#relic", "pacts", "", 1.0, [f"{pct(c['PACT_RELIC_CHANCE_PER_HEAT'])} a heat, at most {pct(c['PACT_RELIC_MAX_CHANCE'])}"])
        # The ending's two tools (they lie at the Heart), and the Pale Mason's last stone and lock (HomeOrLock.spoils)
        for i in ("mending_thread", "severing_shears"):
            self.fact(f"{NS}:{i}", "heart", "", 1.0, ["choose one"])
        for i in ("homeward_keystone", "pale_lock"):
            self.fact(f"{NS}:{i}", "gameplay/pale_mason_spoils", "", 1.0, ["while the End has no ending"])
        # Tetherite ore in the Seam (TetheriteOre worldgen settings)
        self.fact(f"{NS}:tetherite_ore", "seam_rock", "", c["TETHERITE_ORE_ISLAND_CHANCE"], ["of the rock"])
        self.fact(f"{NS}:tetherite_ore", "fray_knots", "", c["TETHERITE_ORE_FRAY_CHANCE"], ["of the stone"])

    def read_barter(self):
        """The Hollowlings' wares (BarterService), at the price a tolerated player pays."""
        src = java(JAVA / "tribe/BarterService.java")
        c = self.cfg
        k = max(0.0, c["TRIBE_BARTER_PRICE"])

        def item(ref):
            m = re.match(r"(Mod)?Items\.(\w+)", ref)
            return (NS if m.group(1) else "minecraft") + ":" + m.group(2).lower()

        def price(p):
            m = re.match(r"Math\.max\(1, Config\.(\w+)\)", p)
            return max(1, int(c[m.group(1)])) if m else int(p)

        wares = []
        for case, want, p, give, n, uses in re.findall(
                r"case (\w+) -> new Ware\(\w+, (Items\.\w+), (\d+|Math\.max\(1, Config\.\w+\)), (ModItems\.\w+|Items\.\w+|RelicService\.item\(.*?\)), (\d+), (\d+)\);", src):
            when = {"FRAGMENT": "every day", "CORD": "when the tribe trusts you", "RELIC": "kin only", "CLOTH": "kin only"}[case]
            if case == "CORD" and not c.get("CLAN_ENABLED", 1):
                continue
            if case == "RELIC" and c["TRIBE_BARTER_RELIC_PRICE"] <= 0:
                continue
            wares.append(("#relic" if case == "RELIC" else item(give), int(n), item(want), price(p), when, int(uses)))
        for _, want, p, give, n, uses in re.findall(r"new Ware\((\d+), (Items\.\w+), (\d+), ((?:Mod)?Items\.\w+), (\d+), (\d+)\)", src):
            wares.append((item(give), int(n), item(want), int(p), "some days", int(uses)))
        if not wares:
            die("no wares found in BarterService.java")
        order = {"every day": 0, "some days": 1, "when the tribe trusts you": 2, "kin only": 3}
        wares.sort(key=lambda w: order[w[4]])
        self.wares = []
        for give, n, want, p, when, uses in wares:
            cost = max(1, math.ceil(p * k))
            self.wares.append((give, n, want, cost, when, uses))
            self.fact(give, "barter", f"×{n}" if n > 1 else "", 1.0, [when, f"{uses} a day"], {"want": [want, cost]})
        self.likes = [("minecraft:" + i.lower(), int(n)) for i, n in re.findall(r"LIKES\.put\(Items\.(\w+), (\d+)\)", src)]

    # -- per page
    def detail(self, count, chance, notes):
        parts = [x for x in (count, pct(chance)) if x] + list(notes)
        return " · ".join(parts)

    def build(self):
        self.read_recipes()
        self.define_sources()
        self.read_loot()
        self.read_besides()
        self.read_barter()
        pages = {}

        def px(pg):
            return pages.setdefault(pg, {})

        relic_slot = {"o": [f"{NS}:{r}" for r in self.findable], "tn": "A random relic", "p": "concept-relics"}

        def row_item(item):
            return relic_slot if item == "#relic" else item

        # recipes: what makes a page's thing, and what uses it
        order = {t: k for k, t in enumerate(self.STATION_ORDER)}
        idx = sorted(range(len(self.recipes)), key=lambda i: (order[self.recipes[i]["t"]], i))
        self.recipes = [self.recipes[i] for i in idx]
        uses = {}
        for i, r in enumerate(self.recipes):
            for o in self.slot_ids(r["out"]):
                pg = self.page_of(o)
                if pg:
                    px(pg).setdefault("mk", []).append(i)
            for s in r["in"]:
                for o in self.slot_ids(s):
                    pg = self.page_of(o)
                    if pg and i not in px(pg).setdefault("us", []):
                        px(pg)["us"].append(i)
            if r["f"] == "minecraft:lodestone":
                # an anchor is a lodestone by a portal: the mod's lodestone recipe goes on the anchors' pages
                for pg in ("block-ember_lodestone", "block-void_lodestone", "concept-anchors"):
                    if pg in self.page.by_id:
                        px(pg).setdefault("mk", []).append(i)
        for pg in pages:
            if "us" in pages[pg]:
                uses[pg] = len(pages[pg]["us"])

        # sources on the things' pages, and the sources' own lists of what they give
        src_rows = {}
        group_rows = {}
        merged = {}
        for f in self.facts:
            k = (f[0], f[1], json.dumps((f[5] or {}).get("ench"), sort_keys=True))
            if k in merged:
                m = merged[k]
                m[2] = m[2] or f[2]
                m[3] = 1 - (1 - m[3]) * (1 - f[3]) if f[3] < 1 or m[3] < 1 else 1.0
                m[4] += [n for n in f[4] if n not in m[4]]
                if f[5]:
                    m[5] = dict(m[5] or {}, **f[5])
            else:
                merged[k] = list(f)
        for item, key, cnt, chance, notes, extra in merged.values():
            src = self.sources[key]
            extra = extra or {}
            back = extra.get("back")
            targets = self.findable if item == "#relic" else [short(item)] if item.startswith(NS + ":") else []
            if not back and not extra.get("ench"):
                for t in targets:
                    pg = self.page_of(f"{NS}:{t}")
                    if not pg or (src["k"] == "mined" and src["p"] == pg):
                        continue
                    row = {"k": src["k"], "s": src["s"]}
                    if src["p"]:
                        row["p"] = src["p"]
                    if src["k"] == "mined":
                        row["i"] = f"{NS}:{key[len('blocks/'):]}"
                    d = self.detail(cnt if item != "#relic" else ("" if cnt in ("", "1") else f"×{cnt}"), chance, notes)
                    if d:
                        row["d"] = d
                    if "want" in extra:
                        row["w"] = extra["want"]
                    src_rows.setdefault(pg, []).append(row)
            for gpg, heading, collapsed in self.groups.get(key, []):
                row = {"i": row_item(item)}
                if extra.get("ench"):
                    names = [self.ench_name(e, l) for e, l in extra["ench"].items()]
                    if item == "minecraft:book":
                        row["i"] = "minecraft:enchanted_book"
                        row["n"] = "Enchanted Book (" + ", ".join(names) + ")"
                        mods = [e for e in extra["ench"] if e.startswith(NS + ":")]
                        if len(mods) == 1:
                            row["p"] = "enchant-" + short(mods[0])
                    else:
                        row["n"] = self.name(item) + " (" + ", ".join(names) + ")"
                d = self.detail(cnt, chance, notes)
                if d:
                    row["d"] = d
                if "want" in extra:
                    row["w"] = extra["want"]
                group_rows.setdefault((gpg, heading, collapsed, key), []).append(row)
        # enchantment pages: where their books and gear lie
        for eid, key, chance, carrier, ench in self.ench:
            pg = "enchant-" + short(eid)
            if pg not in self.page.by_id:
                self.unmapped.append(f"enchantment {eid}: no page")
                continue
            src = self.sources[key]
            row = {"k": src["k"], "s": src["s"], "i": "minecraft:enchanted_book" if carrier == "minecraft:book" else carrier}
            if src["p"]:
                row["p"] = src["p"]
            others = [self.ench_name(e, l) for e, l in ench.items() if e != eid]
            d = [pct(chance), "a book" if carrier == "minecraft:book" else "on " + self.name(carrier)]
            if others:
                d.append("with " + ", ".join(others))
            row["d"] = " · ".join(x for x in d if x)
            src_rows.setdefault(pg, []).append(row)

        for pg, rows in src_rows.items():
            rows.sort(key=lambda r: KIND_ORDER.index(r["k"]))
            px(pg)["src"] = rows
        for r in self.findable:
            if f"relic-{r}" in pages and pages[f"relic-{r}"].get("src"):
                pages[f"relic-{r}"]["srcn"] = "Each of these gives a relic drawn at random, a rarer tier less often."
        # the drop groups, in the order the sources were defined
        key_order = {k: i for i, k in enumerate(self.sources)}
        for (gpg, heading, collapsed, key), rows in sorted(group_rows.items(), key=lambda kv: (kv[0][0], key_order[kv[0][3]])):
            g = {"h": heading, "r": rows}
            if collapsed:
                g["c"] = 1
            if key.startswith("blocks/") and all(r["i"] == f"{NS}:{key[7:]}" for r in rows):
                g = {"h": heading, "self": 1, "r": rows}
            px(gpg).setdefault("dr", []).append(g)
        # the Hollowling: what it sells and what it takes as a gift
        hp = "creature-hollowling"
        sells = []
        for give, n, want, cost, when, u in self.wares:
            row = {"i": row_item(give), "w": [want, cost], "d": " · ".join(x for x in (f"×{n}" if n > 1 else "", when, f"{u} a day") if x)}
            sells.append(row)
        px(hp).setdefault("dr", []).insert(0, {"h": "Sells (barter, the price a tolerated player pays; trust lowers it)", "r": sells})
        px(hp)["dr"].insert(1, {"h": "Takes as a gift (this many of one)", "r": [{"i": i, "d": str(n) if n > 1 else ""} for i, n in self.likes]})
        for g in px(hp)["dr"]:
            for r in g["r"]:
                if not r.get("d"):
                    r.pop("d", None)

        # write into the entries
        removed = 0
        for e in self.page.wiki["entries"]:
            x = pages.get(e["id"])
            if not x:
                continue
            drop = set()
            if x.get("mk") and e["cat"] in ("item", "block", "relic"):
                drop.add("Crafted from")
            if x.get("src") and e["cat"] in ("item", "block", "relic"):
                drop.add("Found in")
            if e["cat"] == "block" and any(g["h"] == "When mined" for g in x.get("dr", [])):
                drop.add("Drops when mined")
            if e["cat"] == "creature" and any(g["h"] == "Drops" for g in x.get("dr", [])):
                drop.add("Drops")
            before = len(e["facts"])
            e["facts"] = [f for f in e["facts"] if f[0] not in drop]
            removed += before - len(e["facts"])
            e["xr"] = {k: x[k] for k in ("mk", "us", "src", "srcn", "dr") if k in x}
            e["xt"] = self.text(e, x)
        self.removed = removed
        self.pages = pages
        self.uses = uses

        # every thing named in the data, with its name, page and icon
        ids = set()

        def walk(v):
            if isinstance(v, str):
                if re.fullmatch(r"[a-z0-9_.-]+:[a-z0-9_./-]+", v):
                    ids.add(v)
            elif isinstance(v, list):
                for y in v:
                    walk(y)
            elif isinstance(v, dict):
                for kk, y in v.items():
                    if kk in ("o", "h", "i", "w", "out", "in"):
                        walk(y)
        for r in self.recipes:
            walk(r["in"])
            walk(r["out"])
        for x in pages.values():
            for row in x.get("src", []):
                walk(row.get("i"))
                walk(row.get("w"))
            for g in x.get("dr", []):
                for row in g["r"]:
                    walk(row["i"])
                    walk(row.get("w"))
        icon_png = {}
        it = {}
        for i in sorted(ids):
            d = {"n": self.name(i)}
            pg = self.page_of(i)
            if pg:
                d["p"] = pg
            png = self.icons.png(i)
            if png:
                d["i"] = icon_png.setdefault(png, len(icon_png))
            it[i] = d
        # the pages' own icons (blocks without an item too: the anchors, the Curse Knot)
        hi = {}
        for e in self.page.wiki["entries"]:
            if e["cat"] in ("item", "block", "relic"):
                path = e["id"].split("-", 1)[1]
                png = self.icons.png(f"{NS}:{path}")
                if png:
                    hi[e["id"]] = icon_png.setdefault(png, len(icon_png))
        self.icon_png = icon_png
        self.page.wiki["xref"] = {"it": it, "rc": self.recipes, "hi": hi}

    # -- text forms, for the search and the "ask" box
    def slot_text(self, s):
        if s is None:
            return ""
        if isinstance(s, str):
            return self.name(s)
        if s.get("tag"):
            return self.name(s["o"][0]) if len(s["o"]) == 1 and not s.get("more") else s["tn"]
        if s.get("tn"):
            return s["tn"]
        n = " or ".join(self.name(i) for i in s.get("o", []))
        return (f"{s['c']} " if s.get("c", 1) > 1 else "") + n

    def recipe_text(self, r):
        st = {"shaped": "crafting table", "shapeless": "crafting table, shapeless", "smithing": "smithing table",
              "trim": "smithing table, armour trim", "stonecutting": "stonecutter", "smelting": "furnace",
              "blasting": "blast furnace", "spindle": "Spindle"}[r["t"]]
        parts = {}
        for s in r["in"]:
            if s is None:
                continue
            k = self.slot_text(s)
            parts[k] = parts.get(k, 0) + 1
        ins = ", ".join((f"{n} " if n > 1 else "") + k for k, n in parts.items())
        out = r["out"]
        o = out["pat"] if isinstance(out, dict) and out.get("pat") else self.slot_text(out)
        return f"{ins} → {o} ({st})"

    def text(self, e, x):
        lines = []
        if x.get("mk"):
            lines.append("Recipes: " + "; ".join(self.recipe_text(self.recipes[i]) for i in x["mk"]) + ".")
        if x.get("src"):
            by = {}
            for r in x["src"]:
                by.setdefault(r["k"], []).append(r["s"] + (f" ({r['d']})" if r.get("d") else "") +
                                                 (f" for {r['w'][1]} {self.name(r['w'][0])}" if r.get("w") else ""))
            names = {"found": "Found in", "drop": "Dropped by", "given": "Given by", "mined": "Mined from", "world": "Generates",
                     "sold": "Sold by", "other": "Also"}
            lines.append(" ".join(f"{names[k]}: {', '.join(v)}." for k, v in by.items()) + (" " + x["srcn"] if x.get("srcn") else ""))
        if x.get("us"):
            outs = []
            for i in x["us"]:
                r = self.recipes[i]
                o = r["out"]["pat"] if r["t"] == "trim" else self.slot_text(r["out"]).lstrip("0123456789 ")
                if o not in outs:
                    outs.append(o)
            lines.append("Used in: " + ", ".join(outs) + ".")
        for g in x.get("dr", []):
            items = []
            for r in g["r"]:
                n = r.get("n") or self.slot_text(r["i"])
                items.append(n + (f" ({r['d']})" if r.get("d") else "") + (f" for {r['w'][1]} {self.name(r['w'][0])}" if r.get("w") else ""))
            lines.append(f"{g['h']}: " + ("itself" if g.get("self") else ", ".join(items)) + ".")
        t = " ".join(lines)
        return t if len(t) <= 1600 else t[:1597] + "..."


# ------------------------------------------------------------------------------------------------ the page's additions
CSS = r"""<style id="xref-style">
/* Recipes, uses and sources, drawn as a recipe viewer draws them (added by tools/wiki/xref.py) */
.xi { display: inline-block; width: 32px; height: 32px; flex: none; background: center / 100% 100% no-repeat; image-rendering: crisp-edges; image-rendering: pixelated; vertical-align: middle }
article h3 > .xi { margin: -5px 10px 0 0 }
.xsec { margin-top: 14px; padding-top: 10px; border-top: 1px dashed var(--line); min-width: 0 }
.xsec h4 { font: 600 12px/1.3 var(--mono); letter-spacing: .1em; text-transform: uppercase; color: var(--muted); margin: 0 0 8px }
.xsec h4 b { font-weight: 400; margin-left: 6px; color: var(--gold) }
.xsec h5 { font: 700 14.5px/1.3 var(--body); margin: 10px 0 6px; color: var(--ink) }
.xrs { display: flex; flex-wrap: wrap; gap: 10px; align-items: flex-start }
.xr { margin: 0; background: var(--bg); border: 1px solid var(--line); border-radius: 10px; padding: 8px 10px 10px; min-width: 0; max-width: 100% }
.xr figcaption { font: 12px/1.3 var(--mono); color: var(--muted); margin: 0 0 7px }
.xr figcaption b { color: var(--gold); font-weight: 600 }
.xrb { display: flex; flex-wrap: wrap; align-items: center; gap: 6px 8px }
.xrow { display: inline-flex; align-items: center; gap: 6px; margin-right: 6px }
.xg { display: grid; grid-template-columns: repeat(var(--c, 3), 36px); gap: 2px }
.xa { color: var(--gold); font: 700 18px/1 var(--mono); padding: 0 1px }
.xpl { color: var(--muted); font: 700 15px/1 var(--mono) }
.xs { position: relative; display: inline-flex; align-items: center; justify-content: center; width: 36px; height: 36px; box-sizing: border-box; background: var(--chip); border: 1px solid var(--line); border-radius: 5px; box-shadow: inset 1px 1px 0 rgba(0, 0, 0, .07); color: var(--ink); text-decoration: none; vertical-align: middle; outline-offset: 1px }
.xs.e { background: transparent; box-shadow: none; border-style: dashed; opacity: .7 }
.xs.o { width: 46px; height: 46px; background: var(--gold-soft); border: 1.5px solid var(--gold) }
a.xs:hover, a.xc:hover { border-color: var(--teal) }
.xs > b { position: absolute; right: 1px; bottom: -1px; font: 700 12.5px/1 var(--mono); color: #fff; pointer-events: none;
  text-shadow: 1px 1px 0 #262626, -1px 0 0 #262626, 0 -1px 0 #262626, 1px 0 0 #262626, 0 1px 0 #262626 }
.xs > i { position: absolute; top: -6px; right: -6px; font: 600 10px/1 var(--mono); font-style: normal; color: var(--panel); background: var(--teal); border-radius: 999px; padding: 2px 4px; pointer-events: none }
.xs .xw { font: 9px/1.05 var(--body); text-align: center; overflow: hidden; display: -webkit-box; -webkit-line-clamp: 3; -webkit-box-orient: vertical; padding: 0 1px; hyphens: manual; overflow-wrap: break-word }
.xcap { margin: 7px 0 0; font-size: 13.5px; line-height: 1.4; color: var(--muted); max-width: 34em }
.xcap a, .xsrc a, .xmore summary { color: var(--teal) }
.xch { display: flex; flex-wrap: wrap; gap: 6px }
.xc { display: inline-flex; align-items: center; gap: 6px; max-width: 100%; min-width: 0; background: var(--chip); border: 1px solid transparent; border-radius: 8px; padding: 2px 9px 2px 3px; font-size: 15px; line-height: 1.25; color: var(--ink); text-decoration: none }
.xc .xi { width: 24px; height: 24px }
.xc .xb { min-width: 0 }
a.xc .xn { color: var(--teal); text-decoration: underline; text-underline-offset: 2px }
.xc small, .xsrc small { font: 12.5px var(--mono); color: var(--muted) }
.xw2 { white-space: nowrap }
.xsrc .xi, .xc .xw2 .xi { width: 20px; height: 20px; margin: -3px 2px 0 0 }
dl.xsrc { margin: 10px 0 0; font-size: 15px }
dl.xsrc dd { line-height: 1.55 }
.xsrc .xsi { white-space: normal }
.xmore { margin-top: 8px }
.xmore > summary { cursor: pointer; font-size: 14.5px; width: fit-content }
.xmore[open] > summary { margin-bottom: 8px }
.xself { margin: 0; color: var(--muted); font-size: 15px }
.xtip { position: fixed; z-index: 30; max-width: min(260px, calc(100vw - 24px)); background: var(--ink); color: var(--bg); font: 13.5px/1.3 var(--body); padding: 5px 9px; border-radius: 7px; pointer-events: none; box-shadow: 0 2px 10px rgba(0, 0, 0, .25) }
@media (max-width: 560px) { dl.xsrc dd { margin-bottom: 4px } }
</style>
"""

JS = r"""// xref:begin (added by tools/wiki/xref.py: recipes, uses and sources as a recipe viewer shows them; do not edit by hand)
const XR = WIKI.xref || { it: {}, rc: [], hi: {} };
const XKIND = { found: "Found in", drop: "Dropped by", given: "Given", mined: "Mined from", world: "Generates in", sold: "Sold by", other: "Also" };
const XST = { shaped: "Crafting table", shapeless: "Crafting table · shapeless", smithing: "Smithing table", trim: "Smithing table · armour trim",
  stonecutting: "Stonecutter", smelting: "Furnace", blasting: "Blast furnace", spindle: "Spindle" };
for (const e of WIKI.entries) if (e.xt) e._f += " " + norm(e.xt);
const xInfo = id => XR.it[id] || { n: String(id).replace(/^#?[a-z0-9_.-]+:/, "").replace(/[_/]+/g, " ") };
const xHl = (s, toks) => toks && toks.length ? highlight(s, toks) : esc(s);
const xIco = (id, cls) => { const it = xInfo(id); return it.i != null ? `<span class="xi xq${it.i}${cls ? " " + cls : ""}" aria-hidden="true"></span>` : ""; };
const xLink = (pg, inner, cls, self) => pg && pg !== self ? `<a${cls ? ` class="${cls}"` : ""} href="#${esc(pg)}" data-xgo="${esc(pg)}">${inner}</a>` : (cls ? `<span class="${cls}">${inner}</span>` : inner);
const xObj = s => typeof s === "string" ? { o: [s] } : s;
function xSlotName(s) { const o = xObj(s), ids = o.o || []; return o.tag && (ids.length !== 1 || o.more) || (o.tn && !ids.length) ? o.tn : ids.map(i => xInfo(i).n).join(" or ") || o.tn || "?"; }
function xSlot(s, out, self) {
  const k = "xs" + (out ? " o" : "");
  if (s == null) return `<span class="${k} e" aria-hidden="true"></span>`;
  const o = xObj(s), ids = o.o || [], first = ids[0] || o.h, it = first ? xInfo(first) : null;
  let tip = o.tip || (o.tag ? o.tn + (ids.length ? ": " + ids.map(i => xInfo(i).n).join(", ") + (o.more ? " and more" : "") : "") + ` (#${o.tag.replace(/^#/, "")})`
    : o.tn && ids.length > 1 ? `${o.tn}: one of ${ids.length}` : ids.map(i => xInfo(i).n).join(" or "));
  if (o.c > 1) tip = o.c + " × " + tip;
  const plus = ids.length > 1 ? `<i>+${ids.length - 1}</i>` : o.more ? `<i>+</i>` : "";
  // (a name in a slot: long words may break, with a hyphen)
  const inner = (it && it.i != null ? xIco(first) : `<span class="xw">${esc(o.tn || (it ? it.n : "?")).replace(/([A-Za-z]{5})(?=[A-Za-z]{3})/g, "$1\u00ad")}</span>`) + plus + (o.c > 1 ? `<b>${o.c}</b>` : "");
  const pg = o.p || (ids.length === 1 && it ? it.p : null);
  return pg && pg !== self ? `<a class="${k}" href="#${esc(pg)}" data-xgo="${esc(pg)}" title="${esc(tip)}">${inner}</a>`
    : `<span class="${k}" title="${esc(tip)}" tabindex="0">${inner}</span>`;
}
function xCap(rs, toks, self) {
  const m = new Map();
  for (const r of rs) for (const s of r.in) { if (s == null) continue; const key = JSON.stringify(typeof s === "object" ? Object.assign({}, s, { c: undefined }) : s); m.set(key, (m.get(key) || 0) + ((typeof s === "object" && s.c) || 1) / (rs.length > 1 ? rs.length : 1)); }
  const part = (s, n) => { const o = xObj(s), ids = o.o || [], pg = ids.length === 1 && !o.more ? xInfo(ids[0]).p : null;
    return (n > 1 ? Math.round(n) + " " : "") + xLink(pg, xHl(xSlotName(s), toks), "", self); };
  const ins = rs.length > 1 ? rs.map(r => part(r.in[0], 1)).filter((v, i, a) => a.indexOf(v) === i).join(", ")
    : [...m].map(([k, n]) => part(JSON.parse(k), n)).join(", ");
  const r = rs[0], o = xObj(r.out), oid = (o.o || [])[0];
  const outs = rs.map(x => { const q = xObj(x.out), id = (q.o || [])[0]; return x.t === "trim" ? esc(q.tn) : xLink(id && xInfo(id).p, xHl(xInfo(id).n, toks), "", self); })
    .filter((v, i, a) => a.indexOf(v) === i).join(", ");
  return `<p class="xcap">${ins} → ${outs}${r.n ? `. ${esc(r.n)}` : ""}</p>`;
}
function xPanel(rs, toks, self) {
  const r = rs[0];
  const head = `<b>${esc(XST[r.t] || r.t)}</b>${r.x ? " · " + esc(r.x) : ""}`;
  let body;
  if (rs.length > 1 || r.t === "stonecutting" || r.t === "smelting" || r.t === "blasting")
    body = rs.map(x => `<span class="xrow">${xSlot(x.in[0], false, self)}<span class="xa" aria-hidden="true">→</span>${xSlot(x.out, true, self)}</span>`).join("");
  else {
    let ins;
    if (r.t === "shaped") ins = `<span class="xg">${r.in.map(s => xSlot(s, false, self)).join("")}</span>`;
    else if (r.t === "smithing" || r.t === "trim") ins = r.in.map(s => xSlot(s, false, self)).join(`<span class="xpl" aria-hidden="true">+</span>`);
    else { const n = r.in.length; ins = `<span class="xg" style="--c:${n <= 4 ? n : 3}">${r.in.map(s => xSlot(s, false, self)).join("")}</span>`; }
    body = `${ins}<span class="xa" aria-hidden="true">→</span>${xSlot(r.out, true, self)}`;
  }
  return `<figure class="xr"><figcaption>${head}</figcaption><div class="xrb">${body}</div>${xCap(rs, toks, self)}</figure>`;
}
// Recipes as panels; single-input ones (the stonecutter, the furnaces) one panel per output (by: "out") or per input ("in")
function xRecipes(list, by, toks, self) {
  const groups = [], seen = new Map();
  for (const i of list) {
    const r = XR.rc[i];
    if (r.t === "stonecutting" || r.t === "smelting" || r.t === "blasting") {
      const key = r.t + "|" + JSON.stringify(by === "in" ? r.in[0] : r.out);
      if (seen.has(key)) { seen.get(key).push(r); continue; }
      const g = [r]; seen.set(key, g); groups.push(g);
    } else groups.push([r]);
  }
  return `<div class="xrs">${groups.map(g => xPanel(g, toks, self)).join("")}</div>`;
}
function xResult(r) { const o = xObj(r.out); return r.t === "trim" ? (xObj(r.in[0]).o || [])[0] : (o.o || [])[0]; }
function xChip(r, toks, self) {
  const o = xObj(r.i), ids = o.o || [], id = ids[0] || o.h, it = id ? xInfo(id) : { n: "?" };
  const name = r.n || (o.tn && (ids.length !== 1 || o.tag) ? o.tn : it.n);
  const pg = r.p || o.p || (ids.length === 1 ? it.p : null);
  const want = r.w ? `<span class="xw2">for ${xIco(r.w[0])}${r.w[1]} ${esc(xInfo(r.w[0]).n)}</span>` : "";
  const meta = [r.d ? esc(r.d) : "", want].filter(Boolean).join(" · ");
  const title = ids.length > 1 ? ` title="${esc((o.tn ? o.tn + ": " : "") + ids.map(i => xInfo(i).n).join(", "))}"` : "";
  const inner = `${id ? xIco(id) : ""}<span class="xb"><span class="xn">${xHl(name, toks)}</span>${meta ? ` <small>${meta}</small>` : ""}</span>`;
  return pg && pg !== self ? `<a class="xc" href="#${esc(pg)}" data-xgo="${esc(pg)}"${title}>${inner}</a>` : `<span class="xc"${title}>${inner}</span>`;
}
function xSources(rows, toks, self) {
  const by = new Map();
  for (const s of rows) { if (!by.has(s.k)) by.set(s.k, []); by.get(s.k).push(s); }
  return `<dl class="xsrc">${[...by].map(([k, list]) => `<dt>${esc(XKIND[k] || k)}</dt><dd>${list.map(s => {
    const want = s.w ? `<span class="xw2">for ${xIco(s.w[0])}${s.w[1]} ${esc(xInfo(s.w[0]).n)}</span>` : "";
    const d = [s.d ? esc(s.d) : "", want].filter(Boolean).join(" · ");
    return `<span class="xsi">${s.i ? xIco(s.i) : ""}${xLink(s.p, xHl(s.s, toks), "", self)}${d ? ` <small>${d}</small>` : ""}</span>`;
  }).join(", ")}</dd>`).join("")}</dl>`;
}
function xSections(e, toks) {
  const x = e.xr; if (!x) return "";
  let h = "";
  if (x.mk || x.src) h += `<section class="xsec"><h4>How to get it</h4>${x.mk ? xRecipes(x.mk, "out", toks, e.id) : ""}${x.src ? xSources(x.src, toks, e.id) : ""}${x.srcn ? `<p class="xcap">${esc(x.srcn)}</p>` : ""}</section>`;
  if (x.us) {
    const chips = [], seen = new Set();
    for (const i of x.us) {
      const r = XR.rc[i], id = xResult(r), key = r.t === "trim" ? "trim|" + id : id;
      if (seen.has(key)) continue; seen.add(key);
      chips.push(r.t === "trim" ? xChip({ i: id, n: xObj(r.out).pat || xObj(r.out).tn }, toks, e.id) : xChip({ i: id }, toks, e.id));
    }
    h += `<section class="xsec"><h4>Used in<b>${x.us.length} recipe${x.us.length > 1 ? "s" : ""}</b></h4><div class="xch">${chips.join("")}</div>` +
      `<details class="xmore" data-xfill="${esc(e.id)}"><summary>Show ${x.us.length > 1 ? "the recipes" : "the recipe"}</summary></details></section>`;
  }
  if (x.dr) {
    const open = x.dr.filter(g => !g.c), shut = x.dr.filter(g => g.c);
    for (const g of open) h += `<section class="xsec"><h4>${esc(g.h)}</h4>${g.self ? `<p class="xself">Drops itself${g.r[0].d ? " (" + esc(g.r[0].d) + ")" : ""}.</p>`
      : `<div class="xch">${g.r.map(r => xChip(r, toks, e.id)).join("")}</div>`}</section>`;
    if (shut.length) h += `<section class="xsec"><h4>Spoils and finds</h4>${shut.map(g => `<details class="xmore"><summary>${esc(g.h)}</summary><div class="xch">${g.r.map(r => xChip(r, toks, e.id)).join("")}</div></details>`).join("")}</section>`;
  }
  return h;
}
const xCard0 = card;
card = function (e, toks) {
  let h = xCard0(e, toks);
  if (XR.hi && XR.hi[e.id] != null) h = h.replace("<h3>", `<h3><span class="xi xq${XR.hi[e.id]}" aria-hidden="true"></span>`);
  const x = xSections(e, toks);
  return x ? h.replace(/<\/article>$/, x + "</article>") : h;
};
// The recipes something is used in, drawn when they are asked for
document.addEventListener("toggle", ev => {
  const d = ev.target;
  if (!d || !d.dataset || !d.dataset.xfill || !d.open || d.dataset.xdone) return;
  const e = byId.get(d.dataset.xfill); if (!e || !e.xr || !e.xr.us) return;
  d.insertAdjacentHTML("beforeend", xRecipes(e.xr.us, "in", tokens(state.q), e.id)); d.dataset.xdone = "1";
}, true);
// A link jumps to its page; the browser's back button comes back to where you were
function xGo(id) {
  try {
    // (the page as it is now, without the hash of a card it may have jumped to before: back comes here)
    history.replaceState({ xr: 1, q: state.q, cat: state.cat, view: state.view, y: window.scrollY }, "", location.pathname + location.search);
    history.pushState({ xr: 1, go: id }, "", "#" + id);
  } catch (err) {}
  show(id);
}
window.addEventListener("popstate", ev => {
  const s = ev.state;
  if (!s || !s.xr) return;
  if (s.go) { if (byId.has(s.go)) show(s.go); return; }
  state.q = s.q || ""; $("q").value = state.q; if (s.cat) state.cat = s.cat; state.view = s.view || "wiki"; render();
  window.scrollTo(0, s.y || 0);
});
let xTipEl = null, xTipT = 0;
function xTip(el) {
  if (!xTipEl) { xTipEl = document.createElement("div"); xTipEl.className = "xtip"; xTipEl.setAttribute("role", "tooltip"); document.body.appendChild(xTipEl); }
  xTipEl.textContent = el.getAttribute("title"); xTipEl.hidden = false;
  const r = el.getBoundingClientRect(), w = xTipEl.offsetWidth, h = xTipEl.offsetHeight;
  xTipEl.style.left = Math.max(8, Math.min(window.innerWidth - w - 8, r.left + r.width / 2 - w / 2)) + "px";
  xTipEl.style.top = (r.top > h + 12 ? r.top - h - 6 : r.bottom + 6) + "px";
  clearTimeout(xTipT); xTipT = setTimeout(() => { xTipEl.hidden = true; }, 2600);
}
document.addEventListener("click", ev => {
  const a = ev.target.closest("a[data-xgo]");
  if (a) { if (ev.metaKey || ev.ctrlKey || ev.shiftKey || ev.altKey) return; ev.preventDefault(); xGo(a.dataset.xgo); return; }
  const t = ev.target.closest("span.xs[title], span.xc[title]");
  if (t) xTip(t);
});
window.addEventListener("scroll", () => { if (xTipEl) xTipEl.hidden = true; }, { passive: true });
// xref:end
"""


def icon_css(icon_png):
    rules = [f".xq{n}{{background-image:url(data:image/png;base64,{base64.b64encode(png).decode()})}}" for png, n in
             sorted(icon_png.items(), key=lambda kv: kv[1])]
    return '<style id="xref-icons">\n' + "\n".join(rules) + "\n</style>\n"


def main():
    ap = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    ap.add_argument("wiki")
    ap.add_argument("out", nargs="?")
    ap.add_argument("--vanilla-items", help="directory of vanilla item textures (assets/minecraft/textures/item)")
    ap.add_argument("--vanilla-blocks", help="directory of vanilla block textures (assets/minecraft/textures/block)")
    ap.add_argument("-q", "--quiet", action="store_true")
    a = ap.parse_args()
    for d in (a.vanilla_items, a.vanilla_blocks):
        if d and not Path(d).is_dir():
            die(f"not a directory: {d}")
    page = Page(a.wiki)
    page.clean()
    b = Build(page, Icons(a.vanilla_items, a.vanilla_blocks))
    b.build()
    head, data, tail = page.parts()
    if CONTEXT_OLD not in tail:
        die("the page's context() is not as expected (the renderer changed?)")
    tail = tail.replace(CONTEXT_OLD, CONTEXT_NEW, 1)
    if tail.count(JS_ANCHOR) != 1:
        die("no unique 'render();\\nfromHash();' in the page's script")
    tail = tail.replace(JS_ANCHOR, JS + JS_ANCHOR, 1)
    k = head.find("</style>\n\n<div class=\"wrap\">")
    if k < 0:
        k = head.rfind("</style>")
        if k < 0:
            die("no </style> before the data")
    k += len("</style>\n")
    head = head[:k] + CSS + icon_css(b.icon_png) + head[k:]
    Path(a.out or a.wiki).write_text(head + data + tail, encoding="utf-8")
    if not a.quiet:
        mk = sum(1 for x in b.pages.values() if x.get("mk"))
        us = len(b.uses)
        src = sum(len(x.get("src", [])) for x in b.pages.values())
        dr = sum(len(x.get("dr", [])) for x in b.pages.values())
        print(f"recipes: {len(b.recipes)} " + ", ".join(f"{k} {v}" for k, v in sorted(b.counts.items())))
        print(f"pages with recipes: {mk}, with 'Used in': {us}, source rows: {src}, drop groups: {dr}, facts removed: {b.removed}")
        print(f"icons: {len(b.icon_png)} embedded; no icon for: {', '.join(b.icons.missing) or 'none'}")
        for u in dict.fromkeys(b.unmapped):
            print("unmapped:", u)


if __name__ == "__main__":
    main()
