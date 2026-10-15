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
import com.extremedimensions.penalty.CurseEffects;
import com.extremedimensions.penalty.PenaltyService;
import com.extremedimensions.seam.SeamService;
import com.extremedimensions.mending.MendingService;
import com.extremedimensions.world.WorldService;
import com.extremedimensions.server.WorldFx;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Commands under {@code /tether}: {@code where} for everyone (the world you stand in and whether the mod acts there),
 * the co-op answers ({@code burden accept|refuse}, {@code share on|off}) and {@code tribe} (where you stand with it);
 * for operators Overworld curses on or off, curses set or cleared, Lurkers, hoards, the Seam, bosses and difficulty.
 */
public final class TetherCommand {
	private TetherCommand() {}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		java.util.function.Predicate<CommandSourceStack> op = Commands.hasPermission(Commands.LEVEL_GAMEMASTERS);
		dispatcher.register(Commands.literal("tether")
				// Anyone may ask where they stand; everything else is for operators
				.then(Commands.literal("where").executes(c -> where(c.getSource())))
				// Co-op, for everyone: answering an offer to carry a curse, and opening one's anchors to allies
				.then(Commands.literal("burden")
						.then(Commands.literal("accept").executes(c -> burden(c.getSource(), true)))
						.then(Commands.literal("refuse").executes(c -> burden(c.getSource(), false))))
				.then(Commands.literal("share")
						.then(Commands.literal("on").executes(c -> share(c.getSource(), true)))
						.then(Commands.literal("off").executes(c -> share(c.getSource(), false))))
				// The tribe: anyone may ask where they stand with it; operators may set it
				.then(Commands.literal("tribe").executes(c -> tribe(c.getSource()))
						.then(Commands.literal("set").requires(op)
								.then(Commands.argument("standing", IntegerArgumentType.integer(-100, 100))
										.executes(c -> tribeSet(c.getSource(), IntegerArgumentType.getInteger(c, "standing"))))))
				.then(Commands.literal("overworld_penalties").requires(op)
						.executes(c -> show(c.getSource()))
						.then(Commands.literal("on").executes(c -> set(c.getSource(), true)))
						.then(Commands.literal("off").executes(c -> set(c.getSource(), false))))
				.then(Commands.literal("lurker").requires(op)
						.then(Commands.literal("watch").executes(c -> lurker(c.getSource(), "watch")))
						.then(Commands.literal("stalk").executes(c -> lurker(c.getSource(), "stalk")))
						.then(Commands.literal("drain").executes(c -> lurker(c.getSource(), "drain"))))
				.then(Commands.literal("hoard").requires(op).executes(c -> hoard(c.getSource())))
				.then(Commands.literal("boss").requires(op)
						.then(Commands.literal("matriarch").executes(c -> boss(c.getSource(), "matriarch")))
						.then(Commands.literal("ferryman").executes(c -> boss(c.getSource(), "ferryman")))
						.then(Commands.literal("weaver").executes(c -> boss(c.getSource(), "weaver"))))
				.then(Commands.literal("seam").requires(op).executes(c -> seam(c.getSource())))
				.then(Commands.literal("break_seal").requires(op).executes(c -> {
					MendingService.breakSeal(c.getSource().getServer());
					return 1;
				}))
				.then(Commands.literal("seam_showcase").requires(op).executes(c -> {
					int n = SeamService.showcase(c.getSource().getServer());
					c.getSource().sendSuccess(() -> Component.literal("Designed " + n + " Seam structures; building " + SeamArchitect.pending() + " blocks"), false);
					return n;
				}))
				.then(Commands.literal("storm").requires(op).executes(c -> {
					WorldService.begin(c.getSource().getPlayerOrException(), c.getSource().getServer().getTickCount());
					return 1;
				}))
				.then(preset().requires(op))
				.then(Commands.literal("curse").requires(op)
						.then(Commands.literal("clear").executes(c -> clearCurses(c.getSource())))
						.then(Commands.argument("id", StringArgumentType.word())
								.suggests((c, b) -> SharedSuggestionProvider.suggest(java.util.Arrays.stream(Penalty.values()).map(p -> p.id), b))
								.then(Commands.argument("stacks", IntegerArgumentType.integer(0, 99))
										.executes(c -> setCurse(c.getSource(), StringArgumentType.getString(c, "id"), IntegerArgumentType.getInteger(c, "stacks")))))));
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
		int set = d.addStacks(pen, stacks);
		CurseEffects.applyEffects(p, d);
		Sync.state(p, d);
		DataStore.markDirty();
		src.sendSuccess(() -> Component.translatable("commands.extremedimensions.curse.set", Component.translatable(pen.nameKey()), set, pen.max), false);
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

	/** Summons a Lurker in the given mode near the executing player (for testing and showcases). */
	private static int lurker(CommandSourceStack src, String mode) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer p = src.getPlayerOrException();
		boolean ok = LurkerService.summon(p, mode);
		src.sendSuccess(() -> Component.translatable(ok ? "commands.extremedimensions.lurker.summoned" : "commands.extremedimensions.lurker.failed"), false);
		return ok ? 1 : 0;
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
