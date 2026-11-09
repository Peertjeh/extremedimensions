package com.extremedimensions.elder;

import com.extremedimensions.server.WorldFx;

import net.minecraft.server.level.ServerPlayer;

/**
 * How an Elder gives away the blow it is about to strike, now that no name is called out: its own voice and a sound
 * of its own for each blow (with a subtitle, for those who read them), as its body moves into the blow and its light
 * changes colour (see the client's renderer). Each Elder sounds like itself: the Matriarch of wood, roots and a deep
 * groan; the Ferryman of bells, chains and the river; the Last Weaver of glass, threads and a high, thin chime; the Knot
 * Mother of a choir, bronze and thick threads drawn taut (her own sounds, tools/sounds/knot_mother.py); the Pale Mason of
 * stone, chains and the void's warble (tools/sounds/pale_mason.py).
 */
final class ElderTells {
	private ElderTells() {}

	/**
	 * A blow begins: the Elder's voice, and the sound of that blow. The Knot Mother's Threadline is drawn up to the very
	 * moment she sweeps it, so it plays faster as her blows come faster ({@code tempo}).
	 */
	static void begin(ElderBoss b, int action, double tempo) {
		String blow = blow(b, action);
		if (blow == null) {
			return;
		}
		String who = ElderFx.name(b);
		double y = b.getY() + b.getBbHeight() * 0.6;
		float rate = "threadline".equals(blow) ? (float) Math.max(0.5, Math.min(2.0, tempo)) : 1f;
		WorldFx.soundIn(ElderFx.dim(b), "extremedimensions:elder." + who + ".voice", "hostile", b.getX(), y, b.getZ(), 4f, 1f);
		WorldFx.soundIn(ElderFx.dim(b), "extremedimensions:elder." + who + "." + blow, "hostile", b.getX(), y, b.getZ(), 4f, rate);
	}

	/** It pulls a flyer out of the air. */
	static void snare(ElderBoss b, ServerPlayer q) {
		String who = ElderFx.name(b);
		WorldFx.soundIn(ElderFx.dim(b), "extremedimensions:elder." + who + ".snare", "hostile", q.getX(), q.getY(), q.getZ(), 3f, 1f);
		WorldFx.soundIn(ElderFx.dim(b), "extremedimensions:elder." + who + ".voice", "hostile", b.getX(), b.getY() + b.getBbHeight() * 0.6, b.getZ(), 4f, 1f);
	}

	/** The name of the sound of a blow (null for none: idle, forming, the ultimate has its own). */
	static String blow(ElderBoss b, int action) {
		if (b instanceof Matriarch) {
			return switch (action) {
				case Matriarch.SWEEP -> "sweep";
				case Matriarch.LANTERNS -> "lanterns";
				case Matriarch.ROOTS -> "roots";
				case Matriarch.WAIL -> "wail";
				case Matriarch.RUSH -> "rush";
				case Matriarch.CATHEDRAL -> "cathedral";
				case Matriarch.BARRAGE -> "barrage";
				default -> null;
			};
		}
		if (b instanceof Ferryman) {
			return switch (action) {
				case Ferryman.SLAM -> "slam";
				case Ferryman.CHAIN -> "chain";
				case Ferryman.LANTERN -> "lantern";
				case Ferryman.KNELL -> "knell";
				case Ferryman.CROSSING -> "crossing";
				case Ferryman.GAOL -> "gaol";
				case Ferryman.TIDE -> "tide";
				default -> null;
			};
		}
		if (b instanceof PaleMason) {
			// His own: his blink out of sight, his walls grinding up, great blocks lifted and hurled, his gaze
			return switch (action) {
				case PaleMason.STRIKE -> "blink";
				case PaleMason.WALLS -> "wall";
				case PaleMason.LIFT -> "lift";
				case PaleMason.THROW -> "throw";
				case PaleMason.GAZE -> "gaze";
				case PaleMason.CHAINS -> "chains";
				case PaleMason.PILLARS -> "pillar";
				case PaleMason.RINGS -> "ring";
				case PaleMason.SPIRAL -> "spiral";
				default -> null;
			};
		}
		if (b instanceof KnotMother) {
			// Her own: her Threadline drawn tight (it runs up to the moment she sweeps it); for her Snare and the blows she
			// shares with the Last Weaver, threads plucked as she casts (her Snare's own weaving is heard as it is laid)
			return switch (action) {
				case KnotMother.THREADLINE -> "threadline";
				case KnotMother.SNARE, Weaver.THREAD, Weaver.CRADLE -> "cast";
				default -> null;
			};
		}
		return switch (action) {
			case Weaver.THREAD -> "thread";
			case Weaver.NEEDLES -> "needles";
			case Weaver.UNRAVEL -> "unravel";
			case Weaver.LANCE -> "lance";
			case Weaver.CRADLE -> "cradle";
			case Weaver.THOUSAND -> "thousand";
			default -> null;
		};
	}
}
