"""The combat spec v2 (ml/spec/combat_v2.json): what the model sees and does in every melee mode, shared with the mod."""
import os

from pvp_sim.spec import Spec

SPEC2_PATH = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "spec", "combat_v2.json")


class Spec2(Spec):
    """v1's fields plus the terrain rules of the rays, the hands and the radius that counts as near."""

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
        self.hands = list(self.raw["hands"])


SPEC2 = Spec2()
