"""Hush masonry: the JSON for every block of the family (blockstates, models, item models, loot tables, recipes, tags),
written from one table so the family stays consistent. Textures come from build.py.

usage: python3 tools/hush/assets.py
"""
import json
import os

ROOT = os.path.join(os.path.dirname(__file__), "..", "..")
ASSETS = os.path.join(ROOT, "src/client/resources/assets/extremedimensions")
DATA = os.path.join(ROOT, "src/main/resources/data")
NS = "extremedimensions"

# Full blocks: id -> texture (pillars have their own model)
FULL = ["hush_stone", "cobbled_hush_stone", "polished_hush_stone", "hush_bricks", "cracked_hush_bricks",
        "mossy_hush_bricks", "hush_tiles", "chiseled_hush_bricks", "pale_hush_bricks", "polished_pale_hush_stone",
        "silked_hush_bricks"]
PILLARS = ["hush_pillar"]
# Families with a slab, stairs and a wall: base block -> stem of the shapes' ids
SHAPED = {
    "hush_stone": "hush_stone",
    "cobbled_hush_stone": "cobbled_hush_stone",
    "polished_hush_stone": "polished_hush_stone",
    "hush_bricks": "hush_brick",
    "mossy_hush_bricks": "mossy_hush_brick",
    "hush_tiles": "hush_tile",
    "pale_hush_bricks": "pale_hush_brick",
    "polished_pale_hush_stone": "polished_pale_hush_stone",
}
# What the stonecutter makes of each stone (and of everything those make in turn)
CUTS = {
    "hush_stone": ["cobbled_hush_stone", "polished_hush_stone", "hush_bricks", "hush_tiles", "chiseled_hush_bricks", "hush_pillar"],
    "cobbled_hush_stone": ["polished_hush_stone", "hush_bricks", "hush_tiles", "chiseled_hush_bricks", "hush_pillar"],
    "polished_hush_stone": ["hush_bricks", "hush_tiles", "chiseled_hush_bricks", "hush_pillar"],
    "hush_bricks": ["hush_tiles", "chiseled_hush_bricks"],
    "polished_pale_hush_stone": ["pale_hush_bricks"],
}


def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(obj, f, indent=2)
        f.write("\n")


def ref(name):
    return f"{NS}:{name}"


def block_model(name, parent, textures):
    write(f"{ASSETS}/models/block/{name}.json", {"parent": parent, "textures": textures})


def blockstate(name, obj):
    write(f"{ASSETS}/blockstates/{name}.json", obj)


def item(name, model=None):
    write(f"{ASSETS}/items/{name}.json", {"model": {"type": "minecraft:model", "model": ref("block/" + (model or name))}})


def loot(name, slab=False):
    entry = {"type": "minecraft:item", "name": ref(name)}
    pool = {"rolls": 1, "entries": [entry], "conditions": [{"condition": "minecraft:survives_explosion"}]}
    if slab:
        entry["functions"] = [{"function": "minecraft:set_count", "count": 2, "add": False,
                               "conditions": [{"condition": "minecraft:block_state_property", "block": ref(name), "properties": {"type": "double"}}]}]
    write(f"{DATA}/{NS}/loot_table/blocks/{name}.json", {"type": "minecraft:block", "pools": [pool], "random_sequence": ref("blocks/" + name)})


def shaped(file, pattern, key, result, count):
    write(f"{DATA}/{NS}/recipe/{file}.json", {"type": "minecraft:crafting_shaped", "category": "building", "pattern": pattern,
                                              "key": key, "result": {"id": ref(result), "count": count}})


def shapeless(file, ingredients, result, count):
    write(f"{DATA}/{NS}/recipe/{file}.json", {"type": "minecraft:crafting_shapeless", "category": "building",
                                              "ingredients": ingredients, "result": {"id": ref(result), "count": count}})


def cutting(base, result, count=1):
    write(f"{DATA}/{NS}/recipe/{result}_from_{base}_stonecutting.json",
          {"type": "minecraft:stonecutting", "ingredient": ref(base), "result": {"id": ref(result), "count": count}})


def smelting(file, ingredient, result):
    write(f"{DATA}/{NS}/recipe/{file}.json", {"type": "minecraft:smelting", "category": "blocks", "ingredient": ref(ingredient),
                                              "result": {"id": ref(result)}, "experience": 0.1, "cookingtime": 200})


def stairs_state(name):
    base = {"east": 0, "south": 90, "west": 180, "north": 270}
    variants = {}
    for facing, y0 in base.items():
        for half in ("bottom", "top"):
            for shape in ("straight", "inner_left", "inner_right", "outer_left", "outer_right"):
                model = ref("block/" + name + ("" if shape == "straight" else "_inner" if shape.startswith("inner") else "_outer"))
                y = y0
                if half == "bottom" and shape.endswith("left"):
                    y -= 90
                if half == "top" and shape.endswith("right"):
                    y += 90
                v = {"model": model}
                if half == "top":
                    v["x"] = 180
                y %= 360
                if y:
                    v["y"] = y
                if "x" in v or "y" in v:
                    v["uvlock"] = True
                variants[f"facing={facing},half={half},shape={shape}"] = v
    return {"variants": variants}


def wall_state(name):
    parts = [{"when": {"up": "true"}, "apply": {"model": ref(f"block/{name}_post")}}]
    for side, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
        for height, suffix in (("low", "_side"), ("tall", "_side_tall")):
            apply = {"model": ref(f"block/{name}{suffix}"), "uvlock": True}
            if y:
                apply["y"] = y
            parts.append({"when": {side: height}, "apply": apply})
    return {"multipart": parts}


def main():
    names = []
    for b in FULL:
        tex = ref("block/" + b)
        block_model(b, "minecraft:block/cube_all", {"all": tex})
        blockstate(b, {"variants": {"": {"model": ref("block/" + b)}}})
        item(b)
        loot(b)
        names.append(b)
    for b in PILLARS:
        textures = {"end": ref(f"block/{b}_top"), "side": ref(f"block/{b}")}
        block_model(b, "minecraft:block/cube_column", textures)
        block_model(b + "_horizontal", "minecraft:block/cube_column_horizontal", textures)
        blockstate(b, {"variants": {
            "axis=y": {"model": ref("block/" + b)},
            "axis=z": {"model": ref(f"block/{b}_horizontal"), "x": 90},
            "axis=x": {"model": ref(f"block/{b}_horizontal"), "x": 90, "y": 90}}})
        item(b)
        loot(b)
        names.append(b)
    slabs, stairs, walls = [], [], []
    for base, stem in SHAPED.items():
        tex = ref("block/" + base)
        three = {"bottom": tex, "top": tex, "side": tex}
        slab, stair, wall = stem + "_slab", stem + "_stairs", stem + "_wall"
        block_model(slab, "minecraft:block/slab", three)
        block_model(slab + "_top", "minecraft:block/slab_top", three)
        blockstate(slab, {"variants": {"type=bottom": {"model": ref("block/" + slab)}, "type=top": {"model": ref(f"block/{slab}_top")},
                                       "type=double": {"model": ref("block/" + base)}}})
        item(slab)
        loot(slab, slab=True)
        block_model(stair, "minecraft:block/stairs", three)
        block_model(stair + "_inner", "minecraft:block/inner_stairs", three)
        block_model(stair + "_outer", "minecraft:block/outer_stairs", three)
        blockstate(stair, stairs_state(stair))
        item(stair)
        loot(stair)
        for suffix, parent in (("_post", "template_wall_post"), ("_side", "template_wall_side"), ("_side_tall", "template_wall_side_tall"),
                               ("_inventory", "wall_inventory")):
            block_model(wall + suffix, "minecraft:block/" + parent, {"wall": tex})
        blockstate(wall, wall_state(wall))
        item(wall, wall + "_inventory")
        loot(wall)
        slabs.append(slab)
        stairs.append(stair)
        walls.append(wall)
        names += [slab, stair, wall]
        # Crafting the shapes, and the stonecutter's cleaner yield
        shaped(slab, ["###"], {"#": ref(base)}, slab, 6)
        shaped(stair, ["#  ", "## ", "###"], {"#": ref(base)}, stair, 4)
        shaped(wall, ["###", "###"], {"#": ref(base)}, wall, 6)
        cutting(base, slab, 2)
        cutting(base, stair)
        cutting(base, wall)

    # The stonecutter takes a stone to everything below it on the ladder, shapes included
    for base, results in CUTS.items():
        for r in results:
            cutting(base, r)
            if r in SHAPED:
                stem = SHAPED[r]
                cutting(base, stem + "_slab", 2)
                cutting(base, stem + "_stairs")
                cutting(base, stem + "_wall")

    # Crafting the family from the stone up
    shaped("hush_bricks", ["##", "##"], {"#": ref("hush_stone")}, "hush_bricks", 4)
    shaped("polished_hush_stone", ["##", "##"], {"#": ref("cobbled_hush_stone")}, "polished_hush_stone", 4)
    shaped("hush_tiles", ["##", "##"], {"#": ref("hush_bricks")}, "hush_tiles", 4)
    shaped("chiseled_hush_bricks", ["#", "#"], {"#": ref("hush_brick_slab")}, "chiseled_hush_bricks", 1)
    shaped("hush_pillar", ["#", "#"], {"#": ref("polished_hush_stone")}, "hush_pillar", 2)
    shaped("pale_hush_bricks", ["###", "#B#", "###"], {"#": ref("hush_bricks"), "B": "minecraft:bone_meal"}, "pale_hush_bricks", 8)
    shaped("pale_hush_bricks_from_polished", ["##", "##"], {"#": ref("polished_pale_hush_stone")}, "pale_hush_bricks", 4)
    shaped("polished_pale_hush_stone", ["###", "#B#", "###"], {"#": ref("polished_hush_stone"), "B": "minecraft:bone_meal"}, "polished_pale_hush_stone", 8)
    shaped("silked_hush_bricks", [" # ", "#S#", " # "], {"#": ref("hush_bricks"), "S": ref("seam_silk")}, "silked_hush_bricks", 4)
    shapeless("mossy_hush_bricks", [ref("hush_bricks"), "minecraft:moss_block"], "mossy_hush_bricks", 1)
    shapeless("mossy_hush_bricks_from_vine", [ref("hush_bricks"), "minecraft:vine"], "mossy_hush_bricks", 1)
    smelting("cracked_hush_bricks", "hush_bricks", "cracked_hush_bricks")

    # Tags: mined with a pickaxe (the old blocks and the vein too), and known as slabs, stairs and walls
    pick = [ref(n) for n in names] + [ref("seam_vein"), ref("ember_lodestone"), ref("void_lodestone")]
    write(f"{DATA}/minecraft/tags/block/mineable/pickaxe.json", {"replace": False, "values": sorted(set(pick))})
    for kind, ids in (("slabs", slabs), ("stairs", stairs), ("walls", walls)):
        write(f"{DATA}/minecraft/tags/block/{kind}.json", {"replace": False, "values": [ref(n) for n in ids]})
        write(f"{DATA}/minecraft/tags/item/{kind}.json", {"replace": False, "values": [ref(n) for n in ids]})
    for kind in ("stone_tool_materials", "stone_crafting_materials"):
        write(f"{DATA}/minecraft/tags/item/{kind}.json", {"replace": False, "values": [ref("cobbled_hush_stone")]})
    print(len(names), "blocks:", ", ".join(names))


if __name__ == "__main__":
    main()
