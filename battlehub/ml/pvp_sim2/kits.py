"""What each fighter has: weapon, armor, effects and the things it carries, drawn per episode from the mod's kits
(config/KitConfig.java, the Bed Wars shop, the SkyWars and Hunger Games loot) and The Bridge: Chaos' effects.

A kit is a dict of plain numbers (see FIELDS); Fighters keeps them as arrays (n, K).
"""
import numpy as np

# attack damage (with the player's 1), attack speed, axe (disables shields), mace (smash); strength adds 3 a level
# to every hit, also with the fist
WEAPONS = {
    "fist": (1.0, 4.0, False, False),
    "stick": (1.0, 4.0, False, False),
    "wooden_sword": (4.0, 1.6, False, False),
    "golden_sword": (4.0, 1.6, False, False),
    "stone_sword": (5.0, 1.6, False, False),
    "iron_sword": (6.0, 1.6, False, False),
    "diamond_sword": (7.0, 1.6, False, False),
    "netherite_sword": (8.0, 1.6, False, False),
    "wooden_axe": (7.0, 0.8, True, False),
    "stone_axe": (9.0, 0.8, True, False),
    "iron_axe": (9.0, 0.9, True, False),
    "diamond_axe": (9.0, 1.0, True, False),
    "netherite_axe": (10.0, 1.0, True, False),
    "mace": (6.0, 0.6, False, True),
}

# armor points of helmet, chestplate, leggings, boots; toughness and knockback resistance per piece
ARMOR = {
    "leather": ((1, 3, 2, 1), 0.0, 0.0),
    "golden": ((2, 5, 3, 1), 0.0, 0.0),
    "chainmail": ((2, 5, 4, 1), 0.0, 0.0),
    "iron": ((2, 6, 5, 2), 0.0, 0.0),
    "diamond": ((3, 8, 6, 3), 2.0, 0.0),
    "netherite": ((3, 8, 6, 3), 3.0, 0.1),
}

FIELDS = ["atk_damage", "strength", "atk_speed", "sharp", "kb_level", "axe", "mace", "armor", "toughness", "kb_resist", "resistance",
          "walk_speed", "jump_power", "gravity", "scale", "reach", "arrows", "bow_power", "food_items", "gapples", "pearls",
          "winds", "shield", "saturation_effect"]


def base():
    return {"atk_damage": 1.0, "strength": 0.0, "atk_speed": 4.0, "sharp": 0.0, "kb_level": 0, "axe": False, "mace": False, "armor": 0.0,
            "toughness": 0.0, "kb_resist": 0.0, "resistance": 0, "walk_speed": 0.1, "jump_power": 0.42, "gravity": 0.08,
            "scale": 1.0, "reach": 3.0, "arrows": 0, "bow_power": -1, "food_items": 0, "gapples": 0, "pearls": 0, "winds": 0,
            "shield": False, "saturation_effect": False}


def weapon(k, name, sharpness=0, knockback=0):
    d, s, axe, mace = WEAPONS[name]
    k["atk_damage"], k["atk_speed"], k["axe"], k["mace"] = d, s, axe, mace
    k["sharp"] = 0.5 * sharpness + 0.5 if sharpness > 0 else 0.0
    k["kb_level"] = knockback
    return k


def armor(k, *materials):
    """One material for all four pieces, or one per piece (None: no piece)."""
    mats = materials * 4 if len(materials) == 1 else materials
    pts = tough = kbr = 0.0
    for slot, m in enumerate(mats):
        if m is None:
            continue
        p, t, r = ARMOR[m]
        pts += p[slot]
        tough += t
        kbr += r
    k["armor"], k["toughness"], k["kb_resist"] = pts, tough, kbr
    return k


def bow(k, arrows, power=0):
    k["arrows"], k["bow_power"] = arrows, power
    return k


# ---------------------------------------------------------------------------------------------- the mod's kits

def sword(rng):
    k = weapon(base(), "diamond_sword")
    k["gapples"] = 3
    return armor(k, "iron")


def axe(rng):
    k = weapon(base(), "diamond_axe")
    k["shield"] = True
    return armor(k, "iron")


def mace(rng):
    k = weapon(base(), "mace")
    k["winds"], k["gapples"] = 16, 2
    return armor(k, "diamond")


def bow_duel(rng):
    k = bow(weapon(base(), "iron_sword"), 32, 1)
    return armor(k, "chainmail")


def koth(rng):
    k = weapon(base(), "stone_sword")
    k["saturation_effect"] = True
    return armor(k, "chainmail")


def ctf(rng):
    k = bow(weapon(base(), "iron_sword"), 16)
    k["gapples"], k["saturation_effect"] = 1, True
    return armor(k, "leather")


def lms(rng):
    k = bow(weapon(base(), "iron_sword"), 16)
    k["gapples"], k["food_items"] = 2, 8
    return armor(k, "iron")


def ffa(rng):
    k = bow(weapon(base(), "diamond_sword"), 16, 1)
    k["gapples"], k["saturation_effect"] = 1, True
    return armor(k, "iron")


def bridge(rng):
    # one arrow that comes back now and then in the game; a few in the simulation
    k = bow(weapon(base(), "iron_sword"), int(rng.integers(1, 4)))
    k["gapples"] = 8
    return armor(k, "leather")


def boxing(rng):
    k = weapon(base(), "diamond_sword", sharpness=1)
    k["walk_speed"] = 0.1 * 1.4
    k["resistance"], k["saturation_effect"] = 5, True
    return k


def build_uhc(rng):
    k = bow(weapon(base(), "diamond_sword", sharpness=2), 32, 2)
    k["gapples"], k["food_items"] = 6, 16
    return armor(k, "diamond")


def sumo(rng):
    k = base()
    k["resistance"], k["saturation_effect"] = 5, True
    return k


def bedwars(rng):
    """The start kit or something from the shop: a better sword or the knockback stick, better legs and boots,
    golden apples, pearls, a bow."""
    stage = int(rng.integers(0, 4))
    k = weapon(base(), ["wooden_sword", "stone_sword", "iron_sword", "diamond_sword"][stage])
    if rng.random() < 0.15:
        weapon(k, "stick", knockback=1)
    legs = ["leather", "chainmail", "iron", "diamond"][int(rng.integers(0, stage + 1))]
    armor(k, "leather", "leather", legs, legs)
    k["saturation_effect"] = True
    if rng.random() < 0.3:
        k["gapples"] = int(rng.integers(1, 4))
    if rng.random() < 0.15:
        k["pearls"] = int(rng.integers(1, 3))
    if rng.random() < 0.15:
        bow(k, int(rng.integers(4, 17)))
    return k


def loot(rng):
    """SkyWars and the Hunger Games: whatever was in the chests."""
    k = base()
    w = rng.choice(["fist", "wooden_sword", "stone_sword", "iron_sword", "diamond_sword", "stone_axe", "iron_axe",
                    "diamond_axe", "golden_sword"], p=[0.08, 0.12, 0.2, 0.22, 0.12, 0.08, 0.08, 0.05, 0.05])
    weapon(k, str(w), sharpness=int(rng.integers(1, 3)) if rng.random() < 0.15 else 0,
           knockback=1 if rng.random() < 0.05 else 0)
    pieces = []
    for _ in range(4):
        pieces.append(None if rng.random() < 0.35 else str(rng.choice(["leather", "golden", "chainmail", "iron", "diamond"],
                                                                     p=[0.3, 0.15, 0.2, 0.25, 0.1])))
    armor(k, *pieces)
    if rng.random() < 0.3:
        bow(k, int(rng.integers(3, 25)), int(rng.integers(0, 2)))
    if rng.random() < 0.5:
        k["food_items"] = int(rng.integers(1, 12))
    if rng.random() < 0.3:
        k["gapples"] = int(rng.integers(1, 4))
    if rng.random() < 0.2:
        k["pearls"] = int(rng.integers(1, 3))
    if rng.random() < 0.1:
        k["winds"] = int(rng.integers(2, 9))
    if rng.random() < 0.1:
        k["shield"] = True
    return k


def chaos(k, rng):
    """The Bridge: Chaos' effects on one fighter: speed or slowness, strength, jump boost, size, gravity, knockback,
    resistance."""
    if rng.random() < 0.35:
        lvl = int(rng.integers(1, 3))
        k["walk_speed"] *= 1 + 0.2 * lvl
    elif rng.random() < 0.2:
        k["walk_speed"] *= 1 - 0.15 * int(rng.integers(1, 3))
    if rng.random() < 0.25:
        k["strength"] += 3.0 * int(rng.integers(1, 3))
    if rng.random() < 0.25:
        k["jump_power"] += 0.1 * int(rng.integers(1, 4))
    if rng.random() < 0.2:
        k["scale"] = float(rng.choice([0.5, 0.75, 1.5, 2.0]))
    if rng.random() < 0.15:
        k["gravity"] = float(rng.choice([0.04, 0.06, 0.1, 0.12]))
    if rng.random() < 0.2:
        k["kb_level"] += int(rng.integers(1, 3))
    if rng.random() < 0.1:
        k["resistance"] = max(k["resistance"], int(rng.integers(1, 3)))
    return k


# how the kits are dealt: the same for everyone (duels, the team modes) or each its own (loot, the shop)
FAMILIES = {
    "sword": (sword, True), "axe": (axe, True), "mace": (mace, True), "bow": (bow_duel, True), "koth": (koth, True),
    "ctf": (ctf, True), "lms": (lms, True), "ffa": (ffa, True), "bridge": (bridge, True), "boxing": (boxing, True),
    "build_uhc": (build_uhc, True), "sumo": (sumo, True), "bedwars": (bedwars, False), "loot": (loot, False),
}


# where the chests or the shop can give any item: only there Scenario.extra_items deals more of them
ITEM_KITS = ("loot", "bedwars")


def extra_items(k, rng):
    """More of what chests and the shop can give, on top of a kit, for learning to use items (Scenario.extra_items):
    a bow with arrows, food, golden apples, pearls, wind charges, a shield, each with its own chance."""
    if k["bow_power"] < 0 and rng.random() < 0.6:
        bow(k, int(rng.integers(8, 25)))
    if rng.random() < 0.7:
        k["food_items"] = max(k["food_items"], int(rng.integers(4, 17)))
    if rng.random() < 0.5:
        k["gapples"] = max(k["gapples"], int(rng.integers(1, 5)))
    if rng.random() < 0.6:
        k["pearls"] = max(k["pearls"], int(rng.integers(1, 5)))
    if rng.random() < 0.4:
        k["winds"] = max(k["winds"], int(rng.integers(2, 9)))
    if rng.random() < 0.2:
        k["shield"] = True
    return k


def deal(family, count, rng, chaos_p=0.0):
    """Kits for `count` fighters of one arena."""
    make, same = FAMILIES[family]
    first = make(rng)
    kits = [dict(first) if same else (first if i == 0 else make(rng)) for i in range(count)]
    for k in kits:
        if rng.random() < chaos_p:
            chaos(k, rng)
    return kits
