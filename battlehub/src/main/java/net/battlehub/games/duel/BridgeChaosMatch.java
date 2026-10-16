package net.battlehub.games.duel;

import java.util.List;
import java.util.UUID;

import net.battlehub.core.GameMode;
import net.battlehub.lang.Lang;
import net.battlehub.util.Ids;
import net.battlehub.util.Msg;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * The Bridge: Chaos (a test mode next to the normal Bridge). Everything of the Bridge, plus: a power-up falls onto
 * the middle every few seconds (wind charges, an ender pearl, a golden apple, snowballs, cobwebs, arrows, extra
 * clay or a knockback stick) and every half minute something happens to everyone for a while: low gravity, a
 * speed rush, a double goal, the middle of the bridge crumbling, or wind charges for all.
 */
public final class BridgeChaosMatch extends BridgeMatch {
	public static final List<String> POWERUPS = List.of("wind", "pearl", "apple", "snowballs", "cobweb", "arrows", "clay", "stick");
	public static final List<String> EVENTS = List.of("low_gravity", "speed_rush", "double_goal", "crumble", "gift");

	/** Ticks of play until the next power-up and the next event (they keep counting over goals). */
	private int powerupIn = -1;
	private int eventIn = -1;
	private boolean doubleGoal;
	private String lastEvent = "";

	public BridgeChaosMatch(int id, boolean challenge, MinecraftServer server, ServerPlayer a, ServerPlayer b) {
		super(id, GameMode.BRIDGE_CHAOS, false, challenge, server, a, b);
	}

	private int powerupTicks() {
		return (int) Math.max(3, game().setting("powerupSeconds", 12)) * 20;
	}

	private int eventTicks() {
		return (int) Math.max(10, game().setting("eventSeconds", 35)) * 20;
	}

	public boolean doubleGoal() {
		return doubleGoal;
	}

	public String lastEvent() {
		return lastEvent;
	}

	@Override
	protected int goalValue() {
		return doubleGoal ? 2 : 1;
	}

	@Override
	void scored(UUID scorer) {
		super.scored(scorer);
		doubleGoal = false;
	}

	/** One wind charge on top of the Bridge kit. */
	@Override
	protected void afterKit(ServerPlayer p) {
		super.afterKit(p);
		p.getInventory().add(new ItemStack(Ids.item("minecraft:wind_charge"), 1));
		p.inventoryMenu.sendAllDataToRemote();
	}

	@Override
	protected void tickGame() {
		super.tickGame();
		if (phase != Phase.RUNNING || arena == null) {
			return;
		}
		if (powerupIn < 0) powerupIn = powerupTicks() / 2;
		if (eventIn < 0) eventIn = eventTicks();
		if (--powerupIn <= 0) {
			powerupIn = powerupTicks();
			dropPowerup(POWERUPS.get(random.nextInt(POWERUPS.size())));
		}
		if (--eventIn <= 0) {
			eventIn = eventTicks();
			event(EVENTS.get(random.nextInt(EVENTS.size())));
		}
		if (runTicks % 10 == 0) {
			Vec3 mid = Vec3.atBottomCenterOf(arena.origin()).add(0, 1.2, 0);
			arena.level().sendParticles(ParticleTypes.END_ROD, mid.x, mid.y, mid.z, 2, 0.6, 0.3, 0.6, 0.01);
		}
	}

	/** The item of a power-up. */
	ItemStack powerupItem(String kind) {
		return switch (kind) {
			case "wind" -> new ItemStack(Ids.item("minecraft:wind_charge"), 2);
			case "pearl" -> new ItemStack(Ids.item("minecraft:ender_pearl"), 1);
			case "apple" -> new ItemStack(Ids.item("minecraft:golden_apple"), 1);
			case "snowballs" -> new ItemStack(Ids.item("minecraft:snowball"), 8);
			case "cobweb" -> new ItemStack(Ids.item("minecraft:cobweb"), 2);
			case "arrows" -> new ItemStack(Ids.item("minecraft:arrow"), 8);
			case "clay" -> new ItemStack(Ids.item("minecraft:white_terracotta"), 32);
			default -> {
				ItemStack stick = new ItemStack(Ids.item("minecraft:stick"));
				Ids.enchantment(server, "minecraft:knockback").ifPresent(h -> stick.enchant(h, 2));
				yield stick;
			}
		};
	}

	/** A power-up falls onto the middle of the bridge, glowing. */
	public void dropPowerup(String kind) {
		ServerLevel level = arena.level();
		Vec3 mid = Vec3.atBottomCenterOf(arena.origin()).add(0, 3, 0);
		ItemEntity item = new ItemEntity(level, mid.x, mid.y, mid.z, powerupItem(kind));
		item.setDeltaMovement(Vec3.ZERO);
		item.setGlowingTag(true);
		level.addFreshEntity(item);
		level.sendParticles(ParticleTypes.FIREWORK, mid.x, mid.y, mid.z, 20, 0.3, 0.3, 0.3, 0.1);
		forEveryone(o -> {
			Msg.actionBar(o, "bridge_chaos.powerup", Lang.tr(o, "bridge_chaos.powerup." + kind));
			Msg.sound(o, SoundEvents.NOTE_BLOCK_PLING, 0.6f, 1.6f);
		});
	}

	/** Something happens to everyone. */
	public void event(String kind) {
		lastEvent = kind;
		int ticks = 200;
		for (UUID id : alive) {
			ServerPlayer p = player(id);
			if (p == null) continue;
			switch (kind) {
				case "low_gravity" -> {
					effect(p, "minecraft:jump_boost", ticks, 2);
					effect(p, "minecraft:slow_falling", ticks, 0);
				}
				case "speed_rush" -> effect(p, "minecraft:speed", ticks, 1);
				case "gift" -> {
					p.getInventory().add(new ItemStack(Ids.item("minecraft:wind_charge"), 2));
					p.inventoryMenu.sendAllDataToRemote();
				}
				default -> { }
			}
		}
		if (kind.equals("double_goal")) {
			doubleGoal = true;
		}
		if (kind.equals("crumble") && build != null) {
			// Everything built over the middle of the gap falls apart
			BlockPos o = arena.origin();
			build.crumble(p -> Math.abs(p.getX() - o.getX()) <= 7);
		}
		forEveryone(o -> {
			Msg.title(o, Lang.tr(o, "bridge_chaos.event." + kind), Lang.tr(o, "bridge_chaos.event." + kind + ".sub"), 5, 40, 10);
			Msg.sound(o, SoundEvents.FIRECHARGE_USE, 0.7f, 1.4f);
		});
	}

	private static void effect(ServerPlayer p, String id, int ticks, int amp) {
		Ids.effect(id).ifPresent(h -> p.addEffect(new MobEffectInstance(h, ticks, amp, false, true, true)));
	}
}
