package com.extremedimensions.server;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/** Dimension ids (paths) and small helpers around them. */
public final class Dims {
	private Dims() {}

	public static final String OVERWORLD = "overworld";
	public static final String NETHER = "the_nether";
	public static final String END = "the_end";

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

	/** Dimensions where expeditions drain charge. */
	public static boolean isBeyond(String dim) {
		return NETHER.equals(dim) || END.equals(dim);
	}

	/** Language key of the dimension's display name. */
	public static String nameKey(String dim) {
		return switch (dim) {
			case NETHER -> "dimension.extremedimensions.nether";
			case END -> "dimension.extremedimensions.end";
			default -> "dimension.extremedimensions.overworld";
		};
	}
}
