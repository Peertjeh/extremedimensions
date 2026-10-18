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
	public static final SoundEvent LURKER_HURT = register("lurker_hurt");
	public static final SoundEvent LURKER_DEATH = register("lurker_death");
	public static final SoundEvent LURKER_SHRIEK = register("lurker_shriek");
	public static final SoundEvent LURKER_FLEE = register("lurker_flee");
	public static final SoundEvent HOLLOWLING_AMBIENT = register("hollowling_ambient");
	public static final SoundEvent HOLLOWLING_HURT = register("hollowling_hurt");
	public static final SoundEvent HOLLOWLING_DEATH = register("hollowling_death");
	public static final SoundEvent KEEPER_ROAR = register("keeper_roar");
	public static final SoundEvent VEIL_RAY_SONG = register("veil_ray_song");
	public static final SoundEvent MUSIC_MENU_1 = register("music.menu_1");
	public static final SoundEvent MUSIC_MENU_2 = register("music.menu_2");
	/** The Elders' fight music: "Cavernous Echoes", "Subterranean Pressure", "Final Collapse". */
	public static final SoundEvent MUSIC_BOSS_MATRIARCH = register("music.boss_matriarch");
	public static final SoundEvent MUSIC_BOSS_FERRYMAN = register("music.boss_ferryman");
	public static final SoundEvent MUSIC_BOSS_WEAVER = register("music.boss_weaver");

	/** The Elders' voices and the sounds that give their blows away (see ElderTells), "elder.<elder>.<blow>". */
	private static final String[] ELDER_TELLS = {
			"matriarch.voice", "matriarch.sweep", "matriarch.lanterns", "matriarch.roots", "matriarch.wail", "matriarch.rush",
			"matriarch.cathedral", "matriarch.barrage", "matriarch.snare",
			"ferryman.voice", "ferryman.slam", "ferryman.chain", "ferryman.lantern", "ferryman.knell", "ferryman.crossing",
			"ferryman.gaol", "ferryman.tide", "ferryman.snare",
			"weaver.voice", "weaver.thread", "weaver.needles", "weaver.unravel", "weaver.lance", "weaver.cradle",
			"weaver.thousand", "weaver.snare"};

	static {
		for (String tell : ELDER_TELLS) {
			register("elder." + tell);
		}
	}

	private static SoundEvent register(String path) {
		var id = ExtremeDimensions.id(path);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}

	/** Forces class loading, and with it registration. */
	public static void init() {}
}
