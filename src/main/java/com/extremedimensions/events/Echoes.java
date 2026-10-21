package com.extremedimensions.events;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.extremedimensions.binding.Homebound;
import com.extremedimensions.config.Config;
import com.extremedimensions.data.DataStore;
import com.extremedimensions.data.Echo;
import com.extremedimensions.data.PlayerData;
import com.extremedimensions.journal.Journal;
import com.extremedimensions.journal.JournalEntry;
import com.extremedimensions.penalty.PenaltyService;
import com.extremedimensions.progression.Advancements;
import com.extremedimensions.registry.ModItems;
import com.extremedimensions.server.Dims;
import com.extremedimensions.server.WorldFx;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

/**
 * Echoes of Collapse. Where someone collapsed beyond the wound the place remembers: some while later, whoever comes
 * near finds their shape kneeling there in chains, and hears it. Stand by it and listen until it lets go, and it gives
 * something back: to the listener a fragment and a little atonement; to the one who collapsed half of what that death
 * cost them (waiting for them if they are away); in a severed world, one of the things it destroyed. While anyone
 * listens, everyone close drains twice as fast (not in a mended world: the wound is closed, but it remembers).
 */
public final class Echoes {
	private Echoes() {}

	/** In a severed world: the first thing a collapsing player's death destroyed, until their echo is recorded. */
	private static final Map<UUID, String> LOST = new HashMap<>();

	/** A Collapse ended in death beyond the wound: the place remembers. Returns the echo (null when echoes are off). */
	public static Echo record(ServerPlayer p, String dim, int toll) {
		String lost = LOST.remove(p.getUUID());
		if (Config.ECHOES <= 0) {
			return null;
		}
		ServerLevel level = (ServerLevel) p.level();
		BlockPos at = ground(level, p.blockPosition());
		Echo e = new Echo(dim, at.getX(), at.getY(), at.getZ(), p.getStringUUID(), p.getName().getString(), System.currentTimeMillis(), toll);
		e.lost = lost;
		List<Echo> all = DataStore.echoes();
		all.add(e);
		while (all.size() > Math.max(1, Config.ECHO_MAX)) {
			hide(all.remove(0));
		}
		DataStore.markDirty();
		return e;
	}

	/** A severed world destroys what a collapsing player dropped: the echo keeps one of those things. */
	public static void lost(ServerPlayer p, ItemStack stack) {
		if (!stack.isEmpty() && !LOST.containsKey(p.getUUID())) {
			String json = Homebound.encode(p.level().registryAccess(), stack);
			if (json != null) {
				LOST.put(p.getUUID(), json);
			}
		}
	}

	/** The ground under where a player was held up in the pull (up to sixteen blocks down). */
	private static BlockPos ground(ServerLevel level, BlockPos from) {
		BlockPos.MutableBlockPos m = from.mutable();
		for (int i = 0; i < 16 && m.getY() > level.getMinY(); i++) {
			if (!level.getBlockState(m.below()).getCollisionShape(level, m.below()).isEmpty()) {
				return m.immutable();
			}
			m.move(0, -1, 0);
		}
		return from;
	}

	/** Twice a second: echoes show to whoever is near, are listened to, and let go. */
	public static void tick(MinecraftServer s) {
		if (s.getTickCount() % 10 == 7) {
			step(s);
		}
	}

	/** Half a second of every echo (for the tests too). */
	public static void step(MinecraftServer s) {
		long now = System.currentTimeMillis();
		boolean draw = s.getTickCount() % 20 < 10;
		for (Echo e : new ArrayList<>(DataStore.echoes())) {
			ServerLevel level = WorldFx.level(Dims.fullIdOf(e.dim));
			BlockPos at = new BlockPos(e.x, e.y, e.z);
			if (level == null || !level.isLoaded(at)) {
				continue;
			}
			// (once shown, it stays a little further out: walking the edge of its reach does not flicker it)
			double reach = Math.max(4, Config.ECHO_RANGE) + (e.shown ? 8 : 0);
			List<ServerPlayer> near = near(level, e, reach);
			if (Config.ECHOES <= 0 || now - e.at < Math.max(0, Config.ECHO_DELAY) * 1000L || near.isEmpty()) {
				if (e.shown) {
					hide(e);
				}
				e.heard.clear();
				continue;
			}
			if (!e.shown) {
				show(e);
			}
			for (ServerPlayer p : near) {
				if (e.told.add(p.getStringUUID())) {
					WorldFx.soundFor(p, "minecraft:block.chain.step", "ambient", e.x + 0.5, e.y + 1, e.z + 0.5, 1.0f, 0.5f);
					WorldFx.soundFor(p, "extremedimensions:lurker_whisper", "ambient", e.x + 0.5, e.y + 1, e.z + 0.5, 0.7f, 0.5f);
					p.sendSystemMessage(Component.translatable("gui.extremedimensions.echo.near").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC), true);
				}
			}
			if (draw) {
				shape(level, e);
			}
			ServerPlayer done = null;
			double listen = Math.max(1, Config.ECHO_LISTEN_RANGE);
			Set<String> listening = new HashSet<>();
			for (ServerPlayer p : near) {
				if (e.distSq(p.getX(), p.getY(), p.getZ()) > listen * listen) {
					continue;
				}
				String id = p.getStringUUID();
				listening.add(id);
				int heard = e.heard.merge(id, 10, Integer::sum);
				if (heard == 10) {
					p.sendSystemMessage(Component.translatable("gui.extremedimensions.echo.listening").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC), true);
				}
				if (heard % 60 == 0) {
					WorldFx.soundFor(p, "minecraft:particle.soul_escape", "ambient", e.x + 0.5, e.y + 1, e.z + 0.5, 1.0f, 0.6f);
				}
				if (heard >= Math.max(1, Config.ECHO_LISTEN_SECONDS) * 20 && done == null) {
					done = p;
				}
			}
			// Only those listening now count: whoever walked off, left the world or fell starts over when back
			e.heard.keySet().retainAll(listening);
			if (done != null) {
				release(level, e, done);
			}
		}
	}

	/** Who an echo shows to: the living near it, not spectators or creative players. */
	private static List<ServerPlayer> near(ServerLevel level, Echo e, double r) {
		List<ServerPlayer> out = new ArrayList<>();
		for (ServerPlayer p : level.players()) {
			if (p.isAlive() && !p.isSpectator() && !p.isCreative() && e.distSq(p.getX(), p.getY(), p.getZ()) <= r * r) {
				out.add(p);
			}
		}
		return out;
	}

	/** The chains rattle and something whispers: the chains stand where the shape will kneel, with its name over it. */
	private static void show(Echo e) {
		hide(e);
		e.shown = true;
		String dim = Dims.fullIdOf(e.dim), tag = e.tag();
		for (int[] c : new int[][] {{-1, -1}, {1, -1}, {-1, 1}, {1, 1}}) {
			WorldFx.run(String.format(Locale.ROOT, "execute in %s run summon minecraft:block_display %.2f %d %.2f "
					+ "{Tags:[\"ed_echo\",\"%s\"],block_state:{Name:\"minecraft:iron_chain\",Properties:{axis:\"y\"}},brightness:{sky:7,block:7},"
					+ "transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[-0.5f,0f,-0.5f],scale:[1f,3f,1f]}}",
					dim, e.x + 0.5 + c[0] * 0.9, e.y, e.z + 0.5 + c[1] * 0.9, tag));
		}
		WorldFx.run(String.format(Locale.ROOT, "execute in %s run summon minecraft:text_display %.2f %.2f %.2f "
				+ "{Tags:[\"ed_echo\",\"%s\"],billboard:\"center\",text:\"%s\",background:0,text_opacity:150,see_through:0b}",
				dim, e.x + 0.5, e.y + 2.4, e.z + 0.5, tag, e.name.replaceAll("[^A-Za-z0-9_]", "")));
	}

	/** Takes down an echo's chains and name. */
	static void hide(Echo e) {
		e.shown = false;
		e.told.clear();
		WorldFx.run("kill @e[type=minecraft:block_display,tag=" + e.tag() + "]");
		WorldFx.run("kill @e[type=minecraft:text_display,tag=" + e.tag() + "]");
	}

	/** The shape kneeling in the chains, drawn in soul fire: bowed head, arms held up by the chains, knees on the ground. */
	private static void shape(ServerLevel level, Echo e) {
		double cx = e.x + 0.5, cy = e.y, cz = e.z + 0.5;
		double[][] pts = {
				{0, 1.25, 0.05}, {0.12, 1.2, 0.05}, {-0.12, 1.2, 0.05}, {0, 1.33, 0.02},
				{0, 1.05, 0}, {0, 0.9, -0.03}, {0, 0.75, -0.05}, {0, 0.6, -0.05},
				{0.28, 1.05, 0}, {0.45, 1.3, 0}, {0.62, 1.55, 0}, {0.8, 1.8, 0},
				{-0.28, 1.05, 0}, {-0.45, 1.3, 0}, {-0.62, 1.55, 0}, {-0.8, 1.8, 0},
				{0.13, 0.5, 0.1}, {0.13, 0.35, 0.25}, {0.13, 0.12, 0.35}, {-0.13, 0.5, 0.1}, {-0.13, 0.35, 0.25}, {-0.13, 0.12, 0.35},
				{0.13, 0.08, 0.1}, {0.13, 0.06, -0.15}, {-0.13, 0.08, 0.1}, {-0.13, 0.06, -0.15}};
		double a = (e.tag().hashCode() & 0xff) / 256.0 * Math.PI * 2, cos = Math.cos(a), sin = Math.sin(a);
		for (double[] q : pts) {
			double x = q[0] * cos - q[2] * sin, z = q[0] * sin + q[2] * cos;
			level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, cx + x, cy + q[1], cz + z, 1, 0.01, 0.01, 0.01, 0.0);
		}
	}

	/** Heard out: the echo lets go, and gives back what it gives. */
	private static void release(ServerLevel level, Echo e, ServerPlayer listener) {
		hide(e);
		DataStore.echoes().remove(e);
		DataStore.markDirty();
		PlayerData d = DataStore.get(listener.getUUID());
		Journal.unlock(listener, d, JournalEntry.ECHOES);
		Advancements.grant(listener, Advancements.ECHO_HEARD);
		// Only another's echo pays the listener, and only once in a while: collapsing on purpose to hear oneself brings
		// back the toll, nothing more, and a row of echoes heard out together pays once
		long now = System.currentTimeMillis();
		boolean pays = !listener.getStringUUID().equals(e.owner) && now - d.echoPaidAt >= Math.max(0, Config.ECHO_PAY_COOLDOWN) * 1000L;
		if (pays) {
			d.echoPaidAt = now;
			WorldFx.give(listener, new ItemStack(ModItems.TETHER_FRAGMENT));
			if (Config.ECHO_ATONE > 0) {
				PenaltyService.atone(listener, d, false, Config.ECHO_ATONE);
			}
		}
		level.sendParticles(ParticleTypes.SCULK_SOUL, e.x + 0.5, e.y + 1, e.z + 0.5, 30, 0.4, 0.8, 0.4, 0.04);
		WorldFx.soundIn(Dims.fullIdOf(e.dim), "minecraft:block.chain.break", "ambient", e.x + 0.5, e.y + 1, e.z + 0.5, 1.0f, 0.6f);
		WorldFx.soundIn(Dims.fullIdOf(e.dim), "minecraft:particle.soul_escape", "ambient", e.x + 0.5, e.y + 1, e.z + 0.5, 1.2f, 0.8f);
		// (in the chat, where an atonement's own message does not wipe it out)
		listener.sendSystemMessage(pays ? Component.translatable("gui.extremedimensions.echo.released_paid", e.name, Math.max(0, Config.ECHO_ATONE))
				.withStyle(ChatFormatting.AQUA) : Component.translatable("gui.extremedimensions.echo.released", e.name).withStyle(ChatFormatting.AQUA));
		// To the one who collapsed: half of what that death cost them (all of it where the Heart panel is woven in a mended
		// world), and in a severed world one thing it destroyed
		int back = (int) Math.round(Math.max(0, e.toll)
				* com.extremedimensions.tapestry.Tapestry.echoShare(Math.max(0, Math.min(1, Config.ECHO_RETURN))));
		UUID ownerId;
		try {
			ownerId = UUID.fromString(e.owner);
		} catch (IllegalArgumentException ignored) {
			return;
		}
		ServerPlayer owner = level.getServer().getPlayerList().getPlayer(ownerId);
		PlayerData od = DataStore.get(ownerId);
		if (owner != null) {
			if (back > 0) {
				PenaltyService.atone(owner, od, false, back);
				owner.sendSystemMessage(Component.translatable("gui.extremedimensions.echo.returned", back).withStyle(ChatFormatting.AQUA));
			}
		} else {
			od.echoOwed += back;
		}
		if (e.lost != null) {
			ItemStack s = Homebound.decode(level.registryAccess(), e.lost);
			if (owner != null && owner.isAlive() && !s.isEmpty()) {
				WorldFx.give(owner, s);
				owner.sendSystemMessage(Component.translatable("gui.extremedimensions.homebound.restored").withStyle(ChatFormatting.AQUA));
			} else {
				// away, or on the death screen: it waits for them to be back on their feet
				od.homebound.add(e.lost);
			}
		}
		DataStore.markDirty();
	}

	/**
	 * An echo's chains or name loaded with their chunk: kept only while that echo shows (otherwise they were left behind
	 * by a stop, or by an echo that has since gone, and come down; the echo puts up its own when someone comes near).
	 */
	public static boolean stray(Entity entity) {
		if (!entity.entityTags().contains("ed_echo")) {
			return false;
		}
		for (Echo e : DataStore.echoes()) {
			if (e.shown && entity.entityTags().contains(e.tag())) {
				return false;
			}
		}
		return true;
	}

	/** The server stops: every echo's chains come down before the world is saved. */
	public static void clear() {
		for (Echo e : DataStore.echoes()) {
			if (e.shown) {
				hide(e);
			}
			e.heard.clear();
		}
	}

	/** An echo being listened to makes everyone near drain faster (not in a mended world). */
	public static double drainFactor(ServerPlayer p) {
		if (Config.ECHOES <= 0 || "mended".equals(DataStore.ending())) {
			return 1.0;
		}
		String dim = Dims.of(p);
		double r = Math.max(0, Config.ECHO_DRAIN_RANGE);
		for (Echo e : DataStore.echoes()) {
			if (!e.heard.isEmpty() && dim.equals(e.dim) && e.distSq(p.getX(), p.getY(), p.getZ()) <= r * r) {
				return Math.max(1, Config.ECHO_DRAIN);
			}
		}
		return 1.0;
	}

	/** Back in the world: what an echo gave back while the player was away. */
	public static void welcome(ServerPlayer p, PlayerData d) {
		if (d.echoOwed > 0) {
			int back = d.echoOwed;
			d.echoOwed = 0;
			PenaltyService.atone(p, d, false, back);
			p.sendSystemMessage(Component.translatable("gui.extremedimensions.echo.returned", back).withStyle(ChatFormatting.AQUA));
			DataStore.markDirty();
		}
	}

	/** An operator's echo: one where the player stands, ripe at once, in their own name. */
	static String force(ServerPlayer p) {
		Echo e = new Echo(Dims.of(p), p.getBlockX(), p.getBlockY(), p.getBlockZ(), p.getStringUUID(), p.getName().getString(),
				System.currentTimeMillis() - Math.max(0, Config.ECHO_DELAY) * 1000L - 1000L, 20);
		DataStore.echoes().add(e);
		DataStore.markDirty();
		return null;
	}

	/** Takes an echo away, chains and all, without its letting go (for the tests). */
	public static void forget(Echo e) {
		hide(e);
		DataStore.echoes().remove(e);
	}

	/** The echoes waiting in a world (for the tests). */
	public static List<Echo> all() {
		return DataStore.echoes();
	}
}
