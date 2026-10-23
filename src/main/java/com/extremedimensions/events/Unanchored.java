package com.extremedimensions.events;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import com.extremedimensions.config.Config;
import com.extremedimensions.data.DataStore;
import com.extremedimensions.data.PlayerData;
import com.extremedimensions.journal.Journal;
import com.extremedimensions.journal.MarginNote;
import com.extremedimensions.lore.Loot;
import com.extremedimensions.progression.Advancements;
import com.extremedimensions.server.Dims;
import com.extremedimensions.server.Loaded;
import com.extremedimensions.server.WorldFx;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * The Unanchored Build. Out on the End's far islands, on an expedition, the Endermen sometimes stop and turn to one
 * spot: three of them lay a ring of end stone round a floating pearl, block by block, for a minute. Look at them and
 * they turn on you, and the ring stays as it is. Let them finish without looking and they step into the ring and are
 * gone, leaving pearls, a fragment and a note in another hand beside XIII.
 */
public final class Unanchored {
	private Unanchored() {}

	/** One build under way. */
	public static final class Build {
		public final UUID player;
		public final ServerLevel level;
		public final BlockPos centre;
		public final List<BlockPos> ring;
		public final List<UUID> builders = new ArrayList<>();
		public final long started;
		public int laid;

		Build(UUID player, ServerLevel level, BlockPos centre, List<BlockPos> ring, long started) {
			this.player = player;
			this.level = level;
			this.centre = centre;
			this.ring = ring;
			this.started = started;
		}

		String tag() {
			return "ed_unanchored_" + Integer.toHexString(player.hashCode());
		}
	}

	private static final Map<UUID, Build> BUILDS = new HashMap<>();
	private static final String TAG = "ed_unanchored";

	public static void tick(MinecraftServer s) {
		long now = s.getTickCount();
		if (now % 1200 == 600 && Config.UNANCHORED > 0) {
			for (ServerPlayer p : s.getPlayerList().getPlayers()) {
				PlayerData d = DataStore.get(p.getUUID());
				if (d.inRun && Dims.END.equals(Dims.of(p)) && Math.hypot(p.getX(), p.getZ()) >= Config.UNANCHORED_FROM
						&& !BUILDS.containsKey(p.getUUID()) && System.currentTimeMillis() - d.lastUnanchored >= Math.max(0, Config.UNANCHORED_COOLDOWN) * 60_000L
						&& ThreadLocalRandom.current().nextDouble() < Config.UNANCHORED_CHANCE && WorldEvents.free(p, now)) {
					start(p, now);
				}
			}
		}
		if (now % 5 != 0) {
			return;
		}
		for (Iterator<Build> it = BUILDS.values().iterator(); it.hasNext();) {
			Build b = it.next();
			String end = step(b, now);
			if (end != null) {
				it.remove();
				WorldEvents.end(b.player, "unanchored");
			}
		}
	}

	/** Begins a build near a player. Null when it began, else a language key for why not. */
	public static String start(ServerPlayer p, long now) {
		if (!(p.level() instanceof ServerLevel level)) {
			return "commands.extremedimensions.event.no_room";
		}
		BlockPos centre = site(level, p);
		if (centre == null) {
			return "commands.extremedimensions.event.no_room";
		}
		List<BlockPos> ring = new ArrayList<>();
		for (int dx = -3; dx <= 3; dx++) {
			for (int dz = -3; dz <= 3; dz++) {
				double r = Math.sqrt(dx * dx + dz * dz);
				if (r >= 2.5 && r < 3.5) {
					ring.add(centre.offset(dx, 0, dz));
				}
			}
		}
		ring.sort(Comparator.comparingDouble(q -> Math.atan2(q.getZ() - centre.getZ(), q.getX() - centre.getX())));
		Build b = new Build(p.getUUID(), level, centre, ring, now);
		for (int i = 0; i < 3; i++) {
			BlockPos at = ring.get(i * ring.size() / 3).offset(Integer.signum(ring.get(i * ring.size() / 3).getX() - centre.getX()), 0,
					Integer.signum(ring.get(i * ring.size() / 3).getZ() - centre.getZ()));
			Entity e = WorldFx.summon(level, "minecraft:enderman", at.getX(), at.getY(), at.getZ());
			if (e instanceof Mob m) {
				m.setNoAi(true);
				m.addTag(TAG);
				m.addTag(b.tag());
				face(m, centre);
				b.builders.add(m.getUUID());
			}
		}
		if (b.builders.isEmpty()) {
			return "commands.extremedimensions.event.no_room";
		}
		// (known before the pearl is put up: a pearl of no build under way is taken away as it loads)
		BUILDS.put(p.getUUID(), b);
		// The pearl they build round, hanging over the middle
		WorldFx.run(String.format(Locale.ROOT, "execute in %s run summon minecraft:item_display %.2f %.2f %.2f "
				+ "{Tags:[\"%s\",\"%s\"],item:{id:\"minecraft:ender_pearl\",count:1},billboard:\"center\",brightness:{sky:15,block:15},"
				+ "transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0f,0f,0f],scale:[0.7f,0.7f,0.7f]}}",
				level.dimension().identifier(), centre.getX() + 0.5, centre.getY() + 1.3, centre.getZ() + 0.5, TAG, b.tag()));
		DataStore.get(p.getUUID()).lastUnanchored = System.currentTimeMillis();
		DataStore.markDirty();
		WorldEvents.begin(p, "unanchored", now);
		for (ServerPlayer q : level.players()) {
			if (q.distanceToSqr(Vec3.atCenterOf(centre)) < 64 * 64) {
				WorldFx.soundFor(q, "minecraft:entity.enderman.ambient", "hostile", centre.getX() + 0.5, centre.getY() + 1, centre.getZ() + 0.5, 1.0f, 0.5f);
				q.sendSystemMessage(Component.translatable("gui.extremedimensions.unanchored.begin").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.ITALIC), true);
			}
		}
		return null;
	}

	/** A level spot ten to sixteen blocks from the player, with room for the ring. */
	private static BlockPos site(ServerLevel level, ServerPlayer p) {
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		double a0 = rnd.nextDouble() * Math.PI * 2;
		for (int i = 0; i < 12; i++) {
			double a = a0 + i * Math.PI / 6, d = 10 + rnd.nextInt(7);
			int x = (int) Math.floor(p.getX() + Math.cos(a) * d), z = (int) Math.floor(p.getZ() + Math.sin(a) * d);
			if (!Loaded.ready(level, new BlockPos(x, (int) p.getY(), z))) {
				continue;
			}
			int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
			if (y <= level.getMinY() + 2 || Math.abs(y - p.getY()) > 12) {
				continue;
			}
			boolean flat = true;
			for (int dx = -4; dx <= 4 && flat; dx++) {
				for (int dz = -4; dz <= 4 && flat; dz++) {
					int h = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x + dx, z + dz);
					flat = Math.abs(h - y) <= 1 && level.getBlockState(new BlockPos(x + dx, y + 1, z + dz)).isAir()
							&& level.getBlockState(new BlockPos(x + dx, y + 2, z + dz)).isAir();
				}
			}
			if (flat) {
				return new BlockPos(x, y, z);
			}
		}
		return null;
	}

	/** One step of a build: anger, a stone laid, or the end. Returns why it ended, or null while it goes on. */
	public static String step(Build b, long now) {
		List<Mob> builders = new ArrayList<>();
		boolean killed = false, unloaded = false;
		for (UUID id : b.builders) {
			Entity e = b.level.getEntity(id);
			if (e instanceof Mob m && m.isAlive()) {
				builders.add(m);
			} else if (e != null) {
				// (a killed one lies dying for a second: it is seen)
				killed = true;
			} else {
				unloaded = true;
			}
		}
		ServerPlayer owner = b.level.getServer().getPlayerList().getPlayer(b.player);
		// Gone from the world, or out of it with their part of it unloaded: the builders go too
		if (owner == null || owner.level() != b.level || owner.distanceToSqr(Vec3.atCenterOf(b.centre)) > 160 * 160 || (unloaded && !killed)) {
			vanish(b, builders, false);
			return "left";
		}
		if (killed) {
			anger(b, builders, owner, false);
			return "angered";
		}
		for (Mob m : builders) {
			if (m.hurtTime > 0 && m.getLastHurtByMob() != null) {
				anger(b, builders, m.getLastHurtByMob(), false);
				return "angered";
			}
		}
		for (ServerPlayer q : b.level.players()) {
			if (q.isSpectator() || q.distanceToSqr(Vec3.atCenterOf(b.centre)) > 64 * 64) {
				continue;
			}
			for (Mob m : builders) {
				if (staring(q, m)) {
					anger(b, builders, q, true);
					return "angered";
				}
			}
		}
		long total = Math.max(5, Config.UNANCHORED_SECONDS) * 20L;
		double done = Math.min(1, (now - b.started) / (double) total);
		int target = (int) Math.floor(done * b.ring.size());
		while (b.laid < target && b.laid < b.ring.size()) {
			BlockPos at = b.ring.get(b.laid);
			Mob m = builders.get(b.laid % builders.size());
			BlockPos stand = at.offset(Integer.signum(at.getX() - b.centre.getX()), 0, Integer.signum(at.getZ() - b.centre.getZ()));
			b.level.sendParticles(ParticleTypes.PORTAL, m.getX(), m.getY() + 1.4, m.getZ(), 20, 0.3, 0.8, 0.3, 0.3);
			m.teleportTo(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5);
			face(m, b.centre);
			if (b.level.getBlockState(at).canBeReplaced()) {
				b.level.setBlock(at, Blocks.END_STONE_BRICKS.defaultBlockState(), 3);
			}
			WorldFx.soundIn(b.level.dimension().identifier().toString(), "minecraft:block.stone.place", "block", at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 1.0f, 0.7f);
			b.laid++;
		}
		for (Mob m : builders) {
			face(m, b.centre);
		}
		if (b.laid >= b.ring.size() && done >= 1) {
			finish(b, builders);
			return "finished";
		}
		return null;
	}

	/** Looked at, hurt or one of them killed: they turn on whoever did it, and the ring stays as it is. */
	private static void anger(Build b, List<Mob> builders, LivingEntity at, boolean stared) {
		for (Mob m : builders) {
			// plain Endermen again, angry ones
			m.setNoAi(false);
			m.removeTag(TAG);
			m.removeTag(b.tag());
			if (at != null) {
				m.setTarget(at);
			}
		}
		kill(b, "item_display");
		for (ServerPlayer q : b.level.players()) {
			if (q.distanceToSqr(Vec3.atCenterOf(b.centre)) < 64 * 64) {
				q.sendSystemMessage(Component.translatable(stared ? "gui.extremedimensions.unanchored.seen" : "gui.extremedimensions.unanchored.broken")
						.withStyle(ChatFormatting.DARK_PURPLE), true);
			}
		}
	}

	/**
	 * A builder or the pearl loaded with its chunk, but no build under way is theirs (a stop or an unloaded world ended
	 * it without them): they are taken away, rather than stand frozen there for good.
	 */
	public static boolean stray(Entity e) {
		boolean ours = false;
		for (String t : e.entityTags()) {
			if (t.startsWith(TAG)) {
				ours = true;
				for (Build b : BUILDS.values()) {
					if (t.equals(b.tag())) {
						return false;
					}
				}
			}
		}
		return ours;
	}

	/** Finished unwatched: they step into the ring and are gone, and leave what they leave. */
	private static void finish(Build b, List<Mob> builders) {
		Vec3 c = Vec3.atCenterOf(b.centre);
		for (Mob m : builders) {
			b.level.sendParticles(ParticleTypes.PORTAL, m.getX(), m.getY() + 1.4, m.getZ(), 40, 0.3, 0.9, 0.3, 0.5);
			m.teleportTo(c.x, b.centre.getY(), c.z);
			b.level.sendParticles(ParticleTypes.REVERSE_PORTAL, c.x, b.centre.getY() + 1.4, c.z, 40, 0.3, 0.9, 0.3, 0.1);
			m.discard();
		}
		kill(b, "item_display");
		WorldFx.soundIn(b.level.dimension().identifier().toString(), "minecraft:block.end_portal_frame.fill", "ambient", c.x, b.centre.getY() + 1, c.z, 1.2f, 0.7f);
		WorldFx.soundIn(b.level.dimension().identifier().toString(), "minecraft:entity.enderman.teleport", "hostile", c.x, b.centre.getY() + 1, c.z, 1.0f, 0.6f);
		for (ItemStack s : Loot.roll(b.level, Loot.UNANCHORED, c)) {
			ItemEntity drop = new ItemEntity(b.level, c.x, b.centre.getY() + 1.2, c.z, s);
			drop.setDeltaMovement(0, 0.15, 0);
			b.level.addFreshEntity(drop);
		}
		for (ServerPlayer q : b.level.players()) {
			if (q.distanceToSqr(c) < 48 * 48 || q.getUUID().equals(b.player)) {
				Journal.note(q, DataStore.get(q.getUUID()), MarginNote.UNANCHORED);
				Advancements.grant(q, Advancements.UNANCHORED);
				q.sendSystemMessage(Component.translatable("gui.extremedimensions.unanchored.done").withStyle(ChatFormatting.LIGHT_PURPLE), true);
			}
		}
	}

	/** The player went away: the builders go too, without a word. */
	private static void vanish(Build b, List<Mob> builders, boolean loud) {
		for (Mob m : builders) {
			b.level.sendParticles(ParticleTypes.PORTAL, m.getX(), m.getY() + 1.4, m.getZ(), 20, 0.3, 0.8, 0.3, 0.3);
			m.discard();
		}
		kill(b, "item_display");
	}

	private static void kill(Build b, String type) {
		WorldFx.run("kill @e[type=minecraft:" + type + ",tag=" + b.tag() + "]");
	}

	private static void face(Mob m, BlockPos c) {
		float yaw = (float) Math.toDegrees(Math.atan2(c.getZ() + 0.5 - m.getZ(), c.getX() + 0.5 - m.getX())) - 90f;
		m.setYRot(yaw);
		m.setYHeadRot(yaw);
		m.setYBodyRot(yaw);
	}

	/** Whether a player looks one of them in the eye, as an Enderman notices it (a carved pumpkin hides the eyes). */
	public static boolean staring(ServerPlayer p, LivingEntity e) {
		if (p.getItemBySlot(EquipmentSlot.HEAD).is(Items.CARVED_PUMPKIN)) {
			return false;
		}
		Vec3 view = p.getViewVector(1.0f).normalize();
		Vec3 to = new Vec3(e.getX() - p.getX(), e.getEyeY() - p.getEyeY(), e.getZ() - p.getZ());
		double d = to.length();
		if (d < 0.5) {
			return false;
		}
		return view.dot(to.scale(1 / d)) > 1.0 - 0.025 / d && p.hasLineOfSight(e);
	}

	/** The build under way for a player, or null (for the tests). */
	public static Build of(UUID player) {
		return BUILDS.get(player);
	}

	static String force(ServerPlayer p, long now) {
		if (BUILDS.containsKey(p.getUUID())) {
			return "commands.extremedimensions.event.busy";
		}
		return start(p, now);
	}

	/** Leaving the world: every build stops and its builders go. */
	public static void clear() {
		for (Build b : BUILDS.values()) {
			for (UUID id : b.builders) {
				if (b.level.getEntity(id) instanceof Mob m) {
					m.discard();
				}
			}
		}
		BUILDS.clear();
		WorldFx.run("kill @e[tag=" + TAG + "]");
	}
}
