package net.battlehub.core.bedwars;

import java.util.Map;

/**
 * Buying in the Bed Wars shop. A purchase is all or nothing: it checks the price, the tier and the space first,
 * then takes the exact price and only then hands out the goods, so double clicks, full inventories or a price
 * that cannot be paid never create or destroy anything.
 */
public final class Shop {
	private Shop() {}

	/**
	 * One thing for sale. {@code group}/{@code tier}: upgrades such as armour or pickaxes, where you can only buy a
	 * higher tier than you own; empty group for plain goods.
	 */
	public record Offer(String id, String category, String item, int count, Resource currency, int price, String group, int tier) {
		public boolean isUpgrade() {
			return group != null && !group.isEmpty();
		}
	}

	/** Where the money and the goods are (the player's inventory in the game; a fake one in tests). */
	public interface Wallet {
		int count(Resource currency);

		/** Takes exactly {@code amount}, or nothing at all (false). */
		boolean take(Resource currency, int amount);

		/** Is there room for these goods (always true for upgrades that replace something)? */
		boolean canFit(Offer offer);

		void give(Offer offer);
	}

	public enum Result { BOUGHT, TOO_EXPENSIVE, ALREADY_OWNED, NO_SPACE }

	public static Result check(Offer o, Wallet w, Map<String, Integer> ownedTiers) {
		if (o.isUpgrade() && ownedTiers.getOrDefault(o.group(), 0) >= o.tier()) {
			return Result.ALREADY_OWNED;
		}
		if (w.count(o.currency()) < o.price()) {
			return Result.TOO_EXPENSIVE;
		}
		if (!w.canFit(o)) {
			return Result.NO_SPACE;
		}
		return Result.BOUGHT;
	}

	public static Result buy(Offer o, Wallet w, Map<String, Integer> ownedTiers) {
		Result r = check(o, w, ownedTiers);
		if (r != Result.BOUGHT) {
			return r;
		}
		if (!w.take(o.currency(), o.price())) {
			return Result.TOO_EXPENSIVE;
		}
		w.give(o);
		if (o.isUpgrade()) {
			ownedTiers.put(o.group(), o.tier());
		}
		return Result.BOUGHT;
	}
}
