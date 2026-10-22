"""PPO with self-play on the 1v1 simulation (Stable-Baselines3).

The learner fights in n arenas at once as fighter 0. Each arena draws its opponent when a fight starts:
  * an earlier version of the learner from the pool (saved every --pool-every steps; the newest ones more often),
  * the scripted bot with a random skill,
  * now and then random play.
Checkpoints are written every --save-every steps into the run directory, with an evaluation against the scripted bot.
A run can be resumed (--resume): the newest checkpoint and the pool are picked up again.

    python train/selfplay.py --run runs/sword --steps 30000000
"""
import argparse
import glob
import json
import os
import sys
import time

import numpy as np
import torch
from gymnasium import spaces
from stable_baselines3 import PPO
from stable_baselines3.common.callbacks import BaseCallback
from stable_baselines3.common.vec_env import VecEnv, VecNormalize

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from pvp_sim import BOXING, SWORD, DummyPolicy, PvpBatch, RandomPolicy, ScriptedPolicy  # noqa: E402
from pvp_sim.spec import SPEC  # noqa: E402

RULES = {"sword": SWORD, "boxing": BOXING}


# ---------------------------------------------------------------------------------------------- opponents

class FrozenPolicy:
    """An earlier learner: the policy network only, on the CPU, sampling its actions."""

    def __init__(self, policy, steps):
        self.policy = policy
        self.steps = steps
        self.policy.set_training_mode(False)

    def __call__(self, obs, deterministic=False):
        with torch.no_grad():
            t = torch.as_tensor(np.asarray(obs, dtype=np.float32))
            return self.policy._predict(t, deterministic=deterministic).cpu().numpy()


class OpponentPool:
    """Who fighter 1 is in each arena: -3 a dummy that never swings, -1 random, -2 scripted (with a skill per arena),
    k >= 0 frozen policy k. A curriculum moves from easy targets to the pool over the first `curriculum` steps:
    first it has to find out that hitting pays at all."""

    def __init__(self, rng, max_size=24, p_pool=0.5, p_random=0.05, p_dummy=0.05, newest_share=0.5, warmup=1_500_000, curriculum=5_000_000):
        self.rng = rng
        self.frozen = []
        self.max_size = max_size
        self.end = {"pool": p_pool, "random": p_random, "dummy": p_dummy}
        self.start = {"pool": 0.0, "random": 0.3, "dummy": 0.6}
        self.warmup = warmup
        self.newest_share = newest_share
        self.curriculum = curriculum
        self.steps = 0
        self.scripted = ScriptedPolicy(seed=int(rng.integers(1 << 30)))
        self.random = RandomPolicy(seed=int(rng.integers(1 << 30)))
        self.dummy = DummyPolicy(seed=int(rng.integers(1 << 30)))

    def progress(self):
        """0 during the warm-up (only easy targets and weak bots), then up to 1 at the end of the curriculum."""
        return float(np.clip((self.steps - self.warmup) / max(1, self.curriculum - self.warmup), 0.0, 1.0))

    def share(self, name):
        f = self.progress()
        return self.start[name] * (1 - f) + self.end[name] * f

    def add(self, frozen):
        self.frozen.append(frozen)
        if len(self.frozen) > self.max_size:
            # keep the first, thin out the middle
            self.frozen.pop(1 + int(self.rng.integers(len(self.frozen) - 2)))

    def draw(self, m):
        f = self.progress()
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
        out[kind == -1] = self.random(obs)[kind == -1]
        out[kind == -3] = self.dummy(obs)[kind == -3]
        for k in np.unique(kind[kind >= 0]):
            rows = kind == k
            out[rows] = self.frozen[int(k)](obs[rows])
        return out


# ---------------------------------------------------------------------------------------------- the vectorised env

class SelfPlayVecEnv(VecEnv):
    """n arenas as an SB3 VecEnv: the learner is fighter 0, the pool plays fighter 1."""

    def __init__(self, n, rules, seed, pool, max_ticks=1200):
        self.batch = PvpBatch(n, rules, seed=seed, max_ticks=max_ticks, auto_reset=True)
        self.pool = pool
        obs_space = spaces.Box(-1.0, 1.0, shape=(SPEC.obs_dim,), dtype=np.float32)
        act_space = spaces.MultiDiscrete(SPEC.action_nvec)
        super().__init__(n, obs_space, act_space)
        self.kind, self.skill = pool.draw(n)
        self.obs = None
        self.actions = None
        self.results_by = {}

    def reset(self):
        self.obs = self.batch.reset()
        self.kind, self.skill = self.pool.draw(self.num_envs)
        return self.obs[:, 0].copy()

    def step_async(self, actions):
        self.actions = np.asarray(actions, dtype=np.int64)

    def step_wait(self):
        opp = self.pool.act(self.obs[:, 1], self.kind, self.skill)
        obs, reward, done, info = self.batch.step(np.stack([self.actions, opp], axis=1))
        infos = [{} for _ in range(self.num_envs)]
        if done.any():
            idx = np.nonzero(done)[0]
            for i in idx:
                w = int(info["winner"][i])
                res = "win" if w == 0 else "loss" if w == 1 else "draw"
                who = {-1: "random", -2: "scripted", -3: "dummy"}.get(int(self.kind[i]), "pool")
                self.results_by.setdefault(who, {"win": 0, "loss": 0, "draw": 0})[res] += 1
                infos[i] = {"terminal_observation": info["terminal_obs"][i, 0].copy(),
                            "episode": {"r": float(info["episode_return"][i, 0]), "l": int(info["ticks"][i])},
                            "winner": w}
            k, s = self.pool.draw(idx.size)
            self.kind[idx] = k
            self.skill[idx] = s
        self.obs = obs
        return obs[:, 0].copy(), reward[:, 0].copy(), done.copy(), infos

    def close(self):
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


# ---------------------------------------------------------------------------------------------- evaluation

def play(policy_a, policy_b, episodes=400, rules=SWORD, seed=0, n=200, max_ticks=1200):
    """Fights between two policies (callables obs -> actions); returns wins, losses and draws of policy_a."""
    b = PvpBatch(n, rules, seed=seed, max_ticks=max_ticks, auto_reset=True)
    obs = b.reset()
    res = np.zeros(3, dtype=np.int64)
    while res.sum() < episodes:
        acts = np.stack([policy_a(obs[:, 0]), policy_b(obs[:, 1])], axis=1)
        obs, r, d, info = b.step(acts)
        for w in info["winner"][d]:
            res[0 if w == 0 else 1 if w == 1 else 2] += 1
    return res


def model_fn(model, deterministic=True):
    def act(obs):
        a, _ = model.predict(obs, deterministic=deterministic)
        return a
    return act


class Checkpoints(BaseCallback):
    """Saves checkpoints, grows the opponent pool and evaluates against the scripted bot."""

    def __init__(self, run, venv, pool, save_every, pool_every, rules):
        super().__init__()
        self.run = run
        self.venv = venv
        self.pool = pool
        self.save_every = save_every
        self.pool_every = pool_every
        self.rules = rules
        self.next_save = 0
        self.next_pool = 0
        self.t0 = time.time()
        self.log_path = os.path.join(run, "progress.jsonl")

    def _init_callback(self):
        steps = self.model.num_timesteps
        self.next_save = (steps // self.save_every + 1) * self.save_every
        self.next_pool = (steps // self.pool_every + 1) * self.pool_every

    def _on_step(self):
        steps = self.model.num_timesteps
        self.pool.steps = steps
        if steps >= self.next_pool:
            self.next_pool += self.pool_every
            self.pool.add(frozen_copy(self.model, steps))
        if steps >= self.next_save:
            self.next_save += self.save_every
            self.save(steps)
        return True

    def save(self, steps):
        path = os.path.join(self.run, "ckpt_%09d.zip" % steps)
        self.model.save(path)
        vs = {}
        for skill in (0.5, 0.8, 1.0):
            res = play(model_fn(self.model), ScriptedPolicy(skill, seed=steps % 9973), episodes=300, rules=self.rules, seed=steps % 7919)
            vs["scripted_%.1f" % skill] = "%d/%d/%d" % tuple(res)
        res = play(model_fn(self.model), DummyPolicy(seed=steps % 31), episodes=200, rules=self.rules, seed=steps % 101)
        vs["dummy"] = "%d/%d/%d" % tuple(res)
        row = {"steps": int(steps), "minutes": round((time.time() - self.t0) / 60, 1), "score_vs": vs,
               "pool": len(self.pool.frozen), "training_results": getattr(self.venv, "results_by", None)}
        with open(self.log_path, "a") as f:
            f.write(json.dumps(row) + "\n")
        print(json.dumps(row), flush=True)
        self.venv.results_by = {}


def frozen_copy(model, steps):
    policy = model.policy.__class__(model.observation_space, model.action_space, model.lr_schedule, **model.policy_kwargs)
    policy.load_state_dict(model.policy.state_dict())
    return FrozenPolicy(policy.to("cpu"), steps)


def load_pool(run, pool, model):
    """The pool of a resumed run: the saved checkpoints, thinned like the pool itself."""
    for path in sorted(glob.glob(os.path.join(run, "ckpt_*.zip"))):
        m = PPO.load(path, device="cpu")
        pool.add(FrozenPolicy(m.policy, int(os.path.basename(path)[5:14])))


# ---------------------------------------------------------------------------------------------- main

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--run", default="runs/sword")
    ap.add_argument("--rules", default="sword", choices=sorted(RULES))
    ap.add_argument("--steps", type=int, default=30_000_000)
    ap.add_argument("--envs", type=int, default=64)
    ap.add_argument("--n-steps", type=int, default=256)
    ap.add_argument("--save-every", type=int, default=1_000_000)
    ap.add_argument("--pool-every", type=int, default=500_000)
    ap.add_argument("--seed", type=int, default=1)
    ap.add_argument("--threads", type=int, default=4)
    ap.add_argument("--resume", action="store_true")
    args = ap.parse_args()
    torch.set_num_threads(args.threads)
    os.makedirs(args.run, exist_ok=True)
    rules = RULES[args.rules]
    rng = np.random.default_rng(args.seed)
    pool = OpponentPool(rng)
    venv = SelfPlayVecEnv(args.envs, rules, args.seed, pool)
    norm_path = os.path.join(args.run, "vecnormalize.pkl")
    ckpts = sorted(glob.glob(os.path.join(args.run, "ckpt_*.zip")))
    if args.resume and ckpts:
        env = VecNormalize.load(norm_path, venv) if os.path.exists(norm_path) else VecNormalize(venv, norm_obs=False, norm_reward=True)
        model = PPO.load(ckpts[-1], env=env, device="cpu")
        load_pool(args.run, pool, model)
        print("resumed from", ckpts[-1], "at", model.num_timesteps, "steps, pool", len(pool.frozen), flush=True)
    else:
        env = VecNormalize(venv, norm_obs=False, norm_reward=True, gamma=0.99)
        model = PPO("MlpPolicy", env, n_steps=args.n_steps, batch_size=4096, n_epochs=4, learning_rate=3e-4, gamma=0.99,
                    gae_lambda=0.95, clip_range=0.2, ent_coef=0.01, vf_coef=0.5, max_grad_norm=0.5,
                    policy_kwargs=dict(net_arch=dict(pi=[128, 128], vf=[128, 128])), seed=args.seed, device="cpu", verbose=0)
    with open(os.path.join(args.run, "config.json"), "w") as f:
        json.dump(vars(args) | {"spec_sha256": SPEC.sha256}, f, indent=1)
    cb = Checkpoints(args.run, venv, pool, args.save_every, args.pool_every, rules)

    class SaveNorm(BaseCallback):
        def _on_rollout_end(self):
            env.save(norm_path)

        def _on_step(self):
            return True

    remaining = max(0, args.steps - model.num_timesteps)
    model.learn(total_timesteps=remaining, callback=[cb, SaveNorm()], reset_num_timesteps=not args.resume, progress_bar=False)
    cb.save(model.num_timesteps)
    env.save(norm_path)


if __name__ == "__main__":
    main()
