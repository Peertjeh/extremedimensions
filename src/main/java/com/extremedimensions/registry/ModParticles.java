package com.extremedimensions.registry;

import com.extremedimensions.ExtremeDimensions;

import net.minecraft.core.Registry;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;

import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;

/** The mod's own particles; their look and motion live on the client ({@code MoteParticle}). */
public final class ModParticles {
	private ModParticles() {}

	/** Teal soul light: the Tether, soul wards, journal pages. */
	public static final SimpleParticleType SOUL_MOTE = register("soul_mote");
	/** Flakes of ember light rising from Nether anchors. */
	public static final SimpleParticleType EMBER_MOTE = register("ember_mote");
	/** Violet twinkles rising from End anchors. */
	public static final SimpleParticleType VOID_MOTE = register("void_mote");
	/** Curling black ink: the Lurker's trail and its vanishing. */
	public static final SimpleParticleType LURKER_WISP = register("lurker_wisp");

	private static SimpleParticleType register(String id) {
		return Registry.register(BuiltInRegistries.PARTICLE_TYPE, ExtremeDimensions.id(id), FabricParticleTypes.simple());
	}

	public static void init() {
		// class loading registers the particle types
	}
}
