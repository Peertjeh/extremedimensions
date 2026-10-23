"""The opponent pool of selfplay2.py: model v1 as an opponent (phase 3B) takes its share of the slots and plays them."""
import numpy as np

from pvp_sim2.spec import SPEC2
from train.selfplay2 import Curriculum, OpponentPool2


class FakeV1:
    """Stands in for the league's V1Adapter: always the same action, and counts what it was asked."""

    def __init__(self):
        self.rows = 0

    def __call__(self, obs):
        self.rows += len(obs)
        out = np.zeros((len(obs), len(SPEC2.action_nvec)), dtype=np.int64)
        out[:, SPEC2.action_index("attack")] = 1
        return out


def test_v1_plays_its_share_of_the_slots():
    cur = Curriculum(warmup=0, widen=1, fade_from=0, fade_to=1)
    cur.steps = 10
    v1 = FakeV1()
    pool = OpponentPool2(np.random.default_rng(0), cur, v1=v1, v1_share=0.25)
    kind, skill = pool.draw(4000)
    share = (kind == -4).mean()
    assert 0.22 < share < 0.28
    obs = np.zeros((4000, SPEC2.obs_dim), dtype=np.float32)
    acts = pool.act(obs, kind, skill)
    assert v1.rows == int((kind == -4).sum())
    assert (acts[kind == -4, SPEC2.action_index("attack")] == 1).all()


def test_without_v1_no_slot_is_its():
    cur = Curriculum()
    pool = OpponentPool2(np.random.default_rng(1), cur, v1=None, v1_share=0.5)
    kind, _ = pool.draw(1000)
    assert not (kind == -4).any()
