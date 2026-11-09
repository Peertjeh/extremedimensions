package com.extremedimensions.client.entity.elder;

import com.extremedimensions.elder.ElderBoss;
import com.extremedimensions.elder.Ferryman;
import com.extremedimensions.elder.KnotMother;
import com.extremedimensions.elder.Matriarch;
import com.extremedimensions.elder.PaleMason;
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
			case "knot_mother" -> new String[] {"amber", "violet", "white"};
			case "pale_mason" -> new String[] {"violet", "white", "gold"};
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
			// The Knot Mother: amber as her Threadline is drawn, violet as her Snare closes, white as she lies exposed
			case "knot_mother" -> switch (action) {
				case KnotMother.THREADLINE -> 0;
				case KnotMother.SNARE -> 1;
				case KnotMother.EXPOSED -> 2;
				default -> NONE;
			};
			// The Pale Mason: violet as he strikes out of the air, as his gaze falls, as his Bound Stones swing and as his
			// Spiral winds; white as his blocks are lifted and hurled and as his Pillars tear up; gold as his walls rise,
			// as he casts his rings and as the Last Door opens
			case "pale_mason" -> switch (action) {
				case PaleMason.STRIKE, PaleMason.GAZE, PaleMason.CHAINS, PaleMason.SPIRAL -> 0;
				case PaleMason.LIFT, PaleMason.THROW, PaleMason.PILLARS -> 1;
				case PaleMason.WALLS, PaleMason.RINGS, ElderBoss.ULTIMATE -> 2;
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
