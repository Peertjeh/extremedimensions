package net.battlehub.core;

import java.util.Locale;

/**
 * Markers in custom maps: structure blocks in Data mode whose text says what the spot is. {@code spawn} (or
 * {@code spawn:90} to face west), {@code spectator}, and any other name for game spots ({@code goal_red},
 * {@code chest}, {@code gold}, {@code zone}, ...). Text is case-insensitive; spaces become underscores.
 */
public final class MapMarkers {
	private MapMarkers() {}

	public record Marker(String name, float yaw) {}

	/** The marker for a structure block's text, or null when the text is empty. */
	public static Marker parse(String text) {
		if (text == null) {
			return null;
		}
		String t = text.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
		if (t.isEmpty()) {
			return null;
		}
		float yaw = 0;
		int colon = t.indexOf(':');
		if (colon >= 0) {
			try {
				yaw = Float.parseFloat(t.substring(colon + 1));
			} catch (NumberFormatException e) {
				yaw = 0;
			}
			t = t.substring(0, colon);
		}
		return t.isEmpty() ? null : new Marker(t, yaw);
	}

	/** A file name to a map id: lower case, letters, digits and underscores. */
	public static String id(String fileName) {
		String n = fileName.toLowerCase(Locale.ROOT);
		if (n.endsWith(".nbt")) n = n.substring(0, n.length() - 4);
		return n.replaceAll("[^a-z0-9_]", "_");
	}
}
