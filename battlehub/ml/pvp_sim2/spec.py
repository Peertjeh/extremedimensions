"""The combat specs v2 and v3 (ml/spec/combat_v2.json, combat_v3.json): what the model sees and does in every melee mode,
shared with the mod. v3 keeps v2's observations in their place and adds the team mate, a third enemy, memory and the
history (earlier decisions' values, `of` and `ago`)."""
import os

from pvp_sim.spec import Spec

SPEC_DIR = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "spec")
SPEC2_PATH = os.path.join(SPEC_DIR, "combat_v2.json")
SPEC3_PATH = os.path.join(SPEC_DIR, "combat_v3.json")


class Spec2(Spec):
    """v1's fields plus the terrain rules of the rays, the hands and the radius that counts as near; for v3 also the
    radius for team mates and the history: (index, index of `of`, ago) per observation from an earlier decision."""

    def __init__(self, path=SPEC2_PATH):
        super().__init__(path)
        t = self.raw["terrain"]
        self.sample = t["sample"]
        self.depth = t["depth"]
        self.step_up = t["step_up"]
        self.jump_over = t["jump_over"]
        self.drop = t["drop"]
        self.scan_from = t["scan_from"]
        self.near_enemies = self.raw["near_enemies"]
        self.near_allies = self.raw.get("near_allies", self.near_enemies)
        self.hands = list(self.raw["hands"])
        self.history = [(k, self.index(o["of"]), int(o["ago"])) for k, o in enumerate(self.raw["observation"]) if "of" in o]
        self.max_ago = max([ago for _, _, ago in self.history], default=0)
        self.pause = self.raw.get("history", {}).get("pause", 0)
        self.current = [k for k, o in enumerate(self.raw["observation"]) if "of" not in o]


SPEC2 = Spec2()
SPEC3 = Spec2(SPEC3_PATH)
