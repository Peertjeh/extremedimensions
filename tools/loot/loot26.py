"""Brings loot tables to the 26.3 format: an entry's "functions" list becomes its "modifier" (one object, or a list),
each "function" key a "type"; a "conditions" list becomes one "condition" (an all_of when there are several), each
"condition" key a "type". Run from the repository root on the files to convert:

    python3 tools/loot/loot26.py src/main/resources/data/extremedimensions/loot_table
"""

import json
import os
import sys


def typed(d, key):
    d = dict(d)
    d["type"] = d.pop(key)
    return {"type": d["type"], **{k: convert(v) for k, v in d.items() if k != "type"}}


def convert(node):
    if isinstance(node, list):
        return [convert(n) for n in node]
    if not isinstance(node, dict):
        return node
    out = {}
    for k, v in node.items():
        if k == "functions":
            mods = [typed(f, "function") for f in v]
            out["modifier"] = mods[0] if len(mods) == 1 else mods
        elif k == "conditions":
            conds = [typed(c, "condition") for c in v]
            out["condition"] = conds[0] if len(conds) == 1 else {"type": "minecraft:all_of", "terms": conds}
        else:
            out[k] = convert(v)
    return out


def main():
    count = 0
    for root in sys.argv[1:]:
        for dirpath, _, files in os.walk(root):
            for name in files:
                if name.endswith(".json"):
                    path = os.path.join(dirpath, name)
                    body = json.load(open(path))
                    new = convert(body)
                    if new != body:
                        with open(path, "w") as f:
                            json.dump(new, f, indent=2)
                            f.write("\n")
                        count += 1
    print(count, "tables converted")


if __name__ == "__main__":
    main()
