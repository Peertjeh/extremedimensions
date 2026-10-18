package net.battlehub.games;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.battlehub.arena.Arena;
import net.battlehub.arena.Blueprint;
import net.battlehub.arena.Theme;
import net.battlehub.arena.layout.PartyLayouts;
import net.battlehub.core.CtfGame;
import net.battlehub.core.CtfGame.Team;
import net.battlehub.core.GameMode;
import net.battlehub.lang.Lang;
import net.battlehub.progression.TitleService;
import net.battlehub.util.Ids;
import net.battlehub.util.Msg;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Capture The Flag: two teams, two banners. Take the enemy banner to your own base while your banner is home.
 * First team to the capture target wins. Dead players drop the flag and respawn after a short delay.
 */
public final class CtfMatch extends PartyMatch {
	public static final int RED_COLOR = 0xB02E26;
	public static final int BLUE_COLOR = 0x3C44AA;

	private final CtfGame ctf;
	private final Map<Team, BlockPos> dropped = new EnumMap<>(Team.class);
	private final Map<UUID, ItemStack> savedHelmet = new HashMap<>();
	private final Map<UUID, Integer> teamIndex = new HashMap<>();

	public CtfMatch(int id, MinecraftServer server, List<ServerPlayer> players) {
		super(id, GameMode.CTF, server, players);
		ctf = new CtfGame((int) Math.max(1, game().setting("capturesToWin", 3)));
		List<UUID> shuffled = net.battlehub.core.TeamSplit.interleave(net.battlehub.core.TeamSplit.split(participants, 2,
				net.battlehub.social.Groups.among(participants), new java.util.Random(random.nextLong())));
		ctf.assign(shuffled);
		int r = 0;
		int b = 0;
		for (UUID uid : shuffled) {
			teamIndex.put(uid, ctf.teamOf(uid) == Team.RED ? r++ : b++);
		}
	}

	public CtfGame ctf() {
		return ctf;
	}

	@Override
	protected Blueprint design(Theme theme) {
		return PartyLayouts.ctf(theme, random);
	}

	private static String color(Team t) {
		return t == Team.RED ? "red" : "blue";
	}

	@Override
	protected int leatherColor(UUID id) {
		return ctf.teamOf(id) == Team.BLUE ? BLUE_COLOR : RED_COLOR;
	}

	@Override
	protected Arena.Point spawnPoint(UUID id, int index) {
		Team t = ctf.teamOf(id);
		List<BlockPos> spawns = arena.marks("spawn_" + color(t));
		BlockPos p = spawns.get(teamIndex.getOrDefault(id, 0) % spawns.size());
		return new Arena.Point(new Vec3(p.getX() + 0.5, p.getY(), p.getZ() + 0.5), t == Team.RED ? -90 : 90, 0);
	}

	@Override
	protected void onArrived(ServerPlayer p, int index) {
		Team t = ctf.teamOf(p.getUUID());
		TitleService.setOverride(p, Lang.tr(p, t == Team.RED ? "ctf.prefix_red" : "ctf.prefix_blue"));
		Msg.send(p, t == Team.RED ? "ctf.team_red" : "ctf.team_blue", ctf.captures(Team.RED), ((int) game().setting("capturesToWin", 3)));
	}

	@Override
	protected void onGo() {
		placeFlag(Team.RED, home(Team.RED));
		placeFlag(Team.BLUE, home(Team.BLUE));
	}

	@Override
	protected void onRestored(ServerPlayer p) {
		TitleService.clearOverride(p);
	}

	@Override
	protected boolean damageAllowed(ServerPlayer victim, DamageSource source, ServerPlayer attacker) {
		return attacker == null || attacker == victim || ctf.teamOf(attacker.getUUID()) != ctf.teamOf(victim.getUUID());
	}

	@Override
	protected void onKilled(ServerPlayer victim, ServerPlayer killer) {
		dropFlag(victim, victim.blockPosition());
		scheduleRespawn(victim);
	}

	@Override
	protected void fellOut(ServerPlayer p) {
		// A flag that falls into the void goes home.
		Team carried = ctf.carriedBy(p.getUUID());
		if (carried != null) {
			ctf.returnHome(carried);
			restoreHelmet(p);
			placeFlag(carried, home(carried));
			forEveryone(o -> Msg.send(o, "ctf.auto_returned", flagName(o, carried)));
		}
		super.fellOut(p);
	}

	@Override
	protected void onLeft(UUID id) {
		super.onLeft(id);
		Team carried = ctf.carriedBy(id);
		ctf.removePlayer(id);
		if (carried != null) {
			placeFlag(carried, home(carried));
		}
	}

	@Override
	protected void checkEnd() {
		boolean red = false;
		boolean blue = false;
		for (UUID id : alive) {
			if (ctf.teamOf(id) == Team.RED) red = true;
			if (ctf.teamOf(id) == Team.BLUE) blue = true;
		}
		if (!red || !blue) {
			Team w = red ? Team.RED : (blue ? Team.BLUE : null);
			finish(w == null ? List.of() : ctf.members(w));
		}
	}

	@Override
	public boolean isEnemy(UUID a, UUID b) {
		Team ta = ctf.teamOf(a);
		return ta == null || ta != ctf.teamOf(b);
	}

	/** Carrying a flag: home. Own flag on the ground nearby: bring it back. Otherwise: the enemy flag (or our carrier). */
	@Override
	public net.minecraft.world.phys.Vec3 botObjective(ServerPlayer bot) {
		Team mine = ctf.teamOf(bot.getUUID());
		if (mine == null) {
			return null;
		}
		Team enemy = mine.other();
		if (bot.getUUID().equals(ctf.carrier(enemy))) {
			return center(home(mine));
		}
		BlockPos ownDropped = dropped.get(mine);
		if (ctf.state(mine) == CtfGame.FlagState.DROPPED && ownDropped != null && bot.position().distanceTo(center(ownDropped)) < 24) {
			return center(ownDropped);
		}
		return switch (ctf.state(enemy)) {
			case HOME -> center(home(enemy));
			case DROPPED -> dropped.get(enemy) == null ? center(home(enemy)) : center(dropped.get(enemy));
			case CARRIED -> {
				ServerPlayer c = player(ctf.carrier(enemy));
				yield c == null ? center(home(mine)) : c.position();
			}
		};
	}

	/**
	 * Carrying the enemy flag: home, avoiding fights. With the own flag at home and a team of two or more, one bot
	 * guards it; the others go for the enemy flag (or after whoever has ours).
	 */
	@Override
	public net.battlehub.core.ai.Objective botGoal(ServerPlayer bot) {
		Team mine = ctf.teamOf(bot.getUUID());
		if (mine == null) return null;
		if (bot.getUUID().equals(ctf.carrier(mine.other()))) {
			return net.battlehub.core.ai.Objective.carry(v3(center(home(mine))), "the flag");
		}
		boolean home = ctf.state(mine) == CtfGame.FlagState.HOME;
		if (home && botRole(bot, true, center(home(mine))) == net.battlehub.core.ai.Roles.Role.DEFENDER) {
			return net.battlehub.core.ai.Objective.defend(v3(center(home(mine))), 3, 12, "our flag");
		}
		net.minecraft.world.phys.Vec3 p = botObjective(bot);
		return p == null ? null : net.battlehub.core.ai.Objective.attack(v3(p), "the enemy flag");
	}

	private static net.minecraft.world.phys.Vec3 center(BlockPos p) {
		return new net.minecraft.world.phys.Vec3(p.getX() + 0.5, p.getY(), p.getZ() + 0.5);
	}

	public BlockPos home(Team t) {
		return arena.mark("flag_" + color(t));
	}

	private void placeFlag(Team t, BlockPos pos) {
		ServerLevel level = arena.level();
		level.setBlock(pos, Ids.state("minecraft:" + color(t) + "_banner"), 2 | 16 | 32);
	}

	private void removeFlagBlock(BlockPos pos) {
		if (pos != null) {
			arena.level().setBlock(pos, Blocks.AIR.defaultBlockState(), 2 | 16 | 32);
		}
	}

	private void dropFlag(ServerPlayer carrier, BlockPos where) {
		CtfGame.Event e = ctf.drop(carrier.getUUID(), runTicks);
		if (e.type() != CtfGame.EventType.DROPPED) {
			return;
		}
		restoreHelmet(carrier);
		BlockPos pos = where;
		ServerLevel level = arena.level();
		for (int i = 0; i < 4 && !level.getBlockState(pos).isAir(); i++) {
			pos = pos.above();
		}
		if (pos.getY() < arena.origin().getY() || pos.getY() > arena.origin().getY() + 6) {
			pos = arena.origin().above();
		}
		dropped.put(e.flag(), pos);
		placeFlag(e.flag(), pos);
		Team flag = e.flag();
		forEveryone(p -> Msg.send(p, "ctf.dropped", name(carrier.getUUID()), flagName(p, flag)));
	}

	private Component flagName(ServerPlayer viewer, Team t) {
		return Lang.tr(viewer, t == Team.RED ? "ctf.flag_red" : "ctf.flag_blue");
	}

	private void restoreHelmet(ServerPlayer p) {
		ItemStack helmet = savedHelmet.remove(p.getUUID());
		if (helmet != null) {
			p.setItemSlot(EquipmentSlot.HEAD, helmet);
		}
		p.removeEffect(MobEffects.GLOWING);
	}

	@Override
	protected void tickGame() {
		tickRespawns();
		for (CtfGame.Event e : ctf.tick(runTicks, (int) Math.max(1, game().setting("flagReturnSeconds", 30)) * 20)) {
			removeFlagBlock(dropped.remove(e.flag()));
			placeFlag(e.flag(), home(e.flag()));
			forEveryone(p -> Msg.send(p, "ctf.auto_returned", flagName(p, e.flag())));
		}
		for (UUID id : new ArrayList<>(alive)) {
			ServerPlayer p = player(id);
			if (p == null || !active(id)) continue;
			Team mine = ctf.teamOf(id);
			if (mine == null) continue;
			for (Team flag : Team.values()) {
				BlockPos at = switch (ctf.state(flag)) {
					case HOME -> home(flag);
					case DROPPED -> dropped.get(flag);
					default -> null;
				};
				if (at != null && near(p, at, 1.3)) {
					handle(p, ctf.touchFlag(id, flag));
				}
			}
			if (near(p, home(mine), 2.5)) {
				handle(p, ctf.touchBase(id));
			}
			if (phase != Phase.RUNNING) {
				return;
			}
		}
		if (runTicks % 20 == 0) {
			for (UUID id : participants) {
				ServerPlayer p = player(id);
				if (p != null && !left.contains(id)) actionBar(p);
			}
			forWatchers(this::actionBar);
			for (Team t : Team.values()) {
				UUID carrier = ctf.carrier(t);
				ServerPlayer c = carrier == null ? null : player(carrier);
				if (c != null) {
					arena.level().sendParticles(ParticleTypes.FLAME, c.getX(), c.getY() + 2.3, c.getZ(), 3, 0.2, 0.1, 0.2, 0.01);
				}
			}
		}
	}

	private void actionBar(ServerPlayer p) {
		Msg.actionBar(p, "ctf.actionbar", ctf.captures(Team.RED), ctf.captures(Team.BLUE), (int) game().setting("capturesToWin", 3));
	}

	private static boolean near(ServerPlayer p, BlockPos pos, double r) {
		double dx = p.getX() - (pos.getX() + 0.5);
		double dz = p.getZ() - (pos.getZ() + 0.5);
		double dy = p.getY() - pos.getY();
		return dx * dx + dz * dz <= r * r && dy > -1.5 && dy < 2.5;
	}

	private void handle(ServerPlayer p, CtfGame.Event e) {
		switch (e.type()) {
			case PICKED_UP -> {
				BlockPos from = dropped.containsKey(e.flag()) ? dropped.remove(e.flag()) : home(e.flag());
				removeFlagBlock(from);
				savedHelmet.put(p.getUUID(), p.getItemBySlot(EquipmentSlot.HEAD).copy());
				p.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Ids.item("minecraft:" + color(e.flag()) + "_banner")));
				p.addEffect(new MobEffectInstance(MobEffects.GLOWING, MobEffectInstance.INFINITE_DURATION, 0, false, false, false));
				forEveryone(o -> {
					Msg.send(o, "ctf.picked", name(p.getUUID()), flagName(o, e.flag()));
					Msg.sound(o, SoundEvents.NOTE_BLOCK_PLING, 0.7f, 0.8f);
				});
			}
			case RETURNED -> {
				removeFlagBlock(dropped.remove(e.flag()));
				placeFlag(e.flag(), home(e.flag()));
				forEveryone(o -> Msg.send(o, "ctf.returned", name(p.getUUID()), flagName(o, e.flag())));
			}
			case CAPTURED, WON -> {
				restoreHelmet(p);
				placeFlag(e.flag(), home(e.flag()));
				forEveryone(o -> {
					Msg.send(o, "ctf.captured", name(p.getUUID()), flagName(o, e.flag()), ctf.captures(Team.RED), ctf.captures(Team.BLUE));
					Msg.sound(o, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 0.6f, 1.2f);
				});
				if (e.type() == CtfGame.EventType.WON) {
					finish(ctf.members(ctf.winner()));
				}
			}
			default -> { }
		}
	}

	@Override
	protected void onTimeUp() {
		Team lead = ctf.leading();
		finish(lead == null ? List.of() : ctf.members(lead));
	}

	@Override
	public Component status(ServerPlayer viewer) {
		return Lang.tr(viewer, "ctf.status", ctf.captures(Team.RED), ctf.captures(Team.BLUE));
	}
}
