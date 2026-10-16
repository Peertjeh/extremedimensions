package net.battlehub.games;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.battlehub.arena.Blueprint;
import net.battlehub.arena.Theme;
import net.battlehub.arena.layout.NewLayouts;
import net.battlehub.core.GameMode;
import net.battlehub.core.MurderRoles;
import net.battlehub.lang.Lang;
import net.battlehub.util.Msg;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * Murder Mystery: one murderer (a sword after a few seconds; one hit kills), one detective (a bow, an arrow back
 * a few seconds after every shot), the rest innocent. Innocents collect gold: enough gold buys a bow. Shooting
 * an innocent kills you too. When the detective dies the bow stays on the ground for anyone to pick up. The
 * innocents win when the murderer is shot or time runs out; the murderer wins when nobody else is left.
 */
public final class MurderMysteryMatch extends PartyMatch {
	private MurderRoles.Roles roles;
	private final Map<UUID, Integer> arrowAt = new HashMap<>();
	private final List<ItemEntity> gold = new ArrayList<>();
	private ItemEntity droppedBow;
	private boolean swordGiven;

	public MurderMysteryMatch(int id, MinecraftServer server, List<ServerPlayer> players) {
		super(id, GameMode.MURDER_MYSTERY, server, players);
	}

	@Override
	protected int minimumToStart() {
		return 3;
	}

	@Override
	public net.battlehub.core.BotStyle botStyle() {
		return net.battlehub.core.BotStyle.MELEE;
	}

	@Override
	protected Blueprint design(Theme theme) {
		return NewLayouts.murder(theme, random);
	}


	public MurderRoles.Role role(UUID id) {
		return roles == null ? MurderRoles.Role.INNOCENT : roles.of(id);
	}

	public MurderRoles.Roles roles() {
		return roles;
	}

	@Override
	public boolean isEnemy(UUID a, UUID b) {
		return role(a) == MurderRoles.Role.MURDERER || role(b) == MurderRoles.Role.MURDERER;
	}

	@Override
	protected void onGo() {
		roles = MurderRoles.pick(new ArrayList<>(alive), new java.util.Random(random.nextLong()));
		for (UUID id : alive) {
			ServerPlayer p = player(id);
			if (p == null) continue;
			String r = role(id).name().toLowerCase(java.util.Locale.ROOT);
			Msg.title(p, Lang.tr(p, "mm.role." + r), Lang.tr(p, "mm.role." + r + ".sub"), 5, 60, 15);
			if (role(id) == MurderRoles.Role.DETECTIVE) {
				giveBow(p);
			}
		}
	}

	private int seconds(String key, double fallback) {
		return (int) Math.max(1, game().setting(key, fallback));
	}

	private void giveBow(ServerPlayer p) {
		if (p.getInventory().countItem(Items.BOW) == 0) {
			p.getInventory().setItem(1, new ItemStack(Items.BOW));
		}
		if (p.getInventory().countItem(Items.ARROW) == 0) {
			p.getInventory().setItem(8, new ItemStack(Items.ARROW));
		}
		p.inventoryMenu.sendAllDataToRemote();
	}

	@Override
	public void recordShot(ServerPlayer shooter) {
		super.recordShot(shooter);
		if (phase == Phase.RUNNING && roles != null) {
			arrowAt.put(shooter.getUUID(), runTicks + seconds("arrowCooldownSeconds", 4) * 20);
		}
	}

	/** Only the murderer's sword and arrows hurt, and both kill at once. */
	@Override
	protected boolean damageAllowed(ServerPlayer victim, DamageSource source, ServerPlayer attacker) {
		if (roles == null || attacker == null || attacker == victim || phase != Phase.RUNNING) {
			return false;
		}
		boolean arrow = source.getDirectEntity() instanceof Projectile;
		MurderRoles.Role killer = role(attacker.getUUID());
		if (!arrow) {
			if (killer == MurderRoles.Role.MURDERER && attacker.getMainHandItem().is(Items.IRON_SWORD)) {
				killed(victim, attacker, false);
			}
			return false;
		}
		if (killer == MurderRoles.Role.MURDERER) {
			return false;
		}
		boolean shotMurderer = role(victim.getUUID()) == MurderRoles.Role.MURDERER;
		killed(victim, attacker, false);
		if (!shotMurderer && alive.contains(attacker.getUUID()) && phase == Phase.RUNNING) {
			// Shooting an innocent costs your own life
			Msg.send(attacker, "mm.wrong_kill");
			killed(attacker, null, false);
		}
		return false;
	}

	@Override
	protected void onKilled(ServerPlayer victim, ServerPlayer killer) {
		if (role(victim.getUUID()) == MurderRoles.Role.DETECTIVE && victim.getInventory().countItem(Items.BOW) > 0) {
			dropBow(victim.position());
			forEveryone(o -> Msg.send(o, "mm.detective_dead"));
		}
		eliminate(victim);
	}

	private void dropBow(Vec3 at) {
		ItemEntity e = new ItemEntity(arena.level(), at.x, at.y + 0.5, at.z, new ItemStack(Items.BOW));
		e.setGlowingTag(true);
		e.setUnlimitedLifetime();
		arena.level().addFreshEntity(e);
		droppedBow = e;
	}

	@Override
	protected void checkEnd() {
		if (roles == null) {
			if (alive.size() <= 1) finish(new ArrayList<>(alive));
			return;
		}
		MurderRoles.Role w = MurderRoles.winner(roles, alive);
		if (w == MurderRoles.Role.MURDERER) {
			finish(List.of(roles.murderer()));
		} else if (w == MurderRoles.Role.INNOCENT) {
			finish(innocents());
		}
	}

	/** Everyone but the murderer who did not leave. */
	private List<UUID> innocents() {
		List<UUID> out = new ArrayList<>();
		for (UUID id : participants) {
			if (roles != null && !id.equals(roles.murderer()) && !left.contains(id)) out.add(id);
		}
		return out;
	}

	@Override
	protected void onTimeUp() {
		forEveryone(o -> Msg.send(o, "mm.time_up"));
		finish(innocents());
	}

	@Override
	protected void tickGame() {
		if (roles == null) {
			return;
		}
		ServerPlayer murderer = player(roles.murderer());
		if (!swordGiven && runTicks >= seconds("swordAfterSeconds", 10) * 20 && murderer != null) {
			swordGiven = true;
			murderer.getInventory().setItem(1, new ItemStack(Items.IRON_SWORD));
			murderer.inventoryMenu.sendAllDataToRemote();
			Msg.send(murderer, "mm.sword");
			forEveryone(o -> Msg.sound(o, SoundEvents.WITHER_SPAWN, 0.25f, 1.6f));
		}
		// The murderer cannot use a bow: one picked up goes back where it was
		if (murderer != null && murderer.getInventory().countItem(Items.BOW) > 0) {
			take(murderer, Items.BOW, -1);
			dropBow(murderer.position());
		}
		// Arrows come back a while after a shot
		arrowAt.entrySet().removeIf(e -> {
			if (runTicks < e.getValue()) return false;
			ServerPlayer p = player(e.getKey());
			if (p != null && alive.contains(e.getKey()) && p.getInventory().countItem(Items.BOW) > 0
					&& p.getInventory().countItem(Items.ARROW) == 0) {
				p.getInventory().add(new ItemStack(Items.ARROW));
			}
			return true;
		});
		// Gold now and then; enough gold buys a bow
		if (runTicks % (seconds("goldEverySeconds", 3) * 20) == 0) {
			spawnGold();
		}
		int need = seconds("goldToBow", 10);
		for (UUID id : alive) {
			ServerPlayer p = player(id);
			if (p == null || role(id) == MurderRoles.Role.MURDERER) continue;
			int g = p.getInventory().countItem(Items.GOLD_INGOT);
			if (g >= need) {
				take(p, Items.GOLD_INGOT, need);
				giveBow(p);
				Msg.send(p, "mm.bow_bought");
				Msg.sound(p, SoundEvents.PLAYER_LEVELUP, 0.6f, 1.4f);
			} else if (g > 0 && runTicks % 20 == 0) {
				Msg.actionBar(p, "mm.gold", g, need);
			}
		}
	}

	/** Removes {@code count} of an item (-1: all of it). */
	private static void take(ServerPlayer p, net.minecraft.world.item.Item item, int count) {
		var inv = p.getInventory();
		for (int i = 0; i < inv.getContainerSize() && count != 0; i++) {
			ItemStack s = inv.getItem(i);
			if (!s.is(item)) continue;
			int n = count < 0 ? s.getCount() : Math.min(count, s.getCount());
			s.shrink(n);
			if (count > 0) count -= n;
		}
		p.inventoryMenu.sendAllDataToRemote();
	}

	/** A gold ingot on a free spot of the floor (at most twenty lie around). */
	private void spawnGold() {
		gold.removeIf(e -> e.isRemoved());
		if (gold.size() >= 20) {
			return;
		}
		ServerLevel level = arena.level();
		List<BlockPos> spots = arena.marks("gold");
		BlockPos at = null;
		if (!spots.isEmpty()) {
			at = spots.get(random.nextInt(spots.size()));
		} else {
			var b = arena.bounds().deflate(6);
			for (int tries = 0; tries < 20 && at == null; tries++) {
				int x = (int) (b.minX + random.nextDouble() * (b.maxX - b.minX));
				int z = (int) (b.minZ + random.nextDouble() * (b.maxZ - b.minZ));
				BlockPos p = new BlockPos(x, arena.origin().getY() + 1, z);
				if (level.getBlockState(p).isAir() && !level.getBlockState(p.below()).isAir()) {
					at = p;
				}
			}
		}
		if (at == null) {
			return;
		}
		ItemEntity e = new ItemEntity(level, at.getX() + 0.5, at.getY() + 0.2, at.getZ() + 0.5, new ItemStack(Items.GOLD_INGOT));
		e.setDeltaMovement(Vec3.ZERO);
		level.addFreshEntity(e);
		gold.add(e);
	}

	@Override
	public boolean allowsDrops(ServerPlayer p) {
		return false;
	}

	@Override
	protected void onClosed() {
		for (ItemEntity e : gold) e.discard();
		gold.clear();
		if (droppedBow != null) droppedBow.discard();
	}

	@Override
	public Component status(ServerPlayer viewer) {
		return Lang.tr(viewer, "mm.status", alive.size());
	}
}
