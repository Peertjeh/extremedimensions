"""The v2 simulation against the game: terrain, falling, the void, kits, the bow, food, pearls, wind charges, the
shield, the mace, hunger and teams."""
import numpy as np
import pytest

from pvp_sim2 import kits as K
from pvp_sim2.physics import ARROW, World, bow_power, mace_bonus
from pvp_sim2.terrain import WALL

Z = np.zeros((1, 3))
N = np.zeros((1, 3), dtype=bool)
H = np.zeros((1, 3), dtype=np.int64)


def world(ground=0.0, kit=None, others=None):
    """One arena of flat ground (or a function filling the terrain), fighter 0 at the origin looking along +z,
    fighter 1 (if `others`) 10 blocks ahead, slot 2 empty."""
    w = World(1, 3, rng=np.random.default_rng(0))
    t = w.terrain
    if callable(ground):
        t.clear(0)
        ground(t)
    else:
        t.fill_box(0, -30, -30, 30, 30, ground)
    t.floor[0] = np.nanmin(t.ground[0])
    w.place(0, 0, 0.5, t.column(np.array([0]), np.array([0.5]), np.array([0.5]))[0], 0.5, 0.0, kit or K.sword(None), 0)
    if others is not None:
        for slot, (x, z, kit2, team) in enumerate(others, start=1):
            y = t.column(np.array([0]), np.array([x]), np.array([z]))[0]
            w.place(0, slot, x, y, z, 180.0, kit2, team)
    for slot in range(1 + len(others or []), 3):
        w.remove(0, slot)
    return w


def tick(w, n=1, forward=0.0, strafe=0.0, jump=False, sprint=False, attack=False, use=False, yaw=0.0, pitch=0.0, hand=0, who=0, hold=()):
    """n ticks with these inputs for fighter `who`; the others do nothing but keep the right button down if in `hold`."""
    for i in range(n):
        f, s, j, sp, a, u = Z.copy(), Z.copy(), N.copy(), N.copy(), N.copy(), N.copy()
        for k in hold:
            u[0, k] = True
        y, p, h = Z.copy(), Z.copy(), w.f.hand.copy()
        f[0, who], s[0, who], j[0, who], sp[0, who], a[0, who], u[0, who] = forward, strafe, jump, sprint, attack and i == 0, use
        y[0, who], p[0, who], h[0, who] = (yaw if i == 0 else 0.0), (pitch if i == 0 else 0.0), hand
        w.tick(f, s, j, sp, a, u, y, p, h)


def test_flat_ground_moves_like_v1():
    w = world()
    tick(w, 80, forward=1.0)
    assert w.f.vz[0, 0] / 0.546 == pytest.approx(0.2158, abs=0.001)
    assert w.f.on_ground[0, 0]
    assert w.f.y[0, 0] == pytest.approx(0.0)


def test_a_slab_is_stepped_onto_a_block_needs_a_jump_two_blocks_stop():
    def steps(t):
        t.fill_box(0, -30, -30, 30, 30, 0.0)
        t.fill_box(0, -30, 3, 30, 6, 0.5)    # slab at z 3..6
        t.fill_box(0, -30, 6, 30, 9, 1.5)    # a block on top of it at z 6..9
        t.fill_box(0, -30, 9, 30, 30, 3.5)   # two more blocks from z 9
    w = world(steps)
    tick(w, 40, forward=1.0)
    assert w.f.y[0, 0] == pytest.approx(0.5)
    assert w.f.z[0, 0] == pytest.approx(6 - 0.3, abs=0.01)  # against the block
    tick(w, 30, forward=1.0, jump=True)
    tick(w, 20, forward=1.0)
    assert w.f.y[0, 0] == pytest.approx(1.5)
    assert w.f.z[0, 0] == pytest.approx(9 - 0.3, abs=0.01)  # the two blocks are too high to jump onto
    assert w.f.health[0, 0] == 20.0


def test_falling_six_blocks_does_three_damage_and_jump_boost_saves_one():
    def ledge(t):
        t.fill_box(0, -30, -30, 30, 2, 6.0)
        t.fill_box(0, -30, 2, 30, 30, 0.0)
    w = world(ledge)
    w.natural_regen[0] = False
    tick(w, 40, forward=1.0)
    assert w.f.y[0, 0] == pytest.approx(0.0)
    assert w.f.health[0, 0] == pytest.approx(17.0)
    kit = K.sword(None)
    kit["jump_power"] = 0.52
    w = world(ledge, kit=kit)
    w.natural_regen[0] = False
    tick(w, 40, forward=1.0)
    assert w.f.health[0, 0] == pytest.approx(18.0)


def test_walking_off_a_platform_is_death_in_the_void_credited_to_who_hit_last():
    w = world(lambda t: t.fill_box(0, -2, -2, 2, 2, 0.0), others=[(0.5, 1.6, K.sword(None), 1)])
    w.f.yaw[0, 1] = 180.0
    # fighter 1, at 1.1 blocks, hits fighter 0 towards the edge behind it (fighter 1 looks along -z)
    w.f.x[0, 1] = 0.5
    w.f.z[0, 0] = -1.0
    w.f.z[0, 1] = 0.6
    w.f.pitch[0, 1] = 25.0
    tick(w, 1, attack=True, who=1)
    assert w.landed[0, 1]
    for _ in range(60):
        tick(w, 1, forward=-1.0)
        if w.died[0, 0]:
            break
    assert w.died[0, 0]
    assert w.killer[0, 0] == 1
    assert not w.f.alive[0, 0]


def test_a_full_draw_shoots_three_blocks_a_tick_and_hits_for_six_or_more():
    kit = K.bow(K.base(), 16, 0)
    target = K.base()
    w = world(kit=kit, others=[(0.5, 12.5, target, 1)])
    tick(w, 1, hand=1)
    tick(w, 25, use=True, hand=1, pitch=-1.0)
    assert w.f.using[0, 0]
    assert w.f.use_ticks[0, 0] == 25
    tick(w, 1, use=False, hand=1)
    assert w.fired[0, 0]
    assert w.f.arrows[0, 0] == 15
    p = w.p
    j = np.nonzero(p.active[0])[0][0]
    assert np.sqrt(p.vx[0, j] ** 2 + p.vy[0, j] ** 2 + p.vz[0, j] ** 2) == pytest.approx(3.0, abs=0.15)
    assert p.crit[0, j]
    for _ in range(10):
        tick(w, 1, hand=1)
    assert w.f.health[0, 1] <= 20 - 6
    assert w.f.health[0, 1] >= 20 - 6 - 5
    assert bow_power(20) == pytest.approx(1.0)
    assert bow_power(10) == pytest.approx((0.25 + 1.0) / 3)


def test_drawing_walks_at_a_fifth_and_stops_the_sprint():
    w = world(kit=K.bow(K.base(), 16, 0))
    tick(w, 1, hand=1)
    tick(w, 60, forward=1.0, sprint=True, use=True, hand=1)
    assert not w.f.sprinting[0, 0]
    assert w.f.vz[0, 0] / 0.546 == pytest.approx(0.2158 * 0.2, abs=0.002)


def test_a_golden_apple_gives_absorption_and_four_health_over_five_seconds():
    kit = K.sword(None)
    w = world(kit=kit)
    w.natural_regen[0] = False
    w.f.health[0, 0] = 10.0
    tick(w, 1, hand=3)
    tick(w, 31, use=True, hand=3)
    assert w.f.gapples[0, 0] == 3
    tick(w, 1, use=True, hand=3)
    assert w.f.gapples[0, 0] == 2
    assert w.f.absorption[0, 0] == pytest.approx(4.0)
    tick(w, 110, hand=3)
    assert w.f.health[0, 0] == pytest.approx(14.0)


def test_food_is_only_eaten_when_not_full():
    kit = K.lms(None)
    w = world(kit=kit)
    tick(w, 1, hand=2)
    tick(w, 40, use=True, hand=2)
    assert w.f.food_items[0, 0] == 8
    w.f.food[0, 0] = 10.0
    tick(w, 33, use=True, hand=2)
    assert w.f.food_items[0, 0] == 7
    assert w.f.food[0, 0] == pytest.approx(18.0)


def test_a_pearl_puts_the_thrower_where_it_lands_with_five_damage():
    kit = K.base()
    kit["pearls"] = 2
    w = world(kit=kit)
    w.natural_regen[0] = False
    tick(w, 1, hand=4)
    tick(w, 1, use=True, hand=4)
    assert w.f.pearls[0, 0] == 1
    assert w.f.pearl_cd[0, 0] == 20
    for _ in range(60):
        tick(w, 1, hand=4)
        if w.f.health[0, 0] < 20:
            break
    assert w.f.health[0, 0] == pytest.approx(15.0)
    assert 8.0 < w.f.z[0, 0] < 20.0


def test_a_wind_charge_at_the_feet_sends_one_up_without_fall_damage():
    kit = K.mace(None)
    w = world(kit=kit)
    tick(w, 1, hand=5, pitch=90.0)
    tick(w, 1, use=True, hand=5)
    top = 0.0
    for _ in range(80):
        tick(w, 1, hand=5)
        top = max(top, w.f.y[0, 0])
    assert 5.0 < top < 9.0
    assert w.f.on_ground[0, 0]
    assert w.f.health[0, 0] == 20.0


def test_the_mace_adds_damage_by_the_fall():
    assert mace_bonus(np.array(3.0)) == pytest.approx(12.0)
    assert mace_bonus(np.array(5.0)) == pytest.approx(16.0)
    assert mace_bonus(np.array(10.0)) == pytest.approx(24.0)
    target = K.base()
    w = world(kit=K.mace(None), others=[(0.5, 1.5, target, 1)])
    w.natural_regen[0] = False
    w.f.y[0, 0] = 3.5
    w.f.on_ground[0, 0] = False
    w.f.pitch[0, 0] = 75.0
    tick(w, 6)
    assert w.f.fall[0, 0] > 1.5
    fall = w.f.fall[0, 0]
    tick(w, 1, attack=True)
    assert w.landed[0, 0]
    # charge full (100 ticks since the last swing), a critical hit, plus the smash
    want = 6.0 * 1.5 + mace_bonus(np.array(fall))
    assert 20.0 - w.f.health[0, 1] == pytest.approx(min(20.0, want), abs=1.5)


def test_a_shield_blocks_from_the_front_and_an_axe_disables_it():
    defender = K.axe(None)
    w = world(kit=K.sword(None), others=[(0.5, 2.5, defender, 1)])
    # fighter 1 looks at fighter 0 and holds its shield up
    for _ in range(6):
        tick(w, 1, use=True, who=1)
    assert w.f.blocking()[0, 1]
    tick(w, 1, attack=True, hold=(1,))
    assert w.f.health[0, 1] == 20.0
    # the attacker was pushed off; back in front, now with an axe, which disables the shield
    assert w.f.vz[0, 0] < -0.2
    w.f.z[0, 0], w.f.vz[0, 0], w.f.y[0, 0], w.f.vy[0, 0] = 0.5, 0.0, 0.0, -0.0784
    w.f.atk_damage[0, 0], w.f.atk_speed[0, 0], w.f.axe[0, 0] = 9.0, 1.0, True
    w.f.ticker[0, 0] = 100
    tick(w, 1, attack=True, hold=(1,))
    assert w.f.shield_cd[0, 1] == 100
    assert not w.f.blocking()[0, 1]
    w.f.ticker[0, 0] = 100
    tick(w, 1, attack=True, hold=(1,))
    assert w.f.health[0, 1] < 20.0


def test_a_hit_on_a_team_mate_does_nothing_and_switching_costs_the_charge():
    w = world(others=[(0.5, 2.5, K.sword(None), 0)])
    tick(w, 1, attack=True)
    assert w.f.health[0, 1] == 20.0
    w.f.ticker[0, 0] = 100
    tick(w, 1, hand=3)
    assert w.f.ticker[0, 0] == 0


def test_hunger_ends_the_sprint_and_the_saturation_effect_heals_fast():
    w = world()
    w.f.food[0, 0] = 6.0
    tick(w, 5, forward=1.0, sprint=True)
    assert not w.f.sprinting[0, 0]
    w = world(kit=K.koth(None))
    w.f.health[0, 0] = 10.0
    tick(w, 100)
    assert w.f.health[0, 0] == pytest.approx(20.0, abs=0.5)


def test_a_hard_push_does_not_go_through_a_wall():
    def box(t):
        t.fill_box(0, -6, -6, 6, 6, WALL)
        t.fill_box(0, -5, -5, 5, 5, 0.0)
    w = world(box)
    w.f.vz[0, 0] = 3.0
    tick(w, 5)
    assert w.f.z[0, 0] == pytest.approx(5 - 0.3, abs=0.01)
    assert w.f.alive[0, 0]


def test_resistance_five_takes_no_damage_but_still_the_push():
    w = world(kit=K.sumo(None), others=[(0.5, 2.5, K.sumo(None), 1)])
    tick(w, 1, attack=True)
    assert w.landed[0, 0]
    assert w.f.health[0, 1] == 20.0
    assert w.f.vz[0, 1] > 0.2


def test_a_big_fighter_next_to_a_wall_stands_on_the_floor():
    kit = K.sword(None)
    kit["scale"] = 2.0

    def nook(t):
        t.fill_box(0, -30, -30, 30, 30, 0.0)
        t.fill_box(0, 1, 1, 2, 2, WALL)  # a wall column diagonal to its cell, under its wide body
    w = world(nook, kit=kit)
    tick(w, 20)
    assert w.f.alive[0, 0] and w.f.on_ground[0, 0]
    assert w.f.y[0, 0] == pytest.approx(0.0)
