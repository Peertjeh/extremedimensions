package net.battlehub.progression;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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

	public static final List<String> KILL_EFFECTS = List.of("lightning", "explosion", "hearts", "flames", "souls", "blood", "firework", "totem",
			"smite", "rocket", "frost", "sculk", "gust", "confetti", "ender", "cherry", "sonic", "squid", "bubbles", "skull", "gold", "notes");
	public static final List<String> WIN_EFFECTS = List.of("fireworks", "totem", "hearts", "notes", "flames", "sparks",
			"rockets", "storm", "rainbow", "dragon", "galaxy", "snow", "confetti", "aurora", "sakura", "souls", "gold_rain");
	/** Sounds the effects play by id (checked by the self-test). */
	private static final Set<String> USED_SOUNDS = new HashSet<>();

	private static final Map<UUID, Vec3> LAST_POS = new HashMap<>();
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
		if (CosmeticRules.WARDROBE.contains(c.category())) {
			Wardrobe.apply(p);
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
		if (CosmeticRules.WARDROBE.contains(category)) {
			Wardrobe.apply(p);
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
			case "trail", "arrow" -> Particles.emit(level, c.effect, at.x, at.y + 0.5, at.z, 20, 0.4, 0.4, 0.4, 0.02, 0);
			case "aura" -> {
				for (int f = 0; f < 4; f++) Auras.draw(level, c.effect, at, p.getYRot() + 180, f);
			}
			case "kill" -> killEffect(c.effect, level, at);
			case "win" -> winEffect(c.effect, level, p.position(), 0);
			case "cry" -> cry(level, c.effect, p.position());
			case "skin" -> Msg.send(p, "cosmetics.skin_preview", Lang.colored(c.display));
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

	/** Plain particle by id; null when unknown. */
	public static ParticleOptions particle(String id) {
		return id == null || id.isBlank() ? null : Particles.simple(id.trim());
	}

	/** A particle text (see {@link net.battlehub.core.ParticleSpec}) at one spot. */
	private static void burst(ServerLevel level, String particle, double x, double y, double z, int count, double dx, double dy, double dz, double speed) {
		USED.add(particle);
		Particles.emit(level, particle, x, y, z, count, dx, dy, dz, speed, level.getGameTime());
	}

	/** Coloured dust (RGB) at one spot. */
	private static void dust(ServerLevel level, int rgb, float scale, double x, double y, double z, int count, double spread) {
		level.sendParticles(new net.minecraft.core.particles.DustParticleOptions(rgb, scale), x, y, z, count, spread, spread, spread, 0);
	}

	private static int hue(double h) {
		return net.battlehub.core.ParticleSpec.hsb((float) h, 0.85f, 1f);
	}

	/** A sound by id ("entity.enderman.teleport"), if this version has it. */
	private static void sound(ServerLevel level, Vec3 at, String id, float volume, float pitch) {
		USED_SOUNDS.add(id);
		Identifier rl = Ids.id(id);
		if (BuiltInRegistries.SOUND_EVENT.containsKey(rl)) {
			level.playSound(null, at.x, at.y, at.z, BuiltInRegistries.SOUND_EVENT.getValue(rl), SoundSource.PLAYERS, volume, pitch);
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
			if (!Particles.valid(id)) out.add("particle " + id);
		}
		for (String id : USED_SOUNDS) {
			if (!BuiltInRegistries.SOUND_EVENT.containsKey(Ids.id(id))) out.add("sound " + id);
		}
		for (CosmeticDef c : all()) {
			boolean ok = switch (c.category()) {
				case "trail", "arrow" -> Particles.valid(c.effect);
				case "aura" -> Auras.valid(c.effect);
				case "kill" -> KILL_EFFECTS.contains(c.effect);
				case "win" -> WIN_EFFECTS.contains(c.effect);
				case "cry" -> BuiltInRegistries.SOUND_EVENT.containsKey(Ids.id(CosmeticRules.crySound(c.effect)));
				case "skin" -> Ids.isItem(CosmeticRules.skinModel(c.effect));
				case "pet", "hat" -> Ids.isItem(c.effect);
				case "color" -> teamColor(c.effect) != null;
				case "outfit" -> CosmeticRules.outfitColors(c.effect) != null;
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
				burst(level, "minecraft:end_rod", x, y + 1.5, z, 12, 0.1, 0.1, 0.1, 0.15);
				sound(level, at, SoundEvents.FIREWORK_ROCKET_BLAST, 1.0f, 1.0f);
				sound(level, at, SoundEvents.FIREWORK_ROCKET_TWINKLE, 0.8f, 1.0f);
			}
			case "totem" -> {
				burst(level, "minecraft:totem_of_undying", x, y + 1, z, 60, 0.4, 0.8, 0.4, 0.4);
				sound(level, at, SoundEvents.TOTEM_USE, 0.4f, 1.2f);
			}
			case "smite" -> {
				net.battlehub.fx.Spectacle.lightning(level, at);
				burst(level, "minecraft:electric_spark", x, y + 1, z, 30, 0.3, 0.8, 0.3, 0.1);
			}
			case "rocket" -> {
				java.util.Random r = new java.util.Random();
				net.battlehub.fx.Spectacle.randomFirework(level, at.add(0, 1.4, 0), r);
			}
			case "frost" -> {
				burst(level, "minecraft:snowflake", x, y + 1, z, 40, 0.4, 0.6, 0.4, 0.05);
				burst(level, "block:minecraft:packed_ice", x, y + 1, z, 30, 0.3, 0.5, 0.3, 0.1);
				burst(level, "dust:#E0FFFF:1.3", x, y + 1, z, 20, 0.5, 0.6, 0.5, 0);
				sound(level, at, "block.glass.break", 1.0f, 1.2f);
			}
			case "sculk" -> {
				burst(level, "minecraft:sculk_soul", x, y + 0.8, z, 15, 0.4, 0.5, 0.4, 0.05);
				burst(level, "minecraft:sculk_charge_pop", x, y + 0.3, z, 25, 0.6, 0.2, 0.6, 0.02);
				sound(level, at, "block.sculk_shrieker.shriek", 0.6f, 1.1f);
			}
			case "gust" -> {
				burst(level, "minecraft:gust_emitter_small", x, y + 1, z, 1, 0, 0, 0, 0);
				burst(level, "minecraft:small_gust", x, y + 1, z, 10, 0.6, 0.5, 0.6, 0);
				sound(level, at, "entity.wind_charge.wind_burst", 1.0f, 1.0f);
			}
			case "confetti" -> {
				for (int i = 0; i < 6; i++) {
					dust(level, hue(i / 6.0), 1.2f, x, y + 1.4, z, 10, 0.7);
				}
				burst(level, "minecraft:firework", x, y + 1.4, z, 20, 0.2, 0.2, 0.2, 0.15);
				sound(level, at, "entity.firework_rocket.twinkle", 0.8f, 1.3f);
			}
			case "ender" -> {
				burst(level, "minecraft:portal", x, y + 1, z, 80, 0.5, 0.8, 0.5, 0.8);
				burst(level, "minecraft:reverse_portal", x, y + 1, z, 25, 0.3, 0.6, 0.3, 0.05);
				sound(level, at, "entity.enderman.teleport", 1.0f, 0.9f);
			}
			case "cherry" -> {
				burst(level, "minecraft:cherry_leaves", x, y + 1.5, z, 60, 0.9, 0.6, 0.9, 0);
				sound(level, at, "block.amethyst_block.chime", 1.0f, 1.4f);
			}
			case "sonic" -> {
				burst(level, "minecraft:sonic_boom", x, y + 1, z, 1, 0, 0, 0, 0);
				sound(level, at, "entity.warden.sonic_boom", 0.6f, 1.0f);
			}
			case "squid" -> {
				burst(level, "minecraft:squid_ink", x, y + 1, z, 30, 0.4, 0.5, 0.4, 0.05);
				burst(level, "minecraft:glow_squid_ink", x, y + 1, z, 15, 0.4, 0.5, 0.4, 0.05);
				sound(level, at, "entity.glow_squid.squirt", 1.0f, 1.0f);
			}
			case "bubbles" -> {
				burst(level, "minecraft:bubble_pop", x, y + 1, z, 40, 0.5, 0.7, 0.5, 0.05);
				burst(level, "minecraft:splash", x, y + 0.5, z, 40, 0.5, 0.3, 0.5, 0.1);
				sound(level, at, "entity.player.splash", 0.8f, 1.2f);
			}
			case "skull" -> {
				burst(level, "minecraft:soul", x, y + 1, z, 15, 0.4, 0.5, 0.4, 0.05);
				burst(level, "minecraft:large_smoke", x, y + 1, z, 20, 0.3, 0.5, 0.3, 0.03);
				burst(level, "dust:#202020:2.0", x, y + 1.2, z, 15, 0.4, 0.4, 0.4, 0);
				sound(level, at, "entity.wither.ambient", 0.6f, 1.2f);
			}
			case "gold" -> {
				burst(level, "item:minecraft:gold_ingot", x, y + 1, z, 25, 0.3, 0.4, 0.3, 0.2);
				burst(level, "block:minecraft:gold_block", x, y + 1, z, 20, 0.3, 0.4, 0.3, 0.1);
				sound(level, at, "entity.experience_orb.pickup", 1.0f, 0.8f);
				sound(level, at, "block.amethyst_block.chime", 1.0f, 1.6f);
			}
			case "notes" -> {
				ring(level, "minecraft:note", at, 0.9, 10, 1.0, 0);
				ring(level, "minecraft:note", at, 0.6, 8, 1.8, 0.4);
				sound(level, at, "block.note_block.harp", 1.0f, 0.7f);
				sound(level, at, "block.note_block.harp", 1.0f, 0.5f);
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
			case "rockets" -> {
				double a = r.nextDouble() * Math.PI * 2;
				double d = 2 + r.nextDouble() * 2;
				net.battlehub.fx.Spectacle.randomFirework(level, at.add(Math.cos(a) * d, 4 + r.nextDouble() * 3, Math.sin(a) * d), new java.util.Random(r.nextLong()));
			}
			case "storm" -> {
				burst(level, "minecraft:cloud", at.x, at.y + 4.5, at.z, 25, 2.0, 0.3, 2.0, 0.01);
				burst(level, "minecraft:falling_water", at.x, at.y + 4.2, at.z, 15, 2.0, 0.1, 2.0, 0);
				if (ticks % 30 == 0) {
					double a = r.nextDouble() * Math.PI * 2;
					double d = 4 + r.nextDouble() * 3;
					net.battlehub.fx.Spectacle.lightning(level, at.add(Math.cos(a) * d, 0, Math.sin(a) * d));
				}
			}
			case "rainbow" -> {
				for (int ringNo = 0; ringNo < 3; ringNo++) {
					double radius = 1.0 + ringNo * 0.35;
					for (int i = 0; i < 18; i++) {
						double a = (ringNo % 2 == 0 ? phase : -phase) + i * Math.PI * 2 / 18;
						dust(level, hue(i / 18.0 + ticks * 0.01), 1.0f, at.x + Math.cos(a) * radius, at.y + 0.3 + ringNo * 0.6, at.z + Math.sin(a) * radius, 1, 0);
					}
				}
				if (ticks % 20 == 0) sound(level, at, "block.amethyst_block.chime", 0.8f, 1.2f);
			}
			case "dragon" -> {
				for (int i = 0; i < 14; i++) {
					double a = phase * 2 + i * 0.45;
					burst(level, "minecraft:dragon_breath", at.x + Math.cos(a) * 1.3, at.y + i * 0.18, at.z + Math.sin(a) * 1.3, 1, 0, 0, 0, 0);
				}
				if (ticks % 60 == 0) sound(level, at, "entity.ender_dragon.growl", 0.5f, 1.2f);
			}
			case "galaxy" -> {
				for (int arm = 0; arm < 3; arm++) {
					for (int i = 1; i <= 8; i++) {
						double radius = i * 0.25;
						double a = phase + arm * Math.PI * 2 / 3 + radius * 1.8;
						burst(level, i % 2 == 0 ? "minecraft:end_rod" : "minecraft:portal", at.x + Math.cos(a) * radius, at.y + 0.2, at.z + Math.sin(a) * radius, 1, 0, 0, 0, 0);
					}
				}
				if (ticks % 20 == 0) sound(level, at, "block.amethyst_block.chime", 0.6f, 0.6f + r.nextFloat());
			}
			case "snow" -> {
				burst(level, "minecraft:snowflake", at.x, at.y + 4, at.z, 40, 2.5, 0.5, 2.5, 0.02);
				burst(level, "block:minecraft:snow_block", at.x, at.y + 3, at.z, 10, 2.0, 0.5, 2.0, 0);
				if (ticks % 40 == 0) sound(level, at, "block.powder_snow.step", 1.0f, 1.0f);
			}
			case "confetti" -> {
				for (int i = 0; i < 8; i++) {
					dust(level, hue(r.nextDouble()), 1.1f, at.x + r.nextDouble(-2.5, 2.5), at.y + 3.5 + r.nextDouble(), at.z + r.nextDouble(-2.5, 2.5), 4, 0.3);
				}
				if (ticks % 20 == 0) sound(level, at, "entity.firework_rocket.twinkle", 0.7f, 1.4f);
			}
			case "aurora" -> {
				net.minecraft.core.particles.DustColorTransitionOptions band = new net.minecraft.core.particles.DustColorTransitionOptions(0x33FF99, 0x9966FF, 1.6f);
				for (int i = 0; i < 24; i++) {
					double a = i * Math.PI * 2 / 24 + phase * 0.3;
					double h = 4.5 + 0.5 * Math.sin(a * 3 + phase);
					level.sendParticles(band, at.x + Math.cos(a) * 4, at.y + h, at.z + Math.sin(a) * 4, 2, 0.1, 0.4, 0.1, 0);
				}
			}
			case "sakura" -> {
				burst(level, "minecraft:cherry_leaves", at.x, at.y + 4, at.z, 35, 2.5, 0.4, 2.5, 0);
				if (ticks % 30 == 0) sound(level, at, "block.amethyst_block.chime", 0.6f, 1.5f);
			}
			case "souls" -> {
				ring(level, "minecraft:soul_fire_flame", at, 1.5, 18, 0.1, phase);
				burst(level, "minecraft:soul", at.x, at.y + 1, at.z, 4, 1.0, 0.5, 1.0, 0.02);
				if (ticks % 30 == 0) sound(level, at, "particle.soul_escape", 1.0f, 1.0f);
			}
			case "gold_rain" -> {
				burst(level, "item:minecraft:gold_nugget", at.x, at.y + 4, at.z, 25, 2.0, 0.3, 2.0, 0);
				if (ticks % 20 == 0) sound(level, at, "entity.experience_orb.pickup", 0.8f, 0.6f + r.nextFloat());
			}
			default -> { }
		}
	}

	// ------------------------------------------------------------------ death cries and weapon skins

	/** Called from the match when a player dies: their death cry, if they chose one. */
	public static void onDeath(ServerPlayer victim, Vec3 at) {
		ServerLevel level = victim.level();
		if (!HubWorld.isBattleHub(level)) {
			return;
		}
		CosmeticDef c = active(victim, "cry");
		if (c != null) {
			cry(level, c.effect, at);
		}
	}

	public static void cry(ServerLevel level, String effect, Vec3 at) {
		sound(level, at, CosmeticRules.crySound(effect), 1.0f, CosmeticRules.cryPitch(effect));
	}

	/** A sword from a kit takes the look of the player's weapon skin (it stays the same sword). */
	public static void applySkin(ServerPlayer p, net.minecraft.world.item.ItemStack stack) {
		if (stack.isEmpty() || !stack.is(net.minecraft.tags.ItemTags.SWORDS)) {
			return;
		}
		CosmeticDef c = active(p, "skin");
		if (c == null) {
			return;
		}
		String model = CosmeticRules.skinModel(c.effect);
		if (!Ids.isItem(model)) {
			return;
		}
		stack.set(net.minecraft.core.component.DataComponents.ITEM_MODEL, Ids.id(model));
		if (CosmeticRules.skinGlint(c.effect)) {
			stack.set(net.minecraft.core.component.DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		}
	}

	/** Trails behind moving players (every 2 ticks) and behind arrows in flight. */
	public static void tick(MinecraftServer server, int tick) {
		try {
			Pets.tick(server, tick);
			Auras.tick(server, tick);
		} catch (RuntimeException e) {
			if (!failed) {
				failed = true;
				BattleHub.LOGGER.error("Cosmetic pets or auras failed", e);
			}
		}
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
				Particles.emit(level, c.effect, pos.x, pos.y + 0.15, pos.z, 2, 0.15, 0.05, 0.15, 0.0, level.getGameTime());
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
					Particles.emit(level, c.effect, a.getX(), a.getY(), a.getZ(), 2, 0.05, 0.05, 0.05, 0.0, level.getGameTime());
				}
			}
		}
	}

	public static void forget(UUID id) {
		LAST_POS.remove(id);
		Pets.remove(id);
	}
}
