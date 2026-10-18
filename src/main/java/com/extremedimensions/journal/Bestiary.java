package com.extremedimensions.journal;

import com.extremedimensions.data.DataStore;
import com.extremedimensions.data.PlayerData;
import com.extremedimensions.hollow.Hollowling;
import com.extremedimensions.hollow.Keeper;
import com.extremedimensions.lurker.Lurker;
import com.extremedimensions.mending.StarvedOne;
import com.extremedimensions.seam.Thinned;
import com.extremedimensions.seam.VeilRay;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;

/**
 * The bestiary at the back of the journal: every creature of the wound, and what the player knows of it. A creature
 * is written in once it has been seen (close, in plain sight); how to face it once enough of them have been slain (one
 * of the great ones, three of the rest). Great ones count for everyone who fought them, the rest for who struck last.
 */
public final class Bestiary {
	private Bestiary() {}

	public enum Beast {
		LURKER("lurker", 3), HOLLOWLING("hollowling", 3), KEEPER("keeper", 1), STARVED_ONE("starved_one", 1),
		THINNED("thinned", 3), VEIL_RAY("veil_ray", 3), FRAYED_RAY("frayed_ray", 3), MATRIARCH("matriarch", 1), FERRYMAN("ferryman", 1), LAST_WEAVER("weaver", 1);

		public final String id;
		/** Slain this many times, the journal notes how to face it. */
		public final int notesAt;

		Beast(String id, int notesAt) {
			this.id = id;
			this.notesAt = notesAt;
		}

		public boolean great() {
			return this == STARVED_ONE || this == MATRIARCH || this == FERRYMAN || this == LAST_WEAVER;
		}

		public String nameKey() {
			return "entity.extremedimensions." + id;
		}

		public String loreKey() {
			return "gui.extremedimensions.bestiary." + id + ".lore";
		}

		public String notesKey() {
			return "gui.extremedimensions.bestiary." + id + ".notes";
		}

		public static Beast byId(String id) {
			for (Beast b : values()) {
				if (b.id.equals(id)) {
					return b;
				}
			}
			return null;
		}
	}

	/** Which creature of the bestiary this is (the Starved One and the Keeper are kinds of Lurker), or null. */
	public static Beast of(Entity e) {
		if (e instanceof StarvedOne) {
			return Beast.STARVED_ONE;
		}
		if (e instanceof Keeper) {
			return Beast.KEEPER;
		}
		if (e instanceof Lurker) {
			return Beast.LURKER;
		}
		if (e instanceof Hollowling) {
			return Beast.HOLLOWLING;
		}
		if (e instanceof Thinned) {
			return Beast.THINNED;
		}
		if (e instanceof VeilRay ray) {
			return ray.isFrayed() ? Beast.FRAYED_RAY : Beast.VEIL_RAY;
		}
		if (e instanceof com.extremedimensions.elder.Matriarch) {
			return Beast.MATRIARCH;
		}
		if (e instanceof com.extremedimensions.elder.Ferryman) {
			return Beast.FERRYMAN;
		}
		if (e instanceof com.extremedimensions.elder.Weaver) {
			return Beast.LAST_WEAVER;
		}
		return null;
	}

	/** Written into the journal: the player has seen it. */
	public static void seen(ServerPlayer p, Beast b) {
		PlayerData d = DataStore.get(p.getUUID());
		if (d.seen.add(b.id)) {
			// a toast, as the game shows such news
			com.extremedimensions.server.WorldFx.fx(p, com.extremedimensions.network.Fx.BESTIARY, b.ordinal());
			DataStore.markDirty();
		}
	}

	/** Now and then: every creature of the bestiary close by and in plain sight is seen. */
	public static void scan(ServerPlayer p) {
		if (p.isSpectator()) {
			return;
		}
		for (Mob m : p.level().getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(24), m -> m.isAlive() && of(m) != null)) {
			Beast b = of(m);
			if (!DataStore.get(p.getUUID()).seen.contains(b.id) && p.hasLineOfSight(m)) {
				seen(p, b);
			}
		}
	}

	/** A creature of the bestiary died: counted for who slew it (a great one for everyone near). */
	public static void slain(Entity dead, Entity killer) {
		Beast b = of(dead);
		if (b == null || !(dead.level() instanceof ServerLevel level)) {
			return;
		}
		if (b.great()) {
			for (ServerPlayer p : level.players()) {
				if (!p.isSpectator() && p.distanceToSqr(dead) < 64 * 64) {
					credit(p, b);
				}
			}
		} else if (killer instanceof ServerPlayer p) {
			credit(p, b);
		}
	}

	private static void credit(ServerPlayer p, Beast b) {
		PlayerData d = DataStore.get(p.getUUID());
		seen(p, b);
		int n = d.kills.merge(b.id, 1, Integer::sum);
		if (n == b.notesAt) {
			p.sendSystemMessage(Component.translatable("gui.extremedimensions.bestiary.noted", Component.translatable(b.nameKey()))
					.withStyle(ChatFormatting.DARK_AQUA));
		}
		DataStore.markDirty();
	}

	/** The bestiary as the journal reads it: "id:seen:slain;..." in the bestiary's order. */
	public static String encode(PlayerData d) {
		StringBuilder out = new StringBuilder();
		for (Beast b : Beast.values()) {
			if (out.length() > 0) {
				out.append(';');
			}
			out.append(b.id).append(':').append(d.seen.contains(b.id) ? 1 : 0).append(':').append(d.kills.getOrDefault(b.id, 0));
		}
		return out.toString();
	}
}
