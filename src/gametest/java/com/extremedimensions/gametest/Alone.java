package com.extremedimensions.gametest;

/**
 * Test environments that hold one test each, so that test runs in a batch of its own. The game runs the tests of a batch
 * side by side in one world, and a fight with an Elder reaches far past its own test's walls: a Weaver's beams strike
 * any player they sweep through, an arena rises over its neighbours, and the fights change settings they all share. So
 * every test that wakes an Elder runs alone, and so does the Frayed Ray's, whose prey stands out in the open sky. Each
 * name has its file under {@code data/extremedimensions_test/test_environment/alone/}; never give two tests one name.
 */
final class Alone {
	private Alone() {}

	static final String FRAYED_RAY = "extremedimensions_test:alone/frayed_ray";
	static final String MATRIARCH_CALLED = "extremedimensions_test:alone/matriarch_called";
	static final String FERRYMAN_TOLL = "extremedimensions_test:alone/ferryman_toll";
	static final String ELDER_FORMING = "extremedimensions_test:alone/elder_forming";
	static final String ARENA_RAISED = "extremedimensions_test:alone/arena_raised";
	static final String WEAVER_RINGS = "extremedimensions_test:alone/weaver_rings";
	static final String KNOT_MOTHER = "extremedimensions_test:alone/knot_mother";
	static final String ELDER_HEALTH = "extremedimensions_test:alone/elder_health";
	static final String FLYERS_PULLED = "extremedimensions_test:alone/flyers_pulled";
	static final String FERRYMAN_GAOL = "extremedimensions_test:alone/ferryman_gaol";
	static final String WEAVER_LANCE = "extremedimensions_test:alone/weaver_lance";
	static final String LANCE_HIGH = "extremedimensions_test:alone/lance_high";
	static final String SHOCKWAVE_STEP = "extremedimensions_test:alone/shockwave_step";
	static final String PERF_MATRIARCH = "extremedimensions_test:alone/perf_matriarch";
	static final String PERF_FERRYMAN = "extremedimensions_test:alone/perf_ferryman";
	static final String PERF_WEAVER = "extremedimensions_test:alone/perf_weaver";
	static final String WILD_KILLS = "extremedimensions_test:alone/wild_kills";
	static final String ONE_SPOT = "extremedimensions_test:alone/one_spot";
	static final String SHARED_SPOT = "extremedimensions_test:alone/shared_spot";
	static final String SPAWNER_ROOM = "extremedimensions_test:alone/spawner_room";
}
