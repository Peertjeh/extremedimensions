package net.battlehub.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;

import net.battlehub.BattleHub;
import net.battlehub.config.ConfigManager;
import net.battlehub.config.HubConfig;
import net.battlehub.gui.MainMenu;
import net.battlehub.lang.Lang;
import net.battlehub.lobby.HubItems;
import net.battlehub.lobby.Lobby;
import net.battlehub.match.Match;
import net.battlehub.match.Matches;
import net.battlehub.progression.Ranks;
import net.battlehub.queue.Queues;
import net.battlehub.session.Sessions;
import net.battlehub.util.Msg;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.battlehub.core.GameMode;
import net.battlehub.gui.DuelModeMenu;
import net.battlehub.queue.Challenges;
import net.minecraft.server.level.ServerPlayer;

/** Player commands (/hub, /battlehub menu) and admin commands (/battlehub setlobby, reload, ...). */
public final class HubCommands {
	private HubCommands() {}

	public static void register(CommandDispatcher<CommandSourceStack> d) {
		d.register(Commands.literal("hub").executes(c -> hub(c.getSource().getPlayerOrException())));
		d.register(Commands.literal("lobby").executes(c -> hub(c.getSource().getPlayerOrException())));
		d.register(Commands.literal("survival").executes(c -> net.battlehub.hub.Travel.leave(c.getSource().getPlayerOrException()) ? 1 : 0));

		d.register(Commands.literal("spectate")
				.executes(c -> {
					new net.battlehub.gui.SpectateMenu(c.getSource().getPlayerOrException()).open();
					return 1;
				})
				.then(Commands.literal("leave").executes(c -> hub(c.getSource().getPlayerOrException())))
				.then(Commands.argument("player", EntityArgument.player()).executes(c -> {
					ServerPlayer p = c.getSource().getPlayerOrException();
					ServerPlayer target = EntityArgument.getPlayer(c, "player");
					Match m = Matches.of(target.getUUID());
					if (m == null || !m.participants().contains(target.getUUID())) {
						Msg.send(p, "spectate.not_playing", target.getGameProfile().name());
						return 0;
					}
					return net.battlehub.spectate.Spectators.join(p, m) ? 1 : 0;
				})));

		d.register(Commands.literal("cosmetics").executes(c -> {
			new net.battlehub.gui.CosmeticsMenu(c.getSource().getPlayerOrException()).open();
			return 1;
		}));
		d.register(Commands.literal("stats")
				.executes(c -> {
					ServerPlayer p = c.getSource().getPlayerOrException();
					new net.battlehub.gui.StatsMenu(p, p.getUUID()).open();
					return 1;
				})
				.then(Commands.argument("player", StringArgumentType.word())
						.suggests((c, b) -> SharedSuggestionProvider.suggest(net.battlehub.data.PlayerStore.names(), b))
						.executes(c -> {
							ServerPlayer p = c.getSource().getPlayerOrException();
							String name = StringArgumentType.getString(c, "player");
							net.battlehub.core.PlayerProfile prof = net.battlehub.data.PlayerStore.find(name);
							if (prof == null || prof.uuid.isEmpty()) {
								Msg.send(p, "stats.unknown", name);
								return 0;
							}
							new net.battlehub.gui.StatsMenu(p, java.util.UUID.fromString(prof.uuid)).open();
							return 1;
						})));

		d.register(Commands.literal("duel")
				.then(Commands.literal("accept").then(Commands.argument("player", EntityArgument.player())
						.executes(c -> Challenges.accept(c.getSource().getPlayerOrException(), EntityArgument.getPlayer(c, "player")) ? 1 : 0)))
				.then(Commands.literal("deny").then(Commands.argument("player", EntityArgument.player())
						.executes(c -> Challenges.deny(c.getSource().getPlayerOrException(), EntityArgument.getPlayer(c, "player")) ? 1 : 0)))
				.then(Commands.argument("target", EntityArgument.player())
						.executes(c -> {
							ServerPlayer p = c.getSource().getPlayerOrException();
							ServerPlayer target = EntityArgument.getPlayer(c, "target");
							if (target == p) {
								Msg.send(p, "challenge.self");
								return 0;
							}
							new DuelModeMenu(p, target).open();
							return 1;
						})
						.then(Commands.argument("mode", StringArgumentType.word())
								.suggests((c, b) -> SharedSuggestionProvider.suggest(duelModeIds(), b))
								.executes(c -> {
									ServerPlayer p = c.getSource().getPlayerOrException();
									GameMode mode = GameMode.byId(StringArgumentType.getString(c, "mode"));
									if (mode == null || !mode.isDuel()) {
										Msg.send(p, "challenge.unknown_mode");
										return 0;
									}
									Challenges.send(p, EntityArgument.getPlayer(c, "target"), mode);
									return 1;
								})
								.then(Commands.argument("format", StringArgumentType.word())
										.suggests((c, b) -> SharedSuggestionProvider.suggest(java.util.List.of("bo1", "bo3"), b))
										.executes(c -> {
											ServerPlayer p = c.getSource().getPlayerOrException();
											GameMode mode = GameMode.byId(StringArgumentType.getString(c, "mode"));
											if (mode == null || !mode.isDuel()) {
												Msg.send(p, "challenge.unknown_mode");
												return 0;
											}
											Challenges.send(p, EntityArgument.getPlayer(c, "target"), mode,
													net.battlehub.core.DuelFormat.byId(StringArgumentType.getString(c, "format")));
											return 1;
										})))));

		d.register(Commands.literal("battlehub")
				.executes(c -> {
					new MainMenu(c.getSource().getPlayerOrException()).open();
					return 1;
				})
				.then(Commands.literal("join").then(Commands.argument("mode", StringArgumentType.word())
						.suggests((c, b) -> SharedSuggestionProvider.suggest(allModeIds(), b))
						.executes(c -> joinMode(c.getSource().getPlayerOrException(), StringArgumentType.getString(c, "mode")))))
				.then(Commands.literal("leave").executes(c -> hub(c.getSource().getPlayerOrException())))
				.then(Commands.literal("exit").executes(c -> net.battlehub.hub.Travel.leave(c.getSource().getPlayerOrException()) ? 1 : 0))
				.then(Commands.literal("again").executes(c -> net.battlehub.queue.PlayAgain.accept(c.getSource().getPlayerOrException()) ? 1 : 0))
				.then(Commands.literal("setlobby").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.executes(c -> setLobby(c.getSource())))
				.then(Commands.literal("lobby").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.then(Commands.literal("rebuild").executes(c -> {
							net.battlehub.hub.HubWorld.rebuild(c.getSource().getServer());
							ServerPlayer p = c.getSource().getPlayer();
							c.getSource().sendSuccess(() -> Lang.tr(p, "admin.lobby_rebuilt"), true);
							return 1;
						})))
				.then(Commands.literal("reload").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.executes(c -> reload(c.getSource())))
				.then(Commands.literal("matches").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.executes(c -> AdminCommands.matches(c.getSource())))
				.then(Commands.literal("endmatch").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.then(Commands.argument("id", IntegerArgumentType.integer(1))
								.executes(c -> AdminCommands.endMatch(c.getSource(), IntegerArgumentType.getInteger(c, "id")))))
				.then(Commands.literal("arena").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.then(Commands.literal("list").executes(c -> AdminCommands.arenaList(c.getSource()))))
				.then(coinsCommand()));
		if (net.battlehub.debug.SelfTest.enabled()) {
			d.register(Commands.literal("battlehub").then(Commands.literal("selftest")
					.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
					.executes(c -> {
						try {
							net.battlehub.debug.SelfTest.start(c.getSource().getServer());
						} catch (RuntimeException e) {
							net.battlehub.BattleHub.LOGGER.error("SELFTEST could not start", e);
							throw e;
						}
						return 1;
					})));
		}
	}

	/** /battlehub coins <player> | give|take|set <player> <amount> (admins). */
	private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> coinsCommand() {
		var root = Commands.literal("coins").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(playerArg().executes(c -> AdminCommands.coinsShow(c.getSource(), StringArgumentType.getString(c, "player"))));
		for (AdminCommands.CoinChange change : AdminCommands.CoinChange.values()) {
			root.then(Commands.literal(change.name().toLowerCase(java.util.Locale.ROOT))
					.then(playerArg().then(Commands.argument("amount", IntegerArgumentType.integer(change == AdminCommands.CoinChange.SET ? 0 : 1))
							.executes(c -> AdminCommands.coins(c.getSource(), StringArgumentType.getString(c, "player"), change,
									IntegerArgumentType.getInteger(c, "amount"))))));
		}
		return root;
	}

	private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> playerArg() {
		return Commands.argument("player", StringArgumentType.word())
				.suggests((c, b) -> SharedSuggestionProvider.suggest(net.battlehub.data.PlayerStore.names(), b));
	}

	private static java.util.List<String> allModeIds() {
		java.util.List<String> out = new java.util.ArrayList<>();
		for (GameMode m : GameMode.values()) {
			out.add(m.id());
		}
		return out;
	}

	/** "/battlehub join <mode>": party lobby or casual duel queue (used by clickable invites). */
	private static int joinMode(ServerPlayer p, String id) {
		GameMode mode = GameMode.byId(id);
		if (mode == null) {
			Msg.send(p, "challenge.unknown_mode");
			return 0;
		}
		boolean ok = mode.isParty() ? net.battlehub.queue.Parties.join(p, mode)
				: mode.isTeamFight() ? net.battlehub.queue.TeamFights.join(p, mode, GameMode.SWORD, net.battlehub.core.SideQueue.Side.ANY)
				: Queues.join(p, mode, false);
		return ok ? 1 : 0;
	}

	private static java.util.List<String> duelModeIds() {
		java.util.List<String> out = new java.util.ArrayList<>();
		for (GameMode m : GameMode.duels()) {
			out.add(m.id());
		}
		return out;
	}

	/** From the normal world: into BattleHub. In BattleHub: leave whatever you are doing and go back to the lobby. */
	public static int hub(ServerPlayer p) {
		if (!net.battlehub.hub.Travel.inBattleHub(p)) {
			return net.battlehub.hub.Travel.enter(p) ? 1 : 0;
		}
		if (Sessions.state(p.getUUID()) == Sessions.State.SPECTATING) {
			net.battlehub.spectate.Spectators.leave(p, true);
			return 1;
		}
		Match m = Matches.of(p.getUUID());
		if (m != null) {
			m.forfeit(p);
			Msg.send(p, "hub.left_match");
			return 1;
		}
		Queues.leaveAll(p);
		Sessions.toLobby(p.getUUID());
		Lobby.teleport(p);
		HubItems.ensure(p, false);
		Msg.send(p, "hub.teleported");
		return 1;
	}

	private static int setLobby(CommandSourceStack src) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer p = src.getPlayerOrException();
		if (!net.battlehub.hub.HubWorld.is(p.level())) {
			src.sendFailure(Lang.tr(p, "admin.lobby_not_hub"));
			return 0;
		}
		HubConfig.Lobby l = ConfigManager.config().lobby;
		l.set = true;
		l.dimension = p.level().dimension().identifier().toString();
		l.x = Math.floor(p.getX()) + 0.5;
		l.y = p.getY();
		l.z = Math.floor(p.getZ()) + 0.5;
		l.yaw = Math.round(p.getYRot() / 45f) * 45f;
		l.pitch = 0;
		ConfigManager.saveMain();
		src.sendSuccess(() -> Lang.tr(p, "admin.lobby_set", String.format(java.util.Locale.ROOT, "%.1f %.1f %.1f", l.x, l.y, l.z), l.dimension), true);
		return 1;
	}

	private static int reload(CommandSourceStack src) {
		boolean ok = ConfigManager.load();
		Lang.load();
		Ranks.reload();
		ServerPlayer p = src.getPlayer();
		src.sendSuccess(() -> Lang.tr(p, ok ? "admin.reloaded" : "admin.reload_failed"), true);
		BattleHub.LOGGER.info("Configuration reloaded ({})", ok ? "ok" : "with errors");
		return ok ? 1 : 0;
	}
}
