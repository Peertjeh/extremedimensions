package net.battlehub.games.hunger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.battlehub.config.ConfigManager;
import net.battlehub.core.hunger.Favour;
import net.battlehub.core.hunger.GiftPick;
import net.battlehub.data.PlayerStore;
import net.battlehub.lang.Lang;
import net.battlehub.util.Ids;
import net.battlehub.util.Msg;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Display;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * The sponsors: every tribute earns their favour ({@link Favour}) and at each threshold a silver parachute brings
 * what it needs most ({@link GiftPick}). Watchers and the fallen can sponsor a tribute themselves with their coins.
 * A parachute is a barrel under a white canopy that floats down; where it lands it becomes a barrel with the gift.
 * The Gamemakers' supply drops come the same way.
 */
public final class Sponsors {
	/** What a watcher can send, and what it costs. */
	public enum Gift {
		FOOD(40, Items.COOKED_BEEF), HEAL(90, Items.GOLDEN_APPLE), WEAPON(120, Items.IRON_SWORD), POWER(150, Items.NETHER_STAR);

		public final int cost;
		public final Item icon;

		Gift(int cost, Item icon) {
			this.cost = cost;
			this.icon = icon;
		}
	}

	/** How many gifts one watcher may send in a match. */
	public static final int WATCHER_GIFTS = 3;

	private final HungerHost host;
	private final Map<UUID, Integer> points = new HashMap<>();
	private final Map<UUID, Integer> given = new HashMap<>();
	private final Map<UUID, Integer> damage = new HashMap<>();
	private final Map<UUID, Integer> sent = new HashMap<>();
	private final List<Parachute> falling = new ArrayList<>();
	/** Barrels from parachutes on the ground: for whom (null: a supply drop, anyone's). */
	private final Map<BlockPos, UUID> landed = new LinkedHashMap<>();
	private boolean firstBlood;
	private int parachutes;

	Sponsors(HungerHost host) {
		this.host = host;
	}

	// ------------------------------------------------------------------ favour

	/** Adds favour; a threshold reached sends a parachute to the tribute. */
	public void favour(UUID id, int pts) {
		int now = points.merge(id, pts, Integer::sum);
		int due = Favour.giftsEarned(now);
		int had = given.getOrDefault(id, 0);
		if (due <= had) return;
		given.put(id, due);
		for (ServerPlayer p : host.tributes()) {
			if (p.getUUID().equals(id)) gift(p, pickFor(p), false);
		}
	}

	/** Just enough favour for the next parachute (admins and the self-test). */
	public void favourToNext(UUID id) {
		int now = points(id);
		favour(id, Math.max(1, Favour.threshold(Favour.giftsEarned(now)) - now));
	}

	public int points(UUID id) {
		return points.getOrDefault(id, 0);
	}

	/** A kill: favour, and the first of the match more. */
	void killed(UUID killer) {
		favour(killer, Favour.KILL + (firstBlood ? 0 : Favour.FIRST_BLOOD));
		firstBlood = true;
	}

	/** Damage dealt to another tribute: favour per full ten. */
	void hit(UUID attacker, float amount) {
		int before = damage.getOrDefault(attacker, 0);
		int after = before + Math.round(amount * 10);
		damage.put(attacker, after);
		int tens = after / 100 - before / 100;
		if (tens > 0) favour(attacker, tens * Favour.DAMAGE_TEN);
	}

	void tick() {
		int t = host.runTicks();
		if (t > 0 && t % (60 * 20) == 0) {
			for (ServerPlayer p : host.tributes()) favour(p.getUUID(), Favour.MINUTE_ALIVE);
		}
		for (Iterator<Parachute> it = falling.iterator(); it.hasNext();) {
			if (!it.next().tick()) it.remove();
		}
	}

	private GiftPick.Kind pickFor(ServerPlayer p) {
		boolean weapon = false;
		for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
			String id = Ids.key(p.getInventory().getItem(i).getItem());
			if (id.endsWith("_sword") || id.endsWith("_axe") || id.equals("minecraft:bow") || id.equals("minecraft:crossbow")
					|| id.equals("minecraft:trident") || id.equals("minecraft:mace")) weapon = true;
		}
		return GiftPick.pick(p.getHealth() / p.getMaxHealth(), p.getFoodData().getFoodLevel(), weapon);
	}

	/** A parachute with {@code kind} for {@code p}, landing a few blocks from it; {@code byWatcher}: a watcher paid. */
	private void gift(ServerPlayer p, GiftPick.Kind kind, boolean byWatcher) {
		drop(landingNear(p), "gift:" + kind.name().toLowerCase(java.util.Locale.ROOT), p.getUUID());
		for (ServerPlayer o : host.audience()) {
			Msg.send(o, o == p ? "hg.sponsor.yours" : "hg.sponsor.other", host.name(p.getUUID()), Lang.tr(o, "hg.gift." + kind.name().toLowerCase(java.util.Locale.ROOT)));
		}
		Msg.sound(p, SoundEvents.NOTE_BLOCK_CHIME, 1f, 1.6f);
	}

	/**
	 * Where a parachute for {@code p} comes down: a few blocks away on about the tribute's own height, where one can
	 * stand, under the open sky when there is such a spot (not on a roof, not in a closed room), else beside it.
	 */
	private Vec3 landingNear(ServerPlayer p) {
		for (int pass = 0; pass < 2; pass++) {
			for (int attempt = 0; attempt < 24; attempt++) {
				double ang = host.rng().nextDouble() * Math.PI * 2, r = pass == 0 ? 2.5 + host.rng().nextDouble() * 3 : 1.5 + host.rng().nextDouble();
				BlockPos spot = standable(BlockPos.containing(p.getX() + Math.cos(ang) * r, p.getY(), p.getZ() + Math.sin(ang) * r));
				if (spot != null && (pass == 1 || host.level().canSeeSky(spot))) return Vec3.atBottomCenterOf(spot);
			}
		}
		return p.position();
	}

	/** A spot to stand on in the column of {@code near}, at most two blocks up or down from it, or null. */
	private BlockPos standable(BlockPos near) {
		ServerLevel level = host.level();
		for (int dy : new int[] {0, 1, -1, 2, -2}) {
			BlockPos at = near.above(dy);
			if (free(level, at) && free(level, at.above()) && !free(level, at.below())) return at;
		}
		return null;
	}

	private static boolean free(ServerLevel level, BlockPos pos) {
		return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
	}

	// ------------------------------------------------------------------ watchers sponsor

	/** Whether {@code watcher} may still send a gift (not playing, gifts left). */
	public boolean maySponsor(ServerPlayer watcher) {
		for (ServerPlayer p : host.tributes()) {
			if (p == watcher) return false;
		}
		return sent.getOrDefault(watcher.getUUID(), 0) < WATCHER_GIFTS;
	}

	public int giftsLeft(ServerPlayer watcher) {
		return Math.max(0, WATCHER_GIFTS - sent.getOrDefault(watcher.getUUID(), 0));
	}

	/** A watcher pays for a gift to a tribute; false (and a message) when it cannot. */
	public boolean sponsor(ServerPlayer watcher, UUID tribute, Gift gift) {
		if (!maySponsor(watcher)) {
			Msg.send(watcher, "hg.sponsor.none_left");
			return false;
		}
		ServerPlayer t = null;
		for (ServerPlayer p : host.tributes()) {
			if (p.getUUID().equals(tribute)) t = p;
		}
		if (t == null) {
			Msg.send(watcher, "hg.sponsor.gone");
			return false;
		}
		var prof = PlayerStore.get(watcher);
		if (prof.coins < gift.cost) {
			Msg.send(watcher, "hg.sponsor.too_expensive", gift.cost - prof.coins);
			return false;
		}
		prof.coins -= gift.cost;
		PlayerStore.save(watcher.getUUID());
		sent.merge(watcher.getUUID(), 1, Integer::sum);
		gift(t, GiftPick.Kind.valueOf(gift.name()), true);
		Msg.send(watcher, "hg.sponsor.sent", host.name(tribute), gift.cost);
		Msg.send(t, "hg.sponsor.from", watcher.getGameProfile().name());
		return true;
	}

	// ------------------------------------------------------------------ parachutes

	/** A parachute landing at {@code at} with loot of {@code table} ("supply" or "gift:<kind>"), for someone or anyone. */
	public void drop(Vec3 at, String table, UUID forWhom) {
		falling.add(new Parachute(at, table, forWhom));
		parachutes++;
	}

	/** The barrels parachutes left, and for whom (bots go for theirs). */
	public Map<BlockPos, UUID> landed() {
		return landed;
	}

	/** Parachutes sent this match (self-test). */
	public int parachutes() {
		return parachutes;
	}

	public int falling() {
		return falling.size();
	}

	private final class Parachute {
		final Vec3 land;
		final String table;
		final UUID forWhom;
		final Display.BlockDisplay crate, canopy;
		double y;

		Parachute(Vec3 land, String table, UUID forWhom) {
			this.land = land;
			this.table = table;
			this.forWhom = forWhom;
			y = land.y + 28;
			ServerLevel level = host.level();
			crate = Holo.block(level, new Vec3(land.x, y, land.z), Blocks.BARREL.defaultBlockState(), 0.8f);
			canopy = Holo.block(level, new Vec3(land.x, y + 1.4, land.z),
					Ids.state(forWhom == null ? "minecraft:red_wool" : "minecraft:white_wool"), 2.2f);
		}

		boolean tick() {
			ServerLevel level = host.level();
			y -= 0.22;
			if (crate != null) crate.setPos(land.x, y, land.z);
			if (canopy != null) canopy.setPos(land.x, y + 1.4, land.z);
			if (host.runTicks() % 4 == 0) level.sendParticles(ParticleTypes.CLOUD, land.x, y + 2.2, land.z, 2, 0.6, 0.1, 0.6, 0.01);
			if (y > land.y) return true;
			Holo.remove(crate);
			Holo.remove(canopy);
			BlockPos pos = BlockPos.containing(land);
			for (int i = 0; i < 4 && !free(level, pos); i++) pos = pos.above();
			level.setBlockAndUpdate(pos, Blocks.BARREL.defaultBlockState());
			if (level.getBlockEntity(pos) instanceof Container c) {
				fill(c);
				landed.put(pos.immutable(), forWhom);
			}
			level.sendParticles(ParticleTypes.POOF, land.x, land.y + 0.5, land.z, 16, 0.4, 0.3, 0.4, 0.05);
			level.playSound(null, land.x, land.y, land.z, SoundEvents.BARREL_CLOSE, SoundSource.BLOCKS, 1f, 0.8f);
			return false;
		}

		private void fill(Container c) {
			List<ItemStack> items = new ArrayList<>();
			var server = host.level().getServer();
			if (table.startsWith("gift:")) {
				switch (table.substring(5)) {
					case "heal" -> {
						items.add(PowerUps.stack(server, "salve", 1));
						items.add(new ItemStack(Items.GOLDEN_APPLE));
					}
					case "food" -> {
						items.add(PowerUps.stack(server, "bread", 1));
						items.add(new ItemStack(Items.COOKED_BEEF, 4));
					}
					case "weapon" -> {
						if (host.rng().nextBoolean()) {
							items.add(new ItemStack(Items.IRON_SWORD));
						} else {
							items.add(new ItemStack(Items.BOW));
							items.add(new ItemStack(Items.ARROW, 12));
						}
					}
					default -> {
						List<String> kinds = new ArrayList<>(PowerUps.KINDS.keySet());
						kinds.remove("key");
						items.add(PowerUps.stack(server, kinds.get(host.rng().nextInt(kinds.size())), 1));
						if (host.rng().nextInt(3) == 0) items.add(PowerUps.stack(server, "key", 1));
					}
				}
			} else {
				items.addAll(Capitol.roll(server, ConfigManager.hungerGames().supply(), 3, host.rng()));
				if (host.rng().nextInt(5) < 2) items.add(PowerUps.stack(server, "key", 1));
			}
			int slot = 0;
			for (ItemStack st : items) {
				if (!st.isEmpty() && slot < c.getContainerSize()) c.setItem(slot++, st);
			}
			c.setChanged();
		}

		void clear() {
			Holo.remove(crate);
			Holo.remove(canopy);
		}
	}

	void clear() {
		for (Parachute p : falling) p.clear();
		falling.clear();
	}
}
