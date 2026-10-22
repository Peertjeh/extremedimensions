package net.battlehub.bot;

import java.lang.reflect.Method;

import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * What a bot does goes through the same doors as a client's clicks: attacks pass the attack event (so match
 * rules, protection and spawn shields apply), item use goes through the game mode like a right-click.
 */
final class BotActions {
	private BotActions() {}

	private static Method swing;
	private static Object[] swingArgs;
	private static java.lang.reflect.Field swinging;
	private static boolean swingLooked;

	/** A left-click on an entity. */
	static void attack(ServerPlayer bot, Entity target) {
		InteractionResult r = AttackEntityCallback.EVENT.invoker().interact(bot, bot.level(), InteractionHand.MAIN_HAND, target, null);
		if (r == InteractionResult.PASS) {
			bot.attack(target);
		}
		swing(bot);
	}

	/** A right-click with the item in this hand. */
	static void use(ServerPlayer bot, InteractionHand hand) {
		bot.gameMode.useItem(bot, bot.level(), bot.getItemInHand(hand), hand);
	}

	/**
	 * The arm swing everybody sees. Its signature changed over versions (the hand; then also a flag; in 26 also the
	 * item's swing animation), so it is looked up once: the fullest one whose arguments can be filled (the hand, true,
	 * and for anything else that type's default). A swing the server still counts as running would make the next one
	 * silent (and for a bot it is not always ended), so that state is cleared first; the animation packet goes out
	 * directly as well.
	 */
	static void swing(LivingEntity e) {
		if (!swingLooked) {
			swingLooked = true;
			for (Method m : LivingEntity.class.getDeclaredMethods()) {
				if (!m.getName().equals("swing") || m.getParameterCount() < 1 || m.getParameterTypes()[0] != InteractionHand.class) continue;
				Object[] args = swingArgs(m.getParameterTypes());
				if (args != null && (swing == null || m.getParameterCount() > swing.getParameterCount())) {
					swing = m;
					swingArgs = args;
				}
			}
			if (swing != null) swing.setAccessible(true);
			try {
				swinging = LivingEntity.class.getDeclaredField("swinging");
				swinging.setAccessible(true);
			} catch (ReflectiveOperationException | RuntimeException ex) {
				swinging = null;
			}
			net.battlehub.BattleHub.LOGGER.info("Bots swing their arm with {}", swingHow());
		}
		if (swinging != null) {
			try {
				swinging.setBoolean(e, false);
			} catch (ReflectiveOperationException | RuntimeException ignored) {
				// the packet below shows it
			}
		}
		if (swing != null) {
			try {
				swing.invoke(e, swingArgs);
			} catch (ReflectiveOperationException | RuntimeException ignored) {
				// the packet below shows it
			}
		}
		// the animation always goes out as well: twice in a tick looks like once
		if (e.level() instanceof ServerLevel level) {
			net.minecraft.network.protocol.Packet<?> packet = swingPacket(e);
			if (packet != null) {
				level.getServer().getPlayerList().broadcast(null, e.getX(), e.getY(), e.getZ(), 64, level.dimension(), packet);
			}
		}
	}

	/** The arguments for a swing method: the main hand, true, and each other type's default; null when one has none. */
	private static Object[] swingArgs(Class<?>[] types) {
		Object[] args = new Object[types.length];
		for (int i = 0; i < types.length; i++) {
			if (types[i] == InteractionHand.class) {
				args[i] = InteractionHand.MAIN_HAND;
			} else if (types[i] == boolean.class) {
				args[i] = Boolean.TRUE;
			} else {
				args[i] = defaultOf(types[i]);
				if (args[i] == null) return null;
			}
		}
		return args;
	}

	/** A type's default: an enum's first constant, or a static field of its own type (DEFAULT and the like). */
	private static Object defaultOf(Class<?> type) {
		if (type.isEnum()) {
			Object[] all = type.getEnumConstants();
			return all.length == 0 ? null : all[0];
		}
		for (java.lang.reflect.Field f : type.getDeclaredFields()) {
			if (!java.lang.reflect.Modifier.isStatic(f.getModifiers()) || f.getType() != type) continue;
			try {
				f.setAccessible(true);
				Object v = f.get(null);
				if (v != null) return v;
			} catch (ReflectiveOperationException | RuntimeException ignored) {
				// the next one
			}
		}
		return null;
	}

	/** How bots swing in this version: the method, the packet's constructor and its action (self-test, log). */
	static String swingHow() {
		if (!animateLooked) swingPacket(null);
		return (swing == null ? "no swing method" : swing.toString()) + ", swinging field " + (swinging != null) + ", packet "
				+ (animate == null ? "none" : animate.toString()) + " action " + swingAction;
	}

	private static java.lang.reflect.Constructor<?> animate;
	private static Object swingAction;
	private static boolean animateLooked;

	/**
	 * The "swing main hand" animation packet. Its action was an int constant and may be an enum, so the constructor
	 * and the action are found once.
	 */
	private static net.minecraft.network.protocol.Packet<?> swingPacket(Entity e) {
		if (!animateLooked) {
			animateLooked = true;
			for (java.lang.reflect.Constructor<?> c : net.minecraft.network.protocol.game.ClientboundAnimatePacket.class.getConstructors()) {
				Class<?>[] t = c.getParameterTypes();
				if (t.length != 2 || !t[0].isAssignableFrom(Entity.class)) continue;
				if (t[1] == int.class) {
					swingAction = 0;
					for (java.lang.reflect.Field f : net.minecraft.network.protocol.game.ClientboundAnimatePacket.class.getFields()) {
						if (f.getType() == int.class && java.lang.reflect.Modifier.isStatic(f.getModifiers()) && f.getName().contains("SWING")
								&& f.getName().contains("MAIN")) {
							try {
								swingAction = f.getInt(null);
							} catch (IllegalAccessException ignored) {
								// 0 it is
							}
						}
					}
				} else if (t[1].isEnum()) {
					Object[] all = t[1].getEnumConstants();
					swingAction = all.length == 0 ? null : all[0];
					for (Object o : all) {
						String n = ((Enum<?>) o).name();
						if (n.contains("SWING") && n.contains("MAIN")) swingAction = o;
					}
				} else {
					continue;
				}
				animate = c;
				break;
			}
			net.battlehub.BattleHub.LOGGER.info("Bots show their swing with {} ({})", animate, swingAction);
		}
		if (animate == null || swingAction == null || e == null) return null;
		try {
			return (net.minecraft.network.protocol.Packet<?>) animate.newInstance(e, swingAction);
		} catch (ReflectiveOperationException | RuntimeException ex) {
			return null;
		}
	}

	private static Method particleTo;
	private static boolean particleLooked;

	/**
	 * One particle only this player sees (for admins watching a bot). The method's arguments differ between versions
	 * (one flag more or less), so it is looked up once; without it nothing is shown.
	 */
	static void particle(ServerPlayer viewer, net.minecraft.core.particles.ParticleOptions type, Vec3 at) {
		if (!particleLooked) {
			particleLooked = true;
			for (Method m : ServerLevel.class.getMethods()) {
				if (m.getName().equals("sendParticles") && (m.getParameterCount() == 11 || m.getParameterCount() == 12)
						&& m.getParameterTypes()[0] == ServerPlayer.class) {
					particleTo = m;
					break;
				}
			}
		}
		if (particleTo == null) return;
		try {
			Object[] args = particleTo.getParameterCount() == 12
					? new Object[] {viewer, type, true, true, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0}
					: new Object[] {viewer, type, true, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0};
			particleTo.invoke(viewer.level(), args);
		} catch (ReflectiveOperationException | IllegalArgumentException e) {
			particleTo = null;
		}
	}

	/** Is there something to stand on within a few blocks below this spot? Water does not count as a hole; lava does. */
	static boolean groundBelow(ServerLevel level, double x, double y, double z, int depth) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(Math.floor(x), Math.floor(y) - 1, Math.floor(z));
		for (int i = 0; i < depth; i++) {
			BlockState st = level.getBlockState(pos);
			if (st.is(Blocks.WATER)) {
				// A pool (a fountain) is no drop: what counts is the bottom under it
				pos.move(0, -1, 0);
				continue;
			}
			if (!st.isAir() && st.getFluidState().isEmpty()) {
				return true;
			}
			if (!st.getFluidState().isEmpty()) {
				return false;
			}
			pos.move(0, -1, 0);
		}
		return false;
	}

	/** The block right under this spot is solid. */
	static boolean floorAt(ServerLevel level, double x, double y, double z) {
		BlockState st = level.getBlockState(BlockPos.containing(x, y - 0.5, z));
		return !st.isAir() && st.getFluidState().isEmpty();
	}

	/** Horizontal unit vector for a Minecraft yaw. */
	static Vec3 dir(float yaw) {
		double r = Math.toRadians(yaw);
		return new Vec3(-Math.sin(r), 0, Math.cos(r));
	}
}
