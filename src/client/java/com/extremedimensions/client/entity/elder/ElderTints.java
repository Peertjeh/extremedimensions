package com.extremedimensions.client.entity.elder;

import com.extremedimensions.elder.Ferryman;
import com.extremedimensions.elder.Matriarch;
import com.extremedimensions.elder.Weaver;

/**
 * The colour an Elder's light turns as it begins each blow, so that the blow can be read without a name: the same
 * colour for the same kind of danger. Its own colour stays for the rest, and a few blows put its light out instead. The
 * recoloured light is made by {@code tools/elders/tints.py}, in the order {@link #names} gives.
 */
final class ElderTints {
	private ElderTints() {}

	/** Its own light. */
	static final int NONE = -1;
	/** Its light goes out. */
	static final int DARK = -2;

	static String[] names(String elder) {
		return switch (elder) {
			case "matriarch" -> new String[] {"red", "green", "amber", "white"};
			case "ferryman" -> new String[] {"red", "sea", "gold", "white", "soul"};
			default -> new String[] {"blue", "crimson", "silver"};
		};
	}

	/** The tint (an index into {@link #names}) for an action, or {@link #NONE} or {@link #DARK}. */
	static int of(String elder, int action) {
		return switch (elder) {
			// Red for her charge, green for thorns out of the ground, amber for lanterns thrown, white for her wail
			case "matriarch" -> switch (action) {
				case Matriarch.RUSH -> 0;
				case Matriarch.ROOTS, Matriarch.CATHEDRAL -> 1;
				case Matriarch.LANTERNS, Matriarch.BARRAGE -> 2;
				case Matriarch.WAIL -> 3;
				default -> NONE;
			};
			// Red for his crossing, sea-green for the tide, gold for the knell, white for the pole, blue soul fire for
			// his lanterns; his light goes out as the gaol closes
			case "ferryman" -> switch (action) {
				case Ferryman.CROSSING -> 0;
				case Ferryman.TIDE -> 1;
				case Ferryman.KNELL -> 2;
				case Ferryman.SLAM -> 3;
				case Ferryman.LANTERN -> 4;
				case Ferryman.GAOL -> DARK;
				default -> NONE;
			};
			// Blue-white for the lance, crimson for the thousand needles, silver for needles; dark as it unravels
			default -> switch (action) {
				case Weaver.LANCE -> 0;
				case Weaver.THOUSAND -> 1;
				case Weaver.NEEDLES -> 2;
				case Weaver.UNRAVEL -> DARK;
				default -> NONE;
			};
		};
	}
}
