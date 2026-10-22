"""The simulation against the game's own numbers (Minecraft 1.21 / 26.x)."""
import numpy as np
import pytest

from pvp_sim.physics import BOXING, REST_VY, SWORD, Physics, Rules, armor_reduce

ZERO = np.zeros((1, 2))
NO = np.zeros((1, 2), dtype=bool)


def lone(rules=SWORD, half=1000.0):
    """One arena, fighter 0 at the origin looking along +z, fighter 1 far away."""
    ph = Physics(1, rules, rng=np.random.default_rng(0))
    ph.half[:] = half
    ph.place(np.array([0]), 0.0, 0.0, 500.0, 500.0, 0.0, 0.0)
    return ph


def run(ph, ticks, forward=0.0, strafe=0.0, jump=False, sprint=False, attack=False, yaw=0.0, pitch=0.0):
    f = np.array([[forward, 0.0]])
    s = np.array([[strafe, 0.0]])
    j = np.array([[jump, False]])
    sp = np.array([[sprint, False]])
    out = []
    for t in range(ticks):
        a = np.array([[attack and t == 0, False]])
        x0, z0 = ph.f.x[0, 0], ph.f.z[0, 0]
        ph.tick(f, s, j, sp, a, np.array([[yaw if t == 0 else 0.0, 0.0]]), np.array([[pitch if t == 0 else 0.0, 0.0]]))
        out.append((ph.f.x[0, 0] - x0, ph.f.y[0, 0], ph.f.z[0, 0] - z0))
    return np.array(out)


def test_walking_and_sprinting_reach_the_games_speeds():
    walk = run(lone(), 80, forward=1.0)
    assert walk[-1, 2] == pytest.approx(0.2158, abs=0.001)  # 4.317 m/s
    sprint = run(lone(), 80, forward=1.0, sprint=True)
    assert sprint[-1, 2] == pytest.approx(0.2806, abs=0.001)  # 5.612 m/s
    # diagonal input is no faster
    diag = run(lone(), 80, forward=1.0, strafe=1.0)
    assert np.hypot(diag[-1, 0], diag[-1, 2]) == pytest.approx(0.2202, abs=0.002)
    # Speed II (boxing kit): 40 percent faster
    fast = run(lone(BOXING), 80, forward=1.0)
    assert fast[-1, 2] == pytest.approx(0.2158 * 1.4, abs=0.002)


def test_a_jump_goes_1_25_high_and_takes_12_ticks():
    ph = lone()
    ys = run(ph, 20, jump=True)[:, 1]
    assert ys.max() == pytest.approx(1.2522, abs=0.001)
    ph = lone()
    run(ph, 1, jump=True)
    air = 1
    while not ph.f.on_ground[0, 0]:
        run(ph, 1)
        air += 1
    assert air == 12
    assert ph.f.vy[0, 0] == pytest.approx(REST_VY)


def test_sprint_jumping_is_about_7_1_metres_a_second():
    ph = lone()
    run(ph, 30, forward=1.0, sprint=True)
    z0 = ph.f.z[0, 0]
    run(ph, 120, forward=1.0, sprint=True, jump=True)
    speed = (ph.f.z[0, 0] - z0) / 120 * 20
    assert 6.8 < speed < 7.4, speed


def test_the_attack_charge_and_damage_follow_the_game():
    r = SWORD
    assert r.charge_ticks == pytest.approx(12.5)
    # full charge against full iron: 7 -> 3.78
    assert armor_reduce(7.0, 15.0, 0.0) == pytest.approx(3.78, abs=1e-9)
    assert armor_reduce(7.0, 0.0, 0.0) == 7.0
    # a hit 6 ticks after the last swing (the tick itself counts one up): charge 0.52, damage 7 * (0.2 + 0.8 * 0.2704)
    ph, hit = duel(gap=2.0, ticker=5)
    assert ph.dealt[0, 0] == pytest.approx(armor_reduce(7 * (0.2 + 0.8 * 0.52 ** 2), 15, 0), abs=1e-9)


def duel(gap=2.0, ticker=100, rules=SWORD, sprint=False, airborne=False):
    """Fighter 0 at the origin looking at fighter 1, `gap` blocks ahead; fighter 0 clicks once."""
    ph = Physics(1, rules, rng=np.random.default_rng(0))
    ph.half[:] = 100.0
    ph.place(np.array([0]), 0.0, 0.0, 0.0, gap, 0.0, 180.0)
    ph.f.ticker[0, 0] = ticker
    if airborne:
        ph.f.y[0, 0] = 0.1
        ph.f.vy[0, 0] = -0.2
        ph.f.on_ground[0, 0] = False
    fw = np.array([[1.0 if sprint else 0.0, 0.0]])
    ph.tick(fw, ZERO, NO, np.array([[sprint, False]]), np.array([[True, False]]), ZERO, ZERO)
    return ph, ph.landed[0, 0]


def test_a_hit_pushes_0_4_away_and_lifts_like_the_game():
    ph = Physics(1, SWORD, rng=np.random.default_rng(0))
    ph.half[:] = 100.0
    ph.place(np.array([0]), 0.0, 0.0, 0.0, 2.0, 0.0, 180.0)
    vy_before = ph.f.vy[0, 1]
    ph._attacks(np.array([[True, False]]))
    # standing on the ground the vertical speed is -0.0784: min(0.4, -0.0392 + 0.4)
    assert ph.f.vz[0, 1] == pytest.approx(0.4)
    assert ph.f.vy[0, 1] == pytest.approx(min(0.4, vy_before / 2 + 0.4))
    assert ph.f.invul[0, 1] == 20


def test_after_a_full_hit_ten_ticks_of_protection():
    ph, hit = duel(gap=2.0)
    assert hit
    first = ph.f.health[0, 1]
    v = ph.f.vz[0, 1]
    # 4 ticks later a weaker hit does nothing, not even a push
    ph.f.ticker[0, 0] = 100
    ph.f.invul[0, 1] = 16
    ph.f.vz[0, 1] = 0.0
    ph.f.z[0, 1] = 2.0
    ph._attacks(np.array([[True, False]]))
    assert ph.f.health[0, 1] == pytest.approx(first)
    assert ph.f.vz[0, 1] == 0.0
    assert ph.landed[0, 0]


def test_critical_and_sprint_hits():
    ph, _ = duel(gap=2.0, airborne=True)
    crit = ph.dealt[0, 0]
    ph2, _ = duel(gap=2.0)
    assert crit == pytest.approx(armor_reduce(10.5, 15, 0), abs=1e-9)
    assert ph2.dealt[0, 0] == pytest.approx(armor_reduce(7.0, 15, 0), abs=1e-9)
    # sprinting: no crit, an extra push of 0.5 along the look, the attacker slows and stops sprinting
    sp = Physics(1, SWORD, rng=np.random.default_rng(0))
    sp.half[:] = 100.0
    sp.place(np.array([0]), 0.0, 0.0, 0.0, 2.0, 0.0, 180.0)
    sp.f.sprinting[0, 0] = True
    sp.f.vz[0, 0] = 0.28
    sp._attacks(np.array([[True, False]]))
    assert sp.f.vz[0, 1] == pytest.approx(0.4 / 2 + 0.5)
    assert not sp.f.sprinting[0, 0]
    assert sp.f.vz[0, 0] == pytest.approx(0.28 * 0.6)


def test_a_swing_only_lands_on_the_hitbox_within_reach():
    assert duel(gap=3.1)[1]          # the box starts 0.3 before its centre: 2.8 < 3
    assert not duel(gap=3.4)[1]      # 3.1 > 3
    ph, hit = duel(gap=2.0)
    ph2 = Physics(1, SWORD, rng=np.random.default_rng(0))
    ph2.half[:] = 100.0
    ph2.place(np.array([0]), 0.0, 0.0, 1.5, 2.0, 0.0, 180.0)  # 37 degrees to the side: the look misses
    ph2.tick(ZERO, ZERO, NO, NO, np.array([[True, False]]), ZERO, ZERO)
    assert hit and not ph2.landed[0, 0] and ph2.whiffed[0, 0]
    # a miss also costs the charge
    assert ph2.f.ticker[0, 0] == 0


def test_walls_stop_you_and_a_platform_lets_you_fall():
    ph = lone(half=5.0)
    run(ph, 100, forward=1.0, sprint=True)
    assert ph.f.z[0, 0] == pytest.approx(5.0 - 0.3)
    sumo = lone()
    sumo.platform[:] = 4.0
    run(sumo, 60, forward=1.0)
    assert sumo.fell[0, 0]


def test_health_comes_back():
    ph = lone()
    ph.f.health[0, 0] = 10.0
    run(ph, 10)
    assert ph.f.health[0, 0] > 10.0
    box = lone(BOXING)
    box.f.health[0, 0] = 3.0
    run(box, 1)
    assert box.f.health[0, 0] == BOXING.max_health
