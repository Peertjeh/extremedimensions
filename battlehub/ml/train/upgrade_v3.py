"""A v2 checkpoint as a v3 checkpoint that plays exactly the same (phase 3C): spec v3 keeps v2's 118 observations in
their place and adds 51 after them, so the first layer of the policy and of the value network gets 51 inputs with
weight 0. The model then chooses as before, whatever the new values say, and training from it (selfplay2.py --init)
learns to use them. The lower levels' v2 models become v3 models this way, and v2 can leave the mod.

    python train/upgrade_v3.py runs/v3b/ckpt_004000032.zip runs/v3/start.zip
"""
import argparse
import os
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from pvp_sim2.spec import SPEC2, SPEC3  # noqa: E402

FIRST = ("mlp_extractor.policy_net.0.weight", "mlp_extractor.value_net.0.weight")


def upgrade(model, spec_from=SPEC2, spec_to=SPEC3):
    """The model (PPO or MaskablePPO) with a policy for spec_to's observation, in place; returns it."""
    import torch
    from gymnasium import spaces
    old = model.observation_space.shape[0]
    if old != spec_from.obs_dim:
        raise ValueError("the model takes %d observations, spec %s has %d" % (old, spec_from.name, spec_from.obs_dim))
    if spec_to.obs_names[:old] != spec_from.obs_names:
        raise ValueError("%s does not keep %s's observations in their place" % (spec_to.name, spec_from.name))
    space = spaces.Box(-1.0, 1.0, shape=(spec_to.obs_dim,), dtype=np.float32)
    state = model.policy.state_dict()
    for key in FIRST:
        w = state[key]
        state[key] = torch.cat([w, torch.zeros(w.shape[0], spec_to.obs_dim - old, dtype=w.dtype)], dim=1)
    policy = model.policy.__class__(space, model.action_space, model.lr_schedule, **model.policy_kwargs)
    policy.load_state_dict(state)
    model.policy = policy.to("cpu")
    model.observation_space = space
    return model


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("checkpoint")
    ap.add_argument("out")
    a = ap.parse_args()
    from train.policies import load
    model = upgrade(load(a.checkpoint))
    os.makedirs(os.path.dirname(os.path.abspath(a.out)), exist_ok=True)
    model.save(a.out)
    print("wrote", a.out, "for", SPEC3.name, SPEC3.obs_dim)


if __name__ == "__main__":
    main()
