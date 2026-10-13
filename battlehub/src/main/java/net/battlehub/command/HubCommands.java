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
								}))));

		d.register(Commands.literal("battlehub")
				.executes(c -> {
					new MainMenu(c.getSource().getPlayerOrException()).open();
					return 1;
				})
				.then(Commands.literal("setlobby").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.executes(c -> setLobby(c.getSource())))
				.then(Commands.literal("reload").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.executes(c -> reload(c.getSource())))
				.then(Commands.literal("matches").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.executes(c -> AdminCommands.matches(c.getSource())))
				.then(Commands.literal("endmatch").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.then(Commands.argument("id", IntegerArgumentType.integer(1))
								.executes(c -> AdminCommands.endMatch(c.getSource(), IntegerArgumentType.getInteger(c, "id")))))
				.then(Commands.literal("arena").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.then(Commands.literal("list").executes(c -> AdminCommands.arenaList(c.getSource())))));
		if (net.battlehub.debug.SelfTest.enabled()) {
			d.register(Commands.literal("battlehub").then(Commands.literal("selftest")
					.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
					.executes(c -> {
						net.battlehub.debug.SelfTest.start(c.getSource().getServer());
						return 1;
					})));
		}
	}

	private static java.util.List<String> duelModeIds() {
		java.util.List<String> out = new java.util.ArrayList<>();
		for (GameMode m : GameMode.duels()) {
			out.add(m.id());
		}
		return out;
	}

	/** Leave whatever you are doing and go back to the lobby. */
	public static int hub(ServerPlayer p) {
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
