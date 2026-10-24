package net.battlehub.games.hunger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.battlehub.core.hunger.HgSchedule;
import net.battlehub.lang.Lang;
import net.battlehub.util.Ids;
import net.battlehub.util.Msg;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/**
 * The Gamemakers of one Hunger Games match: they follow their plan ({@link HgSchedule}) and send the Feast and the
 * arena events, announced with a title and a sound. Every event cleans up after itself (mutts and bees gone, rain
 * stopped) and none breaks the city. A tribute alive when an event ends wins the sponsors' favour. The places an
 * event makes dangerous are known ({@link #danger}), so bots run from them.
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
	private boolean feastHeld;

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
	}

	/** Starts an event now (the plan, or an admin and the self-test); false when the id is unknown. */
	public boolean start(String id) {
		if (!HgSchedule.known(id)) return false;
		Event e = switch (id) {
			case HgSchedule.FEAST -> new Feast();
			case "wildfire" -> new Wildfire();
			case "fog" -> new Fog();
			case "mutts" -> new Mutts();
			case "tracker_jackers" -> new Jackers();
			case "storm" -> new Storm();
			case "supplies" -> new Supplies();
			case "revealed" -> new Revealed();
			case "acid_rain" -> new AcidRain();
			case "jabberjays" -> new Jabberjays();
			default -> null;
		};
		if (e == null) return false;
		e.since = host.runTicks();
		started.add(id);
		for (ServerPlayer p : host.audience()) {
			Msg.title(p, Lang.tr(p, "hg.event." + id), Lang.tr(p, "hg.event." + id + ".sub"), 10, 60, 20);
			Msg.send(p, "hg.event.chat", Lang.tr(p, "hg.event." + id), Lang.tr(p, "hg.event." + id + ".sub"));
			Msg.sound(p, SoundEvents.RAID_HORN, 0.7f, 1.3f);
		}
		e.start();
		running.add(e);
		return true;
	}

	private void announceFeast() {
		for (ServerPlayer p : host.audience()) {
			Msg.title(p, Lang.tr(p, "hg.feast.soon"), Lang.tr(p, "hg.feast.soon.sub"), 10, 60, 20);
			Msg.send(p, "hg.feast.soon.chat");
			Msg.sound(p, SoundEvents.NOTE_BLOCK_BELL, 1f, 0.7f);
		}
	}

	/** Ends everything that runs (the match is over). */
	void clear() {
		for (Event e : running) e.end();
		running.clear();
		for (Swarm s : swarms) s.end();
		swarms.clear();
		clouds.clear();
	}

	// ------------------------------------------------------------------ dangers (for bots)

	/**
	 * The middle of a danger near {@code at} that a bot should get away from (fire, fog, a swarm, mutts, lightning),
	 * or null.
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

	private static void effect(ServerPlayer p, String id, int ticks, int amp) {
		Ids.effect(id).ifPresent(h -> p.addEffect(new MobEffectInstance(h, ticks, amp, false, true, true)));
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

		Vec3 danger(Vec3 at) {
			return null;
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
			level().sendParticles(ParticleTypes.FIREWORK, c.x, c.y + 6, c.z, 120, 4, 2, 4, 0.2);
		}
	}

	/** A wall of fire that sweeps across the city from one side. */
	private final class Wildfire extends Event {
		Vec3 dir, across;
		double from, to;

		Wildfire() {
			super("wildfire", 40 * 20);
		}

		@Override
		void start() {
			double ang = host.rng().nextInt(4) * Math.PI / 2;
			dir = new Vec3(Math.cos(ang), 0, Math.sin(ang));
			across = new Vec3(-dir.z, 0, dir.x);
			double half = host.borderSize() / 2;
			from = -half;
			to = half;
		}

		/** How far along its way the front is now (blocks from the plaza, along {@code dir}). */
		double front(int age) {
			return from + (to - from) * Math.min(1.0, age / (double) length);
		}

		@Override
		void tick(int age) {
			Vec3 c = host.plaza();
			double f = front(age);
			if (age % 3 == 0) {
				for (ServerPlayer p : host.tributes()) {
					// the flames shown near each tribute, along the front
					double side = (p.getX() - c.x) * across.x + (p.getZ() - c.z) * across.z;
					for (int k = -12; k <= 12; k += 2) {
						double s = side + k;
						double x = c.x + dir.x * f + across.x * s, z = c.z + dir.z * f + across.z * s;
						level().sendParticles(ParticleTypes.FLAME, x, p.getY() + 0.5, z, 3, 0.4, 1.2, 0.4, 0.01);
						level().sendParticles(ParticleTypes.LARGE_SMOKE, x, p.getY() + 2.5, z, 1, 0.4, 0.8, 0.4, 0.01);
					}
				}
			}
			for (ServerPlayer p : host.tributes()) {
				double along = (p.getX() - c.x) * dir.x + (p.getZ() - c.z) * dir.z;
				if (Math.abs(along - f) < 2.0) {
					p.igniteForSeconds(4f);
					if (age % 10 == 0) p.hurtServer(level(), level().damageSources().inFire(), 1f);
				}
			}
		}

		@Override
		Vec3 danger(Vec3 at) {
			Vec3 c = host.plaza();
			double f = front(host.runTicks() - since);
			double along = (at.x - c.x) * dir.x + (at.z - c.z) * dir.z;
			// ahead of the front and close: run ahead of it (away from where it comes)
			return along > f - 3 && along < f + 10 ? new Vec3(c.x + dir.x * (f - 20), at.y, c.z + dir.z * (f - 20)) : null;
		}
	}

	/** A poisonous fog drifting through part of the city. */
	private final class Fog extends Event {
		Vec3 at, drift;
		static final double RADIUS = 18;

		Fog() {
			super("fog", 45 * 20);
		}

		@Override
		void start() {
			at = somewhere(10, 50);
			double ang = host.rng().nextDouble() * Math.PI * 2;
			drift = new Vec3(Math.cos(ang) * 0.05, 0, Math.sin(ang) * 0.05);
		}

		@Override
		void tick(int age) {
			at = at.add(drift);
			if (age % 2 == 0) {
				for (int i = 0; i < 24; i++) {
					double a = host.rng().nextDouble() * Math.PI * 2, r = Math.sqrt(host.rng().nextDouble()) * RADIUS;
					level().sendParticles(ParticleTypes.SNEEZE, at.x + Math.cos(a) * r, at.y + 0.5 + host.rng().nextDouble() * 3, at.z + Math.sin(a) * r,
							1, 0.2, 0.2, 0.2, 0);
				}
			}
			if (age % 10 != 0) return;
			for (ServerPlayer p : host.tributes()) {
				if (horizontal(p.position(), at) > RADIUS || Math.abs(p.getY() - at.y) > 12) continue;
				effect(p, "minecraft:poison", 60, 0);
				effect(p, "minecraft:slowness", 30, 0);
				Msg.actionBar(p, "hg.fog.inside");
			}
		}

		@Override
		Vec3 danger(Vec3 p) {
			return horizontal(p, at) < RADIUS + 4 ? at : null;
		}
	}

	/** Mutts: beasts that hunt the tributes for a while. */
	private final class Mutts extends Event {
		final List<Entity> beasts = new ArrayList<>();

		Mutts() {
			super("mutts", 45 * 20);
		}

		@Override
		void start() {
			for (ServerPlayer p : host.tributes()) {
				double ang = host.rng().nextDouble() * Math.PI * 2, r = 10 + host.rng().nextInt(5);
				Vec3 at = ground(p.getX() + Math.cos(ang) * r, p.getZ() + Math.sin(ang) * r, p.getY());
				Entity e = spawn("minecraft:zoglin", at);
				if (e == null) continue;
				e.setCustomName(Lang.tr("hg.mutt"));
				e.setCustomNameVisible(true);
				if (e instanceof Mob m) {
					m.setTarget(p);
					m.setPersistenceRequired();
				}
				beasts.add(e);
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
	}

	/** Tracker jackers: a swarm somewhere near a tribute that chases the nearest one. */
	private final class Jackers extends Event {
		Jackers() {
			super("tracker_jackers", 30 * 20);
		}

		@Override
		void start() {
			List<ServerPlayer> t = host.tributes();
			if (t.isEmpty()) return;
			ServerPlayer p = t.get(host.rng().nextInt(t.size()));
			double ang = host.rng().nextDouble() * Math.PI * 2;
			swarm(ground(p.getX() + Math.cos(ang) * 18, p.getZ() + Math.sin(ang) * 18, p.getY()).add(0, 1.5, 0), null, length);
		}
	}

	/** Lightning over one part of the city. */
	private final class Storm extends Event {
		Vec3 at;

		Storm() {
			super("storm", 30 * 20);
		}

		@Override
		void start() {
			at = somewhere(0, 45);
		}

		@Override
		void tick(int age) {
			if (age % 12 != 0) return;
			double a = host.rng().nextDouble() * Math.PI * 2, r = host.rng().nextDouble() * 18;
			Vec3 hit = ground(at.x + Math.cos(a) * r, at.z + Math.sin(a) * r, at.y);
			// a tribute in the storm is struck now and then
			for (ServerPlayer p : host.tributes()) {
				if (horizontal(p.position(), at) < 18 && host.rng().nextInt(6) == 0) hit = p.position();
			}
			Entity e = Holo.create(level(), "minecraft:lightning_bolt", Entity.class);
			if (e instanceof LightningBolt bolt) {
				bolt.setVisualOnly(true);
				bolt.snapTo(hit.x, hit.y, hit.z);
				level().addFreshEntity(bolt);
			}
			level().playSound(null, hit.x, hit.y, hit.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, 2f, 0.9f);
			for (ServerPlayer p : host.tributes()) {
				if (p.position().distanceTo(hit) > 3) continue;
				p.hurtServer(level(), level().damageSources().lightningBolt(), 5f);
				p.igniteForSeconds(2f);
			}
		}

		@Override
		Vec3 danger(Vec3 p) {
			return horizontal(p, at) < 22 ? at : null;
		}
	}

	/** Crates of supplies drop on parachutes all over the city. */
	private final class Supplies extends Event {
		Supplies() {
			super("supplies", 20);
		}

		@Override
		void start() {
			for (int i = 0; i < 5; i++) capitol.sponsors().drop(somewhere(8, 70), "supply", null);
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
	}

	/** Rain that burns whoever is not under a roof. The rain falls for the match's players only. */
	private final class AcidRain extends Event {
		AcidRain() {
			super("acid_rain", 30 * 20);
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
			if (age % 30 != 0) return;
			for (ServerPlayer p : host.tributes()) {
				if (!level().canSeeSky(BlockPos.containing(p.getX(), p.getEyeY(), p.getZ()))) continue;
				p.hurtServer(level(), level().damageSources().magic(), 1f);
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
	}

	/** Strange voices and whispered names, and an arrow to the nearest tribute. */
	private final class Jabberjays extends Event {
		Jabberjays() {
			super("jabberjays", 45 * 20);
		}

		@Override
		void tick(int age) {
			for (ServerPlayer p : host.tributes()) {
				if (age % 5 == 0) capitol.powers().pointer(p, "hg.jabberjays.pointer");
				if (host.rng().nextInt(70) != 0) continue;
				double a = host.rng().nextDouble() * Math.PI * 2, r = 6 + host.rng().nextInt(7);
				net.minecraft.sounds.SoundEvent[] voices = {SoundEvents.PARROT_AMBIENT, SoundEvents.FOX_SCREECH, SoundEvents.GOAT_SCREAMING_AMBIENT,
						SoundEvents.ENDERMAN_AMBIENT, SoundEvents.VILLAGER_AMBIENT};
				level().playSound(null, p.getX() + Math.cos(a) * r, p.getY() + 1, p.getZ() + Math.sin(a) * r,
						voices[host.rng().nextInt(voices.length)], SoundSource.HOSTILE, 1f, 0.6f + host.rng().nextFloat() * 0.5f);
				List<ServerPlayer> others = host.tributes();
				ServerPlayer o = others.get(host.rng().nextInt(others.size()));
				if (o != p) Msg.send(p, "hg.jabberjays.whisper", host.name(o.getUUID()));
			}
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
		final int until;
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
		Swarm s = new Swarm(at, owner, host.runTicks() + ticks);
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
		final int until;
		final List<Entity> bees = new ArrayList<>();
		final Map<UUID, Integer> stung = new HashMap<>();

		Swarm(Vec3 at, UUID owner, int until) {
			this.at = at;
			this.owner = owner;
			this.until = until;
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
				if (len > 0.01) at = at.add(to.scale(Math.min(len, 0.24) / len));
				if (len < 1.8 && t - stung.getOrDefault(target.getUUID(), -100) > 25) {
					stung.put(target.getUUID(), t);
					target.hurtServer(level(), level().damageSources().sting(null), 2f);
					effect(target, "minecraft:poison", 100, 0);
					effect(target, "minecraft:nausea", 140, 0);
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
}
