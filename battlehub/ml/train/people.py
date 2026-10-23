"""An opponent that fights like the people in the game's fight logs (ML phase 3D): behavior cloning on the decisions
of people who were really fighting (their target in sight within 8 blocks), from the master's checkpoint and held
close to it (a KL term to the master on the same observations), so that a few minutes of people do not take it off
the rails. Self-play puts it in the pool of opponents (selfplay2.py --people) and eval2 has a block against it.

    python train/people.py data/ --init runs/v3d/ckpt_006000192.zip --out runs/people

Fights are split as train/simfit.py splits them: it learns from the fit part and is judged on the test part, against
the master (does it predict what the person did more often?), and its behaviour in the simulation (sprinting, strafe
changes, jumps and clicks per second, the charge of its hits, the distance it keeps) against the people's in the logs.
Writes <out>/ckpt_people.zip and <out>/people.json.
"""
import argparse
import copy
import json
import os
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from pvp_sim.actions import decode  # noqa: E402
from pvp_sim2.spec import SPEC3  # noqa: E402
from train import fights as F  # noqa: E402
from train.imitate import heads, heads_accuracy  # noqa: E402
from train.simfit import part_of  # noqa: E402
from train.policies import Chooser  # noqa: E402

NEAR = 8.0
TICK = 1 / 20.0


def fighting(s):
    """The rows whose fighter had its target in sight within 8 blocks."""
    d = np.hypot(s["opp_x"] - s["self_x"], s["opp_z"] - s["self_z"])
    return s["opp_visible"] & (d < NEAR)


def arrays(fights, part, who="human"):
    """Observations and action indices of `who`'s fighting rows in the fights of `part` ("fit", "test", "all")."""
    obs, act = [], []
    for f in fights:
        if part != "all" and part_of(f.name) != part:
            continue
        kinds = [x["kind"] for x in f.header["fighters"]]
        rows = [i for i, r in enumerate(f.rows) if kinds[r["f"]] == who]
        if not rows:
            continue
        s = F.states(f)
        keep = [i for i in rows if fighting({k: v[i:i + 1] for k, v in s.items() if np.ndim(v) == 1})[0]]
        if not keep:
            continue
        o = F.observations(f)
        a = F.actions(f)
        obs.append(o[keep].astype(np.float32))
        act.append(a[keep])
    if not obs:
        return np.zeros((0, SPEC3.obs_dim), np.float32), np.zeros((0, len(SPEC3.action_nvec)), np.int64)
    return np.concatenate(obs), np.concatenate(act)


def clone(model, obs, act, epochs=12, batch=512, lr=2e-4, kl=0.5, seed=0, log=print):
    """Behavior cloning with a KL term to the policy it started from: -log p(what was done) + kl * KL(start || policy)."""
    import torch
    policy = model.policy
    anchor = copy.deepcopy(policy)
    anchor.set_training_mode(False)
    for p in anchor.parameters():
        p.requires_grad_(False)
    rng = np.random.default_rng(seed)
    opt = torch.optim.Adam(policy.parameters(), lr=lr)
    history = []
    for epoch in range(epochs):
        policy.set_training_mode(True)
        order = rng.permutation(len(obs))
        losses = []
        for i in range(0, len(order), batch):
            idx = order[i:i + batch]
            x = torch.as_tensor(obs[idx])
            dist = policy.get_distribution(x)
            with torch.no_grad():
                ref = anchor.get_distribution(x)
            logp = dist.log_prob(torch.as_tensor(act[idx]))
            div = sum(torch.distributions.kl_divergence(r, d) for r, d in zip(heads(ref), heads(dist)))
            loss = -logp.mean() + kl * div.mean()
            opt.zero_grad()
            loss.backward()
            torch.nn.utils.clip_grad_norm_(policy.parameters(), 0.5)
            opt.step()
            losses.append(float(loss))
        policy.set_training_mode(False)
        history.append({"epoch": epoch + 1, "loss": round(float(np.mean(losses)), 4)})
        log(json.dumps(history[-1]))
    return history


def change_rates_logs(fights, who="human"):
    """Per action head, how often `who` changed its choice from one fighting decision to the next in the logs."""
    changed, total = np.zeros(len(SPEC3.action_nvec)), 0
    for f in fights:
        kinds = [x["kind"] for x in f.header["fighters"]]
        s = F.states(f)
        fight = fighting(s)
        act = F.actions(f)
        last = {}
        for i, r in enumerate(f.rows):
            if kinds[r["f"]] != who or not fight[i]:
                last.pop(r["f"], None)
                continue
            if r["f"] in last and r["t"] - last[r["f"]][0] == f.header.get("decision_ticks", 2):
                changed += act[i] != last[r["f"]][1]
                total += 1
            last[r["f"]] = (r["t"], act[i])
    return changed / max(1, total)


CALIBRATE = {"sprint": "sprint_share", "jump": "jumps_per_s", "attack": "clicks_per_s"}


class People:
    """The people policy as an opponent: it samples its choices (allowed ones, as the masks say), with a shift of the
    logits of some choices (`offsets`: sprinting, jumping and clicking as often as people do, fitted in the simulation),
    and per action head keeps its last choice with the head's stickiness (people hold a key; a sample every decision
    would change it ten times a second)."""

    def __init__(self, policy, stick, offsets=None, seed=0):
        import torch
        from train import policies
        self.torch, self.policies = torch, policies
        self.policy = policy
        policy.set_training_mode(False)
        self.dim = int(policy.observation_space.shape[0])
        self.stick = np.asarray(stick, dtype=np.float64)
        self.shifts = dict(offsets or {})
        self.offsets = np.zeros(int(np.sum(SPEC3.action_nvec)), dtype=np.float32)
        for head, shift in (offsets or {}).items():
            self.offsets[int(np.sum(SPEC3.action_nvec[:SPEC3.action_index(head)])) + 1] = shift
        self.gen = torch.Generator().manual_seed(seed)
        self.rng = np.random.default_rng(seed)
        self.last = None

    def __call__(self, obs):
        return self.rows(obs)

    def __deepcopy__(self, memo):
        """A copy for another slot (eval2.per_slot): the same network, its own choices and random numbers."""
        return People(self.policy, self.stick, self.shifts, seed=int(self.rng.integers(1 << 30)))

    def rows(self, obs, rows=None, size=None):
        """Choices for `obs`; with `rows` (their indices in a batch of `size`) the last choices are kept per row of that
        batch, for a caller that hands over a different subset each time (the training's opponent pool)."""
        act = self._sample(obs)
        if rows is None:
            rows, size = np.arange(len(act)), len(act)
        if self.last is None or len(self.last) != size:
            self.last = np.full((size, act.shape[1]), -1, dtype=act.dtype)
        before = self.last[rows]
        keep = (self.rng.random(act.shape) < self.stick[None, :]) & (before >= 0)
        # the hand is chosen anew (a kept one may no longer be allowed)
        keep[:, SPEC3.action_index("hand")] = False
        act = np.where(keep, before, act)
        self.last[rows] = act
        return act

    def _sample(self, obs):
        torch, P = self.torch, self.policies
        obs = np.asarray(obs, dtype=np.float32)[:, :self.dim]
        with torch.no_grad():
            z = P.logits(self.policy, torch.as_tensor(obs)) + torch.as_tensor(self.offsets)
            m = P.MASKS2(obs[:, :P.MASKS2.spec.obs_dim])
            z = z.masked_fill(~torch.as_tensor(m), -torch.inf)
            return torch.stack([torch.multinomial(torch.softmax(part, dim=1), 1, generator=self.gen)[:, 0]
                                for part in torch.split(z, [int(k) for k in SPEC3.action_nvec], dim=1)], dim=1).numpy()

    @staticmethod
    def load(run, seed=0):
        """<run>/ckpt_people.zip with its stickiness and offsets from <run>/people.json."""
        from train.policies import load
        with open(os.path.join(run, "people.json"), encoding="utf-8") as fh:
            r = json.load(fh)
        return People(load(os.path.join(run, "ckpt_people.zip")).policy, [r["stick"][n] for n in SPEC3.action_names], r["offsets"], seed)


def log_behaviour(fights, who="human"):
    """`who`'s behaviour while fighting in the logs (the numbers of the human-opponent requirement)."""
    sprint, change, jump, click, dist, charge, n = [], [], [], [], [], [], 0
    for f in fights:
        kinds = [x["kind"] for x in f.header["fighters"]]
        s = F.states(f)
        fight = fighting(s)
        names = f.header["action_names"]
        a = np.array([r["a"] for r in f.rows], dtype=np.float64).reshape(len(f.rows), -1)
        last = {}
        for i, r in enumerate(f.rows):
            if kinds[r["f"]] != who or not fight[i]:
                last.pop(r["f"], None)
                continue
            n += 1
            sprint.append(float(s["self_sprinting"][i]))
            st = a[i, names.index("strafe")]
            if r["f"] in last:
                change.append(float(st != last[r["f"]]))
            last[r["f"]] = st
            jump.append(float(a[i, names.index("jump")] > 0.5))
            click.append(float(a[i, names.index("attack")] > 0.5))
            dist.append(float(np.hypot(s["opp_x"][i] - s["self_x"][i], s["opp_z"][i] - s["self_z"][i])))
        for h in f.hits:
            att = h["hit"][0]
            if att >= 0 and kinds[att] == who and h.get("kind", "melee") == "melee":
                rows = [i for i, r in enumerate(f.rows) if r["f"] == att and h["t"] - 2 < r["t"] <= h["t"]]
                if rows and s["self_hand"][rows[-1]] == 0:
                    charge.append(float(s["self_charge"][rows[-1]]))
    return _stats(n, sprint, change, jump, click, dist, charge, 2)


def _stats(n, sprint, change, jump, click, dist, charge, dt):
    sec = max(1, n) * dt * TICK
    return {"decisions": n, "sprint_share": float(np.mean(sprint)) if sprint else None,
            "strafe_changes_per_s": float(np.sum(change)) / sec, "jumps_per_s": float(np.sum(jump)) / sec,
            "clicks_per_s": float(np.sum(click)) / sec, "charge_at_hit": float(np.median(charge)) if charge else None,
            "distance": float(np.mean(dist)) if dist else None}


def sim_behaviour(policy, opponent, n=64, decisions=400, seed=0):
    """The behaviour of `policy` (slot 1) while fighting `opponent` (slot 0) one on one in the simulation."""
    from pvp_sim2 import Batch2
    from pvp_sim2.env import Scenario
    b = Batch2(n, k=2, seed=seed, spec=SPEC3, breadth=1.0, scenario=Scenario(three=0.0, four=0.0))
    obs = b.reset()
    f = b.w.f
    sprint, change, jump, click, dist, charge, count = [], [], [], [], [], [], 0
    last = np.full(n, np.nan)
    prev_act, heads_changed, heads_total = None, np.zeros(len(SPEC3.action_nvec)), 0
    for _ in range(decisions):
        st = b.state(1)
        act1 = policy(obs[:, 1])
        act0 = opponent(obs[:, 0])
        v = decode(act1, SPEC3)
        fight = fighting(st) & f.alive[:, 1]
        count += int(fight.sum())
        sprint += list(st["self_sprinting"][fight].astype(float))
        prev = last[fight]
        change += list((v["strafe"][fight] != prev)[~np.isnan(prev)].astype(float))
        last = np.where(fight, v["strafe"], np.nan)
        jump += list((v["jump"][fight] > 0.5).astype(float))
        click += list((v["attack"][fight] > 0.5).astype(float))
        dist += list(np.hypot(st["opp_x"] - st["self_x"], st["opp_z"] - st["self_z"])[fight])
        if prev_act is not None:
            both = fight & was_fighting
            heads_changed += (act1[both] != prev_act[both]).sum(axis=0)
            heads_total += int(both.sum())
        prev_act, was_fighting = act1, fight
        c = f.charge()[:, 1].copy()
        hits = f.hits[:, 1].copy()
        obs, _, _, _ = b.step(np.stack([act0, act1], axis=1))
        landed = (f.hits[:, 1] > hits) & fight
        charge += list(c[landed])
    out = _stats(count, sprint, change, jump, click, dist, charge, SPEC3.decision_ticks)
    out["change_rates"] = dict(zip(SPEC3.action_names, map(float, heads_changed / max(1, heads_total))))
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("paths", nargs="+")
    ap.add_argument("--init", required=True, help="the master's checkpoint (the policy starts as it and stays near it)")
    ap.add_argument("--out", default="runs/people")
    ap.add_argument("--epochs", type=int, default=12)
    ap.add_argument("--kl", type=float, default=0.5)
    ap.add_argument("--lr", type=float, default=2e-4)
    a = ap.parse_args()
    import torch
    from train.policies import Chooser, load
    torch.set_num_threads(4)
    fights = [f for p in a.paths for f in F.read(p)]
    fit_obs, fit_act = arrays(fights, "fit")
    test_obs, test_act = arrays(fights, "test")
    print("people fighting: %d decisions to learn from, %d to test on" % (len(fit_obs), len(test_obs)), flush=True)
    master = load(a.init)
    model = load(a.init)
    before = heads_accuracy(master.policy, test_obs, test_act)
    history = clone(model, fit_obs, fit_act, a.epochs, lr=a.lr, kl=a.kl)
    after = heads_accuracy(model.policy, test_obs, test_act)
    os.makedirs(a.out, exist_ok=True)
    model.save(os.path.join(a.out, "ckpt_people.zip"))
    # stickiness per head: a sample every decision changes a head at rate q, people at rate c; keeping the last choice
    # with probability 1 - c / q brings it to c (fitted on the fit part's people)
    fit = [f for f in fights if part_of(f.name) == "fit"]
    zero = np.zeros(len(SPEC3.action_nvec))
    free = sim_behaviour(People(model.policy, zero, seed=1), Chooser(master.policy), seed=3)
    want = change_rates_logs(fit)
    q = np.array([free["change_rates"][n] for n in SPEC3.action_names])
    stick = np.clip(1 - want / np.maximum(q, 1e-6), 0.0, 0.95)
    # then how often it sprints, jumps and clicks: shift the logit of that choice by the log of people's rate over its own;
    # and how its clicks come: people click in bursts (a lower charge when a click lands), so the click's stickiness
    # follows the charge at a hit; the other heads' stickiness is fitted again to the shifted choices
    target = log_behaviour(fit)
    offsets = {h: 0.0 for h in CALIBRATE}
    attack = SPEC3.action_index("attack")
    for _ in range(10):
        sim = sim_behaviour(People(model.policy, stick, offsets, seed=1), Chooser(master.policy), seed=3)
        for h, key in CALIBRATE.items():
            offsets[h] = float(np.clip(offsets[h] + np.log(max(target[key], 1e-3) / max(sim[key], 1e-3)), -4, 4))
        if sim["charge_at_hit"] is not None and target["charge_at_hit"] is not None:
            stick[attack] = float(np.clip(stick[attack] + 0.5 * (sim["charge_at_hit"] - target["charge_at_hit"]), 0.0, 0.95))
    sim = sim_behaviour(People(model.policy, stick, offsets, seed=1), Chooser(master.policy), seed=3)
    sim_master = sim_behaviour(Chooser(master.policy, deterministic=False, seed=2), Chooser(master.policy), seed=3)
    logs = log_behaviour(fights)
    report = {"init": a.init, "kl": a.kl, "epochs": a.epochs, "fit_decisions": int(len(fit_obs)), "test_decisions": int(len(test_obs)),
              "stick": dict(zip(SPEC3.action_names, map(float, stick))), "offsets": offsets,
              "change_rates": {"people_in_logs": dict(zip(SPEC3.action_names, map(float, want))), "sampled_every_decision": free["change_rates"]},
              "accuracy_test": {"master": dict(zip(SPEC3.action_names, map(float, before))),
                                "people": dict(zip(SPEC3.action_names, map(float, after)))},
              "behaviour": {"people_in_logs": logs, "people_policy_in_sim": sim, "people_unsticky_in_sim": free, "master_in_sim": sim_master},
              "history": history}
    with open(os.path.join(a.out, "people.json"), "w") as fh:
        json.dump(report, fh, indent=1)
    print(json.dumps(report["accuracy_test"]))
    print(json.dumps(report["stick"]), json.dumps(offsets))
    for k, v in report["behaviour"].items():
        print(k, json.dumps({x: (round(y, 3) if isinstance(y, float) else y) for x, y in v.items() if x != "change_rates"}))


if __name__ == "__main__":
    main()
