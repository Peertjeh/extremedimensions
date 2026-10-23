"""Writes ml/spec/chaos_effects.json: the effects of the mod's Chaos events (src/main/java/net/battlehub/core/chaos/
ChaosCatalog.java) as the simulation's kits take them, so pvp_sim2/kits.chaos plays the game's own events.

Each event that lasts (seconds > 0) and changes how a fighter moves, how big it is or how hard it hits becomes a
profile, as the game computes the attributes (multiplying modifiers multiply):
  walk_speed  a factor: speed +20% a level, slowness -15% a level, attr movement_speed total
  jump        jump power 0.42 times attr jump_strength total, plus 0.1 a level of jump boost
  scale       attr scale total;  gravity  attr gravity total (slow falling halves it: it only slows the fall)
  strength    +3 a level;  resistance  its level;  attack_speed  attr total;  reach  attr entity_interaction_range add
weighted by the event's weight times its seconds (how much of the time it is on).

    python tools/chaos_effects.py          # write the file
    python tools/chaos_effects.py --check  # fail when the file is not what the catalog gives now
"""
import hashlib
import json
import os
import re
import sys

ML = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CATALOG = os.path.normpath(os.path.join(ML, "..", "src", "main", "java", "net", "battlehub", "core", "chaos", "ChaosCatalog.java"))
OUT = os.path.join(ML, "spec", "chaos_effects.json")
EVENT = re.compile(r'e\(l, "(\w+)", (\w+), (\d+), (\d+), "([^"]*)"\);')


def profile(actions):
    """The profile of one event's actions, or None when it changes none of the fields."""
    speed = jump_mult = scale = gravity = attack = 1.0
    jump_add = strength = reach = 0.0
    resistance = 0
    touched = False
    for a in actions.split(";"):
        w = a.split()
        if len(w) < 3:
            continue
        verb, what = w[0], w[1].replace("minecraft:", "")
        if verb == "effect":
            amp = int(w[2]) + 1
            if what == "speed":
                speed *= 1 + 0.2 * amp
            elif what == "slowness":
                speed *= max(0.0, 1 - 0.15 * amp)
            elif what == "strength":
                strength += 3.0 * amp
            elif what == "jump_boost":
                jump_add += 0.1 * amp
            elif what == "resistance":
                resistance = max(resistance, amp)
            elif what == "slow_falling":
                gravity *= 0.5
            else:
                continue
            touched = True
        elif verb == "attr" and len(w) >= 4:
            op, v = w[2], float(w[3])
            if what == "entity_interaction_range" and op == "add":
                reach += v
            elif op != "total":
                continue
            elif what == "movement_speed":
                speed *= 1 + v
            elif what == "jump_strength":
                jump_mult *= 1 + v
            elif what == "scale":
                scale *= 1 + v
            elif what == "gravity":
                gravity *= 1 + v
            elif what == "attack_speed":
                attack *= 1 + v
            else:
                continue
            touched = True
    if not touched:
        return None
    r = lambda x: round(x, 6)
    return {"walk_speed": r(max(0.0, speed)), "jump_mult": r(max(0.0, jump_mult)), "jump_add": r(jump_add), "scale": r(scale),
            "gravity": r(gravity), "strength": r(strength), "resistance": resistance, "attack_speed": r(attack), "reach": r(reach)}


def build():
    with open(CATALOG, "rb") as fh:
        raw = fh.read()
    events = []
    for m in EVENT.finditer(raw.decode("utf-8")):
        eid, cat, seconds, weight, actions = m.group(1), m.group(2), int(m.group(3)), int(m.group(4)), m.group(5)
        p = profile(actions)
        if p is None or seconds <= 0:
            continue
        p.update({"id": eid, "category": cat, "seconds": seconds, "weight": weight})
        events.append(p)
    return {"doc": "Chaos events that last and change a fighter's body (tools/chaos_effects.py from ChaosCatalog.java); "
                   "pvp_sim2/kits.chaos draws them by weight times seconds.",
            "catalog_sha256": hashlib.sha256(raw).hexdigest(), "events": events}


def main():
    data = build()
    text = json.dumps(data, indent=1) + "\n"
    if "--check" in sys.argv:
        with open(OUT, encoding="utf-8") as fh:
            if fh.read() != text:
                sys.exit("spec/chaos_effects.json is not what ChaosCatalog.java gives: run tools/chaos_effects.py")
        print("chaos_effects.json is up to date (%d events)" % len(data["events"]))
        return
    with open(OUT, "w", encoding="utf-8") as fh:
        fh.write(text)
    print("wrote", OUT, len(data["events"]), "events")


if __name__ == "__main__":
    main()
