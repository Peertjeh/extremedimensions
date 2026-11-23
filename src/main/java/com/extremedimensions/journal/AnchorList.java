package com.extremedimensions.journal;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import com.extremedimensions.anchor.AnchorService;
import com.extremedimensions.data.Anchor;
import com.extremedimensions.data.DataStore;
import com.extremedimensions.data.PlayerData;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * The journal's anchors: every anchor that keeps charge for a player (their own, what allies opened to them, and what
 * an older world kept for everyone), where it stands, what it holds for them, whether it is cracked or blocked, and
 * whom it is shared with.
 */
public final class AnchorList {
	private AnchorList() {}

	/** At most this many anchors are written out (the rest only counted), so the journal still fits in one packet. */
	private static final int MAX = 100;
	/** Names written of a list, and how many more there are after them. */
	private static final int NAMES = 3;

	/**
	 * The anchors as the journal reads them: "open|allies|total|entry;entry;...". Open is 1 when the player's anchors are
	 * opened to their allies, and allies then names those who may draw on them; total counts every anchor, also those
	 * past {@link #MAX}. Each entry is "kind,x,y,z,charge,own,cracked,lenders,everyone,blocked": kind "nether" or "end",
	 * the charge it holds for the player and their own part of it in whole seconds, the seconds before a cracked anchor
	 * breaks (0 when steady), the allies whose charge in it is opened to the player, 1 where an older world's charge in
	 * it is kept for everyone, and (3.55.0, last so a journal of before reads the rest) 1 where its way to its portal is
	 * blocked and it leaks. Names are joined by "/", with "+n" for the n more not named.
	 */
	public static String encode(ServerPlayer p) {
		String uid = p.getStringUUID();
		MinecraftServer s = p.level().getServer();
		List<Anchor> kept = new ArrayList<>();
		for (Anchor a : DataStore.anchors().values()) {
			if (a.of(uid) >= 1) {
				kept.add(a);
			}
		}
		// The Nether's first, then the fullest, so the anchors that matter most are on the first spread
		kept.sort(Comparator.comparing((Anchor a) -> !a.isNether()).thenComparingDouble(a -> -a.of(uid)).thenComparing(a -> a.key()));
		StringBuilder out = new StringBuilder();
		for (int i = 0; i < Math.min(MAX, kept.size()); i++) {
			Anchor a = kept.get(i);
			if (out.length() > 0) {
				out.append(';');
			}
			out.append(AnchorService.side(a.type)).append(',').append(a.x).append(',').append(a.y).append(',').append(a.z).append(',')
					.append(Math.round(a.of(uid))).append(',').append(Math.round(a.own(uid))).append(',').append(Math.max(0, a.unstable)).append(',')
					.append(names(s, a.lenders(uid))).append(',').append(a.keptForAll() ? 1 : 0).append(',').append(a.blocked ? 1 : 0);
		}
		boolean open = Anchor.open.test(uid);
		return String.join("|", open ? "1" : "0", open ? names(s, allies(uid)) : "", String.valueOf(kept.size()), out.toString());
	}

	/** Every player the world knows who may draw on this player's charge once it is opened to them. */
	private static List<String> allies(String uid) {
		List<String> out = new ArrayList<>();
		for (String other : DataStore.playerIds()) {
			if (!other.equals(uid) && Anchor.allied.test(other, uid)) {
				out.add(other);
			}
		}
		return out;
	}

	/** The players by name (sorted, the unknown left out), the first few of them and how many more. */
	private static String names(MinecraftServer s, List<String> uids) {
		List<String> named = new ArrayList<>();
		for (String uid : uids) {
			String n = name(s, uid);
			if (n != null && !n.isEmpty()) {
				named.add(n);
			}
		}
		named.sort(String.CASE_INSENSITIVE_ORDER);
		List<String> out = new ArrayList<>(named.subList(0, Math.min(NAMES, named.size())));
		if (named.size() > NAMES) {
			out.add("+" + (named.size() - NAMES));
		}
		return String.join("/", out);
	}

	/** A player's name as they play now, or as they last played; without the marks the journal's data is split on. */
	private static String name(MinecraftServer s, String uid) {
		try {
			UUID id = UUID.fromString(uid);
			ServerPlayer o = s == null ? null : s.getPlayerList().getPlayer(id);
			String n = o != null ? o.getScoreboardName() : null;
			if (n == null) {
				PlayerData d = DataStore.peek(id);
				n = d == null ? null : d.name;
			}
			return n == null ? null : n.replaceAll("[,;|/+]", "");
		} catch (IllegalArgumentException e) {
			return null; // not a player's id
		}
	}
}
