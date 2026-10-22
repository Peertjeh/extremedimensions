"""Observations of the combat spec, computed from a CombatState: the same formulas as net.battlehub.core.ml in the mod.

A CombatState is a dict of numpy arrays (one entry per bot), what the bot knows at one moment:

  self_x, self_y, self_z, self_vx, self_vy, self_vz, self_yaw, self_pitch,
  self_on_ground, self_sprinting, self_charge, self_hurt_ticks, self_health, self_max_health,
  opp_x, opp_y, opp_z, opp_vx, opp_vy, opp_vz, opp_yaw,
  opp_on_ground, opp_sprinting, opp_since_swing, opp_hurt_ticks, opp_health, opp_max_health, opp_visible,
  edges (n, 8), walls (n, 8)

Angles are Minecraft's: yaw 0 looks along +z, 90 along -x; pitch positive looks down. The opponent's position is
where the bot last knew it; what it can only see (velocity, health, ground, sprint, hurt, swing) is masked here when
the opponent is not visible, so the caller may pass the true values.
"""
import numpy as np

from .spec import SPEC

SELF_FIELDS = ["x", "y", "z", "vx", "vy", "vz", "yaw", "pitch", "on_ground", "sprinting", "charge", "hurt_ticks", "health", "max_health"]
OPP_FIELDS = ["x", "y", "z", "vx", "vy", "vz", "yaw", "on_ground", "sprinting", "since_swing", "hurt_ticks", "health", "max_health", "visible"]
STATE_KEYS = ["self_" + f for f in SELF_FIELDS] + ["opp_" + f for f in OPP_FIELDS] + ["edges", "walls"]


def look_vector(yaw, pitch):
    ry, rp = np.radians(yaw), np.radians(pitch)
    return -np.sin(ry) * np.cos(rp), -np.sin(rp), np.cos(ry) * np.cos(rp)


def frame(yaw):
    """Unit vectors (x, z) of the bot's horizontal forward and left."""
    ry = np.radians(yaw)
    return (-np.sin(ry), np.cos(ry)), (np.cos(ry), np.sin(ry))


def ray_hits_box(ex, ey, ez, lx, ly, lz, minx, miny, minz, maxx, maxy, maxz, reach, spec=SPEC):
    """Does the ray from the eye along the look hit the box within reach (or start inside it)?"""
    tmin = np.full(np.shape(ex), -np.inf)
    tmax = np.full(np.shape(ex), np.inf)
    ok = np.ones(np.shape(ex), dtype=bool)
    for e, l, lo, hi in ((ex, lx, minx, maxx), (ey, ly, miny, maxy), (ez, lz, minz, maxz)):
        par = np.abs(l) < 1e-12
        ok &= ~(par & ((e < lo) | (e > hi)))
        safe = np.where(par, 1.0, l)
        t1 = (lo - e) / safe
        t2 = (hi - e) / safe
        near = np.where(par, -np.inf, np.minimum(t1, t2))
        far = np.where(par, np.inf, np.maximum(t1, t2))
        tmin = np.maximum(tmin, near)
        tmax = np.minimum(tmax, far)
    return ok & (tmax >= np.maximum(tmin, 0.0)) & (tmin <= reach)


def crosshair(s, spec=SPEC):
    """1 where the bot's look ray hits the opponent's hitbox within reach."""
    lx, ly, lz = look_vector(s["self_yaw"], s["self_pitch"])
    hw = spec.hitbox_width / 2
    return ray_hits_box(s["self_x"], s["self_y"] + spec.eye_height, s["self_z"], lx, ly, lz,
                        s["opp_x"] - hw, s["opp_y"], s["opp_z"] - hw, s["opp_x"] + hw, s["opp_y"] + spec.hitbox_height, s["opp_z"] + hw,
                        spec.reach).astype(np.float64)


def raw_features(s, spec=SPEC):
    """The unscaled observation values, shape (n, obs_dim), in spec order."""
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
    aim_sin = np.where(near, 0.0, a_left / np.where(near, 1.0, dist))
    aim_cos = np.where(near, 1.0, a_fwd / np.where(near, 1.0, dist))
    chest_dy = (s["opp_y"] + spec.chest_height) - (s["self_y"] + spec.eye_height)
    need_pitch = -np.degrees(np.arctan2(chest_dy, dist))
    (ofx, ofz), (olx, olz) = frame(s["opp_yaw"])
    o_fwd = -dx * ofx - dz * ofz
    o_left = -dx * olx - dz * olz
    look_sin = np.where(near, 0.0, o_left / np.where(near, 1.0, dist))
    look_cos = np.where(near, 1.0, o_fwd / np.where(near, 1.0, dist))
    cols = {
        "opp_fwd": a_fwd,
        "opp_left": a_left,
        "opp_up": dy,
        "opp_dist": dist,
        "opp_vel_fwd": ovx * fx + ovz * fz,
        "opp_vel_left": ovx * lx + ovz * lz,
        "opp_vel_up": ovy,
        "self_vel_fwd": s["self_vx"] * fx + s["self_vz"] * fz,
        "self_vel_left": s["self_vx"] * lx + s["self_vz"] * lz,
        "self_vel_up": s["self_vy"],
        "aim_yaw_sin": aim_sin,
        "aim_yaw_cos": aim_cos,
        "aim_pitch": need_pitch - s["self_pitch"],
        "opp_look_sin": look_sin,
        "opp_look_cos": look_cos,
        "on_crosshair": crosshair(s, spec),
        "self_ground": s["self_on_ground"].astype(np.float64),
        "self_sprint": s["self_sprinting"].astype(np.float64),
        "self_charge": s["self_charge"],
        "self_hurt": s["self_hurt_ticks"],
        "self_health": s["self_health"] / np.maximum(1e-9, s["self_max_health"]),
        "opp_visible": vis.astype(np.float64),
        "opp_ground": np.where(vis, s["opp_on_ground"], 0.0).astype(np.float64),
        "opp_sprint": np.where(vis, s["opp_sprinting"], 0.0).astype(np.float64),
        "opp_since_swing": np.where(vis, s["opp_since_swing"], spec.obs_scale[spec.index("opp_since_swing")]),
        "opp_hurt": np.where(vis, s["opp_hurt_ticks"], 0.0),
        "opp_health": np.where(vis, s["opp_health"] / np.maximum(1e-9, s["opp_max_health"]), 1.0),
    }
    for k in range(spec.ray_directions):
        cols["edge_%d" % k] = s["edges"][:, k]
        cols["wall_%d" % k] = s["walls"][:, k]
    return np.stack([np.asarray(cols[name], dtype=np.float64) for name in spec.obs_names], axis=1)


def observe(s, spec=SPEC):
    """The observation vectors, shape (n, obs_dim): raw / scale, clipped to [-clip, clip]."""
    raw = raw_features(s, spec)
    return np.clip(raw / spec.obs_scale, -spec.obs_clip, spec.obs_clip)


def in_view(ex, ey, ez, yaw, pitch, px, py, pz, spec=SPEC):
    """Within the view half-angle of where the head points (the mod's Senses.inView)."""
    lx, ly, lz = look_vector(yaw, pitch)
    tx, ty, tz = px - ex, py - ey, pz - ez
    ln = np.sqrt(tx * tx + ty * ty + tz * tz)
    cos = (lx * tx + ly * ty + lz * tz) / np.maximum(ln, 1e-9)
    return (ln < 1e-6) | (cos >= np.cos(np.radians(spec.view_half_angle)))
