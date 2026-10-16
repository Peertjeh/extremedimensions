package net.battlehub.progression;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import net.battlehub.core.ParticleSpec;
import net.battlehub.util.Ids;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.DustColorTransitionOptions;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.particles.TrailParticleOption;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/** Turns a cosmetic's particle text ({@link ParticleSpec}) into particles in the world. */
public final class Particles {
	private Particles() {}

	private static final Map<String, Optional<ParticleOptions>> SIMPLE = new HashMap<>();
	private static final Map<String, List<ParticleSpec>> PARSED = new HashMap<>();

	public static List<ParticleSpec> specs(String text) {
		return PARSED.computeIfAbsent(text == null ? "" : text, ParticleSpec::parseAll);
	}

	/** A plain particle by id, or null. */
	public static ParticleOptions simple(String id) {
		return SIMPLE.computeIfAbsent(id, k -> {
			Identifier rl = Identifier.tryParse(k);
			if (rl == null || !BuiltInRegistries.PARTICLE_TYPE.containsKey(rl)) {
				return Optional.empty();
			}
			ParticleType<?> t = BuiltInRegistries.PARTICLE_TYPE.getValue(rl);
			return t instanceof SimpleParticleType s ? Optional.<ParticleOptions>of(s) : Optional.<ParticleOptions>empty();
		}).orElse(null);
	}

	/** Can every part of this text be shown? */
	public static boolean valid(String text) {
		List<ParticleSpec> list = ParticleSpec.parseAll(text);
		if (list.isEmpty()) {
			return false;
		}
		for (ParticleSpec s : list) {
			if (options(s, 0, Vec3.ZERO) == null) return false;
		}
		return true;
	}

	/** The particle for one spec at this moment ({@code at}: where a trail streak starts). */
	@SuppressWarnings("unchecked")
	public static ParticleOptions options(ParticleSpec s, long tick, Vec3 at) {
		return switch (s.kind()) {
			case SIMPLE -> simple(s.id());
			case DUST -> new DustParticleOptions(s.color(), s.scale());
			case RAINBOW -> new DustParticleOptions(s.colorAt(tick, 90), s.scale());
			case DUST_FADE -> new DustColorTransitionOptions(s.color(), s.color2(), s.scale());
			case TRAIL -> new TrailParticleOption(at.add(0, 0.5, 0), s.color(), 12);
			case BLOCK -> BuiltInRegistries.BLOCK.containsKey(Ids.id(s.id())) && !Ids.state(s.id()).isAir()
					? new BlockParticleOption(ParticleTypes.BLOCK, Ids.state(s.id())) : null;
			case ITEM -> Ids.isItem(s.id()) ? new ItemParticleOption(ParticleTypes.ITEM, Ids.item(s.id())) : null;
			case TINT -> {
				ParticleType<?> t = BuiltInRegistries.PARTICLE_TYPE.getValue(Ids.id(s.id()));
				if (t != ParticleTypes.ENTITY_EFFECT && t != ParticleTypes.TINTED_LEAVES) yield null;
				yield ColorParticleOption.create((ParticleType<ColorParticleOption>) t, 0xFF000000 | s.color());
			}
		};
	}

	/** Every part of the text at one spot. */
	public static void emit(ServerLevel level, String text, double x, double y, double z, int count, double dx, double dy, double dz,
			double speed, long tick) {
		Vec3 at = new Vec3(x, y, z);
		for (ParticleSpec s : specs(text)) {
			ParticleOptions o = options(s, tick, at);
			if (o != null) {
				// Coloured dust ignores the speed; a little spread looks the same
				level.sendParticles(o, x, y, z, count, dx, dy, dz, s.kind() == ParticleSpec.Kind.SIMPLE ? speed : 0);
			}
		}
	}
}
