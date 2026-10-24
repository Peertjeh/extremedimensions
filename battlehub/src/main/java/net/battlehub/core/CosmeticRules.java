package net.battlehub.core;

import java.util.List;

/** Buying and choosing cosmetics. One cosmetic per category is active at a time. */
public final class CosmeticRules {
	private CosmeticRules() {}

	public static final List<String> CATEGORIES = List.of("trail", "aura", "arrow", "kill", "killmsg", "win", "cry", "skin",
			"pet", "hat", "topper", "balloon", "outfit", "color", "badge", "gadget");
	/** Categories worn in the lobby (the wardrobe). */
	public static final List<String> WARDROBE = List.of("hat", "outfit");

	public enum BuyResult { BOUGHT, ALREADY_OWNED, TOO_EXPENSIVE }

	public static boolean owns(PlayerProfile p, CosmeticDef c) {
		return c.price <= 0 || p.ownedCosmetics.contains(c.id);
	}

	public static BuyResult buy(PlayerProfile p, CosmeticDef c) {
		if (owns(p, c)) {
			return BuyResult.ALREADY_OWNED;
		}
		if (p.coins < c.price) {
			return BuyResult.TOO_EXPENSIVE;
		}
		p.coins -= c.price;
		p.ownedCosmetics.add(c.id);
		return BuyResult.BOUGHT;
	}

	/** Makes an owned cosmetic the active one of its category. */
	public static boolean select(PlayerProfile p, CosmeticDef c) {
		if (!owns(p, c)) {
			return false;
		}
		p.cosmetics.put(c.category(), c.id);
		return true;
	}

	public static void clear(PlayerProfile p, String category) {
		p.cosmetics.remove(category);
	}

	/** The active cosmetic of a category, or null (also when it no longer exists or is not owned). */
	public static CosmeticDef active(PlayerProfile p, String category, List<CosmeticDef> all) {
		String id = p.cosmetics.get(category);
		if (id == null) {
			return null;
		}
		for (CosmeticDef c : all) {
			if (c.id.equals(id) && c.category().equals(category) && owns(p, c)) {
				return c;
			}
		}
		return null;
	}

	/**
	 * The colours of an outfit's chestplate, leggings and boots: one colour for all ("#RRGGBB"), a fade from top
	 * to bottom ("#RRGGBB>#RRGGBB") or one each ("#RRGGBB,#RRGGBB,#RRGGBB"); null when it is none of these.
	 */
	public static int[] outfitColors(String text) {
		if (text == null) return null;
		String t = text.trim();
		if (t.contains(",")) {
			String[] parts = t.split(",");
			if (parts.length != 3) return null;
			int[] out = new int[3];
			for (int i = 0; i < 3; i++) {
				out[i] = rgb(parts[i]);
				if (out[i] < 0) return null;
			}
			return out;
		}
		if (t.contains(">")) {
			String[] parts = t.split(">");
			if (parts.length != 2) return null;
			int a = rgb(parts[0]);
			int b = rgb(parts[1]);
			if (a < 0 || b < 0) return null;
			return new int[] {a, mix(a, b, 0.5), b};
		}
		int c = rgb(t);
		return c < 0 ? null : new int[] {c, c, c};
	}

	/** Two colours mixed: {@code f} = 0 is the first, 1 the second. */
	public static int mix(int a, int b, double f) {
		int r = (int) Math.round(((a >> 16) & 255) * (1 - f) + ((b >> 16) & 255) * f);
		int g = (int) Math.round(((a >> 8) & 255) * (1 - f) + ((b >> 8) & 255) * f);
		int bl = (int) Math.round((a & 255) * (1 - f) + (b & 255) * f);
		return (r << 16) | (g << 8) | bl;
	}

	/** A weapon skin is "model" or "model;glint": the item whose look the sword takes. */
	public static String skinModel(String effect) {
		if (effect == null) return "";
		int cut = effect.indexOf(';');
		String m = (cut < 0 ? effect : effect.substring(0, cut)).trim();
		return m.isEmpty() || m.contains(":") ? m : "minecraft:" + m;
	}

	public static boolean skinGlint(String effect) {
		return effect != null && effect.toLowerCase(java.util.Locale.ROOT).contains(";glint");
	}

	/** A death cry is "sound" or "sound;pitch". */
	public static String crySound(String effect) {
		if (effect == null) return "";
		int cut = effect.indexOf(';');
		String m = (cut < 0 ? effect : effect.substring(0, cut)).trim();
		return m.isEmpty() || m.contains(":") ? m : "minecraft:" + m;
	}

	public static float cryPitch(String effect) {
		if (effect == null || effect.indexOf(';') < 0) return 1f;
		try {
			float f = Float.parseFloat(effect.substring(effect.indexOf(';') + 1).trim());
			return Math.max(0.5f, Math.min(2f, f));
		} catch (NumberFormatException e) {
			return 1f;
		}
	}

	/** A colour for an outfit: "#RRGGBB" or "RRGGBB"; -1 when it is not one. */
	public static int rgb(String text) {
		if (text == null) return -1;
		String t = text.trim();
		if (t.startsWith("#")) t = t.substring(1);
		if (t.length() != 6) return -1;
		try {
			int v = Integer.parseInt(t, 16);
			return v < 0 || t.startsWith("+") ? -1 : v;
		} catch (NumberFormatException e) {
			return -1;
		}
	}
}
