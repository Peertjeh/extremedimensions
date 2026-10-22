"""Exports a trained checkpoint to ONNX for the mod, and checks that ONNX Runtime gives what PyTorch gives.

The ONNX model takes "obs" (batch, 43) float32 and gives "logits" (batch, 26): the action heads one after the other in
spec order (3 forward, 3 strafe, 2 jump, 2 sprint, 2 attack, 9 yaw, 5 pitch). The mod takes the highest logit of each
head (or samples them with a temperature). The spec's sha256, the training steps and the heads are stored in the
model's metadata, so the mod can refuse a model made for another spec.

    python train/export_onnx.py runs/sword/ckpt_030000000.zip out.onnx [--name expert]
"""
import argparse
import json
import os
import sys

import numpy as np
import onnx
import onnxruntime as ort
import torch
from stable_baselines3 import PPO

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from pvp_sim.spec import SPEC  # noqa: E402


class Logits(torch.nn.Module):
    """obs -> the logits of every action head (what the policy samples its actions from)."""

    def __init__(self, policy):
        super().__init__()
        self.policy = policy

    def forward(self, obs):
        features = self.policy.extract_features(obs)
        if isinstance(features, tuple):
            features = features[0]
        latent = self.policy.mlp_extractor.forward_actor(features)
        return self.policy.action_net(latent)


def export(checkpoint, out, name="", meta=None):
    model = PPO.load(checkpoint, device="cpu")
    net = Logits(model.policy).eval()
    dummy = torch.zeros((1, SPEC.obs_dim), dtype=torch.float32)
    try:
        torch.onnx.export(net, (dummy,), out, input_names=["obs"], output_names=["logits"],
                          dynamic_axes={"obs": {0: "batch"}, "logits": {0: "batch"}}, opset_version=17, dynamo=False)
    except TypeError:
        torch.onnx.export(net, (dummy,), out, input_names=["obs"], output_names=["logits"],
                          dynamic_axes={"obs": {0: "batch"}, "logits": {0: "batch"}}, opset_version=17)
    m = onnx.load(out)
    props = {"spec_name": SPEC.name, "spec_version": str(SPEC.version), "spec_sha256": SPEC.sha256,
             "obs_dim": str(SPEC.obs_dim), "action_nvec": json.dumps([int(v) for v in SPEC.action_nvec]),
             "steps": str(model.num_timesteps), "name": name}
    props.update(meta or {})
    del m.metadata_props[:]
    for k, v in props.items():
        e = m.metadata_props.add()
        e.key, e.value = k, str(v)
    onnx.checker.check_model(m)
    onnx.save(m, out)
    check(net, out)
    return out


def check(net, path, n=2000):
    """ONNX Runtime and PyTorch agree on the logits and on the chosen action of every head."""
    rng = np.random.default_rng(0)
    obs = rng.uniform(-1, 1, size=(n, SPEC.obs_dim)).astype(np.float32)
    with torch.no_grad():
        want = net(torch.as_tensor(obs)).numpy()
    sess = ort.InferenceSession(path, providers=["CPUExecutionProvider"])
    got = sess.run(["logits"], {"obs": obs})[0]
    diff = float(np.abs(want - got).max())
    if diff > 1e-4:
        raise SystemExit("ONNX differs from PyTorch by %g" % diff)
    o = 0
    for k in SPEC.action_nvec:
        if not (want[:, o:o + k].argmax(1) == got[:, o:o + k].argmax(1)).all():
            raise SystemExit("ONNX picks another action than PyTorch")
        o += k
    print("exported %s: logits %s, max difference to PyTorch %.2e" % (path, got.shape, diff))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("checkpoint")
    ap.add_argument("out")
    ap.add_argument("--name", default="")
    a = ap.parse_args()
    export(a.checkpoint, a.out, a.name)


if __name__ == "__main__":
    main()
