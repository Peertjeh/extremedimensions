"""The enchantments of 3.3 as data: their files, the tags that place them (table, treasure, curse, exclusive sets) and
the books and cursed gear the Seam's chests hold. What they do beyond their data is in Charms.java.

usage: python3 tools/binding/enchantments.py
"""
import json
import os

ROOT = os.path.join(os.path.dirname(__file__), "..", "..")
DATA = os.path.join(ROOT, "src/main/resources/data")
NS = "extremedimensions"


def cost(base, per):
    return {"base": base, "per_level_above_first": per}


def linear(base, per):
    return {"type": "minecraft:linear", "base": base, "per_level_above_first": per}


def from_wound(kind):
    """A hit dealt by a creature of the wound (kind: wound_born or wound_elders), unless it bypasses armour wholly."""
    return {"type": "minecraft:damage_source_properties", "predicate": {
        "source_entity": {"minecraft:entity_type": "#%s:%s" % (NS, kind)},
        "tags": [{"expected": False, "id": "#minecraft:bypasses_invulnerability"}]}}


# id: (supported, primary, slots, max level, weight, anvil, min cost, max cost, exclusive set, effects)
ENCHANTMENTS = {
    "unravelling": dict(supported="#minecraft:enchantable/fire_aspect", primary="#minecraft:enchantable/melee_weapon",
                        slots=["mainhand"], max_level=3, weight=2, anvil=4, min=cost(10, 15), max=cost(50, 15),
                        exclusive="minecraft:fire_aspect", effects={
                            "minecraft:post_attack": [{
                                "affected": "victim", "enchanted": "attacker",
                                "effect": {"type": "minecraft:apply_mob_effect", "to_apply": NS + ":unravelling",
                                           "min_duration": linear(2.0, 2.0), "max_duration": linear(2.0, 2.0),
                                           "min_amplifier": 0.0, "max_amplifier": 0.0},
                                "requirements": {"type": "minecraft:damage_source_properties", "predicate": {"is_direct": True}}}]}),
    "reaping": dict(supported="#%s:enchantable/woundbane" % NS, primary="#minecraft:enchantable/melee_weapon",
                    slots=["mainhand"], max_level=2, weight=2, anvil=4, min=cost(15, 12), max=cost(45, 12)),
    "tethershot": dict(supported="#%s:enchantable/tethershot" % NS, slots=["hand"], max_level=1, weight=2, anvil=4,
                       min=cost(20, 0), max=cost(50, 0), exclusive="minecraft:punch"),
    "veinseeker": dict(supported="#minecraft:pickaxes", slots=["mainhand"], max_level=1, weight=2, anvil=4,
                       min=cost(15, 0), max=cost(45, 0)),
    "anchored": dict(supported="#%s:enchantable/anchored" % NS, slots=["chest", "legs"], max_level=3, weight=2, anvil=4,
                     min=cost(10, 10), max=cost(40, 10), effects={
                         "minecraft:attributes": [{"amount": linear(0.05, 0.05), "attribute": "minecraft:knockback_resistance",
                                                   "id": NS + ":enchantment.anchored", "operation": "add_value"}]}),
    "wardweave": dict(supported="#minecraft:enchantable/armor", slots=["armor"], max_level=4, weight=5, anvil=2,
                      min=cost(5, 8), max=cost(13, 8), exclusive="#minecraft:exclusive_set/armor", effects={
                          "minecraft:damage_protection": [
                              {"effect": {"type": "minecraft:add", "value": linear(2.0, 2.0)}, "requirements": from_wound("wound_born")},
                              {"effect": {"type": "minecraft:add", "value": linear(2.0, 2.0)}, "requirements": from_wound("wound_elders")}]}),
    "sated": dict(supported="#minecraft:enchantable/head_armor", slots=["head"], max_level=2, weight=2, anvil=4,
                  min=cost(10, 12), max=cost(40, 12)),
    "driftfall": dict(supported="#minecraft:enchantable/foot_armor", slots=["feet"], max_level=1, weight=2, anvil=4,
                      min=cost(15, 0), max=cost(45, 0)),
    "hushed_steps": dict(supported="#minecraft:enchantable/foot_armor", slots=["feet"], max_level=2, weight=2, anvil=4,
                         min=cost(12, 12), max=cost(42, 12)),
    "foresight": dict(supported="#%s:enchantable/tether" % NS, slots=["any"], max_level=1, weight=2, anvil=4,
                      min=cost(20, 0), max=cost(50, 0), exclusive="#%s:exclusive_set/tether" % NS),
    "soulward": dict(supported="#minecraft:enchantable/head_armor", slots=["head"], max_level=1, weight=1, anvil=8,
                     min=cost(20, 0), max=cost(50, 0)),
    "knitting": dict(supported="#minecraft:enchantable/durability", slots=["any"], max_level=1, weight=2, anvil=4,
                     min=cost(25, 0), max=cost(75, 0), exclusive="minecraft:mending"),
    "steadfast": dict(supported="#minecraft:enchantable/leg_armor", slots=["legs"], max_level=2, weight=2, anvil=4,
                      min=cost(12, 12), max=cost(42, 12)),
    "fraying_curse": dict(supported="#minecraft:enchantable/durability", slots=["any"], max_level=1, weight=1, anvil=8,
                          min=cost(25, 0), max=cost(50, 0)),
    "starved_curse": dict(supported="#minecraft:enchantable/armor", slots=["armor"], max_level=1, weight=1, anvil=8,
                          min=cost(25, 0), max=cost(50, 0)),
}

TREASURE = ["knitting", "fraying_curse", "starved_curse"]
CURSES = ["fraying_curse", "starved_curse"]

ITEM_TAGS = {
    "enchantable/tethershot": ["#minecraft:enchantable/bow", "#minecraft:enchantable/crossbow"],
    "enchantable/anchored": ["#minecraft:enchantable/chest_armor", "#minecraft:enchantable/leg_armor"],
}


def write(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2, sort_keys=True)
        f.write("\n")


def merge_tag(path, values, replace_flag=True):
    """Adds values to a tag file (kept in order, nothing doubled)."""
    data = {"values": []}
    if os.path.exists(path):
        data = json.load(open(path, encoding="utf-8"))
    for v in values:
        if v not in data["values"]:
            data["values"].append(v)
    if replace_flag:
        data = {"replace": False, "values": data["values"]}
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2)
        f.write("\n")


def enchantment(name, e):
    out = {
        "anvil_cost": e["anvil"],
        "description": {"translate": "enchantment.%s.%s" % (NS, name)},
        "effects": e.get("effects", {}),
        "max_cost": e["max"],
        "max_level": e["max_level"],
        "min_cost": e["min"],
        "slots": e["slots"],
        "supported_items": e["supported"],
        "weight": e["weight"],
    }
    if "primary" in e:
        out["primary_items"] = e["primary"]
    if "exclusive" in e:
        out["exclusive_set"] = e["exclusive"]
    return out


# Books and cursed gear in the Seam's chests: (chest, item, enchantments, chance, more modifiers)
CHEST_LOOT = [
    ("seam_reliquary", "minecraft:book", {NS + ":knitting": 1}, 0.08, []),
    ("seam_loom", "minecraft:book", {NS + ":knitting": 1}, 0.05, []),
    ("seam_village", "minecraft:book", {NS + ":soulward": 1}, 0.06, []),
    ("old_hoard", "minecraft:book", {NS + ":foresight": 1}, 0.08, []),
    # what the Fray kept is tempting, and it wears out in the Seam
    ("fray_tangle", "minecraft:diamond_pickaxe", {NS + ":fraying_curse": 1, "minecraft:efficiency": 4, NS + ":veinseeker": 1}, 0.10,
     [{"type": "minecraft:set_damage", "damage": {"type": "minecraft:uniform", "min": 0.4, "max": 0.8}}]),
    ("fray_tangle", "minecraft:diamond_chestplate", {NS + ":starved_curse": 1, "minecraft:unbreaking": 3, NS + ":wardweave": 3}, 0.08,
     [{"type": "minecraft:set_damage", "damage": {"type": "minecraft:uniform", "min": 0.4, "max": 0.8}}]),
]


def chest_loot():
    for chest, item, enchants, chance, more in CHEST_LOOT:
        path = os.path.join(DATA, NS, "loot_table/chests/%s.json" % chest)
        table = json.load(open(path, encoding="utf-8"))
        mods = [{"type": "minecraft:set_enchantments", "enchantments": enchants}] + more
        pool = {"rolls": 1, "entries": [{"type": "minecraft:item", "name": item,
                                         "modifier": mods[0] if len(mods) == 1 else mods}],
                "condition": {"type": "minecraft:random_chance", "chance": chance}}
        # one pool per (item, enchantments); run again, it is replaced, not doubled
        table["pools"] = [p for p in table["pools"] if not same_pool(p, item, enchants)] + [pool]
        with open(path, "w", encoding="utf-8") as f:
            json.dump(table, f, indent=2)
            f.write("\n")


def same_pool(pool, item, enchants):
    for entry in pool.get("entries", []):
        if entry.get("name") != item:
            continue
        mods = entry.get("modifier", [])
        for m in mods if isinstance(mods, list) else [mods]:
            if m.get("type") == "minecraft:set_enchantments" and m.get("enchantments") == enchants:
                return True
    return False


def main():
    for name, e in ENCHANTMENTS.items():
        write(os.path.join(DATA, NS, "enchantment", name + ".json"), enchantment(name, e))
    ids = [NS + ":" + n for n in ENCHANTMENTS]
    tags = os.path.join(DATA, "minecraft/tags/enchantment")
    merge_tag(os.path.join(tags, "treasure.json"), [NS + ":" + n for n in TREASURE])
    merge_tag(os.path.join(tags, "non_treasure.json"), [i for i in ids if i.split(":")[1] not in TREASURE])
    merge_tag(os.path.join(tags, "curse.json"), [NS + ":" + n for n in CURSES])
    merge_tag(os.path.join(tags, "exclusive_set/armor.json"), [NS + ":wardweave"])
    merge_tag(os.path.join(DATA, NS, "tags/enchantment/exclusive_set/tether.json"), [NS + ":foresight"], replace_flag=False)
    for tag, values in ITEM_TAGS.items():
        merge_tag(os.path.join(DATA, NS, "tags/item", tag + ".json"), values, replace_flag=False)
    chest_loot()
    print("ok", len(ENCHANTMENTS))


if __name__ == "__main__":
    main()
