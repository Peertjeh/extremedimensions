"""Writes the Seam's chest loot tables (26.3 format: "modifier" and "condition"). Run from the repository root:

    python3 tools/loot/seam_chests.py

Five tables, from what the Thinned kept in every house and tower to what the Fray swallowed:

    seam_storeroom   a storeroom's barrel or chest (furnished houses, tower floors, halls, the loom, a stone ring)
    seam_loom        the barrel by a great loom
    seam_village     the barrel of lore in a village of the Thinned
    seam_reliquary   the reliquary at the head of a great tower's stair
    fray_tangle      a snarl in the Fray

The book pools that tools/binding/enchantments.py adds (knitting, soulward, the Fray's cursed gear) are written here as
well, in the same shape, so running that script after this one replaces them rather than doubling them.
"""

import json
import os

NS = "extremedimensions"
DIR = os.path.join("src/main/resources/data", NS, "loot_table/chests")


def item(name, lo=None, hi=None, weight=None, mods=None):
    e = {"type": "minecraft:item", "name": name}
    m = []
    if lo is not None:
        m.append({"type": "minecraft:set_count", "count": lo if hi is None or hi == lo else {"type": "minecraft:uniform", "min": lo, "max": hi}})
    m += mods or []
    if m:
        e["modifier"] = m[0] if len(m) == 1 else m
    if weight is not None:
        e["weight"] = weight
    return e


def rolls(lo, hi):
    return lo if lo == hi else {"type": "minecraft:uniform", "min": lo, "max": hi}


def pool(entries, n=1, chance=None):
    p = {"rolls": n if isinstance(n, (int, dict)) else n, "entries": entries if isinstance(entries, list) else [entries]}
    if chance is not None:
        p["condition"] = {"type": "minecraft:random_chance", "chance": chance}
    return p


def book(enchants):
    return item("minecraft:book", mods=[{"type": "minecraft:set_enchantments", "enchantments": enchants}])


RANDOM_BOOK = {"type": "minecraft:enchant_randomly", "options": "#minecraft:on_random_loot"}


def levels(lo, hi):
    return {"type": "minecraft:enchant_with_levels", "levels": {"type": "minecraft:uniform", "min": lo, "max": hi},
            "options": "#minecraft:on_random_loot"}


SILK, PAGE, FRAG = NS + ":seam_silk", NS + ":wanderers_page", NS + ":tether_fragment"
WORN = {"type": "minecraft:set_damage", "damage": {"type": "minecraft:uniform", "min": 0.4, "max": 0.8}}

TABLES = {
    # What any household of the Thinned put by: thread, light, a little food, and now and then a coin of the tribe's
    "seam_storeroom": [
        pool([
            item("minecraft:string", 2, 8, 6), item("minecraft:paper", 2, 6, 5), item("minecraft:candle", 1, 4, 5),
            item(SILK, 1, 3, 5), item("minecraft:bone", 1, 5, 4), item("minecraft:soul_torch", 2, 6, 4),
            item("minecraft:amethyst_shard", 1, 5, 4), item("minecraft:glow_berries", 2, 8, 4), item("minecraft:white_wool", 1, 4, 3),
            item("minecraft:bread", 1, 3, 3), item("minecraft:book", 1, 3, 3), item("minecraft:coal", 2, 8, 3),
            item("minecraft:ink_sac", 1, 3, 2), item("minecraft:ender_pearl", 1, None, 1), item("minecraft:experience_bottle", 1, 2, 1),
        ], rolls(2, 5)),
        pool(item("minecraft:emerald", 1, 5), chance=0.4),
        pool(item(FRAG), chance=0.12),
        pool(item(PAGE), chance=0.06),
    ],
    # By a great loom: silk above all, and what the weavers worked with
    "seam_loom": [
        pool(item(SILK, 3, 8)),
        pool(item(PAGE), chance=0.6),
        pool(item(FRAG, 1, 2), chance=0.4),
        pool([
            item("minecraft:string", 3, 9, 5), item("minecraft:amethyst_shard", 2, 6, 4), item("minecraft:white_wool", 2, 5, 3),
            item("minecraft:soul_torch", 2, 5, 3), item("minecraft:candle", 1, 3, 3), item("minecraft:paper", 2, 5, 3),
            item("minecraft:glow_berries", 2, 6, 3), item("minecraft:echo_shard", 1, 2, 2), item("minecraft:experience_bottle", 1, 3, 2),
            item("minecraft:book", weight=2, mods=[RANDOM_BOOK]),
        ], rolls(2, 4)),
        pool(item("minecraft:emerald", 2, 7), chance=0.55),
        pool(item(NS + ":weaver_armor_trim_smithing_template"), chance=0.12),
        pool(book({NS + ":knitting": 1}), chance=0.05),
    ],
    # The barrel of lore in a village: a household's whole store, and the pages it kept
    "seam_village": [
        pool(item(SILK, 4, 10)),
        pool(item(PAGE), chance=0.8),
        pool(item(FRAG, 1, 2), chance=0.55),
        pool([
            item("minecraft:echo_shard", 1, 3, 3), item("minecraft:amethyst_shard", 2, 8, 3), item("minecraft:bread", 2, 5, 3),
            item("minecraft:string", 2, 6, 2), item("minecraft:soul_torch", 2, 6, 2), item("minecraft:candle", 1, 4, 2),
            item("minecraft:glow_berries", 2, 8, 2), item("minecraft:book", 1, 3, 2), item("minecraft:ender_pearl", 1, 2, 2),
            item("minecraft:coal", 3, 10, 2), item("minecraft:experience_bottle", 1, 4, 2), item("minecraft:gold_ingot", 1, 4, 2),
            item("minecraft:book", weight=2, mods=[RANDOM_BOOK]), item("minecraft:golden_apple", 1, None, 1),
        ], rolls(3, 5)),
        pool(item("minecraft:emerald", 3, 10), chance=0.8),
        pool(item(NS + ":veil_wing"), chance=0.04),
        pool(book({NS + ":soulward": 1}), chance=0.06),
    ],
    # At the head of a great tower's stair: what the Weavers would not leave lying about
    "seam_reliquary": [
        pool(item(SILK, 6, 14)),
        pool(item(PAGE), chance=0.95),
        pool(item(FRAG, 1, 3), chance=0.75),
        pool(item("minecraft:echo_shard", 2, 4)),
        pool([
            item("minecraft:amethyst_shard", 3, 10, 3), item("minecraft:experience_bottle", 2, 6, 3), item("minecraft:candle", 2, 5, 2),
            item("minecraft:soul_torch", 2, 6, 2), item("minecraft:glow_berries", 3, 8, 2), item("minecraft:ender_pearl", 1, 3, 2),
            item("minecraft:gold_ingot", 2, 6, 2), item("minecraft:diamond", 1, 3, 2), item("minecraft:golden_apple", 1, 2, 2),
            item("minecraft:book", weight=3, mods=[levels(20, 30)]), item(NS + ":tether_cord", 1, None, 1),
        ], rolls(3, 6)),
        pool(item("minecraft:emerald", 6, 16), chance=0.9),
        pool(book({NS + ":veilsight": 1}), chance=0.15),
        pool(item(NS + ":tetherite_upgrade_smithing_template"), chance=0.05),
        pool(item(NS + ":weaver_armor_trim_smithing_template"), chance=0.2),
        pool(item(NS + ":tetherite_scrap"), chance=0.1),
        pool(item(NS + ":mending_cloth"), chance=0.03),
        pool(item("minecraft:enchanted_golden_apple"), chance=0.02),
        pool(book({NS + ":knitting": 1}), chance=0.08),
    ],
    # A snarl of the Fray: the richest of all, and exactly one page (the tests count on it)
    "fray_tangle": [
        pool(item(SILK, 4, 14)),
        pool(item(PAGE)),
        pool(item(FRAG, 1, 2), chance=0.8),
        pool(item("minecraft:echo_shard", 1, 3)),
        pool([
            item("minecraft:amethyst_shard", 3, 10, 3), item("minecraft:experience_bottle", 2, 6, 3), item("minecraft:emerald", 4, 12, 3),
            item("minecraft:soul_torch", 2, 6, 2), item("minecraft:glow_berries", 3, 8, 2), item("minecraft:ender_pearl", 1, 3, 2),
            item("minecraft:gold_ingot", 2, 6, 2), item("minecraft:diamond", 1, 3, 2), item("minecraft:golden_apple", 1, 2, 2),
            item("minecraft:book", weight=3, mods=[levels(25, 35)]), item(NS + ":tether_cord", 1, None, 1),
        ], rolls(3, 6)),
        pool(book({NS + ":veilsight": 1}), chance=0.08),
        pool(item(NS + ":tetherite_upgrade_smithing_template"), chance=0.08),
        pool(item(NS + ":fray_armor_trim_smithing_template"), chance=0.15),
        pool(item(NS + ":tetherite_scrap"), chance=0.15),
        pool(item(NS + ":mending_cloth"), chance=0.05),
        pool(item("minecraft:enchanted_golden_apple"), chance=0.03),
        pool(item("minecraft:diamond_pickaxe", mods=[{"type": "minecraft:set_enchantments", "enchantments": {
            NS + ":fraying_curse": 1, "minecraft:efficiency": 4, NS + ":veinseeker": 1}}, WORN]), chance=0.1),
        pool(item("minecraft:diamond_chestplate", mods=[{"type": "minecraft:set_enchantments", "enchantments": {
            NS + ":starved_curse": 1, "minecraft:unbreaking": 3, NS + ":wardweave": 3}}, WORN]), chance=0.08),
    ],
}


def main():
    for name, pools in TABLES.items():
        table = {"type": "minecraft:chest", "pools": pools, "random_sequence": NS + ":chests/" + name}
        with open(os.path.join(DIR, name + ".json"), "w", encoding="utf-8") as f:
            json.dump(table, f, indent=2)
            f.write("\n")
    print(len(TABLES), "tables written")


if __name__ == "__main__":
    main()
