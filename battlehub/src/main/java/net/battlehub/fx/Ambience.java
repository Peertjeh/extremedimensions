package net.battlehub.fx;

import java.util.HashMap;
import java.util.Map;

import net.battlehub.arch.arena.Dress;
import net.battlehub.arena.Theme;
import net.minecraft.core.particles.ParticleOptions;

/**
 * The air of an arena: a few particles of its theme drifting round the players while a match is on (fireflies in
 * the forest, spores in the nether and the jungle, snow on ice, petals under the cherry trees, ash in the sky,
 * souls in the deep dark). The particle comes from the theme's dress; a particle this version lacks is skipped.
 */
public final class Ambience {
	private Ambience() {}

	private static final Map<String, String> IDS = new HashMap<>();

	public static String id(Theme t) {
		return IDS.computeIfAbsent(t.id(), id -> Dress.of(id, 0).ambient);
	}

	/** The theme's particle, or null when it has none (or the game does not know it). */
	public static ParticleOptions particle(Theme t) {
		String id = id(t);
		return id.isEmpty() ? null : net.battlehub.progression.Particles.simple(id);
	}
}
