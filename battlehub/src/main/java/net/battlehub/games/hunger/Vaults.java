package net.battlehub.games.hunger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.battlehub.config.ConfigManager;
import net.battlehub.core.hunger.Crack;
import net.battlehub.core.hunger.Favour;
import net.battlehub.lang.Lang;
import net.battlehub.util.Msg;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Display;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.phys.Vec3;

/**
 * Loot you have to earn: vaults in the landmarks open with a key, sealed crates in the houses after five seconds of
 * sneaking beside them ({@link Crack}), and the Capitol's chest in the cornucopia only at the Feast, when tables of the
 * best loot appear round the cornucopia. A floating text over each says what it takes.
 */
public final class Vaults {
	private final HungerHost host;
	private final Capitol capitol;
	private final Map<BlockPos, Display.TextDisplay> vaults = new LinkedHashMap<>();
	private final Map<BlockPos, Crate> crates = new LinkedHashMap<>();
	private final Set<BlockPos> open = new HashSet<>();
	private final List<BlockPos> feast = new ArrayList<>();
	private final Set<UUID> feasted = new HashSet<>();
	private BlockPos capitolChest;
	private Display.TextDisplay capitolLabel;
	private int vaultsOpened, cratesOpened;

	private static final class Crate {
		final Crack crack = new Crack();
		Display.TextDisplay label;
		UUID by;
	}

	Vaults(HungerHost host, Capitol capitol) {
		this.host = host;
		this.capitol = capitol;
	}

	// ------------------------------------------------------------------ setting up

	/** Picks the vaults, crates and the Capitol's chest among the city's chests and fills them (after the plain fill). */
	void setUp() {
		var server = host.level().getServer();
		var cfg = ConfigManager.hungerGames();
		List<BlockPos> landmarks = new ArrayList<>(host.arena().marks("landmark_chest"));
		Collections.shuffle(landmarks, host.rng());
		int nv = landmarks.size() < 2 ? landmarks.size() : Math.max(2, (int) Math.round(landmarks.size() * 0.4));
		for (BlockPos pos : landmarks.subList(0, nv)) {
			fill(pos, Capitol.roll(server, cfg.vault(), 6, host.rng()));
			vaults.put(pos.immutable(), label(pos, Lang.tr("hg.vault.label")));
		}
		List<BlockPos> houses = new ArrayList<>(host.arena().marks("chest"));
		Collections.shuffle(houses, host.rng());
		int nc = Math.min(houses.size(), Math.max(3, (int) Math.round(houses.size() * 0.12)));
		for (BlockPos pos : houses.subList(0, nc)) {
			fill(pos, Capitol.roll(server, cfg.crate(), 5, host.rng()));
			Crate c = new Crate();
			c.label = label(pos, Lang.tr("hg.crate.label"));
			crates.put(pos.immutable(), c);
		}
		Vec3 mid = host.plaza();
		double best = Double.MAX_VALUE;
		for (BlockPos pos : host.arena().marks("cornucopia")) {
			double d = Vec3.atCenterOf(pos).distanceTo(mid);
			if (d < best) {
				best = d;
				capitolChest = pos.immutable();
			}
		}
		if (capitolChest != null) {
			fill(capitolChest, Capitol.roll(server, cfg.capitol(), 6, host.rng()));
			capitolLabel = label(capitolChest, Lang.tr("hg.capitol.label"));
		}
	}

	private Display.TextDisplay label(BlockPos pos, net.minecraft.network.chat.Component text) {
		return Holo.text(host.level(), Vec3.atBottomCenterOf(pos).add(0, 1.25, 0), text, 0.6f);
	}

	private void fill(BlockPos pos, List<ItemStack> items) {
		if (!(host.level().getBlockEntity(pos) instanceof Container c)) return;
		c.clearContent();
		List<Integer> free = new ArrayList<>();
		for (int i = 0; i < c.getContainerSize(); i++) free.add(i);
		Collections.shuffle(free, host.rng());
		int n = 0;
		for (ItemStack st : items) {
			if (!st.isEmpty() && n < free.size()) c.setItem(free.get(n++), st);
		}
		c.setChanged();
	}

	// ------------------------------------------------------------------ opening

	/** Whether this container is still closed to everyone (a locked vault, a sealed crate, the Capitol before the Feast). */
	public boolean locked(BlockPos pos) {
		if (open.contains(pos)) return false;
		return vaults.containsKey(pos) || crates.containsKey(pos) || pos.equals(capitolChest);
	}

	public boolean isVault(BlockPos pos) {
		return vaults.containsKey(pos);
	}

	public boolean isCrate(BlockPos pos) {
		return crates.containsKey(pos);
	}

	public BlockPos capitolChest() {
		return capitolChest;
	}

	/**
	 * A tribute opens a container: a locked vault opens with a key (used up), a sealed crate or the Capitol's chest
	 * stays shut with a word on how; a Feast chest wins favour once. PASS lets the game open it.
	 */
	InteractionResult use(ServerPlayer p, BlockPos pos, ItemStack held) {
		if (feast.contains(pos) && feasted.add(p.getUUID())) capitol.sponsors().favour(p.getUUID(), Favour.FEAST);
		if (!locked(pos)) return InteractionResult.PASS;
		if (vaults.containsKey(pos)) {
			if (!PowerUps.isKey(held)) {
				Msg.actionBar(p, "hg.vault.needs_key");
				host.level().playSound(null, pos, SoundEvents.CHEST_LOCKED, SoundSource.BLOCKS, 1f, 1f);
				return InteractionResult.FAIL;
			}
			held.shrink(1);
			unlockVault(pos, p);
			return InteractionResult.PASS;
		}
		if (crates.containsKey(pos)) {
			Msg.actionBar(p, "hg.crate.sealed");
			host.level().playSound(null, pos, SoundEvents.CHEST_LOCKED, SoundSource.BLOCKS, 1f, 1.3f);
			return InteractionResult.FAIL;
		}
		Msg.actionBar(p, "hg.capitol.sealed");
		host.level().playSound(null, pos, SoundEvents.CHEST_LOCKED, SoundSource.BLOCKS, 1f, 0.7f);
		return InteractionResult.FAIL;
	}

	/** A vault opened with a key (a player clicked it, or a bot with a key stands at it). */
	public void unlockVault(BlockPos pos, ServerPlayer by) {
		if (!vaults.containsKey(pos) || open.contains(pos)) return;
		open.add(pos);
		vaultsOpened++;
		Holo.setText(vaults.get(pos), Lang.tr("hg.vault.open"));
		ServerLevel level = host.level();
		level.playSound(null, pos, SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 1f, 0.8f);
		level.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, 12, 0.4, 0.3, 0.4, 0);
		for (ServerPlayer o : host.audience()) Msg.send(o, "hg.vault.opened", host.name(by.getUUID()));
	}

	// ------------------------------------------------------------------ the Feast

	/** The Feast: the Capitol's chest opens and tables of the best loot appear round the cornucopia. */
	void feast() {
		var server = host.level().getServer();
		if (capitolChest != null && open.add(capitolChest)) Holo.setText(capitolLabel, Lang.tr("hg.capitol.open"));
		Vec3 mid = host.plaza();
		ServerLevel level = host.level();
		for (int i = 0; i < 4; i++) {
			double a = Math.PI / 4 + i * Math.PI / 2;
			BlockPos pos = spot(mid.x + Math.cos(a) * 9, mid.z + Math.sin(a) * 9, mid.y);
			if (pos == null) continue;
			level.setBlockAndUpdate(pos, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, net.minecraft.core.Direction.NORTH));
			fill(pos, Capitol.roll(server, ConfigManager.hungerGames().feast(), 6, host.rng()));
			feast.add(pos.immutable());
			level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, 30, 0.4, 0.6, 0.4, 0.2);
		}
	}

	/** An empty spot to stand a chest on near (x, z), about at the plaza's height. */
	private BlockPos spot(double x, double z, double nearY) {
		ServerLevel level = host.level();
		BlockPos at = BlockPos.containing(x, nearY + 4, z);
		for (int i = 0; i < 10; i++) {
			if (level.getBlockState(at).isAir() && !level.getBlockState(at.below()).isAir()) return at;
			at = at.below();
		}
		return null;
	}

	/** The Feast's chests that still hold loot (bots go there). */
	public List<BlockPos> feastChests() {
		List<BlockPos> out = new ArrayList<>();
		for (BlockPos pos : feast) {
			if (host.level().getBlockEntity(pos) instanceof Container c && !c.isEmpty()) out.add(pos);
		}
		return out;
	}

	// ------------------------------------------------------------------ cracking crates

	void tick() {
		for (Map.Entry<BlockPos, Crate> e : crates.entrySet()) {
			BlockPos pos = e.getKey();
			Crate c = e.getValue();
			if (open.contains(pos)) continue;
			Vec3 mid = Vec3.atCenterOf(pos);
			ServerPlayer by = null;
			for (ServerPlayer p : host.tributes()) {
				boolean close = p.isShiftKeyDown() && p.position().distanceTo(mid) <= Crack.REACH;
				if (!close) continue;
				if (p.getUUID().equals(c.by)) {
					by = p;
					break;
				}
				if (by == null) by = p;
			}
			if (by == null) {
				c.crack.tick(false, false);
				c.by = null;
				continue;
			}
			if (!by.getUUID().equals(c.by)) c.crack.tick(false, false);
			c.by = by.getUUID();
			boolean opened = c.crack.tick(true, by.hurtTime > 0);
			if (host.runTicks() % 5 == 0 && !opened) Msg.actionBar(by, "hg.crate.progress", (int) Math.round(c.crack.share() * 100));
			if (opened) {
				open.add(pos);
				cratesOpened++;
				Holo.remove(c.label);
				host.level().playSound(null, pos, SoundEvents.CHAIN_BREAK, SoundSource.BLOCKS, 1f, 0.9f);
				host.level().sendParticles(ParticleTypes.CRIT, mid.x, mid.y + 0.5, mid.z, 20, 0.4, 0.3, 0.4, 0.2);
				Msg.actionBar(by, "hg.crate.open");
			}
		}
	}

	/** The nearest sealed crate within {@code range} of {@code at}, or null (bots). */
	public BlockPos nearestCrate(Vec3 at, double range) {
		BlockPos best = null;
		double bd = range;
		for (BlockPos pos : crates.keySet()) {
			if (open.contains(pos)) continue;
			double d = Vec3.atCenterOf(pos).distanceTo(at);
			if (d < bd) {
				bd = d;
				best = pos;
			}
		}
		return best;
	}

	/** The nearest locked vault within {@code range}, or null (bots with a key). */
	public BlockPos nearestVault(Vec3 at, double range) {
		BlockPos best = null;
		double bd = range;
		for (BlockPos pos : vaults.keySet()) {
			if (open.contains(pos)) continue;
			double d = Vec3.atCenterOf(pos).distanceTo(at);
			if (d < bd) {
				bd = d;
				best = pos;
			}
		}
		return best;
	}

	public int vaults() {
		return vaults.size();
	}

	public int crates() {
		return crates.size();
	}

	public int vaultsOpened() {
		return vaultsOpened;
	}

	public int cratesOpened() {
		return cratesOpened;
	}

	/** Progress of the crate at {@code pos}, 0..1 (self-test). */
	public double crateShare(BlockPos pos) {
		Crate c = crates.get(pos);
		return c == null ? 0 : c.crack.share();
	}

	void clear() {
		for (Display.TextDisplay d : vaults.values()) Holo.remove(d);
		for (Crate c : crates.values()) Holo.remove(c.label);
		Holo.remove(capitolLabel);
	}
}
