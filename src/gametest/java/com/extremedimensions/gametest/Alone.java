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
	static final String MATRIARCH_REQUIEM = "extremedimensions_test:alone/matriarch_requiem";
	// (3.43: the Weaver's and Ferryman's new blows tear ground and throw players well past a test's own floor)
	static final String WEAVER_WARP = "extremedimensions_test:alone/weaver_warp";
	static final String WEAVER_SPINDLE = "extremedimensions_test:alone/weaver_spindle";
	static final String WEAVER_NEEDLEFALL = "extremedimensions_test:alone/weaver_needlefall";
	static final String FERRYMAN_UNDERTOW = "extremedimensions_test:alone/ferryman_undertow";
	static final String FERRYMAN_OARS = "extremedimensions_test:alone/ferryman_oars";
	static final String FERRYMAN_BELLS = "extremedimensions_test:alone/ferryman_bells";
	static final String MATRIARCH_STIRS = "extremedimensions_test:alone/matriarch_stirs";
	static final String MATRIARCH_SETTLES = "extremedimensions_test:alone/matriarch_settles";
	static final String WEAVER_SETTLES = "extremedimensions_test:alone/weaver_settles";
	static final String REACH_ANCHOR = "extremedimensions_test:alone/reach_anchor";
	static final String REACH_HAULER = "extremedimensions_test:alone/reach_hauler";
	// (3.44: the End's ending is the world's, and its scenes reach past a test's own floor)
	static final String CHOICE_HOME = "extremedimensions_test:alone/choice_home";
	static final String CHOICE_LOCK = "extremedimensions_test:alone/choice_lock";
	static final String CHOICE_ENDERMEN = "extremedimensions_test:alone/choice_endermen";
	static final String RITES_HOME = "extremedimensions_test:alone/rites_home";
	static final String RITES_LOCK = "extremedimensions_test:alone/rites_lock";
	static final String FERRYMAN_TOLL = "extremedimensions_test:alone/ferryman_toll";
	static final String ELDER_FORMING = "extremedimensions_test:alone/elder_forming";
	static final String ARENA_RAISED = "extremedimensions_test:alone/arena_raised";
	// (nothing a fight puts into the world is left: whole fights high over the world, and what they share)
	static final String LEFTOVER_DESIGN = "extremedimensions_test:alone/leftover_design";
	static final String LEFTOVER_JOURNAL = "extremedimensions_test:alone/leftover_journal";
	static final String LEFTOVER_MATRIARCH = "extremedimensions_test:alone/leftover_matriarch";
	static final String LEFTOVER_FERRYMAN = "extremedimensions_test:alone/leftover_ferryman";
	static final String LEFTOVER_WEAVER = "extremedimensions_test:alone/leftover_weaver";
	static final String LEFTOVER_MASON = "extremedimensions_test:alone/leftover_mason";
	static final String LEFTOVER_KNOT_MOTHER = "extremedimensions_test:alone/leftover_knot_mother";
	static final String WEAVER_RINGS = "extremedimensions_test:alone/weaver_rings";
	static final String KNOT_MOTHER = "extremedimensions_test:alone/knot_mother";
	static final String ELDER_HEALTH = "extremedimensions_test:alone/elder_health";
	static final String FLYERS_PULLED = "extremedimensions_test:alone/flyers_pulled";
	static final String PINION_UNBOUND = "extremedimensions_test:alone/pinion_unbound";
	// (a kill whose drops are counted: nothing another test drops may lie among them)
	static final String MAGPIES_GLINT = "extremedimensions_test:alone/magpies_glint";
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
	static final String LURKER_TAKE = "extremedimensions_test:alone/lurker_take";
	static final String LURKER_HUNT = "extremedimensions_test:alone/lurker_hunt";
	static final String LURKER_LEAP = "extremedimensions_test:alone/lurker_leap";
	static final String LURKER_STRIDE = "extremedimensions_test:alone/lurker_stride";
	static final String LURKER_NAMED = "extremedimensions_test:alone/lurker_named";
	static final String LURKER_SWARM = "extremedimensions_test:alone/lurker_swarm";
	static final String FLEE = "extremedimensions_test:alone/flee";
	static final String LURKER_HIDE = "extremedimensions_test:alone/lurker_hide";
	static final String LURKER_BOLT = "extremedimensions_test:alone/lurker_bolt";
	static final String SWARM_DREAD = "extremedimensions_test:alone/swarm_dread";
	static final String CAVE_TUNNEL = "extremedimensions_test:alone/cave_tunnel";
	static final String CAVE_HEARING = "extremedimensions_test:alone/cave_hearing";
	static final String CAVE_COMES = "extremedimensions_test:alone/cave_comes";
	static final String SCULK_SPARED = "extremedimensions_test:alone/sculk_spared";
	static final String SCULK_BECKON = "extremedimensions_test:alone/sculk_beckon";
	static final String SCULK_LURE = "extremedimensions_test:alone/sculk_lure";
	static final String SCULK_COMES = "extremedimensions_test:alone/sculk_comes";
	static final String SCULK_SENSOR = "extremedimensions_test:alone/sculk_sensor";
	static final String PATH_ENDING = "extremedimensions_test:alone/path_ending";
	static final String MAGPIE_RAID = "extremedimensions_test:alone/magpie_raid";
	static final String WICK_FEAST = "extremedimensions_test:alone/wick_feast";
	static final String TIDE_DRAG = "extremedimensions_test:alone/tide_drag";
	static final String GATE_SMASH = "extremedimensions_test:alone/gate_smash";
	static final String LOCK_SNATCH = "extremedimensions_test:alone/lock_snatch";
	static final String THREAD_BITE = "extremedimensions_test:alone/thread_bite";
	static final String EDGE_THROW = "extremedimensions_test:alone/edge_throw";
	static final String CRAG_WAKE = "extremedimensions_test:alone/crag_wake";
	static final String KNOT_DRAW = "extremedimensions_test:alone/knot_draw";
	static final String KNOT_SNARL = "extremedimensions_test:alone/knot_snarl";
	static final String KNOT_ANCHOR = "extremedimensions_test:alone/knot_anchor";
	// (an Elder on ground laid wider than a test's own space, which it reaches well past)
	static final String FOOTING_MATRIARCH_RAISED = "extremedimensions_test:alone/footing_matriarch_raised";
	static final String FOOTING_MATRIARCH_RIM = "extremedimensions_test:alone/footing_matriarch_rim";
	static final String FOOTING_MATRIARCH_LEDGE = "extremedimensions_test:alone/footing_matriarch_ledge";
	static final String FOOTING_MATRIARCH_WALL = "extremedimensions_test:alone/footing_matriarch_wall";
	static final String FOOTING_FERRYMAN_RAISED = "extremedimensions_test:alone/footing_ferryman_raised";
	static final String FOOTING_FERRYMAN_RIM = "extremedimensions_test:alone/footing_ferryman_rim";
	static final String FOOTING_FERRYMAN_LEDGE = "extremedimensions_test:alone/footing_ferryman_ledge";
	static final String FOOTING_FERRYMAN_WALL = "extremedimensions_test:alone/footing_ferryman_wall";
	static final String FOOTING_WEAVER_RAISED = "extremedimensions_test:alone/footing_weaver_raised";
	static final String FOOTING_WEAVER_RIM = "extremedimensions_test:alone/footing_weaver_rim";
	static final String FOOTING_WEAVER_LEDGE = "extremedimensions_test:alone/footing_weaver_ledge";
	static final String FOOTING_WEAVER_WALL = "extremedimensions_test:alone/footing_weaver_wall";
	static final String FOOTING_MASON_RAISED = "extremedimensions_test:alone/footing_mason_raised";
	static final String FOOTING_MASON_RIM = "extremedimensions_test:alone/footing_mason_rim";
	static final String FOOTING_MASON_LEDGE = "extremedimensions_test:alone/footing_mason_ledge";
	static final String FOOTING_MASON_WALL = "extremedimensions_test:alone/footing_mason_wall";
	static final String FOOTING_MASON_BLINK = "extremedimensions_test:alone/footing_mason_blink";
	// (3.48: the Heart's fight is the Seam's own, one for the whole world, and its arena fills the sky round the Heart)
	static final String HEART_LEAVE = "extremedimensions_test:alone/heart_leave";
	static final String HEART_RISE = "extremedimensions_test:alone/heart_rise";
	static final String HEART_ARENA = "extremedimensions_test:alone/heart_arena";
	// (a reset of the relics reaches every player the world knows, and every relic made while it holds)
	static final String RELIC_RESET = "extremedimensions_test:alone/relic_reset";
	// (the Pale Mason lifts a block more for every player near him, and the players of the tests round his would count)
	static final String MASON_LIFT = "extremedimensions_test:alone/mason_lift";
}
