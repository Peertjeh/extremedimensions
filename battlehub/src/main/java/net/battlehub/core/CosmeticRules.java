package net.battlehub.core;

import java.util.List;

/** Buying and choosing cosmetics. One cosmetic per category is active at a time. */
public final class CosmeticRules {
	private CosmeticRules() {}

	public static final List<String> CATEGORIES = List.of("trail", "kill", "arrow", "win", "color");

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
}
