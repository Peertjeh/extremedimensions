package net.battlehub.arch.mat;

import java.util.List;
import java.util.Set;

import net.battlehub.arch.Shape;

/**
 * A family of blocks that look alike: the full block and, where the game has them, its stairs, slab, wall, fence,
 * pane, pillar, trapdoor and door. Carries what the generator needs to know to use it well: an average colour
 * (from the texture), semantic tags and its aged variants (mossy, cracked, oxidised).
 *
 * @param alt the family whose forms stand in for forms this one does not have (calcite has no stairs)
 */
public record Material(String name, int rgb, Set<String> tags, String cube, String stairs, String slab, String wall,
		String fence, String pane, String pillar, String trapdoor, String door, List<String> aged, String alt) {

	/** The block for a form, or null when this family has none. */
	public String id(Shape shape) {
		return switch (shape) {
			case CUBE -> cube;
			case STAIRS -> stairs;
			case SLAB -> slab;
			case WALL -> wall;
			case FENCE -> fence;
			case PANE -> pane;
			case LOG -> pillar != null ? pillar : cube;
			case TRAPDOOR -> trapdoor;
			case DOOR -> door;
			case LITERAL -> null;
		};
	}

	public boolean has(Shape shape) {
		return id(shape) != null;
	}

	public boolean is(String tag) {
		return tags.contains(tag);
	}

	/** Perceived brightness 0..1 (Rec. 709 luma of the average colour). */
	public double value() {
		return luma(rgb);
	}

	public static double luma(int rgb) {
		int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
		return (0.2126 * r + 0.7152 * g + 0.0722 * b) / 255.0;
	}

	/** Colour distance (0 = same, about 1 = black against white), weighted for the eye. */
	public static double distance(int a, int b) {
		double rm = (((a >> 16) & 255) + ((b >> 16) & 255)) / 2.0;
		double dr = ((a >> 16) & 255) - ((b >> 16) & 255);
		double dg = ((a >> 8) & 255) - ((b >> 8) & 255);
		double db = (a & 255) - (b & 255);
		return Math.sqrt((2 + rm / 256) * dr * dr + 4 * dg * dg + (2 + (255 - rm) / 256) * db * db) / 765.0;
	}
}
