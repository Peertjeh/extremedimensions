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
	public static final SoundEvent MUSIC_MENU_1 = register("music.menu_1");
	public static final SoundEvent MUSIC_MENU_2 = register("music.menu_2");

	private static SoundEvent register(String path) {
		var id = ExtremeDimensions.id(path);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}

	/** Forces class loading, and with it registration. */
	public static void init() {}
}
