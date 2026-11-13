package com.extremedimensions.compat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import com.extremedimensions.data.Anchor;
import com.extremedimensions.data.DataStore;
import com.extremedimensions.data.PlayerData;
import com.extremedimensions.registry.ModBlocks;
import com.extremedimensions.server.Dims;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What an anchor tells a player who looks at it through Jade or WTHIT: the charge it holds for them, whether it is
 * cracked or blocked, whose charge it keeps and who opened theirs to them. Only the server knows the anchors, so it is worked out
 * here and handed to the tooltip mod as a few plain values, the same for both (the client's LookLines puts them into
 * words). Names go as they are; the words around them are the client's, in the player's own language.
 */
public final class AnchorLook {
	private AnchorLook() {}

	/** Seconds of charge it holds for the one looking (their own, and what allies opened to them). */
	public static final String YOURS = "extremedimensions_yours";
	/** Seconds before it breaks, while cracked (0: steady). */
	public static final String CRACKED = "extremedimensions_cracked";
	/** 1 when its way to its portal is blocked, so that it leaks (3.55.0). */
	public static final String BLOCKED = "extremedimensions_blocked";
	/** 1 when the one looking keeps charge of their own in it. */
	public static final String MINE = "extremedimensions_mine";
	/** The names of the others who keep charge in it, one per line ("" for one whose name is not known). */
	public static final String OWNERS = "extremedimensions_owners";
	/** How many more keep charge in it than are named. */
	public static final String MORE = "extremedimensions_more";
	/** 1 when it holds charge from before charge was kept per player, which anyone may use. */
	public static final String EVERYONE = "extremedimensions_everyone";
	/** The names of those whose charge in it is opened to the one looking, one per line. */
	public static final String LENDERS = "extremedimensions_lenders";

	/** How many of the others are named before the rest are only counted. */
	private static final int NAMED = 4;

	public static boolean isAnchor(BlockState s) {
		return s.is(ModBlocks.EMBER_ANCHOR) || s.is(ModBlocks.VOID_ANCHOR);
	}

	/** Writes what the anchor at {@code pos} tells {@code p} into {@code tag}; nothing when there is no anchor there. */
	public static void write(CompoundTag tag, ServerPlayer p, BlockPos pos) {
		// (anchors are kept in the Overworld: a lodestone elsewhere at the same spot is no anchor)
		if (!Dims.OVERWORLD.equals(Dims.of(p))) {
			return;
		}
		Anchor a = DataStore.anchors().get(Anchor.key(pos.getX(), pos.getY(), pos.getZ()));
		if (a == null) {
			return;
		}
		String uid = p.getStringUUID();
		MinecraftServer s = p.level().getServer();
		tag.putInt(YOURS, (int) Math.round(a.of(uid)));
		tag.putInt(CRACKED, Math.max(0, a.unstable));
		tag.putInt(BLOCKED, a.blocked ? 1 : 0);
		List<String> owners = new ArrayList<>();
		List<String> lenders = new ArrayList<>();
		int more = 0;
		// (in the order of their ids, so the names keep their places from one look to the next)
		for (Map.Entry<String, Double> e : new TreeMap<>(a.owners).entrySet()) {
			if (e.getValue() < 1) {
				continue;
			}
			String owner = e.getKey();
			if (owner.equals(uid)) {
				tag.putInt(MINE, 1);
				continue;
			}
			if ("*".equals(owner)) {
				tag.putInt(EVERYONE, 1);
				continue;
			}
			String name = ownerName(s, owner);
			if (owners.size() < NAMED) {
				owners.add(name == null ? "" : name);
			} else {
				more++;
			}
			if (Anchor.lends(owner, uid) && lenders.size() < NAMED) {
				lenders.add(name == null ? "" : name);
			}
		}
		tag.putString(OWNERS, String.join("\n", owners));
		tag.putInt(MORE, more);
		tag.putString(LENDERS, String.join("\n", lenders));
	}

	/** A player's name by their id, also while they are away; null when it is not known (or it is no player's id). */
	public static String ownerName(MinecraftServer s, String uid) {
		try {
			UUID id = UUID.fromString(uid);
			ServerPlayer q = s == null ? null : s.getPlayerList().getPlayer(id);
			if (q != null) {
				return q.getName().getString();
			}
			PlayerData d = DataStore.peek(id);
			if (d != null && d.name != null) {
				return d.name;
			}
		} catch (IllegalArgumentException e) {
			// not a player's id
		}
		return null;
	}
}
