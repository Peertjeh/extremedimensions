"""Trained v2 and v3 policies as players: load a checkpoint (PPO, or MaskablePPO from sb3-contrib) and choose actions
from its logits with the action masks of ml/spec/combat_v2_masks.json (v3's are the same rules on the same first
values), as the mod does (core/ml/CombatMasks). A v2 policy plays in the v3 simulation on the first 118 values. Used
by the evaluation, the league, the opponent pool and behavior cloning, so every place that lets a model choose does it
the same way.

    from train.policies import load, Chooser
    model = load("runs/v2/ckpt_040000032.zip")
    act = Chooser(model.policy)            # obs (m, obs_dim) -> action indices (m, heads), masked, the best choice
"""
import json
import os
import sys
import zipfile

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from pvp_sim2.masks import MASKS2  # noqa: E402


def maskable(path):
    """Whether a checkpoint was saved by MaskablePPO."""
    with zipfile.ZipFile(path) as z:
        data = json.loads(z.read("data").decode("utf-8"))
    return "maskable" in str(data.get("policy_class", {}).get("__module__", ""))


def load(path, env=None, **kw):
    """The model of a checkpoint, with the class it was trained with."""
    if maskable(path):
        from sb3_contrib import MaskablePPO
        return MaskablePPO.load(path, env=env, device="cpu", **kw)
    from stable_baselines3 import PPO
    return PPO.load(path, env=env, device="cpu", **kw)


def logits(policy, obs):
    """The logits of every head (torch, (m, all choices)): what the ONNX export gives (train/export_onnx.Logits)."""
    features = policy.extract_features(obs)
    if isinstance(features, tuple):
        features = features[0]
    return policy.action_net(policy.mlp_extractor.forward_actor(features))


def masked_logits(policy, obs, mask):
    """The logits with every choice that is not allowed at minus infinity (mask: bool tensor of the same shape)."""
    import torch
    return logits(policy, obs).masked_fill(~mask, -torch.inf)


class Chooser:
    """obs (m, obs_dim) -> action indices (m, heads): per head the highest allowed logit, or a sample of the allowed
    choices. masks=False chooses as before the masks (something not carried can be chosen; it does nothing). hands: the
    hands the model may choose besides the weapon (as the mod does with a model's "items" list); None: all."""

    def __init__(self, policy, masks=True, deterministic=True, seed=0, hands=None):
        import torch
        self.torch = torch
        self.policy = policy
        # a v2 policy reads the first 118 values of a v3 observation (v3 keeps v2's in their place)
        self.dim = int(policy.observation_space.shape[0])
        self.masks = masks
        hand = MASKS2.spec.action_index("hand")
        at = int(MASKS2.offsets[hand])
        self.off = [at + k for k, name in enumerate(MASKS2.spec.hands) if k > 0 and hands is not None and name not in hands]
        self.deterministic = deterministic
        self.gen = torch.Generator().manual_seed(seed)
        policy.set_training_mode(False)

    def __call__(self, obs):
        torch = self.torch
        obs = np.asarray(obs, dtype=np.float32)[:, :self.dim]
        with torch.no_grad():
            t = torch.as_tensor(obs)
            z = logits(self.policy, t)
            if self.masks:
                m = MASKS2(obs[:, :MASKS2.spec.obs_dim])
                if self.off:
                    m[:, self.off] = False
                z = z.masked_fill(~torch.as_tensor(m), -torch.inf)
            out = []
            for part in torch.split(z, [int(k) for k in MASKS2.spec.action_nvec], dim=1):
                if self.deterministic:
                    out.append(part.argmax(dim=1))
                else:
                    out.append(torch.multinomial(torch.softmax(part, dim=1), 1, generator=self.gen)[:, 0])
            return torch.stack(out, dim=1).numpy()
