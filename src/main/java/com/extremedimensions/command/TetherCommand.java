package com.extremedimensions.command;

import com.mojang.brigadier.CommandDispatcher;

import com.extremedimensions.data.DataStore;
import com.extremedimensions.hollow.HollowService;
import com.extremedimensions.lurker.LurkerService;
import com.extremedimensions.penalty.PenaltyService;
import com.extremedimensions.seam.SeamService;
import com.extremedimensions.server.WorldFx;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** Operator commands: {@code /tether overworld_penalties [on|off]} and {@code /tether lurker <watch|stalk|drain>}
 * and {@code /tether hoard}. */
public final class TetherCommand {
	private TetherCommand() {}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("tether")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.literal("overworld_penalties")
						.executes(c -> show(c.getSource()))
						.then(Commands.literal("on").executes(c -> set(c.getSource(), true)))
						.then(Commands.literal("off").executes(c -> set(c.getSource(), false))))
				.then(Commands.literal("lurker")
						.then(Commands.literal("watch").executes(c -> lurker(c.getSource(), "watch")))
						.then(Commands.literal("stalk").executes(c -> lurker(c.getSource(), "stalk")))
						.then(Commands.literal("drain").executes(c -> lurker(c.getSource(), "drain"))))
				.then(Commands.literal("hoard").executes(c -> hoard(c.getSource())))
				.then(Commands.literal("seam").executes(c -> seam(c.getSource()))));
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
				PenaltyService.applyEffects(p, DataStore.get(p.getUUID()));
			}
		}
		return 1;
	}
}
