package net.battlehub.ui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.numbers.BlankFormat;
import net.minecraft.network.protocol.game.ClientboundResetScorePacket;
import net.minecraft.network.protocol.game.ClientboundSetDisplayObjectivePacket;
import net.minecraft.network.protocol.game.ClientboundSetObjectivePacket;
import net.minecraft.network.protocol.game.ClientboundSetScorePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;

/**
 * A sidebar per player, sent with packets only to that player (the server's own scoreboard and other players are
 * not touched). Each line is a score entry with its own text; only changed lines are sent again.
 */
public final class Sidebar {
	private static final int MAX_LINES = 15;

	private final String name;
	private final Map<UUID, List<String>> shown = new HashMap<>();
	private final Map<UUID, String> titles = new HashMap<>();

	/** {@code name}: unique per match (max 16 characters). */
	public Sidebar(String name) {
		this.name = name.length() > 16 ? name.substring(0, 16) : name;
	}

	private Objective objective(ServerPlayer p, Component title) {
		Scoreboard board = new Scoreboard();
		return new Objective(board, name, ObjectiveCriteria.DUMMY, title, ObjectiveCriteria.RenderType.INTEGER, false, BlankFormat.INSTANCE);
	}

	/** Shows (or updates) the sidebar for this player: a title and up to 15 lines, top to bottom. */
	public void show(ServerPlayer p, Component title, List<Component> lines) {
		UUID id = p.getUUID();
		List<String> old = shown.get(id);
		String titleText = title.getString();
		if (old == null) {
			Objective o = objective(p, title);
			p.connection.send(new ClientboundSetObjectivePacket(o, ClientboundSetObjectivePacket.METHOD_ADD));
			p.connection.send(new ClientboundSetDisplayObjectivePacket(DisplaySlot.SIDEBAR, o));
			old = new ArrayList<>();
			titles.put(id, titleText);
		} else if (!titleText.equals(titles.get(id))) {
			p.connection.send(new ClientboundSetObjectivePacket(objective(p, title), ClientboundSetObjectivePacket.METHOD_CHANGE));
			titles.put(id, titleText);
		}
		List<String> now = new ArrayList<>();
		int n = Math.min(MAX_LINES, lines.size());
		for (int i = 0; i < n; i++) {
			Component line = lines.get(i);
			String text = line.getString();
			now.add(text);
			if (i >= old.size() || !old.get(i).equals(text)) {
				// Higher score = higher line; the owner is a hidden key per row
				p.connection.send(new ClientboundSetScorePacket("#" + i, name, n - i, Optional.of(line), Optional.of(BlankFormat.INSTANCE)));
			}
		}
		for (int i = n; i < old.size(); i++) {
			p.connection.send(new ClientboundResetScorePacket("#" + i, name));
		}
		// Scores of rows that stay but whose order value changed (when the number of lines changes)
		if (old.size() != n) {
			for (int i = 0; i < n; i++) {
				if (i < old.size() && old.get(i).equals(now.get(i))) {
					p.connection.send(new ClientboundSetScorePacket("#" + i, name, n - i, Optional.of(lines.get(i)), Optional.of(BlankFormat.INSTANCE)));
				}
			}
		}
		shown.put(id, now);
	}

	public void hide(ServerPlayer p) {
		if (shown.remove(p.getUUID()) != null) {
			p.connection.send(new ClientboundSetObjectivePacket(objective(p, Component.empty()), ClientboundSetObjectivePacket.METHOD_REMOVE));
		}
		titles.remove(p.getUUID());
	}

	/** Forgets a player who left (their client drops the sidebar with the connection). */
	public void forget(UUID id) {
		shown.remove(id);
		titles.remove(id);
	}

	public java.util.Set<UUID> viewers() {
		return java.util.Set.copyOf(shown.keySet());
	}
}
