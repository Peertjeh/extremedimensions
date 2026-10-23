"""Learning items by copying (tools/make_bc_data.py, train/bc.py) and the items mark of an exported model."""
import os

import numpy as np
import pytest

torch = pytest.importorskip("torch")
pytest.importorskip("stable_baselines3")

from pvp_sim2.masks import MASKS2  # noqa: E402
from pvp_sim2.spec import SPEC2  # noqa: E402

HAND, USE = SPEC2.action_index("hand"), SPEC2.action_index("use")


@pytest.fixture(scope="module")
def data_path(tmp_path_factory):
    """A small data set with the teacher in every slot (no model needed)."""
    from tools.make_bc_data import chunk
    d = chunk((None, 3000, 5, 32))
    p = tmp_path_factory.mktemp("bc") / "bc.npz"
    np.savez_compressed(p, **d, mask_size=np.array(MASKS2.size))
    return str(p)


def _model():
    from stable_baselines3 import PPO
    from pvp_sim2 import PvpEnv2
    torch.manual_seed(0)
    return PPO("MlpPolicy", PvpEnv2(seed=0), n_steps=64, batch_size=64, device="cpu",
               policy_kwargs=dict(net_arch=dict(pi=[64, 64], vf=[64, 64])))


def test_the_data_holds_allowed_item_choices(data_path):
    d = np.load(data_path)
    mask = np.unpackbits(d["mask"], axis=1, count=MASKS2.size).astype(bool)
    act = d["act"].astype(np.int64)
    assert len(act) >= 3000
    # every label is allowed, items are in it, and the aiming rows hold a bow or a throwable
    for h in (HAND, USE):
        assert mask[np.arange(len(act)), MASKS2.offsets[h] + act[:, h]].all()
    assert (act[:, HAND] == 1).any() and (act[:, HAND] != 0).mean() > 0.05
    assert np.isin(act[d["aim"], HAND], (1, 4, 5)).all()


def test_copying_learns_the_teachers_items_and_keeps_the_rest(data_path):
    from train.bc import BCData, bc_loss, clone, kl_other, report
    data = BCData(data_path)
    model = _model()
    ref = _model().policy
    ref.load_state_dict(model.policy.state_dict())
    b = data.batch(256)
    assert float(kl_other(model.policy, ref, b)) < 1e-6
    assert np.isfinite(float(bc_loss(model.policy, b, data.hand_weight, ref=ref)))
    before = report(model.policy, ref, data)
    clone(model, ref, data, epochs1=3, epochs2=2, batch=512, kl=1.0, log=lambda row: None)
    after = report(model.policy, ref, data)
    assert after["hand"]["bow"] > max(0.6, before["hand"]["bow"])
    assert all(v < 0.2 for v in after["kl_other"].values())


def test_an_exported_model_carries_the_items_mark(tmp_path):
    onnx = pytest.importorskip("onnx")
    pytest.importorskip("onnxruntime")
    from train.export_onnx import export
    model = _model()
    ckpt = str(tmp_path / "ckpt_000000064.zip")
    model.save(ckpt)
    out = str(tmp_path / "m.onnx")
    export(ckpt, out, name="test", meta={"items": "true"}, spec=SPEC2)
    meta = {p.key: p.value for p in onnx.load(out).metadata_props}
    assert meta["items"] == "true" and meta["spec_sha256"] == SPEC2.sha256
    assert os.path.exists(out + ".check.json")
