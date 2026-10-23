"""Observations of specs v2 and v3 from a CombatState: the same formulas as net.battlehub.core.ml in the mod.

A CombatState (v2) is a dict of numpy arrays, one entry per bot, with what it knows at one moment (STATE2_KEYS):
  self_*   where it is and how it moves, its charge, protection, health and absorption, food and saturation, the health
           it gets back a second (regen), how far it has been falling, its size and reach, its weapon's full damage
           (melee_damage with strength, melee_bonus from sharpness), the ticks its weapon needs to charge, knockback
           level, armor, toughness, resistance level, speed, jump strength and gravity; what it carries and holds
  opp_*    the target as last located (position) and as last seen (the rest; masked here when not visible)
  e2_*     the nearest other enemy, if any (e2_present)
  threat_* the projectile coming at it, if any (threat_present)
  terrain  voids, drops, steps, walls (n, 8), self_below, opp_below, opp_drop_behind, opp_void_behind (terrain.py)
and for v3 (STATE3_KEYS):
  ally_*   the nearest team mate, if any (ally_present), and allies_near
  e3_*     the next nearest enemy after the second, if any (e3_present)
  memory   opp_unseen (ticks since the target was last seen), self_since_hit (since an enemy last hurt it),
           opp_since_hit (since it last hurt its target)
v3's history (an observation `of` an earlier decision) is not in the state: observe() takes the earlier observations.
Angles are Minecraft's: yaw 0 looks along +z, 90 along -x; pitch positive looks down.
"""
import numpy as np

from pvp_sim.obs import frame, look_vector, ray_hits_box

from .spec import SPEC2

SELF2 = ["x", "y", "z", "vx", "vy", "vz", "yaw", "pitch", "on_ground", "sprinting", "charge", "hurt_ticks", "health",
         "max_health", "absorption", "food", "saturation", "regen", "fall", "scale", "reach", "melee_damage", "melee_bonus",
         "cooldown", "kb", "armor", "toughness", "resistance", "walk_speed", "jump_power", "gravity", "arrows", "food_items",
         "gapples", "pearls", "winds", "shield", "pearl_cd", "wind_cd", "shield_cd", "hand", "use_ticks", "blocking"]
OPP2 = ["x", "y", "z", "vx", "vy", "vz", "yaw", "on_ground", "sprinting", "since_swing", "hurt_ticks", "health", "max_health",
        "absorption", "visible", "blocking", "draw", "eating", "holds_bow", "holds_food", "holds_throwable", "melee_damage",
        "melee_bonus", "cooldown", "kb", "armor", "toughness", "resistance", "scale"]
E2 = ["present", "x", "y", "z", "yaw", "visible", "since_swing", "holds_bow"]
THREAT = ["present", "x", "y", "z", "vx", "vy", "vz"]
STATE2_KEYS = (["self_" + k for k in SELF2] + ["opp_" + k for k in OPP2] + ["e2_" + k for k in E2] + ["threat_" + k for k in THREAT]
               + ["damage_on", "enemies_near", "self_below", "opp_below", "opp_drop_behind", "opp_void_behind",
                  "voids", "drops", "steps", "walls"])
ALLY = ["present", "x", "y", "z", "visible", "health", "max_health"]
MEMORY = ["opp_unseen", "self_since_hit", "opp_since_hit"]
RAYS = ("voids", "drops", "steps", "walls")
# v2's values, v3's, then the rays (the order of the mod's CombatState.KEYS, which the fight logs store)
STATE3_KEYS = ([k for k in STATE2_KEYS if k not in RAYS] + ["ally_" + k for k in ALLY] + ["e3_" + k for k in E2] + ["allies_near"]
               + MEMORY + list(RAYS))
HANDS = ["weapon", "bow", "food", "gapple", "pearl", "wind"]
# the kinds of the values (the rest are floats): the golden file and the mod's fight logs type them this way
BOOLS = {"self_on_ground", "self_sprinting", "self_shield", "self_blocking", "opp_on_ground", "opp_sprinting", "opp_visible",
         "opp_blocking", "opp_eating", "opp_holds_bow", "opp_holds_food", "opp_holds_throwable", "e2_present", "e2_visible",
         "e2_holds_bow", "threat_present", "damage_on", "ally_present", "ally_visible", "e3_present", "e3_visible", "e3_holds_bow"}
INTS = {"self_hand"}


def armor_reduce(damage, armor, toughness):
    """Damage after armor (the game's formula; no armor leaves it as it is)."""
    f = 2.0 + toughness / 4.0
    pts = np.clip(armor - damage / f, armor * 0.2, 20.0)
    return damage * (1.0 - pts / 25.0)


def full_hit(damage, bonus, armor, toughness, resistance, max_health):
    """A full (charged, not critical) hit after armor and resistance, per the victim's max health."""
    d = armor_reduce(np.maximum(0.0, damage + bonus), armor, toughness) * np.maximum(0.0, 1.0 - 0.2 * resistance)
    return d / np.maximum(1e-9, max_health)


def crosshair(s, spec=SPEC2):
    """1 where the look ray from my eyes hits the target's hitbox within my reach."""
    lx, ly, lz = look_vector(s["self_yaw"], s["self_pitch"])
    hw = 0.6 * s["opp_scale"] / 2
    hh = 1.8 * s["opp_scale"]
    return ray_hits_box(s["self_x"], s["self_y"] + spec.eye_height * s["self_scale"], s["self_z"], lx, ly, lz,
                        s["opp_x"] - hw, s["opp_y"], s["opp_z"] - hw, s["opp_x"] + hw, s["opp_y"] + hh, s["opp_z"] + hw,
                        s["self_reach"]).astype(np.float64)


def threat(s, spec=SPEC2):
    """time to the closest approach, how close it passes my chest, and where it is (fwd, left) for a projectile."""
    (fx, fz), (lx, lz) = frame(s["self_yaw"])
    present = s["threat_present"] > 0.5
    px = s["threat_x"] - s["self_x"]
    py = s["threat_y"] - (s["self_y"] + spec.chest_height * s["self_scale"])
    pz = s["threat_z"] - s["self_z"]
    vx, vy, vz = s["threat_vx"], s["threat_vy"], s["threat_vz"]
    vv = vx * vx + vy * vy + vz * vz
    t = np.where(vv > 1e-9, np.clip(-(px * vx + py * vy + pz * vz) / np.where(vv > 1e-9, vv, 1.0), 0.0, 40.0), 0.0)
    cx, cy, cz = px + vx * t, py + vy * t, pz + vz * t
    miss = np.sqrt(cx * cx + cy * cy + cz * cz)
    ph = np.hypot(px, pz)
    tiny = ph < 1e-9
    fwd = np.where(tiny, 0.0, (px * fx + pz * fz) / np.where(tiny, 1.0, ph))
    left = np.where(tiny, 0.0, (px * lx + pz * lz) / np.where(tiny, 1.0, ph))
    return (np.where(present, t, 20.0), np.where(present, miss, 2.0), np.where(present, fwd, 0.0), np.where(present, left, 0.0))


def raw_features(s, spec=SPEC2):
    """The unscaled observation values, shape (n, obs_dim), in spec order (0 for the observations of earlier decisions:
    observe() fills them in)."""
    (fx, fz), (lx, lz) = frame(s["self_yaw"])
    dx = s["opp_x"] - s["self_x"]
    dz = s["opp_z"] - s["self_z"]
    dy = s["opp_y"] - s["self_y"]
    dist = np.hypot(dx, dz)
    vis = s["opp_visible"] > 0.5
    ovx = np.where(vis, s["opp_vx"], 0.0)
    ovy = np.where(vis, s["opp_vy"], 0.0)
    ovz = np.where(vis, s["opp_vz"], 0.0)
    a_fwd = dx * fx + dz * fz
    a_left = dx * lx + dz * lz
    near = dist < 1e-9
    chest_dy = (s["opp_y"] + spec.chest_height * s["opp_scale"]) - (s["self_y"] + spec.eye_height * s["self_scale"])
    need_pitch = -np.degrees(np.arctan2(chest_dy, dist))
    (ofx, ofz), (olx, olz) = frame(s["opp_yaw"])
    o_fwd = -dx * ofx - dz * ofz
    o_left = -dx * olx - dz * olz
    # the second enemy
    e2 = s["e2_present"] > 0.5
    e2v = e2 & (s["e2_visible"] > 0.5)
    ex = np.where(e2, s["e2_x"] - s["self_x"], 0.0)
    ez = np.where(e2, s["e2_z"] - s["self_z"], 0.0)
    ey = np.where(e2, s["e2_y"] - s["self_y"], 0.0)
    edist = np.hypot(ex, ez)
    (efx, efz), _ = frame(s["e2_yaw"])
    elook = np.where(edist < 1e-9, 1.0, (-ex * efx - ez * efz) / np.where(edist < 1e-9, 1.0, edist))
    t_time, t_miss, t_fwd, t_left = threat(s, spec)
    hand = np.asarray(s["self_hand"]).astype(np.int64)
    one = lambda b: np.asarray(b, dtype=np.float64)
    cols = {
        "opp_fwd": a_fwd,
        "opp_left": a_left,
        "opp_up": dy,
        "opp_dist": dist,
        "opp_vel_fwd": ovx * fx + ovz * fz,
        "opp_vel_left": ovx * lx + ovz * lz,
        "opp_vel_up": ovy,
        "aim_yaw_sin": np.where(near, 0.0, a_left / np.where(near, 1.0, dist)),
        "aim_yaw_cos": np.where(near, 1.0, a_fwd / np.where(near, 1.0, dist)),
        "aim_pitch": need_pitch - s["self_pitch"],
        "opp_look_sin": np.where(near, 0.0, o_left / np.where(near, 1.0, dist)),
        "opp_look_cos": np.where(near, 1.0, o_fwd / np.where(near, 1.0, dist)),
        "on_crosshair": crosshair(s, spec),
        "self_vel_fwd": s["self_vx"] * fx + s["self_vz"] * fz,
        "self_vel_left": s["self_vx"] * lx + s["self_vz"] * lz,
        "self_vel_up": s["self_vy"],
        "self_ground": one(s["self_on_ground"]),
        "self_sprint": one(s["self_sprinting"]),
        "self_charge": s["self_charge"],
        "self_hurt": s["self_hurt_ticks"],
        "self_health": s["self_health"] / np.maximum(1e-9, s["self_max_health"]),
        "self_absorption": s["self_absorption"],
        "self_food": s["self_food"],
        "self_saturation": s["self_saturation"],
        "self_regen": s["self_regen"],
        "self_fall": s["self_fall"],
        "opp_visible": one(vis),
        "opp_ground": np.where(vis, one(s["opp_on_ground"]), 0.0),
        "opp_sprint": np.where(vis, one(s["opp_sprinting"]), 0.0),
        "opp_since_swing": np.where(vis, s["opp_since_swing"], spec.obs_scale[spec.index("opp_since_swing")]),
        "opp_hurt": np.where(vis, s["opp_hurt_ticks"], 0.0),
        "opp_health": np.where(vis, s["opp_health"] / np.maximum(1e-9, s["opp_max_health"]), 1.0),
        "opp_absorption": np.where(vis, s["opp_absorption"], 0.0),
        "opp_blocking": np.where(vis, one(s["opp_blocking"]), 0.0),
        "opp_draw": np.where(vis, s["opp_draw"], 0.0),
        "opp_eating": np.where(vis, one(s["opp_eating"]), 0.0),
        "opp_holds_bow": np.where(vis, one(s["opp_holds_bow"]), 0.0),
        "opp_holds_food": np.where(vis, one(s["opp_holds_food"]), 0.0),
        "opp_holds_throwable": np.where(vis, one(s["opp_holds_throwable"]), 0.0),
        "self_dmg": full_hit(s["self_melee_damage"], s["self_melee_bonus"], s["opp_armor"], s["opp_toughness"], s["opp_resistance"], s["opp_max_health"]),
        "opp_dmg": full_hit(s["opp_melee_damage"], s["opp_melee_bonus"], s["self_armor"], s["self_toughness"], s["self_resistance"], s["self_max_health"]),
        "self_cooldown": s["self_cooldown"],
        "opp_cooldown": s["opp_cooldown"],
        "self_kb": s["self_kb"],
        "opp_kb": s["opp_kb"],
        "self_speed": s["self_walk_speed"] / 0.1 - 1.0,
        "self_jump": s["self_jump_power"] / 0.42 - 1.0,
        "self_gravity": s["self_gravity"] / 0.08 - 1.0,
        "self_scale": s["self_scale"] - 1.0,
        "opp_scale": s["opp_scale"] - 1.0,
        "self_reach": s["self_reach"] / 3.0 - 1.0,
        "damage_on": one(s["damage_on"]),
        "self_arrows": s["self_arrows"],
        "self_food_items": s["self_food_items"],
        "self_gapples": s["self_gapples"],
        "self_pearls": s["self_pearls"],
        "self_winds": s["self_winds"],
        "self_shield": one(s["self_shield"]),
        "pearl_cooldown": s["self_pearl_cd"],
        "wind_cooldown": s["self_wind_cd"],
        "shield_cooldown": s["self_shield_cd"],
        "self_use": s["self_use_ticks"],
        "self_blocking": one(s["self_blocking"]),
        "threat_time": t_time,
        "threat_miss": t_miss,
        "threat_fwd": t_fwd,
        "threat_left": t_left,
        "e2_fwd": ex * fx + ez * fz,
        "e2_left": ex * lx + ez * lz,
        "e2_up": ey,
        "e2_dist": np.where(e2, edist, spec.obs_scale[spec.index("e2_dist")]),
        "e2_visible": one(e2v),
        "e2_look": np.where(e2v, elook, 0.0),
        "e2_since_swing": np.where(e2v, s["e2_since_swing"], spec.obs_scale[spec.index("e2_since_swing")]),
        "e2_holds_bow": np.where(e2v, one(s["e2_holds_bow"]), 0.0),
        "enemies_near": s["enemies_near"],
        "self_below": s["self_below"],
        "opp_below": s["opp_below"],
        "opp_drop_behind": s["opp_drop_behind"],
        "opp_void_behind": s["opp_void_behind"],
    }
    for i, h in enumerate(HANDS):
        cols["hand_" + h] = one(hand == i)
    for k in range(spec.ray_directions):
        cols["void_%d" % k] = s["voids"][:, k]
        cols["drop_%d" % k] = s["drops"][:, k]
        cols["step_%d" % k] = s["steps"][:, k]
        cols["wall_%d" % k] = s["walls"][:, k]
    if "ally_fwd" in spec.obs_names:
        cols.update(v3_features(s, spec, fx, fz, lx, lz))
    m = np.shape(s["self_x"])[0]
    out = np.zeros((m, len(spec.obs_names)))
    for k in spec.current:
        out[:, k] = cols[spec.obs_names[k]]
    return out


def v3_features(s, spec, fx, fz, lx, lz):
    """v3's team mate, third enemy and memory (unscaled)."""
    one = lambda b: np.asarray(b, dtype=np.float64)
    al = s["ally_present"] > 0.5
    alv = al & (s["ally_visible"] > 0.5)
    ax = np.where(al, s["ally_x"] - s["self_x"], 0.0)
    az = np.where(al, s["ally_z"] - s["self_z"], 0.0)
    ay = np.where(al, s["ally_y"] - s["self_y"], 0.0)
    e3 = s["e3_present"] > 0.5
    e3v = e3 & (s["e3_visible"] > 0.5)
    ex = np.where(e3, s["e3_x"] - s["self_x"], 0.0)
    ez = np.where(e3, s["e3_z"] - s["self_z"], 0.0)
    ey = np.where(e3, s["e3_y"] - s["self_y"], 0.0)
    edist = np.hypot(ex, ez)
    (efx, efz), _ = frame(s["e3_yaw"])
    elook = np.where(edist < 1e-9, 1.0, (-ex * efx - ez * efz) / np.where(edist < 1e-9, 1.0, edist))
    scale = lambda name: spec.obs_scale[spec.index(name)]
    return {
        "ally_fwd": ax * fx + az * fz,
        "ally_left": ax * lx + az * lz,
        "ally_up": ay,
        "ally_dist": np.where(al, np.hypot(ax, az), scale("ally_dist")),
        "ally_visible": one(alv),
        "ally_health": np.where(alv, s["ally_health"] / np.maximum(1e-9, s["ally_max_health"]), 1.0),
        "ally_opp_dist": np.where(al, np.hypot(s["opp_x"] - s["ally_x"], s["opp_z"] - s["ally_z"]), scale("ally_opp_dist")),
        "allies_near": s["allies_near"],
        "e3_fwd": ex * fx + ez * fz,
        "e3_left": ex * lx + ez * lz,
        "e3_up": ey,
        "e3_dist": np.where(e3, edist, scale("e3_dist")),
        "e3_visible": one(e3v),
        "e3_look": np.where(e3v, elook, 0.0),
        "e3_since_swing": np.where(e3v, s["e3_since_swing"], scale("e3_since_swing")),
        "e3_holds_bow": np.where(e3v, one(s["e3_holds_bow"]), 0.0),
        "opp_unseen": s["opp_unseen"],
        "self_since_hit": s["self_since_hit"],
        "opp_since_hit": s["opp_since_hit"],
    }


def observe(s, spec=SPEC2, past=None, start=None):
    """The observation vectors, shape (n, obs_dim): raw / scale, clipped to [-clip, clip]. With a history (v3): past[a]
    is the observation of a + 1 decisions earlier (n, obs_dim); without it (the start of a fight), or where `start`
    (n,) is true, an observation of an earlier decision is the value now."""
    raw = raw_features(s, spec)
    out = np.clip(raw / spec.obs_scale, -spec.obs_clip, spec.obs_clip)
    return history(out, spec, past, start)


def history(obs, spec, past=None, start=None):
    """Fills the observations of earlier decisions in obs (n, obs_dim): from past (see observe), else the value now."""
    if not spec.history:
        return obs
    ks, ofs, agos = (np.array(v) for v in zip(*spec.history))
    now = obs[:, ofs]
    if past is None:
        obs[:, ks] = now
        return obs
    then = np.stack([past[a - 1][:, o] for o, a in zip(ofs, agos)], axis=1)
    then = np.clip(then / spec.obs_scale[ks], -spec.obs_clip[ks], spec.obs_clip[ks])
    obs[:, ks] = then if start is None else np.where(np.asarray(start)[:, None], now, then)
    return obs
