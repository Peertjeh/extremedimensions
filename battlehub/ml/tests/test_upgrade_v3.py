"""A v2 model upgraded to spec v3 (train/upgrade_v3.py) chooses exactly as before, whatever v3's new values are, and
is saved and loaded as a v3 model."""
import numpy as np
import pytest

from pvp_sim2.spec import SPEC2, SPEC3


def test_the_upgraded_model_gives_the_same_logits_and_choices_and_loads_again(tmp_path):
    torch = pytest.importorskip("torch")
    import gymnasium as gym
    from gymnasium import spaces
    from stable_baselines3 import PPO
    from stable_baselines3.common.env_util import DummyVecEnv
    from train.policies import Chooser, load, logits
    from train.upgrade_v3 import upgrade

    class Tiny(gym.Env):
        observation_space = spaces.Box(-1.0, 1.0, shape=(SPEC2.obs_dim,), dtype=np.float32)
        action_space = spaces.MultiDiscrete(SPEC2.action_nvec)

        def reset(self, seed=None, options=None):
            return np.zeros(SPEC2.obs_dim, dtype=np.float32), {}

        def step(self, a):
            return np.zeros(SPEC2.obs_dim, dtype=np.float32), 0.0, True, False, {}

    model = PPO("MlpPolicy", DummyVecEnv([Tiny]), policy_kwargs=dict(net_arch=dict(pi=[32, 32], vf=[32, 32])), seed=1, device="cpu")
    model.save(tmp_path / "v2.zip")
    v2 = load(str(tmp_path / "v2.zip"))
    rng = np.random.default_rng(2)
    obs3 = rng.uniform(-1, 1, (64, SPEC3.obs_dim)).astype(np.float32)
    with torch.no_grad():
        before = logits(v2.policy, torch.as_tensor(obs3[:, :SPEC2.obs_dim])).numpy()
        v_before = v2.policy.predict_values(torch.as_tensor(obs3[:, :SPEC2.obs_dim])).numpy()
    upgrade(model)
    assert model.observation_space.shape == (SPEC3.obs_dim,)
    model.save(tmp_path / "v3.zip")
    v3 = load(str(tmp_path / "v3.zip"))
    assert v3.observation_space.shape == (SPEC3.obs_dim,)
    with torch.no_grad():
        after = logits(v3.policy, torch.as_tensor(obs3)).numpy()
        v_after = v3.policy.predict_values(torch.as_tensor(obs3)).numpy()
    assert np.allclose(before, after, atol=1e-5) and np.allclose(v_before, v_after, atol=1e-5)
    # in the v3 simulation the v2 policy reads the first 118 values: the same choices as its upgrade
    assert (Chooser(v2.policy)(obs3) == Chooser(v3.policy)(obs3)).all()
    with pytest.raises(ValueError):
        upgrade(v3)
