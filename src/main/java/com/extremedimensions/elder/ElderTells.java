package com.extremedimensions.elder;

import com.extremedimensions.server.WorldFx;

import net.minecraft.server.level.ServerPlayer;

/**
 * How an Elder gives away the blow it is about to strike, now that no name is called out: its own voice and a sound
 * of its own for each blow (with a subtitle, for those who read them), as its body moves into the blow and its light
 * changes colour (see the client's renderer). Each Elder sounds like itself: the Matriarch of wood, roots and a deep
 * groan; the Ferryman of bells, chains and the river; the Last Weaver of glass, threads and a high, thin chime.
 */
final class ElderTells {
	private ElderTells() {}

	private static final String[] NAMES = {"matriarch", "ferryman", "weaver"};

	/** A blow begins: the Elder's voice, and the sound of that blow. */
	static void begin(ElderBoss b, int action) {
		String blow = blow(b, action);
		if (blow == null) {
			return;
		}
		String who = NAMES[ElderFx.index(b)];
		double y = b.getY() + b.getBbHeight() * 0.6;
		WorldFx.soundIn(ElderFx.dim(b), "extremedimensions:elder." + who + ".voice", "hostile", b.getX(), y, b.getZ(), 4f, 1f);
		WorldFx.soundIn(ElderFx.dim(b), "extremedimensions:elder." + who + "." + blow, "hostile", b.getX(), y, b.getZ(), 4f, 1f);
	}

	/** It pulls a flyer out of the air. */
	static void snare(ElderBoss b, ServerPlayer q) {
		String who = NAMES[ElderFx.index(b)];
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
