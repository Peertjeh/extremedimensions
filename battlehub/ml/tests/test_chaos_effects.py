"""The Chaos effects the simulation plays: the mod's own catalog (spec/chaos_effects.json, tools/chaos_effects.py from
ChaosCatalog.java) and the profiles the game's fight logs had on (spec/effects_game.json, train/simfit.py --effects)."""
import json

import numpy as np

from pvp_sim2 import kits
from tools import chaos_effects


def test_the_file_is_what_the_catalog_gives_and_reads_the_games_attributes():
    with open(chaos_effects.OUT, encoding="utf-8") as fh:
        assert json.load(fh) == chaos_effects.build(), "run tools/chaos_effects.py"
    ev = {e["id"]: e for e in chaos_effects.build()["events"]}
    assert ev["super_jump"]["jump_add"] == 0.4                                 # jump boost 3: four levels
    assert ev["berserk"]["walk_speed"] == 0.85 and ev["berserk"]["strength"] == 6.0
    assert ev["freeze"]["walk_speed"] == 0.0 and ev["freeze"]["jump_mult"] == 0.0
    assert ev["mice"]["walk_speed"] == 1.3 and ev["mice"]["scale"] == 0.6
    assert ev["long_arms"]["reach"] == 2.5 and ev["tank_mode"]["resistance"] == 3
    assert "pearl" not in ev and "crumble" not in ev                            # items and the world are no body


def test_chaos_draws_the_games_profiles_and_the_catalogs_events():
    assert kits.GAME_EFFECTS and kits.CATALOG
    rng = np.random.default_rng(5)
    seen = [kits.chaos(kits.weapon(kits.base(), "iron_sword"), rng) for _ in range(4000)]
    jump = np.array([k["jump_power"] for k in seen])
    walk = np.array([k["walk_speed"] for k in seen])
    scale = np.array([k["scale"] for k in seen])
    profiles = {(p["walk_speed"], p["jump_power"], p["scale"]) for p in kits.GAME_EFFECTS}
    from_logs = np.mean([(round(k["walk_speed"] / 0.1, 2), round(k["jump_power"], 2), round(k["scale"], 2)) in profiles for k in seen])
    assert 0.4 < from_logs < 0.7
    assert (np.abs(jump - 0.82) < 1e-9).any() and (jump > 0.9).any() and (jump == 0).any()   # super jump, stacked, frozen
    assert (walk == 0).any() and walk.max() >= 0.2 and (scale <= 0.35).any() and scale.max() <= 3.0
    assert any(k["reach"] > 3.0 for k in seen) and any(k["strength"] >= 9 for k in seen)
