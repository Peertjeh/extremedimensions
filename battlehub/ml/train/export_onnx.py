"""Exports a trained checkpoint to ONNX for the mod, and checks that ONNX Runtime gives what PyTorch gives.

The ONNX model takes "obs" (batch, obs_dim) float32 and gives "logits" (batch, all heads' choices): the action heads one
after the other in spec order (v1: 43 -> 26: 3 forward, 3 strafe, 2 jump, 2 sprint, 2 attack, 9 yaw, 5 pitch; v2:
118 -> 38, with 11 yaw, 7 pitch, 6 hand, 2 use). The mod takes the highest logit of each head (or samples them with a
temperature). The spec's sha256, the training steps and the heads are stored in the model's metadata, so the mod can
refuse a model made for another spec.

    python train/export_onnx.py runs/sword/ckpt_030000000.zip out.onnx [--name expert] [--spec 2]
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
from pvp_sim2.spec import SPEC2  # noqa: E402


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


def export(checkpoint, out, name="", meta=None, spec=SPEC):
    model = PPO.load(checkpoint, device="cpu")
    net = Logits(model.policy).eval()
    dummy = torch.zeros((1, spec.obs_dim), dtype=torch.float32)
    try:
        torch.onnx.export(net, (dummy,), out, input_names=["obs"], output_names=["logits"],
                          dynamic_axes={"obs": {0: "batch"}, "logits": {0: "batch"}}, opset_version=17, dynamo=False)
    except TypeError:
        torch.onnx.export(net, (dummy,), out, input_names=["obs"], output_names=["logits"],
                          dynamic_axes={"obs": {0: "batch"}, "logits": {0: "batch"}}, opset_version=17)
    m = onnx.load(out)
    props = {"spec_name": spec.name, "spec_version": str(spec.version), "spec_sha256": spec.sha256,
             "obs_dim": str(spec.obs_dim), "action_nvec": json.dumps([int(v) for v in spec.action_nvec]),
             "steps": str(model.num_timesteps), "name": name}
    props.update(meta or {})
    del m.metadata_props[:]
    for k, v in props.items():
        e = m.metadata_props.add()
        e.key, e.value = k, str(v)
    onnx.checker.check_model(m)
    onnx.save(m, out)
    check(net, out, spec)
    write_check(net, out, spec)
    return out


def write_check(net, path, spec=SPEC, n=8):
    """<model>.check.json: a few observations with the logits PyTorch gives, for the mod's test of its own runtime."""
    rng = np.random.default_rng(1)
    obs = rng.uniform(-1, 1, size=(n, spec.obs_dim)).astype(np.float32)
    with torch.no_grad():
        logits = net(torch.as_tensor(obs)).numpy()
    with open(path + ".check.json", "w") as f:
        json.dump({"spec_sha256": spec.sha256, "obs": obs.tolist(), "logits": logits.tolist(), "tolerance": 1e-4}, f)


def check(net, path, spec=SPEC, n=2000):
    """ONNX Runtime and PyTorch agree on the logits and on the chosen action of every head."""
    rng = np.random.default_rng(0)
    obs = rng.uniform(-1, 1, size=(n, spec.obs_dim)).astype(np.float32)
    with torch.no_grad():
        want = net(torch.as_tensor(obs)).numpy()
    sess = ort.InferenceSession(path, providers=["CPUExecutionProvider"])
    got = sess.run(["logits"], {"obs": obs})[0]
    diff = float(np.abs(want - got).max())
    if diff > 1e-4:
        raise SystemExit("ONNX differs from PyTorch by %g" % diff)
    o = 0
    for k in spec.action_nvec:
        if not (want[:, o:o + k].argmax(1) == got[:, o:o + k].argmax(1)).all():
            raise SystemExit("ONNX picks another action than PyTorch")
        o += k
    print("exported %s: logits %s, max difference to PyTorch %.2e" % (path, got.shape, diff))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("checkpoint")
    ap.add_argument("out")
    ap.add_argument("--name", default="")
    ap.add_argument("--spec", type=int, default=1, choices=[1, 2])
    a = ap.parse_args()
    export(a.checkpoint, a.out, a.name, spec=SPEC2 if a.spec == 2 else SPEC)


if __name__ == "__main__":
    main()
