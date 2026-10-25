package net.battlehub.games.hunger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.battlehub.core.hunger.Escalation;
import net.battlehub.core.hunger.HgSchedule;
import net.battlehub.fx.Music;
import net.battlehub.lang.Lang;
import net.battlehub.ui.MatchBossBar;
import net.battlehub.util.Ids;
import net.battlehub.util.Msg;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The Gamemakers of one Hunger Games match: they follow their plan ({@link HgSchedule}) and send the Feast and the
 * arena events. An event is impossible to miss (1.47): an alarm (BattleHub's own music), a big title, a boss bar that
 * counts down while it lasts, and where the danger is (direction and distance) for every tribute. Every eight minutes
 * the stakes rise a level ({@link Escalation}): bigger, faster, more of them, more damage. Every event cleans up after
 * itself (mutts, bees and rubble gone, rain stopped) and none breaks the city. A tribute alive when an event ends wins
 * the sponsors' favour. The places an event makes dangerous are known ({@link #danger}), so bots run from them.
 */
public final class Gamemakers {
	private final HungerHost host;
	private final Capitol capitol;
	private final List<HgSchedule.Entry> plan;
	private int next;
	private final List<Event> running = new ArrayList<>();
	private final List<Cloud> clouds = new ArrayList<>();
	private final List<Swarm> swarms = new ArrayList<>();
	private final List<String> started = new ArrayList<>();
	private final MatchBossBar bar = new MatchBossBar(BossEvent.BossBarColor.RED);
	private boolean feastHeld;
	private int impacts, shocks;

	Gamemakers(HungerHost host, Capitol capitol, List<HgSchedule.Entry> plan) {
		this.host = host;
		this.capitol = capitol;
		this.plan = new ArrayList<>(plan);
	}

	/** The events started so far, in order (self-test, inspect). */
	public List<String> started() {
		return started;
	}

	/** The ids of the events running now. */
	public List<String> running() {
		List<String> out = new ArrayList<>();
		for (Event e : running) out.add(e.id);
		return out;
	}

	public List<HgSchedule.Entry> plan() {
		return plan;
	}

	public boolean feastHeld() {
		return feastHeld;
	}

	/** The level of danger now: 1, 2 or 3. */
	public int tier() {
		return HgSchedule.tier(host.runTicks());
	}

	// ------------------------------------------------------------------ the clock

	void tick() {
		int t = host.runTicks();
		while (next < plan.size()) {
			HgSchedule.Entry e = plan.get(next);
			if (e.id().equals(HgSchedule.FEAST) && t == e.tick() - HgSchedule.FEAST_WARNING_TICKS) announceFeast();
			if (t < e.tick()) break;
			start(e.id());
			next++;
		}
		for (Iterator<Event> it = running.iterator(); it.hasNext();) {
			Event e = it.next();
			int age = t - e.since;
			if (age >= e.length) {
				e.end();
				it.remove();
				for (ServerPlayer p : host.tributes()) capitol.sponsors().favour(p.getUUID(), net.battlehub.core.hunger.Favour.EVENT_SURVIVED);
				continue;
			}
			e.tick(age);
		}
		for (Iterator<Cloud> it = clouds.iterator(); it.hasNext();) {
			if (!it.next().tick(t)) it.remove();
		}
		for (Iterator<Swarm> it = swarms.iterator(); it.hasNext();) {
			Swarm s = it.next();
			if (!s.tick(t)) {
				s.end();
				it.remove();
			}
		}
		if (t % 10 == 0) showBar(t);
		if (t % 20 == 10) hints();
	}

	/** Starts an event now (the plan, or an admin and the self-test); false when the id is unknown. */
	public boolean start(String id) {
		if (!HgSchedule.known(id)) return false;
		int tier = tier();
		Event e = switch (id) {
			case HgSchedule.FEAST -> new Feast();
			case "wildfire" -> new Wildfire(tier);
			case "fog" -> new Fog(tier);
			case "mutts" -> new Mutts(tier);
			case "tracker_jackers" -> new Jackers(tier);
			case "storm" -> new Storm(tier);
			case "supplies" -> new Supplies(tier);
			case "revealed" -> new Revealed();
			case "acid_rain" -> new AcidRain(tier);
			case "jabberjays" -> new Jabberjays(tier);
			case "meteors" -> new Meteors(tier);
			case "earthquake" -> new Earthquake(tier);
			default -> null;
		};
		if (e == null) return false;
		e.since = host.runTicks();
		started.add(id);
		boolean gift = e.gift();
		for (ServerPlayer p : host.audience()) {
			Component name = Lang.tr(p, "hg.event." + id);
			Component sub = Lang.tr(p, "hg.event." + id + ".sub");
			Msg.title(p, Lang.tr(p, gift ? "hg.event.gift" : "hg.event.big", name), sub, 5, 70, 20);
			Msg.send(p, "hg.event.chat", name, sub);
			if (!gift && tier > 1) Msg.send(p, "hg.event.tier", tier);
			Msg.sound(p, SoundEvents.RAID_HORN, 0.5f, gift ? 1.4f : 0.9f);
		}
		Music.play(host.audience(), gift ? Music.FEAST : Music.ALARM);
		e.start();
		running.add(e);
		showBar(host.runTicks());
		return true;
	}

	private void announceFeast() {
		for (ServerPlayer p : host.audience()) {
			Msg.title(p, Lang.tr(p, "hg.feast.soon"), Lang.tr(p, "hg.feast.soon.sub"), 10, 60, 20);
			Msg.send(p, "hg.feast.soon.chat");
		}
		Music.play(host.audience(), Music.FEAST);
	}

	/** Ends everything that runs (the match is over). */
	void clear() {
		for (Event e : running) e.end();
		running.clear();
		for (Swarm s : swarms) s.end();
		swarms.clear();
		clouds.clear();
		bar.clear();
	}

	private int heldAt = -1;

	/**
	 * While the night's tribute lasts everything holds still (the clock does not tick then): mutts stop hunting, and
	 * afterwards every running event, warning, swarm and cloud has its time left as before.
	 */
	void hold(boolean on) {
		for (Event e : running) e.hold(on);
		if (on) {
			heldAt = host.runTicks();
			bar.clear();
		} else if (heldAt >= 0) {
			int d = host.runTicks() - heldAt;
			heldAt = -1;
			for (Event e : running) {
				e.since += d;
				e.shift(d);
			}
			for (Swarm s : swarms) s.until += d;
			for (Cloud c : clouds) c.until += d;
		}
	}

	// ------------------------------------------------------------------ what everyone sees

	/** The event that runs longest yet in the boss bar, counting down, in its colour. */
	private void showBar(int t) {
		Event shown = null;
		for (Event e : running) {
			if (e.length >= 60 && (shown == null || e.since >= shown.since)) shown = e;
		}
		if (shown == null) {
			bar.clear();
			return;
		}
		int left = Math.max(0, shown.length - (t - shown.since));
		bar.update(Lang.tr("hg.event.bar", Lang.tr("hg.event." + shown.id), (left + 19) / 20), left / (float) shown.length, shown.color());
		for (ServerPlayer p : host.audience()) bar.show(p);
	}

	/** Where the danger is, for every tribute that is near one: what, how far, which way. */
	private void hints() {
		for (ServerPlayer p : host.tributes()) {
			Event best = null;
			Vec3 at = null;
			double bestD = 70;
			for (Event e : running) {
				Vec3 w = e.where(p.position());
				if (w == null) continue;
				double d = horizontal(p.position(), w);
				if (d < bestD) {
					bestD = d;
					best = e;
					at = w;
				}
			}
			if (best == null) continue;
			Msg.actionBar(p, bestD < 3 ? "hg.event.here" : "hg.event.hint", Lang.tr(p, "hg.event." + best.id), (int) Math.round(bestD),
					Lang.tr(p, "hg.dir." + compass(at.x - p.getX(), at.z - p.getZ())));
		}
	}

	/** One of eight directions (0 east, going round by north as 2, west 4, south 6). */
	static int compass(double dx, double dz) {
		double a = Math.atan2(-dz, dx);
		return Math.floorMod((int) Math.round(a / (Math.PI / 4)), 8);
	}

	// ------------------------------------------------------------------ dangers (for bots)

	/**
	 * The middle of a danger near {@code at} that a bot should get away from (fire, fog, a swarm, mutts, lightning, a
	 * meteor about to land, the heart of an earthquake), or null.
	 */
	public Vec3 danger(Vec3 at) {
		for (Event e : running) {
			Vec3 d = e.danger(at);
			if (d != null) return d;
		}
		for (Cloud c : clouds) {
			if (horizontal(at, c.at) < c.radius + 2) return c.at;
		}
		for (Swarm s : swarms) {
			if (horizontal(at, s.at) < 7) return s.at;
		}
		return null;
	}

	private static double horizontal(Vec3 a, Vec3 b) {
		return Math.hypot(a.x - b.x, a.z - b.z);
	}

	// ------------------------------------------------------------------ helpers

	private ServerLevel level() {
		return host.level();
	}

	private static void effect(net.minecraft.world.entity.LivingEntity p, String id, int ticks, int amp) {
		Ids.effect(id).ifPresent(h -> p.addEffect(new MobEffectInstance(h, ticks, amp, false, true, true)));
	}

	private static void sound(ServerPlayer p, String id, float volume, float pitch) {
		Msg.sound(p, Holder.direct(SoundEvent.createVariableRangeEvent(Ids.id(id))), volume, pitch);
	}

	/** A sound for every tribute and watcher near {@code at}. */
	private void soundAt(Vec3 at, String id, float volume, float pitch, double range) {
		for (ServerPlayer p : host.audience()) {
			if (p.position().distanceTo(at) <= range) sound(p, id, volume, pitch);
		}
	}

	/** A push for a player (the client moves a player, so it is sent to them). */
	private static void push(ServerPlayer p, Vec3 by) {
		p.setDeltaMovement(p.getDeltaMovement().add(by));
		p.connection.send(new ClientboundSetEntityMotionPacket(p));
	}

	/** A ring of particles on the ground (the warning of what is about to come down there). */
	private void ring(Vec3 at, double radius, net.minecraft.core.particles.ParticleOptions particle, int points) {
		for (int i = 0; i < points; i++) {
			double a = i * Math.PI * 2 / points;
			level().sendParticles(particle, at.x + Math.cos(a) * radius, at.y + 0.15, at.z + Math.sin(a) * radius, 1, 0.02, 0.02, 0.02, 0);
		}
	}

	/** A random point on the city's ground, {@code min} to {@code max} blocks from the plaza, inside the border. */
	Vec3 somewhere(double min, double max) {
		Vec3 c = host.plaza();
		double half = Math.max(10, host.borderSize() / 2 - 6);
		max = Math.min(max, half);
		min = Math.min(min, max * 0.8);
		double ang = host.rng().nextDouble() * Math.PI * 2, r = min + host.rng().nextDouble() * (max - min);
		return ground(c.x + Math.cos(ang) * r, c.z + Math.sin(ang) * r, c.y);
	}

	/** The ground at (x, z): the highest place to stand on below some open sky near {@code nearY}. */
	Vec3 ground(double x, double z, double nearY) {
		ServerLevel level = level();
		int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
		int top = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz);
		if (top > level.getMinY() && top < nearY + 40) return new Vec3(bx + 0.5, top, bz + 0.5);
		return new Vec3(bx + 0.5, nearY, bz + 0.5);
	}

	/** Near a random tribute ({@code spread} blocks round them), or somewhere in the city when there is none. */
	private Vec3 nearSomeone(double spread) {
		List<ServerPlayer> t = host.tributes();
		if (t.isEmpty()) return somewhere(0, 50);
		ServerPlayer p = t.get(host.rng().nextInt(t.size()));
		double a = host.rng().nextDouble() * Math.PI * 2, r = host.rng().nextDouble() * spread;
		return ground(p.getX() + Math.cos(a) * r, p.getZ() + Math.sin(a) * r, p.getY());
	}

	private Entity spawn(String type, Vec3 at) {
		Entity e = Holo.create(level(), type, Entity.class);
		if (e == null) return null;
		e.snapTo(at.x, at.y, at.z);
		level().addFreshEntity(e);
		return e;
	}

	// ------------------------------------------------------------------ the events

	private abstract class Event {
		final String id;
		final int length;
		int since;

		Event(String id, int length) {
			this.id = id;
			this.length = length;
		}

		void start() {}

		void tick(int age) {}

		void end() {}

		void hold(boolean on) {}

		/** Its own moments to come are {@code d} ticks later (after a hold). */
		void shift(int d) {}

		Vec3 danger(Vec3 at) {
			return null;
		}

		/** Where this danger is as seen from {@code from} (for the direction in the action bar), or null. */
		Vec3 where(Vec3 from) {
			return null;
		}

		/** Good news (the Feast, supplies): a fanfare instead of the alarm. */
		boolean gift() {
			return false;
		}

		BossEvent.BossBarColor color() {
			return BossEvent.BossBarColor.RED;
		}
	}

	/** The Feast: tables of the best loot round the cornucopia, and the Capitol's chest opens. */
	private final class Feast extends Event {
		Feast() {
			super(HgSchedule.FEAST, 20);
		}

		@Override
		void start() {
			feastHeld = true;
			capitol.vaults().feast();
			Vec3 c = host.plaza();
			level().sendParticles(ParticleTypes.FIREWORK, c.x, c.y + 6, c.z, 160, 5, 3, 5, 0.25);
		}

		@Override
		boolean gift() {
			return true;
		}
	}

	/**
	 * A wall of fire that sweeps across the city from one side; at the third level two walls close in on the middle
	 * from two sides and leave only a narrow strip there.
	 */
	private final class Wildfire extends Event {
		final int tier;
		final List<double[]> fronts = new ArrayList<>();
		final List<Vec3> dirs = new ArrayList<>();

		Wildfire(int tier) {
			super("wildfire", Escalation.fireTicks(tier));
			this.tier = tier;
		}

		@Override
		void start() {
			double ang = host.rng().nextInt(4) * Math.PI / 2;
			Vec3 dir = new Vec3(Math.cos(ang), 0, Math.sin(ang));
			double half = host.borderSize() / 2;
			if (Escalation.twoFronts(tier)) {
				dirs.add(dir);
				fronts.add(new double[] {-half, -3});
				dirs.add(dir.scale(-1));
				fronts.add(new double[] {-half, -3});
			} else {
				dirs.add(dir);
				fronts.add(new double[] {-half, half});
			}
		}

		/** How far along its way front {@code i} is now (blocks from the plaza, along its direction). */
		double front(int i, int age) {
			double[] f = fronts.get(i);
			return f[0] + (f[1] - f[0]) * Math.min(1.0, age / (double) length);
		}

		@Override
		void tick(int age) {
			Vec3 c = host.plaza();
			double width = Escalation.fireWidth(tier);
			for (int i = 0; i < fronts.size(); i++) {
				Vec3 dir = dirs.get(i), across = new Vec3(-dir.z, 0, dir.x);
				double f = front(i, age);
				if (age % 3 == 0) {
					for (ServerPlayer p : host.audience()) {
						// the flames shown near everyone who watches, along the front, as thick as it is
						double side = (p.getX() - c.x) * across.x + (p.getZ() - c.z) * across.z;
						for (int k = -16; k <= 16; k += 2) {
							double s = side + k;
							for (double w = -width / 2; w <= width / 2; w += 1.5) {
								double x = c.x + dir.x * (f + w) + across.x * s, z = c.z + dir.z * (f + w) + across.z * s;
								level().sendParticles(ParticleTypes.FLAME, x, p.getY() + 0.5, z, 2, 0.4, 1.4, 0.4, 0.01);
							}
							double x = c.x + dir.x * f + across.x * s, z = c.z + dir.z * f + across.z * s;
							level().sendParticles(ParticleTypes.LARGE_SMOKE, x, p.getY() + 3, z, 1, 0.5, 1.5, 0.5, 0.02);
							if (k % 8 == 0) level().sendParticles(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, x, p.getY() + 2, z, 1, 0.3, 0.3, 0.3, 0.02);
						}
					}
				}
				for (ServerPlayer p : host.tributes()) {
					double along = (p.getX() - c.x) * dir.x + (p.getZ() - c.z) * dir.z;
					if (Math.abs(along - f) < width / 2 + 0.5) {
						p.igniteForSeconds(Escalation.burnSeconds(tier));
						if (age % 10 == 0) p.hurtServer(level(), level().damageSources().inFire(), Escalation.fireDamage(tier));
					}
				}
			}
			if (age % 30 == 0) soundAtFronts(age);
		}

		private void soundAtFronts(int age) {
			for (ServerPlayer p : host.audience()) {
				Vec3 w = where(p.position());
				if (w != null && horizontal(p.position(), w) < 30) sound(p, "minecraft:block.fire.ambient", 1f, 0.6f);
			}
		}

		@Override
		Vec3 where(Vec3 from) {
			Vec3 c = host.plaza();
			Vec3 best = null;
			double bestD = Double.MAX_VALUE;
			int age = host.runTicks() - since;
			for (int i = 0; i < fronts.size(); i++) {
				Vec3 dir = dirs.get(i);
				double f = front(i, age), along = (from.x - c.x) * dir.x + (from.z - c.z) * dir.z;
				Vec3 at = from.add(dir.scale(f - along));
				double d = Math.abs(f - along);
				if (d < bestD) {
					bestD = d;
					best = at;
				}
			}
			return best;
		}

		@Override
		Vec3 danger(Vec3 at) {
			Vec3 c = host.plaza();
			int age = host.runTicks() - since;
			for (int i = 0; i < fronts.size(); i++) {
				Vec3 dir = dirs.get(i);
				double f = front(i, age), along = (at.x - c.x) * dir.x + (at.z - c.z) * dir.z;
				// ahead of the front and close: run ahead of it (away from where it comes)
				if (along > f - 3 && along < f + 10) return new Vec3(c.x + dir.x * (f - 20), at.y, c.z + dir.z * (f - 20));
			}
			return null;
		}
	}

	/** A poisonous fog that drifts after the nearest tribute; from the second level it withers too. */
	private final class Fog extends Event {
		final int tier;
		final double radius;
		Vec3 at;

		Fog(int tier) {
			super("fog", 45 * 20);
			this.tier = tier;
			this.radius = Escalation.fogRadius(tier);
		}

		@Override
		void start() {
			at = somewhere(10, 50);
		}

		@Override
		void tick(int age) {
			ServerPlayer near = null;
			double best = 80;
			for (ServerPlayer p : host.tributes()) {
				double d = horizontal(p.position(), at);
				if (d < best) {
					best = d;
					near = p;
				}
			}
			if (near != null && best > 1) {
				Vec3 to = new Vec3(near.getX() - at.x, 0, near.getZ() - at.z).normalize().scale(Escalation.fogDrift(tier));
				at = new Vec3(at.x + to.x, near.getY() * 0.02 + at.y * 0.98, at.z + to.z);
			}
			if (age % 2 == 0) {
				for (int i = 0; i < 30 + tier * 8; i++) {
					double a = host.rng().nextDouble() * Math.PI * 2, r = Math.sqrt(host.rng().nextDouble()) * radius;
					level().sendParticles(ParticleTypes.SNEEZE, at.x + Math.cos(a) * r, at.y + 0.5 + host.rng().nextDouble() * 4, at.z + Math.sin(a) * r,
							1, 0.2, 0.2, 0.2, 0);
				}
				ring(at, radius, new DustParticleOptions(0x4F7A28, 2.0f), 48);
			}
			if (age % 10 != 0) return;
			for (ServerPlayer p : host.tributes()) {
				double d = horizontal(p.position(), at);
				if (d > radius || Math.abs(p.getY() - at.y) > 12) continue;
				effect(p, "minecraft:poison", 60, Escalation.fogPoison(tier));
				effect(p, "minecraft:slowness", 30, 0);
				if (Escalation.fogWither(tier)) effect(p, "minecraft:wither", 40, 0);
				if (d > radius - 3) effect(p, "minecraft:blindness", 25, 0);
				Msg.actionBar(p, "hg.fog.inside");
			}
		}

		@Override
		Vec3 danger(Vec3 p) {
			return horizontal(p, at) < radius + 4 ? at : null;
		}

		@Override
		Vec3 where(Vec3 from) {
			return at;
		}

		@Override
		BossEvent.BossBarColor color() {
			return BossEvent.BossBarColor.GREEN;
		}
	}

	/** Mutts: beasts that hunt the tributes for a while, more and faster at higher levels. */
	private final class Mutts extends Event {
		final int tier;
		final List<Entity> beasts = new ArrayList<>();

		Mutts(int tier) {
			super("mutts", Escalation.muttTicks(tier));
			this.tier = tier;
		}

		@Override
		void start() {
			for (ServerPlayer p : host.tributes()) {
				for (int k = 0; k < Escalation.muttsEach(tier); k++) {
					double ang = host.rng().nextDouble() * Math.PI * 2, r = 10 + host.rng().nextInt(5);
					Vec3 at = ground(p.getX() + Math.cos(ang) * r, p.getZ() + Math.sin(ang) * r, p.getY());
					Entity e = spawn("minecraft:zoglin", at);
					if (e == null) continue;
					e.setCustomName(Lang.tr("hg.mutt"));
					e.setCustomNameVisible(true);
					if (e instanceof Mob m) {
						m.setTarget(p);
						m.setPersistenceRequired();
						if (tier >= 2) effect(m, "minecraft:speed", length, tier - 2);
						if (tier >= 3) effect(m, "minecraft:resistance", length, 0);
					}
					level().sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y + 0.5, at.z, 12, 0.4, 0.4, 0.4, 0.02);
					beasts.add(e);
				}
				sound(p, "minecraft:entity.zoglin.angry", 1f, 0.7f);
			}
		}

		@Override
		void hold(boolean on) {
			for (Entity e : beasts) {
				if (e instanceof Mob m && !e.isRemoved()) m.setNoAi(on);
			}
		}

		@Override
		void end() {
			for (Entity e : beasts) {
				if (!e.isRemoved()) {
					level().sendParticles(ParticleTypes.POOF, e.getX(), e.getY() + 0.5, e.getZ(), 10, 0.3, 0.3, 0.3, 0.02);
					e.discard();
				}
			}
			beasts.clear();
		}

		@Override
		Vec3 danger(Vec3 at) {
			for (Entity e : beasts) {
				if (!e.isRemoved() && e.position().distanceTo(at) < 8) return e.position();
			}
			return null;
		}

		@Override
		Vec3 where(Vec3 from) {
			Vec3 best = null;
			for (Entity e : beasts) {
				if (!e.isRemoved() && (best == null || e.position().distanceTo(from) < best.distanceTo(from))) best = e.position();
			}
			return best;
		}

		@Override
		BossEvent.BossBarColor color() {
			return BossEvent.BossBarColor.PINK;
		}
	}

	/** Tracker jackers: one swarm per level, each near another tribute, chasing the nearest one. */
	private final class Jackers extends Event {
		final int tier;

		Jackers(int tier) {
			super("tracker_jackers", 30 * 20);
			this.tier = tier;
		}

		@Override
		void start() {
			List<ServerPlayer> t = new ArrayList<>(host.tributes());
			java.util.Collections.shuffle(t, host.rng());
			for (int i = 0; i < Math.min(t.size(), Escalation.swarms(tier)); i++) {
				ServerPlayer p = t.get(i);
				double ang = host.rng().nextDouble() * Math.PI * 2;
				swarm(ground(p.getX() + Math.cos(ang) * 18, p.getZ() + Math.sin(ang) * 18, p.getY()).add(0, 1.5, 0), null, length,
						Escalation.swarmSpeed(tier), Escalation.stingDamage(tier), tier >= 2);
			}
		}

		@Override
		Vec3 where(Vec3 from) {
			Vec3 best = null;
			for (Swarm s : swarms) {
				if (best == null || s.at.distanceTo(from) < best.distanceTo(from)) best = s.at;
			}
			return best;
		}

		@Override
		BossEvent.BossBarColor color() {
			return BossEvent.BossBarColor.YELLOW;
		}
	}

	/**
	 * Lightning over one part of the city: more often, wider and harder at higher levels. A bolt meant for a tribute is
	 * shown a second before it strikes (a crackling ring), so it can be dodged.
	 */
	private final class Storm extends Event {
		final int tier;
		final double radius;
		final List<double[]> coming = new ArrayList<>();
		Vec3 at;

		Storm(int tier) {
			super("storm", 30 * 20);
			this.tier = tier;
			this.radius = Escalation.stormRadius(tier);
		}

		@Override
		void start() {
			at = somewhere(0, 45);
			for (ServerPlayer p : host.audience()) {
				p.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.THUNDER_LEVEL_CHANGE, 1f));
			}
		}

		@Override
		void tick(int age) {
			int t = host.runTicks();
			for (Iterator<double[]> it = coming.iterator(); it.hasNext();) {
				double[] c = it.next();
				Vec3 hit = new Vec3(c[0], c[1], c[2]);
				if (t >= c[3]) {
					it.remove();
					strike(hit);
				} else if (t % 3 == 0) {
					ring(hit, 1.6, ParticleTypes.ELECTRIC_SPARK, 18);
				}
			}
			if (age % Escalation.strikeEvery(tier) != 0) return;
			double a = host.rng().nextDouble() * Math.PI * 2, r = host.rng().nextDouble() * radius;
			Vec3 hit = ground(at.x + Math.cos(a) * r, at.z + Math.sin(a) * r, at.y);
			// a tribute in the storm is aimed at now and then: shown a second ahead
			for (ServerPlayer p : host.tributes()) {
				if (horizontal(p.position(), at) < radius && host.rng().nextDouble() < Escalation.aimedShare(tier)) {
					coming.add(new double[] {p.getX(), p.getY(), p.getZ(), t + 20});
					soundAt(p.position(), "minecraft:block.beacon.deactivate", 0.8f, 1.8f, 12);
					return;
				}
			}
			strike(hit);
		}

		@Override
		void shift(int d) {
			for (double[] c : coming) c[3] += d;
		}

		private void strike(Vec3 hit) {
			Entity e = Holo.create(level(), "minecraft:lightning_bolt", Entity.class);
			if (e instanceof LightningBolt bolt) {
				bolt.setVisualOnly(true);
				bolt.snapTo(hit.x, hit.y, hit.z);
				level().addFreshEntity(bolt);
			}
			level().playSound(null, hit.x, hit.y, hit.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, 2f, 0.9f);
			for (ServerPlayer p : host.tributes()) {
				if (p.position().distanceTo(hit) > 3) continue;
				p.hurtServer(level(), level().damageSources().lightningBolt(), Escalation.strikeDamage(tier));
				p.igniteForSeconds(3f);
			}
		}

		@Override
		void end() {
			for (ServerPlayer p : host.audience()) {
				p.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.THUNDER_LEVEL_CHANGE, 0f));
			}
		}

		@Override
		Vec3 danger(Vec3 p) {
			for (double[] c : coming) {
				if (Math.hypot(p.x - c[0], p.z - c[2]) < 4) return new Vec3(c[0], c[1], c[2]);
			}
			return horizontal(p, at) < radius + 4 ? at : null;
		}

		@Override
		Vec3 where(Vec3 from) {
			return at;
		}

		@Override
		BossEvent.BossBarColor color() {
			return BossEvent.BossBarColor.BLUE;
		}
	}

	/** Crates of supplies drop on parachutes all over the city (more later on). */
	private final class Supplies extends Event {
		final int tier;

		Supplies(int tier) {
			super("supplies", 20);
			this.tier = tier;
		}

		@Override
		void start() {
			for (int i = 0; i < 4 + tier; i++) capitol.sponsors().drop(somewhere(8, 70), "supply", null);
		}

		@Override
		boolean gift() {
			return true;
		}
	}

	/** Everyone glows for a while: there is nowhere to hide. */
	private final class Revealed extends Event {
		Revealed() {
			super("revealed", 20 * 20);
		}

		@Override
		void start() {
			for (ServerPlayer p : host.tributes()) effect(p, "minecraft:glowing", length, 0);
		}

		@Override
		BossEvent.BossBarColor color() {
			return BossEvent.BossBarColor.PURPLE;
		}
	}

	/** Rain that burns whoever is not under a roof (harder later on). The rain falls for the match's players only. */
	private final class AcidRain extends Event {
		final int tier;

		AcidRain(int tier) {
			super("acid_rain", 30 * 20);
			this.tier = tier;
		}

		@Override
		void start() {
			for (ServerPlayer p : host.audience()) {
				p.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.START_RAINING, 0f));
				p.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.RAIN_LEVEL_CHANGE, 1f));
			}
		}

		@Override
		void tick(int age) {
			if (age % Escalation.acidEvery(tier) != 0) return;
			for (ServerPlayer p : host.tributes()) {
				if (!level().canSeeSky(BlockPos.containing(p.getX(), p.getEyeY(), p.getZ()))) continue;
				p.hurtServer(level(), level().damageSources().magic(), Escalation.acidDamage(tier));
				if (tier >= 2) effect(p, "minecraft:weakness", 60, 0);
				level().sendParticles(ParticleTypes.ITEM_SLIME, p.getX(), p.getY() + 1, p.getZ(), 6, 0.3, 0.5, 0.3, 0.02);
				Msg.actionBar(p, "hg.acid.outside");
			}
		}

		@Override
		void end() {
			for (ServerPlayer p : host.audience()) {
				p.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.RAIN_LEVEL_CHANGE, 0f));
				p.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.STOP_RAINING, 0f));
			}
		}

		@Override
		BossEvent.BossBarColor color() {
			return BossEvent.BossBarColor.GREEN;
		}
	}

	/** Strange voices and whispered names, an arrow to the nearest tribute, and a scream that turns the stomach. */
	private final class Jabberjays extends Event {
		final int tier;

		Jabberjays(int tier) {
			super("jabberjays", 45 * 20);
			this.tier = tier;
		}

		@Override
		void tick(int age) {
			List<ServerPlayer> all = host.tributes();
			for (ServerPlayer p : all) {
				if (age % 5 == 0) capitol.powers().pointer(p, "hg.jabberjays.pointer");
				if (host.rng().nextInt(70) != 0) continue;
				double a = host.rng().nextDouble() * Math.PI * 2, r = 6 + host.rng().nextInt(7);
				net.minecraft.sounds.SoundEvent[] voices = {SoundEvents.PARROT_AMBIENT, SoundEvents.FOX_SCREECH, SoundEvents.GOAT_SCREAMING_AMBIENT,
						SoundEvents.ENDERMAN_AMBIENT, SoundEvents.VILLAGER_AMBIENT};
				level().playSound(null, p.getX() + Math.cos(a) * r, p.getY() + 1, p.getZ() + Math.sin(a) * r,
						voices[host.rng().nextInt(voices.length)], SoundSource.HOSTILE, 1f, 0.6f + host.rng().nextFloat() * 0.5f);
				ServerPlayer o = all.get(host.rng().nextInt(all.size()));
				if (o != p) Msg.send(p, "hg.jabberjays.whisper", host.name(o.getUUID()));
			}
			if (age % 100 == 50 && !all.isEmpty()) {
				ServerPlayer p = all.get(host.rng().nextInt(all.size()));
				sound(p, "minecraft:entity.ghast.scream", 1f, 0.5f);
				effect(p, "minecraft:nausea", 80, 0);
				if (tier >= 2) effect(p, "minecraft:darkness", 60, 0);
				Msg.actionBar(p, "hg.jabberjays.scream");
			}
		}

		@Override
		BossEvent.BossBarColor color() {
			return BossEvent.BossBarColor.PURPLE;
		}
	}

	/**
	 * Meteors: burning rocks from the sky, often near a tribute. Where one will land a red circle glows two seconds
	 * ahead; then it comes down in flames and bursts (it hurts, burns and throws people about; the city stays whole).
	 */
	private final class Meteors extends Event {
		final int tier;
		final List<double[]> coming = new ArrayList<>();

		Meteors(int tier) {
			super("meteors", 30 * 20);
			this.tier = tier;
		}

		@Override
		void tick(int age) {
			int t = host.runTicks();
			if (age % Escalation.meteorEvery(tier) == 0 && age < length - Escalation.METEOR_WARNING) {
				Vec3 at = host.rng().nextDouble() < 0.6 ? nearSomeone(6) : nearSomeone(30);
				coming.add(new double[] {at.x, at.y, at.z, t + Escalation.METEOR_WARNING});
				soundAt(at, "minecraft:entity.firework_rocket.launch", 1f, 0.5f, 40);
			}
			for (Iterator<double[]> it = coming.iterator(); it.hasNext();) {
				double[] c = it.next();
				Vec3 at = new Vec3(c[0], c[1], c[2]);
				int left = (int) (c[3] - t);
				if (left <= 0) {
					it.remove();
					impact(at);
					continue;
				}
				if (t % 3 == 0) ring(at, 2.8, new DustParticleOptions(0xFF2A00, 1.6f), 24);
				if (left <= 12) {
					// the rock comes down in flames
					double y = at.y + left * 2.2;
					level().sendParticles(ParticleTypes.FLAME, at.x, y, at.z, 12, 0.35, 0.35, 0.35, 0.02);
					level().sendParticles(ParticleTypes.LARGE_SMOKE, at.x, y + 1.5, at.z, 4, 0.4, 0.6, 0.4, 0.01);
					level().sendParticles(ParticleTypes.LAVA, at.x, y, at.z, 2, 0.2, 0.2, 0.2, 0);
				}
			}
		}

		@Override
		void shift(int d) {
			for (double[] c : coming) c[3] += d;
		}

		private void impact(Vec3 at) {
			impacts++;
			level().explode(null, at.x, at.y + 0.2, at.z, Escalation.meteorPower(tier), Level.ExplosionInteraction.NONE);
			level().sendParticles(ParticleTypes.LAVA, at.x, at.y + 0.5, at.z, 20, 1.2, 0.5, 1.2, 0.1);
			level().sendParticles(ParticleTypes.FLAME, at.x, at.y + 0.5, at.z, 40, 1.5, 0.6, 1.5, 0.06);
			for (ServerPlayer p : host.tributes()) {
				if (p.position().distanceTo(at) < 3.5) p.igniteForSeconds(4f);
			}
		}

		@Override
		Vec3 danger(Vec3 p) {
			for (double[] c : coming) {
				if (Math.hypot(p.x - c[0], p.z - c[2]) < 5) return new Vec3(c[0], c[1], c[2]);
			}
			return null;
		}

		@Override
		Vec3 where(Vec3 from) {
			Vec3 best = null;
			for (double[] c : coming) {
				Vec3 v = new Vec3(c[0], c[1], c[2]);
				if (best == null || v.distanceTo(from) < best.distanceTo(from)) best = v;
			}
			return best;
		}
	}

	/**
	 * An earthquake round one point of the city: a rumble, dust from the ground, shocks that throw the tributes up and
	 * hurt them, rubble that falls from the sky (its shadow is shown where it will land), slowness and, later on, nausea.
	 */
	private final class Earthquake extends Event {
		final int tier;
		final double radius;
		final List<Rubble> rubble = new ArrayList<>();
		Vec3 at;

		Earthquake(int tier) {
			super("earthquake", 25 * 20);
			this.tier = tier;
			this.radius = Escalation.quakeRadius(tier);
		}

		private final class Rubble {
			final Vec3 to;
			int lands;
			final Display.BlockDisplay block;
			final double fromY;

			Rubble(Vec3 to, int lands, Display.BlockDisplay block, double fromY) {
				this.to = to;
				this.lands = lands;
				this.block = block;
				this.fromY = fromY;
			}
		}

		@Override
		void start() {
			at = somewhere(0, 45);
		}

		@Override
		void shift(int d) {
			for (Rubble r : rubble) r.lands += d;
		}

		private boolean shaken(ServerPlayer p) {
			return horizontal(p.position(), at) < radius;
		}

		@Override
		void tick(int age) {
			int t = host.runTicks();
			if (age % 20 == 0) {
				for (ServerPlayer p : host.audience()) {
					if (horizontal(p.position(), at) < radius + 20) sound(p, "minecraft:entity.warden.dig", 1f, 0.5f);
				}
			}
			if (age % 4 == 0) {
				for (ServerPlayer p : host.tributes()) {
					if (!shaken(p)) continue;
					var below = level().getBlockState(BlockPos.containing(p.getX(), p.getY() - 0.5, p.getZ()));
					if (!below.isAir()) {
						level().sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, below), p.getX(), p.getY() + 0.1, p.getZ(), 10, 2.5, 0.1, 2.5, 0.1);
					}
				}
			}
			if (age % Escalation.shockEvery(tier) == 0) shock();
			for (Iterator<Rubble> it = rubble.iterator(); it.hasNext();) {
				Rubble r = it.next();
				int left = r.lands - t;
				if (left <= 0) {
					it.remove();
					Holo.remove(r.block);
					level().sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Ids.state("minecraft:cobblestone")), r.to.x, r.to.y + 0.3, r.to.z,
							25, 0.6, 0.3, 0.6, 0.1);
					soundAt(r.to, "minecraft:block.stone.break", 1f, 0.6f, 20);
					for (ServerPlayer p : host.tributes()) {
						if (p.position().distanceTo(r.to) < 1.8) p.hurtServer(level(), level().damageSources().fallingBlock(null), 4f + tier);
					}
					continue;
				}
				if (r.block != null) r.block.snapTo(r.to.x, r.to.y + (r.fromY - r.to.y) * left / 16.0, r.to.z);
				if (t % 3 == 0) ring(r.to, 0.9, new DustParticleOptions(0x3A3A3A, 1.2f), 10);
			}
		}

		private void shock() {
			shocks++;
			int t = host.runTicks();
			for (ServerPlayer p : host.tributes()) {
				if (!shaken(p)) continue;
				double a = host.rng().nextDouble() * Math.PI * 2;
				push(p, new Vec3(Math.cos(a) * 0.25, 0.35 + 0.08 * tier, Math.sin(a) * 0.25));
				p.hurtServer(level(), level().damageSources().magic(), Escalation.shockDamage(tier));
				effect(p, "minecraft:slowness", 50, 1);
				if (tier >= 2) effect(p, "minecraft:nausea", 90, 0);
				sound(p, "minecraft:entity.generic.explode", 0.6f, 0.5f);
			}
			for (int i = 0; i < Escalation.rubble(tier); i++) {
				Vec3 to = nearSomeone(8);
				if (horizontal(to, at) > radius) continue;
				Display.BlockDisplay b = Holo.block(level(), to.add(0, 16, 0), Ids.state("minecraft:cobblestone"), 0.9f);
				rubble.add(new Rubble(to, t + 16, b, to.y + 16));
			}
		}

		@Override
		void end() {
			for (Rubble r : rubble) Holo.remove(r.block);
			rubble.clear();
		}

		@Override
		Vec3 danger(Vec3 p) {
			for (Rubble r : rubble) {
				if (horizontal(p, r.to) < 2.5) return r.to;
			}
			return horizontal(p, at) < radius * 0.5 ? at : null;
		}

		@Override
		Vec3 where(Vec3 from) {
			return at;
		}

		@Override
		BossEvent.BossBarColor color() {
			return BossEvent.BossBarColor.WHITE;
		}
	}

	// ------------------------------------------------------------------ clouds and swarms (power-ups too)

	/** A poison cloud (nightlock) at {@code at}, {@code radius} blocks, for {@code ticks}; its thrower is spared. */
	void cloud(Vec3 at, double radius, int ticks, UUID owner) {
		clouds.add(new Cloud(at, radius, host.runTicks() + ticks, owner));
	}

	private final class Cloud {
		final Vec3 at;
		final double radius;
		int until;
		final UUID owner;

		Cloud(Vec3 at, double radius, int until, UUID owner) {
			this.at = at;
			this.radius = radius;
			this.until = until;
			this.owner = owner;
		}

		boolean tick(int t) {
			if (t > until) return false;
			if (t % 3 == 0) level().sendParticles(ParticleTypes.WITCH, at.x, at.y + 0.6, at.z, 8, radius * 0.5, 0.4, radius * 0.5, 0.01);
			if (t % 10 == 0) {
				for (ServerPlayer p : host.tributes()) {
					if (p.getUUID().equals(owner) || p.position().distanceTo(at) > radius) continue;
					effect(p, "minecraft:poison", 60, 1);
				}
			}
			return true;
		}
	}

	/** A swarm of tracker jackers at {@code at} for {@code ticks}, chasing the nearest tribute but its thrower. */
	void swarm(Vec3 at, UUID owner, int ticks) {
		swarm(at, owner, ticks, Escalation.swarmSpeed(1), Escalation.stingDamage(1), false);
	}

	private void swarm(Vec3 at, UUID owner, int ticks, double speed, float sting, boolean blind) {
		Swarm s = new Swarm(at, owner, host.runTicks() + ticks, speed, sting, blind);
		for (int i = 0; i < 6; i++) {
			Entity b = Holo.create(level(), "minecraft:bee", Entity.class);
			if (b == null) continue;
			if (b instanceof Mob m) m.setNoAi(true);
			b.setCustomName(Lang.tr("hg.jacker"));
			b.snapTo(at.x, at.y, at.z);
			level().addFreshEntity(b);
			s.bees.add(b);
		}
		swarms.add(s);
	}

	private final class Swarm {
		Vec3 at;
		final UUID owner;
		int until;
		final double speed;
		final float sting;
		final boolean blind;
		final List<Entity> bees = new ArrayList<>();
		final Map<UUID, Integer> stung = new HashMap<>();

		Swarm(Vec3 at, UUID owner, int until, double speed, float sting, boolean blind) {
			this.at = at;
			this.owner = owner;
			this.until = until;
			this.speed = speed;
			this.sting = sting;
			this.blind = blind;
		}

		boolean tick(int t) {
			if (t > until) return false;
			ServerPlayer target = null;
			double best = 40;
			for (ServerPlayer p : host.tributes()) {
				if (p.getUUID().equals(owner)) continue;
				double d = p.position().distanceTo(at);
				if (d < best) {
					best = d;
					target = p;
				}
			}
			if (target != null) {
				Vec3 to = target.position().add(0, 1.2, 0).subtract(at);
				double len = to.length();
				if (len > 0.01) at = at.add(to.scale(Math.min(len, speed) / len));
				if (len < 1.8 && t - stung.getOrDefault(target.getUUID(), -100) > 25) {
					stung.put(target.getUUID(), t);
					target.hurtServer(level(), level().damageSources().sting(null), sting);
					effect(target, "minecraft:poison", 100, sting > 3 ? 1 : 0);
					effect(target, "minecraft:nausea", 140, 0);
					if (blind) effect(target, "minecraft:blindness", 20, 0);
					level().playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.BEE_STING, SoundSource.HOSTILE, 1f, 1f);
				}
			}
			for (int i = 0; i < bees.size(); i++) {
				Entity b = bees.get(i);
				double a = t * 0.3 + i * Math.PI / 3, r = 0.8 + 0.3 * Math.sin(t * 0.2 + i);
				b.setPos(at.x + Math.cos(a) * r, at.y + 0.3 * Math.sin(t * 0.4 + i), at.z + Math.sin(a) * r);
			}
			if (t % 20 == 0) level().playSound(null, at.x, at.y, at.z, SoundEvents.BEE_LOOP_AGGRESSIVE, SoundSource.HOSTILE, 0.8f, 1f);
			return true;
		}

		void end() {
			for (Entity b : bees) Holo.remove(b);
			bees.clear();
		}
	}

	/** Swarms and clouds alive now (self-test). */
	public int swarms() {
		return swarms.size();
	}

	public int clouds() {
		return clouds.size();
	}

	/** Meteors that came down, earthquake shocks, and who sees the boss bar of the events (self-test). */
	public int impacts() {
		return impacts;
	}

	public int shocks() {
		return shocks;
	}

	public int barViewers() {
		return bar.viewers();
	}
}
