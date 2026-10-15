package net.battlehub.fair;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.battlehub.BattleHub;
import net.battlehub.bot.Bots;
import net.battlehub.config.ConfigManager;
import net.battlehub.config.HubConfig;
import net.battlehub.core.ClickRate;
import net.battlehub.lang.Lang;
import net.battlehub.session.Sessions;
import net.battlehub.util.Msg;
import net.minecraft.commands.Commands;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Light checks in matches that warn admins (never punish by themselves): hits from too far away, clicking
 * faster than a hand can, hovering in the air and moving faster than possible. Every alert is logged; online
 * admins get a chat line, at most every half minute per player and check.
 */
public final class AntiCheat {
	private AntiCheat() {}

	private static final class Track {
		final ClickRate clicks = new ClickRate(20);
		int fastSeconds;
		int airTicks;
		double airMinY = Double.MAX_VALUE;
		double airMaxY = -Double.MAX_VALUE;
		Vec3 last;
		double moved;
		int movedTicks;
		int speedSeconds;
		int reachHits;
		long reachWindow;
	}

	private static final Map<UUID, Track> TRACKS = new HashMap<>();
	private static final Map<String, Long> LAST_ALERT = new HashMap<>();
	private static long ticks;

	private static HubConfig.Fairness cfg() {
		return ConfigManager.config().fairness;
	}

	private static boolean watched(ServerPlayer p) {
		return cfg().antiCheat && !Bots.isBot(p) && Sessions.state(p.getUUID()) == Sessions.State.MATCH
				&& !p.isSpectator() && !p.isCreative();
	}

	private static Track track(ServerPlayer p) {
		return TRACKS.computeIfAbsent(p.getUUID(), k -> new Track());
	}

	public static void swing(ServerPlayer p) {
		if (watched(p)) {
			track(p).clicks.click(ticks);
		}
	}

	/** A melee hit: how far was the target from the attacker's eyes? */
	public static void hit(ServerPlayer attacker, ServerPlayer victim) {
		if (!watched(attacker)) {
			return;
		}
		AABB box = victim.getBoundingBox();
		Vec3 eye = attacker.getEyePosition();
		double dx = Math.max(box.minX - eye.x, Math.max(0, eye.x - box.maxX));
		double dy = Math.max(box.minY - eye.y, Math.max(0, eye.y - box.maxY));
		double dz = Math.max(box.minZ - eye.z, Math.max(0, eye.z - box.maxZ));
		double reach = Math.sqrt(dx * dx + dy * dy + dz * dz);
		if (reach <= cfg().maxReach) {
			return;
		}
		Track t = track(attacker);
		if (ticks - t.reachWindow > 200) {
			t.reachWindow = ticks;
			t.reachHits = 0;
		}
		if (++t.reachHits >= 2) {
			alert(attacker, "reach", String.format(java.util.Locale.ROOT, "%.2f", reach));
		}
	}

	public static void tick(MinecraftServer server) {
		ticks++;
		if (!cfg().antiCheat) {
			return;
		}
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			if (!watched(p)) {
				TRACKS.remove(p.getUUID());
				continue;
			}
			Track t = track(p);
			Vec3 pos = p.position();
			// Hovering: long in the air without going down (not swimming, climbing, gliding or levitating)
			boolean free = !p.onGround() && !p.isInWater() && !p.onClimbable() && !p.isFallFlying()
					&& !p.hasEffect(MobEffects.LEVITATION) && !p.hasEffect(MobEffects.SLOW_FALLING) && !p.isPassenger();
			if (free) {
				t.airTicks++;
				t.airMinY = Math.min(t.airMinY, pos.y);
				t.airMaxY = Math.max(t.airMaxY, pos.y);
				if (t.airTicks >= cfg().hoverTicks && t.airMaxY - t.airMinY < 1.5) {
					alert(p, "hover", t.airTicks / 20 + "s");
					t.airTicks = 0;
					t.airMinY = Double.MAX_VALUE;
					t.airMaxY = -Double.MAX_VALUE;
				}
			} else {
				t.airTicks = 0;
				t.airMinY = Double.MAX_VALUE;
				t.airMaxY = -Double.MAX_VALUE;
			}
			// Speed: blocks per second on the ground, not right after being hit (knockback) or teleported
			if (t.last != null && p.level() == p.level() && p.hurtTime == 0 && pos.distanceTo(t.last) < 8) {
				t.moved += Math.hypot(pos.x - t.last.x, pos.z - t.last.z);
				t.movedTicks++;
			}
			t.last = pos;
			if (ticks % 20 == 0) {
				double cps = t.clicks.perSecond(ticks);
				t.fastSeconds = cps > cfg().maxCps ? t.fastSeconds + 1 : 0;
				if (t.fastSeconds >= 3) {
					alert(p, "cps", String.format(java.util.Locale.ROOT, "%.0f", cps));
					t.fastSeconds = 0;
				}
				double speed = t.movedTicks == 0 ? 0 : t.moved * 20 / t.movedTicks;
				boolean boosted = p.hasEffect(MobEffects.SPEED) || p.isFallFlying();
				t.speedSeconds = !boosted && speed > cfg().maxSpeed ? t.speedSeconds + 1 : 0;
				if (t.speedSeconds >= 2) {
					alert(p, "speed", String.format(java.util.Locale.ROOT, "%.1f", speed));
					t.speedSeconds = 0;
				}
				t.moved = 0;
				t.movedTicks = 0;
			}
		}
	}

	private static void alert(ServerPlayer p, String check, String detail) {
		String key = p.getUUID() + ":" + check;
		Long last = LAST_ALERT.get(key);
		if (last != null && ticks - last < 600) {
			return;
		}
		LAST_ALERT.put(key, ticks);
		String name = p.getGameProfile().name();
		BattleHub.LOGGER.warn("[BattleHub anti-cheat] {}: {} ({})", name, check, detail);
		MinecraftServer server = p.level().getServer();
		for (ServerPlayer admin : server.getPlayerList().getPlayers()) {
			if (Commands.hasPermission(Commands.LEVEL_GAMEMASTERS).test(admin.createCommandSourceStack())) {
				Msg.raw(admin, Lang.tr(admin, "fair.alert", name, Lang.tr(admin, "fair.check." + check), detail));
			}
		}
	}

	public static void forget(UUID id) {
		TRACKS.remove(id);
	}

	public static void clear() {
		TRACKS.clear();
		LAST_ALERT.clear();
	}
}
