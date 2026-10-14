package net.battlehub.progression;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import net.battlehub.BattleHub;
import net.battlehub.config.ConfigManager;
import net.battlehub.core.CosmeticDef;
import net.battlehub.core.CosmeticRules;
import net.battlehub.core.PlayerProfile;
import net.battlehub.data.PlayerStore;
import net.battlehub.hub.HubWorld;
import net.battlehub.lang.Lang;
import net.battlehub.match.Match;
import net.battlehub.match.Matches;
import net.battlehub.mixin.ArrowAccessor;
import net.battlehub.util.Ids;
import net.battlehub.util.Msg;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.TeamColor;

/**
 * Cosmetics bought with coins: particle trails while walking, arrow trails, an effect where you defeat someone, a
 * celebration when you win and a name colour. Effects only play in BattleHub's own worlds and are purely visual.
 */
public final class Cosmetics {
	private Cosmetics() {}

	public enum ClickResult { SELECTED, DESELECTED, BOUGHT, TOO_EXPENSIVE }

	public static final List<String> KILL_EFFECTS = List.of("lightning", "explosion", "hearts", "flames", "souls", "blood", "firework", "totem");
	public static final List<String> WIN_EFFECTS = List.of("fireworks", "totem", "hearts", "notes", "flames", "sparks");

	private static final Map<UUID, Vec3> LAST_POS = new HashMap<>();
	private static final Map<String, Optional<ParticleOptions>> PARTICLES = new HashMap<>();
	/** Particle ids the built-in kill and win effects use (checked by the self-test). */
	private static final Set<String> USED = new HashSet<>();
	private static boolean failed;

	public static List<CosmeticDef> all() {
		return ConfigManager.cosmetics().cosmetics;
	}

	public static List<CosmeticDef> inCategory(String category) {
		List<CosmeticDef> out = new ArrayList<>();
		for (CosmeticDef c : all()) {
			if (c.category().equals(category)) out.add(c);
		}
		return out;
	}

	public static CosmeticDef byId(String id) {
		for (CosmeticDef c : all()) {
			if (c.id.equals(id)) return c;
		}
		return null;
	}

	public static CosmeticDef active(ServerPlayer p, String category) {
		return CosmeticRules.active(PlayerStore.get(p), category, all());
	}

	// ------------------------------------------------------------------ shop

	/** A click in the shop: buy (and wear) a cosmetic, wear an owned one, or take off the one you wear. */
	public static ClickResult click(ServerPlayer p, CosmeticDef c) {
		PlayerProfile prof = PlayerStore.get(p);
		ClickResult result;
		CosmeticDef current = CosmeticRules.active(prof, c.category(), all());
		if (current != null && current.id.equals(c.id)) {
			CosmeticRules.clear(prof, c.category());
			Msg.send(p, "cosmetics.deselected", Lang.colored(c.display));
			result = ClickResult.DESELECTED;
		} else if (CosmeticRules.owns(prof, c)) {
			CosmeticRules.select(prof, c);
			Msg.send(p, "cosmetics.selected", Lang.colored(c.display));
			Msg.sound(p, SoundEvents.EXPERIENCE_ORB_PICKUP, 0.7f, 1.2f);
			result = ClickResult.SELECTED;
		} else if (CosmeticRules.buy(prof, c) == CosmeticRules.BuyResult.BOUGHT) {
			CosmeticRules.select(prof, c);
			Msg.send(p, "cosmetics.bought", Lang.colored(c.display), c.price);
			Msg.sound(p, SoundEvents.PLAYER_LEVELUP, 0.7f, 1.4f);
			result = ClickResult.BOUGHT;
		} else {
			Msg.send(p, "cosmetics.too_expensive", c.price, prof.coins);
			Msg.sound(p, SoundEvents.VILLAGER_NO, 0.7f, 1.0f);
			return ClickResult.TOO_EXPENSIVE;
		}
		PlayerStore.save(p.getUUID());
		if (c.category().equals("color")) {
			TitleService.apply(p);
		}
		if (result != ClickResult.DESELECTED && !c.category().equals("color")) {
			preview(p, c);
		}
		return result;
	}

	public static void clear(ServerPlayer p, String category) {
		CosmeticRules.clear(PlayerStore.get(p), category);
		PlayerStore.save(p.getUUID());
		if (category.equals("color")) {
			TitleService.apply(p);
		}
	}

	/** A short look at the effect right after choosing it. */
	private static void preview(ServerPlayer p, CosmeticDef c) {
		ServerLevel level = p.level();
		if (!HubWorld.isBattleHub(level)) {
			return;
		}
		Vec3 at = p.position().add(p.getLookAngle().multiply(2, 0, 2));
		switch (c.category()) {
			case "trail", "arrow" -> burst(level, c.effect, at.x, at.y + 0.5, at.z, 20, 0.4, 0.4, 0.4, 0.02);
			case "kill" -> killEffect(c.effect, level, at);
			case "win" -> winEffect(c.effect, level, p.position(), 0);
			default -> { }
		}
	}

	// ------------------------------------------------------------------ name colour

	/** The chosen name colour, or null. */
	public static TeamColor nameColor(PlayerProfile prof) {
		CosmeticDef c = CosmeticRules.active(prof, "color", all());
		return c == null ? null : teamColor(c.effect);
	}

	/** A team colour by name (red, gold, dark_aqua, light_purple, ...), or null. */
	public static TeamColor teamColor(String name) {
		if (name == null) {
			return null;
		}
		for (TeamColor t : TeamColor.values()) {
			if (t.getSerializedName().equalsIgnoreCase(name.trim()) || t.name().equalsIgnoreCase(name.trim())) {
				return t;
			}
		}
		return null;
	}

	// ------------------------------------------------------------------ effects

	/** Particle type by id, only simple ones (no extra options needed); null when unknown. */
	public static ParticleOptions particle(String id) {
		if (id == null || id.isBlank()) {
			return null;
		}
		return PARTICLES.computeIfAbsent(id, k -> {
			Identifier rl = Identifier.tryParse(k.trim());
			if (rl == null || !BuiltInRegistries.PARTICLE_TYPE.containsKey(rl)) {
				return Optional.empty();
			}
			ParticleType<?> t = BuiltInRegistries.PARTICLE_TYPE.getValue(rl);
			return t instanceof SimpleParticleType s ? Optional.<ParticleOptions>of(s) : Optional.<ParticleOptions>empty();
		}).orElse(null);
	}

	private static void burst(ServerLevel level, String particle, double x, double y, double z, int count, double dx, double dy, double dz, double speed) {
		USED.add(particle);
		ParticleOptions o = particle(particle);
		if (o != null) {
			level.sendParticles(o, x, y, z, count, dx, dy, dz, speed);
		}
	}

	private static void ring(ServerLevel level, String particle, Vec3 c, double radius, int points, double y, double phase) {
		for (int i = 0; i < points; i++) {
			double a = phase + i * Math.PI * 2 / points;
			burst(level, particle, c.x + Math.cos(a) * radius, c.y + y, c.z + Math.sin(a) * radius, 1, 0, 0, 0, 0);
		}
	}

	private static void sound(ServerLevel level, Vec3 at, SoundEvent s, float volume, float pitch) {
		level.playSound(null, at.x, at.y, at.z, s, SoundSource.PLAYERS, volume, pitch);
	}

	private static void sound(ServerLevel level, Vec3 at, Holder<SoundEvent> s, float volume, float pitch) {
		level.playSound(null, at.x, at.y, at.z, s, SoundSource.PLAYERS, volume, pitch);
	}

	/** Unknown particles, effects, colours or icons in the effects and the cosmetics list (should be empty). */
	public static List<String> problems() {
		List<String> out = new ArrayList<>();
		for (String id : USED) {
			if (particle(id) == null) out.add("particle " + id);
		}
		for (CosmeticDef c : all()) {
			boolean ok = switch (c.category()) {
				case "trail", "arrow" -> particle(c.effect) != null;
				case "kill" -> KILL_EFFECTS.contains(c.effect);
				case "win" -> WIN_EFFECTS.contains(c.effect);
				case "color" -> teamColor(c.effect) != null;
				default -> false;
			};
			if (!ok) out.add(c.id + " effect " + c.effect);
			if (!Ids.isItem(c.icon)) out.add(c.id + " icon " + c.icon);
		}
		return out;
	}

	/** Called from the match when {@code killer} gets the kill on {@code victim}, standing at {@code at}. */
	public static void onKill(ServerPlayer killer, ServerPlayer victim, Vec3 at) {
		ServerLevel level = victim.level();
		if (!HubWorld.isBattleHub(level)) {
			return;
		}
		CosmeticDef c = active(killer, "kill");
		if (c != null) {
			killEffect(c.effect, level, at);
		}
	}

	public static void killEffect(String effect, ServerLevel level, Vec3 at) {
		double x = at.x;
		double y = at.y;
		double z = at.z;
		switch (effect) {
			case "lightning" -> {
				for (int i = 0; i < 16; i++) {
					burst(level, "minecraft:electric_spark", x, y + i * 0.75, z, 8, 0.12, 0.3, 0.12, 0.05);
				}
				burst(level, "minecraft:end_rod", x, y + 1, z, 30, 0.3, 1.0, 0.3, 0.08);
				sound(level, at, SoundEvents.LIGHTNING_BOLT_THUNDER, 0.5f, 1.3f);
				sound(level, at, SoundEvents.LIGHTNING_BOLT_IMPACT, 0.8f, 1.0f);
			}
			case "explosion" -> {
				burst(level, "minecraft:explosion", x, y + 1, z, 3, 0.5, 0.5, 0.5, 0);
				burst(level, "minecraft:large_smoke", x, y + 1, z, 20, 0.4, 0.4, 0.4, 0.05);
				sound(level, at, SoundEvents.GENERIC_EXPLODE, 0.6f, 1.3f);
			}
			case "hearts" -> {
				burst(level, "minecraft:heart", x, y + 1.2, z, 14, 0.6, 0.5, 0.6, 0.1);
				burst(level, "minecraft:damage_indicator", x, y + 1, z, 10, 0.4, 0.4, 0.4, 0.1);
				sound(level, at, SoundEvents.PLAYER_LEVELUP, 0.4f, 1.8f);
			}
			case "flames" -> {
				burst(level, "minecraft:flame", x, y + 1, z, 40, 0.3, 0.6, 0.3, 0.08);
				burst(level, "minecraft:lava", x, y + 0.5, z, 8, 0.3, 0.2, 0.3, 0);
				sound(level, at, SoundEvents.FIRECHARGE_USE, 0.6f, 1.0f);
			}
			case "souls" -> {
				burst(level, "minecraft:soul", x, y + 0.6, z, 16, 0.4, 0.6, 0.4, 0.05);
				burst(level, "minecraft:soul_fire_flame", x, y + 0.3, z, 20, 0.5, 0.1, 0.5, 0.02);
				sound(level, at, SoundEvents.SOUL_ESCAPE, 1.0f, 0.9f);
			}
			case "blood" -> {
				burst(level, "minecraft:damage_indicator", x, y + 1, z, 20, 0.3, 0.5, 0.3, 0.15);
				burst(level, "minecraft:crimson_spore", x, y + 1, z, 60, 0.4, 0.6, 0.4, 0.0);
				sound(level, at, SoundEvents.PLAYER_HURT, 0.7f, 0.6f);
			}
			case "firework" -> {
				burst(level, "minecraft:firework", x, y + 1.5, z, 60, 0.2, 0.2, 0.2, 0.25);
				burst(level, "minecraft:flash", x, y + 1.5, z, 1, 0, 0, 0, 0);
				sound(level, at, SoundEvents.FIREWORK_ROCKET_BLAST, 1.0f, 1.0f);
				sound(level, at, SoundEvents.FIREWORK_ROCKET_TWINKLE, 0.8f, 1.0f);
			}
			case "totem" -> {
				burst(level, "minecraft:totem_of_undying", x, y + 1, z, 60, 0.4, 0.8, 0.4, 0.4);
				sound(level, at, SoundEvents.TOTEM_USE, 0.4f, 1.2f);
			}
			default -> { }
		}
	}

	/** Every 10 ticks while a match ends: the winners celebrate with their win effect. */
	public static void celebrate(ServerPlayer winner, int ticks) {
		ServerLevel level = winner.level();
		if (!HubWorld.isBattleHub(level)) {
			return;
		}
		CosmeticDef c = active(winner, "win");
		if (c != null) {
			winEffect(c.effect, level, winner.position(), ticks);
		}
	}

	public static void winEffect(String effect, ServerLevel level, Vec3 at, int ticks) {
		double phase = ticks * 0.15;
		java.util.concurrent.ThreadLocalRandom r = java.util.concurrent.ThreadLocalRandom.current();
		switch (effect) {
			case "fireworks" -> {
				double fx = at.x + r.nextDouble(-2.5, 2.5);
				double fz = at.z + r.nextDouble(-2.5, 2.5);
				burst(level, "minecraft:firework", fx, at.y + 3 + r.nextDouble(2), fz, 40, 0.1, 0.1, 0.1, 0.2);
				if (ticks % 20 == 0) {
					sound(level, at, SoundEvents.FIREWORK_ROCKET_BLAST, 0.8f, 1.0f);
				} else {
					sound(level, at, SoundEvents.FIREWORK_ROCKET_TWINKLE, 0.6f, 1.0f);
				}
			}
			case "totem" -> {
				burst(level, "minecraft:totem_of_undying", at.x, at.y + 2.5, at.z, 25, 0.8, 0.3, 0.8, 0.3);
				if (ticks % 40 == 0) sound(level, at, SoundEvents.TOTEM_USE, 0.3f, 1.4f);
			}
			case "hearts" -> {
				ring(level, "minecraft:heart", at, 1.4, 8, 1.0, phase);
				burst(level, "minecraft:heart", at.x, at.y + 2.3, at.z, 3, 0.3, 0.2, 0.3, 0);
			}
			case "notes" -> {
				ring(level, "minecraft:note", at, 1.3, 8, 1.2, phase);
				sound(level, at, SoundEvents.NOTE_BLOCK_BELL, 0.6f, 0.6f + r.nextFloat() * 1.2f);
			}
			case "flames" -> {
				ring(level, "minecraft:flame", at, 1.6, 20, 0.1, phase);
				ring(level, "minecraft:flame", at, 1.6, 10, 1.0, -phase);
				if (ticks % 30 == 0) sound(level, at, SoundEvents.FIRECHARGE_USE, 0.4f, 1.2f);
			}
			case "sparks" -> {
				for (int i = 0; i < 12; i++) {
					double a = phase + i * 0.5;
					burst(level, "minecraft:end_rod", at.x + Math.cos(a) * 1.2, at.y + i * 0.2, at.z + Math.sin(a) * 1.2, 1, 0, 0, 0, 0);
				}
				sound(level, at, SoundEvents.AMETHYST_BLOCK_CHIME, 0.8f, 1.0f + r.nextFloat());
			}
			default -> { }
		}
	}

	/** Trails behind moving players (every 2 ticks) and behind arrows in flight. */
	public static void tick(MinecraftServer server, int tick) {
		if (tick % 2 != 0) {
			return;
		}
		try {
			trails(server);
			arrows(server);
		} catch (RuntimeException e) {
			if (!failed) {
				failed = true;
				BattleHub.LOGGER.error("Cosmetic effects failed", e);
			}
		}
	}

	private static void trails(MinecraftServer server) {
		boolean inMatches = ConfigManager.cosmetics().trailsInMatches;
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			ServerLevel level = p.level();
			if (p.isSpectator() || p.isInvisible() || !HubWorld.isBattleHub(level)) {
				LAST_POS.remove(p.getUUID());
				continue;
			}
			Vec3 pos = p.position();
			Vec3 last = LAST_POS.put(p.getUUID(), pos);
			if (last == null || last.distanceToSqr(pos) < 0.004 || last.distanceToSqr(pos) > 64) {
				continue;
			}
			if (!inMatches && Matches.of(p.getUUID()) != null) {
				continue;
			}
			CosmeticDef c = active(p, "trail");
			if (c != null) {
				burst(level, c.effect, pos.x, pos.y + 0.15, pos.z, 2, 0.15, 0.05, 0.15, 0.0);
			}
		}
	}

	private static void arrows(MinecraftServer server) {
		for (Match m : Matches.all()) {
			if (m.phase() != Match.Phase.RUNNING || m.arena() == null) continue;
			Map<UUID, CosmeticDef> trails = new HashMap<>();
			for (UUID id : m.participants()) {
				ServerPlayer p = server.getPlayerList().getPlayer(id);
				CosmeticDef c = p == null ? null : active(p, "arrow");
				if (c != null) trails.put(id, c);
			}
			if (trails.isEmpty()) continue;
			ServerLevel level = m.arena().level();
			for (AbstractArrow a : level.getEntitiesOfClass(AbstractArrow.class, m.arena().bounds())) {
				if (a.getOwner() == null || ((ArrowAccessor) (Object) a).battlehub$isInGround()) continue;
				CosmeticDef c = trails.get(a.getOwner().getUUID());
				if (c != null) {
					burst(level, c.effect, a.getX(), a.getY(), a.getZ(), 2, 0.05, 0.05, 0.05, 0.0);
				}
			}
		}
	}

	public static void forget(UUID id) {
		LAST_POS.remove(id);
	}
}
