package com.extremedimensions.server;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/** Dimension ids (paths) and small helpers around them. */
public final class Dims {
	private Dims() {}

	public static final String OVERWORLD = "overworld";
	public static final String NETHER = "the_nether";
	public static final String END = "the_end";
	/** Short id of the Seam ({@code extremedimensions:the_seam}). */
	public static final String SEAM = "the_seam";

	public static String of(Level level) {
		return level.dimension().identifier().getPath();
	}

	public static String of(ServerPlayer p) {
		return of(p.level());
	}

	/** Full id such as {@code minecraft:the_nether}, for commands. */
	public static String fullId(ServerPlayer p) {
		return p.level().dimension().identifier().toString();
	}

	/** Full id for a short vanilla dimension id ({@code the_nether} to {@code minecraft:the_nether}). */
	public static String fullIdOf(String dim) {
		if (SEAM.equals(dim)) {
			return "extremedimensions:" + SEAM;
		}
		return dim.contains(":") ? dim : "minecraft:" + dim;
	}

	/** Dimensions where expeditions drain charge. */
	public static boolean isBeyond(String dim) {
		return NETHER.equals(dim) || END.equals(dim);
	}

	/** Language key of the dimension's display name. */
	public static String nameKey(String dim) {
		return switch (dim) {
			case NETHER -> "dimension.extremedimensions.nether";
			case END -> "dimension.extremedimensions.end";
			case SEAM -> "dimension.extremedimensions.seam";
			default -> "dimension.extremedimensions.overworld";
		};
	}
}
