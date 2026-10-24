package net.battlehub.progression;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.joml.Vector3f;

import net.battlehub.core.CosmeticDef;
import net.battlehub.core.Gadgets;
import net.battlehub.hub.HubWorld;
import net.battlehub.lang.Lang;
import net.battlehub.mixin.BlockDisplayAccessor;
import net.battlehub.mixin.DisplayAccessor;
import net.battlehub.session.Sessions;
import net.battlehub.util.Ids;
import net.battlehub.util.Msg;
import net.battlehub.util.Tasks;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.phys.Vec3;

/**
 * Gadgets: toys for the lobby, used from their own hotbar slot (right-click), each with a cooldown. They only make
 * particles, sounds and displays: nobody is hurt and nothing breaks, and they do nothing in a match.
 */
public final class GadgetFx {
	private GadgetFx() {}

	private static final Map<UUID, Long> LAST = new HashMap<>();
	private static int used;

	/** Gadgets used since the server started (self-test). */
	public static int used() {
		return used;
	}

	/** Ticks until {@code p} may use their gadget again. */
	public static int ticksLeft(ServerPlayer p, String id) {
		return Gadgets.ticksLeft(id, LAST.getOrDefault(p.getUUID(), -1L), p.level().getGameTime());
	}

	/** The gadget item was used: its effect, or how long to wait. */
	public static void use(ServerPlayer p) {
		CosmeticDef c = Cosmetics.active(p, "gadget");
		if (c == null || !Gadgets.known(c.effect)) return;
		if (!HubWorld.isBattleHub(p.level()) || !Sessions.inLobby(p.getUUID())) return;
		int left = ticksLeft(p, c.effect);
		if (left > 0) {
			Msg.actionBar(p, "gadget.wait", Lang.colored(c.display), (left + 19) / 20);
			return;
		}
		LAST.put(p.getUUID(), p.level().getGameTime());
		play(p, c.effect);
	}

	public static void forget(UUID id) {
		LAST.remove(id);
	}

	/** The effect of gadget {@code id} from {@code p} (also the preview in the shop). */
	public static void play(ServerPlayer p, String id) {
		ServerLevel level = p.level();
		used++;
		Vec3 eye = p.getEyePosition(), look = p.getLookAngle();
		Vec3 front = eye.add(look.scale(1.5));
		switch (id) {
			case "confetti" -> {
				int[] colors = {0xFF4F4F, 0xFFD24F, 0x4FFF7A, 0x4FC3FF, 0xC44FFF, 0xFF4FD8};
				for (int i = 0; i < 6; i++) {
					Vec3 at = eye.add(look.scale(1.5 + i * 0.6));
					for (int c : colors) Cosmetics.dust(level, c, 1.2f, at.x, at.y, at.z, 4, 0.35 + i * 0.08);
				}
				Cosmetics.sound(level, front, "minecraft:entity.firework_rocket.blast", 0.8f, 1.6f);
			}
			case "firework" -> {
				Cosmetics.sound(level, eye, "minecraft:entity.firework_rocket.launch", 1f, 1f);
				for (int t = 0; t < 16; t += 2) {
					double h = t * 0.6;
					Tasks.later(t, () -> Cosmetics.burst(level, "minecraft:firework", p.getX(), p.getY() + 2 + h, p.getZ(), 2, 0.05, 0.1, 0.05, 0.01));
				}
				Tasks.later(18, () -> {
					Vec3 top = new Vec3(p.getX(), p.getY() + 12, p.getZ());
					for (int i = 0; i < 24; i++) Cosmetics.dust(level, Cosmetics.hue(i / 24.0), 1.6f, top.x, top.y, top.z, 3, 1.6);
					Cosmetics.burst(level, "minecraft:firework", top.x, top.y, top.z, 60, 0.2, 0.2, 0.2, 0.3);
					Cosmetics.sound(level, top, "minecraft:entity.firework_rocket.large_blast", 2f, 1f);
					Cosmetics.sound(level, top, "minecraft:entity.firework_rocket.twinkle", 2f, 1f);
				});
			}
			case "rainbow" -> {
				Vec3 dir = new Vec3(look.x, 0, look.z).normalize();
				Vec3 side = new Vec3(-dir.z, 0, dir.x);
				for (int t = 0; t < 60; t += 5) {
					Tasks.later(t, () -> {
						for (int band = 0; band < 7; band++) {
							double r = 3.2 - band * 0.18;
							int color = Cosmetics.hue(band / 7.0);
							for (int k = 0; k <= 16; k++) {
								double a = Math.PI * k / 16;
								Vec3 at = p.position().add(dir.scale(3)).add(side.scale(Math.cos(a) * r)).add(0, Math.sin(a) * r, 0);
								Cosmetics.dust(level, color, 1.1f, at.x, at.y, at.z, 1, 0);
							}
						}
					});
				}
				Cosmetics.sound(level, eye, "minecraft:block.amethyst_block.chime", 1f, 1.2f);
			}
			case "disco" -> disco(p);
			case "bubbles" -> {
				for (int t = 0; t < 60; t += 4) {
					Tasks.later(t, () -> {
						Cosmetics.burst(level, "minecraft:bubble_pop", p.getX(), p.getY() + 1.4, p.getZ(), 8, 1.2, 0.8, 1.2, 0.02);
						Cosmetics.burst(level, "minecraft:splash", p.getX(), p.getY() + 1.8, p.getZ(), 6, 1.0, 0.4, 1.0, 0.1);
					});
				}
				Cosmetics.sound(level, eye, "minecraft:block.bubble_column.bubble_pop", 1f, 1.2f);
			}
			case "wand" -> {
				for (int i = 1; i <= 24; i++) {
					Vec3 at = eye.add(look.scale(i * 0.5));
					int step = i;
					Tasks.later(i / 3, () -> {
						Cosmetics.burst(level, "minecraft:end_rod", at.x, at.y, at.z, 1, 0.02, 0.02, 0.02, 0.0);
						Cosmetics.burst(level, "minecraft:enchant", at.x, at.y, at.z, 3, 0.15, 0.15, 0.15, 0.2);
						if (step == 24) Cosmetics.burst(level, "minecraft:totem_of_undying", at.x, at.y, at.z, 25, 0.2, 0.2, 0.2, 0.3);
					});
				}
				Cosmetics.sound(level, eye, "minecraft:entity.illusioner.cast_spell", 0.8f, 1.4f);
			}
			case "thunder" -> {
				Vec3 at = p.position().add(new Vec3(look.x, 0, look.z).normalize().scale(6));
				Entity e = create(level, "minecraft:lightning_bolt");
				if (e instanceof LightningBolt bolt) {
					bolt.setVisualOnly(true);
					bolt.snapTo(at.x, at.y, at.z);
					level.addFreshEntity(bolt);
				}
				Cosmetics.burst(level, "minecraft:electric_spark", at.x, at.y + 1, at.z, 40, 0.5, 1, 0.5, 0.2);
			}
			case "snowglobe" -> {
				for (int t = 0; t < 100; t += 4) {
					Tasks.later(t, () -> {
						Cosmetics.burst(level, "minecraft:snowflake", p.getX(), p.getY() + 3, p.getZ(), 14, 2.5, 0.5, 2.5, 0.01);
						Cosmetics.burst(level, "minecraft:white_ash", p.getX(), p.getY() + 2, p.getZ(), 10, 2.5, 1.0, 2.5, 0.0);
					});
				}
				Cosmetics.sound(level, eye, "minecraft:block.powder_snow.place", 1f, 0.8f);
			}
			case "smoke" -> {
				for (int t = 0; t < 50; t += 5) {
					Tasks.later(t, () -> {
						Cosmetics.burst(level, "minecraft:campfire_cosy_smoke", p.getX(), p.getY() + 0.5, p.getZ(), 6, 1.2, 0.4, 1.2, 0.01);
						Cosmetics.burst(level, "minecraft:large_smoke", p.getX(), p.getY() + 1, p.getZ(), 12, 1.0, 0.8, 1.0, 0.02);
					});
				}
				Cosmetics.sound(level, eye, "minecraft:block.fire.extinguish", 1f, 0.7f);
			}
			case "popper" -> {
				Cosmetics.burst(level, "minecraft:crit", front.x, front.y, front.z, 20, 0.2, 0.2, 0.2, 0.4);
				for (int i = 0; i < 12; i++) Cosmetics.dust(level, Cosmetics.hue(i / 12.0), 1f, front.x + look.x, front.y + look.y, front.z + look.z, 2, 0.5);
				Cosmetics.sound(level, front, "minecraft:entity.firework_rocket.blast", 0.7f, 2f);
			}
			case "kiss" -> {
				ServerPlayer target = lookedAt(p, 16);
				Vec3 to = target != null ? target.getEyePosition() : eye.add(look.scale(8));
				Vec3 step = to.subtract(eye).scale(1 / 12.0);
				for (int i = 1; i <= 12; i++) {
					Vec3 at = eye.add(step.scale(i));
					Tasks.later(i, () -> Cosmetics.burst(level, "minecraft:heart", at.x, at.y, at.z, 1, 0.05, 0.05, 0.05, 0));
				}
				Cosmetics.sound(level, eye, "minecraft:entity.experience_orb.pickup", 0.8f, 1.8f);
				if (target != null) Tasks.later(13, () -> Msg.actionBar(target, "gadget.kiss", p.getGameProfile().name()));
			}
			case "boombox" -> {
				float[] tune = {1f, 1.19f, 1.33f, 1.5f, 1.33f, 1.19f, 1f, 0.89f, 1f, 1.19f, 1.5f, 2f};
				for (int i = 0; i < tune.length; i++) {
					float pitch = tune[i];
					Tasks.later(i * 5, () -> {
						Cosmetics.sound(level, p.position(), "minecraft:block.note_block.harp", 1f, pitch);
						if (pitch <= 1f) Cosmetics.sound(level, p.position(), "minecraft:block.note_block.bass", 1f, pitch);
						Cosmetics.burst(level, "minecraft:note", p.getX(), p.getY() + 2.3, p.getZ(), 3, 0.6, 0.3, 0.6, 1);
					});
				}
			}
			default -> { }
		}
	}

	/** A spinning sea lantern over {@code p} for five seconds, with coloured lights round it and a tune. */
	private static void disco(ServerPlayer p) {
		ServerLevel level = p.level();
		Entity e = create(level, "minecraft:block_display");
		if (!(e instanceof Display.BlockDisplay ball)) return;
		((BlockDisplayAccessor) ball).battlehub$setBlockState(Ids.state("minecraft:sea_lantern"));
		ball.getEntityData().set(DisplayAccessor.battlehub$translation(), new Vector3f(-0.35f, -0.35f, -0.35f));
		ball.getEntityData().set(DisplayAccessor.battlehub$scale(), new Vector3f(0.7f, 0.7f, 0.7f));
		ball.getEntityData().set(DisplayAccessor.battlehub$posRotDuration(), 2);
		ball.snapTo(p.getX(), p.getY() + 3.2, p.getZ());
		ball.addTag(Cosmetics.FX_TAG);
		level.addFreshEntity(ball);
		for (int t = 0; t < 100; t += 2) {
			int tick = t;
			Tasks.later(t, () -> {
				if (ball.isRemoved()) return;
				ball.teleportTo(p.getX(), p.getY() + 3.2, p.getZ());
				ball.setYRot((tick * 9) % 360);
				for (int k = 0; k < 6; k++) {
					double a = tick * 0.3 + k * Math.PI / 3;
					Cosmetics.dust(level, Cosmetics.hue((tick / 20.0 + k / 6.0) % 1), 1.3f, p.getX() + Math.cos(a) * 2.5, p.getY() + 0.3, p.getZ() + Math.sin(a) * 2.5, 1, 0);
				}
				if (tick % 10 == 0) Cosmetics.sound(level, p.position(), "minecraft:block.note_block.bit", 0.8f, tick % 20 == 0 ? 1.2f : 1.6f);
			});
		}
		Tasks.later(100, ball::discard);
	}

	private static ServerPlayer lookedAt(ServerPlayer p, double range) {
		Vec3 eye = p.getEyePosition(), look = p.getLookAngle();
		ServerPlayer best = null;
		double bestDot = 0.96;
		for (ServerPlayer o : p.level().players()) {
			if (o == p || o.isSpectator()) continue;
			Vec3 to = o.getEyePosition().subtract(eye);
			double d = to.length();
			if (d > range || d < 0.5) continue;
			double dot = to.scale(1 / d).dot(look);
			if (dot > bestDot) {
				bestDot = dot;
				best = o;
			}
		}
		return best;
	}

	private static Entity create(ServerLevel level, String id) {
		var type = BuiltInRegistries.ENTITY_TYPE.getValue(Ids.id(id));
		return type == null ? null : type.create(level, EntitySpawnReason.COMMAND);
	}
}
