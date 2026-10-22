"""A fast 1v1 Minecraft melee simulation for training the BattleHub combat model (see ml/README.md)."""
from .spec import SPEC, Spec
from .physics import BOXING, SWORD, Physics, Rules
from .env import PvpBatch, PvpEnv, Randomize, Rewards
from .scripted import DummyPolicy, RandomPolicy, ScriptedPolicy
