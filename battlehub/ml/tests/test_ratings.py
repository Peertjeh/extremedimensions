"""The league's Elo ratings."""
import pytest

from train.ratings import elo


def test_ratings_follow_the_results_and_the_anchor():
    names = ["scripted_0.5", "a", "b"]
    # a beats the anchor 3 out of 4 (+191 Elo), b loses 3 out of 4 to it (-191)
    r = elo(names, {("a", "scripted_0.5"): (75, 25, 0), ("b", "scripted_0.5"): (25, 75, 0)})
    assert r["scripted_0.5"] == pytest.approx(1000.0)
    assert r["a"] == pytest.approx(1191, abs=5)
    assert r["b"] == pytest.approx(809, abs=5)
    # draws count half
    r = elo(names, {("a", "scripted_0.5"): (0, 0, 10), ("b", "a"): (5, 5, 0)})
    assert r["a"] == pytest.approx(1000, abs=1) and r["b"] == pytest.approx(1000, abs=1)
