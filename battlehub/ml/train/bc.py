"""Learning to use items by copying the teacher (behavior cloning on tools/make_bc_data.py's data), starting from a
trained model, so that the fight it learned stays:

  step 1  only the hand and use heads learn (the rest of the network is frozen): what to hold and when to press;
  step 2  everything learns: the teacher's hand, use and (while it aims a bow or a throw) pitch, and a KL term keeps
          the other heads (moving, turning, the click) near the model it started from, which fights better than the
          teacher.

Only where the teacher takes an item is it copied; where it keeps its weapon, hand and use stay near the model it
started from (which blocks with the shield and throws wind charges more, and as usefully, as the teacher).

The loss is taken over the allowed choices only (the action masks), and the hand head weighs rare items up (one over
the square root of how often each is chosen), so that pearls and food are not drowned by "keep the weapon".
The functions are also used by selfplay2.py --bc-data: a copying term next to PPO that fades out.

    python train/bc.py --data bc.npz --init runs/v2/ckpt_040000032.zip --out runs/v2bc

Writes <out>/ckpt_bc.zip and <out>/bc.json (per epoch: losses, per item how often the model chooses what the teacher
chose on a held-out tenth, and the KL of the other heads to the model it started from).
"""
import argparse
import json
import os
import sys
import time

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from pvp_sim2.masks import MASKS2  # noqa: E402
from pvp_sim2.spec import SPEC2  # noqa: E402
from train.policies import logits  # noqa: E402

HAND, USE, PITCH = SPEC2.action_index("hand"), SPEC2.action_index("use"), SPEC2.action_index("pitch")
SIZES = [int(k) for k in SPEC2.action_nvec]
OTHER = [h for h in range(len(SIZES)) if h not in (HAND, USE)]


class BCData:
    """The copying data as tensors, with a held-out tenth."""

    def __init__(self, path, seed=0, hold=0.1):
        import torch
        d = np.load(path)
        n = len(d["act"])
        self.obs = torch.as_tensor(d["obs"].astype(np.float32))
        self.act = torch.as_tensor(d["act"].astype(np.int64))
        self.mask = torch.as_tensor(np.unpackbits(d["mask"], axis=1, count=int(d["mask_size"])).astype(bool))
        self.aim = torch.as_tensor(d["aim"].astype(bool))
        rng = np.random.default_rng(seed)
        order = rng.permutation(n)
        k = max(1, int(n * hold))
        self.hold, self.train = order[:k], order[k:]
        self.rng = rng
        freq = np.bincount(d["act"][:, HAND], minlength=SIZES[HAND]).astype(np.float64)
        w = 1.0 / np.sqrt(np.maximum(freq, 1.0) / n)
        w = np.minimum(w / w[0], 20.0)
        self.hand_weight = torch.as_tensor(w, dtype=torch.float32)

    def batch(self, size, idx=None):
        idx = self.rng.choice(self.train, size=size) if idx is None else idx
        return {"obs": self.obs[idx], "act": self.act[idx], "mask": self.mask[idx], "aim": self.aim[idx]}


def heads(z):
    import torch
    return torch.split(z, SIZES, dim=1)


def _kl(zr, z):
    """KL(ref || policy) per row of two logit tensors of one head (minus infinity where not allowed)."""
    import torch
    import torch.nn.functional as F
    lr, lp = F.log_softmax(zr, dim=1), F.log_softmax(z, dim=1)
    pr = lr.exp()
    return torch.where(pr > 0, pr * (lr - lp), torch.zeros_like(pr)).sum(dim=1)


def readout_logits(policy, obs):
    """The logits with the network below the action layer held still (no gradient into it)."""
    import torch
    with torch.no_grad():
        features = policy.extract_features(obs)
        if isinstance(features, tuple):
            features = features[0]
        latent = policy.mlp_extractor.forward_actor(features)
    return policy.action_net(latent)


def bc_loss(policy, b, hand_weight=None, pitch=True, ref=None, readout=False):
    """Where the teacher takes an item: cross-entropy of its hand and use (over the allowed choices), and of its pitch
    while it aims. Where it keeps the weapon, with `ref`: KL of hand and use to the reference instead (the model it
    started from blocks with the shield and throws wind charges better than the teacher). readout=True moves only the
    action layer (next to PPO: the copying must not pull the fight the network learned)."""
    import torch
    import torch.nn.functional as F
    z = (readout_logits if readout else logits)(policy, b["obs"])
    z = heads(z.masked_fill(~b["mask"], -float("inf")))
    item = b["act"][:, HAND] != 0
    loss = torch.zeros(())
    if item.any():
        loss = loss + F.cross_entropy(z[HAND][item], b["act"][item, HAND], weight=hand_weight, reduction="sum") / len(item)
        loss = loss + F.cross_entropy(z[USE][item], b["act"][item, USE], reduction="sum") / len(item)
    if pitch and b["aim"].any():
        loss = loss + F.cross_entropy(z[PITCH][b["aim"]], b["act"][b["aim"], PITCH])
    if ref is not None and (~item).any():
        with torch.no_grad():
            zr = heads(logits(ref, b["obs"]).masked_fill(~b["mask"], -float("inf")))
        for h in (HAND, USE):
            loss = loss + _kl(zr[h][~item], z[h][~item]).sum() / len(item)
    return loss


def kl_hand_use(policy, ref, b, readout=True):
    """KL(ref || policy) of the hand and use heads (over the allowed choices), mean over the rows."""
    import torch
    with torch.no_grad():
        zr = heads(logits(ref, b["obs"]).masked_fill(~b["mask"], -float("inf")))
    z = heads((readout_logits if readout else logits)(policy, b["obs"]).masked_fill(~b["mask"], -float("inf")))
    return _kl(zr[HAND], z[HAND]).mean() + _kl(zr[USE], z[USE]).mean()


def aim_loss(policy, ref, b):
    """The teacher's pitch while it aims a bow or a throw, and the reference's pitch everywhere else (KL), through the
    action layer only: aiming learned without moving the melee aim."""
    import torch
    import torch.nn.functional as F
    z = heads(readout_logits(policy, b["obs"]))
    with torch.no_grad():
        zr = heads(logits(ref, b["obs"]))
    loss = torch.zeros(())
    if b["aim"].any():
        loss = loss + F.cross_entropy(z[PITCH][b["aim"]], b["act"][b["aim"], PITCH])
    if (~b["aim"]).any():
        loss = loss + _kl(zr[PITCH][~b["aim"]], z[PITCH][~b["aim"]]).mean()
    return loss


def kl_other(policy, ref, b):
    """KL(ref || policy) summed over the heads other than hand and use (pitch only where the teacher does not aim)."""
    import torch
    with torch.no_grad():
        zr = heads(logits(ref, b["obs"]))
    z = heads(logits(policy, b["obs"]))
    total = torch.zeros(())
    for h in OTHER:
        kl = _kl(zr[h], z[h])
        if h == PITCH:
            kl = kl[~b["aim"]]
        if kl.numel():
            total = total + kl.mean()
    return total


def report(policy, ref, data, n=20000):
    """On the held-out tenth: per hand the share of the teacher's choices the model makes (its best allowed choice),
    the same for use and for pitch while aiming, and the KL of the other heads to the reference."""
    import torch
    idx = data.hold[:n]
    b = data.batch(len(idx), idx)
    with torch.no_grad():
        z = heads(logits(policy, b["obs"]).masked_fill(~b["mask"], -float("inf")))
        best = [p.argmax(dim=1) for p in z]
        out = {"hand": {}, "use": round(float((best[USE] == b["act"][:, USE]).float().mean()), 3)}
        for k, name in enumerate(SPEC2.hands):
            rows = b["act"][:, HAND] == k
            if rows.any():
                out["hand"][name] = round(float((best[HAND][rows] == k).float().mean()), 3)
        if b["aim"].any():
            out["pitch_aiming"] = round(float((best[PITCH][b["aim"]] == b["act"][b["aim"], PITCH]).float().mean()), 3)
        zr = heads(logits(ref, b["obs"]))
        zp = heads(logits(policy, b["obs"]))
        out["kl_other"] = {SPEC2.action_names[h]: round(float((torch.softmax(zr[h], 1) * (torch.log_softmax(zr[h], 1) - torch.log_softmax(zp[h], 1))).sum(1).mean()), 4)
                           for h in OTHER}
    return out


def clone(model, ref_policy, data, epochs1=2, epochs2=3, batch=4096, lr1=1e-3, lr2=3e-4, kl=1.0, pitch=True, log=print):
    """Steps 1 and 2 on model.policy (in place); returns the history."""
    import torch
    policy = model.policy
    history = []
    steps_per_epoch = max(1, len(data.train) // batch)
    # step 1: only the rows of the action layer that belong to hand and use
    for p in policy.parameters():
        p.requires_grad_(False)
    net = policy.action_net
    net.weight.requires_grad_(True)
    net.bias.requires_grad_(True)
    rows = torch.zeros(sum(SIZES), dtype=torch.bool)
    off = np.concatenate([[0], np.cumsum(SIZES)])
    rows[off[HAND]:off[USE + 1]] = True
    hooks = [net.weight.register_hook(lambda g: g * rows[:, None]), net.bias.register_hook(lambda g: g * rows)]
    opt = torch.optim.Adam([net.weight, net.bias], lr=lr1)
    t0 = time.time()
    for epoch in range(epochs1):
        policy.set_training_mode(True)
        losses = []
        for _ in range(steps_per_epoch):
            loss = bc_loss(policy, data.batch(batch), data.hand_weight, pitch=False, ref=ref_policy)
            opt.zero_grad()
            loss.backward()
            opt.step()
            losses.append(float(loss.detach()))
        policy.set_training_mode(False)
        row = {"step": 1, "epoch": epoch + 1, "bc": round(float(np.mean(losses)), 4), "minutes": round((time.time() - t0) / 60, 1),
               **report(policy, ref_policy, data)}
        history.append(row)
        log(json.dumps(row))
    for h in hooks:
        h.remove()
    # step 2: the whole actor, with the KL term
    for p in policy.parameters():
        p.requires_grad_(True)
    actor = [p for name, p in policy.named_parameters() if not name.startswith(("value_net", "mlp_extractor.value_net", "vf_features_extractor"))]
    opt = torch.optim.Adam(actor, lr=lr2)
    for epoch in range(epochs2):
        policy.set_training_mode(True)
        losses, kls = [], []
        for _ in range(steps_per_epoch):
            b = data.batch(batch)
            l_bc = bc_loss(policy, b, data.hand_weight, pitch=pitch, ref=ref_policy)
            l_kl = kl_other(policy, ref_policy, b)
            loss = l_bc + kl * l_kl
            opt.zero_grad()
            loss.backward()
            torch.nn.utils.clip_grad_norm_(actor, 0.5)
            opt.step()
            losses.append(float(l_bc.detach()))
            kls.append(float(l_kl.detach()))
        policy.set_training_mode(False)
        row = {"step": 2, "epoch": epoch + 1, "bc": round(float(np.mean(losses)), 4), "kl": round(float(np.mean(kls)), 4),
               "minutes": round((time.time() - t0) / 60, 1), **report(policy, ref_policy, data)}
        history.append(row)
        log(json.dumps(row))
    return history


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--data", required=True)
    ap.add_argument("--init", required=True, help="the checkpoint to start from (and to stay near)")
    ap.add_argument("--out", default="runs/v2bc")
    ap.add_argument("--epochs1", type=int, default=2)
    ap.add_argument("--epochs2", type=int, default=3)
    ap.add_argument("--batch", type=int, default=4096)
    ap.add_argument("--kl", type=float, default=1.0)
    ap.add_argument("--threads", type=int, default=4)
    ap.add_argument("--no-pitch", action="store_true", help="do not copy the teacher's pitch while it aims")
    a = ap.parse_args()
    import torch
    from train.policies import load
    torch.set_num_threads(a.threads)
    torch.manual_seed(0)
    data = BCData(a.data)
    print("%d decisions (%d held out); hand weights %s" % (len(data.train) + len(data.hold), len(data.hold),
                                                          [round(float(w), 2) for w in data.hand_weight]), flush=True)
    model = load(a.init)
    ref = load(a.init).policy
    ref.set_training_mode(False)
    before = report(model.policy, ref, data)
    print(json.dumps({"before": before}), flush=True)
    history = clone(model, ref, data, a.epochs1, a.epochs2, a.batch, kl=a.kl, pitch=not a.no_pitch)
    os.makedirs(a.out, exist_ok=True)
    model.save(os.path.join(a.out, "ckpt_bc.zip"))
    with open(os.path.join(a.out, "bc.json"), "w") as f:
        json.dump({"init": a.init, "data": a.data, "kl": a.kl, "pitch": not a.no_pitch, "epochs": [a.epochs1, a.epochs2],
                   "before": before, "history": history}, f, indent=1)
    print("saved", os.path.join(a.out, "ckpt_bc.zip"))


if __name__ == "__main__":
    main()
