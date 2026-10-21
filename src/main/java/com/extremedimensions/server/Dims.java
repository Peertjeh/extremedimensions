package com.extremedimensions.server;

import com.extremedimensions.config.Config;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/**
 * Dimension ids (paths) and small helpers around them. The mod belongs to the Overworld, the Nether, the End and its
 * own worlds (the Seam); every other world, another mod's, lies outside the wound's reach (see {@link #ours}).
 */
public final class Dims {
	private Dims() {}

	public static final String OVERWORLD = "overworld";
	public static final String NETHER = "the_nether";
	public static final String END = "the_end";
	/** Short id of the Seam ({@code extremedimensions:the_seam}). */
	public static final String SEAM = "the_seam";

	private static final String MINECRAFT = "minecraft";
	private static final String MOD = "extremedimensions";

	/**
	 * The short id of a world ({@code the_nether}); a world of another namespace keeps its full id
	 * ({@code othermod:the_nether}), so it never passes for one of the mod's under a borrowed name.
	 */
	public static String of(Level level) {
		Identifier id = level.dimension().identifier();
		return known(id) ? id.getPath() : id.toString();
	}

	/** Whether the mod acts in this world: the Overworld, the Nether, the End and its own (unless the config lets it act everywhere). */
	public static boolean ours(Level level) {
		return ours(level.dimension().identifier());
	}

	public static boolean ours(ServerPlayer p) {
		return ours(p.level());
	}

	public static boolean ours(Identifier id) {
		if (Config.OWN_WORLDS_ONLY <= 0) {
			return true;
		}
		String path = id.getPath();
		return MOD.equals(id.getNamespace())
				|| MINECRAFT.equals(id.getNamespace()) && (OVERWORLD.equals(path) || NETHER.equals(path) || END.equals(path));
	}

	private static boolean known(Identifier id) {
		return MINECRAFT.equals(id.getNamespace()) || MOD.equals(id.getNamespace());
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
