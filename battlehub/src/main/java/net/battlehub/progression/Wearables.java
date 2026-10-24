package net.battlehub.progression;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.joml.Vector3f;

import net.battlehub.bot.Bots;
import net.battlehub.core.Balloons;
import net.battlehub.core.CosmeticDef;
import net.battlehub.core.Toppers;
import net.battlehub.hub.HubWorld;
import net.battlehub.mixin.BlockDisplayAccessor;
import net.battlehub.mixin.DisplayAccessor;
import net.battlehub.mixin.ItemDisplayAccessor;
import net.battlehub.session.Sessions;
import net.battlehub.util.Ids;
import net.battlehub.util.Tags;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.Vec3;

/**
 * Toppers and balloons: cosmetics built from displays that go with you in the lobby. A topper is a small model over
 * your head ({@link Toppers}), a balloon floats on a string by your right hand ({@link Balloons}). They go when you
 * leave the lobby (a match, spectating) and come back after. A preview in the shop puts one on you for five seconds.
 */
public final class Wearables {
	private Wearables() {}

	public static final String TAG = "battlehub_wear";
	/** How long a preview lasts (ticks). */
	public static final int PREVIEW_TICKS = 100;

	private static final class Worn {
		String topper = "";
		final List<Display> parts = new ArrayList<>();
		String balloon = "";
		Display.BlockDisplay ball;
		double[] at;
	}

	private static final Map<UUID, Worn> WORN = new HashMap<>();
	/** Previews from the shop: the cosmetic and until which tick. */
	private static final Map<UUID, CosmeticDef> PREVIEW = new HashMap<>();
	private static final Map<UUID, Integer> PREVIEW_UNTIL = new HashMap<>();
	private static int now;

	/** Shows {@code c} (a topper or a balloon) on {@code p} for a few seconds, bought or not. */
	public static void preview(ServerPlayer p, CosmeticDef c) {
		PREVIEW.put(p.getUUID(), c);
		PREVIEW_UNTIL.put(p.getUUID(), now + PREVIEW_TICKS);
	}

	/** The parts of the topper {@code id} wears now (self-test). */
	public static int parts(UUID id) {
		Worn w = WORN.get(id);
		return w == null ? 0 : (int) w.parts.stream().filter(e -> !e.isRemoved()).count();
	}

	/** The balloon of {@code id}, or null. */
	public static Entity balloon(UUID id) {
		Worn w = WORN.get(id);
		return w == null || w.ball == null || w.ball.isRemoved() ? null : w.ball;
	}

	static void tick(MinecraftServer server, int tick) {
		now = tick;
		PREVIEW_UNTIL.entrySet().removeIf(e -> {
			boolean over = e.getValue() <= tick;
			if (over) PREVIEW.remove(e.getKey());
			return over;
		});
		for (UUID id : new ArrayList<>(WORN.keySet())) {
			ServerPlayer owner = server.getPlayerList().getPlayer(id);
			if (owner == null || !eligible(owner)) remove(id);
		}
		ServerLevel hub = HubWorld.level(server);
		if (hub == null) return;
		for (ServerPlayer p : hub.players()) {
			if (!eligible(p)) continue;
			String topper = wanted(p, "topper"), balloon = wanted(p, "balloon");
			if (topper.isEmpty() && balloon.isEmpty()) {
				remove(p.getUUID());
				continue;
			}
			Worn w = WORN.computeIfAbsent(p.getUUID(), k -> new Worn());
			topper(hub, p, w, topper, tick);
			balloon(hub, p, w, balloon, tick);
		}
		if (tick % 100 == 0) sweep(hub);
	}

	/** The topper or balloon {@code p} has on: a preview, else the one chosen; "" none. */
	private static String wanted(ServerPlayer p, String category) {
		CosmeticDef pre = PREVIEW.get(p.getUUID());
		if (pre != null && pre.category().equals(category)) return pre.effect;
		CosmeticDef c = Cosmetics.active(p, category);
		return c == null ? "" : c.effect;
	}

	private static boolean eligible(ServerPlayer p) {
		return HubWorld.is(p.level()) && Sessions.inLobby(p.getUUID()) && !p.isSpectator() && !p.isInvisible() && !Bots.isBot(p);
	}

	// ------------------------------------------------------------------ toppers

	private static void topper(ServerLevel hub, ServerPlayer p, Worn w, String model, int tick) {
		if (!model.equals(w.topper)) {
			for (Display d : w.parts) d.discard();
			w.parts.clear();
			w.topper = model;
		}
		if (!Toppers.known(model)) return;
		List<Toppers.Placed> frame = Toppers.frame(model, tick, p.yBodyRot);
		double top = p.getY() + p.getBbHeight() + 0.02;
		if (w.parts.isEmpty()) {
			for (Toppers.Placed part : frame) {
				Display d = spawn(hub, part, new Vec3(p.getX() + part.x(), top + part.y(), p.getZ() + part.z()));
				if (d != null) w.parts.add(d);
			}
		}
		for (int i = 0; i < w.parts.size() && i < frame.size(); i++) {
			Toppers.Placed part = frame.get(i);
			Display d = w.parts.get(i);
			d.teleportTo(p.getX() + part.x(), top + part.y(), p.getZ() + part.z());
			d.setYRot(part.yaw());
		}
		Toppers.Model m = Toppers.MODELS.get(model);
		if (!m.particle().isEmpty() && tick % 4 == 0) {
			Particles.emit(hub, m.particle(), p.getX(), top + m.particleY(), p.getZ(), 1, 0.2, 0.02, 0.2, 0.0, tick);
		}
	}

	private static Display spawn(ServerLevel hub, Toppers.Placed part, Vec3 at) {
		Display d;
		if (part.kind() == Toppers.Kind.BLOCK) {
			Display.BlockDisplay b = create(hub, "minecraft:block_display", Display.BlockDisplay.class);
			if (b == null) return null;
			((BlockDisplayAccessor) b).battlehub$setBlockState(Ids.state(part.id()));
			b.getEntityData().set(DisplayAccessor.battlehub$translation(), new Vector3f(-part.sx() / 2, -part.sy() / 2, -part.sz() / 2));
			d = b;
		} else {
			Display.ItemDisplay it = create(hub, "minecraft:item_display", Display.ItemDisplay.class);
			if (it == null) return null;
			((ItemDisplayAccessor) it).battlehub$setItemStack(new ItemStack(Ids.item(part.id())));
			d = it;
		}
		d.getEntityData().set(DisplayAccessor.battlehub$scale(), new Vector3f(part.sx(), part.sy(), part.sz()));
		d.getEntityData().set(DisplayAccessor.battlehub$posRotDuration(), 2);
		d.snapTo(at.x, at.y, at.z);
		d.setYRot(part.yaw());
		d.addTag(TAG);
		hub.addFreshEntity(d);
		return d;
	}

	// ------------------------------------------------------------------ balloons

	private static void balloon(ServerLevel hub, ServerPlayer p, Worn w, String block, int tick) {
		if (!block.equals(w.balloon)) {
			if (w.ball != null) w.ball.discard();
			w.ball = null;
			w.balloon = block;
			w.at = null;
		}
		if (block.isEmpty() || !Ids.isBlock(block)) return;
		double[] hand = Balloons.hand(p.getX(), p.getY(), p.getZ(), p.yBodyRot);
		if (w.at == null) w.at = new double[] {hand[0], hand[1] + Balloons.STRING, hand[2]};
		w.at = Balloons.step(w.at, hand, tick);
		if (w.ball == null || w.ball.isRemoved()) {
			w.ball = create(hub, "minecraft:block_display", Display.BlockDisplay.class);
			if (w.ball == null) return;
			((BlockDisplayAccessor) w.ball).battlehub$setBlockState(Ids.state(block));
			w.ball.getEntityData().set(DisplayAccessor.battlehub$translation(), new Vector3f(-0.3f, 0f, -0.3f));
			w.ball.getEntityData().set(DisplayAccessor.battlehub$scale(), new Vector3f(0.6f, 0.6f, 0.6f));
			w.ball.getEntityData().set(DisplayAccessor.battlehub$posRotDuration(), 2);
			w.ball.snapTo(w.at[0], w.at[1], w.at[2]);
			w.ball.addTag(TAG);
			hub.addFreshEntity(w.ball);
		}
		w.ball.teleportTo(w.at[0], w.at[1], w.at[2]);
		w.ball.setYRot((tick * 2) % 360);
		if (tick % 2 == 0) {
			// the string: small white dots from the hand to the balloon
			for (int i = 1; i <= 6; i++) {
				double f = i / 7.0;
				Particles.emit(hub, "dust:#F0F0F0", hand[0] + (w.at[0] - hand[0]) * f, hand[1] + (w.at[1] - hand[1]) * f,
						hand[2] + (w.at[2] - hand[2]) * f, 1, 0, 0, 0, 0, tick);
			}
		}
	}

	// ------------------------------------------------------------------ cleaning up

	private static <T extends Entity> T create(ServerLevel level, String id, Class<T> type) {
		EntityType<?> t = BuiltInRegistries.ENTITY_TYPE.getValue(Ids.id(id));
		Entity e = t == null ? null : t.create(level, EntitySpawnReason.COMMAND);
		return type.isInstance(e) ? type.cast(e) : null;
	}

	public static void remove(UUID id) {
		Worn w = WORN.remove(id);
		if (w == null) return;
		for (Display d : w.parts) d.discard();
		if (w.ball != null) w.ball.discard();
	}

	public static void removeAll() {
		for (UUID id : new ArrayList<>(WORN.keySet())) remove(id);
		PREVIEW.clear();
		PREVIEW_UNTIL.clear();
	}

	/** Displays left from before a restart (or a crash) are not ours any more. */
	private static void sweep(ServerLevel hub) {
		List<Entity> ours = new ArrayList<>();
		for (Worn w : WORN.values()) {
			ours.addAll(w.parts);
			if (w.ball != null) ours.add(w.ball);
		}
		for (Display e : hub.getEntities(EntityTypeTest.forClass(Display.class), e -> Boolean.TRUE.equals(Tags.has(e, TAG)))) {
			if (!ours.contains(e)) e.discard();
		}
	}
}
