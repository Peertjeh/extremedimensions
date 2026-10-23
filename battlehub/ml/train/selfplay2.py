"""PPO with self-play on the v2 simulation (every melee mode), with the arenas stepped in worker processes.

The learner is slot 0 of every arena; the other slots are played by opponents drawn per arena and slot when a fight
starts: an earlier version of the learner from the pool, the scripted bot with a random skill, random play or a
dummy that only walks about. A curriculum widens three things over the first steps:
  * the scenarios (Batch2.breadth): from flat duels with the sword kit to every map, kit, item and 1v2 / three-way;
  * the opponents: from dummies and weak bots to the pool;
  * the shaping (getting closer, facing the target) fades to half later on, so far fights with a bow can pay.
Checkpoints every --save-every steps; each one is evaluated in a separate, low-priority process (train/eval2.py) that
writes <run>/eval.jsonl. --resume continues a run.

A follow-up run (--init <checkpoint>) starts from a trained policy with every scenario and the pool from the start;
--items deals mostly kits with items (bows, golden apples, food, pearls, wind charges, shields) and --ent raises the
exploration, for learning to use them. --focus <ml_feedback.json> (written by the mod after matches with learned
bots) deals more of what they lacked there: the lessons of the model reports (train/focus.py).

Learning items (phase 3A): --masks trains with MaskablePPO (sb3-contrib) and the action masks of
spec/combat_v2_masks.json, for the learner and its frozen opponents alike; --extra-items gives everyone more items in
that share of the loot and shop arenas; --scripted-latch makes the scripted opponents throw pearls and wind charges;
--bc-data adds a copying term (train/bc.py: the teacher's item choices and its aim) after every PPO update, its weight
falling from --bc-weight to 0 over --bc-decay steps.

One model instead of v1 and v2 (phase 3B): --v1 lets model v1 play a share of the opponent slots (through the league's
adapter), --family-weight and --pair deal more of a kit and the maps that go with it (sword=3, sword=walled: the flat
sword duel where v1 still fought).

Spec v3 (phase 3C, the default; --spec 2 trains as before): four slots per arena, so besides 1v1, 1v2 and three
against each other there are two against two, the learner with one or two team mates, and four against each other
(Scenario.four, modes4). The other slots, team mates included, are drawn from the pool. A fight with team mates goes on
until a team is left, also after the learner died; a dead learner's masks allow only "nothing", and it keeps its share
of the team's reward (Rewards2.team_spirit). --init takes a v3 checkpoint, e.g. a v2 one made into v3 by
train/upgrade_v3.py, which starts exactly as the v2 model plays.

    python train/selfplay2.py --run runs/v2 --steps 60000000
    python train/selfplay2.py --run runs/v2items --init runs/v2/ckpt_060000000.zip --pool-from runs/v2 --items --ent 0.02 --steps 20000000
    python train/selfplay2.py --run runs/v3a --init runs/v2bc/ckpt_bc.zip --pool-from runs/v2 --masks --extra-items 1.0 \
        --scripted-latch --bc-data bc.npz --bc-weight 0.5 --bc-decay 8000000 --steps 20000000 --spec 2
    python train/upgrade_v3.py runs/v3b/ckpt_004000032.zip runs/v3c/start.zip
    python train/selfplay2.py --run runs/v3c --init runs/v3c/start.zip --pool-from runs/v3b --masks --extra-items 1.0 \
        --scripted-latch --steps 20000000
"""
import argparse
import glob
import json
import multiprocessing as mp
import os
import subprocess
import sys
import time

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from pvp_sim2 import Batch2, Dummy2, Random2, Scripted2  # noqa: E402
from pvp_sim2.spec import SPEC2, SPEC3  # noqa: E402

SPECS = {2: (SPEC2, 3), 3: (SPEC3, 4)}   # the spec and the slots per arena

HERE = os.path.dirname(os.path.abspath(__file__))


# ---------------------------------------------------------------------------------------------- arenas in workers

ITEM_FAMILIES = {"sword": 0.6, "axe": 0.8, "mace": 0.8, "bow": 1.2, "koth": 0.2, "ctf": 0.6, "lms": 1.0, "ffa": 0.8, "bridge": 0.8,
                 "boxing": 0.1, "build_uhc": 1.2, "sumo": 0.2, "bedwars": 1.0, "loot": 1.5}


def worker(conn, n, seed, max_ticks, items=False, lessons=None, extra_items=0.0, family_weight=None, pairs=None, spec_version=3,
           shares=None):
    from pvp_sim2.env import Scenario
    sc = Scenario(extra_items=extra_items)
    for name, v in (shares or {}).items():
        if isinstance(v, dict):
            for key, w in v.items():
                if key not in getattr(sc, name):
                    raise KeyError("%s has no %r" % (name, key))
                getattr(sc, name)[key] = w
        else:
            setattr(sc, name, v)
    if items:
        sc.families = dict(ITEM_FAMILIES)
    for fam, w in (family_weight or {}).items():
        sc.families[fam] = w
    for fam, maps in (pairs or {}).items():
        sc.paired[fam] = list(maps)
    if lessons:
        from train.focus import apply
        sc, _ = apply(sc, lessons)
    spec, k = SPECS[spec_version]
    b = Batch2(n, k=k, seed=seed, max_ticks=max_ticks, scenario=sc, spec=spec)
    while True:
        cmd, data = conn.recv()
        if cmd == "reset":
            conn.send(b.reset())
        elif cmd == "step":
            actions, breadth, shaping = data
            b.breadth, b.shaping = breadth, shaping
            obs, r, d, info = b.step(actions)
            lite = {"winner": info["winner"], "ticks": info["ticks"], "return": info["episode_return"][:, 0], "alive": info["alive"][:, 0]}
            if d.any():
                lite.update(terminal=info["terminal_obs"][d, 0], terrain=info["terrain"], family=info["family"], mode=info["mode"])
            conn.send((obs, r, d, lite))
        elif cmd == "close":
            conn.close()
            return


class Curriculum:
    def __init__(self, warmup=2_000_000, widen=12_000_000, fade_from=15_000_000, fade_to=35_000_000):
        self.warmup, self.widen, self.fade_from, self.fade_to = warmup, widen, fade_from, fade_to
        self.steps = 0

    def breadth(self):
        return 0.1 + 0.9 * float(np.clip(self.steps / self.widen, 0.0, 1.0))

    def shaping(self):
        return 1.0 - 0.5 * float(np.clip((self.steps - self.fade_from) / (self.fade_to - self.fade_from), 0.0, 1.0))

    def progress(self):
        return float(np.clip((self.steps - self.warmup) / max(1, self.widen - self.warmup), 0.0, 1.0))


# ---------------------------------------------------------------------------------------------- opponents

class FrozenPolicy:
    """An earlier version of the learner as an opponent; with masks it samples among the allowed choices only."""

    def __init__(self, policy, steps, masks=False):
        import torch
        self.torch = torch
        self.policy = policy
        self.steps = steps
        self.masks = masks
        self.policy.set_training_mode(False)
        if masks:
            from train.policies import Chooser
            self.chooser = Chooser(policy, masks=True, deterministic=False, seed=int(steps) % (1 << 30))

    def __call__(self, obs, deterministic=False):
        if self.masks and not deterministic:
            return self.chooser(obs)
        with self.torch.no_grad():
            t = self.torch.as_tensor(np.asarray(obs, dtype=np.float32)[:, :self.policy.observation_space.shape[0]])
            return self.policy._predict(t, deterministic=deterministic).cpu().numpy()


class OpponentPool2:
    """Who plays each opponent slot: -3 dummy, -1 random, -2 scripted (a skill per slot), -4 model v1 (through the
    league's adapter, with --v1), -5 the people policy (train/people.py, with --people), k >= 0 frozen policy k."""

    START = {"pool": 0.0, "random": 0.2, "dummy": 0.5}
    END = {"pool": 0.5, "random": 0.04, "dummy": 0.08}

    def __init__(self, rng, curriculum, max_size=24, newest_share=0.5, latch=False, v1=None, v1_share=0.0, people=None, people_share=0.0):
        self.v1 = v1
        self.v1_share = v1_share if v1 is not None else 0.0
        self.people = people
        self.people_share = people_share if people is not None else 0.0
        self.rng = rng
        self.cur = curriculum
        self.frozen = []
        self.max_size = max_size
        self.newest_share = newest_share
        self.scripted = Scripted2(seed=int(rng.integers(1 << 30)), latch=latch)
        self.random = Random2(seed=int(rng.integers(1 << 30)))
        self.dummy = Dummy2(seed=int(rng.integers(1 << 30)))

    def share(self, name):
        f = self.cur.progress()
        return self.START[name] * (1 - f) + self.END[name] * f

    def add(self, frozen):
        self.frozen.append(frozen)
        if len(self.frozen) > self.max_size:
            self.frozen.pop(1 + int(self.rng.integers(len(self.frozen) - 2)))

    def draw(self, m):
        f = self.cur.progress()
        kind = np.full(m, -2)
        skill = self.rng.uniform(0.3 * f, 0.3 + 0.7 * f, size=m)
        u = self.rng.random(m)
        pr, pd = self.share("random"), self.share("dummy")
        pp = self.share("pool") if self.frozen else 0.0
        kind[u < pr] = -1
        kind[(u >= pr) & (u < pr + pd)] = -3
        if self.frozen:
            use = (u >= pr + pd) & (u < pr + pd + pp)
            newest = self.rng.random(m) < self.newest_share
            pick = np.where(newest, len(self.frozen) - 1, self.rng.integers(0, len(self.frozen), size=m))
            kind = np.where(use, pick, kind)
        if self.v1_share > 0:
            kind = np.where(self.rng.random(m) < self.v1_share, -4, kind)
        if self.people_share > 0:
            kind = np.where(self.rng.random(m) < self.people_share, -5, kind)
        return kind, skill

    def act(self, obs, kind, skill):
        out = self.scripted(obs, skill)
        if (kind == -1).any():
            out[kind == -1] = self.random(obs[kind == -1])
        if (kind == -3).any():
            out[kind == -3] = self.dummy(obs[kind == -3])
        if (kind == -4).any():
            out[kind == -4] = self.v1(obs[kind == -4])
        if (kind == -5).any():
            rows = np.nonzero(kind == -5)[0]
            out[rows] = self.people.rows(obs[rows], rows, len(kind))
        for k in np.unique(kind[kind >= 0]):
            rows = kind == k
            out[rows] = self.frozen[int(k)](obs[rows])
        return out


# ---------------------------------------------------------------------------------------------- the VecEnv

def make_vecenv(workers, per, seed, pool, cur, max_ticks, items=False, lessons=None, extra_items=0.0, family_weight=None, pairs=None,
                spec_version=3, shares=None):
    from gymnasium import spaces
    from stable_baselines3.common.vec_env import VecEnv

    class SelfPlay2VecEnv(VecEnv):
        def __init__(self):
            ctx = mp.get_context("spawn")
            self.conns, self.procs = [], []
            for w in range(workers):
                a, b = ctx.Pipe()
                p = ctx.Process(target=worker, args=(b, per, seed * 100 + w, max_ticks, items, lessons, extra_items, family_weight, pairs,
                                                     spec_version, shares), daemon=True)
                p.start()
                self.conns.append(a)
                self.procs.append(p)
            n = workers * per
            spec, self.k = SPECS[spec_version]
            super().__init__(n, spaces.Box(-1.0, 1.0, shape=(spec.obs_dim,), dtype=np.float32), spaces.MultiDiscrete(spec.action_nvec))
            self.pool, self.cur = pool, cur
            others = self.k - 1
            self.kind, self.skill = pool.draw(n * others)
            self.kind, self.skill = self.kind.reshape(n, others), self.skill.reshape(n, others)
            self.alive = np.ones(n, dtype=bool)
            self.obs = None
            self.actions = None
            self.results_by = {}
            self.scenarios = {}

        def reset(self):
            for c in self.conns:
                c.send(("reset", None))
            self.obs = np.concatenate([c.recv() for c in self.conns], axis=0)
            self.alive[:] = True
            return self.obs[:, 0].copy()

        def step_async(self, actions):
            self.actions = np.asarray(actions, dtype=np.int64)

        def step_wait(self):
            n, others = self.num_envs, self.k - 1
            opp = self.pool.act(self.obs[:, 1:].reshape(n * others, -1), self.kind.reshape(-1), self.skill.reshape(-1)).reshape(n, others, -1)
            acts = np.concatenate([self.actions[:, None, :], opp], axis=1)
            br, sh = self.cur.breadth(), self.cur.shaping()
            for w, c in enumerate(self.conns):
                c.send(("step", (acts[w * per:(w + 1) * per], br, sh)))
            parts = [c.recv() for c in self.conns]
            obs = np.concatenate([p[0] for p in parts], axis=0)
            reward = np.concatenate([p[1] for p in parts], axis=0)
            done = np.concatenate([p[2] for p in parts], axis=0)
            self.alive = np.concatenate([p[3]["alive"] for p in parts], axis=0)
            infos = [{} for _ in range(n)]
            for w, (o, r, d, lite) in enumerate(parts):
                if not d.any():
                    continue
                idx = np.nonzero(d)[0]
                for j, i in enumerate(idx):
                    g = w * per + i
                    win = int(lite["winner"][i])
                    res = "win" if win == 0 else "loss" if win == 1 else "draw"
                    who = {-1: "random", -2: "scripted", -3: "dummy", -4: "v1", -5: "people"}.get(int(self.kind[g, 0]), "pool")
                    self.results_by.setdefault(who, {"win": 0, "loss": 0, "draw": 0})[res] += 1
                    sc = "%s/%s/%s" % (lite["terrain"][j], lite["family"][j], lite["mode"][j])
                    self.scenarios.setdefault(sc, {"win": 0, "loss": 0, "draw": 0})[res] += 1
                    infos[g] = {"terminal_observation": lite["terminal"][j].copy(),
                                "episode": {"r": float(lite["return"][i]), "l": int(lite["ticks"][i])}}
                k, s = self.pool.draw(idx.size * others)
                self.kind[w * per + idx] = k.reshape(-1, others)
                self.skill[w * per + idx] = s.reshape(-1, others)
            self.obs = obs
            return obs[:, 0].copy(), reward[:, 0].copy(), done.copy(), infos

        def close(self):
            for c in self.conns:
                try:
                    c.send(("close", None))
                except (BrokenPipeError, OSError):
                    pass

        def get_attr(self, attr_name, indices=None):
            return [getattr(self, attr_name)] * len(self._idx(indices))

        def set_attr(self, attr_name, value, indices=None):
            setattr(self, attr_name, value)

        def action_masks(self):
            """The learner's action masks (MaskablePPO): what it may choose now in every arena; a dead learner whose
            team fights on may only do nothing."""
            from pvp_sim2.masks import MASKS2, dead_mask
            m = MASKS2(self.obs[:, 0, :MASKS2.spec.obs_dim])
            m[~self.alive] = dead_mask()
            return m

        def env_method(self, method_name, *args, indices=None, **kwargs):
            if method_name == "action_masks":
                m = self.action_masks()
                return [m[i] for i in self._idx(indices)]
            return [None] * len(self._idx(indices))

        def env_is_wrapped(self, wrapper_class, indices=None):
            return [False] * len(self._idx(indices))

        def seed(self, seed=None):
            return [seed] * self.num_envs

        def _idx(self, indices):
            if indices is None:
                return range(self.num_envs)
            return [indices] if isinstance(indices, int) else indices

    return SelfPlay2VecEnv()


# ---------------------------------------------------------------------------------------------- copying next to PPO

class BCTerm:
    """train/bc.py's copying loss after every PPO update: `steps` minibatches of the teacher's data, weighted from
    `weight` down to 0 over `decay` steps. mode "kl": instead of the teacher's choices, the KL of hand and use to a
    reference policy (the copying start), which keeps its item habits without pushing rare items up."""

    def __init__(self, path, weight=0.5, decay=8_000_000, steps=8, batch=4096, start=0, pitch=False, readout=True,
                 mode="ce", ref=None, aim=False):
        from train.bc import BCData
        self.data = BCData(path)
        self.mode, self.ref, self.aim = mode, ref, aim
        self.weight0, self.decay, self.steps, self.batch, self.start = weight, decay, steps, batch, start
        # by default only the hand and use rows of the action layer: a copying term that reaches into the network
        # (and copies the aim) pulled the fight apart within 2 million steps (43% against the scripted bot, from 77%)
        self.pitch, self.readout = pitch, readout

    def weight(self, steps):
        return self.weight0 * max(0.0, 1.0 - (steps - self.start) / max(1, self.decay))


_PPO_BC = None


def ppo_bc_class():
    """MaskablePPO with the copying term (the class is made when needed: sb3-contrib is only needed with --masks)."""
    global _PPO_BC
    if _PPO_BC is None:
        import torch
        from sb3_contrib import MaskablePPO
        from train.bc import aim_loss, bc_loss, kl_hand_use

        class MaskablePPOBC(MaskablePPO):
            bc = None

            def _excluded_save_params(self):
                return super()._excluded_save_params() + ["bc"]

            def train(self):
                super().train()
                w = self.bc.weight(self.num_timesteps) if self.bc is not None else 0.0
                if w <= 0:
                    return
                self.policy.set_training_mode(True)
                losses = []
                for _ in range(self.bc.steps):
                    b = self.bc.data.batch(self.bc.batch)
                    if self.bc.mode == "kl":
                        loss = w * kl_hand_use(self.policy, self.bc.ref, b, readout=self.bc.readout)
                        if self.bc.aim:
                            loss = loss + w * aim_loss(self.policy, self.bc.ref, b)
                    else:
                        loss = w * bc_loss(self.policy, b, self.bc.data.hand_weight, pitch=self.bc.pitch, readout=self.bc.readout)
                    self.policy.optimizer.zero_grad()
                    loss.backward()
                    torch.nn.utils.clip_grad_norm_(self.policy.parameters(), self.max_grad_norm)
                    self.policy.optimizer.step()
                    losses.append(float(loss.detach()))
                self.policy.set_training_mode(False)
                self.logger.record("train/bc_weight", w)
                self.logger.record("train/bc_loss", float(np.mean(losses)))

        _PPO_BC = MaskablePPOBC
    return _PPO_BC


# ---------------------------------------------------------------------------------------------- main

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--run", default="runs/v2")
    ap.add_argument("--steps", type=int, default=60_000_000)
    ap.add_argument("--workers", type=int, default=3)
    ap.add_argument("--per", type=int, default=96, help="arenas per worker")
    ap.add_argument("--n-steps", type=int, default=128)
    ap.add_argument("--batch", type=int, default=9216)
    ap.add_argument("--save-every", type=int, default=2_000_000)
    ap.add_argument("--pool-every", type=int, default=1_000_000)
    ap.add_argument("--seed", type=int, default=1)
    ap.add_argument("--threads", type=int, default=2)
    ap.add_argument("--max-ticks", type=int, default=1600)
    ap.add_argument("--resume", action="store_true")
    ap.add_argument("--no-eval", action="store_true")
    ap.add_argument("--init", default=None, help="start from this checkpoint's policy, with the full curriculum")
    ap.add_argument("--pool-from", nargs="*", default=[], help="fill the opponent pool with these runs' checkpoints")
    ap.add_argument("--items", action="store_true", help="mostly kits with items")
    ap.add_argument("--ent", type=float, default=0.01)
    ap.add_argument("--focus", default=None, help="the mod's ml_feedback.json: deal more of what the bots lacked in its fights")
    ap.add_argument("--masks", action="store_true", help="MaskablePPO with the action masks (learner and pool)")
    ap.add_argument("--extra-items", type=float, default=0.0, help="share of loot and shop arenas with extra items")
    ap.add_argument("--scripted-latch", action="store_true", help="scripted opponents throw pearls and wind charges")
    ap.add_argument("--bc-data", default=None, help="tools/make_bc_data.py's data: a copying term next to PPO (needs --masks)")
    ap.add_argument("--bc-weight", type=float, default=0.5)
    ap.add_argument("--bc-decay", type=int, default=8_000_000)
    ap.add_argument("--bc-steps", type=int, default=8, help="copying minibatches after every PPO update")
    ap.add_argument("--bc-full", action="store_true", help="the copying term reaches the whole network and copies the aim")
    ap.add_argument("--bc-mode", default="ce", choices=["ce", "kl"], help="ce: the teacher's item choices; kl: hand and use near --init")
    ap.add_argument("--bc-aim", action="store_true", help="with --bc-mode kl: also the teacher's aim with bow and throws (action layer only)")
    ap.add_argument("--v1", default=None, help="model v1's checkpoint as an opponent (phase 3B: v2 learns to beat it)")
    ap.add_argument("--v1-share", type=float, default=0.2, help="share of the opponent slots v1 plays (with --v1)")
    ap.add_argument("--people", default=None, help="train/people.py's run: the people policy as an opponent (phase 3D)")
    ap.add_argument("--people-share", type=float, default=0.15, help="share of the opponent slots it plays (with --people)")
    ap.add_argument("--family-weight", nargs="*", default=[], help="kit weights over the default, e.g. sword=3 boxing=0.6")
    ap.add_argument("--pair", nargs="*", default=[], help="maps that go with a kit most of the time, e.g. sword=walled")
    ap.add_argument("--spec", type=int, default=3, choices=[2, 3], help="3: team mates, a third enemy, memory, four slots")
    ap.add_argument("--eval-hands", default=None, help="the hands the evaluation lets the model choose (eval2.py --hands), as the mod will")
    ap.add_argument("--three", type=float, default=None, help="share of arenas with three fighters (default: the Scenario's)")
    ap.add_argument("--four", type=float, default=None, help="share of arenas with four fighters (spec 3; default: the Scenario's)")
    ap.add_argument("--mode-weight", nargs="*", default=[], help="weights of who fights whom, e.g. 1v2=0.7 ffa=0.2 (modes3 and modes4)")
    ap.add_argument("--terrain-weight", nargs="*", default=[], help="map weights instead of the default, e.g. town=2.5")
    a = ap.parse_args()
    if a.bc_data and not a.masks:
        sys.exit("--bc-data needs --masks")
    import torch
    from stable_baselines3 import PPO
    from stable_baselines3.common.callbacks import BaseCallback
    from stable_baselines3.common.vec_env import VecNormalize

    torch.set_num_threads(a.threads)
    os.makedirs(a.run, exist_ok=True)
    rng = np.random.default_rng(a.seed)
    cur = Curriculum() if not a.init else Curriculum(warmup=0, widen=1, fade_from=0, fade_to=1)
    v1 = None
    if a.v1:
        from train.league2 import V1Adapter
        v1 = V1Adapter(a.v1)
        print("v1 from", a.v1, "in", a.v1_share, "of the opponent slots", flush=True)
    family_weight = {kv.split("=")[0]: float(kv.split("=")[1]) for kv in a.family_weight}
    pairs = {kv.split("=")[0]: kv.split("=")[1].split(",") for kv in a.pair}
    people = None
    if a.people:
        from train.people import People
        people = People.load(a.people, seed=a.seed)
        print("people from", a.people, "in", a.people_share, "of the opponent slots", flush=True)
    pool = OpponentPool2(rng, cur, latch=a.scripted_latch, v1=v1, v1_share=a.v1_share, people=people, people_share=a.people_share)
    lessons = None
    if a.focus:
        from train.focus import apply, load
        from pvp_sim2.env import Scenario
        lessons = load(a.focus, "v%d" % a.spec)
        for note in apply(Scenario(), lessons)[1]:
            print("focus:", note, flush=True)
        if any(x["items"] for x in lessons) and not a.items:
            print("focus: some lessons need the items; add --items to practise them", flush=True)
    shares = {k: v for k, v in (("three", a.three), ("four", a.four)) if v is not None}
    from pvp_sim2.env import Scenario
    modes = {kv.split("=")[0]: float(kv.split("=")[1]) for kv in a.mode_weight}
    for name in ("modes3", "modes4"):
        part = {k: v for k, v in modes.items() if k in getattr(Scenario(), name)}
        if part:
            shares[name] = part
    if a.terrain_weight:
        shares["terrains"] = {kv.split("=")[0]: float(kv.split("=")[1]) for kv in a.terrain_weight}
    venv = make_vecenv(a.workers, a.per, a.seed, pool, cur, a.max_ticks, a.items, lessons, a.extra_items, family_weight, pairs, a.spec, shares)
    from train.policies import load
    for run in a.pool_from:
        for path in sorted(glob.glob(os.path.join(run, "ckpt_*.zip")))[::2]:
            m = load(path)
            pool.add(FrozenPolicy(m.policy, int(os.path.basename(path)[5:14]), masks=a.masks))
        print("pool from", run, len(pool.frozen), flush=True)
    Algo = ppo_bc_class() if a.masks else PPO
    norm_path = os.path.join(a.run, "vecnormalize.pkl")
    ckpts = sorted(glob.glob(os.path.join(a.run, "ckpt_*.zip")))
    if a.resume and ckpts:
        env = VecNormalize.load(norm_path, venv) if os.path.exists(norm_path) else VecNormalize(venv, norm_obs=False, norm_reward=True)
        model = Algo.load(ckpts[-1], env=env, device="cpu")
        for path in ckpts:
            m = load(path)
            pool.add(FrozenPolicy(m.policy, int(os.path.basename(path)[5:14]), masks=a.masks))
        print("resumed from", ckpts[-1], "at", model.num_timesteps, "steps, pool", len(pool.frozen), flush=True)
    else:
        env = VecNormalize(venv, norm_obs=False, norm_reward=True, gamma=0.99)
        model = Algo("MlpPolicy", env, n_steps=a.n_steps, batch_size=a.batch, n_epochs=4, learning_rate=3e-4, gamma=0.99,
                    gae_lambda=0.95, clip_range=0.2, ent_coef=a.ent, vf_coef=0.5, max_grad_norm=0.5,
                    policy_kwargs=dict(net_arch=dict(pi=[256, 256], vf=[256, 256])), seed=a.seed, device="cpu", verbose=0)
        if a.init:
            src = load(a.init)
            model.policy.load_state_dict(src.policy.state_dict())
            print("policy from", a.init, flush=True)
    if a.bc_data:
        ref = None
        if a.bc_mode == "kl":
            ref = load(a.init).policy
            ref.set_training_mode(False)
        model.bc = BCTerm(a.bc_data, a.bc_weight, a.bc_decay, a.bc_steps, start=0 if a.resume else model.num_timesteps,
                          pitch=a.bc_full, readout=not a.bc_full, mode=a.bc_mode, ref=ref, aim=a.bc_aim)
        print("copying term from", a.bc_data, "weight", a.bc_weight, "to 0 over", a.bc_decay, "steps", flush=True)
    with open(os.path.join(a.run, "config.json"), "w") as f:
        json.dump(vars(a) | {"spec_sha256": SPECS[a.spec][0].sha256}, f, indent=1)
    log_path = os.path.join(a.run, "progress.jsonl")

    class Checkpoints(BaseCallback):
        def __init__(self):
            super().__init__()
            self.t0 = time.time()
            self.s0 = 0
            self.eval_proc = None

        def _init_callback(self):
            steps = self.model.num_timesteps
            self.s0 = steps
            self.next_save = (steps // a.save_every + 1) * a.save_every
            self.next_pool = (steps // a.pool_every + 1) * a.pool_every

        def _on_step(self):
            steps = self.model.num_timesteps
            cur.steps = steps
            if steps >= self.next_pool:
                self.next_pool += a.pool_every
                policy = self.model.policy.__class__(self.model.observation_space, self.model.action_space, self.model.lr_schedule, **self.model.policy_kwargs)
                policy.load_state_dict(self.model.policy.state_dict())
                pool.add(FrozenPolicy(policy.to("cpu"), steps, masks=a.masks))
            if steps >= self.next_save:
                self.next_save += a.save_every
                self.save(steps)
            return True

        def _on_rollout_end(self):
            env.save(norm_path)

        def save(self, steps):
            path = os.path.join(a.run, "ckpt_%09d.zip" % steps)
            self.model.save(path)
            minutes = (time.time() - self.t0) / 60
            row = {"steps": int(steps), "minutes": round(minutes, 1), "fps": round((steps - self.s0) / max(1.0, time.time() - self.t0)),
                   "breadth": round(cur.breadth(), 2), "shaping": round(cur.shaping(), 2), "pool": len(pool.frozen),
                   "bc_weight": round(model.bc.weight(steps), 3) if getattr(model, "bc", None) is not None else None,
                   "by_opponent": venv.results_by, "scenarios": venv.scenarios}
            with open(log_path, "a") as f:
                f.write(json.dumps(row) + "\n")
            print(json.dumps({k: v for k, v in row.items() if k != "scenarios"}), flush=True)
            venv.results_by, venv.scenarios = {}, {}
            if not a.no_eval and (self.eval_proc is None or self.eval_proc.poll() is not None):
                hands = (["--hands", a.eval_hands] if a.eval_hands else []) + (["--people", a.people] if a.people else [])
                self.eval_proc = subprocess.Popen(["nice", "-n", "15", sys.executable, os.path.join(HERE, "eval2.py"), path,
                                                   "--out", os.path.join(a.run, "eval.jsonl")] + hands,
                                                  stdout=subprocess.DEVNULL, stderr=open(os.path.join(a.run, "eval.err"), "a"))

    cb = Checkpoints()
    remaining = max(0, a.steps - model.num_timesteps)
    try:
        model.learn(total_timesteps=remaining, callback=cb, reset_num_timesteps=not a.resume, progress_bar=False)
        cb.save(model.num_timesteps)
        env.save(norm_path)
    finally:
        venv.close()


if __name__ == "__main__":
    main()
