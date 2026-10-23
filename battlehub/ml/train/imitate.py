"""Learning from recorded fights (behavior cloning): the policy of a checkpoint learns to choose what the people in the
mod's fight logs did in the same situations (train/fights.py), and is saved as a checkpoint that self-play continues
from (selfplay2.py --init). Copying gets the model the people's habits, items included (eating, the bow, pearls), which
self-play alone did not find; self-play afterwards keeps what works and sharpens it.

    python train/imitate.py exports/ --init runs/v3c/ckpt_016000000.zip --out runs/v3bc [--epochs 8] [--winners-only]
    python train/selfplay2.py --run runs/v3bc_sp --init runs/v3bc/ckpt_bc.zip --pool-from runs/v3c --masks --steps 16000000

The observations are spec v3's (with the history from the fighter's earlier decisions); a v2 checkpoint learns from
their first 118 values.

Writes <out>/ckpt_bc.zip and <out>/imitate.json (per epoch: loss, and on a held-out tenth how often each action head
chose what the person did).
"""
import argparse
import json
import os
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from pvp_sim2.spec import SPEC2  # noqa: E402
from train import fights as F  # noqa: E402


def heads_accuracy(policy, obs, act):
    """How often the policy's most likely choice is what was done, per action head."""
    import torch
    with torch.no_grad():
        dist = policy.get_distribution(torch.as_tensor(obs))
        best = np.stack([d.probs.argmax(dim=1).numpy() for d in dist.distribution], axis=1)
    return (best == act).mean(axis=0)


def clone(model, obs, act, epochs=8, batch=1024, lr=3e-4, ent=0.001, seed=0, log=print):
    """Behavior cloning on (obs, act); returns per epoch {loss, accuracy per head on the held-out tenth}."""
    import torch
    rng = np.random.default_rng(seed)
    n = len(obs)
    order = rng.permutation(n)
    hold = order[: max(1, n // 10)]
    train = order[max(1, n // 10):] if n > 1 else order
    policy = model.policy
    opt = torch.optim.Adam(policy.parameters(), lr=lr)
    history = []
    for epoch in range(epochs):
        policy.set_training_mode(True)
        rng.shuffle(train)
        losses = []
        for i in range(0, len(train), batch):
            idx = train[i:i + batch]
            dist = policy.get_distribution(torch.as_tensor(obs[idx]))
            logp = dist.log_prob(torch.as_tensor(act[idx]))
            loss = -logp.mean() - ent * dist.entropy().mean()
            opt.zero_grad()
            loss.backward()
            torch.nn.utils.clip_grad_norm_(policy.parameters(), 0.5)
            opt.step()
            losses.append(float(loss))
        policy.set_training_mode(False)
        acc = heads_accuracy(policy, obs[hold], act[hold])
        row = {"epoch": epoch + 1, "loss": round(float(np.mean(losses)), 4),
               "held_out_accuracy": {name: round(float(a), 3) for name, a in zip(SPEC2.action_names, acc)}}
        history.append(row)
        log(json.dumps(row))
    return history


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("paths", nargs="+", help="fight logs: files, zips (/battlehub fights export) or folders")
    ap.add_argument("--init", required=True, help="the checkpoint to start from (its policy learns)")
    ap.add_argument("--out", default="runs/v2bc")
    ap.add_argument("--who", default="human", choices=["human", "bot", "all"])
    ap.add_argument("--winners-only", action="store_true", help="only the fighters who won their fight")
    ap.add_argument("--epochs", type=int, default=8)
    ap.add_argument("--batch", type=int, default=1024)
    ap.add_argument("--lr", type=float, default=3e-4)
    a = ap.parse_args()
    import torch
    from train.policies import load
    torch.set_num_threads(2)
    fights = [f for p in a.paths for f in F.read(p)]
    obs, act, info = F.arrays(fights, who=a.who, winners_only=a.winners_only)
    if len(obs) < 100:
        sys.exit("only %d decisions of %s in %d fights: record more first" % (len(obs), a.who, len(fights)))
    print("%d fights, %d decisions (%.0f minutes) of %s" % (len(fights), len(obs), len(obs) / 600.0, a.who), flush=True)
    model = load(a.init)
    obs = obs[:, :model.observation_space.shape[0]]
    history = clone(model, obs, act, a.epochs, a.batch, a.lr)
    os.makedirs(a.out, exist_ok=True)
    model.save(os.path.join(a.out, "ckpt_bc.zip"))
    with open(os.path.join(a.out, "imitate.json"), "w") as f:
        json.dump({"init": a.init, "fights": len(fights), "decisions": int(len(obs)), "who": a.who, "winners_only": a.winners_only,
                   "modes": sorted(set(info["mode"])), "history": history}, f, indent=1)
    print("saved", os.path.join(a.out, "ckpt_bc.zip"))


if __name__ == "__main__":
    main()
