package com.extremedimensions.command;

import com.mojang.brigadier.CommandDispatcher;

import com.extremedimensions.data.DataStore;
import com.extremedimensions.penalty.PenaltyService;
import com.extremedimensions.server.WorldFx;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** {@code /tether overworld_penalties [on|off]} for operators. */
public final class TetherCommand {
	private TetherCommand() {}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("tether")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.literal("overworld_penalties")
						.executes(c -> show(c.getSource()))
						.then(Commands.literal("on").executes(c -> set(c.getSource(), true)))
						.then(Commands.literal("off").executes(c -> set(c.getSource(), false)))));
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
