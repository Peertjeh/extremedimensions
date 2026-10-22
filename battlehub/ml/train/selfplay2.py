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

    python train/selfplay2.py --run runs/v2 --steps 60000000
    python train/selfplay2.py --run runs/v2items --init runs/v2/ckpt_060000000.zip --pool-from runs/v2 --items --ent 0.02 --steps 20000000
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
from pvp_sim2.spec import SPEC2  # noqa: E402

HERE = os.path.dirname(os.path.abspath(__file__))


# ---------------------------------------------------------------------------------------------- arenas in workers

ITEM_FAMILIES = {"sword": 0.6, "axe": 0.8, "mace": 0.8, "bow": 1.2, "koth": 0.2, "ctf": 0.6, "lms": 1.0, "ffa": 0.8, "bridge": 0.8,
                 "boxing": 0.1, "build_uhc": 1.2, "sumo": 0.2, "bedwars": 1.0, "loot": 1.5}


def worker(conn, n, seed, max_ticks, items=False, lessons=None):
    from pvp_sim2.env import Scenario
    sc = Scenario()
    if items:
        sc.families = dict(ITEM_FAMILIES)
    if lessons:
        from train.focus import apply
        sc, _ = apply(sc, lessons)
    b = Batch2(n, seed=seed, max_ticks=max_ticks, scenario=sc)
    while True:
        cmd, data = conn.recv()
        if cmd == "reset":
            conn.send(b.reset())
        elif cmd == "step":
            actions, breadth, shaping = data
            b.breadth, b.shaping = breadth, shaping
            obs, r, d, info = b.step(actions)
            lite = {"winner": info["winner"], "ticks": info["ticks"], "return": info["episode_return"][:, 0]}
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
    def __init__(self, policy, steps):
        import torch
        self.torch = torch
        self.policy = policy
        self.steps = steps
        self.policy.set_training_mode(False)

    def __call__(self, obs, deterministic=False):
        with self.torch.no_grad():
            t = self.torch.as_tensor(np.asarray(obs, dtype=np.float32))
            return self.policy._predict(t, deterministic=deterministic).cpu().numpy()


class OpponentPool2:
    """Who plays each opponent slot: -3 dummy, -1 random, -2 scripted (a skill per slot), k >= 0 frozen policy k."""

    START = {"pool": 0.0, "random": 0.2, "dummy": 0.5}
    END = {"pool": 0.5, "random": 0.04, "dummy": 0.08}

    def __init__(self, rng, curriculum, max_size=24, newest_share=0.5):
        self.rng = rng
        self.cur = curriculum
        self.frozen = []
        self.max_size = max_size
        self.newest_share = newest_share
        self.scripted = Scripted2(seed=int(rng.integers(1 << 30)))
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
        return kind, skill

    def act(self, obs, kind, skill):
        out = self.scripted(obs, skill)
        if (kind == -1).any():
            out[kind == -1] = self.random(obs[kind == -1])
        if (kind == -3).any():
            out[kind == -3] = self.dummy(obs[kind == -3])
        for k in np.unique(kind[kind >= 0]):
            rows = kind == k
            out[rows] = self.frozen[int(k)](obs[rows])
        return out


# ---------------------------------------------------------------------------------------------- the VecEnv

def make_vecenv(workers, per, seed, pool, cur, max_ticks, items=False, lessons=None):
    from gymnasium import spaces
    from stable_baselines3.common.vec_env import VecEnv

    class SelfPlay2VecEnv(VecEnv):
        def __init__(self):
            ctx = mp.get_context("spawn")
            self.conns, self.procs = [], []
            for w in range(workers):
                a, b = ctx.Pipe()
                p = ctx.Process(target=worker, args=(b, per, seed * 100 + w, max_ticks, items, lessons), daemon=True)
                p.start()
                self.conns.append(a)
                self.procs.append(p)
            n = workers * per
            super().__init__(n, spaces.Box(-1.0, 1.0, shape=(SPEC2.obs_dim,), dtype=np.float32), spaces.MultiDiscrete(SPEC2.action_nvec))
            self.pool, self.cur = pool, cur
            self.kind, self.skill = pool.draw(n * 2)
            self.kind, self.skill = self.kind.reshape(n, 2), self.skill.reshape(n, 2)
            self.obs = None
            self.actions = None
            self.results_by = {}
            self.scenarios = {}

        def reset(self):
            for c in self.conns:
                c.send(("reset", None))
            self.obs = np.concatenate([c.recv() for c in self.conns], axis=0)
            return self.obs[:, 0].copy()

        def step_async(self, actions):
            self.actions = np.asarray(actions, dtype=np.int64)

        def step_wait(self):
            n = self.num_envs
            opp = self.pool.act(self.obs[:, 1:].reshape(n * 2, -1), self.kind.reshape(-1), self.skill.reshape(-1)).reshape(n, 2, -1)
            acts = np.concatenate([self.actions[:, None, :], opp], axis=1)
            br, sh = self.cur.breadth(), self.cur.shaping()
            for w, c in enumerate(self.conns):
                c.send(("step", (acts[w * per:(w + 1) * per], br, sh)))
            parts = [c.recv() for c in self.conns]
            obs = np.concatenate([p[0] for p in parts], axis=0)
            reward = np.concatenate([p[1] for p in parts], axis=0)
            done = np.concatenate([p[2] for p in parts], axis=0)
            infos = [{} for _ in range(n)]
            for w, (o, r, d, lite) in enumerate(parts):
                if not d.any():
                    continue
                idx = np.nonzero(d)[0]
                for j, i in enumerate(idx):
                    g = w * per + i
                    win = int(lite["winner"][i])
                    res = "win" if win == 0 else "loss" if win == 1 else "draw"
                    who = {-1: "random", -2: "scripted", -3: "dummy"}.get(int(self.kind[g, 0]), "pool")
                    self.results_by.setdefault(who, {"win": 0, "loss": 0, "draw": 0})[res] += 1
                    sc = "%s/%s/%s" % (lite["terrain"][j], lite["family"][j], lite["mode"][j])
                    self.scenarios.setdefault(sc, {"win": 0, "loss": 0, "draw": 0})[res] += 1
                    infos[g] = {"terminal_observation": lite["terminal"][j].copy(),
                                "episode": {"r": float(lite["return"][i]), "l": int(lite["ticks"][i])}}
                k, s = self.pool.draw(idx.size * 2)
                self.kind[w * per + idx] = k.reshape(-1, 2)
                self.skill[w * per + idx] = s.reshape(-1, 2)
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

        def env_method(self, method_name, *args, indices=None, **kwargs):
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
    ap.add_argument("--pool-from", default=None, help="fill the opponent pool with this run's checkpoints")
    ap.add_argument("--items", action="store_true", help="mostly kits with items")
    ap.add_argument("--ent", type=float, default=0.01)
    ap.add_argument("--focus", default=None, help="the mod's ml_feedback.json: deal more of what the bots lacked in its fights")
    a = ap.parse_args()
    import torch
    from stable_baselines3 import PPO
    from stable_baselines3.common.callbacks import BaseCallback
    from stable_baselines3.common.vec_env import VecNormalize

    torch.set_num_threads(a.threads)
    os.makedirs(a.run, exist_ok=True)
    rng = np.random.default_rng(a.seed)
    cur = Curriculum() if not a.init else Curriculum(warmup=0, widen=1, fade_from=0, fade_to=1)
    pool = OpponentPool2(rng, cur)
    lessons = None
    if a.focus:
        from train.focus import apply, load
        from pvp_sim2.env import Scenario
        lessons = load(a.focus, "v2")
        for note in apply(Scenario(), lessons)[1]:
            print("focus:", note, flush=True)
        if any(x["items"] for x in lessons) and not a.items:
            print("focus: some lessons need the items; add --items to practise them", flush=True)
    venv = make_vecenv(a.workers, a.per, a.seed, pool, cur, a.max_ticks, a.items, lessons)
    if a.pool_from:
        for path in sorted(glob.glob(os.path.join(a.pool_from, "ckpt_*.zip")))[::2]:
            m = PPO.load(path, device="cpu")
            pool.add(FrozenPolicy(m.policy, int(os.path.basename(path)[5:14])))
        print("pool from", a.pool_from, len(pool.frozen), flush=True)
    norm_path = os.path.join(a.run, "vecnormalize.pkl")
    ckpts = sorted(glob.glob(os.path.join(a.run, "ckpt_*.zip")))
    if a.resume and ckpts:
        env = VecNormalize.load(norm_path, venv) if os.path.exists(norm_path) else VecNormalize(venv, norm_obs=False, norm_reward=True)
        model = PPO.load(ckpts[-1], env=env, device="cpu")
        for path in ckpts:
            m = PPO.load(path, device="cpu")
            pool.add(FrozenPolicy(m.policy, int(os.path.basename(path)[5:14])))
        print("resumed from", ckpts[-1], "at", model.num_timesteps, "steps, pool", len(pool.frozen), flush=True)
    else:
        env = VecNormalize(venv, norm_obs=False, norm_reward=True, gamma=0.99)
        model = PPO("MlpPolicy", env, n_steps=a.n_steps, batch_size=a.batch, n_epochs=4, learning_rate=3e-4, gamma=0.99,
                    gae_lambda=0.95, clip_range=0.2, ent_coef=a.ent, vf_coef=0.5, max_grad_norm=0.5,
                    policy_kwargs=dict(net_arch=dict(pi=[256, 256], vf=[256, 256])), seed=a.seed, device="cpu", verbose=0)
        if a.init:
            src = PPO.load(a.init, device="cpu")
            model.policy.load_state_dict(src.policy.state_dict())
            print("policy from", a.init, flush=True)
    with open(os.path.join(a.run, "config.json"), "w") as f:
        json.dump(vars(a) | {"spec_sha256": SPEC2.sha256}, f, indent=1)
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
                pool.add(FrozenPolicy(policy.to("cpu"), steps))
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
                   "by_opponent": venv.results_by, "scenarios": venv.scenarios}
            with open(log_path, "a") as f:
                f.write(json.dumps(row) + "\n")
            print(json.dumps({k: v for k, v in row.items() if k != "scenarios"}), flush=True)
            venv.results_by, venv.scenarios = {}, {}
            if not a.no_eval and (self.eval_proc is None or self.eval_proc.poll() is not None):
                self.eval_proc = subprocess.Popen(["nice", "-n", "15", sys.executable, os.path.join(HERE, "eval2.py"), path,
                                                   "--out", os.path.join(a.run, "eval.jsonl")],
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
