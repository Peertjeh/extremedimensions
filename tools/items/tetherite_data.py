"""Writes the data and model files of Tetherite and the Seam's armor trims (3.3). Run from the repository root:

    python3 tools/items/tetherite_data.py

Data: the ore's and the Spindle's loot (the Spindle gives back what it holds), smelting, the ingot, the upgrade and trim
templates and their copies, the smithing upgrades and trims, the trim materials and patterns, the item and block tags,
the advancements. Assets: the item model definitions (armor icons show any trim, vanilla's or the Seam's), the block
models of the ore and the Spindle (its spool grows with what is loaded and turns a step with every turn), the equipment
asset, the trim pattern textures' palette metadata, and the items atlas entries that colour vanilla's trim overlays in
the Seam's two materials. The textures are drawn by tools/items/tetherite.py.
"""
import json
import os

ROOT = os.path.join(os.path.dirname(__file__), "..", "..")
DATA = os.path.join(ROOT, "src/main/resources/data")
ASSETS = os.path.join(ROOT, "src/client/resources/assets")
NS = "extremedimensions"

TOOLS = ["sword", "shovel", "pickaxe", "axe", "hoe", "spear"]
ARMOR = ["helmet", "chestplate", "leggings", "boots"]
VANILLA_TRIMS = ["quartz", "iron", "netherite", "redstone", "copper", "gold", "emerald", "diamond", "lapis", "amethyst", "resin"]
SEAM_TRIMS = ["tetherite", "seam_silk"]
PATTERNS = {"weaver": "hush_bricks", "fray": "cobbled_hush_stone"}


def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(obj, f, indent=2)
        f.write("\n")


def d(*parts):
    return os.path.join(DATA, *parts)


def a(*parts):
    return os.path.join(ASSETS, *parts)


def item(name, count=None):
    out = {"id": f"{NS}:{name}"}
    if count:
        out["count"] = count
    return out


# ----------------------------------------------------------------------------------------------------------- data

def recipes():
    r = lambda name: d(NS, "recipe", name + ".json")
    for kind in ("smelting", "blasting"):
        write(r(f"tetherite_scrap{'' if kind == 'smelting' else '_from_blasting'}"), {
            "type": f"minecraft:{kind}", "category": "misc", "cookingtime": 200 if kind == "smelting" else 100, "experience": 2.0,
            "ingredient": f"{NS}:tetherite_ore", "result": item("tetherite_scrap")})
    write(r("tetherite_ingot"), {
        "type": "minecraft:crafting_shapeless", "category": "misc", "group": "tetherite_ingot",
        "ingredients": [f"{NS}:tetherite_scrap"] * 4 + [f"{NS}:tether_cord"] * 4, "result": item("tetherite_ingot")})
    write(r("spindle"), {
        "type": "minecraft:crafting_shaped", "category": "misc",
        "key": {"S": f"{NS}:seam_silk", "E": "minecraft:end_rod", "P": f"{NS}:polished_hush_stone_slab"},
        "pattern": ["S S", " E ", "PPP"], "result": item("spindle")})
    copies = {"tetherite_upgrade_smithing_template": "hush_stone"}
    copies.update({f"{p}_armor_trim_smithing_template": base for p, base in PATTERNS.items()})
    for template, base in copies.items():
        write(r(template), {
            "type": "minecraft:crafting_shaped", "category": "misc",
            "key": {"#": "minecraft:diamond", "C": f"{NS}:{base}", "S": f"{NS}:{template}"},
            "pattern": ["#S#", "#C#", "###"], "result": item(template, 2)})
    for piece in TOOLS + ARMOR:
        write(r(f"tetherite_{piece}_smithing"), {
            "type": "minecraft:smithing_transform", "template": f"{NS}:tetherite_upgrade_smithing_template",
            "base": f"minecraft:netherite_{piece}", "addition": f"#{NS}:tetherite_tool_materials",
            "result": item(f"tetherite_{piece}")})
    for p in PATTERNS:
        write(r(f"{p}_armor_trim_smithing_template_smithing_trim"), {
            "type": "minecraft:smithing_trim", "template": f"{NS}:{p}_armor_trim_smithing_template",
            "base": "#minecraft:trimmable_armor", "addition": "#minecraft:trim_materials", "pattern": f"{NS}:{p}"})


def trims():
    colors = {"tetherite": "#46E2D6", "seam_silk": "#C484FA"}
    for m in SEAM_TRIMS:
        write(d(NS, "trim_material", m + ".json"), {
            "description": {"color": colors[m], "translate": f"trim_material.{NS}.{m}"}, "palette_id": f"{NS}:trim/{m}"})
    for p in PATTERNS:
        write(d(NS, "trim_pattern", p + ".json"), {
            "asset_id": f"{NS}:{p}", "decal": False, "description": {"translate": f"trim_pattern.{NS}.{p}"}})


def tags():
    t = lambda ns, kind, name: d(ns, "tags", kind, name + ".json")
    def add(values, path=None):
        # the mod's own entries join whatever its file for the tag already lists
        have = []
        if path and os.path.exists(path):
            with open(path) as f:
                have = json.load(f).get("values", [])
        return {"replace": False, "values": have + [v for v in values if v not in have]}
    gear = [f"{NS}:tetherite_{p}" for p in TOOLS + ARMOR]
    write(t(NS, "item", "tetherite_tool_materials"), {"values": [f"{NS}:tetherite_ingot"]})
    write(t(NS, "item", "repairs_tetherite_armor"), {"values": [f"{NS}:tetherite_ingot"]})
    write(t(NS, "item", "tetherite_gear"), {"values": gear})
    vanilla = {"swords": "sword", "shovels": "shovel", "pickaxes": "pickaxe", "axes": "axe", "hoes": "hoe", "spears": "spear",
               "head_armor": "helmet", "chest_armor": "chestplate", "leg_armor": "leggings", "foot_armor": "boots"}
    for tag, piece in vanilla.items():
        write(t("minecraft", "item", tag), add([f"{NS}:tetherite_{piece}"], t("minecraft", "item", tag)))
    for kind, tag, values in (("item", "trim_materials", [f"{NS}:tetherite_ingot", f"{NS}:seam_silk"]),
                              ("item", "beacon_payment_items", [f"{NS}:tetherite_ingot"]),
                              ("block", "mineable/pickaxe", [f"{NS}:tetherite_ore", f"{NS}:spindle"]),
                              ("block", "needs_diamond_tool", [f"{NS}:tetherite_ore"])):
        write(t("minecraft", kind, tag), add(values, t("minecraft", kind, tag)))


def loot():
    l = lambda name: d(NS, "loot_table", "blocks", name + ".json")
    write(l("tetherite_ore"), {
        "type": "minecraft:block",
        "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": f"{NS}:tetherite_ore"}],
                   "condition": {"type": "minecraft:survives_explosion"}}],
        "random_sequence": f"{NS}:blocks/tetherite_ore"})

    def held(prop, item_id, most):
        # what the Spindle held comes back with it, as many as its block state says (as a candle gives its candles)
        state = lambda n: {"type": "minecraft:match_block", "blocks": f"{NS}:spindle", "state": {prop: str(n)}}
        return {"rolls": 1, "condition": {"type": "minecraft:inverted", "term": state(0)},
                "entries": [{"type": "minecraft:item", "name": item_id,
                             "modifier": [{"type": "minecraft:set_count", "condition": state(n), "count": n} for n in range(2, most + 1)]}]}

    write(l("spindle"), {
        "type": "minecraft:block",
        "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": f"{NS}:spindle"}],
                   "condition": {"type": "minecraft:survives_explosion"}},
                  held("fragments", f"{NS}:tether_fragment", 8), held("silk", f"{NS}:seam_silk", 4)],
        "random_sequence": f"{NS}:blocks/spindle"})


def advancements():
    def adv(name, parent, icon, criteria, frame="task", announce=True, requirements=None):
        obj = {
            "parent": f"{NS}:{parent}",
            "display": {"icon": {"id": icon}, "title": {"translate": f"advancements.{NS}.{name}.title"},
                        "description": {"translate": f"advancements.{NS}.{name}.description"},
                        "frame": frame, "show_toast": True, "announce_to_chat": announce, "hidden": False},
            "criteria": criteria}
        if requirements:
            obj["requirements"] = requirements  # any one of a group will do
        write(d(NS, "advancement", name + ".json"), obj)

    has = lambda *ids: {"trigger": "minecraft:inventory_changed", "conditions": {"items": [{"items": i} for i in ids]}}
    adv("tetherite_found", "fray_entered", f"{NS}:tetherite_ore", {"found": has(f"{NS}:tetherite_ore")}, "goal")
    adv("cord_spun", "tetherite_found", f"{NS}:tether_cord", {"spun": has(f"{NS}:tether_cord")})
    adv("tetherite_ingot", "cord_spun", f"{NS}:tetherite_ingot", {"ingot": has(f"{NS}:tetherite_ingot")})
    adv("tetherite_full", "tetherite_ingot", f"{NS}:tetherite_chestplate",
        {"armor": has(*[f"{NS}:tetherite_{p}" for p in ARMOR])}, "challenge")
    adv("seam_trimmed", "seam_entered", f"{NS}:weaver_armor_trim_smithing_template",
        {p: {"trigger": "minecraft:recipe_crafted",
             "conditions": {"recipes": f"{NS}:{p}_armor_trim_smithing_template_smithing_trim"}} for p in PATTERNS},
        requirements=[list(PATTERNS)])


# ----------------------------------------------------------------------------------------------------------- assets

def models():
    m = lambda name: a(NS, "models", "item", name + ".json")
    i = lambda name: a(NS, "items", name + ".json")
    plain = ["tetherite_scrap", "tetherite_ingot", "tether_cord", "tetherite_upgrade_smithing_template"] + \
            [f"{p}_armor_trim_smithing_template" for p in PATTERNS]
    for name in plain:
        write(m(name), {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/{name}"}})
        write(i(name), {"model": {"type": "minecraft:model", "model": f"{NS}:item/{name}"}})
    for t in TOOLS:
        name = f"tetherite_{t}"
        if t == "spear":
            write(m(name), {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/{name}"}})
            write(m(name + "_in_hand"), {"parent": "minecraft:item/spear_in_hand", "textures": {"layer0": f"{NS}:item/{name}_in_hand"}})
            write(i(name), {"model": {"type": "minecraft:select", "property": "minecraft:display_context",
                                      "cases": [{"when": ["gui", "ground", "fixed", "on_shelf"],
                                                 "model": {"type": "minecraft:model", "model": f"{NS}:item/{name}"}}],
                                      "fallback": {"type": "minecraft:model", "model": f"{NS}:item/{name}_in_hand"}},
                            "swap_animation_scale": 1.95})
        else:
            write(m(name), {"parent": "minecraft:item/handheld", "textures": {"layer0": f"{NS}:item/{name}"}})
            write(i(name), {"model": {"type": "minecraft:model", "model": f"{NS}:item/{name}"}})
    for piece in ARMOR:
        name = f"tetherite_{piece}"
        write(m(name), {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/{name}"}})
        cases = []
        for mat in VANILLA_TRIMS + SEAM_TRIMS:
            # Tetherite trimmed with Tetherite takes the darker palette, as netherite does with its own
            overlay = "tetherite_darker" if mat == "tetherite" else mat
            ns = "minecraft" if mat in VANILLA_TRIMS else NS
            model = f"{name}_{mat}_trim"
            write(m(model), {"parent": "minecraft:item/generated",
                             "textures": {"layer0": f"{NS}:item/{name}", "layer1": f"minecraft:trims/items/{piece}_trim_{overlay}"}})
            cases.append({"when": f"{ns}:{mat}", "model": {"type": "minecraft:model", "model": f"{NS}:item/{model}"}})
        write(i(name), {"model": {"type": "minecraft:select", "property": "minecraft:trim_material", "cases": cases,
                                  "fallback": {"type": "minecraft:model", "model": f"{NS}:item/{name}"}}})
    # the blocks' items
    for name in ("tetherite_ore", "spindle"):
        write(i(name), {"model": {"type": "minecraft:model", "model": f"{NS}:block/{name}" + ("_item" if name == "spindle" else "")}})


def blocks():
    b = lambda name: a(NS, "models", "block", name + ".json")
    write(a(NS, "blockstates", "tetherite_ore.json"), {"variants": {"": {"model": f"{NS}:block/tetherite_ore"}}})
    write(b("tetherite_ore"), {"parent": "minecraft:block/cube_all", "textures": {"all": f"{NS}:block/tetherite_ore"}})

    tex = {"base": f"{NS}:block/spindle_base", "rod": f"{NS}:block/spindle_rod", "thread": f"{NS}:block/spindle_thread",
           "silk": f"{NS}:block/spindle_silk", "particle": f"{NS}:block/spindle_base"}

    def box(frm, to, t, angle=0, faces=("north", "south", "east", "west", "up", "down")):
        el = {"from": frm, "to": to, "faces": {}}
        for f in faces:
            if f in ("up", "down"):
                uv = [frm[0], frm[2], to[0], to[2]]
            elif f in ("north", "south"):
                uv = [frm[0], 16 - to[1], to[0], 16 - frm[1]]
            else:
                uv = [frm[2], 16 - to[1], to[2], 16 - frm[1]]
            el["faces"][f] = {"uv": uv, "texture": "#" + t}
        if angle:
            el["rotation"] = {"origin": [8, 8, 8], "axis": "y", "angle": angle}
        return el

    # the frame: a hush-stone base with a whorl, the rod, a cap
    frame = [box([2, 0, 2], [14, 2, 14], "base"), box([4, 2, 4], [12, 3, 12], "base"),
             box([7.5, 3, 7.5], [8.5, 16, 8.5], "rod"), box([6.5, 15, 6.5], [9.5, 16, 9.5], "rod")]
    write(b("spindle"), {"parent": "minecraft:block/block", "textures": tex, "elements": frame})
    # its spool, by how much is loaded, at the four steps a turn goes round
    sizes = {1: 2.0, 2: 3.0, 3: 4.0}
    for size, half in sizes.items():
        for step, angle in enumerate((0, 22.5, 45, -22.5)):
            spool = [box([8 - half, 5, 8 - half], [8 + half, 5 + 2 * size + 3, 8 + half], "thread", angle)]
            write(b(f"spindle_spool_{size}_{step}"), {"parent": "minecraft:block/block", "textures": tex, "elements": spool})
    write(b("spindle_silk"), {"parent": "minecraft:block/block", "textures": tex,
                             "elements": [box([5, 3, 5], [11, 4.5, 11], "silk")]})
    write(b("spindle_item"), {"parent": f"{NS}:block/spindle", "textures": tex,
                             "elements": frame + [box([6, 5, 6], [10, 12, 10], "thread")]})

    loaded = {1: "1|2", 2: "3|4|5", 3: "6|7|8"}
    steps = {0: "0|4|8|12", 1: "1|5|9|13", 2: "2|6|10|14", 3: "3|7|11|15"}
    multipart = [{"apply": {"model": f"{NS}:block/spindle"}}]
    for size, frag in loaded.items():
        for step, spun in steps.items():
            multipart.append({"when": {"fragments": frag, "spun": spun}, "apply": {"model": f"{NS}:block/spindle_spool_{size}_{step}"}})
    multipart.append({"when": {"silk": "1|2|3|4"}, "apply": {"model": f"{NS}:block/spindle_silk"}})
    write(a(NS, "blockstates", "spindle.json"), {"multipart": multipart})


def equipment():
    write(a(NS, "equipment", "tetherite.json"), {
        "layers": {"humanoid": [{"texture": f"{NS}:tetherite"}], "humanoid_leggings": [{"texture": f"{NS}:tetherite"}]},
        "trim_overrides": [{"palette": f"{NS}:trim/tetherite_darker", "when": {"material": f"{NS}:tetherite"}}]})
    # vanilla's trim overlays on item icons, in the Seam's materials
    write(a("minecraft", "atlases", "items.json"), {"sources": [{
        "type": "minecraft:paletted_permutations", "palette_key": "minecraft:trim_base",
        "permutations": {"tetherite": f"{NS}:trim/tetherite", "tetherite_darker": f"{NS}:trim/tetherite_darker",
                         "seam_silk": f"{NS}:trim/seam_silk"},
        "textures": [f"minecraft:trims/items/{p}_trim" for p in ARMOR]}]})
    for p in PATTERNS:
        for layer in ("humanoid", "humanoid_leggings"):
            write(a(NS, "textures", "trims", "entity", layer, p + ".png.mcmeta"), {"palette": {"base_palette": "minecraft:trim_base"}})


VANILLA_ARMOR = [(m, p) for m in ("leather", "chainmail", "iron", "golden", "diamond", "netherite", "copper") for p in ARMOR] + [("turtle", "helmet")]
LEATHER_TINT = [{"type": "minecraft:dye", "default": -6265536}]


def vanilla_icons():
    """Vanilla's armor icons, as vanilla defines them, with the Seam's two materials added to their trim cases."""
    for mat, piece in VANILLA_ARMOR:
        name = f"{mat}_{piece}"
        leather = mat == "leather"

        def ref(model):
            out = {"type": "minecraft:model", "model": model}
            if leather:
                out["tints"] = LEATHER_TINT
            return out

        cases = [{"model": ref(f"minecraft:item/{name}_{t}_trim"), "when": f"minecraft:{t}"} for t in VANILLA_TRIMS]
        for t in SEAM_TRIMS:
            model = f"{name}_{t}_trim"
            layers = {"layer0": f"minecraft:item/{name}"}
            if leather:
                layers["layer1"] = f"minecraft:item/{name}_overlay"
            layers["layer2" if leather else "layer1"] = f"minecraft:trims/items/{piece}_trim_{t}"
            write(a(NS, "models", "item", model + ".json"), {"parent": "minecraft:item/generated", "textures": layers})
            cases.append({"model": ref(f"{NS}:item/{model}"), "when": f"{NS}:{t}"})
        write(a("minecraft", "items", name + ".json"), {"model": {"type": "minecraft:select", "cases": cases,
                                                                   "fallback": ref(f"minecraft:item/{name}"),
                                                                   "property": "minecraft:trim_material"}})


def main():
    recipes()
    trims()
    tags()
    loot()
    advancements()
    models()
    blocks()
    equipment()
    vanilla_icons()


if __name__ == "__main__":
    main()
