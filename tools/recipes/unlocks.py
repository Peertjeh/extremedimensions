"""Writes the advancements that put the mod's recipes in the recipe book.

Each recipe is unlocked the way vanilla unlocks its own: by holding one of its ingredients (a recipe already known is
simply given again, which changes nothing). A recipe that uses the mod's own materials is unlocked by any of those; one
made only of vanilla things by any of its ingredients. The lodestone (the mod's own recipe for it) replaces vanilla's
advancement for it. Run from the repository root:

    python3 tools/recipes/unlocks.py
"""

import json
import os
import shutil

DATA = "src/main/resources/data"
RECIPES = os.path.join(DATA, "extremedimensions", "recipe")
OUT = os.path.join(DATA, "extremedimensions", "advancement", "recipes")
FOLDER = {"building": "building_blocks", "blocks": "building_blocks", "misc": "misc"}
# Recipes learned by a deed rather than by holding an ingredient: the game grants them (the Weaver's Loom, when an
# Elder falls; see Tapestry.earn).
GRANTED = {"weavers_loom"}


def ingredients(recipe):
    kind = recipe["type"].split(":")[1]
    if kind in ("stonecutting", "smelting"):
        return [recipe["ingredient"]]
    if kind == "crafting_shapeless":
        return recipe["ingredients"]
    if kind == "crafting_shaped":
        used = "".join(recipe["pattern"])
        return [recipe["key"][k] for k in recipe["key"] if k in used]
    raise ValueError("no ingredients for " + recipe["type"])


def keys(recipe):
    found = []
    for item in ingredients(recipe):
        if isinstance(item, str) and item not in found:
            found.append(item)
    own = [i for i in found if i.startswith("extremedimensions:")]
    return own or found


def granted(recipe_id):
    return {
        "parent": "minecraft:recipes/root",
        "criteria": {"granted": {"trigger": "minecraft:impossible"}},
        "requirements": [["granted"]],
        "rewards": {"recipes": [recipe_id]},
    }


def advancement(recipe_id, items):
    criteria = {}
    for item in items:
        criteria["has_" + item.split(":")[1]] = {
            "trigger": "minecraft:inventory_changed",
            "conditions": {"items": [{"items": item}]},
        }
    return {
        "parent": "minecraft:recipes/root",
        "criteria": criteria,
        "requirements": [list(criteria)],
        "rewards": {"recipes": [recipe_id]},
    }


def write(path, body):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(body, f, indent=2)
        f.write("\n")


def main():
    if os.path.isdir(OUT):
        shutil.rmtree(OUT)
    count = 0
    for name in sorted(os.listdir(RECIPES)):
        recipe = json.load(open(os.path.join(RECIPES, name)))
        stem = name[:-5]
        folder = FOLDER.get(recipe.get("category", "misc"), "misc")
        body = granted("extremedimensions:" + stem) if stem in GRANTED else advancement("extremedimensions:" + stem, keys(recipe))
        write(os.path.join(OUT, folder, stem + ".json"), body)
        count += 1
    lodestone = json.load(open(os.path.join(DATA, "minecraft", "recipe", "lodestone.json")))
    write(os.path.join(DATA, "minecraft", "advancement", "recipes", "misc", "lodestone.json"),
          advancement("minecraft:lodestone", keys(lodestone)))
    print(count + 1, "recipe advancements written")


if __name__ == "__main__":
    main()
