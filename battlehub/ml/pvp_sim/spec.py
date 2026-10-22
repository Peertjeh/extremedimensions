"""The combat spec (ml/spec/combat_v1.json): the one definition of observations and actions, shared with the mod."""
import hashlib
import json
import os

import numpy as np

SPEC_PATH = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "spec", "combat_v1.json")


class Spec:
    def __init__(self, path=SPEC_PATH):
        with open(path, "rb") as f:
            raw = f.read()
        self.sha256 = hashlib.sha256(raw).hexdigest()
        d = json.loads(raw.decode("utf-8"))
        self.raw = d
        self.name = d["name"]
        self.version = d["version"]
        self.tick_hz = d["tick_hz"]
        self.decision_ticks = d["decision_ticks"]
        self.reach = d["reach"]
        self.eye_height = d["eye_height"]
        self.chest_height = d["chest_height"]
        self.hitbox_width = d["hitbox"]["width"]
        self.hitbox_height = d["hitbox"]["height"]
        self.view_half_angle = d["view_half_angle"]
        self.ray_directions = d["ray_directions"]
        self.ray_max = d["ray_max"]
        self.obs_names = [o["name"] for o in d["observation"]]
        self.obs_scale = np.array([o["scale"] for o in d["observation"]], dtype=np.float64)
        self.obs_clip = np.array([o["clip"] for o in d["observation"]], dtype=np.float64)
        self.action_names = [a["name"] for a in d["actions"]]
        self.action_values = [list(a["values"]) for a in d["actions"]]
        self.action_nvec = np.array([len(v) for v in self.action_values], dtype=np.int64)

    @property
    def obs_dim(self):
        return len(self.obs_names)

    def index(self, name):
        return self.obs_names.index(name)

    def action_index(self, name):
        return self.action_names.index(name)


SPEC = Spec()
