package com.extremedimensions.elder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;

/**
 * An Elder's wind-up before it rises (3.38, after the Matriarch's of 3.28): a bar counts down for everyone near, the
 * place groans louder as the time runs out, and whoever it would rise for can make ready or go. When the time is up it
 * rises for the one who woke it, if they are still near, else for the nearest who may keep it awake; with all of them
 * gone far enough it settles again. The Ferryman's wind-up follows the one he comes for wherever they go, and settles
 * only once they leave his waters.
 *
 * <p>Each Elder has its own words, under {@code gui.extremedimensions.stir.<name>}: the bar's title, {@code .chat} as it
 * begins (its seconds and the distance to go), {@code .bar} each second for those near (seconds left, the distance) and
 * {@code .settles} when it settles.
 */
public final class ElderStir {
	private ElderStir() {}

	/** Players within this many blocks of the place see the bar and hear it begin. */
	static final double HEARD = 64;

	/** Once a second while it stirs: the place's own groaning (share: how much of the time has gone, 0 to 1). */
	public interface Pulse {
		void at(ServerLevel level, double x, double y, double z, float share);
	}

	/** One wind-up: where, who woke it, how long, who may keep it awake, and what rises at the end. */
	public static final class Stir {
		final String key, name;
		final ServerLevel level;
		double x, y, z;
		final UUID waker;
		final int total;
		int left;
		final double calm;
		final Predicate<ServerPlayer> keeps;
		final Consumer<ServerPlayer> rise;
		final ServerBossEvent bar;
		boolean follows;
		ChatFormatting tint = ChatFormatting.LIGHT_PURPLE;
		Runnable settled = () -> {};
		Pulse pulse = (l, px, py, pz, share) -> {};

		/**
		 * @param key one of a kind: a second wind-up under the same key is refused
		 * @param name the Elder's name in its words ({@code gui.extremedimensions.stir.<name>})
		 * @param calm how far (blocks) from the place everyone must go for it to settle
		 * @param keeps who, besides being near, may keep it awake (and be risen for)
		 * @param rise what rises at the end, for whom
		 */
		public Stir(String key, String name, ServerLevel level, double x, double y, double z, ServerPlayer waker, int seconds, double calm,
				BossEvent.BossBarColor colour, Predicate<ServerPlayer> keeps, Consumer<ServerPlayer> rise) {
			this.key = key;
			this.name = name;
			this.level = level;
			this.x = x;
			this.y = y;
			this.z = z;
			this.waker = waker.getUUID();
			this.total = Math.max(1, seconds) * 20;
			this.left = total;
			this.calm = calm;
			this.keeps = keeps;
			this.rise = rise;
			this.bar = new ServerBossEvent(UUID.randomUUID(), Component.translatable("gui.extremedimensions.stir." + name), colour,
					BossEvent.BossBarOverlay.PROGRESS);
		}

		/** The place goes with the one who woke it (the Ferryman sails after his passenger). */
		public Stir follows() {
			this.follows = true;
			return this;
		}

		public Stir tint(ChatFormatting tint) {
			this.tint = tint;
			return this;
		}

		/** What happens when it settles (after the words are said). */
		public Stir settled(Runnable settled) {
			this.settled = settled;
			return this;
		}

		public Stir pulse(Pulse pulse) {
			this.pulse = pulse;
			return this;
		}

		int seconds() {
			return total / 20;
		}
	}

	private static final Map<String, Stir> STIRS = new HashMap<>();

	/** Begins a wind-up and tells everyone near; false if one runs under its key already. */
	public static boolean begin(Stir st) {
		if (STIRS.containsKey(st.key)) {
			return false;
		}
		STIRS.put(st.key, st);
		for (ServerPlayer q : st.level.players()) {
			if (!q.isSpectator() && hears(st, q)) {
				q.sendSystemMessage(Component.translatable("gui.extremedimensions.stir." + st.name + ".chat", st.seconds(), (int) st.calm).withStyle(st.tint));
				st.bar.addPlayer(q);
			}
		}
		st.pulse.at(st.level, st.x, st.y, st.z, 0f);
		return true;
	}

	/** Whether a wind-up runs under this key. */
	public static boolean stirring(String key) {
		return STIRS.containsKey(key);
	}

	/** Whether any wind-up runs for an Elder of this name. */
	public static boolean stirringAny(String name) {
		for (Stir st : STIRS.values()) {
			if (st.name.equals(name)) {
				return true;
			}
		}
		return false;
	}

	public static void clear() {
		for (Stir st : STIRS.values()) {
			st.bar.removeAllPlayers();
		}
		STIRS.clear();
	}

	private static boolean hears(Stir st, ServerPlayer q) {
		return st.follows ? q.getUUID().equals(st.waker) || q.distanceToSqr(st.x, st.y, st.z) < HEARD * HEARD
				: q.distanceToSqr(st.x, st.y, st.z) < HEARD * HEARD;
	}

	private static boolean near(Stir st, ServerPlayer q) {
		return st.follows || q.distanceToSqr(st.x, st.y, st.z) < st.calm * st.calm;
	}

	/** Who it rises for: the one who woke it if still near, else the nearest near who may keep it awake; null = none. */
	private static ServerPlayer target(Stir st) {
		ServerPlayer found = null;
		double best = Double.MAX_VALUE;
		for (ServerPlayer q : st.level.players()) {
			if (q.isSpectator() || q.isCreative() || !q.isAlive() || !near(st, q) || !st.keeps.test(q)) {
				continue;
			}
			if (q.getUUID().equals(st.waker)) {
				return q;
			}
			double d2 = q.distanceToSqr(st.x, st.y, st.z);
			if (d2 < best) {
				best = d2;
				found = q;
			}
		}
		return found;
	}

	/** Counts every wind-up down: the bar, the words each second, the groaning; it rises at the end, or settles. */
	public static void tick(MinecraftServer s) {
		if (STIRS.isEmpty()) {
			return;
		}
		// (what rises or settles is done after the count, so that it may begin another wind-up)
		List<Runnable> after = new ArrayList<>();
		for (Iterator<Stir> it = STIRS.values().iterator(); it.hasNext();) {
			Stir st = it.next();
			st.left--;
			if (st.follows) {
				ServerPlayer w = s.getPlayerList().getPlayer(st.waker);
				if (w != null && w.level() == st.level) {
					st.x = w.getX();
					st.y = w.getY();
					st.z = w.getZ();
				}
			}
			ServerPlayer target = target(st);
			if (target == null) {
				// Everyone it could rise for went far enough: it falls still
				it.remove();
				for (ServerPlayer q : st.bar.getPlayers()) {
					if (q.hasDisconnected()) {
						continue;
					}
					q.sendSystemMessage(Component.translatable("gui.extremedimensions.stir." + st.name + ".settles").withStyle(ChatFormatting.GRAY));
				}
				st.bar.removeAllPlayers();
				after.add(st.settled);
				continue;
			}
			if (st.left <= 0) {
				it.remove();
				st.bar.removeAllPlayers();
				after.add(() -> st.rise.accept(target));
				continue;
			}
			st.bar.setProgress(Math.max(0f, st.left / (float) st.total));
			if (st.left % 20 == 0) {
				int secs = (st.left + 19) / 20;
				for (ServerPlayer q : st.level.players()) {
					boolean hears = !q.isSpectator() && hears(st, q);
					if (hears && !st.bar.getPlayers().contains(q)) {
						st.bar.addPlayer(q);
					} else if (!hears && st.bar.getPlayers().contains(q)) {
						st.bar.removePlayer(q);
					}
					if (hears && near(st, q) && (!st.follows || q.getUUID().equals(st.waker))) {
						q.sendSystemMessage(Component.translatable("gui.extremedimensions.stir." + st.name + ".bar", secs, (int) st.calm).withStyle(st.tint), true);
					}
				}
				st.pulse.at(st.level, st.x, st.y, st.z, 1f - st.left / (float) st.total);
			}
		}
		// (players who left the level are dropped from its bar too)
		for (Stir st : STIRS.values()) {
			for (ServerPlayer q : new ArrayList<>(st.bar.getPlayers())) {
				if (q.level() != st.level) {
					st.bar.removePlayer(q);
				}
			}
		}
		after.forEach(Runnable::run);
	}
}
