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

		d.register(Commands.literal("report").then(Commands.argument("player", StringArgumentType.word())
				.suggests((c, b) -> SharedSuggestionProvider.suggest(c.getSource().getOnlinePlayerNames(), b))
				.then(Commands.argument("reason", StringArgumentType.greedyString())
						.executes(c -> net.battlehub.fair.Reports.report(c.getSource().getPlayerOrException(),
								StringArgumentType.getString(c, "player"), StringArgumentType.getString(c, "reason")) ? 1 : 0))));
		registerSocial(d);
		d.register(Commands.literal("quests").executes(c -> {
			new net.battlehub.gui.QuestsMenu(c.getSource().getPlayerOrException()).open();
			return 1;
		}));
		d.register(Commands.literal("pass").executes(c -> {
			new net.battlehub.gui.PassMenu(c.getSource().getPlayerOrException(), 0).open();
			return 1;
		}));
		d.register(Commands.literal("achievements").executes(c -> {
			new net.battlehub.gui.AchievementsMenu(c.getSource().getPlayerOrException(), false).open();
			return 1;
		}));
		// Bots filling your queues: /bots toggles, /bots on|off sets it
		d.register(Commands.literal("bots")
				.executes(c -> {
					ServerPlayer p = c.getSource().getPlayerOrException();
					net.battlehub.bot.Bots.choose(p, net.battlehub.data.PlayerStore.get(p).noBots);
					return 1;
				})
				.then(Commands.literal("on").executes(c -> {
					net.battlehub.bot.Bots.choose(c.getSource().getPlayerOrException(), true);
					return 1;
				}))
				.then(Commands.literal("off").executes(c -> {
					net.battlehub.bot.Bots.choose(c.getSource().getPlayerOrException(), false);
					return 1;
				}))
				// how many bots you want in party lobbies: the server's standard, the lobby filled, or a number
				.then(Commands.literal("standard").executes(c -> {
					net.battlehub.bot.Bots.wish(c.getSource().getPlayerOrException(), net.battlehub.core.BotFill.Wish.STANDARD);
					return 1;
				}))
				.then(Commands.literal("fill").executes(c -> {
					net.battlehub.bot.Bots.wish(c.getSource().getPlayerOrException(), net.battlehub.core.BotFill.Wish.FILL);
					return 1;
				}))
				.then(Commands.argument("count", IntegerArgumentType.integer(1, net.battlehub.core.BotFill.MAX_COUNT))
						.executes(c -> {
							net.battlehub.bot.Bots.wish(c.getSource().getPlayerOrException(),
									net.battlehub.core.BotFill.Wish.count(IntegerArgumentType.getInteger(c, "count")));
							return 1;
						}))
				// what bots learned about you, and wiping it
				.then(Commands.literal("memory").executes(c -> {
					ServerPlayer p = c.getSource().getPlayerOrException();
					for (net.minecraft.network.chat.Component line : net.battlehub.bot.Bots.memory(p)) p.sendSystemMessage(line);
					return 1;
				}))
				.then(Commands.literal("forget").executes(c -> {
					ServerPlayer p = c.getSource().getPlayerOrException();
					net.battlehub.bot.AiMemory.forget(p.level().getServer(), p.getUUID());
					Msg.send(p, "bots.forgot");
					return 1;
				})));
		d.register(Commands.literal("kits").executes(c -> {
			new net.battlehub.gui.KitListMenu(c.getSource().getPlayerOrException()).open();
			return 1;
		}));

		d.register(Commands.literal("practice")
				.executes(c -> {
					new net.battlehub.gui.PracticeMenu(c.getSource().getPlayerOrException()).open();
					return 1;
				})
				.then(Commands.argument("mode", StringArgumentType.word())
						.suggests((c, b) -> SharedSuggestionProvider.suggest(practiceModes(), b))
						.executes(c -> practice(c.getSource().getPlayerOrException(), StringArgumentType.getString(c, "mode"), null, null))
						.then(Commands.argument("level", StringArgumentType.word())
								.suggests((c, b) -> SharedSuggestionProvider.suggest(java.util.stream.Stream.concat(java.util.Arrays.stream(net.battlehub.core.ai.Difficulty.values())
										.map(net.battlehub.core.ai.Difficulty::id), java.util.stream.Stream.of("adaptive")).toList(), b))
								.executes(c -> practice(c.getSource().getPlayerOrException(), StringArgumentType.getString(c, "mode"),
										StringArgumentType.getString(c, "level"), null))
								// 1v2 / 1v3: the kit (sword, axe or mace)
								.then(Commands.argument("kit", StringArgumentType.word())
										.suggests((c, b) -> SharedSuggestionProvider.suggest(java.util.Arrays.stream(GameMode.meleeKits()).map(GameMode::id).toList(), b))
										.executes(c -> practice(c.getSource().getPlayerOrException(), StringArgumentType.getString(c, "mode"),
												StringArgumentType.getString(c, "level"), StringArgumentType.getString(c, "kit")))))));

		d.register(Commands.literal("battlehub")
				.executes(c -> {
					new MainMenu(c.getSource().getPlayerOrException()).open();
					return 1;
				})
				// the fight logs for training the combat model
				.then(Commands.literal("fights").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.executes(c -> {
							int[] st = net.battlehub.bot.FightRecorder.status();
							c.getSource().sendSuccess(() -> ConfigManager.config().fights.record ? Lang.tr("fights.status", st[0], st[1], st[2])
									: Lang.tr("fights.off"), false);
							return st[0];
						})
						.then(Commands.literal("export").executes(c -> {
							net.minecraft.commands.CommandSourceStack src = c.getSource();
							src.sendSuccess(() -> Lang.tr("fights.exporting"), false);
							net.battlehub.bot.FightRecorder.export(src.getServer(), r -> {
								int n = (int) r[1];
								if (n < 0) src.sendFailure(Lang.tr("fights.export_failed"));
								else if (n == 0) src.sendSuccess(() -> Lang.tr("fights.export_none"), false);
								else src.sendSuccess(() -> Lang.tr("fights.exported", n, Math.round((long) r[2] / 1024.0), r[0].toString()), true);
							});
							return 1;
						}))
						.then(Commands.literal("clear").executes(c -> {
							int n = net.battlehub.bot.FightRecorder.clearFiles();
							c.getSource().sendSuccess(() -> Lang.tr("fights.cleared", n), true);
							return n;
						})))
				.then(Commands.literal("reports").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.executes(c -> {
							java.util.List<String> recent = net.battlehub.fair.Reports.recent();
							if (recent.isEmpty()) {
								c.getSource().sendSuccess(() -> Lang.tr("fair.reports_none"), false);
							}
							for (String line : recent) {
								c.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal(line), false);
							}
							return recent.size();
						}))
				.then(Commands.literal("bots").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.executes(c -> {
							c.getSource().sendSuccess(() -> Lang.tr("bots.count", net.battlehub.bot.Bots.online()), false);
							// what the bots cost the server: thinks, think time, tick time and how much they slowed down
							for (net.minecraft.network.chat.Component line : net.battlehub.bot.Bots.metrics()) {
								c.getSource().sendSuccess(() -> line, false);
							}
							return 1;
						})
						.then(Commands.literal("show").then(Commands.argument("bot", StringArgumentType.word())
								.suggests((c, b) -> SharedSuggestionProvider.suggest(net.battlehub.bot.Bots.names(), b))
								.executes(c -> {
									ServerPlayer viewer = c.getSource().getPlayerOrException();
									net.battlehub.bot.BotPlayer bot = net.battlehub.bot.Bots.byName(StringArgumentType.getString(c, "bot"));
									if (bot == null) {
										c.getSource().sendFailure(Lang.tr("bots.unknown", StringArgumentType.getString(c, "bot")));
										return 0;
									}
									boolean on = net.battlehub.bot.Bots.toggleShow(viewer, bot);
									c.getSource().sendSuccess(() -> Lang.tr(on ? "bots.show_on" : "bots.show_off", bot.getGameProfile().name()), false);
									return 1;
								})))
						.then(Commands.literal("clear").executes(c -> {
							int n = net.battlehub.bot.Bots.online();
							for (net.battlehub.bot.BotPlayer b : net.battlehub.bot.Bots.all()) {
								Match m = Matches.of(b.getUUID());
								if (m != null) m.quit(b);
								net.battlehub.bot.Bots.remove(b);
							}
							c.getSource().sendSuccess(() -> Lang.tr("bots.cleared", n), true);
							return n;
						}))
						// The fighting minds: what a bot thinks and why, its style, and a decision log
						.then(Commands.literal("inspect").then(Commands.argument("bot", StringArgumentType.word())
								.suggests((c, b) -> SharedSuggestionProvider.suggest(net.battlehub.bot.Bots.names(), b))
								.executes(c -> {
									net.battlehub.bot.BotPlayer bot = net.battlehub.bot.Bots.byName(StringArgumentType.getString(c, "bot"));
									if (bot == null) {
										c.getSource().sendFailure(Lang.tr("bots.unknown", StringArgumentType.getString(c, "bot")));
										return 0;
									}
									for (String line : net.battlehub.bot.Bots.inspect(bot)) {
										c.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal(line), false);
									}
									return 1;
								})))
						.then(Commands.literal("profile").then(Commands.argument("bot", StringArgumentType.word())
								.suggests((c, b) -> SharedSuggestionProvider.suggest(net.battlehub.bot.Bots.names(), b))
								.then(Commands.argument("style", StringArgumentType.word())
										.suggests((c, b) -> SharedSuggestionProvider.suggest(java.util.Arrays.stream(net.battlehub.core.ai.Archetype.values())
												.map(net.battlehub.core.ai.Archetype::id).toList(), b))
										.executes(c -> {
											net.battlehub.bot.BotPlayer bot = net.battlehub.bot.Bots.byName(StringArgumentType.getString(c, "bot"));
											String style = StringArgumentType.getString(c, "style");
											if (bot == null || !net.battlehub.bot.Bots.profile(bot, style)) {
												c.getSource().sendFailure(Lang.tr(bot == null ? "bots.unknown" : "bots.unknown_style",
														bot == null ? StringArgumentType.getString(c, "bot") : style));
												return 0;
											}
											c.getSource().sendSuccess(() -> Lang.tr("bots.profiled", bot.getGameProfile().name(), style), true);
											return 1;
										}))))
						.then(Commands.literal("forget").then(Commands.argument("player", StringArgumentType.word())
								.suggests((c, b) -> SharedSuggestionProvider.suggest(java.util.stream.Stream.concat(c.getSource().getOnlinePlayerNames().stream(),
										java.util.stream.Stream.of("all")).toList(), b))
								.executes(c -> {
									String who = StringArgumentType.getString(c, "player");
									if (who.equalsIgnoreCase("all")) {
										int n = net.battlehub.bot.AiMemory.forgetAll(c.getSource().getServer());
										c.getSource().sendSuccess(() -> Lang.tr("bots.forgot_all", n), true);
										return n;
									}
									ServerPlayer target = c.getSource().getServer().getPlayerList().getPlayerByName(who);
									if (target == null) {
										c.getSource().sendFailure(Lang.tr("admin.unknown_player", who));
										return 0;
									}
									net.battlehub.bot.AiMemory.forget(c.getSource().getServer(), target.getUUID());
									c.getSource().sendSuccess(() -> Lang.tr("bots.forgot_other", who), true);
									return 1;
								})))
						// How the learned bots kept to their models, and what to practise in the simulation
						.then(Commands.literal("report")
								.executes(c -> {
									ServerPlayer viewer = c.getSource().getPlayer();
									for (net.minecraft.network.chat.Component line : net.battlehub.bot.ModelReports.summary(viewer)) {
										c.getSource().sendSuccess(() -> line, false);
									}
									return 1;
								})
								.then(Commands.literal("on").executes(c -> {
									net.battlehub.bot.ModelReports.toggle(c.getSource().getPlayerOrException(), true);
									c.getSource().sendSuccess(() -> Lang.tr("bots.report.on"), false);
									return 1;
								}))
								.then(Commands.literal("off").executes(c -> {
									net.battlehub.bot.ModelReports.toggle(c.getSource().getPlayerOrException(), false);
									c.getSource().sendSuccess(() -> Lang.tr("bots.report.off"), false);
									return 1;
								}))
								.then(Commands.literal("reset").executes(c -> {
									net.battlehub.bot.ModelReports.reset();
									c.getSource().sendSuccess(() -> Lang.tr("bots.report.reset"), true);
									return 1;
								})))
						.then(Commands.literal("debug").then(Commands.argument("bot", StringArgumentType.word())
								.suggests((c, b) -> SharedSuggestionProvider.suggest(net.battlehub.bot.Bots.names(), b))
								.executes(c -> {
									net.battlehub.bot.BotPlayer bot = net.battlehub.bot.Bots.byName(StringArgumentType.getString(c, "bot"));
									if (bot == null) {
										c.getSource().sendFailure(Lang.tr("bots.unknown", StringArgumentType.getString(c, "bot")));
										return 0;
									}
									boolean on = net.battlehub.bot.Bots.toggleDebug(bot);
									c.getSource().sendSuccess(() -> Lang.tr(on ? "bots.debug_on" : "bots.debug_off", bot.getGameProfile().name()), true);
									return 1;
								}))))
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
				.then(Commands.literal("world").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.then(Commands.literal("reset").executes(c -> worldReset(c.getSource(), false))
								.then(Commands.literal("new").executes(c -> worldReset(c.getSource(), true)))))
				.then(Commands.literal("reload").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.executes(c -> reload(c.getSource())))
				.then(Commands.literal("matches").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.executes(c -> AdminCommands.matches(c.getSource())))
				.then(Commands.literal("endmatch").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.then(Commands.argument("id", IntegerArgumentType.integer(1))
								.executes(c -> AdminCommands.endMatch(c.getSource(), IntegerArgumentType.getInteger(c, "id")))))
				.then(Commands.literal("arena").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.then(Commands.literal("list").executes(c -> AdminCommands.arenaList(c.getSource()))))
				.then(coinsCommand())
				.then(Commands.literal("weekly")
						.executes(c -> weeklyStatus(c.getSource().getPlayerOrException()))
						.then(Commands.literal("join").executes(c -> net.battlehub.tournament.WeeklyTournament
								.join(c.getSource().getPlayerOrException()) ? 1 : 0))
						.then(Commands.literal("leave").executes(c -> net.battlehub.tournament.WeeklyTournament
								.leave(c.getSource().getPlayerOrException()) ? 1 : 0))
						.then(Commands.literal("open").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
								.then(Commands.argument("seconds", IntegerArgumentType.integer(10, 7200)).executes(c -> {
									net.battlehub.tournament.WeeklyTournament.openNow(c.getSource().getServer(),
											IntegerArgumentType.getInteger(c, "seconds"));
									return 1;
								}))))
				.then(Commands.literal("export").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.executes(c -> {
							boolean ok = net.battlehub.export.StatsExport.write(c.getSource().getServer(), false);
							ServerPlayer p = c.getSource().getPlayer();
							c.getSource().sendSuccess(() -> Lang.tr(p, ok ? "export.done" : "export.failed",
									net.battlehub.export.StatsExport.dir().toString()), true);
							return ok ? 1 : 0;
						})));
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

	/** When the next weekly tournament is, and whether you signed up. */
	private static int weeklyStatus(ServerPlayer p) {
		java.time.ZonedDateTime next = net.battlehub.tournament.WeeklyTournament.next();
		String when = next.format(java.time.format.DateTimeFormatter.ofPattern("EEEE d MMMM HH:mm",
				java.util.Locale.forLanguageTag(Lang.languageOf(p).replace('_', '-'))));
		Msg.send(p, "weekly.status", when, net.battlehub.tournament.WeeklyTournament.signups(),
				Lang.tr(p, net.battlehub.tournament.WeeklyTournament.signedUp(p.getUUID()) ? "weekly.status_in" : "weekly.status_out"));
		if (net.battlehub.tournament.WeeklyTournament.signupsOpen() && !net.battlehub.tournament.WeeklyTournament.signedUp(p.getUUID())) {
			Msg.raw(p, Msg.button(p, "weekly.join_button", "weekly.join_hover", "/battlehub weekly join"));
		}
		return 1;
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

	/** /battlehub world reset [new]: everyone out of the lobby, the whole lobby area emptied and built again. */
	private static int worldReset(CommandSourceStack src, boolean newLook) {
		ServerPlayer p = src.getPlayer();
		if (!net.battlehub.hub.HubWorld.reset(src.getServer(), newLook)) {
			src.sendFailure(Lang.tr(p, "admin.world_reset_busy"));
			return 0;
		}
		src.sendSuccess(() -> Lang.tr(p, newLook ? "admin.world_reset_new" : "admin.world_reset"), true);
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
		// The pack is built again (new overlay files, other settings); players are offered it on their next visit
		net.battlehub.fx.ResourcePack.start(src.getServer());
		net.battlehub.arena.CustomMaps.load(src.getServer());
		ServerPlayer p = src.getPlayer();
		src.sendSuccess(() -> Lang.tr(p, ok ? "admin.reloaded" : "admin.reload_failed"), true);
		BattleHub.LOGGER.info("Configuration reloaded ({})", ok ? "ok" : "with errors");
		return ok ? 1 : 0;
	}

	private static java.util.List<String> practiceModes() {
		java.util.List<String> out = new java.util.ArrayList<>();
		for (GameMode m : GameMode.duels()) out.add(m.id());
		for (GameMode m : GameMode.teamFights()) out.add(m.id());
		return out;
	}

	/** /practice sword hard: a duel against a bot right away (or 1v2 / 1v3 against bots, with a kit: /practice 1v2 hard mace). */
	private static int practice(ServerPlayer p, String modeId, String level, String kitId) {
		GameMode mode = GameMode.byId(modeId);
		// "/practice 1v2 mace": the kit in the place of the level
		GameMode asKit = level == null ? null : GameMode.byId(level);
		if (asKit != null && java.util.Arrays.asList(GameMode.meleeKits()).contains(asKit)) {
			kitId = level;
			level = null;
		}
		if ("adaptive".equalsIgnoreCase(level)) net.battlehub.gui.PracticeMenu.adaptive(p.getUUID(), true);
		net.battlehub.core.ai.Difficulty l = level == null || "adaptive".equalsIgnoreCase(level) ? net.battlehub.gui.PracticeMenu.practiceLevel(p)
				: net.battlehub.core.ai.Difficulty.parse(level, net.battlehub.gui.PracticeMenu.practiceLevel(p));
		if (mode != null && mode.isDuel()) {
			return net.battlehub.bot.Bots.practice(p, mode, l) ? 1 : 0;
		}
		if (mode != null && mode.isTeamFight()) {
			GameMode kit = kitId == null ? null : GameMode.byId(kitId);
			if (kit == null && kitId == null) {
				// no kit given: the choice of kit first
				new net.battlehub.gui.PracticeKitMenu(p, mode).open();
				return 1;
			}
			return net.battlehub.bot.Bots.practiceTeam(p, mode, kit == null ? GameMode.SWORD : kit, l) ? 1 : 0;
		}
		Msg.send(p, "bots.practice_modes");
		return 0;
	}

	private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> name(String arg) {
		return Commands.argument(arg, StringArgumentType.word())
				.suggests((c, b) -> SharedSuggestionProvider.suggest(c.getSource().getOnlinePlayerNames(), b));
	}

	/** /friend, /party (and /pc), /clan (and /cc), /custom. */
	private static void registerSocial(CommandDispatcher<CommandSourceStack> d) {
		d.register(Commands.literal("friend")
				.executes(c -> {
					new net.battlehub.gui.FriendsMenu(c.getSource().getPlayerOrException()).open();
					return 1;
				})
				.then(Commands.literal("add").then(name("player").executes(c -> {
					net.battlehub.social.Friends.add(c.getSource().getPlayerOrException(), StringArgumentType.getString(c, "player"));
					return 1;
				})))
				.then(Commands.literal("accept").then(name("player").executes(c -> {
					net.battlehub.social.Friends.accept(c.getSource().getPlayerOrException(), StringArgumentType.getString(c, "player"));
					return 1;
				})))
				.then(Commands.literal("deny").then(name("player").executes(c -> {
					net.battlehub.social.Friends.deny(c.getSource().getPlayerOrException(), StringArgumentType.getString(c, "player"));
					return 1;
				})))
				.then(Commands.literal("remove").then(name("player").executes(c -> {
					net.battlehub.social.Friends.remove(c.getSource().getPlayerOrException(), StringArgumentType.getString(c, "player"));
					return 1;
				}))));
		d.register(Commands.literal("party")
				.executes(c -> {
					new net.battlehub.gui.FriendsMenu(c.getSource().getPlayerOrException()).open();
					return 1;
				})
				.then(Commands.literal("invite").then(Commands.argument("player", EntityArgument.player()).executes(c -> {
					net.battlehub.social.Groups.invite(c.getSource().getPlayerOrException(), EntityArgument.getPlayer(c, "player"));
					return 1;
				})))
				.then(Commands.literal("accept").then(Commands.argument("leader", EntityArgument.player()).executes(c -> {
					net.battlehub.social.Groups.accept(c.getSource().getPlayerOrException(), EntityArgument.getPlayer(c, "leader"));
					return 1;
				})))
				.then(Commands.literal("kick").then(Commands.argument("player", EntityArgument.player()).executes(c -> {
					net.battlehub.social.Groups.kick(c.getSource().getPlayerOrException(), EntityArgument.getPlayer(c, "player"));
					return 1;
				})))
				.then(Commands.literal("leave").executes(c -> {
					net.battlehub.social.Groups.leave(c.getSource().getPlayerOrException(), false);
					return 1;
				}))
				.then(Commands.literal("disband").executes(c -> {
					net.battlehub.social.Groups.disband(c.getSource().getPlayerOrException());
					return 1;
				}))
				.then(Commands.literal("chat").then(Commands.argument("message", StringArgumentType.greedyString()).executes(c -> {
					net.battlehub.social.Groups.chat(c.getSource().getPlayerOrException(), StringArgumentType.getString(c, "message"));
					return 1;
				}))));
		d.register(Commands.literal("pc").then(Commands.argument("message", StringArgumentType.greedyString()).executes(c -> {
			net.battlehub.social.Groups.chat(c.getSource().getPlayerOrException(), StringArgumentType.getString(c, "message"));
			return 1;
		})));
		d.register(Commands.literal("clan")
				.executes(c -> {
					new net.battlehub.gui.ClanMenu(c.getSource().getPlayerOrException()).open();
					return 1;
				})
				.then(Commands.literal("create").then(Commands.argument("name", StringArgumentType.word())
						.then(Commands.argument("tag", StringArgumentType.word()).executes(c -> {
							net.battlehub.social.Clans.create(c.getSource().getPlayerOrException(), StringArgumentType.getString(c, "name"),
									StringArgumentType.getString(c, "tag"));
							return 1;
						}))))
				.then(Commands.literal("invite").then(name("player").executes(c -> {
					net.battlehub.social.Clans.invite(c.getSource().getPlayerOrException(), StringArgumentType.getString(c, "player"));
					return 1;
				})))
				.then(Commands.literal("join").then(Commands.argument("clan", StringArgumentType.word()).executes(c -> {
					net.battlehub.social.Clans.join(c.getSource().getPlayerOrException(), StringArgumentType.getString(c, "clan"));
					return 1;
				})))
				.then(Commands.literal("kick").then(name("player").executes(c -> {
					net.battlehub.social.Clans.kick(c.getSource().getPlayerOrException(), StringArgumentType.getString(c, "player"));
					return 1;
				})))
				.then(Commands.literal("leave").executes(c -> {
					net.battlehub.social.Clans.leave(c.getSource().getPlayerOrException());
					return 1;
				}))
				.then(Commands.literal("disband").executes(c -> {
					net.battlehub.social.Clans.disband(c.getSource().getPlayerOrException());
					return 1;
				}))
				.then(Commands.literal("chat").then(Commands.argument("message", StringArgumentType.greedyString()).executes(c -> {
					net.battlehub.social.Clans.chat(c.getSource().getPlayerOrException(), StringArgumentType.getString(c, "message"));
					return 1;
				}))));
		d.register(Commands.literal("cc").then(Commands.argument("message", StringArgumentType.greedyString()).executes(c -> {
			net.battlehub.social.Clans.chat(c.getSource().getPlayerOrException(), StringArgumentType.getString(c, "message"));
			return 1;
		})));
		d.register(Commands.literal("custom")
				.executes(c -> {
					new net.battlehub.gui.CustomGameMenu(c.getSource().getPlayerOrException()).open();
					return 1;
				})
				.then(Commands.literal("create").then(Commands.argument("mode", StringArgumentType.word())
						.suggests((c, b) -> SharedSuggestionProvider.suggest(allModeIds(), b))
						.executes(c -> net.battlehub.social.CustomGames.create(c.getSource().getPlayerOrException(),
								GameMode.byId(StringArgumentType.getString(c, "mode"))) != null ? 1 : 0)))
				.then(Commands.literal("join").then(Commands.argument("code", StringArgumentType.word())
						.executes(c -> net.battlehub.social.CustomGames.join(c.getSource().getPlayerOrException(),
								StringArgumentType.getString(c, "code")) ? 1 : 0)))
				.then(Commands.literal("start").executes(c -> net.battlehub.social.CustomGames.start(c.getSource().getPlayerOrException()) ? 1 : 0))
				.then(Commands.literal("leave").executes(c -> {
					net.battlehub.social.CustomGames.leave(c.getSource().getPlayerOrException(), false);
					return 1;
				})));
	}
}
