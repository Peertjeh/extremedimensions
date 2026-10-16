package net.battlehub.games;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.battlehub.arena.Blueprint;
import net.battlehub.arena.Theme;
import net.battlehub.arena.layout.PartyLayouts;
import net.battlehub.core.BorderShrink;
import net.battlehub.core.GameMode;
import net.battlehub.lang.Lang;
import net.battlehub.util.Msg;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundInitializeBorderPacket;
import net.minecraft.network.protocol.game.ClientboundSetBorderSizePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.border.WorldBorder;

/**
 * Last Man Standing: free for all with a kit and a shrinking border. A world border belongs to a whole dimension,
 * so every player in this match gets their own border through packets; the server applies the damage outside it.
 */
public final class LmsMatch extends PartyMatch {
	private final WorldBorder border = new WorldBorder();
	private BorderShrink shrink;
	private boolean announcedShrink;

	public LmsMatch(int id, MinecraftServer server, List<ServerPlayer> players) {
		super(id, GameMode.LMS, server, players);
		shrink = new BorderShrink(game().setting("borderStart", 80), game().setting("borderEnd", 8),
				(int) game().setting("borderDelaySeconds", 45) * 20, (int) game().setting("borderShrinkSeconds", 180) * 20);
	}

	@Override
	protected Blueprint design(Theme theme) {
		return PartyLayouts.lms(theme, random);
	}


	private void sendBorder(ServerPlayer p) {
		p.connection.send(new ClientboundInitializeBorderPacket(border));
	}

	@Override
	protected void onArrived(ServerPlayer p, int index) {
		border.setCenter(arena.origin().getX() + 0.5, arena.origin().getZ() + 0.5);
		border.setSize(shrink.startSize());
		border.setWarningBlocks(4);
		border.setWarningTime(5);
		sendBorder(p);
	}

	@Override
	protected void onWatcherAdded(ServerPlayer p) {
		sendBorder(p);
	}

	@Override
	protected void onRestored(ServerPlayer p) {
		// Back to the real border of wherever the player is now
		p.connection.send(new ClientboundInitializeBorderPacket(p.level().getWorldBorder()));
	}

	public double borderSize() {
		return shrink == null ? 0 : shrink.sizeAt(runTicks);
	}

	@Override
	protected void tickGame() {
		if (runTicks % 20 != 0) {
			return;
		}
		double size = shrink.sizeAt(runTicks);
		if (shrink.shrinking(runTicks) && !announcedShrink) {
			announcedShrink = true;
			forEveryone(p -> Msg.send(p, "lms.shrinking"));
		}
		border.setSize(size);
		ClientboundSetBorderSizePacket packet = new ClientboundSetBorderSizePacket(border);
		forEveryone(p -> p.connection.send(packet));
		double dmg = game().setting("borderDamagePerSecond", 2);
		double cx = arena.origin().getX() + 0.5;
		double cz = arena.origin().getZ() + 0.5;
		for (UUID id : new ArrayList<>(alive)) {
			ServerPlayer p = player(id);
			if (p == null || !active(id)) continue;
			if (BorderShrink.outside(p.getX(), p.getZ(), cx, cz, size) > 0) {
				p.hurtServer(arena.level(), p.damageSources().outOfBorder(), (float) dmg);
				Msg.actionBar(p, "lms.outside");
			}
			if (phase != Phase.RUNNING) {
				return;
			}
		}
	}

	@Override
	protected void onTimeUp() {
		ServerPlayer best = null;
		boolean tie = false;
		for (UUID id : alive) {
			ServerPlayer p = player(id);
			if (p == null) continue;
			if (best == null || p.getHealth() > best.getHealth()) {
				best = p;
				tie = false;
			} else if (p.getHealth() == best.getHealth()) {
				tie = true;
			}
		}
		finish(best == null || tie ? List.of() : List.of(best.getUUID()));
	}

	@Override
	public Component status(ServerPlayer viewer) {
		return Lang.tr(viewer, "lms.status", alive.size(), (int) Math.round(borderSize()));
	}
}
