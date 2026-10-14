package net.battlehub.core.bedwars;

import java.util.Locale;

/** The currencies of Bed Wars, dropped by generators and spent in the shop. */
public enum Resource {
	IRON("minecraft:iron_ingot", "&f"),
	GOLD("minecraft:gold_ingot", "&6"),
	DIAMOND("minecraft:diamond", "&b"),
	EMERALD("minecraft:emerald", "&a");

	public final String item;
	public final String color;

	Resource(String item, String color) {
		this.item = item;
		this.color = color;
	}

	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	public static Resource byId(String id) {
		for (Resource r : values()) {
			if (r.id().equalsIgnoreCase(id == null ? "" : id.trim())) return r;
		}
		return null;
	}

	public static Resource byItem(String item) {
		for (Resource r : values()) {
			if (r.item.equals(item)) return r;
		}
		return null;
	}
}
