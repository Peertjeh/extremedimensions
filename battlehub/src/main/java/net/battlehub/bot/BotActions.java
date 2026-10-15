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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * What a bot does goes through the same doors as a client's clicks: attacks pass the attack event (so match
 * rules, protection and spawn shields apply), item use goes through the game mode like a right-click.
 */
final class BotActions {
	private BotActions() {}

	private static Method swing;
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

	/** The arm swing everybody sees (its signature changed over versions, so it is looked up once). */
	static void swing(LivingEntity e) {
		if (!swingLooked) {
			swingLooked = true;
			for (Method m : LivingEntity.class.getDeclaredMethods()) {
				if (m.getName().equals("swing") && m.getParameterCount() >= 1 && m.getParameterTypes()[0] == InteractionHand.class) {
					m.setAccessible(true);
					swing = m;
					break;
				}
			}
		}
		if (swing == null) {
			return;
		}
		Class<?>[] types = swing.getParameterTypes();
		Object[] args = new Object[types.length];
		for (int i = 0; i < types.length; i++) {
			if (types[i] == InteractionHand.class) {
				args[i] = InteractionHand.MAIN_HAND;
			} else if (types[i] == boolean.class) {
				args[i] = false;
			} else if (types[i].isEnum()) {
				Object[] values = types[i].getEnumConstants();
				args[i] = values.length > 0 ? values[0] : null;
			} else if (types[i].isPrimitive()) {
				args[i] = types[i] == int.class ? 0 : types[i] == float.class ? 0f : types[i] == double.class ? 0d : null;
			}
		}
		try {
			swing.invoke(e, args);
		} catch (ReflectiveOperationException | RuntimeException ignored) {
			// Only the animation is lost
		}
	}

	/** Is there something to stand on within a few blocks below this spot? */
	static boolean groundBelow(ServerLevel level, double x, double y, double z, int depth) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(Math.floor(x), Math.floor(y) - 1, Math.floor(z));
		for (int i = 0; i < depth; i++) {
			BlockState st = level.getBlockState(pos);
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
