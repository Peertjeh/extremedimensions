package net.battlehub.hub;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.battlehub.BattleHub;
import net.battlehub.config.ConfigManager;
import net.battlehub.config.HubConfig;
import net.battlehub.core.BotMath;
import net.battlehub.core.DummyHits;
import net.battlehub.fx.Glyphs;
import net.battlehub.fx.Sounds;
import net.battlehub.lang.Lang;
import net.battlehub.mixin.InteractionAccessor;
import net.battlehub.util.Ids;
import net.battlehub.util.Msg;
import net.battlehub.util.Tags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundHurtAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Interaction;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A training dummy on the plaza. Every player sees their own numbers above it: what the last hit would have
 * done with a diamond sword (the attack cooldown counts, critical hits too), the combo, clicks per second,
 * reach and the average. A number floats up from every hit. Right-click starts over.
 */
public final class HubDummy {
	private HubDummy() {}

	public static final String TAG = "battlehub_dummy";
	private static final double LABEL_HEIGHT = 2.35;

	private record Popup(UUID viewer, int id, long until) {}

	private static ClientEntities.Handle body;
	private static ClientEntities.Handle label;
	private static EntityType<?> bodyType;
	private static Vec3 pos;
	private static float yaw;
	private static Interaction hitbox;
	private static final HubViewers VIEWERS = new HubViewers();
	private static final Map<UUID, DummyHits> HITS = new HashMap<>();
	private static final List<Popup> POPUPS = new ArrayList<>();
	private static HubConfig builtFor;
	private static long ticks;

	public static void tick(MinecraftServer server) {
		ticks++;
		HubConfig cfg = ConfigManager.config();
		ServerLevel hub = server.getLevel(HubWorld.KEY);
		if (cfg != builtFor) {
			VIEWERS.clear(HubDummy::remove);
			removeHitbox();
			build(cfg, hub);
		}
		boolean active = hub != null && HubWorld.ready() && body != null;
		List<HubViewers.Viewer> fresh = VIEWERS.update(server, hub, active, HubDummy::remove);
		if (hub != null && ticks % 100 == 0) {
			sweep(hub, active);
		}
		expirePopups(server);
		if (!active) {
			return;
		}
		for (HubViewers.Viewer v : VIEWERS.all()) {
			boolean isNew = fresh.contains(v);
			if (isNew) {
				spawn(v.player);
			}
			if (isNew || ticks % 10 == 0) {
				label(v);
			}
		}
	}

	public static void rebuild() {
		builtFor = null;
	}

	public static void clear() {
		body = null;
		label = null;
		hitbox = null;
		HITS.clear();
		POPUPS.clear();
		VIEWERS.forget();
		builtFor = null;
	}

	private static void build(HubConfig cfg, ServerLevel hub) {
		builtFor = cfg;
		body = null;
		if (hub == null || !cfg.lobbyExtras.dummy) {
			return;
		}
		bodyType = BuiltInRegistries.ENTITY_TYPE.containsKey(Ids.id("minecraft:mannequin"))
				? ClientEntities.type("minecraft:mannequin") : ClientEntities.type("minecraft:armor_stand");
		BlockPos o = HubWorld.origin();
		Vec3 spawn = HubWorld.defaultSpawn();
		double a = Math.toRadians(cfg.lobbyExtras.dummyAngle);
		double x = o.getX() + 0.5 + Math.cos(a) * cfg.lobbyExtras.dummyRadius;
		double z = o.getZ() + 0.5 + Math.sin(a) * cfg.lobbyExtras.dummyRadius;
		pos = new Vec3(x, o.getY() + 1, z);
		yaw = BotMath.yaw(spawn.x - x, spawn.z - z);
		body = ClientEntities.handle(hub, bodyType);
		label = ClientEntities.handle(hub, ClientEntities.type("minecraft:text_display"));
		if (body == null || label == null) {
			BattleHub.LOGGER.warn("Training dummy: could not create entities");
			body = null;
		}
	}

	// ------------------------------------------------------------------ the click box (a real entity)

	private static void sweep(ServerLevel hub, boolean active) {
		for (Interaction e : hub.getEntities(EntityTypeTest.forClass(Interaction.class), HubDummy::stray)) {
			if (e != hitbox) {
				e.discard();
			}
		}
		if (!active || VIEWERS.isEmpty() || (hitbox != null && !hitbox.isRemoved()) || !hub.isLoaded(BlockPos.containing(pos))) {
			return;
		}
		EntityType<?> type = ClientEntities.type("minecraft:interaction");
		Entity e = type == null ? null : type.create(hub, EntitySpawnReason.COMMAND);
		if (!(e instanceof Interaction i)) {
			return;
		}
		((InteractionAccessor) i).battlehub$setWidth(0.9f);
		((InteractionAccessor) i).battlehub$setHeight(2.0f);
		i.snapTo(pos.x, pos.y, pos.z);
		i.addTag(TAG);
		hub.addFreshEntity(i);
		hitbox = i;
	}

	private static boolean stray(Interaction e) {
		Boolean tagged = Tags.has(e, TAG);
		if (tagged != null) {
			return tagged;
		}
		return pos != null && e.position().distanceToSqr(pos) < 0.04;
	}

	private static void removeHitbox() {
		if (hitbox != null) {
			hitbox.discard();
		}
		hitbox = null;
	}

	private static boolean isDummy(Entity e) {
		return e instanceof Interaction i && body != null && (i == hitbox || stray(i));
	}

	/** A hit on the dummy; true when it was the dummy (the attack itself is cancelled). */
	public static boolean hit(ServerPlayer p, Entity e) {
		if (!isDummy(e)) {
			return false;
		}
		float strength = p.getAttackStrengthScale(0.5f);
		boolean crit = DummyHits.crit(strength, p.fallDistance, p.onGround(), p.onClimbable(), p.isInWater(),
				p.hasEffect(MobEffects.BLINDNESS), p.isPassenger(), p.isSprinting());
		AABB box = e.getBoundingBox();
		Vec3 eye = p.getEyePosition();
		Vec3 near = new Vec3(Math.max(box.minX, Math.min(eye.x, box.maxX)), Math.max(box.minY, Math.min(eye.y, box.maxY)),
				Math.max(box.minZ, Math.min(eye.z, box.maxZ)));
		DummyHits d = HITS.computeIfAbsent(p.getUUID(), k -> new DummyHits());
		double dmg = d.hit(ticks, strength, crit, eye.distanceTo(near));
		p.resetAttackStrengthTicker();
		p.connection.send(new ClientboundHurtAnimationPacket(body.id(), p.getYRot()));
		Msg.sound(p, crit ? SoundEvents.PLAYER_ATTACK_CRIT : strength > 0.9f ? SoundEvents.PLAYER_ATTACK_STRONG
				: SoundEvents.PLAYER_ATTACK_WEAK, 0.8f, 1f);
		Sounds.play(p, Sounds.DUMMY);
		if (crit) {
			p.level().sendParticles(ParticleTypes.CRIT, pos.x, pos.y + 1.2, pos.z, 10, 0.3, 0.4, 0.3, 0.1);
		}
		popup(p, dmg, crit);
		HubViewers.Viewer v = VIEWERS.get(p.getUUID());
		if (v != null) {
			label(v);
		}
		return true;
	}

	/** A right-click on the dummy starts the numbers over; true when it was the dummy. */
	public static boolean use(ServerPlayer p, Entity e) {
		if (!isDummy(e)) {
			return false;
		}
		if (HITS.remove(p.getUUID()) != null) {
			Msg.send(p, "dummy.reset");
			HubViewers.Viewer v = VIEWERS.get(p.getUUID());
			if (v != null) label(v);
		}
		return true;
	}

	/** Arm swings count as clicks for players who are using the dummy. */
	public static void swing(ServerPlayer p) {
		DummyHits d = HITS.get(p.getUUID());
		if (d != null) {
			d.click(ticks);
		}
	}

	public static void forget(UUID id) {
		HITS.remove(id);
	}

	// ------------------------------------------------------------------ what the clients see

	private static void spawn(ServerPlayer p) {
		ClientEntities.add(p, body, bodyType, pos, yaw, yaw);
		ClientEntities.holding(p, body.id(), new net.minecraft.world.item.ItemStack(Ids.item("minecraft:target")));
		ClientEntities.add(p, label, ClientEntities.type("minecraft:text_display"), pos.add(0, LABEL_HEIGHT, 0), 0f, 0f);
		ClientEntities.textStyle(p, label.id(), 220, 0x50000000, ClientEntities.SHADOW, 0.9f, ClientEntities.BILLBOARD_CENTER);
	}

	private static void remove(ServerPlayer p) {
		ClientEntities.remove(p, new int[] {body.id(), label.id()});
	}

	private static void label(HubViewers.Viewer v) {
		ServerPlayer p = v.player;
		DummyHits d = HITS.get(p.getUUID());
		MutableComponent text = Component.empty().append(Lang.tr(p, "dummy.title"));
		if (d == null || d.hits == 0) {
			text.append("\n").append(Lang.tr(p, "dummy.hint"));
		} else {
			text.append("\n").append(Lang.tr(p, "dummy.last", String.format(java.util.Locale.ROOT, "%.1f", d.lastDamage),
					d.comboOver(ticks) ? 0 : d.combo, d.bestCombo));
			text.append("\n").append(Lang.tr(p, "dummy.cps", String.format(java.util.Locale.ROOT, "%.0f", d.cps(ticks)),
					String.format(java.util.Locale.ROOT, "%.0f", d.bestCps), String.format(java.util.Locale.ROOT, "%.2f", d.lastReach)));
			text.append("\n").append(Lang.tr(p, "dummy.totals", d.hits, d.crits,
					String.format(java.util.Locale.ROOT, "%.1f", d.average())));
			text.append("\n").append(Lang.tr(p, "dummy.reset_hint"));
		}
		Component out = text;
		if (!out.equals(v.sent.get(label.id()))) {
			v.sent.put(label.id(), out);
			ClientEntities.text(p, label.id(), out);
		}
	}

	/** A number that floats up from the dummy and fades after a moment (only for the one who hit). */
	private static void popup(ServerPlayer p, double dmg, boolean crit) {
		if (VIEWERS.get(p.getUUID()) == null) {
			return;
		}
		ClientEntities.Handle h = ClientEntities.handle((ServerLevel) p.level(), ClientEntities.type("minecraft:text_display"));
		if (h == null) {
			return;
		}
		double ox = (p.getRandom().nextDouble() - 0.5) * 0.8;
		double oz = (p.getRandom().nextDouble() - 0.5) * 0.8;
		Vec3 at = pos.add(ox, 1.6, oz);
		ClientEntities.add(p, h, ClientEntities.type("minecraft:text_display"), at, 0f, 0f);
		ClientEntities.textStyle(p, h.id(), 200, 0, ClientEntities.SHADOW, crit ? 1.3f : 1.0f, ClientEntities.BILLBOARD_CENTER);
		String amount = String.format(java.util.Locale.ROOT, "%.1f", dmg);
		ClientEntities.text(p, h.id(), crit ? Lang.tr(p, "dummy.popup_crit", amount, Glyphs.icon(p, "star"))
				: Lang.tr(p, "dummy.popup", amount, Glyphs.icon(p, "heart")));
		ClientEntities.glide(p, h.id(), 14);
		ClientEntities.move(p, h.id(), at.add(0, 0.7, 0), 0f, 0f);
		POPUPS.add(new Popup(p.getUUID(), h.id(), ticks + 16));
	}

	private static void expirePopups(MinecraftServer server) {
		for (Iterator<Popup> it = POPUPS.iterator(); it.hasNext();) {
			Popup pop = it.next();
			if (pop.until() > ticks) continue;
			it.remove();
			ServerPlayer p = server.getPlayerList().getPlayer(pop.viewer());
			if (p != null && VIEWERS.get(pop.viewer()) != null) {
				ClientEntities.remove(p, new int[] {pop.id()});
			}
		}
	}

	// For the self-test

	public static Interaction hitbox() {
		return hitbox;
	}

	public static DummyHits stats(UUID id) {
		return HITS.get(id);
	}

	public static boolean showing(UUID viewer) {
		return VIEWERS.get(viewer) != null;
	}

	public static int popups() {
		return POPUPS.size();
	}
}
