package com.extremedimensions.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;

import com.extremedimensions.config.Preset;
import com.extremedimensions.config.SettingsService;
import com.extremedimensions.data.DataStore;
import com.extremedimensions.data.PlayerData;
import com.extremedimensions.network.Sync;
import com.extremedimensions.penalty.Penalty;
import com.extremedimensions.hollow.HollowService;
import com.extremedimensions.lurker.LurkerService;
import com.extremedimensions.seam.SeamArchitect;
import com.extremedimensions.seam.SeamReset;
import com.extremedimensions.penalty.CurseEffects;
import com.extremedimensions.penalty.PenaltyService;
import com.extremedimensions.seam.SeamService;
import com.extremedimensions.mending.MendingService;
import com.extremedimensions.world.WorldService;
import com.extremedimensions.server.WorldFx;

import com.extremedimensions.server.Perms;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Commands under {@code /tether}: {@code where} for everyone (the world you stand in and whether the mod acts there),
 * the co-op answers ({@code burden accept|refuse}, {@code share on|off}), {@code clan} (your clan; {@code accept|refuse}
 * a Blood Cord, {@code leave}) and {@code tribe} (where you stand with it);
 * for operators Overworld curses on or off, curses set or cleared, Lurkers, hoards, the Seam, bosses and difficulty,
 * the stats export written now ({@code export}) and a test message to the Discord webhook ({@code discord_test}); for
 * admins a reset of the Seam.
 */
public final class TetherCommand {
	private TetherCommand() {}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		// Each operator command needs its permission (3.29): granted by a permission mod such as LuckPerms, or without one
		// by the operator level it always needed
		java.util.function.Predicate<CommandSourceStack> players = Perms.require(Perms.Node.PLAYERS);
		java.util.function.Predicate<CommandSourceStack> settings = Perms.require(Perms.Node.SETTINGS);
		java.util.function.Predicate<CommandSourceStack> spawn = Perms.require(Perms.Node.SPAWN);
		java.util.function.Predicate<CommandSourceStack> export = Perms.require(Perms.Node.EXPORT);
		// Wiping a world is for admins (and the server console), not for command blocks
		java.util.function.Predicate<CommandSourceStack> admin = Perms.require(Perms.Node.SEAM_RESET);
		dispatcher.register(Commands.literal("tether")
				// Anyone may ask where they stand; everything else is for operators
				.then(Commands.literal("where").executes(c -> where(c.getSource())))
				// Co-op, for everyone: answering an offer to carry a curse, and opening one's anchors to allies
				.then(Commands.literal("burden")
						.then(Commands.literal("accept").executes(c -> burden(c.getSource(), true)))
						.then(Commands.literal("refuse").executes(c -> burden(c.getSource(), false))))
				.then(Commands.literal("clan").executes(c -> clan(c.getSource(), "info"))
						.then(Commands.literal("accept").executes(c -> clan(c.getSource(), "accept")))
						.then(Commands.literal("refuse").executes(c -> clan(c.getSource(), "refuse")))
						.then(Commands.literal("leave").executes(c -> clan(c.getSource(), "leave"))))
				.then(Commands.literal("share")
						.then(Commands.literal("on").executes(c -> share(c.getSource(), true)))
						.then(Commands.literal("off").executes(c -> share(c.getSource(), false))))
				// The tribe: anyone may ask where they stand with it; operators may set it
				.then(Commands.literal("tribe").executes(c -> tribe(c.getSource()))
						.then(Commands.literal("set").requires(players)
								.then(Commands.argument("standing", IntegerArgumentType.integer(-100, 100))
										.executes(c -> tribeSet(c.getSource(), IntegerArgumentType.getInteger(c, "standing"))))))
				.then(Commands.literal("overworld_penalties").requires(settings)
						.executes(c -> show(c.getSource()))
						.then(Commands.literal("on").executes(c -> set(c.getSource(), true)))
						.then(Commands.literal("off").executes(c -> set(c.getSource(), false))))
				// Any kind of Lurker, near the operator or near the players named after it (and set on them)
				.then(lurkerCommand(spawn))
				// Each kind of Lurker switched on or off for the whole world (3.30)
				.then(lurkersCommand(settings))
				.then(Commands.literal("hoard").requires(spawn).executes(c -> hoard(c.getSource())))
				.then(Commands.literal("boss").requires(spawn)
						.then(Commands.literal("matriarch").executes(c -> boss(c.getSource(), "matriarch")))
						.then(Commands.literal("ferryman").executes(c -> boss(c.getSource(), "ferryman")))
						.then(Commands.literal("weaver").executes(c -> boss(c.getSource(), "weaver")))
						.then(Commands.literal("pale_mason").executes(c -> boss(c.getSource(), "pale_mason"))))
				.then(Commands.literal("seam").requires(spawn).executes(c -> seam(c.getSource())))
				.then(Commands.literal("seam_reset").requires(admin)
						.executes(c -> seamReset(c.getSource()))
						.then(Commands.literal("confirm").executes(c -> seamResetConfirm(c.getSource())))
						.then(Commands.literal("cancel").executes(c -> seamResetCancel(c.getSource()))))
				.then(Commands.literal("break_seal").requires(spawn).executes(c -> {
					MendingService.breakSeal(c.getSource().getServer());
					return 1;
				}))
				.then(Commands.literal("seam_showcase").requires(spawn).executes(c -> {
					int n = SeamService.showcase(c.getSource().getServer());
					c.getSource().sendSuccess(() -> Component.literal("Designed " + n + " Seam structures; building " + SeamArchitect.pending() + " blocks"), false);
					return n;
				}))
				.then(Commands.literal("reach").requires(spawn)
						.then(Commands.literal("anchor").executes(c -> reachAnchor(c.getSource())))
						.then(Commands.literal("choice")
								.then(Commands.literal("home").executes(c -> reachChoice(c.getSource(), com.extremedimensions.reach.HomeOrLock.HOME)))
								.then(Commands.literal("lock").executes(c -> reachChoice(c.getSource(), com.extremedimensions.reach.HomeOrLock.LOCK)))
								.then(Commands.literal("undo").executes(c -> reachChoice(c.getSource(), "")))))
				.then(Commands.literal("event").requires(spawn)
						.then(Commands.argument("id", StringArgumentType.word())
								.suggests((c, b) -> SharedSuggestionProvider.suggest(com.extremedimensions.events.WorldEvents.IDS, b))
								.executes(c -> event(c.getSource(), StringArgumentType.getString(c, "id")))))
				.then(Commands.literal("storm").requires(spawn).executes(c -> {
					WorldService.begin(c.getSource().getPlayerOrException(), c.getSource().getServer().getTickCount());
					return 1;
				}))
				.then(preset().requires(settings))
				.then(Commands.literal("export").requires(export).executes(c -> export(c.getSource())))
				.then(Commands.literal("discord_test").requires(export).executes(c -> discordTest(c.getSource())))
				.then(Commands.literal("players").requires(players).executes(c -> players(c.getSource())))
				.then(Commands.literal("curse").requires(players)
						.then(Commands.literal("clear").executes(c -> clearCurses(c.getSource())))
						.then(Commands.argument("id", StringArgumentType.word())
								.suggests((c, b) -> SharedSuggestionProvider.suggest(java.util.Arrays.stream(Penalty.values()).map(p -> p.id), b))
								.then(Commands.argument("stacks", IntegerArgumentType.integer(0, 99))
										.executes(c -> setCurse(c.getSource(), StringArgumentType.getString(c, "id"), IntegerArgumentType.getInteger(c, "stacks")))))));
	}

	/**
	 * {@code /tether reach anchor}: an Unfinished Anchor flies together a little way in front of the player, wherever
	 * they are (for showing and testing the Pale Reach, 3.39).
	 */
	private static int reachAnchor(CommandSourceStack src) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer p = src.getPlayerOrException();
		net.minecraft.world.phys.Vec3 look = p.getLookAngle();
		double flat = Math.max(1e-3, Math.sqrt(look.x * look.x + look.z * look.z));
		net.minecraft.core.BlockPos origin = net.minecraft.core.BlockPos.containing(p.getX() + look.x / flat * 20, p.getY(), p.getZ() + look.z / flat * 20);
		com.extremedimensions.reach.PaleReach.build((net.minecraft.server.level.ServerLevel) p.level(), "cmd@" + origin.getX() + "," + origin.getZ(), origin,
				java.util.concurrent.ThreadLocalRandom.current().nextLong());
		src.sendSuccess(() -> Component.translatable("commands.extremedimensions.reach.anchor", origin.getX(), origin.getY(), origin.getZ()), true);
		return 1;
	}

	/**
	 * {@code /tether reach choice home|lock|undo}: decides the End's ending at once (3.44). Within 64 blocks of the fitting
	 * of an Unfinished Anchor, the anchor is finished or locked there as if the Keystone or the Lock were set in it;
	 * elsewhere only the choice is kept. {@code undo} forgets it (the anchors stay as they are).
	 */
	private static int reachChoice(CommandSourceStack src, String choice) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer p = src.getPlayerOrException();
		if (choice.isEmpty()) {
			com.extremedimensions.reach.HomeOrLock.set("");
			src.sendSuccess(() -> Component.translatable("commands.extremedimensions.reach.choice_undone"), true);
			return 1;
		}
		net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) p.level();
		net.minecraft.core.BlockPos fitting = null;
		double best = 64 * 64;
		for (net.minecraft.core.BlockPos at : net.minecraft.core.BlockPos.betweenClosed(p.blockPosition().offset(-64, -32, -64), p.blockPosition().offset(64, 32, 64))) {
			double d = at.distSqr(p.blockPosition());
			if (d < best && level.getBlockState(at).is(net.minecraft.world.level.block.Blocks.LODESTONE)
					&& com.extremedimensions.reach.PaleReach.isFitting(level, at)) {
				best = d;
				fitting = at.immutable();
			}
		}
		if (fitting == null) {
			com.extremedimensions.reach.HomeOrLock.set(choice);
		} else {
			com.extremedimensions.reach.HomeOrLock.set("");
			com.extremedimensions.reach.HomeOrLock.choose(level, fitting, p, com.extremedimensions.reach.HomeOrLock.HOME.equals(choice));
		}
		src.sendSuccess(() -> Component.translatable("commands.extremedimensions.reach.choice_set", choice), true);
		return 1;
	}

	/** {@code /tether event <id>}: starts a world event for the player at once (storm, echo, unstable, unanchored, frayed, squall). */
	private static int event(CommandSourceStack src, String id) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		net.minecraft.server.level.ServerPlayer p = src.getPlayerOrException();
		String why = com.extremedimensions.events.WorldEvents.force(p, id);
		if (why != null) {
			src.sendFailure(Component.translatable(why));
			return 0;
		}
		src.sendSuccess(() -> Component.translatable("commands.extremedimensions.event.started", id), true);
		return 1;
	}

	/** {@code /tether where}: the world the player stands in, and whether the mod acts there (anyone may ask). */
	/** {@code /tether burden accept|refuse}: the answer to an ally's offer to carry one of the player's curses. */
	private static int burden(CommandSourceStack src, boolean yes) {
		net.minecraft.server.level.ServerPlayer p = src.getPlayer();
		if (p == null) {
			return 0;
		}
		com.extremedimensions.coop.BurdenService.answer(p, yes);
		return 1;
	}

	/** {@code /tether clan [accept|refuse|leave]}: the player's clan, an answer to a Blood Cord, or cutting the cord. */
	private static int clan(CommandSourceStack src, String what) {
		net.minecraft.server.level.ServerPlayer p = src.getPlayer();
		if (p == null) {
			return 0;
		}
		switch (what) {
			case "accept" -> com.extremedimensions.coop.ClanService.answer(p, true);
			case "refuse" -> com.extremedimensions.coop.ClanService.answer(p, false);
			case "leave" -> com.extremedimensions.coop.ClanService.leave(p);
			default -> com.extremedimensions.coop.ClanService.info(p);
		}
		return 1;
	}

	/** {@code /tether share on|off}: the player's anchors opened to their allies, or theirs alone again. */
	private static int share(CommandSourceStack src, boolean on) {
		net.minecraft.server.level.ServerPlayer p = src.getPlayer();
		if (p == null) {
			return 0;
		}
		com.extremedimensions.coop.Allies.share(p, on);
		return 1;
	}

	private static int tribe(CommandSourceStack src) {
		ServerPlayer p = src.getPlayer();
		if (p == null) {
			return 0;
		}
		src.sendSuccess(() -> com.extremedimensions.tribe.Standing.describe(p), false);
		return 1;
	}

	private static int tribeSet(CommandSourceStack src, int standing) {
		ServerPlayer p = src.getPlayer();
		if (p == null) {
			return 0;
		}
		com.extremedimensions.data.DataStore.get(p.getUUID()).tribeRep = standing;
		com.extremedimensions.data.DataStore.markDirty();
		src.sendSuccess(() -> com.extremedimensions.tribe.Standing.describe(p), false);
		return 1;
	}

	private static int where(CommandSourceStack src) {
		var level = src.getLevel();
		String id = level.dimension().identifier().toString();
		boolean ours = com.extremedimensions.server.Dims.ours(level);
		String version = net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer(com.extremedimensions.ExtremeDimensions.MOD_ID)
				.map(m -> m.getMetadata().getVersion().getFriendlyString()).orElse("?");
		src.sendSuccess(() -> Component.translatable(ours ? "commands.extremedimensions.where.ours" : "commands.extremedimensions.where.asleep",
				id, version), false);
		return ours ? 1 : 0;
	}

	/** {@code /tether boss <name>}: one of the Elders comes for the player at once, whatever the world's rules. */
	private static int boss(CommandSourceStack src, String which) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		net.minecraft.server.level.ServerPlayer p = src.getPlayerOrException();
		boolean ok = switch (which) {
			case "matriarch" -> com.extremedimensions.hollow.HollowService.summonMatriarch(p);
			case "ferryman" -> com.extremedimensions.elder.FerrymanService.summon(p);
			case "pale_mason" -> com.extremedimensions.elder.PaleMasonService.summon(p);
			default -> com.extremedimensions.elder.WeaverService.summon(p, p.blockPosition().offset(6, 0, 0));
		};
		if (ok) {
			src.sendSuccess(() -> Component.translatable("commands.extremedimensions.boss", Component.translatable("entity.extremedimensions." + which)), true);
		} else {
			src.sendFailure(Component.translatable("commands.extremedimensions.boss_failed"));
		}
		return ok ? 1 : 0;
	}

	/** {@code /tether preset <name>}: a difficulty for the whole world at once (also from the server console). */
	private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> preset() {
		var node = Commands.literal("preset").executes(c -> {
			Preset now = Preset.matching(Preset.current());
			c.getSource().sendSuccess(() -> Component.translatable("commands.extremedimensions.preset.current",
					now == null ? Component.translatable("gui.extremedimensions.preset.custom") : Component.translatable(now.nameKey())), false);
			return 1;
		});
		for (Preset p : Preset.values()) {
			node.then(Commands.literal(p.id).executes(c -> {
				SettingsService.applyPreset(c.getSource().getServer(), p, c.getSource().getTextName());
				c.getSource().sendSuccess(() -> Component.translatable("gui.extremedimensions.preset.applied", Component.translatable(p.nameKey())), true);
				return 1;
			}));
		}
		return node;
	}

	/**
	 * {@code /tether export}: the stats export is written now (it is otherwise every {@code export_minutes}). The answer
	 * comes once the file is written; the texts fall back on English where they are not translated (the server console).
	 */
	private static int export(CommandSourceStack src) {
		var done = com.extremedimensions.compat.StatsExport.export(src.getServer(), false);
		if (done == null) {
			src.sendFailure(Component.translatableWithFallback("commands.extremedimensions.export.off",
					"There is no stats export: set export_dir in config/extremedimensions.json while the server is stopped, then start it."));
			return 0;
		}
		var server = src.getServer();
		done.whenComplete((file, error) -> server.execute(() -> {
			if (error == null) {
				src.sendSuccess(() -> Component.translatableWithFallback("commands.extremedimensions.export.written", "The stats export is written to %s.",
						file.toString()), false);
			} else {
				src.sendFailure(Component.translatableWithFallback("commands.extremedimensions.export.failed",
						"The stats export could not be written. See the server log."));
			}
		}));
		return 1;
	}

	/** {@code /tether discord_test}: a test message to the Discord webhook, and what Discord answered. */
	private static int discordTest(CommandSourceStack src) {
		if (!com.extremedimensions.compat.DiscordWebhook.on()) {
			src.sendFailure(Component.translatableWithFallback("commands.extremedimensions.discord.off",
					"There is no Discord webhook: set discord_webhook in config/extremedimensions.json while the server is stopped, then start it."));
			return 0;
		}
		var sent = com.extremedimensions.compat.DiscordWebhook.post(com.extremedimensions.compat.DiscordWebhook.testText());
		if (sent == null) {
			src.sendFailure(Component.translatableWithFallback("commands.extremedimensions.discord.not_sent",
					"Nothing was sent: discord_webhook is not a web address, or too many messages went out this minute. See the server log."));
			return 0;
		}
		var server = src.getServer();
		sent.thenAccept(code -> server.execute(() -> {
			if (code / 100 == 2) {
				src.sendSuccess(() -> Component.translatableWithFallback("commands.extremedimensions.discord.sent", "Discord took the test message."), false);
			} else if (code < 0) {
				src.sendFailure(Component.translatableWithFallback("commands.extremedimensions.discord.unreachable",
						"Discord could not be reached. See the server log."));
			} else {
				src.sendFailure(Component.translatableWithFallback("commands.extremedimensions.discord.refused",
						"Discord answered %s: is the webhook deleted, or its URL mistyped?", String.valueOf(code)));
			}
		}));
		return 1;
	}

	/** Sets a curse's stacks on the executing player (0 takes it off, never past its maximum). */
	private static int setCurse(CommandSourceStack src, String id, int stacks) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer p = src.getPlayerOrException();
		Penalty pen = Penalty.byId(id);
		if (pen == null) {
			src.sendFailure(Component.translatable("commands.extremedimensions.curse.unknown", id));
			return 0;
		}
		PlayerData d = DataStore.get(p.getUUID());
		d.curses.remove(pen.id);
		d.atonement.remove(pen.id);
		int set = com.extremedimensions.penalty.CurseLog.add(d, pen, stacks, com.extremedimensions.penalty.CurseLog.Source.COMMAND);
		CurseEffects.applyEffects(p, d);
		Sync.state(p, d);
		DataStore.markDirty();
		src.sendSuccess(() -> Component.translatable("commands.extremedimensions.curse.set", Component.translatable(pen.nameKey()), set, pen.max), false);
		return 1;
	}

	/** {@code /tether players}: opens the operators' Players screen (every player, their curses laid on or lifted off). */
	private static int players(CommandSourceStack src) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer p = src.getPlayerOrException();
		com.extremedimensions.admin.AdminService.send(p, "", true);
		return 1;
	}

	/** Takes every curse off the executing player. */
	private static int clearCurses(CommandSourceStack src) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer p = src.getPlayerOrException();
		PlayerData d = DataStore.get(p.getUUID());
		d.curses.clear();
		d.atonement.clear();
		CurseEffects.applyEffects(p, d);
		Sync.state(p, d);
		DataStore.markDirty();
		src.sendSuccess(() -> Component.translatable("commands.extremedimensions.curse.cleared"), false);
		return 1;
	}

	/** Every kind of Lurker the command calls: the Lurker's own moods first, then its kin. */
	/** One kind of Lurker that can be switched off: its word in the command, its setting, its entity and that setting now. */
	public record LurkerSwitch(String kind, String key, String entity, java.util.function.IntSupplier value) {
		public boolean on() {
			return value.getAsInt() > 0;
		}
	}

	/** Every kind of Lurker with a switch of its own (the settings {@code *_enabled}), in the order the bestiary has them. */
	public static final java.util.List<LurkerSwitch> LURKER_SWITCHES = java.util.List.of(
			new LurkerSwitch("lurker", "lurker_enabled", "lurker", () -> com.extremedimensions.config.Config.LURKER_ENABLED),
			new LurkerSwitch("cave", "cave_lurker_enabled", "cave_lurker", () -> com.extremedimensions.config.Config.CAVE_LURKER_ENABLED),
			new LurkerSwitch("sculk", "sculk_lurker_enabled", "sculk_lurker", () -> com.extremedimensions.config.Config.SCULK_LURKER_ENABLED),
			new LurkerSwitch("magpie", "magpie_enabled", "magpie", () -> com.extremedimensions.config.Config.MAGPIE_ENABLED),
			new LurkerSwitch("wick", "wick_enabled", "wick", () -> com.extremedimensions.config.Config.WICK_ENABLED),
			new LurkerSwitch("tidewalker", "tidewalker_enabled", "tidewalker", () -> com.extremedimensions.config.Config.TIDEWALKER_ENABLED),
			new LurkerSwitch("gatebreaker", "gatebreaker_enabled", "gatebreaker", () -> com.extremedimensions.config.Config.GATEBREAKER_ENABLED),
			new LurkerSwitch("lockwarden", "lockwarden_enabled", "lockwarden", () -> com.extremedimensions.config.Config.LOCKWARDEN_ENABLED),
			new LurkerSwitch("threadbiter", "threadbiter_enabled", "threadbiter", () -> com.extremedimensions.config.Config.THREADBITER_ENABLED),
			new LurkerSwitch("edgecrawler", "edgecrawler_enabled", "edgecrawler", () -> com.extremedimensions.config.Config.EDGECRAWLER_ENABLED),
			new LurkerSwitch("cragback", "cragback_enabled", "cragback", () -> com.extremedimensions.config.Config.CRAGBACK_ENABLED),
			new LurkerSwitch("pale_hauler", "pale_hauler_enabled", "pale_hauler", () -> com.extremedimensions.config.Config.PALE_HAULER_ENABLED));

	/**
	 * {@code /tether lurkers}: every kind of Lurker and whether it comes, each with a button to switch it;
	 * {@code /tether lurkers <kind> on|off} and {@code /tether lurkers all on|off} switch them (3.30). The same settings
	 * as in the settings screen, saved and sent to everyone; no new ones come, and any already out stay until they leave or are slain.
	 */
	private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> lurkersCommand(
			java.util.function.Predicate<CommandSourceStack> settings) {
		var root = Commands.literal("lurkers").requires(settings).executes(c -> lurkers(c.getSource()));
		for (LurkerSwitch sw : LURKER_SWITCHES) {
			root.then(Commands.literal(sw.kind())
					.executes(c -> lurkers(c.getSource()))
					.then(Commands.literal("on").executes(c -> lurkerSwitch(c.getSource(), java.util.List.of(sw), true)))
					.then(Commands.literal("off").executes(c -> lurkerSwitch(c.getSource(), java.util.List.of(sw), false))));
		}
		root.then(Commands.literal("all")
				.then(Commands.literal("on").executes(c -> lurkerSwitch(c.getSource(), LURKER_SWITCHES, true)))
				.then(Commands.literal("off").executes(c -> lurkerSwitch(c.getSource(), LURKER_SWITCHES, false))));
		return root;
	}

	/** The list: each kind, on or off, and a button that switches it the other way. */
	private static int lurkers(CommandSourceStack src) {
		src.sendSuccess(() -> Component.translatable("commands.extremedimensions.lurkers.title").withStyle(net.minecraft.ChatFormatting.GOLD), false);
		int on = 0;
		for (LurkerSwitch sw : LURKER_SWITCHES) {
			boolean is = sw.on();
			on += is ? 1 : 0;
			String command = "/tether lurkers " + sw.kind() + (is ? " off" : " on");
			Component line = Component.literal(" ").append(Component.translatable("entity.extremedimensions." + sw.entity()))
					.append(Component.literal(" (" + sw.kind() + "): "))
					.append(Component.translatable(is ? "commands.extremedimensions.lurkers.on" : "commands.extremedimensions.lurkers.off")
							.withStyle(is ? net.minecraft.ChatFormatting.GREEN : net.minecraft.ChatFormatting.RED))
					.append(Component.literal(" "))
					.append(Component.translatable(is ? "commands.extremedimensions.lurkers.switch_off" : "commands.extremedimensions.lurkers.switch_on")
							.withStyle(net.minecraft.network.chat.Style.EMPTY.withColor(net.minecraft.ChatFormatting.GRAY).withUnderlined(true)
									.withClickEvent(new net.minecraft.network.chat.ClickEvent.RunCommand(command))
									.withHoverEvent(new net.minecraft.network.chat.HoverEvent.ShowText(Component.literal(command)))));
			src.sendSuccess(() -> line, false);
		}
		return on;
	}

	/** Switches these kinds on or off for the whole world; says which changed, or that nothing did. */
	private static int lurkerSwitch(CommandSourceStack src, java.util.List<LurkerSwitch> which, boolean on) {
		java.util.Map<String, String> changes = new java.util.LinkedHashMap<>();
		java.util.List<LurkerSwitch> changed = new java.util.ArrayList<>();
		for (LurkerSwitch sw : which) {
			if (sw.on() != on) {
				changes.put(sw.key(), on ? "1" : "0");
				changed.add(sw);
			}
		}
		if (changed.isEmpty()) {
			src.sendSuccess(() -> Component.translatable(on ? "commands.extremedimensions.lurkers.already_on" : "commands.extremedimensions.lurkers.already_off"), false);
			return 0;
		}
		SettingsService.set(src.getServer(), changes, src.getTextName());
		for (LurkerSwitch sw : changed) {
			Component name = Component.translatable("entity.extremedimensions." + sw.entity());
			// Told to every operator, as a setting changed by command always is
			src.sendSuccess(() -> Component.translatable(on ? "commands.extremedimensions.lurkers.switched_on" : "commands.extremedimensions.lurkers.switched_off", name), true);
		}
		return changed.size();
	}

	static final java.util.List<String> LURKER_KINDS = java.util.List.of("watch", "stalk", "hunt", "swarm", "drain", "cave", "sculk",
			"magpie", "wick", "tidewalker", "gatebreaker", "lockwarden", "threadbiter", "edgecrawler", "cragback", "pale_hauler");

	/**
	 * {@code /tether lurker <kind> [players]}: a Lurker of that kind near the operator, or (3.26.1) near each player named
	 * or selected after it, set on that player: the one it watches, stalks, hunts or robs.
	 */
	private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> lurkerCommand(
			java.util.function.Predicate<CommandSourceStack> op) {
		var root = Commands.literal("lurker").requires(op);
		for (String kind : LURKER_KINDS) {
			root.then(Commands.literal(kind).executes(c -> lurker(c.getSource(), kind))
					.then(Commands.argument("target", net.minecraft.commands.arguments.EntityArgument.players())
							.executes(c -> lurkerFor(c.getSource(), kind, net.minecraft.commands.arguments.EntityArgument.getPlayers(c, "target")))));
		}
		return root;
	}

	/** Summons a Lurker of the given kind near each of the players, set on them. */
	private static int lurkerFor(CommandSourceStack src, String mode, java.util.Collection<ServerPlayer> targets) {
		int done = 0;
		for (ServerPlayer p : targets) {
			boolean ok = summonLurker(p, mode);
			if (ok) {
				done++;
				src.sendSuccess(() -> Component.translatable("commands.extremedimensions.lurker.summoned_for", p.getDisplayName()), true);
			} else {
				src.sendFailure(Component.translatable("commands.extremedimensions.lurker.failed_for", p.getDisplayName(),
						Component.translatable(failedKey(mode))));
			}
		}
		return done;
	}

	/** Summons a Lurker in the given mode near the executing player (for testing and showcases). */
	private static int lurker(CommandSourceStack src, String mode) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer p = src.getPlayerOrException();
		boolean ok = summonLurker(p, mode);
		src.sendSuccess(() -> Component.translatable(ok ? "commands.extremedimensions.lurker.summoned" : failedKey(mode)), false);
		return ok ? 1 : 0;
	}

	/** A Lurker of the given kind near this player, set on them. True when one came. */
	static boolean summonLurker(ServerPlayer p, String mode) {
		return switch (mode) {
			case "cave" -> com.extremedimensions.lurker.CaveLurkerService.summon(p);
			case "sculk" -> com.extremedimensions.lurker.SculkLurkerService.summon(p);
			case "magpie" -> com.extremedimensions.lurker.MagpieService.summon(p);
			case "wick" -> com.extremedimensions.lurker.WickService.summon(p);
			case "tidewalker" -> com.extremedimensions.lurker.TidewalkerService.summon(p);
			case "gatebreaker" -> com.extremedimensions.lurker.GatebreakerService.summon(p);
			case "lockwarden" -> com.extremedimensions.lurker.LockwardenService.summon(p);
			case "threadbiter" -> com.extremedimensions.lurker.ThreadbiterService.summon(p);
			case "edgecrawler" -> com.extremedimensions.lurker.EdgecrawlerService.summon(p);
			case "cragback" -> com.extremedimensions.lurker.CragbackService.summon(p);
			case "pale_hauler" -> com.extremedimensions.lurker.PaleHaulerService.summon(p);
			default -> LurkerService.summon(p, mode);
		};
	}

	/** What to say when a Lurker of this kind found no place. */
	private static String failedKey(String mode) {
		return switch (mode) {
			case "cave" -> "commands.extremedimensions.lurker.failed_cave";
			case "sculk" -> "commands.extremedimensions.lurker.failed_sculk";
			case "magpie" -> "commands.extremedimensions.lurker.failed_magpie";
			case "wick" -> "commands.extremedimensions.lurker.failed_wick";
			case "tidewalker" -> "commands.extremedimensions.lurker.failed_tidewalker";
			case "gatebreaker" -> "commands.extremedimensions.lurker.failed_gatebreaker";
			case "lockwarden" -> "commands.extremedimensions.lurker.failed_lockwarden";
			case "threadbiter" -> "commands.extremedimensions.lurker.failed_threadbiter";
			case "edgecrawler" -> "commands.extremedimensions.lurker.failed_edgecrawler";
			case "cragback" -> "commands.extremedimensions.lurker.failed_cragback";
			case "pale_hauler" -> "commands.extremedimensions.lurker.failed_pale_hauler";
			default -> "commands.extremedimensions.lurker.failed";
		};
	}

	/** Builds an old hoard in front of the executing player, with its Hollowlings and its Keeper. */
	private static int hoard(CommandSourceStack src) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer p = src.getPlayerOrException();
		boolean ok = HollowService.summonHoard(p);
		src.sendSuccess(() -> Component.translatable(ok ? "commands.extremedimensions.hoard.summoned" : "commands.extremedimensions.hoard.failed"), false);
		return ok ? 1 : 0;
	}

	/** Sends the executing player into the Seam, as if through a rift at their feet. */
	private static int seam(CommandSourceStack src) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer p = src.getPlayerOrException();
		boolean ok = SeamService.summon(p);
		src.sendSuccess(() -> Component.translatable(ok ? "commands.extremedimensions.seam.entered" : "commands.extremedimensions.seam.failed"), false);
		return ok ? 1 : 0;
	}

	/**
	 * {@code /tether seam_reset}: what a reset of the Seam does, or that one is waiting. The texts fall back on English
	 * where they are not translated (the server console).
	 */
	private static int seamReset(CommandSourceStack src) {
		java.nio.file.Path root = SeamReset.root(src.getServer());
		if (SeamReset.waiting(root)) {
			src.sendSuccess(() -> Component.translatableWithFallback("commands.extremedimensions.seam_reset.waiting",
					"A reset of the Seam is waiting (asked for by %s). It happens the next time the world loads. To call it off: /tether seam_reset cancel",
					SeamReset.askedBy(root)), false);
			return 1;
		}
		src.sendSuccess(() -> Component.translatableWithFallback("commands.extremedimensions.seam_reset.ask",
				"A reset wipes the Seam: every island and building in it, and everything left there. It is then built anew as players explore. "
						+ "An open world cannot be wiped, so it happens the next time the world loads: restart the server, or leave the world and open it again. "
						+ "Anyone still in the Seam then is brought home safely. To go ahead: /tether seam_reset confirm"), false);
		return 0;
	}

	/** {@code /tether seam_reset confirm}: the Seam is wiped the next time the world loads. */
	private static int seamResetConfirm(CommandSourceStack src) {
		if (!SeamReset.ask(SeamReset.root(src.getServer()), src.getTextName())) {
			src.sendFailure(Component.translatableWithFallback("commands.extremedimensions.seam_reset.failed",
					"The reset could not be asked for: the world's folder cannot be written to. See the server log."));
			return 0;
		}
		src.sendSuccess(() -> Component.translatableWithFallback("commands.extremedimensions.seam_reset.confirmed",
				"The Seam will be made anew the next time the world loads (restart the server, or leave the world and open it again). "
						+ "Until then it stays as it is. To call it off: /tether seam_reset cancel"), true);
		return 1;
	}

	/** {@code /tether seam_reset cancel}: a reset that was waiting is called off. */
	private static int seamResetCancel(CommandSourceStack src) {
		boolean was = SeamReset.callOff(SeamReset.root(src.getServer()));
		src.sendSuccess(() -> was
				? Component.translatableWithFallback("commands.extremedimensions.seam_reset.cancelled", "The reset of the Seam is called off.")
				: Component.translatableWithFallback("commands.extremedimensions.seam_reset.none", "No reset of the Seam is waiting."), was);
		return was ? 1 : 0;
	}

	private static int show(CommandSourceStack src) {
		boolean on = DataStore.overworldPenalties();
		src.sendSuccess(() -> Component.translatable(on
				? "commands.extremedimensions.overworld_penalties.status_on"
				: "commands.extremedimensions.overworld_penalties.status_off"), false);
		return on ? 1 : 0;
	}

	private static int set(CommandSourceStack src, boolean on) {
		DataStore.setOverworldPenalties(on);
		src.sendSuccess(() -> Component.translatable(on
				? "commands.extremedimensions.overworld_penalties.enabled"
				: "commands.extremedimensions.overworld_penalties.disabled"), true);
		if (WorldFx.server() != null) {
			for (ServerPlayer p : WorldFx.server().getPlayerList().getPlayers()) {
				CurseEffects.applyEffects(p, DataStore.get(p.getUUID()));
			}
		}
		return 1;
	}
}
