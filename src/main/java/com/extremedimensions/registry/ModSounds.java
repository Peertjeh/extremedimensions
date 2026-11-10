package com.extremedimensions.registry;

import com.extremedimensions.ExtremeDimensions;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;

/** Every sound of the mod; the files are defined in {@code assets/extremedimensions/sounds.json}. */
public final class ModSounds {
	private ModSounds() {}

	public static final SoundEvent PENALTY = register("penalty");
	public static final SoundEvent EXPEDITION_ENDED = register("expedition_ended");
	public static final SoundEvent PORTAL_BEYOND = register("portal_beyond");
	public static final SoundEvent PORTAL_HOME = register("portal_home");
	public static final SoundEvent COLLAPSE = register("collapse");
	public static final SoundEvent LURKER_WHISPER = register("lurker_whisper");
	public static final SoundEvent LURKER_TAKE = register("lurker_take");
	public static final SoundEvent LURKER_DRAIN = register("lurker_drain");
	public static final SoundEvent LURKER_VANISH = register("lurker_vanish");
	public static final SoundEvent LURKER_AMBIENT = register("lurker_ambient");
	public static final SoundEvent LURKER_STEP = register("lurker_step");
	/** The Lurkers of the deep, each with a voice of its own place (tools/sounds/lurker_voices.py). */
	public static final SoundEvent CAVE_LURKER_CLICK = register("cave_lurker.click");
	public static final SoundEvent CAVE_LURKER_BREATH = register("cave_lurker.breath");
	public static final SoundEvent CAVE_LURKER_HISS = register("cave_lurker.hiss");
	public static final SoundEvent CAVE_LURKER_HUNT = register("cave_lurker.hunt");
	public static final SoundEvent CAVE_LURKER_HURT = register("cave_lurker.hurt");
	public static final SoundEvent CAVE_LURKER_DEATH = register("cave_lurker.death");
	public static final SoundEvent CAVE_LURKER_STEP = register("cave_lurker.step");
	public static final SoundEvent CAVE_LURKER_GRAB = register("cave_lurker.grab");
	public static final SoundEvent SCULK_LURKER_CHIRP = register("sculk_lurker.chirp");
	public static final SoundEvent SCULK_LURKER_RATTLE = register("sculk_lurker.rattle");
	public static final SoundEvent SCULK_LURKER_CALL = register("sculk_lurker.call");
	public static final SoundEvent SCULK_LURKER_PULSE = register("sculk_lurker.pulse");
	public static final SoundEvent SCULK_LURKER_HURT = register("sculk_lurker.hurt");
	public static final SoundEvent SCULK_LURKER_DEATH = register("sculk_lurker.death");
	public static final SoundEvent SCULK_LURKER_STEP = register("sculk_lurker.step");
	public static final SoundEvent SCULK_LURKER_LAUGH = register("sculk_lurker.laugh");
	public static final SoundEvent LURKER_HURT = register("lurker_hurt");
	public static final SoundEvent LURKER_DEATH = register("lurker_death");
	public static final SoundEvent LURKER_SHRIEK = register("lurker_shriek");
	public static final SoundEvent LURKER_FLEE = register("lurker_flee");
	public static final SoundEvent HOLLOWLING_AMBIENT = register("hollowling_ambient");
	public static final SoundEvent HOLLOWLING_HURT = register("hollowling_hurt");
	public static final SoundEvent HOLLOWLING_DEATH = register("hollowling_death");
	public static final SoundEvent KEEPER_ROAR = register("keeper_roar");
	public static final SoundEvent VEIL_RAY_SONG = register("veil_ray_song");
	/** The menus' music: "Tick of the Boundary" on the title screen and the menus, "Ominous Loading Screen" while a world loads. */
	public static final SoundEvent MUSIC_MENU_TITLE = register("music.menu_title");
	public static final SoundEvent MUSIC_MENU_LOADING = register("music.menu_loading");
	/**
	 * The Elders' fight music: "Cavernous Echoes", "Subterranean Pressure", "Final Collapse", and the Knot Mother's own,
	 * "Mother of Knots" (tools/sounds/boss_knot_mother.py).
	 */
	public static final SoundEvent MUSIC_BOSS_MATRIARCH = register("music.boss_matriarch");
	public static final SoundEvent MUSIC_BOSS_FERRYMAN = register("music.boss_ferryman");
	public static final SoundEvent MUSIC_BOSS_WEAVER = register("music.boss_weaver");
	public static final SoundEvent MUSIC_BOSS_KNOT_MOTHER = register("music.boss_knot_mother");
	/** The Pale Mason's theme (tools/sounds/boss_pale_mason.py). */
	public static final SoundEvent MUSIC_BOSS_PALE_MASON = register("music.boss_pale_mason");
	/** The Starved One's theme, "The First Wound" (tools/sounds/boss_starved_one.py): track 5 of BossMusic. */
	public static final SoundEvent MUSIC_BOSS_STARVED_ONE = register("music.boss_starved_one");
	/** The Seam's music disc: "Tick of the Boundary", the menus' track, as a jukebox plays it. */
	public static final SoundEvent MUSIC_DISC_TICK_OF_THE_BOUNDARY = register("music_disc.tick_of_the_boundary");
	/** The wound's own record: "What the Wound Remembers", made from its ambient sounds. */
	public static final SoundEvent MUSIC_DISC_WOUND_REMEMBERS = register("music_disc.wound_remembers");
	/** The deep's record: "What Listens". */
	public static final SoundEvent MUSIC_DISC_WHAT_LISTENS = register("music_disc.what_listens");
	/** The Elders' requiem: "Elegy of the Elders". */
	public static final SoundEvent MUSIC_DISC_ELEGY_OF_THE_ELDERS = register("music_disc.elegy_of_the_elders");
	/** The abyss's record: "The Abyss Answers". */
	public static final SoundEvent MUSIC_DISC_THE_ABYSS_ANSWERS = register("music_disc.the_abyss_answers");

	/** The Elders' voices and the sounds that give their blows away (see ElderTells), "elder.<elder>.<blow>". */
	private static final String[] ELDER_TELLS = {
			"matriarch.voice", "matriarch.sweep", "matriarch.lanterns", "matriarch.roots", "matriarch.wail", "matriarch.rush",
			"matriarch.cathedral", "matriarch.barrage", "matriarch.snare",
			"ferryman.voice", "ferryman.slam", "ferryman.chain", "ferryman.lantern", "ferryman.knell", "ferryman.crossing",
			"ferryman.gaol", "ferryman.tide", "ferryman.snare",
			// his blows of 3.43 (tools/sounds/elders.py): the whirlpool of his undertow, its geyser of soul fire, a stroke
			// of his oars, a toll of his drowned bells
			"ferryman.undertow", "ferryman.geyser", "ferryman.oars", "ferryman.bell",
			"weaver.voice", "weaver.thread", "weaver.needles", "weaver.unravel", "weaver.lance", "weaver.cradle",
			"weaver.thousand", "weaver.snare",
			// its blows of 3.43 (tools/sounds/elders.py): the warp drawn taut and falling, the spindle storm, the great
			// needles falling
			"weaver.warp", "weaver.warpfall", "weaver.spindle", "weaver.needlefall",
			// The Knot Mother's own (tools/sounds/knot_mother.py): her voice before a blow, her Threadline drawn and swept,
			// her Snare woven and closing, threads cast, a flyer caught, her anchors struck and broken, her laid bare, the
			// turning of her phases, her ultimate, and her forming
			"knot_mother.voice", "knot_mother.threadline", "knot_mother.lash", "knot_mother.knot", "knot_mother.close",
			"knot_mother.cast", "knot_mother.snare", "knot_mother.anchor_hit", "knot_mother.anchor_break", "knot_mother.exposed",
			"knot_mother.phase", "knot_mother.ultimate", "knot_mother.wake",
			// The Pale Mason's own (tools/sounds/pale_mason.py): his voice before a blow, his blink, the claw coming down,
			// his walls grinding up, great blocks lifted, hurled and landing, his gaze, the Last Door opening and drawing,
			// a flyer caught, the turning of his phases, his ultimate, and his coming
			"pale_mason.voice", "pale_mason.blink", "pale_mason.strike", "pale_mason.wall", "pale_mason.lift", "pale_mason.throw",
			"pale_mason.impact", "pale_mason.gaze", "pale_mason.door", "pale_mason.pull", "pale_mason.snare", "pale_mason.phase",
			"pale_mason.ultimate", "pale_mason.wake",
			// his blows of 3.41, and the ground torn up as his ultimate breaks
			"pale_mason.chains", "pale_mason.swing", "pale_mason.fling", "pale_mason.pillar", "pale_mason.ring", "pale_mason.spiral",
			"pale_mason.burst", "pale_mason.upheaval"};

	static {
		for (String tell : ELDER_TELLS) {
			register("elder." + tell);
		}
	}

	/**
	 * Each Elder's own body: hit, at rest, dying (Matriarch, Ferryman, Weaver, Knot Mother, Pale Mason: see ElderFx.voice); its steps
	 * and roar are played by name.
	 */
	public static final SoundEvent[] ELDER_HURT = {register("elder.matriarch.hurt"), register("elder.ferryman.hurt"), register("elder.weaver.hurt"),
			register("elder.knot_mother.hurt"),
			register("elder.pale_mason.hurt")};
	public static final SoundEvent[] ELDER_AMBIENT = {register("elder.matriarch.ambient"), register("elder.ferryman.ambient"),
			register("elder.weaver.ambient"), register("elder.knot_mother.ambient"),
			register("elder.pale_mason.ambient")};
	public static final SoundEvent[] ELDER_DEATH = {register("elder.matriarch.death"), register("elder.ferryman.death"), register("elder.weaver.death"),
			register("elder.knot_mother.death"),
			register("elder.pale_mason.death")};
	public static final SoundEvent[] ELDER_STEP = {register("elder.matriarch.step"), register("elder.ferryman.step"), register("elder.weaver.step"),
			register("elder.knot_mother.step"),
			register("elder.pale_mason.step")};
	public static final SoundEvent[] ELDER_ROAR = {register("elder.matriarch.roar"), register("elder.ferryman.roar"), register("elder.weaver.roar"),
			register("elder.knot_mother.roar"),
			register("elder.pale_mason.roar")};

	/*
	 * The Starved One's own (tools/sounds/starved_one.py), "elder.starved_one.<name>", made at the pitch they are played
	 * at (1.0): its voice before a blow (its tell), at rest, hit, its roar and its death; its rise out of the Heart; its
	 * dive, its call to the tribe's red young, the Veil Rays' light turned into its hunting ray, time drunk; a pillar's
	 * heart feeding it and quenched, bound by all four and tearing loose; the turning of its phases, its strongest blow,
	 * and the ground erupting.
	 */
	public static final SoundEvent STARVED_ONE_VOICE = register("elder.starved_one.voice");
	public static final SoundEvent STARVED_ONE_AMBIENT = register("elder.starved_one.ambient");
	public static final SoundEvent STARVED_ONE_HURT = register("elder.starved_one.hurt");
	public static final SoundEvent STARVED_ONE_ROAR = register("elder.starved_one.roar");
	public static final SoundEvent STARVED_ONE_DEATH = register("elder.starved_one.death");
	public static final SoundEvent STARVED_ONE_WAKE = register("elder.starved_one.wake");
	public static final SoundEvent STARVED_ONE_SWOOP = register("elder.starved_one.swoop");
	public static final SoundEvent STARVED_ONE_CALL = register("elder.starved_one.call");
	public static final SoundEvent STARVED_ONE_RAY = register("elder.starved_one.ray");
	public static final SoundEvent STARVED_ONE_DRINK = register("elder.starved_one.drink");
	public static final SoundEvent STARVED_ONE_HEAL = register("elder.starved_one.heal");
	public static final SoundEvent STARVED_ONE_QUENCH = register("elder.starved_one.quench");
	public static final SoundEvent STARVED_ONE_BOUND = register("elder.starved_one.bound");
	public static final SoundEvent STARVED_ONE_BREAK_FREE = register("elder.starved_one.break_free");
	public static final SoundEvent STARVED_ONE_PHASE = register("elder.starved_one.phase");
	public static final SoundEvent STARVED_ONE_ULTIMATE = register("elder.starved_one.ultimate");
	public static final SoundEvent STARVED_ONE_UPHEAVAL = register("elder.starved_one.upheaval");

	private static SoundEvent register(String path) {
		var id = ExtremeDimensions.id(path);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}

	/** Forces class loading, and with it registration. */
	public static void init() {}
}
