"""The simulation against the game, on the mod's fight logs (ML phase 3D).

Each measure puts the simulation on what a log stored and lets it go on as the game did:
  move      a fighter's state at a decision, its action over the decision's two ticks on flat ground (where its terrain
            rays see nothing within 2 blocks and no hit falls in those ticks): where it is and how fast it goes at its
            next decision, against that next row;
  push      a click that landed (a "melee" hit; in logs before 1.41, with the weapon in hand within 4 blocks) on a victim
            standing on the ground: its rise tells when it took the push (a client, or a bot through the mod's
            BotConnection, takes it at the start of a tick); from its last row before the hit, its own actions with the
            hit through the simulation's World.land in that tick, its horizontal velocity against the game's then.
            (Over a longer window walls and other players get in the way, which flat ground does not have.);
  damage    the hit's damage (before armor) against the simulation's formula for the attacker's state (World.melee_damage);
  reach     how far the attacker's eyes were from the victim's body at the hit, against its reach;
  coverage  the model's decisions (bot rows with the model's choice) with an observation outside the training's ranges
            (spec/combat_v3_ranges.json), per group of observations;
  behaviour per kind of fighter: share of time sprinting, strafe changes, jumps and clicks per second, the charge of the
            clicks that landed, the distance to the target while it is in sight within 8 blocks.
Fights are split for good into fit and test by their name (a quarter is test), so a fitted simulation is judged on
fights it was not fitted on.

    python train/simfit.py data/                     # every measure, every fight
    python train/simfit.py data/ --part test --json report.json
"""
import argparse
import collections
import json
import os
import sys
import zlib

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from pvp_sim2.kits import base  # noqa: E402
from pvp_sim2.physics import World  # noqa: E402
from pvp_sim2.spec import SPEC3  # noqa: E402
from train import fights as F  # noqa: E402

RANGES = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "spec", "combat_v3_ranges.json")
CLEAR = 2.0          # no terrain feature within this many blocks for a move to be measured on flat ground
EYE = 1.62
TICK = 1 / 20.0


def part_of(name):
    """"test" for a fixed quarter of the fights (by name), else "fit"."""
    return "test" if zlib.crc32(os.path.basename(name).encode()) % 4 == 0 else "fit"


def _q(v):
    v = np.asarray(v, dtype=np.float64)
    if not len(v):
        return {"n": 0}
    return {"n": int(len(v)), "mean": float(v.mean()), "p10": float(np.quantile(v, 0.1)), "median": float(np.median(v)),
            "p90": float(np.quantile(v, 0.9))}


class Log:
    """One fight's rows with their states, the kind of each fighter and each fighter's rows in order."""

    def __init__(self, fight):
        self.fight = fight
        self.rows = fight.rows
        self.s = F.states(fight)
        self.kinds = [x["kind"] for x in fight.header["fighters"]]
        self.actions = np.array([r["a"] for r in self.rows], dtype=np.float64).reshape(len(self.rows), -1)
        self.names = fight.header["action_names"]
        self.by = collections.defaultdict(list)
        for i, r in enumerate(self.rows):
            self.by[r["f"]].append(i)
        self.dt = fight.header.get("decision_ticks", 2)

    def act(self, i, name):
        return self.actions[i, self.names.index(name)]

    def next_row(self, i):
        """The same fighter's next row one decision later, or None."""
        f, t = self.rows[i]["f"], self.rows[i]["t"]
        idx = self.by[f]
        k = idx.index(i)
        if k + 1 < len(idx) and self.rows[idx[k + 1]]["t"] == t + self.dt:
            return idx[k + 1]
        return None

    def row_at(self, fighter, t):
        """The fighter's row of the decision under way at tick t (its start at or before t), or None."""
        best = None
        for i in self.by[fighter]:
            if self.rows[i]["t"] <= t:
                best = i
            else:
                break
        return best if best is not None and t - self.rows[best]["t"] < self.dt else None

    def melee_hits(self):
        """The hits that were clicks: (hit, attacker row, victim row). Logs before 1.41 have no kind: then the attacker
        had its weapon in hand and was within 4 blocks."""
        out = []
        for h in self.fight.hits:
            att, vic = h["hit"]
            if att < 0:
                continue
            ia, iv = self.row_at(att, h["t"]), self.row_at(vic, h["t"])
            if ia is None or iv is None:
                continue
            kind = h.get("kind")
            if kind is None:
                kind = "melee" if self.s["self_hand"][ia] == 0 and eye_distance(self.s, ia, iv) <= 4.0 else "other"
            if kind == "melee":
                out.append((h, ia, iv))
        return out


def eye_distance(s, ia, iv):
    """From the attacker's eyes to the nearest point of the victim's body."""
    sc, vs = s["self_scale"][ia], s["self_scale"][iv]
    ex, ey, ez = s["self_x"][ia], s["self_y"][ia] + EYE * sc, s["self_z"][ia]
    hw = 0.3 * vs
    bx, by, bz = s["self_x"][iv], s["self_y"][iv], s["self_z"][iv]
    cx = np.clip(ex, bx - hw, bx + hw)
    cy = np.clip(ey, by, by + 1.8 * vs)
    cz = np.clip(ez, bz - hw, bz + hw)
    return float(np.sqrt((ex - cx) ** 2 + (ey - cy) ** 2 + (ez - cz) ** 2))


def _kit(s, i):
    """The simulation's fields for the fighter of row i, from what its state says (strength is in its weapon damage)."""
    k = base()
    k.update({"atk_damage": s["self_melee_damage"][i], "strength": 0.0, "atk_speed": 20.0 / max(1e-6, s["self_cooldown"][i]),
              "sharp": s["self_melee_bonus"][i], "kb_level": int(round(s["self_kb"][i])), "armor": s["self_armor"][i],
              "toughness": s["self_toughness"][i], "resistance": int(round(s["self_resistance"][i])),
              "walk_speed": s["self_walk_speed"][i], "jump_power": s["self_jump_power"][i], "gravity": s["self_gravity"][i],
              "scale": s["self_scale"][i], "reach": s["self_reach"][i]})
    return k


def _put(w, a, slot, s, i, x, y, z, team):
    """Slot `slot` of arena a as the fighter of row i, at (x, y, z) with its velocity, look and state."""
    w.place(a, slot, x, y, z, s["self_yaw"][i], _kit(s, i), team)
    f = w.f
    f.vx[a, slot], f.vy[a, slot], f.vz[a, slot] = s["self_vx"][i], s["self_vy"][i], s["self_vz"][i]
    f.pitch[a, slot] = s["self_pitch"][i]
    f.on_ground[a, slot] = bool(s["self_on_ground"][i])
    f.sprinting[a, slot] = bool(s["self_sprinting"][i])
    f.health[a, slot] = s["self_health"][i]
    f.food[a, slot] = s["self_food"][i]
    f.fall[a, slot] = s["self_fall"][i]
    f.hand[a, slot] = int(s["self_hand"][i])
    f.invul[a, slot] = int(s["self_hurt_ticks"][i]) + (10 if s["self_hurt_ticks"][i] > 0 else 0)


def _flat(w, a, floor):
    w.terrain.ground[a] = floor
    w.terrain.floor[a] = floor if np.isfinite(floor) else 0.0


def _floor(s, i):
    """The ground under the fighter of row i (NaN: none within the spec's depth)."""
    below = s["self_below"][i]
    return s["self_y"][i] - below if below < SPEC3.depth - 1e-6 else np.nan


def _clear(s, i):
    return all(np.min(s[r][i]) >= CLEAR for r in ("walls", "steps", "drops", "voids"))


def measure_moves(logs):
    """Replays every clear decision on flat ground: per kind of fighter, the error of where it is and how fast it goes
    (horizontal) at its next decision."""
    picks = []
    for log in logs:
        s = log.s
        busy = collections.defaultdict(set)
        for h in log.fight.hits:
            for who in h["hit"]:
                if who >= 0:
                    busy[who].update(range(h["t"] - log.dt, h["t"] + 1))
        for i, r in enumerate(log.rows):
            j = log.next_row(i)
            if j is None or r["t"] in busy[r["f"]] or (r["t"] + 1) in busy[r["f"]]:
                continue
            if s["self_use_ticks"][i] > 0 or log.act(i, "use") > 0.5 or not _clear(s, i):
                continue
            # someone within a block pushes (the simulation pushes too, but only between its own fighters)
            if s["opp_visible"][i] and np.hypot(s["opp_x"][i] - s["self_x"][i], s["opp_z"][i] - s["self_z"][i]) < 1.0:
                continue
            picks.append((log, i, j))
    out = {}
    if not picks:
        return out
    n = len(picks)
    w = World(n, 1, spec=SPEC3, rng=np.random.default_rng(0))
    for a, (log, i, j) in enumerate(picks):
        s = log.s
        _flat(w, a, _floor(s, i))
        _put(w, a, 0, s, i, 0.0, s["self_y"][i], 0.0, 0)
    dt = picks[0][0].dt
    inputs = {key: np.zeros((n, 1)) for key in ("forward", "strafe", "yaw_left", "pitch_down")}
    jump = np.zeros((n, 1), dtype=bool)
    sprint = np.zeros((n, 1), dtype=bool)
    hand = np.zeros((n, 1), dtype=np.int64)
    for a, (log, i, j) in enumerate(picks):
        inputs["forward"][a, 0] = log.act(i, "forward")
        inputs["strafe"][a, 0] = log.act(i, "strafe")
        inputs["yaw_left"][a, 0] = log.act(i, "yaw") / log.dt
        inputs["pitch_down"][a, 0] = log.act(i, "pitch") / log.dt
        jump[a, 0] = log.act(i, "jump") > 0.5
        sprint[a, 0] = log.act(i, "sprint") > 0.5
        hand[a, 0] = int(log.s["self_hand"][i])
    off = np.zeros((n, 1), dtype=bool)
    for _ in range(dt):
        w.tick(inputs["forward"], inputs["strafe"], jump, sprint, off, off, inputs["yaw_left"], inputs["pitch_down"], hand)
    err_xz, err_y, err_v, ratio = (collections.defaultdict(list) for _ in range(4))
    for a, (log, i, j) in enumerate(picks):
        s = log.s
        kind = log.kinds[log.rows[i]["f"]]
        dx, dz = s["self_x"][j] - s["self_x"][i], s["self_z"][j] - s["self_z"][i]
        px, pz = w.f.x[a, 0], w.f.z[a, 0]
        err_xz[kind].append(np.hypot(dx - px, dz - pz))
        err_y[kind].append(abs((s["self_y"][j] - s["self_y"][i]) - (w.f.y[a, 0] - s["self_y"][i])))
        err_v[kind].append(np.hypot(s["self_vx"][j] - w.f.vx[a, 0], s["self_vz"][j] - w.f.vz[a, 0]))
        moved = np.hypot(px, pz)
        if moved > 0.3:
            ratio[kind].append(np.hypot(dx, dz) / moved)
    for kind in err_xz:
        out[kind] = {"position": _q(err_xz[kind]), "height": _q(err_y[kind]), "velocity": _q(err_v[kind]),
                     "distance_ratio": _q(ratio[kind])}
    return out


def measure_hits(logs):
    """Every click that landed: its damage against the simulation's formula (World.melee_damage) per kind of attacker,
    and how far the attacker's eyes were from the victim's body."""
    dmg_ratio, reach = collections.defaultdict(list), collections.defaultdict(list)
    picks = []
    for log in logs:
        s = log.s
        for h, ia, iv in log.melee_hits():
            reach[log.kinds[h["hit"][0]]].append(eye_distance(s, ia, iv))
            if h["dmg"] < 20:
                picks.append((log, h, ia))
    out = {"reach": {k: _q(v) for k, v in reach.items()}}
    if not picks:
        return out
    n = len(picks)
    w = World(n, 1, spec=SPEC3, rng=np.random.default_rng(1))
    charge, crit = np.zeros(n), np.zeros(n, dtype=bool)
    for a, (log, h, ia) in enumerate(picks):
        s = log.s
        _put(w, a, 0, s, ia, 0.0, s["self_y"][ia], 0.0, 0)
        c = charge[a] = s["self_charge"][ia]
        crit[a] = c > 0.9 and not s["self_on_ground"][ia] and s["self_vy"][ia] < 0 and not s["self_sprinting"][ia] and s["self_fall"][ia] > 0
    sim = w.melee_damage(0, charge, crit, np.zeros(n, dtype=bool))
    for a, (log, h, ia) in enumerate(picks):
        if sim[a] > 0:
            dmg_ratio[log.kinds[h["hit"][0]]].append(h["dmg"] / sim[a])
    out["damage_ratio"] = {k: _q(v) for k, v in dmg_ratio.items()}
    return out


# a push on the ground lifts a player to min(0.4, vy / 2 + 0.4) = 0.3608 from the resting -0.0784, a second one in the
# same hit (a sprint hit, the knockback enchantment) to 0.4; then each tick (vy - 0.08) * 0.98: how far it rose tells
# how many ticks ago the push was taken
def _rise(lift, ticks=5):
    out = [lift]
    for _ in range(ticks):
        out.append((out[-1] - 0.08) * 0.98)
    return out


RISES = (_rise(0.3608), _rise(0.4))


def measure_push(logs, detail=None):
    """The push of a click on a victim standing on the ground (a full hit), timed by its rise: the first row after the
    hit whose upward speed is that of a push 1 to 4 ticks ago fixes when the push was taken; the simulation plays the
    victim's actions from its last row before the hit, lands the hit through World.land in that tick (with one push when the
    rise says so), and its horizontal velocity at that row is
    compared with the game's. When in a tick a client or bot takes its push (the
    mod's BotConnection) does not matter then, nor do walls further on (the window is short)."""
    picks = []
    for log in logs:
        s = log.s
        for h, ia, _ in log.melee_hits():
            if h["dmg"] >= 20:
                continue
            att, vic = h["hit"]
            pre = None
            for i in log.by[vic]:
                if log.rows[i]["t"] < h["t"]:
                    pre = i
            if pre is None or not s["self_on_ground"][pre] or s["self_hurt_ticks"][pre] > 0:
                continue
            seq, i, found = [pre], pre, None
            for _ in range(3):
                i = log.next_row(i)
                if i is None:
                    break
                seq.append(i)
                vy = s["self_vy"][i]
                # rise[k] after the tick: the push was taken at the start of the tick k - 1 ticks before
                ago = [(k, p) for p, rise in enumerate(RISES) for k in range(1, 5) if abs(vy - rise[k]) < 0.004]
                if ago and log.rows[i]["t"] - ago[0][0] + 1 >= h["t"]:
                    found = (i, ago[0][0], ago[0][1])
                    break
            if found is None or any(o is not h and o["hit"][1] == vic and log.rows[pre]["t"] < o["t"] <= log.rows[found[0]]["t"]
                                    for o in log.fight.hits):
                continue
            picks.append((log, h, ia, seq, found[0], log.rows[found[0]]["t"] - found[1] + 1, found[2]))
    if not picks:
        return {}
    n = len(picks)
    dt = picks[0][0].dt
    w = World(n, 2, spec=SPEC3, rng=np.random.default_rng(4))
    charge, crit = np.zeros(n), np.zeros(n, dtype=bool)
    land_at, end_at = np.zeros(n, dtype=np.int64), np.zeros(n, dtype=np.int64)
    for a, (log, h, ia, seq, j, taken, second) in enumerate(picks):
        s, pre = log.s, seq[0]
        _flat(w, a, _floor(s, pre))
        ox, oz = s["self_x"][pre], s["self_z"][pre]
        _put(w, a, 0, s, ia, s["self_x"][ia] - ox, s["self_y"][ia], s["self_z"][ia] - oz, 0)
        _put(w, a, 1, s, pre, 0.0, s["self_y"][pre], 0.0, 1)
        charge[a] = s["self_charge"][ia]
        # a rise from 0.3608 was one push: no second (the attacker's last row may have it sprinting when it no longer
        # was); 0.4 can be either (a single push on a victim already going up reaches it too)
        if second == 0:
            w.f.sprinting[a, 0] = False
            w.f.kb_level[a, 0] = 0
        land_at[a] = taken - log.rows[pre]["t"] - 1
        end_at[a] = log.rows[j]["t"] - log.rows[pre]["t"]
    off = np.zeros((n, 2), dtype=bool)
    vx, vz = np.zeros(n), np.zeros(n)
    for tk in range(int(end_at.max())):
        fw, st, yl, pd = (np.zeros((n, 2)) for _ in range(4))
        ju, sp = np.zeros((n, 2), dtype=bool), np.zeros((n, 2), dtype=bool)
        hd = np.zeros((n, 2), dtype=np.int64)
        for a, (log, h, ia, seq, j, taken, second) in enumerate(picks):
            i = seq[min(len(seq) - 1, tk // dt)]
            fw[a, 1], st[a, 1] = log.act(i, "forward"), log.act(i, "strafe")
            yl[a, 1], pd[a, 1] = log.act(i, "yaw") / dt, log.act(i, "pitch") / dt
            ju[a, 1], sp[a, 1] = log.act(i, "jump") > 0.5, log.act(i, "sprint") > 0.5
            hd[a] = [int(log.s["self_hand"][ia]), int(log.s["self_hand"][i])]
        now = land_at == tk
        if now.any():
            w.land(0, 1, now, charge, crit, np.zeros(n, dtype=bool))
        w.tick(fw, st, ju, sp, off, off, yl, pd, hd)
        done = end_at == tk + 1
        vx = np.where(done, w.f.vx[:, 1], vx)
        vz = np.where(done, w.f.vz[:, 1], vz)
    err, game, sim = (collections.defaultdict(list) for _ in range(3))
    for a, (log, h, ia, seq, j, taken, second) in enumerate(picks):
        s = log.s
        kind = log.kinds[h["hit"][1]]
        err[kind].append(np.hypot(s["self_vx"][j] - vx[a], s["self_vz"][j] - vz[a]))
        game[kind].append(np.hypot(s["self_vx"][j], s["self_vz"][j]))
        sim[kind].append(np.hypot(vx[a], vz[a]))
        if detail is not None:
            detail.append({"victim": kind, "attacker": log.kinds[h["hit"][0]], "delay": int(taken - h["t"]),
                           "game": (float(s["self_vx"][j]), float(s["self_vz"][j])), "sim": (float(vx[a]), float(vz[a]))})
    return {k: {"velocity_error": _q(err[k]), "speed_game": _q(game[k]), "speed_sim": _q(sim[k])} for k in err}


def _groups(names):
    g = collections.OrderedDict()
    for j, nm in enumerate(names):
        key = "history" if "@" in nm else nm.split("_")[0]
        g.setdefault(key, []).append(j)
    return g


def measure_coverage(logs, ranges_path=RANGES, spec=SPEC3):
    """The model's decisions in the game with an observation outside the training's ranges: in all, per group, and the
    observations most often out."""
    with open(ranges_path, encoding="utf-8") as fh:
        r = json.load(fh)
    lo = np.array([r["ranges"][nm][0] for nm in spec.obs_names])
    hi = np.array([r["ranges"][nm][1] for nm in spec.obs_names])
    tol = float(r["tolerance"]) * (hi - lo)
    obs = []
    for log in logs:
        o = F.observations(log.fight)
        keep = [i for i, row in enumerate(log.rows) if row.get("m") is not None]
        if keep:
            obs.append(o[keep])
    if not obs:
        return {"decisions": 0}
    obs = np.concatenate(obs)
    out_of = (obs < lo - tol) | (obs > hi + tol)
    per = out_of.mean(axis=0)
    worst = np.argsort(-per)[:10]
    return {"decisions": int(len(obs)), "any_out": float(out_of.any(axis=1).mean()),
            "groups": {g: float(out_of[:, idx].any(axis=1).mean()) for g, idx in _groups(spec.obs_names).items()},
            "worst": {spec.obs_names[j]: float(per[j]) for j in worst if per[j] > 0}}


def measure_behaviour(logs):
    """Per kind of fighter: what a fight looks like (the human-opponent requirement compares these)."""
    acc = collections.defaultdict(lambda: collections.defaultdict(list))
    for log in logs:
        s = log.s
        for f, idx in log.by.items():
            kind = log.kinds[f]
            prev_strafe = None
            for i in idx:
                a = acc[kind]
                a["decisions"].append(1)
                a["sprint"].append(float(s["self_sprinting"][i]))
                st = log.act(i, "strafe")
                a["strafe_change"].append(float(prev_strafe is not None and st != prev_strafe))
                prev_strafe = st
                a["jump"].append(float(log.act(i, "jump") > 0.5))
                a["click"].append(float(log.act(i, "attack") > 0.5))
                if s["opp_visible"][i]:
                    d = np.hypot(s["opp_x"][i] - s["self_x"][i], s["opp_z"][i] - s["self_z"][i])
                    if d < 8:
                        a["distance"].append(d)
        for h, ia, iv in log.melee_hits():
            acc[log.kinds[h["hit"][0]]]["charge"].append(s["self_charge"][ia])
    out = {}
    for kind, a in acc.items():
        sec = len(a["decisions"]) * logs[0].dt * TICK if logs else 1.0
        out[kind] = {"decisions": len(a["decisions"]), "sprint_share": float(np.mean(a["sprint"])),
                     "strafe_changes_per_s": float(np.sum(a["strafe_change"]) / sec), "jumps_per_s": float(np.sum(a["jump"]) / sec),
                     "clicks_per_s": float(np.sum(a["click"]) / sec),
                     "charge_at_hit": float(np.median(a["charge"])) if a["charge"] else None,
                     "distance": float(np.mean(a["distance"])) if a["distance"] else None}
    return out


def report(fights, part="all"):
    logs = [Log(f) for f in fights if part == "all" or part_of(f.name) == part]
    return {"part": part, "fights": len(logs), "decisions": int(sum(len(l.rows) for l in logs)),
            "move": measure_moves(logs), "hits": measure_hits(logs),
            "push": measure_push(logs), "coverage": measure_coverage(logs),
            "behaviour": measure_behaviour(logs)}


def text(r):
    lines = ["%s: %d fights, %d decisions" % (r["part"], r["fights"], r["decisions"])]
    for kind, m in r["move"].items():
        lines.append("move %-6s position error median %.3f p90 %.3f, velocity median %.3f, distance game/sim %.3f (n %d)"
                     % (kind, m["position"]["median"], m["position"]["p90"], m["velocity"]["median"],
                        m["distance_ratio"].get("median", float("nan")), m["position"]["n"]))
    for kind, m in r["push"].items():
        lines.append("push  %-6s velocity error median %.3f p90 %.3f, speed game %.3f sim %.3f (n %d)" % (
            kind, m["velocity_error"]["median"], m["velocity_error"]["p90"], m["speed_game"]["median"], m["speed_sim"]["median"],
            m["velocity_error"]["n"]))
    for kind, m in r["hits"].get("damage_ratio", {}).items():
        lines.append("damage %-6s game/sim median %.3f p10 %.3f p90 %.3f (n %d)" % (kind, m["median"], m["p10"], m["p90"], m["n"]))
    for kind, m in r["hits"].get("reach", {}).items():
        lines.append("reach %-6s median %.2f p90 %.2f (n %d)" % (kind, m["median"], m["p90"], m["n"]))
    c = r["coverage"]
    if c.get("decisions"):
        lines.append("coverage: %.1f%% of %d model decisions have a value out of range; worst %s" % (
            100 * c["any_out"], c["decisions"], ", ".join("%s %.1f%%" % (k, 100 * v) for k, v in list(c["worst"].items())[:6])))
    for kind, b in r["behaviour"].items():
        lines.append("behaviour %-6s sprint %.2f, strafe changes %.2f/s, jumps %.2f/s, clicks %.2f/s, charge at hit %s, distance %s"
                     % (kind, b["sprint_share"], b["strafe_changes_per_s"], b["jumps_per_s"], b["clicks_per_s"],
                        "%.2f" % b["charge_at_hit"] if b["charge_at_hit"] is not None else "-",
                        "%.2f" % b["distance"] if b["distance"] is not None else "-"))
    return "\n".join(lines)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("paths", nargs="+")
    ap.add_argument("--part", default="all", choices=["all", "fit", "test"])
    ap.add_argument("--json")
    a = ap.parse_args()
    fights = [f for p in a.paths for f in F.read(p)]
    r = report(fights, a.part)
    print(text(r))
    if a.json:
        with open(a.json, "w", encoding="utf-8") as fh:
            json.dump(r, fh, indent=1)


if __name__ == "__main__":
    main()
